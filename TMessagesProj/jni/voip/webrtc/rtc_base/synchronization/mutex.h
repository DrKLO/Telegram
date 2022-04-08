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

#include "absl/base/attributes.h"
#include "absl/base/const_init.h"
#include "rtc_base/checks.h"
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
    impl_.Lock();
  }
  ABSL_MUST_USE_RESULT bool TryLock() RTC_EXCLUSIVE_TRYLOCK_FUNCTION(true) {
    return impl_.TryLock();
  }
  // Return immediately if this thread holds the mutex, or RTC_DCHECK_IS_ON==0.
  // Otherwise, may report an error (typically by crashing with a diagnostic),
  // or may return immediately.
  void AssertHeld() const RTC_ASSERT_EXCLUSIVE_LOCK() { impl_.AssertHeld(); }
  void Unlock() RTC_UNLOCK_FUNCTION() {
    impl_.Unlock();
  }

 private:
  MutexImpl impl_;
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
