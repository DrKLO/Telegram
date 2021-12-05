// Copyright 2015 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

#ifndef BASE_TASK_SEQUENCE_MANAGER_TASK_QUEUE_SELECTOR_H_
#define BASE_TASK_SEQUENCE_MANAGER_TASK_QUEUE_SELECTOR_H_

#include <stddef.h>

#include "base/base_export.h"
#include "base/macros.h"
#include "base/pending_task.h"
#include "base/task/sequence_manager/sequence_manager.h"
#include "base/task/sequence_manager/task_queue_selector_logic.h"
#include "base/task/sequence_manager/work_queue_sets.h"

namespace base {
namespace sequence_manager {
namespace internal {

class AssociatedThreadId;

// TaskQueueSelector is used by the SchedulerHelper to enable prioritization
// of particular task queues.
class BASE_EXPORT TaskQueueSelector : public WorkQueueSets::Observer {
 public:
  TaskQueueSelector(scoped_refptr<AssociatedThreadId> associated_thread,
                    const SequenceManager::Settings& settings);

  ~TaskQueueSelector() override;

  // Called to register a queue that can be selected. This function is called
  // on the main thread.
  void AddQueue(internal::TaskQueueImpl* queue);

  // The specified work will no longer be considered for selection. This
  // function is called on the main thread.
  void RemoveQueue(internal::TaskQueueImpl* queue);

  // Make |queue| eligible for selection. This function is called on the main
  // thread. Must only be called if |queue| is disabled.
  void EnableQueue(internal::TaskQueueImpl* queue);

  // Disable selection from |queue|. Must only be called if |queue| is enabled.
  void DisableQueue(internal::TaskQueueImpl* queue);

  // Called get or set the priority of |queue|.
  void SetQueuePriority(internal::TaskQueueImpl* queue,
                        TaskQueue::QueuePriority priority);

  // Called to choose the work queue from which the next task should be taken
  // and run. Return the queue to service if there is one or null otherwise.
  // This function is called on the main thread.
  WorkQueue* SelectWorkQueueToService();

  // Serialize the selector state for tracing.
  void AsValueInto(trace_event::TracedValue* state) const;

  class BASE_EXPORT Observer {
   public:
    virtual ~Observer() = default;

    // Called when |queue| transitions from disabled to enabled.
    virtual void OnTaskQueueEnabled(internal::TaskQueueImpl* queue) = 0;
  };

  // Called once to set the Observer. This function is called
  // on the main thread. If |observer| is null, then no callbacks will occur.
  void SetTaskQueueSelectorObserver(Observer* observer);

  // Returns the priority of the most important pending task if one exists.
  // O(1).
  Optional<TaskQueue::QueuePriority> GetHighestPendingPriority() const;

  // WorkQueueSets::Observer implementation:
  void WorkQueueSetBecameEmpty(size_t set_index) override;
  void WorkQueueSetBecameNonEmpty(size_t set_index) override;

  // Populates |result| with tasks with lower priority than the first task from
  // |selected_work_queue| which could otherwise run now.
  void CollectSkippedOverLowerPriorityTasks(
      const internal::WorkQueue* selected_work_queue,
      std::vector<const Task*>* result) const;

 protected:
  WorkQueueSets* delayed_work_queue_sets() { return &delayed_work_queue_sets_; }

  WorkQueueSets* immediate_work_queue_sets() {
    return &immediate_work_queue_sets_;
  }

  // This method will force select an immediate task if those are being
  // starved by delayed tasks.
  void SetImmediateStarvationCountForTest(size_t immediate_starvation_count);

  // Maximum number of delayed tasks tasks which can be run while there's a
  // waiting non-delayed task.
  static const size_t kMaxDelayedStarvationTasks = 3;

  // Tracks which priorities are currently active, meaning there are pending
  // runnable tasks with that priority. Because there are only a handful of
  // priorities, and because we always run tasks in order from highest to lowest
  // priority, we can use a single integer to represent enabled priorities,
  // using a bit per priority.
  class BASE_EXPORT ActivePriorityTracker {
   public:
    ActivePriorityTracker();

    bool HasActivePriority() const { return active_priorities_ != 0; }

    bool IsActive(TaskQueue::QueuePriority priority) const {
      return active_priorities_ & (1u << static_cast<size_t>(priority));
    }

    void SetActive(TaskQueue::QueuePriority priority, bool is_active);

    TaskQueue::QueuePriority HighestActivePriority() const;

   private:
    static_assert(TaskQueue::QueuePriority::kQueuePriorityCount <
                      sizeof(size_t) * 8,
                  "The number of priorities must be strictly less than the "
                  "number of bits of |active_priorities_|!");
    size_t active_priorities_ = 0;
  };

  /*
   * SetOperation is used to configure ChooseWithPriority() and must have:
   *
   * static WorkQueue* GetWithPriority(const WorkQueueSets& sets,
   *                                   TaskQueue::QueuePriority priority);
   *
   * static WorkQueue* GetWithPriorityAndEnqueueOrder(
   *     const WorkQueueSets& sets,
   *     TaskQueue::QueuePriority priority
   *     EnqueueOrder* enqueue_order);
   */

  // The default
  struct SetOperationOldest {
    static WorkQueue* GetWithPriority(const WorkQueueSets& sets,
                                      TaskQueue::QueuePriority priority) {
      return sets.GetOldestQueueInSet(priority);
    }

    static WorkQueue* GetWithPriorityAndEnqueueOrder(
        const WorkQueueSets& sets,
        TaskQueue::QueuePriority priority,
        EnqueueOrder* enqueue_order) {
      return sets.GetOldestQueueAndEnqueueOrderInSet(priority, enqueue_order);
    }
  };

#if DCHECK_IS_ON()
  struct SetOperationRandom {
    static WorkQueue* GetWithPriority(const WorkQueueSets& sets,
                                      TaskQueue::QueuePriority priority) {
      return sets.GetRandomQueueInSet(priority);
    }

    static WorkQueue* GetWithPriorityAndEnqueueOrder(
        const WorkQueueSets& sets,
        TaskQueue::QueuePriority priority,
        EnqueueOrder* enqueue_order) {
      return sets.GetRandomQueueAndEnqueueOrderInSet(priority, enqueue_order);
    }
  };
#endif  // DCHECK_IS_ON()

  template <typename SetOperation>
  WorkQueue* ChooseWithPriority(TaskQueue::QueuePriority priority) const {
    // Select an immediate work queue if we are starving immediate tasks.
    if (immediate_starvation_count_ >= kMaxDelayedStarvationTasks) {
      WorkQueue* queue =
          SetOperation::GetWithPriority(immediate_work_queue_sets_, priority);
      if (queue)
        return queue;
      return SetOperation::GetWithPriority(delayed_work_queue_sets_, priority);
    }
    return ChooseImmediateOrDelayedTaskWithPriority<SetOperation>(priority);
  }

 private:
  void ChangeSetIndex(internal::TaskQueueImpl* queue,
                      TaskQueue::QueuePriority priority);
  void AddQueueImpl(internal::TaskQueueImpl* queue,
                    TaskQueue::QueuePriority priority);
  void RemoveQueueImpl(internal::TaskQueueImpl* queue);

#if DCHECK_IS_ON() || !defined(NDEBUG)
  bool CheckContainsQueueForTest(const internal::TaskQueueImpl* queue) const;
#endif

  template <typename SetOperation>
  WorkQueue* ChooseImmediateOrDelayedTaskWithPriority(
      TaskQueue::QueuePriority priority) const {
    EnqueueOrder immediate_enqueue_order;
    WorkQueue* immediate_queue = SetOperation::GetWithPriorityAndEnqueueOrder(
        immediate_work_queue_sets_, priority, &immediate_enqueue_order);
    if (immediate_queue) {
      EnqueueOrder delayed_enqueue_order;
      WorkQueue* delayed_queue = SetOperation::GetWithPriorityAndEnqueueOrder(
          delayed_work_queue_sets_, priority, &delayed_enqueue_order);
      if (!delayed_queue)
        return immediate_queue;

      if (immediate_enqueue_order < delayed_enqueue_order) {
        return immediate_queue;
      } else {
        return delayed_queue;
      }
    }
    return SetOperation::GetWithPriority(delayed_work_queue_sets_, priority);
  }

  // Returns the priority which is next after |priority|.
  static TaskQueue::QueuePriority NextPriority(
      TaskQueue::QueuePriority priority);

  // Returns true if there are pending tasks with priority |priority|.
  bool HasTasksWithPriority(TaskQueue::QueuePriority priority);

  scoped_refptr<AssociatedThreadId> associated_thread_;

#if DCHECK_IS_ON()
  const bool random_task_selection_ = false;
#endif

  // Count of the number of sets (delayed or immediate) for each priority.
  // Should only contain 0, 1 or 2.
  std::array<int, TaskQueue::kQueuePriorityCount> non_empty_set_counts_ = {{0}};

  static constexpr const int kMaxNonEmptySetCount = 2;

  // List of active priorities, which is used to work out which priority to run
  // next.
  ActivePriorityTracker active_priority_tracker_;

  WorkQueueSets delayed_work_queue_sets_;
  WorkQueueSets immediate_work_queue_sets_;
  size_t immediate_starvation_count_ = 0;

  Observer* task_queue_selector_observer_ = nullptr;  // Not owned.
  DISALLOW_COPY_AND_ASSIGN(TaskQueueSelector);
};

}  // namespace internal
}  // namespace sequence_manager
}  // namespace base

#endif  // BASE_TASK_SEQUENCE_MANAGER_TASK_QUEUE_SELECTOR_H_
