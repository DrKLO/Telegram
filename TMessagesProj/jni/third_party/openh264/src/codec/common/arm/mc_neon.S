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

.macro AVERAGE_TWO_8BITS arg0, arg1, arg2
//  {   // input:dst_d, src_d A and B; working: q13
    vaddl.u8    q13, \arg2, \arg1
    vrshrn.u16      \arg0, q13, #1
//  }
.endm

.macro FILTER_6TAG_8BITS arg0, arg1, arg2, arg3, arg4, arg5, arg6, arg7, arg8
//  {   // input:src[-2], src[-1], src[0], src[1], src[2], src[3], dst_d, multiplier a/b; working: q12, q13
    vaddl.u8    q12, \arg0, \arg5   //q12=src[-2]+src[3]
    vaddl.u8    q13, \arg2, \arg3   //src[0]+src[1]
    vmla.u16    q12, q13, \arg7 //q12 += 20*(src[0]+src[1]), 2 cycles
    vaddl.u8    q13, \arg1, \arg4   //src[-1]+src[2]
    vmls.s16    q12, q13, \arg8 //q12 -= 5*(src[-1]+src[2]), 2 cycles
    vqrshrun.s16        \arg6, q12, #5
//  }
.endm

.macro FILTER_SINGLE_TAG_8BITS arg0, arg1,arg2, arg3, arg4     // when width=17/9, used
//  {   // input: src_d{Y[0][1][2][3][4][5]X, the even of working_q2}
    vrev64.8    \arg2, \arg0                // X[5][4][3][2][1][0]O
    vaddl.u8    \arg3, \arg0, \arg2         // each 16bits, *[50][41][32][23][14][05]*
    vmul.s16    \arg0, \arg2, \arg1         // 0+1*[50]-5*[41]+20[32]
    vpadd.s16   \arg0, \arg0, \arg0
    vpadd.s16   \arg0, \arg0, \arg0
    vqrshrun.s16    \arg0, \arg4, #5
//  }
.endm

.macro FILTER_6TAG_8BITS_AVERAGE_WITH_0 arg0, arg1, arg2, arg3, arg4, arg5, arg6, arg7, arg8
//  {   // input:src[-2], src[-1], src[0], src[1], src[2], src[3], dst_d, multiplier a/b; working: q12, q13
    vaddl.u8    q12, \arg0, \arg5   //q12=src[-2]+src[3]
    vaddl.u8    q13, \arg2, \arg3   //src[0]+src[1]
    vmla.u16    q12, q13, \arg7 //q12 += 20*(src[0]+src[1]), 2 cycles
    vaddl.u8    q13, \arg1, \arg4   //src[-1]+src[2]
    vmls.s16    q12, q13, \arg8 //q12 -= 5*(src[-1]+src[2]), 2 cycles
    vqrshrun.s16        \arg6, q12, #5
    vaddl.u8    q13, \arg2, \arg6
    vrshrn.u16      \arg6, q13, #1
//  }
.endm

.macro FILTER_6TAG_8BITS_AVERAGE_WITH_1 arg0, arg1, arg2, arg3, arg4, arg5, arg6, arg7, arg8
//  {   // input:src[-2], src[-1], src[0], src[1], src[2], src[3], dst_d, multiplier a/b; working: q12, q13
    vaddl.u8    q12, \arg0, \arg5   //q12=src[-2]+src[3]
    vaddl.u8    q13, \arg2, \arg3   //src[0]+src[1]
    vmla.u16    q12, q13, \arg7 //q12 += 20*(src[0]+src[1]), 2 cycles
    vaddl.u8    q13, \arg1, \arg4   //src[-1]+src[2]
    vmls.s16    q12, q13, \arg8 //q12 -= 5*(src[-1]+src[2]), 2 cycles
    vqrshrun.s16        \arg6, q12, #5
    vaddl.u8    q13, \arg3, \arg6
    vrshrn.u16      \arg6, q13, #1
//  }
.endm

.macro FILTER_6TAG_8BITS_TO_16BITS arg0, arg1, arg2, arg3, arg4, arg5, arg6, arg7, arg8
//  {   // input:d_src[-2], d_src[-1], d_src[0], d_src[1], d_src[2], d_src[3], dst_q, multiplier a/b; working:q13
    vaddl.u8    \arg6, \arg0, \arg5     //dst_q=src[-2]+src[3]
    vaddl.u8    q13, \arg2, \arg3   //src[0]+src[1]
    vmla.u16    \arg6, q13, \arg7   //dst_q += 20*(src[0]+src[1]), 2 cycles
    vaddl.u8    q13, \arg1, \arg4   //src[-1]+src[2]
    vmls.s16    \arg6, q13, \arg8   //dst_q -= 5*(src[-1]+src[2]), 2 cycles
//  }
.endm

.macro FILTER_3_IN_16BITS_TO_8BITS arg0, arg1, arg2, arg3
//  {   // input:a, b, c, dst_d;
    vsub.s16    \arg0, \arg0, \arg1         //a-b
    vshr.s16    \arg0, \arg0, #2            //(a-b)/4
    vsub.s16    \arg0, \arg0, \arg1         //(a-b)/4-b
    vadd.s16    \arg0, \arg0, \arg2         //(a-b)/4-b+c
    vshr.s16    \arg0, \arg0, #2            //((a-b)/4-b+c)/4
    vadd.s16    \arg0, \arg0, \arg2         //((a-b)/4-b+c)/4+c = (a-5*b+20*c)/16
    vqrshrun.s16    \arg3, \arg0, #6        //(+32)>>6
//  }
.endm

.macro UNPACK_2_16BITS_TO_ABC arg0, arg1, arg2, arg3, arg4
//  {   // input:q_src[-2:5], q_src[6:13](avail 8+5)/q_src[6:**](avail 4+5), dst_a, dst_b, dst_c;
    vext.16 \arg4, \arg0, \arg1, #2     //src[0]
    vext.16 \arg3, \arg0, \arg1, #3     //src[1]
    vadd.s16    \arg4, \arg3                    //c=src[0]+src[1]

    vext.16 \arg3, \arg0, \arg1, #1     //src[-1]
    vext.16 \arg2, \arg0, \arg1, #4     //src[2]
    vadd.s16    \arg3,\arg2                 //b=src[-1]+src[2]

    vext.16 \arg2, \arg0, \arg1, #5     //src[3]
    vadd.s16    \arg2, \arg0                    //a=src[-2]+src[3]
//  }
.endm

.macro UNPACK_1_IN_8x16BITS_TO_8BITS arg0, arg1,arg2, arg3
//  {   // each 16bits; input: d_dst, d_src[0:3] (even), d_src[4:5]+%% (odd)
    vext.16 \arg3, \arg3, \arg3, #7 // 0x????, [0][1][2][3][4][5]
    vrev64.16   \arg1, \arg1
    vadd.u16    \arg2, \arg1                // C[2+3],B[1+4],A[0+5]
    vshr.s64    \arg1, \arg2, #16
    vshr.s64    \arg0, \arg2, #32       // Output: C \arg2, B \arg1, A \arg0

    vsub.s16    \arg0, \arg0, \arg1         //a-b
    vshr.s16    \arg0, \arg0, #2            //(a-b)/4
    vsub.s16    \arg0, \arg0, \arg1         //(a-b)/4-b
    vadd.s16    \arg0, \arg0, \arg2         //(a-b)/4-b+c
    vshr.s16    \arg0, \arg0, #2            //((a-b)/4-b+c)/4
    vadd.s16    \arg1, \arg0, \arg2         //((a-b)/4-b+c)/4+c = (a-5*b+20*c)/16
    vqrshrun.s16    \arg0, \arg3, #6        //(+32)>>6
//  }
.endm

WELS_ASM_FUNC_BEGIN McHorVer20WidthEq16_neon
    push        {r4}
    ldr         r4, [sp, #4]

    sub         r0, #2
    vmov.u16    q14, #0x0014                // 20
    vshr.u16    q15, q14, #2                // 5

w16_h_mc_luma_loop:
    vld1.u8 {d0,d1,d2}, [r0], r1    //only use 21(16+5); q0=src[-2]
    pld         [r0]
    pld         [r0, #16]

    vext.8      q2, q0, q1, #1      //q2=src[-1]
    vext.8      q3, q0, q1, #2      //q3=src[0]
    vext.8      q8, q0, q1, #3      //q8=src[1]
    vext.8      q9, q0, q1, #4      //q9=src[2]
    vext.8      q10, q0, q1, #5     //q10=src[3]

    FILTER_6TAG_8BITS   d0, d4, d6, d16, d18, d20, d2, q14, q15

    FILTER_6TAG_8BITS   d1, d5, d7, d17, d19, d21, d3, q14, q15

    sub     r4, #1
    vst1.u8 {d2, d3}, [r2], r3      //write 16Byte

    cmp     r4, #0
    bne     w16_h_mc_luma_loop
    pop     {r4}
WELS_ASM_FUNC_END


WELS_ASM_FUNC_BEGIN McHorVer20WidthEq8_neon
    push        {r4}
    ldr         r4, [sp, #4]

    sub         r0, #2
    vmov.u16    q14, #0x0014                // 20
    vshr.u16    q15, q14, #2                // 5

w8_h_mc_luma_loop:
    vld1.u8 {d0,d1}, [r0], r1   //only use 13(8+5); q0=src[-2]
    pld         [r0]

    vext.8      d2, d0, d1, #1      //d2=src[-1]
    vext.8      d3, d0, d1, #2      //d3=src[0]
    vext.8      d4, d0, d1, #3      //d4=src[1]
    vext.8      d5, d0, d1, #4      //d5=src[2]
    vext.8      d6, d0, d1, #5      //d6=src[3]

    FILTER_6TAG_8BITS   d0, d2, d3, d4, d5, d6, d1, q14, q15

    sub     r4, #1
    vst1.u8 {d1}, [r2], r3

    cmp     r4, #0
    bne     w8_h_mc_luma_loop
    pop     {r4}
WELS_ASM_FUNC_END


WELS_ASM_FUNC_BEGIN McHorVer20WidthEq4_neon
    push        {r4, r5, r6}
    ldr         r6, [sp, #12]

    sub         r0, #2
    vmov.u16    q14, #0x0014                // 20
    vshr.u16    q15, q14, #2                // 5

w4_h_mc_luma_loop:
    vld1.u8 {d0, d1}, [r0], r1  //only use 9(4+5);d0: 1st row src[-2:5]
    pld         [r0]
    vld1.u8 {d2, d3}, [r0], r1  //d2: 2nd row src[-2:5]
    pld         [r0]

    vext.8      d4, d0, d1, #1      //d4: 1st row src[-1:6]
    vext.8      d5, d2, d3, #1      //d5: 2nd row src[-1:6]
    vext.8      q3, q2, q2, #1      //src[0:6 *]
    vext.8      q8, q2, q2, #2      //src[1:6 * *]

    vtrn.32 q3, q8                  //q3::d6:1st row [0:3]+[1:4]; d7:2nd row [0:3]+[1:4]
    vtrn.32 d6, d7                  //d6:[0:3]; d7[1:4]
    vtrn.32     d0, d2              //d0:[-2:1]; d2[2:5]
    vtrn.32     d4, d5              //d4:[-1:2]; d5[3:6]

    FILTER_6TAG_8BITS   d0, d4, d6, d7, d2, d5, d1, q14, q15

    vmov        r4, r5, d1
    str r4, [r2], r3
    str r5, [r2], r3

    sub     r6, #2
    cmp     r6, #0
    bne     w4_h_mc_luma_loop

    pop     {r4, r5, r6}
WELS_ASM_FUNC_END


WELS_ASM_FUNC_BEGIN McHorVer10WidthEq16_neon
    push        {r4}
    ldr         r4, [sp, #4]

    sub         r0, #2
    vmov.u16    q14, #0x0014                // 20
    vshr.u16    q15, q14, #2                // 5

w16_xy_10_mc_luma_loop:
    vld1.u8 {d0,d1,d2}, [r0], r1    //only use 21(16+5); q0=src[-2]
    pld         [r0]
    pld         [r0, #16]

    vext.8      q2, q0, q1, #1      //q2=src[-1]
    vext.8      q3, q0, q1, #2      //q3=src[0]
    vext.8      q8, q0, q1, #3      //q8=src[1]
    vext.8      q9, q0, q1, #4      //q9=src[2]
    vext.8      q10, q0, q1, #5     //q10=src[3]

    FILTER_6TAG_8BITS_AVERAGE_WITH_0    d0, d4, d6, d16, d18, d20, d2, q14, q15

    FILTER_6TAG_8BITS_AVERAGE_WITH_0    d1, d5, d7, d17, d19, d21, d3, q14, q15

    sub     r4, #1
    vst1.u8 {d2, d3}, [r2], r3      //write 16Byte

    cmp     r4, #0
    bne     w16_xy_10_mc_luma_loop
    pop     {r4}
WELS_ASM_FUNC_END


WELS_ASM_FUNC_BEGIN McHorVer10WidthEq8_neon
    push        {r4}
    ldr         r4, [sp, #4]

    sub         r0, #2
    vmov.u16    q14, #0x0014                // 20
    vshr.u16    q15, q14, #2                // 5

w8_xy_10_mc_luma_loop:
    vld1.u8 {d0,d1}, [r0], r1   //only use 13(8+5); q0=src[-2]
    pld         [r0]

    vext.8      d2, d0, d1, #1      //d2=src[-1]
    vext.8      d3, d0, d1, #2      //d3=src[0]
    vext.8      d4, d0, d1, #3      //d4=src[1]
    vext.8      d5, d0, d1, #4      //d5=src[2]
    vext.8      d6, d0, d1, #5      //d6=src[3]

    FILTER_6TAG_8BITS_AVERAGE_WITH_0    d0, d2, d3, d4, d5, d6, d1, q14, q15

    sub     r4, #1
    vst1.u8 {d1}, [r2], r3

    cmp     r4, #0
    bne     w8_xy_10_mc_luma_loop
    pop     {r4}
WELS_ASM_FUNC_END


WELS_ASM_FUNC_BEGIN McHorVer10WidthEq4_neon
    push        {r4, r5, r6}
    ldr         r6, [sp, #12]

    sub         r0, #2
    vmov.u16    q14, #0x0014                // 20
    vshr.u16    q15, q14, #2                // 5

w4_xy_10_mc_luma_loop:
    vld1.u8 {d0, d1}, [r0], r1  //only use 9(4+5);d0: 1st row src[-2:5]
    pld         [r0]
    vld1.u8 {d2, d3}, [r0], r1  //d2: 2nd row src[-2:5]
    pld         [r0]

    vext.8      d4, d0, d1, #1      //d4: 1st row src[-1:6]
    vext.8      d5, d2, d3, #1      //d5: 2nd row src[-1:6]
    vext.8      q3, q2, q2, #1      //src[0:6 *]
    vext.8      q8, q2, q2, #2      //src[1:6 * *]

    vtrn.32 q3, q8                  //q3::d6:1st row [0:3]+[1:4]; d7:2nd row [0:3]+[1:4]
    vtrn.32 d6, d7                  //d6:[0:3]; d7[1:4]
    vtrn.32     d0, d2              //d0:[-2:1]; d2[2:5]
    vtrn.32     d4, d5              //d4:[-1:2]; d5[3:6]

    FILTER_6TAG_8BITS_AVERAGE_WITH_0    d0, d4, d6, d7, d2, d5, d1, q14, q15

    vmov        r4, r5, d1
    str r4, [r2], r3
    str r5, [r2], r3

    sub     r6, #2
    cmp     r6, #0
    bne     w4_xy_10_mc_luma_loop

    pop     {r4, r5, r6}
WELS_ASM_FUNC_END


WELS_ASM_FUNC_BEGIN McHorVer30WidthEq16_neon
    push        {r4}
    ldr         r4, [sp, #4]

    sub         r0, #2
    vmov.u16    q14, #0x0014                // 20
    vshr.u16    q15, q14, #2                // 5

w16_xy_30_mc_luma_loop:
    vld1.u8 {d0,d1,d2}, [r0], r1    //only use 21(16+5); q0=src[-2]
    pld         [r0]
    pld         [r0, #16]

    vext.8      q2, q0, q1, #1      //q2=src[-1]
    vext.8      q3, q0, q1, #2      //q3=src[0]
    vext.8      q8, q0, q1, #3      //q8=src[1]
    vext.8      q9, q0, q1, #4      //q9=src[2]
    vext.8      q10, q0, q1, #5     //q10=src[3]

    FILTER_6TAG_8BITS_AVERAGE_WITH_1    d0, d4, d6, d16, d18, d20, d2, q14, q15

    FILTER_6TAG_8BITS_AVERAGE_WITH_1    d1, d5, d7, d17, d19, d21, d3, q14, q15

    sub     r4, #1
    vst1.u8 {d2, d3}, [r2], r3      //write 16Byte

    cmp     r4, #0
    bne     w16_xy_30_mc_luma_loop
    pop     {r4}
WELS_ASM_FUNC_END


WELS_ASM_FUNC_BEGIN McHorVer30WidthEq8_neon
    push        {r4}
    ldr         r4, [sp, #4]

    sub         r0, #2
    vmov.u16    q14, #0x0014                // 20
    vshr.u16    q15, q14, #2                // 5

w8_xy_30_mc_luma_loop:
    vld1.u8 {d0,d1}, [r0], r1   //only use 13(8+5); q0=src[-2]
    pld         [r0]

    vext.8      d2, d0, d1, #1      //d2=src[-1]
    vext.8      d3, d0, d1, #2      //d3=src[0]
    vext.8      d4, d0, d1, #3      //d4=src[1]
    vext.8      d5, d0, d1, #4      //d5=src[2]
    vext.8      d6, d0, d1, #5      //d6=src[3]

    FILTER_6TAG_8BITS_AVERAGE_WITH_1    d0, d2, d3, d4, d5, d6, d1, q14, q15

    sub     r4, #1
    vst1.u8 {d1}, [r2], r3

    cmp     r4, #0
    bne     w8_xy_30_mc_luma_loop
    pop     {r4}
WELS_ASM_FUNC_END


WELS_ASM_FUNC_BEGIN McHorVer30WidthEq4_neon
    push        {r4, r5, r6}
    ldr         r6, [sp, #12]

    sub         r0, #2
    vmov.u16    q14, #0x0014                // 20
    vshr.u16    q15, q14, #2                // 5

w4_xy_30_mc_luma_loop:
    vld1.u8 {d0, d1}, [r0], r1  //only use 9(4+5);d0: 1st row src[-2:5]
    pld         [r0]
    vld1.u8 {d2, d3}, [r0], r1  //d2: 2nd row src[-2:5]
    pld         [r0]

    vext.8      d4, d0, d1, #1      //d4: 1st row src[-1:6]
    vext.8      d5, d2, d3, #1      //d5: 2nd row src[-1:6]
    vext.8      q3, q2, q2, #1      //src[0:6 *]
    vext.8      q8, q2, q2, #2      //src[1:6 * *]

    vtrn.32 q3, q8                  //q3::d6:1st row [0:3]+[1:4]; d7:2nd row [0:3]+[1:4]
    vtrn.32 d6, d7                  //d6:[0:3]; d7[1:4]
    vtrn.32     d0, d2              //d0:[-2:1]; d2[2:5]
    vtrn.32     d4, d5              //d4:[-1:2]; d5[3:6]

    FILTER_6TAG_8BITS_AVERAGE_WITH_1    d0, d4, d6, d7, d2, d5, d1, q14, q15

    vmov        r4, r5, d1
    str r4, [r2], r3
    str r5, [r2], r3

    sub     r6, #2
    cmp     r6, #0
    bne     w4_xy_30_mc_luma_loop

    pop     {r4, r5, r6}
WELS_ASM_FUNC_END


WELS_ASM_FUNC_BEGIN McHorVer01WidthEq16_neon
    push        {r4}
    ldr         r4, [sp, #4]

    sub         r0, r0, r1, lsl #1      //src[-2*src_stride]
    pld         [r0]
    pld         [r0, r1]
    vmov.u16    q14, #0x0014            // 20
    vld1.u8 {q0}, [r0], r1      //q0=src[-2]
    vld1.u8 {q1}, [r0], r1      //q1=src[-1]

    pld         [r0]
    pld         [r0, r1]
    vshr.u16    q15, q14, #2            // 5
    vld1.u8 {q2}, [r0], r1      //q2=src[0]
    vld1.u8 {q3}, [r0], r1      //q3=src[1]
    vld1.u8 {q8}, [r0], r1      //q8=src[2]

w16_xy_01_luma_loop:

    vld1.u8 {q9}, [r0], r1      //q9=src[3]

    FILTER_6TAG_8BITS_AVERAGE_WITH_0    d0, d2, d4, d6, d16, d18, d20, q14, q15
    pld         [r0]
    FILTER_6TAG_8BITS_AVERAGE_WITH_0    d1, d3, d5, d7, d17, d19, d21, q14, q15
    vld1.u8 {q0}, [r0], r1      //read 2nd row
    vst1.u8 {q10}, [r2], r3         //write 1st 16Byte

    FILTER_6TAG_8BITS_AVERAGE_WITH_0    d2, d4, d6, d16, d18, d0, d20, q14, q15
    pld         [r0]
    FILTER_6TAG_8BITS_AVERAGE_WITH_0    d3, d5, d7, d17, d19, d1, d21, q14, q15
    vld1.u8 {q1}, [r0], r1      //read 3rd row
    vst1.u8 {q10}, [r2], r3         //write 2nd 16Byte

    FILTER_6TAG_8BITS_AVERAGE_WITH_0    d4, d6, d16, d18, d0, d2, d20, q14, q15
    pld         [r0]
    FILTER_6TAG_8BITS_AVERAGE_WITH_0    d5, d7, d17, d19, d1, d3, d21, q14, q15
    vld1.u8 {q2}, [r0], r1      //read 4th row
    vst1.u8 {q10}, [r2], r3         //write 3rd 16Byte

    FILTER_6TAG_8BITS_AVERAGE_WITH_0    d6, d16, d18, d0, d2, d4, d20, q14, q15
    pld         [r0]
    FILTER_6TAG_8BITS_AVERAGE_WITH_0    d7, d17, d19, d1, d3, d5, d21, q14, q15
    vld1.u8 {q3}, [r0], r1      //read 5th row
    vst1.u8 {q10}, [r2], r3         //write 4th 16Byte

    FILTER_6TAG_8BITS_AVERAGE_WITH_0    d16, d18, d0, d2, d4, d6, d20, q14, q15
    pld         [r0]
    FILTER_6TAG_8BITS_AVERAGE_WITH_0    d17, d19, d1, d3, d5, d7, d21, q14, q15
    vld1.u8 {q8}, [r0], r1      //read 6th row
    vst1.u8 {q10}, [r2], r3         //write 5th 16Byte

    FILTER_6TAG_8BITS_AVERAGE_WITH_0    d18, d0, d2, d4, d6, d16, d20, q14, q15
    pld         [r0]
    FILTER_6TAG_8BITS_AVERAGE_WITH_0    d19, d1, d3, d5, d7, d17, d21, q14, q15
    vld1.u8 {q9}, [r0], r1      //read 7th row
    vst1.u8 {q10}, [r2], r3         //write 6th 16Byte

    FILTER_6TAG_8BITS_AVERAGE_WITH_0    d0, d2, d4, d6, d16, d18, d20, q14, q15
    pld         [r0]
    FILTER_6TAG_8BITS_AVERAGE_WITH_0    d1, d3, d5, d7, d17, d19, d21, q14, q15
    vld1.u8 {q0}, [r0], r1      //read 8th row
    vst1.u8 {q10}, [r2], r3         //write 7th 16Byte

    FILTER_6TAG_8BITS_AVERAGE_WITH_0    d2, d4, d6, d16, d18, d0, d20, q14, q15
    pld         [r0]
    FILTER_6TAG_8BITS_AVERAGE_WITH_0    d3, d5, d7, d17, d19, d1, d21, q14, q15
    vst1.u8 {q10}, [r2], r3         //write 8th 16Byte

    //q2, q3, q4, q5, q0 --> q0~q4
    vswp    q0, q8
    vswp    q0, q2
    vmov    q1, q3
    vmov    q3, q9                      //q0~q4

    sub     r4, #8
    cmp     r4, #0
    bne     w16_xy_01_luma_loop
    pop     {r4}
WELS_ASM_FUNC_END


WELS_ASM_FUNC_BEGIN McHorVer01WidthEq8_neon
    push        {r4}
    ldr         r4, [sp, #4]

    sub         r0, r0, r1, lsl #1      //src[-2*src_stride]
    pld         [r0]
    pld         [r0, r1]
    vmov.u16    q14, #0x0014            // 20
    vld1.u8 {d0}, [r0], r1      //d0=src[-2]
    vld1.u8 {d1}, [r0], r1      //d1=src[-1]

    pld         [r0]
    pld         [r0, r1]
    vshr.u16    q15, q14, #2            // 5
    vld1.u8 {d2}, [r0], r1      //d2=src[0]
    vld1.u8 {d3}, [r0], r1      //d3=src[1]

    vld1.u8 {d4}, [r0], r1      //d4=src[2]
    vld1.u8 {d5}, [r0], r1      //d5=src[3]

w8_xy_01_mc_luma_loop:

    pld         [r0]
    FILTER_6TAG_8BITS_AVERAGE_WITH_0    d0, d1, d2, d3, d4, d5, d16, q14, q15
    vld1.u8 {d0}, [r0], r1      //read 2nd row
    vst1.u8 {d16}, [r2], r3     //write 1st 8Byte

    pld         [r0]
    FILTER_6TAG_8BITS_AVERAGE_WITH_0    d1, d2, d3, d4, d5, d0, d16, q14, q15
    vld1.u8 {d1}, [r0], r1      //read 3rd row
    vst1.u8 {d16}, [r2], r3     //write 2nd 8Byte

    pld         [r0]
    FILTER_6TAG_8BITS_AVERAGE_WITH_0    d2, d3, d4, d5, d0, d1, d16, q14, q15
    vld1.u8 {d2}, [r0], r1      //read 4th row
    vst1.u8 {d16}, [r2], r3     //write 3rd 8Byte

    pld         [r0]
    FILTER_6TAG_8BITS_AVERAGE_WITH_0    d3, d4, d5, d0, d1, d2, d16, q14, q15
    vld1.u8 {d3}, [r0], r1      //read 5th row
    vst1.u8 {d16}, [r2], r3     //write 4th 8Byte

    //d4, d5, d0, d1, d2, d3 --> d0, d1, d2, d3, d4, d5
    vswp    q0, q2
    vswp    q1, q2

    sub     r4, #4
    cmp     r4, #0
    bne     w8_xy_01_mc_luma_loop

    pop     {r4}
WELS_ASM_FUNC_END


WELS_ASM_FUNC_BEGIN McHorVer01WidthEq4_neon
    push        {r4, r5, r6, r7}
    sub         r0, r0, r1, lsl #1      //src[-2*src_stride]
    pld         [r0]
    pld         [r0, r1]
    vmov.u16    q14, #0x0014            // 20
    ldr     r4, [r0], r1        //r4=src[-2]
    ldr     r5, [r0], r1        //r5=src[-1]

    pld         [r0]
    pld         [r0, r1]
    vshr.u16    q15, q14, #2            // 5
    ldr     r6, [r0], r1        //r6=src[0]
    ldr     r7, [r0], r1        //r7=src[1]

    vmov        d0, r4, r5
    vmov        d1, r5, r6
    vmov        d2, r6, r7

    ldr     r4, [r0], r1        //r4=src[2]
    vmov        d3, r7, r4
    ldr         r7, [sp, #16]

w4_xy_01_mc_luma_loop:

//  pld         [r0]
    //using reserving r4
    ldr     r5, [r0], r1        //r5=src[3]
    ldr     r6, [r0], r1        //r6=src[0]
    vmov        d4, r4, r5
    vmov        d5, r5, r6          //reserved r6

    FILTER_6TAG_8BITS_AVERAGE_WITH_0    d0, d1, d2, d3, d4, d5, d16, q14, q15
    vmov        r4, r5, d16
    str r4, [r2], r3            //write 1st 4Byte
    str r5, [r2], r3            //write 2nd 4Byte

    ldr     r5, [r0], r1        //r5=src[1]
    ldr     r4, [r0], r1        //r4=src[2]
    vmov        d0, r6, r5
    vmov        d1, r5, r4          //reserved r4

    FILTER_6TAG_8BITS_AVERAGE_WITH_0    d2, d3, d4, d5, d0, d1, d16, q14, q15
    vmov        r5, r6, d16
    str r5, [r2], r3            //write 3rd 4Byte
    str r6, [r2], r3            //write 4th 4Byte

    //d4, d5, d0, d1 --> d0, d1, d2, d3
    vmov    q1, q0
    vmov    q0, q2

    sub     r7, #4
    cmp     r7, #0
    bne     w4_xy_01_mc_luma_loop

    pop     {r4, r5, r6, r7}
WELS_ASM_FUNC_END


WELS_ASM_FUNC_BEGIN McHorVer03WidthEq16_neon
    push        {r4}
    ldr         r4, [sp, #4]

    sub         r0, r0, r1, lsl #1      //src[-2*src_stride]
    pld         [r0]
    pld         [r0, r1]
    vmov.u16    q14, #0x0014            // 20
    vld1.u8 {q0}, [r0], r1      //q0=src[-2]
    vld1.u8 {q1}, [r0], r1      //q1=src[-1]

    pld         [r0]
    pld         [r0, r1]
    vshr.u16    q15, q14, #2            // 5
    vld1.u8 {q2}, [r0], r1      //q2=src[0]
    vld1.u8 {q3}, [r0], r1      //q3=src[1]
    vld1.u8 {q8}, [r0], r1      //q8=src[2]

w16_xy_03_luma_loop:

    vld1.u8 {q9}, [r0], r1      //q9=src[3]

    FILTER_6TAG_8BITS_AVERAGE_WITH_1    d0, d2, d4, d6, d16, d18, d20, q14, q15
    pld         [r0]
    FILTER_6TAG_8BITS_AVERAGE_WITH_1    d1, d3, d5, d7, d17, d19, d21, q14, q15
    vld1.u8 {q0}, [r0], r1      //read 2nd row
    vst1.u8 {q10}, [r2], r3         //write 1st 16Byte

    FILTER_6TAG_8BITS_AVERAGE_WITH_1    d2, d4, d6, d16, d18, d0, d20, q14, q15
    pld         [r0]
    FILTER_6TAG_8BITS_AVERAGE_WITH_1    d3, d5, d7, d17, d19, d1, d21, q14, q15
    vld1.u8 {q1}, [r0], r1      //read 3rd row
    vst1.u8 {q10}, [r2], r3         //write 2nd 16Byte

    FILTER_6TAG_8BITS_AVERAGE_WITH_1    d4, d6, d16, d18, d0, d2, d20, q14, q15
    pld         [r0]
    FILTER_6TAG_8BITS_AVERAGE_WITH_1    d5, d7, d17, d19, d1, d3, d21, q14, q15
    vld1.u8 {q2}, [r0], r1      //read 4th row
    vst1.u8 {q10}, [r2], r3         //write 3rd 16Byte

    FILTER_6TAG_8BITS_AVERAGE_WITH_1    d6, d16, d18, d0, d2, d4, d20, q14, q15
    pld         [r0]
    FILTER_6TAG_8BITS_AVERAGE_WITH_1    d7, d17, d19, d1, d3, d5, d21, q14, q15
    vld1.u8 {q3}, [r0], r1      //read 5th row
    vst1.u8 {q10}, [r2], r3         //write 4th 16Byte

    FILTER_6TAG_8BITS_AVERAGE_WITH_1    d16, d18, d0, d2, d4, d6, d20, q14, q15
    pld         [r0]
    FILTER_6TAG_8BITS_AVERAGE_WITH_1    d17, d19, d1, d3, d5, d7, d21, q14, q15
    vld1.u8 {q8}, [r0], r1      //read 6th row
    vst1.u8 {q10}, [r2], r3         //write 5th 16Byte

    FILTER_6TAG_8BITS_AVERAGE_WITH_1    d18, d0, d2, d4, d6, d16, d20, q14, q15
    pld         [r0]
    FILTER_6TAG_8BITS_AVERAGE_WITH_1    d19, d1, d3, d5, d7, d17, d21, q14, q15
    vld1.u8 {q9}, [r0], r1      //read 7th row
    vst1.u8 {q10}, [r2], r3         //write 6th 16Byte

    FILTER_6TAG_8BITS_AVERAGE_WITH_1    d0, d2, d4, d6, d16, d18, d20, q14, q15
    pld         [r0]
    FILTER_6TAG_8BITS_AVERAGE_WITH_1    d1, d3, d5, d7, d17, d19, d21, q14, q15
    vld1.u8 {q0}, [r0], r1      //read 8th row
    vst1.u8 {q10}, [r2], r3         //write 7th 16Byte

    FILTER_6TAG_8BITS_AVERAGE_WITH_1    d2, d4, d6, d16, d18, d0, d20, q14, q15
    pld         [r0]
    FILTER_6TAG_8BITS_AVERAGE_WITH_1    d3, d5, d7, d17, d19, d1, d21, q14, q15
    vst1.u8 {q10}, [r2], r3         //write 8th 16Byte

    //q2, q3, q8, q9, q0 --> q0~q8
    vswp    q0, q8
    vswp    q0, q2
    vmov    q1, q3
    vmov    q3, q9                      //q0~q8

    sub     r4, #8
    cmp     r4, #0
    bne     w16_xy_03_luma_loop
    pop     {r4}
WELS_ASM_FUNC_END


WELS_ASM_FUNC_BEGIN McHorVer03WidthEq8_neon
    push        {r4}
    ldr         r4, [sp, #4]

    sub         r0, r0, r1, lsl #1      //src[-2*src_stride]
    pld         [r0]
    pld         [r0, r1]
    vmov.u16    q14, #0x0014            // 20
    vld1.u8 {d0}, [r0], r1      //d0=src[-2]
    vld1.u8 {d1}, [r0], r1      //d1=src[-1]

    pld         [r0]
    pld         [r0, r1]
    vshr.u16    q15, q14, #2            // 5
    vld1.u8 {d2}, [r0], r1      //d2=src[0]
    vld1.u8 {d3}, [r0], r1      //d3=src[1]

    vld1.u8 {d4}, [r0], r1      //d4=src[2]
    vld1.u8 {d5}, [r0], r1      //d5=src[3]

w8_xy_03_mc_luma_loop:

    pld         [r0]
    FILTER_6TAG_8BITS_AVERAGE_WITH_1    d0, d1, d2, d3, d4, d5, d16, q14, q15
    vld1.u8 {d0}, [r0], r1      //read 2nd row
    vst1.u8 {d16}, [r2], r3     //write 1st 8Byte

    pld         [r0]
    FILTER_6TAG_8BITS_AVERAGE_WITH_1    d1, d2, d3, d4, d5, d0, d16, q14, q15
    vld1.u8 {d1}, [r0], r1      //read 3rd row
    vst1.u8 {d16}, [r2], r3     //write 2nd 8Byte

    pld         [r0]
    FILTER_6TAG_8BITS_AVERAGE_WITH_1    d2, d3, d4, d5, d0, d1, d16, q14, q15
    vld1.u8 {d2}, [r0], r1      //read 4th row
    vst1.u8 {d16}, [r2], r3     //write 3rd 8Byte

    pld         [r0]
    FILTER_6TAG_8BITS_AVERAGE_WITH_1    d3, d4, d5, d0, d1, d2, d16, q14, q15
    vld1.u8 {d3}, [r0], r1      //read 5th row
    vst1.u8 {d16}, [r2], r3     //write 4th 8Byte

    //d4, d5, d0, d1, d2, d3 --> d0, d1, d2, d3, d4, d5
    vswp    q0, q2
    vswp    q1, q2

    sub     r4, #4
    cmp     r4, #0
    bne     w8_xy_03_mc_luma_loop

    pop     {r4}
WELS_ASM_FUNC_END


WELS_ASM_FUNC_BEGIN McHorVer03WidthEq4_neon
    push        {r4, r5, r6, r7}
    sub         r0, r0, r1, lsl #1      //src[-2*src_stride]
    pld         [r0]
    pld         [r0, r1]
    vmov.u16    q14, #0x0014            // 20
    ldr     r4, [r0], r1        //r4=src[-2]
    ldr     r5, [r0], r1        //r5=src[-1]

    pld         [r0]
    pld         [r0, r1]
    vshr.u16    q15, q14, #2            // 5
    ldr     r6, [r0], r1        //r6=src[0]
    ldr     r7, [r0], r1        //r7=src[1]

    vmov        d0, r4, r5
    vmov        d1, r5, r6
    vmov        d2, r6, r7

    ldr     r4, [r0], r1        //r4=src[2]
    vmov        d3, r7, r4
    ldr         r7, [sp, #16]

w4_xy_03_mc_luma_loop:

//  pld         [r0]
    //using reserving r4
    ldr     r5, [r0], r1        //r5=src[3]
    ldr     r6, [r0], r1        //r6=src[0]
    vmov        d4, r4, r5
    vmov        d5, r5, r6          //reserved r6

    FILTER_6TAG_8BITS_AVERAGE_WITH_1    d0, d1, d2, d3, d4, d5, d16, q14, q15
    vmov        r4, r5, d16
    str r4, [r2], r3            //write 1st 4Byte
    str r5, [r2], r3            //write 2nd 4Byte

    ldr     r5, [r0], r1        //r5=src[1]
    ldr     r4, [r0], r1        //r4=src[2]
    vmov        d0, r6, r5
    vmov        d1, r5, r4          //reserved r4

    FILTER_6TAG_8BITS_AVERAGE_WITH_1    d2, d3, d4, d5, d0, d1, d16, q14, q15
    vmov        r5, r6, d16
    str r5, [r2], r3            //write 3rd 4Byte
    str r6, [r2], r3            //write 4th 4Byte

    //d4, d5, d0, d1 --> d0, d1, d2, d3
    vmov    q1, q0
    vmov    q0, q2

    sub     r7, #4
    cmp     r7, #0
    bne     w4_xy_03_mc_luma_loop

    pop     {r4, r5, r6, r7}
WELS_ASM_FUNC_END


WELS_ASM_FUNC_BEGIN McHorVer02WidthEq16_neon
    push        {r4}
    ldr         r4, [sp, #4]

    sub         r0, r0, r1, lsl #1      //src[-2*src_stride]
    pld         [r0]
    pld         [r0, r1]
    vmov.u16    q14, #0x0014            // 20
    vld1.u8 {q0}, [r0], r1      //q0=src[-2]
    vld1.u8 {q1}, [r0], r1      //q1=src[-1]

    pld         [r0]
    pld         [r0, r1]
    vshr.u16    q15, q14, #2            // 5
    vld1.u8 {q2}, [r0], r1      //q2=src[0]
    vld1.u8 {q3}, [r0], r1      //q3=src[1]
    vld1.u8 {q8}, [r0], r1      //q8=src[2]

w16_v_mc_luma_loop:

    vld1.u8 {q9}, [r0], r1      //q9=src[3]

    FILTER_6TAG_8BITS   d0, d2, d4, d6, d16, d18, d20, q14, q15
    pld         [r0]
    FILTER_6TAG_8BITS   d1, d3, d5, d7, d17, d19, d21, q14, q15
    vld1.u8 {q0}, [r0], r1      //read 2nd row
    vst1.u8 {q10}, [r2], r3         //write 1st 16Byte

    FILTER_6TAG_8BITS   d2, d4, d6, d16, d18, d0, d20, q14, q15
    pld         [r0]
    FILTER_6TAG_8BITS   d3, d5, d7, d17, d19, d1, d21, q14, q15
    vld1.u8 {q1}, [r0], r1      //read 3rd row
    vst1.u8 {q10}, [r2], r3         //write 2nd 16Byte

    FILTER_6TAG_8BITS   d4, d6, d16, d18, d0, d2, d20, q14, q15
    pld         [r0]
    FILTER_6TAG_8BITS   d5, d7, d17, d19, d1, d3, d21, q14, q15
    vld1.u8 {q2}, [r0], r1      //read 4th row
    vst1.u8 {q10}, [r2], r3         //write 3rd 16Byte

    FILTER_6TAG_8BITS   d6, d16, d18, d0, d2, d4, d20, q14, q15
    pld         [r0]
    FILTER_6TAG_8BITS   d7, d17, d19, d1, d3, d5, d21, q14, q15
    vld1.u8 {q3}, [r0], r1      //read 5th row
    vst1.u8 {q10}, [r2], r3         //write 4th 16Byte

    FILTER_6TAG_8BITS   d16, d18, d0, d2, d4, d6, d20, q14, q15
    pld         [r0]
    FILTER_6TAG_8BITS   d17, d19, d1, d3, d5, d7, d21, q14, q15
    vld1.u8 {q8}, [r0], r1      //read 6th row
    vst1.u8 {q10}, [r2], r3         //write 5th 16Byte

    FILTER_6TAG_8BITS   d18, d0, d2, d4, d6, d16, d20, q14, q15
    pld         [r0]
    FILTER_6TAG_8BITS   d19, d1, d3, d5, d7, d17, d21, q14, q15
    vld1.u8 {q9}, [r0], r1      //read 7th row
    vst1.u8 {q10}, [r2], r3         //write 6th 16Byte

    FILTER_6TAG_8BITS   d0, d2, d4, d6, d16, d18, d20, q14, q15
    pld         [r0]
    FILTER_6TAG_8BITS   d1, d3, d5, d7, d17, d19, d21, q14, q15
    vld1.u8 {q0}, [r0], r1      //read 8th row
    vst1.u8 {q10}, [r2], r3         //write 7th 16Byte

    FILTER_6TAG_8BITS   d2, d4, d6, d16, d18, d0, d20, q14, q15
    pld         [r0]
    FILTER_6TAG_8BITS   d3, d5, d7, d17, d19, d1, d21, q14, q15
    vst1.u8 {q10}, [r2], r3         //write 8th 16Byte

    //q2, q3, q8, q9, q0 --> q0~q8
    vswp    q0, q8
    vswp    q0, q2
    vmov    q1, q3
    vmov    q3, q9                      //q0~q8

    sub     r4, #8
    cmp     r4, #0
    bne     w16_v_mc_luma_loop
    pop     {r4}
WELS_ASM_FUNC_END


WELS_ASM_FUNC_BEGIN McHorVer02WidthEq8_neon
    push        {r4}
    ldr         r4, [sp, #4]

    sub         r0, r0, r1, lsl #1      //src[-2*src_stride]
    pld         [r0]
    pld         [r0, r1]
    vmov.u16    q14, #0x0014            // 20
    vld1.u8 {d0}, [r0], r1      //d0=src[-2]
    vld1.u8 {d1}, [r0], r1      //d1=src[-1]

    pld         [r0]
    pld         [r0, r1]
    vshr.u16    q15, q14, #2            // 5
    vld1.u8 {d2}, [r0], r1      //d2=src[0]
    vld1.u8 {d3}, [r0], r1      //d3=src[1]

    vld1.u8 {d4}, [r0], r1      //d4=src[2]
    vld1.u8 {d5}, [r0], r1      //d5=src[3]

w8_v_mc_luma_loop:

    pld         [r0]
    FILTER_6TAG_8BITS   d0, d1, d2, d3, d4, d5, d16, q14, q15
    vld1.u8 {d0}, [r0], r1      //read 2nd row
    vst1.u8 {d16}, [r2], r3     //write 1st 8Byte

    pld         [r0]
    FILTER_6TAG_8BITS   d1, d2, d3, d4, d5, d0, d16, q14, q15
    vld1.u8 {d1}, [r0], r1      //read 3rd row
    vst1.u8 {d16}, [r2], r3     //write 2nd 8Byte

    pld         [r0]
    FILTER_6TAG_8BITS   d2, d3, d4, d5, d0, d1, d16, q14, q15
    vld1.u8 {d2}, [r0], r1      //read 4th row
    vst1.u8 {d16}, [r2], r3     //write 3rd 8Byte

    pld         [r0]
    FILTER_6TAG_8BITS   d3, d4, d5, d0, d1, d2, d16, q14, q15
    vld1.u8 {d3}, [r0], r1      //read 5th row
    vst1.u8 {d16}, [r2], r3     //write 4th 8Byte

    //d4, d5, d0, d1, d2, d3 --> d0, d1, d2, d3, d4, d5
    vswp    q0, q2
    vswp    q1, q2

    sub     r4, #4
    cmp     r4, #0
    bne     w8_v_mc_luma_loop

    pop     {r4}
WELS_ASM_FUNC_END


WELS_ASM_FUNC_BEGIN McHorVer02WidthEq4_neon
    push        {r4, r5, r6, r7}
    sub         r0, r0, r1, lsl #1      //src[-2*src_stride]
    pld         [r0]
    pld         [r0, r1]
    vmov.u16    q14, #0x0014            // 20
    ldr     r4, [r0], r1        //r4=src[-2]
    ldr     r5, [r0], r1        //r5=src[-1]

    pld         [r0]
    pld         [r0, r1]
    vshr.u16    q15, q14, #2            // 5
    ldr     r6, [r0], r1        //r6=src[0]
    ldr     r7, [r0], r1        //r7=src[1]

    vmov        d0, r4, r5
    vmov        d1, r5, r6
    vmov        d2, r6, r7

    ldr     r4, [r0], r1        //r4=src[2]
    vmov        d3, r7, r4
    ldr         r7, [sp, #16]

w4_v_mc_luma_loop:

//  pld         [r0]
    //using reserving r4
    ldr     r5, [r0], r1        //r5=src[3]
    ldr     r6, [r0], r1        //r6=src[0]
    vmov        d4, r4, r5
    vmov        d5, r5, r6          //reserved r6

    FILTER_6TAG_8BITS   d0, d1, d2, d3, d4, d5, d16, q14, q15
    vmov        r4, r5, d16
    str r4, [r2], r3            //write 1st 4Byte
    str r5, [r2], r3            //write 2nd 4Byte

    ldr     r5, [r0], r1        //r5=src[1]
    ldr     r4, [r0], r1        //r4=src[2]
    vmov        d0, r6, r5
    vmov        d1, r5, r4          //reserved r4

    FILTER_6TAG_8BITS   d2, d3, d4, d5, d0, d1, d16, q14, q15
    vmov        r5, r6, d16
    str r5, [r2], r3            //write 3rd 4Byte
    str r6, [r2], r3            //write 4th 4Byte

    //d4, d5, d0, d1 --> d0, d1, d2, d3
    vmov    q1, q0
    vmov    q0, q2

    sub     r7, #4
    cmp     r7, #0
    bne     w4_v_mc_luma_loop

    pop     {r4, r5, r6, r7}
WELS_ASM_FUNC_END


WELS_ASM_FUNC_BEGIN McHorVer22WidthEq16_neon
    push        {r4}
    vpush       {q4-q7}
    ldr         r4, [sp, #68]

    sub         r0, #2                  //src[-2]
    sub         r0, r0, r1, lsl #1      //src[-2*src_stride-2]
    pld         [r0]
    pld         [r0, r1]

    vmov.u16    q14, #0x0014            // 20
    vld1.u8 {d0-d2}, [r0], r1       //use 21(16+5), =src[-2]
    vld1.u8 {d3-d5}, [r0], r1       //use 21(16+5), =src[-1]

    pld         [r0]
    pld         [r0, r1]
    vshr.u16    q15, q14, #2            // 5

    vld1.u8 {d6-d8}, [r0], r1       //use 21(16+5), =src[0]
    vld1.u8 {d9-d11}, [r0], r1  //use 21(16+5), =src[1]
    pld         [r0]
    pld         [r0, r1]
    vld1.u8 {d12-d14}, [r0], r1 //use 21(16+5), =src[2]

w16_hv_mc_luma_loop:

    vld1.u8 {d15-d17}, [r0], r1 //use 21(16+5), =src[3]
    //the 1st row
    pld         [r0]
    // vertical filtered into q9/q10
    FILTER_6TAG_8BITS_TO_16BITS     d0, d3, d6, d9, d12, d15, q9, q14, q15  // 8 avail
    FILTER_6TAG_8BITS_TO_16BITS     d1, d4, d7,d10, d13, d16,q10, q14, q15  // 8 avail
    // horizon filtered
    UNPACK_2_16BITS_TO_ABC  q9, q10, q11, q12, q13
    FILTER_3_IN_16BITS_TO_8BITS q11, q12, q13, d0   //output to q0[0]

    // vertical filtered into q10/q11
    FILTER_6TAG_8BITS_TO_16BITS     d2, d5, d8,d11, d14, d17,q11, q14, q15  // only 5 avail
    // horizon filtered
    UNPACK_2_16BITS_TO_ABC  q10, q11, q9, q12, q13
    FILTER_3_IN_16BITS_TO_8BITS q9, q12, q13, d1    //output to q0[1]
    vst1.u8 {q0}, [r2], r3      //write 16Byte


    vld1.u8 {d0-d2}, [r0], r1       //read 2nd row
    //the 2nd row
    pld         [r0]
    // vertical filtered into q9/q10
    FILTER_6TAG_8BITS_TO_16BITS     d3, d6, d9, d12, d15, d0, q9, q14, q15  // 8 avail
    FILTER_6TAG_8BITS_TO_16BITS     d4, d7,d10, d13, d16, d1,q10, q14, q15  // 8 avail
    // horizon filtered
    UNPACK_2_16BITS_TO_ABC  q9, q10, q11, q12, q13
    FILTER_3_IN_16BITS_TO_8BITS q11, q12, q13, d3   //output to d3

    // vertical filtered into q10/q11
    FILTER_6TAG_8BITS_TO_16BITS     d5, d8,d11, d14, d17, d2,q11, q14, q15  // only 5 avail
    // horizon filtered
    UNPACK_2_16BITS_TO_ABC  q10, q11, q9, q12, q13
    FILTER_3_IN_16BITS_TO_8BITS q9, q12, q13, d4    //output to d4

    vst1.u8 {d3, d4}, [r2], r3      //write 16Byte

    vld1.u8 {d3-d5}, [r0], r1       //read 3rd row
    //the 3rd row
    pld         [r0]
    // vertical filtered into q9/q10
    FILTER_6TAG_8BITS_TO_16BITS     d6, d9, d12, d15, d0, d3, q9, q14, q15  // 8 avail
    FILTER_6TAG_8BITS_TO_16BITS     d7,d10, d13, d16, d1, d4,q10, q14, q15  // 8 avail
    // horizon filtered
    UNPACK_2_16BITS_TO_ABC  q9, q10, q11, q12, q13
    FILTER_3_IN_16BITS_TO_8BITS q11, q12, q13, d6   //output to d6

    // vertical filtered into q10/q11
    FILTER_6TAG_8BITS_TO_16BITS     d8,d11, d14, d17, d2, d5,q11, q14, q15  // only 5 avail
    // horizon filtered
    UNPACK_2_16BITS_TO_ABC  q10, q11, q9, q12, q13
    FILTER_3_IN_16BITS_TO_8BITS q9, q12, q13, d7    //output to d7
    vst1.u8 {d6, d7}, [r2], r3      //write 16Byte

    vld1.u8 {d6-d8}, [r0], r1       //read 4th row
    //the 4th row
    pld         [r0]
    // vertical filtered into q9/q10
    FILTER_6TAG_8BITS_TO_16BITS      d9, d12, d15, d0, d3, d6, q9, q14, q15 // 8 avail
    FILTER_6TAG_8BITS_TO_16BITS     d10, d13, d16, d1, d4, d7,q10, q14, q15 // 8 avail
    // horizon filtered
    UNPACK_2_16BITS_TO_ABC  q9, q10, q11, q12, q13
    FILTER_3_IN_16BITS_TO_8BITS q11, q12, q13, d9   //output to d9
    // vertical filtered into q10/q11
    FILTER_6TAG_8BITS_TO_16BITS     d11, d14, d17, d2, d5, d8,q11, q14, q15 // only 5 avail
    // horizon filtered
    UNPACK_2_16BITS_TO_ABC  q10, q11, q9, q12, q13
    FILTER_3_IN_16BITS_TO_8BITS q9, q12, q13, d10   //output to d10
    vst1.u8 {d9, d10}, [r2], r3     //write 16Byte

    //d12~d17(q6~q8), d0~d8(q0~q3+d8), --> d0~d14
    vswp    q0, q6
    vswp    q6, q3
    vmov    q5, q2
    vmov    q2, q8

    vmov    d20,d8
    vmov    q4, q1
    vmov    q1, q7
    vmov    d14,d20

    sub     r4, #4
    cmp     r4, #0
    bne     w16_hv_mc_luma_loop
    vpop        {q4-q7}
    pop     {r4}
WELS_ASM_FUNC_END


WELS_ASM_FUNC_BEGIN McHorVer22WidthEq8_neon
    push        {r4}
    vpush       {q4}
    ldr         r4, [sp, #20]

    sub         r0, #2              //src[-2]
    sub         r0, r0, r1, lsl #1  //src[-2*src_stride-2]
    pld         [r0]
    pld         [r0, r1]

    vmov.u16    q14, #0x0014        // 20
    vld1.u8 {q0}, [r0], r1  //use 13(8+5), =src[-2]
    vld1.u8 {q1}, [r0], r1  //use 13(8+5), =src[-1]

    pld         [r0]
    pld         [r0, r1]
    vshr.u16    q15, q14, #2        // 5

    vld1.u8 {q2}, [r0], r1  //use 13(8+5), =src[0]
    vld1.u8 {q3}, [r0], r1  //use 13(8+5), =src[1]
    pld         [r0]
    pld         [r0, r1]
    vld1.u8 {q4}, [r0], r1  //use 13(8+5), =src[2]

w8_hv_mc_luma_loop:

    vld1.u8 {q8}, [r0], r1  //use 13(8+5), =src[3]
    //the 1st row
    pld         [r0]
    // vertical filtered into q9/q10
    FILTER_6TAG_8BITS_TO_16BITS     d0, d2, d4, d6, d8, d16, q9, q14, q15   // 8 avail
    FILTER_6TAG_8BITS_TO_16BITS     d1, d3, d5, d7, d9, d17, q10, q14, q15  // 5 avail
    // horizon filtered
    UNPACK_2_16BITS_TO_ABC  q9, q10, q11, q12, q13
    FILTER_3_IN_16BITS_TO_8BITS q11, q12, q13, d18  //output to q9[0]
    vst1.u8 d18, [r2], r3           //write 8Byte

    vld1.u8 {q0}, [r0], r1      //read 2nd row
    //the 2nd row
    pld         [r0]
    // vertical filtered into q9/q10
    FILTER_6TAG_8BITS_TO_16BITS     d2, d4, d6, d8, d16, d0, q9, q14, q15   // 8 avail
    FILTER_6TAG_8BITS_TO_16BITS     d3, d5, d7, d9, d17, d1, q10, q14, q15  // 5 avail
    // horizon filtered
    UNPACK_2_16BITS_TO_ABC  q9, q10, q11, q12, q13
    FILTER_3_IN_16BITS_TO_8BITS q11, q12, q13, d18  //output to q9[0]
    vst1.u8 d18, [r2], r3       //write 8Byte

    vld1.u8 {q1}, [r0], r1      //read 3rd row
    //the 3rd row
    pld         [r0]
    // vertical filtered into q9/q10
    FILTER_6TAG_8BITS_TO_16BITS     d4, d6, d8, d16, d0, d2, q9, q14, q15   // 8 avail
    FILTER_6TAG_8BITS_TO_16BITS     d5, d7, d9, d17, d1, d3, q10, q14, q15  // 5 avail
    // horizon filtered
    UNPACK_2_16BITS_TO_ABC  q9, q10, q11, q12, q13
    FILTER_3_IN_16BITS_TO_8BITS q11, q12, q13, d18  //output to q9[0]
    vst1.u8 d18, [r2], r3           //write 8Byte

    vld1.u8 {q2}, [r0], r1      //read 4th row
    //the 4th row
    pld         [r0]
    // vertical filtered into q9/q10
    FILTER_6TAG_8BITS_TO_16BITS     d6, d8, d16, d0, d2, d4, q9, q14, q15   // 8 avail
    FILTER_6TAG_8BITS_TO_16BITS     d7, d9, d17, d1, d3, d5, q10, q14, q15  // 5 avail
    // horizon filtered
    UNPACK_2_16BITS_TO_ABC  q9, q10, q11, q12, q13
    FILTER_3_IN_16BITS_TO_8BITS q11, q12, q13, d18  //output to q9[0]
    vst1.u8 d18, [r2], r3           //write 8Byte

    //q4~q5, q0~q2, --> q0~q4
    vswp    q0, q4
    vswp    q2, q4
    vmov    q3, q1
    vmov    q1, q8

    sub     r4, #4
    cmp     r4, #0
    bne     w8_hv_mc_luma_loop
    vpop        {q4}
    pop     {r4}
WELS_ASM_FUNC_END


WELS_ASM_FUNC_BEGIN McHorVer22WidthEq4_neon
    push        {r4 ,r5, r6}
    vpush       {q4-q7}
    ldr         r6, [sp, #76]

    sub         r0, #2              //src[-2]
    sub         r0, r0, r1, lsl #1  //src[-2*src_stride-2]
    pld         [r0]
    pld         [r0, r1]

    vmov.u16    q14, #0x0014        // 20
    vld1.u8 {q0}, [r0], r1  //use 9(4+5), =src[-2]
    vld1.u8 {q1}, [r0], r1  //use 9(4+5), =src[-1]

    pld         [r0]
    pld         [r0, r1]
    vshr.u16    q15, q14, #2        // 5

    vld1.u8 {q2}, [r0], r1  //use 9(4+5), =src[0]
    vld1.u8 {q3}, [r0], r1  //use 9(4+5), =src[1]
    pld         [r0]
    pld         [r0, r1]
    vld1.u8 {q4}, [r0], r1  //use 9(4+5), =src[2]

w4_hv_mc_luma_loop:

    vld1.u8 {q5}, [r0], r1  //use 9(4+5), =src[3]
    vld1.u8 {q6}, [r0], r1  //use 9(4+5), =src[4]

    //the 1st&2nd row
    pld         [r0]
    pld         [r0, r1]
    // vertical filtered
    FILTER_6TAG_8BITS_TO_16BITS     d0, d2, d4, d6, d8, d10, q7, q14, q15   // 8 avail
    FILTER_6TAG_8BITS_TO_16BITS     d1, d3, d5, d7, d9, d11, q8, q14, q15   // 1 avail

    FILTER_6TAG_8BITS_TO_16BITS     d2, d4, d6, d8,d10, d12, q9, q14, q15   // 8 avail
    FILTER_6TAG_8BITS_TO_16BITS     d3, d5, d7, d9,d11, d13,q10, q14, q15   // 1 avail
    // horizon filtered
    UNPACK_2_16BITS_TO_ABC  q7, q8, q11, q12, q13   //4 avail
    UNPACK_2_16BITS_TO_ABC  q9,q10, q0, q7, q8      //4 avail

    vmov    d23, d0
    vmov    d25, d14
    vmov    d27, d16

    FILTER_3_IN_16BITS_TO_8BITS q11, q12, q13, d22  //output to q11[0]
    vmov        r4 ,r5, d22
    str     r4, [r2], r3                //write 4Byte
    str     r5, [r2], r3                //write 4Byte

    //the 3rd&4th row
    vld1.u8 {q0}, [r0], r1  //use 9(4+5), =src[3]
    vld1.u8 {q1}, [r0], r1  //use 9(4+5), =src[4]
    pld         [r0]
    pld         [r0, r1]
    // vertical filtered
    FILTER_6TAG_8BITS_TO_16BITS     d4, d6, d8, d10, d12, d0, q7, q14, q15  // 8 avail
    FILTER_6TAG_8BITS_TO_16BITS     d5, d7, d9, d11, d13, d1, q8, q14, q15  // 1 avail

    FILTER_6TAG_8BITS_TO_16BITS     d6, d8,d10, d12, d0, d2, q9, q14, q15   // 8 avail
    FILTER_6TAG_8BITS_TO_16BITS     d7, d9,d11, d13, d1, d3,q10, q14, q15   // 1 avail
    // horizon filtered
    UNPACK_2_16BITS_TO_ABC  q7, q8, q11, q12, q13   //4 avail
    UNPACK_2_16BITS_TO_ABC  q9,q10, q2, q7, q8      //4 avail

    vmov    d23, d4
    vmov    d25, d14
    vmov    d27, d16

    FILTER_3_IN_16BITS_TO_8BITS q11, q12, q13, d22  //output to q11[0]
    vmov        r4 ,r5, d22
    str     r4, [r2], r3                //write 4Byte
    str     r5, [r2], r3                //write 4Byte

    //q4~q6, q0~q1, --> q0~q4
    vswp    q4, q0
    vmov    q3, q4
    vmov    q4, q1
    vmov    q1, q5
    vmov    q2, q6

    sub     r6, #4
    cmp     r6, #0
    bne     w4_hv_mc_luma_loop

    vpop        {q4-q7}
    pop     {r4, r5, r6}
WELS_ASM_FUNC_END


WELS_ASM_FUNC_BEGIN McCopyWidthEq16_neon
    push        {r4}
    ldr         r4, [sp, #4]
w16_copy_loop:
    vld1.u8     {q0}, [r0], r1
    sub         r4, #2
    vld1.u8     {q1}, [r0], r1
    vst1.u8     {q0}, [r2], r3
    cmp         r4, #0
    vst1.u8     {q1}, [r2], r3
    bne         w16_copy_loop

    pop     {r4}
WELS_ASM_FUNC_END


WELS_ASM_FUNC_BEGIN McCopyWidthEq8_neon
    push        {r4}
    ldr         r4, [sp, #4]
w8_copy_loop:
    vld1.u8     {d0}, [r0], r1
    vld1.u8     {d1}, [r0], r1
    vst1.u8     {d0}, [r2], r3
    vst1.u8     {d1}, [r2], r3
    sub         r4, #2
    cmp         r4, #0
    bne         w8_copy_loop

    pop     {r4}
WELS_ASM_FUNC_END


WELS_ASM_FUNC_BEGIN McCopyWidthEq4_neon
    push        {r4, r5, r6}
    ldr         r4, [sp, #12]
w4_copy_loop:
    ldr     r5, [r0], r1
    ldr     r6, [r0], r1
    str     r5, [r2], r3
    str     r6, [r2], r3

    sub         r4, #2
    cmp         r4, #0
    bne         w4_copy_loop

    pop     {r4, r5, r6}
WELS_ASM_FUNC_END


WELS_ASM_FUNC_BEGIN PixelAvgWidthEq16_neon
    push        {r4}
    ldr         r4, [sp, #4]
w16_pix_avg_loop:
    vld1.u8     {q0}, [r2]!
    vld1.u8     {q1}, [r3]!
    vld1.u8     {q2}, [r2]!
    vld1.u8     {q3}, [r3]!

    vld1.u8     {q8}, [r2]!
    vld1.u8     {q9}, [r3]!
    vld1.u8     {q10}, [r2]!
    vld1.u8     {q11}, [r3]!

    AVERAGE_TWO_8BITS       d0, d0, d2
    AVERAGE_TWO_8BITS       d1, d1, d3
    vst1.u8     {q0}, [r0], r1

    AVERAGE_TWO_8BITS       d4, d4, d6
    AVERAGE_TWO_8BITS       d5, d5, d7
    vst1.u8     {q2}, [r0], r1

    AVERAGE_TWO_8BITS       d16, d16, d18
    AVERAGE_TWO_8BITS       d17, d17, d19
    vst1.u8     {q8}, [r0], r1

    AVERAGE_TWO_8BITS       d20, d20, d22
    AVERAGE_TWO_8BITS       d21, d21, d23
    vst1.u8     {q10}, [r0], r1

    sub         r4, #4
    cmp         r4, #0
    bne         w16_pix_avg_loop

    pop     {r4}
WELS_ASM_FUNC_END


WELS_ASM_FUNC_BEGIN PixelAvgWidthEq8_neon
    push        {r4, r5}
    ldr         r4, [sp, #8]
    mov         r5, #16
w8_pix_avg_loop:

    vld1.u8     {d0}, [r2], r5
    vld1.u8     {d2}, [r3], r5
    vld1.u8     {d1}, [r2], r5
    vld1.u8     {d3}, [r3], r5

    AVERAGE_TWO_8BITS       d0, d0, d2
    AVERAGE_TWO_8BITS       d1, d1, d3
    vst1.u8     {d0}, [r0], r1
    vst1.u8     {d1}, [r0], r1

    vld1.u8     {d4}, [r2], r5
    vld1.u8     {d6}, [r3], r5
    vld1.u8     {d5}, [r2], r5
    vld1.u8     {d7}, [r3], r5

    AVERAGE_TWO_8BITS       d4, d4, d6
    AVERAGE_TWO_8BITS       d5, d5, d7
    vst1.u8     {d4}, [r0], r1
    vst1.u8     {d5}, [r0], r1

    sub         r4, #4
    cmp         r4, #0
    bne         w8_pix_avg_loop

    pop     {r4, r5}
WELS_ASM_FUNC_END


WELS_ASM_FUNC_BEGIN PixelAvgWidthEq4_neon
    push        {r4-r8}
    ldr         r4, [sp, #20]
w4_pix_avg_loop:

    ldr     r5, [r2]
    ldr     r6, [r2, #16]
    ldr     r7, [r3]
    ldr     r8, [r3, #16]
    add     r2, #32
    add     r3, #32

    vmov        d0, r5, r6
    vmov        d1, r7, r8
    AVERAGE_TWO_8BITS       d0, d0, d1
    vmov        r5, r6, d0

    str     r5, [r0], r1
    str     r6, [r0], r1

    sub         r4, #2
    cmp         r4, #0
    bne         w4_pix_avg_loop

    pop     {r4-r8}
WELS_ASM_FUNC_END

WELS_ASM_FUNC_BEGIN McChromaWidthEq8_neon
    push        {r4, r5}
    ldr         r4, [sp, #8]
    ldr         r5, [sp, #12]
//  normal case: {cA*src[x]  + cB*src[x+1]} + {cC*src[x+stride] + cD*srcp[x+stride+1]}
//  we can opti it by adding vert only/ hori only cases, to be continue
    vld1.u8 {d31}, [r4]     //load A/B/C/D
    vld1.u8     {q0}, [r0], r1  //src[x]

    vdup.u8 d28, d31[0]         //A
    vdup.u8 d29, d31[1]         //B
    vdup.u8 d30, d31[2]         //C
    vdup.u8 d31, d31[3]         //D

    vext.u8     d1, d0, d1, #1      //src[x+1]

w8_mc_chroma_loop:  // each two pxl row
    vld1.u8     {q1}, [r0], r1  //src[x+stride]
    vld1.u8     {q2}, [r0], r1  //src[x+2*stride]
    vext.u8     d3, d2, d3, #1      //src[x+stride+1]
    vext.u8     d5, d4, d5, #1      //src[x+2*stride+1]

    vmull.u8        q3, d0, d28         //(src[x] * A)
    vmlal.u8        q3, d1, d29         //+=(src[x+1] * B)
    vmlal.u8        q3, d2, d30         //+=(src[x+stride] * C)
    vmlal.u8        q3, d3, d31         //+=(src[x+stride+1] * D)
    vrshrn.u16      d6, q3, #6
    vst1.u8 d6, [r2], r3

    vmull.u8        q3, d2, d28         //(src[x] * A)
    vmlal.u8        q3, d3, d29         //+=(src[x+1] * B)
    vmlal.u8        q3, d4, d30         //+=(src[x+stride] * C)
    vmlal.u8        q3, d5, d31         //+=(src[x+stride+1] * D)
    vrshrn.u16      d6, q3, #6
    vst1.u8 d6, [r2], r3

    vmov        q0, q2
    sub         r5, #2
    cmp         r5, #0
    bne         w8_mc_chroma_loop

    pop     {r4, r5}
WELS_ASM_FUNC_END


WELS_ASM_FUNC_BEGIN McChromaWidthEq4_neon

    push        {r4, r5, r6}
    ldr         r4, [sp, #12]
    ldr         r6, [sp, #16]
//  normal case: {cA*src[x]  + cB*src[x+1]} + {cC*src[x+stride] + cD*srcp[x+stride+1]}
//  we can opti it by adding vert only/ hori only cases, to be continue
    vld1.u8 {d31}, [r4]     //load A/B/C/D

    vdup.u8 d28, d31[0]         //A
    vdup.u8 d29, d31[1]         //B
    vdup.u8 d30, d31[2]         //C
    vdup.u8 d31, d31[3]         //D

w4_mc_chroma_loop:  // each two pxl row
    vld1.u8     {d0}, [r0], r1  //a::src[x]
    vld1.u8     {d2}, [r0], r1  //b::src[x+stride]
    vld1.u8     {d4}, [r0]          //c::src[x+2*stride]

    vshr.u64        d1, d0, #8
    vshr.u64        d3, d2, #8
    vshr.u64        d5, d4, #8

    vmov            q3, q1              //b::[0:7]+b::[1~8]
    vtrn.32     q0, q1              //d0{a::[0:3]+b::[0:3]}; d1{a::[1:4]+b::[1:4]}
    vtrn.32     q3, q2              //d6{b::[0:3]+c::[0:3]}; d7{b::[1:4]+c::[1:4]}

    vmull.u8        q1, d0, d28         //(src[x] * A)
    vmlal.u8        q1, d1, d29         //+=(src[x+1] * B)
    vmlal.u8        q1, d6, d30         //+=(src[x+stride] * C)
    vmlal.u8        q1, d7, d31         //+=(src[x+stride+1] * D)

    vrshrn.u16      d2, q1, #6
    vmov        r4, r5, d2
    str r4, [r2], r3
    str r5, [r2], r3

    sub         r6, #2
    cmp         r6, #0
    bne         w4_mc_chroma_loop

    pop     {r4, r5, r6}
WELS_ASM_FUNC_END

WELS_ASM_FUNC_BEGIN McHorVer20Width17_neon
    push        {r4-r5}
    mov         r4, #20
    mov         r5, #1
    sub         r4, r4, r4, lsl #(16-2)
    lsl         r5, #16
    ror         r4, #16
    vmov        d3, r5, r4                  // 0x0014FFFB00010000

    sub         r3, #16
    ldr         r4, [sp, #8]

    sub         r0, #2
    vmov.u16    q14, #0x0014                // 20
    vshr.u16    q15, q14, #2                // 5

w17_h_mc_luma_loop:
    vld1.u8 {d0,d1,d2}, [r0], r1    //only use 22(17+5); q0=src[-2]

    vext.8      q2, q0, q1, #1      //q2=src[-1]
    vext.8      q3, q0, q1, #2      //q3=src[0]
    vext.8      q8, q0, q1, #3      //q8=src[1]
    vext.8      q9, q0, q1, #4      //q9=src[2]
    vext.8      q10, q0, q1, #5     //q10=src[3]

    FILTER_6TAG_8BITS   d0, d4, d6, d16, d18, d20, d22, q14, q15

    FILTER_6TAG_8BITS   d1, d5, d7, d17, d19, d21, d23, q14, q15

    vst1.u8 {d22, d23}, [r2]!       //write [0:15] Byte

    vsli.64 d2, d2, #8              // [0][1][2][3][4][5]XO-->O[0][1][2][3][4][5]X
    FILTER_SINGLE_TAG_8BITS d2, d3, d22, q11, q1

    vst1.u8 {d2[0]}, [r2], r3       //write 16th Byte

    sub     r4, #1
    cmp     r4, #0
    bne     w17_h_mc_luma_loop
    pop     {r4-r5}
WELS_ASM_FUNC_END


WELS_ASM_FUNC_BEGIN McHorVer20Width9_neon
    push        {r4-r5}
    mov         r4, #20
    mov         r5, #1
    sub         r4, r4, r4, lsl #(16-2)
    lsl         r5, #16
    ror         r4, #16
    vmov        d7, r5, r4                  // 0x0014FFFB00010000

    sub         r3, #8
    ldr         r4, [sp, #8]

    sub         r0, #2
    vmov.u16    q14, #0x0014                // 20
    vshr.u16    q15, q14, #2                // 5

w9_h_mc_luma_loop:
    vld1.u8 {d0,d1}, [r0], r1   //only use 14(9+5); q0=src[-2]
    pld         [r0]

    vext.8      d2, d0, d1, #1      //d2=src[-1]
    vext.8      d3, d0, d1, #2      //d3=src[0]
    vext.8      d4, d0, d1, #3      //d4=src[1]
    vext.8      d5, d0, d1, #4      //d5=src[2]
    vext.8      d6, d0, d1, #5      //d6=src[3]

    FILTER_6TAG_8BITS   d0, d2, d3, d4, d5, d6, d16, q14, q15

    sub     r4, #1
    vst1.u8 {d16}, [r2]!        //write [0:7] Byte

    vsli.64 d2, d1, #8              // [0][1][2][3][4][5]XO-->O[0][1][2][3][4][5]X
    FILTER_SINGLE_TAG_8BITS d2, d7, d18, q9, q1
    vst1.u8 {d2[0]}, [r2], r3       //write 8th Byte

    cmp     r4, #0
    bne     w9_h_mc_luma_loop
    pop     {r4-r5}
WELS_ASM_FUNC_END


WELS_ASM_FUNC_BEGIN McHorVer20Width5_neon
    push        {r4}
    sub         r3, #4
    sub         r0, #2
    ldr         r4, [sp, #4]
    vmov.u16    q14, #0x0014                // 20
    vshr.u16    q15, q14, #2                // 5

w5_h_mc_luma_loop:
    vld1.u8 {d0,d1}, [r0], r1   //only use 10(5+5); q0=src[-2]
    pld         [r0]

    vext.8      d2, d0, d1, #1      //d2=src[-1]
    vext.8      d3, d0, d1, #2      //d3=src[0]
    vext.8      d4, d0, d1, #3      //d4=src[1]
    vext.8      d5, d0, d1, #4      //d5=src[2]
    vext.8      d6, d0, d1, #5      //d6=src[3]

    FILTER_6TAG_8BITS   d0, d2, d3, d4, d5, d6, d16, q14, q15

    sub     r4, #1
    vst1.u32 {d16[0]}, [r2]!        //write [0:3] Byte
    vst1.u8 {d16[4]}, [r2], r3       //write 5th Byte

    cmp     r4, #0
    bne     w5_h_mc_luma_loop
    pop     {r4}
WELS_ASM_FUNC_END


WELS_ASM_FUNC_BEGIN McHorVer02Height17_neon
    push        {r4}
    ldr         r4, [sp, #4]

    sub         r0, r0, r1, lsl #1      //src[-2*src_stride]
    pld         [r0]
    pld         [r0, r1]
    vmov.u16    q14, #0x0014            // 20
    vld1.u8 {q0}, [r0], r1      //q0=src[-2]
    vld1.u8 {q1}, [r0], r1      //q1=src[-1]

    pld         [r0]
    pld         [r0, r1]
    vshr.u16    q15, q14, #2            // 5
    vld1.u8 {q2}, [r0], r1      //q2=src[0]
    vld1.u8 {q3}, [r0], r1      //q3=src[1]
    vld1.u8 {q8}, [r0], r1      //q8=src[2]

w17_v_mc_luma_loop:

    vld1.u8 {q9}, [r0], r1      //q9=src[3]

    FILTER_6TAG_8BITS   d0, d2, d4, d6, d16, d18, d20, q14, q15
    pld         [r0]
    FILTER_6TAG_8BITS   d1, d3, d5, d7, d17, d19, d21, q14, q15
    vld1.u8 {q0}, [r0], r1      //read 2nd row
    vst1.u8 {q10}, [r2], r3         //write 1st 16Byte

    FILTER_6TAG_8BITS   d2, d4, d6, d16, d18, d0, d20, q14, q15
    pld         [r0]
    FILTER_6TAG_8BITS   d3, d5, d7, d17, d19, d1, d21, q14, q15
    vld1.u8 {q1}, [r0], r1      //read 3rd row
    vst1.u8 {q10}, [r2], r3         //write 2nd 16Byte

    FILTER_6TAG_8BITS   d4, d6, d16, d18, d0, d2, d20, q14, q15
    pld         [r0]
    FILTER_6TAG_8BITS   d5, d7, d17, d19, d1, d3, d21, q14, q15
    vld1.u8 {q2}, [r0], r1      //read 4th row
    vst1.u8 {q10}, [r2], r3         //write 3rd 16Byte

    FILTER_6TAG_8BITS   d6, d16, d18, d0, d2, d4, d20, q14, q15
    pld         [r0]
    FILTER_6TAG_8BITS   d7, d17, d19, d1, d3, d5, d21, q14, q15
    vld1.u8 {q3}, [r0], r1      //read 5th row
    vst1.u8 {q10}, [r2], r3         //write 4th 16Byte

    FILTER_6TAG_8BITS   d16, d18, d0, d2, d4, d6, d20, q14, q15
    pld         [r0]
    FILTER_6TAG_8BITS   d17, d19, d1, d3, d5, d7, d21, q14, q15
    vld1.u8 {q8}, [r0], r1      //read 6th row
    vst1.u8 {q10}, [r2], r3         //write 5th 16Byte

    FILTER_6TAG_8BITS   d18, d0, d2, d4, d6, d16, d20, q14, q15
    pld         [r0]
    FILTER_6TAG_8BITS   d19, d1, d3, d5, d7, d17, d21, q14, q15
    vld1.u8 {q9}, [r0], r1      //read 7th row
    vst1.u8 {q10}, [r2], r3         //write 6th 16Byte

    FILTER_6TAG_8BITS   d0, d2, d4, d6, d16, d18, d20, q14, q15
    pld         [r0]
    FILTER_6TAG_8BITS   d1, d3, d5, d7, d17, d19, d21, q14, q15
    vld1.u8 {q0}, [r0], r1      //read 8th row
    vst1.u8 {q10}, [r2], r3         //write 7th 16Byte

    FILTER_6TAG_8BITS   d2, d4, d6, d16, d18, d0, d20, q14, q15
    pld         [r0]
    FILTER_6TAG_8BITS   d3, d5, d7, d17, d19, d1, d21, q14, q15
    vst1.u8 {q10}, [r2], r3         //write 8th 16Byte

    //q2, q3, q8, q9, q0 --> q0~q8
    vswp    q0, q8
    vswp    q0, q2
    vmov    q1, q3
    vmov    q3, q9                      //q0~q8

    sub     r4, #8
    cmp     r4, #1
    bne     w17_v_mc_luma_loop
    // the last 16Bytes
    vld1.u8 {q9}, [r0], r1      //q9=src[3]
    FILTER_6TAG_8BITS   d0, d2, d4, d6, d16, d18, d20, q14, q15
    FILTER_6TAG_8BITS   d1, d3, d5, d7, d17, d19, d21, q14, q15
    vst1.u8 {q10}, [r2], r3         //write 1st 16Byte

    pop     {r4}
WELS_ASM_FUNC_END


WELS_ASM_FUNC_BEGIN McHorVer02Height9_neon
    push        {r4}
    ldr         r4, [sp, #4]

    sub         r0, r0, r1, lsl #1      //src[-2*src_stride]
    pld         [r0]
    pld         [r0, r1]
    vmov.u16    q14, #0x0014            // 20
    vld1.u8 {d0}, [r0], r1      //d0=src[-2]
    vld1.u8 {d1}, [r0], r1      //d1=src[-1]

    pld         [r0]
    pld         [r0, r1]
    vshr.u16    q15, q14, #2            // 5
    vld1.u8 {d2}, [r0], r1      //d2=src[0]
    vld1.u8 {d3}, [r0], r1      //d3=src[1]

    vld1.u8 {d4}, [r0], r1      //d4=src[2]
    vld1.u8 {d5}, [r0], r1      //d5=src[3]

w9_v_mc_luma_loop:

    pld         [r0]
    FILTER_6TAG_8BITS   d0, d1, d2, d3, d4, d5, d16, q14, q15
    vld1.u8 {d0}, [r0], r1      //read 2nd row
    vst1.u8 {d16}, [r2], r3     //write 1st 8Byte

    pld         [r0]
    FILTER_6TAG_8BITS   d1, d2, d3, d4, d5, d0, d16, q14, q15
    vld1.u8 {d1}, [r0], r1      //read 3rd row
    vst1.u8 {d16}, [r2], r3     //write 2nd 8Byte

    pld         [r0]
    FILTER_6TAG_8BITS   d2, d3, d4, d5, d0, d1, d16, q14, q15
    vld1.u8 {d2}, [r0], r1      //read 4th row
    vst1.u8 {d16}, [r2], r3     //write 3rd 8Byte

    pld         [r0]
    FILTER_6TAG_8BITS   d3, d4, d5, d0, d1, d2, d16, q14, q15
    vld1.u8 {d3}, [r0], r1      //read 5th row
    vst1.u8 {d16}, [r2], r3     //write 4th 8Byte

    //d4, d5, d0, d1, d2, d3 --> d0, d1, d2, d3, d4, d5
    vswp    q0, q2
    vswp    q1, q2

    sub     r4, #4
    cmp     r4, #1
    bne     w9_v_mc_luma_loop

    FILTER_6TAG_8BITS   d0, d1, d2, d3, d4, d5, d16, q14, q15
    vst1.u8 {d16}, [r2], r3     //write last 8Byte

    pop     {r4}
WELS_ASM_FUNC_END


WELS_ASM_FUNC_BEGIN McHorVer02Height5_neon
    push        {r4}
    ldr         r4, [sp, #4]

    sub         r0, r0, r1, lsl #1      //src[-2*src_stride]
    pld         [r0]
    pld         [r0, r1]
    vmov.u16    q14, #0x0014            // 20
    vld1.u8 {d0}, [r0], r1      //d0=src[-2]
    vld1.u8 {d1}, [r0], r1      //d1=src[-1]

    pld         [r0]
    pld         [r0, r1]
    vshr.u16    q15, q14, #2            // 5
    vld1.u8 {d2}, [r0], r1      //d2=src[0]
    vld1.u8 {d3}, [r0], r1      //d3=src[1]

    vld1.u8 {d4}, [r0], r1      //d4=src[2]
    vld1.u8 {d5}, [r0], r1      //d5=src[3]

w5_v_mc_luma_loop:

    pld         [r0]
    FILTER_6TAG_8BITS   d0, d1, d2, d3, d4, d5, d16, q14, q15
    vld1.u8 {d0}, [r0], r1      //read 2nd row
    vst1.u32 {d16[0]}, [r2], r3     //write 1st 4Byte

    pld         [r0]
    FILTER_6TAG_8BITS   d1, d2, d3, d4, d5, d0, d16, q14, q15
    vld1.u8 {d1}, [r0], r1      //read 3rd row
    vst1.u32 {d16[0]}, [r2], r3     //write 2nd 4Byte

    pld         [r0]
    FILTER_6TAG_8BITS   d2, d3, d4, d5, d0, d1, d16, q14, q15
    vld1.u8 {d2}, [r0], r1      //read 4th row
    vst1.u32 {d16[0]}, [r2], r3     //write 3rd 4Byte

    pld         [r0]
    FILTER_6TAG_8BITS   d3, d4, d5, d0, d1, d2, d16, q14, q15
    vld1.u8 {d3}, [r0], r1      //read 5th row
    vst1.u32 {d16[0]}, [r2], r3     //write 4th 8Byte

    //d4, d5, d0, d1, d2, d3 --> d0, d1, d2, d3, d4, d5
    vswp    q0, q2
    vswp    q1, q2

    sub     r4, #4
    cmp     r4, #1
    bne     w5_v_mc_luma_loop

    FILTER_6TAG_8BITS   d0, d1, d2, d3, d4, d5, d16, q14, q15
    vst1.u32 {d16[0]}, [r2], r3     //write last 4Byte

    pop     {r4}
WELS_ASM_FUNC_END


WELS_ASM_FUNC_BEGIN McHorVer22Width17_neon
    push        {r4}
    vpush       {q4-q7}
    ldr         r4, [sp, #68]

    sub         r0, #2                  //src[-2]
    sub         r0, r0, r1, lsl #1      //src[-2*src_stride-2]
    pld         [r0]
    pld         [r0, r1]

    vmov.u16    q14, #0x0014            // 20
    vld1.u8 {d0-d2}, [r0], r1       //use 21(17+5), =src[-2]
    vld1.u8 {d3-d5}, [r0], r1       //use 21(17+5), =src[-1]

    pld         [r0]
    pld         [r0, r1]
    vshr.u16    q15, q14, #2            // 5

    vld1.u8 {d6-d8}, [r0], r1       //use 21(17+5), =src[0]
    vld1.u8 {d9-d11}, [r0], r1  //use 21(17+5), =src[1]
    pld         [r0]
    pld         [r0, r1]
    vld1.u8 {d12-d14}, [r0], r1 //use 21(17+5), =src[2]
    sub         r3, #16

w17_hv_mc_luma_loop:

    vld1.u8 {d15-d17}, [r0], r1 //use 21(17+5), =src[3]
    //the 1st row
    pld         [r0]
    // vertical filtered into q9/q10
    FILTER_6TAG_8BITS_TO_16BITS     d0, d3, d6, d9, d12, d15, q9, q14, q15  // 8 avail
    FILTER_6TAG_8BITS_TO_16BITS     d1, d4, d7,d10, d13, d16,q10, q14, q15  // 8 avail
    // horizon filtered
    UNPACK_2_16BITS_TO_ABC  q9, q10, q11, q12, q13
    FILTER_3_IN_16BITS_TO_8BITS q11, q12, q13, d0   //output to q0[0]
    // vertical filtered into q10/q11
    FILTER_6TAG_8BITS_TO_16BITS     d2, d5, d8,d11, d14, d17,q11, q14, q15  // only 6 avail
    // horizon filtered
    UNPACK_2_16BITS_TO_ABC  q10, q11, q9, q12, q13
    FILTER_3_IN_16BITS_TO_8BITS q9, q12, q13, d1    //output to q0[1]
    vst1.u8 {d0, d1}, [r2]!         //write 16Byte
    UNPACK_1_IN_8x16BITS_TO_8BITS   d2, d22, d23, q11 //output to d2[0]
    vst1.u8 {d2[0]}, [r2], r3       //write 16th Byte

    vld1.u8 {d0-d2}, [r0], r1       //read 2nd row
    //the 2nd row
    pld         [r0]
    // vertical filtered into q9/q10
    FILTER_6TAG_8BITS_TO_16BITS     d3, d6, d9, d12, d15, d0, q9, q14, q15  // 8 avail
    FILTER_6TAG_8BITS_TO_16BITS     d4, d7,d10, d13, d16, d1,q10, q14, q15  // 8 avail
    // horizon filtered
    UNPACK_2_16BITS_TO_ABC  q9, q10, q11, q12, q13
    FILTER_3_IN_16BITS_TO_8BITS q11, q12, q13, d3   //output to d3
    // vertical filtered into q10/q11
    FILTER_6TAG_8BITS_TO_16BITS     d5, d8,d11, d14, d17, d2,q11, q14, q15  // only 6 avail
    // horizon filtered
    UNPACK_2_16BITS_TO_ABC  q10, q11, q9, q12, q13
    FILTER_3_IN_16BITS_TO_8BITS q9, q12, q13, d4    //output to d4
    vst1.u8 {d3, d4}, [r2]!     //write 16Byte
    UNPACK_1_IN_8x16BITS_TO_8BITS   d5, d22, d23, q11 //output to d5[0]
    vst1.u8 {d5[0]}, [r2], r3       //write 16th Byte

    vld1.u8 {d3-d5}, [r0], r1       //read 3rd row
    //the 3rd row
    pld         [r0]
    // vertical filtered into q9/q10
    FILTER_6TAG_8BITS_TO_16BITS     d6, d9, d12, d15, d0, d3, q9, q14, q15  // 8 avail
    FILTER_6TAG_8BITS_TO_16BITS     d7,d10, d13, d16, d1, d4,q10, q14, q15  // 8 avail
    // horizon filtered
    UNPACK_2_16BITS_TO_ABC  q9, q10, q11, q12, q13
    FILTER_3_IN_16BITS_TO_8BITS q11, q12, q13, d6   //output to d6
    // vertical filtered into q10/q11
    FILTER_6TAG_8BITS_TO_16BITS     d8,d11, d14, d17, d2, d5,q11, q14, q15  // only 6 avail
    // horizon filtered
    UNPACK_2_16BITS_TO_ABC  q10, q11, q9, q12, q13
    FILTER_3_IN_16BITS_TO_8BITS q9, q12, q13, d7    //output to d7
    vst1.u8 {d6, d7}, [r2]!     //write 16Byte
    UNPACK_1_IN_8x16BITS_TO_8BITS   d8, d22, d23, q11 //output to d8[0]
    vst1.u8 {d8[0]}, [r2], r3       //write 16th Byte

    vld1.u8 {d6-d8}, [r0], r1       //read 4th row
    //the 4th row
    pld         [r0]
    // vertical filtered into q9/q10
    FILTER_6TAG_8BITS_TO_16BITS      d9, d12, d15, d0, d3, d6, q9, q14, q15 // 8 avail
    FILTER_6TAG_8BITS_TO_16BITS     d10, d13, d16, d1, d4, d7,q10, q14, q15 // 8 avail
    // horizon filtered
    UNPACK_2_16BITS_TO_ABC  q9, q10, q11, q12, q13
    FILTER_3_IN_16BITS_TO_8BITS q11, q12, q13, d9   //output to d9
    // vertical filtered into q10/q11
    FILTER_6TAG_8BITS_TO_16BITS     d11, d14, d17, d2, d5, d8,q11, q14, q15 // only 6 avail
    // horizon filtered
    UNPACK_2_16BITS_TO_ABC  q10, q11, q9, q12, q13
    FILTER_3_IN_16BITS_TO_8BITS q9, q12, q13, d10   //output to d10
    vst1.u8 {d9, d10}, [r2]!        //write 16Byte
    UNPACK_1_IN_8x16BITS_TO_8BITS   d11, d22, d23, q11 //output to d11[0]
    vst1.u8 {d11[0]}, [r2], r3      //write 16th Byte

    //d12~d17(q6~q8), d0~d8(q0~q3+d8), --> d0~d14
    vswp    q0, q6
    vswp    q6, q3
    vmov    q5, q2
    vmov    q2, q8

    vmov    d20,d8
    vmov    q4, q1
    vmov    q1, q7
    vmov    d14,d20

    sub     r4, #4
    cmp     r4, #1
    bne     w17_hv_mc_luma_loop
    //the last row
    vld1.u8 {d15-d17}, [r0], r1 //use 21(17+5), =src[3]
    // vertical filtered into q9/q10
    FILTER_6TAG_8BITS_TO_16BITS     d0, d3, d6, d9, d12, d15, q9, q14, q15  // 8 avail
    FILTER_6TAG_8BITS_TO_16BITS     d1, d4, d7,d10, d13, d16,q10, q14, q15  // 8 avail
    // horizon filtered
    UNPACK_2_16BITS_TO_ABC  q9, q10, q11, q12, q13
    FILTER_3_IN_16BITS_TO_8BITS q11, q12, q13, d0   //output to q0[0]
    // vertical filtered into q10/q11
    FILTER_6TAG_8BITS_TO_16BITS     d2, d5, d8,d11, d14, d17,q11, q14, q15  // only 6 avail
    // horizon filtered
    UNPACK_2_16BITS_TO_ABC  q10, q11, q9, q12, q13
    FILTER_3_IN_16BITS_TO_8BITS q9, q12, q13, d1    //output to q0[1]
    vst1.u8 {q0}, [r2]!         //write 16Byte
    UNPACK_1_IN_8x16BITS_TO_8BITS   d2, d22, d23, q11 //output to d2[0]
    vst1.u8 {d2[0]}, [r2], r3       //write 16th Byte

    vpop        {q4-q7}
    pop     {r4}
WELS_ASM_FUNC_END


WELS_ASM_FUNC_BEGIN McHorVer22Width9_neon
    push        {r4}
    vpush       {q4}
    ldr         r4, [sp, #20]

    sub         r0, #2              //src[-2]
    sub         r0, r0, r1, lsl #1  //src[-2*src_stride-2]
    pld         [r0]
    pld         [r0, r1]

    vmov.u16    q14, #0x0014        // 20
    vld1.u8 {q0}, [r0], r1  //use 14(9+5), =src[-2]
    vld1.u8 {q1}, [r0], r1  //use 14(9+5), =src[-1]

    pld         [r0]
    pld         [r0, r1]
    vshr.u16    q15, q14, #2        // 5

    vld1.u8 {q2}, [r0], r1  //use 14(9+5), =src[0]
    vld1.u8 {q3}, [r0], r1  //use 14(9+5), =src[1]
    pld         [r0]
    pld         [r0, r1]
    vld1.u8 {q4}, [r0], r1  //use 14(9+5), =src[2]
    sub         r3, #8

w9_hv_mc_luma_loop:

    vld1.u8 {q8}, [r0], r1  //use 14(9+5), =src[3]
    //the 1st row
    pld         [r0]
    // vertical filtered into q9/q10
    FILTER_6TAG_8BITS_TO_16BITS     d0, d2, d4, d6, d8, d16, q9, q14, q15   // 8 avail
    FILTER_6TAG_8BITS_TO_16BITS     d1, d3, d5, d7, d9, d17, q10, q14, q15  // 6 avail
    // horizon filtered
    UNPACK_2_16BITS_TO_ABC  q9, q10, q11, q12, q13
    FILTER_3_IN_16BITS_TO_8BITS q11, q12, q13, d18  //output to q9[0]
    vst1.u8 d18, [r2]!              //write 8Byte
    UNPACK_1_IN_8x16BITS_TO_8BITS   d19, d20, d21, q10 //output to d19[0]
    vst1.u8 {d19[0]}, [r2], r3  //write 8th Byte

    vld1.u8 {q0}, [r0], r1      //read 2nd row
    //the 2nd row
    pld         [r0]
    // vertical filtered into q9/q10
    FILTER_6TAG_8BITS_TO_16BITS     d2, d4, d6, d8, d16, d0, q9, q14, q15   // 8 avail
    FILTER_6TAG_8BITS_TO_16BITS     d3, d5, d7, d9, d17, d1, q10, q14, q15  // 6 avail
    // horizon filtered
    UNPACK_2_16BITS_TO_ABC  q9, q10, q11, q12, q13
    FILTER_3_IN_16BITS_TO_8BITS q11, q12, q13, d18  //output to q9[0]
    vst1.u8 d18, [r2]!              //write 8Byte
    UNPACK_1_IN_8x16BITS_TO_8BITS   d19, d20, d21, q10 //output to d19[0]
    vst1.u8 {d19[0]}, [r2], r3  //write 8th Byte

    vld1.u8 {q1}, [r0], r1      //read 3rd row
    //the 3rd row
    pld         [r0]
    // vertical filtered into q9/q10
    FILTER_6TAG_8BITS_TO_16BITS     d4, d6, d8, d16, d0, d2, q9, q14, q15   // 8 avail
    FILTER_6TAG_8BITS_TO_16BITS     d5, d7, d9, d17, d1, d3, q10, q14, q15  // 6 avail
    // horizon filtered
    UNPACK_2_16BITS_TO_ABC  q9, q10, q11, q12, q13
    FILTER_3_IN_16BITS_TO_8BITS q11, q12, q13, d18  //output to q9[0]
    vst1.u8 d18, [r2]!              //write 8Byte
    UNPACK_1_IN_8x16BITS_TO_8BITS   d19, d20, d21, q10 //output to d19[0]
    vst1.u8 {d19[0]}, [r2], r3  //write 8th Byte

    vld1.u8 {q2}, [r0], r1      //read 4th row
    //the 4th row
    pld         [r0]
    // vertical filtered into q9/q10
    FILTER_6TAG_8BITS_TO_16BITS     d6, d8, d16, d0, d2, d4, q9, q14, q15   // 8 avail
    FILTER_6TAG_8BITS_TO_16BITS     d7, d9, d17, d1, d3, d5, q10, q14, q15  // 6 avail
    // horizon filtered
    UNPACK_2_16BITS_TO_ABC  q9, q10, q11, q12, q13
    FILTER_3_IN_16BITS_TO_8BITS q11, q12, q13, d18  //output to q9[0]
    vst1.u8 d18, [r2]!          //write 8Byte
    UNPACK_1_IN_8x16BITS_TO_8BITS   d19, d20, d21, q10 //output to d19[0]
    vst1.u8 {d19[0]}, [r2], r3  //write 8th Byte

    //q4~q8, q0~q2, --> q0~q4
    vswp    q0, q4
    vswp    q2, q4
    vmov    q3, q1
    vmov    q1, q8

    sub     r4, #4
    cmp     r4, #1
    bne     w9_hv_mc_luma_loop
    //the last row
    vld1.u8 {q8}, [r0], r1  //use 14(9+5), =src[3]
    // vertical filtered into q9/q10
    FILTER_6TAG_8BITS_TO_16BITS     d0, d2, d4, d6, d8, d16, q9, q14, q15   // 8 avail
    FILTER_6TAG_8BITS_TO_16BITS     d1, d3, d5, d7, d9, d17, q10, q14, q15  // 6 avail
    // horizon filtered
    UNPACK_2_16BITS_TO_ABC  q9, q10, q11, q12, q13
    FILTER_3_IN_16BITS_TO_8BITS q11, q12, q13, d18  //output to q9[0]
    vst1.u8 d18, [r2]!              //write 8Byte
    UNPACK_1_IN_8x16BITS_TO_8BITS   d19, d20, d21, q10 //output to d19[0]
    vst1.u8 {d19[0]}, [r2], r3  //write 8th Byte
    vpop        {q4}
    pop     {r4}
WELS_ASM_FUNC_END


WELS_ASM_FUNC_BEGIN McHorVer22Width5_neon
    push        {r4}
    vpush       {q4}
    ldr         r4, [sp, #20]

    sub         r0, #2              //src[-2]
    sub         r0, r0, r1, lsl #1  //src[-2*src_stride-2]
    pld         [r0]
    pld         [r0, r1]

    vmov.u16    q14, #0x0014        // 20
    vld1.u8 {q0}, [r0], r1  //use 10(5+5), =src[-2]
    vld1.u8 {q1}, [r0], r1  //use 10(5+5), =src[-1]

    pld         [r0]
    pld         [r0, r1]
    vshr.u16    q15, q14, #2        // 5

    vld1.u8 {q2}, [r0], r1  //use 10(5+5), =src[0]
    vld1.u8 {q3}, [r0], r1  //use 10(5+5), =src[1]
    pld         [r0]
    pld         [r0, r1]
    vld1.u8 {q4}, [r0], r1  //use 10(5+5), =src[2]
    sub         r3, #4

w5_hv_mc_luma_loop:

    vld1.u8 {q8}, [r0], r1  //use 10(5+5), =src[3]
    //the 1st row
    pld         [r0]
    // vertical filtered into q9/q10
    FILTER_6TAG_8BITS_TO_16BITS     d0, d2, d4, d6, d8, d16, q9, q14, q15
    FILTER_6TAG_8BITS_TO_16BITS     d1, d3, d5, d7, d9, d17, q10, q14, q15
    // horizon filtered
    UNPACK_2_16BITS_TO_ABC  q9, q10, q11, q12, q13
    FILTER_3_IN_16BITS_TO_8BITS q11, q12, q13, d18
    vst1.u32 {d18[0]}, [r2]!              //write 4Byte
    vst1.u8 {d18[4]}, [r2], r3  //write 5th Byte

    vld1.u8 {q0}, [r0], r1      //read 2nd row
    //the 2nd row
    pld         [r0]
    // vertical filtered into q9/q10
    FILTER_6TAG_8BITS_TO_16BITS     d2, d4, d6, d8, d16, d0, q9, q14, q15
    FILTER_6TAG_8BITS_TO_16BITS     d3, d5, d7, d9, d17, d1, q10, q14, q15
    // horizon filtered
    UNPACK_2_16BITS_TO_ABC  q9, q10, q11, q12, q13
    FILTER_3_IN_16BITS_TO_8BITS q11, q12, q13, d18
    vst1.u32 {d18[0]}, [r2]!              //write 4Byte
    vst1.u8 {d18[4]}, [r2], r3  //write 5th Byte

    vld1.u8 {q1}, [r0], r1      //read 3rd row
    //the 3rd row
    pld         [r0]
    // vertical filtered into q9/q10
    FILTER_6TAG_8BITS_TO_16BITS     d4, d6, d8, d16, d0, d2, q9, q14, q15
    FILTER_6TAG_8BITS_TO_16BITS     d5, d7, d9, d17, d1, d3, q10, q14, q15
    // horizon filtered
    UNPACK_2_16BITS_TO_ABC  q9, q10, q11, q12, q13
    FILTER_3_IN_16BITS_TO_8BITS q11, q12, q13, d18
    vst1.u32 {d18[0]}, [r2]!              //write 4Byte
    vst1.u8 {d18[4]}, [r2], r3  //write 5th Byte

    vld1.u8 {q2}, [r0], r1      //read 4th row
    //the 4th row
    pld         [r0]
    // vertical filtered into q9/q10
    FILTER_6TAG_8BITS_TO_16BITS     d6, d8, d16, d0, d2, d4, q9, q14, q15
    FILTER_6TAG_8BITS_TO_16BITS     d7, d9, d17, d1, d3, d5, q10, q14, q15
    // horizon filtered
    UNPACK_2_16BITS_TO_ABC  q9, q10, q11, q12, q13
    FILTER_3_IN_16BITS_TO_8BITS q11, q12, q13, d18
    vst1.u32 {d18[0]}, [r2]!          //write 4Byte
    vst1.u8 {d18[4]}, [r2], r3  //write 5th Byte

    //q4~q8, q0~q2, --> q0~q4
    vswp    q0, q4
    vswp    q2, q4
    vmov    q3, q1
    vmov    q1, q8

    sub     r4, #4
    cmp     r4, #1
    bne     w5_hv_mc_luma_loop
    //the last row
    vld1.u8 {q8}, [r0], r1  //use 10(5+5), =src[3]
    // vertical filtered into q9/q10
    FILTER_6TAG_8BITS_TO_16BITS     d0, d2, d4, d6, d8, d16, q9, q14, q15
    FILTER_6TAG_8BITS_TO_16BITS     d1, d3, d5, d7, d9, d17, q10, q14, q15
    // horizon filtered
    UNPACK_2_16BITS_TO_ABC  q9, q10, q11, q12, q13
    FILTER_3_IN_16BITS_TO_8BITS q11, q12, q13, d18
    vst1.u32 {d18[0]}, [r2]!              //write 4Byte
    vst1.u8 {d18[4]}, [r2], r3  //write 5th Byte
    vpop        {q4}
    pop     {r4}
WELS_ASM_FUNC_END


WELS_ASM_FUNC_BEGIN PixStrideAvgWidthEq16_neon
    push        {r4, r5, r6}
    ldr         r4, [sp, #12]
    ldr         r5, [sp, #16]
    ldr         r6, [sp, #20]

enc_w16_pix_avg_loop:
    vld1.u8     {q0}, [r2], r3
    vld1.u8     {q1}, [r4], r5
    vld1.u8     {q2}, [r2], r3
    vld1.u8     {q3}, [r4], r5

    vld1.u8     {q8}, [r2], r3
    vld1.u8     {q9}, [r4], r5
    vld1.u8     {q10}, [r2], r3
    vld1.u8     {q11}, [r4], r5

    AVERAGE_TWO_8BITS       d0, d0, d2
    AVERAGE_TWO_8BITS       d1, d1, d3
    vst1.u8     {q0}, [r0], r1

    AVERAGE_TWO_8BITS       d4, d4, d6
    AVERAGE_TWO_8BITS       d5, d5, d7
    vst1.u8     {q2}, [r0], r1

    AVERAGE_TWO_8BITS       d16, d16, d18
    AVERAGE_TWO_8BITS       d17, d17, d19
    vst1.u8     {q8}, [r0], r1

    AVERAGE_TWO_8BITS       d20, d20, d22
    AVERAGE_TWO_8BITS       d21, d21, d23
    vst1.u8     {q10}, [r0], r1

    sub         r6, #4
    cmp         r6, #0
    bne         enc_w16_pix_avg_loop

    pop     {r4, r5, r6}
WELS_ASM_FUNC_END


WELS_ASM_FUNC_BEGIN PixStrideAvgWidthEq8_neon
    push        {r4, r5, r6}
    ldr         r4, [sp, #12]
    ldr         r5, [sp, #16]
    ldr         r6, [sp, #20]
enc_w8_pix_avg_loop:

    vld1.u8     {d0}, [r2], r3
    vld1.u8     {d2}, [r4], r5
    vld1.u8     {d1}, [r2], r3
    vld1.u8     {d3}, [r4], r5

    AVERAGE_TWO_8BITS       d0, d0, d2
    AVERAGE_TWO_8BITS       d1, d1, d3
    vst1.u8     {d0}, [r0], r1
    vst1.u8     {d1}, [r0], r1

    vld1.u8     {d4}, [r2], r3
    vld1.u8     {d6}, [r4], r5
    vld1.u8     {d5}, [r2], r3
    vld1.u8     {d7}, [r4], r5

    AVERAGE_TWO_8BITS       d4, d4, d6
    AVERAGE_TWO_8BITS       d5, d5, d7
    vst1.u8     {d4}, [r0], r1
    vst1.u8     {d5}, [r0], r1

    sub         r6, #4
    cmp         r6, #0
    bne         enc_w8_pix_avg_loop

    pop     {r4, r5, r6}
WELS_ASM_FUNC_END

#endif
