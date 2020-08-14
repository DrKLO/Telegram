// Copyright 2014 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.
#include "base/debug/gdi_debug_util_win.h"

#include <algorithm>
#include <cmath>

#include <TlHelp32.h>
#include <psapi.h>
#include <stddef.h>
#include <windows.h>
#include <winternl.h>

#include "base/debug/alias.h"
#include "base/logging.h"
#include "base/optional.h"
#include "base/process/process.h"
#include "base/win/scoped_handle.h"
#include "base/win/win_util.h"
#include "base/win/windows_version.h"

namespace {

// A partial PEB up until GdiSharedHandleTable.
// Derived from the ntdll symbols (ntdll!_PEB).
template <typename PointerType>
struct PartialWinPeb {
  unsigned char InheritedAddressSpace;
  unsigned char ReadImageFileExecOptions;
  unsigned char BeingDebugged;
  unsigned char ImageUsesLargePages : 1;
  unsigned char IsProtectedProcess : 1;
  unsigned char IsLegacyProcess : 1;
  unsigned char IsImageDynamicallyRelocated : 1;
  unsigned char SkipPatchingUser32Forwarders : 1;
  unsigned char IsAppContainer : 1;
  unsigned char IsProtectedProcessLight : 1;
  unsigned char IsLongPathAwareProcess : 1;
  PointerType Mutant;
  PointerType ImageBaseAddress;
  PointerType Ldr;
  PointerType ProcessParamters;
  PointerType SubSystemData;
  PointerType ProcessHeap;
  PointerType FastPebLock;
  PointerType AtlThunkSListPtr;
  PointerType IFEOKey;
  uint32_t ProcessInJob : 1;
  uint32_t ProcessInitializing : 1;
  uint32_t ProcessUsingVEH : 1;
  uint32_t ProcessUsingVCH : 1;
  uint32_t ProcessUsingFTH : 1;
  uint32_t ProcessPreviouslyThrottled : 1;
  uint32_t ProcessCurrentlyThrottled : 1;
  uint32_t ProcessImagesHotPatched : 1;
  PointerType KernelCallbackTable;
  uint32_t SystemReserved;
  uint32_t AtlThunkSListPtr32;
  PointerType ApiSetMap;
  uint32_t TlsExpansionCounter;
  PointerType TlsBitmap;
  uint32_t TlsBitmapBits[2];
  PointerType ReadOnlySharedMemoryBase;
  PointerType HotpatchInformation;
  PointerType ReadOnlyStaticServerData;
  PointerType AnsiCodePageData;
  PointerType OemCodePageData;
  PointerType UnicodeCaseTableData;
  uint32_t NumberOfProcessors;
  uint32_t NtGlobalFlag;
  uint64_t CriticalSectionTimeout;
  PointerType HeapSegmentReserve;
  PointerType HeapSegmentCommit;
  PointerType HeapDeCommitTotalFreeThreshold;
  PointerType HeapDeCommitFreeBlockThreshold;
  uint32_t NumberOfHeaps;
  uint32_t MaximumNumberOfHeaps;
  PointerType ProcessHeaps;
  PointerType GdiSharedHandleTable;
};

// Found from
// https://stackoverflow.com/questions/13905661/how-to-get-list-of-gdi-handles.
enum GdiHandleType : USHORT {
  kDC = 1,
  kRegion = 4,
  kBitmap = 5,
  kPalette = 8,
  kFont = 10,
  kBrush = 16,
  kPen = 48,
};

// Adapted from GDICELL.
template <typename PointerType>
struct GdiTableEntry {
  PointerType pKernelAddress;
  USHORT wProcessId;
  USHORT wCount;
  USHORT wUpper;
  GdiHandleType wType;
  PointerType pUserAddress;
};

// Types and names used for regular processes.
struct RegularProcessTypes {
  using QueryInformationProcessFunc = decltype(NtQueryInformationProcess);
  static const char* query_information_process_name;
  // PROCESS_BASIC_INFORMATION
  struct ProcessBasicInformation {
    PVOID Reserved1;
    PVOID PebBaseAddress;
    PVOID Reserved2[2];
    ULONG_PTR UniqueProcessId;
    PVOID Reserved3;
  };

  using ReadVirtualMemoryFunc = NTSTATUS NTAPI(IN HANDLE ProcessHandle,
                                               IN PVOID BaseAddress,
                                               OUT PVOID Buffer,
                                               IN SIZE_T Size,
                                               OUT PSIZE_T NumberOfBytesRead);
  static const char* read_virtual_memory_func_name;
  using NativePointerType = PVOID;
};

// static
const char* RegularProcessTypes::query_information_process_name =
    "NtQueryInformationProcess";

// static
const char* RegularProcessTypes::read_virtual_memory_func_name =
    "NtReadVirtualMemory";

// Types and names used for WOW based processes.
struct WowProcessTypes {
  // http://crbug.com/972185: Clang doesn't handle PVOID64 correctly, so we use
  // uint64_t as a substitute.

  // NtWow64QueryInformationProcess64 and NtQueryInformationProcess share the
  // same signature.
  using QueryInformationProcessFunc = decltype(NtQueryInformationProcess);
  static const char* query_information_process_name;
  // PROCESS_BASIC_INFORMATION_WOW64
  struct ProcessBasicInformation {
    PVOID Reserved1[2];
    uint64_t PebBaseAddress;
    PVOID Reserved2[4];
    ULONG_PTR UniqueProcessId[2];
    PVOID Reserved3[2];
  };

  using ReadVirtualMemoryFunc = NTSTATUS NTAPI(IN HANDLE ProcessHandle,
                                               IN uint64_t BaseAddress,
                                               OUT PVOID Buffer,
                                               IN ULONG64 Size,
                                               OUT PULONG64 NumberOfBytesRead);
  static const char* read_virtual_memory_func_name;
  using NativePointerType = uint64_t;
};

// static
const char* WowProcessTypes::query_information_process_name =
    "NtWow64QueryInformationProcess64";

// static
const char* WowProcessTypes::read_virtual_memory_func_name =
    "NtWow64ReadVirtualMemory64";

// To prevent from having to write a regular and WOW codepaths that do the same
// thing with different structures and functions, GetGdiTableEntries is
// templated to expect either RegularProcessTypes or WowProcessTypes.
template <typename ProcessType>
std::vector<GdiTableEntry<typename ProcessType::NativePointerType>>
GetGdiTableEntries(const base::Process& process) {
  using GdiTableEntryVector =
      std::vector<GdiTableEntry<typename ProcessType::NativePointerType>>;
  HMODULE ntdll = GetModuleHandle(L"ntdll.dll");
  if (!ntdll)
    return GdiTableEntryVector();

  static auto query_information_process_func =
      reinterpret_cast<typename ProcessType::QueryInformationProcessFunc*>(
          GetProcAddress(ntdll, ProcessType::query_information_process_name));
  if (!query_information_process_func) {
    LOG(ERROR) << ProcessType::query_information_process_name << " Missing";
    return GdiTableEntryVector();
  }

  typename ProcessType::ProcessBasicInformation basic_info;
  NTSTATUS result =
      query_information_process_func(process.Handle(), ProcessBasicInformation,
                                     &basic_info, sizeof(basic_info), nullptr);
  if (result != 0) {
    LOG(ERROR) << ProcessType::query_information_process_name << " Failed "
               << std::hex << result;
    return GdiTableEntryVector();
  }

  static auto read_virtual_mem_func =
      reinterpret_cast<typename ProcessType::ReadVirtualMemoryFunc*>(
          GetProcAddress(ntdll, ProcessType::read_virtual_memory_func_name));
  if (!read_virtual_mem_func) {
    LOG(ERROR) << ProcessType::read_virtual_memory_func_name << " Missing";
    return GdiTableEntryVector();
  }

  PartialWinPeb<typename ProcessType::NativePointerType> peb;
  result = read_virtual_mem_func(process.Handle(), basic_info.PebBaseAddress,
                                 &peb, sizeof(peb), nullptr);
  if (result != 0) {
    LOG(ERROR) << ProcessType::read_virtual_memory_func_name << " PEB Failed "
               << std::hex << result;
    return GdiTableEntryVector();
  }

  // Estimated size derived from address space allocation of the table:
  // Windows 10
  // 32-bit Size: 1052672 bytes
  // 64-bit Size: 1576960 bytes
  // sizeof(GdiTableEntry)
  // 32-bit: 16 bytes
  // 64-bit: 24 bytes
  // Entry Count
  // 32-bit: 65792
  // 64-bit: 65706ish
  // So we'll take a look at 65536 entries since that's the maximum handle count.
  constexpr int kGdiTableEntryCount = 65536;
  GdiTableEntryVector entries;
  entries.resize(kGdiTableEntryCount);
  result = read_virtual_mem_func(
      process.Handle(), peb.GdiSharedHandleTable, &entries[0],
      sizeof(typename GdiTableEntryVector::value_type) * entries.size(),
      nullptr);
  if (result != 0) {
    LOG(ERROR) << ProcessType::read_virtual_memory_func_name
               << " GDI Handle Table Failed " << std::hex << result;
    return GdiTableEntryVector();
  }

  return entries;
}

// Iterates through |gdi_table| and finds handles that belong to |pid|,
// incrementing the appropriate fields in |base::debug::GdiHandleCounts|.
template <typename PointerType>
base::debug::GdiHandleCounts CountHandleTypesFromTable(
    DWORD pid,
    const std::vector<GdiTableEntry<PointerType>>& gdi_table) {
  base::debug::GdiHandleCounts counts{};
  for (const auto& entry : gdi_table) {
    if (entry.wProcessId != pid)
      continue;

    switch (entry.wType & 0x7F) {
      case GdiHandleType::kDC:
        ++counts.dcs;
        break;
      case GdiHandleType::kRegion:
        ++counts.regions;
        break;
      case GdiHandleType::kBitmap:
        ++counts.bitmaps;
        break;
      case GdiHandleType::kPalette:
        ++counts.palettes;
        break;
      case GdiHandleType::kFont:
        ++counts.fonts;
        break;
      case GdiHandleType::kBrush:
        ++counts.brushes;
        break;
      case GdiHandleType::kPen:
        ++counts.pens;
        break;
      default:
        ++counts.unknown;
        break;
    }
  }
  counts.total_tracked = counts.dcs + counts.regions + counts.bitmaps +
                         counts.palettes + counts.fonts + counts.brushes +
                         counts.pens + counts.unknown;
  return counts;
}

template <typename ProcessType>
base::Optional<base::debug::GdiHandleCounts> CollectGdiHandleCountsImpl(
    DWORD pid) {
  base::Process process = base::Process::OpenWithExtraPrivileges(pid);
  if (!process.IsValid())
    return base::nullopt;

  std::vector<GdiTableEntry<typename ProcessType::NativePointerType>>
      gdi_entries = GetGdiTableEntries<ProcessType>(process);
  return CountHandleTypesFromTable(pid, gdi_entries);
}

// Returns the GDI Handle counts from the GDI Shared handle table. Empty on
// failure.
base::Optional<base::debug::GdiHandleCounts> CollectGdiHandleCounts(DWORD pid) {
  if (base::win::OSInfo::GetInstance()->wow64_status() ==
      base::win::OSInfo::WOW64_ENABLED) {
    return CollectGdiHandleCountsImpl<WowProcessTypes>(pid);
  }

  return CollectGdiHandleCountsImpl<RegularProcessTypes>(pid);
}

constexpr size_t kLotsOfMemory = 1500 * 1024 * 1024;  // 1.5GB

HANDLE NOINLINE GetToolhelpSnapshot() {
  HANDLE snapshot = CreateToolhelp32Snapshot(TH32CS_SNAPPROCESS, 0);
  CHECK_NE(INVALID_HANDLE_VALUE, snapshot);
  return snapshot;
}

void NOINLINE GetFirstProcess(HANDLE snapshot, PROCESSENTRY32* proc_entry) {
  proc_entry->dwSize = sizeof(PROCESSENTRY32);
  CHECK(Process32First(snapshot, proc_entry));
}

void NOINLINE CrashIfExcessiveHandles(DWORD num_gdi_handles) {
  // By default, Windows 10 allows a max of 10,000 GDI handles per process.
  // Number found by inspecting
  //
  // HKEY_LOCAL_MACHINE\SOFTWARE\Microsoft\Windows NT\
  //    CurrentVersion\Windows\GDIProcessHandleQuota
  //
  // on a Windows 10 laptop.
  static constexpr DWORD kLotsOfHandles = 9990;
  CHECK_LE(num_gdi_handles, kLotsOfHandles);
}

void NOINLINE
CrashIfPagefileUsageTooLarge(const PROCESS_MEMORY_COUNTERS_EX& pmc) {
  CHECK_LE(pmc.PagefileUsage, kLotsOfMemory);
}

void NOINLINE
CrashIfPrivateUsageTooLarge(const PROCESS_MEMORY_COUNTERS_EX& pmc) {
  CHECK_LE(pmc.PrivateUsage, kLotsOfMemory);
}

void NOINLINE CrashIfCannotAllocateSmallBitmap(BITMAPINFOHEADER* header,
                                               HANDLE shared_section) {
  void* small_data = nullptr;
  base::debug::Alias(&small_data);
  header->biWidth = 5;
  header->biHeight = -5;
  HBITMAP small_bitmap =
      CreateDIBSection(nullptr, reinterpret_cast<BITMAPINFO*>(&header), 0,
                       &small_data, shared_section, 0);
  CHECK(small_bitmap != nullptr);
  DeleteObject(small_bitmap);
}

void NOINLINE GetProcessMemoryInfo(PROCESS_MEMORY_COUNTERS_EX* pmc) {
  pmc->cb = sizeof(*pmc);
  CHECK(GetProcessMemoryInfo(GetCurrentProcess(),
                             reinterpret_cast<PROCESS_MEMORY_COUNTERS*>(pmc),
                             sizeof(*pmc)));
}

DWORD NOINLINE GetNumGdiHandles() {
  DWORD num_gdi_handles = GetGuiResources(GetCurrentProcess(), GR_GDIOBJECTS);
  if (num_gdi_handles == 0) {
    DWORD get_gui_resources_error = GetLastError();
    base::debug::Alias(&get_gui_resources_error);
    CHECK(false);
  }
  return num_gdi_handles;
}

void CollectChildGDIUsageAndDie(DWORD parent_pid) {
  HANDLE snapshot = GetToolhelpSnapshot();

  int total_process_count = 0;
  base::debug::Alias(&total_process_count);
  int total_peak_gdi_count = 0;
  base::debug::Alias(&total_peak_gdi_count);
  int total_gdi_count = 0;
  base::debug::Alias(&total_gdi_count);
  int total_user_count = 0;
  base::debug::Alias(&total_user_count);

  int child_count = 0;
  base::debug::Alias(&child_count);
  int peak_gdi_count = 0;
  base::debug::Alias(&peak_gdi_count);
  int sum_gdi_count = 0;
  base::debug::Alias(&sum_gdi_count);
  int sum_user_count = 0;
  base::debug::Alias(&sum_user_count);

  PROCESSENTRY32 proc_entry = {};
  GetFirstProcess(snapshot, &proc_entry);

  do {
    base::win::ScopedHandle process(
        OpenProcess(PROCESS_QUERY_INFORMATION,
                    FALSE,
                    proc_entry.th32ProcessID));
    if (!process.IsValid())
      continue;

    int num_gdi_handles = GetGuiResources(process.Get(), GR_GDIOBJECTS);
    int num_user_handles = GetGuiResources(process.Get(), GR_USEROBJECTS);

    // Compute sum and peak counts for all processes.
    ++total_process_count;
    total_user_count += num_user_handles;
    total_gdi_count += num_gdi_handles;
    total_peak_gdi_count = std::max(total_peak_gdi_count, num_gdi_handles);

    if (parent_pid != proc_entry.th32ParentProcessID)
      continue;

    // Compute sum and peak counts for child processes.
    ++child_count;
    sum_user_count += num_user_handles;
    sum_gdi_count += num_gdi_handles;
    peak_gdi_count = std::max(peak_gdi_count, num_gdi_handles);

  } while (Process32Next(snapshot, &proc_entry));

  CloseHandle(snapshot);
  CHECK(false);
}

}  // namespace

namespace base {
namespace debug {

void CollectGDIUsageAndDie(BITMAPINFOHEADER* header, HANDLE shared_section) {
  // Make sure parameters are saved in the minidump.
  DWORD last_error = GetLastError();
  bool is_gdi_available = base::win::IsUser32AndGdi32Available();

  LONG width = header ? header->biWidth : 0;
  LONG height = header ? header->biHeight : 0;

  base::debug::Alias(&last_error);
  base::debug::Alias(&is_gdi_available);
  base::debug::Alias(&width);
  base::debug::Alias(&height);
  base::debug::Alias(&shared_section);

  DWORD num_user_handles = GetGuiResources(GetCurrentProcess(), GR_USEROBJECTS);
  DWORD num_gdi_handles = GetNumGdiHandles();

  base::debug::Alias(&num_gdi_handles);
  base::debug::Alias(&num_user_handles);

  base::Optional<GdiHandleCounts> optional_handle_counts =
      CollectGdiHandleCounts(GetCurrentProcessId());
  bool handle_counts_set = optional_handle_counts.has_value();
  GdiHandleCounts handle_counts =
      optional_handle_counts.value_or(GdiHandleCounts());
  int tracked_dcs = handle_counts.dcs;
  int tracked_regions = handle_counts.regions;
  int tracked_bitmaps = handle_counts.bitmaps;
  int tracked_palettes = handle_counts.palettes;
  int tracked_fonts = handle_counts.fonts;
  int tracked_brushes = handle_counts.brushes;
  int tracked_pens = handle_counts.pens;
  int tracked_unknown_handles = handle_counts.unknown;
  int tracked_total = handle_counts.total_tracked;

  base::debug::Alias(&handle_counts_set);
  base::debug::Alias(&tracked_dcs);
  base::debug::Alias(&tracked_regions);
  base::debug::Alias(&tracked_bitmaps);
  base::debug::Alias(&tracked_palettes);
  base::debug::Alias(&tracked_fonts);
  base::debug::Alias(&tracked_brushes);
  base::debug::Alias(&tracked_pens);
  base::debug::Alias(&tracked_unknown_handles);
  base::debug::Alias(&tracked_total);

  CrashIfExcessiveHandles(num_gdi_handles);

  PROCESS_MEMORY_COUNTERS_EX pmc;
  GetProcessMemoryInfo(&pmc);
  CrashIfPagefileUsageTooLarge(pmc);
  CrashIfPrivateUsageTooLarge(pmc);

  if (std::abs(height) * width > 100) {
    // Huh, that's weird.  We don't have crazy handle count, we don't have
    // ridiculous memory usage. Try to allocate a small bitmap and see if that
    // fails too.
    CrashIfCannotAllocateSmallBitmap(header, shared_section);
  }
  // Maybe the child processes are the ones leaking GDI or USER resouces.
  CollectChildGDIUsageAndDie(GetCurrentProcessId());
}

GdiHandleCounts GetGDIHandleCountsInCurrentProcessForTesting() {
  base::Optional<GdiHandleCounts> handle_counts =
      CollectGdiHandleCounts(GetCurrentProcessId());
  DCHECK(handle_counts.has_value());
  return handle_counts.value_or(GdiHandleCounts());
}

}  // namespace debug
}  // namespace base
