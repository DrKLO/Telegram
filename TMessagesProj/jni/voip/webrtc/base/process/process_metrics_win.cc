// Copyright (c) 2013 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

#include "base/process/process_metrics.h"

#include <windows.h>  // Must be in front of other Windows header files.

#include <psapi.h>
#include <stddef.h>
#include <stdint.h>
#include <winternl.h>

#include <algorithm>

#include "base/logging.h"
#include "base/memory/ptr_util.h"
#include "base/process/process_metrics_iocounters.h"
#include "base/system/sys_info.h"
#include "base/threading/scoped_blocking_call.h"

namespace base {
namespace {

// System pagesize. This value remains constant on x86/64 architectures.
const int PAGESIZE_KB = 4;

// ntstatus.h conflicts with windows.h so define this locally.
#define STATUS_SUCCESS ((NTSTATUS)0x00000000L)

// Definition of this struct is taken from the book:
// Windows NT/200, Native API reference, Gary Nebbett
struct SYSTEM_PERFORMANCE_INFORMATION {
  // Total idle time of all processes in the system (units of 100 ns).
  LARGE_INTEGER IdleTime;
  // Number of bytes read (by all call to ZwReadFile).
  LARGE_INTEGER ReadTransferCount;
  // Number of bytes written (by all call to ZwWriteFile).
  LARGE_INTEGER WriteTransferCount;
  // Number of bytes transferred (e.g. DeviceIoControlFile)
  LARGE_INTEGER OtherTransferCount;
  // The amount of read operations.
  ULONG ReadOperationCount;
  // The amount of write operations.
  ULONG WriteOperationCount;
  // The amount of other operations.
  ULONG OtherOperationCount;
  // The number of pages of physical memory available to processes running on
  // the system.
  ULONG AvailablePages;
  ULONG TotalCommittedPages;
  ULONG TotalCommitLimit;
  ULONG PeakCommitment;
  ULONG PageFaults;
  ULONG WriteCopyFaults;
  ULONG TransitionFaults;
  ULONG CacheTransitionFaults;
  ULONG DemandZeroFaults;
  // The number of pages read from disk to resolve page faults.
  ULONG PagesRead;
  // The number of read operations initiated to resolve page faults.
  ULONG PageReadIos;
  ULONG CacheReads;
  ULONG CacheIos;
  // The number of pages written to the system's pagefiles.
  ULONG PagefilePagesWritten;
  // The number of write operations performed on the system's pagefiles.
  ULONG PagefilePageWriteIos;
  ULONG MappedFilePagesWritten;
  ULONG MappedFilePageWriteIos;
  ULONG PagedPoolUsage;
  ULONG NonPagedPoolUsage;
  ULONG PagedPoolAllocs;
  ULONG PagedPoolFrees;
  ULONG NonPagedPoolAllocs;
  ULONG NonPagedPoolFrees;
  ULONG TotalFreeSystemPtes;
  ULONG SystemCodePage;
  ULONG TotalSystemDriverPages;
  ULONG TotalSystemCodePages;
  ULONG SmallNonPagedLookasideListAllocateHits;
  ULONG SmallPagedLookasideListAllocateHits;
  ULONG Reserved3;
  ULONG MmSystemCachePage;
  ULONG PagedPoolPage;
  ULONG SystemDriverPage;
  ULONG FastReadNoWait;
  ULONG FastReadWait;
  ULONG FastReadResourceMiss;
  ULONG FastReadNotPossible;
  ULONG FastMdlReadNoWait;
  ULONG FastMdlReadWait;
  ULONG FastMdlReadResourceMiss;
  ULONG FastMdlReadNotPossible;
  ULONG MapDataNoWait;
  ULONG MapDataWait;
  ULONG MapDataNoWaitMiss;
  ULONG MapDataWaitMiss;
  ULONG PinMappedDataCount;
  ULONG PinReadNoWait;
  ULONG PinReadWait;
  ULONG PinReadNoWaitMiss;
  ULONG PinReadWaitMiss;
  ULONG CopyReadNoWait;
  ULONG CopyReadWait;
  ULONG CopyReadNoWaitMiss;
  ULONG CopyReadWaitMiss;
  ULONG MdlReadNoWait;
  ULONG MdlReadWait;
  ULONG MdlReadNoWaitMiss;
  ULONG MdlReadWaitMiss;
  ULONG ReadAheadIos;
  ULONG LazyWriteIos;
  ULONG LazyWritePages;
  ULONG DataFlushes;
  ULONG DataPages;
  ULONG ContextSwitches;
  ULONG FirstLevelTbFills;
  ULONG SecondLevelTbFills;
  ULONG SystemCalls;
};

}  // namespace

ProcessMetrics::~ProcessMetrics() { }

size_t GetMaxFds() {
  // Windows is only limited by the amount of physical memory.
  return std::numeric_limits<size_t>::max();
}

size_t GetHandleLimit() {
  // Rounded down from value reported here:
  // http://blogs.technet.com/b/markrussinovich/archive/2009/09/29/3283844.aspx
  return static_cast<size_t>(1 << 23);
}

// static
std::unique_ptr<ProcessMetrics> ProcessMetrics::CreateProcessMetrics(
    ProcessHandle process) {
  return WrapUnique(new ProcessMetrics(process));
}

TimeDelta ProcessMetrics::GetCumulativeCPUUsage() {
  FILETIME creation_time;
  FILETIME exit_time;
  FILETIME kernel_time;
  FILETIME user_time;

  if (!process_.IsValid())
    return TimeDelta();

  if (!GetProcessTimes(process_.Get(), &creation_time, &exit_time, &kernel_time,
                       &user_time)) {
    // This should never fail because we duplicate the handle to guarantee it
    // will remain valid.
    DCHECK(false);
    return TimeDelta();
  }

  return TimeDelta::FromFileTime(kernel_time) +
         TimeDelta::FromFileTime(user_time);
}

bool ProcessMetrics::GetIOCounters(IoCounters* io_counters) const {
  if (!process_.IsValid())
    return false;

  return GetProcessIoCounters(process_.Get(), io_counters) != FALSE;
}

uint64_t ProcessMetrics::GetCumulativeDiskUsageInBytes() {
  IoCounters counters;
  if (!GetIOCounters(&counters))
    return 0;

  return counters.ReadTransferCount + counters.WriteTransferCount +
         counters.OtherTransferCount;
}

ProcessMetrics::ProcessMetrics(ProcessHandle process) {
  if (process) {
    HANDLE duplicate_handle = INVALID_HANDLE_VALUE;
    BOOL result = ::DuplicateHandle(::GetCurrentProcess(), process,
                                    ::GetCurrentProcess(), &duplicate_handle,
                                    PROCESS_QUERY_INFORMATION, FALSE, 0);
    DPCHECK(result);
    process_.Set(duplicate_handle);
  }
}

size_t GetSystemCommitCharge() {
  // Get the System Page Size.
  SYSTEM_INFO system_info;
  GetSystemInfo(&system_info);

  PERFORMANCE_INFORMATION info;
  if (!GetPerformanceInfo(&info, sizeof(info))) {
    DLOG(ERROR) << "Failed to fetch internal performance info.";
    return 0;
  }
  return (info.CommitTotal * system_info.dwPageSize) / 1024;
}

size_t GetPageSize() {
  return PAGESIZE_KB * 1024;
}

// This function uses the following mapping between MEMORYSTATUSEX and
// SystemMemoryInfoKB:
//   ullTotalPhys ==> total
//   ullAvailPhys ==> avail_phys
//   ullTotalPageFile ==> swap_total
//   ullAvailPageFile ==> swap_free
bool GetSystemMemoryInfo(SystemMemoryInfoKB* meminfo) {
  MEMORYSTATUSEX mem_status;
  mem_status.dwLength = sizeof(mem_status);
  if (!::GlobalMemoryStatusEx(&mem_status))
    return false;

  meminfo->total = mem_status.ullTotalPhys / 1024;
  meminfo->avail_phys = mem_status.ullAvailPhys / 1024;
  meminfo->swap_total = mem_status.ullTotalPageFile / 1024;
  meminfo->swap_free = mem_status.ullAvailPageFile / 1024;

  return true;
}

size_t ProcessMetrics::GetMallocUsage() {
  // Unsupported as getting malloc usage on Windows requires iterating through
  // the heap which is slow and crashes.
  return 0;
}

SystemPerformanceInfo::SystemPerformanceInfo() = default;
SystemPerformanceInfo::SystemPerformanceInfo(
    const SystemPerformanceInfo& other) = default;

std::unique_ptr<Value> SystemPerformanceInfo::ToValue() const {
  std::unique_ptr<DictionaryValue> result(new DictionaryValue());

  // Write out uint64_t variables as doubles.
  // Note: this may discard some precision, but for JS there's no other option.
  result->SetDouble("idle_time", strict_cast<double>(idle_time));
  result->SetDouble("read_transfer_count",
                    strict_cast<double>(read_transfer_count));
  result->SetDouble("write_transfer_count",
                    strict_cast<double>(write_transfer_count));
  result->SetDouble("other_transfer_count",
                    strict_cast<double>(other_transfer_count));
  result->SetDouble("read_operation_count",
                    strict_cast<double>(read_operation_count));
  result->SetDouble("write_operation_count",
                    strict_cast<double>(write_operation_count));
  result->SetDouble("other_operation_count",
                    strict_cast<double>(other_operation_count));
  result->SetDouble("pagefile_pages_written",
                    strict_cast<double>(pagefile_pages_written));
  result->SetDouble("pagefile_pages_write_ios",
                    strict_cast<double>(pagefile_pages_write_ios));
  result->SetDouble("available_pages", strict_cast<double>(available_pages));
  result->SetDouble("pages_read", strict_cast<double>(pages_read));
  result->SetDouble("page_read_ios", strict_cast<double>(page_read_ios));

  return result;
}

// Retrieves performance counters from the operating system.
// Fills in the provided |info| structure. Returns true on success.
BASE_EXPORT bool GetSystemPerformanceInfo(SystemPerformanceInfo* info) {
  static const auto query_system_information_ptr =
      reinterpret_cast<decltype(&::NtQuerySystemInformation)>(GetProcAddress(
          GetModuleHandle(L"ntdll.dll"), "NtQuerySystemInformation"));
  if (!query_system_information_ptr)
    return false;

  SYSTEM_PERFORMANCE_INFORMATION counters = {};
  {
    // The call to NtQuerySystemInformation might block on a lock.
    base::ScopedBlockingCall scoped_blocking_call(FROM_HERE,
                                                  BlockingType::MAY_BLOCK);
    if (query_system_information_ptr(::SystemPerformanceInformation, &counters,
                                     sizeof(SYSTEM_PERFORMANCE_INFORMATION),
                                     nullptr) != STATUS_SUCCESS) {
      return false;
    }
  }

  info->idle_time = counters.IdleTime.QuadPart;
  info->read_transfer_count = counters.ReadTransferCount.QuadPart;
  info->write_transfer_count = counters.WriteTransferCount.QuadPart;
  info->other_transfer_count = counters.OtherTransferCount.QuadPart;
  info->read_operation_count = counters.ReadOperationCount;
  info->write_operation_count = counters.WriteOperationCount;
  info->other_operation_count = counters.OtherOperationCount;
  info->pagefile_pages_written = counters.PagefilePagesWritten;
  info->pagefile_pages_write_ios = counters.PagefilePageWriteIos;
  info->available_pages = counters.AvailablePages;
  info->pages_read = counters.PagesRead;
  info->page_read_ios = counters.PageReadIos;

  return true;
}

}  // namespace base
