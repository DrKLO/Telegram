// Copyright (c) 2013 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

#include "base/process/process_metrics.h"

#include <libproc.h>
#include <mach/mach.h>
#include <mach/mach_time.h>
#include <mach/mach_vm.h>
#include <mach/shared_region.h>
#include <stddef.h>
#include <stdint.h>
#include <sys/sysctl.h>

#include "base/logging.h"
#include "base/mac/mac_util.h"
#include "base/mac/mach_logging.h"
#include "base/mac/scoped_mach_port.h"
#include "base/memory/ptr_util.h"
#include "base/numerics/safe_conversions.h"
#include "base/numerics/safe_math.h"
#include "base/process/process_metrics_iocounters.h"
#include "base/time/time.h"

namespace {

// This is a standin for the private pm_task_energy_data_t struct.
struct OpaquePMTaskEnergyData {
  // Empirical size of the private struct.
  uint8_t data[384];
};

// Sample everything but network usage, since fetching network
// usage can hang.
static constexpr uint8_t kPMSampleFlags = 0xff & ~0x8;

}  // namespace

extern "C" {

// From libpmsample.dylib
int pm_sample_task(mach_port_t task,
                   OpaquePMTaskEnergyData* pm_energy,
                   uint64_t mach_time,
                   uint8_t flags);

// From libpmenergy.dylib
double pm_energy_impact(OpaquePMTaskEnergyData* pm_energy);

}  // extern "C"

namespace base {

namespace {

bool GetTaskInfo(mach_port_t task, task_basic_info_64* task_info_data) {
  if (task == MACH_PORT_NULL)
    return false;
  mach_msg_type_number_t count = TASK_BASIC_INFO_64_COUNT;
  kern_return_t kr = task_info(task,
                               TASK_BASIC_INFO_64,
                               reinterpret_cast<task_info_t>(task_info_data),
                               &count);
  // Most likely cause for failure: |task| is a zombie.
  return kr == KERN_SUCCESS;
}

MachVMRegionResult ParseOutputFromMachVMRegion(kern_return_t kr) {
  if (kr == KERN_INVALID_ADDRESS) {
    // We're at the end of the address space.
    return MachVMRegionResult::Finished;
  } else if (kr != KERN_SUCCESS) {
    return MachVMRegionResult::Error;
  }
  return MachVMRegionResult::Success;
}

bool GetPowerInfo(mach_port_t task, task_power_info* power_info_data) {
  if (task == MACH_PORT_NULL)
    return false;

  mach_msg_type_number_t power_info_count = TASK_POWER_INFO_COUNT;
  kern_return_t kr = task_info(task, TASK_POWER_INFO,
                               reinterpret_cast<task_info_t>(power_info_data),
                               &power_info_count);
  // Most likely cause for failure: |task| is a zombie.
  return kr == KERN_SUCCESS;
}

double GetEnergyImpactInternal(mach_port_t task, uint64_t mach_time) {
  OpaquePMTaskEnergyData energy_info{};

  if (pm_sample_task(task, &energy_info, mach_time, kPMSampleFlags) != 0)
    return 0.0;
  return pm_energy_impact(&energy_info);
}

}  // namespace

// Getting a mach task from a pid for another process requires permissions in
// general, so there doesn't really seem to be a way to do these (and spinning
// up ps to fetch each stats seems dangerous to put in a base api for anyone to
// call). Child processes ipc their port, so return something if available,
// otherwise return 0.

// static
std::unique_ptr<ProcessMetrics> ProcessMetrics::CreateProcessMetrics(
    ProcessHandle process,
    PortProvider* port_provider) {
  return WrapUnique(new ProcessMetrics(process, port_provider));
}

#define TIME_VALUE_TO_TIMEVAL(a, r) do {  \
  (r)->tv_sec = (a)->seconds;             \
  (r)->tv_usec = (a)->microseconds;       \
} while (0)

TimeDelta ProcessMetrics::GetCumulativeCPUUsage() {
  mach_port_t task = TaskForPid(process_);
  if (task == MACH_PORT_NULL)
    return TimeDelta();

  // Libtop explicitly loops over the threads (libtop_pinfo_update_cpu_usage()
  // in libtop.c), but this is more concise and gives the same results:
  task_thread_times_info thread_info_data;
  mach_msg_type_number_t thread_info_count = TASK_THREAD_TIMES_INFO_COUNT;
  kern_return_t kr = task_info(task,
                               TASK_THREAD_TIMES_INFO,
                               reinterpret_cast<task_info_t>(&thread_info_data),
                               &thread_info_count);
  if (kr != KERN_SUCCESS) {
    // Most likely cause: |task| is a zombie.
    return TimeDelta();
  }

  task_basic_info_64 task_info_data;
  if (!GetTaskInfo(task, &task_info_data))
    return TimeDelta();

  /* Set total_time. */
  // thread info contains live time...
  struct timeval user_timeval, system_timeval, task_timeval;
  TIME_VALUE_TO_TIMEVAL(&thread_info_data.user_time, &user_timeval);
  TIME_VALUE_TO_TIMEVAL(&thread_info_data.system_time, &system_timeval);
  timeradd(&user_timeval, &system_timeval, &task_timeval);

  // ... task info contains terminated time.
  TIME_VALUE_TO_TIMEVAL(&task_info_data.user_time, &user_timeval);
  TIME_VALUE_TO_TIMEVAL(&task_info_data.system_time, &system_timeval);
  timeradd(&user_timeval, &task_timeval, &task_timeval);
  timeradd(&system_timeval, &task_timeval, &task_timeval);

  return TimeDelta::FromMicroseconds(TimeValToMicroseconds(task_timeval));
}

int ProcessMetrics::GetPackageIdleWakeupsPerSecond() {
  mach_port_t task = TaskForPid(process_);
  task_power_info power_info_data;

  GetPowerInfo(task, &power_info_data);

  // The task_power_info struct contains two wakeup counters:
  // task_interrupt_wakeups and task_platform_idle_wakeups.
  // task_interrupt_wakeups is the total number of wakeups generated by the
  // process, and is the number that Activity Monitor reports.
  // task_platform_idle_wakeups is a subset of task_interrupt_wakeups that
  // tallies the number of times the processor was taken out of its low-power
  // idle state to handle a wakeup. task_platform_idle_wakeups therefore result
  // in a greater power increase than the other interrupts which occur while the
  // CPU is already working, and reducing them has a greater overall impact on
  // power usage. See the powermetrics man page for more info.
  return CalculatePackageIdleWakeupsPerSecond(
      power_info_data.task_platform_idle_wakeups);
}

int ProcessMetrics::GetIdleWakeupsPerSecond() {
  mach_port_t task = TaskForPid(process_);
  task_power_info power_info_data;

  GetPowerInfo(task, &power_info_data);

  return CalculateIdleWakeupsPerSecond(power_info_data.task_interrupt_wakeups);
}

int ProcessMetrics::GetEnergyImpact() {
  uint64_t now = mach_absolute_time();
  if (last_energy_impact_ == 0) {
    last_energy_impact_ = GetEnergyImpactInternal(TaskForPid(process_), now);
    last_energy_impact_time_ = now;
    return 0;
  }

  double total_energy_impact =
      GetEnergyImpactInternal(TaskForPid(process_), now);
  uint64_t delta = now - last_energy_impact_time_;
  if (delta == 0)
    return 0;

  // Scale by 100 since the histogram is integral.
  double seconds_since_last_measurement =
      base::TimeTicks::FromMachAbsoluteTime(delta).since_origin().InSecondsF();
  int energy_impact = 100 * (total_energy_impact - last_energy_impact_) /
                      seconds_since_last_measurement;
  last_energy_impact_ = total_energy_impact;
  last_energy_impact_time_ = now;

  return energy_impact;
}

int ProcessMetrics::GetOpenFdCount() const {
  // In order to get a true count of the open number of FDs, PROC_PIDLISTFDS
  // is used. This is done twice: first to get the appropriate size of a
  // buffer, and then secondly to fill the buffer with the actual FD info.
  //
  // The buffer size returned in the first call is an estimate, based on the
  // number of allocated fileproc structures in the kernel. This number can be
  // greater than the actual number of open files, since the structures are
  // allocated in slabs. The value returned in proc_bsdinfo::pbi_nfiles is
  // also the number of allocated fileprocs, not the number in use.
  //
  // However, the buffer size returned in the second call is an accurate count
  // of the open number of descriptors. The contents of the buffer are unused.
  int rv = proc_pidinfo(process_, PROC_PIDLISTFDS, 0, nullptr, 0);
  if (rv < 0)
    return -1;

  std::unique_ptr<char[]> buffer(new char[rv]);
  rv = proc_pidinfo(process_, PROC_PIDLISTFDS, 0, buffer.get(), rv);
  if (rv < 0)
    return -1;
  return rv / PROC_PIDLISTFD_SIZE;
}

int ProcessMetrics::GetOpenFdSoftLimit() const {
  return GetMaxFds();
}

bool ProcessMetrics::GetIOCounters(IoCounters* io_counters) const {
  return false;
}

ProcessMetrics::ProcessMetrics(ProcessHandle process,
                               PortProvider* port_provider)
    : process_(process),
      last_absolute_idle_wakeups_(0),
      last_absolute_package_idle_wakeups_(0),
      last_energy_impact_(0),
      port_provider_(port_provider) {}

mach_port_t ProcessMetrics::TaskForPid(ProcessHandle process) const {
  mach_port_t task = MACH_PORT_NULL;
  if (port_provider_)
    task = port_provider_->TaskForPid(process_);
  if (task == MACH_PORT_NULL && process_ == getpid())
    task = mach_task_self();
  return task;
}

// Bytes committed by the system.
size_t GetSystemCommitCharge() {
  base::mac::ScopedMachSendRight host(mach_host_self());
  mach_msg_type_number_t count = HOST_VM_INFO_COUNT;
  vm_statistics_data_t data;
  kern_return_t kr = host_statistics(host.get(), HOST_VM_INFO,
                                     reinterpret_cast<host_info_t>(&data),
                                     &count);
  if (kr != KERN_SUCCESS) {
    MACH_DLOG(WARNING, kr) << "host_statistics";
    return 0;
  }

  return (data.active_count * PAGE_SIZE) / 1024;
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

  static_assert(PAGE_SIZE % 1024 == 0, "Invalid page size");
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

// Both |size| and |address| are in-out parameters.
// |info| is an output parameter, only valid on Success.
MachVMRegionResult GetTopInfo(mach_port_t task,
                              mach_vm_size_t* size,
                              mach_vm_address_t* address,
                              vm_region_top_info_data_t* info) {
  mach_msg_type_number_t info_count = VM_REGION_TOP_INFO_COUNT;
  mach_port_t object_name;
  kern_return_t kr = mach_vm_region(task, address, size, VM_REGION_TOP_INFO,
                                    reinterpret_cast<vm_region_info_t>(info),
                                    &info_count, &object_name);
  // The kernel always returns a null object for VM_REGION_TOP_INFO, but
  // balance it with a deallocate in case this ever changes. See 10.9.2
  // xnu-2422.90.20/osfmk/vm/vm_map.c vm_map_region.
  mach_port_deallocate(task, object_name);
  return ParseOutputFromMachVMRegion(kr);
}

MachVMRegionResult GetBasicInfo(mach_port_t task,
                                mach_vm_size_t* size,
                                mach_vm_address_t* address,
                                vm_region_basic_info_64* info) {
  mach_msg_type_number_t info_count = VM_REGION_BASIC_INFO_COUNT_64;
  mach_port_t object_name;
  kern_return_t kr = mach_vm_region(
      task, address, size, VM_REGION_BASIC_INFO_64,
      reinterpret_cast<vm_region_info_t>(info), &info_count, &object_name);
  // The kernel always returns a null object for VM_REGION_BASIC_INFO_64, but
  // balance it with a deallocate in case this ever changes. See 10.9.2
  // xnu-2422.90.20/osfmk/vm/vm_map.c vm_map_region.
  mach_port_deallocate(task, object_name);
  return ParseOutputFromMachVMRegion(kr);
}

}  // namespace base
