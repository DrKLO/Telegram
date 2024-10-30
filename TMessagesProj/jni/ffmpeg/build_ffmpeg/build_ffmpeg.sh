#!/bin/bash

# instructions for build
# used
# ffmpeg 4.4.3
# lib vpx 1.10.9
# NDK for compile libvpx. Last successful build with 21.1.6352462
# NDK r10e for compile ffmpeg
#
# 1) download ffmpeg
# 2) set NDK_r10e and NDK variables
# 3) download lib vpx
# 4) copy libvpx to vpx-android folder and rename as libvpx
# 5) copy build_ffmpeg foleder in ffmepg directory
# 6) run build_ffmpeg.sh
# 7) see compiled library in build_ffmpeg/adnroid folder

NDK="/opt/android/ndk/android-ndk-r21e"
NDK_r10e="/opt/android/ndk/android-ndk-r10e"

#build vpx
cd ./vpx-android
export ANDROID_NDK=$NDK
sh build-vpx.sh
cd ..


NDK=$NDK_r10e

function build_one {

echo "Cleaning..."
rm config.h
make clean

echo "Configuring..."

INCLUDES=" -I${PREFIX}/include"
LIBS=" -L${PREFIX}/lib"

./configure \
--cc=$CC \
--nm=$NM \
--enable-stripping \
--arch=$ARCH \
--target-os=linux \
--enable-cross-compile \
--yasmexe=$NDK/prebuilt/darwin-x86_64/bin/yasm \
--prefix=$PREFIX \
--enable-pic \
--disable-shared \
--enable-static \
--enable-asm \
--enable-inline-asm \
--cross-prefix=$CROSS_PREFIX \
--sysroot=$PLATFORM \
--extra-cflags="${INCLUDES} -Wl,-Bsymbolic -Os -DCONFIG_LINUX_PERF=0 -DANDROID $OPTIMIZE_CFLAGS -fPIE -pie --static -fPIC" \
--extra-ldflags="${LIBS} -Wl,-Bsymbolic -Wl,-rpath-link=$PLATFORM/usr/lib -L$PLATFORM/usr/lib -nostdlib -lc -lm -ldl -fPIC" \
--extra-libs="-lgcc" \
\
--enable-version3 \
--enable-gpl \
\
--disable-doc \
--disable-avx \
\
--disable-everything \
--disable-network \
--disable-zlib \
--disable-avfilter \
--disable-avdevice \
--disable-postproc \
--disable-debug \
--disable-programs \
--disable-network \
\
--enable-libvpx \
--enable-decoder=libvpx_vp9 \
--enable-encoder=libvpx_vp9 \
--enable-muxer=matroska \
--enable-bsf=vp9_superframe \
--enable-bsf=vp9_raw_reorder \
--enable-runtime-cpudetect \
--enable-pthreads \
--enable-avresample \
--enable-swscale \
--enable-protocol=file \
--enable-decoder=h264 \
--enable-decoder=h265 \
--enable-decoder=mpeg4 \
--enable-decoder=mjpeg \
--enable-decoder=gif \
--enable-decoder=alac \
--enable-decoder=opus \
--enable-decoder=mp3 \
--enable-decoder=aac \
--enable-demuxer=mov \
--enable-demuxer=gif \
--enable-demuxer=ogg \
--enable-demuxer=matroska \
--enable-demuxer=mp3 \
--enable-demuxer=aac \
--enable-hwaccels \
--enable-runtime-cpudetect \
$ADDITIONAL_CONFIGURE_FLAG

#echo "continue?"
#read
make -j8 install

}

#x86_64
PREBUILT=$NDK/toolchains/x86_64-4.9/prebuilt/darwin-x86_64
PLATFORM=$NDK/platforms/android-21/arch-x86_64
LD=$PREBUILT/bin/x86_64-linux-android-ld
AR=$PREBUILT/bin/x86_64-linux-android-ar
NM=$PREBUILT/bin/x86_64-linux-android-nm
GCCLIB=$PREBUILT/lib/gcc/x86_64-linux-android/4.9/libgcc.a
CC=$PREBUILT/bin/x86_64-linux-android-gcc
CROSS_PREFIX=$PREBUILT/bin/x86_64-linux-android-
ARCH=x86_64
CPU=x86_64
PREFIX=./android/x86_64
ADDITIONAL_CONFIGURE_FLAG="--disable-mmx --disable-inline-asm"
build_one

#arm64-v8a
PREBUILT=$NDK/toolchains/aarch64-linux-android-4.9/prebuilt/darwin-x86_64
PLATFORM=$NDK/platforms/android-21/arch-arm64
LD=$PREBUILT/bin/aarch64-linux-android-ld
AR=$PREBUILT/bin/aarch64-linux-android-ar
NM=$PREBUILT/bin/aarch64-linux-android-nm
GCCLIB=$PREBUILT/lib/gcc/aarch64-linux-android/4.9/libgcc.a
CC=$PREBUILT/bin/aarch64-linux-android-gcc
CROSS_PREFIX=$PREBUILT/bin/aarch64-linux-android-
ARCH=arm64
CPU=arm64-v8a
OPTIMIZE_CFLAGS=
PREFIX=./android/arm64-v8a
ADDITIONAL_CONFIGURE_FLAG="--enable-neon --enable-optimizations"
build_one

#arm v7n
PREBUILT=$NDK/toolchains/arm-linux-androideabi-4.9/prebuilt/darwin-x86_64
PLATFORM=$NDK/platforms/android-16/arch-arm
LD=$PREBUILT/bin/arm-linux-androideabi-ld
AR=$PREBUILT/bin/arm-linux-androideabi-ar
NM=$PREBUILT/bin/arm-linux-androideabi-nm
GCCLIB=$PREBUILT/lib/gcc/arm-linux-androideabi/4.9/libgcc.a
CC=$PREBUILT/bin/arm-linux-androideabi-gcc
CROSS_PREFIX=$PREBUILT/bin/arm-linux-androideabi-
ARCH=arm
CPU=armv7-a
OPTIMIZE_CFLAGS="-marm -march=$CPU"
PREFIX=./android/armeabi-v7a
ADDITIONAL_CONFIGURE_FLAG=--enable-neon
build_one

#x86
PREBUILT=$NDK/toolchains/x86-4.9/prebuilt/darwin-x86_64
PLATFORM=$NDK/platforms/android-16/arch-x86
LD=$PREBUILT/bin/i686-linux-android-ld
AR=$PREBUILT/bin/i686-linux-android-ar
NM=$PREBUILT/bin/i686-linux-android-nm
GCCLIB=$PREBUILT/lib/gcc/i686-linux-android/4.9/libgcc.a
CC=$PREBUILT/bin/i686-linux-android-gcc
CROSS_PREFIX=$PREBUILT/bin/i686-linux-android-
ARCH=x86
CPU=i686
OPTIMIZE_CFLAGS="-march=$CPU"
PREFIX=./android/x86
ADDITIONAL_CONFIGURE_FLAG="--disable-mmx --disable-yasm"
build_one

  if [[ -e ./build_ffmpeg/android ]]; then
      rm -rf ./build_ffmpeg/android
  fi

mv ./android ./build_ffmpeg/


