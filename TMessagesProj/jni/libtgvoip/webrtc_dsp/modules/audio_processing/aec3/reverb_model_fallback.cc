/*
 *  Copyright (c) 2018 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "modules/audio_processing/aec3/reverb_model_fallback.h"

#include <algorithm>
#include <functional>

#include "modules/audio_processing/aec3/aec3_common.h"
#include "rtc_base/checks.h"

namespace webrtc {

ReverbModelFallback::ReverbModelFallback(size_t length_blocks)
    : S2_old_(length_blocks) {
  Reset();
}

ReverbModelFallback::~ReverbModelFallback() = default;

void ReverbModelFallback::Reset() {
  R2_reverb_.fill(0.f);
  for (auto& S2_k : S2_old_) {
    S2_k.fill(0.f);
  }
}

void ReverbModelFallback::AddEchoReverb(
    const std::array<float, kFftLengthBy2Plus1>& S2,
    size_t delay,
    float reverb_decay_factor,
    std::array<float, kFftLengthBy2Plus1>* R2) {
  // Compute the decay factor for how much the echo has decayed before leaving
  // the region covered by the linear model.
  auto integer_power = [](float base, int exp) {
    float result = 1.f;
    for (int k = 0; k < exp; ++k) {
      result *= base;
    }
    return result;
  };
  RTC_DCHECK_LE(delay, S2_old_.size());
  const float reverb_decay_for_delay =
      integer_power(reverb_decay_factor, S2_old_.size() - delay);

  // Update the estimate of the reverberant residual echo power.
  S2_old_index_ = S2_old_index_ > 0 ? S2_old_index_ - 1 : S2_old_.size() - 1;
  const auto& S2_end = S2_old_[S2_old_index_];
  std::transform(
      S2_end.begin(), S2_end.end(), R2_reverb_.begin(), R2_reverb_.begin(),
      [reverb_decay_for_delay, reverb_decay_factor](float a, float b) {
        return (b + a * reverb_decay_for_delay) * reverb_decay_factor;
      });

  // Update the buffer of old echo powers.
  std::copy(S2.begin(), S2.end(), S2_old_[S2_old_index_].begin());

  // Add the power of the echo reverb to the residual echo power.
  std::transform(R2->begin(), R2->end(), R2_reverb_.begin(), R2->begin(),
                 std::plus<float>());
}

}  // namespace webrtc
