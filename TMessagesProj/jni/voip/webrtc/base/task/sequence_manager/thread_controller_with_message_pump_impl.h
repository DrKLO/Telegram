// Copyright 2018 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

#ifndef BASE_TASK_SEQUENCE_MANAGER_THREAD_CONTROLLER_WITH_MESSAGE_PUMP_IMPL_H_
#define BASE_TASK_SEQUENCE_MANAGER_THREAD_CONTROLLER_WITH_MESSAGE_PUMP_IMPL_H_

#include <memory>

#include "base/message_loop/message_pump.h"
#include "base/message_loop/work_id_provider.h"
#include "base/optional.h"
#include "base/run_loop.h"
#include "base/task/common/checked_lock.h"
#include "base/task/common/task_annotator.h"
#include "base/task/sequence_manager/associated_thread_id.h"
#include "base/task/sequence_manager/sequence_manager_impl.h"
#include "base/task/sequence_manager/sequenced_task_source.h"
#include "base/task/sequence_manager/thread_controller.h"
#include "base/task/sequence_manager/work_deduplicator.h"
#include "base/thread_annotations.h"
#include "base/threading/hang_watcher.h"
#include "base/threading/platform_thread.h"
#include "base/threading/sequence_local_storage_map.h"
#include "base/threading/thread_task_runner_handle.h"
#include "build/build_config.h"

namespace base {
namespace sequence_manager {
namespace internal {

// This is the interface between the SequenceManager and the MessagePump.
class BASE_EXPORT ThreadControllerWithMessagePumpImpl
    : public ThreadController,
      public MessagePump::Delegate,
      public RunLoop::Delegate,
      public RunLoop::NestingObserver {
 public:
  ThreadControllerWithMessagePumpImpl(
      std::unique_ptr<MessagePump> message_pump,
      const SequenceManager::Settings& settings);
  ~ThreadControllerWithMessagePumpImpl() override;

  using ShouldScheduleWork = WorkDeduplicator::ShouldScheduleWork;

  static std::unique_ptr<ThreadControllerWithMessagePumpImpl> CreateUnbound(
      const SequenceManager::Settings& settings);

  // ThreadController implementation:
  void SetSequencedTaskSource(SequencedTaskSource* task_source) override;
  void BindToCurrentThread(std::unique_ptr<MessagePump> message_pump) override;
  void SetWorkBatchSize(int work_batch_size) override;
  void WillQueueTask(PendingTask* pending_task,
                     const char* task_queue_name) override;
  void ScheduleWork() override;
  void SetNextDelayedDoWork(LazyNow* lazy_now, TimeTicks run_time) override;
  void SetTimerSlack(TimerSlack timer_slack) override;
  const TickClock* GetClock() override;
  bool RunsTasksInCurrentSequence() override;
  void SetDefaultTaskRunner(
      scoped_refptr<SingleThreadTaskRunner> task_runner) override;
  scoped_refptr<SingleThreadTaskRunner> GetDefaultTaskRunner() override;
  void RestoreDefaultTaskRunner() override;
  void AddNestingObserver(RunLoop::NestingObserver* observer) override;
  void RemoveNestingObserver(RunLoop::NestingObserver* observer) override;
  const scoped_refptr<AssociatedThreadId>& GetAssociatedThread() const override;
  void SetTaskExecutionAllowed(bool allowed) override;
  bool IsTaskExecutionAllowed() const override;
  MessagePump* GetBoundMessagePump() const override;
#if defined(OS_IOS) || defined(OS_ANDROID)
  void AttachToMessagePump() override;
#endif
#if defined(OS_IOS)
  void DetachFromMessagePump() override;
#endif
  bool ShouldQuitRunLoopWhenIdle() override;

  // RunLoop::NestingObserver:
  void OnBeginNestedRunLoop() override;
  void OnExitNestedRunLoop() override;

 protected:
  explicit ThreadControllerWithMessagePumpImpl(
      const SequenceManager::Settings& settings);

  // MessagePump::Delegate implementation.
  void BeforeDoInternalWork() override;
  void BeforeWait() override;
  MessagePump::Delegate::NextWorkInfo DoSomeWork() override;
  bool DoIdleWork() override;

  // RunLoop::Delegate implementation.
  void Run(bool application_tasks_allowed, TimeDelta timeout) override;
  void Quit() override;
  void EnsureWorkScheduled() override;

 private:
  friend class DoWorkScope;
  friend class RunScope;

  // Returns the delay till the next task. If there's no delay TimeDelta::Max()
  // will be returned.
  TimeDelta DoWorkImpl(LazyNow* continuation_lazy_now, bool* ran_task);

  void InitializeThreadTaskRunnerHandle()
      EXCLUSIVE_LOCKS_REQUIRED(task_runner_lock_);

  struct MainThreadOnly {
    MainThreadOnly();
    ~MainThreadOnly();

    SequencedTaskSource* task_source = nullptr;            // Not owned.
    RunLoop::NestingObserver* nesting_observer = nullptr;  // Not owned.
    std::unique_ptr<ThreadTaskRunnerHandle> thread_task_runner_handle;

    // Indicates that we should yield DoWork between each task to let a possibly
    // nested RunLoop exit.
    bool quit_pending = false;

    // Whether high resolution timing is enabled or not.
    bool in_high_res_mode = false;

    // Number of tasks processed in a single DoWork invocation.
    int work_batch_size = 1;

    int runloop_count = 0;

    // When the next scheduled delayed work should run, if any.
    TimeTicks next_delayed_do_work = TimeTicks::Max();

    // The time after which the runloop should quit.
    TimeTicks quit_runloop_after = TimeTicks::Max();

    bool task_execution_allowed = true;
  };

  MainThreadOnly& main_thread_only() {
    DCHECK_CALLED_ON_VALID_THREAD(associated_thread_->thread_checker);
    return main_thread_only_;
  }

  const MainThreadOnly& main_thread_only() const {
    DCHECK_CALLED_ON_VALID_THREAD(associated_thread_->thread_checker);
    return main_thread_only_;
  }

  // TODO(altimin): Merge with the one in SequenceManager.
  scoped_refptr<AssociatedThreadId> associated_thread_;
  MainThreadOnly main_thread_only_;

  mutable base::internal::CheckedLock task_runner_lock_;
  scoped_refptr<SingleThreadTaskRunner> task_runner_
      GUARDED_BY(task_runner_lock_);

  WorkDeduplicator work_deduplicator_;

  // Can only be set once (just before calling
  // work_deduplicator_.BindToCurrentThread()). After that only read access is
  // allowed.
  std::unique_ptr<MessagePump> pump_;

  TaskAnnotator task_annotator_;

#if DCHECK_IS_ON()
  const bool log_runloop_quit_and_quit_when_idle_;
  bool quit_when_idle_requested_ = false;
#endif

  const TickClock* time_source_;  // Not owned.

  // Non-null provider of id state for identifying distinct work items executed
  // by the message loop (task, event, etc.). Cached on the class to avoid TLS
  // lookups on task execution.
  WorkIdProvider* work_id_provider_ = nullptr;

  // Required to register the current thread as a sequence.
  base::internal::SequenceLocalStorageMap sequence_local_storage_map_;
  std::unique_ptr<
      base::internal::ScopedSetSequenceLocalStorageMapForCurrentThread>
      scoped_set_sequence_local_storage_map_for_current_thread_;

  // Reset at the start of each unit of work to cover the work itself and then
  // transition to the next one.
  base::Optional<HangWatchScope> hang_watch_scope_;

  DISALLOW_COPY_AND_ASSIGN(ThreadControllerWithMessagePumpImpl);
};

}  // namespace internal
}  // namespace sequence_manager
}  // namespace base

#endif  // BASE_TASK_SEQUENCE_MANAGER_THREAD_CONTROLLER_WITH_MESSAGE_PUMP_IMPL_H_
