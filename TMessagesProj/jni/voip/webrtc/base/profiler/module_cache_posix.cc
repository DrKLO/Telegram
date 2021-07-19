// Copyright 2018 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

#include "base/profiler/module_cache.h"

#include <dlfcn.h>
#include <elf.h>

#include "base/debug/elf_reader.h"
#include "build/build_config.h"

namespace base {

namespace {

// Returns the unique build ID for a module loaded at |module_addr|. Returns the
// empty string if the function fails to get the build ID.
//
// Build IDs follow a cross-platform format consisting of several fields
// concatenated together:
// - the module's unique ID, and
// - the age suffix for incremental builds.
//
// On POSIX, the unique ID is read from the ELF binary located at |module_addr|.
// The age field is always 0.
std::string GetUniqueBuildId(const void* module_addr) {
  base::debug::ElfBuildIdBuffer build_id;
  size_t build_id_length =
      base::debug::ReadElfBuildId(module_addr, true, build_id);
  if (!build_id_length)
    return std::string();

  // Append 0 for the age value.
  return std::string(build_id, build_id_length) + "0";
}

// Returns the offset from |module_addr| to the first byte following the last
// executable segment from the ELF file mapped at |module_addr|.
// It's defined this way so that any executable address from this module is in
// range [addr, addr + GetLastExecutableOffset(addr)).
// If no executable segment is found, returns 0.
size_t GetLastExecutableOffset(const void* module_addr) {
  size_t max_offset = 0;
  for (const Phdr& header : base::debug::GetElfProgramHeaders(module_addr)) {
    if (header.p_type != PT_LOAD || !(header.p_flags & PF_X))
      continue;

    max_offset = std::max(max_offset,
                          static_cast<size_t>(header.p_vaddr + header.p_memsz));
  }

  return max_offset;
}

class PosixModule : public ModuleCache::Module {
 public:
  PosixModule(const Dl_info& dl_info);

  PosixModule(const PosixModule&) = delete;
  PosixModule& operator=(const PosixModule&) = delete;

  // ModuleCache::Module
  uintptr_t GetBaseAddress() const override { return base_address_; }
  std::string GetId() const override { return id_; }
  FilePath GetDebugBasename() const override { return debug_basename_; }
  size_t GetSize() const override { return size_; }
  bool IsNative() const override { return true; }

 private:
  uintptr_t base_address_;
  std::string id_;
  FilePath debug_basename_;
  size_t size_;
};

PosixModule::PosixModule(const Dl_info& dl_info)
    : base_address_(reinterpret_cast<uintptr_t>(dl_info.dli_fbase)),
      id_(GetUniqueBuildId(dl_info.dli_fbase)),
      debug_basename_(FilePath(dl_info.dli_fname).BaseName()),
      size_(GetLastExecutableOffset(dl_info.dli_fbase)) {}

}  // namespace

// static
std::unique_ptr<const ModuleCache::Module> ModuleCache::CreateModuleForAddress(
    uintptr_t address) {
#if defined(ARCH_CPU_ARM64)
  // arm64 has execute-only memory (XOM) protecting code pages from being read.
  // PosixModule reads executable pages in order to extract module info. This
  // may result in a crash if the module is mapped as XOM
  // (https://crbug.com/957801).
  return nullptr;
#else
  Dl_info info;
  if (!dladdr(reinterpret_cast<const void*>(address), &info))
    return nullptr;

  return std::make_unique<PosixModule>(info);
#endif
}

}  // namespace base
