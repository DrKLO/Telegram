/*
 *  Copyright (c) 2018 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#ifndef MODULES_VIDEO_CODING_CODECS_MULTIPLEX_INCLUDE_AUGMENTED_VIDEO_FRAME_BUFFER_H_
#define MODULES_VIDEO_CODING_CODECS_MULTIPLEX_INCLUDE_AUGMENTED_VIDEO_FRAME_BUFFER_H_

#include <cstdint>
#include <memory>

#include "api/scoped_refptr.h"
#include "api/video/video_frame_buffer.h"

namespace webrtc {
class AugmentedVideoFrameBuffer : public VideoFrameBuffer {
 public:
  AugmentedVideoFrameBuffer(
      const rtc::scoped_refptr<VideoFrameBuffer>& video_frame_buffer,
      std::unique_ptr<uint8_t[]> augmenting_data,
      uint16_t augmenting_data_size);

  // Retrieves the underlying VideoFrameBuffer without the augmented data
  rtc::scoped_refptr<VideoFrameBuffer> GetVideoFrameBuffer() const;

  // Gets a pointer to the augmenting data and moves ownership to the caller
  uint8_t* GetAugmentingData() const;

  // Get the size of the augmenting data
  uint16_t GetAugmentingDataSize() const;

  // Returns the type of the underlying VideoFrameBuffer
  Type type() const final;

  // Returns the width of the underlying VideoFrameBuffer
  int width() const final;

  // Returns the height of the underlying VideoFrameBuffer
  int height() const final;

  // Get the I140 Buffer from the underlying frame buffer
  rtc::scoped_refptr<I420BufferInterface> ToI420() final;

 private:
  uint16_t augmenting_data_size_;
  std::unique_ptr<uint8_t[]> augmenting_data_;
  rtc::scoped_refptr<webrtc::VideoFrameBuffer> video_frame_buffer_;
};
}  // namespace webrtc

#endif  // MODULES_VIDEO_CODING_CODECS_MULTIPLEX_INCLUDE_AUGMENTED_VIDEO_FRAME_BUFFER_H_
