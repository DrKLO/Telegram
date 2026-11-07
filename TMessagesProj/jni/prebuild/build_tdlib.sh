#!/usr/bin/env bash
# Compile TDLib static libraries for Android into lib/<abi>/.
# Requires BoringSSL archives in the same lib/<abi>/ directories.

set -Eeuo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "$SCRIPT_DIR/common.sh"

TD_SRC="${TD_SOURCE_DIR:-$SCRIPT_DIR/../td}"
BORINGSSL_SRC="${BORINGSSL_SOURCE_DIR:-$SCRIPT_DIR/../third_party/boringssl}"
BUILD_DIR="$SCRIPT_DIR/build/tdlib"

require_ndk
require_cmake_ninja

[[ -f "$TD_SRC/CMakeLists.txt" ]] || {
    echo "ERROR: TDLib source tree not found: $TD_SRC" >&2
    echo "       Set TD_SOURCE_DIR if TDLib is elsewhere." >&2
    exit 1
}

[[ -f "$BORINGSSL_SRC/include/openssl/ssl.h" ]] || {
    echo "ERROR: BoringSSL headers not found: $BORINGSSL_SRC/include" >&2
    echo "       Set BORINGSSL_SOURCE_DIR if BoringSSL is elsewhere." >&2
    exit 1
}

find_archive() {
    local root="$1"
    local name="$2"
    find "$root" -type f -name "$name" -print -quit
}

rm -rf "$BUILD_DIR"
mkdir -p "$BUILD_DIR"

NATIVE_BUILD="$BUILD_DIR/native"
echo "==> Generating TDLib source files"

"$CMAKE_BIN" \
    -S "$TD_SRC" \
    -B "$NATIVE_BUILD" \
    -G Ninja \
    -DCMAKE_MAKE_PROGRAM="$NINJA_BIN" \
    -DCMAKE_BUILD_TYPE=Release \
    -DCMAKE_C_FLAGS="$CMAKE_REPRO_C_FLAGS" \
    -DCMAKE_CXX_FLAGS="$CMAKE_REPRO_CXX_FLAGS" \
    -DTD_GENERATE_SOURCE_FILES=ON

"$CMAKE_BIN" --build "$NATIVE_BUILD" --parallel "$JOBS"

for abi in $ABIS; do
    ABI_DIR="$(abi_output_dir "$abi")"
    ABI_BUILD="$BUILD_DIR/$abi"
    SSL_LIB="$ABI_DIR/libssl.a"
    CRYPTO_LIB="$ABI_DIR/libcrypto.a"

    [[ -f "$SSL_LIB" ]] || { echo "ERROR: BoringSSL library not found: $SSL_LIB" >&2; exit 1; }
    [[ -f "$CRYPTO_LIB" ]] || { echo "ERROR: BoringSSL library not found: $CRYPTO_LIB" >&2; exit 1; }

    echo
    echo "==> Building TDLib for $abi (API=$API)"

    "$CMAKE_BIN" \
        -S "$TD_SRC" \
        -B "$ABI_BUILD" \
        -G Ninja \
        -DCMAKE_MAKE_PROGRAM="$NINJA_BIN" \
        -DCMAKE_BUILD_TYPE=Release \
        -DCMAKE_C_FLAGS="$CMAKE_REPRO_C_FLAGS" \
        -DCMAKE_CXX_FLAGS="$CMAKE_REPRO_CXX_FLAGS" \
        -DCMAKE_TOOLCHAIN_FILE="$TOOLCHAIN_FILE" \
        -DANDROID_ABI="$abi" \
        -DANDROID_PLATFORM="android-$API" \
        -DANDROID_STL=c++_static \
        -DOPENSSL_INCLUDE_DIR="$BORINGSSL_SRC/include" \
        -DOPENSSL_SSL_LIBRARY="$SSL_LIB" \
        -DOPENSSL_CRYPTO_LIBRARY="$CRYPTO_LIB"

    "$CMAKE_BIN" --build "$ABI_BUILD" --target tde2e tdutils --parallel "$JOBS"

    TDE2E_LIB="$(find_archive "$ABI_BUILD" libtde2e.a)"
    TDUTILS_LIB="$(find_archive "$ABI_BUILD" libtdutils.a)"

    [[ -n "$TDE2E_LIB" ]] || { echo "ERROR: libtde2e.a was not produced for $abi" >&2; exit 1; }
    [[ -n "$TDUTILS_LIB" ]] || { echo "ERROR: libtdutils.a was not produced for $abi" >&2; exit 1; }

    copy_archive "$TDE2E_LIB" "$ABI_DIR/libtde2e.a"
    copy_archive "$TDUTILS_LIB" "$ABI_DIR/libtdutils.a"
    finalize_archive "$ABI_DIR/libtde2e.a"
    finalize_archive "$ABI_DIR/libtdutils.a"

    echo "==> $ABI_DIR/libtde2e.a"
    echo "==> $ABI_DIR/libtdutils.a"
done

rm -rf "$BUILD_DIR"

echo
echo "TDLib static build completed."
