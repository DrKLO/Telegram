/*
 *  Copyright (c) 2016 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "modules/audio_processing/agc2/biquad_filter.h"

#include "rtc_base/arraysize.h"

namespace webrtc {

BiQuadFilter::BiQuadFilter(const Config& config)
    : config_(config), state_({}) {}

BiQuadFilter::~BiQuadFilter() = default;

void BiQuadFilter::SetConfig(const Config& config) {
  config_ = config;
  state_ = {};
}

void BiQuadFilter::Reset() {
  state_ = {};
}

void BiQuadFilter::Process(rtc::ArrayView<const float> x,
                           rtc::ArrayView<float> y) {
  RTC_DCHECK_EQ(x.size(), y.size());
  const float config_a0 = config_.a[0];
  const float config_a1 = config_.a[1];
  const float config_b0 = config_.b[0];
  const float config_b1 = config_.b[1];
  const float config_b2 = config_.b[2];
  float state_a0 = state_.a[0];
  float state_a1 = state_.a[1];
  float state_b0 = state_.b[0];
  float state_b1 = state_.b[1];
  for (size_t k = 0, x_size = x.size(); k < x_size; ++k) {
    // Use a temporary variable for `x[k]` to allow in-place processing.
    const float tmp = x[k];
    float y_k = config_b0 * tmp + config_b1 * state_b0 + config_b2 * state_b1 -
                config_a0 * state_a0 - config_a1 * state_a1;
    state_b1 = state_b0;
    state_b0 = tmp;
    state_a1 = state_a0;
    state_a0 = y_k;
    y[k] = y_k;
  }
  state_.a[0] = state_a0;
  state_.a[1] = state_a1;
  state_.b[0] = state_b0;
  state_.b[1] = state_b1;
}

}  // namespace webrtc
