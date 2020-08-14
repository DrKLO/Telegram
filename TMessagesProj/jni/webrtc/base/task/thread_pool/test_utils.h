// Copyright 2016 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

#ifndef BASE_TASK_THREAD_POOL_TEST_UTILS_H_
#define BASE_TASK_THREAD_POOL_TEST_UTILS_H_

#include <atomic>

#include "base/callback.h"
#include "base/task/common/checked_lock.h"
#include "base/task/post_job.h"
#include "base/task/task_features.h"
#include "base/task/task_traits.h"
#include "base/task/thread_pool/delayed_task_manager.h"
#include "base/task/thread_pool/pooled_task_runner_delegate.h"
#include "base/task/thread_pool/sequence.h"
#include "base/task/thread_pool/task_tracker.h"
#include "base/task/thread_pool/thread_group.h"
#include "base/task/thread_pool/worker_thread_observer.h"
#include "base/task_runner.h"
#include "base/thread_annotations.h"
#include "build/build_config.h"
#include "testing/gmock/include/gmock/gmock.h"

namespace base {
namespace internal {

struct Task;

namespace test {

class MockWorkerThreadObserver : public WorkerThreadObserver {
 public:
  MockWorkerThreadObserver();
  ~MockWorkerThreadObserver();

  void AllowCallsOnMainExit(int num_calls);
  void WaitCallsOnMainExit();

  // WorkerThreadObserver:
  MOCK_METHOD0(OnWorkerThreadMainEntry, void());
  // This doesn't use MOCK_METHOD0 because some tests need to wait for all calls
  // to happen, which isn't possible with gmock.
  void OnWorkerThreadMainExit() override;

 private:
  CheckedLock lock_;
  std::unique_ptr<ConditionVariable> on_main_exit_cv_ GUARDED_BY(lock_);
  int allowed_calls_on_main_exit_ GUARDED_BY(lock_) = 0;

  DISALLOW_COPY_AND_ASSIGN(MockWorkerThreadObserver);
};

class MockPooledTaskRunnerDelegate : public PooledTaskRunnerDelegate {
 public:
  MockPooledTaskRunnerDelegate(TrackedRef<TaskTracker> task_tracker,
                               DelayedTaskManager* delayed_task_manager);
  ~MockPooledTaskRunnerDelegate() override;

  // PooledTaskRunnerDelegate:
  bool PostTaskWithSequence(Task task,
                            scoped_refptr<Sequence> sequence) override;
  bool EnqueueJobTaskSource(scoped_refptr<JobTaskSource> task_source) override;
  void RemoveJobTaskSource(scoped_refptr<JobTaskSource> task_source) override;
  bool ShouldYield(const TaskSource* task_source) const override;
  void UpdatePriority(scoped_refptr<TaskSource> task_source,
                      TaskPriority priority) override;

  void SetThreadGroup(ThreadGroup* thread_group);

  void PostTaskWithSequenceNow(Task task, scoped_refptr<Sequence> sequence);

 private:
  const TrackedRef<TaskTracker> task_tracker_;
  DelayedTaskManager* const delayed_task_manager_;
  ThreadGroup* thread_group_ = nullptr;
};

// A simple MockJobTask that will give |worker_task| a fixed number of times,
// possibly in parallel.
class MockJobTask : public base::RefCountedThreadSafe<MockJobTask> {
 public:
  // Gives |worker_task| to requesting workers |num_tasks_to_run| times.
  MockJobTask(base::RepeatingCallback<void(JobDelegate*)> worker_task,
              size_t num_tasks_to_run);

  // Gives |worker_task| to a single requesting worker.
  MockJobTask(base::OnceClosure worker_task);

  // Updates the remaining number of time |worker_task| runs to
  // |num_tasks_to_run|.
  void SetNumTasksToRun(size_t num_tasks_to_run) {
    remaining_num_tasks_to_run_ = num_tasks_to_run;
  }

  size_t GetMaxConcurrency() const;
  void Run(JobDelegate* delegate);

  scoped_refptr<JobTaskSource> GetJobTaskSource(
      const Location& from_here,
      const TaskTraits& traits,
      PooledTaskRunnerDelegate* delegate);

 private:
  friend class base::RefCountedThreadSafe<MockJobTask>;

  ~MockJobTask();

  base::RepeatingCallback<void(JobDelegate*)> worker_task_;
  std::atomic_size_t remaining_num_tasks_to_run_;

  DISALLOW_COPY_AND_ASSIGN(MockJobTask);
};

// An enumeration of possible thread pool types. Used to parametrize relevant
// thread_pool tests.
enum class PoolType {
  GENERIC,
#if HAS_NATIVE_THREAD_POOL()
  NATIVE,
#endif
};

// Creates a Sequence with given |traits| and pushes |task| to it. If a
// TaskRunner is associated with |task|, it should be be passed as |task_runner|
// along with its |execution_mode|. Returns the created Sequence.
scoped_refptr<Sequence> CreateSequenceWithTask(
    Task task,
    const TaskTraits& traits,
    scoped_refptr<TaskRunner> task_runner = nullptr,
    TaskSourceExecutionMode execution_mode =
        TaskSourceExecutionMode::kParallel);

// Creates a TaskRunner that posts tasks to the thread group owned by
// |pooled_task_runner_delegate| with the |execution_mode|.
// Caveat: this does not support TaskSourceExecutionMode::kSingleThread.
scoped_refptr<TaskRunner> CreatePooledTaskRunnerWithExecutionMode(
    TaskSourceExecutionMode execution_mode,
    MockPooledTaskRunnerDelegate* mock_pooled_task_runner_delegate,
    const TaskTraits& traits = {});

scoped_refptr<TaskRunner> CreatePooledTaskRunner(
    const TaskTraits& traits,
    MockPooledTaskRunnerDelegate* mock_pooled_task_runner_delegate);

scoped_refptr<SequencedTaskRunner> CreatePooledSequencedTaskRunner(
    const TaskTraits& traits,
    MockPooledTaskRunnerDelegate* mock_pooled_task_runner_delegate);

RegisteredTaskSource QueueAndRunTaskSource(
    TaskTracker* task_tracker,
    scoped_refptr<TaskSource> task_source);

// Calls StartShutdown() and CompleteShutdown() on |task_tracker|.
void ShutdownTaskTracker(TaskTracker* task_tracker);

}  // namespace test
}  // namespace internal
}  // namespace base

#endif  // BASE_TASK_THREAD_POOL_TEST_UTILS_H_
