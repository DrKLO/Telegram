// Copyright 2019 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

#ifndef BASE_MEMORY_SHARED_MEMORY_HOOKS_H_
#define BASE_MEMORY_SHARED_MEMORY_HOOKS_H_

#include "base/memory/read_only_shared_memory_region.h"
#include "base/memory/unsafe_shared_memory_region.h"
#include "base/memory/writable_shared_memory_region.h"

// TODO(https://crbug.com/1062136): This can be removed when Cloud Print support
// is dropped.
namespace content {
struct MainFunctionParams;
}  // namespace content
int CloudPrintServiceProcessMain(const content::MainFunctionParams& parameters);

namespace mojo {

class SharedMemoryUtils;

}  // namespace mojo

namespace base {

class SharedMemoryHooks {
 public:
  SharedMemoryHooks() = delete;

 private:
  friend class SharedMemoryHooksTest;
  friend int ::CloudPrintServiceProcessMain(
      const content::MainFunctionParams& parameters);
  friend mojo::SharedMemoryUtils;

  // Allows shared memory region creation to be hooked. Useful for sandboxed
  // processes that are restricted from invoking the platform APIs directly.
  // Intentionally private so callers need to be explicitly friended.
  static void SetCreateHooks(
      ReadOnlySharedMemoryRegion::CreateFunction* read_only_hook,
      UnsafeSharedMemoryRegion::CreateFunction* unsafe_hook,
      WritableSharedMemoryRegion::CreateFunction* writable_hook) {
    ReadOnlySharedMemoryRegion::set_create_hook(read_only_hook);
    UnsafeSharedMemoryRegion::set_create_hook(unsafe_hook);
    WritableSharedMemoryRegion::set_create_hook(writable_hook);
  }
};

}  // namespace base

#endif  // BASE_MEMORY_SHARED_MEMORY_HOOKS_H_
