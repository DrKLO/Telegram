// Copyright (c) 2007, Google Inc.
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
//
// Author: Craig Silverstein
//
// Produce stack trace.  I'm guessing (hoping!) the code is much like
// for x86.  For apple machines, at least, it seems to be; see
//    http://developer.apple.com/documentation/mac/runtimehtml/RTArch-59.html
//    http://www.linux-foundation.org/spec/ELF/ppc64/PPC-elf64abi-1.9.html#STACK
// Linux has similar code: http://patchwork.ozlabs.org/linuxppc/patch?id=8882

#include <stdio.h>
#include <stdint.h>   // for uintptr_t
#include "stacktrace.h"

_START_GOOGLE_NAMESPACE_

// Given a pointer to a stack frame, locate and return the calling
// stackframe, or return NULL if no stackframe can be found. Perform sanity
// checks (the strictness of which is controlled by the boolean parameter
// "STRICT_UNWINDING") to reduce the chance that a bad pointer is returned.
template<bool STRICT_UNWINDING>
static void **NextStackFrame(void **old_sp) {
  void **new_sp = (void **) *old_sp;

  // Check that the transition from frame pointer old_sp to frame
  // pointer new_sp isn't clearly bogus
  if (STRICT_UNWINDING) {
    // With the stack growing downwards, older stack frame must be
    // at a greater address that the current one.
    if (new_sp <= old_sp) return NULL;
    // Assume stack frames larger than 100,000 bytes are bogus.
    if ((uintptr_t)new_sp - (uintptr_t)old_sp > 100000) return NULL;
  } else {
    // In the non-strict mode, allow discontiguous stack frames.
    // (alternate-signal-stacks for example).
    if (new_sp == old_sp) return NULL;
    // And allow frames upto about 1MB.
    if ((new_sp > old_sp)
        && ((uintptr_t)new_sp - (uintptr_t)old_sp > 1000000)) return NULL;
  }
  if ((uintptr_t)new_sp & (sizeof(void *) - 1)) return NULL;
  return new_sp;
}

// This ensures that GetStackTrace stes up the Link Register properly.
void StacktracePowerPCDummyFunction() __attribute__((noinline));
void StacktracePowerPCDummyFunction() { __asm__ volatile(""); }

// If you change this function, also change GetStackFrames below.
int GetStackTrace(void** result, int max_depth, int skip_count) {
  void **sp;
  // Apple OS X uses an old version of gnu as -- both Darwin 7.9.0 (Panther)
  // and Darwin 8.8.1 (Tiger) use as 1.38.  This means we have to use a
  // different asm syntax.  I don't know quite the best way to discriminate
  // systems using the old as from the new one; I've gone with __APPLE__.
#ifdef __APPLE__
  __asm__ volatile ("mr %0,r1" : "=r" (sp));
#else
  __asm__ volatile ("mr %0,1" : "=r" (sp));
#endif

  // On PowerPC, the "Link Register" or "Link Record" (LR), is a stack
  // entry that holds the return address of the subroutine call (what
  // instruction we run after our function finishes).  This is the
  // same as the stack-pointer of our parent routine, which is what we
  // want here.  While the compiler will always(?) set up LR for
  // subroutine calls, it may not for leaf functions (such as this one).
  // This routine forces the compiler (at least gcc) to push it anyway.
  StacktracePowerPCDummyFunction();

  // The LR save area is used by the callee, so the top entry is bogus.
  skip_count++;

  int n = 0;
  while (sp && n < max_depth) {
    if (skip_count > 0) {
      skip_count--;
    } else {
      // PowerPC has 3 main ABIs, which say where in the stack the
      // Link Register is.  For DARWIN and AIX (used by apple and
      // linux ppc64), it's in sp[2].  For SYSV (used by linux ppc),
      // it's in sp[1].
#if defined(_CALL_AIX) || defined(_CALL_DARWIN)
      result[n++] = *(sp+2);
#elif defined(_CALL_SYSV)
      result[n++] = *(sp+1);
#elif defined(__APPLE__) || (defined(__linux) && defined(__PPC64__))
      // This check is in case the compiler doesn't define _CALL_AIX/etc.
      result[n++] = *(sp+2);
#elif defined(__linux)
      // This check is in case the compiler doesn't define _CALL_SYSV.
      result[n++] = *(sp+1);
#else
#error Need to specify the PPC ABI for your archiecture.
#endif
    }
    // Use strict unwinding rules.
    sp = NextStackFrame<true>(sp);
  }
  return n;
}

_END_GOOGLE_NAMESPACE_
