// Copyright 2017 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

#ifndef BASE_ALLOCATOR_ALLOCATOR_SHIM_DEFAULT_DISPATCH_TO_ZONED_MALLOC_H_
#define BASE_ALLOCATOR_ALLOCATOR_SHIM_DEFAULT_DISPATCH_TO_ZONED_MALLOC_H_

namespace base {
namespace allocator {

// This initializes AllocatorDispatch::default_dispatch by saving pointers to
// the functions in the current default malloc zone. This must be called before
// the default malloc zone is changed to have its intended effect.
void InitializeDefaultDispatchToMacAllocator();

}  // namespace allocator
}  // namespace base

#endif  // BASE_ALLOCATOR_ALLOCATOR_SHIM_DEFAULT_DISPATCH_TO_ZONED_MALLOC_H_
