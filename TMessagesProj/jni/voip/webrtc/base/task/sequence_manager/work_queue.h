// Copyright 2015 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

#ifndef BASE_TASK_SEQUENCE_MANAGER_WORK_QUEUE_H_
#define BASE_TASK_SEQUENCE_MANAGER_WORK_QUEUE_H_

#include "base/base_export.h"
#include "base/task/common/intrusive_heap.h"
#include "base/task/sequence_manager/enqueue_order.h"
#include "base/task/sequence_manager/sequenced_task_source.h"
#include "base/task/sequence_manager/task_queue_impl.h"
#include "base/trace_event/trace_event.h"
#include "base/trace_event/traced_value.h"

namespace base {
namespace sequence_manager {
namespace internal {

class WorkQueueSets;

// This class keeps track of immediate and delayed tasks which are due to run
// now. It interfaces deeply with WorkQueueSets which keeps track of which queue
// (with a given priority) contains the oldest task.
//
// If a fence is inserted, WorkQueue behaves normally up until
// TakeTaskFromWorkQueue reaches or exceeds the fence.  At that point it the
// API subset used by WorkQueueSets pretends the WorkQueue is empty until the
// fence is removed.  This functionality is a primitive intended for use by
// throttling mechanisms.
class BASE_EXPORT WorkQueue {
 public:
  using QueueType = internal::TaskQueueImpl::WorkQueueType;

  // Note |task_queue| can be null if queue_type is kNonNestable.
  WorkQueue(TaskQueueImpl* task_queue, const char* name, QueueType queue_type);
  ~WorkQueue();

  // Associates this work queue with the given work queue sets. This must be
  // called before any tasks can be inserted into this work queue.
  void AssignToWorkQueueSets(WorkQueueSets* work_queue_sets);

  // Assigns the current set index.
  void AssignSetIndex(size_t work_queue_set_index);

  void AsValueInto(TimeTicks now, trace_event::TracedValue* state) const;

  // Returns true if the |tasks_| is empty. This method ignores any fences.
  bool Empty() const { return tasks_.empty(); }

  // If the |tasks_| isn't empty and a fence hasn't been reached,
  // |enqueue_order| gets set to the enqueue order of the front task and the
  // function returns true. Otherwise the function returns false.
  bool GetFrontTaskEnqueueOrder(EnqueueOrder* enqueue_order) const;

  // Returns the first task in this queue or null if the queue is empty. This
  // method ignores any fences.
  const Task* GetFrontTask() const;

  // Returns the last task in this queue or null if the queue is empty. This
  // method ignores any fences.
  const Task* GetBackTask() const;

  // Pushes the task onto the |tasks_| and if a fence hasn't been reached
  // it informs the WorkQueueSets if the head changed.
  void Push(Task task);

  // RAII helper that helps efficiently push N Tasks to a WorkQueue.
  class BASE_EXPORT TaskPusher {
   public:
    TaskPusher(const TaskPusher&) = delete;
    TaskPusher(TaskPusher&& other);
    ~TaskPusher();

    void Push(Task* task);

   private:
    friend class WorkQueue;

    explicit TaskPusher(WorkQueue* work_queue);

    WorkQueue* work_queue_;
    const bool was_empty_;
  };

  // Returns an RAII helper to efficiently push multiple tasks.
  TaskPusher CreateTaskPusher();

  // Pushes the task onto the front of the |tasks_| and if it's before any
  // fence it informs the WorkQueueSets the head changed. Use with caution this
  // API can easily lead to task starvation if misused.
  void PushNonNestableTaskToFront(Task task);

  // Reloads the empty |tasks_| with
  // |task_queue_->TakeImmediateIncomingQueue| and if a fence hasn't been
  // reached it informs the WorkQueueSets if the head changed.
  void TakeImmediateIncomingQueueTasks();

  size_t Size() const { return tasks_.size(); }

  size_t Capacity() const { return tasks_.capacity(); }

  // Pulls a task off the |tasks_| and informs the WorkQueueSets.  If the
  // task removed had an enqueue order >= the current fence then WorkQueue
  // pretends to be empty as far as the WorkQueueSets is concerned.
  Task TakeTaskFromWorkQueue();

  // Removes all canceled tasks from the head of the list. Returns true if any
  // tasks were removed.
  bool RemoveAllCanceledTasksFromFront();

  const char* name() const { return name_; }

  TaskQueueImpl* task_queue() const { return task_queue_; }

  WorkQueueSets* work_queue_sets() const { return work_queue_sets_; }

  size_t work_queue_set_index() const { return work_queue_set_index_; }

  base::internal::HeapHandle heap_handle() const { return heap_handle_; }

  void set_heap_handle(base::internal::HeapHandle handle) {
    heap_handle_ = handle;
  }

  QueueType queue_type() const { return queue_type_; }

  // Returns true if the front task in this queue has an older enqueue order
  // than the front task of |other_queue|. Both queue are assumed to be
  // non-empty. This method ignores any fences.
  bool ShouldRunBefore(const WorkQueue* other_queue) const;

  // Submit a fence. When TakeTaskFromWorkQueue encounters a task whose
  // enqueue_order is >= |fence| then the WorkQueue will start pretending to be.
  // empty.
  // Inserting a fence may supersede a previous one and unblock some tasks.
  // Returns true if any tasks where unblocked, returns false otherwise.
  bool InsertFence(EnqueueOrder fence);

  // Submit a fence without triggering a WorkQueueSets notification.
  // Caller must ensure that WorkQueueSets are properly updated.
  // This method should not be called when a fence is already present.
  void InsertFenceSilently(EnqueueOrder fence);

  // Removes any fences that where added and if WorkQueue was pretending to be
  // empty, then the real value is reported to WorkQueueSets. Returns true if
  // any tasks where unblocked.
  bool RemoveFence();

  // Returns true if any tasks are blocked by the fence. Returns true if the
  // queue is empty and fence has been set (i.e. future tasks would be blocked).
  // Otherwise returns false.
  bool BlockedByFence() const;

  // Shrinks |tasks_| if it's wasting memory.
  void MaybeShrinkQueue();

  // Delete all tasks within this WorkQueue.
  void DeletePendingTasks();

  // Test support function. This should not be used in production code.
  void PopTaskForTesting();

  // Iterates through |tasks_| adding any that are older than |reference| to
  // |result|.
  void CollectTasksOlderThan(EnqueueOrder reference,
                             std::vector<const Task*>* result) const;

 private:
  bool InsertFenceImpl(EnqueueOrder fence);

  TaskQueueImpl::TaskDeque tasks_;
  WorkQueueSets* work_queue_sets_ = nullptr;  // NOT OWNED.
  TaskQueueImpl* const task_queue_;           // NOT OWNED.
  size_t work_queue_set_index_ = 0;

  // Iff the queue isn't empty (or appearing to be empty due to a fence) then
  // |heap_handle_| will be valid and correspond to this queue's location within
  // an IntrusiveHeap inside the WorkQueueSet.
  base::internal::HeapHandle heap_handle_;
  const char* const name_;
  EnqueueOrder fence_;
  const QueueType queue_type_;

  DISALLOW_COPY_AND_ASSIGN(WorkQueue);
};

}  // namespace internal
}  // namespace sequence_manager
}  // namespace base

#endif  // BASE_TASK_SEQUENCE_MANAGER_WORK_QUEUE_H_
