// Copyright 2018 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

#ifndef BASE_TASK_TASK_EXECUTOR_H_
#define BASE_TASK_TASK_EXECUTOR_H_

#include <stdint.h>

#include "base/base_export.h"
#include "base/memory/ref_counted.h"
#include "base/sequenced_task_runner.h"
#include "base/single_thread_task_runner.h"
#include "base/task/single_thread_task_runner_thread_mode.h"
#include "base/task_runner.h"
#include "build/build_config.h"

namespace base {

class Location;
class TaskTraits;

// A TaskExecutor can execute Tasks with a specific TaskTraits extension id. To
// handle Tasks posted via the //base/task/post_task.h API, the TaskExecutor
// should be registered by calling RegisterTaskExecutor().
class BASE_EXPORT TaskExecutor {
 public:
  virtual ~TaskExecutor() = default;

  // Posts |task| with a |delay| and specific |traits|. |delay| can be zero. For
  // one off tasks that don't require a TaskRunner. Returns false if the task
  // definitely won't run because of current shutdown state.
  virtual bool PostDelayedTask(const Location& from_here,
                               const TaskTraits& traits,
                               OnceClosure task,
                               TimeDelta delay) = 0;

  // Returns a TaskRunner whose PostTask invocations result in scheduling tasks
  // using |traits|. Tasks may run in any order and in parallel.
  virtual scoped_refptr<TaskRunner> CreateTaskRunner(
      const TaskTraits& traits) = 0;

  // Returns a SequencedTaskRunner whose PostTask invocations result in
  // scheduling tasks using |traits|. Tasks run one at a time in posting order.
  virtual scoped_refptr<SequencedTaskRunner> CreateSequencedTaskRunner(
      const TaskTraits& traits) = 0;

  // Returns a SingleThreadTaskRunner whose PostTask invocations result in
  // scheduling tasks using |traits|. Tasks run on a single thread in posting
  // order. If |traits| identifies an existing thread,
  // SingleThreadTaskRunnerThreadMode::SHARED must be used.
  virtual scoped_refptr<SingleThreadTaskRunner> CreateSingleThreadTaskRunner(
      const TaskTraits& traits,
      SingleThreadTaskRunnerThreadMode thread_mode) = 0;

#if defined(OS_WIN)
  // Returns a SingleThreadTaskRunner whose PostTask invocations result in
  // scheduling tasks using |traits| in a COM Single-Threaded Apartment. Tasks
  // run in the same Single-Threaded Apartment in posting order for the returned
  // SingleThreadTaskRunner. If |traits| identifies an existing thread,
  // SingleThreadTaskRunnerThreadMode::SHARED must be used.
  virtual scoped_refptr<SingleThreadTaskRunner> CreateCOMSTATaskRunner(
      const TaskTraits& traits,
      SingleThreadTaskRunnerThreadMode thread_mode) = 0;
#endif  // defined(OS_WIN)
};

// Register a TaskExecutor with the //base/task/post_task.h API in the current
// process for tasks subsequently posted with a TaskTraits extension with the
// given |extension_id|. All executors need to be registered before any tasks
// are posted with |extension_id|. Only one executor per |extension_id| is
// supported.
void BASE_EXPORT RegisterTaskExecutor(uint8_t extension_id,
                                      TaskExecutor* task_executor);
void BASE_EXPORT UnregisterTaskExecutorForTesting(uint8_t extension_id);

// Stores the provided TaskExecutor in TLS for the current thread, to be used by
// tasks with the CurrentThread() trait.
void BASE_EXPORT SetTaskExecutorForCurrentThread(TaskExecutor* task_executor);

// Returns the task executor registered for the current thread.
BASE_EXPORT TaskExecutor* GetTaskExecutorForCurrentThread();

// Determines whether a registered TaskExecutor will handle tasks with the given
// |traits| and, if so, returns a pointer to it. Otherwise, returns |nullptr|.
TaskExecutor* GetRegisteredTaskExecutorForTraits(const TaskTraits& traits);

}  // namespace base

#endif  // BASE_TASK_TASK_EXECUTOR_H_
