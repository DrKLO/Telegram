// Copyright (c) 2013 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

#include "base/process/process_metrics.h"

#include <limits.h>
#include <mach/task.h>
#include <stddef.h>

#include "base/logging.h"
#include "base/mac/scoped_mach_port.h"
#include "base/memory/ptr_util.h"
#include "base/numerics/safe_conversions.h"

namespace base {

ProcessMetrics::ProcessMetrics(ProcessHandle process) {}

ProcessMetrics::~ProcessMetrics() {}

// static
std::unique_ptr<ProcessMetrics> ProcessMetrics::CreateProcessMetrics(
    ProcessHandle process) {
  return WrapUnique(new ProcessMetrics(process));
}

TimeDelta ProcessMetrics::GetCumulativeCPUUsage() {
  NOTIMPLEMENTED();
  return TimeDelta();
}

size_t GetMaxFds() {
  static const rlim_t kSystemDefaultMaxFds = 256;
  rlim_t max_fds;
  struct rlimit nofile;
  if (getrlimit(RLIMIT_NOFILE, &nofile)) {
    // Error case: Take a best guess.
    max_fds = kSystemDefaultMaxFds;
  } else {
    max_fds = nofile.rlim_cur;
  }

  if (max_fds > INT_MAX)
    max_fds = INT_MAX;

  return static_cast<size_t>(max_fds);
}

void IncreaseFdLimitTo(unsigned int max_descriptors) {
  // Unimplemented.
}

size_t GetPageSize() {
  return getpagesize();
}

// Bytes committed by the system.
size_t GetSystemCommitCharge() {
  NOTIMPLEMENTED();
  return 0;
}

bool GetSystemMemoryInfo(SystemMemoryInfoKB* meminfo) {
  struct host_basic_info hostinfo;
  mach_msg_type_number_t count = HOST_BASIC_INFO_COUNT;
  base::mac::ScopedMachSendRight host(mach_host_self());
  int result = host_info(host.get(), HOST_BASIC_INFO,
                         reinterpret_cast<host_info_t>(&hostinfo), &count);
  if (result != KERN_SUCCESS)
    return false;

  DCHECK_EQ(HOST_BASIC_INFO_COUNT, count);
  meminfo->total = static_cast<int>(hostinfo.max_mem / 1024);

  vm_statistics64_data_t vm_info;
  count = HOST_VM_INFO64_COUNT;

  if (host_statistics64(host.get(), HOST_VM_INFO64,
                        reinterpret_cast<host_info64_t>(&vm_info),
                        &count) != KERN_SUCCESS) {
    return false;
  }
  DCHECK_EQ(HOST_VM_INFO64_COUNT, count);

  // Check that PAGE_SIZE is divisible by 1024 (2^10).
  CHECK_EQ(PAGE_SIZE, (PAGE_SIZE >> 10) << 10);
  meminfo->free = saturated_cast<int>(
      PAGE_SIZE / 1024 * (vm_info.free_count - vm_info.speculative_count));
  meminfo->speculative =
      saturated_cast<int>(PAGE_SIZE / 1024 * vm_info.speculative_count);
  meminfo->file_backed =
      saturated_cast<int>(PAGE_SIZE / 1024 * vm_info.external_page_count);
  meminfo->purgeable =
      saturated_cast<int>(PAGE_SIZE / 1024 * vm_info.purgeable_count);

  return true;
}

}  // namespace base
