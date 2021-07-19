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

WELS_ASM_FUNC_BEGIN WelsI16x16LumaPredV_neon
    //Get the top line data to 'q0'
    sub  r3, r1, r2
    vldm r3, {d0, d1}

    //mov  r2, #16
    mov  r3, #4
    //Set the top line to the each line of MB(16*16)
loop_0_get_i16x16_luma_pred_v:
    vst1.8 {d0,d1}, [r0]!
    vst1.8 {d0,d1}, [r0]!
    vst1.8 {d0,d1}, [r0]!
    vst1.8 {d0,d1}, [r0]!
    subs  r3, #1
    bne  loop_0_get_i16x16_luma_pred_v
WELS_ASM_FUNC_END


WELS_ASM_FUNC_BEGIN WelsI16x16LumaPredH_neon
    //stmdb sp!, {r4, lr}
    sub  r1, r1, #1
    mov  r3, #4
loop_0_get_i16x16_luma_pred_h:
    //Get one byte data from left side
    vld1.8 {d0[],d1[]}, [r1], r2
    vld1.8 {d2[],d3[]}, [r1], r2
    vld1.8 {d4[],d5[]}, [r1], r2
    vld1.8 {d6[],d7[]}, [r1], r2

    //Set the line of MB using the left side byte data
    vst1.8 {d0,d1}, [r0]!
    //add r0, #16
    vst1.8 {d2,d3}, [r0]!
    //add r0, #16
    vst1.8 {d4,d5}, [r0]!
    //add r0, #16
    vst1.8 {d6,d7}, [r0]!
    //add r0, #16

    subs  r3, #1
    bne  loop_0_get_i16x16_luma_pred_h

WELS_ASM_FUNC_END


#endif
