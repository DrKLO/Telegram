/*
 *  Copyright 2012 The WebRTC Project Authors. All rights reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "rtc_base/data_rate_limiter.h"

namespace rtc {

bool DataRateLimiter::CanUse(size_t desired, double time) {
  return ((time > period_end_ && desired <= max_per_period_) ||
          (used_in_period_ + desired) <= max_per_period_);
}

void DataRateLimiter::Use(size_t used, double time) {
  if (time > period_end_) {
    period_start_ = time;
    period_end_ = time + period_length_;
    used_in_period_ = 0;
  }
  used_in_period_ += used;
}

}  // namespace rtc
