#!/bin/bash
#apply fix http://permalink.gmane.org/gmane.comp.video.ffmpeg.devel/203198

function build_one {

echo "Cleaning..."
make clean

echo "Configuring..."

./configure \
--cc=$CC \
--nm=$NM \
--enable-stripping \
--arch=$ARCH \
--cpu=$CPU \
--target-os=linux \
--enable-cross-compile \
--yasmexe=$NDK/prebuilt/darwin-x86_64/bin/yasm \
--prefix=$PREFIX \
--enable-pic \
--disable-shared \
--enable-static \
--cross-prefix=$CROSS_PREFIX \
--sysroot=$PLATFORM \
--extra-cflags="-Os -DANDROID $OPTIMIZE_CFLAGS -fPIE -pie --static" \
--extra-ldflags="-Wl,-rpath-link=$PLATFORM/usr/lib -L$PLATFORM/usr/lib -nostdlib -lc -lm -ldl" \
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
--enable-pthreads \
--enable-protocol=file \
--enable-decoder=h264 \
--enable-decoder=gif \
--enable-demuxer=mov \
--enable-demuxer=gif \
--enable-hwaccels \
--enable-runtime-cpudetect \
--enable-asm \
$ADDITIONAL_CONFIGURE_FLAG

#echo "continue?"
#read
make -j8 install

}

NDK=/Applications/sdk/ndk-bundle

#arm platform
PLATFORM=$NDK/platforms/android-9/arch-arm
PREBUILT=$NDK/toolchains/arm-linux-androideabi-4.8/prebuilt/darwin-x86_64
LD=$PREBUILT/bin/arm-linux-androideabi-ld
AR=$PREBUILT/bin/arm-linux-androideabi-ar
NM=$PREBUILT/bin/arm-linux-androideabi-nm
GCCLIB=$PREBUILT/lib/gcc/arm-linux-androideabi/4.8/libgcc.a
ARCH=arm
CC=$PREBUILT/bin/arm-linux-androideabi-gcc
CROSS_PREFIX=$PREBUILT/bin/arm-linux-androideabi-

#arm v5
CPU=armv5te
OPTIMIZE_CFLAGS="-marm -march=$CPU"
PREFIX=./android/$CPU
ADDITIONAL_CONFIGURE_FLAG="--disable-armv6 --disable-armv6t2 --disable-vfp --disable-neon"
build_one

#arm v7n
CPU=armv7-a
OPTIMIZE_CFLAGS="-marm -march=$CPU"
PREFIX=./android/$CPU
ADDITIONAL_CONFIGURE_FLAG=--enable-neon
build_one

#x86 platform
PLATFORM=$NDK/platforms/android-9/arch-x86
PREBUILT=$NDK/toolchains/x86-4.8/prebuilt/darwin-x86_64
LD=$PREBUILT/bin/i686-linux-android-ld
AR=$PREBUILT/bin/i686-linux-android-ar
NM=$PREBUILT/bin/i686-linux-android-nm
GCCLIB=$PREBUILT/lib/gcc/i686-linux-android/4.8/libgcc.a
ARCH=x86
CC=$PREBUILT/bin/i686-linux-android-gcc
CROSS_PREFIX=$PREBUILT/bin/i686-linux-android-

CPU=i686
OPTIMIZE_CFLAGS="-march=$CPU"
PREFIX=./android/$CPU
ADDITIONAL_CONFIGURE_FLAG="--disable-mmx --disable-yasm"
build_one

