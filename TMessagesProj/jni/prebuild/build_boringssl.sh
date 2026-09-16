#!/usr/bin/env bash
# Compile static BoringSSL libraries for Android into lib/<abi>/.

set -Eeuo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=common.sh
source "$SCRIPT_DIR/common.sh"

BORINGSSL_SRC="${BORINGSSL_SOURCE_DIR:-$SCRIPT_DIR/../third_party/boringssl}"
BUILD_DIR="$SCRIPT_DIR/build/boringssl"

require_ndk
require_cmake_ninja

[[ -f "$BORINGSSL_SRC/CMakeLists.txt" ]] || {
    echo "ERROR: BoringSSL source tree not found: $BORINGSSL_SRC" >&2
    echo "       Set BORINGSSL_SOURCE_DIR if the source tree is elsewhere." >&2
    exit 1
}

echo "Using $("$CMAKE_BIN" --version | head -n1)"
echo "Using Ninja: $NINJA_BIN"
echo "Using NDK: $ANDROID_NDK_HOME"

rm -rf "$BUILD_DIR"
mkdir -p "$BUILD_DIR"

for abi in $ABIS; do
    abi_build_dir="$BUILD_DIR/$abi"
    abi_out="$(abi_output_dir "$abi")"

    echo
    echo "==> Configuring BoringSSL for $abi (API=$API)"

    rm -rf "$abi_build_dir"

    "$CMAKE_BIN" \
        -S "$BORINGSSL_SRC" \
        -B "$abi_build_dir" \
        -GNinja \
        -DCMAKE_MAKE_PROGRAM="$NINJA_BIN" \
        -DCMAKE_TOOLCHAIN_FILE="$TOOLCHAIN_FILE" \
        -DANDROID_ABI="$abi" \
        -DANDROID_PLATFORM="android-$API" \
        -DCMAKE_BUILD_TYPE=Release \
        -DCMAKE_C_FLAGS="$CMAKE_REPRO_C_FLAGS" \
        -DCMAKE_CXX_FLAGS="$CMAKE_REPRO_CXX_FLAGS" \
        -DBUILD_SHARED_LIBS=OFF

    echo "==> Building BoringSSL for $abi"
    "$CMAKE_BIN" --build "$abi_build_dir" --target crypto ssl --parallel "$JOBS"

    crypto_lib="$abi_build_dir/crypto/libcrypto.a"
    ssl_lib="$abi_build_dir/ssl/libssl.a"

    [[ -f "$crypto_lib" ]] || { echo "ERROR: libcrypto.a was not produced for $abi" >&2; exit 1; }
    [[ -f "$ssl_lib" ]] || { echo "ERROR: libssl.a was not produced for $abi" >&2; exit 1; }

    copy_archive "$crypto_lib" "$abi_out/libcrypto.a"
    copy_archive "$ssl_lib" "$abi_out/libssl.a"
    finalize_archive "$abi_out/libcrypto.a"
    finalize_archive "$abi_out/libssl.a"
    echo "==> $abi_out/libcrypto.a"
    echo "==> $abi_out/libssl.a"
done

rm -rf "$BUILD_DIR"

echo
echo "BoringSSL static build completed."
