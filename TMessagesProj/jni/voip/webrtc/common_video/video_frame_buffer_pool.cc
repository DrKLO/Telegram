/*
 *  Copyright (c) 2015 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "common_video/include/video_frame_buffer_pool.h"

#include <limits>

#include "rtc_base/checks.h"

namespace webrtc {

namespace {
bool HasOneRef(const rtc::scoped_refptr<VideoFrameBuffer>& buffer) {
  // Cast to rtc::RefCountedObject is safe because this function is only called
  // on locally created VideoFrameBuffers, which are either
  // |rtc::RefCountedObject<I420Buffer>| or |rtc::RefCountedObject<NV12Buffer>|.
  switch (buffer->type()) {
    case VideoFrameBuffer::Type::kI420: {
      return static_cast<rtc::RefCountedObject<I420Buffer>*>(buffer.get())
          ->HasOneRef();
    }
    case VideoFrameBuffer::Type::kNV12: {
      return static_cast<rtc::RefCountedObject<NV12Buffer>*>(buffer.get())
          ->HasOneRef();
    }
    default:
      RTC_NOTREACHED();
  }
  return false;
}

}  // namespace

VideoFrameBufferPool::VideoFrameBufferPool() : VideoFrameBufferPool(false) {}

VideoFrameBufferPool::VideoFrameBufferPool(bool zero_initialize)
    : VideoFrameBufferPool(zero_initialize,
                           std::numeric_limits<size_t>::max()) {}

VideoFrameBufferPool::VideoFrameBufferPool(bool zero_initialize,
                                           size_t max_number_of_buffers)
    : zero_initialize_(zero_initialize),
      max_number_of_buffers_(max_number_of_buffers) {}

VideoFrameBufferPool::~VideoFrameBufferPool() = default;

void VideoFrameBufferPool::Release() {
  buffers_.clear();
}

bool VideoFrameBufferPool::Resize(size_t max_number_of_buffers) {
  RTC_DCHECK_RUNS_SERIALIZED(&race_checker_);
  size_t used_buffers_count = 0;
  for (const rtc::scoped_refptr<VideoFrameBuffer>& buffer : buffers_) {
    // If the buffer is in use, the ref count will be >= 2, one from the list we
    // are looping over and one from the application. If the ref count is 1,
    // then the list we are looping over holds the only reference and it's safe
    // to reuse.
    if (!HasOneRef(buffer)) {
      used_buffers_count++;
    }
  }
  if (used_buffers_count > max_number_of_buffers) {
    return false;
  }
  max_number_of_buffers_ = max_number_of_buffers;

  size_t buffers_to_purge = buffers_.size() - max_number_of_buffers_;
  auto iter = buffers_.begin();
  while (iter != buffers_.end() && buffers_to_purge > 0) {
    if (HasOneRef(*iter)) {
      iter = buffers_.erase(iter);
      buffers_to_purge--;
    } else {
      ++iter;
    }
  }
  return true;
}

rtc::scoped_refptr<I420Buffer> VideoFrameBufferPool::CreateI420Buffer(
    int width,
    int height) {
  RTC_DCHECK_RUNS_SERIALIZED(&race_checker_);

  rtc::scoped_refptr<VideoFrameBuffer> existing_buffer =
      GetExistingBuffer(width, height, VideoFrameBuffer::Type::kI420);
  if (existing_buffer) {
    // Cast is safe because the only way kI420 buffer is created is
    // in the same function below, where |RefCountedObject<I420Buffer>| is
    // created.
    rtc::RefCountedObject<I420Buffer>* raw_buffer =
        static_cast<rtc::RefCountedObject<I420Buffer>*>(existing_buffer.get());
    // Creates a new scoped_refptr, which is also pointing to the same
    // RefCountedObject as buffer, increasing ref count.
    return rtc::scoped_refptr<I420Buffer>(raw_buffer);
  }

  if (buffers_.size() >= max_number_of_buffers_)
    return nullptr;
  // Allocate new buffer.
  rtc::scoped_refptr<I420Buffer> buffer =
      rtc::make_ref_counted<I420Buffer>(width, height);

  if (zero_initialize_)
    buffer->InitializeData();

  buffers_.push_back(buffer);
  return buffer;
}

rtc::scoped_refptr<NV12Buffer> VideoFrameBufferPool::CreateNV12Buffer(
    int width,
    int height) {
  RTC_DCHECK_RUNS_SERIALIZED(&race_checker_);

  rtc::scoped_refptr<VideoFrameBuffer> existing_buffer =
      GetExistingBuffer(width, height, VideoFrameBuffer::Type::kNV12);
  if (existing_buffer) {
    // Cast is safe because the only way kI420 buffer is created is
    // in the same function below, where |RefCountedObject<I420Buffer>| is
    // created.
    rtc::RefCountedObject<NV12Buffer>* raw_buffer =
        static_cast<rtc::RefCountedObject<NV12Buffer>*>(existing_buffer.get());
    // Creates a new scoped_refptr, which is also pointing to the same
    // RefCountedObject as buffer, increasing ref count.
    return rtc::scoped_refptr<NV12Buffer>(raw_buffer);
  }

  if (buffers_.size() >= max_number_of_buffers_)
    return nullptr;
  // Allocate new buffer.
  rtc::scoped_refptr<NV12Buffer> buffer =
      rtc::make_ref_counted<NV12Buffer>(width, height);

  if (zero_initialize_)
    buffer->InitializeData();

  buffers_.push_back(buffer);
  return buffer;
}

rtc::scoped_refptr<VideoFrameBuffer> VideoFrameBufferPool::GetExistingBuffer(
    int width,
    int height,
    VideoFrameBuffer::Type type) {
  // Release buffers with wrong resolution or different type.
  for (auto it = buffers_.begin(); it != buffers_.end();) {
    const auto& buffer = *it;
    if (buffer->width() != width || buffer->height() != height ||
        buffer->type() != type) {
      it = buffers_.erase(it);
    } else {
      ++it;
    }
  }
  // Look for a free buffer.
  for (const rtc::scoped_refptr<VideoFrameBuffer>& buffer : buffers_) {
    // If the buffer is in use, the ref count will be >= 2, one from the list we
    // are looping over and one from the application. If the ref count is 1,
    // then the list we are looping over holds the only reference and it's safe
    // to reuse.
    if (HasOneRef(buffer)) {
      RTC_CHECK(buffer->type() == type);
      return buffer;
    }
  }
  return nullptr;
}

}  // namespace webrtc
