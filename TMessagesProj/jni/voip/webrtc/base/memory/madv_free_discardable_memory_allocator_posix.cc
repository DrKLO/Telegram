// Copyright 2019 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

#include <inttypes.h>
#include <sys/mman.h>

#include "base/memory/madv_free_discardable_memory_allocator_posix.h"
#include "base/process/process_metrics.h"
#include "base/strings/string_number_conversions.h"
#include "base/strings/stringprintf.h"
#include "base/threading/thread_task_runner_handle.h"
#include "base/trace_event/memory_dump_manager.h"

namespace base {

MadvFreeDiscardableMemoryAllocatorPosix::
    MadvFreeDiscardableMemoryAllocatorPosix() {
  // Don't register dump provider if ThreadTaskRunnerHandle is not set, such as
  // in tests and Android Webview.
  if (base::ThreadTaskRunnerHandle::IsSet()) {
    trace_event::MemoryDumpManager::GetInstance()->RegisterDumpProvider(
        this, "MadvFreeDiscardableMemoryAllocator",
        ThreadTaskRunnerHandle::Get());
  }
}

MadvFreeDiscardableMemoryAllocatorPosix::
    ~MadvFreeDiscardableMemoryAllocatorPosix() {
  trace_event::MemoryDumpManager::GetInstance()->UnregisterDumpProvider(this);
}

std::unique_ptr<DiscardableMemory>
MadvFreeDiscardableMemoryAllocatorPosix::AllocateLockedDiscardableMemory(
    size_t size) {
  return std::make_unique<MadvFreeDiscardableMemoryPosix>(size,
                                                          &bytes_allocated_);
}

size_t MadvFreeDiscardableMemoryAllocatorPosix::GetBytesAllocated() const {
  return bytes_allocated_;
}

bool MadvFreeDiscardableMemoryAllocatorPosix::OnMemoryDump(
    const trace_event::MemoryDumpArgs& args,
    trace_event::ProcessMemoryDump* pmd) {
  if (args.level_of_detail !=
      base::trace_event::MemoryDumpLevelOfDetail::BACKGROUND) {
    return true;
  }

  base::trace_event::MemoryAllocatorDump* total_dump =
      pmd->CreateAllocatorDump("discardable/madv_free_allocated");
  total_dump->AddScalar(base::trace_event::MemoryAllocatorDump::kNameSize,
                        base::trace_event::MemoryAllocatorDump::kUnitsBytes,
                        GetBytesAllocated());
  return true;
}

}  // namespace base
