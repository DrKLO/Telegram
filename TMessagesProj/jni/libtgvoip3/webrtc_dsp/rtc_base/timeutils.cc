/*
 *  Copyright 2004 The WebRTC Project Authors. All rights reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include <stdint.h>

#if defined(WEBRTC_POSIX)
#include <sys/time.h>
#if defined(WEBRTC_MAC)
#include <mach/mach_time.h>
#include "rtc_base/numerics/safe_conversions.h"
#endif
#endif

#if defined(WEBRTC_WIN)
// clang-format off
// clang formatting would put <windows.h> last,
// which leads to compilation failure.
#include <windows.h>
#include <mmsystem.h>
#include <sys/timeb.h>
// clang-format on
#endif

#include "rtc_base/checks.h"
#include "rtc_base/timeutils.h"

namespace rtc {

ClockInterface* g_clock = nullptr;

ClockInterface* SetClockForTesting(ClockInterface* clock) {
  ClockInterface* prev = g_clock;
  g_clock = clock;
  return prev;
}

ClockInterface* GetClockForTesting() {
  return g_clock;
}

int64_t SystemTimeNanos() {
  int64_t ticks;
#if defined(WEBRTC_MAC)
  static mach_timebase_info_data_t timebase;
  if (timebase.denom == 0) {
    // Get the timebase if this is the first time we run.
    // Recommended by Apple's QA1398.
    if (mach_timebase_info(&timebase) != KERN_SUCCESS) {
      RTC_NOTREACHED();
    }
  }
  // Use timebase to convert absolute time tick units into nanoseconds.
  const auto mul = [](uint64_t a, uint32_t b) -> int64_t {
    RTC_DCHECK_NE(b, 0);
    RTC_DCHECK_LE(a, std::numeric_limits<int64_t>::max() / b)
        << "The multiplication " << a << " * " << b << " overflows";
    return rtc::dchecked_cast<int64_t>(a * b);
  };
  ticks = mul(mach_absolute_time(), timebase.numer) / timebase.denom;
#elif defined(WEBRTC_POSIX)
  struct timespec ts;
  // TODO(deadbeef): Do we need to handle the case when CLOCK_MONOTONIC is not
  // supported?
  clock_gettime(CLOCK_MONOTONIC, &ts);
  ticks = kNumNanosecsPerSec * static_cast<int64_t>(ts.tv_sec) +
          static_cast<int64_t>(ts.tv_nsec);
#elif defined(WEBRTC_UWP)
  static volatile ULONGLONG last_timegettime = 0;
  static volatile int64_t num_wrap_timegettime = 0;
  volatile ULONGLONG* last_timegettime_ptr = &last_timegettime;
  ULONGLONG now = GetTickCount64();
  // Atomically update the last gotten time
  ULONGLONG old = InterlockedExchange(last_timegettime_ptr, now);
  if (now < old) {
	  // If now is earlier than old, there may have been a race between threads.
	  // 0x0fffffff ~3.1 days, the code will not take that long to execute
	  // so it must have been a wrap around.
	  if (old > 0xf0000000 && now < 0x0fffffff) {
		  num_wrap_timegettime++;
	  }
  }
  ticks = now + (num_wrap_timegettime << 32);
  // TODO(deadbeef): Calculate with nanosecond precision. Otherwise, we're
  // just wasting a multiply and divide when doing Time() on Windows.
  ticks = ticks * kNumNanosecsPerMillisec;
#elif defined(WEBRTC_WIN)
  static volatile LONG last_timegettime = 0;
  static volatile int64_t num_wrap_timegettime = 0;
  volatile LONG* last_timegettime_ptr = &last_timegettime;
  DWORD now = timeGetTime();
  // Atomically update the last gotten time
  DWORD old = InterlockedExchange(last_timegettime_ptr, now);
  if (now < old) {
    // If now is earlier than old, there may have been a race between threads.
    // 0x0fffffff ~3.1 days, the code will not take that long to execute
    // so it must have been a wrap around.
    if (old > 0xf0000000 && now < 0x0fffffff) {
      num_wrap_timegettime++;
    }
  }
  ticks = now + (num_wrap_timegettime << 32);
  // TODO(deadbeef): Calculate with nanosecond precision. Otherwise, we're
  // just wasting a multiply and divide when doing Time() on Windows.
  ticks = ticks * kNumNanosecsPerMillisec;
#else
#error Unsupported platform.
#endif
  return ticks;
}

int64_t SystemTimeMillis() {
  return static_cast<int64_t>(SystemTimeNanos() / kNumNanosecsPerMillisec);
}

int64_t TimeNanos() {
  if (g_clock) {
    return g_clock->TimeNanos();
  }
  return SystemTimeNanos();
}

uint32_t Time32() {
  return static_cast<uint32_t>(TimeNanos() / kNumNanosecsPerMillisec);
}

int64_t TimeMillis() {
  return TimeNanos() / kNumNanosecsPerMillisec;
}

int64_t TimeMicros() {
  return TimeNanos() / kNumNanosecsPerMicrosec;
}

int64_t TimeAfter(int64_t elapsed) {
  RTC_DCHECK_GE(elapsed, 0);
  return TimeMillis() + elapsed;
}

int32_t TimeDiff32(uint32_t later, uint32_t earlier) {
  return later - earlier;
}

int64_t TimeDiff(int64_t later, int64_t earlier) {
  return later - earlier;
}

TimestampWrapAroundHandler::TimestampWrapAroundHandler()
    : last_ts_(0), num_wrap_(-1) {}

int64_t TimestampWrapAroundHandler::Unwrap(uint32_t ts) {
  if (num_wrap_ == -1) {
    last_ts_ = ts;
    num_wrap_ = 0;
    return ts;
  }

  if (ts < last_ts_) {
    if (last_ts_ >= 0xf0000000 && ts < 0x0fffffff)
      ++num_wrap_;
  } else if ((ts - last_ts_) > 0xf0000000) {
    // Backwards wrap. Unwrap with last wrap count and don't update last_ts_.
    return ts + ((num_wrap_ - 1) << 32);
  }

  last_ts_ = ts;
  return ts + (num_wrap_ << 32);
}

int64_t TmToSeconds(const tm& tm) {
  static short int mdays[12] = {31, 28, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31};
  static short int cumul_mdays[12] = {0,   31,  59,  90,  120, 151,
                                      181, 212, 243, 273, 304, 334};
  int year = tm.tm_year + 1900;
  int month = tm.tm_mon;
  int day = tm.tm_mday - 1;  // Make 0-based like the rest.
  int hour = tm.tm_hour;
  int min = tm.tm_min;
  int sec = tm.tm_sec;

  bool expiry_in_leap_year =
      (year % 4 == 0 && (year % 100 != 0 || year % 400 == 0));

  if (year < 1970)
    return -1;
  if (month < 0 || month > 11)
    return -1;
  if (day < 0 || day >= mdays[month] + (expiry_in_leap_year && month == 2 - 1))
    return -1;
  if (hour < 0 || hour > 23)
    return -1;
  if (min < 0 || min > 59)
    return -1;
  if (sec < 0 || sec > 59)
    return -1;

  day += cumul_mdays[month];

  // Add number of leap days between 1970 and the expiration year, inclusive.
  day += ((year / 4 - 1970 / 4) - (year / 100 - 1970 / 100) +
          (year / 400 - 1970 / 400));

  // We will have added one day too much above if expiration is during a leap
  // year, and expiration is in January or February.
  if (expiry_in_leap_year && month <= 2 - 1)  // |month| is zero based.
    day -= 1;

  // Combine all variables into seconds from 1970-01-01 00:00 (except |month|
  // which was accumulated into |day| above).
  return (((static_cast<int64_t>(year - 1970) * 365 + day) * 24 + hour) * 60 +
          min) *
             60 +
         sec;
}

int64_t TimeUTCMicros() {
  if (g_clock) {
    return g_clock->TimeNanos() / kNumNanosecsPerMicrosec;
  }
#if defined(WEBRTC_POSIX)
  struct timeval time;
  gettimeofday(&time, nullptr);
  // Convert from second (1.0) and microsecond (1e-6).
  return (static_cast<int64_t>(time.tv_sec) * rtc::kNumMicrosecsPerSec +
          time.tv_usec);

#elif defined(WEBRTC_WIN)
  struct _timeb time;
  _ftime(&time);
  // Convert from second (1.0) and milliseconds (1e-3).
  return (static_cast<int64_t>(time.time) * rtc::kNumMicrosecsPerSec +
          static_cast<int64_t>(time.millitm) * rtc::kNumMicrosecsPerMillisec);
#endif
}

int64_t TimeUTCMillis() {
  return TimeUTCMicros() / kNumMicrosecsPerMillisec;
}

}  // namespace rtc
