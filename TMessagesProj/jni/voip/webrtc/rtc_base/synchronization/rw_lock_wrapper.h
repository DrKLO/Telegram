/*
 *  Copyright (c) 2011 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#ifndef RTC_BASE_SYNCHRONIZATION_RW_LOCK_WRAPPER_H_
#define RTC_BASE_SYNCHRONIZATION_RW_LOCK_WRAPPER_H_

#include "rtc_base/thread_annotations.h"

// Note, Windows pre-Vista version of RW locks are not supported natively. For
// these OSs regular critical sections have been used to approximate RW lock
// functionality and will therefore have worse performance.

namespace webrtc {

class RTC_LOCKABLE RWLockWrapper {
 public:
  static RWLockWrapper* CreateRWLock();
  virtual ~RWLockWrapper() {}

  virtual void AcquireLockExclusive() RTC_EXCLUSIVE_LOCK_FUNCTION() = 0;
  virtual void ReleaseLockExclusive() RTC_UNLOCK_FUNCTION() = 0;

  virtual void AcquireLockShared() RTC_SHARED_LOCK_FUNCTION() = 0;
  virtual void ReleaseLockShared() RTC_UNLOCK_FUNCTION() = 0;
};

// RAII extensions of the RW lock. Prevents Acquire/Release missmatches and
// provides more compact locking syntax.
class RTC_SCOPED_LOCKABLE ReadLockScoped {
 public:
  explicit ReadLockScoped(RWLockWrapper& rw_lock)
      RTC_SHARED_LOCK_FUNCTION(rw_lock)
      : rw_lock_(rw_lock) {
    rw_lock_.AcquireLockShared();
  }

  ~ReadLockScoped() RTC_UNLOCK_FUNCTION() { rw_lock_.ReleaseLockShared(); }

 private:
  RWLockWrapper& rw_lock_;
};

class RTC_SCOPED_LOCKABLE WriteLockScoped {
 public:
  explicit WriteLockScoped(RWLockWrapper& rw_lock)
      RTC_EXCLUSIVE_LOCK_FUNCTION(rw_lock)
      : rw_lock_(rw_lock) {
    rw_lock_.AcquireLockExclusive();
  }

  ~WriteLockScoped() RTC_UNLOCK_FUNCTION() { rw_lock_.ReleaseLockExclusive(); }

 private:
  RWLockWrapper& rw_lock_;
};

}  // namespace webrtc

#endif  // RTC_BASE_SYNCHRONIZATION_RW_LOCK_WRAPPER_H_
