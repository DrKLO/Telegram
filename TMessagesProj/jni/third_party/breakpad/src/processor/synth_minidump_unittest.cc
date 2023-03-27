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

// synth_minidump_unittest.cc: Unit tests for google_breakpad::SynthMinidump
// classes.

#include <sstream>
#include <string>

#include "breakpad_googletest_includes.h"
#include "common/using_std_string.h"
#include "google_breakpad/common/minidump_format.h"
#include "processor/synth_minidump.h"
#include "processor/synth_minidump_unittest_data.h"

using google_breakpad::SynthMinidump::Context;
using google_breakpad::SynthMinidump::Dump;
using google_breakpad::SynthMinidump::Exception;
using google_breakpad::SynthMinidump::List;
using google_breakpad::SynthMinidump::Memory;
using google_breakpad::SynthMinidump::Module;
using google_breakpad::SynthMinidump::Section;
using google_breakpad::SynthMinidump::Stream;
using google_breakpad::SynthMinidump::String;
using google_breakpad::SynthMinidump::SystemInfo;
using google_breakpad::test_assembler::kBigEndian;
using google_breakpad::test_assembler::kLittleEndian;
using google_breakpad::test_assembler::Label;

TEST(Section, Simple) {
  Dump dump(0);
  Section section(dump);
  section.L32(0x12345678);
  section.Finish(0);
  string contents;
  ASSERT_TRUE(section.GetContents(&contents));
  EXPECT_EQ(string("\x78\x56\x34\x12", 4), contents);
}

TEST(Section, CiteLocationIn) {
  Dump dump(0, kBigEndian);
  Section section1(dump), section2(dump);
  section1.Append("order");
  section2.Append("mayhem");
  section2.Finish(0x32287ec2);
  section2.CiteLocationIn(&section1);
  string contents;
  ASSERT_TRUE(section1.GetContents(&contents));
  string expected("order\0\0\0\x06\x32\x28\x7e\xc2", 13);
  EXPECT_EQ(expected, contents);
}

TEST(Stream, CiteStreamIn) {
  Dump dump(0, kLittleEndian);
  Stream stream(dump, 0x40cae2b3);
  Section section(dump);
  stream.Append("stream contents");
  section.Append("section contents");
  stream.Finish(0x41424344);
  stream.CiteStreamIn(&section);
  string contents;
  ASSERT_TRUE(section.GetContents(&contents));
  string expected("section contents"
                  "\xb3\xe2\xca\x40"
                  "\x0f\0\0\0"
                  "\x44\x43\x42\x41",
                  16 + 4 + 4 + 4);
  EXPECT_EQ(expected, contents);
}

TEST(Memory, CiteMemoryIn) {
  Dump dump(0, kBigEndian);
  Memory memory(dump, 0x76d010874ab019f9ULL);
  Section section(dump);
  memory.Append("memory contents");
  section.Append("section contents");
  memory.Finish(0x51525354);
  memory.CiteMemoryIn(&section);
  string contents;
  ASSERT_TRUE(section.GetContents(&contents));
  string expected("section contents"
                  "\x76\xd0\x10\x87\x4a\xb0\x19\xf9"
                  "\0\0\0\x0f"
                  "\x51\x52\x53\x54",
                  16 + 8 + 4 + 4);
  EXPECT_EQ(contents, expected);
}

TEST(Memory, Here) {
  Dump dump(0, kBigEndian);
  Memory memory(dump, 0x89979731eb060ed4ULL);
  memory.Append(1729, 42);
  Label l = memory.Here();
  ASSERT_EQ(0x89979731eb060ed4ULL + 1729, l.Value());
}

TEST(Context, X86) {
  Dump dump(0, kLittleEndian);
  assert(x86_raw_context.context_flags & MD_CONTEXT_X86);
  Context context(dump, x86_raw_context);
  string contents;
  ASSERT_TRUE(context.GetContents(&contents));
  EXPECT_EQ(sizeof(x86_expected_contents), contents.size());
  EXPECT_TRUE(memcmp(contents.data(), x86_expected_contents, contents.size())
              == 0);
}

TEST(Context, ARM) {
  Dump dump(0, kLittleEndian);
  assert(arm_raw_context.context_flags & MD_CONTEXT_ARM);
  Context context(dump, arm_raw_context);
  string contents;
  ASSERT_TRUE(context.GetContents(&contents));
  EXPECT_EQ(sizeof(arm_expected_contents), contents.size());
  EXPECT_TRUE(memcmp(contents.data(), arm_expected_contents, contents.size())
              == 0);
}

TEST(ContextDeathTest, X86BadFlags) {
  Dump dump(0, kLittleEndian);
  MDRawContextX86 raw;
  raw.context_flags = MD_CONTEXT_AMD64;
  ASSERT_DEATH(Context context(dump, raw);,
               "context\\.context_flags & (0x[0-9a-f]+|MD_CONTEXT_X86)");
}

TEST(ContextDeathTest, X86BadEndianness) {
  Dump dump(0, kBigEndian);
  MDRawContextX86 raw;
  raw.context_flags = MD_CONTEXT_X86;
  ASSERT_DEATH(Context context(dump, raw);,
               "dump\\.endianness\\(\\) == kLittleEndian");
}

TEST(Thread, Simple) {
  Dump dump(0, kLittleEndian);
  Context context(dump, x86_raw_context);
  context.Finish(0x8665da0c);
  Memory stack(dump, 0xaad55a93cc3c0efcULL);
  stack.Append("stack contents");
  stack.Finish(0xe08cdbd1);
  google_breakpad::SynthMinidump::Thread thread(
      dump, 0x3d7ec360, stack, context,
      0x3593f44d, // suspend count
      0xab352b82, // priority class
      0x2753d838, // priority
      0xeb2de4be3f29e3e9ULL); // thread environment block
  string contents;
  ASSERT_TRUE(thread.GetContents(&contents));
  static const uint8_t expected_bytes[] = {
    0x60, 0xc3, 0x7e, 0x3d, // thread id
    0x4d, 0xf4, 0x93, 0x35, // suspend count
    0x82, 0x2b, 0x35, 0xab, // priority class
    0x38, 0xd8, 0x53, 0x27, // priority
    0xe9, 0xe3, 0x29, 0x3f, 0xbe, 0xe4, 0x2d, 0xeb, // thread environment block
    0xfc, 0x0e, 0x3c, 0xcc, 0x93, 0x5a, 0xd5, 0xaa, // stack address
    0x0e, 0x00, 0x00, 0x00, // stack size
    0xd1, 0xdb, 0x8c, 0xe0, // stack MDRVA
    0xcc, 0x02, 0x00, 0x00, // context size
    0x0c, 0xda, 0x65, 0x86  // context MDRVA
  };
  EXPECT_EQ(sizeof(expected_bytes), contents.size());
  EXPECT_TRUE(memcmp(contents.data(), expected_bytes, contents.size()) == 0);
}

TEST(Exception, Simple) {
  Dump dump(0, kLittleEndian);
  Context context(dump, x86_raw_context);
  context.Finish(0x8665da0c);
  
  Exception exception(dump, context,
                      0x1234abcd, // thread id
                      0xdcba4321, // exception code
                      0xf0e0d0c0, // exception flags
                      0x0919a9b9c9d9e9f9ULL); // exception address
  string contents;
  ASSERT_TRUE(exception.GetContents(&contents));
  static const uint8_t expected_bytes[] = {
    0xcd, 0xab, 0x34, 0x12, // thread id
    0x00, 0x00, 0x00, 0x00, // __align
    0x21, 0x43, 0xba, 0xdc, // exception code
    0xc0, 0xd0, 0xe0, 0xf0, // exception flags
    0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, // exception record
    0xf9, 0xe9, 0xd9, 0xc9, 0xb9, 0xa9, 0x19, 0x09, // exception address
    0x00, 0x00, 0x00, 0x00, // number parameters
    0x00, 0x00, 0x00, 0x00, // __align
    0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, // exception_information
    0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, // exception_information
    0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, // exception_information
    0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, // exception_information
    0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, // exception_information
    0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, // exception_information
    0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, // exception_information
    0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, // exception_information
    0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, // exception_information
    0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, // exception_information
    0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, // exception_information
    0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, // exception_information
    0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, // exception_information
    0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, // exception_information
    0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, // exception_information
    0xcc, 0x02, 0x00, 0x00, // context size
    0x0c, 0xda, 0x65, 0x86  // context MDRVA
  };
  EXPECT_EQ(sizeof(expected_bytes), contents.size());
  EXPECT_TRUE(memcmp(contents.data(), expected_bytes, contents.size()) == 0);
}

TEST(String, Simple) {
  Dump dump(0, kBigEndian);
  String s(dump, "All mimsy were the borogoves");
  string contents;
  ASSERT_TRUE(s.GetContents(&contents));
  static const char expected[] = 
    "\x00\x00\x00\x38\0A\0l\0l\0 \0m\0i\0m\0s\0y\0 \0w\0e\0r\0e"
    "\0 \0t\0h\0e\0 \0b\0o\0r\0o\0g\0o\0v\0e\0s";
  string expected_string(expected, sizeof(expected) - 1);
  EXPECT_EQ(expected_string, contents);
}

TEST(String, CiteStringIn) {
  Dump dump(0, kLittleEndian);
  String s(dump, "and the mome wraths outgrabe");
  Section section(dump);
  section.Append("initial");
  s.CiteStringIn(&section);
  s.Finish(0xdc2bb469);
  string contents;
  ASSERT_TRUE(section.GetContents(&contents));
  EXPECT_EQ(string("initial\x69\xb4\x2b\xdc", 7 + 4), contents);
}

TEST(List, Empty) {
  Dump dump(0, kBigEndian);
  List<Section> list(dump, 0x2442779c);
  EXPECT_TRUE(list.Empty());
  list.Finish(0x84e09808);
  string contents;
  ASSERT_TRUE(list.GetContents(&contents));
  EXPECT_EQ(string("\0\0\0\0", 4), contents);
}

TEST(List, Two) {
  Dump dump(0, kBigEndian);
  List<Section> list(dump, 0x26c9f498);
  Section section1(dump);
  section1.Append("section one contents");
  EXPECT_TRUE(list.Empty());
  list.Add(&section1);
  EXPECT_FALSE(list.Empty());
  Section section2(dump);
  section2.Append("section two contents");
  list.Add(&section2);
  list.Finish(0x1e5bb60e);
  string contents;
  ASSERT_TRUE(list.GetContents(&contents));
  EXPECT_EQ(string("\0\0\0\x02section one contentssection two contents", 44),
            contents);
}

TEST(Dump, Header) {
  Dump dump(0x9f738b33685cc84cULL, kLittleEndian, 0xb3817faf, 0x2c741c0a);
  dump.Finish();
  string contents;
  ASSERT_TRUE(dump.GetContents(&contents));
  ASSERT_EQ(string("\x4d\x44\x4d\x50"   // signature
                   "\xaf\x7f\x81\xb3"   // version
                   "\0\0\0\0"           // stream count
                   "\x20\0\0\0"         // directory RVA (could be anything)
                   "\0\0\0\0"           // checksum
                   "\x0a\x1c\x74\x2c"   // time_date_stamp
                   "\x4c\xc8\x5c\x68\x33\x8b\x73\x9f", // flags
                   32),
            contents);
}

TEST(Dump, HeaderBigEndian) {
  Dump dump(0x206ce3cc6fb8e0f0ULL, kBigEndian, 0x161693e2, 0x35667744);
  dump.Finish();
  string contents;
  ASSERT_TRUE(dump.GetContents(&contents));
  ASSERT_EQ(string("\x50\x4d\x44\x4d"   // signature
                   "\x16\x16\x93\xe2"   // version
                   "\0\0\0\0"           // stream count
                   "\0\0\0\x20"         // directory RVA (could be anything)
                   "\0\0\0\0"           // checksum
                   "\x35\x66\x77\x44"   // time_date_stamp
                   "\x20\x6c\xe3\xcc\x6f\xb8\xe0\xf0", // flags
                   32),
            contents);
}

TEST(Dump, OneSection) {
  Dump dump(0, kLittleEndian);
  Section section(dump);
  section.Append("section contents");
  dump.Add(&section);
  dump.Finish();
  string dump_contents;
  // Just check for undefined labels; don't worry about the contents.
  ASSERT_TRUE(dump.GetContents(&dump_contents));

  Section referencing_section(dump);
  section.CiteLocationIn(&referencing_section);
  string contents;
  ASSERT_TRUE(referencing_section.GetContents(&contents));
  ASSERT_EQ(string("\x10\0\0\0\x20\0\0\0", 8), contents);
}
