// Copyright (c) 2018 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

#include "base/allocator/partition_allocator/partition_root_base.h"

#include "base/allocator/partition_allocator/oom.h"
#include "base/allocator/partition_allocator/partition_oom.h"
#include "base/allocator/partition_allocator/partition_page.h"
#include "build/build_config.h"

namespace base {
namespace internal {

NOINLINE void PartitionRootBase::OutOfMemory(size_t size) {
#if !defined(ARCH_CPU_64_BITS)
  // Check whether this OOM is due to a lot of super pages that are allocated
  // but not committed, probably due to http://crbug.com/421387.
  if (total_size_of_super_pages + total_size_of_direct_mapped_pages -
          total_size_of_committed_pages >
      kReasonableSizeOfUnusedPages) {
    PartitionOutOfMemoryWithLotsOfUncommitedPages(size);
  }
#endif
  if (PartitionRootBase::g_oom_handling_function)
    (*PartitionRootBase::g_oom_handling_function)(size);
  OOM_CRASH(size);
}

void PartitionRootBase::DecommitEmptyPages() {
  for (size_t i = 0; i < kMaxFreeableSpans; ++i) {
    internal::PartitionPage* page = global_empty_page_ring[i];
    if (page)
      page->DecommitIfPossible(this);
    global_empty_page_ring[i] = nullptr;
  }
}

}  // namespace internal
}  // namespace base
