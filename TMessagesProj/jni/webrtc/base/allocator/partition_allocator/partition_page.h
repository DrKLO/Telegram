// Copyright (c) 2018 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

#ifndef BASE_ALLOCATOR_PARTITION_ALLOCATOR_PARTITION_PAGE_H_
#define BASE_ALLOCATOR_PARTITION_ALLOCATOR_PARTITION_PAGE_H_

#include <string.h>

#include "base/allocator/partition_allocator/partition_alloc_constants.h"
#include "base/allocator/partition_allocator/partition_bucket.h"
#include "base/allocator/partition_allocator/partition_cookie.h"
#include "base/allocator/partition_allocator/partition_freelist_entry.h"
#include "base/allocator/partition_allocator/random.h"
#include "base/logging.h"

namespace base {
namespace internal {

struct PartitionRootBase;

// Some notes on page states. A page can be in one of four major states:
// 1) Active.
// 2) Full.
// 3) Empty.
// 4) Decommitted.
// An active page has available free slots. A full page has no free slots. An
// empty page has no free slots, and a decommitted page is an empty page that
// had its backing memory released back to the system.
// There are two linked lists tracking the pages. The "active page" list is an
// approximation of a list of active pages. It is an approximation because
// full, empty and decommitted pages may briefly be present in the list until
// we next do a scan over it.
// The "empty page" list is an accurate list of pages which are either empty
// or decommitted.
//
// The significant page transitions are:
// - free() will detect when a full page has a slot free()'d and immediately
// return the page to the head of the active list.
// - free() will detect when a page is fully emptied. It _may_ add it to the
// empty list or it _may_ leave it on the active list until a future list scan.
// - malloc() _may_ scan the active page list in order to fulfil the request.
// If it does this, full, empty and decommitted pages encountered will be
// booted out of the active list. If there are no suitable active pages found,
// an empty or decommitted page (if one exists) will be pulled from the empty
// list on to the active list.
//
// TODO(ajwong): Evaluate if this should be named PartitionSlotSpanMetadata or
// similar. If so, all uses of the term "page" in comments, member variables,
// local variables, and documentation that refer to this concept should be
// updated.
struct PartitionPage {
  PartitionFreelistEntry* freelist_head;
  PartitionPage* next_page;
  PartitionBucket* bucket;
  // Deliberately signed, 0 for empty or decommitted page, -n for full pages:
  int16_t num_allocated_slots;
  uint16_t num_unprovisioned_slots;
  uint16_t page_offset;
  int16_t empty_cache_index;  // -1 if not in the empty cache.

  // Public API

  // Note the matching Alloc() functions are in PartitionPage.
  BASE_EXPORT NOINLINE void FreeSlowPath();
  ALWAYS_INLINE void Free(void* ptr);

  void Decommit(PartitionRootBase* root);
  void DecommitIfPossible(PartitionRootBase* root);

  // Pointer manipulation functions. These must be static as the input |page|
  // pointer may be the result of an offset calculation and therefore cannot
  // be trusted. The objective of these functions is to sanitize this input.
  ALWAYS_INLINE static void* ToPointer(const PartitionPage* page);
  ALWAYS_INLINE static PartitionPage* FromPointerNoAlignmentCheck(void* ptr);
  ALWAYS_INLINE static PartitionPage* FromPointer(void* ptr);

  ALWAYS_INLINE const size_t* get_raw_size_ptr() const;
  ALWAYS_INLINE size_t* get_raw_size_ptr() {
    return const_cast<size_t*>(
        const_cast<const PartitionPage*>(this)->get_raw_size_ptr());
  }

  ALWAYS_INLINE size_t get_raw_size() const;
  ALWAYS_INLINE void set_raw_size(size_t size);

  ALWAYS_INLINE void Reset();

  // TODO(ajwong): Can this be made private?  https://crbug.com/787153
  BASE_EXPORT static PartitionPage* get_sentinel_page();

  // Page State accessors.
  // Note that it's only valid to call these functions on pages found on one of
  // the page lists. Specifically, you can't call these functions on full pages
  // that were detached from the active list.
  //
  // This restriction provides the flexibity for some of the status fields to
  // be repurposed when a page is taken off a list. See the negation of
  // |num_allocated_slots| when a full page is removed from the active list
  // for an example of such repurposing.
  ALWAYS_INLINE bool is_active() const;
  ALWAYS_INLINE bool is_full() const;
  ALWAYS_INLINE bool is_empty() const;
  ALWAYS_INLINE bool is_decommitted() const;

 private:
  // g_sentinel_page is used as a sentinel to indicate that there is no page
  // in the active page list. We can use nullptr, but in that case we need
  // to add a null-check branch to the hot allocation path. We want to avoid
  // that.
  //
  // Note, this declaration is kept in the header as opposed to an anonymous
  // namespace so the getter can be fully inlined.
  static PartitionPage sentinel_page_;
};
static_assert(sizeof(PartitionPage) <= kPageMetadataSize,
              "PartitionPage must be able to fit in a metadata slot");

ALWAYS_INLINE char* PartitionSuperPageToMetadataArea(char* ptr) {
  uintptr_t pointer_as_uint = reinterpret_cast<uintptr_t>(ptr);
  DCHECK(!(pointer_as_uint & kSuperPageOffsetMask));
  // The metadata area is exactly one system page (the guard page) into the
  // super page.
  return reinterpret_cast<char*>(pointer_as_uint + kSystemPageSize);
}

ALWAYS_INLINE PartitionPage* PartitionPage::FromPointerNoAlignmentCheck(
    void* ptr) {
  uintptr_t pointer_as_uint = reinterpret_cast<uintptr_t>(ptr);
  char* super_page_ptr =
      reinterpret_cast<char*>(pointer_as_uint & kSuperPageBaseMask);
  uintptr_t partition_page_index =
      (pointer_as_uint & kSuperPageOffsetMask) >> kPartitionPageShift;
  // Index 0 is invalid because it is the metadata and guard area and
  // the last index is invalid because it is a guard page.
  DCHECK(partition_page_index);
  DCHECK(partition_page_index < kNumPartitionPagesPerSuperPage - 1);
  PartitionPage* page = reinterpret_cast<PartitionPage*>(
      PartitionSuperPageToMetadataArea(super_page_ptr) +
      (partition_page_index << kPageMetadataShift));
  // Partition pages in the same slot span can share the same page object.
  // Adjust for that.
  size_t delta = page->page_offset << kPageMetadataShift;
  page =
      reinterpret_cast<PartitionPage*>(reinterpret_cast<char*>(page) - delta);
  return page;
}

// Resturns start of the slot span for the PartitionPage.
ALWAYS_INLINE void* PartitionPage::ToPointer(const PartitionPage* page) {
  uintptr_t pointer_as_uint = reinterpret_cast<uintptr_t>(page);

  uintptr_t super_page_offset = (pointer_as_uint & kSuperPageOffsetMask);

  // A valid |page| must be past the first guard System page and within
  // the following metadata region.
  DCHECK(super_page_offset > kSystemPageSize);
  // Must be less than total metadata region.
  DCHECK(super_page_offset < kSystemPageSize + (kNumPartitionPagesPerSuperPage *
                                                kPageMetadataSize));
  uintptr_t partition_page_index =
      (super_page_offset - kSystemPageSize) >> kPageMetadataShift;
  // Index 0 is invalid because it is the superpage extent metadata and the
  // last index is invalid because the whole PartitionPage is set as guard
  // pages for the metadata region.
  DCHECK(partition_page_index);
  DCHECK(partition_page_index < kNumPartitionPagesPerSuperPage - 1);
  uintptr_t super_page_base = (pointer_as_uint & kSuperPageBaseMask);
  void* ret = reinterpret_cast<void*>(
      super_page_base + (partition_page_index << kPartitionPageShift));
  return ret;
}

ALWAYS_INLINE PartitionPage* PartitionPage::FromPointer(void* ptr) {
  PartitionPage* page = PartitionPage::FromPointerNoAlignmentCheck(ptr);
  // Checks that the pointer is a multiple of bucket size.
  DCHECK(!((reinterpret_cast<uintptr_t>(ptr) -
            reinterpret_cast<uintptr_t>(PartitionPage::ToPointer(page))) %
           page->bucket->slot_size));
  return page;
}

ALWAYS_INLINE const size_t* PartitionPage::get_raw_size_ptr() const {
  // For single-slot buckets which span more than one partition page, we
  // have some spare metadata space to store the raw allocation size. We
  // can use this to report better statistics.
  if (bucket->slot_size <= kMaxSystemPagesPerSlotSpan * kSystemPageSize)
    return nullptr;

  DCHECK((bucket->slot_size % kSystemPageSize) == 0);
  DCHECK(bucket->is_direct_mapped() || bucket->get_slots_per_span() == 1);

  const PartitionPage* the_next_page = this + 1;
  return reinterpret_cast<const size_t*>(&the_next_page->freelist_head);
}

ALWAYS_INLINE size_t PartitionPage::get_raw_size() const {
  const size_t* ptr = get_raw_size_ptr();
  if (UNLIKELY(ptr != nullptr))
    return *ptr;
  return 0;
}

ALWAYS_INLINE void PartitionPage::Free(void* ptr) {
#if DCHECK_IS_ON()
  size_t slot_size = bucket->slot_size;
  const size_t raw_size = get_raw_size();
  if (raw_size) {
    slot_size = raw_size;
  }

  // If these asserts fire, you probably corrupted memory.
  PartitionCookieCheckValue(ptr);
  PartitionCookieCheckValue(reinterpret_cast<char*>(ptr) + slot_size -
                            kCookieSize);

  memset(ptr, kFreedByte, slot_size);
#endif

  DCHECK(num_allocated_slots);
  // Catches an immediate double free.
  CHECK(ptr != freelist_head);
  // Look for double free one level deeper in debug.
  DCHECK(!freelist_head ||
         ptr != EncodedPartitionFreelistEntry::Decode(freelist_head->next));
  internal::PartitionFreelistEntry* entry =
      static_cast<internal::PartitionFreelistEntry*>(ptr);
  entry->next = internal::PartitionFreelistEntry::Encode(freelist_head);
  freelist_head = entry;
  --num_allocated_slots;
  if (UNLIKELY(num_allocated_slots <= 0)) {
    FreeSlowPath();
  } else {
    // All single-slot allocations must go through the slow path to
    // correctly update the size metadata.
    DCHECK(get_raw_size() == 0);
  }
}

ALWAYS_INLINE bool PartitionPage::is_active() const {
  DCHECK(this != get_sentinel_page());
  DCHECK(!page_offset);
  return (num_allocated_slots > 0 &&
          (freelist_head || num_unprovisioned_slots));
}

ALWAYS_INLINE bool PartitionPage::is_full() const {
  DCHECK(this != get_sentinel_page());
  DCHECK(!page_offset);
  bool ret = (num_allocated_slots == bucket->get_slots_per_span());
  if (ret) {
    DCHECK(!freelist_head);
    DCHECK(!num_unprovisioned_slots);
  }
  return ret;
}

ALWAYS_INLINE bool PartitionPage::is_empty() const {
  DCHECK(this != get_sentinel_page());
  DCHECK(!page_offset);
  return (!num_allocated_slots && freelist_head);
}

ALWAYS_INLINE bool PartitionPage::is_decommitted() const {
  DCHECK(this != get_sentinel_page());
  DCHECK(!page_offset);
  bool ret = (!num_allocated_slots && !freelist_head);
  if (ret) {
    DCHECK(!num_unprovisioned_slots);
    DCHECK(empty_cache_index == -1);
  }
  return ret;
}

ALWAYS_INLINE void PartitionPage::set_raw_size(size_t size) {
  size_t* raw_size_ptr = get_raw_size_ptr();
  if (UNLIKELY(raw_size_ptr != nullptr))
    *raw_size_ptr = size;
}

ALWAYS_INLINE void PartitionPage::Reset() {
  DCHECK(is_decommitted());

  num_unprovisioned_slots = bucket->get_slots_per_span();
  DCHECK(num_unprovisioned_slots);

  next_page = nullptr;
}

}  // namespace internal
}  // namespace base

#endif  // BASE_ALLOCATOR_PARTITION_ALLOCATOR_PARTITION_PAGE_H_
