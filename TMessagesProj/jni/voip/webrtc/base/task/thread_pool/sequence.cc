// Copyright 2016 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

#include "base/task/thread_pool/sequence.h"

#include <utility>

#include "base/bind.h"
#include "base/critical_closure.h"
#include "base/feature_list.h"
#include "base/logging.h"
#include "base/memory/ptr_util.h"
#include "base/task/task_features.h"
#include "base/time/time.h"

namespace base {
namespace internal {

Sequence::Transaction::Transaction(Sequence* sequence)
    : TaskSource::Transaction(sequence) {}

Sequence::Transaction::Transaction(Sequence::Transaction&& other) = default;

Sequence::Transaction::~Transaction() = default;

bool Sequence::Transaction::WillPushTask() const {
  // If the sequence is empty before a Task is inserted into it and the pool is
  // not running any task from this sequence, it should be queued.
  // Otherwise, one of these must be true:
  // - The Sequence is already queued, or,
  // - A thread is running a Task from the Sequence. It is expected to reenqueue
  //   the Sequence once it's done running the Task.
  return sequence()->queue_.empty() && !sequence()->has_worker_;
}

void Sequence::Transaction::PushTask(Task task) {
  // Use CHECK instead of DCHECK to crash earlier. See http://crbug.com/711167
  // for details.
  CHECK(task.task);
  DCHECK(task.queue_time.is_null());

  bool should_be_queued = WillPushTask();
  task.queue_time = TimeTicks::Now();

  task.task = sequence()->traits_.shutdown_behavior() ==
                      TaskShutdownBehavior::BLOCK_SHUTDOWN
                  ? MakeCriticalClosure(std::move(task.task))
                  : std::move(task.task);

  sequence()->queue_.push(std::move(task));

  // AddRef() matched by manual Release() when the sequence has no more tasks
  // to run (in DidProcessTask() or Clear()).
  if (should_be_queued && sequence()->task_runner())
    sequence()->task_runner()->AddRef();
}

TaskSource::RunStatus Sequence::WillRunTask() {
  // There should never be a second call to WillRunTask() before DidProcessTask
  // since the RunStatus is always marked a saturated.
  DCHECK(!has_worker_);

  // It's ok to access |has_worker_| outside of a Transaction since
  // WillRunTask() is externally synchronized, always called in sequence with
  // TakeTask() and DidProcessTask() and only called if |!queue_.empty()|, which
  // means it won't race with WillPushTask()/PushTask().
  has_worker_ = true;
  return RunStatus::kAllowedSaturated;
}

size_t Sequence::GetRemainingConcurrency() const {
  return 1;
}

Task Sequence::TakeTask(TaskSource::Transaction* transaction) {
  CheckedAutoLockMaybe auto_lock(transaction ? nullptr : &lock_);

  DCHECK(has_worker_);
  DCHECK(!queue_.empty());
  DCHECK(queue_.front().task);

  auto next_task = std::move(queue_.front());
  queue_.pop();
  return next_task;
}

bool Sequence::DidProcessTask(TaskSource::Transaction* transaction) {
  CheckedAutoLockMaybe auto_lock(transaction ? nullptr : &lock_);
  // There should never be a call to DidProcessTask without an associated
  // WillRunTask().
  DCHECK(has_worker_);
  has_worker_ = false;
  // See comment on TaskSource::task_runner_ for lifetime management details.
  if (queue_.empty()) {
    ReleaseTaskRunner();
    return false;
  }
  // Let the caller re-enqueue this non-empty Sequence regardless of
  // |run_result| so it can continue churning through this Sequence's tasks and
  // skip/delete them in the proper scope.
  return true;
}

SequenceSortKey Sequence::GetSortKey() const {
  DCHECK(!queue_.empty());
  return SequenceSortKey(traits_.priority(), queue_.front().queue_time);
}

Task Sequence::Clear(TaskSource::Transaction* transaction) {
  CheckedAutoLockMaybe auto_lock(transaction ? nullptr : &lock_);
  // See comment on TaskSource::task_runner_ for lifetime management details.
  if (!queue_.empty() && !has_worker_)
    ReleaseTaskRunner();
  return Task(FROM_HERE,
              base::BindOnce(
                  [](base::queue<Task> queue) {
                    while (!queue.empty())
                      queue.pop();
                  },
                  std::move(queue_)),
              TimeDelta());
}

void Sequence::ReleaseTaskRunner() {
  if (!task_runner())
    return;
  if (execution_mode() == TaskSourceExecutionMode::kParallel) {
    static_cast<PooledParallelTaskRunner*>(task_runner())
        ->UnregisterSequence(this);
  }
  // No member access after this point, releasing |task_runner()| might delete
  // |this|.
  task_runner()->Release();
}

Sequence::Sequence(const TaskTraits& traits,
                   TaskRunner* task_runner,
                   TaskSourceExecutionMode execution_mode)
    : TaskSource(traits, task_runner, execution_mode) {}

Sequence::~Sequence() = default;

Sequence::Transaction Sequence::BeginTransaction() {
  return Transaction(this);
}

ExecutionEnvironment Sequence::GetExecutionEnvironment() {
  return {token_, &sequence_local_storage_};
}

}  // namespace internal
}  // namespace base
