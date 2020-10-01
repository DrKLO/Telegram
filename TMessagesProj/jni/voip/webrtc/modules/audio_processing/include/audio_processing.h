/*
 *  Copyright (c) 2012 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#ifndef MODULES_AUDIO_PROCESSING_INCLUDE_AUDIO_PROCESSING_H_
#define MODULES_AUDIO_PROCESSING_INCLUDE_AUDIO_PROCESSING_H_

// MSVC++ requires this to be set before any other includes to get M_PI.
#ifndef _USE_MATH_DEFINES
#define _USE_MATH_DEFINES
#endif

#include <math.h>
#include <stddef.h>  // size_t
#include <stdio.h>   // FILE
#include <string.h>

#include <vector>

#include "absl/types/optional.h"
#include "api/array_view.h"
#include "api/audio/echo_canceller3_config.h"
#include "api/audio/echo_control.h"
#include "api/scoped_refptr.h"
#include "modules/audio_processing/include/audio_processing_statistics.h"
#include "modules/audio_processing/include/config.h"
#include "rtc_base/arraysize.h"
#include "rtc_base/deprecation.h"
#include "rtc_base/ref_count.h"
#include "rtc_base/system/file_wrapper.h"
#include "rtc_base/system/rtc_export.h"

namespace rtc {
class TaskQueue;
}  // namespace rtc

namespace webrtc {

class AecDump;
class AudioBuffer;

class StreamConfig;
class ProcessingConfig;

class EchoDetector;
class CustomAudioAnalyzer;
class CustomProcessing;

// Use to enable experimental gain control (AGC). At startup the experimental
// AGC moves the microphone volume up to |startup_min_volume| if the current
// microphone volume is set too low. The value is clamped to its operating range
// [12, 255]. Here, 255 maps to 100%.
//
// Must be provided through AudioProcessingBuilder().Create(config).
#if defined(WEBRTC_CHROMIUM_BUILD)
static const int kAgcStartupMinVolume = 85;
#else
static const int kAgcStartupMinVolume = 0;
#endif  // defined(WEBRTC_CHROMIUM_BUILD)
static constexpr int kClippedLevelMin = 70;

// To be deprecated: Please instead use the flag in the
// AudioProcessing::Config::AnalogGainController.
// TODO(webrtc:5298): Remove.
struct ExperimentalAgc {
  ExperimentalAgc() = default;
  explicit ExperimentalAgc(bool enabled) : enabled(enabled) {}
  ExperimentalAgc(bool enabled,
                  bool enabled_agc2_level_estimator,
                  bool digital_adaptive_disabled)
      : enabled(enabled),
        enabled_agc2_level_estimator(enabled_agc2_level_estimator),
        digital_adaptive_disabled(digital_adaptive_disabled) {}
  // Deprecated constructor: will be removed.
  ExperimentalAgc(bool enabled,
                  bool enabled_agc2_level_estimator,
                  bool digital_adaptive_disabled,
                  bool analyze_before_aec)
      : enabled(enabled),
        enabled_agc2_level_estimator(enabled_agc2_level_estimator),
        digital_adaptive_disabled(digital_adaptive_disabled) {}
  ExperimentalAgc(bool enabled, int startup_min_volume)
      : enabled(enabled), startup_min_volume(startup_min_volume) {}
  ExperimentalAgc(bool enabled, int startup_min_volume, int clipped_level_min)
      : enabled(enabled),
        startup_min_volume(startup_min_volume),
        clipped_level_min(clipped_level_min) {}
  static const ConfigOptionID identifier = ConfigOptionID::kExperimentalAgc;
  bool enabled = true;
  int startup_min_volume = kAgcStartupMinVolume;
  // Lowest microphone level that will be applied in response to clipping.
  int clipped_level_min = kClippedLevelMin;
  bool enabled_agc2_level_estimator = false;
  bool digital_adaptive_disabled = false;
};

// To be deprecated: Please instead use the flag in the
// AudioProcessing::Config::TransientSuppression.
//
// Use to enable experimental noise suppression. It can be set in the
// constructor or using AudioProcessing::SetExtraOptions().
// TODO(webrtc:5298): Remove.
struct ExperimentalNs {
  ExperimentalNs() : enabled(false) {}
  explicit ExperimentalNs(bool enabled) : enabled(enabled) {}
  static const ConfigOptionID identifier = ConfigOptionID::kExperimentalNs;
  bool enabled;
};

// The Audio Processing Module (APM) provides a collection of voice processing
// components designed for real-time communications software.
//
// APM operates on two audio streams on a frame-by-frame basis. Frames of the
// primary stream, on which all processing is applied, are passed to
// |ProcessStream()|. Frames of the reverse direction stream are passed to
// |ProcessReverseStream()|. On the client-side, this will typically be the
// near-end (capture) and far-end (render) streams, respectively. APM should be
// placed in the signal chain as close to the audio hardware abstraction layer
// (HAL) as possible.
//
// On the server-side, the reverse stream will normally not be used, with
// processing occurring on each incoming stream.
//
// Component interfaces follow a similar pattern and are accessed through
// corresponding getters in APM. All components are disabled at create-time,
// with default settings that are recommended for most situations. New settings
// can be applied without enabling a component. Enabling a component triggers
// memory allocation and initialization to allow it to start processing the
// streams.
//
// Thread safety is provided with the following assumptions to reduce locking
// overhead:
//   1. The stream getters and setters are called from the same thread as
//      ProcessStream(). More precisely, stream functions are never called
//      concurrently with ProcessStream().
//   2. Parameter getters are never called concurrently with the corresponding
//      setter.
//
// APM accepts only linear PCM audio data in chunks of 10 ms. The int16
// interfaces use interleaved data, while the float interfaces use deinterleaved
// data.
//
// Usage example, omitting error checking:
// AudioProcessing* apm = AudioProcessingBuilder().Create();
//
// AudioProcessing::Config config;
// config.echo_canceller.enabled = true;
// config.echo_canceller.mobile_mode = false;
//
// config.gain_controller1.enabled = true;
// config.gain_controller1.mode =
// AudioProcessing::Config::GainController1::kAdaptiveAnalog;
// config.gain_controller1.analog_level_minimum = 0;
// config.gain_controller1.analog_level_maximum = 255;
//
// config.gain_controller2.enabled = true;
//
// config.high_pass_filter.enabled = true;
//
// config.voice_detection.enabled = true;
//
// apm->ApplyConfig(config)
//
// apm->noise_reduction()->set_level(kHighSuppression);
// apm->noise_reduction()->Enable(true);
//
// // Start a voice call...
//
// // ... Render frame arrives bound for the audio HAL ...
// apm->ProcessReverseStream(render_frame);
//
// // ... Capture frame arrives from the audio HAL ...
// // Call required set_stream_ functions.
// apm->set_stream_delay_ms(delay_ms);
// apm->set_stream_analog_level(analog_level);
//
// apm->ProcessStream(capture_frame);
//
// // Call required stream_ functions.
// analog_level = apm->recommended_stream_analog_level();
// has_voice = apm->stream_has_voice();
//
// // Repeate render and capture processing for the duration of the call...
// // Start a new call...
// apm->Initialize();
//
// // Close the application...
// delete apm;
//
class RTC_EXPORT AudioProcessing : public rtc::RefCountInterface {
 public:
  // The struct below constitutes the new parameter scheme for the audio
  // processing. It is being introduced gradually and until it is fully
  // introduced, it is prone to change.
  // TODO(peah): Remove this comment once the new config scheme is fully rolled
  // out.
  //
  // The parameters and behavior of the audio processing module are controlled
  // by changing the default values in the AudioProcessing::Config struct.
  // The config is applied by passing the struct to the ApplyConfig method.
  //
  // This config is intended to be used during setup, and to enable/disable
  // top-level processing effects. Use during processing may cause undesired
  // submodule resets, affecting the audio quality. Use the RuntimeSetting
  // construct for runtime configuration.
  struct RTC_EXPORT Config {

    // Sets the properties of the audio processing pipeline.
    struct RTC_EXPORT Pipeline {
      Pipeline();

      // Maximum allowed processing rate used internally. May only be set to
      // 32000 or 48000 and any differing values will be treated as 48000. The
      // default rate is currently selected based on the CPU architecture, but
      // that logic may change.
      int maximum_internal_processing_rate;
      // Allow multi-channel processing of render audio.
      bool multi_channel_render = false;
      // Allow multi-channel processing of capture audio when AEC3 is active
      // or a custom AEC is injected..
      bool multi_channel_capture = false;
    } pipeline;

    // Enabled the pre-amplifier. It amplifies the capture signal
    // before any other processing is done.
    struct PreAmplifier {
      bool enabled = false;
      float fixed_gain_factor = 1.f;
    } pre_amplifier;

    struct HighPassFilter {
      bool enabled = false;
      bool apply_in_full_band = true;
    } high_pass_filter;

    struct EchoCanceller {
      bool enabled = false;
      bool mobile_mode = false;
      bool export_linear_aec_output = false;
      // Enforce the highpass filter to be on (has no effect for the mobile
      // mode).
      bool enforce_high_pass_filtering = true;
    } echo_canceller;

    // Enables background noise suppression.
    struct NoiseSuppression {
      bool enabled = false;
      enum Level { kLow, kModerate, kHigh, kVeryHigh };
      Level level = kModerate;
      bool analyze_linear_aec_output_when_available = false;
    } noise_suppression;

    // Enables transient suppression.
    struct TransientSuppression {
      bool enabled = false;
    } transient_suppression;

    // Enables reporting of |voice_detected| in webrtc::AudioProcessingStats.
    struct VoiceDetection {
      bool enabled = false;
    } voice_detection;

    // Enables automatic gain control (AGC) functionality.
    // The automatic gain control (AGC) component brings the signal to an
    // appropriate range. This is done by applying a digital gain directly and,
    // in the analog mode, prescribing an analog gain to be applied at the audio
    // HAL.
    // Recommended to be enabled on the client-side.
    struct GainController1 {
      bool enabled = false;
      enum Mode {
        // Adaptive mode intended for use if an analog volume control is
        // available on the capture device. It will require the user to provide
        // coupling between the OS mixer controls and AGC through the
        // stream_analog_level() functions.
        // It consists of an analog gain prescription for the audio device and a
        // digital compression stage.
        kAdaptiveAnalog,
        // Adaptive mode intended for situations in which an analog volume
        // control is unavailable. It operates in a similar fashion to the
        // adaptive analog mode, but with scaling instead applied in the digital
        // domain. As with the analog mode, it additionally uses a digital
        // compression stage.
        kAdaptiveDigital,
        // Fixed mode which enables only the digital compression stage also used
        // by the two adaptive modes.
        // It is distinguished from the adaptive modes by considering only a
        // short time-window of the input signal. It applies a fixed gain
        // through most of the input level range, and compresses (gradually
        // reduces gain with increasing level) the input signal at higher
        // levels. This mode is preferred on embedded devices where the capture
        // signal level is predictable, so that a known gain can be applied.
        kFixedDigital
      };
      Mode mode = kAdaptiveAnalog;
      // Sets the target peak level (or envelope) of the AGC in dBFs (decibels
      // from digital full-scale). The convention is to use positive values. For
      // instance, passing in a value of 3 corresponds to -3 dBFs, or a target
      // level 3 dB below full-scale. Limited to [0, 31].
      int target_level_dbfs = 3;
      // Sets the maximum gain the digital compression stage may apply, in dB. A
      // higher number corresponds to greater compression, while a value of 0
      // will leave the signal uncompressed. Limited to [0, 90].
      // For updates after APM setup, use a RuntimeSetting instead.
      int compression_gain_db = 9;
      // When enabled, the compression stage will hard limit the signal to the
      // target level. Otherwise, the signal will be compressed but not limited
      // above the target level.
      bool enable_limiter = true;
      // Sets the minimum and maximum analog levels of the audio capture device.
      // Must be set if an analog mode is used. Limited to [0, 65535].
      int analog_level_minimum = 0;
      int analog_level_maximum = 255;

      // Enables the analog gain controller functionality.
      struct AnalogGainController {
        bool enabled = true;
        int startup_min_volume = kAgcStartupMinVolume;
        // Lowest analog microphone level that will be applied in response to
        // clipping.
        int clipped_level_min = kClippedLevelMin;
        bool enable_agc2_level_estimator = false;
        bool enable_digital_adaptive = true;
      } analog_gain_controller;
    } gain_controller1;

    // Enables the next generation AGC functionality. This feature replaces the
    // standard methods of gain control in the previous AGC. Enabling this
    // submodule enables an adaptive digital AGC followed by a limiter. By
    // setting |fixed_gain_db|, the limiter can be turned into a compressor that
    // first applies a fixed gain. The adaptive digital AGC can be turned off by
    // setting |adaptive_digital_mode=false|.
    struct GainController2 {
      enum LevelEstimator { kRms, kPeak };
      bool enabled = false;
      struct {
        float gain_db = 0.f;
      } fixed_digital;
      struct {
        bool enabled = false;
        LevelEstimator level_estimator = kRms;
        bool use_saturation_protector = true;
        float extra_saturation_margin_db = 2.f;
      } adaptive_digital;
    } gain_controller2;

    struct ResidualEchoDetector {
      bool enabled = true;
    } residual_echo_detector;

    // Enables reporting of |output_rms_dbfs| in webrtc::AudioProcessingStats.
    struct LevelEstimation {
      bool enabled = false;
    } level_estimation;

    std::string ToString() const;
  };

  // TODO(mgraczyk): Remove once all methods that use ChannelLayout are gone.
  enum ChannelLayout {
    kMono,
    // Left, right.
    kStereo,
    // Mono, keyboard, and mic.
    kMonoAndKeyboard,
    // Left, right, keyboard, and mic.
    kStereoAndKeyboard
  };

  // Specifies the properties of a setting to be passed to AudioProcessing at
  // runtime.
  class RuntimeSetting {
   public:
    enum class Type {
      kNotSpecified,
      kCapturePreGain,
      kCaptureCompressionGain,
      kCaptureFixedPostGain,
      kPlayoutVolumeChange,
      kCustomRenderProcessingRuntimeSetting,
      kPlayoutAudioDeviceChange
    };

    // Play-out audio device properties.
    struct PlayoutAudioDeviceInfo {
      int id;          // Identifies the audio device.
      int max_volume;  // Maximum play-out volume.
    };

    RuntimeSetting() : type_(Type::kNotSpecified), value_(0.f) {}
    ~RuntimeSetting() = default;

    static RuntimeSetting CreateCapturePreGain(float gain) {
      RTC_DCHECK_GE(gain, 1.f) << "Attenuation is not allowed.";
      return {Type::kCapturePreGain, gain};
    }

    // Corresponds to Config::GainController1::compression_gain_db, but for
    // runtime configuration.
    static RuntimeSetting CreateCompressionGainDb(int gain_db) {
      RTC_DCHECK_GE(gain_db, 0);
      RTC_DCHECK_LE(gain_db, 90);
      return {Type::kCaptureCompressionGain, static_cast<float>(gain_db)};
    }

    // Corresponds to Config::GainController2::fixed_digital::gain_db, but for
    // runtime configuration.
    static RuntimeSetting CreateCaptureFixedPostGain(float gain_db) {
      RTC_DCHECK_GE(gain_db, 0.f);
      RTC_DCHECK_LE(gain_db, 90.f);
      return {Type::kCaptureFixedPostGain, gain_db};
    }

    // Creates a runtime setting to notify play-out (aka render) audio device
    // changes.
    static RuntimeSetting CreatePlayoutAudioDeviceChange(
        PlayoutAudioDeviceInfo audio_device) {
      return {Type::kPlayoutAudioDeviceChange, audio_device};
    }

    // Creates a runtime setting to notify play-out (aka render) volume changes.
    // |volume| is the unnormalized volume, the maximum of which
    static RuntimeSetting CreatePlayoutVolumeChange(int volume) {
      return {Type::kPlayoutVolumeChange, volume};
    }

    static RuntimeSetting CreateCustomRenderSetting(float payload) {
      return {Type::kCustomRenderProcessingRuntimeSetting, payload};
    }

    Type type() const { return type_; }
    // Getters do not return a value but instead modify the argument to protect
    // from implicit casting.
    void GetFloat(float* value) const {
      RTC_DCHECK(value);
      *value = value_.float_value;
    }
    void GetInt(int* value) const {
      RTC_DCHECK(value);
      *value = value_.int_value;
    }
    void GetPlayoutAudioDeviceInfo(PlayoutAudioDeviceInfo* value) const {
      RTC_DCHECK(value);
      *value = value_.playout_audio_device_info;
    }

   private:
    RuntimeSetting(Type id, float value) : type_(id), value_(value) {}
    RuntimeSetting(Type id, int value) : type_(id), value_(value) {}
    RuntimeSetting(Type id, PlayoutAudioDeviceInfo value)
        : type_(id), value_(value) {}
    Type type_;
    union U {
      U() {}
      U(int value) : int_value(value) {}
      U(float value) : float_value(value) {}
      U(PlayoutAudioDeviceInfo value) : playout_audio_device_info(value) {}
      float float_value;
      int int_value;
      PlayoutAudioDeviceInfo playout_audio_device_info;
    } value_;
  };

  ~AudioProcessing() override {}

  // Initializes internal states, while retaining all user settings. This
  // should be called before beginning to process a new audio stream. However,
  // it is not necessary to call before processing the first stream after
  // creation.
  //
  // It is also not necessary to call if the audio parameters (sample
  // rate and number of channels) have changed. Passing updated parameters
  // directly to |ProcessStream()| and |ProcessReverseStream()| is permissible.
  // If the parameters are known at init-time though, they may be provided.
  virtual int Initialize() = 0;

  // The int16 interfaces require:
  //   - only |NativeRate|s be used
  //   - that the input, output and reverse rates must match
  //   - that |processing_config.output_stream()| matches
  //     |processing_config.input_stream()|.
  //
  // The float interfaces accept arbitrary rates and support differing input and
  // output layouts, but the output must have either one channel or the same
  // number of channels as the input.
  virtual int Initialize(const ProcessingConfig& processing_config) = 0;

  // Initialize with unpacked parameters. See Initialize() above for details.
  //
  // TODO(mgraczyk): Remove once clients are updated to use the new interface.
  virtual int Initialize(int capture_input_sample_rate_hz,
                         int capture_output_sample_rate_hz,
                         int render_sample_rate_hz,
                         ChannelLayout capture_input_layout,
                         ChannelLayout capture_output_layout,
                         ChannelLayout render_input_layout) = 0;

  // TODO(peah): This method is a temporary solution used to take control
  // over the parameters in the audio processing module and is likely to change.
  virtual void ApplyConfig(const Config& config) = 0;

  // Pass down additional options which don't have explicit setters. This
  // ensures the options are applied immediately.
  virtual void SetExtraOptions(const webrtc::Config& config) = 0;

  // TODO(ajm): Only intended for internal use. Make private and friend the
  // necessary classes?
  virtual int proc_sample_rate_hz() const = 0;
  virtual int proc_split_sample_rate_hz() const = 0;
  virtual size_t num_input_channels() const = 0;
  virtual size_t num_proc_channels() const = 0;
  virtual size_t num_output_channels() const = 0;
  virtual size_t num_reverse_channels() const = 0;

  // Set to true when the output of AudioProcessing will be muted or in some
  // other way not used. Ideally, the captured audio would still be processed,
  // but some components may change behavior based on this information.
  // Default false.
  virtual void set_output_will_be_muted(bool muted) = 0;

  // Enqueue a runtime setting.
  virtual void SetRuntimeSetting(RuntimeSetting setting) = 0;

  // Accepts and produces a 10 ms frame interleaved 16 bit integer audio as
  // specified in |input_config| and |output_config|. |src| and |dest| may use
  // the same memory, if desired.
  virtual int ProcessStream(const int16_t* const src,
                            const StreamConfig& input_config,
                            const StreamConfig& output_config,
                            int16_t* const dest) = 0;

  // Accepts deinterleaved float audio with the range [-1, 1]. Each element of
  // |src| points to a channel buffer, arranged according to |input_stream|. At
  // output, the channels will be arranged according to |output_stream| in
  // |dest|.
  //
  // The output must have one channel or as many channels as the input. |src|
  // and |dest| may use the same memory, if desired.
  virtual int ProcessStream(const float* const* src,
                            const StreamConfig& input_config,
                            const StreamConfig& output_config,
                            float* const* dest) = 0;

  // Accepts and produces a 10 ms frame of interleaved 16 bit integer audio for
  // the reverse direction audio stream as specified in |input_config| and
  // |output_config|. |src| and |dest| may use the same memory, if desired.
  virtual int ProcessReverseStream(const int16_t* const src,
                                   const StreamConfig& input_config,
                                   const StreamConfig& output_config,
                                   int16_t* const dest) = 0;

  // Accepts deinterleaved float audio with the range [-1, 1]. Each element of
  // |data| points to a channel buffer, arranged according to |reverse_config|.
  virtual int ProcessReverseStream(const float* const* src,
                                   const StreamConfig& input_config,
                                   const StreamConfig& output_config,
                                   float* const* dest) = 0;

  // Accepts deinterleaved float audio with the range [-1, 1]. Each element
  // of |data| points to a channel buffer, arranged according to
  // |reverse_config|.
  virtual int AnalyzeReverseStream(const float* const* data,
                                   const StreamConfig& reverse_config) = 0;

  // Returns the most recently produced 10 ms of the linear AEC output at a rate
  // of 16 kHz. If there is more than one capture channel, a mono representation
  // of the input is returned. Returns true/false to indicate whether an output
  // returned.
  virtual bool GetLinearAecOutput(
      rtc::ArrayView<std::array<float, 160>> linear_output) const = 0;

  // This must be called prior to ProcessStream() if and only if adaptive analog
  // gain control is enabled, to pass the current analog level from the audio
  // HAL. Must be within the range provided in Config::GainController1.
  virtual void set_stream_analog_level(int level) = 0;

  // When an analog mode is set, this should be called after ProcessStream()
  // to obtain the recommended new analog level for the audio HAL. It is the
  // user's responsibility to apply this level.
  virtual int recommended_stream_analog_level() const = 0;

  // This must be called if and only if echo processing is enabled.
  //
  // Sets the |delay| in ms between ProcessReverseStream() receiving a far-end
  // frame and ProcessStream() receiving a near-end frame containing the
  // corresponding echo. On the client-side this can be expressed as
  //   delay = (t_render - t_analyze) + (t_process - t_capture)
  // where,
  //   - t_analyze is the time a frame is passed to ProcessReverseStream() and
  //     t_render is the time the first sample of the same frame is rendered by
  //     the audio hardware.
  //   - t_capture is the time the first sample of a frame is captured by the
  //     audio hardware and t_process is the time the same frame is passed to
  //     ProcessStream().
  virtual int set_stream_delay_ms(int delay) = 0;
  virtual int stream_delay_ms() const = 0;

  // Call to signal that a key press occurred (true) or did not occur (false)
  // with this chunk of audio.
  virtual void set_stream_key_pressed(bool key_pressed) = 0;

  // Creates and attaches an webrtc::AecDump for recording debugging
  // information.
  // The |worker_queue| may not be null and must outlive the created
  // AecDump instance. |max_log_size_bytes == -1| means the log size
  // will be unlimited. |handle| may not be null. The AecDump takes
  // responsibility for |handle| and closes it in the destructor. A
  // return value of true indicates that the file has been
  // sucessfully opened, while a value of false indicates that
  // opening the file failed.
  virtual bool CreateAndAttachAecDump(const std::string& file_name,
                                      int64_t max_log_size_bytes,
                                      rtc::TaskQueue* worker_queue) = 0;
  virtual bool CreateAndAttachAecDump(FILE* handle,
                                      int64_t max_log_size_bytes,
                                      rtc::TaskQueue* worker_queue) = 0;

  // TODO(webrtc:5298) Deprecated variant.
  // Attaches provided webrtc::AecDump for recording debugging
  // information. Log file and maximum file size logic is supposed to
  // be handled by implementing instance of AecDump. Calling this
  // method when another AecDump is attached resets the active AecDump
  // with a new one. This causes the d-tor of the earlier AecDump to
  // be called. The d-tor call may block until all pending logging
  // tasks are completed.
  virtual void AttachAecDump(std::unique_ptr<AecDump> aec_dump) = 0;

  // If no AecDump is attached, this has no effect. If an AecDump is
  // attached, it's destructor is called. The d-tor may block until
  // all pending logging tasks are completed.
  virtual void DetachAecDump() = 0;

  // Get audio processing statistics.
  virtual AudioProcessingStats GetStatistics() = 0;
  // TODO(webrtc:5298) Deprecated variant. The |has_remote_tracks| argument
  // should be set if there are active remote tracks (this would usually be true
  // during a call). If there are no remote tracks some of the stats will not be
  // set by AudioProcessing, because they only make sense if there is at least
  // one remote track.
  virtual AudioProcessingStats GetStatistics(bool has_remote_tracks) = 0;

  // Returns the last applied configuration.
  virtual AudioProcessing::Config GetConfig() const = 0;

  enum Error {
    // Fatal errors.
    kNoError = 0,
    kUnspecifiedError = -1,
    kCreationFailedError = -2,
    kUnsupportedComponentError = -3,
    kUnsupportedFunctionError = -4,
    kNullPointerError = -5,
    kBadParameterError = -6,
    kBadSampleRateError = -7,
    kBadDataLengthError = -8,
    kBadNumberChannelsError = -9,
    kFileError = -10,
    kStreamParameterNotSetError = -11,
    kNotEnabledError = -12,

    // Warnings are non-fatal.
    // This results when a set_stream_ parameter is out of range. Processing
    // will continue, but the parameter may have been truncated.
    kBadStreamParameterWarning = -13
  };

  // Native rates supported by the integer interfaces.
  enum NativeRate {
    kSampleRate8kHz = 8000,
    kSampleRate16kHz = 16000,
    kSampleRate32kHz = 32000,
    kSampleRate48kHz = 48000
  };

  // TODO(kwiberg): We currently need to support a compiler (Visual C++) that
  // complains if we don't explicitly state the size of the array here. Remove
  // the size when that's no longer the case.
  static constexpr int kNativeSampleRatesHz[4] = {
      kSampleRate8kHz, kSampleRate16kHz, kSampleRate32kHz, kSampleRate48kHz};
  static constexpr size_t kNumNativeSampleRates =
      arraysize(kNativeSampleRatesHz);
  static constexpr int kMaxNativeSampleRateHz =
      kNativeSampleRatesHz[kNumNativeSampleRates - 1];

  static const int kChunkSizeMs = 10;
};

class RTC_EXPORT AudioProcessingBuilder {
 public:
  AudioProcessingBuilder();
  ~AudioProcessingBuilder();
  // The AudioProcessingBuilder takes ownership of the echo_control_factory.
  AudioProcessingBuilder& SetEchoControlFactory(
      std::unique_ptr<EchoControlFactory> echo_control_factory) {
    echo_control_factory_ = std::move(echo_control_factory);
    return *this;
  }
  // The AudioProcessingBuilder takes ownership of the capture_post_processing.
  AudioProcessingBuilder& SetCapturePostProcessing(
      std::unique_ptr<CustomProcessing> capture_post_processing) {
    capture_post_processing_ = std::move(capture_post_processing);
    return *this;
  }
  // The AudioProcessingBuilder takes ownership of the render_pre_processing.
  AudioProcessingBuilder& SetRenderPreProcessing(
      std::unique_ptr<CustomProcessing> render_pre_processing) {
    render_pre_processing_ = std::move(render_pre_processing);
    return *this;
  }
  // The AudioProcessingBuilder takes ownership of the echo_detector.
  AudioProcessingBuilder& SetEchoDetector(
      rtc::scoped_refptr<EchoDetector> echo_detector) {
    echo_detector_ = std::move(echo_detector);
    return *this;
  }
  // The AudioProcessingBuilder takes ownership of the capture_analyzer.
  AudioProcessingBuilder& SetCaptureAnalyzer(
      std::unique_ptr<CustomAudioAnalyzer> capture_analyzer) {
    capture_analyzer_ = std::move(capture_analyzer);
    return *this;
  }
  // This creates an APM instance using the previously set components. Calling
  // the Create function resets the AudioProcessingBuilder to its initial state.
  AudioProcessing* Create();
  AudioProcessing* Create(const webrtc::Config& config);

 private:
  std::unique_ptr<EchoControlFactory> echo_control_factory_;
  std::unique_ptr<CustomProcessing> capture_post_processing_;
  std::unique_ptr<CustomProcessing> render_pre_processing_;
  rtc::scoped_refptr<EchoDetector> echo_detector_;
  std::unique_ptr<CustomAudioAnalyzer> capture_analyzer_;
  RTC_DISALLOW_COPY_AND_ASSIGN(AudioProcessingBuilder);
};

class StreamConfig {
 public:
  // sample_rate_hz: The sampling rate of the stream.
  //
  // num_channels: The number of audio channels in the stream, excluding the
  //               keyboard channel if it is present. When passing a
  //               StreamConfig with an array of arrays T*[N],
  //
  //                N == {num_channels + 1  if  has_keyboard
  //                     {num_channels      if  !has_keyboard
  //
  // has_keyboard: True if the stream has a keyboard channel. When has_keyboard
  //               is true, the last channel in any corresponding list of
  //               channels is the keyboard channel.
  StreamConfig(int sample_rate_hz = 0,
               size_t num_channels = 0,
               bool has_keyboard = false)
      : sample_rate_hz_(sample_rate_hz),
        num_channels_(num_channels),
        has_keyboard_(has_keyboard),
        num_frames_(calculate_frames(sample_rate_hz)) {}

  void set_sample_rate_hz(int value) {
    sample_rate_hz_ = value;
    num_frames_ = calculate_frames(value);
  }
  void set_num_channels(size_t value) { num_channels_ = value; }
  void set_has_keyboard(bool value) { has_keyboard_ = value; }

  int sample_rate_hz() const { return sample_rate_hz_; }

  // The number of channels in the stream, not including the keyboard channel if
  // present.
  size_t num_channels() const { return num_channels_; }

  bool has_keyboard() const { return has_keyboard_; }
  size_t num_frames() const { return num_frames_; }
  size_t num_samples() const { return num_channels_ * num_frames_; }

  bool operator==(const StreamConfig& other) const {
    return sample_rate_hz_ == other.sample_rate_hz_ &&
           num_channels_ == other.num_channels_ &&
           has_keyboard_ == other.has_keyboard_;
  }

  bool operator!=(const StreamConfig& other) const { return !(*this == other); }

 private:
  static size_t calculate_frames(int sample_rate_hz) {
    return static_cast<size_t>(AudioProcessing::kChunkSizeMs * sample_rate_hz /
                               1000);
  }

  int sample_rate_hz_;
  size_t num_channels_;
  bool has_keyboard_;
  size_t num_frames_;
};

class ProcessingConfig {
 public:
  enum StreamName {
    kInputStream,
    kOutputStream,
    kReverseInputStream,
    kReverseOutputStream,
    kNumStreamNames,
  };

  const StreamConfig& input_stream() const {
    return streams[StreamName::kInputStream];
  }
  const StreamConfig& output_stream() const {
    return streams[StreamName::kOutputStream];
  }
  const StreamConfig& reverse_input_stream() const {
    return streams[StreamName::kReverseInputStream];
  }
  const StreamConfig& reverse_output_stream() const {
    return streams[StreamName::kReverseOutputStream];
  }

  StreamConfig& input_stream() { return streams[StreamName::kInputStream]; }
  StreamConfig& output_stream() { return streams[StreamName::kOutputStream]; }
  StreamConfig& reverse_input_stream() {
    return streams[StreamName::kReverseInputStream];
  }
  StreamConfig& reverse_output_stream() {
    return streams[StreamName::kReverseOutputStream];
  }

  bool operator==(const ProcessingConfig& other) const {
    for (int i = 0; i < StreamName::kNumStreamNames; ++i) {
      if (this->streams[i] != other.streams[i]) {
        return false;
      }
    }
    return true;
  }

  bool operator!=(const ProcessingConfig& other) const {
    return !(*this == other);
  }

  StreamConfig streams[StreamName::kNumStreamNames];
};

// Experimental interface for a custom analysis submodule.
class CustomAudioAnalyzer {
 public:
  // (Re-) Initializes the submodule.
  virtual void Initialize(int sample_rate_hz, int num_channels) = 0;
  // Analyzes the given capture or render signal.
  virtual void Analyze(const AudioBuffer* audio) = 0;
  // Returns a string representation of the module state.
  virtual std::string ToString() const = 0;

  virtual ~CustomAudioAnalyzer() {}
};

// Interface for a custom processing submodule.
class CustomProcessing {
 public:
  // (Re-)Initializes the submodule.
  virtual void Initialize(int sample_rate_hz, int num_channels) = 0;
  // Processes the given capture or render signal.
  virtual void Process(AudioBuffer* audio) = 0;
  // Returns a string representation of the module state.
  virtual std::string ToString() const = 0;
  // Handles RuntimeSettings. TODO(webrtc:9262): make pure virtual
  // after updating dependencies.
  virtual void SetRuntimeSetting(AudioProcessing::RuntimeSetting setting);

  virtual ~CustomProcessing() {}
};

// Interface for an echo detector submodule.
class EchoDetector : public rtc::RefCountInterface {
 public:
  // (Re-)Initializes the submodule.
  virtual void Initialize(int capture_sample_rate_hz,
                          int num_capture_channels,
                          int render_sample_rate_hz,
                          int num_render_channels) = 0;

  // Analysis (not changing) of the render signal.
  virtual void AnalyzeRenderAudio(rtc::ArrayView<const float> render_audio) = 0;

  // Analysis (not changing) of the capture signal.
  virtual void AnalyzeCaptureAudio(
      rtc::ArrayView<const float> capture_audio) = 0;

  // Pack an AudioBuffer into a vector<float>.
  static void PackRenderAudioBuffer(AudioBuffer* audio,
                                    std::vector<float>* packed_buffer);

  struct Metrics {
    absl::optional<double> echo_likelihood;
    absl::optional<double> echo_likelihood_recent_max;
  };

  // Collect current metrics from the echo detector.
  virtual Metrics GetMetrics() const = 0;
};

}  // namespace webrtc

#endif  // MODULES_AUDIO_PROCESSING_INCLUDE_AUDIO_PROCESSING_H_
