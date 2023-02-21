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

// dump_context.cc: A (mini/micro)dump context.
//
// See dump_context.h for documentation.

#include "google_breakpad/processor/dump_context.h"

#include <assert.h>
#include <stdio.h>

#ifdef _WIN32
#include <io.h>
#define PRIx64 "llx"
#define PRIx32 "lx"
#define snprintf _snprintf
#else  // _WIN32
#include <unistd.h>
#endif  // _WIN32

#include "processor/logging.h"

namespace google_breakpad {

DumpContext::DumpContext() : context_(),
                             context_flags_(0) { }

DumpContext::~DumpContext() {
  FreeContext();
}

uint32_t DumpContext::GetContextCPU() const {
  if (!valid_) {
    // Don't log a message, GetContextCPU can be legitimately called with
    // valid_ false by FreeContext, which is called by Read.
    return 0;
  }

  return context_flags_ & MD_CONTEXT_CPU_MASK;
}

uint32_t DumpContext::GetContextFlags() const {
  return context_flags_;
}

const MDRawContextX86* DumpContext::GetContextX86() const {
  if (GetContextCPU() != MD_CONTEXT_X86) {
    BPLOG(ERROR) << "DumpContext cannot get x86 context";
    return NULL;
  }

  return context_.x86;
}

const MDRawContextPPC* DumpContext::GetContextPPC() const {
  if (GetContextCPU() != MD_CONTEXT_PPC) {
    BPLOG(ERROR) << "DumpContext cannot get ppc context";
    return NULL;
  }

  return context_.ppc;
}

const MDRawContextPPC64* DumpContext::GetContextPPC64() const {
  if (GetContextCPU() != MD_CONTEXT_PPC64) {
    BPLOG(ERROR) << "DumpContext cannot get ppc64 context";
    return NULL;
  }

  return context_.ppc64;
}

const MDRawContextAMD64* DumpContext::GetContextAMD64() const {
  if (GetContextCPU() != MD_CONTEXT_AMD64) {
    BPLOG(ERROR) << "DumpContext cannot get amd64 context";
    return NULL;
  }

  return context_.amd64;
}

const MDRawContextSPARC* DumpContext::GetContextSPARC() const {
  if (GetContextCPU() != MD_CONTEXT_SPARC) {
    BPLOG(ERROR) << "DumpContext cannot get sparc context";
    return NULL;
  }

  return context_.ctx_sparc;
}

const MDRawContextARM* DumpContext::GetContextARM() const {
  if (GetContextCPU() != MD_CONTEXT_ARM) {
    BPLOG(ERROR) << "DumpContext cannot get arm context";
    return NULL;
  }

  return context_.arm;
}

const MDRawContextARM64* DumpContext::GetContextARM64() const {
  if (GetContextCPU() != MD_CONTEXT_ARM64) {
    BPLOG(ERROR) << "DumpContext cannot get arm64 context";
    return NULL;
  }

  return context_.arm64;
}

const MDRawContextMIPS* DumpContext::GetContextMIPS() const {
  if (GetContextCPU() != MD_CONTEXT_MIPS) {
    BPLOG(ERROR) << "DumpContext cannot get MIPS context";
    return NULL;
  }

  return context_.ctx_mips;
}

bool DumpContext::GetInstructionPointer(uint64_t* ip) const {
  BPLOG_IF(ERROR, !ip) << "DumpContext::GetInstructionPointer requires |ip|";
  assert(ip);
  *ip = 0;

  if (!valid_) {
    BPLOG(ERROR) << "Invalid DumpContext for GetInstructionPointer";
    return false;
  }

  switch (GetContextCPU()) {
  case MD_CONTEXT_AMD64:
    *ip = GetContextAMD64()->rip;
    break;
  case MD_CONTEXT_ARM:
    *ip = GetContextARM()->iregs[MD_CONTEXT_ARM_REG_PC];
    break;
  case MD_CONTEXT_ARM64:
    *ip = GetContextARM64()->iregs[MD_CONTEXT_ARM64_REG_PC];
    break;
  case MD_CONTEXT_PPC:
    *ip = GetContextPPC()->srr0;
    break;
  case MD_CONTEXT_PPC64:
    *ip = GetContextPPC64()->srr0;
    break;
  case MD_CONTEXT_SPARC:
    *ip = GetContextSPARC()->pc;
    break;
  case MD_CONTEXT_X86:
    *ip = GetContextX86()->eip;
    break;
  case MD_CONTEXT_MIPS:
    *ip = GetContextMIPS()->epc;
    break;
  default:
    // This should never happen.
    BPLOG(ERROR) << "Unknown CPU architecture in GetInstructionPointer";
    return false;
  }
  return true;
}

void DumpContext::SetContextFlags(uint32_t context_flags) {
  context_flags_ = context_flags;
}

void DumpContext::SetContextX86(MDRawContextX86* x86) {
  context_.x86 = x86;
}

void DumpContext::SetContextPPC(MDRawContextPPC* ppc) {
  context_.ppc = ppc;
}

void DumpContext::SetContextPPC64(MDRawContextPPC64* ppc64) {
  context_.ppc64 = ppc64;
}

void DumpContext::SetContextAMD64(MDRawContextAMD64* amd64) {
  context_.amd64 = amd64;
}

void DumpContext::SetContextSPARC(MDRawContextSPARC* ctx_sparc) {
  context_.ctx_sparc = ctx_sparc;
}

void DumpContext::SetContextARM(MDRawContextARM* arm) {
  context_.arm = arm;
}

void DumpContext::SetContextARM64(MDRawContextARM64* arm64) {
  context_.arm64 = arm64;
}

void DumpContext::SetContextMIPS(MDRawContextMIPS* ctx_mips) {
  context_.ctx_mips = ctx_mips;
}

void DumpContext::FreeContext() {
  switch (GetContextCPU()) {
    case MD_CONTEXT_X86:
      delete context_.x86;
      break;

    case MD_CONTEXT_PPC:
      delete context_.ppc;
      break;

    case MD_CONTEXT_PPC64:
      delete context_.ppc64;
      break;

    case MD_CONTEXT_AMD64:
      delete context_.amd64;
      break;

    case MD_CONTEXT_SPARC:
      delete context_.ctx_sparc;
      break;

    case MD_CONTEXT_ARM:
      delete context_.arm;
      break;

    case MD_CONTEXT_ARM64:
      delete context_.arm64;
      break;

    case MD_CONTEXT_MIPS:
      delete context_.ctx_mips;
      break;

    default:
      // There is no context record (valid_ is false) or there's a
      // context record for an unknown CPU (shouldn't happen, only known
      // records are stored by Read).
      break;
  }

  context_flags_ = 0;
  context_.base = NULL;
}

void DumpContext::Print() {
  if (!valid_) {
    BPLOG(ERROR) << "DumpContext cannot print invalid data";
    return;
  }

  switch (GetContextCPU()) {
    case MD_CONTEXT_X86: {
      const MDRawContextX86* context_x86 = GetContextX86();
      printf("MDRawContextX86\n");
      printf("  context_flags                = 0x%x\n",
             context_x86->context_flags);
      printf("  dr0                          = 0x%x\n", context_x86->dr0);
      printf("  dr1                          = 0x%x\n", context_x86->dr1);
      printf("  dr2                          = 0x%x\n", context_x86->dr2);
      printf("  dr3                          = 0x%x\n", context_x86->dr3);
      printf("  dr6                          = 0x%x\n", context_x86->dr6);
      printf("  dr7                          = 0x%x\n", context_x86->dr7);
      printf("  float_save.control_word      = 0x%x\n",
             context_x86->float_save.control_word);
      printf("  float_save.status_word       = 0x%x\n",
             context_x86->float_save.status_word);
      printf("  float_save.tag_word          = 0x%x\n",
             context_x86->float_save.tag_word);
      printf("  float_save.error_offset      = 0x%x\n",
             context_x86->float_save.error_offset);
      printf("  float_save.error_selector    = 0x%x\n",
             context_x86->float_save.error_selector);
      printf("  float_save.data_offset       = 0x%x\n",
             context_x86->float_save.data_offset);
      printf("  float_save.data_selector     = 0x%x\n",
             context_x86->float_save.data_selector);
      printf("  float_save.register_area[%2d] = 0x",
             MD_FLOATINGSAVEAREA_X86_REGISTERAREA_SIZE);
      for (unsigned int register_index = 0;
           register_index < MD_FLOATINGSAVEAREA_X86_REGISTERAREA_SIZE;
           ++register_index) {
        printf("%02x", context_x86->float_save.register_area[register_index]);
      }
      printf("\n");
      printf("  float_save.cr0_npx_state     = 0x%x\n",
             context_x86->float_save.cr0_npx_state);
      printf("  gs                           = 0x%x\n", context_x86->gs);
      printf("  fs                           = 0x%x\n", context_x86->fs);
      printf("  es                           = 0x%x\n", context_x86->es);
      printf("  ds                           = 0x%x\n", context_x86->ds);
      printf("  edi                          = 0x%x\n", context_x86->edi);
      printf("  esi                          = 0x%x\n", context_x86->esi);
      printf("  ebx                          = 0x%x\n", context_x86->ebx);
      printf("  edx                          = 0x%x\n", context_x86->edx);
      printf("  ecx                          = 0x%x\n", context_x86->ecx);
      printf("  eax                          = 0x%x\n", context_x86->eax);
      printf("  ebp                          = 0x%x\n", context_x86->ebp);
      printf("  eip                          = 0x%x\n", context_x86->eip);
      printf("  cs                           = 0x%x\n", context_x86->cs);
      printf("  eflags                       = 0x%x\n", context_x86->eflags);
      printf("  esp                          = 0x%x\n", context_x86->esp);
      printf("  ss                           = 0x%x\n", context_x86->ss);
      printf("  extended_registers[%3d]      = 0x",
             MD_CONTEXT_X86_EXTENDED_REGISTERS_SIZE);
      for (unsigned int register_index = 0;
           register_index < MD_CONTEXT_X86_EXTENDED_REGISTERS_SIZE;
           ++register_index) {
        printf("%02x", context_x86->extended_registers[register_index]);
      }
      printf("\n\n");

      break;
    }

    case MD_CONTEXT_PPC: {
      const MDRawContextPPC* context_ppc = GetContextPPC();
      printf("MDRawContextPPC\n");
      printf("  context_flags            = 0x%x\n",
             context_ppc->context_flags);
      printf("  srr0                     = 0x%x\n", context_ppc->srr0);
      printf("  srr1                     = 0x%x\n", context_ppc->srr1);
      for (unsigned int gpr_index = 0;
           gpr_index < MD_CONTEXT_PPC_GPR_COUNT;
           ++gpr_index) {
        printf("  gpr[%2d]                  = 0x%x\n",
               gpr_index, context_ppc->gpr[gpr_index]);
      }
      printf("  cr                       = 0x%x\n", context_ppc->cr);
      printf("  xer                      = 0x%x\n", context_ppc->xer);
      printf("  lr                       = 0x%x\n", context_ppc->lr);
      printf("  ctr                      = 0x%x\n", context_ppc->ctr);
      printf("  mq                       = 0x%x\n", context_ppc->mq);
      printf("  vrsave                   = 0x%x\n", context_ppc->vrsave);
      for (unsigned int fpr_index = 0;
           fpr_index < MD_FLOATINGSAVEAREA_PPC_FPR_COUNT;
           ++fpr_index) {
        printf("  float_save.fpregs[%2d]    = 0x%" PRIx64 "\n",
               fpr_index, context_ppc->float_save.fpregs[fpr_index]);
      }
      printf("  float_save.fpscr         = 0x%x\n",
             context_ppc->float_save.fpscr);
      // TODO(mmentovai): print the 128-bit quantities in
      // context_ppc->vector_save.  This isn't done yet because printf
      // doesn't support 128-bit quantities, and printing them using
      // PRIx64 as two 64-bit quantities requires knowledge of the CPU's
      // byte ordering.
      printf("  vector_save.save_vrvalid = 0x%x\n",
             context_ppc->vector_save.save_vrvalid);
      printf("\n");

      break;
    }

    case MD_CONTEXT_PPC64: {
      const MDRawContextPPC64* context_ppc64 = GetContextPPC64();
      printf("MDRawContextPPC64\n");
      printf("  context_flags            = 0x%" PRIx64 "\n",
             context_ppc64->context_flags);
      printf("  srr0                     = 0x%" PRIx64 "\n",
             context_ppc64->srr0);
      printf("  srr1                     = 0x%" PRIx64 "\n",
             context_ppc64->srr1);
      for (unsigned int gpr_index = 0;
           gpr_index < MD_CONTEXT_PPC64_GPR_COUNT;
           ++gpr_index) {
        printf("  gpr[%2d]                  = 0x%" PRIx64 "\n",
               gpr_index, context_ppc64->gpr[gpr_index]);
      }
      printf("  cr                       = 0x%" PRIx64 "\n", context_ppc64->cr);
      printf("  xer                      = 0x%" PRIx64 "\n",
             context_ppc64->xer);
      printf("  lr                       = 0x%" PRIx64 "\n", context_ppc64->lr);
      printf("  ctr                      = 0x%" PRIx64 "\n",
             context_ppc64->ctr);
      printf("  vrsave                   = 0x%" PRIx64 "\n",
             context_ppc64->vrsave);
      for (unsigned int fpr_index = 0;
           fpr_index < MD_FLOATINGSAVEAREA_PPC_FPR_COUNT;
           ++fpr_index) {
        printf("  float_save.fpregs[%2d]    = 0x%" PRIx64 "\n",
               fpr_index, context_ppc64->float_save.fpregs[fpr_index]);
      }
      printf("  float_save.fpscr         = 0x%x\n",
             context_ppc64->float_save.fpscr);
      // TODO(mmentovai): print the 128-bit quantities in
      // context_ppc64->vector_save.  This isn't done yet because printf
      // doesn't support 128-bit quantities, and printing them using
      // PRIx64 as two 64-bit quantities requires knowledge of the CPU's
      // byte ordering.
      printf("  vector_save.save_vrvalid = 0x%x\n",
             context_ppc64->vector_save.save_vrvalid);
      printf("\n");

      break;
    }

    case MD_CONTEXT_AMD64: {
      const MDRawContextAMD64* context_amd64 = GetContextAMD64();
      printf("MDRawContextAMD64\n");
      printf("  p1_home       = 0x%" PRIx64 "\n",
             context_amd64->p1_home);
      printf("  p2_home       = 0x%" PRIx64 "\n",
             context_amd64->p2_home);
      printf("  p3_home       = 0x%" PRIx64 "\n",
             context_amd64->p3_home);
      printf("  p4_home       = 0x%" PRIx64 "\n",
             context_amd64->p4_home);
      printf("  p5_home       = 0x%" PRIx64 "\n",
             context_amd64->p5_home);
      printf("  p6_home       = 0x%" PRIx64 "\n",
             context_amd64->p6_home);
      printf("  context_flags = 0x%x\n",
             context_amd64->context_flags);
      printf("  mx_csr        = 0x%x\n",
             context_amd64->mx_csr);
      printf("  cs            = 0x%x\n", context_amd64->cs);
      printf("  ds            = 0x%x\n", context_amd64->ds);
      printf("  es            = 0x%x\n", context_amd64->es);
      printf("  fs            = 0x%x\n", context_amd64->fs);
      printf("  gs            = 0x%x\n", context_amd64->gs);
      printf("  ss            = 0x%x\n", context_amd64->ss);
      printf("  eflags        = 0x%x\n", context_amd64->eflags);
      printf("  dr0           = 0x%" PRIx64 "\n", context_amd64->dr0);
      printf("  dr1           = 0x%" PRIx64 "\n", context_amd64->dr1);
      printf("  dr2           = 0x%" PRIx64 "\n", context_amd64->dr2);
      printf("  dr3           = 0x%" PRIx64 "\n", context_amd64->dr3);
      printf("  dr6           = 0x%" PRIx64 "\n", context_amd64->dr6);
      printf("  dr7           = 0x%" PRIx64 "\n", context_amd64->dr7);
      printf("  rax           = 0x%" PRIx64 "\n", context_amd64->rax);
      printf("  rcx           = 0x%" PRIx64 "\n", context_amd64->rcx);
      printf("  rdx           = 0x%" PRIx64 "\n", context_amd64->rdx);
      printf("  rbx           = 0x%" PRIx64 "\n", context_amd64->rbx);
      printf("  rsp           = 0x%" PRIx64 "\n", context_amd64->rsp);
      printf("  rbp           = 0x%" PRIx64 "\n", context_amd64->rbp);
      printf("  rsi           = 0x%" PRIx64 "\n", context_amd64->rsi);
      printf("  rdi           = 0x%" PRIx64 "\n", context_amd64->rdi);
      printf("  r8            = 0x%" PRIx64 "\n", context_amd64->r8);
      printf("  r9            = 0x%" PRIx64 "\n", context_amd64->r9);
      printf("  r10           = 0x%" PRIx64 "\n", context_amd64->r10);
      printf("  r11           = 0x%" PRIx64 "\n", context_amd64->r11);
      printf("  r12           = 0x%" PRIx64 "\n", context_amd64->r12);
      printf("  r13           = 0x%" PRIx64 "\n", context_amd64->r13);
      printf("  r14           = 0x%" PRIx64 "\n", context_amd64->r14);
      printf("  r15           = 0x%" PRIx64 "\n", context_amd64->r15);
      printf("  rip           = 0x%" PRIx64 "\n", context_amd64->rip);
      // TODO: print xmm, vector, debug registers
      printf("\n");
      break;
    }

    case MD_CONTEXT_SPARC: {
      const MDRawContextSPARC* context_sparc = GetContextSPARC();
      printf("MDRawContextSPARC\n");
      printf("  context_flags       = 0x%x\n",
             context_sparc->context_flags);
      for (unsigned int g_r_index = 0;
           g_r_index < MD_CONTEXT_SPARC_GPR_COUNT;
           ++g_r_index) {
        printf("  g_r[%2d]             = 0x%" PRIx64 "\n",
               g_r_index, context_sparc->g_r[g_r_index]);
      }
      printf("  ccr                 = 0x%" PRIx64 "\n", context_sparc->ccr);
      printf("  pc                  = 0x%" PRIx64 "\n", context_sparc->pc);
      printf("  npc                 = 0x%" PRIx64 "\n", context_sparc->npc);
      printf("  y                   = 0x%" PRIx64 "\n", context_sparc->y);
      printf("  asi                 = 0x%" PRIx64 "\n", context_sparc->asi);
      printf("  fprs                = 0x%" PRIx64 "\n", context_sparc->fprs);

      for (unsigned int fpr_index = 0;
           fpr_index < MD_FLOATINGSAVEAREA_SPARC_FPR_COUNT;
           ++fpr_index) {
        printf("  float_save.regs[%2d] = 0x%" PRIx64 "\n",
               fpr_index, context_sparc->float_save.regs[fpr_index]);
      }
      printf("  float_save.filler   = 0x%" PRIx64 "\n",
             context_sparc->float_save.filler);
      printf("  float_save.fsr      = 0x%" PRIx64 "\n",
             context_sparc->float_save.fsr);
      break;
    }

    case MD_CONTEXT_ARM: {
      const MDRawContextARM* context_arm = GetContextARM();
      printf("MDRawContextARM\n");
      printf("  context_flags       = 0x%x\n",
             context_arm->context_flags);
      for (unsigned int ireg_index = 0;
           ireg_index < MD_CONTEXT_ARM_GPR_COUNT;
           ++ireg_index) {
        printf("  iregs[%2d]            = 0x%x\n",
               ireg_index, context_arm->iregs[ireg_index]);
      }
      printf("  cpsr                = 0x%x\n", context_arm->cpsr);
      printf("  float_save.fpscr     = 0x%" PRIx64 "\n",
             context_arm->float_save.fpscr);
      for (unsigned int fpr_index = 0;
           fpr_index < MD_FLOATINGSAVEAREA_ARM_FPR_COUNT;
           ++fpr_index) {
        printf("  float_save.regs[%2d] = 0x%" PRIx64 "\n",
               fpr_index, context_arm->float_save.regs[fpr_index]);
      }
      for (unsigned int fpe_index = 0;
           fpe_index < MD_FLOATINGSAVEAREA_ARM_FPEXTRA_COUNT;
           ++fpe_index) {
        printf("  float_save.extra[%2d] = 0x%" PRIx32 "\n",
               fpe_index, context_arm->float_save.extra[fpe_index]);
      }

      break;
    }

    case MD_CONTEXT_ARM64: {
      const MDRawContextARM64* context_arm64 = GetContextARM64();
      printf("MDRawContextARM64\n");
      printf("  context_flags       = 0x%" PRIx64 "\n",
             context_arm64->context_flags);
      for (unsigned int ireg_index = 0;
           ireg_index < MD_CONTEXT_ARM64_GPR_COUNT;
           ++ireg_index) {
        printf("  iregs[%2d]            = 0x%" PRIx64 "\n",
               ireg_index, context_arm64->iregs[ireg_index]);
      }
      printf("  cpsr                = 0x%x\n", context_arm64->cpsr);
      printf("  float_save.fpsr     = 0x%x\n", context_arm64->float_save.fpsr);
      printf("  float_save.fpcr     = 0x%x\n", context_arm64->float_save.fpcr);

      for (unsigned int freg_index = 0;
           freg_index < MD_FLOATINGSAVEAREA_ARM64_FPR_COUNT;
           ++freg_index) {
        uint128_struct fp_value = context_arm64->float_save.regs[freg_index];
        printf("  float_save.regs[%2d]            = 0x%" PRIx64 "%" PRIx64 "\n",
               freg_index, fp_value.high, fp_value.low);
      }
      break;
    }

    case MD_CONTEXT_MIPS: {
      const MDRawContextMIPS* context_mips = GetContextMIPS();
      printf("MDRawContextMIPS\n");
      printf("  context_flags        = 0x%x\n",
             context_mips->context_flags);
      for (int ireg_index = 0;
           ireg_index < MD_CONTEXT_MIPS_GPR_COUNT;
           ++ireg_index) {
        printf("  iregs[%2d]           = 0x%" PRIx64 "\n",
               ireg_index, context_mips->iregs[ireg_index]);
      }
      printf("  mdhi                 = 0x%" PRIx64 "\n",
             context_mips->mdhi);
      printf("  mdlo                 = 0x%" PRIx64 "\n",
             context_mips->mdhi);
      for (int dsp_index = 0;
           dsp_index < MD_CONTEXT_MIPS_DSP_COUNT;
           ++dsp_index) {
        printf("  hi[%1d]              = 0x%" PRIx32 "\n",
               dsp_index, context_mips->hi[dsp_index]);
        printf("  lo[%1d]              = 0x%" PRIx32 "\n",
               dsp_index, context_mips->lo[dsp_index]);
      }
      printf("  dsp_control          = 0x%" PRIx32 "\n",
             context_mips->dsp_control);
      printf("  epc                  = 0x%" PRIx64 "\n",
             context_mips->epc);
      printf("  badvaddr             = 0x%" PRIx64 "\n",
             context_mips->badvaddr);
      printf("  status               = 0x%" PRIx32 "\n",
             context_mips->status);
      printf("  cause                = 0x%" PRIx32 "\n",
             context_mips->cause);

      for (int fpr_index = 0;
           fpr_index < MD_FLOATINGSAVEAREA_MIPS_FPR_COUNT;
           ++fpr_index) {
        printf("  float_save.regs[%2d] = 0x%" PRIx64 "\n",
               fpr_index, context_mips->float_save.regs[fpr_index]);
      }
      printf("  float_save.fpcsr     = 0x%" PRIx32 "\n",
             context_mips->float_save.fpcsr);
      printf("  float_save.fir       = 0x%" PRIx32 "\n",
             context_mips->float_save.fir);
      break;
    }

    default: {
      break;
    }
  }
}

}  // namespace google_breakpad
