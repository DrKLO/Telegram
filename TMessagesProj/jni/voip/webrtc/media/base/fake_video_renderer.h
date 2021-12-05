/*
 *  Copyright (c) 2011 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#ifndef MEDIA_BASE_FAKE_VIDEO_RENDERER_H_
#define MEDIA_BASE_FAKE_VIDEO_RENDERER_H_

#include <stdint.h>

#include "api/scoped_refptr.h"
#include "api/video/video_frame.h"
#include "api/video/video_frame_buffer.h"
#include "api/video/video_rotation.h"
#include "api/video/video_sink_interface.h"
#include "rtc_base/event.h"
#include "rtc_base/synchronization/mutex.h"

namespace cricket {

// Faked video renderer that has a callback for actions on rendering.
class FakeVideoRenderer : public rtc::VideoSinkInterface<webrtc::VideoFrame> {
 public:
  FakeVideoRenderer();

  void OnFrame(const webrtc::VideoFrame& frame) override;

  int errors() const { return errors_; }

  int width() const {
    webrtc::MutexLock lock(&mutex_);
    return width_;
  }
  int height() const {
    webrtc::MutexLock lock(&mutex_);
    return height_;
  }

  webrtc::VideoRotation rotation() const {
    webrtc::MutexLock lock(&mutex_);
    return rotation_;
  }

  int64_t timestamp_us() const {
    webrtc::MutexLock lock(&mutex_);
    return timestamp_us_;
  }

  int num_rendered_frames() const {
    webrtc::MutexLock lock(&mutex_);
    return num_rendered_frames_;
  }

  bool black_frame() const {
    webrtc::MutexLock lock(&mutex_);
    return black_frame_;
  }

  int64_t ntp_time_ms() const {
    webrtc::MutexLock lock(&mutex_);
    return ntp_timestamp_ms_;
  }

  absl::optional<webrtc::ColorSpace> color_space() const {
    webrtc::MutexLock lock(&mutex_);
    return color_space_;
  }

  webrtc::RtpPacketInfos packet_infos() const {
    webrtc::MutexLock lock(&mutex_);
    return packet_infos_;
  }

  bool WaitForRenderedFrame(int64_t timeout_ms);

 private:
  static bool CheckFrameColorYuv(uint8_t y_min,
                                 uint8_t y_max,
                                 uint8_t u_min,
                                 uint8_t u_max,
                                 uint8_t v_min,
                                 uint8_t v_max,
                                 const webrtc::VideoFrame* frame) {
    if (!frame || !frame->video_frame_buffer()) {
      return false;
    }
    rtc::scoped_refptr<const webrtc::I420BufferInterface> i420_buffer =
        frame->video_frame_buffer()->ToI420();
    // Y
    int y_width = frame->width();
    int y_height = frame->height();
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

  int errors_ = 0;
  int width_ = 0;
  int height_ = 0;
  webrtc::VideoRotation rotation_ = webrtc::kVideoRotation_0;
  int64_t timestamp_us_ = 0;
  int num_rendered_frames_ = 0;
  int64_t ntp_timestamp_ms_ = 0;
  bool black_frame_ = false;
  mutable webrtc::Mutex mutex_;
  rtc::Event frame_rendered_event_;
  absl::optional<webrtc::ColorSpace> color_space_;
  webrtc::RtpPacketInfos packet_infos_;
};

}  // namespace cricket

#endif  // MEDIA_BASE_FAKE_VIDEO_RENDERER_H_
