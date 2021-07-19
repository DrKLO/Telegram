// Copyright 2017 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

#include "base/task/thread_pool/test_utils.h"

#include <utility>

#include "base/bind.h"
#include "base/synchronization/condition_variable.h"
#include "base/task/thread_pool/pooled_parallel_task_runner.h"
#include "base/task/thread_pool/pooled_sequenced_task_runner.h"
#include "base/test/bind_test_util.h"
#include "base/threading/scoped_blocking_call_internal.h"
#include "base/threading/thread_restrictions.h"
#include "testing/gtest/include/gtest/gtest.h"

namespace base {
namespace internal {
namespace test {

namespace {

// A task runner that posts each task as a MockJobTaskSource that runs a single
// task. This is used to run ThreadGroupTests which require a TaskRunner with
// kJob execution mode. Delayed tasks are not supported.
class MockJobTaskRunner : public TaskRunner {
 public:
  MockJobTaskRunner(const TaskTraits& traits,
                    PooledTaskRunnerDelegate* pooled_task_runner_delegate)
      : traits_(traits),
        pooled_task_runner_delegate_(pooled_task_runner_delegate) {}

  // TaskRunner:
  bool PostDelayedTask(const Location& from_here,
                       OnceClosure closure,
                       TimeDelta delay) override;

 private:
  ~MockJobTaskRunner() override;

  const TaskTraits traits_;
  PooledTaskRunnerDelegate* const pooled_task_runner_delegate_;

  DISALLOW_COPY_AND_ASSIGN(MockJobTaskRunner);
};

bool MockJobTaskRunner::PostDelayedTask(const Location& from_here,
                                        OnceClosure closure,
                                        TimeDelta delay) {
  DCHECK_EQ(delay, TimeDelta());  // Jobs doesn't support delayed tasks.

  if (!PooledTaskRunnerDelegate::Exists())
    return false;

  auto job_task = base::MakeRefCounted<MockJobTask>(std::move(closure));
  scoped_refptr<JobTaskSource> task_source = job_task->GetJobTaskSource(
      from_here, traits_, pooled_task_runner_delegate_);
  return pooled_task_runner_delegate_->EnqueueJobTaskSource(
      std::move(task_source));
}

MockJobTaskRunner::~MockJobTaskRunner() = default;

scoped_refptr<TaskRunner> CreateJobTaskRunner(
    const TaskTraits& traits,
    MockPooledTaskRunnerDelegate* mock_pooled_task_runner_delegate) {
  return MakeRefCounted<MockJobTaskRunner>(traits,
                                           mock_pooled_task_runner_delegate);
}

}  // namespace

MockWorkerThreadObserver::MockWorkerThreadObserver()
    : on_main_exit_cv_(lock_.CreateConditionVariable()) {}

MockWorkerThreadObserver::~MockWorkerThreadObserver() {
  WaitCallsOnMainExit();
}

void MockWorkerThreadObserver::AllowCallsOnMainExit(int num_calls) {
  CheckedAutoLock auto_lock(lock_);
  EXPECT_EQ(0, allowed_calls_on_main_exit_);
  allowed_calls_on_main_exit_ = num_calls;
}

void MockWorkerThreadObserver::WaitCallsOnMainExit() {
  CheckedAutoLock auto_lock(lock_);
  while (allowed_calls_on_main_exit_ != 0)
    on_main_exit_cv_->Wait();
}

void MockWorkerThreadObserver::OnWorkerThreadMainExit() {
  CheckedAutoLock auto_lock(lock_);
  EXPECT_GE(allowed_calls_on_main_exit_, 0);
  --allowed_calls_on_main_exit_;
  if (allowed_calls_on_main_exit_ == 0)
    on_main_exit_cv_->Signal();
}

scoped_refptr<Sequence> CreateSequenceWithTask(
    Task task,
    const TaskTraits& traits,
    scoped_refptr<TaskRunner> task_runner,
    TaskSourceExecutionMode execution_mode) {
  scoped_refptr<Sequence> sequence =
      MakeRefCounted<Sequence>(traits, task_runner.get(), execution_mode);
  sequence->BeginTransaction().PushTask(std::move(task));
  return sequence;
}

scoped_refptr<TaskRunner> CreatePooledTaskRunnerWithExecutionMode(
    TaskSourceExecutionMode execution_mode,
    MockPooledTaskRunnerDelegate* mock_pooled_task_runner_delegate,
    const TaskTraits& traits) {
  switch (execution_mode) {
    case TaskSourceExecutionMode::kParallel:
      return CreatePooledTaskRunner(traits, mock_pooled_task_runner_delegate);
    case TaskSourceExecutionMode::kSequenced:
      return CreatePooledSequencedTaskRunner(traits,
                                             mock_pooled_task_runner_delegate);
    case TaskSourceExecutionMode::kJob:
      return CreateJobTaskRunner(traits, mock_pooled_task_runner_delegate);
    default:
      // Fall through.
      break;
  }
  ADD_FAILURE() << "Unexpected ExecutionMode";
  return nullptr;
}

scoped_refptr<TaskRunner> CreatePooledTaskRunner(
    const TaskTraits& traits,
    MockPooledTaskRunnerDelegate* mock_pooled_task_runner_delegate) {
  return MakeRefCounted<PooledParallelTaskRunner>(
      traits, mock_pooled_task_runner_delegate);
}

scoped_refptr<SequencedTaskRunner> CreatePooledSequencedTaskRunner(
    const TaskTraits& traits,
    MockPooledTaskRunnerDelegate* mock_pooled_task_runner_delegate) {
  return MakeRefCounted<PooledSequencedTaskRunner>(
      traits, mock_pooled_task_runner_delegate);
}

MockPooledTaskRunnerDelegate::MockPooledTaskRunnerDelegate(
    TrackedRef<TaskTracker> task_tracker,
    DelayedTaskManager* delayed_task_manager)
    : task_tracker_(task_tracker),
      delayed_task_manager_(delayed_task_manager) {}

MockPooledTaskRunnerDelegate::~MockPooledTaskRunnerDelegate() = default;

bool MockPooledTaskRunnerDelegate::PostTaskWithSequence(
    Task task,
    scoped_refptr<Sequence> sequence) {
  // |thread_group_| must be initialized with SetThreadGroup() before
  // proceeding.
  DCHECK(thread_group_);
  DCHECK(task.task);
  DCHECK(sequence);

  if (!task_tracker_->WillPostTask(&task, sequence->shutdown_behavior()))
    return false;

  if (task.delayed_run_time.is_null()) {
    PostTaskWithSequenceNow(std::move(task), std::move(sequence));
  } else {
    // It's safe to take a ref on this pointer since the caller must have a ref
    // to the TaskRunner in order to post.
    scoped_refptr<TaskRunner> task_runner = sequence->task_runner();
    delayed_task_manager_->AddDelayedTask(
        std::move(task),
        BindOnce(
            [](scoped_refptr<Sequence> sequence,
               MockPooledTaskRunnerDelegate* self, Task task) {
              self->PostTaskWithSequenceNow(std::move(task),
                                            std::move(sequence));
            },
            std::move(sequence), Unretained(this)),
        std::move(task_runner));
  }

  return true;
}

void MockPooledTaskRunnerDelegate::PostTaskWithSequenceNow(
    Task task,
    scoped_refptr<Sequence> sequence) {
  auto transaction = sequence->BeginTransaction();
  const bool sequence_should_be_queued = transaction.WillPushTask();
  RegisteredTaskSource task_source;
  if (sequence_should_be_queued) {
    task_source = task_tracker_->RegisterTaskSource(std::move(sequence));
    // We shouldn't push |task| if we're not allowed to queue |task_source|.
    if (!task_source)
      return;
  }
  transaction.PushTask(std::move(task));
  if (task_source) {
    thread_group_->PushTaskSourceAndWakeUpWorkers(
        {std::move(task_source), std::move(transaction)});
  }
}

bool MockPooledTaskRunnerDelegate::ShouldYield(
    const TaskSource* task_source) const {
  return thread_group_->ShouldYield(task_source->priority_racy());
}

bool MockPooledTaskRunnerDelegate::EnqueueJobTaskSource(
    scoped_refptr<JobTaskSource> task_source) {
  // |thread_group_| must be initialized with SetThreadGroup() before
  // proceeding.
  DCHECK(thread_group_);
  DCHECK(task_source);

  auto registered_task_source =
      task_tracker_->RegisterTaskSource(std::move(task_source));
  if (!registered_task_source)
    return false;
  auto transaction = registered_task_source->BeginTransaction();
  thread_group_->PushTaskSourceAndWakeUpWorkers(
      {std::move(registered_task_source), std::move(transaction)});
  return true;
}

void MockPooledTaskRunnerDelegate::RemoveJobTaskSource(
    scoped_refptr<JobTaskSource> task_source) {
  thread_group_->RemoveTaskSource(*task_source);
}

void MockPooledTaskRunnerDelegate::UpdatePriority(
    scoped_refptr<TaskSource> task_source,
    TaskPriority priority) {
  auto transaction = task_source->BeginTransaction();
  transaction.UpdatePriority(priority);
  thread_group_->UpdateSortKey(std::move(transaction));
}

void MockPooledTaskRunnerDelegate::SetThreadGroup(ThreadGroup* thread_group) {
  thread_group_ = thread_group;
}

MockJobTask::~MockJobTask() = default;

MockJobTask::MockJobTask(
    base::RepeatingCallback<void(JobDelegate*)> worker_task,
    size_t num_tasks_to_run)
    : worker_task_(std::move(worker_task)),
      remaining_num_tasks_to_run_(num_tasks_to_run) {}

MockJobTask::MockJobTask(base::OnceClosure worker_task)
    : worker_task_(base::BindRepeating(
          [](base::OnceClosure&& worker_task, JobDelegate*) mutable {
            std::move(worker_task).Run();
          },
          base::Passed(std::move(worker_task)))),
      remaining_num_tasks_to_run_(1) {}

size_t MockJobTask::GetMaxConcurrency() const {
  return remaining_num_tasks_to_run_.load();
}

void MockJobTask::Run(JobDelegate* delegate) {
  worker_task_.Run(delegate);
  size_t before = remaining_num_tasks_to_run_.fetch_sub(1);
  DCHECK_GT(before, 0U);
}

scoped_refptr<JobTaskSource> MockJobTask::GetJobTaskSource(
    const Location& from_here,
    const TaskTraits& traits,
    PooledTaskRunnerDelegate* delegate) {
  return MakeRefCounted<JobTaskSource>(
      from_here, traits, base::BindRepeating(&test::MockJobTask::Run, this),
      base::BindRepeating(&test::MockJobTask::GetMaxConcurrency, this),
      delegate);
}

RegisteredTaskSource QueueAndRunTaskSource(
    TaskTracker* task_tracker,
    scoped_refptr<TaskSource> task_source) {
  auto registered_task_source =
      task_tracker->RegisterTaskSource(std::move(task_source));
  EXPECT_TRUE(registered_task_source);
  EXPECT_NE(registered_task_source.WillRunTask(),
            TaskSource::RunStatus::kDisallowed);
  return task_tracker->RunAndPopNextTask(std::move(registered_task_source));
}

void ShutdownTaskTracker(TaskTracker* task_tracker) {
  task_tracker->StartShutdown();
  task_tracker->CompleteShutdown();
}

}  // namespace test
}  // namespace internal
}  // namespace base
