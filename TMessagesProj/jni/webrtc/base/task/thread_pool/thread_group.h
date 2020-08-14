// Copyright 2016 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

#ifndef BASE_TASK_THREAD_POOL_THREAD_GROUP_H_
#define BASE_TASK_THREAD_POOL_THREAD_GROUP_H_

#include "base/base_export.h"
#include "base/memory/ref_counted.h"
#include "base/task/common/checked_lock.h"
#include "base/task/thread_pool/priority_queue.h"
#include "base/task/thread_pool/task.h"
#include "base/task/thread_pool/task_source.h"
#include "base/task/thread_pool/tracked_ref.h"
#include "build/build_config.h"

#if defined(OS_WIN)
#include "base/win/scoped_windows_thread_environment.h"
#endif

namespace base {
namespace internal {

class TaskTracker;

// Interface and base implementation for a thread group. A thread group is a
// subset of the threads in the thread pool (see GetThreadGroupForTraits() for
// thread group selection logic when posting tasks and creating task runners).
class BASE_EXPORT ThreadGroup {
 public:
  // Delegate interface for ThreadGroup.
  class BASE_EXPORT Delegate {
   public:
    virtual ~Delegate() = default;

    // Invoked when a TaskSource with |traits| is non-empty after the
    // ThreadGroup has run a task from it. The implementation must return the
    // thread group in which the TaskSource should be reenqueued.
    virtual ThreadGroup* GetThreadGroupForTraits(const TaskTraits& traits) = 0;
  };

  enum class WorkerEnvironment {
    // No special worker environment required.
    NONE,
#if defined(OS_WIN)
    // Initialize a COM MTA on the worker.
    COM_MTA,
    // Initialize a COM STA on the worker.
    COM_STA,
#endif  // defined(OS_WIN)
  };

  virtual ~ThreadGroup();

  // Registers the thread group in TLS.
  void BindToCurrentThread();

  // Resets the thread group in TLS.
  void UnbindFromCurrentThread();

  // Returns true if the thread group is registered in TLS.
  bool IsBoundToCurrentThread() const;

  // Removes |task_source| from |priority_queue_|. Returns a
  // RegisteredTaskSource that evaluats to true if successful, or false if
  // |task_source| is not currently in |priority_queue_|, such as when a worker
  // is running a task from it.
  RegisteredTaskSource RemoveTaskSource(const TaskSource& task_source);

  // Updates the position of the TaskSource in |transaction| in this
  // ThreadGroup's PriorityQueue based on the TaskSource's current traits.
  //
  // Implementations should instantiate a concrete ScopedCommandsExecutor and
  // invoke UpdateSortKeyImpl().
  virtual void UpdateSortKey(TaskSource::Transaction transaction) = 0;

  // Pushes the TaskSource in |transaction_with_task_source| into this
  // ThreadGroup's PriorityQueue and wakes up workers as appropriate.
  //
  // Implementations should instantiate a concrete ScopedCommandsExecutor and
  // invoke PushTaskSourceAndWakeUpWorkersImpl().
  virtual void PushTaskSourceAndWakeUpWorkers(
      TransactionWithRegisteredTaskSource transaction_with_task_source) = 0;

  // Removes all task sources from this ThreadGroup's PriorityQueue and enqueues
  // them in another |destination_thread_group|. After this method is called,
  // any task sources posted to this ThreadGroup will be forwarded to
  // |destination_thread_group|.
  //
  // TODO(crbug.com/756547): Remove this method once the UseNativeThreadPool
  // experiment is complete.
  void InvalidateAndHandoffAllTaskSourcesToOtherThreadGroup(
      ThreadGroup* destination_thread_group);

  // Returns true if a task with |priority| running in this thread group should
  // return ASAP, either because this priority is not allowed to run or because
  // work of higher priority is pending. Thread-safe but may return an outdated
  // result (if a task unnecessarily yields due to this, it will simply be
  // re-scheduled).
  bool ShouldYield(TaskPriority priority) const;

  // Prevents new tasks from starting to run and waits for currently running
  // tasks to complete their execution. It is guaranteed that no thread will do
  // work on behalf of this ThreadGroup after this returns. It is
  // invalid to post a task once this is called. TaskTracker::Flush() can be
  // called before this to complete existing tasks, which might otherwise post a
  // task during JoinForTesting(). This can only be called once.
  virtual void JoinForTesting() = 0;

  // Returns the maximum number of non-blocked tasks that can run concurrently
  // in this ThreadGroup.
  //
  // TODO(fdoray): Remove this method. https://crbug.com/687264
  virtual size_t GetMaxConcurrentNonBlockedTasksDeprecated() const = 0;

  // Reports relevant metrics per implementation.
  virtual void ReportHeartbeatMetrics() const = 0;

  // Wakes up workers as appropriate for the new CanRunPolicy policy. Must be
  // called after an update to CanRunPolicy in TaskTracker.
  virtual void DidUpdateCanRunPolicy() = 0;

 protected:
  // Derived classes must implement a ScopedCommandsExecutor that derives from
  // this to perform operations at the end of a scope, when all locks have been
  // released.
  class BaseScopedCommandsExecutor {
   public:
    void ScheduleReleaseTaskSource(RegisteredTaskSource task_source);

   protected:
    BaseScopedCommandsExecutor();
    ~BaseScopedCommandsExecutor();

   private:
    std::vector<RegisteredTaskSource> task_sources_to_release_;

    DISALLOW_COPY_AND_ASSIGN(BaseScopedCommandsExecutor);
  };

  // Allows a task source to be pushed to a ThreadGroup's PriorityQueue at the
  // end of a scope, when all locks have been released.
  class ScopedReenqueueExecutor {
   public:
    ScopedReenqueueExecutor();
    ~ScopedReenqueueExecutor();

    // A TransactionWithRegisteredTaskSource and the ThreadGroup in which it
    // should be enqueued.
    void SchedulePushTaskSourceAndWakeUpWorkers(
        TransactionWithRegisteredTaskSource transaction_with_task_source,
        ThreadGroup* destination_thread_group);

   private:
    // A TransactionWithRegisteredTaskSource and the thread group in which it
    // should be enqueued.
    Optional<TransactionWithRegisteredTaskSource> transaction_with_task_source_;
    ThreadGroup* destination_thread_group_ = nullptr;

    DISALLOW_COPY_AND_ASSIGN(ScopedReenqueueExecutor);
  };

  // |predecessor_thread_group| is a ThreadGroup whose lock can be acquired
  // before the constructed ThreadGroup's lock. This is necessary to move all
  // task sources from |predecessor_thread_group| to the constructed ThreadGroup
  // and support the UseNativeThreadPool experiment.
  //
  // TODO(crbug.com/756547): Remove |predecessor_thread_group| once the
  // experiment is complete.
  ThreadGroup(TrackedRef<TaskTracker> task_tracker,
              TrackedRef<Delegate> delegate,
              ThreadGroup* predecessor_thread_group = nullptr);

#if defined(OS_WIN)
  static std::unique_ptr<win::ScopedWindowsThreadEnvironment>
  GetScopedWindowsThreadEnvironment(WorkerEnvironment environment);
#endif

  const TrackedRef<TaskTracker> task_tracker_;
  const TrackedRef<Delegate> delegate_;

  // Returns the number of workers required of workers to run all queued
  // BEST_EFFORT task sources allowed to run by the current CanRunPolicy.
  size_t GetNumAdditionalWorkersForBestEffortTaskSourcesLockRequired() const
      EXCLUSIVE_LOCKS_REQUIRED(lock_);

  // Returns the number of workers required to run all queued
  // USER_VISIBLE/USER_BLOCKING task sources allowed to run by the current
  // CanRunPolicy.
  size_t GetNumAdditionalWorkersForForegroundTaskSourcesLockRequired() const
      EXCLUSIVE_LOCKS_REQUIRED(lock_);

  // Ensures that there are enough workers to run queued task sources.
  // |executor| is forwarded from the one received in
  // PushTaskSourceAndWakeUpWorkersImpl()
  virtual void EnsureEnoughWorkersLockRequired(
      BaseScopedCommandsExecutor* executor) EXCLUSIVE_LOCKS_REQUIRED(lock_) = 0;

  // Reenqueues a |transaction_with_task_source| from which a Task just ran in
  // the current ThreadGroup into the appropriate ThreadGroup.
  void ReEnqueueTaskSourceLockRequired(
      BaseScopedCommandsExecutor* workers_executor,
      ScopedReenqueueExecutor* reenqueue_executor,
      TransactionWithRegisteredTaskSource transaction_with_task_source)
      EXCLUSIVE_LOCKS_REQUIRED(lock_);

  // Returns the next task source from |priority_queue_| if permitted to run and
  // pops |priority_queue_| if the task source returned no longer needs to be
  // queued (reached its maximum concurrency). Otherwise returns nullptr and
  // pops |priority_queue_| so this can be called again.
  RegisteredTaskSource TakeRegisteredTaskSource(
      BaseScopedCommandsExecutor* executor) EXCLUSIVE_LOCKS_REQUIRED(lock_);

  // Must be invoked by implementations of the corresponding non-Impl() methods.
  void UpdateSortKeyImpl(BaseScopedCommandsExecutor* executor,
                         TaskSource::Transaction transaction);
  void PushTaskSourceAndWakeUpWorkersImpl(
      BaseScopedCommandsExecutor* executor,
      TransactionWithRegisteredTaskSource transaction_with_task_source);

  // Synchronizes accesses to all members of this class which are neither const,
  // atomic, nor immutable after start. Since this lock is a bottleneck to post
  // and schedule work, only simple data structure manipulations are allowed
  // within its scope (no thread creation or wake up).
  mutable CheckedLock lock_;

  // PriorityQueue from which all threads of this ThreadGroup get work.
  PriorityQueue priority_queue_ GUARDED_BY(lock_);

  // Minimum priority allowed to run below which tasks should yield. This is
  // expected to be always kept up-to-date by derived classes when |lock_| is
  // released. It is annotated as GUARDED_BY(lock_) because it is always updated
  // under the lock (to avoid races with other state during the update) but it
  // is nonetheless always safe to read it without the lock (since it's atomic).
  std::atomic<TaskPriority> min_allowed_priority_ GUARDED_BY(lock_){
      TaskPriority::BEST_EFFORT};

  // If |replacement_thread_group_| is non-null, this ThreadGroup is invalid and
  // all task sources should be scheduled on |replacement_thread_group_|. Used
  // to support the UseNativeThreadPool experiment.
  ThreadGroup* replacement_thread_group_ = nullptr;

 private:
  DISALLOW_COPY_AND_ASSIGN(ThreadGroup);
};

}  // namespace internal
}  // namespace base

#endif  // BASE_TASK_THREAD_POOL_THREAD_GROUP_H_
