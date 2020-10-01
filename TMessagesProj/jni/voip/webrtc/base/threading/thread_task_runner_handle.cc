// Copyright 2016 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

#include "base/threading/thread_task_runner_handle.h"

#include <memory>
#include <utility>

#include "base/bind.h"
#include "base/lazy_instance.h"
#include "base/logging.h"
#include "base/run_loop.h"
#include "base/threading/thread_local.h"

namespace base {

namespace {

base::LazyInstance<base::ThreadLocalPointer<ThreadTaskRunnerHandle>>::Leaky
    thread_task_runner_tls = LAZY_INSTANCE_INITIALIZER;

}  // namespace

// static
const scoped_refptr<SingleThreadTaskRunner>& ThreadTaskRunnerHandle::Get() {
  const ThreadTaskRunnerHandle* current =
      thread_task_runner_tls.Pointer()->Get();
  CHECK(current)
      << "Error: This caller requires a single-threaded context (i.e. the "
         "current task needs to run from a SingleThreadTaskRunner). If you're "
         "in a test refer to //docs/threading_and_tasks_testing.md.";
  return current->task_runner_;
}

// static
bool ThreadTaskRunnerHandle::IsSet() {
  return !!thread_task_runner_tls.Pointer()->Get();
}

// static
ScopedClosureRunner ThreadTaskRunnerHandle::OverrideForTesting(
    scoped_refptr<SingleThreadTaskRunner> overriding_task_runner) {
  // OverrideForTesting() is not compatible with a SequencedTaskRunnerHandle
  // already being set on this thread (except when it's set by the current
  // ThreadTaskRunnerHandle).
  DCHECK(!SequencedTaskRunnerHandle::IsSet() || IsSet());

  if (!IsSet()) {
    auto top_level_ttrh = std::make_unique<ThreadTaskRunnerHandle>(
        std::move(overriding_task_runner));
    return ScopedClosureRunner(base::BindOnce(
        [](std::unique_ptr<ThreadTaskRunnerHandle> ttrh_to_release) {},
        std::move(top_level_ttrh)));
  }

  ThreadTaskRunnerHandle* ttrh = thread_task_runner_tls.Pointer()->Get();
  // Swap the two (and below bind |overriding_task_runner|, which is now the
  // previous one, as the |task_runner_to_restore|).
  ttrh->sequenced_task_runner_handle_.task_runner_ = overriding_task_runner;
  ttrh->task_runner_.swap(overriding_task_runner);

  auto no_running_during_override =
      std::make_unique<RunLoop::ScopedDisallowRunningForTesting>();

  return ScopedClosureRunner(base::BindOnce(
      [](scoped_refptr<SingleThreadTaskRunner> task_runner_to_restore,
         SingleThreadTaskRunner* expected_task_runner_before_restore,
         std::unique_ptr<RunLoop::ScopedDisallowRunningForTesting>
             no_running_during_override) {
        ThreadTaskRunnerHandle* ttrh = thread_task_runner_tls.Pointer()->Get();

        DCHECK_EQ(expected_task_runner_before_restore, ttrh->task_runner_.get())
            << "Nested overrides must expire their ScopedClosureRunners "
               "in LIFO order.";

        ttrh->sequenced_task_runner_handle_.task_runner_ =
            task_runner_to_restore;
        ttrh->task_runner_.swap(task_runner_to_restore);
      },
      std::move(overriding_task_runner),
      base::Unretained(ttrh->task_runner_.get()),
      std::move(no_running_during_override)));
}

ThreadTaskRunnerHandle::ThreadTaskRunnerHandle(
    scoped_refptr<SingleThreadTaskRunner> task_runner)
    : task_runner_(std::move(task_runner)),
      sequenced_task_runner_handle_(task_runner_) {
  DCHECK(task_runner_->BelongsToCurrentThread());
  DCHECK(!thread_task_runner_tls.Pointer()->Get());
  thread_task_runner_tls.Pointer()->Set(this);
}

ThreadTaskRunnerHandle::~ThreadTaskRunnerHandle() {
  DCHECK(task_runner_->BelongsToCurrentThread());
  DCHECK_EQ(thread_task_runner_tls.Pointer()->Get(), this);
  thread_task_runner_tls.Pointer()->Set(nullptr);
}

}  // namespace base
