// Copyright 2017 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

#ifndef BASE_TASK_SEQUENCE_MANAGER_THREAD_CONTROLLER_IMPL_H_
#define BASE_TASK_SEQUENCE_MANAGER_THREAD_CONTROLLER_IMPL_H_

#include <memory>

#include "base/cancelable_callback.h"
#include "base/macros.h"
#include "base/memory/weak_ptr.h"
#include "base/run_loop.h"
#include "base/sequence_checker.h"
#include "base/single_thread_task_runner.h"
#include "base/task/common/task_annotator.h"
#include "base/task/sequence_manager/associated_thread_id.h"
#include "base/task/sequence_manager/thread_controller.h"
#include "base/task/sequence_manager/work_deduplicator.h"
#include "build/build_config.h"

namespace base {
namespace sequence_manager {
namespace internal {
class SequenceManagerImpl;

// This is the interface between a SequenceManager which sits on top of an
// underlying SequenceManagerImpl or SingleThreadTaskRunner. Currently it's only
// used for workers in blink although we'd intend to migrate those to
// ThreadControllerWithMessagePumpImpl (https://crbug.com/948051). Long term we
// intend to use this for sequence funneling.
class BASE_EXPORT ThreadControllerImpl : public ThreadController,
                                         public RunLoop::NestingObserver {
 public:
  ~ThreadControllerImpl() override;

  // TODO(https://crbug.com/948051): replace |funneled_sequence_manager| with
  // |funneled_task_runner| when we sort out the workers
  static std::unique_ptr<ThreadControllerImpl> Create(
      SequenceManagerImpl* funneled_sequence_manager,
      const TickClock* time_source);

  // ThreadController:
  void SetWorkBatchSize(int work_batch_size) override;
  void WillQueueTask(PendingTask* pending_task,
                     const char* task_queue_name) override;
  void ScheduleWork() override;
  void BindToCurrentThread(std::unique_ptr<MessagePump> message_pump) override;
  void SetNextDelayedDoWork(LazyNow* lazy_now, TimeTicks run_time) override;
  void SetSequencedTaskSource(SequencedTaskSource* sequence) override;
  void SetTimerSlack(TimerSlack timer_slack) override;
  bool RunsTasksInCurrentSequence() override;
  const TickClock* GetClock() override;
  void SetDefaultTaskRunner(scoped_refptr<SingleThreadTaskRunner>) override;
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
  ThreadControllerImpl(SequenceManagerImpl* sequence_manager,
                       scoped_refptr<SingleThreadTaskRunner> task_runner,
                       const TickClock* time_source);

  // TODO(altimin): Make these const. Blocked on removing
  // lazy initialisation support.
  SequenceManagerImpl* funneled_sequence_manager_;
  scoped_refptr<SingleThreadTaskRunner> task_runner_;

  RunLoop::NestingObserver* nesting_observer_ = nullptr;

 private:
  enum class WorkType { kImmediate, kDelayed };

  void DoWork(WorkType work_type);

  // TODO(scheduler-dev): Maybe fold this into the main class and use
  // thread annotations.
  struct MainSequenceOnly {
    MainSequenceOnly();
    ~MainSequenceOnly();

    int nesting_depth = 0;
    int work_batch_size_ = 1;

    TimeTicks next_delayed_do_work = TimeTicks::Max();
  };

  scoped_refptr<AssociatedThreadId> associated_thread_;

  MainSequenceOnly main_sequence_only_;
  MainSequenceOnly& main_sequence_only() {
    DCHECK_CALLED_ON_VALID_SEQUENCE(associated_thread_->sequence_checker);
    return main_sequence_only_;
  }
  const MainSequenceOnly& main_sequence_only() const {
    DCHECK_CALLED_ON_VALID_SEQUENCE(associated_thread_->sequence_checker);
    return main_sequence_only_;
  }

  scoped_refptr<SingleThreadTaskRunner> message_loop_task_runner_;
  const TickClock* time_source_;
  RepeatingClosure immediate_do_work_closure_;
  RepeatingClosure delayed_do_work_closure_;
  CancelableClosure cancelable_delayed_do_work_closure_;
  SequencedTaskSource* sequence_ = nullptr;  // Not owned.
  TaskAnnotator task_annotator_;
  WorkDeduplicator work_deduplicator_;

#if DCHECK_IS_ON()
  bool default_task_runner_set_ = false;
#endif

  WeakPtrFactory<ThreadControllerImpl> weak_factory_{this};

  DISALLOW_COPY_AND_ASSIGN(ThreadControllerImpl);
};

}  // namespace internal
}  // namespace sequence_manager
}  // namespace base

#endif  // BASE_TASK_SEQUENCE_MANAGER_THREAD_CONTROLLER_IMPL_H_
