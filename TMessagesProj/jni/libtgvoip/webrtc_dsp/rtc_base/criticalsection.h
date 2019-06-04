/*
 *  Copyright 2004 The WebRTC Project Authors. All rights reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#ifndef RTC_BASE_CRITICALSECTION_H_
#define RTC_BASE_CRITICALSECTION_H_

#include "rtc_base/checks.h"
#include "rtc_base/constructormagic.h"
#include "rtc_base/platform_thread_types.h"
#include "rtc_base/thread_annotations.h"

#if defined(WEBRTC_WIN)
// clang-format off
// clang formating would change include order.

// Include winsock2.h before including <windows.h> to maintain consistency with
// win32.h. To include win32.h directly, it must be broken out into its own
// build target.
#include <winsock2.h>
#include <windows.h>
#include <sal.h>  // must come after windows headers.
// clang-format on
#endif  // defined(WEBRTC_WIN)

#if defined(WEBRTC_POSIX)
#include <pthread.h>
#endif

// See notes in the 'Performance' unit test for the effects of this flag.
#define USE_NATIVE_MUTEX_ON_MAC 0

#if defined(WEBRTC_MAC) && !USE_NATIVE_MUTEX_ON_MAC
#include <dispatch/dispatch.h>
#endif

#define CS_DEBUG_CHECKS RTC_DCHECK_IS_ON

#if CS_DEBUG_CHECKS
#define CS_DEBUG_CODE(x) x
#else  // !CS_DEBUG_CHECKS
#define CS_DEBUG_CODE(x)
#endif  // !CS_DEBUG_CHECKS

namespace rtc {

// Locking methods (Enter, TryEnter, Leave)are const to permit protecting
// members inside a const context without requiring mutable CriticalSections
// everywhere.
class RTC_LOCKABLE CriticalSection {
 public:
  CriticalSection();
  ~CriticalSection();

  void Enter() const RTC_EXCLUSIVE_LOCK_FUNCTION();
  bool TryEnter() const RTC_EXCLUSIVE_TRYLOCK_FUNCTION(true);
  void Leave() const RTC_UNLOCK_FUNCTION();

 private:
  // Use only for RTC_DCHECKing.
  bool CurrentThreadIsOwner() const;

#if defined(WEBRTC_WIN)
  mutable CRITICAL_SECTION crit_;
#elif defined(WEBRTC_POSIX)
#if defined(WEBRTC_MAC) && !USE_NATIVE_MUTEX_ON_MAC
  // Number of times the lock has been locked + number of threads waiting.
  // TODO(tommi): We could use this number and subtract the recursion count
  // to find places where we have multiple threads contending on the same lock.
  mutable volatile int lock_queue_;
  // |recursion_| represents the recursion count + 1 for the thread that owns
  // the lock. Only modified by the thread that owns the lock.
  mutable int recursion_;
  // Used to signal a single waiting thread when the lock becomes available.
  mutable dispatch_semaphore_t semaphore_;
  // The thread that currently holds the lock. Required to handle recursion.
  mutable PlatformThreadRef owning_thread_;
#else
  mutable pthread_mutex_t mutex_;
#endif
  mutable PlatformThreadRef thread_;  // Only used by RTC_DCHECKs.
  mutable int recursion_count_;       // Only used by RTC_DCHECKs.
#else  // !defined(WEBRTC_WIN) && !defined(WEBRTC_POSIX)
#error Unsupported platform.
#endif
};

// CritScope, for serializing execution through a scope.
class RTC_SCOPED_LOCKABLE CritScope {
 public:
  explicit CritScope(const CriticalSection* cs) RTC_EXCLUSIVE_LOCK_FUNCTION(cs);
  ~CritScope() RTC_UNLOCK_FUNCTION();

 private:
  const CriticalSection* const cs_;
  RTC_DISALLOW_COPY_AND_ASSIGN(CritScope);
};

// Tries to lock a critical section on construction via
// CriticalSection::TryEnter, and unlocks on destruction if the
// lock was taken. Never blocks.
//
// IMPORTANT: Unlike CritScope, the lock may not be owned by this thread in
// subsequent code. Users *must* check locked() to determine if the
// lock was taken. If you're not calling locked(), you're doing it wrong!
class TryCritScope {
 public:
  explicit TryCritScope(const CriticalSection* cs);
  ~TryCritScope();
#if defined(WEBRTC_WIN)
  _Check_return_ bool locked() const;
#elif defined(WEBRTC_POSIX)
  bool locked() const __attribute__((__warn_unused_result__));
#else  // !defined(WEBRTC_WIN) && !defined(WEBRTC_POSIX)
#error Unsupported platform.
#endif
 private:
  const CriticalSection* const cs_;
  const bool locked_;
  mutable bool lock_was_called_;  // Only used by RTC_DCHECKs.
  RTC_DISALLOW_COPY_AND_ASSIGN(TryCritScope);
};

// A POD lock used to protect global variables. Do NOT use for other purposes.
// No custom constructor or private data member should be added.
class RTC_LOCKABLE GlobalLockPod {
 public:
  void Lock() RTC_EXCLUSIVE_LOCK_FUNCTION();

  void Unlock() RTC_UNLOCK_FUNCTION();

  volatile int lock_acquired;
};

class GlobalLock : public GlobalLockPod {
 public:
  GlobalLock();
};

// GlobalLockScope, for serializing execution through a scope.
class RTC_SCOPED_LOCKABLE GlobalLockScope {
 public:
  explicit GlobalLockScope(GlobalLockPod* lock)
      RTC_EXCLUSIVE_LOCK_FUNCTION(lock);
  ~GlobalLockScope() RTC_UNLOCK_FUNCTION();

 private:
  GlobalLockPod* const lock_;
  RTC_DISALLOW_COPY_AND_ASSIGN(GlobalLockScope);
};

}  // namespace rtc

#endif  // RTC_BASE_CRITICALSECTION_H_
