// Copyright 2016 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

#include "base/task/thread_pool/delayed_task_manager.h"

#include <algorithm>

#include "base/bind.h"
#include "base/logging.h"
#include "base/sequenced_task_runner.h"
#include "base/task/post_task.h"
#include "base/task/thread_pool/task.h"
#include "base/task_runner.h"

namespace base {
namespace internal {

DelayedTaskManager::DelayedTask::DelayedTask() = default;

DelayedTaskManager::DelayedTask::DelayedTask(
    Task task,
    PostTaskNowCallback callback,
    scoped_refptr<TaskRunner> task_runner)
    : task(std::move(task)),
      callback(std::move(callback)),
      task_runner(std::move(task_runner)) {}

DelayedTaskManager::DelayedTask::DelayedTask(
    DelayedTaskManager::DelayedTask&& other) = default;

DelayedTaskManager::DelayedTask::~DelayedTask() = default;

DelayedTaskManager::DelayedTask& DelayedTaskManager::DelayedTask::operator=(
    DelayedTaskManager::DelayedTask&& other) = default;

bool DelayedTaskManager::DelayedTask::operator<=(
    const DelayedTask& other) const {
  if (task.delayed_run_time == other.task.delayed_run_time) {
    return task.sequence_num <= other.task.sequence_num;
  }
  return task.delayed_run_time < other.task.delayed_run_time;
}

bool DelayedTaskManager::DelayedTask::IsScheduled() const {
  return scheduled_;
}
void DelayedTaskManager::DelayedTask::SetScheduled() {
  DCHECK(!scheduled_);
  scheduled_ = true;
}

DelayedTaskManager::DelayedTaskManager(const TickClock* tick_clock)
    : process_ripe_tasks_closure_(
          BindRepeating(&DelayedTaskManager::ProcessRipeTasks,
                        Unretained(this))),
      tick_clock_(tick_clock) {
  DCHECK(tick_clock_);
}

DelayedTaskManager::~DelayedTaskManager() = default;

void DelayedTaskManager::Start(
    scoped_refptr<SequencedTaskRunner> service_thread_task_runner) {
  DCHECK(service_thread_task_runner);

  TimeTicks process_ripe_tasks_time;
  {
    CheckedAutoLock auto_lock(queue_lock_);
    DCHECK(!service_thread_task_runner_);
    service_thread_task_runner_ = std::move(service_thread_task_runner);
    process_ripe_tasks_time = GetTimeToScheduleProcessRipeTasksLockRequired();
  }
  ScheduleProcessRipeTasksOnServiceThread(process_ripe_tasks_time);
}

void DelayedTaskManager::AddDelayedTask(
    Task task,
    PostTaskNowCallback post_task_now_callback,
    scoped_refptr<TaskRunner> task_runner) {
  DCHECK(task.task);
  DCHECK(!task.delayed_run_time.is_null());

  // Use CHECK instead of DCHECK to crash earlier. See http://crbug.com/711167
  // for details.
  CHECK(task.task);
  TimeTicks process_ripe_tasks_time;
  {
    CheckedAutoLock auto_lock(queue_lock_);
    delayed_task_queue_.insert(DelayedTask(std::move(task),
                                           std::move(post_task_now_callback),
                                           std::move(task_runner)));
    // Not started yet.
    if (service_thread_task_runner_ == nullptr)
      return;
    process_ripe_tasks_time = GetTimeToScheduleProcessRipeTasksLockRequired();
  }
  ScheduleProcessRipeTasksOnServiceThread(process_ripe_tasks_time);
}

void DelayedTaskManager::ProcessRipeTasks() {
  std::vector<DelayedTask> ripe_delayed_tasks;
  TimeTicks process_ripe_tasks_time;

  {
    CheckedAutoLock auto_lock(queue_lock_);
    const TimeTicks now = tick_clock_->NowTicks();
    while (!delayed_task_queue_.empty() &&
           delayed_task_queue_.Min().task.delayed_run_time <= now) {
      // The const_cast on top is okay since the DelayedTask is
      // transactionally being popped from |delayed_task_queue_| right after
      // and the move doesn't alter the sort order.
      ripe_delayed_tasks.push_back(
          std::move(const_cast<DelayedTask&>(delayed_task_queue_.Min())));
      delayed_task_queue_.Pop();
    }
    process_ripe_tasks_time = GetTimeToScheduleProcessRipeTasksLockRequired();
  }
  ScheduleProcessRipeTasksOnServiceThread(process_ripe_tasks_time);

  for (auto& delayed_task : ripe_delayed_tasks) {
    std::move(delayed_task.callback).Run(std::move(delayed_task.task));
  }
}

Optional<TimeTicks> DelayedTaskManager::NextScheduledRunTime() const {
  CheckedAutoLock auto_lock(queue_lock_);
  if (delayed_task_queue_.empty())
    return nullopt;
  return delayed_task_queue_.Min().task.delayed_run_time;
}

TimeTicks DelayedTaskManager::GetTimeToScheduleProcessRipeTasksLockRequired() {
  queue_lock_.AssertAcquired();
  if (delayed_task_queue_.empty())
    return TimeTicks::Max();
  // The const_cast on top is okay since |IsScheduled()| and |SetScheduled()|
  // don't alter the sort order.
  DelayedTask& ripest_delayed_task =
      const_cast<DelayedTask&>(delayed_task_queue_.Min());
  if (ripest_delayed_task.IsScheduled())
    return TimeTicks::Max();
  ripest_delayed_task.SetScheduled();
  return ripest_delayed_task.task.delayed_run_time;
}

void DelayedTaskManager::ScheduleProcessRipeTasksOnServiceThread(
    TimeTicks next_delayed_task_run_time) {
  DCHECK(!next_delayed_task_run_time.is_null());
  if (next_delayed_task_run_time.is_max())
    return;
  const TimeTicks now = tick_clock_->NowTicks();
  TimeDelta delay = std::max(TimeDelta(), next_delayed_task_run_time - now);
  service_thread_task_runner_->PostDelayedTask(
      FROM_HERE, process_ripe_tasks_closure_, delay);
}

}  // namespace internal
}  // namespace base
