cmake_minimum_required(VERSION 3.16)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_CXX_EXTENSIONS OFF)

set(CMAKE_CXX_FLAGS "-DANDROID -g")
set(CMAKE_C_FLAGS "-w -std=c11 -DANDROID -D_LARGEFILE_SOURCE=1 -g -Wno-error=implicit-function-declaration")
set(CMAKE_ASM_FLAGS "${CFLAGS} -x assembler-with-cpp")
set(CMAKE_C_FLAGS "${CMAKE_C_FLAGS} -ffunction-sections -fdata-sections -fvisibility=hidden -flto=full")
set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} -ffunction-sections -fdata-sections -fvisibility=hidden -fvisibility-inlines-hidden -flto=full")
set(CMAKE_SHARED_LINKER_FLAGS "${CMAKE_SHARED_LINKER_FLAGS} -flto=full -Wl,--gc-sections -Wl,--icf=safe -Wl,-Bsymbolic")

if (${ANDROID_ABI} STREQUAL "armeabi-v7a" OR ${ANDROID_ABI} STREQUAL "arm64-v8a")
    enable_language(ASM)
else()
    enable_language(ASM_NASM)
endif()

set(CAN_COMPILE_SME FALSE CACHE INTERNAL "Disable libyuv SME" FORCE)
add_subdirectory(
        third_party/libyuv
        ${CMAKE_CURRENT_BINARY_DIR}/libyuv
        EXCLUDE_FROM_ALL)

# Abseil
set(ABSL_PROPAGATE_CXX_STD ON)
set(ABSL_BUILD_TESTING OFF)
set(ABSL_ENABLE_INSTALL OFF)
add_subdirectory(
        third_party/absl
        ${CMAKE_CURRENT_BINARY_DIR}/absl
        EXCLUDE_FROM_ALL)

add_library(webrtc_absl INTERFACE)
target_link_libraries(webrtc_absl INTERFACE
        absl::base
        absl::strings
        absl::str_format
        absl::cord
        absl::synchronization
        absl::time
        absl::status
        absl::statusor
        absl::hash
        absl::flat_hash_map
        absl::flat_hash_set
        absl::flags
        absl::flags_parse
        absl::random_random
        absl::random_distributions
        absl::crc32c
        absl::log
        absl::check
)

add_library(avutil STATIC IMPORTED)
set_target_properties(avutil PROPERTIES IMPORTED_LOCATION ${CMAKE_HOME_DIRECTORY}/prebuild/lib/${ANDROID_ABI}/libavutil.a)

add_library(avformat STATIC IMPORTED)
set_target_properties(avformat PROPERTIES IMPORTED_LOCATION ${CMAKE_HOME_DIRECTORY}/prebuild/lib/${ANDROID_ABI}/libavformat.a)

add_library(avcodec STATIC IMPORTED)
set_target_properties(avcodec PROPERTIES IMPORTED_LOCATION ${CMAKE_HOME_DIRECTORY}/prebuild/lib/${ANDROID_ABI}/libavcodec.a)

add_library(swresample STATIC IMPORTED)
set_target_properties(swresample PROPERTIES IMPORTED_LOCATION ${CMAKE_HOME_DIRECTORY}/prebuild/lib/${ANDROID_ABI}/libswresample.a)

add_library(swscale STATIC IMPORTED)
set_target_properties(swscale PROPERTIES IMPORTED_LOCATION ${CMAKE_HOME_DIRECTORY}/prebuild/lib/${ANDROID_ABI}/libswscale.a)

add_library(crypto STATIC IMPORTED)
set_target_properties(crypto PROPERTIES IMPORTED_LOCATION ${CMAKE_HOME_DIRECTORY}/prebuild/lib/${ANDROID_ABI}/libcrypto.a)

add_library(ssl STATIC IMPORTED)
set_target_properties(ssl PROPERTIES IMPORTED_LOCATION ${CMAKE_HOME_DIRECTORY}/prebuild/lib/${ANDROID_ABI}/libssl.a)


#aesige
#add_library(aesige STATIC openssl/aes_ige.c)
#target_compile_options(aesige PUBLIC -Wall -frtti -finline-functions -ffast-math -Oz)
#target_include_directories(aesige PUBLIC third_party/boringssl/include openssl/include)
#target_link_libraries(aesige crypto)


add_library(libvpx STATIC IMPORTED)
set_target_properties(libvpx PROPERTIES IMPORTED_LOCATION ${CMAKE_HOME_DIRECTORY}/prebuild/lib/${ANDROID_ABI}/libvpx.a)

add_library(libdav1d STATIC IMPORTED)
set_target_properties(libdav1d PROPERTIES IMPORTED_LOCATION ${CMAKE_HOME_DIRECTORY}/prebuild/lib/${ANDROID_ABI}/libdav1d.a)

add_library(libopus STATIC IMPORTED)
set_target_properties(libopus PROPERTIES IMPORTED_LOCATION ${CMAKE_HOME_DIRECTORY}/prebuild/lib/${ANDROID_ABI}/libopus.a)

add_library(tde2e STATIC IMPORTED)
set_target_properties(tde2e PROPERTIES IMPORTED_LOCATION ${CMAKE_HOME_DIRECTORY}/prebuild/lib/${ANDROID_ABI}/libtde2e.a)

add_library(tdutils STATIC IMPORTED)
set_target_properties(tdutils PROPERTIES IMPORTED_LOCATION ${CMAKE_HOME_DIRECTORY}/prebuild/lib/${ANDROID_ABI}/libtdutils.a)

add_library(prisma STATIC IMPORTED)
set_target_properties(prisma PROPERTIES IMPORTED_LOCATION ${CMAKE_HOME_DIRECTORY}/prebuild/lib/${ANDROID_ABI}/libprisma.a)

#tgnet
add_library(tgnet STATIC
        openssl/aes_ige.c
        tgnet/ApiScheme.cpp
        tgnet/BuffersStorage.cpp
        tgnet/ByteArray.cpp
        tgnet/ByteStream.cpp
        tgnet/Connection.cpp
        tgnet/ConnectionSession.cpp
        tgnet/ConnectionsManager.cpp
        tgnet/TLSHello.cpp
        tgnet/ConnectionSocket.cpp
        tgnet/Datacenter.cpp
        tgnet/EventObject.cpp
        tgnet/FileLog.cpp
        tgnet/MTProtoScheme.cpp
        tgnet/NativeByteBuffer.cpp
        tgnet/Request.cpp
        tgnet/Timer.cpp
        tgnet/TLObject.cpp
        tgnet/ProxyCheckInfo.cpp
        tgnet/Handshake.cpp
        tgnet/Config.cpp)
target_compile_options(tgnet PUBLIC
        -Wall -frtti -finline-functions -ffast-math -Oz)
set_target_properties(tgnet PROPERTIES
        ANDROID_ARM_MODE arm)
target_compile_definitions(tgnet PUBLIC
        HAVE_PTHREAD)
target_include_directories(tgnet PUBLIC
        third_party/boringssl/include
        openssl/include)
target_link_libraries(tgnet
        crypto)

# tlottie (prebuilt Rust static library)
add_library(tlottie STATIC IMPORTED)
set_target_properties(tlottie PROPERTIES
        IMPORTED_LOCATION ${CMAKE_HOME_DIRECTORY}/prebuild/lib/${ANDROID_ABI}/libtlottie.a)

# Wallet Engine: matching Rust archive and generated UniFFI C++20 wrapper.
set(WALLET_ENGINE_INCLUDE_DIR ${CMAKE_CURRENT_SOURCE_DIR}/prebuild/include/wallet-engine)
add_library(wallet_engine STATIC IMPORTED)
set_target_properties(wallet_engine PROPERTIES
        IMPORTED_LOCATION ${CMAKE_CURRENT_SOURCE_DIR}/prebuild/lib/${ANDROID_ABI}/libwallet_engine.a)

add_library(wallet_engine_cpp STATIC
        ${WALLET_ENGINE_INCLUDE_DIR}/wallet_engine.cpp
        ${WALLET_ENGINE_INCLUDE_DIR}/gram_wallet.cpp)
# Keep C++20 local: existing JNI sources include Breakpad's C++17-only headers.
# Any new source that includes wallet_engine.hpp must also opt into C++20.
target_compile_features(wallet_engine_cpp PRIVATE cxx_std_20)
# Keep unused bindings discardable even when voip resets CMAKE_CXX_FLAGS below.
target_compile_options(wallet_engine_cpp PRIVATE -ffunction-sections -fdata-sections)
target_include_directories(wallet_engine_cpp PUBLIC ${WALLET_ENGINE_INCLUDE_DIR})
target_link_libraries(wallet_engine_cpp PUBLIC wallet_engine dl m)

# Compile the JNI bridge as C++20 without changing the legacy JNI sources.
# An object library keeps its JNI entry points in the existing tmessages .so.
add_library(wallet_engine_jni OBJECT wallet/WalletEngineBridge.cpp wallet/GramWalletAdapter.cpp)
target_compile_features(wallet_engine_jni PRIVATE cxx_std_20)
set_target_properties(wallet_engine_jni PROPERTIES POSITION_INDEPENDENT_CODE ON)
target_link_libraries(wallet_engine_jni PRIVATE wallet_engine_cpp)

#sqlite
add_library(sqlite STATIC
        sqlite/sqlite3.c)
target_compile_options(sqlite PUBLIC
        -fno-strict-aliasing -Oz)
set_target_properties(sqlite PROPERTIES
        ANDROID_ARM_MODE arm)
target_compile_definitions(sqlite PUBLIC
        NULL=0 SOCKLEN_T=socklen_t LOCALE_NOT_USED ANDROID_NDK DISABLE_IMPORTGL AVOID_TABLES ANDROID_TILE_BASED_DECODE HAVE_STRCHRNUL=0 ANDROID_ARMV6_IDCT)

#breakpad
add_library(breakpad STATIC
        third_party/breakpad/src/client/linux/crash_generation/crash_generation_client.cc
        third_party/breakpad/src/client/linux/handler/exception_handler.cc
        third_party/breakpad/src/client/linux/handler/minidump_descriptor.cc
        third_party/breakpad/src/client/linux/log/log.cc
        third_party/breakpad/src/client/linux/dump_writer_common/thread_info.cc
        third_party/breakpad/src/client/linux/dump_writer_common/seccomp_unwinder.cc
        third_party/breakpad/src/client/linux/dump_writer_common/ucontext_reader.cc
        third_party/breakpad/src/client/linux/microdump_writer/microdump_writer.cc
        third_party/breakpad/src/client/linux/minidump_writer/linux_dumper.cc
        third_party/breakpad/src/client/linux/minidump_writer/linux_ptrace_dumper.cc
        third_party/breakpad/src/client/linux/minidump_writer/minidump_writer.cc
        third_party/breakpad/src/client/minidump_file_writer.cc
        third_party/breakpad/src/common/android/breakpad_getcontext.S
        third_party/breakpad/src/common/convert_UTF.c
        third_party/breakpad/src/common/md5.cc
        third_party/breakpad/src/common/string_conversion.cc
        third_party/breakpad/src/common/linux/elfutils.cc
        third_party/breakpad/src/common/linux/file_id.cc
        third_party/breakpad/src/common/linux/guid_creator.cc
        third_party/breakpad/src/common/linux/linux_libc_support.cc
        third_party/breakpad/src/common/linux/memory_mapped_file.cc
        third_party/breakpad/src/common/linux/safe_readlink.cc)
set_target_properties(breakpad PROPERTIES ANDROID_ARM_MODE arm)
set_property(SOURCE third_party/breakpad/src/common/android/breakpad_getcontext.S PROPERTY LANGUAGE C)
target_include_directories(breakpad PUBLIC
        third_party/breakpad/src/common/android/include
        third_party/breakpad/src)


#voip
include(${CMAKE_HOME_DIRECTORY}/voip/CMakeLists.txt)

set(NATIVE_LIB "tmessages.49")

#tmessages
add_library(${NATIVE_LIB} SHARED
        jni.c
        audio.c
        webm_encoder.c
        image.cpp
        intro/IntroRenderer.c
        SqliteWrapper.cpp
        utilities.cpp
        gifvideo.cpp
        lottie.cpp
        TgNetWrapper.cpp
        ffmpeg_jni.cc
        fast-edge.cpp
        genann.c
        secureid_ocr.cpp
        tde2e/bridge.cpp
        tests.cpp
)

# Compile JNI directly into the shared library so archive extraction cannot drop its exports.
set(PRISMA_BRIDGE_SOURCES
        "${CMAKE_CURRENT_SOURCE_DIR}/prisma/bridge/PrismaEngine.cpp"
        "${CMAKE_CURRENT_SOURCE_DIR}/prisma/bridge/PrismaJni.cpp")
target_sources(${NATIVE_LIB} PRIVATE ${PRISMA_BRIDGE_SOURCES})
target_include_directories(${NATIVE_LIB} PRIVATE
        "${CMAKE_CURRENT_SOURCE_DIR}/prisma/bridge"
        "${CMAKE_CURRENT_SOURCE_DIR}/third_party/boost-regex/include"
        "${CMAKE_CURRENT_SOURCE_DIR}/third_party/libprisma/libprisma")
set_property(SOURCE ${PRISMA_BRIDGE_SOURCES} APPEND PROPERTY
        COMPILE_OPTIONS -fexceptions -frtti)
set_property(SOURCE ${PRISMA_BRIDGE_SOURCES} APPEND PROPERTY
        COMPILE_DEFINITIONS BOOST_REGEX_STANDALONE)

target_compile_options(${NATIVE_LIB} PUBLIC
        -ffast-math -Oz -funroll-loops -ffast-math -fno-strict-aliasing -fno-math-errno)

target_compile_definitions(${NATIVE_LIB} PUBLIC
        BSD=1 NULL=0 SOCKLEN_T=socklen_t ANDROID_NDK DISABLE_IMPORTGL AVOID_TABLES ANDROID_TILE_BASED_DECODE __STDC_CONSTANT_MACROS ANDROID_ARMV6_IDCT OPUS_BUILD FIXED_POINT USE_ALLOCA restrict= __EMX__ LOCALE_NOT_USED HAVE_LRINT HAVE_LRINTF)

target_sources(${NATIVE_LIB} PRIVATE

        third_party/xiph/opusfile/src/info.c
        third_party/xiph/opusfile/src/internal.c
        third_party/xiph/opusfile/src/opusfile.c
        third_party/xiph/opusfile/src/stream.c
        third_party/xiph/ogg/src/bitwise.c
        third_party/xiph/ogg/src/framing.c)

target_include_directories(${NATIVE_LIB} PUBLIC
        third_party/xiph/opus/include
        third_party/xiph/opus/silk
        third_party/xiph/opus/silk/fixed
        third_party/xiph/opus/celt
        third_party/xiph/opus
        third_party/xiph/opusfile/include
        third_party/xiph/ogg/include
        third_party/libyuv/include
        third_party/absl
        third_party/boringssl/include
        openssl/include
        prebuild/include/${ANDROID_ABI}
        third_party/ffmpeg
        third_party/libvpx
        third_party/dav1d/include
        emoji
        exoplayer/include
        intro
        tlottie/include
        tgcalls/
        webrtc/
        td/tde2e
        td/tde2e/td/e2e
)

target_link_options(${NATIVE_LIB} PRIVATE "-Wl,-z,max-page-size=16384")
target_link_options(${NATIVE_LIB} PRIVATE "-Wl,-z,common-page-size=16384")
target_link_options(${NATIVE_LIB} PRIVATE "-Wl,--version-script=${CMAKE_CURRENT_SOURCE_DIR}/exports.map")
target_link_libraries(${NATIVE_LIB}
        -Wl,--whole-archive rnnoise openh264 voipandroid -Wl,--no-whole-archive
        webrtc_absl
        tgcalls
        wasm
        tgcalls_tp
        tgnet
        prisma
        tlottie
        wallet_engine_jni
        wallet_engine_cpp
        sqlite
        swscale
        avformat
        avcodec
        swresample
        libvpx
        libdav1d
        libopus
        tde2e
        tdutils
        yuv
        avutil
        ssl
        crypto
        jnigraphics
        log
        z
        GLESv2
        EGL
        android
        OpenSLES
        cpufeatures
        breakpad)

#if (${ANDROID_ABI} STREQUAL "x86" OR ${ANDROID_ABI} STREQUAL "x86_64")
#    target_link_libraries(${NATIVE_LIB}
#            -Wl,--whole-archive vpxasm -Wl,--no-whole-archive
#            c)
#endif()

include(AndroidNdkModules)
android_ndk_import_module_cpufeatures()
