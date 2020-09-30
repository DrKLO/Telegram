/*
 *  Copyright 2020 The WebRTC Project Authors. All rights reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "rtc_base/synchronization/yield.h"

#if defined(WEBRTC_WIN)
#include <windows.h>
#else
#include <sched.h>
#include <time.h>
#endif

namespace webrtc {

void YieldCurrentThread() {
  // TODO(bugs.webrtc.org/11634): use dedicated OS functionality instead of
  // sleep for yielding.
#if defined(WEBRTC_WIN)
  ::Sleep(0);
#elif defined(WEBRTC_MAC) && defined(RTC_USE_NATIVE_MUTEX_ON_MAC) && \
    !RTC_USE_NATIVE_MUTEX_ON_MAC
  sched_yield();
#else
  static const struct timespec ts_null = {0};
  nanosleep(&ts_null, nullptr);
#endif
}

}  // namespace webrtc
