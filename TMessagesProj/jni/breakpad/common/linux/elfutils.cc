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

#include "common/linux/elfutils.h"

#include <assert.h>
#include <string.h>

#include "common/linux/linux_libc_support.h"
#include "common/linux/elfutils-inl.h"

namespace google_breakpad {

namespace {

template<typename ElfClass>
void FindElfClassSection(const char *elf_base,
                         const char *section_name,
                         typename ElfClass::Word section_type,
                         const void **section_start,
                         size_t *section_size) {
  typedef typename ElfClass::Ehdr Ehdr;
  typedef typename ElfClass::Shdr Shdr;

  assert(elf_base);
  assert(section_start);
  assert(section_size);

  assert(my_strncmp(elf_base, ELFMAG, SELFMAG) == 0);

  const Ehdr* elf_header = reinterpret_cast<const Ehdr*>(elf_base);
  assert(elf_header->e_ident[EI_CLASS] == ElfClass::kClass);

  const Shdr* sections =
    GetOffset<ElfClass, Shdr>(elf_header, elf_header->e_shoff);
  const Shdr* section_names = sections + elf_header->e_shstrndx;
  const char* names =
    GetOffset<ElfClass, char>(elf_header, section_names->sh_offset);
  const char *names_end = names + section_names->sh_size;

  const Shdr* section =
    FindElfSectionByName<ElfClass>(section_name, section_type,
                                   sections, names, names_end,
                                   elf_header->e_shnum);

  if (section != NULL && section->sh_size > 0) {
    *section_start = elf_base + section->sh_offset;
    *section_size = section->sh_size;
  }
}

template<typename ElfClass>
void FindElfClassSegment(const char *elf_base,
                         typename ElfClass::Word segment_type,
                         const void **segment_start,
                         size_t *segment_size) {
  typedef typename ElfClass::Ehdr Ehdr;
  typedef typename ElfClass::Phdr Phdr;

  assert(elf_base);
  assert(segment_start);
  assert(segment_size);

  assert(my_strncmp(elf_base, ELFMAG, SELFMAG) == 0);

  const Ehdr* elf_header = reinterpret_cast<const Ehdr*>(elf_base);
  assert(elf_header->e_ident[EI_CLASS] == ElfClass::kClass);

  const Phdr* phdrs =
    GetOffset<ElfClass, Phdr>(elf_header, elf_header->e_phoff);

  for (int i = 0; i < elf_header->e_phnum; ++i) {
    if (phdrs[i].p_type == segment_type) {
      *segment_start = elf_base + phdrs[i].p_offset;
      *segment_size = phdrs[i].p_filesz;
      return;
    }
  }
}

}  // namespace

bool IsValidElf(const void* elf_base) {
  return my_strncmp(reinterpret_cast<const char*>(elf_base),
                    ELFMAG, SELFMAG) == 0;
}

int ElfClass(const void* elf_base) {
  const ElfW(Ehdr)* elf_header =
    reinterpret_cast<const ElfW(Ehdr)*>(elf_base);

  return elf_header->e_ident[EI_CLASS];
}

bool FindElfSection(const void *elf_mapped_base,
                    const char *section_name,
                    uint32_t section_type,
                    const void **section_start,
                    size_t *section_size,
                    int *elfclass) {
  assert(elf_mapped_base);
  assert(section_start);
  assert(section_size);

  *section_start = NULL;
  *section_size = 0;

  if (!IsValidElf(elf_mapped_base))
    return false;

  int cls = ElfClass(elf_mapped_base);
  if (elfclass) {
    *elfclass = cls;
  }

  const char* elf_base =
    static_cast<const char*>(elf_mapped_base);

  if (cls == ELFCLASS32) {
    FindElfClassSection<ElfClass32>(elf_base, section_name, section_type,
                                    section_start, section_size);
    return *section_start != NULL;
  } else if (cls == ELFCLASS64) {
    FindElfClassSection<ElfClass64>(elf_base, section_name, section_type,
                                    section_start, section_size);
    return *section_start != NULL;
  }

  return false;
}

bool FindElfSegment(const void *elf_mapped_base,
                    uint32_t segment_type,
                    const void **segment_start,
                    size_t *segment_size,
                    int *elfclass) {
  assert(elf_mapped_base);
  assert(segment_start);
  assert(segment_size);

  *segment_start = NULL;
  *segment_size = 0;

  if (!IsValidElf(elf_mapped_base))
    return false;

  int cls = ElfClass(elf_mapped_base);
  if (elfclass) {
    *elfclass = cls;
  }

  const char* elf_base =
    static_cast<const char*>(elf_mapped_base);

  if (cls == ELFCLASS32) {
    FindElfClassSegment<ElfClass32>(elf_base, segment_type,
                                    segment_start, segment_size);
    return *segment_start != NULL;
  } else if (cls == ELFCLASS64) {
    FindElfClassSegment<ElfClass64>(elf_base, segment_type,
                                    segment_start, segment_size);
    return *segment_start != NULL;
  }

  return false;
}

}  // namespace google_breakpad
