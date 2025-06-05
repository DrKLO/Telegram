/*
 *  Copyright (c) 2012 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "modules/audio_processing/audio_processing_impl.h"

#include <algorithm>
#include <cstdint>
#include <cstring>
#include <memory>
#include <string>
#include <type_traits>
#include <utility>

#include "absl/base/nullability.h"
#include "absl/strings/match.h"
#include "absl/strings/string_view.h"
#include "absl/types/optional.h"
#include "api/array_view.h"
#include "api/audio/audio_frame.h"
#include "api/task_queue/task_queue_base.h"
#include "common_audio/audio_converter.h"
#include "common_audio/include/audio_util.h"
#include "modules/audio_processing/aec_dump/aec_dump_factory.h"
#include "modules/audio_processing/audio_buffer.h"
#include "modules/audio_processing/include/audio_frame_view.h"
#include "modules/audio_processing/logging/apm_data_dumper.h"
#include "modules/audio_processing/optionally_built_submodule_creators.h"
#include "rtc_base/checks.h"
#include "rtc_base/experiments/field_trial_parser.h"
#include "rtc_base/logging.h"
#include "rtc_base/time_utils.h"
#include "rtc_base/trace_event.h"
#include "system_wrappers/include/denormal_disabler.h"
#include "system_wrappers/include/field_trial.h"
#include "system_wrappers/include/metrics.h"

#define RETURN_ON_ERR(expr) \
  do {                      \
    int err = (expr);       \
    if (err != kNoError) {  \
      return err;           \
    }                       \
  } while (0)

namespace webrtc {

namespace {

bool SampleRateSupportsMultiBand(int sample_rate_hz) {
  return sample_rate_hz == AudioProcessing::kSampleRate32kHz ||
         sample_rate_hz == AudioProcessing::kSampleRate48kHz;
}

// Checks whether the high-pass filter should be done in the full-band.
bool EnforceSplitBandHpf() {
  return field_trial::IsEnabled("WebRTC-FullBandHpfKillSwitch");
}

// Checks whether AEC3 should be allowed to decide what the default
// configuration should be based on the render and capture channel configuration
// at hand.
bool UseSetupSpecificDefaultAec3Congfig() {
  return !field_trial::IsEnabled(
      "WebRTC-Aec3SetupSpecificDefaultConfigDefaultsKillSwitch");
}

// Identify the native processing rate that best handles a sample rate.
int SuitableProcessRate(int minimum_rate,
                        int max_splitting_rate,
                        bool band_splitting_required) {
  const int uppermost_native_rate =
      band_splitting_required ? max_splitting_rate : 48000;
  for (auto rate : {16000, 32000, 48000}) {
    if (rate >= uppermost_native_rate) {
      return uppermost_native_rate;
    }
    if (rate >= minimum_rate) {
      return rate;
    }
  }
  RTC_DCHECK_NOTREACHED();
  return uppermost_native_rate;
}

GainControl::Mode Agc1ConfigModeToInterfaceMode(
    AudioProcessing::Config::GainController1::Mode mode) {
  using Agc1Config = AudioProcessing::Config::GainController1;
  switch (mode) {
    case Agc1Config::kAdaptiveAnalog:
      return GainControl::kAdaptiveAnalog;
    case Agc1Config::kAdaptiveDigital:
      return GainControl::kAdaptiveDigital;
    case Agc1Config::kFixedDigital:
      return GainControl::kFixedDigital;
  }
  RTC_CHECK_NOTREACHED();
}

bool MinimizeProcessingForUnusedOutput() {
  return !field_trial::IsEnabled("WebRTC-MutedStateKillSwitch");
}

// Maximum lengths that frame of samples being passed from the render side to
// the capture side can have (does not apply to AEC3).
static const size_t kMaxAllowedValuesOfSamplesPerBand = 160;
static const size_t kMaxAllowedValuesOfSamplesPerFrame = 480;

// Maximum number of frames to buffer in the render queue.
// TODO(peah): Decrease this once we properly handle hugely unbalanced
// reverse and forward call numbers.
static const size_t kMaxNumFramesToBuffer = 100;

void PackRenderAudioBufferForEchoDetector(const AudioBuffer& audio,
                                          std::vector<float>& packed_buffer) {
  packed_buffer.clear();
  packed_buffer.insert(packed_buffer.end(), audio.channels_const()[0],
                       audio.channels_const()[0] + audio.num_frames());
}

// Options for gracefully handling processing errors.
enum class FormatErrorOutputOption {
  kOutputExactCopyOfInput,
  kOutputBroadcastCopyOfFirstInputChannel,
  kOutputSilence,
  kDoNothing
};

enum class AudioFormatValidity {
  // Format is supported by APM.
  kValidAndSupported,
  // Format has a reasonable interpretation but is not supported.
  kValidButUnsupportedSampleRate,
  // The remaining enums values signal that the audio does not have a reasonable
  // interpretation and cannot be used.
  kInvalidSampleRate,
  kInvalidChannelCount
};

AudioFormatValidity ValidateAudioFormat(const StreamConfig& config) {
  if (config.sample_rate_hz() < 0)
    return AudioFormatValidity::kInvalidSampleRate;
  if (config.num_channels() == 0)
    return AudioFormatValidity::kInvalidChannelCount;

  // Format has a reasonable interpretation, but may still be unsupported.
  if (config.sample_rate_hz() < 8000 ||
      config.sample_rate_hz() > AudioBuffer::kMaxSampleRate)
    return AudioFormatValidity::kValidButUnsupportedSampleRate;

  // Format is fully supported.
  return AudioFormatValidity::kValidAndSupported;
}

int AudioFormatValidityToErrorCode(AudioFormatValidity validity) {
  switch (validity) {
    case AudioFormatValidity::kValidAndSupported:
      return AudioProcessing::kNoError;
    case AudioFormatValidity::kValidButUnsupportedSampleRate:  // fall-through
    case AudioFormatValidity::kInvalidSampleRate:
      return AudioProcessing::kBadSampleRateError;
    case AudioFormatValidity::kInvalidChannelCount:
      return AudioProcessing::kBadNumberChannelsError;
  }
  RTC_DCHECK(false);
}

// Returns an AudioProcessing::Error together with the best possible option for
// output audio content.
std::pair<int, FormatErrorOutputOption> ChooseErrorOutputOption(
    const StreamConfig& input_config,
    const StreamConfig& output_config) {
  AudioFormatValidity input_validity = ValidateAudioFormat(input_config);
  AudioFormatValidity output_validity = ValidateAudioFormat(output_config);

  if (input_validity == AudioFormatValidity::kValidAndSupported &&
      output_validity == AudioFormatValidity::kValidAndSupported &&
      (output_config.num_channels() == 1 ||
       output_config.num_channels() == input_config.num_channels())) {
    return {AudioProcessing::kNoError, FormatErrorOutputOption::kDoNothing};
  }

  int error_code = AudioFormatValidityToErrorCode(input_validity);
  if (error_code == AudioProcessing::kNoError) {
    error_code = AudioFormatValidityToErrorCode(output_validity);
  }
  if (error_code == AudioProcessing::kNoError) {
    // The individual formats are valid but there is some error - must be
    // channel mismatch.
    error_code = AudioProcessing::kBadNumberChannelsError;
  }

  FormatErrorOutputOption output_option;
  if (output_validity != AudioFormatValidity::kValidAndSupported &&
      output_validity != AudioFormatValidity::kValidButUnsupportedSampleRate) {
    // The output format is uninterpretable: cannot do anything.
    output_option = FormatErrorOutputOption::kDoNothing;
  } else if (input_validity != AudioFormatValidity::kValidAndSupported &&
             input_validity !=
                 AudioFormatValidity::kValidButUnsupportedSampleRate) {
    // The input format is uninterpretable: cannot use it, must output silence.
    output_option = FormatErrorOutputOption::kOutputSilence;
  } else if (input_config.sample_rate_hz() != output_config.sample_rate_hz()) {
    // Sample rates do not match: Cannot copy input into output, output silence.
    // Note: If the sample rates are in a supported range, we could resample.
    // However, that would significantly increase complexity of this error
    // handling code.
    output_option = FormatErrorOutputOption::kOutputSilence;
  } else if (input_config.num_channels() != output_config.num_channels()) {
    // Channel counts do not match: We cannot easily map input channels to
    // output channels.
    output_option =
        FormatErrorOutputOption::kOutputBroadcastCopyOfFirstInputChannel;
  } else {
    // The formats match exactly.
    RTC_DCHECK(input_config == output_config);
    output_option = FormatErrorOutputOption::kOutputExactCopyOfInput;
  }
  return std::make_pair(error_code, output_option);
}

// Checks if the audio format is supported. If not, the output is populated in a
// best-effort manner and an APM error code is returned.
int HandleUnsupportedAudioFormats(const int16_t* const src,
                                  const StreamConfig& input_config,
                                  const StreamConfig& output_config,
                                  int16_t* const dest) {
  RTC_DCHECK(src);
  RTC_DCHECK(dest);

  auto [error_code, output_option] =
      ChooseErrorOutputOption(input_config, output_config);
  if (error_code == AudioProcessing::kNoError)
    return AudioProcessing::kNoError;

  const size_t num_output_channels = output_config.num_channels();
  switch (output_option) {
    case FormatErrorOutputOption::kOutputSilence:
      memset(dest, 0, output_config.num_samples() * sizeof(int16_t));
      break;
    case FormatErrorOutputOption::kOutputBroadcastCopyOfFirstInputChannel:
      for (size_t i = 0; i < output_config.num_frames(); ++i) {
        int16_t sample = src[input_config.num_channels() * i];
        for (size_t ch = 0; ch < num_output_channels; ++ch) {
          dest[ch + num_output_channels * i] = sample;
        }
      }
      break;
    case FormatErrorOutputOption::kOutputExactCopyOfInput:
      memcpy(dest, src, output_config.num_samples() * sizeof(int16_t));
      break;
    case FormatErrorOutputOption::kDoNothing:
      break;
  }
  return error_code;
}

// Checks if the audio format is supported. If not, the output is populated in a
// best-effort manner and an APM error code is returned.
int HandleUnsupportedAudioFormats(const float* const* src,
                                  const StreamConfig& input_config,
                                  const StreamConfig& output_config,
                                  float* const* dest) {
  RTC_DCHECK(src);
  RTC_DCHECK(dest);
  for (size_t i = 0; i < input_config.num_channels(); ++i) {
    RTC_DCHECK(src[i]);
  }
  for (size_t i = 0; i < output_config.num_channels(); ++i) {
    RTC_DCHECK(dest[i]);
  }

  auto [error_code, output_option] =
      ChooseErrorOutputOption(input_config, output_config);
  if (error_code == AudioProcessing::kNoError)
    return AudioProcessing::kNoError;

  const size_t num_output_channels = output_config.num_channels();
  switch (output_option) {
    case FormatErrorOutputOption::kOutputSilence:
      for (size_t ch = 0; ch < num_output_channels; ++ch) {
        memset(dest[ch], 0, output_config.num_frames() * sizeof(float));
      }
      break;
    case FormatErrorOutputOption::kOutputBroadcastCopyOfFirstInputChannel:
      for (size_t ch = 0; ch < num_output_channels; ++ch) {
        memcpy(dest[ch], src[0], output_config.num_frames() * sizeof(float));
      }
      break;
    case FormatErrorOutputOption::kOutputExactCopyOfInput:
      for (size_t ch = 0; ch < num_output_channels; ++ch) {
        memcpy(dest[ch], src[ch], output_config.num_frames() * sizeof(float));
      }
      break;
    case FormatErrorOutputOption::kDoNothing:
      break;
  }
  return error_code;
}

using DownmixMethod = AudioProcessing::Config::Pipeline::DownmixMethod;

void SetDownmixMethod(AudioBuffer& buffer, DownmixMethod method) {
  switch (method) {
    case DownmixMethod::kAverageChannels:
      buffer.set_downmixing_by_averaging();
      break;
    case DownmixMethod::kUseFirstChannel:
      buffer.set_downmixing_to_specific_channel(/*channel=*/0);
      break;
  }
}

constexpr int kUnspecifiedDataDumpInputVolume = -100;

}  // namespace

// Throughout webrtc, it's assumed that success is represented by zero.
static_assert(AudioProcessing::kNoError == 0, "kNoError must be zero");

absl::optional<AudioProcessingImpl::GainController2ExperimentParams>
AudioProcessingImpl::GetGainController2ExperimentParams() {
  constexpr char kFieldTrialName[] = "WebRTC-Audio-GainController2";

  if (!field_trial::IsEnabled(kFieldTrialName)) {
    return absl::nullopt;
  }

  FieldTrialFlag enabled("Enabled", false);

  // Whether the gain control should switch to AGC2. Enabled by default.
  FieldTrialParameter<bool> switch_to_agc2("switch_to_agc2", true);

  // AGC2 input volume controller configuration.
  constexpr InputVolumeController::Config kDefaultInputVolumeControllerConfig;
  FieldTrialConstrained<int> min_input_volume(
      "min_input_volume", kDefaultInputVolumeControllerConfig.min_input_volume,
      0, 255);
  FieldTrialConstrained<int> clipped_level_min(
      "clipped_level_min",
      kDefaultInputVolumeControllerConfig.clipped_level_min, 0, 255);
  FieldTrialConstrained<int> clipped_level_step(
      "clipped_level_step",
      kDefaultInputVolumeControllerConfig.clipped_level_step, 0, 255);
  FieldTrialConstrained<double> clipped_ratio_threshold(
      "clipped_ratio_threshold",
      kDefaultInputVolumeControllerConfig.clipped_ratio_threshold, 0, 1);
  FieldTrialConstrained<int> clipped_wait_frames(
      "clipped_wait_frames",
      kDefaultInputVolumeControllerConfig.clipped_wait_frames, 0,
      absl::nullopt);
  FieldTrialParameter<bool> enable_clipping_predictor(
      "enable_clipping_predictor",
      kDefaultInputVolumeControllerConfig.enable_clipping_predictor);
  FieldTrialConstrained<int> target_range_max_dbfs(
      "target_range_max_dbfs",
      kDefaultInputVolumeControllerConfig.target_range_max_dbfs, -90, 30);
  FieldTrialConstrained<int> target_range_min_dbfs(
      "target_range_min_dbfs",
      kDefaultInputVolumeControllerConfig.target_range_min_dbfs, -90, 30);
  FieldTrialConstrained<int> update_input_volume_wait_frames(
      "update_input_volume_wait_frames",
      kDefaultInputVolumeControllerConfig.update_input_volume_wait_frames, 0,
      absl::nullopt);
  FieldTrialConstrained<double> speech_probability_threshold(
      "speech_probability_threshold",
      kDefaultInputVolumeControllerConfig.speech_probability_threshold, 0, 1);
  FieldTrialConstrained<double> speech_ratio_threshold(
      "speech_ratio_threshold",
      kDefaultInputVolumeControllerConfig.speech_ratio_threshold, 0, 1);

  // AGC2 adaptive digital controller configuration.
  constexpr AudioProcessing::Config::GainController2::AdaptiveDigital
      kDefaultAdaptiveDigitalConfig;
  FieldTrialConstrained<double> headroom_db(
      "headroom_db", kDefaultAdaptiveDigitalConfig.headroom_db, 0,
      absl::nullopt);
  FieldTrialConstrained<double> max_gain_db(
      "max_gain_db", kDefaultAdaptiveDigitalConfig.max_gain_db, 0,
      absl::nullopt);
  FieldTrialConstrained<double> initial_gain_db(
      "initial_gain_db", kDefaultAdaptiveDigitalConfig.initial_gain_db, 0,
      absl::nullopt);
  FieldTrialConstrained<double> max_gain_change_db_per_second(
      "max_gain_change_db_per_second",
      kDefaultAdaptiveDigitalConfig.max_gain_change_db_per_second, 0,
      absl::nullopt);
  FieldTrialConstrained<double> max_output_noise_level_dbfs(
      "max_output_noise_level_dbfs",
      kDefaultAdaptiveDigitalConfig.max_output_noise_level_dbfs, absl::nullopt,
      0);

  // Transient suppressor.
  FieldTrialParameter<bool> disallow_transient_suppressor_usage(
      "disallow_transient_suppressor_usage", false);

  // Field-trial based override for the input volume controller and adaptive
  // digital configs.
  ParseFieldTrial(
      {&enabled, &switch_to_agc2, &min_input_volume, &clipped_level_min,
       &clipped_level_step, &clipped_ratio_threshold, &clipped_wait_frames,
       &enable_clipping_predictor, &target_range_max_dbfs,
       &target_range_min_dbfs, &update_input_volume_wait_frames,
       &speech_probability_threshold, &speech_ratio_threshold, &headroom_db,
       &max_gain_db, &initial_gain_db, &max_gain_change_db_per_second,
       &max_output_noise_level_dbfs, &disallow_transient_suppressor_usage},
      field_trial::FindFullName(kFieldTrialName));
  // Checked already by `IsEnabled()` before parsing, therefore always true.
  RTC_DCHECK(enabled);

  const bool do_not_change_agc_config = !switch_to_agc2.Get();
  if (do_not_change_agc_config && !disallow_transient_suppressor_usage.Get()) {
    // Return an unspecifed value since, in this case, both the AGC2 and TS
    // configurations won't be adjusted.
    return absl::nullopt;
  }
  using Params = AudioProcessingImpl::GainController2ExperimentParams;
  if (do_not_change_agc_config) {
    // Return a value that leaves the AGC2 config unchanged and that always
    // disables TS.
    return Params{.agc2_config = absl::nullopt,
                  .disallow_transient_suppressor_usage = true};
  }
  // Return a value that switches all the gain control to AGC2.
  return Params{
      .agc2_config =
          Params::Agc2Config{
              .input_volume_controller =
                  {
                      .min_input_volume = min_input_volume.Get(),
                      .clipped_level_min = clipped_level_min.Get(),
                      .clipped_level_step = clipped_level_step.Get(),
                      .clipped_ratio_threshold =
                          static_cast<float>(clipped_ratio_threshold.Get()),
                      .clipped_wait_frames = clipped_wait_frames.Get(),
                      .enable_clipping_predictor =
                          enable_clipping_predictor.Get(),
                      .target_range_max_dbfs = target_range_max_dbfs.Get(),
                      .target_range_min_dbfs = target_range_min_dbfs.Get(),
                      .update_input_volume_wait_frames =
                          update_input_volume_wait_frames.Get(),
                      .speech_probability_threshold = static_cast<float>(
                          speech_probability_threshold.Get()),
                      .speech_ratio_threshold =
                          static_cast<float>(speech_ratio_threshold.Get()),
                  },
              .adaptive_digital_controller =
                  {
                      .headroom_db = static_cast<float>(headroom_db.Get()),
                      .max_gain_db = static_cast<float>(max_gain_db.Get()),
                      .initial_gain_db =
                          static_cast<float>(initial_gain_db.Get()),
                      .max_gain_change_db_per_second = static_cast<float>(
                          max_gain_change_db_per_second.Get()),
                      .max_output_noise_level_dbfs =
                          static_cast<float>(max_output_noise_level_dbfs.Get()),
                  }},
      .disallow_transient_suppressor_usage =
          disallow_transient_suppressor_usage.Get()};
}

AudioProcessing::Config AudioProcessingImpl::AdjustConfig(
    const AudioProcessing::Config& config,
    const absl::optional<AudioProcessingImpl::GainController2ExperimentParams>&
        experiment_params) {
  if (!experiment_params.has_value() ||
      (!experiment_params->agc2_config.has_value() &&
       !experiment_params->disallow_transient_suppressor_usage)) {
    // When the experiment parameters are unspecified or when the AGC and TS
    // configuration are not overridden, return the unmodified configuration.
    return config;
  }

  AudioProcessing::Config adjusted_config = config;

  // Override the transient suppressor configuration.
  if (experiment_params->disallow_transient_suppressor_usage) {
    adjusted_config.transient_suppression.enabled = false;
  }

  // Override the auto gain control configuration if the AGC1 analog gain
  // controller is active and `experiment_params->agc2_config` is specified.
  const bool agc1_analog_enabled =
      config.gain_controller1.enabled &&
      (config.gain_controller1.mode ==
           AudioProcessing::Config::GainController1::kAdaptiveAnalog ||
       config.gain_controller1.analog_gain_controller.enabled);
  if (agc1_analog_enabled && experiment_params->agc2_config.has_value()) {
    // Check that the unadjusted AGC config meets the preconditions.
    const bool hybrid_agc_config_detected =
        config.gain_controller1.enabled &&
        config.gain_controller1.analog_gain_controller.enabled &&
        !config.gain_controller1.analog_gain_controller
             .enable_digital_adaptive &&
        config.gain_controller2.enabled &&
        config.gain_controller2.adaptive_digital.enabled;
    const bool full_agc1_config_detected =
        config.gain_controller1.enabled &&
        config.gain_controller1.analog_gain_controller.enabled &&
        config.gain_controller1.analog_gain_controller
            .enable_digital_adaptive &&
        !config.gain_controller2.enabled;
    const bool one_and_only_one_input_volume_controller =
        hybrid_agc_config_detected != full_agc1_config_detected;
    const bool agc2_input_volume_controller_enabled =
        config.gain_controller2.enabled &&
        config.gain_controller2.input_volume_controller.enabled;
    if (!one_and_only_one_input_volume_controller ||
        agc2_input_volume_controller_enabled) {
      RTC_LOG(LS_ERROR) << "Cannot adjust AGC config (precondition failed)";
      if (!one_and_only_one_input_volume_controller)
        RTC_LOG(LS_ERROR)
            << "One and only one input volume controller must be enabled.";
      if (agc2_input_volume_controller_enabled)
        RTC_LOG(LS_ERROR)
            << "The AGC2 input volume controller must be disabled.";
    } else {
      adjusted_config.gain_controller1.enabled = false;
      adjusted_config.gain_controller1.analog_gain_controller.enabled = false;

      adjusted_config.gain_controller2.enabled = true;
      adjusted_config.gain_controller2.input_volume_controller.enabled = true;
      adjusted_config.gain_controller2.adaptive_digital =
          experiment_params->agc2_config->adaptive_digital_controller;
      adjusted_config.gain_controller2.adaptive_digital.enabled = true;
    }
  }

  return adjusted_config;
}

bool AudioProcessingImpl::UseApmVadSubModule(
    const AudioProcessing::Config& config,
    const absl::optional<GainController2ExperimentParams>& experiment_params) {
  // The VAD as an APM sub-module is needed only in one case, that is when TS
  // and AGC2 are both enabled and when the AGC2 experiment is running and its
  // parameters require to fully switch the gain control to AGC2.
  return config.transient_suppression.enabled &&
         config.gain_controller2.enabled &&
         (config.gain_controller2.input_volume_controller.enabled ||
          config.gain_controller2.adaptive_digital.enabled) &&
         experiment_params.has_value() &&
         experiment_params->agc2_config.has_value();
}

AudioProcessingImpl::SubmoduleStates::SubmoduleStates(
    bool capture_post_processor_enabled,
    bool render_pre_processor_enabled,
    bool capture_analyzer_enabled)
    : capture_post_processor_enabled_(capture_post_processor_enabled),
      render_pre_processor_enabled_(render_pre_processor_enabled),
      capture_analyzer_enabled_(capture_analyzer_enabled) {}

bool AudioProcessingImpl::SubmoduleStates::Update(
    bool high_pass_filter_enabled,
    bool mobile_echo_controller_enabled,
    bool noise_suppressor_enabled,
    bool adaptive_gain_controller_enabled,
    bool gain_controller2_enabled,
    bool voice_activity_detector_enabled,
    bool gain_adjustment_enabled,
    bool echo_controller_enabled,
    bool transient_suppressor_enabled) {
  bool changed = false;
  changed |= (high_pass_filter_enabled != high_pass_filter_enabled_);
  changed |=
      (mobile_echo_controller_enabled != mobile_echo_controller_enabled_);
  changed |= (noise_suppressor_enabled != noise_suppressor_enabled_);
  changed |=
      (adaptive_gain_controller_enabled != adaptive_gain_controller_enabled_);
  changed |= (gain_controller2_enabled != gain_controller2_enabled_);
  changed |=
      (voice_activity_detector_enabled != voice_activity_detector_enabled_);
  changed |= (gain_adjustment_enabled != gain_adjustment_enabled_);
  changed |= (echo_controller_enabled != echo_controller_enabled_);
  changed |= (transient_suppressor_enabled != transient_suppressor_enabled_);
  if (changed) {
    high_pass_filter_enabled_ = high_pass_filter_enabled;
    mobile_echo_controller_enabled_ = mobile_echo_controller_enabled;
    noise_suppressor_enabled_ = noise_suppressor_enabled;
    adaptive_gain_controller_enabled_ = adaptive_gain_controller_enabled;
    gain_controller2_enabled_ = gain_controller2_enabled;
    voice_activity_detector_enabled_ = voice_activity_detector_enabled;
    gain_adjustment_enabled_ = gain_adjustment_enabled;
    echo_controller_enabled_ = echo_controller_enabled;
    transient_suppressor_enabled_ = transient_suppressor_enabled;
  }

  changed |= first_update_;
  first_update_ = false;
  return changed;
}

bool AudioProcessingImpl::SubmoduleStates::CaptureMultiBandSubModulesActive()
    const {
  return CaptureMultiBandProcessingPresent();
}

bool AudioProcessingImpl::SubmoduleStates::CaptureMultiBandProcessingPresent()
    const {
  // If echo controller is present, assume it performs active processing.
  return CaptureMultiBandProcessingActive(/*ec_processing_active=*/true);
}

bool AudioProcessingImpl::SubmoduleStates::CaptureMultiBandProcessingActive(
    bool ec_processing_active) const {
  return high_pass_filter_enabled_ || mobile_echo_controller_enabled_ ||
         noise_suppressor_enabled_ || adaptive_gain_controller_enabled_ ||
         (echo_controller_enabled_ && ec_processing_active);
}

bool AudioProcessingImpl::SubmoduleStates::CaptureFullBandProcessingActive()
    const {
  return gain_controller2_enabled_ || capture_post_processor_enabled_ ||
         gain_adjustment_enabled_;
}

bool AudioProcessingImpl::SubmoduleStates::CaptureAnalyzerActive() const {
  return capture_analyzer_enabled_;
}

bool AudioProcessingImpl::SubmoduleStates::RenderMultiBandSubModulesActive()
    const {
  return RenderMultiBandProcessingActive() || mobile_echo_controller_enabled_ ||
         adaptive_gain_controller_enabled_ || echo_controller_enabled_;
}

bool AudioProcessingImpl::SubmoduleStates::RenderFullBandProcessingActive()
    const {
  return render_pre_processor_enabled_;
}

bool AudioProcessingImpl::SubmoduleStates::RenderMultiBandProcessingActive()
    const {
  return false;
}

bool AudioProcessingImpl::SubmoduleStates::HighPassFilteringRequired() const {
  return high_pass_filter_enabled_ || mobile_echo_controller_enabled_ ||
         noise_suppressor_enabled_;
}

AudioProcessingImpl::AudioProcessingImpl()
    : AudioProcessingImpl(/*config=*/{},
                          /*capture_post_processor=*/nullptr,
                          /*render_pre_processor=*/nullptr,
                          /*echo_control_factory=*/nullptr,
                          /*echo_detector=*/nullptr,
                          /*capture_analyzer=*/nullptr) {}

std::atomic<int> AudioProcessingImpl::instance_count_(0);

AudioProcessingImpl::AudioProcessingImpl(
    const AudioProcessing::Config& config,
    std::unique_ptr<CustomProcessing> capture_post_processor,
    std::unique_ptr<CustomProcessing> render_pre_processor,
    std::unique_ptr<EchoControlFactory> echo_control_factory,
    rtc::scoped_refptr<EchoDetector> echo_detector,
    std::unique_ptr<CustomAudioAnalyzer> capture_analyzer)
    : data_dumper_(new ApmDataDumper(instance_count_.fetch_add(1) + 1)),
      use_setup_specific_default_aec3_config_(
          UseSetupSpecificDefaultAec3Congfig()),
      gain_controller2_experiment_params_(GetGainController2ExperimentParams()),
      transient_suppressor_vad_mode_(TransientSuppressor::VadMode::kDefault),
      capture_runtime_settings_(RuntimeSettingQueueSize()),
      render_runtime_settings_(RuntimeSettingQueueSize()),
      capture_runtime_settings_enqueuer_(&capture_runtime_settings_),
      render_runtime_settings_enqueuer_(&render_runtime_settings_),
      echo_control_factory_(std::move(echo_control_factory)),
      config_(AdjustConfig(config, gain_controller2_experiment_params_)),
      submodule_states_(!!capture_post_processor,
                        !!render_pre_processor,
                        !!capture_analyzer),
      submodules_(std::move(capture_post_processor),
                  std::move(render_pre_processor),
                  std::move(echo_detector),
                  std::move(capture_analyzer)),
      constants_(!field_trial::IsEnabled(
                     "WebRTC-ApmExperimentalMultiChannelRenderKillSwitch"),
                 !field_trial::IsEnabled(
                     "WebRTC-ApmExperimentalMultiChannelCaptureKillSwitch"),
                 EnforceSplitBandHpf(),
                 MinimizeProcessingForUnusedOutput(),
                 field_trial::IsEnabled("WebRTC-TransientSuppressorForcedOff")),
      capture_(),
      capture_nonlocked_(),
      applied_input_volume_stats_reporter_(
          InputVolumeStatsReporter::InputVolumeType::kApplied),
      recommended_input_volume_stats_reporter_(
          InputVolumeStatsReporter::InputVolumeType::kRecommended) {
  RTC_LOG(LS_INFO) << "Injected APM submodules:"
                      "\nEcho control factory: "
                   << !!echo_control_factory_
                   << "\nEcho detector: " << !!submodules_.echo_detector
                   << "\nCapture analyzer: " << !!submodules_.capture_analyzer
                   << "\nCapture post processor: "
                   << !!submodules_.capture_post_processor
                   << "\nRender pre processor: "
                   << !!submodules_.render_pre_processor;
  if (!DenormalDisabler::IsSupported()) {
    RTC_LOG(LS_INFO) << "Denormal disabler unsupported";
  }

  RTC_LOG(LS_INFO) << "AudioProcessing: " << config_.ToString();

  // Mark Echo Controller enabled if a factory is injected.
  capture_nonlocked_.echo_controller_enabled =
      static_cast<bool>(echo_control_factory_);

  Initialize();
}

AudioProcessingImpl::~AudioProcessingImpl() = default;

int AudioProcessingImpl::Initialize() {
  // Run in a single-threaded manner during initialization.
  MutexLock lock_render(&mutex_render_);
  MutexLock lock_capture(&mutex_capture_);
  InitializeLocked();
  return kNoError;
}

int AudioProcessingImpl::Initialize(const ProcessingConfig& processing_config) {
  // Run in a single-threaded manner during initialization.
  MutexLock lock_render(&mutex_render_);
  MutexLock lock_capture(&mutex_capture_);
  InitializeLocked(processing_config);
  return kNoError;
}

void AudioProcessingImpl::MaybeInitializeRender(
    const StreamConfig& input_config,
    const StreamConfig& output_config) {
  ProcessingConfig processing_config = formats_.api_format;
  processing_config.reverse_input_stream() = input_config;
  processing_config.reverse_output_stream() = output_config;

  if (processing_config == formats_.api_format) {
    return;
  }

  MutexLock lock_capture(&mutex_capture_);
  InitializeLocked(processing_config);
}

void AudioProcessingImpl::InitializeLocked() {
  UpdateActiveSubmoduleStates();

  const int render_audiobuffer_sample_rate_hz =
      formats_.api_format.reverse_output_stream().num_frames() == 0
          ? formats_.render_processing_format.sample_rate_hz()
          : formats_.api_format.reverse_output_stream().sample_rate_hz();
  if (formats_.api_format.reverse_input_stream().num_channels() > 0) {
    render_.render_audio.reset(new AudioBuffer(
        formats_.api_format.reverse_input_stream().sample_rate_hz(),
        formats_.api_format.reverse_input_stream().num_channels(),
        formats_.render_processing_format.sample_rate_hz(),
        formats_.render_processing_format.num_channels(),
        render_audiobuffer_sample_rate_hz,
        formats_.render_processing_format.num_channels()));
    if (formats_.api_format.reverse_input_stream() !=
        formats_.api_format.reverse_output_stream()) {
      render_.render_converter = AudioConverter::Create(
          formats_.api_format.reverse_input_stream().num_channels(),
          formats_.api_format.reverse_input_stream().num_frames(),
          formats_.api_format.reverse_output_stream().num_channels(),
          formats_.api_format.reverse_output_stream().num_frames());
    } else {
      render_.render_converter.reset(nullptr);
    }
  } else {
    render_.render_audio.reset(nullptr);
    render_.render_converter.reset(nullptr);
  }

  capture_.capture_audio.reset(new AudioBuffer(
      formats_.api_format.input_stream().sample_rate_hz(),
      formats_.api_format.input_stream().num_channels(),
      capture_nonlocked_.capture_processing_format.sample_rate_hz(),
      formats_.api_format.output_stream().num_channels(),
      formats_.api_format.output_stream().sample_rate_hz(),
      formats_.api_format.output_stream().num_channels()));
  SetDownmixMethod(*capture_.capture_audio,
                   config_.pipeline.capture_downmix_method);

  if (capture_nonlocked_.capture_processing_format.sample_rate_hz() <
          formats_.api_format.output_stream().sample_rate_hz() &&
      formats_.api_format.output_stream().sample_rate_hz() == 48000) {
    capture_.capture_fullband_audio.reset(
        new AudioBuffer(formats_.api_format.input_stream().sample_rate_hz(),
                        formats_.api_format.input_stream().num_channels(),
                        formats_.api_format.output_stream().sample_rate_hz(),
                        formats_.api_format.output_stream().num_channels(),
                        formats_.api_format.output_stream().sample_rate_hz(),
                        formats_.api_format.output_stream().num_channels()));
    SetDownmixMethod(*capture_.capture_fullband_audio,
                     config_.pipeline.capture_downmix_method);
  } else {
    capture_.capture_fullband_audio.reset();
  }

  AllocateRenderQueue();

  InitializeGainController1();
  InitializeTransientSuppressor();
  InitializeHighPassFilter(true);
  InitializeResidualEchoDetector();
  InitializeEchoController();
  InitializeGainController2();
  InitializeVoiceActivityDetector();
  InitializeNoiseSuppressor();
  InitializeAnalyzer();
  InitializePostProcessor();
  InitializePreProcessor();
  InitializeCaptureLevelsAdjuster();

  if (aec_dump_) {
    aec_dump_->WriteInitMessage(formats_.api_format, rtc::TimeUTCMillis());
  }
}

void AudioProcessingImpl::InitializeLocked(const ProcessingConfig& config) {
  UpdateActiveSubmoduleStates();

  formats_.api_format = config;

  // Choose maximum rate to use for the split filtering.
  RTC_DCHECK(config_.pipeline.maximum_internal_processing_rate == 48000 ||
             config_.pipeline.maximum_internal_processing_rate == 32000);
  int max_splitting_rate = 48000;
  if (config_.pipeline.maximum_internal_processing_rate == 32000) {
    max_splitting_rate = config_.pipeline.maximum_internal_processing_rate;
  }

  int capture_processing_rate = SuitableProcessRate(
      std::min(formats_.api_format.input_stream().sample_rate_hz(),
               formats_.api_format.output_stream().sample_rate_hz()),
      max_splitting_rate,
      submodule_states_.CaptureMultiBandSubModulesActive() ||
          submodule_states_.RenderMultiBandSubModulesActive());
  RTC_DCHECK_NE(8000, capture_processing_rate);

  capture_nonlocked_.capture_processing_format =
      StreamConfig(capture_processing_rate);

  int render_processing_rate;
  if (!capture_nonlocked_.echo_controller_enabled) {
    render_processing_rate = SuitableProcessRate(
        std::min(formats_.api_format.reverse_input_stream().sample_rate_hz(),
                 formats_.api_format.reverse_output_stream().sample_rate_hz()),
        max_splitting_rate,
        submodule_states_.CaptureMultiBandSubModulesActive() ||
            submodule_states_.RenderMultiBandSubModulesActive());
  } else {
    render_processing_rate = capture_processing_rate;
  }

  // If the forward sample rate is 8 kHz, the render stream is also processed
  // at this rate.
  if (capture_nonlocked_.capture_processing_format.sample_rate_hz() ==
      kSampleRate8kHz) {
    render_processing_rate = kSampleRate8kHz;
  } else {
    render_processing_rate =
        std::max(render_processing_rate, static_cast<int>(kSampleRate16kHz));
  }

  RTC_DCHECK_NE(8000, render_processing_rate);

  if (submodule_states_.RenderMultiBandSubModulesActive()) {
    // By default, downmix the render stream to mono for analysis. This has been
    // demonstrated to work well for AEC in most practical scenarios.
    const bool multi_channel_render = config_.pipeline.multi_channel_render &&
                                      constants_.multi_channel_render_support;
    int render_processing_num_channels =
        multi_channel_render
            ? formats_.api_format.reverse_input_stream().num_channels()
            : 1;
    formats_.render_processing_format =
        StreamConfig(render_processing_rate, render_processing_num_channels);
  } else {
    formats_.render_processing_format = StreamConfig(
        formats_.api_format.reverse_input_stream().sample_rate_hz(),
        formats_.api_format.reverse_input_stream().num_channels());
  }

  if (capture_nonlocked_.capture_processing_format.sample_rate_hz() ==
          kSampleRate32kHz ||
      capture_nonlocked_.capture_processing_format.sample_rate_hz() ==
          kSampleRate48kHz) {
    capture_nonlocked_.split_rate = kSampleRate16kHz;
  } else {
    capture_nonlocked_.split_rate =
        capture_nonlocked_.capture_processing_format.sample_rate_hz();
  }

  InitializeLocked();
}

void AudioProcessingImpl::ApplyConfig(const AudioProcessing::Config& config) {
  // Run in a single-threaded manner when applying the settings.
  MutexLock lock_render(&mutex_render_);
  MutexLock lock_capture(&mutex_capture_);

  const auto adjusted_config =
      AdjustConfig(config, gain_controller2_experiment_params_);
  RTC_LOG(LS_INFO) << "AudioProcessing::ApplyConfig: "
                   << adjusted_config.ToString();

  const bool pipeline_config_changed =
      config_.pipeline.multi_channel_render !=
          adjusted_config.pipeline.multi_channel_render ||
      config_.pipeline.multi_channel_capture !=
          adjusted_config.pipeline.multi_channel_capture ||
      config_.pipeline.maximum_internal_processing_rate !=
          adjusted_config.pipeline.maximum_internal_processing_rate ||
      config_.pipeline.capture_downmix_method !=
          adjusted_config.pipeline.capture_downmix_method;

  const bool aec_config_changed =
      config_.echo_canceller.enabled !=
          adjusted_config.echo_canceller.enabled ||
      config_.echo_canceller.mobile_mode !=
          adjusted_config.echo_canceller.mobile_mode;

  const bool agc1_config_changed =
      config_.gain_controller1 != adjusted_config.gain_controller1;

  const bool agc2_config_changed =
      config_.gain_controller2 != adjusted_config.gain_controller2;

  const bool ns_config_changed =
      config_.noise_suppression.enabled !=
          adjusted_config.noise_suppression.enabled ||
      config_.noise_suppression.level !=
          adjusted_config.noise_suppression.level;

  const bool ts_config_changed = config_.transient_suppression.enabled !=
                                 adjusted_config.transient_suppression.enabled;

  const bool pre_amplifier_config_changed =
      config_.pre_amplifier.enabled != adjusted_config.pre_amplifier.enabled ||
      config_.pre_amplifier.fixed_gain_factor !=
          adjusted_config.pre_amplifier.fixed_gain_factor;

  const bool gain_adjustment_config_changed =
      config_.capture_level_adjustment !=
      adjusted_config.capture_level_adjustment;

  config_ = adjusted_config;

  if (aec_config_changed) {
    InitializeEchoController();
  }

  if (ns_config_changed) {
    InitializeNoiseSuppressor();
  }

  if (ts_config_changed) {
    InitializeTransientSuppressor();
  }

  InitializeHighPassFilter(false);

  if (agc1_config_changed) {
    InitializeGainController1();
  }

  const bool config_ok = GainController2::Validate(config_.gain_controller2);
  if (!config_ok) {
    RTC_LOG(LS_ERROR)
        << "Invalid Gain Controller 2 config; using the default config.";
    config_.gain_controller2 = AudioProcessing::Config::GainController2();
  }

  if (agc2_config_changed || ts_config_changed) {
    // AGC2 also depends on TS because of the possible dependency on the APM VAD
    // sub-module.
    InitializeGainController2();
    InitializeVoiceActivityDetector();
  }

  if (pre_amplifier_config_changed || gain_adjustment_config_changed) {
    InitializeCaptureLevelsAdjuster();
  }

  // Reinitialization must happen after all submodule configuration to avoid
  // additional reinitializations on the next capture / render processing call.
  if (pipeline_config_changed) {
    InitializeLocked(formats_.api_format);
  }
}

void AudioProcessingImpl::OverrideSubmoduleCreationForTesting(
    const ApmSubmoduleCreationOverrides& overrides) {
  MutexLock lock(&mutex_capture_);
  submodule_creation_overrides_ = overrides;
}

int AudioProcessingImpl::proc_sample_rate_hz() const {
  // Used as callback from submodules, hence locking is not allowed.
  return capture_nonlocked_.capture_processing_format.sample_rate_hz();
}

int AudioProcessingImpl::proc_fullband_sample_rate_hz() const {
  return capture_.capture_fullband_audio
             ? capture_.capture_fullband_audio->num_frames() * 100
             : capture_nonlocked_.capture_processing_format.sample_rate_hz();
}

int AudioProcessingImpl::proc_split_sample_rate_hz() const {
  // Used as callback from submodules, hence locking is not allowed.
  return capture_nonlocked_.split_rate;
}

size_t AudioProcessingImpl::num_reverse_channels() const {
  // Used as callback from submodules, hence locking is not allowed.
  return formats_.render_processing_format.num_channels();
}

size_t AudioProcessingImpl::num_input_channels() const {
  // Used as callback from submodules, hence locking is not allowed.
  return formats_.api_format.input_stream().num_channels();
}

size_t AudioProcessingImpl::num_proc_channels() const {
  // Used as callback from submodules, hence locking is not allowed.
  const bool multi_channel_capture = config_.pipeline.multi_channel_capture &&
                                     constants_.multi_channel_capture_support;
  if (capture_nonlocked_.echo_controller_enabled && !multi_channel_capture) {
    return 1;
  }
  return num_output_channels();
}

size_t AudioProcessingImpl::num_output_channels() const {
  // Used as callback from submodules, hence locking is not allowed.
  return formats_.api_format.output_stream().num_channels();
}

void AudioProcessingImpl::set_output_will_be_muted(bool muted) {
  MutexLock lock(&mutex_capture_);
  HandleCaptureOutputUsedSetting(!muted);
}

void AudioProcessingImpl::HandleCaptureOutputUsedSetting(
    bool capture_output_used) {
  capture_.capture_output_used =
      capture_output_used || !constants_.minimize_processing_for_unused_output;

  if (submodules_.agc_manager.get()) {
    submodules_.agc_manager->HandleCaptureOutputUsedChange(
        capture_.capture_output_used);
  }
  if (submodules_.echo_controller) {
    submodules_.echo_controller->SetCaptureOutputUsage(
        capture_.capture_output_used);
  }
  if (submodules_.noise_suppressor) {
    submodules_.noise_suppressor->SetCaptureOutputUsage(
        capture_.capture_output_used);
  }
  if (submodules_.gain_controller2) {
    submodules_.gain_controller2->SetCaptureOutputUsed(
        capture_.capture_output_used);
  }
}

void AudioProcessingImpl::SetRuntimeSetting(RuntimeSetting setting) {
  PostRuntimeSetting(setting);
}

bool AudioProcessingImpl::PostRuntimeSetting(RuntimeSetting setting) {
  switch (setting.type()) {
    case RuntimeSetting::Type::kCustomRenderProcessingRuntimeSetting:
    case RuntimeSetting::Type::kPlayoutAudioDeviceChange:
      return render_runtime_settings_enqueuer_.Enqueue(setting);
    case RuntimeSetting::Type::kCapturePreGain:
    case RuntimeSetting::Type::kCapturePostGain:
    case RuntimeSetting::Type::kCaptureCompressionGain:
    case RuntimeSetting::Type::kCaptureFixedPostGain:
    case RuntimeSetting::Type::kCaptureOutputUsed:
      return capture_runtime_settings_enqueuer_.Enqueue(setting);
    case RuntimeSetting::Type::kPlayoutVolumeChange: {
      bool enqueueing_successful;
      enqueueing_successful =
          capture_runtime_settings_enqueuer_.Enqueue(setting);
      enqueueing_successful =
          render_runtime_settings_enqueuer_.Enqueue(setting) &&
          enqueueing_successful;
      return enqueueing_successful;
    }
    case RuntimeSetting::Type::kNotSpecified:
      RTC_DCHECK_NOTREACHED();
      return true;
  }
  // The language allows the enum to have a non-enumerator
  // value. Check that this doesn't happen.
  RTC_DCHECK_NOTREACHED();
  return true;
}

AudioProcessingImpl::RuntimeSettingEnqueuer::RuntimeSettingEnqueuer(
    SwapQueue<RuntimeSetting>* runtime_settings)
    : runtime_settings_(*runtime_settings) {
  RTC_DCHECK(runtime_settings);
}

AudioProcessingImpl::RuntimeSettingEnqueuer::~RuntimeSettingEnqueuer() =
    default;

bool AudioProcessingImpl::RuntimeSettingEnqueuer::Enqueue(
    RuntimeSetting setting) {
  const bool successful_insert = runtime_settings_.Insert(&setting);

  if (!successful_insert) {
    RTC_LOG(LS_ERROR) << "Cannot enqueue a new runtime setting.";
  }
  return successful_insert;
}

void AudioProcessingImpl::MaybeInitializeCapture(
    const StreamConfig& input_config,
    const StreamConfig& output_config) {
  ProcessingConfig processing_config;
  bool reinitialization_required = false;
  {
    // Acquire the capture lock in order to access api_format. The lock is
    // released immediately, as we may need to acquire the render lock as part
    // of the conditional reinitialization.
    MutexLock lock_capture(&mutex_capture_);
    processing_config = formats_.api_format;
    reinitialization_required = UpdateActiveSubmoduleStates();
  }

  if (processing_config.input_stream() != input_config) {
    reinitialization_required = true;
  }

  if (processing_config.output_stream() != output_config) {
    reinitialization_required = true;
  }

  if (reinitialization_required) {
    MutexLock lock_render(&mutex_render_);
    MutexLock lock_capture(&mutex_capture_);
    // Reread the API format since the render format may have changed.
    processing_config = formats_.api_format;
    processing_config.input_stream() = input_config;
    processing_config.output_stream() = output_config;
    InitializeLocked(processing_config);
  }
}

int AudioProcessingImpl::ProcessStream(const float* const* src,
                                       const StreamConfig& input_config,
                                       const StreamConfig& output_config,
                                       float* const* dest) {
  TRACE_EVENT0("webrtc", "AudioProcessing::ProcessStream_StreamConfig");
  DenormalDisabler denormal_disabler;
  RETURN_ON_ERR(
      HandleUnsupportedAudioFormats(src, input_config, output_config, dest));
  MaybeInitializeCapture(input_config, output_config);

  MutexLock lock_capture(&mutex_capture_);

  if (aec_dump_) {
    RecordUnprocessedCaptureStream(src);
  }

  capture_.capture_audio->CopyFrom(src, formats_.api_format.input_stream());
  if (capture_.capture_fullband_audio) {
    capture_.capture_fullband_audio->CopyFrom(
        src, formats_.api_format.input_stream());
  }
  RETURN_ON_ERR(ProcessCaptureStreamLocked());
  if (capture_.capture_fullband_audio) {
    capture_.capture_fullband_audio->CopyTo(formats_.api_format.output_stream(),
                                            dest);
  } else {
    capture_.capture_audio->CopyTo(formats_.api_format.output_stream(), dest);
  }

  if (aec_dump_) {
    RecordProcessedCaptureStream(dest);
  }
  return kNoError;
}

void AudioProcessingImpl::HandleCaptureRuntimeSettings() {
  RuntimeSetting setting;
  int num_settings_processed = 0;
  while (capture_runtime_settings_.Remove(&setting)) {
    if (aec_dump_) {
      aec_dump_->WriteRuntimeSetting(setting);
    }
    switch (setting.type()) {
      case RuntimeSetting::Type::kCapturePreGain:
        if (config_.pre_amplifier.enabled ||
            config_.capture_level_adjustment.enabled) {
          float value;
          setting.GetFloat(&value);
          // If the pre-amplifier is used, apply the new gain to the
          // pre-amplifier regardless if the capture level adjustment is
          // activated. This approach allows both functionalities to coexist
          // until they have been properly merged.
          if (config_.pre_amplifier.enabled) {
            config_.pre_amplifier.fixed_gain_factor = value;
          } else {
            config_.capture_level_adjustment.pre_gain_factor = value;
          }

          // Use both the pre-amplifier and the capture level adjustment gains
          // as pre-gains.
          float gain = 1.f;
          if (config_.pre_amplifier.enabled) {
            gain *= config_.pre_amplifier.fixed_gain_factor;
          }
          if (config_.capture_level_adjustment.enabled) {
            gain *= config_.capture_level_adjustment.pre_gain_factor;
          }

          submodules_.capture_levels_adjuster->SetPreGain(gain);
        }
        // TODO(bugs.chromium.org/9138): Log setting handling by Aec Dump.
        break;
      case RuntimeSetting::Type::kCapturePostGain:
        if (config_.capture_level_adjustment.enabled) {
          float value;
          setting.GetFloat(&value);
          config_.capture_level_adjustment.post_gain_factor = value;
          submodules_.capture_levels_adjuster->SetPostGain(
              config_.capture_level_adjustment.post_gain_factor);
        }
        // TODO(bugs.chromium.org/9138): Log setting handling by Aec Dump.
        break;
      case RuntimeSetting::Type::kCaptureCompressionGain: {
        if (!submodules_.agc_manager &&
            !(submodules_.gain_controller2 &&
              config_.gain_controller2.input_volume_controller.enabled)) {
          float value;
          setting.GetFloat(&value);
          int int_value = static_cast<int>(value + .5f);
          config_.gain_controller1.compression_gain_db = int_value;
          if (submodules_.gain_control) {
            int error =
                submodules_.gain_control->set_compression_gain_db(int_value);
            RTC_DCHECK_EQ(kNoError, error);
          }
        }
        break;
      }
      case RuntimeSetting::Type::kCaptureFixedPostGain: {
        if (submodules_.gain_controller2) {
          float value;
          setting.GetFloat(&value);
          config_.gain_controller2.fixed_digital.gain_db = value;
          submodules_.gain_controller2->SetFixedGainDb(value);
        }
        break;
      }
      case RuntimeSetting::Type::kPlayoutVolumeChange: {
        int value;
        setting.GetInt(&value);
        capture_.playout_volume = value;
        break;
      }
      case RuntimeSetting::Type::kPlayoutAudioDeviceChange:
        RTC_DCHECK_NOTREACHED();
        break;
      case RuntimeSetting::Type::kCustomRenderProcessingRuntimeSetting:
        RTC_DCHECK_NOTREACHED();
        break;
      case RuntimeSetting::Type::kNotSpecified:
        RTC_DCHECK_NOTREACHED();
        break;
      case RuntimeSetting::Type::kCaptureOutputUsed:
        bool value;
        setting.GetBool(&value);
        HandleCaptureOutputUsedSetting(value);
        break;
    }
    ++num_settings_processed;
  }

  if (num_settings_processed >= RuntimeSettingQueueSize()) {
    // Handle overrun of the runtime settings queue, which likely will has
    // caused settings to be discarded.
    HandleOverrunInCaptureRuntimeSettingsQueue();
  }
}

void AudioProcessingImpl::HandleOverrunInCaptureRuntimeSettingsQueue() {
  // Fall back to a safe state for the case when a setting for capture output
  // usage setting has been missed.
  HandleCaptureOutputUsedSetting(/*capture_output_used=*/true);
}

void AudioProcessingImpl::HandleRenderRuntimeSettings() {
  RuntimeSetting setting;
  while (render_runtime_settings_.Remove(&setting)) {
    if (aec_dump_) {
      aec_dump_->WriteRuntimeSetting(setting);
    }
    switch (setting.type()) {
      case RuntimeSetting::Type::kPlayoutAudioDeviceChange:  // fall-through
      case RuntimeSetting::Type::kPlayoutVolumeChange:       // fall-through
      case RuntimeSetting::Type::kCustomRenderProcessingRuntimeSetting:
        if (submodules_.render_pre_processor) {
          submodules_.render_pre_processor->SetRuntimeSetting(setting);
        }
        break;
      case RuntimeSetting::Type::kCapturePreGain:          // fall-through
      case RuntimeSetting::Type::kCapturePostGain:         // fall-through
      case RuntimeSetting::Type::kCaptureCompressionGain:  // fall-through
      case RuntimeSetting::Type::kCaptureFixedPostGain:    // fall-through
      case RuntimeSetting::Type::kCaptureOutputUsed:       // fall-through
      case RuntimeSetting::Type::kNotSpecified:
        RTC_DCHECK_NOTREACHED();
        break;
    }
  }
}

void AudioProcessingImpl::QueueBandedRenderAudio(AudioBuffer* audio) {
  RTC_DCHECK_GE(160, audio->num_frames_per_band());

  if (submodules_.echo_control_mobile) {
    EchoControlMobileImpl::PackRenderAudioBuffer(audio, num_output_channels(),
                                                 num_reverse_channels(),
                                                 &aecm_render_queue_buffer_);
    RTC_DCHECK(aecm_render_signal_queue_);
    // Insert the samples into the queue.
    if (!aecm_render_signal_queue_->Insert(&aecm_render_queue_buffer_)) {
      // The data queue is full and needs to be emptied.
      EmptyQueuedRenderAudio();

      // Retry the insert (should always work).
      bool result =
          aecm_render_signal_queue_->Insert(&aecm_render_queue_buffer_);
      RTC_DCHECK(result);
    }
  }

  if (!submodules_.agc_manager && submodules_.gain_control) {
    GainControlImpl::PackRenderAudioBuffer(*audio, &agc_render_queue_buffer_);
    // Insert the samples into the queue.
    if (!agc_render_signal_queue_->Insert(&agc_render_queue_buffer_)) {
      // The data queue is full and needs to be emptied.
      EmptyQueuedRenderAudio();

      // Retry the insert (should always work).
      bool result = agc_render_signal_queue_->Insert(&agc_render_queue_buffer_);
      RTC_DCHECK(result);
    }
  }
}

void AudioProcessingImpl::QueueNonbandedRenderAudio(AudioBuffer* audio) {
  if (submodules_.echo_detector) {
    PackRenderAudioBufferForEchoDetector(*audio, red_render_queue_buffer_);
    RTC_DCHECK(red_render_signal_queue_);
    // Insert the samples into the queue.
    if (!red_render_signal_queue_->Insert(&red_render_queue_buffer_)) {
      // The data queue is full and needs to be emptied.
      EmptyQueuedRenderAudio();

      // Retry the insert (should always work).
      bool result = red_render_signal_queue_->Insert(&red_render_queue_buffer_);
      RTC_DCHECK(result);
    }
  }
}

void AudioProcessingImpl::AllocateRenderQueue() {
  const size_t new_agc_render_queue_element_max_size =
      std::max(static_cast<size_t>(1), kMaxAllowedValuesOfSamplesPerBand);

  const size_t new_red_render_queue_element_max_size =
      std::max(static_cast<size_t>(1), kMaxAllowedValuesOfSamplesPerFrame);

  // Reallocate the queues if the queue item sizes are too small to fit the
  // data to put in the queues.

  if (agc_render_queue_element_max_size_ <
      new_agc_render_queue_element_max_size) {
    agc_render_queue_element_max_size_ = new_agc_render_queue_element_max_size;

    std::vector<int16_t> template_queue_element(
        agc_render_queue_element_max_size_);

    agc_render_signal_queue_.reset(
        new SwapQueue<std::vector<int16_t>, RenderQueueItemVerifier<int16_t>>(
            kMaxNumFramesToBuffer, template_queue_element,
            RenderQueueItemVerifier<int16_t>(
                agc_render_queue_element_max_size_)));

    agc_render_queue_buffer_.resize(agc_render_queue_element_max_size_);
    agc_capture_queue_buffer_.resize(agc_render_queue_element_max_size_);
  } else {
    agc_render_signal_queue_->Clear();
  }

  if (submodules_.echo_detector) {
    if (red_render_queue_element_max_size_ <
        new_red_render_queue_element_max_size) {
      red_render_queue_element_max_size_ =
          new_red_render_queue_element_max_size;

      std::vector<float> template_queue_element(
          red_render_queue_element_max_size_);

      red_render_signal_queue_.reset(
          new SwapQueue<std::vector<float>, RenderQueueItemVerifier<float>>(
              kMaxNumFramesToBuffer, template_queue_element,
              RenderQueueItemVerifier<float>(
                  red_render_queue_element_max_size_)));

      red_render_queue_buffer_.resize(red_render_queue_element_max_size_);
      red_capture_queue_buffer_.resize(red_render_queue_element_max_size_);
    } else {
      red_render_signal_queue_->Clear();
    }
  }
}

void AudioProcessingImpl::EmptyQueuedRenderAudio() {
  MutexLock lock_capture(&mutex_capture_);
  EmptyQueuedRenderAudioLocked();
}

void AudioProcessingImpl::EmptyQueuedRenderAudioLocked() {
  if (submodules_.echo_control_mobile) {
    RTC_DCHECK(aecm_render_signal_queue_);
    while (aecm_render_signal_queue_->Remove(&aecm_capture_queue_buffer_)) {
      submodules_.echo_control_mobile->ProcessRenderAudio(
          aecm_capture_queue_buffer_);
    }
  }

  if (submodules_.gain_control) {
    while (agc_render_signal_queue_->Remove(&agc_capture_queue_buffer_)) {
      submodules_.gain_control->ProcessRenderAudio(agc_capture_queue_buffer_);
    }
  }

  if (submodules_.echo_detector) {
    while (red_render_signal_queue_->Remove(&red_capture_queue_buffer_)) {
      submodules_.echo_detector->AnalyzeRenderAudio(red_capture_queue_buffer_);
    }
  }
}

int AudioProcessingImpl::ProcessStream(const int16_t* const src,
                                       const StreamConfig& input_config,
                                       const StreamConfig& output_config,
                                       int16_t* const dest) {
  TRACE_EVENT0("webrtc", "AudioProcessing::ProcessStream_AudioFrame");

  RETURN_ON_ERR(
      HandleUnsupportedAudioFormats(src, input_config, output_config, dest));
  MaybeInitializeCapture(input_config, output_config);

  MutexLock lock_capture(&mutex_capture_);
  DenormalDisabler denormal_disabler;

  if (aec_dump_) {
    RecordUnprocessedCaptureStream(src, input_config);
  }

  capture_.capture_audio->CopyFrom(src, input_config);
  if (capture_.capture_fullband_audio) {
    capture_.capture_fullband_audio->CopyFrom(src, input_config);
  }
  RETURN_ON_ERR(ProcessCaptureStreamLocked());
  if (submodule_states_.CaptureMultiBandProcessingPresent() ||
      submodule_states_.CaptureFullBandProcessingActive()) {
    if (capture_.capture_fullband_audio) {
      capture_.capture_fullband_audio->CopyTo(output_config, dest);
    } else {
      capture_.capture_audio->CopyTo(output_config, dest);
    }
  }

  if (aec_dump_) {
    RecordProcessedCaptureStream(dest, output_config);
  }
  return kNoError;
}

int AudioProcessingImpl::ProcessCaptureStreamLocked() {
  EmptyQueuedRenderAudioLocked();
  HandleCaptureRuntimeSettings();
  DenormalDisabler denormal_disabler;

  // Ensure that not both the AEC and AECM are active at the same time.
  // TODO(peah): Simplify once the public API Enable functions for these
  // are moved to APM.
  RTC_DCHECK_LE(
      !!submodules_.echo_controller + !!submodules_.echo_control_mobile, 1);

  data_dumper_->DumpRaw(
      "applied_input_volume",
      capture_.applied_input_volume.value_or(kUnspecifiedDataDumpInputVolume));

  AudioBuffer* capture_buffer = capture_.capture_audio.get();  // For brevity.
  AudioBuffer* linear_aec_buffer = capture_.linear_aec_output.get();

  if (submodules_.high_pass_filter &&
      config_.high_pass_filter.apply_in_full_band &&
      !constants_.enforce_split_band_hpf) {
    submodules_.high_pass_filter->Process(capture_buffer,
                                          /*use_split_band_data=*/false);
  }

  if (submodules_.capture_levels_adjuster) {
    if (config_.capture_level_adjustment.analog_mic_gain_emulation.enabled) {
      // When the input volume is emulated, retrieve the volume applied to the
      // input audio and notify that to APM so that the volume is passed to the
      // active AGC.
      set_stream_analog_level_locked(
          submodules_.capture_levels_adjuster->GetAnalogMicGainLevel());
    }
    submodules_.capture_levels_adjuster->ApplyPreLevelAdjustment(
        *capture_buffer);
  }

  capture_input_rms_.Analyze(rtc::ArrayView<const float>(
      capture_buffer->channels_const()[0],
      capture_nonlocked_.capture_processing_format.num_frames()));
  const bool log_rms = ++capture_rms_interval_counter_ >= 1000;
  if (log_rms) {
    capture_rms_interval_counter_ = 0;
    RmsLevel::Levels levels = capture_input_rms_.AverageAndPeak();
    RTC_HISTOGRAM_COUNTS_LINEAR("WebRTC.Audio.ApmCaptureInputLevelAverageRms",
                                levels.average, 1, RmsLevel::kMinLevelDb, 64);
    RTC_HISTOGRAM_COUNTS_LINEAR("WebRTC.Audio.ApmCaptureInputLevelPeakRms",
                                levels.peak, 1, RmsLevel::kMinLevelDb, 64);
  }

  if (capture_.applied_input_volume.has_value()) {
    applied_input_volume_stats_reporter_.UpdateStatistics(
        *capture_.applied_input_volume);
  }

  if (submodules_.echo_controller) {
    // Determine if the echo path gain has changed by checking all the gains
    // applied before AEC.
    capture_.echo_path_gain_change = capture_.applied_input_volume_changed;

    // Detect and flag any change in the capture level adjustment pre-gain.
    if (submodules_.capture_levels_adjuster) {
      float pre_adjustment_gain =
          submodules_.capture_levels_adjuster->GetPreAdjustmentGain();
      capture_.echo_path_gain_change =
          capture_.echo_path_gain_change ||
          (capture_.prev_pre_adjustment_gain != pre_adjustment_gain &&
           capture_.prev_pre_adjustment_gain >= 0.0f);
      capture_.prev_pre_adjustment_gain = pre_adjustment_gain;
    }

    // Detect volume change.
    capture_.echo_path_gain_change =
        capture_.echo_path_gain_change ||
        (capture_.prev_playout_volume != capture_.playout_volume &&
         capture_.prev_playout_volume >= 0);
    capture_.prev_playout_volume = capture_.playout_volume;

    submodules_.echo_controller->AnalyzeCapture(capture_buffer);
  }

  if (submodules_.agc_manager) {
    submodules_.agc_manager->AnalyzePreProcess(*capture_buffer);
  }

  if (submodules_.gain_controller2 &&
      config_.gain_controller2.input_volume_controller.enabled) {
    // Expect the volume to be available if the input controller is enabled.
    RTC_DCHECK(capture_.applied_input_volume.has_value());
    if (capture_.applied_input_volume.has_value()) {
      submodules_.gain_controller2->Analyze(*capture_.applied_input_volume,
                                            *capture_buffer);
    }
  }

  if (submodule_states_.CaptureMultiBandSubModulesActive() &&
      SampleRateSupportsMultiBand(
          capture_nonlocked_.capture_processing_format.sample_rate_hz())) {
    capture_buffer->SplitIntoFrequencyBands();
  }

  const bool multi_channel_capture = config_.pipeline.multi_channel_capture &&
                                     constants_.multi_channel_capture_support;
  if (submodules_.echo_controller && !multi_channel_capture) {
    // Force down-mixing of the number of channels after the detection of
    // capture signal saturation.
    // TODO(peah): Look into ensuring that this kind of tampering with the
    // AudioBuffer functionality should not be needed.
    capture_buffer->set_num_channels(1);
  }

  if (submodules_.high_pass_filter &&
      (!config_.high_pass_filter.apply_in_full_band ||
       constants_.enforce_split_band_hpf)) {
    submodules_.high_pass_filter->Process(capture_buffer,
                                          /*use_split_band_data=*/true);
  }

  if (submodules_.gain_control) {
    RETURN_ON_ERR(
        submodules_.gain_control->AnalyzeCaptureAudio(*capture_buffer));
  }

  if ((!config_.noise_suppression.analyze_linear_aec_output_when_available ||
       !linear_aec_buffer || submodules_.echo_control_mobile) &&
      submodules_.noise_suppressor) {
    submodules_.noise_suppressor->Analyze(*capture_buffer);
  }

  if (submodules_.echo_control_mobile) {
    // Ensure that the stream delay was set before the call to the
    // AECM ProcessCaptureAudio function.
    if (!capture_.was_stream_delay_set) {
      return AudioProcessing::kStreamParameterNotSetError;
    }

    if (submodules_.noise_suppressor) {
      submodules_.noise_suppressor->Process(capture_buffer);
    }

    RETURN_ON_ERR(submodules_.echo_control_mobile->ProcessCaptureAudio(
        capture_buffer, stream_delay_ms()));
  } else {
    if (submodules_.echo_controller) {
      data_dumper_->DumpRaw("stream_delay", stream_delay_ms());

      if (capture_.was_stream_delay_set) {
        submodules_.echo_controller->SetAudioBufferDelay(stream_delay_ms());
      }

      submodules_.echo_controller->ProcessCapture(
          capture_buffer, linear_aec_buffer, capture_.echo_path_gain_change);
    }

    if (config_.noise_suppression.analyze_linear_aec_output_when_available &&
        linear_aec_buffer && submodules_.noise_suppressor) {
      submodules_.noise_suppressor->Analyze(*linear_aec_buffer);
    }

    if (submodules_.noise_suppressor) {
      submodules_.noise_suppressor->Process(capture_buffer);
    }
  }

  if (submodules_.agc_manager) {
    submodules_.agc_manager->Process(*capture_buffer);

    absl::optional<int> new_digital_gain =
        submodules_.agc_manager->GetDigitalComressionGain();
    if (new_digital_gain && submodules_.gain_control) {
      submodules_.gain_control->set_compression_gain_db(*new_digital_gain);
    }
  }

  if (submodules_.gain_control) {
    // TODO(peah): Add reporting from AEC3 whether there is echo.
    RETURN_ON_ERR(submodules_.gain_control->ProcessCaptureAudio(
        capture_buffer, /*stream_has_echo*/ false));
  }

  if (submodule_states_.CaptureMultiBandProcessingPresent() &&
      SampleRateSupportsMultiBand(
          capture_nonlocked_.capture_processing_format.sample_rate_hz())) {
    capture_buffer->MergeFrequencyBands();
  }

  if (capture_.capture_output_used) {
    if (capture_.capture_fullband_audio) {
      const auto& ec = submodules_.echo_controller;
      bool ec_active = ec ? ec->ActiveProcessing() : false;
      // Only update the fullband buffer if the multiband processing has changed
      // the signal. Keep the original signal otherwise.
      if (submodule_states_.CaptureMultiBandProcessingActive(ec_active)) {
        capture_buffer->CopyTo(capture_.capture_fullband_audio.get());
      }
      capture_buffer = capture_.capture_fullband_audio.get();
    }

    if (submodules_.echo_detector) {
      submodules_.echo_detector->AnalyzeCaptureAudio(
          rtc::ArrayView<const float>(capture_buffer->channels()[0],
                                      capture_buffer->num_frames()));
    }

    absl::optional<float> voice_probability;
    if (!!submodules_.voice_activity_detector) {
      voice_probability = submodules_.voice_activity_detector->Analyze(
          AudioFrameView<const float>(capture_buffer->channels(),
                                      capture_buffer->num_channels(),
                                      capture_buffer->num_frames()));
    }

    if (submodules_.transient_suppressor) {
      float transient_suppressor_voice_probability = 1.0f;
      switch (transient_suppressor_vad_mode_) {
        case TransientSuppressor::VadMode::kDefault:
          if (submodules_.agc_manager) {
            transient_suppressor_voice_probability =
                submodules_.agc_manager->voice_probability();
          }
          break;
        case TransientSuppressor::VadMode::kRnnVad:
          RTC_DCHECK(voice_probability.has_value());
          transient_suppressor_voice_probability = *voice_probability;
          break;
        case TransientSuppressor::VadMode::kNoVad:
          // The transient suppressor will ignore `voice_probability`.
          break;
      }
      float delayed_voice_probability =
          submodules_.transient_suppressor->Suppress(
              capture_buffer->channels()[0], capture_buffer->num_frames(),
              capture_buffer->num_channels(),
              capture_buffer->split_bands_const(0)[kBand0To8kHz],
              capture_buffer->num_frames_per_band(),
              /*reference_data=*/nullptr, /*reference_length=*/0,
              transient_suppressor_voice_probability, capture_.key_pressed);
      if (voice_probability.has_value()) {
        *voice_probability = delayed_voice_probability;
      }
    }

    // Experimental APM sub-module that analyzes `capture_buffer`.
    if (submodules_.capture_analyzer) {
      submodules_.capture_analyzer->Analyze(capture_buffer);
    }

    if (submodules_.gain_controller2) {
      // TODO(bugs.webrtc.org/7494): Let AGC2 detect applied input volume
      // changes.
      submodules_.gain_controller2->Process(
          voice_probability, capture_.applied_input_volume_changed,
          capture_buffer);
    }

    if (submodules_.capture_post_processor) {
      submodules_.capture_post_processor->Process(capture_buffer);
    }

    capture_output_rms_.Analyze(rtc::ArrayView<const float>(
        capture_buffer->channels_const()[0],
        capture_nonlocked_.capture_processing_format.num_frames()));
    if (log_rms) {
      RmsLevel::Levels levels = capture_output_rms_.AverageAndPeak();
      RTC_HISTOGRAM_COUNTS_LINEAR(
          "WebRTC.Audio.ApmCaptureOutputLevelAverageRms", levels.average, 1,
          RmsLevel::kMinLevelDb, 64);
      RTC_HISTOGRAM_COUNTS_LINEAR("WebRTC.Audio.ApmCaptureOutputLevelPeakRms",
                                  levels.peak, 1, RmsLevel::kMinLevelDb, 64);
    }

    // Compute echo-detector stats.
    if (submodules_.echo_detector) {
      auto ed_metrics = submodules_.echo_detector->GetMetrics();
      capture_.stats.residual_echo_likelihood = ed_metrics.echo_likelihood;
      capture_.stats.residual_echo_likelihood_recent_max =
          ed_metrics.echo_likelihood_recent_max;
    }
  }

  // Compute echo-controller stats.
  if (submodules_.echo_controller) {
    auto ec_metrics = submodules_.echo_controller->GetMetrics();
    capture_.stats.echo_return_loss = ec_metrics.echo_return_loss;
    capture_.stats.echo_return_loss_enhancement =
        ec_metrics.echo_return_loss_enhancement;
    capture_.stats.delay_ms = ec_metrics.delay_ms;
  }

  // Pass stats for reporting.
  stats_reporter_.UpdateStatistics(capture_.stats);

  UpdateRecommendedInputVolumeLocked();
  if (capture_.recommended_input_volume.has_value()) {
    recommended_input_volume_stats_reporter_.UpdateStatistics(
        *capture_.recommended_input_volume);
  }

  if (submodules_.capture_levels_adjuster) {
    submodules_.capture_levels_adjuster->ApplyPostLevelAdjustment(
        *capture_buffer);

    if (config_.capture_level_adjustment.analog_mic_gain_emulation.enabled) {
      // If the input volume emulation is used, retrieve the recommended input
      // volume and set that to emulate the input volume on the next processed
      // audio frame.
      RTC_DCHECK(capture_.recommended_input_volume.has_value());
      submodules_.capture_levels_adjuster->SetAnalogMicGainLevel(
          *capture_.recommended_input_volume);
    }
  }

  // Temporarily set the output to zero after the stream has been unmuted
  // (capture output is again used). The purpose of this is to avoid clicks and
  // artefacts in the audio that results when the processing again is
  // reactivated after unmuting.
  if (!capture_.capture_output_used_last_frame &&
      capture_.capture_output_used) {
    for (size_t ch = 0; ch < capture_buffer->num_channels(); ++ch) {
      rtc::ArrayView<float> channel_view(capture_buffer->channels()[ch],
                                         capture_buffer->num_frames());
      std::fill(channel_view.begin(), channel_view.end(), 0.f);
    }
  }
  capture_.capture_output_used_last_frame = capture_.capture_output_used;

  capture_.was_stream_delay_set = false;

  data_dumper_->DumpRaw("recommended_input_volume",
                        capture_.recommended_input_volume.value_or(
                            kUnspecifiedDataDumpInputVolume));

  return kNoError;
}

int AudioProcessingImpl::AnalyzeReverseStream(
    const float* const* data,
    const StreamConfig& reverse_config) {
  TRACE_EVENT0("webrtc", "AudioProcessing::AnalyzeReverseStream_StreamConfig");
  MutexLock lock(&mutex_render_);
  DenormalDisabler denormal_disabler;
  RTC_DCHECK(data);
  for (size_t i = 0; i < reverse_config.num_channels(); ++i) {
    RTC_DCHECK(data[i]);
  }
  RETURN_ON_ERR(
      AudioFormatValidityToErrorCode(ValidateAudioFormat(reverse_config)));

  MaybeInitializeRender(reverse_config, reverse_config);
  return AnalyzeReverseStreamLocked(data, reverse_config, reverse_config);
}

int AudioProcessingImpl::ProcessReverseStream(const float* const* src,
                                              const StreamConfig& input_config,
                                              const StreamConfig& output_config,
                                              float* const* dest) {
  TRACE_EVENT0("webrtc", "AudioProcessing::ProcessReverseStream_StreamConfig");
  MutexLock lock(&mutex_render_);
  DenormalDisabler denormal_disabler;
  RETURN_ON_ERR(
      HandleUnsupportedAudioFormats(src, input_config, output_config, dest));

  MaybeInitializeRender(input_config, output_config);

  RETURN_ON_ERR(AnalyzeReverseStreamLocked(src, input_config, output_config));

  if (submodule_states_.RenderMultiBandProcessingActive() ||
      submodule_states_.RenderFullBandProcessingActive()) {
    render_.render_audio->CopyTo(formats_.api_format.reverse_output_stream(),
                                 dest);
  } else if (formats_.api_format.reverse_input_stream() !=
             formats_.api_format.reverse_output_stream()) {
    render_.render_converter->Convert(src, input_config.num_samples(), dest,
                                      output_config.num_samples());
  } else {
    CopyAudioIfNeeded(src, input_config.num_frames(),
                      input_config.num_channels(), dest);
  }

  return kNoError;
}

int AudioProcessingImpl::AnalyzeReverseStreamLocked(
    const float* const* src,
    const StreamConfig& input_config,
    const StreamConfig& output_config) {
  if (aec_dump_) {
    const size_t channel_size =
        formats_.api_format.reverse_input_stream().num_frames();
    const size_t num_channels =
        formats_.api_format.reverse_input_stream().num_channels();
    aec_dump_->WriteRenderStreamMessage(
        AudioFrameView<const float>(src, num_channels, channel_size));
  }
  render_.render_audio->CopyFrom(src,
                                 formats_.api_format.reverse_input_stream());
  return ProcessRenderStreamLocked();
}

int AudioProcessingImpl::ProcessReverseStream(const int16_t* const src,
                                              const StreamConfig& input_config,
                                              const StreamConfig& output_config,
                                              int16_t* const dest) {
  TRACE_EVENT0("webrtc", "AudioProcessing::ProcessReverseStream_AudioFrame");

  MutexLock lock(&mutex_render_);
  DenormalDisabler denormal_disabler;

  RETURN_ON_ERR(
      HandleUnsupportedAudioFormats(src, input_config, output_config, dest));
  MaybeInitializeRender(input_config, output_config);

  if (aec_dump_) {
    aec_dump_->WriteRenderStreamMessage(src, input_config.num_frames(),
                                        input_config.num_channels());
  }

  render_.render_audio->CopyFrom(src, input_config);
  RETURN_ON_ERR(ProcessRenderStreamLocked());
  if (submodule_states_.RenderMultiBandProcessingActive() ||
      submodule_states_.RenderFullBandProcessingActive()) {
    render_.render_audio->CopyTo(output_config, dest);
  }
  return kNoError;
}

int AudioProcessingImpl::ProcessRenderStreamLocked() {
  AudioBuffer* render_buffer = render_.render_audio.get();  // For brevity.

  HandleRenderRuntimeSettings();
  DenormalDisabler denormal_disabler;

  if (submodules_.render_pre_processor) {
    submodules_.render_pre_processor->Process(render_buffer);
  }

  QueueNonbandedRenderAudio(render_buffer);

  if (submodule_states_.RenderMultiBandSubModulesActive() &&
      SampleRateSupportsMultiBand(
          formats_.render_processing_format.sample_rate_hz())) {
    render_buffer->SplitIntoFrequencyBands();
  }

  if (submodule_states_.RenderMultiBandSubModulesActive()) {
    QueueBandedRenderAudio(render_buffer);
  }

  // TODO(peah): Perform the queuing inside QueueRenderAudiuo().
  if (submodules_.echo_controller) {
    submodules_.echo_controller->AnalyzeRender(render_buffer);
  }

  if (submodule_states_.RenderMultiBandProcessingActive() &&
      SampleRateSupportsMultiBand(
          formats_.render_processing_format.sample_rate_hz())) {
    render_buffer->MergeFrequencyBands();
  }

  return kNoError;
}

int AudioProcessingImpl::set_stream_delay_ms(int delay) {
  MutexLock lock(&mutex_capture_);
  Error retval = kNoError;
  capture_.was_stream_delay_set = true;

  if (delay < 0) {
    delay = 0;
    retval = kBadStreamParameterWarning;
  }

  // TODO(ajm): the max is rather arbitrarily chosen; investigate.
  if (delay > 500) {
    delay = 500;
    retval = kBadStreamParameterWarning;
  }

  capture_nonlocked_.stream_delay_ms = delay;
  return retval;
}

bool AudioProcessingImpl::GetLinearAecOutput(
    rtc::ArrayView<std::array<float, 160>> linear_output) const {
  MutexLock lock(&mutex_capture_);
  AudioBuffer* linear_aec_buffer = capture_.linear_aec_output.get();

  RTC_DCHECK(linear_aec_buffer);
  if (linear_aec_buffer) {
    RTC_DCHECK_EQ(1, linear_aec_buffer->num_bands());
    RTC_DCHECK_EQ(linear_output.size(), linear_aec_buffer->num_channels());

    for (size_t ch = 0; ch < linear_aec_buffer->num_channels(); ++ch) {
      RTC_DCHECK_EQ(linear_output[ch].size(), linear_aec_buffer->num_frames());
      rtc::ArrayView<const float> channel_view =
          rtc::ArrayView<const float>(linear_aec_buffer->channels_const()[ch],
                                      linear_aec_buffer->num_frames());
      FloatS16ToFloat(channel_view.data(), channel_view.size(),
                      linear_output[ch].data());
    }
    return true;
  }
  RTC_LOG(LS_ERROR) << "No linear AEC output available";
  RTC_DCHECK_NOTREACHED();
  return false;
}

int AudioProcessingImpl::stream_delay_ms() const {
  // Used as callback from submodules, hence locking is not allowed.
  return capture_nonlocked_.stream_delay_ms;
}

void AudioProcessingImpl::set_stream_key_pressed(bool key_pressed) {
  MutexLock lock(&mutex_capture_);
  capture_.key_pressed = key_pressed;
}

void AudioProcessingImpl::set_stream_analog_level(int level) {
  MutexLock lock_capture(&mutex_capture_);
  set_stream_analog_level_locked(level);
}

void AudioProcessingImpl::set_stream_analog_level_locked(int level) {
  capture_.applied_input_volume_changed =
      capture_.applied_input_volume.has_value() &&
      *capture_.applied_input_volume != level;
  capture_.applied_input_volume = level;

  // Invalidate any previously recommended input volume which will be updated by
  // `ProcessStream()`.
  capture_.recommended_input_volume = absl::nullopt;

  if (submodules_.agc_manager) {
    submodules_.agc_manager->set_stream_analog_level(level);
    return;
  }

  if (submodules_.gain_control) {
    int error = submodules_.gain_control->set_stream_analog_level(level);
    RTC_DCHECK_EQ(kNoError, error);
    return;
  }
}

int AudioProcessingImpl::recommended_stream_analog_level() const {
  MutexLock lock_capture(&mutex_capture_);
  if (!capture_.applied_input_volume.has_value()) {
    RTC_LOG(LS_ERROR) << "set_stream_analog_level has not been called";
  }
  // Input volume to recommend when `set_stream_analog_level()` is not called.
  constexpr int kFallBackInputVolume = 255;
  // When APM has no input volume to recommend, return the latest applied input
  // volume that has been observed in order to possibly produce no input volume
  // change. If no applied input volume has been observed, return a fall-back
  // value.
  return capture_.recommended_input_volume.value_or(
      capture_.applied_input_volume.value_or(kFallBackInputVolume));
}

void AudioProcessingImpl::UpdateRecommendedInputVolumeLocked() {
  if (!capture_.applied_input_volume.has_value()) {
    // When `set_stream_analog_level()` is not called, no input level can be
    // recommended.
    capture_.recommended_input_volume = absl::nullopt;
    return;
  }

  if (submodules_.agc_manager) {
    capture_.recommended_input_volume =
        submodules_.agc_manager->recommended_analog_level();
    return;
  }

  if (submodules_.gain_control) {
    capture_.recommended_input_volume =
        submodules_.gain_control->stream_analog_level();
    return;
  }

  if (submodules_.gain_controller2 &&
      config_.gain_controller2.input_volume_controller.enabled) {
    capture_.recommended_input_volume =
        submodules_.gain_controller2->recommended_input_volume();
    return;
  }

  capture_.recommended_input_volume = capture_.applied_input_volume;
}

bool AudioProcessingImpl::CreateAndAttachAecDump(
    absl::string_view file_name,
    int64_t max_log_size_bytes,
    absl::Nonnull<TaskQueueBase*> worker_queue) {
  std::unique_ptr<AecDump> aec_dump =
      AecDumpFactory::Create(file_name, max_log_size_bytes, worker_queue);
  if (!aec_dump) {
    return false;
  }

  AttachAecDump(std::move(aec_dump));
  return true;
}

bool AudioProcessingImpl::CreateAndAttachAecDump(
    FILE* handle,
    int64_t max_log_size_bytes,
    absl::Nonnull<TaskQueueBase*> worker_queue) {
  std::unique_ptr<AecDump> aec_dump =
      AecDumpFactory::Create(handle, max_log_size_bytes, worker_queue);
  if (!aec_dump) {
    return false;
  }

  AttachAecDump(std::move(aec_dump));
  return true;
}

void AudioProcessingImpl::AttachAecDump(std::unique_ptr<AecDump> aec_dump) {
  RTC_DCHECK(aec_dump);
  MutexLock lock_render(&mutex_render_);
  MutexLock lock_capture(&mutex_capture_);

  // The previously attached AecDump will be destroyed with the
  // 'aec_dump' parameter, which is after locks are released.
  aec_dump_.swap(aec_dump);
  WriteAecDumpConfigMessage(true);
  aec_dump_->WriteInitMessage(formats_.api_format, rtc::TimeUTCMillis());
}

void AudioProcessingImpl::DetachAecDump() {
  // The d-tor of a task-queue based AecDump blocks until all pending
  // tasks are done. This construction avoids blocking while holding
  // the render and capture locks.
  std::unique_ptr<AecDump> aec_dump = nullptr;
  {
    MutexLock lock_render(&mutex_render_);
    MutexLock lock_capture(&mutex_capture_);
    aec_dump = std::move(aec_dump_);
  }
}

AudioProcessing::Config AudioProcessingImpl::GetConfig() const {
  MutexLock lock_render(&mutex_render_);
  MutexLock lock_capture(&mutex_capture_);
  return config_;
}

bool AudioProcessingImpl::UpdateActiveSubmoduleStates() {
  return submodule_states_.Update(
      config_.high_pass_filter.enabled, !!submodules_.echo_control_mobile,
      !!submodules_.noise_suppressor, !!submodules_.gain_control,
      !!submodules_.gain_controller2, !!submodules_.voice_activity_detector,
      config_.pre_amplifier.enabled || config_.capture_level_adjustment.enabled,
      capture_nonlocked_.echo_controller_enabled,
      !!submodules_.transient_suppressor);
}

void AudioProcessingImpl::InitializeTransientSuppressor() {
  // Choose the VAD mode for TS and detect a VAD mode change.
  const TransientSuppressor::VadMode previous_vad_mode =
      transient_suppressor_vad_mode_;
  transient_suppressor_vad_mode_ = TransientSuppressor::VadMode::kDefault;
  if (UseApmVadSubModule(config_, gain_controller2_experiment_params_)) {
    transient_suppressor_vad_mode_ = TransientSuppressor::VadMode::kRnnVad;
  }
  const bool vad_mode_changed =
      previous_vad_mode != transient_suppressor_vad_mode_;

  if (config_.transient_suppression.enabled &&
      !constants_.transient_suppressor_forced_off) {
    // Attempt to create a transient suppressor, if one is not already created.
    if (!submodules_.transient_suppressor || vad_mode_changed) {
      submodules_.transient_suppressor = CreateTransientSuppressor(
          submodule_creation_overrides_, transient_suppressor_vad_mode_,
          proc_fullband_sample_rate_hz(), capture_nonlocked_.split_rate,
          num_proc_channels());
      if (!submodules_.transient_suppressor) {
        RTC_LOG(LS_WARNING)
            << "No transient suppressor created (probably disabled)";
      }
    } else {
      submodules_.transient_suppressor->Initialize(
          proc_fullband_sample_rate_hz(), capture_nonlocked_.split_rate,
          num_proc_channels());
    }
  } else {
    submodules_.transient_suppressor.reset();
  }
}

void AudioProcessingImpl::InitializeHighPassFilter(bool forced_reset) {
  bool high_pass_filter_needed_by_aec =
      config_.echo_canceller.enabled &&
      config_.echo_canceller.enforce_high_pass_filtering &&
      !config_.echo_canceller.mobile_mode;
  if (submodule_states_.HighPassFilteringRequired() ||
      high_pass_filter_needed_by_aec) {
    bool use_full_band = config_.high_pass_filter.apply_in_full_band &&
                         !constants_.enforce_split_band_hpf;
    int rate = use_full_band ? proc_fullband_sample_rate_hz()
                             : proc_split_sample_rate_hz();
    size_t num_channels =
        use_full_band ? num_output_channels() : num_proc_channels();

    if (!submodules_.high_pass_filter ||
        rate != submodules_.high_pass_filter->sample_rate_hz() ||
        forced_reset ||
        num_channels != submodules_.high_pass_filter->num_channels()) {
      submodules_.high_pass_filter.reset(
          new HighPassFilter(rate, num_channels));
    }
  } else {
    submodules_.high_pass_filter.reset();
  }
}

void AudioProcessingImpl::InitializeEchoController() {
  bool use_echo_controller =
      echo_control_factory_ ||
      (config_.echo_canceller.enabled && !config_.echo_canceller.mobile_mode);

  if (use_echo_controller) {
    // Create and activate the echo controller.
    if (echo_control_factory_) {
      submodules_.echo_controller = echo_control_factory_->Create(
          proc_sample_rate_hz(), num_reverse_channels(), num_proc_channels());
      RTC_DCHECK(submodules_.echo_controller);
    } else {
      EchoCanceller3Config config;
      absl::optional<EchoCanceller3Config> multichannel_config;
      if (use_setup_specific_default_aec3_config_) {
        multichannel_config = EchoCanceller3::CreateDefaultMultichannelConfig();
      }
      submodules_.echo_controller = std::make_unique<EchoCanceller3>(
          config, multichannel_config, proc_sample_rate_hz(),
          num_reverse_channels(), num_proc_channels());
    }

    // Setup the storage for returning the linear AEC output.
    if (config_.echo_canceller.export_linear_aec_output) {
      constexpr int kLinearOutputRateHz = 16000;
      capture_.linear_aec_output = std::make_unique<AudioBuffer>(
          kLinearOutputRateHz, num_proc_channels(), kLinearOutputRateHz,
          num_proc_channels(), kLinearOutputRateHz, num_proc_channels());
    } else {
      capture_.linear_aec_output.reset();
    }

    capture_nonlocked_.echo_controller_enabled = true;

    submodules_.echo_control_mobile.reset();
    aecm_render_signal_queue_.reset();
    return;
  }

  submodules_.echo_controller.reset();
  capture_nonlocked_.echo_controller_enabled = false;
  capture_.linear_aec_output.reset();

  if (!config_.echo_canceller.enabled) {
    submodules_.echo_control_mobile.reset();
    aecm_render_signal_queue_.reset();
    return;
  }

  if (config_.echo_canceller.mobile_mode) {
    // Create and activate AECM.
    size_t max_element_size =
        std::max(static_cast<size_t>(1),
                 kMaxAllowedValuesOfSamplesPerBand *
                     EchoControlMobileImpl::NumCancellersRequired(
                         num_output_channels(), num_reverse_channels()));

    std::vector<int16_t> template_queue_element(max_element_size);

    aecm_render_signal_queue_.reset(
        new SwapQueue<std::vector<int16_t>, RenderQueueItemVerifier<int16_t>>(
            kMaxNumFramesToBuffer, template_queue_element,
            RenderQueueItemVerifier<int16_t>(max_element_size)));

    aecm_render_queue_buffer_.resize(max_element_size);
    aecm_capture_queue_buffer_.resize(max_element_size);

    submodules_.echo_control_mobile.reset(new EchoControlMobileImpl());

    submodules_.echo_control_mobile->Initialize(proc_split_sample_rate_hz(),
                                                num_reverse_channels(),
                                                num_output_channels());
    return;
  }

  submodules_.echo_control_mobile.reset();
  aecm_render_signal_queue_.reset();
}

void AudioProcessingImpl::InitializeGainController1() {
  if (config_.gain_controller2.enabled &&
      config_.gain_controller2.input_volume_controller.enabled &&
      config_.gain_controller1.enabled &&
      (config_.gain_controller1.mode ==
           AudioProcessing::Config::GainController1::kAdaptiveAnalog ||
       config_.gain_controller1.analog_gain_controller.enabled)) {
    RTC_LOG(LS_ERROR) << "APM configuration not valid: "
                      << "Multiple input volume controllers enabled.";
  }

  if (!config_.gain_controller1.enabled) {
    submodules_.agc_manager.reset();
    submodules_.gain_control.reset();
    return;
  }

  RTC_HISTOGRAM_BOOLEAN(
      "WebRTC.Audio.GainController.Analog.Enabled",
      config_.gain_controller1.analog_gain_controller.enabled);

  if (!submodules_.gain_control) {
    submodules_.gain_control.reset(new GainControlImpl());
  }

  submodules_.gain_control->Initialize(num_proc_channels(),
                                       proc_sample_rate_hz());
  if (!config_.gain_controller1.analog_gain_controller.enabled) {
    int error = submodules_.gain_control->set_mode(
        Agc1ConfigModeToInterfaceMode(config_.gain_controller1.mode));
    RTC_DCHECK_EQ(kNoError, error);
    error = submodules_.gain_control->set_target_level_dbfs(
        config_.gain_controller1.target_level_dbfs);
    RTC_DCHECK_EQ(kNoError, error);
    error = submodules_.gain_control->set_compression_gain_db(
        config_.gain_controller1.compression_gain_db);
    RTC_DCHECK_EQ(kNoError, error);
    error = submodules_.gain_control->enable_limiter(
        config_.gain_controller1.enable_limiter);
    RTC_DCHECK_EQ(kNoError, error);
    constexpr int kAnalogLevelMinimum = 0;
    constexpr int kAnalogLevelMaximum = 255;
    error = submodules_.gain_control->set_analog_level_limits(
        kAnalogLevelMinimum, kAnalogLevelMaximum);
    RTC_DCHECK_EQ(kNoError, error);

    submodules_.agc_manager.reset();
    return;
  }

  if (!submodules_.agc_manager.get() ||
      submodules_.agc_manager->num_channels() !=
          static_cast<int>(num_proc_channels())) {
    int stream_analog_level = -1;
    const bool re_creation = !!submodules_.agc_manager;
    if (re_creation) {
      stream_analog_level = submodules_.agc_manager->recommended_analog_level();
    }
    submodules_.agc_manager.reset(new AgcManagerDirect(
        num_proc_channels(), config_.gain_controller1.analog_gain_controller));
    if (re_creation) {
      submodules_.agc_manager->set_stream_analog_level(stream_analog_level);
    }
  }
  submodules_.agc_manager->Initialize();
  submodules_.agc_manager->SetupDigitalGainControl(*submodules_.gain_control);
  submodules_.agc_manager->HandleCaptureOutputUsedChange(
      capture_.capture_output_used);
}

void AudioProcessingImpl::InitializeGainController2() {
  if (!config_.gain_controller2.enabled) {
    submodules_.gain_controller2.reset();
    return;
  }
  // Override the input volume controller configuration if the AGC2 experiment
  // is running and its parameters require to fully switch the gain control to
  // AGC2.
  const bool input_volume_controller_config_overridden =
      gain_controller2_experiment_params_.has_value() &&
      gain_controller2_experiment_params_->agc2_config.has_value();
  const InputVolumeController::Config input_volume_controller_config =
      input_volume_controller_config_overridden
          ? gain_controller2_experiment_params_->agc2_config
                ->input_volume_controller
          : InputVolumeController::Config{};
  // If the APM VAD sub-module is not used, let AGC2 use its internal VAD.
  const bool use_internal_vad =
      !UseApmVadSubModule(config_, gain_controller2_experiment_params_);
  submodules_.gain_controller2 = std::make_unique<GainController2>(
      config_.gain_controller2, input_volume_controller_config,
      proc_fullband_sample_rate_hz(), num_output_channels(), use_internal_vad);
  submodules_.gain_controller2->SetCaptureOutputUsed(
      capture_.capture_output_used);
}

void AudioProcessingImpl::InitializeVoiceActivityDetector() {
  if (!UseApmVadSubModule(config_, gain_controller2_experiment_params_)) {
    submodules_.voice_activity_detector.reset();
    return;
  }

  if (!submodules_.voice_activity_detector) {
    RTC_DCHECK(!!submodules_.gain_controller2);
    // TODO(bugs.webrtc.org/13663): Cache CPU features in APM and use here.
    submodules_.voice_activity_detector =
        std::make_unique<VoiceActivityDetectorWrapper>(
            submodules_.gain_controller2->GetCpuFeatures(),
            proc_fullband_sample_rate_hz());
  } else {
    submodules_.voice_activity_detector->Initialize(
        proc_fullband_sample_rate_hz());
  }
}

void AudioProcessingImpl::InitializeNoiseSuppressor() {
  submodules_.noise_suppressor.reset();

  if (config_.noise_suppression.enabled) {
    auto map_level =
        [](AudioProcessing::Config::NoiseSuppression::Level level) {
          using NoiseSuppresionConfig =
              AudioProcessing::Config::NoiseSuppression;
          switch (level) {
            case NoiseSuppresionConfig::kLow:
              return NsConfig::SuppressionLevel::k6dB;
            case NoiseSuppresionConfig::kModerate:
              return NsConfig::SuppressionLevel::k12dB;
            case NoiseSuppresionConfig::kHigh:
              return NsConfig::SuppressionLevel::k18dB;
            case NoiseSuppresionConfig::kVeryHigh:
              return NsConfig::SuppressionLevel::k21dB;
          }
          RTC_CHECK_NOTREACHED();
        };

    NsConfig cfg;
    cfg.target_level = map_level(config_.noise_suppression.level);
    submodules_.noise_suppressor = std::make_unique<NoiseSuppressor>(
        cfg, proc_sample_rate_hz(), num_proc_channels());
  }
}

void AudioProcessingImpl::InitializeCaptureLevelsAdjuster() {
  if (config_.pre_amplifier.enabled ||
      config_.capture_level_adjustment.enabled) {
    // Use both the pre-amplifier and the capture level adjustment gains as
    // pre-gains.
    float pre_gain = 1.f;
    if (config_.pre_amplifier.enabled) {
      pre_gain *= config_.pre_amplifier.fixed_gain_factor;
    }
    if (config_.capture_level_adjustment.enabled) {
      pre_gain *= config_.capture_level_adjustment.pre_gain_factor;
    }

    submodules_.capture_levels_adjuster =
        std::make_unique<CaptureLevelsAdjuster>(
            config_.capture_level_adjustment.analog_mic_gain_emulation.enabled,
            config_.capture_level_adjustment.analog_mic_gain_emulation
                .initial_level,
            pre_gain, config_.capture_level_adjustment.post_gain_factor);
  } else {
    submodules_.capture_levels_adjuster.reset();
  }
}

void AudioProcessingImpl::InitializeResidualEchoDetector() {
  if (submodules_.echo_detector) {
    submodules_.echo_detector->Initialize(
        proc_fullband_sample_rate_hz(), 1,
        formats_.render_processing_format.sample_rate_hz(), 1);
  }
}

void AudioProcessingImpl::InitializeAnalyzer() {
  if (submodules_.capture_analyzer) {
    submodules_.capture_analyzer->Initialize(proc_fullband_sample_rate_hz(),
                                             num_proc_channels());
  }
}

void AudioProcessingImpl::InitializePostProcessor() {
  if (submodules_.capture_post_processor) {
    submodules_.capture_post_processor->Initialize(
        proc_fullband_sample_rate_hz(), num_proc_channels());
  }
}

void AudioProcessingImpl::InitializePreProcessor() {
  if (submodules_.render_pre_processor) {
    submodules_.render_pre_processor->Initialize(
        formats_.render_processing_format.sample_rate_hz(),
        formats_.render_processing_format.num_channels());
  }
}

void AudioProcessingImpl::WriteAecDumpConfigMessage(bool forced) {
  if (!aec_dump_) {
    return;
  }

  std::string experiments_description = "";
  // TODO(peah): Add semicolon-separated concatenations of experiment
  // descriptions for other submodules.
  if (!!submodules_.capture_post_processor) {
    experiments_description += "CapturePostProcessor;";
  }
  if (!!submodules_.render_pre_processor) {
    experiments_description += "RenderPreProcessor;";
  }
  if (capture_nonlocked_.echo_controller_enabled) {
    experiments_description += "EchoController;";
  }
  if (config_.gain_controller2.enabled) {
    experiments_description += "GainController2;";
  }

  InternalAPMConfig apm_config;

  apm_config.aec_enabled = config_.echo_canceller.enabled;
  apm_config.aec_delay_agnostic_enabled = false;
  apm_config.aec_extended_filter_enabled = false;
  apm_config.aec_suppression_level = 0;

  apm_config.aecm_enabled = !!submodules_.echo_control_mobile;
  apm_config.aecm_comfort_noise_enabled =
      submodules_.echo_control_mobile &&
      submodules_.echo_control_mobile->is_comfort_noise_enabled();
  apm_config.aecm_routing_mode =
      submodules_.echo_control_mobile
          ? static_cast<int>(submodules_.echo_control_mobile->routing_mode())
          : 0;

  apm_config.agc_enabled = !!submodules_.gain_control;

  apm_config.agc_mode = submodules_.gain_control
                            ? static_cast<int>(submodules_.gain_control->mode())
                            : GainControl::kAdaptiveAnalog;
  apm_config.agc_limiter_enabled =
      submodules_.gain_control ? submodules_.gain_control->is_limiter_enabled()
                               : false;
  apm_config.noise_robust_agc_enabled = !!submodules_.agc_manager;

  apm_config.hpf_enabled = config_.high_pass_filter.enabled;

  apm_config.ns_enabled = config_.noise_suppression.enabled;
  apm_config.ns_level = static_cast<int>(config_.noise_suppression.level);

  apm_config.transient_suppression_enabled =
      config_.transient_suppression.enabled;
  apm_config.experiments_description = experiments_description;
  apm_config.pre_amplifier_enabled = config_.pre_amplifier.enabled;
  apm_config.pre_amplifier_fixed_gain_factor =
      config_.pre_amplifier.fixed_gain_factor;

  if (!forced && apm_config == apm_config_for_aec_dump_) {
    return;
  }
  aec_dump_->WriteConfig(apm_config);
  apm_config_for_aec_dump_ = apm_config;
}

void AudioProcessingImpl::RecordUnprocessedCaptureStream(
    const float* const* src) {
  RTC_DCHECK(aec_dump_);
  WriteAecDumpConfigMessage(false);

  const size_t channel_size = formats_.api_format.input_stream().num_frames();
  const size_t num_channels = formats_.api_format.input_stream().num_channels();
  aec_dump_->AddCaptureStreamInput(
      AudioFrameView<const float>(src, num_channels, channel_size));
  RecordAudioProcessingState();
}

void AudioProcessingImpl::RecordUnprocessedCaptureStream(
    const int16_t* const data,
    const StreamConfig& config) {
  RTC_DCHECK(aec_dump_);
  WriteAecDumpConfigMessage(false);

  aec_dump_->AddCaptureStreamInput(data, config.num_channels(),
                                   config.num_frames());
  RecordAudioProcessingState();
}

void AudioProcessingImpl::RecordProcessedCaptureStream(
    const float* const* processed_capture_stream) {
  RTC_DCHECK(aec_dump_);

  const size_t channel_size = formats_.api_format.output_stream().num_frames();
  const size_t num_channels =
      formats_.api_format.output_stream().num_channels();
  aec_dump_->AddCaptureStreamOutput(AudioFrameView<const float>(
      processed_capture_stream, num_channels, channel_size));
  aec_dump_->WriteCaptureStreamMessage();
}

void AudioProcessingImpl::RecordProcessedCaptureStream(
    const int16_t* const data,
    const StreamConfig& config) {
  RTC_DCHECK(aec_dump_);

  aec_dump_->AddCaptureStreamOutput(data, config.num_channels(),
                                    config.num_frames());
  aec_dump_->WriteCaptureStreamMessage();
}

void AudioProcessingImpl::RecordAudioProcessingState() {
  RTC_DCHECK(aec_dump_);
  AecDump::AudioProcessingState audio_proc_state;
  audio_proc_state.delay = capture_nonlocked_.stream_delay_ms;
  audio_proc_state.drift = 0;
  audio_proc_state.applied_input_volume = capture_.applied_input_volume;
  audio_proc_state.keypress = capture_.key_pressed;
  aec_dump_->AddAudioProcessingState(audio_proc_state);
}

AudioProcessingImpl::ApmCaptureState::ApmCaptureState()
    : was_stream_delay_set(false),
      capture_output_used(true),
      capture_output_used_last_frame(true),
      key_pressed(false),
      capture_processing_format(kSampleRate16kHz),
      split_rate(kSampleRate16kHz),
      echo_path_gain_change(false),
      prev_pre_adjustment_gain(-1.0f),
      playout_volume(-1),
      prev_playout_volume(-1),
      applied_input_volume_changed(false) {}

AudioProcessingImpl::ApmCaptureState::~ApmCaptureState() = default;

AudioProcessingImpl::ApmRenderState::ApmRenderState() = default;

AudioProcessingImpl::ApmRenderState::~ApmRenderState() = default;

AudioProcessingImpl::ApmStatsReporter::ApmStatsReporter()
    : stats_message_queue_(1) {}

AudioProcessingImpl::ApmStatsReporter::~ApmStatsReporter() = default;

AudioProcessingStats AudioProcessingImpl::ApmStatsReporter::GetStatistics() {
  MutexLock lock_stats(&mutex_stats_);
  bool new_stats_available = stats_message_queue_.Remove(&cached_stats_);
  // If the message queue is full, return the cached stats.
  static_cast<void>(new_stats_available);

  return cached_stats_;
}

void AudioProcessingImpl::ApmStatsReporter::UpdateStatistics(
    const AudioProcessingStats& new_stats) {
  AudioProcessingStats stats_to_queue = new_stats;
  bool stats_message_passed = stats_message_queue_.Insert(&stats_to_queue);
  // If the message queue is full, discard the new stats.
  static_cast<void>(stats_message_passed);
}

}  // namespace webrtc
