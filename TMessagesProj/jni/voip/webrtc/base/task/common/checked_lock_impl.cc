// Copyright 2016 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

#include "base/task/common/checked_lock_impl.h"

#include <algorithm>
#include <unordered_map>
#include <vector>

#include "base/lazy_instance.h"
#include "base/logging.h"
#include "base/synchronization/condition_variable.h"
#include "base/task/common/checked_lock.h"
#include "base/threading/platform_thread.h"
#include "base/threading/thread_local.h"

namespace base {
namespace internal {

namespace {

class SafeAcquisitionTracker {
 public:
  SafeAcquisitionTracker() = default;

  void RegisterLock(const CheckedLockImpl* const lock,
                    const CheckedLockImpl* const predecessor) {
    DCHECK_NE(lock, predecessor) << "Reentrant locks are unsupported.";
    AutoLock auto_lock(allowed_predecessor_map_lock_);
    allowed_predecessor_map_[lock] = predecessor;
    AssertSafePredecessor(lock);
  }

  void UnregisterLock(const CheckedLockImpl* const lock) {
    AutoLock auto_lock(allowed_predecessor_map_lock_);
    allowed_predecessor_map_.erase(lock);
  }

  void RecordAcquisition(const CheckedLockImpl* const lock) {
    AssertSafeAcquire(lock);
    GetAcquiredLocksOnCurrentThread()->push_back(lock);
  }

  void RecordRelease(const CheckedLockImpl* const lock) {
    LockVector* acquired_locks = GetAcquiredLocksOnCurrentThread();
    const auto iter_at_lock =
        std::find(acquired_locks->begin(), acquired_locks->end(), lock);
    DCHECK(iter_at_lock != acquired_locks->end());
    acquired_locks->erase(iter_at_lock);
  }

  void AssertNoLockHeldOnCurrentThread() {
    DCHECK(GetAcquiredLocksOnCurrentThread()->empty());
  }

 private:
  using LockVector = std::vector<const CheckedLockImpl*>;
  using PredecessorMap =
      std::unordered_map<const CheckedLockImpl*, const CheckedLockImpl*>;

  // This asserts that the lock is safe to acquire. This means that this should
  // be run before actually recording the acquisition.
  void AssertSafeAcquire(const CheckedLockImpl* const lock) {
    const LockVector* acquired_locks = GetAcquiredLocksOnCurrentThread();

    // If the thread currently holds no locks, this is inherently safe.
    if (acquired_locks->empty())
      return;

    // A universal predecessor may not be acquired after any other lock.
    DCHECK(!lock->is_universal_predecessor());

    // Otherwise, make sure that the previous lock acquired is either an
    // allowed predecessor for this lock or a universal predecessor.
    const CheckedLockImpl* previous_lock = acquired_locks->back();
    if (previous_lock->is_universal_predecessor())
      return;

    AutoLock auto_lock(allowed_predecessor_map_lock_);
    // Using at() is exception-safe here as |lock| was registered already.
    const CheckedLockImpl* allowed_predecessor =
        allowed_predecessor_map_.at(lock);
    DCHECK_EQ(previous_lock, allowed_predecessor);
  }

  // Asserts that |lock|'s registered predecessor is safe. Because
  // CheckedLocks are registered at construction time and any predecessor
  // specified on a CheckedLock must already exist, the first registered
  // CheckedLock in a potential chain must have a null predecessor and is thus
  // cycle-free. Any subsequent CheckedLock with a predecessor must come from
  // the set of registered CheckedLocks. Since the registered CheckedLocks
  // only contain cycle-free CheckedLocks, this subsequent CheckedLock is
  // itself cycle-free and may be safely added to the registered CheckedLock
  // set.
  void AssertSafePredecessor(const CheckedLockImpl* lock) const {
    allowed_predecessor_map_lock_.AssertAcquired();
    // Using at() is exception-safe here as |lock| was registered already.
    const CheckedLockImpl* predecessor = allowed_predecessor_map_.at(lock);
    if (predecessor) {
      DCHECK(allowed_predecessor_map_.find(predecessor) !=
             allowed_predecessor_map_.end())
          << "CheckedLock was registered before its predecessor. "
          << "Potential cycle detected";
    }
  }

  LockVector* GetAcquiredLocksOnCurrentThread() {
    if (!tls_acquired_locks_.Get())
      tls_acquired_locks_.Set(std::make_unique<LockVector>());

    return tls_acquired_locks_.Get();
  }

  // Synchronizes access to |allowed_predecessor_map_|.
  Lock allowed_predecessor_map_lock_;

  // A map of allowed predecessors.
  PredecessorMap allowed_predecessor_map_;

  // A thread-local slot holding a vector of locks currently acquired on the
  // current thread.
  ThreadLocalOwnedPointer<LockVector> tls_acquired_locks_;

  DISALLOW_COPY_AND_ASSIGN(SafeAcquisitionTracker);
};

LazyInstance<SafeAcquisitionTracker>::Leaky g_safe_acquisition_tracker =
    LAZY_INSTANCE_INITIALIZER;

}  // namespace

CheckedLockImpl::CheckedLockImpl() : CheckedLockImpl(nullptr) {}

CheckedLockImpl::CheckedLockImpl(const CheckedLockImpl* predecessor)
    : is_universal_predecessor_(false) {
  g_safe_acquisition_tracker.Get().RegisterLock(this, predecessor);
}

CheckedLockImpl::CheckedLockImpl(UniversalPredecessor)
    : is_universal_predecessor_(true) {}

CheckedLockImpl::~CheckedLockImpl() {
  g_safe_acquisition_tracker.Get().UnregisterLock(this);
}

void CheckedLockImpl::AssertNoLockHeldOnCurrentThread() {
  g_safe_acquisition_tracker.Get().AssertNoLockHeldOnCurrentThread();
}

void CheckedLockImpl::Acquire() {
  lock_.Acquire();
  g_safe_acquisition_tracker.Get().RecordAcquisition(this);
}

void CheckedLockImpl::Release() {
  lock_.Release();
  g_safe_acquisition_tracker.Get().RecordRelease(this);
}

void CheckedLockImpl::AssertAcquired() const {
  lock_.AssertAcquired();
}

std::unique_ptr<ConditionVariable> CheckedLockImpl::CreateConditionVariable() {
  return std::make_unique<ConditionVariable>(&lock_);
}

}  // namespace internal
}  // namespace base
