/*
 *  Copyright (c) 2017 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "modules/audio_mixer/sine_wave_generator.h"

#include <math.h>
#include <stddef.h>

#include "rtc_base/numerics/safe_conversions.h"

namespace webrtc {

namespace {
constexpr float kPi = 3.14159265f;
}  // namespace

void SineWaveGenerator::GenerateNextFrame(AudioFrame* frame) {
  RTC_DCHECK(frame);
  int16_t* frame_data = frame->mutable_data();
  for (size_t i = 0; i < frame->samples_per_channel_; ++i) {
    for (size_t ch = 0; ch < frame->num_channels_; ++ch) {
      frame_data[frame->num_channels_ * i + ch] =
          rtc::saturated_cast<int16_t>(amplitude_ * sinf(phase_));
    }
    phase_ += wave_frequency_hz_ * 2 * kPi / frame->sample_rate_hz_;
  }
}
}  // namespace webrtc
