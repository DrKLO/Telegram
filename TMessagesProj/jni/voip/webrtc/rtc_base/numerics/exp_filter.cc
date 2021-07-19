/*
 *  Copyright (c) 2011 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "rtc_base/numerics/exp_filter.h"

#include <cmath>

namespace rtc {

const float ExpFilter::kValueUndefined = -1.0f;

void ExpFilter::Reset(float alpha) {
  alpha_ = alpha;
  filtered_ = kValueUndefined;
}

float ExpFilter::Apply(float exp, float sample) {
  if (filtered_ == kValueUndefined) {
    // Initialize filtered value.
    filtered_ = sample;
  } else if (exp == 1.0) {
    filtered_ = alpha_ * filtered_ + (1 - alpha_) * sample;
  } else {
    float alpha = std::pow(alpha_, exp);
    filtered_ = alpha * filtered_ + (1 - alpha) * sample;
  }
  if (max_ != kValueUndefined && filtered_ > max_) {
    filtered_ = max_;
  }
  return filtered_;
}

void ExpFilter::UpdateBase(float alpha) {
  alpha_ = alpha;
}
}  // namespace rtc
