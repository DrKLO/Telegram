#!/bin/sh
#
# Top-level orchestrator for the tlottie Android build.
#
# Responsibilities kept here (outside the container):
#   1. remove stale libtlottie.a files, so a failed build can never leave a
#      previous artifact looking up to date
#   2. build the tlottie Docker image
#   3. run it with the right mounts and platform pin
#
# Everything about *how* tlottie itself is compiled (rustc flags, profile,
# features, per-ABI loop) lives in scripts/tlottie/entrypoint.sh, which runs
# inside the container - see that file to change build settings.
#
# No environment variables are read - paths and settings are hardcoded
# below. Layout this script expects (relative to this file):
#   arm64-v8a/, armeabi-v7a/, x86/, x86_64/   - output directories
#   scripts/tlottie/Dockerfile                - build environment for tlottie
#   ../tlottie                                 - tlottie source (sibling of
#                                                 this repo checkout)

set -eu

script_dir=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
tlottie_dir=$(CDPATH= cd -- "$script_dir/../tlottie" && pwd)
dockerfile_dir="$script_dir/scripts/tlottie"
image_tag=tlottie-build
platform=linux/amd64

if [ ! -f "$tlottie_dir/Cargo.toml" ]; then
  echo "error: tlottie Cargo.toml not found at $tlottie_dir/Cargo.toml" >&2
  exit 1
fi

echo "Removing previous tlottie archives"
for abi in arm64-v8a armeabi-v7a x86 x86_64; do
  dest="$script_dir/$abi/libtlottie.a"
  if [ -f "$dest" ]; then
    rm -f "$dest"
    echo "  removed $dest"
  fi
  if [ ! -d "$script_dir/$abi" ]; then
    echo "error: output directory does not exist: $script_dir/$abi" >&2
    exit 1
  fi
done

echo "Building tlottie build image ($platform)"
# Plain "docker build" already uses BuildKit (and loads the result into the
# local image store) on any reasonably current Docker install. Swap for
# "docker buildx build --load ..." if your setup needs an explicit builder.
docker build \
  --platform "$platform" \
  -t "$image_tag" \
  "$dockerfile_dir"

echo "Running tlottie build in container"
docker run --rm \
  --platform "$platform" \
  -v "$script_dir:/work" \
  -v "$tlottie_dir:/tlottie:ro" \
  -e HOST_UID="$(id -u)" \
  -e HOST_GID="$(id -g)" \
  "$image_tag"

echo "All tlottie Android archives are ready."