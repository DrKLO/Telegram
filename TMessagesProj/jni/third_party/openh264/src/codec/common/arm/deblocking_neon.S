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

.macro JMP_IF_128BITS_IS_ZERO arg0, arg1, arg2
    vorr.s16    \arg2, \arg0, \arg1
    vmov        r3, r2, \arg2
    orr         r3, r3, r2
    cmp         r3, #0
.endm

.macro MASK_MATRIX arg0, arg1, arg2, arg3, arg4, arg5, arg6
    vabd.u8 \arg6, \arg1, \arg2
    vcgt.u8 \arg6, \arg4, \arg6

    vabd.u8 \arg4, \arg0, \arg1
    vclt.u8 \arg4, \arg4, \arg5
    vand.u8 \arg6, \arg6, \arg4

    vabd.u8 \arg4, \arg3, \arg2
    vclt.u8 \arg4, \arg4, \arg5
    vand.u8 \arg6, \arg6, \arg4
.endm

.macro DIFF_LUMA_LT4_P1_Q1 arg0, arg1, arg2, arg3, arg4, arg5, arg6, arg7, arg8, arg9
    vmov.i8 \arg9, #128
    vrhadd.u8   \arg8, \arg2, \arg3
    vhadd.u8    \arg8, \arg0, \arg8
    vsub.s8 \arg8, \arg8, \arg9
    vsub.s8 \arg9, \arg1, \arg9
    vqsub.s8    \arg8, \arg8, \arg9
    vmax.s8 \arg8, \arg8, \arg5
    vmin.s8 \arg8, \arg8, \arg6
    vabd.u8 \arg9, \arg0, \arg2
    vclt.u8 \arg9, \arg9, \arg4
    vand.s8 \arg8, \arg8, \arg9
    vand.s8 \arg8, \arg8, \arg7
    vadd.u8 \arg8, \arg1, \arg8
    vabs.s8 \arg9, \arg9
.endm

.macro DIFF_LUMA_LT4_P0_Q0 arg0, arg1, arg2, arg3, arg4, arg5, arg6
    vsubl.u8    \arg5, \arg0, \arg3
    vsubl.u8    \arg6, \arg2, \arg1
    vshl.s16    \arg6, \arg6, #2
    vadd.s16    \arg5, \arg5, \arg6
    vqrshrn.s16     \arg4, \arg5, #3
.endm


.macro DIFF_LUMA_EQ4_P2P1P0 arg0, arg1, arg2, arg3, arg4, arg5, arg6, arg7
    vaddl.u8    q4, \arg1, \arg2
    vaddl.u8    q5, \arg3, \arg4
    vadd.u16    q5, q4, q5

    vaddl.u8    q4, \arg0, \arg1
    vshl.u16    q4, q4, #1
    vadd.u16    q4, q5, q4

    vrshrn.u16      \arg0, q5, #2
    vrshrn.u16      \arg7, q4, #3

    vshl.u16    q5, q5, #1
    vsubl.u8    q4, \arg5, \arg1
    vadd.u16    q5, q4,q5

    vaddl.u8    q4, \arg2, \arg5
    vaddw.u8    q4, q4, \arg2
    vaddw.u8    q4, q4, \arg3

    vrshrn.u16      d10,q5, #3
    vrshrn.u16      d8, q4, #2
    vbsl.u8     \arg6, d10, d8
.endm

.macro DIFF_LUMA_EQ4_MASK arg0, arg1, arg2, arg3
    vmov    \arg3, \arg2
    vbsl.u8 \arg3, \arg0, \arg1
.endm

.macro DIFF_CHROMA_EQ4_P0Q0 arg0, arg1, arg2, arg3, arg4, arg5, arg6, arg7, arg8
    vaddl.u8    \arg4, \arg0, \arg3
    vaddw.u8    \arg5, \arg4, \arg1
    vaddw.u8    \arg6, \arg4, \arg2
    vaddw.u8    \arg5, \arg5, \arg0
    vaddw.u8    \arg6, \arg6, \arg3
    vrshrn.u16      \arg7, \arg5, #2
    vrshrn.u16      \arg8, \arg6, #2
.endm

.macro LOAD_CHROMA_DATA_4 arg0, arg1, arg2, arg3, arg4, arg5, arg6, arg7, arg8
    vld4.u8 {\arg0[\arg8],\arg1[\arg8],\arg2[\arg8],\arg3[\arg8]}, [r0], r2
    vld4.u8 {\arg4[\arg8],\arg5[\arg8],\arg6[\arg8],\arg7[\arg8]}, [r1], r2
.endm

.macro STORE_CHROMA_DATA_4 arg0, arg1, arg2, arg3, arg4, arg5, arg6, arg7, arg8
    vst4.u8 {\arg0[\arg8],\arg1[\arg8],\arg2[\arg8],\arg3[\arg8]}, [r0], r2
    vst4.u8 {\arg4[\arg8],\arg5[\arg8],\arg6[\arg8],\arg7[\arg8]}, [r1], r2
.endm

.macro LOAD_LUMA_DATA_3 arg0, arg1, arg2, arg3, arg4, arg5, arg6
    vld3.u8 {\arg0[\arg6],\arg1[\arg6],\arg2[\arg6]}, [r2], r1
    vld3.u8 {\arg3[\arg6],\arg4[\arg6],\arg5[\arg6]}, [r0], r1
.endm

.macro STORE_LUMA_DATA_4 arg0, arg1, arg2, arg3, arg4, arg5
    vst4.u8 {\arg0[\arg4],\arg1[\arg4],\arg2[\arg4],\arg3[\arg4]}, [r0], r1
    vst4.u8 {\arg0[\arg5],\arg1[\arg5],\arg2[\arg5],\arg3[\arg5]}, [r2], r1
.endm

.macro STORE_LUMA_DATA_3 arg0, arg1, arg2, arg3, arg4, arg5, arg6
    vst3.u8 {\arg0[\arg6],\arg1[\arg6],\arg2[\arg6]}, [r3], r1
    vst3.u8 {\arg3[\arg6],\arg4[\arg6],\arg5[\arg6]}, [r0], r1
.endm

.macro EXTRACT_DELTA_INTO_TWO_PART arg0, arg1
    vcge.s8 \arg1, \arg0, #0
    vand    \arg1, \arg0, \arg1
    vsub.s8 \arg0, \arg1, \arg0
.endm

WELS_ASM_FUNC_BEGIN DeblockLumaLt4V_neon
    vpush   {q4-q7}
    vdup.u8 q11, r2
    vdup.u8 q9, r3

    add         r2, r1, r1, lsl #1
    sub         r2, r0, r2
    vld1.u8 {q0}, [r2], r1
    vld1.u8 {q3}, [r0], r1
    vld1.u8 {q1}, [r2], r1
    vld1.u8 {q4}, [r0], r1
    vld1.u8 {q2}, [r2]
    vld1.u8 {q5}, [r0]
    sub         r2, r2, r1

    ldr         r3, [sp, #64]
    vld1.s8 {d31}, [r3]
    vdup.s8 d28, d31[0]
    vdup.s8 d30, d31[1]
    vdup.s8 d29, d31[2]
    vdup.s8 d31, d31[3]
    vtrn.32 d28, d30
    vtrn.32 d29, d31
    vcge.s8 q10, q14, #0

    MASK_MATRIX q1, q2, q3, q4, q11, q9, q15
    vand.u8 q10, q10, q15

    veor        q15, q15
    vsub.i8 q15,q15,q14

    DIFF_LUMA_LT4_P1_Q1 q0, q1, q2, q3, q9, q15, q14, q10, q6, q12
    vst1.u8 {q6}, [r2], r1

    DIFF_LUMA_LT4_P1_Q1 q5, q4, q3, q2, q9, q15, q14, q10, q7, q13

    vabs.s8 q12, q12
    vabs.s8 q13, q13
    vadd.u8 q14,q14,q12
    vadd.u8 q14,q14,q13
    veor        q15, q15
    vsub.i8 q15,q15,q14

    DIFF_LUMA_LT4_P0_Q0 d2, d4, d6, d8, d16, q12, q13
    DIFF_LUMA_LT4_P0_Q0 d3, d5, d7, d9, d17, q12, q13
    vmax.s8 q8, q8, q15
    vmin.s8 q8, q8, q14
    vand.s8 q8, q8, q10
    EXTRACT_DELTA_INTO_TWO_PART q8, q9
    vqadd.u8    q2, q2, q9
    vqsub.u8    q2, q2, q8
    vst1.u8 {q2}, [r2], r1
    vqsub.u8    q3, q3, q9
    vqadd.u8    q3, q3, q8
    vst1.u8 {q3}, [r2]  , r1
    vst1.u8 {q7}, [r2]

    vpop    {q4-q7}
WELS_ASM_FUNC_END


WELS_ASM_FUNC_BEGIN DeblockLumaEq4V_neon
    vpush   {q4-q7}

    vdup.u8 q5, r2
    vdup.u8 q4, r3

    sub         r3, r0, r1, lsl #2
    vld1.u8 {q8},  [r3], r1
    vld1.u8 {q12}, [r0], r1
    vld1.u8 {q9},  [r3], r1
    vld1.u8 {q13}, [r0], r1
    vld1.u8 {q10}, [r3], r1
    vld1.u8 {q14}, [r0], r1
    vld1.u8 {q11}, [r3]
    vld1.u8 {q15}, [r0]
    sub         r3, r3, r1  , lsl #1

    MASK_MATRIX q10, q11, q12, q13, q5, q4, q6

    mov         r2, r2, lsr #2
    add         r2, r2, #2
    vdup.u8 q5, r2
    vabd.u8 q0, q11, q12
    vclt.u8 q7, q0, q5

    vabd.u8 q1, q9, q11
    vclt.u8 q1, q1, q4
    vand.s8 q1, q1, q7

    vabd.u8 q2, q14,q12
    vclt.u8 q2, q2, q4
    vand.s8 q2, q2, q7
    vand.u8 q7, q7, q6

    vmov        q3, q1

    DIFF_LUMA_EQ4_P2P1P0        d16, d18, d20, d22, d24, d26, d2, d0
    DIFF_LUMA_EQ4_P2P1P0        d17, d19, d21, d23, d25, d27, d3, d1

    vand.u8 q3, q7, q3
    DIFF_LUMA_EQ4_MASK  q0, q9, q3, q4
    vst1.u8 {q4}, [r3], r1
    DIFF_LUMA_EQ4_MASK  q8,q10, q3, q4
    vst1.u8 {q4}, [r3], r1
    DIFF_LUMA_EQ4_MASK  q1,q11, q6, q4
    vst1.u8 {q4}, [r3], r1

    vmov        q0, q2
    DIFF_LUMA_EQ4_P2P1P0        d30, d28, d26, d24, d22, d20, d4, d6
    DIFF_LUMA_EQ4_P2P1P0        d31, d29, d27, d25, d23, d21, d5, d7

    vand.u8 q0, q7, q0
    DIFF_LUMA_EQ4_MASK  q2,  q12, q6, q4
    vst1.u8 {q4}, [r3], r1
    DIFF_LUMA_EQ4_MASK  q15, q13, q0, q4
    vst1.u8 {q4}, [r3], r1
    DIFF_LUMA_EQ4_MASK  q3,  q14, q0, q4
    vst1.u8 {q4}, [r3], r1

    vpop    {q4-q7}
WELS_ASM_FUNC_END


WELS_ASM_FUNC_BEGIN DeblockLumaLt4H_neon
    vpush   {q4-q7}

    vdup.u8 q11, r2
    vdup.u8 q9, r3

    sub         r2, r0, #3
    LOAD_LUMA_DATA_3        d0, d1, d2, d6, d7, d8, 0
    LOAD_LUMA_DATA_3        d0, d1, d2, d6, d7, d8, 1
    LOAD_LUMA_DATA_3        d0, d1, d2, d6, d7, d8, 2
    LOAD_LUMA_DATA_3        d0, d1, d2, d6, d7, d8, 3
    LOAD_LUMA_DATA_3        d0, d1, d2, d6, d7, d8, 4
    LOAD_LUMA_DATA_3        d0, d1, d2, d6, d7, d8, 5
    LOAD_LUMA_DATA_3        d0, d1, d2, d6, d7, d8, 6
    LOAD_LUMA_DATA_3        d0, d1, d2, d6, d7, d8, 7

    LOAD_LUMA_DATA_3        d3, d4, d5, d9, d10, d11, 0
    LOAD_LUMA_DATA_3        d3, d4, d5, d9, d10, d11, 1
    LOAD_LUMA_DATA_3        d3, d4, d5, d9, d10, d11, 2
    LOAD_LUMA_DATA_3        d3, d4, d5, d9, d10, d11, 3
    LOAD_LUMA_DATA_3        d3, d4, d5, d9, d10, d11, 4
    LOAD_LUMA_DATA_3        d3, d4, d5, d9, d10, d11, 5
    LOAD_LUMA_DATA_3        d3, d4, d5, d9, d10, d11, 6
    LOAD_LUMA_DATA_3        d3, d4, d5, d9, d10, d11, 7

    vswp        d1, d2
    vswp        d3, d4
    vswp        d1, d4
    vswp        d7, d8
    vswp        d9, d10
    vswp        d7, d10

    sub         r0, r0, r1, lsl #4

    ldr         r3, [sp, #64]
    vld1.s8 {d31}, [r3]
    vdup.s8 d28, d31[0]
    vdup.s8 d30, d31[1]
    vdup.s8 d29, d31[2]
    vdup.s8 d31, d31[3]
    vtrn.32 d28, d30
    vtrn.32 d29, d31
    vcge.s8 q10, q14, #0

    MASK_MATRIX q1, q2, q3, q4, q11, q9, q15
    vand.u8 q10, q10, q15

    veor        q15, q15
    vsub.i8 q15,q15,q14

    DIFF_LUMA_LT4_P1_Q1 q0, q1, q2, q3, q9, q15, q14, q10, q6, q12
    DIFF_LUMA_LT4_P1_Q1 q5, q4, q3, q2, q9, q15, q14, q10, q7, q13

    vabs.s8 q12, q12
    vabs.s8 q13, q13
    vadd.u8 q14,q14,q12
    vadd.u8 q14,q14,q13
    veor        q15, q15
    vsub.i8 q15,q15,q14

    DIFF_LUMA_LT4_P0_Q0 d2, d4, d6, d8, d16, q12, q13
    DIFF_LUMA_LT4_P0_Q0 d3, d5, d7, d9, d17, q12, q13
    vmax.s8 q8, q8, q15
    vmin.s8 q8, q8, q14
    vand.s8 q8, q8, q10
    EXTRACT_DELTA_INTO_TWO_PART q8, q9
    vqadd.u8    q2, q2, q9
    vqsub.u8    q2, q2, q8

    vqsub.u8    q3, q3, q9
    vqadd.u8    q3, q3, q8

    sub     r0, #2
    add     r2, r0, r1
    lsl     r1, #1

    vmov        q1, q6
    vmov        q4, q7

    vswp        q2, q3
    vswp        d3, d6
    vswp        d5, d8

    STORE_LUMA_DATA_4       d2, d3, d4, d5, 0, 1
    STORE_LUMA_DATA_4       d2, d3, d4, d5, 2, 3
    STORE_LUMA_DATA_4       d2, d3, d4, d5, 4, 5
    STORE_LUMA_DATA_4       d2, d3, d4, d5, 6, 7

    STORE_LUMA_DATA_4       d6, d7, d8, d9, 0, 1
    STORE_LUMA_DATA_4       d6, d7, d8, d9, 2, 3
    STORE_LUMA_DATA_4       d6, d7, d8, d9, 4, 5
    STORE_LUMA_DATA_4       d6, d7, d8, d9, 6, 7

    vpop    {q4-q7}
WELS_ASM_FUNC_END


WELS_ASM_FUNC_BEGIN DeblockLumaEq4H_neon
    vpush   {q4-q7}
    vdup.u8 q5, r2
    vdup.u8 q4, r3

    sub         r3, r0, #4              //  pix -= 4

    vld1.u8 {d16}, [r3], r1
    vld1.u8 {d17}, [r3], r1
    vld1.u8 {d18}, [r3], r1
    vld1.u8 {d19}, [r3], r1
    vld1.u8 {d20}, [r3], r1
    vld1.u8 {d21}, [r3], r1
    vld1.u8 {d22}, [r3], r1
    vld1.u8 {d23}, [r3], r1
    vld1.u8 {d24}, [r3], r1
    vld1.u8 {d25}, [r3], r1
    vld1.u8 {d26}, [r3], r1
    vld1.u8 {d27}, [r3], r1
    vld1.u8 {d28}, [r3], r1
    vld1.u8 {d29}, [r3], r1
    vld1.u8 {d30}, [r3], r1
    vld1.u8 {d31}, [r3], r1

    vtrn.u32    d16, d20
    vtrn.u32    d17, d21
    vtrn.u32    d18, d22
    vtrn.u32    d19, d23
    vtrn.u32    d24, d28
    vtrn.u32    d25, d29
    vtrn.u32    d26, d30
    vtrn.u32    d27, d31

    vtrn.u16    d16, d18
    vtrn.u16    d17, d19
    vtrn.u16    d20, d22
    vtrn.u16    d21, d23
    vtrn.u16    d24, d26
    vtrn.u16    d25, d27
    vtrn.u16    d28, d30
    vtrn.u16    d29, d31

    vtrn.u8 d16, d17
    vtrn.u8 d18, d19
    vtrn.u8 d20, d21
    vtrn.u8 d22, d23
    vtrn.u8 d24, d25
    vtrn.u8 d26, d27
    vtrn.u8 d28, d29
    vtrn.u8 d30, d31

    vswp    d17, d24
    vswp    d19, d26
    vswp    d21, d28
    vswp    d23, d30

    vswp    q12, q9
    vswp    q14, q11

    vswp    q12, q10
    vswp    q13, q11

    MASK_MATRIX q10, q11, q12, q13, q5, q4, q6

    mov         r2, r2, lsr #2
    add         r2, r2, #2
    vdup.u8 q5, r2
    vabd.u8 q0, q11, q12
    vclt.u8 q7, q0, q5

    vabd.u8 q1, q9, q11
    vclt.u8 q1, q1, q4
    vand.s8 q1, q1, q7

    vabd.u8 q2, q14,q12
    vclt.u8 q2, q2, q4
    vand.s8 q2, q2, q7
    vand.u8 q7, q7, q6

    vmov        q3, q1

    DIFF_LUMA_EQ4_P2P1P0        d16, d18, d20, d22, d24, d26, d2, d0
    DIFF_LUMA_EQ4_P2P1P0        d17, d19, d21, d23, d25, d27, d3, d1

    vand.u8 q3, q7, q3
    DIFF_LUMA_EQ4_MASK  q0, q9, q3, q4
    vmov        q9, q4
    vbsl.u8 q3, q8, q10
    DIFF_LUMA_EQ4_MASK  q1,q11, q6, q8

    vand.u8 q7, q7, q2

    DIFF_LUMA_EQ4_P2P1P0        d30, d28, d26, d24, d22, d20, d4, d0
    DIFF_LUMA_EQ4_P2P1P0        d31, d29, d27, d25, d23, d21, d5, d1

    vbsl.u8 q6, q2, q12
    DIFF_LUMA_EQ4_MASK  q15, q13, q7, q4

    vbsl.u8 q7, q0, q14

    vmov        q5, q6
    vmov        q2, q9
    vmov        q6, q4
    vmov        q4, q8

    vswp    d8, d6
    vswp    d5, d7
    vswp    d5, d8
    vswp    d14, d12
    vswp    d11, d13
    vswp    d11, d14

    sub     r3, r0, #3
    STORE_LUMA_DATA_3       d4,d5,d6,d10,d11,d12,0
    STORE_LUMA_DATA_3       d4,d5,d6,d10,d11,d12,1
    STORE_LUMA_DATA_3       d4,d5,d6,d10,d11,d12,2
    STORE_LUMA_DATA_3       d4,d5,d6,d10,d11,d12,3
    STORE_LUMA_DATA_3       d4,d5,d6,d10,d11,d12,4
    STORE_LUMA_DATA_3       d4,d5,d6,d10,d11,d12,5
    STORE_LUMA_DATA_3       d4,d5,d6,d10,d11,d12,6
    STORE_LUMA_DATA_3       d4,d5,d6,d10,d11,d12,7

    STORE_LUMA_DATA_3       d7,d8,d9,d13,d14,d15,0
    STORE_LUMA_DATA_3       d7,d8,d9,d13,d14,d15,1
    STORE_LUMA_DATA_3       d7,d8,d9,d13,d14,d15,2
    STORE_LUMA_DATA_3       d7,d8,d9,d13,d14,d15,3
    STORE_LUMA_DATA_3       d7,d8,d9,d13,d14,d15,4
    STORE_LUMA_DATA_3       d7,d8,d9,d13,d14,d15,5
    STORE_LUMA_DATA_3       d7,d8,d9,d13,d14,d15,6
    STORE_LUMA_DATA_3       d7,d8,d9,d13,d14,d15,7

    vpop    {q4-q7}
WELS_ASM_FUNC_END


WELS_ASM_FUNC_BEGIN DeblockChromaLt4V_neon
    vdup.u8 q11, r3
    ldr         r3, [sp, #0]

    sub         r0, r0, r2  , lsl #1
    sub         r1, r1, r2, lsl #1
    vdup.u8     q9, r3
    ldr         r3, [sp, #4]

    vld1.u8 {d0}, [r0], r2
    vld1.u8 {d1}, [r1], r2
    vld1.u8 {d2}, [r0], r2
    vld1.u8 {d3}, [r1], r2
    vld1.u8 {d4}, [r0], r2
    vld1.u8 {d5}, [r1], r2
    vld1.u8 {d6}, [r0]
    vld1.u8 {d7}, [r1]

    sub         r0, r0, r2, lsl #1
    sub         r1, r1, r2, lsl #1

    vld1.s8 {d31}, [r3]
    vmovl.u8    q14,d31
    vshl.u64    d29,d28,#8
    vorr        d28,d29
    vmov        d29, d28
    veor        q15, q15
    vsub.i8 q15,q15,q14

    MASK_MATRIX q0, q1, q2, q3, q11, q9, q10

    DIFF_LUMA_LT4_P0_Q0 d0, d2, d4, d6, d16, q12, q13
    DIFF_LUMA_LT4_P0_Q0 d1, d3, d5, d7, d17, q12, q13
    vmax.s8 q8, q8, q15
    vmin.s8 q8, q8, q14

    vand.s8 q8, q8, q10
    vcge.s8 q14, q14, #0
    vand.s8 q8, q8, q14
    EXTRACT_DELTA_INTO_TWO_PART q8, q10
    vqadd.u8    q1, q1, q10
    vqsub.u8    q1, q1, q8
    vst1.u8 {d2}, [r0], r2
    vst1.u8 {d3}, [r1], r2
    vqsub.u8    q2, q2, q10
    vqadd.u8    q2, q2, q8
    vst1.u8 {d4}, [r0]
    vst1.u8 {d5}, [r1]

WELS_ASM_FUNC_END


WELS_ASM_FUNC_BEGIN DeblockChromaEq4V_neon
    vpush   {q4-q5}

    vdup.u8 q11, r3
    ldr         r3, [sp, #32]

    sub         r0, r0, r2  , lsl #1
    sub         r1, r1, r2, lsl #1
    vdup.u8 q9, r3
    vld1.u8 {d0}, [r0], r2      //  q0::p1
    vld1.u8 {d1}, [r1], r2
    vld1.u8 {d2}, [r0], r2      //  q1::p0
    vld1.u8 {d3}, [r1], r2
    vld1.u8 {d4}, [r0], r2      //  q2::q0
    vld1.u8 {d5}, [r1], r2
    vld1.u8 {d6}, [r0]              //  q3::q1
    vld1.u8 {d7}, [r1]

    sub         r0, r0, r2, lsl #1  //  pix = [-1*src_stride]
    sub         r1, r1, r2, lsl #1

    MASK_MATRIX q0, q1, q2, q3, q11, q9, q10

    vmov            q11, q10

    DIFF_CHROMA_EQ4_P0Q0        d0, d2, d4, d6, q4, q5, q8, d30, d0     // Cb::p0' q0'
    DIFF_CHROMA_EQ4_P0Q0        d1, d3, d5, d7, q12, q13, q14, d31, d1  // Cr::p0' q0'

    vbsl.u8 q10, q15, q1
    vst1.u8 {d20}, [r0], r2
    vst1.u8 {d21}, [r1], r2

    vbsl.u8 q11, q0, q2
    vst1.u8 {d22}, [r0]
    vst1.u8 {d23}, [r1]

    vpop    {q4-q5}
WELS_ASM_FUNC_END

WELS_ASM_FUNC_BEGIN DeblockChromaLt4H_neon

    vdup.u8 q11, r3
    ldr         r3, [sp, #0]

    sub         r0, r0, #2
    vdup.u8 q9, r3
    ldr         r3, [sp, #4]
    sub         r1, r1, #2
    vld1.s8 {d31}, [r3]

    LOAD_CHROMA_DATA_4  d0, d1, d2, d3, d4, d5, d6, d7, 0
    LOAD_CHROMA_DATA_4  d0, d1, d2, d3, d4, d5, d6, d7, 1
    LOAD_CHROMA_DATA_4  d0, d1, d2, d3, d4, d5, d6, d7, 2
    LOAD_CHROMA_DATA_4  d0, d1, d2, d3, d4, d5, d6, d7, 3
    LOAD_CHROMA_DATA_4  d0, d1, d2, d3, d4, d5, d6, d7, 4
    LOAD_CHROMA_DATA_4  d0, d1, d2, d3, d4, d5, d6, d7, 5
    LOAD_CHROMA_DATA_4  d0, d1, d2, d3, d4, d5, d6, d7, 6
    LOAD_CHROMA_DATA_4  d0, d1, d2, d3, d4, d5, d6, d7, 7
    vswp        q1, q2
    vswp        d1, d2
    vswp        d6, d5

    vmovl.u8    q14, d31
    vshl.u64    d29,d28,#8
    vorr        d28,d29
    vmov        d29, d28
    veor        q15, q15
    vsub.i8 q15,q15,q14

    MASK_MATRIX q0, q1, q2, q3, q11, q9, q10

    DIFF_LUMA_LT4_P0_Q0 d0, d2, d4, d6, d16, q12, q13
    DIFF_LUMA_LT4_P0_Q0 d1, d3, d5, d7, d17, q12, q13
    vmax.s8 q8, q8, q15
    vmin.s8 q8, q8, q14

    vand.s8 q8, q8, q10
    vcge.s8 q14, q14, #0
    vand.s8 q8, q8, q14
    EXTRACT_DELTA_INTO_TWO_PART q8, q10
    vqadd.u8    q1, q1, q10
    vqsub.u8    q1, q1, q8
    vqsub.u8    q2, q2, q10
    vqadd.u8    q2, q2, q8

    sub         r0, r0, r2, lsl #3
    sub         r1, r1, r2, lsl #3
    vswp        d1, d2
    vswp        d6, d5
    vswp        q1, q2

    STORE_CHROMA_DATA_4 d0, d1, d2, d3, d4, d5, d6, d7, 0
    STORE_CHROMA_DATA_4 d0, d1, d2, d3, d4, d5, d6, d7, 1
    STORE_CHROMA_DATA_4 d0, d1, d2, d3, d4, d5, d6, d7, 2
    STORE_CHROMA_DATA_4 d0, d1, d2, d3, d4, d5, d6, d7, 3
    STORE_CHROMA_DATA_4 d0, d1, d2, d3, d4, d5, d6, d7, 4
    STORE_CHROMA_DATA_4 d0, d1, d2, d3, d4, d5, d6, d7, 5
    STORE_CHROMA_DATA_4 d0, d1, d2, d3, d4, d5, d6, d7, 6
    STORE_CHROMA_DATA_4 d0, d1, d2, d3, d4, d5, d6, d7, 7

WELS_ASM_FUNC_END

WELS_ASM_FUNC_BEGIN DeblockChromaEq4H_neon
    vpush   {q4-q5}
    vdup.u8 q11, r3
    ldr         r3, [sp, #32]

    sub         r0, r0, #2
    sub         r1, r1, #2

    LOAD_CHROMA_DATA_4  d0, d1, d2, d3, d4, d5, d6, d7, 0
    LOAD_CHROMA_DATA_4  d0, d1, d2, d3, d4, d5, d6, d7, 1
    LOAD_CHROMA_DATA_4  d0, d1, d2, d3, d4, d5, d6, d7, 2
    LOAD_CHROMA_DATA_4  d0, d1, d2, d3, d4, d5, d6, d7, 3
    LOAD_CHROMA_DATA_4  d0, d1, d2, d3, d4, d5, d6, d7, 4
    LOAD_CHROMA_DATA_4  d0, d1, d2, d3, d4, d5, d6, d7, 5
    LOAD_CHROMA_DATA_4  d0, d1, d2, d3, d4, d5, d6, d7, 6
    LOAD_CHROMA_DATA_4  d0, d1, d2, d3, d4, d5, d6, d7, 7
    vswp        q1, q2
    vswp        d1, d2
    vswp        d6, d5

    vdup.u8 q9, r3
    MASK_MATRIX q0, q1, q2, q3, q11, q9, q10
    vmov            q11, q10

    DIFF_CHROMA_EQ4_P0Q0        d0, d2, d4, d6, q8, q9, q12, d8, d10
    DIFF_CHROMA_EQ4_P0Q0        d1, d3, d5, d7, q13, q14, q15, d9, d11

    vbsl.u8 q10, q4, q1
    vbsl.u8 q11, q5, q2
    sub         r0, r0, r2, lsl #3  //  pix: 0th row    [-2]
    sub         r1, r1, r2, lsl #3

    vmov        q1, q10
    vmov        q2, q11
    vswp        d1, d2
    vswp        d6, d5
    vswp        q1, q2
    //  Cb:d0d1d2d3, Cr:d4d5d6d7
    STORE_CHROMA_DATA_4 d0, d1, d2, d3, d4, d5, d6, d7, 0
    STORE_CHROMA_DATA_4 d0, d1, d2, d3, d4, d5, d6, d7, 1
    STORE_CHROMA_DATA_4 d0, d1, d2, d3, d4, d5, d6, d7, 2
    STORE_CHROMA_DATA_4 d0, d1, d2, d3, d4, d5, d6, d7, 3
    STORE_CHROMA_DATA_4 d0, d1, d2, d3, d4, d5, d6, d7, 4
    STORE_CHROMA_DATA_4 d0, d1, d2, d3, d4, d5, d6, d7, 5
    STORE_CHROMA_DATA_4 d0, d1, d2, d3, d4, d5, d6, d7, 6
    STORE_CHROMA_DATA_4 d0, d1, d2, d3, d4, d5, d6, d7, 7

    vpop    {q4-q5}
WELS_ASM_FUNC_END


WELS_ASM_FUNC_BEGIN WelsNonZeroCount_neon
    mov       r1, #1
    vdup.8    q2, r1
    vld1.64   {d0,d1,d2}, [r0]
    vmin.s8   q0, q0, q2
    vmin.s8   d2, d2, d4
    vst1.64   {d0,d1,d2}, [r0]
WELS_ASM_FUNC_END

.macro BS_NZC_CHECK  arg0, arg1, arg2, arg3, arg4
    vld1.8   {d0,d1}, [\arg0]
    /* Arrenge the input data --- TOP */
    ands     r6, \arg1, #2
    beq      bs_nzc_check_jump0

    sub      r6, \arg0, \arg2, lsl #4
    sub      r6, r6, \arg2, lsl #3
    add      r6, #12
    vld1.32  d3[1], [r6]

bs_nzc_check_jump0:
    vext.8   q1, q1, q0, #12
    vadd.u8  \arg3, q0, q1


    /* Arrenge the input data --- LEFT */
    ands     r6, \arg1, #1
    beq      bs_nzc_check_jump1

    sub      r6, \arg0, #21
    add      r7, r6, #4
    vld1.8   d3[4], [r6]
    add      r6, r7, #4
    vld1.8   d3[5], [r7]
    add      r7, r6, #4
    vld1.8   d3[6], [r6]
    vld1.8   d3[7], [r7]

bs_nzc_check_jump1:
    vzip.8   d0, d1
    vzip.8   d0, d1
    vext.8   q1, q1, q0, #12
    vadd.u8  \arg4, q0, q1
.endm

.macro BS_COMPARE_MV  arg0, arg1, arg2, arg3, arg4, arg5, arg6 //in: $0,$1(const),$2(const),$3(const),$4(const); out:$5, $6
    mov       r6, #4
    vabd.s16  q8, \arg0, \arg1
    vabd.s16  q9, \arg1, \arg2
    vdup.s16  \arg0, r6
    vabd.s16  q10, \arg2, \arg3
    vabd.s16  q11, \arg3, \arg4

    vcge.s16  q8, \arg0
    vcge.s16  q9, \arg0
    vcge.s16  q10, \arg0
    vcge.s16  q11, \arg0

    vpadd.i16 d16, d16, d17
    vpadd.i16 d17, d18, d19
    vpadd.i16 d18, d20, d21
    vpadd.i16 d19, d22, d23

    vaddhn.i16  \arg5, q8, q8
    vaddhn.i16  \arg6, q9, q9
.endm

.macro BS_MV_CHECK  arg0, arg1, arg2, arg3, arg4, arg5, arg6
    vldm   \arg0, {q0,q1,q2,q3}

    /* Arrenge the input data --- TOP */
    ands     r6, \arg1, #2
    beq      bs_mv_check_jump0

    sub      r6, \arg0, \arg2, lsl #6
    add      r6, #48
    vld1.8   {d8, d9}, [r6]

bs_mv_check_jump0:
    BS_COMPARE_MV  q4, q0, q1, q2, q3, \arg3, \arg4

    /* Arrenge the input data --- LEFT */
    ands     r6, \arg1, #1
    beq      bs_mv_check_jump1

    sub      r6, \arg0, #52
    add      r7, r6, #16
    vld1.32   d8[0], [r6]
    add      r6, r7, #16
    vld1.32   d8[1], [r7]
    add      r7, r6, #16
    vld1.32   d9[0], [r6]
    vld1.32   d9[1], [r7]

bs_mv_check_jump1:
    vzip.32   q0, q2
    vzip.32   q1, q3
    vzip.32   q0, q1
    vzip.32   q2, q3
    BS_COMPARE_MV  q4, q0, q1, q2, q3, \arg5, \arg6
.endm


WELS_ASM_FUNC_BEGIN DeblockingBSCalcEnc_neon

    stmdb sp!, {r5-r7}
    vpush {q4}

    ldr  r5, [sp, #28]  //Save BS to r5

    /* Checking the nzc status */
    BS_NZC_CHECK r0, r2, r3, q14, q15 //q14,q15 save the nzc status

    /* For checking bS[I] = 2 */
    mov      r6, #2
    vcgt.s8  q14, q14, #0
    vdup.u8  q0, r6
    vcgt.s8  q15, q15, #0

    vand.u8  q14, q14, q0 //q14 save the nzc check result all the time --- for dir is top
    vand.u8  q15, q15, q0 //q15 save the nzc check result all the time --- for dir is left

    /* Checking the mv status*/
    BS_MV_CHECK r1, r2, r3, d24, d25, d26, d27//q12, q13 save the mv status

    /* For checking bS[I] = 1 */
    mov      r6, #1
    vdup.u8  q0, r6

    vand.u8  q12, q12, q0 //q12 save the nzc check result all the time --- for dir is top
    vand.u8  q13, q13, q0 //q13 save the nzc check result all the time --- for dir is left


    /* Check bS[I] is '1' or '2' */
    vmax.u8 q1, q12, q14
    vmax.u8 q0, q13, q15

    //vstm r5, {q0, q1}
    vst1.32 {q0, q1}, [r5]
    vpop {q4}
    ldmia sp!, {r5-r7}
WELS_ASM_FUNC_END
#endif
