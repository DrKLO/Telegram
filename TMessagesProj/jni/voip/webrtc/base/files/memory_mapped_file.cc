// Copyright 2013 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

#include "base/files/memory_mapped_file.h"

#include <utility>

#include "base/files/file_path.h"
#include "base/logging.h"
#include "base/numerics/safe_math.h"
#include "base/system/sys_info.h"
#include "build/build_config.h"

namespace base {

const MemoryMappedFile::Region MemoryMappedFile::Region::kWholeFile = {0, 0};

bool MemoryMappedFile::Region::operator==(
    const MemoryMappedFile::Region& other) const {
  return other.offset == offset && other.size == size;
}

bool MemoryMappedFile::Region::operator!=(
    const MemoryMappedFile::Region& other) const {
  return other.offset != offset || other.size != size;
}

MemoryMappedFile::~MemoryMappedFile() {
  CloseHandles();
}

#if !defined(OS_NACL)
bool MemoryMappedFile::Initialize(const FilePath& file_name, Access access) {
  if (IsValid())
    return false;

  uint32_t flags = 0;
  switch (access) {
    case READ_ONLY:
      flags = File::FLAG_OPEN | File::FLAG_READ;
      break;
    case READ_WRITE:
      flags = File::FLAG_OPEN | File::FLAG_READ | File::FLAG_WRITE;
      break;
    case READ_WRITE_EXTEND:
      // Can't open with "extend" because no maximum size is known.
      NOTREACHED();
      break;
#if defined(OS_WIN)
    case READ_CODE_IMAGE:
      flags |= File::FLAG_OPEN | File::FLAG_READ | File::FLAG_EXCLUSIVE_WRITE |
               File::FLAG_EXECUTE;
      break;
#endif
  }
  file_.Initialize(file_name, flags);

  if (!file_.IsValid()) {
    DLOG(ERROR) << "Couldn't open " << file_name.AsUTF8Unsafe();
    return false;
  }

  if (!MapFileRegionToMemory(Region::kWholeFile, access)) {
    CloseHandles();
    return false;
  }

  return true;
}

bool MemoryMappedFile::Initialize(File file, Access access) {
  DCHECK_NE(READ_WRITE_EXTEND, access);
  return Initialize(std::move(file), Region::kWholeFile, access);
}

bool MemoryMappedFile::Initialize(File file,
                                  const Region& region,
                                  Access access) {
  switch (access) {
    case READ_WRITE_EXTEND:
      DCHECK(Region::kWholeFile != region);
      {
        CheckedNumeric<int64_t> region_end(region.offset);
        region_end += region.size;
        if (!region_end.IsValid()) {
          DLOG(ERROR) << "Region bounds exceed maximum for base::File.";
          return false;
        }
      }
      FALLTHROUGH;
    case READ_ONLY:
    case READ_WRITE:
      // Ensure that the region values are valid.
      if (region.offset < 0) {
        DLOG(ERROR) << "Region bounds are not valid.";
        return false;
      }
      break;
#if defined(OS_WIN)
    case READ_CODE_IMAGE:
      // Can't open with "READ_CODE_IMAGE", not supported outside Windows
      // or with a |region|.
      NOTREACHED();
      break;
#endif
  }

  if (IsValid())
    return false;

  if (region != Region::kWholeFile)
    DCHECK_GE(region.offset, 0);

  file_ = std::move(file);

  if (!MapFileRegionToMemory(region, access)) {
    CloseHandles();
    return false;
  }

  return true;
}

bool MemoryMappedFile::IsValid() const {
  return data_ != nullptr;
}

// static
void MemoryMappedFile::CalculateVMAlignedBoundaries(int64_t start,
                                                    size_t size,
                                                    int64_t* aligned_start,
                                                    size_t* aligned_size,
                                                    int32_t* offset) {
  // Sadly, on Windows, the mmap alignment is not just equal to the page size.
  auto mask = SysInfo::VMAllocationGranularity() - 1;
  DCHECK(IsValueInRangeForNumericType<int32_t>(mask));
  *offset = start & mask;
  *aligned_start = start & ~mask;
  *aligned_size = (size + *offset + mask) & ~mask;
}
#endif  // !defined(OS_NACL)

}  // namespace base
