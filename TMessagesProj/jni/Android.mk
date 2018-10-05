LOCAL_PATH := $(call my-dir)

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

LOCAL_MODULE := voip
LOCAL_CPPFLAGS := -Wall -std=c++11 -DANDROID -finline-functions -ffast-math -Os -fno-strict-aliasing -O3 -frtti -D__STDC_LIMIT_MACROS -Wno-unknown-pragmas
LOCAL_CFLAGS := -O3 -DUSE_KISS_FFT -fexceptions -DWEBRTC_APM_DEBUG_DUMP=0 -DWEBRTC_POSIX -DWEBRTC_ANDROID -D__STDC_LIMIT_MACROS -DFIXED_POINT -DWEBRTC_NS_FLOAT

MY_DIR := libtgvoip

LOCAL_C_INCLUDES := jni/opus/include jni/boringssl/include/ jni/libtgvoip/webrtc_dsp/

ifeq ($(TARGET_ARCH_ABI),$(filter $(TARGET_ARCH_ABI),armeabi-v7a arm64-v8a))
CC_NEON := cc.neon
LOCAL_CFLAGS += -DWEBRTC_HAS_NEON
else
CC_NEON := cc
endif

LOCAL_SRC_FILES := \
./libtgvoip/logging.cpp \
./libtgvoip/VoIPController.cpp \
./libtgvoip/VoIPGroupController.cpp \
./libtgvoip/Buffers.cpp \
./libtgvoip/BlockingQueue.cpp \
./libtgvoip/audio/AudioInput.cpp \
./libtgvoip/os/android/AudioInputOpenSLES.cpp \
./libtgvoip/MediaStreamItf.cpp \
./libtgvoip/audio/AudioOutput.cpp \
./libtgvoip/OpusEncoder.cpp \
./libtgvoip/os/android/AudioOutputOpenSLES.cpp \
./libtgvoip/JitterBuffer.cpp \
./libtgvoip/OpusDecoder.cpp \
./libtgvoip/os/android/OpenSLEngineWrapper.cpp \
./libtgvoip/os/android/AudioInputAndroid.cpp \
./libtgvoip/os/android/AudioOutputAndroid.cpp \
./libtgvoip/EchoCanceller.cpp \
./libtgvoip/CongestionControl.cpp \
./libtgvoip/VoIPServerConfig.cpp \
./libtgvoip/audio/Resampler.cpp \
./libtgvoip/NetworkSocket.cpp \
./libtgvoip/os/posix/NetworkSocketPosix.cpp \
./libtgvoip/PacketReassembler.cpp \
./libtgvoip/MessageThread.cpp \
./libtgvoip/json11.cpp \
./libtgvoip/audio/AudioIO.cpp \
./libtgvoip/video/VideoRenderer.cpp \
./libtgvoip/video/VideoSource.cpp \
./libtgvoip/os/android/VideoSourceAndroid.cpp \
./libtgvoip/os/android/VideoRendererAndroid.cpp

# WebRTC signal processing

LOCAL_SRC_FILES += \
./libtgvoip/webrtc_dsp/system_wrappers/source/field_trial.cc \
./libtgvoip/webrtc_dsp/system_wrappers/source/metrics.cc \
./libtgvoip/webrtc_dsp/system_wrappers/source/cpu_features.cc \
./libtgvoip/webrtc_dsp/absl/strings/internal/memutil.cc \
./libtgvoip/webrtc_dsp/absl/strings/string_view.cc \
./libtgvoip/webrtc_dsp/absl/strings/ascii.cc \
./libtgvoip/webrtc_dsp/absl/types/bad_optional_access.cc \
./libtgvoip/webrtc_dsp/absl/types/optional.cc \
./libtgvoip/webrtc_dsp/absl/base/internal/raw_logging.cc \
./libtgvoip/webrtc_dsp/absl/base/internal/throw_delegate.cc \
./libtgvoip/webrtc_dsp/rtc_base/race_checker.cc \
./libtgvoip/webrtc_dsp/rtc_base/strings/string_builder.cc \
./libtgvoip/webrtc_dsp/rtc_base/memory/aligned_malloc.cc \
./libtgvoip/webrtc_dsp/rtc_base/timeutils.cc \
./libtgvoip/webrtc_dsp/rtc_base/platform_file.cc \
./libtgvoip/webrtc_dsp/rtc_base/string_to_number.cc \
./libtgvoip/webrtc_dsp/rtc_base/thread_checker_impl.cc \
./libtgvoip/webrtc_dsp/rtc_base/stringencode.cc \
./libtgvoip/webrtc_dsp/rtc_base/stringutils.cc \
./libtgvoip/webrtc_dsp/rtc_base/checks.cc \
./libtgvoip/webrtc_dsp/rtc_base/platform_thread.cc \
./libtgvoip/webrtc_dsp/rtc_base/criticalsection.cc \
./libtgvoip/webrtc_dsp/rtc_base/platform_thread_types.cc \
./libtgvoip/webrtc_dsp/rtc_base/event.cc \
./libtgvoip/webrtc_dsp/rtc_base/event_tracer.cc \
./libtgvoip/webrtc_dsp/rtc_base/logging_webrtc.cc \
./libtgvoip/webrtc_dsp/third_party/rnnoise/src/rnn_vad_weights.cc \
./libtgvoip/webrtc_dsp/third_party/rnnoise/src/kiss_fft.cc \
./libtgvoip/webrtc_dsp/api/audio/audio_frame.cc \
./libtgvoip/webrtc_dsp/api/audio/echo_canceller3_config.cc \
./libtgvoip/webrtc_dsp/api/audio/echo_canceller3_factory.cc \
./libtgvoip/webrtc_dsp/modules/third_party/fft/fft.c \
./libtgvoip/webrtc_dsp/modules/audio_coding/codecs/isac/main/source/pitch_estimator.c \
./libtgvoip/webrtc_dsp/modules/audio_coding/codecs/isac/main/source/lpc_shape_swb16_tables.c \
./libtgvoip/webrtc_dsp/modules/audio_coding/codecs/isac/main/source/pitch_gain_tables.c \
./libtgvoip/webrtc_dsp/modules/audio_coding/codecs/isac/main/source/arith_routines_logist.c \
./libtgvoip/webrtc_dsp/modules/audio_coding/codecs/isac/main/source/filterbanks.c \
./libtgvoip/webrtc_dsp/modules/audio_coding/codecs/isac/main/source/transform.c \
./libtgvoip/webrtc_dsp/modules/audio_coding/codecs/isac/main/source/pitch_filter.c \
./libtgvoip/webrtc_dsp/modules/audio_coding/codecs/isac/main/source/encode_lpc_swb.c \
./libtgvoip/webrtc_dsp/modules/audio_coding/codecs/isac/main/source/filter_functions.c \
./libtgvoip/webrtc_dsp/modules/audio_coding/codecs/isac/main/source/decode.c \
./libtgvoip/webrtc_dsp/modules/audio_coding/codecs/isac/main/source/lattice.c \
./libtgvoip/webrtc_dsp/modules/audio_coding/codecs/isac/main/source/intialize.c \
./libtgvoip/webrtc_dsp/modules/audio_coding/codecs/isac/main/source/lpc_tables.c \
./libtgvoip/webrtc_dsp/modules/audio_coding/codecs/isac/main/source/lpc_gain_swb_tables.c \
./libtgvoip/webrtc_dsp/modules/audio_coding/codecs/isac/main/source/bandwidth_estimator.c \
./libtgvoip/webrtc_dsp/modules/audio_coding/codecs/isac/main/source/encode.c \
./libtgvoip/webrtc_dsp/modules/audio_coding/codecs/isac/main/source/lpc_analysis.c \
./libtgvoip/webrtc_dsp/modules/audio_coding/codecs/isac/main/source/arith_routines_hist.c \
./libtgvoip/webrtc_dsp/modules/audio_coding/codecs/isac/main/source/entropy_coding.c \
./libtgvoip/webrtc_dsp/modules/audio_coding/codecs/isac/main/source/isac_vad.c \
./libtgvoip/webrtc_dsp/modules/audio_coding/codecs/isac/main/source/arith_routines.c \
./libtgvoip/webrtc_dsp/modules/audio_coding/codecs/isac/main/source/crc.c \
./libtgvoip/webrtc_dsp/modules/audio_coding/codecs/isac/main/source/lpc_shape_swb12_tables.c \
./libtgvoip/webrtc_dsp/modules/audio_coding/codecs/isac/main/source/decode_bwe.c \
./libtgvoip/webrtc_dsp/modules/audio_coding/codecs/isac/main/source/spectrum_ar_model_tables.c \
./libtgvoip/webrtc_dsp/modules/audio_coding/codecs/isac/main/source/pitch_lag_tables.c \
./libtgvoip/webrtc_dsp/modules/audio_coding/codecs/isac/main/source/isac.c \
./libtgvoip/webrtc_dsp/modules/audio_processing/rms_level.cc \
./libtgvoip/webrtc_dsp/modules/audio_processing/echo_detector/normalized_covariance_estimator.cc \
./libtgvoip/webrtc_dsp/modules/audio_processing/echo_detector/moving_max.cc \
./libtgvoip/webrtc_dsp/modules/audio_processing/echo_detector/circular_buffer.cc \
./libtgvoip/webrtc_dsp/modules/audio_processing/echo_detector/mean_variance_estimator.cc \
./libtgvoip/webrtc_dsp/modules/audio_processing/splitting_filter.cc \
./libtgvoip/webrtc_dsp/modules/audio_processing/gain_control_impl.cc \
./libtgvoip/webrtc_dsp/modules/audio_processing/ns/nsx_core.c \
./libtgvoip/webrtc_dsp/modules/audio_processing/ns/noise_suppression_x.c \
./libtgvoip/webrtc_dsp/modules/audio_processing/ns/nsx_core_c.c \
./libtgvoip/webrtc_dsp/modules/audio_processing/ns/ns_core.c \
./libtgvoip/webrtc_dsp/modules/audio_processing/ns/noise_suppression.c \
./libtgvoip/webrtc_dsp/modules/audio_processing/audio_buffer.cc \
./libtgvoip/webrtc_dsp/modules/audio_processing/typing_detection.cc \
./libtgvoip/webrtc_dsp/modules/audio_processing/include/audio_processing_statistics.cc \
./libtgvoip/webrtc_dsp/modules/audio_processing/include/audio_generator_factory.cc \
./libtgvoip/webrtc_dsp/modules/audio_processing/include/aec_dump.cc \
./libtgvoip/webrtc_dsp/modules/audio_processing/include/audio_processing.cc \
./libtgvoip/webrtc_dsp/modules/audio_processing/include/config.cc \
./libtgvoip/webrtc_dsp/modules/audio_processing/agc2/interpolated_gain_curve.cc \
./libtgvoip/webrtc_dsp/modules/audio_processing/agc2/agc2_common.cc \
./libtgvoip/webrtc_dsp/modules/audio_processing/agc2/gain_applier.cc \
./libtgvoip/webrtc_dsp/modules/audio_processing/agc2/adaptive_agc.cc \
./libtgvoip/webrtc_dsp/modules/audio_processing/agc2/adaptive_digital_gain_applier.cc \
./libtgvoip/webrtc_dsp/modules/audio_processing/agc2/limiter.cc \
./libtgvoip/webrtc_dsp/modules/audio_processing/agc2/saturation_protector.cc \
./libtgvoip/webrtc_dsp/modules/audio_processing/agc2/rnn_vad/spectral_features_internal.cc \
./libtgvoip/webrtc_dsp/modules/audio_processing/agc2/rnn_vad/rnn.cc \
./libtgvoip/webrtc_dsp/modules/audio_processing/agc2/rnn_vad/pitch_search_internal.cc \
./libtgvoip/webrtc_dsp/modules/audio_processing/agc2/rnn_vad/spectral_features.cc \
./libtgvoip/webrtc_dsp/modules/audio_processing/agc2/rnn_vad/pitch_search.cc \
./libtgvoip/webrtc_dsp/modules/audio_processing/agc2/rnn_vad/features_extraction.cc \
./libtgvoip/webrtc_dsp/modules/audio_processing/agc2/rnn_vad/fft_util.cc \
./libtgvoip/webrtc_dsp/modules/audio_processing/agc2/rnn_vad/lp_residual.cc \
./libtgvoip/webrtc_dsp/modules/audio_processing/agc2/adaptive_mode_level_estimator_agc.cc \
./libtgvoip/webrtc_dsp/modules/audio_processing/agc2/vector_float_frame.cc \
./libtgvoip/webrtc_dsp/modules/audio_processing/agc2/noise_level_estimator.cc \
./libtgvoip/webrtc_dsp/modules/audio_processing/agc2/agc2_testing_common.cc \
./libtgvoip/webrtc_dsp/modules/audio_processing/agc2/fixed_digital_level_estimator.cc \
./libtgvoip/webrtc_dsp/modules/audio_processing/agc2/fixed_gain_controller.cc \
./libtgvoip/webrtc_dsp/modules/audio_processing/agc2/vad_with_level.cc \
./libtgvoip/webrtc_dsp/modules/audio_processing/agc2/limiter_db_gain_curve.cc \
./libtgvoip/webrtc_dsp/modules/audio_processing/agc2/down_sampler.cc \
./libtgvoip/webrtc_dsp/modules/audio_processing/agc2/signal_classifier.cc \
./libtgvoip/webrtc_dsp/modules/audio_processing/agc2/noise_spectrum_estimator.cc \
./libtgvoip/webrtc_dsp/modules/audio_processing/agc2/compute_interpolated_gain_curve.cc \
./libtgvoip/webrtc_dsp/modules/audio_processing/agc2/biquad_filter.cc \
./libtgvoip/webrtc_dsp/modules/audio_processing/agc2/adaptive_mode_level_estimator.cc \
./libtgvoip/webrtc_dsp/modules/audio_processing/transient/moving_moments.cc \
./libtgvoip/webrtc_dsp/modules/audio_processing/transient/wpd_tree.cc \
./libtgvoip/webrtc_dsp/modules/audio_processing/transient/wpd_node.cc \
./libtgvoip/webrtc_dsp/modules/audio_processing/transient/transient_suppressor.cc \
./libtgvoip/webrtc_dsp/modules/audio_processing/transient/transient_detector.cc \
./libtgvoip/webrtc_dsp/modules/audio_processing/low_cut_filter.cc \
./libtgvoip/webrtc_dsp/modules/audio_processing/level_estimator_impl.cc \
./libtgvoip/webrtc_dsp/modules/audio_processing/three_band_filter_bank.cc \
./libtgvoip/webrtc_dsp/modules/audio_processing/aec/echo_cancellation.cc \
./libtgvoip/webrtc_dsp/modules/audio_processing/aec/aec_resampler.cc \
./libtgvoip/webrtc_dsp/modules/audio_processing/aec/aec_core.cc \
./libtgvoip/webrtc_dsp/modules/audio_processing/voice_detection_impl.cc \
./libtgvoip/webrtc_dsp/modules/audio_processing/echo_cancellation_impl.cc \
./libtgvoip/webrtc_dsp/modules/audio_processing/gain_control_for_experimental_agc.cc \
./libtgvoip/webrtc_dsp/modules/audio_processing/agc/agc.cc \
./libtgvoip/webrtc_dsp/modules/audio_processing/agc/loudness_histogram.cc \
./libtgvoip/webrtc_dsp/modules/audio_processing/agc/agc_manager_direct.cc \
./libtgvoip/webrtc_dsp/modules/audio_processing/agc/legacy/analog_agc.c \
./libtgvoip/webrtc_dsp/modules/audio_processing/agc/legacy/digital_agc.c \
./libtgvoip/webrtc_dsp/modules/audio_processing/agc/utility.cc \
./libtgvoip/webrtc_dsp/modules/audio_processing/audio_processing_impl.cc \
./libtgvoip/webrtc_dsp/modules/audio_processing/audio_generator/file_audio_generator.cc \
./libtgvoip/webrtc_dsp/modules/audio_processing/gain_controller2.cc \
./libtgvoip/webrtc_dsp/modules/audio_processing/residual_echo_detector.cc \
./libtgvoip/webrtc_dsp/modules/audio_processing/noise_suppression_impl.cc \
./libtgvoip/webrtc_dsp/modules/audio_processing/aecm/aecm_core.cc \
./libtgvoip/webrtc_dsp/modules/audio_processing/aecm/aecm_core_c.cc \
./libtgvoip/webrtc_dsp/modules/audio_processing/aecm/echo_control_mobile.cc \
./libtgvoip/webrtc_dsp/modules/audio_processing/aec3/render_reverb_model.cc \
./libtgvoip/webrtc_dsp/modules/audio_processing/aec3/reverb_model_fallback.cc \
./libtgvoip/webrtc_dsp/modules/audio_processing/aec3/echo_remover_metrics.cc \
./libtgvoip/webrtc_dsp/modules/audio_processing/aec3/matched_filter_lag_aggregator.cc \
./libtgvoip/webrtc_dsp/modules/audio_processing/aec3/render_delay_buffer2.cc \
./libtgvoip/webrtc_dsp/modules/audio_processing/aec3/echo_path_variability.cc \
./libtgvoip/webrtc_dsp/modules/audio_processing/aec3/frame_blocker.cc \
./libtgvoip/webrtc_dsp/modules/audio_processing/aec3/subtractor.cc \
./libtgvoip/webrtc_dsp/modules/audio_processing/aec3/aec3_fft.cc \
./libtgvoip/webrtc_dsp/modules/audio_processing/aec3/fullband_erle_estimator.cc \
./libtgvoip/webrtc_dsp/modules/audio_processing/aec3/suppression_filter.$(CC_NEON) \
./libtgvoip/webrtc_dsp/modules/audio_processing/aec3/block_processor.cc \
./libtgvoip/webrtc_dsp/modules/audio_processing/aec3/subband_erle_estimator.cc \
./libtgvoip/webrtc_dsp/modules/audio_processing/aec3/render_delay_controller_metrics.cc \
./libtgvoip/webrtc_dsp/modules/audio_processing/aec3/render_delay_buffer.cc \
./libtgvoip/webrtc_dsp/modules/audio_processing/aec3/vector_buffer.cc \
./libtgvoip/webrtc_dsp/modules/audio_processing/aec3/erl_estimator.cc \
./libtgvoip/webrtc_dsp/modules/audio_processing/aec3/aec_state.cc \
./libtgvoip/webrtc_dsp/modules/audio_processing/aec3/adaptive_fir_filter.$(CC_NEON) \
./libtgvoip/webrtc_dsp/modules/audio_processing/aec3/render_delay_controller.cc \
./libtgvoip/webrtc_dsp/modules/audio_processing/aec3/skew_estimator.cc \
./libtgvoip/webrtc_dsp/modules/audio_processing/aec3/echo_path_delay_estimator.cc \
./libtgvoip/webrtc_dsp/modules/audio_processing/aec3/block_framer.cc \
./libtgvoip/webrtc_dsp/modules/audio_processing/aec3/erle_estimator.cc \
./libtgvoip/webrtc_dsp/modules/audio_processing/aec3/reverb_model.cc \
./libtgvoip/webrtc_dsp/modules/audio_processing/aec3/cascaded_biquad_filter.cc \
./libtgvoip/webrtc_dsp/modules/audio_processing/aec3/render_buffer.cc \
./libtgvoip/webrtc_dsp/modules/audio_processing/aec3/subtractor_output.cc \
./libtgvoip/webrtc_dsp/modules/audio_processing/aec3/stationarity_estimator.cc \
./libtgvoip/webrtc_dsp/modules/audio_processing/aec3/render_signal_analyzer.cc \
./libtgvoip/webrtc_dsp/modules/audio_processing/aec3/subtractor_output_analyzer.cc \
./libtgvoip/webrtc_dsp/modules/audio_processing/aec3/suppression_gain.$(CC_NEON) \
./libtgvoip/webrtc_dsp/modules/audio_processing/aec3/echo_audibility.cc \
./libtgvoip/webrtc_dsp/modules/audio_processing/aec3/block_processor_metrics.cc \
./libtgvoip/webrtc_dsp/modules/audio_processing/aec3/moving_average.cc \
./libtgvoip/webrtc_dsp/modules/audio_processing/aec3/reverb_model_estimator.cc \
./libtgvoip/webrtc_dsp/modules/audio_processing/aec3/aec3_common.cc \
./libtgvoip/webrtc_dsp/modules/audio_processing/aec3/residual_echo_estimator.cc \
./libtgvoip/webrtc_dsp/modules/audio_processing/aec3/matched_filter.$(CC_NEON) \
./libtgvoip/webrtc_dsp/modules/audio_processing/aec3/reverb_decay_estimator.cc \
./libtgvoip/webrtc_dsp/modules/audio_processing/aec3/render_delay_controller2.cc \
./libtgvoip/webrtc_dsp/modules/audio_processing/aec3/suppression_gain_limiter.cc \
./libtgvoip/webrtc_dsp/modules/audio_processing/aec3/main_filter_update_gain.cc \
./libtgvoip/webrtc_dsp/modules/audio_processing/aec3/echo_remover.cc \
./libtgvoip/webrtc_dsp/modules/audio_processing/aec3/downsampled_render_buffer.cc \
./libtgvoip/webrtc_dsp/modules/audio_processing/aec3/matrix_buffer.cc \
./libtgvoip/webrtc_dsp/modules/audio_processing/aec3/block_processor2.cc \
./libtgvoip/webrtc_dsp/modules/audio_processing/aec3/echo_canceller3.cc \
./libtgvoip/webrtc_dsp/modules/audio_processing/aec3/block_delay_buffer.cc \
./libtgvoip/webrtc_dsp/modules/audio_processing/aec3/fft_buffer.cc \
./libtgvoip/webrtc_dsp/modules/audio_processing/aec3/comfort_noise_generator.$(CC_NEON) \
./libtgvoip/webrtc_dsp/modules/audio_processing/aec3/shadow_filter_update_gain.cc \
./libtgvoip/webrtc_dsp/modules/audio_processing/aec3/filter_analyzer.cc \
./libtgvoip/webrtc_dsp/modules/audio_processing/aec3/reverb_frequency_response.cc \
./libtgvoip/webrtc_dsp/modules/audio_processing/aec3/decimator.cc \
./libtgvoip/webrtc_dsp/modules/audio_processing/echo_control_mobile_impl.cc \
./libtgvoip/webrtc_dsp/modules/audio_processing/logging/apm_data_dumper.cc \
./libtgvoip/webrtc_dsp/modules/audio_processing/vad/voice_activity_detector.cc \
./libtgvoip/webrtc_dsp/modules/audio_processing/vad/standalone_vad.cc \
./libtgvoip/webrtc_dsp/modules/audio_processing/vad/pitch_internal.cc \
./libtgvoip/webrtc_dsp/modules/audio_processing/vad/vad_circular_buffer.cc \
./libtgvoip/webrtc_dsp/modules/audio_processing/vad/vad_audio_proc.cc \
./libtgvoip/webrtc_dsp/modules/audio_processing/vad/pole_zero_filter.cc \
./libtgvoip/webrtc_dsp/modules/audio_processing/vad/pitch_based_vad.cc \
./libtgvoip/webrtc_dsp/modules/audio_processing/vad/gmm.cc \
./libtgvoip/webrtc_dsp/modules/audio_processing/utility/ooura_fft.cc \
./libtgvoip/webrtc_dsp/modules/audio_processing/utility/delay_estimator_wrapper.cc \
./libtgvoip/webrtc_dsp/modules/audio_processing/utility/delay_estimator.cc \
./libtgvoip/webrtc_dsp/modules/audio_processing/utility/block_mean_calculator.cc \
./libtgvoip/webrtc_dsp/common_audio/window_generator.cc \
./libtgvoip/webrtc_dsp/common_audio/channel_buffer.cc \
./libtgvoip/webrtc_dsp/common_audio/fir_filter_factory.cc \
./libtgvoip/webrtc_dsp/common_audio/wav_header.cc \
./libtgvoip/webrtc_dsp/common_audio/real_fourier_ooura.cc \
./libtgvoip/webrtc_dsp/common_audio/audio_util.cc \
./libtgvoip/webrtc_dsp/common_audio/resampler/push_sinc_resampler.cc \
./libtgvoip/webrtc_dsp/common_audio/resampler/resampler.cc \
./libtgvoip/webrtc_dsp/common_audio/resampler/push_resampler.cc \
./libtgvoip/webrtc_dsp/common_audio/resampler/sinc_resampler.cc \
./libtgvoip/webrtc_dsp/common_audio/resampler/sinusoidal_linear_chirp_source.cc \
./libtgvoip/webrtc_dsp/common_audio/wav_file.cc \
./libtgvoip/webrtc_dsp/common_audio/third_party/spl_sqrt_floor/spl_sqrt_floor.c \
./libtgvoip/webrtc_dsp/common_audio/third_party/fft4g/fft4g.c \
./libtgvoip/webrtc_dsp/common_audio/audio_converter.cc \
./libtgvoip/webrtc_dsp/common_audio/real_fourier.cc \
./libtgvoip/webrtc_dsp/common_audio/sparse_fir_filter.cc \
./libtgvoip/webrtc_dsp/common_audio/smoothing_filter.cc \
./libtgvoip/webrtc_dsp/common_audio/fir_filter_c.cc \
./libtgvoip/webrtc_dsp/common_audio/ring_buffer.c \
./libtgvoip/webrtc_dsp/common_audio/signal_processing/complex_fft.c \
./libtgvoip/webrtc_dsp/common_audio/signal_processing/filter_ma_fast_q12.c \
./libtgvoip/webrtc_dsp/common_audio/signal_processing/levinson_durbin.c \
./libtgvoip/webrtc_dsp/common_audio/signal_processing/dot_product_with_scale.cc \
./libtgvoip/webrtc_dsp/common_audio/signal_processing/auto_corr_to_refl_coef.c \
./libtgvoip/webrtc_dsp/common_audio/signal_processing/resample_by_2_internal.c \
./libtgvoip/webrtc_dsp/common_audio/signal_processing/energy.c \
./libtgvoip/webrtc_dsp/common_audio/signal_processing/sqrt_of_one_minus_x_squared.c \
./libtgvoip/webrtc_dsp/common_audio/signal_processing/downsample_fast.c \
./libtgvoip/webrtc_dsp/common_audio/signal_processing/splitting_filter1.c \
./libtgvoip/webrtc_dsp/common_audio/signal_processing/filter_ar_fast_q12.c \
./libtgvoip/webrtc_dsp/common_audio/signal_processing/spl_init.c \
./libtgvoip/webrtc_dsp/common_audio/signal_processing/lpc_to_refl_coef.c \
./libtgvoip/webrtc_dsp/common_audio/signal_processing/cross_correlation.c \
./libtgvoip/webrtc_dsp/common_audio/signal_processing/division_operations.c \
./libtgvoip/webrtc_dsp/common_audio/signal_processing/auto_correlation.c \
./libtgvoip/webrtc_dsp/common_audio/signal_processing/get_scaling_square.c \
./libtgvoip/webrtc_dsp/common_audio/signal_processing/resample.c \
./libtgvoip/webrtc_dsp/common_audio/signal_processing/min_max_operations.c \
./libtgvoip/webrtc_dsp/common_audio/signal_processing/refl_coef_to_lpc.c \
./libtgvoip/webrtc_dsp/common_audio/signal_processing/filter_ar.c \
./libtgvoip/webrtc_dsp/common_audio/signal_processing/vector_scaling_operations.c \
./libtgvoip/webrtc_dsp/common_audio/signal_processing/resample_fractional.c \
./libtgvoip/webrtc_dsp/common_audio/signal_processing/real_fft.c \
./libtgvoip/webrtc_dsp/common_audio/signal_processing/ilbc_specific_functions.c \
./libtgvoip/webrtc_dsp/common_audio/signal_processing/complex_bit_reverse.c \
./libtgvoip/webrtc_dsp/common_audio/signal_processing/randomization_functions.c \
./libtgvoip/webrtc_dsp/common_audio/signal_processing/copy_set_operations.c \
./libtgvoip/webrtc_dsp/common_audio/signal_processing/resample_by_2.c \
./libtgvoip/webrtc_dsp/common_audio/signal_processing/get_hanning_window.c \
./libtgvoip/webrtc_dsp/common_audio/signal_processing/resample_48khz.c \
./libtgvoip/webrtc_dsp/common_audio/signal_processing/spl_inl.c \
./libtgvoip/webrtc_dsp/common_audio/signal_processing/spl_sqrt.c \
./libtgvoip/webrtc_dsp/common_audio/vad/vad_sp.c \
./libtgvoip/webrtc_dsp/common_audio/vad/vad.cc \
./libtgvoip/webrtc_dsp/common_audio/vad/webrtc_vad.c \
./libtgvoip/webrtc_dsp/common_audio/vad/vad_filterbank.c \
./libtgvoip/webrtc_dsp/common_audio/vad/vad_core.c \
./libtgvoip/webrtc_dsp/common_audio/vad/vad_gmm.c

ifeq ($(TARGET_ARCH_ABI),$(filter $(TARGET_ARCH_ABI),armeabi-v7a arm64-v8a))
LOCAL_SRC_FILES += \
./libtgvoip/webrtc_dsp/modules/audio_processing/ns/nsx_core_neon.c.neon \
./libtgvoip/webrtc_dsp/modules/audio_processing/aec/aec_core_neon.cc.neon \
./libtgvoip/webrtc_dsp/modules/audio_processing/aecm/aecm_core_neon.cc.neon \
./libtgvoip/webrtc_dsp/modules/audio_processing/utility/ooura_fft_neon.cc.neon \
./libtgvoip/webrtc_dsp/common_audio/fir_filter_neon.cc.neon \
./libtgvoip/webrtc_dsp/common_audio/resampler/sinc_resampler_neon.cc.neon \
./libtgvoip/webrtc_dsp/common_audio/signal_processing/downsample_fast_neon.c.neon \
./libtgvoip/webrtc_dsp/common_audio/signal_processing/min_max_operations_neon.c.neon \
./libtgvoip/webrtc_dsp/common_audio/signal_processing/cross_correlation_neon.c.neon
endif

ifeq ($(TARGET_ARCH_ABI),armeabi-v7a)
LOCAL_SRC_FILES += \
./libtgvoip/webrtc_dsp/common_audio/third_party/spl_sqrt_floor/spl_sqrt_floor_arm.S.neon \
./libtgvoip/webrtc_dsp/common_audio/signal_processing/complex_bit_reverse_arm.S.neon \
./libtgvoip/webrtc_dsp/common_audio/signal_processing/filter_ar_fast_q12_armv7.S.neon
endif

ifeq ($(TARGET_ARCH_ABI),$(filter $(TARGET_ARCH_ABI),x86 x86_64))
LOCAL_SRC_FILES += \
./libtgvoip/webrtc_dsp/modules/audio_processing/aec/aec_core_sse2.cc \
./libtgvoip/webrtc_dsp/modules/audio_processing/utility/ooura_fft_sse2.cc \
./libtgvoip/webrtc_dsp/common_audio/fir_filter_sse.cc \
./libtgvoip/webrtc_dsp/common_audio/resampler/sinc_resampler_sse.cc
endif

include $(BUILD_STATIC_LIBRARY)

include $(CLEAR_VARS)

LOCAL_CPPFLAGS := -Wall -std=c++11 -DANDROID -frtti -DHAVE_PTHREAD -finline-functions -ffast-math -O0
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
./tgnet/FileLoadOperation.cpp \
./tgnet/ProxyCheckInfo.cpp \
./tgnet/Handshake.cpp \
./tgnet/Config.cpp

include $(BUILD_STATIC_LIBRARY)

include $(CLEAR_VARS)

LOCAL_CFLAGS := -Wall -DANDROID -DHAVE_MALLOC_H -DHAVE_PTHREAD -DWEBP_USE_THREAD -finline-functions -ffast-math -ffunction-sections -fdata-sections -O0
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
LOCAL_CFLAGS += '-DVERSION="1.3.1"' -DFLAC__NO_MD5 -DFLAC__INTEGER_ONLY_LIBRARY -DFLAC__NO_ASM
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
LOCAL_PRELINK_MODULE := false

LOCAL_MODULE 	:= tmessages.29
LOCAL_CFLAGS 	:= -w -std=c11 -Os -DNULL=0 -DSOCKLEN_T=socklen_t -DLOCALE_NOT_USED -D_LARGEFILE_SOURCE=1
LOCAL_CFLAGS 	+= -Drestrict='' -D__EMX__ -DOPUS_BUILD -DFIXED_POINT -DUSE_ALLOCA -DHAVE_LRINT -DHAVE_LRINTF -fno-math-errno
LOCAL_CFLAGS 	+= -DANDROID_NDK -DDISABLE_IMPORTGL -fno-strict-aliasing -fprefetch-loop-arrays -DAVOID_TABLES -DANDROID_TILE_BASED_DECODE -DANDROID_ARMV6_IDCT -ffast-math -D__STDC_CONSTANT_MACROS
LOCAL_CPPFLAGS 	:= -DBSD=1 -ffast-math -Os -funroll-loops -std=c++11
LOCAL_LDLIBS 	:= -ljnigraphics -llog -lz -latomic -lOpenSLES -lEGL -lGLESv2 -landroid
LOCAL_STATIC_LIBRARIES := webp sqlite tgnet avformat avcodec avresample avutil voip flac

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
./jni/intro

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
./utils.c \
./image.c \
./video.c \
./intro/IntroRenderer.c \
./gifvideo.cpp \
./SqliteWrapper.cpp \
./TgNetWrapper.cpp \
./NativeLoader.cpp \
./emoji/emoji_suggestions_data.cpp \
./emoji/emoji_suggestions.cpp \
./exoplayer/flac_jni.cc \
./exoplayer/flac_parser.cc \
./exoplayer/opus_jni.cc \
./exoplayer/ffmpeg_jni.cc \
./libtgvoip/client/android/tg_voip_jni.cpp \
./fast-edge.cpp \
./genann.c \
./secureid_ocr.cpp

include $(BUILD_SHARED_LIBRARY)

$(call import-module,android/cpufeatures)