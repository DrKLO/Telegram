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
#include <WinSock2.h>
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

  // Wait for the event to become signaled, for the specified number of
  // |milliseconds|.  To wait indefinetly, pass kForever.
  bool Wait(int milliseconds);

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

// This class is provided for compatibility with Chromium.
// The rtc::Event implementation is overriden inside of Chromium for the
// purposes of detecting when threads are blocked that shouldn't be as well as
// to use the more accurate event implementation that's there than is provided
// by default on some platforms (e.g. Windows).
// When building with standalone WebRTC, this class is a noop.
// For further information, please see the ScopedAllowBaseSyncPrimitives class
// in Chromium.
class ScopedAllowBaseSyncPrimitives {
 public:
  ScopedAllowBaseSyncPrimitives() {}
  ~ScopedAllowBaseSyncPrimitives() {}
};

}  // namespace rtc

#endif  // RTC_BASE_EVENT_H_
