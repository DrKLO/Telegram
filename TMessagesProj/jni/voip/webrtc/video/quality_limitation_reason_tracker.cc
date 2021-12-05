/*
 *  Copyright 2019 The WebRTC Project Authors. All rights reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "video/quality_limitation_reason_tracker.h"

#include <utility>

#include "rtc_base/checks.h"

namespace webrtc {

QualityLimitationReasonTracker::QualityLimitationReasonTracker(Clock* clock)
    : clock_(clock),
      current_reason_(QualityLimitationReason::kNone),
      current_reason_updated_timestamp_ms_(clock_->TimeInMilliseconds()),
      durations_ms_({std::make_pair(QualityLimitationReason::kNone, 0),
                     std::make_pair(QualityLimitationReason::kCpu, 0),
                     std::make_pair(QualityLimitationReason::kBandwidth, 0),
                     std::make_pair(QualityLimitationReason::kOther, 0)}) {}

QualityLimitationReason QualityLimitationReasonTracker::current_reason() const {
  return current_reason_;
}

void QualityLimitationReasonTracker::SetReason(QualityLimitationReason reason) {
  if (reason == current_reason_)
    return;
  int64_t now_ms = clock_->TimeInMilliseconds();
  durations_ms_[current_reason_] +=
      now_ms - current_reason_updated_timestamp_ms_;
  current_reason_ = reason;
  current_reason_updated_timestamp_ms_ = now_ms;
}

std::map<QualityLimitationReason, int64_t>
QualityLimitationReasonTracker::DurationsMs() const {
  std::map<QualityLimitationReason, int64_t> total_durations_ms = durations_ms_;
  auto it = total_durations_ms.find(current_reason_);
  RTC_DCHECK(it != total_durations_ms.end());
  it->second +=
      clock_->TimeInMilliseconds() - current_reason_updated_timestamp_ms_;
  return total_durations_ms;
}

}  // namespace webrtc
