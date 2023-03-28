/*
 *  Copyright (c) 2018 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "media/base/fake_video_renderer.h"

namespace cricket {
namespace {
bool CheckFrameColorYuv(const webrtc::VideoFrame& frame) {
  // TODO(zhurunz) Check with VP8 team to see if we can remove this
  // tolerance on Y values. Some unit tests produce Y values close
  // to 16 rather than close to zero, for supposedly black frames.
  // Largest value observed is 34, e.g., running
  // PeerConnectionIntegrationTest.SendAndReceive16To9AspectRatio.
  static constexpr uint8_t y_min = 0;
  static constexpr uint8_t y_max = 48;
  static constexpr uint8_t u_min = 128;
  static constexpr uint8_t u_max = 128;
  static constexpr uint8_t v_min = 128;
  static constexpr uint8_t v_max = 128;

  if (!frame.video_frame_buffer()) {
    return false;
  }
  rtc::scoped_refptr<const webrtc::I420BufferInterface> i420_buffer =
      frame.video_frame_buffer()->ToI420();
  // Y
  int y_width = frame.width();
  int y_height = frame.height();
  const uint8_t* y_plane = i420_buffer->DataY();
  const uint8_t* y_pos = y_plane;
  int32_t y_pitch = i420_buffer->StrideY();
  for (int i = 0; i < y_height; ++i) {
    for (int j = 0; j < y_width; ++j) {
      uint8_t y_value = *(y_pos + j);
      if (y_value < y_min || y_value > y_max) {
        return false;
      }
    }
    y_pos += y_pitch;
  }
  // U and V
  int chroma_width = i420_buffer->ChromaWidth();
  int chroma_height = i420_buffer->ChromaHeight();
  const uint8_t* u_plane = i420_buffer->DataU();
  const uint8_t* v_plane = i420_buffer->DataV();
  const uint8_t* u_pos = u_plane;
  const uint8_t* v_pos = v_plane;
  int32_t u_pitch = i420_buffer->StrideU();
  int32_t v_pitch = i420_buffer->StrideV();
  for (int i = 0; i < chroma_height; ++i) {
    for (int j = 0; j < chroma_width; ++j) {
      uint8_t u_value = *(u_pos + j);
      if (u_value < u_min || u_value > u_max) {
        return false;
      }
      uint8_t v_value = *(v_pos + j);
      if (v_value < v_min || v_value > v_max) {
        return false;
      }
    }
    u_pos += u_pitch;
    v_pos += v_pitch;
  }
  return true;
}
}  // namespace

FakeVideoRenderer::FakeVideoRenderer() = default;

void FakeVideoRenderer::OnFrame(const webrtc::VideoFrame& frame) {
  webrtc::MutexLock lock(&mutex_);
  black_frame_ = CheckFrameColorYuv(frame);
  ++num_rendered_frames_;
  width_ = frame.width();
  height_ = frame.height();
  rotation_ = frame.rotation();
  timestamp_us_ = frame.timestamp_us();
}

}  // namespace cricket
