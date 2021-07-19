// Copyright 2018 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

#include "base/profiler/module_cache.h"

#include <dlfcn.h>
#include <mach-o/getsect.h>
#include <uuid/uuid.h>

#include "base/strings/string_number_conversions.h"

namespace base {

namespace {

// Returns the unique build ID for a module loaded at |module_addr|. Returns the
// empty string if the function fails to get the build ID.
//
// Build IDs are created by the concatenation of the module's GUID (Windows) /
// UUID (Mac) and an "age" field that indicates how many times that GUID/UUID
// has been reused. In Windows binaries, the "age" field is present in the
// module header, but on the Mac, UUIDs are never reused and so the "age" value
// appended to the UUID is always 0.
std::string GetUniqueId(const void* module_addr) {
  const mach_header_64* mach_header =
      reinterpret_cast<const mach_header_64*>(module_addr);
  DCHECK_EQ(MH_MAGIC_64, mach_header->magic);

  size_t offset = sizeof(mach_header_64);
  size_t offset_limit = sizeof(mach_header_64) + mach_header->sizeofcmds;

  for (uint32_t i = 0; i < mach_header->ncmds; ++i) {
    if (offset + sizeof(load_command) >= offset_limit)
      return std::string();

    const load_command* current_cmd = reinterpret_cast<const load_command*>(
        reinterpret_cast<const uint8_t*>(mach_header) + offset);

    if (offset + current_cmd->cmdsize > offset_limit) {
      // This command runs off the end of the command list. This is malformed.
      return std::string();
    }

    if (current_cmd->cmd == LC_UUID) {
      if (current_cmd->cmdsize < sizeof(uuid_command)) {
        // This "UUID command" is too small. This is malformed.
        return std::string();
      }

      const uuid_command* uuid_cmd =
          reinterpret_cast<const uuid_command*>(current_cmd);
      static_assert(sizeof(uuid_cmd->uuid) == sizeof(uuid_t),
                    "UUID field of UUID command should be 16 bytes.");
      // The ID is comprised of the UUID concatenated with the Mac's "age" value
      // which is always 0.
      return HexEncode(&uuid_cmd->uuid, sizeof(uuid_cmd->uuid)) + "0";
    }
    offset += current_cmd->cmdsize;
  }
  return std::string();
}

// Returns the size of the _TEXT segment of the module loaded at |module_addr|.
size_t GetModuleTextSize(const void* module_addr) {
  const mach_header_64* mach_header =
      reinterpret_cast<const mach_header_64*>(module_addr);
  DCHECK_EQ(MH_MAGIC_64, mach_header->magic);
  unsigned long module_size;
  getsegmentdata(mach_header, SEG_TEXT, &module_size);
  return module_size;
}

}  // namespace

class MacModule : public ModuleCache::Module {
 public:
  MacModule(const Dl_info& dl_info)
      : base_address_(reinterpret_cast<uintptr_t>(dl_info.dli_fbase)),
        id_(GetUniqueId(dl_info.dli_fbase)),
        debug_basename_(FilePath(dl_info.dli_fname).BaseName()),
        size_(GetModuleTextSize(dl_info.dli_fbase)) {}

  MacModule(const MacModule&) = delete;
  MacModule& operator=(const MacModule&) = delete;

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

// static
std::unique_ptr<const ModuleCache::Module> ModuleCache::CreateModuleForAddress(
    uintptr_t address) {
  Dl_info info;
  if (!dladdr(reinterpret_cast<const void*>(address), &info))
    return nullptr;
  return std::make_unique<MacModule>(info);
}

}  // namespace base
