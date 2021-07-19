/*
 *  Copyright (c) 2021 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */
#include "modules/audio_processing/capture_levels_adjuster/capture_levels_adjuster.h"

#include "modules/audio_processing/audio_buffer.h"
#include "rtc_base/checks.h"
#include "rtc_base/numerics/safe_minmax.h"

namespace webrtc {

namespace {

constexpr int kMinAnalogMicGainLevel = 0;
constexpr int kMaxAnalogMicGainLevel = 255;

float ComputeLevelBasedGain(int emulated_analog_mic_gain_level) {
  static_assert(
      kMinAnalogMicGainLevel == 0,
      "The minimum gain level must be 0 for the maths below to work.");
  static_assert(kMaxAnalogMicGainLevel > 0,
                "The minimum gain level must be larger than 0 for the maths "
                "below to work.");
  constexpr float kGainToLevelMultiplier = 1.f / kMaxAnalogMicGainLevel;

  RTC_DCHECK_GE(emulated_analog_mic_gain_level, kMinAnalogMicGainLevel);
  RTC_DCHECK_LE(emulated_analog_mic_gain_level, kMaxAnalogMicGainLevel);
  return kGainToLevelMultiplier * emulated_analog_mic_gain_level;
}

float ComputePreGain(float pre_gain,
                     int emulated_analog_mic_gain_level,
                     bool emulated_analog_mic_gain_enabled) {
  return emulated_analog_mic_gain_enabled
             ? pre_gain * ComputeLevelBasedGain(emulated_analog_mic_gain_level)
             : pre_gain;
}

}  // namespace

CaptureLevelsAdjuster::CaptureLevelsAdjuster(
    bool emulated_analog_mic_gain_enabled,
    int emulated_analog_mic_gain_level,
    float pre_gain,
    float post_gain)
    : emulated_analog_mic_gain_enabled_(emulated_analog_mic_gain_enabled),
      emulated_analog_mic_gain_level_(emulated_analog_mic_gain_level),
      pre_gain_(pre_gain),
      pre_adjustment_gain_(ComputePreGain(pre_gain_,
                                          emulated_analog_mic_gain_level_,
                                          emulated_analog_mic_gain_enabled_)),
      pre_scaler_(pre_adjustment_gain_),
      post_scaler_(post_gain) {}

void CaptureLevelsAdjuster::ApplyPreLevelAdjustment(AudioBuffer& audio_buffer) {
  pre_scaler_.Process(audio_buffer);
}

void CaptureLevelsAdjuster::ApplyPostLevelAdjustment(
    AudioBuffer& audio_buffer) {
  post_scaler_.Process(audio_buffer);
}

void CaptureLevelsAdjuster::SetPreGain(float pre_gain) {
  pre_gain_ = pre_gain;
  UpdatePreAdjustmentGain();
}

void CaptureLevelsAdjuster::SetPostGain(float post_gain) {
  post_scaler_.SetGain(post_gain);
}

void CaptureLevelsAdjuster::SetAnalogMicGainLevel(int level) {
  RTC_DCHECK_GE(level, kMinAnalogMicGainLevel);
  RTC_DCHECK_LE(level, kMaxAnalogMicGainLevel);
  int clamped_level =
      rtc::SafeClamp(level, kMinAnalogMicGainLevel, kMaxAnalogMicGainLevel);

  emulated_analog_mic_gain_level_ = clamped_level;
  UpdatePreAdjustmentGain();
}

void CaptureLevelsAdjuster::UpdatePreAdjustmentGain() {
  pre_adjustment_gain_ =
      ComputePreGain(pre_gain_, emulated_analog_mic_gain_level_,
                     emulated_analog_mic_gain_enabled_);
  pre_scaler_.SetGain(pre_adjustment_gain_);
}

}  // namespace webrtc
