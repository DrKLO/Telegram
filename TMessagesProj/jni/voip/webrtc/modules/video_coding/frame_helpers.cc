/*
 *  Copyright (c) 2021 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "modules/video_coding/frame_helpers.h"

#include <utility>

#include "rtc_base/logging.h"

namespace webrtc {

bool FrameHasBadRenderTiming(int64_t render_time_ms,
                             int64_t now_ms,
                             int target_video_delay) {
  // Zero render time means render immediately.
  if (render_time_ms == 0) {
    return false;
  }
  if (render_time_ms < 0) {
    return true;
  }
  const int64_t kMaxVideoDelayMs = 10000;
  if (std::abs(render_time_ms - now_ms) > kMaxVideoDelayMs) {
    int frame_delay = static_cast<int>(std::abs(render_time_ms - now_ms));
    RTC_LOG(LS_WARNING)
        << "A frame about to be decoded is out of the configured "
           "delay bounds ("
        << frame_delay << " > " << kMaxVideoDelayMs
        << "). Resetting the video jitter buffer.";
    return true;
  }
  if (target_video_delay > kMaxVideoDelayMs) {
    RTC_LOG(LS_WARNING) << "The video target delay has grown larger than "
                        << kMaxVideoDelayMs << " ms.";
    return true;
  }
  return false;
}

std::unique_ptr<EncodedFrame> CombineAndDeleteFrames(
    absl::InlinedVector<std::unique_ptr<EncodedFrame>, 4> frames) {
  RTC_DCHECK(!frames.empty());

  if (frames.size() == 1) {
    return std::move(frames[0]);
  }

  size_t total_length = 0;
  for (const auto& frame : frames) {
    total_length += frame->size();
  }
  const EncodedFrame& last_frame = *frames.back();
  std::unique_ptr<EncodedFrame> first_frame = std::move(frames[0]);
  auto encoded_image_buffer = EncodedImageBuffer::Create(total_length);
  uint8_t* buffer = encoded_image_buffer->data();
  first_frame->SetSpatialLayerFrameSize(first_frame->SpatialIndex().value_or(0),
                                        first_frame->size());
  memcpy(buffer, first_frame->data(), first_frame->size());
  buffer += first_frame->size();

  // Spatial index of combined frame is set equal to spatial index of its top
  // spatial layer.
  first_frame->SetSpatialIndex(last_frame.SpatialIndex().value_or(0));

  first_frame->video_timing_mutable()->network2_timestamp_ms =
      last_frame.video_timing().network2_timestamp_ms;
  first_frame->video_timing_mutable()->receive_finish_ms =
      last_frame.video_timing().receive_finish_ms;

  // Append all remaining frames to the first one.
  for (size_t i = 1; i < frames.size(); ++i) {
    // Let |next_frame| fall out of scope so it is deleted after copying.
    std::unique_ptr<EncodedFrame> next_frame = std::move(frames[i]);
    first_frame->SetSpatialLayerFrameSize(
        next_frame->SpatialIndex().value_or(0), next_frame->size());
    memcpy(buffer, next_frame->data(), next_frame->size());
    buffer += next_frame->size();
  }
  first_frame->SetEncodedData(encoded_image_buffer);
  return first_frame;
}

}  // namespace webrtc
