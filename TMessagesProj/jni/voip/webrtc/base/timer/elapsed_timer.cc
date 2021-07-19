// Copyright 2013 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

#include "base/timer/elapsed_timer.h"

namespace base {

namespace {
bool g_mock_elapsed_timers_for_test = false;
}  // namespace

ElapsedTimer::ElapsedTimer() : begin_(TimeTicks::Now()) {}

ElapsedTimer::ElapsedTimer(ElapsedTimer&& other) : begin_(other.begin_) {}

void ElapsedTimer::operator=(ElapsedTimer&& other) {
  begin_ = other.begin_;
}

TimeDelta ElapsedTimer::Elapsed() const {
  if (g_mock_elapsed_timers_for_test)
    return ScopedMockElapsedTimersForTest::kMockElapsedTime;
  return TimeTicks::Now() - begin_;
}

ElapsedThreadTimer::ElapsedThreadTimer()
    : is_supported_(ThreadTicks::IsSupported()),
      begin_(is_supported_ ? ThreadTicks::Now() : ThreadTicks()) {}

TimeDelta ElapsedThreadTimer::Elapsed() const {
  if (!is_supported_)
    return TimeDelta();
  if (g_mock_elapsed_timers_for_test)
    return ScopedMockElapsedTimersForTest::kMockElapsedTime;
  return ThreadTicks::Now() - begin_;
}

// static
constexpr TimeDelta ScopedMockElapsedTimersForTest::kMockElapsedTime;

ScopedMockElapsedTimersForTest::ScopedMockElapsedTimersForTest() {
  DCHECK(!g_mock_elapsed_timers_for_test);
  g_mock_elapsed_timers_for_test = true;
}

ScopedMockElapsedTimersForTest::~ScopedMockElapsedTimersForTest() {
  DCHECK(g_mock_elapsed_timers_for_test);
  g_mock_elapsed_timers_for_test = false;
}

}  // namespace base
