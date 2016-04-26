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
// file_id.cc: Return a unique identifier for a file
//
// See file_id.h for documentation
//

#include "common/linux/file_id.h"

#include <arpa/inet.h>
#include <assert.h>
#include <string.h>

#include <algorithm>

#include "common/linux/elf_gnu_compat.h"
#include "common/linux/elfutils.h"
#include "common/linux/linux_libc_support.h"
#include "common/linux/memory_mapped_file.h"
#include "third_party/lss/linux_syscall_support.h"

namespace google_breakpad {

FileID::FileID(const char* path) : path_(path) {}

// ELF note name and desc are 32-bits word padded.
#define NOTE_PADDING(a) ((a + 3) & ~3)

// These functions are also used inside the crashed process, so be safe
// and use the syscall/libc wrappers instead of direct syscalls or libc.

template<typename ElfClass>
static bool ElfClassBuildIDNoteIdentifier(const void *section, size_t length,
                                          uint8_t identifier[kMDGUIDSize]) {
  typedef typename ElfClass::Nhdr Nhdr;

  const void* section_end = reinterpret_cast<const char*>(section) + length;
  const Nhdr* note_header = reinterpret_cast<const Nhdr*>(section);
  while (reinterpret_cast<const void *>(note_header) < section_end) {
    if (note_header->n_type == NT_GNU_BUILD_ID)
      break;
    note_header = reinterpret_cast<const Nhdr*>(
                  reinterpret_cast<const char*>(note_header) + sizeof(Nhdr) +
                  NOTE_PADDING(note_header->n_namesz) +
                  NOTE_PADDING(note_header->n_descsz));
  }
  if (reinterpret_cast<const void *>(note_header) >= section_end ||
      note_header->n_descsz == 0) {
    return false;
  }

  const char* build_id = reinterpret_cast<const char*>(note_header) +
    sizeof(Nhdr) + NOTE_PADDING(note_header->n_namesz);
  // Copy as many bits of the build ID as will fit
  // into the GUID space.
  my_memset(identifier, 0, kMDGUIDSize);
  memcpy(identifier, build_id,
         std::min(kMDGUIDSize, (size_t)note_header->n_descsz));

  return true;
}

// Attempt to locate a .note.gnu.build-id section in an ELF binary
// and copy as many bytes of it as will fit into |identifier|.
static bool FindElfBuildIDNote(const void *elf_mapped_base,
                               uint8_t identifier[kMDGUIDSize]) {
  void* note_section;
  size_t note_size;
  int elfclass;
  if ((!FindElfSegment(elf_mapped_base, PT_NOTE,
                       (const void**)&note_section, &note_size, &elfclass) ||
      note_size == 0)  &&
      (!FindElfSection(elf_mapped_base, ".note.gnu.build-id", SHT_NOTE,
                       (const void**)&note_section, &note_size, &elfclass) ||
      note_size == 0)) {
    return false;
  }

  if (elfclass == ELFCLASS32) {
    return ElfClassBuildIDNoteIdentifier<ElfClass32>(note_section, note_size,
                                                     identifier);
  } else if (elfclass == ELFCLASS64) {
    return ElfClassBuildIDNoteIdentifier<ElfClass64>(note_section, note_size,
                                                     identifier);
  }

  return false;
}

// Attempt to locate the .text section of an ELF binary and generate
// a simple hash by XORing the first page worth of bytes into |identifier|.
static bool HashElfTextSection(const void *elf_mapped_base,
                               uint8_t identifier[kMDGUIDSize]) {
  void* text_section;
  size_t text_size;
  if (!FindElfSection(elf_mapped_base, ".text", SHT_PROGBITS,
                      (const void**)&text_section, &text_size, NULL) ||
      text_size == 0) {
    return false;
  }

  my_memset(identifier, 0, kMDGUIDSize);
  const uint8_t* ptr = reinterpret_cast<const uint8_t*>(text_section);
  const uint8_t* ptr_end = ptr + std::min(text_size, static_cast<size_t>(4096));
  while (ptr < ptr_end) {
    for (unsigned i = 0; i < kMDGUIDSize; i++)
      identifier[i] ^= ptr[i];
    ptr += kMDGUIDSize;
  }
  return true;
}

// static
bool FileID::ElfFileIdentifierFromMappedFile(const void* base,
                                             uint8_t identifier[kMDGUIDSize]) {
  // Look for a build id note first.
  if (FindElfBuildIDNote(base, identifier))
    return true;

  // Fall back on hashing the first page of the text section.
  return HashElfTextSection(base, identifier);
}

bool FileID::ElfFileIdentifier(uint8_t identifier[kMDGUIDSize]) {
  MemoryMappedFile mapped_file(path_.c_str(), 0);
  if (!mapped_file.data())  // Should probably check if size >= ElfW(Ehdr)?
    return false;

  return ElfFileIdentifierFromMappedFile(mapped_file.data(), identifier);
}

// static
void FileID::ConvertIdentifierToString(const uint8_t identifier[kMDGUIDSize],
                                       char* buffer, int buffer_length) {
  uint8_t identifier_swapped[kMDGUIDSize];

  // Endian-ness swap to match dump processor expectation.
  memcpy(identifier_swapped, identifier, kMDGUIDSize);
  uint32_t* data1 = reinterpret_cast<uint32_t*>(identifier_swapped);
  *data1 = htonl(*data1);
  uint16_t* data2 = reinterpret_cast<uint16_t*>(identifier_swapped + 4);
  *data2 = htons(*data2);
  uint16_t* data3 = reinterpret_cast<uint16_t*>(identifier_swapped + 6);
  *data3 = htons(*data3);

  int buffer_idx = 0;
  for (unsigned int idx = 0;
       (buffer_idx < buffer_length) && (idx < kMDGUIDSize);
       ++idx) {
    int hi = (identifier_swapped[idx] >> 4) & 0x0F;
    int lo = (identifier_swapped[idx]) & 0x0F;

    if (idx == 4 || idx == 6 || idx == 8 || idx == 10)
      buffer[buffer_idx++] = '-';

    buffer[buffer_idx++] = (hi >= 10) ? 'A' + hi - 10 : '0' + hi;
    buffer[buffer_idx++] = (lo >= 10) ? 'A' + lo - 10 : '0' + lo;
  }

  // NULL terminate
  buffer[(buffer_idx < buffer_length) ? buffer_idx : buffer_idx - 1] = 0;
}

}  // namespace google_breakpad
