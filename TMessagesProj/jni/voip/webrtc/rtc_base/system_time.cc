/*
 *  Copyright 2021 The WebRTC Project Authors. All rights reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

// If WEBRTC_EXCLUDE_SYSTEM_TIME is set, an implementation of
// rtc::SystemTimeNanos() must be provided externally.
#ifndef WEBRTC_EXCLUDE_SYSTEM_TIME

#include <stdint.h>

#include <limits>

#if defined(WEBRTC_POSIX)
#include <sys/time.h>
#if defined(WEBRTC_MAC)
#include <mach/mach_time.h>
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
#include "rtc_base/numerics/safe_conversions.h"
#include "rtc_base/system_time.h"
#include "rtc_base/time_utils.h"

namespace rtc {

int64_t SystemTimeNanos() {
  int64_t ticks;
#if defined(WEBRTC_MAC)
  static mach_timebase_info_data_t timebase;
  if (timebase.denom == 0) {
    // Get the timebase if this is the first time we run.
    // Recommended by Apple's QA1398.
    if (mach_timebase_info(&timebase) != KERN_SUCCESS) {
      RTC_DCHECK_NOTREACHED();
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
#elif defined(WINUWP)
  ticks = WinUwpSystemTimeNanos();
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

}  // namespace rtc
#endif  // WEBRTC_EXCLUDE_SYSTEM_TIME
