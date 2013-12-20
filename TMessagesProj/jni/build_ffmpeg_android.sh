#!/bin/bash

function build_one {

echo "Cleaning..."
make clean

echo "Configuring..."

./configure --target-os=linux \
--prefix=$PREFIX \
--enable-cross-compile \
--extra-libs="-lgcc" \
--arch=$ARCH \
--cc=$CC \
--cross-prefix=$CROSS_PREFIX \
--nm=$NM \
--sysroot=$PLATFORM \
--extra-cflags=" -O3 -fpic -DANDROID -DHAVE_SYS_UIO_H=1 -fasm -Wno-psabi -fno-short-enums -Dipv6mr_interface=ipv6mr_ifindex -fno-strict-aliasing -finline-limit=300 $OPTIMIZE_CFLAGS " \
--disable-shared \
--enable-static \
--extra-ldflags="-Wl,-rpath-link=$PLATFORM/usr/lib -L$PLATFORM/usr/lib -nostdlib -lc -lm -ldl" \
\
--disable-everything \
--disable-network \
--enable-small \
--enable-zlib \
--disable-avfilter \
--disable-avdevice \
--disable-programs \
--disable-doc \
--disable-lsp \
--disable-dwt \
--disable-dct \
--enable-stripping \
--disable-postproc \
--disable-fft \
--disable-lzo \
--disable-rdft \
--disable-mdct \
--disable-debug \
\
--enable-muxer='mp4' \
--enable-protocol='file' \
--enable-encoder='aac,mpeg4' \
--enable-decoder='aac,amrnb,amrwb,flv,h263,h264' \
--enable-demuxer='flv,mpegvideo,mov' \
--enable-hwaccel='mpeg4_vaapi,mpeg4_vdpau' \
--enable-swresample \
--enable-swscale \
--enable-asm \
$ADDITIONAL_CONFIGURE_FLAG

echo "continue?"
read
make -j8 install

#$AR d libavcodec/libavcodec.a inverse.o

#$LD -rpath-link=$PLATFORM/usr/lib -L$PLATFORM/usr/lib  -soname libffmpeg.a -static -nostdlib -z noexecstack -Bsymbolic --whole-archive --no-undefined -o $PREFIX/libffmpeg.so libavcodec/libavcodec.a libavformat/libavformat.a libavutil/libavutil.a -lc -lm -lz -ldl --dynamic-linker=/system/bin/linker $GCCLIB

}

NDK=/Users/DrKLO/ndk9

#arm platform
PLATFORM=$NDK/platforms/android-8/arch-arm
PREBUILT=$NDK/toolchains/arm-linux-androideabi-4.8/prebuilt/darwin-x86_64
LD=$PREBUILT/bin/arm-linux-androideabi-ld
AR=$PREBUILT/bin/arm-linux-androideabi-ar
NM=$PREBUILT/bin/arm-linux-androideabi-nm
GCCLIB=$PREBUILT/lib/gcc/arm-linux-androideabi/4.8/libgcc.a
ARCH=arm
CC=$PREBUILT/bin/arm-linux-androideabi-gcc
CROSS_PREFIX=$PREBUILT/bin/arm-linux-androideabi-

#arm v6
CPU=armv6
OPTIMIZE_CFLAGS="-marm -march=$CPU"
PREFIX=/Users/DrKLO/ndk9/platforms/android-9/arch-$ARCH/usr
ADDITIONAL_CONFIGURE_FLAG=
build_one

#arm v7vfpv3
#CPU=armv7-a
#OPTIMIZE_CFLAGS="-mfloat-abi=softfp -mfpu=vfpv3-d16 -marm -march=$CPU "
#PREFIX=./android/$CPU
#ADDITIONAL_CONFIGURE_FLAG=
#build_one

#arm v7vfp
#CPU=armv7-a
#OPTIMIZE_CFLAGS="-mfloat-abi=softfp -mfpu=vfp -marm -march=$CPU "
#PREFIX=./android/$CPU-vfp
#ADDITIONAL_CONFIGURE_FLAG=
#build_one

#arm v7n
#CPU=armv7-a
#OPTIMIZE_CFLAGS="-mfloat-abi=softfp -mfpu=neon -marm -march=$CPU -mtune=cortex-a8"
#PREFIX=./android/$CPU
#ADDITIONAL_CONFIGURE_FLAG=--enable-neon
#build_one

#arm v6+vfp
#CPU=armv6
#OPTIMIZE_CFLAGS="-DCMP_HAVE_VFP -mfloat-abi=softfp -mfpu=vfp -marm -march=$CPU"
#PREFIX=./android/${CPU}_vfp
#ADDITIONAL_CONFIGURE_FLAG=
#build_one

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
PREFIX=/Users/DrKLO/ndk9/platforms/android-9/arch-$ARCH/usr
ADDITIONAL_CONFIGURE_FLAG="--disable-mmx --disable-yasm"
build_one

