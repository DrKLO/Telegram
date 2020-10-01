// Copyright (c) 2013 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

#include "base/deferred_sequenced_task_runner.h"

#include <utility>

#include "base/bind.h"
#include "base/logging.h"

namespace base {

DeferredSequencedTaskRunner::DeferredTask::DeferredTask()
    : is_non_nestable(false) {
}

DeferredSequencedTaskRunner::DeferredTask::DeferredTask(DeferredTask&& other) =
    default;

DeferredSequencedTaskRunner::DeferredTask::~DeferredTask() = default;

DeferredSequencedTaskRunner::DeferredTask&
DeferredSequencedTaskRunner::DeferredTask::operator=(DeferredTask&& other) =
    default;

DeferredSequencedTaskRunner::DeferredSequencedTaskRunner(
    scoped_refptr<SequencedTaskRunner> target_task_runner)
    : DeferredSequencedTaskRunner() {
  DCHECK(target_task_runner);
  target_task_runner_ = std::move(target_task_runner);
}

DeferredSequencedTaskRunner::DeferredSequencedTaskRunner()
    : created_thread_id_(PlatformThread::CurrentId()) {}

bool DeferredSequencedTaskRunner::PostDelayedTask(const Location& from_here,
                                                  OnceClosure task,
                                                  TimeDelta delay) {
  AutoLock lock(lock_);
  if (started_) {
    DCHECK(deferred_tasks_queue_.empty());
    return target_task_runner_->PostDelayedTask(from_here, std::move(task),
                                                delay);
  }

  QueueDeferredTask(from_here, std::move(task), delay,
                    false /* is_non_nestable */);
  return true;
}

bool DeferredSequencedTaskRunner::RunsTasksInCurrentSequence() const {
  AutoLock lock(lock_);
  if (target_task_runner_)
    return target_task_runner_->RunsTasksInCurrentSequence();

  return created_thread_id_ == PlatformThread::CurrentId();
}

bool DeferredSequencedTaskRunner::PostNonNestableDelayedTask(
    const Location& from_here,
    OnceClosure task,
    TimeDelta delay) {
  AutoLock lock(lock_);
  if (started_) {
    DCHECK(deferred_tasks_queue_.empty());
    return target_task_runner_->PostNonNestableDelayedTask(
        from_here, std::move(task), delay);
  }
  QueueDeferredTask(from_here, std::move(task), delay,
                    true /* is_non_nestable */);
  return true;
}

void DeferredSequencedTaskRunner::Start() {
  AutoLock lock(lock_);
  StartImpl();
}

void DeferredSequencedTaskRunner::StartWithTaskRunner(
    scoped_refptr<SequencedTaskRunner> target_task_runner) {
  AutoLock lock(lock_);
  DCHECK(!target_task_runner_);
  DCHECK(target_task_runner);
  target_task_runner_ = std::move(target_task_runner);
  StartImpl();
}

DeferredSequencedTaskRunner::~DeferredSequencedTaskRunner() = default;

void DeferredSequencedTaskRunner::QueueDeferredTask(const Location& from_here,
                                                    OnceClosure task,
                                                    TimeDelta delay,
                                                    bool is_non_nestable) {
  lock_.AssertAcquired();

  // Use CHECK instead of DCHECK to crash earlier. See http://crbug.com/711167
  // for details.
  CHECK(task);

  DeferredTask deferred_task;
  deferred_task.posted_from = from_here;
  deferred_task.task = std::move(task);
  deferred_task.delay = delay;
  deferred_task.is_non_nestable = is_non_nestable;
  deferred_tasks_queue_.push_back(std::move(deferred_task));
}

void DeferredSequencedTaskRunner::StartImpl() {
  lock_.AssertAcquired();  // Callers should have grabbed the lock.
  DCHECK(!started_);
  started_ = true;
  DCHECK(target_task_runner_);
  for (auto& task : deferred_tasks_queue_) {
    if (task.is_non_nestable) {
      target_task_runner_->PostNonNestableDelayedTask(
          task.posted_from, std::move(task.task), task.delay);
    } else {
      target_task_runner_->PostDelayedTask(task.posted_from,
                                           std::move(task.task), task.delay);
    }
  }
  deferred_tasks_queue_.clear();
}

}  // namespace base
