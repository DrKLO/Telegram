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

.macro SQR_ADD_16BYTES arg0, arg1, arg2
    vmull.u8 q3, \arg0, \arg0
    vmull.u8 q8, \arg1, \arg1
    vpadal.u16 \arg2, q3
    vpadal.u16 \arg2, q8
.endm


WELS_ASM_FUNC_BEGIN SampleVariance16x16_neon
    stmdb sp!, {r4}

    vld1.8   {q15}, [r0], r1 //save the ref data (16bytes)
    vld1.8   {q14}, [r2], r3 //save the src data (16bytes)


    vabd.u8  q13, q14, q15
    vmull.u8 q12, d27, d27
    vmull.u8 q11, d26, d26
    vaddl.u16 q12, d24, d25
    vpadal.u16 q12, q11     //sqr

    vaddl.u8 q13, d26, d27 //sum

    vaddl.u8 q10, d28, d29 //sum_cur

    vmull.u8 q9,  d29, d29
    vmull.u8 q8,  d28, d28
    vaddl.u16 q9, d18, d19       //sqr_cur
    vpadal.u16 q9, q8

    mov r4, #15
pixel_var_16x16_loop0:

    vld1.8 {q0}, [r0], r1 //save the ref data (16bytes)
    vld1.8 {q1}, [r2], r3 //save the src data (16bytes)

    vabd.u8 q2, q0, q1

    //q10 save sum_cur
    vpadal.u8 q10, q1

    //q12 save sqr
    SQR_ADD_16BYTES d4, d5, q12

    //q13 save sum
    vpadal.u8 q13, q2

    subs r4, #1

    //q9 save sqr_cur
    SQR_ADD_16BYTES d2, d3, q9

    bne pixel_var_16x16_loop0

    vadd.u16 d0, d26, d27 //sum
    vadd.u16 d1, d20, d21 //sum_cur
    vpaddl.u16 q0, q0
    vadd.u32 d2, d24, d25 //sqr
    vadd.u32 d3, d18, d19 //sqr_cur
    vpadd.u32 d0, d0, d1
    vpadd.u32 d1, d2, d3

    ldr       r4, [sp, #4]

    vshr.u32  q0, q0, #8
    vmul.u32  d0, d0
    vsub.u32  d0, d1, d0
    vmovl.u32 q0, d0
    vst2.16  {d0[0], d1[0]}, [r4]

    ldmia sp!, {r4}

WELS_ASM_FUNC_END

#endif
