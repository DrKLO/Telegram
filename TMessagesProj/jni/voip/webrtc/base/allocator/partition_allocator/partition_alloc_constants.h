// Copyright (c) 2018 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

#ifndef BASE_ALLOCATOR_PARTITION_ALLOCATOR_PARTITION_ALLOC_CONSTANTS_H_
#define BASE_ALLOCATOR_PARTITION_ALLOCATOR_PARTITION_ALLOC_CONSTANTS_H_

#include <limits.h>

#include "base/allocator/partition_allocator/page_allocator_constants.h"
#include "base/logging.h"

#include "build/build_config.h"

namespace base {

// Allocation granularity of sizeof(void*) bytes.
static const size_t kAllocationGranularity = sizeof(void*);
static const size_t kAllocationGranularityMask = kAllocationGranularity - 1;
static const size_t kBucketShift = (kAllocationGranularity == 8) ? 3 : 2;

// Underlying partition storage pages (`PartitionPage`s) are a power-of-2 size.
// It is typical for a `PartitionPage` to be based on multiple system pages.
// Most references to "page" refer to `PartitionPage`s.
//
// *Super pages* are the underlying system allocations we make. Super pages
// contain multiple partition pages and include space for a small amount of
// metadata per partition page.
//
// Inside super pages, we store *slot spans*. A slot span is a continguous range
// of one or more `PartitionPage`s that stores allocations of the same size.
// Slot span sizes are adjusted depending on the allocation size, to make sure
// the packing does not lead to unused (wasted) space at the end of the last
// system page of the span. For our current maximum slot span size of 64 KiB and
// other constant values, we pack _all_ `PartitionRootGeneric::Alloc` sizes
// perfectly up against the end of a system page.

#if defined(_MIPS_ARCH_LOONGSON)
static const size_t kPartitionPageShift = 16;  // 64 KiB
#elif defined(ARCH_CPU_PPC64)
static const size_t kPartitionPageShift = 18;  // 256 KiB
#else
static const size_t kPartitionPageShift = 14;  // 16 KiB
#endif
static const size_t kPartitionPageSize = 1 << kPartitionPageShift;
static const size_t kPartitionPageOffsetMask = kPartitionPageSize - 1;
static const size_t kPartitionPageBaseMask = ~kPartitionPageOffsetMask;
// TODO: Should this be 1 if defined(_MIPS_ARCH_LOONGSON)?
static const size_t kMaxPartitionPagesPerSlotSpan = 4;

// To avoid fragmentation via never-used freelist entries, we hand out partition
// freelist sections gradually, in units of the dominant system page size. What
// we're actually doing is avoiding filling the full `PartitionPage` (16 KiB)
// with freelist pointers right away. Writing freelist pointers will fault and
// dirty a private page, which is very wasteful if we never actually store
// objects there.

static const size_t kNumSystemPagesPerPartitionPage =
    kPartitionPageSize / kSystemPageSize;
static const size_t kMaxSystemPagesPerSlotSpan =
    kNumSystemPagesPerPartitionPage * kMaxPartitionPagesPerSlotSpan;

// We reserve virtual address space in 2 MiB chunks (aligned to 2 MiB as well).
// These chunks are called *super pages*. We do this so that we can store
// metadata in the first few pages of each 2 MiB-aligned section. This makes
// freeing memory very fast. We specifically choose 2 MiB because this virtual
// address block represents a full but single PTE allocation on ARM, ia32 and
// x64.
//
// The layout of the super page is as follows. The sizes below are the same for
// 32- and 64-bit platforms.
//
//     +-----------------------+
//     | Guard page (4 KiB)    |
//     | Metadata page (4 KiB) |
//     | Guard pages (8 KiB)   |
//     | Slot span             |
//     | Slot span             |
//     | ...                   |
//     | Slot span             |
//     | Guard page (4 KiB)    |
//     +-----------------------+
//
// Each slot span is a contiguous range of one or more `PartitionPage`s.
//
// The metadata page has the following format. Note that the `PartitionPage`
// that is not at the head of a slot span is "unused". In other words, the
// metadata for the slot span is stored only in the first `PartitionPage` of the
// slot span. Metadata accesses to other `PartitionPage`s are redirected to the
// first `PartitionPage`.
//
//     +---------------------------------------------+
//     | SuperPageExtentEntry (32 B)                 |
//     | PartitionPage of slot span 1 (32 B, used)   |
//     | PartitionPage of slot span 1 (32 B, unused) |
//     | PartitionPage of slot span 1 (32 B, unused) |
//     | PartitionPage of slot span 2 (32 B, used)   |
//     | PartitionPage of slot span 3 (32 B, used)   |
//     | ...                                         |
//     | PartitionPage of slot span N (32 B, unused) |
//     +---------------------------------------------+
//
// A direct-mapped page has a similar layout to fake it looking like a super
// page:
//
//     +-----------------------+
//     | Guard page (4 KiB)    |
//     | Metadata page (4 KiB) |
//     | Guard pages (8 KiB)   |
//     | Direct mapped object  |
//     | Guard page (4 KiB)    |
//     +-----------------------+
//
// A direct-mapped page's metadata page has the following layout:
//
//     +--------------------------------+
//     | SuperPageExtentEntry (32 B)    |
//     | PartitionPage (32 B)           |
//     | PartitionBucket (32 B)         |
//     | PartitionDirectMapExtent (8 B) |
//     +--------------------------------+

static const size_t kSuperPageShift = 21;  // 2 MiB
static const size_t kSuperPageSize = 1 << kSuperPageShift;
static const size_t kSuperPageOffsetMask = kSuperPageSize - 1;
static const size_t kSuperPageBaseMask = ~kSuperPageOffsetMask;
static const size_t kNumPartitionPagesPerSuperPage =
    kSuperPageSize / kPartitionPageSize;

// The following kGeneric* constants apply to the generic variants of the API.
// The "order" of an allocation is closely related to the power-of-1 size of the
// allocation. More precisely, the order is the bit index of the
// most-significant-bit in the allocation size, where the bit numbers starts at
// index 1 for the least-significant-bit.
//
// In terms of allocation sizes, order 0 covers 0, order 1 covers 1, order 2
// covers 2->3, order 3 covers 4->7, order 4 covers 8->15.

static const size_t kGenericMinBucketedOrder = 4;  // 8 bytes.
// The largest bucketed order is 1 << (20 - 1), storing [512 KiB, 1 MiB):
static const size_t kGenericMaxBucketedOrder = 20;
static const size_t kGenericNumBucketedOrders =
    (kGenericMaxBucketedOrder - kGenericMinBucketedOrder) + 1;
// Eight buckets per order (for the higher orders), e.g. order 8 is 128, 144,
// 160, ..., 240:
static const size_t kGenericNumBucketsPerOrderBits = 3;
static const size_t kGenericNumBucketsPerOrder =
    1 << kGenericNumBucketsPerOrderBits;
static const size_t kGenericNumBuckets =
    kGenericNumBucketedOrders * kGenericNumBucketsPerOrder;
static const size_t kGenericSmallestBucket = 1
                                             << (kGenericMinBucketedOrder - 1);
static const size_t kGenericMaxBucketSpacing =
    1 << ((kGenericMaxBucketedOrder - 1) - kGenericNumBucketsPerOrderBits);
static const size_t kGenericMaxBucketed =
    (1 << (kGenericMaxBucketedOrder - 1)) +
    ((kGenericNumBucketsPerOrder - 1) * kGenericMaxBucketSpacing);
// Limit when downsizing a direct mapping using `realloc`:
static const size_t kGenericMinDirectMappedDownsize = kGenericMaxBucketed + 1;
static const size_t kGenericMaxDirectMapped =
    (1UL << 31) + kPageAllocationGranularity;  // 2 GiB plus 1 more page.
static const size_t kBitsPerSizeT = sizeof(void*) * CHAR_BIT;

// Constant for the memory reclaim logic.
static const size_t kMaxFreeableSpans = 16;

// If the total size in bytes of allocated but not committed pages exceeds this
// value (probably it is a "out of virtual address space" crash), a special
// crash stack trace is generated at
// `PartitionOutOfMemoryWithLotsOfUncommitedPages`. This is to distinguish "out
// of virtual address space" from "out of physical memory" in crash reports.
static const size_t kReasonableSizeOfUnusedPages = 1024 * 1024 * 1024;  // 1 GiB

// These byte values match tcmalloc.
static const unsigned char kUninitializedByte = 0xAB;
static const unsigned char kFreedByte = 0xCD;

// Flags for `PartitionAllocGenericFlags`.
enum PartitionAllocFlags {
  PartitionAllocReturnNull = 1 << 0,
  PartitionAllocZeroFill = 1 << 1,

  PartitionAllocLastFlag = PartitionAllocZeroFill
};

}  // namespace base

#endif  // BASE_ALLOCATOR_PARTITION_ALLOCATOR_PARTITION_ALLOC_CONSTANTS_H_
