/*
 *  Copyright 2020 The WebRTC Project Authors. All rights reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#ifndef RTC_BASE_SYNCHRONIZATION_MUTEX_H_
#define RTC_BASE_SYNCHRONIZATION_MUTEX_H_

#include <atomic>

#include "absl/base/const_init.h"
#include "rtc_base/checks.h"
#include "rtc_base/platform_thread_types.h"
#include "rtc_base/system/unused.h"
#include "rtc_base/thread_annotations.h"

#if defined(WEBRTC_ABSL_MUTEX)
#include "rtc_base/synchronization/mutex_abseil.h"  // nogncheck
#elif defined(WEBRTC_WIN)
#include "rtc_base/synchronization/mutex_critical_section.h"
#elif defined(WEBRTC_POSIX)
#include "rtc_base/synchronization/mutex_pthread.h"
#else
#error Unsupported platform.
#endif

namespace webrtc {

// The Mutex guarantees exclusive access and aims to follow Abseil semantics
// (i.e. non-reentrant etc).
class RTC_LOCKABLE Mutex final {
 public:
  Mutex() = default;
  Mutex(const Mutex&) = delete;
  Mutex& operator=(const Mutex&) = delete;

  void Lock() RTC_EXCLUSIVE_LOCK_FUNCTION() {
    rtc::PlatformThreadRef current = CurrentThreadRefAssertingNotBeingHolder();
    impl_.Lock();
    // |holder_| changes from 0 to CurrentThreadRef().
    holder_.store(current, std::memory_order_relaxed);
  }
  RTC_WARN_UNUSED_RESULT bool TryLock() RTC_EXCLUSIVE_TRYLOCK_FUNCTION(true) {
    rtc::PlatformThreadRef current = CurrentThreadRefAssertingNotBeingHolder();
    if (impl_.TryLock()) {
      // |holder_| changes from 0 to CurrentThreadRef().
      holder_.store(current, std::memory_order_relaxed);
      return true;
    }
    return false;
  }
  void Unlock() RTC_UNLOCK_FUNCTION() {
    // |holder_| changes from CurrentThreadRef() to 0. If something else than
    // CurrentThreadRef() is stored in |holder_|, the Unlock results in
    // undefined behavior as mutexes can't be unlocked from another thread than
    // the one that locked it, or called while not being locked.
    holder_.store(0, std::memory_order_relaxed);
    impl_.Unlock();
  }

 private:
  rtc::PlatformThreadRef CurrentThreadRefAssertingNotBeingHolder() {
    rtc::PlatformThreadRef holder = holder_.load(std::memory_order_relaxed);
    rtc::PlatformThreadRef current = rtc::CurrentThreadRef();
    // TODO(bugs.webrtc.org/11567): remove this temporary check after migrating
    // fully to Mutex.
    RTC_CHECK_NE(holder, current);
    return current;
  }

  MutexImpl impl_;
  // TODO(bugs.webrtc.org/11567): remove |holder_| after migrating fully to
  // Mutex.
  // |holder_| contains the PlatformThreadRef of the thread currently holding
  // the lock, or 0.
  // Remarks on the used memory orders: the atomic load in
  // CurrentThreadRefAssertingNotBeingHolder() observes either of two things:
  // 1. our own previous write to holder_ with our thread ID.
  // 2. another thread (with ID y) writing y and then 0 from an initial value of
  // 0. If we're observing case 1, our own stores are obviously ordered before
  // the load, and hit the CHECK. If we're observing case 2, the value observed
  // w.r.t |impl_| being locked depends on the memory order. Since we only care
  // that it's different from CurrentThreadRef()), we use the more performant
  // option, memory_order_relaxed.
  std::atomic<rtc::PlatformThreadRef> holder_ = {0};
};

// MutexLock, for serializing execution through a scope.
class RTC_SCOPED_LOCKABLE MutexLock final {
 public:
  MutexLock(const MutexLock&) = delete;
  MutexLock& operator=(const MutexLock&) = delete;

  explicit MutexLock(Mutex* mutex) RTC_EXCLUSIVE_LOCK_FUNCTION(mutex)
      : mutex_(mutex) {
    mutex->Lock();
  }
  ~MutexLock() RTC_UNLOCK_FUNCTION() { mutex_->Unlock(); }

 private:
  Mutex* mutex_;
};

// A mutex used to protect global variables. Do NOT use for other purposes.
#if defined(WEBRTC_ABSL_MUTEX)
using GlobalMutex = absl::Mutex;
using GlobalMutexLock = absl::MutexLock;
#else
class RTC_LOCKABLE GlobalMutex final {
 public:
  GlobalMutex(const GlobalMutex&) = delete;
  GlobalMutex& operator=(const GlobalMutex&) = delete;

  constexpr explicit GlobalMutex(absl::ConstInitType /*unused*/)
      : mutex_locked_(0) {}

  void Lock() RTC_EXCLUSIVE_LOCK_FUNCTION();
  void Unlock() RTC_UNLOCK_FUNCTION();

 private:
  std::atomic<int> mutex_locked_;  // 0 means lock not taken, 1 means taken.
};

// GlobalMutexLock, for serializing execution through a scope.
class RTC_SCOPED_LOCKABLE GlobalMutexLock final {
 public:
  GlobalMutexLock(const GlobalMutexLock&) = delete;
  GlobalMutexLock& operator=(const GlobalMutexLock&) = delete;

  explicit GlobalMutexLock(GlobalMutex* mutex)
      RTC_EXCLUSIVE_LOCK_FUNCTION(mutex_);
  ~GlobalMutexLock() RTC_UNLOCK_FUNCTION();

 private:
  GlobalMutex* mutex_;
};
#endif  // if defined(WEBRTC_ABSL_MUTEX)

}  // namespace webrtc

#endif  // RTC_BASE_SYNCHRONIZATION_MUTEX_H_
