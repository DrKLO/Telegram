// Copyright 2015 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

#include "base/task/sequence_manager/work_queue_sets.h"

#include "base/logging.h"

namespace base {
namespace sequence_manager {
namespace internal {

WorkQueueSets::WorkQueueSets(const char* name,
                             Observer* observer,
                             const SequenceManager::Settings& settings)
    : name_(name),
#if DCHECK_IS_ON()
      last_rand_(settings.random_task_selection_seed),
#endif
      observer_(observer) {
}

WorkQueueSets::~WorkQueueSets() = default;

void WorkQueueSets::AddQueue(WorkQueue* work_queue, size_t set_index) {
  DCHECK(!work_queue->work_queue_sets());
  DCHECK_LT(set_index, work_queue_heaps_.size());
  DCHECK(!work_queue->heap_handle().IsValid());
  EnqueueOrder enqueue_order;
  bool has_enqueue_order = work_queue->GetFrontTaskEnqueueOrder(&enqueue_order);
  work_queue->AssignToWorkQueueSets(this);
  work_queue->AssignSetIndex(set_index);
  if (!has_enqueue_order)
    return;
  bool was_empty = work_queue_heaps_[set_index].empty();
  work_queue_heaps_[set_index].insert({enqueue_order, work_queue});
  if (was_empty)
    observer_->WorkQueueSetBecameNonEmpty(set_index);
}

void WorkQueueSets::RemoveQueue(WorkQueue* work_queue) {
  DCHECK_EQ(this, work_queue->work_queue_sets());
  work_queue->AssignToWorkQueueSets(nullptr);
  if (!work_queue->heap_handle().IsValid())
    return;
  size_t set_index = work_queue->work_queue_set_index();
  DCHECK_LT(set_index, work_queue_heaps_.size());
  work_queue_heaps_[set_index].erase(work_queue->heap_handle());
  if (work_queue_heaps_[set_index].empty())
    observer_->WorkQueueSetBecameEmpty(set_index);
  DCHECK(!work_queue->heap_handle().IsValid());
}

void WorkQueueSets::ChangeSetIndex(WorkQueue* work_queue, size_t set_index) {
  DCHECK_EQ(this, work_queue->work_queue_sets());
  DCHECK_LT(set_index, work_queue_heaps_.size());
  EnqueueOrder enqueue_order;
  bool has_enqueue_order = work_queue->GetFrontTaskEnqueueOrder(&enqueue_order);
  size_t old_set = work_queue->work_queue_set_index();
  DCHECK_LT(old_set, work_queue_heaps_.size());
  DCHECK_NE(old_set, set_index);
  work_queue->AssignSetIndex(set_index);
  DCHECK_EQ(has_enqueue_order, work_queue->heap_handle().IsValid());
  if (!has_enqueue_order)
    return;
  work_queue_heaps_[old_set].erase(work_queue->heap_handle());
  bool was_empty = work_queue_heaps_[set_index].empty();
  work_queue_heaps_[set_index].insert({enqueue_order, work_queue});
  if (work_queue_heaps_[old_set].empty())
    observer_->WorkQueueSetBecameEmpty(old_set);
  if (was_empty)
    observer_->WorkQueueSetBecameNonEmpty(set_index);
}

void WorkQueueSets::OnQueuesFrontTaskChanged(WorkQueue* work_queue) {
  EnqueueOrder enqueue_order;
  size_t set_index = work_queue->work_queue_set_index();
  DCHECK_EQ(this, work_queue->work_queue_sets());
  DCHECK_LT(set_index, work_queue_heaps_.size());
  DCHECK(work_queue->heap_handle().IsValid());
  DCHECK(!work_queue_heaps_[set_index].empty()) << " set_index = " << set_index;
  if (work_queue->GetFrontTaskEnqueueOrder(&enqueue_order)) {
    // O(log n)
    work_queue_heaps_[set_index].ChangeKey(work_queue->heap_handle(),
                                           {enqueue_order, work_queue});
  } else {
    // O(log n)
    work_queue_heaps_[set_index].erase(work_queue->heap_handle());
    DCHECK(!work_queue->heap_handle().IsValid());
    if (work_queue_heaps_[set_index].empty())
      observer_->WorkQueueSetBecameEmpty(set_index);
  }
}

void WorkQueueSets::OnTaskPushedToEmptyQueue(WorkQueue* work_queue) {
  // NOTE if this function changes, we need to keep |WorkQueueSets::AddQueue| in
  // sync.
  DCHECK_EQ(this, work_queue->work_queue_sets());
  EnqueueOrder enqueue_order;
  bool has_enqueue_order = work_queue->GetFrontTaskEnqueueOrder(&enqueue_order);
  DCHECK(has_enqueue_order);
  size_t set_index = work_queue->work_queue_set_index();
  DCHECK_LT(set_index, work_queue_heaps_.size())
      << " set_index = " << set_index;
  // |work_queue| should not be in work_queue_heaps_[set_index].
  DCHECK(!work_queue->heap_handle().IsValid());
  bool was_empty = work_queue_heaps_[set_index].empty();
  work_queue_heaps_[set_index].insert({enqueue_order, work_queue});
  if (was_empty)
    observer_->WorkQueueSetBecameNonEmpty(set_index);
}

void WorkQueueSets::OnPopMinQueueInSet(WorkQueue* work_queue) {
  // Assume that |work_queue| contains the lowest enqueue_order.
  size_t set_index = work_queue->work_queue_set_index();
  DCHECK_EQ(this, work_queue->work_queue_sets());
  DCHECK_LT(set_index, work_queue_heaps_.size());
  DCHECK(!work_queue_heaps_[set_index].empty()) << " set_index = " << set_index;
  DCHECK_EQ(work_queue_heaps_[set_index].Min().value, work_queue)
      << " set_index = " << set_index;
  DCHECK(work_queue->heap_handle().IsValid());
  EnqueueOrder enqueue_order;
  if (work_queue->GetFrontTaskEnqueueOrder(&enqueue_order)) {
    // O(log n)
    work_queue_heaps_[set_index].ReplaceMin({enqueue_order, work_queue});
  } else {
    // O(log n)
    work_queue_heaps_[set_index].Pop();
    DCHECK(!work_queue->heap_handle().IsValid());
    DCHECK(work_queue_heaps_[set_index].empty() ||
           work_queue_heaps_[set_index].Min().value != work_queue);
    if (work_queue_heaps_[set_index].empty()) {
      observer_->WorkQueueSetBecameEmpty(set_index);
    }
  }
}

void WorkQueueSets::OnQueueBlocked(WorkQueue* work_queue) {
  DCHECK_EQ(this, work_queue->work_queue_sets());
  base::internal::HeapHandle heap_handle = work_queue->heap_handle();
  if (!heap_handle.IsValid())
    return;
  size_t set_index = work_queue->work_queue_set_index();
  DCHECK_LT(set_index, work_queue_heaps_.size());
  work_queue_heaps_[set_index].erase(heap_handle);
  if (work_queue_heaps_[set_index].empty())
    observer_->WorkQueueSetBecameEmpty(set_index);
}

WorkQueue* WorkQueueSets::GetOldestQueueInSet(size_t set_index) const {
  DCHECK_LT(set_index, work_queue_heaps_.size());
  if (work_queue_heaps_[set_index].empty())
    return nullptr;
  WorkQueue* queue = work_queue_heaps_[set_index].Min().value;
  DCHECK_EQ(set_index, queue->work_queue_set_index());
  DCHECK(queue->heap_handle().IsValid());
  return queue;
}

WorkQueue* WorkQueueSets::GetOldestQueueAndEnqueueOrderInSet(
    size_t set_index,
    EnqueueOrder* out_enqueue_order) const {
  DCHECK_LT(set_index, work_queue_heaps_.size());
  if (work_queue_heaps_[set_index].empty())
    return nullptr;
  const OldestTaskEnqueueOrder& oldest = work_queue_heaps_[set_index].Min();
  DCHECK(oldest.value->heap_handle().IsValid());
  *out_enqueue_order = oldest.key;
  EnqueueOrder enqueue_order;
  DCHECK(oldest.value->GetFrontTaskEnqueueOrder(&enqueue_order) &&
         oldest.key == enqueue_order);
  return oldest.value;
}

#if DCHECK_IS_ON()
WorkQueue* WorkQueueSets::GetRandomQueueInSet(size_t set_index) const {
  DCHECK_LT(set_index, work_queue_heaps_.size());
  if (work_queue_heaps_[set_index].empty())
    return nullptr;

  WorkQueue* queue =
      work_queue_heaps_[set_index]
          .begin()[Random() % work_queue_heaps_[set_index].size()]
          .value;
  DCHECK_EQ(set_index, queue->work_queue_set_index());
  DCHECK(queue->heap_handle().IsValid());
  return queue;
}

WorkQueue* WorkQueueSets::GetRandomQueueAndEnqueueOrderInSet(
    size_t set_index,
    EnqueueOrder* out_enqueue_order) const {
  DCHECK_LT(set_index, work_queue_heaps_.size());
  if (work_queue_heaps_[set_index].empty())
    return nullptr;
  const OldestTaskEnqueueOrder& chosen =
      work_queue_heaps_[set_index]
          .begin()[Random() % work_queue_heaps_[set_index].size()];
  *out_enqueue_order = chosen.key;
  EnqueueOrder enqueue_order;
  DCHECK(chosen.value->GetFrontTaskEnqueueOrder(&enqueue_order) &&
         chosen.key == enqueue_order);
  return chosen.value;
}
#endif

bool WorkQueueSets::IsSetEmpty(size_t set_index) const {
  DCHECK_LT(set_index, work_queue_heaps_.size())
      << " set_index = " << set_index;
  return work_queue_heaps_[set_index].empty();
}

#if DCHECK_IS_ON() || !defined(NDEBUG)
bool WorkQueueSets::ContainsWorkQueueForTest(
    const WorkQueue* work_queue) const {
  EnqueueOrder enqueue_order;
  bool has_enqueue_order = work_queue->GetFrontTaskEnqueueOrder(&enqueue_order);

  for (const base::internal::IntrusiveHeap<OldestTaskEnqueueOrder>& heap :
       work_queue_heaps_) {
    for (const OldestTaskEnqueueOrder& heap_value_pair : heap) {
      if (heap_value_pair.value == work_queue) {
        DCHECK(has_enqueue_order);
        DCHECK_EQ(heap_value_pair.key, enqueue_order);
        DCHECK_EQ(this, work_queue->work_queue_sets());
        return true;
      }
    }
  }

  if (work_queue->work_queue_sets() == this) {
    DCHECK(!has_enqueue_order);
    return true;
  }

  return false;
}
#endif

void WorkQueueSets::CollectSkippedOverLowerPriorityTasks(
    const internal::WorkQueue* selected_work_queue,
    std::vector<const Task*>* result) const {
  EnqueueOrder selected_enqueue_order;
  CHECK(selected_work_queue->GetFrontTaskEnqueueOrder(&selected_enqueue_order));
  for (size_t priority = selected_work_queue->work_queue_set_index() + 1;
       priority < TaskQueue::kQueuePriorityCount; priority++) {
    for (const OldestTaskEnqueueOrder& pair : work_queue_heaps_[priority]) {
      pair.value->CollectTasksOlderThan(selected_enqueue_order, result);
    }
  }
}

}  // namespace internal
}  // namespace sequence_manager
}  // namespace base
