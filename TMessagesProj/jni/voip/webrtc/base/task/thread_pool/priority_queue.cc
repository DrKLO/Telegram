// Copyright 2016 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

#include "base/task/thread_pool/priority_queue.h"

#include <utility>

#include "base/logging.h"
#include "base/memory/ptr_util.h"
#include "base/stl_util.h"

namespace base {
namespace internal {

// A class combining a TaskSource and the SequenceSortKey that determines its
// position in a PriorityQueue. Instances are only mutable via
// take_task_source() which can only be called once and renders its instance
// invalid after the call.
class PriorityQueue::TaskSourceAndSortKey {
 public:
  TaskSourceAndSortKey() = default;
  TaskSourceAndSortKey(RegisteredTaskSource task_source,
                       const SequenceSortKey& sort_key)
      : task_source_(std::move(task_source)), sort_key_(sort_key) {
    DCHECK(task_source_);
  }

  // Note: while |task_source_| should always be non-null post-move (i.e. we
  // shouldn't be moving an invalid TaskSourceAndSortKey around), there can't be
  // a DCHECK(task_source_) on moves as IntrusiveHeap moves elements on pop
  // instead of overwriting them: resulting in the move of a
  // TaskSourceAndSortKey with a null |task_source_| in Transaction::Pop()'s
  // implementation.
  TaskSourceAndSortKey(TaskSourceAndSortKey&& other) = default;
  TaskSourceAndSortKey& operator=(TaskSourceAndSortKey&& other) = default;

  // Extracts |task_source_| from this object. This object is invalid after this
  // call.
  RegisteredTaskSource take_task_source() {
    DCHECK(task_source_);
    task_source_->ClearHeapHandle();
    return std::move(task_source_);
  }

  // Compares this TaskSourceAndSortKey to |other| based on their respective
  // |sort_key_|. Required by IntrusiveHeap.
  bool operator<=(const TaskSourceAndSortKey& other) const {
    return sort_key_ <= other.sort_key_;
  }

  // Required by IntrusiveHeap.
  void SetHeapHandle(const HeapHandle& handle) {
    DCHECK(task_source_);
    task_source_->SetHeapHandle(handle);
  }

  // Required by IntrusiveHeap.
  void ClearHeapHandle() {
    // Ensure |task_source_| is not nullptr, which may be the case if
    // take_task_source() was called before this.
    if (task_source_)
      task_source_->ClearHeapHandle();
  }

  // Required by IntrusiveHeap.
  HeapHandle GetHeapHandle() const {
    if (task_source_)
      return task_source_->GetHeapHandle();
    return HeapHandle::Invalid();
  }

  const RegisteredTaskSource& task_source() const { return task_source_; }
  RegisteredTaskSource& task_source() { return task_source_; }

  const SequenceSortKey& sort_key() const { return sort_key_; }

 private:
  RegisteredTaskSource task_source_;
  SequenceSortKey sort_key_;

  DISALLOW_COPY_AND_ASSIGN(TaskSourceAndSortKey);
};

PriorityQueue::PriorityQueue() = default;

PriorityQueue::~PriorityQueue() {
  if (!is_flush_task_sources_on_destroy_enabled_)
    return;

  while (!container_.empty()) {
    auto task_source = PopTaskSource();
    auto task = task_source.Clear();
    std::move(task.task).Run();
  }
}

PriorityQueue& PriorityQueue::operator=(PriorityQueue&& other) = default;

void PriorityQueue::Push(
    TransactionWithRegisteredTaskSource transaction_with_task_source) {
  auto sequence_sort_key =
      transaction_with_task_source.transaction.GetSortKey();
  container_.insert(TaskSourceAndSortKey(
      std::move(transaction_with_task_source.task_source), sequence_sort_key));
  IncrementNumTaskSourcesForPriority(sequence_sort_key.priority());
}

const SequenceSortKey& PriorityQueue::PeekSortKey() const {
  DCHECK(!IsEmpty());
  return container_.Min().sort_key();
}

RegisteredTaskSource& PriorityQueue::PeekTaskSource() const {
  DCHECK(!IsEmpty());

  // The const_cast on Min() is okay since modifying the TaskSource cannot alter
  // the sort order of TaskSourceAndSortKey.
  auto& task_source_and_sort_key =
      const_cast<PriorityQueue::TaskSourceAndSortKey&>(container_.Min());
  return task_source_and_sort_key.task_source();
}

RegisteredTaskSource PriorityQueue::PopTaskSource() {
  DCHECK(!IsEmpty());

  // The const_cast on Min() is okay since the TaskSourceAndSortKey is
  // transactionally being popped from |container_| right after and taking its
  // TaskSource does not alter its sort order.
  auto& task_source_and_sort_key =
      const_cast<TaskSourceAndSortKey&>(container_.Min());
  DecrementNumTaskSourcesForPriority(
      task_source_and_sort_key.sort_key().priority());
  RegisteredTaskSource task_source =
      task_source_and_sort_key.take_task_source();
  container_.Pop();
  return task_source;
}

RegisteredTaskSource PriorityQueue::RemoveTaskSource(
    const TaskSource& task_source) {
  if (IsEmpty())
    return nullptr;

  const HeapHandle heap_handle = task_source.heap_handle();
  if (!heap_handle.IsValid())
    return nullptr;

  TaskSourceAndSortKey& task_source_and_sort_key =
      const_cast<PriorityQueue::TaskSourceAndSortKey&>(
          container_.at(heap_handle));
  DCHECK_EQ(task_source_and_sort_key.task_source().get(), &task_source);
  RegisteredTaskSource registered_task_source =
      task_source_and_sort_key.take_task_source();

  DecrementNumTaskSourcesForPriority(
      task_source_and_sort_key.sort_key().priority());
  container_.erase(heap_handle);
  return registered_task_source;
}

void PriorityQueue::UpdateSortKey(TaskSource::Transaction transaction) {
  DCHECK(transaction);

  if (IsEmpty())
    return;

  const HeapHandle heap_handle = transaction.task_source()->heap_handle();
  if (!heap_handle.IsValid())
    return;

  auto old_sort_key = container_.at(heap_handle).sort_key();
  auto new_sort_key = transaction.GetSortKey();
  auto registered_task_source =
      const_cast<PriorityQueue::TaskSourceAndSortKey&>(
          container_.at(heap_handle))
          .take_task_source();

  DecrementNumTaskSourcesForPriority(old_sort_key.priority());
  IncrementNumTaskSourcesForPriority(new_sort_key.priority());

  container_.ChangeKey(
      heap_handle,
      TaskSourceAndSortKey(std::move(registered_task_source), new_sort_key));
}

bool PriorityQueue::IsEmpty() const {
  return container_.empty();
}

size_t PriorityQueue::Size() const {
  return container_.size();
}

void PriorityQueue::EnableFlushTaskSourcesOnDestroyForTesting() {
  DCHECK(!is_flush_task_sources_on_destroy_enabled_);
  is_flush_task_sources_on_destroy_enabled_ = true;
}

void PriorityQueue::DecrementNumTaskSourcesForPriority(TaskPriority priority) {
  DCHECK_GT(num_task_sources_per_priority_[static_cast<int>(priority)], 0U);
  --num_task_sources_per_priority_[static_cast<int>(priority)];
}

void PriorityQueue::IncrementNumTaskSourcesForPriority(TaskPriority priority) {
  ++num_task_sources_per_priority_[static_cast<int>(priority)];
}

}  // namespace internal
}  // namespace base
