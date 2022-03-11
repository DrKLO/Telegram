/*
 *  Copyright (c) 2022 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "video/task_queue_frame_decode_scheduler.h"

#include <algorithm>
#include <utility>

#include "api/sequence_checker.h"
#include "rtc_base/checks.h"
#include "rtc_base/task_utils/to_queued_task.h"

namespace webrtc {

TaskQueueFrameDecodeScheduler::TaskQueueFrameDecodeScheduler(
    Clock* clock,
    TaskQueueBase* const bookkeeping_queue)
    : clock_(clock), bookkeeping_queue_(bookkeeping_queue) {
  RTC_DCHECK(clock_);
  RTC_DCHECK(bookkeeping_queue_);
}

TaskQueueFrameDecodeScheduler::~TaskQueueFrameDecodeScheduler() {
  RTC_DCHECK(stopped_);
  RTC_DCHECK(!scheduled_rtp_) << "Outstanding scheduled rtp=" << *scheduled_rtp_
                              << ". Call CancelOutstanding before destruction.";
}

void TaskQueueFrameDecodeScheduler::ScheduleFrame(
    uint32_t rtp,
    FrameDecodeTiming::FrameSchedule schedule,
    FrameReleaseCallback cb) {
  RTC_DCHECK(!stopped_) << "Can not schedule frames after stopped.";
  RTC_DCHECK(!scheduled_rtp_.has_value())
      << "Can not schedule two frames for release at the same time.";
  RTC_DCHECK(cb);
  scheduled_rtp_ = rtp;

  TimeDelta wait = std::max(
      TimeDelta::Zero(), schedule.latest_decode_time - clock_->CurrentTime());
  bookkeeping_queue_->PostDelayedTask(
      ToQueuedTask(task_safety_.flag(),
                   [this, rtp, schedule, cb = std::move(cb)] {
                     RTC_DCHECK_RUN_ON(bookkeeping_queue_);
                     // If the next frame rtp  has changed since this task was
                     // this scheduled  release should be skipped.
                     if (scheduled_rtp_ != rtp)
                       return;
                     scheduled_rtp_ = absl::nullopt;
                     cb(rtp, schedule.render_time);
                   }),
      wait.ms());
}

void TaskQueueFrameDecodeScheduler::CancelOutstanding() {
  scheduled_rtp_ = absl::nullopt;
}

absl::optional<uint32_t>
TaskQueueFrameDecodeScheduler::ScheduledRtpTimestamp() {
  return scheduled_rtp_;
}

void TaskQueueFrameDecodeScheduler::Stop() {
  CancelOutstanding();
  stopped_ = true;
}

}  // namespace webrtc
