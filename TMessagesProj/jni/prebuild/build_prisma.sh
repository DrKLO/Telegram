#!/usr/bin/env bash
# Compile libprisma into prebuild/lib/<ABI>/, using Telegram's shared build configuration.
set -Eeuo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "$SCRIPT_DIR/common.sh"

PRISMA_SRC="${PRISMA_SOURCE_DIR:-$JNI_DIR/third_party/libprisma}"
PRISMA_BUILD_ROOT="${PRISMA_BUILD_ROOT:-$SCRIPT_DIR/build/prisma}"
BOOST_REGEX_SRC="${BOOST_REGEX_SOURCE_DIR:-$JNI_DIR/third_party/boost-regex}"
EXPECTED_BOOST_COMMIT=4cbcd3078e6ae10d05124379623a1bf03fcb9350
EXPECTED_COMMIT=31a5d6fbbef8136e4a2b3adaf7b95e2ccaa7436d

require_ndk
require_cmake_ninja
require_command git

[[ -f "$PRISMA_SRC/libprisma/SyntaxHighlighter.cpp" ]] || {
    echo "ERROR: Initialize the libprisma submodule: $PRISMA_SRC" >&2
    exit 1
}
actual_commit="$(git -C "$PRISMA_SRC" rev-parse HEAD)"
[[ "$actual_commit" == "$EXPECTED_COMMIT" ]] || {
    echo "ERROR: Expected libprisma $EXPECTED_COMMIT, found $actual_commit" >&2
    exit 1
}

[[ -f "$BOOST_REGEX_SRC/include/boost/regex.hpp" ]] || {
    echo "ERROR: Initialize the Boost.Regex submodule: $BOOST_REGEX_SRC" >&2
    exit 1
}
actual_boost_commit="$(git -C "$BOOST_REGEX_SRC" rev-parse HEAD)"
[[ "$actual_boost_commit" == "$EXPECTED_BOOST_COMMIT" ]] || {
    echo "ERROR: Expected Boost.Regex $EXPECTED_BOOST_COMMIT, found $actual_boost_commit" >&2
    exit 1
}

for abi in $ABIS; do
    build_dir="$PRISMA_BUILD_ROOT/$abi"
    out_dir="$(abi_output_dir "$abi")"
    echo "==> Building libprisma for $abi (API=$API)"

    "$CMAKE_BIN" \
        -S "$JNI_DIR/prisma" \
        -B "$build_dir" \
        -G Ninja \
        -DCMAKE_MAKE_PROGRAM="$NINJA_BIN" \
        -DCMAKE_TOOLCHAIN_FILE="$TOOLCHAIN_FILE" \
        -DANDROID_ABI="$abi" \
        -DANDROID_PLATFORM="android-$API" \
        -DANDROID_STL=c++_static \
        -DCMAKE_BUILD_TYPE=Release \
        -DCMAKE_C_FLAGS="$CMAKE_REPRO_C_FLAGS" \
        -DCMAKE_CXX_FLAGS="$CMAKE_REPRO_CXX_FLAGS" \
        -DPRISMA_SOURCE_DIR="$PRISMA_SRC" \
        -DBOOST_REGEX_SOURCE_DIR="$BOOST_REGEX_SRC"

    "$CMAKE_BIN" --build "$build_dir" --target prisma --parallel "$JOBS"
    archive="$build_dir/libprisma.a"
    [[ -f "$archive" ]] || {
        echo "ERROR: libprisma.a was not produced for $abi" >&2
        exit 1
    }
    copy_archive "$archive" "$out_dir/libprisma.a"
    # common.sh already supplies -g0/-fno-ident; do not strip/objcopy clang objects.
    echo "    -> $out_dir/libprisma.a"
done
