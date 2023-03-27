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

// stackwalker_sparc.cc: sparc-specific stackwalker.
//
// See stackwalker_sparc.h for documentation.
//
// Author: Michael Shang


#include "google_breakpad/processor/call_stack.h"
#include "google_breakpad/processor/memory_region.h"
#include "google_breakpad/processor/stack_frame_cpu.h"
#include "processor/logging.h"
#include "processor/stackwalker_sparc.h"

namespace google_breakpad {


StackwalkerSPARC::StackwalkerSPARC(const SystemInfo* system_info,
                                   const MDRawContextSPARC* context,
                                   MemoryRegion* memory,
                                   const CodeModules* modules,
                                   StackFrameSymbolizer* resolver_helper)
    : Stackwalker(system_info, memory, modules, resolver_helper),
      context_(context) {
}


StackFrame* StackwalkerSPARC::GetContextFrame() {
  if (!context_) {
    BPLOG(ERROR) << "Can't get context frame without context";
    return NULL;
  }

  StackFrameSPARC* frame = new StackFrameSPARC();

  // The instruction pointer is stored directly in a register, so pull it
  // straight out of the CPU context structure.
  frame->context = *context_;
  frame->context_validity = StackFrameSPARC::CONTEXT_VALID_ALL;
  frame->trust = StackFrame::FRAME_TRUST_CONTEXT;
  frame->instruction = frame->context.pc;

  return frame;
}


StackFrame* StackwalkerSPARC::GetCallerFrame(const CallStack* stack,
                                             bool stack_scan_allowed) {
  if (!memory_ || !stack) {
    BPLOG(ERROR) << "Can't get caller frame without memory or stack";
    return NULL;
  }

  StackFrameSPARC* last_frame = static_cast<StackFrameSPARC*>(
      stack->frames()->back());

  // new: caller
  // old: callee
  // %fp, %i6 and g_r[30] is the same, see minidump_format.h
  // %sp, %o6 and g_r[14] is the same, see minidump_format.h
  // %sp_new = %fp_old
  // %fp_new = *(%fp_old + 32 + 32 - 8), where the callee's %i6
  // %pc_new = *(%fp_old + 32 + 32 - 4) + 8
  // which is callee's %i7 plus 8

  // A caller frame must reside higher in memory than its callee frames.
  // Anything else is an error, or an indication that we've reached the
  // end of the stack.
  uint64_t stack_pointer = last_frame->context.g_r[30];
  if (stack_pointer <= last_frame->context.g_r[14]) {
    return NULL;
  }

  uint32_t instruction;
  if (!memory_->GetMemoryAtAddress(stack_pointer + 60,
                     &instruction) || instruction <= 1) {
    return NULL;
  }

  uint32_t stack_base;
  if (!memory_->GetMemoryAtAddress(stack_pointer + 56,
                     &stack_base) || stack_base <= 1) {
    return NULL;
  }

  StackFrameSPARC* frame = new StackFrameSPARC();

  frame->context = last_frame->context;
  frame->context.g_r[14] = stack_pointer;
  frame->context.g_r[30] = stack_base;

  // frame->context.pc is the return address, which is 2 instruction
  // past the branch that caused us to arrive at the callee, which are
  // a CALL instruction then a NOP instruction.
  // frame_ppc->instruction to 8 less than that.  Since all sparc
  // instructions are 4 bytes wide, this is the address of the branch
  // instruction.  This allows source line information to match up with the
  // line that contains a function call.  Callers that require the exact
  // return address value may access the %i7/g_r[31] field of StackFrameSPARC.
  frame->context.pc = instruction + 8;
  frame->instruction = instruction;
  frame->context_validity = StackFrameSPARC::CONTEXT_VALID_PC |
                            StackFrameSPARC::CONTEXT_VALID_SP |
                            StackFrameSPARC::CONTEXT_VALID_FP;
  frame->trust = StackFrame::FRAME_TRUST_FP;

  return frame;
}


}  // namespace google_breakpad
