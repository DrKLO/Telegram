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


 //The data sequence will be used
.macro GET_8BYTE_DATA_L0 arg0, arg1, arg2
    vld1.8 {\arg0[0]}, [\arg1], \arg2
    vld1.8 {\arg0[1]}, [\arg1], \arg2
    vld1.8 {\arg0[2]}, [\arg1], \arg2
    vld1.8 {\arg0[3]}, [\arg1], \arg2
    vld1.8 {\arg0[4]}, [\arg1], \arg2
    vld1.8 {\arg0[5]}, [\arg1], \arg2
    vld1.8 {\arg0[6]}, [\arg1], \arg2
    vld1.8 {\arg0[7]}, [\arg1], \arg2
.endm

.macro HDM_TRANSFORM_4X4_L0 arg0, arg1, arg2,arg3, arg4, arg5, arg6, arg7, arg8

    //Do the vertical transform
    vaddl.u8 q0, \arg0, \arg1 //{0,4,8,12,1,5,9,13}
    vsubl.u8 q1, \arg0, \arg1 //{2,6,10,14,3,7,11,15}
    vswp  d1, d2
    vadd.s16 q2, q0, q1 //{0,1,2,3,4,5,6,7}
    vsub.s16 q1, q0, q1 //{12,13,14,15,8,9,10,11}

    //Do the horizontal transform
    vtrn.32 q2, q1
    vadd.s16 q0, q2, q1
    vsub.s16 q1, q2, q1

    vtrn.16 q0, q1
    vadd.s16 q2, q0, q1
    vsub.s16 q1, q0, q1

    vmov.s16 d0, d4
    vmov.s16 d1, d2

    vabs.s16 d3, d3

    //16x16_v
    vtrn.32 d0, d1 //{0,1,3,2}
    vaba.s16 \arg5, d0, \arg2 //16x16_v
    vaba.s16 \arg5, d1, \arg8
    vaba.s16 \arg5, d5, \arg8
    vadd.u16 \arg5, d3

    //16x16_h
    vtrn.16 d4, d5 //{0,4,12,8}
    vaba.s16 \arg6, d4, \arg3 //16x16_h
    vabs.s16 d2, d2
    vabs.s16 d5, d5
    vadd.u16 d2, d3
    vadd.u16 d2, d5
    vadd.u16 \arg6, d2

    //16x16_dc_both
    vaba.s16 \arg7, d4, \arg4 //16x16_dc_both
    vadd.u16 \arg7, d2
.endm

WELS_ASM_FUNC_BEGIN WelsIntra16x16Combined3Satd_neon
    stmdb sp!, {r4-r7, lr}
    vpush {q4-q7}

    //Get the top line data to 'q15'(16 bytes)
    sub  r7, r0, r1
    vld1.8 {q15}, [r7]

    //Get the left colume data to 'q14' (16 bytes)
    sub  r7, r0, #1
    GET_8BYTE_DATA_L0 d28, r7, r1
    GET_8BYTE_DATA_L0 d29, r7, r1

    //Calculate the mean value and save to 'q13->d27(reserve the d26)' (2 bytes)
    //Calculate the 16x16_dc_both mode SATD
    vaddl.u8 q0, d30, d31
    vaddl.u8 q1, d28, d29
    vadd.u16 q0, q1
    vadd.u16 d0, d1
    vpaddl.u16 d0, d0
    vpaddl.u32 d0, d0

    //Calculate the mean value
    vrshr.u16  d0, #5
    vshl.u16   d27, d0, #4


    //Calculate the 16x16_v mode SATD and save to "q11, 12"
    vshll.u8 q0, d30, #2
    vshll.u8 q1, d31, #2
    vtrn.32  q0, q1
    vadd.s16 q2, q0, q1
    vsub.s16 q1, q0, q1
    vtrn.16  q2, q1
    vadd.s16 q12, q2, q1
    vsub.s16 q11, q2, q1
    vtrn.32  q12, q11 //{0,1,3,2, 4,5,7,6} q12
                      //{8,9,11,10, 12,13,15,14} q11
    //Calculate the 16x16_h mode SATD and save to "q9, q10"
    vshll.u8 q0, d28, #2
    vshll.u8 q1, d29, #2
    vtrn.32  q0, q1
    vadd.s16 q2, q0, q1
    vsub.s16 q1, q0, q1
    vtrn.16  q2, q1
    vadd.s16 q10, q2, q1
    vsub.s16 q9,  q2, q1
    vtrn.32  q10, q9  //{0,1,3,2, 4,5,7,6} q10
                      //{8,9,11,10, 12,13,15,14} q9

    vmov.i32 d17, #0//Save the SATD of DC_BOTH
    vmov.i32 d16, #0//Save the SATD of H
    vmov.i32 d15, #0//Save the SATD of V
    vmov.i32 d14, #0//For zero D register
    //Load the p_enc data and save to "q3 ~ q6"--- 16X4 bytes
    vld1.32  {q3}, [r2], r3
    vld1.32  {q4}, [r2], r3
    vld1.32  {q5}, [r2], r3
    vld1.32  {q6}, [r2], r3
    vtrn.32  q3, q4
    vtrn.32  q5, q6

    HDM_TRANSFORM_4X4_L0 d6, d10, d24, d20, d27, d15, d16, d17, d14
    HDM_TRANSFORM_4X4_L0 d7, d11, d22, d20, d27, d15, d16, d17, d14
    HDM_TRANSFORM_4X4_L0 d8, d12, d25, d20, d27, d15, d16, d17, d14
    HDM_TRANSFORM_4X4_L0 d9, d13, d23, d20, d27, d15, d16, d17, d14

    //Load the p_enc data and save to "q3 ~ q6"--- 16X4 bytes
    vld1.32  {q3}, [r2], r3
    vld1.32  {q4}, [r2], r3
    vld1.32  {q5}, [r2], r3
    vld1.32  {q6}, [r2], r3
    vtrn.32  q3, q4
    vtrn.32  q5, q6

    HDM_TRANSFORM_4X4_L0 d6, d10, d24, d21, d27, d15, d16, d17, d14
    HDM_TRANSFORM_4X4_L0 d7, d11, d22, d21, d27, d15, d16, d17, d14
    HDM_TRANSFORM_4X4_L0 d8, d12, d25, d21, d27, d15, d16, d17, d14
    HDM_TRANSFORM_4X4_L0 d9, d13, d23, d21, d27, d15, d16, d17, d14

    //Load the p_enc data and save to "q3 ~ q6"--- 16X4 bytes
    vld1.32  {q3}, [r2], r3
    vld1.32  {q4}, [r2], r3
    vld1.32  {q5}, [r2], r3
    vld1.32  {q6}, [r2], r3
    vtrn.32  q3, q4
    vtrn.32  q5, q6

    HDM_TRANSFORM_4X4_L0 d6, d10, d24, d18, d27, d15, d16, d17, d14
    HDM_TRANSFORM_4X4_L0 d7, d11, d22, d18, d27, d15, d16, d17, d14
    HDM_TRANSFORM_4X4_L0 d8, d12, d25, d18, d27, d15, d16, d17, d14
    HDM_TRANSFORM_4X4_L0 d9, d13, d23, d18, d27, d15, d16, d17, d14

    //Load the p_enc data and save to "q3 ~ q6"--- 16X4 bytes
    vld1.32  {q3}, [r2], r3
    vld1.32  {q4}, [r2], r3
    vld1.32  {q5}, [r2], r3
    vld1.32  {q6}, [r2], r3
    vtrn.32  q3, q4
    vtrn.32  q5, q6

    HDM_TRANSFORM_4X4_L0 d6, d10, d24, d19, d27, d15, d16, d17, d14
    HDM_TRANSFORM_4X4_L0 d7, d11, d22, d19, d27, d15, d16, d17, d14
    HDM_TRANSFORM_4X4_L0 d8, d12, d25, d19, d27, d15, d16, d17, d14
    HDM_TRANSFORM_4X4_L0 d9, d13, d23, d19, d27, d15, d16, d17, d14

    //Get the data from stack
    ldr r5, [sp, #84] //the addr of Best_mode
    ldr r6, [sp, #88] //the value of i_lambda

    //vadd.u16   d24, d25
    vrshr.u16  d15, #1
    vpaddl.u16 d15, d15
    vpaddl.u32 d15, d15
    vmov.u32   r0, d15[0]

    //vadd.u16   d22, d23
    vrshr.u16  d16, #1
    vpaddl.u16 d16, d16
    vpaddl.u32 d16, d16
    vmov.u32   r1, d16[0]
    add  r1, r1, r6, lsl #1

    //vadd.u16   d20, d21
    vrshr.u16  d17, #1
    vpaddl.u16 d17, d17
    vpaddl.u32 d17, d17
    vmov.u32   r2, d17[0]
    add  r2, r2, r6, lsl #1

    mov r4, #0
    cmp r1, r0
    movcc r0, r1
    movcc r4, #1
    cmp r2, r0
    movcc r0, r2
    movcc r4, #2

    str r4, [r5]

    vpop {q4-q7}
    ldmia sp!, {r4-r7, lr}
WELS_ASM_FUNC_END


WELS_ASM_FUNC_BEGIN WelsIntra16x16Combined3Sad_neon
    stmdb sp!, {r4-r7, lr}

    //Get the top line data to 'q15'(16 bytes)
    sub  r4, r0, r1
    vld1.8 {q15}, [r4]

    //Get the left colume data to 'q14' (16 bytes)
    sub  r4, r0, #1
    GET_8BYTE_DATA_L0 d28, r4, r1
    GET_8BYTE_DATA_L0 d29, r4, r1

    //Calculate the mean value and save to 'q13' (8 bytes)
    //Calculate the 16x16_dc_both mode SATD
    vaddl.u8 q0, d30, d31
    vaddl.u8 q1, d28, d29
    vadd.u16 q0, q1
    vadd.u16 d0, d1
    vpaddl.u16 d0, d0
    vpaddl.u32 d0, d0

    //Calculate the mean value
    vrshr.u16  d0, d0, #5
    vdup.8     q13, d0[0]

    sub  r4, r0, #1

    vmov.i32 q12, #0//Save the SATD of DC_BOTH
    vmov.i32 q11, #0//Save the SATD of H
    vmov.i32 q10, #0//Save the SATD of V

    mov lr, #16
sad_intra_16x16_x3_opt_loop0:
    //Get the left colume data to 'd0' (16 bytes)
    vld1.8 {d0[]}, [r4], r1

    //Load the p_enc data and save to "q1 ~ q2"--- 16X4 bytes
    vld1.8  {q1}, [r2], r3

    subs lr, #1
    //Do the SAD for top colume
    vabal.u8  q12, d30, d2
    vabal.u8  q12, d31, d3

    //Do the SAD for left colume
    vabal.u8  q11, d0, d2
    vabal.u8  q11, d0, d3

    //Do the SAD for mean value
    vabal.u8  q10, d26, d2
    vabal.u8  q10, d26, d3

    bne sad_intra_16x16_x3_opt_loop0

    //Get the data from stack
    ldr r5, [sp, #20] //the addr of Best_mode
    ldr r6, [sp, #24] //the value of i_lambda

    vadd.u16   d24, d25
    vpaddl.u16 d24, d24
    vpaddl.u32 d24, d24
    vmov.u32   r0, d24[0]

    vadd.u16   d22, d23
    vpaddl.u16 d22, d22
    vpaddl.u32 d22, d22
    vmov.u32   r1, d22[0]
    add  r1, r1, r6, lsl #1

    vadd.u16   d20, d21
    vpaddl.u16 d20, d20
    vpaddl.u32 d20, d20
    vmov.u32   r2, d20[0]
    add  r2, r2, r6, lsl #1

    mov r4, #0
    cmp r1, r0
    movcc r0, r1
    movcc r4, #1
    cmp r2, r0
    movcc r0, r2
    movcc r4, #2

    str r4, [r5]

    ldmia sp!, {r4-r7, lr}
WELS_ASM_FUNC_END


WELS_ASM_FUNC_BEGIN WelsIntra8x8Combined3Sad_neon
    stmdb sp!, {r4-r7, lr}

    //Get the data from stack
    ldr r4, [sp, #32] //p_dec_cr
    ldr r5, [sp, #36] //p_enc_cr

    //Get the left colume data to 'd28(cb), d30(cr)' (16 bytes)
    sub  r6, r0, #1
    GET_8BYTE_DATA_L0 d28, r6, r1
    sub  r6, r4, #1
    GET_8BYTE_DATA_L0 d30, r6, r1

    //Get the top line data to 'd29(cb), d31(cr)'(16 bytes)
    sub  r6, r0, r1
    vld1.8 {d29}, [r6]
    sub  r6, r4, r1
    vld1.8 {d31}, [r6]

    //Calculate the sum of left column and top row
    vmov.i32   q0, q14
    vpaddl.u8  q0, q0
    vpaddl.u16 q0, q0
    vadd.u32   d2, d0, d1 //'m1' save to d2
    vrshr.u32  q0, q0, #2 //calculate 'm2','m3'
    vrshr.u32  d2, d2, #3 //calculate 'm4'

    //duplicate the 'mx' to a vector line
    vdup.8     d27, d2[0]
    vdup.8     d26, d1[4]
    vtrn.32    d27, d26

    vdup.8     d26, d0[4]
    vdup.8     d25, d2[4]
    vtrn.32    d26, d25   //Save to "d27, d26"

    vmov.i32   q0, q15
    vpaddl.u8  q0, q0
    vpaddl.u16 q0, q0
    vadd.u32   d2, d0, d1 //'m1' save to d2
    vrshr.u32  q0, q0, #2 //calculate 'm2','m3'
    vrshr.u32  d2, d2, #3 //calculate 'm4'

    //duplicate the 'mx' to a vector line
    vdup.8     d25, d2[0]
    vdup.8     d24, d1[4]
    vtrn.32    d25, d24

    vdup.8     d24, d0[4]
    vdup.8     d23, d2[4]
    vtrn.32    d24, d23   //Save to "d25, d24"

    vmov.i32 q11, #0//Save the SATD of DC_BOTH
    vmov.i32 q10, #0//Save the SATD of H
    vmov.i32 q9 , #0//Save the SATD of V
    sub  r6, r0, #1
    sub  r7, r4, #1
    mov lr, #4
sad_intra_8x8_x3_opt_loop0:

    //Load the p_enc data and save to "q1 ~ q2"--- 16X4 bytes
    vld1.8  {d0}, [r2], r3
    vld1.8  {d1}, [r5], r3

    //Get the left colume data to 'd0' (16 bytes)
    vld1.8 {d2[]}, [r6], r1
    vld1.8 {d3[]}, [r7], r1

    subs lr, #1


    //Do the SAD for top colume
    vabal.u8  q11, d29, d0
    vabal.u8  q11, d31, d1

    //Do the SAD for left colume
    vabal.u8  q10, d2, d0
    vabal.u8  q10, d3, d1

    //Do the SAD for mean value
    vabal.u8  q9, d27, d0
    vabal.u8  q9, d25, d1


    bne sad_intra_8x8_x3_opt_loop0

    mov lr, #4
sad_intra_8x8_x3_opt_loop1:

    //Load the p_enc data and save to "q1 ~ q2"--- 16X4 bytes
    vld1.8  {d0}, [r2], r3
    vld1.8  {d1}, [r5], r3

    //Get the left colume data to 'd0' (16 bytes)
    vld1.8 {d2[]}, [r6], r1
    vld1.8 {d3[]}, [r7], r1

    subs lr, #1


    //Do the SAD for top colume
    vabal.u8  q11, d29, d0
    vabal.u8  q11, d31, d1

    //Do the SAD for left colume
    vabal.u8  q10, d2, d0
    vabal.u8  q10, d3, d1

    //Do the SAD for mean value
    vabal.u8  q9, d26, d0
    vabal.u8  q9, d24, d1


    bne sad_intra_8x8_x3_opt_loop1
    //Get the data from stack
    ldr r5, [sp, #20] //the addr of Best_mode
    ldr r6, [sp, #24] //the value of i_lambda

    vadd.u16   d22, d23
    vpaddl.u16 d22, d22
    vpaddl.u32 d22, d22
    vmov.u32   r0, d22[0]
    add  r0, r0, r6, lsl #1

    vadd.u16   d20, d21
    vpaddl.u16 d20, d20
    vpaddl.u32 d20, d20
    vmov.u32   r1, d20[0]
    add  r1, r1, r6, lsl #1

    vadd.u16   d18, d19
    vpaddl.u16 d18, d18
    vpaddl.u32 d18, d18
    vmov.u32   r2, d18[0]

    mov r4, #2
    cmp r1, r0
    movcc r0, r1
    movcc r4, #1
    cmp r2, r0
    movcc r0, r2
    movcc r4, #0

    str r4, [r5]

    ldmia sp!, {r4-r7, lr}
WELS_ASM_FUNC_END


WELS_ASM_FUNC_BEGIN WelsIntra8x8Combined3Satd_neon
    stmdb sp!, {r4-r7, lr}
    vpush {q4-q7}

    //Get the data from stack
    ldr r4, [sp, #96] //p_dec_cr
    ldr r5, [sp, #100] //p_enc_cr

    //Get the top line data to 'd29(cb), d31(cr)'(16 bytes)
    sub  r6, r0, r1
    vld1.8 {d29}, [r6]
    sub  r6, r4, r1
    vld1.8 {d31}, [r6]

    //Get the left colume data to 'd28(cb), d30(cr)' (16 bytes)
    sub  r6, r0, #1
    GET_8BYTE_DATA_L0 d28, r6, r1
    sub  r6, r4, #1
    GET_8BYTE_DATA_L0 d30, r6, r1

    //Calculate the 16x16_v mode SATD and save to "q12, 13"
    vshll.u8 q0, d29, #2
    vshll.u8 q1, d31, #2
    vtrn.32  q0, q1
    vadd.s16 q2, q0, q1
    vsub.s16 q1, q0, q1
    vtrn.16  q2, q1
    vadd.s16 q13, q2, q1
    vsub.s16 q12, q2, q1
    vtrn.32  q13, q12 //{0,1,3,2, 4,5,7,6} q13
                      //{8,9,11,10, 12,13,15,14} q12
    //Calculate the 16x16_h mode SATD and save to "q10, q11"
    vshll.u8 q0, d28, #2
    vshll.u8 q1, d30, #2
    vtrn.32  q0, q1
    vadd.s16 q2, q0, q1
    vsub.s16 q1, q0, q1
    vtrn.16  q2, q1
    vadd.s16 q11, q2, q1
    vsub.s16 q10,  q2, q1
    vtrn.32  q11, q10  //{0,1,3,2, 4,5,7,6} q11
                       //{8,9,11,10, 12,13,15,14} q10

    //Calculate the sum of left column and top row
    //vmov.i32   q0, q14
    vpaddl.u8  q0, q14
    vpaddl.u16 q0, q0
    vadd.u32   d2, d0, d1

    vpaddl.u8  q2, q15
    vpaddl.u16 q2, q2
    vadd.u32   d3, d4, d5

    vtrn.32    q0, q2
    vrshr.u32  q1, #3
    vrshr.u32  q2, #2
    vshll.u32  q9, d4, #4 // {2cb, 2cr} q9
    vshll.u32  q8, d5, #4 // {1cb, 1cr} q8
    vshll.u32  q7, d2, #4 // {0cb, 3cb} q7
    vshll.u32  q6, d3, #4 // {0cr, 3cr} q6


    vmov.i32 d28, #0//Save the SATD of DC_BOTH
    vmov.i32 d10, #0//Save the SATD of H
    vmov.i32 d11, #0//Save the SATD of V
    vmov.i32 d30, #0//For zero D register
    //Load the p_enc data and save to "q3 ~ q6"--- 16X4 bytes
    vld1.32  {d6}, [r2], r3
    vld1.32  {d7}, [r2], r3
    vld1.32  {d8}, [r2], r3
    vld1.32  {d9}, [r2], r3
    vtrn.32  d6, d7
    vtrn.32  d8, d9
    HDM_TRANSFORM_4X4_L0 d6, d8, d26, d22, d14, d11, d10, d28, d30
    HDM_TRANSFORM_4X4_L0 d7, d9, d27, d22, d16, d11, d10, d28, d30

    vld1.32  {d6}, [r5], r3
    vld1.32  {d7}, [r5], r3
    vld1.32  {d8}, [r5], r3
    vld1.32  {d9}, [r5], r3
    vtrn.32  d6, d7
    vtrn.32  d8, d9
    HDM_TRANSFORM_4X4_L0 d6, d8, d24, d20, d12, d11, d10, d28, d30
    HDM_TRANSFORM_4X4_L0 d7, d9, d25, d20, d17, d11, d10, d28, d30

    //Load the p_enc data and save to "q3 ~ q6"--- 16X4 bytes
    vld1.32  {d6}, [r2], r3
    vld1.32  {d7}, [r2], r3
    vld1.32  {d8}, [r2], r3
    vld1.32  {d9}, [r2], r3
    vtrn.32  d6, d7
    vtrn.32  d8, d9
    HDM_TRANSFORM_4X4_L0 d6, d8, d26, d23, d18, d11, d10, d28, d30
    HDM_TRANSFORM_4X4_L0 d7, d9, d27, d23, d15, d11, d10, d28, d30

    vld1.32  {d6}, [r5], r3
    vld1.32  {d7}, [r5], r3
    vld1.32  {d8}, [r5], r3
    vld1.32  {d9}, [r5], r3
    vtrn.32  d6, d7
    vtrn.32  d8, d9
    HDM_TRANSFORM_4X4_L0 d6, d8, d24, d21, d19, d11, d10, d28, d30
    HDM_TRANSFORM_4X4_L0 d7, d9, d25, d21, d13, d11, d10, d28, d30

    //Get the data from stack
    ldr r5, [sp, #84] //the addr of Best_mode
    ldr r6, [sp, #88] //the value of i_lambda

    vrshr.u16  d11, #1
    vpaddl.u16 d11, d11
    vpaddl.u32 d11, d11
    vmov.u32   lr, d11[0]
    add  lr, lr, r6, lsl #1

    vrshr.u16  d10, #1
    vpaddl.u16 d10, d10
    vpaddl.u32 d10, d10
    vmov.u32   r3, d10[0]
    add  r3, r3, r6, lsl #1

    vrshr.u16  d28, #1
    vpaddl.u16 d28, d28
    vpaddl.u32 d28, d28
    vmov.u32   r2, d28[0]

    mov r6, #2
    cmp r3, lr
    movcc lr, r3
    movcc r6, #1
    cmp r2, lr
    movcc lr, r2
    movcc r6, #0

    str r6, [r5]
    mov r0, lr

    vpop {q4-q7}
    ldmia sp!, {r4-r7, lr}
WELS_ASM_FUNC_END


WELS_ASM_FUNC_BEGIN WelsIntra4x4Combined3Satd_neon
    stmdb sp!, {r4-r7, lr}

    //Get the top line data to 'd31[0~3]'(4 bytes)
    sub  r7, r0, r1
    vld1.32 {d31[0]}, [r7]

    //Get the left colume data to 'd31[4~7]' (4 bytes)
    sub  r7, r0, #1
    vld1.8 {d31[4]}, [r7], r1
    vld1.8 {d31[5]}, [r7], r1
    vld1.8 {d31[6]}, [r7], r1
    vld1.8 {d31[7]}, [r7], r1

    //Calculate the mean value and save to 'd30' (2 bytes)
    vpaddl.u8 d0, d31
    vpaddl.u16 d0, d0
    vpaddl.u32 d0, d0
    //Calculate the mean value
    vrshr.u16  d0, #3
    vshl.u16   d30, d0, #4

    //Calculate the 16x16_v mode SATD and save to "d29"
    //Calculate the 16x16_h mode SATD and save to "d28"
    vshll.u8 q0, d31, #2
    vtrn.32  d0, d1
    vadd.s16 d2, d0, d1
    vsub.s16 d1, d0, d1
    vtrn.16  d2, d1
    vadd.s16 d29, d2, d1
    vsub.s16 d28, d2, d1
    vtrn.32  d29, d28 //{0,1,3,2 top} d29
                      //{0,1,3,2 left} d28

    vmov.i32 d27, #0//Save the SATD of DC_BOTH
    vmov.i32 d26, #0//Save the SATD of H
    vmov.i32 d25, #0//Save the SATD of V
    vmov.i32 d24, #0//For zero D register

    //Load the p_enc data and save to "d22,d23"--- 4X4 bytes
    vld1.32  {d23[0]}, [r2], r3
    vld1.32  {d23[1]}, [r2], r3
    vld1.32  {d22[0]}, [r2], r3
    vld1.32  {d22[1]}, [r2], r3

    HDM_TRANSFORM_4X4_L0 d23, d22, d29, d28, d30, d25, d26, d27, d24

    //Get the data from stack
    ldr r5, [sp, #28] //the value of lambda2
    ldr r6, [sp, #32] //the value of lambda1
    ldr r7, [sp, #36] //the value of lambda0

    vrshr.u16  d25, #1
    vpaddl.u16 d25, d25
    vpaddl.u32 d25, d25
    vmov.u32   r0, d25[0]
    add  r0, r7

    vrshr.u16  d26, #1
    vpaddl.u16 d26, d26
    vpaddl.u32 d26, d26
    vmov.u32   r1, d26[0]
    add  r1, r6

    vrshr.u16  d27, #1
    vpaddl.u16 d27, d27
    vpaddl.u32 d27, d27
    vmov.u32   r2, d27[0]
    add  r2, r5

    ldr r5, [sp, #20] //p_dst
    ldr r6, [sp, #24] //the addr of Best_mode

    mov r4, r0
    cmp r1, r4
    movcc r4, r1
    cmp r2, r4
    movcc r4, r2

    //The compare sequence affect the resule
    cmp r4, r2
    bne satd_intra_4x4_x3_opt_jump0
    mov r0, #2
    str r0, [r6]
    vshr.u32  d0, d30, #4 // {2cb, 2cr} q9
    vdup.8 q1, d0[0]
    vst1.8 {q1}, [r5]
    //...
    bl satd_intra_4x4_x3_opt_end
satd_intra_4x4_x3_opt_jump0:

    cmp r4, r1
    bne satd_intra_4x4_x3_opt_jump1
    mov r0, #1
    str r0, [r6]
    vdup.8 d0, d31[4]
    vdup.8 d1, d31[5]
    vdup.8 d2, d31[6]
    vdup.8 d3, d31[7]
    vst4.32 {d0[0],d1[0],d2[0],d3[0]}, [r5]

    bl satd_intra_4x4_x3_opt_end
satd_intra_4x4_x3_opt_jump1:

    mov r0, #0
    str r0, [r6]
    vst1.32 {d31[0]}, [r5]!
    vst1.32 {d31[0]}, [r5]!
    vst1.32 {d31[0]}, [r5]!
    vst1.32 {d31[0]}, [r5]!


satd_intra_4x4_x3_opt_end:
    mov r0, r4

    ldmia sp!, {r4-r7, lr}
WELS_ASM_FUNC_END

#endif
