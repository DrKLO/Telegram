// Copyright (c) 2011 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

#ifndef BASE_SYNCHRONIZATION_LOCK_H_
#define BASE_SYNCHRONIZATION_LOCK_H_

#include "base/base_export.h"
#include "base/logging.h"
#include "base/macros.h"
#include "base/synchronization/lock_impl.h"
#include "base/thread_annotations.h"
#include "base/threading/platform_thread.h"
#include "build/build_config.h"

namespace base {

// A convenient wrapper for an OS specific critical section.  The only real
// intelligence in this class is in debug mode for the support for the
// AssertAcquired() method.
class LOCKABLE BASE_EXPORT Lock {
 public:
#if !DCHECK_IS_ON()
  // Optimized wrapper implementation
  Lock() : lock_() {}
  ~Lock() {}

  void Acquire() EXCLUSIVE_LOCK_FUNCTION() { lock_.Lock(); }
  void Release() UNLOCK_FUNCTION() { lock_.Unlock(); }

  // If the lock is not held, take it and return true. If the lock is already
  // held by another thread, immediately return false. This must not be called
  // by a thread already holding the lock (what happens is undefined and an
  // assertion may fail).
  bool Try() EXCLUSIVE_TRYLOCK_FUNCTION(true) { return lock_.Try(); }

  // Null implementation if not debug.
  void AssertAcquired() const ASSERT_EXCLUSIVE_LOCK() {}
#else
  Lock();
  ~Lock();

  // NOTE: We do not permit recursive locks and will commonly fire a DCHECK() if
  // a thread attempts to acquire the lock a second time (while already holding
  // it).
  void Acquire() EXCLUSIVE_LOCK_FUNCTION() {
    lock_.Lock();
    CheckUnheldAndMark();
  }
  void Release() UNLOCK_FUNCTION() {
    CheckHeldAndUnmark();
    lock_.Unlock();
  }

  bool Try() EXCLUSIVE_TRYLOCK_FUNCTION(true) {
    bool rv = lock_.Try();
    if (rv) {
      CheckUnheldAndMark();
    }
    return rv;
  }

  void AssertAcquired() const ASSERT_EXCLUSIVE_LOCK();
#endif  // DCHECK_IS_ON()

  // Whether Lock mitigates priority inversion when used from different thread
  // priorities.
  static bool HandlesMultipleThreadPriorities() {
#if defined(OS_WIN)
    // Windows mitigates priority inversion by randomly boosting the priority of
    // ready threads.
    // https://msdn.microsoft.com/library/windows/desktop/ms684831.aspx
    return true;
#elif defined(OS_POSIX) || defined(OS_FUCHSIA)
    // POSIX mitigates priority inversion by setting the priority of a thread
    // holding a Lock to the maximum priority of any other thread waiting on it.
    return internal::LockImpl::PriorityInheritanceAvailable();
#else
#error Unsupported platform
#endif
  }

  // Both Windows and POSIX implementations of ConditionVariable need to be
  // able to see our lock and tweak our debugging counters, as they release and
  // acquire locks inside of their condition variable APIs.
  friend class ConditionVariable;

 private:
#if DCHECK_IS_ON()
  // Members and routines taking care of locks assertions.
  // Note that this checks for recursive locks and allows them
  // if the variable is set.  This is allowed by the underlying implementation
  // on windows but not on Posix, so we're doing unneeded checks on Posix.
  // It's worth it to share the code.
  void CheckHeldAndUnmark();
  void CheckUnheldAndMark();

  // All private data is implicitly protected by lock_.
  // Be VERY careful to only access members under that lock.
  base::PlatformThreadRef owning_thread_ref_;
#endif  // DCHECK_IS_ON()

  // Platform specific underlying lock implementation.
  internal::LockImpl lock_;

  DISALLOW_COPY_AND_ASSIGN(Lock);
};

// A helper class that acquires the given Lock while the AutoLock is in scope.
using AutoLock = internal::BasicAutoLock<Lock>;

// AutoUnlock is a helper that will Release() the |lock| argument in the
// constructor, and re-Acquire() it in the destructor.
using AutoUnlock = internal::BasicAutoUnlock<Lock>;

// Like AutoLock but is a no-op when the provided Lock* is null. Inspired from
// absl::MutexLockMaybe. Use this instead of base::Optional<base::AutoLock> to
// get around -Wthread-safety-analysis warnings for conditional locking.
using AutoLockMaybe = internal::BasicAutoLockMaybe<Lock>;

// Like AutoLock but permits Release() of its mutex before destruction.
// Release() may be called at most once. Inspired from
// absl::ReleasableMutexLock. Use this instead of base::Optional<base::AutoLock>
// to get around -Wthread-safety-analysis warnings for AutoLocks that are
// explicitly released early (prefer proper scoping to this).
using ReleasableAutoLock = internal::BasicReleasableAutoLock<Lock>;

}  // namespace base

#endif  // BASE_SYNCHRONIZATION_LOCK_H_
