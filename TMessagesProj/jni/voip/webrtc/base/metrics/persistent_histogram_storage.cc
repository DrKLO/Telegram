// Copyright 2018 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

#include "base/metrics/persistent_histogram_storage.h"

#include "base/files/file_util.h"
#include "base/files/important_file_writer.h"
#include "base/logging.h"
#include "base/metrics/persistent_histogram_allocator.h"
#include "base/metrics/persistent_memory_allocator.h"
#include "base/process/memory.h"
#include "base/strings/string_util.h"
#include "base/strings/stringprintf.h"
#include "base/time/time.h"
#include "build/build_config.h"

#if defined(OS_WIN)
#include <windows.h>
// Dummy line to stop `git cl format` from reordering these includes.
#include <memoryapi.h>
#elif defined(OS_POSIX) || defined(OS_FUCHSIA)
#include <sys/mman.h>
#endif

namespace {

constexpr size_t kAllocSize = 1 << 20;  // 1 MiB

void* AllocateLocalMemory(size_t size) {
  void* address;

#if defined(OS_WIN)
  address =
      ::VirtualAlloc(nullptr, size, MEM_RESERVE | MEM_COMMIT, PAGE_READWRITE);
  if (address)
    return address;
#elif defined(OS_POSIX) || defined(OS_FUCHSIA)
  // MAP_ANON is deprecated on Linux but MAP_ANONYMOUS is not universal on Mac.
  // MAP_SHARED is not available on Linux <2.4 but required on Mac.
  address = ::mmap(nullptr, size, PROT_READ | PROT_WRITE, MAP_ANON | MAP_SHARED,
                   -1, 0);
  if (address != MAP_FAILED)
    return address;
#else
#error This architecture is not (yet) supported.
#endif

  // As a last resort, just allocate the memory from the heap. This will
  // achieve the same basic result but the acquired memory has to be
  // explicitly zeroed and thus realized immediately (i.e. all pages are
  // added to the process now instead of only when first accessed).
  if (!base::UncheckedMalloc(size, &address))
    return nullptr;
  DCHECK(address);
  memset(address, 0, size);
  return address;
}

}  // namespace

namespace base {

PersistentHistogramStorage::PersistentHistogramStorage(
    StringPiece allocator_name,
    StorageDirManagement storage_dir_management)
    : storage_dir_management_(storage_dir_management) {
  DCHECK(!allocator_name.empty());
  DCHECK(IsStringASCII(allocator_name));

  // This code may be executed before crash handling and/or OOM handling has
  // been initialized for the process. Silently ignore a failed allocation
  // (no metric persistence) rather that generating a crash that won't be
  // caught/reported.
  void* memory = AllocateLocalMemory(kAllocSize);
  if (!memory)
    return;

  GlobalHistogramAllocator::CreateWithPersistentMemory(memory, kAllocSize, 0,
                                                       0,  // No identifier.
                                                       allocator_name);
  GlobalHistogramAllocator::Get()->CreateTrackingHistograms(allocator_name);
}

PersistentHistogramStorage::~PersistentHistogramStorage() {
  PersistentHistogramAllocator* allocator = GlobalHistogramAllocator::Get();
  if (!allocator)
    return;

  allocator->UpdateTrackingHistograms();

  if (disabled_)
    return;

  // Stop if the storage base directory has not been properly set.
  if (storage_base_dir_.empty()) {
    LOG(ERROR)
        << "Could not write \"" << allocator->Name()
        << "\" persistent histograms to file as the storage base directory "
           "is not properly set.";
    return;
  }

  FilePath storage_dir = storage_base_dir_.AppendASCII(allocator->Name());

  switch (storage_dir_management_) {
    case StorageDirManagement::kCreate:
      if (!CreateDirectory(storage_dir)) {
        LOG(ERROR)
            << "Could not write \"" << allocator->Name()
            << "\" persistent histograms to file as the storage directory "
               "cannot be created.";
        return;
      }
      break;
    case StorageDirManagement::kUseExisting:
      if (!DirectoryExists(storage_dir)) {
        // When the consumer of this class decides to use an existing storage
        // directory, it should ensure the directory's existence if it's
        // essential.
        LOG(ERROR)
            << "Could not write \"" << allocator->Name()
            << "\" persistent histograms to file as the storage directory "
               "does not exist.";
        return;
      }
      break;
  }

  // Save data using the current time as the filename. The actual filename
  // doesn't matter (so long as it ends with the correct extension) but this
  // works as well as anything.
  Time::Exploded exploded;
  Time::Now().LocalExplode(&exploded);
  const FilePath file_path =
      storage_dir
          .AppendASCII(StringPrintf("%04d%02d%02d%02d%02d%02d", exploded.year,
                                    exploded.month, exploded.day_of_month,
                                    exploded.hour, exploded.minute,
                                    exploded.second))
          .AddExtension(PersistentMemoryAllocator::kFileExtension);

  StringPiece contents(static_cast<const char*>(allocator->data()),
                       allocator->used());
  if (!ImportantFileWriter::WriteFileAtomically(file_path, contents)) {
    LOG(ERROR) << "Persistent histograms fail to write to file: "
               << file_path.value();
  }
}

}  // namespace base
