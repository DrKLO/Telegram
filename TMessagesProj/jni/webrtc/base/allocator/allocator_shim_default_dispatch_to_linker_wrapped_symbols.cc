// Copyright 2016 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

#include <malloc.h>

#include "base/allocator/allocator_shim.h"
#include "build/build_config.h"

// This translation unit defines a default dispatch for the allocator shim which
// routes allocations to the original libc functions when using the link-time
// -Wl,-wrap,malloc approach (see README.md).
// The __real_X functions here are special symbols that the linker will relocate
// against the real "X" undefined symbol, so that __real_malloc becomes the
// equivalent of what an undefined malloc symbol reference would have been.
// This is the counterpart of allocator_shim_override_linker_wrapped_symbols.h,
// which routes the __wrap_X functions into the shim.

extern "C" {
void* __real_malloc(size_t);
void* __real_calloc(size_t, size_t);
void* __real_realloc(void*, size_t);
void* __real_memalign(size_t, size_t);
void* __real_free(void*);
}  // extern "C"

namespace {

using base::allocator::AllocatorDispatch;

void* RealMalloc(const AllocatorDispatch*, size_t size, void* context) {
  return __real_malloc(size);
}

void* RealCalloc(const AllocatorDispatch*,
                 size_t n,
                 size_t size,
                 void* context) {
  return __real_calloc(n, size);
}

void* RealRealloc(const AllocatorDispatch*,
                  void* address,
                  size_t size,
                  void* context) {
  return __real_realloc(address, size);
}

void* RealMemalign(const AllocatorDispatch*,
                   size_t alignment,
                   size_t size,
                   void* context) {
  return __real_memalign(alignment, size);
}

void RealFree(const AllocatorDispatch*, void* address, void* context) {
  __real_free(address);
}

}  // namespace

const AllocatorDispatch AllocatorDispatch::default_dispatch = {
    &RealMalloc,   /* alloc_function */
    &RealCalloc,   /* alloc_zero_initialized_function */
    &RealMemalign, /* alloc_aligned_function */
    &RealRealloc,  /* realloc_function */
    &RealFree,     /* free_function */
    nullptr,       /* get_size_estimate_function */
    nullptr,       /* batch_malloc_function */
    nullptr,       /* batch_free_function */
    nullptr,       /* free_definite_size_function */
    nullptr,       /* aligned_malloc_function */
    nullptr,       /* aligned_realloc_function */
    nullptr,       /* aligned_free_function */
    nullptr,       /* next */
};
