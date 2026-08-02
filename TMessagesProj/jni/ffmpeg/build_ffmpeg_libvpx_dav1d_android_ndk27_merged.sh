#!/usr/bin/env bash
set -Eeuo pipefail

# Reproducible-build environment. SOURCE_DATE_EPOCH may be overridden by the
# caller, but defaults to a fixed timestamp so rebuilding the same sources does
# not depend on the current date or time.
SOURCE_DATE_EPOCH="${SOURCE_DATE_EPOCH:-946684800}"
export SOURCE_DATE_EPOCH
export TZ=UTC
export LC_ALL=C
export LANG=C
export PYTHONHASHSEED=0
export ZERO_AR_DATE=1
umask 022

# Build libvpx, dav1d and FFmpeg from source for Android.
#
# Expected layout:
#   project/
#   ├── build_ffmpeg_libvpx_dav1d_android_ndk27.sh
#   └── ../third_party/
#       ├── libvpx/
#       │   └── configure
#       ├── dav1d/
#       │   └── meson.build
#       └── ffmpeg/
#           └── configure
#
# Default output next to this script:
#   include/                   # FFmpeg headers + libvpx/ + ABI dispatchers
#   arm64-v8a/                 # static libraries for arm64-v8a
#   armeabi-v7a/               # static libraries for armeabi-v7a
#   x86_64/                    # static libraries for x86_64
#   x86/                       # static libraries for x86
#
# Intermediate build/install files remain under build_android/.
#
# Common overrides:
#   ANDROID_NDK_HOME=/path/to/ndk/27.2.12479018
#   API=21
#   ABIS="arm64-v8a armeabi-v7a x86_64 x86"
#   JOBS=16
#   SOURCE_DATE_EPOCH=946684800
#   LIBVPX_SOURCE_DIR=/path/to/libvpx
#   DAV1D_SOURCE_DIR=/path/to/dav1d
#   FFMPEG_SOURCE_DIR=/path/to/ffmpeg
#   BUILD_ROOT=/path/to/build_android
#   ENABLE_SMALL=1
#   BUILD_LIBVPX=1
#   BUILD_DAV1D=1
#   BUILD_FFMPEG=1
#   CLEAN=1
#   PACKAGE_OUTPUT=1
#   PACKAGE_DIR=/path/to/output-root

NDK_VERSION="27.2.12479018"
API="${API:-21}"
ABIS="${ABIS:-arm64-v8a armeabi-v7a x86_64 x86}"
ENABLE_SMALL="${ENABLE_SMALL:-1}"
BUILD_LIBVPX="${BUILD_LIBVPX:-1}"
BUILD_DAV1D="${BUILD_DAV1D:-1}"
BUILD_FFMPEG="${BUILD_FFMPEG:-1}"
CLEAN="${CLEAN:-1}"
PACKAGE_OUTPUT="${PACKAGE_OUTPUT:-1}"

# ============================================================================
# Parity with the legacy build (FFmpeg 4.4.5 / libvpx 1.14 / NDK r10e GCC 4.9)
#
# Defaults below reproduce the component and code-size profile of the old
# prebuilt archives. Each switch documents what the legacy build did.
# ============================================================================

# Legacy build-vpx.sh used: --enable-realtime-only --enable-small
# --enable-better-hw-compatibility --disable-webm-io --disable-neon-asm
# --disable-neon-dotprod --disable-runtime-cpu-detect (non-x86).
# Dropping --enable-realtime-only alone pulls in the whole VP9 two-pass
# encoder (firstpass, mbgraph, alt-ref, adaptive quantization, temporal
# filter) — roughly 300 KB of code that a realtime-only encoder never calls.
LIBVPX_REALTIME_ONLY="${LIBVPX_REALTIME_ONLY:-1}"
LIBVPX_SMALL="${LIBVPX_SMALL:-1}"
LIBVPX_BETTER_HW_COMPATIBILITY="${LIBVPX_BETTER_HW_COMPATIBILITY:-1}"
LIBVPX_WEBM_IO="${LIBVPX_WEBM_IO:-0}"

# auto = enabled for x86/x86_64, disabled for ARM (legacy behaviour).
# Can also be forced to on/off.
LIBVPX_RUNTIME_CPU_DETECT="${LIBVPX_RUNTIME_CPU_DETECT:-auto}"

# AArch64 ISA extension kernels (dotprod / i8mm / SVE / SVE2). With
# runtime CPU detection these are all compiled in on top of the baseline
# NEON paths. The legacy build had none of them.
# Set to 1 to trade ~150 KB for measurably faster VP9 decode on modern SoCs.
LIBVPX_ARM_EXTENSIONS="${LIBVPX_ARM_EXTENSIONS:-0}"

# Decoder input size guard. The legacy build had none; kept here as a
# deliberate deviation (cheap DoS protection on malformed streams).
# Set to an empty value to remove the limit.
LIBVPX_SIZE_LIMIT="${LIBVPX_SIZE_LIMIT:-4096x4096}"

# The legacy FFmpeg build passed -Os in --extra-cflags but had no
# --enable-small, so configure appended its own -O3 afterwards and the code
# was actually built at -O3 without CONFIG_SMALL. Keeping ENABLE_SMALL=1 here
# is deliberate: it is the closest match to the old archive sizes and the
# only reason libavcodec did not grow across the version bump.
# For a bit-for-bit-intent legacy reproduction use:
#   ENABLE_SMALL=0 FFMPEG_OPTFLAGS=-O3
# Empty value means: let configure pick (-Os with --enable-small, else -O3).
FFMPEG_OPTFLAGS="${FFMPEG_OPTFLAGS:-}"

# The legacy build wrapped VP9 only (--enable-decoder=libvpx_vp9 /
# --enable-encoder=libvpx_vp9). VP8 wrappers are an addition over it, kept on
# deliberately. libvpx itself builds VP8 either way, so this only adds the
# thin libvpxdec/libvpxenc wrapper code in libavcodec.
# Set to 0 for exact legacy parity.
FFMPEG_LIBVPX_VP8="${FFMPEG_LIBVPX_VP8:-1}"

# Legacy build enabled no parsers at all. They are kept on by default because
# removing them breaks bitstream parsing paths that the old tree worked around
# elsewhere; set to 0 for exact legacy parity.
FFMPEG_PARSERS="${FFMPEG_PARSERS:-1}"

# GCC 4.9 on AArch64 emitted no .eh_frame; Clang does by default, which is
# ~370 KB across the FFmpeg/libvpx/dav1d archives and the single largest
# category that actually survives into the linked .so.
# 0 means native crash backtraces will not unwind accurately through these
# frames.
UNWIND_TABLES="${UNWIND_TABLES:-0}"

# Frame pointers are the fallback simpleperf and libunwindstack use when there
# are no unwind tables. Only useful if you care about profiling or crash
# backtraces inside FFmpeg/libvpx/dav1d; costs a register and a few bytes of
# prologue per leaf function otherwise.
FRAME_POINTERS="${FRAME_POINTERS:-0}"

# Not present in either build. Emits per-function/per-object sections so the
# final .so link can drop unreferenced code with -Wl,--gc-sections.
# Requires -Wl,--gc-sections on the consuming target (the Telegram jni
# CMakeLists already sets it).
FUNCTION_SECTIONS="${FUNCTION_SECTIONS:-1}"

# Address-significance table, consumed by -Wl,--icf=safe to decide which
# sections are safe to fold. Clang enables -faddrsig by default on ELF but
# explicitly NOT on Android (clang/lib/Driver/ToolChains/Clang.cpp: the default
# is gated on !TC.getTriple().isAndroid()), so without this flag every section
# is treated as address-significant and --icf=safe folds nothing.
# Harmless if the consuming link does not use ICF. FFmpeg does not strip
# installed static libraries, so the section survives into the .a.
ADDRSIG="${ADDRSIG:-1}"

# libvpx is the exception. Its build produces libvpx_g.a and then derives
# libvpx.a from it (build/make/Makefile):
#     %.a: %_g.a
#         $(STRIP) --strip-debug -o $@ $<
# llvm-strip keeps the SHT_LLVM_ADDRSIG section but zeroes its sh_link,
# because rewriting the symbol table invalidates the symbol indices the table
# refers to. lld then reports
#     --icf=safe conservatively ignores SHT_LLVM_ADDRSIG [index N] with
#     sh_link=0 (likely created using objcopy or ld -r)
# which the NDK's default -Wl,--fatal-warnings turns into a link error.
# So -faddrsig is withheld from libvpx by default.
#
# Setting this to 1 passes it anyway and forces HAVE_GNU_STRIP=no in libvpx's
# config.mk so the derivation degrades to a plain copy and the table stays
# valid. Cost: libvpx.a keeps its local symbols and grows on disk. That is
# .symtab/.strtab only — the linked .so is unaffected.
LIBVPX_ADDRSIG="${LIBVPX_ADDRSIG:-0}"

# Compile the prebuilt archives with hidden default visibility.
# The correct place to keep these symbols out of the consuming .so's .dynsym
# is -Wl,--exclude-libs on the link, and lld matches those entries by archive
# BASENAME (path::filename), so absolute paths in that list never match.
# Enable this only as a fallback if fixing the link flags is not an option:
# exported symbols are GC roots, so leaking them defeats --gc-sections.
# Note: it makes the archives unusable by anything that expects to re-export
# the FFmpeg/libvpx/dav1d ABI from the resulting .so.
HIDDEN_VISIBILITY="${HIDDEN_VISIBILITY:-0}"

# ============================================================================
# FFmpeg CPU/assembly experiment switches
# Accepted values: auto, on, off.
# Defaults reproduce the old FFmpeg build script.
# ============================================================================
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

JOBS="${JOBS:-$(getconf _NPROCESSORS_ONLN 2>/dev/null || sysctl -n hw.logicalcpu 2>/dev/null || echo 8)}"

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
THIRD_PARTY_DIR="${THIRD_PARTY_DIR:-$PROJECT_ROOT/third_party}"
LIBVPX_SOURCE_DIR="${LIBVPX_SOURCE_DIR:-$THIRD_PARTY_DIR/libvpx}"
DAV1D_SOURCE_DIR="${DAV1D_SOURCE_DIR:-$THIRD_PARTY_DIR/dav1d}"
FFMPEG_SOURCE_DIR="${FFMPEG_SOURCE_DIR:-$THIRD_PARTY_DIR/ffmpeg}"
BUILD_ROOT="${BUILD_ROOT:-$SCRIPT_DIR/build_android}"

LIBVPX_WORK_DIR="$BUILD_ROOT/work/libvpx"
DAV1D_WORK_DIR="$BUILD_ROOT/work/dav1d"
FFMPEG_WORK_DIR="$BUILD_ROOT/work/ffmpeg"
LIBVPX_OUTPUT_DIR="$BUILD_ROOT/libvpx"
DAV1D_OUTPUT_DIR="$BUILD_ROOT/dav1d"
FFMPEG_OUTPUT_DIR="$BUILD_ROOT/ffmpeg"
PACKAGE_DIR="${PACKAGE_DIR:-$SCRIPT_DIR}"

error() {
    echo "ERROR: $*" >&2
}

require_bool() {
    local name="$1"
    local value="$2"
    if [[ "$value" != "0" && "$value" != "1" ]]; then
        error "$name must be 0 or 1, got: $value"
        exit 1
    fi
}

require_toggle() {
    local name="$1"
    local value="$2"
    case "$value" in
        auto|on|off) ;;
        *) error "$name must be auto, on or off, got: $value"; exit 1 ;;
    esac
}

append_configure_toggle() {
    local -n destination="$1"
    local feature="$2"
    local value="$3"
    case "$value" in
        auto) ;;
        on) destination+=("--enable-$feature") ;;
        off) destination+=("--disable-$feature") ;;
    esac
}

require_bool ENABLE_SMALL "$ENABLE_SMALL"
require_bool BUILD_LIBVPX "$BUILD_LIBVPX"
require_bool BUILD_DAV1D "$BUILD_DAV1D"
require_bool BUILD_FFMPEG "$BUILD_FFMPEG"
require_bool CLEAN "$CLEAN"
require_bool PACKAGE_OUTPUT "$PACKAGE_OUTPUT"
require_bool LIBVPX_REALTIME_ONLY "$LIBVPX_REALTIME_ONLY"
require_bool LIBVPX_SMALL "$LIBVPX_SMALL"
require_bool LIBVPX_BETTER_HW_COMPATIBILITY "$LIBVPX_BETTER_HW_COMPATIBILITY"
require_bool LIBVPX_WEBM_IO "$LIBVPX_WEBM_IO"
require_bool LIBVPX_ARM_EXTENSIONS "$LIBVPX_ARM_EXTENSIONS"
require_bool FFMPEG_LIBVPX_VP8 "$FFMPEG_LIBVPX_VP8"
require_bool FFMPEG_PARSERS "$FFMPEG_PARSERS"
require_bool UNWIND_TABLES "$UNWIND_TABLES"
require_bool FRAME_POINTERS "$FRAME_POINTERS"
require_bool FUNCTION_SECTIONS "$FUNCTION_SECTIONS"
require_bool ADDRSIG "$ADDRSIG"
require_bool LIBVPX_ADDRSIG "$LIBVPX_ADDRSIG"
require_bool HIDDEN_VISIBILITY "$HIDDEN_VISIBILITY"
require_toggle LIBVPX_RUNTIME_CPU_DETECT "$LIBVPX_RUNTIME_CPU_DETECT"

# Compiler flags shared by libvpx, dav1d and FFmpeg. -faddrsig is handled
# separately because libvpx's strip step invalidates the table it produces.
COMMON_CFLAGS_BASE=""

# Remove host-specific absolute paths from DWARF, __FILE__, diagnostics and
# compiler-produced strings. Both source and build trees are mapped to stable
# virtual roots so the output is independent of checkout location.
COMMON_CFLAGS_BASE+=" -ffile-prefix-map=$PROJECT_ROOT=/src"
COMMON_CFLAGS_BASE+=" -fdebug-prefix-map=$PROJECT_ROOT=/src"
COMMON_CFLAGS_BASE+=" -fmacro-prefix-map=$PROJECT_ROOT=/src"
COMMON_CFLAGS_BASE+=" -ffile-prefix-map=$BUILD_ROOT=/build"
COMMON_CFLAGS_BASE+=" -fdebug-prefix-map=$BUILD_ROOT=/build"
COMMON_CFLAGS_BASE+=" -fmacro-prefix-map=$BUILD_ROOT=/build"
COMMON_CFLAGS_BASE+=" -fdebug-compilation-dir=."
if [[ "$FUNCTION_SECTIONS" == "1" ]]; then
    COMMON_CFLAGS_BASE+=" -ffunction-sections -fdata-sections"
fi
if [[ "$HIDDEN_VISIBILITY" == "1" ]]; then
    COMMON_CFLAGS_BASE+=" -fvisibility=hidden"
fi
if [[ "$UNWIND_TABLES" == "0" ]]; then
    COMMON_CFLAGS_BASE+=" -fno-asynchronous-unwind-tables -fno-unwind-tables"
fi
if [[ "$FRAME_POINTERS" == "1" ]]; then
    COMMON_CFLAGS_BASE+=" -fno-omit-frame-pointer"
else
    COMMON_CFLAGS_BASE+=" -fomit-frame-pointer"
fi
COMMON_CFLAGS_BASE="${COMMON_CFLAGS_BASE# }"

COMMON_CFLAGS="$COMMON_CFLAGS_BASE"
LIBVPX_COMMON_CFLAGS="$COMMON_CFLAGS_BASE"
LIBVPX_SKIP_STRIP=0
if [[ "$ADDRSIG" == "1" ]]; then
    COMMON_CFLAGS+=" -faddrsig"
    if [[ "$LIBVPX_ADDRSIG" == "1" ]]; then
        LIBVPX_COMMON_CFLAGS+=" -faddrsig"
        LIBVPX_SKIP_STRIP=1
    fi
fi

for toggle_name in \
    FFMPEG_RUNTIME_CPUDETECT FFMPEG_MMX FFMPEG_MMXEXT FFMPEG_SSE \
    FFMPEG_SSE2 FFMPEG_SSE3 FFMPEG_SSSE3 FFMPEG_SSE4 FFMPEG_SSE42 \
    FFMPEG_AVX FFMPEG_AVX2 FFMPEG_AVX512 FFMPEG_FMA3 FFMPEG_FMA4 \
    FFMPEG_BMI1 FFMPEG_BMI2 FFMPEG_X86_64_MMX \
    FFMPEG_X86_64_INLINE_ASM FFMPEG_X86_64_X86ASM FFMPEG_X86_MMX \
    FFMPEG_X86_INLINE_ASM FFMPEG_X86_X86ASM
do
    require_toggle "$toggle_name" "${!toggle_name}"
done

if [[ "$BUILD_LIBVPX" == "0" && "$BUILD_DAV1D" == "0" && "$BUILD_FFMPEG" == "0" ]]; then
    error "BUILD_LIBVPX, BUILD_DAV1D and BUILD_FFMPEG are all disabled."
    exit 1
fi

# Locate Android NDK.
if [[ -z "${ANDROID_NDK_HOME:-}" ]]; then
    SDK_ROOT="${ANDROID_SDK_ROOT:-${ANDROID_HOME:-}}"

    if [[ -z "$SDK_ROOT" ]]; then
        for candidate in "$HOME/Android/Sdk" "$HOME/Library/Android/sdk"; do
            if [[ -d "$candidate/ndk/$NDK_VERSION" ]]; then
                SDK_ROOT="$candidate"
                break
            fi
        done
    fi

    if [[ -n "$SDK_ROOT" ]]; then
        ANDROID_NDK_HOME="$SDK_ROOT/ndk/$NDK_VERSION"
    fi
fi

if [[ -z "${ANDROID_NDK_HOME:-}" || ! -d "$ANDROID_NDK_HOME" ]]; then
    error "Android NDK $NDK_VERSION was not found."
    echo "Set ANDROID_NDK_HOME, for example:" >&2
    echo "  ANDROID_NDK_HOME=\"\$HOME/Android/Sdk/ndk/$NDK_VERSION\" $0" >&2
    exit 1
fi
ANDROID_NDK_HOME="$(cd "$ANDROID_NDK_HOME" && pwd)"

if [[ "$BUILD_LIBVPX" == "1" && ! -x "$LIBVPX_SOURCE_DIR/configure" ]]; then
    error "libvpx sources were not found in: $LIBVPX_SOURCE_DIR"
    exit 1
fi

if [[ "$BUILD_DAV1D" == "1" && ! -f "$DAV1D_SOURCE_DIR/meson.build" ]]; then
    error "dav1d sources were not found in: $DAV1D_SOURCE_DIR"
    exit 1
fi

if [[ "$BUILD_FFMPEG" == "1" && ! -x "$FFMPEG_SOURCE_DIR/configure" ]]; then
    error "FFmpeg sources were not found in: $FFMPEG_SOURCE_DIR"
    exit 1
fi

[[ -d "$LIBVPX_SOURCE_DIR" ]] && LIBVPX_SOURCE_DIR="$(cd "$LIBVPX_SOURCE_DIR" && pwd)"
[[ -d "$DAV1D_SOURCE_DIR" ]] && DAV1D_SOURCE_DIR="$(cd "$DAV1D_SOURCE_DIR" && pwd)"
[[ -d "$FFMPEG_SOURCE_DIR" ]] && FFMPEG_SOURCE_DIR="$(cd "$FFMPEG_SOURCE_DIR" && pwd)"

case "$(uname -s)" in
    Linux)
        case "$(uname -m)" in
            x86_64) HOST_TAG="linux-x86_64" ;;
            aarch64|arm64) HOST_TAG="linux-aarch64" ;;
            *) error "Unsupported Linux host architecture: $(uname -m)"; exit 1 ;;
        esac
        ;;
    Darwin)
        case "$(uname -m)" in
            x86_64) HOST_TAG="darwin-x86_64" ;;
            arm64)
                if [[ -d "$ANDROID_NDK_HOME/toolchains/llvm/prebuilt/darwin-arm64" ]]; then
                    HOST_TAG="darwin-arm64"
                else
                    HOST_TAG="darwin-x86_64"
                fi
                ;;
            *) error "Unsupported macOS host architecture: $(uname -m)"; exit 1 ;;
        esac
        ;;
    *) error "Only Linux and macOS hosts are supported."; exit 1 ;;
esac

TOOLCHAIN="$ANDROID_NDK_HOME/toolchains/llvm/prebuilt/$HOST_TAG"
if [[ ! -d "$TOOLCHAIN" ]]; then
    error "LLVM toolchain was not found: $TOOLCHAIN"
    exit 1
fi

if [[ "$BUILD_DAV1D" == "1" ]]; then
    for tool in meson ninja python3 pkg-config; do
        if ! command -v "$tool" >/dev/null 2>&1; then
            error "Required dav1d build tool is missing: $tool"
            exit 1
        fi
    done
fi

mkdir -p "$LIBVPX_WORK_DIR" "$DAV1D_WORK_DIR" "$FFMPEG_WORK_DIR" \
    "$LIBVPX_OUTPUT_DIR" "$DAV1D_OUTPUT_DIR" "$FFMPEG_OUTPUT_DIR"

check_x86_assembler() {
    local abi="$1"
    if [[ "$abi" == "x86" || "$abi" == "x86_64" ]]; then
        if ! command -v nasm >/dev/null 2>&1 && ! command -v yasm >/dev/null 2>&1; then
            error "NASM or Yasm is required for $abi."
            echo "Install NASM, for example: sudo apt install nasm" >&2
            return 1
        fi
    fi
}

# libvpx renames/removes configure toggles between releases (neon_i8mm, sve
# and sve2 do not exist before 1.15, neon_asm is ARM-only). Probe the option
# list in the configure script instead of failing on an unknown flag.
libvpx_has_option() {
    grep -qE "^[[:space:]]*$1[[:space:]]*$" "$LIBVPX_SOURCE_DIR/configure"
}

# usage: libvpx_toggle <array-name> <underscored_option> <0|1>
libvpx_toggle() {
    local -n destination="$1"
    local option="$2"
    local enable="$3"
    libvpx_has_option "$option" || return 0
    if [[ "$enable" == "1" ]]; then
        destination+=("--enable-${option//_/-}")
    else
        destination+=("--disable-${option//_/-}")
    fi
}

build_libvpx_for_abi() {
    local abi="$1"
    local target vpx_target extra_cflags

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

    local prefix="$LIBVPX_OUTPUT_DIR/$abi"
    local build_dir="$LIBVPX_WORK_DIR/$abi"
    local cc="$TOOLCHAIN/bin/${target}${API}-clang"
    local cxx="$TOOLCHAIN/bin/${target}${API}-clang++"

    if [[ "$CLEAN" == "1" ]]; then
        rm -rf "$build_dir" "$prefix"
    fi
    mkdir -p "$build_dir" "$prefix"

    echo
    echo "========== libvpx: $abi, API $API =========="

    local -a configure_env=(
        "CC=$cc"
        "CXX=$cxx"
        "LD=$cc"
        "AR=$TOOLCHAIN/bin/llvm-ar"
        "ARFLAGS=crsD"
        "NM=$TOOLCHAIN/bin/llvm-nm"
        "STRIP=$TOOLCHAIN/bin/llvm-strip"
    )

    # ARM .S uses Clang integrated assembler. x86 .asm must use NASM/Yasm.
    if [[ "$abi" == "arm64-v8a" || "$abi" == "armeabi-v7a" ]]; then
        configure_env+=("AS=$cc")
    fi

    local -a vpx_args=(
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

    # Runtime CPU detection: legacy build enabled it on x86 only.
    local runtime_cpu_detect="$LIBVPX_RUNTIME_CPU_DETECT"
    if [[ "$runtime_cpu_detect" == "auto" ]]; then
        case "$abi" in
            x86|x86_64) runtime_cpu_detect="on" ;;
            *)          runtime_cpu_detect="off" ;;
        esac
    fi
    if [[ "$runtime_cpu_detect" == "on" ]]; then
        vpx_args+=(--enable-runtime-cpu-detect)
    else
        vpx_args+=(--disable-runtime-cpu-detect)
    fi

    libvpx_toggle vpx_args realtime_only          "$LIBVPX_REALTIME_ONLY"
    libvpx_toggle vpx_args small                  "$LIBVPX_SMALL"
    libvpx_toggle vpx_args better_hw_compatibility "$LIBVPX_BETTER_HW_COMPATIBILITY"
    libvpx_toggle vpx_args webm_io                "$LIBVPX_WEBM_IO"

    case "$abi" in
        armeabi-v7a)
            libvpx_toggle vpx_args neon_asm 0
            ;;
        arm64-v8a)
            libvpx_toggle vpx_args neon_asm      0
            libvpx_toggle vpx_args neon_dotprod  "$LIBVPX_ARM_EXTENSIONS"
            libvpx_toggle vpx_args neon_i8mm     "$LIBVPX_ARM_EXTENSIONS"
            libvpx_toggle vpx_args sve           "$LIBVPX_ARM_EXTENSIONS"
            libvpx_toggle vpx_args sve2          "$LIBVPX_ARM_EXTENSIONS"
            ;;
    esac

    if [[ -n "$LIBVPX_SIZE_LIMIT" ]]; then
        vpx_args+=(--size-limit="$LIBVPX_SIZE_LIMIT")
    fi

    vpx_args+=(
        --extra-cflags="$extra_cflags"
        --log="$build_dir/config.log"
    )

    pushd "$build_dir" >/dev/null

    if ! env -u AS -u ASFLAGS "${configure_env[@]}" \
        "$LIBVPX_SOURCE_DIR/configure" "${vpx_args[@]}"
    then
        error "libvpx configure failed for $abi. Log: $build_dir/config.log"
        popd >/dev/null
        return 1
    fi

    # `%.a: %_g.a` runs `$(STRIP) --strip-debug`, which zeroes sh_link on
    # SHT_LLVM_ADDRSIG and makes lld reject the table under --icf=safe.
    # Degrade the rule to a plain copy so the table stays usable.
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

    # WebRTC links directly against both VP8 and VP9 encoder/decoder interfaces.
    local symbol
    for symbol in \
        vpx_codec_vp8_cx \
        vpx_codec_vp8_dx \
        vpx_codec_vp9_cx \
        vpx_codec_vp9_dx
    do
        if ! "$TOOLCHAIN/bin/llvm-nm" -g --defined-only "$prefix/lib/libvpx.a" \
            | grep -E "[[:space:]]${symbol}$" >/dev/null;
        then
            error "Missing libvpx symbol for $abi: $symbol"
            return 1
        fi
    done
}


write_dav1d_cross_file() {
    local abi="$1"
    local file="$2"
    local clang_target cpu_family cpu extra_c_args

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

    local cc="$TOOLCHAIN/bin/${clang_target}-clang"
    [[ -x "$cc" ]] || { error "dav1d compiler was not found: $cc"; return 1; }

    # Meson wants a quoted list, so turn COMMON_CFLAGS into "'a', 'b'".
    local shared_c_args=""
    local flag
    for flag in $COMMON_CFLAGS; do
        shared_c_args+=", '$flag'"
    done

    cat > "$file" <<EOF
[binaries]
c = '$cc'
ar = '$TOOLCHAIN/bin/llvm-ar'
strip = '$TOOLCHAIN/bin/llvm-strip'
ranlib = '$TOOLCHAIN/bin/llvm-ranlib'
nm = '$TOOLCHAIN/bin/llvm-nm'
objcopy = '$TOOLCHAIN/bin/llvm-objcopy'
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
EOF
}

verify_dav1d_archive() {
    local abi="$1"
    local library="$2"

    [[ -f "$library" ]] || { error "Missing dav1d archive for $abi: $library"; return 1; }

    if ! "$TOOLCHAIN/bin/llvm-nm" -g --defined-only "$library" \
        | awk '$NF == "dav1d_open" { found = 1 } END { exit found ? 0 : 1 }';
    then
        error "dav1d_open was not found in $library"
        return 1
    fi
}

build_dav1d_for_abi() {
    local abi="$1"
    check_x86_assembler "$abi"

    local prefix="$DAV1D_OUTPUT_DIR/$abi"
    local build_dir="$DAV1D_WORK_DIR/$abi"
    local cross_file="$DAV1D_WORK_DIR/crossfiles/$abi.ini"

    if [[ "$CLEAN" == "1" ]]; then
        rm -rf "$build_dir" "$prefix"
    fi
    mkdir -p "$build_dir" "$prefix" "$(dirname "$cross_file")"
    write_dav1d_cross_file "$abi" "$cross_file"

    echo
    echo "========== dav1d: $abi, API $API =========="

    if ! meson setup "$build_dir" "$DAV1D_SOURCE_DIR" \
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

    if ! ninja -C "$build_dir" -j "$JOBS" 2>&1 | tee "$build_dir/build.log"; then
        error "dav1d build failed for $abi. Log: $build_dir/build.log"
        return 1
    fi

    if ! ninja -C "$build_dir" install 2>&1 | tee "$build_dir/install.log"; then
        error "dav1d install failed for $abi. Log: $build_dir/install.log"
        return 1
    fi

    verify_dav1d_archive "$abi" "$prefix/lib/libdav1d.a"
    [[ -f "$prefix/include/dav1d/dav1d.h" ]] || { error "Missing dav1d headers for $abi"; return 1; }
    [[ -f "$prefix/lib/pkgconfig/dav1d.pc" ]] || { error "Missing dav1d.pc for $abi"; return 1; }
}

build_ffmpeg_for_abi() {
    local abi="$1"
    local arch target cpu extra_cflags
    local -a abi_flags=()

    case "$abi" in
        arm64-v8a)
            arch="aarch64"
            target="aarch64-linux-android"
            cpu="armv8-a"
            extra_cflags="-march=armv8-a"
            ;;
        armeabi-v7a)
            arch="arm"
            target="armv7a-linux-androideabi"
            cpu="armv7-a"
            extra_cflags="-march=armv7-a -mfloat-abi=softfp -mfpu=neon"
            abi_flags+=(--enable-neon)
            ;;
        x86_64)
            arch="x86_64"
            target="x86_64-linux-android"
            cpu="x86-64"
            extra_cflags="-march=x86-64${FFMPEG_X86_64_EXTRA_ISA_CFLAGS:+ $FFMPEG_X86_64_EXTRA_ISA_CFLAGS}"
            append_configure_toggle abi_flags mmx "$FFMPEG_X86_64_MMX"
            append_configure_toggle abi_flags inline-asm "$FFMPEG_X86_64_INLINE_ASM"
            append_configure_toggle abi_flags x86asm "$FFMPEG_X86_64_X86ASM"
            ;;
        x86)
            arch="x86"
            target="i686-linux-android"
            cpu="i686"
            extra_cflags="-march=i686${FFMPEG_X86_EXTRA_ISA_CFLAGS:+ $FFMPEG_X86_EXTRA_ISA_CFLAGS}"
            append_configure_toggle abi_flags mmx "$FFMPEG_X86_MMX"
            append_configure_toggle abi_flags inline-asm "$FFMPEG_X86_INLINE_ASM"
            append_configure_toggle abi_flags x86asm "$FFMPEG_X86_X86ASM"
            ;;
        *) error "Unsupported ABI: $abi"; return 1 ;;
    esac

    check_x86_assembler "$abi"

    local vpx_prefix="$LIBVPX_OUTPUT_DIR/$abi"
    local vpx_archive="$vpx_prefix/lib/libvpx.a"
    local vpx_headers="$vpx_prefix/include/vpx"
    local vpx_pc_dir="$vpx_prefix/lib/pkgconfig"

    local dav1d_prefix="$DAV1D_OUTPUT_DIR/$abi"
    local dav1d_archive="$dav1d_prefix/lib/libdav1d.a"
    local dav1d_headers="$dav1d_prefix/include/dav1d"
    local dav1d_pc_dir="$dav1d_prefix/lib/pkgconfig"

    [[ -f "$vpx_archive" ]] || { error "Missing libvpx for $abi: $vpx_archive"; return 1; }
    [[ -d "$vpx_headers" ]] || { error "Missing libvpx headers for $abi: $vpx_headers"; return 1; }
    [[ -f "$dav1d_archive" ]] || { error "Missing dav1d for $abi: $dav1d_archive"; return 1; }
    [[ -d "$dav1d_headers" ]] || { error "Missing dav1d headers for $abi: $dav1d_headers"; return 1; }
    [[ -f "$dav1d_pc_dir/dav1d.pc" ]] || { error "Missing dav1d.pc for $abi"; return 1; }

    local prefix="$FFMPEG_OUTPUT_DIR/$abi"
    local build_dir="$FFMPEG_WORK_DIR/$abi"
    local cc="$TOOLCHAIN/bin/${target}${API}-clang"
    local cxx="$TOOLCHAIN/bin/${target}${API}-clang++"

    if [[ "$CLEAN" == "1" ]]; then
        rm -rf "$build_dir" "$prefix"
    fi
    mkdir -p "$build_dir" "$prefix"

    local -a size_flags=()
    [[ "$ENABLE_SMALL" == "1" ]] && size_flags+=(--enable-small)

    local dependency_pc_dirs="$vpx_pc_dir:$dav1d_pc_dir"
    local -a configure_env=(
        "PKG_CONFIG_PATH=$dependency_pc_dirs${PKG_CONFIG_PATH:+:$PKG_CONFIG_PATH}"
        "PKG_CONFIG_LIBDIR=$dependency_pc_dirs"
        "ARFLAGS=rcD"
    )

    local -a configure_args=(
        --prefix="$prefix"
        --target-os=android
        --arch="$arch"
        --cpu="$cpu"
        --enable-cross-compile
        --cc="$cc"
        --cxx="$cxx"
        --ar="$TOOLCHAIN/bin/llvm-ar"
        --nm="$TOOLCHAIN/bin/llvm-nm"
        --ranlib="$TOOLCHAIN/bin/llvm-ranlib"
        --strip="$TOOLCHAIN/bin/llvm-strip"
        --sysroot="$TOOLCHAIN/sysroot"

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
        --enable-decoder=opus
        --enable-decoder=mp3
        --enable-decoder=aac

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
        --enable-decoder=libdav1d
        --enable-decoder=libvpx_vp9
        --enable-encoder=libvpx_vp9
        --pkg-config-flags=--static

        # Legacy build disabled this explicitly; --disable-autodetect only
        # stops probing for it, it does not forbid a system copy.
        # (--disable-postproc from the legacy script is gone: libpostproc was
        # removed in FFmpeg 8.0 and configure rejects the unknown option.)
        --disable-zlib

        "--extra-cflags=-fPIC -DANDROID $extra_cflags${COMMON_CFLAGS:+ $COMMON_CFLAGS} -I$vpx_prefix/include -I$dav1d_prefix/include"
        "--extra-ldflags=-Wl,-Bsymbolic -L$vpx_prefix/lib -L$dav1d_prefix/lib"
        "--extra-libs=-ldav1d -lvpx -lm -ldl"
    )

    # configure appends its own optimization flag (-Os with --enable-small,
    # otherwise -O3) *after* --extra-cflags, so an -O level passed there is
    # silently overridden. --optflags is the only flag that actually wins.
    if [[ -n "$FFMPEG_OPTFLAGS" ]]; then
        configure_args+=("--optflags=$FFMPEG_OPTFLAGS")
    fi

    if [[ "$FFMPEG_PARSERS" == "1" ]]; then
        configure_args+=(
            --enable-parser=h264
            --enable-parser=hevc
            --enable-parser=mpeg4video
            --enable-parser=mpegaudio
            --enable-parser=aac
            --enable-parser=opus
            --enable-parser=av1
        )
    fi

    if [[ "$FFMPEG_LIBVPX_VP8" == "1" ]]; then
        configure_args+=(
            --enable-decoder=libvpx_vp8
            --enable-encoder=libvpx_vp8
        )
    fi

    if [[ "$abi" == "x86" || "$abi" == "x86_64" ]]; then
        append_configure_toggle configure_args runtime-cpudetect "$FFMPEG_RUNTIME_CPUDETECT"
        append_configure_toggle configure_args mmx "$FFMPEG_MMX"
        append_configure_toggle configure_args mmxext "$FFMPEG_MMXEXT"
        append_configure_toggle configure_args sse "$FFMPEG_SSE"
        append_configure_toggle configure_args sse2 "$FFMPEG_SSE2"
        append_configure_toggle configure_args sse3 "$FFMPEG_SSE3"
        append_configure_toggle configure_args ssse3 "$FFMPEG_SSSE3"
        append_configure_toggle configure_args sse4 "$FFMPEG_SSE4"
        append_configure_toggle configure_args sse42 "$FFMPEG_SSE42"
        append_configure_toggle configure_args avx "$FFMPEG_AVX"
        append_configure_toggle configure_args avx2 "$FFMPEG_AVX2"
        append_configure_toggle configure_args avx512 "$FFMPEG_AVX512"
        append_configure_toggle configure_args fma3 "$FFMPEG_FMA3"
        append_configure_toggle configure_args fma4 "$FFMPEG_FMA4"
        append_configure_toggle configure_args bmi1 "$FFMPEG_BMI1"
        append_configure_toggle configure_args bmi2 "$FFMPEG_BMI2"
    fi

    configure_args+=("${size_flags[@]}")
    configure_args+=("${abi_flags[@]}")

    echo
    echo "========== FFmpeg: $abi, API $API =========="
    if [[ "$abi" == "x86" || "$abi" == "x86_64" ]]; then
        printf 'Selected CPU/asm configure flags:'
        for flag in "${configure_args[@]}"; do
            case "$flag" in
                --enable-runtime-cpudetect|--disable-runtime-cpudetect|\
                --enable-mmx|--disable-mmx|--enable-mmxext|--disable-mmxext|\
                --enable-sse|--disable-sse|--enable-sse2|--disable-sse2|\
                --enable-sse3|--disable-sse3|--enable-ssse3|--disable-ssse3|\
                --enable-sse4|--disable-sse4|--enable-sse42|--disable-sse42|\
                --enable-avx|--disable-avx|--enable-avx2|--disable-avx2|\
                --enable-avx512|--disable-avx512|--enable-fma3|--disable-fma3|\
                --enable-fma4|--disable-fma4|--enable-bmi1|--disable-bmi1|\
                --enable-bmi2|--disable-bmi2|--enable-inline-asm|--disable-inline-asm|\
                --enable-x86asm|--disable-x86asm)
                    printf ' %s' "$flag"
                    ;;
            esac
        done
        printf '\n'
    fi

    pushd "$build_dir" >/dev/null

    if ! env "${configure_env[@]}" "$FFMPEG_SOURCE_DIR/configure" "${configure_args[@]}"; then
        error "FFmpeg configure failed for $abi. Log: $build_dir/ffbuild/config.log"
        popd >/dev/null
        return 1
    fi

    local components_header="$build_dir/config_components.h"
    [[ -f "$components_header" ]] || components_header="$build_dir/config.h"

    local -a required_components=(
        CONFIG_LIBDAV1D_DECODER
        CONFIG_LIBVPX_VP9_DECODER
        CONFIG_LIBVPX_VP9_ENCODER
    )
    if [[ "$FFMPEG_LIBVPX_VP8" == "1" ]]; then
        required_components+=(
            CONFIG_LIBVPX_VP8_DECODER
            CONFIG_LIBVPX_VP8_ENCODER
        )
    fi

    for component in "${required_components[@]}"; do
        if ! grep -Eq "^#define[[:space:]]+$component[[:space:]]+1$" "$components_header"; then
            error "FFmpeg component is disabled for $abi: $component (see $components_header and $build_dir/ffbuild/config.log)"
            popd >/dev/null
            return 1
        fi
    done

    # Keep only specialized implementations:
    # VP8/VP9 -> libvpx, AV1 -> dav1d.
    for component in CONFIG_VP8_DECODER CONFIG_VP9_DECODER CONFIG_AV1_DECODER; do
        if ! grep -Eq "^#define[[:space:]]+$component[[:space:]]+0$" "$components_header"; then
            error "Built-in FFmpeg decoder is unexpectedly enabled for $abi: $component"
            popd >/dev/null
            return 1
        fi
    done

    local enabled_av1_decoders
    enabled_av1_decoders="$(
        awk '/^#define[[:space:]]+CONFIG_.*(AV1|DAV1D).*_DECODER[[:space:]]+1$/ { print $2 }'             "$components_header" | LC_ALL=C sort
    )"
    if [[ "$enabled_av1_decoders" != "CONFIG_LIBDAV1D_DECODER" ]]; then
        error "Unexpected enabled AV1 decoders for $abi:"
        printf '%s\n' "${enabled_av1_decoders:-<none>}" >&2
        error "Exactly CONFIG_LIBDAV1D_DECODER must be enabled."
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
}

echo
echo "========== Size/parity configuration =========="
printf '  %-32s %s\n' \
    "SOURCE_DATE_EPOCH"            "$SOURCE_DATE_EPOCH" \
    "PROJECT_ROOT mapping"          "$PROJECT_ROOT -> /src" \
    "BUILD_ROOT mapping"            "$BUILD_ROOT -> /build" \
    "ENABLE_SMALL (FFmpeg)"        "$ENABLE_SMALL" \
    "FFMPEG_OPTFLAGS"              "${FFMPEG_OPTFLAGS:-<configure default>}" \
    "FFMPEG_PARSERS"               "$FFMPEG_PARSERS" \
    "FFMPEG_LIBVPX_VP8"            "$FFMPEG_LIBVPX_VP8" \
    "LIBVPX_REALTIME_ONLY"         "$LIBVPX_REALTIME_ONLY" \
    "LIBVPX_SMALL"                 "$LIBVPX_SMALL" \
    "LIBVPX_RUNTIME_CPU_DETECT"    "$LIBVPX_RUNTIME_CPU_DETECT" \
    "LIBVPX_ARM_EXTENSIONS"        "$LIBVPX_ARM_EXTENSIONS" \
    "LIBVPX_SIZE_LIMIT"            "${LIBVPX_SIZE_LIMIT:-<none>}" \
    "ADDRSIG"                      "$ADDRSIG" \
    "LIBVPX_ADDRSIG"               "$LIBVPX_ADDRSIG" \
    "HIDDEN_VISIBILITY"            "$HIDDEN_VISIBILITY" \
    "UNWIND_TABLES"                "$UNWIND_TABLES" \
    "FRAME_POINTERS"               "$FRAME_POINTERS" \
    "FUNCTION_SECTIONS"            "$FUNCTION_SECTIONS" \
    "COMMON_CFLAGS"                "${COMMON_CFLAGS:-<none>}" \
    "COMMON_CFLAGS (libvpx)"       "${LIBVPX_COMMON_CFLAGS:-<none>}"

for abi in $ABIS; do
    echo
    echo "############################################################"
    echo "ABI: $abi"
    echo "############################################################"

    if [[ "$BUILD_LIBVPX" == "1" ]]; then
        build_libvpx_for_abi "$abi"
    fi

    if [[ "$BUILD_DAV1D" == "1" ]]; then
        build_dav1d_for_abi "$abi"
    fi

    if [[ "$BUILD_FFMPEG" == "1" ]]; then
        build_ffmpeg_for_abi "$abi"
    fi
done


abi_identifier() {
    case "$1" in
        arm64-v8a) echo "arm64_v8a" ;;
        armeabi-v7a) echo "armeabi_v7a" ;;
        x86_64) echo "x86_64" ;;
        x86) echo "x86" ;;
        *) error "Unsupported ABI in abi_identifier: $1"; return 1 ;;
    esac
}

abi_cpp_condition() {
    case "$1" in
        arm64-v8a) echo "defined(__aarch64__)" ;;
        armeabi-v7a) echo "defined(__arm__) && !defined(__aarch64__)" ;;
        x86_64) echo "defined(__x86_64__)" ;;
        x86) echo "defined(__i386__)" ;;
        *) error "Unsupported ABI in abi_cpp_condition: $1"; return 1 ;;
    esac
}

list_relative_files() {
    local directory="$1"
    (
        cd "$directory"
        find . -type f -print | sed 's#^\./##' | LC_ALL=C sort
    )
}

copy_or_dispatch_header() {
    local component="$1"
    local relative_path="$2"
    local source_base="$3"
    local destination_root="$4"

    local reference_abi=""
    local reference_file=""
    local abi source_file
    local files_identical=1

    for abi in $ABIS; do
        source_file="$source_base/$abi/include/$relative_path"
        [[ -f "$source_file" ]] || {
            error "Missing $component header for $abi: $source_file"
            return 1
        }

        if [[ -z "$reference_abi" ]]; then
            reference_abi="$abi"
            reference_file="$source_file"
        elif ! cmp -s "$reference_file" "$source_file"; then
            files_identical=0
        fi
    done

    local destination_file="$destination_root/$relative_path"
    mkdir -p "$(dirname "$destination_file")"

    if [[ "$files_identical" == "1" ]]; then
        if [[ -e "$destination_file" ]] && ! cmp -s "$reference_file" "$destination_file"; then
            error "Header collision while packaging: $relative_path"
            return 1
        fi
        cp -f "$reference_file" "$destination_file"
        return 0
    fi

    if [[ "$relative_path" != *.h ]]; then
        error "$component file differs between ABIs and is not a header: $relative_path"
        return 1
    fi

    local destination_dir
    local base_name
    local stem
    destination_dir="$(dirname "$destination_file")"
    base_name="$(basename "$destination_file")"
    stem="${base_name%.h}"

    for abi in $ABIS; do
        local abi_id
        abi_id="$(abi_identifier "$abi")"
        cp -f \
            "$source_base/$abi/include/$relative_path" \
            "$destination_dir/${stem}.${abi_id}.h"
    done

    local guard
    guard="ANDROID_ABI_DISPATCH_$(printf '%s' "$relative_path" | tr '[:lower:]/.-' '[:upper:]____')"

    {
        echo "/* Auto-generated ABI dispatcher for $component. */"
        echo "#ifndef $guard"
        echo "#define $guard"
        echo

        local first=1
        for abi in $ABIS; do
            local abi_id condition
            abi_id="$(abi_identifier "$abi")"
            condition="$(abi_cpp_condition "$abi")"

            if [[ "$first" == "1" ]]; then
                echo "#if $condition"
                first=0
            else
                echo "#elif $condition"
            fi
            echo "#include \"${stem}.${abi_id}.h\""
        done

        echo "#else"
        echo "#error Unsupported Android ABI"
        echo "#endif"
        echo
        echo "#endif /* $guard */"
    } > "$destination_file"

    echo "ABI-dependent header: $component/$relative_path"
}

merge_component_headers() {
    local component="$1"
    local source_base="$2"
    local destination_root="$3"

    local reference_abi=""
    local reference_include=""
    local reference_manifest=""
    local abi include_dir manifest

    for abi in $ABIS; do
        include_dir="$source_base/$abi/include"
        [[ -d "$include_dir" ]] || {
            error "Missing include directory for $component/$abi: $include_dir"
            return 1
        }

        manifest="$PACKAGE_DIR/.${component}.${abi}.headers"
        list_relative_files "$include_dir" > "$manifest"

        if [[ -z "$reference_abi" ]]; then
            reference_abi="$abi"
            reference_include="$include_dir"
            reference_manifest="$manifest"
        elif ! cmp -s "$reference_manifest" "$manifest"; then
            error "$component installs a different header set for $reference_abi and $abi."
            diff -u "$reference_manifest" "$manifest" || true
            return 1
        fi
    done

    while IFS= read -r relative_path; do
        [[ -n "$relative_path" ]] || continue
        copy_or_dispatch_header \
            "$component" \
            "$relative_path" \
            "$source_base" \
            "$destination_root"
    done < "$reference_manifest"
}


package_libvpx_headers() {
    local temporary_include="$PACKAGE_DIR/.libvpx_include"

    rm -rf "$temporary_include"
    mkdir -p "$temporary_include"

    # libvpx installs public headers under include/vpx. Merge them there first
    # so ABI comparison/dispatcher generation still uses the original layout.
    merge_component_headers "libvpx" "$LIBVPX_OUTPUT_DIR" "$temporary_include"

    [[ -d "$temporary_include/vpx" ]] || {
        error "Merged libvpx headers were not found: $temporary_include/vpx"
        return 1
    }

    rm -rf "$PACKAGE_DIR/include/libvpx"
    mv "$temporary_include/vpx" "$PACKAGE_DIR/include/libvpx"
    rm -rf "$temporary_include"

    # Installed libvpx headers refer to one another as <vpx/...>.
    # The Telegram tree expects <libvpx/...>, so rewrite only those include paths
    # in the final package. Intermediate libvpx/FFmpeg installs stay untouched.
    while IFS= read -r -d '' header; do
        sed -i.bak             -e 's#<vpx/#<libvpx/#g'             -e 's#"vpx/#"libvpx/#g'             "$header"
        rm -f "${header}.bak"
    done < <(find "$PACKAGE_DIR/include/libvpx" -type f -name '*.h' -print0)

    [[ -f "$PACKAGE_DIR/include/libvpx/vpx_codec.h" ]] || {
        error "Missing packaged libvpx header: $PACKAGE_DIR/include/libvpx/vpx_codec.h"
        return 1
    }

    [[ -f "$PACKAGE_DIR/include/libvpx/vpx_decoder.h" ]] || {
        error "Missing packaged decoder header: $PACKAGE_DIR/include/libvpx/vpx_decoder.h"
        return 1
    }
}

copy_component_libraries() {
    local component="$1"
    local source_base="$2"
    local abi source_lib_dir destination_lib_dir library

    for abi in $ABIS; do
        source_lib_dir="$source_base/$abi/lib"
        destination_lib_dir="$PACKAGE_DIR/$abi"

        [[ -d "$source_lib_dir" ]] || {
            error "Missing library directory for $component/$abi: $source_lib_dir"
            return 1
        }

        mkdir -p "$destination_lib_dir"

        while IFS= read -r -d '' library; do
            local destination_library
            destination_library="$destination_lib_dir/$(basename "$library")"

            if [[ -e "$destination_library" ]] && ! cmp -s "$library" "$destination_library"; then
                error "Library collision while packaging: $destination_library"
                return 1
            fi

            cp -f "$library" "$destination_library"
        done < <(find "$source_lib_dir" -maxdepth 1 -type f \( -name '*.a' -o -name '*.so' \) -print0)
    done
}

report_archive_flags() {
    local readelf="$TOOLCHAIN/bin/llvm-readelf"
    [[ -x "$readelf" ]] || return 0

    echo
    echo "========== Section report =========="
    echo "  function-sections / addrsig enable -Wl,--gc-sections and -Wl,--icf=safe;"
    echo "  eh_frame is unwind data that survives into the linked .so."

    local abi archive name sections count_text count_addrsig count_eh
    for abi in $ABIS; do
        for name in libavcodec libvpx libdav1d; do
            archive="$PACKAGE_DIR/$abi/$name.a"
            [[ -f "$archive" ]] || continue

            sections="$("$readelf" --section-headers --wide "$archive" 2>/dev/null || true)"
            count_text="$(grep -c ' \.text\.' <<<"$sections" || true)"
            count_addrsig="$(grep -c '\.llvm_addrsig' <<<"$sections" || true)"
            count_eh="$(grep -c ' \.eh_frame' <<<"$sections" || true)"

            printf '  %-12s %-11s .text.* = %-6s .llvm_addrsig = %-6s .eh_frame = %s\n' \
                "$abi" "$name" "$count_text" "$count_addrsig" "$count_eh"
        done
    done
}

package_outputs() {
    echo
    echo "========== Packaging common include and per-ABI libraries =========="

    mkdir -p "$PACKAGE_DIR"
    rm -rf "$PACKAGE_DIR/include"
    mkdir -p "$PACKAGE_DIR/include"

    local abi
    for abi in $ABIS; do
        rm -rf "$PACKAGE_DIR/$abi"
        mkdir -p "$PACKAGE_DIR/$abi"
    done

    if [[ "$BUILD_LIBVPX" == "1" || -d "$LIBVPX_OUTPUT_DIR" ]]; then
        package_libvpx_headers
        copy_component_libraries "libvpx" "$LIBVPX_OUTPUT_DIR"
    fi

    if [[ "$BUILD_DAV1D" == "1" || -d "$DAV1D_OUTPUT_DIR" ]]; then
        merge_component_headers "dav1d" "$DAV1D_OUTPUT_DIR" "$PACKAGE_DIR/include"
        copy_component_libraries "dav1d" "$DAV1D_OUTPUT_DIR"
    fi

    if [[ "$BUILD_FFMPEG" == "1" || -d "$FFMPEG_OUTPUT_DIR" ]]; then
        merge_component_headers "ffmpeg" "$FFMPEG_OUTPUT_DIR" "$PACKAGE_DIR/include"
        copy_component_libraries "ffmpeg" "$FFMPEG_OUTPUT_DIR"
    fi

    rm -f "$PACKAGE_DIR"/.*.headers 2>/dev/null || true

    echo "Package created:"
    echo "  Headers:   $PACKAGE_DIR/include"
    echo "  Libraries: $PACKAGE_DIR/<ABI>"

    report_archive_flags
}

if [[ "$PACKAGE_OUTPUT" == "1" ]]; then
    package_outputs
fi

echo
echo "Build completed."
echo "Intermediate libvpx install: $LIBVPX_OUTPUT_DIR/<ABI>"
echo "Intermediate dav1d install:  $DAV1D_OUTPUT_DIR/<ABI>"
echo "Intermediate FFmpeg install: $FFMPEG_OUTPUT_DIR/<ABI>"
if [[ "$PACKAGE_OUTPUT" == "1" ]]; then
    echo "Final headers: $PACKAGE_DIR/include"
echo "Final libraries: $PACKAGE_DIR/<ABI>"
fi
