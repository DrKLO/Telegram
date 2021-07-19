// Copyright (c) 2012 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

#include "base/time/time.h"

#include <stdint.h>
#include <sys/time.h>
#include <time.h>
#if defined(OS_ANDROID) && !defined(__LP64__)
#include <time64.h>
#endif
#include <unistd.h>

#include <limits>

#include "base/numerics/safe_math.h"
#include "base/synchronization/lock.h"
#include "build/build_config.h"

#if defined(OS_ANDROID)
#include "base/os_compat_android.h"
#elif defined(OS_NACL)
#include "base/os_compat_nacl.h"
#endif

#if defined(OS_MACOSX) || defined(OS_IOS)
static_assert(sizeof(time_t) >= 8, "Y2038 problem!");
#endif

namespace {

// This prevents a crash on traversing the environment global and looking up
// the 'TZ' variable in libc. See: crbug.com/390567.
base::Lock* GetSysTimeToTimeStructLock() {
  static auto* lock = new base::Lock();
  return lock;
}

// Define a system-specific SysTime that wraps either to a time_t or
// a time64_t depending on the host system, and associated convertion.
// See crbug.com/162007
#if defined(OS_ANDROID) && !defined(__LP64__)
typedef time64_t SysTime;

SysTime SysTimeFromTimeStruct(struct tm* timestruct, bool is_local) {
  base::AutoLock locked(*GetSysTimeToTimeStructLock());
  if (is_local)
    return mktime64(timestruct);
  else
    return timegm64(timestruct);
}

void SysTimeToTimeStruct(SysTime t, struct tm* timestruct, bool is_local) {
  base::AutoLock locked(*GetSysTimeToTimeStructLock());
  if (is_local)
    localtime64_r(&t, timestruct);
  else
    gmtime64_r(&t, timestruct);
}
#elif defined(OS_AIX)
// The function timegm is not available on AIX.
time_t aix_timegm(struct tm* tm) {
  time_t ret;
  char* tz;

  tz = getenv("TZ");
  if (tz) {
    tz = strdup(tz);
  }
  setenv("TZ", "GMT0", 1);
  tzset();
  ret = mktime(tm);
  if (tz) {
    setenv("TZ", tz, 1);
    free(tz);
  } else {
    unsetenv("TZ");
  }
  tzset();
  return ret;
}

typedef time_t SysTime;

SysTime SysTimeFromTimeStruct(struct tm* timestruct, bool is_local) {
  base::AutoLock locked(*GetSysTimeToTimeStructLock());
  if (is_local)
    return mktime(timestruct);
  else
    return aix_timegm(timestruct);
}

void SysTimeToTimeStruct(SysTime t, struct tm* timestruct, bool is_local) {
  base::AutoLock locked(*GetSysTimeToTimeStructLock());
  if (is_local)
    localtime_r(&t, timestruct);
  else
    gmtime_r(&t, timestruct);
}

#else
typedef time_t SysTime;

SysTime SysTimeFromTimeStruct(struct tm* timestruct, bool is_local) {
  base::AutoLock locked(*GetSysTimeToTimeStructLock());
  return is_local ? mktime(timestruct) : timegm(timestruct);
}

void SysTimeToTimeStruct(SysTime t, struct tm* timestruct, bool is_local) {
  base::AutoLock locked(*GetSysTimeToTimeStructLock());
  if (is_local)
    localtime_r(&t, timestruct);
  else
    gmtime_r(&t, timestruct);
}
#endif  // defined(OS_ANDROID) && !defined(__LP64__)

}  // namespace

namespace base {

void Time::Explode(bool is_local, Exploded* exploded) const {
  // The following values are all rounded towards -infinity.
  int64_t milliseconds = ToRoundedDownMillisecondsSinceUnixEpoch();
  SysTime seconds;       // Seconds since epoch.
  int millisecond;       // Exploded millisecond value (0-999).

  // If the microseconds were negative, the rounded down milliseconds will also
  // be negative. For example, -1 us becomes -1 ms.
  if (milliseconds >= 0) {
    // Rounding towards -infinity <=> rounding towards 0, in this case.
    seconds = milliseconds / kMillisecondsPerSecond;
    millisecond = milliseconds % kMillisecondsPerSecond;
  } else {
    // Round these *down* (towards -infinity).
    seconds = (milliseconds + 1) / kMillisecondsPerSecond - 1;
    // Make this nonnegative (and between 0 and 999 inclusive).
    millisecond = milliseconds % kMillisecondsPerSecond;
    if (millisecond < 0)
      millisecond += kMillisecondsPerSecond;
  }

  struct tm timestruct;
  SysTimeToTimeStruct(seconds, &timestruct, is_local);

  exploded->year = timestruct.tm_year + 1900;
  exploded->month = timestruct.tm_mon + 1;
  exploded->day_of_week = timestruct.tm_wday;
  exploded->day_of_month = timestruct.tm_mday;
  exploded->hour = timestruct.tm_hour;
  exploded->minute = timestruct.tm_min;
  exploded->second = timestruct.tm_sec;
  exploded->millisecond = millisecond;
}

// static
bool Time::FromExploded(bool is_local, const Exploded& exploded, Time* time) {
  CheckedNumeric<int> month = exploded.month;
  month--;
  CheckedNumeric<int> year = exploded.year;
  year -= 1900;
  if (!month.IsValid() || !year.IsValid()) {
    *time = Time(0);
    return false;
  }

  struct tm timestruct;
  timestruct.tm_sec = exploded.second;
  timestruct.tm_min = exploded.minute;
  timestruct.tm_hour = exploded.hour;
  timestruct.tm_mday = exploded.day_of_month;
  timestruct.tm_mon = month.ValueOrDie();
  timestruct.tm_year = year.ValueOrDie();
  timestruct.tm_wday = exploded.day_of_week;  // mktime/timegm ignore this
  timestruct.tm_yday = 0;                     // mktime/timegm ignore this
  timestruct.tm_isdst = -1;                   // attempt to figure it out
#if !defined(OS_NACL) && !defined(OS_SOLARIS) && !defined(OS_AIX)
  timestruct.tm_gmtoff = 0;   // not a POSIX field, so mktime/timegm ignore
  timestruct.tm_zone = nullptr;  // not a POSIX field, so mktime/timegm ignore
#endif

  SysTime seconds;

  // Certain exploded dates do not really exist due to daylight saving times,
  // and this causes mktime() to return implementation-defined values when
  // tm_isdst is set to -1. On Android, the function will return -1, while the
  // C libraries of other platforms typically return a liberally-chosen value.
  // Handling this requires the special code below.

  // SysTimeFromTimeStruct() modifies the input structure, save current value.
  struct tm timestruct0 = timestruct;

  seconds = SysTimeFromTimeStruct(&timestruct, is_local);
  if (seconds == -1) {
    // Get the time values with tm_isdst == 0 and 1, then select the closest one
    // to UTC 00:00:00 that isn't -1.
    timestruct = timestruct0;
    timestruct.tm_isdst = 0;
    int64_t seconds_isdst0 = SysTimeFromTimeStruct(&timestruct, is_local);

    timestruct = timestruct0;
    timestruct.tm_isdst = 1;
    int64_t seconds_isdst1 = SysTimeFromTimeStruct(&timestruct, is_local);

    // seconds_isdst0 or seconds_isdst1 can be -1 for some timezones.
    // E.g. "CLST" (Chile Summer Time) returns -1 for 'tm_isdt == 1'.
    if (seconds_isdst0 < 0)
      seconds = seconds_isdst1;
    else if (seconds_isdst1 < 0)
      seconds = seconds_isdst0;
    else
      seconds = std::min(seconds_isdst0, seconds_isdst1);
  }

  // Handle overflow.  Clamping the range to what mktime and timegm might
  // return is the best that can be done here.  It's not ideal, but it's better
  // than failing here or ignoring the overflow case and treating each time
  // overflow as one second prior to the epoch.
  int64_t milliseconds = 0;
  if (seconds == -1 && (exploded.year < 1969 || exploded.year > 1970)) {
    // If exploded.year is 1969 or 1970, take -1 as correct, with the
    // time indicating 1 second prior to the epoch.  (1970 is allowed to handle
    // time zone and DST offsets.)  Otherwise, return the most future or past
    // time representable.  Assumes the time_t epoch is 1970-01-01 00:00:00 UTC.
    //
    // The minimum and maximum representible times that mktime and timegm could
    // return are used here instead of values outside that range to allow for
    // proper round-tripping between exploded and counter-type time
    // representations in the presence of possible truncation to time_t by
    // division and use with other functions that accept time_t.
    //
    // When representing the most distant time in the future, add in an extra
    // 999ms to avoid the time being less than any other possible value that
    // this function can return.

    // On Android, SysTime is int64_t, special care must be taken to avoid
    // overflows.
    const int64_t min_seconds = (sizeof(SysTime) < sizeof(int64_t))
                                    ? std::numeric_limits<SysTime>::min()
                                    : std::numeric_limits<int32_t>::min();
    const int64_t max_seconds = (sizeof(SysTime) < sizeof(int64_t))
                                    ? std::numeric_limits<SysTime>::max()
                                    : std::numeric_limits<int32_t>::max();
    if (exploded.year < 1969) {
      milliseconds = min_seconds * kMillisecondsPerSecond;
    } else {
      milliseconds = max_seconds * kMillisecondsPerSecond;
      milliseconds += (kMillisecondsPerSecond - 1);
    }
  } else {
    CheckedNumeric<int64_t> checked_millis = seconds;
    checked_millis *= kMillisecondsPerSecond;
    checked_millis += exploded.millisecond;
    if (!checked_millis.IsValid()) {
      *time = Time(0);
      return false;
    }
    milliseconds = checked_millis.ValueOrDie();
  }

  Time converted_time;
  if (!FromMillisecondsSinceUnixEpoch(milliseconds, &converted_time)) {
    *time = base::Time(0);
    return false;
  }

  // If |exploded.day_of_month| is set to 31 on a 28-30 day month, it will
  // return the first day of the next month. Thus round-trip the time and
  // compare the initial |exploded| with |utc_to_exploded| time.
  Time::Exploded to_exploded;
  if (!is_local)
    converted_time.UTCExplode(&to_exploded);
  else
    converted_time.LocalExplode(&to_exploded);

  if (ExplodedMostlyEquals(to_exploded, exploded)) {
    *time = converted_time;
    return true;
  }

  *time = Time(0);
  return false;
}

}  // namespace base
