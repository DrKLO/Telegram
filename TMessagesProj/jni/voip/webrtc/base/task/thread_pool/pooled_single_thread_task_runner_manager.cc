// Copyright 2017 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

#include "base/task/thread_pool/pooled_single_thread_task_runner_manager.h"

#include <algorithm>
#include <memory>
#include <string>
#include <utility>

#include "base/bind.h"
#include "base/callback.h"
#include "base/memory/ptr_util.h"
#include "base/single_thread_task_runner.h"
#include "base/stl_util.h"
#include "base/strings/stringprintf.h"
#include "base/synchronization/atomic_flag.h"
#include "base/task/task_traits.h"
#include "base/task/thread_pool/delayed_task_manager.h"
#include "base/task/thread_pool/priority_queue.h"
#include "base/task/thread_pool/sequence.h"
#include "base/task/thread_pool/task.h"
#include "base/task/thread_pool/task_source.h"
#include "base/task/thread_pool/task_tracker.h"
#include "base/task/thread_pool/worker_thread.h"
#include "base/threading/platform_thread.h"
#include "base/time/time.h"

#if defined(OS_WIN)
#include <windows.h>

#include "base/win/scoped_com_initializer.h"
#endif  // defined(OS_WIN)

namespace base {
namespace internal {

namespace {

// Boolean indicating whether there's a PooledSingleThreadTaskRunnerManager
// instance alive in this process. This variable should only be set when the
// PooledSingleThreadTaskRunnerManager instance is brought up (on the main
// thread; before any tasks are posted) and decremented when the instance is
// brought down (i.e., only when unit tests tear down the task environment and
// never in production). This makes the variable const while worker threads are
// up and as such it doesn't need to be atomic. It is used to tell when a task
// is posted from the main thread after the task environment was brought down in
// unit tests so that PooledSingleThreadTaskRunnerManager bound TaskRunners
// can return false on PostTask, letting such callers know they should complete
// necessary work synchronously. Note: |!g_manager_is_alive| is generally
// equivalent to |!ThreadPoolInstance::Get()| but has the advantage of being
// valid in thread_pool unit tests that don't instantiate a full
// thread pool.
bool g_manager_is_alive = false;

size_t GetEnvironmentIndexForTraits(const TaskTraits& traits) {
  const bool is_background =
      traits.priority() == TaskPriority::BEST_EFFORT &&
      traits.thread_policy() == ThreadPolicy::PREFER_BACKGROUND &&
      CanUseBackgroundPriorityForWorkerThread();
  if (traits.may_block() || traits.with_base_sync_primitives())
    return is_background ? BACKGROUND_BLOCKING : FOREGROUND_BLOCKING;
  return is_background ? BACKGROUND : FOREGROUND;
}

// Allows for checking the PlatformThread::CurrentRef() against a set
// PlatformThreadRef atomically without using locks.
class AtomicThreadRefChecker {
 public:
  AtomicThreadRefChecker() = default;
  ~AtomicThreadRefChecker() = default;

  void Set() {
    thread_ref_ = PlatformThread::CurrentRef();
    is_set_.Set();
  }

  bool IsCurrentThreadSameAsSetThread() {
    return is_set_.IsSet() && thread_ref_ == PlatformThread::CurrentRef();
  }

 private:
  AtomicFlag is_set_;
  PlatformThreadRef thread_ref_;

  DISALLOW_COPY_AND_ASSIGN(AtomicThreadRefChecker);
};

class WorkerThreadDelegate : public WorkerThread::Delegate {
 public:
  WorkerThreadDelegate(const std::string& thread_name,
                       WorkerThread::ThreadLabel thread_label,
                       TrackedRef<TaskTracker> task_tracker)
      : task_tracker_(std::move(task_tracker)),
        thread_name_(thread_name),
        thread_label_(thread_label) {}

  void set_worker(WorkerThread* worker) {
    DCHECK(!worker_);
    worker_ = worker;
  }

  WorkerThread::ThreadLabel GetThreadLabel() const final {
    return thread_label_;
  }

  void OnMainEntry(const WorkerThread* /* worker */) override {
    thread_ref_checker_.Set();
    PlatformThread::SetName(thread_name_);
  }

  RegisteredTaskSource GetWork(WorkerThread* worker) override {
    CheckedAutoLock auto_lock(lock_);
    DCHECK(worker_awake_);
    auto task_source = GetWorkLockRequired(worker);
    if (!task_source) {
      // The worker will sleep after this returns nullptr.
      worker_awake_ = false;
      return nullptr;
    }
    auto run_status = task_source.WillRunTask();
    DCHECK_NE(run_status, TaskSource::RunStatus::kDisallowed);
    return task_source;
  }

  void DidProcessTask(RegisteredTaskSource task_source) override {
    if (task_source) {
      EnqueueTaskSource(TransactionWithRegisteredTaskSource::FromTaskSource(
          std::move(task_source)));
    }
  }

  TimeDelta GetSleepTimeout() override { return TimeDelta::Max(); }

  bool PostTaskNow(scoped_refptr<Sequence> sequence, Task task) {
    auto transaction = sequence->BeginTransaction();

    // |task| will be pushed to |sequence|, and |sequence| will be queued
    // to |priority_queue_| iff |sequence_should_be_queued| is true.
    const bool sequence_should_be_queued = transaction.WillPushTask();
    RegisteredTaskSource task_source;
    if (sequence_should_be_queued) {
      task_source = task_tracker_->RegisterTaskSource(sequence);
      // We shouldn't push |task| if we're not allowed to queue |task_source|.
      if (!task_source)
        return false;
    }
    if (!task_tracker_->WillPostTaskNow(task, transaction.traits().priority()))
      return false;
    transaction.PushTask(std::move(task));
    if (task_source) {
      bool should_wakeup =
          EnqueueTaskSource({std::move(task_source), std::move(transaction)});
      if (should_wakeup)
        worker_->WakeUp();
    }
    return true;
  }

  bool RunsTasksInCurrentSequence() {
    // We check the thread ref instead of the sequence for the benefit of COM
    // callbacks which may execute without a sequence context.
    return thread_ref_checker_.IsCurrentThreadSameAsSetThread();
  }

  void OnMainExit(WorkerThread* /* worker */) override {}

  void DidUpdateCanRunPolicy() {
    bool should_wakeup = false;
    {
      CheckedAutoLock auto_lock(lock_);
      if (!worker_awake_ && CanRunNextTaskSource()) {
        should_wakeup = true;
        worker_awake_ = true;
      }
    }
    if (should_wakeup)
      worker_->WakeUp();
  }

  void EnableFlushPriorityQueueTaskSourcesOnDestroyForTesting() {
    CheckedAutoLock auto_lock(lock_);
    priority_queue_.EnableFlushTaskSourcesOnDestroyForTesting();
  }

 protected:
  RegisteredTaskSource GetWorkLockRequired(WorkerThread* worker)
      EXCLUSIVE_LOCKS_REQUIRED(lock_) {
    if (!CanRunNextTaskSource()) {
      return nullptr;
    }
    return priority_queue_.PopTaskSource();
  }

  const TrackedRef<TaskTracker>& task_tracker() { return task_tracker_; }

  CheckedLock lock_;
  bool worker_awake_ GUARDED_BY(lock_) = false;

  const TrackedRef<TaskTracker> task_tracker_;

 private:
  // Enqueues a task source in this single-threaded worker's priority queue.
  // Returns true iff the worker must wakeup, i.e. task source is allowed to run
  // and the worker was not awake.
  bool EnqueueTaskSource(
      TransactionWithRegisteredTaskSource transaction_with_task_source) {
    CheckedAutoLock auto_lock(lock_);
    priority_queue_.Push(std::move(transaction_with_task_source));
    if (!worker_awake_ && CanRunNextTaskSource()) {
      worker_awake_ = true;
      return true;
    }
    return false;
  }

  bool CanRunNextTaskSource() EXCLUSIVE_LOCKS_REQUIRED(lock_) {
    return !priority_queue_.IsEmpty() &&
           task_tracker_->CanRunPriority(
               priority_queue_.PeekSortKey().priority());
  }

  const std::string thread_name_;
  const WorkerThread::ThreadLabel thread_label_;

  // The WorkerThread that has |this| as a delegate. Must be set before
  // starting or posting a task to the WorkerThread, because it's used in
  // OnMainEntry() and PostTaskNow().
  WorkerThread* worker_ = nullptr;

  PriorityQueue priority_queue_ GUARDED_BY(lock_);

  AtomicThreadRefChecker thread_ref_checker_;

  DISALLOW_COPY_AND_ASSIGN(WorkerThreadDelegate);
};

#if defined(OS_WIN)

class WorkerThreadCOMDelegate : public WorkerThreadDelegate {
 public:
  WorkerThreadCOMDelegate(const std::string& thread_name,
                          WorkerThread::ThreadLabel thread_label,
                          TrackedRef<TaskTracker> task_tracker)
      : WorkerThreadDelegate(thread_name,
                             thread_label,
                             std::move(task_tracker)) {}

  ~WorkerThreadCOMDelegate() override { DCHECK(!scoped_com_initializer_); }

  // WorkerThread::Delegate:
  void OnMainEntry(const WorkerThread* worker) override {
    WorkerThreadDelegate::OnMainEntry(worker);

    scoped_com_initializer_ = std::make_unique<win::ScopedCOMInitializer>();
  }

  RegisteredTaskSource GetWork(WorkerThread* worker) override {
    // This scheme below allows us to cover the following scenarios:
    // * Only WorkerThreadDelegate::GetWork() has work:
    //   Always return the task source from GetWork().
    // * Only the Windows Message Queue has work:
    //   Always return the task source from GetWorkFromWindowsMessageQueue();
    // * Both WorkerThreadDelegate::GetWork() and the Windows Message Queue
    //   have work:
    //   Process task sources from each source round-robin style.
    CheckedAutoLock auto_lock(lock_);

    // |worker_awake_| is always set before a call to WakeUp(), but it is
    // not set when messages are added to the Windows Message Queue. Ensure that
    // it is set before getting work, to avoid unnecessary wake ups.
    //
    // Note: It wouldn't be sufficient to set |worker_awake_| in WaitForWork()
    // when MsgWaitForMultipleObjectsEx() indicates that it was woken up by a
    // Windows Message, because of the following scenario:
    //  T1: PostTask
    //      Queue task
    //      Set |worker_awake_| to true
    //  T2: Woken up by a Windows Message
    //      Set |worker_awake_| to true
    //      Run the task posted by T1
    //      Wait for work
    //  T1: WakeUp()
    //  T2: Woken up by Waitable Event
    //      Does not set |worker_awake_| (wake up not from Windows Message)
    //      GetWork
    //      !! Getting work while |worker_awake_| is false !!
    worker_awake_ = true;
    RegisteredTaskSource task_source;
    if (get_work_first_) {
      task_source = WorkerThreadDelegate::GetWorkLockRequired(worker);
      if (task_source)
        get_work_first_ = false;
    }

    if (!task_source) {
      CheckedAutoUnlock auto_unlock(lock_);
      task_source = GetWorkFromWindowsMessageQueue();
      if (task_source)
        get_work_first_ = true;
    }

    if (!task_source && !get_work_first_) {
      // This case is important if we checked the Windows Message Queue first
      // and found there was no work. We don't want to return null immediately
      // as that could cause the thread to go to sleep while work is waiting via
      // WorkerThreadDelegate::GetWork().
      task_source = WorkerThreadDelegate::GetWorkLockRequired(worker);
    }
    if (!task_source) {
      // The worker will sleep after this returns nullptr.
      worker_awake_ = false;
      return nullptr;
    }
    auto run_status = task_source.WillRunTask();
    DCHECK_NE(run_status, TaskSource::RunStatus::kDisallowed);
    return task_source;
  }

  void OnMainExit(WorkerThread* /* worker */) override {
    scoped_com_initializer_.reset();
  }

  void WaitForWork(WaitableEvent* wake_up_event) override {
    DCHECK(wake_up_event);
    const TimeDelta sleep_time = GetSleepTimeout();
    const DWORD milliseconds_wait = checked_cast<DWORD>(
        sleep_time.is_max() ? INFINITE : sleep_time.InMilliseconds());
    const HANDLE wake_up_event_handle = wake_up_event->handle();
    MsgWaitForMultipleObjectsEx(1, &wake_up_event_handle, milliseconds_wait,
                                QS_ALLINPUT, 0);
  }

 private:
  RegisteredTaskSource GetWorkFromWindowsMessageQueue() {
    MSG msg;
    if (PeekMessage(&msg, nullptr, 0, 0, PM_REMOVE) != FALSE) {
      Task pump_message_task(FROM_HERE,
                             BindOnce(
                                 [](MSG msg) {
                                   TranslateMessage(&msg);
                                   DispatchMessage(&msg);
                                 },
                                 std::move(msg)),
                             TimeDelta());
      if (task_tracker()->WillPostTask(
              &pump_message_task, TaskShutdownBehavior::SKIP_ON_SHUTDOWN)) {
        auto transaction = message_pump_sequence_->BeginTransaction();
        const bool sequence_should_be_queued = transaction.WillPushTask();
        DCHECK(sequence_should_be_queued)
            << "GetWorkFromWindowsMessageQueue() does not expect "
               "queueing of pump tasks.";
        auto registered_task_source = task_tracker_->RegisterTaskSource(
            std::move(message_pump_sequence_));
        if (!registered_task_source)
          return nullptr;
        transaction.PushTask(std::move(pump_message_task));
        return registered_task_source;
      }
    }
    return nullptr;
  }

  bool get_work_first_ = true;
  const scoped_refptr<Sequence> message_pump_sequence_ =
      MakeRefCounted<Sequence>(TaskTraits{MayBlock()},
                               nullptr,
                               TaskSourceExecutionMode::kParallel);
  std::unique_ptr<win::ScopedCOMInitializer> scoped_com_initializer_;

  DISALLOW_COPY_AND_ASSIGN(WorkerThreadCOMDelegate);
};

#endif  // defined(OS_WIN)

}  // namespace

class PooledSingleThreadTaskRunnerManager::PooledSingleThreadTaskRunner
    : public SingleThreadTaskRunner {
 public:
  // Constructs a PooledSingleThreadTaskRunner that indirectly controls the
  // lifetime of a dedicated |worker| for |traits|.
  PooledSingleThreadTaskRunner(PooledSingleThreadTaskRunnerManager* const outer,
                               const TaskTraits& traits,
                               WorkerThread* worker,
                               SingleThreadTaskRunnerThreadMode thread_mode)
      : outer_(outer),
        worker_(worker),
        thread_mode_(thread_mode),
        sequence_(
            MakeRefCounted<Sequence>(traits,
                                     this,
                                     TaskSourceExecutionMode::kSingleThread)) {
    DCHECK(outer_);
    DCHECK(worker_);
  }

  // SingleThreadTaskRunner:
  bool PostDelayedTask(const Location& from_here,
                       OnceClosure closure,
                       TimeDelta delay) override {
    if (!g_manager_is_alive)
      return false;

    Task task(from_here, std::move(closure), delay);

    if (!outer_->task_tracker_->WillPostTask(&task,
                                             sequence_->shutdown_behavior())) {
      return false;
    }

    if (task.delayed_run_time.is_null())
      return GetDelegate()->PostTaskNow(sequence_, std::move(task));

    // Unretained(GetDelegate()) is safe because this TaskRunner and its
    // worker are kept alive as long as there are pending Tasks.
    outer_->delayed_task_manager_->AddDelayedTask(
        std::move(task),
        BindOnce(IgnoreResult(&WorkerThreadDelegate::PostTaskNow),
                 Unretained(GetDelegate()), sequence_),
        this);
    return true;
  }

  bool PostNonNestableDelayedTask(const Location& from_here,
                                  OnceClosure closure,
                                  TimeDelta delay) override {
    // Tasks are never nested within the thread pool.
    return PostDelayedTask(from_here, std::move(closure), delay);
  }

  bool RunsTasksInCurrentSequence() const override {
    if (!g_manager_is_alive)
      return false;
    return GetDelegate()->RunsTasksInCurrentSequence();
  }

 private:
  ~PooledSingleThreadTaskRunner() override {
    // Only unregister if this is a DEDICATED SingleThreadTaskRunner. SHARED
    // task runner WorkerThreads are managed separately as they are reused.
    // |g_manager_is_alive| avoids a use-after-free should this
    // PooledSingleThreadTaskRunner outlive its manager. It is safe to access
    // |g_manager_is_alive| without synchronization primitives as it is const
    // for the lifetime of the manager and ~PooledSingleThreadTaskRunner()
    // either happens prior to the end of JoinForTesting() (which happens-before
    // manager's destruction) or on main thread after the task environment's
    // entire destruction (which happens-after the manager's destruction). Yes,
    // there's a theoretical use case where the last ref to this
    // PooledSingleThreadTaskRunner is handed to a thread not controlled by
    // thread_pool and that this ends up causing
    // ~PooledSingleThreadTaskRunner() to race with
    // ~PooledSingleThreadTaskRunnerManager() but this is intentionally not
    // supported (and it doesn't matter in production where we leak the task
    // environment for such reasons). TSan should catch this weird paradigm
    // should anyone elect to use it in a unit test and the error would point
    // here.
    if (g_manager_is_alive &&
        thread_mode_ == SingleThreadTaskRunnerThreadMode::DEDICATED) {
      outer_->UnregisterWorkerThread(worker_);
    }
  }

  WorkerThreadDelegate* GetDelegate() const {
    return static_cast<WorkerThreadDelegate*>(worker_->delegate());
  }

  PooledSingleThreadTaskRunnerManager* const outer_;
  WorkerThread* const worker_;
  const SingleThreadTaskRunnerThreadMode thread_mode_;
  const scoped_refptr<Sequence> sequence_;

  DISALLOW_COPY_AND_ASSIGN(PooledSingleThreadTaskRunner);
};

PooledSingleThreadTaskRunnerManager::PooledSingleThreadTaskRunnerManager(
    TrackedRef<TaskTracker> task_tracker,
    DelayedTaskManager* delayed_task_manager)
    : task_tracker_(std::move(task_tracker)),
      delayed_task_manager_(delayed_task_manager) {
  DCHECK(task_tracker_);
  DCHECK(delayed_task_manager_);
#if defined(OS_WIN)
  static_assert(std::extent<decltype(shared_com_worker_threads_)>() ==
                    std::extent<decltype(shared_worker_threads_)>(),
                "The size of |shared_com_worker_threads_| must match "
                "|shared_worker_threads_|");
  static_assert(
      std::extent<
          std::remove_reference<decltype(shared_com_worker_threads_[0])>>() ==
          std::extent<
              std::remove_reference<decltype(shared_worker_threads_[0])>>(),
      "The size of |shared_com_worker_threads_| must match "
      "|shared_worker_threads_|");
#endif  // defined(OS_WIN)
  DCHECK(!g_manager_is_alive);
  g_manager_is_alive = true;
}

PooledSingleThreadTaskRunnerManager::~PooledSingleThreadTaskRunnerManager() {
  DCHECK(g_manager_is_alive);
  g_manager_is_alive = false;
}

void PooledSingleThreadTaskRunnerManager::Start(
    WorkerThreadObserver* worker_thread_observer) {
  DCHECK(!worker_thread_observer_);
  worker_thread_observer_ = worker_thread_observer;

  decltype(workers_) workers_to_start;
  {
    CheckedAutoLock auto_lock(lock_);
    started_ = true;
    workers_to_start = workers_;
  }

  // Start workers that were created before this method was called.
  // Workers that already need to wake up are already signaled as part of
  // PooledSingleThreadTaskRunner::PostTaskNow(). As a result, it's
  // unnecessary to call WakeUp() for each worker (in fact, an extraneous
  // WakeUp() would be racy and wrong - see https://crbug.com/862582).
  for (scoped_refptr<WorkerThread> worker : workers_to_start) {
    worker->Start(worker_thread_observer_);
  }
}

void PooledSingleThreadTaskRunnerManager::DidUpdateCanRunPolicy() {
  decltype(workers_) workers_to_update;

  {
    CheckedAutoLock auto_lock(lock_);
    if (!started_)
      return;
    workers_to_update = workers_;
  }
  // Any worker created after the lock is released will see the latest
  // CanRunPolicy if tasks are posted to it and thus doesn't need a
  // DidUpdateCanRunPolicy() notification.
  for (auto& worker : workers_to_update) {
    static_cast<WorkerThreadDelegate*>(worker->delegate())
        ->DidUpdateCanRunPolicy();
  }
}

scoped_refptr<SingleThreadTaskRunner>
PooledSingleThreadTaskRunnerManager::CreateSingleThreadTaskRunner(
    const TaskTraits& traits,
    SingleThreadTaskRunnerThreadMode thread_mode) {
  return CreateTaskRunnerImpl<WorkerThreadDelegate>(traits, thread_mode);
}

#if defined(OS_WIN)
scoped_refptr<SingleThreadTaskRunner>
PooledSingleThreadTaskRunnerManager::CreateCOMSTATaskRunner(
    const TaskTraits& traits,
    SingleThreadTaskRunnerThreadMode thread_mode) {
  return CreateTaskRunnerImpl<WorkerThreadCOMDelegate>(traits, thread_mode);
}
#endif  // defined(OS_WIN)

// static
PooledSingleThreadTaskRunnerManager::ContinueOnShutdown
PooledSingleThreadTaskRunnerManager::TraitsToContinueOnShutdown(
    const TaskTraits& traits) {
  if (traits.shutdown_behavior() == TaskShutdownBehavior::CONTINUE_ON_SHUTDOWN)
    return IS_CONTINUE_ON_SHUTDOWN;
  return IS_NOT_CONTINUE_ON_SHUTDOWN;
}

template <typename DelegateType>
scoped_refptr<PooledSingleThreadTaskRunnerManager::PooledSingleThreadTaskRunner>
PooledSingleThreadTaskRunnerManager::CreateTaskRunnerImpl(
    const TaskTraits& traits,
    SingleThreadTaskRunnerThreadMode thread_mode) {
  DCHECK(thread_mode != SingleThreadTaskRunnerThreadMode::SHARED ||
         !traits.with_base_sync_primitives())
      << "Using WithBaseSyncPrimitives() on a shared SingleThreadTaskRunner "
         "may cause deadlocks. Either reevaluate your usage (e.g. use "
         "SequencedTaskRunner) or use "
         "SingleThreadTaskRunnerThreadMode::DEDICATED.";
  // To simplify the code, |dedicated_worker| is a local only variable that
  // allows the code to treat both the DEDICATED and SHARED cases similarly for
  // SingleThreadTaskRunnerThreadMode. In DEDICATED, the scoped_refptr is backed
  // by a local variable and in SHARED, the scoped_refptr is backed by a member
  // variable.
  WorkerThread* dedicated_worker = nullptr;
  WorkerThread*& worker =
      thread_mode == SingleThreadTaskRunnerThreadMode::DEDICATED
          ? dedicated_worker
          : GetSharedWorkerThreadForTraits<DelegateType>(traits);
  bool new_worker = false;
  bool started;
  {
    CheckedAutoLock auto_lock(lock_);
    if (!worker) {
      const auto& environment_params =
          kEnvironmentParams[GetEnvironmentIndexForTraits(traits)];
      std::string worker_name;
      if (thread_mode == SingleThreadTaskRunnerThreadMode::SHARED)
        worker_name += "Shared";
      worker_name += environment_params.name_suffix;
      worker = CreateAndRegisterWorkerThread<DelegateType>(
          worker_name, thread_mode,
          CanUseBackgroundPriorityForWorkerThread()
              ? environment_params.priority_hint
              : ThreadPriority::NORMAL);
      new_worker = true;
    }
    started = started_;
  }

  if (new_worker && started)
    worker->Start(worker_thread_observer_);

  return MakeRefCounted<PooledSingleThreadTaskRunner>(this, traits, worker,
                                                      thread_mode);
}

void PooledSingleThreadTaskRunnerManager::JoinForTesting() {
  decltype(workers_) local_workers;
  {
    CheckedAutoLock auto_lock(lock_);
    local_workers = std::move(workers_);
  }

  for (const auto& worker : local_workers) {
    static_cast<WorkerThreadDelegate*>(worker->delegate())
        ->EnableFlushPriorityQueueTaskSourcesOnDestroyForTesting();
    worker->JoinForTesting();
  }

  {
    CheckedAutoLock auto_lock(lock_);
    DCHECK(workers_.empty())
        << "New worker(s) unexpectedly registered during join.";
    workers_ = std::move(local_workers);
  }

  // Release shared WorkerThreads at the end so they get joined above. If
  // this call happens before the joins, the WorkerThreads are effectively
  // detached and may outlive the PooledSingleThreadTaskRunnerManager.
  ReleaseSharedWorkerThreads();
}

template <>
std::unique_ptr<WorkerThreadDelegate>
PooledSingleThreadTaskRunnerManager::CreateWorkerThreadDelegate<
    WorkerThreadDelegate>(const std::string& name,
                          int id,
                          SingleThreadTaskRunnerThreadMode thread_mode) {
  return std::make_unique<WorkerThreadDelegate>(
      StringPrintf("ThreadPoolSingleThread%s%d", name.c_str(), id),
      thread_mode == SingleThreadTaskRunnerThreadMode::DEDICATED
          ? WorkerThread::ThreadLabel::DEDICATED
          : WorkerThread::ThreadLabel::SHARED,
      task_tracker_);
}

#if defined(OS_WIN)
template <>
std::unique_ptr<WorkerThreadDelegate>
PooledSingleThreadTaskRunnerManager::CreateWorkerThreadDelegate<
    WorkerThreadCOMDelegate>(const std::string& name,
                             int id,
                             SingleThreadTaskRunnerThreadMode thread_mode) {
  return std::make_unique<WorkerThreadCOMDelegate>(
      StringPrintf("ThreadPoolSingleThreadCOMSTA%s%d", name.c_str(), id),
      thread_mode == SingleThreadTaskRunnerThreadMode::DEDICATED
          ? WorkerThread::ThreadLabel::DEDICATED_COM
          : WorkerThread::ThreadLabel::SHARED_COM,
      task_tracker_);
}
#endif  // defined(OS_WIN)

template <typename DelegateType>
WorkerThread*
PooledSingleThreadTaskRunnerManager::CreateAndRegisterWorkerThread(
    const std::string& name,
    SingleThreadTaskRunnerThreadMode thread_mode,
    ThreadPriority priority_hint) {
  int id = next_worker_id_++;
  std::unique_ptr<WorkerThreadDelegate> delegate =
      CreateWorkerThreadDelegate<DelegateType>(name, id, thread_mode);
  WorkerThreadDelegate* delegate_raw = delegate.get();
  scoped_refptr<WorkerThread> worker = MakeRefCounted<WorkerThread>(
      priority_hint, std::move(delegate), task_tracker_);
  delegate_raw->set_worker(worker.get());
  workers_.emplace_back(std::move(worker));
  return workers_.back().get();
}

template <>
WorkerThread*&
PooledSingleThreadTaskRunnerManager::GetSharedWorkerThreadForTraits<
    WorkerThreadDelegate>(const TaskTraits& traits) {
  return shared_worker_threads_[GetEnvironmentIndexForTraits(traits)]
                               [TraitsToContinueOnShutdown(traits)];
}

#if defined(OS_WIN)
template <>
WorkerThread*&
PooledSingleThreadTaskRunnerManager::GetSharedWorkerThreadForTraits<
    WorkerThreadCOMDelegate>(const TaskTraits& traits) {
  return shared_com_worker_threads_[GetEnvironmentIndexForTraits(traits)]
                                   [TraitsToContinueOnShutdown(traits)];
}
#endif  // defined(OS_WIN)

void PooledSingleThreadTaskRunnerManager::UnregisterWorkerThread(
    WorkerThread* worker) {
  // Cleanup uses a CheckedLock, so call Cleanup() after releasing |lock_|.
  scoped_refptr<WorkerThread> worker_to_destroy;
  {
    CheckedAutoLock auto_lock(lock_);

    // Skip when joining (the join logic takes care of the rest).
    if (workers_.empty())
      return;

    auto worker_iter = std::find(workers_.begin(), workers_.end(), worker);
    DCHECK(worker_iter != workers_.end());
    worker_to_destroy = std::move(*worker_iter);
    workers_.erase(worker_iter);
  }
  worker_to_destroy->Cleanup();
}

void PooledSingleThreadTaskRunnerManager::ReleaseSharedWorkerThreads() {
  decltype(shared_worker_threads_) local_shared_worker_threads;
#if defined(OS_WIN)
  decltype(shared_com_worker_threads_) local_shared_com_worker_threads;
#endif
  {
    CheckedAutoLock auto_lock(lock_);
    for (size_t i = 0; i < base::size(shared_worker_threads_); ++i) {
      for (size_t j = 0; j < base::size(shared_worker_threads_[i]); ++j) {
        local_shared_worker_threads[i][j] = shared_worker_threads_[i][j];
        shared_worker_threads_[i][j] = nullptr;
#if defined(OS_WIN)
        local_shared_com_worker_threads[i][j] =
            shared_com_worker_threads_[i][j];
        shared_com_worker_threads_[i][j] = nullptr;
#endif
      }
    }
  }

  for (size_t i = 0; i < base::size(local_shared_worker_threads); ++i) {
    for (size_t j = 0; j < base::size(local_shared_worker_threads[i]); ++j) {
      if (local_shared_worker_threads[i][j])
        UnregisterWorkerThread(local_shared_worker_threads[i][j]);
#if defined(OS_WIN)
      if (local_shared_com_worker_threads[i][j])
        UnregisterWorkerThread(local_shared_com_worker_threads[i][j]);
#endif
    }
  }
}

}  // namespace internal
}  // namespace base
