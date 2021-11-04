#!/bin/bash

set -e


if [[ -z "$(ANDROID_SDK)" ]]; then
   cmakePath=""
else
   cmakePath=`find $ANDROID_SDK/cmake -executable -type f | grep cmake$ | head -n 1`/
fi

function build_one {
	mkdir ${CPU}
	cd ${CPU}

	echo "Configuring..."
	${cmakePath}cmake \
	-DANDROID_NATIVE_API_LEVEL=${API} \
	-DANDROID_ABI=${CPU} \
	-DCMAKE_BUILD_TYPE=Release \
	-DANDROID_NDK=${NDK} \
	-DCMAKE_TOOLCHAIN_FILE=${NDK}/build/cmake/android.toolchain.cmake \
	-GNinja -DCMAKE_MAKE_PROGRAM=${NINJA_PATH} \
	../..

	echo "Building..."
	${cmakePath}cmake --build .

	cd ..
}

function checkPreRequisites {

	if ! [ -d "boringssl" ] || ! [ "$(ls -A boringssl)" ]; then
		echo -e "\033[31mFailed! Submodule 'boringssl' not found!\033[0m"
		echo -e "\033[31mTry to run: 'git submodule init && git submodule update'\033[0m"
		exit
	fi

	if [ -z "$NDK" -a "$NDK" == "" ]; then
		echo -e "\033[31mFailed! NDK is empty. Run 'export NDK=[PATH_TO_NDK]'\033[0m"
		exit
	fi

	if [ -z "$NINJA_PATH" -a "$NINJA_PATH" == "" ]; then
		echo -e "\033[31mFailed! NINJA_PATH is empty. Run 'export NINJA_PATH=[PATH_TO_NINJA]'\033[0m"
		exit
	fi
}

ANDROID_NDK=$NDK
checkPreRequisites

cd boringssl

rm -rf build
mkdir build
cd build

function build {
	for arg in "$@"; do
		case "${arg}" in
			x86_64)
				API=21
				CPU=x86_64
				build_one
			;;
			arm64)
				API=21
				CPU=arm64-v8a
				build_one
			;;
			arm)
				API=16
				CPU=armeabi-v7a
				build_one
			;;
			x86)
				API=16
				CPU=x86
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
