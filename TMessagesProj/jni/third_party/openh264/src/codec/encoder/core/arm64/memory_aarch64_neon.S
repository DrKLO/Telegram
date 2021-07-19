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

#ifdef HAVE_NEON_AARCH64
#include "arm_arch64_common_macro.S"

//void WelsSetMemZero_AArch64_neon (void* pDst, int32_t iSize);
WELS_ASM_AARCH64_FUNC_BEGIN WelsSetMemZero_AArch64_neon
    eor v0.16b, v0.16b, v0.16b
    SIGN_EXTENSION x1,w1
    cmp x1, #32
    b.eq mem_zero_32_neon_start
    b.lt mem_zero_24_neon_start
mem_zero_loop:
    subs x1, x1, #64
    st1 {v0.16b}, [x0], #16
    st1 {v0.16b}, [x0], #16
    st1 {v0.16b}, [x0], #16
    st1 {v0.16b}, [x0], #16
    b.ne mem_zero_loop
    b mem_zero_end

mem_zero_32_neon_start:
    st1 {v0.16b}, [x0], #16
    st1 {v0.16b}, [x0], #16
    b mem_zero_end
mem_zero_24_neon_start:
    st1 {v0.16b}, [x0], #16
    st1 {v0.8b}, [x0], #8
mem_zero_end:

WELS_ASM_AARCH64_FUNC_END

#endif
