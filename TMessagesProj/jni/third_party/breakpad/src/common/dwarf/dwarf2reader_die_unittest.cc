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

// Original author: Jim Blandy <jimb@mozilla.com> <jimb@red-bean.com>

// dwarf2reader_die_unittest.cc: Unit tests for dwarf2reader::CompilationUnit

#include <stdlib.h>

#include <iostream>
#include <string>
#include <vector>

#include "breakpad_googletest_includes.h"
#include "common/dwarf/bytereader-inl.h"
#include "common/dwarf/dwarf2reader_test_common.h"
#include "common/dwarf/dwarf2reader.h"
#include "common/using_std_string.h"
#include "google_breakpad/common/breakpad_types.h"

using google_breakpad::test_assembler::Endianness;
using google_breakpad::test_assembler::Label;
using google_breakpad::test_assembler::Section;
using google_breakpad::test_assembler::kBigEndian;
using google_breakpad::test_assembler::kLittleEndian;

using dwarf2reader::ByteReader;
using dwarf2reader::CompilationUnit;
using dwarf2reader::Dwarf2Handler;
using dwarf2reader::DwarfAttribute;
using dwarf2reader::DwarfForm;
using dwarf2reader::DwarfHasChild;
using dwarf2reader::DwarfTag;
using dwarf2reader::ENDIANNESS_BIG;
using dwarf2reader::ENDIANNESS_LITTLE;
using dwarf2reader::SectionMap;

using std::vector;
using testing::InSequence;
using testing::Pointee;
using testing::Return;
using testing::Sequence;
using testing::Test;
using testing::TestWithParam;
using testing::_;

class MockDwarf2Handler: public Dwarf2Handler {
 public:
  MOCK_METHOD5(StartCompilationUnit, bool(uint64 offset, uint8 address_size,
                                          uint8 offset_size, uint64 cu_length,
                                          uint8 dwarf_version));
  MOCK_METHOD2(StartDIE, bool(uint64 offset, enum DwarfTag tag));
  MOCK_METHOD4(ProcessAttributeUnsigned, void(uint64 offset,
                                              DwarfAttribute attr,
                                              enum DwarfForm form,
                                              uint64 data));
  MOCK_METHOD4(ProcessAttributeSigned, void(uint64 offset,
                                            enum DwarfAttribute attr,
                                            enum DwarfForm form,
                                            int64 data));
  MOCK_METHOD4(ProcessAttributeReference, void(uint64 offset,
                                               enum DwarfAttribute attr,
                                               enum DwarfForm form,
                                               uint64 data));
  MOCK_METHOD5(ProcessAttributeBuffer, void(uint64 offset,
                                            enum DwarfAttribute attr,
                                            enum DwarfForm form,
                                            const char* data,
                                            uint64 len));
  MOCK_METHOD4(ProcessAttributeString, void(uint64 offset,
                                            enum DwarfAttribute attr,
                                            enum DwarfForm form,
                                            const string& data));
  MOCK_METHOD4(ProcessAttributeSignature, void(uint64 offset,
                                               DwarfAttribute attr,
                                               enum DwarfForm form,
                                               uint64 signature));
  MOCK_METHOD1(EndDIE, void(uint64 offset));
};

struct DIEFixture {

  DIEFixture() {
    // Fix the initial offset of the .debug_info and .debug_abbrev sections.
    info.start() = 0;
    abbrevs.start() = 0;

    // Default expectations for the data handler.
    EXPECT_CALL(handler, StartCompilationUnit(_, _, _, _, _)).Times(0);
    EXPECT_CALL(handler, StartDIE(_, _)).Times(0);
    EXPECT_CALL(handler, ProcessAttributeUnsigned(_, _, _, _)).Times(0);
    EXPECT_CALL(handler, ProcessAttributeSigned(_, _, _, _)).Times(0);
    EXPECT_CALL(handler, ProcessAttributeReference(_, _, _, _)).Times(0);
    EXPECT_CALL(handler, ProcessAttributeBuffer(_, _, _, _, _)).Times(0);
    EXPECT_CALL(handler, ProcessAttributeString(_, _, _, _)).Times(0);
    EXPECT_CALL(handler, EndDIE(_)).Times(0);
  }

  // Return a reference to a section map whose .debug_info section refers
  // to |info|, and whose .debug_abbrev section refers to |abbrevs|. This
  // function returns a reference to the same SectionMap each time; new
  // calls wipe out maps established by earlier calls.
  const SectionMap &MakeSectionMap() {
    // Copy the sections' contents into strings that will live as long as
    // the map itself.
    assert(info.GetContents(&info_contents));
    assert(abbrevs.GetContents(&abbrevs_contents));
    section_map.clear();
    section_map[".debug_info"].first  = info_contents.data();
    section_map[".debug_info"].second = info_contents.size();
    section_map[".debug_abbrev"].first  = abbrevs_contents.data();
    section_map[".debug_abbrev"].second = abbrevs_contents.size();
    return section_map;
  }

  TestCompilationUnit info;
  TestAbbrevTable abbrevs;
  MockDwarf2Handler handler;
  string abbrevs_contents, info_contents;
  SectionMap section_map;
};

struct DwarfHeaderParams {
  DwarfHeaderParams(Endianness endianness, size_t format_size,
                   int version, size_t address_size)
      : endianness(endianness), format_size(format_size),
        version(version), address_size(address_size) { }
  Endianness endianness;
  size_t format_size;                   // 4-byte or 8-byte DWARF offsets
  int version;
  size_t address_size;
};

class DwarfHeader: public DIEFixture,
                   public TestWithParam<DwarfHeaderParams> { };

TEST_P(DwarfHeader, Header) {
  Label abbrev_table = abbrevs.Here();
  abbrevs.Abbrev(1, dwarf2reader::DW_TAG_compile_unit,
                 dwarf2reader::DW_children_yes)
      .Attribute(dwarf2reader::DW_AT_name, dwarf2reader::DW_FORM_string)
      .EndAbbrev()
      .EndTable();

  info.set_format_size(GetParam().format_size);
  info.set_endianness(GetParam().endianness);

  info.Header(GetParam().version, abbrev_table, GetParam().address_size)
      .ULEB128(1)                     // DW_TAG_compile_unit, with children
      .AppendCString("sam")           // DW_AT_name, DW_FORM_string
      .D8(0);                         // end of children
  info.Finish();

  {
    InSequence s;
    EXPECT_CALL(handler,
                StartCompilationUnit(0, GetParam().address_size,
                                     GetParam().format_size, _,
                                     GetParam().version))
        .WillOnce(Return(true));
    EXPECT_CALL(handler, StartDIE(_, dwarf2reader::DW_TAG_compile_unit))
        .WillOnce(Return(true));
    EXPECT_CALL(handler, ProcessAttributeString(_, dwarf2reader::DW_AT_name, 
                                                dwarf2reader::DW_FORM_string,
                                                "sam"))
        .WillOnce(Return());
    EXPECT_CALL(handler, EndDIE(_))
        .WillOnce(Return());
  }

  ByteReader byte_reader(GetParam().endianness == kLittleEndian ?
                         ENDIANNESS_LITTLE : ENDIANNESS_BIG);
  CompilationUnit parser(MakeSectionMap(), 0, &byte_reader, &handler);
  EXPECT_EQ(parser.Start(), info_contents.size());
}

INSTANTIATE_TEST_CASE_P(
    HeaderVariants, DwarfHeader,
    ::testing::Values(DwarfHeaderParams(kLittleEndian, 4, 2, 4),
                      DwarfHeaderParams(kLittleEndian, 4, 2, 8),
                      DwarfHeaderParams(kLittleEndian, 4, 3, 4),
                      DwarfHeaderParams(kLittleEndian, 4, 3, 8),
                      DwarfHeaderParams(kLittleEndian, 4, 4, 4),
                      DwarfHeaderParams(kLittleEndian, 4, 4, 8),
                      DwarfHeaderParams(kLittleEndian, 8, 2, 4),
                      DwarfHeaderParams(kLittleEndian, 8, 2, 8),
                      DwarfHeaderParams(kLittleEndian, 8, 3, 4),
                      DwarfHeaderParams(kLittleEndian, 8, 3, 8),
                      DwarfHeaderParams(kLittleEndian, 8, 4, 4),
                      DwarfHeaderParams(kLittleEndian, 8, 4, 8),
                      DwarfHeaderParams(kBigEndian,    4, 2, 4),
                      DwarfHeaderParams(kBigEndian,    4, 2, 8),
                      DwarfHeaderParams(kBigEndian,    4, 3, 4),
                      DwarfHeaderParams(kBigEndian,    4, 3, 8),
                      DwarfHeaderParams(kBigEndian,    4, 4, 4),
                      DwarfHeaderParams(kBigEndian,    4, 4, 8),
                      DwarfHeaderParams(kBigEndian,    8, 2, 4),
                      DwarfHeaderParams(kBigEndian,    8, 2, 8),
                      DwarfHeaderParams(kBigEndian,    8, 3, 4),
                      DwarfHeaderParams(kBigEndian,    8, 3, 8),
                      DwarfHeaderParams(kBigEndian,    8, 4, 4),
                      DwarfHeaderParams(kBigEndian,    8, 4, 8)));

struct DwarfFormsFixture: public DIEFixture {
  // Start a compilation unit, as directed by |params|, containing one
  // childless DIE of the given tag, with one attribute of the given name
  // and form. The 'info' fixture member is left just after the abbrev
  // code, waiting for the attribute value to be appended.
  void StartSingleAttributeDIE(const DwarfHeaderParams &params,
                               DwarfTag tag, DwarfAttribute name,
                               DwarfForm form) {
    // Create the abbreviation table.
    Label abbrev_table = abbrevs.Here();
    abbrevs.Abbrev(1, tag, dwarf2reader::DW_children_no)
        .Attribute(name, form)
        .EndAbbrev()
        .EndTable();

    // Create the compilation unit, up to the attribute value.
    info.set_format_size(params.format_size);
    info.set_endianness(params.endianness);
    info.Header(params.version, abbrev_table, params.address_size)
        .ULEB128(1);                    // abbrev code
  }

  // Set up handler to expect a compilation unit matching |params|,
  // containing one childless DIE of the given tag, in the sequence s. Stop
  // just before the expectations.
  void ExpectBeginCompilationUnit(const DwarfHeaderParams &params,
                                  DwarfTag tag, uint64 offset=0) {
    EXPECT_CALL(handler,
                StartCompilationUnit(offset, params.address_size,
                                     params.format_size, _,
                                     params.version))
        .InSequence(s)
        .WillOnce(Return(true));
    EXPECT_CALL(handler, StartDIE(_, tag))
        .InSequence(s)
        .WillOnce(Return(true));
  }

  void ExpectEndCompilationUnit() {
    EXPECT_CALL(handler, EndDIE(_))
        .InSequence(s)
        .WillOnce(Return());
  }

  void ParseCompilationUnit(const DwarfHeaderParams &params, uint64 offset=0) {
    ByteReader byte_reader(params.endianness == kLittleEndian ?
                           ENDIANNESS_LITTLE : ENDIANNESS_BIG);
    CompilationUnit parser(MakeSectionMap(), offset, &byte_reader, &handler);
    EXPECT_EQ(offset + parser.Start(), info_contents.size());
  }

  // The sequence to which the fixture's methods append expectations.
  Sequence s;
};

struct DwarfForms: public DwarfFormsFixture,
                   public TestWithParam<DwarfHeaderParams> { };

TEST_P(DwarfForms, addr) {
  StartSingleAttributeDIE(GetParam(), dwarf2reader::DW_TAG_compile_unit,
                          dwarf2reader::DW_AT_low_pc,
                          dwarf2reader::DW_FORM_addr);
  uint64_t value;
  if (GetParam().address_size == 4) {
    value = 0xc8e9ffcc;
    info.D32(value);
  } else {
    value = 0xe942517fc2768564ULL;
    info.D64(value);
  }
  info.Finish();

  ExpectBeginCompilationUnit(GetParam(), dwarf2reader::DW_TAG_compile_unit);
  EXPECT_CALL(handler, ProcessAttributeUnsigned(_, dwarf2reader::DW_AT_low_pc, 
                                                dwarf2reader::DW_FORM_addr,
                                                value))
      .InSequence(s)
      .WillOnce(Return());
  ExpectEndCompilationUnit();

  ParseCompilationUnit(GetParam());
}

TEST_P(DwarfForms, block2_empty) {
  StartSingleAttributeDIE(GetParam(), (DwarfTag) 0x16e4d2f7,
                          (DwarfAttribute) 0xe52c4463,
                          dwarf2reader::DW_FORM_block2);
  info.D16(0);
  info.Finish();

  ExpectBeginCompilationUnit(GetParam(), (DwarfTag) 0x16e4d2f7);
  EXPECT_CALL(handler, ProcessAttributeBuffer(_, (DwarfAttribute) 0xe52c4463,
                                              dwarf2reader::DW_FORM_block2,
                                              _, 0))
      .InSequence(s)
      .WillOnce(Return());
  ExpectEndCompilationUnit();

  ParseCompilationUnit(GetParam());
}

TEST_P(DwarfForms, block2) {
  StartSingleAttributeDIE(GetParam(), (DwarfTag) 0x16e4d2f7,
                          (DwarfAttribute) 0xe52c4463,
                          dwarf2reader::DW_FORM_block2);
  unsigned char data[258];
  memset(data, '*', sizeof(data));
  info.D16(sizeof(data))
      .Append(data, sizeof(data));
  info.Finish();

  ExpectBeginCompilationUnit(GetParam(), (DwarfTag) 0x16e4d2f7);
  EXPECT_CALL(handler, ProcessAttributeBuffer(_, (DwarfAttribute) 0xe52c4463,
                                              dwarf2reader::DW_FORM_block2,
                                              Pointee('*'), 258))
      .InSequence(s)
      .WillOnce(Return());
  ExpectEndCompilationUnit();

  ParseCompilationUnit(GetParam());
}

TEST_P(DwarfForms, flag_present) {
  StartSingleAttributeDIE(GetParam(), (DwarfTag) 0x3e449ac2,
                          (DwarfAttribute) 0x359d1972,
                          dwarf2reader::DW_FORM_flag_present);
  // DW_FORM_flag_present occupies no space in the DIE.
  info.Finish();

  ExpectBeginCompilationUnit(GetParam(), (DwarfTag) 0x3e449ac2);
  EXPECT_CALL(handler,
              ProcessAttributeUnsigned(_, (DwarfAttribute) 0x359d1972,
                                       dwarf2reader::DW_FORM_flag_present,
                                       1))
      .InSequence(s)
      .WillOnce(Return());
  ExpectEndCompilationUnit();

  ParseCompilationUnit(GetParam());
}

TEST_P(DwarfForms, sec_offset) {
  StartSingleAttributeDIE(GetParam(), (DwarfTag) 0x1d971689,
                          (DwarfAttribute) 0xa060bfd1,
                          dwarf2reader::DW_FORM_sec_offset);
  uint64_t value;
  if (GetParam().format_size == 4) {
    value = 0xacc9c388;
    info.D32(value);
  } else {
    value = 0xcffe5696ffe3ed0aULL;
    info.D64(value);
  }
  info.Finish();

  ExpectBeginCompilationUnit(GetParam(), (DwarfTag) 0x1d971689);
  EXPECT_CALL(handler, ProcessAttributeUnsigned(_, (DwarfAttribute) 0xa060bfd1,
                                                dwarf2reader::DW_FORM_sec_offset,
                                                value))
      .InSequence(s)
      .WillOnce(Return());
  ExpectEndCompilationUnit();

  ParseCompilationUnit(GetParam());
}

TEST_P(DwarfForms, exprloc) {
  StartSingleAttributeDIE(GetParam(), (DwarfTag) 0xb6d167bb,
                          (DwarfAttribute) 0xba3ae5cb,
                          dwarf2reader::DW_FORM_exprloc);
  info.ULEB128(29)
      .Append(29, 173);
  info.Finish();

  ExpectBeginCompilationUnit(GetParam(), (DwarfTag) 0xb6d167bb);
  EXPECT_CALL(handler, ProcessAttributeBuffer(_, (DwarfAttribute) 0xba3ae5cb,
                                              dwarf2reader::DW_FORM_exprloc,
                                              Pointee(173), 29))
      .InSequence(s)
      .WillOnce(Return());
  ExpectEndCompilationUnit();

  ParseCompilationUnit(GetParam());
}

TEST_P(DwarfForms, ref_sig8) {
  StartSingleAttributeDIE(GetParam(), (DwarfTag) 0x253e7b2b,
                          (DwarfAttribute) 0xd708d908,
                          dwarf2reader::DW_FORM_ref_sig8);
  info.D64(0xf72fa0cb6ddcf9d6ULL);
  info.Finish();

  ExpectBeginCompilationUnit(GetParam(), (DwarfTag) 0x253e7b2b);
  EXPECT_CALL(handler, ProcessAttributeSignature(_, (DwarfAttribute) 0xd708d908,
                                                 dwarf2reader::DW_FORM_ref_sig8,
                                                 0xf72fa0cb6ddcf9d6ULL))
      .InSequence(s)
      .WillOnce(Return());
  ExpectEndCompilationUnit();

  ParseCompilationUnit(GetParam());
}

// A value passed to ProcessAttributeSignature is just an absolute number,
// not an offset within the compilation unit as most of the other
// DW_FORM_ref forms are. Check that the reader doesn't try to apply any
// offset to the signature, by reading it from a compilation unit that does
// not start at the beginning of the section.
TEST_P(DwarfForms, ref_sig8_not_first) {
  info.Append(98, '*');
  StartSingleAttributeDIE(GetParam(), (DwarfTag) 0x253e7b2b,
                          (DwarfAttribute) 0xd708d908,
                          dwarf2reader::DW_FORM_ref_sig8);
  info.D64(0xf72fa0cb6ddcf9d6ULL);
  info.Finish();

  ExpectBeginCompilationUnit(GetParam(), (DwarfTag) 0x253e7b2b, 98);
  EXPECT_CALL(handler, ProcessAttributeSignature(_, (DwarfAttribute) 0xd708d908,
                                                 dwarf2reader::DW_FORM_ref_sig8,
                                                 0xf72fa0cb6ddcf9d6ULL))
      .InSequence(s)
      .WillOnce(Return());
  ExpectEndCompilationUnit();

  ParseCompilationUnit(GetParam(), 98);
}

// Tests for the other attribute forms could go here.

INSTANTIATE_TEST_CASE_P(
    HeaderVariants, DwarfForms,
    ::testing::Values(DwarfHeaderParams(kLittleEndian, 4, 2, 4),
                      DwarfHeaderParams(kLittleEndian, 4, 2, 8),
                      DwarfHeaderParams(kLittleEndian, 4, 3, 4),
                      DwarfHeaderParams(kLittleEndian, 4, 3, 8),
                      DwarfHeaderParams(kLittleEndian, 4, 4, 4),
                      DwarfHeaderParams(kLittleEndian, 4, 4, 8),
                      DwarfHeaderParams(kLittleEndian, 8, 2, 4),
                      DwarfHeaderParams(kLittleEndian, 8, 2, 8),
                      DwarfHeaderParams(kLittleEndian, 8, 3, 4),
                      DwarfHeaderParams(kLittleEndian, 8, 3, 8),
                      DwarfHeaderParams(kLittleEndian, 8, 4, 4),
                      DwarfHeaderParams(kLittleEndian, 8, 4, 8),
                      DwarfHeaderParams(kBigEndian,    4, 2, 4),
                      DwarfHeaderParams(kBigEndian,    4, 2, 8),
                      DwarfHeaderParams(kBigEndian,    4, 3, 4),
                      DwarfHeaderParams(kBigEndian,    4, 3, 8),
                      DwarfHeaderParams(kBigEndian,    4, 4, 4),
                      DwarfHeaderParams(kBigEndian,    4, 4, 8),
                      DwarfHeaderParams(kBigEndian,    8, 2, 4),
                      DwarfHeaderParams(kBigEndian,    8, 2, 8),
                      DwarfHeaderParams(kBigEndian,    8, 3, 4),
                      DwarfHeaderParams(kBigEndian,    8, 3, 8),
                      DwarfHeaderParams(kBigEndian,    8, 4, 4),
                      DwarfHeaderParams(kBigEndian,    8, 4, 8)));
