// Copyright (c) 2019 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

#include "base/time/time.h"

#include <CoreFoundation/CFDate.h>
#include <CoreFoundation/CFCalendar.h>
#include <CoreFoundation/CFTimeZone.h>

#include "base/mac/scoped_cftyperef.h"

#if __LP64__
#error Use posix implementation on 64-bit platforms.
#endif  // __LP64__

namespace base {

// Note: These implementations of Time::FromExploded() and Time::Explode() are
// only used on iOS now. Since Mac is now always 64-bit, we can use the POSIX
// versions of these functions as time_t is not capped at year 2038 on 64-bit
// builds. The POSIX functions are preferred since they don't suffer from some
// performance problems that are present in these implementations.
// See crbug.com/781601, crbug.com/985061 for more details.

// static
bool Time::FromExploded(bool is_local, const Exploded& exploded, Time* time) {
  base::ScopedCFTypeRef<CFTimeZoneRef> time_zone(
      is_local
          ? CFTimeZoneCopySystem()
          : CFTimeZoneCreateWithTimeIntervalFromGMT(kCFAllocatorDefault, 0));
  base::ScopedCFTypeRef<CFCalendarRef> gregorian(CFCalendarCreateWithIdentifier(
      kCFAllocatorDefault, kCFGregorianCalendar));
  CFCalendarSetTimeZone(gregorian, time_zone);
  CFAbsoluteTime absolute_time;
  // 'S' is not defined in componentDesc in Apple documentation, but can be
  // found at http://www.opensource.apple.com/source/CF/CF-855.17/CFCalendar.c
  CFCalendarComposeAbsoluteTime(
      gregorian, &absolute_time, "yMdHmsS", exploded.year, exploded.month,
      exploded.day_of_month, exploded.hour, exploded.minute, exploded.second,
      exploded.millisecond);
  CFAbsoluteTime seconds = absolute_time + kCFAbsoluteTimeIntervalSince1970;

  // CFAbsolutTime is typedef of double. Convert seconds to
  // microseconds and then cast to int64. If
  // it cannot be suited to int64, then fail to avoid overflows.
  double microseconds =
      (seconds * kMicrosecondsPerSecond) + kTimeTToMicrosecondsOffset;
  if (microseconds > std::numeric_limits<int64_t>::max() ||
      microseconds < std::numeric_limits<int64_t>::min()) {
    *time = Time(0);
    return false;
  }

  base::Time converted_time = Time(static_cast<int64_t>(microseconds));

  // If |exploded.day_of_month| is set to 31
  // on a 28-30 day month, it will return the first day of the next month.
  // Thus round-trip the time and compare the initial |exploded| with
  // |utc_to_exploded| time.
  base::Time::Exploded to_exploded;
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

void Time::Explode(bool is_local, Exploded* exploded) const {
  // Avoid rounding issues, by only putting the integral number of seconds
  // (rounded towards -infinity) into a |CFAbsoluteTime| (which is a |double|).
  int64_t microsecond = us_ % kMicrosecondsPerSecond;
  if (microsecond < 0)
    microsecond += kMicrosecondsPerSecond;
  CFAbsoluteTime seconds = ((us_ - microsecond - kTimeTToMicrosecondsOffset) /
                            kMicrosecondsPerSecond) -
                           kCFAbsoluteTimeIntervalSince1970;

  base::ScopedCFTypeRef<CFTimeZoneRef> time_zone(
      is_local
          ? CFTimeZoneCopySystem()
          : CFTimeZoneCreateWithTimeIntervalFromGMT(kCFAllocatorDefault, 0));
  base::ScopedCFTypeRef<CFCalendarRef> gregorian(CFCalendarCreateWithIdentifier(
      kCFAllocatorDefault, kCFGregorianCalendar));
  CFCalendarSetTimeZone(gregorian, time_zone);
  int second, day_of_week;
  // 'E' sets the day of week, but is not defined in componentDesc in Apple
  // documentation. It can be found in open source code here:
  // http://www.opensource.apple.com/source/CF/CF-855.17/CFCalendar.c
  CFCalendarDecomposeAbsoluteTime(gregorian, seconds, "yMdHmsE",
                                  &exploded->year, &exploded->month,
                                  &exploded->day_of_month, &exploded->hour,
                                  &exploded->minute, &second, &day_of_week);
  // Make sure seconds are rounded down towards -infinity.
  exploded->second = floor(second);
  // |Exploded|'s convention for day of week is 0 = Sunday, i.e. different
  // from CF's 1 = Sunday.
  exploded->day_of_week = (day_of_week - 1) % 7;
  // Calculate milliseconds ourselves, since we rounded the |seconds|, making
  // sure to round towards -infinity.
  exploded->millisecond =
      (microsecond >= 0) ? microsecond / kMicrosecondsPerMillisecond
                         : ((microsecond + 1) / kMicrosecondsPerMillisecond - 1);
}

}  // namespace base
