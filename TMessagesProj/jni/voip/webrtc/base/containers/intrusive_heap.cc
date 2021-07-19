// Copyright 2018 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

#include "base/containers/intrusive_heap.h"

#include "base/logging.h"
#include "base/memory/ptr_util.h"

namespace base {

////////////////////////////////////////////////////////////////////////////////
// HeapHandle

// static
HeapHandle HeapHandle::Invalid() {
  return HeapHandle();
}

////////////////////////////////////////////////////////////////////////////////
// InternalHeapHandleStorage

InternalHeapHandleStorage::InternalHeapHandleStorage()
    : handle_(new HeapHandle()) {}

InternalHeapHandleStorage::InternalHeapHandleStorage(
    InternalHeapHandleStorage&& other) noexcept
    : handle_(std::move(other.handle_)) {
  DCHECK(intrusive_heap::IsInvalid(other.handle_));
}

InternalHeapHandleStorage::~InternalHeapHandleStorage() = default;

InternalHeapHandleStorage& InternalHeapHandleStorage::operator=(
    InternalHeapHandleStorage&& other) noexcept {
  handle_ = std::move(other.handle_);
  DCHECK(intrusive_heap::IsInvalid(other.handle_));
  return *this;
}

void InternalHeapHandleStorage::swap(
    InternalHeapHandleStorage& other) noexcept {
  std::swap(handle_, other.handle_);
}

}  // namespace base
