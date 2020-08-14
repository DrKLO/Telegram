// Copyright (c) 2013 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

#include "base/process/process_metrics.h"

#include <stddef.h>
#include <stdint.h>
#include <sys/param.h>
#include <sys/sysctl.h>

#include "base/memory/ptr_util.h"
#include "base/process/process_metrics_iocounters.h"
#include "base/stl_util.h"

namespace base {

// static
std::unique_ptr<ProcessMetrics> ProcessMetrics::CreateProcessMetrics(
    ProcessHandle process) {
  return WrapUnique(new ProcessMetrics(process));
}

bool ProcessMetrics::GetIOCounters(IoCounters* io_counters) const {
  return false;
}

static int GetProcessCPU(pid_t pid) {
  struct kinfo_proc info;
  size_t length;
  int mib[] = { CTL_KERN, KERN_PROC, KERN_PROC_PID, pid,
                sizeof(struct kinfo_proc), 0 };

  if (sysctl(mib, base::size(mib), NULL, &length, NULL, 0) < 0)
    return -1;

  mib[5] = (length / sizeof(struct kinfo_proc));

  if (sysctl(mib, base::size(mib), &info, &length, NULL, 0) < 0)
    return 0;

  return info.p_pctcpu;
}

double ProcessMetrics::GetPlatformIndependentCPUUsage() {
  TimeTicks time = TimeTicks::Now();

  if (last_cpu_time_.is_zero()) {
    // First call, just set the last values.
    last_cpu_time_ = time;
    return 0;
  }

  int cpu = GetProcessCPU(process_);

  last_cpu_time_ = time;
  double percentage = static_cast<double>((cpu * 100.0) / FSCALE);

  return percentage;
}

TimeDelta ProcessMetrics::GetCumulativeCPUUsage() {
  NOTREACHED();
  return TimeDelta();
}

ProcessMetrics::ProcessMetrics(ProcessHandle process)
    : process_(process),
      last_cpu_(0) {}

size_t GetSystemCommitCharge() {
  int mib[] = { CTL_VM, VM_METER };
  int pagesize;
  struct vmtotal vmtotal;
  unsigned long mem_total, mem_free, mem_inactive;
  size_t len = sizeof(vmtotal);

  if (sysctl(mib, base::size(mib), &vmtotal, &len, NULL, 0) < 0)
    return 0;

  mem_total = vmtotal.t_vm;
  mem_free = vmtotal.t_free;
  mem_inactive = vmtotal.t_vm - vmtotal.t_avm;

  pagesize = getpagesize();

  return mem_total - (mem_free*pagesize) - (mem_inactive*pagesize);
}

}  // namespace base
