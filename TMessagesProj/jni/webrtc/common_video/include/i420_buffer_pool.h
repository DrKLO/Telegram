/*
 *  Copyright (c) 2015 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#ifndef COMMON_VIDEO_INCLUDE_I420_BUFFER_POOL_H_
#define COMMON_VIDEO_INCLUDE_I420_BUFFER_POOL_H_

#include <stddef.h>

#include <list>

#include "api/scoped_refptr.h"
#include "api/video/i420_buffer.h"
#include "rtc_base/race_checker.h"
#include "rtc_base/ref_counted_object.h"

namespace webrtc {

// Simple buffer pool to avoid unnecessary allocations of I420Buffer objects.
// The pool manages the memory of the I420Buffer returned from CreateBuffer.
// When the I420Buffer is destructed, the memory is returned to the pool for use
// by subsequent calls to CreateBuffer. If the resolution passed to CreateBuffer
// changes, old buffers will be purged from the pool.
// Note that CreateBuffer will crash if more than kMaxNumberOfFramesBeforeCrash
// are created. This is to prevent memory leaks where frames are not returned.
class I420BufferPool {
 public:
  I420BufferPool();
  explicit I420BufferPool(bool zero_initialize);
  I420BufferPool(bool zero_initialze, size_t max_number_of_buffers);
  ~I420BufferPool();

  // Returns a buffer from the pool. If no suitable buffer exist in the pool
  // and there are less than |max_number_of_buffers| pending, a buffer is
  // created. Returns null otherwise.
  rtc::scoped_refptr<I420Buffer> CreateBuffer(int width, int height);

  // Returns a buffer from the pool with the explicitly specified stride.
  rtc::scoped_refptr<I420Buffer> CreateBuffer(int width,
                                              int height,
                                              int stride_y,
                                              int stride_u,
                                              int stride_v);

  // Changes the max amount of buffers in the pool to the new value.
  // Returns true if change was successful and false if the amount of already
  // allocated buffers is bigger than new value.
  bool Resize(size_t max_number_of_buffers);

  // Clears buffers_ and detaches the thread checker so that it can be reused
  // later from another thread.
  void Release();

 private:
  // Explicitly use a RefCountedObject to get access to HasOneRef,
  // needed by the pool to check exclusive access.
  using PooledI420Buffer = rtc::RefCountedObject<I420Buffer>;

  rtc::RaceChecker race_checker_;
  std::list<rtc::scoped_refptr<PooledI420Buffer>> buffers_;
  // If true, newly allocated buffers are zero-initialized. Note that recycled
  // buffers are not zero'd before reuse. This is required of buffers used by
  // FFmpeg according to http://crbug.com/390941, which only requires it for the
  // initial allocation (as shown by FFmpeg's own buffer allocation code). It
  // has to do with "Use-of-uninitialized-value" on "Linux_msan_chrome".
  const bool zero_initialize_;
  // Max number of buffers this pool can have pending.
  size_t max_number_of_buffers_;
};

}  // namespace webrtc

#endif  // COMMON_VIDEO_INCLUDE_I420_BUFFER_POOL_H_
