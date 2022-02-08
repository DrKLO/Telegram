#!/bin/bash
set -e
function build_one {
	echo "Building ${ARCH}..."

	PREBUILT=${NDK}/toolchains/${PREBUILT_ARCH}${PREBUILT_MIDDLE}-${VERSION}/prebuilt/${BUILD_PLATFORM}
	PLATFORM=${NDK}/platforms/android-${ANDROID_API}/arch-${ARCH}

	TOOLS_PREFIX="${LLVM_BIN}/${ARCH_NAME}-linux-${BIN_MIDDLE}-"

	LD=${TOOLS_PREFIX}ld
	AR=${TOOLS_PREFIX}ar
	STRIP=${TOOLS_PREFIX}strip
	NM=${TOOLS_PREFIX}nm

	CC_PREFIX="${LLVM_BIN}/${CLANG_PREFIX}-linux-${BIN_MIDDLE}${ANDROID_API}-"

	CC=${CC_PREFIX}clang
	CXX=${CC_PREFIX}clang++
	CROSS_PREFIX=${PREBUILT}/bin/${ARCH_NAME}-linux-${BIN_MIDDLE}-
	
	INCLUDES=" -I${LIBVPXPREFIX}/include"
	LIBS=" -L${LIBVPXPREFIX}/lib"

	echo "Cleaning..."
	rm -f config.h
	make clean || true

	echo "Configuring..."

	./configure \
	--nm=${NM} \
	--ar=${AR} \
	--strip=${STRIP} \
	--cc=${CC} \
	--cxx=${CXX} \
	--enable-stripping \
	--arch=$ARCH \
	--target-os=linux \
	--enable-cross-compile \
	--x86asmexe=$NDK/prebuilt/${BUILD_PLATFORM}/bin/yasm \
	--prefix=$PREFIX \
	--enable-pic \
	--disable-shared \
	--enable-static \
	--enable-asm \
	--enable-inline-asm \
	--enable-x86asm \
	--cross-prefix=$CROSS_PREFIX \
	--sysroot="${LLVM_PREFIX}/sysroot" \
	--extra-cflags="${INCLUDES} -Wl,-Bsymbolic -Os -DCONFIG_LINUX_PERF=0 -DANDROID $OPTIMIZE_CFLAGS -fPIE -pie --static -fPIC" \
	--extra-cxxflags="${INCLUDES} -Wl,-Bsymbolic -Os -DCONFIG_LINUX_PERF=0 -DANDROID $OPTIMIZE_CFLAGS -fPIE -pie --static -fPIC" \
	--extra-ldflags="${LIBS} -Wl,-Bsymbolic -Wl,-rpath-link=$PLATFORM/usr/lib -L$PLATFORM/usr/lib -nostdlib -lc -lm -ldl -fPIC" \
	\
	--enable-version3 \
	--enable-gpl \
	\
	--disable-linux-perf \
	\
	--disable-doc \
	--disable-htmlpages \
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
	--disable-ffplay \
	--disable-ffprobe \
	--disable-postproc \
	--disable-avdevice \
	\
	--enable-libvpx \
	--enable-decoder=libvpx_vp9 \
	--enable-runtime-cpudetect \
	--enable-pthreads \
	--enable-avresample \
	--enable-swscale \
	--enable-protocol=file \
	--enable-decoder=opus \
	--enable-decoder=h264 \
	--enable-decoder=mpeg4 \
	--enable-decoder=mjpeg \
	--enable-decoder=gif \
	--enable-decoder=alac \
	--enable-demuxer=mov \
	--enable-demuxer=gif \
	--enable-demuxer=ogg \
	--enable-demuxer=matroska \
	--enable-hwaccels \
	$ADDITIONAL_CONFIGURE_FLAG

	#echo "continue?"
	#read
	make -j$COMPILATION_PROC_COUNT
	make install
}

function setCurrentPlatform {

	CURRENT_PLATFORM="$(uname -s)"
	case "${CURRENT_PLATFORM}" in
		Darwin*)
			BUILD_PLATFORM=darwin-x86_64
			COMPILATION_PROC_COUNT=`sysctl -n hw.physicalcpu`
			;;
		Linux*)
			BUILD_PLATFORM=linux-x86_64
			COMPILATION_PROC_COUNT=$(nproc)
			;;
		*)
			echo -e "\033[33mWarning! Unknown platform ${CURRENT_PLATFORM}! falling back to linux-x86_64\033[0m"
			BUILD_PLATFORM=linux-x86_64
			COMPILATION_PROC_COUNT=1
			;;
	esac

	echo "Build platform: ${BUILD_PLATFORM}"
	echo "Parallel jobs: ${COMPILATION_PROC_COUNT}"

}

function checkPreRequisites {

	if ! [ -d "ffmpeg" ] || ! [ "$(ls -A ffmpeg)" ]; then
		echo -e "\033[31mFailed! Submodule 'ffmpeg' not found!\033[0m"
		echo -e "\033[31mTry to run: 'git submodule init && git submodule update'\033[0m"
		exit
	fi

	if [ -z "$NDK" -a "$NDK" == "" ]; then
		echo -e "\033[31mFailed! NDK is empty. Run 'export NDK=[PATH_TO_NDK]'\033[0m"
		exit
	fi
}

setCurrentPlatform
checkPreRequisites

# TODO: fix env variable for NDK
# NDK=/opt/android-sdk/ndk-bundle

cd ffmpeg

## common
LLVM_PREFIX="${NDK}/toolchains/llvm/prebuilt/linux-x86_64"
LLVM_BIN="${LLVM_PREFIX}/bin"
VERSION="4.9"

function build {
	for arg in "$@"; do
		case "${arg}" in
			x86_64)
				ANDROID_API=21

				ARCH=x86_64
				ARCH_NAME=x86_64
				PREBUILT_ARCH=x86_64
				PREBUILT_MIDDLE=
				CLANG_PREFIX=x86_64
				BIN_MIDDLE=android
				CPU=x86_64
				PREFIX=./build/$CPU
				LIBVPXPREFIX=../libvpx/build/$ARCH_NAME
				ADDITIONAL_CONFIGURE_FLAG="--disable-asm"
				build_one
			;;
			arm64)
				ANDROID_API=21

				ARCH=arm64
				ARCH_NAME=aarch64
				PREBUILT_ARCH=aarch64
				PREBUILT_MIDDLE="-linux-android"
				CLANG_PREFIX=aarch64
				BIN_MIDDLE=android
				CPU=arm64-v8a
				OPTIMIZE_CFLAGS=
				PREFIX=./build/$CPU
				LIBVPXPREFIX=../libvpx/build/$CPU
				ADDITIONAL_CONFIGURE_FLAG="--enable-neon --enable-optimizations"
				build_one
			;;
			arm)
				ANDROID_API=16

				ARCH=arm
				ARCH_NAME=arm
				PREBUILT_ARCH=arm
				PREBUILT_MIDDLE="-linux-androideabi"
				CLANG_PREFIX=armv7a
				BIN_MIDDLE=androideabi
				CPU=armv7-a
				OPTIMIZE_CFLAGS="-marm -march=$CPU"
				PREFIX=./build/armeabi-v7a
				LIBVPXPREFIX=../libvpx/build/armeabi-v7a
				ADDITIONAL_CONFIGURE_FLAG=--enable-neon
				build_one
			;;
			x86)
				ANDROID_API=16

				ARCH=x86
				ARCH_NAME=i686
				PREBUILT_ARCH=x86
				PREBUILT_MIDDLE=
				CLANG_PREFIX=i686
				BIN_MIDDLE=android
				CPU=i686
				OPTIMIZE_CFLAGS="-march=$CPU"
				PREFIX=./build/$ARCH
				LIBVPXPREFIX=../libvpx/build/$ARCH
				ADDITIONAL_CONFIGURE_FLAG="--disable-x86asm --disable-inline-asm --disable-asm"
				build_one
			;;
			*)
			;;
		esac
	done
}

if (( $# == 0 )); then
	build x86_64 arm64 arm x86
else
	build $@
fi
