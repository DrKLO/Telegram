/*
 *  Copyright 2005 The WebRTC Project Authors. All rights reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#ifndef RTC_BASE_TIMEUTILS_H_
#define RTC_BASE_TIMEUTILS_H_

#include <stdint.h>
#include <time.h>
#include <string>

#include "rtc_base/checks.h"
#include "rtc_base/strings/string_builder.h"

namespace rtc {

static const int64_t kNumMillisecsPerSec = INT64_C(1000);
static const int64_t kNumMicrosecsPerSec = INT64_C(1000000);
static const int64_t kNumNanosecsPerSec = INT64_C(1000000000);

static const int64_t kNumMicrosecsPerMillisec =
    kNumMicrosecsPerSec / kNumMillisecsPerSec;
static const int64_t kNumNanosecsPerMillisec =
    kNumNanosecsPerSec / kNumMillisecsPerSec;
static const int64_t kNumNanosecsPerMicrosec =
    kNumNanosecsPerSec / kNumMicrosecsPerSec;

// TODO(honghaiz): Define a type for the time value specifically.

class ClockInterface {
 public:
  virtual ~ClockInterface() {}
  virtual int64_t TimeNanos() const = 0;
};

// Sets the global source of time. This is useful mainly for unit tests.
//
// Returns the previously set ClockInterface, or nullptr if none is set.
//
// Does not transfer ownership of the clock. SetClockForTesting(nullptr)
// should be called before the ClockInterface is deleted.
//
// This method is not thread-safe; it should only be used when no other thread
// is running (for example, at the start/end of a unit test, or start/end of
// main()).
//
// TODO(deadbeef): Instead of having functions that access this global
// ClockInterface, we may want to pass the ClockInterface into everything
// that uses it, eliminating the need for a global variable and this function.
ClockInterface* SetClockForTesting(ClockInterface* clock);

// Returns previously set clock, or nullptr if no custom clock is being used.
ClockInterface* GetClockForTesting();

// Returns the actual system time, even if a clock is set for testing.
// Useful for timeouts while using a test clock, or for logging.
int64_t SystemTimeNanos();
int64_t SystemTimeMillis();

// Returns the current time in milliseconds in 32 bits.
uint32_t Time32();

// Returns the current time in milliseconds in 64 bits.
int64_t TimeMillis();
// Deprecated. Do not use this in any new code.
inline int64_t Time() {
  return TimeMillis();
}

// Returns the current time in microseconds.
int64_t TimeMicros();

// Returns the current time in nanoseconds.
int64_t TimeNanos();

// Returns a future timestamp, 'elapsed' milliseconds from now.
int64_t TimeAfter(int64_t elapsed);

// Number of milliseconds that would elapse between 'earlier' and 'later'
// timestamps.  The value is negative if 'later' occurs before 'earlier'.
int64_t TimeDiff(int64_t later, int64_t earlier);
int32_t TimeDiff32(uint32_t later, uint32_t earlier);

// The number of milliseconds that have elapsed since 'earlier'.
inline int64_t TimeSince(int64_t earlier) {
  return TimeMillis() - earlier;
}

// The number of milliseconds that will elapse between now and 'later'.
inline int64_t TimeUntil(int64_t later) {
  return later - TimeMillis();
}

class TimestampWrapAroundHandler {
 public:
  TimestampWrapAroundHandler();

  int64_t Unwrap(uint32_t ts);

 private:
  uint32_t last_ts_;
  int64_t num_wrap_;
};

// Convert from tm, which is relative to 1900-01-01 00:00 to number of
// seconds from 1970-01-01 00:00 ("epoch"). Don't return time_t since that
// is still 32 bits on many systems.
int64_t TmToSeconds(const tm& tm);

// Return the number of microseconds since January 1, 1970, UTC.
// Useful mainly when producing logs to be correlated with other
// devices, and when the devices in question all have properly
// synchronized clocks.
//
// Note that this function obeys the system's idea about what the time
// is. It is not guaranteed to be monotonic; it will jump in case the
// system time is changed, e.g., by some other process calling
// settimeofday. Always use rtc::TimeMicros(), not this function, for
// measuring time intervals and timeouts.
int64_t TimeUTCMicros();

// Return the number of milliseconds since January 1, 1970, UTC.
// See above.
int64_t TimeUTCMillis();

// Interval of time from the range [min, max] inclusive.
class IntervalRange {
 public:
  IntervalRange() : min_(0), max_(0) {}
  IntervalRange(int min, int max) : min_(min), max_(max) {
    RTC_DCHECK_LE(min, max);
  }

  int min() const { return min_; }
  int max() const { return max_; }

  std::string ToString() const {
    rtc::StringBuilder ss;
    ss << "[" << min_ << "," << max_ << "]";
    return ss.Release();
  }

  bool operator==(const IntervalRange& o) const {
    return min_ == o.min_ && max_ == o.max_;
  }

  bool operator!=(const IntervalRange& o) const { return !operator==(o); }

 private:
  int min_;
  int max_;
};

}  // namespace rtc

#endif  // RTC_BASE_TIMEUTILS_H_
