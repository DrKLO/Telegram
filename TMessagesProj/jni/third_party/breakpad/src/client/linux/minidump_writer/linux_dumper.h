// Copyright (c) 2010, Google Inc.
// All rights reserved.
//
// Redistribution and use in source and binary forms, with or without
// modification, are permitted provided that the following conditions are
// met:
//
//     * Redistributions of source code must retain the above copyright
// notice, this list of conditions and the following disclaimer.
//     * Redistributions in binary form must reproduce the above
// copyright notice, this list of conditions and the following disclaimer
// in the documentation and/or other materials provided with the
// distribution.
//     * Neither the name of Google Inc. nor the names of its
// contributors may be used to endorse or promote products derived from
// this software without specific prior written permission.
//
// THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
// "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT
// LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR
// A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT
// OWNER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
// SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT
// LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,
// DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY
// THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
// (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
// OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.

// linux_dumper.h: Define the google_breakpad::LinuxDumper class, which
// is a base class for extracting information of a crashed process. It
// was originally a complete implementation using the ptrace API, but
// has been refactored to allow derived implementations supporting both
// ptrace and core dump. A portion of the original implementation is now
// in google_breakpad::LinuxPtraceDumper (see linux_ptrace_dumper.h for
// details).

#ifndef CLIENT_LINUX_MINIDUMP_WRITER_LINUX_DUMPER_H_
#define CLIENT_LINUX_MINIDUMP_WRITER_LINUX_DUMPER_H_

#include <elf.h>
#include <linux/limits.h>
#include <stdint.h>
#include <sys/types.h>
#include <sys/user.h>

#include "client/linux/dump_writer_common/mapping_info.h"
#include "client/linux/dump_writer_common/thread_info.h"
#include "common/memory.h"
#include "google_breakpad/common/minidump_format.h"

namespace google_breakpad {

// Typedef for our parsing of the auxv variables in /proc/pid/auxv.
#if defined(__i386) || defined(__ARM_EABI__) || \
 (defined(__mips__) && _MIPS_SIM == _ABIO32)
typedef Elf32_auxv_t elf_aux_entry;
#elif defined(__x86_64) || defined(__aarch64__) || \
     (defined(__mips__) && _MIPS_SIM != _ABIO32)
typedef Elf64_auxv_t elf_aux_entry;
#endif

typedef __typeof__(((elf_aux_entry*) 0)->a_un.a_val) elf_aux_val_t;

// When we find the VDSO mapping in the process's address space, this
// is the name we use for it when writing it to the minidump.
// This should always be less than NAME_MAX!
const char kLinuxGateLibraryName[] = "linux-gate.so";

class LinuxDumper {
 public:
  explicit LinuxDumper(pid_t pid);

  virtual ~LinuxDumper();

  // Parse the data for |threads| and |mappings|.
  virtual bool Init();

  // Return true if the dumper performs a post-mortem dump.
  virtual bool IsPostMortem() const = 0;

  // Suspend/resume all threads in the given process.
  virtual bool ThreadsSuspend() = 0;
  virtual bool ThreadsResume() = 0;

  // Read information about the |index|-th thread of |threads_|.
  // Returns true on success. One must have called |ThreadsSuspend| first.
  virtual bool GetThreadInfoByIndex(size_t index, ThreadInfo* info) = 0;

  // These are only valid after a call to |Init|.
  const wasteful_vector<pid_t> &threads() { return threads_; }
  const wasteful_vector<MappingInfo*> &mappings() { return mappings_; }
  const MappingInfo* FindMapping(const void* address) const;
  const wasteful_vector<elf_aux_val_t>& auxv() { return auxv_; }

  // Find a block of memory to take as the stack given the top of stack pointer.
  //   stack: (output) the lowest address in the memory area
  //   stack_len: (output) the length of the memory area
  //   stack_top: the current top of the stack
  bool GetStackInfo(const void** stack, size_t* stack_len, uintptr_t stack_top);

  PageAllocator* allocator() { return &allocator_; }

  // Copy content of |length| bytes from a given process |child|,
  // starting from |src|, into |dest|. Returns true on success.
  virtual bool CopyFromProcess(void* dest, pid_t child, const void* src,
                               size_t length) = 0;

  // Builds a proc path for a certain pid for a node (/proc/<pid>/<node>).
  // |path| is a character array of at least NAME_MAX bytes to return the
  // result.|node| is the final node without any slashes. Returns true on
  // success.
  virtual bool BuildProcPath(char* path, pid_t pid, const char* node) const = 0;

  // Generate a File ID from the .text section of a mapped entry.
  // If not a member, mapping_id is ignored. This method can also manipulate the
  // |mapping|.name to truncate "(deleted)" from the file name if necessary.
  bool ElfFileIdentifierForMapping(const MappingInfo& mapping,
                                   bool member,
                                   unsigned int mapping_id,
                                   uint8_t identifier[sizeof(MDGUID)]);

  uintptr_t crash_address() const { return crash_address_; }
  void set_crash_address(uintptr_t crash_address) {
    crash_address_ = crash_address;
  }

  int crash_signal() const { return crash_signal_; }
  void set_crash_signal(int crash_signal) { crash_signal_ = crash_signal; }

  pid_t crash_thread() const { return crash_thread_; }
  void set_crash_thread(pid_t crash_thread) { crash_thread_ = crash_thread; }

  // Extracts the effective path and file name of from |mapping|. In most cases
  // the effective name/path are just the mapping's path and basename. In some
  // other cases, however, a library can be mapped from an archive (e.g., when
  // loading .so libs from an apk on Android) and this method is able to
  // reconstruct the original file name.
  static void GetMappingEffectiveNameAndPath(const MappingInfo& mapping,
                                             char* file_path,
                                             size_t file_path_size,
                                             char* file_name,
                                             size_t file_name_size);

 protected:
  bool ReadAuxv();

  virtual bool EnumerateMappings();

  virtual bool EnumerateThreads() = 0;

  // For the case where a running program has been deleted, it'll show up in
  // /proc/pid/maps as "/path/to/program (deleted)". If this is the case, then
  // see if '/path/to/program (deleted)' matches /proc/pid/exe and return
  // /proc/pid/exe in |path| so ELF identifier generation works correctly. This
  // also checks to see if '/path/to/program (deleted)' exists, so it does not
  // get fooled by a poorly named binary.
  // For programs that don't end with ' (deleted)', this is a no-op.
  // This assumes |path| is a buffer with length NAME_MAX.
  // Returns true if |path| is modified.
  bool HandleDeletedFileInMapping(char* path) const;

   // ID of the crashed process.
  const pid_t pid_;

  // Virtual address at which the process crashed.
  uintptr_t crash_address_;

  // Signal that terminated the crashed process.
  int crash_signal_;

  // ID of the crashed thread.
  pid_t crash_thread_;

  mutable PageAllocator allocator_;

  // IDs of all the threads.
  wasteful_vector<pid_t> threads_;

  // Info from /proc/<pid>/maps.
  wasteful_vector<MappingInfo*> mappings_;

  // Info from /proc/<pid>/auxv
  wasteful_vector<elf_aux_val_t> auxv_;
};

}  // namespace google_breakpad

#endif  // CLIENT_LINUX_HANDLER_LINUX_DUMPER_H_
