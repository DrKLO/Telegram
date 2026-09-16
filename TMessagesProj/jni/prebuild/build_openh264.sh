#!/usr/bin/env bash
# Compile static OpenH264 libraries for Android into lib/<abi>/.

set -Eeuo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "$SCRIPT_DIR/common.sh"

OPENH264_SRC="${OPENH264_SOURCE_DIR:-$SCRIPT_DIR/../third_party/openh264}"
BUILD_DIR="$SCRIPT_DIR/build/openh264"

require_ndk
require_command make

[[ -f "$OPENH264_SRC/Makefile" ]] || {
    echo "ERROR: OpenH264 Makefile not found: $OPENH264_SRC/Makefile" >&2
    echo "       Set OPENH264_SOURCE_DIR if the source tree is elsewhere." >&2
    exit 1
}

if [[ " $ABIS " == *" x86 "* || " $ABIS " == *" x86_64 "* ]]; then
    require_command nasm
fi

abi_to_arch() {
    case "$1" in
        armeabi-v7a) echo arm ;;
        arm64-v8a)   echo arm64 ;;
        x86)         echo x86 ;;
        x86_64)      echo x86_64 ;;
        *) echo "ERROR: unsupported ABI: $1" >&2; return 1 ;;
    esac
}

copy_source_tree() {
    local dst="$1"
    rm -rf "$dst"
    mkdir -p "$dst"
    cp -R "$OPENH264_SRC/." "$dst/"
}

rm -rf "$BUILD_DIR"
mkdir -p "$BUILD_DIR"

for abi in $ABIS; do
    arch="$(abi_to_arch "$abi")"
    work_src="$BUILD_DIR/$abi"
    abi_out="$(abi_output_dir "$abi")"

    echo
    echo "==> Building OpenH264 for $abi (ARCH=$arch, API=$API)"

    copy_source_tree "$work_src"

    make -C "$work_src" \
        -j"$JOBS" \
        V=No \
        OS=android \
        ARCH="$arch" \
        NDKROOT="$ANDROID_NDK_HOME" \
        TARGET="android-$API" \
        NDKLEVEL="$API" \
        BUILDTYPE=Release \
        CFLAGS_OPT="-O3 $REPRODUCIBLE_PATH_FLAGS $PREBUILD_OBJECT_FLAGS" \
        ENABLEPIC=Yes \
        libopenh264.a

    [[ -f "$work_src/libopenh264.a" ]] || {
        echo "ERROR: build succeeded but libopenh264.a was not produced for $abi" >&2
        exit 1
    }

    copy_archive "$work_src/libopenh264.a" "$abi_out/libopenh264.a"
    finalize_archive "$abi_out/libopenh264.a"
    echo "==> $abi_out/libopenh264.a"
done

rm -rf "$BUILD_DIR"

echo
echo "OpenH264 static build completed."
