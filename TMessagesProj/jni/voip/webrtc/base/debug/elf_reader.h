// Copyright 2018 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

#ifndef BASE_DEBUG_ELF_READER_H_
#define BASE_DEBUG_ELF_READER_H_

#include <elf.h>

#include "base/base_export.h"
#include "base/containers/span.h"
#include "base/hash/sha1.h"
#include "base/optional.h"
#include "base/strings/string_piece.h"

// Functions for querying metadata from ELF binaries.
// All functions require that the file be fully memory mapped.

#if __SIZEOF_POINTER__ == 4
using Phdr = Elf32_Phdr;
#else
using Phdr = Elf64_Phdr;
#endif

namespace base {
namespace debug {

// Hex-encodes the build ID from the ELF binary located at |elf_base|.
// Returns the length of the build ID in bytes, or zero if the build ID couldn't
// be read.
// When |uppercase| is |true|, the output string is written using uppercase hex
// characters. Otherwise, the output is lowercased.
constexpr size_t kMaxBuildIdStringLength = kSHA1Length * 2;
using ElfBuildIdBuffer = char[kMaxBuildIdStringLength + 1];
size_t BASE_EXPORT ReadElfBuildId(const void* elf_base,
                                  bool uppercase,
                                  ElfBuildIdBuffer build_id);

// Returns the library name from the ELF file mapped at |elf_base|.
// Returns an empty result if the name could not be read.
Optional<StringPiece> BASE_EXPORT ReadElfLibraryName(const void* elf_base);

// Returns a span of ELF program headers for the ELF file mapped at
// |elf_base|, or an empty span if the header couldn't be read.
span<const Phdr> BASE_EXPORT GetElfProgramHeaders(const void* elf_base);

}  // namespace debug
}  // namespace base

#endif  // BASE_DEBUG_ELF_READER_H_
