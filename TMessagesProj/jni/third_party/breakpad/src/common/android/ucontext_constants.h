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

// This header can be included either from a C, C++ or Assembly file.
// Its purpose is to contain constants that must match the offsets of
// various fields in ucontext_t.
//
// They should match the definitions from
// src/common/android/include/sys/ucontext.h
//
// Used by src/common/android/breakpad_getcontext.S
// Tested by src/common/android/testing/breakpad_getcontext_unittest.cc

#ifndef GOOGLEBREAKPAD_COMMON_ANDROID_UCONTEXT_CONSTANTS_H
#define GOOGLEBREAKPAD_COMMON_ANDROID_UCONTEXT_CONSTANTS_H

#if defined(__arm__)

#define  MCONTEXT_GREGS_OFFSET     32
#define  UCONTEXT_SIGMASK_OFFSET   104

#elif defined(__aarch64__)

#define  UCONTEXT_SIGMASK_OFFSET     40

#define  MCONTEXT_GREGS_OFFSET       184
#define  MCONTEXT_SP_OFFSET          432
#define  MCONTEXT_PC_OFFSET          440
#define  MCONTEXT_PSTATE_OFFSET      448
#define  MCONTEXT_EXTENSION_OFFSET   464

#define  FPSIMD_MAGIC                0x46508001

#define  FPSIMD_CONTEXT_MAGIC_OFFSET 0
#define  FPSIMD_CONTEXT_SIZE_OFFSET  4
#define  FPSIMD_CONTEXT_FPSR_OFFSET  8
#define  FPSIMD_CONTEXT_FPCR_OFFSET  12
#define  FPSIMD_CONTEXT_VREGS_OFFSET 16
#define  FPSIMD_CONTEXT_SIZE         528

#define  REGISTER_SIZE               8
#define  SIMD_REGISTER_SIZE          16

#elif defined(__i386__)

#define  MCONTEXT_GREGS_OFFSET     20
#define  MCONTEXT_GS_OFFSET        (MCONTEXT_GREGS_OFFSET + 0*4)
#define  MCONTEXT_FS_OFFSET        (MCONTEXT_GREGS_OFFSET + 1*4)
#define  MCONTEXT_ES_OFFSET        (MCONTEXT_GREGS_OFFSET + 2*4)
#define  MCONTEXT_DS_OFFSET        (MCONTEXT_GREGS_OFFSET + 3*4)
#define  MCONTEXT_EDI_OFFSET       (MCONTEXT_GREGS_OFFSET + 4*4)
#define  MCONTEXT_ESI_OFFSET       (MCONTEXT_GREGS_OFFSET + 5*4)
#define  MCONTEXT_EBP_OFFSET       (MCONTEXT_GREGS_OFFSET + 6*4)
#define  MCONTEXT_ESP_OFFSET       (MCONTEXT_GREGS_OFFSET + 7*4)
#define  MCONTEXT_EBX_OFFSET       (MCONTEXT_GREGS_OFFSET + 8*4)
#define  MCONTEXT_EDX_OFFSET       (MCONTEXT_GREGS_OFFSET + 9*4)
#define  MCONTEXT_ECX_OFFSET       (MCONTEXT_GREGS_OFFSET + 10*4)
#define  MCONTEXT_EAX_OFFSET       (MCONTEXT_GREGS_OFFSET + 11*4)
#define  MCONTEXT_TRAPNO_OFFSET    (MCONTEXT_GREGS_OFFSET + 12*4)
#define  MCONTEXT_ERR_OFFSET       (MCONTEXT_GREGS_OFFSET + 13*4)
#define  MCONTEXT_EIP_OFFSET       (MCONTEXT_GREGS_OFFSET + 14*4)
#define  MCONTEXT_CS_OFFSET        (MCONTEXT_GREGS_OFFSET + 15*4)
#define  MCONTEXT_EFL_OFFSET       (MCONTEXT_GREGS_OFFSET + 16*4)
#define  MCONTEXT_UESP_OFFSET      (MCONTEXT_GREGS_OFFSET + 17*4)
#define  MCONTEXT_SS_OFFSET        (MCONTEXT_GREGS_OFFSET + 18*4)

#define  UCONTEXT_SIGMASK_OFFSET   108

#define  UCONTEXT_FPREGS_OFFSET       96
#define  UCONTEXT_FPREGS_MEM_OFFSET   116

#elif defined(__mips__)

#if _MIPS_SIM == _ABIO32
#define  MCONTEXT_PC_OFFSET        32
#define  MCONTEXT_GREGS_OFFSET     40
#define  MCONTEXT_FPREGS_OFFSET    296
#define  MCONTEXT_FPC_CSR          556
#define  UCONTEXT_SIGMASK_OFFSET   616
#else
#define  MCONTEXT_GREGS_OFFSET     40
#define  MCONTEXT_FPREGS_OFFSET    296
#define  MCONTEXT_PC_OFFSET        616
#define  MCONTEXT_FPC_CSR          624
#define  UCONTEXT_SIGMASK_OFFSET   640
#endif

#elif defined(__x86_64__)

#define MCONTEXT_GREGS_OFFSET     40
#define UCONTEXT_SIGMASK_OFFSET   296

#define MCONTEXT_GREGS_R8    40
#define MCONTEXT_GREGS_R9    48
#define MCONTEXT_GREGS_R10   56
#define MCONTEXT_GREGS_R11   64
#define MCONTEXT_GREGS_R12   72
#define MCONTEXT_GREGS_R13   80
#define MCONTEXT_GREGS_R14   88
#define MCONTEXT_GREGS_R15   96
#define MCONTEXT_GREGS_RDI   104
#define MCONTEXT_GREGS_RSI   112
#define MCONTEXT_GREGS_RBP   120
#define MCONTEXT_GREGS_RBX   128
#define MCONTEXT_GREGS_RDX   136
#define MCONTEXT_GREGS_RAX   144
#define MCONTEXT_GREGS_RCX   152
#define MCONTEXT_GREGS_RSP   160
#define MCONTEXT_GREGS_RIP   168
#define MCONTEXT_FPREGS_PTR  224
#define MCONTEXT_FPREGS_MEM  304
#define FPREGS_OFFSET_MXCSR  24

#else
#error "This header has not been ported for your CPU"
#endif

#endif  // GOOGLEBREAKPAD_COMMON_ANDROID_UCONTEXT_CONSTANTS_H
