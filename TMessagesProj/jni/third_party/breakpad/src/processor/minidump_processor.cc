// Copyright (c) 2006, Google Inc.
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
//    * Neither the name of Google Inc. nor the names of its
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

#include "google_breakpad/processor/minidump_processor.h"

#include <assert.h>
#include <stdio.h>

#include <string>

#include "common/scoped_ptr.h"
#include "common/using_std_string.h"
#include "google_breakpad/processor/call_stack.h"
#include "google_breakpad/processor/minidump.h"
#include "google_breakpad/processor/process_state.h"
#include "google_breakpad/processor/exploitability.h"
#include "google_breakpad/processor/stack_frame_symbolizer.h"
#include "processor/logging.h"
#include "processor/stackwalker_x86.h"
#include "processor/symbolic_constants_win.h"

namespace google_breakpad {

MinidumpProcessor::MinidumpProcessor(SymbolSupplier *supplier,
                                     SourceLineResolverInterface *resolver)
    : frame_symbolizer_(new StackFrameSymbolizer(supplier, resolver)),
      own_frame_symbolizer_(true),
      enable_exploitability_(false) {
}

MinidumpProcessor::MinidumpProcessor(SymbolSupplier *supplier,
                                     SourceLineResolverInterface *resolver,
                                     bool enable_exploitability)
    : frame_symbolizer_(new StackFrameSymbolizer(supplier, resolver)),
      own_frame_symbolizer_(true),
      enable_exploitability_(enable_exploitability) {
}

MinidumpProcessor::MinidumpProcessor(StackFrameSymbolizer *frame_symbolizer,
                                     bool enable_exploitability)
    : frame_symbolizer_(frame_symbolizer),
      own_frame_symbolizer_(false),
      enable_exploitability_(enable_exploitability) {
  assert(frame_symbolizer_);
}

MinidumpProcessor::~MinidumpProcessor() {
  if (own_frame_symbolizer_) delete frame_symbolizer_;
}

ProcessResult MinidumpProcessor::Process(
    Minidump *dump, ProcessState *process_state) {
  assert(dump);
  assert(process_state);

  process_state->Clear();

  const MDRawHeader *header = dump->header();
  if (!header) {
    BPLOG(ERROR) << "Minidump " << dump->path() << " has no header";
    return PROCESS_ERROR_NO_MINIDUMP_HEADER;
  }
  process_state->time_date_stamp_ = header->time_date_stamp;

  bool has_process_create_time =
      GetProcessCreateTime(dump, &process_state->process_create_time_);

  bool has_cpu_info = GetCPUInfo(dump, &process_state->system_info_);
  bool has_os_info = GetOSInfo(dump, &process_state->system_info_);

  uint32_t dump_thread_id = 0;
  bool has_dump_thread = false;
  uint32_t requesting_thread_id = 0;
  bool has_requesting_thread = false;

  MinidumpBreakpadInfo *breakpad_info = dump->GetBreakpadInfo();
  if (breakpad_info) {
    has_dump_thread = breakpad_info->GetDumpThreadID(&dump_thread_id);
    has_requesting_thread =
        breakpad_info->GetRequestingThreadID(&requesting_thread_id);
  }

  MinidumpException *exception = dump->GetException();
  if (exception) {
    process_state->crashed_ = true;
    has_requesting_thread = exception->GetThreadID(&requesting_thread_id);

    process_state->crash_reason_ = GetCrashReason(
        dump, &process_state->crash_address_);
  }

  // This will just return an empty string if it doesn't exist.
  process_state->assertion_ = GetAssertion(dump);

  MinidumpModuleList *module_list = dump->GetModuleList();

  // Put a copy of the module list into ProcessState object.  This is not
  // necessarily a MinidumpModuleList, but it adheres to the CodeModules
  // interface, which is all that ProcessState needs to expose.
  if (module_list)
    process_state->modules_ = module_list->Copy();

  MinidumpMemoryList *memory_list = dump->GetMemoryList();
  if (memory_list) {
    BPLOG(INFO) << "Found " << memory_list->region_count()
                << " memory regions.";
  }

  MinidumpThreadList *threads = dump->GetThreadList();
  if (!threads) {
    BPLOG(ERROR) << "Minidump " << dump->path() << " has no thread list";
    return PROCESS_ERROR_NO_THREAD_LIST;
  }

  BPLOG(INFO) << "Minidump " << dump->path() << " has " <<
      (has_cpu_info            ? "" : "no ") << "CPU info, " <<
      (has_os_info             ? "" : "no ") << "OS info, " <<
      (breakpad_info != NULL   ? "" : "no ") << "Breakpad info, " <<
      (exception != NULL       ? "" : "no ") << "exception, " <<
      (module_list != NULL     ? "" : "no ") << "module list, " <<
      (threads != NULL         ? "" : "no ") << "thread list, " <<
      (has_dump_thread         ? "" : "no ") << "dump thread, " <<
      (has_requesting_thread   ? "" : "no ") << "requesting thread, and " <<
      (has_process_create_time ? "" : "no ") << "process create time";

  bool interrupted = false;
  bool found_requesting_thread = false;
  unsigned int thread_count = threads->thread_count();

  // Reset frame_symbolizer_ at the beginning of stackwalk for each minidump.
  frame_symbolizer_->Reset();

  for (unsigned int thread_index = 0;
       thread_index < thread_count;
       ++thread_index) {
    char thread_string_buffer[64];
    snprintf(thread_string_buffer, sizeof(thread_string_buffer), "%d/%d",
             thread_index, thread_count);
    string thread_string = dump->path() + ":" + thread_string_buffer;

    MinidumpThread *thread = threads->GetThreadAtIndex(thread_index);
    if (!thread) {
      BPLOG(ERROR) << "Could not get thread for " << thread_string;
      return PROCESS_ERROR_GETTING_THREAD;
    }

    uint32_t thread_id;
    if (!thread->GetThreadID(&thread_id)) {
      BPLOG(ERROR) << "Could not get thread ID for " << thread_string;
      return PROCESS_ERROR_GETTING_THREAD_ID;
    }

    thread_string += " id " + HexString(thread_id);
    BPLOG(INFO) << "Looking at thread " << thread_string;

    // If this thread is the thread that produced the minidump, don't process
    // it.  Because of the problems associated with a thread producing a
    // dump of itself (when both its context and its stack are in flux),
    // processing that stack wouldn't provide much useful data.
    if (has_dump_thread && thread_id == dump_thread_id) {
      continue;
    }

    MinidumpContext *context = thread->GetContext();

    if (has_requesting_thread && thread_id == requesting_thread_id) {
      if (found_requesting_thread) {
        // There can't be more than one requesting thread.
        BPLOG(ERROR) << "Duplicate requesting thread: " << thread_string;
        return PROCESS_ERROR_DUPLICATE_REQUESTING_THREADS;
      }

      // Use processed_state->threads_.size() instead of thread_index.
      // thread_index points to the thread index in the minidump, which
      // might be greater than the thread index in the threads vector if
      // any of the minidump's threads are skipped and not placed into the
      // processed threads vector.  The thread vector's current size will
      // be the index of the current thread when it's pushed into the
      // vector.
      process_state->requesting_thread_ = process_state->threads_.size();

      found_requesting_thread = true;

      if (process_state->crashed_) {
        // Use the exception record's context for the crashed thread, instead
        // of the thread's own context.  For the crashed thread, the thread's
        // own context is the state inside the exception handler.  Using it
        // would not result in the expected stack trace from the time of the
        // crash. If the exception context is invalid, however, we fall back
        // on the thread context.
        MinidumpContext *ctx = exception->GetContext();
        context = ctx ? ctx : thread->GetContext();
      }
    }

    // If the memory region for the stack cannot be read using the RVA stored
    // in the memory descriptor inside MINIDUMP_THREAD, try to locate and use
    // a memory region (containing the stack) from the minidump memory list.
    MinidumpMemoryRegion *thread_memory = thread->GetMemory();
    if (!thread_memory && memory_list) {
      uint64_t start_stack_memory_range = thread->GetStartOfStackMemoryRange();
      if (start_stack_memory_range) {
        thread_memory = memory_list->GetMemoryRegionForAddress(
           start_stack_memory_range);
      }
    }
    if (!thread_memory) {
      BPLOG(ERROR) << "No memory region for " << thread_string;
    }

    // Use process_state->modules_ instead of module_list, because the
    // |modules| argument will be used to populate the |module| fields in
    // the returned StackFrame objects, which will be placed into the
    // returned ProcessState object.  module_list's lifetime is only as
    // long as the Minidump object: it will be deleted when this function
    // returns.  process_state->modules_ is owned by the ProcessState object
    // (just like the StackFrame objects), and is much more suitable for this
    // task.
    scoped_ptr<Stackwalker> stackwalker(
        Stackwalker::StackwalkerForCPU(process_state->system_info(),
                                       context,
                                       thread_memory,
                                       process_state->modules_,
                                       frame_symbolizer_));

    scoped_ptr<CallStack> stack(new CallStack());
    if (stackwalker.get()) {
      if (!stackwalker->Walk(stack.get(),
                             &process_state->modules_without_symbols_,
                             &process_state->modules_with_corrupt_symbols_)) {
        BPLOG(INFO) << "Stackwalker interrupt (missing symbols?) at "
                    << thread_string;
        interrupted = true;
      }
    } else {
      // Threads with missing CPU contexts will hit this, but
      // don't abort processing the rest of the dump just for
      // one bad thread.
      BPLOG(ERROR) << "No stackwalker for " << thread_string;
    }
    process_state->threads_.push_back(stack.release());
    process_state->thread_memory_regions_.push_back(thread_memory);
  }

  if (interrupted) {
    BPLOG(INFO) << "Processing interrupted for " << dump->path();
    return PROCESS_SYMBOL_SUPPLIER_INTERRUPTED;
  }

  // If a requesting thread was indicated, it must be present.
  if (has_requesting_thread && !found_requesting_thread) {
    // Don't mark as an error, but invalidate the requesting thread
    BPLOG(ERROR) << "Minidump indicated requesting thread " <<
        HexString(requesting_thread_id) << ", not found in " <<
        dump->path();
    process_state->requesting_thread_ = -1;
  }

  // Exploitability defaults to EXPLOITABILITY_NOT_ANALYZED
  process_state->exploitability_ = EXPLOITABILITY_NOT_ANALYZED;

  // If an exploitability run was requested we perform the platform specific
  // rating.
  if (enable_exploitability_) {
    scoped_ptr<Exploitability> exploitability(
        Exploitability::ExploitabilityForPlatform(dump, process_state));
    // The engine will be null if the platform is not supported
    if (exploitability != NULL) {
      process_state->exploitability_ = exploitability->CheckExploitability();
    } else {
      process_state->exploitability_ = EXPLOITABILITY_ERR_NOENGINE;
    }
  }

  BPLOG(INFO) << "Processed " << dump->path();
  return PROCESS_OK;
}

ProcessResult MinidumpProcessor::Process(
    const string &minidump_file, ProcessState *process_state) {
  BPLOG(INFO) << "Processing minidump in file " << minidump_file;

  Minidump dump(minidump_file);
  if (!dump.Read()) {
     BPLOG(ERROR) << "Minidump " << dump.path() << " could not be read";
     return PROCESS_ERROR_MINIDUMP_NOT_FOUND;
  }

  return Process(&dump, process_state);
}

// Returns the MDRawSystemInfo from a minidump, or NULL if system info is
// not available from the minidump.  If system_info is non-NULL, it is used
// to pass back the MinidumpSystemInfo object.
static const MDRawSystemInfo* GetSystemInfo(Minidump *dump,
                                            MinidumpSystemInfo **system_info) {
  MinidumpSystemInfo *minidump_system_info = dump->GetSystemInfo();
  if (!minidump_system_info)
    return NULL;

  if (system_info)
    *system_info = minidump_system_info;

  return minidump_system_info->system_info();
}

// Extract CPU info string from ARM-specific MDRawSystemInfo structure.
// raw_info: pointer to source MDRawSystemInfo.
// cpu_info: address of target string, cpu info text will be appended to it.
static void GetARMCpuInfo(const MDRawSystemInfo* raw_info,
                          string* cpu_info) {
  assert(raw_info != NULL && cpu_info != NULL);

  // Write ARM architecture version.
  char cpu_string[32];
  snprintf(cpu_string, sizeof(cpu_string), "ARMv%d",
           raw_info->processor_level);
  cpu_info->append(cpu_string);

  // There is no good list of implementer id values, but the following
  // pages provide some help:
  //   http://comments.gmane.org/gmane.linux.linaro.devel/6903
  //   http://forum.xda-developers.com/archive/index.php/t-480226.html
  const struct {
    uint32_t id;
    const char* name;
  } vendors[] = {
    { 0x41, "ARM" },
    { 0x51, "Qualcomm" },
    { 0x56, "Marvell" },
    { 0x69, "Intel/Marvell" },
  };
  const struct {
    uint32_t id;
    const char* name;
  } parts[] = {
    { 0x4100c050, "Cortex-A5" },
    { 0x4100c080, "Cortex-A8" },
    { 0x4100c090, "Cortex-A9" },
    { 0x4100c0f0, "Cortex-A15" },
    { 0x4100c140, "Cortex-R4" },
    { 0x4100c150, "Cortex-R5" },
    { 0x4100b360, "ARM1136" },
    { 0x4100b560, "ARM1156" },
    { 0x4100b760, "ARM1176" },
    { 0x4100b020, "ARM11-MPCore" },
    { 0x41009260, "ARM926" },
    { 0x41009460, "ARM946" },
    { 0x41009660, "ARM966" },
    { 0x510006f0, "Krait" },
    { 0x510000f0, "Scorpion" },
  };

  const struct {
    uint32_t hwcap;
    const char* name;
  } features[] = {
    { MD_CPU_ARM_ELF_HWCAP_SWP, "swp" },
    { MD_CPU_ARM_ELF_HWCAP_HALF, "half" },
    { MD_CPU_ARM_ELF_HWCAP_THUMB, "thumb" },
    { MD_CPU_ARM_ELF_HWCAP_26BIT, "26bit" },
    { MD_CPU_ARM_ELF_HWCAP_FAST_MULT, "fastmult" },
    { MD_CPU_ARM_ELF_HWCAP_FPA, "fpa" },
    { MD_CPU_ARM_ELF_HWCAP_VFP, "vfpv2" },
    { MD_CPU_ARM_ELF_HWCAP_EDSP, "edsp" },
    { MD_CPU_ARM_ELF_HWCAP_JAVA, "java" },
    { MD_CPU_ARM_ELF_HWCAP_IWMMXT, "iwmmxt" },
    { MD_CPU_ARM_ELF_HWCAP_CRUNCH, "crunch" },
    { MD_CPU_ARM_ELF_HWCAP_THUMBEE, "thumbee" },
    { MD_CPU_ARM_ELF_HWCAP_NEON, "neon" },
    { MD_CPU_ARM_ELF_HWCAP_VFPv3, "vfpv3" },
    { MD_CPU_ARM_ELF_HWCAP_VFPv3D16, "vfpv3d16" },
    { MD_CPU_ARM_ELF_HWCAP_TLS, "tls" },
    { MD_CPU_ARM_ELF_HWCAP_VFPv4, "vfpv4" },
    { MD_CPU_ARM_ELF_HWCAP_IDIVA, "idiva" },
    { MD_CPU_ARM_ELF_HWCAP_IDIVT, "idivt" },
  };

  uint32_t cpuid = raw_info->cpu.arm_cpu_info.cpuid;
  if (cpuid != 0) {
    // Extract vendor name from CPUID
    const char* vendor = NULL;
    uint32_t vendor_id = (cpuid >> 24) & 0xff;
    for (size_t i = 0; i < sizeof(vendors)/sizeof(vendors[0]); ++i) {
      if (vendors[i].id == vendor_id) {
        vendor = vendors[i].name;
        break;
      }
    }
    cpu_info->append(" ");
    if (vendor) {
      cpu_info->append(vendor);
    } else {
      snprintf(cpu_string, sizeof(cpu_string), "vendor(0x%x)", vendor_id);
      cpu_info->append(cpu_string);
    }

    // Extract part name from CPUID
    uint32_t part_id = (cpuid & 0xff00fff0);
    const char* part = NULL;
    for (size_t i = 0; i < sizeof(parts)/sizeof(parts[0]); ++i) {
      if (parts[i].id == part_id) {
        part = parts[i].name;
        break;
      }
    }
    cpu_info->append(" ");
    if (part != NULL) {
      cpu_info->append(part);
    } else {
      snprintf(cpu_string, sizeof(cpu_string), "part(0x%x)", part_id);
      cpu_info->append(cpu_string);
    }
  }
  uint32_t elf_hwcaps = raw_info->cpu.arm_cpu_info.elf_hwcaps;
  if (elf_hwcaps != 0) {
    cpu_info->append(" features: ");
    const char* comma = "";
    for (size_t i = 0; i < sizeof(features)/sizeof(features[0]); ++i) {
      if (elf_hwcaps & features[i].hwcap) {
        cpu_info->append(comma);
        cpu_info->append(features[i].name);
        comma = ",";
      }
    }
  }
}

// static
bool MinidumpProcessor::GetCPUInfo(Minidump *dump, SystemInfo *info) {
  assert(dump);
  assert(info);

  info->cpu.clear();
  info->cpu_info.clear();

  MinidumpSystemInfo *system_info;
  const MDRawSystemInfo *raw_system_info = GetSystemInfo(dump, &system_info);
  if (!raw_system_info)
    return false;

  switch (raw_system_info->processor_architecture) {
    case MD_CPU_ARCHITECTURE_X86:
    case MD_CPU_ARCHITECTURE_AMD64: {
      if (raw_system_info->processor_architecture ==
          MD_CPU_ARCHITECTURE_X86)
        info->cpu = "x86";
      else
        info->cpu = "amd64";

      const string *cpu_vendor = system_info->GetCPUVendor();
      if (cpu_vendor) {
        info->cpu_info = *cpu_vendor;
        info->cpu_info.append(" ");
      }

      char x86_info[36];
      snprintf(x86_info, sizeof(x86_info), "family %u model %u stepping %u",
               raw_system_info->processor_level,
               raw_system_info->processor_revision >> 8,
               raw_system_info->processor_revision & 0xff);
      info->cpu_info.append(x86_info);
      break;
    }

    case MD_CPU_ARCHITECTURE_PPC: {
      info->cpu = "ppc";
      break;
    }

    case MD_CPU_ARCHITECTURE_PPC64: {
      info->cpu = "ppc64";
      break;
    }

    case MD_CPU_ARCHITECTURE_SPARC: {
      info->cpu = "sparc";
      break;
    }

    case MD_CPU_ARCHITECTURE_ARM: {
      info->cpu = "arm";
      GetARMCpuInfo(raw_system_info, &info->cpu_info);
      break;
    }

    case MD_CPU_ARCHITECTURE_ARM64: {
      info->cpu = "arm64";
      break;
    }

    case MD_CPU_ARCHITECTURE_MIPS: {
      info->cpu = "mips";
      break;
    }

    default: {
      // Assign the numeric architecture ID into the CPU string.
      char cpu_string[7];
      snprintf(cpu_string, sizeof(cpu_string), "0x%04x",
               raw_system_info->processor_architecture);
      info->cpu = cpu_string;
      break;
    }
  }

  info->cpu_count = raw_system_info->number_of_processors;

  return true;
}

// static
bool MinidumpProcessor::GetOSInfo(Minidump *dump, SystemInfo *info) {
  assert(dump);
  assert(info);

  info->os.clear();
  info->os_short.clear();
  info->os_version.clear();

  MinidumpSystemInfo *system_info;
  const MDRawSystemInfo *raw_system_info = GetSystemInfo(dump, &system_info);
  if (!raw_system_info)
    return false;

  info->os_short = system_info->GetOS();

  switch (raw_system_info->platform_id) {
    case MD_OS_WIN32_NT: {
      info->os = "Windows NT";
      break;
    }

    case MD_OS_WIN32_WINDOWS: {
      info->os = "Windows";
      break;
    }

    case MD_OS_MAC_OS_X: {
      info->os = "Mac OS X";
      break;
    }

    case MD_OS_IOS: {
      info->os = "iOS";
      break;
    }

    case MD_OS_LINUX: {
      info->os = "Linux";
      break;
    }

    case MD_OS_SOLARIS: {
      info->os = "Solaris";
      break;
    }

    case MD_OS_ANDROID: {
      info->os = "Android";
      break;
    }

    case MD_OS_PS3: {
      info->os = "PS3";
      break;
    }

    case MD_OS_NACL: {
      info->os = "NaCl";
      break;
    }

    default: {
      // Assign the numeric platform ID into the OS string.
      char os_string[11];
      snprintf(os_string, sizeof(os_string), "0x%08x",
               raw_system_info->platform_id);
      info->os = os_string;
      break;
    }
  }

  char os_version_string[33];
  snprintf(os_version_string, sizeof(os_version_string), "%u.%u.%u",
           raw_system_info->major_version,
           raw_system_info->minor_version,
           raw_system_info->build_number);
  info->os_version = os_version_string;

  const string *csd_version = system_info->GetCSDVersion();
  if (csd_version) {
    info->os_version.append(" ");
    info->os_version.append(*csd_version);
  }

  return true;
}

// static
bool MinidumpProcessor::GetProcessCreateTime(Minidump* dump,
                                             uint32_t* process_create_time) {
  assert(dump);
  assert(process_create_time);

  *process_create_time = 0;

  MinidumpMiscInfo* minidump_misc_info = dump->GetMiscInfo();
  if (!minidump_misc_info) {
    return false;
  }

  const MDRawMiscInfo* md_raw_misc_info = minidump_misc_info->misc_info();
  if (!md_raw_misc_info) {
    return false;
  }

  if (!(md_raw_misc_info->flags1 & MD_MISCINFO_FLAGS1_PROCESS_TIMES)) {
    return false;
  }

  *process_create_time = md_raw_misc_info->process_create_time;
  return true;
}

// static
string MinidumpProcessor::GetCrashReason(Minidump *dump, uint64_t *address) {
  MinidumpException *exception = dump->GetException();
  if (!exception)
    return "";

  const MDRawExceptionStream *raw_exception = exception->exception();
  if (!raw_exception)
    return "";

  if (address)
    *address = raw_exception->exception_record.exception_address;

  // The reason value is OS-specific and possibly CPU-specific.  Set up
  // sensible numeric defaults for the reason string in case we can't
  // map the codes to a string (because there's no system info, or because
  // it's an unrecognized platform, or because it's an unrecognized code.)
  char reason_string[24];
  uint32_t exception_code = raw_exception->exception_record.exception_code;
  uint32_t exception_flags = raw_exception->exception_record.exception_flags;
  snprintf(reason_string, sizeof(reason_string), "0x%08x / 0x%08x",
           exception_code, exception_flags);
  string reason = reason_string;

  const MDRawSystemInfo *raw_system_info = GetSystemInfo(dump, NULL);
  if (!raw_system_info)
    return reason;

  switch (raw_system_info->platform_id) {
    case MD_OS_MAC_OS_X:
    case MD_OS_IOS: {
      char flags_string[11];
      snprintf(flags_string, sizeof(flags_string), "0x%08x", exception_flags);
      switch (exception_code) {
        case MD_EXCEPTION_MAC_BAD_ACCESS:
          reason = "EXC_BAD_ACCESS / ";
          switch (exception_flags) {
            case MD_EXCEPTION_CODE_MAC_INVALID_ADDRESS:
              reason.append("KERN_INVALID_ADDRESS");
              break;
            case MD_EXCEPTION_CODE_MAC_PROTECTION_FAILURE:
              reason.append("KERN_PROTECTION_FAILURE");
              break;
            case MD_EXCEPTION_CODE_MAC_NO_ACCESS:
              reason.append("KERN_NO_ACCESS");
              break;
            case MD_EXCEPTION_CODE_MAC_MEMORY_FAILURE:
              reason.append("KERN_MEMORY_FAILURE");
              break;
            case MD_EXCEPTION_CODE_MAC_MEMORY_ERROR:
              reason.append("KERN_MEMORY_ERROR");
              break;
            default:
              // arm and ppc overlap
              if (raw_system_info->processor_architecture ==
                  MD_CPU_ARCHITECTURE_ARM ||
                  raw_system_info->processor_architecture ==
                  MD_CPU_ARCHITECTURE_ARM64) {
                switch (exception_flags) {
                  case MD_EXCEPTION_CODE_MAC_ARM_DA_ALIGN:
                    reason.append("EXC_ARM_DA_ALIGN");
                    break;
                  case MD_EXCEPTION_CODE_MAC_ARM_DA_DEBUG:
                    reason.append("EXC_ARM_DA_DEBUG");
                    break;
                  default:
                    reason.append(flags_string);
                    BPLOG(INFO) << "Unknown exception reason " << reason;
                    break;
                }
              } else if (raw_system_info->processor_architecture ==
                         MD_CPU_ARCHITECTURE_PPC) {
                switch (exception_flags) {
                  case MD_EXCEPTION_CODE_MAC_PPC_VM_PROT_READ:
                    reason.append("EXC_PPC_VM_PROT_READ");
                    break;
                  case MD_EXCEPTION_CODE_MAC_PPC_BADSPACE:
                    reason.append("EXC_PPC_BADSPACE");
                    break;
                  case MD_EXCEPTION_CODE_MAC_PPC_UNALIGNED:
                    reason.append("EXC_PPC_UNALIGNED");
                    break;
                  default:
                    reason.append(flags_string);
                    BPLOG(INFO) << "Unknown exception reason " << reason;
                    break;
                }
              } else {
                reason.append(flags_string);
                BPLOG(INFO) << "Unknown exception reason " << reason;
              }
              break;
          }
          break;
        case MD_EXCEPTION_MAC_BAD_INSTRUCTION:
          reason = "EXC_BAD_INSTRUCTION / ";
          switch (raw_system_info->processor_architecture) {
            case MD_CPU_ARCHITECTURE_ARM:
            case MD_CPU_ARCHITECTURE_ARM64: {
              switch (exception_flags) {
                case MD_EXCEPTION_CODE_MAC_ARM_UNDEFINED:
                  reason.append("EXC_ARM_UNDEFINED");
                  break;
                default:
                  reason.append(flags_string);
                  BPLOG(INFO) << "Unknown exception reason " << reason;
                  break;
              }
              break;
            }
            case MD_CPU_ARCHITECTURE_PPC: {
              switch (exception_flags) {
                case MD_EXCEPTION_CODE_MAC_PPC_INVALID_SYSCALL:
                  reason.append("EXC_PPC_INVALID_SYSCALL");
                  break;
                case MD_EXCEPTION_CODE_MAC_PPC_UNIMPLEMENTED_INSTRUCTION:
                  reason.append("EXC_PPC_UNIPL_INST");
                  break;
                case MD_EXCEPTION_CODE_MAC_PPC_PRIVILEGED_INSTRUCTION:
                  reason.append("EXC_PPC_PRIVINST");
                  break;
                case MD_EXCEPTION_CODE_MAC_PPC_PRIVILEGED_REGISTER:
                  reason.append("EXC_PPC_PRIVREG");
                  break;
                case MD_EXCEPTION_CODE_MAC_PPC_TRACE:
                  reason.append("EXC_PPC_TRACE");
                  break;
                case MD_EXCEPTION_CODE_MAC_PPC_PERFORMANCE_MONITOR:
                  reason.append("EXC_PPC_PERFMON");
                  break;
                default:
                  reason.append(flags_string);
                  BPLOG(INFO) << "Unknown exception reason " << reason;
                  break;
              }
              break;
            }
            case MD_CPU_ARCHITECTURE_X86: {
              switch (exception_flags) {
                case MD_EXCEPTION_CODE_MAC_X86_INVALID_OPERATION:
                  reason.append("EXC_I386_INVOP");
                  break;
                case MD_EXCEPTION_CODE_MAC_X86_INVALID_TASK_STATE_SEGMENT:
                  reason.append("EXC_INVTSSFLT");
                  break;
                case MD_EXCEPTION_CODE_MAC_X86_SEGMENT_NOT_PRESENT:
                  reason.append("EXC_SEGNPFLT");
                  break;
                case MD_EXCEPTION_CODE_MAC_X86_STACK_FAULT:
                  reason.append("EXC_STKFLT");
                  break;
                case MD_EXCEPTION_CODE_MAC_X86_GENERAL_PROTECTION_FAULT:
                  reason.append("EXC_GPFLT");
                  break;
                case MD_EXCEPTION_CODE_MAC_X86_ALIGNMENT_FAULT:
                  reason.append("EXC_ALIGNFLT");
                  break;
                default:
                  reason.append(flags_string);
                  BPLOG(INFO) << "Unknown exception reason " << reason;
                  break;
              }
              break;
            }
            default:
              reason.append(flags_string);
              BPLOG(INFO) << "Unknown exception reason " << reason;
              break;
          }
          break;
        case MD_EXCEPTION_MAC_ARITHMETIC:
          reason = "EXC_ARITHMETIC / ";
          switch (raw_system_info->processor_architecture) {
            case MD_CPU_ARCHITECTURE_PPC: {
              switch (exception_flags) {
                case MD_EXCEPTION_CODE_MAC_PPC_OVERFLOW:
                  reason.append("EXC_PPC_OVERFLOW");
                  break;
                case MD_EXCEPTION_CODE_MAC_PPC_ZERO_DIVIDE:
                  reason.append("EXC_PPC_ZERO_DIVIDE");
                  break;
                case MD_EXCEPTION_CODE_MAC_PPC_FLOAT_INEXACT:
                  reason.append("EXC_FLT_INEXACT");
                  break;
                case MD_EXCEPTION_CODE_MAC_PPC_FLOAT_ZERO_DIVIDE:
                  reason.append("EXC_PPC_FLT_ZERO_DIVIDE");
                  break;
                case MD_EXCEPTION_CODE_MAC_PPC_FLOAT_UNDERFLOW:
                  reason.append("EXC_PPC_FLT_UNDERFLOW");
                  break;
                case MD_EXCEPTION_CODE_MAC_PPC_FLOAT_OVERFLOW:
                  reason.append("EXC_PPC_FLT_OVERFLOW");
                  break;
                case MD_EXCEPTION_CODE_MAC_PPC_FLOAT_NOT_A_NUMBER:
                  reason.append("EXC_PPC_FLT_NOT_A_NUMBER");
                  break;
                case MD_EXCEPTION_CODE_MAC_PPC_NO_EMULATION:
                  reason.append("EXC_PPC_NOEMULATION");
                  break;
                case MD_EXCEPTION_CODE_MAC_PPC_ALTIVEC_ASSIST:
                  reason.append("EXC_PPC_ALTIVECASSIST");
                default:
                  reason.append(flags_string);
                  BPLOG(INFO) << "Unknown exception reason " << reason;
                  break;
              }
              break;
            }
            case MD_CPU_ARCHITECTURE_X86: {
              switch (exception_flags) {
                case MD_EXCEPTION_CODE_MAC_X86_DIV:
                  reason.append("EXC_I386_DIV");
                  break;
                case MD_EXCEPTION_CODE_MAC_X86_INTO:
                  reason.append("EXC_I386_INTO");
                  break;
                case MD_EXCEPTION_CODE_MAC_X86_NOEXT:
                  reason.append("EXC_I386_NOEXT");
                  break;
                case MD_EXCEPTION_CODE_MAC_X86_EXTOVR:
                  reason.append("EXC_I386_EXTOVR");
                  break;
                case MD_EXCEPTION_CODE_MAC_X86_EXTERR:
                  reason.append("EXC_I386_EXTERR");
                  break;
                case MD_EXCEPTION_CODE_MAC_X86_EMERR:
                  reason.append("EXC_I386_EMERR");
                  break;
                case MD_EXCEPTION_CODE_MAC_X86_BOUND:
                  reason.append("EXC_I386_BOUND");
                  break;
                case MD_EXCEPTION_CODE_MAC_X86_SSEEXTERR:
                  reason.append("EXC_I386_SSEEXTERR");
                  break;
                default:
                  reason.append(flags_string);
                  BPLOG(INFO) << "Unknown exception reason " << reason;
                  break;
              }
              break;
            }
            default:
              reason.append(flags_string);
              BPLOG(INFO) << "Unknown exception reason " << reason;
              break;
          }
          break;
        case MD_EXCEPTION_MAC_EMULATION:
          reason = "EXC_EMULATION / ";
          reason.append(flags_string);
          break;
        case MD_EXCEPTION_MAC_SOFTWARE:
          reason = "EXC_SOFTWARE / ";
          switch (exception_flags) {
            case MD_EXCEPTION_CODE_MAC_ABORT:
              reason.append("SIGABRT");
              break;
            case MD_EXCEPTION_CODE_MAC_NS_EXCEPTION:
              reason.append("UNCAUGHT_NS_EXCEPTION");
              break;
            // These are ppc only but shouldn't be a problem as they're
            // unused on x86
            case MD_EXCEPTION_CODE_MAC_PPC_TRAP:
              reason.append("EXC_PPC_TRAP");
              break;
            case MD_EXCEPTION_CODE_MAC_PPC_MIGRATE:
              reason.append("EXC_PPC_MIGRATE");
              break;
            default:
              reason.append(flags_string);
              BPLOG(INFO) << "Unknown exception reason " << reason;
              break;
          }
          break;
        case MD_EXCEPTION_MAC_BREAKPOINT:
          reason = "EXC_BREAKPOINT / ";
          switch (raw_system_info->processor_architecture) {
            case MD_CPU_ARCHITECTURE_ARM:
            case MD_CPU_ARCHITECTURE_ARM64: {
              switch (exception_flags) {
                case MD_EXCEPTION_CODE_MAC_ARM_DA_ALIGN:
                  reason.append("EXC_ARM_DA_ALIGN");
                  break;
                case MD_EXCEPTION_CODE_MAC_ARM_DA_DEBUG:
                  reason.append("EXC_ARM_DA_DEBUG");
                  break;
                case MD_EXCEPTION_CODE_MAC_ARM_BREAKPOINT:
                  reason.append("EXC_ARM_BREAKPOINT");
                  break;
                default:
                  reason.append(flags_string);
                  BPLOG(INFO) << "Unknown exception reason " << reason;
                  break;
              }
              break;
            }
            case MD_CPU_ARCHITECTURE_PPC: {
              switch (exception_flags) {
                case MD_EXCEPTION_CODE_MAC_PPC_BREAKPOINT:
                  reason.append("EXC_PPC_BREAKPOINT");
                  break;
                default:
                  reason.append(flags_string);
                  BPLOG(INFO) << "Unknown exception reason " << reason;
                  break;
              }
              break;
            }
            case MD_CPU_ARCHITECTURE_X86: {
              switch (exception_flags) {
                case MD_EXCEPTION_CODE_MAC_X86_SGL:
                  reason.append("EXC_I386_SGL");
                  break;
                case MD_EXCEPTION_CODE_MAC_X86_BPT:
                  reason.append("EXC_I386_BPT");
                  break;
                default:
                  reason.append(flags_string);
                  BPLOG(INFO) << "Unknown exception reason " << reason;
                  break;
              }
              break;
            }
            default:
              reason.append(flags_string);
              BPLOG(INFO) << "Unknown exception reason " << reason;
              break;
          }
          break;
        case MD_EXCEPTION_MAC_SYSCALL:
          reason = "EXC_SYSCALL / ";
          reason.append(flags_string);
          break;
        case MD_EXCEPTION_MAC_MACH_SYSCALL:
          reason = "EXC_MACH_SYSCALL / ";
          reason.append(flags_string);
          break;
        case MD_EXCEPTION_MAC_RPC_ALERT:
          reason = "EXC_RPC_ALERT / ";
          reason.append(flags_string);
          break;
      }
      break;
    }

    case MD_OS_WIN32_NT:
    case MD_OS_WIN32_WINDOWS: {
      switch (exception_code) {
        case MD_EXCEPTION_CODE_WIN_CONTROL_C:
          reason = "DBG_CONTROL_C";
          break;
        case MD_EXCEPTION_CODE_WIN_GUARD_PAGE_VIOLATION:
          reason = "EXCEPTION_GUARD_PAGE";
          break;
        case MD_EXCEPTION_CODE_WIN_DATATYPE_MISALIGNMENT:
          reason = "EXCEPTION_DATATYPE_MISALIGNMENT";
          break;
        case MD_EXCEPTION_CODE_WIN_BREAKPOINT:
          reason = "EXCEPTION_BREAKPOINT";
          break;
        case MD_EXCEPTION_CODE_WIN_SINGLE_STEP:
          reason = "EXCEPTION_SINGLE_STEP";
          break;
        case MD_EXCEPTION_CODE_WIN_ACCESS_VIOLATION:
          // For EXCEPTION_ACCESS_VIOLATION, Windows puts the address that
          // caused the fault in exception_information[1].
          // exception_information[0] is 0 if the violation was caused by
          // an attempt to read data, 1 if it was an attempt to write data,
          // and 8 if this was a data execution violation.
          // This information is useful in addition to the code address, which
          // will be present in the crash thread's instruction field anyway.
          if (raw_exception->exception_record.number_parameters >= 1) {
            MDAccessViolationTypeWin av_type =
                static_cast<MDAccessViolationTypeWin>
                (raw_exception->exception_record.exception_information[0]);
            switch (av_type) {
              case MD_ACCESS_VIOLATION_WIN_READ:
                reason = "EXCEPTION_ACCESS_VIOLATION_READ";
                break;
              case MD_ACCESS_VIOLATION_WIN_WRITE:
                reason = "EXCEPTION_ACCESS_VIOLATION_WRITE";
                break;
              case MD_ACCESS_VIOLATION_WIN_EXEC:
                reason = "EXCEPTION_ACCESS_VIOLATION_EXEC";
                break;
              default:
                reason = "EXCEPTION_ACCESS_VIOLATION";
                break;
            }
          } else {
            reason = "EXCEPTION_ACCESS_VIOLATION";
          }
          if (address &&
              raw_exception->exception_record.number_parameters >= 2) {
            *address =
                raw_exception->exception_record.exception_information[1];
          }
          break;
        case MD_EXCEPTION_CODE_WIN_IN_PAGE_ERROR:
          // For EXCEPTION_IN_PAGE_ERROR, Windows puts the address that
          // caused the fault in exception_information[1].
          // exception_information[0] is 0 if the violation was caused by
          // an attempt to read data, 1 if it was an attempt to write data,
          // and 8 if this was a data execution violation.
          // exception_information[2] contains the underlying NTSTATUS code,
          // which is the explanation for why this error occured.
          // This information is useful in addition to the code address, which
          // will be present in the crash thread's instruction field anyway.
          if (raw_exception->exception_record.number_parameters >= 1) {
            MDInPageErrorTypeWin av_type =
                static_cast<MDInPageErrorTypeWin>
                (raw_exception->exception_record.exception_information[0]);
            switch (av_type) {
              case MD_IN_PAGE_ERROR_WIN_READ:
                reason = "EXCEPTION_IN_PAGE_ERROR_READ";
                break;
              case MD_IN_PAGE_ERROR_WIN_WRITE:
                reason = "EXCEPTION_IN_PAGE_ERROR_WRITE";
                break;
              case MD_IN_PAGE_ERROR_WIN_EXEC:
                reason = "EXCEPTION_IN_PAGE_ERROR_EXEC";
                break;
              default:
                reason = "EXCEPTION_IN_PAGE_ERROR";
                break;
            }
          } else {
            reason = "EXCEPTION_IN_PAGE_ERROR";
          }
          if (address &&
              raw_exception->exception_record.number_parameters >= 2) {
            *address =
                raw_exception->exception_record.exception_information[1];
          }
          if (raw_exception->exception_record.number_parameters >= 3) {
            uint32_t ntstatus =
                static_cast<uint32_t>
                (raw_exception->exception_record.exception_information[2]);
            reason.append(" / ");
            reason.append(NTStatusToString(ntstatus));
          }
          break;
        case MD_EXCEPTION_CODE_WIN_INVALID_HANDLE:
          reason = "EXCEPTION_INVALID_HANDLE";
          break;
        case MD_EXCEPTION_CODE_WIN_ILLEGAL_INSTRUCTION:
          reason = "EXCEPTION_ILLEGAL_INSTRUCTION";
          break;
        case MD_EXCEPTION_CODE_WIN_NONCONTINUABLE_EXCEPTION:
          reason = "EXCEPTION_NONCONTINUABLE_EXCEPTION";
          break;
        case MD_EXCEPTION_CODE_WIN_INVALID_DISPOSITION:
          reason = "EXCEPTION_INVALID_DISPOSITION";
          break;
        case MD_EXCEPTION_CODE_WIN_ARRAY_BOUNDS_EXCEEDED:
          reason = "EXCEPTION_BOUNDS_EXCEEDED";
          break;
        case MD_EXCEPTION_CODE_WIN_FLOAT_DENORMAL_OPERAND:
          reason = "EXCEPTION_FLT_DENORMAL_OPERAND";
          break;
        case MD_EXCEPTION_CODE_WIN_FLOAT_DIVIDE_BY_ZERO:
          reason = "EXCEPTION_FLT_DIVIDE_BY_ZERO";
          break;
        case MD_EXCEPTION_CODE_WIN_FLOAT_INEXACT_RESULT:
          reason = "EXCEPTION_FLT_INEXACT_RESULT";
          break;
        case MD_EXCEPTION_CODE_WIN_FLOAT_INVALID_OPERATION:
          reason = "EXCEPTION_FLT_INVALID_OPERATION";
          break;
        case MD_EXCEPTION_CODE_WIN_FLOAT_OVERFLOW:
          reason = "EXCEPTION_FLT_OVERFLOW";
          break;
        case MD_EXCEPTION_CODE_WIN_FLOAT_STACK_CHECK:
          reason = "EXCEPTION_FLT_STACK_CHECK";
          break;
        case MD_EXCEPTION_CODE_WIN_FLOAT_UNDERFLOW:
          reason = "EXCEPTION_FLT_UNDERFLOW";
          break;
        case MD_EXCEPTION_CODE_WIN_INTEGER_DIVIDE_BY_ZERO:
          reason = "EXCEPTION_INT_DIVIDE_BY_ZERO";
          break;
        case MD_EXCEPTION_CODE_WIN_INTEGER_OVERFLOW:
          reason = "EXCEPTION_INT_OVERFLOW";
          break;
        case MD_EXCEPTION_CODE_WIN_PRIVILEGED_INSTRUCTION:
          reason = "EXCEPTION_PRIV_INSTRUCTION";
          break;
        case MD_EXCEPTION_CODE_WIN_STACK_OVERFLOW:
          reason = "EXCEPTION_STACK_OVERFLOW";
          break;
        case MD_EXCEPTION_CODE_WIN_POSSIBLE_DEADLOCK:
          reason = "EXCEPTION_POSSIBLE_DEADLOCK";
          break;
        case MD_EXCEPTION_CODE_WIN_STACK_BUFFER_OVERRUN:
          reason = "EXCEPTION_STACK_BUFFER_OVERRUN";
          break;
        case MD_EXCEPTION_CODE_WIN_HEAP_CORRUPTION:
          reason = "EXCEPTION_HEAP_CORRUPTION";
          break;
        case MD_EXCEPTION_CODE_WIN_UNHANDLED_CPP_EXCEPTION:
          reason = "Unhandled C++ Exception";
          break;
        default:
          BPLOG(INFO) << "Unknown exception reason " << reason;
          break;
      }
      break;
    }

    case MD_OS_ANDROID:
    case MD_OS_LINUX: {
      switch (exception_code) {
        case MD_EXCEPTION_CODE_LIN_SIGHUP:
          reason = "SIGHUP";
          break;
        case MD_EXCEPTION_CODE_LIN_SIGINT:
          reason = "SIGINT";
          break;
        case MD_EXCEPTION_CODE_LIN_SIGQUIT:
          reason = "SIGQUIT";
          break;
        case MD_EXCEPTION_CODE_LIN_SIGILL:
          reason = "SIGILL";
          break;
        case MD_EXCEPTION_CODE_LIN_SIGTRAP:
          reason = "SIGTRAP";
          break;
        case MD_EXCEPTION_CODE_LIN_SIGABRT:
          reason = "SIGABRT";
          break;
        case MD_EXCEPTION_CODE_LIN_SIGBUS:
          reason = "SIGBUS";
          break;
        case MD_EXCEPTION_CODE_LIN_SIGFPE:
          reason = "SIGFPE";
          break;
        case MD_EXCEPTION_CODE_LIN_SIGKILL:
          reason = "SIGKILL";
          break;
        case MD_EXCEPTION_CODE_LIN_SIGUSR1:
          reason = "SIGUSR1";
          break;
        case MD_EXCEPTION_CODE_LIN_SIGSEGV:
          reason = "SIGSEGV";
          break;
        case MD_EXCEPTION_CODE_LIN_SIGUSR2:
          reason = "SIGUSR2";
          break;
        case MD_EXCEPTION_CODE_LIN_SIGPIPE:
          reason = "SIGPIPE";
          break;
        case MD_EXCEPTION_CODE_LIN_SIGALRM:
          reason = "SIGALRM";
          break;
        case MD_EXCEPTION_CODE_LIN_SIGTERM:
          reason = "SIGTERM";
          break;
        case MD_EXCEPTION_CODE_LIN_SIGSTKFLT:
          reason = "SIGSTKFLT";
          break;
        case MD_EXCEPTION_CODE_LIN_SIGCHLD:
          reason = "SIGCHLD";
          break;
        case MD_EXCEPTION_CODE_LIN_SIGCONT:
          reason = "SIGCONT";
          break;
        case MD_EXCEPTION_CODE_LIN_SIGSTOP:
          reason = "SIGSTOP";
          break;
        case MD_EXCEPTION_CODE_LIN_SIGTSTP:
          reason = "SIGTSTP";
          break;
        case MD_EXCEPTION_CODE_LIN_SIGTTIN:
          reason = "SIGTTIN";
          break;
        case MD_EXCEPTION_CODE_LIN_SIGTTOU:
          reason = "SIGTTOU";
          break;
        case MD_EXCEPTION_CODE_LIN_SIGURG:
          reason = "SIGURG";
          break;
        case MD_EXCEPTION_CODE_LIN_SIGXCPU:
          reason = "SIGXCPU";
          break;
        case MD_EXCEPTION_CODE_LIN_SIGXFSZ:
          reason = "SIGXFSZ";
          break;
        case MD_EXCEPTION_CODE_LIN_SIGVTALRM:
          reason = "SIGVTALRM";
          break;
        case MD_EXCEPTION_CODE_LIN_SIGPROF:
          reason = "SIGPROF";
          break;
        case MD_EXCEPTION_CODE_LIN_SIGWINCH:
          reason = "SIGWINCH";
          break;
        case MD_EXCEPTION_CODE_LIN_SIGIO:
          reason = "SIGIO";
          break;
        case MD_EXCEPTION_CODE_LIN_SIGPWR:
          reason = "SIGPWR";
          break;
        case MD_EXCEPTION_CODE_LIN_SIGSYS:
          reason = "SIGSYS";
          break;
      case MD_EXCEPTION_CODE_LIN_DUMP_REQUESTED:
          reason = "DUMP_REQUESTED";
          break;
        default:
          BPLOG(INFO) << "Unknown exception reason " << reason;
          break;
      }
      break;
    }

    case MD_OS_SOLARIS: {
      switch (exception_code) {
        case MD_EXCEPTION_CODE_SOL_SIGHUP:
          reason = "SIGHUP";
          break;
        case MD_EXCEPTION_CODE_SOL_SIGINT:
          reason = "SIGINT";
          break;
        case MD_EXCEPTION_CODE_SOL_SIGQUIT:
          reason = "SIGQUIT";
          break;
        case MD_EXCEPTION_CODE_SOL_SIGILL:
          reason = "SIGILL";
          break;
        case MD_EXCEPTION_CODE_SOL_SIGTRAP:
          reason = "SIGTRAP";
          break;
        case MD_EXCEPTION_CODE_SOL_SIGIOT:
          reason = "SIGIOT | SIGABRT";
          break;
        case MD_EXCEPTION_CODE_SOL_SIGEMT:
          reason = "SIGEMT";
          break;
        case MD_EXCEPTION_CODE_SOL_SIGFPE:
          reason = "SIGFPE";
          break;
        case MD_EXCEPTION_CODE_SOL_SIGKILL:
          reason = "SIGKILL";
          break;
        case MD_EXCEPTION_CODE_SOL_SIGBUS:
          reason = "SIGBUS";
          break;
        case MD_EXCEPTION_CODE_SOL_SIGSEGV:
          reason = "SIGSEGV";
          break;
        case MD_EXCEPTION_CODE_SOL_SIGSYS:
          reason = "SIGSYS";
          break;
        case MD_EXCEPTION_CODE_SOL_SIGPIPE:
          reason = "SIGPIPE";
          break;
        case MD_EXCEPTION_CODE_SOL_SIGALRM:
          reason = "SIGALRM";
          break;
        case MD_EXCEPTION_CODE_SOL_SIGTERM:
          reason = "SIGTERM";
          break;
        case MD_EXCEPTION_CODE_SOL_SIGUSR1:
          reason = "SIGUSR1";
          break;
        case MD_EXCEPTION_CODE_SOL_SIGUSR2:
          reason = "SIGUSR2";
          break;
        case MD_EXCEPTION_CODE_SOL_SIGCLD:
          reason = "SIGCLD | SIGCHLD";
          break;
        case MD_EXCEPTION_CODE_SOL_SIGPWR:
          reason = "SIGPWR";
          break;
        case MD_EXCEPTION_CODE_SOL_SIGWINCH:
          reason = "SIGWINCH";
          break;
        case MD_EXCEPTION_CODE_SOL_SIGURG:
          reason = "SIGURG";
          break;
        case MD_EXCEPTION_CODE_SOL_SIGPOLL:
          reason = "SIGPOLL | SIGIO";
          break;
        case MD_EXCEPTION_CODE_SOL_SIGSTOP:
          reason = "SIGSTOP";
          break;
        case MD_EXCEPTION_CODE_SOL_SIGTSTP:
          reason = "SIGTSTP";
          break;
        case MD_EXCEPTION_CODE_SOL_SIGCONT:
          reason = "SIGCONT";
          break;
        case MD_EXCEPTION_CODE_SOL_SIGTTIN:
          reason = "SIGTTIN";
          break;
        case MD_EXCEPTION_CODE_SOL_SIGTTOU:
          reason = "SIGTTOU";
          break;
        case MD_EXCEPTION_CODE_SOL_SIGVTALRM:
          reason = "SIGVTALRM";
          break;
        case MD_EXCEPTION_CODE_SOL_SIGPROF:
          reason = "SIGPROF";
          break;
        case MD_EXCEPTION_CODE_SOL_SIGXCPU:
          reason = "SIGXCPU";
          break;
        case MD_EXCEPTION_CODE_SOL_SIGXFSZ:
          reason = "SIGXFSZ";
          break;
        case MD_EXCEPTION_CODE_SOL_SIGWAITING:
          reason = "SIGWAITING";
          break;
        case MD_EXCEPTION_CODE_SOL_SIGLWP:
          reason = "SIGLWP";
          break;
        case MD_EXCEPTION_CODE_SOL_SIGFREEZE:
          reason = "SIGFREEZE";
          break;
        case MD_EXCEPTION_CODE_SOL_SIGTHAW:
          reason = "SIGTHAW";
          break;
        case MD_EXCEPTION_CODE_SOL_SIGCANCEL:
          reason = "SIGCANCEL";
          break;
        case MD_EXCEPTION_CODE_SOL_SIGLOST:
          reason = "SIGLOST";
          break;
        case MD_EXCEPTION_CODE_SOL_SIGXRES:
          reason = "SIGXRES";
          break;
        case MD_EXCEPTION_CODE_SOL_SIGJVM1:
          reason = "SIGJVM1";
          break;
        case MD_EXCEPTION_CODE_SOL_SIGJVM2:
          reason = "SIGJVM2";
          break;
        default:
          BPLOG(INFO) << "Unknown exception reason " << reason;
          break;
      }
      break;
    }

    case MD_OS_PS3: {
      switch (exception_code) {
        case MD_EXCEPTION_CODE_PS3_UNKNOWN:
          reason = "UNKNOWN";
          break;
        case MD_EXCEPTION_CODE_PS3_TRAP_EXCEP:
          reason = "TRAP_EXCEP";
          break;
        case MD_EXCEPTION_CODE_PS3_PRIV_INSTR:
          reason = "PRIV_INSTR";
          break;
        case MD_EXCEPTION_CODE_PS3_ILLEGAL_INSTR:
          reason = "ILLEGAL_INSTR";
          break;
        case MD_EXCEPTION_CODE_PS3_INSTR_STORAGE:
          reason = "INSTR_STORAGE";
          break;
        case MD_EXCEPTION_CODE_PS3_INSTR_SEGMENT:
          reason = "INSTR_SEGMENT";
          break;
        case MD_EXCEPTION_CODE_PS3_DATA_STORAGE:
          reason = "DATA_STORAGE";
          break;
        case MD_EXCEPTION_CODE_PS3_DATA_SEGMENT:
          reason = "DATA_SEGMENT";
          break;
        case MD_EXCEPTION_CODE_PS3_FLOAT_POINT:
          reason = "FLOAT_POINT";
          break;
        case MD_EXCEPTION_CODE_PS3_DABR_MATCH:
          reason = "DABR_MATCH";
          break;
        case MD_EXCEPTION_CODE_PS3_ALIGN_EXCEP:
          reason = "ALIGN_EXCEP";
          break;
        case MD_EXCEPTION_CODE_PS3_MEMORY_ACCESS:
          reason = "MEMORY_ACCESS";
          break;
        case MD_EXCEPTION_CODE_PS3_COPRO_ALIGN:
          reason = "COPRO_ALIGN";
          break;
        case MD_EXCEPTION_CODE_PS3_COPRO_INVALID_COM:
          reason = "COPRO_INVALID_COM";
          break;
        case MD_EXCEPTION_CODE_PS3_COPRO_ERR:
          reason = "COPRO_ERR";
          break;
        case MD_EXCEPTION_CODE_PS3_COPRO_FIR:
          reason = "COPRO_FIR";
          break;
        case MD_EXCEPTION_CODE_PS3_COPRO_DATA_SEGMENT:
          reason = "COPRO_DATA_SEGMENT";
          break;
        case MD_EXCEPTION_CODE_PS3_COPRO_DATA_STORAGE:
          reason = "COPRO_DATA_STORAGE";
          break;
        case MD_EXCEPTION_CODE_PS3_COPRO_STOP_INSTR:
          reason = "COPRO_STOP_INSTR";
          break;
        case MD_EXCEPTION_CODE_PS3_COPRO_HALT_INSTR:
          reason = "COPRO_HALT_INSTR";
          break;
        case MD_EXCEPTION_CODE_PS3_COPRO_HALTINST_UNKNOWN:
          reason = "COPRO_HALTINSTR_UNKNOWN";
          break;
        case MD_EXCEPTION_CODE_PS3_COPRO_MEMORY_ACCESS:
          reason = "COPRO_MEMORY_ACCESS";
          break;
        case MD_EXCEPTION_CODE_PS3_GRAPHIC:
          reason = "GRAPHIC";
          break;
        default:
          BPLOG(INFO) << "Unknown exception reason "<< reason;
          break;
      }
      break;
    }

    default: {
      BPLOG(INFO) << "Unknown exception reason " << reason;
      break;
    }
  }

  return reason;
}

// static
string MinidumpProcessor::GetAssertion(Minidump *dump) {
  MinidumpAssertion *assertion = dump->GetAssertion();
  if (!assertion)
    return "";

  const MDRawAssertionInfo *raw_assertion = assertion->assertion();
  if (!raw_assertion)
    return "";

  string assertion_string;
  switch (raw_assertion->type) {
  case MD_ASSERTION_INFO_TYPE_INVALID_PARAMETER:
    assertion_string = "Invalid parameter passed to library function";
    break;
  case MD_ASSERTION_INFO_TYPE_PURE_VIRTUAL_CALL:
    assertion_string = "Pure virtual function called";
    break;
  default: {
    char assertion_type[32];
    snprintf(assertion_type, sizeof(assertion_type),
             "0x%08x", raw_assertion->type);
    assertion_string = "Unknown assertion type ";
    assertion_string += assertion_type;
    break;
  }
  }

  string expression = assertion->expression();
  if (!expression.empty()) {
    assertion_string.append(" " + expression);
  }

  string function = assertion->function();
  if (!function.empty()) {
    assertion_string.append(" in function " + function);
  }

  string file = assertion->file();
  if (!file.empty()) {
    assertion_string.append(", in file " + file);
  }

  if (raw_assertion->line != 0) {
    char assertion_line[32];
    snprintf(assertion_line, sizeof(assertion_line), "%u", raw_assertion->line);
    assertion_string.append(" at line ");
    assertion_string.append(assertion_line);
  }

  return assertion_string;
}

}  // namespace google_breakpad
