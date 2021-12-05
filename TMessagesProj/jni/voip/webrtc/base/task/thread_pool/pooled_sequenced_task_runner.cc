// Copyright 2018 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

#include "base/task/thread_pool/pooled_sequenced_task_runner.h"

#include "base/sequence_token.h"

namespace base {
namespace internal {

PooledSequencedTaskRunner::PooledSequencedTaskRunner(
    const TaskTraits& traits,
    PooledTaskRunnerDelegate* pooled_task_runner_delegate)
    : pooled_task_runner_delegate_(pooled_task_runner_delegate),
      sequence_(MakeRefCounted<Sequence>(traits,
                                         this,
                                         TaskSourceExecutionMode::kSequenced)) {
}

PooledSequencedTaskRunner::~PooledSequencedTaskRunner() = default;

bool PooledSequencedTaskRunner::PostDelayedTask(const Location& from_here,
                                                OnceClosure closure,
                                                TimeDelta delay) {
  if (!PooledTaskRunnerDelegate::Exists())
    return false;

  Task task(from_here, std::move(closure), delay);

  // Post the task as part of |sequence_|.
  return pooled_task_runner_delegate_->PostTaskWithSequence(std::move(task),
                                                            sequence_);
}

bool PooledSequencedTaskRunner::PostNonNestableDelayedTask(
    const Location& from_here,
    OnceClosure closure,
    TimeDelta delay) {
  // Tasks are never nested within the thread pool.
  return PostDelayedTask(from_here, std::move(closure), delay);
}

bool PooledSequencedTaskRunner::RunsTasksInCurrentSequence() const {
  return sequence_->token() == SequenceToken::GetForCurrentThread();
}

void PooledSequencedTaskRunner::UpdatePriority(TaskPriority priority) {
  pooled_task_runner_delegate_->UpdatePriority(sequence_, priority);
}

}  // namespace internal
}  // namespace base
