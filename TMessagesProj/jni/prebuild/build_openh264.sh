#!/usr/bin/env bash
# Compile static OpenH264 libraries for Android.
#
# The resulting archives are placed in ABI directories next to this script:
#
#   arm64-v8a/libopenh264.a
#   armeabi-v7a/libopenh264.a
#   x86_64/libopenh264.a
#   x86/libopenh264.a
#
# build_openh264/ is used as temporary workspace. It is removed after all
# architectures are built successfully and preserved if the build fails.
#
# OpenH264 is built with its upstream Makefile, which selects the appropriate
# sources and architecture-specific optimizations (including NEON/assembly).
#
# Optional environment overrides:
#   OPENH264_SOURCE_DIR=/path/to/openh264
#   ANDROID_NDK_HOME=/path/to/ndk
#   NDK_VERSION=27.2.12479018
#   API=21
#   ABIS="arm64-v8a armeabi-v7a x86_64 x86"
#   JOBS=16

set -Eeuo pipefail

NDK_VERSION="${NDK_VERSION:-27.2.12479018}"
API="${API:-21}"
ABIS="${ABIS:-arm64-v8a armeabi-v7a x86_64 x86}"
JOBS="${JOBS:-$(nproc)}"

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
OPENH264_SRC="${OPENH264_SOURCE_DIR:-$SCRIPT_DIR/../third_party/openh264}"
BUILD_DIR="$SCRIPT_DIR/build_openh264"

: "${ANDROID_NDK_HOME:=${ANDROID_SDK_ROOT:-$HOME/Android/Sdk}/ndk/$NDK_VERSION}"

[ -d "$ANDROID_NDK_HOME" ] || {
    echo "ERROR: Android NDK not found: $ANDROID_NDK_HOME" >&2
    exit 1
}

[ -f "$OPENH264_SRC/Makefile" ] || {
    echo "ERROR: OpenH264 Makefile not found: $OPENH264_SRC/Makefile" >&2
    echo "       Set OPENH264_SOURCE_DIR if the source tree is elsewhere." >&2
    exit 1
}

command -v make >/dev/null || {
    echo "ERROR: make is required" >&2
    exit 1
}

if [[ " $ABIS " == *" x86 "* || " $ABIS " == *" x86_64 "* ]]; then
    command -v nasm >/dev/null || {
        echo "ERROR: nasm is required for OpenH264 x86/x86_64 builds" >&2
        exit 1
    }
fi

abi_to_arch() {
    case "$1" in
        armeabi-v7a) echo arm ;;
        arm64-v8a)   echo arm64 ;;
        x86)         echo x86 ;;
        x86_64)      echo x86_64 ;;
        *)
            echo "ERROR: unsupported ABI: $1" >&2
            return 1
            ;;
    esac
}

copy_source_tree() {
    local dst="$1"
    rm -rf "$dst"
    mkdir -p "$dst"
    cp -a --reflink=auto "$OPENH264_SRC/." "$dst/"
}

rm -rf "$BUILD_DIR"
mkdir -p "$BUILD_DIR"

for abi in $ABIS; do
    arch="$(abi_to_arch "$abi")"
    work_src="$BUILD_DIR/$abi"

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
        ENABLEPIC=Yes \
        libopenh264.a

    [ -f "$work_src/libopenh264.a" ] || {
        echo "ERROR: build succeeded but libopenh264.a was not produced for $abi" >&2
        exit 1
    }

    install -D -m 644 \
        "$work_src/libopenh264.a" \
        "$SCRIPT_DIR/$abi/libopenh264.a"

    echo "==> $SCRIPT_DIR/$abi/libopenh264.a"
done

rm -rf "$BUILD_DIR"

echo
echo "OpenH264 static build completed."
for abi in $ABIS; do
    echo "  $SCRIPT_DIR/$abi/libopenh264.a"
done
