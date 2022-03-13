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
  for (size_t k = 0; k < x.size(); ++k) {
    // Use a temporary variable for `x[k]` to allow in-place processing.
    const float tmp = x[k];
    y[k] = config_.b[0] * tmp + config_.b[1] * state_.b[0] +
           config_.b[2] * state_.b[1] - config_.a[0] * state_.a[0] -
           config_.a[1] * state_.a[1];
    state_.b[1] = state_.b[0];
    state_.b[0] = tmp;
    state_.a[1] = state_.a[0];
    state_.a[0] = y[k];
  }
}

}  // namespace webrtc
