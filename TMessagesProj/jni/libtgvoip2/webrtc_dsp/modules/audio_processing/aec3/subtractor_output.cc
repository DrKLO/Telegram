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
  s_main.fill(0.f);
  s_shadow.fill(0.f);
  e_main.fill(0.f);
  e_shadow.fill(0.f);
  E_main.re.fill(0.f);
  E_main.im.fill(0.f);
  E2_main.fill(0.f);
  E2_shadow.fill(0.f);
  e2_main = 0.f;
  e2_shadow = 0.f;
  s2_main = 0.f;
  s2_shadow = 0.f;
  y2 = 0.f;
}

void SubtractorOutput::ComputeMetrics(rtc::ArrayView<const float> y) {
  const auto sum_of_squares = [](float a, float b) { return a + b * b; };
  y2 = std::accumulate(y.begin(), y.end(), 0.f, sum_of_squares);
  e2_main = std::accumulate(e_main.begin(), e_main.end(), 0.f, sum_of_squares);
  e2_shadow =
      std::accumulate(e_shadow.begin(), e_shadow.end(), 0.f, sum_of_squares);
  s2_main = std::accumulate(s_main.begin(), s_main.end(), 0.f, sum_of_squares);
  s2_shadow =
      std::accumulate(s_shadow.begin(), s_shadow.end(), 0.f, sum_of_squares);

  s_main_max_abs = *std::max_element(s_main.begin(), s_main.end());
  s_main_max_abs = std::max(s_main_max_abs,
                            -(*std::min_element(s_main.begin(), s_main.end())));

  s_shadow_max_abs = *std::max_element(s_shadow.begin(), s_shadow.end());
  s_shadow_max_abs = std::max(
      s_shadow_max_abs, -(*std::min_element(s_shadow.begin(), s_shadow.end())));
}

}  // namespace webrtc
