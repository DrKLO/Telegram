// Copyright (c) 2013, Google Inc.
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

// stackwalker_address_list_unittest.cc: Unit tests for the
// StackwalkerAddressList class.
//
// Author: Chris Hamilton <chrisha@chromium.org>

#include <string>
#include <vector>

#include "breakpad_googletest_includes.h"
#include "common/using_std_string.h"
#include "google_breakpad/common/minidump_format.h"
#include "google_breakpad/processor/basic_source_line_resolver.h"
#include "google_breakpad/processor/call_stack.h"
#include "google_breakpad/processor/code_module.h"
#include "google_breakpad/processor/source_line_resolver_interface.h"
#include "google_breakpad/processor/stack_frame.h"
#include "processor/stackwalker_unittest_utils.h"
#include "processor/stackwalker_address_list.h"

using google_breakpad::BasicSourceLineResolver;
using google_breakpad::CallStack;
using google_breakpad::CodeModule;
using google_breakpad::StackFrameSymbolizer;
using google_breakpad::StackFrame;
using google_breakpad::Stackwalker;
using google_breakpad::StackwalkerAddressList;
using std::vector;
using testing::_;
using testing::AnyNumber;
using testing::Return;
using testing::SetArgumentPointee;

#define arraysize(f) (sizeof(f) / sizeof(*f))

// Addresses and sizes of a couple dummy modules.
uint64_t kModule1Base = 0x40000000;
uint64_t kModule1Size = 0x10000;
uint64_t kModule2Base = 0x50000000;
uint64_t kModule2Size = 0x10000;

// A handful of addresses that lie within the modules above.
const uint64_t kDummyFrames[] = {
    0x50003000, 0x50002000, 0x50001000, 0x40002000, 0x40001000 };

class StackwalkerAddressListTest : public testing::Test {
 public:
  StackwalkerAddressListTest()
    : // Give the two modules reasonable standard locations and names
      // for tests to play with.
      module1(kModule1Base, kModule1Size, "module1", "version1"),
      module2(kModule2Base, kModule2Size, "module2", "version2") {
    // Create some modules with some stock debugging information.
    modules.Add(&module1);
    modules.Add(&module2);

    // By default, none of the modules have symbol info; call
    // SetModuleSymbols to override this.
    EXPECT_CALL(supplier, GetCStringSymbolData(_, _, _, _, _))
      .WillRepeatedly(Return(MockSymbolSupplier::NOT_FOUND));

    // Avoid GMOCK WARNING "Uninteresting mock function call - returning
    // directly" for FreeSymbolData().
    EXPECT_CALL(supplier, FreeSymbolData(_)).Times(AnyNumber());
  }

  // Set the Breakpad symbol information that supplier should return for
  // MODULE to INFO.
  void SetModuleSymbols(MockCodeModule *module, const string &info) {
    size_t buffer_size;
    char *buffer = supplier.CopySymbolDataAndOwnTheCopy(info, &buffer_size);
    EXPECT_CALL(supplier, GetCStringSymbolData(module, NULL, _, _, _))
      .WillRepeatedly(DoAll(SetArgumentPointee<3>(buffer),
                            SetArgumentPointee<4>(buffer_size),
                            Return(MockSymbolSupplier::FOUND)));
  }

  void CheckCallStack(const CallStack& call_stack) {
    const std::vector<StackFrame*>* frames = call_stack.frames();
    ASSERT_EQ(arraysize(kDummyFrames), frames->size());
    for (size_t i = 0; i < arraysize(kDummyFrames); ++i) {
      ASSERT_EQ(kDummyFrames[i], frames->at(i)->instruction);
      ASSERT_EQ(StackFrame::FRAME_TRUST_PREWALKED, frames->at(i)->trust);
    }
    ASSERT_EQ(static_cast<const CodeModule*>(&module2), frames->at(0)->module);
    ASSERT_EQ(static_cast<const CodeModule*>(&module2), frames->at(1)->module);
    ASSERT_EQ(static_cast<const CodeModule*>(&module2), frames->at(2)->module);
    ASSERT_EQ(static_cast<const CodeModule*>(&module1), frames->at(3)->module);
    ASSERT_EQ(static_cast<const CodeModule*>(&module1), frames->at(4)->module);
  }

  MockCodeModule module1;
  MockCodeModule module2;
  MockCodeModules modules;
  MockSymbolSupplier supplier;
  BasicSourceLineResolver resolver;
};

TEST_F(StackwalkerAddressListTest, ScanWithoutSymbols) {
  StackFrameSymbolizer frame_symbolizer(&supplier, &resolver);
  StackwalkerAddressList walker(kDummyFrames, arraysize(kDummyFrames),
                         &modules, &frame_symbolizer);

  CallStack call_stack;
  vector<const CodeModule*> modules_without_symbols;
  vector<const CodeModule*> modules_with_corrupt_symbols;
  ASSERT_TRUE(walker.Walk(&call_stack, &modules_without_symbols,
                          &modules_with_corrupt_symbols));

  // The stack starts in module2, so we expect that to be the first module
  // found without symbols.
  ASSERT_EQ(2U, modules_without_symbols.size());
  ASSERT_EQ("module2", modules_without_symbols[0]->debug_file());
  ASSERT_EQ("module1", modules_without_symbols[1]->debug_file());
  ASSERT_EQ(0u, modules_with_corrupt_symbols.size());

  ASSERT_NO_FATAL_FAILURE(CheckCallStack(call_stack));
}

TEST_F(StackwalkerAddressListTest, ScanWithSymbols) {
  // File    : FILE number(dex) name
  // Function: FUNC address(hex) size(hex) parameter_size(hex) name
  // Line    : address(hex) size(hex) line(dec) filenum(dec)
  SetModuleSymbols(&module2,
                   "FILE 1 module2.cc\n"
                   "FUNC 3000 100 10 mod2func3\n"
                   "3000 10 1  1\n"
                   "FUNC 2000 200 10 mod2func2\n"
                   "FUNC 1000 300 10 mod2func1\n");
  SetModuleSymbols(&module1,
                   "FUNC 2000 200 10 mod1func2\n"
                   "FUNC 1000 300 10 mod1func1\n");

  StackFrameSymbolizer frame_symbolizer(&supplier, &resolver);
  StackwalkerAddressList walker(kDummyFrames, arraysize(kDummyFrames),
                         &modules, &frame_symbolizer);

  CallStack call_stack;
  vector<const CodeModule*> modules_without_symbols;
  vector<const CodeModule*> modules_with_corrupt_symbols;
  ASSERT_TRUE(walker.Walk(&call_stack, &modules_without_symbols,
                          &modules_with_corrupt_symbols));

  ASSERT_EQ(0u, modules_without_symbols.size());
  ASSERT_EQ(0u, modules_with_corrupt_symbols.size());

  ASSERT_NO_FATAL_FAILURE(CheckCallStack(call_stack));

  const std::vector<StackFrame*>* frames = call_stack.frames();

  // We have full file/line information for the first function call.
  ASSERT_EQ("mod2func3", frames->at(0)->function_name);
  ASSERT_EQ(0x50003000u, frames->at(0)->function_base);
  ASSERT_EQ("module2.cc", frames->at(0)->source_file_name);
  ASSERT_EQ(1, frames->at(0)->source_line);
  ASSERT_EQ(0x50003000u, frames->at(0)->source_line_base);

  ASSERT_EQ("mod2func2", frames->at(1)->function_name);
  ASSERT_EQ(0x50002000u, frames->at(1)->function_base);

  ASSERT_EQ("mod2func1", frames->at(2)->function_name);
  ASSERT_EQ(0x50001000u, frames->at(2)->function_base);

  ASSERT_EQ("mod1func2", frames->at(3)->function_name);
  ASSERT_EQ(0x40002000u, frames->at(3)->function_base);

  ASSERT_EQ("mod1func1", frames->at(4)->function_name);
  ASSERT_EQ(0x40001000u, frames->at(4)->function_base);
}
