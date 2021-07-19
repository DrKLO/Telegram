// Copyright 2019 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

#include "base/task/thread_pool/task_source.h"

#include <utility>

#include "base/feature_list.h"
#include "base/logging.h"
#include "base/memory/ptr_util.h"
#include "base/task/task_features.h"
#include "base/task/thread_pool/task_tracker.h"

namespace base {
namespace internal {

TaskSource::Transaction::Transaction(TaskSource* task_source)
    : task_source_(task_source) {
  task_source->lock_.Acquire();
}

TaskSource::Transaction::Transaction(TaskSource::Transaction&& other)
    : task_source_(other.task_source()) {
  other.task_source_ = nullptr;
}

TaskSource::Transaction::~Transaction() {
  if (task_source_) {
    task_source_->lock_.AssertAcquired();
    task_source_->lock_.Release();
  }
}

SequenceSortKey TaskSource::Transaction::GetSortKey() const {
  return task_source_->GetSortKey();
}

void TaskSource::Transaction::UpdatePriority(TaskPriority priority) {
  if (FeatureList::IsEnabled(kAllTasksUserBlocking))
    return;
  task_source_->traits_.UpdatePriority(priority);
  task_source_->priority_racy_.store(task_source_->traits_.priority(),
                                     std::memory_order_relaxed);
}

void TaskSource::SetHeapHandle(const HeapHandle& handle) {
  heap_handle_ = handle;
}

void TaskSource::ClearHeapHandle() {
  heap_handle_ = HeapHandle();
}

TaskSource::TaskSource(const TaskTraits& traits,
                       TaskRunner* task_runner,
                       TaskSourceExecutionMode execution_mode)
    : traits_(traits),
      priority_racy_(traits.priority()),
      task_runner_(task_runner),
      execution_mode_(execution_mode) {
  DCHECK(task_runner_ ||
         execution_mode_ == TaskSourceExecutionMode::kParallel ||
         execution_mode_ == TaskSourceExecutionMode::kJob);
}

TaskSource::~TaskSource() = default;

TaskSource::Transaction TaskSource::BeginTransaction() {
  return Transaction(this);
}

RegisteredTaskSource::RegisteredTaskSource() = default;

RegisteredTaskSource::RegisteredTaskSource(std::nullptr_t)
    : RegisteredTaskSource() {}

RegisteredTaskSource::RegisteredTaskSource(
    RegisteredTaskSource&& other) noexcept
    :
#if DCHECK_IS_ON()
      run_step_{std::exchange(other.run_step_, State::kInitial)},
#endif  // DCHECK_IS_ON()
      task_source_{std::move(other.task_source_)},
      task_tracker_{std::exchange(other.task_tracker_, nullptr)} {
}

RegisteredTaskSource::~RegisteredTaskSource() {
  Unregister();
}

//  static
RegisteredTaskSource RegisteredTaskSource::CreateForTesting(
    scoped_refptr<TaskSource> task_source,
    TaskTracker* task_tracker) {
  return RegisteredTaskSource(std::move(task_source), task_tracker);
}

scoped_refptr<TaskSource> RegisteredTaskSource::Unregister() {
#if DCHECK_IS_ON()
  DCHECK_EQ(run_step_, State::kInitial);
#endif  // DCHECK_IS_ON()
  if (task_source_ && task_tracker_)
    return task_tracker_->UnregisterTaskSource(std::move(task_source_));
  return std::move(task_source_);
}

RegisteredTaskSource& RegisteredTaskSource::operator=(
    RegisteredTaskSource&& other) {
  Unregister();
#if DCHECK_IS_ON()
  run_step_ = std::exchange(other.run_step_, State::kInitial);
#endif  // DCHECK_IS_ON()
  task_source_ = std::move(other.task_source_);
  task_tracker_ = std::exchange(other.task_tracker_, nullptr);
  return *this;
}

TaskSource::RunStatus RegisteredTaskSource::WillRunTask() {
  TaskSource::RunStatus run_status = task_source_->WillRunTask();
#if DCHECK_IS_ON()
  DCHECK_EQ(run_step_, State::kInitial);
  if (run_status != TaskSource::RunStatus::kDisallowed)
    run_step_ = State::kReady;
#endif  // DCHECK_IS_ON()
  return run_status;
}

Task RegisteredTaskSource::TakeTask(TaskSource::Transaction* transaction) {
  DCHECK(!transaction || transaction->task_source() == get());
#if DCHECK_IS_ON()
  DCHECK_EQ(State::kReady, run_step_);
#endif  // DCHECK_IS_ON()
  return task_source_->TakeTask(transaction);
}

Task RegisteredTaskSource::Clear(TaskSource::Transaction* transaction) {
  DCHECK(!transaction || transaction->task_source() == get());
  return task_source_->Clear(transaction);
}

bool RegisteredTaskSource::DidProcessTask(
    TaskSource::Transaction* transaction) {
  DCHECK(!transaction || transaction->task_source() == get());
#if DCHECK_IS_ON()
  DCHECK_EQ(State::kReady, run_step_);
  run_step_ = State::kInitial;
#endif  // DCHECK_IS_ON()
  return task_source_->DidProcessTask(transaction);
}

RegisteredTaskSource::RegisteredTaskSource(
    scoped_refptr<TaskSource> task_source,
    TaskTracker* task_tracker)
    : task_source_(std::move(task_source)), task_tracker_(task_tracker) {}

TransactionWithRegisteredTaskSource::TransactionWithRegisteredTaskSource(
    RegisteredTaskSource task_source_in,
    TaskSource::Transaction transaction_in)
    : task_source(std::move(task_source_in)),
      transaction(std::move(transaction_in)) {
  DCHECK_EQ(task_source.get(), transaction.task_source());
}

// static:
TransactionWithRegisteredTaskSource
TransactionWithRegisteredTaskSource::FromTaskSource(
    RegisteredTaskSource task_source_in) {
  auto transaction = task_source_in->BeginTransaction();
  return TransactionWithRegisteredTaskSource(std::move(task_source_in),
                                             std::move(transaction));
}

}  // namespace internal
}  // namespace base
