// Copyright 2018 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

#include "base/android/scoped_hardware_buffer_handle.h"

#include "base/android/android_hardware_buffer_compat.h"
#include "base/logging.h"
#include "base/posix/unix_domain_socket.h"

namespace base {
namespace android {

ScopedHardwareBufferHandle::ScopedHardwareBufferHandle() = default;

ScopedHardwareBufferHandle::ScopedHardwareBufferHandle(
    ScopedHardwareBufferHandle&& other) {
  *this = std::move(other);
}

ScopedHardwareBufferHandle::~ScopedHardwareBufferHandle() {
  reset();
}

// static
ScopedHardwareBufferHandle ScopedHardwareBufferHandle::Adopt(
    AHardwareBuffer* buffer) {
  return ScopedHardwareBufferHandle(buffer);
}

// static
ScopedHardwareBufferHandle ScopedHardwareBufferHandle::Create(
    AHardwareBuffer* buffer) {
  AndroidHardwareBufferCompat::GetInstance().Acquire(buffer);
  return ScopedHardwareBufferHandle(buffer);
}

ScopedHardwareBufferHandle& ScopedHardwareBufferHandle::operator=(
    ScopedHardwareBufferHandle&& other) {
  reset();
  std::swap(buffer_, other.buffer_);
  return *this;
}

bool ScopedHardwareBufferHandle::is_valid() const {
  return buffer_ != nullptr;
}

AHardwareBuffer* ScopedHardwareBufferHandle::get() const {
  return buffer_;
}

void ScopedHardwareBufferHandle::reset() {
  if (buffer_) {
    AndroidHardwareBufferCompat::GetInstance().Release(buffer_);
    buffer_ = nullptr;
  }
}

AHardwareBuffer* ScopedHardwareBufferHandle::Take() {
  AHardwareBuffer* buffer = nullptr;
  std::swap(buffer, buffer_);
  return buffer;
}

ScopedHardwareBufferHandle ScopedHardwareBufferHandle::Clone() const {
  DCHECK(buffer_);
  AndroidHardwareBufferCompat::GetInstance().Acquire(buffer_);
  return ScopedHardwareBufferHandle(buffer_);
}

ScopedFD ScopedHardwareBufferHandle::SerializeAsFileDescriptor() const {
  DCHECK(is_valid());

  ScopedFD reader, writer;
  if (!CreateSocketPair(&reader, &writer)) {
    PLOG(ERROR) << "socketpair";
    return ScopedFD();
  }

  // NOTE: SendHandleToUnixSocket does NOT acquire or retain a reference to the
  // buffer object. The caller is therefore responsible for ensuring that the
  // buffer remains alive through the lifetime of this file descriptor.
  int result =
      AndroidHardwareBufferCompat::GetInstance().SendHandleToUnixSocket(
          buffer_, writer.get());
  if (result < 0) {
    PLOG(ERROR) << "send";
    return ScopedFD();
  }

  return reader;
}

// static
ScopedHardwareBufferHandle
ScopedHardwareBufferHandle::DeserializeFromFileDescriptor(ScopedFD fd) {
  DCHECK(fd.is_valid());
  DCHECK(AndroidHardwareBufferCompat::IsSupportAvailable());
  AHardwareBuffer* buffer = nullptr;

  // NOTE: Upon success, RecvHandleFromUnixSocket acquires a new reference to
  // the AHardwareBuffer.
  int result =
      AndroidHardwareBufferCompat::GetInstance().RecvHandleFromUnixSocket(
          fd.get(), &buffer);
  if (result < 0) {
    PLOG(ERROR) << "recv";
    return ScopedHardwareBufferHandle();
  }

  return ScopedHardwareBufferHandle(buffer);
}

ScopedHardwareBufferHandle::ScopedHardwareBufferHandle(AHardwareBuffer* buffer)
    : buffer_(buffer) {
  DCHECK(AndroidHardwareBufferCompat::IsSupportAvailable());
}

}  // namespace android
}  // namespace base
