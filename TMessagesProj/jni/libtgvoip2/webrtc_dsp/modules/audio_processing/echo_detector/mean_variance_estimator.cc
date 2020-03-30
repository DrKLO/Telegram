/*
 *  Copyright (c) 2016 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "modules/audio_processing/echo_detector/mean_variance_estimator.h"

#include <math.h>

#include "rtc_base/checks.h"

namespace webrtc {
namespace {

// Parameter controlling the adaptation speed.
constexpr float kAlpha = 0.001f;

}  // namespace

void MeanVarianceEstimator::Update(float value) {
  mean_ = (1.f - kAlpha) * mean_ + kAlpha * value;
  variance_ =
      (1.f - kAlpha) * variance_ + kAlpha * (value - mean_) * (value - mean_);
  RTC_DCHECK(isfinite(mean_));
  RTC_DCHECK(isfinite(variance_));
}

float MeanVarianceEstimator::std_deviation() const {
  RTC_DCHECK_GE(variance_, 0.f);
  return sqrtf(variance_);
}

float MeanVarianceEstimator::mean() const {
  return mean_;
}

void MeanVarianceEstimator::Clear() {
  mean_ = 0.f;
  variance_ = 0.f;
}

}  // namespace webrtc
