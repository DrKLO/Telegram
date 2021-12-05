/*
 *  Copyright 2020 The WebRTC Project Authors. All rights reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "call/adaptation/video_stream_input_state.h"

#include "api/video_codecs/video_encoder.h"

namespace webrtc {

VideoStreamInputState::VideoStreamInputState()
    : has_input_(false),
      frame_size_pixels_(absl::nullopt),
      frames_per_second_(0),
      video_codec_type_(VideoCodecType::kVideoCodecGeneric),
      min_pixels_per_frame_(kDefaultMinPixelsPerFrame),
      single_active_stream_pixels_(absl::nullopt) {}

void VideoStreamInputState::set_has_input(bool has_input) {
  has_input_ = has_input;
}

void VideoStreamInputState::set_frame_size_pixels(
    absl::optional<int> frame_size_pixels) {
  frame_size_pixels_ = frame_size_pixels;
}

void VideoStreamInputState::set_frames_per_second(int frames_per_second) {
  frames_per_second_ = frames_per_second;
}

void VideoStreamInputState::set_video_codec_type(
    VideoCodecType video_codec_type) {
  video_codec_type_ = video_codec_type;
}

void VideoStreamInputState::set_min_pixels_per_frame(int min_pixels_per_frame) {
  min_pixels_per_frame_ = min_pixels_per_frame;
}

void VideoStreamInputState::set_single_active_stream_pixels(
    absl::optional<int> single_active_stream_pixels) {
  single_active_stream_pixels_ = single_active_stream_pixels;
}

bool VideoStreamInputState::has_input() const {
  return has_input_;
}

absl::optional<int> VideoStreamInputState::frame_size_pixels() const {
  return frame_size_pixels_;
}

int VideoStreamInputState::frames_per_second() const {
  return frames_per_second_;
}

VideoCodecType VideoStreamInputState::video_codec_type() const {
  return video_codec_type_;
}

int VideoStreamInputState::min_pixels_per_frame() const {
  return min_pixels_per_frame_;
}

absl::optional<int> VideoStreamInputState::single_active_stream_pixels() const {
  return single_active_stream_pixels_;
}

bool VideoStreamInputState::HasInputFrameSizeAndFramesPerSecond() const {
  return has_input_ && frame_size_pixels_.has_value();
}

}  // namespace webrtc
