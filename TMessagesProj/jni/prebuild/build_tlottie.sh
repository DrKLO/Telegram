#!/usr/bin/env bash

set -Eeuo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "$SCRIPT_DIR/common.sh"
script_dir="$SCRIPT_DIR"
tlottie_dir=${TLOTTIE_SOURCE_DIR:-"$SCRIPT_DIR/../tlottie"}
tlottie_dir=$(CDPATH= cd -- "$tlottie_dir" && pwd)
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

# Cargo otherwise resolves rustc through PATH. That can mix a rustup Cargo with
# a Homebrew/system rustc whose sysroot does not contain the checked targets.
if [ -n "$rustup_toolchain" ]; then
  RUSTC=$(rustup which --toolchain "$rustup_toolchain" rustc)
else
  RUSTC=$(command -v rustc)
fi
export RUSTC

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
mkdir -p "$target_dir"
target_dir=$(CDPATH= cd -- "$target_dir" && pwd)
export CARGO_TARGET_DIR="$target_dir"

# rustc records source, dependency, sysroot, and OUT_DIR paths in object files.
# Give every machine the same virtual paths so those details do not make the
# resulting static archives depend on the checkout location or user account.
rust_sysroot=$(run_rustc --print sysroot)
cargo_home=${CARGO_HOME:-}
if [ -z "$cargo_home" ]; then
  cargo_home=$(dirname -- "$(dirname -- "$(command -v cargo)")")
  if [ -d "$cargo_home/registry" ]; then
    :
  elif [ -n "${HOME:-}" ] && [ -d "$HOME/.cargo" ]; then
    cargo_home="$HOME/.cargo"
  else
    cargo_home=
  fi
fi

# Do not inherit per-machine compiler flags. CARGO_ENCODED_RUSTFLAGS takes
# precedence over RUSTFLAGS and, unlike RUSTFLAGS, safely supports spaces in
# paths. Its separator is ASCII unit separator (0x1f).
unset RUSTFLAGS
CARGO_ENCODED_RUSTFLAGS=
append_rustflag() {
  if [ -n "$CARGO_ENCODED_RUSTFLAGS" ]; then
    CARGO_ENCODED_RUSTFLAGS="$CARGO_ENCODED_RUSTFLAGS$(printf '\037')"
  fi
  CARGO_ENCODED_RUSTFLAGS="$CARGO_ENCODED_RUSTFLAGS$1"
}
append_rustflag "--remap-path-prefix=$tlottie_dir=/tlottie"
append_rustflag "--remap-path-prefix=$target_dir=/tlottie-target"
append_rustflag "--remap-path-prefix=$rust_sysroot=/rust-sysroot"
append_rustflag "-Cdebuginfo=0"
if [ -n "$cargo_home" ]; then
  cargo_home=$(CDPATH= cd -- "$cargo_home" && pwd)
  append_rustflag "--remap-path-prefix=$cargo_home=/cargo-home"
fi
export CARGO_ENCODED_RUSTFLAGS
export CARGO_INCREMENTAL=0
export SOURCE_DATE_EPOCH=1
export TZ=UTC
export LC_ALL=C

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

for abi in $ABIS; do
  case "$abi" in
    arm64-v8a) rust_target=aarch64-linux-android ;;
    armeabi-v7a) rust_target=armv7-linux-androideabi ;;
    x86) rust_target=i686-linux-android ;;
    x86_64) rust_target=x86_64-linux-android ;;
    *) echo "error: unsupported ABI: $abi" >&2; exit 1 ;;
  esac

  destination="$(abi_output_dir "$abi")/libtlottie.a"

  echo "Building $abi ($rust_target)"
  run_cargo rustc \
    --manifest-path "$manifest" \
    --locked \
    --profile "$profile" \
    --target "$rust_target" \
    --lib \
    --no-default-features \
    --features "$features" \
    --crate-type staticlib \
    -- \
    -C metadata=tlottie-staticlib

  artifact="$target_dir/$rust_target/$profile/libtlottie.a"
  if [ ! -f "$artifact" ]; then
    echo "error: Cargo did not emit $artifact" >&2
    exit 1
  fi

  mkdir -p "$(dirname "$destination")"
  temporary_archive=$(mktemp "$(dirname "$destination")/.libtlottie.a.XXXXXX")
  cp "$artifact" "$temporary_archive"
  chmod 0644 "$temporary_archive"
  mv -f "$temporary_archive" "$destination"
  temporary_archive=
  finalize_archive "$destination"

  bytes=$(wc -c < "$destination" | tr -d ' ')
  echo "Wrote $destination ($bytes bytes)"
done

echo "All tlottie Android archives are ready."
