// Copyright (c) 2013 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

#ifndef BASE_ALLOCATOR_PARTITION_ALLOCATOR_PARTITION_ALLOC_H_
#define BASE_ALLOCATOR_PARTITION_ALLOCATOR_PARTITION_ALLOC_H_

// DESCRIPTION
// PartitionRoot::Alloc() / PartitionRootGeneric::Alloc() and PartitionFree() /
// PartitionRootGeneric::Free() are approximately analagous to malloc() and
// free().
//
// The main difference is that a PartitionRoot / PartitionRootGeneric object
// must be supplied to these functions, representing a specific "heap partition"
// that will be used to satisfy the allocation. Different partitions are
// guaranteed to exist in separate address spaces, including being separate from
// the main system heap. If the contained objects are all freed, physical memory
// is returned to the system but the address space remains reserved.
// See PartitionAlloc.md for other security properties PartitionAlloc provides.
//
// THE ONLY LEGITIMATE WAY TO OBTAIN A PartitionRoot IS THROUGH THE
// SizeSpecificPartitionAllocator / PartitionAllocatorGeneric classes. To
// minimize the instruction count to the fullest extent possible, the
// PartitionRoot is really just a header adjacent to other data areas provided
// by the allocator class.
//
// The PartitionRoot::Alloc() variant of the API has the following caveats:
// - Allocations and frees against a single partition must be single threaded.
// - Allocations must not exceed a max size, chosen at compile-time via a
// templated parameter to PartitionAllocator.
// - Allocation sizes must be aligned to the system pointer size.
// - Allocations are bucketed exactly according to size.
//
// And for PartitionRootGeneric::Alloc():
// - Multi-threaded use against a single partition is ok; locking is handled.
// - Allocations of any arbitrary size can be handled (subject to a limit of
// INT_MAX bytes for security reasons).
// - Bucketing is by approximate size, for example an allocation of 4000 bytes
// might be placed into a 4096-byte bucket. Bucket sizes are chosen to try and
// keep worst-case waste to ~10%.
//
// The allocators are designed to be extremely fast, thanks to the following
// properties and design:
// - Just two single (reasonably predicatable) branches in the hot / fast path
//   for both allocating and (significantly) freeing.
// - A minimal number of operations in the hot / fast path, with the slow paths
//   in separate functions, leading to the possibility of inlining.
// - Each partition page (which is usually multiple physical pages) has a
//   metadata structure which allows fast mapping of free() address to an
//   underlying bucket.
// - Supports a lock-free API for fast performance in single-threaded cases.
// - The freelist for a given bucket is split across a number of partition
//   pages, enabling various simple tricks to try and minimize fragmentation.
// - Fine-grained bucket sizes leading to less waste and better packing.
//
// The following security properties could be investigated in the future:
// - Per-object bucketing (instead of per-size) is mostly available at the API,
// but not used yet.
// - No randomness of freelist entries or bucket position.
// - Better checking for wild pointers in free().
// - Better freelist masking function to guarantee fault on 32-bit.

#include <limits.h>
#include <string.h>

#include "base/allocator/partition_allocator/memory_reclaimer.h"
#include "base/allocator/partition_allocator/page_allocator.h"
#include "base/allocator/partition_allocator/partition_alloc_constants.h"
#include "base/allocator/partition_allocator/partition_bucket.h"
#include "base/allocator/partition_allocator/partition_cookie.h"
#include "base/allocator/partition_allocator/partition_page.h"
#include "base/allocator/partition_allocator/partition_root_base.h"
#include "base/allocator/partition_allocator/spin_lock.h"
#include "base/base_export.h"
#include "base/bits.h"
#include "base/compiler_specific.h"
#include "base/logging.h"
#include "base/stl_util.h"
#include "base/sys_byteorder.h"
#include "build/build_config.h"

#if defined(MEMORY_TOOL_REPLACES_ALLOCATOR)
#include <stdlib.h>
#endif

// We use this to make MEMORY_TOOL_REPLACES_ALLOCATOR behave the same for max
// size as other alloc code.
#define CHECK_MAX_SIZE_OR_RETURN_NULLPTR(size, flags) \
  if (size > kGenericMaxDirectMapped) {               \
    if (flags & PartitionAllocReturnNull) {           \
      return nullptr;                                 \
    }                                                 \
    CHECK(false);                                     \
  }

namespace base {

class PartitionStatsDumper;

enum PartitionPurgeFlags {
  // Decommitting the ring list of empty pages is reasonably fast.
  PartitionPurgeDecommitEmptyPages = 1 << 0,
  // Discarding unused system pages is slower, because it involves walking all
  // freelists in all active partition pages of all buckets >= system page
  // size. It often frees a similar amount of memory to decommitting the empty
  // pages, though.
  PartitionPurgeDiscardUnusedSystemPages = 1 << 1,
};

// Never instantiate a PartitionRoot directly, instead use PartitionAlloc.
struct BASE_EXPORT PartitionRoot : public internal::PartitionRootBase {
  PartitionRoot();
  ~PartitionRoot() override;
  // This references the buckets OFF the edge of this struct. All uses of
  // PartitionRoot must have the bucket array come right after.
  //
  // The PartitionAlloc templated class ensures the following is correct.
  ALWAYS_INLINE internal::PartitionBucket* buckets() {
    return reinterpret_cast<internal::PartitionBucket*>(this + 1);
  }
  ALWAYS_INLINE const internal::PartitionBucket* buckets() const {
    return reinterpret_cast<const internal::PartitionBucket*>(this + 1);
  }

  void Init(size_t bucket_count, size_t maximum_allocation);

  ALWAYS_INLINE void* Alloc(size_t size, const char* type_name);
  ALWAYS_INLINE void* AllocFlags(int flags, size_t size, const char* type_name);

  void PurgeMemory(int flags) override;

  void DumpStats(const char* partition_name,
                 bool is_light_dump,
                 PartitionStatsDumper* dumper);
};

// Never instantiate a PartitionRootGeneric directly, instead use
// PartitionAllocatorGeneric.
struct BASE_EXPORT PartitionRootGeneric : public internal::PartitionRootBase {
  PartitionRootGeneric();
  ~PartitionRootGeneric() override;
  subtle::SpinLock lock;
  // Some pre-computed constants.
  size_t order_index_shifts[kBitsPerSizeT + 1] = {};
  size_t order_sub_index_masks[kBitsPerSizeT + 1] = {};
  // The bucket lookup table lets us map a size_t to a bucket quickly.
  // The trailing +1 caters for the overflow case for very large allocation
  // sizes.  It is one flat array instead of a 2D array because in the 2D
  // world, we'd need to index array[blah][max+1] which risks undefined
  // behavior.
  internal::PartitionBucket*
      bucket_lookups[((kBitsPerSizeT + 1) * kGenericNumBucketsPerOrder) + 1] =
          {};
  internal::PartitionBucket buckets[kGenericNumBuckets] = {};

  // Public API.
  void Init();

  ALWAYS_INLINE void* Alloc(size_t size, const char* type_name);
  ALWAYS_INLINE void* AllocFlags(int flags, size_t size, const char* type_name);
  ALWAYS_INLINE void Free(void* ptr);

  NOINLINE void* Realloc(void* ptr, size_t new_size, const char* type_name);
  // Overload that may return nullptr if reallocation isn't possible. In this
  // case, |ptr| remains valid.
  NOINLINE void* TryRealloc(void* ptr, size_t new_size, const char* type_name);

  ALWAYS_INLINE size_t ActualSize(size_t size);

  void PurgeMemory(int flags) override;

  void DumpStats(const char* partition_name,
                 bool is_light_dump,
                 PartitionStatsDumper* partition_stats_dumper);
};

// Struct used to retrieve total memory usage of a partition. Used by
// PartitionStatsDumper implementation.
struct PartitionMemoryStats {
  size_t total_mmapped_bytes;    // Total bytes mmaped from the system.
  size_t total_committed_bytes;  // Total size of commmitted pages.
  size_t total_resident_bytes;   // Total bytes provisioned by the partition.
  size_t total_active_bytes;     // Total active bytes in the partition.
  size_t total_decommittable_bytes;  // Total bytes that could be decommitted.
  size_t total_discardable_bytes;    // Total bytes that could be discarded.
};

// Struct used to retrieve memory statistics about a partition bucket. Used by
// PartitionStatsDumper implementation.
struct PartitionBucketMemoryStats {
  bool is_valid;       // Used to check if the stats is valid.
  bool is_direct_map;  // True if this is a direct mapping; size will not be
                       // unique.
  uint32_t bucket_slot_size;     // The size of the slot in bytes.
  uint32_t allocated_page_size;  // Total size the partition page allocated from
                                 // the system.
  uint32_t active_bytes;         // Total active bytes used in the bucket.
  uint32_t resident_bytes;       // Total bytes provisioned in the bucket.
  uint32_t decommittable_bytes;  // Total bytes that could be decommitted.
  uint32_t discardable_bytes;    // Total bytes that could be discarded.
  uint32_t num_full_pages;       // Number of pages with all slots allocated.
  uint32_t num_active_pages;     // Number of pages that have at least one
                                 // provisioned slot.
  uint32_t num_empty_pages;      // Number of pages that are empty
                                 // but not decommitted.
  uint32_t num_decommitted_pages;  // Number of pages that are empty
                                   // and decommitted.
};

// Interface that is passed to PartitionDumpStats and
// PartitionDumpStatsGeneric for using the memory statistics.
class BASE_EXPORT PartitionStatsDumper {
 public:
  // Called to dump total memory used by partition, once per partition.
  virtual void PartitionDumpTotals(const char* partition_name,
                                   const PartitionMemoryStats*) = 0;

  // Called to dump stats about buckets, for each bucket.
  virtual void PartitionsDumpBucketStats(const char* partition_name,
                                         const PartitionBucketMemoryStats*) = 0;
};

BASE_EXPORT void PartitionAllocGlobalInit(OomFunction on_out_of_memory);

// PartitionAlloc supports setting hooks to observe allocations/frees as they
// occur as well as 'override' hooks that allow overriding those operations.
class BASE_EXPORT PartitionAllocHooks {
 public:
  // Log allocation and free events.
  typedef void AllocationObserverHook(void* address,
                                      size_t size,
                                      const char* type_name);
  typedef void FreeObserverHook(void* address);

  // If it returns true, the allocation has been overridden with the pointer in
  // *out.
  typedef bool AllocationOverrideHook(void** out,
                                      int flags,
                                      size_t size,
                                      const char* type_name);
  // If it returns true, then the allocation was overridden and has been freed.
  typedef bool FreeOverrideHook(void* address);
  // If it returns true, the underlying allocation is overridden and *out holds
  // the size of the underlying allocation.
  typedef bool ReallocOverrideHook(size_t* out, void* address);

  // To unhook, call Set*Hooks with nullptrs.
  static void SetObserverHooks(AllocationObserverHook* alloc_hook,
                               FreeObserverHook* free_hook);
  static void SetOverrideHooks(AllocationOverrideHook* alloc_hook,
                               FreeOverrideHook* free_hook,
                               ReallocOverrideHook realloc_hook);

  // Helper method to check whether hooks are enabled. This is an optimization
  // so that if a function needs to call observer and override hooks in two
  // different places this value can be cached and only loaded once.
  static bool AreHooksEnabled() {
    return hooks_enabled_.load(std::memory_order_relaxed);
  }

  static void AllocationObserverHookIfEnabled(void* address,
                                              size_t size,
                                              const char* type_name);
  static bool AllocationOverrideHookIfEnabled(void** out,
                                              int flags,
                                              size_t size,
                                              const char* type_name);

  static void FreeObserverHookIfEnabled(void* address);
  static bool FreeOverrideHookIfEnabled(void* address);

  static void ReallocObserverHookIfEnabled(void* old_address,
                                           void* new_address,
                                           size_t size,
                                           const char* type_name);
  static bool ReallocOverrideHookIfEnabled(size_t* out, void* address);

 private:
  // Single bool that is used to indicate whether observer or allocation hooks
  // are set to reduce the numbers of loads required to check whether hooking is
  // enabled.
  static std::atomic<bool> hooks_enabled_;

  // Lock used to synchronize Set*Hooks calls.
  static std::atomic<AllocationObserverHook*> allocation_observer_hook_;
  static std::atomic<FreeObserverHook*> free_observer_hook_;

  static std::atomic<AllocationOverrideHook*> allocation_override_hook_;
  static std::atomic<FreeOverrideHook*> free_override_hook_;
  static std::atomic<ReallocOverrideHook*> realloc_override_hook_;
};

ALWAYS_INLINE void* PartitionRoot::Alloc(size_t size, const char* type_name) {
  return AllocFlags(0, size, type_name);
}

ALWAYS_INLINE void* PartitionRoot::AllocFlags(int flags,
                                              size_t size,
                                              const char* type_name) {
#if defined(MEMORY_TOOL_REPLACES_ALLOCATOR)
  CHECK_MAX_SIZE_OR_RETURN_NULLPTR(size, flags);
  void* result = malloc(size);
  CHECK(result);
  return result;
#else
  DCHECK(max_allocation == 0 || size <= max_allocation);
  void* result;
  const bool hooks_enabled = PartitionAllocHooks::AreHooksEnabled();
  if (UNLIKELY(hooks_enabled)) {
    if (PartitionAllocHooks::AllocationOverrideHookIfEnabled(&result, flags,
                                                             size, type_name)) {
      PartitionAllocHooks::AllocationObserverHookIfEnabled(result, size,
                                                           type_name);
      return result;
    }
  }
  size_t requested_size = size;
  size = internal::PartitionCookieSizeAdjustAdd(size);
  DCHECK(initialized);
  size_t index = size >> kBucketShift;
  DCHECK(index < num_buckets);
  DCHECK(size == index << kBucketShift);
  internal::PartitionBucket* bucket = &buckets()[index];
  result = AllocFromBucket(bucket, flags, size);
  if (UNLIKELY(hooks_enabled)) {
    PartitionAllocHooks::AllocationObserverHookIfEnabled(result, requested_size,
                                                         type_name);
  }
  return result;
#endif  // defined(MEMORY_TOOL_REPLACES_ALLOCATOR)
}

ALWAYS_INLINE bool PartitionAllocSupportsGetSize() {
#if defined(MEMORY_TOOL_REPLACES_ALLOCATOR)
  return false;
#else
  return true;
#endif
}

ALWAYS_INLINE size_t PartitionAllocGetSize(void* ptr) {
  // No need to lock here. Only |ptr| being freed by another thread could
  // cause trouble, and the caller is responsible for that not happening.
  DCHECK(PartitionAllocSupportsGetSize());
  ptr = internal::PartitionCookieFreePointerAdjust(ptr);
  internal::PartitionPage* page = internal::PartitionPage::FromPointer(ptr);
  // TODO(palmer): See if we can afford to make this a CHECK.
  DCHECK(internal::PartitionRootBase::IsValidPage(page));
  size_t size = page->bucket->slot_size;
  return internal::PartitionCookieSizeAdjustSubtract(size);
}

ALWAYS_INLINE void PartitionFree(void* ptr) {
#if defined(MEMORY_TOOL_REPLACES_ALLOCATOR)
  free(ptr);
#else
  // TODO(palmer): Check ptr alignment before continuing. Shall we do the check
  // inside PartitionCookieFreePointerAdjust?
  if (PartitionAllocHooks::AreHooksEnabled()) {
    PartitionAllocHooks::FreeObserverHookIfEnabled(ptr);
    if (PartitionAllocHooks::FreeOverrideHookIfEnabled(ptr))
      return;
  }

  ptr = internal::PartitionCookieFreePointerAdjust(ptr);
  internal::PartitionPage* page = internal::PartitionPage::FromPointer(ptr);
  // TODO(palmer): See if we can afford to make this a CHECK.
  DCHECK(internal::PartitionRootBase::IsValidPage(page));
  page->Free(ptr);
#endif
}

ALWAYS_INLINE internal::PartitionBucket* PartitionGenericSizeToBucket(
    PartitionRootGeneric* root,
    size_t size) {
  size_t order = kBitsPerSizeT - bits::CountLeadingZeroBitsSizeT(size);
  // The order index is simply the next few bits after the most significant bit.
  size_t order_index = (size >> root->order_index_shifts[order]) &
                       (kGenericNumBucketsPerOrder - 1);
  // And if the remaining bits are non-zero we must bump the bucket up.
  size_t sub_order_index = size & root->order_sub_index_masks[order];
  internal::PartitionBucket* bucket =
      root->bucket_lookups[(order << kGenericNumBucketsPerOrderBits) +
                           order_index + !!sub_order_index];
  CHECK(bucket);
  DCHECK(!bucket->slot_size || bucket->slot_size >= size);
  DCHECK(!(bucket->slot_size % kGenericSmallestBucket));
  return bucket;
}

ALWAYS_INLINE void* PartitionAllocGenericFlags(PartitionRootGeneric* root,
                                               int flags,
                                               size_t size,
                                               const char* type_name) {
  DCHECK_LT(flags, PartitionAllocLastFlag << 1);

#if defined(MEMORY_TOOL_REPLACES_ALLOCATOR)
  CHECK_MAX_SIZE_OR_RETURN_NULLPTR(size, flags);
  const bool zero_fill = flags & PartitionAllocZeroFill;
  void* result = zero_fill ? calloc(1, size) : malloc(size);
  CHECK(result || flags & PartitionAllocReturnNull);
  return result;
#else
  DCHECK(root->initialized);
  // Only SizeSpecificPartitionAllocator should use max_allocation.
  DCHECK(root->max_allocation == 0);
  void* result;
  const bool hooks_enabled = PartitionAllocHooks::AreHooksEnabled();
  if (UNLIKELY(hooks_enabled)) {
    if (PartitionAllocHooks::AllocationOverrideHookIfEnabled(&result, flags,
                                                             size, type_name)) {
      PartitionAllocHooks::AllocationObserverHookIfEnabled(result, size,
                                                           type_name);
      return result;
    }
  }
  size_t requested_size = size;
  size = internal::PartitionCookieSizeAdjustAdd(size);
  internal::PartitionBucket* bucket = PartitionGenericSizeToBucket(root, size);
  {
    subtle::SpinLock::Guard guard(root->lock);
    result = root->AllocFromBucket(bucket, flags, size);
  }
  if (UNLIKELY(hooks_enabled)) {
    PartitionAllocHooks::AllocationObserverHookIfEnabled(result, requested_size,
                                                         type_name);
  }

  return result;
#endif
}

ALWAYS_INLINE void* PartitionRootGeneric::Alloc(size_t size,
                                                const char* type_name) {
  return PartitionAllocGenericFlags(this, 0, size, type_name);
}

ALWAYS_INLINE void* PartitionRootGeneric::AllocFlags(int flags,
                                                     size_t size,
                                                     const char* type_name) {
  return PartitionAllocGenericFlags(this, flags, size, type_name);
}

ALWAYS_INLINE void PartitionRootGeneric::Free(void* ptr) {
#if defined(MEMORY_TOOL_REPLACES_ALLOCATOR)
  free(ptr);
#else
  DCHECK(initialized);

  if (UNLIKELY(!ptr))
    return;

  if (PartitionAllocHooks::AreHooksEnabled()) {
    PartitionAllocHooks::FreeObserverHookIfEnabled(ptr);
    if (PartitionAllocHooks::FreeOverrideHookIfEnabled(ptr))
      return;
  }

  ptr = internal::PartitionCookieFreePointerAdjust(ptr);
  internal::PartitionPage* page = internal::PartitionPage::FromPointer(ptr);
  // TODO(palmer): See if we can afford to make this a CHECK.
  DCHECK(IsValidPage(page));
  {
    subtle::SpinLock::Guard guard(lock);
    page->Free(ptr);
  }
#endif
}

BASE_EXPORT void* PartitionReallocGenericFlags(PartitionRootGeneric* root,
                                               int flags,
                                               void* ptr,
                                               size_t new_size,
                                               const char* type_name);

ALWAYS_INLINE size_t PartitionRootGeneric::ActualSize(size_t size) {
#if defined(MEMORY_TOOL_REPLACES_ALLOCATOR)
  return size;
#else
  DCHECK(initialized);
  size = internal::PartitionCookieSizeAdjustAdd(size);
  internal::PartitionBucket* bucket = PartitionGenericSizeToBucket(this, size);
  if (LIKELY(!bucket->is_direct_mapped())) {
    size = bucket->slot_size;
  } else if (size > kGenericMaxDirectMapped) {
    // Too large to allocate => return the size unchanged.
  } else {
    size = internal::PartitionBucket::get_direct_map_size(size);
  }
  return internal::PartitionCookieSizeAdjustSubtract(size);
#endif
}

template <size_t N>
class SizeSpecificPartitionAllocator {
 public:
  SizeSpecificPartitionAllocator() {
    memset(actual_buckets_, 0,
           sizeof(internal::PartitionBucket) * base::size(actual_buckets_));
  }
  ~SizeSpecificPartitionAllocator() {
    PartitionAllocMemoryReclaimer::Instance()->UnregisterPartition(
        &partition_root_);
  }
  static const size_t kMaxAllocation = N - kAllocationGranularity;
  static const size_t kNumBuckets = N / kAllocationGranularity;
  void init() {
    partition_root_.Init(kNumBuckets, kMaxAllocation);
    PartitionAllocMemoryReclaimer::Instance()->RegisterPartition(
        &partition_root_);
  }
  ALWAYS_INLINE PartitionRoot* root() { return &partition_root_; }

 private:
  PartitionRoot partition_root_;
  internal::PartitionBucket actual_buckets_[kNumBuckets];
};

class BASE_EXPORT PartitionAllocatorGeneric {
 public:
  PartitionAllocatorGeneric();
  ~PartitionAllocatorGeneric() {
    PartitionAllocMemoryReclaimer::Instance()->UnregisterPartition(
        &partition_root_);
  }

  void init() {
    partition_root_.Init();
    PartitionAllocMemoryReclaimer::Instance()->RegisterPartition(
        &partition_root_);
  }
  ALWAYS_INLINE PartitionRootGeneric* root() { return &partition_root_; }

 private:
  PartitionRootGeneric partition_root_;
};

}  // namespace base

#endif  // BASE_ALLOCATOR_PARTITION_ALLOCATOR_PARTITION_ALLOC_H_
