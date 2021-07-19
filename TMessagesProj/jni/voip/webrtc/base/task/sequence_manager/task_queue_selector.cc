// Copyright 2014 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

#include "base/task/sequence_manager/task_queue_selector.h"

#include <utility>

#include "base/bits.h"
#include "base/logging.h"
#include "base/task/sequence_manager/associated_thread_id.h"
#include "base/task/sequence_manager/task_queue_impl.h"
#include "base/task/sequence_manager/work_queue.h"
#include "base/threading/thread_checker.h"
#include "base/trace_event/traced_value.h"

namespace base {
namespace sequence_manager {
namespace internal {

TaskQueueSelector::TaskQueueSelector(
    scoped_refptr<AssociatedThreadId> associated_thread,
    const SequenceManager::Settings& settings)
    : associated_thread_(std::move(associated_thread)),
#if DCHECK_IS_ON()
      random_task_selection_(settings.random_task_selection_seed != 0),
#endif
      delayed_work_queue_sets_("delayed", this, settings),
      immediate_work_queue_sets_("immediate", this, settings) {
}

TaskQueueSelector::~TaskQueueSelector() = default;

void TaskQueueSelector::AddQueue(internal::TaskQueueImpl* queue) {
  DCHECK_CALLED_ON_VALID_THREAD(associated_thread_->thread_checker);
  DCHECK(queue->IsQueueEnabled());
  AddQueueImpl(queue, TaskQueue::kNormalPriority);
}

void TaskQueueSelector::RemoveQueue(internal::TaskQueueImpl* queue) {
  DCHECK_CALLED_ON_VALID_THREAD(associated_thread_->thread_checker);
  if (queue->IsQueueEnabled()) {
    RemoveQueueImpl(queue);
  }
}

void TaskQueueSelector::EnableQueue(internal::TaskQueueImpl* queue) {
  DCHECK_CALLED_ON_VALID_THREAD(associated_thread_->thread_checker);
  DCHECK(queue->IsQueueEnabled());
  AddQueueImpl(queue, queue->GetQueuePriority());
  if (task_queue_selector_observer_)
    task_queue_selector_observer_->OnTaskQueueEnabled(queue);
}

void TaskQueueSelector::DisableQueue(internal::TaskQueueImpl* queue) {
  DCHECK_CALLED_ON_VALID_THREAD(associated_thread_->thread_checker);
  DCHECK(!queue->IsQueueEnabled());
  RemoveQueueImpl(queue);
}

void TaskQueueSelector::SetQueuePriority(internal::TaskQueueImpl* queue,
                                         TaskQueue::QueuePriority priority) {
  DCHECK_LT(priority, TaskQueue::kQueuePriorityCount);
  DCHECK_CALLED_ON_VALID_THREAD(associated_thread_->thread_checker);
  if (queue->IsQueueEnabled()) {
    ChangeSetIndex(queue, priority);
  } else {
    // Disabled queue is not in any set so we can't use ChangeSetIndex here
    // and have to assign priority for the queue itself.
    queue->delayed_work_queue()->AssignSetIndex(priority);
    queue->immediate_work_queue()->AssignSetIndex(priority);
  }
  DCHECK_EQ(priority, queue->GetQueuePriority());
}

TaskQueue::QueuePriority TaskQueueSelector::NextPriority(
    TaskQueue::QueuePriority priority) {
  DCHECK(priority < TaskQueue::kQueuePriorityCount);
  return static_cast<TaskQueue::QueuePriority>(static_cast<int>(priority) + 1);
}

void TaskQueueSelector::AddQueueImpl(internal::TaskQueueImpl* queue,
                                     TaskQueue::QueuePriority priority) {
#if DCHECK_IS_ON()
  DCHECK(!CheckContainsQueueForTest(queue));
#endif
  delayed_work_queue_sets_.AddQueue(queue->delayed_work_queue(), priority);
  immediate_work_queue_sets_.AddQueue(queue->immediate_work_queue(), priority);
#if DCHECK_IS_ON()
  DCHECK(CheckContainsQueueForTest(queue));
#endif
}

void TaskQueueSelector::ChangeSetIndex(internal::TaskQueueImpl* queue,
                                       TaskQueue::QueuePriority priority) {
#if DCHECK_IS_ON()
  DCHECK(CheckContainsQueueForTest(queue));
#endif
  delayed_work_queue_sets_.ChangeSetIndex(queue->delayed_work_queue(),
                                          priority);
  immediate_work_queue_sets_.ChangeSetIndex(queue->immediate_work_queue(),
                                            priority);
#if DCHECK_IS_ON()
  DCHECK(CheckContainsQueueForTest(queue));
#endif
}

void TaskQueueSelector::RemoveQueueImpl(internal::TaskQueueImpl* queue) {
#if DCHECK_IS_ON()
  DCHECK(CheckContainsQueueForTest(queue));
#endif
  delayed_work_queue_sets_.RemoveQueue(queue->delayed_work_queue());
  immediate_work_queue_sets_.RemoveQueue(queue->immediate_work_queue());

#if DCHECK_IS_ON()
  DCHECK(!CheckContainsQueueForTest(queue));
#endif
}

void TaskQueueSelector::WorkQueueSetBecameEmpty(size_t set_index) {
  non_empty_set_counts_[set_index]--;
  DCHECK_GE(non_empty_set_counts_[set_index], 0);

  // There are no delayed or immediate tasks for |set_index| so remove from
  // |active_priority_tracker_|.
  if (non_empty_set_counts_[set_index] == 0) {
    active_priority_tracker_.SetActive(
        static_cast<TaskQueue::QueuePriority>(set_index), false);
  }
}

void TaskQueueSelector::WorkQueueSetBecameNonEmpty(size_t set_index) {
  non_empty_set_counts_[set_index]++;
  DCHECK_LE(non_empty_set_counts_[set_index], kMaxNonEmptySetCount);

  // There is now a delayed or an immediate task for |set_index|, so add to
  // |active_priority_tracker_|.
  if (non_empty_set_counts_[set_index] == 1) {
    TaskQueue::QueuePriority priority =
        static_cast<TaskQueue::QueuePriority>(set_index);
    active_priority_tracker_.SetActive(priority, true);
  }
}

void TaskQueueSelector::CollectSkippedOverLowerPriorityTasks(
    const internal::WorkQueue* selected_work_queue,
    std::vector<const Task*>* result) const {
  delayed_work_queue_sets_.CollectSkippedOverLowerPriorityTasks(
      selected_work_queue, result);
  immediate_work_queue_sets_.CollectSkippedOverLowerPriorityTasks(
      selected_work_queue, result);
}

#if DCHECK_IS_ON() || !defined(NDEBUG)
bool TaskQueueSelector::CheckContainsQueueForTest(
    const internal::TaskQueueImpl* queue) const {
  bool contains_delayed_work_queue =
      delayed_work_queue_sets_.ContainsWorkQueueForTest(
          queue->delayed_work_queue());

  bool contains_immediate_work_queue =
      immediate_work_queue_sets_.ContainsWorkQueueForTest(
          queue->immediate_work_queue());

  DCHECK_EQ(contains_delayed_work_queue, contains_immediate_work_queue);
  return contains_delayed_work_queue;
}
#endif

WorkQueue* TaskQueueSelector::SelectWorkQueueToService() {
  DCHECK_CALLED_ON_VALID_THREAD(associated_thread_->thread_checker);

  if (!active_priority_tracker_.HasActivePriority())
    return nullptr;

  // Select the priority from which we will select a task. Usually this is
  // the highest priority for which we have work, unless we are starving a lower
  // priority.
  TaskQueue::QueuePriority priority =
      active_priority_tracker_.HighestActivePriority();

  WorkQueue* queue =
#if DCHECK_IS_ON()
      random_task_selection_ ? ChooseWithPriority<SetOperationRandom>(priority)
                             :
#endif
                             ChooseWithPriority<SetOperationOldest>(priority);

  // If we have selected a delayed task while having an immediate task of the
  // same priority, increase the starvation count.
  if (queue->queue_type() == WorkQueue::QueueType::kDelayed &&
      !immediate_work_queue_sets_.IsSetEmpty(priority)) {
    immediate_starvation_count_++;
  } else {
    immediate_starvation_count_ = 0;
  }
  return queue;
}

void TaskQueueSelector::AsValueInto(trace_event::TracedValue* state) const {
  DCHECK_CALLED_ON_VALID_THREAD(associated_thread_->thread_checker);
  state->SetInteger("immediate_starvation_count", immediate_starvation_count_);
}

void TaskQueueSelector::SetTaskQueueSelectorObserver(Observer* observer) {
  task_queue_selector_observer_ = observer;
}

Optional<TaskQueue::QueuePriority>
TaskQueueSelector::GetHighestPendingPriority() const {
  DCHECK_CALLED_ON_VALID_THREAD(associated_thread_->thread_checker);
  if (!active_priority_tracker_.HasActivePriority())
    return nullopt;
  return active_priority_tracker_.HighestActivePriority();
}

void TaskQueueSelector::SetImmediateStarvationCountForTest(
    size_t immediate_starvation_count) {
  immediate_starvation_count_ = immediate_starvation_count;
}

bool TaskQueueSelector::HasTasksWithPriority(
    TaskQueue::QueuePriority priority) {
  return !delayed_work_queue_sets_.IsSetEmpty(priority) ||
         !immediate_work_queue_sets_.IsSetEmpty(priority);
}

TaskQueueSelector::ActivePriorityTracker::ActivePriorityTracker() = default;

void TaskQueueSelector::ActivePriorityTracker::SetActive(
    TaskQueue::QueuePriority priority,
    bool is_active) {
  DCHECK_LT(priority, TaskQueue::QueuePriority::kQueuePriorityCount);
  DCHECK_NE(IsActive(priority), is_active);
  if (is_active) {
    active_priorities_ |= (1u << static_cast<size_t>(priority));
  } else {
    active_priorities_ &= ~(1u << static_cast<size_t>(priority));
  }
}

TaskQueue::QueuePriority
TaskQueueSelector::ActivePriorityTracker::HighestActivePriority() const {
  DCHECK_NE(active_priorities_, 0u)
      << "CountTrailingZeroBits(0) has undefined behavior";
  return static_cast<TaskQueue::QueuePriority>(
      bits::CountTrailingZeroBits(active_priorities_));
}

}  // namespace internal
}  // namespace sequence_manager
}  // namespace base
