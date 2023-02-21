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

// Original author: Ted Mielczarek <ted.mielczarek@gmail.com>

// dump_symbols_unittest.cc:
// Unittests for google_breakpad::DumpSymbols

#include <elf.h>
#include <link.h>
#include <stdio.h>

#include <sstream>
#include <vector>

#include "breakpad_googletest_includes.h"
#include "common/linux/dump_symbols.h"
#include "common/linux/synth_elf.h"
#include "common/module.h"
#include "common/using_std_string.h"

namespace google_breakpad {

bool ReadSymbolDataInternal(const uint8_t* obj_file,
                            const string& obj_filename,
                            const std::vector<string>& debug_dir,
                            const DumpOptions& options,
                            Module** module);

using google_breakpad::synth_elf::ELF;
using google_breakpad::synth_elf::StringTable;
using google_breakpad::synth_elf::SymbolTable;
using google_breakpad::test_assembler::kLittleEndian;
using google_breakpad::test_assembler::Section;
using std::stringstream;
using std::vector;
using ::testing::Test;

class DumpSymbols : public Test {
 public:
  void GetElfContents(ELF& elf) {
    string contents;
    ASSERT_TRUE(elf.GetContents(&contents));
    ASSERT_LT(0U, contents.size());

    elfdata_v.clear();
    elfdata_v.insert(elfdata_v.begin(), contents.begin(), contents.end());
    elfdata = &elfdata_v[0];
  }

  vector<uint8_t> elfdata_v;
  uint8_t* elfdata;
};

TEST_F(DumpSymbols, Invalid) {
  Elf32_Ehdr header;
  memset(&header, 0, sizeof(header));
  Module* module;
  DumpOptions options(ALL_SYMBOL_DATA, true);
  EXPECT_FALSE(ReadSymbolDataInternal(reinterpret_cast<uint8_t*>(&header),
                                      "foo",
                                      vector<string>(),
                                      options,
                                      &module));
}

TEST_F(DumpSymbols, SimplePublic32) {
  ELF elf(EM_386, ELFCLASS32, kLittleEndian);
  // Zero out text section for simplicity.
  Section text(kLittleEndian);
  text.Append(4096, 0);
  elf.AddSection(".text", text, SHT_PROGBITS);

  // Add a public symbol.
  StringTable table(kLittleEndian);
  SymbolTable syms(kLittleEndian, 4, table);
  syms.AddSymbol("superfunc", (uint32_t)0x1000, (uint32_t)0x10,
                 ELF32_ST_INFO(STB_GLOBAL, STT_FUNC),
                 SHN_UNDEF + 1);
  int index = elf.AddSection(".dynstr", table, SHT_STRTAB);
  elf.AddSection(".dynsym", syms,
                 SHT_DYNSYM,          // type
                 SHF_ALLOC,           // flags
                 0,                   // addr
                 index,               // link
                 sizeof(Elf32_Sym));  // entsize

  elf.Finish();
  GetElfContents(elf);

  Module* module;
  DumpOptions options(ALL_SYMBOL_DATA, true);
  EXPECT_TRUE(ReadSymbolDataInternal(elfdata,
                                     "foo",
                                     vector<string>(),
                                     options,
                                     &module));

  stringstream s;
  module->Write(s, ALL_SYMBOL_DATA);
  EXPECT_EQ("MODULE Linux x86 000000000000000000000000000000000 foo\n"
            "PUBLIC 1000 0 superfunc\n",
            s.str());
  delete module;
}

TEST_F(DumpSymbols, SimplePublic64) {
  ELF elf(EM_X86_64, ELFCLASS64, kLittleEndian);
  // Zero out text section for simplicity.
  Section text(kLittleEndian);
  text.Append(4096, 0);
  elf.AddSection(".text", text, SHT_PROGBITS);

  // Add a public symbol.
  StringTable table(kLittleEndian);
  SymbolTable syms(kLittleEndian, 8, table);
  syms.AddSymbol("superfunc", (uint64_t)0x1000, (uint64_t)0x10,
                 ELF64_ST_INFO(STB_GLOBAL, STT_FUNC),
                 SHN_UNDEF + 1);
  int index = elf.AddSection(".dynstr", table, SHT_STRTAB);
  elf.AddSection(".dynsym", syms,
                 SHT_DYNSYM,          // type
                 SHF_ALLOC,           // flags
                 0,                   // addr
                 index,               // link
                 sizeof(Elf64_Sym));  // entsize

  elf.Finish();
  GetElfContents(elf);

  Module* module;
  DumpOptions options(ALL_SYMBOL_DATA, true);
  EXPECT_TRUE(ReadSymbolDataInternal(elfdata,
                                     "foo",
                                     vector<string>(),
                                     options,
                                     &module));

  stringstream s;
  module->Write(s, ALL_SYMBOL_DATA);
  EXPECT_EQ("MODULE Linux x86_64 000000000000000000000000000000000 foo\n"
            "PUBLIC 1000 0 superfunc\n",
            s.str());
}

}  // namespace google_breakpad
