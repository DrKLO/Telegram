/*
 *  Copyright (c) 2011 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "rtc_base/synchronization/rw_lock_posix.h"

#include <stddef.h>

namespace webrtc {

RWLockPosix::RWLockPosix() : lock_() {}

RWLockPosix::~RWLockPosix() {
  pthread_rwlock_destroy(&lock_);
}

RWLockPosix* RWLockPosix::Create() {
  RWLockPosix* ret_val = new RWLockPosix();
  if (!ret_val->Init()) {
    delete ret_val;
    return NULL;
  }
  return ret_val;
}

bool RWLockPosix::Init() {
  return pthread_rwlock_init(&lock_, 0) == 0;
}

void RWLockPosix::AcquireLockExclusive() {
  pthread_rwlock_wrlock(&lock_);
}

void RWLockPosix::ReleaseLockExclusive() {
  pthread_rwlock_unlock(&lock_);
}

void RWLockPosix::AcquireLockShared() {
  pthread_rwlock_rdlock(&lock_);
}

void RWLockPosix::ReleaseLockShared() {
  pthread_rwlock_unlock(&lock_);
}

}  // namespace webrtc
