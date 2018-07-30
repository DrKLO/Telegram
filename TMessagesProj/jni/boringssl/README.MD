cmake, ninja, go are required

export ANDROID_NDK=/Applications/sdk/ndk-bundle

/Applications/sdk/cmake/3.6.4111459/bin/cmake -DANDROID_ABI=armeabi-v7a \
-DCMAKE_TOOLCHAIN_FILE=${ANDROID_NDK}/build/cmake/android.toolchain.cmake \
-DANDROID_NATIVE_API_LEVEL=16 \
-DCMAKE_BUILD_TYPE=Release \
-DARCH=arm \
-DOPENSSL_NO_ASM=1 \
-GNinja ..

/Applications/sdk/cmake/3.6.4111459/bin/cmake -DANDROID_ABI=x86 \
-DCMAKE_TOOLCHAIN_FILE=${ANDROID_NDK}/build/cmake/android.toolchain.cmake \
-DANDROID_NATIVE_API_LEVEL=16 \
-DCMAKE_BUILD_TYPE=Release \
-DARCH=x86 \
-GNinja ..

/Applications/sdk/cmake/3.6.4111459/bin/cmake -DANDROID_ABI=x86_64 \
-DCMAKE_TOOLCHAIN_FILE=${ANDROID_NDK}/build/cmake/android.toolchain.cmake \
-DANDROID_NATIVE_API_LEVEL=21 \
-DCMAKE_BUILD_TYPE=Release \
-DARCH=x86_64 \
-GNinja ..

/Applications/sdk/cmake/3.6.4111459/bin/cmake -DANDROID_ABI=arm64-v8a \
-DCMAKE_TOOLCHAIN_FILE=${ANDROID_NDK}/build/cmake/android.toolchain.cmake \
-DANDROID_NATIVE_API_LEVEL=21 \
-DCMAKE_BUILD_TYPE=Release \
-DARCH=arm64-v8a \
-GNinja ..

/Applications/sdk/cmake/3.6.4111459/bin/cmake --build .
