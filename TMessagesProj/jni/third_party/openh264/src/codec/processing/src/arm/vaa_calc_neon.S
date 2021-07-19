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

.macro ABS_SUB_SUM_16BYTES arg0, arg1, arg2, arg3, arg4
    vld1.32 {q15}, [\arg0], \arg2
    vld1.32 {q14}, [\arg1], \arg2
    vabal.u8 \arg3, d30, d28
    vabal.u8 \arg4, d31, d29
.endm

.macro ABS_SUB_SUM_8x16BYTES arg0, arg1, arg2, arg3, arg4
    vld1.32 {q15}, [\arg0], \arg2
    vld1.32 {q14}, [\arg1], \arg2
    vabdl.u8 \arg3, d30, d28
    vabdl.u8 \arg4, d31, d29

    ABS_SUB_SUM_16BYTES \arg0, \arg1, \arg2, \arg3, \arg4
    ABS_SUB_SUM_16BYTES \arg0, \arg1, \arg2, \arg3, \arg4
    ABS_SUB_SUM_16BYTES \arg0, \arg1, \arg2, \arg3, \arg4
    ABS_SUB_SUM_16BYTES \arg0, \arg1, \arg2, \arg3, \arg4
    ABS_SUB_SUM_16BYTES \arg0, \arg1, \arg2, \arg3, \arg4
    ABS_SUB_SUM_16BYTES \arg0, \arg1, \arg2, \arg3, \arg4
    ABS_SUB_SUM_16BYTES \arg0, \arg1, \arg2, \arg3, \arg4
.endm

.macro SAD_8X16BITS arg0, arg1, arg2
    vadd.u16 d31, \arg0, \arg1
    vpaddl.u16 d31, d31
    vpaddl.u32 \arg2, d31
.endm


WELS_ASM_FUNC_BEGIN VAACalcSad_neon

    stmdb sp!, {r4-r8}

    ldr r4, [sp, #20] //load pic_stride
    ldr r5, [sp, #28] //load psad8x8

    //Initial the Q8 register for save the "psadframe"
    vmov.s64 q8, #0

    //Get the jump distance to use on loop codes
    lsl r8, r4, #4
    sub r7, r8, #16 //R7 keep the 16*pic_stride-16
    sub r8, r2      //R8 keep the 16*pic_stride-pic_width

vaa_calc_sad_loop0:

    //R6 keep the pic_width
    mov r6, r2

vaa_calc_sad_loop1:

    //Process the 16x16 bytes
    ABS_SUB_SUM_8x16BYTES r0, r1, r4, q0, q1
    ABS_SUB_SUM_8x16BYTES r0, r1, r4, q2, q3

    //Do the SAD
    SAD_8X16BITS d0, d1, d0
    SAD_8X16BITS d2, d3, d1
    SAD_8X16BITS d4, d5, d2
    SAD_8X16BITS d6, d7, d3

    //Write to "psad8x8" buffer
    vst4.32 {d0[0],d1[0],d2[0],d3[0]}, [r5]!


    //Adjust the input address
    sub r0, r7
    sub r1, r7

    subs r6, #16

    //Save to calculate "psadframe"
    vadd.u32 q0, q1
    vadd.u32 q8, q0

    bne vaa_calc_sad_loop1

    //Adjust the input address
    add r0, r8
    add r1, r8

    subs r3, #16
    bne vaa_calc_sad_loop0

    ldr r6, [sp, #24] //load psadframe
    vadd.u32 d16, d17
    vst1.32 {d16[0]}, [r6]

    ldmia sp!, {r4-r8}

WELS_ASM_FUNC_END


.macro SAD_SD_MAD_16BYTES arg0, arg1, arg2, arg3, arg4, arg5, arg6
    vld1.32 {q0}, [\arg0], \arg2
    vld1.32 {q1}, [\arg1], \arg2

    vpadal.u8 \arg3, q0
    vpadal.u8 \arg4, q1

    vabd.u8 q0, q0, q1
    vmax.u8 \arg5, q0
    vpadal.u8 \arg6, q0
.endm

.macro SAD_SD_MAD_8x16BYTES arg0, arg1, arg2, arg3, arg4, arg5
    vld1.32 {q0}, [\arg0], \arg2
    vld1.32 {q1}, [\arg1], \arg2

    vpaddl.u8 q2, q0
    vpaddl.u8 q3, q1

    vabd.u8 \arg3, q0, q1
    vpaddl.u8 \arg4, \arg3       //abs_diff


    SAD_SD_MAD_16BYTES \arg0,\arg1,\arg2,q2,q3,\arg3,\arg4
    SAD_SD_MAD_16BYTES \arg0,\arg1,\arg2,q2,q3,\arg3,\arg4
    SAD_SD_MAD_16BYTES \arg0,\arg1,\arg2,q2,q3,\arg3,\arg4
    SAD_SD_MAD_16BYTES \arg0,\arg1,\arg2,q2,q3,\arg3,\arg4
    SAD_SD_MAD_16BYTES \arg0,\arg1,\arg2,q2,q3,\arg3,\arg4
    SAD_SD_MAD_16BYTES \arg0,\arg1,\arg2,q2,q3,\arg3,\arg4
    SAD_SD_MAD_16BYTES \arg0,\arg1,\arg2,q2,q3,\arg3,\arg4

    vsub.u16 \arg5, q2, q3
.endm

.macro SAD_SD_MAD_CALC arg0, arg1, arg2, arg3, arg4
    vpmax.u8 d0, \arg0, \arg1 //8bytes
    vpmax.u8 d0, d0, d0 //4bytes
    vpmax.u8 \arg2, d0, d0 //2bytes

    vpaddl.u16 \arg3, \arg3
    vpaddl.u32 \arg3, \arg3
    vpaddl.s16 \arg4, \arg4
    vpaddl.s32 \arg4, \arg4
.endm

WELS_ASM_FUNC_BEGIN VAACalcSadBgd_neon

    stmdb sp!, {r4-r10}

    ldr r4, [sp, #28] //load pic_stride
    ldr r5, [sp, #36] //load psad8x8
    ldr r6, [sp, #40] //load psd8x8
    ldr r7, [sp, #44] //load pmad8x8

    //Initial the Q4 register for save the "psadframe"
    vmov.s64 q15, #0

    //Get the jump distance to use on loop codes
    lsl r10, r4, #4
    sub r9, r10, #16 //R9 keep the 16*pic_stride-16
    sub r10, r2      //R10 keep the 16*pic_stride-pic_width

vaa_calc_sad_bgd_loop0:

    //R6 keep the pic_width
    mov r8, r2

vaa_calc_sad_bgd_loop1:

    //Process the 16x16 bytes        pmad psad psd
    SAD_SD_MAD_8x16BYTES r0, r1, r4, q13, q11, q9
    SAD_SD_MAD_8x16BYTES r0, r1, r4, q14, q12, q10

    SAD_SD_MAD_CALC d26, d27, d16, q11, q9
    SAD_SD_MAD_CALC d28, d29, d17, q12, q10

    //Write to "psad8x8" buffer
    vst4.32 {d22[0],d23[0],d24[0],d25[0]}, [r5]!
    //Adjust the input address
    sub r0, r9
    sub r1, r9
    //Write to "psd8x8" buffer
    vst4.32 {d18[0],d19[0],d20[0],d21[0]}, [r6]!
    subs r8, #16
    //Write to "pmad8x8" buffer
    vst2.16 {d16[0],d17[0]}, [r7]!
    //Save to calculate "psadframe"
    vadd.u32 q11, q12
    vadd.u32 q15, q11

    bne vaa_calc_sad_bgd_loop1

    //Adjust the input address
    add r0, r10
    add r1, r10

    subs r3, #16
    bne vaa_calc_sad_bgd_loop0

    ldr r8, [sp, #32] //load psadframe
    vadd.u32 d30, d31
    vst1.32 {d30[0]}, [r8]
    ldmia sp!, {r4-r10}

WELS_ASM_FUNC_END


.macro SSD_MUL_SUM_16BYTES_RESET arg0, arg1, arg2, arg3
    vmull.u8   \arg3, \arg0, \arg0
    vpaddl.u16 \arg2, \arg3

    vmull.u8   \arg3, \arg1, \arg1
    vpadal.u16 \arg2, \arg3
.endm

.macro SSD_MUL_SUM_16BYTES arg0, arg1, arg2, arg3
    vmull.u8   \arg3, \arg0, \arg0
    vpadal.u16 \arg2, \arg3

    vmull.u8   \arg3, \arg1, \arg1
    vpadal.u16 \arg2, \arg3
.endm

.macro SAD_SSD_BGD_16 arg0, arg1, arg2, arg3
    vld1.8 {q0}, [\arg0], \arg2 //load cur_row

    vpadal.u8 q3, q0    //add cur_row together
    vpadal.u8 q4, q1    //add ref_row together

    vabd.u8 q2, q0, q1  //abs_diff

    vmax.u8 q5, q2                              //l_mad for 16 bytes reset for every 8x16

    vpadal.u8 \arg3, q2                         //l_sad for 16 bytes reset for every 8x16

    SSD_MUL_SUM_16BYTES d4,d5, q8, q11          //q8 for l_sqiff    reset for every 16x16

    vld1.8 {q1}, [\arg1], \arg2 //load ref_row
    vpadal.u8 q9, q0                            //q9 for l_sum      reset for every 16x16

    SSD_MUL_SUM_16BYTES d0,d1, q10, q11         //q10 for lsqsum    reset for every 16x16
.endm

//the last row of a 16x16 block
.macro SAD_SSD_BGD_16_end arg0, arg1, arg2
    vld1.8 {q0}, [\arg0], \arg1 //load cur_row

    vpadal.u8 q3, q0    //add cur_row together
    vpadal.u8 q4, q1    //add ref_row together

    vabd.u8 q2, q0, q1  //abs_diff

    vmax.u8 q5, q2                              //l_mad for 16 bytes reset for every 8x16

    vpadal.u8 \arg2, q2                         //l_sad for 16 bytes reset for every 8x16

    SSD_MUL_SUM_16BYTES d4,d5, q8, q11          //q8 for l_sqiff    reset for every 16x16

    vpadal.u8 q9, q0                            //q9 for l_sum      reset for every 16x16

    SSD_MUL_SUM_16BYTES d0,d1, q10, q11         //q10 for lsqsum    reset for every 16x16
.endm

//for the begin of a 8x16 block, use some instructions to reset the register
.macro SAD_SSD_BGD_16_RESET_8x8 arg0, arg1, arg2, arg3
    vld1.8 {q0}, [\arg0], \arg2 //load cur_row

    vpaddl.u8 q3, q0    //add cur_row together
    vpaddl.u8 q4, q1    //add ref_row together

    vabd.u8 q2, q0, q1  //abs_diff

    vmov q5,q2         //calculate max and avoid reset to zero, l_mad for 16 bytes reset for every 8x16

    vpaddl.u8 \arg3, q2                         //l_sad for 16 bytes reset for every 8x16


    SSD_MUL_SUM_16BYTES d4,d5, q8, q11          //q8 for l_sqiff    reset for every 16x16

    vld1.8 {q1}, [\arg1], \arg2 //load ref_row

    vpadal.u8 q9, q0                            //q9 for l_sum      reset for every 16x16

    SSD_MUL_SUM_16BYTES d0,d1, q10, q11         //q10 for lsqsum    reset for every 16x16
.endm

//for the begin of a 16x16 block, use some instructions to reset the register
.macro SAD_SSD_BGD_16_RESET_16x16 arg0, arg1, arg2, arg3
    vld1.8 {q0}, [\arg0], \arg2 //load cur_row
    vld1.8 {q1}, [\arg1], \arg2 //load ref_row

    vpaddl.u8 q3, q0    //add cur_row together
    vpaddl.u8 q4, q1    //add ref_row together

    vabd.u8 q2, q0, q1  //abs_diff

    vmov q5,q2         //calculate max and avoid reset to zero, l_mad for 16 bytes reset for every 8x16

    vpaddl.u8 \arg3, q2                         //l_sad for 16 bytes reset for every 8x16

    SSD_MUL_SUM_16BYTES_RESET d4,d5,q8, q11         //q8 for l_sqiff    reset for every 16x16

    vld1.8 {q1}, [\arg1], \arg2 //load ref_row

    vpaddl.u8 q9, q0                                //q9 for l_sum      reset for every 16x16

    SSD_MUL_SUM_16BYTES_RESET d0,d1,q10,q11         //q10 for lsqsum    reset for every 16x16
.endm

//for each 8x16 block
.macro SAD_SSD_BGD_CALC_8x16 arg0, arg1, arg2

    vpmax.u8 d10, d10, d11 //4 numbers
    vpmax.u8 d10, d10, d10 //2 numbers
    vpmax.u8 d10, d10, d10 //1 number1

    vmov \arg0, d10         //d26 d27 keeps the l_mad

    //p_sd8x8
    vpaddl.u16 q3, q3
    vpaddl.u16 q4, q4

    vsub.i32 \arg1, q3, q4
    vpaddl.u32 \arg1, \arg1

    //psad8x8
    vpaddl.u16 \arg2, \arg2
    vpaddl.u32 \arg2, \arg2

    //psadframe
    vadd.i32 q12, \arg2
.endm

.macro SAD_SSD_BGD_16x16 arg0, arg1, arg2
    //for one 8x16
    SAD_SSD_BGD_16_RESET_16x16 \arg0, \arg1, \arg2, q6
    SAD_SSD_BGD_16 \arg0, \arg1, \arg2, q6
    SAD_SSD_BGD_16 \arg0, \arg1, \arg2, q6
    SAD_SSD_BGD_16 \arg0, \arg1, \arg2, q6
    SAD_SSD_BGD_16 \arg0, \arg1, \arg2, q6
    SAD_SSD_BGD_16 \arg0, \arg1, \arg2, q6
    SAD_SSD_BGD_16 \arg0, \arg1, \arg2, q6
    SAD_SSD_BGD_16 \arg0, \arg1, \arg2, q6

    SAD_SSD_BGD_CALC_8x16 d26, q14, q6

    //for another 8x16
    SAD_SSD_BGD_16_RESET_8x8 \arg0, \arg1, \arg2, q7
    SAD_SSD_BGD_16 \arg0, \arg1, \arg2, q7
    SAD_SSD_BGD_16 \arg0, \arg1, \arg2, q7
    SAD_SSD_BGD_16 \arg0, \arg1, \arg2, q7
    SAD_SSD_BGD_16 \arg0, \arg1, \arg2, q7
    SAD_SSD_BGD_16 \arg0, \arg1, \arg2, q7
    SAD_SSD_BGD_16 \arg0, \arg1, \arg2, q7
    SAD_SSD_BGD_16_end \arg0, \arg2, q7

    SAD_SSD_BGD_CALC_8x16 d27, q15, q7
.endm

.macro SSD_SAD_SD_MAD_PADDL arg0, arg1, arg2
    vpaddl.s16 \arg0, \arg0
    vpaddl.s32 \arg0, \arg0
    vadd.i32 \arg1, \arg1, \arg2
.endm


WELS_ASM_FUNC_BEGIN VAACalcSadSsdBgd_neon
    stmdb sp!, {r0-r12, r14}
    vpush {q4-q7}

    ldr r4, [sp, #120] //r4 keeps the pic_stride

    sub r5, r4, #1
    lsl r5, r5, #4 //r5 keeps the little step

    lsl r6, r4, #4
    sub r6, r2, r6  //r6 keeps the big step


    ldr r8, [sp, #128]//psad8x8
    ldr r9, [sp, #132]//psum16x16
    ldr r10, [sp, #136]//psqsum16x16
    ldr r11, [sp, #140]//psqdiff16x16
    ldr r12, [sp, #144]//p_sd8x8
    ldr r14, [sp, #148]//p_mad8x8

    vmov.i8 q12, #0

vaa_calc_sad_ssd_bgd_height_loop:

    mov r7, r2
vaa_calc_sad_ssd_bgd_width_loop:

    //l_sd q14&q15, l_mad q13, l_sad q6 & q7, l_sqdiff  q8, l_sum q9, l_sqsum q10
    SAD_SSD_BGD_16x16 r0,r1,r4

    //psad8x8
    vst4.32 {d12[0], d13[0], d14[0], d15[0]}, [r8]!

    sub r0, r0, r5 //jump to next 16x16
    sub r1, r1, r5 //jump to next 16x16

    //p_sd8x8
    vst4.32 {d28[0], d29[0],d30[0], d31[0]}, [r12]!

    //p_mad8x8
    vst2.16 {d26[0], d27[0]}, [r14]!

    //psqdiff16x16
    vpaddl.s32 q8, q8
    vadd.i32 d16, d16, d17

    vst1.32 {d16[0]}, [r11]! //psqdiff16x16

    //psum16x16
    SSD_SAD_SD_MAD_PADDL q9, d18, d19
    vst1.32 {d18[0]}, [r9]! //psum16x16

    //psqsum16x16
    vpaddl.s32 q10, q10
    vadd.i32 d20, d20, d21
    vst1.32 {d20[0]}, [r10]! //psqsum16x16

    subs r7, #16

    bne vaa_calc_sad_ssd_bgd_width_loop

    sub r0, r0, r6      //jump to next 16 x width
    sub r1, r1, r6      //jump to next 16 x width

    subs r3, #16
bne vaa_calc_sad_ssd_bgd_height_loop

    //psadframe
    ldr r7, [sp, #124]//psadframe

    vadd.i32 d24, d24, d25
    vst1.32 {d24[0]}, [r7]

    vpop {q4-q7}
    ldmia sp!, {r0-r12, r14}

WELS_ASM_FUNC_END


.macro SAD_VAR_16 arg0, arg1, arg2, arg3
    vld1.8 {q0}, [\arg0], \arg2 //load cur_row

    vpadal.u8 q3, q0    //add cur_row together
    vpadal.u8 q4, q1    //add ref_row together

    vabd.u8 q2, q0, q1  //abs_diff

    vpadal.u8 \arg3, q2                         //l_sad for 16 bytes reset for every 8x16

    vld1.8 {q1}, [\arg1], \arg2

    vpadal.u8 q9, q0                            //q9 for l_sum      reset for every 16x16

    SSD_MUL_SUM_16BYTES d0,d1, q10, q11         //q10 for lsqsum    reset for every 16x16
.endm

.macro SAD_VAR_16_END arg0, arg1, arg2
    vld1.8 {q0}, [\arg0], \arg1 //load cur_row

    vpadal.u8 q3, q0    //add cur_row together
    vpadal.u8 q4, q1    //add ref_row together

    vabd.u8 q2, q0, q1  //abs_diff

    vpadal.u8 \arg2, q2                         //l_sad for 16 bytes reset for every 8x16

    vpadal.u8 q9, q0                            //q9 for l_sum      reset for every 16x16

    SSD_MUL_SUM_16BYTES d0,d1, q10, q11         //q10 for lsqsum    reset for every 16x16
.endm


.macro SAD_VAR_16_RESET_16x16 arg0, arg1, arg2, arg3
    vld1.8 {q0}, [\arg0], \arg2 //load cur_row
    vld1.8 {q1}, [\arg1], \arg2

    vpaddl.u8 q3, q0    //add cur_row together
    vpaddl.u8 q4, q1    //add ref_row together

    vabd.u8 q2, q0, q1  //abs_diff

    vpaddl.u8 \arg3, q2                         //l_sad for 16 bytes reset for every 8x16

    vld1.8 {q1}, [\arg1], \arg2

    vpaddl.u8 q9, q0                            //q9 for l_sum      reset for every 16x16

    SSD_MUL_SUM_16BYTES_RESET d0,d1, q10, q11
.endm

.macro SAD_VAR_16_RESET_8x8 arg0, arg1, arg2, arg3
    vld1.8 {q0}, [\arg0], \arg2 //load cur_row

    vpaddl.u8 q3, q0    //add cur_row together
    vpaddl.u8 q4, q1    //add ref_row together

    vabd.u8 q2, q0, q1  //abs_diff

    vpaddl.u8 \arg3, q2                         //l_sad for 16 bytes reset for every 8x16

    vld1.8 {q1}, [\arg1], \arg2

    vpadal.u8 q9, q0                            //q9 for l_sum      reset for every 16x16

    SSD_MUL_SUM_16BYTES d0,d1, q10, q11         //q10 for lsqsum    reset for every 16x16
.endm

.macro SAD_VAR_16x16 arg0, arg1, arg2
    //for one 8x16
    SAD_VAR_16_RESET_16x16 \arg0, \arg1, \arg2, q6
    SAD_VAR_16 \arg0, \arg1, \arg2, q6
    SAD_VAR_16 \arg0, \arg1, \arg2, q6
    SAD_VAR_16 \arg0, \arg1, \arg2, q6
    SAD_VAR_16 \arg0, \arg1, \arg2, q6
    SAD_VAR_16 \arg0, \arg1, \arg2, q6
    SAD_VAR_16 \arg0, \arg1, \arg2, q6
    SAD_VAR_16 \arg0, \arg1, \arg2, q6

    vpaddl.u16 q6, q6
    vpaddl.u32 q6, q6
    vadd.i32 q12, q6

    //for another 8x16
    SAD_VAR_16_RESET_8x8 \arg0, \arg1, \arg2, q7
    SAD_VAR_16 \arg0, \arg1, \arg2, q7
    SAD_VAR_16 \arg0, \arg1, \arg2, q7
    SAD_VAR_16 \arg0, \arg1, \arg2, q7
    SAD_VAR_16 \arg0, \arg1, \arg2, q7
    SAD_VAR_16 \arg0, \arg1, \arg2, q7
    SAD_VAR_16 \arg0, \arg1, \arg2, q7
    SAD_VAR_16_END \arg0, \arg2, q7

    vpaddl.u16 q7, q7
    vpaddl.u32 q7, q7

    vadd.i32 q12, q7
.endm


WELS_ASM_FUNC_BEGIN VAACalcSadVar_neon
    stmdb sp!, {r4-r11}
    vpush {q4}
    vpush {q6-q7}

    ldr r4, [sp, #80] //r4 keeps the pic_stride

    sub r5, r4, #1
    lsl r5, r5, #4 //r5 keeps the little step

    lsl r6, r4, #4
    sub r6, r2, r6  //r6 keeps the big step

    ldr r7,     [sp, #84]   //psadframe
    ldr r8,     [sp, #88]   //psad8x8
    ldr r9,     [sp, #92]   //psum16x16
    ldr r10,    [sp, #96]   //psqsum16x16

    vmov.i8 q12, #0
vaa_calc_sad_var_height_loop:

    mov r11, r2
vaa_calc_sad_var_width_loop:


    SAD_VAR_16x16 r0,r1,r4
    //psad8x8
    vst4.32 {d12[0], d13[0], d14[0], d15[0]}, [r8]!

    sub r0, r0, r5 //jump to next 16x16
    sub r1, r1, r5 //jump to next 16x16

    //psum16x16
    SSD_SAD_SD_MAD_PADDL q9, d18, d19
    vst1.32 {d18[0]}, [r9]! //psum16x16

    //psqsum16x16
    vpaddl.s32 q10, q10
    subs r11, #16
    vadd.i32 d20, d20, d21
    vst1.32 {d20[0]}, [r10]! //psqsum16x16

    bne vaa_calc_sad_var_width_loop

    sub r0, r0, r6      //jump to next 16 x width
    sub r1, r1, r6      //jump to next 16 x width

    subs r3, #16
bne vaa_calc_sad_var_height_loop

    vadd.i32 d24, d24, d25
    vst1.32 {d24[0]}, [r7]

    vpop {q6-q7}
    vpop {q4}
    ldmia sp!, {r4-r11}
WELS_ASM_FUNC_END


.macro SAD_SSD_16 arg0, arg1, arg2, arg3
    SAD_VAR_16 \arg0, \arg1, \arg2, \arg3

    SSD_MUL_SUM_16BYTES d4,d5,q8, q11
.endm

.macro SAD_SSD_16_END arg0, arg1, arg2
    SAD_VAR_16_END \arg0, \arg1, \arg2

    SSD_MUL_SUM_16BYTES d4,d5,q8, q11           //q8 for l_sqiff    reset for every 16x16
.endm

.macro SAD_SSD_16_RESET_16x16 arg0, arg1, arg2, arg3
    SAD_VAR_16_RESET_16x16 \arg0, \arg1, \arg2, \arg3

    SSD_MUL_SUM_16BYTES_RESET d4,d5,q8, q11         //q8 for l_sqiff    reset for every 16x16
.endm

.macro SAD_SSD_16_RESET_8x8 arg0, arg1, arg2, arg3
    SAD_VAR_16_RESET_8x8 \arg0, \arg1, \arg2, \arg3

    SSD_MUL_SUM_16BYTES d4,d5,q8, q11           //q8 for l_sqiff    reset for every 16x16
.endm

.macro SAD_SSD_16x16 arg0, arg1, arg2
    //for one 8x16
    SAD_SSD_16_RESET_16x16 \arg0, \arg1, \arg2, q6
    SAD_SSD_16 \arg0, \arg1, \arg2, q6
    SAD_SSD_16 \arg0, \arg1, \arg2, q6
    SAD_SSD_16 \arg0, \arg1, \arg2, q6
    SAD_SSD_16 \arg0, \arg1, \arg2, q6
    SAD_SSD_16 \arg0, \arg1, \arg2, q6
    SAD_SSD_16 \arg0, \arg1, \arg2, q6
    SAD_SSD_16 \arg0, \arg1, \arg2, q6

    vpaddl.u16 q6, q6
    vpaddl.u32 q6, q6
    vadd.i32 q12, q6

    //for another 8x16
    SAD_SSD_16_RESET_8x8 \arg0, \arg1, \arg2, q7
    SAD_SSD_16 \arg0, \arg1, \arg2, q7
    SAD_SSD_16 \arg0, \arg1, \arg2, q7
    SAD_SSD_16 \arg0, \arg1, \arg2, q7
    SAD_SSD_16 \arg0, \arg1, \arg2, q7
    SAD_SSD_16 \arg0, \arg1, \arg2, q7
    SAD_SSD_16 \arg0, \arg1, \arg2, q7
    SAD_SSD_16_END \arg0, \arg2, q7

    vpaddl.u16 q7, q7
    vpaddl.u32 q7, q7

    vadd.i32 q12, q7
.endm


WELS_ASM_FUNC_BEGIN VAACalcSadSsd_neon
    stmdb sp!, {r4-r12}
    vpush {q4}
    vpush {q6-q7}

    ldr r4, [sp, #84] //r4 keeps the pic_stride

    sub r5, r4, #1
    lsl r5, r5, #4 //r5 keeps the little step

    lsl r6, r4, #4
    sub r6, r2, r6  //r6 keeps the big step

    ldr r7,     [sp, #88]   //psadframe
    ldr r8,     [sp, #92]   //psad8x8
    ldr r9,     [sp, #96]   //psum16x16
    ldr r10,    [sp, #100]  //psqsum16x16
    ldr r11,    [sp, #104]  //psqdiff16x16

    vmov.i8 q12, #0
vaa_calc_sad_ssd_height_loop:

    mov r12, r2
vaa_calc_sad_ssd_width_loop:


    SAD_SSD_16x16 r0,r1,r4
    //psad8x8
    vst4.32 {d12[0], d13[0], d14[0], d15[0]}, [r8]!

    sub r0, r0, r5 //jump to next 16x16
    sub r1, r1, r5 //jump to next 16x16

    //psum16x16
    vpaddl.s16 q9, q9
    vpaddl.s32 q9, q9
    vadd.i32 d18, d18, d19
    vst1.32 {d18[0]}, [r9]! //psum16x16

    //psqsum16x16
    vpaddl.s32 q10, q10
    vadd.i32 d20, d20, d21
    vst1.32 {d20[0]}, [r10]! //psqsum16x16

    //psqdiff16x16
    vpaddl.s32 q8, q8
    vadd.i32 d16, d16, d17
    subs r12, #16
    vst1.32 {d16[0]}, [r11]! //psqdiff16x16

    bne vaa_calc_sad_ssd_width_loop

    sub r0, r0, r6      //jump to next 16 x width
    sub r1, r1, r6      //jump to next 16 x width

    subs r3, #16
    bne vaa_calc_sad_ssd_height_loop

    vadd.i32 d24, d24, d25
    vst1.32 {d24[0]}, [r7]

    vpop {q6-q7}
    vpop {q4}
    ldmia sp!, {r4-r12}
WELS_ASM_FUNC_END

#endif
