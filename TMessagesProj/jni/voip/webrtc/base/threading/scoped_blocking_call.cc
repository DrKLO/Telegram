// Copyright 2017 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

#include "base/threading/scoped_blocking_call.h"

#include "base/lazy_instance.h"
#include "base/threading/thread_local.h"
#include "base/threading/thread_restrictions.h"
#include "base/time/time.h"
#include "base/trace_event/trace_event.h"
#include "build/build_config.h"

namespace base {

namespace {

#if DCHECK_IS_ON()
// Used to verify that the trace events used in the constructor do not result in
// instantiating a ScopedBlockingCall themselves (which would cause an infinite
// reentrancy loop).
LazyInstance<ThreadLocalBoolean>::Leaky tls_construction_in_progress =
    LAZY_INSTANCE_INITIALIZER;
#endif

}  // namespace

ScopedBlockingCall::ScopedBlockingCall(const Location& from_here,
                                       BlockingType blocking_type)
    : UncheckedScopedBlockingCall(from_here, blocking_type) {
#if DCHECK_IS_ON()
  DCHECK(!tls_construction_in_progress.Get().Get());
  tls_construction_in_progress.Get().Set(true);
#endif

  internal::AssertBlockingAllowed();
  TRACE_EVENT_BEGIN2("base", "ScopedBlockingCall", "file_name",
                     from_here.file_name(), "function_name",
                     from_here.function_name());
#if DCHECK_IS_ON()
  tls_construction_in_progress.Get().Set(false);
#endif
}

ScopedBlockingCall::~ScopedBlockingCall() {
  TRACE_EVENT_END0("base", "ScopedBlockingCall");
}

namespace internal {

ScopedBlockingCallWithBaseSyncPrimitives::
    ScopedBlockingCallWithBaseSyncPrimitives(const Location& from_here,
                                             BlockingType blocking_type)
    : UncheckedScopedBlockingCall(from_here, blocking_type) {
#if DCHECK_IS_ON()
  DCHECK(!tls_construction_in_progress.Get().Get());
  tls_construction_in_progress.Get().Set(true);
#endif

  internal::AssertBaseSyncPrimitivesAllowed();
  TRACE_EVENT_BEGIN2("base", "ScopedBlockingCallWithBaseSyncPrimitives",
                     "file_name", from_here.file_name(), "function_name",
                     from_here.function_name());

#if DCHECK_IS_ON()
  tls_construction_in_progress.Get().Set(false);
#endif
}

ScopedBlockingCallWithBaseSyncPrimitives::
    ~ScopedBlockingCallWithBaseSyncPrimitives() {
  TRACE_EVENT_END0("base", "ScopedBlockingCallWithBaseSyncPrimitives");
}

}  // namespace internal

}  // namespace base
