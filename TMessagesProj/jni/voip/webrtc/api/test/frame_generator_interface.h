/*
 *  Copyright (c) 2019 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#ifndef API_TEST_FRAME_GENERATOR_INTERFACE_H_
#define API_TEST_FRAME_GENERATOR_INTERFACE_H_

#include <utility>

#include "absl/types/optional.h"
#include "api/scoped_refptr.h"
#include "api/video/video_frame.h"
#include "api/video/video_frame_buffer.h"

namespace webrtc {
namespace test {

class FrameGeneratorInterface {
 public:
  struct VideoFrameData {
    VideoFrameData(rtc::scoped_refptr<VideoFrameBuffer> buffer,
                   absl::optional<VideoFrame::UpdateRect> update_rect)
        : buffer(std::move(buffer)), update_rect(update_rect) {}

    rtc::scoped_refptr<VideoFrameBuffer> buffer;
    absl::optional<VideoFrame::UpdateRect> update_rect;
  };

  enum class OutputType { kI420, kI420A, kI010 };

  virtual ~FrameGeneratorInterface() = default;

  // Returns VideoFrameBuffer and area where most of update was done to set them
  // on the VideoFrame object.
  virtual VideoFrameData NextFrame() = 0;

  // Change the capture resolution.
  virtual void ChangeResolution(size_t width, size_t height) = 0;
};

}  // namespace test
}  // namespace webrtc

#endif  // API_TEST_FRAME_GENERATOR_INTERFACE_H_
