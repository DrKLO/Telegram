#!/bin/bash

if [[ -z $ANDROID_NDK ]] || [[ ! -d $ANDROID_NDK ]] ; then
	echo "You need to set ANDROID_NDK environment variable, exiting"
	echo "Use: export ANDROID_NDK=/your/path/to/android-ndk-rxx"
	echo "e.g.: export ANDROID_NDK=/opt/android/android-ndk-r18b"
	exit 1
fi

set -u

ANDROID_API=21

ABIS=("armeabi-v7a" "arm64-v8a" "x86" "x86_64")

BASEDIR=`pwd`
NDK=${ANDROID_NDK}
HOST_NUM_CORES=$(nproc)

CFLAGS_="-DANDROID -fpic -fpie"
LDFLAGS_=""

configure() {
  ABI=$1;

  case $ABI in
    armeabi)
      NDK_ARCH="arm"
      NDK_ABIARCH="arm-linux-androideabi"
      CFLAGS="-march=armv5 -marm -finline-limit=64"
      LDFLAGS=""
      ASFLAGS=""
    ;;
    armeabi-v7a)
      NDK_ARCH="arm"
      NDK_ABIARCH="arm-linux-androideabi"
      CFLAGS="${CFLAGS_} -Os -march=armv7-a -mfloat-abi=softfp -mfpu=neon -mtune=cortex-a8 -mthumb -D__thumb__"
      LDFLAGS="${LDFLAGS_} -march=armv7-a"
      ASFLAGS=""
    ;;
    arm64-v8a)
      NDK_ARCH="arm64"
      NDK_ABIARCH="aarch64-linux-android"
      CFLAGS="${CFLAGS_} -O3 -march=armv8-a"
      LDFLAGS="${LDFLAGS_}"
      ASFLAGS=""
    ;;
    x86)
      NDK_ARCH="x86"
      NDK_ABIARCH="i686-linux-android"
      CFLAGS="${CFLAGS_} -O3 -march=i686 -mtune=intel -msse3 -mfpmath=sse -m32 -fPIC"
      LDFLAGS="-m32"
      ASFLAGS="-D__ANDROID__"
    ;;
    x86_64)
      NDK_ARCH="x86_64"
      NDK_ABIARCH="x86_64-linux-android"
      CFLAGS="${CFLAGS_} -O3 -march=x86-64 -mtune=intel -msse4.2 -mpopcnt -m64 -fPIC"
      LDFLAGS=""
      ASFLAGS="-D__ANDROID__"
    ;;
  esac

  TOOLCHAIN_PREFIX=${BASEDIR}/android-toolchain
  NDK_SYSROOT=${TOOLCHAIN_PREFIX}/sysroot

  if [[ -e ${TOOLCHAIN_PREFIX} ]]; then
      rm -rf ${TOOLCHAIN_PREFIX}
  fi

  [[ -d ${TOOLCHAIN_PREFIX} ]] || python ${NDK}/build/tools/make_standalone_toolchain.py \
     --arch ${NDK_ARCH} \
     --api ${ANDROID_API} \
     --stl libc++ \
     --install-dir=${TOOLCHAIN_PREFIX}

  PREFIX=../../android/${ABI}


  export PATH=${TOOLCHAIN_PREFIX}/bin:$PATH
  export CROSS_PREFIX=${TOOLCHAIN_PREFIX}/bin/${NDK_ABIARCH}-
  export CFLAGS="${CFLAGS}"
  export CPPFLAGS="${CFLAGS}"
  export CXXFLAGS="${CFLAGS} -std=c++11"
  export ASFLAGS="${ASFLAGS}"
  export LDFLAGS="${LDFLAGS} -L${NDK_SYSROOT}/usr/lib"

  export AR="${CROSS_PREFIX}ar"
  export AS="${CROSS_PREFIX}clang"
  export CC="${CROSS_PREFIX}clang"
  export CXX="${CROSS_PREFIX}clang++"
  export LD="${CROSS_PREFIX}ld"
  export STRIP="${CROSS_PREFIX}strip"
  export RANLIB="${CROSS_PREFIX}ranlib"
  export CPP="${CROSS_PREFIX}cpp"
  export NM="${CROSS_PREFIX}nm"

  echo "**********************************************"
  echo "### Use NDK=${NDK}"
  echo "### Use ANDROID_API=${ANDROID_API}"
  echo "### Install directory: PREFIX=${PREFIX}"
  echo "**********************************************"
}
