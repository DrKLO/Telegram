/*
 *  Copyright 2019 The WebRTC Project Authors. All rights reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */
#ifndef API_TASK_QUEUE_TASK_QUEUE_BASE_H_
#define API_TASK_QUEUE_TASK_QUEUE_BASE_H_

#include <memory>
#include <utility>

#include "api/task_queue/queued_task.h"
#include "rtc_base/system/rtc_export.h"
#include "rtc_base/thread_annotations.h"

namespace webrtc {

// Asynchronously executes tasks in a way that guarantees that they're executed
// in FIFO order and that tasks never overlap. Tasks may always execute on the
// same worker thread and they may not. To DCHECK that tasks are executing on a
// known task queue, use IsCurrent().
class RTC_LOCKABLE RTC_EXPORT TaskQueueBase {
 public:
  enum class DelayPrecision {
    // This may include up to a 17 ms leeway in addition to OS timer precision.
    // See PostDelayedTask() for more information.
    kLow,
    // This does not have the additional delay that kLow has, but it is still
    // limited by OS timer precision. See PostDelayedHighPrecisionTask() for
    // more information.
    kHigh,
  };

  // Starts destruction of the task queue.
  // On return ensures no task are running and no new tasks are able to start
  // on the task queue.
  // Responsible for deallocation. Deallocation may happen synchronously during
  // Delete or asynchronously after Delete returns.
  // Code not running on the TaskQueue should not make any assumption when
  // TaskQueue is deallocated and thus should not call any methods after Delete.
  // Code running on the TaskQueue should not call Delete, but can assume
  // TaskQueue still exists and may call other methods, e.g. PostTask.
  // Should be called on the same task queue or thread that this task queue
  // was created on.
  virtual void Delete() = 0;

  // Schedules a task to execute. Tasks are executed in FIFO order.
  // If `task->Run()` returns true, task is deleted on the task queue
  // before next QueuedTask starts executing.
  // When a TaskQueue is deleted, pending tasks will not be executed but they
  // will be deleted. The deletion of tasks may happen synchronously on the
  // TaskQueue or it may happen asynchronously after TaskQueue is deleted.
  // This may vary from one implementation to the next so assumptions about
  // lifetimes of pending tasks should not be made.
  // May be called on any thread or task queue, including this task queue.
  virtual void PostTask(std::unique_ptr<QueuedTask> task) = 0;

  // Prefer PostDelayedTask() over PostDelayedHighPrecisionTask() whenever
  // possible.
  //
  // Schedules a task to execute a specified number of milliseconds from when
  // the call is made, using "low" precision. All scheduling is affected by
  // OS-specific leeway and current workloads which means that in terms of
  // precision there are no hard guarantees, but in addition to the OS induced
  // leeway, "low" precision adds up to a 17 ms additional leeway. The purpose
  // of this leeway is to achieve more efficient CPU scheduling and reduce Idle
  // Wake Up frequency.
  //
  // The task may execute with [-1, 17 + OS induced leeway) ms additional delay.
  //
  // Avoid making assumptions about the precision of the OS scheduler. On macOS,
  // the OS induced leeway may be 10% of sleep interval. On Windows, 1 ms
  // precision timers may be used but there are cases, such as when running on
  // battery, when the timer precision can be as poor as 15 ms.
  //
  // "Low" precision is not implemented everywhere yet. Where not yet
  // implemented, PostDelayedTask() has "high" precision. See
  // https://crbug.com/webrtc/13583 for more information.
  //
  // May be called on any thread or task queue, including this task queue.
  virtual void PostDelayedTask(std::unique_ptr<QueuedTask> task,
                               uint32_t milliseconds) = 0;

  // Prefer PostDelayedTask() over PostDelayedHighPrecisionTask() whenever
  // possible.
  //
  // Schedules a task to execute a specified number of milliseconds from when
  // the call is made, using "high" precision. All scheduling is affected by
  // OS-specific leeway and current workloads which means that in terms of
  // precision there are no hard guarantees.
  //
  // The task may execute with [-1, OS induced leeway] ms additional delay.
  //
  // Avoid making assumptions about the precision of the OS scheduler. On macOS,
  // the OS induced leeway may be 10% of sleep interval. On Windows, 1 ms
  // precision timers may be used but there are cases, such as when running on
  // battery, when the timer precision can be as poor as 15 ms.
  //
  // May be called on any thread or task queue, including this task queue.
  virtual void PostDelayedHighPrecisionTask(std::unique_ptr<QueuedTask> task,
                                            uint32_t milliseconds) {
    // Remove default implementation when dependencies have implemented this
    // method.
    PostDelayedTask(std::move(task), milliseconds);
  }

  // As specified by |precision|, calls either PostDelayedTask() or
  // PostDelayedHighPrecisionTask().
  void PostDelayedTaskWithPrecision(DelayPrecision precision,
                                    std::unique_ptr<QueuedTask> task,
                                    uint32_t milliseconds) {
    switch (precision) {
      case DelayPrecision::kLow:
        PostDelayedTask(std::move(task), milliseconds);
        break;
      case DelayPrecision::kHigh:
        PostDelayedHighPrecisionTask(std::move(task), milliseconds);
        break;
    }
  }

  // Returns the task queue that is running the current thread.
  // Returns nullptr if this thread is not associated with any task queue.
  // May be called on any thread or task queue, including this task queue.
  static TaskQueueBase* Current();
  bool IsCurrent() const { return Current() == this; }

 protected:
  class CurrentTaskQueueSetter {
   public:
    explicit CurrentTaskQueueSetter(TaskQueueBase* task_queue);
    CurrentTaskQueueSetter(const CurrentTaskQueueSetter&) = delete;
    CurrentTaskQueueSetter& operator=(const CurrentTaskQueueSetter&) = delete;
    ~CurrentTaskQueueSetter();

   private:
    TaskQueueBase* const previous_;
  };

  // Users of the TaskQueue should call Delete instead of directly deleting
  // this object.
  virtual ~TaskQueueBase() = default;
};

struct TaskQueueDeleter {
  void operator()(TaskQueueBase* task_queue) const { task_queue->Delete(); }
};

}  // namespace webrtc

#endif  // API_TASK_QUEUE_TASK_QUEUE_BASE_H_
