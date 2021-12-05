/*
 *  Copyright (c) 2018 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "modules/audio_processing/aec3/reverb_frequency_response.h"

#include <stddef.h>

#include <algorithm>
#include <array>
#include <numeric>

#include "api/array_view.h"
#include "modules/audio_processing/aec3/aec3_common.h"
#include "rtc_base/checks.h"

namespace webrtc {

namespace {

// Computes the ratio of the energies between the direct path and the tail. The
// energy is computed in the power spectrum domain discarding the DC
// contributions.
float AverageDecayWithinFilter(
    rtc::ArrayView<const float> freq_resp_direct_path,
    rtc::ArrayView<const float> freq_resp_tail) {
  // Skipping the DC for the ratio computation
  constexpr size_t kSkipBins = 1;
  RTC_CHECK_EQ(freq_resp_direct_path.size(), freq_resp_tail.size());

  float direct_path_energy =
      std::accumulate(freq_resp_direct_path.begin() + kSkipBins,
                      freq_resp_direct_path.end(), 0.f);

  if (direct_path_energy == 0.f) {
    return 0.f;
  }

  float tail_energy = std::accumulate(freq_resp_tail.begin() + kSkipBins,
                                      freq_resp_tail.end(), 0.f);
  return tail_energy / direct_path_energy;
}

}  // namespace

ReverbFrequencyResponse::ReverbFrequencyResponse() {
  tail_response_.fill(0.f);
}
ReverbFrequencyResponse::~ReverbFrequencyResponse() = default;

void ReverbFrequencyResponse::Update(
    const std::vector<std::array<float, kFftLengthBy2Plus1>>&
        frequency_response,
    int filter_delay_blocks,
    const absl::optional<float>& linear_filter_quality,
    bool stationary_block) {
  if (stationary_block || !linear_filter_quality) {
    return;
  }

  Update(frequency_response, filter_delay_blocks, *linear_filter_quality);
}

void ReverbFrequencyResponse::Update(
    const std::vector<std::array<float, kFftLengthBy2Plus1>>&
        frequency_response,
    int filter_delay_blocks,
    float linear_filter_quality) {
  rtc::ArrayView<const float> freq_resp_tail(
      frequency_response[frequency_response.size() - 1]);

  rtc::ArrayView<const float> freq_resp_direct_path(
      frequency_response[filter_delay_blocks]);

  float average_decay =
      AverageDecayWithinFilter(freq_resp_direct_path, freq_resp_tail);

  const float smoothing = 0.2f * linear_filter_quality;
  average_decay_ += smoothing * (average_decay - average_decay_);

  for (size_t k = 0; k < kFftLengthBy2Plus1; ++k) {
    tail_response_[k] = freq_resp_direct_path[k] * average_decay_;
  }

  for (size_t k = 1; k < kFftLengthBy2; ++k) {
    const float avg_neighbour =
        0.5f * (tail_response_[k - 1] + tail_response_[k + 1]);
    tail_response_[k] = std::max(tail_response_[k], avg_neighbour);
  }
}

}  // namespace webrtc
