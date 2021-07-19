// Copyright 2017 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

#include "base/debug/stack_trace.h"

#include <elf.h>
#include <link.h>
#include <stddef.h>
#include <threads.h>
#include <unwind.h>
#include <zircon/process.h>
#include <zircon/syscalls.h>
#include <zircon/syscalls/port.h>
#include <zircon/types.h>

#include <algorithm>
#include <array>
#include <iomanip>
#include <iostream>
#include <type_traits>

#include "base/atomic_sequence_num.h"
#include "base/debug/elf_reader.h"
#include "base/logging.h"
#include "base/no_destructor.h"
#include "base/stl_util.h"

namespace base {
namespace debug {
namespace {

struct BacktraceData {
  void** trace_array;
  size_t* count;
  size_t max;
};

_Unwind_Reason_Code UnwindStore(struct _Unwind_Context* context,
                                void* user_data) {
  BacktraceData* data = reinterpret_cast<BacktraceData*>(user_data);
  uintptr_t pc = _Unwind_GetIP(context);
  data->trace_array[*data->count] = reinterpret_cast<void*>(pc);
  *data->count += 1;
  if (*data->count == data->max)
    return _URC_END_OF_STACK;
  return _URC_NO_REASON;
}

// Build a "rwx" C string-based representation of the permission bits.
// The output buffer is reused across calls, and should not be retained across
// consecutive invocations of this function.
const char* PermissionFlagsToString(int flags, char permission_buf[4]) {
  char* permission = permission_buf;

  if (flags & PF_R)
    (*permission++) = 'r';

  if (flags & PF_W)
    (*permission++) = 'w';

  if (flags & PF_X)
    (*permission++) = 'x';

  *permission = '\0';

  return permission_buf;
}

// Stores and queries debugging symbol map info for the current process.
class SymbolMap {
 public:
  struct Segment {
    const void* addr = nullptr;
    size_t relative_addr = 0;
    int permission_flags = 0;
    size_t size = 0;
  };

  struct Module {
    // Maximum number of PT_LOAD segments to process per ELF binary. Most
    // binaries have only 2-3 such segments.
    static constexpr size_t kMaxSegmentCount = 8;

    const void* addr = nullptr;
    std::array<Segment, kMaxSegmentCount> segments;
    size_t segment_count = 0;
    char name[ZX_MAX_NAME_LEN + 1] = {0};
    char build_id[kMaxBuildIdStringLength + 1] = {0};
  };

  SymbolMap();
  ~SymbolMap() = default;

  // Gets all entries for the symbol map.
  span<Module> GetModules() { return {modules_.data(), count_}; }

 private:
  // Component builds of Chrome pull about 250 shared libraries (on Linux), so
  // 512 entries should be enough in most cases.
  static const size_t kMaxMapEntries = 512;

  void Populate();

  // Sorted in descending order by address, for lookup purposes.
  std::array<Module, kMaxMapEntries> modules_;

  size_t count_ = 0;
  bool valid_ = false;

  DISALLOW_COPY_AND_ASSIGN(SymbolMap);
};

SymbolMap::SymbolMap() {
  Populate();
}

void SymbolMap::Populate() {
  zx_handle_t process = zx_process_self();

  // Try to fetch the name of the process' main executable, which was set as the
  // name of the |process| kernel object.
  // TODO(wez): Object names can only have up to ZX_MAX_NAME_LEN characters, so
  // if we keep hitting problems with truncation, find a way to plumb argv[0]
  // through to here instead, e.g. using CommandLine::GetProgramName().
  char app_name[std::extent<decltype(SymbolMap::Module::name)>()];
  zx_status_t status =
      zx_object_get_property(process, ZX_PROP_NAME, app_name, sizeof(app_name));
  if (status == ZX_OK) {
    // The process name may have a process type suffix at the end (e.g.
    // "context", "renderer", gpu"), which doesn't belong in the module list.
    // Trim the suffix from the name.
    for (size_t i = 0; i < base::size(app_name) && app_name[i] != '\0'; ++i) {
      if (app_name[i] == ':') {
        app_name[i] = 0;
        break;
      }
    }
  } else {
    DPLOG(WARNING)
        << "Couldn't get name, falling back to 'app' for program name: "
        << status;
    strlcat(app_name, "app", sizeof(app_name));
  }

  // Retrieve the debug info struct.
  uintptr_t debug_addr;
  status = zx_object_get_property(process, ZX_PROP_PROCESS_DEBUG_ADDR,
                                  &debug_addr, sizeof(debug_addr));
  if (status != ZX_OK) {
    DPLOG(ERROR) << "Couldn't get symbol map for process: " << status;
    return;
  }
  r_debug* debug_info = reinterpret_cast<r_debug*>(debug_addr);

  // Get the link map from the debug info struct.
  link_map* lmap = reinterpret_cast<link_map*>(debug_info->r_map);
  if (!lmap) {
    DPLOG(ERROR) << "Null link_map for process.";
    return;
  }

  // Populate ELF binary metadata into |modules_|.
  while (lmap != nullptr) {
    if (count_ >= kMaxMapEntries)
      break;

    SymbolMap::Module& next_entry = modules_[count_];
    ++count_;

    next_entry.addr = reinterpret_cast<void*>(lmap->l_addr);

    // Create Segment sub-entries for all PT_LOAD headers.
    // Each Segment corresponds to a "mmap" line in the output.
    next_entry.segment_count = 0;
    for (const Elf64_Phdr& phdr : GetElfProgramHeaders(next_entry.addr)) {
      if (phdr.p_type != PT_LOAD)
        continue;

      if (next_entry.segment_count > Module::kMaxSegmentCount) {
        LOG(WARNING) << "Exceeded the maximum number of segments.";
        break;
      }

      Segment segment;
      segment.addr =
          reinterpret_cast<const char*>(next_entry.addr) + phdr.p_vaddr;
      segment.relative_addr = phdr.p_vaddr;
      segment.size = phdr.p_memsz;
      segment.permission_flags = phdr.p_flags;

      next_entry.segments[next_entry.segment_count] = std::move(segment);
      ++next_entry.segment_count;
    }

    // Get the human-readable library name from the ELF header, falling back on
    // using names from the link map for binaries that aren't shared libraries.
    Optional<StringPiece> elf_library_name =
        ReadElfLibraryName(next_entry.addr);
    if (elf_library_name) {
      strlcpy(next_entry.name, elf_library_name->data(),
              elf_library_name->size() + 1);
    } else {
      StringPiece link_map_name(lmap->l_name[0] ? lmap->l_name : app_name);

      // The "module" stack trace annotation doesn't allow for strings which
      // resemble paths, so extract the filename portion from |link_map_name|.
      size_t directory_prefix_idx = link_map_name.find_last_of("/");
      if (directory_prefix_idx != StringPiece::npos) {
        link_map_name = link_map_name.substr(
            directory_prefix_idx + 1,
            link_map_name.size() - directory_prefix_idx - 1);
      }
      strlcpy(next_entry.name, link_map_name.data(), link_map_name.size() + 1);
    }

    if (!ReadElfBuildId(next_entry.addr, false, next_entry.build_id)) {
      LOG(WARNING) << "Couldn't read build ID.";
      continue;
    }

    lmap = lmap->l_next;
  }

  valid_ = true;
}

}  // namespace

// static
bool EnableInProcessStackDumping() {
  // StackTrace works to capture the current stack (e.g. for diagnostics added
  // to code), but for local capture and print of backtraces, we just let the
  // system crashlogger take over. It handles printing out a nicely formatted
  // backtrace with dso information, relative offsets, etc. that we can then
  // filter with addr2line in the run script to get file/line info.
  return true;
}

size_t CollectStackTrace(void** trace, size_t count) {
  size_t frame_count = 0;
  BacktraceData data = {trace, &frame_count, count};
  _Unwind_Backtrace(&UnwindStore, &data);
  return frame_count;
}

void StackTrace::PrintWithPrefix(const char* prefix_string) const {
  OutputToStreamWithPrefix(&std::cerr, prefix_string);
}

// Emits stack trace data using the symbolizer markup format specified at:
// https://fuchsia.googlesource.com/zircon/+/master/docs/symbolizer_markup.md
void StackTrace::OutputToStreamWithPrefix(std::ostream* os,
                                          const char* prefix_string) const {
  SymbolMap map;

  int module_id = 0;
  *os << "{{{reset}}}\n";
  for (const SymbolMap::Module& entry : map.GetModules()) {
    *os << "{{{module:" << module_id << ":" << entry.name
        << ":elf:" << entry.build_id << "}}}\n";

    for (size_t i = 0; i < entry.segment_count; ++i) {
      const SymbolMap::Segment& segment = entry.segments[i];

      char permission_string[4] = {};
      *os << "{{{mmap:" << segment.addr << ":0x" << std::hex << segment.size
          << std::dec << ":load:" << module_id << ":"
          << PermissionFlagsToString(segment.permission_flags,
                                     permission_string)
          << ":"
          << "0x" << std::hex << segment.relative_addr << std::dec << "}}}\n";
    }

    ++module_id;
  }

  for (size_t i = 0; i < count_; ++i)
    *os << "{{{bt:" << i << ":" << trace_[i] << "}}}\n";
}

}  // namespace debug
}  // namespace base
