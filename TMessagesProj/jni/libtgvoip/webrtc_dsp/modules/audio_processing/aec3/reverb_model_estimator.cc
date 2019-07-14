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

ReverbModelEstimator::ReverbModelEstimator(const EchoCanceller3Config& config)
    : reverb_decay_estimator_(config) {}

ReverbModelEstimator::~ReverbModelEstimator() = default;

void ReverbModelEstimator::Update(
    rtc::ArrayView<const float> impulse_response,
    const std::vector<std::array<float, kFftLengthBy2Plus1>>&
        frequency_response,
    const absl::optional<float>& linear_filter_quality,
    int filter_delay_blocks,
    bool usable_linear_estimate,
    bool stationary_block) {
  // Estimate the frequency response for the reverb.
  reverb_frequency_response_.Update(frequency_response, filter_delay_blocks,
                                    linear_filter_quality, stationary_block);

  // Estimate the reverb decay,
  reverb_decay_estimator_.Update(impulse_response, linear_filter_quality,
                                 filter_delay_blocks, usable_linear_estimate,
                                 stationary_block);
}
}  // namespace webrtc
