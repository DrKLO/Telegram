/*!
 * \copy
 *     Copyright (c)  2013, Cisco Systems
 *     All rights reserved.
 *
 *     Redistribution and use in source and binary forms, with or without
 *     modification, are permitted provided that the following conditions
 *     are met:
 *
 *        * Redistributions of source code must retain the above copyright
 *          notice, this list of conditions and the following disclaimer.
 *
 *        * Redistributions in binary form must reproduce the above copyright
 *          notice, this list of conditions and the following disclaimer in
 *          the documentation and/or other materials provided with the
 *          distribution.
 *
 *     THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
 *     "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT
 *     LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS
 *     FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE
 *     COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT,
 *     INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING,
 *     BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 *     LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER
 *     CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT
 *     LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN
 *     ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 *     POSSIBILITY OF SUCH DAMAGE.
 *
 */

#ifdef HAVE_NEON
#include "arm_arch_common_macro.S"


WELS_ASM_FUNC_BEGIN WelsSetMemZero_neon
    veor q0, q0
    cmp r1, #32
    beq mem_zero_32_neon_start
    blt mem_zero_24_neon_start

mem_zero_loop:
    subs r1, r1, #64
    vst1.64 {q0}, [r0]!
    vst1.64 {q0}, [r0]!
    vst1.64 {q0}, [r0]!
    vst1.64 {q0}, [r0]!
    bne mem_zero_loop
    bx lr

mem_zero_32_neon_start:
    vst1.64 {q0}, [r0]!
    vst1.64 {q0}, [r0]!
    bx lr

mem_zero_24_neon_start:
    vst1.64 {q0}, [r0]!
    vst1.64 {d0}, [r0]!
WELS_ASM_FUNC_END

#endif
