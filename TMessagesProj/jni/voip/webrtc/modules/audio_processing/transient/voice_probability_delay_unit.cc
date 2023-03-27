/*
 *  Copyright (c) 2022 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "modules/audio_processing/transient/voice_probability_delay_unit.h"

#include <array>

#include "rtc_base/checks.h"

namespace webrtc {

VoiceProbabilityDelayUnit::VoiceProbabilityDelayUnit(int delay_num_samples,
                                                     int sample_rate_hz) {
  Initialize(delay_num_samples, sample_rate_hz);
}

void VoiceProbabilityDelayUnit::Initialize(int delay_num_samples,
                                           int sample_rate_hz) {
  RTC_DCHECK_GE(delay_num_samples, 0);
  RTC_DCHECK_LE(delay_num_samples, sample_rate_hz / 50)
      << "The implementation does not support delays greater than 20 ms.";
  int frame_size = rtc::CheckedDivExact(sample_rate_hz, 100);  // 10 ms.
  if (delay_num_samples <= frame_size) {
    weights_[0] = 0.0f;
    weights_[1] = static_cast<float>(delay_num_samples) / frame_size;
    weights_[2] =
        static_cast<float>(frame_size - delay_num_samples) / frame_size;
  } else {
    delay_num_samples -= frame_size;
    weights_[0] = static_cast<float>(delay_num_samples) / frame_size;
    weights_[1] =
        static_cast<float>(frame_size - delay_num_samples) / frame_size;
    weights_[2] = 0.0f;
  }

  // Resets the delay unit.
  last_probabilities_.fill(0.0f);
}

float VoiceProbabilityDelayUnit::Delay(float voice_probability) {
  float weighted_probability = weights_[0] * last_probabilities_[0] +
                               weights_[1] * last_probabilities_[1] +
                               weights_[2] * voice_probability;
  last_probabilities_[0] = last_probabilities_[1];
  last_probabilities_[1] = voice_probability;
  return weighted_probability;
}

}  // namespace webrtc
