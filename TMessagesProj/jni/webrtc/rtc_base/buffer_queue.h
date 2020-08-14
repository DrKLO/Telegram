/*
 *  Copyright 2015 The WebRTC Project Authors. All rights reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#ifndef RTC_BASE_BUFFER_QUEUE_H_
#define RTC_BASE_BUFFER_QUEUE_H_

#include <stddef.h>

#include <deque>
#include <vector>

#include "rtc_base/buffer.h"
#include "rtc_base/constructor_magic.h"
#include "rtc_base/synchronization/mutex.h"
#include "rtc_base/thread_annotations.h"

namespace rtc {

class BufferQueue {
 public:
  // Creates a buffer queue with a given capacity and default buffer size.
  BufferQueue(size_t capacity, size_t default_size);
  virtual ~BufferQueue();

  // Return number of queued buffers.
  size_t size() const;

  // Clear the BufferQueue by moving all Buffers from |queue_| to |free_list_|.
  void Clear();

  // ReadFront will only read one buffer at a time and will truncate buffers
  // that don't fit in the passed memory.
  // Returns true unless no data could be returned.
  bool ReadFront(void* data, size_t bytes, size_t* bytes_read);

  // WriteBack always writes either the complete memory or nothing.
  // Returns true unless no data could be written.
  bool WriteBack(const void* data, size_t bytes, size_t* bytes_written);

 protected:
  // These methods are called when the state of the queue changes.
  virtual void NotifyReadableForTest() {}
  virtual void NotifyWritableForTest() {}

 private:
  size_t capacity_;
  size_t default_size_;
  mutable webrtc::Mutex mutex_;
  std::deque<Buffer*> queue_ RTC_GUARDED_BY(mutex_);
  std::vector<Buffer*> free_list_ RTC_GUARDED_BY(mutex_);

  RTC_DISALLOW_COPY_AND_ASSIGN(BufferQueue);
};

}  // namespace rtc

#endif  // RTC_BASE_BUFFER_QUEUE_H_
