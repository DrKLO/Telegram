#!/bin/sh

set -eu

script_dir=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
tlottie_dir=${TLOTTIE_SOURCE_DIR:-"$script_dir/../tlottie"}
manifest="$tlottie_dir/Cargo.toml"
profile=release-nostd
features=cpu,no-std,c-api

if [ ! -f "$manifest" ]; then
  echo "error: tlottie Cargo.toml not found at $manifest" >&2
  echo "set TLOTTIE_SOURCE_DIR if the source is not next to tlottie_lib" >&2
  exit 1
fi

rustup_toolchain=
if command -v rustup >/dev/null 2>&1; then
  rustup_toolchain=${TLOTTIE_RUST_TOOLCHAIN:-stable}
fi

run_cargo() {
  if [ -n "$rustup_toolchain" ]; then
    rustup run "$rustup_toolchain" cargo "$@"
  else
    cargo "$@"
  fi
}

run_rustc() {
  if [ -n "$rustup_toolchain" ]; then
    rustup run "$rustup_toolchain" rustc "$@"
  else
    rustc "$@"
  fi
}

if [ -n "$rustup_toolchain" ]; then
  missing_targets=
  for target in \
    aarch64-linux-android \
    armv7-linux-androideabi \
    i686-linux-android \
    x86_64-linux-android
  do
    if ! rustup target list --toolchain "$rustup_toolchain" --installed | grep -qx "$target"; then
      missing_targets="$missing_targets $target"
    fi
  done
  if [ -n "$missing_targets" ]; then
    echo "error: Rust Android targets are not installed:$missing_targets" >&2
    echo "install them with: rustup target add --toolchain $rustup_toolchain$missing_targets" >&2
    exit 1
  fi
fi

target_dir=${CARGO_TARGET_DIR:-"$tlottie_dir/target/no-std-android"}
export CARGO_TARGET_DIR="$target_dir"

echo "Building tlottie Android archives"
echo "  source:    $tlottie_dir"
echo "  rustc:     $(run_rustc --version)"
echo "  profile:   $profile"
echo "  features:  $features"
echo "  target dir: $target_dir"

temporary_archive=
cleanup() {
  if [ -n "$temporary_archive" ] && [ -f "$temporary_archive" ]; then
    unlink "$temporary_archive"
  fi
}
trap cleanup EXIT HUP INT TERM

for target_spec in \
  arm64-v8a:aarch64-linux-android \
  armeabi-v7a:armv7-linux-androideabi \
  x86:i686-linux-android \
  x86_64:x86_64-linux-android
do
  abi=${target_spec%%:*}
  rust_target=${target_spec#*:}
  destination="$script_dir/$abi/libtlottie.a"

  echo "Building $abi ($rust_target)"
  run_cargo rustc \
    --manifest-path "$manifest" \
    --locked \
    --profile "$profile" \
    --target "$rust_target" \
    --lib \
    --no-default-features \
    --features "$features" \
    --crate-type staticlib

  artifact="$target_dir/$rust_target/$profile/libtlottie.a"
  if [ ! -f "$artifact" ]; then
    echo "error: Cargo did not emit $artifact" >&2
    exit 1
  fi
  if [ ! -d "$script_dir/$abi" ]; then
    echo "error: output directory does not exist: $script_dir/$abi" >&2
    exit 1
  fi

  # Copy through a file in the destination directory so replacement is atomic.
  temporary_archive=$(mktemp "$script_dir/$abi/.libtlottie.a.XXXXXX")
  cp "$artifact" "$temporary_archive"
  chmod 0644 "$temporary_archive"
  mv -f "$temporary_archive" "$destination"
  temporary_archive=

  bytes=$(wc -c < "$destination" | tr -d ' ')
  echo "Wrote $destination ($bytes bytes)"
done

echo "All tlottie Android archives are ready."
