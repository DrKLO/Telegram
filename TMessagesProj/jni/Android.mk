MY_LOCAL_PATH := $(call my-dir)
LOCAL_PATH := $(MY_LOCAL_PATH)

LOCAL_MODULE    := avutil 

ifeq ($(TARGET_ARCH_ABI),armeabi-v7a)
    LOCAL_SRC_FILES := ./ffmpeg/armv7-a/libavutil.a
else ifeq ($(TARGET_ARCH_ABI),arm64-v8a)
    LOCAL_SRC_FILES := ./ffmpeg/arm64/libavutil.a
else ifeq ($(TARGET_ARCH_ABI),x86)
    LOCAL_SRC_FILES := ./ffmpeg/i686/libavutil.a
else ifeq ($(TARGET_ARCH_ABI),x86_64)
    LOCAL_SRC_FILES := ./ffmpeg/x86_64/libavutil.a
endif

include $(PREBUILT_STATIC_LIBRARY)

include $(CLEAR_VARS)

LOCAL_MODULE    := avformat

ifeq ($(TARGET_ARCH_ABI),armeabi-v7a)
    LOCAL_SRC_FILES := ./ffmpeg/armv7-a/libavformat.a
else ifeq ($(TARGET_ARCH_ABI),arm64-v8a)
    LOCAL_SRC_FILES := ./ffmpeg/arm64/libavformat.a
else ifeq ($(TARGET_ARCH_ABI),x86)
    LOCAL_SRC_FILES := ./ffmpeg/i686/libavformat.a
else ifeq ($(TARGET_ARCH_ABI),x86_64)
    LOCAL_SRC_FILES := ./ffmpeg/x86_64/libavformat.a
endif

include $(PREBUILT_STATIC_LIBRARY)

include $(CLEAR_VARS)

LOCAL_MODULE    := avcodec

ifeq ($(TARGET_ARCH_ABI),armeabi-v7a)
    LOCAL_SRC_FILES := ./ffmpeg/armv7-a/libavcodec.a
else ifeq ($(TARGET_ARCH_ABI),arm64-v8a)
    LOCAL_SRC_FILES := ./ffmpeg/arm64/libavcodec.a
else ifeq ($(TARGET_ARCH_ABI),x86)
    LOCAL_SRC_FILES := ./ffmpeg/i686/libavcodec.a
else ifeq ($(TARGET_ARCH_ABI),x86_64)
    LOCAL_SRC_FILES := ./ffmpeg/x86_64/libavcodec.a
endif

include $(PREBUILT_STATIC_LIBRARY)

include $(CLEAR_VARS)

LOCAL_MODULE    := avresample

ifeq ($(TARGET_ARCH_ABI),armeabi-v7a)
    LOCAL_SRC_FILES := ./ffmpeg/armv7-a/libavresample.a
else ifeq ($(TARGET_ARCH_ABI),arm64-v8a)
    LOCAL_SRC_FILES := ./ffmpeg/arm64/libavresample.a
else ifeq ($(TARGET_ARCH_ABI),x86)
    LOCAL_SRC_FILES := ./ffmpeg/i686/libavresample.a
else ifeq ($(TARGET_ARCH_ABI),x86_64)
    LOCAL_SRC_FILES := ./ffmpeg/x86_64/libavresample.a
endif

include $(PREBUILT_STATIC_LIBRARY)

include $(CLEAR_VARS)

LOCAL_MODULE    := swscale

ifeq ($(TARGET_ARCH_ABI),armeabi-v7a)
    LOCAL_SRC_FILES := ./ffmpeg/armv7-a/libswscale.a
else ifeq ($(TARGET_ARCH_ABI),arm64-v8a)
    LOCAL_SRC_FILES := ./ffmpeg/arm64/libswscale.a
else ifeq ($(TARGET_ARCH_ABI),x86)
    LOCAL_SRC_FILES := ./ffmpeg/i686/libswscale.a
else ifeq ($(TARGET_ARCH_ABI),x86_64)
    LOCAL_SRC_FILES := ./ffmpeg/x86_64/libswscale.a
endif

include $(PREBUILT_STATIC_LIBRARY)

include $(CLEAR_VARS)

LOCAL_MODULE    := crypto

ifeq ($(TARGET_ARCH_ABI),armeabi-v7a)
    LOCAL_SRC_FILES := ./boringssl/lib/libcrypto_armeabi-v7a.a
else ifeq ($(TARGET_ARCH_ABI),arm64-v8a)
    LOCAL_SRC_FILES := ./boringssl/lib/libcrypto_arm64-v8a.a
else ifeq ($(TARGET_ARCH_ABI),x86)
    LOCAL_SRC_FILES := ./boringssl/lib/libcrypto_x86.a
else ifeq ($(TARGET_ARCH_ABI),x86_64)
    LOCAL_SRC_FILES := ./boringssl/lib/libcrypto_x86_64.a
endif

include $(PREBUILT_STATIC_LIBRARY)

include $(CLEAR_VARS)

TGVOIP_NATIVE_VERSION := 1.1
TGVOIP_ADDITIONAL_CFLAGS := -DTGVOIP_NO_VIDEO
include $(MY_LOCAL_PATH)/libtgvoip/Android.mk
LOCAL_PATH := $(MY_LOCAL_PATH) # restore local path after include
include $(CLEAR_VARS)

TGVOIP_NATIVE_VERSION := 3.1
TGVOIP_ADDITIONAL_CFLAGS := -DTGVOIP_NO_VIDEO
include $(MY_LOCAL_PATH)/libtgvoip3/Android.mk
LOCAL_PATH := $(MY_LOCAL_PATH) # restore local path after include
include $(CLEAR_VARS)

LOCAL_CPPFLAGS := -Wall -std=c++14 -DANDROID -frtti -DHAVE_PTHREAD -finline-functions -ffast-math -Os

LOCAL_C_INCLUDES += ./jni/boringssl/include/
LOCAL_ARM_MODE := arm
LOCAL_MODULE := tgnet
LOCAL_STATIC_LIBRARIES := crypto

LOCAL_SRC_FILES := \
./tgnet/ApiScheme.cpp \
./tgnet/BuffersStorage.cpp \
./tgnet/ByteArray.cpp \
./tgnet/ByteStream.cpp \
./tgnet/Connection.cpp \
./tgnet/ConnectionSession.cpp \
./tgnet/ConnectionsManager.cpp \
./tgnet/ConnectionSocket.cpp \
./tgnet/Datacenter.cpp \
./tgnet/EventObject.cpp \
./tgnet/FileLog.cpp \
./tgnet/MTProtoScheme.cpp \
./tgnet/NativeByteBuffer.cpp \
./tgnet/Request.cpp \
./tgnet/Timer.cpp \
./tgnet/TLObject.cpp \
./tgnet/ProxyCheckInfo.cpp \
./tgnet/Handshake.cpp \
./tgnet/Config.cpp

include $(BUILD_STATIC_LIBRARY)

include $(CLEAR_VARS)

LOCAL_CFLAGS := -Wall -DANDROID -DHAVE_MALLOC_H -DHAVE_PTHREAD -DWEBP_USE_THREAD -finline-functions -ffast-math -ffunction-sections -fdata-sections -Os
LOCAL_C_INCLUDES += ./jni/libwebp/src
LOCAL_ARM_MODE := arm
LOCAL_STATIC_LIBRARIES := cpufeatures
LOCAL_MODULE := webp

ifneq ($(findstring armeabi-v7a, $(TARGET_ARCH_ABI)),)
  NEON := c.neon
else
  NEON := c
endif

LOCAL_SRC_FILES := \
./libwebp/dec/alpha.c \
./libwebp/dec/buffer.c \
./libwebp/dec/frame.c \
./libwebp/dec/idec.c \
./libwebp/dec/io.c \
./libwebp/dec/quant.c \
./libwebp/dec/tree.c \
./libwebp/dec/vp8.c \
./libwebp/dec/vp8l.c \
./libwebp/dec/webp.c \
./libwebp/dsp/alpha_processing.c \
./libwebp/dsp/alpha_processing_sse2.c \
./libwebp/dsp/cpu.c \
./libwebp/dsp/dec.c \
./libwebp/dsp/dec_clip_tables.c \
./libwebp/dsp/dec_mips32.c \
./libwebp/dsp/dec_neon.$(NEON) \
./libwebp/dsp/dec_sse2.c \
./libwebp/dsp/enc.c \
./libwebp/dsp/enc_avx2.c \
./libwebp/dsp/enc_mips32.c \
./libwebp/dsp/enc_neon.$(NEON) \
./libwebp/dsp/enc_sse2.c \
./libwebp/dsp/lossless.c \
./libwebp/dsp/lossless_mips32.c \
./libwebp/dsp/lossless_neon.$(NEON) \
./libwebp/dsp/lossless_sse2.c \
./libwebp/dsp/upsampling.c \
./libwebp/dsp/upsampling_neon.$(NEON) \
./libwebp/dsp/upsampling_sse2.c \
./libwebp/dsp/yuv.c \
./libwebp/dsp/yuv_mips32.c \
./libwebp/dsp/yuv_sse2.c \
./libwebp/enc/alpha.c \
./libwebp/enc/analysis.c \
./libwebp/enc/backward_references.c \
./libwebp/enc/config.c \
./libwebp/enc/cost.c \
./libwebp/enc/filter.c \
./libwebp/enc/frame.c \
./libwebp/enc/histogram.c \
./libwebp/enc/iterator.c \
./libwebp/enc/picture.c \
./libwebp/enc/picture_csp.c \
./libwebp/enc/picture_psnr.c \
./libwebp/enc/picture_rescale.c \
./libwebp/enc/picture_tools.c \
./libwebp/enc/quant.c \
./libwebp/enc/syntax.c \
./libwebp/enc/token.c \
./libwebp/enc/tree.c \
./libwebp/enc/vp8l.c \
./libwebp/enc/webpenc.c \
./libwebp/utils/bit_reader.c \
./libwebp/utils/bit_writer.c \
./libwebp/utils/color_cache.c \
./libwebp/utils/filters.c \
./libwebp/utils/huffman.c \
./libwebp/utils/huffman_encode.c \
./libwebp/utils/quant_levels.c \
./libwebp/utils/quant_levels_dec.c \
./libwebp/utils/random.c \
./libwebp/utils/rescaler.c \
./libwebp/utils/thread.c \
./libwebp/utils/utils.c

include $(BUILD_STATIC_LIBRARY)

include $(CLEAR_VARS)

LOCAL_CPPFLAGS := -frtti
LOCAL_CFLAGS += -DVERSION="1.3.1" -DFLAC__NO_MD5 -DFLAC__INTEGER_ONLY_LIBRARY -DFLAC__NO_ASM
LOCAL_CFLAGS += -D_REENTRANT -DPIC -DU_COMMON_IMPLEMENTATION -fPIC -DHAVE_SYS_PARAM_H
LOCAL_CFLAGS += -O3 -funroll-loops -finline-functions
LOCAL_C_INCLUDES := ./jni/exoplayer/libFLAC/include
LOCAL_ARM_MODE := arm
LOCAL_CPP_EXTENSION := .cc
LOCAL_MODULE := flac

LOCAL_SRC_FILES := \
./exoplayer/libFLAC/bitmath.c                     \
./exoplayer/libFLAC/bitreader.c                   \
./exoplayer/libFLAC/bitwriter.c                   \
./exoplayer/libFLAC/cpu.c                         \
./exoplayer/libFLAC/crc.c                         \
./exoplayer/libFLAC/fixed.c                       \
./exoplayer/libFLAC/fixed_intrin_sse2.c           \
./exoplayer/libFLAC/fixed_intrin_ssse3.c          \
./exoplayer/libFLAC/float.c                       \
./exoplayer/libFLAC/format.c                      \
./exoplayer/libFLAC/lpc.c                         \
./exoplayer/libFLAC/lpc_intrin_avx2.c             \
./exoplayer/libFLAC/lpc_intrin_sse2.c             \
./exoplayer/libFLAC/lpc_intrin_sse41.c            \
./exoplayer/libFLAC/lpc_intrin_sse.c              \
./exoplayer/libFLAC/md5.c                         \
./exoplayer/libFLAC/memory.c                      \
./exoplayer/libFLAC/metadata_iterators.c          \
./exoplayer/libFLAC/metadata_object.c             \
./exoplayer/libFLAC/stream_decoder.c              \
./exoplayer/libFLAC/stream_encoder.c              \
./exoplayer/libFLAC/stream_encoder_framing.c      \
./exoplayer/libFLAC/stream_encoder_intrin_avx2.c  \
./exoplayer/libFLAC/stream_encoder_intrin_sse2.c  \
./exoplayer/libFLAC/stream_encoder_intrin_ssse3.c \
./exoplayer/libFLAC/window.c

include $(BUILD_STATIC_LIBRARY)

include $(CLEAR_VARS)

LOCAL_ARM_MODE  := arm
LOCAL_MODULE := sqlite
LOCAL_CFLAGS 	:= -w -std=c11 -Os -DNULL=0 -DSOCKLEN_T=socklen_t -DLOCALE_NOT_USED -D_LARGEFILE_SOURCE=1
LOCAL_CFLAGS 	+= -DANDROID_NDK -DDISABLE_IMPORTGL -fno-strict-aliasing -fprefetch-loop-arrays -DAVOID_TABLES -DANDROID_TILE_BASED_DECODE -DANDROID_ARMV6_IDCT -DHAVE_STRCHRNUL=0

LOCAL_SRC_FILES     := \
./sqlite/sqlite3.c

include $(BUILD_STATIC_LIBRARY)

include $(CLEAR_VARS)

LOCAL_ARM_MODE  := arm
LOCAL_MODULE := lz4
LOCAL_CFLAGS 	:= -w -std=c11 -O3

LOCAL_SRC_FILES     := \
./lz4/lz4.c \
./lz4/lz4frame.c \
./lz4/xxhash.c

include $(BUILD_STATIC_LIBRARY)

include $(CLEAR_VARS)

LOCAL_ARM_MODE  := arm
LOCAL_MODULE := rlottie
LOCAL_CPPFLAGS := -DNDEBUG -Wall -std=c++14 -DANDROID -fno-rtti -DHAVE_PTHREAD -finline-functions -ffast-math -Os -fno-exceptions -fno-unwind-tables -fno-asynchronous-unwind-tables -Wnon-virtual-dtor -Woverloaded-virtual -Wno-unused-parameter -fvisibility=hidden
ifeq ($(TARGET_ARCH_ABI),$(filter $(TARGET_ARCH_ABI),armeabi-v7a))
 LOCAL_CFLAGS := -DUSE_ARM_NEON  -fno-integrated-as
 LOCAL_CPPFLAGS += -DUSE_ARM_NEON  -fno-integrated-as
else ifeq ($(TARGET_ARCH_ABI),$(filter $(TARGET_ARCH_ABI),arm64-v8a))
 LOCAL_CFLAGS := -DUSE_ARM_NEON -D__ARM64_NEON__ -fno-integrated-as
 LOCAL_CPPFLAGS += -DUSE_ARM_NEON -D__ARM64_NEON__ -fno-integrated-as
endif

LOCAL_C_INCLUDES := \
./jni/rlottie/inc \
./jni/rlottie/src/vector/ \
./jni/rlottie/src/vector/pixman \
./jni/rlottie/src/vector/freetype \
./jni/rlottie/src/vector/stb

LOCAL_SRC_FILES     := \
./rlottie/src/lottie/lottieanimation.cpp \
./rlottie/src/lottie/lottieitem.cpp \
./rlottie/src/lottie/lottiekeypath.cpp \
./rlottie/src/lottie/lottieloader.cpp \
./rlottie/src/lottie/lottiemodel.cpp \
./rlottie/src/lottie/lottieparser.cpp \
./rlottie/src/lottie/lottieproxymodel.cpp \
./rlottie/src/vector/freetype/v_ft_math.cpp \
./rlottie/src/vector/freetype/v_ft_raster.cpp \
./rlottie/src/vector/freetype/v_ft_stroker.cpp \
./rlottie/src/vector/pixman/vregion.cpp \
./rlottie/src/vector/stb/stb_image.cpp \
./rlottie/src/vector/vbezier.cpp \
./rlottie/src/vector/vbitmap.cpp \
./rlottie/src/vector/vbrush.cpp \
./rlottie/src/vector/vcompositionfunctions.cpp \
./rlottie/src/vector/vdasher.cpp \
./rlottie/src/vector/vdebug.cpp \
./rlottie/src/vector/vdrawable.cpp \
./rlottie/src/vector/vdrawhelper.cpp \
./rlottie/src/vector/vdrawhelper_neon.cpp \
./rlottie/src/vector/velapsedtimer.cpp \
./rlottie/src/vector/vimageloader.cpp \
./rlottie/src/vector/vinterpolator.cpp \
./rlottie/src/vector/vmatrix.cpp \
./rlottie/src/vector/vpainter.cpp \
./rlottie/src/vector/vpath.cpp \
./rlottie/src/vector/vpathmesure.cpp \
./rlottie/src/vector/vraster.cpp \
./rlottie/src/vector/vrect.cpp \
./rlottie/src/vector/vrle.cpp

ifeq ($(TARGET_ARCH_ABI),$(filter $(TARGET_ARCH_ABI),armeabi-v7a))
    LOCAL_SRC_FILES += ./rlottie/src/vector/pixman/pixman-arm-neon-asm.S.neon
else ifeq ($(TARGET_ARCH_ABI),$(filter $(TARGET_ARCH_ABI),arm64-v8a))
    LOCAL_SRC_FILES += ./rlottie/src/vector/pixman/pixman-arma64-neon-asm.S.neon
endif

LOCAL_STATIC_LIBRARIES := cpufeatures
include $(BUILD_STATIC_LIBRARY)

include $(CLEAR_VARS)
LOCAL_PRELINK_MODULE := false

LOCAL_MODULE 	:= tmessages.30
LOCAL_CFLAGS 	:= -w -std=c11 -Os -DNULL=0 -DSOCKLEN_T=socklen_t -DLOCALE_NOT_USED -D_LARGEFILE_SOURCE=1
LOCAL_CFLAGS 	+= -Drestrict='' -D__EMX__ -DOPUS_BUILD -DFIXED_POINT -DUSE_ALLOCA -DHAVE_LRINT -DHAVE_LRINTF -fno-math-errno
LOCAL_CFLAGS 	+= -DANDROID_NDK -DDISABLE_IMPORTGL -fno-strict-aliasing -fprefetch-loop-arrays -DAVOID_TABLES -DANDROID_TILE_BASED_DECODE -DANDROID_ARMV6_IDCT -ffast-math -D__STDC_CONSTANT_MACROS
LOCAL_CPPFLAGS 	:= -DBSD=1 -ffast-math -Os -funroll-loops -std=c++14
LOCAL_LDLIBS 	:= -ljnigraphics -llog -lz -lEGL -lGLESv2 -landroid
LOCAL_STATIC_LIBRARIES := webp sqlite lz4 rlottie tgnet swscale avformat avcodec avresample avutil flac

LOCAL_SRC_FILES     := \
./opus/src/opus.c \
./opus/src/opus_decoder.c \
./opus/src/opus_encoder.c \
./opus/src/opus_multistream.c \
./opus/src/opus_multistream_encoder.c \
./opus/src/opus_multistream_decoder.c \
./opus/src/repacketizer.c \
./opus/src/analysis.c \
./opus/src/mlp.c \
./opus/src/mlp_data.c \
./opus/src/opus_projection_encoder.c \
./opus/src/opus_projection_decoder.c \
./opus/src/mapping_matrix.c

ifeq ($(TARGET_ARCH_ABI),$(filter $(TARGET_ARCH_ABI),armeabi-v7a arm64-v8a))
    LOCAL_ARM_MODE := arm
    LOCAL_CPPFLAGS += -DLIBYUV_NEON
    LOCAL_CFLAGS += -DLIBYUV_NEON
    LOCAL_CFLAGS += -DOPUS_HAVE_RTCD -DOPUS_ARM_ASM
    LOCAL_SRC_FILES += \
    ./opus/celt/arm/celt_neon_intr.c.neon \
    ./opus/celt/arm/pitch_neon_intr.c.neon \
    ./opus/silk/arm/NSQ_neon.c.neon \
    ./opus/silk/arm/arm_silk_map.c \
    ./opus/silk/arm/LPC_inv_pred_gain_neon_intr.c.neon \
    ./opus/silk/arm/NSQ_del_dec_neon_intr.c.neon \
    ./opus/silk/arm/biquad_alt_neon_intr.c.neon \
    ./opus/silk/fixed/arm/warped_autocorrelation_FIX_neon_intr.c.neon

#    LOCAL_SRC_FILES += ./opus/celt/arm/celt_pitch_xcorr_arm-gnu.S

else
	ifeq ($(TARGET_ARCH_ABI),x86)
	    LOCAL_CFLAGS += -Dx86fix
 	    LOCAL_CPPFLAGS += -Dx86fix
	    LOCAL_ARM_MODE  := arm
#	    LOCAL_SRC_FILES += \
#	    ./libyuv/source/row_x86.asm

#	    LOCAL_SRC_FILES += \
#	    ./opus/celt/x86/celt_lpc_sse.c \
#		./opus/celt/x86/pitch_sse.c \
#		./opus/celt/x86/pitch_sse2.c \
#		./opus/celt/x86/pitch_sse4_1.c \
#		./opus/celt/x86/vq_sse2.c \
#		./opus/celt/x86/x86_celt_map.c \
#		./opus/celt/x86/x86cpu.c \
#		./opus/silk/fixed/x86/burg_modified_FIX_sse.c \
#		./opus/silk/fixed/x86/vector_ops_FIX_sse.c \
#		./opus/silk/x86/NSQ_del_dec_sse.c \
#		./opus/silk/x86/NSQ_sse.c \
#		./opus/silk/x86/VAD_sse.c \
#		./opus/silk/x86/VQ_WMat_sse.c \
#		./opus/silk/x86/x86_silk_map.c
    endif
endif

LOCAL_SRC_FILES     += \
./opus/silk/CNG.c \
./opus/silk/code_signs.c \
./opus/silk/init_decoder.c \
./opus/silk/decode_core.c \
./opus/silk/decode_frame.c \
./opus/silk/decode_parameters.c \
./opus/silk/decode_indices.c \
./opus/silk/decode_pulses.c \
./opus/silk/decoder_set_fs.c \
./opus/silk/dec_API.c \
./opus/silk/enc_API.c \
./opus/silk/encode_indices.c \
./opus/silk/encode_pulses.c \
./opus/silk/gain_quant.c \
./opus/silk/interpolate.c \
./opus/silk/LP_variable_cutoff.c \
./opus/silk/NLSF_decode.c \
./opus/silk/NSQ.c \
./opus/silk/NSQ_del_dec.c \
./opus/silk/PLC.c \
./opus/silk/shell_coder.c \
./opus/silk/tables_gain.c \
./opus/silk/tables_LTP.c \
./opus/silk/tables_NLSF_CB_NB_MB.c \
./opus/silk/tables_NLSF_CB_WB.c \
./opus/silk/tables_other.c \
./opus/silk/tables_pitch_lag.c \
./opus/silk/tables_pulses_per_block.c \
./opus/silk/VAD.c \
./opus/silk/control_audio_bandwidth.c \
./opus/silk/quant_LTP_gains.c \
./opus/silk/VQ_WMat_EC.c \
./opus/silk/HP_variable_cutoff.c \
./opus/silk/NLSF_encode.c \
./opus/silk/NLSF_VQ.c \
./opus/silk/NLSF_unpack.c \
./opus/silk/NLSF_del_dec_quant.c \
./opus/silk/process_NLSFs.c \
./opus/silk/stereo_LR_to_MS.c \
./opus/silk/stereo_MS_to_LR.c \
./opus/silk/check_control_input.c \
./opus/silk/control_SNR.c \
./opus/silk/init_encoder.c \
./opus/silk/control_codec.c \
./opus/silk/A2NLSF.c \
./opus/silk/ana_filt_bank_1.c \
./opus/silk/biquad_alt.c \
./opus/silk/bwexpander_32.c \
./opus/silk/bwexpander.c \
./opus/silk/debug.c \
./opus/silk/decode_pitch.c \
./opus/silk/inner_prod_aligned.c \
./opus/silk/lin2log.c \
./opus/silk/log2lin.c \
./opus/silk/LPC_analysis_filter.c \
./opus/silk/LPC_inv_pred_gain.c \
./opus/silk/table_LSF_cos.c \
./opus/silk/NLSF2A.c \
./opus/silk/NLSF_stabilize.c \
./opus/silk/NLSF_VQ_weights_laroia.c \
./opus/silk/pitch_est_tables.c \
./opus/silk/resampler.c \
./opus/silk/resampler_down2_3.c \
./opus/silk/resampler_down2.c \
./opus/silk/resampler_private_AR2.c \
./opus/silk/resampler_private_down_FIR.c \
./opus/silk/resampler_private_IIR_FIR.c \
./opus/silk/resampler_private_up2_HQ.c \
./opus/silk/resampler_rom.c \
./opus/silk/sigm_Q15.c \
./opus/silk/sort.c \
./opus/silk/sum_sqr_shift.c \
./opus/silk/stereo_decode_pred.c \
./opus/silk/stereo_encode_pred.c \
./opus/silk/stereo_find_predictor.c \
./opus/silk/stereo_quant_pred.c \
./opus/silk/LPC_fit.c

LOCAL_SRC_FILES     += \
./opus/silk/fixed/LTP_analysis_filter_FIX.c \
./opus/silk/fixed/LTP_scale_ctrl_FIX.c \
./opus/silk/fixed/corrMatrix_FIX.c \
./opus/silk/fixed/encode_frame_FIX.c \
./opus/silk/fixed/find_LPC_FIX.c \
./opus/silk/fixed/find_LTP_FIX.c \
./opus/silk/fixed/find_pitch_lags_FIX.c \
./opus/silk/fixed/find_pred_coefs_FIX.c \
./opus/silk/fixed/noise_shape_analysis_FIX.c \
./opus/silk/fixed/process_gains_FIX.c \
./opus/silk/fixed/regularize_correlations_FIX.c \
./opus/silk/fixed/residual_energy16_FIX.c \
./opus/silk/fixed/residual_energy_FIX.c \
./opus/silk/fixed/warped_autocorrelation_FIX.c \
./opus/silk/fixed/apply_sine_window_FIX.c \
./opus/silk/fixed/autocorr_FIX.c \
./opus/silk/fixed/burg_modified_FIX.c \
./opus/silk/fixed/k2a_FIX.c \
./opus/silk/fixed/k2a_Q16_FIX.c \
./opus/silk/fixed/pitch_analysis_core_FIX.c \
./opus/silk/fixed/vector_ops_FIX.c \
./opus/silk/fixed/schur64_FIX.c \
./opus/silk/fixed/schur_FIX.c

LOCAL_SRC_FILES     += \
./opus/celt/bands.c \
./opus/celt/celt.c \
./opus/celt/celt_encoder.c \
./opus/celt/celt_decoder.c \
./opus/celt/cwrs.c \
./opus/celt/entcode.c \
./opus/celt/entdec.c \
./opus/celt/entenc.c \
./opus/celt/kiss_fft.c \
./opus/celt/laplace.c \
./opus/celt/mathops.c \
./opus/celt/mdct.c \
./opus/celt/modes.c \
./opus/celt/pitch.c \
./opus/celt/celt_lpc.c \
./opus/celt/quant_bands.c \
./opus/celt/rate.c \
./opus/celt/vq.c \
./opus/celt/arm/armcpu.c \
./opus/celt/arm/arm_celt_map.c

LOCAL_SRC_FILES     += \
./opus/ogg/bitwise.c \
./opus/ogg/framing.c \
./opus/opusfile/info.c \
./opus/opusfile/internal.c \
./opus/opusfile/opusfile.c \
./opus/opusfile/stream.c

LOCAL_C_INCLUDES    := \
./jni/opus/include \
./jni/opus/silk \
./jni/opus/silk/fixed \
./jni/opus/celt \
./jni/opus/ \
./jni/opus/opusfile \
./jni/libyuv/include \
./jni/boringssl/include \
./jni/ffmpeg/include \
./jni/emoji \
./jni/exoplayer/include \
./jni/exoplayer/libFLAC/include \
./jni/intro \
./jni/rlottie/inc \
./jni/ton \
./jni/lz4

LOCAL_SRC_FILES     += \
./libyuv/source/compare_common.cc \
./libyuv/source/compare_gcc.cc \
./libyuv/source/compare_neon64.cc \
./libyuv/source/compare_win.cc \
./libyuv/source/compare.cc \
./libyuv/source/convert_argb.cc \
./libyuv/source/convert_from_argb.cc \
./libyuv/source/convert_from.cc \
./libyuv/source/convert_jpeg.cc \
./libyuv/source/convert_to_argb.cc \
./libyuv/source/convert_to_i420.cc \
./libyuv/source/convert.cc \
./libyuv/source/cpu_id.cc \
./libyuv/source/mjpeg_decoder.cc \
./libyuv/source/mjpeg_validate.cc \
./libyuv/source/planar_functions.cc \
./libyuv/source/rotate_any.cc \
./libyuv/source/rotate_argb.cc \
./libyuv/source/rotate_common.cc \
./libyuv/source/rotate_gcc.cc \
./libyuv/source/rotate_mips.cc \
./libyuv/source/rotate_neon64.cc \
./libyuv/source/rotate_win.cc \
./libyuv/source/rotate.cc \
./libyuv/source/row_any.cc \
./libyuv/source/row_common.cc \
./libyuv/source/row_gcc.cc \
./libyuv/source/row_mips.cc \
./libyuv/source/row_neon64.cc \
./libyuv/source/row_win.cc \
./libyuv/source/scale_any.cc \
./libyuv/source/scale_argb.cc \
./libyuv/source/scale_common.cc \
./libyuv/source/scale_gcc.cc \
./libyuv/source/scale_mips.cc \
./libyuv/source/scale_neon64.cc \
./libyuv/source/scale_win.cc \
./libyuv/source/scale.cc \
./libyuv/source/video_common.cc

ifeq ($(TARGET_ARCH_ABI),armeabi-v7a)
    LOCAL_CFLAGS += -DLIBYUV_NEON
    LOCAL_SRC_FILES += \
        ./libyuv/source/compare_neon.cc.neon    \
        ./libyuv/source/rotate_neon.cc.neon     \
        ./libyuv/source/row_neon.cc.neon        \
        ./libyuv/source/scale_neon.cc.neon
endif

LOCAL_SRC_FILES     += \
./jni.c \
./audio.c \
./image.cpp \
./video.c \
./intro/IntroRenderer.c \
./utilities.cpp \
./gifvideo.cpp \
./lottie.cpp \
./SqliteWrapper.cpp \
./TgNetWrapper.cpp \
./NativeLoader.cpp \
./exoplayer/flac_jni.cc \
./exoplayer/flac_parser.cc \
./exoplayer/opus_jni.cc \
./exoplayer/ffmpeg_jni.cc \
./fast-edge.cpp \
./genann.c \
./secureid_ocr.cpp

include $(BUILD_SHARED_LIBRARY)

$(call import-module,android/cpufeatures)