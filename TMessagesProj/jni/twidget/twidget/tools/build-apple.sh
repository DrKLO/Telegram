#!/bin/sh
# Builds libtwidget_litehtml.a (the litehtml fork, TwidgetCore and the CoreGraphics backend behind
# the C ABI, merged into one archive) for the platform and architectures of the current Xcode build
# (PLATFORM_NAME, ARCHS, which Xcode sets when this runs as a build phase; macOS arm64 from a shell),
# and puts the fat library in build/apple/<PLATFORM_NAME>/. Needs cmake and ninja.
# The library targets iOS 16.3 / macOS 13.3: std::to_chars for doubles in twidget/json.h needs
# those libc++ versions.
set -eu
cd "$(dirname "$0")/.."
export PATH="/opt/homebrew/bin:/usr/local/bin:$PATH"
platform="${PLATFORM_NAME:-macosx}"
archs="${ARCHS:-arm64}"
case "$platform" in
  iphoneos) system="iOS"; sysroot="iphoneos"; target="16.3" ;;
  iphonesimulator) system="iOS"; sysroot="iphonesimulator"; target="16.3" ;;
  macosx) system="Darwin"; sysroot="macosx"; target="13.3" ;;
  *) echo "build-apple.sh: unsupported PLATFORM_NAME '$platform'" >&2; exit 1 ;;
esac
# Xcode's environment describes the app's target; CMake gets the same facts explicitly and must not inherit the rest.
unset SDKROOT IPHONEOS_DEPLOYMENT_TARGET MACOSX_DEPLOYMENT_TARGET CC CXX LD CPLUSPLUS CPP
libs=""
for arch in $archs; do
  case "$arch" in
    arm64|x86_64) ;;
    *) continue ;;
  esac
  build="build/apple/build-$platform-$arch"
  cmake -S . -B "$build" -G Ninja -DCMAKE_BUILD_TYPE=Release -DTWIDGET_BUILD_TESTS=OFF \
    -DCMAKE_SYSTEM_NAME="$system" -DCMAKE_OSX_SYSROOT="$sysroot" -DCMAKE_OSX_ARCHITECTURES="$arch" \
    -DCMAKE_OSX_DEPLOYMENT_TARGET="$target" >/dev/null
  cmake --build "$build" --target twidget_litehtml
  # One archive for the linker: the platform glue, the core and the fork.
  libtool -static -o "$build/libtwidget_litehtml_all.a" \
    "$build/libtwidget_litehtml.a" "$build/libtwidget_core.a" "$build/liblitehtml.a" 2>/dev/null
  libs="$libs $build/libtwidget_litehtml_all.a"
done
[ -n "$libs" ] || { echo "build-apple.sh: no supported arch in ARCHS='$archs'" >&2; exit 1; }
out="build/apple/$platform"
mkdir -p "$out"
# shellcheck disable=SC2086
lipo -create $libs -output "$out/libtwidget_litehtml.a"
echo "build-apple.sh: $out/libtwidget_litehtml.a ($archs)"
