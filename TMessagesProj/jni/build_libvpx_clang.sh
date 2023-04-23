#!/bin/bash
set -e
function build_one {
	echo "Building ${ARCH}..."

	PREBUILT=${NDK}/toolchains/${PREBUILT_ARCH}-${VERSION}/prebuilt/${BUILD_PLATFORM}
	PLATFORM=${NDK}/platforms/android-${ANDROID_API}/arch-${ARCH}

	TOOLS_PREFIX="${LLVM_BIN}/${ARCH_NAME}-linux-${BIN_MIDDLE}-"

	export LD=${TOOLS_PREFIX}ld
	export AR=${TOOLS_PREFIX}ar
	export STRIP=${TOOLS_PREFIX}strip
	export RANLIB=${TOOLS_PREFIX}ranlib
	export NM=${TOOLS_PREFIX}nm

	export CC_PREFIX="${LLVM_BIN}/${CLANG_PREFIX}-linux-${BIN_MIDDLE}${ANDROID_API}-"

	export CC=${CC_PREFIX}clang
	export CXX=${CC_PREFIX}clang++
	export AS=${CC_PREFIX}clang++
	export CROSS_PREFIX=${PREBUILT}/bin/${ARCH_NAME}-linux-${BIN_MIDDLE}-
	
	
	export CFLAGS="-DANDROID -fpic -fpie ${OPTIMIZE_CFLAGS}"
	export CPPFLAGS="${CFLAGS}"
	export CXXFLAGS="${CFLAGS} -std=c++11"
	export ASFLAGS="-D__ANDROID__"
	export LDFLAGS="-L${PLATFORM}/usr/lib"
	
	if [ "x86" = ${ARCH} ]; then
		sed -i '20s/^/#define rand() ((int)lrand48())\n/' vpx_dsp/add_noise.c
	fi

	echo "Cleaning..."
	make clean || true

	echo "Configuring..."



	./configure \
	--extra-cflags="-isystem ${LLVM_PREFIX}/sysroot/usr/include/${ARCH_NAME}-linux-${BIN_MIDDLE} -isystem ${LLVM_PREFIX}/sysroot/usr/include" \
	--libc="${LLVM_PREFIX}/sysroot" \
	--prefix=${PREFIX} \
	--target=${TARGET} \
	${CPU_DETECT} \
	--as=yasm \
	--enable-static \
	--enable-pic \
	--disable-docs \
	--enable-libyuv \
	--enable-small \
	--enable-optimizations \
	--enable-better-hw-compatibility \
	--disable-examples \
	--disable-tools \
	--disable-debug \
	--disable-unit-tests \
	--disable-install-docs \
	--enable-realtime-only \
	--enable-vp8 \
	--enable-vp9 \
	--disable-webm-io

	make -j$COMPILATION_PROC_COUNT install
	
	if [ "x86" = ${ARCH} ]; then
		sed -i '20d' vpx_dsp/add_noise.c
	fi
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

	if ! [ -d "libvpx" ] || ! [ "$(ls -A libvpx)" ]; then
		echo -e "\033[31mFailed! Submodule 'libvpx' not found!\033[0m"
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

cd libvpx

## common
LLVM_PREFIX="${NDK}/toolchains/llvm/prebuilt/linux-x86_64"
LLVM_BIN="${LLVM_PREFIX}/bin"
VERSION="4.9"
ANDROID_API=21

function build {
	for arg in "$@"; do
		case "${arg}" in
			x86_64)
				ARCH=x86_64
				ARCH_NAME=x86_64
				PREBUILT_ARCH=x86_64
				CLANG_PREFIX=x86_64
				BIN_MIDDLE=android
				CPU=x86_64
				OPTIMIZE_CFLAGS="-O3 -march=x86-64 -mtune=intel -msse4.2 -mpopcnt -m64 -fPIC"
				TARGET="x86_64-android-gcc"
				PREFIX=./build/$CPU
                CPU_DETECT="--enable-runtime-cpu-detect"
				build_one
			;;
			x86)
				ARCH=x86
				ARCH_NAME=i686
				PREBUILT_ARCH=x86
				CLANG_PREFIX=i686
				BIN_MIDDLE=android
				CPU=i686
				OPTIMIZE_CFLAGS="-O3 -march=i686 -mtune=intel -msse3 -mfpmath=sse -m32 -fPIC"
				TARGET="x86-android-gcc"
				PREFIX=./build/$ARCH
				CPU_DETECT="--enable-runtime-cpu-detect"
				build_one
			;;
			arm64)
				ARCH=arm64
				ARCH_NAME=aarch64
				PREBUILT_ARCH=aarch64
				CLANG_PREFIX=aarch64
				BIN_MIDDLE=android
				CPU=arm64-v8a
				OPTIMIZE_CFLAGS="-O3 -march=armv8-a"
				TARGET="arm64-android-gcc"
				PREFIX=./build/$CPU
				CPU_DETECT="--disable-runtime-cpu-detect"
				build_one
			;;
			arm)
				ARCH=arm
				ARCH_NAME=arm
				PREBUILT_ARCH=arm
				CLANG_PREFIX=armv7a
				BIN_MIDDLE=androideabi
				CPU=armeabi-v7a
				OPTIMIZE_CFLAGS="-Os -march=armv7-a -mfloat-abi=softfp -mfpu=neon -mtune=cortex-a8 -mthumb -D__thumb__"
				TARGET="armv7-android-gcc --enable-neon --disable-neon-asm"
				PREFIX=./build/$CPU
				CPU_DETECT="--disable-runtime-cpu-detect"
				build_one
			;;
			*)
			;;
		esac
	done
}

if (( $# == 0 )); then
	build x86_64 x86 arm arm64
else
	build $@
fi
