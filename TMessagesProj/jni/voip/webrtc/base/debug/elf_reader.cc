// Copyright 2018 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

#include "base/debug/elf_reader.h"

#include <arpa/inet.h>
#include <elf.h>

#include "base/bits.h"
#include "base/containers/span.h"
#include "base/hash/sha1.h"
#include "base/strings/safe_sprintf.h"
#include "build/build_config.h"

// NOTE: This code may be used in crash handling code, so the implementation
// must avoid dynamic memory allocation or using data structures which rely on
// dynamic allocation.

namespace base {
namespace debug {
namespace {

#if __SIZEOF_POINTER__ == 4
using Ehdr = Elf32_Ehdr;
using Dyn = Elf32_Dyn;
using Half = Elf32_Half;
using Nhdr = Elf32_Nhdr;
using Word = Elf32_Word;
#else
using Ehdr = Elf64_Ehdr;
using Dyn = Elf64_Dyn;
using Half = Elf64_Half;
using Nhdr = Elf64_Nhdr;
using Word = Elf64_Word;
#endif

constexpr char kGnuNoteName[] = "GNU";

// Returns a pointer to the header of the ELF binary mapped into memory,
// or a null pointer if the header is invalid.
const Ehdr* GetElfHeader(const void* elf_mapped_base) {
  const char* elf_base = reinterpret_cast<const char*>(elf_mapped_base);
  if (strncmp(elf_base, ELFMAG, SELFMAG) != 0)
    return nullptr;

  const Ehdr* elf_header = reinterpret_cast<const Ehdr*>(elf_base);
  return elf_header;
}

// Returns the ELF base address that should be used as a starting point to
// access other segments.
const char* GetElfBaseVirtualAddress(const void* elf_mapped_base) {
  const char* elf_base = reinterpret_cast<const char*>(elf_mapped_base);
  for (const Phdr& header : GetElfProgramHeaders(elf_mapped_base)) {
    if (header.p_type == PT_LOAD) {
      size_t load_bias = static_cast<size_t>(header.p_vaddr);
      CHECK_GE(reinterpret_cast<uintptr_t>(elf_base), load_bias);
      return elf_base - load_bias;
    }
  }
  return elf_base;
}

}  // namespace

span<const Phdr> GetElfProgramHeaders(const void* elf_mapped_base) {
  // NOTE: Function should use async signal safe calls only.

  const char* elf_base = reinterpret_cast<const char*>(elf_mapped_base);
  const Ehdr* elf_header = GetElfHeader(elf_mapped_base);
  if (!elf_header)
    return {};

  return span<const Phdr>(
      reinterpret_cast<const Phdr*>(elf_base + elf_header->e_phoff),
      elf_header->e_phnum);
}

size_t ReadElfBuildId(const void* elf_mapped_base,
                      bool uppercase,
                      ElfBuildIdBuffer build_id) {
  // NOTE: Function should use async signal safe calls only.

  const char* elf_virtual_base = GetElfBaseVirtualAddress(elf_mapped_base);
  const Ehdr* elf_header = GetElfHeader(elf_mapped_base);
  if (!elf_header)
    return 0;

  for (const Phdr& header : GetElfProgramHeaders(elf_mapped_base)) {
    if (header.p_type != PT_NOTE)
      continue;

    // Look for a NT_GNU_BUILD_ID note with name == "GNU".
    const char* current_section = elf_virtual_base + header.p_vaddr;
    const char* section_end = current_section + header.p_memsz;
    const Nhdr* current_note = nullptr;
    bool found = false;
    while (current_section < section_end) {
      current_note = reinterpret_cast<const Nhdr*>(current_section);
      if (current_note->n_type == NT_GNU_BUILD_ID) {
        StringPiece note_name(current_section + sizeof(Nhdr),
                              current_note->n_namesz);
        // Explicit constructor is used to include the '\0' character.
        if (note_name == StringPiece(kGnuNoteName, sizeof(kGnuNoteName))) {
          found = true;
          break;
        }
      }

      size_t section_size = bits::Align(current_note->n_namesz, 4) +
                            bits::Align(current_note->n_descsz, 4) +
                            sizeof(Nhdr);
      if (section_size > static_cast<size_t>(section_end - current_section))
        return 0;
      current_section += section_size;
    }

    if (!found)
      continue;

    // Validate that the serialized build ID will fit inside |build_id|.
    size_t note_size = current_note->n_descsz;
    if ((note_size * 2) > kMaxBuildIdStringLength)
      continue;

    // Write out the build ID as a null-terminated hex string.
    const uint8_t* build_id_raw =
        reinterpret_cast<const uint8_t*>(current_note) + sizeof(Nhdr) +
        bits::Align(current_note->n_namesz, 4);
    size_t i = 0;
    for (i = 0; i < current_note->n_descsz; ++i) {
      strings::SafeSNPrintf(&build_id[i * 2], 3, (uppercase ? "%02X" : "%02x"),
                            build_id_raw[i]);
    }
    build_id[i * 2] = '\0';

    // Return the length of the string.
    return i * 2;
  }

  return 0;
}

Optional<StringPiece> ReadElfLibraryName(const void* elf_mapped_base) {
  // NOTE: Function should use async signal safe calls only.

  const char* elf_base = reinterpret_cast<const char*>(elf_mapped_base);
  const Ehdr* elf_header = GetElfHeader(elf_mapped_base);
  if (!elf_header)
    return {};

  for (const Phdr& header : GetElfProgramHeaders(elf_mapped_base)) {
    if (header.p_type != PT_DYNAMIC)
      continue;

    // Read through the ELF dynamic sections to find the string table and
    // SONAME offsets, which are used to compute the offset of the library
    // name string.
    const Dyn* dynamic_start =
        reinterpret_cast<const Dyn*>(elf_base + header.p_vaddr);
    const Dyn* dynamic_end = reinterpret_cast<const Dyn*>(
        elf_base + header.p_vaddr + header.p_memsz);
    Word soname_strtab_offset = 0;
    const char* strtab_addr = 0;
    for (const Dyn* dynamic_iter = dynamic_start; dynamic_iter < dynamic_end;
         ++dynamic_iter) {
      if (dynamic_iter->d_tag == DT_STRTAB) {
#if defined(OS_FUCHSIA) || defined(OS_ANDROID)
        // Fuchsia and Android executables are position-independent, so treat
        // pointers in the ELF header as offsets into the address space instead
        // of absolute addresses.
        strtab_addr = (size_t)dynamic_iter->d_un.d_ptr + (const char*)elf_base;
#else
        strtab_addr = (const char*)dynamic_iter->d_un.d_ptr;
#endif
      } else if (dynamic_iter->d_tag == DT_SONAME) {
        soname_strtab_offset = dynamic_iter->d_un.d_val;
      }
    }
    if (soname_strtab_offset && strtab_addr)
      return StringPiece(strtab_addr + soname_strtab_offset);
  }

  return nullopt;
}

}  // namespace debug
}  // namespace base
