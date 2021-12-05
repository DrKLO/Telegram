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

.macro SATD_16x4
    vld1.64     {q0}, [r0,:128], r1
    vld1.64     {q1}, [r2], r3

    vsubl.u8    q8,  d0,  d2
    vld1.64     {q2}, [r0,:128], r1

    vsubl.u8    q10, d1,  d3
    vld1.64     {q3}, [r2], r3

    vsubl.u8    q9,  d4,  d6
    vld1.64     {q0}, [r0,:128], r1

    vsubl.u8    q11, d5,  d7
    vld1.64     {q1}, [r2], r3

    vsubl.u8    q12, d0,  d2
    vld1.64     {q2}, [r0,:128], r1

    vsubl.u8    q14, d1,  d3
    vadd.s16    q0,  q8,  q9

    vld1.64     {q3}, [r2], r3
    vsub.s16    q1,  q8,  q9

    vsubl.u8    q13, d4,  d6
    vsubl.u8    q15, d5,  d7

    vadd.s16    q2, q12, q13
    vsub.s16    q3, q12, q13

    vadd.s16    q8, q10, q11
    vsub.s16    q9, q10, q11

    vadd.s16    q10, q14, q15
    vsub.s16    q11, q14, q15

    vadd.s16    q12, q0, q2
    vsub.s16    q14, q0, q2

    vadd.s16    q13, q8, q10
    vsub.s16    q15, q8, q10

    vsub.s16    q0, q1, q3
    vadd.s16    q2, q1, q3

    vsub.s16    q1, q9, q11
    vadd.s16    q3, q9, q11

    vtrn.16 q12, q14
    vtrn.16 q13, q15

    vadd.s16 q8, q12, q14
    vabd.s16 q10, q12, q14

    vadd.s16 q9, q13, q15
    vabd.s16 q11, q13, q15

    vabs.s16 q8, q8
    vabs.s16 q9, q9

    vtrn.16 q0, q2
    vtrn.16 q1, q3

    vadd.s16 q12, q0, q2
    vabd.s16 q14, q0, q2

    vadd.s16 q13, q1, q3
    vabd.s16 q15, q1, q3

    vabs.s16 q12, q12
    vabs.s16 q13, q13

    vtrn.32 q8, q10
    vtrn.32 q9, q11

    vtrn.32 q12, q14
    vtrn.32 q13, q15

    vmax.s16    q0, q8,  q10
    vmax.s16    q1, q9,  q11
    vmax.s16    q2, q12,  q14
    vmax.s16    q3, q13,  q15

    vadd.u16 q0, q0, q1
    vadd.u16 q2, q2, q3
.endm

.macro SATD_8x4

    vld1.64     {d0}, [r0,:64], r1
    vld1.64     {d1}, [r2], r3

    vld1.64     {d2}, [r0,:64], r1
    vsubl.u8    q8, d0, d1

    vld1.64     {d3}, [r2], r3
    vsubl.u8    q9, d2, d3

    vld1.64     {d4}, [r0,:64], r1
    vld1.64     {d5}, [r2], r3

    vadd.s16    q12, q8, q9
    vsubl.u8    q10, d4, d5

    vld1.64     {d6}, [r0,:64], r1
    vld1.64     {d7}, [r2], r3

    vsubl.u8    q11, d6,  d7
    vsub.s16    q13, q8, q9

    vadd.s16    q14, q10, q11
    vsub.s16    q15, q10, q11

    vadd.s16    q0, q12, q14
    vsub.s16    q1, q12, q14

    vsub.s16    q2, q13, q15
    vadd.s16    q3, q13, q15

    vtrn.16     q0, q1
    vtrn.16     q2, q3

    vadd.s16    q8, q0, q1
    vabd.s16    q9, q0, q1

    vabs.s16    q8, q8
    vadd.s16    q10, q2, q3

    vabd.s16    q11, q2, q3
    vabs.s16    q10, q10

    vtrn.32     q8, q9
    vtrn.32     q10, q11

    vmax.s16    q0, q8, q9
    vmax.s16    q1, q10, q11
.endm

.macro SAD_16x4
    vld1.64 {q6}, [r0, :128], r1
    vabal.u8 q10, d8, d10

    vld1.64 {q7}, [r2], r3
    vabal.u8 q11, d9, d11

    vld1.64 {q0}, [r0, :128], r1
    vabal.u8 q12, d12, d14

    vld1.64 {q1}, [r2], r3
    vabal.u8 q13, d13, d15

    vld1.64 {q2}, [r0, :128], r1
    vabal.u8 q10, d0, d2

    vld1.64 {q3}, [r2], r3
    vabal.u8 q11, d1, d3

    vld1.64 {q4}, [r0, :128], r1
    vabal.u8 q12, d4, d6

    vld1.64 {q5}, [r2], r3
    vabal.u8 q13, d5, d7
.endm

.macro SAD_8x4
    vld1.64 {d0}, [r0, :64], r1
    vld1.64 {d1}, [r2], r3

    vabal.u8 q10, d0, d1
    vld1.64 {d2}, [r0, :64], r1

    vld1.64 {d3}, [r2], r3
    vabal.u8 q11, d2, d3

    vld1.64 {d4}, [r0, :64], r1
    vld1.64 {d5}, [r2], r3

    vabal.u8 q12, d4, d5
    vld1.64 {d6}, [r0, :64], r1

    vld1.64 {d7}, [r2], r3
    vabal.u8 q13, d6, d7
.endm


WELS_ASM_FUNC_BEGIN WelsSampleSad16x16_neon
    vpush {q4-q7}

    vld1.64 {q0}, [r0, :128], r1
    vld1.64 {q1}, [r2], r3

    vabdl.u8 q10, d0, d2
    vld1.64 {q2}, [r0, :128], r1

    vabdl.u8 q11, d1, d3
    vld1.64 {q3}, [r2], r3

    vld1.64 {q4}, [r0, :128], r1
    vabdl.u8 q12, d4, d6
    vld1.64 {q5}, [r2], r3
    vabdl.u8 q13, d5, d7

    SAD_16x4
    SAD_16x4
    SAD_16x4

    vld1.64 {q6}, [r0, :128], r1
    vabal.u8 q10, d8, d10

    vld1.64 {q7}, [r2], r3
    vabal.u8 q11, d9, d11

    vabal.u8 q12, d12, d14
    vabal.u8 q13, d13, d15

    vadd.u16 q14, q10, q11
    vadd.u16 q15, q12, q13

    vadd.u16 q15, q14, q15
    vadd.u16 d0, d30, d31
    vpaddl.u16 d0, d0
    vpaddl.u32 d0, d0
    vmov.u32   r0, d0[0]

    vpop {q4-q7}
WELS_ASM_FUNC_END


WELS_ASM_FUNC_BEGIN WelsSampleSad16x8_neon
    vpush {q4-q7}

    vld1.64 {q0}, [r0, :128], r1
    vld1.64 {q1}, [r2], r3

    vabdl.u8 q10, d0, d2
    vld1.64 {q2}, [r0, :128], r1

    vabdl.u8 q11, d1, d3
    vld1.64 {q3}, [r2], r3

    vld1.64 {q4}, [r0, :128], r1
    vabdl.u8 q12, d4, d6
    vld1.64 {q5}, [r2], r3
    vabdl.u8 q13, d5, d7

    SAD_16x4

    vld1.64 {q6}, [r0, :128], r1
    vabal.u8 q10, d8, d10

    vld1.64 {q7}, [r2], r3
    vabal.u8 q11, d9, d11

    vabal.u8 q12, d12, d14
    vabal.u8 q13, d13, d15

    vadd.u16 q14, q10, q11
    vadd.u16 q15, q12, q13

    vadd.u16 q15, q14, q15
    vadd.u16 d0, d30, d31
    vpaddl.u16 d0, d0
    vpaddl.u32 d0, d0
    vmov.u32   r0, d0[0]
    vpop {q4-q7}
WELS_ASM_FUNC_END


WELS_ASM_FUNC_BEGIN WelsSampleSad8x16_neon

    vld1.64 {d0}, [r0, :64], r1
    vld1.64 {d1}, [r2], r3

    vabdl.u8 q10, d0, d1
    vld1.64 {d2}, [r0, :64], r1

    vld1.64 {d3}, [r2], r3
    vabdl.u8 q11, d2, d3

    vld1.64 {d4}, [r0, :64], r1
    vld1.64 {d5}, [r2], r3

    vabdl.u8 q12, d4, d5
    vld1.64 {d6}, [r0, :64], r1

    vld1.64 {d7}, [r2], r3
    vabdl.u8 q13, d6, d7

    SAD_8x4
    SAD_8x4
    SAD_8x4

    vadd.u16 q14, q10, q11
    vadd.u16 q15, q12, q13
    vadd.u16 q15, q15, q14
    vadd.u16 d0, d30, d31
    vpaddl.u16 d0, d0
    vpaddl.u32 d0, d0
    vmov.u32   r0, d0[0]
WELS_ASM_FUNC_END


WELS_ASM_FUNC_BEGIN WelsSampleSad8x8_neon

    vld1.64 {d0}, [r0, :64], r1
    vld1.64 {d1}, [r2], r3

    vabdl.u8 q10, d0, d1
    vld1.64 {d2}, [r0, :64], r1

    vld1.64 {d3}, [r2], r3
    vabdl.u8 q11, d2, d3

    vld1.64 {d4}, [r0, :64], r1
    vld1.64 {d5}, [r2], r3

    vabdl.u8 q12, d4, d5
    vld1.64 {d6}, [r0, :64], r1

    vld1.64 {d7}, [r2], r3
    vabdl.u8 q13, d6, d7

    SAD_8x4

    vadd.u16 q14, q10, q11
    vadd.u16 q15, q12, q13
    vadd.u16 q15, q15, q14
    vadd.u16 d0, d30, d31
    vpaddl.u16 d0, d0
    vpaddl.u32 d0, d0
    vmov.u32   r0, d0[0]
WELS_ASM_FUNC_END


WELS_ASM_FUNC_BEGIN WelsSampleSad4x4_neon
    stmdb sp!, {r4-r5, lr}

    //Loading a horizontal line data (4 bytes)
    //line 0
    ldr r4, [r0], r1
    ldr r5, [r2], r3
    usad8  lr, r4, r5

    //line 1
    ldr r4, [r0], r1
    ldr r5, [r2], r3
    usada8  lr, r4, r5, lr

    //line 2
    ldr r4, [r0], r1
    ldr r5, [r2], r3
    usada8  lr, r4, r5, lr

    //line 3
    ldr r4, [r0]
    ldr r5, [r2]
    usada8  r0, r4, r5, lr

    ldmia sp!, {r4-r5, lr}
WELS_ASM_FUNC_END


WELS_ASM_FUNC_BEGIN WelsSampleSadFour16x16_neon

    stmdb sp!, {r4-r5, lr}

    //Generate the pix2 start addr
    sub   r4, r2, #1
    add   r5, r2, #1
    sub   r2, r3

    //Loading a horizontal line data (16 bytes)
    vld1.8 {q0}, [r0], r1 //save pix1

    vld1.8 {q1}, [r2], r3 //save pix2 - stride
    vld1.8 {q10}, [r2], r3 //save pix2
    vld1.8 {q2}, [r2], r3 //save pix2 + stride

    vld1.8 {q3}, [r4], r3 //save pix2 - 1
    vld1.8 {q8}, [r5], r3 //save pix2 + 1

    //Do the SAD for 16 bytes
    vabdl.u8  q15, d0, d2
    vabal.u8  q15, d1, d3

    vabdl.u8  q13, d0, d4
    vabal.u8  q13, d1, d5

    vabdl.u8  q11, d0, d6
    vabal.u8  q11, d1, d7

    vabdl.u8  q9, d0, d16
    vabal.u8  q9, d1, d17

    mov lr, #15
pixel_sad_4_16x16_loop_0:

    //Loading a horizontal line data (16 bytes)
    vld1.8 {q0}, [r0], r1 //save pix1
    vmov.8 q1,   q10      //save pix2 - stride
    vmov.8 q10,  q2
    vabal.u8  q15, d0, d2
    vld1.8 {q2}, [r2], r3 //save pix2 + stride
    vabal.u8  q15, d1, d3
    vld1.8 {q3}, [r4], r3 //save pix2 - 1
    vabal.u8  q13, d0, d4
    vld1.8 {q8}, [r5], r3 //save pix2 + 1
    vabal.u8  q13, d1, d5
    subs lr, #1

    vabal.u8  q11, d0, d6
    vabal.u8  q11, d1, d7

    vabal.u8  q9, d0, d16
    vabal.u8  q9, d1, d17

    bne pixel_sad_4_16x16_loop_0


    //Save SAD to 'r0'
    ldr   r0, [sp, #12]

    vadd.u16   d0, d30, d31
    vadd.u16   d1, d26, d27
    vadd.u16   d2, d22, d23
    vadd.u16   d3, d18, d19

    vpaddl.u16 q0, q0
    vpaddl.u16 q1, q1

    vpaddl.u32 q0, q0
    vpaddl.u32 q1, q1

    vst4.32    {d0[0],d1[0],d2[0],d3[0]}, [r0]

    ldmia sp!, {r4-r5, lr}
WELS_ASM_FUNC_END


WELS_ASM_FUNC_BEGIN WelsSampleSadFour16x8_neon
    stmdb sp!, {r4-r5, lr}

    //Generate the pix2 start addr
    sub   r4, r2, #1
    add   r5, r2, #1
    sub   r2, r3

    //Loading a horizontal line data (16 bytes)
    vld1.8 {q0}, [r0], r1 //save pix1

    vld1.8 {q1}, [r2], r3 //save pix2 - stride
    vld1.8 {q10}, [r2], r3 //save pix2
    vld1.8 {q2}, [r2], r3 //save pix2 + stride

    vld1.8 {q3}, [r4], r3 //save pix2 - 1
    vld1.8 {q8}, [r5], r3 //save pix2 + 1

    //Do the SAD for 16 bytes
    vabdl.u8  q15, d0, d2
    vabal.u8  q15, d1, d3

    vabdl.u8  q13, d0, d4
    vabal.u8  q13, d1, d5

    vabdl.u8  q11, d0, d6
    vabal.u8  q11, d1, d7

    vabdl.u8  q9, d0, d16
    vabal.u8  q9, d1, d17

    mov lr, #7
pixel_sad_4_16x8_loop_0:

    //Loading a horizontal line data (16 bytes)
    vld1.8 {q0}, [r0], r1 //save pix1
    vmov.8 q1,   q10      //save pix2 - stride
    vmov.8 q10,  q2
    vabal.u8  q15, d0, d2
    vld1.8 {q2}, [r2], r3 //save pix2 + stride
    vabal.u8  q15, d1, d3
    vld1.8 {q3}, [r4], r3 //save pix2 - 1
    vabal.u8  q13, d0, d4
    vld1.8 {q8}, [r5], r3 //save pix2 + 1
    vabal.u8  q13, d1, d5
    subs lr, #1

    vabal.u8  q11, d0, d6
    vabal.u8  q11, d1, d7

    vabal.u8  q9, d0, d16
    vabal.u8  q9, d1, d17

    bne pixel_sad_4_16x8_loop_0

    //Save SAD to 'r0'
    ldr   r0, [sp, #12]

    vadd.u16   d0, d30, d31
    vadd.u16   d1, d26, d27
    vadd.u16   d2, d22, d23
    vadd.u16   d3, d18, d19

    vpaddl.u16 q0, q0
    vpaddl.u16 q1, q1

    vpaddl.u32 q0, q0
    vpaddl.u32 q1, q1

    vst4.32    {d0[0],d1[0],d2[0],d3[0]}, [r0]

    ldmia sp!, {r4-r5, lr}
WELS_ASM_FUNC_END


WELS_ASM_FUNC_BEGIN WelsSampleSadFour8x16_neon
    stmdb sp!, {r4-r5, lr}

    //Generate the pix2 start addr
    sub   r4, r2, #1
    add   r5, r2, #1
    sub   r2, r3

    //Loading a horizontal line data (8 bytes)
    vld1.8 {d0}, [r0], r1 //save pix1

    vld1.8 {d1}, [r2], r3 //save pix2 - stride
    vld1.8 {d6}, [r2], r3 //save pix2
    vld1.8 {d2}, [r2], r3 //save pix2 + stride

    vld1.8 {d3}, [r4], r3 //save pix2 - 1
    vld1.8 {d4}, [r5], r3 //save pix2 + 1

    //Do the SAD for 8 bytes
    vabdl.u8  q15, d0, d1
    vabdl.u8  q14, d0, d2
    vabdl.u8  q13, d0, d3
    vabdl.u8  q12, d0, d4

    mov lr, #15
pixel_sad_4_8x16_loop_0:

    //Loading a horizontal line data (8 bytes)
    vld1.8 {d0}, [r0], r1 //save pix1
    vmov.8 d1,   d6       //save pix2 - stride
    vmov.8 d6,   d2
    vld1.8 {d2}, [r2], r3 //save pix2 + stride
    vld1.8 {d3}, [r4], r3 //save pix2 - 1
    vabal.u8  q15, d0, d1

    vld1.8 {d4}, [r5], r3 //save pix2 + 1
    //Do the SAD for 8 bytes
    vabal.u8  q14, d0, d2
    vabal.u8  q13, d0, d3
    vabal.u8  q12, d0, d4
    subs lr, #1

    bne pixel_sad_4_8x16_loop_0

    //Save SAD to 'r0'
    ldr   r0, [sp, #12]

    vadd.u16   d0, d30, d31
    vadd.u16   d1, d28, d29
    vadd.u16   d2, d26, d27
    vadd.u16   d3, d24, d25

    vpaddl.u16 q0, q0
    vpaddl.u16 q1, q1

    vpaddl.u32 q0, q0
    vpaddl.u32 q1, q1

    vst4.32    {d0[0],d1[0],d2[0],d3[0]}, [r0]

    ldmia sp!, {r4-r5, lr}
WELS_ASM_FUNC_END


WELS_ASM_FUNC_BEGIN WelsSampleSadFour8x8_neon
    stmdb sp!, {r4-r5, lr}

    //Generate the pix2 start addr
    sub   r4, r2, #1
    add   r5, r2, #1
    sub   r2, r3

    //Loading a horizontal line data (8 bytes)
    vld1.8 {d0}, [r0], r1 //save pix1

    vld1.8 {d1}, [r2], r3 //save pix2 - stride
    vld1.8 {d6}, [r2], r3 //save pix2
    vld1.8 {d2}, [r2], r3 //save pix2 + stride

    vld1.8 {d3}, [r4], r3 //save pix2 - 1
    vld1.8 {d4}, [r5], r3 //save pix2 + 1

    //Do the SAD for 8 bytes
    vabdl.u8  q15, d0, d1
    vabdl.u8  q14, d0, d2
    vabdl.u8  q13, d0, d3
    vabdl.u8  q12, d0, d4

    mov lr, #7
pixel_sad_4_8x8_loop_0:

    //Loading a horizontal line data (8 bytes)
    vld1.8 {d0}, [r0], r1 //save pix1
    vmov.8 d1,   d6       //save pix2 - stride
    vmov.8 d6,   d2
    vld1.8 {d2}, [r2], r3 //save pix2 + stride
    vld1.8 {d3}, [r4], r3 //save pix2 - 1
    vabal.u8  q15, d0, d1

    vld1.8 {d4}, [r5], r3 //save pix2 + 1
    //Do the SAD for 8 bytes
    vabal.u8  q14, d0, d2
    vabal.u8  q13, d0, d3
    vabal.u8  q12, d0, d4
    subs lr, #1
    bne pixel_sad_4_8x8_loop_0

    //Save SAD to 'r0'
    ldr   r0, [sp, #12]

    vadd.u16   d0, d30, d31
    vadd.u16   d1, d28, d29
    vadd.u16   d2, d26, d27
    vadd.u16   d3, d24, d25

    vpaddl.u16 q0, q0
    vpaddl.u16 q1, q1

    vpaddl.u32 q0, q0
    vpaddl.u32 q1, q1

    vst4.32    {d0[0],d1[0],d2[0],d3[0]}, [r0]

    ldmia sp!, {r4-r5, lr}
WELS_ASM_FUNC_END


WELS_ASM_FUNC_BEGIN WelsSampleSadFour4x4_neon

    vld1.32  {d0[0]}, [r0], r1
    vld1.32  {d0[1]}, [r0], r1
    vld1.32  {d1[0]}, [r0], r1
    vld1.32  {d1[1]}, [r0]


    sub   r0, r2, r3
    vld1.32  {d2[0]}, [r0], r3
    vld1.32  {d2[1]}, [r0], r3
    vld1.32  {d3[0]}, [r0], r3
    vld1.32  {d3[1]}, [r0], r3
    vld1.32  {d4[0]}, [r0], r3
    vld1.32  {d4[1]}, [r0]

    sub   r0,  r2, #1
    vld1.32  {d5[0]}, [r0], r3
    vld1.32  {d5[1]}, [r0], r3
    vld1.32  {d6[0]}, [r0], r3
    vld1.32  {d6[1]}, [r0]

    add   r0,  r2, #1
    vld1.32  {d7[0]}, [r0], r3
    vld1.32  {d7[1]}, [r0], r3
    vld1.32  {d8[0]}, [r0], r3
    vld1.32  {d8[1]}, [r0]

    vabdl.u8  q15, d0, d2
    vabdl.u8  q14, d1, d3

    vabdl.u8  q13, d0, d3
    vabdl.u8  q12, d1, d4

    vabdl.u8  q11, d0, d5
    vabdl.u8  q10, d1, d6

    vabdl.u8  q9, d0, d7
    vabdl.u8  q8, d1, d8

    //Save SAD to 'r4'
    ldr   r0, [sp]
    vadd.u16   q0, q14, q15
    vadd.u16   q1, q12, q13
    vadd.u16   q2, q10, q11
    vadd.u16   q3, q8 , q9

    vadd.u16   d0, d1
    vadd.u16   d1, d2, d3
    vadd.u16   d2, d4, d5
    vadd.u16   d3, d6, d7

    vpaddl.u16 q0, q0
    vpaddl.u16 q1, q1

    vpaddl.u32 q0, q0
    vpaddl.u32 q1, q1

    vst4.32    {d0[0],d1[0],d2[0],d3[0]}, [r0]

WELS_ASM_FUNC_END


WELS_ASM_FUNC_BEGIN WelsSampleSatd16x16_neon
    vpush       {q7}

    SATD_16x4
    vadd.u16    q7,  q0, q2

    SATD_16x4
    vadd.u16    q7,  q7, q0
    vadd.u16    q7,  q7, q2

    SATD_16x4
    vadd.u16    q7,  q7, q0
    vadd.u16    q7,  q7, q2

    SATD_16x4
    vadd.u16    q7,  q7, q0
    vadd.u16    q7,  q7, q2

    vadd.u16  d0, d14, d15
    vpaddl.u16  d0, d0
    vpaddl.u32  d0, d0

    vmov.32     r0,  d0[0]
    vpop        {q7}
WELS_ASM_FUNC_END


WELS_ASM_FUNC_BEGIN WelsSampleSatd16x8_neon
    vpush       {q7}

    SATD_16x4
    vadd.u16    q7,  q0, q2

    SATD_16x4
    vadd.u16    q7,  q7, q0
    vadd.u16    q7,  q7, q2

    vadd.u16  d0, d14, d15
    vpaddl.u16  d0, d0
    vpaddl.u32  d0, d0

    vmov.32     r0,  d0[0]
    vpop        {q7}
WELS_ASM_FUNC_END


WELS_ASM_FUNC_BEGIN WelsSampleSatd8x16_neon
    vpush       {q7}

    SATD_8x4
    vadd.u16    q7,  q0, q1

    SATD_8x4
    vadd.u16    q7,  q7, q0
    vadd.u16    q7,  q7, q1

    SATD_8x4
    vadd.u16    q7,  q7, q0
    vadd.u16    q7,  q7, q1

    SATD_8x4
    vadd.u16    q7,  q7, q0
    vadd.u16    q7,  q7, q1

    vadd.u16  d0, d14, d15
    vpaddl.u16  d0, d0
    vpaddl.u32  d0, d0

    vmov.32     r0,  d0[0]
    vpop        {q7}
WELS_ASM_FUNC_END


WELS_ASM_FUNC_BEGIN WelsSampleSatd8x8_neon
    vpush       {q7}

    SATD_8x4
    vadd.u16    q7,  q0, q1

    SATD_8x4
    vadd.u16    q7,  q7, q0
    vadd.u16    q7,  q7, q1

    vadd.u16  d0, d14, d15
    vpaddl.u16  d0, d0
    vpaddl.u32  d0, d0

    vmov.32     r0,  d0[0]
    vpop        {q7}
WELS_ASM_FUNC_END


WELS_ASM_FUNC_BEGIN WelsSampleSatd4x4_neon

    //Load the pix1 data --- 16 bytes
    vld1.32  {d0[0]}, [r0], r1
    vld1.32  {d0[1]}, [r0], r1
    vld1.32  {d1[0]}, [r0], r1
    vld1.32  {d1[1]}, [r0]

    //Load the pix2 data --- 16 bytes
    vld1.32  {d2[0]}, [r2], r3
    vld1.32  {d2[1]}, [r2], r3
    vld1.32  {d3[0]}, [r2], r3
    vld1.32  {d3[1]}, [r2]

    //Get the difference
    vsubl.u8 q15, d0, d2 //{0,1,2,3,4,5,6,7}
    vsubl.u8 q14, d1, d3 //{8,9,10,11,12,13,14,15}

    //Do the vertical transform
    vadd.s16 q13, q15, q14 //{0,4,8,12,1,5,9,13}
    vsub.s16 q12, q15, q14 //{2,6,10,14,3,7,11,15}
    vswp  d27, d24
    vadd.s16 q15, q13, q12 //{0,1,2,3,4,5,6,7}
    vsub.s16 q14, q13, q12 //{12,13,14,15,8,9,10,11}

    //Do the horizontal transform
    vtrn.32 q15, q14
    vadd.s16 q13, q15, q14
    vsub.s16 q12, q15, q14

    vtrn.16 q13, q12
    vadd.s16 q15, q13, q12

    //Do the SAD
    vabs.s16 q15, q15
    vabd.s16 q14, q13, q12

    vadd.u16 q0, q15, q14

    vrhadd.u16 d0, d1
    vpaddl.u16 d0, d0
    vpaddl.u32 d0, d0

    vmov.u32   r0, d0[0]

WELS_ASM_FUNC_END

#endif


