// Copyright (c) 2012, Google Inc.
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

#if defined(__x86_64__)
#include <asm/sigcontext.h>
#endif

#include <sys/ucontext.h>

#include "breakpad_googletest_includes.h"
#include "common/android/ucontext_constants.h"

template <int left, int right>
struct CompileAssertEquals {
  // a compilation error here indicates left and right are not equal.
  char left_too_large[right - left];
  // a compilation error here indicates left and right are not equal.
  char right_too_large[left - right];
};

#define COMPILE_ASSERT_EQ(left, right, tag) \
  CompileAssertEquals<left, right> tag;

TEST(AndroidUContext, GRegsOffset) {
#if defined(__arm__)
  // There is no gregs[] array on ARM, so compare to the offset of
  // first register fields, since they're stored in order.
  ASSERT_EQ(static_cast<size_t>(MCONTEXT_GREGS_OFFSET),
            offsetof(ucontext_t,uc_mcontext.arm_r0));
#elif defined(__aarch64__)
  // There is no gregs[] array on ARM, so compare to the offset of
  // first register fields, since they're stored in order.
  ASSERT_EQ(static_cast<size_t>(MCONTEXT_GREGS_OFFSET),
            offsetof(ucontext_t,uc_mcontext.regs[0]));
  ASSERT_EQ(static_cast<size_t>(MCONTEXT_SP_OFFSET),
            offsetof(ucontext_t,uc_mcontext.sp));
  ASSERT_EQ(static_cast<size_t>(MCONTEXT_PC_OFFSET),
            offsetof(ucontext_t,uc_mcontext.pc));
  ASSERT_EQ(static_cast<size_t>(MCONTEXT_PSTATE_OFFSET),
            offsetof(ucontext_t,uc_mcontext.pstate));
  ASSERT_EQ(static_cast<size_t>(MCONTEXT_EXTENSION_OFFSET),
            offsetof(ucontext_t,uc_mcontext.__reserved));
#elif defined(__i386__)
  ASSERT_EQ(static_cast<size_t>(MCONTEXT_GREGS_OFFSET),
            offsetof(ucontext_t,uc_mcontext.gregs));
#define CHECK_REG(x) \
  ASSERT_EQ(static_cast<size_t>(MCONTEXT_##x##_OFFSET),         \
            offsetof(ucontext_t,uc_mcontext.gregs[REG_##x]))
  CHECK_REG(GS);
  CHECK_REG(FS);
  CHECK_REG(ES);
  CHECK_REG(DS);
  CHECK_REG(EDI);
  CHECK_REG(ESI);
  CHECK_REG(EBP);
  CHECK_REG(ESP);
  CHECK_REG(EBX);
  CHECK_REG(EDX);
  CHECK_REG(ECX);
  CHECK_REG(EAX);
  CHECK_REG(TRAPNO);
  CHECK_REG(ERR);
  CHECK_REG(EIP);
  CHECK_REG(CS);
  CHECK_REG(EFL);
  CHECK_REG(UESP);
  CHECK_REG(SS);

  ASSERT_EQ(static_cast<size_t>(UCONTEXT_FPREGS_OFFSET),
            offsetof(ucontext_t,uc_mcontext.fpregs));

  ASSERT_EQ(static_cast<size_t>(UCONTEXT_FPREGS_MEM_OFFSET),
            offsetof(ucontext_t,__fpregs_mem));
#elif defined(__mips__)
  ASSERT_EQ(static_cast<size_t>(MCONTEXT_GREGS_OFFSET),
            offsetof(ucontext_t,uc_mcontext.gregs));

  // PC for mips is not part of gregs.
  ASSERT_EQ(static_cast<size_t>(MCONTEXT_PC_OFFSET),
            offsetof(ucontext_t,uc_mcontext.pc));

  ASSERT_EQ(static_cast<size_t>(MCONTEXT_FPREGS_OFFSET),
            offsetof(ucontext_t,uc_mcontext.fpregs));

  ASSERT_EQ(static_cast<size_t>(MCONTEXT_FPC_CSR),
            offsetof(ucontext_t,uc_mcontext.fpc_csr));
#elif defined(__x86_64__)

  COMPILE_ASSERT_EQ(static_cast<size_t>(MCONTEXT_GREGS_OFFSET),
                    offsetof(ucontext_t,uc_mcontext.gregs),
                    mcontext_gregs_offset);
#define CHECK_REG(x) \
  COMPILE_ASSERT_EQ(static_cast<size_t>(MCONTEXT_GREGS_##x), \
                    offsetof(ucontext_t,uc_mcontext.gregs[REG_##x]), reg_##x)
  CHECK_REG(R8);
  CHECK_REG(R9);
  CHECK_REG(R10);
  CHECK_REG(R11);
  CHECK_REG(R12);
  CHECK_REG(R13);
  CHECK_REG(R14);
  CHECK_REG(R15);
  CHECK_REG(RDI);
  CHECK_REG(RSI);
  CHECK_REG(RBP);
  CHECK_REG(RBX);
  CHECK_REG(RDX);
  CHECK_REG(RAX);
  CHECK_REG(RCX);
  CHECK_REG(RSP);
  CHECK_REG(RIP);

  // sigcontext is an analog to mcontext_t. The layout should be the same.
  COMPILE_ASSERT_EQ(offsetof(mcontext_t,fpregs),
                    offsetof(sigcontext,fpstate), sigcontext_fpstate);
  // Check that _fpstate from asm/sigcontext.h is essentially the same
  // as _libc_fpstate.
  COMPILE_ASSERT_EQ(sizeof(_libc_fpstate), sizeof(_fpstate),
                    sigcontext_fpstate_size);
  COMPILE_ASSERT_EQ(offsetof(_libc_fpstate,cwd),offsetof(_fpstate,cwd),
                    sigcontext_fpstate_cwd);
  COMPILE_ASSERT_EQ(offsetof(_libc_fpstate,swd),offsetof(_fpstate,swd),
                    sigcontext_fpstate_swd);
  COMPILE_ASSERT_EQ(offsetof(_libc_fpstate,ftw),offsetof(_fpstate,twd),
                    sigcontext_fpstate_twd);
  COMPILE_ASSERT_EQ(offsetof(_libc_fpstate,fop),offsetof(_fpstate,fop),
                    sigcontext_fpstate_fop);
  COMPILE_ASSERT_EQ(offsetof(_libc_fpstate,rip),offsetof(_fpstate,rip),
                    sigcontext_fpstate_rip);
  COMPILE_ASSERT_EQ(offsetof(_libc_fpstate,rdp),offsetof(_fpstate,rdp),
                    sigcontext_fpstate_rdp);
  COMPILE_ASSERT_EQ(offsetof(_libc_fpstate,mxcsr),offsetof(_fpstate,mxcsr),
                    sigcontext_fpstate_mxcsr);
  COMPILE_ASSERT_EQ(offsetof(_libc_fpstate,mxcr_mask),
                    offsetof(_fpstate,mxcsr_mask),
                    sigcontext_fpstate_mxcsr_mask);
  COMPILE_ASSERT_EQ(offsetof(_libc_fpstate,_st), offsetof(_fpstate,st_space),
                    sigcontext_fpstate_stspace);
  COMPILE_ASSERT_EQ(offsetof(_libc_fpstate,_xmm), offsetof(_fpstate,xmm_space),
                    sigcontext_fpstate_xmm_space);

  COMPILE_ASSERT_EQ(MCONTEXT_FPREGS_PTR,
                    offsetof(ucontext_t,uc_mcontext.fpregs),
                    mcontext_fpregs_ptr);
  COMPILE_ASSERT_EQ(MCONTEXT_FPREGS_MEM, offsetof(ucontext_t,__fpregs_mem),
                    mcontext_fpregs_mem);
  COMPILE_ASSERT_EQ(FPREGS_OFFSET_MXCSR, offsetof(_libc_fpstate,mxcsr),
                    fpregs_offset_mxcsr);
  COMPILE_ASSERT_EQ(UCONTEXT_SIGMASK_OFFSET, offsetof(ucontext_t, uc_sigmask),
                    ucontext_sigmask);
#else
  ASSERT_EQ(static_cast<size_t>(MCONTEXT_GREGS_OFFSET),
            offsetof(ucontext_t,uc_mcontext.gregs));
#endif
}

TEST(AndroidUContext, SigmakOffset) {
  ASSERT_EQ(static_cast<size_t>(UCONTEXT_SIGMASK_OFFSET),
            offsetof(ucontext_t,uc_sigmask));
}
