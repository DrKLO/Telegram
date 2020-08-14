/*
 *  Copyright (c) 2018 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "modules/video_coding/codecs/multiplex/include/augmented_video_frame_buffer.h"

#include <stdint.h>

#include <utility>

#include "api/video/video_frame_buffer.h"

namespace webrtc {

AugmentedVideoFrameBuffer::AugmentedVideoFrameBuffer(
    const rtc::scoped_refptr<VideoFrameBuffer>& video_frame_buffer,
    std::unique_ptr<uint8_t[]> augmenting_data,
    uint16_t augmenting_data_size)
    : augmenting_data_size_(augmenting_data_size),
      augmenting_data_(std::move(augmenting_data)),
      video_frame_buffer_(video_frame_buffer) {}

rtc::scoped_refptr<VideoFrameBuffer>
AugmentedVideoFrameBuffer::GetVideoFrameBuffer() const {
  return video_frame_buffer_;
}

uint8_t* AugmentedVideoFrameBuffer::GetAugmentingData() const {
  return augmenting_data_.get();
}

uint16_t AugmentedVideoFrameBuffer::GetAugmentingDataSize() const {
  return augmenting_data_size_;
}

VideoFrameBuffer::Type AugmentedVideoFrameBuffer::type() const {
  return video_frame_buffer_->type();
}

int AugmentedVideoFrameBuffer::width() const {
  return video_frame_buffer_->width();
}

int AugmentedVideoFrameBuffer::height() const {
  return video_frame_buffer_->height();
}

rtc::scoped_refptr<I420BufferInterface> AugmentedVideoFrameBuffer::ToI420() {
  return video_frame_buffer_->ToI420();
}
}  // namespace webrtc
