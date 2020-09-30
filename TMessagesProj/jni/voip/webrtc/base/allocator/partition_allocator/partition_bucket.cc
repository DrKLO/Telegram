// Copyright (c) 2018 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

#include "base/allocator/partition_allocator/partition_bucket.h"

#include "base/allocator/partition_allocator/oom.h"
#include "base/allocator/partition_allocator/page_allocator.h"
#include "base/allocator/partition_allocator/partition_alloc_constants.h"
#include "base/allocator/partition_allocator/partition_direct_map_extent.h"
#include "base/allocator/partition_allocator/partition_oom.h"
#include "base/allocator/partition_allocator/partition_page.h"
#include "base/allocator/partition_allocator/partition_root_base.h"
#include "base/logging.h"
#include "build/build_config.h"

namespace base {
namespace internal {

namespace {

ALWAYS_INLINE PartitionPage* PartitionDirectMap(PartitionRootBase* root,
                                                int flags,
                                                size_t raw_size) {
  size_t size = PartitionBucket::get_direct_map_size(raw_size);

  // Because we need to fake looking like a super page, we need to allocate
  // a bunch of system pages more than "size":
  // - The first few system pages are the partition page in which the super
  // page metadata is stored. We fault just one system page out of a partition
  // page sized clump.
  // - We add a trailing guard page on 32-bit (on 64-bit we rely on the
  // massive address space plus randomization instead).
  size_t map_size = size + kPartitionPageSize;
#if !defined(ARCH_CPU_64_BITS)
  map_size += kSystemPageSize;
#endif
  // Round up to the allocation granularity.
  map_size += kPageAllocationGranularityOffsetMask;
  map_size &= kPageAllocationGranularityBaseMask;

  char* ptr = reinterpret_cast<char*>(AllocPages(nullptr, map_size,
                                                 kSuperPageSize, PageReadWrite,
                                                 PageTag::kPartitionAlloc));
  if (UNLIKELY(!ptr))
    return nullptr;

  size_t committed_page_size = size + kSystemPageSize;
  root->total_size_of_direct_mapped_pages += committed_page_size;
  root->IncreaseCommittedPages(committed_page_size);

  char* slot = ptr + kPartitionPageSize;
  SetSystemPagesAccess(ptr + (kSystemPageSize * 2),
                       kPartitionPageSize - (kSystemPageSize * 2),
                       PageInaccessible);
#if !defined(ARCH_CPU_64_BITS)
  SetSystemPagesAccess(ptr, kSystemPageSize, PageInaccessible);
  SetSystemPagesAccess(slot + size, kSystemPageSize, PageInaccessible);
#endif

  PartitionSuperPageExtentEntry* extent =
      reinterpret_cast<PartitionSuperPageExtentEntry*>(
          PartitionSuperPageToMetadataArea(ptr));
  extent->root = root;
  // The new structures are all located inside a fresh system page so they
  // will all be zeroed out. These DCHECKs are for documentation.
  DCHECK(!extent->super_page_base);
  DCHECK(!extent->super_pages_end);
  DCHECK(!extent->next);
  PartitionPage* page = PartitionPage::FromPointerNoAlignmentCheck(slot);
  PartitionBucket* bucket = reinterpret_cast<PartitionBucket*>(
      reinterpret_cast<char*>(page) + (kPageMetadataSize * 2));
  DCHECK(!page->next_page);
  DCHECK(!page->num_allocated_slots);
  DCHECK(!page->num_unprovisioned_slots);
  DCHECK(!page->page_offset);
  DCHECK(!page->empty_cache_index);
  page->bucket = bucket;
  page->freelist_head = reinterpret_cast<PartitionFreelistEntry*>(slot);
  PartitionFreelistEntry* next_entry =
      reinterpret_cast<PartitionFreelistEntry*>(slot);
  next_entry->next = PartitionFreelistEntry::Encode(nullptr);

  DCHECK(!bucket->active_pages_head);
  DCHECK(!bucket->empty_pages_head);
  DCHECK(!bucket->decommitted_pages_head);
  DCHECK(!bucket->num_system_pages_per_slot_span);
  DCHECK(!bucket->num_full_pages);
  bucket->slot_size = size;

  PartitionDirectMapExtent* map_extent =
      PartitionDirectMapExtent::FromPage(page);
  map_extent->map_size = map_size - kPartitionPageSize - kSystemPageSize;
  map_extent->bucket = bucket;

  // Maintain the doubly-linked list of all direct mappings.
  map_extent->next_extent = root->direct_map_list;
  if (map_extent->next_extent)
    map_extent->next_extent->prev_extent = map_extent;
  map_extent->prev_extent = nullptr;
  root->direct_map_list = map_extent;

  return page;
}

}  // namespace

// static
PartitionBucket PartitionBucket::sentinel_bucket_;

PartitionBucket* PartitionBucket::get_sentinel_bucket() {
  return &sentinel_bucket_;
}

// TODO(ajwong): This seems to interact badly with
// get_pages_per_slot_span() which rounds the value from this up to a
// multiple of kNumSystemPagesPerPartitionPage (aka 4) anyways.
// http://crbug.com/776537
//
// TODO(ajwong): The waste calculation seems wrong. The PTE usage should cover
// both used and unsed pages.
// http://crbug.com/776537
uint8_t PartitionBucket::get_system_pages_per_slot_span() {
  // This works out reasonably for the current bucket sizes of the generic
  // allocator, and the current values of partition page size and constants.
  // Specifically, we have enough room to always pack the slots perfectly into
  // some number of system pages. The only waste is the waste associated with
  // unfaulted pages (i.e. wasted address space).
  // TODO: we end up using a lot of system pages for very small sizes. For
  // example, we'll use 12 system pages for slot size 24. The slot size is
  // so small that the waste would be tiny with just 4, or 1, system pages.
  // Later, we can investigate whether there are anti-fragmentation benefits
  // to using fewer system pages.
  double best_waste_ratio = 1.0f;
  uint16_t best_pages = 0;
  if (slot_size > kMaxSystemPagesPerSlotSpan * kSystemPageSize) {
    // TODO(ajwong): Why is there a DCHECK here for this?
    // http://crbug.com/776537
    DCHECK(!(slot_size % kSystemPageSize));
    best_pages = static_cast<uint16_t>(slot_size / kSystemPageSize);
    // TODO(ajwong): Should this be checking against
    // kMaxSystemPagesPerSlotSpan or numeric_limits<uint8_t>::max?
    // http://crbug.com/776537
    CHECK(best_pages < (1 << 8));
    return static_cast<uint8_t>(best_pages);
  }
  DCHECK(slot_size <= kMaxSystemPagesPerSlotSpan * kSystemPageSize);
  for (uint16_t i = kNumSystemPagesPerPartitionPage - 1;
       i <= kMaxSystemPagesPerSlotSpan; ++i) {
    size_t page_size = kSystemPageSize * i;
    size_t num_slots = page_size / slot_size;
    size_t waste = page_size - (num_slots * slot_size);
    // Leaving a page unfaulted is not free; the page will occupy an empty page
    // table entry.  Make a simple attempt to account for that.
    //
    // TODO(ajwong): This looks wrong. PTEs are allocated for all pages
    // regardless of whether or not they are wasted. Should it just
    // be waste += i * sizeof(void*)?
    // http://crbug.com/776537
    size_t num_remainder_pages = i & (kNumSystemPagesPerPartitionPage - 1);
    size_t num_unfaulted_pages =
        num_remainder_pages
            ? (kNumSystemPagesPerPartitionPage - num_remainder_pages)
            : 0;
    waste += sizeof(void*) * num_unfaulted_pages;
    double waste_ratio =
        static_cast<double>(waste) / static_cast<double>(page_size);
    if (waste_ratio < best_waste_ratio) {
      best_waste_ratio = waste_ratio;
      best_pages = i;
    }
  }
  DCHECK(best_pages > 0);
  CHECK(best_pages <= kMaxSystemPagesPerSlotSpan);
  return static_cast<uint8_t>(best_pages);
}

void PartitionBucket::Init(uint32_t new_slot_size) {
  slot_size = new_slot_size;
  active_pages_head = PartitionPage::get_sentinel_page();
  empty_pages_head = nullptr;
  decommitted_pages_head = nullptr;
  num_full_pages = 0;
  num_system_pages_per_slot_span = get_system_pages_per_slot_span();
}

NOINLINE void PartitionBucket::OnFull() {
  OOM_CRASH(0);
}

ALWAYS_INLINE void* PartitionBucket::AllocNewSlotSpan(
    PartitionRootBase* root,
    int flags,
    uint16_t num_partition_pages) {
  DCHECK(!(reinterpret_cast<uintptr_t>(root->next_partition_page) %
           kPartitionPageSize));
  DCHECK(!(reinterpret_cast<uintptr_t>(root->next_partition_page_end) %
           kPartitionPageSize));
  DCHECK(num_partition_pages <= kNumPartitionPagesPerSuperPage);
  size_t total_size = kPartitionPageSize * num_partition_pages;
  size_t num_partition_pages_left =
      (root->next_partition_page_end - root->next_partition_page) >>
      kPartitionPageShift;
  if (LIKELY(num_partition_pages_left >= num_partition_pages)) {
    // In this case, we can still hand out pages from the current super page
    // allocation.
    char* ret = root->next_partition_page;

    // Fresh System Pages in the SuperPages are decommited. Commit them
    // before vending them back.
    SetSystemPagesAccess(ret, total_size, PageReadWrite);

    root->next_partition_page += total_size;
    root->IncreaseCommittedPages(total_size);
    return ret;
  }

  // Need a new super page. We want to allocate super pages in a continguous
  // address region as much as possible. This is important for not causing
  // page table bloat and not fragmenting address spaces in 32 bit
  // architectures.
  char* requested_address = root->next_super_page;
  char* super_page = reinterpret_cast<char*>(
      AllocPages(requested_address, kSuperPageSize, kSuperPageSize,
                 PageReadWrite, PageTag::kPartitionAlloc));
  if (UNLIKELY(!super_page))
    return nullptr;

  root->total_size_of_super_pages += kSuperPageSize;
  root->IncreaseCommittedPages(total_size);

  // |total_size| MUST be less than kSuperPageSize - (kPartitionPageSize*2).
  // This is a trustworthy value because num_partition_pages is not user
  // controlled.
  //
  // TODO(ajwong): Introduce a DCHECK.
  root->next_super_page = super_page + kSuperPageSize;
  char* ret = super_page + kPartitionPageSize;
  root->next_partition_page = ret + total_size;
  root->next_partition_page_end = root->next_super_page - kPartitionPageSize;
  // Make the first partition page in the super page a guard page, but leave a
  // hole in the middle.
  // This is where we put page metadata and also a tiny amount of extent
  // metadata.
  SetSystemPagesAccess(super_page, kSystemPageSize, PageInaccessible);
  SetSystemPagesAccess(super_page + (kSystemPageSize * 2),
                       kPartitionPageSize - (kSystemPageSize * 2),
                       PageInaccessible);
  //  SetSystemPagesAccess(super_page + (kSuperPageSize -
  //  kPartitionPageSize),
  //                             kPartitionPageSize, PageInaccessible);
  // All remaining slotspans for the unallocated PartitionPages inside the
  // SuperPage are conceptually decommitted. Correctly set the state here
  // so they do not occupy resources.
  //
  // TODO(ajwong): Refactor Page Allocator API so the SuperPage comes in
  // decommited initially.
  SetSystemPagesAccess(super_page + kPartitionPageSize + total_size,
                       (kSuperPageSize - kPartitionPageSize - total_size),
                       PageInaccessible);

  // If we were after a specific address, but didn't get it, assume that
  // the system chose a lousy address. Here most OS'es have a default
  // algorithm that isn't randomized. For example, most Linux
  // distributions will allocate the mapping directly before the last
  // successful mapping, which is far from random. So we just get fresh
  // randomness for the next mapping attempt.
  if (requested_address && requested_address != super_page)
    root->next_super_page = nullptr;

  // We allocated a new super page so update super page metadata.
  // First check if this is a new extent or not.
  PartitionSuperPageExtentEntry* latest_extent =
      reinterpret_cast<PartitionSuperPageExtentEntry*>(
          PartitionSuperPageToMetadataArea(super_page));
  // By storing the root in every extent metadata object, we have a fast way
  // to go from a pointer within the partition to the root object.
  latest_extent->root = root;
  // Most new extents will be part of a larger extent, and these three fields
  // are unused, but we initialize them to 0 so that we get a clear signal
  // in case they are accidentally used.
  latest_extent->super_page_base = nullptr;
  latest_extent->super_pages_end = nullptr;
  latest_extent->next = nullptr;

  PartitionSuperPageExtentEntry* current_extent = root->current_extent;
  bool is_new_extent = (super_page != requested_address);
  if (UNLIKELY(is_new_extent)) {
    if (UNLIKELY(!current_extent)) {
      DCHECK(!root->first_extent);
      root->first_extent = latest_extent;
    } else {
      DCHECK(current_extent->super_page_base);
      current_extent->next = latest_extent;
    }
    root->current_extent = latest_extent;
    latest_extent->super_page_base = super_page;
    latest_extent->super_pages_end = super_page + kSuperPageSize;
  } else {
    // We allocated next to an existing extent so just nudge the size up a
    // little.
    DCHECK(current_extent->super_pages_end);
    current_extent->super_pages_end += kSuperPageSize;
    DCHECK(ret >= current_extent->super_page_base &&
           ret < current_extent->super_pages_end);
  }
  return ret;
}

ALWAYS_INLINE uint16_t PartitionBucket::get_pages_per_slot_span() {
  // Rounds up to nearest multiple of kNumSystemPagesPerPartitionPage.
  return (num_system_pages_per_slot_span +
          (kNumSystemPagesPerPartitionPage - 1)) /
         kNumSystemPagesPerPartitionPage;
}

ALWAYS_INLINE void PartitionBucket::InitializeSlotSpan(PartitionPage* page) {
  // The bucket never changes. We set it up once.
  page->bucket = this;
  page->empty_cache_index = -1;

  page->Reset();

  // If this page has just a single slot, do not set up page offsets for any
  // page metadata other than the first one. This ensures that attempts to
  // touch invalid page metadata fail.
  if (page->num_unprovisioned_slots == 1)
    return;

  uint16_t num_partition_pages = get_pages_per_slot_span();
  char* page_char_ptr = reinterpret_cast<char*>(page);
  for (uint16_t i = 1; i < num_partition_pages; ++i) {
    page_char_ptr += kPageMetadataSize;
    PartitionPage* secondary_page =
        reinterpret_cast<PartitionPage*>(page_char_ptr);
    secondary_page->page_offset = i;
  }
}

ALWAYS_INLINE char* PartitionBucket::AllocAndFillFreelist(PartitionPage* page) {
  DCHECK(page != PartitionPage::get_sentinel_page());
  uint16_t num_slots = page->num_unprovisioned_slots;
  DCHECK(num_slots);
  // We should only get here when _every_ slot is either used or unprovisioned.
  // (The third state is "on the freelist". If we have a non-empty freelist, we
  // should not get here.)
  DCHECK(num_slots + page->num_allocated_slots == get_slots_per_span());
  // Similarly, make explicitly sure that the freelist is empty.
  DCHECK(!page->freelist_head);
  DCHECK(page->num_allocated_slots >= 0);

  size_t size = slot_size;
  char* base = reinterpret_cast<char*>(PartitionPage::ToPointer(page));
  char* return_object = base + (size * page->num_allocated_slots);
  char* first_freelist_pointer = return_object + size;
  char* first_freelist_pointer_extent =
      first_freelist_pointer + sizeof(PartitionFreelistEntry*);
  // Our goal is to fault as few system pages as possible. We calculate the
  // page containing the "end" of the returned slot, and then allow freelist
  // pointers to be written up to the end of that page.
  char* sub_page_limit = reinterpret_cast<char*>(
      RoundUpToSystemPage(reinterpret_cast<size_t>(first_freelist_pointer)));
  char* slots_limit = return_object + (size * num_slots);
  char* freelist_limit = sub_page_limit;
  if (UNLIKELY(slots_limit < freelist_limit))
    freelist_limit = slots_limit;

  uint16_t num_new_freelist_entries = 0;
  if (LIKELY(first_freelist_pointer_extent <= freelist_limit)) {
    // Only consider used space in the slot span. If we consider wasted
    // space, we may get an off-by-one when a freelist pointer fits in the
    // wasted space, but a slot does not.
    // We know we can fit at least one freelist pointer.
    num_new_freelist_entries = 1;
    // Any further entries require space for the whole slot span.
    num_new_freelist_entries += static_cast<uint16_t>(
        (freelist_limit - first_freelist_pointer_extent) / size);
  }

  // We always return an object slot -- that's the +1 below.
  // We do not neccessarily create any new freelist entries, because we cross
  // sub page boundaries frequently for large bucket sizes.
  DCHECK(num_new_freelist_entries + 1 <= num_slots);
  num_slots -= (num_new_freelist_entries + 1);
  page->num_unprovisioned_slots = num_slots;
  page->num_allocated_slots++;

  if (LIKELY(num_new_freelist_entries)) {
    char* freelist_pointer = first_freelist_pointer;
    PartitionFreelistEntry* entry =
        reinterpret_cast<PartitionFreelistEntry*>(freelist_pointer);
    page->freelist_head = entry;
    while (--num_new_freelist_entries) {
      freelist_pointer += size;
      PartitionFreelistEntry* next_entry =
          reinterpret_cast<PartitionFreelistEntry*>(freelist_pointer);
      entry->next = PartitionFreelistEntry::Encode(next_entry);
      entry = next_entry;
    }
    entry->next = PartitionFreelistEntry::Encode(nullptr);
  } else {
    page->freelist_head = nullptr;
  }
  return return_object;
}

bool PartitionBucket::SetNewActivePage() {
  PartitionPage* page = active_pages_head;
  if (page == PartitionPage::get_sentinel_page())
    return false;

  PartitionPage* next_page;

  for (; page; page = next_page) {
    next_page = page->next_page;
    DCHECK(page->bucket == this);
    DCHECK(page != empty_pages_head);
    DCHECK(page != decommitted_pages_head);

    if (LIKELY(page->is_active())) {
      // This page is usable because it has freelist entries, or has
      // unprovisioned slots we can create freelist entries from.
      active_pages_head = page;
      return true;
    }

    // Deal with empty and decommitted pages.
    if (LIKELY(page->is_empty())) {
      page->next_page = empty_pages_head;
      empty_pages_head = page;
    } else if (LIKELY(page->is_decommitted())) {
      page->next_page = decommitted_pages_head;
      decommitted_pages_head = page;
    } else {
      DCHECK(page->is_full());
      // If we get here, we found a full page. Skip over it too, and also
      // tag it as full (via a negative value). We need it tagged so that
      // free'ing can tell, and move it back into the active page list.
      page->num_allocated_slots = -page->num_allocated_slots;
      ++num_full_pages;
      // num_full_pages is a uint16_t for efficient packing so guard against
      // overflow to be safe.
      if (UNLIKELY(!num_full_pages))
        OnFull();
      // Not necessary but might help stop accidents.
      page->next_page = nullptr;
    }
  }

  active_pages_head = PartitionPage::get_sentinel_page();
  return false;
}

void* PartitionBucket::SlowPathAlloc(PartitionRootBase* root,
                                     int flags,
                                     size_t size,
                                     bool* is_already_zeroed) {
  // The slow path is called when the freelist is empty.
  DCHECK(!active_pages_head->freelist_head);

  PartitionPage* new_page = nullptr;
  *is_already_zeroed = false;

  // For the PartitionRootGeneric::Alloc() API, we have a bunch of buckets
  // marked as special cases. We bounce them through to the slow path so that
  // we can still have a blazing fast hot path due to lack of corner-case
  // branches.
  //
  // Note: The ordering of the conditionals matter! In particular,
  // SetNewActivePage() has a side-effect even when returning
  // false where it sweeps the active page list and may move things into
  // the empty or decommitted lists which affects the subsequent conditional.
  bool return_null = flags & PartitionAllocReturnNull;
  if (UNLIKELY(is_direct_mapped())) {
    DCHECK(size > kGenericMaxBucketed);
    DCHECK(this == get_sentinel_bucket());
    DCHECK(active_pages_head == PartitionPage::get_sentinel_page());
    if (size > kGenericMaxDirectMapped) {
      if (return_null)
        return nullptr;
      PartitionExcessiveAllocationSize(size);
    }
    new_page = PartitionDirectMap(root, flags, size);
    *is_already_zeroed = true;
  } else if (LIKELY(SetNewActivePage())) {
    // First, did we find an active page in the active pages list?
    new_page = active_pages_head;
    DCHECK(new_page->is_active());
  } else if (LIKELY(empty_pages_head != nullptr) ||
             LIKELY(decommitted_pages_head != nullptr)) {
    // Second, look in our lists of empty and decommitted pages.
    // Check empty pages first, which are preferred, but beware that an
    // empty page might have been decommitted.
    while (LIKELY((new_page = empty_pages_head) != nullptr)) {
      DCHECK(new_page->bucket == this);
      DCHECK(new_page->is_empty() || new_page->is_decommitted());
      empty_pages_head = new_page->next_page;
      // Accept the empty page unless it got decommitted.
      if (new_page->freelist_head) {
        new_page->next_page = nullptr;
        break;
      }
      DCHECK(new_page->is_decommitted());
      new_page->next_page = decommitted_pages_head;
      decommitted_pages_head = new_page;
    }
    if (UNLIKELY(!new_page) && LIKELY(decommitted_pages_head != nullptr)) {
      new_page = decommitted_pages_head;
      DCHECK(new_page->bucket == this);
      DCHECK(new_page->is_decommitted());
      decommitted_pages_head = new_page->next_page;
      void* addr = PartitionPage::ToPointer(new_page);
      root->RecommitSystemPages(addr, new_page->bucket->get_bytes_per_span());
      new_page->Reset();
      // TODO(https://crbug.com/890752): Optimizing here might cause pages to
      // not be zeroed.
      // *is_already_zeroed = true;
    }
    DCHECK(new_page);
  } else {
    // Third. If we get here, we need a brand new page.
    uint16_t num_partition_pages = get_pages_per_slot_span();
    void* raw_pages = AllocNewSlotSpan(root, flags, num_partition_pages);
    if (LIKELY(raw_pages != nullptr)) {
      new_page = PartitionPage::FromPointerNoAlignmentCheck(raw_pages);
      InitializeSlotSpan(new_page);
      // TODO(https://crbug.com/890752): Optimizing here causes pages to not be
      // zeroed on at least macOS.
      // *is_already_zeroed = true;
    }
  }

  // Bail if we had a memory allocation failure.
  if (UNLIKELY(!new_page)) {
    DCHECK(active_pages_head == PartitionPage::get_sentinel_page());
    if (return_null)
      return nullptr;
    root->OutOfMemory(size);
  }

  // TODO(ajwong): Is there a way to avoid the reading of bucket here?
  // It seems like in many of the conditional branches above, |this| ==
  // |new_page->bucket|. Maybe pull this into another function?
  PartitionBucket* bucket = new_page->bucket;
  DCHECK(bucket != get_sentinel_bucket());
  bucket->active_pages_head = new_page;
  new_page->set_raw_size(size);

  // If we found an active page with free slots, or an empty page, we have a
  // usable freelist head.
  if (LIKELY(new_page->freelist_head != nullptr)) {
    PartitionFreelistEntry* entry = new_page->freelist_head;
    PartitionFreelistEntry* new_head =
        EncodedPartitionFreelistEntry::Decode(entry->next);
    new_page->freelist_head = new_head;
    new_page->num_allocated_slots++;
    return entry;
  }
  // Otherwise, we need to build the freelist.
  DCHECK(new_page->num_unprovisioned_slots);
  return AllocAndFillFreelist(new_page);
}

}  // namespace internal
}  // namespace base
