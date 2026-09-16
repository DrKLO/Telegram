#!/usr/bin/env bash
# Shared configuration and portable helpers for Android prebuild scripts.

if [[ -n "${PREBUILD_COMMON_LOADED:-}" ]]; then
    return 0 2>/dev/null || exit 0
fi
PREBUILD_COMMON_LOADED=1

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
JNI_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"
LIB_DIR="${LIB_DIR:-$SCRIPT_DIR/lib}"
INCLUDE_DIR="${INCLUDE_DIR:-$SCRIPT_DIR/include}"
PKGCONFIG_DIR="${PKGCONFIG_DIR:-$SCRIPT_DIR/pkgconfig}"

NDK_VERSION="${NDK_VERSION:-27.2.12479018}"
API="${API:-21}"
ABIS="${ABIS:-arm64-v8a armeabi-v7a x86_64 x86}"

host_os="$(uname -s)"
case "$host_os" in
    Darwin)
        DEFAULT_ANDROID_SDK_ROOT="$HOME/Library/Android/sdk"
        ;;
    Linux)
        DEFAULT_ANDROID_SDK_ROOT="$HOME/Android/Sdk"
        ;;
    *)
        echo "ERROR: unsupported host OS: $host_os" >&2
        return 1 2>/dev/null || exit 1
        ;;
esac

ANDROID_SDK_ROOT="${ANDROID_SDK_ROOT:-$DEFAULT_ANDROID_SDK_ROOT}"
ANDROID_NDK_HOME="${ANDROID_NDK_HOME:-$ANDROID_SDK_ROOT/ndk/$NDK_VERSION}"
TOOLCHAIN_FILE="$ANDROID_NDK_HOME/build/cmake/android.toolchain.cmake"

# Resolve the host-specific LLVM prebuilt directory once for scripts that call
# the NDK compilers directly (libvpx/dav1d/FFmpeg).
case "$host_os" in
    Darwin)
        case "$(uname -m)" in
            arm64)
                if [[ -d "$ANDROID_NDK_HOME/toolchains/llvm/prebuilt/darwin-arm64" ]]; then
                    NDK_HOST_TAG="darwin-arm64"
                else
                    NDK_HOST_TAG="darwin-x86_64"
                fi
                ;;
            x86_64) NDK_HOST_TAG="darwin-x86_64" ;;
            *)
                echo "ERROR: unsupported macOS host architecture: $(uname -m)" >&2
                return 1 2>/dev/null || exit 1
                ;;
        esac
        ;;
    Linux)
        case "$(uname -m)" in
            x86_64) NDK_HOST_TAG="linux-x86_64" ;;
            aarch64|arm64) NDK_HOST_TAG="linux-aarch64" ;;
            *)
                echo "ERROR: unsupported Linux host architecture: $(uname -m)" >&2
                return 1 2>/dev/null || exit 1
                ;;
        esac
        ;;
esac
NDK_TOOLCHAIN="$ANDROID_NDK_HOME/toolchains/llvm/prebuilt/$NDK_HOST_TAG"

# Make C/C++ outputs independent from the absolute checkout path.  Clang may
# otherwise embed /Users/... on macOS and /home/... on Linux into object files
# through __FILE__, diagnostics/debug metadata and other compiler-generated data.
#
# Map both the project JNI tree and Android NDK to stable virtual locations on
# every host. This removes differences such as /Users/... vs /home/... not only
# for project sources, but also for NDK headers/sysroot paths recorded by Clang.
REPRODUCIBLE_SOURCE_ROOT="${REPRODUCIBLE_SOURCE_ROOT:-/jni}"
REPRODUCIBLE_NDK_ROOT="${REPRODUCIBLE_NDK_ROOT:-/android-ndk}"

REPRODUCIBLE_PATH_FLAGS="-ffile-prefix-map=$JNI_DIR=$REPRODUCIBLE_SOURCE_ROOT -fdebug-prefix-map=$JNI_DIR=$REPRODUCIBLE_SOURCE_ROOT -fmacro-prefix-map=$JNI_DIR=$REPRODUCIBLE_SOURCE_ROOT -ffile-prefix-map=$ANDROID_NDK_HOME=$REPRODUCIBLE_NDK_ROOT -fdebug-prefix-map=$ANDROID_NDK_HOME=$REPRODUCIBLE_NDK_ROOT -fmacro-prefix-map=$ANDROID_NDK_HOME=$REPRODUCIBLE_NDK_ROOT"

# Produce final static-library objects directly. Avoid post-build strip/objcopy,
# which can invalidate SHT_LLVM_ADDRSIG used by LLD --icf=safe.
PREBUILD_OBJECT_FLAGS="-g0 -fno-ident"

# Preserve caller-provided flags while appending the reproducibility flags.
CFLAGS="${CFLAGS:+$CFLAGS }$REPRODUCIBLE_PATH_FLAGS $PREBUILD_OBJECT_FLAGS"
CXXFLAGS="${CXXFLAGS:+$CXXFLAGS }$REPRODUCIBLE_PATH_FLAGS $PREBUILD_OBJECT_FLAGS"
export CFLAGS CXXFLAGS

# Pass these explicitly to CMake projects. Some projects/cache setups do not
# reliably preserve environment CFLAGS/CXXFLAGS across configure steps.
CMAKE_REPRO_C_FLAGS="$CFLAGS"
CMAKE_REPRO_CXX_FLAGS="$CXXFLAGS"

# Normalize environment data that can otherwise leak into generated files or
# archives. Individual build systems are free to ignore SOURCE_DATE_EPOCH, but
# exporting it is harmless and makes supported tools deterministic.
export SOURCE_DATE_EPOCH="${SOURCE_DATE_EPOCH:-1}"
export TZ="${TZ:-UTC}"
export LC_ALL="${LC_ALL:-C}"

if [[ -z "${JOBS:-}" ]]; then
    if command -v nproc >/dev/null 2>&1; then
        JOBS="$(nproc)"
    elif command -v sysctl >/dev/null 2>&1; then
        JOBS="$(sysctl -n hw.logicalcpu 2>/dev/null || sysctl -n hw.ncpu 2>/dev/null || echo 8)"
    else
        JOBS=8
    fi
fi

# Convert a dotted Android SDK CMake directory name into a numeric key so the
# newest installed version can be selected without GNU sort -V (not on macOS).
version_key() {
    local version="$1"
    awk -v version="$version" 'BEGIN {
        n = split(version, parts, ".")
        for (i = 1; i <= 6; ++i) {
            value = (i <= n ? parts[i] + 0 : 0)
            printf "%09d", value
        }
    }'
}

find_latest_sdk_tool() {
    local tool_name="$1"
    local cmake_root="$ANDROID_SDK_ROOT/cmake"
    local dir version key best_key="" best_tool=""

    [[ -d "$cmake_root" ]] || return 1

    for dir in "$cmake_root"/*; do
        [[ -d "$dir" ]] || continue
        [[ -x "$dir/bin/$tool_name" ]] || continue

        version="$(basename "$dir")"
        key="$(version_key "$version")"
        if [[ -z "$best_key" || "$key" > "$best_key" ]]; then
            best_key="$key"
            best_tool="$dir/bin/$tool_name"
        fi
    done

    [[ -n "$best_tool" ]] || return 1
    printf '%s\n' "$best_tool"
}

resolve_tool() {
    local override="$1"
    local tool_name="$2"
    local path_tool=""
    local sdk_tool=""

    if [[ -n "$override" ]]; then
        printf '%s\n' "$override"
        return 0
    fi

    path_tool="$(command -v "$tool_name" 2>/dev/null || true)"
    if [[ -n "$path_tool" ]]; then
        printf '%s\n' "$path_tool"
        return 0
    fi

    sdk_tool="$(find_latest_sdk_tool "$tool_name" 2>/dev/null || true)"
    if [[ -n "$sdk_tool" ]]; then
        printf '%s\n' "$sdk_tool"
        return 0
    fi

    return 1
}

CMAKE_BIN="$(resolve_tool "${CMAKE_BIN:-}" cmake || true)"
NINJA_BIN="$(resolve_tool "${NINJA_BIN:-}" ninja || true)"

require_command() {
    local command_name="$1"
    command -v "$command_name" >/dev/null 2>&1 || {
        echo "ERROR: $command_name is required" >&2
        return 1
    }
}

require_cmake_ninja() {
    [[ -n "$CMAKE_BIN" && -x "$CMAKE_BIN" ]] || {
        echo "ERROR: cmake is required" >&2
        echo "       Install it, add it to PATH, or install CMake from Android SDK Manager." >&2
        echo "       Android SDK searched at: $ANDROID_SDK_ROOT" >&2
        return 1
    }
    [[ -n "$NINJA_BIN" && -x "$NINJA_BIN" ]] || {
        echo "ERROR: ninja is required" >&2
        echo "       Install it, add it to PATH, or install CMake from Android SDK Manager." >&2
        echo "       Android SDK searched at: $ANDROID_SDK_ROOT" >&2
        return 1
    }
}

require_ndk() {
    [[ -f "$TOOLCHAIN_FILE" ]] || {
        echo "ERROR: Android NDK not found: $ANDROID_NDK_HOME" >&2
        echo "       Set ANDROID_NDK_HOME, ANDROID_SDK_ROOT, or NDK_VERSION." >&2
        return 1
    }
}

find_ndk_tool() {
    local tool_name="$1"
    local candidate

    for candidate in "$ANDROID_NDK_HOME"/toolchains/llvm/prebuilt/*/bin/"$tool_name"; do
        [[ -x "$candidate" ]] || continue
        printf '%s\n' "$candidate"
        return 0
    done

    return 1
}

LLVM_STRIP_BIN="${LLVM_STRIP_BIN:-$(find_ndk_tool llvm-strip || true)}"
LLVM_OBJCOPY_BIN="${LLVM_OBJCOPY_BIN:-$(find_ndk_tool llvm-objcopy || true)}"
LLVM_READELF_BIN="${LLVM_READELF_BIN:-$(find_ndk_tool llvm-readelf || true)}"

require_llvm_strip() {
    [[ -n "$LLVM_STRIP_BIN" && -x "$LLVM_STRIP_BIN" ]] || {
        echo "ERROR: llvm-strip was not found in Android NDK: $ANDROID_NDK_HOME" >&2
        return 1
    }
}

require_llvm_objcopy() {
    [[ -n "$LLVM_OBJCOPY_BIN" && -x "$LLVM_OBJCOPY_BIN" ]] || {
        echo "ERROR: llvm-objcopy was not found in Android NDK: $ANDROID_NDK_HOME" >&2
        return 1
    }
}

strip_archive_debug() {
    local archive="$1"
    [[ -f "$archive" ]] || {
        echo "ERROR: archive not found for stripping: $archive" >&2
        return 1
    }
    require_llvm_strip
    "$LLVM_STRIP_BIN" --strip-debug "$archive"
}

normalize_archive() {
    local archive="$1"
    local temporary="${archive}.normalized.$$"

    [[ -f "$archive" ]] || {
        echo "ERROR: archive not found for normalization: $archive" >&2
        return 1
    }

    require_llvm_objcopy
    rm -f "$temporary"

    # llvm-objcopy understands GNU/LLVM archives directly, so archive member
    # ordering and duplicate member names are preserved. Removing .comment
    # eliminates host-Clang build metadata (+/-bolt, +/-mlgo) without touching
    # code, relocations, or symbols required by the final linker.
    if ! "$LLVM_OBJCOPY_BIN" --remove-section=.comment "$archive" "$temporary"; then
        rm -f "$temporary"
        return 1
    fi

    chmod 0644 "$temporary"
    mv -f "$temporary" "$archive"
}

finalize_archive() {
    local archive="$1"
    strip_archive_debug "$archive"
    normalize_archive "$archive"
}

abi_output_dir() {
    printf '%s/%s\n' "$LIB_DIR" "$1"
}

abi_include_dir() {
    printf '%s/%s\n' "$INCLUDE_DIR" "$1"
}

abi_pkgconfig_dir() {
    printf '%s/%s\n' "$PKGCONFIG_DIR" "$1"
}

copy_archive() {
    local source="$1"
    local destination="$2"
    mkdir -p "$(dirname "$destination")"
    cp "$source" "$destination"
    chmod 0644 "$destination"
}

# Rewrite host-specific absolute paths only inside generated diagnostic/build
# metadata. This does not change the real paths passed to compilers/linkers.
normalize_host_paths_in_string() {
    local value="$1"

    # Replace the full host-specific LLVM prebuilt path before the NDK root so
    # linux-x86_64 / darwin-x86_64 cannot leak into embedded config strings.
    value="${value//$NDK_TOOLCHAIN/$REPRODUCIBLE_NDK_ROOT/toolchain}"
    value="${value//$ANDROID_NDK_HOME/$REPRODUCIBLE_NDK_ROOT}"
    value="${value//$JNI_DIR/$REPRODUCIBLE_SOURCE_ROOT}"

    printf '%s' "$value"
}

# Normalize only lines containing MARKER. Useful for generated files where the
# build configuration is embedded as a runtime diagnostic string.
normalize_generated_metadata_lines() {
    local file="$1"
    local marker="$2"
    local temporary line normalized found=0

    [[ -f "$file" ]] || {
        echo "ERROR: generated metadata file not found: $file" >&2
        return 1
    }

    temporary="$(mktemp "${file}.normalized.XXXXXX")"
    while IFS= read -r line || [[ -n "$line" ]]; do
        if [[ "$line" == *"$marker"* ]]; then
            normalized="$(normalize_host_paths_in_string "$line")"
            printf '%s\n' "$normalized" >> "$temporary"
            found=1
        else
            printf '%s\n' "$line" >> "$temporary"
        fi
    done < "$file"

    if [[ "$found" != "1" ]]; then
        rm -f "$temporary"
        echo "ERROR: metadata marker '$marker' was not found in: $file" >&2
        return 1
    fi

    chmod --reference="$file" "$temporary" 2>/dev/null || chmod 0644 "$temporary"
    mv -f "$temporary" "$file"
}


command_version_line() {
    local label="$1"
    local command_name="$2"
    shift 2

    local command_path
    command_path="$(command -v "$command_name" 2>/dev/null || true)"
    if [[ -z "$command_path" ]]; then
        printf '%-16s %s\n' "$label" '<not installed>'
        return 0
    fi

    local version
    version="$($command_name "$@" 2>&1 | head -n 1 || true)"
    printf '%-16s %s | %s\n' "$label" "$version" "$command_path"
}

write_build_environment() {
    local destination="${1:-$SCRIPT_DIR/build/env.txt}"
    local clang_bin="$NDK_TOOLCHAIN/bin/clang"

    mkdir -p "$(dirname "$destination")"
    {
        echo "Build environment"
        echo "Generated: $(date -u '+%Y-%m-%dT%H:%M:%SZ')"
        echo "Host OS: $host_os"
        echo "Host arch: $(uname -m)"
        echo "ABIs: $ABIS"
        echo "API: $API"
        echo "Jobs: $JOBS"
        echo "Android SDK: $ANDROID_SDK_ROOT"
        echo "Android NDK: $ANDROID_NDK_HOME"
        echo "NDK version: $NDK_VERSION"
        echo "NDK host tag: $NDK_HOST_TAG"
        if [[ -x "$clang_bin" ]]; then
            printf '%-16s %s | %s\n' "Clang" "$("$clang_bin" --version 2>&1 | head -n 1)" "$clang_bin"
        fi
        if [[ -n "$CMAKE_BIN" && -x "$CMAKE_BIN" ]]; then
            printf '%-16s %s | %s\n' "CMake" "$("$CMAKE_BIN" --version 2>&1 | head -n 1)" "$CMAKE_BIN"
        fi
        if [[ -n "$NINJA_BIN" && -x "$NINJA_BIN" ]]; then
            printf '%-16s %s | %s\n' "Ninja" "$("$NINJA_BIN" --version 2>&1 | head -n 1)" "$NINJA_BIN"
        fi
        command_version_line "Make" make --version
        command_version_line "NASM" nasm -v
        command_version_line "Yasm" yasm --version
        command_version_line "Meson" meson --version
        command_version_line "pkg-config" pkg-config --version
        command_version_line "gperf" gperf --version
        command_version_line "Python" python3 --version
        command_version_line "Rust" rustc --version
        command_version_line "Cargo" cargo --version
        command_version_line "Go" go version
        command_version_line "Perl" perl -v
        echo "SOURCE_DATE_EPOCH: ${SOURCE_DATE_EPOCH:-}"
        echo "TZ: ${TZ:-}"
        echo "LC_ALL: ${LC_ALL:-}"
    } > "$destination"

    echo "Build environment saved: $destination"
}

print_common_config() {
    echo "Host: $host_os"
    echo "Jobs: $JOBS"
    echo "ABIs: $ABIS"
    echo "Library output: $LIB_DIR"
    echo "Generated header output: $INCLUDE_DIR"
    echo "Pkg-config output: $PKGCONFIG_DIR"
    echo "Reproducible source root: $REPRODUCIBLE_SOURCE_ROOT"
    echo "Reproducible NDK root: $REPRODUCIBLE_NDK_ROOT"
    [[ -n "$CMAKE_BIN" ]] && echo "CMake: $CMAKE_BIN"
    [[ -n "$NINJA_BIN" ]] && echo "Ninja: $NINJA_BIN"
    [[ -n "$LLVM_STRIP_BIN" ]] && echo "llvm-strip: $LLVM_STRIP_BIN"
    [[ -n "$LLVM_OBJCOPY_BIN" ]] && echo "llvm-objcopy: $LLVM_OBJCOPY_BIN"
    [[ -n "$LLVM_READELF_BIN" ]] && echo "llvm-readelf: $LLVM_READELF_BIN"
}
