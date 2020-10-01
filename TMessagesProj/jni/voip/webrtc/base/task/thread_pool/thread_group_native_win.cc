// Copyright 2017 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

#include "base/task/thread_pool/thread_group_native_win.h"

#include "base/optional.h"
#include "base/task/thread_pool/task_tracker.h"
#include "base/threading/scoped_blocking_call_internal.h"
#include "base/win/scoped_com_initializer.h"

namespace base {
namespace internal {

class ThreadGroupNativeWin::ScopedCallbackMayRunLongObserver
    : public BlockingObserver {
 public:
  ScopedCallbackMayRunLongObserver(PTP_CALLBACK_INSTANCE callback)
      : callback_(callback) {
    SetBlockingObserverForCurrentThread(this);
  }

  ~ScopedCallbackMayRunLongObserver() override {
    ClearBlockingObserverForCurrentThread();
  }

  // BlockingObserver:
  void BlockingStarted(BlockingType blocking_type) override {
    ::CallbackMayRunLong(callback_);
    // CallbackMayRunLong should not be called twice.
    ClearBlockingObserverForCurrentThread();
  }

  void BlockingTypeUpgraded() override {}
  void BlockingEnded() override {}

 private:
  PTP_CALLBACK_INSTANCE callback_;

  DISALLOW_COPY_AND_ASSIGN(ScopedCallbackMayRunLongObserver);
};

ThreadGroupNativeWin::ThreadGroupNativeWin(
    TrackedRef<TaskTracker> task_tracker,
    TrackedRef<Delegate> delegate,
    ThreadGroup* predecessor_thread_group)
    : ThreadGroupNative(std::move(task_tracker),
                        std::move(delegate),
                        predecessor_thread_group) {}

ThreadGroupNativeWin::~ThreadGroupNativeWin() {
  ::DestroyThreadpoolEnvironment(&environment_);
  ::CloseThreadpoolWork(work_);
  ::CloseThreadpool(pool_);
}

void ThreadGroupNativeWin::StartImpl() {
  ::InitializeThreadpoolEnvironment(&environment_);

  pool_ = ::CreateThreadpool(nullptr);
  DCHECK(pool_) << "LastError: " << ::GetLastError();
  ::SetThreadpoolThreadMinimum(pool_, 1);
  ::SetThreadpoolThreadMaximum(pool_, 256);

  work_ = ::CreateThreadpoolWork(&RunNextTaskSource, this, &environment_);
  DCHECK(work_) << "LastError: " << GetLastError();
  ::SetThreadpoolCallbackPool(&environment_, pool_);
}

void ThreadGroupNativeWin::JoinImpl() {
  ::WaitForThreadpoolWorkCallbacks(work_, true);
}

void ThreadGroupNativeWin::SubmitWork() {
  // TODO(fdoray): Handle priorities by having different work objects and using
  // SetThreadpoolCallbackPriority().
  ::SubmitThreadpoolWork(work_);
}

// static
void CALLBACK
ThreadGroupNativeWin::RunNextTaskSource(PTP_CALLBACK_INSTANCE callback_instance,
                                        void* thread_group_windows_impl,
                                        PTP_WORK) {
  auto* thread_group =
      static_cast<ThreadGroupNativeWin*>(thread_group_windows_impl);

  // Windows Thread Pool API best practices state that all resources created
  // in the callback function should be cleaned up before returning from the
  // function. This includes COM initialization.
  auto win_thread_environment = thread_group->GetScopedWindowsThreadEnvironment(
      thread_group->worker_environment_);

  ScopedCallbackMayRunLongObserver callback_may_run_long_observer(
      callback_instance);

  thread_group->RunNextTaskSourceImpl();
}

}  // namespace internal
}  // namespace base
