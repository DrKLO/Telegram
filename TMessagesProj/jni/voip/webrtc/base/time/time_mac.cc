// Copyright (c) 2012 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

#include "base/time/time.h"

#include <CoreFoundation/CFDate.h>
#include <mach/mach.h>
#include <mach/mach_time.h>
#include <stddef.h>
#include <stdint.h>
#include <sys/sysctl.h>
#include <sys/time.h>
#include <sys/types.h>
#include <time.h>

#include "base/logging.h"
#include "base/mac/mach_logging.h"
#include "base/mac/scoped_cftyperef.h"
#include "base/mac/scoped_mach_port.h"
#include "base/numerics/safe_conversions.h"
#include "base/stl_util.h"
#include "base/time/time_override.h"
#include "build/build_config.h"

#if defined(OS_IOS)
#include <time.h>
#include "base/ios/ios_util.h"
#endif

namespace {

#if defined(OS_MACOSX) && !defined(OS_IOS)
int64_t MachAbsoluteTimeToTicks(uint64_t mach_absolute_time) {
  static mach_timebase_info_data_t timebase_info;
  if (timebase_info.denom == 0) {
    // Zero-initialization of statics guarantees that denom will be 0 before
    // calling mach_timebase_info.  mach_timebase_info will never set denom to
    // 0 as that would be invalid, so the zero-check can be used to determine
    // whether mach_timebase_info has already been called.  This is
    // recommended by Apple's QA1398.
    kern_return_t kr = mach_timebase_info(&timebase_info);
    MACH_DCHECK(kr == KERN_SUCCESS, kr) << "mach_timebase_info";
  }

  // timebase_info converts absolute time tick units into nanoseconds.  Convert
  // to microseconds up front to stave off overflows.
  base::CheckedNumeric<uint64_t> result(mach_absolute_time /
                                        base::Time::kNanosecondsPerMicrosecond);
  result *= timebase_info.numer;
  result /= timebase_info.denom;

  // Don't bother with the rollover handling that the Windows version does.
  // With numer and denom = 1 (the expected case), the 64-bit absolute time
  // reported in nanoseconds is enough to last nearly 585 years.
  return base::checked_cast<int64_t>(result.ValueOrDie());
}
#endif  // defined(OS_MACOSX) && !defined(OS_IOS)

// Returns monotonically growing number of ticks in microseconds since some
// unspecified starting point.
int64_t ComputeCurrentTicks() {
#if defined(OS_IOS)
  // iOS 10 supports clock_gettime(CLOCK_MONOTONIC, ...), which is
  // around 15 times faster than sysctl() call. Use it if possible;
  // otherwise, fall back to sysctl().
  if (__builtin_available(iOS 10, *)) {
    struct timespec tp;
    if (clock_gettime(CLOCK_MONOTONIC, &tp) == 0) {
      return (int64_t)tp.tv_sec * 1000000 + tp.tv_nsec / 1000;
    }
  }

  // On iOS mach_absolute_time stops while the device is sleeping. Instead use
  // now - KERN_BOOTTIME to get a time difference that is not impacted by clock
  // changes. KERN_BOOTTIME will be updated by the system whenever the system
  // clock change.
  struct timeval boottime;
  int mib[2] = {CTL_KERN, KERN_BOOTTIME};
  size_t size = sizeof(boottime);
  int kr = sysctl(mib, base::size(mib), &boottime, &size, nullptr, 0);
  DCHECK_EQ(KERN_SUCCESS, kr);
  base::TimeDelta time_difference =
      base::subtle::TimeNowIgnoringOverride() -
      (base::Time::FromTimeT(boottime.tv_sec) +
       base::TimeDelta::FromMicroseconds(boottime.tv_usec));
  return time_difference.InMicroseconds();
#else
  // mach_absolute_time is it when it comes to ticks on the Mac.  Other calls
  // with less precision (such as TickCount) just call through to
  // mach_absolute_time.
  return MachAbsoluteTimeToTicks(mach_absolute_time());
#endif  // defined(OS_IOS)
}

int64_t ComputeThreadTicks() {
#if defined(OS_IOS)
  NOTREACHED();
  return 0;
#else
  // The pthreads library keeps a cached reference to the thread port, which
  // does not have to be released like mach_thread_self() does.
  mach_port_t thread_port = pthread_mach_thread_np(pthread_self());
  if (thread_port == MACH_PORT_NULL) {
    DLOG(ERROR) << "Failed to get pthread_mach_thread_np()";
    return 0;
  }

  mach_msg_type_number_t thread_info_count = THREAD_BASIC_INFO_COUNT;
  thread_basic_info_data_t thread_info_data;

  kern_return_t kr = thread_info(
      thread_port,
      THREAD_BASIC_INFO,
      reinterpret_cast<thread_info_t>(&thread_info_data),
      &thread_info_count);
  MACH_DCHECK(kr == KERN_SUCCESS, kr) << "thread_info";

  base::CheckedNumeric<int64_t> absolute_micros(
      thread_info_data.user_time.seconds +
      thread_info_data.system_time.seconds);
  absolute_micros *= base::Time::kMicrosecondsPerSecond;
  absolute_micros += (thread_info_data.user_time.microseconds +
                      thread_info_data.system_time.microseconds);
  return absolute_micros.ValueOrDie();
#endif  // defined(OS_IOS)
}

}  // namespace

namespace base {

// The Time routines in this file use Mach and CoreFoundation APIs, since the
// POSIX definition of time_t in Mac OS X wraps around after 2038--and
// there are already cookie expiration dates, etc., past that time out in
// the field.  Using CFDate prevents that problem, and using mach_absolute_time
// for TimeTicks gives us nice high-resolution interval timing.

// Time -----------------------------------------------------------------------

namespace subtle {
Time TimeNowIgnoringOverride() {
  return Time::FromCFAbsoluteTime(CFAbsoluteTimeGetCurrent());
}

Time TimeNowFromSystemTimeIgnoringOverride() {
  // Just use TimeNowIgnoringOverride() because it returns the system time.
  return TimeNowIgnoringOverride();
}
}  // namespace subtle

// static
Time Time::FromCFAbsoluteTime(CFAbsoluteTime t) {
  static_assert(std::numeric_limits<CFAbsoluteTime>::has_infinity,
                "CFAbsoluteTime must have an infinity value");
  if (t == 0)
    return Time();  // Consider 0 as a null Time.
  if (t == std::numeric_limits<CFAbsoluteTime>::infinity())
    return Max();
  return Time(static_cast<int64_t>((t + kCFAbsoluteTimeIntervalSince1970) *
                                   kMicrosecondsPerSecond) +
              kTimeTToMicrosecondsOffset);
}

CFAbsoluteTime Time::ToCFAbsoluteTime() const {
  static_assert(std::numeric_limits<CFAbsoluteTime>::has_infinity,
                "CFAbsoluteTime must have an infinity value");
  if (is_null())
    return 0;  // Consider 0 as a null Time.
  if (is_max())
    return std::numeric_limits<CFAbsoluteTime>::infinity();
  return (static_cast<CFAbsoluteTime>(us_ - kTimeTToMicrosecondsOffset) /
          kMicrosecondsPerSecond) -
         kCFAbsoluteTimeIntervalSince1970;
}

// TimeTicks ------------------------------------------------------------------

namespace subtle {
TimeTicks TimeTicksNowIgnoringOverride() {
  return TimeTicks() + TimeDelta::FromMicroseconds(ComputeCurrentTicks());
}
}  // namespace subtle

// static
bool TimeTicks::IsHighResolution() {
  return true;
}

// static
bool TimeTicks::IsConsistentAcrossProcesses() {
  return true;
}

#if defined(OS_MACOSX) && !defined(OS_IOS)
// static
TimeTicks TimeTicks::FromMachAbsoluteTime(uint64_t mach_absolute_time) {
  return TimeTicks(MachAbsoluteTimeToTicks(mach_absolute_time));
}
#endif  // defined(OS_MACOSX) && !defined(OS_IOS)

// static
TimeTicks::Clock TimeTicks::GetClock() {
#if defined(OS_IOS)
  return Clock::IOS_CF_ABSOLUTE_TIME_MINUS_KERN_BOOTTIME;
#else
  return Clock::MAC_MACH_ABSOLUTE_TIME;
#endif  // defined(OS_IOS)
}

// ThreadTicks ----------------------------------------------------------------

namespace subtle {
ThreadTicks ThreadTicksNowIgnoringOverride() {
  return ThreadTicks() + TimeDelta::FromMicroseconds(ComputeThreadTicks());
}
}  // namespace subtle

}  // namespace base
