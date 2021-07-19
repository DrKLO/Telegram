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

void ErlEstimator::Update(
    const std::vector<bool>& converged_filters,
    rtc::ArrayView<const std::array<float, kFftLengthBy2Plus1>> render_spectra,
    rtc::ArrayView<const std::array<float, kFftLengthBy2Plus1>>
        capture_spectra) {
  const size_t num_capture_channels = converged_filters.size();
  RTC_DCHECK_EQ(capture_spectra.size(), num_capture_channels);

  // Corresponds to WGN of power -46 dBFS.
  constexpr float kX2Min = 44015068.0f;

  const auto first_converged_iter =
      std::find(converged_filters.begin(), converged_filters.end(), true);
  const bool any_filter_converged =
      first_converged_iter != converged_filters.end();

  if (++blocks_since_reset_ < startup_phase_length_blocks__ ||
      !any_filter_converged) {
    return;
  }

  // Use the maximum spectrum across capture and the maximum across render.
  std::array<float, kFftLengthBy2Plus1> max_capture_spectrum_data;
  std::array<float, kFftLengthBy2Plus1> max_capture_spectrum =
      capture_spectra[/*channel=*/0];
  if (num_capture_channels > 1) {
    // Initialize using the first channel with a converged filter.
    const size_t first_converged =
        std::distance(converged_filters.begin(), first_converged_iter);
    RTC_DCHECK_GE(first_converged, 0);
    RTC_DCHECK_LT(first_converged, num_capture_channels);
    max_capture_spectrum_data = capture_spectra[first_converged];

    for (size_t ch = first_converged + 1; ch < num_capture_channels; ++ch) {
      if (!converged_filters[ch]) {
        continue;
      }
      for (size_t k = 0; k < kFftLengthBy2Plus1; ++k) {
        max_capture_spectrum_data[k] =
            std::max(max_capture_spectrum_data[k], capture_spectra[ch][k]);
      }
    }
    max_capture_spectrum = max_capture_spectrum_data;
  }

  const size_t num_render_channels = render_spectra.size();
  std::array<float, kFftLengthBy2Plus1> max_render_spectrum_data;
  rtc::ArrayView<const float, kFftLengthBy2Plus1> max_render_spectrum =
      render_spectra[/*channel=*/0];
  if (num_render_channels > 1) {
    std::copy(render_spectra[0].begin(), render_spectra[0].end(),
              max_render_spectrum_data.begin());
    for (size_t ch = 1; ch < num_render_channels; ++ch) {
      for (size_t k = 0; k < kFftLengthBy2Plus1; ++k) {
        max_render_spectrum_data[k] =
            std::max(max_render_spectrum_data[k], render_spectra[ch][k]);
      }
    }
    max_render_spectrum = max_render_spectrum_data;
  }

  const auto& X2 = max_render_spectrum;
  const auto& Y2 = max_capture_spectrum;

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
