#!/bin/bash

#recomended use ndk r12b
NDK=/path-to-NDK

ROOT_DIR=$PWD

function build_one {

echo "Cleaning..."
rm config.h
make clean

echo "========== Building x264 for $ARCH =========="

cd $ROOT_DIR/x264

./configure \
--cc=$CC \
--prefix=$PREFIX \
--enable-static \
--enable-shared \
--enable-pic \
--enable-strip \
--disable-asm \
--disable-cli \
--disable-opencl \
--disable-avs \
--disable-swscale \
--disable-lavf \
--disable-ffms \
--disable-gpac \
--disable-interlaced \
--chroma-format=420 \
--host=$X264_ARCH-linux \
--extra-cflags="-D_ANDROID_SYS_ -fno-tree-vectorize -funsafe-math-optimizations $BUILD_EXTRA_CFLAGS" \
--extra-ldflags="-Wl,-rpath-link=${PLATFORM}/usr/lib -L${PLATFORM}/usr/lib" \
--cross-prefix=$CROSS_PREFIX \
--sysroot=$PLATFORM

make install && cd -

echo "========== Building ffmpeg for $ARCH =========="

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
--extra-cflags="-I$PREFIX/include -Wl,-Bsymbolic -Os -DCONFIG_LINUX_PERF=0 -DANDROID $OPTIMIZE_CFLAGS -fPIE -pie --static -fPIC" \
--extra-ldflags="-L$PREFIX/lib -lx264 -Wl,-Bsymbolic,-rpath-link=$PLATFORM/usr/lib -L$PLATFORM/usr/lib -nostdlib -lc -lm -lz -ldl -fPIC" \
--extra-libs="-lgcc" \
\
--enable-version3 \
--enable-gpl \
--enable-libx264 \
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
--disable-swresample \
\
--enable-runtime-cpudetect \
--enable-pthreads \
--enable-avresample \
--enable-protocol=file \
\
--enable-decoder=gif \
--enable-decoder=h264 \
--enable-decoder=mpeg4 \
--enable-decoder=gif \
\
--enable-encoder=libx264 \
--enable-encoder=h264 \
\
--enable-muxer=mp4 \
--enable-demuxer=mov \
--enable-demuxer=gif \
--enable-demuxer=matroska \
--enable-hwaccels \
$ADDITIONAL_CONFIGURE_FLAG

#echo "continue?"
#read
make -j8 install

}

download_and_unpack_package()
{
    TARBALL=$1.tar.gz
    URL=$3
    if [[ ! -f ${TARBALL} ]]; then
        echo "Download package $URL/$TARBALL ..."
        wget ${URL}/${TARBALL}

        if [[ ! $? -eq 0 ]]; then
            TARBALL=$1.tar.bz2
            if [[ ! -f $TARBALL ]]; then
                wget ${URL}/${TARBALL} || exit 1
            fi
        fi

        if [[ -d $1 ]]; then
            rm -rf $1
        fi
    fi

    if [[ -f ${TARBALL} ]] && [[ ! -d $2 ]]; then
        echo "Unpack package $TARBALL ..."
        tar -xzvf $TARBALL
        if [[ -f $2 ]]; then
            rm $2
        fi
        ln -s $1 $2
    fi
}

rm_package()
{
    echo "Removing package $1..."

    TARBALL=$1.tar.gz
    if [[ -f ${TARBALL} ]]; then
        rm -rf ${TARBALL}
    fi

    TARBALL=$1.tar.bz2
    if [ -f $TARBALL ]; then
        rm -rf $TARBALL
    fi

    if [ -L $1 ]; then
        echo "Removing package symlink $1..."
        rm -rf $1
    fi

    if [ -L $2 ]; then
        echo "Removing package version symlink $2..."
        rm -rf $2
    fi

    if [ -d $1 ]; then
        echo "Removing package source $1..."
        rm -rf $1
    fi
}

X264_LIB_NAME=x264
X264_LIB_VERSION="snapshot-20170101-2245-stable"
X264_LIB_URL=https://download.videolan.org/pub/videolan/x264/snapshots/

download_and_unpack_package $X264_LIB_NAME-$X264_LIB_VERSION $X264_LIB_NAME $X264_LIB_URL

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
X264_ARCH=x86_64
PREFIX=$ROOT_DIR/android/$CPU
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
X264_ARCH=arm
CPU=arm64-v8a
OPTIMIZE_CFLAGS=
PREFIX=$ROOT_DIR/android/$CPU
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
X264_ARCH=armv7-a
CPU=armv7-a
OPTIMIZE_CFLAGS="-marm -march=$CPU"
PREFIX=$ROOT_DIR/android/$CPU
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
X264_ARCH=i686
OPTIMIZE_CFLAGS="-march=$CPU"
PREFIX=$ROOT_DIR/android/$CPU
ADDITIONAL_CONFIGURE_FLAG="--disable-mmx --disable-yasm"
build_one

rm_package $X264_LIB_NAME-$X264_LIB_VERSION $X264_LIB_NAME

