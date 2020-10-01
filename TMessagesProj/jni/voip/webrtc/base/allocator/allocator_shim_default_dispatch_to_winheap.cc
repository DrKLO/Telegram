// Copyright 2016 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

#include "base/allocator/allocator_shim.h"

#include "base/allocator/winheap_stubs_win.h"
#include "base/logging.h"

namespace {

using base::allocator::AllocatorDispatch;

void* DefaultWinHeapMallocImpl(const AllocatorDispatch*,
                               size_t size,
                               void* context) {
  return base::allocator::WinHeapMalloc(size);
}

void* DefaultWinHeapCallocImpl(const AllocatorDispatch* self,
                               size_t n,
                               size_t elem_size,
                               void* context) {
  // Overflow check.
  const size_t size = n * elem_size;
  if (elem_size != 0 && size / elem_size != n)
    return nullptr;

  void* result = DefaultWinHeapMallocImpl(self, size, context);
  if (result) {
    memset(result, 0, size);
  }
  return result;
}

void* DefaultWinHeapMemalignImpl(const AllocatorDispatch* self,
                                 size_t alignment,
                                 size_t size,
                                 void* context) {
  CHECK(false) << "The windows heap does not support memalign.";
  return nullptr;
}

void* DefaultWinHeapReallocImpl(const AllocatorDispatch* self,
                                void* address,
                                size_t size,
                                void* context) {
  return base::allocator::WinHeapRealloc(address, size);
}

void DefaultWinHeapFreeImpl(const AllocatorDispatch*,
                            void* address,
                            void* context) {
  base::allocator::WinHeapFree(address);
}

size_t DefaultWinHeapGetSizeEstimateImpl(const AllocatorDispatch*,
                                         void* address,
                                         void* context) {
  return base::allocator::WinHeapGetSizeEstimate(address);
}

void* DefaultWinHeapAlignedMallocImpl(const AllocatorDispatch*,
                                      size_t size,
                                      size_t alignment,
                                      void* context) {
  return base::allocator::WinHeapAlignedMalloc(size, alignment);
}

void* DefaultWinHeapAlignedReallocImpl(const AllocatorDispatch*,
                                       void* ptr,
                                       size_t size,
                                       size_t alignment,
                                       void* context) {
  return base::allocator::WinHeapAlignedRealloc(ptr, size, alignment);
}

void DefaultWinHeapAlignedFreeImpl(const AllocatorDispatch*,
                                   void* ptr,
                                   void* context) {
  base::allocator::WinHeapAlignedFree(ptr);
}

}  // namespace

// Guarantee that default_dispatch is compile-time initialized to avoid using
// it before initialization (allocations before main in release builds with
// optimizations disabled).
constexpr AllocatorDispatch AllocatorDispatch::default_dispatch = {
    &DefaultWinHeapMallocImpl,
    &DefaultWinHeapCallocImpl,
    &DefaultWinHeapMemalignImpl,
    &DefaultWinHeapReallocImpl,
    &DefaultWinHeapFreeImpl,
    &DefaultWinHeapGetSizeEstimateImpl,
    nullptr, /* batch_malloc_function */
    nullptr, /* batch_free_function */
    nullptr, /* free_definite_size_function */
    &DefaultWinHeapAlignedMallocImpl,
    &DefaultWinHeapAlignedReallocImpl,
    &DefaultWinHeapAlignedFreeImpl,
    nullptr, /* next */
};
