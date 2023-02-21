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

// bytereader_unittest.cc: Unit tests for dwarf2reader::ByteReader

#include <string>

#include "breakpad_googletest_includes.h"
#include "common/dwarf/bytereader.h"
#include "common/dwarf/bytereader-inl.h"
#include "common/dwarf/cfi_assembler.h"
#include "common/using_std_string.h"

using dwarf2reader::ByteReader;
using dwarf2reader::DwarfPointerEncoding;
using dwarf2reader::ENDIANNESS_BIG;
using dwarf2reader::ENDIANNESS_LITTLE;
using google_breakpad::CFISection;
using google_breakpad::test_assembler::Label;
using google_breakpad::test_assembler::kBigEndian;
using google_breakpad::test_assembler::kLittleEndian;
using google_breakpad::test_assembler::Section;
using testing::Test;

struct ReaderFixture {
  string contents;
  size_t pointer_size;
};

class Reader: public ReaderFixture, public Test { };
class ReaderDeathTest: public ReaderFixture, public Test { };

TEST_F(Reader, SimpleConstructor) {
  ByteReader reader(ENDIANNESS_BIG);
  reader.SetAddressSize(4);
  CFISection section(kBigEndian, 4);
  section
    .D8(0xc0)
    .D16(0xcf0d)
    .D32(0x96fdd219)
    .D64(0xbbf55fef0825f117ULL)
    .ULEB128(0xa0927048ba8121afULL)
    .LEB128(-0x4f337badf4483f83LL)
    .D32(0xfec319c9);
  ASSERT_TRUE(section.GetContents(&contents));
  const char *data = contents.data();
  EXPECT_EQ(0xc0U, reader.ReadOneByte(data));
  EXPECT_EQ(0xcf0dU, reader.ReadTwoBytes(data + 1));
  EXPECT_EQ(0x96fdd219U, reader.ReadFourBytes(data + 3));
  EXPECT_EQ(0xbbf55fef0825f117ULL, reader.ReadEightBytes(data + 7));
  size_t leb128_size;
  EXPECT_EQ(0xa0927048ba8121afULL,
            reader.ReadUnsignedLEB128(data + 15, &leb128_size));
  EXPECT_EQ(10U, leb128_size);
  EXPECT_EQ(-0x4f337badf4483f83LL,
            reader.ReadSignedLEB128(data + 25, &leb128_size));
  EXPECT_EQ(10U, leb128_size);
  EXPECT_EQ(0xfec319c9, reader.ReadAddress(data + 35));
}

TEST_F(Reader, ValidEncodings) {
  ByteReader reader(ENDIANNESS_LITTLE);
  EXPECT_TRUE(reader.ValidEncoding(
      DwarfPointerEncoding(dwarf2reader::DW_EH_PE_absptr)));
  EXPECT_TRUE(reader.ValidEncoding(
      DwarfPointerEncoding(dwarf2reader::DW_EH_PE_omit)));
  EXPECT_TRUE(reader.ValidEncoding(
      DwarfPointerEncoding(dwarf2reader::DW_EH_PE_aligned)));
  EXPECT_TRUE(reader.ValidEncoding(
      DwarfPointerEncoding(dwarf2reader::DW_EH_PE_uleb128)));
  EXPECT_TRUE(reader.ValidEncoding(
      DwarfPointerEncoding(dwarf2reader::DW_EH_PE_udata2)));
  EXPECT_TRUE(reader.ValidEncoding(
      DwarfPointerEncoding(dwarf2reader::DW_EH_PE_udata4)));
  EXPECT_TRUE(reader.ValidEncoding(
      DwarfPointerEncoding(dwarf2reader::DW_EH_PE_udata8)));
  EXPECT_TRUE(reader.ValidEncoding(
      DwarfPointerEncoding(dwarf2reader::DW_EH_PE_sleb128)));
  EXPECT_TRUE(reader.ValidEncoding(
      DwarfPointerEncoding(dwarf2reader::DW_EH_PE_sdata2)));
  EXPECT_TRUE(reader.ValidEncoding(
      DwarfPointerEncoding(dwarf2reader::DW_EH_PE_sdata4)));
  EXPECT_TRUE(reader.ValidEncoding(
      DwarfPointerEncoding(dwarf2reader::DW_EH_PE_sdata8)));
  EXPECT_TRUE(reader.ValidEncoding(
      DwarfPointerEncoding(dwarf2reader::DW_EH_PE_absptr |
                           dwarf2reader::DW_EH_PE_pcrel)));
  EXPECT_TRUE(reader.ValidEncoding(
      DwarfPointerEncoding(dwarf2reader::DW_EH_PE_uleb128 |
                           dwarf2reader::DW_EH_PE_pcrel)));
  EXPECT_TRUE(reader.ValidEncoding(
      DwarfPointerEncoding(dwarf2reader::DW_EH_PE_udata2 |
                           dwarf2reader::DW_EH_PE_pcrel)));
  EXPECT_TRUE(reader.ValidEncoding(
      DwarfPointerEncoding(dwarf2reader::DW_EH_PE_udata4 |
                           dwarf2reader::DW_EH_PE_pcrel)));
  EXPECT_TRUE(reader.ValidEncoding(
      DwarfPointerEncoding(dwarf2reader::DW_EH_PE_udata8 |
                           dwarf2reader::DW_EH_PE_pcrel)));
  EXPECT_TRUE(reader.ValidEncoding(
      DwarfPointerEncoding(dwarf2reader::DW_EH_PE_sleb128 |
                           dwarf2reader::DW_EH_PE_pcrel)));
  EXPECT_TRUE(reader.ValidEncoding(
      DwarfPointerEncoding(dwarf2reader::DW_EH_PE_sdata2 |
                           dwarf2reader::DW_EH_PE_pcrel)));
  EXPECT_TRUE(reader.ValidEncoding(
      DwarfPointerEncoding(dwarf2reader::DW_EH_PE_sdata4 |
                           dwarf2reader::DW_EH_PE_pcrel)));
  EXPECT_TRUE(reader.ValidEncoding(
      DwarfPointerEncoding(dwarf2reader::DW_EH_PE_sdata8 |
                           dwarf2reader::DW_EH_PE_pcrel)));
  EXPECT_TRUE(reader.ValidEncoding(
      DwarfPointerEncoding(dwarf2reader::DW_EH_PE_absptr |
                           dwarf2reader::DW_EH_PE_textrel)));
  EXPECT_TRUE(reader.ValidEncoding(
      DwarfPointerEncoding(dwarf2reader::DW_EH_PE_uleb128 |
                           dwarf2reader::DW_EH_PE_textrel)));
  EXPECT_TRUE(reader.ValidEncoding(
      DwarfPointerEncoding(dwarf2reader::DW_EH_PE_udata2 |
                           dwarf2reader::DW_EH_PE_textrel)));
  EXPECT_TRUE(reader.ValidEncoding(
      DwarfPointerEncoding(dwarf2reader::DW_EH_PE_udata4 |
                           dwarf2reader::DW_EH_PE_textrel)));
  EXPECT_TRUE(reader.ValidEncoding(
      DwarfPointerEncoding(dwarf2reader::DW_EH_PE_udata8 |
                           dwarf2reader::DW_EH_PE_textrel)));
  EXPECT_TRUE(reader.ValidEncoding(
      DwarfPointerEncoding(dwarf2reader::DW_EH_PE_sleb128 |
                           dwarf2reader::DW_EH_PE_textrel)));
  EXPECT_TRUE(reader.ValidEncoding(
      DwarfPointerEncoding(dwarf2reader::DW_EH_PE_sdata2 |
                           dwarf2reader::DW_EH_PE_textrel)));
  EXPECT_TRUE(reader.ValidEncoding(
      DwarfPointerEncoding(dwarf2reader::DW_EH_PE_sdata4 |
                           dwarf2reader::DW_EH_PE_textrel)));
  EXPECT_TRUE(reader.ValidEncoding(
      DwarfPointerEncoding(dwarf2reader::DW_EH_PE_sdata8 |
                           dwarf2reader::DW_EH_PE_textrel)));
  EXPECT_TRUE(reader.ValidEncoding(
      DwarfPointerEncoding(dwarf2reader::DW_EH_PE_absptr |
                           dwarf2reader::DW_EH_PE_datarel)));
  EXPECT_TRUE(reader.ValidEncoding(
      DwarfPointerEncoding(dwarf2reader::DW_EH_PE_uleb128 |
                           dwarf2reader::DW_EH_PE_datarel)));
  EXPECT_TRUE(reader.ValidEncoding(
      DwarfPointerEncoding(dwarf2reader::DW_EH_PE_udata2 |
                           dwarf2reader::DW_EH_PE_datarel)));
  EXPECT_TRUE(reader.ValidEncoding(
      DwarfPointerEncoding(dwarf2reader::DW_EH_PE_udata4 |
                           dwarf2reader::DW_EH_PE_datarel)));
  EXPECT_TRUE(reader.ValidEncoding(
      DwarfPointerEncoding(dwarf2reader::DW_EH_PE_udata8 |
                           dwarf2reader::DW_EH_PE_datarel)));
  EXPECT_TRUE(reader.ValidEncoding(
      DwarfPointerEncoding(dwarf2reader::DW_EH_PE_sleb128 |
                           dwarf2reader::DW_EH_PE_datarel)));
  EXPECT_TRUE(reader.ValidEncoding(
      DwarfPointerEncoding(dwarf2reader::DW_EH_PE_sdata2 |
                           dwarf2reader::DW_EH_PE_datarel)));
  EXPECT_TRUE(reader.ValidEncoding(
      DwarfPointerEncoding(dwarf2reader::DW_EH_PE_sdata4 |
                           dwarf2reader::DW_EH_PE_datarel)));
  EXPECT_TRUE(reader.ValidEncoding(
      DwarfPointerEncoding(dwarf2reader::DW_EH_PE_sdata8 |
                           dwarf2reader::DW_EH_PE_datarel)));
  EXPECT_TRUE(reader.ValidEncoding(
      DwarfPointerEncoding(dwarf2reader::DW_EH_PE_absptr |
                           dwarf2reader::DW_EH_PE_funcrel)));
  EXPECT_TRUE(reader.ValidEncoding(
      DwarfPointerEncoding(dwarf2reader::DW_EH_PE_uleb128 |
                           dwarf2reader::DW_EH_PE_funcrel)));
  EXPECT_TRUE(reader.ValidEncoding(
      DwarfPointerEncoding(dwarf2reader::DW_EH_PE_udata2 |
                           dwarf2reader::DW_EH_PE_funcrel)));
  EXPECT_TRUE(reader.ValidEncoding(
      DwarfPointerEncoding(dwarf2reader::DW_EH_PE_udata4 |
                           dwarf2reader::DW_EH_PE_funcrel)));
  EXPECT_TRUE(reader.ValidEncoding(
      DwarfPointerEncoding(dwarf2reader::DW_EH_PE_udata8 |
                           dwarf2reader::DW_EH_PE_funcrel)));
  EXPECT_TRUE(reader.ValidEncoding(
      DwarfPointerEncoding(dwarf2reader::DW_EH_PE_sleb128 |
                           dwarf2reader::DW_EH_PE_funcrel)));
  EXPECT_TRUE(reader.ValidEncoding(
      DwarfPointerEncoding(dwarf2reader::DW_EH_PE_sdata2 |
                           dwarf2reader::DW_EH_PE_funcrel)));
  EXPECT_TRUE(reader.ValidEncoding(
      DwarfPointerEncoding(dwarf2reader::DW_EH_PE_sdata4 |
                           dwarf2reader::DW_EH_PE_funcrel)));
  EXPECT_TRUE(reader.ValidEncoding(
      DwarfPointerEncoding(dwarf2reader::DW_EH_PE_sdata8 |
                           dwarf2reader::DW_EH_PE_funcrel)));
  EXPECT_TRUE(reader.ValidEncoding(
      DwarfPointerEncoding(dwarf2reader::DW_EH_PE_indirect |
                           dwarf2reader::DW_EH_PE_absptr |
                           dwarf2reader::DW_EH_PE_pcrel)));
  EXPECT_TRUE(reader.ValidEncoding(
      DwarfPointerEncoding(dwarf2reader::DW_EH_PE_indirect |
                           dwarf2reader::DW_EH_PE_uleb128 |
                           dwarf2reader::DW_EH_PE_pcrel)));
  EXPECT_TRUE(reader.ValidEncoding(
      DwarfPointerEncoding(dwarf2reader::DW_EH_PE_indirect |
                           dwarf2reader::DW_EH_PE_udata2 |
                           dwarf2reader::DW_EH_PE_pcrel)));
  EXPECT_TRUE(reader.ValidEncoding(
      DwarfPointerEncoding(dwarf2reader::DW_EH_PE_indirect |
                           dwarf2reader::DW_EH_PE_udata4 |
                           dwarf2reader::DW_EH_PE_pcrel)));
  EXPECT_TRUE(reader.ValidEncoding(
      DwarfPointerEncoding(dwarf2reader::DW_EH_PE_indirect |
                           dwarf2reader::DW_EH_PE_udata8 |
                           dwarf2reader::DW_EH_PE_pcrel)));
  EXPECT_TRUE(reader.ValidEncoding(
      DwarfPointerEncoding(dwarf2reader::DW_EH_PE_indirect |
                           dwarf2reader::DW_EH_PE_sleb128 |
                           dwarf2reader::DW_EH_PE_pcrel)));
  EXPECT_TRUE(reader.ValidEncoding(
      DwarfPointerEncoding(dwarf2reader::DW_EH_PE_indirect |
                           dwarf2reader::DW_EH_PE_sdata2 |
                           dwarf2reader::DW_EH_PE_pcrel)));
  EXPECT_TRUE(reader.ValidEncoding(
      DwarfPointerEncoding(dwarf2reader::DW_EH_PE_indirect |
                           dwarf2reader::DW_EH_PE_sdata4 |
                           dwarf2reader::DW_EH_PE_pcrel)));
  EXPECT_TRUE(reader.ValidEncoding(
      DwarfPointerEncoding(dwarf2reader::DW_EH_PE_indirect |
                           dwarf2reader::DW_EH_PE_sdata8 |
                           dwarf2reader::DW_EH_PE_pcrel)));
  EXPECT_TRUE(reader.ValidEncoding(
      DwarfPointerEncoding(dwarf2reader::DW_EH_PE_indirect |
                           dwarf2reader::DW_EH_PE_absptr |
                           dwarf2reader::DW_EH_PE_textrel)));
  EXPECT_TRUE(reader.ValidEncoding(
      DwarfPointerEncoding(dwarf2reader::DW_EH_PE_indirect |
                           dwarf2reader::DW_EH_PE_uleb128 |
                           dwarf2reader::DW_EH_PE_textrel)));
  EXPECT_TRUE(reader.ValidEncoding(
      DwarfPointerEncoding(dwarf2reader::DW_EH_PE_indirect |
                           dwarf2reader::DW_EH_PE_udata2 |
                           dwarf2reader::DW_EH_PE_textrel)));
  EXPECT_TRUE(reader.ValidEncoding(
      DwarfPointerEncoding(dwarf2reader::DW_EH_PE_indirect |
                           dwarf2reader::DW_EH_PE_udata4 |
                           dwarf2reader::DW_EH_PE_textrel)));
  EXPECT_TRUE(reader.ValidEncoding(
      DwarfPointerEncoding(dwarf2reader::DW_EH_PE_indirect |
                           dwarf2reader::DW_EH_PE_udata8 |
                           dwarf2reader::DW_EH_PE_textrel)));
  EXPECT_TRUE(reader.ValidEncoding(
      DwarfPointerEncoding(dwarf2reader::DW_EH_PE_indirect |
                           dwarf2reader::DW_EH_PE_sleb128 |
                           dwarf2reader::DW_EH_PE_textrel)));
  EXPECT_TRUE(reader.ValidEncoding(
      DwarfPointerEncoding(dwarf2reader::DW_EH_PE_indirect |
                           dwarf2reader::DW_EH_PE_sdata2 |
                           dwarf2reader::DW_EH_PE_textrel)));
  EXPECT_TRUE(reader.ValidEncoding(
      DwarfPointerEncoding(dwarf2reader::DW_EH_PE_indirect |
                           dwarf2reader::DW_EH_PE_sdata4 |
                           dwarf2reader::DW_EH_PE_textrel)));
  EXPECT_TRUE(reader.ValidEncoding(
      DwarfPointerEncoding(dwarf2reader::DW_EH_PE_indirect |
                           dwarf2reader::DW_EH_PE_sdata8 |
                           dwarf2reader::DW_EH_PE_textrel)));
  EXPECT_TRUE(reader.ValidEncoding(
      DwarfPointerEncoding(dwarf2reader::DW_EH_PE_indirect |
                           dwarf2reader::DW_EH_PE_absptr |
                           dwarf2reader::DW_EH_PE_datarel)));
  EXPECT_TRUE(reader.ValidEncoding(
      DwarfPointerEncoding(dwarf2reader::DW_EH_PE_indirect |
                           dwarf2reader::DW_EH_PE_uleb128 |
                           dwarf2reader::DW_EH_PE_datarel)));
  EXPECT_TRUE(reader.ValidEncoding(
      DwarfPointerEncoding(dwarf2reader::DW_EH_PE_indirect |
                           dwarf2reader::DW_EH_PE_udata2 |
                           dwarf2reader::DW_EH_PE_datarel)));
  EXPECT_TRUE(reader.ValidEncoding(
      DwarfPointerEncoding(dwarf2reader::DW_EH_PE_indirect |
                           dwarf2reader::DW_EH_PE_udata4 |
                           dwarf2reader::DW_EH_PE_datarel)));
  EXPECT_TRUE(reader.ValidEncoding(
      DwarfPointerEncoding(dwarf2reader::DW_EH_PE_indirect |
                           dwarf2reader::DW_EH_PE_udata8 |
                           dwarf2reader::DW_EH_PE_datarel)));
  EXPECT_TRUE(reader.ValidEncoding(
      DwarfPointerEncoding(dwarf2reader::DW_EH_PE_indirect |
                           dwarf2reader::DW_EH_PE_sleb128 |
                           dwarf2reader::DW_EH_PE_datarel)));
  EXPECT_TRUE(reader.ValidEncoding(
      DwarfPointerEncoding(dwarf2reader::DW_EH_PE_indirect |
                           dwarf2reader::DW_EH_PE_sdata2 |
                           dwarf2reader::DW_EH_PE_datarel)));
  EXPECT_TRUE(reader.ValidEncoding(
      DwarfPointerEncoding(dwarf2reader::DW_EH_PE_indirect |
                           dwarf2reader::DW_EH_PE_sdata4 |
                           dwarf2reader::DW_EH_PE_datarel)));
  EXPECT_TRUE(reader.ValidEncoding(
      DwarfPointerEncoding(dwarf2reader::DW_EH_PE_indirect |
                           dwarf2reader::DW_EH_PE_sdata8 |
                           dwarf2reader::DW_EH_PE_datarel)));
  EXPECT_TRUE(reader.ValidEncoding(
      DwarfPointerEncoding(dwarf2reader::DW_EH_PE_indirect |
                           dwarf2reader::DW_EH_PE_absptr |
                           dwarf2reader::DW_EH_PE_funcrel)));
  EXPECT_TRUE(reader.ValidEncoding(
      DwarfPointerEncoding(dwarf2reader::DW_EH_PE_indirect |
                           dwarf2reader::DW_EH_PE_uleb128 |
                           dwarf2reader::DW_EH_PE_funcrel)));
  EXPECT_TRUE(reader.ValidEncoding(
      DwarfPointerEncoding(dwarf2reader::DW_EH_PE_indirect |
                           dwarf2reader::DW_EH_PE_udata2 |
                           dwarf2reader::DW_EH_PE_funcrel)));
  EXPECT_TRUE(reader.ValidEncoding(
      DwarfPointerEncoding(dwarf2reader::DW_EH_PE_indirect |
                           dwarf2reader::DW_EH_PE_udata4 |
                           dwarf2reader::DW_EH_PE_funcrel)));
  EXPECT_TRUE(reader.ValidEncoding(
      DwarfPointerEncoding(dwarf2reader::DW_EH_PE_indirect |
                           dwarf2reader::DW_EH_PE_udata8 |
                           dwarf2reader::DW_EH_PE_funcrel)));
  EXPECT_TRUE(reader.ValidEncoding(
      DwarfPointerEncoding(dwarf2reader::DW_EH_PE_indirect |
                           dwarf2reader::DW_EH_PE_sleb128 |
                           dwarf2reader::DW_EH_PE_funcrel)));
  EXPECT_TRUE(reader.ValidEncoding(
      DwarfPointerEncoding(dwarf2reader::DW_EH_PE_indirect |
                           dwarf2reader::DW_EH_PE_sdata2 |
                           dwarf2reader::DW_EH_PE_funcrel)));
  EXPECT_TRUE(reader.ValidEncoding(
      DwarfPointerEncoding(dwarf2reader::DW_EH_PE_indirect |
                           dwarf2reader::DW_EH_PE_sdata4 |
                           dwarf2reader::DW_EH_PE_funcrel)));
  EXPECT_TRUE(reader.ValidEncoding(
      DwarfPointerEncoding(dwarf2reader::DW_EH_PE_indirect |
                           dwarf2reader::DW_EH_PE_sdata8 |
                           dwarf2reader::DW_EH_PE_funcrel)));

  EXPECT_FALSE(reader.ValidEncoding(DwarfPointerEncoding(0x05)));
  EXPECT_FALSE(reader.ValidEncoding(DwarfPointerEncoding(0x07)));
  EXPECT_FALSE(reader.ValidEncoding(DwarfPointerEncoding(0x0d)));
  EXPECT_FALSE(reader.ValidEncoding(DwarfPointerEncoding(0x0f)));
  EXPECT_FALSE(reader.ValidEncoding(DwarfPointerEncoding(0x51)));
  EXPECT_FALSE(reader.ValidEncoding(DwarfPointerEncoding(0x60)));
  EXPECT_FALSE(reader.ValidEncoding(DwarfPointerEncoding(0x70)));
  EXPECT_FALSE(reader.ValidEncoding(DwarfPointerEncoding(0xf0)));
  EXPECT_FALSE(reader.ValidEncoding(DwarfPointerEncoding(0xd0)));
}

TEST_F(ReaderDeathTest, DW_EH_PE_omit) {
  static const char data[1] = { 42 };
  ByteReader reader(ENDIANNESS_BIG);
  reader.SetAddressSize(4);
  EXPECT_DEATH(reader.ReadEncodedPointer(data, dwarf2reader::DW_EH_PE_omit,
                                         &pointer_size),
               "encoding != DW_EH_PE_omit");
}

TEST_F(Reader, DW_EH_PE_absptr4) {
  static const char data[] = { 0x27, 0x57, 0xea, 0x40 };
  ByteReader reader(ENDIANNESS_LITTLE);
  reader.SetAddressSize(4);
  EXPECT_EQ(0x40ea5727U,
            reader.ReadEncodedPointer(data, dwarf2reader::DW_EH_PE_absptr,
                                      &pointer_size));
  EXPECT_EQ(4U, pointer_size);
}

TEST_F(Reader, DW_EH_PE_absptr8) {
  static const char data[] = {
    0x60, 0x27, 0x57, 0xea, 0x40, 0xc2, 0x98, 0x05, 0x01, 0x50
  };
  ByteReader reader(ENDIANNESS_LITTLE);
  reader.SetAddressSize(8);
  EXPECT_EQ(0x010598c240ea5727ULL,
            reader.ReadEncodedPointer(data + 1, dwarf2reader::DW_EH_PE_absptr,
                                      &pointer_size));
  EXPECT_EQ(8U, pointer_size);
}

TEST_F(Reader, DW_EH_PE_uleb128) {
  static const char data[] = { 0x81, 0x84, 0x4c };
  ByteReader reader(ENDIANNESS_LITTLE);
  reader.SetAddressSize(4);
  EXPECT_EQ(0x130201U,
            reader.ReadEncodedPointer(data, dwarf2reader::DW_EH_PE_uleb128,
                                      &pointer_size));
  EXPECT_EQ(3U, pointer_size);
}

TEST_F(Reader, DW_EH_PE_udata2) {
  static const char data[] = { 0xf4, 0x8d };
  ByteReader reader(ENDIANNESS_BIG);
  reader.SetAddressSize(4);
  EXPECT_EQ(0xf48dU,
            reader.ReadEncodedPointer(data, dwarf2reader::DW_EH_PE_udata2,
                                      &pointer_size));
  EXPECT_EQ(2U, pointer_size);
}

TEST_F(Reader, DW_EH_PE_udata4) {
  static const char data[] = { 0xb2, 0x68, 0xa5, 0x62, 0x8f, 0x8b };
  ByteReader reader(ENDIANNESS_BIG);
  reader.SetAddressSize(8);
  EXPECT_EQ(0xa5628f8b,
            reader.ReadEncodedPointer(data + 2, dwarf2reader::DW_EH_PE_udata4,
                                      &pointer_size));
  EXPECT_EQ(4U, pointer_size);
}

TEST_F(Reader, DW_EH_PE_udata8Addr8) {
  static const char data[] = {
    0x27, 0x04, 0x73, 0x04, 0x69, 0x9f, 0x19, 0xed, 0x8f, 0xfe
  };
  ByteReader reader(ENDIANNESS_LITTLE);
  reader.SetAddressSize(8);
  EXPECT_EQ(0x8fed199f69047304ULL,
            reader.ReadEncodedPointer(data + 1, dwarf2reader::DW_EH_PE_udata8,
                                        &pointer_size));
  EXPECT_EQ(8U, pointer_size);
}

TEST_F(Reader, DW_EH_PE_udata8Addr4) {
  static const char data[] = {
    0x27, 0x04, 0x73, 0x04, 0x69, 0x9f, 0x19, 0xed, 0x8f, 0xfe
  };
  ByteReader reader(ENDIANNESS_LITTLE);
  reader.SetAddressSize(4);
  EXPECT_EQ(0x69047304ULL,
            reader.ReadEncodedPointer(data + 1, dwarf2reader::DW_EH_PE_udata8,
                                        &pointer_size));
  EXPECT_EQ(8U, pointer_size);
}

TEST_F(Reader, DW_EH_PE_sleb128) {
  static const char data[] = { 0x42, 0xff, 0xfb, 0x73 };
  ByteReader reader(ENDIANNESS_BIG);
  reader.SetAddressSize(4);
  EXPECT_EQ(-0x030201U & 0xffffffff,
            reader.ReadEncodedPointer(data + 1, dwarf2reader::DW_EH_PE_sleb128,
                                        &pointer_size));
  EXPECT_EQ(3U, pointer_size);
}

TEST_F(Reader, DW_EH_PE_sdata2) {
  static const char data[] = { 0xb9, 0xbf };
  ByteReader reader(ENDIANNESS_LITTLE);
  reader.SetAddressSize(8);
  EXPECT_EQ(0xffffffffffffbfb9ULL,
            reader.ReadEncodedPointer(data, dwarf2reader::DW_EH_PE_sdata2,
                                        &pointer_size));
  EXPECT_EQ(2U, pointer_size);
}

TEST_F(Reader, DW_EH_PE_sdata4) {
  static const char data[] = { 0xa0, 0xca, 0xf2, 0xb8, 0xc2, 0xad };
  ByteReader reader(ENDIANNESS_LITTLE);
  reader.SetAddressSize(8);
  EXPECT_EQ(0xffffffffadc2b8f2ULL,
            reader.ReadEncodedPointer(data + 2, dwarf2reader::DW_EH_PE_sdata4,
                                        &pointer_size));
  EXPECT_EQ(4U, pointer_size);
}

TEST_F(Reader, DW_EH_PE_sdata8) {
  static const char data[] = {
    0xf6, 0x66, 0x57, 0x79, 0xe0, 0x0c, 0x9b, 0x26, 0x87
  };
  ByteReader reader(ENDIANNESS_LITTLE);
  reader.SetAddressSize(8);
  EXPECT_EQ(0x87269b0ce0795766ULL,
            reader.ReadEncodedPointer(data + 1, dwarf2reader::DW_EH_PE_sdata8,
                                        &pointer_size));
  EXPECT_EQ(8U, pointer_size);
}

TEST_F(Reader, DW_EH_PE_pcrel) {
  static const char data[] = { 0x4a, 0x8b, 0x1b, 0x14, 0xc8, 0xc4, 0x02, 0xce };
  ByteReader reader(ENDIANNESS_BIG);
  reader.SetAddressSize(4);
  DwarfPointerEncoding encoding =
      DwarfPointerEncoding(dwarf2reader::DW_EH_PE_pcrel
                           | dwarf2reader::DW_EH_PE_absptr);
  reader.SetCFIDataBase(0x89951377, data);
  EXPECT_EQ(0x89951377 + 3 + 0x14c8c402,
            reader.ReadEncodedPointer(data + 3, encoding, &pointer_size));
  EXPECT_EQ(4U, pointer_size);
}

TEST_F(Reader, DW_EH_PE_textrel) {
  static const char data[] = { 0xd9, 0x0d, 0x05, 0x17, 0xc9, 0x7a, 0x42, 0x1e };
  ByteReader reader(ENDIANNESS_LITTLE);
  reader.SetAddressSize(4);
  reader.SetTextBase(0xb91beaf0);
  DwarfPointerEncoding encoding =
      DwarfPointerEncoding(dwarf2reader::DW_EH_PE_textrel
                           | dwarf2reader::DW_EH_PE_sdata2);
  EXPECT_EQ((0xb91beaf0 + 0xffffc917) & 0xffffffff,
            reader.ReadEncodedPointer(data + 3, encoding, &pointer_size));
  EXPECT_EQ(2U, pointer_size);
}

TEST_F(Reader, DW_EH_PE_datarel) {
  static const char data[] = { 0x16, 0xf2, 0xbb, 0x82, 0x68, 0xa7, 0xbc, 0x39 };
  ByteReader reader(ENDIANNESS_BIG);
  reader.SetAddressSize(8);
  reader.SetDataBase(0xbef308bd25ce74f0ULL);
  DwarfPointerEncoding encoding =
      DwarfPointerEncoding(dwarf2reader::DW_EH_PE_datarel
                           | dwarf2reader::DW_EH_PE_sleb128);
  EXPECT_EQ(0xbef308bd25ce74f0ULL + 0xfffffffffffa013bULL,
            reader.ReadEncodedPointer(data + 2, encoding, &pointer_size));
  EXPECT_EQ(3U, pointer_size);
}

TEST_F(Reader, DW_EH_PE_funcrel) {
  static const char data[] = { 0x84, 0xf8, 0x14, 0x01, 0x61, 0xd1, 0x48, 0xc9 };
  ByteReader reader(ENDIANNESS_BIG);
  reader.SetAddressSize(4);
  reader.SetFunctionBase(0x823c3520);
  DwarfPointerEncoding encoding =
      DwarfPointerEncoding(dwarf2reader::DW_EH_PE_funcrel
                           | dwarf2reader::DW_EH_PE_udata2);
  EXPECT_EQ(0x823c3520 + 0xd148,
            reader.ReadEncodedPointer(data + 5, encoding, &pointer_size));
  EXPECT_EQ(2U, pointer_size);
}

TEST(UsableBase, CFI) {
  static const char data[1] = { 0x42 };
  ByteReader reader(ENDIANNESS_BIG);
  reader.SetCFIDataBase(0xb31cbd20, data);
  EXPECT_TRUE(reader.UsableEncoding(dwarf2reader::DW_EH_PE_absptr));
  EXPECT_TRUE(reader.UsableEncoding(dwarf2reader::DW_EH_PE_pcrel));
  EXPECT_FALSE(reader.UsableEncoding(dwarf2reader::DW_EH_PE_textrel));
  EXPECT_FALSE(reader.UsableEncoding(dwarf2reader::DW_EH_PE_datarel));
  EXPECT_FALSE(reader.UsableEncoding(dwarf2reader::DW_EH_PE_funcrel));
  EXPECT_FALSE(reader.UsableEncoding(dwarf2reader::DW_EH_PE_omit));
  EXPECT_FALSE(reader.UsableEncoding(DwarfPointerEncoding(0x60)));
}

TEST(UsableBase, Text) {
  ByteReader reader(ENDIANNESS_BIG);
  reader.SetTextBase(0xa899ccb9);
  EXPECT_TRUE(reader.UsableEncoding(dwarf2reader::DW_EH_PE_absptr));
  EXPECT_FALSE(reader.UsableEncoding(dwarf2reader::DW_EH_PE_pcrel));
  EXPECT_TRUE(reader.UsableEncoding(dwarf2reader::DW_EH_PE_textrel));
  EXPECT_FALSE(reader.UsableEncoding(dwarf2reader::DW_EH_PE_datarel));
  EXPECT_FALSE(reader.UsableEncoding(dwarf2reader::DW_EH_PE_funcrel));
  EXPECT_FALSE(reader.UsableEncoding(dwarf2reader::DW_EH_PE_omit));
  EXPECT_FALSE(reader.UsableEncoding(DwarfPointerEncoding(0x60)));
}

TEST(UsableBase, Data) {
  ByteReader reader(ENDIANNESS_BIG);
  reader.SetDataBase(0xf7b10bcd);
  EXPECT_TRUE(reader.UsableEncoding(dwarf2reader::DW_EH_PE_absptr));
  EXPECT_FALSE(reader.UsableEncoding(dwarf2reader::DW_EH_PE_pcrel));
  EXPECT_FALSE(reader.UsableEncoding(dwarf2reader::DW_EH_PE_textrel));
  EXPECT_TRUE(reader.UsableEncoding(dwarf2reader::DW_EH_PE_datarel));
  EXPECT_FALSE(reader.UsableEncoding(dwarf2reader::DW_EH_PE_funcrel));
  EXPECT_FALSE(reader.UsableEncoding(dwarf2reader::DW_EH_PE_omit));
  EXPECT_FALSE(reader.UsableEncoding(DwarfPointerEncoding(0x60)));
}

TEST(UsableBase, Function) {
  ByteReader reader(ENDIANNESS_BIG);
  reader.SetFunctionBase(0xc2c0ed81);
  EXPECT_TRUE(reader.UsableEncoding(dwarf2reader::DW_EH_PE_absptr));
  EXPECT_FALSE(reader.UsableEncoding(dwarf2reader::DW_EH_PE_pcrel));
  EXPECT_FALSE(reader.UsableEncoding(dwarf2reader::DW_EH_PE_textrel));
  EXPECT_FALSE(reader.UsableEncoding(dwarf2reader::DW_EH_PE_datarel));
  EXPECT_TRUE(reader.UsableEncoding(dwarf2reader::DW_EH_PE_funcrel));
  EXPECT_FALSE(reader.UsableEncoding(dwarf2reader::DW_EH_PE_omit));
  EXPECT_FALSE(reader.UsableEncoding(DwarfPointerEncoding(0x60)));
}

TEST(UsableBase, ClearFunction) {
  ByteReader reader(ENDIANNESS_BIG);
  reader.SetFunctionBase(0xc2c0ed81);
  reader.ClearFunctionBase();
  EXPECT_TRUE(reader.UsableEncoding(dwarf2reader::DW_EH_PE_absptr));
  EXPECT_FALSE(reader.UsableEncoding(dwarf2reader::DW_EH_PE_pcrel));
  EXPECT_FALSE(reader.UsableEncoding(dwarf2reader::DW_EH_PE_textrel));
  EXPECT_FALSE(reader.UsableEncoding(dwarf2reader::DW_EH_PE_datarel));
  EXPECT_FALSE(reader.UsableEncoding(dwarf2reader::DW_EH_PE_funcrel));
  EXPECT_FALSE(reader.UsableEncoding(dwarf2reader::DW_EH_PE_omit));
  EXPECT_FALSE(reader.UsableEncoding(DwarfPointerEncoding(0x60)));
}

struct AlignedFixture {
  AlignedFixture() : reader(ENDIANNESS_BIG) { reader.SetAddressSize(4); }
  static const char data[10];
  ByteReader reader;
  size_t pointer_size;
};
  
const char AlignedFixture::data[10] = {
  0xfe, 0x6e, 0x93, 0xd8, 0x34, 0xd5, 0x1c, 0xd3, 0xac, 0x2b
};

class Aligned: public AlignedFixture, public Test { };

TEST_F(Aligned, DW_EH_PE_aligned0) {
  reader.SetCFIDataBase(0xb440305c, data);
  EXPECT_EQ(0xfe6e93d8U,
            reader.ReadEncodedPointer(data, dwarf2reader::DW_EH_PE_aligned,
                                      &pointer_size));
  EXPECT_EQ(4U, pointer_size);
}

TEST_F(Aligned, DW_EH_PE_aligned1) {
  reader.SetCFIDataBase(0xb440305d, data);
  EXPECT_EQ(0xd834d51cU,
            reader.ReadEncodedPointer(data, dwarf2reader::DW_EH_PE_aligned,
                                      &pointer_size));
  EXPECT_EQ(7U, pointer_size);
}

TEST_F(Aligned, DW_EH_PE_aligned2) {
  reader.SetCFIDataBase(0xb440305e, data);
  EXPECT_EQ(0x93d834d5U,
            reader.ReadEncodedPointer(data, dwarf2reader::DW_EH_PE_aligned,
                                      &pointer_size));
  EXPECT_EQ(6U, pointer_size);
}

TEST_F(Aligned, DW_EH_PE_aligned3) {
  reader.SetCFIDataBase(0xb440305f, data);
  EXPECT_EQ(0x6e93d834U,
            reader.ReadEncodedPointer(data, dwarf2reader::DW_EH_PE_aligned,
                                      &pointer_size));
  EXPECT_EQ(5U, pointer_size);
}

TEST_F(Aligned, DW_EH_PE_aligned11) {
  reader.SetCFIDataBase(0xb4403061, data);
  EXPECT_EQ(0xd834d51cU,
            reader.ReadEncodedPointer(data + 1,
                                      dwarf2reader::DW_EH_PE_aligned,
                                      &pointer_size));
  EXPECT_EQ(6U, pointer_size);
}

TEST_F(Aligned, DW_EH_PE_aligned30) {
  reader.SetCFIDataBase(0xb4403063, data);
  EXPECT_EQ(0x6e93d834U,
            reader.ReadEncodedPointer(data + 1,
                                      dwarf2reader::DW_EH_PE_aligned,
                                      &pointer_size));
  EXPECT_EQ(4U, pointer_size);
}

TEST_F(Aligned, DW_EH_PE_aligned23) {
  reader.SetCFIDataBase(0xb4403062, data);
  EXPECT_EQ(0x1cd3ac2bU,
            reader.ReadEncodedPointer(data + 3,
                                      dwarf2reader::DW_EH_PE_aligned,
                                      &pointer_size));
  EXPECT_EQ(7U, pointer_size);
}

TEST_F(Aligned, DW_EH_PE_aligned03) {
  reader.SetCFIDataBase(0xb4403064, data);
  EXPECT_EQ(0x34d51cd3U,
            reader.ReadEncodedPointer(data + 3,
                                      dwarf2reader::DW_EH_PE_aligned,
                                      &pointer_size));
  EXPECT_EQ(5U, pointer_size);
}  
