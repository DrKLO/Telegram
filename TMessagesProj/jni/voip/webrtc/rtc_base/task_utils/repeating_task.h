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
#include "system_wrappers/include/clock.h"

namespace webrtc {

class RepeatingTaskHandle;

namespace webrtc_repeating_task_impl {
class RepeatingTaskBase : public QueuedTask {
 public:
  RepeatingTaskBase(TaskQueueBase* task_queue,
                    TimeDelta first_delay,
                    Clock* clock);
  ~RepeatingTaskBase() override;

  void Stop();

 private:
  virtual TimeDelta RunClosure() = 0;

  bool Run() final;

  TaskQueueBase* const task_queue_;
  Clock* const clock_;
  // This is always finite, except for the special case where it's PlusInfinity
  // to signal that the task should stop.
  Timestamp next_run_time_ RTC_GUARDED_BY(task_queue_);
};

// The template closure pattern is based on rtc::ClosureTask.
template <class Closure>
class RepeatingTaskImpl final : public RepeatingTaskBase {
 public:
  RepeatingTaskImpl(TaskQueueBase* task_queue,
                    TimeDelta first_delay,
                    Closure&& closure,
                    Clock* clock)
      : RepeatingTaskBase(task_queue, first_delay, clock),
        closure_(std::forward<Closure>(closure)) {
    static_assert(
        std::is_same<TimeDelta,
                     typename std::result_of<decltype (&Closure::operator())(
                         Closure)>::type>::value,
        "");
  }

 private:
  TimeDelta RunClosure() override { return closure_(); }

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
  RepeatingTaskHandle(RepeatingTaskHandle&& other);
  RepeatingTaskHandle& operator=(RepeatingTaskHandle&& other);
  RepeatingTaskHandle(const RepeatingTaskHandle&) = delete;
  RepeatingTaskHandle& operator=(const RepeatingTaskHandle&) = delete;

  // Start can be used to start a task that will be reposted with a delay
  // determined by the return value of the provided closure. The actual task is
  // owned by the TaskQueue and will live until it has been stopped or the
  // TaskQueue is destroyed. Note that this means that trying to stop the
  // repeating task after the TaskQueue is destroyed is an error. However, it's
  // perfectly fine to destroy the handle while the task is running, since the
  // repeated task is owned by the TaskQueue.
  template <class Closure>
  static RepeatingTaskHandle Start(TaskQueueBase* task_queue,
                                   Closure&& closure,
                                   Clock* clock = Clock::GetRealTimeClock()) {
    auto repeating_task = std::make_unique<
        webrtc_repeating_task_impl::RepeatingTaskImpl<Closure>>(
        task_queue, TimeDelta::Zero(), std::forward<Closure>(closure), clock);
    auto* repeating_task_ptr = repeating_task.get();
    task_queue->PostTask(std::move(repeating_task));
    return RepeatingTaskHandle(repeating_task_ptr);
  }

  // DelayedStart is equivalent to Start except that the first invocation of the
  // closure will be delayed by the given amount.
  template <class Closure>
  static RepeatingTaskHandle DelayedStart(
      TaskQueueBase* task_queue,
      TimeDelta first_delay,
      Closure&& closure,
      Clock* clock = Clock::GetRealTimeClock()) {
    auto repeating_task = std::make_unique<
        webrtc_repeating_task_impl::RepeatingTaskImpl<Closure>>(
        task_queue, first_delay, std::forward<Closure>(closure), clock);
    auto* repeating_task_ptr = repeating_task.get();
    task_queue->PostDelayedTask(std::move(repeating_task), first_delay.ms());
    return RepeatingTaskHandle(repeating_task_ptr);
  }

  // Stops future invocations of the repeating task closure. Can only be called
  // from the TaskQueue where the task is running. The closure is guaranteed to
  // not be running after Stop() returns unless Stop() is called from the
  // closure itself.
  void Stop();

  // Returns true if Start() or DelayedStart() was called most recently. Returns
  // false initially and if Stop() or PostStop() was called most recently.
  bool Running() const;

 private:
  explicit RepeatingTaskHandle(
      webrtc_repeating_task_impl::RepeatingTaskBase* repeating_task);
  // Owned by the task queue.
  webrtc_repeating_task_impl::RepeatingTaskBase* repeating_task_ = nullptr;
};

}  // namespace webrtc
#endif  // RTC_BASE_TASK_UTILS_REPEATING_TASK_H_
