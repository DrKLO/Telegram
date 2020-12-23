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

#if defined(WEBRTC_WIN)

// Windows needs to be included before mmsystem.h
#include "rtc_base/win32.h"

#include <mmsystem.h>


#elif defined(WEBRTC_POSIX)

#include <sys/time.h>
#include <time.h>

#endif  // defined(WEBRTC_POSIX)

#include "rtc_base/synchronization/mutex.h"
#include "rtc_base/time_utils.h"

namespace webrtc {

class RealTimeClock : public Clock {
  Timestamp CurrentTime() override {
    return Timestamp::Micros(rtc::TimeMicros());
  }
  // Return a timestamp in milliseconds relative to some arbitrary source; the
  // source is fixed for this clock.
  int64_t TimeInMilliseconds() override { return rtc::TimeMillis(); }

  // Return a timestamp in microseconds relative to some arbitrary source; the
  // source is fixed for this clock.
  int64_t TimeInMicroseconds() override { return rtc::TimeMicros(); }

  // Retrieve an NTP absolute timestamp.
  NtpTime CurrentNtpTime() override {
    timeval tv = CurrentTimeVal();
    double microseconds_in_seconds;
    uint32_t seconds;
    Adjust(tv, &seconds, &microseconds_in_seconds);
    uint32_t fractions = static_cast<uint32_t>(
        microseconds_in_seconds * kMagicNtpFractionalUnit + 0.5);
    return NtpTime(seconds, fractions);
  }

  // Retrieve an NTP absolute timestamp in milliseconds.
  int64_t CurrentNtpInMilliseconds() override {
    timeval tv = CurrentTimeVal();
    uint32_t seconds;
    double microseconds_in_seconds;
    Adjust(tv, &seconds, &microseconds_in_seconds);
    return 1000 * static_cast<int64_t>(seconds) +
           static_cast<int64_t>(1000.0 * microseconds_in_seconds + 0.5);
  }

 protected:
  virtual timeval CurrentTimeVal() = 0;

  static void Adjust(const timeval& tv,
                     uint32_t* adjusted_s,
                     double* adjusted_us_in_s) {
    *adjusted_s = tv.tv_sec + kNtpJan1970;
    *adjusted_us_in_s = tv.tv_usec / 1e6;

    if (*adjusted_us_in_s >= 1) {
      *adjusted_us_in_s -= 1;
      ++*adjusted_s;
    } else if (*adjusted_us_in_s < -1) {
      *adjusted_us_in_s += 1;
      --*adjusted_s;
    }
  }
};

#if defined(WINUWP)
class WinUwpRealTimeClock final : public RealTimeClock {
 public:
  WinUwpRealTimeClock() = default;
  ~WinUwpRealTimeClock() override {}

 protected:
  timeval CurrentTimeVal() override {
    // The rtc::SystemTimeNanos() method is already time offset from a base
    // epoch value and might as be synchronized against an NTP time server as
    // an added bonus.
    auto nanos = rtc::SystemTimeNanos();

    struct timeval tv;

    tv.tv_sec = rtc::dchecked_cast<long>(nanos / 1000000000);
    tv.tv_usec = rtc::dchecked_cast<long>(nanos / 1000);

    return tv;
  }
};

#elif defined(WEBRTC_WIN)
// TODO(pbos): Consider modifying the implementation to synchronize itself
// against system time (update ref_point_) periodically to
// prevent clock drift.
class WindowsRealTimeClock : public RealTimeClock {
 public:
  WindowsRealTimeClock()
      : last_time_ms_(0),
        num_timer_wraps_(0),
        ref_point_(GetSystemReferencePoint()) {}

  ~WindowsRealTimeClock() override {}

 protected:
  struct ReferencePoint {
    FILETIME file_time;
    LARGE_INTEGER counter_ms;
  };

  timeval CurrentTimeVal() override {
    const uint64_t FILETIME_1970 = 0x019db1ded53e8000;

    FILETIME StartTime;
    uint64_t Time;
    struct timeval tv;

    // We can't use query performance counter since they can change depending on
    // speed stepping.
    GetTime(&StartTime);

    Time = (((uint64_t)StartTime.dwHighDateTime) << 32) +
           (uint64_t)StartTime.dwLowDateTime;

    // Convert the hecto-nano second time to tv format.
    Time -= FILETIME_1970;

    tv.tv_sec = (uint32_t)(Time / (uint64_t)10000000);
    tv.tv_usec = (uint32_t)((Time % (uint64_t)10000000) / 10);
    return tv;
  }

  void GetTime(FILETIME* current_time) {
    DWORD t;
    LARGE_INTEGER elapsed_ms;
    {
      MutexLock lock(&mutex_);
      // time MUST be fetched inside the critical section to avoid non-monotonic
      // last_time_ms_ values that'll register as incorrect wraparounds due to
      // concurrent calls to GetTime.
      t = timeGetTime();
      if (t < last_time_ms_)
        num_timer_wraps_++;
      last_time_ms_ = t;
      elapsed_ms.HighPart = num_timer_wraps_;
    }
    elapsed_ms.LowPart = t;
    elapsed_ms.QuadPart = elapsed_ms.QuadPart - ref_point_.counter_ms.QuadPart;

    // Translate to 100-nanoseconds intervals (FILETIME resolution)
    // and add to reference FILETIME to get current FILETIME.
    ULARGE_INTEGER filetime_ref_as_ul;
    filetime_ref_as_ul.HighPart = ref_point_.file_time.dwHighDateTime;
    filetime_ref_as_ul.LowPart = ref_point_.file_time.dwLowDateTime;
    filetime_ref_as_ul.QuadPart +=
        static_cast<ULONGLONG>((elapsed_ms.QuadPart) * 1000 * 10);

    // Copy to result
    current_time->dwHighDateTime = filetime_ref_as_ul.HighPart;
    current_time->dwLowDateTime = filetime_ref_as_ul.LowPart;
  }

  static ReferencePoint GetSystemReferencePoint() {
    ReferencePoint ref = {};
    FILETIME ft0 = {};
    FILETIME ft1 = {};
    // Spin waiting for a change in system time. As soon as this change happens,
    // get the matching call for timeGetTime() as soon as possible. This is
    // assumed to be the most accurate offset that we can get between
    // timeGetTime() and system time.

    // Set timer accuracy to 1 ms.
    timeBeginPeriod(1);
    GetSystemTimeAsFileTime(&ft0);
    do {
      GetSystemTimeAsFileTime(&ft1);

      ref.counter_ms.QuadPart = timeGetTime();
      Sleep(0);
    } while ((ft0.dwHighDateTime == ft1.dwHighDateTime) &&
             (ft0.dwLowDateTime == ft1.dwLowDateTime));
    ref.file_time = ft1;
    timeEndPeriod(1);
    return ref;
  }

  Mutex mutex_;
  DWORD last_time_ms_;
  LONG num_timer_wraps_;
  const ReferencePoint ref_point_;
};

#elif defined(WEBRTC_POSIX)
class UnixRealTimeClock : public RealTimeClock {
 public:
  UnixRealTimeClock() {}

  ~UnixRealTimeClock() override {}

 protected:
  timeval CurrentTimeVal() override {
    struct timeval tv;
    struct timezone tz;
    tz.tz_minuteswest = 0;
    tz.tz_dsttime = 0;
    gettimeofday(&tv, &tz);
    return tv;
  }
};
#endif  // defined(WEBRTC_POSIX)

Clock* Clock::GetRealTimeClock() {
#if defined(WINUWP)
  static Clock* const clock = new WinUwpRealTimeClock();
#elif defined(WEBRTC_WIN)
  static Clock* const clock = new WindowsRealTimeClock();
#elif defined(WEBRTC_POSIX)
  static Clock* const clock = new UnixRealTimeClock();
#else
  static Clock* const clock = nullptr;
#endif
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

NtpTime SimulatedClock::CurrentNtpTime() {
  int64_t now_ms = TimeInMilliseconds();
  uint32_t seconds = (now_ms / 1000) + kNtpJan1970;
  uint32_t fractions =
      static_cast<uint32_t>((now_ms % 1000) * kMagicNtpFractionalUnit / 1000);
  return NtpTime(seconds, fractions);
}

int64_t SimulatedClock::CurrentNtpInMilliseconds() {
  return TimeInMilliseconds() + 1000 * static_cast<int64_t>(kNtpJan1970);
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
