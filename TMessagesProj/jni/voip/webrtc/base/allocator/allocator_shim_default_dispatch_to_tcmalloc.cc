// Copyright 2016 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

#include "base/allocator/allocator_shim.h"
#include "base/allocator/allocator_shim_internals.h"

#include "third_party/tcmalloc/chromium/src/config.h"
#include "third_party/tcmalloc/chromium/src/gperftools/tcmalloc.h"

namespace {

using base::allocator::AllocatorDispatch;

void* TCMalloc(const AllocatorDispatch*, size_t size, void* context) {
  return tc_malloc(size);
}

void* TCCalloc(const AllocatorDispatch*, size_t n, size_t size, void* context) {
  return tc_calloc(n, size);
}

void* TCMemalign(const AllocatorDispatch*,
                 size_t alignment,
                 size_t size,
                 void* context) {
  return tc_memalign(alignment, size);
}

void* TCRealloc(const AllocatorDispatch*,
                void* address,
                size_t size,
                void* context) {
  return tc_realloc(address, size);
}

void TCFree(const AllocatorDispatch*, void* address, void* context) {
  tc_free(address);
}

size_t TCGetSizeEstimate(const AllocatorDispatch*,
                         void* address,
                         void* context) {
  return tc_malloc_size(address);
}

}  // namespace

const AllocatorDispatch AllocatorDispatch::default_dispatch = {
    &TCMalloc,          /* alloc_function */
    &TCCalloc,          /* alloc_zero_initialized_function */
    &TCMemalign,        /* alloc_aligned_function */
    &TCRealloc,         /* realloc_function */
    &TCFree,            /* free_function */
    &TCGetSizeEstimate, /* get_size_estimate_function */
    nullptr,            /* batch_malloc_function */
    nullptr,            /* batch_free_function */
    nullptr,            /* free_definite_size_function */
    nullptr,            /* aligned_malloc_function */
    nullptr,            /* aligned_realloc_function */
    nullptr,            /* aligned_free_function */
    nullptr,            /* next */
};

// In the case of tcmalloc we have also to route the diagnostic symbols,
// which are not part of the unified shim layer, to tcmalloc for consistency.

extern "C" {

SHIM_ALWAYS_EXPORT void malloc_stats(void) __THROW {
  return tc_malloc_stats();
}

SHIM_ALWAYS_EXPORT int mallopt(int cmd, int value) __THROW {
  return tc_mallopt(cmd, value);
}

#ifdef HAVE_STRUCT_MALLINFO
SHIM_ALWAYS_EXPORT struct mallinfo mallinfo(void) __THROW {
  return tc_mallinfo();
}
#endif

}  // extern "C"
