#!/usr/bin/env bash
# Regenerate the C++20 bindings and build Wallet Engine for all Android ABIs.
# Uses common.sh overrides: ABIS, API, JOBS, ANDROID_NDK_HOME, LIB_DIR, INCLUDE_DIR.
# Install Rust via rustup; the source's rust-toolchain.toml selects the version.

set -Eeuo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "$SCRIPT_DIR/common.sh"

require_ndk
require_command cargo
require_command rustup

# gram-wallet's Cargo manifest pins its path dependency to this submodule.
ENGINE_DIR="$JNI_DIR/wallet-engine"
if [[ ! -f "$ENGINE_DIR/Cargo.toml" ]]; then
    echo "ERROR: Wallet Engine sources not found: $ENGINE_DIR" >&2
    echo "       Initialize the TMessagesProj/jni/wallet-engine submodule." >&2
    exit 1
fi
ENGINE_DIR="$(cd "$ENGINE_DIR" && pwd)"
ADAPTER_DIR="$JNI_DIR/gram-wallet"
BUILD_DIR="${WALLET_ENGINE_BUILD_DIR:-$SCRIPT_DIR/build/wallet-engine}"
mkdir -p "$BUILD_DIR" "$INCLUDE_DIR/wallet-engine"
BUILD_DIR="$(cd "$BUILD_DIR" && pwd)"
INCLUDE_DIR="$(cd "$INCLUDE_DIR" && pwd)"
mkdir -p "$LIB_DIR"
LIB_DIR="$(cd "$LIB_DIR" && pwd)"

# The app adapter links the engine and reexports its UniFFI scaffolding. Build
# one archive to avoid linking two copies of Rust and wallet-engine into JNI.
cd "$ADAPTER_DIR"
export RUSTC="$(rustup which rustc)"
export CARGO_BUILD_JOBS="$JOBS"
export CARGO_INCREMENTAL=0

rust_target_for_abi() {
    case "$1" in
        arm64-v8a) echo aarch64-linux-android ;;
        armeabi-v7a) echo armv7-linux-androideabi ;;
        x86_64) echo x86_64-linux-android ;;
        x86) echo i686-linux-android ;;
        *) echo "ERROR: unsupported ABI: $1" >&2; return 1 ;;
    esac
}

installed_targets="$(rustup target list --installed)"
missing_targets=()
for abi in $ABIS; do
    target="$(rust_target_for_abi "$abi")"
    if [[ $'\n'"$installed_targets"$'\n' != *$'\n'"$target"$'\n'* ]]; then
        missing_targets+=("$target")
    fi
done
if (( ${#missing_targets[@]} )); then
    echo "ERROR: missing Rust Android targets. From $ENGINE_DIR run:" >&2
    echo "       rustup target add ${missing_targets[*]}" >&2
    exit 1
fi

# Use stable virtual paths in Rust objects, as common.sh does for C/C++.
rust_sysroot="$(rustc --print sysroot)"
rust_cargo_home="${CARGO_HOME:-$HOME/.cargo}"
rustflags=(
    "--remap-path-prefix=$ENGINE_DIR=/wallet-engine"
    "--remap-path-prefix=$ADAPTER_DIR=/gram-wallet"
    "--remap-path-prefix=$BUILD_DIR=/wallet-engine-target"
    "--remap-path-prefix=$rust_sysroot=/rust-sysroot"
    "--remap-path-prefix=$rust_cargo_home=/cargo-home"
    "-Cdebuginfo=0"
    "-Crelocation-model=pic"
)
unset RUSTFLAGS
CARGO_ENCODED_RUSTFLAGS="$(IFS=$'\037'; echo "${rustflags[*]}")"
export CARGO_ENCODED_RUSTFLAGS

# Equivalent to `cargo xtask bindings cpp`, with build outputs kept in prebuild.
# The pinned generator reads UniFFI metadata from the matching host library.
case "$host_os" in
    Darwin) host_library=libgram_wallet.dylib ;;
    Linux) host_library=libgram_wallet.so ;;
esac
echo "Generating Wallet Engine C++ bindings with $(rustc --version)"
cargo rustc --manifest-path "$ADAPTER_DIR/Cargo.toml" \
    --target-dir "$BUILD_DIR/host" --locked --release --lib --crate-type cdylib
cargo run --manifest-path "$ENGINE_DIR/bindgen/cpp/bindgen/Cargo.toml" \
    --target-dir "$BUILD_DIR/bindgen" --locked -- \
    --library --out-dir "$BUILD_DIR/bindings" \
    "$BUILD_DIR/host/release/$host_library"

for generated in wallet_engine.hpp wallet_engine.cpp wallet_engine_scaffolding.hpp \
                 gram_wallet.hpp gram_wallet.cpp gram_wallet_scaffolding.hpp; do
    cp "$BUILD_DIR/bindings/$generated" "$INCLUDE_DIR/wallet-engine/$generated"
    chmod 0644 "$INCLUDE_DIR/wallet-engine/$generated"
done

for abi in $ABIS; do
    target="$(rust_target_for_abi "$abi")"
    # NDK names its ARMv7 compiler armv7a, while Rust uses armv7.
    case "$abi" in
        armeabi-v7a) clang_prefix=armv7a-linux-androideabi ;;
        *) clang_prefix="$target" ;;
    esac
    linker="$NDK_TOOLCHAIN/bin/${clang_prefix}${API}-clang"
    if [[ ! -x "$linker" ]]; then
        echo "ERROR: Android compiler not found: $linker" >&2
        exit 1
    fi
    target_key="$(echo "$target" | tr '[:lower:]-' '[:upper:]_')"
    cc_key="${target//-/_}"

    echo "Building Wallet Engine: $abi (API $API)"
    env "CARGO_TARGET_${target_key}_LINKER=$linker" \
        "CC_${cc_key}=$linker" \
        "CXX_${cc_key}=${linker}++" \
        "AR_${cc_key}=$NDK_TOOLCHAIN/bin/llvm-ar" \
        cargo rustc --manifest-path "$ADAPTER_DIR/Cargo.toml" \
        --target-dir "$BUILD_DIR/android" --locked --release --lib \
        --target "$target" --crate-type staticlib

    destination="$(abi_output_dir "$abi")/libwallet_engine.a"
    copy_archive "$BUILD_DIR/android/$target/release/libgram_wallet.a" "$destination"
    # tlottie's no_std runtime defines its own rust_eh_personality. Namespace
    # this archive's definition AND references (including the DWARF anchor)
    # so both Rust libraries can be linked into the same JNI shared library.
    require_llvm_objcopy
    "$LLVM_OBJCOPY_BIN" \
        --redefine-sym rust_eh_personality=wallet_engine_rust_eh_personality \
        --redefine-sym DW.ref.rust_eh_personality=DW.ref.wallet_engine_rust_eh_personality \
        "$destination"
    finalize_archive "$destination"
    echo "Wrote $destination"
done

echo "Wallet Engine archives: $LIB_DIR/<abi>/libwallet_engine.a"
echo "Wallet Engine C++ bindings: $INCLUDE_DIR/wallet-engine"
