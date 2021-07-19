// Copyright 2016 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

#ifndef BASE_TASK_THREAD_POOL_THREAD_GROUP_IMPL_H_
#define BASE_TASK_THREAD_POOL_THREAD_GROUP_IMPL_H_

#include <stddef.h>

#include <memory>
#include <string>
#include <vector>

#include "base/base_export.h"
#include "base/compiler_specific.h"
#include "base/containers/stack.h"
#include "base/gtest_prod_util.h"
#include "base/logging.h"
#include "base/macros.h"
#include "base/memory/ref_counted.h"
#include "base/optional.h"
#include "base/sequenced_task_runner.h"
#include "base/strings/string_piece.h"
#include "base/synchronization/condition_variable.h"
#include "base/synchronization/waitable_event.h"
#include "base/task/thread_pool/task.h"
#include "base/task/thread_pool/task_source.h"
#include "base/task/thread_pool/thread_group.h"
#include "base/task/thread_pool/tracked_ref.h"
#include "base/task/thread_pool/worker_thread.h"
#include "base/task/thread_pool/worker_thread_stack.h"
#include "base/time/time.h"

namespace base {

class HistogramBase;
class WorkerThreadObserver;

namespace internal {

class TaskTracker;

// A group of workers that run Tasks.
//
// The thread group doesn't create threads until Start() is called. Tasks can be
// posted at any time but will not run until after Start() is called.
//
// This class is thread-safe.
class BASE_EXPORT ThreadGroupImpl : public ThreadGroup {
 public:
  // Constructs a group without workers.
  //
  // |histogram_label| is used to label the thread group's histograms as
  // "ThreadPool." + histogram_name + "." + |histogram_label| + extra suffixes.
  // It must not be empty. |thread group_label| is used to label the thread
  // group's threads, it must not be empty. |priority_hint| is the preferred
  // thread priority; the actual thread priority depends on shutdown state and
  // platform capabilities. |task_tracker| keeps track of tasks.
  ThreadGroupImpl(StringPiece histogram_label,
                  StringPiece thread_group_label,
                  ThreadPriority priority_hint,
                  TrackedRef<TaskTracker> task_tracker,
                  TrackedRef<Delegate> delegate);

  // Creates threads, allowing existing and future tasks to run. The thread
  // group runs at most |max_tasks| / |max_best_effort_tasks| unblocked task
  // with any / BEST_EFFORT priority concurrently. It reclaims unused threads
  // after |suggested_reclaim_time|. It uses |service_thread_task_runner| to
  // monitor for blocked tasks. If specified, it notifies
  // |worker_thread_observer| when a worker enters and exits its main function
  // (the observer must not be destroyed before JoinForTesting() has returned).
  // |worker_environment| specifies the environment in which tasks are executed.
  // |may_block_threshold| is the timeout after which a task in a MAY_BLOCK
  // ScopedBlockingCall is considered blocked (the thread group will choose an
  // appropriate value if none is specified). Can only be called once. CHECKs on
  // failure.
  void Start(int max_tasks,
             int max_best_effort_tasks,
             TimeDelta suggested_reclaim_time,
             scoped_refptr<SequencedTaskRunner> service_thread_task_runner,
             WorkerThreadObserver* worker_thread_observer,
             WorkerEnvironment worker_environment,
             Optional<TimeDelta> may_block_threshold = Optional<TimeDelta>());

  // Destroying a ThreadGroupImpl returned by Create() is not allowed in
  // production; it is always leaked. In tests, it can only be destroyed after
  // JoinForTesting() has returned.
  ~ThreadGroupImpl() override;

  // ThreadGroup:
  void JoinForTesting() override;
  size_t GetMaxConcurrentNonBlockedTasksDeprecated() const override;
  void ReportHeartbeatMetrics() const override;
  void DidUpdateCanRunPolicy() override;

  const HistogramBase* num_tasks_before_detach_histogram() const {
    return num_tasks_before_detach_histogram_;
  }

  // Waits until at least |n| workers are idle. Note that while workers are
  // disallowed from cleaning up during this call: tests using a custom
  // |suggested_reclaim_time_| need to be careful to invoke this swiftly after
  // unblocking the waited upon workers as: if a worker is already detached by
  // the time this is invoked, it will never make it onto the idle stack and
  // this call will hang.
  void WaitForWorkersIdleForTesting(size_t n);

  // Waits until at least |n| workers are idle.
  void WaitForWorkersIdleLockRequiredForTesting(size_t n)
      EXCLUSIVE_LOCKS_REQUIRED(lock_);

  // Waits until all workers are idle.
  void WaitForAllWorkersIdleForTesting();

  // Waits until |n| workers have cleaned up (went through
  // WorkerThreadDelegateImpl::OnMainExit()) since the last call to
  // WaitForWorkersCleanedUpForTesting() (or Start() if that wasn't called yet).
  void WaitForWorkersCleanedUpForTesting(size_t n);

  // Returns the number of workers in this thread group.
  size_t NumberOfWorkersForTesting() const;

  // Returns |max_tasks_|.
  size_t GetMaxTasksForTesting() const;

  // Returns the number of workers that are idle (i.e. not running tasks).
  size_t NumberOfIdleWorkersForTesting() const;

 private:
  class ScopedCommandsExecutor;
  class WorkerThreadDelegateImpl;

  // Friend tests so that they can access |blocked_workers_poll_period| and
  // may_block_threshold().
  friend class ThreadGroupImplBlockingTest;
  friend class ThreadGroupImplMayBlockTest;
  FRIEND_TEST_ALL_PREFIXES(ThreadGroupImplBlockingTest,
                           ThreadBlockUnblockPremature);

  // ThreadGroup:
  void UpdateSortKey(TaskSource::Transaction transaction) override;
  void PushTaskSourceAndWakeUpWorkers(
      TransactionWithRegisteredTaskSource transaction_with_task_source)
      override;
  void EnsureEnoughWorkersLockRequired(BaseScopedCommandsExecutor* executor)
      override EXCLUSIVE_LOCKS_REQUIRED(lock_);

  // Creates a worker and schedules its start, if needed, to maintain one idle
  // worker, |max_tasks_| permitting.
  void MaintainAtLeastOneIdleWorkerLockRequired(
      ScopedCommandsExecutor* executor) EXCLUSIVE_LOCKS_REQUIRED(lock_);

  // Returns true if worker cleanup is permitted.
  bool CanWorkerCleanupForTestingLockRequired() EXCLUSIVE_LOCKS_REQUIRED(lock_);

  // Creates a worker, adds it to the thread group, schedules its start and
  // returns it. Cannot be called before Start().
  scoped_refptr<WorkerThread> CreateAndRegisterWorkerLockRequired(
      ScopedCommandsExecutor* executor) EXCLUSIVE_LOCKS_REQUIRED(lock_);

  // Returns the number of workers that are awake (i.e. not on the idle stack).
  size_t GetNumAwakeWorkersLockRequired() const EXCLUSIVE_LOCKS_REQUIRED(lock_);

  // Returns the desired number of awake workers, given current workload and
  // concurrency limits.
  size_t GetDesiredNumAwakeWorkersLockRequired() const
      EXCLUSIVE_LOCKS_REQUIRED(lock_);

  // Examines the list of WorkerThreads and increments |max_tasks_| for each
  // worker that has been within the scope of a MAY_BLOCK ScopedBlockingCall for
  // more than BlockedThreshold(). Reschedules a call if necessary.
  void AdjustMaxTasks();

  // Returns the threshold after which the max tasks is increased to compensate
  // for a worker that is within a MAY_BLOCK ScopedBlockingCall.
  TimeDelta may_block_threshold_for_testing() const {
    return after_start().may_block_threshold;
  }

  // Interval at which the service thread checks for workers in this thread
  // group that have been in a MAY_BLOCK ScopedBlockingCall for more than
  // may_block_threshold().
  TimeDelta blocked_workers_poll_period_for_testing() const {
    return after_start().blocked_workers_poll_period;
  }

  // Starts calling AdjustMaxTasks() periodically on
  // |service_thread_task_runner_|.
  void ScheduleAdjustMaxTasks();

  // Schedules AdjustMaxTasks() through |executor| if required.
  void MaybeScheduleAdjustMaxTasksLockRequired(ScopedCommandsExecutor* executor)
      EXCLUSIVE_LOCKS_REQUIRED(lock_);

  // Returns true if AdjustMaxTasks() should periodically be called on
  // |service_thread_task_runner_|.
  bool ShouldPeriodicallyAdjustMaxTasksLockRequired()
      EXCLUSIVE_LOCKS_REQUIRED(lock_);

  // Updates the minimum priority allowed to run below which tasks should yield.
  // This should be called whenever |num_running_tasks_| or |max_tasks| changes,
  // or when a new task is added to |priority_queue_|.
  void UpdateMinAllowedPriorityLockRequired() EXCLUSIVE_LOCKS_REQUIRED(lock_);

  // Increments/decrements the number of tasks of |priority| that are currently
  // running in this thread group. Must be invoked before/after running a task.
  void DecrementTasksRunningLockRequired(TaskPriority priority)
      EXCLUSIVE_LOCKS_REQUIRED(lock_);
  void IncrementTasksRunningLockRequired(TaskPriority priority)
      EXCLUSIVE_LOCKS_REQUIRED(lock_);

  // Increments/decrements the number of tasks that can run in this thread
  // group.  May only be called in a scope where a task is running with
  // |priority|.
  void DecrementMaxTasksLockRequired(TaskPriority priority)
      EXCLUSIVE_LOCKS_REQUIRED(lock_);
  void IncrementMaxTasksLockRequired(TaskPriority priority)
      EXCLUSIVE_LOCKS_REQUIRED(lock_);

  // Values set at Start() and never modified afterwards.
  struct InitializedInStart {
    InitializedInStart();
    ~InitializedInStart();

#if DCHECK_IS_ON()
    // Set after all members of this struct are set.
    bool initialized = false;
#endif

    // Initial value of |max_tasks_|.
    size_t initial_max_tasks = 0;

    // Suggested reclaim time for workers.
    TimeDelta suggested_reclaim_time;

    // Environment to be initialized per worker.
    WorkerEnvironment worker_environment = WorkerEnvironment::NONE;

    scoped_refptr<SequencedTaskRunner> service_thread_task_runner;

    // Optional observer notified when a worker enters and exits its main.
    WorkerThreadObserver* worker_thread_observer = nullptr;

    bool may_block_without_delay;
    bool fixed_max_best_effort_tasks;

    // Threshold after which the max tasks is increased to compensate for a
    // worker that is within a MAY_BLOCK ScopedBlockingCall.
    TimeDelta may_block_threshold;

    // The period between calls to AdjustMaxTasks() when the thread group is at
    // capacity.
    TimeDelta blocked_workers_poll_period;
  } initialized_in_start_;

  InitializedInStart& in_start() {
#if DCHECK_IS_ON()
    DCHECK(!initialized_in_start_.initialized);
#endif
    return initialized_in_start_;
  }
  const InitializedInStart& after_start() const {
#if DCHECK_IS_ON()
    DCHECK(initialized_in_start_.initialized);
#endif
    return initialized_in_start_;
  }

  const std::string thread_group_label_;
  const ThreadPriority priority_hint_;

  // All workers owned by this thread group.
  std::vector<scoped_refptr<WorkerThread>> workers_ GUARDED_BY(lock_);

  // Maximum number of tasks of any priority / BEST_EFFORT priority that can run
  // concurrently in this thread group.
  size_t max_tasks_ GUARDED_BY(lock_) = 0;
  size_t max_best_effort_tasks_ GUARDED_BY(lock_) = 0;

  // Number of tasks of any priority / BEST_EFFORT priority that are currently
  // running in this thread group.
  size_t num_running_tasks_ GUARDED_BY(lock_) = 0;
  size_t num_running_best_effort_tasks_ GUARDED_BY(lock_) = 0;

  // Number of workers running a task of any priority / BEST_EFFORT priority
  // that are within the scope of a MAY_BLOCK ScopedBlockingCall but haven't
  // caused a max tasks increase yet.
  int num_unresolved_may_block_ GUARDED_BY(lock_) = 0;
  int num_unresolved_best_effort_may_block_ GUARDED_BY(lock_) = 0;

  // Stack of idle workers. Initially, all workers are on this stack. A worker
  // is removed from the stack before its WakeUp() function is called and when
  // it receives work from GetWork() (a worker calls GetWork() when its sleep
  // timeout expires, even if its WakeUp() method hasn't been called). A worker
  // is pushed on this stack when it receives nullptr from GetWork().
  WorkerThreadStack idle_workers_stack_ GUARDED_BY(lock_);

  // Signaled when a worker is added to the idle workers stack.
  std::unique_ptr<ConditionVariable> idle_workers_stack_cv_for_testing_
      GUARDED_BY(lock_);

  // Stack that contains the timestamps of when workers get cleaned up.
  // Timestamps get popped off the stack as new workers are added.
  base::stack<TimeTicks, std::vector<TimeTicks>> cleanup_timestamps_
      GUARDED_BY(lock_);

  // Whether an AdjustMaxTasks() task was posted to the service thread.
  bool adjust_max_tasks_posted_ GUARDED_BY(lock_) = false;

  // Indicates to the delegates that workers are not permitted to cleanup.
  bool worker_cleanup_disallowed_for_testing_ GUARDED_BY(lock_) = false;

  // Counts the number of workers cleaned up (went through
  // WorkerThreadDelegateImpl::OnMainExit()) since the last call to
  // WaitForWorkersCleanedUpForTesting() (or Start() if that wasn't called yet).
  // |some_workers_cleaned_up_for_testing_| is true if this was ever
  // incremented. Tests with a custom |suggested_reclaim_time_| can wait on a
  // specific number of workers being cleaned up via
  // WaitForWorkersCleanedUpForTesting().
  size_t num_workers_cleaned_up_for_testing_ GUARDED_BY(lock_) = 0;
#if DCHECK_IS_ON()
  bool some_workers_cleaned_up_for_testing_ GUARDED_BY(lock_) = false;
#endif

  // Signaled, if non-null, when |num_workers_cleaned_up_for_testing_| is
  // incremented.
  std::unique_ptr<ConditionVariable> num_workers_cleaned_up_for_testing_cv_
      GUARDED_BY(lock_);

  // Set at the start of JoinForTesting().
  bool join_for_testing_started_ GUARDED_BY(lock_) = false;

  // Cached HistogramBase pointers, can be accessed without
  // holding |lock_|. If |lock_| is held, add new samples using
  // ThreadGroupImpl::ScopedCommandsExecutor (increase
  // |scheduled_histogram_samples_| size as needed) to defer until after |lock_|
  // release, due to metrics system callbacks which may schedule tasks.

  // ThreadPool.DetachDuration.[thread group name] histogram. Intentionally
  // leaked.
  HistogramBase* const detach_duration_histogram_;

  // ThreadPool.NumTasksBeforeDetach.[thread group name] histogram.
  // Intentionally leaked.
  HistogramBase* const num_tasks_before_detach_histogram_;

  // ThreadPool.NumWorkers.[thread group name] histogram.
  // Intentionally leaked.
  HistogramBase* const num_workers_histogram_;

  // ThreadPool.NumActiveWorkers.[thread group name] histogram.
  // Intentionally leaked.
  HistogramBase* const num_active_workers_histogram_;

  // Ensures recently cleaned up workers (ref.
  // WorkerThreadDelegateImpl::CleanupLockRequired()) had time to exit as
  // they have a raw reference to |this| (and to TaskTracker) which can
  // otherwise result in racy use-after-frees per no longer being part of
  // |workers_| and hence not being explicitly joined in JoinForTesting():
  // https://crbug.com/810464. Uses AtomicRefCount to make its only public
  // method thread-safe.
  TrackedRefFactory<ThreadGroupImpl> tracked_ref_factory_;

  DISALLOW_COPY_AND_ASSIGN(ThreadGroupImpl);
};

}  // namespace internal
}  // namespace base

#endif  // BASE_TASK_THREAD_POOL_THREAD_GROUP_IMPL_H_
