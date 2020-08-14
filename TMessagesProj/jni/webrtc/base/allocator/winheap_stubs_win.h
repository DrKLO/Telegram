// Copyright 2016 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

// Thin allocation wrappers for the windows heap. This file should be deleted
// once the win-specific allocation shim has been removed, and the generic shim
// has becaome the default.

#ifndef BASE_ALLOCATOR_WINHEAP_STUBS_H_
#define BASE_ALLOCATOR_WINHEAP_STUBS_H_

#include <stdint.h>

#include "base/base_export.h"

namespace base {
namespace allocator {

// Set to true if the link-time magic has successfully hooked into the CRT's
// heap initialization.
extern bool g_is_win_shim_layer_initialized;

// Thin wrappers to implement the standard C allocation semantics on the
// CRT's Windows heap.
void* WinHeapMalloc(size_t size);
void WinHeapFree(void* ptr);
void* WinHeapRealloc(void* ptr, size_t size);

// Returns a lower-bound estimate for the full amount of memory consumed by the
// the allocation |ptr|.
size_t WinHeapGetSizeEstimate(void* ptr);

// Call the new handler, if one has been set.
// Returns true on successfully calling the handler, false otherwise.
bool WinCallNewHandler(size_t size);

// Wrappers to implement the interface for the _aligned_* functions on top of
// the CRT's Windows heap. Exported for tests.
BASE_EXPORT void* WinHeapAlignedMalloc(size_t size, size_t alignment);
BASE_EXPORT void* WinHeapAlignedRealloc(void* ptr,
                                        size_t size,
                                        size_t alignment);
BASE_EXPORT void WinHeapAlignedFree(void* ptr);

}  // namespace allocator
}  // namespace base

#endif  // BASE_ALLOCATOR_WINHEAP_STUBS_H_
