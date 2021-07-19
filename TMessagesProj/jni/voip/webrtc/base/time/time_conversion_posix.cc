// Copyright (c) 2012 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

#include "base/time/time.h"

#include <stdint.h>
#include <sys/time.h>
#include <time.h>

#include <limits>

#include "base/logging.h"

namespace base {

// static
TimeDelta TimeDelta::FromTimeSpec(const timespec& ts) {
  return TimeDelta(ts.tv_sec * Time::kMicrosecondsPerSecond +
                   ts.tv_nsec / Time::kNanosecondsPerMicrosecond);
}

struct timespec TimeDelta::ToTimeSpec() const {
  int64_t microseconds = InMicroseconds();
  time_t seconds = 0;
  if (microseconds >= Time::kMicrosecondsPerSecond) {
    seconds = InSeconds();
    microseconds -= seconds * Time::kMicrosecondsPerSecond;
  }
  struct timespec result = {
      seconds,
      static_cast<long>(microseconds * Time::kNanosecondsPerMicrosecond)};
  return result;
}

// static
Time Time::FromTimeVal(struct timeval t) {
  DCHECK_LT(t.tv_usec, static_cast<int>(Time::kMicrosecondsPerSecond));
  DCHECK_GE(t.tv_usec, 0);
  if (t.tv_usec == 0 && t.tv_sec == 0)
    return Time();
  if (t.tv_usec == static_cast<suseconds_t>(Time::kMicrosecondsPerSecond) - 1 &&
      t.tv_sec == std::numeric_limits<time_t>::max())
    return Max();
  return Time((static_cast<int64_t>(t.tv_sec) * Time::kMicrosecondsPerSecond) +
              t.tv_usec + kTimeTToMicrosecondsOffset);
}

struct timeval Time::ToTimeVal() const {
  struct timeval result;
  if (is_null()) {
    result.tv_sec = 0;
    result.tv_usec = 0;
    return result;
  }
  if (is_max()) {
    result.tv_sec = std::numeric_limits<time_t>::max();
    result.tv_usec = static_cast<suseconds_t>(Time::kMicrosecondsPerSecond) - 1;
    return result;
  }
  int64_t us = us_ - kTimeTToMicrosecondsOffset;
  result.tv_sec = us / Time::kMicrosecondsPerSecond;
  result.tv_usec = us % Time::kMicrosecondsPerSecond;
  return result;
}

}  // namespace base
