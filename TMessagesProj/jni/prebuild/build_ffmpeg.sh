#!/usr/bin/env bash
# Build static FFmpeg libraries for Android using prebuilt libvpx, dav1d and Opus.
# Configuration is kept equivalent to build_ffmpeg_libvpx_dav1d_android_ndk27_merged.sh.
#
# Inputs:
#   jni/prebuild/lib/<ABI>/libvpx.a
#   jni/prebuild/lib/<ABI>/libdav1d.a
#   jni/prebuild/lib/<ABI>/libopus.a
#   jni/prebuild/pkgconfig/<ABI>/{vpx,dav1d}.pc
#   source headers from jni/third_party/{libvpx,dav1d,xiph/opus,ffmpeg}
#   generated/ABI headers overlay from jni/prebuild/include/<ABI>
#
# Output:
#   jni/prebuild/lib/<ABI>/libavcodec.a
#   jni/prebuild/lib/<ABI>/libavformat.a
#   jni/prebuild/lib/<ABI>/libavutil.a
#   jni/prebuild/lib/<ABI>/libswscale.a
#   jni/prebuild/lib/<ABI>/libswresample.a
#   generated/ABI-dependent FFmpeg public headers under jni/prebuild/include/<ABI>/

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

# The merged media build supplied its own exact flags to FFmpeg configure.
unset CFLAGS CXXFLAGS

require_ndk
require_command make
require_command pkg-config

PROJECT_ROOT="$JNI_DIR"
FFMPEG_SOURCE_DIR="${FFMPEG_SOURCE_DIR:-$JNI_DIR/third_party/ffmpeg}"
LIBVPX_SOURCE_DIR="${LIBVPX_SOURCE_DIR:-$JNI_DIR/third_party/libvpx}"
DAV1D_SOURCE_DIR="${DAV1D_SOURCE_DIR:-$JNI_DIR/third_party/dav1d}"
OPUS_SOURCE_DIR="${OPUS_SOURCE_DIR:-$JNI_DIR/third_party/xiph/opus}"
BUILD_ROOT="${FFMPEG_BUILD_ROOT:-$SCRIPT_DIR/build/ffmpeg}"
WORK_DIR="$BUILD_ROOT/work"
INSTALL_DIR="$BUILD_ROOT/install"

ENABLE_SMALL="${ENABLE_SMALL:-1}"
FFMPEG_OPTFLAGS="${FFMPEG_OPTFLAGS:-}"
FFMPEG_LIBVPX_VP8="${FFMPEG_LIBVPX_VP8:-1}"
FFMPEG_PARSERS="${FFMPEG_PARSERS:-1}"

UNWIND_TABLES="${UNWIND_TABLES:-0}"
FRAME_POINTERS="${FRAME_POINTERS:-0}"
FUNCTION_SECTIONS="${FUNCTION_SECTIONS:-1}"
ADDRSIG="${ADDRSIG:-1}"
HIDDEN_VISIBILITY="${HIDDEN_VISIBILITY:-0}"

FFMPEG_RUNTIME_CPUDETECT="${FFMPEG_RUNTIME_CPUDETECT:-on}"
FFMPEG_MMX="${FFMPEG_MMX:-auto}"
FFMPEG_MMXEXT="${FFMPEG_MMXEXT:-auto}"
FFMPEG_SSE="${FFMPEG_SSE:-auto}"
FFMPEG_SSE2="${FFMPEG_SSE2:-auto}"
FFMPEG_SSE3="${FFMPEG_SSE3:-auto}"
FFMPEG_SSSE3="${FFMPEG_SSSE3:-auto}"
FFMPEG_SSE4="${FFMPEG_SSE4:-auto}"
FFMPEG_SSE42="${FFMPEG_SSE42:-auto}"
FFMPEG_AVX="${FFMPEG_AVX:-off}"
FFMPEG_AVX2="${FFMPEG_AVX2:-auto}"
FFMPEG_AVX512="${FFMPEG_AVX512:-auto}"
FFMPEG_FMA3="${FFMPEG_FMA3:-auto}"
FFMPEG_FMA4="${FFMPEG_FMA4:-auto}"
FFMPEG_BMI1="${FFMPEG_BMI1:-auto}"
FFMPEG_BMI2="${FFMPEG_BMI2:-auto}"
FFMPEG_X86_64_MMX="${FFMPEG_X86_64_MMX:-off}"
FFMPEG_X86_64_INLINE_ASM="${FFMPEG_X86_64_INLINE_ASM:-off}"
FFMPEG_X86_64_X86ASM="${FFMPEG_X86_64_X86ASM:-auto}"
FFMPEG_X86_MMX="${FFMPEG_X86_MMX:-off}"
FFMPEG_X86_INLINE_ASM="${FFMPEG_X86_INLINE_ASM:-off}"
FFMPEG_X86_X86ASM="${FFMPEG_X86_X86ASM:-off}"
FFMPEG_X86_64_EXTRA_ISA_CFLAGS="${FFMPEG_X86_64_EXTRA_ISA_CFLAGS:-}"
FFMPEG_X86_EXTRA_ISA_CFLAGS="${FFMPEG_X86_EXTRA_ISA_CFLAGS:-}"

error() { echo "ERROR: $*" >&2; }

require_bool() {
    local name="$1" value="$2"
    if [[ "$value" != "0" && "$value" != "1" ]]; then
        error "$name must be 0 or 1, got: $value"
        exit 1
    fi
}

require_toggle() {
    local name="$1" value="$2"
    case "$value" in
        auto|on|off) ;;
        *) error "$name must be auto, on or off, got: $value"; exit 1 ;;
    esac
}

# Bash 3.2 compatible; configure_args is dynamically scoped by build_ffmpeg_for_abi.
append_configure_toggle() {
    local feature="$1" value="$2"
    case "$value" in
        auto) ;;
        on) configure_args+=("--enable-$feature") ;;
        off) configure_args+=("--disable-$feature") ;;
    esac
}

for name in ENABLE_SMALL FFMPEG_LIBVPX_VP8 FFMPEG_PARSERS UNWIND_TABLES FRAME_POINTERS FUNCTION_SECTIONS ADDRSIG HIDDEN_VISIBILITY; do
    require_bool "$name" "${!name}"
done
for name in \
    FFMPEG_RUNTIME_CPUDETECT FFMPEG_MMX FFMPEG_MMXEXT FFMPEG_SSE \
    FFMPEG_SSE2 FFMPEG_SSE3 FFMPEG_SSSE3 FFMPEG_SSE4 FFMPEG_SSE42 \
    FFMPEG_AVX FFMPEG_AVX2 FFMPEG_AVX512 FFMPEG_FMA3 FFMPEG_FMA4 \
    FFMPEG_BMI1 FFMPEG_BMI2 FFMPEG_X86_64_MMX FFMPEG_X86_64_INLINE_ASM \
    FFMPEG_X86_64_X86ASM FFMPEG_X86_MMX FFMPEG_X86_INLINE_ASM FFMPEG_X86_X86ASM
do
    require_toggle "$name" "${!name}"
done

[[ -x "$FFMPEG_SOURCE_DIR/configure" ]] || { error "FFmpeg sources were not found in: $FFMPEG_SOURCE_DIR"; exit 1; }
[[ -d "$LIBVPX_SOURCE_DIR/vpx" ]] || { error "libvpx headers were not found in: $LIBVPX_SOURCE_DIR/vpx"; exit 1; }
[[ -f "$DAV1D_SOURCE_DIR/include/dav1d/dav1d.h" ]] || { error "dav1d headers were not found in: $DAV1D_SOURCE_DIR/include"; exit 1; }
[[ -f "$OPUS_SOURCE_DIR/include/opus_multistream.h" ]] || { error "Opus headers were not found in: $OPUS_SOURCE_DIR/include"; exit 1; }
FFMPEG_SOURCE_DIR="$(cd "$FFMPEG_SOURCE_DIR" && pwd)"
LIBVPX_SOURCE_DIR="$(cd "$LIBVPX_SOURCE_DIR" && pwd)"
DAV1D_SOURCE_DIR="$(cd "$DAV1D_SOURCE_DIR" && pwd)"
OPUS_SOURCE_DIR="$(cd "$OPUS_SOURCE_DIR" && pwd)"

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

write_opus_pkgconfig() {
    local abi="$1" destination_dir="$2"
    local opus_prefix opus_headers
    opus_prefix="$(abi_output_dir "$abi")"
    opus_headers="$OPUS_SOURCE_DIR/include"
    mkdir -p "$destination_dir"
    cat > "$destination_dir/opus.pc" <<PC
libdir=$opus_prefix
includedir=$opus_headers
Name: Opus
Description: Opus IETF audio codec (prebuilt static)
Version: 1.6.1
Libs: -L\${libdir} -lopus
Libs.private: -lm
Cflags: -I\${includedir}
PC
}

copy_generated_header_overlay() {
    local abi="$1" prefix="$2"
    local installed_root="$prefix/include"
    local destination_root
    destination_root="$(abi_include_dir "$abi")"

    local installed relative source destination copied=0
    while IFS= read -r -d '' installed; do
        relative="${installed#$installed_root/}"
        source="$FFMPEG_SOURCE_DIR/$relative"
        if [[ -f "$source" ]] && cmp -s "$source" "$installed"; then
            continue
        fi
        destination="$destination_root/$relative"
        mkdir -p "$(dirname "$destination")"
        cp -f "$installed" "$destination"
        chmod 0644 "$destination"
        copied=$((copied + 1))
        echo "==> generated/ABI header: $destination"
    done < <(find "$installed_root" -type f -name '*.h' -print0)

    if [[ "$copied" == "0" ]]; then
        echo "==> FFmpeg $abi: no generated/ABI-dependent public headers"
    fi
}

build_ffmpeg_for_abi() {
    local abi="$1"
    local arch target cpu extra_cflags prefix build_dir cc cxx
    local vpx_archive dav1d_archive opus_archive dependency_pc_dir opus_pc_dir overlay
    local components_header enabled_av1_decoders component library destination
    local -a configure_env=()
    local -a configure_args=()
    local -a required_components=()

    case "$abi" in
        arm64-v8a)
            arch="aarch64"; target="aarch64-linux-android"; cpu="armv8-a"; extra_cflags="-march=armv8-a"
            ;;
        armeabi-v7a)
            arch="arm"; target="armv7a-linux-androideabi"; cpu="armv7-a"; extra_cflags="-march=armv7-a -mfloat-abi=softfp -mfpu=neon"
            ;;
        x86_64)
            arch="x86_64"; target="x86_64-linux-android"; cpu="x86-64"
            extra_cflags="-march=x86-64${FFMPEG_X86_64_EXTRA_ISA_CFLAGS:+ $FFMPEG_X86_64_EXTRA_ISA_CFLAGS}"
            ;;
        x86)
            arch="x86"; target="i686-linux-android"; cpu="i686"
            extra_cflags="-march=i686${FFMPEG_X86_EXTRA_ISA_CFLAGS:+ $FFMPEG_X86_EXTRA_ISA_CFLAGS}"
            ;;
        *) error "Unsupported ABI: $abi"; return 1 ;;
    esac

    check_x86_assembler "$abi"

    vpx_archive="$(abi_output_dir "$abi")/libvpx.a"
    dav1d_archive="$(abi_output_dir "$abi")/libdav1d.a"
    opus_archive="$(abi_output_dir "$abi")/libopus.a"
    dependency_pc_dir="$(abi_pkgconfig_dir "$abi")"
    overlay="$(abi_include_dir "$abi")"
    opus_pc_dir="$WORK_DIR/opus-pkgconfig/$abi"

    [[ -f "$vpx_archive" ]] || { error "Missing libvpx for $abi: $vpx_archive (run build_libvpx.sh first)"; return 1; }
    [[ -f "$dav1d_archive" ]] || { error "Missing dav1d for $abi: $dav1d_archive (run build_dav1d.sh first)"; return 1; }
    [[ -f "$opus_archive" ]] || { error "Missing Opus for $abi: $opus_archive (run build_opus.sh first)"; return 1; }
    [[ -f "$dependency_pc_dir/vpx.pc" ]] || { error "Missing vpx.pc for $abi: $dependency_pc_dir/vpx.pc"; return 1; }
    [[ -f "$dependency_pc_dir/dav1d.pc" ]] || { error "Missing dav1d.pc for $abi: $dependency_pc_dir/dav1d.pc"; return 1; }

    write_opus_pkgconfig "$abi" "$opus_pc_dir"

    prefix="$INSTALL_DIR/$abi"
    build_dir="$WORK_DIR/$abi"
    cc="$NDK_TOOLCHAIN/bin/${target}${API}-clang"
    cxx="$NDK_TOOLCHAIN/bin/${target}${API}-clang++"
    rm -rf "$build_dir" "$prefix"
    mkdir -p "$build_dir" "$prefix"

    configure_env=(
        "PKG_CONFIG_PATH=$dependency_pc_dir:$opus_pc_dir${PKG_CONFIG_PATH:+:$PKG_CONFIG_PATH}"
        "PKG_CONFIG_LIBDIR=$dependency_pc_dir:$opus_pc_dir"
        "ARFLAGS=rcD"
    )

    configure_args=(
        --prefix="$prefix"
        --target-os=android
        --arch="$arch"
        --cpu="$cpu"
        --enable-cross-compile
        --cc="$cc"
        --cxx="$cxx"
        --ar="$NDK_TOOLCHAIN/bin/llvm-ar"
        --nm="$NDK_TOOLCHAIN/bin/llvm-nm"
        --ranlib="$NDK_TOOLCHAIN/bin/llvm-ranlib"
        --strip="$NDK_TOOLCHAIN/bin/llvm-strip"
        --sysroot="$NDK_TOOLCHAIN/sysroot"
        --enable-pic
        --enable-static
        --disable-shared
        --enable-optimizations
        --enable-pthreads
        --disable-doc
        --disable-debug
        --disable-programs
        --disable-avdevice
        --disable-avfilter
        --disable-network
        --disable-autodetect
        --disable-everything
        --enable-avcodec
        --enable-avformat
        --enable-avutil
        --enable-swscale
        --enable-swresample
        --enable-protocol=file
        --enable-decoder=h264
        --enable-decoder=hevc
        --enable-decoder=mpeg4
        --enable-decoder=mjpeg
        --enable-decoder=gif
        --enable-decoder=alac
        --enable-decoder=libopus
        --enable-decoder=mp3
        --enable-decoder=aac
        --enable-decoder=flac
        --enable-demuxer=mov
        --enable-demuxer=gif
        --enable-demuxer=ogg
        --enable-demuxer=matroska
        --enable-demuxer=mp3
        --enable-demuxer=aac
        --enable-muxer=matroska
        --enable-bsf=vp9_superframe
        --enable-bsf=vp9_raw_reorder
        --enable-libvpx
        --enable-libdav1d
        --enable-libopus
        --enable-decoder=libdav1d
        --enable-decoder=libvpx_vp9
        --enable-encoder=libvpx_vp9
        --pkg-config-flags=--static
        --disable-zlib
        "--extra-cflags=-fPIC -DANDROID $extra_cflags${COMMON_CFLAGS:+ $COMMON_CFLAGS} -I$overlay -I$LIBVPX_SOURCE_DIR -I$DAV1D_SOURCE_DIR/include -I$OPUS_SOURCE_DIR/include"
        "--extra-ldflags=-Wl,-Bsymbolic -L$(abi_output_dir "$abi")"
        "--extra-libs=-ldav1d -lvpx -lopus -lm -ldl"
    )

    [[ -n "$FFMPEG_OPTFLAGS" ]] && configure_args+=("--optflags=$FFMPEG_OPTFLAGS")

    if [[ "$FFMPEG_PARSERS" == "1" ]]; then
        configure_args+=(
            --enable-parser=h264 --enable-parser=hevc --enable-parser=mpeg4video
            --enable-parser=mpegaudio --enable-parser=aac --enable-parser=opus
            --enable-parser=av1 --enable-parser=gif
        )
    fi
    if [[ "$FFMPEG_LIBVPX_VP8" == "1" ]]; then
        configure_args+=(--enable-decoder=libvpx_vp8 --enable-encoder=libvpx_vp8)
    fi

    if [[ "$abi" == "x86" || "$abi" == "x86_64" ]]; then
        append_configure_toggle runtime-cpudetect "$FFMPEG_RUNTIME_CPUDETECT"
        append_configure_toggle mmx "$FFMPEG_MMX"
        append_configure_toggle mmxext "$FFMPEG_MMXEXT"
        append_configure_toggle sse "$FFMPEG_SSE"
        append_configure_toggle sse2 "$FFMPEG_SSE2"
        append_configure_toggle sse3 "$FFMPEG_SSE3"
        append_configure_toggle ssse3 "$FFMPEG_SSSE3"
        append_configure_toggle sse4 "$FFMPEG_SSE4"
        append_configure_toggle sse42 "$FFMPEG_SSE42"
        append_configure_toggle avx "$FFMPEG_AVX"
        append_configure_toggle avx2 "$FFMPEG_AVX2"
        append_configure_toggle avx512 "$FFMPEG_AVX512"
        append_configure_toggle fma3 "$FFMPEG_FMA3"
        append_configure_toggle fma4 "$FFMPEG_FMA4"
        append_configure_toggle bmi1 "$FFMPEG_BMI1"
        append_configure_toggle bmi2 "$FFMPEG_BMI2"
    fi

    [[ "$ENABLE_SMALL" == "1" ]] && configure_args+=(--enable-small)
    case "$abi" in
        armeabi-v7a) configure_args+=(--enable-neon) ;;
        x86_64)
            append_configure_toggle mmx "$FFMPEG_X86_64_MMX"
            append_configure_toggle inline-asm "$FFMPEG_X86_64_INLINE_ASM"
            append_configure_toggle x86asm "$FFMPEG_X86_64_X86ASM"
            ;;
        x86)
            append_configure_toggle mmx "$FFMPEG_X86_MMX"
            append_configure_toggle inline-asm "$FFMPEG_X86_INLINE_ASM"
            append_configure_toggle x86asm "$FFMPEG_X86_X86ASM"
            ;;
    esac

    echo
    echo "========== FFmpeg: $abi, API $API =========="
    pushd "$build_dir" >/dev/null
    if ! env "${configure_env[@]}" "$FFMPEG_SOURCE_DIR/configure" "${configure_args[@]}"; then
        error "FFmpeg configure failed for $abi. Log: $build_dir/ffbuild/config.log"
        popd >/dev/null
        return 1
    fi

    # FFmpeg embeds the literal configure command in every library through
    # FFMPEG_CONFIGURATION. Normalize only that diagnostic macro so Linux and
    # macOS don't differ merely because --cc/--sysroot/--prefix contain host
    # paths. The actual configure/compiler invocation is unchanged.
    normalize_generated_metadata_lines "$build_dir/config.h" "#define FFMPEG_CONFIGURATION " || {
        popd >/dev/null
        return 1
    }

    components_header="$build_dir/config_components.h"
    [[ -f "$components_header" ]] || components_header="$build_dir/config.h"
    required_components=(CONFIG_LIBDAV1D_DECODER CONFIG_LIBVPX_VP9_DECODER CONFIG_LIBVPX_VP9_ENCODER CONFIG_LIBOPUS_DECODER)
    if [[ "$FFMPEG_LIBVPX_VP8" == "1" ]]; then
        required_components+=(CONFIG_LIBVPX_VP8_DECODER CONFIG_LIBVPX_VP8_ENCODER)
    fi
    for component in "${required_components[@]}"; do
        if ! grep -Eq "^#define[[:space:]]+$component[[:space:]]+1$" "$components_header"; then
            error "FFmpeg component is disabled for $abi: $component"
            popd >/dev/null
            return 1
        fi
    done
    for component in CONFIG_VP8_DECODER CONFIG_VP9_DECODER CONFIG_AV1_DECODER; do
        if ! grep -Eq "^#define[[:space:]]+$component[[:space:]]+0$" "$components_header"; then
            error "Built-in FFmpeg decoder is unexpectedly enabled for $abi: $component"
            popd >/dev/null
            return 1
        fi
    done
    enabled_av1_decoders="$(awk '/^#define[[:space:]]+CONFIG_.*(AV1|DAV1D).*_DECODER[[:space:]]+1$/ { print $2 }' "$components_header" | LC_ALL=C sort)"
    if [[ "$enabled_av1_decoders" != "CONFIG_LIBDAV1D_DECODER" ]]; then
        error "Unexpected enabled AV1 decoders for $abi: ${enabled_av1_decoders:-<none>}"
        popd >/dev/null
        return 1
    fi

    if ! make -j"$JOBS" 2>&1 | tee "$build_dir/build.log"; then
        error "FFmpeg build failed for $abi. Log: $build_dir/build.log"
        popd >/dev/null
        return 1
    fi
    if ! make install 2>&1 | tee "$build_dir/install.log"; then
        error "FFmpeg install failed for $abi. Log: $build_dir/install.log"
        popd >/dev/null
        return 1
    fi
    popd >/dev/null

    copy_generated_header_overlay "$abi" "$prefix"

    for library in libavcodec.a libavformat.a libavutil.a libswscale.a libswresample.a; do
        [[ -f "$prefix/lib/$library" ]] || { error "Missing FFmpeg library for $abi: $prefix/lib/$library"; return 1; }
        destination="$(abi_output_dir "$abi")/$library"
        copy_archive "$prefix/lib/$library" "$destination"
        echo "==> $destination"
    done
}

for abi in $ABIS; do
    overlay="$(abi_include_dir "$abi")"
    rm -rf "$overlay/libavcodec" "$overlay/libavformat" "$overlay/libavutil" "$overlay/libswscale" "$overlay/libswresample"
done

print_common_config
echo "FFmpeg source: $FFMPEG_SOURCE_DIR"
echo "FFmpeg build root: $BUILD_ROOT"
echo "FFmpeg flags: $COMMON_CFLAGS"

rm -rf "$BUILD_ROOT"
mkdir -p "$WORK_DIR" "$INSTALL_DIR"
for abi in $ABIS; do
    build_ffmpeg_for_abi "$abi"
done
rm -rf "$BUILD_ROOT"

echo
echo "==> Removing temporary pkg-config metadata"
rm -rf "$PKGCONFIG_DIR"

echo
echo "FFmpeg static build completed."
