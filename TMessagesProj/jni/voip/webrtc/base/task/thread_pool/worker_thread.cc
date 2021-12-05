// Copyright 2016 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

#include "base/task/thread_pool/worker_thread.h"

#include <stddef.h>

#include <utility>

#include "base/compiler_specific.h"
#include "base/debug/alias.h"
#include "base/logging.h"
#include "base/task/thread_pool/environment_config.h"
#include "base/task/thread_pool/task_tracker.h"
#include "base/task/thread_pool/worker_thread_observer.h"
#include "base/threading/hang_watcher.h"
#include "base/time/time_override.h"
#include "base/trace_event/trace_event.h"

#if defined(OS_MACOSX)
#include "base/mac/scoped_nsautorelease_pool.h"
#endif

namespace base {
namespace internal {

void WorkerThread::Delegate::WaitForWork(WaitableEvent* wake_up_event) {
  DCHECK(wake_up_event);
  const TimeDelta sleep_time = GetSleepTimeout();
  if (sleep_time.is_max()) {
    // Calling TimedWait with TimeDelta::Max is not recommended per
    // http://crbug.com/465948.
    wake_up_event->Wait();
  } else {
    wake_up_event->TimedWait(sleep_time);
  }
}

WorkerThread::WorkerThread(ThreadPriority priority_hint,
                           std::unique_ptr<Delegate> delegate,
                           TrackedRef<TaskTracker> task_tracker,
                           const CheckedLock* predecessor_lock)
    : thread_lock_(predecessor_lock),
      delegate_(std::move(delegate)),
      task_tracker_(std::move(task_tracker)),
      priority_hint_(priority_hint),
      current_thread_priority_(GetDesiredThreadPriority()) {
  DCHECK(delegate_);
  DCHECK(task_tracker_);
  DCHECK(CanUseBackgroundPriorityForWorkerThread() ||
         priority_hint_ != ThreadPriority::BACKGROUND);
  wake_up_event_.declare_only_used_while_idle();
}

bool WorkerThread::Start(WorkerThreadObserver* worker_thread_observer) {
  CheckedLock::AssertNoLockHeldOnCurrentThread();
  CheckedAutoLock auto_lock(thread_lock_);
  DCHECK(thread_handle_.is_null());

  if (should_exit_.IsSet() || join_called_for_testing_.IsSet())
    return true;

  DCHECK(!worker_thread_observer_);
  worker_thread_observer_ = worker_thread_observer;

  self_ = this;

  constexpr size_t kDefaultStackSize = 0;
  PlatformThread::CreateWithPriority(kDefaultStackSize, this, &thread_handle_,
                                     current_thread_priority_);

  if (thread_handle_.is_null()) {
    self_ = nullptr;
    return false;
  }

  return true;
}

void WorkerThread::WakeUp() {
  // Signalling an event can deschedule the current thread. Since being
  // descheduled while holding a lock is undesirable (https://crbug.com/890978),
  // assert that no lock is held by the current thread.
  CheckedLock::AssertNoLockHeldOnCurrentThread();
  // Calling WakeUp() after Cleanup() or Join() is wrong because the
  // WorkerThread cannot run more tasks.
  DCHECK(!join_called_for_testing_.IsSet());
  DCHECK(!should_exit_.IsSet());
  wake_up_event_.Signal();
}

void WorkerThread::JoinForTesting() {
  DCHECK(!join_called_for_testing_.IsSet());
  join_called_for_testing_.Set();
  wake_up_event_.Signal();

  PlatformThreadHandle thread_handle;

  {
    CheckedAutoLock auto_lock(thread_lock_);

    if (thread_handle_.is_null())
      return;

    thread_handle = thread_handle_;
    // Reset |thread_handle_| so it isn't joined by the destructor.
    thread_handle_ = PlatformThreadHandle();
  }

  PlatformThread::Join(thread_handle);
}

bool WorkerThread::ThreadAliveForTesting() const {
  CheckedAutoLock auto_lock(thread_lock_);
  return !thread_handle_.is_null();
}

WorkerThread::~WorkerThread() {
  CheckedAutoLock auto_lock(thread_lock_);

  // If |thread_handle_| wasn't joined, detach it.
  if (!thread_handle_.is_null()) {
    DCHECK(!join_called_for_testing_.IsSet());
    PlatformThread::Detach(thread_handle_);
  }
}

void WorkerThread::Cleanup() {
  DCHECK(!should_exit_.IsSet());
  should_exit_.Set();
  wake_up_event_.Signal();
}

void WorkerThread::BeginUnusedPeriod() {
  CheckedAutoLock auto_lock(thread_lock_);
  DCHECK(last_used_time_.is_null());
  last_used_time_ = subtle::TimeTicksNowIgnoringOverride();
}

void WorkerThread::EndUnusedPeriod() {
  CheckedAutoLock auto_lock(thread_lock_);
  DCHECK(!last_used_time_.is_null());
  last_used_time_ = TimeTicks();
}

TimeTicks WorkerThread::GetLastUsedTime() const {
  CheckedAutoLock auto_lock(thread_lock_);
  return last_used_time_;
}

bool WorkerThread::ShouldExit() const {
  // The ordering of the checks is important below. This WorkerThread may be
  // released and outlive |task_tracker_| in unit tests. However, when the
  // WorkerThread is released, |should_exit_| will be set, so check that
  // first.
  return should_exit_.IsSet() || join_called_for_testing_.IsSet() ||
         task_tracker_->IsShutdownComplete();
}

ThreadPriority WorkerThread::GetDesiredThreadPriority() const {
  // To avoid shutdown hangs, disallow a priority below NORMAL during shutdown
  if (task_tracker_->HasShutdownStarted())
    return ThreadPriority::NORMAL;

  return priority_hint_;
}

void WorkerThread::UpdateThreadPriority(
    ThreadPriority desired_thread_priority) {
  if (desired_thread_priority == current_thread_priority_)
    return;

  PlatformThread::SetCurrentThreadPriority(desired_thread_priority);
  current_thread_priority_ = desired_thread_priority;
}

void WorkerThread::ThreadMain() {
  if (priority_hint_ == ThreadPriority::BACKGROUND) {
    switch (delegate_->GetThreadLabel()) {
      case ThreadLabel::POOLED:
        RunBackgroundPooledWorker();
        return;
      case ThreadLabel::SHARED:
        RunBackgroundSharedWorker();
        return;
      case ThreadLabel::DEDICATED:
        RunBackgroundDedicatedWorker();
        return;
#if defined(OS_WIN)
      case ThreadLabel::SHARED_COM:
        RunBackgroundSharedCOMWorker();
        return;
      case ThreadLabel::DEDICATED_COM:
        RunBackgroundDedicatedCOMWorker();
        return;
#endif  // defined(OS_WIN)
    }
  }

  switch (delegate_->GetThreadLabel()) {
    case ThreadLabel::POOLED:
      RunPooledWorker();
      return;
    case ThreadLabel::SHARED:
      RunSharedWorker();
      return;
    case ThreadLabel::DEDICATED:
      RunDedicatedWorker();
      return;
#if defined(OS_WIN)
    case ThreadLabel::SHARED_COM:
      RunSharedCOMWorker();
      return;
    case ThreadLabel::DEDICATED_COM:
      RunDedicatedCOMWorker();
      return;
#endif  // defined(OS_WIN)
  }
}

NOINLINE void WorkerThread::RunPooledWorker() {
  const int line_number = __LINE__;
  RunWorker();
  base::debug::Alias(&line_number);
}

NOINLINE void WorkerThread::RunBackgroundPooledWorker() {
  const int line_number = __LINE__;
  RunWorker();
  base::debug::Alias(&line_number);
}

NOINLINE void WorkerThread::RunSharedWorker() {
  const int line_number = __LINE__;
  RunWorker();
  base::debug::Alias(&line_number);
}

NOINLINE void WorkerThread::RunBackgroundSharedWorker() {
  const int line_number = __LINE__;
  RunWorker();
  base::debug::Alias(&line_number);
}

NOINLINE void WorkerThread::RunDedicatedWorker() {
  const int line_number = __LINE__;
  RunWorker();
  base::debug::Alias(&line_number);
}

NOINLINE void WorkerThread::RunBackgroundDedicatedWorker() {
  const int line_number = __LINE__;
  RunWorker();
  base::debug::Alias(&line_number);
}

#if defined(OS_WIN)
NOINLINE void WorkerThread::RunSharedCOMWorker() {
  const int line_number = __LINE__;
  RunWorker();
  base::debug::Alias(&line_number);
}

NOINLINE void WorkerThread::RunBackgroundSharedCOMWorker() {
  const int line_number = __LINE__;
  RunWorker();
  base::debug::Alias(&line_number);
}

NOINLINE void WorkerThread::RunDedicatedCOMWorker() {
  const int line_number = __LINE__;
  RunWorker();
  base::debug::Alias(&line_number);
}

NOINLINE void WorkerThread::RunBackgroundDedicatedCOMWorker() {
  const int line_number = __LINE__;
  RunWorker();
  base::debug::Alias(&line_number);
}
#endif  // defined(OS_WIN)

void WorkerThread::RunWorker() {
  DCHECK_EQ(self_, this);
  TRACE_EVENT_INSTANT0("thread_pool", "WorkerThreadThread born",
                       TRACE_EVENT_SCOPE_THREAD);
  TRACE_EVENT_BEGIN0("thread_pool", "WorkerThreadThread active");

  if (worker_thread_observer_)
    worker_thread_observer_->OnWorkerThreadMainEntry();

  delegate_->OnMainEntry(this);

  // Background threads can take an arbitrary amount of time to complete, do not
  // watch them for hangs. Ignore priority boosting for now.
  const bool watch_for_hangs =
      base::HangWatcher::GetInstance() != nullptr &&
      GetDesiredThreadPriority() != ThreadPriority::BACKGROUND;

  // If this process has a HangWatcher register this thread for watching.
  base::ScopedClosureRunner unregister_for_hang_watching;
  if (watch_for_hangs) {
    unregister_for_hang_watching =
        base::HangWatcher::GetInstance()->RegisterThread();
  }

  // A WorkerThread starts out waiting for work.
  {
    TRACE_EVENT_END0("thread_pool", "WorkerThreadThread active");
    delegate_->WaitForWork(&wake_up_event_);
    TRACE_EVENT_BEGIN0("thread_pool", "WorkerThreadThread active");
  }

  while (!ShouldExit()) {
#if defined(OS_MACOSX)
    mac::ScopedNSAutoreleasePool autorelease_pool;
#endif
    base::Optional<HangWatchScope> hang_watch_scope;
    if (watch_for_hangs)
      hang_watch_scope.emplace(base::HangWatchScope::kDefaultHangWatchTime);

    UpdateThreadPriority(GetDesiredThreadPriority());

    // Get the task source containing the next task to execute.
    RegisteredTaskSource task_source = delegate_->GetWork(this);
    if (!task_source) {
      // Exit immediately if GetWork() resulted in detaching this worker.
      if (ShouldExit())
        break;

      TRACE_EVENT_END0("thread_pool", "WorkerThreadThread active");
      hang_watch_scope.reset();
      delegate_->WaitForWork(&wake_up_event_);
      TRACE_EVENT_BEGIN0("thread_pool", "WorkerThreadThread active");
      continue;
    }

    task_source = task_tracker_->RunAndPopNextTask(std::move(task_source));

    delegate_->DidProcessTask(std::move(task_source));

    // Calling WakeUp() guarantees that this WorkerThread will run Tasks from
    // TaskSources returned by the GetWork() method of |delegate_| until it
    // returns nullptr. Resetting |wake_up_event_| here doesn't break this
    // invariant and avoids a useless loop iteration before going to sleep if
    // WakeUp() is called while this WorkerThread is awake.
    wake_up_event_.Reset();
  }

  // Important: It is unsafe to access unowned state (e.g. |task_tracker_|)
  // after invoking OnMainExit().

  delegate_->OnMainExit(this);

  if (worker_thread_observer_)
    worker_thread_observer_->OnWorkerThreadMainExit();

  // Release the self-reference to |this|. This can result in deleting |this|
  // and as such no more member accesses should be made after this point.
  self_ = nullptr;

  TRACE_EVENT_END0("thread_pool", "WorkerThreadThread active");
  TRACE_EVENT_INSTANT0("thread_pool", "WorkerThreadThread dead",
                       TRACE_EVENT_SCOPE_THREAD);
}

}  // namespace internal
}  // namespace base
