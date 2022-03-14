/*
 *  Copyright 2018 The WebRTC Project Authors. All rights reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "rtc_base/task_queue_stdlib.h"

#include <string.h>

#include <algorithm>
#include <map>
#include <memory>
#include <queue>
#include <utility>

#include "absl/strings/string_view.h"
#include "api/task_queue/queued_task.h"
#include "api/task_queue/task_queue_base.h"
#include "rtc_base/checks.h"
#include "rtc_base/event.h"
#include "rtc_base/logging.h"
#include "rtc_base/platform_thread.h"
#include "rtc_base/synchronization/mutex.h"
#include "rtc_base/thread_annotations.h"
#include "rtc_base/time_utils.h"

namespace webrtc {
namespace {

rtc::ThreadPriority TaskQueuePriorityToThreadPriority(
    TaskQueueFactory::Priority priority) {
  switch (priority) {
    case TaskQueueFactory::Priority::HIGH:
      return rtc::ThreadPriority::kRealtime;
    case TaskQueueFactory::Priority::LOW:
      return rtc::ThreadPriority::kLow;
    case TaskQueueFactory::Priority::NORMAL:
      return rtc::ThreadPriority::kNormal;
  }
}

class TaskQueueStdlib final : public TaskQueueBase {
 public:
  TaskQueueStdlib(absl::string_view queue_name, rtc::ThreadPriority priority);
  ~TaskQueueStdlib() override = default;

  void Delete() override;
  void PostTask(std::unique_ptr<QueuedTask> task) override;
  void PostDelayedTask(std::unique_ptr<QueuedTask> task,
                       uint32_t milliseconds) override;

 private:
  using OrderId = uint64_t;

  struct DelayedEntryTimeout {
    int64_t next_fire_at_ms_{};
    OrderId order_{};

    bool operator<(const DelayedEntryTimeout& o) const {
      return std::tie(next_fire_at_ms_, order_) <
             std::tie(o.next_fire_at_ms_, o.order_);
    }
  };

  struct NextTask {
    bool final_task_{false};
    std::unique_ptr<QueuedTask> run_task_;
    int64_t sleep_time_ms_{};
  };

  NextTask GetNextTask();

  void ProcessTasks();

  void NotifyWake();

  // Indicates if the thread has started.
  rtc::Event started_;

  // Signaled whenever a new task is pending.
  rtc::Event flag_notify_;

  Mutex pending_lock_;

  // Indicates if the worker thread needs to shutdown now.
  bool thread_should_quit_ RTC_GUARDED_BY(pending_lock_){false};

  // Holds the next order to use for the next task to be
  // put into one of the pending queues.
  OrderId thread_posting_order_ RTC_GUARDED_BY(pending_lock_){};

  // The list of all pending tasks that need to be processed in the
  // FIFO queue ordering on the worker thread.
  std::queue<std::pair<OrderId, std::unique_ptr<QueuedTask>>> pending_queue_
      RTC_GUARDED_BY(pending_lock_);

  // The list of all pending tasks that need to be processed at a future
  // time based upon a delay. On the off change the delayed task should
  // happen at exactly the same time interval as another task then the
  // task is processed based on FIFO ordering. std::priority_queue was
  // considered but rejected due to its inability to extract the
  // std::unique_ptr out of the queue without the presence of a hack.
  std::map<DelayedEntryTimeout, std::unique_ptr<QueuedTask>> delayed_queue_
      RTC_GUARDED_BY(pending_lock_);

  // Contains the active worker thread assigned to processing
  // tasks (including delayed tasks).
  // Placing this last ensures the thread doesn't touch uninitialized attributes
  // throughout it's lifetime.
  rtc::PlatformThread thread_;
};

TaskQueueStdlib::TaskQueueStdlib(absl::string_view queue_name,
                                 rtc::ThreadPriority priority)
    : started_(/*manual_reset=*/false, /*initially_signaled=*/false),
      flag_notify_(/*manual_reset=*/false, /*initially_signaled=*/false),
      thread_(rtc::PlatformThread::SpawnJoinable(
          [this] {
            CurrentTaskQueueSetter set_current(this);
            ProcessTasks();
          },
          queue_name,
          rtc::ThreadAttributes().SetPriority(priority))) {
  started_.Wait(rtc::Event::kForever);
}

void TaskQueueStdlib::Delete() {
  RTC_DCHECK(!IsCurrent());

  {
    MutexLock lock(&pending_lock_);
    thread_should_quit_ = true;
  }

  NotifyWake();

  delete this;
}

void TaskQueueStdlib::PostTask(std::unique_ptr<QueuedTask> task) {
  {
    MutexLock lock(&pending_lock_);
    OrderId order = thread_posting_order_++;

    pending_queue_.push(std::pair<OrderId, std::unique_ptr<QueuedTask>>(
        order, std::move(task)));
  }

  NotifyWake();
}

void TaskQueueStdlib::PostDelayedTask(std::unique_ptr<QueuedTask> task,
                                      uint32_t milliseconds) {
  auto fire_at = rtc::TimeMillis() + milliseconds;

  DelayedEntryTimeout delay;
  delay.next_fire_at_ms_ = fire_at;

  {
    MutexLock lock(&pending_lock_);
    delay.order_ = ++thread_posting_order_;
    delayed_queue_[delay] = std::move(task);
  }

  NotifyWake();
}

TaskQueueStdlib::NextTask TaskQueueStdlib::GetNextTask() {
  NextTask result{};

  auto tick = rtc::TimeMillis();

  MutexLock lock(&pending_lock_);

  if (thread_should_quit_) {
    result.final_task_ = true;
    return result;
  }

  if (delayed_queue_.size() > 0) {
    auto delayed_entry = delayed_queue_.begin();
    const auto& delay_info = delayed_entry->first;
    auto& delay_run = delayed_entry->second;
    if (tick >= delay_info.next_fire_at_ms_) {
      if (pending_queue_.size() > 0) {
        auto& entry = pending_queue_.front();
        auto& entry_order = entry.first;
        auto& entry_run = entry.second;
        if (entry_order < delay_info.order_) {
          result.run_task_ = std::move(entry_run);
          pending_queue_.pop();
          return result;
        }
      }

      result.run_task_ = std::move(delay_run);
      delayed_queue_.erase(delayed_entry);
      return result;
    }

    result.sleep_time_ms_ = delay_info.next_fire_at_ms_ - tick;
  }

  if (pending_queue_.size() > 0) {
    auto& entry = pending_queue_.front();
    result.run_task_ = std::move(entry.second);
    pending_queue_.pop();
  }

  return result;
}

void TaskQueueStdlib::ProcessTasks() {
  started_.Set();

  while (true) {
    auto task = GetNextTask();

    if (task.final_task_)
      break;

    if (task.run_task_) {
      // process entry immediately then try again
      QueuedTask* release_ptr = task.run_task_.release();
      if (release_ptr->Run())
        delete release_ptr;

      // attempt to sleep again
      continue;
    }

    if (0 == task.sleep_time_ms_)
      flag_notify_.Wait(rtc::Event::kForever);
    else
      flag_notify_.Wait(task.sleep_time_ms_);
  }
}

void TaskQueueStdlib::NotifyWake() {
  // The queue holds pending tasks to complete. Either tasks are to be
  // executed immediately or tasks are to be run at some future delayed time.
  // For immediate tasks the task queue's thread is busy running the task and
  // the thread will not be waiting on the flag_notify_ event. If no immediate
  // tasks are available but a delayed task is pending then the thread will be
  // waiting on flag_notify_ with a delayed time-out of the nearest timed task
  // to run. If no immediate or pending tasks are available, the thread will
  // wait on flag_notify_ until signaled that a task has been added (or the
  // thread to be told to shutdown).

  // In all cases, when a new immediate task, delayed task, or request to
  // shutdown the thread is added the flag_notify_ is signaled after. If the
  // thread was waiting then the thread will wake up immediately and re-assess
  // what task needs to be run next (i.e. run a task now, wait for the nearest
  // timed delayed task, or shutdown the thread). If the thread was not waiting
  // then the thread will remained signaled to wake up the next time any
  // attempt to wait on the flag_notify_ event occurs.

  // Any immediate or delayed pending task (or request to shutdown the thread)
  // must always be added to the queue prior to signaling flag_notify_ to wake
  // up the possibly sleeping thread. This prevents a race condition where the
  // thread is notified to wake up but the task queue's thread finds nothing to
  // do so it waits once again to be signaled where such a signal may never
  // happen.
  flag_notify_.Set();
}

class TaskQueueStdlibFactory final : public TaskQueueFactory {
 public:
  std::unique_ptr<TaskQueueBase, TaskQueueDeleter> CreateTaskQueue(
      absl::string_view name,
      Priority priority) const override {
    return std::unique_ptr<TaskQueueBase, TaskQueueDeleter>(
        new TaskQueueStdlib(name, TaskQueuePriorityToThreadPriority(priority)));
  }
};

}  // namespace

std::unique_ptr<TaskQueueFactory> CreateTaskQueueStdlibFactory() {
  return std::make_unique<TaskQueueStdlibFactory>();
}

}  // namespace webrtc
