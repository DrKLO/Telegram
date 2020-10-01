// Copyright (c) 2011 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

#include "base/system/sys_info.h"

#include <stddef.h>
#include <stdint.h>
#include <sys/param.h>
#include <sys/shm.h>
#include <sys/sysctl.h>

#include "base/logging.h"
#include "base/stl_util.h"

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

}  // namespace

namespace base {

// static
int SysInfo::NumberOfProcessors() {
  int mib[] = {CTL_HW, HW_NCPU};
  int ncpu;
  size_t size = sizeof(ncpu);
  if (sysctl(mib, base::size(mib), &ncpu, &size, NULL, 0) < 0) {
    NOTREACHED();
    return 1;
  }
  return ncpu;
}

// static
int64_t SysInfo::AmountOfPhysicalMemoryImpl() {
  return AmountOfMemory(_SC_PHYS_PAGES);
}

// static
int64_t SysInfo::AmountOfAvailablePhysicalMemoryImpl() {
  // We should add inactive file-backed memory also but there is no such
  // information from OpenBSD unfortunately.
  return AmountOfMemory(_SC_AVPHYS_PAGES);
}

// static
uint64_t SysInfo::MaxSharedMemorySize() {
  int mib[] = {CTL_KERN, KERN_SHMINFO, KERN_SHMINFO_SHMMAX};
  size_t limit;
  size_t size = sizeof(limit);
  if (sysctl(mib, base::size(mib), &limit, &size, NULL, 0) < 0) {
    NOTREACHED();
    return 0;
  }
  return static_cast<uint64_t>(limit);
}

// static
std::string SysInfo::CPUModelName() {
  int mib[] = {CTL_HW, HW_MODEL};
  char name[256];
  size_t len = base::size(name);
  if (sysctl(mib, base::size(mib), name, &len, NULL, 0) < 0) {
    NOTREACHED();
    return std::string();
  }
  return name;
}

}  // namespace base
