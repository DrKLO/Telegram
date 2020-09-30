// Copyright (c) 2012 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

// Time represents an absolute point in coordinated universal time (UTC),
// internally represented as microseconds (s/1,000,000) since the Windows epoch
// (1601-01-01 00:00:00 UTC). System-dependent clock interface routines are
// defined in time_PLATFORM.cc. Note that values for Time may skew and jump
// around as the operating system makes adjustments to synchronize (e.g., with
// NTP servers). Thus, client code that uses the Time class must account for
// this.
//
// TimeDelta represents a duration of time, internally represented in
// microseconds.
//
// TimeTicks and ThreadTicks represent an abstract time that is most of the time
// incrementing, for use in measuring time durations. Internally, they are
// represented in microseconds. They cannot be converted to a human-readable
// time, but are guaranteed not to decrease (unlike the Time class). Note that
// TimeTicks may "stand still" (e.g., if the computer is suspended), and
// ThreadTicks will "stand still" whenever the thread has been de-scheduled by
// the operating system.
//
// All time classes are copyable, assignable, and occupy 64-bits per instance.
// As a result, prefer passing them by value:
//   void MyFunction(TimeDelta arg);
// If circumstances require, you may also pass by const reference:
//   void MyFunction(const TimeDelta& arg);  // Not preferred.
//
// Definitions of operator<< are provided to make these types work with
// DCHECK_EQ() and other log macros. For human-readable formatting, see
// "base/i18n/time_formatting.h".
//
// So many choices!  Which time class should you use?  Examples:
//
//   Time:        Interpreting the wall-clock time provided by a remote system.
//                Detecting whether cached resources have expired. Providing the
//                user with a display of the current date and time. Determining
//                the amount of time between events across re-boots of the
//                machine.
//
//   TimeTicks:   Tracking the amount of time a task runs. Executing delayed
//                tasks at the right time. Computing presentation timestamps.
//                Synchronizing audio and video using TimeTicks as a common
//                reference clock (lip-sync). Measuring network round-trip
//                latency.
//
//   ThreadTicks: Benchmarking how long the current thread has been doing actual
//                work.

#ifndef BASE_TIME_TIME_H_
#define BASE_TIME_TIME_H_

#include <stdint.h>
#include <time.h>

#include <iosfwd>
#include <limits>

#include "base/base_export.h"
#include "base/compiler_specific.h"
#include "base/logging.h"
#include "base/numerics/safe_math.h"
#include "build/build_config.h"

#if defined(OS_FUCHSIA)
#include <zircon/types.h>
#endif

#if defined(OS_MACOSX)
#include <CoreFoundation/CoreFoundation.h>
// Avoid Mac system header macro leak.
#undef TYPE_BOOL
#endif

#if defined(OS_ANDROID)
#include <jni.h>
#endif

#if defined(OS_POSIX) || defined(OS_FUCHSIA)
#include <unistd.h>
#include <sys/time.h>
#endif

#if defined(OS_WIN)
#include "base/gtest_prod_util.h"
#include "base/win/windows_types.h"
#endif

namespace ABI {
namespace Windows {
namespace Foundation {
struct DateTime;
}  // namespace Foundation
}  // namespace Windows
}  // namespace ABI

namespace base {

class PlatformThreadHandle;
class TimeDelta;

// The functions in the time_internal namespace are meant to be used only by the
// time classes and functions.  Please use the math operators defined in the
// time classes instead.
namespace time_internal {

// Add or subtract a TimeDelta from |value|. TimeDelta::Min()/Max() are treated
// as infinity and will always saturate the return value (infinity math applies
// if |value| also is at either limit of its spectrum). The int64_t argument and
// return value are in terms of a microsecond timebase.
BASE_EXPORT constexpr int64_t SaturatedAdd(int64_t value, TimeDelta delta);
BASE_EXPORT constexpr int64_t SaturatedSub(int64_t value, TimeDelta delta);

}  // namespace time_internal

// TimeDelta ------------------------------------------------------------------

class BASE_EXPORT TimeDelta {
 public:
  constexpr TimeDelta() : delta_(0) {}

  // Converts units of time to TimeDeltas.
  // These conversions treat minimum argument values as min type values or -inf,
  // and maximum ones as max type values or +inf; and their results will produce
  // an is_min() or is_max() TimeDelta. WARNING: Floating point arithmetic is
  // such that FromXXXD(t.InXXXF()) may not precisely equal |t|. Hence, floating
  // point values should not be used for storage.
  static constexpr TimeDelta FromDays(int days);
  static constexpr TimeDelta FromHours(int hours);
  static constexpr TimeDelta FromMinutes(int minutes);
  static constexpr TimeDelta FromSeconds(int64_t secs);
  static constexpr TimeDelta FromMilliseconds(int64_t ms);
  static constexpr TimeDelta FromMicroseconds(int64_t us);
  static constexpr TimeDelta FromNanoseconds(int64_t ns);
  static constexpr TimeDelta FromSecondsD(double secs);
  static constexpr TimeDelta FromMillisecondsD(double ms);
  static constexpr TimeDelta FromMicrosecondsD(double us);
  static constexpr TimeDelta FromNanosecondsD(double ns);
#if defined(OS_WIN)
  static TimeDelta FromQPCValue(LONGLONG qpc_value);
  // TODO(crbug.com/989694): Avoid base::TimeDelta factory functions
  // based on absolute time
  static TimeDelta FromFileTime(FILETIME ft);
  static TimeDelta FromWinrtDateTime(ABI::Windows::Foundation::DateTime dt);
#elif defined(OS_POSIX) || defined(OS_FUCHSIA)
  static TimeDelta FromTimeSpec(const timespec& ts);
#endif
#if defined(OS_FUCHSIA)
  static TimeDelta FromZxDuration(zx_duration_t nanos);
#endif

  // Converts an integer value representing TimeDelta to a class. This is used
  // when deserializing a |TimeDelta| structure, using a value known to be
  // compatible. It is not provided as a constructor because the integer type
  // may be unclear from the perspective of a caller.
  //
  // DEPRECATED - Do not use in new code. http://crbug.com/634507
  static constexpr TimeDelta FromInternalValue(int64_t delta) {
    return TimeDelta(delta);
  }

  // Returns the maximum time delta, which should be greater than any reasonable
  // time delta we might compare it to. Adding or subtracting the maximum time
  // delta to a time or another time delta has an undefined result.
  static constexpr TimeDelta Max();

  // Returns the minimum time delta, which should be less than than any
  // reasonable time delta we might compare it to. Adding or subtracting the
  // minimum time delta to a time or another time delta has an undefined result.
  static constexpr TimeDelta Min();

  // Returns the internal numeric value of the TimeDelta object. Please don't
  // use this and do arithmetic on it, as it is more error prone than using the
  // provided operators.
  // For serializing, use FromInternalValue to reconstitute.
  //
  // DEPRECATED - Do not use in new code. http://crbug.com/634507
  constexpr int64_t ToInternalValue() const { return delta_; }

  // Returns the magnitude (absolute value) of this TimeDelta.
  constexpr TimeDelta magnitude() const {
    // Some toolchains provide an incomplete C++11 implementation and lack an
    // int64_t overload for std::abs().  The following is a simple branchless
    // implementation:
    const int64_t mask = delta_ >> (sizeof(delta_) * 8 - 1);
    return TimeDelta((delta_ + mask) ^ mask);
  }

  // Returns true if the time delta is zero.
  constexpr bool is_zero() const { return delta_ == 0; }

  // Returns true if the time delta is the maximum/minimum time delta.
  constexpr bool is_max() const {
    return delta_ == std::numeric_limits<int64_t>::max();
  }
  constexpr bool is_min() const {
    return delta_ == std::numeric_limits<int64_t>::min();
  }

#if defined(OS_POSIX) || defined(OS_FUCHSIA)
  struct timespec ToTimeSpec() const;
#endif
#if defined(OS_FUCHSIA)
  zx_duration_t ToZxDuration() const;
#endif
#if defined(OS_WIN)
  ABI::Windows::Foundation::DateTime ToWinrtDateTime() const;
#endif

  // Returns the time delta in some unit. Minimum argument values return as
  // -inf for doubles and min type values otherwise. Maximum ones are treated as
  // +inf for doubles and max type values otherwise. Their results will produce
  // an is_min() or is_max() TimeDelta. The InXYZF versions return a floating
  // point value. The InXYZ versions return a truncated value (aka rounded
  // towards zero, std::trunc() behavior). The InXYZFloored() versions round to
  // lesser integers (std::floor() behavior). The XYZRoundedUp() versions round
  // up to greater integers (std::ceil() behavior). WARNING: Floating point
  // arithmetic is such that FromXXXD(t.InXXXF()) may not precisely equal |t|.
  // Hence, floating point values should not be used for storage.
  int InDays() const;
  int InDaysFloored() const;
  int InHours() const;
  int InMinutes() const;
  double InSecondsF() const;
  int64_t InSeconds() const;
  double InMillisecondsF() const;
  int64_t InMilliseconds() const;
  int64_t InMillisecondsRoundedUp() const;
  constexpr int64_t InMicroseconds() const { return delta_; }
  double InMicrosecondsF() const;
  int64_t InNanoseconds() const;

  // Computations with other deltas.
  constexpr TimeDelta operator+(TimeDelta other) const {
    return TimeDelta(time_internal::SaturatedAdd(delta_, other));
  }
  constexpr TimeDelta operator-(TimeDelta other) const {
    return TimeDelta(time_internal::SaturatedSub(delta_, other));
  }

  constexpr TimeDelta& operator+=(TimeDelta other) {
    return *this = (*this + other);
  }
  constexpr TimeDelta& operator-=(TimeDelta other) {
    return *this = (*this - other);
  }
  constexpr TimeDelta operator-() const {
    if (is_max()) {
      return Min();
    }
    if (is_min()) {
      return Max();
    }
    return TimeDelta(-delta_);
  }

  // Computations with numeric types.
  template <typename T>
  constexpr TimeDelta operator*(T a) const {
    CheckedNumeric<int64_t> rv(delta_);
    rv *= a;
    if (rv.IsValid())
      return TimeDelta(rv.ValueOrDie());
    // Matched sign overflows. Mismatched sign underflows.
    if ((delta_ < 0) ^ (a < 0))
      return TimeDelta(std::numeric_limits<int64_t>::min());
    return TimeDelta(std::numeric_limits<int64_t>::max());
  }
  template <typename T>
  constexpr TimeDelta operator/(T a) const {
    CheckedNumeric<int64_t> rv(delta_);
    rv /= a;
    if (rv.IsValid())
      return TimeDelta(rv.ValueOrDie());
    // Matched sign overflows. Mismatched sign underflows.
    // Special case to catch divide by zero.
    if ((delta_ < 0) ^ (a <= 0))
      return TimeDelta(std::numeric_limits<int64_t>::min());
    return TimeDelta(std::numeric_limits<int64_t>::max());
  }
  template <typename T>
  constexpr TimeDelta& operator*=(T a) {
    return *this = (*this * a);
  }
  template <typename T>
  constexpr TimeDelta& operator/=(T a) {
    return *this = (*this / a);
  }

  constexpr int64_t operator/(TimeDelta a) const {
    if (a.delta_ == 0) {
      return delta_ < 0 ? std::numeric_limits<int64_t>::min()
                        : std::numeric_limits<int64_t>::max();
    }
    if (is_max()) {
      if (a.delta_ < 0) {
        return std::numeric_limits<int64_t>::min();
      }
      return std::numeric_limits<int64_t>::max();
    }
    if (is_min()) {
      if (a.delta_ > 0) {
        return std::numeric_limits<int64_t>::min();
      }
      return std::numeric_limits<int64_t>::max();
    }
    if (a.is_max()) {
      return 0;
    }
    return delta_ / a.delta_;
  }

  constexpr TimeDelta operator%(TimeDelta a) const {
    if (a.is_min() || a.is_max()) {
      return TimeDelta(delta_);
    }
    return TimeDelta(delta_ % a.delta_);
  }
  TimeDelta& operator%=(TimeDelta other) { return *this = (*this % other); }

  // Comparison operators.
  constexpr bool operator==(TimeDelta other) const {
    return delta_ == other.delta_;
  }
  constexpr bool operator!=(TimeDelta other) const {
    return delta_ != other.delta_;
  }
  constexpr bool operator<(TimeDelta other) const {
    return delta_ < other.delta_;
  }
  constexpr bool operator<=(TimeDelta other) const {
    return delta_ <= other.delta_;
  }
  constexpr bool operator>(TimeDelta other) const {
    return delta_ > other.delta_;
  }
  constexpr bool operator>=(TimeDelta other) const {
    return delta_ >= other.delta_;
  }

 private:
  friend constexpr int64_t time_internal::SaturatedAdd(int64_t value,
                                                       TimeDelta delta);
  friend constexpr int64_t time_internal::SaturatedSub(int64_t value,
                                                       TimeDelta delta);

  // Constructs a delta given the duration in microseconds. This is private
  // to avoid confusion by callers with an integer constructor. Use
  // FromSeconds, FromMilliseconds, etc. instead.
  constexpr explicit TimeDelta(int64_t delta_us) : delta_(delta_us) {}

  // Private method to build a delta from a double.
  static constexpr TimeDelta FromDouble(double value);

  // Private method to build a delta from the product of a user-provided value
  // and a known-positive value.
  static constexpr TimeDelta FromProduct(int64_t value, int64_t positive_value);

  // Delta in microseconds.
  int64_t delta_;
};

template <typename T>
constexpr TimeDelta operator*(T a, TimeDelta td) {
  return td * a;
}

// For logging use only.
BASE_EXPORT std::ostream& operator<<(std::ostream& os, TimeDelta time_delta);

// Do not reference the time_internal::TimeBase template class directly.  Please
// use one of the time subclasses instead, and only reference the public
// TimeBase members via those classes.
namespace time_internal {

constexpr int64_t SaturatedAdd(int64_t value, TimeDelta delta) {
  // Treat Min/Max() as +/- infinity (additions involving two infinities are
  // only valid if signs match).
  if (delta.is_max()) {
    CHECK_GT(value, std::numeric_limits<int64_t>::min());
    return std::numeric_limits<int64_t>::max();
  } else if (delta.is_min()) {
    CHECK_LT(value, std::numeric_limits<int64_t>::max());
    return std::numeric_limits<int64_t>::min();
  }

  return base::ClampAdd(value, delta.delta_);
}

constexpr int64_t SaturatedSub(int64_t value, TimeDelta delta) {
  // Treat Min/Max() as +/- infinity (subtractions involving two infinities are
  // only valid if signs are opposite).
  if (delta.is_max()) {
    CHECK_LT(value, std::numeric_limits<int64_t>::max());
    return std::numeric_limits<int64_t>::min();
  } else if (delta.is_min()) {
    CHECK_GT(value, std::numeric_limits<int64_t>::min());
    return std::numeric_limits<int64_t>::max();
  }

  return base::ClampSub(value, delta.delta_);
}

// TimeBase--------------------------------------------------------------------

// Provides value storage and comparison/math operations common to all time
// classes. Each subclass provides for strong type-checking to ensure
// semantically meaningful comparison/math of time values from the same clock
// source or timeline.
template<class TimeClass>
class TimeBase {
 public:
  static constexpr int64_t kHoursPerDay = 24;
  static constexpr int64_t kSecondsPerMinute = 60;
  static constexpr int64_t kSecondsPerHour = 60 * kSecondsPerMinute;
  static constexpr int64_t kMillisecondsPerSecond = 1000;
  static constexpr int64_t kMillisecondsPerDay =
      kMillisecondsPerSecond * 60 * 60 * kHoursPerDay;
  static constexpr int64_t kMicrosecondsPerMillisecond = 1000;
  static constexpr int64_t kMicrosecondsPerSecond =
      kMicrosecondsPerMillisecond * kMillisecondsPerSecond;
  static constexpr int64_t kMicrosecondsPerMinute = kMicrosecondsPerSecond * 60;
  static constexpr int64_t kMicrosecondsPerHour = kMicrosecondsPerMinute * 60;
  static constexpr int64_t kMicrosecondsPerDay =
      kMicrosecondsPerHour * kHoursPerDay;
  static constexpr int64_t kMicrosecondsPerWeek = kMicrosecondsPerDay * 7;
  static constexpr int64_t kNanosecondsPerMicrosecond = 1000;
  static constexpr int64_t kNanosecondsPerSecond =
      kNanosecondsPerMicrosecond * kMicrosecondsPerSecond;

  // Returns true if this object has not been initialized.
  //
  // Warning: Be careful when writing code that performs math on time values,
  // since it's possible to produce a valid "zero" result that should not be
  // interpreted as a "null" value.
  constexpr bool is_null() const { return us_ == 0; }

  // Returns true if this object represents the maximum/minimum time.
  constexpr bool is_max() const {
    return us_ == std::numeric_limits<int64_t>::max();
  }
  constexpr bool is_min() const {
    return us_ == std::numeric_limits<int64_t>::min();
  }

  // Returns the maximum/minimum times, which should be greater/less than than
  // any reasonable time with which we might compare it.
  static constexpr TimeClass Max() {
    return TimeClass(std::numeric_limits<int64_t>::max());
  }

  static constexpr TimeClass Min() {
    return TimeClass(std::numeric_limits<int64_t>::min());
  }

  // For serializing only. Use FromInternalValue() to reconstitute. Please don't
  // use this and do arithmetic on it, as it is more error prone than using the
  // provided operators.
  //
  // DEPRECATED - Do not use in new code. For serializing Time values, prefer
  // Time::ToDeltaSinceWindowsEpoch().InMicroseconds(). http://crbug.com/634507
  constexpr int64_t ToInternalValue() const { return us_; }

  // The amount of time since the origin (or "zero") point. This is a syntactic
  // convenience to aid in code readability, mainly for debugging/testing use
  // cases.
  //
  // Warning: While the Time subclass has a fixed origin point, the origin for
  // the other subclasses can vary each time the application is restarted.
  constexpr TimeDelta since_origin() const {
    return TimeDelta::FromMicroseconds(us_);
  }

  constexpr TimeClass& operator=(TimeClass other) {
    us_ = other.us_;
    return *(static_cast<TimeClass*>(this));
  }

  // Compute the difference between two times.
  constexpr TimeDelta operator-(TimeClass other) const {
    return TimeDelta::FromMicroseconds(us_ - other.us_);
  }

  // Return a new time modified by some delta.
  constexpr TimeClass operator+(TimeDelta delta) const {
    return TimeClass(time_internal::SaturatedAdd(us_, delta));
  }
  constexpr TimeClass operator-(TimeDelta delta) const {
    return TimeClass(time_internal::SaturatedSub(us_, delta));
  }

  // Modify by some time delta.
  constexpr TimeClass& operator+=(TimeDelta delta) {
    return static_cast<TimeClass&>(*this = (*this + delta));
  }
  constexpr TimeClass& operator-=(TimeDelta delta) {
    return static_cast<TimeClass&>(*this = (*this - delta));
  }

  // Comparison operators
  constexpr bool operator==(TimeClass other) const { return us_ == other.us_; }
  constexpr bool operator!=(TimeClass other) const { return us_ != other.us_; }
  constexpr bool operator<(TimeClass other) const { return us_ < other.us_; }
  constexpr bool operator<=(TimeClass other) const { return us_ <= other.us_; }
  constexpr bool operator>(TimeClass other) const { return us_ > other.us_; }
  constexpr bool operator>=(TimeClass other) const { return us_ >= other.us_; }

 protected:
  constexpr explicit TimeBase(int64_t us) : us_(us) {}

  // Time value in a microsecond timebase.
  int64_t us_;
};

}  // namespace time_internal

template <class TimeClass>
inline constexpr TimeClass operator+(TimeDelta delta, TimeClass t) {
  return t + delta;
}

// Time -----------------------------------------------------------------------

// Represents a wall clock time in UTC. Values are not guaranteed to be
// monotonically non-decreasing and are subject to large amounts of skew.
// Time is stored internally as microseconds since the Windows epoch (1601).
class BASE_EXPORT Time : public time_internal::TimeBase<Time> {
 public:
  // Offset of UNIX epoch (1970-01-01 00:00:00 UTC) from Windows FILETIME epoch
  // (1601-01-01 00:00:00 UTC), in microseconds. This value is derived from the
  // following: ((1970-1601)*365+89)*24*60*60*1000*1000, where 89 is the number
  // of leap year days between 1601 and 1970: (1970-1601)/4 excluding 1700,
  // 1800, and 1900.
  static constexpr int64_t kTimeTToMicrosecondsOffset =
      INT64_C(11644473600000000);

#if defined(OS_WIN)
  // To avoid overflow in QPC to Microseconds calculations, since we multiply
  // by kMicrosecondsPerSecond, then the QPC value should not exceed
  // (2^63 - 1) / 1E6. If it exceeds that threshold, we divide then multiply.
  static constexpr int64_t kQPCOverflowThreshold = INT64_C(0x8637BD05AF7);
#endif

// kExplodedMinYear and kExplodedMaxYear define the platform-specific limits
// for values passed to FromUTCExploded() and FromLocalExploded(). Those
// functions will return false if passed values outside these limits. The limits
// are inclusive, meaning that the API should support all dates within a given
// limit year.
#if defined(OS_WIN)
  static constexpr int kExplodedMinYear = 1601;
  static constexpr int kExplodedMaxYear = 30827;
#elif defined(OS_IOS) && !__LP64__
  static constexpr int kExplodedMinYear = std::numeric_limits<int>::min();
  static constexpr int kExplodedMaxYear = std::numeric_limits<int>::max();
#elif defined(OS_MACOSX)
  static constexpr int kExplodedMinYear = 1902;
  static constexpr int kExplodedMaxYear = std::numeric_limits<int>::max();
#elif defined(OS_ANDROID)
  // Though we use 64-bit time APIs on both 32 and 64 bit Android, some OS
  // versions like KitKat (ARM but not x86 emulator) can't handle some early
  // dates (e.g. before 1170). So we set min conservatively here.
  static constexpr int kExplodedMinYear = 1902;
  static constexpr int kExplodedMaxYear = std::numeric_limits<int>::max();
#else
  static constexpr int kExplodedMinYear =
      (sizeof(time_t) == 4 ? 1902 : std::numeric_limits<int>::min());
  static constexpr int kExplodedMaxYear =
      (sizeof(time_t) == 4 ? 2037 : std::numeric_limits<int>::max());
#endif

  // Represents an exploded time that can be formatted nicely. This is kind of
  // like the Win32 SYSTEMTIME structure or the Unix "struct tm" with a few
  // additions and changes to prevent errors.
  struct BASE_EXPORT Exploded {
    int year;          // Four digit year "2007"
    int month;         // 1-based month (values 1 = January, etc.)
    int day_of_week;   // 0-based day of week (0 = Sunday, etc.)
    int day_of_month;  // 1-based day of month (1-31)
    int hour;          // Hour within the current day (0-23)
    int minute;        // Minute within the current hour (0-59)
    int second;        // Second within the current minute (0-59 plus leap
                       //   seconds which may take it up to 60).
    int millisecond;   // Milliseconds within the current second (0-999)

    // A cursory test for whether the data members are within their
    // respective ranges. A 'true' return value does not guarantee the
    // Exploded value can be successfully converted to a Time value.
    bool HasValidValues() const;
  };

  // Contains the NULL time. Use Time::Now() to get the current time.
  constexpr Time() : TimeBase(0) {}

  // Returns the time for epoch in Unix-like system (Jan 1, 1970).
  static Time UnixEpoch();

  // Returns the current time. Watch out, the system might adjust its clock
  // in which case time will actually go backwards. We don't guarantee that
  // times are increasing, or that two calls to Now() won't be the same.
  static Time Now();

  // Returns the current time. Same as Now() except that this function always
  // uses system time so that there are no discrepancies between the returned
  // time and system time even on virtual environments including our test bot.
  // For timing sensitive unittests, this function should be used.
  static Time NowFromSystemTime();

  // Converts to/from TimeDeltas relative to the Windows epoch (1601-01-01
  // 00:00:00 UTC). Prefer these methods for opaque serialization and
  // deserialization of time values, e.g.
  //
  //   // Serialization:
  //   base::Time last_updated = ...;
  //   SaveToDatabase(last_updated.ToDeltaSinceWindowsEpoch().InMicroseconds());
  //
  //   // Deserialization:
  //   base::Time last_updated = base::Time::FromDeltaSinceWindowsEpoch(
  //       base::TimeDelta::FromMicroseconds(LoadFromDatabase()));
  static Time FromDeltaSinceWindowsEpoch(TimeDelta delta);
  TimeDelta ToDeltaSinceWindowsEpoch() const;

  // Converts to/from time_t in UTC and a Time class.
  static Time FromTimeT(time_t tt);
  time_t ToTimeT() const;

  // Converts time to/from a double which is the number of seconds since epoch
  // (Jan 1, 1970).  Webkit uses this format to represent time.
  // Because WebKit initializes double time value to 0 to indicate "not
  // initialized", we map it to empty Time object that also means "not
  // initialized".
  static Time FromDoubleT(double dt);
  double ToDoubleT() const;

#if defined(OS_POSIX) || defined(OS_FUCHSIA)
  // Converts the timespec structure to time. MacOS X 10.8.3 (and tentatively,
  // earlier versions) will have the |ts|'s tv_nsec component zeroed out,
  // having a 1 second resolution, which agrees with
  // https://developer.apple.com/legacy/library/#technotes/tn/tn1150.html#HFSPlusDates.
  static Time FromTimeSpec(const timespec& ts);
#endif

  // Converts to/from the Javascript convention for times, a number of
  // milliseconds since the epoch:
  // https://developer.mozilla.org/en/JavaScript/Reference/Global_Objects/Date/getTime.
  //
  // Don't use ToJsTime() in new code, since it contains a subtle hack (only
  // exactly 1601-01-01 00:00 UTC is represented as 1970-01-01 00:00 UTC), and
  // that is not appropriate for general use. Try to use ToJsTimeIgnoringNull()
  // unless you have a very good reason to use ToJsTime().
  static Time FromJsTime(double ms_since_epoch);
  double ToJsTime() const;
  double ToJsTimeIgnoringNull() const;

  // Converts to/from Java convention for times, a number of milliseconds since
  // the epoch. Because the Java format has less resolution, converting to Java
  // time is a lossy operation.
  static Time FromJavaTime(int64_t ms_since_epoch);
  int64_t ToJavaTime() const;

#if defined(OS_POSIX) || defined(OS_FUCHSIA)
  static Time FromTimeVal(struct timeval t);
  struct timeval ToTimeVal() const;
#endif

#if defined(OS_FUCHSIA)
  static Time FromZxTime(zx_time_t time);
  zx_time_t ToZxTime() const;
#endif

#if defined(OS_MACOSX)
  static Time FromCFAbsoluteTime(CFAbsoluteTime t);
  CFAbsoluteTime ToCFAbsoluteTime() const;
#endif

#if defined(OS_WIN)
  static Time FromFileTime(FILETIME ft);
  FILETIME ToFileTime() const;

  // The minimum time of a low resolution timer.  This is basically a windows
  // constant of ~15.6ms.  While it does vary on some older OS versions, we'll
  // treat it as static across all windows versions.
  static const int kMinLowResolutionThresholdMs = 16;

  // Enable or disable Windows high resolution timer.
  static void EnableHighResolutionTimer(bool enable);

  // Read the minimum timer interval from the feature list. This should be
  // called once after the feature list is initialized. This is needed for
  // an experiment - see https://crbug.com/927165
  static void ReadMinTimerIntervalLowResMs();

  // Activates or deactivates the high resolution timer based on the |activate|
  // flag.  If the HighResolutionTimer is not Enabled (see
  // EnableHighResolutionTimer), this function will return false.  Otherwise
  // returns true.  Each successful activate call must be paired with a
  // subsequent deactivate call.
  // All callers to activate the high resolution timer must eventually call
  // this function to deactivate the high resolution timer.
  static bool ActivateHighResolutionTimer(bool activate);

  // Returns true if the high resolution timer is both enabled and activated.
  // This is provided for testing only, and is not tracked in a thread-safe
  // way.
  static bool IsHighResolutionTimerInUse();

  // The following two functions are used to report the fraction of elapsed time
  // that the high resolution timer is activated.
  // ResetHighResolutionTimerUsage() resets the cumulative usage and starts the
  // measurement interval and GetHighResolutionTimerUsage() returns the
  // percentage of time since the reset that the high resolution timer was
  // activated.
  // ResetHighResolutionTimerUsage() must be called at least once before calling
  // GetHighResolutionTimerUsage(); otherwise the usage result would be
  // undefined.
  static void ResetHighResolutionTimerUsage();
  static double GetHighResolutionTimerUsage();
#endif  // defined(OS_WIN)

  // Converts an exploded structure representing either the local time or UTC
  // into a Time class. Returns false on a failure when, for example, a day of
  // month is set to 31 on a 28-30 day month. Returns Time(0) on overflow.
  static bool FromUTCExploded(const Exploded& exploded,
                              Time* time) WARN_UNUSED_RESULT {
    return FromExploded(false, exploded, time);
  }
  static bool FromLocalExploded(const Exploded& exploded,
                                Time* time) WARN_UNUSED_RESULT {
    return FromExploded(true, exploded, time);
  }

  // Converts a string representation of time to a Time object.
  // An example of a time string which is converted is as below:-
  // "Tue, 15 Nov 1994 12:45:26 GMT". If the timezone is not specified
  // in the input string, FromString assumes local time and FromUTCString
  // assumes UTC. A timezone that cannot be parsed (e.g. "UTC" which is not
  // specified in RFC822) is treated as if the timezone is not specified.
  //
  // WARNING: the underlying converter is very permissive. For example: it is
  // not checked whether a given day of the week matches the date; Feb 29
  // silently becomes Mar 1 in non-leap years; under certain conditions, whole
  // English sentences may be parsed successfully and yield unexpected results.
  //
  // TODO(iyengar) Move the FromString/FromTimeT/ToTimeT/FromFileTime to
  // a new time converter class.
  static bool FromString(const char* time_string,
                         Time* parsed_time) WARN_UNUSED_RESULT {
    return FromStringInternal(time_string, true, parsed_time);
  }
  static bool FromUTCString(const char* time_string,
                            Time* parsed_time) WARN_UNUSED_RESULT {
    return FromStringInternal(time_string, false, parsed_time);
  }

  // Fills the given exploded structure with either the local time or UTC from
  // this time structure (containing UTC).
  void UTCExplode(Exploded* exploded) const {
    return Explode(false, exploded);
  }
  void LocalExplode(Exploded* exploded) const {
    return Explode(true, exploded);
  }

  // The following two functions round down the time to the nearest day in
  // either UTC or local time. It will represent midnight on that day.
  Time UTCMidnight() const { return Midnight(false); }
  Time LocalMidnight() const { return Midnight(true); }

  // Converts an integer value representing Time to a class. This may be used
  // when deserializing a |Time| structure, using a value known to be
  // compatible. It is not provided as a constructor because the integer type
  // may be unclear from the perspective of a caller.
  //
  // DEPRECATED - Do not use in new code. For deserializing Time values, prefer
  // Time::FromDeltaSinceWindowsEpoch(). http://crbug.com/634507
  static constexpr Time FromInternalValue(int64_t us) { return Time(us); }

 private:
  friend class time_internal::TimeBase<Time>;

  constexpr explicit Time(int64_t microseconds_since_win_epoch)
      : TimeBase(microseconds_since_win_epoch) {}

  // Explodes the given time to either local time |is_local = true| or UTC
  // |is_local = false|.
  void Explode(bool is_local, Exploded* exploded) const;

  // Unexplodes a given time assuming the source is either local time
  // |is_local = true| or UTC |is_local = false|. Function returns false on
  // failure and sets |time| to Time(0). Otherwise returns true and sets |time|
  // to non-exploded time.
  static bool FromExploded(bool is_local,
                           const Exploded& exploded,
                           Time* time) WARN_UNUSED_RESULT;

  // Rounds down the time to the nearest day in either local time
  // |is_local = true| or UTC |is_local = false|.
  Time Midnight(bool is_local) const;

  // Converts a string representation of time to a Time object.
  // An example of a time string which is converted is as below:-
  // "Tue, 15 Nov 1994 12:45:26 GMT". If the timezone is not specified
  // in the input string, local time |is_local = true| or
  // UTC |is_local = false| is assumed. A timezone that cannot be parsed
  // (e.g. "UTC" which is not specified in RFC822) is treated as if the
  // timezone is not specified.
  static bool FromStringInternal(const char* time_string,
                                 bool is_local,
                                 Time* parsed_time) WARN_UNUSED_RESULT;

  // Comparison does not consider |day_of_week| when doing the operation.
  static bool ExplodedMostlyEquals(const Exploded& lhs,
                                   const Exploded& rhs) WARN_UNUSED_RESULT;

  // Converts the provided time in milliseconds since the Unix epoch (1970) to a
  // Time object, avoiding overflows.
  static bool FromMillisecondsSinceUnixEpoch(int64_t unix_milliseconds,
                                             Time* time) WARN_UNUSED_RESULT;

  // Returns the milliseconds since the Unix epoch (1970), rounding the
  // microseconds towards -infinity.
  int64_t ToRoundedDownMillisecondsSinceUnixEpoch() const;
};

// static
constexpr TimeDelta TimeDelta::FromDays(int days) {
  return days == std::numeric_limits<int>::max()
             ? Max()
             : TimeDelta(days * Time::kMicrosecondsPerDay);
}

// static
constexpr TimeDelta TimeDelta::FromHours(int hours) {
  return hours == std::numeric_limits<int>::max()
             ? Max()
             : TimeDelta(hours * Time::kMicrosecondsPerHour);
}

// static
constexpr TimeDelta TimeDelta::FromMinutes(int minutes) {
  return minutes == std::numeric_limits<int>::max()
             ? Max()
             : TimeDelta(minutes * Time::kMicrosecondsPerMinute);
}

// static
constexpr TimeDelta TimeDelta::FromSeconds(int64_t secs) {
  return FromProduct(secs, Time::kMicrosecondsPerSecond);
}

// static
constexpr TimeDelta TimeDelta::FromMilliseconds(int64_t ms) {
  return FromProduct(ms, Time::kMicrosecondsPerMillisecond);
}

// static
constexpr TimeDelta TimeDelta::FromMicroseconds(int64_t us) {
  return TimeDelta(us);
}

// static
constexpr TimeDelta TimeDelta::FromNanoseconds(int64_t ns) {
  return TimeDelta(ns / Time::kNanosecondsPerMicrosecond);
}

// static
constexpr TimeDelta TimeDelta::FromSecondsD(double secs) {
  return FromDouble(secs * Time::kMicrosecondsPerSecond);
}

// static
constexpr TimeDelta TimeDelta::FromMillisecondsD(double ms) {
  return FromDouble(ms * Time::kMicrosecondsPerMillisecond);
}

// static
constexpr TimeDelta TimeDelta::FromMicrosecondsD(double us) {
  return FromDouble(us);
}

// static
constexpr TimeDelta TimeDelta::FromNanosecondsD(double ns) {
  return FromDouble(ns / Time::kNanosecondsPerMicrosecond);
}

// static
constexpr TimeDelta TimeDelta::Max() {
  return TimeDelta(std::numeric_limits<int64_t>::max());
}

// static
constexpr TimeDelta TimeDelta::Min() {
  return TimeDelta(std::numeric_limits<int64_t>::min());
}

// static
constexpr TimeDelta TimeDelta::FromDouble(double value) {
  return TimeDelta(saturated_cast<int64_t>(value));
}

// static
constexpr TimeDelta TimeDelta::FromProduct(int64_t value,
                                           int64_t positive_value) {
  DCHECK(positive_value > 0);  // NOLINT, DCHECK_GT isn't constexpr.
  return value > std::numeric_limits<int64_t>::max() / positive_value
             ? Max()
             : value < std::numeric_limits<int64_t>::min() / positive_value
                   ? Min()
                   : TimeDelta(value * positive_value);
}

// For logging use only.
BASE_EXPORT std::ostream& operator<<(std::ostream& os, Time time);

// TimeTicks ------------------------------------------------------------------

// Represents monotonically non-decreasing clock time.
class BASE_EXPORT TimeTicks : public time_internal::TimeBase<TimeTicks> {
 public:
  // The underlying clock used to generate new TimeTicks.
  enum class Clock {
    FUCHSIA_ZX_CLOCK_MONOTONIC,
    LINUX_CLOCK_MONOTONIC,
    IOS_CF_ABSOLUTE_TIME_MINUS_KERN_BOOTTIME,
    MAC_MACH_ABSOLUTE_TIME,
    WIN_QPC,
    WIN_ROLLOVER_PROTECTED_TIME_GET_TIME
  };

  constexpr TimeTicks() : TimeBase(0) {}

  // Platform-dependent tick count representing "right now." When
  // IsHighResolution() returns false, the resolution of the clock could be
  // as coarse as ~15.6ms. Otherwise, the resolution should be no worse than one
  // microsecond.
  static TimeTicks Now();

  // Returns true if the high resolution clock is working on this system and
  // Now() will return high resolution values. Note that, on systems where the
  // high resolution clock works but is deemed inefficient, the low resolution
  // clock will be used instead.
  static bool IsHighResolution() WARN_UNUSED_RESULT;

  // Returns true if TimeTicks is consistent across processes, meaning that
  // timestamps taken on different processes can be safely compared with one
  // another. (Note that, even on platforms where this returns true, time values
  // from different threads that are within one tick of each other must be
  // considered to have an ambiguous ordering.)
  static bool IsConsistentAcrossProcesses() WARN_UNUSED_RESULT;

#if defined(OS_FUCHSIA)
  // Converts between TimeTicks and an ZX_CLOCK_MONOTONIC zx_time_t value.
  static TimeTicks FromZxTime(zx_time_t nanos_since_boot);
  zx_time_t ToZxTime() const;
#endif

#if defined(OS_WIN)
  // Translates an absolute QPC timestamp into a TimeTicks value. The returned
  // value has the same origin as Now(). Do NOT attempt to use this if
  // IsHighResolution() returns false.
  static TimeTicks FromQPCValue(LONGLONG qpc_value);
#endif

#if defined(OS_MACOSX) && !defined(OS_IOS)
  static TimeTicks FromMachAbsoluteTime(uint64_t mach_absolute_time);
#endif  // defined(OS_MACOSX) && !defined(OS_IOS)

#if defined(OS_ANDROID) || defined(OS_CHROMEOS)
  // Converts to TimeTicks the value obtained from SystemClock.uptimeMillis().
  // Note: this convertion may be non-monotonic in relation to previously
  // obtained TimeTicks::Now() values because of the truncation (to
  // milliseconds) performed by uptimeMillis().
  static TimeTicks FromUptimeMillis(int64_t uptime_millis_value);
#endif

  // Get an estimate of the TimeTick value at the time of the UnixEpoch. Because
  // Time and TimeTicks respond differently to user-set time and NTP
  // adjustments, this number is only an estimate. Nevertheless, this can be
  // useful when you need to relate the value of TimeTicks to a real time and
  // date. Note: Upon first invocation, this function takes a snapshot of the
  // realtime clock to establish a reference point.  This function will return
  // the same value for the duration of the application, but will be different
  // in future application runs.
  static TimeTicks UnixEpoch();

  // Returns |this| snapped to the next tick, given a |tick_phase| and
  // repeating |tick_interval| in both directions. |this| may be before,
  // after, or equal to the |tick_phase|.
  TimeTicks SnappedToNextTick(TimeTicks tick_phase,
                              TimeDelta tick_interval) const;

  // Returns an enum indicating the underlying clock being used to generate
  // TimeTicks timestamps. This function should only be used for debugging and
  // logging purposes.
  static Clock GetClock();

  // Converts an integer value representing TimeTicks to a class. This may be
  // used when deserializing a |TimeTicks| structure, using a value known to be
  // compatible. It is not provided as a constructor because the integer type
  // may be unclear from the perspective of a caller.
  //
  // DEPRECATED - Do not use in new code. For deserializing TimeTicks values,
  // prefer TimeTicks + TimeDelta(). http://crbug.com/634507
  static constexpr TimeTicks FromInternalValue(int64_t us) {
    return TimeTicks(us);
  }

 protected:
#if defined(OS_WIN)
  typedef DWORD (*TickFunctionType)(void);
  static TickFunctionType SetMockTickFunction(TickFunctionType ticker);
#endif

 private:
  friend class time_internal::TimeBase<TimeTicks>;

  // Please use Now() to create a new object. This is for internal use
  // and testing.
  constexpr explicit TimeTicks(int64_t us) : TimeBase(us) {}
};

// For logging use only.
BASE_EXPORT std::ostream& operator<<(std::ostream& os, TimeTicks time_ticks);

// ThreadTicks ----------------------------------------------------------------

// Represents a clock, specific to a particular thread, than runs only while the
// thread is running.
class BASE_EXPORT ThreadTicks : public time_internal::TimeBase<ThreadTicks> {
 public:
  constexpr ThreadTicks() : TimeBase(0) {}

  // Returns true if ThreadTicks::Now() is supported on this system.
  static bool IsSupported() WARN_UNUSED_RESULT {
#if (defined(_POSIX_THREAD_CPUTIME) && (_POSIX_THREAD_CPUTIME >= 0)) || \
    (defined(OS_MACOSX) && !defined(OS_IOS)) || defined(OS_ANDROID) ||  \
    defined(OS_FUCHSIA)
    return true;
#elif defined(OS_WIN)
    return IsSupportedWin();
#else
    return false;
#endif
  }

  // Waits until the initialization is completed. Needs to be guarded with a
  // call to IsSupported().
  static void WaitUntilInitialized() {
#if defined(OS_WIN)
    WaitUntilInitializedWin();
#endif
  }

  // Returns thread-specific CPU-time on systems that support this feature.
  // Needs to be guarded with a call to IsSupported(). Use this timer
  // to (approximately) measure how much time the calling thread spent doing
  // actual work vs. being de-scheduled. May return bogus results if the thread
  // migrates to another CPU between two calls. Returns an empty ThreadTicks
  // object until the initialization is completed. If a clock reading is
  // absolutely needed, call WaitUntilInitialized() before this method.
  static ThreadTicks Now();

#if defined(OS_WIN)
  // Similar to Now() above except this returns thread-specific CPU time for an
  // arbitrary thread. All comments for Now() method above apply apply to this
  // method as well.
  static ThreadTicks GetForThread(const PlatformThreadHandle& thread_handle);
#endif

  // Converts an integer value representing ThreadTicks to a class. This may be
  // used when deserializing a |ThreadTicks| structure, using a value known to
  // be compatible. It is not provided as a constructor because the integer type
  // may be unclear from the perspective of a caller.
  //
  // DEPRECATED - Do not use in new code. For deserializing ThreadTicks values,
  // prefer ThreadTicks + TimeDelta(). http://crbug.com/634507
  static constexpr ThreadTicks FromInternalValue(int64_t us) {
    return ThreadTicks(us);
  }

 private:
  friend class time_internal::TimeBase<ThreadTicks>;

  // Please use Now() or GetForThread() to create a new object. This is for
  // internal use and testing.
  constexpr explicit ThreadTicks(int64_t us) : TimeBase(us) {}

#if defined(OS_WIN)
  FRIEND_TEST_ALL_PREFIXES(TimeTicks, TSCTicksPerSecond);

#if defined(ARCH_CPU_ARM64)
  // TSCTicksPerSecond is not supported on Windows on Arm systems because the
  // cycle-counting methods use the actual CPU cycle count, and not a consistent
  // incrementing counter.
#else
  // Returns the frequency of the TSC in ticks per second, or 0 if it hasn't
  // been measured yet. Needs to be guarded with a call to IsSupported().
  // This method is declared here rather than in the anonymous namespace to
  // allow testing.
  static double TSCTicksPerSecond();
#endif

  static bool IsSupportedWin() WARN_UNUSED_RESULT;
  static void WaitUntilInitializedWin();
#endif
};

// For logging use only.
BASE_EXPORT std::ostream& operator<<(std::ostream& os, ThreadTicks time_ticks);

}  // namespace base

#endif  // BASE_TIME_TIME_H_
