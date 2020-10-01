// Copyright 2018 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

#include "base/memory/read_only_shared_memory_region.h"

#include <utility>

#include "build/build_config.h"

namespace base {

ReadOnlySharedMemoryRegion::CreateFunction*
    ReadOnlySharedMemoryRegion::create_hook_ = nullptr;

// static
MappedReadOnlyRegion ReadOnlySharedMemoryRegion::Create(size_t size) {
  if (create_hook_)
    return create_hook_(size);

  subtle::PlatformSharedMemoryRegion handle =
      subtle::PlatformSharedMemoryRegion::CreateWritable(size);
  if (!handle.IsValid())
    return {};

  void* memory_ptr = nullptr;
  size_t mapped_size = 0;
  if (!handle.MapAt(0, handle.GetSize(), &memory_ptr, &mapped_size))
    return {};

  WritableSharedMemoryMapping mapping(memory_ptr, size, mapped_size,
                                      handle.GetGUID());
#if defined(OS_MACOSX) && !defined(OS_IOS)
  handle.ConvertToReadOnly(memory_ptr);
#else
  handle.ConvertToReadOnly();
#endif  // defined(OS_MACOSX) && !defined(OS_IOS)
  ReadOnlySharedMemoryRegion region(std::move(handle));

  if (!region.IsValid() || !mapping.IsValid())
    return {};

  return {std::move(region), std::move(mapping)};
}

// static
ReadOnlySharedMemoryRegion ReadOnlySharedMemoryRegion::Deserialize(
    subtle::PlatformSharedMemoryRegion handle) {
  return ReadOnlySharedMemoryRegion(std::move(handle));
}

// static
subtle::PlatformSharedMemoryRegion
ReadOnlySharedMemoryRegion::TakeHandleForSerialization(
    ReadOnlySharedMemoryRegion region) {
  return std::move(region.handle_);
}

ReadOnlySharedMemoryRegion::ReadOnlySharedMemoryRegion() = default;
ReadOnlySharedMemoryRegion::ReadOnlySharedMemoryRegion(
    ReadOnlySharedMemoryRegion&& region) = default;
ReadOnlySharedMemoryRegion& ReadOnlySharedMemoryRegion::operator=(
    ReadOnlySharedMemoryRegion&& region) = default;
ReadOnlySharedMemoryRegion::~ReadOnlySharedMemoryRegion() = default;

ReadOnlySharedMemoryRegion ReadOnlySharedMemoryRegion::Duplicate() const {
  return ReadOnlySharedMemoryRegion(handle_.Duplicate());
}

ReadOnlySharedMemoryMapping ReadOnlySharedMemoryRegion::Map() const {
  return MapAt(0, handle_.GetSize());
}

ReadOnlySharedMemoryMapping ReadOnlySharedMemoryRegion::MapAt(
    off_t offset,
    size_t size) const {
  if (!IsValid())
    return {};

  void* memory = nullptr;
  size_t mapped_size = 0;
  if (!handle_.MapAt(offset, size, &memory, &mapped_size))
    return {};

  return ReadOnlySharedMemoryMapping(memory, size, mapped_size,
                                     handle_.GetGUID());
}

bool ReadOnlySharedMemoryRegion::IsValid() const {
  return handle_.IsValid();
}

ReadOnlySharedMemoryRegion::ReadOnlySharedMemoryRegion(
    subtle::PlatformSharedMemoryRegion handle)
    : handle_(std::move(handle)) {
  if (handle_.IsValid()) {
    CHECK_EQ(handle_.GetMode(),
             subtle::PlatformSharedMemoryRegion::Mode::kReadOnly);
  }
}

}  // namespace base
