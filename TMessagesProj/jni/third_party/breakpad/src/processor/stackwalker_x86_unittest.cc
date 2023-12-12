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

// stackwalker_x86_unittest.cc: Unit tests for StackwalkerX86 class.

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
#include "processor/stackwalker_x86.h"
#include "processor/windows_frame_info.h"

using google_breakpad::BasicSourceLineResolver;
using google_breakpad::CallStack;
using google_breakpad::CodeModule;
using google_breakpad::StackFrameSymbolizer;
using google_breakpad::StackFrame;
using google_breakpad::StackFrameX86;
using google_breakpad::Stackwalker;
using google_breakpad::StackwalkerX86;
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

class StackwalkerX86Fixture {
 public:
  StackwalkerX86Fixture()
    : stack_section(kLittleEndian),
      // Give the two modules reasonable standard locations and names
      // for tests to play with.
      module1(0x40000000, 0x10000, "module1", "version1"),
      module2(0x50000000, 0x10000, "module2", "version2"),
      module3(0x771d0000, 0x180000, "module3", "version3"),
      module4(0x75f90000, 0x46000, "module4", "version4"),
      module5(0x75730000, 0x110000, "module5", "version5"),
      module6(0x647f0000, 0x1ba8000, "module6", "version6") {
    // Identify the system as a Linux system.
    system_info.os = "Linux";
    system_info.os_short = "linux";
    system_info.os_version = "Salacious Skink";
    system_info.cpu = "x86";
    system_info.cpu_info = "";

    // Put distinctive values in the raw CPU context.
    BrandContext(&raw_context);

    // Create some modules with some stock debugging information.
    modules.Add(&module1);
    modules.Add(&module2);
    modules.Add(&module3);
    modules.Add(&module4);
    modules.Add(&module5);
    modules.Add(&module6);

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
  void SetModuleSymbols(MockCodeModule *module, const string &info) {
    size_t buffer_size;
    char *buffer = supplier.CopySymbolDataAndOwnTheCopy(info, &buffer_size);
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
  void BrandContext(MDRawContextX86 *raw_context) {
    uint8_t x = 173;
    for (size_t i = 0; i < sizeof(*raw_context); i++)
      reinterpret_cast<uint8_t *>(raw_context)[i] = (x += 17);
  }

  SystemInfo system_info;
  MDRawContextX86 raw_context;
  Section stack_section;
  MockMemoryRegion stack_region;
  MockCodeModule module1;
  MockCodeModule module2;
  MockCodeModule module3;
  MockCodeModule module4;
  MockCodeModule module5;
  MockCodeModule module6;
  MockCodeModules modules;
  MockSymbolSupplier supplier;
  BasicSourceLineResolver resolver;
  CallStack call_stack;
  const vector<StackFrame *> *frames;
};

class SanityCheck: public StackwalkerX86Fixture, public Test { };

TEST_F(SanityCheck, NoResolver) {
  stack_section.start() = 0x80000000;
  stack_section.D32(0).D32(0);  // end-of-stack marker
  RegionFromSection();
  raw_context.eip = 0x40000200;
  raw_context.ebp = 0x80000000;

  StackFrameSymbolizer frame_symbolizer(NULL, NULL);
  StackwalkerX86 walker(&system_info, &raw_context, &stack_region, &modules,
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
  StackFrameX86 *frame = static_cast<StackFrameX86 *>(frames->at(0));
  // Check that the values from the original raw context made it
  // through to the context in the stack frame.
  EXPECT_EQ(0, memcmp(&raw_context, &frame->context, sizeof(raw_context)));
}

class GetContextFrame: public StackwalkerX86Fixture, public Test { };

TEST_F(GetContextFrame, Simple) {
  stack_section.start() = 0x80000000;
  stack_section.D32(0).D32(0);  // end-of-stack marker
  RegionFromSection();
  raw_context.eip = 0x40000200;
  raw_context.ebp = 0x80000000;

  StackFrameSymbolizer frame_symbolizer(&supplier, &resolver);
  StackwalkerX86 walker(&system_info, &raw_context, &stack_region, &modules,
                        &frame_symbolizer);
  vector<const CodeModule*> modules_without_symbols;
  vector<const CodeModule*> modules_with_corrupt_symbols;
  ASSERT_TRUE(walker.Walk(&call_stack, &modules_without_symbols,
                          &modules_with_corrupt_symbols));
  ASSERT_EQ(1U, modules_without_symbols.size());
  ASSERT_EQ("module1", modules_without_symbols[0]->debug_file());
  ASSERT_EQ(0U, modules_with_corrupt_symbols.size());
  frames = call_stack.frames();
  StackFrameX86 *frame = static_cast<StackFrameX86 *>(frames->at(0));
  // Check that the values from the original raw context made it
  // through to the context in the stack frame.
  EXPECT_EQ(0, memcmp(&raw_context, &frame->context, sizeof(raw_context)));
}

// The stackwalker should be able to produce the context frame even
// without stack memory present.
TEST_F(GetContextFrame, NoStackMemory) {
  raw_context.eip = 0x40000200;
  raw_context.ebp = 0x80000000;

  StackFrameSymbolizer frame_symbolizer(&supplier, &resolver);
  StackwalkerX86 walker(&system_info, &raw_context, NULL, &modules,
                        &frame_symbolizer);
  vector<const CodeModule*> modules_without_symbols;
  vector<const CodeModule*> modules_with_corrupt_symbols;
  ASSERT_TRUE(walker.Walk(&call_stack, &modules_without_symbols,
                          &modules_with_corrupt_symbols));
  ASSERT_EQ(1U, modules_without_symbols.size());
  ASSERT_EQ("module1", modules_without_symbols[0]->debug_file());
  ASSERT_EQ(0U, modules_with_corrupt_symbols.size());
  frames = call_stack.frames();
  StackFrameX86 *frame = static_cast<StackFrameX86 *>(frames->at(0));
  // Check that the values from the original raw context made it
  // through to the context in the stack frame.
  EXPECT_EQ(0, memcmp(&raw_context, &frame->context, sizeof(raw_context)));
}

class GetCallerFrame: public StackwalkerX86Fixture, public Test {
 protected:
  void IPAddressIsNotInKnownModuleTestImpl(bool has_corrupt_symbols);
};

// Walk a traditional frame. A traditional frame saves the caller's
// %ebp just below the return address, and has its own %ebp pointing
// at the saved %ebp.
TEST_F(GetCallerFrame, Traditional) {
  stack_section.start() = 0x80000000;
  Label frame0_ebp, frame1_ebp;
  stack_section
    .Append(12, 0)                      // frame 0: space
    .Mark(&frame0_ebp)                  // frame 0 %ebp points here
    .D32(frame1_ebp)                    // frame 0: saved %ebp
    .D32(0x40008679)                    // frame 0: return address
    .Append(8, 0)                       // frame 1: space
    .Mark(&frame1_ebp)                  // frame 1 %ebp points here
    .D32(0)                             // frame 1: saved %ebp (stack end)
    .D32(0);                            // frame 1: return address (stack end)
  RegionFromSection();
  raw_context.eip = 0x4000c7a5;
  raw_context.esp = stack_section.start().Value();
  raw_context.ebp = frame0_ebp.Value();

  StackFrameSymbolizer frame_symbolizer(&supplier, &resolver);
  StackwalkerX86 walker(&system_info, &raw_context, &stack_region, &modules,
                        &frame_symbolizer);
  vector<const CodeModule*> modules_without_symbols;
  vector<const CodeModule*> modules_with_corrupt_symbols;
  ASSERT_TRUE(walker.Walk(&call_stack, &modules_without_symbols,
                          &modules_with_corrupt_symbols));
  ASSERT_EQ(1U, modules_without_symbols.size());
  ASSERT_EQ("module1", modules_without_symbols[0]->debug_file());
  ASSERT_EQ(0U, modules_with_corrupt_symbols.size());
  frames = call_stack.frames();
  ASSERT_EQ(2U, frames->size());

  {  // To avoid reusing locals by mistake
    StackFrameX86 *frame0 = static_cast<StackFrameX86 *>(frames->at(0));
    EXPECT_EQ(StackFrame::FRAME_TRUST_CONTEXT, frame0->trust);
    EXPECT_EQ(StackFrameX86::CONTEXT_VALID_ALL, frame0->context_validity);
    EXPECT_EQ(0x4000c7a5U, frame0->instruction);
    EXPECT_EQ(0x4000c7a5U, frame0->context.eip);
    EXPECT_EQ(frame0_ebp.Value(), frame0->context.ebp);
    EXPECT_EQ(NULL, frame0->windows_frame_info);
  }

  {  // To avoid reusing locals by mistake
    StackFrameX86 *frame1 = static_cast<StackFrameX86 *>(frames->at(1));
    EXPECT_EQ(StackFrame::FRAME_TRUST_FP, frame1->trust);
    ASSERT_EQ((StackFrameX86::CONTEXT_VALID_EIP
               | StackFrameX86::CONTEXT_VALID_ESP
               | StackFrameX86::CONTEXT_VALID_EBP),
              frame1->context_validity);
    EXPECT_EQ(0x40008679U, frame1->instruction + 1);
    EXPECT_EQ(0x40008679U, frame1->context.eip);
    EXPECT_EQ(frame1_ebp.Value(), frame1->context.ebp);
    EXPECT_EQ(NULL, frame1->windows_frame_info);
  }
}

// Walk a traditional frame, but use a bogus %ebp value, forcing a scan
// of the stack for something that looks like a return address.
TEST_F(GetCallerFrame, TraditionalScan) {
  stack_section.start() = 0x80000000;
  Label frame1_ebp;
  Label frame1_esp;
  stack_section
    // frame 0
    .D32(0xf065dc76)    // locals area:
    .D32(0x46ee2167)    // garbage that doesn't look like
    .D32(0xbab023ec)    // a return address
    .D32(frame1_ebp)    // saved %ebp (%ebp fails to point here, forcing scan)
    .D32(0x4000129d)    // return address
    // frame 1
    .Mark(&frame1_esp)
    .Append(8, 0)       // space
    .Mark(&frame1_ebp)  // %ebp points here
    .D32(0)             // saved %ebp (stack end)
    .D32(0);            // return address (stack end)

  RegionFromSection();
  raw_context.eip = 0x4000f49d;
  raw_context.esp = stack_section.start().Value();
  // Make the frame pointer bogus, to make the stackwalker scan the stack
  // for something that looks like a return address.
  raw_context.ebp = 0xd43eed6e;

  StackFrameSymbolizer frame_symbolizer(&supplier, &resolver);
  StackwalkerX86 walker(&system_info, &raw_context, &stack_region, &modules,
                        &frame_symbolizer);
  vector<const CodeModule*> modules_without_symbols;
  vector<const CodeModule*> modules_with_corrupt_symbols;
  ASSERT_TRUE(walker.Walk(&call_stack, &modules_without_symbols,
                          &modules_with_corrupt_symbols));
  ASSERT_EQ(1U, modules_without_symbols.size());
  ASSERT_EQ("module1", modules_without_symbols[0]->debug_file());
  ASSERT_EQ(0U, modules_with_corrupt_symbols.size());
  frames = call_stack.frames();
  ASSERT_EQ(2U, frames->size());

  {  // To avoid reusing locals by mistake
    StackFrameX86 *frame0 = static_cast<StackFrameX86 *>(frames->at(0));
    EXPECT_EQ(StackFrame::FRAME_TRUST_CONTEXT, frame0->trust);
    ASSERT_EQ(StackFrameX86::CONTEXT_VALID_ALL, frame0->context_validity);
    EXPECT_EQ(0x4000f49dU, frame0->instruction);
    EXPECT_EQ(0x4000f49dU, frame0->context.eip);
    EXPECT_EQ(stack_section.start().Value(), frame0->context.esp);
    EXPECT_EQ(0xd43eed6eU, frame0->context.ebp);
    EXPECT_EQ(NULL, frame0->windows_frame_info);
  }

  {  // To avoid reusing locals by mistake
    StackFrameX86 *frame1 = static_cast<StackFrameX86 *>(frames->at(1));
    EXPECT_EQ(StackFrame::FRAME_TRUST_SCAN, frame1->trust);
    ASSERT_EQ((StackFrameX86::CONTEXT_VALID_EIP
               | StackFrameX86::CONTEXT_VALID_ESP
               | StackFrameX86::CONTEXT_VALID_EBP),
              frame1->context_validity);
    EXPECT_EQ(0x4000129dU, frame1->instruction + 1);
    EXPECT_EQ(0x4000129dU, frame1->context.eip);
    EXPECT_EQ(frame1_esp.Value(), frame1->context.esp);
    EXPECT_EQ(frame1_ebp.Value(), frame1->context.ebp);
    EXPECT_EQ(NULL, frame1->windows_frame_info);
  }
}

// Force scanning for a return address a long way down the stack
TEST_F(GetCallerFrame, TraditionalScanLongWay) {
  stack_section.start() = 0x80000000;
  Label frame1_ebp;
  Label frame1_esp;
  stack_section
    // frame 0
    .D32(0xf065dc76)    // locals area:
    .D32(0x46ee2167)    // garbage that doesn't look like
    .D32(0xbab023ec)    // a return address
    .Append(20 * 4, 0)  // a bunch of space
    .D32(frame1_ebp)    // saved %ebp (%ebp fails to point here, forcing scan)
    .D32(0x4000129d)    // return address
    // frame 1
    .Mark(&frame1_esp)
    .Append(8, 0)       // space
    .Mark(&frame1_ebp)  // %ebp points here
    .D32(0)             // saved %ebp (stack end)
    .D32(0);            // return address (stack end)

  RegionFromSection();
  raw_context.eip = 0x4000f49d;
  raw_context.esp = stack_section.start().Value();
  // Make the frame pointer bogus, to make the stackwalker scan the stack
  // for something that looks like a return address.
  raw_context.ebp = 0xd43eed6e;

  StackFrameSymbolizer frame_symbolizer(&supplier, &resolver);
  StackwalkerX86 walker(&system_info, &raw_context, &stack_region, &modules,
                        &frame_symbolizer);
  vector<const CodeModule*> modules_without_symbols;
  vector<const CodeModule*> modules_with_corrupt_symbols;
  ASSERT_TRUE(walker.Walk(&call_stack, &modules_without_symbols,
                          &modules_with_corrupt_symbols));
  ASSERT_EQ(1U, modules_without_symbols.size());
  ASSERT_EQ("module1", modules_without_symbols[0]->debug_file());
  ASSERT_EQ(0U, modules_with_corrupt_symbols.size());
  frames = call_stack.frames();
  ASSERT_EQ(2U, frames->size());

  {  // To avoid reusing locals by mistake
    StackFrameX86 *frame0 = static_cast<StackFrameX86 *>(frames->at(0));
    EXPECT_EQ(StackFrame::FRAME_TRUST_CONTEXT, frame0->trust);
    ASSERT_EQ(StackFrameX86::CONTEXT_VALID_ALL, frame0->context_validity);
    EXPECT_EQ(0x4000f49dU, frame0->instruction);
    EXPECT_EQ(0x4000f49dU, frame0->context.eip);
    EXPECT_EQ(stack_section.start().Value(), frame0->context.esp);
    EXPECT_EQ(0xd43eed6eU, frame0->context.ebp);
    EXPECT_EQ(NULL, frame0->windows_frame_info);
  }

  {  // To avoid reusing locals by mistake
    StackFrameX86 *frame1 = static_cast<StackFrameX86 *>(frames->at(1));
    EXPECT_EQ(StackFrame::FRAME_TRUST_SCAN, frame1->trust);
    ASSERT_EQ((StackFrameX86::CONTEXT_VALID_EIP
               | StackFrameX86::CONTEXT_VALID_ESP
               | StackFrameX86::CONTEXT_VALID_EBP),
              frame1->context_validity);
    EXPECT_EQ(0x4000129dU, frame1->instruction + 1);
    EXPECT_EQ(0x4000129dU, frame1->context.eip);
    EXPECT_EQ(frame1_esp.Value(), frame1->context.esp);
    EXPECT_EQ(frame1_ebp.Value(), frame1->context.ebp);
    EXPECT_EQ(NULL, frame1->windows_frame_info);
  }
}

// Test that set_max_frames_scanned prevents using stack scanning
// to find caller frames.
TEST_F(GetCallerFrame, ScanningNotAllowed) {
  stack_section.start() = 0x80000000;
  Label frame1_ebp;
  stack_section
    // frame 0
    .D32(0xf065dc76)    // locals area:
    .D32(0x46ee2167)    // garbage that doesn't look like
    .D32(0xbab023ec)    // a return address
    .D32(frame1_ebp)    // saved %ebp (%ebp fails to point here, forcing scan)
    .D32(0x4000129d)    // return address
    // frame 1
    .Append(8, 0)       // space
    .Mark(&frame1_ebp)  // %ebp points here
    .D32(0)             // saved %ebp (stack end)
    .D32(0);            // return address (stack end)

  RegionFromSection();
  raw_context.eip = 0x4000f49d;
  raw_context.esp = stack_section.start().Value();
  // Make the frame pointer bogus, to make the stackwalker scan the stack
  // for something that looks like a return address.
  raw_context.ebp = 0xd43eed6e;

  StackFrameSymbolizer frame_symbolizer(&supplier, &resolver);
  StackwalkerX86 walker(&system_info, &raw_context, &stack_region, &modules,
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

  {  // To avoid reusing locals by mistake
    StackFrameX86 *frame0 = static_cast<StackFrameX86 *>(frames->at(0));
    EXPECT_EQ(StackFrame::FRAME_TRUST_CONTEXT, frame0->trust);
    ASSERT_EQ(StackFrameX86::CONTEXT_VALID_ALL, frame0->context_validity);
    EXPECT_EQ(0x4000f49dU, frame0->instruction);
    EXPECT_EQ(0x4000f49dU, frame0->context.eip);
    EXPECT_EQ(stack_section.start().Value(), frame0->context.esp);
    EXPECT_EQ(0xd43eed6eU, frame0->context.ebp);
    EXPECT_EQ(NULL, frame0->windows_frame_info);
  }
}

// Use Windows frame data (a "STACK WIN 4" record, from a
// FrameTypeFrameData DIA record) to walk a stack frame.
TEST_F(GetCallerFrame, WindowsFrameData) {
  SetModuleSymbols(&module1,
                   "STACK WIN 4 aa85 176 0 0 4 10 4 0 1"
                   " $T2 $esp .cbSavedRegs + ="
                   " $T0 .raSearchStart ="
                   " $eip $T0 ^ ="
                   " $esp $T0 4 + ="
                   " $ebx $T2 4  - ^ ="
                   " $edi $T2 8  - ^ ="
                   " $esi $T2 12 - ^ ="
                   " $ebp $T2 16 - ^ =\n");
  Label frame1_esp, frame1_ebp;
  stack_section.start() = 0x80000000;
  stack_section
    // frame 0
    .D32(frame1_ebp)                    // saved regs: %ebp
    .D32(0xa7120d1a)                    //             %esi
    .D32(0x630891be)                    //             %edi
    .D32(0x9068a878)                    //             %ebx
    .D32(0xa08ea45f)                    // locals: unused
    .D32(0x40001350)                    // return address
    // frame 1
    .Mark(&frame1_esp)
    .Append(12, 0)                      // empty space
    .Mark(&frame1_ebp)
    .D32(0)                             // saved %ebp (stack end)
    .D32(0);                            // saved %eip (stack end)

  RegionFromSection();
  raw_context.eip = 0x4000aa85;
  raw_context.esp = stack_section.start().Value();
  raw_context.ebp = 0xf052c1de;         // should not be needed to walk frame

  StackFrameSymbolizer frame_symbolizer(&supplier, &resolver);
  StackwalkerX86 walker(&system_info, &raw_context, &stack_region, &modules,
                        &frame_symbolizer);
  vector<const CodeModule*> modules_without_symbols;
  vector<const CodeModule*> modules_with_corrupt_symbols;
  ASSERT_TRUE(walker.Walk(&call_stack, &modules_without_symbols,
                          &modules_with_corrupt_symbols));
  ASSERT_EQ(0U, modules_without_symbols.size());
  ASSERT_EQ(0U, modules_with_corrupt_symbols.size());
  frames = call_stack.frames();
  ASSERT_EQ(2U, frames->size());

  {  // To avoid reusing locals by mistake
    StackFrameX86 *frame0 = static_cast<StackFrameX86 *>(frames->at(0));
    EXPECT_EQ(StackFrame::FRAME_TRUST_CONTEXT, frame0->trust);
    ASSERT_EQ(StackFrameX86::CONTEXT_VALID_ALL, frame0->context_validity);
    EXPECT_EQ(0x4000aa85U, frame0->instruction);
    EXPECT_EQ(0x4000aa85U, frame0->context.eip);
    EXPECT_EQ(stack_section.start().Value(), frame0->context.esp);
    EXPECT_EQ(0xf052c1deU, frame0->context.ebp);
    EXPECT_TRUE(frame0->windows_frame_info != NULL);
  }

  {  // To avoid reusing locals by mistake
    StackFrameX86 *frame1 = static_cast<StackFrameX86 *>(frames->at(1));
    EXPECT_EQ(StackFrame::FRAME_TRUST_CFI, frame1->trust);
    ASSERT_EQ((StackFrameX86::CONTEXT_VALID_EIP
               | StackFrameX86::CONTEXT_VALID_ESP
               | StackFrameX86::CONTEXT_VALID_EBP
               | StackFrameX86::CONTEXT_VALID_EBX
               | StackFrameX86::CONTEXT_VALID_ESI
               | StackFrameX86::CONTEXT_VALID_EDI),
              frame1->context_validity);
    EXPECT_EQ(0x40001350U, frame1->instruction + 1);
    EXPECT_EQ(0x40001350U, frame1->context.eip);
    EXPECT_EQ(frame1_esp.Value(), frame1->context.esp);
    EXPECT_EQ(frame1_ebp.Value(), frame1->context.ebp);
    EXPECT_EQ(0x9068a878U, frame1->context.ebx);
    EXPECT_EQ(0xa7120d1aU, frame1->context.esi);
    EXPECT_EQ(0x630891beU, frame1->context.edi);
    EXPECT_EQ(NULL, frame1->windows_frame_info);
  }
}

// Use Windows frame data (a "STACK WIN 4" record, from a
// FrameTypeFrameData DIA record) to walk a stack frame where the stack
// is aligned and we must search
TEST_F(GetCallerFrame, WindowsFrameDataAligned) {
  SetModuleSymbols(&module1,
                   "STACK WIN 4 aa85 176 0 0 4 4 8 0 1"
                   " $T1 .raSearch ="
                   " $T0 $T1 4 - 8 @ ="
                   " $ebp $T1 4 - ^ ="
                   " $eip $T1 ^ ="
                   " $esp $T1 4 + =");
  Label frame0_esp, frame0_ebp;
  Label frame1_esp, frame1_ebp;
  stack_section.start() = 0x80000000;
  stack_section
    // frame 0
    .Mark(&frame0_esp)
    .D32(0x0ffa0ffa)                    // unused saved register
    .D32(0xdeaddead)                    // locals
    .D32(0xbeefbeef)
    .D32(0)                             // 8-byte alignment
    .Mark(&frame0_ebp)
    .D32(frame1_ebp)                    // saved %ebp
    .D32(0x5000129d)                    // return address
    // frame 1
    .Mark(&frame1_esp)
    .D32(0x1)                           // parameter
    .Mark(&frame1_ebp)
    .D32(0)                             // saved %ebp (stack end)
    .D32(0);                            // saved %eip (stack end)

  RegionFromSection();
  raw_context.eip = 0x4000aa85;
  raw_context.esp = frame0_esp.Value();
  raw_context.ebp = frame0_ebp.Value();

  StackFrameSymbolizer frame_symbolizer(&supplier, &resolver);
  StackwalkerX86 walker(&system_info, &raw_context, &stack_region, &modules,
                        &frame_symbolizer);
  vector<const CodeModule*> modules_without_symbols;
  vector<const CodeModule*> modules_with_corrupt_symbols;
  ASSERT_TRUE(walker.Walk(&call_stack, &modules_without_symbols,
                          &modules_with_corrupt_symbols));
  ASSERT_EQ(1U, modules_without_symbols.size());
  ASSERT_EQ("module2", modules_without_symbols[0]->debug_file());
  ASSERT_EQ(0U, modules_with_corrupt_symbols.size());
  frames = call_stack.frames();
  ASSERT_EQ(2U, frames->size());

  {  // To avoid reusing locals by mistake
    StackFrameX86 *frame0 = static_cast<StackFrameX86 *>(frames->at(0));
    EXPECT_EQ(StackFrame::FRAME_TRUST_CONTEXT, frame0->trust);
    ASSERT_EQ(StackFrameX86::CONTEXT_VALID_ALL, frame0->context_validity);
    EXPECT_EQ(0x4000aa85U, frame0->instruction);
    EXPECT_EQ(0x4000aa85U, frame0->context.eip);
    EXPECT_EQ(frame0_esp.Value(), frame0->context.esp);
    EXPECT_EQ(frame0_ebp.Value(), frame0->context.ebp);
    EXPECT_TRUE(frame0->windows_frame_info != NULL);
  }

  {  // To avoid reusing locals by mistake
    StackFrameX86 *frame1 = static_cast<StackFrameX86 *>(frames->at(1));
    EXPECT_EQ(StackFrame::FRAME_TRUST_CFI, frame1->trust);
    ASSERT_EQ((StackFrameX86::CONTEXT_VALID_EIP
               | StackFrameX86::CONTEXT_VALID_ESP
               | StackFrameX86::CONTEXT_VALID_EBP),
              frame1->context_validity);
    EXPECT_EQ(0x5000129dU, frame1->instruction + 1);
    EXPECT_EQ(0x5000129dU, frame1->context.eip);
    EXPECT_EQ(frame1_esp.Value(), frame1->context.esp);
    EXPECT_EQ(frame1_ebp.Value(), frame1->context.ebp);
    EXPECT_EQ(NULL, frame1->windows_frame_info);
  }
}

// Use Windows frame data (a "STACK WIN 4" record, from a
// FrameTypeFrameData DIA record) to walk a frame, and depend on the
// parameter size from the callee as well.
TEST_F(GetCallerFrame, WindowsFrameDataParameterSize) {
  SetModuleSymbols(&module1, "FUNC 1000 100 c module1::wheedle\n");
  SetModuleSymbols(&module2,
                   // Note bogus parameter size in FUNC record; the stack walker
                   // should prefer the STACK WIN record, and see '4' below.
                   "FUNC aa85 176 beef module2::whine\n"
                   "STACK WIN 4 aa85 176 0 0 4 10 4 0 1"
                   " $T2 $esp .cbLocals + .cbSavedRegs + ="
                   " $T0 .raSearchStart ="
                   " $eip $T0 ^ ="
                   " $esp $T0 4 + ="
                   " $ebp $T0 20 - ^ ="
                   " $ebx $T0 8 - ^ =\n");
  Label frame0_esp, frame0_ebp;
  Label frame1_esp;
  Label frame2_esp, frame2_ebp;
  stack_section.start() = 0x80000000;
  stack_section
    // frame 0, in module1::wheedle.  Traditional frame.
    .Mark(&frame0_esp)
    .Append(16, 0)      // frame space
    .Mark(&frame0_ebp)
    .D32(0x6fa902e0)    // saved %ebp.  Not a frame pointer.
    .D32(0x5000aa95)    // return address, in module2::whine
    // frame 1, in module2::whine.  FrameData frame.
    .Mark(&frame1_esp)
    .D32(0xbaa0cb7a)    // argument 3 passed to module1::wheedle
    .D32(0xbdc92f9f)    // argument 2
    .D32(0x0b1d8442)    // argument 1
    .D32(frame2_ebp)    // saved %ebp
    .D32(0xb1b90a15)    // unused
    .D32(0xf18e072d)    // unused
    .D32(0x2558c7f3)    // saved %ebx
    .D32(0x0365e25e)    // unused
    .D32(0x2a179e38)    // return address; $T0 points here
    // frame 2, in no module
    .Mark(&frame2_esp)
    .Append(12, 0)      // empty space
    .Mark(&frame2_ebp)
    .D32(0)             // saved %ebp (stack end)
    .D32(0);            // saved %eip (stack end)

  RegionFromSection();
  raw_context.eip = 0x40001004;  // in module1::wheedle
  raw_context.esp = stack_section.start().Value();
  raw_context.ebp = frame0_ebp.Value();

  StackFrameSymbolizer frame_symbolizer(&supplier, &resolver);
  StackwalkerX86 walker(&system_info, &raw_context, &stack_region, &modules,
                        &frame_symbolizer);
  vector<const CodeModule*> modules_without_symbols;
  vector<const CodeModule*> modules_with_corrupt_symbols;
  ASSERT_TRUE(walker.Walk(&call_stack, &modules_without_symbols,
                          &modules_with_corrupt_symbols));
  ASSERT_EQ(0U, modules_without_symbols.size());
  ASSERT_EQ(0U, modules_with_corrupt_symbols.size());
  frames = call_stack.frames();
  ASSERT_EQ(3U, frames->size());

  {  // To avoid reusing locals by mistake
    StackFrameX86 *frame0 = static_cast<StackFrameX86 *>(frames->at(0));
    EXPECT_EQ(StackFrame::FRAME_TRUST_CONTEXT, frame0->trust);
    ASSERT_EQ(StackFrameX86::CONTEXT_VALID_ALL, frame0->context_validity);
    EXPECT_EQ(0x40001004U, frame0->instruction);
    EXPECT_EQ(0x40001004U, frame0->context.eip);
    EXPECT_EQ(frame0_esp.Value(), frame0->context.esp);
    EXPECT_EQ(frame0_ebp.Value(), frame0->context.ebp);
    EXPECT_EQ(&module1, frame0->module);
    EXPECT_EQ("module1::wheedle", frame0->function_name);
    EXPECT_EQ(0x40001000U, frame0->function_base);
    // The FUNC record for module1::wheedle should have produced a
    // WindowsFrameInfo structure with only the parameter size valid.
    ASSERT_TRUE(frame0->windows_frame_info != NULL);
    EXPECT_EQ(WindowsFrameInfo::VALID_PARAMETER_SIZE,
              frame0->windows_frame_info->valid);
    EXPECT_EQ(WindowsFrameInfo::STACK_INFO_UNKNOWN,
              frame0->windows_frame_info->type_);
    EXPECT_EQ(12U, frame0->windows_frame_info->parameter_size);
  }

  {  // To avoid reusing locals by mistake
    StackFrameX86 *frame1 = static_cast<StackFrameX86 *>(frames->at(1));
    EXPECT_EQ(StackFrame::FRAME_TRUST_FP, frame1->trust);
    ASSERT_EQ((StackFrameX86::CONTEXT_VALID_EIP
               | StackFrameX86::CONTEXT_VALID_ESP
               | StackFrameX86::CONTEXT_VALID_EBP),
              frame1->context_validity);
    EXPECT_EQ(0x5000aa95U, frame1->instruction + 1);
    EXPECT_EQ(0x5000aa95U, frame1->context.eip);
    EXPECT_EQ(frame1_esp.Value(), frame1->context.esp);
    EXPECT_EQ(0x6fa902e0U, frame1->context.ebp);
    EXPECT_EQ(&module2, frame1->module);
    EXPECT_EQ("module2::whine", frame1->function_name);
    EXPECT_EQ(0x5000aa85U, frame1->function_base);
    ASSERT_TRUE(frame1->windows_frame_info != NULL);
    EXPECT_EQ(WindowsFrameInfo::VALID_ALL, frame1->windows_frame_info->valid);
    EXPECT_EQ(WindowsFrameInfo::STACK_INFO_FRAME_DATA,
              frame1->windows_frame_info->type_);
    // This should not see the 0xbeef parameter size from the FUNC
    // record, but should instead see the STACK WIN record.
    EXPECT_EQ(4U, frame1->windows_frame_info->parameter_size);
  }

  {  // To avoid reusing locals by mistake
    StackFrameX86 *frame2 = static_cast<StackFrameX86 *>(frames->at(2));
    EXPECT_EQ(StackFrame::FRAME_TRUST_CFI, frame2->trust);
    ASSERT_EQ((StackFrameX86::CONTEXT_VALID_EIP
               | StackFrameX86::CONTEXT_VALID_ESP
               | StackFrameX86::CONTEXT_VALID_EBP
               | StackFrameX86::CONTEXT_VALID_EBX),
              frame2->context_validity);
    EXPECT_EQ(0x2a179e38U, frame2->instruction + 1);
    EXPECT_EQ(0x2a179e38U, frame2->context.eip);
    EXPECT_EQ(frame2_esp.Value(), frame2->context.esp);
    EXPECT_EQ(frame2_ebp.Value(), frame2->context.ebp);
    EXPECT_EQ(0x2558c7f3U, frame2->context.ebx);
    EXPECT_EQ(NULL, frame2->module);
    EXPECT_EQ(NULL, frame2->windows_frame_info);
  }
}

// Use Windows frame data (a "STACK WIN 4" record, from a
// FrameTypeFrameData DIA record) to walk a stack frame, where the
// expression fails to yield both an $eip and an $ebp value, and the stack
// walker must scan.
TEST_F(GetCallerFrame, WindowsFrameDataScan) {
  SetModuleSymbols(&module1,
                   "STACK WIN 4 c8c 111 0 0 4 10 4 0 1 bad program string\n");
  // Mark frame 1's PC as the end of the stack.
  SetModuleSymbols(&module2,
                   "FUNC 7c38 accf 0 module2::function\n"
                   "STACK WIN 4 7c38 accf 0 0 4 10 4 0 1 $eip 0 = $ebp 0 =\n");
  Label frame1_esp;
  stack_section.start() = 0x80000000;
  stack_section
    // frame 0
    .Append(16, 0x2a)                   // unused, garbage
    .D32(0x50007ce9)                    // return address
    // frame 1
    .Mark(&frame1_esp)
    .Append(8, 0);                      // empty space

  RegionFromSection();
  raw_context.eip = 0x40000c9c;
  raw_context.esp = stack_section.start().Value();
  raw_context.ebp = 0x2ae314cd;         // should not be needed to walk frame

  StackFrameSymbolizer frame_symbolizer(&supplier, &resolver);
  StackwalkerX86 walker(&system_info, &raw_context, &stack_region, &modules,
                        &frame_symbolizer);
  vector<const CodeModule*> modules_without_symbols;
  vector<const CodeModule*> modules_with_corrupt_symbols;
  ASSERT_TRUE(walker.Walk(&call_stack, &modules_without_symbols,
                          &modules_with_corrupt_symbols));
  ASSERT_EQ(0U, modules_without_symbols.size());
  ASSERT_EQ(0U, modules_with_corrupt_symbols.size());
  frames = call_stack.frames();
  ASSERT_EQ(2U, frames->size());

  {  // To avoid reusing locals by mistake
    StackFrameX86 *frame0 = static_cast<StackFrameX86 *>(frames->at(0));
    EXPECT_EQ(StackFrame::FRAME_TRUST_CONTEXT, frame0->trust);
    ASSERT_EQ(StackFrameX86::CONTEXT_VALID_ALL, frame0->context_validity);
    EXPECT_EQ(0x40000c9cU, frame0->instruction);
    EXPECT_EQ(0x40000c9cU, frame0->context.eip);
    EXPECT_EQ(stack_section.start().Value(), frame0->context.esp);
    EXPECT_EQ(0x2ae314cdU, frame0->context.ebp);
    EXPECT_TRUE(frame0->windows_frame_info != NULL);
  }

  {  // To avoid reusing locals by mistake
    StackFrameX86 *frame1 = static_cast<StackFrameX86 *>(frames->at(1));
    EXPECT_EQ(StackFrame::FRAME_TRUST_SCAN, frame1->trust);
    // I'd argue that CONTEXT_VALID_EBP shouldn't be here, since the walker
    // does not actually fetch the EBP after a scan (forcing the next frame
    // to be scanned as well). But let's grandfather the existing behavior in
    // for now.
    ASSERT_EQ((StackFrameX86::CONTEXT_VALID_EIP
               | StackFrameX86::CONTEXT_VALID_ESP
               | StackFrameX86::CONTEXT_VALID_EBP),
              frame1->context_validity);
    EXPECT_EQ(0x50007ce9U, frame1->instruction + 1);
    EXPECT_EQ(0x50007ce9U, frame1->context.eip);
    EXPECT_EQ(frame1_esp.Value(), frame1->context.esp);
    EXPECT_TRUE(frame1->windows_frame_info != NULL);
  }
}

// Use Windows frame data (a "STACK WIN 4" record, from a
// FrameTypeFrameData DIA record) to walk a stack frame, where the
// expression yields an $eip that falls outside of any module, and the
// stack walker must scan.
TEST_F(GetCallerFrame, WindowsFrameDataBadEIPScan) {
  SetModuleSymbols(&module1,
                   "STACK WIN 4 6e6 e7 0 0 0 8 4 0 1"
                   // A traditional frame, actually.
                   " $eip $ebp 4 + ^ = $esp $ebp 8 + = $ebp $ebp ^ =\n");
  // Mark frame 1's PC as the end of the stack.
  SetModuleSymbols(&module2,
                   "FUNC cfdb 8406 0 module2::function\n"
                   "STACK WIN 4 cfdb 8406 0 0 0 0 0 0 1 $eip 0 = $ebp 0 =\n");
  stack_section.start() = 0x80000000;

  // In this stack, the context's %ebp is pointing at the wrong place, so
  // the stack walker needs to scan to find the return address, and then
  // scan again to find the caller's saved %ebp.
  Label frame0_ebp, frame1_ebp, frame1_esp;
  stack_section
    // frame 0
    .Append(8, 0x2a)            // garbage
    .Mark(&frame0_ebp)          // frame 0 %ebp points here, but should point
                                // at *** below
    // The STACK WIN record says that the following two values are
    // frame 1's saved %ebp and return address, but the %ebp is wrong;
    // they're garbage. The stack walker will scan for the right values.
    .D32(0x3d937b2b)            // alleged to be frame 1's saved %ebp
    .D32(0x17847f5b)            // alleged to be frame 1's return address
    .D32(frame1_ebp)            // frame 1's real saved %ebp; scan will find
    .D32(0x2b2b2b2b)            // first word of realigned register save area
    // *** frame 0 %ebp ought to be pointing here
    .D32(0x2c2c2c2c)            // realigned locals area
    .D32(0x5000d000)            // frame 1's real saved %eip; scan will find
    // Frame 1, in module2::function. The STACK WIN record describes
    // this as the oldest frame, without referring to its contents, so
    // we needn't to provide any actual data here.
    .Mark(&frame1_esp)
    .Mark(&frame1_ebp)          // frame 1 %ebp points here
    // A dummy value for frame 1's %ebp to point at. The scan recognizes the
    // saved %ebp because it points to a valid word in the stack memory region.
    .D32(0x2d2d2d2d);

  RegionFromSection();
  raw_context.eip = 0x40000700;
  raw_context.esp = stack_section.start().Value();
  raw_context.ebp = frame0_ebp.Value();

  StackFrameSymbolizer frame_symbolizer(&supplier, &resolver);
  StackwalkerX86 walker(&system_info, &raw_context, &stack_region, &modules,
                        &frame_symbolizer);
  vector<const CodeModule*> modules_without_symbols;
  vector<const CodeModule*> modules_with_corrupt_symbols;
  ASSERT_TRUE(walker.Walk(&call_stack, &modules_without_symbols,
                          &modules_with_corrupt_symbols));
  ASSERT_EQ(0U, modules_without_symbols.size());
  ASSERT_EQ(0U, modules_with_corrupt_symbols.size());
  frames = call_stack.frames();
  ASSERT_EQ(2U, frames->size());

  {  // To avoid reusing locals by mistake
    StackFrameX86 *frame0 = static_cast<StackFrameX86 *>(frames->at(0));
    EXPECT_EQ(StackFrame::FRAME_TRUST_CONTEXT, frame0->trust);
    ASSERT_EQ(StackFrameX86::CONTEXT_VALID_ALL, frame0->context_validity);
    EXPECT_EQ(0x40000700U, frame0->instruction);
    EXPECT_EQ(0x40000700U, frame0->context.eip);
    EXPECT_EQ(stack_section.start().Value(), frame0->context.esp);
    EXPECT_EQ(frame0_ebp.Value(), frame0->context.ebp);
    EXPECT_TRUE(frame0->windows_frame_info != NULL);
  }

  {  // To avoid reusing locals by mistake
    StackFrameX86 *frame1 = static_cast<StackFrameX86 *>(frames->at(1));
    EXPECT_EQ(StackFrame::FRAME_TRUST_CFI_SCAN, frame1->trust);
    // I'd argue that CONTEXT_VALID_EBP shouldn't be here, since the
    // walker does not actually fetch the EBP after a scan (forcing the
    // next frame to be scanned as well). But let's grandfather the existing
    // behavior in for now.
    ASSERT_EQ((StackFrameX86::CONTEXT_VALID_EIP
               | StackFrameX86::CONTEXT_VALID_ESP
               | StackFrameX86::CONTEXT_VALID_EBP),
              frame1->context_validity);
    EXPECT_EQ(0x5000d000U, frame1->instruction + 1);
    EXPECT_EQ(0x5000d000U, frame1->context.eip);
    EXPECT_EQ(frame1_esp.Value(), frame1->context.esp);
    EXPECT_EQ(frame1_ebp.Value(), frame1->context.ebp);
    EXPECT_TRUE(frame1->windows_frame_info != NULL);
  }
}

// Use Windows FrameTypeFPO data to walk a stack frame for a function that
// does not modify %ebp from the value it had in the caller.
TEST_F(GetCallerFrame, WindowsFPOUnchangedEBP) {
  SetModuleSymbols(&module1,
                   // Note bogus parameter size in FUNC record; the walker
                   // should prefer the STACK WIN record, and see the '8' below.
                   "FUNC e8a8 100 feeb module1::discombobulated\n"
                   "STACK WIN 0 e8a8 100 0 0 8 4 10 0 0 0\n");
  Label frame0_esp;
  Label frame1_esp, frame1_ebp;
  stack_section.start() = 0x80000000;
  stack_section
    // frame 0, in module1::wheedle.  FrameTypeFPO (STACK WIN 0) frame.
    .Mark(&frame0_esp)
    // no outgoing parameters; this is the youngest frame.
    .D32(0x7c521352)     // four bytes of saved registers
    .Append(0x10, 0x42)  // local area
    .D32(0x40009b5b)     // return address, in module1, no function
    // frame 1, in module1, no function.
    .Mark(&frame1_esp)
    .D32(0xf60ea7fc)     // junk
    .Mark(&frame1_ebp)
    .D32(0)              // saved %ebp (stack end)
    .D32(0);             // saved %eip (stack end)

  RegionFromSection();
  raw_context.eip = 0x4000e8b8;  // in module1::whine
  raw_context.esp = stack_section.start().Value();
  // Frame pointer unchanged from caller.
  raw_context.ebp = frame1_ebp.Value();

  StackFrameSymbolizer frame_symbolizer(&supplier, &resolver);
  StackwalkerX86 walker(&system_info, &raw_context, &stack_region, &modules,
                        &frame_symbolizer);
  vector<const CodeModule*> modules_without_symbols;
  vector<const CodeModule*> modules_with_corrupt_symbols;
  ASSERT_TRUE(walker.Walk(&call_stack, &modules_without_symbols,
                          &modules_with_corrupt_symbols));
  ASSERT_EQ(0U, modules_without_symbols.size());
  ASSERT_EQ(0U, modules_with_corrupt_symbols.size());
  frames = call_stack.frames();
  ASSERT_EQ(2U, frames->size());

  {  // To avoid reusing locals by mistake
    StackFrameX86 *frame0 = static_cast<StackFrameX86 *>(frames->at(0));
    EXPECT_EQ(StackFrame::FRAME_TRUST_CONTEXT, frame0->trust);
    ASSERT_EQ(StackFrameX86::CONTEXT_VALID_ALL, frame0->context_validity);
    EXPECT_EQ(0x4000e8b8U, frame0->instruction);
    EXPECT_EQ(0x4000e8b8U, frame0->context.eip);
    EXPECT_EQ(frame0_esp.Value(), frame0->context.esp);
    // unchanged from caller
    EXPECT_EQ(frame1_ebp.Value(), frame0->context.ebp);
    EXPECT_EQ(&module1, frame0->module);
    EXPECT_EQ("module1::discombobulated", frame0->function_name);
    EXPECT_EQ(0x4000e8a8U, frame0->function_base);
    // The STACK WIN record for module1::discombobulated should have
    // produced a fully populated WindowsFrameInfo structure.
    ASSERT_TRUE(frame0->windows_frame_info != NULL);
    EXPECT_EQ(WindowsFrameInfo::VALID_ALL, frame0->windows_frame_info->valid);
    EXPECT_EQ(WindowsFrameInfo::STACK_INFO_FPO,
              frame0->windows_frame_info->type_);
    EXPECT_EQ(0x10U, frame0->windows_frame_info->local_size);
  }

  {  // To avoid reusing locals by mistake
    StackFrameX86 *frame1 = static_cast<StackFrameX86 *>(frames->at(1));
    EXPECT_EQ(StackFrame::FRAME_TRUST_CFI, frame1->trust);
    ASSERT_EQ((StackFrameX86::CONTEXT_VALID_EIP
               | StackFrameX86::CONTEXT_VALID_ESP
               | StackFrameX86::CONTEXT_VALID_EBP),
              frame1->context_validity);
    EXPECT_EQ(0x40009b5bU, frame1->instruction + 1);
    EXPECT_EQ(0x40009b5bU, frame1->context.eip);
    EXPECT_EQ(frame1_esp.Value(), frame1->context.esp);
    EXPECT_EQ(frame1_ebp.Value(), frame1->context.ebp);
    EXPECT_EQ(&module1, frame1->module);
    EXPECT_EQ("", frame1->function_name);
    EXPECT_EQ(NULL, frame1->windows_frame_info);
  }
}

// Use Windows FrameTypeFPO data to walk a stack frame for a function
// that uses %ebp for its own purposes, saving the value it had in the
// caller in the standard place in the saved register area.
TEST_F(GetCallerFrame, WindowsFPOUsedEBP) {
  SetModuleSymbols(&module1,
                   // Note bogus parameter size in FUNC record; the walker
                   // should prefer the STACK WIN record, and see the '8' below.
                   "FUNC 9aa8 e6 abbe module1::RaisedByTheAliens\n"
                   "STACK WIN 0 9aa8 e6 a 0 10 8 4 0 0 1\n");
  Label frame0_esp;
  Label frame1_esp, frame1_ebp;
  stack_section.start() = 0x80000000;
  stack_section
    // frame 0, in module1::wheedle.  FrameTypeFPO (STACK WIN 0) frame.
    .Mark(&frame0_esp)
    // no outgoing parameters; this is the youngest frame.
    .D32(frame1_ebp)    // saved register area: saved %ebp
    .D32(0xb68bd5f9)    // saved register area: something else
    .D32(0xd25d05fc)    // local area
    .D32(0x4000debe)    // return address, in module1, no function
    // frame 1, in module1, no function.
    .Mark(&frame1_esp)
    .D32(0xf0c9a974)    // junk
    .Mark(&frame1_ebp)
    .D32(0)             // saved %ebp (stack end)
    .D32(0);            // saved %eip (stack end)

  RegionFromSection();
  raw_context.eip = 0x40009ab8;  // in module1::RaisedByTheAliens
  raw_context.esp = stack_section.start().Value();
  // RaisedByTheAliens uses %ebp for its own mysterious purposes.
  raw_context.ebp = 0xecbdd1a5;

  StackFrameSymbolizer frame_symbolizer(&supplier, &resolver);
  StackwalkerX86 walker(&system_info, &raw_context, &stack_region, &modules,
                        &frame_symbolizer);
  vector<const CodeModule*> modules_without_symbols;
  vector<const CodeModule*> modules_with_corrupt_symbols;
  ASSERT_TRUE(walker.Walk(&call_stack, &modules_without_symbols,
                          &modules_with_corrupt_symbols));
  ASSERT_EQ(0U, modules_without_symbols.size());
  ASSERT_EQ(0U, modules_with_corrupt_symbols.size());
  frames = call_stack.frames();
  ASSERT_EQ(2U, frames->size());

  {  // To avoid reusing locals by mistake
    StackFrameX86 *frame0 = static_cast<StackFrameX86 *>(frames->at(0));
    EXPECT_EQ(StackFrame::FRAME_TRUST_CONTEXT, frame0->trust);
    ASSERT_EQ(StackFrameX86::CONTEXT_VALID_ALL, frame0->context_validity);
    EXPECT_EQ(0x40009ab8U, frame0->instruction);
    EXPECT_EQ(0x40009ab8U, frame0->context.eip);
    EXPECT_EQ(frame0_esp.Value(), frame0->context.esp);
    EXPECT_EQ(0xecbdd1a5, frame0->context.ebp);
    EXPECT_EQ(&module1, frame0->module);
    EXPECT_EQ("module1::RaisedByTheAliens", frame0->function_name);
    EXPECT_EQ(0x40009aa8U, frame0->function_base);
    // The STACK WIN record for module1::RaisedByTheAliens should have
    // produced a fully populated WindowsFrameInfo structure.
    ASSERT_TRUE(frame0->windows_frame_info != NULL);
    EXPECT_EQ(WindowsFrameInfo::VALID_ALL, frame0->windows_frame_info->valid);
    EXPECT_EQ(WindowsFrameInfo::STACK_INFO_FPO,
              frame0->windows_frame_info->type_);
    EXPECT_EQ("", frame0->windows_frame_info->program_string);
    EXPECT_TRUE(frame0->windows_frame_info->allocates_base_pointer);
  }

  {  // To avoid reusing locals by mistake
    StackFrameX86 *frame1 = static_cast<StackFrameX86 *>(frames->at(1));
    EXPECT_EQ(StackFrame::FRAME_TRUST_CFI, frame1->trust);
    ASSERT_EQ((StackFrameX86::CONTEXT_VALID_EIP
               | StackFrameX86::CONTEXT_VALID_ESP
               | StackFrameX86::CONTEXT_VALID_EBP),
              frame1->context_validity);
    EXPECT_EQ(0x4000debeU, frame1->instruction + 1);
    EXPECT_EQ(0x4000debeU, frame1->context.eip);
    EXPECT_EQ(frame1_esp.Value(), frame1->context.esp);
    EXPECT_EQ(frame1_ebp.Value(), frame1->context.ebp);
    EXPECT_EQ(&module1, frame1->module);
    EXPECT_EQ("", frame1->function_name);
    EXPECT_EQ(NULL, frame1->windows_frame_info);
  }
}

// This is a regression unit test which covers a bug which has to do with
// FPO-optimized Windows system call stubs in the context frame.  There is
// a more recent Windows system call dispatch mechanism which differs from
// the one which is being tested here.  The newer system call dispatch
// mechanism creates an extra context frame (KiFastSystemCallRet).
TEST_F(GetCallerFrame, WindowsFPOSystemCall) {
  SetModuleSymbols(&module3,  // ntdll.dll
                   "PUBLIC 1f8ac c ZwWaitForSingleObject\n"
                   "STACK WIN 0 1f8ac 1b 0 0 c 0 0 0 0 0\n");
  SetModuleSymbols(&module4,  // kernelbase.dll
                   "PUBLIC 109f9 c WaitForSingleObjectEx\n"
                   "PUBLIC 36590 0 _except_handler4\n"
                   "STACK WIN 4 109f9 df c 0 c c 48 0 1 $T0 $ebp = $eip "
                   "$T0 4 + ^ = $ebp $T0 ^ = $esp $T0 8 + = $L "
                   "$T0 .cbSavedRegs - = $P $T0 8 + .cbParams + =\n"
                   "STACK WIN 4 36590 154 17 0 10 0 14 0 1 $T0 $ebp = $eip "
                   "$T0 4 + ^ = $ebp $T0 ^ = $esp $T0 8 + = $L $T0 "
                   ".cbSavedRegs - = $P $T0 8 + .cbParams + =\n");
  SetModuleSymbols(&module5,  // kernel32.dll
                   "PUBLIC 11136 8 WaitForSingleObject\n"
                   "PUBLIC 11151 c WaitForSingleObjectExImplementation\n"
                   "STACK WIN 4 11136 16 5 0 8 0 0 0 1 $T0 $ebp = $eip "
                   "$T0 4 + ^ = $ebp $T0 ^ = $esp $T0 8 + = $L "
                   "$T0 .cbSavedRegs - = $P $T0 8 + .cbParams + =\n"
                   "STACK WIN 4 11151 7a 5 0 c 0 0 0 1 $T0 $ebp = $eip "
                   "$T0 4 + ^ = $ebp $T0 ^ = $esp $T0 8 + = $L "
                   "$T0 .cbSavedRegs - = $P $T0 8 + .cbParams + =\n");
  SetModuleSymbols(&module6,  // chrome.dll
                   "FILE 7038 some_file_name.h\n"
                   "FILE 839776 some_file_name.cc\n"
                   "FUNC 217fda 17 4 function_217fda\n"
                   "217fda 4 102 839776\n"
                   "FUNC 217ff1 a 4 function_217ff1\n"
                   "217ff1 0 594 7038\n"
                   "217ff1 a 596 7038\n"
                   "STACK WIN 0 217ff1 a 0 0 4 0 0 0 0 0\n");

  Label frame0_esp, frame1_esp;
  Label frame1_ebp, frame2_ebp, frame3_ebp;
  stack_section.start() = 0x002ff290;
  stack_section
    .Mark(&frame0_esp)
    .D32(0x771ef8c1)    // EIP in frame 0 (system call)
    .D32(0x75fa0a91)    // return address of frame 0
    .Mark(&frame1_esp)
    .D32(0x000017b0)    // args to child
    .D32(0x00000000)
    .D32(0x002ff2d8)
    .D32(0x88014a2e)
    .D32(0x002ff364)
    .D32(0x000017b0)
    .D32(0x00000000)
    .D32(0x00000024)
    .D32(0x00000001)
    .D32(0x00000000)
    .D32(0x00000000)
    .D32(0x00000000)
    .D32(0x00000000)
    .D32(0x00000000)
    .D32(0x00000000)
    .D32(0x00000000)
    .D32(0x9e3b9800)
    .D32(0xfffffff7)
    .D32(0x00000000)
    .D32(0x002ff2a4)
    .D32(0x64a07ff1)    // random value to be confused with a return address
    .D32(0x002ff8dc)
    .D32(0x75fc6590)    // random value to be confused with a return address
    .D32(0xfdd2c6ea)
    .D32(0x00000000)
    .Mark(&frame1_ebp)
    .D32(frame2_ebp)    // Child EBP
    .D32(0x75741194)    // return address of frame 1
    .D32(0x000017b0)    // args to child
    .D32(0x0036ee80)
    .D32(0x00000000)
    .D32(0x65bc7d14)
    .Mark(&frame2_ebp)
    .D32(frame3_ebp)    // Child EBP
    .D32(0x75741148)    // return address of frame 2
    .D32(0x000017b0)    // args to child
    .D32(0x0036ee80)
    .D32(0x00000000)
    .Mark(&frame3_ebp)
    .D32(0)             // saved %ebp (stack end)
    .D32(0);            // saved %eip (stack end)

  RegionFromSection();
  raw_context.eip = 0x771ef8c1;  // in ntdll::ZwWaitForSingleObject
  raw_context.esp = stack_section.start().Value();
  ASSERT_TRUE(raw_context.esp == frame0_esp.Value());
  raw_context.ebp = frame1_ebp.Value();

  StackFrameSymbolizer frame_symbolizer(&supplier, &resolver);
  StackwalkerX86 walker(&system_info, &raw_context, &stack_region, &modules,
                        &frame_symbolizer);
  vector<const CodeModule*> modules_without_symbols;
  vector<const CodeModule*> modules_with_corrupt_symbols;
  ASSERT_TRUE(walker.Walk(&call_stack, &modules_without_symbols,
                          &modules_with_corrupt_symbols));
  ASSERT_EQ(0U, modules_without_symbols.size());
  ASSERT_EQ(0U, modules_with_corrupt_symbols.size());
  frames = call_stack.frames();

  ASSERT_EQ(4U, frames->size());

  {  // To avoid reusing locals by mistake
    StackFrameX86 *frame0 = static_cast<StackFrameX86 *>(frames->at(0));
    EXPECT_EQ(StackFrame::FRAME_TRUST_CONTEXT, frame0->trust);
    ASSERT_EQ(StackFrameX86::CONTEXT_VALID_ALL, frame0->context_validity);
    EXPECT_EQ(0x771ef8c1U, frame0->instruction);
    EXPECT_EQ(0x771ef8c1U, frame0->context.eip);
    EXPECT_EQ(frame0_esp.Value(), frame0->context.esp);
    EXPECT_EQ(frame1_ebp.Value(), frame0->context.ebp);
    EXPECT_EQ(&module3, frame0->module);
    EXPECT_EQ("ZwWaitForSingleObject", frame0->function_name);
    // The STACK WIN record for module3!ZwWaitForSingleObject should have
    // produced a fully populated WindowsFrameInfo structure.
    ASSERT_TRUE(frame0->windows_frame_info != NULL);
    EXPECT_EQ(WindowsFrameInfo::VALID_ALL, frame0->windows_frame_info->valid);
    EXPECT_EQ(WindowsFrameInfo::STACK_INFO_FPO,
              frame0->windows_frame_info->type_);
    EXPECT_EQ("", frame0->windows_frame_info->program_string);
    EXPECT_FALSE(frame0->windows_frame_info->allocates_base_pointer);
  }

  {  // To avoid reusing locals by mistake
    StackFrameX86 *frame1 = static_cast<StackFrameX86 *>(frames->at(1));
    EXPECT_EQ(StackFrame::FRAME_TRUST_CFI, frame1->trust);
    ASSERT_EQ((StackFrameX86::CONTEXT_VALID_EIP
               | StackFrameX86::CONTEXT_VALID_ESP
               | StackFrameX86::CONTEXT_VALID_EBP),
              frame1->context_validity);
    EXPECT_EQ(0x75fa0a91U, frame1->instruction + 1);
    EXPECT_EQ(0x75fa0a91U, frame1->context.eip);
    EXPECT_EQ(frame1_esp.Value(), frame1->context.esp);
    EXPECT_EQ(frame1_ebp.Value(), frame1->context.ebp);
    EXPECT_EQ(&module4, frame1->module);
    EXPECT_EQ("WaitForSingleObjectEx", frame1->function_name);
    // The STACK WIN record for module4!WaitForSingleObjectEx should have
    // produced a fully populated WindowsFrameInfo structure.
    ASSERT_TRUE(frame1->windows_frame_info != NULL);
    EXPECT_EQ(WindowsFrameInfo::VALID_ALL, frame1->windows_frame_info->valid);
    EXPECT_EQ(WindowsFrameInfo::STACK_INFO_FRAME_DATA,
              frame1->windows_frame_info->type_);
    EXPECT_EQ("$T0 $ebp = $eip $T0 4 + ^ = $ebp $T0 ^ = $esp $T0 8 + = $L "
              "$T0 .cbSavedRegs - = $P $T0 8 + .cbParams + =",
              frame1->windows_frame_info->program_string);
    EXPECT_FALSE(frame1->windows_frame_info->allocates_base_pointer);
  }
}

// Scan the stack for a better return address and potentially skip frames
// when the calculated return address is not in a known module.  Note, that
// the span of this scan is somewhat arbitrarily limited to 120 search words
// for the context frame and 30 search words (pointers) for the other frames:
//     const int kRASearchWords = 30;
// This means that frames can be skipped only when their size is relatively
// small: smaller than 4 * kRASearchWords * sizeof(InstructionType)
TEST_F(GetCallerFrame, ReturnAddressIsNotInKnownModule) {
  MockCodeModule msvcrt_dll(0x77be0000, 0x58000, "msvcrt.dll", "version1");
  SetModuleSymbols(&msvcrt_dll,  // msvcrt.dll
                   "PUBLIC 38180 0 wcsstr\n"
                   "STACK WIN 4 38180 61 10 0 8 0 0 0 1 $T0 $ebp = $eip $T0 "
                   "4 + ^ = $ebp $T0 ^ = $esp $T0 8 + = $L $T0 .cbSavedRegs "
                   "- = $P $T0 4 + .cbParams + =\n");

  MockCodeModule kernel32_dll(0x7c800000, 0x103000, "kernel32.dll", "version1");
  SetModuleSymbols(&kernel32_dll,  // kernel32.dll
                   "PUBLIC efda 8 FindNextFileW\n"
                   "STACK WIN 4 efda 1bb c 0 8 8 3c 0 1 $T0 $ebp = $eip $T0 "
                   "4 + ^ = $ebp $T0 ^ = $esp $T0 8 + = $L $T0 .cbSavedRegs "
                   "- = $P $T0 4 + .cbParams + =\n");

  MockCodeModule chrome_dll(0x1c30000, 0x28C8000, "chrome.dll", "version1");
  SetModuleSymbols(&chrome_dll,  // chrome.dll
                   "FUNC e3cff 4af 0 file_util::FileEnumerator::Next()\n"
                   "e3cff 1a 711 2505\n"
                   "STACK WIN 4 e3cff 4af 20 0 4 c 94 0 1 $T1 .raSearch = "
                   "$T0  $T1 4 - 8 @ = $ebp $T1 4 - ^ = $eip $T1 ^ = $esp "
                   "$T1 4 + = $20 $T0 152 - ^ =  $23 $T0 156 - ^ =  $24 "
                   "$T0 160 - ^ =\n");

  // Create some modules with some stock debugging information.
  MockCodeModules local_modules;
  local_modules.Add(&msvcrt_dll);
  local_modules.Add(&kernel32_dll);
  local_modules.Add(&chrome_dll);

  Label frame0_esp;
  Label frame0_ebp;
  Label frame1_ebp;
  Label frame2_ebp;
  Label frame3_ebp;

  stack_section.start() = 0x0932f2d0;
  stack_section
    .Mark(&frame0_esp)
    .D32(0x0764e000)
    .D32(0x0764e068)
    .Mark(&frame0_ebp)
    .D32(frame1_ebp)    // Child EBP
    .D32(0x001767a0)    // return address of frame 0
                        // Not in known module
    .D32(0x0764e0c6)
    .D32(0x001bb1b8)
    .D32(0x0764e068)
    .D32(0x00000003)
    .D32(0x0764e068)
    .D32(0x00000003)
    .D32(0x07578828)
    .D32(0x0764e000)
    .D32(0x00000000)
    .D32(0x001c0010)
    .D32(0x0764e0c6)
    .Mark(&frame1_ebp)
    .D32(frame2_ebp)    // Child EBP
    .D32(0x7c80f10f)    // return address of frame 1
                        // inside kernel32!FindNextFileW
    .D32(0x000008f8)
    .D32(0x00000000)
    .D32(0x00000000)
    .D32(0x00000000)
    .D32(0x0932f34c)
    .D32(0x0764e000)
    .D32(0x00001000)
    .D32(0x00000000)
    .D32(0x00000001)
    .D32(0x00000000)
    .D32(0x00000000)
    .D32(0x0932f6a8)
    .D32(0x00000000)
    .D32(0x0932f6d8)
    .D32(0x00000000)
    .D32(0x000000d6)
    .D32(0x0764e000)
    .D32(0x7ff9a000)
    .D32(0x0932f3fc)
    .D32(0x00000001)
    .D32(0x00000001)
    .D32(0x07578828)
    .D32(0x0000002e)
    .D32(0x0932f340)
    .D32(0x0932eef4)
    .D32(0x0932ffdc)
    .D32(0x7c839ad8)
    .D32(0x7c80f0d8)
    .D32(0x00000000)
    .Mark(&frame2_ebp)
    .D32(frame3_ebp)    // Child EBP
    .D32(0x01d13f91)    // return address of frame 2
                        // inside chrome_dll!file_util::FileEnumerator::Next
    .D32(0x07578828)
    .D32(0x0932f6ac)
    .D32(0x0932f9c4)
    .D32(0x0932f9b4)
    .D32(0x00000000)
    .D32(0x00000003)
    .D32(0x0932f978)
    .D32(0x01094330)
    .D32(0x00000000)
    .D32(0x00000001)
    .D32(0x01094330)
    .D32(0x00000000)
    .D32(0x00000000)
    .D32(0x07f30000)
    .D32(0x01c3ba17)
    .D32(0x08bab840)
    .D32(0x07f31580)
    .D32(0x00000000)
    .D32(0x00000007)
    .D32(0x0932f940)
    .D32(0x0000002e)
    .D32(0x0932f40c)
    .D32(0x01d13b53)
    .D32(0x0932f958)
    .D32(0x00000001)
    .D32(0x00000007)
    .D32(0x0932f940)
    .D32(0x0000002e)
    .D32(0x00000000)
    .D32(0x0932f6ac)
    .D32(0x01e13ef0)
    .D32(0x00000001)
    .D32(0x00000007)
    .D32(0x0932f958)
    .D32(0x08bab840)
    .D32(0x0932f9b4)
    .D32(0x00000000)
    .D32(0x0932f9b4)
    .D32(0x000000a7)
    .D32(0x000000a7)
    .D32(0x0932f998)
    .D32(0x579627a2)
    .Mark(&frame3_ebp)
    .D32(0)             // saved %ebp (stack end)
    .D32(0);            // saved %eip (stack end)

  RegionFromSection();
  raw_context.eip = 0x77c181cd;  // inside msvcrt!wcsstr
  raw_context.esp = frame0_esp.Value();
  raw_context.ebp = frame0_ebp.Value();
  // sanity
  ASSERT_TRUE(raw_context.esp == stack_section.start().Value());
  ASSERT_TRUE(raw_context.ebp == stack_section.start().Value() + 8);

  StackFrameSymbolizer frame_symbolizer(&supplier, &resolver);
  StackwalkerX86 walker(&system_info, &raw_context, &stack_region,
                        &local_modules, &frame_symbolizer);
  vector<const CodeModule*> modules_without_symbols;
  vector<const CodeModule*> modules_with_corrupt_symbols;
  ASSERT_TRUE(walker.Walk(&call_stack, &modules_without_symbols,
                          &modules_with_corrupt_symbols));
  ASSERT_EQ(0U, modules_without_symbols.size());
  ASSERT_EQ(0U, modules_with_corrupt_symbols.size());
  frames = call_stack.frames();

  ASSERT_EQ(3U, frames->size());

  {  // To avoid reusing locals by mistake
    StackFrameX86 *frame0 = static_cast<StackFrameX86 *>(frames->at(0));
    EXPECT_EQ(StackFrame::FRAME_TRUST_CONTEXT, frame0->trust);
    ASSERT_EQ(StackFrameX86::CONTEXT_VALID_ALL, frame0->context_validity);
    EXPECT_EQ(0x77c181cdU, frame0->instruction);
    EXPECT_EQ(0x77c181cdU, frame0->context.eip);
    EXPECT_EQ(frame0_esp.Value(), frame0->context.esp);
    EXPECT_EQ(frame0_ebp.Value(), frame0->context.ebp);
    EXPECT_EQ(&msvcrt_dll, frame0->module);
    EXPECT_EQ("wcsstr", frame0->function_name);
    ASSERT_TRUE(frame0->windows_frame_info != NULL);
    EXPECT_EQ(WindowsFrameInfo::VALID_ALL, frame0->windows_frame_info->valid);
    EXPECT_EQ(WindowsFrameInfo::STACK_INFO_FRAME_DATA,
              frame0->windows_frame_info->type_);
    EXPECT_EQ("$T0 $ebp = $eip $T0 "
              "4 + ^ = $ebp $T0 ^ = $esp $T0 8 + = $L $T0 .cbSavedRegs "
              "- = $P $T0 4 + .cbParams + =",
              frame0->windows_frame_info->program_string);
    // It has program string, so allocates_base_pointer is not expected
    EXPECT_FALSE(frame0->windows_frame_info->allocates_base_pointer);
  }

  {  // To avoid reusing locals by mistake
    StackFrameX86 *frame1 = static_cast<StackFrameX86 *>(frames->at(1));
    EXPECT_EQ(StackFrame::FRAME_TRUST_CFI_SCAN, frame1->trust);
    ASSERT_EQ((StackFrameX86::CONTEXT_VALID_EIP |
               StackFrameX86::CONTEXT_VALID_ESP |
               StackFrameX86::CONTEXT_VALID_EBP),
              frame1->context_validity);
    EXPECT_EQ(0x7c80f10fU, frame1->instruction + 1);
    EXPECT_EQ(0x7c80f10fU, frame1->context.eip);
    // frame 1 was skipped, so intead of frame1_ebp compare with frame2_ebp.
    EXPECT_EQ(frame2_ebp.Value(), frame1->context.ebp);
    EXPECT_EQ(&kernel32_dll, frame1->module);
    EXPECT_EQ("FindNextFileW", frame1->function_name);
    ASSERT_TRUE(frame1->windows_frame_info != NULL);
    EXPECT_EQ(WindowsFrameInfo::VALID_ALL, frame1->windows_frame_info->valid);
    EXPECT_EQ(WindowsFrameInfo::STACK_INFO_FRAME_DATA,
              frame1->windows_frame_info->type_);
    EXPECT_EQ("$T0 $ebp = $eip $T0 "
              "4 + ^ = $ebp $T0 ^ = $esp $T0 8 + = $L $T0 .cbSavedRegs "
              "- = $P $T0 4 + .cbParams + =",
              frame1->windows_frame_info->program_string);
    EXPECT_FALSE(frame1->windows_frame_info->allocates_base_pointer);
  }

  {  // To avoid reusing locals by mistake
    StackFrameX86 *frame2 = static_cast<StackFrameX86 *>(frames->at(2));
    EXPECT_EQ(StackFrame::FRAME_TRUST_CFI, frame2->trust);
    ASSERT_EQ((StackFrameX86::CONTEXT_VALID_EIP |
               StackFrameX86::CONTEXT_VALID_ESP |
               StackFrameX86::CONTEXT_VALID_EBP),
              frame2->context_validity);
    EXPECT_EQ(0x01d13f91U, frame2->instruction + 1);
    EXPECT_EQ(0x01d13f91U, frame2->context.eip);
    // frame 1 was skipped, so intead of frame2_ebp compare with frame3_ebp.
    EXPECT_EQ(frame3_ebp.Value(), frame2->context.ebp);
    EXPECT_EQ(&chrome_dll, frame2->module);
    EXPECT_EQ("file_util::FileEnumerator::Next()", frame2->function_name);
    ASSERT_TRUE(frame2->windows_frame_info != NULL);
    EXPECT_EQ(WindowsFrameInfo::VALID_ALL, frame2->windows_frame_info->valid);
    EXPECT_EQ(WindowsFrameInfo::STACK_INFO_FRAME_DATA,
              frame2->windows_frame_info->type_);
    EXPECT_EQ("$T1 .raSearch = "
              "$T0  $T1 4 - 8 @ = $ebp $T1 4 - ^ = $eip $T1 ^ = $esp "
              "$T1 4 + = $20 $T0 152 - ^ =  $23 $T0 156 - ^ =  $24 "
              "$T0 160 - ^ =",
              frame2->windows_frame_info->program_string);
    EXPECT_FALSE(frame2->windows_frame_info->allocates_base_pointer);
  }
}

// Test the .raSearchStart/.raSearch calculation when alignment operators are
// used in the program string.  The current %ebp must be valid and it is the
// only reliable data point that can be used for that calculation.
TEST_F(GetCallerFrame, HandleAlignmentInProgramString) {
  MockCodeModule chrome_dll(0x59630000, 0x19e3000, "chrome.dll", "version1");
  SetModuleSymbols(&chrome_dll,  // chrome.dll
                   "FUNC 56422 50c 8 base::MessageLoop::RunTask"
                   "(base::PendingTask const &)\n"
                   "56422 e 458 4589\n"
                   "STACK WIN 4 56422 50c 11 0 8 c ac 0 1 $T1 .raSearch = $T0 "
                   "$T1 4 - 8 @ = $ebp $T1 4 - ^ = $eip $T1 ^ = $esp $T1 4 + = "
                   "$20 $T0 176 - ^ =  $23 $T0 180 - ^ =  $24 $T0 184 - ^ =\n"
                   "FUNC 55d34 34a 0 base::MessageLoop::DoWork()\n"
                   "55d34 11 596 4589\n"
                   "STACK WIN 4 55d34 34a 19 0 0 c 134 0 1 $T1 .raSearch = "
                   "$T0  $T1 4 - 8 @ = $ebp $T1 4 - ^ = $eip $T1 ^ = $esp "
                   "$T1 4 + = $20 $T0 312 - ^ =  $23 $T0 316 - ^ =  $24 $T0 "
                   "320 - ^ =\n"
                   "FUNC 55c39 fb 0 base::MessagePumpForIO::DoRunLoop()\n"
                   "55c39 d 518 19962\n"
                   "STACK WIN 4 55c39 fb d 0 0 c 34 0 1 $T1 .raSearch = $T0 "
                   "$T1 4 - 64 @ = $ebp $T1 4 - ^ = $eip $T1 ^ = $esp $T1 4 + "
                   "= $20 $T0 56 - ^ =  $23 $T0 60 - ^ =  $24 $T0 64 - ^ =\n"
                   "FUNC 55bf0 49 4 base::MessagePumpWin::Run(base::"
                   "MessagePump::Delegate *)\n"
                   "55bf0 49 48 4724\n"
                   "STACK WIN 4 55bf0 49 c 0 4 0 10 0 1 $T0 $ebp = $eip $T0 4 "
                   "+ ^ = $ebp $T0 ^ = $esp $T0 8 + =\n"
                   "FUNC 165d de 4 malloc\n"
                   "165d 6 119 54\n"
                   "STACK WIN 4 165d de d 0 4 8 0 0 1 $T1 .raSearch = $T0 "
                   "$T1 4 - 8 @ = $ebp $T1 4 - ^ = $eip $T1 ^ = $esp $T1 4 "
                   "+ = $23 $T0 4 - ^ =  $24 $T0 8 - ^ =\n"
                   "FUNC 55ac9 79 0 base::MessageLoop::RunInternal()\n"
                   "55ac9 d 427 4589\n"
                   "STACK WIN 4 55ac9 79 d 0 0 8 10 0 1 $T1 .raSearch = $T0 "
                   "$T1 4 - 8 @ = $ebp $T1 4 - ^ = $eip $T1 ^ = $esp $T1 4 + = "
                   "$23 $T0 20 - ^ =  $24 $T0 24 - ^ =\n");

  // Create some modules with some stock debugging information.
  MockCodeModules local_modules;
  local_modules.Add(&chrome_dll);

  Label frame0_esp;
  Label frame0_ebp;
  Label frame1_esp;
  Label frame1_ebp;
  Label frame2_esp;
  Label frame2_ebp;
  Label frame3_esp;
  Label frame3_ebp;

  stack_section.start() = 0x046bfc80;
  stack_section
    .D32(0)
    .Mark(&frame0_esp)
    .D32(0x01e235a0)
    .D32(0x00000000)
    .D32(0x01e9f580)
    .D32(0x01e9f580)
    .D32(0x00000020)
    .D32(0x00000000)
    .D32(0x00463674)
    .D32(0x00000020)
    .D32(0x00000000)
    .D32(0x046bfcd8)
    .D32(0x046bfcd8)
    .D32(0x0001204b)
    .D32(0x00000000)
    .D32(0xfdddb523)
    .D32(0x00000000)
    .D32(0x00000007)
    .D32(0x00000040)
    .D32(0x00000000)
    .D32(0x59631693)  // chrome_59630000!malloc+0x36
    .D32(0x01e9f580)
    .D32(0x01e9f580)
    .D32(0x046bfcf8)
    .D32(0x77da6704)  // ntdll!NtSetIoCompletion+0xc
    .D32(0x046bfd4c)
    .D32(0x59685bec)  // chrome_59630000!base::MessageLoop::StartHistogrammer..
    .D32(0x01e235a0)

    .Mark(&frame0_ebp)
    .D32(frame1_ebp)  // Child EBP    .D32(0x046bfd0c)
    .D32(0x59685c2e)  // Return address in
                      // chrome_59630000!base::MessagePumpWin::Run+0x3e
    .Mark(&frame1_esp)
    .D32(0x01e75a90)
    .D32(0x046bfd4c)
    .D32(0x01e75a90)
    .D32(0x00000000)
    .D32(0x00000300)
    .D32(0x00000001)

    .Mark(&frame1_ebp)
    .D32(frame2_ebp)  // Child EBP    .D32(0x046bfd30)
    .D32(0x59685b3c)  // Return address in
                      // chrome_59630000!base::MessageLoop::RunInternal+0x73
    .Mark(&frame2_esp)
    .D32(0x01e75a90)
    .D32(0x00000000)
    .D32(0x046bfd4c)
    .D32(0x59658123)  // chrome_59630000!std::deque..
    .D32(0x046bfda0)
    .D32(0x01e79d70)
    .D32(0x046bfda0)

    .Mark(&frame2_ebp)  // .D32(0x046bfd40)
    .D32(0)             // saved %ebp (stack end)
    .D32(0);            // saved %eip (stack end)

  RegionFromSection();
  raw_context.eip = 0x59685c46;  // Context frame in
                                 // base::MessagePumpForIO::DoRunLoop
  raw_context.esp = frame0_esp.Value();
  raw_context.ebp = frame0_ebp.Value();

  StackFrameSymbolizer frame_symbolizer(&supplier, &resolver);
  StackwalkerX86 walker(&system_info, &raw_context, &stack_region,
                        &local_modules, &frame_symbolizer);
  vector<const CodeModule*> modules_without_symbols;
  vector<const CodeModule*> modules_with_corrupt_symbols;
  ASSERT_TRUE(walker.Walk(&call_stack, &modules_without_symbols,
                          &modules_with_corrupt_symbols));
  ASSERT_EQ(0U, modules_without_symbols.size());
  ASSERT_EQ(0U, modules_with_corrupt_symbols.size());
  frames = call_stack.frames();

  ASSERT_EQ(3U, frames->size());

  {  // To avoid reusing locals by mistake
    StackFrameX86 *frame = static_cast<StackFrameX86 *>(frames->at(0));
    EXPECT_EQ(StackFrame::FRAME_TRUST_CONTEXT, frame->trust);
    ASSERT_EQ(StackFrameX86::CONTEXT_VALID_ALL, frame->context_validity);
    EXPECT_EQ("base::MessagePumpForIO::DoRunLoop()", frame->function_name);
    EXPECT_EQ(0x59685c46U, frame->instruction);
    EXPECT_EQ(0x59685c46U, frame->context.eip);
    EXPECT_EQ(frame0_esp.Value(), frame->context.esp);
    EXPECT_EQ(frame0_ebp.Value(), frame->context.ebp);
    EXPECT_EQ(&chrome_dll, frame->module);
    ASSERT_TRUE(frame->windows_frame_info != NULL);
    EXPECT_EQ(WindowsFrameInfo::VALID_ALL, frame->windows_frame_info->valid);
    EXPECT_EQ(WindowsFrameInfo::STACK_INFO_FRAME_DATA,
              frame->windows_frame_info->type_);
    EXPECT_EQ("$T1 .raSearch = $T0 "
              "$T1 4 - 64 @ = $ebp $T1 4 - ^ = $eip $T1 ^ = $esp $T1 4 + "
              "= $20 $T0 56 - ^ =  $23 $T0 60 - ^ =  $24 $T0 64 - ^ =",
              frame->windows_frame_info->program_string);
    EXPECT_FALSE(frame->windows_frame_info->allocates_base_pointer);
  }

  {  // To avoid reusing locals by mistake
    StackFrameX86 *frame = static_cast<StackFrameX86 *>(frames->at(1));
    EXPECT_EQ(StackFrame::FRAME_TRUST_CFI, frame->trust);
    ASSERT_EQ((StackFrameX86::CONTEXT_VALID_EIP |
               StackFrameX86::CONTEXT_VALID_ESP |
               StackFrameX86::CONTEXT_VALID_EBP),
              frame->context_validity);
    EXPECT_EQ("base::MessagePumpWin::Run(base::MessagePump::Delegate *)",
              frame->function_name);
    EXPECT_EQ(1500011566U, frame->instruction + 1);
    EXPECT_EQ(1500011566U, frame->context.eip);
    EXPECT_EQ(frame1_esp.Value(), frame->context.esp);
    EXPECT_EQ(frame1_ebp.Value(), frame->context.ebp);
    EXPECT_EQ(&chrome_dll, frame->module);
    ASSERT_TRUE(frame->windows_frame_info != NULL);
    EXPECT_EQ(WindowsFrameInfo::VALID_ALL, frame->windows_frame_info->valid);
    EXPECT_EQ(WindowsFrameInfo::STACK_INFO_FRAME_DATA,
              frame->windows_frame_info->type_);
    EXPECT_EQ("$T0 $ebp = $eip $T0 4 + ^ = $ebp $T0 ^ = $esp $T0 8 + =",
              frame->windows_frame_info->program_string);
    EXPECT_FALSE(frame->windows_frame_info->allocates_base_pointer);
  }

  {  // To avoid reusing locals by mistake
    StackFrameX86 *frame = static_cast<StackFrameX86 *>(frames->at(2));
    EXPECT_EQ(StackFrame::FRAME_TRUST_CFI, frame->trust);
    ASSERT_EQ((StackFrameX86::CONTEXT_VALID_EIP |
               StackFrameX86::CONTEXT_VALID_ESP |
               StackFrameX86::CONTEXT_VALID_EBP),
              frame->context_validity);
    EXPECT_EQ("base::MessageLoop::RunInternal()", frame->function_name);
    EXPECT_EQ(1500011324U, frame->instruction + 1);
    EXPECT_EQ(1500011324U, frame->context.eip);
    EXPECT_EQ(frame2_esp.Value(), frame->context.esp);
    EXPECT_EQ(frame2_ebp.Value(), frame->context.ebp);
    EXPECT_EQ(&chrome_dll, frame->module);
    ASSERT_TRUE(frame->windows_frame_info != NULL);
    EXPECT_EQ(WindowsFrameInfo::VALID_ALL, frame->windows_frame_info->valid);
    EXPECT_EQ(WindowsFrameInfo::STACK_INFO_FRAME_DATA,
              frame->windows_frame_info->type_);
    EXPECT_EQ("$T1 .raSearch = $T0 "
              "$T1 4 - 8 @ = $ebp $T1 4 - ^ = $eip $T1 ^ = $esp $T1 4 + = "
              "$23 $T0 20 - ^ =  $24 $T0 24 - ^ =",
              frame->windows_frame_info->program_string);
    EXPECT_FALSE(frame->windows_frame_info->allocates_base_pointer);
  }
}

// Scan the stack for a return address and potentially skip frames when the
// current IP address is not in a known module.  Note, that that the span of
// this scan is limited to 120 search words for the context frame and 30
// search words (pointers) for the other frames:
//     const int kRASearchWords = 30;
void GetCallerFrame::IPAddressIsNotInKnownModuleTestImpl(
    bool has_corrupt_symbols) {
  MockCodeModule remoting_core_dll(0x54080000, 0x501000, "remoting_core.dll",
                                   "version1");
  string symbols_func_section =
      "FUNC 137214 17d 10 PK11_Verify\n"
      "FUNC 15c834 37 14 nsc_ECDSAVerifyStub\n"
      "FUNC 1611d3 91 14 NSC_Verify\n"
      "FUNC 162ff7 60 4 sftk_SessionFromHandle\n";
  string symbols_stack_section =
                   "STACK WIN 4 137214 17d 9 0 10 0 10 0 1 $T0 $ebp = "
                   "$eip $T0 4 + ^ = $ebp $T0 ^ = $esp $T0 8 + =\n"
                   "STACK WIN 4 15c834 37 6 0 14 0 18 0 1 $T0 $ebp = "
                   "$eip $T0 4 + ^ = $ebp $T0 ^ = $esp $T0 8 + =\n"
                   "STACK WIN 4 1611d3 91 7 0 14 0 8 0 1 $T0 $ebp = "
                   "$eip $T0 4 + ^ = $ebp $T0 ^ = $esp $T0 8 + =\n"
                   "STACK WIN 4 162ff7 60 5 0 4 0 0 0 1 $T0 $ebp = "
                   "$eip $T0 4 + ^ = $ebp $T0 ^ = $esp $T0 8 + =\n";

  string symbols = symbols_func_section;
  if (has_corrupt_symbols) {
    symbols.append(string(1, '\0'));           // null terminator in the middle
    symbols.append("\n");
    symbols.append("FUNC 1234\n"               // invalid FUNC records
                   "FUNNC 1234\n"
                   "STACK WIN 4 1234 234 23 "  // invalid STACK record
                   "23423423 234 23 234 234 "
                   "234 23 234 23 234 234 "
                   "234 234 234\n");
  }
  symbols.append(symbols_stack_section);
  SetModuleSymbols(&remoting_core_dll, symbols);

  // Create some modules with some stock debugging information.
  MockCodeModules local_modules;
  local_modules.Add(&remoting_core_dll);

  Label frame0_esp;
  Label frame0_ebp;
  Label frame1_ebp;
  Label frame1_esp;
  Label frame2_ebp;
  Label frame2_esp;
  Label frame3_ebp;
  Label frame3_esp;
  Label bogus_stack_location_1;
  Label bogus_stack_location_2;
  Label bogus_stack_location_3;

  stack_section.start() = 0x01a3ea28;
  stack_section
    .Mark(&frame0_esp)
    .D32(bogus_stack_location_2)
    .D32(bogus_stack_location_1)
    .D32(0x042478e4)
    .D32(bogus_stack_location_2)
    .D32(0x00000000)
    .D32(0x041f0420)
    .D32(0x00000000)
    .D32(0x00000000)
    .D32(0x00000040)
    .D32(0x00000001)
    .D32(0x00b7e0d0)
    .D32(0x00000000)
    .D32(0x00000040)
    .D32(0x00000001)
    .D32(0x00b7f570)
    .Mark(&bogus_stack_location_1)
    .D32(0x00000000)
    .D32(0x00000040)
    .D32(0x00000008)
    .D32(0x04289530)
    .D32(0x00000000)
    .D32(0x00000040)
    .D32(0x00000008)
    .D32(0x00b7e910)
    .D32(0x00000000)
    .D32(0x00000040)
    .D32(0x00000008)
    .D32(0x00b7d998)
    .D32(0x00000000)
    .D32(0x00000040)
    .D32(0x00000008)
    .D32(0x00b7dec0)
    .Mark(&bogus_stack_location_2)
    .D32(0x00000000)
    .D32(0x00000040)
    .D32(0x00000008)
    .D32(0x04289428)
    .D32(0x00000000)
    .D32(0x00000040)
    .D32(0x00000008)
    .D32(0x00b7f258)
    .Mark(&bogus_stack_location_3)
    .D32(0x00000000)
    .D32(0x041f3560)
    .D32(0x00000041)
    .D32(0x00000020)
    .D32(0xffffffff)
    .Mark(&frame0_ebp)
    .D32(frame1_ebp)  // Child %ebp
    .D32(0x541dc866)  // return address of frame 0
                      // inside remoting_core!nsc_ECDSAVerifyStub+0x32
    .Mark(&frame1_esp)
    .D32(0x04247860)
    .D32(0x01a3eaec)
    .D32(0x01a3eaf8)
    .D32(0x541e304f)  // remoting_core!sftk_SessionFromHandle+0x58
    .D32(0x0404c620)
    .D32(0x00000040)
    .D32(0x01a3eb2c)
    .D32(0x01a3ec08)
    .D32(0x00000014)
    .Mark(&frame1_ebp)
    .D32(frame2_ebp)  // Child %ebp
    .D32(0x541e1234)  // return address of frame 1
                      // inside remoting_core!NSC_Verify+0x61
    .Mark(&frame2_esp)
    .D32(0x04247858)
    .D32(0x0404c620)
    .D32(0x00000040)
    .D32(0x01a3ec08)
    .D32(0x00000014)
    .D32(0x01000005)
    .D32(0x00b2f7a0)
    .D32(0x041f0420)
    .D32(0x041f3650)
    .Mark(&frame2_ebp)
    .D32(frame3_ebp)  // Child %ebp
    .D32(0x541b734d)  // return address of frame 1
                      // inside remoting_core!PK11_Verify+0x139
    .Mark(&frame3_esp)
    .D32(0x01000005)
    .D32(0x01a3ec08)
    .D32(0x00000014)
    .D32(0x0404c620)
    .D32(0x00000040)
    .D32(0x04073e00)
    .D32(0x04073e00)
    .D32(0x04247050)
    .D32(0x00001041)
    .D32(0x00000000)
    .D32(0x00000000)
    .D32(0x00000000)
    .Mark(&frame3_ebp)
    .D32(0)           // saved %ebp (stack end)
    .D32(0);          // saved %eip (stack end)

  RegionFromSection();
  raw_context.eip = 0x4247860;   // IP address not in known module
  raw_context.ebp = 0x5420362d;  // bogus
  raw_context.esp = frame0_esp.Value();

  // sanity
  ASSERT_TRUE(raw_context.esp == stack_section.start().Value());

  StackFrameSymbolizer frame_symbolizer(&supplier, &resolver);
  StackwalkerX86 walker(&system_info, &raw_context, &stack_region,
                        &local_modules, &frame_symbolizer);
  vector<const CodeModule*> modules_without_symbols;
  vector<const CodeModule*> modules_with_corrupt_symbols;
  ASSERT_TRUE(walker.Walk(&call_stack, &modules_without_symbols,
                          &modules_with_corrupt_symbols));
  ASSERT_EQ(0U, modules_without_symbols.size());
  if (has_corrupt_symbols) {
    ASSERT_EQ(1U, modules_with_corrupt_symbols.size());
    ASSERT_EQ("remoting_core.dll",
              modules_with_corrupt_symbols[0]->debug_file());
  } else {
    ASSERT_EQ(0U, modules_with_corrupt_symbols.size());
  }
  frames = call_stack.frames();

  ASSERT_EQ(4U, frames->size());

  {  // To avoid reusing locals by mistake
    StackFrameX86 *frame0 = static_cast<StackFrameX86 *>(frames->at(0));
    EXPECT_EQ(StackFrame::FRAME_TRUST_CONTEXT, frame0->trust);
    ASSERT_EQ(StackFrameX86::CONTEXT_VALID_ALL, frame0->context_validity);
    EXPECT_EQ(raw_context.eip, frame0->context.eip);
    EXPECT_EQ(raw_context.ebp, frame0->context.ebp);
    EXPECT_EQ(raw_context.esp, frame0->context.esp);
    EXPECT_EQ(NULL, frame0->module);  // IP not in known module
    EXPECT_EQ("", frame0->function_name);
    ASSERT_EQ(NULL, frame0->windows_frame_info);
  }

  {  // To avoid reusing locals by mistake
    StackFrameX86 *frame1 = static_cast<StackFrameX86 *>(frames->at(1));
    EXPECT_EQ(StackFrame::FRAME_TRUST_SCAN, frame1->trust);
    ASSERT_EQ((StackFrameX86::CONTEXT_VALID_EIP |
               StackFrameX86::CONTEXT_VALID_ESP |
               StackFrameX86::CONTEXT_VALID_EBP),
              frame1->context_validity);
    EXPECT_EQ(frame1_ebp.Value(), frame1->context.ebp);
    EXPECT_EQ(frame1_esp.Value(), frame1->context.esp);
    EXPECT_EQ(&remoting_core_dll, frame1->module);
    EXPECT_EQ("nsc_ECDSAVerifyStub", frame1->function_name);
    ASSERT_TRUE(frame1->windows_frame_info != NULL);
    EXPECT_EQ(WindowsFrameInfo::VALID_ALL, frame1->windows_frame_info->valid);
    EXPECT_EQ(WindowsFrameInfo::STACK_INFO_FRAME_DATA,
              frame1->windows_frame_info->type_);
    EXPECT_EQ("$T0 $ebp = $eip $T0 4 + ^ = $ebp $T0 ^ = $esp $T0 8 + =",
              frame1->windows_frame_info->program_string);
    EXPECT_FALSE(frame1->windows_frame_info->allocates_base_pointer);
  }

  {  // To avoid reusing locals by mistake
    StackFrameX86 *frame2 = static_cast<StackFrameX86 *>(frames->at(2));
    EXPECT_EQ(StackFrame::FRAME_TRUST_CFI, frame2->trust);
    ASSERT_EQ((StackFrameX86::CONTEXT_VALID_EIP |
               StackFrameX86::CONTEXT_VALID_ESP |
               StackFrameX86::CONTEXT_VALID_EBP),
              frame2->context_validity);
    EXPECT_EQ(frame2_ebp.Value(), frame2->context.ebp);
    EXPECT_EQ(frame2_esp.Value(), frame2->context.esp);
    EXPECT_EQ(&remoting_core_dll, frame2->module);
    EXPECT_EQ("NSC_Verify", frame2->function_name);
    ASSERT_TRUE(frame2->windows_frame_info != NULL);
    EXPECT_EQ(WindowsFrameInfo::VALID_ALL, frame2->windows_frame_info->valid);
    EXPECT_EQ(WindowsFrameInfo::STACK_INFO_FRAME_DATA,
              frame2->windows_frame_info->type_);
    EXPECT_EQ("$T0 $ebp = $eip $T0 4 + ^ = $ebp $T0 ^ = $esp $T0 8 + =",
              frame2->windows_frame_info->program_string);
    EXPECT_FALSE(frame2->windows_frame_info->allocates_base_pointer);
  }

  {  // To avoid reusing locals by mistake
    StackFrameX86 *frame3 = static_cast<StackFrameX86 *>(frames->at(3));
    EXPECT_EQ(StackFrame::FRAME_TRUST_CFI, frame3->trust);
    ASSERT_EQ((StackFrameX86::CONTEXT_VALID_EIP |
               StackFrameX86::CONTEXT_VALID_ESP |
               StackFrameX86::CONTEXT_VALID_EBP),
              frame3->context_validity);
    EXPECT_EQ(frame3_ebp.Value(), frame3->context.ebp);
    EXPECT_EQ(frame3_esp.Value(), frame3->context.esp);
    EXPECT_EQ(&remoting_core_dll, frame3->module);
    EXPECT_EQ("PK11_Verify", frame3->function_name);
    ASSERT_TRUE(frame3->windows_frame_info != NULL);
    EXPECT_EQ(WindowsFrameInfo::VALID_ALL, frame3->windows_frame_info->valid);
    EXPECT_EQ(WindowsFrameInfo::STACK_INFO_FRAME_DATA,
              frame3->windows_frame_info->type_);
    EXPECT_EQ("$T0 $ebp = $eip $T0 4 + ^ = $ebp $T0 ^ = $esp $T0 8 + =",
              frame3->windows_frame_info->program_string);
    EXPECT_FALSE(frame3->windows_frame_info->allocates_base_pointer);
  }
}

// Runs IPAddressIsNotInKnownModule test with good symbols
TEST_F(GetCallerFrame, IPAddressIsNotInKnownModule) {
  IPAddressIsNotInKnownModuleTestImpl(false /* has_corrupt_modules */);
}

// Runs IPAddressIsNotInKnownModule test with corrupt symbols
TEST_F(GetCallerFrame, IPAddressIsNotInKnownModule_CorruptSymbols) {
  IPAddressIsNotInKnownModuleTestImpl(true /* has_corrupt_modules */);
}

struct CFIFixture: public StackwalkerX86Fixture {
  CFIFixture() {
    // Provide a bunch of STACK CFI records; individual tests walk to the
    // caller from every point in this series, expecting to find the same
    // set of register values.
    SetModuleSymbols(&module1,
                     // The youngest frame's function.
                     "FUNC 4000 1000 10 enchiridion\n"
                     // Initially, just a return address.
                     "STACK CFI INIT 4000 100 .cfa: $esp 4 + .ra: .cfa 4 - ^\n"
                     // Push %ebx.
                     "STACK CFI 4001 .cfa: $esp 8 + $ebx: .cfa 8 - ^\n"
                     // Move %esi into %ebx.  Weird, but permitted.
                     "STACK CFI 4002 $esi: $ebx\n"
                     // Allocate frame space, and save %edi.
                     "STACK CFI 4003 .cfa: $esp 20 + $edi: .cfa 16 - ^\n"
                     // Put the return address in %edi.
                     "STACK CFI 4005 .ra: $edi\n"
                     // Save %ebp, and use it as a frame pointer.
                     "STACK CFI 4006 .cfa: $ebp 8 + $ebp: .cfa 12 - ^\n"

                     // The calling function.
                     "FUNC 5000 1000 10 epictetus\n"
                     // Mark it as end of stack.
                     "STACK CFI INIT 5000 1000 .cfa: $esp .ra 0\n");

    // Provide some distinctive values for the caller's registers.
    expected.esp = 0x80000000;
    expected.eip = 0x40005510;
    expected.ebp = 0xc0d4aab9;
    expected.ebx = 0x60f20ce6;
    expected.esi = 0x53d1379d;
    expected.edi = 0xafbae234;

    // By default, registers are unchanged.
    raw_context = expected;
  }

  // Walk the stack, using stack_section as the contents of the stack
  // and raw_context as the current register values. (Set
  // raw_context.esp to the stack's starting address.) Expect two
  // stack frames; in the older frame, expect the callee-saves
  // registers to have values matching those in 'expected'.
  void CheckWalk() {
    RegionFromSection();
    raw_context.esp = stack_section.start().Value();

    StackFrameSymbolizer frame_symbolizer(&supplier, &resolver);
    StackwalkerX86 walker(&system_info, &raw_context, &stack_region, &modules,
                          &frame_symbolizer);
    vector<const CodeModule*> modules_without_symbols;
    vector<const CodeModule*> modules_with_corrupt_symbols;
    ASSERT_TRUE(walker.Walk(&call_stack, &modules_without_symbols,
                            &modules_with_corrupt_symbols));
    ASSERT_EQ(0U, modules_without_symbols.size());
    ASSERT_EQ(0U, modules_with_corrupt_symbols.size());
    frames = call_stack.frames();
    ASSERT_EQ(2U, frames->size());

    {  // To avoid reusing locals by mistake
      StackFrameX86 *frame0 = static_cast<StackFrameX86 *>(frames->at(0));
      EXPECT_EQ(StackFrame::FRAME_TRUST_CONTEXT, frame0->trust);
      ASSERT_EQ(StackFrameX86::CONTEXT_VALID_ALL, frame0->context_validity);
      EXPECT_EQ("enchiridion", frame0->function_name);
      EXPECT_EQ(0x40004000U, frame0->function_base);
      ASSERT_TRUE(frame0->windows_frame_info != NULL);
      ASSERT_EQ(WindowsFrameInfo::VALID_PARAMETER_SIZE,
                frame0->windows_frame_info->valid);
      ASSERT_TRUE(frame0->cfi_frame_info != NULL);
    }

    {  // To avoid reusing locals by mistake
      StackFrameX86 *frame1 = static_cast<StackFrameX86 *>(frames->at(1));
      EXPECT_EQ(StackFrame::FRAME_TRUST_CFI, frame1->trust);
      ASSERT_EQ((StackFrameX86::CONTEXT_VALID_EIP |
                 StackFrameX86::CONTEXT_VALID_ESP |
                 StackFrameX86::CONTEXT_VALID_EBP |
                 StackFrameX86::CONTEXT_VALID_EBX |
                 StackFrameX86::CONTEXT_VALID_ESI |
                 StackFrameX86::CONTEXT_VALID_EDI),
                 frame1->context_validity);
      EXPECT_EQ(expected.eip, frame1->context.eip);
      EXPECT_EQ(expected.esp, frame1->context.esp);
      EXPECT_EQ(expected.ebp, frame1->context.ebp);
      EXPECT_EQ(expected.ebx, frame1->context.ebx);
      EXPECT_EQ(expected.esi, frame1->context.esi);
      EXPECT_EQ(expected.edi, frame1->context.edi);
      EXPECT_EQ("epictetus", frame1->function_name);
    }
  }

  // The values the stack walker should find for the caller's registers.
  MDRawContextX86 expected;
};

class CFI: public CFIFixture, public Test { };

TEST_F(CFI, At4000) {
  Label frame1_esp = expected.esp;
  stack_section
    .D32(0x40005510)             // return address
    .Mark(&frame1_esp);          // This effectively sets stack_section.start().
  raw_context.eip = 0x40004000;
  CheckWalk();
}

TEST_F(CFI, At4001) {
  Label frame1_esp = expected.esp;
  stack_section
    .D32(0x60f20ce6)             // saved %ebx
    .D32(0x40005510)             // return address
    .Mark(&frame1_esp);          // This effectively sets stack_section.start().
  raw_context.eip = 0x40004001;
  raw_context.ebx = 0x91aa9a8b;  // callee's %ebx value
  CheckWalk();
}

TEST_F(CFI, At4002) {
  Label frame1_esp = expected.esp;
  stack_section
    .D32(0x60f20ce6)             // saved %ebx
    .D32(0x40005510)             // return address
    .Mark(&frame1_esp);          // This effectively sets stack_section.start().
  raw_context.eip = 0x40004002;
  raw_context.ebx = 0x53d1379d;  // saved %esi
  raw_context.esi = 0xa5c790ed;  // callee's %esi value
  CheckWalk();
}

TEST_F(CFI, At4003) {
  Label frame1_esp = expected.esp;
  stack_section
    .D32(0x56ec3db7)             // garbage
    .D32(0xafbae234)             // saved %edi
    .D32(0x53d67131)             // garbage
    .D32(0x60f20ce6)             // saved %ebx
    .D32(0x40005510)             // return address
    .Mark(&frame1_esp);          // This effectively sets stack_section.start().
  raw_context.eip = 0x40004003;
  raw_context.ebx = 0x53d1379d;  // saved %esi
  raw_context.esi = 0xa97f229d;  // callee's %esi
  raw_context.edi = 0xb05cc997;  // callee's %edi
  CheckWalk();
}

// The results here should be the same as those at module offset
// 0x4003.
TEST_F(CFI, At4004) {
  Label frame1_esp = expected.esp;
  stack_section
    .D32(0xe29782c2)             // garbage
    .D32(0xafbae234)             // saved %edi
    .D32(0x5ba29ce9)             // garbage
    .D32(0x60f20ce6)             // saved %ebx
    .D32(0x40005510)             // return address
    .Mark(&frame1_esp);          // This effectively sets stack_section.start().
  raw_context.eip = 0x40004004;
  raw_context.ebx = 0x53d1379d;  // saved %esi
  raw_context.esi = 0x0fb7dc4e;  // callee's %esi
  raw_context.edi = 0x993b4280;  // callee's %edi
  CheckWalk();
}

TEST_F(CFI, At4005) {
  Label frame1_esp = expected.esp;
  stack_section
    .D32(0xe29782c2)             // garbage
    .D32(0xafbae234)             // saved %edi
    .D32(0x5ba29ce9)             // garbage
    .D32(0x60f20ce6)             // saved %ebx
    .D32(0x8036cc02)             // garbage
    .Mark(&frame1_esp);          // This effectively sets stack_section.start().
  raw_context.eip = 0x40004005;
  raw_context.ebx = 0x53d1379d;  // saved %esi
  raw_context.esi = 0x0fb7dc4e;  // callee's %esi
  raw_context.edi = 0x40005510;  // return address
  CheckWalk();
}

TEST_F(CFI, At4006) {
  Label frame0_ebp;
  Label frame1_esp = expected.esp;
  stack_section
    .D32(0xdcdd25cd)             // garbage
    .D32(0xafbae234)             // saved %edi
    .D32(0xc0d4aab9)             // saved %ebp
    .Mark(&frame0_ebp)           // frame pointer points here
    .D32(0x60f20ce6)             // saved %ebx
    .D32(0x8036cc02)             // garbage
    .Mark(&frame1_esp);          // This effectively sets stack_section.start().
  raw_context.eip = 0x40004006;
  raw_context.ebp = frame0_ebp.Value();
  raw_context.ebx = 0x53d1379d;  // saved %esi
  raw_context.esi = 0x743833c9;  // callee's %esi
  raw_context.edi = 0x40005510;  // return address
  CheckWalk();
}

