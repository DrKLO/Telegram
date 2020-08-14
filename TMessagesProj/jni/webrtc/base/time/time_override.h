// Copyright 2018 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

#ifndef BASE_TIME_TIME_OVERRIDE_H_
#define BASE_TIME_TIME_OVERRIDE_H_

#include "base/base_export.h"
#include "base/time/time.h"

namespace base {

using TimeNowFunction = decltype(&Time::Now);
using TimeTicksNowFunction = decltype(&TimeTicks::Now);
using ThreadTicksNowFunction = decltype(&ThreadTicks::Now);

// Time overrides should be used with extreme caution. Discuss with //base/time
// OWNERS before adding a new one.
namespace subtle {

// Override the return value of Time::Now and Time::NowFromSystemTime /
// TimeTicks::Now / ThreadTicks::Now to emulate time, e.g. for tests or to
// modify progression of time. Note that the override should be set while
// single-threaded and before the first call to Now() to avoid threading issues
// and inconsistencies in returned values. Nested overrides are not allowed.
class BASE_EXPORT ScopedTimeClockOverrides {
 public:
  // Pass |nullptr| for any override if it shouldn't be overriden.
  ScopedTimeClockOverrides(TimeNowFunction time_override,
                           TimeTicksNowFunction time_ticks_override,
                           ThreadTicksNowFunction thread_ticks_override);

  // Restores the platform default Now() functions.
  ~ScopedTimeClockOverrides();

  static bool overrides_active() { return overrides_active_; }

 private:
  static bool overrides_active_;

  DISALLOW_COPY_AND_ASSIGN(ScopedTimeClockOverrides);
};

// These methods return the platform default Time::Now / TimeTicks::Now /
// ThreadTicks::Now values even while an override is in place. These methods
// should only be used in places where emulated time should be disregarded. For
// example, they can be used to implement test timeouts for tests that may
// override time.
BASE_EXPORT Time TimeNowIgnoringOverride();
BASE_EXPORT Time TimeNowFromSystemTimeIgnoringOverride();
BASE_EXPORT TimeTicks TimeTicksNowIgnoringOverride();
BASE_EXPORT ThreadTicks ThreadTicksNowIgnoringOverride();

}  // namespace subtle

namespace internal {

// These function pointers are used by platform-independent implementations of
// the Now() methods and ScopedTimeClockOverrides. They are set to point to the
// respective NowIgnoringOverride functions by default, but can also be set by
// platform-specific code to select a default implementation at runtime, thereby
// avoiding the indirection via the NowIgnoringOverride functions. Note that the
// pointers can be overridden and later reset to the NowIgnoringOverride
// functions by ScopedTimeClockOverrides.
extern TimeNowFunction g_time_now_function;
extern TimeNowFunction g_time_now_from_system_time_function;
extern TimeTicksNowFunction g_time_ticks_now_function;
extern ThreadTicksNowFunction g_thread_ticks_now_function;

}  // namespace internal

}  // namespace base

#endif  // BASE_TIME_TIME_OVERRIDE_H_
