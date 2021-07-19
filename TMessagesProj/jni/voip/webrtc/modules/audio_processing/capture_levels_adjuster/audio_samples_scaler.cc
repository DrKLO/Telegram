/*
 *  Copyright (c) 2021 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */
#include "modules/audio_processing/capture_levels_adjuster/audio_samples_scaler.h"

#include <algorithm>

#include "api/array_view.h"
#include "modules/audio_processing/audio_buffer.h"
#include "rtc_base/checks.h"
#include "rtc_base/numerics/safe_minmax.h"

namespace webrtc {

AudioSamplesScaler::AudioSamplesScaler(float initial_gain)
    : previous_gain_(initial_gain), target_gain_(initial_gain) {}

void AudioSamplesScaler::Process(AudioBuffer& audio_buffer) {
  if (static_cast<int>(audio_buffer.num_frames()) != samples_per_channel_) {
    // Update the members depending on audio-buffer length if needed.
    RTC_DCHECK_GT(audio_buffer.num_frames(), 0);
    samples_per_channel_ = static_cast<int>(audio_buffer.num_frames());
    one_by_samples_per_channel_ = 1.f / samples_per_channel_;
  }

  if (target_gain_ == 1.f && previous_gain_ == target_gain_) {
    // If only a gain of 1 is to be applied, do an early return without applying
    // any gain.
    return;
  }

  float gain = previous_gain_;
  if (previous_gain_ == target_gain_) {
    // Apply a non-changing gain.
    for (size_t channel = 0; channel < audio_buffer.num_channels(); ++channel) {
      rtc::ArrayView<float> channel_view(audio_buffer.channels()[channel],
                                         samples_per_channel_);
      for (float& sample : channel_view) {
        sample *= gain;
      }
    }
  } else {
    const float increment =
        (target_gain_ - previous_gain_) * one_by_samples_per_channel_;

    if (increment > 0.f) {
      // Apply an increasing gain.
      for (size_t channel = 0; channel < audio_buffer.num_channels();
           ++channel) {
        gain = previous_gain_;
        rtc::ArrayView<float> channel_view(audio_buffer.channels()[channel],
                                           samples_per_channel_);
        for (float& sample : channel_view) {
          gain = std::min(gain + increment, target_gain_);
          sample *= gain;
        }
      }
    } else {
      // Apply a decreasing gain.
      for (size_t channel = 0; channel < audio_buffer.num_channels();
           ++channel) {
        gain = previous_gain_;
        rtc::ArrayView<float> channel_view(audio_buffer.channels()[channel],
                                           samples_per_channel_);
        for (float& sample : channel_view) {
          gain = std::max(gain + increment, target_gain_);
          sample *= gain;
        }
      }
    }
  }
  previous_gain_ = target_gain_;

  // Saturate the samples to be in the S16 range.
  for (size_t channel = 0; channel < audio_buffer.num_channels(); ++channel) {
    rtc::ArrayView<float> channel_view(audio_buffer.channels()[channel],
                                       samples_per_channel_);
    for (float& sample : channel_view) {
      constexpr float kMinFloatS16Value = -32768.f;
      constexpr float kMaxFloatS16Value = 32767.f;
      sample = rtc::SafeClamp(sample, kMinFloatS16Value, kMaxFloatS16Value);
    }
  }
}

}  // namespace webrtc
