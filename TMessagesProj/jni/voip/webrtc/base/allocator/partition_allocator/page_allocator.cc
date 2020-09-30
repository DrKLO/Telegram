// Copyright (c) 2013 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

#include "base/allocator/partition_allocator/page_allocator.h"

#include <limits.h>

#include <atomic>

#include "base/allocator/partition_allocator/address_space_randomization.h"
#include "base/allocator/partition_allocator/page_allocator_internal.h"
#include "base/allocator/partition_allocator/spin_lock.h"
#include "base/bits.h"
#include "base/logging.h"
#include "base/no_destructor.h"
#include "base/numerics/checked_math.h"
#include "build/build_config.h"

#if defined(OS_WIN)
#include <windows.h>
#endif

#if defined(OS_WIN)
#include "base/allocator/partition_allocator/page_allocator_internals_win.h"
#elif defined(OS_POSIX)
#include "base/allocator/partition_allocator/page_allocator_internals_posix.h"
#elif defined(OS_FUCHSIA)
#include "base/allocator/partition_allocator/page_allocator_internals_fuchsia.h"
#else
#error Platform not supported.
#endif

namespace base {

namespace {

// We may reserve/release address space on different threads.
subtle::SpinLock& GetReserveLock() {
  static NoDestructor<subtle::SpinLock> s_reserveLock;
  return *s_reserveLock;
}

// We only support a single block of reserved address space.
void* s_reservation_address = nullptr;
size_t s_reservation_size = 0;

void* AllocPagesIncludingReserved(void* address,
                                  size_t length,
                                  PageAccessibilityConfiguration accessibility,
                                  PageTag page_tag,
                                  bool commit) {
  void* ret =
      SystemAllocPages(address, length, accessibility, page_tag, commit);
  if (ret == nullptr) {
    const bool cant_alloc_length = kHintIsAdvisory || address == nullptr;
    if (cant_alloc_length) {
      // The system cannot allocate |length| bytes. Release any reserved address
      // space and try once more.
      ReleaseReservation();
      ret = SystemAllocPages(address, length, accessibility, page_tag, commit);
    }
  }
  return ret;
}

// Trims |base| to given |trim_length| and |alignment|.
//
// On failure, on Windows, this function returns nullptr and frees |base|.
void* TrimMapping(void* base,
                  size_t base_length,
                  size_t trim_length,
                  uintptr_t alignment,
                  PageAccessibilityConfiguration accessibility,
                  bool commit) {
  size_t pre_slack = reinterpret_cast<uintptr_t>(base) & (alignment - 1);
  if (pre_slack) {
    pre_slack = alignment - pre_slack;
  }
  size_t post_slack = base_length - pre_slack - trim_length;
  DCHECK(base_length >= trim_length || pre_slack || post_slack);
  DCHECK(pre_slack < base_length);
  DCHECK(post_slack < base_length);
  return TrimMappingInternal(base, base_length, trim_length, accessibility,
                             commit, pre_slack, post_slack);
}

}  // namespace

void* SystemAllocPages(void* hint,
                       size_t length,
                       PageAccessibilityConfiguration accessibility,
                       PageTag page_tag,
                       bool commit) {
  DCHECK(!(length & kPageAllocationGranularityOffsetMask));
  DCHECK(!(reinterpret_cast<uintptr_t>(hint) &
           kPageAllocationGranularityOffsetMask));
  DCHECK(commit || accessibility == PageInaccessible);
  return SystemAllocPagesInternal(hint, length, accessibility, page_tag,
                                  commit);
}

void* AllocPages(void* address,
                 size_t length,
                 size_t align,
                 PageAccessibilityConfiguration accessibility,
                 PageTag page_tag,
                 bool commit) {
  DCHECK(length >= kPageAllocationGranularity);
  DCHECK(!(length & kPageAllocationGranularityOffsetMask));
  DCHECK(align >= kPageAllocationGranularity);
  // Alignment must be power of 2 for masking math to work.
  DCHECK(base::bits::IsPowerOfTwo(align));
  DCHECK(!(reinterpret_cast<uintptr_t>(address) &
           kPageAllocationGranularityOffsetMask));
  uintptr_t align_offset_mask = align - 1;
  uintptr_t align_base_mask = ~align_offset_mask;
  DCHECK(!(reinterpret_cast<uintptr_t>(address) & align_offset_mask));

  // If the client passed null as the address, choose a good one.
  if (address == nullptr) {
    address = GetRandomPageBase();
    address = reinterpret_cast<void*>(reinterpret_cast<uintptr_t>(address) &
                                      align_base_mask);
  }

  // First try to force an exact-size, aligned allocation from our random base.
#if defined(ARCH_CPU_32_BITS)
  // On 32 bit systems, first try one random aligned address, and then try an
  // aligned address derived from the value of |ret|.
  constexpr int kExactSizeTries = 2;
#else
  // On 64 bit systems, try 3 random aligned addresses.
  constexpr int kExactSizeTries = 3;
#endif

  for (int i = 0; i < kExactSizeTries; ++i) {
    void* ret = AllocPagesIncludingReserved(address, length, accessibility,
                                            page_tag, commit);
    if (ret != nullptr) {
      // If the alignment is to our liking, we're done.
      if (!(reinterpret_cast<uintptr_t>(ret) & align_offset_mask))
        return ret;
      // Free the memory and try again.
      FreePages(ret, length);
    } else {
      // |ret| is null; if this try was unhinted, we're OOM.
      if (kHintIsAdvisory || address == nullptr)
        return nullptr;
    }

#if defined(ARCH_CPU_32_BITS)
    // For small address spaces, try the first aligned address >= |ret|. Note
    // |ret| may be null, in which case |address| becomes null.
    address = reinterpret_cast<void*>(
        (reinterpret_cast<uintptr_t>(ret) + align_offset_mask) &
        align_base_mask);
#else  // defined(ARCH_CPU_64_BITS)
    // Keep trying random addresses on systems that have a large address space.
    address = GetRandomPageBase();
    address = reinterpret_cast<void*>(reinterpret_cast<uintptr_t>(address) &
                                      align_base_mask);
#endif
  }

  // Make a larger allocation so we can force alignment.
  size_t try_length = length + (align - kPageAllocationGranularity);
  CHECK(try_length >= length);
  void* ret;

  do {
    // Continue randomizing only on POSIX.
    address = kHintIsAdvisory ? GetRandomPageBase() : nullptr;
    ret = AllocPagesIncludingReserved(address, try_length, accessibility,
                                      page_tag, commit);
    // The retries are for Windows, where a race can steal our mapping on
    // resize.
  } while (ret != nullptr &&
           (ret = TrimMapping(ret, try_length, length, align, accessibility,
                              commit)) == nullptr);

  return ret;
}

void FreePages(void* address, size_t length) {
  DCHECK(!(reinterpret_cast<uintptr_t>(address) &
           kPageAllocationGranularityOffsetMask));
  DCHECK(!(length & kPageAllocationGranularityOffsetMask));
  FreePagesInternal(address, length);
}

bool TrySetSystemPagesAccess(void* address,
                             size_t length,
                             PageAccessibilityConfiguration accessibility) {
  DCHECK(!(length & kSystemPageOffsetMask));
  return TrySetSystemPagesAccessInternal(address, length, accessibility);
}

void SetSystemPagesAccess(void* address,
                          size_t length,
                          PageAccessibilityConfiguration accessibility) {
  DCHECK(!(length & kSystemPageOffsetMask));
  SetSystemPagesAccessInternal(address, length, accessibility);
}

void DecommitSystemPages(void* address, size_t length) {
  DCHECK_EQ(0UL, length & kSystemPageOffsetMask);
  DecommitSystemPagesInternal(address, length);
}

bool RecommitSystemPages(void* address,
                         size_t length,
                         PageAccessibilityConfiguration accessibility) {
  DCHECK_EQ(0UL, length & kSystemPageOffsetMask);
  DCHECK_NE(PageInaccessible, accessibility);
  return RecommitSystemPagesInternal(address, length, accessibility);
}

void DiscardSystemPages(void* address, size_t length) {
  DCHECK_EQ(0UL, length & kSystemPageOffsetMask);
  DiscardSystemPagesInternal(address, length);
}

bool ReserveAddressSpace(size_t size) {
  // To avoid deadlock, call only SystemAllocPages.
  subtle::SpinLock::Guard guard(GetReserveLock());
  if (s_reservation_address == nullptr) {
    void* mem = SystemAllocPages(nullptr, size, PageInaccessible,
                                 PageTag::kChromium, false);
    if (mem != nullptr) {
      // We guarantee this alignment when reserving address space.
      DCHECK(!(reinterpret_cast<uintptr_t>(mem) &
               kPageAllocationGranularityOffsetMask));
      s_reservation_address = mem;
      s_reservation_size = size;
      return true;
    }
  }
  return false;
}

bool ReleaseReservation() {
  // To avoid deadlock, call only FreePages.
  subtle::SpinLock::Guard guard(GetReserveLock());
  if (!s_reservation_address)
    return false;

  FreePages(s_reservation_address, s_reservation_size);
  s_reservation_address = nullptr;
  s_reservation_size = 0;
  return true;
}

bool HasReservationForTesting() {
  subtle::SpinLock::Guard guard(GetReserveLock());
  return s_reservation_address != nullptr;
}

uint32_t GetAllocPageErrorCode() {
  return s_allocPageErrorCode;
}

}  // namespace base
