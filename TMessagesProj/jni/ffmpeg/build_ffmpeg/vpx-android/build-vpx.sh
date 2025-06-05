#!/bin/bash

set -u
. _settings.sh

LIB_VPX="libvpx"
LIB_GIT=v1.10.0

if [[ -f "${LIB_VPX}/build/make/version.sh" ]]; then
  version=`"${LIB_VPX}/build/make/version.sh" --bare "${LIB_VPX}"`
fi

configure_make() {
  pushd "${LIB_VPX}" || exit
  ABI=$1;
  echo -e "\n** BUILD STARTED: ${LIB_VPX} (${version}) for ${ABI} **"

  configure "$@"
  case ${ABI} in
    armeabi)
      TARGET="armv7-android-gcc --disable-neon --disable-neon-asm"
    ;;
	armeabi-v7a)
      TARGET="armv7-android-gcc --enable-neon --disable-neon-asm"
    ;;
    arm64-v8a)
      TARGET="arm64-android-gcc"
    ;;
    x86)
      TARGET="x86-android-gcc"
    ;;
    x86_64)
      TARGET="x86_64-android-gcc"
    ;;
    mips)
      TARGET="mips32-linux-gcc"
    ;;
    mips64)
      TARGET="mips64-linux-gcc"
    ;;
  esac

make clean

CPU_DETECT="--disable-runtime-cpu-detect"
if [[ $1 =~ x86.* ]]; then
   CPU_DETECT="--enable-runtime-cpu-detect"
fi

  ./configure \
    --extra-cflags="-isystem ${NDK_SYSROOT}/usr/include/${NDK_ABIARCH} -isystem ${NDK_SYSROOT}/usr/include" \
    --libc=${NDK_SYSROOT} \
    --prefix=${PREFIX} \
    --target=${TARGET} \
    ${CPU_DETECT} \
    --as=auto \
    --disable-docs \
    --enable-pic \
    --enable-libyuv \
    --enable-static \
    --enable-small \
    --enable-optimizations \
    --enable-better-hw-compatibility \
    --enable-realtime-only \
    --enable-vp8 \
    --enable-vp9 \
    --disable-webm-io \
    --disable-examples \
    --disable-tools \
    --disable-debug \
    --disable-neon-asm \
    --disable-neon-dotprod \
    --disable-unit-tests || exit 1

  make -j${HOST_NUM_CORES} install
  popd || true
}

for ((i=0; i < ${#ABIS[@]}; i++))
do
  if [[ $# -eq 0 ]] || [[ "$1" == "${ABIS[i]}" ]]; then
    [[ ${ANDROID_API} -lt 21 ]] && ( echo "${ABIS[i]}" | grep 64 > /dev/null ) && continue;
    configure_make "${ABIS[i]}"
    echo -e "** BUILD COMPLETED: ${LIB_VPX} for ${ABIS[i]} **\n\n"
  fi
done
