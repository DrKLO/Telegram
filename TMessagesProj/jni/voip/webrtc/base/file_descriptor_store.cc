// Copyright 2017 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

#include "base/file_descriptor_store.h"

#include <utility>

#include "base/logging.h"

namespace base {

FileDescriptorStore::Descriptor::Descriptor(const std::string& key,
                                            base::ScopedFD fd)
    : key(key),
      fd(std::move(fd)),
      region(base::MemoryMappedFile::Region::kWholeFile) {}

FileDescriptorStore::Descriptor::Descriptor(
    const std::string& key,
    base::ScopedFD fd,
    base::MemoryMappedFile::Region region)
    : key(key), fd(std::move(fd)), region(region) {}

FileDescriptorStore::Descriptor::Descriptor(
    FileDescriptorStore::Descriptor&& other)
    : key(other.key), fd(std::move(other.fd)), region(other.region) {}

FileDescriptorStore::Descriptor::~Descriptor() = default;

// static
FileDescriptorStore& FileDescriptorStore::GetInstance() {
  static FileDescriptorStore* store = new FileDescriptorStore;
  return *store;
}

base::ScopedFD FileDescriptorStore::TakeFD(
    const std::string& key,
    base::MemoryMappedFile::Region* region) {
  base::ScopedFD fd = MaybeTakeFD(key, region);
  if (!fd.is_valid())
    DLOG(DCHECK) << "Unknown global descriptor: " << key;
  return fd;
}

base::ScopedFD FileDescriptorStore::MaybeTakeFD(
    const std::string& key,
    base::MemoryMappedFile::Region* region) {
  auto iter = descriptors_.find(key);
  if (iter == descriptors_.end())
    return base::ScopedFD();
  *region = iter->second.region;
  base::ScopedFD result = std::move(iter->second.fd);
  descriptors_.erase(iter);
  return result;
}

void FileDescriptorStore::Set(const std::string& key, base::ScopedFD fd) {
  Set(key, std::move(fd), base::MemoryMappedFile::Region::kWholeFile);
}

void FileDescriptorStore::Set(const std::string& key,
                              base::ScopedFD fd,
                              base::MemoryMappedFile::Region region) {
  Descriptor descriptor(key, std::move(fd), region);
  descriptors_.insert(std::make_pair(key, std::move(descriptor)));
}

FileDescriptorStore::FileDescriptorStore() = default;

FileDescriptorStore::~FileDescriptorStore() = default;

}  // namespace base
