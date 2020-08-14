/*
 *  Copyright 2004 The WebRTC Project Authors. All rights reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#ifndef RTC_BASE_EVENT_H_
#define RTC_BASE_EVENT_H_

#if defined(WEBRTC_WIN)
#include <windows.h>
#elif defined(WEBRTC_POSIX)
#include <pthread.h>
#else
#error "Must define either WEBRTC_WIN or WEBRTC_POSIX."
#endif

namespace rtc {

class Event {
 public:
  static const int kForever = -1;

  Event();
  Event(bool manual_reset, bool initially_signaled);
  Event(const Event&) = delete;
  Event& operator=(const Event&) = delete;
  ~Event();

  void Set();
  void Reset();

  // Waits for the event to become signaled, but logs a warning if it takes more
  // than `warn_after_ms` milliseconds, and gives up completely if it takes more
  // than `give_up_after_ms` milliseconds. (If `warn_after_ms >=
  // give_up_after_ms`, no warning will be logged.) Either or both may be
  // `kForever`, which means wait indefinitely.
  //
  // Returns true if the event was signaled, false if there was a timeout or
  // some other error.
  bool Wait(int give_up_after_ms, int warn_after_ms);

  // Waits with the given timeout and a reasonable default warning timeout.
  bool Wait(int give_up_after_ms) {
    return Wait(give_up_after_ms,
                give_up_after_ms == kForever ? 3000 : kForever);
  }

 private:
#if defined(WEBRTC_WIN)
  HANDLE event_handle_;
#elif defined(WEBRTC_POSIX)
  pthread_mutex_t event_mutex_;
  pthread_cond_t event_cond_;
  const bool is_manual_reset_;
  bool event_status_;
#endif
};

// These classes are provided for compatibility with Chromium.
// The rtc::Event implementation is overriden inside of Chromium for the
// purposes of detecting when threads are blocked that shouldn't be as well as
// to use the more accurate event implementation that's there than is provided
// by default on some platforms (e.g. Windows).
// When building with standalone WebRTC, this class is a noop.
// For further information, please see the
// ScopedAllowBaseSyncPrimitives(ForTesting) classes in Chromium.
class ScopedAllowBaseSyncPrimitives {
 public:
  ScopedAllowBaseSyncPrimitives() {}
  ~ScopedAllowBaseSyncPrimitives() {}
};

class ScopedAllowBaseSyncPrimitivesForTesting {
 public:
  ScopedAllowBaseSyncPrimitivesForTesting() {}
  ~ScopedAllowBaseSyncPrimitivesForTesting() {}
};

}  // namespace rtc

#endif  // RTC_BASE_EVENT_H_
