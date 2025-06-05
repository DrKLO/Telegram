/*
 *  Copyright (c) 2012 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "modules/remote_bitrate_estimator/overuse_detector.h"

#include <math.h>
#include <stdio.h>

#include <algorithm>
#include <string>

#include "modules/remote_bitrate_estimator/test/bwe_test_logging.h"
#include "rtc_base/checks.h"
#include "rtc_base/numerics/safe_minmax.h"

namespace webrtc {
namespace {

constexpr double kMaxAdaptOffsetMs = 15.0;
constexpr double kOverUsingTimeThreshold = 10;
constexpr int kMaxNumDeltas = 60;
constexpr double kUp = 0.0087;
constexpr double kDown = 0.039;

}  // namespace

OveruseDetector::OveruseDetector() = default;

BandwidthUsage OveruseDetector::State() const {
  return hypothesis_;
}

BandwidthUsage OveruseDetector::Detect(double offset,
                                       double ts_delta,
                                       int num_of_deltas,
                                       int64_t now_ms) {
  if (num_of_deltas < 2) {
    return BandwidthUsage::kBwNormal;
  }
  const double T = std::min(num_of_deltas, kMaxNumDeltas) * offset;
  BWE_TEST_LOGGING_PLOT(1, "T", now_ms, T);
  BWE_TEST_LOGGING_PLOT(1, "threshold", now_ms, threshold_);
  if (T > threshold_) {
    if (time_over_using_ == -1) {
      // Initialize the timer. Assume that we've been
      // over-using half of the time since the previous
      // sample.
      time_over_using_ = ts_delta / 2;
    } else {
      // Increment timer
      time_over_using_ += ts_delta;
    }
    overuse_counter_++;
    if (time_over_using_ > kOverUsingTimeThreshold && overuse_counter_ > 1) {
      if (offset >= prev_offset_) {
        time_over_using_ = 0;
        overuse_counter_ = 0;
        hypothesis_ = BandwidthUsage::kBwOverusing;
      }
    }
  } else if (T < -threshold_) {
    time_over_using_ = -1;
    overuse_counter_ = 0;
    hypothesis_ = BandwidthUsage::kBwUnderusing;
  } else {
    time_over_using_ = -1;
    overuse_counter_ = 0;
    hypothesis_ = BandwidthUsage::kBwNormal;
  }
  prev_offset_ = offset;

  UpdateThreshold(T, now_ms);

  return hypothesis_;
}

void OveruseDetector::UpdateThreshold(double modified_offset, int64_t now_ms) {
  if (last_update_ms_ == -1)
    last_update_ms_ = now_ms;

  if (fabs(modified_offset) > threshold_ + kMaxAdaptOffsetMs) {
    // Avoid adapting the threshold to big latency spikes, caused e.g.,
    // by a sudden capacity drop.
    last_update_ms_ = now_ms;
    return;
  }

  const double k = fabs(modified_offset) < threshold_ ? kDown : kUp;
  const int64_t kMaxTimeDeltaMs = 100;
  int64_t time_delta_ms = std::min(now_ms - last_update_ms_, kMaxTimeDeltaMs);
  threshold_ += k * (fabs(modified_offset) - threshold_) * time_delta_ms;
  threshold_ = rtc::SafeClamp(threshold_, 6.f, 600.f);
  last_update_ms_ = now_ms;
}

}  // namespace webrtc
