// Copyright (c) 2018 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

#ifndef BASE_ALLOCATOR_PARTITION_ALLOCATOR_PARTITION_BUCKET_H_
#define BASE_ALLOCATOR_PARTITION_ALLOCATOR_PARTITION_BUCKET_H_

#include <stddef.h>
#include <stdint.h>

#include "base/allocator/partition_allocator/partition_alloc_constants.h"
#include "base/base_export.h"
#include "base/compiler_specific.h"
#include "base/logging.h"

namespace base {
namespace internal {

struct PartitionPage;
struct PartitionRootBase;

struct PartitionBucket {
  // Accessed most in hot path => goes first.
  PartitionPage* active_pages_head;

  PartitionPage* empty_pages_head;
  PartitionPage* decommitted_pages_head;
  uint32_t slot_size;
  uint32_t num_system_pages_per_slot_span : 8;
  uint32_t num_full_pages : 24;

  // Public API.
  void Init(uint32_t new_slot_size);

  // Sets |is_already_zeroed| to true if the allocation was satisfied by
  // requesting (a) new page(s) from the operating system, or false otherwise.
  // This enables an optimization for when callers use |PartitionAllocZeroFill|:
  // there is no need to call memset on fresh pages; the OS has already zeroed
  // them. (See |PartitionRootBase::AllocFromBucket|.)
  //
  // Note the matching Free() functions are in PartitionPage.
  BASE_EXPORT NOINLINE void* SlowPathAlloc(PartitionRootBase* root,
                                           int flags,
                                           size_t size,
                                           bool* is_already_zeroed);

  ALWAYS_INLINE bool is_direct_mapped() const {
    return !num_system_pages_per_slot_span;
  }
  ALWAYS_INLINE size_t get_bytes_per_span() const {
    // TODO(ajwong): Change to CheckedMul. https://crbug.com/787153
    // https://crbug.com/680657
    return num_system_pages_per_slot_span * kSystemPageSize;
  }
  ALWAYS_INLINE uint16_t get_slots_per_span() const {
    // TODO(ajwong): Change to CheckedMul. https://crbug.com/787153
    // https://crbug.com/680657
    return static_cast<uint16_t>(get_bytes_per_span() / slot_size);
  }

  static ALWAYS_INLINE size_t get_direct_map_size(size_t size) {
    // Caller must check that the size is not above the kGenericMaxDirectMapped
    // limit before calling. This also guards against integer overflow in the
    // calculation here.
    DCHECK(size <= kGenericMaxDirectMapped);
    return (size + kSystemPageOffsetMask) & kSystemPageBaseMask;
  }

  // TODO(ajwong): Can this be made private?  https://crbug.com/787153
  static PartitionBucket* get_sentinel_bucket();

  // This helper function scans a bucket's active page list for a suitable new
  // active page.  When it finds a suitable new active page (one that has
  // free slots and is not empty), it is set as the new active page. If there
  // is no suitable new active page, the current active page is set to
  // PartitionPage::get_sentinel_page(). As potential pages are scanned, they
  // are tidied up according to their state. Empty pages are swept on to the
  // empty page list, decommitted pages on to the decommitted page list and full
  // pages are unlinked from any list.
  //
  // This is where the guts of the bucket maintenance is done!
  bool SetNewActivePage();

 private:
  static void OutOfMemory(const PartitionRootBase* root);
  static void OutOfMemoryWithLotsOfUncommitedPages();

  static NOINLINE void OnFull();

  // Returns a natural number of PartitionPages (calculated by
  // get_system_pages_per_slot_span()) to allocate from the current
  // SuperPage when the bucket runs out of slots.
  ALWAYS_INLINE uint16_t get_pages_per_slot_span();

  // Returns the number of system pages in a slot span.
  //
  // The calculation attemps to find the best number of System Pages to
  // allocate for the given slot_size to minimize wasted space. It uses a
  // heuristic that looks at number of bytes wasted after the last slot and
  // attempts to account for the PTE usage of each System Page.
  uint8_t get_system_pages_per_slot_span();

  // Allocates a new slot span with size |num_partition_pages| from the
  // current extent. Metadata within this slot span will be uninitialized.
  // Returns nullptr on error.
  ALWAYS_INLINE void* AllocNewSlotSpan(PartitionRootBase* root,
                                       int flags,
                                       uint16_t num_partition_pages);

  // Each bucket allocates a slot span when it runs out of slots.
  // A slot span's size is equal to get_pages_per_slot_span() number of
  // PartitionPages. This function initializes all PartitionPage within the
  // span to point to the first PartitionPage which holds all the metadata
  // for the span and registers this bucket as the owner of the span. It does
  // NOT put the slots into the bucket's freelist.
  ALWAYS_INLINE void InitializeSlotSpan(PartitionPage* page);

  // Allocates one slot from the given |page| and then adds the remainder to
  // the current bucket. If the |page| was freshly allocated, it must have been
  // passed through InitializeSlotSpan() first.
  ALWAYS_INLINE char* AllocAndFillFreelist(PartitionPage* page);

  static PartitionBucket sentinel_bucket_;
};

}  // namespace internal
}  // namespace base

#endif  // BASE_ALLOCATOR_PARTITION_ALLOCATOR_PARTITION_BUCKET_H_
