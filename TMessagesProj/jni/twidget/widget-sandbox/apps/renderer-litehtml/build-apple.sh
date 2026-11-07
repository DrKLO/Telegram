#!/bin/sh
# Builds libtwidget_litehtml.a (litehtml + gumbo + the renderer core + the
# CoreGraphics backend and C ABI, merged into one archive) for the platform and
# architectures of the current Xcode build (PLATFORM_NAME, ARCHS — set by Xcode
# when this runs as the pre-build script phase of the iOS / macOS demo;
# defaults to macOS arm64 from a shell) and puts the (fat) library in
# target/apple/<PLATFORM_NAME>/, where the demos' LIBRARY_SEARCH_PATHS points.
# The twin of renderer-blitz/build-apple.sh; needs cmake and ninja (Homebrew).
# The library targets iOS 16.3 / macOS 13.3 (std::to_chars for doubles in
# twidget/json.h needs those libc++ versions); the demos target 16.0 / 13.0, so
# the linker warns about the newer object and links anyway.
set -eu
cd "$(dirname "$0")"
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
  build="target/apple/build-$platform-$arch"
  cmake -S . -B "$build" -G Ninja -DCMAKE_BUILD_TYPE=Release \
    -DCMAKE_SYSTEM_NAME="$system" -DCMAKE_OSX_SYSROOT="$sysroot" -DCMAKE_OSX_ARCHITECTURES="$arch" \
    -DCMAKE_OSX_DEPLOYMENT_TARGET="$target" >/dev/null
  cmake --build "$build" --target twidget_litehtml
  # one archive for the linker: the core, litehtml and gumbo
  libtool -static -o "$build/libtwidget_litehtml_all.a" \
    "$build/libtwidget_litehtml.a" "$build/libtwidget_litehtml_core.a" \
    "$build/third_party/litehtml/liblitehtml.a" "$build/third_party/litehtml/src/gumbo/libgumbo.a" 2>/dev/null
  libs="$libs $build/libtwidget_litehtml_all.a"
done
[ -n "$libs" ] || { echo "build-apple.sh: no supported arch in ARCHS='$archs'" >&2; exit 1; }
out="target/apple/$platform"
mkdir -p "$out"
# shellcheck disable=SC2086
lipo -create $libs -output "$out/libtwidget_litehtml.a"
echo "build-apple.sh: $out/libtwidget_litehtml.a ($archs)"
