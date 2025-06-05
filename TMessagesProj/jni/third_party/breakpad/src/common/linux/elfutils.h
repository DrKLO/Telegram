// Copyright (c) 2012, Google Inc.
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
//
// elfutils.h: Utilities for dealing with ELF files.
//

#ifndef COMMON_LINUX_ELFUTILS_H_
#define COMMON_LINUX_ELFUTILS_H_

#include <elf.h>
#include <link.h>
#include <stdint.h>

namespace google_breakpad {

// Traits classes so consumers can write templatized code to deal
// with specific ELF bits.
struct ElfClass32 {
  typedef Elf32_Addr Addr;
  typedef Elf32_Ehdr Ehdr;
  typedef Elf32_Nhdr Nhdr;
  typedef Elf32_Phdr Phdr;
  typedef Elf32_Shdr Shdr;
  typedef Elf32_Half Half;
  typedef Elf32_Off Off;
  typedef Elf32_Word Word;
  static const int kClass = ELFCLASS32;
  static const size_t kAddrSize = sizeof(Elf32_Addr);
};

struct ElfClass64 {
  typedef Elf64_Addr Addr;
  typedef Elf64_Ehdr Ehdr;
  typedef Elf64_Nhdr Nhdr;
  typedef Elf64_Phdr Phdr;
  typedef Elf64_Shdr Shdr;
  typedef Elf64_Half Half;
  typedef Elf64_Off Off;
  typedef Elf64_Word Word;
  static const int kClass = ELFCLASS64;
  static const size_t kAddrSize = sizeof(Elf64_Addr);
};

bool IsValidElf(const void* elf_header);
int ElfClass(const void* elf_base);

// Attempt to find a section named |section_name| of type |section_type|
// in the ELF binary data at |elf_mapped_base|. On success, returns true
// and sets |*section_start| to point to the start of the section data,
// and |*section_size| to the size of the section's data. If |elfclass|
// is not NULL, set |*elfclass| to the ELF file class.
bool FindElfSection(const void *elf_mapped_base,
                    const char *section_name,
                    uint32_t section_type,
                    const void **section_start,
                    size_t *section_size,
                    int *elfclass);

// Internal helper method, exposed for convenience for callers
// that already have more info.
template<typename ElfClass>
const typename ElfClass::Shdr*
FindElfSectionByName(const char* name,
                     typename ElfClass::Word section_type,
                     const typename ElfClass::Shdr* sections,
                     const char* section_names,
                     const char* names_end,
                     int nsection);

// Attempt to find the first segment of type |segment_type| in the ELF
// binary data at |elf_mapped_base|. On success, returns true and sets
// |*segment_start| to point to the start of the segment data, and
// and |*segment_size| to the size of the segment's data. If |elfclass|
// is not NULL, set |*elfclass| to the ELF file class.
bool FindElfSegment(const void *elf_mapped_base,
                    uint32_t segment_type,
                    const void **segment_start,
                    size_t *segment_size,
                    int *elfclass);

// Convert an offset from an Elf header into a pointer to the mapped
// address in the current process. Takes an extra template parameter
// to specify the return type to avoid having to dynamic_cast the
// result.
template<typename ElfClass, typename T>
const T*
GetOffset(const typename ElfClass::Ehdr* elf_header,
          typename ElfClass::Off offset);

}  // namespace google_breakpad

#endif  // COMMON_LINUX_ELFUTILS_H_
