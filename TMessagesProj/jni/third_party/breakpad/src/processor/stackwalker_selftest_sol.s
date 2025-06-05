/* Copyright (c) 2007, Google Inc.
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are
 * met:
 *
 *     * Redistributions of source code must retain the above copyright
 * notice, this list of conditions and the following disclaimer.
 *     * Redistributions in binary form must reproduce the above
 * copyright notice, this list of conditions and the following disclaimer
 * in the documentation and/or other materials provided with the
 * distribution.
 *     * Neither the name of Google Inc. nor the names of its
 * contributors may be used to endorse or promote products derived from
 * this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
 * "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT
 * LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR
 * A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT
 * OWNER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
 * SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT
 * LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,
 * DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY
 * THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
 * OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

/* stackwalker_selftest_sol.s
 * On Solaris, the recommeded compiler is CC, so we can not use gcc inline
 * asm, use this method instead.
 *
 * How to compile: as -P -L -D_ASM -D_STDC -K PIC -o \
 *                 src/processor/stackwalker_selftest_sol.o \
 *                 src/processor/stackwalker_selftest_sol.s
 *
 * Author: Michael Shang
 */

#include <sys/asm_linkage.h>

#if defined(__i386)


ENTRY(GetEBP) 
      pushl    %ebp
      movl     %esp,%ebp
      subl     $0x00000004,%esp
      movl     0x00000000(%ebp),%eax
      movl     %eax,0xfffffffc(%ebp)
      movl     0xfffffffc(%ebp),%eax
      leave    
      ret      
SET_SIZE(GetEBP)

ENTRY(GetEIP) 
      pushl    %ebp
      movl     %esp,%ebp
      subl     $0x00000004,%esp
      movl     0x00000004(%ebp),%eax
      movl     %eax,0xfffffffc(%ebp)
      movl     0xfffffffc(%ebp),%eax
      leave    
      ret      
SET_SIZE(GetEIP)

ENTRY(GetESP) 
      pushl    %ebp
      movl     %esp,%ebp
      subl     $0x00000004,%esp
      movl     %ebp,%eax
      movl     %eax,0xfffffffc(%ebp)
      movl     0xfffffffc(%ebp),%eax
      addl     $0x00000008,%eax
      leave    
      ret      
SET_SIZE(GetESP)


#elif defined(__sparc)


ENTRY(GetPC)
      save     %sp, -120, %sp
      mov      %i7, %i4
      inccc    8, %i4
      mov      %i4, %i0
      ret      
      restore  
SET_SIZE(GetPC)

ENTRY(GetSP)
      save     %sp, -120, %sp
      mov      %fp, %i4
      mov      %i4, %i0
      ret      
      restore  
SET_SIZE(GetSP)

ENTRY(GetFP)
      save     %sp, -120, %sp
      ld       [%fp + 56], %g1
      mov      %g1, %i0
      ret      
      restore  
SET_SIZE(GetFP)


#endif  // __i386 || __sparc
