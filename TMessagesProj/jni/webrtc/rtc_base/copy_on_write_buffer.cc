/*
 *  Copyright 2016 The WebRTC Project Authors. All rights reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "rtc_base/copy_on_write_buffer.h"

#include <stddef.h>

namespace rtc {

CopyOnWriteBuffer::CopyOnWriteBuffer() : offset_(0), size_(0) {
  RTC_DCHECK(IsConsistent());
}

CopyOnWriteBuffer::CopyOnWriteBuffer(const CopyOnWriteBuffer& buf)
    : buffer_(buf.buffer_), offset_(buf.offset_), size_(buf.size_) {}

CopyOnWriteBuffer::CopyOnWriteBuffer(CopyOnWriteBuffer&& buf)
    : buffer_(std::move(buf.buffer_)), offset_(buf.offset_), size_(buf.size_) {
  buf.offset_ = 0;
  buf.size_ = 0;
  RTC_DCHECK(IsConsistent());
}

CopyOnWriteBuffer::CopyOnWriteBuffer(const std::string& s)
    : CopyOnWriteBuffer(s.data(), s.length()) {}

CopyOnWriteBuffer::CopyOnWriteBuffer(size_t size)
    : buffer_(size > 0 ? new RefCountedObject<Buffer>(size) : nullptr),
      offset_(0),
      size_(size) {
  RTC_DCHECK(IsConsistent());
}

CopyOnWriteBuffer::CopyOnWriteBuffer(size_t size, size_t capacity)
    : buffer_(size > 0 || capacity > 0
                  ? new RefCountedObject<Buffer>(size, capacity)
                  : nullptr),
      offset_(0),
      size_(size) {
  RTC_DCHECK(IsConsistent());
}

CopyOnWriteBuffer::~CopyOnWriteBuffer() = default;

bool CopyOnWriteBuffer::operator==(const CopyOnWriteBuffer& buf) const {
  // Must either be the same view of the same buffer or have the same contents.
  RTC_DCHECK(IsConsistent());
  RTC_DCHECK(buf.IsConsistent());
  return size_ == buf.size_ &&
         (cdata() == buf.cdata() || memcmp(cdata(), buf.cdata(), size_) == 0);
}

void CopyOnWriteBuffer::SetSize(size_t size) {
  RTC_DCHECK(IsConsistent());
  if (!buffer_) {
    if (size > 0) {
      buffer_ = new RefCountedObject<Buffer>(size);
      offset_ = 0;
      size_ = size;
    }
    RTC_DCHECK(IsConsistent());
    return;
  }

  if (size <= size_) {
    size_ = size;
    return;
  }

  UnshareAndEnsureCapacity(std::max(capacity(), size));
  buffer_->SetSize(size + offset_);
  size_ = size;
  RTC_DCHECK(IsConsistent());
}

void CopyOnWriteBuffer::EnsureCapacity(size_t new_capacity) {
  RTC_DCHECK(IsConsistent());
  if (!buffer_) {
    if (new_capacity > 0) {
      buffer_ = new RefCountedObject<Buffer>(0, new_capacity);
      offset_ = 0;
      size_ = 0;
    }
    RTC_DCHECK(IsConsistent());
    return;
  } else if (new_capacity <= capacity()) {
    return;
  }

  UnshareAndEnsureCapacity(new_capacity);
  RTC_DCHECK(IsConsistent());
}

void CopyOnWriteBuffer::Clear() {
  if (!buffer_)
    return;

  if (buffer_->HasOneRef()) {
    buffer_->Clear();
  } else {
    buffer_ = new RefCountedObject<Buffer>(0, capacity());
  }
  offset_ = 0;
  size_ = 0;
  RTC_DCHECK(IsConsistent());
}

void CopyOnWriteBuffer::UnshareAndEnsureCapacity(size_t new_capacity) {
  if (buffer_->HasOneRef() && new_capacity <= capacity()) {
    return;
  }

  buffer_ = new RefCountedObject<Buffer>(buffer_->data() + offset_, size_,
                                         new_capacity);
  offset_ = 0;
  RTC_DCHECK(IsConsistent());
}

}  // namespace rtc
