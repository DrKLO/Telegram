// Copyright (c) 2014, Google Inc.
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

#include "client/linux/dump_writer_common/seccomp_unwinder.h"

#include <string.h>

#include "google_breakpad/common/minidump_format.h"
#include "common/linux/linux_libc_support.h"

namespace google_breakpad {

void SeccompUnwinder::PopSeccompStackFrame(RawContextCPU* cpu,
                                           const MDRawThread& thread,
                                           uint8_t* stack_copy) {
#if defined(__x86_64)
  uint64_t bp = cpu->rbp;
  uint64_t top = thread.stack.start_of_memory_range;
  for (int i = 4; i--; ) {
    if (bp < top ||
        bp > thread.stack.start_of_memory_range +
        thread.stack.memory.data_size - sizeof(bp) ||
        bp & 1) {
      break;
    }
    uint64_t old_top = top;
    top = bp;
    uint8_t* bp_addr = stack_copy + bp - thread.stack.start_of_memory_range;
    my_memcpy(&bp, bp_addr, sizeof(bp));
    if (bp == 0xDEADBEEFDEADBEEFull) {
      struct {
        uint64_t r15;
        uint64_t r14;
        uint64_t r13;
        uint64_t r12;
        uint64_t r11;
        uint64_t r10;
        uint64_t r9;
        uint64_t r8;
        uint64_t rdi;
        uint64_t rsi;
        uint64_t rdx;
        uint64_t rcx;
        uint64_t rbx;
        uint64_t deadbeef;
        uint64_t rbp;
        uint64_t fakeret;
        uint64_t ret;
        /* char redzone[128]; */
      } seccomp_stackframe;
      if (top - offsetof(__typeof__(seccomp_stackframe), deadbeef) < old_top ||
          top - offsetof(__typeof__(seccomp_stackframe), deadbeef) +
          sizeof(seccomp_stackframe) >
          thread.stack.start_of_memory_range+thread.stack.memory.data_size) {
        break;
      }
      my_memcpy(&seccomp_stackframe,
                bp_addr - offsetof(__typeof__(seccomp_stackframe), deadbeef),
                sizeof(seccomp_stackframe));
      cpu->rbx = seccomp_stackframe.rbx;
      cpu->rcx = seccomp_stackframe.rcx;
      cpu->rdx = seccomp_stackframe.rdx;
      cpu->rsi = seccomp_stackframe.rsi;
      cpu->rdi = seccomp_stackframe.rdi;
      cpu->rbp = seccomp_stackframe.rbp;
      cpu->rsp = top + 4*sizeof(uint64_t) + 128;
      cpu->r8  = seccomp_stackframe.r8;
      cpu->r9  = seccomp_stackframe.r9;
      cpu->r10 = seccomp_stackframe.r10;
      cpu->r11 = seccomp_stackframe.r11;
      cpu->r12 = seccomp_stackframe.r12;
      cpu->r13 = seccomp_stackframe.r13;
      cpu->r14 = seccomp_stackframe.r14;
      cpu->r15 = seccomp_stackframe.r15;
      cpu->rip = seccomp_stackframe.fakeret;
      return;
    }
  }
#elif defined(__i386__)
  uint32_t bp = cpu->ebp;
  uint32_t top = thread.stack.start_of_memory_range;
  for (int i = 4; i--; ) {
    if (bp < top ||
        bp > thread.stack.start_of_memory_range +
        thread.stack.memory.data_size - sizeof(bp) ||
        bp & 1) {
      break;
    }
    uint32_t old_top = top;
    top = bp;
    uint8_t* bp_addr = stack_copy + bp - thread.stack.start_of_memory_range;
    my_memcpy(&bp, bp_addr, sizeof(bp));
    if (bp == 0xDEADBEEFu) {
      struct {
        uint32_t edi;
        uint32_t esi;
        uint32_t edx;
        uint32_t ecx;
        uint32_t ebx;
        uint32_t deadbeef;
        uint32_t ebp;
        uint32_t fakeret;
        uint32_t ret;
      } seccomp_stackframe;
      if (top - offsetof(__typeof__(seccomp_stackframe), deadbeef) < old_top ||
          top - offsetof(__typeof__(seccomp_stackframe), deadbeef) +
          sizeof(seccomp_stackframe) >
          thread.stack.start_of_memory_range+thread.stack.memory.data_size) {
        break;
      }
      my_memcpy(&seccomp_stackframe,
                bp_addr - offsetof(__typeof__(seccomp_stackframe), deadbeef),
                sizeof(seccomp_stackframe));
      cpu->ebx = seccomp_stackframe.ebx;
      cpu->ecx = seccomp_stackframe.ecx;
      cpu->edx = seccomp_stackframe.edx;
      cpu->esi = seccomp_stackframe.esi;
      cpu->edi = seccomp_stackframe.edi;
      cpu->ebp = seccomp_stackframe.ebp;
      cpu->esp = top + 4*sizeof(void*);
      cpu->eip = seccomp_stackframe.fakeret;
      return;
    }
  }
#endif
}

}  // namespace google_breakpad
