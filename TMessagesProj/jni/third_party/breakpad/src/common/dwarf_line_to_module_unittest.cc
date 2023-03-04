// Copyright (c) 2010 Google Inc.
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

// dwarf_line_to_module.cc: Unit tests for google_breakpad::DwarfLineToModule.

#include <vector>

#include "breakpad_googletest_includes.h"
#include "common/dwarf_line_to_module.h"

using std::vector;

using google_breakpad::DwarfLineToModule;
using google_breakpad::Module;
using google_breakpad::Module;

TEST(SimpleModule, One) {
  Module m("name", "os", "architecture", "id");
  vector<Module::Line> lines;
  DwarfLineToModule h(&m, "/", &lines);

  h.DefineFile("file1", 0x30bf0f27, 0, 0, 0);
  h.AddLine(0x6fd126fbf74f2680LL, 0x63c9a14cf556712bLL, 0x30bf0f27,
            0x4c090cbf, 0x1cf9fe0d);

  vector<Module::File *> files;
  m.GetFiles(&files);
  EXPECT_EQ(1U, files.size());
  EXPECT_STREQ("/file1", files[0]->name.c_str());

  EXPECT_EQ(1U, lines.size());
  EXPECT_EQ(0x6fd126fbf74f2680ULL, lines[0].address);
  EXPECT_EQ(0x63c9a14cf556712bULL, lines[0].size);
  EXPECT_TRUE(lines[0].file == files[0]);
  EXPECT_EQ(0x4c090cbf, lines[0].number);
}

TEST(SimpleModule, Many) {
  Module m("name", "os", "architecture", "id");
  vector<Module::Line> lines;
  DwarfLineToModule h(&m, "/", &lines);

  h.DefineDir("directory1", 0x838299ab);
  h.DefineDir("directory2", 0xf85de023);
  h.DefineFile("file1", 0x2b80377a, 0x838299ab, 0, 0);
  h.DefineFile("file1", 0x63beb4a4, 0xf85de023, 0, 0);
  h.DefineFile("file2", 0x1d161d56, 0x838299ab, 0, 0);
  h.DefineFile("file2", 0x1e7a667c, 0xf85de023, 0, 0);
  h.AddLine(0x69900c5d553b7274ULL, 0x90fded183f0d0d3cULL, 0x2b80377a,
            0x15b0f0a9U, 0x3ff5abd6U);
  h.AddLine(0x45811219a39b7101ULL, 0x25a5e6a924afc41fULL, 0x63beb4a4,
            0x4d259ce9U, 0x41c5ee32U);
  h.AddLine(0xfa90514c1dc9704bULL, 0x0063efeabc02f313ULL, 0x1d161d56,
            0x1ee9fa4fU, 0xbf70e46aU);
  h.AddLine(0x556b55fb6a647b10ULL, 0x3f3089ca2bfd80f5ULL, 0x1e7a667c,
            0x77fc280eU, 0x2c4a728cU);
  h.DefineFile("file3", -1, 0, 0, 0);
  h.AddLine(0xe2d72a37f8d9403aULL, 0x034dfab5b0d4d236ULL, 0x63beb4a5,
            0x75047044U, 0xb6a0016cU);

  vector<Module::File *> files;
  m.GetFiles(&files);
  ASSERT_EQ(5U, files.size());
  EXPECT_STREQ("/directory1/file1", files[0]->name.c_str());
  EXPECT_STREQ("/directory1/file2", files[1]->name.c_str());
  EXPECT_STREQ("/directory2/file1", files[2]->name.c_str());
  EXPECT_STREQ("/directory2/file2", files[3]->name.c_str());
  EXPECT_STREQ("/file3",            files[4]->name.c_str());

  ASSERT_EQ(5U, lines.size());

  EXPECT_EQ(0x69900c5d553b7274ULL, lines[0].address);
  EXPECT_EQ(0x90fded183f0d0d3cULL, lines[0].size);
  EXPECT_TRUE(lines[0].file == files[0]);
  EXPECT_EQ(0x15b0f0a9, lines[0].number);

  EXPECT_EQ(0x45811219a39b7101ULL, lines[1].address);
  EXPECT_EQ(0x25a5e6a924afc41fULL, lines[1].size);
  EXPECT_TRUE(lines[1].file == files[2]);
  EXPECT_EQ(0x4d259ce9, lines[1].number);

  EXPECT_EQ(0xfa90514c1dc9704bULL, lines[2].address);
  EXPECT_EQ(0x0063efeabc02f313ULL, lines[2].size);
  EXPECT_TRUE(lines[2].file == files[1]);
  EXPECT_EQ(0x1ee9fa4f, lines[2].number);

  EXPECT_EQ(0x556b55fb6a647b10ULL, lines[3].address);
  EXPECT_EQ(0x3f3089ca2bfd80f5ULL, lines[3].size);
  EXPECT_TRUE(lines[3].file == files[3]);
  EXPECT_EQ(0x77fc280e, lines[3].number);

  EXPECT_EQ(0xe2d72a37f8d9403aULL, lines[4].address);
  EXPECT_EQ(0x034dfab5b0d4d236ULL, lines[4].size);
  EXPECT_TRUE(lines[4].file == files[4]);
  EXPECT_EQ(0x75047044, lines[4].number);
}

TEST(Filenames, Absolute) {
  Module m("name", "os", "architecture", "id");
  vector<Module::Line> lines;
  DwarfLineToModule h(&m, "/", &lines);

  h.DefineDir("directory1", 1);
  h.DefineFile("/absolute", 1, 1, 0, 0);

  h.AddLine(1, 1, 1, 0, 0);

  vector<Module::File *> files;
  m.GetFiles(&files);
  ASSERT_EQ(1U, files.size());
  EXPECT_STREQ("/absolute", files[0]->name.c_str());
  ASSERT_EQ(1U, lines.size());
  EXPECT_TRUE(lines[0].file == files[0]);
}

TEST(Filenames, Relative) {
  Module m("name", "os", "architecture", "id");
  vector<Module::Line> lines;
  DwarfLineToModule h(&m, "/", &lines);

  h.DefineDir("directory1", 1);
  h.DefineFile("relative", 1, 1, 0, 0);

  h.AddLine(1, 1, 1, 0, 0);

  vector<Module::File *> files;
  m.GetFiles(&files);
  ASSERT_EQ(1U, files.size());
  EXPECT_STREQ("/directory1/relative", files[0]->name.c_str());
  ASSERT_EQ(1U, lines.size());
  EXPECT_TRUE(lines[0].file == files[0]);
}

TEST(Filenames, StrangeFile) {
  Module m("name", "os", "architecture", "id");
  vector<Module::Line> lines;
  DwarfLineToModule h(&m, "/", &lines);

  h.DefineDir("directory1", 1);
  h.DefineFile("", 1, 1, 0, 0);
  h.AddLine(1, 1, 1, 0, 0);

  ASSERT_EQ(1U, lines.size());
  EXPECT_STREQ("/directory1/", lines[0].file->name.c_str());
}

TEST(Filenames, StrangeDirectory) {
  Module m("name", "os", "architecture", "id");
  vector<Module::Line> lines;
  DwarfLineToModule h(&m, "/", &lines);

  h.DefineDir("", 1);
  h.DefineFile("file1", 1, 1, 0, 0);
  h.AddLine(1, 1, 1, 0, 0);

  ASSERT_EQ(1U, lines.size());
  EXPECT_STREQ("/file1", lines[0].file->name.c_str());
}

TEST(Filenames, StrangeDirectoryAndFile) {
  Module m("name", "os", "architecture", "id");
  vector<Module::Line> lines;
  DwarfLineToModule h(&m, "/", &lines);

  h.DefineDir("", 1);
  h.DefineFile("", 1, 1, 0, 0);
  h.AddLine(1, 1, 1, 0, 0);

  ASSERT_EQ(1U, lines.size());
  EXPECT_STREQ("/", lines[0].file->name.c_str());
}

// We should use the compilation directory when encountering a file for
// directory number zero.
TEST(Filenames, DirectoryZeroFileIsRelativeToCompilationDir) {
  Module m("name", "os", "architecture", "id");
  vector<Module::Line> lines;
  DwarfLineToModule h(&m, "src/build", &lines);

  h.DefineDir("Dir", 1);
  h.DefineFile("File", 1, 0, 0, 0);

  h.AddLine(1, 1, 1, 0, 0);

  ASSERT_EQ(1U, lines.size());
  EXPECT_STREQ("src/build/File", lines[0].file->name.c_str());
}

// We should treat non-absolute directories as relative to the compilation
// directory.
TEST(Filenames, IncludeDirectoryRelativeToDirectoryZero) {
  Module m("name", "os", "architecture", "id");
  vector<Module::Line> lines;
  DwarfLineToModule h(&m, "src/build", &lines);

  h.DefineDir("Dir", 1);
  h.DefineFile("File", 1, 1, 0, 0);

  h.AddLine(1, 1, 1, 0, 0);

  ASSERT_EQ(1U, lines.size());
  EXPECT_STREQ("src/build/Dir/File", lines[0].file->name.c_str());
}

// We should treat absolute directories as absolute, and not relative to
// the compilation dir.
TEST(Filenames, IncludeDirectoryAbsolute) {
  Module m("name", "os", "architecture", "id");
  vector<Module::Line> lines;
  DwarfLineToModule h(&m, "src/build", &lines);

  h.DefineDir("/Dir", 1);
  h.DefineFile("File", 1, 1, 0, 0);

  h.AddLine(1, 1, 1, 0, 0);

  ASSERT_EQ(1U, lines.size());
  EXPECT_STREQ("/Dir/File", lines[0].file->name.c_str());
}

// We should silently ignore attempts to define directory number zero,
// since that is always the compilation directory.
TEST(ModuleErrors, DirectoryZero) {
  Module m("name", "os", "architecture", "id");
  vector<Module::Line> lines;
  DwarfLineToModule h(&m, "/", &lines);

  h.DefineDir("directory0", 0); // should be ignored
  h.DefineFile("relative", 1, 0, 0, 0);

  h.AddLine(1, 1, 1, 0, 0);

  ASSERT_EQ(1U, lines.size());
  EXPECT_STREQ("/relative", lines[0].file->name.c_str());
}

// We should refuse to add lines with bogus file numbers. We should
// produce only one warning, however.
TEST(ModuleErrors, BadFileNumber) {
  Module m("name", "os", "architecture", "id");
  vector<Module::Line> lines;
  DwarfLineToModule h(&m, "/", &lines);

  h.DefineFile("relative", 1, 0, 0, 0);
  h.AddLine(1, 1, 2, 0, 0); // bad file number
  h.AddLine(2, 1, 2, 0, 0); // bad file number (no duplicate warning)

  EXPECT_EQ(0U, lines.size());
}

// We should treat files with bogus directory numbers as relative to
// the compilation unit.
TEST(ModuleErrors, BadDirectoryNumber) {
  Module m("name", "os", "architecture", "id");
  vector<Module::Line> lines;
  DwarfLineToModule h(&m, "/", &lines);

  h.DefineDir("directory1", 1);
  h.DefineFile("baddirnumber1", 1, 2, 0, 0); // bad directory number
  h.DefineFile("baddirnumber2", 2, 2, 0, 0); // bad dir number (no warning)
  h.AddLine(1, 1, 1, 0, 0);

  ASSERT_EQ(1U, lines.size());
  EXPECT_STREQ("baddirnumber1", lines[0].file->name.c_str());
}

// We promise not to report empty lines.
TEST(ModuleErrors, EmptyLine) {
  Module m("name", "os", "architecture", "id");
  vector<Module::Line> lines;
  DwarfLineToModule h(&m, "/", &lines);

  h.DefineFile("filename1", 1, 0, 0, 0);
  h.AddLine(1, 0, 1, 0, 0);

  ASSERT_EQ(0U, lines.size());
}  

// We are supposed to clip lines that extend beyond the end of the
// address space.
TEST(ModuleErrors, BigLine) {
  Module m("name", "os", "architecture", "id");
  vector<Module::Line> lines;
  DwarfLineToModule h(&m, "/", &lines);

  h.DefineFile("filename1", 1, 0, 0, 0);
  h.AddLine(0xffffffffffffffffULL, 2, 1, 0, 0);

  ASSERT_EQ(1U, lines.size());
  EXPECT_EQ(1U, lines[0].size);
}  

// The 'Omitted' tests verify that we correctly omit line information
// for code in sections that the linker has dropped. See "GNU
// toolchain omitted sections support" at the top of the
// DwarfLineToModule class.

TEST(Omitted, DroppedThenGood) {
  Module m("name", "os", "architecture", "id");
  vector<Module::Line> lines;
  DwarfLineToModule h(&m, "/", &lines);

  h.DefineFile("filename1", 1, 0, 0, 0);
  h.AddLine(0,  10, 1, 83816211, 0);   // should be omitted
  h.AddLine(20, 10, 1, 13059195, 0);   // should be recorded

  ASSERT_EQ(1U, lines.size());
  EXPECT_EQ(13059195, lines[0].number);
}

TEST(Omitted, GoodThenDropped) {
  Module m("name", "os", "architecture", "id");
  vector<Module::Line> lines;
  DwarfLineToModule h(&m, "/", &lines);

  h.DefineFile("filename1", 1, 0, 0, 0);
  h.AddLine(0x9dd6a372, 10, 1, 41454594, 0);   // should be recorded
  h.AddLine(0,  10, 1, 44793413, 0);           // should be omitted

  ASSERT_EQ(1U, lines.size());
  EXPECT_EQ(41454594, lines[0].number);
}

TEST(Omitted, Mix1) {
  Module m("name", "os", "architecture", "id");
  vector<Module::Line> lines;
  DwarfLineToModule h(&m, "/", &lines);

  h.DefineFile("filename1", 1, 0, 0, 0);
  h.AddLine(0x679ed72f,  10,   1, 58932642, 0);   // should be recorded
  h.AddLine(0xdfb5a72d,  10,   1, 39847385, 0);   // should be recorded
  h.AddLine(0,           0x78, 1, 23053829, 0);   // should be omitted
  h.AddLine(0x78,        0x6a, 1, 65317783, 0);   // should be omitted
  h.AddLine(0x78 + 0x6a, 0x2a, 1, 77601423, 0);   // should be omitted
  h.AddLine(0x9fe0cea5,  10,   1, 91806582, 0);   // should be recorded
  h.AddLine(0x7e41a109,  10,   1, 56169221, 0);   // should be recorded

  ASSERT_EQ(4U, lines.size());
  EXPECT_EQ(58932642, lines[0].number);
  EXPECT_EQ(39847385, lines[1].number);
  EXPECT_EQ(91806582, lines[2].number);
  EXPECT_EQ(56169221, lines[3].number);
}

TEST(Omitted, Mix2) {
  Module m("name", "os", "architecture", "id");
  vector<Module::Line> lines;
  DwarfLineToModule h(&m, "/", &lines);

  h.DefineFile("filename1", 1, 0, 0, 0);
  h.AddLine(0,           0xf2, 1, 58802211, 0);   // should be omitted
  h.AddLine(0xf2,        0xb9, 1, 78958222, 0);   // should be omitted
  h.AddLine(0xf2 + 0xb9, 0xf7, 1, 64861892, 0);   // should be omitted
  h.AddLine(0x4e4d271e,  9,    1, 67355743, 0);   // should be recorded
  h.AddLine(0xdfb5a72d,  30,   1, 23365776, 0);   // should be recorded
  h.AddLine(0,           0x64, 1, 76196762, 0);   // should be omitted
  h.AddLine(0x64,        0x33, 1, 71066611, 0);   // should be omitted
  h.AddLine(0x64 + 0x33, 0xe3, 1, 61749337, 0);   // should be omitted

  ASSERT_EQ(2U, lines.size());
  EXPECT_EQ(67355743, lines[0].number);
  EXPECT_EQ(23365776, lines[1].number);
}
