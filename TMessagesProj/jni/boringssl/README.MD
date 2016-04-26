cmake, ninja, go are required

cmake -DANDROID_NATIVE_API_LEVEL=android-8 \
      -DANDROID_ABI=x86 \
      -DCMAKE_BUILD_TYPE=Release \
      -DANDROID_NDK=/Applications/sdk/ndk-bundle/ \
      -DCMAKE_TOOLCHAIN_FILE=../util/android-cmake/android.toolchain.cmake \
      -DANDROID_NATIVE_API_LEVEL=16 \
      -GNinja ..
cmake --build .