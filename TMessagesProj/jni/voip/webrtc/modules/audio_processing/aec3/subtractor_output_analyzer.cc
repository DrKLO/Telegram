/*
 *  Copyright (c) 2018 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "modules/audio_processing/aec3/subtractor_output_analyzer.h"

#include <algorithm>

#include "modules/audio_processing/aec3/aec3_common.h"

namespace webrtc {

SubtractorOutputAnalyzer::SubtractorOutputAnalyzer(size_t num_capture_channels)
    : filters_converged_(num_capture_channels, false) {}

void SubtractorOutputAnalyzer::Update(
    rtc::ArrayView<const SubtractorOutput> subtractor_output,
    bool* any_filter_converged,
    bool* any_coarse_filter_converged,
    bool* all_filters_diverged) {
  RTC_DCHECK(any_filter_converged);
  RTC_DCHECK(all_filters_diverged);
  RTC_DCHECK_EQ(subtractor_output.size(), filters_converged_.size());

  *any_filter_converged = false;
  *any_coarse_filter_converged = false;
  *all_filters_diverged = true;

  for (size_t ch = 0; ch < subtractor_output.size(); ++ch) {
    const float y2 = subtractor_output[ch].y2;
    const float e2_refined = subtractor_output[ch].e2_refined;
    const float e2_coarse = subtractor_output[ch].e2_coarse;

    constexpr float kConvergenceThreshold = 50 * 50 * kBlockSize;
    constexpr float kConvergenceThresholdLowLevel = 20 * 20 * kBlockSize;
    bool refined_filter_converged =
        e2_refined < 0.5f * y2 && y2 > kConvergenceThreshold;
    bool coarse_filter_converged_strict =
        e2_coarse < 0.05f * y2 && y2 > kConvergenceThreshold;
    bool coarse_filter_converged_relaxed =
        e2_coarse < 0.2f * y2 && y2 > kConvergenceThresholdLowLevel;
    float min_e2 = std::min(e2_refined, e2_coarse);
    bool filter_diverged = min_e2 > 1.5f * y2 && y2 > 30.f * 30.f * kBlockSize;
    filters_converged_[ch] =
        refined_filter_converged || coarse_filter_converged_strict;

    *any_filter_converged = *any_filter_converged || filters_converged_[ch];
    *any_coarse_filter_converged =
        *any_coarse_filter_converged || coarse_filter_converged_relaxed;
    *all_filters_diverged = *all_filters_diverged && filter_diverged;
  }
}

void SubtractorOutputAnalyzer::HandleEchoPathChange() {
  std::fill(filters_converged_.begin(), filters_converged_.end(), false);
}

}  // namespace webrtc
