// Copyright 2016 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

// This code should move into the default Windows shim once the win-specific
// allocation shim has been removed, and the generic shim has becaome the
// default.

#include "winheap_stubs_win.h"

#include <limits.h>
#include <malloc.h>
#include <new.h>
#include <windows.h>
#include <algorithm>
#include <limits>

#include "base/bits.h"
#include "base/logging.h"

namespace base {
namespace allocator {

bool g_is_win_shim_layer_initialized = false;

namespace {

const size_t kWindowsPageSize = 4096;
const size_t kMaxWindowsAllocation = INT_MAX - kWindowsPageSize;

inline HANDLE get_heap_handle() {
  return reinterpret_cast<HANDLE>(_get_heap_handle());
}

}  // namespace

void* WinHeapMalloc(size_t size) {
  if (size < kMaxWindowsAllocation)
    return HeapAlloc(get_heap_handle(), 0, size);
  return nullptr;
}

void WinHeapFree(void* ptr) {
  if (!ptr)
    return;

  HeapFree(get_heap_handle(), 0, ptr);
}

void* WinHeapRealloc(void* ptr, size_t size) {
  if (!ptr)
    return WinHeapMalloc(size);
  if (!size) {
    WinHeapFree(ptr);
    return nullptr;
  }
  if (size < kMaxWindowsAllocation)
    return HeapReAlloc(get_heap_handle(), 0, ptr, size);
  return nullptr;
}

size_t WinHeapGetSizeEstimate(void* ptr) {
  if (!ptr)
    return 0;

  return HeapSize(get_heap_handle(), 0, ptr);
}

// Call the new handler, if one has been set.
// Returns true on successfully calling the handler, false otherwise.
bool WinCallNewHandler(size_t size) {
#ifdef _CPPUNWIND
#error "Exceptions in allocator shim are not supported!"
#endif  // _CPPUNWIND
  // Get the current new handler.
  _PNH nh = _query_new_handler();
  if (!nh)
    return false;
  // Since exceptions are disabled, we don't really know if new_handler
  // failed.  Assume it will abort if it fails.
  return nh(size) ? true : false;
}

// The Windows _aligned_* functions are implemented by creating an allocation
// with enough space to create an aligned allocation internally. The offset to
// the original allocation is prefixed to the aligned allocation so that it can
// be correctly freed.

namespace {

struct AlignedPrefix {
  // Offset to the original allocation point.
  unsigned int original_allocation_offset;
  // Make sure an unsigned int is enough to store the offset
  static_assert(
      kMaxWindowsAllocation < std::numeric_limits<unsigned int>::max(),
      "original_allocation_offset must be able to fit into an unsigned int");
#if DCHECK_IS_ON()
  // Magic value used to check that _aligned_free() and _aligned_realloc() are
  // only ever called on an aligned allocated chunk.
  static constexpr unsigned int kMagic = 0x12003400;
  unsigned int magic;
#endif  // DCHECK_IS_ON()
};

// Compute how large an allocation we need to fit an allocation with the given
// size and alignment and space for a prefix pointer.
size_t AdjustedSize(size_t size, size_t alignment) {
  // Minimal alignment is the prefix size so the prefix is properly aligned.
  alignment = std::max(alignment, alignof(AlignedPrefix));
  return size + sizeof(AlignedPrefix) + alignment - 1;
}

// Align the allocation and write the prefix.
void* AlignAllocation(void* ptr, size_t alignment) {
  // Minimal alignment is the prefix size so the prefix is properly aligned.
  alignment = std::max(alignment, alignof(AlignedPrefix));

  uintptr_t address = reinterpret_cast<uintptr_t>(ptr);
  address = base::bits::Align(address + sizeof(AlignedPrefix), alignment);

  // Write the prefix.
  AlignedPrefix* prefix = reinterpret_cast<AlignedPrefix*>(address) - 1;
  prefix->original_allocation_offset =
      address - reinterpret_cast<uintptr_t>(ptr);
#if DCHECK_IS_ON()
  prefix->magic = AlignedPrefix::kMagic;
#endif  // DCHECK_IS_ON()
  return reinterpret_cast<void*>(address);
}

// Return the original allocation from an aligned allocation.
void* UnalignAllocation(void* ptr) {
  AlignedPrefix* prefix = reinterpret_cast<AlignedPrefix*>(ptr) - 1;
#if DCHECK_IS_ON()
  DCHECK_EQ(prefix->magic, AlignedPrefix::kMagic);
#endif  // DCHECK_IS_ON()
  void* unaligned =
      static_cast<uint8_t*>(ptr) - prefix->original_allocation_offset;
  CHECK_LT(unaligned, ptr);
  CHECK_LE(
      reinterpret_cast<uintptr_t>(ptr) - reinterpret_cast<uintptr_t>(unaligned),
      kMaxWindowsAllocation);
  return unaligned;
}

}  // namespace

void* WinHeapAlignedMalloc(size_t size, size_t alignment) {
  CHECK(base::bits::IsPowerOfTwo(alignment));

  size_t adjusted = AdjustedSize(size, alignment);
  if (adjusted >= kMaxWindowsAllocation)
    return nullptr;

  void* ptr = WinHeapMalloc(adjusted);
  if (!ptr)
    return nullptr;

  return AlignAllocation(ptr, alignment);
}

void* WinHeapAlignedRealloc(void* ptr, size_t size, size_t alignment) {
  CHECK(base::bits::IsPowerOfTwo(alignment));

  if (!ptr)
    return WinHeapAlignedMalloc(size, alignment);
  if (!size) {
    WinHeapAlignedFree(ptr);
    return nullptr;
  }

  size_t adjusted = AdjustedSize(size, alignment);
  if (adjusted >= kMaxWindowsAllocation)
    return nullptr;

  // Try to resize the allocation in place first.
  void* unaligned = UnalignAllocation(ptr);
  if (HeapReAlloc(get_heap_handle(), HEAP_REALLOC_IN_PLACE_ONLY, unaligned,
                  adjusted)) {
    return ptr;
  }

  // Otherwise manually perform an _aligned_malloc() and copy since an
  // unaligned allocation from HeapReAlloc() would force us to copy the
  // allocation twice.
  void* new_ptr = WinHeapAlignedMalloc(size, alignment);
  if (!new_ptr)
    return nullptr;

  size_t gap =
      reinterpret_cast<uintptr_t>(ptr) - reinterpret_cast<uintptr_t>(unaligned);
  size_t old_size = WinHeapGetSizeEstimate(unaligned) - gap;
  memcpy(new_ptr, ptr, std::min(size, old_size));
  WinHeapAlignedFree(ptr);
  return new_ptr;
}

void WinHeapAlignedFree(void* ptr) {
  if (!ptr)
    return;

  void* original_allocation = UnalignAllocation(ptr);
  WinHeapFree(original_allocation);
}

}  // namespace allocator
}  // namespace base
