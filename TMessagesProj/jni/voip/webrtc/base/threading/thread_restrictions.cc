// Copyright (c) 2012 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

#include "base/threading/thread_restrictions.h"

#include "base/trace_event/trace_event.h"

#if DCHECK_IS_ON()

#include "base/debug/stack_trace.h"
#include "base/lazy_instance.h"
#include "base/logging.h"
#include "base/threading/thread_local.h"
#include "build/build_config.h"

namespace base {

std::ostream& operator<<(std::ostream&out, const ThreadLocalBoolean& tl) {
  out << "currently set to " << (tl.Get() ? "true" : "false");
  return out;
}

namespace {

#if defined(OS_NACL) || defined(OS_ANDROID)
// NaCL doesn't support stack sampling and Android is slow at stack
// sampling and this causes timeouts (crbug.com/959139).
using ThreadLocalBooleanWithStacks = ThreadLocalBoolean;
#else
class ThreadLocalBooleanWithStacks {
 public:
  ThreadLocalBooleanWithStacks() = default;

  bool Get() const { return bool_.Get(); }

  void Set(bool val) {
    stack_.Set(std::make_unique<debug::StackTrace>());
    bool_.Set(val);
  }

  friend std::ostream& operator<<(std::ostream& out,
                                  const ThreadLocalBooleanWithStacks& tl) {
    out << tl.bool_ << " by ";

    if (!tl.stack_.Get())
      return out << "default value\n";
    out << "\n";
    tl.stack_.Get()->OutputToStream(&out);
    return out;
  }

 private:
  ThreadLocalBoolean bool_;
  ThreadLocalOwnedPointer<debug::StackTrace> stack_;

  DISALLOW_COPY_AND_ASSIGN(ThreadLocalBooleanWithStacks);
};
#endif  // defined(OS_NACL)

LazyInstance<ThreadLocalBooleanWithStacks>::Leaky g_blocking_disallowed =
    LAZY_INSTANCE_INITIALIZER;

LazyInstance<ThreadLocalBooleanWithStacks>::Leaky g_singleton_disallowed =
    LAZY_INSTANCE_INITIALIZER;

LazyInstance<ThreadLocalBooleanWithStacks>::Leaky
    g_base_sync_primitives_disallowed = LAZY_INSTANCE_INITIALIZER;

LazyInstance<ThreadLocalBooleanWithStacks>::Leaky
    g_cpu_intensive_work_disallowed = LAZY_INSTANCE_INITIALIZER;

}  // namespace

namespace internal {

void AssertBlockingAllowed() {
  DCHECK(!g_blocking_disallowed.Get().Get())
      << "Function marked as blocking was called from a scope that disallows "
         "blocking! If this task is running inside the ThreadPool, it needs "
         "to have MayBlock() in its TaskTraits. Otherwise, consider making "
         "this blocking work asynchronous or, as a last resort, you may use "
         "ScopedAllowBlocking (see its documentation for best practices).\n"
      << "g_blocking_disallowed " << g_blocking_disallowed.Get();
}

}  // namespace internal

void DisallowBlocking() {
  g_blocking_disallowed.Get().Set(true);
}

ScopedDisallowBlocking::ScopedDisallowBlocking()
    : was_disallowed_(g_blocking_disallowed.Get().Get()) {
  g_blocking_disallowed.Get().Set(true);
}

ScopedDisallowBlocking::~ScopedDisallowBlocking() {
  DCHECK(g_blocking_disallowed.Get().Get());
  g_blocking_disallowed.Get().Set(was_disallowed_);
}

void DisallowBaseSyncPrimitives() {
  g_base_sync_primitives_disallowed.Get().Set(true);
}

ScopedAllowBaseSyncPrimitives::ScopedAllowBaseSyncPrimitives()
    : was_disallowed_(g_base_sync_primitives_disallowed.Get().Get()) {
  DCHECK(!g_blocking_disallowed.Get().Get())
      << "To allow //base sync primitives in a scope where blocking is "
         "disallowed use ScopedAllowBaseSyncPrimitivesOutsideBlockingScope.\n"
      << "g_blocking_disallowed " << g_blocking_disallowed.Get();
  g_base_sync_primitives_disallowed.Get().Set(false);
}

ScopedAllowBaseSyncPrimitives::~ScopedAllowBaseSyncPrimitives() {
  DCHECK(!g_base_sync_primitives_disallowed.Get().Get());
  g_base_sync_primitives_disallowed.Get().Set(was_disallowed_);
}

ScopedAllowBaseSyncPrimitivesForTesting::
    ScopedAllowBaseSyncPrimitivesForTesting()
    : was_disallowed_(g_base_sync_primitives_disallowed.Get().Get()) {
  g_base_sync_primitives_disallowed.Get().Set(false);
}

ScopedAllowBaseSyncPrimitivesForTesting::
    ~ScopedAllowBaseSyncPrimitivesForTesting() {
  DCHECK(!g_base_sync_primitives_disallowed.Get().Get());
  g_base_sync_primitives_disallowed.Get().Set(was_disallowed_);
}

ScopedAllowUnresponsiveTasksForTesting::ScopedAllowUnresponsiveTasksForTesting()
    : was_disallowed_base_sync_(g_base_sync_primitives_disallowed.Get().Get()),
      was_disallowed_blocking_(g_blocking_disallowed.Get().Get()),
      was_disallowed_cpu_(g_cpu_intensive_work_disallowed.Get().Get()) {
  g_base_sync_primitives_disallowed.Get().Set(false);
  g_blocking_disallowed.Get().Set(false);
  g_cpu_intensive_work_disallowed.Get().Set(false);
}

ScopedAllowUnresponsiveTasksForTesting::
    ~ScopedAllowUnresponsiveTasksForTesting() {
  DCHECK(!g_base_sync_primitives_disallowed.Get().Get());
  DCHECK(!g_blocking_disallowed.Get().Get());
  DCHECK(!g_cpu_intensive_work_disallowed.Get().Get());
  g_base_sync_primitives_disallowed.Get().Set(was_disallowed_base_sync_);
  g_blocking_disallowed.Get().Set(was_disallowed_blocking_);
  g_cpu_intensive_work_disallowed.Get().Set(was_disallowed_cpu_);
}

namespace internal {

void AssertBaseSyncPrimitivesAllowed() {
  DCHECK(!g_base_sync_primitives_disallowed.Get().Get())
      << "Waiting on a //base sync primitive is not allowed on this thread to "
         "prevent jank and deadlock. If waiting on a //base sync primitive is "
         "unavoidable, do it within the scope of a "
         "ScopedAllowBaseSyncPrimitives. If in a test, "
         "use ScopedAllowBaseSyncPrimitivesForTesting.\n"
      << "g_base_sync_primitives_disallowed "
      << g_base_sync_primitives_disallowed.Get()
      << "It can be useful to know that g_blocking_disallowed is "
      << g_blocking_disallowed.Get();
}

void ResetThreadRestrictionsForTesting() {
  g_blocking_disallowed.Get().Set(false);
  g_singleton_disallowed.Get().Set(false);
  g_base_sync_primitives_disallowed.Get().Set(false);
  g_cpu_intensive_work_disallowed.Get().Set(false);
}

}  // namespace internal

void AssertLongCPUWorkAllowed() {
  DCHECK(!g_cpu_intensive_work_disallowed.Get().Get())
      << "Function marked as CPU intensive was called from a scope that "
         "disallows this kind of work! Consider making this work "
         "asynchronous.\n"
      << "g_cpu_intensive_work_disallowed "
      << g_cpu_intensive_work_disallowed.Get();
}

void DisallowUnresponsiveTasks() {
  DisallowBlocking();
  DisallowBaseSyncPrimitives();
  g_cpu_intensive_work_disallowed.Get().Set(true);
}

// static
bool ThreadRestrictions::SetIOAllowed(bool allowed) {
  bool previous_disallowed = g_blocking_disallowed.Get().Get();
  g_blocking_disallowed.Get().Set(!allowed);
  return !previous_disallowed;
}

// static
bool ThreadRestrictions::SetSingletonAllowed(bool allowed) {
  bool previous_disallowed = g_singleton_disallowed.Get().Get();
  g_singleton_disallowed.Get().Set(!allowed);
  return !previous_disallowed;
}

// static
void ThreadRestrictions::AssertSingletonAllowed() {
  DCHECK(!g_singleton_disallowed.Get().Get())
      << "LazyInstance/Singleton is not allowed to be used on this thread. "
         "Most likely it's because this thread is not joinable (or the current "
         "task is running with TaskShutdownBehavior::CONTINUE_ON_SHUTDOWN "
         "semantics), so AtExitManager may have deleted the object on "
         "shutdown, leading to a potential shutdown crash. If you need to use "
         "the object from this context, it'll have to be updated to use Leaky "
         "traits.\n"
      << "g_singleton_disallowed " << g_singleton_disallowed.Get();
}

// static
void ThreadRestrictions::DisallowWaiting() {
  DisallowBaseSyncPrimitives();
}

bool ThreadRestrictions::SetWaitAllowed(bool allowed) {
  bool previous_disallowed = g_base_sync_primitives_disallowed.Get().Get();
  g_base_sync_primitives_disallowed.Get().Set(!allowed);
  return !previous_disallowed;
}

}  // namespace base

#endif  // DCHECK_IS_ON()

namespace base {

ScopedAllowBlocking::ScopedAllowBlocking(const Location& from_here)
#if DCHECK_IS_ON()
    : was_disallowed_(g_blocking_disallowed.Get().Get())
#endif
{
  TRACE_EVENT_BEGIN2("base", "ScopedAllowBlocking", "file_name",
                     from_here.file_name(), "function_name",
                     from_here.function_name());

#if DCHECK_IS_ON()
  g_blocking_disallowed.Get().Set(false);
#endif
}

ScopedAllowBlocking::~ScopedAllowBlocking() {
  TRACE_EVENT_END0("base", "ScopedAllowBlocking");

#if DCHECK_IS_ON()
  DCHECK(!g_blocking_disallowed.Get().Get());
  g_blocking_disallowed.Get().Set(was_disallowed_);
#endif
}

ScopedAllowBaseSyncPrimitivesOutsideBlockingScope::
    ScopedAllowBaseSyncPrimitivesOutsideBlockingScope(const Location& from_here)
#if DCHECK_IS_ON()
    : was_disallowed_(g_base_sync_primitives_disallowed.Get().Get())
#endif
{
  TRACE_EVENT_BEGIN2(
      "base", "ScopedAllowBaseSyncPrimitivesOutsideBlockingScope", "file_name",
      from_here.file_name(), "function_name", from_here.function_name());

#if DCHECK_IS_ON()
  g_base_sync_primitives_disallowed.Get().Set(false);
#endif
}

ScopedAllowBaseSyncPrimitivesOutsideBlockingScope::
    ~ScopedAllowBaseSyncPrimitivesOutsideBlockingScope() {
  TRACE_EVENT_END0("base", "ScopedAllowBaseSyncPrimitivesOutsideBlockingScope");

#if DCHECK_IS_ON()
  DCHECK(!g_base_sync_primitives_disallowed.Get().Get());
  g_base_sync_primitives_disallowed.Get().Set(was_disallowed_);
#endif
}

ThreadRestrictions::ScopedAllowIO::ScopedAllowIO(const Location& from_here)
#if DCHECK_IS_ON()
    : was_allowed_(SetIOAllowed(true))
#endif
{
  TRACE_EVENT_BEGIN2("base", "ScopedAllowIO", "file_name",
                     from_here.file_name(), "function_name",
                     from_here.function_name());
}

ThreadRestrictions::ScopedAllowIO::~ScopedAllowIO() {
  TRACE_EVENT_END0("base", "ScopedAllowIO");

#if DCHECK_IS_ON()
  SetIOAllowed(was_allowed_);
#endif
}

}  // namespace base
