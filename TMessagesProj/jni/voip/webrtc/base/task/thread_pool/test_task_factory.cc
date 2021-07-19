// Copyright 2016 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

#include "base/task/thread_pool/test_task_factory.h"

#include "base/bind.h"
#include "base/bind_helpers.h"
#include "base/callback.h"
#include "base/location.h"
#include "base/logging.h"
#include "base/synchronization/waitable_event.h"
#include "base/threading/sequenced_task_runner_handle.h"
#include "base/threading/thread_task_runner_handle.h"
#include "testing/gtest/include/gtest/gtest.h"

namespace base {
namespace internal {
namespace test {

TestTaskFactory::TestTaskFactory(scoped_refptr<TaskRunner> task_runner,
                                 TaskSourceExecutionMode execution_mode)
    : cv_(&lock_),
      task_runner_(std::move(task_runner)),
      execution_mode_(execution_mode) {
  // Detach |thread_checker_| from the current thread. It will be attached to
  // the first thread that calls ThreadCheckerImpl::CalledOnValidThread().
  thread_checker_.DetachFromThread();
}

TestTaskFactory::~TestTaskFactory() {
  WaitForAllTasksToRun();
}

bool TestTaskFactory::PostTask(PostNestedTask post_nested_task,
                               OnceClosure after_task_closure) {
  AutoLock auto_lock(lock_);
  return task_runner_->PostTask(
      FROM_HERE, BindOnce(&TestTaskFactory::RunTaskCallback, Unretained(this),
                          num_posted_tasks_++, post_nested_task,
                          std::move(after_task_closure)));
}

void TestTaskFactory::WaitForAllTasksToRun() const {
  AutoLock auto_lock(lock_);
  while (ran_tasks_.size() < num_posted_tasks_)
    cv_.Wait();
}

void TestTaskFactory::RunTaskCallback(size_t task_index,
                                      PostNestedTask post_nested_task,
                                      OnceClosure after_task_closure) {
  if (post_nested_task == PostNestedTask::YES)
    PostTask(PostNestedTask::NO, OnceClosure());

  if (execution_mode_ == TaskSourceExecutionMode::kSingleThread ||
      execution_mode_ == TaskSourceExecutionMode::kSequenced) {
    EXPECT_TRUE(static_cast<SequencedTaskRunner*>(task_runner_.get())
                    ->RunsTasksInCurrentSequence());
  }

  // Verify TaskRunnerHandles are set as expected in the task's scope.
  switch (execution_mode_) {
    case TaskSourceExecutionMode::kJob:
    case TaskSourceExecutionMode::kParallel:
      EXPECT_FALSE(ThreadTaskRunnerHandle::IsSet());
      EXPECT_FALSE(SequencedTaskRunnerHandle::IsSet());
      break;
    case TaskSourceExecutionMode::kSequenced:
      EXPECT_FALSE(ThreadTaskRunnerHandle::IsSet());
      EXPECT_TRUE(SequencedTaskRunnerHandle::IsSet());
      EXPECT_EQ(task_runner_, SequencedTaskRunnerHandle::Get());
      break;
    case TaskSourceExecutionMode::kSingleThread:
      // SequencedTaskRunnerHandle inherits from ThreadTaskRunnerHandle so
      // both are expected to be "set" in the kSingleThread case.
      EXPECT_TRUE(ThreadTaskRunnerHandle::IsSet());
      EXPECT_TRUE(SequencedTaskRunnerHandle::IsSet());
      EXPECT_EQ(task_runner_, ThreadTaskRunnerHandle::Get());
      EXPECT_EQ(task_runner_, SequencedTaskRunnerHandle::Get());
      break;
  }

  {
    AutoLock auto_lock(lock_);

    DCHECK_LE(task_index, num_posted_tasks_);

    if ((execution_mode_ == TaskSourceExecutionMode::kSingleThread ||
         execution_mode_ == TaskSourceExecutionMode::kSequenced) &&
        task_index != ran_tasks_.size()) {
      ADD_FAILURE() << "A task didn't run in the expected order.";
    }

    if (execution_mode_ == TaskSourceExecutionMode::kSingleThread)
      EXPECT_TRUE(thread_checker_.CalledOnValidThread());

    if (ran_tasks_.find(task_index) != ran_tasks_.end())
      ADD_FAILURE() << "A task ran more than once.";
    ran_tasks_.insert(task_index);

    cv_.Signal();
  }

  if (!after_task_closure.is_null())
    std::move(after_task_closure).Run();
}

}  // namespace test
}  // namespace internal
}  // namespace base
