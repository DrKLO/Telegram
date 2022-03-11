/*
 *  Copyright 2019 The WebRTC Project Authors. All rights reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#ifndef RTC_BASE_TASK_UTILS_REPEATING_TASK_H_
#define RTC_BASE_TASK_UTILS_REPEATING_TASK_H_

#include <memory>
#include <type_traits>
#include <utility>

#include "api/task_queue/queued_task.h"
#include "api/task_queue/task_queue_base.h"
#include "api/units/time_delta.h"
#include "api/units/timestamp.h"
#include "rtc_base/task_utils/pending_task_safety_flag.h"
#include "system_wrappers/include/clock.h"

namespace webrtc {

namespace webrtc_repeating_task_impl {

// Methods simplifying external tracing of RepeatingTaskHandle operations.
void RepeatingTaskHandleDTraceProbeStart();
void RepeatingTaskHandleDTraceProbeDelayedStart();
void RepeatingTaskImplDTraceProbeRun();

class RepeatingTaskBase : public QueuedTask {
 public:
  RepeatingTaskBase(TaskQueueBase* task_queue,
                    TaskQueueBase::DelayPrecision precision,
                    TimeDelta first_delay,
                    Clock* clock,
                    rtc::scoped_refptr<PendingTaskSafetyFlag> alive_flag);
  ~RepeatingTaskBase() override;

 private:
  virtual TimeDelta RunClosure() = 0;

  bool Run() final;

  TaskQueueBase* const task_queue_;
  const TaskQueueBase::DelayPrecision precision_;
  Clock* const clock_;
  // This is always finite.
  Timestamp next_run_time_ RTC_GUARDED_BY(task_queue_);
  rtc::scoped_refptr<PendingTaskSafetyFlag> alive_flag_
      RTC_GUARDED_BY(task_queue_);
};

// The template closure pattern is based on rtc::ClosureTask. The provided
// closure should have a TimeDelta return value, specifing the desired
// non-negative interval to next repetition, or TimeDelta::PlusInfinity to
// indicate that the task should be deleted and not called again.
template <class Closure>
class RepeatingTaskImpl final : public RepeatingTaskBase {
 public:
  RepeatingTaskImpl(TaskQueueBase* task_queue,
                    TaskQueueBase::DelayPrecision precision,
                    TimeDelta first_delay,
                    Closure&& closure,
                    Clock* clock,
                    rtc::scoped_refptr<PendingTaskSafetyFlag> alive_flag)
      : RepeatingTaskBase(task_queue,
                          precision,
                          first_delay,
                          clock,
                          std::move(alive_flag)),
        closure_(std::forward<Closure>(closure)) {
    static_assert(
        std::is_same<TimeDelta,
                     typename std::result_of<decltype (&Closure::operator())(
                         Closure)>::type>::value,
        "");
  }

 private:
  TimeDelta RunClosure() override {
    RepeatingTaskImplDTraceProbeRun();
    return closure_();
  }

  typename std::remove_const<
      typename std::remove_reference<Closure>::type>::type closure_;
};
}  // namespace webrtc_repeating_task_impl

// Allows starting tasks that repeat themselves on a TaskQueue indefinately
// until they are stopped or the TaskQueue is destroyed. It allows starting and
// stopping multiple times, but you must stop one task before starting another
// and it can only be stopped when in the running state. The public interface is
// not thread safe.
class RepeatingTaskHandle {
 public:
  RepeatingTaskHandle() = default;
  ~RepeatingTaskHandle() = default;
  RepeatingTaskHandle(RepeatingTaskHandle&& other) = default;
  RepeatingTaskHandle& operator=(RepeatingTaskHandle&& other) = default;
  RepeatingTaskHandle(const RepeatingTaskHandle&) = delete;
  RepeatingTaskHandle& operator=(const RepeatingTaskHandle&) = delete;

  // Start can be used to start a task that will be reposted with a delay
  // determined by the return value of the provided closure. The actual task is
  // owned by the TaskQueue and will live until it has been stopped or the
  // TaskQueue deletes it. It's perfectly fine to destroy the handle while the
  // task is running, since the repeated task is owned by the TaskQueue.
  // The tasks are scheduled onto the task queue using the specified precision.
  template <class Closure>
  static RepeatingTaskHandle Start(TaskQueueBase* task_queue,
                                   Closure&& closure,
                                   TaskQueueBase::DelayPrecision precision =
                                       TaskQueueBase::DelayPrecision::kLow,
                                   Clock* clock = Clock::GetRealTimeClock()) {
    auto alive_flag = PendingTaskSafetyFlag::CreateDetached();
    webrtc_repeating_task_impl::RepeatingTaskHandleDTraceProbeStart();
    task_queue->PostTask(
        std::make_unique<
            webrtc_repeating_task_impl::RepeatingTaskImpl<Closure>>(
            task_queue, precision, TimeDelta::Zero(),
            std::forward<Closure>(closure), clock, alive_flag));
    return RepeatingTaskHandle(std::move(alive_flag));
  }

  // DelayedStart is equivalent to Start except that the first invocation of the
  // closure will be delayed by the given amount.
  template <class Closure>
  static RepeatingTaskHandle DelayedStart(
      TaskQueueBase* task_queue,
      TimeDelta first_delay,
      Closure&& closure,
      TaskQueueBase::DelayPrecision precision =
          TaskQueueBase::DelayPrecision::kLow,
      Clock* clock = Clock::GetRealTimeClock()) {
    auto alive_flag = PendingTaskSafetyFlag::CreateDetached();
    webrtc_repeating_task_impl::RepeatingTaskHandleDTraceProbeDelayedStart();
    task_queue->PostDelayedTaskWithPrecision(
        precision,
        std::make_unique<
            webrtc_repeating_task_impl::RepeatingTaskImpl<Closure>>(
            task_queue, precision, first_delay, std::forward<Closure>(closure),
            clock, alive_flag),
        first_delay.ms());
    return RepeatingTaskHandle(std::move(alive_flag));
  }

  // Stops future invocations of the repeating task closure. Can only be called
  // from the TaskQueue where the task is running. The closure is guaranteed to
  // not be running after Stop() returns unless Stop() is called from the
  // closure itself.
  void Stop();

  // Returns true until Stop() was called.
  // Can only be called from the TaskQueue where the task is running.
  bool Running() const;

 private:
  explicit RepeatingTaskHandle(
      rtc::scoped_refptr<PendingTaskSafetyFlag> alive_flag)
      : repeating_task_(std::move(alive_flag)) {}
  rtc::scoped_refptr<PendingTaskSafetyFlag> repeating_task_;
};

}  // namespace webrtc
#endif  // RTC_BASE_TASK_UTILS_REPEATING_TASK_H_
