/*
 *  Copyright (c) 2015 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "common_video/include/i420_buffer_pool.h"

#include <limits>

#include "rtc_base/checks.h"

namespace webrtc {

I420BufferPool::I420BufferPool() : I420BufferPool(false) {}
I420BufferPool::I420BufferPool(bool zero_initialize)
    : I420BufferPool(zero_initialize, std::numeric_limits<size_t>::max()) {}
I420BufferPool::I420BufferPool(bool zero_initialize,
                               size_t max_number_of_buffers)
    : zero_initialize_(zero_initialize),
      max_number_of_buffers_(max_number_of_buffers) {}
I420BufferPool::~I420BufferPool() = default;

void I420BufferPool::Release() {
  buffers_.clear();
}

bool I420BufferPool::Resize(size_t max_number_of_buffers) {
  RTC_DCHECK_RUNS_SERIALIZED(&race_checker_);
  size_t used_buffers_count = 0;
  for (const rtc::scoped_refptr<PooledI420Buffer>& buffer : buffers_) {
    // If the buffer is in use, the ref count will be >= 2, one from the list we
    // are looping over and one from the application. If the ref count is 1,
    // then the list we are looping over holds the only reference and it's safe
    // to reuse.
    if (!buffer->HasOneRef()) {
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
    if ((*iter)->HasOneRef()) {
      iter = buffers_.erase(iter);
      buffers_to_purge--;
    } else {
      ++iter;
    }
  }
  return true;
}

rtc::scoped_refptr<I420Buffer> I420BufferPool::CreateBuffer(int width,
                                                            int height) {
  // Default stride_y is width, default uv stride is width / 2 (rounding up).
  return CreateBuffer(width, height, width, (width + 1) / 2, (width + 1) / 2);
}

rtc::scoped_refptr<I420Buffer> I420BufferPool::CreateBuffer(int width,
                                                            int height,
                                                            int stride_y,
                                                            int stride_u,
                                                            int stride_v) {
  RTC_DCHECK_RUNS_SERIALIZED(&race_checker_);
  // Release buffers with wrong resolution.
  for (auto it = buffers_.begin(); it != buffers_.end();) {
    const auto& buffer = *it;
    if (buffer->width() != width || buffer->height() != height ||
        buffer->StrideY() != stride_y || buffer->StrideU() != stride_u ||
        buffer->StrideV() != stride_v) {
      it = buffers_.erase(it);
    } else {
      ++it;
    }
  }
  // Look for a free buffer.
  for (const rtc::scoped_refptr<PooledI420Buffer>& buffer : buffers_) {
    // If the buffer is in use, the ref count will be >= 2, one from the list we
    // are looping over and one from the application. If the ref count is 1,
    // then the list we are looping over holds the only reference and it's safe
    // to reuse.
    if (buffer->HasOneRef())
      return buffer;
  }

  if (buffers_.size() >= max_number_of_buffers_)
    return nullptr;
  // Allocate new buffer.
  rtc::scoped_refptr<PooledI420Buffer> buffer =
      new PooledI420Buffer(width, height, stride_y, stride_u, stride_v);
  if (zero_initialize_)
    buffer->InitializeData();
  buffers_.push_back(buffer);
  return buffer;
}

}  // namespace webrtc
