// Copyright 2019 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

#include "base/android/scoped_hardware_buffer_fence_sync.h"

#include <utility>

namespace base {
namespace android {

ScopedHardwareBufferFenceSync::ScopedHardwareBufferFenceSync(
    ScopedHardwareBufferHandle handle,
    ScopedFD fence_fd)
    : handle_(std::move(handle)), fence_fd_(std::move(fence_fd)) {}

ScopedHardwareBufferFenceSync::~ScopedHardwareBufferFenceSync() = default;

ScopedHardwareBufferHandle ScopedHardwareBufferFenceSync::TakeBuffer() {
  return std::move(handle_);
}

ScopedFD ScopedHardwareBufferFenceSync::TakeFence() {
  return std::move(fence_fd_);
}

}  // namespace android
}  // namespace base
