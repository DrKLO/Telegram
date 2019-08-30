LOCAL_PATH := $(call my-dir)

LOCAL_MODULE := voip
LOCAL_CPPFLAGS := -Wall -std=c++11 -DANDROID -finline-functions -ffast-math -Os -fno-strict-aliasing -O3 -frtti -D__STDC_LIMIT_MACROS -Wno-unknown-pragmas
LOCAL_CFLAGS := -O3 -DUSE_KISS_FFT -fexceptions -DWEBRTC_APM_DEBUG_DUMP=0 -DWEBRTC_POSIX -DWEBRTC_ANDROID -D__STDC_LIMIT_MACROS -DFIXED_POINT -DWEBRTC_NS_FLOAT
LOCAL_EXPORT_LDLIBS := -llog -lOpenSLES

MY_DIR := libtgvoip

LOCAL_C_INCLUDES := $(LOCAL_PATH)/../opus/include $(LOCAL_PATH)/../boringssl/include/ $(LOCAL_PATH)/webrtc_dsp/

ifeq ($(TARGET_ARCH_ABI),$(filter $(TARGET_ARCH_ABI),armeabi-v7a arm64-v8a))
CC_NEON := cc.neon
LOCAL_CFLAGS += -DWEBRTC_HAS_NEON
else
CC_NEON := cc
endif

LOCAL_CFLAGS += $(TGVOIP_ADDITIONAL_CFLAGS)

LOCAL_SRC_FILES := \
./logging.cpp \
./VoIPController.cpp \
./VoIPGroupController.cpp \
./Buffers.cpp \
./BlockingQueue.cpp \
./audio/AudioInput.cpp \
./os/android/AudioInputOpenSLES.cpp \
./MediaStreamItf.cpp \
./audio/AudioOutput.cpp \
./OpusEncoder.cpp \
./os/android/AudioOutputOpenSLES.cpp \
./JitterBuffer.cpp \
./OpusDecoder.cpp \
./os/android/OpenSLEngineWrapper.cpp \
./os/android/AudioInputAndroid.cpp \
./os/android/AudioOutputAndroid.cpp \
./EchoCanceller.cpp \
./CongestionControl.cpp \
./VoIPServerConfig.cpp \
./audio/Resampler.cpp \
./NetworkSocket.cpp \
./os/posix/NetworkSocketPosix.cpp \
./PacketReassembler.cpp \
./MessageThread.cpp \
./json11.cpp \
./audio/AudioIO.cpp \
./video/VideoRenderer.cpp \
./video/VideoSource.cpp \
./video/ScreamCongestionController.cpp \
./os/android/VideoSourceAndroid.cpp \
./os/android/VideoRendererAndroid.cpp \
./client/android/tg_voip_jni.cpp

# WebRTC signal processing

LOCAL_SRC_FILES += \
./webrtc_dsp/system_wrappers/source/field_trial.cc \
./webrtc_dsp/system_wrappers/source/metrics.cc \
./webrtc_dsp/system_wrappers/source/cpu_features.cc \
./webrtc_dsp/absl/strings/internal/memutil.cc \
./webrtc_dsp/absl/strings/string_view.cc \
./webrtc_dsp/absl/strings/ascii.cc \
./webrtc_dsp/absl/types/bad_optional_access.cc \
./webrtc_dsp/absl/types/optional.cc \
./webrtc_dsp/absl/base/internal/raw_logging.cc \
./webrtc_dsp/absl/base/internal/throw_delegate.cc \
./webrtc_dsp/rtc_base/race_checker.cc \
./webrtc_dsp/rtc_base/strings/string_builder.cc \
./webrtc_dsp/rtc_base/memory/aligned_malloc.cc \
./webrtc_dsp/rtc_base/timeutils.cc \
./webrtc_dsp/rtc_base/platform_file.cc \
./webrtc_dsp/rtc_base/string_to_number.cc \
./webrtc_dsp/rtc_base/thread_checker_impl.cc \
./webrtc_dsp/rtc_base/stringencode.cc \
./webrtc_dsp/rtc_base/stringutils.cc \
./webrtc_dsp/rtc_base/checks.cc \
./webrtc_dsp/rtc_base/platform_thread.cc \
./webrtc_dsp/rtc_base/criticalsection.cc \
./webrtc_dsp/rtc_base/platform_thread_types.cc \
./webrtc_dsp/rtc_base/event.cc \
./webrtc_dsp/rtc_base/event_tracer.cc \
./webrtc_dsp/rtc_base/logging_webrtc.cc \
./webrtc_dsp/third_party/rnnoise/src/rnn_vad_weights.cc \
./webrtc_dsp/third_party/rnnoise/src/kiss_fft.cc \
./webrtc_dsp/api/audio/audio_frame.cc \
./webrtc_dsp/api/audio/echo_canceller3_config.cc \
./webrtc_dsp/api/audio/echo_canceller3_factory.cc \
./webrtc_dsp/modules/third_party/fft/fft.c \
./webrtc_dsp/modules/audio_coding/codecs/isac/main/source/pitch_estimator.c \
./webrtc_dsp/modules/audio_coding/codecs/isac/main/source/lpc_shape_swb16_tables.c \
./webrtc_dsp/modules/audio_coding/codecs/isac/main/source/pitch_gain_tables.c \
./webrtc_dsp/modules/audio_coding/codecs/isac/main/source/arith_routines_logist.c \
./webrtc_dsp/modules/audio_coding/codecs/isac/main/source/filterbanks.c \
./webrtc_dsp/modules/audio_coding/codecs/isac/main/source/transform.c \
./webrtc_dsp/modules/audio_coding/codecs/isac/main/source/pitch_filter.c \
./webrtc_dsp/modules/audio_coding/codecs/isac/main/source/encode_lpc_swb.c \
./webrtc_dsp/modules/audio_coding/codecs/isac/main/source/filter_functions.c \
./webrtc_dsp/modules/audio_coding/codecs/isac/main/source/decode.c \
./webrtc_dsp/modules/audio_coding/codecs/isac/main/source/lattice.c \
./webrtc_dsp/modules/audio_coding/codecs/isac/main/source/intialize.c \
./webrtc_dsp/modules/audio_coding/codecs/isac/main/source/lpc_tables.c \
./webrtc_dsp/modules/audio_coding/codecs/isac/main/source/lpc_gain_swb_tables.c \
./webrtc_dsp/modules/audio_coding/codecs/isac/main/source/bandwidth_estimator.c \
./webrtc_dsp/modules/audio_coding/codecs/isac/main/source/encode.c \
./webrtc_dsp/modules/audio_coding/codecs/isac/main/source/lpc_analysis.c \
./webrtc_dsp/modules/audio_coding/codecs/isac/main/source/arith_routines_hist.c \
./webrtc_dsp/modules/audio_coding/codecs/isac/main/source/entropy_coding.c \
./webrtc_dsp/modules/audio_coding/codecs/isac/main/source/isac_vad.c \
./webrtc_dsp/modules/audio_coding/codecs/isac/main/source/arith_routines.c \
./webrtc_dsp/modules/audio_coding/codecs/isac/main/source/crc.c \
./webrtc_dsp/modules/audio_coding/codecs/isac/main/source/lpc_shape_swb12_tables.c \
./webrtc_dsp/modules/audio_coding/codecs/isac/main/source/decode_bwe.c \
./webrtc_dsp/modules/audio_coding/codecs/isac/main/source/spectrum_ar_model_tables.c \
./webrtc_dsp/modules/audio_coding/codecs/isac/main/source/pitch_lag_tables.c \
./webrtc_dsp/modules/audio_coding/codecs/isac/main/source/isac.c \
./webrtc_dsp/modules/audio_processing/rms_level.cc \
./webrtc_dsp/modules/audio_processing/echo_detector/normalized_covariance_estimator.cc \
./webrtc_dsp/modules/audio_processing/echo_detector/moving_max.cc \
./webrtc_dsp/modules/audio_processing/echo_detector/circular_buffer.cc \
./webrtc_dsp/modules/audio_processing/echo_detector/mean_variance_estimator.cc \
./webrtc_dsp/modules/audio_processing/splitting_filter.cc \
./webrtc_dsp/modules/audio_processing/gain_control_impl.cc \
./webrtc_dsp/modules/audio_processing/ns/nsx_core.c \
./webrtc_dsp/modules/audio_processing/ns/noise_suppression_x.c \
./webrtc_dsp/modules/audio_processing/ns/nsx_core_c.c \
./webrtc_dsp/modules/audio_processing/ns/ns_core.c \
./webrtc_dsp/modules/audio_processing/ns/noise_suppression.c \
./webrtc_dsp/modules/audio_processing/audio_buffer.cc \
./webrtc_dsp/modules/audio_processing/typing_detection.cc \
./webrtc_dsp/modules/audio_processing/include/audio_processing_statistics.cc \
./webrtc_dsp/modules/audio_processing/include/audio_generator_factory.cc \
./webrtc_dsp/modules/audio_processing/include/aec_dump.cc \
./webrtc_dsp/modules/audio_processing/include/audio_processing.cc \
./webrtc_dsp/modules/audio_processing/include/config.cc \
./webrtc_dsp/modules/audio_processing/agc2/interpolated_gain_curve.cc \
./webrtc_dsp/modules/audio_processing/agc2/agc2_common.cc \
./webrtc_dsp/modules/audio_processing/agc2/gain_applier.cc \
./webrtc_dsp/modules/audio_processing/agc2/adaptive_agc.cc \
./webrtc_dsp/modules/audio_processing/agc2/adaptive_digital_gain_applier.cc \
./webrtc_dsp/modules/audio_processing/agc2/limiter.cc \
./webrtc_dsp/modules/audio_processing/agc2/saturation_protector.cc \
./webrtc_dsp/modules/audio_processing/agc2/rnn_vad/spectral_features_internal.cc \
./webrtc_dsp/modules/audio_processing/agc2/rnn_vad/rnn.cc \
./webrtc_dsp/modules/audio_processing/agc2/rnn_vad/pitch_search_internal.cc \
./webrtc_dsp/modules/audio_processing/agc2/rnn_vad/spectral_features.cc \
./webrtc_dsp/modules/audio_processing/agc2/rnn_vad/pitch_search.cc \
./webrtc_dsp/modules/audio_processing/agc2/rnn_vad/features_extraction.cc \
./webrtc_dsp/modules/audio_processing/agc2/rnn_vad/fft_util.cc \
./webrtc_dsp/modules/audio_processing/agc2/rnn_vad/lp_residual.cc \
./webrtc_dsp/modules/audio_processing/agc2/adaptive_mode_level_estimator_agc.cc \
./webrtc_dsp/modules/audio_processing/agc2/vector_float_frame.cc \
./webrtc_dsp/modules/audio_processing/agc2/noise_level_estimator.cc \
./webrtc_dsp/modules/audio_processing/agc2/agc2_testing_common.cc \
./webrtc_dsp/modules/audio_processing/agc2/fixed_digital_level_estimator.cc \
./webrtc_dsp/modules/audio_processing/agc2/fixed_gain_controller.cc \
./webrtc_dsp/modules/audio_processing/agc2/vad_with_level.cc \
./webrtc_dsp/modules/audio_processing/agc2/limiter_db_gain_curve.cc \
./webrtc_dsp/modules/audio_processing/agc2/down_sampler.cc \
./webrtc_dsp/modules/audio_processing/agc2/signal_classifier.cc \
./webrtc_dsp/modules/audio_processing/agc2/noise_spectrum_estimator.cc \
./webrtc_dsp/modules/audio_processing/agc2/compute_interpolated_gain_curve.cc \
./webrtc_dsp/modules/audio_processing/agc2/biquad_filter.cc \
./webrtc_dsp/modules/audio_processing/agc2/adaptive_mode_level_estimator.cc \
./webrtc_dsp/modules/audio_processing/transient/moving_moments.cc \
./webrtc_dsp/modules/audio_processing/transient/wpd_tree.cc \
./webrtc_dsp/modules/audio_processing/transient/wpd_node.cc \
./webrtc_dsp/modules/audio_processing/transient/transient_suppressor.cc \
./webrtc_dsp/modules/audio_processing/transient/transient_detector.cc \
./webrtc_dsp/modules/audio_processing/low_cut_filter.cc \
./webrtc_dsp/modules/audio_processing/level_estimator_impl.cc \
./webrtc_dsp/modules/audio_processing/three_band_filter_bank.cc \
./webrtc_dsp/modules/audio_processing/aec/echo_cancellation.cc \
./webrtc_dsp/modules/audio_processing/aec/aec_resampler.cc \
./webrtc_dsp/modules/audio_processing/aec/aec_core.cc \
./webrtc_dsp/modules/audio_processing/voice_detection_impl.cc \
./webrtc_dsp/modules/audio_processing/echo_cancellation_impl.cc \
./webrtc_dsp/modules/audio_processing/gain_control_for_experimental_agc.cc \
./webrtc_dsp/modules/audio_processing/agc/agc.cc \
./webrtc_dsp/modules/audio_processing/agc/loudness_histogram.cc \
./webrtc_dsp/modules/audio_processing/agc/agc_manager_direct.cc \
./webrtc_dsp/modules/audio_processing/agc/legacy/analog_agc.c \
./webrtc_dsp/modules/audio_processing/agc/legacy/digital_agc.c \
./webrtc_dsp/modules/audio_processing/agc/utility.cc \
./webrtc_dsp/modules/audio_processing/audio_processing_impl.cc \
./webrtc_dsp/modules/audio_processing/audio_generator/file_audio_generator.cc \
./webrtc_dsp/modules/audio_processing/gain_controller2.cc \
./webrtc_dsp/modules/audio_processing/residual_echo_detector.cc \
./webrtc_dsp/modules/audio_processing/noise_suppression_impl.cc \
./webrtc_dsp/modules/audio_processing/aecm/aecm_core.cc \
./webrtc_dsp/modules/audio_processing/aecm/aecm_core_c.cc \
./webrtc_dsp/modules/audio_processing/aecm/echo_control_mobile.cc \
./webrtc_dsp/modules/audio_processing/aec3/render_reverb_model.cc \
./webrtc_dsp/modules/audio_processing/aec3/reverb_model_fallback.cc \
./webrtc_dsp/modules/audio_processing/aec3/echo_remover_metrics.cc \
./webrtc_dsp/modules/audio_processing/aec3/matched_filter_lag_aggregator.cc \
./webrtc_dsp/modules/audio_processing/aec3/render_delay_buffer2.cc \
./webrtc_dsp/modules/audio_processing/aec3/echo_path_variability.cc \
./webrtc_dsp/modules/audio_processing/aec3/frame_blocker.cc \
./webrtc_dsp/modules/audio_processing/aec3/subtractor.cc \
./webrtc_dsp/modules/audio_processing/aec3/aec3_fft.cc \
./webrtc_dsp/modules/audio_processing/aec3/fullband_erle_estimator.cc \
./webrtc_dsp/modules/audio_processing/aec3/suppression_filter.$(CC_NEON) \
./webrtc_dsp/modules/audio_processing/aec3/block_processor.cc \
./webrtc_dsp/modules/audio_processing/aec3/subband_erle_estimator.cc \
./webrtc_dsp/modules/audio_processing/aec3/render_delay_controller_metrics.cc \
./webrtc_dsp/modules/audio_processing/aec3/render_delay_buffer.cc \
./webrtc_dsp/modules/audio_processing/aec3/vector_buffer.cc \
./webrtc_dsp/modules/audio_processing/aec3/erl_estimator.cc \
./webrtc_dsp/modules/audio_processing/aec3/aec_state.cc \
./webrtc_dsp/modules/audio_processing/aec3/adaptive_fir_filter.$(CC_NEON) \
./webrtc_dsp/modules/audio_processing/aec3/render_delay_controller.cc \
./webrtc_dsp/modules/audio_processing/aec3/skew_estimator.cc \
./webrtc_dsp/modules/audio_processing/aec3/echo_path_delay_estimator.cc \
./webrtc_dsp/modules/audio_processing/aec3/block_framer.cc \
./webrtc_dsp/modules/audio_processing/aec3/erle_estimator.cc \
./webrtc_dsp/modules/audio_processing/aec3/reverb_model.cc \
./webrtc_dsp/modules/audio_processing/aec3/cascaded_biquad_filter.cc \
./webrtc_dsp/modules/audio_processing/aec3/render_buffer.cc \
./webrtc_dsp/modules/audio_processing/aec3/subtractor_output.cc \
./webrtc_dsp/modules/audio_processing/aec3/stationarity_estimator.cc \
./webrtc_dsp/modules/audio_processing/aec3/render_signal_analyzer.cc \
./webrtc_dsp/modules/audio_processing/aec3/subtractor_output_analyzer.cc \
./webrtc_dsp/modules/audio_processing/aec3/suppression_gain.$(CC_NEON) \
./webrtc_dsp/modules/audio_processing/aec3/echo_audibility.cc \
./webrtc_dsp/modules/audio_processing/aec3/block_processor_metrics.cc \
./webrtc_dsp/modules/audio_processing/aec3/moving_average.cc \
./webrtc_dsp/modules/audio_processing/aec3/reverb_model_estimator.cc \
./webrtc_dsp/modules/audio_processing/aec3/aec3_common.cc \
./webrtc_dsp/modules/audio_processing/aec3/residual_echo_estimator.cc \
./webrtc_dsp/modules/audio_processing/aec3/matched_filter.$(CC_NEON) \
./webrtc_dsp/modules/audio_processing/aec3/reverb_decay_estimator.cc \
./webrtc_dsp/modules/audio_processing/aec3/render_delay_controller2.cc \
./webrtc_dsp/modules/audio_processing/aec3/suppression_gain_limiter.cc \
./webrtc_dsp/modules/audio_processing/aec3/main_filter_update_gain.cc \
./webrtc_dsp/modules/audio_processing/aec3/echo_remover.cc \
./webrtc_dsp/modules/audio_processing/aec3/downsampled_render_buffer.cc \
./webrtc_dsp/modules/audio_processing/aec3/matrix_buffer.cc \
./webrtc_dsp/modules/audio_processing/aec3/block_processor2.cc \
./webrtc_dsp/modules/audio_processing/aec3/echo_canceller3.cc \
./webrtc_dsp/modules/audio_processing/aec3/block_delay_buffer.cc \
./webrtc_dsp/modules/audio_processing/aec3/fft_buffer.cc \
./webrtc_dsp/modules/audio_processing/aec3/comfort_noise_generator.$(CC_NEON) \
./webrtc_dsp/modules/audio_processing/aec3/shadow_filter_update_gain.cc \
./webrtc_dsp/modules/audio_processing/aec3/filter_analyzer.cc \
./webrtc_dsp/modules/audio_processing/aec3/reverb_frequency_response.cc \
./webrtc_dsp/modules/audio_processing/aec3/decimator.cc \
./webrtc_dsp/modules/audio_processing/echo_control_mobile_impl.cc \
./webrtc_dsp/modules/audio_processing/logging/apm_data_dumper.cc \
./webrtc_dsp/modules/audio_processing/vad/voice_activity_detector.cc \
./webrtc_dsp/modules/audio_processing/vad/standalone_vad.cc \
./webrtc_dsp/modules/audio_processing/vad/pitch_internal.cc \
./webrtc_dsp/modules/audio_processing/vad/vad_circular_buffer.cc \
./webrtc_dsp/modules/audio_processing/vad/vad_audio_proc.cc \
./webrtc_dsp/modules/audio_processing/vad/pole_zero_filter.cc \
./webrtc_dsp/modules/audio_processing/vad/pitch_based_vad.cc \
./webrtc_dsp/modules/audio_processing/vad/gmm.cc \
./webrtc_dsp/modules/audio_processing/utility/ooura_fft.cc \
./webrtc_dsp/modules/audio_processing/utility/delay_estimator_wrapper.cc \
./webrtc_dsp/modules/audio_processing/utility/delay_estimator.cc \
./webrtc_dsp/modules/audio_processing/utility/block_mean_calculator.cc \
./webrtc_dsp/common_audio/window_generator.cc \
./webrtc_dsp/common_audio/channel_buffer.cc \
./webrtc_dsp/common_audio/fir_filter_factory.cc \
./webrtc_dsp/common_audio/wav_header.cc \
./webrtc_dsp/common_audio/real_fourier_ooura.cc \
./webrtc_dsp/common_audio/audio_util.cc \
./webrtc_dsp/common_audio/resampler/push_sinc_resampler.cc \
./webrtc_dsp/common_audio/resampler/resampler.cc \
./webrtc_dsp/common_audio/resampler/push_resampler.cc \
./webrtc_dsp/common_audio/resampler/sinc_resampler.cc \
./webrtc_dsp/common_audio/resampler/sinusoidal_linear_chirp_source.cc \
./webrtc_dsp/common_audio/wav_file.cc \
./webrtc_dsp/common_audio/third_party/spl_sqrt_floor/spl_sqrt_floor.c \
./webrtc_dsp/common_audio/third_party/fft4g/fft4g.c \
./webrtc_dsp/common_audio/audio_converter.cc \
./webrtc_dsp/common_audio/real_fourier.cc \
./webrtc_dsp/common_audio/sparse_fir_filter.cc \
./webrtc_dsp/common_audio/smoothing_filter.cc \
./webrtc_dsp/common_audio/fir_filter_c.cc \
./webrtc_dsp/common_audio/ring_buffer.c \
./webrtc_dsp/common_audio/signal_processing/complex_fft.c \
./webrtc_dsp/common_audio/signal_processing/filter_ma_fast_q12.c \
./webrtc_dsp/common_audio/signal_processing/levinson_durbin.c \
./webrtc_dsp/common_audio/signal_processing/dot_product_with_scale.cc \
./webrtc_dsp/common_audio/signal_processing/auto_corr_to_refl_coef.c \
./webrtc_dsp/common_audio/signal_processing/resample_by_2_internal.c \
./webrtc_dsp/common_audio/signal_processing/energy.c \
./webrtc_dsp/common_audio/signal_processing/sqrt_of_one_minus_x_squared.c \
./webrtc_dsp/common_audio/signal_processing/downsample_fast.c \
./webrtc_dsp/common_audio/signal_processing/splitting_filter1.c \
./webrtc_dsp/common_audio/signal_processing/filter_ar_fast_q12.c \
./webrtc_dsp/common_audio/signal_processing/spl_init.c \
./webrtc_dsp/common_audio/signal_processing/lpc_to_refl_coef.c \
./webrtc_dsp/common_audio/signal_processing/cross_correlation.c \
./webrtc_dsp/common_audio/signal_processing/division_operations.c \
./webrtc_dsp/common_audio/signal_processing/auto_correlation.c \
./webrtc_dsp/common_audio/signal_processing/get_scaling_square.c \
./webrtc_dsp/common_audio/signal_processing/resample.c \
./webrtc_dsp/common_audio/signal_processing/min_max_operations.c \
./webrtc_dsp/common_audio/signal_processing/refl_coef_to_lpc.c \
./webrtc_dsp/common_audio/signal_processing/filter_ar.c \
./webrtc_dsp/common_audio/signal_processing/vector_scaling_operations.c \
./webrtc_dsp/common_audio/signal_processing/resample_fractional.c \
./webrtc_dsp/common_audio/signal_processing/real_fft.c \
./webrtc_dsp/common_audio/signal_processing/ilbc_specific_functions.c \
./webrtc_dsp/common_audio/signal_processing/complex_bit_reverse.c \
./webrtc_dsp/common_audio/signal_processing/randomization_functions.c \
./webrtc_dsp/common_audio/signal_processing/copy_set_operations.c \
./webrtc_dsp/common_audio/signal_processing/resample_by_2.c \
./webrtc_dsp/common_audio/signal_processing/get_hanning_window.c \
./webrtc_dsp/common_audio/signal_processing/resample_48khz.c \
./webrtc_dsp/common_audio/signal_processing/spl_inl.c \
./webrtc_dsp/common_audio/signal_processing/spl_sqrt.c \
./webrtc_dsp/common_audio/vad/vad_sp.c \
./webrtc_dsp/common_audio/vad/vad.cc \
./webrtc_dsp/common_audio/vad/webrtc_vad.c \
./webrtc_dsp/common_audio/vad/vad_filterbank.c \
./webrtc_dsp/common_audio/vad/vad_core.c \
./webrtc_dsp/common_audio/vad/vad_gmm.c

ifeq ($(TARGET_ARCH_ABI),$(filter $(TARGET_ARCH_ABI),armeabi-v7a arm64-v8a))
LOCAL_SRC_FILES += \
./webrtc_dsp/modules/audio_processing/ns/nsx_core_neon.c.neon \
./webrtc_dsp/modules/audio_processing/aec/aec_core_neon.cc.neon \
./webrtc_dsp/modules/audio_processing/aecm/aecm_core_neon.cc.neon \
./webrtc_dsp/modules/audio_processing/utility/ooura_fft_neon.cc.neon \
./webrtc_dsp/common_audio/fir_filter_neon.cc.neon \
./webrtc_dsp/common_audio/resampler/sinc_resampler_neon.cc.neon \
./webrtc_dsp/common_audio/signal_processing/downsample_fast_neon.c.neon \
./webrtc_dsp/common_audio/signal_processing/min_max_operations_neon.c.neon \
./webrtc_dsp/common_audio/signal_processing/cross_correlation_neon.c.neon
endif

ifeq ($(TARGET_ARCH_ABI),armeabi-v7a)
LOCAL_SRC_FILES += \
./webrtc_dsp/common_audio/third_party/spl_sqrt_floor/spl_sqrt_floor_arm.S.neon \
./webrtc_dsp/common_audio/signal_processing/complex_bit_reverse_arm.S.neon \
./webrtc_dsp/common_audio/signal_processing/filter_ar_fast_q12_armv7.S.neon
endif

ifeq ($(TARGET_ARCH_ABI),$(filter $(TARGET_ARCH_ABI),x86 x86_64))
LOCAL_SRC_FILES += \
./webrtc_dsp/modules/audio_processing/aec/aec_core_sse2.cc \
./webrtc_dsp/modules/audio_processing/utility/ooura_fft_sse2.cc \
./webrtc_dsp/common_audio/fir_filter_sse.cc \
./webrtc_dsp/common_audio/resampler/sinc_resampler_sse.cc
endif

include $(BUILD_STATIC_LIBRARY)
