/*
 *  Copyright 2020 The WebRTC Project Authors. All rights reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "rtc_base/synchronization/mutex.h"

#include "rtc_base/checks.h"
#include "rtc_base/synchronization/yield.h"

namespace webrtc {

#if !defined(WEBRTC_ABSL_MUTEX)
void GlobalMutex::Lock() {
  while (mutex_locked_.exchange(1)) {
    YieldCurrentThread();
  }
}

void GlobalMutex::Unlock() {
  int old = mutex_locked_.exchange(0);
  RTC_DCHECK_EQ(old, 1) << "Unlock called without calling Lock first";
}

GlobalMutexLock::GlobalMutexLock(GlobalMutex* mutex) : mutex_(mutex) {
  mutex_->Lock();
}

GlobalMutexLock::~GlobalMutexLock() {
  mutex_->Unlock();
}
#endif  // #if !defined(WEBRTC_ABSL_MUTEX)

}  // namespace webrtc
