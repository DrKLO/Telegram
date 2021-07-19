// Copyright 2020 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

#ifndef BASE_THREADING_SCOPED_BLOCKING_CALL_INTERNAL_H_
#define BASE_THREADING_SCOPED_BLOCKING_CALL_INTERNAL_H_

#include "base/base_export.h"
#include "base/debug/activity_tracker.h"
#include "base/macros.h"

namespace base {

enum class BlockingType;

// Implementation details of types in scoped_blocking_call.h and classes for a
// few key //base types to observe and react to blocking calls.
namespace internal {

// Interface for an observer to be informed when a thread enters or exits
// the scope of ScopedBlockingCall objects.
class BASE_EXPORT BlockingObserver {
 public:
  virtual ~BlockingObserver() = default;

  // Invoked when a ScopedBlockingCall is instantiated on the observed thread
  // where there wasn't an existing ScopedBlockingCall.
  virtual void BlockingStarted(BlockingType blocking_type) = 0;

  // Invoked when a WILL_BLOCK ScopedBlockingCall is instantiated on the
  // observed thread where there was a MAY_BLOCK ScopedBlockingCall but not a
  // WILL_BLOCK ScopedBlockingCall.
  virtual void BlockingTypeUpgraded() = 0;

  // Invoked when the last ScopedBlockingCall on the observed thread is
  // destroyed.
  virtual void BlockingEnded() = 0;
};

// Registers |blocking_observer| on the current thread. It is invalid to call
// this on a thread where there is an active ScopedBlockingCall.
BASE_EXPORT void SetBlockingObserverForCurrentThread(
    BlockingObserver* blocking_observer);

BASE_EXPORT void ClearBlockingObserverForCurrentThread();

// Common implementation class for both ScopedBlockingCall and
// ScopedBlockingCallWithBaseSyncPrimitives without assertions.
class BASE_EXPORT UncheckedScopedBlockingCall {
 public:
  explicit UncheckedScopedBlockingCall(const Location& from_here,
                                       BlockingType blocking_type);
  ~UncheckedScopedBlockingCall();

 private:
  internal::BlockingObserver* const blocking_observer_;

  // Previous ScopedBlockingCall instantiated on this thread.
  UncheckedScopedBlockingCall* const previous_scoped_blocking_call_;

  // Whether the BlockingType of the current thread was WILL_BLOCK after this
  // ScopedBlockingCall was instantiated.
  const bool is_will_block_;

  base::debug::ScopedActivity scoped_activity_;

  DISALLOW_COPY_AND_ASSIGN(UncheckedScopedBlockingCall);
};

}  // namespace internal
}  // namespace base

#endif  // BASE_THREADING_SCOPED_BLOCKING_CALL_INTERNAL_H_
