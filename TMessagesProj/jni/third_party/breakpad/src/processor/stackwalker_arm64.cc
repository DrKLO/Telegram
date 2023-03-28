// Copyright (c) 2013 Google Inc.
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

// stackwalker_arm64.cc: arm64-specific stackwalker.
//
// See stackwalker_arm64.h for documentation.
//
// Author: Mark Mentovai, Ted Mielczarek, Jim Blandy, Colin Blundell

#include <vector>

#include "common/scoped_ptr.h"
#include "google_breakpad/processor/call_stack.h"
#include "google_breakpad/processor/memory_region.h"
#include "google_breakpad/processor/source_line_resolver_interface.h"
#include "google_breakpad/processor/stack_frame_cpu.h"
#include "processor/cfi_frame_info.h"
#include "processor/logging.h"
#include "processor/stackwalker_arm64.h"

namespace google_breakpad {


StackwalkerARM64::StackwalkerARM64(const SystemInfo* system_info,
                                   const MDRawContextARM64* context,
                                   MemoryRegion* memory,
                                   const CodeModules* modules,
                                   StackFrameSymbolizer* resolver_helper)
    : Stackwalker(system_info, memory, modules, resolver_helper),
      context_(context),
      context_frame_validity_(StackFrameARM64::CONTEXT_VALID_ALL) { }


StackFrame* StackwalkerARM64::GetContextFrame() {
  if (!context_) {
    BPLOG(ERROR) << "Can't get context frame without context";
    return NULL;
  }

  StackFrameARM64* frame = new StackFrameARM64();

  // The instruction pointer is stored directly in a register (x32), so pull it
  // straight out of the CPU context structure.
  frame->context = *context_;
  frame->context_validity = context_frame_validity_;
  frame->trust = StackFrame::FRAME_TRUST_CONTEXT;
  frame->instruction = frame->context.iregs[MD_CONTEXT_ARM64_REG_PC];

  return frame;
}

StackFrameARM64* StackwalkerARM64::GetCallerByCFIFrameInfo(
    const vector<StackFrame*> &frames,
    CFIFrameInfo* cfi_frame_info) {
  StackFrameARM64* last_frame = static_cast<StackFrameARM64*>(frames.back());

  static const char* register_names[] = {
    "x0",  "x1",  "x2",  "x3",  "x4",  "x5",  "x6",  "x7",
    "x8",  "x9",  "x10", "x11", "x12", "x13", "x14", "x15",
    "x16", "x17", "x18", "x19", "x20", "x21", "x22", "x23",
    "x24", "x25", "x26", "x27", "x28", "x29", "x30", "sp",
    "pc",  NULL
  };

  // Populate a dictionary with the valid register values in last_frame.
  CFIFrameInfo::RegisterValueMap<uint64_t> callee_registers;
  for (int i = 0; register_names[i]; i++) {
    if (last_frame->context_validity & StackFrameARM64::RegisterValidFlag(i))
      callee_registers[register_names[i]] = last_frame->context.iregs[i];
  }

  // Use the STACK CFI data to recover the caller's register values.
  CFIFrameInfo::RegisterValueMap<uint64_t> caller_registers;
  if (!cfi_frame_info->FindCallerRegs(callee_registers, *memory_,
                                      &caller_registers)) {
    return NULL;
  }
  // Construct a new stack frame given the values the CFI recovered.
  scoped_ptr<StackFrameARM64> frame(new StackFrameARM64());
  for (int i = 0; register_names[i]; i++) {
    CFIFrameInfo::RegisterValueMap<uint64_t>::iterator entry =
      caller_registers.find(register_names[i]);
    if (entry != caller_registers.end()) {
      // We recovered the value of this register; fill the context with the
      // value from caller_registers.
      frame->context_validity |= StackFrameARM64::RegisterValidFlag(i);
      frame->context.iregs[i] = entry->second;
    } else if (19 <= i && i <= 29 && (last_frame->context_validity &
                                      StackFrameARM64::RegisterValidFlag(i))) {
      // If the STACK CFI data doesn't mention some callee-saves register, and
      // it is valid in the callee, assume the callee has not yet changed it.
      // Registers r19 through r29 are callee-saves, according to the Procedure
      // Call Standard for the ARM AARCH64 Architecture, which the Linux ABI
      // follows.
      frame->context_validity |= StackFrameARM64::RegisterValidFlag(i);
      frame->context.iregs[i] = last_frame->context.iregs[i];
    }
  }
  // If the CFI doesn't recover the PC explicitly, then use .ra.
  if (!(frame->context_validity & StackFrameARM64::CONTEXT_VALID_PC)) {
    CFIFrameInfo::RegisterValueMap<uint64_t>::iterator entry =
      caller_registers.find(".ra");
    if (entry != caller_registers.end()) {
      frame->context_validity |= StackFrameARM64::CONTEXT_VALID_PC;
      frame->context.iregs[MD_CONTEXT_ARM64_REG_PC] = entry->second;
    }
  }
  // If the CFI doesn't recover the SP explicitly, then use .cfa.
  if (!(frame->context_validity & StackFrameARM64::CONTEXT_VALID_SP)) {
    CFIFrameInfo::RegisterValueMap<uint64_t>::iterator entry =
      caller_registers.find(".cfa");
    if (entry != caller_registers.end()) {
      frame->context_validity |= StackFrameARM64::CONTEXT_VALID_SP;
      frame->context.iregs[MD_CONTEXT_ARM64_REG_SP] = entry->second;
    }
  }

  // If we didn't recover the PC and the SP, then the frame isn't very useful.
  static const uint64_t essentials = (StackFrameARM64::CONTEXT_VALID_SP
                                     | StackFrameARM64::CONTEXT_VALID_PC);
  if ((frame->context_validity & essentials) != essentials)
    return NULL;

  frame->trust = StackFrame::FRAME_TRUST_CFI;
  return frame.release();
}

StackFrameARM64* StackwalkerARM64::GetCallerByStackScan(
    const vector<StackFrame*> &frames) {
  StackFrameARM64* last_frame = static_cast<StackFrameARM64*>(frames.back());
  uint64_t last_sp = last_frame->context.iregs[MD_CONTEXT_ARM64_REG_SP];
  uint64_t caller_sp, caller_pc;

  if (!ScanForReturnAddress(last_sp, &caller_sp, &caller_pc,
                            frames.size() == 1 /* is_context_frame */)) {
    // No plausible return address was found.
    return NULL;
  }

  // ScanForReturnAddress found a reasonable return address. Advance
  // %sp to the location above the one where the return address was
  // found.
  caller_sp += 8;

  // Create a new stack frame (ownership will be transferred to the caller)
  // and fill it in.
  StackFrameARM64* frame = new StackFrameARM64();

  frame->trust = StackFrame::FRAME_TRUST_SCAN;
  frame->context = last_frame->context;
  frame->context.iregs[MD_CONTEXT_ARM64_REG_PC] = caller_pc;
  frame->context.iregs[MD_CONTEXT_ARM64_REG_SP] = caller_sp;
  frame->context_validity = StackFrameARM64::CONTEXT_VALID_PC |
                            StackFrameARM64::CONTEXT_VALID_SP;

  return frame;
}

StackFrameARM64* StackwalkerARM64::GetCallerByFramePointer(
    const vector<StackFrame*> &frames) {
  StackFrameARM64* last_frame = static_cast<StackFrameARM64*>(frames.back());

  uint64_t last_fp = last_frame->context.iregs[MD_CONTEXT_ARM64_REG_FP];

  uint64_t caller_fp = 0;
  if (last_fp && !memory_->GetMemoryAtAddress(last_fp, &caller_fp)) {
    BPLOG(ERROR) << "Unable to read caller_fp from last_fp: 0x"
                 << std::hex << last_fp;
    return NULL;
  }

  uint64_t caller_lr = 0;
  if (last_fp && !memory_->GetMemoryAtAddress(last_fp + 8, &caller_lr)) {
    BPLOG(ERROR) << "Unable to read caller_lr from last_fp + 8: 0x"
                 << std::hex << (last_fp + 8);
    return NULL;
  }

  uint64_t caller_sp = last_fp ? last_fp + 16 :
      last_frame->context.iregs[MD_CONTEXT_ARM64_REG_SP];

  // Create a new stack frame (ownership will be transferred to the caller)
  // and fill it in.
  StackFrameARM64* frame = new StackFrameARM64();

  frame->trust = StackFrame::FRAME_TRUST_FP;
  frame->context = last_frame->context;
  frame->context.iregs[MD_CONTEXT_ARM64_REG_FP] = caller_fp;
  frame->context.iregs[MD_CONTEXT_ARM64_REG_SP] = caller_sp;
  frame->context.iregs[MD_CONTEXT_ARM64_REG_PC] =
      last_frame->context.iregs[MD_CONTEXT_ARM64_REG_LR];
  frame->context.iregs[MD_CONTEXT_ARM64_REG_LR] = caller_lr;
  frame->context_validity = StackFrameARM64::CONTEXT_VALID_PC |
                            StackFrameARM64::CONTEXT_VALID_LR |
                            StackFrameARM64::CONTEXT_VALID_FP |
                            StackFrameARM64::CONTEXT_VALID_SP;
  return frame;
}

StackFrame* StackwalkerARM64::GetCallerFrame(const CallStack* stack,
                                             bool stack_scan_allowed) {
  if (!memory_ || !stack) {
    BPLOG(ERROR) << "Can't get caller frame without memory or stack";
    return NULL;
  }

  const vector<StackFrame*> &frames = *stack->frames();
  StackFrameARM64* last_frame = static_cast<StackFrameARM64*>(frames.back());
  scoped_ptr<StackFrameARM64> frame;

  // See if there is DWARF call frame information covering this address.
  scoped_ptr<CFIFrameInfo> cfi_frame_info(
      frame_symbolizer_->FindCFIFrameInfo(last_frame));
  if (cfi_frame_info.get())
    frame.reset(GetCallerByCFIFrameInfo(frames, cfi_frame_info.get()));

  // If CFI failed, or there wasn't CFI available, fall back to frame pointer.
  if (!frame.get())
    frame.reset(GetCallerByFramePointer(frames));

  // If everything failed, fall back to stack scanning.
  if (stack_scan_allowed && !frame.get())
    frame.reset(GetCallerByStackScan(frames));

  // If nothing worked, tell the caller.
  if (!frame.get())
    return NULL;

  // An instruction address of zero marks the end of the stack.
  if (frame->context.iregs[MD_CONTEXT_ARM64_REG_PC] == 0)
    return NULL;

  // If the new stack pointer is at a lower address than the old, then
  // that's clearly incorrect. Treat this as end-of-stack to enforce
  // progress and avoid infinite loops.
  if (frame->context.iregs[MD_CONTEXT_ARM64_REG_SP]
      < last_frame->context.iregs[MD_CONTEXT_ARM64_REG_SP])
    return NULL;

  // The new frame's context's PC is the return address, which is one
  // instruction past the instruction that caused us to arrive at the callee.
  // ARM64 instructions have a uniform 4-byte encoding, so subtracting 4 off
  // the return address gets back to the beginning of the call instruction.
  // Callers that require the exact return address value may access
  // frame->context.iregs[MD_CONTEXT_ARM64_REG_PC].
  frame->instruction = frame->context.iregs[MD_CONTEXT_ARM64_REG_PC] - 4;

  return frame.release();
}


}  // namespace google_breakpad
