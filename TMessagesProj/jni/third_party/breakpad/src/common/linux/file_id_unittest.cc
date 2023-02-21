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

// Unit tests for FileID

#include <elf.h>
#include <stdlib.h>

#include <string>

#include "common/linux/elf_gnu_compat.h"
#include "common/linux/elfutils.h"
#include "common/linux/file_id.h"
#include "common/linux/safe_readlink.h"
#include "common/linux/synth_elf.h"
#include "common/test_assembler.h"
#include "common/tests/auto_tempdir.h"
#include "common/using_std_string.h"
#include "breakpad_googletest_includes.h"

using namespace google_breakpad;
using google_breakpad::ElfClass32;
using google_breakpad::ElfClass64;
using google_breakpad::SafeReadLink;
using google_breakpad::synth_elf::ELF;
using google_breakpad::synth_elf::Notes;
using google_breakpad::test_assembler::kLittleEndian;
using google_breakpad::test_assembler::Section;
using ::testing::Types;

namespace {

// Simply calling Section::Append(size, byte) produces a uninteresting pattern
// that tends to get hashed to 0000...0000. This populates the section with
// data to produce better hashes.
void PopulateSection(Section* section, int size, int prime_number) {
  for (int i = 0; i < size; i++)
    section->Append(1, (i % prime_number) % 256);
}

}  // namespace

#ifndef __ANDROID__
// This test is disabled on Android: It will always fail, since there is no
// 'strip' binary installed on test devices.
TEST(FileIDStripTest, StripSelf) {
  // Calculate the File ID of this binary using
  // FileID::ElfFileIdentifier, then make a copy of this binary,
  // strip it, and ensure that the result is the same.
  char exe_name[PATH_MAX];
  ASSERT_TRUE(SafeReadLink("/proc/self/exe", exe_name));

  // copy our binary to a temp file, and strip it
  AutoTempDir temp_dir;
  string templ = temp_dir.path() + "/file-id-unittest";
  char cmdline[4096];
  sprintf(cmdline, "cp \"%s\" \"%s\"", exe_name, templ.c_str());
  ASSERT_EQ(0, system(cmdline)) << "Failed to execute: " << cmdline;
  sprintf(cmdline, "chmod u+w \"%s\"", templ.c_str());
  ASSERT_EQ(0, system(cmdline)) << "Failed to execute: " << cmdline;
  sprintf(cmdline, "strip \"%s\"", templ.c_str());
  ASSERT_EQ(0, system(cmdline)) << "Failed to execute: " << cmdline;

  uint8_t identifier1[sizeof(MDGUID)];
  uint8_t identifier2[sizeof(MDGUID)];
  FileID fileid1(exe_name);
  EXPECT_TRUE(fileid1.ElfFileIdentifier(identifier1));
  FileID fileid2(templ.c_str());
  EXPECT_TRUE(fileid2.ElfFileIdentifier(identifier2));
  char identifier_string1[37];
  char identifier_string2[37];
  FileID::ConvertIdentifierToString(identifier1, identifier_string1,
                                    37);
  FileID::ConvertIdentifierToString(identifier2, identifier_string2,
                                    37);
  EXPECT_STREQ(identifier_string1, identifier_string2);
}
#endif  // !__ANDROID__

template<typename ElfClass>
class FileIDTest : public testing::Test {
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

typedef Types<ElfClass32, ElfClass64> ElfClasses;

TYPED_TEST_CASE(FileIDTest, ElfClasses);

TYPED_TEST(FileIDTest, ElfClass) {
  uint8_t identifier[sizeof(MDGUID)];
  const char expected_identifier_string[] =
      "80808080-8080-0000-0000-008080808080";
  char identifier_string[sizeof(expected_identifier_string)];
  const size_t kTextSectionSize = 128;

  ELF elf(EM_386, TypeParam::kClass, kLittleEndian);
  Section text(kLittleEndian);
  for (size_t i = 0; i < kTextSectionSize; ++i) {
    text.D8(i * 3);
  }
  elf.AddSection(".text", text, SHT_PROGBITS);
  elf.Finish();
  this->GetElfContents(elf);

  EXPECT_TRUE(FileID::ElfFileIdentifierFromMappedFile(this->elfdata,
                                                      identifier));

  FileID::ConvertIdentifierToString(identifier, identifier_string,
                                    sizeof(identifier_string));
  EXPECT_STREQ(expected_identifier_string, identifier_string);
}

TYPED_TEST(FileIDTest, BuildID) {
  const uint8_t kExpectedIdentifier[sizeof(MDGUID)] =
    {0x00, 0x01, 0x02, 0x03, 0x04, 0x05, 0x06, 0x07,
     0x08, 0x09, 0x0A, 0x0B, 0x0C, 0x0D, 0x0E, 0x0F};
  char expected_identifier_string[] =
    "00000000-0000-0000-0000-000000000000";
  FileID::ConvertIdentifierToString(kExpectedIdentifier,
                                    expected_identifier_string,
                                    sizeof(expected_identifier_string));

  uint8_t identifier[sizeof(MDGUID)];
  char identifier_string[sizeof(expected_identifier_string)];

  ELF elf(EM_386, TypeParam::kClass, kLittleEndian);
  Section text(kLittleEndian);
  text.Append(4096, 0);
  elf.AddSection(".text", text, SHT_PROGBITS);
  Notes notes(kLittleEndian);
  notes.AddNote(NT_GNU_BUILD_ID, "GNU", kExpectedIdentifier,
                sizeof(kExpectedIdentifier));
  elf.AddSection(".note.gnu.build-id", notes, SHT_NOTE);
  elf.Finish();
  this->GetElfContents(elf);

  EXPECT_TRUE(FileID::ElfFileIdentifierFromMappedFile(this->elfdata,
                                                      identifier));

  FileID::ConvertIdentifierToString(identifier, identifier_string,
                                    sizeof(identifier_string));
  EXPECT_STREQ(expected_identifier_string, identifier_string);
}

TYPED_TEST(FileIDTest, BuildIDPH) {
  const uint8_t kExpectedIdentifier[sizeof(MDGUID)] =
    {0x00, 0x01, 0x02, 0x03, 0x04, 0x05, 0x06, 0x07,
     0x08, 0x09, 0x0A, 0x0B, 0x0C, 0x0D, 0x0E, 0x0F};
  char expected_identifier_string[] =
    "00000000-0000-0000-0000-000000000000";
  FileID::ConvertIdentifierToString(kExpectedIdentifier,
                                    expected_identifier_string,
                                    sizeof(expected_identifier_string));

  uint8_t identifier[sizeof(MDGUID)];
  char identifier_string[sizeof(expected_identifier_string)];

  ELF elf(EM_386, TypeParam::kClass, kLittleEndian);
  Section text(kLittleEndian);
  text.Append(4096, 0);
  elf.AddSection(".text", text, SHT_PROGBITS);
  Notes notes(kLittleEndian);
  notes.AddNote(0, "Linux",
                reinterpret_cast<const uint8_t *>("\0x42\0x02\0\0"), 4);
  notes.AddNote(NT_GNU_BUILD_ID, "GNU", kExpectedIdentifier,
                sizeof(kExpectedIdentifier));
  int note_idx = elf.AddSection(".note", notes, SHT_NOTE);
  elf.AddSegment(note_idx, note_idx, PT_NOTE);
  elf.Finish();
  this->GetElfContents(elf);

  EXPECT_TRUE(FileID::ElfFileIdentifierFromMappedFile(this->elfdata,
                                                      identifier));

  FileID::ConvertIdentifierToString(identifier, identifier_string,
                                    sizeof(identifier_string));
  EXPECT_STREQ(expected_identifier_string, identifier_string);
}

// Test to make sure two files with different text sections produce
// different hashes when not using a build id.
TYPED_TEST(FileIDTest, UniqueHashes) {
  char identifier_string_1[] =
    "00000000-0000-0000-0000-000000000000";
  char identifier_string_2[] =
    "00000000-0000-0000-0000-000000000000";
  uint8_t identifier_1[sizeof(MDGUID)];
  uint8_t identifier_2[sizeof(MDGUID)];

  {
    ELF elf1(EM_386, TypeParam::kClass, kLittleEndian);
    Section foo_1(kLittleEndian);
    PopulateSection(&foo_1, 32, 5);
    elf1.AddSection(".foo", foo_1, SHT_PROGBITS);
    Section text_1(kLittleEndian);
    PopulateSection(&text_1, 4096, 17);
    elf1.AddSection(".text", text_1, SHT_PROGBITS);
    elf1.Finish();
    this->GetElfContents(elf1);
  }

  EXPECT_TRUE(FileID::ElfFileIdentifierFromMappedFile(this->elfdata,
                                                      identifier_1));
  FileID::ConvertIdentifierToString(identifier_1, identifier_string_1,
                                    sizeof(identifier_string_1));

  {
    ELF elf2(EM_386, TypeParam::kClass, kLittleEndian);
    Section text_2(kLittleEndian);
    Section foo_2(kLittleEndian);
    PopulateSection(&foo_2, 32, 5);
    elf2.AddSection(".foo", foo_2, SHT_PROGBITS);
    PopulateSection(&text_2, 4096, 31);
    elf2.AddSection(".text", text_2, SHT_PROGBITS);
    elf2.Finish();
    this->GetElfContents(elf2);
  }

  EXPECT_TRUE(FileID::ElfFileIdentifierFromMappedFile(this->elfdata,
                                                      identifier_2));
  FileID::ConvertIdentifierToString(identifier_2, identifier_string_2,
                                    sizeof(identifier_string_2));

  EXPECT_STRNE(identifier_string_1, identifier_string_2);
}
