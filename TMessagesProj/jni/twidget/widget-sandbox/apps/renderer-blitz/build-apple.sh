#!/bin/sh
# Builds libtwidget_blitz.a for the platform and architectures of the current
# Xcode build (PLATFORM_NAME, ARCHS — set by Xcode when this runs as the
# pre-build script phase of the iOS / macOS demo; defaults to macOS arm64 from
# a shell) and puts the (fat) library in target/apple/<PLATFORM_NAME>/, where
# the demos' LIBRARY_SEARCH_PATHS points. Needs rustup's cargo (Homebrew's has
# no iOS targets); missing targets are added on first use.
set -eu
cd "$(dirname "$0")"
export PATH="/opt/homebrew/opt/rustup/bin:$HOME/.cargo/bin:$PATH"
platform="${PLATFORM_NAME:-macosx}"
archs="${ARCHS:-arm64}"
case "$platform" in
  iphoneos) os="ios" ;;
  iphonesimulator) os="ios-sim" ;;
  macosx) os="darwin" ;;
  *) echo "build-apple.sh: unsupported PLATFORM_NAME '$platform'" >&2; exit 1 ;;
esac
# Xcode's SDK environment describes the app's target; cargo must not apply it
# to the build scripts and proc-macros it compiles for the host.
unset SDKROOT IPHONEOS_DEPLOYMENT_TARGET MACOSX_DEPLOYMENT_TARGET
libs=""
for arch in $archs; do
  case "$arch" in
    arm64) triple="aarch64-apple-$os" ;;
    x86_64) triple="x86_64-apple-$os" ;;
    *) continue ;;
  esac
  # The x86_64 simulator target has no -sim suffix.
  [ "$triple" = "x86_64-apple-ios-sim" ] && triple="x86_64-apple-ios"
  rustup target list --installed | grep -qx "$triple" || rustup target add "$triple"
  cargo build --release --lib --target "$triple"
  libs="$libs target/$triple/release/libtwidget_blitz.a"
done
[ -n "$libs" ] || { echo "build-apple.sh: no supported arch in ARCHS='$archs'" >&2; exit 1; }
out="target/apple/$platform"
mkdir -p "$out"
# shellcheck disable=SC2086
lipo -create $libs -output "$out/libtwidget_blitz.a"
echo "build-apple.sh: $out/libtwidget_blitz.a ($archs)"
