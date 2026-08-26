#!/bin/sh
#
# Runs inside the tlottie build container. /work is the host repo root
# (bind-mounted read-write, so output ABI directories live at
# /work/<abi>/), /tlottie is the tlottie source (bind-mounted read-only).
# The toolchain itself (rustc, cargo, Android targets, build-essential)
# is already baked into the image at `docker build` time - see Dockerfile.

set -eu

tlottie_dir=/tlottie
manifest="$tlottie_dir/Cargo.toml"
profile=release-nostd
features=cpu,no-std,c-api
out_root=/work

if [ ! -f "$manifest" ]; then
  echo "error: tlottie Cargo.toml not found at $manifest" >&2
  exit 1
fi

# Keep cargo's target dir inside the container, not on the bind-mounted
# host repo - it's build-only scratch space and would otherwise leave a
# large, host-owned cache sitting in the checkout.
target_dir=/build/target
mkdir -p "$target_dir"
export CARGO_TARGET_DIR="$target_dir"

rust_sysroot=$(rustc --print sysroot)

# Do not inherit per-machine compiler flags. CARGO_ENCODED_RUSTFLAGS takes
# precedence over RUSTFLAGS and safely supports spaces in paths.
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
append_rustflag "--remap-path-prefix=$CARGO_HOME=/cargo-home"
export CARGO_ENCODED_RUSTFLAGS

echo "Building tlottie Android archives"
echo "  source:   $tlottie_dir"
echo "  rustc:    $(rustc --version)"
echo "  profile:  $profile"
echo "  features: $features"

for target_spec in \
  arm64-v8a:aarch64-linux-android \
  armeabi-v7a:armv7-linux-androideabi \
  x86:i686-linux-android \
  x86_64:x86_64-linux-android
do
  abi=${target_spec%%:*}
  rust_target=${target_spec#*:}
  destination="$out_root/$abi/libtlottie.a"

  echo "Building $abi ($rust_target)"
  cargo rustc \
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
    echo "error: cargo did not emit $artifact" >&2
    exit 1
  fi
  if [ ! -d "$out_root/$abi" ]; then
    echo "error: output directory does not exist: $out_root/$abi" >&2
    exit 1
  fi

  # Copy through a file in the destination directory so replacement is atomic.
  tmp=$(mktemp "$out_root/$abi/.libtlottie.a.XXXXXX")
  cp "$artifact" "$tmp"
  chmod 0644 "$tmp"
  mv -f "$tmp" "$destination"

  # The container runs as root, so without this the archive would land in
  # the host checkout owned by root on native Linux hosts. HOST_UID/GID are
  # passed in by the top-level build.sh.
  if [ -n "${HOST_UID:-}" ] && [ -n "${HOST_GID:-}" ]; then
    chown "$HOST_UID:$HOST_GID" "$destination"
  fi

  bytes=$(wc -c < "$destination" | tr -d ' ')
  echo "Wrote $destination ($bytes bytes)"
done

echo "All tlottie Android archives are ready."