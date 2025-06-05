/*
 *  Copyright (c) 2013 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "system_wrappers/include/clock.h"

#include "rtc_base/time_utils.h"

namespace webrtc {
namespace {

int64_t NtpOffsetUsCalledOnce() {
  constexpr int64_t kNtpJan1970Sec = 2208988800;
  int64_t clock_time = rtc::TimeMicros();
  int64_t utc_time = rtc::TimeUTCMicros();
  return utc_time - clock_time + kNtpJan1970Sec * rtc::kNumMicrosecsPerSec;
}

NtpTime TimeMicrosToNtp(int64_t time_us) {
  static int64_t ntp_offset_us = NtpOffsetUsCalledOnce();

  int64_t time_ntp_us = time_us + ntp_offset_us;
  RTC_DCHECK_GE(time_ntp_us, 0);  // Time before year 1900 is unsupported.

  // Convert seconds to uint32 through uint64 for a well-defined cast.
  // A wrap around, which will happen in 2036, is expected for NTP time.
  uint32_t ntp_seconds =
      static_cast<uint64_t>(time_ntp_us / rtc::kNumMicrosecsPerSec);

  // Scale fractions of the second to NTP resolution.
  constexpr int64_t kNtpFractionsInSecond = 1LL << 32;
  int64_t us_fractions = time_ntp_us % rtc::kNumMicrosecsPerSec;
  uint32_t ntp_fractions =
      us_fractions * kNtpFractionsInSecond / rtc::kNumMicrosecsPerSec;

  return NtpTime(ntp_seconds, ntp_fractions);
}

}  // namespace

class RealTimeClock : public Clock {
 public:
  RealTimeClock() = default;

  Timestamp CurrentTime() override {
    return Timestamp::Micros(rtc::TimeMicros());
  }

  NtpTime ConvertTimestampToNtpTime(Timestamp timestamp) override {
    return TimeMicrosToNtp(timestamp.us());
  }
};

Clock* Clock::GetRealTimeClock() {
  static Clock* const clock = new RealTimeClock();
  return clock;
}

SimulatedClock::SimulatedClock(int64_t initial_time_us)
    : time_us_(initial_time_us) {}

SimulatedClock::SimulatedClock(Timestamp initial_time)
    : SimulatedClock(initial_time.us()) {}

SimulatedClock::~SimulatedClock() {}

Timestamp SimulatedClock::CurrentTime() {
  return Timestamp::Micros(time_us_.load(std::memory_order_relaxed));
}

NtpTime SimulatedClock::ConvertTimestampToNtpTime(Timestamp timestamp) {
  int64_t now_us = timestamp.us();
  uint32_t seconds = (now_us / 1'000'000) + kNtpJan1970;
  uint32_t fractions = static_cast<uint32_t>(
      (now_us % 1'000'000) * kMagicNtpFractionalUnit / 1'000'000);
  return NtpTime(seconds, fractions);
}

void SimulatedClock::AdvanceTimeMilliseconds(int64_t milliseconds) {
  AdvanceTime(TimeDelta::Millis(milliseconds));
}

void SimulatedClock::AdvanceTimeMicroseconds(int64_t microseconds) {
  AdvanceTime(TimeDelta::Micros(microseconds));
}

// TODO(bugs.webrtc.org(12102): It's desirable to let a single thread own
// advancement of the clock. We could then replace this read-modify-write
// operation with just a thread checker. But currently, that breaks a couple of
// tests, in particular, RepeatingTaskTest.ClockIntegration and
// CallStatsTest.LastProcessedRtt.
void SimulatedClock::AdvanceTime(TimeDelta delta) {
  time_us_.fetch_add(delta.us(), std::memory_order_relaxed);
}

}  // namespace webrtc
