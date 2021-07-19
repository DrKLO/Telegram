/*
 *  Copyright (c) 2018 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "modules/audio_processing/aec3/subtractor_output.h"

#include <numeric>

namespace webrtc {

SubtractorOutput::SubtractorOutput() = default;
SubtractorOutput::~SubtractorOutput() = default;

void SubtractorOutput::Reset() {
  s_refined.fill(0.f);
  s_coarse.fill(0.f);
  e_refined.fill(0.f);
  e_coarse.fill(0.f);
  E_refined.re.fill(0.f);
  E_refined.im.fill(0.f);
  E2_refined.fill(0.f);
  E2_coarse.fill(0.f);
  e2_refined = 0.f;
  e2_coarse = 0.f;
  s2_refined = 0.f;
  s2_coarse = 0.f;
  y2 = 0.f;
}

void SubtractorOutput::ComputeMetrics(rtc::ArrayView<const float> y) {
  const auto sum_of_squares = [](float a, float b) { return a + b * b; };
  y2 = std::accumulate(y.begin(), y.end(), 0.f, sum_of_squares);
  e2_refined =
      std::accumulate(e_refined.begin(), e_refined.end(), 0.f, sum_of_squares);
  e2_coarse =
      std::accumulate(e_coarse.begin(), e_coarse.end(), 0.f, sum_of_squares);
  s2_refined =
      std::accumulate(s_refined.begin(), s_refined.end(), 0.f, sum_of_squares);
  s2_coarse =
      std::accumulate(s_coarse.begin(), s_coarse.end(), 0.f, sum_of_squares);

  s_refined_max_abs = *std::max_element(s_refined.begin(), s_refined.end());
  s_refined_max_abs =
      std::max(s_refined_max_abs,
               -(*std::min_element(s_refined.begin(), s_refined.end())));

  s_coarse_max_abs = *std::max_element(s_coarse.begin(), s_coarse.end());
  s_coarse_max_abs = std::max(
      s_coarse_max_abs, -(*std::min_element(s_coarse.begin(), s_coarse.end())));
}

}  // namespace webrtc
