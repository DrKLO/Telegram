#!/bin/bash

PREFIX="$(pwd)/../android"
mkdir -p "$PREFIX"
echo "Building dav1d into $PREFIX"

pushd dav1d

meson setup builddir-arm64 \
  --prefix "$PREFIX/arm64-v8a" \
  --libdir="lib" \
  --includedir="include" \
  --buildtype=release -Denable_tests=false -Denable_tools=false -Ddefault_library=static \
  --cross-file <(echo "
    [binaries]
    c = '${ANDROID_NDK}/toolchains/llvm/prebuilt/darwin-x86_64/bin/aarch64-linux-android21-clang'
    ar = '${ANDROID_NDK}/toolchains/llvm/prebuilt/darwin-x86_64/bin/aarch64-linux-android-ar'
    
    [host_machine]
    system = 'android'
    cpu_family = 'aarch64'
    cpu = 'arm64'
    endian = 'little'
  ")
ninja -C builddir-arm64
ninja -C builddir-arm64 install

meson setup builddir-armv7 \
  --prefix "$PREFIX/armeabi-v7a" \
  --libdir="lib" \
  --includedir="include" \
  --buildtype=release -Denable_tests=false -Denable_tools=false -Ddefault_library=static \
  --cross-file <(echo "
    [binaries]
    c = '${ANDROID_NDK}/toolchains/llvm/prebuilt/darwin-x86_64/bin/armv7a-linux-androideabi21-clang'
    ar = '${ANDROID_NDK}/toolchains/llvm/prebuilt/darwin-x86_64/bin/arm-linux-androideabi-ar'
    
    [host_machine]
    system = 'android'
    cpu_family = 'arm'
    cpu = 'armv7'
    endian = 'little'
  ") \
  -Dc_args="-DDAV1D_NO_GETAUXVAL"
ninja -C builddir-armv7
ninja -C builddir-armv7 install

meson setup builddir-x86 \
  --prefix "$PREFIX/x86" \
  --libdir="lib" \
  --includedir="include" \
  --buildtype=release -Denable_tests=false -Denable_tools=false -Ddefault_library=static \
  --cross-file <(echo "
    [binaries]
    c = '${ANDROID_NDK}/toolchains/llvm/prebuilt/darwin-x86_64/bin/i686-linux-android21-clang'
    ar = '${ANDROID_NDK}/toolchains/llvm/prebuilt/darwin-x86_64/bin/i686-linux-android-ar'
    
    [host_machine]
    system = 'android'
    cpu_family = 'x86'
    cpu = 'i686'
    endian = 'little'
  ")
ninja -C builddir-x86
ninja -C builddir-x86 install

meson setup builddir-x86_64 \
  --prefix "$PREFIX/x86_64" \
  --libdir="lib" \
  --includedir="include" \
  --buildtype=release -Denable_tests=false -Denable_tools=false -Ddefault_library=static \
  --cross-file <(echo "
    [binaries]
    c = '${ANDROID_NDK}/toolchains/llvm/prebuilt/darwin-x86_64/bin/x86_64-linux-android21-clang'
    ar = '${ANDROID_NDK}/toolchains/llvm/prebuilt/darwin-x86_64/bin/x86_64-linux-android-ar'
    
    [host_machine]
    system = 'android'
    cpu_family = 'x86_64'
    cpu = 'x86_64'
    endian = 'little'
  ")
ninja -C builddir-x86_64
ninja -C builddir-x86_64 install

popd

