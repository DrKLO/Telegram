// Copyright (c) 2006, Google Inc.
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

// stackwalker_selftest.cc: Tests StackwalkerX86 or StackwalkerPPC using the
// running process' stack as test data, if running on an x86 or ppc and
// compiled with gcc.  This test is not enabled in the "make check" suite
// by default, because certain optimizations interfere with its proper
// operation.  To turn it on, configure with --enable-selftest.
//
// Optimizations that cause problems:
//  - stack frame reuse.  The Recursor function here calls itself with
//    |return Recursor|.  When the caller's frame is reused, it will cause
//    CountCallerFrames to correctly return the same number of frames
//    in both the caller and callee.  This is considered an unexpected
//    condition in the test, which expects a callee to have one more
//    caller frame in the stack than its caller.
//  - frame pointer omission.  Even with a stackwalker that understands
//    this optimization, the code to harness debug information currently
//    only exists to retrieve it from minidumps, not the current process.
//
// This test can also serve as a developmental and debugging aid if
// PRINT_STACKS is defined.
//
// Author: Mark Mentovai

#include <assert.h>

#include "processor/logging.h"

#if defined(__i386) && !defined(__i386__)
#define __i386__
#endif
#if defined(__sparc) && !defined(__sparc__)
#define __sparc__
#endif
 
#if (defined(__SUNPRO_CC) || defined(__GNUC__)) && \
    (defined(__i386__) || defined(__ppc__) || defined(__sparc__))


#include <stdio.h>

#include "common/scoped_ptr.h"
#include "google_breakpad/common/breakpad_types.h"
#include "google_breakpad/common/minidump_format.h"
#include "google_breakpad/processor/basic_source_line_resolver.h"
#include "google_breakpad/processor/call_stack.h"
#include "google_breakpad/processor/code_module.h"
#include "google_breakpad/processor/memory_region.h"
#include "google_breakpad/processor/stack_frame.h"
#include "google_breakpad/processor/stack_frame_cpu.h"

using google_breakpad::BasicSourceLineResolver;
using google_breakpad::CallStack;
using google_breakpad::CodeModule;
using google_breakpad::MemoryRegion;
using google_breakpad::scoped_ptr;
using google_breakpad::StackFrame;
using google_breakpad::StackFramePPC;
using google_breakpad::StackFrameX86;
using google_breakpad::StackFrameSPARC;

#if defined(__i386__)
#include "processor/stackwalker_x86.h"
using google_breakpad::StackwalkerX86;
#elif defined(__ppc__)
#include "processor/stackwalker_ppc.h"
using google_breakpad::StackwalkerPPC;
#elif defined(__sparc__)
#include "processor/stackwalker_sparc.h"
using google_breakpad::StackwalkerSPARC;
#endif  // __i386__ || __ppc__ || __sparc__

#define RECURSION_DEPTH 100


// A simple MemoryRegion subclass that provides direct access to this
// process' memory space by pointer.
class SelfMemoryRegion : public MemoryRegion {
 public:
  virtual uint64_t GetBase() const { return 0; }
  virtual uint32_t GetSize() const { return 0xffffffff; }

  bool GetMemoryAtAddress(uint64_t address, uint8_t*  value) const {
      return GetMemoryAtAddressInternal(address, value); }
  bool GetMemoryAtAddress(uint64_t address, uint16_t* value) const {
      return GetMemoryAtAddressInternal(address, value); }
  bool GetMemoryAtAddress(uint64_t address, uint32_t* value) const {
      return GetMemoryAtAddressInternal(address, value); }
  bool GetMemoryAtAddress(uint64_t address, uint64_t* value) const {
      return GetMemoryAtAddressInternal(address, value); }
  void Print() const {
    assert(false);
  }

 private:
  template<typename T> bool GetMemoryAtAddressInternal(uint64_t address,
                                                       T*        value) {
    // Without knowing what addresses are actually mapped, just assume that
    // everything low is not mapped.  This helps the stackwalker catch the
    // end of a stack when it tries to dereference a null or low pointer
    // in an attempt to find the caller frame.  Other unmapped accesses will
    // cause the program to crash, but that would properly be a test failure.
    if (address < 0x100)
      return false;

    uint8_t* memory = 0;
    *value = *reinterpret_cast<const T*>(&memory[address]);
    return true;
  }
};


#if defined(__GNUC__)


#if defined(__i386__)

// GetEBP returns the current value of the %ebp register.  Because it's
// implemented as a function, %ebp itself contains GetEBP's frame pointer
// and not the caller's frame pointer.  Dereference %ebp to obtain the
// caller's frame pointer, which the compiler-generated preamble stored
// on the stack (provided frame pointers are not being omitted.)  Because
// this function depends on the compiler-generated preamble, inlining is
// disabled.
static uint32_t GetEBP() __attribute__((noinline));
static uint32_t GetEBP() {
  uint32_t ebp;
  __asm__ __volatile__(
    "movl (%%ebp), %0"
    : "=a" (ebp)
  );
  return ebp;
}


// The caller's %esp is 8 higher than the value of %ebp in this function,
// assuming that it's not inlined and that the standard prolog is used.
// The CALL instruction places a 4-byte return address on the stack above
// the caller's %esp, and this function's prolog will save the caller's %ebp
// on the stack as well, for another 4 bytes, before storing %esp in %ebp.
static uint32_t GetESP() __attribute__((noinline));
static uint32_t GetESP() {
  uint32_t ebp;
  __asm__ __volatile__(
    "movl %%ebp, %0"
    : "=a" (ebp)
  );
  return ebp + 8;
}


// GetEIP returns the instruction pointer identifying the next instruction
// to execute after GetEIP returns.  It obtains this information from the
// stack, where it was placed by the call instruction that called GetEIP.
// This function depends on frame pointers not being omitted.  It is possible
// to write a pure asm version of this routine that has no compiler-generated
// preamble and uses %esp instead of %ebp; that would function in the
// absence of frame pointers.  However, the simpler approach is used here
// because GetEBP and stackwalking necessarily depends on access to frame
// pointers.  Because this function depends on a call instruction and the
// compiler-generated preamble, inlining is disabled.
static uint32_t GetEIP() __attribute__((noinline));
static uint32_t GetEIP() {
  uint32_t eip;
  __asm__ __volatile__(
    "movl 4(%%ebp), %0"
    : "=a" (eip)
  );
  return eip;
}


#elif defined(__ppc__)


// GetSP returns the current value of the %r1 register, which by convention,
// is the stack pointer on ppc.  Because it's implemented as a function,
// %r1 itself contains GetSP's own stack pointer and not the caller's stack
// pointer.  Dereference %r1 to obtain the caller's stack pointer, which the
// compiler-generated prolog stored on the stack.  Because this function
// depends on the compiler-generated prolog, inlining is disabled.
static uint32_t GetSP() __attribute__((noinline));
static uint32_t GetSP() {
  uint32_t sp;
  __asm__ __volatile__(
    "lwz %0, 0(r1)"
    : "=r" (sp)
  );
  return sp;
}


// GetPC returns the program counter identifying the next instruction to
// execute after GetPC returns.  It obtains this information from the
// link register, where it was placed by the branch instruction that called
// GetPC.  Because this function depends on the caller's use of a branch
// instruction, inlining is disabled.
static uint32_t GetPC() __attribute__((noinline));
static uint32_t GetPC() {
  uint32_t lr;
  __asm__ __volatile__(
    "mflr %0"
    : "=r" (lr)
  );
  return lr;
}


#elif defined(__sparc__)


// GetSP returns the current value of the %sp/%o6/%g_r[14] register, which 
// by convention, is the stack pointer on sparc.  Because it's implemented
// as a function, %sp itself contains GetSP's own stack pointer and not 
// the caller's stack pointer.  Dereference  to obtain the caller's stack 
// pointer, which the compiler-generated prolog stored on the stack.
// Because this function depends on the compiler-generated prolog, inlining
// is disabled.
static uint32_t GetSP() __attribute__((noinline));
static uint32_t GetSP() {
  uint32_t sp;
  __asm__ __volatile__(
    "mov %%fp, %0"
    : "=r" (sp)
  );
  return sp;
}

// GetFP returns the current value of the %fp register.  Because it's
// implemented as a function, %fp itself contains GetFP's frame pointer
// and not the caller's frame pointer.  Dereference %fp to obtain the
// caller's frame pointer, which the compiler-generated preamble stored
// on the stack (provided frame pointers are not being omitted.)  Because
// this function depends on the compiler-generated preamble, inlining is
// disabled.
static uint32_t GetFP() __attribute__((noinline));
static uint32_t GetFP() {
  uint32_t fp;
  __asm__ __volatile__(
    "ld [%%fp+56], %0"
    : "=r" (fp)
  );
  return fp;
}

// GetPC returns the program counter identifying the next instruction to
// execute after GetPC returns.  It obtains this information from the
// link register, where it was placed by the branch instruction that called
// GetPC.  Because this function depends on the caller's use of a branch
// instruction, inlining is disabled.
static uint32_t GetPC() __attribute__((noinline));
static uint32_t GetPC() {
  uint32_t pc;
  __asm__ __volatile__(
    "mov %%i7, %0"
    : "=r" (pc)
  );
  return pc + 8;
}

#endif  // __i386__ || __ppc__ || __sparc__

#elif defined(__SUNPRO_CC)

#if defined(__i386__)
extern "C" {
extern uint32_t GetEIP();
extern uint32_t GetEBP();
extern uint32_t GetESP();
}
#elif defined(__sparc__)
extern "C" {
extern uint32_t GetPC();
extern uint32_t GetFP();
extern uint32_t GetSP();
}
#endif // __i386__ || __sparc__

#endif // __GNUC__ || __SUNPRO_CC

// CountCallerFrames returns the number of stack frames beneath the function
// that called CountCallerFrames.  Because this function's return value
// is dependent on the size of the stack beneath it, inlining is disabled,
// and any function that calls this should not be inlined either.
#if defined(__GNUC__)
static unsigned int CountCallerFrames() __attribute__((noinline));
#elif defined(__SUNPRO_CC)
static unsigned int CountCallerFrames();
#endif
static unsigned int CountCallerFrames() {
  SelfMemoryRegion memory;
  BasicSourceLineResolver resolver;

#if defined(__i386__)
  MDRawContextX86 context = MDRawContextX86();
  context.eip = GetEIP();
  context.ebp = GetEBP();
  context.esp = GetESP();

  StackwalkerX86 stackwalker = StackwalkerX86(NULL, &context, &memory, NULL,
                                              NULL, &resolver);
#elif defined(__ppc__)
  MDRawContextPPC context = MDRawContextPPC();
  context.srr0 = GetPC();
  context.gpr[1] = GetSP();

  StackwalkerPPC stackwalker = StackwalkerPPC(NULL, &context, &memory, NULL,
                                              NULL, &resolver);
#elif defined(__sparc__)
  MDRawContextSPARC context = MDRawContextSPARC();
  context.pc = GetPC();
  context.g_r[14] = GetSP();
  context.g_r[30] = GetFP();

  StackwalkerSPARC stackwalker = StackwalkerSPARC(NULL, &context, &memory,
                                                  NULL, NULL, &resolver);
#endif  // __i386__ || __ppc__ || __sparc__

  CallStack stack;
  vector<const CodeModule*> modules_without_symbols;
  stackwalker.Walk(&stack, &modules_without_symbols);

#ifdef PRINT_STACKS
  printf("\n");
  for (unsigned int frame_index = 0;
      frame_index < stack.frames()->size();
      ++frame_index) {
    StackFrame *frame = stack.frames()->at(frame_index);
    printf("frame %-3d  instruction = 0x%08" PRIx64,
           frame_index, frame->instruction);
#if defined(__i386__)
    StackFrameX86 *frame_x86 = reinterpret_cast<StackFrameX86*>(frame);
    printf("  esp = 0x%08x  ebp = 0x%08x\n",
           frame_x86->context.esp, frame_x86->context.ebp);
#elif defined(__ppc__)
    StackFramePPC *frame_ppc = reinterpret_cast<StackFramePPC*>(frame);
    printf("  gpr[1] = 0x%08x\n", frame_ppc->context.gpr[1]);
#elif defined(__sparc__)
    StackFrameSPARC *frame_sparc = reinterpret_cast<StackFrameSPARC*>(frame);
    printf("  sp = 0x%08x  fp = 0x%08x\n",
           frame_sparc->context.g_r[14], frame_sparc->context.g_r[30]);
#endif  // __i386__ || __ppc__ || __sparc__
  }
#endif  // PRINT_STACKS

  // Subtract 1 because the caller wants the number of frames beneath
  // itself.  Because the caller called us, subract two for our frame and its
  // frame, which are included in stack.size().
  return stack.frames()->size() - 2;
}


// Recursor verifies that the number stack frames beneath itself is one more
// than the number of stack frames beneath its parent.  When depth frames
// have been reached, Recursor stops checking and returns success.  If the
// frame count check fails at any depth, Recursor will stop and return false.
// Because this calls CountCallerFrames, inlining is disabled.
#if defined(__GNUC__)
static bool Recursor(unsigned int depth, unsigned int parent_callers)
    __attribute__((noinline));
#elif defined(__SUNPRO_CC)
static bool Recursor(unsigned int depth, unsigned int parent_callers);
#endif
static bool Recursor(unsigned int depth, unsigned int parent_callers) {
  unsigned int callers = CountCallerFrames();
  if (callers != parent_callers + 1)
    return false;

  if (depth)
    return Recursor(depth - 1, callers);

  // depth == 0
  return true;
}


// Because this calls CountCallerFrames, inlining is disabled - but because
// it's main (and nobody calls it other than the entry point), it wouldn't
// be inlined anyway.
#if defined(__GNUC__)
int main(int argc, char** argv) __attribute__((noinline));
#elif defined(__SUNPRO_CC)
int main(int argc, char** argv);
#endif
int main(int argc, char** argv) {
  BPLOG_INIT(&argc, &argv);

  return Recursor(RECURSION_DEPTH, CountCallerFrames()) ? 0 : 1;
}


#else
// Not i386 or ppc or sparc?  We can only test stacks we know how to walk.


int main(int argc, char **argv) {
  BPLOG_INIT(&argc, &argv);

  // "make check" interprets an exit status of 77 to mean that the test is
  // not supported.
  BPLOG(ERROR) << "Selftest not supported here";
  return 77;
}


#endif  // (__GNUC__ || __SUNPRO_CC) && (__i386__ || __ppc__ || __sparc__)
