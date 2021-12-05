// Copyright 2017 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

#include "base/task/sequence_manager/thread_controller_impl.h"

#include <algorithm>

#include "base/bind.h"
#include "base/memory/ptr_util.h"
#include "base/message_loop/message_pump.h"
#include "base/run_loop.h"
#include "base/task/sequence_manager/lazy_now.h"
#include "base/task/sequence_manager/sequence_manager_impl.h"
#include "base/task/sequence_manager/sequenced_task_source.h"
#include "base/trace_event/trace_event.h"

namespace base {
namespace sequence_manager {
namespace internal {

using ShouldScheduleWork = WorkDeduplicator::ShouldScheduleWork;

ThreadControllerImpl::ThreadControllerImpl(
    SequenceManagerImpl* funneled_sequence_manager,
    scoped_refptr<SingleThreadTaskRunner> task_runner,
    const TickClock* time_source)
    : funneled_sequence_manager_(funneled_sequence_manager),
      task_runner_(task_runner),
      associated_thread_(AssociatedThreadId::CreateUnbound()),
      message_loop_task_runner_(funneled_sequence_manager
                                    ? funneled_sequence_manager->GetTaskRunner()
                                    : nullptr),
      time_source_(time_source),
      work_deduplicator_(associated_thread_) {
  if (task_runner_ || funneled_sequence_manager_)
    work_deduplicator_.BindToCurrentThread();
  immediate_do_work_closure_ =
      BindRepeating(&ThreadControllerImpl::DoWork, weak_factory_.GetWeakPtr(),
                    WorkType::kImmediate);
  delayed_do_work_closure_ =
      BindRepeating(&ThreadControllerImpl::DoWork, weak_factory_.GetWeakPtr(),
                    WorkType::kDelayed);
}

ThreadControllerImpl::~ThreadControllerImpl() = default;

ThreadControllerImpl::MainSequenceOnly::MainSequenceOnly() = default;

ThreadControllerImpl::MainSequenceOnly::~MainSequenceOnly() = default;

std::unique_ptr<ThreadControllerImpl> ThreadControllerImpl::Create(
    SequenceManagerImpl* funneled_sequence_manager,
    const TickClock* time_source) {
  return WrapUnique(new ThreadControllerImpl(
      funneled_sequence_manager,
      funneled_sequence_manager ? funneled_sequence_manager->GetTaskRunner()
                                : nullptr,
      time_source));
}

void ThreadControllerImpl::SetSequencedTaskSource(
    SequencedTaskSource* sequence) {
  DCHECK_CALLED_ON_VALID_SEQUENCE(associated_thread_->sequence_checker);
  DCHECK(sequence);
  DCHECK(!sequence_);
  sequence_ = sequence;
}

void ThreadControllerImpl::SetTimerSlack(TimerSlack timer_slack) {
  if (!funneled_sequence_manager_)
    return;
  funneled_sequence_manager_->SetTimerSlack(timer_slack);
}

void ThreadControllerImpl::ScheduleWork() {
  TRACE_EVENT0(TRACE_DISABLED_BY_DEFAULT("sequence_manager"),
               "ThreadControllerImpl::ScheduleWork::PostTask");

  if (work_deduplicator_.OnWorkRequested() ==
      ShouldScheduleWork::kScheduleImmediate)
    task_runner_->PostTask(FROM_HERE, immediate_do_work_closure_);
}

void ThreadControllerImpl::SetNextDelayedDoWork(LazyNow* lazy_now,
                                                TimeTicks run_time) {
  DCHECK_CALLED_ON_VALID_SEQUENCE(associated_thread_->sequence_checker);
  DCHECK(sequence_);

  if (main_sequence_only().next_delayed_do_work == run_time)
    return;

  // Cancel DoWork if it was scheduled and we set an "infinite" delay now.
  if (run_time == TimeTicks::Max()) {
    cancelable_delayed_do_work_closure_.Cancel();
    main_sequence_only().next_delayed_do_work = TimeTicks::Max();
    return;
  }

  if (work_deduplicator_.OnDelayedWorkRequested() ==
      ShouldScheduleWork::kNotNeeded) {
    return;
  }

  base::TimeDelta delay = std::max(TimeDelta(), run_time - lazy_now->Now());
  TRACE_EVENT1(TRACE_DISABLED_BY_DEFAULT("sequence_manager"),
               "ThreadControllerImpl::SetNextDelayedDoWork::PostDelayedTask",
               "delay_ms", delay.InMillisecondsF());

  main_sequence_only().next_delayed_do_work = run_time;
  // Reset also causes cancellation of the previous DoWork task.
  cancelable_delayed_do_work_closure_.Reset(delayed_do_work_closure_);
  task_runner_->PostDelayedTask(
      FROM_HERE, cancelable_delayed_do_work_closure_.callback(), delay);
}

bool ThreadControllerImpl::RunsTasksInCurrentSequence() {
  return task_runner_->RunsTasksInCurrentSequence();
}

const TickClock* ThreadControllerImpl::GetClock() {
  return time_source_;
}

void ThreadControllerImpl::SetDefaultTaskRunner(
    scoped_refptr<SingleThreadTaskRunner> task_runner) {
#if DCHECK_IS_ON()
  default_task_runner_set_ = true;
#endif
  if (!funneled_sequence_manager_)
    return;
  funneled_sequence_manager_->SetTaskRunner(task_runner);
}

scoped_refptr<SingleThreadTaskRunner>
ThreadControllerImpl::GetDefaultTaskRunner() {
  return funneled_sequence_manager_->GetTaskRunner();
}

void ThreadControllerImpl::RestoreDefaultTaskRunner() {
  if (!funneled_sequence_manager_)
    return;
  funneled_sequence_manager_->SetTaskRunner(message_loop_task_runner_);
}

void ThreadControllerImpl::BindToCurrentThread(
    std::unique_ptr<MessagePump> message_pump) {
  NOTREACHED();
}

void ThreadControllerImpl::WillQueueTask(PendingTask* pending_task,
                                         const char* task_queue_name) {
  task_annotator_.WillQueueTask("SequenceManager PostTask", pending_task,
                                task_queue_name);
}

void ThreadControllerImpl::DoWork(WorkType work_type) {
  TRACE_EVENT0(TRACE_DISABLED_BY_DEFAULT("sequence_manager"),
               "ThreadControllerImpl::DoWork");

  DCHECK_CALLED_ON_VALID_SEQUENCE(associated_thread_->sequence_checker);
  DCHECK(sequence_);

  work_deduplicator_.OnWorkStarted();

  WeakPtr<ThreadControllerImpl> weak_ptr = weak_factory_.GetWeakPtr();
  // TODO(scheduler-dev): Consider moving to a time based work batch instead.
  for (int i = 0; i < main_sequence_only().work_batch_size_; i++) {
    Task* task = sequence_->SelectNextTask();
    if (!task)
      break;

    // Trace-parsing tools (DevTools, Lighthouse, etc) consume this event
    // to determine long tasks.
    // The event scope must span across DidRunTask call below to make sure
    // it covers RunMicrotasks event.
    // See https://crbug.com/681863 and https://crbug.com/874982
    TRACE_EVENT0(TRACE_DISABLED_BY_DEFAULT("devtools.timeline"), "RunTask");

    {
      // Trace events should finish before we call DidRunTask to ensure that
      // SequenceManager trace events do not interfere with them.
      TRACE_TASK_EXECUTION("ThreadControllerImpl::RunTask", *task);
      task_annotator_.RunTask("SequenceManager RunTask", task);
    }

    if (!weak_ptr)
      return;

    sequence_->DidRunTask();

    // NOTE: https://crbug.com/828835.
    // When we're running inside a nested RunLoop it may quit anytime, so any
    // outstanding pending tasks must run in the outer RunLoop
    // (see SequenceManagerTestWithMessageLoop.QuitWhileNested test).
    // Unfortunately, it's MessageLoop who's receving that signal and we can't
    // know it before we return from DoWork, hence, OnExitNestedRunLoop
    // will be called later. Since we must implement ThreadController and
    // SequenceManager in conformance with MessageLoop task runners, we need
    // to disable this batching optimization while nested.
    // Implementing MessagePump::Delegate ourselves will help to resolve this
    // issue.
    if (main_sequence_only().nesting_depth > 0)
      break;
  }

  work_deduplicator_.WillCheckForMoreWork();

  LazyNow lazy_now(time_source_);
  TimeDelta delay_till_next_task = sequence_->DelayTillNextTask(&lazy_now);
  // The OnSystemIdle callback allows the TimeDomains to advance virtual time
  // in which case we now have immediate word to do.
  if (delay_till_next_task <= TimeDelta() || sequence_->OnSystemIdle()) {
    // The next task needs to run immediately, post a continuation if
    // another thread didn't get there first.
    if (work_deduplicator_.DidCheckForMoreWork(
            WorkDeduplicator::NextTask::kIsImmediate) ==
        ShouldScheduleWork::kScheduleImmediate) {
      task_runner_->PostTask(FROM_HERE, immediate_do_work_closure_);
    }
    return;
  }

  // It looks like we have a non-zero delay, however another thread may have
  // posted an immediate task while we computed the delay.
  if (work_deduplicator_.DidCheckForMoreWork(
          WorkDeduplicator::NextTask::kIsDelayed) ==
      ShouldScheduleWork::kScheduleImmediate) {
    task_runner_->PostTask(FROM_HERE, immediate_do_work_closure_);
    return;
  }

  // Check if there's no future work.
  if (delay_till_next_task == TimeDelta::Max()) {
    main_sequence_only().next_delayed_do_work = TimeTicks::Max();
    cancelable_delayed_do_work_closure_.Cancel();
    return;
  }

  // Check if we've already requested the required delay.
  TimeTicks next_task_at = lazy_now.Now() + delay_till_next_task;
  if (next_task_at == main_sequence_only().next_delayed_do_work)
    return;

  // Schedule a callback after |delay_till_next_task| and cancel any previous
  // callback.
  main_sequence_only().next_delayed_do_work = next_task_at;
  cancelable_delayed_do_work_closure_.Reset(delayed_do_work_closure_);
  task_runner_->PostDelayedTask(FROM_HERE,
                                cancelable_delayed_do_work_closure_.callback(),
                                delay_till_next_task);
}

void ThreadControllerImpl::AddNestingObserver(
    RunLoop::NestingObserver* observer) {
  DCHECK_CALLED_ON_VALID_SEQUENCE(associated_thread_->sequence_checker);
  nesting_observer_ = observer;
  RunLoop::AddNestingObserverOnCurrentThread(this);
}

void ThreadControllerImpl::RemoveNestingObserver(
    RunLoop::NestingObserver* observer) {
  DCHECK_CALLED_ON_VALID_SEQUENCE(associated_thread_->sequence_checker);
  DCHECK_EQ(observer, nesting_observer_);
  nesting_observer_ = nullptr;
  RunLoop::RemoveNestingObserverOnCurrentThread(this);
}

const scoped_refptr<AssociatedThreadId>&
ThreadControllerImpl::GetAssociatedThread() const {
  return associated_thread_;
}

void ThreadControllerImpl::OnBeginNestedRunLoop() {
  main_sequence_only().nesting_depth++;

  // Just assume we have a pending task and post a DoWork to make sure we don't
  // grind to a halt while nested.
  work_deduplicator_.OnWorkRequested();  // Set the pending DoWork flag.
  task_runner_->PostTask(FROM_HERE, immediate_do_work_closure_);

  if (nesting_observer_)
    nesting_observer_->OnBeginNestedRunLoop();
}

void ThreadControllerImpl::OnExitNestedRunLoop() {
  main_sequence_only().nesting_depth--;
  if (nesting_observer_)
    nesting_observer_->OnExitNestedRunLoop();
}

void ThreadControllerImpl::SetWorkBatchSize(int work_batch_size) {
  main_sequence_only().work_batch_size_ = work_batch_size;
}

void ThreadControllerImpl::SetTaskExecutionAllowed(bool allowed) {
  NOTREACHED();
}

bool ThreadControllerImpl::IsTaskExecutionAllowed() const {
  return true;
}

bool ThreadControllerImpl::ShouldQuitRunLoopWhenIdle() {
  // The MessageLoop does not expose the API needed to support this query.
  return false;
}

MessagePump* ThreadControllerImpl::GetBoundMessagePump() const {
  return nullptr;
}

#if defined(OS_IOS) || defined(OS_ANDROID)
void ThreadControllerImpl::AttachToMessagePump() {
  NOTREACHED();
}
#endif  // OS_IOS || OS_ANDROID

#if defined(OS_IOS)
void ThreadControllerImpl::DetachFromMessagePump() {
  NOTREACHED();
}
#endif  // OS_IOS

}  // namespace internal
}  // namespace sequence_manager
}  // namespace base
