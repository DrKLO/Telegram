/*
 *  Copyright (c) 2017 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "modules/audio_processing/aec3/erl_estimator.h"

#include <algorithm>
#include <numeric>

#include "rtc_base/checks.h"

namespace webrtc {

namespace {

constexpr float kMinErl = 0.01f;
constexpr float kMaxErl = 1000.f;

}  // namespace

ErlEstimator::ErlEstimator(size_t startup_phase_length_blocks_)
    : startup_phase_length_blocks__(startup_phase_length_blocks_) {
  erl_.fill(kMaxErl);
  hold_counters_.fill(0);
  erl_time_domain_ = kMaxErl;
  hold_counter_time_domain_ = 0;
}

ErlEstimator::~ErlEstimator() = default;

void ErlEstimator::Reset() {
  blocks_since_reset_ = 0;
}

void ErlEstimator::Update(bool converged_filter,
                          rtc::ArrayView<const float> render_spectrum,
                          rtc::ArrayView<const float> capture_spectrum) {
  RTC_DCHECK_EQ(kFftLengthBy2Plus1, render_spectrum.size());
  RTC_DCHECK_EQ(kFftLengthBy2Plus1, capture_spectrum.size());
  const auto& X2 = render_spectrum;
  const auto& Y2 = capture_spectrum;

  // Corresponds to WGN of power -46 dBFS.
  constexpr float kX2Min = 44015068.0f;

  if (++blocks_since_reset_ < startup_phase_length_blocks__ ||
      !converged_filter) {
    return;
  }

  // Update the estimates in a maximum statistics manner.
  for (size_t k = 1; k < kFftLengthBy2; ++k) {
    if (X2[k] > kX2Min) {
      const float new_erl = Y2[k] / X2[k];
      if (new_erl < erl_[k]) {
        hold_counters_[k - 1] = 1000;
        erl_[k] += 0.1f * (new_erl - erl_[k]);
        erl_[k] = std::max(erl_[k], kMinErl);
      }
    }
  }

  std::for_each(hold_counters_.begin(), hold_counters_.end(),
                [](int& a) { --a; });
  std::transform(hold_counters_.begin(), hold_counters_.end(), erl_.begin() + 1,
                 erl_.begin() + 1, [](int a, float b) {
                   return a > 0 ? b : std::min(kMaxErl, 2.f * b);
                 });

  erl_[0] = erl_[1];
  erl_[kFftLengthBy2] = erl_[kFftLengthBy2 - 1];

  // Compute ERL over all frequency bins.
  const float X2_sum = std::accumulate(X2.begin(), X2.end(), 0.0f);

  if (X2_sum > kX2Min * X2.size()) {
    const float Y2_sum = std::accumulate(Y2.begin(), Y2.end(), 0.0f);
    const float new_erl = Y2_sum / X2_sum;
    if (new_erl < erl_time_domain_) {
      hold_counter_time_domain_ = 1000;
      erl_time_domain_ += 0.1f * (new_erl - erl_time_domain_);
      erl_time_domain_ = std::max(erl_time_domain_, kMinErl);
    }
  }

  --hold_counter_time_domain_;
  erl_time_domain_ = (hold_counter_time_domain_ > 0)
                         ? erl_time_domain_
                         : std::min(kMaxErl, 2.f * erl_time_domain_);
}

}  // namespace webrtc
