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

// module_unittest.cc: Unit tests for google_breakpad::Module.

#include <errno.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>

#include <algorithm>
#include <sstream>
#include <string>

#include "breakpad_googletest_includes.h"
#include "common/module.h"
#include "common/using_std_string.h"

using google_breakpad::Module;
using std::stringstream;
using std::vector;
using testing::ContainerEq;

static Module::Function *generate_duplicate_function(const string &name) {
  const Module::Address DUP_ADDRESS = 0xd35402aac7a7ad5cLL;
  const Module::Address DUP_SIZE = 0x200b26e605f99071LL;
  const Module::Address DUP_PARAMETER_SIZE = 0xf14ac4fed48c4a99LL;

  Module::Function *function = new Module::Function(name, DUP_ADDRESS);
  function->size = DUP_SIZE;
  function->parameter_size = DUP_PARAMETER_SIZE;
  return function;
}

#define MODULE_NAME "name with spaces"
#define MODULE_OS "os-name"
#define MODULE_ARCH "architecture"
#define MODULE_ID "id-string"

TEST(Write, Header) {
  stringstream s;
  Module m(MODULE_NAME, MODULE_OS, MODULE_ARCH, MODULE_ID);
  m.Write(s, ALL_SYMBOL_DATA);
  string contents = s.str();
  EXPECT_STREQ("MODULE os-name architecture id-string name with spaces\n",
               contents.c_str());
}

TEST(Write, OneLineFunc) {
  stringstream s;
  Module m(MODULE_NAME, MODULE_OS, MODULE_ARCH, MODULE_ID);

  Module::File *file = m.FindFile("file_name.cc");
  Module::Function *function = new Module::Function(
      "function_name", 0xe165bf8023b9d9abLL);
  function->size = 0x1e4bb0eb1cbf5b09LL;
  function->parameter_size = 0x772beee89114358aLL;
  Module::Line line = { 0xe165bf8023b9d9abLL, 0x1e4bb0eb1cbf5b09LL,
                        file, 67519080 };
  function->lines.push_back(line);
  m.AddFunction(function);

  m.Write(s, ALL_SYMBOL_DATA);
  string contents = s.str();
  EXPECT_STREQ("MODULE os-name architecture id-string name with spaces\n"
               "FILE 0 file_name.cc\n"
               "FUNC e165bf8023b9d9ab 1e4bb0eb1cbf5b09 772beee89114358a"
               " function_name\n"
               "e165bf8023b9d9ab 1e4bb0eb1cbf5b09 67519080 0\n",
               contents.c_str());
}

TEST(Write, RelativeLoadAddress) {
  stringstream s;
  Module m(MODULE_NAME, MODULE_OS, MODULE_ARCH, MODULE_ID);

  // Some source files.  We will expect to see them in lexicographic order.
  Module::File *file1 = m.FindFile("filename-b.cc");
  Module::File *file2 = m.FindFile("filename-a.cc");

  // A function.
  Module::Function *function = new Module::Function(
      "A_FLIBBERTIJIBBET::a_will_o_the_wisp(a clown)", 0xbec774ea5dd935f3LL);
  function->size = 0x2922088f98d3f6fcLL;
  function->parameter_size = 0xe5e9aa008bd5f0d0LL;

  // Some source lines.  The module should not sort these.
  Module::Line line1 = { 0xbec774ea5dd935f3LL, 0x1c2be6d6c5af2611LL,
                         file1, 41676901 };
  Module::Line line2 = { 0xdaf35bc123885c04LL, 0xcf621b8d324d0ebLL,
                         file2, 67519080 };
  function->lines.push_back(line2);
  function->lines.push_back(line1);

  m.AddFunction(function);

  // Some stack information.
  Module::StackFrameEntry *entry = new Module::StackFrameEntry();
  entry->address = 0x30f9e5c83323973dULL;
  entry->size = 0x49fc9ca7c7c13dc2ULL;
  entry->initial_rules[".cfa"] = "he was a handsome man";
  entry->initial_rules["and"] = "what i want to know is";
  entry->rule_changes[0x30f9e5c83323973eULL]["how"] =
    "do you like your blueeyed boy";
  entry->rule_changes[0x30f9e5c83323973eULL]["Mister"] = "Death";
  m.AddStackFrameEntry(entry);

  // Set the load address.  Doing this after adding all the data to
  // the module must work fine.
  m.SetLoadAddress(0x2ab698b0b6407073LL);

  m.Write(s, ALL_SYMBOL_DATA);
  string contents = s.str();
  EXPECT_STREQ("MODULE os-name architecture id-string name with spaces\n"
               "FILE 0 filename-a.cc\n"
               "FILE 1 filename-b.cc\n"
               "FUNC 9410dc39a798c580 2922088f98d3f6fc e5e9aa008bd5f0d0"
               " A_FLIBBERTIJIBBET::a_will_o_the_wisp(a clown)\n"
               "b03cc3106d47eb91 cf621b8d324d0eb 67519080 0\n"
               "9410dc39a798c580 1c2be6d6c5af2611 41676901 1\n"
               "STACK CFI INIT 6434d177ce326ca 49fc9ca7c7c13dc2"
               " .cfa: he was a handsome man"
               " and: what i want to know is\n"
               "STACK CFI 6434d177ce326cb"
               " Mister: Death"
               " how: do you like your blueeyed boy\n",
               contents.c_str());
}

TEST(Write, OmitUnusedFiles) {
  Module m(MODULE_NAME, MODULE_OS, MODULE_ARCH, MODULE_ID);

  // Create some source files.
  Module::File *file1 = m.FindFile("filename1");
  m.FindFile("filename2");  // not used by any line
  Module::File *file3 = m.FindFile("filename3");

  // Create a function.
  Module::Function *function = new Module::Function(
      "function_name", 0x9b926d464f0b9384LL);
  function->size = 0x4f524a4ba795e6a6LL;
  function->parameter_size = 0xbbe8133a6641c9b7LL;

  // Source files that refer to some files, but not others.
  Module::Line line1 = { 0x595fa44ebacc1086LL, 0x1e1e0191b066c5b3LL,
                         file1, 137850127 };
  Module::Line line2 = { 0x401ce8c8a12d25e3LL, 0x895751c41b8d2ce2LL,
                         file3, 28113549 };
  function->lines.push_back(line1);
  function->lines.push_back(line2);
  m.AddFunction(function);

  m.AssignSourceIds();

  vector<Module::File *> vec;
  m.GetFiles(&vec);
  EXPECT_EQ((size_t) 3, vec.size());
  EXPECT_STREQ("filename1", vec[0]->name.c_str());
  EXPECT_NE(-1, vec[0]->source_id);
  // Expect filename2 not to be used.
  EXPECT_STREQ("filename2", vec[1]->name.c_str());
  EXPECT_EQ(-1, vec[1]->source_id);
  EXPECT_STREQ("filename3", vec[2]->name.c_str());
  EXPECT_NE(-1, vec[2]->source_id);

  stringstream s;
  m.Write(s, ALL_SYMBOL_DATA);
  string contents = s.str();
  EXPECT_STREQ("MODULE os-name architecture id-string name with spaces\n"
               "FILE 0 filename1\n"
               "FILE 1 filename3\n"
               "FUNC 9b926d464f0b9384 4f524a4ba795e6a6 bbe8133a6641c9b7"
               " function_name\n"
               "595fa44ebacc1086 1e1e0191b066c5b3 137850127 0\n"
               "401ce8c8a12d25e3 895751c41b8d2ce2 28113549 1\n",
               contents.c_str());
}

TEST(Write, NoCFI) {
  stringstream s;
  Module m(MODULE_NAME, MODULE_OS, MODULE_ARCH, MODULE_ID);

  // Some source files.  We will expect to see them in lexicographic order.
  Module::File *file1 = m.FindFile("filename.cc");

  // A function.
  Module::Function *function = new Module::Function(
      "A_FLIBBERTIJIBBET::a_will_o_the_wisp(a clown)", 0xbec774ea5dd935f3LL);
  function->size = 0x2922088f98d3f6fcLL;
  function->parameter_size = 0xe5e9aa008bd5f0d0LL;

  // Some source lines.  The module should not sort these.
  Module::Line line1 = { 0xbec774ea5dd935f3LL, 0x1c2be6d6c5af2611LL,
                         file1, 41676901 };
  function->lines.push_back(line1);

  m.AddFunction(function);

  // Some stack information.
  Module::StackFrameEntry *entry = new Module::StackFrameEntry();
  entry->address = 0x30f9e5c83323973dULL;
  entry->size = 0x49fc9ca7c7c13dc2ULL;
  entry->initial_rules[".cfa"] = "he was a handsome man";
  entry->initial_rules["and"] = "what i want to know is";
  entry->rule_changes[0x30f9e5c83323973eULL]["how"] =
    "do you like your blueeyed boy";
  entry->rule_changes[0x30f9e5c83323973eULL]["Mister"] = "Death";
  m.AddStackFrameEntry(entry);

  // Set the load address.  Doing this after adding all the data to
  // the module must work fine.
  m.SetLoadAddress(0x2ab698b0b6407073LL);

  m.Write(s, NO_CFI);
  string contents = s.str();
  EXPECT_STREQ("MODULE os-name architecture id-string name with spaces\n"
               "FILE 0 filename.cc\n"
               "FUNC 9410dc39a798c580 2922088f98d3f6fc e5e9aa008bd5f0d0"
               " A_FLIBBERTIJIBBET::a_will_o_the_wisp(a clown)\n"
               "9410dc39a798c580 1c2be6d6c5af2611 41676901 0\n",
               contents.c_str());
}

TEST(Construct, AddFunctions) {
  stringstream s;
  Module m(MODULE_NAME, MODULE_OS, MODULE_ARCH, MODULE_ID);

  // Two functions.
  Module::Function *function1 = new Module::Function(
      "_without_form", 0xd35024aa7ca7da5cLL);
  function1->size = 0x200b26e605f99071LL;
  function1->parameter_size = 0xf14ac4fed48c4a99LL;

  Module::Function *function2 = new Module::Function(
      "_and_void", 0x2987743d0b35b13fLL);
  function2->size = 0xb369db048deb3010LL;
  function2->parameter_size = 0x938e556cb5a79988LL;

  // Put them in a vector.
  vector<Module::Function *> vec;
  vec.push_back(function1);
  vec.push_back(function2);

  m.AddFunctions(vec.begin(), vec.end());

  m.Write(s, ALL_SYMBOL_DATA);
  string contents = s.str();
  EXPECT_STREQ("MODULE os-name architecture id-string name with spaces\n"
               "FUNC 2987743d0b35b13f b369db048deb3010 938e556cb5a79988"
               " _and_void\n"
               "FUNC d35024aa7ca7da5c 200b26e605f99071 f14ac4fed48c4a99"
               " _without_form\n",
               contents.c_str());

  // Check that m.GetFunctions returns the functions we expect.
  vec.clear();
  m.GetFunctions(&vec, vec.end());
  EXPECT_TRUE(vec.end() != find(vec.begin(), vec.end(), function1));
  EXPECT_TRUE(vec.end() != find(vec.begin(), vec.end(), function2));
  EXPECT_EQ((size_t) 2, vec.size());
}

TEST(Construct, AddFrames) {
  stringstream s;
  Module m(MODULE_NAME, MODULE_OS, MODULE_ARCH, MODULE_ID);

  // First STACK CFI entry, with no initial rules or deltas.
  Module::StackFrameEntry *entry1 = new Module::StackFrameEntry();
  entry1->address = 0xddb5f41285aa7757ULL;
  entry1->size = 0x1486493370dc5073ULL;
  m.AddStackFrameEntry(entry1);

  // Second STACK CFI entry, with initial rules but no deltas.
  Module::StackFrameEntry *entry2 = new Module::StackFrameEntry();
  entry2->address = 0x8064f3af5e067e38ULL;
  entry2->size = 0x0de2a5ee55509407ULL;
  entry2->initial_rules[".cfa"] = "I think that I shall never see";
  entry2->initial_rules["stromboli"] = "a poem lovely as a tree";
  entry2->initial_rules["cannoli"] = "a tree whose hungry mouth is prest";
  m.AddStackFrameEntry(entry2);

  // Third STACK CFI entry, with initial rules and deltas.
  Module::StackFrameEntry *entry3 = new Module::StackFrameEntry();
  entry3->address = 0x5e8d0db0a7075c6cULL;
  entry3->size = 0x1c7edb12a7aea229ULL;
  entry3->initial_rules[".cfa"] = "Whose woods are these";
  entry3->rule_changes[0x47ceb0f63c269d7fULL]["calzone"] =
    "the village though";
  entry3->rule_changes[0x47ceb0f63c269d7fULL]["cannoli"] =
    "he will not see me stopping here";
  entry3->rule_changes[0x36682fad3763ffffULL]["stromboli"] =
    "his house is in";
  entry3->rule_changes[0x36682fad3763ffffULL][".cfa"] =
    "I think I know";
  m.AddStackFrameEntry(entry3);

  // Check that Write writes STACK CFI records properly.
  m.Write(s, ALL_SYMBOL_DATA);
  string contents = s.str();
  EXPECT_STREQ("MODULE os-name architecture id-string name with spaces\n"
               "STACK CFI INIT ddb5f41285aa7757 1486493370dc5073 \n"
               "STACK CFI INIT 8064f3af5e067e38 de2a5ee55509407"
               " .cfa: I think that I shall never see"
               " cannoli: a tree whose hungry mouth is prest"
               " stromboli: a poem lovely as a tree\n"
               "STACK CFI INIT 5e8d0db0a7075c6c 1c7edb12a7aea229"
               " .cfa: Whose woods are these\n"
               "STACK CFI 36682fad3763ffff"
               " .cfa: I think I know"
               " stromboli: his house is in\n"
               "STACK CFI 47ceb0f63c269d7f"
               " calzone: the village though"
               " cannoli: he will not see me stopping here\n",
               contents.c_str());

  // Check that GetStackFrameEntries works.
  vector<Module::StackFrameEntry *> entries;
  m.GetStackFrameEntries(&entries);
  ASSERT_EQ(3U, entries.size());
  // Check first entry.
  EXPECT_EQ(0xddb5f41285aa7757ULL, entries[0]->address);
  EXPECT_EQ(0x1486493370dc5073ULL, entries[0]->size);
  ASSERT_EQ(0U, entries[0]->initial_rules.size());
  ASSERT_EQ(0U, entries[0]->rule_changes.size());
  // Check second entry.
  EXPECT_EQ(0x8064f3af5e067e38ULL, entries[1]->address);
  EXPECT_EQ(0x0de2a5ee55509407ULL, entries[1]->size);
  ASSERT_EQ(3U, entries[1]->initial_rules.size());
  Module::RuleMap entry2_initial;
  entry2_initial[".cfa"] = "I think that I shall never see";
  entry2_initial["stromboli"] = "a poem lovely as a tree";
  entry2_initial["cannoli"] = "a tree whose hungry mouth is prest";
  EXPECT_THAT(entries[1]->initial_rules, ContainerEq(entry2_initial));
  ASSERT_EQ(0U, entries[1]->rule_changes.size());
  // Check third entry.
  EXPECT_EQ(0x5e8d0db0a7075c6cULL, entries[2]->address);
  EXPECT_EQ(0x1c7edb12a7aea229ULL, entries[2]->size);
  Module::RuleMap entry3_initial;
  entry3_initial[".cfa"] = "Whose woods are these";
  EXPECT_THAT(entries[2]->initial_rules, ContainerEq(entry3_initial));
  Module::RuleChangeMap entry3_changes;
  entry3_changes[0x36682fad3763ffffULL][".cfa"] = "I think I know";
  entry3_changes[0x36682fad3763ffffULL]["stromboli"] = "his house is in";
  entry3_changes[0x47ceb0f63c269d7fULL]["calzone"] = "the village though";
  entry3_changes[0x47ceb0f63c269d7fULL]["cannoli"] =
    "he will not see me stopping here";
  EXPECT_THAT(entries[2]->rule_changes, ContainerEq(entry3_changes));
}

TEST(Construct, UniqueFiles) {
  Module m(MODULE_NAME, MODULE_OS, MODULE_ARCH, MODULE_ID);
  Module::File *file1 = m.FindFile("foo");
  Module::File *file2 = m.FindFile(string("bar"));
  Module::File *file3 = m.FindFile(string("foo"));
  Module::File *file4 = m.FindFile("bar");
  EXPECT_NE(file1, file2);
  EXPECT_EQ(file1, file3);
  EXPECT_EQ(file2, file4);
  EXPECT_EQ(file1, m.FindExistingFile("foo"));
  EXPECT_TRUE(m.FindExistingFile("baz") == NULL);
}

TEST(Construct, DuplicateFunctions) {
  stringstream s;
  Module m(MODULE_NAME, MODULE_OS, MODULE_ARCH, MODULE_ID);

  // Two functions.
  Module::Function *function1 = generate_duplicate_function("_without_form");
  Module::Function *function2 = generate_duplicate_function("_without_form");

  m.AddFunction(function1);
  m.AddFunction(function2);

  m.Write(s, ALL_SYMBOL_DATA);
  string contents = s.str();
  EXPECT_STREQ("MODULE os-name architecture id-string name with spaces\n"
               "FUNC d35402aac7a7ad5c 200b26e605f99071 f14ac4fed48c4a99"
               " _without_form\n",
               contents.c_str());
}

TEST(Construct, FunctionsWithSameAddress) {
  stringstream s;
  Module m(MODULE_NAME, MODULE_OS, MODULE_ARCH, MODULE_ID);

  // Two functions.
  Module::Function *function1 = generate_duplicate_function("_without_form");
  Module::Function *function2 = generate_duplicate_function("_and_void");

  m.AddFunction(function1);
  m.AddFunction(function2);

  m.Write(s, ALL_SYMBOL_DATA);
  string contents = s.str();
  EXPECT_STREQ("MODULE os-name architecture id-string name with spaces\n"
               "FUNC d35402aac7a7ad5c 200b26e605f99071 f14ac4fed48c4a99"
               " _and_void\n"
               "FUNC d35402aac7a7ad5c 200b26e605f99071 f14ac4fed48c4a99"
               " _without_form\n",
               contents.c_str());
}

// Externs should be written out as PUBLIC records, sorted by
// address.
TEST(Construct, Externs) {
  stringstream s;
  Module m(MODULE_NAME, MODULE_OS, MODULE_ARCH, MODULE_ID);

  // Two externs.
  Module::Extern *extern1 = new Module::Extern(0xffff);
  extern1->name = "_abc";
  Module::Extern *extern2 = new Module::Extern(0xaaaa);
  extern2->name = "_xyz";

  m.AddExtern(extern1);
  m.AddExtern(extern2);

  m.Write(s, ALL_SYMBOL_DATA);
  string contents = s.str();

  EXPECT_STREQ("MODULE " MODULE_OS " " MODULE_ARCH " "
               MODULE_ID " " MODULE_NAME "\n"
               "PUBLIC aaaa 0 _xyz\n"
               "PUBLIC ffff 0 _abc\n",
               contents.c_str());
}

// Externs with the same address should only keep the first entry
// added.
TEST(Construct, DuplicateExterns) {
  stringstream s;
  Module m(MODULE_NAME, MODULE_OS, MODULE_ARCH, MODULE_ID);

  // Two externs.
  Module::Extern *extern1 = new Module::Extern(0xffff);
  extern1->name = "_xyz";
  Module::Extern *extern2 = new Module::Extern(0xffff);
  extern2->name = "_abc";

  m.AddExtern(extern1);
  m.AddExtern(extern2);

  m.Write(s, ALL_SYMBOL_DATA);
  string contents = s.str();

  EXPECT_STREQ("MODULE " MODULE_OS " " MODULE_ARCH " "
               MODULE_ID " " MODULE_NAME "\n"
               "PUBLIC ffff 0 _xyz\n",
               contents.c_str());
}

// If there exists an extern and a function at the same address, only write
// out the FUNC entry.
TEST(Construct, FunctionsAndExternsWithSameAddress) {
  stringstream s;
  Module m(MODULE_NAME, MODULE_OS, MODULE_ARCH, MODULE_ID);

  // Two externs.
  Module::Extern* extern1 = new Module::Extern(0xabc0);
  extern1->name = "abc";
  Module::Extern* extern2 = new Module::Extern(0xfff0);
  extern2->name = "xyz";

  m.AddExtern(extern1);
  m.AddExtern(extern2);

  Module::Function* function = new Module::Function("_xyz", 0xfff0);
  function->size = 0x10;
  function->parameter_size = 0;
  m.AddFunction(function);

  m.Write(s, ALL_SYMBOL_DATA);
  string contents = s.str();

  EXPECT_STREQ("MODULE " MODULE_OS " " MODULE_ARCH " "
               MODULE_ID " " MODULE_NAME "\n"
               "FUNC fff0 10 0 _xyz\n"
               "PUBLIC abc0 0 abc\n",
               contents.c_str());
}

// If there exists an extern and a function at the same address, only write
// out the FUNC entry. For ARM THUMB, the extern that comes from the ELF
// symbol section has bit 0 set.
TEST(Construct, FunctionsAndThumbExternsWithSameAddress) {
  stringstream s;
  Module m(MODULE_NAME, MODULE_OS, "arm", MODULE_ID);

  // Two THUMB externs.
  Module::Extern* thumb_extern1 = new Module::Extern(0xabc1);
  thumb_extern1->name = "thumb_abc";
  Module::Extern* thumb_extern2 = new Module::Extern(0xfff1);
  thumb_extern2->name = "thumb_xyz";

  Module::Extern* arm_extern1 = new Module::Extern(0xcc00);
  arm_extern1->name = "arm_func";

  m.AddExtern(thumb_extern1);
  m.AddExtern(thumb_extern2);
  m.AddExtern(arm_extern1);

  // The corresponding function from the DWARF debug data have the actual
  // address.
  Module::Function* function = new Module::Function("_thumb_xyz", 0xfff0);
  function->size = 0x10;
  function->parameter_size = 0;
  m.AddFunction(function);

  m.Write(s, ALL_SYMBOL_DATA);
  string contents = s.str();

  EXPECT_STREQ("MODULE " MODULE_OS " arm "
               MODULE_ID " " MODULE_NAME "\n"
               "FUNC fff0 10 0 _thumb_xyz\n"
               "PUBLIC abc1 0 thumb_abc\n"
               "PUBLIC cc00 0 arm_func\n",
               contents.c_str());
}
