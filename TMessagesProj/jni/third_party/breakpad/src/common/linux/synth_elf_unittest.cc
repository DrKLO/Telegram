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

// synth_elf_unittest.cc:
// Unittests for google_breakpad::synth_elf::ELF

#include <elf.h>

#include "breakpad_googletest_includes.h"
#include "common/linux/elfutils.h"
#include "common/linux/synth_elf.h"
#include "common/using_std_string.h"

using google_breakpad::ElfClass32;
using google_breakpad::ElfClass64;
using google_breakpad::synth_elf::ELF;
using google_breakpad::synth_elf::Notes;
using google_breakpad::synth_elf::Section;
using google_breakpad::synth_elf::StringTable;
using google_breakpad::synth_elf::SymbolTable;
using google_breakpad::test_assembler::Endianness;
using google_breakpad::test_assembler::kBigEndian;
using google_breakpad::test_assembler::kLittleEndian;
using google_breakpad::test_assembler::Label;
using ::testing::Test;
using ::testing::Types;

class StringTableTest : public Test {
public:
  StringTableTest() : table(kLittleEndian) {}

  StringTable table;
};

TEST_F(StringTableTest, Empty) {
  EXPECT_EQ(1U, table.Size());
  string contents;
  ASSERT_TRUE(table.GetContents(&contents));
  const char* kExpectedContents = "\0";
  EXPECT_EQ(0, memcmp(kExpectedContents,
                      contents.c_str(),
                      contents.size()));
  ASSERT_TRUE(table.empty_string.IsKnownConstant());
  EXPECT_EQ(0U, table.empty_string.Value());
}

TEST_F(StringTableTest, Basic) {
  const string s1("table fills with strings");
  const string s2("offsets preserved as labels");
  const string s3("verified with tests");
  const char* kExpectedContents = 
    "\0table fills with strings\0"
    "offsets preserved as labels\0"
    "verified with tests\0";
  Label l1(table.Add(s1));
  Label l2(table.Add(s2));
  Label l3(table.Add(s3));
  string contents;
  ASSERT_TRUE(table.GetContents(&contents));
  EXPECT_EQ(0, memcmp(kExpectedContents,
                      contents.c_str(),
                      contents.size()));
  // empty_string is at zero, other strings start at 1.
  ASSERT_TRUE(l1.IsKnownConstant());
  EXPECT_EQ(1U, l1.Value());
  // Each string has an extra byte for a trailing null.
  EXPECT_EQ(1 + s1.length() + 1, l2.Value());
  EXPECT_EQ(1 + s1.length() + 1 + s2.length() + 1, l3.Value());
}

TEST_F(StringTableTest, Duplicates) {
  const string s1("string 1");
  const string s2("string 2");
  const string s3("");
  const char* kExpectedContents = "\0string 1\0string 2\0";
  Label l1(table.Add(s1));
  Label l2(table.Add(s2));
  // Adding strings twice should return the same Label.
  Label l3(table.Add(s3));
  Label l4(table.Add(s2));
  string contents;
  ASSERT_TRUE(table.GetContents(&contents));
  EXPECT_EQ(0, memcmp(kExpectedContents,
                      contents.c_str(),
                      contents.size()));
  EXPECT_EQ(0U, table.empty_string.Value());
  EXPECT_EQ(table.empty_string.Value(), l3.Value());
  EXPECT_EQ(l2.Value(), l4.Value());
}

class SymbolTableTest : public Test {};

TEST_F(SymbolTableTest, Simple32) {
  StringTable table(kLittleEndian);
  SymbolTable syms(kLittleEndian, 4, table);

  const string kFuncName1 = "superfunc";
  const uint32_t kFuncAddr1 = 0x10001000;
  const uint32_t kFuncSize1 = 0x10;
  const string kFuncName2 = "awesomefunc";
  const uint32_t kFuncAddr2 = 0x20002000;
  const uint32_t kFuncSize2 = 0x2f;
  const string kFuncName3 = "megafunc";
  const uint32_t kFuncAddr3 = 0x30003000;
  const uint32_t kFuncSize3 = 0x3c;

  syms.AddSymbol(kFuncName1, kFuncAddr1, kFuncSize1,
                 ELF32_ST_INFO(STB_GLOBAL, STT_FUNC),
                 SHN_UNDEF + 1);
  syms.AddSymbol(kFuncName2, kFuncAddr2, kFuncSize2,
                 ELF32_ST_INFO(STB_LOCAL, STT_FUNC),
                 SHN_UNDEF + 2);
  syms.AddSymbol(kFuncName3, kFuncAddr3, kFuncSize3,
                 ELF32_ST_INFO(STB_LOCAL, STT_FUNC),
                 SHN_UNDEF + 3);

  const char kExpectedStringTable[] = "\0superfunc\0awesomefunc\0megafunc";
  const size_t kExpectedStringTableSize = sizeof(kExpectedStringTable);
  EXPECT_EQ(kExpectedStringTableSize, table.Size());
  string table_contents;
  table.GetContents(&table_contents);
  EXPECT_EQ(0, memcmp(kExpectedStringTable,
                      table_contents.c_str(),
                      table_contents.size()));

  const uint8_t kExpectedSymbolContents[] = {
    // Symbol 1
    0x01, 0x00, 0x00, 0x00, // name
    0x00, 0x10, 0x00, 0x10, // value
    0x10, 0x00, 0x00, 0x00, // size
    ELF32_ST_INFO(STB_GLOBAL, STT_FUNC), // info
    0x00, // other
    0x01, 0x00, // shndx
    // Symbol 2
    0x0B, 0x00, 0x00, 0x00, // name
    0x00, 0x20, 0x00, 0x20, // value
    0x2f, 0x00, 0x00, 0x00, // size
    ELF32_ST_INFO(STB_LOCAL, STT_FUNC), // info
    0x00, // other
    0x02, 0x00, // shndx
    // Symbol 3
    0x17, 0x00, 0x00, 0x00, // name
    0x00, 0x30, 0x00, 0x30, // value
    0x3c, 0x00, 0x00, 0x00, // size
    ELF32_ST_INFO(STB_LOCAL, STT_FUNC), // info
    0x00, // other
    0x03, 0x00, // shndx
  };
  const size_t kExpectedSymbolSize = sizeof(kExpectedSymbolContents);
  EXPECT_EQ(kExpectedSymbolSize, syms.Size());

  string symbol_contents;
  syms.GetContents(&symbol_contents);
  EXPECT_EQ(0, memcmp(kExpectedSymbolContents,
                      symbol_contents.c_str(),
                      symbol_contents.size()));
}

template<typename ElfClass>
class BasicElf : public Test {};

// Doesn't seem worthwhile writing the tests to be endian-independent
// when they're unlikely to ever be run on big-endian systems.
#if defined(__i386__) || defined(__x86_64__)

typedef Types<ElfClass32, ElfClass64> ElfClasses;

TYPED_TEST_CASE(BasicElf, ElfClasses);

TYPED_TEST(BasicElf, EmptyLE) {
  typedef typename TypeParam::Ehdr Ehdr;
  typedef typename TypeParam::Phdr Phdr;
  typedef typename TypeParam::Shdr Shdr;
  const size_t kStringTableSize = sizeof("\0.shstrtab");
  const size_t kStringTableAlign = 4 - kStringTableSize % 4;
  const size_t kExpectedSize = sizeof(Ehdr) +
    // Two sections, SHT_NULL + the section header string table.
    2 * sizeof(Shdr) +
    kStringTableSize + kStringTableAlign;

  // It doesn't really matter that the machine type is right for the class.
  ELF elf(EM_386, TypeParam::kClass, kLittleEndian);
  elf.Finish();
  EXPECT_EQ(kExpectedSize, elf.Size());

  string contents;
  ASSERT_TRUE(elf.GetContents(&contents));
  ASSERT_EQ(kExpectedSize, contents.size());
  const Ehdr* header =
    reinterpret_cast<const Ehdr*>(contents.data());
  const uint8_t kIdent[] = {
    ELFMAG0, ELFMAG1, ELFMAG2, ELFMAG3,
    TypeParam::kClass, ELFDATA2LSB, EV_CURRENT, ELFOSABI_SYSV,
    0, 0, 0, 0, 0, 0, 0, 0
  };
  EXPECT_EQ(0, memcmp(kIdent, header->e_ident, sizeof(kIdent)));
  EXPECT_EQ(ET_EXEC, header->e_type);
  EXPECT_EQ(EM_386, header->e_machine);
  EXPECT_EQ(static_cast<unsigned int>(EV_CURRENT), header->e_version);
  EXPECT_EQ(0U, header->e_entry);
  EXPECT_EQ(0U, header->e_phoff);
  EXPECT_EQ(sizeof(Ehdr) + kStringTableSize + kStringTableAlign,
            header->e_shoff);
  EXPECT_EQ(0U, header->e_flags);
  EXPECT_EQ(sizeof(Ehdr), header->e_ehsize);
  EXPECT_EQ(sizeof(Phdr), header->e_phentsize);
  EXPECT_EQ(0, header->e_phnum);
  EXPECT_EQ(sizeof(Shdr), header->e_shentsize);
  EXPECT_EQ(2, header->e_shnum);
  EXPECT_EQ(1, header->e_shstrndx);

  const Shdr* shdr =
    reinterpret_cast<const Shdr*>(contents.data() + header->e_shoff);
  EXPECT_EQ(0U, shdr[0].sh_name);
  EXPECT_EQ(static_cast<unsigned int>(SHT_NULL), shdr[0].sh_type);
  EXPECT_EQ(0U, shdr[0].sh_flags);
  EXPECT_EQ(0U, shdr[0].sh_addr);
  EXPECT_EQ(0U, shdr[0].sh_offset);
  EXPECT_EQ(0U, shdr[0].sh_size);
  EXPECT_EQ(0U, shdr[0].sh_link);
  EXPECT_EQ(0U, shdr[0].sh_info);
  EXPECT_EQ(0U, shdr[0].sh_addralign);
  EXPECT_EQ(0U, shdr[0].sh_entsize);

  EXPECT_EQ(1U, shdr[1].sh_name);
  EXPECT_EQ(static_cast<unsigned int>(SHT_STRTAB), shdr[1].sh_type);
  EXPECT_EQ(0U, shdr[1].sh_flags);
  EXPECT_EQ(0U, shdr[1].sh_addr);
  EXPECT_EQ(sizeof(Ehdr), shdr[1].sh_offset);
  EXPECT_EQ(kStringTableSize, shdr[1].sh_size);
  EXPECT_EQ(0U, shdr[1].sh_link);
  EXPECT_EQ(0U, shdr[1].sh_info);
  EXPECT_EQ(0U, shdr[1].sh_addralign);
  EXPECT_EQ(0U, shdr[1].sh_entsize);
}

TYPED_TEST(BasicElf, BasicLE) {
  typedef typename TypeParam::Ehdr Ehdr;
  typedef typename TypeParam::Phdr Phdr;
  typedef typename TypeParam::Shdr Shdr;
  const size_t kStringTableSize = sizeof("\0.text\0.bss\0.shstrtab");
  const size_t kStringTableAlign = 4 - kStringTableSize % 4;
  const size_t kExpectedSize = sizeof(Ehdr) +
    // Four sections, SHT_NULL + the section header string table +
    // 4096 bytes of the size-aligned .text section + one program header.
    sizeof(Phdr) + 4 * sizeof(Shdr) + 4096 +
    kStringTableSize + kStringTableAlign;

  // It doesn't really matter that the machine type is right for the class.
  ELF elf(EM_386, TypeParam::kClass, kLittleEndian);
  Section text(kLittleEndian);
  text.Append(4094, 0);
  int text_idx = elf.AddSection(".text", text, SHT_PROGBITS);
  Section bss(kLittleEndian);
  bss.Append(16, 0);
  int bss_idx = elf.AddSection(".bss", bss, SHT_NOBITS);
  elf.AddSegment(text_idx, bss_idx, PT_LOAD);
  elf.Finish();
  EXPECT_EQ(kExpectedSize, elf.Size());

  string contents;
  ASSERT_TRUE(elf.GetContents(&contents));
  ASSERT_EQ(kExpectedSize, contents.size());
  const Ehdr* header =
    reinterpret_cast<const Ehdr*>(contents.data());
  const uint8_t kIdent[] = {
    ELFMAG0, ELFMAG1, ELFMAG2, ELFMAG3,
    TypeParam::kClass, ELFDATA2LSB, EV_CURRENT, ELFOSABI_SYSV,
    0, 0, 0, 0, 0, 0, 0, 0
  };
  EXPECT_EQ(0, memcmp(kIdent, header->e_ident, sizeof(kIdent)));
  EXPECT_EQ(ET_EXEC, header->e_type);
  EXPECT_EQ(EM_386, header->e_machine);
  EXPECT_EQ(static_cast<unsigned int>(EV_CURRENT), header->e_version);
  EXPECT_EQ(0U, header->e_entry);
  EXPECT_EQ(sizeof(Ehdr), header->e_phoff);
  EXPECT_EQ(sizeof(Ehdr) + sizeof(Phdr) + 4096 + kStringTableSize +
            kStringTableAlign, header->e_shoff);
  EXPECT_EQ(0U, header->e_flags);
  EXPECT_EQ(sizeof(Ehdr), header->e_ehsize);
  EXPECT_EQ(sizeof(Phdr), header->e_phentsize);
  EXPECT_EQ(1, header->e_phnum);
  EXPECT_EQ(sizeof(Shdr), header->e_shentsize);
  EXPECT_EQ(4, header->e_shnum);
  EXPECT_EQ(3, header->e_shstrndx);

  const Shdr* shdr =
    reinterpret_cast<const Shdr*>(contents.data() + header->e_shoff);
  EXPECT_EQ(0U, shdr[0].sh_name);
  EXPECT_EQ(static_cast<unsigned int>(SHT_NULL), shdr[0].sh_type);
  EXPECT_EQ(0U, shdr[0].sh_flags);
  EXPECT_EQ(0U, shdr[0].sh_addr);
  EXPECT_EQ(0U, shdr[0].sh_offset);
  EXPECT_EQ(0U, shdr[0].sh_size);
  EXPECT_EQ(0U, shdr[0].sh_link);
  EXPECT_EQ(0U, shdr[0].sh_info);
  EXPECT_EQ(0U, shdr[0].sh_addralign);
  EXPECT_EQ(0U, shdr[0].sh_entsize);

  EXPECT_EQ(1U, shdr[1].sh_name);
  EXPECT_EQ(static_cast<unsigned int>(SHT_PROGBITS), shdr[1].sh_type);
  EXPECT_EQ(0U, shdr[1].sh_flags);
  EXPECT_EQ(0U, shdr[1].sh_addr);
  EXPECT_EQ(sizeof(Ehdr) + sizeof(Phdr), shdr[1].sh_offset);
  EXPECT_EQ(4094U, shdr[1].sh_size);
  EXPECT_EQ(0U, shdr[1].sh_link);
  EXPECT_EQ(0U, shdr[1].sh_info);
  EXPECT_EQ(0U, shdr[1].sh_addralign);
  EXPECT_EQ(0U, shdr[1].sh_entsize);

  EXPECT_EQ(sizeof("\0.text"), shdr[2].sh_name);
  EXPECT_EQ(static_cast<unsigned int>(SHT_NOBITS), shdr[2].sh_type);
  EXPECT_EQ(0U, shdr[2].sh_flags);
  EXPECT_EQ(0U, shdr[2].sh_addr);
  EXPECT_EQ(0U, shdr[2].sh_offset);
  EXPECT_EQ(16U, shdr[2].sh_size);
  EXPECT_EQ(0U, shdr[2].sh_link);
  EXPECT_EQ(0U, shdr[2].sh_info);
  EXPECT_EQ(0U, shdr[2].sh_addralign);
  EXPECT_EQ(0U, shdr[2].sh_entsize);

  EXPECT_EQ(sizeof("\0.text\0.bss"), shdr[3].sh_name);
  EXPECT_EQ(static_cast<unsigned int>(SHT_STRTAB), shdr[3].sh_type);
  EXPECT_EQ(0U, shdr[3].sh_flags);
  EXPECT_EQ(0U, shdr[3].sh_addr);
  EXPECT_EQ(sizeof(Ehdr) + sizeof(Phdr) + 4096, shdr[3].sh_offset);
  EXPECT_EQ(kStringTableSize, shdr[3].sh_size);
  EXPECT_EQ(0U, shdr[3].sh_link);
  EXPECT_EQ(0U, shdr[3].sh_info);
  EXPECT_EQ(0U, shdr[3].sh_addralign);
  EXPECT_EQ(0U, shdr[3].sh_entsize);

  const Phdr* phdr =
    reinterpret_cast<const Phdr*>(contents.data() + header->e_phoff);
  EXPECT_EQ(static_cast<unsigned int>(PT_LOAD), phdr->p_type);
  EXPECT_EQ(sizeof(Ehdr) + sizeof(Phdr), phdr->p_offset);
  EXPECT_EQ(0U, phdr->p_vaddr);
  EXPECT_EQ(0U, phdr->p_paddr);
  EXPECT_EQ(4096U, phdr->p_filesz);
  EXPECT_EQ(4096U + 16U, phdr->p_memsz);
  EXPECT_EQ(0U, phdr->p_flags);
  EXPECT_EQ(0U, phdr->p_align);
}

class ElfNotesTest : public Test {};

TEST_F(ElfNotesTest, Empty) {
  Notes notes(kLittleEndian);
  string contents;
  ASSERT_TRUE(notes.GetContents(&contents));
  EXPECT_EQ(0U, contents.size());
}

TEST_F(ElfNotesTest, Notes) {
  Notes notes(kLittleEndian);
  notes.AddNote(1, "Linux", reinterpret_cast<const uint8_t *>("\x42\x02\0\0"),
                4);
  notes.AddNote(2, "a", reinterpret_cast<const uint8_t *>("foobar"),
                sizeof("foobar") - 1);

  const uint8_t kExpectedNotesContents[] = {
    // Note 1
    0x06, 0x00, 0x00, 0x00, // name size, including terminating zero
    0x04, 0x00, 0x00, 0x00, // desc size
    0x01, 0x00, 0x00, 0x00, // type
    'L', 'i', 'n', 'u', 'x', 0x00, 0x00, 0x00, // padded "Linux"
    0x42, 0x02, 0x00, 0x00, // desc
    // Note 2
    0x02, 0x00, 0x00, 0x00, // name size
    0x06, 0x00, 0x00, 0x00, // desc size
    0x02, 0x00, 0x00, 0x00, // type
    'a',  0x00, 0x00, 0x00, // padded "a"
    'f', 'o', 'o', 'b', 'a', 'r', 0x00, 0x00, // padded "foobar"
  };
  const size_t kExpectedNotesSize = sizeof(kExpectedNotesContents);
  EXPECT_EQ(kExpectedNotesSize, notes.Size());

  string notes_contents;
  ASSERT_TRUE(notes.GetContents(&notes_contents));
  EXPECT_EQ(0, memcmp(kExpectedNotesContents,
                      notes_contents.data(),
                      notes_contents.size()));
}

#endif  // defined(__i386__) || defined(__x86_64__)
