/*
 *  Copyright (c) 2016 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "modules/audio_processing/echo_detector/normalized_covariance_estimator.h"

#include <math.h>

#include "rtc_base/checks.h"

namespace webrtc {
namespace {

// Parameter controlling the adaptation speed.
constexpr float kAlpha = 0.001f;

}  // namespace

void NormalizedCovarianceEstimator::Update(float x,
                                           float x_mean,
                                           float x_sigma,
                                           float y,
                                           float y_mean,
                                           float y_sigma) {
  covariance_ =
      (1.f - kAlpha) * covariance_ + kAlpha * (x - x_mean) * (y - y_mean);
  normalized_cross_correlation_ = covariance_ / (x_sigma * y_sigma + .0001f);
  RTC_DCHECK(isfinite(covariance_));
  RTC_DCHECK(isfinite(normalized_cross_correlation_));
}

void NormalizedCovarianceEstimator::Clear() {
  covariance_ = 0.f;
  normalized_cross_correlation_ = 0.f;
}

}  // namespace webrtc
