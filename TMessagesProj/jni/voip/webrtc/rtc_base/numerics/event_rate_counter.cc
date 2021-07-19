/*
 *  Copyright (c) 2019 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */
#include "rtc_base/numerics/event_rate_counter.h"

#include <algorithm>

namespace webrtc {

void EventRateCounter::AddEvent(Timestamp event_time) {
  if (first_time_.IsFinite())
    interval_.AddSample(event_time - last_time_);
  first_time_ = std::min(first_time_, event_time);
  last_time_ = std::max(last_time_, event_time);
  event_count_++;
}

void EventRateCounter::AddEvents(EventRateCounter other) {
  first_time_ = std::min(first_time_, other.first_time_);
  last_time_ = std::max(last_time_, other.last_time_);
  event_count_ += other.event_count_;
  interval_.AddSamples(other.interval_);
}

bool EventRateCounter::IsEmpty() const {
  return first_time_ == last_time_;
}

double EventRateCounter::Rate() const {
  if (event_count_ == 0)
    return 0;
  if (event_count_ == 1)
    return NAN;
  return (event_count_ - 1) / (last_time_ - first_time_).seconds<double>();
}

TimeDelta EventRateCounter::TotalDuration() const {
  if (first_time_.IsInfinite()) {
    return TimeDelta::Zero();
  }
  return last_time_ - first_time_;
}
}  // namespace webrtc
