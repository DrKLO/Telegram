#!/usr/bin/env bash
# Build static libopus for Android.
#
# Expected layout:
#
#   jni/
#     third_party/
#       xiph/
#         opus/
#     prebuild/
#       build_opus.sh
#
# Result:
#
#   jni/prebuild/lib/arm64-v8a/libopus.a
#   jni/prebuild/lib/armeabi-v7a/libopus.a
#   jni/prebuild/lib/x86_64/libopus.a
#   jni/prebuild/lib/x86/libopus.a
#
# build_opus/ is used as temporary workspace and is removed after a successful
# build. Sources are never copied.
#
# Why an explicit source list instead of opus's own CMake:
# for the FIXED_POINT ARM path, opus's CMake defines
# OPUS_ARM_MAY_HAVE_NEON / OPUS_ARM_PRESUME_NEON, which make the code reference
# celt_pitch_xcorr_neon -- a symbol provided only by celt/arm/celt_pitch_xcorr_arm.s.
# Instead, this build enables only the NEON intrinsic sources/macros, avoiding
# the asm-only symbol while still retaining NEON acceleration.
#
# Optional environment overrides:
#   OPUS_SOURCE_DIR=/path/to/opus
#   ANDROID_NDK_HOME=/path/to/ndk
#   ANDROID_SDK_ROOT=/path/to/sdk
#   NDK_VERSION=27.2.12479018
#   API=21
#   ABIS="arm64-v8a armeabi-v7a x86_64 x86"
#   JOBS=16

set -Eeuo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "$SCRIPT_DIR/common.sh"

OPUS_SRC="${OPUS_SOURCE_DIR:-$SCRIPT_DIR/../third_party/xiph/opus}"
BUILD_DIR="$SCRIPT_DIR/build/opus"
GEN_DIR="$BUILD_DIR/cmake"

require_ndk
require_cmake_ninja
require_command awk

# Extract a make-style:
#
#   VAR = a \
#         b \
#         c
#
# list from one of Opus's *.mk files.
mkvar() {
    awk -v v="$2" '
        $0 ~ "^"v"[ \t]*=" {
            f=1
            sub("^"v"[ \t]*=[ \t]*","")
        }
        f {
            cont = sub(/\\[ \t]*$/,"")
            gsub(/^[ \t]+|[ \t]+$/,"")
            if ($0 != "") print $0
            if (!cont) exit
        }
    ' "$1"
}

# Convert newline-separated relative source paths into a semicolon-separated
# list of absolute paths suitable for passing to CMake.
to_cmake_list() {
    local out="" f
    while IFS= read -r f; do
        [ -n "$f" ] && out="$out;$OPUS_SRC/$f"
    done
    echo "${out#;}"
}

OPUS_MK="$OPUS_SRC/opus_sources.mk"
CELT_MK="$OPUS_SRC/celt_sources.mk"
SILK_MK="$OPUS_SRC/silk_sources.mk"

for m in "$OPUS_MK" "$CELT_MK" "$SILK_MK"; do
    [ -f "$m" ] || {
        echo "ERROR: missing $m" >&2
        echo "       Set OPUS_SOURCE_DIR if the source tree is elsewhere." >&2
        exit 1
    }
done

BASE_LIST="$({
    mkvar "$OPUS_MK" OPUS_SOURCES
    mkvar "$OPUS_MK" OPUS_SOURCES_FLOAT
    mkvar "$CELT_MK" CELT_SOURCES
    mkvar "$SILK_MK" SILK_SOURCES
    mkvar "$SILK_MK" SILK_SOURCES_FIXED
} | to_cmake_list)"

NEON_LIST="$({
    mkvar "$CELT_MK" CELT_SOURCES_ARM_NEON_INTR
    mkvar "$SILK_MK" SILK_SOURCES_ARM_NEON_INTR
    mkvar "$SILK_MK" SILK_SOURCES_FIXED_ARM_NEON_INTR
} | to_cmake_list)"

INCLUDES="$OPUS_SRC;$OPUS_SRC/include;$OPUS_SRC/celt;$OPUS_SRC/silk;$OPUS_SRC/silk/fixed"

BASE_DEFS="OPUS_BUILD;FIXED_POINT;USE_ALLOCA;restrict=;LOCALE_NOT_USED;HAVE_LRINT;HAVE_LRINTF"

# Enable only intrinsic NEON support: no plain NEON macros, no RTCD and no
# hand-written ARM assembly dependency.
NEON_DEFS="OPUS_ARM_MAY_HAVE_NEON_INTR;OPUS_ARM_PRESUME_NEON_INTR"

rm -rf "$BUILD_DIR"
mkdir -p "$GEN_DIR"

cat > "$GEN_DIR/CMakeLists.txt" <<'CMAKE'
cmake_minimum_required(VERSION 3.18)

project(opus_static C)

add_library(opus STATIC ${OPUS_SOURCES})

set_target_properties(opus PROPERTIES
    POSITION_INDEPENDENT_CODE ON
)

target_compile_definitions(opus PRIVATE
    ${OPUS_DEFS}
)

target_include_directories(opus PRIVATE
    ${OPUS_INCLUDES}
)

target_compile_options(opus PRIVATE
    -std=c11
    -Oz
    -g0
    -ffast-math
    -funroll-loops
    -fno-strict-aliasing
    -fno-math-errno
    -ffunction-sections
    -fdata-sections
    -fvisibility=hidden
    -w
)
CMAKE

for abi in $ABIS; do
    ABI_BUILD="$BUILD_DIR/$abi"
    ABI_OUT="$(abi_output_dir "$abi")"

    srcs="$BASE_LIST"
    defs="$BASE_DEFS"
    extra=()

    case "$abi" in
        arm64-v8a)
            srcs="$BASE_LIST;$NEON_LIST"
            defs="$BASE_DEFS;$NEON_DEFS"
            ;;
        armeabi-v7a)
            srcs="$BASE_LIST;$NEON_LIST"
            defs="$BASE_DEFS;$NEON_DEFS"
            extra+=(-DANDROID_ARM_NEON=ON)
            ;;
        x86|x86_64)
            ;;
        *)
            echo "ERROR: unsupported ABI: $abi" >&2
            exit 1
            ;;
    esac

    echo
    echo "==> Building Opus for $abi (API=$API)"

    "$CMAKE_BIN" \
        -S "$GEN_DIR" \
        -B "$ABI_BUILD" \
        -G Ninja \
        -DCMAKE_MAKE_PROGRAM="$NINJA_BIN" \
        -DCMAKE_TOOLCHAIN_FILE="$TOOLCHAIN_FILE" \
        -DCMAKE_BUILD_TYPE=Release \
        -DCMAKE_C_FLAGS="$CMAKE_REPRO_C_FLAGS" \
        -DANDROID_ABI="$abi" \
        -DANDROID_PLATFORM="android-$API" \
        ${extra[@]+"${extra[@]}"} \
        -DOPUS_SOURCES="$srcs" \
        -DOPUS_DEFS="$defs" \
        -DOPUS_INCLUDES="$INCLUDES"

    "$CMAKE_BIN" --build "$ABI_BUILD" --parallel "$JOBS"

    [ -f "$ABI_BUILD/libopus.a" ] || {
        echo "ERROR: libopus.a was not produced for $abi" >&2
        exit 1
    }

    copy_archive "$ABI_BUILD/libopus.a" "$ABI_OUT/libopus.a"
    finalize_archive "$ABI_OUT/libopus.a"

    echo "==> $ABI_OUT/libopus.a"
done

rm -rf "$BUILD_DIR"

echo
echo "Opus static build completed."
for abi in $ABIS; do
    echo "  $LIB_DIR/$abi/libopus.a"
done
