// Copyright (c) 2012 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

#ifndef BASE_SEQUENCE_CHECKER_IMPL_H_
#define BASE_SEQUENCE_CHECKER_IMPL_H_

#include <memory>

#include "base/base_export.h"
#include "base/compiler_specific.h"
#include "base/macros.h"
#include "base/synchronization/lock.h"
#include "base/thread_annotations.h"

namespace base {

// Real implementation of SequenceChecker for use in debug mode or for temporary
// use in release mode (e.g. to CHECK on a threading issue seen only in the
// wild).
//
// Note: You should almost always use the SequenceChecker class to get the right
// version for your build configuration.
// Note: This is only a check, not a "lock". It is marked "LOCKABLE" only in
// order to support thread_annotations.h.
class LOCKABLE BASE_EXPORT SequenceCheckerImpl {
 public:
  SequenceCheckerImpl();
  ~SequenceCheckerImpl();

  // Allow move construct/assign. This must be called on |other|'s associated
  // sequence and assignment can only be made into a SequenceCheckerImpl which
  // is detached or already associated with the current sequence. This isn't
  // thread-safe (|this| and |other| shouldn't be in use while this move is
  // performed). If the assignment was legal, the resulting SequenceCheckerImpl
  // will be bound to the current sequence and |other| will be detached.
  SequenceCheckerImpl(SequenceCheckerImpl&& other);
  SequenceCheckerImpl& operator=(SequenceCheckerImpl&& other);

  // Returns true if called in sequence with previous calls to this method and
  // the constructor.
  bool CalledOnValidSequence() const WARN_UNUSED_RESULT;

  // Unbinds the checker from the currently associated sequence. The checker
  // will be re-bound on the next call to CalledOnValidSequence().
  void DetachFromSequence();

 private:
  class Core;

  // Calls straight to ThreadLocalStorage::HasBeenDestroyed(). Exposed purely
  // for 'friend' to work.
  static bool HasThreadLocalStorageBeenDestroyed();

  mutable Lock lock_;
  mutable std::unique_ptr<Core> core_ GUARDED_BY(lock_);

  DISALLOW_COPY_AND_ASSIGN(SequenceCheckerImpl);
};

}  // namespace base

#endif  // BASE_SEQUENCE_CHECKER_IMPL_H_
