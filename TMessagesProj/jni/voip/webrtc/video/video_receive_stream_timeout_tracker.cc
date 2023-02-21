/*
 *  Copyright (c) 2022 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "video/video_receive_stream_timeout_tracker.h"

#include <algorithm>
#include <utility>

#include "rtc_base/logging.h"

namespace webrtc {

VideoReceiveStreamTimeoutTracker::VideoReceiveStreamTimeoutTracker(
    Clock* clock,
    TaskQueueBase* const bookkeeping_queue,
    const Timeouts& timeouts,
    TimeoutCallback callback)
    : clock_(clock),
      bookkeeping_queue_(bookkeeping_queue),
      timeouts_(timeouts),
      timeout_cb_(std::move(callback)) {}

VideoReceiveStreamTimeoutTracker::~VideoReceiveStreamTimeoutTracker() {
  RTC_DCHECK(!timeout_task_.Running());
}

bool VideoReceiveStreamTimeoutTracker::Running() const {
  return timeout_task_.Running();
}

TimeDelta VideoReceiveStreamTimeoutTracker::TimeUntilTimeout() const {
  return std::max(timeout_ - clock_->CurrentTime(), TimeDelta::Zero());
}

void VideoReceiveStreamTimeoutTracker::Start(bool waiting_for_keyframe) {
  RTC_DCHECK_RUN_ON(bookkeeping_queue_);
  RTC_DCHECK(!timeout_task_.Running());
  waiting_for_keyframe_ = waiting_for_keyframe;
  TimeDelta timeout_delay = TimeoutForNextFrame();
  last_frame_ = clock_->CurrentTime();
  timeout_ = last_frame_ + timeout_delay;
  timeout_task_ =
      RepeatingTaskHandle::DelayedStart(bookkeeping_queue_, timeout_delay,
                                        [this] { return HandleTimeoutTask(); });
}

void VideoReceiveStreamTimeoutTracker::Stop() {
  timeout_task_.Stop();
}

void VideoReceiveStreamTimeoutTracker::SetWaitingForKeyframe() {
  RTC_DCHECK_RUN_ON(bookkeeping_queue_);
  waiting_for_keyframe_ = true;
  TimeDelta timeout_delay = TimeoutForNextFrame();
  if (clock_->CurrentTime() + timeout_delay < timeout_) {
    Stop();
    Start(waiting_for_keyframe_);
  }
}

void VideoReceiveStreamTimeoutTracker::OnEncodedFrameReleased() {
  RTC_DCHECK_RUN_ON(bookkeeping_queue_);
  // If we were waiting for a keyframe, then it has just been released.
  waiting_for_keyframe_ = false;
  last_frame_ = clock_->CurrentTime();
  timeout_ = last_frame_ + TimeoutForNextFrame();
}

TimeDelta VideoReceiveStreamTimeoutTracker::HandleTimeoutTask() {
  RTC_DCHECK_RUN_ON(bookkeeping_queue_);
  Timestamp now = clock_->CurrentTime();
  // `timeout_` is hit and we have timed out. Schedule the next timeout at
  // the timeout delay.
  if (now >= timeout_) {
    RTC_DLOG(LS_VERBOSE) << "Stream timeout at " << now;
    TimeDelta timeout_delay = TimeoutForNextFrame();
    timeout_ = now + timeout_delay;
    timeout_cb_(now - last_frame_);
    return timeout_delay;
  }
  // Otherwise, `timeout_` changed since we scheduled a timeout. Reschedule
  // a timeout check.
  return timeout_ - now;
}

void VideoReceiveStreamTimeoutTracker::SetTimeouts(Timeouts timeouts) {
  RTC_DCHECK_RUN_ON(bookkeeping_queue_);
  timeouts_ = timeouts;
}

}  // namespace webrtc
