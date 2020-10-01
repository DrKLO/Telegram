// Copyright (c) 2011 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

#include "base/system/sys_info.h"

#include <stddef.h>
#include <stdint.h>

#include <limits>

#include "base/files/file_util.h"
#include "base/lazy_instance.h"
#include "base/logging.h"
#include "base/numerics/safe_conversions.h"
#include "base/process/process_metrics.h"
#include "base/strings/string_number_conversions.h"
#include "base/strings/string_util.h"
#include "base/system/sys_info_internal.h"
#include "build/build_config.h"

namespace {

int64_t AmountOfMemory(int pages_name) {
  long pages = sysconf(pages_name);
  long page_size = sysconf(_SC_PAGESIZE);
  if (pages == -1 || page_size == -1) {
    NOTREACHED();
    return 0;
  }
  return static_cast<int64_t>(pages) * page_size;
}

int64_t AmountOfPhysicalMemory() {
  return AmountOfMemory(_SC_PHYS_PAGES);
}

base::LazyInstance<
    base::internal::LazySysInfoValue<int64_t, AmountOfPhysicalMemory>>::Leaky
    g_lazy_physical_memory = LAZY_INSTANCE_INITIALIZER;

}  // namespace

namespace base {

// static
int64_t SysInfo::AmountOfPhysicalMemoryImpl() {
  return g_lazy_physical_memory.Get().value();
}

// static
int64_t SysInfo::AmountOfAvailablePhysicalMemoryImpl() {
  SystemMemoryInfoKB info;
  if (!GetSystemMemoryInfo(&info))
    return 0;
  return AmountOfAvailablePhysicalMemory(info);
}

// static
int64_t SysInfo::AmountOfAvailablePhysicalMemory(
    const SystemMemoryInfoKB& info) {
  // See details here:
  // https://git.kernel.org/cgit/linux/kernel/git/torvalds/linux.git/commit/?id=34e431b0ae398fc54ea69ff85ec700722c9da773
  // The fallback logic (when there is no MemAvailable) would be more precise
  // if we had info about zones watermarks (/proc/zoneinfo).
  int64_t res_kb = info.available != 0
                       ? info.available - info.active_file
                       : info.free + info.reclaimable + info.inactive_file;
  return res_kb * 1024;
}

// static
std::string SysInfo::CPUModelName() {
#if defined(OS_CHROMEOS) && defined(ARCH_CPU_ARMEL)
  const char kCpuModelPrefix[] = "Hardware";
#else
  const char kCpuModelPrefix[] = "model name";
#endif
  std::string contents;
  ReadFileToString(FilePath("/proc/cpuinfo"), &contents);
  DCHECK(!contents.empty());
  if (!contents.empty()) {
    std::istringstream iss(contents);
    std::string line;
    while (std::getline(iss, line)) {
      if (line.compare(0, strlen(kCpuModelPrefix), kCpuModelPrefix) == 0) {
        size_t pos = line.find(": ");
        return line.substr(pos + 2);
      }
    }
  }
  return std::string();
}

#if !defined(OS_ANDROID)
// static
SysInfo::HardwareInfo SysInfo::GetHardwareInfoSync() {
  static const size_t kMaxStringSize = 100u;
  HardwareInfo info;
  std::string data;
  if (ReadFileToStringWithMaxSize(
          FilePath("/sys/devices/virtual/dmi/id/sys_vendor"), &data,
          kMaxStringSize)) {
    TrimWhitespaceASCII(data, TrimPositions::TRIM_ALL, &info.manufacturer);
  }
  if (ReadFileToStringWithMaxSize(
          FilePath("/sys/devices/virtual/dmi/id/product_name"), &data,
          kMaxStringSize)) {
    TrimWhitespaceASCII(data, TrimPositions::TRIM_ALL, &info.model);
  }
  DCHECK(IsStringUTF8(info.manufacturer));
  DCHECK(IsStringUTF8(info.model));
  return info;
}
#endif

}  // namespace base
