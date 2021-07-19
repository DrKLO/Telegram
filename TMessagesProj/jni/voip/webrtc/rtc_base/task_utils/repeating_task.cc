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

#include "absl/memory/memory.h"
#include "rtc_base/logging.h"
#include "rtc_base/task_utils/to_queued_task.h"
#include "rtc_base/time_utils.h"

namespace webrtc {
namespace webrtc_repeating_task_impl {

RepeatingTaskBase::RepeatingTaskBase(TaskQueueBase* task_queue,
                                     TimeDelta first_delay,
                                     Clock* clock)
    : task_queue_(task_queue),
      clock_(clock),
      next_run_time_(clock_->CurrentTime() + first_delay) {}

RepeatingTaskBase::~RepeatingTaskBase() = default;

bool RepeatingTaskBase::Run() {
  RTC_DCHECK_RUN_ON(task_queue_);
  // Return true to tell the TaskQueue to destruct this object.
  if (next_run_time_.IsPlusInfinity())
    return true;

  TimeDelta delay = RunClosure();

  // The closure might have stopped this task, in which case we return true to
  // destruct this object.
  if (next_run_time_.IsPlusInfinity())
    return true;

  RTC_DCHECK(delay.IsFinite());
  TimeDelta lost_time = clock_->CurrentTime() - next_run_time_;
  next_run_time_ += delay;
  delay -= lost_time;
  delay = std::max(delay, TimeDelta::Zero());

  task_queue_->PostDelayedTask(absl::WrapUnique(this), delay.ms());

  // Return false to tell the TaskQueue to not destruct this object since we
  // have taken ownership with absl::WrapUnique.
  return false;
}

void RepeatingTaskBase::Stop() {
  RTC_DCHECK_RUN_ON(task_queue_);
  RTC_DCHECK(next_run_time_.IsFinite());
  next_run_time_ = Timestamp::PlusInfinity();
}

}  // namespace webrtc_repeating_task_impl

RepeatingTaskHandle::RepeatingTaskHandle(RepeatingTaskHandle&& other)
    : repeating_task_(other.repeating_task_) {
  other.repeating_task_ = nullptr;
}

RepeatingTaskHandle& RepeatingTaskHandle::operator=(
    RepeatingTaskHandle&& other) {
  repeating_task_ = other.repeating_task_;
  other.repeating_task_ = nullptr;
  return *this;
}

RepeatingTaskHandle::RepeatingTaskHandle(
    webrtc_repeating_task_impl::RepeatingTaskBase* repeating_task)
    : repeating_task_(repeating_task) {}

void RepeatingTaskHandle::Stop() {
  if (repeating_task_) {
    repeating_task_->Stop();
    repeating_task_ = nullptr;
  }
}

bool RepeatingTaskHandle::Running() const {
  return repeating_task_ != nullptr;
}

}  // namespace webrtc
