#!/bin/bash

function build_one {

echo "Cleaning..."
rm config.h
make clean

echo "Configuring..."

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
--extra-cflags="-Wl,-Bsymbolic -Os -DCONFIG_LINUX_PERF=0 -DANDROID $OPTIMIZE_CFLAGS -fPIE -pie --static -fPIC" \
--extra-ldflags="-Wl,-Bsymbolic -Wl,-rpath-link=$PLATFORM/usr/lib -L$PLATFORM/usr/lib -nostdlib -lc -lm -ldl -fPIC" \
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
--enable-runtime-cpudetect \
--enable-pthreads \
--enable-avresample \
--enable-protocol=file \
--enable-decoder=h264 \
--enable-decoder=mpeg4 \
--enable-decoder=gif \
--enable-decoder=alac \
--enable-demuxer=mov \
--enable-demuxer=gif \
--enable-hwaccels \
--enable-runtime-cpudetect \
$ADDITIONAL_CONFIGURE_FLAG

#echo "continue?"
#read
make -j8 install

}

NDK=/path-to-NDK

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
PREFIX=./android/$CPU
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
PREFIX=./android/$CPU
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
PREFIX=./android/$CPU
ADDITIONAL_CONFIGURE_FLAG=--enable-neon
build_one
#
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
PREFIX=./android/$CPU
ADDITIONAL_CONFIGURE_FLAG="--disable-mmx --disable-yasm"
build_one


