/*
 *  Copyright (c) 2018 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "modules/audio_processing/aec3/reverb_model_estimator.h"

namespace webrtc {

ReverbModelEstimator::ReverbModelEstimator(const EchoCanceller3Config& config,
                                           size_t num_capture_channels)
    : reverb_decay_estimators_(num_capture_channels),
      reverb_frequency_responses_(num_capture_channels) {
  for (size_t ch = 0; ch < reverb_decay_estimators_.size(); ++ch) {
    reverb_decay_estimators_[ch] =
        std::make_unique<ReverbDecayEstimator>(config);
  }
}

ReverbModelEstimator::~ReverbModelEstimator() = default;

void ReverbModelEstimator::Update(
    rtc::ArrayView<const std::vector<float>> impulse_responses,
    rtc::ArrayView<const std::vector<std::array<float, kFftLengthBy2Plus1>>>
        frequency_responses,
    rtc::ArrayView<const absl::optional<float>> linear_filter_qualities,
    rtc::ArrayView<const int> filter_delays_blocks,
    const std::vector<bool>& usable_linear_estimates,
    bool stationary_block) {
  const size_t num_capture_channels = reverb_decay_estimators_.size();
  RTC_DCHECK_EQ(num_capture_channels, impulse_responses.size());
  RTC_DCHECK_EQ(num_capture_channels, frequency_responses.size());
  RTC_DCHECK_EQ(num_capture_channels, usable_linear_estimates.size());

  for (size_t ch = 0; ch < num_capture_channels; ++ch) {
    // Estimate the frequency response for the reverb.
    reverb_frequency_responses_[ch].Update(
        frequency_responses[ch], filter_delays_blocks[ch],
        linear_filter_qualities[ch], stationary_block);

    // Estimate the reverb decay,
    reverb_decay_estimators_[ch]->Update(
        impulse_responses[ch], linear_filter_qualities[ch],
        filter_delays_blocks[ch], usable_linear_estimates[ch],
        stationary_block);
  }
}

}  // namespace webrtc
