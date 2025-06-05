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

// stackwalker_mips.cc: MIPS-specific stackwalker.
//
// See stackwalker_mips.h for documentation.
//
// Author: Tata Elxsi

#include "common/scoped_ptr.h"
#include "google_breakpad/processor/call_stack.h"
#include "google_breakpad/processor/code_modules.h"
#include "google_breakpad/processor/memory_region.h"
#include "google_breakpad/processor/source_line_resolver_interface.h"
#include "google_breakpad/processor/stack_frame_cpu.h"
#include "processor/cfi_frame_info.h"
#include "processor/logging.h"
#include "processor/postfix_evaluator-inl.h"
#include "processor/stackwalker_mips.h"
#include "processor/windows_frame_info.h"
#include "google_breakpad/common/minidump_cpu_mips.h"

namespace google_breakpad {

StackwalkerMIPS::StackwalkerMIPS(const SystemInfo* system_info,
                                 const MDRawContextMIPS* context,
                                 MemoryRegion* memory,
                                 const CodeModules* modules,
                                 StackFrameSymbolizer* resolver_helper)
    : Stackwalker(system_info, memory, modules, resolver_helper),
      context_(context) {
  if (memory_ && memory_->GetBase() + memory_->GetSize() - 1 > 0xffffffff) {
    BPLOG(ERROR) << "Memory out of range for stackwalking: "
                 << HexString(memory_->GetBase())
                 << "+"
                 << HexString(memory_->GetSize());
    memory_ = NULL;
  }
}

StackFrame* StackwalkerMIPS::GetContextFrame() {
  if (!context_) {
    BPLOG(ERROR) << "Can't get context frame without context.";
    return NULL;
  }

  StackFrameMIPS* frame = new StackFrameMIPS();

  // The instruction pointer is stored directly in a register, so pull it
  // straight out of the CPU context structure.
  frame->context = *context_;
  frame->context_validity = StackFrameMIPS::CONTEXT_VALID_ALL;
  frame->trust = StackFrame::FRAME_TRUST_CONTEXT;
  frame->instruction = frame->context.epc;

  return frame;
}

// Register names for mips.
static const char* const kRegisterNames[] = {
   "$zero", "$at", "$v0", "$v1", "$a0", "$a1", "$a2", "$a3", "$to", "$t1",
   "$t2",   "$t3", "$t4", "$t5", "$t6", "$t7", "$s0", "$s1", "$s2", "$s3",
   "$s4",   "$s5", "$s6", "$s7", "$t8", "$t9", "$k0", "$k1", "$gp", "$sp",
   "$fp",   "$ra", NULL
  // TODO(gordanac): add float point save registers
};

StackFrameMIPS* StackwalkerMIPS::GetCallerByCFIFrameInfo(
    const vector<StackFrame*>& frames,
    CFIFrameInfo* cfi_frame_info) {
  StackFrameMIPS* last_frame = static_cast<StackFrameMIPS*>(frames.back());

  uint32_t sp = 0, pc = 0;

  // Populate a dictionary with the valid register values in last_frame.
  CFIFrameInfo::RegisterValueMap<uint32_t> callee_registers;
  // Use the STACK CFI data to recover the caller's register values.
  CFIFrameInfo::RegisterValueMap<uint32_t> caller_registers;

  for (int i = 0; kRegisterNames[i]; ++i) {
    caller_registers[kRegisterNames[i]] = last_frame->context.iregs[i];
    callee_registers[kRegisterNames[i]] = last_frame->context.iregs[i];
  }

  if (!cfi_frame_info->FindCallerRegs(callee_registers, *memory_,
                                      &caller_registers))  {
    return NULL;
  }

  CFIFrameInfo::RegisterValueMap<uint32_t>::const_iterator entry =
      caller_registers.find(".cfa");

  if (entry != caller_registers.end()) {
    sp = entry->second;
    caller_registers["$sp"] = entry->second;
  }

  entry = caller_registers.find(".ra");
  if (entry != caller_registers.end()) {
    caller_registers["$ra"] = entry->second;
    pc = entry->second - 2 * sizeof(pc);
  }
  caller_registers["$pc"] = pc;
  // Construct a new stack frame given the values the CFI recovered.
  scoped_ptr<StackFrameMIPS> frame(new StackFrameMIPS());

  for (int i = 0; kRegisterNames[i]; ++i) {
    CFIFrameInfo::RegisterValueMap<uint32_t>::const_iterator caller_entry =
        caller_registers.find(kRegisterNames[i]);

    if (caller_entry != caller_registers.end()) {
      // The value of this register is recovered; fill the context with the
      // value from caller_registers.
      frame->context.iregs[i] = caller_entry->second;
      frame->context_validity |= StackFrameMIPS::RegisterValidFlag(i);
    } else if (((i >= INDEX_MIPS_REG_S0 && i <= INDEX_MIPS_REG_S7) ||
                (i > INDEX_MIPS_REG_GP && i < INDEX_MIPS_REG_RA)) &&
               (last_frame->context_validity &
                StackFrameMIPS::RegisterValidFlag(i))) {
      // If the STACK CFI data doesn't mention some callee-save register, and
      // it is valid in the callee, assume the callee has not yet changed it.
      // Calee-save registers according to the MIPS o32 ABI specification are:
      // $s0 to $s7
      // $sp, $s8
      frame->context.iregs[i] = last_frame->context.iregs[i];
      frame->context_validity |= StackFrameMIPS::RegisterValidFlag(i);
    }
  }

  frame->context.epc = caller_registers["$pc"];
  frame->instruction = caller_registers["$pc"];
  frame->context_validity |= StackFrameMIPS::CONTEXT_VALID_PC;
  
  frame->context.iregs[MD_CONTEXT_MIPS_REG_RA] = caller_registers["$ra"];
  frame->context_validity |= StackFrameMIPS::CONTEXT_VALID_RA;

  frame->trust = StackFrame::FRAME_TRUST_CFI;

  return frame.release();
}

StackFrame* StackwalkerMIPS::GetCallerFrame(const CallStack* stack,
                                            bool stack_scan_allowed) {
  if (!memory_ || !stack) {
    BPLOG(ERROR) << "Can't get caller frame without memory or stack";
    return NULL;
  }

  const vector<StackFrame*>& frames = *stack->frames();
  StackFrameMIPS* last_frame = static_cast<StackFrameMIPS*>(frames.back());
  scoped_ptr<StackFrameMIPS> new_frame;

  // See if there is DWARF call frame information covering this address.
  scoped_ptr<CFIFrameInfo> cfi_frame_info(
    frame_symbolizer_->FindCFIFrameInfo(last_frame));
  if (cfi_frame_info.get())
    new_frame.reset(GetCallerByCFIFrameInfo(frames, cfi_frame_info.get()));

  // If caller frame is not found in CFI try analyzing the stack.
  if (stack_scan_allowed && !new_frame.get()) {
    new_frame.reset(GetCallerByStackScan(frames));
  }

  // If nothing worked, tell the caller.
  if (!new_frame.get()) {
    return NULL;
  }

  // Treat an instruction address of 0 as end-of-stack.
  if (new_frame->context.epc == 0) {
    return NULL;
  }

  // If the new stack pointer is at a lower address than the old, then
  // that's clearly incorrect. Treat this as end-of-stack to enforce
  // progress and avoid infinite loops.
  if (new_frame->context.iregs[MD_CONTEXT_MIPS_REG_SP] <=
      last_frame->context.iregs[MD_CONTEXT_MIPS_REG_SP]) {
    return NULL;
  }
  
  return new_frame.release();
}

StackFrameMIPS* StackwalkerMIPS::GetCallerByStackScan(
    const vector<StackFrame*>& frames) {
  const uint32_t kMaxFrameStackSize = 1024;
  const uint32_t kMinArgsOnStack = 4;

  StackFrameMIPS* last_frame = static_cast<StackFrameMIPS*>(frames.back());

  uint32_t last_sp = last_frame->context.iregs[MD_CONTEXT_MIPS_REG_SP];
  uint32_t caller_pc, caller_sp, caller_fp;

  // Return address cannot be obtained directly.
  // Force stackwalking.

  // We cannot use frame pointer to get the return address.
  // We'll scan the stack for a
  // return address. This can happen if last_frame is executing code
  // for a module for which we don't have symbols.
  int count = kMaxFrameStackSize / sizeof(caller_pc);

  if (frames.size() > 1) {
      // In case of mips32 ABI stack frame of a nonleaf function
      // must have minimum stack frame assigned for 4 arguments (4 words).
      // Move stack pointer for 4 words to avoid reporting non-existing frames
      // for all frames except the topmost one.
      // There is no way of knowing if topmost frame belongs to a leaf or
      // a nonleaf function.
      last_sp +=  kMinArgsOnStack * sizeof(caller_pc);
      // Adjust 'count' so that return address is scanned only in limits 
      // of one stack frame.
      count -= kMinArgsOnStack;
  }

  do {
    // Scanning for return address from stack pointer of the last frame.
    if (!ScanForReturnAddress(last_sp, &caller_sp, &caller_pc, count)) {
      // If we can't find an instruction pointer even with stack scanning,
      // give up.
      BPLOG(ERROR) << " ScanForReturnAddress failed ";
      return NULL;
    }
    // Get $fp stored in the stack frame.
    if (!memory_->GetMemoryAtAddress(caller_sp - sizeof(caller_pc),
                                     &caller_fp)) {
      BPLOG(INFO) << " GetMemoryAtAddress for fp failed " ;
      return NULL;
    }

    count = count - (caller_sp - last_sp) / sizeof(caller_pc);
    // Now scan the next address in the stack.
    last_sp = caller_sp + sizeof(caller_pc);
  } while ((caller_fp - caller_sp >= kMaxFrameStackSize) && count > 0);

  if (!count) {
    BPLOG(INFO) << " No frame found " ;
    return NULL;
  }

  // ScanForReturnAddress found a reasonable return address. Advance
  // $sp to the location above the one where the return address was
  // found. 
  caller_sp += sizeof(caller_pc);
  // caller_pc is actually containing $ra value;
  // $pc is two instructions before $ra,
  // so the caller_pc needs to be decremented accordingly.
  caller_pc -= 2 * sizeof(caller_pc);


  // Create a new stack frame (ownership will be transferred to the caller)
  // and fill it in.
  StackFrameMIPS* frame = new StackFrameMIPS();
  frame->trust = StackFrame::FRAME_TRUST_SCAN;
  frame->context = last_frame->context;
  frame->context.epc = caller_pc;
  frame->context_validity |= StackFrameMIPS::CONTEXT_VALID_PC;
  frame->instruction = caller_pc;

  frame->context.iregs[MD_CONTEXT_MIPS_REG_SP] = caller_sp;
  frame->context_validity |= StackFrameMIPS::CONTEXT_VALID_SP;
  frame->context.iregs[MD_CONTEXT_MIPS_REG_FP] = caller_fp;
  frame->context_validity |= StackFrameMIPS::CONTEXT_VALID_FP;

  frame->context.iregs[MD_CONTEXT_MIPS_REG_RA] =
      caller_pc + 2 * sizeof(caller_pc);
  frame->context_validity |= StackFrameMIPS::CONTEXT_VALID_RA;

  return frame;
}

}  // namespace google_breakpad

