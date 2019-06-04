/*
 *  Copyright 2004 The WebRTC Project Authors. All rights reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "rtc_base/event.h"

#if defined(WEBRTC_WIN)
#include <windows.h>
#elif defined(WEBRTC_POSIX)
#include <pthread.h>
#include <sys/time.h>
#include <time.h>
#else
#error "Must define either WEBRTC_WIN or WEBRTC_POSIX."
#endif

#include "rtc_base/checks.h"

namespace rtc {

Event::Event() : Event(false, false) {}

#if defined(WEBRTC_WIN)

Event::Event(bool manual_reset, bool initially_signaled) {
  event_handle_ = ::CreateEvent(nullptr,  // Security attributes.
                                manual_reset, initially_signaled,
                                nullptr);  // Name.
  RTC_CHECK(event_handle_);
}

Event::~Event() {
  CloseHandle(event_handle_);
}

void Event::Set() {
  SetEvent(event_handle_);
}

void Event::Reset() {
  ResetEvent(event_handle_);
}

bool Event::Wait(int milliseconds) {
  DWORD ms = (milliseconds == kForever) ? INFINITE : milliseconds;
  return (WaitForSingleObject(event_handle_, ms) == WAIT_OBJECT_0);
}

#elif defined(WEBRTC_POSIX)

// On MacOS, clock_gettime is available from version 10.12, and on
// iOS, from version 10.0. So we can't use it yet.
#if defined(WEBRTC_MAC) || defined(WEBRTC_IOS)
#define USE_CLOCK_GETTIME 0
#define USE_PTHREAD_COND_TIMEDWAIT_MONOTONIC_NP 0
// On Android, pthread_condattr_setclock is available from version 21. By
// default, we target a new enough version for 64-bit platforms but not for
// 32-bit platforms. For older versions, use
// pthread_cond_timedwait_monotonic_np.
#elif defined(WEBRTC_ANDROID) && (__ANDROID_API__ < 21)
#define USE_CLOCK_GETTIME 1
#define USE_PTHREAD_COND_TIMEDWAIT_MONOTONIC_NP 1
#else
#define USE_CLOCK_GETTIME 1
#define USE_PTHREAD_COND_TIMEDWAIT_MONOTONIC_NP 0
#endif

Event::Event(bool manual_reset, bool initially_signaled)
    : is_manual_reset_(manual_reset), event_status_(initially_signaled) {
  RTC_CHECK(pthread_mutex_init(&event_mutex_, nullptr) == 0);
  pthread_condattr_t cond_attr;
  RTC_CHECK(pthread_condattr_init(&cond_attr) == 0);
#if USE_CLOCK_GETTIME && !USE_PTHREAD_COND_TIMEDWAIT_MONOTONIC_NP
  RTC_CHECK(pthread_condattr_setclock(&cond_attr, CLOCK_MONOTONIC) == 0);
#endif
  RTC_CHECK(pthread_cond_init(&event_cond_, &cond_attr) == 0);
  pthread_condattr_destroy(&cond_attr);
}

Event::~Event() {
  pthread_mutex_destroy(&event_mutex_);
  pthread_cond_destroy(&event_cond_);
}

void Event::Set() {
  pthread_mutex_lock(&event_mutex_);
  event_status_ = true;
  pthread_cond_broadcast(&event_cond_);
  pthread_mutex_unlock(&event_mutex_);
}

void Event::Reset() {
  pthread_mutex_lock(&event_mutex_);
  event_status_ = false;
  pthread_mutex_unlock(&event_mutex_);
}

bool Event::Wait(int milliseconds) {
  int error = 0;

  struct timespec ts;
  if (milliseconds != kForever) {
#if USE_CLOCK_GETTIME
    clock_gettime(CLOCK_MONOTONIC, &ts);
#else
    struct timeval tv;
    gettimeofday(&tv, nullptr);
    ts.tv_sec = tv.tv_sec;
    ts.tv_nsec = tv.tv_usec * 1000;
#endif

    ts.tv_sec += (milliseconds / 1000);
    ts.tv_nsec += (milliseconds % 1000) * 1000000;

    // Handle overflow.
    if (ts.tv_nsec >= 1000000000) {
      ts.tv_sec++;
      ts.tv_nsec -= 1000000000;
    }
  }

  pthread_mutex_lock(&event_mutex_);
  if (milliseconds != kForever) {
    while (!event_status_ && error == 0) {
#if USE_PTHREAD_COND_TIMEDWAIT_MONOTONIC_NP
      error =
          pthread_cond_timedwait_monotonic_np(&event_cond_, &event_mutex_, &ts);
#else
      error = pthread_cond_timedwait(&event_cond_, &event_mutex_, &ts);
#endif
    }
  } else {
    while (!event_status_ && error == 0)
      error = pthread_cond_wait(&event_cond_, &event_mutex_);
  }

  // NOTE(liulk): Exactly one thread will auto-reset this event. All
  // the other threads will think it's unsignaled.  This seems to be
  // consistent with auto-reset events in WEBRTC_WIN
  if (error == 0 && !is_manual_reset_)
    event_status_ = false;

  pthread_mutex_unlock(&event_mutex_);

  return (error == 0);
}

#endif

}  // namespace rtc
