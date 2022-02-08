cmake_minimum_required(VERSION 3.6.0)

set(CMAKE_CXX_FLAGS "-std=c++14 -DANDROID -g")
set(CMAKE_C_FLAGS "-w -std=c11 -DANDROID -D_LARGEFILE_SOURCE=1 -g")
set(CMAKE_ASM_FLAGS "${CFLAGS} -x assembler-with-cpp")
set(CMAKE_C_FLAGS "${CMAKE_C_FLAGS} -ffunction-sections -fdata-sections")
set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} -ffunction-sections -fdata-sections")
set(CMAKE_SHARED_LINKER_FLAGS "${CMAKE_SHARED_LINKER_FLAGS} -Wl,--exclude-libs,libtgvoip.a,libtgcalls.a,libtgcalls_tp.a,libtgnet.a,liblz4.a,libwebp.a,libflac.a,librlottie.a,libsqlite.a,
${CMAKE_HOME_DIRECTORY}/ffmpeg/build/${ANDROID_ABI}/lib/libswscale.a,
${CMAKE_HOME_DIRECTORY}/ffmpeg/build/${ANDROID_ABI}/lib/libavformat.a,
${CMAKE_HOME_DIRECTORY}/ffmpeg/build/${ANDROID_ABI}/lib/libavcodec.a,
${CMAKE_HOME_DIRECTORY}/ffmpeg/build/${ANDROID_ABI}/lib/libavresample.a,
${CMAKE_HOME_DIRECTORY}/ffmpeg/build/${ANDROID_ABI}/lib/libavutil.a,
${CMAKE_HOME_DIRECTORY}/ffmpeg/build/${ANDROID_ABI}/lib/libswresample.a,
${CMAKE_HOME_DIRECTORY}/libvpx/build/${ANDROID_ABI}/lib/libvpx.a,
${CMAKE_HOME_DIRECTORY}/boringssl/build/${ANDROID_ABI}/ssl/libssl.a,
${CMAKE_HOME_DIRECTORY}/boringssl/build/${ANDROID_ABI}/crypto/libcrypto.a")

if (${ANDROID_ABI} STREQUAL "armeabi-v7a" OR ${ANDROID_ABI} STREQUAL "arm64-v8a")
    enable_language(ASM)
else()
    enable_language(ASM_NASM)
endif()

add_library(avutil STATIC IMPORTED)
set_target_properties(avutil PROPERTIES IMPORTED_LOCATION ${CMAKE_HOME_DIRECTORY}/ffmpeg/build/${ANDROID_ABI}/lib/libavutil.a)

add_library(avformat STATIC IMPORTED)
set_target_properties(avformat PROPERTIES IMPORTED_LOCATION ${CMAKE_HOME_DIRECTORY}/ffmpeg/build/${ANDROID_ABI}/lib/libavformat.a)

add_library(avcodec STATIC IMPORTED)
set_target_properties(avcodec PROPERTIES IMPORTED_LOCATION ${CMAKE_HOME_DIRECTORY}/ffmpeg/build/${ANDROID_ABI}/lib/libavcodec.a)

add_library(avresample STATIC IMPORTED)
set_target_properties(avresample PROPERTIES IMPORTED_LOCATION ${CMAKE_HOME_DIRECTORY}/ffmpeg/build/${ANDROID_ABI}/lib/libavresample.a)

add_library(swresample STATIC IMPORTED)
set_target_properties(swresample PROPERTIES IMPORTED_LOCATION ${CMAKE_HOME_DIRECTORY}/ffmpeg/build/${ANDROID_ABI}/lib/libswresample.a)

add_library(swscale STATIC IMPORTED)
set_target_properties(swscale PROPERTIES IMPORTED_LOCATION ${CMAKE_HOME_DIRECTORY}/ffmpeg/build/${ANDROID_ABI}/lib/libswscale.a)

add_library(crypto STATIC IMPORTED)
set_target_properties(crypto PROPERTIES IMPORTED_LOCATION ${CMAKE_HOME_DIRECTORY}/boringssl/build/${ANDROID_ABI}/crypto/libcrypto.a)

add_library(ssl STATIC IMPORTED)
set_target_properties(ssl PROPERTIES IMPORTED_LOCATION ${CMAKE_HOME_DIRECTORY}/boringssl/build/${ANDROID_ABI}/ssl/libssl.a)

add_library(libvpx STATIC IMPORTED)
set_target_properties(libvpx PROPERTIES IMPORTED_LOCATION ${CMAKE_HOME_DIRECTORY}/libvpx/build/${ANDROID_ABI}/lib/libvpx.a)


#tgnet
add_library(mozjpeg STATIC
        mozjpeg/cjpeg.c mozjpeg/cdjpeg.c mozjpeg/rdgif.c mozjpeg/rdppm.c mozjpeg/rdjpeg.c mozjpeg/rdswitch.c mozjpeg/rdbmp.c
        mozjpeg/rdtarga.c mozjpeg/wrbmp.c mozjpeg/wrtarga.c mozjpeg/djpeg.c mozjpeg/cdjpeg.c mozjpeg/rdcolmap.c mozjpeg/rdswitch.c
        mozjpeg/wrgif.c mozjpeg/wrppm.c mozjpeg/jpegtran.c mozjpeg/cdjpeg.c mozjpeg/rdswitch.c mozjpeg/transupp.c mozjpeg/turbojpeg.c
        mozjpeg/jdatadst-tj.c mozjpeg/jdatasrc-tj.c mozjpeg/jcapimin.c mozjpeg/jcapistd.c mozjpeg/jccoefct.c mozjpeg/jccolor.c
        mozjpeg/jcdctmgr.c mozjpeg/jchuff.c mozjpeg/jcext.c mozjpeg/jcicc.c mozjpeg/jcinit.c mozjpeg/jcmainct.c mozjpeg/jcmarker.c
        mozjpeg/jcmaster.c mozjpeg/jcomapi.c mozjpeg/jcparam.c mozjpeg/jcphuff.c mozjpeg/jcprepct.c mozjpeg/jcsample.c
        mozjpeg/jctrans.c mozjpeg/jdapimin.c mozjpeg/jdapistd.c mozjpeg/jdatadst.c mozjpeg/jdatasrc.c
        mozjpeg/jdcoefct.c mozjpeg/jdcolor.c mozjpeg/jddctmgr.c mozjpeg/jdhuff.c mozjpeg/jdicc.c mozjpeg/jdinput.c
        mozjpeg/jdmainct.c mozjpeg/jdmarker.c mozjpeg/jdmaster.c mozjpeg/jdmerge.c mozjpeg/jdphuff.c mozjpeg/jdpostct.c mozjpeg/jdsample.c
        mozjpeg/jdtrans.c mozjpeg/jerror.c mozjpeg/jfdctflt.c mozjpeg/jfdctfst.c mozjpeg/jfdctint.c mozjpeg/jidctflt.c mozjpeg/jidctfst.c
        mozjpeg/jidctint.c mozjpeg/jidctred.c mozjpeg/jquant1.c mozjpeg/jquant2.c mozjpeg/jutils.c mozjpeg/jmemmgr.c mozjpeg/jmemnobs.c)
target_compile_options(mozjpeg PUBLIC
        -Os)
set_target_properties(mozjpeg PROPERTIES
        ANDROID_ARM_MODE arm)
target_compile_definitions(mozjpeg PUBLIC
        BMP_SUPPORTED GIF_SUPPORTED PPM_SUPPORTED TARGA_SUPPORTED)
target_include_directories(mozjpeg PUBLIC
        mozjpeg
        mozjpeg/simd/nasm)

if (${ANDROID_ABI} STREQUAL "armeabi-v7a")
    target_sources(mozjpeg PRIVATE
            mozjpeg/simd/arm/jsimd_neon.S
            mozjpeg/simd/arm/jsimd.c)
    target_compile_definitions(mozjpeg PUBLIC
            SIZEOF_SIZE_T=4)
elseif(${ANDROID_ABI} STREQUAL "arm64-v8a")
    target_sources(mozjpeg PRIVATE
            mozjpeg/simd/arm64/jsimd_neon.S
            mozjpeg/simd/arm64/jsimd.c)
    target_compile_definitions(mozjpeg PUBLIC
            SIZEOF_SIZE_T=8)
elseif(${ANDROID_ABI} STREQUAL "x86")
    set(CMAKE_ASM_NASM_COMPILER_ARG1 "${CMAKE_ASM_NASM_COMPILER_ARG1} -DPIC")
    target_sources(mozjpeg PRIVATE
            mozjpeg/simd/i386/jsimd.c
            mozjpeg/simd/i386/jsimdcpu.asm
            mozjpeg/simd/i386/jfdctflt-3dn.asm
            mozjpeg/simd/i386/jidctflt-3dn.asm
            mozjpeg/simd/i386/jquant-3dn.asm
            mozjpeg/simd/i386/jccolor-mmx.asm
            mozjpeg/simd/i386/jcgray-mmx.asm
            mozjpeg/simd/i386/jcsample-mmx.asm
            mozjpeg/simd/i386/jdcolor-mmx.asm
            mozjpeg/simd/i386/jdmerge-mmx.asm
            mozjpeg/simd/i386/jdsample-mmx.asm
            mozjpeg/simd/i386/jfdctfst-mmx.asm
            mozjpeg/simd/i386/jfdctint-mmx.asm
            mozjpeg/simd/i386/jidctfst-mmx.asm
            mozjpeg/simd/i386/jidctint-mmx.asm
            mozjpeg/simd/i386/jidctred-mmx.asm
            mozjpeg/simd/i386/jquant-mmx.asm
            mozjpeg/simd/i386/jfdctflt-sse.asm
            mozjpeg/simd/i386/jidctflt-sse.asm
            mozjpeg/simd/i386/jquant-sse.asm
            mozjpeg/simd/i386/jccolor-sse2.asm
            mozjpeg/simd/i386/jcgray-sse2.asm
            mozjpeg/simd/i386/jchuff-sse2.asm
            mozjpeg/simd/i386/jcphuff-sse2.asm
            mozjpeg/simd/i386/jcsample-sse2.asm
            mozjpeg/simd/i386/jdcolor-sse2.asm
            mozjpeg/simd/i386/jdmerge-sse2.asm
            mozjpeg/simd/i386/jdsample-sse2.asm
            mozjpeg/simd/i386/jfdctfst-sse2.asm
            mozjpeg/simd/i386/jfdctint-sse2.asm
            mozjpeg/simd/i386/jidctflt-sse2.asm
            mozjpeg/simd/i386/jidctfst-sse2.asm
            mozjpeg/simd/i386/jidctint-sse2.asm
            mozjpeg/simd/i386/jidctred-sse2.asm
            mozjpeg/simd/i386/jquantf-sse2.asm
            mozjpeg/simd/i386/jquanti-sse2.asm
            mozjpeg/simd/i386/jccolor-avx2.asm
            mozjpeg/simd/i386/jcgray-avx2.asm
            mozjpeg/simd/i386/jcsample-avx2.asm
            mozjpeg/simd/i386/jdcolor-avx2.asm
            mozjpeg/simd/i386/jdmerge-avx2.asm
            mozjpeg/simd/i386/jdsample-avx2.asm
            mozjpeg/simd/i386/jfdctint-avx2.asm
            mozjpeg/simd/i386/jidctint-avx2.asm
            mozjpeg/simd/i386/jquanti-avx2.asm)
    target_compile_definitions(mozjpeg PUBLIC
            SIZEOF_SIZE_T=4)
elseif(${ANDROID_ABI} STREQUAL "x86_64")
    set(CMAKE_ASM_NASM_COMPILER_ARG1 "${CMAKE_ASM_NASM_COMPILER_ARG1} -DPIC")
    set(CMAKE_ASM_NASM_FLAGS "${CMAKE_ASM_NASM_FLAGS} -DELF")
    set(CMAKE_ASM_NASM_DEBUG_FORMAT "dwarf2")
    set(CMAKE_ASM_NASM_FLAGS "${CMAKE_ASM_NASM_FLAGS} -D__x86_64__")
    target_sources(mozjpeg PRIVATE
            mozjpeg/simd/x86_64/jsimd.c
            mozjpeg/simd/x86_64/jsimdcpu.asm
            mozjpeg/simd/x86_64/jfdctflt-sse.asm
            mozjpeg/simd/x86_64/jccolor-sse2.asm
            mozjpeg/simd/x86_64/jcgray-sse2.asm
            mozjpeg/simd/x86_64/jchuff-sse2.asm
            mozjpeg/simd/x86_64/jcphuff-sse2.asm
            mozjpeg/simd/x86_64/jcsample-sse2.asm
            mozjpeg/simd/x86_64/jdcolor-sse2.asm
            mozjpeg/simd/x86_64/jdmerge-sse2.asm
            mozjpeg/simd/x86_64/jdsample-sse2.asm
            mozjpeg/simd/x86_64/jfdctfst-sse2.asm
            mozjpeg/simd/x86_64/jfdctint-sse2.asm
            mozjpeg/simd/x86_64/jidctflt-sse2.asm
            mozjpeg/simd/x86_64/jidctfst-sse2.asm
            mozjpeg/simd/x86_64/jidctint-sse2.asm
            mozjpeg/simd/x86_64/jidctred-sse2.asm
            mozjpeg/simd/x86_64/jquantf-sse2.asm
            mozjpeg/simd/x86_64/jquanti-sse2.asm
            mozjpeg/simd/x86_64/jccolor-avx2.asm
            mozjpeg/simd/x86_64/jcgray-avx2.asm
            mozjpeg/simd/x86_64/jcsample-avx2.asm
            mozjpeg/simd/x86_64/jdcolor-avx2.asm
            mozjpeg/simd/x86_64/jdmerge-avx2.asm
            mozjpeg/simd/x86_64/jdsample-avx2.asm
            mozjpeg/simd/x86_64/jfdctint-avx2.asm
            mozjpeg/simd/x86_64/jidctint-avx2.asm
            mozjpeg/simd/x86_64/jquanti-avx2.asm)
    target_compile_definitions(mozjpeg PUBLIC
            SIZEOF_SIZE_T=8)
endif()

#tgnet
add_library(tgnet STATIC
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
        tgnet/Config.cpp)
target_compile_options(tgnet PUBLIC
        -Wall -frtti -finline-functions -ffast-math -Os)
set_target_properties(tgnet PROPERTIES
        ANDROID_ARM_MODE arm)
target_compile_definitions(tgnet PUBLIC
        HAVE_PTHREAD)
target_include_directories(tgnet PUBLIC
        boringssl/include/)
target_link_libraries(tgnet
        crypto)

#rlottie
add_library(rlottie STATIC
        rlottie/src/lottie/lottieanimation.cpp
        rlottie/src/lottie/lottieitem.cpp
        rlottie/src/lottie/lottiekeypath.cpp
        rlottie/src/lottie/lottieloader.cpp
        rlottie/src/lottie/lottiemodel.cpp
        rlottie/src/lottie/lottieparser.cpp
        rlottie/src/lottie/lottieproxymodel.cpp
        rlottie/src/vector/freetype/v_ft_math.cpp
        rlottie/src/vector/freetype/v_ft_raster.cpp
        rlottie/src/vector/freetype/v_ft_stroker.cpp
        rlottie/src/vector/pixman/vregion.cpp
        rlottie/src/vector/stb/stb_image.cpp
        rlottie/src/vector/vbezier.cpp
        rlottie/src/vector/vbitmap.cpp
        rlottie/src/vector/vbrush.cpp
        rlottie/src/vector/vcompositionfunctions.cpp
        rlottie/src/vector/vdasher.cpp
        rlottie/src/vector/vdebug.cpp
        rlottie/src/vector/vdrawable.cpp
        rlottie/src/vector/vdrawhelper.cpp
        rlottie/src/vector/vdrawhelper_neon.cpp
        rlottie/src/vector/velapsedtimer.cpp
        rlottie/src/vector/vimageloader.cpp
        rlottie/src/vector/vinterpolator.cpp
        rlottie/src/vector/vmatrix.cpp
        rlottie/src/vector/vpainter.cpp
        rlottie/src/vector/vpath.cpp
        rlottie/src/vector/vpathmesure.cpp
        rlottie/src/vector/vraster.cpp
        rlottie/src/vector/vrect.cpp
        rlottie/src/vector/vrle.cpp)
target_compile_options(rlottie PUBLIC
        -Wall -fno-rtti -finline-functions -ffast-math -Os -fno-unwind-tables -fno-asynchronous-unwind-tables -Wnon-virtual-dtor -Woverloaded-virtual -Wno-unused-parameter -fvisibility=hidden)
set_target_properties(rlottie PROPERTIES
        ANDROID_ARM_MODE arm)
target_compile_definitions(rlottie PUBLIC
        HAVE_PTHREAD NDEBUG)
target_include_directories(rlottie PUBLIC
        ./
        rlottie/inc
        rlottie/src/vector/
        rlottie/src/vector/pixman
        rlottie/src/vector/freetype
        rlottie/src/vector/stb)

if (${ANDROID_ABI} STREQUAL "armeabi-v7a")
    target_compile_options(rlottie PUBLIC
            -fno-integrated-as)
    target_compile_definitions(rlottie PUBLIC
            USE_ARM_NEON)
    target_sources(rlottie PRIVATE
            rlottie/src/vector/pixman/pixman-arm-neon-asm.S)
elseif(${ANDROID_ABI} STREQUAL "arm64-v8a")
    target_compile_options(rlottie PUBLIC
            -fno-integrated-as)
    target_compile_definitions(rlottie PUBLIC
            USE_ARM_NEON __ARM64_NEON__)
    target_sources(rlottie PRIVATE
            rlottie/src/vector/pixman/pixman-arma64-neon-asm.S)
endif()

#flac
add_library(flac STATIC
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
        exoplayer/libFLAC/window.c)
target_compile_options(flac PUBLIC
        -O3 -funroll-loops -finline-functions -fPIC -frtti)
set_target_properties(flac PROPERTIES
        ANDROID_ARM_MODE arm)
target_compile_definitions(flac PUBLIC
        _REENTRANT PIC U_COMMON_IMPLEMENTATION HAVE_SYS_PARAM_H VERSION="1.3.1" FLAC__NO_MD5 FLAC__INTEGER_ONLY_LIBRARY FLAC__NO_ASM)
target_include_directories(flac PUBLIC
        exoplayer/libFLAC/include)

#webp
add_library(webp STATIC
        libwebp/src/dec/alpha_dec.c
        libwebp/src/dec/buffer_dec.c
        libwebp/src/dec/frame_dec.c
        libwebp/src/dec/idec_dec.c
        libwebp/src/dec/io_dec.c
        libwebp/src/dec/quant_dec.c
        libwebp/src/dec/tree_dec.c
        libwebp/src/dec/vp8_dec.c
        libwebp/src/dec/vp8l_dec.c
        libwebp/src/dec/webp_dec.c
        libwebp/src/demux/anim_decode.c
        libwebp/src/demux/demux.c
        libwebp/src/dsp/alpha_processing.c
        libwebp/src/dsp/alpha_processing_mips_dsp_r2.c
        libwebp/src/dsp/alpha_processing_neon.c
        libwebp/src/dsp/alpha_processing_sse2.c
        libwebp/src/dsp/alpha_processing_sse41.c
        libwebp/src/dsp/cpu.c
        libwebp/src/dsp/dec.c
        libwebp/src/dsp/dec_clip_tables.c
        libwebp/src/dsp/dec_mips32.c
        libwebp/src/dsp/dec_mips_dsp_r2.c
        libwebp/src/dsp/dec_msa.c
        libwebp/src/dsp/dec_neon.c
        libwebp/src/dsp/dec_sse2.c
        libwebp/src/dsp/dec_sse41.c
        libwebp/src/dsp/filters.c
        libwebp/src/dsp/filters_mips_dsp_r2.c
        libwebp/src/dsp/filters_msa.c
        libwebp/src/dsp/filters_neon.c
        libwebp/src/dsp/filters_sse2.c
        libwebp/src/dsp/lossless.c
        libwebp/src/dsp/lossless_mips_dsp_r2.c
        libwebp/src/dsp/lossless_msa.c
        libwebp/src/dsp/lossless_neon.c
        libwebp/src/dsp/lossless_sse2.c
        libwebp/src/dsp/rescaler.c
        libwebp/src/dsp/rescaler_mips32.c
        libwebp/src/dsp/rescaler_mips_dsp_r2.c
        libwebp/src/dsp/rescaler_msa.c
        libwebp/src/dsp/rescaler_neon.c
        libwebp/src/dsp/rescaler_sse2.c
        libwebp/src/dsp/upsampling.c
        libwebp/src/dsp/upsampling_mips_dsp_r2.c
        libwebp/src/dsp/upsampling_msa.c
        libwebp/src/dsp/upsampling_neon.c
        libwebp/src/dsp/upsampling_sse2.c
        libwebp/src/dsp/upsampling_sse41.c
        libwebp/src/dsp/yuv.c
        libwebp/src/dsp/yuv_mips32.c
        libwebp/src/dsp/yuv_mips_dsp_r2.c
        libwebp/src/dsp/yuv_neon.c
        libwebp/src/dsp/yuv_sse2.c
        libwebp/src/dsp/yuv_sse41.c
        libwebp/src/dsp/cost.c
        libwebp/src/dsp/cost_mips32.c
        libwebp/src/dsp/cost_mips_dsp_r2.c
        libwebp/src/dsp/cost_neon.c
        libwebp/src/dsp/cost_sse2.c
        libwebp/src/dsp/enc.c
        libwebp/src/dsp/enc_mips32.c
        libwebp/src/dsp/enc_mips_dsp_r2.c
        libwebp/src/dsp/enc_msa.c
        libwebp/src/dsp/enc_neon.c
        libwebp/src/dsp/enc_sse2.c
        libwebp/src/dsp/enc_sse41.c
        libwebp/src/dsp/lossless_enc.c
        libwebp/src/dsp/lossless_enc_mips32.c
        libwebp/src/dsp/lossless_enc_mips_dsp_r2.c
        libwebp/src/dsp/lossless_enc_msa.c
        libwebp/src/dsp/lossless_enc_neon.c
        libwebp/src/dsp/lossless_enc_sse2.c
        libwebp/src/dsp/lossless_enc_sse41.c
        libwebp/src/dsp/ssim.c
        libwebp/src/dsp/ssim_sse2.c
        libwebp/src/enc/alpha_enc.c
        libwebp/src/enc/analysis_enc.c
        libwebp/src/enc/backward_references_cost_enc.c
        libwebp/src/enc/backward_references_enc.c
        libwebp/src/enc/config_enc.c
        libwebp/src/enc/cost_enc.c
        libwebp/src/enc/filter_enc.c
        libwebp/src/enc/frame_enc.c
        libwebp/src/enc/histogram_enc.c
        libwebp/src/enc/iterator_enc.c
        libwebp/src/enc/near_lossless_enc.c
        libwebp/src/enc/picture_enc.c
        libwebp/src/enc/picture_csp_enc.c
        libwebp/src/enc/picture_psnr_enc.c
        libwebp/src/enc/picture_rescale_enc.c
        libwebp/src/enc/picture_tools_enc.c
        libwebp/src/enc/predictor_enc.c
        libwebp/src/enc/quant_enc.c
        libwebp/src/enc/syntax_enc.c
        libwebp/src/enc/token_enc.c
        libwebp/src/enc/tree_enc.c
        libwebp/src/enc/vp8l_enc.c
        libwebp/src/enc/webp_enc.c
        libwebp/src/mux/anim_encode.c
        libwebp/src/mux/muxedit.c
        libwebp/src/mux/muxinternal.c
        libwebp/src/mux/muxread.c
        libwebp/src/utils/bit_reader_utils.c
        libwebp/src/utils/color_cache_utils.c
        libwebp/src/utils/filters_utils.c
        libwebp/src/utils/huffman_utils.c
        libwebp/src/utils/quant_levels_dec_utils.c
        libwebp/src/utils/random_utils.c
        libwebp/src/utils/rescaler_utils.c
        libwebp/src/utils/thread_utils.c
        libwebp/src/utils/utils.c
        libwebp/src/utils/bit_writer_utils.c
        libwebp/src/utils/huffman_encode_utils.c
        libwebp/src/utils/quant_levels_utils.c)
target_compile_options(webp PUBLIC
        -Wall -finline-functions -ffast-math -Os)
set_target_properties(webp PROPERTIES
        ANDROID_ARM_MODE arm)
target_compile_definitions(webp PUBLIC
        HAVE_MALLOC_H HAVE_PTHREAD WEBP_USE_THREAD)
target_include_directories(webp PUBLIC
        libwebp libwebp/src)

#lz4
add_library(lz4 STATIC
        lz4/lz4.c
        lz4/lz4frame.c
        lz4/xxhash.c)
target_compile_options(lz4 PUBLIC
        -w -O3)
set_target_properties(lz4 PROPERTIES
        ANDROID_ARM_MODE arm)

#sqlite
add_library(sqlite STATIC
        sqlite/sqlite3.c)
target_compile_options(sqlite PUBLIC
        -fno-strict-aliasing -Os)
set_target_properties(sqlite PROPERTIES
        ANDROID_ARM_MODE arm)
target_compile_definitions(sqlite PUBLIC
        NULL=0 SOCKLEN_T=socklen_t LOCALE_NOT_USED ANDROID_NDK DISABLE_IMPORTGL AVOID_TABLES ANDROID_TILE_BASED_DECODE HAVE_STRCHRNUL=0 ANDROID_ARMV6_IDCT)

#voip
include(${CMAKE_HOME_DIRECTORY}/voip/CMakeLists.txt)

set(NATIVE_LIB "tmessages.41")

#tmessages
add_library(${NATIVE_LIB} SHARED
        jni.c
        audio.c
        image.cpp
        video.c
        intro/IntroRenderer.c
        SqliteWrapper.cpp
        utilities.cpp
        gifvideo.cpp
        lottie.cpp
        TgNetWrapper.cpp
        NativeLoader.cpp
        exoplayer/flac_jni.cc
        exoplayer/flac_parser.cc
        exoplayer/opus_jni.cc
        exoplayer/ffmpeg_jni.cc
        fast-edge.cpp
        genann.c
        secureid_ocr.cpp)

target_compile_options(${NATIVE_LIB} PUBLIC
        -ffast-math -Os -funroll-loops -ffast-math -fno-strict-aliasing -fno-math-errno)

target_compile_definitions(${NATIVE_LIB} PUBLIC
        BSD=1 NULL=0 SOCKLEN_T=socklen_t ANDROID_NDK DISABLE_IMPORTGL AVOID_TABLES ANDROID_TILE_BASED_DECODE __STDC_CONSTANT_MACROS ANDROID_ARMV6_IDCT OPUS_BUILD FIXED_POINT USE_ALLOCA restrict= __EMX__ LOCALE_NOT_USED HAVE_LRINT HAVE_LRINTF)

if (${ANDROID_ABI} STREQUAL "armeabi-v7a")
    set_target_properties(${NATIVE_LIB} PROPERTIES
            ANDROID_ARM_NEON FALSE
            ANDROID_ARM_MODE arm)
    target_compile_definitions(${NATIVE_LIB} PUBLIC
            ANDROID_ARM_NEON=false)
    target_sources(${NATIVE_LIB} PRIVATE
        third_party/libyuv/source/compare_neon.cc
        third_party/libyuv/source/rotate_neon.cc
        third_party/libyuv/source/row_neon.cc
        third_party/libyuv/source/scale_neon.cc)
else()
    set_target_properties(${NATIVE_LIB} PROPERTIES
            ANDROID_ARM_NEON FALSE
            ANDROID_ARM_MODE arm)
endif()

if (${ANDROID_ABI} STREQUAL "armeabi-v7a" OR ${ANDROID_ABI} STREQUAL "arm64-v8a")
    target_compile_definitions(${NATIVE_LIB} PUBLIC
            LIBYUV_NEON OPUS_HAVE_RTCD OPUS_ARM_ASM)

    target_sources(${NATIVE_LIB} PRIVATE
            opus/celt/arm/celt_neon_intr.c
            opus/celt/arm/pitch_neon_intr.c
            opus/silk/arm/NSQ_neon.c
            opus/silk/arm/arm_silk_map.c
            opus/silk/arm/LPC_inv_pred_gain_neon_intr.c
            opus/silk/arm/NSQ_del_dec_neon_intr.c
            opus/silk/arm/biquad_alt_neon_intr.c
            opus/silk/fixed/arm/warped_autocorrelation_FIX_neon_intr.c
            )

elseif(${ANDROID_ABI} STREQUAL "x86")
    target_compile_definitions(${NATIVE_LIB} PUBLIC
            x86fix)
endif()

target_sources(${NATIVE_LIB} PRIVATE

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

        opus/ogg/bitwise.c
        opus/ogg/framing.c
        opus/opusfile/info.c
        opus/opusfile/internal.c
        opus/opusfile/opusfile.c
        opus/opusfile/stream.c

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
        third_party/libyuv/source/scale_uv.cc)

target_include_directories(${NATIVE_LIB} PUBLIC
        opus/include
        opus/silk
        opus/silk/fixed
        opus/celt
        opus/
        opus/opusfile
        third_party/libyuv/include
        boringssl/include
        ffmpeg/build/${ANDROID_ABI}/include
        emoji
        exoplayer/include
        exoplayer/libFLAC/include
        intro
        rlottie/inc
        tgcalls/
        webrtc/
        lz4)

target_link_libraries(${NATIVE_LIB}
        -Wl,--whole-archive rnnoise openh264 voipandroid -Wl,--no-whole-archive
        tgvoip
        tgcalls
        tgcalls_tp
        mozjpeg
        tgnet
        lz4
        webp
        flac
        rlottie
        sqlite
        swscale
        avformat
        avcodec
        avresample
        swresample
        libvpx
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
        cpufeatures)

#if (${ANDROID_ABI} STREQUAL "x86" OR ${ANDROID_ABI} STREQUAL "x86_64")
#    target_link_libraries(${NATIVE_LIB}
#            -Wl,--whole-archive vpxasm -Wl,--no-whole-archive
#            c)
#endif()

include(AndroidNdkModules)
android_ndk_import_module_cpufeatures()
