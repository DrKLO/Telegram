// Copyright 2020 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

#include "base/threading/scoped_blocking_call_internal.h"

#include "base/lazy_instance.h"
#include "base/logging.h"
#include "base/scoped_clear_last_error.h"
#include "base/threading/scoped_blocking_call.h"
#include "base/threading/thread_local.h"
#include "build/build_config.h"

namespace base {
namespace internal {

namespace {

// The first 8 characters of sha1 of "ScopedBlockingCall".
// echo -n "ScopedBlockingCall" | sha1sum
constexpr uint32_t kActivityTrackerId = 0x11be9915;

LazyInstance<ThreadLocalPointer<internal::BlockingObserver>>::Leaky
    tls_blocking_observer = LAZY_INSTANCE_INITIALIZER;

// Last ScopedBlockingCall instantiated on this thread.
LazyInstance<ThreadLocalPointer<internal::UncheckedScopedBlockingCall>>::Leaky
    tls_last_scoped_blocking_call = LAZY_INSTANCE_INITIALIZER;

}  // namespace

void SetBlockingObserverForCurrentThread(BlockingObserver* blocking_observer) {
  DCHECK(!tls_blocking_observer.Get().Get());
  tls_blocking_observer.Get().Set(blocking_observer);
}

void ClearBlockingObserverForCurrentThread() {
  tls_blocking_observer.Get().Set(nullptr);
}

UncheckedScopedBlockingCall::UncheckedScopedBlockingCall(
    const Location& from_here,
    BlockingType blocking_type)
    : blocking_observer_(tls_blocking_observer.Get().Get()),
      previous_scoped_blocking_call_(tls_last_scoped_blocking_call.Get().Get()),
      is_will_block_(blocking_type == BlockingType::WILL_BLOCK ||
                     (previous_scoped_blocking_call_ &&
                      previous_scoped_blocking_call_->is_will_block_)),
      scoped_activity_(from_here, 0, kActivityTrackerId, 0) {
  tls_last_scoped_blocking_call.Get().Set(this);

  if (blocking_observer_) {
    if (!previous_scoped_blocking_call_) {
      blocking_observer_->BlockingStarted(blocking_type);
    } else if (blocking_type == BlockingType::WILL_BLOCK &&
               !previous_scoped_blocking_call_->is_will_block_) {
      blocking_observer_->BlockingTypeUpgraded();
    }
  }

  if (scoped_activity_.IsRecorded()) {
    // Also record the data for extended crash reporting.
    const base::TimeTicks now = base::TimeTicks::Now();
    auto& user_data = scoped_activity_.user_data();
    user_data.SetUint("timestamp_us", now.since_origin().InMicroseconds());
    user_data.SetUint("blocking_type", static_cast<uint64_t>(blocking_type));
  }
}

UncheckedScopedBlockingCall::~UncheckedScopedBlockingCall() {
  // TLS affects result of GetLastError() on Windows. ScopedClearLastError
  // prevents side effect.
  base::internal::ScopedClearLastError save_last_error;
  DCHECK_EQ(this, tls_last_scoped_blocking_call.Get().Get());
  tls_last_scoped_blocking_call.Get().Set(previous_scoped_blocking_call_);
  if (blocking_observer_ && !previous_scoped_blocking_call_)
    blocking_observer_->BlockingEnded();
}

}  // namespace internal
}  // namespace base
