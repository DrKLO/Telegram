/*
 *  Copyright (c) 2016 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "modules/pacing/interval_budget.h"

#include <algorithm>

#include "rtc_base/numerics/safe_conversions.h"

namespace webrtc {
namespace {
constexpr int64_t kWindowMs = 500;
}

IntervalBudget::IntervalBudget(int initial_target_rate_kbps)
    : IntervalBudget(initial_target_rate_kbps, false) {}

IntervalBudget::IntervalBudget(int initial_target_rate_kbps,
                               bool can_build_up_underuse)
    : bytes_remaining_(0), can_build_up_underuse_(can_build_up_underuse) {
  set_target_rate_kbps(initial_target_rate_kbps);
}

void IntervalBudget::set_target_rate_kbps(int target_rate_kbps) {
  target_rate_kbps_ = target_rate_kbps;
  max_bytes_in_budget_ = (kWindowMs * target_rate_kbps_) / 8;
  bytes_remaining_ = std::min(std::max(-max_bytes_in_budget_, bytes_remaining_),
                              max_bytes_in_budget_);
}

void IntervalBudget::IncreaseBudget(int64_t delta_time_ms) {
  int64_t bytes = target_rate_kbps_ * delta_time_ms / 8;
  if (bytes_remaining_ < 0 || can_build_up_underuse_) {
    // We overused last interval, compensate this interval.
    bytes_remaining_ = std::min(bytes_remaining_ + bytes, max_bytes_in_budget_);
  } else {
    // If we underused last interval we can't use it this interval.
    bytes_remaining_ = std::min(bytes, max_bytes_in_budget_);
  }
}

void IntervalBudget::UseBudget(size_t bytes) {
  bytes_remaining_ = std::max(bytes_remaining_ - static_cast<int>(bytes),
                              -max_bytes_in_budget_);
}

size_t IntervalBudget::bytes_remaining() const {
  return rtc::saturated_cast<size_t>(std::max<int64_t>(0, bytes_remaining_));
}

double IntervalBudget::budget_ratio() const {
  if (max_bytes_in_budget_ == 0)
    return 0.0;
  return static_cast<double>(bytes_remaining_) / max_bytes_in_budget_;
}

int IntervalBudget::target_rate_kbps() const {
  return target_rate_kbps_;
}

}  // namespace webrtc
