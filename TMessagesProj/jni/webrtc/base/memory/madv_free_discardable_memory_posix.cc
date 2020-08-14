// Copyright 2019 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

#include <errno.h>
#include <inttypes.h>
#include <sys/mman.h>
#include <sys/types.h>
#include <sys/utsname.h>

#include <atomic>

#include "base/atomicops.h"
#include "base/bits.h"
#include "base/callback.h"
#include "base/memory/madv_free_discardable_memory_allocator_posix.h"
#include "base/memory/madv_free_discardable_memory_posix.h"
#include "base/process/process_metrics.h"
#include "base/strings/string_number_conversions.h"
#include "base/strings/stringprintf.h"
#include "base/trace_event/memory_allocator_dump.h"
#include "base/trace_event/memory_dump_manager.h"

#if defined(ADDRESS_SANITIZER)
#include <sanitizer/asan_interface.h>
#endif  // defined(ADDRESS_SANITIZER)

namespace {

constexpr intptr_t kPageMagicCookie = 1;

void* AllocatePages(size_t size_in_pages) {
  void* data = mmap(nullptr, size_in_pages * base::GetPageSize(),
                    PROT_READ | PROT_WRITE, MAP_ANONYMOUS | MAP_PRIVATE, -1, 0);
  PCHECK(data != MAP_FAILED);
  return data;
}

// Checks if the system supports usage of MADV_FREE as a backing for discardable
// memory.
base::MadvFreeSupport ProbePlatformMadvFreeSupport() {
  // Note: If the compiling system does not have headers for Linux 4.5+, then
  // the MADV_FREE define will not exist and the probe will default to
  // unsupported, regardless of whether the target system actually supports
  // MADV_FREE.
#if !defined(OS_MACOSX) && defined(MADV_FREE)
  uint8_t* dummy_page = static_cast<uint8_t*>(AllocatePages(1));
  dummy_page[0] = 1;

  base::MadvFreeSupport support = base::MadvFreeSupport::kUnsupported;

  // Check if the MADV_FREE advice value exists.
  int retval = madvise(dummy_page, base::GetPageSize(), MADV_FREE);
  if (!retval) {
    // For Linux 4.5 to 4.12, MADV_FREE on a swapless system will lead to memory
    // being immediately discarded. Verify that the memory was not discarded.
    if (dummy_page[0]) {
      support = base::MadvFreeSupport::kSupported;
    }
  }
  PCHECK(!munmap(dummy_page, base::GetPageSize()));
  return support;
#endif

  return base::MadvFreeSupport::kUnsupported;
}

}  // namespace

namespace base {

MadvFreeDiscardableMemoryPosix::MadvFreeDiscardableMemoryPosix(
    size_t size_in_bytes,
    std::atomic<size_t>* allocator_byte_count)
    : size_in_bytes_(size_in_bytes),
      allocated_pages_((size_in_bytes_ + base::GetPageSize() - 1) /
                       base::GetPageSize()),
      allocator_byte_count_(allocator_byte_count),
      page_first_word_((size_in_bytes_ + base::GetPageSize() - 1) /
                       base::GetPageSize()) {
  data_ = AllocatePages(allocated_pages_);
  (*allocator_byte_count_) += size_in_bytes_;
}

MadvFreeDiscardableMemoryPosix::~MadvFreeDiscardableMemoryPosix() {
  if (Deallocate()) {
    DVLOG(1) << "Region evicted during destructor with " << allocated_pages_
             << " pages";
  }
}

bool MadvFreeDiscardableMemoryPosix::Lock() {
  DFAKE_SCOPED_LOCK(thread_collision_warner_);
  DCHECK(!is_locked_);
  // Locking fails if the memory has been deallocated.
  if (!data_)
    return false;

#if defined(ADDRESS_SANITIZER)
  // We need to unpoison here since locking pages writes to them.
  // Note that even if locking fails, we want to unpoison anyways after
  // deallocation.
  ASAN_UNPOISON_MEMORY_REGION(data_, allocated_pages_ * base::GetPageSize());
#endif  // defined(ADDRESS_SANITIZER)

  size_t page_index;
  for (page_index = 0; page_index < allocated_pages_; ++page_index) {
    if (!LockPage(page_index))
      break;
  }

  if (page_index < allocated_pages_) {
    DVLOG(1) << "Region eviction discovered during lock with "
             << allocated_pages_ << " pages";
    Deallocate();
    return false;
  }
  DCHECK(IsResident());

  is_locked_ = true;
  return true;
}

void MadvFreeDiscardableMemoryPosix::Unlock() {
  DFAKE_SCOPED_LOCK(thread_collision_warner_);
  DCHECK(is_locked_);
  DCHECK(data_ != nullptr);

  for (size_t page_index = 0; page_index < allocated_pages_; ++page_index) {
    UnlockPage(page_index);
  }

#ifdef MADV_FREE
  if (!keep_memory_for_testing_) {
    int retval =
        madvise(data_, allocated_pages_ * base::GetPageSize(), MADV_FREE);
    DPCHECK(!retval);
  }
#endif

#if defined(ADDRESS_SANITIZER)
  ASAN_POISON_MEMORY_REGION(data_, allocated_pages_ * base::GetPageSize());
#endif  // defined(ADDRESS_SANITIZER)

  is_locked_ = false;
}

void* MadvFreeDiscardableMemoryPosix::data() const {
  DFAKE_SCOPED_LOCK(thread_collision_warner_);
  DCHECK(is_locked_);
  DCHECK(data_ != nullptr);

  return data_;
}

bool MadvFreeDiscardableMemoryPosix::LockPage(size_t page_index) {
  // We require the byte-level representation of std::atomic<intptr_t> to be
  // equivalent to that of an intptr_t. Since std::atomic<intptr_t> has standard
  // layout, having equal size is sufficient but not necessary for them to have
  // the same byte-level representation.
  static_assert(sizeof(intptr_t) == sizeof(std::atomic<intptr_t>),
                "Incompatible layout of std::atomic.");
  DCHECK(std::atomic<intptr_t>{}.is_lock_free());
  std::atomic<intptr_t>* page_as_atomic =
      reinterpret_cast<std::atomic<intptr_t>*>(
          static_cast<uint8_t*>(data_) + page_index * base::GetPageSize());

  intptr_t expected = kPageMagicCookie;

  // Recall that we set the first word of the page to |kPageMagicCookie|
  // (non-zero) during unlocking. Thus, if the value has changed, the page has
  // been discarded. Restore the page's original first word from before
  // unlocking only if the page has not been discarded.
  if (!std::atomic_compare_exchange_strong_explicit(
          page_as_atomic, &expected,
          static_cast<intptr_t>(page_first_word_[page_index]),
          std::memory_order_relaxed, std::memory_order_relaxed)) {
    return false;
  }

  return true;
}

void MadvFreeDiscardableMemoryPosix::UnlockPage(size_t page_index) {
  DCHECK(std::atomic<intptr_t>{}.is_lock_free());

  std::atomic<intptr_t>* page_as_atomic =
      reinterpret_cast<std::atomic<intptr_t>*>(
          static_cast<uint8_t*>(data_) + page_index * base::GetPageSize());

  // Store the first word of the page for use during unlocking.
  page_first_word_[page_index].store(*page_as_atomic,
                                     std::memory_order_relaxed);
  // Store a non-zero value into the first word of the page, so we can tell when
  // the page is discarded during locking.
  page_as_atomic->store(kPageMagicCookie, std::memory_order_relaxed);
}

void MadvFreeDiscardableMemoryPosix::DiscardPage(size_t page_index) {
  DFAKE_SCOPED_LOCK(thread_collision_warner_);
  DCHECK(!is_locked_);
  DCHECK(page_index < allocated_pages_);
  int retval =
      madvise(static_cast<uint8_t*>(data_) + base::GetPageSize() * page_index,
              base::GetPageSize(), MADV_DONTNEED);
  DPCHECK(!retval);
}

bool MadvFreeDiscardableMemoryPosix::IsLockedForTesting() const {
  DFAKE_SCOPED_LOCK(thread_collision_warner_);
  return is_locked_;
}

void MadvFreeDiscardableMemoryPosix::DiscardForTesting() {
  DFAKE_SCOPED_LOCK(thread_collision_warner_);
  DCHECK(!is_locked_);
  int retval =
      madvise(data_, base::GetPageSize() * allocated_pages_, MADV_DONTNEED);
  DPCHECK(!retval);
}

trace_event::MemoryAllocatorDump*
MadvFreeDiscardableMemoryPosix::CreateMemoryAllocatorDump(
    const char* name,
    trace_event::ProcessMemoryDump* pmd) const {
  DFAKE_SCOPED_LOCK(thread_collision_warner_);

  using base::trace_event::MemoryAllocatorDump;
  std::string allocator_dump_name = base::StringPrintf(
      "discardable/segment_0x%" PRIXPTR, reinterpret_cast<uintptr_t>(this));

  MemoryAllocatorDump* allocator_dump =
      pmd->CreateAllocatorDump(allocator_dump_name);

  bool is_discarded = IsDiscarded();

  MemoryAllocatorDump* dump = pmd->CreateAllocatorDump(name);
  // The effective_size is the amount of unused space as a result of being
  // page-aligned.
  dump->AddScalar(MemoryAllocatorDump::kNameSize,
                  MemoryAllocatorDump::kUnitsBytes,
                  is_discarded ? 0U : static_cast<uint64_t>(size_in_bytes_));

  allocator_dump->AddScalar(
      MemoryAllocatorDump::kNameSize, MemoryAllocatorDump::kUnitsBytes,
      is_discarded
          ? 0U
          : static_cast<uint64_t>(allocated_pages_ * base::GetPageSize()));
  allocator_dump->AddScalar(MemoryAllocatorDump::kNameObjectCount,
                            MemoryAllocatorDump::kUnitsObjects, 1U);
  allocator_dump->AddScalar(
      "wasted_size", MemoryAllocatorDump::kUnitsBytes,
      static_cast<uint64_t>(allocated_pages_ * base::GetPageSize() -
                            size_in_bytes_));
  allocator_dump->AddScalar("locked_size", MemoryAllocatorDump::kUnitsBytes,
                            is_locked_ ? size_in_bytes_ : 0U);
  allocator_dump->AddScalar("page_count", MemoryAllocatorDump::kUnitsObjects,
                            static_cast<uint64_t>(allocated_pages_));

  // The amount of space that is discarded, but not unmapped (i.e. the memory
  // was discarded while unlocked, but the pages are still mapped in memory
  // since Deallocate() has not been called yet). This instance is discarded if
  // it is unlocked and not all pages are resident in memory.
  allocator_dump->AddScalar(
      "discarded_size", MemoryAllocatorDump::kUnitsBytes,
      is_discarded ? allocated_pages_ * base::GetPageSize() : 0U);

  pmd->AddSuballocation(dump->guid(), allocator_dump_name);
  return dump;
}

bool MadvFreeDiscardableMemoryPosix::IsValid() const {
  DFAKE_SCOPED_RECURSIVE_LOCK(thread_collision_warner_);
  return data_ != nullptr;
}

void MadvFreeDiscardableMemoryPosix::SetKeepMemoryForTesting(bool keep_memory) {
  DFAKE_SCOPED_LOCK(thread_collision_warner_);
  DCHECK(is_locked_);
  keep_memory_for_testing_ = keep_memory;
}

bool MadvFreeDiscardableMemoryPosix::IsResident() const {
  DFAKE_SCOPED_RECURSIVE_LOCK(thread_collision_warner_);
#ifdef OS_MACOSX
  std::vector<char> vec(allocated_pages_);
#else
  std::vector<unsigned char> vec(allocated_pages_);
#endif

  int retval =
      mincore(data_, allocated_pages_ * base::GetPageSize(), vec.data());
  DPCHECK(retval == 0 || errno == EAGAIN);

  for (size_t i = 0; i < allocated_pages_; ++i) {
    if (!(vec[i] & 1))
      return false;
  }
  return true;
}

bool MadvFreeDiscardableMemoryPosix::IsDiscarded() const {
  return !is_locked_ && !IsResident();
}

bool MadvFreeDiscardableMemoryPosix::Deallocate() {
  DFAKE_SCOPED_RECURSIVE_LOCK(thread_collision_warner_);
  if (data_) {
#if defined(ADDRESS_SANITIZER)
    ASAN_UNPOISON_MEMORY_REGION(data_, allocated_pages_ * base::GetPageSize());
#endif  // defined(ADDRESS_SANITIZER)

    int retval = munmap(data_, allocated_pages_ * base::GetPageSize());
    PCHECK(!retval);
    data_ = nullptr;
    (*allocator_byte_count_) -= size_in_bytes_;
    return true;
  }
  return false;
}

MadvFreeSupport GetMadvFreeSupport() {
  static MadvFreeSupport kMadvFreeSupport = ProbePlatformMadvFreeSupport();
  return kMadvFreeSupport;
}

}  // namespace base
