// Copyright (c) 2011 Google Inc.
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

// Restructured in 2009 by: Jim Blandy <jimb@mozilla.com> <jimb@red-bean.com>

// dump_symbols.cc: implement google_breakpad::WriteSymbolFile:
// Find all the debugging info in a file and dump it as a Breakpad symbol file.

#include "common/linux/dump_symbols.h"

#include <assert.h>
#include <elf.h>
#include <errno.h>
#include <fcntl.h>
#include <link.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/mman.h>
#include <sys/stat.h>
#include <unistd.h>

#include <iostream>
#include <set>
#include <string>
#include <utility>
#include <vector>

#include "common/dwarf/bytereader-inl.h"
#include "common/dwarf/dwarf2diehandler.h"
#include "common/dwarf_cfi_to_module.h"
#include "common/dwarf_cu_to_module.h"
#include "common/dwarf_line_to_module.h"
#include "common/linux/crc32.h"
#include "common/linux/eintr_wrapper.h"
#include "common/linux/elfutils.h"
#include "common/linux/elfutils-inl.h"
#include "common/linux/elf_symbols_to_module.h"
#include "common/linux/file_id.h"
#include "common/module.h"
#include "common/scoped_ptr.h"
#ifndef NO_STABS_SUPPORT
#include "common/stabs_reader.h"
#include "common/stabs_to_module.h"
#endif
#include "common/using_std_string.h"

// This namespace contains helper functions.
namespace {

using google_breakpad::DumpOptions;
using google_breakpad::DwarfCFIToModule;
using google_breakpad::DwarfCUToModule;
using google_breakpad::DwarfLineToModule;
using google_breakpad::ElfClass;
using google_breakpad::ElfClass32;
using google_breakpad::ElfClass64;
using google_breakpad::FindElfSectionByName;
using google_breakpad::GetOffset;
using google_breakpad::IsValidElf;
using google_breakpad::Module;
#ifndef NO_STABS_SUPPORT
using google_breakpad::StabsToModule;
#endif
using google_breakpad::scoped_ptr;

// Define AARCH64 ELF architecture if host machine does not include this define.
#ifndef EM_AARCH64
#define EM_AARCH64      183
#endif

//
// FDWrapper
//
// Wrapper class to make sure opened file is closed.
//
class FDWrapper {
 public:
  explicit FDWrapper(int fd) :
    fd_(fd) {}
  ~FDWrapper() {
    if (fd_ != -1)
      close(fd_);
  }
  int get() {
    return fd_;
  }
  int release() {
    int fd = fd_;
    fd_ = -1;
    return fd;
  }
 private:
  int fd_;
};

//
// MmapWrapper
//
// Wrapper class to make sure mapped regions are unmapped.
//
class MmapWrapper {
 public:
  MmapWrapper() : is_set_(false) {}
  ~MmapWrapper() {
    if (is_set_ && base_ != NULL) {
      assert(size_ > 0);
      munmap(base_, size_);
    }
  }
  void set(void *mapped_address, size_t mapped_size) {
    is_set_ = true;
    base_ = mapped_address;
    size_ = mapped_size;
  }
  void release() {
    assert(is_set_);
    is_set_ = false;
    base_ = NULL;
    size_ = 0;
  }

 private:
  bool is_set_;
  void* base_;
  size_t size_;
};

// Find the preferred loading address of the binary.
template<typename ElfClass>
typename ElfClass::Addr GetLoadingAddress(
    const typename ElfClass::Phdr* program_headers,
    int nheader) {
  typedef typename ElfClass::Phdr Phdr;

  // For non-PIC executables (e_type == ET_EXEC), the load address is
  // the start address of the first PT_LOAD segment.  (ELF requires
  // the segments to be sorted by load address.)  For PIC executables
  // and dynamic libraries (e_type == ET_DYN), this address will
  // normally be zero.
  for (int i = 0; i < nheader; ++i) {
    const Phdr& header = program_headers[i];
    if (header.p_type == PT_LOAD)
      return header.p_vaddr;
  }
  return 0;
}

#ifndef NO_STABS_SUPPORT
template<typename ElfClass>
bool LoadStabs(const typename ElfClass::Ehdr* elf_header,
               const typename ElfClass::Shdr* stab_section,
               const typename ElfClass::Shdr* stabstr_section,
               const bool big_endian,
               Module* module) {
  // A callback object to handle data from the STABS reader.
  StabsToModule handler(module);
  // Find the addresses of the STABS data, and create a STABS reader object.
  // On Linux, STABS entries always have 32-bit values, regardless of the
  // address size of the architecture whose code they're describing, and
  // the strings are always "unitized".
  const uint8_t* stabs =
      GetOffset<ElfClass, uint8_t>(elf_header, stab_section->sh_offset);
  const uint8_t* stabstr =
      GetOffset<ElfClass, uint8_t>(elf_header, stabstr_section->sh_offset);
  google_breakpad::StabsReader reader(stabs, stab_section->sh_size,
                                      stabstr, stabstr_section->sh_size,
                                      big_endian, 4, true, &handler);
  // Read the STABS data, and do post-processing.
  if (!reader.Process())
    return false;
  handler.Finalize();
  return true;
}
#endif  // NO_STABS_SUPPORT

// A line-to-module loader that accepts line number info parsed by
// dwarf2reader::LineInfo and populates a Module and a line vector
// with the results.
class DumperLineToModule: public DwarfCUToModule::LineToModuleHandler {
 public:
  // Create a line-to-module converter using BYTE_READER.
  explicit DumperLineToModule(dwarf2reader::ByteReader *byte_reader)
      : byte_reader_(byte_reader) { }
  void StartCompilationUnit(const string& compilation_dir) {
    compilation_dir_ = compilation_dir;
  }
  void ReadProgram(const char* program, uint64 length,
                   Module* module, std::vector<Module::Line>* lines) {
    DwarfLineToModule handler(module, compilation_dir_, lines);
    dwarf2reader::LineInfo parser(program, length, byte_reader_, &handler);
    parser.Start();
  }
 private:
  string compilation_dir_;
  dwarf2reader::ByteReader *byte_reader_;
};

template<typename ElfClass>
bool LoadDwarf(const string& dwarf_filename,
               const typename ElfClass::Ehdr* elf_header,
               const bool big_endian,
               bool handle_inter_cu_refs,
               Module* module) {
  typedef typename ElfClass::Shdr Shdr;

  const dwarf2reader::Endianness endianness = big_endian ?
      dwarf2reader::ENDIANNESS_BIG : dwarf2reader::ENDIANNESS_LITTLE;
  dwarf2reader::ByteReader byte_reader(endianness);

  // Construct a context for this file.
  DwarfCUToModule::FileContext file_context(dwarf_filename,
                                            module,
                                            handle_inter_cu_refs);

  // Build a map of the ELF file's sections.
  const Shdr* sections =
      GetOffset<ElfClass, Shdr>(elf_header, elf_header->e_shoff);
  int num_sections = elf_header->e_shnum;
  const Shdr* section_names = sections + elf_header->e_shstrndx;
  for (int i = 0; i < num_sections; i++) {
    const Shdr* section = &sections[i];
    string name = GetOffset<ElfClass, char>(elf_header,
                                            section_names->sh_offset) +
                  section->sh_name;
    const char* contents = GetOffset<ElfClass, char>(elf_header,
                                                     section->sh_offset);
    file_context.AddSectionToSectionMap(name, contents, section->sh_size);
  }

  // Parse all the compilation units in the .debug_info section.
  DumperLineToModule line_to_module(&byte_reader);
  dwarf2reader::SectionMap::const_iterator debug_info_entry =
      file_context.section_map().find(".debug_info");
  assert(debug_info_entry != file_context.section_map().end());
  const std::pair<const char*, uint64>& debug_info_section =
      debug_info_entry->second;
  // This should never have been called if the file doesn't have a
  // .debug_info section.
  assert(debug_info_section.first);
  uint64 debug_info_length = debug_info_section.second;
  for (uint64 offset = 0; offset < debug_info_length;) {
    // Make a handler for the root DIE that populates MODULE with the
    // data that was found.
    DwarfCUToModule::WarningReporter reporter(dwarf_filename, offset);
    DwarfCUToModule root_handler(&file_context, &line_to_module, &reporter);
    // Make a Dwarf2Handler that drives the DIEHandler.
    dwarf2reader::DIEDispatcher die_dispatcher(&root_handler);
    // Make a DWARF parser for the compilation unit at OFFSET.
    dwarf2reader::CompilationUnit reader(file_context.section_map(),
                                         offset,
                                         &byte_reader,
                                         &die_dispatcher);
    // Process the entire compilation unit; get the offset of the next.
    offset += reader.Start();
  }
  return true;
}

// Fill REGISTER_NAMES with the register names appropriate to the
// machine architecture given in HEADER, indexed by the register
// numbers used in DWARF call frame information. Return true on
// success, or false if HEADER's machine architecture is not
// supported.
template<typename ElfClass>
bool DwarfCFIRegisterNames(const typename ElfClass::Ehdr* elf_header,
                           std::vector<string>* register_names) {
  switch (elf_header->e_machine) {
    case EM_386:
      *register_names = DwarfCFIToModule::RegisterNames::I386();
      return true;
    case EM_ARM:
      *register_names = DwarfCFIToModule::RegisterNames::ARM();
      return true;
    case EM_AARCH64:
      *register_names = DwarfCFIToModule::RegisterNames::ARM64();
      return true;
    case EM_MIPS:
      *register_names = DwarfCFIToModule::RegisterNames::MIPS();
      return true;
    case EM_X86_64:
      *register_names = DwarfCFIToModule::RegisterNames::X86_64();
      return true;
    default:
      return false;
  }
}

template<typename ElfClass>
bool LoadDwarfCFI(const string& dwarf_filename,
                  const typename ElfClass::Ehdr* elf_header,
                  const char* section_name,
                  const typename ElfClass::Shdr* section,
                  const bool eh_frame,
                  const typename ElfClass::Shdr* got_section,
                  const typename ElfClass::Shdr* text_section,
                  const bool big_endian,
                  Module* module) {
  // Find the appropriate set of register names for this file's
  // architecture.
  std::vector<string> register_names;
  if (!DwarfCFIRegisterNames<ElfClass>(elf_header, &register_names)) {
    fprintf(stderr, "%s: unrecognized ELF machine architecture '%d';"
            " cannot convert DWARF call frame information\n",
            dwarf_filename.c_str(), elf_header->e_machine);
    return false;
  }

  const dwarf2reader::Endianness endianness = big_endian ?
      dwarf2reader::ENDIANNESS_BIG : dwarf2reader::ENDIANNESS_LITTLE;

  // Find the call frame information and its size.
  const char* cfi =
      GetOffset<ElfClass, char>(elf_header, section->sh_offset);
  size_t cfi_size = section->sh_size;

  // Plug together the parser, handler, and their entourages.
  DwarfCFIToModule::Reporter module_reporter(dwarf_filename, section_name);
  DwarfCFIToModule handler(module, register_names, &module_reporter);
  dwarf2reader::ByteReader byte_reader(endianness);

  byte_reader.SetAddressSize(ElfClass::kAddrSize);

  // Provide the base addresses for .eh_frame encoded pointers, if
  // possible.
  byte_reader.SetCFIDataBase(section->sh_addr, cfi);
  if (got_section)
    byte_reader.SetDataBase(got_section->sh_addr);
  if (text_section)
    byte_reader.SetTextBase(text_section->sh_addr);

  dwarf2reader::CallFrameInfo::Reporter dwarf_reporter(dwarf_filename,
                                                       section_name);
  dwarf2reader::CallFrameInfo parser(cfi, cfi_size,
                                     &byte_reader, &handler, &dwarf_reporter,
                                     eh_frame);
  parser.Start();
  return true;
}

bool LoadELF(const string& obj_file, MmapWrapper* map_wrapper,
             void** elf_header) {
  int obj_fd = open(obj_file.c_str(), O_RDONLY);
  if (obj_fd < 0) {
    fprintf(stderr, "Failed to open ELF file '%s': %s\n",
            obj_file.c_str(), strerror(errno));
    return false;
  }
  FDWrapper obj_fd_wrapper(obj_fd);
  struct stat st;
  if (fstat(obj_fd, &st) != 0 && st.st_size <= 0) {
    fprintf(stderr, "Unable to fstat ELF file '%s': %s\n",
            obj_file.c_str(), strerror(errno));
    return false;
  }
  void* obj_base = mmap(NULL, st.st_size,
                        PROT_READ | PROT_WRITE, MAP_PRIVATE, obj_fd, 0);
  if (obj_base == MAP_FAILED) {
    fprintf(stderr, "Failed to mmap ELF file '%s': %s\n",
            obj_file.c_str(), strerror(errno));
    return false;
  }
  map_wrapper->set(obj_base, st.st_size);
  *elf_header = obj_base;
  if (!IsValidElf(*elf_header)) {
    fprintf(stderr, "Not a valid ELF file: %s\n", obj_file.c_str());
    return false;
  }
  return true;
}

// Get the endianness of ELF_HEADER. If it's invalid, return false.
template<typename ElfClass>
bool ElfEndianness(const typename ElfClass::Ehdr* elf_header,
                   bool* big_endian) {
  if (elf_header->e_ident[EI_DATA] == ELFDATA2LSB) {
    *big_endian = false;
    return true;
  }
  if (elf_header->e_ident[EI_DATA] == ELFDATA2MSB) {
    *big_endian = true;
    return true;
  }

  fprintf(stderr, "bad data encoding in ELF header: %d\n",
          elf_header->e_ident[EI_DATA]);
  return false;
}

// Given |left_abspath|, find the absolute path for |right_path| and see if the
// two absolute paths are the same.
bool IsSameFile(const char* left_abspath, const string& right_path) {
  char right_abspath[PATH_MAX];
  if (!realpath(right_path.c_str(), right_abspath))
    return false;
  return strcmp(left_abspath, right_abspath) == 0;
}

// Read the .gnu_debuglink and get the debug file name. If anything goes
// wrong, return an empty string.
string ReadDebugLink(const char* debuglink,
                     const size_t debuglink_size,
                     const bool big_endian,
                     const string& obj_file,
                     const std::vector<string>& debug_dirs) {
  size_t debuglink_len = strlen(debuglink) + 5;  // Include '\0' + CRC32.
  debuglink_len = 4 * ((debuglink_len + 3) / 4);  // Round up to 4 bytes.

  // Sanity check.
  if (debuglink_len != debuglink_size) {
    fprintf(stderr, "Mismatched .gnu_debuglink string / section size: "
            "%zx %zx\n", debuglink_len, debuglink_size);
    return string();
  }

  char obj_file_abspath[PATH_MAX];
  if (!realpath(obj_file.c_str(), obj_file_abspath)) {
    fprintf(stderr, "Cannot resolve absolute path for %s\n", obj_file.c_str());
    return string();
  }

  std::vector<string> searched_paths;
  string debuglink_path;
  std::vector<string>::const_iterator it;
  for (it = debug_dirs.begin(); it < debug_dirs.end(); ++it) {
    const string& debug_dir = *it;
    debuglink_path = debug_dir + "/" + debuglink;

    // There is the annoying case of /path/to/foo.so having foo.so as the
    // debug link file name. Thus this may end up opening /path/to/foo.so again,
    // and there is a small chance of the two files having the same CRC.
    if (IsSameFile(obj_file_abspath, debuglink_path))
      continue;

    searched_paths.push_back(debug_dir);
    int debuglink_fd = open(debuglink_path.c_str(), O_RDONLY);
    if (debuglink_fd < 0)
      continue;

    FDWrapper debuglink_fd_wrapper(debuglink_fd);

    // The CRC is the last 4 bytes in |debuglink|.
    const dwarf2reader::Endianness endianness = big_endian ?
        dwarf2reader::ENDIANNESS_BIG : dwarf2reader::ENDIANNESS_LITTLE;
    dwarf2reader::ByteReader byte_reader(endianness);
    uint32_t expected_crc =
        byte_reader.ReadFourBytes(&debuglink[debuglink_size - 4]);

    uint32_t actual_crc = 0;
    while (true) {
      const size_t kReadSize = 4096;
      char buf[kReadSize];
      ssize_t bytes_read = HANDLE_EINTR(read(debuglink_fd, &buf, kReadSize));
      if (bytes_read < 0) {
        fprintf(stderr, "Error reading debug ELF file %s.\n",
                debuglink_path.c_str());
        return string();
      }
      if (bytes_read == 0)
        break;
      actual_crc = google_breakpad::UpdateCrc32(actual_crc, buf, bytes_read);
    }
    if (actual_crc != expected_crc) {
      fprintf(stderr, "Error reading debug ELF file - CRC32 mismatch: %s\n",
              debuglink_path.c_str());
      continue;
    }

    // Found debug file.
    return debuglink_path;
  }

  // Not found case.
  fprintf(stderr, "Failed to find debug ELF file for '%s' after trying:\n",
          obj_file.c_str());
  for (it = searched_paths.begin(); it < searched_paths.end(); ++it) {
    const string& debug_dir = *it;
    fprintf(stderr, "  %s/%s\n", debug_dir.c_str(), debuglink);
  }
  return string();
}

//
// LoadSymbolsInfo
//
// Holds the state between the two calls to LoadSymbols() in case it's necessary
// to follow the .gnu_debuglink section and load debug information from a
// different file.
//
template<typename ElfClass>
class LoadSymbolsInfo {
 public:
  typedef typename ElfClass::Addr Addr;

  explicit LoadSymbolsInfo(const std::vector<string>& dbg_dirs) :
    debug_dirs_(dbg_dirs),
    has_loading_addr_(false) {}

  // Keeps track of which sections have been loaded so sections don't
  // accidentally get loaded twice from two different files.
  void LoadedSection(const string &section) {
    if (loaded_sections_.count(section) == 0) {
      loaded_sections_.insert(section);
    } else {
      fprintf(stderr, "Section %s has already been loaded.\n",
              section.c_str());
    }
  }

  // The ELF file and linked debug file are expected to have the same preferred
  // loading address.
  void set_loading_addr(Addr addr, const string &filename) {
    if (!has_loading_addr_) {
      loading_addr_ = addr;
      loaded_file_ = filename;
      return;
    }

    if (addr != loading_addr_) {
      fprintf(stderr,
              "ELF file '%s' and debug ELF file '%s' "
              "have different load addresses.\n",
              loaded_file_.c_str(), filename.c_str());
      assert(false);
    }
  }

  // Setters and getters
  const std::vector<string>& debug_dirs() const {
    return debug_dirs_;
  }

  string debuglink_file() const {
    return debuglink_file_;
  }
  void set_debuglink_file(string file) {
    debuglink_file_ = file;
  }

 private:
  const std::vector<string>& debug_dirs_; // Directories in which to
                                          // search for the debug ELF file.

  string debuglink_file_;  // Full path to the debug ELF file.

  bool has_loading_addr_;  // Indicate if LOADING_ADDR_ is valid.

  Addr loading_addr_;  // Saves the preferred loading address from the
                       // first call to LoadSymbols().

  string loaded_file_;  // Name of the file loaded from the first call to
                        // LoadSymbols().

  std::set<string> loaded_sections_;  // Tracks the Loaded ELF sections
                                      // between calls to LoadSymbols().
};

template<typename ElfClass>
bool LoadSymbols(const string& obj_file,
                 const bool big_endian,
                 const typename ElfClass::Ehdr* elf_header,
                 const bool read_gnu_debug_link,
                 LoadSymbolsInfo<ElfClass>* info,
                 const DumpOptions& options,
                 Module* module) {
  typedef typename ElfClass::Addr Addr;
  typedef typename ElfClass::Phdr Phdr;
  typedef typename ElfClass::Shdr Shdr;
  typedef typename ElfClass::Word Word;

  Addr loading_addr = GetLoadingAddress<ElfClass>(
      GetOffset<ElfClass, Phdr>(elf_header, elf_header->e_phoff),
      elf_header->e_phnum);
  module->SetLoadAddress(loading_addr);
  info->set_loading_addr(loading_addr, obj_file);

  Word debug_section_type =
      elf_header->e_machine == EM_MIPS ? SHT_MIPS_DWARF : SHT_PROGBITS;
  const Shdr* sections =
      GetOffset<ElfClass, Shdr>(elf_header, elf_header->e_shoff);
  const Shdr* section_names = sections + elf_header->e_shstrndx;
  const char* names =
      GetOffset<ElfClass, char>(elf_header, section_names->sh_offset);
  const char *names_end = names + section_names->sh_size;
  bool found_debug_info_section = false;
  bool found_usable_info = false;

  if (options.symbol_data != ONLY_CFI) {
#ifndef NO_STABS_SUPPORT
    // Look for STABS debugging information, and load it if present.
    const Shdr* stab_section =
      FindElfSectionByName<ElfClass>(".stab", SHT_PROGBITS,
                                     sections, names, names_end,
                                     elf_header->e_shnum);
    if (stab_section) {
      const Shdr* stabstr_section = stab_section->sh_link + sections;
      if (stabstr_section) {
        found_debug_info_section = true;
        found_usable_info = true;
        info->LoadedSection(".stab");
        if (!LoadStabs<ElfClass>(elf_header, stab_section, stabstr_section,
                                 big_endian, module)) {
          fprintf(stderr, "%s: \".stab\" section found, but failed to load"
                  " STABS debugging information\n", obj_file.c_str());
        }
      }
    }
#endif  // NO_STABS_SUPPORT

    // Look for DWARF debugging information, and load it if present.
    const Shdr* dwarf_section =
      FindElfSectionByName<ElfClass>(".debug_info", debug_section_type,
                                     sections, names, names_end,
                                     elf_header->e_shnum);
    if (dwarf_section) {
      found_debug_info_section = true;
      found_usable_info = true;
      info->LoadedSection(".debug_info");
      if (!LoadDwarf<ElfClass>(obj_file, elf_header, big_endian,
                               options.handle_inter_cu_refs, module)) {
        fprintf(stderr, "%s: \".debug_info\" section found, but failed to load "
                "DWARF debugging information\n", obj_file.c_str());
      }
    }

    // See if there are export symbols available.
    const Shdr* dynsym_section =
      FindElfSectionByName<ElfClass>(".dynsym", SHT_DYNSYM,
                                     sections, names, names_end,
                                     elf_header->e_shnum);
    const Shdr* dynstr_section =
      FindElfSectionByName<ElfClass>(".dynstr", SHT_STRTAB,
                                     sections, names, names_end,
                                     elf_header->e_shnum);
    if (dynsym_section && dynstr_section) {
      info->LoadedSection(".dynsym");

      const uint8_t* dynsyms =
          GetOffset<ElfClass, uint8_t>(elf_header,
                                       dynsym_section->sh_offset);
      const uint8_t* dynstrs =
          GetOffset<ElfClass, uint8_t>(elf_header,
                                       dynstr_section->sh_offset);
      bool result =
          ELFSymbolsToModule(dynsyms,
                             dynsym_section->sh_size,
                             dynstrs,
                             dynstr_section->sh_size,
                             big_endian,
                             ElfClass::kAddrSize,
                             module);
      found_usable_info = found_usable_info || result;
    }
  }

  if (options.symbol_data != NO_CFI) {
    // Dwarf Call Frame Information (CFI) is actually independent from
    // the other DWARF debugging information, and can be used alone.
    const Shdr* dwarf_cfi_section =
        FindElfSectionByName<ElfClass>(".debug_frame", debug_section_type,
                                       sections, names, names_end,
                                       elf_header->e_shnum);
    if (dwarf_cfi_section) {
      // Ignore the return value of this function; even without call frame
      // information, the other debugging information could be perfectly
      // useful.
      info->LoadedSection(".debug_frame");
      bool result =
          LoadDwarfCFI<ElfClass>(obj_file, elf_header, ".debug_frame",
                                 dwarf_cfi_section, false, 0, 0, big_endian,
                                 module);
      found_usable_info = found_usable_info || result;
    }

    // Linux C++ exception handling information can also provide
    // unwinding data.
    const Shdr* eh_frame_section =
        FindElfSectionByName<ElfClass>(".eh_frame", SHT_PROGBITS,
                                       sections, names, names_end,
                                       elf_header->e_shnum);
    if (eh_frame_section) {
      // Pointers in .eh_frame data may be relative to the base addresses of
      // certain sections. Provide those sections if present.
      const Shdr* got_section =
          FindElfSectionByName<ElfClass>(".got", SHT_PROGBITS,
                                         sections, names, names_end,
                                         elf_header->e_shnum);
      const Shdr* text_section =
          FindElfSectionByName<ElfClass>(".text", SHT_PROGBITS,
                                         sections, names, names_end,
                                         elf_header->e_shnum);
      info->LoadedSection(".eh_frame");
      // As above, ignore the return value of this function.
      bool result =
          LoadDwarfCFI<ElfClass>(obj_file, elf_header, ".eh_frame",
                                 eh_frame_section, true,
                                 got_section, text_section, big_endian, module);
      found_usable_info = found_usable_info || result;
    }
  }

  if (!found_debug_info_section) {
    fprintf(stderr, "%s: file contains no debugging information"
            " (no \".stab\" or \".debug_info\" sections)\n",
            obj_file.c_str());

    // Failed, but maybe there's a .gnu_debuglink section?
    if (read_gnu_debug_link) {
      const Shdr* gnu_debuglink_section
          = FindElfSectionByName<ElfClass>(".gnu_debuglink", SHT_PROGBITS,
                                           sections, names,
                                           names_end, elf_header->e_shnum);
      if (gnu_debuglink_section) {
        if (!info->debug_dirs().empty()) {
          const char* debuglink_contents =
              GetOffset<ElfClass, char>(elf_header,
                                        gnu_debuglink_section->sh_offset);
          string debuglink_file =
              ReadDebugLink(debuglink_contents,
                            gnu_debuglink_section->sh_size,
                            big_endian,
                            obj_file,
                            info->debug_dirs());
          info->set_debuglink_file(debuglink_file);
        } else {
          fprintf(stderr, ".gnu_debuglink section found in '%s', "
                  "but no debug path specified.\n", obj_file.c_str());
        }
      } else {
        fprintf(stderr, "%s does not contain a .gnu_debuglink section.\n",
                obj_file.c_str());
      }
    } else {
      // Return true if some usable information was found, since the caller
      // doesn't want to use .gnu_debuglink.
      return found_usable_info;
    }

    // No debug info was found, let the user try again with .gnu_debuglink
    // if present.
    return false;
  }

  return true;
}

// Return the breakpad symbol file identifier for the architecture of
// ELF_HEADER.
template<typename ElfClass>
const char* ElfArchitecture(const typename ElfClass::Ehdr* elf_header) {
  typedef typename ElfClass::Half Half;
  Half arch = elf_header->e_machine;
  switch (arch) {
    case EM_386:        return "x86";
    case EM_ARM:        return "arm";
    case EM_AARCH64:    return "arm64";
    case EM_MIPS:       return "mips";
    case EM_PPC64:      return "ppc64";
    case EM_PPC:        return "ppc";
    case EM_S390:       return "s390";
    case EM_SPARC:      return "sparc";
    case EM_SPARCV9:    return "sparcv9";
    case EM_X86_64:     return "x86_64";
    default: return NULL;
  }
}

// Format the Elf file identifier in IDENTIFIER as a UUID with the
// dashes removed.
string FormatIdentifier(unsigned char identifier[16]) {
  char identifier_str[40];
  google_breakpad::FileID::ConvertIdentifierToString(
      identifier,
      identifier_str,
      sizeof(identifier_str));
  string id_no_dash;
  for (int i = 0; identifier_str[i] != '\0'; ++i)
    if (identifier_str[i] != '-')
      id_no_dash += identifier_str[i];
  // Add an extra "0" by the end.  PDB files on Windows have an 'age'
  // number appended to the end of the file identifier; this isn't
  // really used or necessary on other platforms, but be consistent.
  id_no_dash += '0';
  return id_no_dash;
}

// Return the non-directory portion of FILENAME: the portion after the
// last slash, or the whole filename if there are no slashes.
string BaseFileName(const string &filename) {
  // Lots of copies!  basename's behavior is less than ideal.
  char* c_filename = strdup(filename.c_str());
  string base = basename(c_filename);
  free(c_filename);
  return base;
}

template<typename ElfClass>
bool SanitizeDebugFile(const typename ElfClass::Ehdr* debug_elf_header,
                       const string& debuglink_file,
                       const string& obj_filename,
                       const char* obj_file_architecture,
                       const bool obj_file_is_big_endian) {
  const char* debug_architecture =
      ElfArchitecture<ElfClass>(debug_elf_header);
  if (!debug_architecture) {
    fprintf(stderr, "%s: unrecognized ELF machine architecture: %d\n",
            debuglink_file.c_str(), debug_elf_header->e_machine);
    return false;
  }
  if (strcmp(obj_file_architecture, debug_architecture)) {
    fprintf(stderr, "%s with ELF machine architecture %s does not match "
            "%s with ELF architecture %s\n",
            debuglink_file.c_str(), debug_architecture,
            obj_filename.c_str(), obj_file_architecture);
    return false;
  }
  bool debug_big_endian;
  if (!ElfEndianness<ElfClass>(debug_elf_header, &debug_big_endian))
    return false;
  if (debug_big_endian != obj_file_is_big_endian) {
    fprintf(stderr, "%s and %s does not match in endianness\n",
            obj_filename.c_str(), debuglink_file.c_str());
    return false;
  }
  return true;
}

template<typename ElfClass>
bool ReadSymbolDataElfClass(const typename ElfClass::Ehdr* elf_header,
                            const string& obj_filename,
                            const std::vector<string>& debug_dirs,
                            const DumpOptions& options,
                            Module** out_module) {
  typedef typename ElfClass::Ehdr Ehdr;
  typedef typename ElfClass::Shdr Shdr;

  *out_module = NULL;

  unsigned char identifier[16];
  if (!google_breakpad::FileID::ElfFileIdentifierFromMappedFile(elf_header,
                                                                identifier)) {
    fprintf(stderr, "%s: unable to generate file identifier\n",
            obj_filename.c_str());
    return false;
  }

  const char *architecture = ElfArchitecture<ElfClass>(elf_header);
  if (!architecture) {
    fprintf(stderr, "%s: unrecognized ELF machine architecture: %d\n",
            obj_filename.c_str(), elf_header->e_machine);
    return false;
  }

  // Figure out what endianness this file is.
  bool big_endian;
  if (!ElfEndianness<ElfClass>(elf_header, &big_endian))
    return false;

  string name = BaseFileName(obj_filename);
  string os = "Linux";
  string id = FormatIdentifier(identifier);

  LoadSymbolsInfo<ElfClass> info(debug_dirs);
  scoped_ptr<Module> module(new Module(name, os, architecture, id));
  if (!LoadSymbols<ElfClass>(obj_filename, big_endian, elf_header,
                             !debug_dirs.empty(), &info,
                             options, module.get())) {
    const string debuglink_file = info.debuglink_file();
    if (debuglink_file.empty())
      return false;

    // Load debuglink ELF file.
    fprintf(stderr, "Found debugging info in %s\n", debuglink_file.c_str());
    MmapWrapper debug_map_wrapper;
    Ehdr* debug_elf_header = NULL;
    if (!LoadELF(debuglink_file, &debug_map_wrapper,
                 reinterpret_cast<void**>(&debug_elf_header)) ||
        !SanitizeDebugFile<ElfClass>(debug_elf_header, debuglink_file,
                                     obj_filename, architecture, big_endian)) {
      return false;
    }

    if (!LoadSymbols<ElfClass>(debuglink_file, big_endian,
                               debug_elf_header, false, &info,
                               options, module.get())) {
      return false;
    }
  }

  *out_module = module.release();
  return true;
}

}  // namespace

namespace google_breakpad {

// Not explicitly exported, but not static so it can be used in unit tests.
bool ReadSymbolDataInternal(const uint8_t* obj_file,
                            const string& obj_filename,
                            const std::vector<string>& debug_dirs,
                            const DumpOptions& options,
                            Module** module) {
  if (!IsValidElf(obj_file)) {
    fprintf(stderr, "Not a valid ELF file: %s\n", obj_filename.c_str());
    return false;
  }

  int elfclass = ElfClass(obj_file);
  if (elfclass == ELFCLASS32) {
    return ReadSymbolDataElfClass<ElfClass32>(
        reinterpret_cast<const Elf32_Ehdr*>(obj_file), obj_filename, debug_dirs,
        options, module);
  }
  if (elfclass == ELFCLASS64) {
    return ReadSymbolDataElfClass<ElfClass64>(
        reinterpret_cast<const Elf64_Ehdr*>(obj_file), obj_filename, debug_dirs,
        options, module);
  }

  return false;
}

bool WriteSymbolFile(const string &obj_file,
                     const std::vector<string>& debug_dirs,
                     const DumpOptions& options,
                     std::ostream &sym_stream) {
  Module* module;
  if (!ReadSymbolData(obj_file, debug_dirs, options, &module))
    return false;

  bool result = module->Write(sym_stream, options.symbol_data);
  delete module;
  return result;
}

bool ReadSymbolData(const string& obj_file,
                    const std::vector<string>& debug_dirs,
                    const DumpOptions& options,
                    Module** module) {
  MmapWrapper map_wrapper;
  void* elf_header = NULL;
  if (!LoadELF(obj_file, &map_wrapper, &elf_header))
    return false;

  return ReadSymbolDataInternal(reinterpret_cast<uint8_t*>(elf_header),
                                obj_file, debug_dirs, options, module);
}

}  // namespace google_breakpad
