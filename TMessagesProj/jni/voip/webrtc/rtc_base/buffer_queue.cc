/*
 *  Copyright 2015 The WebRTC Project Authors. All rights reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "rtc_base/buffer_queue.h"

#include <stdint.h>
#include <string.h>

#include <algorithm>

namespace rtc {

BufferQueue::BufferQueue(size_t capacity, size_t default_size)
    : capacity_(capacity), default_size_(default_size) {}

BufferQueue::~BufferQueue() {
  webrtc::MutexLock lock(&mutex_);

  for (Buffer* buffer : queue_) {
    delete buffer;
  }
  for (Buffer* buffer : free_list_) {
    delete buffer;
  }
}

size_t BufferQueue::size() const {
  webrtc::MutexLock lock(&mutex_);
  return queue_.size();
}

void BufferQueue::Clear() {
  webrtc::MutexLock lock(&mutex_);
  while (!queue_.empty()) {
    free_list_.push_back(queue_.front());
    queue_.pop_front();
  }
}

bool BufferQueue::ReadFront(void* buffer, size_t bytes, size_t* bytes_read) {
  webrtc::MutexLock lock(&mutex_);
  if (queue_.empty()) {
    return false;
  }

  bool was_writable = queue_.size() < capacity_;
  Buffer* packet = queue_.front();
  queue_.pop_front();

  bytes = std::min(bytes, packet->size());
  memcpy(buffer, packet->data(), bytes);
  if (bytes_read) {
    *bytes_read = bytes;
  }
  free_list_.push_back(packet);
  if (!was_writable) {
    NotifyWritableForTest();
  }
  return true;
}

bool BufferQueue::WriteBack(const void* buffer,
                            size_t bytes,
                            size_t* bytes_written) {
  webrtc::MutexLock lock(&mutex_);
  if (queue_.size() == capacity_) {
    return false;
  }

  bool was_readable = !queue_.empty();
  Buffer* packet;
  if (!free_list_.empty()) {
    packet = free_list_.back();
    free_list_.pop_back();
  } else {
    packet = new Buffer(bytes, default_size_);
  }

  packet->SetData(static_cast<const uint8_t*>(buffer), bytes);
  if (bytes_written) {
    *bytes_written = bytes;
  }
  queue_.push_back(packet);
  if (!was_readable) {
    NotifyReadableForTest();
  }
  return true;
}

}  // namespace rtc
