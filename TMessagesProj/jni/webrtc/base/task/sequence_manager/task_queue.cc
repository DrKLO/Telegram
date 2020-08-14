// Copyright 2017 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

#include "base/task/sequence_manager/task_queue.h"

#include <utility>

#include "base/bind.h"
#include "base/memory/ptr_util.h"
#include "base/task/sequence_manager/associated_thread_id.h"
#include "base/task/sequence_manager/sequence_manager_impl.h"
#include "base/task/sequence_manager/task_queue_impl.h"
#include "base/threading/thread_checker.h"
#include "base/threading/thread_checker_impl.h"
#include "base/time/time.h"

namespace base {
namespace sequence_manager {

namespace {

class NullTaskRunner final : public SingleThreadTaskRunner {
 public:
  NullTaskRunner() {}

  bool PostDelayedTask(const Location& location,
                       OnceClosure callback,
                       TimeDelta delay) override {
    return false;
  }

  bool PostNonNestableDelayedTask(const Location& location,
                                  OnceClosure callback,
                                  TimeDelta delay) override {
    return false;
  }

  bool RunsTasksInCurrentSequence() const override {
    return thread_checker_.CalledOnValidThread();
  }

 private:
  // Ref-counted
  ~NullTaskRunner() override = default;

  ThreadCheckerImpl thread_checker_;
};

// TODO(kraynov): Move NullTaskRunner from //base/test to //base.
scoped_refptr<SingleThreadTaskRunner> CreateNullTaskRunner() {
  return MakeRefCounted<NullTaskRunner>();
}

}  // namespace

TaskQueue::QueueEnabledVoter::QueueEnabledVoter(
    scoped_refptr<TaskQueue> task_queue)
    : task_queue_(std::move(task_queue)), enabled_(true) {
  task_queue_->AddQueueEnabledVoter(enabled_);
}

TaskQueue::QueueEnabledVoter::~QueueEnabledVoter() {
  task_queue_->RemoveQueueEnabledVoter(enabled_);
}

void TaskQueue::QueueEnabledVoter::SetVoteToEnable(bool enabled) {
  if (enabled == enabled_)
    return;
  enabled_ = enabled;
  task_queue_->OnQueueEnabledVoteChanged(enabled_);
}

void TaskQueue::AddQueueEnabledVoter(bool voter_is_enabled) {
  DCHECK_CALLED_ON_VALID_THREAD(associated_thread_->thread_checker);
  ++voter_count_;
  if (voter_is_enabled)
    ++enabled_voter_count_;
}

void TaskQueue::RemoveQueueEnabledVoter(bool voter_is_enabled) {
  DCHECK_CALLED_ON_VALID_THREAD(associated_thread_->thread_checker);
  if (!impl_)
    return;

  bool was_enabled = AreAllQueueEnabledVotersEnabled();
  if (voter_is_enabled) {
    --enabled_voter_count_;
    DCHECK_GE(enabled_voter_count_, 0);
  }

  --voter_count_;
  DCHECK_GE(voter_count_, 0);

  bool is_enabled = AreAllQueueEnabledVotersEnabled();
  if (was_enabled != is_enabled)
    impl_->SetQueueEnabled(is_enabled);
}

bool TaskQueue::AreAllQueueEnabledVotersEnabled() const {
  return enabled_voter_count_ == voter_count_;
}

void TaskQueue::OnQueueEnabledVoteChanged(bool enabled) {
  DCHECK_CALLED_ON_VALID_THREAD(associated_thread_->thread_checker);
  bool was_enabled = AreAllQueueEnabledVotersEnabled();
  if (enabled) {
    ++enabled_voter_count_;
    DCHECK_LE(enabled_voter_count_, voter_count_);
  } else {
    --enabled_voter_count_;
    DCHECK_GE(enabled_voter_count_, 0);
  }

  bool is_enabled = AreAllQueueEnabledVotersEnabled();
  if (was_enabled != is_enabled)
    impl_->SetQueueEnabled(is_enabled);
}

TaskQueue::TaskQueue(std::unique_ptr<internal::TaskQueueImpl> impl,
                     const TaskQueue::Spec& spec)
    : impl_(std::move(impl)),
      sequence_manager_(impl_ ? impl_->GetSequenceManagerWeakPtr() : nullptr),
      associated_thread_((impl_ && impl_->sequence_manager())
                             ? impl_->sequence_manager()->associated_thread()
                             : MakeRefCounted<internal::AssociatedThreadId>()),
      default_task_runner_(impl_ ? impl_->CreateTaskRunner(kTaskTypeNone)
                                 : CreateNullTaskRunner()),
      name_(impl_ ? impl_->GetName() : "") {}

TaskQueue::~TaskQueue() {
  ShutdownTaskQueueGracefully();
}

void TaskQueue::ShutdownTaskQueueGracefully() {
  // scoped_refptr guarantees us that this object isn't used.
  if (!impl_)
    return;
  if (impl_->IsUnregistered())
    return;

  // If we've not been unregistered then this must occur on the main thread.
  DCHECK_CALLED_ON_VALID_THREAD(associated_thread_->thread_checker);
  impl_->SetObserver(nullptr);
  impl_->sequence_manager()->ShutdownTaskQueueGracefully(TakeTaskQueueImpl());
}

TaskQueue::TaskTiming::TaskTiming(bool has_wall_time, bool has_thread_time)
    : has_wall_time_(has_wall_time), has_thread_time_(has_thread_time) {}

void TaskQueue::TaskTiming::RecordTaskStart(LazyNow* now) {
  DCHECK_EQ(State::NotStarted, state_);
  state_ = State::Running;

  if (has_wall_time())
    start_time_ = now->Now();
  if (has_thread_time())
    start_thread_time_ = base::ThreadTicks::Now();
}

void TaskQueue::TaskTiming::RecordTaskEnd(LazyNow* now) {
  DCHECK(state_ == State::Running || state_ == State::Finished);
  if (state_ == State::Finished)
    return;
  state_ = State::Finished;

  if (has_wall_time())
    end_time_ = now->Now();
  if (has_thread_time())
    end_thread_time_ = base::ThreadTicks::Now();
}

void TaskQueue::ShutdownTaskQueue() {
  DCHECK_CALLED_ON_VALID_THREAD(associated_thread_->thread_checker);
  if (!impl_)
    return;
  if (!sequence_manager_) {
    TakeTaskQueueImpl().reset();
    return;
  }
  impl_->SetBlameContext(nullptr);
  impl_->SetOnTaskStartedHandler(
      internal::TaskQueueImpl::OnTaskStartedHandler());
  impl_->SetOnTaskCompletedHandler(
      internal::TaskQueueImpl::OnTaskCompletedHandler());
  sequence_manager_->UnregisterTaskQueueImpl(TakeTaskQueueImpl());
}

scoped_refptr<SingleThreadTaskRunner> TaskQueue::CreateTaskRunner(
    TaskType task_type) {
  // We only need to lock if we're not on the main thread.
  base::internal::CheckedAutoLockMaybe lock(IsOnMainThread() ? &impl_lock_
                                                             : nullptr);
  if (!impl_)
    return CreateNullTaskRunner();
  return impl_->CreateTaskRunner(task_type);
}

std::unique_ptr<TaskQueue::QueueEnabledVoter>
TaskQueue::CreateQueueEnabledVoter() {
  DCHECK_CALLED_ON_VALID_THREAD(associated_thread_->thread_checker);
  if (!impl_)
    return nullptr;
  return WrapUnique(new QueueEnabledVoter(this));
}

bool TaskQueue::IsQueueEnabled() const {
  DCHECK_CALLED_ON_VALID_THREAD(associated_thread_->thread_checker);
  if (!impl_)
    return false;
  return impl_->IsQueueEnabled();
}

bool TaskQueue::IsEmpty() const {
  DCHECK_CALLED_ON_VALID_THREAD(associated_thread_->thread_checker);
  if (!impl_)
    return true;
  return impl_->IsEmpty();
}

size_t TaskQueue::GetNumberOfPendingTasks() const {
  DCHECK_CALLED_ON_VALID_THREAD(associated_thread_->thread_checker);
  if (!impl_)
    return 0;
  return impl_->GetNumberOfPendingTasks();
}

bool TaskQueue::HasTaskToRunImmediately() const {
  DCHECK_CALLED_ON_VALID_THREAD(associated_thread_->thread_checker);
  if (!impl_)
    return false;
  return impl_->HasTaskToRunImmediately();
}

Optional<TimeTicks> TaskQueue::GetNextScheduledWakeUp() {
  DCHECK_CALLED_ON_VALID_THREAD(associated_thread_->thread_checker);
  if (!impl_)
    return nullopt;
  return impl_->GetNextScheduledWakeUp();
}

void TaskQueue::SetQueuePriority(TaskQueue::QueuePriority priority) {
  DCHECK_CALLED_ON_VALID_THREAD(associated_thread_->thread_checker);
  if (!impl_)
    return;
  impl_->SetQueuePriority(priority);
}

TaskQueue::QueuePriority TaskQueue::GetQueuePriority() const {
  DCHECK_CALLED_ON_VALID_THREAD(associated_thread_->thread_checker);
  if (!impl_)
    return TaskQueue::QueuePriority::kLowPriority;
  return impl_->GetQueuePriority();
}

void TaskQueue::AddTaskObserver(TaskObserver* task_observer) {
  DCHECK_CALLED_ON_VALID_THREAD(associated_thread_->thread_checker);
  if (!impl_)
    return;
  impl_->AddTaskObserver(task_observer);
}

void TaskQueue::RemoveTaskObserver(TaskObserver* task_observer) {
  DCHECK_CALLED_ON_VALID_THREAD(associated_thread_->thread_checker);
  if (!impl_)
    return;
  impl_->RemoveTaskObserver(task_observer);
}

void TaskQueue::SetTimeDomain(TimeDomain* time_domain) {
  DCHECK_CALLED_ON_VALID_THREAD(associated_thread_->thread_checker);
  if (!impl_)
    return;
  impl_->SetTimeDomain(time_domain);
}

TimeDomain* TaskQueue::GetTimeDomain() const {
  DCHECK_CALLED_ON_VALID_THREAD(associated_thread_->thread_checker);
  if (!impl_)
    return nullptr;
  return impl_->GetTimeDomain();
}

void TaskQueue::SetBlameContext(trace_event::BlameContext* blame_context) {
  DCHECK_CALLED_ON_VALID_THREAD(associated_thread_->thread_checker);
  if (!impl_)
    return;
  impl_->SetBlameContext(blame_context);
}

void TaskQueue::InsertFence(InsertFencePosition position) {
  DCHECK_CALLED_ON_VALID_THREAD(associated_thread_->thread_checker);
  if (!impl_)
    return;
  impl_->InsertFence(position);
}

void TaskQueue::InsertFenceAt(TimeTicks time) {
  impl_->InsertFenceAt(time);
}

void TaskQueue::RemoveFence() {
  DCHECK_CALLED_ON_VALID_THREAD(associated_thread_->thread_checker);
  if (!impl_)
    return;
  impl_->RemoveFence();
}

bool TaskQueue::HasActiveFence() {
  DCHECK_CALLED_ON_VALID_THREAD(associated_thread_->thread_checker);
  if (!impl_)
    return false;
  return impl_->HasActiveFence();
}

bool TaskQueue::BlockedByFence() const {
  DCHECK_CALLED_ON_VALID_THREAD(associated_thread_->thread_checker);
  if (!impl_)
    return false;
  return impl_->BlockedByFence();
}

EnqueueOrder TaskQueue::GetEnqueueOrderAtWhichWeBecameUnblocked() const {
  DCHECK_CALLED_ON_VALID_THREAD(associated_thread_->thread_checker);
  if (!impl_)
    return EnqueueOrder();
  return impl_->GetEnqueueOrderAtWhichWeBecameUnblocked();
}

const char* TaskQueue::GetName() const {
  return name_;
}

void TaskQueue::SetObserver(Observer* observer) {
  DCHECK_CALLED_ON_VALID_THREAD(associated_thread_->thread_checker);
  if (!impl_)
    return;

  // Observer is guaranteed to outlive TaskQueue and TaskQueueImpl lifecycle is
  // controlled by |this|.
  impl_->SetObserver(observer);
}

void TaskQueue::SetShouldReportPostedTasksWhenDisabled(bool should_report) {
  impl_->SetShouldReportPostedTasksWhenDisabled(should_report);
}

bool TaskQueue::IsOnMainThread() const {
  return associated_thread_->IsBoundToCurrentThread();
}

std::unique_ptr<internal::TaskQueueImpl> TaskQueue::TakeTaskQueueImpl() {
  base::internal::CheckedAutoLock lock(impl_lock_);
  DCHECK(impl_);
  return std::move(impl_);
}

}  // namespace sequence_manager
}  // namespace base
