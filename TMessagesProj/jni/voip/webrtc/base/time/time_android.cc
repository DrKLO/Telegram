// Copyright 2018 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

#include "base/time/time.h"

namespace base {

// static
TimeTicks TimeTicks::FromUptimeMillis(int64_t uptime_millis_value) {
  // The implementation of the SystemClock.uptimeMillis() in AOSP uses the same
  // clock as base::TimeTicks::Now(): clock_gettime(CLOCK_MONOTONIC), see in
  // platform/system/code:
  // 1. libutils/SystemClock.cpp
  // 2. libutils/Timers.cpp
  //
  // We are not aware of any motivations for Android OEMs to modify the AOSP
  // implementation of either uptimeMillis() or clock_gettime(CLOCK_MONOTONIC),
  // so we assume that there are no such customizations.
  //
  // Under these assumptions the conversion is as safe as copying the value of
  // base::TimeTicks::Now() with a loss of sub-millisecond precision.
  return TimeTicks(uptime_millis_value * Time::kMicrosecondsPerMillisecond);
}

}  // namespace base
