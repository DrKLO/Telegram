#!/usr/bin/env bash
# Clean all prebuilt archives and build every bundled static library in order.

set -Eeuo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "$SCRIPT_DIR/common.sh"

print_common_config

BUILD_ENV_FILE="$SCRIPT_DIR/build_env.txt"
write_build_environment "$BUILD_ENV_FILE"

echo
echo "==> Removing previous outputs"
echo "    $LIB_DIR"
echo "    $INCLUDE_DIR"
echo "    $PKGCONFIG_DIR"
rm -rf "$LIB_DIR" "$INCLUDE_DIR" "$PKGCONFIG_DIR"
mkdir -p "$LIB_DIR" "$INCLUDE_DIR" "$PKGCONFIG_DIR"

build_scripts=(
    build_boringssl.sh
    build_openh264.sh
    build_opus.sh
    build_libvpx.sh
    build_dav1d.sh
    build_ffmpeg.sh
    build_tlottie.sh
    build_tdlib.sh
    build_wamr.sh
)

for script in "${build_scripts[@]}"; do
    echo
    echo "============================================================"
    echo "==> Running $script"
    echo "============================================================"
    "$SCRIPT_DIR/$script"
done

echo
echo "All prebuilt libraries were built successfully into: $LIB_DIR"
echo "Generated/ABI-dependent headers: $INCLUDE_DIR"
echo "Build environment: $BUILD_ENV_FILE"
if [[ -d "$PKGCONFIG_DIR" ]]; then
    echo "WARNING: temporary pkg-config directory still exists: $PKGCONFIG_DIR" >&2
fi
