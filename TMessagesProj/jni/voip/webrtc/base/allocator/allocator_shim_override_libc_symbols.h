// Copyright 2016 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

// Its purpose is to preempt the Libc symbols for malloc/new so they call the
// shim layer entry points.

#ifdef BASE_ALLOCATOR_ALLOCATOR_SHIM_OVERRIDE_LIBC_SYMBOLS_H_
#error This header is meant to be included only once by allocator_shim.cc
#endif
#define BASE_ALLOCATOR_ALLOCATOR_SHIM_OVERRIDE_LIBC_SYMBOLS_H_

#include <malloc.h>

#include "base/allocator/allocator_shim_internals.h"

extern "C" {

SHIM_ALWAYS_EXPORT void* malloc(size_t size) __THROW {
  return ShimMalloc(size, nullptr);
}

SHIM_ALWAYS_EXPORT void free(void* ptr) __THROW {
  ShimFree(ptr, nullptr);
}

SHIM_ALWAYS_EXPORT void* realloc(void* ptr, size_t size) __THROW {
  return ShimRealloc(ptr, size, nullptr);
}

SHIM_ALWAYS_EXPORT void* calloc(size_t n, size_t size) __THROW {
  return ShimCalloc(n, size, nullptr);
}

SHIM_ALWAYS_EXPORT void cfree(void* ptr) __THROW {
  ShimFree(ptr, nullptr);
}

SHIM_ALWAYS_EXPORT void* memalign(size_t align, size_t s) __THROW {
  return ShimMemalign(align, s, nullptr);
}

SHIM_ALWAYS_EXPORT void* valloc(size_t size) __THROW {
  return ShimValloc(size, nullptr);
}

SHIM_ALWAYS_EXPORT void* pvalloc(size_t size) __THROW {
  return ShimPvalloc(size);
}

SHIM_ALWAYS_EXPORT int posix_memalign(void** r, size_t a, size_t s) __THROW {
  return ShimPosixMemalign(r, a, s);
}

SHIM_ALWAYS_EXPORT size_t malloc_size(void* address) __THROW {
  return ShimGetSizeEstimate(address, nullptr);
}

SHIM_ALWAYS_EXPORT size_t malloc_usable_size(void* address) __THROW {
  return ShimGetSizeEstimate(address, nullptr);
}

// The default dispatch translation unit has to define also the following
// symbols (unless they are ultimately routed to the system symbols):
//   void malloc_stats(void);
//   int mallopt(int, int);
//   struct mallinfo mallinfo(void);

}  // extern "C"
