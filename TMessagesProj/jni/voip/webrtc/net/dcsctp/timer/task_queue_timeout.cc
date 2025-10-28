/*
 *  Copyright (c) 2021 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */
#include "net/dcsctp/timer/task_queue_timeout.h"

#include "api/task_queue/pending_task_safety_flag.h"
#include "api/units/time_delta.h"
#include "rtc_base/logging.h"

namespace dcsctp {
using ::webrtc::TimeDelta;
using ::webrtc::Timestamp;

TaskQueueTimeoutFactory::TaskQueueTimeout::TaskQueueTimeout(
    TaskQueueTimeoutFactory& parent,
    webrtc::TaskQueueBase::DelayPrecision precision)
    : parent_(parent),
      precision_(precision),
      pending_task_safety_flag_(webrtc::PendingTaskSafetyFlag::Create()) {}

TaskQueueTimeoutFactory::TaskQueueTimeout::~TaskQueueTimeout() {
  RTC_DCHECK_RUN_ON(&parent_.thread_checker_);
  pending_task_safety_flag_->SetNotAlive();
}

void TaskQueueTimeoutFactory::TaskQueueTimeout::Start(DurationMs duration_ms,
                                                      TimeoutID timeout_id) {
  RTC_DCHECK_RUN_ON(&parent_.thread_checker_);
  RTC_DCHECK(timeout_expiration_.IsPlusInfinity());
  timeout_expiration_ = parent_.Now() + duration_ms.ToTimeDelta();
  timeout_id_ = timeout_id;

  if (timeout_expiration_ >= posted_task_expiration_) {
    // There is already a running task, and it's scheduled to expire sooner than
    // the new expiration time. Don't do anything; The `timeout_expiration_` has
    // already been updated and if the delayed task _does_ expire and the timer
    // hasn't been stopped, that will be noticed in the timeout handler, and the
    // task will be re-scheduled. Most timers are stopped before they expire.
    return;
  }

  if (!posted_task_expiration_.IsPlusInfinity()) {
    RTC_DLOG(LS_VERBOSE) << "New timeout duration is less than scheduled - "
                            "ghosting old delayed task.";
    // There is already a scheduled delayed task, but its expiration time is
    // further away than the new expiration, so it can't be used. It will be
    // "killed" by replacing the safety flag. This is not expected to happen
    // especially often; Mainly when a timer did exponential backoff and
    // later recovered.
    pending_task_safety_flag_->SetNotAlive();
    pending_task_safety_flag_ = webrtc::PendingTaskSafetyFlag::Create();
  }

  posted_task_expiration_ = timeout_expiration_;
  parent_.task_queue_.PostDelayedTaskWithPrecision(
      precision_,
      webrtc::SafeTask(
          pending_task_safety_flag_,
          [timeout_id, this]() {
            RTC_DLOG(LS_VERBOSE) << "Timout expired: " << timeout_id.value();
            RTC_DCHECK_RUN_ON(&parent_.thread_checker_);
            RTC_DCHECK(!posted_task_expiration_.IsPlusInfinity());
            posted_task_expiration_ = Timestamp::PlusInfinity();

            if (timeout_expiration_.IsPlusInfinity()) {
              // The timeout was stopped before it expired. Very common.
            } else {
              // Note that the timeout might have been restarted, which updated
              // `timeout_expiration_` but left the scheduled task running. So
              // if it's not quite time to trigger the timeout yet, schedule a
              // new delayed task with what's remaining and retry at that point
              // in time.
              TimeDelta remaining = timeout_expiration_ - parent_.Now();
              timeout_expiration_ = Timestamp::PlusInfinity();
              if (remaining > TimeDelta::Zero()) {
                Start(DurationMs(remaining.ms()), timeout_id_);
              } else {
                // It has actually triggered.
                RTC_DLOG(LS_VERBOSE)
                    << "Timout triggered: " << timeout_id.value();
                parent_.on_expired_(timeout_id_);
              }
            }
          }),
      webrtc::TimeDelta::Millis(duration_ms.value()));
}

void TaskQueueTimeoutFactory::TaskQueueTimeout::Stop() {
  // As the TaskQueue doesn't support deleting a posted task, just mark the
  // timeout as not running.
  RTC_DCHECK_RUN_ON(&parent_.thread_checker_);
  timeout_expiration_ = Timestamp::PlusInfinity();
}

}  // namespace dcsctp
