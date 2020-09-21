cmake_minimum_required(VERSION 3.4.1)
enable_language(C ASM)
add_definitions(-DNDEBUG -Wall -DANDROID -fno-rtti -DHAVE_PTHREAD -finline-functions -ffast-math -Os -fno-exceptions -fno-unwind-tables -fno-asynchronous-unwind-tables -Wnon-virtual-dtor -Woverloaded-virtual -Wno-unused-parameter -fvisibility=hidden)
if (${ANDROID_ABI} STREQUAL "arm64-v8a")
    add_definitions(-DUSE_ARM_NEON -D__ARM64_NEON__ -fno-integrated-as)
elseif (${ANDROID_ABI} STREQUAL "armeabi-v7a")
    add_definitions(-DUSE_ARM_NEON -fno-integrated-as)
endif ()

file(GLOB_RECURSE src_rlottie
        src/lottie/lottieanimation.cpp
        src/lottie/lottieitem.cpp
        src/lottie/lottiekeypath.cpp
        src/lottie/lottieloader.cpp
        src/lottie/lottiemodel.cpp
        src/lottie/lottieparser.cpp
        src/lottie/lottieproxymodel.cpp
        src/vector/freetype/v_ft_math.cpp
        src/vector/freetype/v_ft_raster.cpp
        src/vector/freetype/v_ft_stroker.cpp
        src/vector/pixman/vregion.cpp
        src/vector/stb/stb_image.cpp
        src/vector/vbezier.cpp
        src/vector/vbitmap.cpp
        src/vector/vbrush.cpp
        src/vector/vcompositionfunctions.cpp
        src/vector/vdasher.cpp
        src/vector/vdebug.cpp
        src/vector/vdrawable.cpp
        src/vector/vdrawhelper.cpp
        src/vector/vdrawhelper_neon.cpp
        src/vector/velapsedtimer.cpp
        src/vector/vimageloader.cpp
        src/vector/vinterpolator.cpp
        src/vector/vmatrix.cpp
        src/vector/vpainter.cpp
        src/vector/vpath.cpp
        src/vector/vpathmesure.cpp
        src/vector/vraster.cpp
        src/vector/vrect.cpp
        src/vector/vrle.cpp
        )

if (${ANDROID_ABI} STREQUAL "armeabi-v7a")

    file(GLOB_RECURSE src_rlottie_neon
            src/vector/pixman/pixman-arm-neon-asm.S
            )
elseif (${ANDROID_ABI} STREQUAL "arm64-v8a")
    file(GLOB_RECURSE src_rlottie_neon
            src/vector/pixman/pixman-arma64-neon-asm.S
            )
endif ()

add_library(rlottie STATIC
        ${src_rlottie}
        ${src_rlottie_neon}
        )

target_include_directories(rlottie
        PRIVATE
        inc
        src/vector/
        src/vector/pixman
        src/vector/freetype
        src/vector/stb
        )

target_link_libraries(rlottie cpufeatures)