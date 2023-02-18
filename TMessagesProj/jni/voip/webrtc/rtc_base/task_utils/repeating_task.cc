/*
 *  Copyright 2019 The WebRTC Project Authors. All rights reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "rtc_base/task_utils/repeating_task.h"

#include "absl/functional/any_invocable.h"
#include "api/task_queue/pending_task_safety_flag.h"
#include "rtc_base/logging.h"

namespace webrtc {
namespace {

class RepeatingTask {
 public:
  RepeatingTask(TaskQueueBase* task_queue,
                TaskQueueBase::DelayPrecision precision,
                TimeDelta first_delay,
                absl::AnyInvocable<TimeDelta()> task,
                Clock* clock,
                rtc::scoped_refptr<PendingTaskSafetyFlag> alive_flag);
  RepeatingTask(RepeatingTask&&) = default;
  RepeatingTask& operator=(RepeatingTask&&) = delete;
  ~RepeatingTask() = default;

  void operator()() &&;

 private:
  TaskQueueBase* const task_queue_;
  const TaskQueueBase::DelayPrecision precision_;
  Clock* const clock_;
  absl::AnyInvocable<TimeDelta()> task_;
  // This is always finite.
  Timestamp next_run_time_ RTC_GUARDED_BY(task_queue_);
  rtc::scoped_refptr<PendingTaskSafetyFlag> alive_flag_
      RTC_GUARDED_BY(task_queue_);
};

RepeatingTask::RepeatingTask(
    TaskQueueBase* task_queue,
    TaskQueueBase::DelayPrecision precision,
    TimeDelta first_delay,
    absl::AnyInvocable<TimeDelta()> task,
    Clock* clock,
    rtc::scoped_refptr<PendingTaskSafetyFlag> alive_flag)
    : task_queue_(task_queue),
      precision_(precision),
      clock_(clock),
      task_(std::move(task)),
      next_run_time_(clock_->CurrentTime() + first_delay),
      alive_flag_(std::move(alive_flag)) {}

void RepeatingTask::operator()() && {
  RTC_DCHECK_RUN_ON(task_queue_);
  if (!alive_flag_->alive())
    return;

  webrtc_repeating_task_impl::RepeatingTaskImplDTraceProbeRun();
  TimeDelta delay = task_();
  RTC_DCHECK_GE(delay, TimeDelta::Zero());

  // A delay of +infinity means that the task should not be run again.
  // Alternatively, the closure might have stopped this task.
  if (delay.IsPlusInfinity() || !alive_flag_->alive())
    return;

  TimeDelta lost_time = clock_->CurrentTime() - next_run_time_;
  next_run_time_ += delay;
  delay -= lost_time;
  delay = std::max(delay, TimeDelta::Zero());

  task_queue_->PostDelayedTaskWithPrecision(precision_, std::move(*this),
                                            delay);
}

}  // namespace

RepeatingTaskHandle RepeatingTaskHandle::Start(
    TaskQueueBase* task_queue,
    absl::AnyInvocable<TimeDelta()> closure,
    TaskQueueBase::DelayPrecision precision,
    Clock* clock) {
  auto alive_flag = PendingTaskSafetyFlag::CreateDetached();
  webrtc_repeating_task_impl::RepeatingTaskHandleDTraceProbeStart();
  task_queue->PostTask(RepeatingTask(task_queue, precision, TimeDelta::Zero(),
                                     std::move(closure), clock, alive_flag));
  return RepeatingTaskHandle(std::move(alive_flag));
}

// DelayedStart is equivalent to Start except that the first invocation of the
// closure will be delayed by the given amount.
RepeatingTaskHandle RepeatingTaskHandle::DelayedStart(
    TaskQueueBase* task_queue,
    TimeDelta first_delay,
    absl::AnyInvocable<TimeDelta()> closure,
    TaskQueueBase::DelayPrecision precision,
    Clock* clock) {
  auto alive_flag = PendingTaskSafetyFlag::CreateDetached();
  webrtc_repeating_task_impl::RepeatingTaskHandleDTraceProbeDelayedStart();
  task_queue->PostDelayedTaskWithPrecision(
      precision,
      RepeatingTask(task_queue, precision, first_delay, std::move(closure),
                    clock, alive_flag),
      first_delay);
  return RepeatingTaskHandle(std::move(alive_flag));
}

void RepeatingTaskHandle::Stop() {
  if (repeating_task_) {
    repeating_task_->SetNotAlive();
    repeating_task_ = nullptr;
  }
}

bool RepeatingTaskHandle::Running() const {
  return repeating_task_ != nullptr;
}

namespace webrtc_repeating_task_impl {
// These methods are empty, but can be externally equipped with actions using
// dtrace.
void RepeatingTaskHandleDTraceProbeStart() {}
void RepeatingTaskHandleDTraceProbeDelayedStart() {}
void RepeatingTaskImplDTraceProbeRun() {}
}  // namespace webrtc_repeating_task_impl
}  // namespace webrtc
