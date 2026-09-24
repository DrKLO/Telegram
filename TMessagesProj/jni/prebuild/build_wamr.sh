#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "$SCRIPT_DIR/common.sh"

WAMR_SRC="${WAMR_SOURCE_DIR:-$SCRIPT_DIR/../third_party/wamr}"
BUILD_ROOT="${WAMR_BUILD_ROOT:-$SCRIPT_DIR/build/wamr}"

require_ndk
require_cmake_ninja

if [[ ! -f "$WAMR_SRC/CMakeLists.txt" ]]; then
    echo "ERROR: WAMR source tree not found: $WAMR_SRC" >&2
    exit 1
fi

android_target_for_abi() {
    case "$1" in
        arm64-v8a)
            printf '%s\n' "AARCH64"
            ;;
        armeabi-v7a)
            printf '%s\n' "ARM"
            ;;
        x86_64)
            printf '%s\n' "X86_64"
            ;;
        x86)
            printf '%s\n' "X86_32"
            ;;
        *)
            echo "Unsupported ABI: $1" >&2
            return 1
            ;;
    esac
}

mkdir -p "$BUILD_ROOT"

for abi in $ABIS; do
    echo
    echo "==> Building WAMR for $abi"

    build_dir="$BUILD_ROOT/$abi"
    out_dir="$(abi_output_dir "$abi")"
    target="$(android_target_for_abi "$abi")"

    rm -rf "$build_dir"
    mkdir -p "$build_dir" "$out_dir"

    "$CMAKE_BIN" \
        -S "$WAMR_SRC" \
        -B "$build_dir" \
        -G Ninja \
        -DCMAKE_TOOLCHAIN_FILE="$ANDROID_NDK_HOME/build/cmake/android.toolchain.cmake" \
        -DANDROID_ABI="$abi" \
        -DANDROID_PLATFORM="android-$API" \
        -DANDROID_STL=c++_static \
        -DCMAKE_BUILD_TYPE=Release \
        -DBUILD_SHARED_LIBS=OFF \
        -DWAMR_BUILD_PLATFORM=android \
        -DWAMR_BUILD_TARGET="$target" \
        -DWAMR_BUILD_INTERP=1 \
        -DWAMR_BUILD_FAST_INTERP=1 \
        -DWAMR_BUILD_AOT=1 \
        -DWAMR_BUILD_JIT=0 \
        -DWAMR_BUILD_FAST_JIT=0 \
        -DWAMR_BUILD_LIBC_BUILTIN=1 \
        -DWAMR_BUILD_LIBC_WASI=1 \
        -DWAMR_BUILD_LIB_PTHREAD=0 \
        -DWAMR_BUILD_LIB_WASI_THREADS=0

    "$CMAKE_BIN" --build "$build_dir" --target vmlib --parallel "$JOBS"

    archive="$build_dir/libiwasm.a"
    if [[ ! -f "$archive" ]]; then
        archive="$(find "$build_dir" -type f -name 'libiwasm.a' -print -quit)"
    fi

    if [[ -z "${archive:-}" || ! -f "$archive" ]]; then
        echo "ERROR: libiwasm.a was not produced for $abi" >&2
        exit 1
    fi

    destination="$out_dir/libiwasm.a"
    copy_archive "$archive" "$destination"
    finalize_archive "$destination"

    echo "    -> $destination"
done

echo
echo "WAMR build completed."

