/*
 *  Copyright (c) 2012 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "common_video/include/incoming_video_stream.h"

#include <memory>
#include <utility>

#include "absl/types/optional.h"
#include "common_video/video_render_frames.h"
#include "rtc_base/checks.h"
#include "rtc_base/trace_event.h"

namespace webrtc {

IncomingVideoStream::IncomingVideoStream(
    TaskQueueFactory* task_queue_factory,
    int32_t delay_ms,
    rtc::VideoSinkInterface<VideoFrame>* callback)
    : render_buffers_(delay_ms),
      callback_(callback),
      incoming_render_queue_(task_queue_factory->CreateTaskQueue(
          "IncomingVideoStream",
          TaskQueueFactory::Priority::HIGH)) {}

IncomingVideoStream::~IncomingVideoStream() {
  RTC_DCHECK(main_thread_checker_.IsCurrent());
}

void IncomingVideoStream::OnFrame(const VideoFrame& video_frame) {
  TRACE_EVENT0("webrtc", "IncomingVideoStream::OnFrame");
  RTC_CHECK_RUNS_SERIALIZED(&decoder_race_checker_);
  RTC_DCHECK(!incoming_render_queue_.IsCurrent());
  // TODO(srte): Using video_frame = std::move(video_frame) would move the frame
  // into the lambda instead of copying it, but it doesn't work unless we change
  // OnFrame to take its frame argument by value instead of const reference.
  incoming_render_queue_.PostTask([this, video_frame = video_frame]() mutable {
    RTC_DCHECK_RUN_ON(&incoming_render_queue_);
    if (render_buffers_.AddFrame(std::move(video_frame)) == 1)
      Dequeue();
  });
}

void IncomingVideoStream::Dequeue() {
  TRACE_EVENT0("webrtc", "IncomingVideoStream::Dequeue");
  RTC_DCHECK_RUN_ON(&incoming_render_queue_);
  absl::optional<VideoFrame> frame_to_render = render_buffers_.FrameToRender();
  if (frame_to_render)
    callback_->OnFrame(*frame_to_render);

  if (render_buffers_.HasPendingFrames()) {
    uint32_t wait_time = render_buffers_.TimeToNextFrameRelease();
    incoming_render_queue_.PostDelayedHighPrecisionTask([this]() { Dequeue(); },
                                                        wait_time);
  }
}

}  // namespace webrtc
