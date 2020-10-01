// Copyright (c) 2012 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

#ifndef BASE_ALLOCATOR_ALLOCATOR_EXTENSION_H_
#define BASE_ALLOCATOR_ALLOCATOR_EXTENSION_H_

#include <stddef.h> // for size_t
#include <string>

#include "base/base_export.h"
#include "build/build_config.h"

namespace base {
namespace allocator {

// Callback types for alloc and free.
using AllocHookFunc = void (*)(const void*, size_t);
using FreeHookFunc = void (*)(const void*);

// Request that the allocator release any free memory it knows about to the
// system.
BASE_EXPORT void ReleaseFreeMemory();

// Get the named property's |value|. Returns true if the property is known.
// Returns false if the property is not a valid property name for the current
// allocator implementation.
// |name| or |value| cannot be NULL
BASE_EXPORT bool GetNumericProperty(const char* name, size_t* value);

// Set the named property's |value|. Returns true if the property is known and
// writable. Returns false if the property is not a valid property name for the
// current allocator implementation, or is not writable. |name| cannot be NULL.
BASE_EXPORT bool SetNumericProperty(const char* name, size_t value);

// Outputs to |writer| a sample of live objects and the stack traces
// that allocated these objects.  The format of the returned output
// is equivalent to the output of the heap profiler and can
// therefore be passed to "pprof".
// NOTE: by default, the allocator does not do any heap sampling, and this
//       function will always return an empty sample.  To get useful
//       data from GetHeapSample, you must also set the numeric property
//       "tcmalloc.sampling_period_bytes" to a value such as 524288.
BASE_EXPORT void GetHeapSample(std::string* writer);

BASE_EXPORT bool IsHeapProfilerRunning();

// Register callbacks for alloc and free. Can only store one callback at a time
// for each of alloc and free.
BASE_EXPORT void SetHooks(AllocHookFunc alloc_hook, FreeHookFunc free_hook);

// Attempts to unwind the call stack from the current location where this
// function is being called from. Must be called from a hook function registered
// by calling SetSingle{Alloc,Free}Hook, directly or indirectly.
//
// Arguments:
//   stack:          pointer to a pre-allocated array of void*'s.
//   max_stack_size: indicates the size of the array in |stack|.
//
// Returns the number of call stack frames stored in |stack|, or 0 if no call
// stack information is available.
BASE_EXPORT int GetCallStack(void** stack, int max_stack_size);

}  // namespace allocator
}  // namespace base

#endif  // BASE_ALLOCATOR_ALLOCATOR_EXTENSION_H_
