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


WELS_ASM_FUNC_BEGIN DyadicBilinearDownsampler_neon
    stmdb   sp!, {r4-r8, lr}

    //Get   the width   and height
    ldr  r4, [sp,   #24]    //src_width
    ldr  r5, [sp,   #28]    //src_height

    //Initialize the register
    mov r6, r2
    mov r8, r0
    mov lr, #0
    lsr r5, #1

    //Save the tailer   for the unasigned   size
    mla  r7, r1, r5, r0
    vld1.32 {q15}, [r7]

    add r7, r2, r3
    //processing a colume   data
comp_ds_bilinear_loop0:

    vld1.8 {q0,q1}, [r2]!
    vld1.8 {q2,q3}, [r7]!
    vuzp.8 q0, q1
    vuzp.8 q2, q3
    vrhadd.u8 q0, q0, q1
    vrhadd.u8 q2, q2, q3
    vrhadd.u8 q0, q0, q2
    vst1.32 {q0},   [r0]!
    add lr, #32

    cmp lr, r4
    movcs   lr, #0
    addcs   r6, r6, r3, lsl #1
    movcs   r2, r6
    addcs   r7, r2, r3
    addcs   r8, r1
    movcs   r0, r8
    subscs r5, #1
    bne comp_ds_bilinear_loop0

    //restore   the tailer for the unasigned size
    vst1.32 {q15}, [r0]

    ldmia   sp!, {r4-r8,lr}
WELS_ASM_FUNC_END


WELS_ASM_FUNC_BEGIN comp_ds_bilinear_w_x8_neon
    stmdb   sp!, {r4-r7, lr}

    //Get   the width   and height
    ldr  r4, [sp,   #20]    //src_width
    ldr  r5, [sp,   #24]    //src_height

    //Get   the difference
    sub lr, r3, r4
    sub r1, r1, r4, lsr #1

    lsr r5, #1

    //processing a colume   data
comp_ds_bilinear_w_x8_loop0:

    lsr r6, r4, #3
    add r7, r2, r3
    //processing a line data
comp_ds_bilinear_w_x8_loop1:

    vld1.8 {d0}, [r2]!
    vld1.8 {d1}, [r7]!
    vpaddl.u8   q0, q0
    vrshr.u16   q0, #1
    vrhadd.u16 d0, d1

    vmovn.u16   d0, q0
    vst1.32 {d0[0]}, [r0]!
    subs r6, #1
    bne comp_ds_bilinear_w_x8_loop1

    add r2, r7, lr
    add r0, r1
    subs r5, #1
    bne comp_ds_bilinear_w_x8_loop0

    ldmia   sp!, {r4-r7,lr}
WELS_ASM_FUNC_END


WELS_ASM_FUNC_BEGIN comp_ds_bilinear_w_x16_neon
    stmdb   sp!, {r4-r7, lr}

    //Get   the width   and height
    ldr  r4, [sp,   #20]    //src_width
    ldr  r5, [sp,   #24]    //src_height

    //Get   the difference
    sub lr, r3, r4
    sub r1, r1, r4, lsr #1

    lsr r5, #1

    //processing a colume   data
comp_ds_bilinear_w_x16_loop0:

    lsr r6, r4, #4
    add r7, r2, r3
    //processing a line data
comp_ds_bilinear_w_x16_loop1:

    vld1.8 {q0}, [r2]!
    vld1.8 {q1}, [r7]!
    vpaddl.u8   q0, q0
    vpaddl.u8   q1, q1
    vrshr.u16   q0, #1
    vrshr.u16   q1, #1
    vrhadd.u16 q0, q1

    vmovn.u16   d0, q0
    vst1.32 {d0},   [r0]!
    subs r6, #1
    bne comp_ds_bilinear_w_x16_loop1

    add r2, r7, lr
    add r0, r1
    subs r5, #1
    bne comp_ds_bilinear_w_x16_loop0

    ldmia   sp!, {r4-r7,lr}
WELS_ASM_FUNC_END


WELS_ASM_FUNC_BEGIN DyadicBilinearDownsamplerWidthx32_neon
    stmdb   sp!, {r4-r7, lr}

    //Get   the width   and height
    ldr  r4, [sp,   #20]    //src_width
    ldr  r5, [sp,   #24]    //src_height

    //Get   the difference
    sub lr, r3, r4
    sub r1, r1, r4, lsr #1

    lsr r5, #1

    //processing a colume   data
comp_ds_bilinear_w_x32_loop0:

    lsr r6, r4, #5
    add r7, r2, r3
    //processing a line data
comp_ds_bilinear_w_x32_loop1:

    vld1.8 {q0,q1}, [r2]!
    vld1.8 {q2,q3}, [r7]!
    vuzp.8 q0, q1
    vuzp.8 q2, q3
    vrhadd.u8 q0, q0, q1
    vrhadd.u8 q2, q2, q3
    vrhadd.u8 q0, q0, q2
    vst1.32 {q0},   [r0]!
    subs r6, #1
    bne comp_ds_bilinear_w_x32_loop1

    add r2, r7, lr
    add r0, r1
    subs r5, #1
    bne comp_ds_bilinear_w_x32_loop0

    ldmia   sp!, {r4-r7,lr}
WELS_ASM_FUNC_END


WELS_ASM_FUNC_BEGIN GeneralBilinearAccurateDownsampler_neon
    stmdb sp!, {r4-r12, lr}

    //Get the data from stack
    ldr r4, [sp, #40] //the addr of src
    ldr r5, [sp, #44] //the value of src_stride
    ldr r6, [sp, #48] //the value of scaleX
    ldr r7, [sp, #52] //the value of scaleY

    mov     r10, #32768
    sub     r10, #1
    and     r8, r6, r10         // r8 uinc(scaleX mod 32767)
    mov     r11, #-1
    mul     r11, r8         // r11 -uinc

    vdup.s16 d2, r8
    vdup.s16 d0, r11
    vzip.s16 d0, d2         // uinc -uinc uinc -uinc

    and     r9, r7, r10         // r9 vinc(scaleY mod 32767)
    mov     r11, #-1
    mul     r11, r9         // r11 -vinc

    vdup.s16 d2, r9
    vdup.s16 d3, r11
    vext.8   d5, d3, d2, #4     // vinc vinc -vinc -vinc

    mov      r11, #0x40000000
    mov      r12, #0x4000
    sub      r12, #1
    add      r11, r12
    vdup.s32 d1, r11;           //init u  16384 16383 16384 16383

    mov      r11, #16384
    vdup.s16 d16, r11
    sub      r11, #1
    vdup.s16 d17, r11
    vext.8   d7, d17, d16, #4       //init v  16384 16384 16383 16383

    veor    q14,     q14
    sub     r1,     r2          // stride - width
    mov     r8,     #16384      // yInverse
    sub     r3,     #1

_HEIGHT:
    ldr     r4, [sp, #40]           //the addr of src
    mov     r11,    r8
    lsr     r11,    #15
    mul     r11,    r5
    add     r11,    r4                  // get current row address
    mov     r12,    r11
    add     r12,    r5

    mov     r9,     #16384              // xInverse
    sub     r10, r2, #1
    vmov.s16 d6, d1

_WIDTH:
    mov     lr,     r9
    lsr     lr,     #15
    add     r4,     r11,lr
    vld2.8  {d28[0],d29[0]},    [r4]        //q14: 0000000b0000000a;
    add     r4,     r12,lr
    vld2.8  {d28[4],d29[4]},    [r4]        //q14: 000d000b000c000a;
    vzip.32     d28, d29                    //q14: 000d000c000b000a;

    vmull.u16   q13, d6, d7         //q13: init u  *  init  v
    vmull.u32   q12, d26,d28
    vmlal.u32   q12, d27,d29
    vqadd.u64   d24, d24,d25
    vrshr.u64   d24, #30

    vst1.8  {d24[0]},   [r0]!
    add     r9, r6
    vadd.u16    d6, d0              // inc u
    vshl.u16    d6, #1
    vshr.u16    d6, #1
    subs    r10, #1
    bne     _WIDTH

WIDTH_END:
    lsr     r9,     #15
    add     r4,r11,r9
    vld1.8  {d24[0]},   [r4]
    vst1.8  {d24[0]},   [r0]
    add     r0,     #1
    add     r8,     r7
    add     r0,     r1
    vadd.s16    d7, d5              // inc v
    vshl.u16    d7, #1
    vshr.u16    d7, #1
    subs    r3,     #1
    bne     _HEIGHT

LAST_ROW:
    ldr     r4, [sp, #40]           //the addr of src
    lsr     r8, #15
    mul     r8, r5
    add     r4, r8                  // get current row address
    mov     r9,     #16384

_LAST_ROW_WIDTH:
    mov     r11,    r9
    lsr     r11,    #15

    add     r3,     r4,r11
    vld1.8  {d0[0]},    [r3]
    vst1.8  {d0[0]},    [r0]
    add     r0,     #1
    add     r9,     r6
    subs    r2,     #1
    bne     _LAST_ROW_WIDTH

    ldmia sp!, {r4-r12, lr}
WELS_ASM_FUNC_END

WELS_ASM_FUNC_BEGIN DyadicBilinearOneThirdDownsampler_neon
    stmdb sp!, {r4-r8, lr}

    //Get the width and height
    ldr  r4, [sp, #24]  //src_width
    ldr  r5, [sp, #28]  //src_height

    //Initialize the register
    mov r6, r2
    mov r8, r0
    mov lr, #0

    //Save the tailer for the un-aligned size
    mla  r7, r1, r5, r0
    vld1.32 {q15}, [r7]

    add r7, r2, r3
    //processing a colume data
comp_ds_bilinear_onethird_loop0:

    vld3.8 {d0, d1, d2}, [r2]!
    vld3.8 {d3, d4, d5}, [r2]!
    vld3.8 {d16, d17, d18}, [r7]!
    vld3.8 {d19, d20, d21}, [r7]!

    vaddl.u8 q11, d0, d1
    vaddl.u8 q12, d3, d4
    vaddl.u8 q13, d16, d17
    vaddl.u8 q14, d19, d20
    vrshr.u16 q11, #1
    vrshr.u16 q12, #1
    vrshr.u16 q13, #1
    vrshr.u16 q14, #1

    vrhadd.u16 q11, q13
    vrhadd.u16 q12, q14

    vmovn.u16 d0, q11
    vmovn.u16 d1, q12
    vst1.8 {q0}, [r0]!

    add lr, #48
    cmp lr, r4
    movcs lr, #0
    addcs r6, r6, r3, lsl #1
    addcs r6, r6, r3
    movcs r2, r6
    addcs r7, r2, r3
    addcs r8, r1
    movcs r0, r8
    subscs r5, #1
    bne comp_ds_bilinear_onethird_loop0

    //restore the tailer for the un-aligned size
    vst1.32 {q15}, [r0]

    ldmia sp!, {r4-r8,lr}
WELS_ASM_FUNC_END

WELS_ASM_FUNC_BEGIN DyadicBilinearQuarterDownsampler_neon
    stmdb sp!, {r4-r8, lr}

    //Get the width and height
    ldr  r4, [sp, #24]  //src_width
    ldr  r5, [sp, #28]  //src_height

    //Initialize the register
    mov r6, r2
    mov r8, r0
    mov lr, #0
    lsr r5, #2

    //Save the tailer for the un-aligned size
    mla  r7, r1, r5, r0
    vld1.32 {q15}, [r7]

    add r7, r2, r3
    //processing a colume data
comp_ds_bilinear_quarter_loop0:

    vld2.16 {q0, q1}, [r2]!
    vld2.16 {q2, q3}, [r2]!
    vld2.16 {q8, q9}, [r7]!
    vld2.16 {q10, q11}, [r7]!

    vpaddl.u8 q0, q0
    vpaddl.u8 q2, q2
    vpaddl.u8 q8, q8
    vpaddl.u8 q10, q10
    vrshr.u16 q0, #1
    vrshr.u16 q2, #1
    vrshr.u16 q8, #1
    vrshr.u16 q10, #1

    vrhadd.u16 q0, q8
    vrhadd.u16 q2, q10
    vmovn.u16 d0, q0
    vmovn.u16 d1, q2
    vst1.8 {q0}, [r0]!

    add lr, #64
    cmp lr, r4
    movcs lr, #0
    addcs r6, r6, r3, lsl #2
    movcs r2, r6
    addcs r7, r2, r3
    addcs r8, r1
    movcs r0, r8
    subscs r5, #1
    bne comp_ds_bilinear_quarter_loop0

    //restore the tailer for the un-aligned size
    vst1.32 {q15}, [r0]

    ldmia sp!, {r4-r8,lr}
WELS_ASM_FUNC_END

#endif
