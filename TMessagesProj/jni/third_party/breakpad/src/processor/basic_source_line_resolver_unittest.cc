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

#include <assert.h>
#include <stdio.h>

#include <string>

#include "breakpad_googletest_includes.h"
#include "common/scoped_ptr.h"
#include "common/using_std_string.h"
#include "google_breakpad/processor/basic_source_line_resolver.h"
#include "google_breakpad/processor/code_module.h"
#include "google_breakpad/processor/stack_frame.h"
#include "google_breakpad/processor/memory_region.h"
#include "processor/linked_ptr.h"
#include "processor/logging.h"
#include "processor/windows_frame_info.h"
#include "processor/cfi_frame_info.h"

namespace {

using google_breakpad::BasicSourceLineResolver;
using google_breakpad::CFIFrameInfo;
using google_breakpad::CodeModule;
using google_breakpad::MemoryRegion;
using google_breakpad::StackFrame;
using google_breakpad::WindowsFrameInfo;
using google_breakpad::linked_ptr;
using google_breakpad::scoped_ptr;
using google_breakpad::SymbolParseHelper;

class TestCodeModule : public CodeModule {
 public:
  TestCodeModule(string code_file) : code_file_(code_file) {}
  virtual ~TestCodeModule() {}

  virtual uint64_t base_address() const { return 0; }
  virtual uint64_t size() const { return 0xb000; }
  virtual string code_file() const { return code_file_; }
  virtual string code_identifier() const { return ""; }
  virtual string debug_file() const { return ""; }
  virtual string debug_identifier() const { return ""; }
  virtual string version() const { return ""; }
  virtual const CodeModule* Copy() const {
    return new TestCodeModule(code_file_);
  }

 private:
  string code_file_;
};

// A mock memory region object, for use by the STACK CFI tests.
class MockMemoryRegion: public MemoryRegion {
  uint64_t GetBase() const { return 0x10000; }
  uint32_t GetSize() const { return 0x01000; }
  bool GetMemoryAtAddress(uint64_t address, uint8_t *value) const {
    *value = address & 0xff;
    return true;
  }
  bool GetMemoryAtAddress(uint64_t address, uint16_t *value) const {
    *value = address & 0xffff;
    return true;
  }
  bool GetMemoryAtAddress(uint64_t address, uint32_t *value) const {
    switch (address) {
      case 0x10008: *value = 0x98ecadc3; break; // saved %ebx
      case 0x1000c: *value = 0x878f7524; break; // saved %esi
      case 0x10010: *value = 0x6312f9a5; break; // saved %edi
      case 0x10014: *value = 0x10038;    break; // caller's %ebp
      case 0x10018: *value = 0xf6438648; break; // return address
      default: *value = 0xdeadbeef;      break; // junk
    }
    return true;
  }
  bool GetMemoryAtAddress(uint64_t address, uint64_t *value) const {
    *value = address;
    return true;
  }
  void Print() const {
    assert(false);
  }
};

// Verify that, for every association in ACTUAL, EXPECTED has the same
// association. (That is, ACTUAL's associations should be a subset of
// EXPECTED's.) Also verify that ACTUAL has associations for ".ra" and
// ".cfa".
static bool VerifyRegisters(
    const char *file, int line,
    const CFIFrameInfo::RegisterValueMap<uint32_t> &expected,
    const CFIFrameInfo::RegisterValueMap<uint32_t> &actual) {
  CFIFrameInfo::RegisterValueMap<uint32_t>::const_iterator a;
  a = actual.find(".cfa");
  if (a == actual.end())
    return false;
  a = actual.find(".ra");
  if (a == actual.end())
    return false;
  for (a = actual.begin(); a != actual.end(); a++) {
    CFIFrameInfo::RegisterValueMap<uint32_t>::const_iterator e =
      expected.find(a->first);
    if (e == expected.end()) {
      fprintf(stderr, "%s:%d: unexpected register '%s' recovered, value 0x%x\n",
              file, line, a->first.c_str(), a->second);
      return false;
    }
    if (e->second != a->second) {
      fprintf(stderr,
              "%s:%d: register '%s' recovered value was 0x%x, expected 0x%x\n",
              file, line, a->first.c_str(), a->second, e->second);
      return false;
    }
    // Don't complain if this doesn't recover all registers. Although
    // the DWARF spec says that unmentioned registers are undefined,
    // GCC uses omission to mean that they are unchanged.
  }
  return true;
}


static bool VerifyEmpty(const StackFrame &frame) {
  if (frame.function_name.empty() &&
      frame.source_file_name.empty() &&
      frame.source_line == 0)
    return true;
  return false;
}

static void ClearSourceLineInfo(StackFrame *frame) {
  frame->function_name.clear();
  frame->module = NULL;
  frame->source_file_name.clear();
  frame->source_line = 0;
}

class TestBasicSourceLineResolver : public ::testing::Test {
public:
  void SetUp() {
    testdata_dir = string(getenv("srcdir") ? getenv("srcdir") : ".") +
                         "/src/processor/testdata";
  }

  BasicSourceLineResolver resolver;
  string testdata_dir;
};

TEST_F(TestBasicSourceLineResolver, TestLoadAndResolve)
{
  TestCodeModule module1("module1");
  ASSERT_TRUE(resolver.LoadModule(&module1, testdata_dir + "/module1.out"));
  ASSERT_TRUE(resolver.HasModule(&module1));
  TestCodeModule module2("module2");
  ASSERT_TRUE(resolver.LoadModule(&module2, testdata_dir + "/module2.out"));
  ASSERT_TRUE(resolver.HasModule(&module2));


  StackFrame frame;
  scoped_ptr<WindowsFrameInfo> windows_frame_info;
  scoped_ptr<CFIFrameInfo> cfi_frame_info;
  frame.instruction = 0x1000;
  frame.module = NULL;
  resolver.FillSourceLineInfo(&frame);
  ASSERT_FALSE(frame.module);
  ASSERT_TRUE(frame.function_name.empty());
  ASSERT_EQ(frame.function_base, 0U);
  ASSERT_TRUE(frame.source_file_name.empty());
  ASSERT_EQ(frame.source_line, 0);
  ASSERT_EQ(frame.source_line_base, 0U);

  frame.module = &module1;
  resolver.FillSourceLineInfo(&frame);
  ASSERT_EQ(frame.function_name, "Function1_1");
  ASSERT_TRUE(frame.module);
  ASSERT_EQ(frame.module->code_file(), "module1");
  ASSERT_EQ(frame.function_base, 0x1000U);
  ASSERT_EQ(frame.source_file_name, "file1_1.cc");
  ASSERT_EQ(frame.source_line, 44);
  ASSERT_EQ(frame.source_line_base, 0x1000U);
  windows_frame_info.reset(resolver.FindWindowsFrameInfo(&frame));
  ASSERT_TRUE(windows_frame_info.get());
  ASSERT_EQ(windows_frame_info->type_, WindowsFrameInfo::STACK_INFO_FRAME_DATA);
  ASSERT_FALSE(windows_frame_info->allocates_base_pointer);
  ASSERT_EQ(windows_frame_info->program_string,
            "$eip 4 + ^ = $esp $ebp 8 + = $ebp $ebp ^ =");

  ClearSourceLineInfo(&frame);
  frame.instruction = 0x800;
  frame.module = &module1;
  resolver.FillSourceLineInfo(&frame);
  ASSERT_TRUE(VerifyEmpty(frame));
  windows_frame_info.reset(resolver.FindWindowsFrameInfo(&frame));
  ASSERT_FALSE(windows_frame_info.get());

  frame.instruction = 0x1280;
  resolver.FillSourceLineInfo(&frame);
  ASSERT_EQ(frame.function_name, "Function1_3");
  ASSERT_TRUE(frame.source_file_name.empty());
  ASSERT_EQ(frame.source_line, 0);
  windows_frame_info.reset(resolver.FindWindowsFrameInfo(&frame));
  ASSERT_TRUE(windows_frame_info.get());
  ASSERT_EQ(windows_frame_info->type_, WindowsFrameInfo::STACK_INFO_UNKNOWN);
  ASSERT_FALSE(windows_frame_info->allocates_base_pointer);
  ASSERT_TRUE(windows_frame_info->program_string.empty());

  frame.instruction = 0x1380;
  resolver.FillSourceLineInfo(&frame);
  ASSERT_EQ(frame.function_name, "Function1_4");
  ASSERT_TRUE(frame.source_file_name.empty());
  ASSERT_EQ(frame.source_line, 0);
  windows_frame_info.reset(resolver.FindWindowsFrameInfo(&frame));
  ASSERT_EQ(windows_frame_info->type_, WindowsFrameInfo::STACK_INFO_FRAME_DATA);
  ASSERT_TRUE(windows_frame_info.get());
  ASSERT_FALSE(windows_frame_info->allocates_base_pointer);
  ASSERT_FALSE(windows_frame_info->program_string.empty());

  frame.instruction = 0x2000;
  windows_frame_info.reset(resolver.FindWindowsFrameInfo(&frame));
  ASSERT_FALSE(windows_frame_info.get());

  // module1 has STACK CFI records covering 3d40..3def;
  // module2 has STACK CFI records covering 3df0..3e9f;
  // check that FindCFIFrameInfo doesn't claim to find any outside those ranges.
  frame.instruction = 0x3d3f;
  frame.module = &module1;
  cfi_frame_info.reset(resolver.FindCFIFrameInfo(&frame));
  ASSERT_FALSE(cfi_frame_info.get());

  frame.instruction = 0x3e9f;
  frame.module = &module1;
  cfi_frame_info.reset(resolver.FindCFIFrameInfo(&frame));
  ASSERT_FALSE(cfi_frame_info.get());

  CFIFrameInfo::RegisterValueMap<uint32_t> current_registers;
  CFIFrameInfo::RegisterValueMap<uint32_t> caller_registers;
  CFIFrameInfo::RegisterValueMap<uint32_t> expected_caller_registers;
  MockMemoryRegion memory;

  // Regardless of which instruction evaluation takes place at, it
  // should produce the same values for the caller's registers.
  expected_caller_registers[".cfa"] = 0x1001c;
  expected_caller_registers[".ra"]  = 0xf6438648;
  expected_caller_registers["$ebp"] = 0x10038;
  expected_caller_registers["$ebx"] = 0x98ecadc3;
  expected_caller_registers["$esi"] = 0x878f7524;
  expected_caller_registers["$edi"] = 0x6312f9a5;

  frame.instruction = 0x3d40;
  frame.module = &module1;
  current_registers.clear();
  current_registers["$esp"] = 0x10018;
  current_registers["$ebp"] = 0x10038;
  current_registers["$ebx"] = 0x98ecadc3;
  current_registers["$esi"] = 0x878f7524;
  current_registers["$edi"] = 0x6312f9a5;
  cfi_frame_info.reset(resolver.FindCFIFrameInfo(&frame));
  ASSERT_TRUE(cfi_frame_info.get());
  ASSERT_TRUE(cfi_frame_info.get()
              ->FindCallerRegs<uint32_t>(current_registers, memory,
                                          &caller_registers));
  ASSERT_TRUE(VerifyRegisters(__FILE__, __LINE__,
                              expected_caller_registers, caller_registers));

  frame.instruction = 0x3d41;
  current_registers["$esp"] = 0x10014;
  cfi_frame_info.reset(resolver.FindCFIFrameInfo(&frame));
  ASSERT_TRUE(cfi_frame_info.get());
  ASSERT_TRUE(cfi_frame_info.get()
              ->FindCallerRegs<uint32_t>(current_registers, memory,
                                          &caller_registers));
  ASSERT_TRUE(VerifyRegisters(__FILE__, __LINE__,
                              expected_caller_registers, caller_registers));

  frame.instruction = 0x3d43;
  current_registers["$ebp"] = 0x10014;
  cfi_frame_info.reset(resolver.FindCFIFrameInfo(&frame));
  ASSERT_TRUE(cfi_frame_info.get());
  ASSERT_TRUE(cfi_frame_info.get()
              ->FindCallerRegs<uint32_t>(current_registers, memory,
                                          &caller_registers));
  VerifyRegisters(__FILE__, __LINE__,
                  expected_caller_registers, caller_registers);

  frame.instruction = 0x3d54;
  current_registers["$ebx"] = 0x6864f054U;
  cfi_frame_info.reset(resolver.FindCFIFrameInfo(&frame));
  ASSERT_TRUE(cfi_frame_info.get());
  ASSERT_TRUE(cfi_frame_info.get()
              ->FindCallerRegs<uint32_t>(current_registers, memory,
                                          &caller_registers));
  VerifyRegisters(__FILE__, __LINE__,
                  expected_caller_registers, caller_registers);

  frame.instruction = 0x3d5a;
  current_registers["$esi"] = 0x6285f79aU;
  cfi_frame_info.reset(resolver.FindCFIFrameInfo(&frame));
  ASSERT_TRUE(cfi_frame_info.get());
  ASSERT_TRUE(cfi_frame_info.get()
              ->FindCallerRegs<uint32_t>(current_registers, memory,
                                          &caller_registers));
  VerifyRegisters(__FILE__, __LINE__,
                  expected_caller_registers, caller_registers);

  frame.instruction = 0x3d84;
  current_registers["$edi"] = 0x64061449U;
  cfi_frame_info.reset(resolver.FindCFIFrameInfo(&frame));
  ASSERT_TRUE(cfi_frame_info.get());
  ASSERT_TRUE(cfi_frame_info.get()
              ->FindCallerRegs<uint32_t>(current_registers, memory,
                                          &caller_registers));
  VerifyRegisters(__FILE__, __LINE__,
                  expected_caller_registers, caller_registers);

  frame.instruction = 0x2900;
  frame.module = &module1;
  resolver.FillSourceLineInfo(&frame);
  ASSERT_EQ(frame.function_name, string("PublicSymbol"));

  frame.instruction = 0x4000;
  frame.module = &module1;
  resolver.FillSourceLineInfo(&frame);
  ASSERT_EQ(frame.function_name, string("LargeFunction"));

  frame.instruction = 0x2181;
  frame.module = &module2;
  resolver.FillSourceLineInfo(&frame);
  ASSERT_EQ(frame.function_name, "Function2_2");
  ASSERT_EQ(frame.function_base, 0x2170U);
  ASSERT_TRUE(frame.module);
  ASSERT_EQ(frame.module->code_file(), "module2");
  ASSERT_EQ(frame.source_file_name, "file2_2.cc");
  ASSERT_EQ(frame.source_line, 21);
  ASSERT_EQ(frame.source_line_base, 0x2180U);
  windows_frame_info.reset(resolver.FindWindowsFrameInfo(&frame));
  ASSERT_TRUE(windows_frame_info.get());
  ASSERT_EQ(windows_frame_info->type_, WindowsFrameInfo::STACK_INFO_FRAME_DATA);
  ASSERT_EQ(windows_frame_info->prolog_size, 1U);

  frame.instruction = 0x216f;
  resolver.FillSourceLineInfo(&frame);
  ASSERT_EQ(frame.function_name, "Public2_1");

  ClearSourceLineInfo(&frame);
  frame.instruction = 0x219f;
  frame.module = &module2;
  resolver.FillSourceLineInfo(&frame);
  ASSERT_TRUE(frame.function_name.empty());

  frame.instruction = 0x21a0;
  frame.module = &module2;
  resolver.FillSourceLineInfo(&frame);
  ASSERT_EQ(frame.function_name, "Public2_2");
}

TEST_F(TestBasicSourceLineResolver, TestInvalidLoads)
{
  TestCodeModule module3("module3");
  ASSERT_TRUE(resolver.LoadModule(&module3,
                                   testdata_dir + "/module3_bad.out"));
  ASSERT_TRUE(resolver.HasModule(&module3));
  ASSERT_TRUE(resolver.IsModuleCorrupt(&module3));
  TestCodeModule module4("module4");
  ASSERT_TRUE(resolver.LoadModule(&module4,
                                   testdata_dir + "/module4_bad.out"));
  ASSERT_TRUE(resolver.HasModule(&module4));
  ASSERT_TRUE(resolver.IsModuleCorrupt(&module4));
  TestCodeModule module5("module5");
  ASSERT_FALSE(resolver.LoadModule(&module5,
                                   testdata_dir + "/invalid-filename"));
  ASSERT_FALSE(resolver.HasModule(&module5));
  TestCodeModule invalidmodule("invalid-module");
  ASSERT_FALSE(resolver.HasModule(&invalidmodule));
}

TEST_F(TestBasicSourceLineResolver, TestUnload)
{
  TestCodeModule module1("module1");
  ASSERT_FALSE(resolver.HasModule(&module1));
  ASSERT_TRUE(resolver.LoadModule(&module1, testdata_dir + "/module1.out"));
  ASSERT_TRUE(resolver.HasModule(&module1));
  resolver.UnloadModule(&module1);
  ASSERT_FALSE(resolver.HasModule(&module1));
  ASSERT_TRUE(resolver.LoadModule(&module1, testdata_dir + "/module1.out"));
  ASSERT_TRUE(resolver.HasModule(&module1));
}

// Test parsing of valid FILE lines.  The format is:
// FILE <id> <filename>
TEST(SymbolParseHelper, ParseFileValid) {
  long index;
  char *filename;

  char kTestLine[] = "FILE 1 file name";
  ASSERT_TRUE(SymbolParseHelper::ParseFile(kTestLine, &index, &filename));
  EXPECT_EQ(1, index);
  EXPECT_EQ("file name", string(filename));

  // 0 is a valid index.
  char kTestLine1[] = "FILE 0 file name";
  ASSERT_TRUE(SymbolParseHelper::ParseFile(kTestLine1, &index, &filename));
  EXPECT_EQ(0, index);
  EXPECT_EQ("file name", string(filename));
}

// Test parsing of invalid FILE lines.  The format is:
// FILE <id> <filename>
TEST(SymbolParseHelper, ParseFileInvalid) {
  long index;
  char *filename;

  // Test missing file name.
  char kTestLine[] = "FILE 1 ";
  ASSERT_FALSE(SymbolParseHelper::ParseFile(kTestLine, &index, &filename));

  // Test bad index.
  char kTestLine1[] = "FILE x1 file name";
  ASSERT_FALSE(SymbolParseHelper::ParseFile(kTestLine1, &index, &filename));

  // Test large index.
  char kTestLine2[] = "FILE 123123123123123123123123 file name";
  ASSERT_FALSE(SymbolParseHelper::ParseFile(kTestLine2, &index, &filename));

  // Test negative index.
  char kTestLine3[] = "FILE -2 file name";
  ASSERT_FALSE(SymbolParseHelper::ParseFile(kTestLine3, &index, &filename));
}

// Test parsing of valid FUNC lines.  The format is:
// FUNC <address> <size> <stack_param_size> <name>
TEST(SymbolParseHelper, ParseFunctionValid) {
  uint64_t address;
  uint64_t size;
  long stack_param_size;
  char *name;

  char kTestLine[] = "FUNC 1 2 3 function name";
  ASSERT_TRUE(SymbolParseHelper::ParseFunction(kTestLine, &address, &size,
                                               &stack_param_size, &name));
  EXPECT_EQ(1ULL, address);
  EXPECT_EQ(2ULL, size);
  EXPECT_EQ(3, stack_param_size);
  EXPECT_EQ("function name", string(name));

  // Test hex address, size, and param size.
  char kTestLine1[] = "FUNC a1 a2 a3 function name";
  ASSERT_TRUE(SymbolParseHelper::ParseFunction(kTestLine1, &address, &size,
                                               &stack_param_size, &name));
  EXPECT_EQ(0xa1ULL, address);
  EXPECT_EQ(0xa2ULL, size);
  EXPECT_EQ(0xa3, stack_param_size);
  EXPECT_EQ("function name", string(name));

  char kTestLine2[] = "FUNC 0 0 0 function name";
  ASSERT_TRUE(SymbolParseHelper::ParseFunction(kTestLine2, &address, &size,
                                               &stack_param_size, &name));
  EXPECT_EQ(0ULL, address);
  EXPECT_EQ(0ULL, size);
  EXPECT_EQ(0, stack_param_size);
  EXPECT_EQ("function name", string(name));
}

// Test parsing of invalid FUNC lines.  The format is:
// FUNC <address> <size> <stack_param_size> <name>
TEST(SymbolParseHelper, ParseFunctionInvalid) {
  uint64_t address;
  uint64_t size;
  long stack_param_size;
  char *name;

  // Test missing function name.
  char kTestLine[] = "FUNC 1 2 3 ";
  ASSERT_FALSE(SymbolParseHelper::ParseFunction(kTestLine, &address, &size,
                                                &stack_param_size, &name));
  // Test bad address.
  char kTestLine1[] = "FUNC 1z 2 3 function name";
  ASSERT_FALSE(SymbolParseHelper::ParseFunction(kTestLine1, &address, &size,
                                                &stack_param_size, &name));
  // Test large address.
  char kTestLine2[] = "FUNC 123123123123123123123123123 2 3 function name";
  ASSERT_FALSE(SymbolParseHelper::ParseFunction(kTestLine2, &address, &size,
                                                &stack_param_size, &name));
  // Test bad size.
  char kTestLine3[] = "FUNC 1 z2 3 function name";
  ASSERT_FALSE(SymbolParseHelper::ParseFunction(kTestLine3, &address, &size,
                                                &stack_param_size, &name));
  // Test large size.
  char kTestLine4[] = "FUNC 1 231231231231231231231231232 3 function name";
  ASSERT_FALSE(SymbolParseHelper::ParseFunction(kTestLine4, &address, &size,
                                                &stack_param_size, &name));
  // Test bad param size.
  char kTestLine5[] = "FUNC 1 2 3z function name";
  ASSERT_FALSE(SymbolParseHelper::ParseFunction(kTestLine5, &address, &size,
                                                &stack_param_size, &name));
  // Test large param size.
  char kTestLine6[] = "FUNC 1 2 312312312312312312312312323 function name";
  ASSERT_FALSE(SymbolParseHelper::ParseFunction(kTestLine6, &address, &size,
                                                &stack_param_size, &name));
  // Negative param size.
  char kTestLine7[] = "FUNC 1 2 -5 function name";
  ASSERT_FALSE(SymbolParseHelper::ParseFunction(kTestLine7, &address, &size,
                                                &stack_param_size, &name));
}

// Test parsing of valid lines.  The format is:
// <address> <size> <line number> <source file id>
TEST(SymbolParseHelper, ParseLineValid) {
  uint64_t address;
  uint64_t size;
  long line_number;
  long source_file;

  char kTestLine[] = "1 2 3 4";
  ASSERT_TRUE(SymbolParseHelper::ParseLine(kTestLine, &address, &size,
                                           &line_number, &source_file));
  EXPECT_EQ(1ULL, address);
  EXPECT_EQ(2ULL, size);
  EXPECT_EQ(3, line_number);
  EXPECT_EQ(4, source_file);

  // Test hex size and address.
  char kTestLine1[] = "a1 a2 3 4  // some comment";
  ASSERT_TRUE(SymbolParseHelper::ParseLine(kTestLine1, &address, &size,
                                           &line_number, &source_file));
  EXPECT_EQ(0xa1ULL, address);
  EXPECT_EQ(0xa2ULL, size);
  EXPECT_EQ(3, line_number);
  EXPECT_EQ(4, source_file);

  // 0 is a valid line number.
  char kTestLine2[] = "a1 a2 0 4  // some comment";
  ASSERT_TRUE(SymbolParseHelper::ParseLine(kTestLine2, &address, &size,
                                           &line_number, &source_file));
  EXPECT_EQ(0xa1ULL, address);
  EXPECT_EQ(0xa2ULL, size);
  EXPECT_EQ(0, line_number);
  EXPECT_EQ(4, source_file);
}

// Test parsing of invalid lines.  The format is:
// <address> <size> <line number> <source file id>
TEST(SymbolParseHelper, ParseLineInvalid) {
  uint64_t address;
  uint64_t size;
  long line_number;
  long source_file;

  // Test missing source file id.
  char kTestLine[] = "1 2 3";
  ASSERT_FALSE(SymbolParseHelper::ParseLine(kTestLine, &address, &size,
                                            &line_number, &source_file));
  // Test bad address.
  char kTestLine1[] = "1z 2 3 4";
  ASSERT_FALSE(SymbolParseHelper::ParseLine(kTestLine1, &address, &size,
                                            &line_number, &source_file));
  // Test large address.
  char kTestLine2[] = "123123123123123123123123 2 3 4";
  ASSERT_FALSE(SymbolParseHelper::ParseLine(kTestLine2, &address, &size,
                                            &line_number, &source_file));
  // Test bad size.
  char kTestLine3[] = "1 z2 3 4";
  ASSERT_FALSE(SymbolParseHelper::ParseLine(kTestLine3, &address, &size,
                                            &line_number, &source_file));
  // Test large size.
  char kTestLine4[] = "1 123123123123123123123123 3 4";
  ASSERT_FALSE(SymbolParseHelper::ParseLine(kTestLine4, &address, &size,
                                            &line_number, &source_file));
  // Test bad line number.
  char kTestLine5[] = "1 2 z3 4";
  ASSERT_FALSE(SymbolParseHelper::ParseLine(kTestLine5, &address, &size,
                                            &line_number, &source_file));
  // Test negative line number.
  char kTestLine6[] = "1 2 -1 4";
  ASSERT_FALSE(SymbolParseHelper::ParseLine(kTestLine6, &address, &size,
                                            &line_number, &source_file));
  // Test large line number.
  char kTestLine7[] = "1 2 123123123123123123123 4";
  ASSERT_FALSE(SymbolParseHelper::ParseLine(kTestLine7, &address, &size,
                                            &line_number, &source_file));
  // Test bad source file id.
  char kTestLine8[] = "1 2 3 f";
  ASSERT_FALSE(SymbolParseHelper::ParseLine(kTestLine8, &address, &size,
                                            &line_number, &source_file));
}

// Test parsing of valid PUBLIC lines.  The format is:
// PUBLIC <address> <stack_param_size> <name>
TEST(SymbolParseHelper, ParsePublicSymbolValid) {
  uint64_t address;
  long stack_param_size;
  char *name;

  char kTestLine[] = "PUBLIC 1 2 3";
  ASSERT_TRUE(SymbolParseHelper::ParsePublicSymbol(kTestLine, &address,
                                                   &stack_param_size, &name));
  EXPECT_EQ(1ULL, address);
  EXPECT_EQ(2, stack_param_size);
  EXPECT_EQ("3", string(name));

  // Test hex size and address.
  char kTestLine1[] = "PUBLIC a1 a2 function name";
  ASSERT_TRUE(SymbolParseHelper::ParsePublicSymbol(kTestLine1, &address,
                                                   &stack_param_size, &name));
  EXPECT_EQ(0xa1ULL, address);
  EXPECT_EQ(0xa2, stack_param_size);
  EXPECT_EQ("function name", string(name));

  // Test 0 is a valid address.
  char kTestLine2[] = "PUBLIC 0 a2 function name";
  ASSERT_TRUE(SymbolParseHelper::ParsePublicSymbol(kTestLine2, &address,
                                                   &stack_param_size, &name));
  EXPECT_EQ(0ULL, address);
  EXPECT_EQ(0xa2, stack_param_size);
  EXPECT_EQ("function name", string(name));
}

// Test parsing of invalid PUBLIC lines.  The format is:
// PUBLIC <address> <stack_param_size> <name>
TEST(SymbolParseHelper, ParsePublicSymbolInvalid) {
  uint64_t address;
  long stack_param_size;
  char *name;

  // Test missing source function name.
  char kTestLine[] = "PUBLIC 1 2 ";
  ASSERT_FALSE(SymbolParseHelper::ParsePublicSymbol(kTestLine, &address,
                                                    &stack_param_size, &name));
  // Test bad address.
  char kTestLine1[] = "PUBLIC 1z 2 3";
  ASSERT_FALSE(SymbolParseHelper::ParsePublicSymbol(kTestLine1, &address,
                                                    &stack_param_size, &name));
  // Test large address.
  char kTestLine2[] = "PUBLIC 123123123123123123123123 2 3";
  ASSERT_FALSE(SymbolParseHelper::ParsePublicSymbol(kTestLine2, &address,
                                                    &stack_param_size, &name));
  // Test bad param stack size.
  char kTestLine3[] = "PUBLIC 1 z2 3";
  ASSERT_FALSE(SymbolParseHelper::ParsePublicSymbol(kTestLine3, &address,
                                                    &stack_param_size, &name));
  // Test large param stack size.
  char kTestLine4[] = "PUBLIC 1 123123123123123123123123123 3";
  ASSERT_FALSE(SymbolParseHelper::ParsePublicSymbol(kTestLine4, &address,
                                                    &stack_param_size, &name));
  // Test negative param stack size.
  char kTestLine5[] = "PUBLIC 1 -5 3";
  ASSERT_FALSE(SymbolParseHelper::ParsePublicSymbol(kTestLine5, &address,
                                                    &stack_param_size, &name));
}

}  // namespace

int main(int argc, char *argv[]) {
  ::testing::InitGoogleTest(&argc, argv);
  return RUN_ALL_TESTS();
}
