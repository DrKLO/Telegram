/*
 *  Copyright 2019 The WebRTC Project Authors. All rights reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "rtc_base/memory/fifo_buffer.h"

#include <algorithm>

#include "rtc_base/thread.h"

namespace rtc {

FifoBuffer::FifoBuffer(size_t size)
    : state_(SS_OPEN),
      buffer_(new char[size]),
      buffer_length_(size),
      data_length_(0),
      read_position_(0),
      owner_(Thread::Current()) {
  // all events are done on the owner_ thread
}

FifoBuffer::FifoBuffer(size_t size, Thread* owner)
    : state_(SS_OPEN),
      buffer_(new char[size]),
      buffer_length_(size),
      data_length_(0),
      read_position_(0),
      owner_(owner) {
  // all events are done on the owner_ thread
}

FifoBuffer::~FifoBuffer() {}

bool FifoBuffer::GetBuffered(size_t* size) const {
  webrtc::MutexLock lock(&mutex_);
  *size = data_length_;
  return true;
}

bool FifoBuffer::SetCapacity(size_t size) {
  webrtc::MutexLock lock(&mutex_);
  if (data_length_ > size) {
    return false;
  }

  if (size != buffer_length_) {
    char* buffer = new char[size];
    const size_t copy = data_length_;
    const size_t tail_copy = std::min(copy, buffer_length_ - read_position_);
    memcpy(buffer, &buffer_[read_position_], tail_copy);
    memcpy(buffer + tail_copy, &buffer_[0], copy - tail_copy);
    buffer_.reset(buffer);
    read_position_ = 0;
    buffer_length_ = size;
  }
  return true;
}

StreamResult FifoBuffer::ReadOffset(void* buffer,
                                    size_t bytes,
                                    size_t offset,
                                    size_t* bytes_read) {
  webrtc::MutexLock lock(&mutex_);
  return ReadOffsetLocked(buffer, bytes, offset, bytes_read);
}

StreamResult FifoBuffer::WriteOffset(const void* buffer,
                                     size_t bytes,
                                     size_t offset,
                                     size_t* bytes_written) {
  webrtc::MutexLock lock(&mutex_);
  return WriteOffsetLocked(buffer, bytes, offset, bytes_written);
}

StreamState FifoBuffer::GetState() const {
  webrtc::MutexLock lock(&mutex_);
  return state_;
}

StreamResult FifoBuffer::Read(void* buffer,
                              size_t bytes,
                              size_t* bytes_read,
                              int* error) {
  webrtc::MutexLock lock(&mutex_);
  const bool was_writable = data_length_ < buffer_length_;
  size_t copy = 0;
  StreamResult result = ReadOffsetLocked(buffer, bytes, 0, &copy);

  if (result == SR_SUCCESS) {
    // If read was successful then adjust the read position and number of
    // bytes buffered.
    read_position_ = (read_position_ + copy) % buffer_length_;
    data_length_ -= copy;
    if (bytes_read) {
      *bytes_read = copy;
    }

    // if we were full before, and now we're not, post an event
    if (!was_writable && copy > 0) {
      PostEvent(SE_WRITE, 0);
    }
  }
  return result;
}

StreamResult FifoBuffer::Write(const void* buffer,
                               size_t bytes,
                               size_t* bytes_written,
                               int* error) {
  webrtc::MutexLock lock(&mutex_);

  const bool was_readable = (data_length_ > 0);
  size_t copy = 0;
  StreamResult result = WriteOffsetLocked(buffer, bytes, 0, &copy);

  if (result == SR_SUCCESS) {
    // If write was successful then adjust the number of readable bytes.
    data_length_ += copy;
    if (bytes_written) {
      *bytes_written = copy;
    }

    // if we didn't have any data to read before, and now we do, post an event
    if (!was_readable && copy > 0) {
      PostEvent(SE_READ, 0);
    }
  }
  return result;
}

void FifoBuffer::Close() {
  webrtc::MutexLock lock(&mutex_);
  state_ = SS_CLOSED;
}

const void* FifoBuffer::GetReadData(size_t* size) {
  webrtc::MutexLock lock(&mutex_);
  *size = (read_position_ + data_length_ <= buffer_length_)
              ? data_length_
              : buffer_length_ - read_position_;
  return &buffer_[read_position_];
}

void FifoBuffer::ConsumeReadData(size_t size) {
  webrtc::MutexLock lock(&mutex_);
  RTC_DCHECK(size <= data_length_);
  const bool was_writable = data_length_ < buffer_length_;
  read_position_ = (read_position_ + size) % buffer_length_;
  data_length_ -= size;
  if (!was_writable && size > 0) {
    PostEvent(SE_WRITE, 0);
  }
}

void* FifoBuffer::GetWriteBuffer(size_t* size) {
  webrtc::MutexLock lock(&mutex_);
  if (state_ == SS_CLOSED) {
    return nullptr;
  }

  // if empty, reset the write position to the beginning, so we can get
  // the biggest possible block
  if (data_length_ == 0) {
    read_position_ = 0;
  }

  const size_t write_position =
      (read_position_ + data_length_) % buffer_length_;
  *size = (write_position > read_position_ || data_length_ == 0)
              ? buffer_length_ - write_position
              : read_position_ - write_position;
  return &buffer_[write_position];
}

void FifoBuffer::ConsumeWriteBuffer(size_t size) {
  webrtc::MutexLock lock(&mutex_);
  RTC_DCHECK(size <= buffer_length_ - data_length_);
  const bool was_readable = (data_length_ > 0);
  data_length_ += size;
  if (!was_readable && size > 0) {
    PostEvent(SE_READ, 0);
  }
}

bool FifoBuffer::GetWriteRemaining(size_t* size) const {
  webrtc::MutexLock lock(&mutex_);
  *size = buffer_length_ - data_length_;
  return true;
}

StreamResult FifoBuffer::ReadOffsetLocked(void* buffer,
                                          size_t bytes,
                                          size_t offset,
                                          size_t* bytes_read) {
  if (offset >= data_length_) {
    return (state_ != SS_CLOSED) ? SR_BLOCK : SR_EOS;
  }

  const size_t available = data_length_ - offset;
  const size_t read_position = (read_position_ + offset) % buffer_length_;
  const size_t copy = std::min(bytes, available);
  const size_t tail_copy = std::min(copy, buffer_length_ - read_position);
  char* const p = static_cast<char*>(buffer);
  memcpy(p, &buffer_[read_position], tail_copy);
  memcpy(p + tail_copy, &buffer_[0], copy - tail_copy);

  if (bytes_read) {
    *bytes_read = copy;
  }
  return SR_SUCCESS;
}

StreamResult FifoBuffer::WriteOffsetLocked(const void* buffer,
                                           size_t bytes,
                                           size_t offset,
                                           size_t* bytes_written) {
  if (state_ == SS_CLOSED) {
    return SR_EOS;
  }

  if (data_length_ + offset >= buffer_length_) {
    return SR_BLOCK;
  }

  const size_t available = buffer_length_ - data_length_ - offset;
  const size_t write_position =
      (read_position_ + data_length_ + offset) % buffer_length_;
  const size_t copy = std::min(bytes, available);
  const size_t tail_copy = std::min(copy, buffer_length_ - write_position);
  const char* const p = static_cast<const char*>(buffer);
  memcpy(&buffer_[write_position], p, tail_copy);
  memcpy(&buffer_[0], p + tail_copy, copy - tail_copy);

  if (bytes_written) {
    *bytes_written = copy;
  }
  return SR_SUCCESS;
}

}  // namespace rtc
