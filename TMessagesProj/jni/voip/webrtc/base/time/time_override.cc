// Copyright 2018 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

#include "base/time/time_override.h"

namespace base {
namespace subtle {

// static
bool ScopedTimeClockOverrides::overrides_active_ = false;

ScopedTimeClockOverrides::ScopedTimeClockOverrides(
    TimeNowFunction time_override,
    TimeTicksNowFunction time_ticks_override,
    ThreadTicksNowFunction thread_ticks_override) {
  DCHECK(!overrides_active_);
  overrides_active_ = true;
  if (time_override) {
    internal::g_time_now_function = time_override;
    internal::g_time_now_from_system_time_function = time_override;
  }
  if (time_ticks_override)
    internal::g_time_ticks_now_function = time_ticks_override;
  if (thread_ticks_override)
    internal::g_thread_ticks_now_function = thread_ticks_override;
}

ScopedTimeClockOverrides::~ScopedTimeClockOverrides() {
  internal::g_time_now_function = &TimeNowIgnoringOverride;
  internal::g_time_now_from_system_time_function =
      &TimeNowFromSystemTimeIgnoringOverride;
  internal::g_time_ticks_now_function = &TimeTicksNowIgnoringOverride;
  internal::g_thread_ticks_now_function = &ThreadTicksNowIgnoringOverride;
  overrides_active_ = false;
}

}  // namespace subtle
}  // namespace base
