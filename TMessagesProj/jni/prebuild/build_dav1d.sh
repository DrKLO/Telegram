#!/usr/bin/env bash
# Build static dav1d for Android with the exact configuration previously used
# by build_ffmpeg_libvpx_dav1d_android_ndk27_merged.sh.
#
# Output:
#   jni/prebuild/lib/<ABI>/libdav1d.a
#
# Public source headers remain canonical. Only generated or ABI-dependent
# installed headers are copied to jni/prebuild/include/<ABI>/ as an overlay.

set -Eeuo pipefail

export SOURCE_DATE_EPOCH="${SOURCE_DATE_EPOCH:-946684800}"
export TZ=UTC
export LC_ALL=C
export LANG=C
export PYTHONHASHSEED=0
export ZERO_AR_DATE=1
umask 022

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "$SCRIPT_DIR/common.sh"

# Keep build flags byte-for-byte equivalent to the merged media script instead
# of inheriting common.sh's generic CFLAGS/CXXFLAGS.
unset CFLAGS CXXFLAGS

require_ndk
require_command meson
require_command python3

# Meson determines the object ordering passed to the static archiver. Different
# Meson versions can therefore produce byte-different libdav1d.a archives even
# when every .o is identical. Set MESON_VERSION to enforce the same version on
# all build hosts, e.g. MESON_VERSION=1.9.0.
MESON_VERSION="${MESON_VERSION:-}"
DETECTED_MESON_VERSION="$(meson --version | head -n 1)"
if [[ -n "$MESON_VERSION" && "$DETECTED_MESON_VERSION" != "$MESON_VERSION" ]]; then
    echo "ERROR: Meson $MESON_VERSION is required for reproducible dav1d builds" >&2
    echo "       Found: $DETECTED_MESON_VERSION ($(command -v meson))" >&2
    exit 1
fi
require_command pkg-config
[[ -n "$NINJA_BIN" && -x "$NINJA_BIN" ]] || {
    echo "ERROR: ninja is required" >&2
    exit 1
}

PROJECT_ROOT="$JNI_DIR"
DAV1D_SOURCE_DIR="${DAV1D_SOURCE_DIR:-$JNI_DIR/third_party/dav1d}"
BUILD_ROOT="${DAV1D_BUILD_ROOT:-$SCRIPT_DIR/build/dav1d}"
WORK_DIR="$BUILD_ROOT/work"
INSTALL_DIR="$BUILD_ROOT/install"

UNWIND_TABLES="${UNWIND_TABLES:-0}"
FRAME_POINTERS="${FRAME_POINTERS:-0}"
FUNCTION_SECTIONS="${FUNCTION_SECTIONS:-1}"
ADDRSIG="${ADDRSIG:-1}"
HIDDEN_VISIBILITY="${HIDDEN_VISIBILITY:-0}"

error() { echo "ERROR: $*" >&2; }

require_bool() {
    local name="$1" value="$2"
    if [[ "$value" != "0" && "$value" != "1" ]]; then
        error "$name must be 0 or 1, got: $value"
        exit 1
    fi
}
for name in UNWIND_TABLES FRAME_POINTERS FUNCTION_SECTIONS ADDRSIG HIDDEN_VISIBILITY; do
    require_bool "$name" "${!name}"
done

[[ -f "$DAV1D_SOURCE_DIR/meson.build" ]] || {
    error "dav1d sources were not found in: $DAV1D_SOURCE_DIR"
    exit 1
}
DAV1D_SOURCE_DIR="$(cd "$DAV1D_SOURCE_DIR" && pwd)"

check_x86_assembler() {
    local abi="$1"
    if [[ "$abi" == "x86" || "$abi" == "x86_64" ]]; then
        if ! command -v nasm >/dev/null 2>&1 && ! command -v yasm >/dev/null 2>&1; then
            error "NASM or Yasm is required for $abi."
            return 1
        fi
    fi
}

COMMON_CFLAGS_BASE=""
COMMON_CFLAGS_BASE+=" -ffile-prefix-map=$PROJECT_ROOT=/src"
COMMON_CFLAGS_BASE+=" -fdebug-prefix-map=$PROJECT_ROOT=/src"
COMMON_CFLAGS_BASE+=" -fmacro-prefix-map=$PROJECT_ROOT=/src"
COMMON_CFLAGS_BASE+=" -ffile-prefix-map=$BUILD_ROOT=/build"
COMMON_CFLAGS_BASE+=" -fdebug-prefix-map=$BUILD_ROOT=/build"
COMMON_CFLAGS_BASE+=" -fmacro-prefix-map=$BUILD_ROOT=/build"
COMMON_CFLAGS_BASE+=" -fdebug-compilation-dir=."
[[ "$FUNCTION_SECTIONS" == "1" ]] && COMMON_CFLAGS_BASE+=" -ffunction-sections -fdata-sections"
[[ "$HIDDEN_VISIBILITY" == "1" ]] && COMMON_CFLAGS_BASE+=" -fvisibility=hidden"
[[ "$UNWIND_TABLES" == "0" ]] && COMMON_CFLAGS_BASE+=" -fno-asynchronous-unwind-tables -fno-unwind-tables"
if [[ "$FRAME_POINTERS" == "1" ]]; then
    COMMON_CFLAGS_BASE+=" -fno-omit-frame-pointer"
else
    COMMON_CFLAGS_BASE+=" -fomit-frame-pointer"
fi
COMMON_CFLAGS_BASE+=" -g0 -fno-ident"
COMMON_CFLAGS_BASE="${COMMON_CFLAGS_BASE# }"
COMMON_CFLAGS="$COMMON_CFLAGS_BASE"
[[ "$ADDRSIG" == "1" ]] && COMMON_CFLAGS+=" -faddrsig"

write_dav1d_cross_file() {
    local abi="$1" file="$2"
    local clang_target cpu_family cpu extra_c_args cc shared_c_args flag

    case "$abi" in
        armeabi-v7a)
            clang_target="armv7a-linux-androideabi${API}"
            cpu_family="arm"
            cpu="armv7-a"
            extra_c_args="'-march=armv7-a', '-mthumb', '-mfpu=neon', '-mfloat-abi=softfp'"
            ;;
        arm64-v8a)
            clang_target="aarch64-linux-android${API}"
            cpu_family="aarch64"
            cpu="armv8-a"
            extra_c_args="'-march=armv8-a'"
            ;;
        x86)
            clang_target="i686-linux-android${API}"
            cpu_family="x86"
            cpu="i686"
            extra_c_args="'-march=i686', '-msse3', '-mfpmath=sse'"
            ;;
        x86_64)
            clang_target="x86_64-linux-android${API}"
            cpu_family="x86_64"
            cpu="x86_64"
            extra_c_args="'-march=x86-64', '-msse4.1'"
            ;;
        *) error "Unsupported ABI for dav1d: $abi"; return 1 ;;
    esac

    cc="$NDK_TOOLCHAIN/bin/${clang_target}-clang"
    [[ -x "$cc" ]] || { error "dav1d compiler was not found: $cc"; return 1; }

    shared_c_args=""
    for flag in $COMMON_CFLAGS; do
        shared_c_args+=", '$flag'"
    done

    cat > "$file" <<CROSS
[binaries]
c = '$cc'
ar = '$NDK_TOOLCHAIN/bin/llvm-ar'
strip = '$NDK_TOOLCHAIN/bin/llvm-strip'
ranlib = '$NDK_TOOLCHAIN/bin/llvm-ranlib'
nm = '$NDK_TOOLCHAIN/bin/llvm-nm'
objcopy = '$NDK_TOOLCHAIN/bin/llvm-objcopy'
pkg-config = 'pkg-config'

[host_machine]
system = 'android'
cpu_family = '$cpu_family'
cpu = '$cpu'
endian = 'little'

[properties]
needs_exe_wrapper = true

[built-in options]
c_args = [$extra_c_args, '-fPIC'$shared_c_args]
c_link_args = ['-Wl,--gc-sections']
CROSS
}

verify_dav1d_archive() {
    local abi="$1" library="$2"
    [[ -f "$library" ]] || { error "Missing dav1d archive for $abi: $library"; return 1; }
    if ! "$NDK_TOOLCHAIN/bin/llvm-nm" -g --defined-only "$library" \
        | awk '$NF == "dav1d_open" { found = 1 } END { exit found ? 0 : 1 }';
    then
        error "dav1d_open was not found in $library"
        return 1
    fi
}


copy_generated_header_overlay() {
    local abi="$1"
    local installed_root="$2/include"
    local destination_root
    destination_root="$(abi_include_dir "$abi")"

    local installed relative source destination copied=0
    while IFS= read -r -d '' installed; do
        relative="${installed#$installed_root/}"
        source="$DAV1D_SOURCE_DIR/include/$relative"
        if [[ -f "$source" ]] && cmp -s "$source" "$installed"; then
            continue
        fi
        destination="$destination_root/$relative"
        mkdir -p "$(dirname "$destination")"
        cp -f "$installed" "$destination"
        chmod 0644 "$destination"
        copied=$((copied + 1))
        echo "==> generated/ABI header: $destination"
    done < <(find "$installed_root/dav1d" -type f -name '*.h' -print0)

    if [[ "$copied" == "0" ]]; then
        echo "==> dav1d $abi: no generated/ABI-dependent public headers"
    fi
}


install_pkgconfig_metadata() {
    local abi="$1" prefix="$2"
    local source_pc="$prefix/lib/pkgconfig/dav1d.pc"
    local destination_dir destination overlay source_include
    destination_dir="$(abi_pkgconfig_dir "$abi")"
    destination="$destination_dir/dav1d.pc"
    overlay="$(abi_include_dir "$abi")"
    source_include="$DAV1D_SOURCE_DIR/include"

    [[ -f "$source_pc" ]] || { error "Missing dav1d pkg-config file: $source_pc"; return 1; }
    mkdir -p "$destination_dir"
    awk -v libdir="$(abi_output_dir "$abi")" -v includedir="$source_include" -v overlay="$overlay" '
        /^prefix=/ { print "prefix=" libdir; next }
        /^libdir=/ { print "libdir=" libdir; next }
        /^includedir=/ { print "includedir=" includedir; next }
        /^Cflags:/ { print "Cflags: -I" overlay " -I" includedir; next }
        { print }
    ' "$source_pc" > "$destination"
    chmod 0644 "$destination"
    echo "==> $destination"
}

build_dav1d_for_abi() {
    local abi="$1" prefix build_dir cross_file destination
    check_x86_assembler "$abi"

    prefix="$INSTALL_DIR/$abi"
    build_dir="$WORK_DIR/$abi"
    cross_file="$WORK_DIR/crossfiles/$abi.ini"

    rm -rf "$build_dir" "$prefix"
    mkdir -p "$build_dir" "$prefix" "$(dirname "$cross_file")"
    write_dav1d_cross_file "$abi" "$cross_file"

    echo
    echo "========== dav1d: $abi, API $API =========="

    # Make SDK-provided Ninja visible to Meson on macOS without changing the
    # selected backend or any compilation parameter.
    local meson_path="$(dirname "$NINJA_BIN"):$PATH"
    if ! env PATH="$meson_path" meson setup "$build_dir" "$DAV1D_SOURCE_DIR" \
        --cross-file "$cross_file" \
        --prefix "$prefix" \
        --libdir lib \
        --buildtype release \
        --default-library static \
        -Db_ndebug=true \
        -Db_lto=false \
        -Denable_tools=false \
        -Denable_examples=false \
        -Denable_tests=false \
        -Denable_docs=false \
        -Denable_asm=true;
    then
        error "dav1d configure failed for $abi."
        return 1
    fi

    if ! "$NINJA_BIN" -C "$build_dir" -j "$JOBS" 2>&1 | tee "$build_dir/build.log"; then
        error "dav1d build failed for $abi. Log: $build_dir/build.log"
        return 1
    fi
    if ! "$NINJA_BIN" -C "$build_dir" install 2>&1 | tee "$build_dir/install.log"; then
        error "dav1d install failed for $abi. Log: $build_dir/install.log"
        return 1
    fi

    verify_dav1d_archive "$abi" "$prefix/lib/libdav1d.a"
    copy_generated_header_overlay "$abi" "$prefix"
    install_pkgconfig_metadata "$abi" "$prefix"
    [[ -f "$prefix/include/dav1d/dav1d.h" ]] || { error "Missing dav1d headers for $abi"; return 1; }
    [[ -f "$prefix/lib/pkgconfig/dav1d.pc" ]] || { error "Missing dav1d.pc for $abi"; return 1; }

    destination="$(abi_output_dir "$abi")/libdav1d.a"
    copy_archive "$prefix/lib/libdav1d.a" "$destination"
    echo "==> $destination"
}

for abi in $ABIS; do rm -rf "$(abi_include_dir "$abi")/dav1d"; done

print_common_config
echo "dav1d source: $DAV1D_SOURCE_DIR"
echo "Meson: $DETECTED_MESON_VERSION ($(command -v meson))"
if [[ -z "$MESON_VERSION" ]]; then
    echo "WARNING: MESON_VERSION is not pinned; use the same Meson version on every host for byte-reproducible libdav1d.a"
fi
echo "dav1d build root: $BUILD_ROOT"
echo "dav1d flags: $COMMON_CFLAGS"

rm -rf "$BUILD_ROOT"
mkdir -p "$WORK_DIR" "$INSTALL_DIR"
for abi in $ABIS; do
    build_dav1d_for_abi "$abi"
done
rm -rf "$BUILD_ROOT"

echo
echo "dav1d static build completed."
