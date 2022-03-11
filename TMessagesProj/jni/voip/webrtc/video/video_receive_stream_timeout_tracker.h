/*
 *  Copyright (c) 2022 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#ifndef VIDEO_VIDEO_RECEIVE_STREAM_TIMEOUT_TRACKER_H_
#define VIDEO_VIDEO_RECEIVE_STREAM_TIMEOUT_TRACKER_H_

#include <functional>

#include "api/task_queue/task_queue_base.h"
#include "api/units/time_delta.h"
#include "rtc_base/task_utils/repeating_task.h"
#include "system_wrappers/include/clock.h"

namespace webrtc {

class VideoReceiveStreamTimeoutTracker {
 public:
  struct Timeouts {
    TimeDelta max_wait_for_keyframe;
    TimeDelta max_wait_for_frame;
  };

  using TimeoutCallback = std::function<void()>;
  VideoReceiveStreamTimeoutTracker(Clock* clock,
                                   TaskQueueBase* const bookkeeping_queue,
                                   const Timeouts& timeouts,
                                   TimeoutCallback callback);
  ~VideoReceiveStreamTimeoutTracker();
  VideoReceiveStreamTimeoutTracker(const VideoReceiveStreamTimeoutTracker&) =
      delete;
  VideoReceiveStreamTimeoutTracker& operator=(
      const VideoReceiveStreamTimeoutTracker&) = delete;

  bool Running() const;
  void Start(bool waiting_for_keyframe);
  void Stop();
  void SetWaitingForKeyframe();
  void OnEncodedFrameReleased();

 private:
  TimeDelta TimeoutForNextFrame() const {
    return waiting_for_keyframe_ ? timeouts_.max_wait_for_keyframe
                                 : timeouts_.max_wait_for_frame;
  }
  TimeDelta HandleTimeoutTask();

  Clock* const clock_;
  TaskQueueBase* const bookkeeping_queue_;
  const Timeouts timeouts_;
  const TimeoutCallback callback_;
  RepeatingTaskHandle timeout_task_;

  Timestamp timeout_ = Timestamp::MinusInfinity();
  bool waiting_for_keyframe_;
};
}  // namespace webrtc

#endif  // VIDEO_VIDEO_RECEIVE_STREAM_TIMEOUT_TRACKER_H_
