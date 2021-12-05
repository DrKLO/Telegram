// Copyright 2018 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

#include "base/task/thread_pool/pooled_parallel_task_runner.h"
#include "base/task/thread_pool/pooled_task_runner_delegate.h"

#include "base/task/thread_pool/sequence.h"

namespace base {
namespace internal {

PooledParallelTaskRunner::PooledParallelTaskRunner(
    const TaskTraits& traits,
    PooledTaskRunnerDelegate* pooled_task_runner_delegate)
    : traits_(traits),
      pooled_task_runner_delegate_(pooled_task_runner_delegate) {}

PooledParallelTaskRunner::~PooledParallelTaskRunner() = default;

bool PooledParallelTaskRunner::PostDelayedTask(const Location& from_here,
                                               OnceClosure closure,
                                               TimeDelta delay) {
  if (!PooledTaskRunnerDelegate::Exists())
    return false;

  // Post the task as part of a one-off single-task Sequence.
  scoped_refptr<Sequence> sequence = MakeRefCounted<Sequence>(
      traits_, this, TaskSourceExecutionMode::kParallel);

  {
    CheckedAutoLock auto_lock(lock_);
    sequences_.insert(sequence.get());
  }

  return pooled_task_runner_delegate_->PostTaskWithSequence(
      Task(from_here, std::move(closure), delay), std::move(sequence));
}

void PooledParallelTaskRunner::UnregisterSequence(Sequence* sequence) {
  DCHECK(sequence);

  CheckedAutoLock auto_lock(lock_);
  sequences_.erase(sequence);
}

}  // namespace internal
}  // namespace base
