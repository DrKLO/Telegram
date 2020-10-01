// Copyright (c) 2012 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

#include "base/sequence_checker_impl.h"

#include "base/logging.h"
#include "base/memory/ptr_util.h"
#include "base/sequence_token.h"
#include "base/threading/thread_checker_impl.h"
#include "base/threading/thread_local_storage.h"

namespace base {

class SequenceCheckerImpl::Core {
 public:
  Core() : sequence_token_(SequenceToken::GetForCurrentThread()) {}

  ~Core() = default;

  bool CalledOnValidSequence() const {
    // SequenceToken::GetForCurrentThread() accesses thread-local storage.
    // During destruction the state of thread-local storage is not guaranteed to
    // be in a consistent state. Further, task-runner only installs the
    // SequenceToken when running a task. For this reason, |sequence_token_| is
    // not checked during thread destruction.
    if (!SequenceCheckerImpl::HasThreadLocalStorageBeenDestroyed() &&
        sequence_token_.IsValid()) {
      return sequence_token_ == SequenceToken::GetForCurrentThread();
    }

    // SequenceChecker behaves as a ThreadChecker when it is not bound to a
    // valid sequence token.
    return thread_checker_.CalledOnValidThread();
  }

 private:
  SequenceToken sequence_token_;

  // Used when |sequence_token_| is invalid, or during thread destruction.
  ThreadCheckerImpl thread_checker_;
};

SequenceCheckerImpl::SequenceCheckerImpl() : core_(std::make_unique<Core>()) {}
SequenceCheckerImpl::~SequenceCheckerImpl() = default;

SequenceCheckerImpl::SequenceCheckerImpl(SequenceCheckerImpl&& other) {
  // Verify that |other| is called on its associated sequence and bind it now if
  // it is currently detached (even if this isn't a DCHECK build).
  const bool other_called_on_valid_sequence = other.CalledOnValidSequence();
  DCHECK(other_called_on_valid_sequence);

  core_ = std::move(other.core_);
}

SequenceCheckerImpl& SequenceCheckerImpl::operator=(
    SequenceCheckerImpl&& other) {
  // If |this| is not in a detached state it needs to be bound to the current
  // sequence.
  DCHECK(CalledOnValidSequence());

  // Verify that |other| is called on its associated sequence and bind it now if
  // it is currently detached (even if this isn't a DCHECK build).
  const bool other_called_on_valid_sequence = other.CalledOnValidSequence();
  DCHECK(other_called_on_valid_sequence);

  // Intentionally not using either |lock_| in this method to let TSAN catch
  // racy assign.
  TS_UNCHECKED_READ(core_) = std::move(TS_UNCHECKED_READ(other.core_));

  return *this;
}

bool SequenceCheckerImpl::CalledOnValidSequence() const {
  AutoLock auto_lock(lock_);
  if (!core_)
    core_ = std::make_unique<Core>();
  return core_->CalledOnValidSequence();
}

void SequenceCheckerImpl::DetachFromSequence() {
  AutoLock auto_lock(lock_);
  core_.reset();
}

// static
bool SequenceCheckerImpl::HasThreadLocalStorageBeenDestroyed() {
  return ThreadLocalStorage::HasBeenDestroyed();
}

}  // namespace base
