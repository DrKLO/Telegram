// Copyright 2016 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

#include "base/task/thread_pool/thread_group_impl.h"

#include <stddef.h>

#include <algorithm>
#include <type_traits>
#include <utility>

#include "base/atomicops.h"
#include "base/auto_reset.h"
#include "base/bind.h"
#include "base/bind_helpers.h"
#include "base/compiler_specific.h"
#include "base/containers/stack_container.h"
#include "base/feature_list.h"
#include "base/location.h"
#include "base/memory/ptr_util.h"
#include "base/metrics/histogram.h"
#include "base/numerics/clamped_math.h"
#include "base/optional.h"
#include "base/sequence_token.h"
#include "base/strings/string_util.h"
#include "base/strings/stringprintf.h"
#include "base/task/task_features.h"
#include "base/task/task_traits.h"
#include "base/task/thread_pool/task_tracker.h"
#include "base/threading/platform_thread.h"
#include "base/threading/scoped_blocking_call.h"
#include "base/threading/scoped_blocking_call_internal.h"
#include "base/threading/thread_checker.h"
#include "base/threading/thread_restrictions.h"
#include "base/time/time_override.h"
#include "build/build_config.h"

#if defined(OS_WIN)
#include "base/win/scoped_com_initializer.h"
#include "base/win/scoped_windows_thread_environment.h"
#include "base/win/scoped_winrt_initializer.h"
#include "base/win/windows_version.h"
#endif  // defined(OS_WIN)

namespace base {
namespace internal {

namespace {

constexpr char kDetachDurationHistogramPrefix[] = "ThreadPool.DetachDuration.";
constexpr char kNumTasksBeforeDetachHistogramPrefix[] =
    "ThreadPool.NumTasksBeforeDetach.";
constexpr char kNumWorkersHistogramPrefix[] = "ThreadPool.NumWorkers.";
constexpr char kNumActiveWorkersHistogramPrefix[] =
    "ThreadPool.NumActiveWorkers.";
constexpr size_t kMaxNumberOfWorkers = 256;

// In a background thread group:
// - Blocking calls take more time than in a foreground thread group.
// - We want to minimize impact on foreground work, not maximize execution
//   throughput.
// For these reasons, the timeout to increase the maximum number of concurrent
// tasks when there is a MAY_BLOCK ScopedBlockingCall is *long*. It is not
// infinite because execution throughput should not be reduced forever if a task
// blocks forever.
//
// TODO(fdoray): On platforms without background thread groups, blocking in a
// BEST_EFFORT task should:
// 1. Increment the maximum number of concurrent tasks after a *short* timeout,
//    to allow scheduling of USER_VISIBLE/USER_BLOCKING tasks.
// 2. Increment the maximum number of concurrent BEST_EFFORT tasks after a
//    *long* timeout, because we only want to allow more BEST_EFFORT tasks to be
//    be scheduled concurrently when we believe that a BEST_EFFORT task is
//    blocked forever.
// Currently, only 1. is true as the configuration is per thread group.
// TODO(https://crbug.com/927755): Fix racy condition when MayBlockThreshold ==
// BlockedWorkersPoll.
constexpr TimeDelta kForegroundMayBlockThreshold =
    TimeDelta::FromMilliseconds(1000);
constexpr TimeDelta kForegroundBlockedWorkersPoll =
    TimeDelta::FromMilliseconds(1200);
constexpr TimeDelta kBackgroundMayBlockThreshold = TimeDelta::FromSeconds(10);
constexpr TimeDelta kBackgroundBlockedWorkersPoll = TimeDelta::FromSeconds(12);

// Only used in DCHECKs.
bool ContainsWorker(const std::vector<scoped_refptr<WorkerThread>>& workers,
                    const WorkerThread* worker) {
  auto it = std::find_if(workers.begin(), workers.end(),
                         [worker](const scoped_refptr<WorkerThread>& i) {
                           return i.get() == worker;
                         });
  return it != workers.end();
}

}  // namespace

// Upon destruction, executes actions that control the number of active workers.
// Useful to satisfy locking requirements of these actions.
class ThreadGroupImpl::ScopedCommandsExecutor
    : public ThreadGroup::BaseScopedCommandsExecutor {
 public:
  ScopedCommandsExecutor(ThreadGroupImpl* outer) : outer_(outer) {}

  ~ScopedCommandsExecutor() { FlushImpl(); }

  void ScheduleWakeUp(scoped_refptr<WorkerThread> worker) {
    workers_to_wake_up_.AddWorker(std::move(worker));
  }

  void ScheduleStart(scoped_refptr<WorkerThread> worker) {
    workers_to_start_.AddWorker(std::move(worker));
  }

  void FlushWorkerCreation(CheckedLock* held_lock) {
    if (workers_to_wake_up_.empty() && workers_to_start_.empty())
      return;
    CheckedAutoUnlock auto_unlock(*held_lock);
    FlushImpl();
    workers_to_wake_up_.clear();
    workers_to_start_.clear();
    must_schedule_adjust_max_tasks_ = false;
  }

  void ScheduleAdjustMaxTasks() {
    DCHECK(!must_schedule_adjust_max_tasks_);
    must_schedule_adjust_max_tasks_ = true;
  }

  void ScheduleAddHistogramSample(HistogramBase* histogram,
                                  HistogramBase::Sample sample) {
    scheduled_histogram_samples_->emplace_back(histogram, sample);
  }

 private:
  class WorkerContainer {
   public:
    WorkerContainer() = default;

    void AddWorker(scoped_refptr<WorkerThread> worker) {
      if (!worker)
        return;
      if (!first_worker_)
        first_worker_ = std::move(worker);
      else
        additional_workers_.push_back(std::move(worker));
    }

    template <typename Action>
    void ForEachWorker(Action action) {
      if (first_worker_) {
        action(first_worker_.get());
        for (scoped_refptr<WorkerThread> worker : additional_workers_)
          action(worker.get());
      } else {
        DCHECK(additional_workers_.empty());
      }
    }

    bool empty() const { return first_worker_ == nullptr; }

    void clear() {
      first_worker_.reset();
      additional_workers_.clear();
    }

   private:
    // The purpose of |first_worker| is to avoid a heap allocation by the vector
    // in the case where there is only one worker in the container.
    scoped_refptr<WorkerThread> first_worker_;
    std::vector<scoped_refptr<WorkerThread>> additional_workers_;

    DISALLOW_COPY_AND_ASSIGN(WorkerContainer);
  };

  void FlushImpl() {
    CheckedLock::AssertNoLockHeldOnCurrentThread();

    // Wake up workers.
    workers_to_wake_up_.ForEachWorker(
        [](WorkerThread* worker) { worker->WakeUp(); });

    // Start workers. Happens after wake ups to prevent the case where a worker
    // enters its main function, is descheduled because it wasn't woken up yet,
    // and is woken up immediately after.
    workers_to_start_.ForEachWorker([&](WorkerThread* worker) {
      worker->Start(outer_->after_start().worker_thread_observer);
    });

    if (must_schedule_adjust_max_tasks_)
      outer_->ScheduleAdjustMaxTasks();

    if (!scheduled_histogram_samples_->empty()) {
      DCHECK_LE(scheduled_histogram_samples_->size(),
                kHistogramSampleStackSize);
      for (auto& scheduled_sample : scheduled_histogram_samples_)
        scheduled_sample.first->Add(scheduled_sample.second);
      scheduled_histogram_samples_->clear();
    }
  }

  ThreadGroupImpl* const outer_;

  WorkerContainer workers_to_wake_up_;
  WorkerContainer workers_to_start_;
  bool must_schedule_adjust_max_tasks_ = false;

  // StackVector rather than std::vector avoid heap allocations; size should be
  // high enough to store the maximum number of histogram samples added to a
  // given ScopedCommandsExecutor instance.
  static constexpr size_t kHistogramSampleStackSize = 5;
  StackVector<std::pair<HistogramBase*, HistogramBase::Sample>,
              kHistogramSampleStackSize>
      scheduled_histogram_samples_;

  DISALLOW_COPY_AND_ASSIGN(ScopedCommandsExecutor);
};

// static
constexpr size_t
    ThreadGroupImpl::ScopedCommandsExecutor::kHistogramSampleStackSize;

class ThreadGroupImpl::WorkerThreadDelegateImpl : public WorkerThread::Delegate,
                                                  public BlockingObserver {
 public:
  // |outer| owns the worker for which this delegate is constructed.
  WorkerThreadDelegateImpl(TrackedRef<ThreadGroupImpl> outer);
  ~WorkerThreadDelegateImpl() override;

  // WorkerThread::Delegate:
  WorkerThread::ThreadLabel GetThreadLabel() const override;
  void OnMainEntry(const WorkerThread* worker) override;
  RegisteredTaskSource GetWork(WorkerThread* worker) override;
  void DidProcessTask(RegisteredTaskSource task_source) override;
  TimeDelta GetSleepTimeout() override;
  void OnMainExit(WorkerThread* worker) override;

  // BlockingObserver:
  void BlockingStarted(BlockingType blocking_type) override;
  void BlockingTypeUpgraded() override;
  void BlockingEnded() override;

  void MayBlockEntered();
  void WillBlockEntered();

  // Returns true iff the worker can get work. Cleans up the worker or puts it
  // on the idle stack if it can't get work.
  bool CanGetWorkLockRequired(ScopedCommandsExecutor* executor,
                              WorkerThread* worker)
      EXCLUSIVE_LOCKS_REQUIRED(outer_->lock_);

  // Returns true iff this worker has been within a MAY_BLOCK ScopedBlockingCall
  // for more than |may_block_threshold|. The max tasks must be
  // incremented if this returns true.
  bool MustIncrementMaxTasksLockRequired()
      EXCLUSIVE_LOCKS_REQUIRED(outer_->lock_);

  TaskPriority current_task_priority_lock_required() const
      EXCLUSIVE_LOCKS_REQUIRED(outer_->lock_) {
    return *read_any().current_task_priority;
  }

  // Exposed for AnnotateCheckedLockAcquired in
  // ThreadGroupImpl::AdjustMaxTasks()
  const CheckedLock& lock() const LOCK_RETURNED(outer_->lock_) {
    return outer_->lock_;
  }

 private:
  // Returns true if |worker| is allowed to cleanup and remove itself from the
  // thread group. Called from GetWork() when no work is available.
  bool CanCleanupLockRequired(const WorkerThread* worker) const
      EXCLUSIVE_LOCKS_REQUIRED(outer_->lock_);

  // Calls cleanup on |worker| and removes it from the thread group. Called from
  // GetWork() when no work is available and CanCleanupLockRequired() returns
  // true.
  void CleanupLockRequired(ScopedCommandsExecutor* executor,
                           WorkerThread* worker)
      EXCLUSIVE_LOCKS_REQUIRED(outer_->lock_);

  // Called in GetWork() when a worker becomes idle.
  void OnWorkerBecomesIdleLockRequired(WorkerThread* worker)
      EXCLUSIVE_LOCKS_REQUIRED(outer_->lock_);

  // Accessed only from the worker thread.
  struct WorkerOnly {
    // Number of tasks executed since the last time the
    // ThreadPool.NumTasksBeforeDetach histogram was recorded.
    size_t num_tasks_since_last_detach = 0;

    // Whether the worker is currently running a task (i.e. GetWork() has
    // returned a non-empty task source and DidProcessTask() hasn't been called
    // yet).
    bool is_running_task = false;

#if defined(OS_WIN)
    std::unique_ptr<win::ScopedWindowsThreadEnvironment> win_thread_environment;
#endif  // defined(OS_WIN)
  } worker_only_;

  // Writes from the worker thread protected by |outer_->lock_|. Reads from any
  // thread, protected by |outer_->lock_| when not on the worker thread.
  struct WriteWorkerReadAny {
    // The priority of the task the worker is currently running if any.
    base::Optional<TaskPriority> current_task_priority;

    // Time when MayBlockScopeEntered() was last called. Reset when
    // BlockingScopeExited() is called.
    TimeTicks may_block_start_time;
  } write_worker_read_any_;

  WorkerOnly& worker_only() {
    DCHECK_CALLED_ON_VALID_THREAD(worker_thread_checker_);
    return worker_only_;
  }

  WriteWorkerReadAny& write_worker() EXCLUSIVE_LOCKS_REQUIRED(outer_->lock_) {
    DCHECK_CALLED_ON_VALID_THREAD(worker_thread_checker_);
    return write_worker_read_any_;
  }

  const WriteWorkerReadAny& read_any() const
      EXCLUSIVE_LOCKS_REQUIRED(outer_->lock_) {
    return write_worker_read_any_;
  }

  const WriteWorkerReadAny& read_worker() const {
    DCHECK_CALLED_ON_VALID_THREAD(worker_thread_checker_);
    return write_worker_read_any_;
  }

  const TrackedRef<ThreadGroupImpl> outer_;

  // Whether |outer_->max_tasks_| was incremented due to a ScopedBlockingCall on
  // the thread.
  bool incremented_max_tasks_since_blocked_ GUARDED_BY(outer_->lock_) = false;

  // Verifies that specific calls are always made from the worker thread.
  THREAD_CHECKER(worker_thread_checker_);

  DISALLOW_COPY_AND_ASSIGN(WorkerThreadDelegateImpl);
};

ThreadGroupImpl::ThreadGroupImpl(StringPiece histogram_label,
                                 StringPiece thread_group_label,
                                 ThreadPriority priority_hint,
                                 TrackedRef<TaskTracker> task_tracker,
                                 TrackedRef<Delegate> delegate)
    : ThreadGroup(std::move(task_tracker), std::move(delegate)),
      thread_group_label_(thread_group_label.as_string()),
      priority_hint_(priority_hint),
      idle_workers_stack_cv_for_testing_(lock_.CreateConditionVariable()),
      // Mimics the UMA_HISTOGRAM_LONG_TIMES macro.
      detach_duration_histogram_(
          histogram_label.empty()
              ? nullptr
              : Histogram::FactoryTimeGet(
                    JoinString(
                        {kDetachDurationHistogramPrefix, histogram_label},
                        ""),
                    TimeDelta::FromMilliseconds(1),
                    TimeDelta::FromHours(1),
                    50,
                    HistogramBase::kUmaTargetedHistogramFlag)),
      // Mimics the UMA_HISTOGRAM_COUNTS_1000 macro. When a worker runs more
      // than 1000 tasks before detaching, there is no need to know the exact
      // number of tasks that ran.
      num_tasks_before_detach_histogram_(
          histogram_label.empty()
              ? nullptr
              : Histogram::FactoryGet(
                    JoinString(
                        {kNumTasksBeforeDetachHistogramPrefix, histogram_label},
                        ""),
                    1,
                    1000,
                    50,
                    HistogramBase::kUmaTargetedHistogramFlag)),
      // Mimics the UMA_HISTOGRAM_COUNTS_100 macro. A ThreadGroup is
      // expected to run between zero and a few tens of workers.
      // When it runs more than 100 worker, there is no need to know the exact
      // number of workers that ran.
      num_workers_histogram_(
          histogram_label.empty()
              ? nullptr
              : Histogram::FactoryGet(
                    JoinString({kNumWorkersHistogramPrefix, histogram_label},
                               ""),
                    1,
                    100,
                    50,
                    HistogramBase::kUmaTargetedHistogramFlag)),
      num_active_workers_histogram_(
          histogram_label.empty()
              ? nullptr
              : Histogram::FactoryGet(
                    JoinString(
                        {kNumActiveWorkersHistogramPrefix, histogram_label},
                        ""),
                    1,
                    100,
                    50,
                    HistogramBase::kUmaTargetedHistogramFlag)),
      tracked_ref_factory_(this) {
  DCHECK(!thread_group_label_.empty());
}

void ThreadGroupImpl::Start(
    int max_tasks,
    int max_best_effort_tasks,
    TimeDelta suggested_reclaim_time,
    scoped_refptr<SequencedTaskRunner> service_thread_task_runner,
    WorkerThreadObserver* worker_thread_observer,
    WorkerEnvironment worker_environment,
    Optional<TimeDelta> may_block_threshold) {
  DCHECK(!replacement_thread_group_);

  in_start().may_block_without_delay =
      FeatureList::IsEnabled(kMayBlockWithoutDelay);
  in_start().fixed_max_best_effort_tasks =
      FeatureList::IsEnabled(kFixedMaxBestEffortTasks);
  in_start().may_block_threshold =
      may_block_threshold ? may_block_threshold.value()
                          : (priority_hint_ == ThreadPriority::NORMAL
                                 ? kForegroundMayBlockThreshold
                                 : kBackgroundMayBlockThreshold);
  in_start().blocked_workers_poll_period =
      priority_hint_ == ThreadPriority::NORMAL ? kForegroundBlockedWorkersPoll
                                               : kBackgroundBlockedWorkersPoll;

  ScopedCommandsExecutor executor(this);
  CheckedAutoLock auto_lock(lock_);

  DCHECK(workers_.empty());
  max_tasks_ = max_tasks;
  DCHECK_GE(max_tasks_, 1U);
  in_start().initial_max_tasks = max_tasks_;
  DCHECK_LE(in_start().initial_max_tasks, kMaxNumberOfWorkers);
  max_best_effort_tasks_ = max_best_effort_tasks;
  in_start().suggested_reclaim_time = suggested_reclaim_time;
  in_start().worker_environment = worker_environment;
  in_start().service_thread_task_runner = std::move(service_thread_task_runner);
  in_start().worker_thread_observer = worker_thread_observer;

#if DCHECK_IS_ON()
  in_start().initialized = true;
#endif

  EnsureEnoughWorkersLockRequired(&executor);
}

ThreadGroupImpl::~ThreadGroupImpl() {
  // ThreadGroup should only ever be deleted:
  //  1) In tests, after JoinForTesting().
  //  2) In production, iff initialization failed.
  // In both cases |workers_| should be empty.
  DCHECK(workers_.empty());
}

void ThreadGroupImpl::UpdateSortKey(TaskSource::Transaction transaction) {
  ScopedCommandsExecutor executor(this);
  UpdateSortKeyImpl(&executor, std::move(transaction));
}

void ThreadGroupImpl::PushTaskSourceAndWakeUpWorkers(
    TransactionWithRegisteredTaskSource transaction_with_task_source) {
  ScopedCommandsExecutor executor(this);
  PushTaskSourceAndWakeUpWorkersImpl(&executor,
                                     std::move(transaction_with_task_source));
}

size_t ThreadGroupImpl::GetMaxConcurrentNonBlockedTasksDeprecated() const {
#if DCHECK_IS_ON()
  CheckedAutoLock auto_lock(lock_);
  DCHECK_NE(after_start().initial_max_tasks, 0U)
      << "GetMaxConcurrentTasksDeprecated() should only be called after the "
      << "thread group has started.";
#endif
  return after_start().initial_max_tasks;
}

void ThreadGroupImpl::WaitForWorkersIdleForTesting(size_t n) {
  CheckedAutoLock auto_lock(lock_);

#if DCHECK_IS_ON()
  DCHECK(!some_workers_cleaned_up_for_testing_)
      << "Workers detached prior to waiting for a specific number of idle "
         "workers. Doing the wait under such conditions is flaky. Consider "
         "setting the suggested reclaim time to TimeDelta::Max() in Start().";
#endif

  WaitForWorkersIdleLockRequiredForTesting(n);
}

void ThreadGroupImpl::WaitForAllWorkersIdleForTesting() {
  CheckedAutoLock auto_lock(lock_);
  WaitForWorkersIdleLockRequiredForTesting(workers_.size());
}

void ThreadGroupImpl::WaitForWorkersCleanedUpForTesting(size_t n) {
  CheckedAutoLock auto_lock(lock_);

  if (!num_workers_cleaned_up_for_testing_cv_)
    num_workers_cleaned_up_for_testing_cv_ = lock_.CreateConditionVariable();

  while (num_workers_cleaned_up_for_testing_ < n)
    num_workers_cleaned_up_for_testing_cv_->Wait();

  num_workers_cleaned_up_for_testing_ = 0;
}

void ThreadGroupImpl::JoinForTesting() {
  decltype(workers_) workers_copy;
  {
    CheckedAutoLock auto_lock(lock_);
    priority_queue_.EnableFlushTaskSourcesOnDestroyForTesting();

    DCHECK_GT(workers_.size(), size_t(0))
        << "Joined an unstarted thread group.";

    join_for_testing_started_ = true;

    // Ensure WorkerThreads in |workers_| do not attempt to cleanup while
    // being joined.
    worker_cleanup_disallowed_for_testing_ = true;

    // Make a copy of the WorkerThreads so that we can call
    // WorkerThread::JoinForTesting() without holding |lock_| since
    // WorkerThreads may need to access |workers_|.
    workers_copy = workers_;
  }
  for (const auto& worker : workers_copy)
    worker->JoinForTesting();

  CheckedAutoLock auto_lock(lock_);
  DCHECK(workers_ == workers_copy);
  // Release |workers_| to clear their TrackedRef against |this|.
  workers_.clear();
}

size_t ThreadGroupImpl::NumberOfWorkersForTesting() const {
  CheckedAutoLock auto_lock(lock_);
  return workers_.size();
}

size_t ThreadGroupImpl::GetMaxTasksForTesting() const {
  CheckedAutoLock auto_lock(lock_);
  return max_tasks_;
}

size_t ThreadGroupImpl::NumberOfIdleWorkersForTesting() const {
  CheckedAutoLock auto_lock(lock_);
  return idle_workers_stack_.Size();
}

void ThreadGroupImpl::ReportHeartbeatMetrics() const {
  HistogramBase::Sample num_workers_sample;
  HistogramBase::Sample num_active_workers_sample;

  // Increase histogram counts while |lock_| isn't being held,
  // in case histogram code triggers PostTasks in callbacks.
  {
    CheckedAutoLock auto_lock(lock_);
    num_workers_sample = workers_.size();
    num_active_workers_sample = workers_.size() - idle_workers_stack_.Size();
  }

  if (num_workers_histogram_)
    num_workers_histogram_->Add(num_workers_sample);

  if (num_active_workers_histogram_)
    num_active_workers_histogram_->Add(num_active_workers_sample);
}

ThreadGroupImpl::WorkerThreadDelegateImpl::WorkerThreadDelegateImpl(
    TrackedRef<ThreadGroupImpl> outer)
    : outer_(std::move(outer)) {
  // Bound in OnMainEntry().
  DETACH_FROM_THREAD(worker_thread_checker_);
}

// OnMainExit() handles the thread-affine cleanup; WorkerThreadDelegateImpl
// can thereafter safely be deleted from any thread.
ThreadGroupImpl::WorkerThreadDelegateImpl::~WorkerThreadDelegateImpl() =
    default;

WorkerThread::ThreadLabel
ThreadGroupImpl::WorkerThreadDelegateImpl::GetThreadLabel() const {
  return WorkerThread::ThreadLabel::POOLED;
}

void ThreadGroupImpl::WorkerThreadDelegateImpl::OnMainEntry(
    const WorkerThread* worker) {
  DCHECK_CALLED_ON_VALID_THREAD(worker_thread_checker_);

  {
#if DCHECK_IS_ON()
    CheckedAutoLock auto_lock(outer_->lock_);
    DCHECK(ContainsWorker(outer_->workers_, worker));
#endif
  }

#if defined(OS_WIN)
  worker_only().win_thread_environment = GetScopedWindowsThreadEnvironment(
      outer_->after_start().worker_environment);
#endif  // defined(OS_WIN)

  PlatformThread::SetName(
      StringPrintf("ThreadPool%sWorker", outer_->thread_group_label_.c_str()));

  outer_->BindToCurrentThread();
  SetBlockingObserverForCurrentThread(this);
}

RegisteredTaskSource ThreadGroupImpl::WorkerThreadDelegateImpl::GetWork(
    WorkerThread* worker) {
  DCHECK_CALLED_ON_VALID_THREAD(worker_thread_checker_);
  DCHECK(!worker_only().is_running_task);

  ScopedCommandsExecutor executor(outer_.get());
  CheckedAutoLock auto_lock(outer_->lock_);

  DCHECK(ContainsWorker(outer_->workers_, worker));

  // Use this opportunity, before assigning work to this worker, to create/wake
  // additional workers if needed (doing this here allows us to reduce
  // potentially expensive create/wake directly on PostTask()).
  outer_->EnsureEnoughWorkersLockRequired(&executor);
  executor.FlushWorkerCreation(&outer_->lock_);

  if (!CanGetWorkLockRequired(&executor, worker))
    return nullptr;

  RegisteredTaskSource task_source;
  TaskPriority priority;
  while (!task_source && !outer_->priority_queue_.IsEmpty()) {
    // Enforce the CanRunPolicy and that no more than |max_best_effort_tasks_|
    // BEST_EFFORT tasks run concurrently.
    priority = outer_->priority_queue_.PeekSortKey().priority();
    if (!outer_->task_tracker_->CanRunPriority(priority) ||
        (priority == TaskPriority::BEST_EFFORT &&
         outer_->num_running_best_effort_tasks_ >=
             outer_->max_best_effort_tasks_)) {
      break;
    }

    task_source = outer_->TakeRegisteredTaskSource(&executor);
  }
  if (!task_source) {
    OnWorkerBecomesIdleLockRequired(worker);
    return nullptr;
  }

  // Running task bookkeeping.
  worker_only().is_running_task = true;
  outer_->IncrementTasksRunningLockRequired(priority);
  DCHECK(!outer_->idle_workers_stack_.Contains(worker));
  write_worker().current_task_priority = priority;

  return task_source;
}

void ThreadGroupImpl::WorkerThreadDelegateImpl::DidProcessTask(
    RegisteredTaskSource task_source) {
  DCHECK_CALLED_ON_VALID_THREAD(worker_thread_checker_);
  DCHECK(worker_only().is_running_task);
  DCHECK(read_worker().may_block_start_time.is_null());

  ++worker_only().num_tasks_since_last_detach;

  // A transaction to the TaskSource to reenqueue, if any. Instantiated here as
  // |TaskSource::lock_| is a UniversalPredecessor and must always be acquired
  // prior to acquiring a second lock
  Optional<TransactionWithRegisteredTaskSource> transaction_with_task_source;
  if (task_source) {
    transaction_with_task_source.emplace(
        TransactionWithRegisteredTaskSource::FromTaskSource(
            std::move(task_source)));
  }

  ScopedCommandsExecutor workers_executor(outer_.get());
  ScopedReenqueueExecutor reenqueue_executor;
  CheckedAutoLock auto_lock(outer_->lock_);

  DCHECK(!incremented_max_tasks_since_blocked_);

  // Running task bookkeeping.
  outer_->DecrementTasksRunningLockRequired(
      *read_worker().current_task_priority);
  worker_only().is_running_task = false;

  if (transaction_with_task_source) {
    outer_->ReEnqueueTaskSourceLockRequired(
        &workers_executor, &reenqueue_executor,
        std::move(transaction_with_task_source.value()));
  }
}

TimeDelta ThreadGroupImpl::WorkerThreadDelegateImpl::GetSleepTimeout() {
  DCHECK_CALLED_ON_VALID_THREAD(worker_thread_checker_);
  // Sleep for an extra 10% to avoid the following pathological case:

  //   0) A task is running on a timer which matches
  //      |after_start().suggested_reclaim_time|.
  //   1) The timer fires and this worker is created by
  //      MaintainAtLeastOneIdleWorkerLockRequired() because the last idle
  //      worker was assigned the task.
  //   2) This worker begins sleeping |after_start().suggested_reclaim_time| (on
  //      top of the idle stack).
  //   3) The task assigned to the other worker completes and the worker goes
  //      back on the idle stack (this worker is now second on the idle stack;
  //      its GetLastUsedTime() is set to Now()).
  //   4) The sleep in (2) expires. Since (3) was fast this worker is likely to
  //      have been second on the idle stack long enough for
  //      CanCleanupLockRequired() to be satisfied in which case this worker is
  //      cleaned up.
  //   5) The timer fires at roughly the same time and we're back to (1) if (4)
  //      resulted in a clean up; causing thread churn.
  //
  //   Sleeping 10% longer in (2) makes it much less likely that (4) occurs
  //   before (5). In that case (5) will cause (3) and refresh this worker's
  //   GetLastUsedTime(), making CanCleanupLockRequired() return false in (4)
  //   and avoiding churn.
  //
  //   Of course the same problem arises if in (0) the timer matches
  //   |after_start().suggested_reclaim_time * 1.1| but it's expected that any
  //   timer slower than |after_start().suggested_reclaim_time| will cause such
  //   churn during long idle periods. If this is a problem in practice, the
  //   standby thread configuration and algorithm should be revisited.
  return outer_->after_start().suggested_reclaim_time * 1.1;
}

bool ThreadGroupImpl::WorkerThreadDelegateImpl::CanCleanupLockRequired(
    const WorkerThread* worker) const {
  DCHECK_CALLED_ON_VALID_THREAD(worker_thread_checker_);

  const TimeTicks last_used_time = worker->GetLastUsedTime();
  return !last_used_time.is_null() &&
         subtle::TimeTicksNowIgnoringOverride() - last_used_time >=
             outer_->after_start().suggested_reclaim_time &&
         (outer_->workers_.size() > outer_->after_start().initial_max_tasks ||
          !FeatureList::IsEnabled(kNoDetachBelowInitialCapacity)) &&
         LIKELY(!outer_->worker_cleanup_disallowed_for_testing_);
}

void ThreadGroupImpl::WorkerThreadDelegateImpl::CleanupLockRequired(
    ScopedCommandsExecutor* executor,
    WorkerThread* worker) {
  DCHECK(!outer_->join_for_testing_started_);
  DCHECK_CALLED_ON_VALID_THREAD(worker_thread_checker_);

  if (outer_->num_tasks_before_detach_histogram_) {
    executor->ScheduleAddHistogramSample(
        outer_->num_tasks_before_detach_histogram_,
        worker_only().num_tasks_since_last_detach);
  }
  outer_->cleanup_timestamps_.push(subtle::TimeTicksNowIgnoringOverride());
  worker->Cleanup();
  outer_->idle_workers_stack_.Remove(worker);

  // Remove the worker from |workers_|.
  auto worker_iter =
      std::find(outer_->workers_.begin(), outer_->workers_.end(), worker);
  DCHECK(worker_iter != outer_->workers_.end());
  outer_->workers_.erase(worker_iter);
}

void ThreadGroupImpl::WorkerThreadDelegateImpl::OnWorkerBecomesIdleLockRequired(
    WorkerThread* worker) {
  DCHECK_CALLED_ON_VALID_THREAD(worker_thread_checker_);

  // Add the worker to the idle stack.
  DCHECK(!outer_->idle_workers_stack_.Contains(worker));
  outer_->idle_workers_stack_.Push(worker);
  DCHECK_LE(outer_->idle_workers_stack_.Size(), outer_->workers_.size());
  outer_->idle_workers_stack_cv_for_testing_->Broadcast();
}

void ThreadGroupImpl::WorkerThreadDelegateImpl::OnMainExit(
    WorkerThread* worker) {
  DCHECK_CALLED_ON_VALID_THREAD(worker_thread_checker_);

#if DCHECK_IS_ON()
  {
    bool shutdown_complete = outer_->task_tracker_->IsShutdownComplete();
    CheckedAutoLock auto_lock(outer_->lock_);

    // |worker| should already have been removed from the idle workers stack and
    // |workers_| by the time the thread is about to exit. (except in the cases
    // where the thread group is no longer going to be used - in which case,
    // it's fine for there to be invalid workers in the thread group.
    if (!shutdown_complete && !outer_->join_for_testing_started_) {
      DCHECK(!outer_->idle_workers_stack_.Contains(worker));
      DCHECK(!ContainsWorker(outer_->workers_, worker));
    }
  }
#endif

#if defined(OS_WIN)
  worker_only().win_thread_environment.reset();
#endif  // defined(OS_WIN)

  // Count cleaned up workers for tests. It's important to do this here instead
  // of at the end of CleanupLockRequired() because some side-effects of
  // cleaning up happen outside the lock (e.g. recording histograms) and
  // resuming from tests must happen-after that point or checks on the main
  // thread will be flaky (crbug.com/1047733).
  CheckedAutoLock auto_lock(outer_->lock_);
  ++outer_->num_workers_cleaned_up_for_testing_;
#if DCHECK_IS_ON()
  outer_->some_workers_cleaned_up_for_testing_ = true;
#endif
  if (outer_->num_workers_cleaned_up_for_testing_cv_)
    outer_->num_workers_cleaned_up_for_testing_cv_->Signal();
}

void ThreadGroupImpl::WorkerThreadDelegateImpl::BlockingStarted(
    BlockingType blocking_type) {
  DCHECK_CALLED_ON_VALID_THREAD(worker_thread_checker_);
  DCHECK(worker_only().is_running_task);

  // MayBlock with no delay reuses WillBlock implementation.
  // WillBlock is always used when time overrides is active. crbug.com/1038867
  if (outer_->after_start().may_block_without_delay ||
      base::subtle::ScopedTimeClockOverrides::overrides_active()) {
    blocking_type = BlockingType::WILL_BLOCK;
  }

  switch (blocking_type) {
    case BlockingType::MAY_BLOCK:
      MayBlockEntered();
      break;
    case BlockingType::WILL_BLOCK:
      WillBlockEntered();
      break;
  }
}

void ThreadGroupImpl::WorkerThreadDelegateImpl::BlockingTypeUpgraded() {
  DCHECK_CALLED_ON_VALID_THREAD(worker_thread_checker_);
  DCHECK(worker_only().is_running_task);

  // The blocking type always being WILL_BLOCK in this experiment and with time
  // overrides, it should never be considered "upgraded".
  if (outer_->after_start().may_block_without_delay ||
      base::subtle::ScopedTimeClockOverrides::overrides_active()) {
    return;
  }

  {
    CheckedAutoLock auto_lock(outer_->lock_);

    // Don't do anything if a MAY_BLOCK ScopedBlockingCall instantiated in the
    // same scope already caused the max tasks to be incremented.
    if (incremented_max_tasks_since_blocked_)
      return;

    // Cancel the effect of a MAY_BLOCK ScopedBlockingCall instantiated in the
    // same scope.
    if (!read_worker().may_block_start_time.is_null()) {
      write_worker().may_block_start_time = TimeTicks();
      --outer_->num_unresolved_may_block_;
      if (*read_worker().current_task_priority == TaskPriority::BEST_EFFORT)
        --outer_->num_unresolved_best_effort_may_block_;
    }
  }

  WillBlockEntered();
}

void ThreadGroupImpl::WorkerThreadDelegateImpl::BlockingEnded() {
  DCHECK_CALLED_ON_VALID_THREAD(worker_thread_checker_);
  DCHECK(worker_only().is_running_task);

  CheckedAutoLock auto_lock(outer_->lock_);
  if (incremented_max_tasks_since_blocked_) {
    outer_->DecrementMaxTasksLockRequired(*read_worker().current_task_priority);
  } else {
    DCHECK(!read_worker().may_block_start_time.is_null());
    --outer_->num_unresolved_may_block_;
    if (*read_worker().current_task_priority == TaskPriority::BEST_EFFORT)
      --outer_->num_unresolved_best_effort_may_block_;
  }

  incremented_max_tasks_since_blocked_ = false;
  write_worker().may_block_start_time = TimeTicks();
}

void ThreadGroupImpl::WorkerThreadDelegateImpl::MayBlockEntered() {
  DCHECK_CALLED_ON_VALID_THREAD(worker_thread_checker_);
  DCHECK(worker_only().is_running_task);

  ScopedCommandsExecutor executor(outer_.get());
  CheckedAutoLock auto_lock(outer_->lock_);

  DCHECK(!incremented_max_tasks_since_blocked_);
  DCHECK(read_worker().may_block_start_time.is_null());
  write_worker().may_block_start_time = subtle::TimeTicksNowIgnoringOverride();
  ++outer_->num_unresolved_may_block_;
  if (*read_worker().current_task_priority == TaskPriority::BEST_EFFORT)
    ++outer_->num_unresolved_best_effort_may_block_;

  outer_->MaybeScheduleAdjustMaxTasksLockRequired(&executor);
}

void ThreadGroupImpl::WorkerThreadDelegateImpl::WillBlockEntered() {
  DCHECK_CALLED_ON_VALID_THREAD(worker_thread_checker_);
  DCHECK(worker_only().is_running_task);

  ScopedCommandsExecutor executor(outer_.get());
  CheckedAutoLock auto_lock(outer_->lock_);

  DCHECK(!incremented_max_tasks_since_blocked_);
  DCHECK(read_worker().may_block_start_time.is_null());
  incremented_max_tasks_since_blocked_ = true;
  outer_->IncrementMaxTasksLockRequired(*read_worker().current_task_priority);
  outer_->EnsureEnoughWorkersLockRequired(&executor);
}

bool ThreadGroupImpl::WorkerThreadDelegateImpl::CanGetWorkLockRequired(
    ScopedCommandsExecutor* executor,
    WorkerThread* worker) {
  // To avoid searching through the idle stack : use GetLastUsedTime() not being
  // null (or being directly on top of the idle stack) as a proxy for being on
  // the idle stack.
  const bool is_on_idle_workers_stack =
      outer_->idle_workers_stack_.Peek() == worker ||
      !worker->GetLastUsedTime().is_null();
  DCHECK_EQ(is_on_idle_workers_stack,
            outer_->idle_workers_stack_.Contains(worker));

  if (is_on_idle_workers_stack) {
    if (CanCleanupLockRequired(worker))
      CleanupLockRequired(executor, worker);
    return false;
  }

  // Excess workers should not get work, until they are no longer excess (i.e.
  // max tasks increases). This ensures that if we have excess workers in the
  // thread group, they get a chance to no longer be excess before being cleaned
  // up.
  if (outer_->GetNumAwakeWorkersLockRequired() >
      outer_->GetDesiredNumAwakeWorkersLockRequired()) {
    OnWorkerBecomesIdleLockRequired(worker);
    return false;
  }

  return true;
}

bool ThreadGroupImpl::WorkerThreadDelegateImpl::
    MustIncrementMaxTasksLockRequired() {
  if (!incremented_max_tasks_since_blocked_ &&
      !read_any().may_block_start_time.is_null() &&
      subtle::TimeTicksNowIgnoringOverride() -
              read_any().may_block_start_time >=
          outer_->after_start().may_block_threshold) {
    incremented_max_tasks_since_blocked_ = true;

    --outer_->num_unresolved_may_block_;
    if (*read_any().current_task_priority == TaskPriority::BEST_EFFORT)
      --outer_->num_unresolved_best_effort_may_block_;

    return true;
  }

  return false;
}

void ThreadGroupImpl::WaitForWorkersIdleLockRequiredForTesting(size_t n) {
  // Make sure workers do not cleanup while watching the idle count.
  AutoReset<bool> ban_cleanups(&worker_cleanup_disallowed_for_testing_, true);

  while (idle_workers_stack_.Size() < n)
    idle_workers_stack_cv_for_testing_->Wait();
}

void ThreadGroupImpl::MaintainAtLeastOneIdleWorkerLockRequired(
    ScopedCommandsExecutor* executor) {
  if (workers_.size() == kMaxNumberOfWorkers)
    return;
  DCHECK_LT(workers_.size(), kMaxNumberOfWorkers);

  if (!idle_workers_stack_.IsEmpty())
    return;

  if (workers_.size() >= max_tasks_)
    return;

  scoped_refptr<WorkerThread> new_worker =
      CreateAndRegisterWorkerLockRequired(executor);
  DCHECK(new_worker);
  idle_workers_stack_.Push(new_worker.get());
}

scoped_refptr<WorkerThread>
ThreadGroupImpl::CreateAndRegisterWorkerLockRequired(
    ScopedCommandsExecutor* executor) {
  DCHECK(!join_for_testing_started_);
  DCHECK_LT(workers_.size(), max_tasks_);
  DCHECK_LT(workers_.size(), kMaxNumberOfWorkers);
  DCHECK(idle_workers_stack_.IsEmpty());

  // WorkerThread needs |lock_| as a predecessor for its thread lock
  // because in WakeUpOneWorker, |lock_| is first acquired and then
  // the thread lock is acquired when WakeUp is called on the worker.
  scoped_refptr<WorkerThread> worker =
      MakeRefCounted<WorkerThread>(priority_hint_,
                                   std::make_unique<WorkerThreadDelegateImpl>(
                                       tracked_ref_factory_.GetTrackedRef()),
                                   task_tracker_, &lock_);

  workers_.push_back(worker);
  executor->ScheduleStart(worker);
  DCHECK_LE(workers_.size(), max_tasks_);

  if (!cleanup_timestamps_.empty()) {
    if (detach_duration_histogram_) {
      executor->ScheduleAddHistogramSample(
          detach_duration_histogram_,
          (subtle::TimeTicksNowIgnoringOverride() - cleanup_timestamps_.top())
              .InMilliseconds());
    }
    cleanup_timestamps_.pop();
  }

  return worker;
}

size_t ThreadGroupImpl::GetNumAwakeWorkersLockRequired() const {
  DCHECK_GE(workers_.size(), idle_workers_stack_.Size());
  size_t num_awake_workers = workers_.size() - idle_workers_stack_.Size();
  DCHECK_GE(num_awake_workers, num_running_tasks_);
  return num_awake_workers;
}

size_t ThreadGroupImpl::GetDesiredNumAwakeWorkersLockRequired() const {
  // Number of BEST_EFFORT task sources that are running or queued and allowed
  // to run by the CanRunPolicy.
  const size_t num_running_or_queued_can_run_best_effort_task_sources =
      num_running_best_effort_tasks_ +
      GetNumAdditionalWorkersForBestEffortTaskSourcesLockRequired();

  const size_t workers_for_best_effort_task_sources =
      std::max(std::min(num_running_or_queued_can_run_best_effort_task_sources,
                        max_best_effort_tasks_),
               num_running_best_effort_tasks_);

  // Number of USER_{VISIBLE|BLOCKING} task sources that are running or queued.
  const size_t num_running_or_queued_foreground_task_sources =
      (num_running_tasks_ - num_running_best_effort_tasks_) +
      GetNumAdditionalWorkersForForegroundTaskSourcesLockRequired();

  const size_t workers_for_foreground_task_sources =
      num_running_or_queued_foreground_task_sources;

  return std::min({workers_for_best_effort_task_sources +
                       workers_for_foreground_task_sources,
                   max_tasks_, kMaxNumberOfWorkers});
}

void ThreadGroupImpl::DidUpdateCanRunPolicy() {
  ScopedCommandsExecutor executor(this);
  CheckedAutoLock auto_lock(lock_);
  EnsureEnoughWorkersLockRequired(&executor);
}

void ThreadGroupImpl::EnsureEnoughWorkersLockRequired(
    BaseScopedCommandsExecutor* base_executor) {
  // Don't do anything if the thread group isn't started.
  if (max_tasks_ == 0 || UNLIKELY(join_for_testing_started_))
    return;

  ScopedCommandsExecutor* executor =
      static_cast<ScopedCommandsExecutor*>(base_executor);

  const size_t desired_num_awake_workers =
      GetDesiredNumAwakeWorkersLockRequired();
  const size_t num_awake_workers = GetNumAwakeWorkersLockRequired();

  size_t num_workers_to_wake_up =
      ClampSub(desired_num_awake_workers, num_awake_workers);
  num_workers_to_wake_up = std::min(num_workers_to_wake_up, size_t(2U));

  // Wake up the appropriate number of workers.
  for (size_t i = 0; i < num_workers_to_wake_up; ++i) {
    MaintainAtLeastOneIdleWorkerLockRequired(executor);
    WorkerThread* worker_to_wakeup = idle_workers_stack_.Pop();
    DCHECK(worker_to_wakeup);
    executor->ScheduleWakeUp(worker_to_wakeup);
  }

  // In the case where the loop above didn't wake up any worker and we don't
  // have excess workers, the idle worker should be maintained. This happens
  // when called from the last worker awake, or a recent increase in |max_tasks|
  // now makes it possible to keep an idle worker.
  if (desired_num_awake_workers == num_awake_workers)
    MaintainAtLeastOneIdleWorkerLockRequired(executor);

  // This function is called every time a task source is (re-)enqueued,
  // hence the minimum priority needs to be updated.
  UpdateMinAllowedPriorityLockRequired();

  // Ensure that the number of workers is periodically adjusted if needed.
  MaybeScheduleAdjustMaxTasksLockRequired(executor);
}

void ThreadGroupImpl::AdjustMaxTasks() {
  DCHECK(
      after_start().service_thread_task_runner->RunsTasksInCurrentSequence());

  ScopedCommandsExecutor executor(this);
  CheckedAutoLock auto_lock(lock_);
  DCHECK(adjust_max_tasks_posted_);
  adjust_max_tasks_posted_ = false;

  // Increment max tasks for each worker that has been within a MAY_BLOCK
  // ScopedBlockingCall for more than may_block_threshold.
  for (scoped_refptr<WorkerThread> worker : workers_) {
    // The delegates of workers inside a ThreadGroupImpl should be
    // WorkerThreadDelegateImpls.
    WorkerThreadDelegateImpl* delegate =
        static_cast<WorkerThreadDelegateImpl*>(worker->delegate());
    AnnotateAcquiredLockAlias annotate(lock_, delegate->lock());
    if (delegate->MustIncrementMaxTasksLockRequired()) {
      IncrementMaxTasksLockRequired(
          delegate->current_task_priority_lock_required());
    }
  }

  // Wake up workers according to the updated |max_tasks_|. This will also
  // reschedule AdjustMaxTasks() if necessary.
  EnsureEnoughWorkersLockRequired(&executor);
}

void ThreadGroupImpl::ScheduleAdjustMaxTasks() {
  // |adjust_max_tasks_posted_| can't change before the task posted below runs.
  // Skip check on NaCl to avoid unsafe reference acquisition warning.
#if !defined(OS_NACL)
  DCHECK(TS_UNCHECKED_READ(adjust_max_tasks_posted_));
#endif

  after_start().service_thread_task_runner->PostDelayedTask(
      FROM_HERE, BindOnce(&ThreadGroupImpl::AdjustMaxTasks, Unretained(this)),
      after_start().blocked_workers_poll_period);
}

void ThreadGroupImpl::MaybeScheduleAdjustMaxTasksLockRequired(
    ScopedCommandsExecutor* executor) {
  if (!adjust_max_tasks_posted_ &&
      ShouldPeriodicallyAdjustMaxTasksLockRequired()) {
    executor->ScheduleAdjustMaxTasks();
    adjust_max_tasks_posted_ = true;
  }
}

bool ThreadGroupImpl::ShouldPeriodicallyAdjustMaxTasksLockRequired() {
  // AdjustMaxTasks() should be scheduled to periodically adjust |max_tasks_|
  // and |max_best_effort_tasks_| when (1) the concurrency limits are not large
  // enough to accommodate all queued and running task sources and an idle
  // worker and (2) there are unresolved MAY_BLOCK ScopedBlockingCalls.
  // - When (1) is false: No worker would be created or woken up if the
  //   concurrency limits were increased, so there is no hurry to increase them.
  // - When (2) is false: The concurrency limits could not be increased by
  //   AdjustMaxTasks().

  const size_t num_running_or_queued_best_effort_task_sources =
      num_running_best_effort_tasks_ +
      GetNumAdditionalWorkersForBestEffortTaskSourcesLockRequired();
  if (num_running_or_queued_best_effort_task_sources > max_best_effort_tasks_ &&
      num_unresolved_best_effort_may_block_ > 0) {
    return true;
  }

  const size_t num_running_or_queued_task_sources =
      num_running_tasks_ +
      GetNumAdditionalWorkersForBestEffortTaskSourcesLockRequired() +
      GetNumAdditionalWorkersForForegroundTaskSourcesLockRequired();
  constexpr size_t kIdleWorker = 1;
  return num_running_or_queued_task_sources + kIdleWorker > max_tasks_ &&
         num_unresolved_may_block_ > 0;
}

void ThreadGroupImpl::UpdateMinAllowedPriorityLockRequired() {
  if (priority_queue_.IsEmpty() || num_running_tasks_ < max_tasks_) {
    min_allowed_priority_.store(TaskPriority::BEST_EFFORT,
                                std::memory_order_relaxed);
  } else {
    min_allowed_priority_.store(priority_queue_.PeekSortKey().priority(),
                                std::memory_order_relaxed);
  }
}

void ThreadGroupImpl::DecrementTasksRunningLockRequired(TaskPriority priority) {
  DCHECK_GT(num_running_tasks_, 0U);
  --num_running_tasks_;
  if (priority == TaskPriority::BEST_EFFORT) {
    DCHECK_GT(num_running_best_effort_tasks_, 0U);
    --num_running_best_effort_tasks_;
  }
  UpdateMinAllowedPriorityLockRequired();
}

void ThreadGroupImpl::IncrementTasksRunningLockRequired(TaskPriority priority) {
  ++num_running_tasks_;
  DCHECK_LE(num_running_tasks_, max_tasks_);
  DCHECK_LE(num_running_tasks_, kMaxNumberOfWorkers);
  if (priority == TaskPriority::BEST_EFFORT) {
    ++num_running_best_effort_tasks_;
    DCHECK_LE(num_running_best_effort_tasks_, num_running_tasks_);
    DCHECK_LE(num_running_best_effort_tasks_, max_best_effort_tasks_);
  }
  UpdateMinAllowedPriorityLockRequired();
}

void ThreadGroupImpl::DecrementMaxTasksLockRequired(TaskPriority priority) {
  DCHECK_GT(num_running_tasks_, 0U);
  DCHECK_GT(max_tasks_, 0U);
  --max_tasks_;
  if (priority == TaskPriority::BEST_EFFORT &&
      !after_start().fixed_max_best_effort_tasks) {
    --max_best_effort_tasks_;
  }
  UpdateMinAllowedPriorityLockRequired();
}

void ThreadGroupImpl::IncrementMaxTasksLockRequired(TaskPriority priority) {
  DCHECK_GT(num_running_tasks_, 0U);
  ++max_tasks_;
  if (priority == TaskPriority::BEST_EFFORT &&
      !after_start().fixed_max_best_effort_tasks) {
    ++max_best_effort_tasks_;
  }
  UpdateMinAllowedPriorityLockRequired();
}

ThreadGroupImpl::InitializedInStart::InitializedInStart() = default;
ThreadGroupImpl::InitializedInStart::~InitializedInStart() = default;

}  // namespace internal
}  // namespace base
