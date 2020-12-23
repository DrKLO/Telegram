/*
 *  Copyright (c) 2016 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "modules/audio_processing/include/audio_processing.h"

#include "rtc_base/strings/string_builder.h"
#include "rtc_base/system/arch.h"

namespace webrtc {
namespace {

using Agc1Config = AudioProcessing::Config::GainController1;
using Agc2Config = AudioProcessing::Config::GainController2;

std::string NoiseSuppressionLevelToString(
    const AudioProcessing::Config::NoiseSuppression::Level& level) {
  switch (level) {
    case AudioProcessing::Config::NoiseSuppression::Level::kLow:
      return "Low";
    case AudioProcessing::Config::NoiseSuppression::Level::kModerate:
      return "Moderate";
    case AudioProcessing::Config::NoiseSuppression::Level::kHigh:
      return "High";
    case AudioProcessing::Config::NoiseSuppression::Level::kVeryHigh:
      return "VeryHigh";
  }
  RTC_CHECK_NOTREACHED();
}

std::string GainController1ModeToString(const Agc1Config::Mode& mode) {
  switch (mode) {
    case Agc1Config::Mode::kAdaptiveAnalog:
      return "AdaptiveAnalog";
    case Agc1Config::Mode::kAdaptiveDigital:
      return "AdaptiveDigital";
    case Agc1Config::Mode::kFixedDigital:
      return "FixedDigital";
  }
  RTC_CHECK_NOTREACHED();
}

std::string GainController2LevelEstimatorToString(
    const Agc2Config::LevelEstimator& level) {
  switch (level) {
    case Agc2Config::LevelEstimator::kRms:
      return "Rms";
    case Agc2Config::LevelEstimator::kPeak:
      return "Peak";
  }
  RTC_CHECK_NOTREACHED();
}

int GetDefaultMaxInternalRate() {
#ifdef WEBRTC_ARCH_ARM_FAMILY
  return 32000;
#else
  return 48000;
#endif
}

}  // namespace

constexpr int AudioProcessing::kNativeSampleRatesHz[];

void CustomProcessing::SetRuntimeSetting(
    AudioProcessing::RuntimeSetting setting) {}

AudioProcessing::Config::Pipeline::Pipeline()
    : maximum_internal_processing_rate(GetDefaultMaxInternalRate()) {}

bool Agc1Config::operator==(const Agc1Config& rhs) const {
  const auto& analog_lhs = analog_gain_controller;
  const auto& analog_rhs = rhs.analog_gain_controller;
  return enabled == rhs.enabled && mode == rhs.mode &&
         target_level_dbfs == rhs.target_level_dbfs &&
         compression_gain_db == rhs.compression_gain_db &&
         enable_limiter == rhs.enable_limiter &&
         analog_level_minimum == rhs.analog_level_minimum &&
         analog_level_maximum == rhs.analog_level_maximum &&
         analog_lhs.enabled == analog_rhs.enabled &&
         analog_lhs.startup_min_volume == analog_rhs.startup_min_volume &&
         analog_lhs.clipped_level_min == analog_rhs.clipped_level_min &&
         analog_lhs.enable_agc2_level_estimator ==
             analog_rhs.enable_agc2_level_estimator &&
         analog_lhs.enable_digital_adaptive ==
             analog_rhs.enable_digital_adaptive;
}

bool Agc2Config::operator==(const Agc2Config& rhs) const {
  const auto& adaptive_lhs = adaptive_digital;
  const auto& adaptive_rhs = rhs.adaptive_digital;

  return enabled == rhs.enabled &&
         fixed_digital.gain_db == rhs.fixed_digital.gain_db &&
         adaptive_lhs.enabled == adaptive_rhs.enabled &&
         adaptive_lhs.vad_probability_attack ==
             adaptive_rhs.vad_probability_attack &&
         adaptive_lhs.level_estimator == adaptive_rhs.level_estimator &&
         adaptive_lhs.level_estimator_adjacent_speech_frames_threshold ==
             adaptive_rhs.level_estimator_adjacent_speech_frames_threshold &&
         adaptive_lhs.use_saturation_protector ==
             adaptive_rhs.use_saturation_protector &&
         adaptive_lhs.initial_saturation_margin_db ==
             adaptive_rhs.initial_saturation_margin_db &&
         adaptive_lhs.extra_saturation_margin_db ==
             adaptive_rhs.extra_saturation_margin_db &&
         adaptive_lhs.gain_applier_adjacent_speech_frames_threshold ==
             adaptive_rhs.gain_applier_adjacent_speech_frames_threshold &&
         adaptive_lhs.max_gain_change_db_per_second ==
             adaptive_rhs.max_gain_change_db_per_second &&
         adaptive_lhs.max_output_noise_level_dbfs ==
             adaptive_rhs.max_output_noise_level_dbfs;
}

std::string AudioProcessing::Config::ToString() const {
  char buf[2048];
  rtc::SimpleStringBuilder builder(buf);
  builder << "AudioProcessing::Config{ "
             "pipeline: {"
             "maximum_internal_processing_rate: "
          << pipeline.maximum_internal_processing_rate
          << ", multi_channel_render: " << pipeline.multi_channel_render
          << ", multi_channel_capture: " << pipeline.multi_channel_capture
          << "}, pre_amplifier: { enabled: " << pre_amplifier.enabled
          << ", fixed_gain_factor: " << pre_amplifier.fixed_gain_factor
          << " }, high_pass_filter: { enabled: " << high_pass_filter.enabled
          << " }, echo_canceller: { enabled: " << echo_canceller.enabled
          << ", mobile_mode: " << echo_canceller.mobile_mode
          << ", enforce_high_pass_filtering: "
          << echo_canceller.enforce_high_pass_filtering
          << " }, noise_suppression: { enabled: " << noise_suppression.enabled
          << ", level: "
          << NoiseSuppressionLevelToString(noise_suppression.level)
          << " }, transient_suppression: { enabled: "
          << transient_suppression.enabled
          << " }, voice_detection: { enabled: " << voice_detection.enabled
          << " }, gain_controller1: { enabled: " << gain_controller1.enabled
          << ", mode: " << GainController1ModeToString(gain_controller1.mode)
          << ", target_level_dbfs: " << gain_controller1.target_level_dbfs
          << ", compression_gain_db: " << gain_controller1.compression_gain_db
          << ", enable_limiter: " << gain_controller1.enable_limiter
          << ", analog_level_minimum: " << gain_controller1.analog_level_minimum
          << ", analog_level_maximum: " << gain_controller1.analog_level_maximum
          << " }, gain_controller2: { enabled: " << gain_controller2.enabled
          << ", fixed_digital: { gain_db: "
          << gain_controller2.fixed_digital.gain_db
          << "}, adaptive_digital: { enabled: "
          << gain_controller2.adaptive_digital.enabled
          << ", level_estimator: { type: "
          << GainController2LevelEstimatorToString(
                 gain_controller2.adaptive_digital.level_estimator)
          << ", adjacent_speech_frames_threshold: "
          << gain_controller2.adaptive_digital
                 .level_estimator_adjacent_speech_frames_threshold
          << ", initial_saturation_margin_db: "
          << gain_controller2.adaptive_digital.initial_saturation_margin_db
          << ", extra_saturation_margin_db: "
          << gain_controller2.adaptive_digital.extra_saturation_margin_db
          << "}, gain_applier: { adjacent_speech_frames_threshold: "
          << gain_controller2.adaptive_digital
                 .gain_applier_adjacent_speech_frames_threshold
          << ", max_gain_change_db_per_second: "
          << gain_controller2.adaptive_digital.max_gain_change_db_per_second
          << ", max_output_noise_level_dbfs: "
          << gain_controller2.adaptive_digital.max_output_noise_level_dbfs
          << " } }, residual_echo_detector: { enabled: "
          << residual_echo_detector.enabled
          << " }, level_estimation: { enabled: " << level_estimation.enabled
          << " }}}";
  return builder.str();
}

}  // namespace webrtc
