// Copyright (c) 2013 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

#include "base/process/process_metrics.h"

#include <stddef.h>
#include <sys/sysctl.h>
#include <sys/user.h>
#include <unistd.h>

#include "base/macros.h"
#include "base/memory/ptr_util.h"
#include "base/process/process_metrics_iocounters.h"
#include "base/stl_util.h"

namespace base {

ProcessMetrics::ProcessMetrics(ProcessHandle process)
    : process_(process),
      last_cpu_(0) {}

// static
std::unique_ptr<ProcessMetrics> ProcessMetrics::CreateProcessMetrics(
    ProcessHandle process) {
  return WrapUnique(new ProcessMetrics(process));
}

double ProcessMetrics::GetPlatformIndependentCPUUsage() {
  struct kinfo_proc info;
  int mib[] = {CTL_KERN, KERN_PROC, KERN_PROC_PID, process_};
  size_t length = sizeof(info);

  if (sysctl(mib, base::size(mib), &info, &length, NULL, 0) < 0)
    return 0;

  return (info.ki_pctcpu / FSCALE) * 100.0;
}

TimeDelta ProcessMetrics::GetCumulativeCPUUsage() {
  NOTREACHED();
  return TimeDelta();
}

bool ProcessMetrics::GetIOCounters(IoCounters* io_counters) const {
  return false;
}

size_t GetSystemCommitCharge() {
  int mib[2], pagesize;
  unsigned long mem_total, mem_free, mem_inactive;
  size_t length = sizeof(mem_total);

  if (sysctl(mib, base::size(mib), &mem_total, &length, NULL, 0) < 0)
    return 0;

  length = sizeof(mem_free);
  if (sysctlbyname("vm.stats.vm.v_free_count", &mem_free, &length, NULL, 0) < 0)
    return 0;

  length = sizeof(mem_inactive);
  if (sysctlbyname("vm.stats.vm.v_inactive_count", &mem_inactive, &length,
      NULL, 0) < 0) {
    return 0;
  }

  pagesize = getpagesize();

  return mem_total - (mem_free*pagesize) - (mem_inactive*pagesize);
}

}  // namespace base
