/*
 *  Copyright 2019 The WebRTC Project Authors. All rights reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "rtc_base/numerics/event_based_exponential_moving_average.h"

#include <cmath>

#include "rtc_base/checks.h"

namespace {

// For a normal distributed value, the 95% double sided confidence interval is
// is 1.96 * stddev.
constexpr double ninetyfive_percent_confidence = 1.96;

}  // namespace

namespace rtc {

// `half_time` specifies how much weight will be given to old samples,
// a sample gets exponentially less weight so that it's 50%
// after `half_time` time units has passed.
EventBasedExponentialMovingAverage::EventBasedExponentialMovingAverage(
    int half_time) {
  SetHalfTime(half_time);
}

void EventBasedExponentialMovingAverage::SetHalfTime(int half_time) {
  tau_ = static_cast<double>(half_time) / log(2);
  Reset();
}

void EventBasedExponentialMovingAverage::Reset() {
  value_ = std::nan("uninit");
  sample_variance_ = std::numeric_limits<double>::infinity();
  estimator_variance_ = 1;
  last_observation_timestamp_.reset();
}

void EventBasedExponentialMovingAverage::AddSample(int64_t now, int sample) {
  if (!last_observation_timestamp_.has_value()) {
    value_ = sample;
  } else {
    // TODO(webrtc:11140): This should really be > (e.g not >=)
    // but some pesky tests run with simulated clock and let
    // samples arrive simultaneously!
    RTC_DCHECK(now >= *last_observation_timestamp_);
    // Variance gets computed after second sample.
    int64_t age = now - *last_observation_timestamp_;
    double e = exp(-age / tau_);
    double alpha = e / (1 + e);
    double one_minus_alpha = 1 - alpha;
    double sample_diff = sample - value_;
    value_ = one_minus_alpha * value_ + alpha * sample;
    estimator_variance_ =
        (one_minus_alpha * one_minus_alpha) * estimator_variance_ +
        (alpha * alpha);
    if (sample_variance_ == std::numeric_limits<double>::infinity()) {
      // First variance.
      sample_variance_ = sample_diff * sample_diff;
    } else {
      double new_variance = one_minus_alpha * sample_variance_ +
                            alpha * sample_diff * sample_diff;
      sample_variance_ = new_variance;
    }
  }
  last_observation_timestamp_ = now;
}

double EventBasedExponentialMovingAverage::GetConfidenceInterval() const {
  return ninetyfive_percent_confidence *
         sqrt(sample_variance_ * estimator_variance_);
}

}  // namespace rtc
