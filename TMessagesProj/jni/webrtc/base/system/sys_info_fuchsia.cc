// Copyright 2017 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

#include "base/system/sys_info.h"

#include <zircon/syscalls.h>

#include "base/fuchsia/fuchsia_logging.h"
#include "base/logging.h"
#include "base/threading/scoped_blocking_call.h"
#include "build/build_config.h"

namespace base {

// static
int64_t SysInfo::AmountOfPhysicalMemoryImpl() {
  return zx_system_get_physmem();
}

// static
int64_t SysInfo::AmountOfAvailablePhysicalMemoryImpl() {
  // TODO(https://crbug.com/986608): Implement this.
  NOTIMPLEMENTED_LOG_ONCE();
  return 0;
}

// static
int SysInfo::NumberOfProcessors() {
  return zx_system_get_num_cpus();
}

// static
int64_t SysInfo::AmountOfVirtualMemory() {
  return 0;
}

// static
std::string SysInfo::OperatingSystemName() {
  return "Fuchsia";
}

// static
int64_t SysInfo::AmountOfFreeDiskSpace(const FilePath& path) {
  base::ScopedBlockingCall scoped_blocking_call(FROM_HERE,
                                                base::BlockingType::MAY_BLOCK);
  NOTIMPLEMENTED_LOG_ONCE();
  return -1;
}

// static
int64_t SysInfo::AmountOfTotalDiskSpace(const FilePath& path) {
  base::ScopedBlockingCall scoped_blocking_call(FROM_HERE,
                                                base::BlockingType::MAY_BLOCK);
  NOTIMPLEMENTED_LOG_ONCE();
  return -1;
}

// static
std::string SysInfo::OperatingSystemVersion() {
  return zx_system_get_version_string();
}

// static
void SysInfo::OperatingSystemVersionNumbers(int32_t* major_version,
                                            int32_t* minor_version,
                                            int32_t* bugfix_version) {
  // Fuchsia doesn't have OS version numbers.
  *major_version = 0;
  *minor_version = 0;
  *bugfix_version = 0;
}

// static
std::string SysInfo::OperatingSystemArchitecture() {
#if defined(ARCH_CPU_X86_64)
  return "x86_64";
#elif defined(ARCH_CPU_ARM64)
  return "aarch64";
#else
#error Unsupported architecture.
#endif
}

// static
size_t SysInfo::VMAllocationGranularity() {
  return getpagesize();
}

}  // namespace base
