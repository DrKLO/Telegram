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

// Original author: Jim Blandy <jimb@mozilla.com> <jimb@red-bean.com>

// synth_minidump.cc: Implementation of SynthMinidump.  See synth_minidump.h

#include "processor/synth_minidump.h"

namespace google_breakpad {

namespace SynthMinidump {

Section::Section(const Dump &dump)
  : test_assembler::Section(dump.endianness()) { }

void Section::CiteLocationIn(test_assembler::Section *section) const {
  if (this)
    (*section).D32(size_).D32(file_offset_);
  else
    (*section).D32(0).D32(0);
}

void Stream::CiteStreamIn(test_assembler::Section *section) const {
  section->D32(type_);
  CiteLocationIn(section);
}

SystemInfo::SystemInfo(const Dump &dump,
                       const MDRawSystemInfo &system_info,
                       const String &csd_version)
    : Stream(dump, MD_SYSTEM_INFO_STREAM) {
  D16(system_info.processor_architecture);
  D16(system_info.processor_level);
  D16(system_info.processor_revision);
  D8(system_info.number_of_processors);
  D8(system_info.product_type);
  D32(system_info.major_version);
  D32(system_info.minor_version);
  D32(system_info.build_number);
  D32(system_info.platform_id);
  csd_version.CiteStringIn(this);
  D16(system_info.suite_mask);
  D16(system_info.reserved2);           // Well, why not?

  // MDCPUInformation cpu;
  if (system_info.processor_architecture == MD_CPU_ARCHITECTURE_X86) {
    D32(system_info.cpu.x86_cpu_info.vendor_id[0]);
    D32(system_info.cpu.x86_cpu_info.vendor_id[1]);
    D32(system_info.cpu.x86_cpu_info.vendor_id[2]);
    D32(system_info.cpu.x86_cpu_info.version_information);
    D32(system_info.cpu.x86_cpu_info.feature_information);
    D32(system_info.cpu.x86_cpu_info.amd_extended_cpu_features);
  } else if (system_info.processor_architecture == MD_CPU_ARCHITECTURE_ARM) {
    D32(system_info.cpu.arm_cpu_info.cpuid);
    D32(system_info.cpu.arm_cpu_info.elf_hwcaps);
  } else {
    D64(system_info.cpu.other_cpu_info.processor_features[0]);
    D64(system_info.cpu.other_cpu_info.processor_features[1]);
  }
}

const MDRawSystemInfo SystemInfo::windows_x86 = {
  MD_CPU_ARCHITECTURE_X86,              // processor_architecture
  6,                                    // processor_level
  0xd08,                                // processor_revision
  1,                                    // number_of_processors
  1,                                    // product_type
  5,                                    // major_version
  1,                                    // minor_version
  2600,                                 // build_number
  2,                                    // platform_id
  0xdeadbeef,                           // csd_version_rva
  0x100,                                // suite_mask
  0,                                    // reserved2
  {                                     // cpu
    { // x86_cpu_info
      { 0x756e6547, 0x49656e69, 0x6c65746e }, // vendor_id
      0x6d8,                                  // version_information
      0xafe9fbff,                             // feature_information
      0xffffffff                              // amd_extended_cpu_features
    }
  }
};

const string SystemInfo::windows_x86_csd_version = "Service Pack 2";

String::String(const Dump &dump, const string &contents) : Section(dump) {
  D32(contents.size() * 2);
  for (string::const_iterator i = contents.begin(); i != contents.end(); i++)
    D16(*i);
}

void String::CiteStringIn(test_assembler::Section *section) const {
  section->D32(file_offset_);
}

void Memory::CiteMemoryIn(test_assembler::Section *section) const {
  section->D64(address_);
  CiteLocationIn(section);
}

Context::Context(const Dump &dump, const MDRawContextX86 &context)
  : Section(dump) {
  // The caller should have properly set the CPU type flag.
  // The high 24 bits identify the CPU.  Note that context records with no CPU
  // type information can be valid (e.g. produced by ::RtlCaptureContext).
  assert(((context.context_flags & MD_CONTEXT_CPU_MASK) == 0) ||
         (context.context_flags & MD_CONTEXT_X86));
  // It doesn't make sense to store x86 registers in big-endian form.
  assert(dump.endianness() == kLittleEndian);
  D32(context.context_flags);
  D32(context.dr0);
  D32(context.dr1);
  D32(context.dr2);
  D32(context.dr3);
  D32(context.dr6);
  D32(context.dr7);
  D32(context.float_save.control_word);
  D32(context.float_save.status_word);
  D32(context.float_save.tag_word);
  D32(context.float_save.error_offset);
  D32(context.float_save.error_selector);
  D32(context.float_save.data_offset);
  D32(context.float_save.data_selector);
  // context.float_save.register_area[] contains 8-bit quantities and
  // does not need to be swapped.
  Append(context.float_save.register_area,
         sizeof(context.float_save.register_area));
  D32(context.float_save.cr0_npx_state);
  D32(context.gs);
  D32(context.fs);
  D32(context.es);
  D32(context.ds);
  D32(context.edi);
  D32(context.esi);
  D32(context.ebx);
  D32(context.edx);
  D32(context.ecx);
  D32(context.eax);
  D32(context.ebp);
  D32(context.eip);
  D32(context.cs);
  D32(context.eflags);
  D32(context.esp);
  D32(context.ss);
  // context.extended_registers[] contains 8-bit quantities and does
  // not need to be swapped.
  Append(context.extended_registers, sizeof(context.extended_registers));
  assert(Size() == sizeof(MDRawContextX86));
}

Context::Context(const Dump &dump, const MDRawContextARM &context)
  : Section(dump) {
  // The caller should have properly set the CPU type flag.
  assert((context.context_flags & MD_CONTEXT_ARM) ||
         (context.context_flags & MD_CONTEXT_ARM_OLD));
  // It doesn't make sense to store ARM registers in big-endian form.
  assert(dump.endianness() == kLittleEndian);
  D32(context.context_flags);
  for (int i = 0; i < MD_CONTEXT_ARM_GPR_COUNT; ++i)
    D32(context.iregs[i]);
  D32(context.cpsr);
  D64(context.float_save.fpscr);
  for (int i = 0; i < MD_FLOATINGSAVEAREA_ARM_FPR_COUNT; ++i)
    D64(context.float_save.regs[i]);
  for (int i = 0; i < MD_FLOATINGSAVEAREA_ARM_FPEXTRA_COUNT; ++i)
    D32(context.float_save.extra[i]);
  assert(Size() == sizeof(MDRawContextARM));
}

Context::Context(const Dump &dump, const MDRawContextMIPS &context)
    : Section(dump) {
  // The caller should have properly set the CPU type flag.
  assert(context.context_flags & MD_CONTEXT_MIPS);
  D32(context.context_flags);
  D32(context._pad0);

  for (int i = 0; i < MD_CONTEXT_MIPS_GPR_COUNT; ++i)
    D64(context.iregs[i]);

  D64(context.mdhi);
  D64(context.mdlo);

  for (int i = 0; i < MD_CONTEXT_MIPS_DSP_COUNT; ++i)
    D32(context.hi[i]);

  for (int i = 0; i < MD_CONTEXT_MIPS_DSP_COUNT; ++i)
    D32(context.lo[i]);

  D32(context.dsp_control);
  D32(context._pad1);

  D64(context.epc);
  D64(context.badvaddr);
  D32(context.status);
  D32(context.cause);

  for (int i = 0; i < MD_FLOATINGSAVEAREA_MIPS_FPR_COUNT; ++i)
    D64(context.float_save.regs[i]);

  D32(context.float_save.fpcsr);
  D32(context.float_save.fir);

  assert(Size() == sizeof(MDRawContextMIPS));
}

Thread::Thread(const Dump &dump,
               uint32_t thread_id, const Memory &stack, const Context &context,
               uint32_t suspend_count, uint32_t priority_class,
               uint32_t priority, uint64_t teb) : Section(dump) {
  D32(thread_id);
  D32(suspend_count);
  D32(priority_class);
  D32(priority);
  D64(teb);
  stack.CiteMemoryIn(this);
  context.CiteLocationIn(this);
  assert(Size() == sizeof(MDRawThread));
}

Module::Module(const Dump &dump,
               uint64_t base_of_image,
               uint32_t size_of_image,
               const String &name,
               uint32_t time_date_stamp,
               uint32_t checksum,
               const MDVSFixedFileInfo &version_info,
               const Section *cv_record,
               const Section *misc_record) : Section(dump) {
  D64(base_of_image);
  D32(size_of_image);
  D32(checksum);
  D32(time_date_stamp);
  name.CiteStringIn(this);
  D32(version_info.signature);
  D32(version_info.struct_version);
  D32(version_info.file_version_hi);
  D32(version_info.file_version_lo);
  D32(version_info.product_version_hi);
  D32(version_info.product_version_lo);
  D32(version_info.file_flags_mask);
  D32(version_info.file_flags);
  D32(version_info.file_os);
  D32(version_info.file_type);
  D32(version_info.file_subtype);
  D32(version_info.file_date_hi);
  D32(version_info.file_date_lo);
  cv_record->CiteLocationIn(this);
  misc_record->CiteLocationIn(this);
  D64(0).D64(0);
}

const MDVSFixedFileInfo Module::stock_version_info = {
  MD_VSFIXEDFILEINFO_SIGNATURE,         // signature
  MD_VSFIXEDFILEINFO_VERSION,           // struct_version
  0x11111111,                           // file_version_hi
  0x22222222,                           // file_version_lo
  0x33333333,                           // product_version_hi
  0x44444444,                           // product_version_lo
  MD_VSFIXEDFILEINFO_FILE_FLAGS_DEBUG,  // file_flags_mask
  MD_VSFIXEDFILEINFO_FILE_FLAGS_DEBUG,  // file_flags
  MD_VSFIXEDFILEINFO_FILE_OS_NT | MD_VSFIXEDFILEINFO_FILE_OS__WINDOWS32,
                                        // file_os
  MD_VSFIXEDFILEINFO_FILE_TYPE_APP,     // file_type
  MD_VSFIXEDFILEINFO_FILE_SUBTYPE_UNKNOWN, // file_subtype
  0,                                    // file_date_hi
  0                                     // file_date_lo
};

Exception::Exception(const Dump &dump,
                     const Context &context,
                     uint32_t thread_id,
                     uint32_t exception_code,
                     uint32_t exception_flags,
                     uint64_t exception_address)
  : Stream(dump, MD_EXCEPTION_STREAM) {
  D32(thread_id);
  D32(0);  // __align
  D32(exception_code);
  D32(exception_flags);
  D64(0);  // exception_record
  D64(exception_address);
  D32(0);  // number_parameters
  D32(0);  // __align
  for (int i = 0; i < MD_EXCEPTION_MAXIMUM_PARAMETERS; ++i)
    D64(0);  // exception_information
  context.CiteLocationIn(this);
  assert(Size() == sizeof(MDRawExceptionStream));
}

Dump::Dump(uint64_t flags,
           Endianness endianness,
           uint32_t version,
           uint32_t date_time_stamp)
    : test_assembler::Section(endianness),
      file_start_(0),
      stream_directory_(*this),
      stream_count_(0),
      thread_list_(*this, MD_THREAD_LIST_STREAM),
      module_list_(*this, MD_MODULE_LIST_STREAM),
      memory_list_(*this, MD_MEMORY_LIST_STREAM)
 {
  D32(MD_HEADER_SIGNATURE);
  D32(version);
  D32(stream_count_label_);
  D32(stream_directory_rva_);
  D32(0);
  D32(date_time_stamp);
  D64(flags);
  assert(Size() == sizeof(MDRawHeader));
}

Dump &Dump::Add(SynthMinidump::Section *section) {
  section->Finish(file_start_ + Size());
  Append(*section);
  return *this;
}

Dump &Dump::Add(Stream *stream) {
  Add(static_cast<SynthMinidump::Section *>(stream));
  stream->CiteStreamIn(&stream_directory_);
  stream_count_++;
  return *this;
}

Dump &Dump::Add(Memory *memory) {
  // Add the memory contents themselves to the file.
  Add(static_cast<SynthMinidump::Section *>(memory));

  // The memory list is a list of MDMemoryDescriptors, not of actual
  // memory elements. Produce a descriptor, and add that to the list.
  SynthMinidump::Section descriptor(*this);
  memory->CiteMemoryIn(&descriptor);
  memory_list_.Add(&descriptor);
  return *this;
}

Dump &Dump::Add(Thread *thread) {
  thread_list_.Add(thread);
  return *this;
}

Dump &Dump::Add(Module *module) {
  module_list_.Add(module);
  return *this;
}

void Dump::Finish() {
  if (!thread_list_.Empty()) Add(&thread_list_);
  if (!module_list_.Empty()) Add(&module_list_);
  if (!memory_list_.Empty()) Add(&memory_list_);

  // Create the stream directory. We don't use
  // stream_directory_.Finish here, because the stream directory isn't
  // cited using a location descriptor; rather, the Minidump header
  // has the stream count and MDRVA.
  stream_count_label_ = stream_count_;
  stream_directory_rva_ = file_start_ + Size();
  Append(static_cast<test_assembler::Section &>(stream_directory_));
}

} // namespace SynthMinidump
          
} // namespace google_breakpad
