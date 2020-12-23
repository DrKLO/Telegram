/*
 *  Copyright (c) 2017 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "api/video/video_frame_buffer.h"

#include "api/video/i420_buffer.h"
#include "rtc_base/checks.h"

namespace webrtc {

rtc::scoped_refptr<VideoFrameBuffer> VideoFrameBuffer::CropAndScale(
    int offset_x,
    int offset_y,
    int crop_width,
    int crop_height,
    int scaled_width,
    int scaled_height) {
  rtc::scoped_refptr<I420Buffer> result =
      I420Buffer::Create(scaled_width, scaled_height);
  result->CropAndScaleFrom(*this->ToI420(), offset_x, offset_y, crop_width,
                           crop_height);
  return result;
}

const I420BufferInterface* VideoFrameBuffer::GetI420() const {
  // Overridden by subclasses that can return an I420 buffer without any
  // conversion, in particular, I420BufferInterface.
  return nullptr;
}

const I420ABufferInterface* VideoFrameBuffer::GetI420A() const {
  RTC_CHECK(type() == Type::kI420A);
  return static_cast<const I420ABufferInterface*>(this);
}

const I444BufferInterface* VideoFrameBuffer::GetI444() const {
  RTC_CHECK(type() == Type::kI444);
  return static_cast<const I444BufferInterface*>(this);
}

const I010BufferInterface* VideoFrameBuffer::GetI010() const {
  RTC_CHECK(type() == Type::kI010);
  return static_cast<const I010BufferInterface*>(this);
}

const NV12BufferInterface* VideoFrameBuffer::GetNV12() const {
  RTC_CHECK(type() == Type::kNV12);
  return static_cast<const NV12BufferInterface*>(this);
}

rtc::scoped_refptr<VideoFrameBuffer> VideoFrameBuffer::GetMappedFrameBuffer(
    rtc::ArrayView<Type> types) {
  RTC_CHECK(type() == Type::kNative);
  return nullptr;
}

VideoFrameBuffer::Type I420BufferInterface::type() const {
  return Type::kI420;
}

const char* VideoFrameBufferTypeToString(VideoFrameBuffer::Type type) {
  switch (type) {
    case VideoFrameBuffer::Type::kNative:
      return "kNative";
    case VideoFrameBuffer::Type::kI420:
      return "kI420";
    case VideoFrameBuffer::Type::kI420A:
      return "kI420A";
    case VideoFrameBuffer::Type::kI444:
      return "kI444";
    case VideoFrameBuffer::Type::kI010:
      return "kI010";
    case VideoFrameBuffer::Type::kNV12:
      return "kNV12";
    default:
      RTC_NOTREACHED();
  }
}

int I420BufferInterface::ChromaWidth() const {
  return (width() + 1) / 2;
}

int I420BufferInterface::ChromaHeight() const {
  return (height() + 1) / 2;
}

rtc::scoped_refptr<I420BufferInterface> I420BufferInterface::ToI420() {
  return this;
}

const I420BufferInterface* I420BufferInterface::GetI420() const {
  return this;
}

VideoFrameBuffer::Type I420ABufferInterface::type() const {
  return Type::kI420A;
}

VideoFrameBuffer::Type I444BufferInterface::type() const {
  return Type::kI444;
}

int I444BufferInterface::ChromaWidth() const {
  return width();
}

int I444BufferInterface::ChromaHeight() const {
  return height();
}

VideoFrameBuffer::Type I010BufferInterface::type() const {
  return Type::kI010;
}

int I010BufferInterface::ChromaWidth() const {
  return (width() + 1) / 2;
}

int I010BufferInterface::ChromaHeight() const {
  return (height() + 1) / 2;
}

VideoFrameBuffer::Type NV12BufferInterface::type() const {
  return Type::kNV12;
}

int NV12BufferInterface::ChromaWidth() const {
  return (width() + 1) / 2;
}

int NV12BufferInterface::ChromaHeight() const {
  return (height() + 1) / 2;
}
}  // namespace webrtc
