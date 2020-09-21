cmake_minimum_required(VERSION 3.4.1)
project(tmessages.32 LANGUAGES C CXX ASM)
include(AndroidNdkModules)

android_ndk_import_module_cpufeatures()

add_library(avutil STATIC IMPORTED)

if (${ANDROID_ABI} STREQUAL "arm64-v8a")
    set_target_properties(
            avutil
            PROPERTIES IMPORTED_LOCATION
            ${CMAKE_SOURCE_DIR}/ffmpeg/arm64/libavutil.a
    )
elseif (${ANDROID_ABI} STREQUAL "armeabi-v7a")
    set_target_properties(
            avutil
            PROPERTIES IMPORTED_LOCATION
            ${CMAKE_SOURCE_DIR}/ffmpeg/armv7-a/libavutil.a
    )
elseif (${ANDROID_ABI} STREQUAL "x86")
    set_target_properties(
            avutil
            PROPERTIES IMPORTED_LOCATION
            ${CMAKE_SOURCE_DIR}/ffmpeg/i686/libavutil.a
    )
elseif (${ANDROID_ABI} STREQUAL "x86_64")
    set_target_properties(
            avutil
            PROPERTIES IMPORTED_LOCATION
            ${CMAKE_SOURCE_DIR}/ffmpeg/x86_64/libavutil.a
    )
endif ()

add_library(avformat STATIC IMPORTED)

if (${ANDROID_ABI} STREQUAL "arm64-v8a")
    set_target_properties(
            avformat
            PROPERTIES IMPORTED_LOCATION
            ${CMAKE_SOURCE_DIR}/ffmpeg/arm64/libavformat.a
    )
elseif (${ANDROID_ABI} STREQUAL "armeabi-v7a")
    set_target_properties(
            avformat
            PROPERTIES IMPORTED_LOCATION
            ${CMAKE_SOURCE_DIR}/ffmpeg/armv7-a/libavformat.a
    )
elseif (${ANDROID_ABI} STREQUAL "x86")
    set_target_properties(
            avformat
            PROPERTIES IMPORTED_LOCATION
            ${CMAKE_SOURCE_DIR}/ffmpeg/i686/libavformat.a
    )
elseif (${ANDROID_ABI} STREQUAL "x86_64")
    set_target_properties(
            avformat
            PROPERTIES IMPORTED_LOCATION
            ${CMAKE_SOURCE_DIR}/ffmpeg/x86_64/libavformat.a
    )
endif ()

add_library(avcodec STATIC IMPORTED)

if (${ANDROID_ABI} STREQUAL "arm64-v8a")
    set_target_properties(
            avcodec
            PROPERTIES IMPORTED_LOCATION
            ${CMAKE_SOURCE_DIR}/ffmpeg/arm64/libavcodec.a
    )
elseif (${ANDROID_ABI} STREQUAL "armeabi-v7a")
    set_target_properties(
            avcodec
            PROPERTIES IMPORTED_LOCATION
            ${CMAKE_SOURCE_DIR}/ffmpeg/armv7-a/libavcodec.a
    )
elseif (${ANDROID_ABI} STREQUAL "x86")
    set_target_properties(
            avcodec
            PROPERTIES IMPORTED_LOCATION
            ${CMAKE_SOURCE_DIR}/ffmpeg/i686/libavcodec.a
    )
elseif (${ANDROID_ABI} STREQUAL "x86_64")
    set_target_properties(
            avcodec
            PROPERTIES IMPORTED_LOCATION
            ${CMAKE_SOURCE_DIR}/ffmpeg/x86_64/libavcodec.a
    )
endif ()

add_library(avresample STATIC IMPORTED)

if (${ANDROID_ABI} STREQUAL "arm64-v8a")
    set_target_properties(
            avresample
            PROPERTIES IMPORTED_LOCATION
            ${CMAKE_SOURCE_DIR}/ffmpeg/arm64/libavresample.a
    )
elseif (${ANDROID_ABI} STREQUAL "armeabi-v7a")
    set_target_properties(
            avresample
            PROPERTIES IMPORTED_LOCATION
            ${CMAKE_SOURCE_DIR}/ffmpeg/armv7-a/libavresample.a
    )
elseif (${ANDROID_ABI} STREQUAL "x86")
    set_target_properties(
            avresample
            PROPERTIES IMPORTED_LOCATION
            ${CMAKE_SOURCE_DIR}/ffmpeg/i686/libavresample.a
    )
elseif (${ANDROID_ABI} STREQUAL "x86_64")
    set_target_properties(
            avresample
            PROPERTIES IMPORTED_LOCATION
            ${CMAKE_SOURCE_DIR}/ffmpeg/x86_64/libavresample.a
    )
endif ()

add_library(swscale STATIC IMPORTED)

if (${ANDROID_ABI} STREQUAL "arm64-v8a")
    set_target_properties(
            swscale
            PROPERTIES IMPORTED_LOCATION
            ${CMAKE_SOURCE_DIR}/ffmpeg/arm64/libswscale.a
    )
elseif (${ANDROID_ABI} STREQUAL "armeabi-v7a")
    set_target_properties(
            swscale
            PROPERTIES IMPORTED_LOCATION
            ${CMAKE_SOURCE_DIR}/ffmpeg/armv7-a/libswscale.a
    )
elseif (${ANDROID_ABI} STREQUAL "x86")
    set_target_properties(
            swscale
            PROPERTIES IMPORTED_LOCATION
            ${CMAKE_SOURCE_DIR}/ffmpeg/i686/libswscale.a
    )
elseif (${ANDROID_ABI} STREQUAL "x86_64")
    set_target_properties(
            swscale
            PROPERTIES IMPORTED_LOCATION
            ${CMAKE_SOURCE_DIR}/ffmpeg/x86_64/libswscale.a
    )
endif ()

add_library(crypto STATIC IMPORTED)

if (${ANDROID_ABI} STREQUAL "arm64-v8a")
    set_target_properties(
            crypto
            PROPERTIES IMPORTED_LOCATION
            ${CMAKE_SOURCE_DIR}/boringssl/lib/libcrypto_arm64-v8a.a
    )
elseif (${ANDROID_ABI} STREQUAL "armeabi-v7a")
    set_target_properties(
            crypto
            PROPERTIES IMPORTED_LOCATION
            ${CMAKE_SOURCE_DIR}/boringssl/lib/libcrypto_armeabi-v7a.a
    )
elseif (${ANDROID_ABI} STREQUAL "x86")
    set_target_properties(
            crypto
            PROPERTIES IMPORTED_LOCATION
            ${CMAKE_SOURCE_DIR}/boringssl/lib/libcrypto_x86.a
    )
elseif (${ANDROID_ABI} STREQUAL "x86_64")
    set_target_properties(
            crypto PROPERTIES IMPORTED_LOCATION
            ${CMAKE_SOURCE_DIR}/boringssl/lib/libcrypto_x86_64.a
    )
endif ()

add_library(ssl STATIC IMPORTED)

if (${ANDROID_ABI} STREQUAL "arm64-v8a")
    set_target_properties(
            ssl
            PROPERTIES IMPORTED_LOCATION
            ${CMAKE_SOURCE_DIR}/boringssl/lib/libssl_arm64-v8a.a
    )
elseif (${ANDROID_ABI} STREQUAL "armeabi-v7a")
    set_target_properties(
            ssl
            PROPERTIES IMPORTED_LOCATION
            ${CMAKE_SOURCE_DIR}/boringssl/lib/libssl_armeabi-v7a.a
    )
elseif (${ANDROID_ABI} STREQUAL "x86")
    set_target_properties(
            ssl
            PROPERTIES IMPORTED_LOCATION
            ${CMAKE_SOURCE_DIR}/boringssl/lib/libssl_x86.a
    )
elseif (${ANDROID_ABI} STREQUAL "x86_64")
    set_target_properties(
            ssl
            PROPERTIES IMPORTED_LOCATION
            ${CMAKE_SOURCE_DIR}/boringssl/lib/libssl_x86_64.a
    )
endif ()

if (${ANDROID_ABI} STREQUAL ("x86" OR "x86_64"))
    add_library(libvpx_yasm STATIC IMPORTED)
    if (${ANDROID_ABI} STREQUAL "x86")
        set_target_properties(
                libvpx_yasm
                PROPERTIES IMPORTED_LOCATION
                ${CMAKE_SOURCE_DIR}/third_party/libvpx/source/libvpx/vpx_dsp/x86/libvpx_x86_yasm.a
        )
    elseif (${ANDROID_ABI} STREQUAL "x86_64")
        set_target_properties(
                libvpx_yasm
                PROPERTIES IMPORTED_LOCATION
                ${CMAKE_SOURCE_DIR}/third_party/libvpx/source/libvpx/vpx_dsp/x86/libvpx_x86_64_yasm.a
        )
    endif ()
endif ()

unset(CMAKE_CXX_FLAGS)
unset(CMAKE_C_FLAGS)
set(CMAKE_CXX_FLAGS "-Wall -DANDROID -finline-functions -ffast-math -fno-strict-aliasing -O3 -frtti -D__STDC_LIMIT_MACROS -Wno-unknown-pragmas")
set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} -DBSD=1 -funroll-loops")
set(CMAKE_C_FLAGS "-O3 -DUSE_KISS_FFT -fexceptions -D__STDC_LIMIT_MACROS -DTGVOIP_NO_VIDEO")
set(CMAKE_C_FLAGS "${CMAKE_C_FLAGS} -DNULL=0 -DSOCKLEN_T=socklen_t -DLOCALE_NOT_USED -D_LARGEFILE_SOURCE=1")
set(CMAKE_C_FLAGS "${CMAKE_C_FLAGS} -Drestrict= -DOPUS_BUILD -DFIXED_POINT -DUSE_ALLOCA -DHAVE_LRINT -DHAVE_LRINTF -fno-math-errno")
set(CMAKE_C_FLAGS "${CMAKE_C_FLAGS} -DHAVE_PTHREAD -finline-functions -ffast-math -DRTC_ENABLE_VP9 -DWEBRTC_POSIX -DWEBRTC_LINUX -DWEBRTC_ANDROID -DWEBRTC_APM_DEBUG_DUMP=0 -DWEBRTC_USE_BUILTIN_ISAC_FLOAT -DWEBRTC_OPUS_VARIABLE_COMPLEXITY=0 -DHAVE_NETINET_IN_H -DWEBRTC_INCLUDE_INTERNAL_AUDIO_DEVICE -D__Userspace__ -DSCTP_SIMPLE_ALLOCATOR -DSCTP_PROCESS_LEVEL_LOCKS -D__Userspace_os_Linux")

if (${ANDROID_ABI} STREQUAL "armeabi-v7a")
    set(CMAKE_C_FLAGS "${CMAKE_C_FLAGS} -DWEBRTC_ARCH_ARM -DWEBRTC_ARCH_ARM_V7 -DWEBRTC_HAS_NEON")
    set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} -DWEBRTC_ARCH_ARM -DWEBRTC_ARCH_ARM_V7 -DWEBRTC_HAS_NEON")
elseif (${ANDROID_ABI} STREQUAL "arm64-v8a")
    set(CMAKE_C_FLAGS "${CMAKE_C_FLAGS} -DWEBRTC_ARCH_ARM64 -DWEBRTC_HAS_NEON")
    set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} -DWEBRTC_ARCH_ARM64 -DWEBRTC_HAS_NEON")
elseif (${ANDROID_ABI} STREQUAL "x86")
    set(CMAKE_C_FLAGS "${CMAKE_C_FLAGS} -DHAVE_SSE2")
elseif (${ANDROID_ABI} STREQUAL "x86_64")
    set(CMAKE_C_FLAGS "${CMAKE_C_FLAGS} -DHAVE_SSE2")
endif ()

include_directories(
        opus/include
        opus/silk
        opus/silk/fixed
        opus/celt
        opus
        opus/opusfile
        boringssl/include
        webrtc/
        tgcalls
        libtgvoip
)

file(GLOB src_tgvoip
        libtgvoip/logging.cpp
        libtgvoip/VoIPController.cpp
        libtgvoip/VoIPGroupController.cpp
        libtgvoip/Buffers.cpp
        libtgvoip/BlockingQueue.cpp
        libtgvoip/audio/AudioInput.cpp
        libtgvoip/os/android/AudioInputOpenSLES.cpp
        libtgvoip/MediaStreamItf.cpp
        libtgvoip/audio/AudioOutput.cpp
        libtgvoip/OpusEncoder.cpp
        libtgvoip/os/android/AudioOutputOpenSLES.cpp
        libtgvoip/JitterBuffer.cpp
        libtgvoip/OpusDecoder.cpp
        libtgvoip/os/android/OpenSLEngineWrapper.cpp
        libtgvoip/os/android/AudioInputAndroid.cpp
        libtgvoip/os/android/AudioOutputAndroid.cpp
        libtgvoip/EchoCanceller.cpp
        libtgvoip/CongestionControl.cpp
        libtgvoip/VoIPServerConfig.cpp
        libtgvoip/audio/Resampler.cpp
        libtgvoip/NetworkSocket.cpp
        libtgvoip/os/posix/NetworkSocketPosix.cpp
        libtgvoip/PacketReassembler.cpp
        libtgvoip/MessageThread.cpp
        libtgvoip/json11.cpp
        libtgvoip/audio/AudioIO.cpp
        libtgvoip/video/VideoRenderer.cpp
        libtgvoip/video/VideoSource.cpp
        libtgvoip/video/ScreamCongestionController.cpp
        libtgvoip/os/android/VideoSourceAndroid.cpp
        libtgvoip/os/android/VideoRendererAndroid.cpp
        libtgvoip/client/android/org_telegram_messenger_voip_Instance.cpp
        libtgvoip/client/android/tg_voip_jni.cpp
        )

add_library(tgvoip STATIC
        ${src_tgvoip}
        )

target_link_libraries(tgvoip crypto)

#end tgvoip
unset(CMAKE_CXX_FLAGS)
unset(CMAKE_C_FLAGS)
#start tgcall_tp
set(CMAKE_CXX_FLAGS "-Wall -std=c++14 -DANDROID -DHAVE_PTHREAD -finline-functions -ffast-math -Os")
set(CMAKE_C_FLAGS "-std=c11")
add_definitions(
        -DRTC_ENABLE_VP9
        -DWEBRTC_POSIX
        -DWEBRTC_LINUX
        -DWEBRTC_ANDROID
        -DWEBRTC_APM_DEBUG_DUMP=0
        -DWEBRTC_USE_BUILTIN_ISAC_FLOAT
        -DWEBRTC_OPUS_VARIABLE_COMPLEXITY=0
        -DHAVE_NETINET_IN_H
        -DWEBRTC_INCLUDE_INTERNAL_AUDIO_DEVICE
        -D__Userspace__
        -DSCTP_SIMPLE_ALLOCATOR
        -DSCTP_PROCESS_LEVEL_LOCKS
        -D__Userspace_os_Linux
        -DHAVE_WEBRTC_VIDEO
)
if (${ANDROID_ABI} STREQUAL "armeabi-v7a")
    set(CMAKE_C_FLAGS "${CMAKE_C_FLAGS} -DWEBRTC_ARCH_ARM -DWEBRTC_ARCH_ARM_V7 -DWEBRTC_HAS_NEON")
    set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} -DWEBRTC_ARCH_ARM -DWEBRTC_ARCH_ARM_V7 -DWEBRTC_HAS_NEON")
elseif (${ANDROID_ABI} STREQUAL "arm64-v8a")
    set(CMAKE_C_FLAGS "${CMAKE_C_FLAGS} -DWEBRTC_ARCH_ARM64 -DWEBRTC_HAS_NEON")
    set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} -DWEBRTC_ARCH_ARM64 -DWEBRTC_HAS_NEON")
elseif (${ANDROID_ABI} STREQUAL "x86")
    set(CMAKE_C_FLAGS "${CMAKE_C_FLAGS} -DHAVE_SSE2")
elseif (${ANDROID_ABI} STREQUAL "x86_64")
    set(CMAKE_C_FLAGS "${CMAKE_C_FLAGS} -DHAVE_SSE2")
endif ()

include_directories(
        boringssl/include/
        tgcalls/
        webrtc/
        opus/include
        opus/silk
        opus/silk/fixed
        opus/celt
        opus/
        /
        opus/opusfile
        third_party/libyuv/include
        third_party/usrsctplib
        third_party/libsrtp/include
        third_party/libsrtp/config
        third_party/libsrtp/crypto/include
        third_party/libvpx/source/libvpx
        third_party/libvpx/source/config
        third_party
)

if (${ANDROID_ABI} STREQUAL "armeabi-v7a")
    include_directories(third_party/libvpx/source/config/linux/arm-neon-cpu-detect)
elseif (${ANDROID_ABI} STREQUAL "arm64-v8a")
    include_directories(third_party/libvpx/source/config/linux/arm64-highbd)
elseif (${ANDROID_ABI} STREQUAL "x86")
    include_directories(third_party/libvpx/source/config/linux/ia32)
elseif (${ANDROID_ABI} STREQUAL "x86_64")
    include_directories(third_party/libvpx/source/config/linux/x64)
endif ()

file(GLOB src_tgcall_tp
        third_party/rnnoise/src/rnn_vad_weights.cc
        third_party/pffft/src/fftpack.c
        third_party/pffft/src/pffft.c
        third_party/libvpx/source/libvpx/args.c
        third_party/libvpx/source/libvpx/ivfdec.c
        third_party/libvpx/source/libvpx/ivfenc.c
        third_party/libvpx/source/libvpx/md5_utils.c
        third_party/libvpx/source/libvpx/rate_hist.c
        third_party/libvpx/source/libvpx/tools_common.c
        third_party/libvpx/source/libvpx/video_reader.c
        third_party/libvpx/source/libvpx/video_writer.c
        third_party/libvpx/source/libvpx/vp8/common/alloccommon.c
        third_party/libvpx/source/libvpx/vp8/common/blockd.c
        third_party/libvpx/source/libvpx/vp8/common/context.c
        third_party/libvpx/source/libvpx/vp8/common/debugmodes.c
        third_party/libvpx/source/libvpx/vp8/common/dequantize.c
        third_party/libvpx/source/libvpx/vp8/common/entropy.c
        third_party/libvpx/source/libvpx/vp8/common/entropymode.c
        third_party/libvpx/source/libvpx/vp8/common/entropymv.c
        third_party/libvpx/source/libvpx/vp8/common/extend.c
        third_party/libvpx/source/libvpx/vp8/common/filter.c
        third_party/libvpx/source/libvpx/vp8/common/findnearmv.c
        third_party/libvpx/source/libvpx/vp8/common/generic/systemdependent.c
        third_party/libvpx/source/libvpx/vp8/common/idct_blk.c
        third_party/libvpx/source/libvpx/vp8/common/idctllm.c
        third_party/libvpx/source/libvpx/vp8/common/loopfilter_filters.c
        third_party/libvpx/source/libvpx/vp8/common/mbpitch.c
        third_party/libvpx/source/libvpx/vp8/common/mfqe.c
        third_party/libvpx/source/libvpx/vp8/common/modecont.c
        third_party/libvpx/source/libvpx/vp8/common/postproc.c
        third_party/libvpx/source/libvpx/vp8/common/quant_common.c
        third_party/libvpx/source/libvpx/vp8/common/reconinter.c
        third_party/libvpx/source/libvpx/vp8/common/reconintra.c
        third_party/libvpx/source/libvpx/vp8/common/reconintra4x4.c
        third_party/libvpx/source/libvpx/vp8/common/rtcd.c
        third_party/libvpx/source/libvpx/vp8/common/setupintrarecon.c
        third_party/libvpx/source/libvpx/vp8/common/swapyv12buffer.c
        third_party/libvpx/source/libvpx/vp8/common/treecoder.c
        third_party/libvpx/source/libvpx/vp8/common/vp8_loopfilter.c
        third_party/libvpx/source/libvpx/vp8/common/vp8_skin_detection.c
        third_party/libvpx/source/libvpx/vp8/decoder/dboolhuff.c
        third_party/libvpx/source/libvpx/vp8/decoder/decodeframe.c
        third_party/libvpx/source/libvpx/vp8/decoder/decodemv.c
        third_party/libvpx/source/libvpx/vp8/decoder/detokenize.c
        third_party/libvpx/source/libvpx/vp8/decoder/onyxd_if.c
        third_party/libvpx/source/libvpx/vp8/decoder/threading.c
        third_party/libvpx/source/libvpx/vp8/encoder/bitstream.c
        third_party/libvpx/source/libvpx/vp8/encoder/boolhuff.c
        third_party/libvpx/source/libvpx/vp8/encoder/copy_c.c
        third_party/libvpx/source/libvpx/vp8/encoder/dct.c
        third_party/libvpx/source/libvpx/vp8/encoder/denoising.c
        third_party/libvpx/source/libvpx/vp8/encoder/encodeframe.c
        third_party/libvpx/source/libvpx/vp8/encoder/encodeintra.c
        third_party/libvpx/source/libvpx/vp8/encoder/encodemb.c
        third_party/libvpx/source/libvpx/vp8/encoder/encodemv.c
        third_party/libvpx/source/libvpx/vp8/encoder/ethreading.c
        third_party/libvpx/source/libvpx/vp8/encoder/firstpass.c
        third_party/libvpx/source/libvpx/vp8/encoder/lookahead.c
        third_party/libvpx/source/libvpx/vp8/encoder/mcomp.c
        third_party/libvpx/source/libvpx/vp8/encoder/modecosts.c
        third_party/libvpx/source/libvpx/vp8/encoder/mr_dissim.c
        third_party/libvpx/source/libvpx/vp8/encoder/onyx_if.c
        third_party/libvpx/source/libvpx/vp8/encoder/pickinter.c
        third_party/libvpx/source/libvpx/vp8/encoder/picklpf.c
        third_party/libvpx/source/libvpx/vp8/encoder/ratectrl.c
        third_party/libvpx/source/libvpx/vp8/encoder/rdopt.c
        third_party/libvpx/source/libvpx/vp8/encoder/segmentation.c
        third_party/libvpx/source/libvpx/vp8/encoder/temporal_filter.c
        third_party/libvpx/source/libvpx/vp8/encoder/tokenize.c
        third_party/libvpx/source/libvpx/vp8/encoder/treewriter.c
        third_party/libvpx/source/libvpx/vp8/encoder/vp8_quantize.c
        third_party/libvpx/source/libvpx/vp8/vp8_cx_iface.c
        third_party/libvpx/source/libvpx/vp8/vp8_dx_iface.c
        third_party/libvpx/source/libvpx/vp9/common/vp9_alloccommon.c
        third_party/libvpx/source/libvpx/vp9/common/vp9_blockd.c
        third_party/libvpx/source/libvpx/vp9/common/vp9_common_data.c
        third_party/libvpx/source/libvpx/vp9/common/vp9_debugmodes.c
        third_party/libvpx/source/libvpx/vp9/common/vp9_entropy.c
        third_party/libvpx/source/libvpx/vp9/common/vp9_entropymode.c
        third_party/libvpx/source/libvpx/vp9/common/vp9_entropymv.c
        third_party/libvpx/source/libvpx/vp9/common/vp9_filter.c
        third_party/libvpx/source/libvpx/vp9/common/vp9_frame_buffers.c
        third_party/libvpx/source/libvpx/vp9/common/vp9_idct.c
        third_party/libvpx/source/libvpx/vp9/common/vp9_loopfilter.c
        third_party/libvpx/source/libvpx/vp9/common/vp9_mfqe.c
        third_party/libvpx/source/libvpx/vp9/common/vp9_mvref_common.c
        third_party/libvpx/source/libvpx/vp9/common/vp9_postproc.c
        third_party/libvpx/source/libvpx/vp9/common/vp9_pred_common.c
        third_party/libvpx/source/libvpx/vp9/common/vp9_quant_common.c
        third_party/libvpx/source/libvpx/vp9/common/vp9_reconinter.c
        third_party/libvpx/source/libvpx/vp9/common/vp9_reconintra.c
        third_party/libvpx/source/libvpx/vp9/common/vp9_rtcd.c
        third_party/libvpx/source/libvpx/vp9/common/vp9_scale.c
        third_party/libvpx/source/libvpx/vp9/common/vp9_scan.c
        third_party/libvpx/source/libvpx/vp9/common/vp9_seg_common.c
        third_party/libvpx/source/libvpx/vp9/common/vp9_thread_common.c
        third_party/libvpx/source/libvpx/vp9/common/vp9_tile_common.c
        third_party/libvpx/source/libvpx/vp9/decoder/vp9_decodeframe.c
        third_party/libvpx/source/libvpx/vp9/decoder/vp9_decodemv.c
        third_party/libvpx/source/libvpx/vp9/decoder/vp9_decoder.c
        third_party/libvpx/source/libvpx/vp9/decoder/vp9_detokenize.c
        third_party/libvpx/source/libvpx/vp9/decoder/vp9_dsubexp.c
        third_party/libvpx/source/libvpx/vp9/decoder/vp9_job_queue.c
        third_party/libvpx/source/libvpx/vp9/encoder/vp9_alt_ref_aq.c
        third_party/libvpx/source/libvpx/vp9/encoder/vp9_aq_360.c
        third_party/libvpx/source/libvpx/vp9/encoder/vp9_aq_complexity.c
        third_party/libvpx/source/libvpx/vp9/encoder/vp9_aq_cyclicrefresh.c
        third_party/libvpx/source/libvpx/vp9/encoder/vp9_aq_variance.c
        third_party/libvpx/source/libvpx/vp9/encoder/vp9_bitstream.c
        third_party/libvpx/source/libvpx/vp9/encoder/vp9_blockiness.c
        third_party/libvpx/source/libvpx/vp9/encoder/vp9_context_tree.c
        third_party/libvpx/source/libvpx/vp9/encoder/vp9_cost.c
        third_party/libvpx/source/libvpx/vp9/encoder/vp9_dct.c
        third_party/libvpx/source/libvpx/vp9/encoder/vp9_denoiser.c
        third_party/libvpx/source/libvpx/vp9/encoder/vp9_encodeframe.c
        third_party/libvpx/source/libvpx/vp9/encoder/vp9_encodemb.c
        third_party/libvpx/source/libvpx/vp9/encoder/vp9_encodemv.c
        third_party/libvpx/source/libvpx/vp9/encoder/vp9_encoder.c
        third_party/libvpx/source/libvpx/vp9/encoder/vp9_ethread.c
        third_party/libvpx/source/libvpx/vp9/encoder/vp9_extend.c
        third_party/libvpx/source/libvpx/vp9/encoder/vp9_firstpass.c
        third_party/libvpx/source/libvpx/vp9/encoder/vp9_frame_scale.c
        third_party/libvpx/source/libvpx/vp9/encoder/vp9_lookahead.c
        third_party/libvpx/source/libvpx/vp9/encoder/vp9_mbgraph.c
        third_party/libvpx/source/libvpx/vp9/encoder/vp9_mcomp.c
        third_party/libvpx/source/libvpx/vp9/encoder/vp9_multi_thread.c
        third_party/libvpx/source/libvpx/vp9/encoder/vp9_noise_estimate.c
        third_party/libvpx/source/libvpx/vp9/encoder/vp9_non_greedy_mv.c
        third_party/libvpx/source/libvpx/vp9/encoder/vp9_picklpf.c
        third_party/libvpx/source/libvpx/vp9/encoder/vp9_pickmode.c
        third_party/libvpx/source/libvpx/vp9/encoder/vp9_quantize.c
        third_party/libvpx/source/libvpx/vp9/encoder/vp9_ratectrl.c
        third_party/libvpx/source/libvpx/vp9/encoder/vp9_rd.c
        third_party/libvpx/source/libvpx/vp9/encoder/vp9_rdopt.c
        third_party/libvpx/source/libvpx/vp9/encoder/vp9_resize.c
        third_party/libvpx/source/libvpx/vp9/encoder/vp9_segmentation.c
        third_party/libvpx/source/libvpx/vp9/encoder/vp9_skin_detection.c
        third_party/libvpx/source/libvpx/vp9/encoder/vp9_speed_features.c
        third_party/libvpx/source/libvpx/vp9/encoder/vp9_subexp.c
        third_party/libvpx/source/libvpx/vp9/encoder/vp9_svc_layercontext.c
        third_party/libvpx/source/libvpx/vp9/encoder/vp9_temporal_filter.c
        third_party/libvpx/source/libvpx/vp9/encoder/vp9_tokenize.c
        third_party/libvpx/source/libvpx/vp9/encoder/vp9_treewriter.c
        third_party/libvpx/source/libvpx/vp9/vp9_cx_iface.c
        third_party/libvpx/source/libvpx/vp9/vp9_dx_iface.c
        third_party/libvpx/source/libvpx/vp9/vp9_iface_common.c
        third_party/libvpx/source/libvpx/vpx/src/vpx_codec.c
        third_party/libvpx/source/libvpx/vpx/src/vpx_decoder.c
        third_party/libvpx/source/libvpx/vpx/src/vpx_encoder.c
        third_party/libvpx/source/libvpx/vpx/src/vpx_image.c
        third_party/libvpx/source/libvpx/vpx_dsp/add_noise.c
        third_party/libvpx/source/libvpx/vpx_dsp/avg.c
        third_party/libvpx/source/libvpx/vpx_dsp/bitreader.c
        third_party/libvpx/source/libvpx/vpx_dsp/bitreader_buffer.c
        third_party/libvpx/source/libvpx/vpx_dsp/bitwriter.c
        third_party/libvpx/source/libvpx/vpx_dsp/bitwriter_buffer.c
        third_party/libvpx/source/libvpx/vpx_dsp/deblock.c
        third_party/libvpx/source/libvpx/vpx_dsp/fastssim.c
        third_party/libvpx/source/libvpx/vpx_dsp/fwd_txfm.c
        third_party/libvpx/source/libvpx/vpx_dsp/intrapred.c
        third_party/libvpx/source/libvpx/vpx_dsp/inv_txfm.c
        third_party/libvpx/source/libvpx/vpx_dsp/loopfilter.c
        third_party/libvpx/source/libvpx/vpx_dsp/prob.c
        third_party/libvpx/source/libvpx/vpx_dsp/psnr.c
        third_party/libvpx/source/libvpx/vpx_dsp/psnrhvs.c
        third_party/libvpx/source/libvpx/vpx_dsp/quantize.c
        third_party/libvpx/source/libvpx/vpx_dsp/sad.c
        third_party/libvpx/source/libvpx/vpx_dsp/skin_detection.c
        third_party/libvpx/source/libvpx/vpx_dsp/ssim.c
        third_party/libvpx/source/libvpx/vpx_dsp/subtract.c
        third_party/libvpx/source/libvpx/vpx_dsp/sum_squares.c
        third_party/libvpx/source/libvpx/vpx_dsp/variance.c
        third_party/libvpx/source/libvpx/vpx_dsp/vpx_convolve.c
        third_party/libvpx/source/libvpx/vpx_dsp/vpx_dsp_rtcd.c
        third_party/libvpx/source/libvpx/vpx_mem/vpx_mem.c
        third_party/libvpx/source/libvpx/vpx_ports/arm_cpudetect.c
        third_party/libvpx/source/libvpx/vpx_scale/generic/gen_scalers.c
        third_party/libvpx/source/libvpx/vpx_scale/generic/vpx_scale.c
        third_party/libvpx/source/libvpx/vpx_scale/generic/yv12config.c
        third_party/libvpx/source/libvpx/vpx_scale/generic/yv12extend.c
        third_party/libvpx/source/libvpx/vpx_scale/vpx_scale_rtcd.c
        third_party/libvpx/source/libvpx/vpx_util/vpx_debug_util.c
        third_party/libvpx/source/libvpx/vpx_util/vpx_thread.c
        third_party/libvpx/source/libvpx/vpx_util/vpx_write_yuv_frame.c
        third_party/libvpx/source/libvpx/vpxdec.c
        third_party/libvpx/source/libvpx/vpxenc.c
        third_party/libvpx/source/libvpx/vpxstats.c
        third_party/libvpx/source/libvpx/warnings.c
        third_party/libvpx/source/libvpx/y4menc.c
        third_party/libvpx/source/libvpx/y4minput.c
        third_party/libsrtp/crypto/cipher/aes_gcm_ossl.c
        third_party/libsrtp/crypto/cipher/aes_icm_ossl.c
        third_party/libsrtp/crypto/cipher/cipher.c
        third_party/libsrtp/crypto/cipher/null_cipher.c
        third_party/libsrtp/crypto/hash/auth.c
        third_party/libsrtp/crypto/hash/hmac_ossl.c
        third_party/libsrtp/crypto/hash/null_auth.c
        third_party/libsrtp/crypto/kernel/alloc.c
        third_party/libsrtp/crypto/kernel/crypto_kernel.c
        third_party/libsrtp/crypto/kernel/err.c
        third_party/libsrtp/crypto/kernel/key.c
        third_party/libsrtp/crypto/math/datatypes.c
        third_party/libsrtp/crypto/math/stat.c
        third_party/libsrtp/crypto/replay/rdb.c
        third_party/libsrtp/crypto/replay/rdbx.c
        third_party/libsrtp/crypto/replay/ut_sim.c
        third_party/libsrtp/srtp/ekt.c
        third_party/libsrtp/srtp/srtp.c
        third_party/usrsctplib/netinet/sctp_asconf.c
        third_party/usrsctplib/netinet/sctp_auth.c
        third_party/usrsctplib/netinet/sctp_bsd_addr.c
        third_party/usrsctplib/netinet/sctp_callout.c
        third_party/usrsctplib/netinet/sctp_cc_functions.c
        third_party/usrsctplib/netinet/sctp_crc32.c
        third_party/usrsctplib/netinet/sctp_indata.c
        third_party/usrsctplib/netinet/sctp_input.c
        third_party/usrsctplib/netinet/sctp_output.c
        third_party/usrsctplib/netinet/sctp_pcb.c
        third_party/usrsctplib/netinet/sctp_peeloff.c
        third_party/usrsctplib/netinet/sctp_sha1.c
        third_party/usrsctplib/netinet/sctp_ss_functions.c
        third_party/usrsctplib/netinet/sctp_sysctl.c
        third_party/usrsctplib/netinet/sctp_timer.c
        third_party/usrsctplib/netinet/sctp_userspace.c
        third_party/usrsctplib/netinet/sctp_usrreq.c
        third_party/usrsctplib/netinet/sctputil.c
        third_party/usrsctplib/netinet6/sctp6_usrreq.c
        third_party/usrsctplib/user_environment.c
        third_party/usrsctplib/user_mbuf.c
        third_party/usrsctplib/user_recv_thread.c
        third_party/usrsctplib/user_socket.c
        webrtc/absl/base/dynamic_annotations.cc
        webrtc/absl/base/internal/cycleclock.cc
        webrtc/absl/base/internal/exception_safety_testing.cc
        webrtc/absl/base/internal/exponential_biased.cc
        webrtc/absl/base/internal/low_level_alloc.cc
        webrtc/absl/base/internal/periodic_sampler.cc
        webrtc/absl/base/internal/raw_logging.cc
        webrtc/absl/base/internal/scoped_set_env.cc
        webrtc/absl/base/internal/spinlock.cc
        webrtc/absl/base/internal/spinlock_wait.cc
        webrtc/absl/base/internal/strerror.cc
        webrtc/absl/base/internal/sysinfo.cc
        webrtc/absl/base/internal/thread_identity.cc
        webrtc/absl/base/internal/throw_delegate.cc
        webrtc/absl/base/internal/unscaledcycleclock.cc
        webrtc/absl/base/log_severity.cc
        webrtc/absl/container/internal/hash_generator_testing.cc
        webrtc/absl/container/internal/hashtablez_sampler.cc
        webrtc/absl/container/internal/hashtablez_sampler_force_weak_definition.cc
        webrtc/absl/container/internal/raw_hash_set.cc
        webrtc/absl/container/internal/test_instance_tracker.cc
        webrtc/absl/debugging/failure_signal_handler.cc
        webrtc/absl/debugging/internal/address_is_readable.cc
        webrtc/absl/debugging/internal/demangle.cc
        webrtc/absl/debugging/internal/elf_mem_image.cc
        webrtc/absl/debugging/internal/examine_stack.cc
        webrtc/absl/debugging/internal/stack_consumption.cc
        webrtc/absl/debugging/internal/vdso_support.cc
        webrtc/absl/debugging/leak_check.cc
        webrtc/absl/debugging/leak_check_disable.cc
        webrtc/absl/debugging/stacktrace.cc
        webrtc/absl/debugging/symbolize.cc
        webrtc/absl/flags/flag.cc
        webrtc/absl/flags/flag_test_defs.cc
        webrtc/absl/flags/internal/commandlineflag.cc
        webrtc/absl/flags/internal/flag.cc
        webrtc/absl/flags/internal/program_name.cc
        webrtc/absl/flags/internal/registry.cc
        webrtc/absl/flags/internal/type_erased.cc
        webrtc/absl/flags/internal/usage.cc
        webrtc/absl/flags/marshalling.cc
        webrtc/absl/flags/parse.cc
        webrtc/absl/flags/usage.cc
        webrtc/absl/flags/usage_config.cc
        webrtc/absl/hash/internal/city.cc
        webrtc/absl/hash/internal/hash.cc
        webrtc/absl/numeric/int128.cc
        webrtc/absl/random/discrete_distribution.cc
        webrtc/absl/random/gaussian_distribution.cc
        webrtc/absl/random/internal/chi_square.cc
        webrtc/absl/random/internal/distribution_test_util.cc
        webrtc/absl/random/internal/nanobenchmark.cc
        webrtc/absl/random/internal/pool_urbg.cc
        webrtc/absl/random/internal/randen.cc
        webrtc/absl/random/internal/randen_detect.cc
        webrtc/absl/random/internal/randen_hwaes.cc
        webrtc/absl/random/internal/randen_slow.cc
        webrtc/absl/random/internal/seed_material.cc
        webrtc/absl/random/seed_gen_exception.cc
        webrtc/absl/random/seed_sequences.cc
        webrtc/absl/status/status.cc
        webrtc/absl/status/status_payload_printer.cc
        webrtc/absl/strings/ascii.cc
        webrtc/absl/strings/charconv.cc
        webrtc/absl/strings/cord.cc
        webrtc/absl/strings/escaping.cc
        webrtc/absl/strings/internal/charconv_bigint.cc
        webrtc/absl/strings/internal/charconv_parse.cc
        webrtc/absl/strings/internal/escaping.cc
        webrtc/absl/strings/internal/memutil.cc
        webrtc/absl/strings/internal/ostringstream.cc
        webrtc/absl/strings/internal/pow10_helper.cc
        webrtc/absl/strings/internal/str_format/arg.cc
        webrtc/absl/strings/internal/str_format/bind.cc
        webrtc/absl/strings/internal/str_format/extension.cc
        webrtc/absl/strings/internal/str_format/float_conversion.cc
        webrtc/absl/strings/internal/str_format/output.cc
        webrtc/absl/strings/internal/str_format/parser.cc
        webrtc/absl/strings/internal/utf8.cc
        webrtc/absl/strings/match.cc
        webrtc/absl/strings/numbers.cc
        webrtc/absl/strings/str_cat.cc
        webrtc/absl/strings/str_replace.cc
        webrtc/absl/strings/str_split.cc
        webrtc/absl/strings/string_view.cc
        webrtc/absl/strings/substitute.cc
        webrtc/absl/synchronization/barrier.cc
        webrtc/absl/synchronization/blocking_counter.cc
        webrtc/absl/synchronization/internal/create_thread_identity.cc
        webrtc/absl/synchronization/internal/graphcycles.cc
        webrtc/absl/synchronization/internal/per_thread_sem.cc
        webrtc/absl/synchronization/internal/waiter.cc
        webrtc/absl/synchronization/mutex.cc
        webrtc/absl/synchronization/notification.cc
        webrtc/absl/time/civil_time.cc
        webrtc/absl/time/clock.cc
        webrtc/absl/time/duration.cc
        webrtc/absl/time/format.cc
        webrtc/absl/time/internal/cctz/src/civil_time_detail.cc
        webrtc/absl/time/internal/cctz/src/time_zone_fixed.cc
        webrtc/absl/time/internal/cctz/src/time_zone_format.cc
        webrtc/absl/time/internal/cctz/src/time_zone_if.cc
        webrtc/absl/time/internal/cctz/src/time_zone_impl.cc
        webrtc/absl/time/internal/cctz/src/time_zone_info.cc
        webrtc/absl/time/internal/cctz/src/time_zone_libc.cc
        webrtc/absl/time/internal/cctz/src/time_zone_lookup.cc
        webrtc/absl/time/internal/cctz/src/time_zone_posix.cc
        webrtc/absl/time/internal/cctz/src/zone_info_source.cc
        webrtc/absl/time/internal/test_util.cc
        webrtc/absl/time/time.cc
        webrtc/absl/types/bad_any_cast.cc
        webrtc/absl/types/bad_optional_access.cc
        webrtc/absl/types/bad_variant_access.cc
        )
if (${ANDROID_ABI} STREQUAL ("arm64-v8a" OR "armeabi-v7a"))
    file(GLOB src_tgcall_tp_neon
            third_party/libvpx/source/libvpx/vp8/common/arm/loopfilter_arm.c
            third_party/libvpx/source/libvpx/vp8/common/arm/neon/bilinearpredict_neon.c
            third_party/libvpx/source/libvpx/vp8/common/arm/neon/copymem_neon.c
            third_party/libvpx/source/libvpx/vp8/common/arm/neon/dc_only_idct_add_neon.c
            third_party/libvpx/source/libvpx/vp8/common/arm/neon/dequant_idct_neon.c
            third_party/libvpx/source/libvpx/vp8/common/arm/neon/dequantizeb_neon.c
            third_party/libvpx/source/libvpx/vp8/common/arm/neon/idct_blk_neon.c
            third_party/libvpx/source/libvpx/vp8/common/arm/neon/iwalsh_neon.c
            third_party/libvpx/source/libvpx/vp8/common/arm/neon/loopfiltersimplehorizontaledge_neon.c
            third_party/libvpx/source/libvpx/vp8/common/arm/neon/loopfiltersimpleverticaledge_neon.c
            third_party/libvpx/source/libvpx/vp8/common/arm/neon/mbloopfilter_neon.c
            third_party/libvpx/source/libvpx/vp8/common/arm/neon/shortidct4x4llm_neon.c
            third_party/libvpx/source/libvpx/vp8/common/arm/neon/sixtappredict_neon.c
            third_party/libvpx/source/libvpx/vp8/common/arm/neon/vp8_loopfilter_neon.c
            third_party/libvpx/source/libvpx/vp8/encoder/arm/neon/denoising_neon.c
            third_party/libvpx/source/libvpx/vp8/encoder/arm/neon/fastquantizeb_neon.c
            third_party/libvpx/source/libvpx/vp8/encoder/arm/neon/shortfdct_neon.c
            third_party/libvpx/source/libvpx/vp8/encoder/arm/neon/vp8_shortwalsh4x4_neon.c
            third_party/libvpx/source/libvpx/vp9/common/arm/neon/vp9_highbd_iht16x16_add_neon.c
            third_party/libvpx/source/libvpx/vp9/common/arm/neon/vp9_highbd_iht4x4_add_neon.c
            third_party/libvpx/source/libvpx/vp9/common/arm/neon/vp9_highbd_iht8x8_add_neon.c
            third_party/libvpx/source/libvpx/vp9/common/arm/neon/vp9_iht16x16_add_neon.c
            third_party/libvpx/source/libvpx/vp9/common/arm/neon/vp9_iht4x4_add_neon.c
            third_party/libvpx/source/libvpx/vp9/common/arm/neon/vp9_iht8x8_add_neon.c
            third_party/libvpx/source/libvpx/vp9/encoder/arm/neon/vp9_denoiser_neon.c
            third_party/libvpx/source/libvpx/vp9/encoder/arm/neon/vp9_error_neon.c
            third_party/libvpx/source/libvpx/vp9/encoder/arm/neon/vp9_frame_scale_neon.c
            third_party/libvpx/source/libvpx/vp9/encoder/arm/neon/vp9_quantize_neon.c
            third_party/libvpx/source/libvpx/vpx_dsp/arm/avg_neon.c
            third_party/libvpx/source/libvpx/vpx_dsp/arm/avg_pred_neon.c
            third_party/libvpx/source/libvpx/vpx_dsp/arm/deblock_neon.c
            third_party/libvpx/source/libvpx/vpx_dsp/arm/fdct16x16_neon.c
            third_party/libvpx/source/libvpx/vpx_dsp/arm/fdct32x32_neon.c
            third_party/libvpx/source/libvpx/vpx_dsp/arm/fdct_neon.c
            third_party/libvpx/source/libvpx/vpx_dsp/arm/fdct_partial_neon.c
            third_party/libvpx/source/libvpx/vpx_dsp/arm/fwd_txfm_neon.c
            third_party/libvpx/source/libvpx/vpx_dsp/arm/hadamard_neon.c
            third_party/libvpx/source/libvpx/vpx_dsp/arm/highbd_idct16x16_add_neon.c
            third_party/libvpx/source/libvpx/vpx_dsp/arm/highbd_idct32x32_1024_add_neon.c
            third_party/libvpx/source/libvpx/vpx_dsp/arm/highbd_idct32x32_135_add_neon.c
            third_party/libvpx/source/libvpx/vpx_dsp/arm/highbd_idct32x32_34_add_neon.c
            third_party/libvpx/source/libvpx/vpx_dsp/arm/highbd_idct32x32_add_neon.c
            third_party/libvpx/source/libvpx/vpx_dsp/arm/highbd_idct4x4_add_neon.c
            third_party/libvpx/source/libvpx/vpx_dsp/arm/highbd_idct8x8_add_neon.c
            third_party/libvpx/source/libvpx/vpx_dsp/arm/highbd_intrapred_neon.c
            third_party/libvpx/source/libvpx/vpx_dsp/arm/highbd_loopfilter_neon.c
            third_party/libvpx/source/libvpx/vpx_dsp/arm/highbd_vpx_convolve8_neon.c
            third_party/libvpx/source/libvpx/vpx_dsp/arm/highbd_vpx_convolve_avg_neon.c
            third_party/libvpx/source/libvpx/vpx_dsp/arm/highbd_vpx_convolve_copy_neon.c
            third_party/libvpx/source/libvpx/vpx_dsp/arm/highbd_vpx_convolve_neon.c
            third_party/libvpx/source/libvpx/vpx_dsp/arm/idct16x16_1_add_neon.c
            third_party/libvpx/source/libvpx/vpx_dsp/arm/idct16x16_add_neon.c
            third_party/libvpx/source/libvpx/vpx_dsp/arm/idct32x32_135_add_neon.c
            third_party/libvpx/source/libvpx/vpx_dsp/arm/idct32x32_1_add_neon.c
            third_party/libvpx/source/libvpx/vpx_dsp/arm/idct32x32_34_add_neon.c
            third_party/libvpx/source/libvpx/vpx_dsp/arm/idct32x32_add_neon.c
            third_party/libvpx/source/libvpx/vpx_dsp/arm/idct4x4_1_add_neon.c
            third_party/libvpx/source/libvpx/vpx_dsp/arm/idct4x4_add_neon.c
            third_party/libvpx/source/libvpx/vpx_dsp/arm/idct8x8_1_add_neon.c
            third_party/libvpx/source/libvpx/vpx_dsp/arm/idct8x8_add_neon.c
            third_party/libvpx/source/libvpx/vpx_dsp/arm/intrapred_neon.c
            third_party/libvpx/source/libvpx/vpx_dsp/arm/loopfilter_neon.c
            third_party/libvpx/source/libvpx/vpx_dsp/arm/quantize_neon.c
            third_party/libvpx/source/libvpx/vpx_dsp/arm/sad4d_neon.c
            third_party/libvpx/source/libvpx/vpx_dsp/arm/sad_neon.c
            third_party/libvpx/source/libvpx/vpx_dsp/arm/subpel_variance_neon.c
            third_party/libvpx/source/libvpx/vpx_dsp/arm/subtract_neon.c
            third_party/libvpx/source/libvpx/vpx_dsp/arm/sum_squares_neon.c
            third_party/libvpx/source/libvpx/vpx_dsp/arm/variance_neon.c
            third_party/libvpx/source/libvpx/vpx_dsp/arm/vpx_convolve8_neon.c
            third_party/libvpx/source/libvpx/vpx_dsp/arm/vpx_convolve8_neon_asm.c
            third_party/libvpx/source/libvpx/vpx_dsp/arm/vpx_convolve_avg_neon.c
            third_party/libvpx/source/libvpx/vpx_dsp/arm/vpx_convolve_copy_neon.c
            third_party/libvpx/source/libvpx/vpx_dsp/arm/vpx_convolve_neon.c
            third_party/libvpx/source/libvpx/vpx_dsp/arm/vpx_scaled_convolve8_neon.c
            )
elseif (${ANDROID_ABI} STREQUAL "x86")
    file(GLOB src_tgcall_tp_neon
            third_party/libvpx/source/libvpx/vp8/encoder/x86/denoising_sse2.c
            third_party/libvpx/source/libvpx/vp8/encoder/x86/vp8_enc_stubs_sse2.c
            third_party/libvpx/source/libvpx/vp8/encoder/x86/vp8_quantize_sse2.c
            third_party/libvpx/source/libvpx/vp8/common/x86/bilinear_filter_sse2.c
            third_party/libvpx/source/libvpx/vp8/common/x86/idct_blk_mmx.c
            third_party/libvpx/source/libvpx/vp8/common/x86/idct_blk_sse2.c
            third_party/libvpx/source/libvpx/vp8/common/x86/loopfilter_x86.c
            third_party/libvpx/source/libvpx/vp8/common/x86/vp8_asm_stubs.c
            third_party/libvpx/source/libvpx/vp9/common/x86/vp9_idct_intrin_sse2.c
            third_party/libvpx/source/libvpx/vp9/encoder/x86/vp9_dct_intrin_sse2.c
            third_party/libvpx/source/libvpx/vp9/encoder/x86/vp9_denoiser_sse2.c
            third_party/libvpx/source/libvpx/vp9/encoder/x86/vp9_highbd_block_error_intrin_sse2.c
            third_party/libvpx/source/libvpx/vp9/encoder/x86/vp9_quantize_sse2.c
            third_party/libvpx/source/libvpx/vpx_dsp/x86/avg_intrin_sse2.c
            third_party/libvpx/source/libvpx/vpx_dsp/x86/avg_pred_sse2.c
            third_party/libvpx/source/libvpx/vpx_dsp/x86/fwd_txfm_sse2.c
            third_party/libvpx/source/libvpx/vpx_dsp/x86/highbd_idct16x16_add_sse2.c
            third_party/libvpx/source/libvpx/vpx_dsp/x86/highbd_idct32x32_add_sse2.c
            third_party/libvpx/source/libvpx/vpx_dsp/x86/highbd_idct4x4_add_sse2.c
            third_party/libvpx/source/libvpx/vpx_dsp/x86/highbd_idct8x8_add_sse2.c
            third_party/libvpx/source/libvpx/vpx_dsp/x86/highbd_intrapred_intrin_sse2.c
            third_party/libvpx/source/libvpx/vpx_dsp/x86/highbd_loopfilter_sse2.c
            third_party/libvpx/source/libvpx/vpx_dsp/x86/highbd_quantize_intrin_sse2.c
            third_party/libvpx/source/libvpx/vpx_dsp/x86/highbd_variance_sse2.c
            third_party/libvpx/source/libvpx/vpx_dsp/x86/inv_txfm_sse2.c
            third_party/libvpx/source/libvpx/vpx_dsp/x86/loopfilter_sse2.c
            third_party/libvpx/source/libvpx/vpx_dsp/x86/post_proc_sse2.c
            third_party/libvpx/source/libvpx/vpx_dsp/x86/quantize_sse2.c
            third_party/libvpx/source/libvpx/vpx_dsp/x86/sum_squares_sse2.c
            third_party/libvpx/source/libvpx/vpx_dsp/x86/variance_sse2.c
            third_party/libvpx/source/libvpx/vpx_dsp/x86/vpx_subpixel_4t_intrin_sse2.c
            )
elseif (${ANDROID_ABI} STREQUAL "x86_64")
    file(GLOB src_tgcall_tp_neon
            third_party/libvpx/source/libvpx/vp8/encoder/x86/denoising_sse2.c
            third_party/libvpx/source/libvpx/vp8/encoder/x86/vp8_enc_stubs_sse2.c
            third_party/libvpx/source/libvpx/vp8/encoder/x86/vp8_quantize_sse2.c
            third_party/libvpx/source/libvpx/vp8/common/x86/bilinear_filter_sse2.c
            third_party/libvpx/source/libvpx/vp8/common/x86/idct_blk_mmx.c
            third_party/libvpx/source/libvpx/vp8/common/x86/idct_blk_sse2.c
            third_party/libvpx/source/libvpx/vp8/common/x86/loopfilter_x86.c
            third_party/libvpx/source/libvpx/vp8/common/x86/vp8_asm_stubs.c
            third_party/libvpx/source/libvpx/vp9/common/x86/vp9_idct_intrin_sse2.c
            third_party/libvpx/source/libvpx/vp9/encoder/x86/vp9_dct_intrin_sse2.c
            third_party/libvpx/source/libvpx/vp9/encoder/x86/vp9_denoiser_sse2.c
            third_party/libvpx/source/libvpx/vp9/encoder/x86/vp9_highbd_block_error_intrin_sse2.c
            third_party/libvpx/source/libvpx/vp9/encoder/x86/vp9_quantize_sse2.c
            third_party/libvpx/source/libvpx/vpx_dsp/x86/avg_intrin_sse2.c
            third_party/libvpx/source/libvpx/vpx_dsp/x86/avg_pred_sse2.c
            third_party/libvpx/source/libvpx/vpx_dsp/x86/fwd_txfm_sse2.c
            third_party/libvpx/source/libvpx/vpx_dsp/x86/highbd_idct16x16_add_sse2.c
            third_party/libvpx/source/libvpx/vpx_dsp/x86/highbd_idct32x32_add_sse2.c
            third_party/libvpx/source/libvpx/vpx_dsp/x86/highbd_idct4x4_add_sse2.c
            third_party/libvpx/source/libvpx/vpx_dsp/x86/highbd_idct8x8_add_sse2.c
            third_party/libvpx/source/libvpx/vpx_dsp/x86/highbd_intrapred_intrin_sse2.c
            third_party/libvpx/source/libvpx/vpx_dsp/x86/highbd_loopfilter_sse2.c
            third_party/libvpx/source/libvpx/vpx_dsp/x86/highbd_quantize_intrin_sse2.c
            third_party/libvpx/source/libvpx/vpx_dsp/x86/highbd_variance_sse2.c
            third_party/libvpx/source/libvpx/vpx_dsp/x86/inv_txfm_sse2.c
            third_party/libvpx/source/libvpx/vpx_dsp/x86/loopfilter_sse2.c
            third_party/libvpx/source/libvpx/vpx_dsp/x86/post_proc_sse2.c
            third_party/libvpx/source/libvpx/vpx_dsp/x86/quantize_sse2.c
            third_party/libvpx/source/libvpx/vpx_dsp/x86/sum_squares_sse2.c
            third_party/libvpx/source/libvpx/vpx_dsp/x86/variance_sse2.c
            third_party/libvpx/source/libvpx/vpx_dsp/x86/vpx_subpixel_4t_intrin_sse2.c
            )
endif ()

if (${ANDROID_ABI} STREQUAL "armeabi-v7a")
    file(GLOB src_tg_call_tp_asm
            third_party/libvpx/source/libvpx/vpx_dsp/arm/idct_neon.asm.S
            third_party/libvpx/source/libvpx/vpx_dsp/arm/idct4x4_1_add_neon.asm.S
            third_party/libvpx/source/libvpx/vpx_dsp/arm/idct4x4_add_neon.asm.S
            third_party/libvpx/source/libvpx/vpx_dsp/arm/intrapred_neon_asm.asm.S
            third_party/libvpx/source/libvpx/vpx_dsp/arm/loopfilter_4_neon.asm.S
            third_party/libvpx/source/libvpx/vpx_dsp/arm/loopfilter_8_neon.asm.S
            third_party/libvpx/source/libvpx/vpx_dsp/arm/loopfilter_16_neon.asm.S
            third_party/libvpx/source/libvpx/vpx_dsp/arm/save_reg_neon.asm.S
            third_party/libvpx/source/libvpx/vpx_dsp/arm/vpx_convolve_avg_neon_asm.asm.S
            third_party/libvpx/source/libvpx/vpx_dsp/arm/vpx_convolve_copy_neon_asm.asm.S
            third_party/libvpx/source/libvpx/vpx_dsp/arm/vpx_convolve8_avg_horiz_filter_type1_neon.asm.S
            third_party/libvpx/source/libvpx/vpx_dsp/arm/vpx_convolve8_avg_horiz_filter_type2_neon.asm.S
            third_party/libvpx/source/libvpx/vpx_dsp/arm/vpx_convolve8_avg_vert_filter_type1_neon.asm.S
            third_party/libvpx/source/libvpx/vpx_dsp/arm/vpx_convolve8_avg_vert_filter_type2_neon.asm.S
            third_party/libvpx/source/libvpx/vpx_dsp/arm/vpx_convolve8_horiz_filter_type1_neon.asm.S
            third_party/libvpx/source/libvpx/vpx_dsp/arm/vpx_convolve8_horiz_filter_type2_neon.asm.S
            third_party/libvpx/source/libvpx/vpx_dsp/arm/vpx_convolve8_vert_filter_type1_neon.asm.S
            third_party/libvpx/source/libvpx/vpx_dsp/arm/vpx_convolve8_vert_filter_type2_neon.asm.S
            )
elseif (${ANDROID_ABI} STREQUAL "arm64-v8a")

elseif (${ANDROID_ABI} STREQUAL "x86")
    file(GLOB src_tg_call_tp_asm
            third_party/libvpx/source/libvpx/vpx_ports/emms_mmx.c
            )
elseif (${ANDROID_ABI} STREQUAL "x86_64")

endif ()

add_library(tgcalls_tp STATIC
        ${src_tgcall_tp}
        ${src_tgcall_tp_neon}
        ${src_tgcall_tp_asm}
        )

target_link_libraries(tgcalls_tp ssl crypto cpufeatures)
#end tgcall_tp

#start tg_calls
unset(CMAKE_CXX_FLAGS)
unset(CMAKE_C_FLAGS)
set(CMAKE_CXX_FLAGS "-Wall -std=c++14 -DWEBRTC_NS_FLOAT -DANDROID -DHAVE_PTHREAD -finline-functions -ffast-math -Os -DRTC_ENABLE_VP9  -DHAVE_WEBRTC_VIDEO")
set(CMAKE_C_FLAGS "-std=c11 -DWEBRTC_NS_FLOAT -DRTC_ENABLE_VP9 -DHAVE_WEBRTC_VIDEO")

add_definitions(
        -DWEBRTC_POSIX
        -DHAVE_NETINET_IN_H
        -DWEBRTC_OPUS_VARIABLE_COMPLEXITY=0
        -DWEBRTC_ANDROID
        -DWEBRTC_LINUX
        -DWEBRTC_APM_DEBUG_DUMP=0
        -DWEBRTC_USE_BUILTIN_ISAC_FLOAT
        -DWEBRTC_INCLUDE_INTERNAL_AUDIO_DEVICE
)
if (${ANDROID_ABI} STREQUAL "armeabi-v7a")
    set(CMAKE_C_FLAGS "${CMAKE_C_FLAGS} -DWEBRTC_ARCH_ARM -DWEBRTC_ARCH_ARM_V7 -DWEBRTC_HAS_NEON")
    set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} -DWEBRTC_ARCH_ARM -DWEBRTC_ARCH_ARM_V7 -DWEBRTC_HAS_NEON")
elseif (${ANDROID_ABI} STREQUAL "arm64-v8a")
    set(CMAKE_C_FLAGS "${CMAKE_C_FLAGS} -DWEBRTC_ARCH_ARM64 -DWEBRTC_HAS_NEON")
    set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} -DWEBRTC_ARCH_ARM64 -DWEBRTC_HAS_NEON")
elseif (${ANDROID_ABI} STREQUAL "x86")
    set(CMAKE_C_FLAGS "${CMAKE_C_FLAGS} -DHAVE_SSE2")
elseif (${ANDROID_ABI} STREQUAL "x86_64")
    set(CMAKE_C_FLAGS "${CMAKE_C_FLAGS} -DHAVE_SSE2")
endif ()

include_directories(
        boringssl/include
        tgcalls
        webrtc
        opus/include
        opus/silk
        opus/silk/fixed
        opus/celt
        opus
        opus/opusfile
        third_party/libyuv/include
        third_party/libsrtp/include
        third_party/libsrtp/config
        third_party/libsrtp/crypto/include
        third_party/libvpx/source/libvpx
        third_party/libvpx/source/config
        third_party/usrsctplib
        third_party
        libtgvoip
)

if (${ANDROID_ABI} STREQUAL "armeabi-v7a")
    include_directories(third_party/libvpx/source/config/linux/arm-neon-cpu-detect)
elseif (${ANDROID_ABI} STREQUAL "arm64-v8a")
    include_directories(third_party/libvpx/source/config/linux/arm64-highbd)
elseif (${ANDROID_ABI} STREQUAL "x86")
    include_directories(third_party/libvpx/source/config/linux/ia32)
elseif (${ANDROID_ABI} STREQUAL "x86_64")
    include_directories(third_party/libvpx/source/config/linux/x64)
endif ()

file(GLOB tgcalls_src
        tgcalls/CodecSelectHelper.cpp
        tgcalls/CryptoHelper.cpp
        tgcalls/EncryptedConnection.cpp
        tgcalls/Instance.cpp
        tgcalls/InstanceImpl.cpp
        tgcalls/LogSinkImpl.cpp
        tgcalls/Manager.cpp
        tgcalls/MediaManager.cpp
        tgcalls/Message.cpp
        tgcalls/NetworkManager.cpp
        tgcalls/ThreadLocalObject.cpp
        tgcalls/VideoCaptureInterface.cpp
        tgcalls/VideoCaptureInterfaceImpl.cpp
        tgcalls/JsonConfig.cpp
        tgcalls/reference/InstanceImplReference.cpp
        tgcalls/legacy/InstanceImplLegacy.cpp
        tgcalls/platform/android/AndroidInterface.cpp
        tgcalls/platform/android/VideoCameraCapturer.cpp
        tgcalls/platform/android/AndroidContext.cpp
        tgcalls/platform/android/VideoCapturerInterfaceImpl.cpp
        )

file(GLOB webrtc_src
        webrtc/rtc_base/async_invoker.cc
        webrtc/rtc_base/async_packet_socket.cc
        webrtc/rtc_base/async_resolver_interface.cc
        webrtc/rtc_base/async_socket.cc
        webrtc/rtc_base/async_tcp_socket.cc
        webrtc/rtc_base/async_udp_socket.cc
        webrtc/rtc_base/bit_buffer.cc
        webrtc/rtc_base/buffer_queue.cc
        webrtc/rtc_base/byte_buffer.cc
        webrtc/rtc_base/checks.cc
        webrtc/rtc_base/copy_on_write_buffer.cc
        webrtc/rtc_base/crc32.cc
        webrtc/rtc_base/crypt_string.cc
        webrtc/rtc_base/data_rate_limiter.cc
        webrtc/rtc_base/event.cc
        webrtc/rtc_base/event_tracer.cc
        webrtc/rtc_base/experiments/alr_experiment.cc
        webrtc/rtc_base/experiments/balanced_degradation_settings.cc
        webrtc/rtc_base/experiments/cpu_speed_experiment.cc
        webrtc/rtc_base/experiments/field_trial_list.cc
        webrtc/rtc_base/experiments/field_trial_parser.cc
        webrtc/rtc_base/experiments/field_trial_units.cc
        webrtc/rtc_base/experiments/jitter_upper_bound_experiment.cc
        webrtc/rtc_base/experiments/keyframe_interval_settings.cc
        webrtc/rtc_base/experiments/min_video_bitrate_experiment.cc
        webrtc/rtc_base/experiments/normalize_simulcast_size_experiment.cc
        webrtc/rtc_base/experiments/quality_rampup_experiment.cc
        webrtc/rtc_base/experiments/quality_scaler_settings.cc
        webrtc/rtc_base/experiments/quality_scaling_experiment.cc
        webrtc/rtc_base/experiments/rate_control_settings.cc
        webrtc/rtc_base/experiments/rtt_mult_experiment.cc
        webrtc/rtc_base/experiments/stable_target_rate_experiment.cc
        webrtc/rtc_base/experiments/struct_parameters_parser.cc
        webrtc/rtc_base/file_rotating_stream.cc
        webrtc/rtc_base/helpers.cc
        webrtc/rtc_base/http_common.cc
        webrtc/rtc_base/ifaddrs_android.cc
        webrtc/rtc_base/ifaddrs_converter.cc
        webrtc/rtc_base/ip_address.cc
        webrtc/rtc_base/location.cc
        webrtc/rtc_base/log_sinks.cc
        webrtc/rtc_base/logging.cc
        webrtc/rtc_base/memory/aligned_malloc.cc
        webrtc/rtc_base/memory/fifo_buffer.cc
        webrtc/rtc_base/message_digest.cc
        webrtc/rtc_base/message_handler.cc
        webrtc/rtc_base/net_helper.cc
        webrtc/rtc_base/net_helpers.cc
        webrtc/rtc_base/network.cc
        webrtc/rtc_base/network/sent_packet.cc
        webrtc/rtc_base/network_constants.cc
        webrtc/rtc_base/network_monitor_factory.cc
        webrtc/rtc_base/network_monitor.cc
        webrtc/rtc_base/network_route.cc
        webrtc/rtc_base/null_socket_server.cc
        webrtc/rtc_base/numerics/event_based_exponential_moving_average.cc
        webrtc/rtc_base/numerics/event_rate_counter.cc
        webrtc/rtc_base/numerics/exp_filter.cc
        webrtc/rtc_base/numerics/histogram_percentile_counter.cc
        webrtc/rtc_base/numerics/moving_average.cc
        webrtc/rtc_base/numerics/sample_counter.cc
        webrtc/rtc_base/numerics/sample_stats.cc
        webrtc/rtc_base/numerics/samples_stats_counter.cc
        webrtc/rtc_base/openssl_adapter.cc
        webrtc/rtc_base/openssl_certificate.cc
        webrtc/rtc_base/openssl_digest.cc
        webrtc/rtc_base/openssl_identity.cc
        webrtc/rtc_base/openssl_session_cache.cc
        webrtc/rtc_base/openssl_stream_adapter.cc
        webrtc/rtc_base/openssl_utility.cc
        webrtc/rtc_base/operations_chain.cc
        webrtc/rtc_base/physical_socket_server.cc
        webrtc/rtc_base/platform_thread.cc
        webrtc/rtc_base/platform_thread_types.cc
        webrtc/rtc_base/proxy_info.cc
        webrtc/rtc_base/race_checker.cc
        webrtc/rtc_base/random.cc
        webrtc/rtc_base/rate_limiter.cc
        webrtc/rtc_base/rate_statistics.cc
        webrtc/rtc_base/rate_tracker.cc
        webrtc/rtc_base/rtc_certificate.cc
        webrtc/rtc_base/rtc_certificate_generator.cc
        webrtc/rtc_base/socket.cc
        webrtc/rtc_base/socket_adapters.cc
        webrtc/rtc_base/socket_address.cc
        webrtc/rtc_base/socket_address_pair.cc
        webrtc/rtc_base/ssl_adapter.cc
        webrtc/rtc_base/ssl_certificate.cc
        webrtc/rtc_base/ssl_fingerprint.cc
        webrtc/rtc_base/ssl_identity.cc
        webrtc/rtc_base/ssl_stream_adapter.cc
        webrtc/rtc_base/stream.cc
        webrtc/rtc_base/string_encode.cc
        webrtc/rtc_base/string_to_number.cc
        webrtc/rtc_base/string_utils.cc
        webrtc/rtc_base/strings/audio_format_to_string.cc
        webrtc/rtc_base/strings/string_builder.cc
        webrtc/rtc_base/strings/string_format.cc
        webrtc/rtc_base/synchronization/rw_lock_posix.cc
        webrtc/rtc_base/synchronization/rw_lock_wrapper.cc
        webrtc/rtc_base/synchronization/mutex.cc
        webrtc/rtc_base/synchronization/yield.cc
        webrtc/rtc_base/synchronization/sequence_checker.cc
        webrtc/rtc_base/synchronization/yield_policy.cc
        webrtc/rtc_base/system/file_wrapper.cc
        webrtc/rtc_base/system/thread_registry.cc
        webrtc/rtc_base/system/warn_current_thread_is_deadlocked.cc
        webrtc/rtc_base/task_queue.cc
        webrtc/rtc_base/task_queue_libevent.cc
        webrtc/rtc_base/task_queue_stdlib.cc
        webrtc/rtc_base/task_utils/pending_task_safety_flag.cc
        webrtc/rtc_base/task_utils/repeating_task.cc
        webrtc/rtc_base/third_party/base64/base64.cc
        webrtc/rtc_base/third_party/sigslot/sigslot.cc
        webrtc/rtc_base/thread.cc
        webrtc/rtc_base/time/timestamp_extrapolator.cc
        webrtc/rtc_base/time_utils.cc
        webrtc/rtc_base/timestamp_aligner.cc
        webrtc/rtc_base/unique_id_generator.cc
        webrtc/rtc_base/weak_ptr.cc
        webrtc/rtc_base/zero_memory.cc
        webrtc/rtc_base/deprecated/recursive_critical_section.cc
        webrtc/rtc_base/deprecated/signal_thread.cc
        webrtc/api/audio/audio_frame.cc
        webrtc/api/audio/channel_layout.cc
        webrtc/api/audio/echo_canceller3_config.cc
        webrtc/api/audio/echo_canceller3_factory.cc
        webrtc/api/audio_codecs/L16/audio_decoder_L16.cc
        webrtc/api/audio_codecs/L16/audio_encoder_L16.cc
        webrtc/api/audio_codecs/audio_codec_pair_id.cc
        webrtc/api/audio_codecs/audio_decoder.cc
        webrtc/api/audio_codecs/audio_encoder.cc
        webrtc/api/audio_codecs/audio_format.cc
        webrtc/api/audio_codecs/builtin_audio_decoder_factory.cc
        webrtc/api/audio_codecs/builtin_audio_encoder_factory.cc
        webrtc/api/audio_codecs/g711/audio_decoder_g711.cc
        webrtc/api/audio_codecs/g711/audio_encoder_g711.cc
        webrtc/api/audio_codecs/g722/audio_decoder_g722.cc
        webrtc/api/audio_codecs/g722/audio_encoder_g722.cc
        webrtc/api/audio_codecs/ilbc/audio_decoder_ilbc.cc
        webrtc/api/audio_codecs/ilbc/audio_encoder_ilbc.cc
        webrtc/api/audio_codecs/isac/audio_decoder_isac_fix.cc
        webrtc/api/audio_codecs/isac/audio_decoder_isac_float.cc
        webrtc/api/audio_codecs/isac/audio_encoder_isac_fix.cc
        webrtc/api/audio_codecs/isac/audio_encoder_isac_float.cc
        webrtc/api/audio_codecs/opus/audio_decoder_multi_channel_opus.cc
        webrtc/api/audio_codecs/opus/audio_decoder_opus.cc
        webrtc/api/audio_codecs/opus/audio_encoder_multi_channel_opus.cc
        webrtc/api/audio_codecs/opus/audio_encoder_multi_channel_opus_config.cc
        webrtc/api/audio_codecs/opus/audio_encoder_opus.cc
        webrtc/api/audio_codecs/opus/audio_encoder_opus_config.cc
        webrtc/api/audio_codecs/opus_audio_decoder_factory.cc
        webrtc/api/audio_codecs/opus_audio_encoder_factory.cc
        webrtc/api/audio_options.cc
        webrtc/api/call/transport.cc
        webrtc/api/candidate.cc
        webrtc/api/create_peerconnection_factory.cc
        webrtc/api/crypto/crypto_options.cc
        webrtc/api/data_channel_interface.cc
        webrtc/api/dtls_transport_interface.cc
        webrtc/api/ice_transport_factory.cc
        webrtc/api/jsep.cc
        webrtc/api/jsep_ice_candidate.cc
        webrtc/api/media_stream_interface.cc
        webrtc/api/media_types.cc
        webrtc/api/neteq/custom_neteq_factory.cc
        webrtc/api/neteq/default_neteq_controller_factory.cc
        webrtc/api/neteq/neteq.cc
        webrtc/api/neteq/tick_timer.cc
        webrtc/api/peer_connection_interface.cc
        webrtc/api/proxy.cc
        webrtc/api/rtc_error.cc
        webrtc/api/rtc_event_log/rtc_event.cc
        webrtc/api/rtc_event_log/rtc_event_log.cc
        webrtc/api/rtc_event_log/rtc_event_log_factory.cc
        webrtc/api/rtc_event_log_output_file.cc
        webrtc/api/rtp_headers.cc
        webrtc/api/rtp_packet_info.cc
        webrtc/api/rtp_parameters.cc
        webrtc/api/rtp_receiver_interface.cc
        webrtc/api/rtp_sender_interface.cc
        webrtc/api/rtp_transceiver_interface.cc
        webrtc/api/sctp_transport_interface.cc
        webrtc/api/stats_types.cc
        webrtc/api/task_queue/default_task_queue_factory_libevent.cc
        webrtc/api/task_queue/task_queue_base.cc
        webrtc/api/transport/bitrate_settings.cc
        webrtc/api/transport/field_trial_based_config.cc
        webrtc/api/transport/goog_cc_factory.cc
        webrtc/api/transport/network_types.cc
        webrtc/api/transport/stun.cc
        webrtc/api/transport/rtp/dependency_descriptor.cc
        webrtc/api/video/video_adaptation_counters.cc
        webrtc/api/video/video_frame_metadata.cc
        webrtc/api/voip/voip_engine_factory.cc
        webrtc/call/adaptation/adaptation_constraint.cc
        webrtc/call/adaptation/broadcast_resource_listener.cc
        webrtc/call/adaptation/degradation_preference_provider.cc
        webrtc/call/adaptation/resource_adaptation_processor.cc
        webrtc/call/adaptation/video_stream_adapter.cc
        webrtc/call/adaptation/video_stream_input_state.cc
        webrtc/call/adaptation/video_stream_input_state_provider.cc
        webrtc/call/adaptation/encoder_settings.cc
        webrtc/modules/rtp_rtcp/source/rtp_rtcp_impl2.cc
        webrtc/audio/voip/audio_channel.cc
        webrtc/audio/voip/audio_ingress.cc
        webrtc/audio/voip/voip_core.cc
        webrtc/api/adaptation/resource.cc
        webrtc/api/units/data_rate.cc
        webrtc/api/units/data_size.cc
        webrtc/api/units/frequency.cc
        webrtc/api/units/time_delta.cc
        webrtc/api/units/timestamp.cc
        webrtc/api/video/builtin_video_bitrate_allocator_factory.cc
        webrtc/api/video/color_space.cc
        webrtc/api/video/encoded_frame.cc
        webrtc/api/video/encoded_image.cc
        webrtc/api/video/hdr_metadata.cc
        webrtc/api/video/i010_buffer.cc
        webrtc/api/video/i420_buffer.cc
        webrtc/api/video/video_bitrate_allocation.cc
        webrtc/api/video/video_bitrate_allocator.cc
        webrtc/api/video/video_content_type.cc
        webrtc/api/video/video_frame.cc
        webrtc/api/video/video_frame_buffer.cc
        webrtc/api/video/video_source_interface.cc
        webrtc/api/video/video_stream_decoder_create.cc
        webrtc/api/video/video_stream_encoder_create.cc
        webrtc/api/video/video_timing.cc
        webrtc/api/video_codecs/builtin_video_decoder_factory.cc
        webrtc/api/video_codecs/builtin_video_encoder_factory.cc
        webrtc/api/video_codecs/sdp_video_format.cc
        webrtc/api/video_codecs/video_codec.cc
        webrtc/api/video_codecs/video_decoder.cc
        webrtc/api/video_codecs/video_decoder_factory.cc
        webrtc/api/video_codecs/video_decoder_software_fallback_wrapper.cc
        webrtc/api/video_codecs/video_encoder.cc
        webrtc/api/video_codecs/video_encoder_config.cc
        webrtc/api/video_codecs/video_encoder_software_fallback_wrapper.cc
        webrtc/api/video_codecs/vp8_frame_config.cc
        webrtc/api/video_codecs/vp8_temporal_layers.cc
        webrtc/api/video_codecs/vp8_temporal_layers_factory.cc
        webrtc/pc/audio_rtp_receiver.cc
        webrtc/pc/audio_track.cc
        webrtc/pc/channel.cc
        webrtc/pc/channel_manager.cc
        webrtc/pc/composite_rtp_transport.cc
        webrtc/pc/data_channel_controller.cc
        webrtc/pc/data_channel_utils.cc
        webrtc/pc/dtls_srtp_transport.cc
        webrtc/pc/dtls_transport.cc
        webrtc/pc/dtmf_sender.cc
        webrtc/pc/external_hmac.cc
        webrtc/pc/ice_server_parsing.cc
        webrtc/pc/ice_transport.cc
        webrtc/pc/jitter_buffer_delay.cc
        webrtc/pc/jsep_ice_candidate.cc
        webrtc/pc/jsep_session_description.cc
        webrtc/pc/jsep_transport.cc
        webrtc/pc/jsep_transport_controller.cc
        webrtc/pc/local_audio_source.cc
        webrtc/pc/media_protocol_names.cc
        webrtc/pc/media_session.cc
        webrtc/pc/media_stream.cc
        webrtc/pc/media_stream_observer.cc
        webrtc/pc/peer_connection.cc
        webrtc/pc/peer_connection_factory.cc
        webrtc/pc/remote_audio_source.cc
        webrtc/pc/rtc_stats_collector.cc
        webrtc/pc/rtc_stats_traversal.cc
        webrtc/pc/rtcp_mux_filter.cc
        webrtc/pc/rtp_media_utils.cc
        webrtc/pc/rtp_parameters_conversion.cc
        webrtc/pc/rtp_receiver.cc
        webrtc/pc/rtp_data_channel.cc
        webrtc/pc/rtp_sender.cc
        webrtc/pc/rtp_transceiver.cc
        webrtc/pc/rtp_transport.cc
        webrtc/pc/sctp_data_channel_transport.cc
        webrtc/pc/sctp_transport.cc
        webrtc/pc/sctp_utils.cc
        webrtc/pc/sctp_data_channel.cc
        webrtc/pc/sdp_serializer.cc
        webrtc/pc/sdp_utils.cc
        webrtc/pc/session_description.cc
        webrtc/pc/simulcast_description.cc
        webrtc/pc/srtp_filter.cc
        webrtc/pc/srtp_session.cc
        webrtc/pc/srtp_transport.cc
        webrtc/pc/stats_collector.cc
        webrtc/pc/track_media_info_map.cc
        webrtc/pc/transport_stats.cc
        webrtc/pc/video_rtp_receiver.cc
        webrtc/pc/video_rtp_track_source.cc
        webrtc/pc/video_track.cc
        webrtc/pc/video_track_source.cc
        webrtc/pc/webrtc_sdp.cc
        webrtc/pc/webrtc_session_description_factory.cc
        webrtc/media/base/adapted_video_track_source.cc
        webrtc/media/base/codec.cc
        webrtc/media/base/h264_profile_level_id.cc
        webrtc/media/base/media_channel.cc
        webrtc/media/base/media_constants.cc
        webrtc/media/base/media_engine.cc
        webrtc/media/base/rid_description.cc
        webrtc/media/base/rtp_data_engine.cc
        webrtc/media/base/rtp_utils.cc
        webrtc/media/base/sdp_fmtp_utils.cc
        webrtc/media/base/stream_params.cc
        webrtc/media/base/turn_utils.cc
        webrtc/media/base/video_adapter.cc
        webrtc/media/base/video_broadcaster.cc
        webrtc/media/base/video_common.cc
        webrtc/media/base/video_source_base.cc
        webrtc/media/base/vp9_profile.cc
        webrtc/media/engine/adm_helpers.cc
        webrtc/media/engine/constants.cc
        webrtc/media/engine/encoder_simulcast_proxy.cc
        webrtc/media/engine/internal_decoder_factory.cc
        webrtc/media/engine/internal_encoder_factory.cc
        webrtc/media/engine/multiplex_codec_factory.cc
        webrtc/media/engine/payload_type_mapper.cc
        webrtc/media/engine/simulcast.cc
        webrtc/media/engine/simulcast_encoder_adapter.cc
        webrtc/media/engine/unhandled_packets_buffer.cc
        webrtc/media/engine/webrtc_media_engine.cc
        webrtc/media/engine/webrtc_media_engine_defaults.cc
        webrtc/media/engine/webrtc_video_engine.cc
        webrtc/media/engine/webrtc_voice_engine.cc
        webrtc/media/sctp/noop.cc
        webrtc/media/sctp/sctp_transport.cc
        webrtc/system_wrappers/source/clock.cc
        webrtc/system_wrappers/source/cpu_features.cc
        webrtc/system_wrappers/source/cpu_info.cc
        webrtc/system_wrappers/source/field_trial.cc
        webrtc/system_wrappers/source/metrics.cc
        webrtc/system_wrappers/source/rtp_to_ntp_estimator.cc
        webrtc/system_wrappers/source/sleep.cc
        webrtc/modules/audio_coding/acm2/acm_receiver.cc
        webrtc/modules/audio_coding/acm2/acm_remixing.cc
        webrtc/modules/audio_coding/acm2/acm_resampler.cc
        webrtc/modules/audio_coding/acm2/audio_coding_module.cc
        webrtc/modules/audio_coding/acm2/call_statistics.cc
        webrtc/modules/audio_coding/audio_network_adaptor/audio_network_adaptor_config.cc
        webrtc/modules/audio_coding/audio_network_adaptor/audio_network_adaptor_impl.cc
        webrtc/modules/audio_coding/audio_network_adaptor/bitrate_controller.cc
        webrtc/modules/audio_coding/audio_network_adaptor/channel_controller.cc
        webrtc/modules/audio_coding/audio_network_adaptor/controller.cc
        webrtc/modules/audio_coding/audio_network_adaptor/controller_manager.cc
        webrtc/modules/audio_coding/audio_network_adaptor/debug_dump_writer.cc
        webrtc/modules/audio_coding/audio_network_adaptor/dtx_controller.cc
        webrtc/modules/audio_coding/audio_network_adaptor/event_log_writer.cc
        webrtc/modules/audio_coding/audio_network_adaptor/fec_controller_plr_based.cc
        webrtc/modules/audio_coding/audio_network_adaptor/frame_length_controller.cc
        webrtc/modules/audio_coding/codecs/cng/audio_encoder_cng.cc
        webrtc/modules/audio_coding/codecs/cng/webrtc_cng.cc
        webrtc/modules/audio_coding/codecs/g711/g711_interface.c
        webrtc/modules/audio_coding/codecs/g711/audio_decoder_pcm.cc
        webrtc/modules/audio_coding/codecs/g711/audio_encoder_pcm.cc
        webrtc/modules/audio_coding/codecs/g722/audio_decoder_g722.cc
        webrtc/modules/audio_coding/codecs/g722/audio_encoder_g722.cc
        webrtc/modules/audio_coding/codecs/g722/g722_interface.c
        webrtc/modules/audio_coding/codecs/pcm16b/pcm16b.c
        webrtc/modules/third_party/g711/g711.c
        webrtc/modules/third_party/g722/g722_decode.c
        webrtc/modules/third_party/g722/g722_encode.c
        webrtc/modules/third_party/fft/fft.c
        webrtc/modules/audio_coding/codecs/ilbc/audio_decoder_ilbc.cc
        webrtc/modules/audio_coding/codecs/ilbc/audio_encoder_ilbc.cc
        webrtc/modules/audio_coding/codecs/isac/empty.cc
        webrtc/modules/audio_coding/codecs/isac/fix/source/audio_decoder_isacfix.cc
        webrtc/modules/audio_coding/codecs/isac/fix/source/audio_encoder_isacfix.cc
        webrtc/modules/audio_coding/codecs/isac/fix/source/isacfix.c
        webrtc/modules/audio_coding/codecs/isac/fix/source/arith_routines.c
        webrtc/modules/audio_coding/codecs/isac/fix/source/arith_routines_hist.c
        webrtc/modules/audio_coding/codecs/isac/fix/source/arith_routines_logist.c
        webrtc/modules/audio_coding/codecs/isac/fix/source/bandwidth_estimator.c
        webrtc/modules/audio_coding/codecs/isac/fix/source/decode.c
        webrtc/modules/audio_coding/codecs/isac/fix/source/decode_bwe.c
        webrtc/modules/audio_coding/codecs/isac/fix/source/decode_plc.c
        webrtc/modules/audio_coding/codecs/isac/fix/source/encode.c
        webrtc/modules/audio_coding/codecs/isac/fix/source/entropy_coding.c
        webrtc/modules/audio_coding/codecs/isac/fix/source/fft.c
        webrtc/modules/audio_coding/codecs/isac/fix/source/filterbank_tables.c
        webrtc/modules/audio_coding/codecs/isac/fix/source/filterbanks.c
        webrtc/modules/audio_coding/codecs/isac/fix/source/filters.c
        webrtc/modules/audio_coding/codecs/isac/fix/source/initialize.c
        webrtc/modules/audio_coding/codecs/isac/fix/source/lattice.c
        webrtc/modules/audio_coding/codecs/isac/fix/source/lattice_c.c
        webrtc/modules/audio_coding/codecs/isac/fix/source/lpc_masking_model.c
        webrtc/modules/audio_coding/codecs/isac/fix/source/lpc_tables.c
        webrtc/modules/audio_coding/codecs/isac/fix/source/pitch_estimator.c
        webrtc/modules/audio_coding/codecs/isac/fix/source/pitch_estimator_c.c
        webrtc/modules/audio_coding/codecs/isac/fix/source/pitch_filter.c
        webrtc/modules/audio_coding/codecs/isac/fix/source/pitch_filter_c.c
        webrtc/modules/audio_coding/codecs/isac/fix/source/pitch_gain_tables.c
        webrtc/modules/audio_coding/codecs/isac/fix/source/pitch_lag_tables.c
        webrtc/modules/audio_coding/codecs/isac/fix/source/spectrum_ar_model_tables.c
        webrtc/modules/audio_coding/codecs/isac/fix/source/transform.c
        webrtc/modules/audio_coding/codecs/isac/fix/source/transform_tables.c
        webrtc/modules/audio_coding/codecs/isac/main/source/audio_decoder_isac.cc
        webrtc/modules/audio_coding/codecs/isac/main/source/audio_encoder_isac.cc
        webrtc/modules/audio_coding/codecs/isac/main/source/arith_routines.c
        webrtc/modules/audio_coding/codecs/isac/main/source/arith_routines_hist.c
        webrtc/modules/audio_coding/codecs/isac/main/source/arith_routines_logist.c
        webrtc/modules/audio_coding/codecs/isac/main/source/bandwidth_estimator.c
        webrtc/modules/audio_coding/codecs/isac/main/source/crc.c
        webrtc/modules/audio_coding/codecs/isac/main/source/decode.c
        webrtc/modules/audio_coding/codecs/isac/main/source/decode_bwe.c
        webrtc/modules/audio_coding/codecs/isac/main/source/encode.c
        webrtc/modules/audio_coding/codecs/isac/main/source/encode_lpc_swb.c
        webrtc/modules/audio_coding/codecs/isac/main/source/entropy_coding.c
        webrtc/modules/audio_coding/codecs/isac/main/source/filter_functions.c
        webrtc/modules/audio_coding/codecs/isac/main/source/filterbanks.c
        webrtc/modules/audio_coding/codecs/isac/main/source/intialize.c
        webrtc/modules/audio_coding/codecs/isac/main/source/isac.c
        webrtc/modules/audio_coding/codecs/isac/main/source/isac_vad.c
        webrtc/modules/audio_coding/codecs/isac/main/source/lattice.c
        webrtc/modules/audio_coding/codecs/isac/main/source/lpc_analysis.c
        webrtc/modules/audio_coding/codecs/isac/main/source/lpc_gain_swb_tables.c
        webrtc/modules/audio_coding/codecs/isac/main/source/lpc_shape_swb12_tables.c
        webrtc/modules/audio_coding/codecs/isac/main/source/lpc_shape_swb16_tables.c
        webrtc/modules/audio_coding/codecs/isac/main/source/lpc_tables.c
        webrtc/modules/audio_coding/codecs/isac/main/source/pitch_estimator.c
        webrtc/modules/audio_coding/codecs/isac/main/source/pitch_filter.c
        webrtc/modules/audio_coding/codecs/isac/main/source/pitch_gain_tables.c
        webrtc/modules/audio_coding/codecs/isac/main/source/pitch_lag_tables.c
        webrtc/modules/audio_coding/codecs/isac/main/source/spectrum_ar_model_tables.c
        webrtc/modules/audio_coding/codecs/isac/main/source/transform.c
        webrtc/modules/audio_coding/codecs/isac/main/util/utility.c
        webrtc/modules/audio_coding/codecs/legacy_encoded_audio_frame.cc
        webrtc/modules/audio_coding/codecs/opus/audio_coder_opus_common.cc
        webrtc/modules/audio_coding/codecs/opus/audio_decoder_multi_channel_opus_impl.cc
        webrtc/modules/audio_coding/codecs/opus/audio_decoder_opus.cc
        webrtc/modules/audio_coding/codecs/opus/audio_encoder_multi_channel_opus_impl.cc
        webrtc/modules/audio_coding/codecs/opus/audio_encoder_opus.cc
        webrtc/modules/audio_coding/codecs/opus/opus_interface.cc
        webrtc/modules/audio_coding/codecs/opus/test/audio_ring_buffer.cc
        webrtc/modules/audio_coding/codecs/opus/test/blocker.cc
        webrtc/modules/audio_coding/codecs/opus/test/lapped_transform.cc
        webrtc/modules/audio_coding/codecs/pcm16b/audio_decoder_pcm16b.cc
        webrtc/modules/audio_coding/codecs/pcm16b/audio_encoder_pcm16b.cc
        webrtc/modules/audio_coding/codecs/pcm16b/pcm16b_common.cc
        webrtc/modules/audio_coding/codecs/red/audio_encoder_copy_red.cc
        webrtc/modules/audio_coding/codecs/ilbc/abs_quant.c
        webrtc/modules/audio_coding/codecs/ilbc/abs_quant_loop.c
        webrtc/modules/audio_coding/codecs/ilbc/augmented_cb_corr.c
        webrtc/modules/audio_coding/codecs/ilbc/bw_expand.c
        webrtc/modules/audio_coding/codecs/ilbc/cb_construct.c
        webrtc/modules/audio_coding/codecs/ilbc/cb_mem_energy.c
        webrtc/modules/audio_coding/codecs/ilbc/cb_mem_energy_augmentation.c
        webrtc/modules/audio_coding/codecs/ilbc/cb_mem_energy_calc.c
        webrtc/modules/audio_coding/codecs/ilbc/cb_search.c
        webrtc/modules/audio_coding/codecs/ilbc/cb_search_core.c
        webrtc/modules/audio_coding/codecs/ilbc/cb_update_best_index.c
        webrtc/modules/audio_coding/codecs/ilbc/chebyshev.c
        webrtc/modules/audio_coding/codecs/ilbc/comp_corr.c
        webrtc/modules/audio_coding/codecs/ilbc/constants.c
        webrtc/modules/audio_coding/codecs/ilbc/create_augmented_vec.c
        webrtc/modules/audio_coding/codecs/ilbc/decode.c
        webrtc/modules/audio_coding/codecs/ilbc/decode_residual.c
        webrtc/modules/audio_coding/codecs/ilbc/decoder_interpolate_lsf.c
        webrtc/modules/audio_coding/codecs/ilbc/do_plc.c
        webrtc/modules/audio_coding/codecs/ilbc/encode.c
        webrtc/modules/audio_coding/codecs/ilbc/energy_inverse.c
        webrtc/modules/audio_coding/codecs/ilbc/enh_upsample.c
        webrtc/modules/audio_coding/codecs/ilbc/enhancer.c
        webrtc/modules/audio_coding/codecs/ilbc/enhancer_interface.c
        webrtc/modules/audio_coding/codecs/ilbc/filtered_cb_vecs.c
        webrtc/modules/audio_coding/codecs/ilbc/frame_classify.c
        webrtc/modules/audio_coding/codecs/ilbc/gain_dequant.c
        webrtc/modules/audio_coding/codecs/ilbc/gain_quant.c
        webrtc/modules/audio_coding/codecs/ilbc/get_cd_vec.c
        webrtc/modules/audio_coding/codecs/ilbc/get_lsp_poly.c
        webrtc/modules/audio_coding/codecs/ilbc/get_sync_seq.c
        webrtc/modules/audio_coding/codecs/ilbc/hp_input.c
        webrtc/modules/audio_coding/codecs/ilbc/hp_output.c
        webrtc/modules/audio_coding/codecs/ilbc/ilbc.c
        webrtc/modules/audio_coding/codecs/ilbc/index_conv_dec.c
        webrtc/modules/audio_coding/codecs/ilbc/index_conv_enc.c
        webrtc/modules/audio_coding/codecs/ilbc/init_decode.c
        webrtc/modules/audio_coding/codecs/ilbc/init_encode.c
        webrtc/modules/audio_coding/codecs/ilbc/interpolate.c
        webrtc/modules/audio_coding/codecs/ilbc/interpolate_samples.c
        webrtc/modules/audio_coding/codecs/ilbc/lpc_encode.c
        webrtc/modules/audio_coding/codecs/ilbc/lsf_check.c
        webrtc/modules/audio_coding/codecs/ilbc/lsf_interpolate_to_poly_dec.c
        webrtc/modules/audio_coding/codecs/ilbc/lsf_interpolate_to_poly_enc.c
        webrtc/modules/audio_coding/codecs/ilbc/lsf_to_lsp.c
        webrtc/modules/audio_coding/codecs/ilbc/lsf_to_poly.c
        webrtc/modules/audio_coding/codecs/ilbc/lsp_to_lsf.c
        webrtc/modules/audio_coding/codecs/ilbc/my_corr.c
        webrtc/modules/audio_coding/codecs/ilbc/nearest_neighbor.c
        webrtc/modules/audio_coding/codecs/ilbc/pack_bits.c
        webrtc/modules/audio_coding/codecs/ilbc/poly_to_lsf.c
        webrtc/modules/audio_coding/codecs/ilbc/poly_to_lsp.c
        webrtc/modules/audio_coding/codecs/ilbc/refiner.c
        webrtc/modules/audio_coding/codecs/ilbc/simple_interpolate_lsf.c
        webrtc/modules/audio_coding/codecs/ilbc/simple_lpc_analysis.c
        webrtc/modules/audio_coding/codecs/ilbc/simple_lsf_dequant.c
        webrtc/modules/audio_coding/codecs/ilbc/simple_lsf_quant.c
        webrtc/modules/audio_coding/codecs/ilbc/smooth.c
        webrtc/modules/audio_coding/codecs/ilbc/smooth_out_data.c
        webrtc/modules/audio_coding/codecs/ilbc/sort_sq.c
        webrtc/modules/audio_coding/codecs/ilbc/split_vq.c
        webrtc/modules/audio_coding/codecs/ilbc/state_construct.c
        webrtc/modules/audio_coding/codecs/ilbc/state_search.c
        webrtc/modules/audio_coding/codecs/ilbc/swap_bytes.c
        webrtc/modules/audio_coding/codecs/ilbc/unpack_bits.c
        webrtc/modules/audio_coding/codecs/ilbc/vq3.c
        webrtc/modules/audio_coding/codecs/ilbc/vq4.c
        webrtc/modules/audio_coding/codecs/ilbc/window32_w32.c
        webrtc/modules/audio_coding/codecs/ilbc/xcorr_coef.c
        webrtc/modules/audio_coding/neteq/accelerate.cc
        webrtc/modules/audio_coding/neteq/audio_multi_vector.cc
        webrtc/modules/audio_coding/neteq/audio_vector.cc
        webrtc/modules/audio_coding/neteq/background_noise.cc
        webrtc/modules/audio_coding/neteq/buffer_level_filter.cc
        webrtc/modules/audio_coding/neteq/comfort_noise.cc
        webrtc/modules/audio_coding/neteq/cross_correlation.cc
        webrtc/modules/audio_coding/neteq/decision_logic.cc
        webrtc/modules/audio_coding/neteq/decoder_database.cc
        webrtc/modules/audio_coding/neteq/default_neteq_factory.cc
        webrtc/modules/audio_coding/neteq/delay_manager.cc
        webrtc/modules/audio_coding/neteq/dsp_helper.cc
        webrtc/modules/audio_coding/neteq/dtmf_buffer.cc
        webrtc/modules/audio_coding/neteq/dtmf_tone_generator.cc
        webrtc/modules/audio_coding/neteq/expand.cc
        webrtc/modules/audio_coding/neteq/expand_uma_logger.cc
        webrtc/modules/audio_coding/neteq/histogram.cc
        webrtc/modules/audio_coding/neteq/merge.cc
        webrtc/modules/audio_coding/neteq/nack_tracker.cc
        webrtc/modules/audio_coding/neteq/neteq_impl.cc
        webrtc/modules/audio_coding/neteq/normal.cc
        webrtc/modules/audio_coding/neteq/packet.cc
        webrtc/modules/audio_coding/neteq/packet_buffer.cc
        webrtc/modules/audio_coding/neteq/post_decode_vad.cc
        webrtc/modules/audio_coding/neteq/preemptive_expand.cc
        webrtc/modules/audio_coding/neteq/random_vector.cc
        webrtc/modules/audio_coding/neteq/red_payload_splitter.cc
        webrtc/modules/audio_coding/neteq/statistics_calculator.cc
        webrtc/modules/audio_coding/neteq/sync_buffer.cc
        webrtc/modules/audio_coding/neteq/time_stretch.cc
        webrtc/modules/audio_coding/neteq/timestamp_scaler.cc
        webrtc/modules/audio_device/android/audio_manager.cc
        webrtc/modules/audio_device/android/audio_record_jni.cc
        webrtc/modules/audio_device/android/audio_track_jni.cc
        webrtc/modules/audio_device/android/build_info.cc
        webrtc/modules/audio_device/android/opensles_common.cc
        webrtc/modules/audio_device/android/opensles_player.cc
        webrtc/modules/audio_device/android/opensles_recorder.cc
        webrtc/modules/audio_device/audio_device_buffer.cc
        webrtc/modules/audio_device/audio_device_data_observer.cc
        webrtc/modules/audio_device/audio_device_generic.cc
        webrtc/modules/audio_device/audio_device_impl.cc
        webrtc/modules/audio_device/audio_device_name.cc
        webrtc/modules/audio_device/dummy/audio_device_dummy.cc
        webrtc/modules/audio_device/dummy/file_audio_device.cc
        webrtc/modules/audio_device/dummy/file_audio_device_factory.cc
        webrtc/modules/audio_device/fine_audio_buffer.cc
        webrtc/modules/audio_device/include/test_audio_device.cc
        webrtc/modules/audio_mixer/audio_frame_manipulator.cc
        webrtc/modules/audio_mixer/audio_mixer_impl.cc
        webrtc/modules/audio_mixer/default_output_rate_calculator.cc
        webrtc/modules/audio_mixer/frame_combiner.cc
        webrtc/modules/audio_mixer/gain_change_calculator.cc
        webrtc/modules/audio_mixer/sine_wave_generator.cc
        webrtc/modules/audio_processing/aec3/adaptive_fir_filter.cc
        webrtc/modules/audio_processing/aec3/adaptive_fir_filter_erl.cc
        webrtc/modules/audio_processing/aec3/aec3_common.cc
        webrtc/modules/audio_processing/aec3/aec3_fft.cc
        webrtc/modules/audio_processing/aec3/aec_state.cc
        webrtc/modules/audio_processing/aec3/alignment_mixer.cc
        webrtc/modules/audio_processing/aec3/api_call_jitter_metrics.cc
        webrtc/modules/audio_processing/aec3/block_buffer.cc
        webrtc/modules/audio_processing/aec3/block_delay_buffer.cc
        webrtc/modules/audio_processing/aec3/block_framer.cc
        webrtc/modules/audio_processing/aec3/block_processor.cc
        webrtc/modules/audio_processing/aec3/block_processor_metrics.cc
        webrtc/modules/audio_processing/aec3/clockdrift_detector.cc
        webrtc/modules/audio_processing/aec3/coarse_filter_update_gain.cc
        webrtc/modules/audio_processing/aec3/comfort_noise_generator.cc
        webrtc/modules/audio_processing/aec3/decimator.cc
        webrtc/modules/audio_processing/aec3/dominant_nearend_detector.cc
        webrtc/modules/audio_processing/aec3/downsampled_render_buffer.cc
        webrtc/modules/audio_processing/aec3/echo_audibility.cc
        webrtc/modules/audio_processing/aec3/echo_canceller3.cc
        webrtc/modules/audio_processing/aec3/echo_path_delay_estimator.cc
        webrtc/modules/audio_processing/aec3/echo_path_variability.cc
        webrtc/modules/audio_processing/aec3/echo_remover.cc
        webrtc/modules/audio_processing/aec3/echo_remover_metrics.cc
        webrtc/modules/audio_processing/aec3/erl_estimator.cc
        webrtc/modules/audio_processing/aec3/erle_estimator.cc
        webrtc/modules/audio_processing/aec3/fft_buffer.cc
        webrtc/modules/audio_processing/aec3/filter_analyzer.cc
        webrtc/modules/audio_processing/aec3/frame_blocker.cc
        webrtc/modules/audio_processing/aec3/fullband_erle_estimator.cc
        webrtc/modules/audio_processing/aec3/matched_filter.cc
        webrtc/modules/audio_processing/aec3/matched_filter_lag_aggregator.cc
        webrtc/modules/audio_processing/aec3/moving_average.cc
        webrtc/modules/audio_processing/aec3/refined_filter_update_gain.cc
        webrtc/modules/audio_processing/aec3/render_buffer.cc
        webrtc/modules/audio_processing/aec3/render_delay_buffer.cc
        webrtc/modules/audio_processing/aec3/render_delay_controller.cc
        webrtc/modules/audio_processing/aec3/render_delay_controller_metrics.cc
        webrtc/modules/audio_processing/aec3/render_signal_analyzer.cc
        webrtc/modules/audio_processing/aec3/residual_echo_estimator.cc
        webrtc/modules/audio_processing/aec3/reverb_decay_estimator.cc
        webrtc/modules/audio_processing/aec3/reverb_frequency_response.cc
        webrtc/modules/audio_processing/aec3/reverb_model.cc
        webrtc/modules/audio_processing/aec3/reverb_model_estimator.cc
        webrtc/modules/audio_processing/aec3/signal_dependent_erle_estimator.cc
        webrtc/modules/audio_processing/aec3/spectrum_buffer.cc
        webrtc/modules/audio_processing/aec3/stationarity_estimator.cc
        webrtc/modules/audio_processing/aec3/subband_erle_estimator.cc
        webrtc/modules/audio_processing/aec3/subband_nearend_detector.cc
        webrtc/modules/audio_processing/aec3/subtractor.cc
        webrtc/modules/audio_processing/aec3/subtractor_output.cc
        webrtc/modules/audio_processing/aec3/subtractor_output_analyzer.cc
        webrtc/modules/audio_processing/aec3/suppression_filter.cc
        webrtc/modules/audio_processing/aec3/suppression_gain.cc
        webrtc/modules/audio_processing/aec_dump/null_aec_dump_factory.cc
        webrtc/modules/audio_processing/aecm/aecm_core.cc
        webrtc/modules/audio_processing/aecm/aecm_core_c.cc
        webrtc/modules/audio_processing/aecm/echo_control_mobile.cc
        webrtc/modules/audio_processing/agc/agc.cc
        webrtc/modules/audio_processing/agc/agc_manager_direct.cc
        webrtc/modules/audio_processing/agc/legacy/analog_agc.cc
        webrtc/modules/audio_processing/agc/legacy/digital_agc.cc
        webrtc/modules/audio_processing/agc/loudness_histogram.cc
        webrtc/modules/audio_processing/agc/utility.cc
        webrtc/modules/audio_processing/agc2/adaptive_agc.cc
        webrtc/modules/audio_processing/agc2/adaptive_digital_gain_applier.cc
        webrtc/modules/audio_processing/agc2/adaptive_mode_level_estimator.cc
        webrtc/modules/audio_processing/agc2/adaptive_mode_level_estimator_agc.cc
        webrtc/modules/audio_processing/agc2/agc2_common.cc
        webrtc/modules/audio_processing/agc2/agc2_testing_common.cc
        webrtc/modules/audio_processing/agc2/biquad_filter.cc
        webrtc/modules/audio_processing/agc2/compute_interpolated_gain_curve.cc
        webrtc/modules/audio_processing/agc2/down_sampler.cc
        webrtc/modules/audio_processing/agc2/fixed_digital_level_estimator.cc
        webrtc/modules/audio_processing/agc2/gain_applier.cc
        webrtc/modules/audio_processing/agc2/interpolated_gain_curve.cc
        webrtc/modules/audio_processing/agc2/limiter.cc
        webrtc/modules/audio_processing/agc2/limiter_db_gain_curve.cc
        webrtc/modules/audio_processing/agc2/noise_level_estimator.cc
        webrtc/modules/audio_processing/agc2/noise_spectrum_estimator.cc
        webrtc/modules/audio_processing/agc2/saturation_protector.cc
        webrtc/modules/audio_processing/agc2/signal_classifier.cc
        webrtc/modules/audio_processing/agc2/vad_with_level.cc
        webrtc/modules/audio_processing/agc2/vector_float_frame.cc
        webrtc/modules/audio_processing/agc2/rnn_vad/auto_correlation.cc
        webrtc/modules/audio_processing/agc2/rnn_vad/common.cc
        webrtc/modules/audio_processing/agc2/rnn_vad/features_extraction.cc
        webrtc/modules/audio_processing/agc2/rnn_vad/lp_residual.cc
        webrtc/modules/audio_processing/agc2/rnn_vad/pitch_search.cc
        webrtc/modules/audio_processing/agc2/rnn_vad/pitch_search_internal.cc
        webrtc/modules/audio_processing/agc2/rnn_vad/rnn.cc
        webrtc/modules/audio_processing/agc2/rnn_vad/spectral_features.cc
        webrtc/modules/audio_processing/agc2/rnn_vad/spectral_features_internal.cc
        webrtc/modules/audio_processing/audio_buffer.cc
        webrtc/modules/audio_processing/audio_processing_impl.cc
        webrtc/modules/audio_processing/audio_processing_builder_impl.cc
        webrtc/modules/audio_processing/echo_control_mobile_impl.cc
        webrtc/modules/audio_processing/echo_detector/circular_buffer.cc
        webrtc/modules/audio_processing/echo_detector/mean_variance_estimator.cc
        webrtc/modules/audio_processing/echo_detector/moving_max.cc
        webrtc/modules/audio_processing/echo_detector/normalized_covariance_estimator.cc
        webrtc/modules/audio_processing/gain_control_impl.cc
        webrtc/modules/audio_processing/gain_controller2.cc
        webrtc/modules/audio_processing/high_pass_filter.cc
        webrtc/modules/audio_processing/include/aec_dump.cc
        webrtc/modules/audio_processing/include/audio_frame_proxies.cc
        webrtc/modules/audio_processing/include/audio_processing.cc
        webrtc/modules/audio_processing/include/audio_processing_statistics.cc
        webrtc/modules/audio_processing/include/config.cc
        webrtc/modules/audio_processing/level_estimator.cc
        webrtc/modules/audio_processing/logging/apm_data_dumper.cc
        webrtc/modules/audio_processing/ns/fast_math.cc
        webrtc/modules/audio_processing/ns/histograms.cc
        webrtc/modules/audio_processing/ns/noise_estimator.cc
        webrtc/modules/audio_processing/ns/noise_suppressor.cc
        webrtc/modules/audio_processing/ns/ns_fft.cc
        webrtc/modules/audio_processing/ns/prior_signal_model.cc
        webrtc/modules/audio_processing/ns/prior_signal_model_estimator.cc
        webrtc/modules/audio_processing/ns/quantile_noise_estimator.cc
        webrtc/modules/audio_processing/ns/signal_model.cc
        webrtc/modules/audio_processing/ns/signal_model_estimator.cc
        webrtc/modules/audio_processing/ns/speech_probability_estimator.cc
        webrtc/modules/audio_processing/ns/suppression_params.cc
        webrtc/modules/audio_processing/ns/wiener_filter.cc
        webrtc/modules/audio_processing/residual_echo_detector.cc
        webrtc/modules/audio_processing/rms_level.cc
        webrtc/modules/audio_processing/splitting_filter.cc
        webrtc/modules/audio_processing/three_band_filter_bank.cc
        webrtc/modules/audio_processing/transient/file_utils.cc
        webrtc/modules/audio_processing/transient/moving_moments.cc
        webrtc/modules/audio_processing/transient/transient_detector.cc
        webrtc/modules/audio_processing/transient/transient_suppressor_impl.cc
        webrtc/modules/audio_processing/transient/wpd_node.cc
        webrtc/modules/audio_processing/transient/wpd_tree.cc
        webrtc/modules/audio_processing/typing_detection.cc
        webrtc/modules/audio_processing/utility/cascaded_biquad_filter.cc
        webrtc/modules/audio_processing/utility/delay_estimator.cc
        webrtc/modules/audio_processing/utility/delay_estimator_wrapper.cc
        webrtc/modules/audio_processing/utility/pffft_wrapper.cc
        webrtc/modules/audio_processing/vad/gmm.cc
        webrtc/modules/audio_processing/vad/pitch_based_vad.cc
        webrtc/modules/audio_processing/vad/pitch_internal.cc
        webrtc/modules/audio_processing/vad/pole_zero_filter.cc
        webrtc/modules/audio_processing/vad/standalone_vad.cc
        webrtc/modules/audio_processing/vad/vad_audio_proc.cc
        webrtc/modules/audio_processing/vad/vad_circular_buffer.cc
        webrtc/modules/audio_processing/vad/voice_activity_detector.cc
        webrtc/modules/audio_processing/voice_detection.cc
        webrtc/modules/audio_processing/optionally_built_submodule_creators.cc
        webrtc/modules/congestion_controller/pcc/bitrate_controller.cc
        webrtc/modules/congestion_controller/pcc/monitor_interval.cc
        webrtc/modules/congestion_controller/pcc/pcc_factory.cc
        webrtc/modules/congestion_controller/pcc/pcc_network_controller.cc
        webrtc/modules/congestion_controller/pcc/rtt_tracker.cc
        webrtc/modules/congestion_controller/pcc/utility_function.cc
        webrtc/modules/congestion_controller/receive_side_congestion_controller.cc
        webrtc/modules/congestion_controller/rtp/control_handler.cc
        webrtc/modules/congestion_controller/rtp/transport_feedback_adapter.cc
        webrtc/modules/congestion_controller/rtp/transport_feedback_demuxer.cc
        webrtc/modules/congestion_controller/goog_cc/acknowledged_bitrate_estimator.cc
        webrtc/modules/congestion_controller/goog_cc/acknowledged_bitrate_estimator_interface.cc
        webrtc/modules/congestion_controller/goog_cc/alr_detector.cc
        webrtc/modules/congestion_controller/goog_cc/bitrate_estimator.cc
        webrtc/modules/congestion_controller/goog_cc/congestion_window_pushback_controller.cc
        webrtc/modules/congestion_controller/goog_cc/delay_based_bwe.cc
        webrtc/modules/congestion_controller/goog_cc/goog_cc_network_control.cc
        webrtc/modules/congestion_controller/goog_cc/link_capacity_estimator.cc
        webrtc/modules/congestion_controller/goog_cc/loss_based_bandwidth_estimation.cc
        webrtc/modules/congestion_controller/goog_cc/probe_bitrate_estimator.cc
        webrtc/modules/congestion_controller/goog_cc/probe_controller.cc
        webrtc/modules/congestion_controller/goog_cc/robust_throughput_estimator.cc
        webrtc/modules/congestion_controller/goog_cc/send_side_bandwidth_estimation.cc
        webrtc/modules/congestion_controller/goog_cc/trendline_estimator.cc
        webrtc/modules/include/module_common_types.cc
        webrtc/modules/pacing/bitrate_prober.cc
        webrtc/modules/pacing/interval_budget.cc
        webrtc/modules/pacing/paced_sender.cc
        webrtc/modules/pacing/pacing_controller.cc
        webrtc/modules/pacing/packet_router.cc
        webrtc/modules/pacing/round_robin_packet_queue.cc
        webrtc/modules/pacing/task_queue_paced_sender.cc
        webrtc/modules/rtp_rtcp/include/report_block_data.cc
        webrtc/modules/rtp_rtcp/include/rtp_rtcp_defines.cc
        webrtc/modules/rtp_rtcp/source/active_decode_targets_helper.cc
        webrtc/modules/rtp_rtcp/source/absolute_capture_time_receiver.cc
        webrtc/modules/rtp_rtcp/source/absolute_capture_time_sender.cc
        webrtc/modules/rtp_rtcp/source/create_video_rtp_depacketizer.cc
        webrtc/modules/rtp_rtcp/source/dtmf_queue.cc
        webrtc/modules/rtp_rtcp/source/fec_private_tables_bursty.cc
        webrtc/modules/rtp_rtcp/source/fec_private_tables_random.cc
        webrtc/modules/rtp_rtcp/source/fec_test_helper.cc
        webrtc/modules/rtp_rtcp/source/flexfec_header_reader_writer.cc
        webrtc/modules/rtp_rtcp/source/flexfec_receiver.cc
        webrtc/modules/rtp_rtcp/source/flexfec_sender.cc
        webrtc/modules/rtp_rtcp/source/forward_error_correction.cc
        webrtc/modules/rtp_rtcp/source/forward_error_correction_internal.cc
        webrtc/modules/rtp_rtcp/source/packet_loss_stats.cc
        webrtc/modules/rtp_rtcp/source/remote_ntp_time_estimator.cc
        webrtc/modules/rtp_rtcp/source/rtcp_nack_stats.cc
        webrtc/modules/rtp_rtcp/source/rtcp_packet.cc
        webrtc/modules/rtp_rtcp/source/rtcp_packet/app.cc
        webrtc/modules/rtp_rtcp/source/rtcp_packet/bye.cc
        webrtc/modules/rtp_rtcp/source/rtcp_packet/common_header.cc
        webrtc/modules/rtp_rtcp/source/rtcp_packet/compound_packet.cc
        webrtc/modules/rtp_rtcp/source/rtcp_packet/dlrr.cc
        webrtc/modules/rtp_rtcp/source/rtcp_packet/extended_jitter_report.cc
        webrtc/modules/rtp_rtcp/source/rtcp_packet/extended_reports.cc
        webrtc/modules/rtp_rtcp/source/rtcp_packet/fir.cc
        webrtc/modules/rtp_rtcp/source/rtcp_packet/loss_notification.cc
        webrtc/modules/rtp_rtcp/source/rtcp_packet/nack.cc
        webrtc/modules/rtp_rtcp/source/rtcp_packet/pli.cc
        webrtc/modules/rtp_rtcp/source/rtcp_packet/psfb.cc
        webrtc/modules/rtp_rtcp/source/rtcp_packet/rapid_resync_request.cc
        webrtc/modules/rtp_rtcp/source/rtcp_packet/receiver_report.cc
        webrtc/modules/rtp_rtcp/source/rtcp_packet/remb.cc
        webrtc/modules/rtp_rtcp/source/rtcp_packet/remote_estimate.cc
        webrtc/modules/rtp_rtcp/source/rtcp_packet/report_block.cc
        webrtc/modules/rtp_rtcp/source/rtcp_packet/rrtr.cc
        webrtc/modules/rtp_rtcp/source/rtcp_packet/rtpfb.cc
        webrtc/modules/rtp_rtcp/source/rtcp_packet/sdes.cc
        webrtc/modules/rtp_rtcp/source/rtcp_packet/sender_report.cc
        webrtc/modules/rtp_rtcp/source/rtcp_packet/target_bitrate.cc
        webrtc/modules/rtp_rtcp/source/rtcp_packet/tmmb_item.cc
        webrtc/modules/rtp_rtcp/source/rtcp_packet/tmmbn.cc
        webrtc/modules/rtp_rtcp/source/rtcp_packet/tmmbr.cc
        webrtc/modules/rtp_rtcp/source/rtcp_packet/transport_feedback.cc
        webrtc/modules/rtp_rtcp/source/rtcp_receiver.cc
        webrtc/modules/rtp_rtcp/source/rtcp_sender.cc
        webrtc/modules/rtp_rtcp/source/rtcp_transceiver.cc
        webrtc/modules/rtp_rtcp/source/rtcp_transceiver_config.cc
        webrtc/modules/rtp_rtcp/source/rtcp_transceiver_impl.cc
        webrtc/modules/rtp_rtcp/source/rtp_dependency_descriptor_extension.cc
        webrtc/modules/rtp_rtcp/source/rtp_dependency_descriptor_reader.cc
        webrtc/modules/rtp_rtcp/source/rtp_dependency_descriptor_writer.cc
        webrtc/modules/rtp_rtcp/source/rtp_descriptor_authentication.cc
        webrtc/modules/rtp_rtcp/source/rtp_format.cc
        webrtc/modules/rtp_rtcp/source/rtp_format_h264.cc
        webrtc/modules/rtp_rtcp/source/rtp_format_h265.cc
        webrtc/modules/rtp_rtcp/source/rtp_format_video_generic.cc
        webrtc/modules/rtp_rtcp/source/rtp_format_vp8.cc
        webrtc/modules/rtp_rtcp/source/rtp_format_vp9.cc
        webrtc/modules/rtp_rtcp/source/rtp_generic_frame_descriptor.cc
        webrtc/modules/rtp_rtcp/source/rtp_generic_frame_descriptor_extension.cc
        webrtc/modules/rtp_rtcp/source/rtp_header_extension_map.cc
        webrtc/modules/rtp_rtcp/source/rtp_header_extension_size.cc
        webrtc/modules/rtp_rtcp/source/rtp_header_extensions.cc
        webrtc/modules/rtp_rtcp/source/rtp_packet.cc
        webrtc/modules/rtp_rtcp/source/rtp_packet_history.cc
        webrtc/modules/rtp_rtcp/source/rtp_packet_received.cc
        webrtc/modules/rtp_rtcp/source/rtp_packet_to_send.cc
        webrtc/modules/rtp_rtcp/source/rtp_packetizer_av1.cc
        webrtc/modules/rtp_rtcp/source/rtp_rtcp_impl.cc
        webrtc/modules/rtp_rtcp/source/rtp_sender.cc
        webrtc/modules/rtp_rtcp/source/rtp_sender_audio.cc
        webrtc/modules/rtp_rtcp/source/rtp_sender_egress.cc
        webrtc/modules/rtp_rtcp/source/rtp_sender_video.cc
        webrtc/modules/rtp_rtcp/source/rtp_sender_video_frame_transformer_delegate.cc
        webrtc/modules/rtp_rtcp/source/rtp_sequence_number_map.cc
        webrtc/modules/rtp_rtcp/source/rtp_utility.cc
        webrtc/modules/rtp_rtcp/source/rtp_video_header.cc
        webrtc/modules/rtp_rtcp/source/source_tracker.cc
        webrtc/modules/rtp_rtcp/source/time_util.cc
        webrtc/modules/rtp_rtcp/source/tmmbr_help.cc
        webrtc/modules/rtp_rtcp/source/ulpfec_generator.cc
        webrtc/modules/rtp_rtcp/source/ulpfec_header_reader_writer.cc
        webrtc/modules/rtp_rtcp/source/ulpfec_receiver_impl.cc
        webrtc/modules/rtp_rtcp/source/video_rtp_depacketizer.cc
        webrtc/modules/rtp_rtcp/source/video_rtp_depacketizer_av1.cc
        webrtc/modules/rtp_rtcp/source/video_rtp_depacketizer_generic.cc
        webrtc/modules/rtp_rtcp/source/video_rtp_depacketizer_h264.cc
        webrtc/modules/rtp_rtcp/source/video_rtp_depacketizer_h265.cc
        webrtc/modules/rtp_rtcp/source/video_rtp_depacketizer_raw.cc
        webrtc/modules/rtp_rtcp/source/video_rtp_depacketizer_vp8.cc
        webrtc/modules/rtp_rtcp/source/video_rtp_depacketizer_vp9.cc
        webrtc/modules/rtp_rtcp/source/receive_statistics_impl.cc
        webrtc/modules/rtp_rtcp/source/deprecated/deprecated_rtp_sender_egress.cc
        webrtc/modules/utility/source/helpers_android.cc
        webrtc/modules/utility/source/jvm_android.cc
        webrtc/modules/utility/source/process_thread_impl.cc
        webrtc/modules/video_capture/device_info_impl.cc
        webrtc/modules/video_capture/linux/device_info_linux.cc
        webrtc/modules/video_capture/linux/video_capture_linux.cc
        webrtc/modules/video_capture/video_capture_factory.cc
        webrtc/modules/video_capture/video_capture_impl.cc
        webrtc/modules/video_coding/codec_timer.cc
        webrtc/modules/video_coding/codecs/av1/libaom_av1_decoder_absent.cc
        webrtc/modules/video_coding/codecs/av1/libaom_av1_encoder_absent.cc
        webrtc/modules/video_coding/codecs/h264/h264.cc
        webrtc/modules/video_coding/codecs/h264/h264_color_space.cc
        webrtc/modules/video_coding/codecs/h264/h264_decoder_impl.cc
        webrtc/modules/video_coding/codecs/h264/h264_encoder_impl.cc
        webrtc/modules/video_coding/codecs/multiplex/augmented_video_frame_buffer.cc
        webrtc/modules/video_coding/codecs/multiplex/multiplex_decoder_adapter.cc
        webrtc/modules/video_coding/codecs/multiplex/multiplex_encoded_image_packer.cc
        webrtc/modules/video_coding/codecs/multiplex/multiplex_encoder_adapter.cc
        webrtc/modules/video_coding/decoder_database.cc
        webrtc/modules/video_coding/chain_diff_calculator.cc
        webrtc/modules/video_coding/decoding_state.cc
        webrtc/modules/video_coding/encoded_frame.cc
        webrtc/modules/video_coding/event_wrapper.cc
        webrtc/modules/video_coding/fec_controller_default.cc
        webrtc/modules/video_coding/frame_buffer.cc
        webrtc/modules/video_coding/frame_buffer2.cc
        webrtc/modules/video_coding/frame_dependencies_calculator.cc
        webrtc/modules/video_coding/frame_object.cc
        webrtc/modules/video_coding/generic_decoder.cc
        webrtc/modules/video_coding/h264_sprop_parameter_sets.cc
        webrtc/modules/video_coding/h264_sps_pps_tracker.cc
        webrtc/modules/video_coding/h265_vps_sps_pps_tracker.cc
        webrtc/modules/video_coding/histogram.cc
        webrtc/modules/video_coding/include/video_codec_interface.cc
        webrtc/modules/video_coding/inter_frame_delay.cc
        webrtc/modules/video_coding/jitter_buffer.cc
        webrtc/modules/video_coding/jitter_estimator.cc
        webrtc/modules/video_coding/loss_notification_controller.cc
        webrtc/modules/video_coding/media_opt_util.cc
        webrtc/modules/video_coding/packet.cc
        webrtc/modules/video_coding/packet_buffer.cc
        webrtc/modules/video_coding/receiver.cc
        webrtc/modules/video_coding/rtp_frame_reference_finder.cc
        webrtc/modules/video_coding/rtt_filter.cc
        webrtc/modules/video_coding/session_info.cc
        webrtc/modules/video_coding/timestamp_map.cc
        webrtc/modules/video_coding/timing.cc
        webrtc/modules/video_coding/unique_timestamp_counter.cc
        webrtc/modules/video_coding/utility/decoded_frames_history.cc
        webrtc/modules/video_coding/utility/frame_dropper.cc
        webrtc/modules/video_coding/utility/framerate_controller.cc
        webrtc/modules/video_coding/utility/ivf_file_reader.cc
        webrtc/modules/video_coding/utility/ivf_file_writer.cc
        webrtc/modules/video_coding/utility/quality_scaler.cc
        webrtc/modules/video_coding/utility/simulcast_rate_allocator.cc
        webrtc/modules/video_coding/utility/simulcast_utility.cc
        webrtc/modules/video_coding/utility/vp8_header_parser.cc
        webrtc/modules/video_coding/utility/vp9_uncompressed_header_parser.cc
        webrtc/modules/video_coding/video_codec_initializer.cc
        webrtc/modules/video_coding/video_coding_defines.cc
        webrtc/modules/video_coding/video_coding_impl.cc
        webrtc/modules/video_coding/video_receiver.cc
        webrtc/modules/video_coding/video_receiver2.cc
        webrtc/modules/video_coding/codecs/vp8/default_temporal_layers.cc
        webrtc/modules/video_coding/codecs/vp8/libvpx_interface.cc
        webrtc/modules/video_coding/codecs/vp8/libvpx_vp8_decoder.cc
        webrtc/modules/video_coding/codecs/vp8/libvpx_vp8_encoder.cc
        webrtc/modules/video_coding/codecs/vp8/screenshare_layers.cc
        webrtc/modules/video_coding/codecs/vp8/temporal_layers_checker.cc
        webrtc/modules/video_coding/codecs/vp9/svc_config.cc
        webrtc/modules/video_coding/codecs/vp9/svc_rate_allocator.cc
        webrtc/modules/video_coding/codecs/vp9/vp9.cc
        webrtc/modules/video_coding/codecs/vp9/vp9_frame_buffer_pool.cc
        webrtc/modules/video_coding/codecs/vp9/vp9_impl.cc
        webrtc/modules/video_processing/util/denoiser_filter.cc
        webrtc/modules/video_processing/util/denoiser_filter_c.cc
        webrtc/modules/video_processing/util/noise_estimation.cc
        webrtc/modules/video_processing/util/skin_detection.cc
        webrtc/modules/video_processing/video_denoiser.cc
        webrtc/call/adaptation/resource_adaptation_processor_interface.cc
        webrtc/call/adaptation/video_source_restrictions.cc
        webrtc/call/audio_receive_stream.cc
        webrtc/call/audio_send_stream.cc
        webrtc/call/audio_state.cc
        webrtc/call/bitrate_allocator.cc
        webrtc/call/call.cc
        webrtc/call/call_config.cc
        webrtc/call/call_factory.cc
        webrtc/call/degraded_call.cc
        webrtc/call/fake_network_pipe.cc
        webrtc/call/flexfec_receive_stream.cc
        webrtc/call/flexfec_receive_stream_impl.cc
        webrtc/call/receive_time_calculator.cc
        webrtc/call/rtp_bitrate_configurator.cc
        webrtc/call/rtp_config.cc
        webrtc/call/rtp_demuxer.cc
        webrtc/call/rtp_payload_params.cc
        webrtc/call/rtp_stream_receiver_controller.cc
        webrtc/call/rtp_transport_controller_send.cc
        webrtc/call/rtp_video_sender.cc
        webrtc/call/rtx_receive_stream.cc
        webrtc/call/simulated_network.cc
        webrtc/call/syncable.cc
        webrtc/call/video_receive_stream.cc
        webrtc/call/video_send_stream.cc
        webrtc/common_audio/audio_converter.cc
        webrtc/common_audio/audio_util.cc
        webrtc/common_audio/channel_buffer.cc
        webrtc/common_audio/fir_filter_c.cc
        webrtc/common_audio/fir_filter_factory.cc
        webrtc/common_audio/real_fourier.cc
        webrtc/common_audio/real_fourier_ooura.cc
        webrtc/common_audio/resampler/push_resampler.cc
        webrtc/common_audio/resampler/push_sinc_resampler.cc
        webrtc/common_audio/resampler/resampler.cc
        webrtc/common_audio/resampler/sinc_resampler.cc
        webrtc/common_audio/resampler/sinusoidal_linear_chirp_source.cc
        webrtc/common_audio/signal_processing/dot_product_with_scale.cc
        webrtc/common_audio/smoothing_filter.cc
        webrtc/common_audio/vad/vad.cc
        webrtc/common_audio/wav_file.cc
        webrtc/common_audio/wav_header.cc
        webrtc/common_audio/window_generator.cc
        webrtc/common_audio/ring_buffer.c
        webrtc/common_audio/signal_processing/auto_corr_to_refl_coef.c
        webrtc/common_audio/signal_processing/auto_correlation.c
        webrtc/common_audio/signal_processing/complex_fft.c
        webrtc/common_audio/signal_processing/copy_set_operations.c
        webrtc/common_audio/signal_processing/cross_correlation.c
        webrtc/common_audio/signal_processing/division_operations.c
        webrtc/common_audio/signal_processing/downsample_fast.c
        webrtc/common_audio/signal_processing/energy.c
        webrtc/common_audio/signal_processing/filter_ar.c
        webrtc/common_audio/signal_processing/filter_ma_fast_q12.c
        webrtc/common_audio/signal_processing/get_hanning_window.c
        webrtc/common_audio/signal_processing/get_scaling_square.c
        webrtc/common_audio/signal_processing/ilbc_specific_functions.c
        webrtc/common_audio/signal_processing/levinson_durbin.c
        webrtc/common_audio/signal_processing/lpc_to_refl_coef.c
        webrtc/common_audio/signal_processing/min_max_operations.c
        webrtc/common_audio/signal_processing/randomization_functions.c
        webrtc/common_audio/signal_processing/real_fft.c
        webrtc/common_audio/signal_processing/refl_coef_to_lpc.c
        webrtc/common_audio/signal_processing/resample.c
        webrtc/common_audio/signal_processing/resample_48khz.c
        webrtc/common_audio/signal_processing/resample_by_2.c
        webrtc/common_audio/signal_processing/resample_by_2_internal.c
        webrtc/common_audio/signal_processing/resample_fractional.c
        webrtc/common_audio/signal_processing/spl_init.c
        webrtc/common_audio/signal_processing/spl_inl.c
        webrtc/common_audio/signal_processing/spl_sqrt.c
        webrtc/common_audio/signal_processing/splitting_filter.c
        webrtc/common_audio/signal_processing/sqrt_of_one_minus_x_squared.c
        webrtc/common_audio/signal_processing/vector_scaling_operations.c
        webrtc/common_audio/third_party/spl_sqrt_floor/spl_sqrt_floor.c
        webrtc/common_audio/vad/vad_core.c
        webrtc/common_audio/vad/vad_filterbank.c
        webrtc/common_audio/vad/vad_gmm.c
        webrtc/common_audio/vad/vad_sp.c
        webrtc/common_audio/vad/webrtc_vad.c
        webrtc/common_audio/third_party/ooura/fft_size_128/ooura_fft.cc
        webrtc/common_audio/third_party/ooura/fft_size_256/fft4g.cc
        webrtc/common_video/frame_rate_estimator.cc
        webrtc/common_video/generic_frame_descriptor/generic_frame_info.cc
        webrtc/common_video/h264/h264_bitstream_parser.cc
        webrtc/common_video/h264/h264_common.cc
        webrtc/common_video/h264/pps_parser.cc
        webrtc/common_video/h264/prefix_parser.cc
        webrtc/common_video/h264/sps_parser.cc
        webrtc/common_video/h264/sps_vui_rewriter.cc
        webrtc/common_video/h265/h265_bitstream_parser.cc
        webrtc/common_video/h265/h265_common.cc
        webrtc/common_video/h265/h265_pps_parser.cc
        webrtc/common_video/h265/h265_sps_parser.cc
        webrtc/common_video/h265/h265_vps_parser.cc
        webrtc/common_video/i420_buffer_pool.cc
        webrtc/common_video/incoming_video_stream.cc
        webrtc/common_video/libyuv/webrtc_libyuv.cc
        webrtc/common_video/video_frame_buffer.cc
        webrtc/common_video/video_render_frames.cc
        webrtc/p2p/base/async_stun_tcp_socket.cc
        webrtc/p2p/base/basic_async_resolver_factory.cc
        webrtc/p2p/base/basic_ice_controller.cc
        webrtc/p2p/base/basic_packet_socket_factory.cc
        webrtc/p2p/base/connection.cc
        webrtc/p2p/base/connection_info.cc
        webrtc/p2p/base/default_ice_transport_factory.cc
        webrtc/p2p/base/dtls_transport.cc
        webrtc/p2p/base/dtls_transport_internal.cc
        webrtc/p2p/base/ice_controller_interface.cc
        webrtc/p2p/base/ice_credentials_iterator.cc
        webrtc/p2p/base/ice_transport_internal.cc
        webrtc/p2p/base/mdns_message.cc
        webrtc/p2p/base/p2p_constants.cc
        webrtc/p2p/base/p2p_transport_channel.cc
        webrtc/p2p/base/packet_transport_internal.cc
        webrtc/p2p/base/port.cc
        webrtc/p2p/base/port_allocator.cc
        webrtc/p2p/base/port_interface.cc
        webrtc/p2p/base/pseudo_tcp.cc
        webrtc/p2p/base/regathering_controller.cc
        webrtc/p2p/base/stun_port.cc
        webrtc/p2p/base/stun_request.cc
        webrtc/p2p/base/stun_server.cc
        webrtc/p2p/base/tcp_port.cc
        webrtc/p2p/base/test_stun_server.cc
        webrtc/p2p/base/transport_description.cc
        webrtc/p2p/base/transport_description_factory.cc
        webrtc/p2p/base/turn_port.cc
        webrtc/p2p/base/turn_server.cc
        webrtc/p2p/client/basic_port_allocator.cc
        webrtc/p2p/client/turn_port_factory.cc
        webrtc/p2p/stunprober/stun_prober.cc
        webrtc/video/adaptation/quality_rampup_experiment_helper.cc
        webrtc/modules/video_coding/deprecated/nack_module.cc
        webrtc/modules/video_coding/nack_module2.cc
        webrtc/logging/rtc_event_log/encoder/blob_encoding.cc
        webrtc/logging/rtc_event_log/encoder/delta_encoding.cc
        webrtc/logging/rtc_event_log/encoder/rtc_event_log_encoder_common.cc
        webrtc/logging/rtc_event_log/encoder/var_int.cc
        webrtc/logging/rtc_event_log/events/rtc_event_alr_state.cc
        webrtc/logging/rtc_event_log/events/rtc_event_audio_network_adaptation.cc
        webrtc/logging/rtc_event_log/events/rtc_event_audio_playout.cc
        webrtc/logging/rtc_event_log/events/rtc_event_audio_receive_stream_config.cc
        webrtc/logging/rtc_event_log/events/rtc_event_audio_send_stream_config.cc
        webrtc/logging/rtc_event_log/events/rtc_event_bwe_update_delay_based.cc
        webrtc/logging/rtc_event_log/events/rtc_event_bwe_update_loss_based.cc
        webrtc/logging/rtc_event_log/events/rtc_event_dtls_transport_state.cc
        webrtc/logging/rtc_event_log/events/rtc_event_dtls_writable_state.cc
        webrtc/logging/rtc_event_log/events/rtc_event_generic_ack_received.cc
        webrtc/logging/rtc_event_log/events/rtc_event_generic_packet_received.cc
        webrtc/logging/rtc_event_log/events/rtc_event_generic_packet_sent.cc
        webrtc/logging/rtc_event_log/events/rtc_event_ice_candidate_pair.cc
        webrtc/logging/rtc_event_log/events/rtc_event_ice_candidate_pair_config.cc
        webrtc/logging/rtc_event_log/events/rtc_event_probe_cluster_created.cc
        webrtc/logging/rtc_event_log/events/rtc_event_probe_result_failure.cc
        webrtc/logging/rtc_event_log/events/rtc_event_probe_result_success.cc
        webrtc/logging/rtc_event_log/events/rtc_event_route_change.cc
        webrtc/logging/rtc_event_log/events/rtc_event_rtcp_packet_incoming.cc
        webrtc/logging/rtc_event_log/events/rtc_event_rtcp_packet_outgoing.cc
        webrtc/logging/rtc_event_log/events/rtc_event_rtp_packet_incoming.cc
        webrtc/logging/rtc_event_log/events/rtc_event_rtp_packet_outgoing.cc
        webrtc/logging/rtc_event_log/events/rtc_event_video_receive_stream_config.cc
        webrtc/logging/rtc_event_log/events/rtc_event_video_send_stream_config.cc
        webrtc/logging/rtc_event_log/fake_rtc_event_log.cc
        webrtc/logging/rtc_event_log/fake_rtc_event_log_factory.cc
        webrtc/logging/rtc_event_log/ice_logger.cc
        webrtc/logging/rtc_event_log/rtc_event_log_impl.cc
        webrtc/logging/rtc_event_log/rtc_stream_config.cc
        webrtc/video/adaptation/encode_usage_resource.cc
        webrtc/video/adaptation/overuse_frame_detector.cc
        webrtc/video/adaptation/quality_scaler_resource.cc
        webrtc/video/adaptation/video_stream_encoder_resource.cc
        webrtc/video/adaptation/video_stream_encoder_resource_manager.cc
        webrtc/video/buffered_frame_decryptor.cc
        webrtc/video/call_stats.cc
        webrtc/video/encoder_bitrate_adjuster.cc
        webrtc/video/encoder_overshoot_detector.cc
        webrtc/video/encoder_rtcp_feedback.cc
        webrtc/video/frame_dumping_decoder.cc
        webrtc/video/frame_encode_metadata_writer.cc
        webrtc/video/quality_limitation_reason_tracker.cc
        webrtc/video/quality_threshold.cc
        webrtc/video/receive_statistics_proxy.cc
        webrtc/video/report_block_stats.cc
        webrtc/video/rtp_streams_synchronizer.cc
        webrtc/video/rtp_video_stream_receiver.cc
        webrtc/video/rtp_video_stream_receiver_frame_transformer_delegate.cc
        webrtc/video/send_delay_stats.cc
        webrtc/video/send_statistics_proxy.cc
        webrtc/video/stats_counter.cc
        webrtc/video/stream_synchronization.cc
        webrtc/video/transport_adapter.cc
        webrtc/video/video_quality_observer.cc
        webrtc/video/video_receive_stream.cc
        webrtc/video/video_send_stream.cc
        webrtc/video/video_send_stream_impl.cc
        webrtc/video/video_source_sink_controller.cc
        webrtc/video/video_stream_decoder.cc
        webrtc/video/video_stream_decoder_impl.cc
        webrtc/video/video_stream_encoder.cc
        webrtc/video/video_stream_decoder2.cc
        webrtc/video/video_receive_stream2.cc
        webrtc/video/video_quality_observer2.cc
        webrtc/video/rtp_video_stream_receiver2.cc
        webrtc/video/rtp_streams_synchronizer2.cc
        webrtc/video/receive_statistics_proxy2.cc
        webrtc/video/call_stats2.cc
        webrtc/audio/audio_level.cc
        webrtc/audio/audio_receive_stream.cc
        webrtc/audio/audio_send_stream.cc
        webrtc/audio/audio_state.cc
        webrtc/audio/audio_transport_impl.cc
        webrtc/audio/channel_receive.cc
        webrtc/audio/channel_receive_frame_transformer_delegate.cc
        webrtc/audio/channel_send.cc
        webrtc/audio/channel_send_frame_transformer_delegate.cc
        webrtc/audio/null_audio_poller.cc
        webrtc/audio/remix_resample.cc
        webrtc/audio/utility/audio_frame_operations.cc
        webrtc/audio/utility/channel_mixer.cc
        webrtc/audio/utility/channel_mixing_matrix.cc
        webrtc/audio/voip/audio_egress.cc
        webrtc/modules/remote_bitrate_estimator/aimd_rate_control.cc
        webrtc/modules/remote_bitrate_estimator/bwe_defines.cc
        webrtc/modules/remote_bitrate_estimator/inter_arrival.cc
        webrtc/modules/remote_bitrate_estimator/overuse_detector.cc
        webrtc/modules/remote_bitrate_estimator/overuse_estimator.cc
        webrtc/modules/remote_bitrate_estimator/remote_bitrate_estimator_abs_send_time.cc
        webrtc/modules/remote_bitrate_estimator/remote_bitrate_estimator_single_stream.cc
        webrtc/modules/remote_bitrate_estimator/test/bwe_test_logging.cc
        webrtc/modules/remote_bitrate_estimator/remote_estimator_proxy.cc
        webrtc/sdk/media_constraints.cc
        webrtc/sdk/android/native_api/audio_device_module/audio_device_android.cc
        webrtc/sdk/android/native_api/base/init.cc
        webrtc/sdk/android/native_api/codecs/wrapper.cc
        webrtc/sdk/android/native_api/jni/class_loader.cc
        webrtc/sdk/android/native_api/jni/java_types.cc
        webrtc/sdk/android/native_api/jni/jvm.cc
        webrtc/sdk/android/native_api/peerconnection/peer_connection_factory.cc
        webrtc/sdk/android/native_api/stacktrace/stacktrace.cc
        webrtc/sdk/android/native_api/video/video_source.cc
        webrtc/sdk/android/native_api/video/wrapper.cc
        webrtc/sdk/android/native_api/network_monitor/network_monitor.cc
        webrtc/sdk/android/src/jni/android_histogram.cc
        webrtc/sdk/android/src/jni/android_metrics.cc
        webrtc/sdk/android/src/jni/android_network_monitor.cc
        webrtc/sdk/android/src/jni/android_video_track_source.cc
        webrtc/sdk/android/src/jni/audio_device/audio_device_module.cc
        webrtc/sdk/android/src/jni/audio_device/audio_record_jni.cc
        webrtc/sdk/android/src/jni/audio_device/audio_track_jni.cc
        webrtc/sdk/android/src/jni/audio_device/java_audio_device_module.cc
        webrtc/sdk/android/src/jni/audio_device/opensles_common.cc
        webrtc/sdk/android/src/jni/audio_device/opensles_player.cc
        webrtc/sdk/android/src/jni/audio_device/opensles_recorder.cc
        webrtc/sdk/android/src/jni/builtin_audio_decoder_factory_factory.cc
        webrtc/sdk/android/src/jni/builtin_audio_encoder_factory_factory.cc
        webrtc/sdk/android/src/jni/encoded_image.cc
        webrtc/sdk/android/src/jni/h264_utils.cc
        webrtc/sdk/android/src/jni/java_i420_buffer.cc
        webrtc/sdk/android/src/jni/jni_common.cc
        webrtc/sdk/android/src/jni/jni_generator_helper.cc
        webrtc/sdk/android/src/jni/jni_helpers.cc
        webrtc/sdk/android/src/jni/jvm.cc
        webrtc/sdk/android/src/jni/logging/log_sink.cc
        webrtc/sdk/android/src/jni/native_capturer_observer.cc
        webrtc/sdk/android/src/jni/nv12_buffer.cc
        webrtc/sdk/android/src/jni/nv21_buffer.cc
        webrtc/sdk/android/src/jni/pc/audio.cc
        webrtc/sdk/android/src/jni/pc/audio_track.cc
        webrtc/sdk/android/src/jni/pc/call_session_file_rotating_log_sink.cc
        webrtc/sdk/android/src/jni/pc/crypto_options.cc
        webrtc/sdk/android/src/jni/pc/data_channel.cc
        webrtc/sdk/android/src/jni/pc/dtmf_sender.cc
        webrtc/sdk/android/src/jni/pc/ice_candidate.cc
        webrtc/sdk/android/src/jni/pc/logging.cc
        webrtc/sdk/android/src/jni/pc/media_constraints.cc
        webrtc/sdk/android/src/jni/pc/media_source.cc
        webrtc/sdk/android/src/jni/pc/media_stream.cc
        webrtc/sdk/android/src/jni/pc/media_stream_track.cc
        webrtc/sdk/android/src/jni/pc/owned_factory_and_threads.cc
        webrtc/sdk/android/src/jni/pc/peer_connection.cc
        webrtc/sdk/android/src/jni/pc/peer_connection_factory.cc
        webrtc/sdk/android/src/jni/pc/rtc_certificate.cc
        webrtc/sdk/android/src/jni/pc/rtc_stats_collector_callback_wrapper.cc
        webrtc/sdk/android/src/jni/pc/rtp_parameters.cc
        webrtc/sdk/android/src/jni/pc/rtp_receiver.cc
        webrtc/sdk/android/src/jni/pc/rtp_sender.cc
        webrtc/sdk/android/src/jni/pc/rtp_transceiver.cc
        webrtc/sdk/android/src/jni/pc/sdp_observer.cc
        webrtc/sdk/android/src/jni/pc/session_description.cc
        webrtc/sdk/android/src/jni/pc/ssl_certificate_verifier_wrapper.cc
        webrtc/sdk/android/src/jni/pc/stats_observer.cc
        webrtc/sdk/android/src/jni/pc/turn_customizer.cc
        webrtc/sdk/android/src/jni/pc/video.cc
        webrtc/sdk/android/src/jni/scoped_java_ref_counted.cc
        webrtc/sdk/android/src/jni/timestamp_aligner.cc
        webrtc/sdk/android/src/jni/video_codec_info.cc
        webrtc/sdk/android/src/jni/video_codec_status.cc
        webrtc/sdk/android/src/jni/video_decoder_factory_wrapper.cc
        webrtc/sdk/android/src/jni/video_decoder_fallback.cc
        webrtc/sdk/android/src/jni/video_decoder_wrapper.cc
        webrtc/sdk/android/src/jni/video_encoder_factory_wrapper.cc
        webrtc/sdk/android/src/jni/video_encoder_fallback.cc
        webrtc/sdk/android/src/jni/video_encoder_wrapper.cc
        webrtc/sdk/android/src/jni/video_frame.cc
        webrtc/sdk/android/src/jni/video_sink.cc
        webrtc/sdk/android/src/jni/video_track.cc
        webrtc/sdk/android/src/jni/vp8_codec.cc
        webrtc/sdk/android/src/jni/vp9_codec.cc
        webrtc/sdk/android/src/jni/wrapped_native_i420_buffer.cc
        webrtc/sdk/android/src/jni/yuv_helper.cc
        webrtc/stats/rtc_stats_report.cc
        webrtc/stats/rtc_stats.cc
        webrtc/stats/rtcstats_objects.cc
        webrtc/base/third_party/libevent/buffer.c
        webrtc/base/third_party/libevent/epoll.c
        webrtc/base/third_party/libevent/evbuffer.c
        webrtc/base/third_party/libevent/evdns.c
        webrtc/base/third_party/libevent/event.c
        webrtc/base/third_party/libevent/event_tagging.c
        webrtc/base/third_party/libevent/evrpc.c
        webrtc/base/third_party/libevent/evutil.c
        webrtc/base/third_party/libevent/http.c
        webrtc/base/third_party/libevent/log.c
        webrtc/base/third_party/libevent/poll.c
        webrtc/base/third_party/libevent/select.c
        webrtc/base/third_party/libevent/signal.c
        webrtc/base/third_party/libevent/strlcpy.c
        )

if (${ANDROID_ABI} STREQUAL "armeabi-v7a")
    file(GLOB src_tg_call_neon
            webrtc/common_audio/signal_processing/complex_bit_reverse_arm.S
            webrtc/common_audio/signal_processing/filter_ar_fast_q12_armv7.S
            )
elseif (${ANDROID_ABI} STREQUAL "arm64-v8a")
    file(GLOB src_tg_call_neon
            webrtc/common_audio/signal_processing/complex_bit_reverse.c
            webrtc/common_audio/signal_processing/filter_ar_fast_q12.c
            )
endif ()

if (${ANDROID_ABI} STREQUAL ("armeabi-v7a" OR "arm64-v8a"))
    file(GLOB src_tg_call_end
            webrtc/modules/audio_coding/codecs/isac/fix/source/entropy_coding_neon.c
            webrtc/modules/audio_coding/codecs/isac/fix/source/filterbanks_neon.c
            webrtc/modules/audio_coding/codecs/isac/fix/source/filters_neon.c
            webrtc/modules/audio_coding/codecs/isac/fix/source/lattice_neon.c
            webrtc/modules/audio_coding/codecs/isac/fix/source/transform_neon.c
            webrtc/modules/audio_processing/aecm/aecm_core_neon.cc
            webrtc/modules/video_processing/util/denoiser_filter_neon.cc
            webrtc/common_audio/fir_filter_neon.cc
            webrtc/common_audio/signal_processing/cross_correlation_neon.c
            webrtc/common_audio/signal_processing/downsample_fast_neon.c
            webrtc/common_audio/signal_processing/min_max_operations_neon.c
            webrtc/common_audio/resampler/sinc_resampler_neon.cc
            webrtc/common_audio/third_party/ooura/fft_size_128/ooura_fft_neon.cc
            )
elseif (${ANDROID_ABI} STREQUAL "x86")
    file(GLOB src_tg_call_end
            webrtc/modules/video_processing/util/denoiser_filter_sse2.cc
            webrtc/common_audio/fir_filter_sse.cc
            webrtc/common_audio/resampler/sinc_resampler_sse.cc
            webrtc/common_audio/signal_processing/complex_bit_reverse.c
            webrtc/common_audio/signal_processing/filter_ar_fast_q12.c
            webrtc/common_audio/third_party/ooura/fft_size_128/ooura_fft_sse2.cc

            )
elseif (${ANDROID_ABI} STREQUAL "x86_64")
    file(GLOB src_tg_call_end
            webrtc/modules/video_processing/util/denoiser_filter_sse2.cc
            webrtc/common_audio/fir_filter_sse.cc
            webrtc/common_audio/resampler/sinc_resampler_sse.cc
            webrtc/common_audio/signal_processing/complex_bit_reverse.c
            webrtc/common_audio/signal_processing/filter_ar_fast_q12.c
            webrtc/common_audio/third_party/ooura/fft_size_128/ooura_fft_sse2.cc

            )
endif ()

add_library(tgcalls STATIC
        ${tgcalls_src}
        ${webrtc_src}
        ${src_tg_call_neon}
        ${src_tg_call_end}
        )

if (${ANDROID_ABI} STREQUAL ("x86" OR "x86_64"))
    target_link_libraries(tgcalls tgcalls_tp ssl cpufeatures crypto libvpx_yasm)
    target_link_libraries(tgvoip tgcalls)
else ()
    target_link_libraries(tgcalls tgcalls_tp ssl cpufeatures crypto)
    target_link_libraries(tgvoip tgcalls)
endif ()

unset(CMAKE_CXX_FLAGS)
unset(CMAKE_C_FLAGS)
set(CMAKE_C_FLAGS "-Wall -frtti -DHAVE_PTHREAD -finline-functions -ffast-math -Os")
add_definitions(-DANDROID)
include_directories(boringssl/include/)
file(GLOB src_tgnet
        tgnet/ApiScheme.cpp
        tgnet/BuffersStorage.cpp
        tgnet/ByteArray.cpp
        tgnet/ByteStream.cpp
        tgnet/Connection.cpp
        tgnet/ConnectionSession.cpp
        tgnet/ConnectionsManager.cpp
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
        tgnet/Config.cpp
        )

add_library(tgnet STATIC
        ${src_tgnet}
        )

target_link_libraries(tgnet crypto)

unset(CMAKE_CXX_FLAGS)
unset(CMAKE_C_FLAGS)

set(CMAKE_C_FLAGS "-w -std=c11 -O3")
file(GLOB_RECURSE src_lz4
        lz4/lz4.c
        lz4/lz4frame.c
        lz4/xxhash.c
        )

add_library(lz4 STATIC
        ${src_lz4}
        )

unset(CMAKE_CXX_FLAGS)
unset(CMAKE_C_FLAGS)

set(CMAKE_C_FLAGS "-Wall -DANDROID -DHAVE_MALLOC_H -DHAVE_PTHREAD -DWEBP_USE_THREAD -finline-functions -ffast-math -ffunction-sections -fdata-sections -Os")
include_directories(libwebp/src)
file(GLOB src_webp
        libwebp/dec/alpha.c
        libwebp/dec/buffer.c
        libwebp/dec/frame.c
        libwebp/dec/idec.c
        libwebp/dec/io.c
        libwebp/dec/quant.c
        libwebp/dec/tree.c
        libwebp/dec/vp8.c
        libwebp/dec/vp8l.c
        libwebp/dec/webp.c
        libwebp/dsp/alpha_processing.c
        libwebp/dsp/alpha_processing_sse2.c
        libwebp/dsp/cpu.c
        libwebp/dsp/dec.c
        libwebp/dsp/dec_clip_tables.c
        libwebp/dsp/dec_mips32.c
        libwebp/dsp/dec_neon.c
        libwebp/dsp/dec_sse2.c
        libwebp/dsp/enc.c
        libwebp/dsp/enc_avx2.c
        libwebp/dsp/enc_mips32.c
        libwebp/dsp/enc_neon.c
        libwebp/dsp/enc_sse2.c
        libwebp/dsp/lossless.c
        libwebp/dsp/lossless_mips32.c
        libwebp/dsp/lossless_neon.c
        libwebp/dsp/lossless_sse2.c
        libwebp/dsp/upsampling.c
        libwebp/dsp/upsampling_neon.c
        libwebp/dsp/upsampling_sse2.c
        libwebp/dsp/yuv.c
        libwebp/dsp/yuv_mips32.c
        libwebp/dsp/yuv_sse2.c
        libwebp/enc/alpha.c
        libwebp/enc/analysis.c
        libwebp/enc/backward_references.c
        libwebp/enc/config.c
        libwebp/enc/cost.c
        libwebp/enc/filter.c
        libwebp/enc/frame.c
        libwebp/enc/histogram.c
        libwebp/enc/iterator.c
        libwebp/enc/picture.c
        libwebp/enc/picture_csp.c
        libwebp/enc/picture_psnr.c
        libwebp/enc/picture_rescale.c
        libwebp/enc/picture_tools.c
        libwebp/enc/quant.c
        libwebp/enc/syntax.c
        libwebp/enc/token.c
        libwebp/enc/tree.c
        libwebp/enc/vp8l.c
        libwebp/enc/webpenc.c
        libwebp/utils/bit_reader.c
        libwebp/utils/bit_writer.c
        libwebp/utils/color_cache.c
        libwebp/utils/filters.c
        libwebp/utils/huffman.c
        libwebp/utils/huffman_encode.c
        libwebp/utils/quant_levels.c
        libwebp/utils/quant_levels_dec.c
        libwebp/utils/random.c
        libwebp/utils/rescaler.c
        libwebp/utils/thread.c
        libwebp/utils/utils.c
        )


add_library(webp
        STATIC
        ${src_webp}
        )

target_link_libraries(webp cpufeatures)

unset(CMAKE_CXX_FLAGS)
unset(CMAKE_C_FLAGS)

set(CMAKE_C_FLAGS "-w -std=c11 -Os -DNULL=0 -DSOCKLEN_T=socklen_t -DLOCALE_NOT_USED -D_LARGEFILE_SOURCE=1")
set(CMAKE_C_FLAGS "${CMAKE_C_FLAGS} -DANDROID_NDK -DDISABLE_IMPORTGL -fno-strict-aliasing -fprefetch-loop-arrays -DAVOID_TABLES -DANDROID_TILE_BASED_DECODE -DANDROID_ARMV6_IDCT -DHAVE_STRCHRNUL=0")
add_library(sqlite STATIC
        sqlite/sqlite3.c
        )

unset(CMAKE_CXX_FLAGS)
unset(CMAKE_C_FLAGS)

add_subdirectory(rlottie)

unset(CMAKE_CXX_FLAGS)
unset(CMAKE_C_FLAGS)

set(CMAKE_CXX_FLAGS "-frtti")
set(CMAKE_C_FLAGS "-DVERSION=1.3.1 -DFLAC__NO_MD5 -DFLAC__INTEGER_ONLY_LIBRARY -DFLAC__NO_ASM")
set(CMAKE_C_FLAGS "${CMAKE_C_FLAGS} -D_REENTRANT -DPIC -DU_COMMON_IMPLEMENTATION -fPIC -DHAVE_SYS_PARAM_H")
set(CMAKE_C_FLAGS "${CMAKE_C_FLAGS} -O3 -funroll-loops -finline-functions")
file(GLOB src_flac
        exoplayer/libFLAC/bitmath.c
        exoplayer/libFLAC/bitreader.c
        exoplayer/libFLAC/bitwriter.c
        exoplayer/libFLAC/cpu.c
        exoplayer/libFLAC/crc.c
        exoplayer/libFLAC/fixed.c
        exoplayer/libFLAC/fixed_intrin_sse2.c
        exoplayer/libFLAC/fixed_intrin_ssse3.c
        exoplayer/libFLAC/float.c
        exoplayer/libFLAC/format.c
        exoplayer/libFLAC/lpc.c
        exoplayer/libFLAC/lpc_intrin_avx2.c
        exoplayer/libFLAC/lpc_intrin_sse2.c
        exoplayer/libFLAC/lpc_intrin_sse41.c
        exoplayer/libFLAC/lpc_intrin_sse.c
        exoplayer/libFLAC/md5.c
        exoplayer/libFLAC/memory.c
        exoplayer/libFLAC/metadata_iterators.c
        exoplayer/libFLAC/metadata_object.c
        exoplayer/libFLAC/stream_decoder.c
        exoplayer/libFLAC/stream_encoder.c
        exoplayer/libFLAC/stream_encoder_framing.c
        exoplayer/libFLAC/stream_encoder_intrin_avx2.c
        exoplayer/libFLAC/stream_encoder_intrin_sse2.c
        exoplayer/libFLAC/stream_encoder_intrin_ssse3.c
        exoplayer/libFLAC/window.c
        )


add_library(flac STATIC
        ${src_flac}
        )

unset(CMAKE_CXX_FLAGS)
unset(CMAKE_C_FLAGS)
set(CMAKE_C_FLAGS "-w -std=c11 -Os -DNULL=0 -DSOCKLEN_T=socklen_t -DLOCALE_NOT_USED -D_LARGEFILE_SOURCE=1")
set(CMAKE_C_FLAGS "${CMAKE_C_FLAGS} -Drestrict= -DOPUS_BUILD -DHAVE_LRINT -DHAVE_LRINTF -DUSE_ALLOCA -DFIXED_POINT -DHAVE_LROUND -fno-math-errno")
set(CMAKE_C_FLAGS "${CMAKE_C_FLAGS} -DANDROID_NDK -DDISABLE_IMPORTGL -fno-strict-aliasing -fprefetch-loop-arrays -DAVOID_TABLES -DANDROID_TILE_BASED_DECODE -DANDROID_ARMV6_IDCT -D__STDC_CONSTANT_MACROS")
set(CMAKE_CXX_FLAGS "-DBSD=1 -ffast-math -Os -funroll-loops -std=c++14")

include_directories(
        opus/include
        opus/silk
        opus/silk/fixed
        opus/celt
        opus/
        opus/opusfile
        third_party/libyuv/include
        ffmpeg/include
        exoplayer/include
        exoplayer/libFLAC/include
        rlottie/inc
        lz4
)

file(GLOB opus_main
        opus/src/opus.c
        opus/src/opus_decoder.c
        opus/src/opus_encoder.c
        opus/src/opus_multistream.c
        opus/src/opus_multistream_encoder.c
        opus/src/opus_multistream_decoder.c
        opus/src/repacketizer.c
        opus/src/analysis.c
        opus/src/mlp.c
        opus/src/mlp_data.c
        opus/src/opus_projection_encoder.c
        opus/src/opus_projection_decoder.c
        opus/src/mapping_matrix.c
        )


if (${ANDROID_ABI} STREQUAL ("arm64-v8a" OR "armeabi-v7a"))
    set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} -DLIBYUV_NEON")
    set(CMAKE_C_FLAGS "${CMAKE_C_FLAGS} -DLIBYUV_NEON")
    set(CMAKE_C_FLAGS "${CMAKE_C_FLAGS} -DOPUS_HAVE_RTCD -DOPUS_ARM_ASM")
    file(GLOB_RECURSE opus_arm
            opus/celt/arm/celt_neon_intr.c
            opus/celt/arm/pitch_neon_intr.c
            opus/silk/arm/NSQ_neon.c
            opus/silk/arm/arm_silk_map.c
            opus/silk/arm/LPC_inv_pred_gain_neon_intr.c
            opus/silk/arm/NSQ_del_dec_neon_intr.c
            opus/silk/arm/biquad_alt_neon_intr.c
            opus/silk/fixed/arm/warped_autocorrelation_FIX_neon_intr.c
            )

elseif (${ANDROID_ABI} STREQUAL "x86")
    set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} -Dx86fix")
    set(CMAKE_C_FLAGS "${CMAKE_C_FLAGS} -Dx86fix")
endif ()

file(GLOB src_opus_silk
        opus/silk/CNG.c
        opus/silk/code_signs.c
        opus/silk/init_decoder.c
        opus/silk/decode_core.c
        opus/silk/decode_frame.c
        opus/silk/decode_parameters.c
        opus/silk/decode_indices.c
        opus/silk/decode_pulses.c
        opus/silk/decoder_set_fs.c
        opus/silk/dec_API.c
        opus/silk/enc_API.c
        opus/silk/encode_indices.c
        opus/silk/encode_pulses.c
        opus/silk/gain_quant.c
        opus/silk/interpolate.c
        opus/silk/LP_variable_cutoff.c
        opus/silk/NLSF_decode.c
        opus/silk/NSQ.c
        opus/silk/NSQ_del_dec.c
        opus/silk/PLC.c
        opus/silk/shell_coder.c
        opus/silk/tables_gain.c
        opus/silk/tables_LTP.c
        opus/silk/tables_NLSF_CB_NB_MB.c
        opus/silk/tables_NLSF_CB_WB.c
        opus/silk/tables_other.c
        opus/silk/tables_pitch_lag.c
        opus/silk/tables_pulses_per_block.c
        opus/silk/VAD.c
        opus/silk/control_audio_bandwidth.c
        opus/silk/quant_LTP_gains.c
        opus/silk/VQ_WMat_EC.c
        opus/silk/HP_variable_cutoff.c
        opus/silk/NLSF_encode.c
        opus/silk/NLSF_VQ.c
        opus/silk/NLSF_unpack.c
        opus/silk/NLSF_del_dec_quant.c
        opus/silk/process_NLSFs.c
        opus/silk/stereo_LR_to_MS.c
        opus/silk/stereo_MS_to_LR.c
        opus/silk/check_control_input.c
        opus/silk/control_SNR.c
        opus/silk/init_encoder.c
        opus/silk/control_codec.c
        opus/silk/A2NLSF.c
        opus/silk/ana_filt_bank_1.c
        opus/silk/biquad_alt.c
        opus/silk/bwexpander_32.c
        opus/silk/bwexpander.c
        opus/silk/debug.c
        opus/silk/decode_pitch.c
        opus/silk/inner_prod_aligned.c
        opus/silk/lin2log.c
        opus/silk/log2lin.c
        opus/silk/LPC_analysis_filter.c
        opus/silk/LPC_inv_pred_gain.c
        opus/silk/table_LSF_cos.c
        opus/silk/NLSF2A.c
        opus/silk/NLSF_stabilize.c
        opus/silk/NLSF_VQ_weights_laroia.c
        opus/silk/pitch_est_tables.c
        opus/silk/resampler.c
        opus/silk/resampler_down2_3.c
        opus/silk/resampler_down2.c
        opus/silk/resampler_private_AR2.c
        opus/silk/resampler_private_down_FIR.c
        opus/silk/resampler_private_IIR_FIR.c
        opus/silk/resampler_private_up2_HQ.c
        opus/silk/resampler_rom.c
        opus/silk/sigm_Q15.c
        opus/silk/sort.c
        opus/silk/sum_sqr_shift.c
        opus/silk/stereo_decode_pred.c
        opus/silk/stereo_encode_pred.c
        opus/silk/stereo_find_predictor.c
        opus/silk/stereo_quant_pred.c
        opus/silk/LPC_fit.c
        )


file(GLOB src_opus_fixed
        opus/silk/fixed/LTP_analysis_filter_FIX.c
        opus/silk/fixed/LTP_scale_ctrl_FIX.c
        opus/silk/fixed/corrMatrix_FIX.c
        opus/silk/fixed/encode_frame_FIX.c
        opus/silk/fixed/find_LPC_FIX.c
        opus/silk/fixed/find_LTP_FIX.c
        opus/silk/fixed/find_pitch_lags_FIX.c
        opus/silk/fixed/find_pred_coefs_FIX.c
        opus/silk/fixed/noise_shape_analysis_FIX.c
        opus/silk/fixed/process_gains_FIX.c
        opus/silk/fixed/regularize_correlations_FIX.c
        opus/silk/fixed/residual_energy16_FIX.c
        opus/silk/fixed/residual_energy_FIX.c
        opus/silk/fixed/warped_autocorrelation_FIX.c
        opus/silk/fixed/apply_sine_window_FIX.c
        opus/silk/fixed/autocorr_FIX.c
        opus/silk/fixed/burg_modified_FIX.c
        opus/silk/fixed/k2a_FIX.c
        opus/silk/fixed/k2a_Q16_FIX.c
        opus/silk/fixed/pitch_analysis_core_FIX.c
        opus/silk/fixed/vector_ops_FIX.c
        opus/silk/fixed/schur64_FIX.c
        opus/silk/fixed/schur_FIX.c
        )

file(GLOB src_opus_celt
        opus/celt/bands.c
        opus/celt/celt.c
        opus/celt/celt_encoder.c
        opus/celt/celt_decoder.c
        opus/celt/cwrs.c
        opus/celt/entcode.c
        opus/celt/entdec.c
        opus/celt/entenc.c
        opus/celt/kiss_fft.c
        opus/celt/laplace.c
        opus/celt/mathops.c
        opus/celt/mdct.c
        opus/celt/modes.c
        opus/celt/pitch.c
        opus/celt/celt_lpc.c
        opus/celt/quant_bands.c
        opus/celt/rate.c
        opus/celt/vq.c
        opus/celt/arm/armcpu.c
        opus/celt/arm/arm_celt_map.c
        )

file(GLOB src_opus_file
        opus/ogg/bitwise.c
        opus/ogg/framing.c
        opus/opusfile/info.c
        opus/opusfile/internal.c
        opus/opusfile/opusfile.c
        opus/opusfile/stream.c
        )

file(GLOB src_libyuv
        third_party/libyuv/source/compare_common.cc
        third_party/libyuv/source/compare_gcc.cc
        third_party/libyuv/source/compare_neon64.cc
        third_party/libyuv/source/compare_win.cc
        third_party/libyuv/source/compare.cc
        third_party/libyuv/source/convert_argb.cc
        third_party/libyuv/source/convert_from_argb.cc
        third_party/libyuv/source/convert_from.cc
        third_party/libyuv/source/convert_jpeg.cc
        third_party/libyuv/source/convert_to_argb.cc
        third_party/libyuv/source/convert_to_i420.cc
        third_party/libyuv/source/convert.cc
        third_party/libyuv/source/cpu_id.cc
        third_party/libyuv/source/mjpeg_decoder.cc
        third_party/libyuv/source/mjpeg_validate.cc
        third_party/libyuv/source/planar_functions.cc
        third_party/libyuv/source/rotate_any.cc
        third_party/libyuv/source/rotate_argb.cc
        third_party/libyuv/source/rotate_common.cc
        third_party/libyuv/source/rotate_gcc.cc
        third_party/libyuv/source/rotate_neon64.cc
        third_party/libyuv/source/rotate_win.cc
        third_party/libyuv/source/rotate.cc
        third_party/libyuv/source/row_any.cc
        third_party/libyuv/source/row_common.cc
        third_party/libyuv/source/row_gcc.cc
        third_party/libyuv/source/row_neon64.cc
        third_party/libyuv/source/row_win.cc
        third_party/libyuv/source/scale_any.cc
        third_party/libyuv/source/scale_argb.cc
        third_party/libyuv/source/scale_common.cc
        third_party/libyuv/source/scale_gcc.cc
        third_party/libyuv/source/scale_neon64.cc
        third_party/libyuv/source/scale_win.cc
        third_party/libyuv/source/scale.cc
        third_party/libyuv/source/video_common.cc
        )

if (${ANDROID_ABI} STREQUAL "armeabi-v7a")
    add_definitions(-DLIBYUV_NEON)
    file(GLOB src_libyuv_neon
            third_party/libyuv/source/compare_neon.cc
            third_party/libyuv/source/rotate_neon.cc
            third_party/libyuv/source/row_neon.cc
            third_party/libyuv/source/scale_neon.cc
            )
endif ()

file(GLOB src_main
        jni.c
        audio.c
        image.cpp
        video.c
        intro/IntroRenderer.c
        utilities.cpp
        gifvideo.cpp
        lottie.cpp
        SqliteWrapper.cpp
        TgNetWrapper.cpp
        NativeLoader.cpp
        exoplayer/flac_jni.cc
        exoplayer/flac_parser.cc
        exoplayer/opus_jni.cc
        exoplayer/ffmpeg_jni.cc
        fast-edge.cpp
        genann.c
        secureid_ocr.cpp
        )


add_library(tmessages.32
        SHARED
        ${src_main}
        ${src_opus_file}
        ${src_opus_celt}
        ${src_opus_fixed}
        ${src_opus_silk}
        ${opus_arm}
        ${opus_main}
        ${src_libyuv}
        ${src_libyuv_neon}
        )

target_link_libraries(
        tmessages.32
        log
        z
        jnigraphics
        EGL
        GLESv2
        android
        OpenSLES
        swscale
        avformat
        avcodec
        avresample
        avutil
        flac
        tgnet
        tgcalls
        tgvoip
        webp
        sqlite
        rlottie
        lz4
)

