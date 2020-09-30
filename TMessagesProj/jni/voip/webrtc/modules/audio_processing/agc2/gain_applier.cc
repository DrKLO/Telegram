/*
 *  Copyright (c) 2018 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "modules/audio_processing/agc2/gain_applier.h"

#include "api/array_view.h"
#include "modules/audio_processing/agc2/agc2_common.h"
#include "rtc_base/numerics/safe_minmax.h"

namespace webrtc {
namespace {

// Returns true when the gain factor is so close to 1 that it would
// not affect int16 samples.
bool GainCloseToOne(float gain_factor) {
  return 1.f - 1.f / kMaxFloatS16Value <= gain_factor &&
         gain_factor <= 1.f + 1.f / kMaxFloatS16Value;
}

void ClipSignal(AudioFrameView<float> signal) {
  for (size_t k = 0; k < signal.num_channels(); ++k) {
    rtc::ArrayView<float> channel_view = signal.channel(k);
    for (auto& sample : channel_view) {
      sample = rtc::SafeClamp(sample, kMinFloatS16Value, kMaxFloatS16Value);
    }
  }
}

void ApplyGainWithRamping(float last_gain_linear,
                          float gain_at_end_of_frame_linear,
                          float inverse_samples_per_channel,
                          AudioFrameView<float> float_frame) {
  // Do not modify the signal.
  if (last_gain_linear == gain_at_end_of_frame_linear &&
      GainCloseToOne(gain_at_end_of_frame_linear)) {
    return;
  }

  // Gain is constant and different from 1.
  if (last_gain_linear == gain_at_end_of_frame_linear) {
    for (size_t k = 0; k < float_frame.num_channels(); ++k) {
      rtc::ArrayView<float> channel_view = float_frame.channel(k);
      for (auto& sample : channel_view) {
        sample *= gain_at_end_of_frame_linear;
      }
    }
    return;
  }

  // The gain changes. We have to change slowly to avoid discontinuities.
  const float increment = (gain_at_end_of_frame_linear - last_gain_linear) *
                          inverse_samples_per_channel;
  float gain = last_gain_linear;
  for (size_t i = 0; i < float_frame.samples_per_channel(); ++i) {
    for (size_t ch = 0; ch < float_frame.num_channels(); ++ch) {
      float_frame.channel(ch)[i] *= gain;
    }
    gain += increment;
  }
}

}  // namespace

GainApplier::GainApplier(bool hard_clip_samples, float initial_gain_factor)
    : hard_clip_samples_(hard_clip_samples),
      last_gain_factor_(initial_gain_factor),
      current_gain_factor_(initial_gain_factor) {}

void GainApplier::ApplyGain(AudioFrameView<float> signal) {
  if (static_cast<int>(signal.samples_per_channel()) != samples_per_channel_) {
    Initialize(signal.samples_per_channel());
  }

  ApplyGainWithRamping(last_gain_factor_, current_gain_factor_,
                       inverse_samples_per_channel_, signal);

  last_gain_factor_ = current_gain_factor_;

  if (hard_clip_samples_) {
    ClipSignal(signal);
  }
}

void GainApplier::SetGainFactor(float gain_factor) {
  RTC_DCHECK_GT(gain_factor, 0.f);
  current_gain_factor_ = gain_factor;
}

void GainApplier::Initialize(size_t samples_per_channel) {
  RTC_DCHECK_GT(samples_per_channel, 0);
  samples_per_channel_ = static_cast<int>(samples_per_channel);
  inverse_samples_per_channel_ = 1.f / samples_per_channel_;
}

}  // namespace webrtc
