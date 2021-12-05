// Copyright (c) 2018 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

#include "base/allocator/partition_allocator/partition_oom.h"

#include "base/allocator/partition_allocator/oom.h"
#include "build/build_config.h"

namespace base {
namespace internal {

void NOINLINE PartitionExcessiveAllocationSize(size_t size) {
  OOM_CRASH(size);
}

#if !defined(ARCH_CPU_64_BITS)
NOINLINE void PartitionOutOfMemoryWithLotsOfUncommitedPages(size_t size) {
  OOM_CRASH(size);
}
#endif

}  // namespace internal
}  // namespace base
