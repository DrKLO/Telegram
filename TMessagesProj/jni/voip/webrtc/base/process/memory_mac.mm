// Copyright (c) 2013 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

#include "base/process/memory.h"

#include "base/allocator/allocator_interception_mac.h"
#include "base/allocator/allocator_shim.h"
#include "base/allocator/buildflags.h"
#include "build/build_config.h"

namespace base {

namespace {
void oom_killer_new() {
  TerminateBecauseOutOfMemory(0);
}
}  // namespace

void EnableTerminationOnHeapCorruption() {
#if !ARCH_CPU_64_BITS
  DLOG(WARNING) << "EnableTerminationOnHeapCorruption only works on 64-bit";
#endif
}

bool UncheckedMalloc(size_t size, void** result) {
  return allocator::UncheckedMallocMac(size, result);
}

bool UncheckedCalloc(size_t num_items, size_t size, void** result) {
  return allocator::UncheckedCallocMac(num_items, size, result);
}

void EnableTerminationOnOutOfMemory() {
  // Step 1: Enable OOM killer on C++ failures.
  std::set_new_handler(oom_killer_new);

// Step 2: Enable OOM killer on C-malloc failures for the default zone (if we
// have a shim).
#if BUILDFLAG(USE_ALLOCATOR_SHIM)
  allocator::SetCallNewHandlerOnMallocFailure(true);
#endif

  // Step 3: Enable OOM killer on all other malloc zones (or just "all" without
  // "other" if shim is disabled).
  allocator::InterceptAllocationsMac();
}

}  // namespace base
