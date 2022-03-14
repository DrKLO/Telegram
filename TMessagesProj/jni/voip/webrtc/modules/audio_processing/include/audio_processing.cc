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

}  // namespace

constexpr int AudioProcessing::kNativeSampleRatesHz[];

void CustomProcessing::SetRuntimeSetting(
    AudioProcessing::RuntimeSetting setting) {}

bool Agc1Config::operator==(const Agc1Config& rhs) const {
  const auto& analog_lhs = analog_gain_controller;
  const auto& analog_rhs = rhs.analog_gain_controller;
  return enabled == rhs.enabled && mode == rhs.mode &&
         target_level_dbfs == rhs.target_level_dbfs &&
         compression_gain_db == rhs.compression_gain_db &&
         enable_limiter == rhs.enable_limiter &&
         analog_lhs.enabled == analog_rhs.enabled &&
         analog_lhs.startup_min_volume == analog_rhs.startup_min_volume &&
         analog_lhs.clipped_level_min == analog_rhs.clipped_level_min &&
         analog_lhs.enable_digital_adaptive ==
             analog_rhs.enable_digital_adaptive &&
         analog_lhs.clipped_level_step == analog_rhs.clipped_level_step &&
         analog_lhs.clipped_ratio_threshold ==
             analog_rhs.clipped_ratio_threshold &&
         analog_lhs.clipped_wait_frames == analog_rhs.clipped_wait_frames &&
         analog_lhs.clipping_predictor.mode ==
             analog_rhs.clipping_predictor.mode &&
         analog_lhs.clipping_predictor.window_length ==
             analog_rhs.clipping_predictor.window_length &&
         analog_lhs.clipping_predictor.reference_window_length ==
             analog_rhs.clipping_predictor.reference_window_length &&
         analog_lhs.clipping_predictor.reference_window_delay ==
             analog_rhs.clipping_predictor.reference_window_delay &&
         analog_lhs.clipping_predictor.clipping_threshold ==
             analog_rhs.clipping_predictor.clipping_threshold &&
         analog_lhs.clipping_predictor.crest_factor_margin ==
             analog_rhs.clipping_predictor.crest_factor_margin &&
         analog_lhs.clipping_predictor.use_predicted_step ==
             analog_rhs.clipping_predictor.use_predicted_step;
}

bool Agc2Config::AdaptiveDigital::operator==(
    const Agc2Config::AdaptiveDigital& rhs) const {
  return enabled == rhs.enabled && dry_run == rhs.dry_run &&
         headroom_db == rhs.headroom_db && max_gain_db == rhs.max_gain_db &&
         initial_gain_db == rhs.initial_gain_db &&
         vad_reset_period_ms == rhs.vad_reset_period_ms &&
         adjacent_speech_frames_threshold ==
             rhs.adjacent_speech_frames_threshold &&
         max_gain_change_db_per_second == rhs.max_gain_change_db_per_second &&
         max_output_noise_level_dbfs == rhs.max_output_noise_level_dbfs;
}

bool Agc2Config::operator==(const Agc2Config& rhs) const {
  return enabled == rhs.enabled &&
         fixed_digital.gain_db == rhs.fixed_digital.gain_db &&
         adaptive_digital == rhs.adaptive_digital;
}

bool AudioProcessing::Config::CaptureLevelAdjustment::operator==(
    const AudioProcessing::Config::CaptureLevelAdjustment& rhs) const {
  return enabled == rhs.enabled && pre_gain_factor == rhs.pre_gain_factor &&
         post_gain_factor == rhs.post_gain_factor &&
         analog_mic_gain_emulation == rhs.analog_mic_gain_emulation;
}

bool AudioProcessing::Config::CaptureLevelAdjustment::AnalogMicGainEmulation::
operator==(const AudioProcessing::Config::CaptureLevelAdjustment::
               AnalogMicGainEmulation& rhs) const {
  return enabled == rhs.enabled && initial_level == rhs.initial_level;
}

std::string AudioProcessing::Config::ToString() const {
  char buf[2048];
  rtc::SimpleStringBuilder builder(buf);
  builder << "AudioProcessing::Config{ "
             "pipeline: { "
             "maximum_internal_processing_rate: "
          << pipeline.maximum_internal_processing_rate
          << ", multi_channel_render: " << pipeline.multi_channel_render
          << ", multi_channel_capture: " << pipeline.multi_channel_capture
          << " }, pre_amplifier: { enabled: " << pre_amplifier.enabled
          << ", fixed_gain_factor: " << pre_amplifier.fixed_gain_factor
          << " },capture_level_adjustment: { enabled: "
          << capture_level_adjustment.enabled
          << ", pre_gain_factor: " << capture_level_adjustment.pre_gain_factor
          << ", post_gain_factor: " << capture_level_adjustment.post_gain_factor
          << ", analog_mic_gain_emulation: { enabled: "
          << capture_level_adjustment.analog_mic_gain_emulation.enabled
          << ", initial_level: "
          << capture_level_adjustment.analog_mic_gain_emulation.initial_level
          << " }}, high_pass_filter: { enabled: " << high_pass_filter.enabled
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
          << ", analog_gain_controller { enabled: "
          << gain_controller1.analog_gain_controller.enabled
          << ", startup_min_volume: "
          << gain_controller1.analog_gain_controller.startup_min_volume
          << ", clipped_level_min: "
          << gain_controller1.analog_gain_controller.clipped_level_min
          << ", enable_digital_adaptive: "
          << gain_controller1.analog_gain_controller.enable_digital_adaptive
          << ", clipped_level_step: "
          << gain_controller1.analog_gain_controller.clipped_level_step
          << ", clipped_ratio_threshold: "
          << gain_controller1.analog_gain_controller.clipped_ratio_threshold
          << ", clipped_wait_frames: "
          << gain_controller1.analog_gain_controller.clipped_wait_frames
          << ", clipping_predictor:  { enabled: "
          << gain_controller1.analog_gain_controller.clipping_predictor.enabled
          << ", mode: "
          << gain_controller1.analog_gain_controller.clipping_predictor.mode
          << ", window_length: "
          << gain_controller1.analog_gain_controller.clipping_predictor
                 .window_length
          << ", reference_window_length: "
          << gain_controller1.analog_gain_controller.clipping_predictor
                 .reference_window_length
          << ", reference_window_delay: "
          << gain_controller1.analog_gain_controller.clipping_predictor
                 .reference_window_delay
          << ", clipping_threshold: "
          << gain_controller1.analog_gain_controller.clipping_predictor
                 .clipping_threshold
          << ", crest_factor_margin: "
          << gain_controller1.analog_gain_controller.clipping_predictor
                 .crest_factor_margin
          << ", use_predicted_step: "
          << gain_controller1.analog_gain_controller.clipping_predictor
                 .use_predicted_step
          << " }}}, gain_controller2: { enabled: " << gain_controller2.enabled
          << ", fixed_digital: { gain_db: "
          << gain_controller2.fixed_digital.gain_db
          << " }, adaptive_digital: { enabled: "
          << gain_controller2.adaptive_digital.enabled
          << ", dry_run: " << gain_controller2.adaptive_digital.dry_run
          << ", headroom_db: " << gain_controller2.adaptive_digital.headroom_db
          << ", max_gain_db: " << gain_controller2.adaptive_digital.max_gain_db
          << ", initial_gain_db: "
          << gain_controller2.adaptive_digital.initial_gain_db
          << ", vad_reset_period_ms: "
          << gain_controller2.adaptive_digital.vad_reset_period_ms
          << ", adjacent_speech_frames_threshold: "
          << gain_controller2.adaptive_digital.adjacent_speech_frames_threshold
          << ", max_gain_change_db_per_second: "
          << gain_controller2.adaptive_digital.max_gain_change_db_per_second
          << ", max_output_noise_level_dbfs: "
          << gain_controller2.adaptive_digital.max_output_noise_level_dbfs
          << "}}, residual_echo_detector: { enabled: "
          << residual_echo_detector.enabled << " }}";
  return builder.str();
}

}  // namespace webrtc
