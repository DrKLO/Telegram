// Copyright 2016 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

#ifndef BASE_ALLOCATOR_ALLOCATOR_SHIM_H_
#define BASE_ALLOCATOR_ALLOCATOR_SHIM_H_

#include <stddef.h>

#include "base/base_export.h"
#include "build/build_config.h"

namespace base {
namespace allocator {

// Allocator Shim API. Allows to:
//  - Configure the behavior of the allocator (what to do on OOM failures).
//  - Install new hooks (AllocatorDispatch) in the allocator chain.

// When this shim layer is enabled, the route of an allocation is as-follows:
//
// [allocator_shim_override_*.h] Intercept malloc() / operator new calls:
//   The override_* headers define the symbols required to intercept calls to
//   malloc() and operator new (if not overridden by specific C++ classes).
//
// [allocator_shim.cc] Routing allocation calls to the shim:
//   The headers above route the calls to the internal ShimMalloc(), ShimFree(),
//   ShimCppNew() etc. methods defined in allocator_shim.cc.
//   These methods will: (1) forward the allocation call to the front of the
//   AllocatorDispatch chain. (2) perform security hardenings (e.g., might
//   call std::new_handler on OOM failure).
//
// [allocator_shim_default_dispatch_to_*.cc] The AllocatorDispatch chain:
//   It is a singly linked list where each element is a struct with function
//   pointers (|malloc_function|, |free_function|, etc). Normally the chain
//   consists of a single AllocatorDispatch element, herein called
//   the "default dispatch", which is statically defined at build time and
//   ultimately routes the calls to the actual allocator defined by the build
//   config (tcmalloc, glibc, ...).
//
// It is possible to dynamically insert further AllocatorDispatch stages
// to the front of the chain, for debugging / profiling purposes.
//
// All the functions must be thread safe. The shim does not enforce any
// serialization. This is to route to thread-aware allocators (e.g, tcmalloc)
// wihout introducing unnecessary perf hits.

struct AllocatorDispatch {
  using AllocFn = void*(const AllocatorDispatch* self,
                        size_t size,
                        void* context);
  using AllocZeroInitializedFn = void*(const AllocatorDispatch* self,
                                       size_t n,
                                       size_t size,
                                       void* context);
  using AllocAlignedFn = void*(const AllocatorDispatch* self,
                               size_t alignment,
                               size_t size,
                               void* context);
  using ReallocFn = void*(const AllocatorDispatch* self,
                          void* address,
                          size_t size,
                          void* context);
  using FreeFn = void(const AllocatorDispatch* self,
                      void* address,
                      void* context);
  // Returns the best available estimate for the actual amount of memory
  // consumed by the allocation |address|. If possible, this should include
  // heap overhead or at least a decent estimate of the full cost of the
  // allocation. If no good estimate is possible, returns zero.
  using GetSizeEstimateFn = size_t(const AllocatorDispatch* self,
                                   void* address,
                                   void* context);
  using BatchMallocFn = unsigned(const AllocatorDispatch* self,
                                 size_t size,
                                 void** results,
                                 unsigned num_requested,
                                 void* context);
  using BatchFreeFn = void(const AllocatorDispatch* self,
                           void** to_be_freed,
                           unsigned num_to_be_freed,
                           void* context);
  using FreeDefiniteSizeFn = void(const AllocatorDispatch* self,
                                  void* ptr,
                                  size_t size,
                                  void* context);
  using AlignedMallocFn = void*(const AllocatorDispatch* self,
                                size_t size,
                                size_t alignment,
                                void* context);
  using AlignedReallocFn = void*(const AllocatorDispatch* self,
                                 void* address,
                                 size_t size,
                                 size_t alignment,
                                 void* context);
  using AlignedFreeFn = void(const AllocatorDispatch* self,
                             void* address,
                             void* context);

  AllocFn* const alloc_function;
  AllocZeroInitializedFn* const alloc_zero_initialized_function;
  AllocAlignedFn* const alloc_aligned_function;
  ReallocFn* const realloc_function;
  FreeFn* const free_function;
  GetSizeEstimateFn* const get_size_estimate_function;
  // batch_malloc, batch_free, and free_definite_size are specific to the OSX
  // and iOS allocators.
  BatchMallocFn* const batch_malloc_function;
  BatchFreeFn* const batch_free_function;
  FreeDefiniteSizeFn* const free_definite_size_function;
  // _aligned_malloc, _aligned_realloc, and _aligned_free are specific to the
  // Windows allocator.
  AlignedMallocFn* const aligned_malloc_function;
  AlignedReallocFn* const aligned_realloc_function;
  AlignedFreeFn* const aligned_free_function;

  const AllocatorDispatch* next;

  // |default_dispatch| is statically defined by one (and only one) of the
  // allocator_shim_default_dispatch_to_*.cc files, depending on the build
  // configuration.
  static const AllocatorDispatch default_dispatch;
};

// When true makes malloc behave like new, w.r.t calling the new_handler if
// the allocation fails (see set_new_mode() in Windows).
BASE_EXPORT void SetCallNewHandlerOnMallocFailure(bool value);

// Allocates |size| bytes or returns nullptr. It does NOT call the new_handler,
// regardless of SetCallNewHandlerOnMallocFailure().
BASE_EXPORT void* UncheckedAlloc(size_t size);

// Inserts |dispatch| in front of the allocator chain. This method is
// thread-safe w.r.t concurrent invocations of InsertAllocatorDispatch().
// The callers have responsibility for inserting a single dispatch no more
// than once.
BASE_EXPORT void InsertAllocatorDispatch(AllocatorDispatch* dispatch);

// Test-only. Rationale: (1) lack of use cases; (2) dealing safely with a
// removal of arbitrary elements from a singly linked list would require a lock
// in malloc(), which we really don't want.
BASE_EXPORT void RemoveAllocatorDispatchForTesting(AllocatorDispatch* dispatch);

#if defined(OS_MACOSX)
// On macOS, the allocator shim needs to be turned on during runtime.
BASE_EXPORT void InitializeAllocatorShim();
#endif  // defined(OS_MACOSX)

}  // namespace allocator
}  // namespace base

#endif  // BASE_ALLOCATOR_ALLOCATOR_SHIM_H_
