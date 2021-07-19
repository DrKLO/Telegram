/*
 *  Copyright (c) 2012 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */
// An OS-independent sleep function.

#include "system_wrappers/include/sleep.h"

#ifdef _WIN32
// For Sleep()
#include <windows.h>
#else
// For nanosleep()
#include <time.h>
#endif

namespace webrtc {

void SleepMs(int msecs) {
#ifdef _WIN32
  Sleep(msecs);
#else
  struct timespec short_wait;
  struct timespec remainder;
  short_wait.tv_sec = msecs / 1000;
  short_wait.tv_nsec = (msecs % 1000) * 1000 * 1000;
  nanosleep(&short_wait, &remainder);
#endif
}

}  // namespace webrtc
