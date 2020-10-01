// Copyright 2019 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

#include "base/task/simple_task_executor.h"

namespace base {

SimpleTaskExecutor::SimpleTaskExecutor(
    scoped_refptr<SingleThreadTaskRunner> task_queue)
    : task_queue_(std::move(task_queue)),
      previous_task_executor_(GetTaskExecutorForCurrentThread()) {
  DCHECK(task_queue_);
  // The TaskExecutor API does not expect nesting, but this can happen in tests
  // so we have to work around it here.
  if (previous_task_executor_)
    SetTaskExecutorForCurrentThread(nullptr);
  SetTaskExecutorForCurrentThread(this);
}

SimpleTaskExecutor::~SimpleTaskExecutor() {
  if (previous_task_executor_)
    SetTaskExecutorForCurrentThread(nullptr);
  SetTaskExecutorForCurrentThread(previous_task_executor_);
}

bool SimpleTaskExecutor::PostDelayedTask(const Location& from_here,
                                         const TaskTraits& traits,
                                         OnceClosure task,
                                         TimeDelta delay) {
  return task_queue_->PostDelayedTask(from_here, std::move(task), delay);
}

scoped_refptr<TaskRunner> SimpleTaskExecutor::CreateTaskRunner(
    const TaskTraits& traits) {
  return task_queue_;
}

scoped_refptr<SequencedTaskRunner>
SimpleTaskExecutor::CreateSequencedTaskRunner(const TaskTraits& traits) {
  return task_queue_;
}

scoped_refptr<SingleThreadTaskRunner>
SimpleTaskExecutor::CreateSingleThreadTaskRunner(
    const TaskTraits& traits,
    SingleThreadTaskRunnerThreadMode thread_mode) {
  return task_queue_;
}

#if defined(OS_WIN)
scoped_refptr<SingleThreadTaskRunner>
SimpleTaskExecutor::CreateCOMSTATaskRunner(
    const TaskTraits& traits,
    SingleThreadTaskRunnerThreadMode thread_mode) {
  // It seems pretty unlikely this will be used on a comsta task thread.
  NOTREACHED();
  return task_queue_;
}
#endif  // defined(OS_WIN)

}  // namespace base
