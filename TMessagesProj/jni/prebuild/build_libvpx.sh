#!/usr/bin/env bash
# Build static libvpx for Android with the exact configuration previously used
# by build_ffmpeg_libvpx_dav1d_android_ndk27_merged.sh.
#
# Output:
#   jni/prebuild/lib/<ABI>/libvpx.a
#
# Public source headers remain canonical. Only generated or ABI-dependent
# installed headers are copied to jni/prebuild/include/<ABI>/ as an overlay.

set -Eeuo pipefail

# Preserve the merged build's reproducibility environment before common.sh sets
# its generic defaults.
export SOURCE_DATE_EPOCH="${SOURCE_DATE_EPOCH:-946684800}"
export TZ=UTC
export LC_ALL=C
export LANG=C
export PYTHONHASHSEED=0
export ZERO_AR_DATE=1
umask 022

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "$SCRIPT_DIR/common.sh"

# common.sh exports generic CFLAGS for the other prebuild scripts. libvpx must
# receive exactly the flags from the merged media build through --extra-cflags.
unset CFLAGS CXXFLAGS

require_ndk
require_command make

PROJECT_ROOT="$JNI_DIR"
LIBVPX_SOURCE_DIR="${LIBVPX_SOURCE_DIR:-$JNI_DIR/third_party/libvpx}"
BUILD_ROOT="${LIBVPX_BUILD_ROOT:-$SCRIPT_DIR/build/libvpx}"
WORK_DIR="$BUILD_ROOT/work"
INSTALL_DIR="$BUILD_ROOT/install"

LIBVPX_REALTIME_ONLY="${LIBVPX_REALTIME_ONLY:-1}"
LIBVPX_SMALL="${LIBVPX_SMALL:-1}"
LIBVPX_BETTER_HW_COMPATIBILITY="${LIBVPX_BETTER_HW_COMPATIBILITY:-1}"
LIBVPX_WEBM_IO="${LIBVPX_WEBM_IO:-0}"
LIBVPX_RUNTIME_CPU_DETECT="${LIBVPX_RUNTIME_CPU_DETECT:-auto}"
LIBVPX_ARM_EXTENSIONS="${LIBVPX_ARM_EXTENSIONS:-0}"
LIBVPX_SIZE_LIMIT="${LIBVPX_SIZE_LIMIT:-4096x4096}"

UNWIND_TABLES="${UNWIND_TABLES:-0}"
FRAME_POINTERS="${FRAME_POINTERS:-0}"
FUNCTION_SECTIONS="${FUNCTION_SECTIONS:-1}"
ADDRSIG="${ADDRSIG:-1}"
LIBVPX_ADDRSIG="${LIBVPX_ADDRSIG:-0}"
HIDDEN_VISIBILITY="${HIDDEN_VISIBILITY:-0}"

error() { echo "ERROR: $*" >&2; }

require_bool() {
    local name="$1" value="$2"
    if [[ "$value" != "0" && "$value" != "1" ]]; then
        error "$name must be 0 or 1, got: $value"
        exit 1
    fi
}

for name in \
    LIBVPX_REALTIME_ONLY LIBVPX_SMALL LIBVPX_BETTER_HW_COMPATIBILITY \
    LIBVPX_WEBM_IO LIBVPX_ARM_EXTENSIONS UNWIND_TABLES FRAME_POINTERS \
    FUNCTION_SECTIONS ADDRSIG LIBVPX_ADDRSIG HIDDEN_VISIBILITY
do
    require_bool "$name" "${!name}"
done
case "$LIBVPX_RUNTIME_CPU_DETECT" in
    auto|on|off) ;;
    *) error "LIBVPX_RUNTIME_CPU_DETECT must be auto, on or off"; exit 1 ;;
esac

[[ -x "$LIBVPX_SOURCE_DIR/configure" ]] || {
    error "libvpx sources were not found in: $LIBVPX_SOURCE_DIR"
    exit 1
}
LIBVPX_SOURCE_DIR="$(cd "$LIBVPX_SOURCE_DIR" && pwd)"

check_x86_assembler() {
    local abi="$1"
    if [[ "$abi" == "x86" || "$abi" == "x86_64" ]]; then
        if ! command -v nasm >/dev/null 2>&1 && ! command -v yasm >/dev/null 2>&1; then
            error "NASM or Yasm is required for $abi."
            return 1
        fi
    fi
}

libvpx_has_option() {
    grep -qE "^[[:space:]]*$1[[:space:]]*$" "$LIBVPX_SOURCE_DIR/configure"
}

# Bash 3.2 compatible: mutates the dynamically-scoped local vpx_args array in
# build_libvpx_for_abi, avoiding `local -n` which is unavailable on macOS Bash.
libvpx_toggle() {
    local option="$1" enable="$2"
    libvpx_has_option "$option" || return 0
    if [[ "$enable" == "1" ]]; then
        vpx_args+=("--enable-${option//_/-}")
    else
        vpx_args+=("--disable-${option//_/-}")
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

LIBVPX_COMMON_CFLAGS="$COMMON_CFLAGS_BASE"
LIBVPX_SKIP_STRIP=0
if [[ "$ADDRSIG" == "1" && "$LIBVPX_ADDRSIG" == "1" ]]; then
    LIBVPX_COMMON_CFLAGS+=" -faddrsig"
    LIBVPX_SKIP_STRIP=1
fi


copy_generated_header_overlay() {
    local abi="$1"
    local installed_root="$2/include"
    local destination_root
    destination_root="$(abi_include_dir "$abi")"

    local installed relative source destination copied=0
    while IFS= read -r -d '' installed; do
        relative="${installed#$installed_root/}"
        source="$LIBVPX_SOURCE_DIR/$relative"
        if [[ -f "$source" ]] && cmp -s "$source" "$installed"; then
            continue
        fi
        destination="$destination_root/$relative"
        mkdir -p "$(dirname "$destination")"
        cp -f "$installed" "$destination"
        chmod 0644 "$destination"
        copied=$((copied + 1))
        echo "==> generated/ABI header: $destination"
    done < <(find "$installed_root/vpx" -type f -name '*.h' -print0)

    if [[ "$copied" == "0" ]]; then
        echo "==> libvpx $abi: no generated/ABI-dependent public headers"
    fi
}


install_pkgconfig_metadata() {
    local abi="$1" prefix="$2"
    local source_pc="$prefix/lib/pkgconfig/vpx.pc"
    local destination_dir destination overlay
    destination_dir="$(abi_pkgconfig_dir "$abi")"
    destination="$destination_dir/vpx.pc"
    overlay="$(abi_include_dir "$abi")"

    [[ -f "$source_pc" ]] || { error "Missing libvpx pkg-config file: $source_pc"; return 1; }
    mkdir -p "$destination_dir"
    awk -v libdir="$(abi_output_dir "$abi")" -v includedir="$LIBVPX_SOURCE_DIR" -v overlay="$overlay" '
        /^prefix=/ { print "prefix=" libdir; next }
        /^exec_prefix=/ { print "exec_prefix=" libdir; next }
        /^libdir=/ { print "libdir=" libdir; next }
        /^includedir=/ { print "includedir=" includedir; next }
        /^Cflags:/ { print "Cflags: -I" overlay " -I" includedir; next }
        { print }
    ' "$source_pc" > "$destination"
    chmod 0644 "$destination"
    echo "==> $destination"
}

build_libvpx_for_abi() {
    local abi="$1"
    local target vpx_target extra_cflags runtime_cpu_detect prefix build_dir cc cxx symbol
    local -a configure_env=()
    local -a vpx_args=()

    case "$abi" in
        arm64-v8a)
            target="aarch64-linux-android"
            vpx_target="arm64-android-gcc"
            extra_cflags="-O3 -fPIC -march=armv8-a"
            ;;
        armeabi-v7a)
            target="armv7a-linux-androideabi"
            vpx_target="armv7-android-gcc"
            extra_cflags="-O3 -fPIC -march=armv7-a -mfloat-abi=softfp -mfpu=neon"
            ;;
        x86_64)
            target="x86_64-linux-android"
            vpx_target="x86_64-android-gcc"
            extra_cflags="-O3 -fPIC -march=x86-64"
            ;;
        x86)
            target="i686-linux-android"
            vpx_target="x86-android-gcc"
            extra_cflags="-O3 -fPIC -march=i686 -mssse3 -mfpmath=sse"
            ;;
        *) error "Unsupported ABI: $abi"; return 1 ;;
    esac

    extra_cflags="$extra_cflags${LIBVPX_COMMON_CFLAGS:+ $LIBVPX_COMMON_CFLAGS}"
    check_x86_assembler "$abi"

    prefix="$INSTALL_DIR/$abi"
    build_dir="$WORK_DIR/$abi"
    cc="$NDK_TOOLCHAIN/bin/${target}${API}-clang"
    cxx="$NDK_TOOLCHAIN/bin/${target}${API}-clang++"

    rm -rf "$build_dir" "$prefix"
    mkdir -p "$build_dir" "$prefix"

    echo
    echo "========== libvpx: $abi, API $API =========="

    configure_env=(
        "CC=$cc"
        "CXX=$cxx"
        "LD=$cc"
        "AR=$NDK_TOOLCHAIN/bin/llvm-ar"
        "ARFLAGS=crsD"
        "NM=$NDK_TOOLCHAIN/bin/llvm-nm"
        "STRIP=$NDK_TOOLCHAIN/bin/llvm-strip"
        "READELF=$LLVM_READELF_BIN"
    )
    if [[ "$abi" == "arm64-v8a" || "$abi" == "armeabi-v7a" ]]; then
        configure_env+=("AS=$cc")
    fi

    vpx_args=(
        --target="$vpx_target"
        --prefix="$prefix"
        --disable-shared
        --enable-static
        --enable-pic
        --enable-optimizations
        --enable-multithread
        --disable-examples
        --disable-tools
        --disable-docs
        --disable-unit-tests
        --disable-install-bins
        --disable-install-docs
        --disable-debug
        --enable-vp8
        --enable-vp9
        --enable-vp8-decoder
        --enable-vp8-encoder
        --enable-vp9-decoder
        --enable-vp9-encoder
    )

    runtime_cpu_detect="$LIBVPX_RUNTIME_CPU_DETECT"
    if [[ "$runtime_cpu_detect" == "auto" ]]; then
        case "$abi" in
            x86|x86_64) runtime_cpu_detect="on" ;;
            *) runtime_cpu_detect="off" ;;
        esac
    fi
    if [[ "$runtime_cpu_detect" == "on" ]]; then
        vpx_args+=(--enable-runtime-cpu-detect)
    else
        vpx_args+=(--disable-runtime-cpu-detect)
    fi

    libvpx_toggle realtime_only "$LIBVPX_REALTIME_ONLY"
    libvpx_toggle small "$LIBVPX_SMALL"
    libvpx_toggle better_hw_compatibility "$LIBVPX_BETTER_HW_COMPATIBILITY"
    libvpx_toggle webm_io "$LIBVPX_WEBM_IO"

    case "$abi" in
        armeabi-v7a)
            libvpx_toggle neon_asm 0
            ;;
        arm64-v8a)
            libvpx_toggle neon_asm 0
            libvpx_toggle neon_dotprod "$LIBVPX_ARM_EXTENSIONS"
            libvpx_toggle neon_i8mm "$LIBVPX_ARM_EXTENSIONS"
            libvpx_toggle sve "$LIBVPX_ARM_EXTENSIONS"
            libvpx_toggle sve2 "$LIBVPX_ARM_EXTENSIONS"
            ;;
    esac

    [[ -n "$LIBVPX_SIZE_LIMIT" ]] && vpx_args+=(--size-limit="$LIBVPX_SIZE_LIMIT")
    vpx_args+=(--extra-cflags="$extra_cflags" --log="$build_dir/config.log")

    local tool_shim_dir="$build_dir/tool-shims"
    mkdir -p "$tool_shim_dir"
    cat > "$tool_shim_dir/readelf" <<EOF
#!/bin/sh
exec "$LLVM_READELF_BIN" "\$@"
EOF
    chmod +x "$tool_shim_dir/readelf"

    pushd "$build_dir" >/dev/null
    if ! env -u AS -u ASFLAGS \
        "PATH=$tool_shim_dir:$PATH" \
        "${configure_env[@]}" \
        "$LIBVPX_SOURCE_DIR/configure" "${vpx_args[@]}";
    then
        error "libvpx configure failed for $abi. Log: $build_dir/config.log"
        popd >/dev/null
        return 1
    fi

    # libvpx embeds its full configure command in vpx_codec_build_config().
    # Normalize host/check-out paths in that diagnostic string only; configure
    # itself and all compiler arguments above remain untouched.
    local vpx_config_c
    vpx_config_c="$(find "$build_dir" -type f -name vpx_config.c -print -quit)"
    [[ -n "$vpx_config_c" ]] || {
        error "Generated vpx_config.c was not found for $abi"
        popd >/dev/null
        return 1
    }
    normalize_generated_metadata_lines "$vpx_config_c" "--target=" || {
        popd >/dev/null
        return 1
    }

    if [[ "$LIBVPX_SKIP_STRIP" == "1" && -f "$build_dir/config.mk" ]]; then
        if grep -q '^HAVE_GNU_STRIP=' "$build_dir/config.mk"; then
            sed -i.bak 's/^HAVE_GNU_STRIP=.*/HAVE_GNU_STRIP=no/' "$build_dir/config.mk"
            rm -f "$build_dir/config.mk.bak"
        else
            echo 'HAVE_GNU_STRIP=no' >> "$build_dir/config.mk"
        fi
    fi

    if ! make -j"$JOBS" 2>&1 | tee "$build_dir/build.log"; then
        error "libvpx build failed for $abi. Log: $build_dir/build.log"
        popd >/dev/null
        return 1
    fi
    if ! make install 2>&1 | tee "$build_dir/install.log"; then
        error "libvpx install failed for $abi. Log: $build_dir/install.log"
        popd >/dev/null
        return 1
    fi
    popd >/dev/null

    [[ -f "$prefix/lib/libvpx.a" ]] || { error "Missing $prefix/lib/libvpx.a"; return 1; }
    [[ -d "$prefix/include/vpx" ]] || { error "Missing $prefix/include/vpx"; return 1; }

    copy_generated_header_overlay "$abi" "$prefix"
    install_pkgconfig_metadata "$abi" "$prefix"

    for symbol in vpx_codec_vp8_cx vpx_codec_vp8_dx vpx_codec_vp9_cx vpx_codec_vp9_dx; do
        if ! "$NDK_TOOLCHAIN/bin/llvm-nm" -g --defined-only "$prefix/lib/libvpx.a" \
            | grep -E "[[:space:]]${symbol}$" >/dev/null;
        then
            error "Missing libvpx symbol for $abi: $symbol"
            return 1
        fi
    done

    local destination="$(abi_output_dir "$abi")/libvpx.a"
    copy_archive "$prefix/lib/libvpx.a" "$destination"
    echo "==> $destination"
}

for abi in $ABIS; do rm -rf "$(abi_include_dir "$abi")/vpx"; done

print_common_config
echo "libvpx source: $LIBVPX_SOURCE_DIR"
echo "libvpx build root: $BUILD_ROOT"
echo "libvpx flags: $LIBVPX_COMMON_CFLAGS"

rm -rf "$BUILD_ROOT"
mkdir -p "$WORK_DIR" "$INSTALL_DIR"
for abi in $ABIS; do
    build_libvpx_for_abi "$abi"
done
rm -rf "$BUILD_ROOT"

echo
echo "libvpx static build completed."
