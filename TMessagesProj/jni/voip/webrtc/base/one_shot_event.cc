// Copyright 2013 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

#include "base/one_shot_event.h"

#include <stddef.h>

#include "base/callback.h"
#include "base/location.h"
#include "base/single_thread_task_runner.h"
#include "base/task_runner.h"
#include "base/threading/thread_task_runner_handle.h"
#include "base/time/time.h"

namespace base {

struct OneShotEvent::TaskInfo {
  TaskInfo() {}
  TaskInfo(const Location& from_here,
           const scoped_refptr<SingleThreadTaskRunner>& runner,
           OnceClosure task,
           const TimeDelta& delay)
      : from_here(from_here),
        runner(runner),
        task(std::move(task)),
        delay(delay) {
    CHECK(runner.get());  // Detect mistakes with a decent stack frame.
  }
  TaskInfo(TaskInfo&&) = default;

  Location from_here;
  scoped_refptr<SingleThreadTaskRunner> runner;
  OnceClosure task;
  TimeDelta delay;

 private:
  DISALLOW_COPY_AND_ASSIGN(TaskInfo);
};

OneShotEvent::OneShotEvent() : signaled_(false) {
  // It's acceptable to construct the OneShotEvent on one thread, but
  // immediately move it to another thread.
  thread_checker_.DetachFromThread();
}
OneShotEvent::OneShotEvent(bool signaled) : signaled_(signaled) {
  thread_checker_.DetachFromThread();
}
OneShotEvent::~OneShotEvent() {}

void OneShotEvent::Post(const Location& from_here, OnceClosure task) const {
  PostImpl(from_here, std::move(task), ThreadTaskRunnerHandle::Get(),
           TimeDelta());
}

void OneShotEvent::Post(
    const Location& from_here,
    OnceClosure task,
    const scoped_refptr<SingleThreadTaskRunner>& runner) const {
  PostImpl(from_here, std::move(task), runner, TimeDelta());
}

void OneShotEvent::PostDelayed(const Location& from_here,
                               OnceClosure task,
                               const TimeDelta& delay) const {
  PostImpl(from_here, std::move(task), ThreadTaskRunnerHandle::Get(), delay);
}

void OneShotEvent::Signal() {
  DCHECK(thread_checker_.CalledOnValidThread());

  CHECK(!signaled_) << "Only call Signal once.";

  signaled_ = true;
  // After this point, a call to Post() from one of the queued tasks
  // could proceed immediately, but the fact that this object is
  // single-threaded prevents that from being relevant.

  // Move tasks to a temporary to ensure no new ones are added.
  std::vector<TaskInfo> moved_tasks;
  std::swap(moved_tasks, tasks_);

  // We could randomize tasks in debug mode in order to check that
  // the order doesn't matter...
  for (TaskInfo& task : moved_tasks) {
    if (task.delay.is_zero())
      task.runner->PostTask(task.from_here, std::move(task.task));
    else
      task.runner->PostDelayedTask(task.from_here, std::move(task.task),
                                   task.delay);
  }
  DCHECK(tasks_.empty()) << "No new tasks should be added during task running!";
}

void OneShotEvent::PostImpl(const Location& from_here,
                            OnceClosure task,
                            const scoped_refptr<SingleThreadTaskRunner>& runner,
                            const TimeDelta& delay) const {
  DCHECK(thread_checker_.CalledOnValidThread());

  if (is_signaled()) {
    if (delay.is_zero())
      runner->PostTask(from_here, std::move(task));
    else
      runner->PostDelayedTask(from_here, std::move(task), delay);
  } else {
    tasks_.emplace_back(from_here, runner, std::move(task), delay);
  }
}

}  // namespace base
