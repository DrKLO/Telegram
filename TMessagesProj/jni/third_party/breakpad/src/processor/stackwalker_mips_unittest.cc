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

// Original author: Gordana Cmiljanovic <gordana.cmiljanovic@imgtec.com>

// stackwalker_mips_unittest.cc: Unit tests for StackwalkerMIPS class.

#include <string.h>
#include <string>
#include <vector>

#include "breakpad_googletest_includes.h"
#include "common/test_assembler.h"
#include "common/using_std_string.h"
#include "google_breakpad/common/minidump_format.h"
#include "google_breakpad/processor/basic_source_line_resolver.h"
#include "google_breakpad/processor/call_stack.h"
#include "google_breakpad/processor/code_module.h"
#include "google_breakpad/processor/source_line_resolver_interface.h"
#include "google_breakpad/processor/stack_frame_cpu.h"
#include "processor/stackwalker_unittest_utils.h"
#include "processor/stackwalker_mips.h"
#include "processor/windows_frame_info.h"

using google_breakpad::BasicSourceLineResolver;
using google_breakpad::CallStack;
using google_breakpad::CodeModule;
using google_breakpad::StackFrameSymbolizer;
using google_breakpad::StackFrame;
using google_breakpad::StackFrameMIPS;
using google_breakpad::Stackwalker;
using google_breakpad::StackwalkerMIPS;
using google_breakpad::SystemInfo;
using google_breakpad::WindowsFrameInfo;
using google_breakpad::test_assembler::kLittleEndian;
using google_breakpad::test_assembler::Label;
using google_breakpad::test_assembler::Section;
using std::vector;
using testing::_;
using testing::AnyNumber;
using testing::Return;
using testing::SetArgumentPointee;
using testing::Test;

class StackwalkerMIPSFixture {
 public:
  StackwalkerMIPSFixture()
    : stack_section(kLittleEndian),
      // Give the two modules reasonable standard locations and names
      // for tests to play with.
      module1(0x00400000, 0x10000, "module1", "version1"),
      module2(0x00500000, 0x10000, "module2", "version2") {
    // Identify the system as a Linux system.
    system_info.os = "Linux";
    system_info.os_short = "linux";
    system_info.os_version = "Observant Opossum";  // Jealous Jellyfish
    system_info.cpu = "mips";
    system_info.cpu_info = "";

    // Put distinctive values in the raw CPU context.
    BrandContext(&raw_context);

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

    // Reset max_frames_scanned since it's static.
    Stackwalker::set_max_frames_scanned(1024);    
  }

  // Set the Breakpad symbol information that supplier should return for
  // MODULE to INFO.
  void SetModuleSymbols(MockCodeModule* module, const string& info) {
    size_t buffer_size;
    char* buffer = supplier.CopySymbolDataAndOwnTheCopy(info, &buffer_size);
    EXPECT_CALL(supplier, GetCStringSymbolData(module, &system_info, _, _, _))
      .WillRepeatedly(DoAll(SetArgumentPointee<3>(buffer),
                            SetArgumentPointee<4>(buffer_size),
                            Return(MockSymbolSupplier::FOUND)));
  }

  // Populate stack_region with the contents of stack_section. Use
  // stack_section.start() as the region's starting address.
  void RegionFromSection() {
    string contents;
    ASSERT_TRUE(stack_section.GetContents(&contents));
    stack_region.Init(stack_section.start().Value(), contents);
  }

  // Fill RAW_CONTEXT with pseudo-random data, for round-trip checking.
  void BrandContext(MDRawContextMIPS* raw_context) {
    uint8_t x = 173;
    for (size_t i = 0; i < sizeof(*raw_context); ++i)
      reinterpret_cast<uint8_t*>(raw_context)[i] = (x += 17);
  }

  SystemInfo system_info;
  MDRawContextMIPS raw_context;
  Section stack_section;
  MockMemoryRegion stack_region;
  MockCodeModule module1;
  MockCodeModule module2;
  MockCodeModules modules;
  MockSymbolSupplier supplier;
  BasicSourceLineResolver resolver;
  CallStack call_stack;
  const vector<StackFrame*>* frames;
};

class SanityCheck: public StackwalkerMIPSFixture, public Test { };

TEST_F(SanityCheck, NoResolver) {
  stack_section.start() = 0x80000000;
  stack_section.D32(0).D32(0x0);
  RegionFromSection();
  raw_context.epc = 0x00400020;
  raw_context.iregs[MD_CONTEXT_MIPS_REG_SP] = 0x80000000;

  StackFrameSymbolizer frame_symbolizer(NULL, NULL);
  StackwalkerMIPS walker(&system_info, &raw_context, &stack_region, &modules,
                        &frame_symbolizer);
  // This should succeed, even without a resolver or supplier.
  vector<const CodeModule*> modules_without_symbols;
  vector<const CodeModule*> modules_with_corrupt_symbols;
  ASSERT_TRUE(walker.Walk(&call_stack, &modules_without_symbols,
                          &modules_with_corrupt_symbols));
  ASSERT_EQ(1U, modules_without_symbols.size());
  ASSERT_EQ("module1", modules_without_symbols[0]->debug_file());
  ASSERT_EQ(0U, modules_with_corrupt_symbols.size());
  frames = call_stack.frames();
  ASSERT_EQ(1U, frames->size());
  StackFrameMIPS* frame = static_cast<StackFrameMIPS*>(frames->at(0));
  // Check that the values from the original raw context made it
  // through to the context in the stack frame.
  EXPECT_EQ(0, memcmp(&raw_context, &frame->context, sizeof(raw_context)));
}

class GetContextFrame: public StackwalkerMIPSFixture, public Test { };

TEST_F(GetContextFrame, Simple) {
  stack_section.start() = 0x80000000;
  stack_section.D32(0).D32(0x0);
  RegionFromSection();
  raw_context.epc = 0x00400020;
  raw_context.iregs[MD_CONTEXT_MIPS_REG_SP] = 0x80000000;

  StackFrameSymbolizer frame_symbolizer(&supplier, &resolver);
  StackwalkerMIPS walker(&system_info, &raw_context, &stack_region, &modules,
                         &frame_symbolizer);
  vector<const CodeModule*> modules_without_symbols;
  vector<const CodeModule*> modules_with_corrupt_symbols;
  ASSERT_TRUE(walker.Walk(&call_stack, &modules_without_symbols,
                          &modules_with_corrupt_symbols));
  ASSERT_EQ(1U, modules_without_symbols.size());
  ASSERT_EQ("module1", modules_without_symbols[0]->debug_file());
  ASSERT_EQ(0U, modules_with_corrupt_symbols.size());
  frames = call_stack.frames();
  StackFrameMIPS* frame = static_cast<StackFrameMIPS*>(frames->at(0));
  // Check that the values from the original raw context made it
  // through to the context in the stack frame.
  EXPECT_EQ(0, memcmp(&raw_context, &frame->context, sizeof(raw_context)));
}

// The stackwalker should be able to produce the context frame even
// without stack memory present.
TEST_F(GetContextFrame, NoStackMemory) {
  raw_context.epc = 0x00400020;
  raw_context.iregs[MD_CONTEXT_MIPS_REG_SP] = 0x80000000;

  StackFrameSymbolizer frame_symbolizer(&supplier, &resolver);
  StackwalkerMIPS walker(&system_info, &raw_context, NULL, &modules,
                         &frame_symbolizer);
  vector<const CodeModule*> modules_without_symbols;
  vector<const CodeModule*> modules_with_corrupt_symbols;
  ASSERT_TRUE(walker.Walk(&call_stack, &modules_without_symbols,
                          &modules_with_corrupt_symbols));
  ASSERT_EQ(1U, modules_without_symbols.size());
  ASSERT_EQ("module1", modules_without_symbols[0]->debug_file());
  ASSERT_EQ(0U, modules_with_corrupt_symbols.size());
  frames = call_stack.frames();
  StackFrameMIPS* frame = static_cast<StackFrameMIPS*>(frames->at(0));
  // Check that the values from the original raw context made it
  // through to the context in the stack frame.
  EXPECT_EQ(0, memcmp(&raw_context, &frame->context, sizeof(raw_context)));
}

class GetCallerFrame: public StackwalkerMIPSFixture, public Test { };

TEST_F(GetCallerFrame, ScanWithoutSymbols) {
  // When the stack walker resorts to scanning the stack,
  // only addresses located within loaded modules are
  // considered valid return addresses.
  // Force scanning through three frames to ensure that the
  // stack pointer is set properly in scan-recovered frames.
  stack_section.start() = 0x80000000;
  uint32_t return_address1 = 0x00400100;
  uint32_t return_address2 = 0x00400900;
  Label frame1_sp, frame2_sp;
  stack_section
    // frame 0
    .Append(16, 0)                      // space

    .D32(0x00490000)                    // junk that's not
    .D32(0x00600000)                    // a return address

    .D32(frame1_sp)                     // stack pointer
    .D32(return_address1)               // actual return address
    // frame 1
    .Mark(&frame1_sp)
    .Append(16, 0)                      // space

    .D32(0xF0000000)                    // more junk
    .D32(0x0000000D)

    .D32(frame2_sp)                     // stack pointer
    .D32(return_address2)               // actual return address
    // frame 2
    .Mark(&frame2_sp)
    .Append(32, 0);                     // end of stack
  RegionFromSection();

  raw_context.epc = 0x00405510;
  raw_context.iregs[MD_CONTEXT_MIPS_REG_SP] = stack_section.start().Value();
  raw_context.iregs[MD_CONTEXT_MIPS_REG_RA] = return_address1;

  StackFrameSymbolizer frame_symbolizer(&supplier, &resolver);
  StackwalkerMIPS walker(&system_info, &raw_context, &stack_region, &modules,
                         &frame_symbolizer);
  vector<const CodeModule*> modules_without_symbols;
  vector<const CodeModule*> modules_with_corrupt_symbols;
  ASSERT_TRUE(walker.Walk(&call_stack, &modules_without_symbols,
                          &modules_with_corrupt_symbols));
  ASSERT_EQ(1U, modules_without_symbols.size());
  ASSERT_EQ("module1", modules_without_symbols[0]->debug_file());
  ASSERT_EQ(0U, modules_with_corrupt_symbols.size());
  frames = call_stack.frames();
  ASSERT_EQ(3U, frames->size());

  StackFrameMIPS* frame0 = static_cast<StackFrameMIPS*>(frames->at(0));
  EXPECT_EQ(StackFrame::FRAME_TRUST_CONTEXT, frame0->trust);
  ASSERT_EQ(StackFrameMIPS::CONTEXT_VALID_ALL, frame0->context_validity);
  EXPECT_EQ(0, memcmp(&raw_context, &frame0->context, sizeof(raw_context)));

  StackFrameMIPS* frame1 = static_cast<StackFrameMIPS*>(frames->at(1));
  EXPECT_EQ(StackFrame::FRAME_TRUST_SCAN, frame1->trust);
  ASSERT_EQ((StackFrameMIPS::CONTEXT_VALID_PC |
             StackFrameMIPS::CONTEXT_VALID_SP |
             StackFrameMIPS::CONTEXT_VALID_FP |
             StackFrameMIPS::CONTEXT_VALID_RA),
            frame1->context_validity);
  EXPECT_EQ(return_address1 - 2 * sizeof(return_address1), frame1->context.epc);
  EXPECT_EQ(frame1_sp.Value(), frame1->context.iregs[MD_CONTEXT_MIPS_REG_SP]);

  StackFrameMIPS* frame2 = static_cast<StackFrameMIPS*>(frames->at(2));
  EXPECT_EQ(StackFrame::FRAME_TRUST_SCAN, frame2->trust);
  ASSERT_EQ((StackFrameMIPS::CONTEXT_VALID_PC |
             StackFrameMIPS::CONTEXT_VALID_SP |
             StackFrameMIPS::CONTEXT_VALID_FP |
             StackFrameMIPS::CONTEXT_VALID_RA),
            frame2->context_validity);
  EXPECT_EQ(return_address2 - 2 * sizeof(return_address2), frame2->context.epc);
  EXPECT_EQ(frame2_sp.Value(), frame2->context.iregs[MD_CONTEXT_MIPS_REG_SP]);
}

TEST_F(GetCallerFrame, ScanWithFunctionSymbols) {
  // During stack scanning, if a potential return address
  // is located within a loaded module that has symbols,
  // it is only considered a valid return address if it
  // lies within a function's bounds.
  stack_section.start() = 0x80000000;
  uint32_t return_address = 0x00500200;
  Label frame1_sp;
  stack_section
    // frame 0
    .Append(16, 0)                      // space

    .D32(0x00490000)                    // junk that's not
    .D32(0x00600000)                    // a return address
    
    .D32(0x00401000)                    // a couple of plausible addresses
    .D32(0x0050F000)                    // that are not within functions

    .D32(frame1_sp)                     // stack pointer
    .D32(return_address)                // actual return address
    // frame 1
    .Mark(&frame1_sp)
    .Append(32, 0);                     // end of stack
  RegionFromSection();

  raw_context.epc = 0x00400200;
  raw_context.iregs[MD_CONTEXT_MIPS_REG_SP] = stack_section.start().Value();
  raw_context.iregs[MD_CONTEXT_MIPS_REG_RA] = return_address;

  SetModuleSymbols(&module1,
                   // The youngest frame's function.
                   "FUNC 100 400 10 monotreme\n");
  SetModuleSymbols(&module2,
                   // The calling frame's function.
                   "FUNC 100 400 10 marsupial\n");

  StackFrameSymbolizer frame_symbolizer(&supplier, &resolver);
  StackwalkerMIPS walker(&system_info, &raw_context, &stack_region, &modules,
                         &frame_symbolizer);
  vector<const CodeModule*> modules_without_symbols;
  vector<const CodeModule*> modules_with_corrupt_symbols;
  ASSERT_TRUE(walker.Walk(&call_stack, &modules_without_symbols,
                          &modules_with_corrupt_symbols));
  ASSERT_EQ(0U, modules_without_symbols.size());
  ASSERT_EQ(0U, modules_with_corrupt_symbols.size());
  frames = call_stack.frames();
  ASSERT_EQ(2U, frames->size());

  StackFrameMIPS* frame0 = static_cast<StackFrameMIPS*>(frames->at(0));
  EXPECT_EQ(StackFrame::FRAME_TRUST_CONTEXT, frame0->trust);
  ASSERT_EQ(StackFrameMIPS::CONTEXT_VALID_ALL, frame0->context_validity);
  EXPECT_EQ(0, memcmp(&raw_context, &frame0->context, sizeof(raw_context)));
  EXPECT_EQ("monotreme", frame0->function_name);
  EXPECT_EQ(0x00400100U, frame0->function_base);

  StackFrameMIPS* frame1 = static_cast<StackFrameMIPS*>(frames->at(1));
  EXPECT_EQ(StackFrame::FRAME_TRUST_SCAN, frame1->trust);
  ASSERT_EQ((StackFrameMIPS::CONTEXT_VALID_PC |
             StackFrameMIPS::CONTEXT_VALID_SP |
             StackFrameMIPS::CONTEXT_VALID_FP |
             StackFrameMIPS::CONTEXT_VALID_RA),
            frame1->context_validity);
  EXPECT_EQ(return_address - 2 * sizeof(return_address), frame1->context.epc);
  EXPECT_EQ(frame1_sp.Value(), frame1->context.iregs[MD_CONTEXT_MIPS_REG_SP]);
  EXPECT_EQ("marsupial", frame1->function_name);
  EXPECT_EQ(0x00500100U, frame1->function_base);
}

TEST_F(GetCallerFrame, CheckStackFrameSizeLimit) {
  // If the stackwalker resorts to stack scanning, it will scan only
  // 1024 bytes of stack which correspondes to maximum size of stack frame.
  stack_section.start() = 0x80000000;
  uint32_t return_address1 = 0x00500100;
  uint32_t return_address2 = 0x00500900;
  Label frame1_sp, frame2_sp;
  stack_section
    // frame 0
    .Append(32, 0)                      // space

    .D32(0x00490000)                    // junk that's not
    .D32(0x00600000)                    // a return address

    .Append(96, 0)                      // more space

    .D32(frame1_sp)                     // stack pointer
    .D32(return_address1)               // actual return address
    // frame 1
    .Mark(&frame1_sp)
    .Append(128 * 4, 0)                 // space

    .D32(0x00F00000)                    // more junk
    .D32(0x0000000D)

    .Append(128 * 4, 0)                 // more space

    .D32(frame2_sp)                     // stack pointer
    .D32(return_address2)               // actual return address
                                        // (won't be found)
    // frame 2
    .Mark(&frame2_sp)
    .Append(32, 0);                     // end of stack
  RegionFromSection();

  raw_context.epc = 0x00405510;
  raw_context.iregs[MD_CONTEXT_MIPS_REG_SP] = stack_section.start().Value();
  raw_context.iregs[MD_CONTEXT_MIPS_REG_RA] = return_address1;

  StackFrameSymbolizer frame_symbolizer(&supplier, &resolver);
  StackwalkerMIPS walker(&system_info, &raw_context, &stack_region, &modules,
                         &frame_symbolizer);
  vector<const CodeModule*> modules_without_symbols;
  vector<const CodeModule*> modules_with_corrupt_symbols;
  ASSERT_TRUE(walker.Walk(&call_stack, &modules_without_symbols,
                          &modules_with_corrupt_symbols));
  ASSERT_EQ(2U, modules_without_symbols.size());
  ASSERT_EQ("module1", modules_without_symbols[0]->debug_file());
  ASSERT_EQ("module2", modules_without_symbols[1]->debug_file());
  ASSERT_EQ(0U, modules_with_corrupt_symbols.size());
  frames = call_stack.frames();
  ASSERT_EQ(2U, frames->size());

  StackFrameMIPS* frame0 = static_cast<StackFrameMIPS*>(frames->at(0));
  EXPECT_EQ(StackFrame::FRAME_TRUST_CONTEXT, frame0->trust);
  ASSERT_EQ(StackFrameMIPS::CONTEXT_VALID_ALL, frame0->context_validity);
  EXPECT_EQ(0, memcmp(&raw_context, &frame0->context, sizeof(raw_context)));

  StackFrameMIPS* frame1 = static_cast<StackFrameMIPS*>(frames->at(1));
  EXPECT_EQ(StackFrame::FRAME_TRUST_SCAN, frame1->trust);
  ASSERT_EQ((StackFrameMIPS::CONTEXT_VALID_PC |
             StackFrameMIPS::CONTEXT_VALID_SP |
             StackFrameMIPS::CONTEXT_VALID_FP |
             StackFrameMIPS::CONTEXT_VALID_RA),
            frame1->context_validity);
  EXPECT_EQ(return_address1 - 2 * sizeof(return_address1), frame1->context.epc);
  EXPECT_EQ(frame1_sp.Value(), frame1->context.iregs[MD_CONTEXT_MIPS_REG_SP]);
}

// Test that set_max_frames_scanned prevents using stack scanning
// to find caller frames.
TEST_F(GetCallerFrame, ScanningNotAllowed) {
  // When the stack walker resorts to scanning the stack,
  // only fixed number of frames are allowed to be scanned out from stack
  stack_section.start() = 0x80000000;
  uint32_t return_address1 = 0x00500100;
  uint32_t return_address2 = 0x00500900;
  Label frame1_sp, frame2_sp;
  stack_section
    // frame 0
    .Append(32, 0)                      // space

    .D32(0x00490000)                    // junk that's not
    .D32(0x00600000)                    // a return address

    .Append(96, 0)                      // more space

    .D32(frame1_sp)                     // stack pointer
    .D32(return_address1)               // actual return address
    // frame 1
    .Mark(&frame1_sp)
    .Append(128 * 4, 0)                 // space

    .D32(0x00F00000)                    // more junk
    .D32(0x0000000D)

    .Append(128 * 4, 0)                 // more space

    .D32(frame2_sp)                     // stack pointer
    .D32(return_address2)               // actual return address
                                        // (won't be found)
    // frame 2
    .Mark(&frame2_sp)
    .Append(32, 0);                     // end of stack
  RegionFromSection();

  raw_context.epc = 0x00405510;
  raw_context.iregs[MD_CONTEXT_MIPS_REG_SP] = stack_section.start().Value();
  raw_context.iregs[MD_CONTEXT_MIPS_REG_RA] = return_address1;

  StackFrameSymbolizer frame_symbolizer(&supplier, &resolver);
  StackwalkerMIPS walker(&system_info, &raw_context, &stack_region, &modules,
                         &frame_symbolizer);
  Stackwalker::set_max_frames_scanned(0);
                         
  vector<const CodeModule*> modules_without_symbols;
  vector<const CodeModule*> modules_with_corrupt_symbols;
  ASSERT_TRUE(walker.Walk(&call_stack, &modules_without_symbols,
                          &modules_with_corrupt_symbols));
  ASSERT_EQ(1U, modules_without_symbols.size());
  ASSERT_EQ("module1", modules_without_symbols[0]->debug_file());
  ASSERT_EQ(0U, modules_with_corrupt_symbols.size());
  frames = call_stack.frames();
  ASSERT_EQ(1U, frames->size());

  StackFrameMIPS* frame0 = static_cast<StackFrameMIPS*>(frames->at(0));
  EXPECT_EQ(StackFrame::FRAME_TRUST_CONTEXT, frame0->trust);
  ASSERT_EQ(StackFrameMIPS::CONTEXT_VALID_ALL, frame0->context_validity);
  EXPECT_EQ(0, memcmp(&raw_context, &frame0->context, sizeof(raw_context)));
}

struct CFIFixture: public StackwalkerMIPSFixture {
  CFIFixture() {
    // Provide some STACK CFI records;
    SetModuleSymbols(&module1,
                     // The youngest frame's function.
                     "FUNC 4000 1000 0 enchiridion\n"
                     // Initially, nothing has been pushed on the stack,
                     // and the return address is still in the $ra register.
                     "STACK CFI INIT 4000 1000 .cfa: $sp 0 + .ra: $ra\n"
                     // Move stack pointer.
                     "STACK CFI 4004 .cfa: $sp 32 +\n"
                     // store $fp and ra
                     "STACK CFI 4008 $fp: .cfa -8 + ^ .ra: .cfa -4 + ^\n"
                     // restore $fp
                     "STACK CFI 400c .cfa: $fp 32 +\n"
                     // restore $sp
                     "STACK CFI 4018 .cfa: $sp 32 +\n"

                     "STACK CFI 4020 $fp: $fp .cfa: $sp 0 + .ra: .ra\n"

                     // The calling function.
                     "FUNC 5000 1000 0 epictetus\n"
                     // Initially, nothing has been pushed on the stack,
                     // and the return address is still in the $ra register.
                     "STACK CFI INIT 5000 1000 .cfa: $sp .ra: $ra\n"
                     // Mark it as end of stack.
                     "STACK CFI INIT 5000 8 .cfa: $sp 0 + .ra: $ra\n"

                     // A function whose CFI makes the stack pointer
                     // go backwards.
                     "FUNC 6000 1000 20 palinal\n"
                     "STACK CFI INIT 6000 1000 .cfa: $sp 4 - .ra: $ra\n"

                     // A function with CFI expressions that can't be
                     // evaluated.
                     "FUNC 7000 1000 20 rhetorical\n"
                     "STACK CFI INIT 7000 1000 .cfa: moot .ra: ambiguous\n"
                   );

    // Provide some distinctive values for the caller's registers.
    expected.epc = 0x00405508;
    expected.iregs[MD_CONTEXT_MIPS_REG_S0] = 0x0;
    expected.iregs[MD_CONTEXT_MIPS_REG_S1] = 0x1;
    expected.iregs[MD_CONTEXT_MIPS_REG_S2] = 0x2;
    expected.iregs[MD_CONTEXT_MIPS_REG_S3] = 0x3;
    expected.iregs[MD_CONTEXT_MIPS_REG_S4] = 0x4;
    expected.iregs[MD_CONTEXT_MIPS_REG_S5] = 0x5;
    expected.iregs[MD_CONTEXT_MIPS_REG_S6] = 0x6;
    expected.iregs[MD_CONTEXT_MIPS_REG_S7] = 0x7;
    expected.iregs[MD_CONTEXT_MIPS_REG_SP] = 0x80000000;
    expected.iregs[MD_CONTEXT_MIPS_REG_FP] = 0x80000000;
    expected.iregs[MD_CONTEXT_MIPS_REG_RA] = 0x00405510;

    // Expect CFI to recover all callee-save registers. Since CFI is the
    // only stack frame construction technique we have, aside from the
    // context frame itself, there's no way for us to have a set of valid
    // registers smaller than this.
    expected_validity = (StackFrameMIPS::CONTEXT_VALID_PC |
                         StackFrameMIPS::CONTEXT_VALID_S0 |
                         StackFrameMIPS::CONTEXT_VALID_S1 |
                         StackFrameMIPS::CONTEXT_VALID_S2 |
                         StackFrameMIPS::CONTEXT_VALID_S3 |
                         StackFrameMIPS::CONTEXT_VALID_S4 |
                         StackFrameMIPS::CONTEXT_VALID_S5 |
                         StackFrameMIPS::CONTEXT_VALID_S6 |
                         StackFrameMIPS::CONTEXT_VALID_S7 |
                         StackFrameMIPS::CONTEXT_VALID_SP |
                         StackFrameMIPS::CONTEXT_VALID_FP |
                         StackFrameMIPS::CONTEXT_VALID_RA);

    // By default, context frames provide all registers, as normal.
    context_frame_validity = StackFrameMIPS::CONTEXT_VALID_ALL;

    // By default, registers are unchanged.
    raw_context = expected;
  }

  // Walk the stack, using stack_section as the contents of the stack
  // and raw_context as the current register values. (Set the stack
  // pointer to the stack's starting address.) Expect two stack
  // frames; in the older frame, expect the callee-saves registers to
  // have values matching those in 'expected'.
  void CheckWalk() {
    RegionFromSection();
    raw_context.iregs[MD_CONTEXT_MIPS_REG_SP] = stack_section.start().Value();

    StackFrameSymbolizer frame_symbolizer(&supplier, &resolver);
    StackwalkerMIPS walker(&system_info, &raw_context, &stack_region,
                           &modules, &frame_symbolizer);
    vector<const CodeModule*> modules_without_symbols;
    vector<const CodeModule*> modules_with_corrupt_symbols;
    ASSERT_TRUE(walker.Walk(&call_stack, &modules_without_symbols,
                            &modules_with_corrupt_symbols));
    ASSERT_EQ(0U, modules_without_symbols.size());
    ASSERT_EQ(0U, modules_with_corrupt_symbols.size());
    frames = call_stack.frames();
    ASSERT_EQ(2U, frames->size());

    StackFrameMIPS* frame0 = static_cast<StackFrameMIPS*>(frames->at(0));
    EXPECT_EQ(StackFrame::FRAME_TRUST_CONTEXT, frame0->trust);
    ASSERT_EQ(StackFrameMIPS::CONTEXT_VALID_ALL, frame0->context_validity);
    EXPECT_EQ("enchiridion", frame0->function_name);
    EXPECT_EQ(0x00404000U, frame0->function_base);

    StackFrameMIPS* frame1 = static_cast<StackFrameMIPS*>(frames->at(1));
    EXPECT_EQ(StackFrame::FRAME_TRUST_CFI, frame1->trust);
    ASSERT_EQ(expected_validity, frame1->context_validity);
    EXPECT_EQ(expected.iregs[MD_CONTEXT_MIPS_REG_S0],
              frame1->context.iregs[MD_CONTEXT_MIPS_REG_S0]);
    EXPECT_EQ(expected.iregs[MD_CONTEXT_MIPS_REG_S1],
              frame1->context.iregs[MD_CONTEXT_MIPS_REG_S1]);
    EXPECT_EQ(expected.iregs[MD_CONTEXT_MIPS_REG_S2],
              frame1->context.iregs[MD_CONTEXT_MIPS_REG_S2]);
    EXPECT_EQ(expected.iregs[MD_CONTEXT_MIPS_REG_S3],
              frame1->context.iregs[MD_CONTEXT_MIPS_REG_S3]);
    EXPECT_EQ(expected.iregs[MD_CONTEXT_MIPS_REG_S4],
              frame1->context.iregs[MD_CONTEXT_MIPS_REG_S4]);
    EXPECT_EQ(expected.iregs[MD_CONTEXT_MIPS_REG_S5],
              frame1->context.iregs[MD_CONTEXT_MIPS_REG_S5]);
    EXPECT_EQ(expected.iregs[MD_CONTEXT_MIPS_REG_S6],
              frame1->context.iregs[MD_CONTEXT_MIPS_REG_S6]);
    EXPECT_EQ(expected.iregs[MD_CONTEXT_MIPS_REG_S7],
              frame1->context.iregs[MD_CONTEXT_MIPS_REG_S7]);
    EXPECT_EQ(expected.iregs[MD_CONTEXT_MIPS_REG_FP],
              frame1->context.iregs[MD_CONTEXT_MIPS_REG_FP]);
    EXPECT_EQ(expected.iregs[MD_CONTEXT_MIPS_REG_RA],
              frame1->context.iregs[MD_CONTEXT_MIPS_REG_RA]);
    EXPECT_EQ(expected.iregs[MD_CONTEXT_MIPS_REG_SP],
              frame1->context.iregs[MD_CONTEXT_MIPS_REG_SP]);
    EXPECT_EQ(expected.epc, frame1->context.epc);
    EXPECT_EQ(expected.epc, frame1->instruction);
    EXPECT_EQ("epictetus", frame1->function_name);
    EXPECT_EQ(0x00405000U, frame1->function_base);    
  }

  // The values we expect to find for the caller's registers.
  MDRawContextMIPS expected;

  // The validity mask for expected.
  int expected_validity;

  // The validity mask to impose on the context frame.
  int context_frame_validity;
};

class CFI: public CFIFixture, public Test { };

// TODO(gordanac): add CFI tests

TEST_F(CFI, At4004) {
  Label frame1_sp = expected.iregs[MD_CONTEXT_MIPS_REG_SP];
  stack_section
    // frame0
    .Append(24, 0)               // space
    .D32(frame1_sp)              // stack pointer
    .D32(0x00405510)             // return address
    .Mark(&frame1_sp);           // This effectively sets stack_section.start().
  raw_context.epc = 0x00404004;
  CheckWalk();
}

// Check that we reject rules that would cause the stack pointer to
// move in the wrong direction.
TEST_F(CFI, RejectBackwards) {
  raw_context.epc = 0x40005000;
  raw_context.iregs[MD_CONTEXT_MIPS_REG_SP] = 0x80000000;
  raw_context.iregs[MD_CONTEXT_MIPS_REG_RA] = 0x00405510;
  
  StackFrameSymbolizer frame_symbolizer(&supplier, &resolver);
  StackwalkerMIPS walker(&system_info, &raw_context, &stack_region, &modules,
                         &frame_symbolizer);
  vector<const CodeModule*> modules_without_symbols;
  vector<const CodeModule*> modules_with_corrupt_symbols;
  ASSERT_TRUE(walker.Walk(&call_stack, &modules_without_symbols,
                          &modules_with_corrupt_symbols));
  ASSERT_EQ(0U, modules_without_symbols.size());
  ASSERT_EQ(0U, modules_with_corrupt_symbols.size());
  frames = call_stack.frames();
  ASSERT_EQ(1U, frames->size());
}

// Check that we reject rules whose expressions' evaluation fails.
TEST_F(CFI, RejectBadExpressions) {
  raw_context.epc = 0x00407000;
  raw_context.iregs[MD_CONTEXT_MIPS_REG_SP] = 0x80000000;
  raw_context.iregs[MD_CONTEXT_MIPS_REG_RA] = 0x00405510;
  
  StackFrameSymbolizer frame_symbolizer(&supplier, &resolver);
  StackwalkerMIPS walker(&system_info, &raw_context, &stack_region, &modules,
                         &frame_symbolizer);
  vector<const CodeModule*> modules_without_symbols;
  vector<const CodeModule*> modules_with_corrupt_symbols;
  ASSERT_TRUE(walker.Walk(&call_stack, &modules_without_symbols,
                          &modules_with_corrupt_symbols));
  ASSERT_EQ(0U, modules_without_symbols.size());
  ASSERT_EQ(0U, modules_with_corrupt_symbols.size());
  frames = call_stack.frames();
  ASSERT_EQ(1U, frames->size());
}
