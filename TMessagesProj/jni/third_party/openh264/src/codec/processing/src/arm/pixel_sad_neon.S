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


WELS_ASM_FUNC_BEGIN WelsProcessingSampleSad8x8_neon
    stmdb sp!, {lr}
    //Loading a horizontal line data (8 bytes)
    vld1.8 {d0}, [r0], r1
    vld1.8 {d1}, [r2], r3

    //Do the SAD for 8 bytes
    vabdl.u8  q1, d0, d1

    mov lr, #7
pixel_sad_8x8_loop0:

    //Loading a horizontal line data (8 bytes)
    vld1.8 {d0}, [r0], r1
    vld1.8 {d1}, [r2], r3

    subs lr, #1

    //Do the SAD for 8 bytes
    vabal.u8  q1, d0, d1
    bne pixel_sad_8x8_loop0

    vadd.u16   d2, d3
    vpaddl.u16 d2, d2
    vpaddl.u32 d2, d2
    vmov.u32   r0, d2[0]//TBO...

    ldmia sp!, {lr}
WELS_ASM_FUNC_END

#endif
