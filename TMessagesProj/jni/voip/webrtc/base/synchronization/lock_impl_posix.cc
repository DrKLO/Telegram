// Copyright (c) 2011 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

#include "base/synchronization/lock_impl.h"

#include <string>

#include "base/debug/activity_tracker.h"
#include "base/logging.h"
#include "base/posix/safe_strerror.h"
#include "base/strings/stringprintf.h"
#include "base/synchronization/lock.h"
#include "base/synchronization/synchronization_buildflags.h"
#include "build/build_config.h"

namespace base {
namespace internal {

namespace {

#if DCHECK_IS_ON()
const char* AdditionalHintForSystemErrorCode(int error_code) {
  switch (error_code) {
    case EINVAL:
      return "Hint: This is often related to a use-after-free.";
    default:
      return "";
  }
}
#endif  // DCHECK_IS_ON()

std::string SystemErrorCodeToString(int error_code) {
#if DCHECK_IS_ON()
  return base::safe_strerror(error_code) + ". " +
         AdditionalHintForSystemErrorCode(error_code);
#else   // DCHECK_IS_ON()
  return std::string();
#endif  // DCHECK_IS_ON()
}

}  // namespace

// Determines which platforms can consider using priority inheritance locks. Use
// this define for platform code that may not compile if priority inheritance
// locks aren't available. For this platform code,
// PRIORITY_INHERITANCE_LOCKS_POSSIBLE() is a necessary but insufficient check.
// Lock::PriorityInheritanceAvailable still must be checked as the code may
// compile but the underlying platform still may not correctly support priority
// inheritance locks.
#if defined(OS_NACL) || defined(OS_ANDROID) || defined(OS_FUCHSIA)
#define PRIORITY_INHERITANCE_LOCKS_POSSIBLE() 0
#else
#define PRIORITY_INHERITANCE_LOCKS_POSSIBLE() 1
#endif

LockImpl::LockImpl() {
  pthread_mutexattr_t mta;
  int rv = pthread_mutexattr_init(&mta);
  DCHECK_EQ(rv, 0) << ". " << SystemErrorCodeToString(rv);
#if PRIORITY_INHERITANCE_LOCKS_POSSIBLE()
  if (PriorityInheritanceAvailable()) {
    rv = pthread_mutexattr_setprotocol(&mta, PTHREAD_PRIO_INHERIT);
    DCHECK_EQ(rv, 0) << ". " << SystemErrorCodeToString(rv);
  }
#endif
#ifndef NDEBUG
  // In debug, setup attributes for lock error checking.
  rv = pthread_mutexattr_settype(&mta, PTHREAD_MUTEX_ERRORCHECK);
  DCHECK_EQ(rv, 0) << ". " << SystemErrorCodeToString(rv);
#endif
  rv = pthread_mutex_init(&native_handle_, &mta);
  DCHECK_EQ(rv, 0) << ". " << SystemErrorCodeToString(rv);
  rv = pthread_mutexattr_destroy(&mta);
  DCHECK_EQ(rv, 0) << ". " << SystemErrorCodeToString(rv);
}

LockImpl::~LockImpl() {
  int rv = pthread_mutex_destroy(&native_handle_);
  DCHECK_EQ(rv, 0) << ". " << SystemErrorCodeToString(rv);
}

bool LockImpl::Try() {
  int rv = pthread_mutex_trylock(&native_handle_);
  DCHECK(rv == 0 || rv == EBUSY) << ". " << SystemErrorCodeToString(rv);
  return rv == 0;
}

void LockImpl::Lock() {
  // The ScopedLockAcquireActivity below is relatively expensive and so its
  // actions can become significant due to the very large number of locks
  // that tend to be used throughout the build. To avoid this cost in the
  // vast majority of the calls, simply "try" the lock first and only do the
  // (tracked) blocking call if that fails. Since "try" itself is a system
  // call, and thus also somewhat expensive, don't bother with it unless
  // tracking is actually enabled.
  if (base::debug::GlobalActivityTracker::IsEnabled())
    if (Try())
      return;

  base::debug::ScopedLockAcquireActivity lock_activity(this);
  int rv = pthread_mutex_lock(&native_handle_);
  DCHECK_EQ(rv, 0) << ". " << SystemErrorCodeToString(rv);
}

// static
bool LockImpl::PriorityInheritanceAvailable() {
#if BUILDFLAG(ENABLE_MUTEX_PRIORITY_INHERITANCE)
  return true;
#elif PRIORITY_INHERITANCE_LOCKS_POSSIBLE() && defined(OS_MACOSX)
  return true;
#else
  // Security concerns prevent the use of priority inheritance mutexes on Linux.
  //   * CVE-2010-0622 - Linux < 2.6.33-rc7, wake_futex_pi possible DoS.
  //     https://cve.mitre.org/cgi-bin/cvename.cgi?name=CVE-2010-0622
  //   * CVE-2012-6647 - Linux < 3.5.1, futex_wait_requeue_pi possible DoS.
  //     https://cve.mitre.org/cgi-bin/cvename.cgi?name=CVE-2012-6647
  //   * CVE-2014-3153 - Linux <= 3.14.5, futex_requeue, privilege escalation.
  //     https://cve.mitre.org/cgi-bin/cvename.cgi?name=CVE-2014-3153
  //
  // If the above were all addressed, we still need a runtime check to deal with
  // the bug below.
  //   * glibc Bug 14652: https://sourceware.org/bugzilla/show_bug.cgi?id=14652
  //     Fixed in glibc 2.17.
  //     Priority inheritance mutexes may deadlock with condition variables
  //     during reacquisition of the mutex after the condition variable is
  //     signalled.
  return false;
#endif
}

}  // namespace internal
}  // namespace base
