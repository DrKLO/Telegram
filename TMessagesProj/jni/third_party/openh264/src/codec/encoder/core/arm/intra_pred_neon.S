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

//Global macro
.macro GET_8BYTE_DATA arg0, arg1, arg2
    vld1.8 {\arg0[0]}, [\arg1], \arg2
    vld1.8 {\arg0[1]}, [\arg1], \arg2
    vld1.8 {\arg0[2]}, [\arg1], \arg2
    vld1.8 {\arg0[3]}, [\arg1], \arg2
    vld1.8 {\arg0[4]}, [\arg1], \arg2
    vld1.8 {\arg0[5]}, [\arg1], \arg2
    vld1.8 {\arg0[6]}, [\arg1], \arg2
    vld1.8 {\arg0[7]}, [\arg1], \arg2
.endm


WELS_ASM_FUNC_BEGIN WelsI16x16LumaPredDc_neon
    //stmdb sp!, { r2-r5, lr}
    //Get the left vertical line data
    sub r3, r1, #1
    GET_8BYTE_DATA d0, r3, r2
    GET_8BYTE_DATA d1, r3, r2

    //Get the top horizontal line data
    sub  r3, r1, r2
    vldm r3, {d2, d3}

    //Calculate the sum of top horizontal line data and vertical line data
    vpaddl.u8 q0, q0
    vpaddl.u8 q1, q1
    vadd.u16  q0, q0, q1
    vadd.u16  d0, d0, d1
    vpaddl.u16 d0, d0
    vpaddl.u32 d0, d0

    //Calculate the mean value
    vrshr.u16  d0, d0, #5
    vdup.8     q0, d0[0]

    //Set the mean value to the all of member of MB
    mov  r3, #4
loop_0_get_i16x16_luma_pred_dc_both:
    vst1.8 {d0,d1}, [r0]!
    vst1.8 {d0,d1}, [r0]!
    vst1.8 {d0,d1}, [r0]!
    vst1.8 {d0,d1}, [r0]!
    subs  r3, #1
    bne  loop_0_get_i16x16_luma_pred_dc_both

WELS_ASM_FUNC_END


.align 3
//The table for SIMD instruction {(8,7,6,5,4,3,2,1) * 5}
CONST0_GET_I16X16_LUMA_PRED_PLANE: .long 0x191e2328, 0x050a0f14

//The table for SIMD instruction {-7,-6,-5,-4,-3,-2,-1,0}
CONST1_GET_I16X16_LUMA_PRED_PLANE: .long 0xfcfbfaf9, 0x00fffefd


WELS_ASM_FUNC_BEGIN WelsI16x16LumaPredPlane_neon
    //stmdb sp!, { r4, lr}

    //Load the table {(8,7,6,5,4,3,2,1) * 5}
    adr r3, CONST0_GET_I16X16_LUMA_PRED_PLANE
    vldr    d0, [r3]

    //Pack the top[-1] ~ top[6] to d1
    sub       r3,  r1, r2
    sub       r1,  r3, #1
    vld1.8    d1, [r1]

    //Pack the top[8] ~ top[15] to d2
    add       r1, #9
    vld1.8    d2, [r1]

    //Save the top[15] to d6 for next step
    vdup.u8   d6,   d2[7]

    //Get and pack left[-1] ~ left[6] to d4
    sub       r1,  r3, #1
    GET_8BYTE_DATA d4, r1, r2

    //Get and pack left[8] ~ left[15] to d3
    add       r1,  r2
    GET_8BYTE_DATA d3, r1, r2

    //Save the left[15] to d7 for next step
    vdup.u8   d7,   d3[7]

    //revert the sequence of d2,d3
    vrev64.8   q1, q1

    vsubl.u8   q2, d3, d4 //q2={left[8]-left[6],left[9]-left[5],left[10]-left[4], ...}
    vsubl.u8   q1, d2, d1 //q1={top[8]-top[6],top[9]-top[5],top[10]-top[4], ...}


    vmovl.u8   q0, d0
    vmul.s16   q1, q0, q1 //q1 = q1*{(8,7,6,5,4,3,2,1) * 5}
    vmul.s16   q2, q0, q2 //q2 = q2*{(8,7,6,5,4,3,2,1) * 5}

    //Calculate the sum of items of q1, q2
    vpadd.s16  d0, d2, d3
    vpadd.s16  d1, d4, d5
    vpaddl.s16 q0, q0
    vpaddl.s32 q0, q0

    //Get the value of 'b', 'c' and extend to q1, q2.
    vrshr.s64  q0, #6
    vdup.s16   q1, d0[0]
    vdup.s16   q2, d1[0]

    //Load the table {-7,-6,-5,-4,-3,-2,-1,0} to d0
    adr r3, CONST1_GET_I16X16_LUMA_PRED_PLANE
    vld1.32   {d0}, [r3]

    //Get the value of 'a' and save to q3
    vaddl.u8  q3, d6, d7
    vshl.u16  q3, #4

    //calculate a+'b'*{-7,-6,-5,-4,-3,-2,-1,0} + c*{-7}
    vmovl.s8  q0, d0
    vmla.s16  q3, q0, q1
    vmla.s16  q3, q2, d0[0]

    //Calculate a+'b'*{1,2,3,4,5,6,7,8} + c*{-7}
    vshl.s16  q8, q1, #3
    vadd.s16  q8, q3

    //right shift 5 bits and rounding
    vqrshrun.s16 d0, q3, #5
    vqrshrun.s16 d1, q8, #5

    //Set the line of MB
    vst1.u32  {d0,d1}, [r0]!


    //Do the same processing for setting other lines
    mov  r3, #15
loop_0_get_i16x16_luma_pred_plane:
    vadd.s16  q3, q2
    vadd.s16  q8, q2
    vqrshrun.s16 d0, q3, #5
    vqrshrun.s16 d1, q8, #5
    vst1.u32  {d0,d1}, [r0]!
    subs  r3, #1
    bne  loop_0_get_i16x16_luma_pred_plane

WELS_ASM_FUNC_END


WELS_ASM_FUNC_BEGIN WelsI4x4LumaPredV_neon
    //stmdb sp!, { r2-r5, lr}
    //Load the top row (4 bytes)
    sub  r3, r1, r2
    ldr  r3, [r3]

    //Set the luma MB using top line
    str  r3, [r0], #4
    str  r3, [r0], #4
    str  r3, [r0], #4
    str  r3, [r0]

WELS_ASM_FUNC_END


WELS_ASM_FUNC_BEGIN WelsI4x4LumaPredH_neon
    //stmdb sp!, { r2-r5, lr}
    //Load the left column (4 bytes)
    sub  r3, r1, #1
    vld1.8 {d0[]}, [r3], r2
    vld1.8 {d1[]}, [r3], r2
    vld1.8 {d2[]}, [r3], r2
    vld1.8 {d3[]}, [r3]

    //Set the luma MB using the left side byte
    vst1.32 {d0[0]}, [r0]!
    vst1.32 {d1[0]}, [r0]!
    vst1.32 {d2[0]}, [r0]!
    vst1.32 {d3[0]}, [r0]

WELS_ASM_FUNC_END


WELS_ASM_FUNC_BEGIN WelsI4x4LumaPredDDL_neon
    //stmdb sp!, { r2-r5, lr}
    //Load the top row data(8 bytes)
    sub    r3,  r1, r2
    vld1.32  {d0}, [r3]

    //For "t7 + (t7<<1)"
    vdup.8   d1,  d0[7]

    //calculate "t0+t1,t1+t2,t2+t3...t6+t7,t7+t7"
    vext.8   d1,  d0, d1, #1
    vaddl.u8 q1,  d1, d0

    //calculate "x,t0+t1+t1+t2,t1+t2+t2+t3,...t5+t6+t6+t7,t6+t7+t7+t7"
    vext.8   q2,  q1, q1, #14
    vadd.u16 q0,  q1, q2

    //right shift 2 bits and rounding
    vqrshrn.u16  d0,  q0, #2

    //Save "ddl0, ddl1, ddl2, ddl3"
    vext.8   d1, d0, d0, #1
    vst1.32  d1[0], [r0]!

    //Save "ddl1, ddl2, ddl3, ddl4"
    vext.8   d1, d0, d0, #2
    vst1.32  d1[0], [r0]!

    //Save "ddl2, ddl3, ddl4, ddl5"
    vext.8   d1, d0, d0, #3
    vst1.32  d1[0], [r0]!

    //Save "ddl3, ddl4, ddl5, ddl6"
    vst1.32  d0[1], [r0]

WELS_ASM_FUNC_END


WELS_ASM_FUNC_BEGIN WelsI4x4LumaPredDDR_neon
    //stmdb sp!, { r2-r5, lr}
    //Load the top row (4 bytes)
    sub    r3,  r1, r2
    vld1.32  {d0[1]}, [r3]

    //Load the left column (5 bytes)
    sub    r3,  #1
    vld1.8 {d0[3]}, [r3], r2
    vld1.8 {d0[2]}, [r3], r2
    vld1.8 {d0[1]}, [r3], r2
    vld1.8 {d0[0]}, [r3], r2
    vld1.8 {d1[7]}, [r3] //For packing the right sequence to do SIMD processing


    vext.8   d2, d1, d0, #7   //d0:{L2,L1,L0,LT,T0,T1,T2,T3}
                              //d2:{L3,L2,L1,L0,LT,T0,T1,T2}

    //q2:{L2+L3,L1+L2,L0+L1...T1+T2,T2+T3}
    vaddl.u8 q2, d2, d0

    //q1:{TL0+LT0,LT0+T01,...L12+L23}
    vext.8   q3, q3, q2, #14
    vadd.u16 q1, q2, q3

    //right shift 2 bits and rounding
    vqrshrn.u16 d0, q1, #2

    //Adjust the data sequence for setting luma MB of 'pred'
    vst1.32   d0[1], [r0]!
    vext.8    d0, d0, d0, #7
    vst1.32   d0[1], [r0]!
    vext.8    d0, d0, d0, #7
    vst1.32   d0[1], [r0]!
    vext.8    d0, d0, d0, #7
    vst1.32   d0[1], [r0]

WELS_ASM_FUNC_END


WELS_ASM_FUNC_BEGIN WelsI4x4LumaPredVL_neon
    //stmdb sp!, { r2-r5, lr}
    //Load the top row (8 bytes)
    sub    r3,  r1, r2
    vld1.32  {d0}, [r3]


    vext.8   d1,  d0, d0, #1
    vaddl.u8 q1,  d1, d0     //q1:{t0+t1,t1+t2,t2+t3...t5+t6,x,x}

    vext.8   q2,  q1, q1, #2
    vadd.u16 q2,  q1, q2     //q2:{t0+t1+t1+t2,t1+t2+t2+t3,...t4+t5+t5+t6,x,x}

    //calculate the "vl0,vl1,vl2,vl3,vl4"
    vqrshrn.u16  d0,  q1, #1

    //calculate the "vl5,vl6,vl7,vl8,vl9"
    vqrshrn.u16  d1,  q2, #2

    //Adjust the data sequence for setting the luma MB
    vst1.32  d0[0], [r0]!
    vst1.32  d1[0], [r0]!
    vext.8   d0,  d0, d0, #1
    vext.8   d1,  d1, d1, #1
    vst1.32  d0[0], [r0]!
    vst1.32  d1[0], [r0]

WELS_ASM_FUNC_END


WELS_ASM_FUNC_BEGIN WelsI4x4LumaPredVR_neon
    //stmdb sp!, { r2-r5, lr}
    //Load the top row (4 bytes)
    sub       r3,  r1, r2
    vld1.32   {d0[1]}, [r3]

    //Load the left column (4 bytes)
    sub       r3,  #1
    vld1.8    {d0[3]}, [r3], r2
    vld1.8    {d0[2]}, [r3], r2
    vld1.8    {d0[1]}, [r3], r2
    vld1.8    {d0[0]}, [r3]


    vext.8    d1, d0, d0, #7
    vaddl.u8  q1, d0, d1      //q1:{X,L2+L1,L1+L0,L0+LT,LT+T0,T0+T1,T1+T2,T2+T3}

    vext.u8   q2, q1, q1, #14
    vadd.u16  q2, q2, q1      //q2:{X,L2+L1+L1+L0,L1+L0+L0+LT,...T1+T2+T2+T3}

    //Calculate the vr0 ~ vr9
    vqrshrn.u16 d1, q2, #2
    vqrshrn.u16 d0, q1, #1

    //Adjust the data sequence for setting the luma MB
    vst1.32  d0[1], [r0]!
    vst1.32  d1[1], [r0]!
    //add    r2, r0, r1
    vst1.8   d1[3], [r0]!
    vst1.16  d0[2], [r0]!
    vst1.8   d0[6], [r0]!
    vst1.8   d1[2], [r0]!
    vst1.16  d1[2], [r0]!
    vst1.8   d1[6], [r0]
WELS_ASM_FUNC_END


WELS_ASM_FUNC_BEGIN WelsI4x4LumaPredHU_neon
    //stmdb sp!, { r4, lr}
    //Load the left column data
    sub       r3,  r1, #1
    mov       r1,  #3
    mul       r1,  r2
    add       r1,  r3
    vld1.8    {d0[]},  [r1]
    vld1.8    {d0[4]}, [r3], r2
    vld1.8    {d0[5]}, [r3], r2
    vld1.8    {d0[6]}, [r3], r2 //d0:{L3,L3,L3,L3,L0,L1,L2,L3}

    vext.8    d1, d0, d0, #1
    vaddl.u8  q2, d0, d1        //q2:{L3+L3,L3+L3,L3+L3,L3+L0,L0+L1,L1+L2,L2+L3,L3+L3}

    vext.u8   d2, d5, d4, #2
    vadd.u16  d3, d2, d5        //d3:{L0+L1+L1+L2,L1+L2+L2+L3,L2+L3+L3+L3,L3+L3+L3+L3}

    //Calculate the hu0 ~ hu5
    vqrshrn.u16 d2, q2, #1
    vqrshrn.u16 d1, q1, #2

    //Adjust the data sequence for setting the luma MB
    vzip.8   d2, d1
    vst1.32  d1[0], [r0]!
    vext.8   d2, d1, d1, #2
    vst1.32  d2[0], [r0]!
    vst1.32  d1[1], [r0]!
    vst1.32  d0[0], [r0]

WELS_ASM_FUNC_END


WELS_ASM_FUNC_BEGIN WelsI4x4LumaPredHD_neon
    //stmdb sp!, { r2-r5, lr}
    //Load the data
    sub       r3,  r1, r2
    sub       r3,  #1
    vld1.32   {d0[1]}, [r3], r2
    vld1.8    {d0[3]}, [r3], r2
    vld1.8    {d0[2]}, [r3], r2
    vld1.8    {d0[1]}, [r3], r2
    vld1.8    {d0[0]}, [r3]     //d0:{L3,L2,L1,L0,LT,T0,T1,T2}


    vext.8    d1, d0, d0, #7
    vaddl.u8  q1, d0, d1        //q1:{x,L3+L2,L2+L1,L1+L0,L0+LT,LT+T0,T0+T1,T1+T2}

    vext.u8   q2, q1, q1, #14   //q2:{x,x, L3+L2,L2+L1,L1+L0,L0+LT,LT+T0,T0+T1}
    vadd.u16  q3, q2, q1        //q3:{x,x,L3+L2+L2+L1,L2+L1+L1+L0,L1+L0+L0+LT,L0+LT+LT+T0,LT+T0+T0+T1,T0+T1+T1+T2}

    //Calculate the hd0~hd9
    vqrshrn.u16 d1, q3, #2
    vqrshrn.u16 d0, q2, #1

    //Adjust the data sequence for setting the luma MB
    vmov      d3, d1
    vtrn.8    d0, d1
    vext.u8   d2, d1, d1, #6
    vst2.16  {d2[3], d3[3]}, [r0]!
    vst2.16  {d0[2], d1[2]}, [r0]!
    vmov     d3, d0
    vst2.16  {d2[2], d3[2]}, [r0]!
    vst2.16  {d0[1], d1[1]}, [r0]

WELS_ASM_FUNC_END


WELS_ASM_FUNC_BEGIN WelsIChromaPredV_neon
    //stmdb sp!, { r2-r5, lr}
    //Get the top row (8 byte)
    sub  r3, r1, r2
    vldr d0, [r3]

    //Set the chroma MB using top row data
    vst1.8 {d0}, [r0]!
    vst1.8 {d0}, [r0]!
    vst1.8 {d0}, [r0]!
    vst1.8 {d0}, [r0]!
    vst1.8 {d0}, [r0]!
    vst1.8 {d0}, [r0]!
    vst1.8 {d0}, [r0]!
    vst1.8 {d0}, [r0]

WELS_ASM_FUNC_END


WELS_ASM_FUNC_BEGIN WelsIChromaPredH_neon
    //stmdb sp!, { r2-r5, lr}
    ////Get the left column (8 byte)
    sub  r3, r1, #1
    vld1.8 {d0[]}, [r3], r2
    vld1.8 {d1[]}, [r3], r2
    vld1.8 {d2[]}, [r3], r2
    vld1.8 {d3[]}, [r3], r2
    vld1.8 {d4[]}, [r3], r2
    vld1.8 {d5[]}, [r3], r2
    vld1.8 {d6[]}, [r3], r2
    vld1.8 {d7[]}, [r3]

    //Set the chroma MB using left column data
    vst1.8 {d0}, [r0]!
    vst1.8 {d1}, [r0]!
    vst1.8 {d2}, [r0]!
    vst1.8 {d3}, [r0]!
    vst1.8 {d4}, [r0]!
    vst1.8 {d5}, [r0]!
    vst1.8 {d6}, [r0]!
    vst1.8 {d7}, [r0]

WELS_ASM_FUNC_END


WELS_ASM_FUNC_BEGIN WelsIChromaPredDc_neon
    //stmdb sp!, { r2-r5, lr}
    //Load the left column data (8 bytes)
    sub r3, r1, #1
    GET_8BYTE_DATA d0, r3, r2

    //Load the top row data (8 bytes)
    sub  r3, r1, r2
    vldr d1, [r3]

    //Calculate the sum of left column and top row
    vpaddl.u8  q0, q0
    vpaddl.u16 q0, q0
    vadd.u32   d2, d0, d1 //'m1' save to d2

    vrshr.u32  q0, q0, #2 //calculate 'm2','m3'
    vrshr.u32  d2, d2, #3 //calculate 'm4'

    //duplicate the 'mx' to a vector line
    vdup.8     d4, d2[0]
    vdup.8     d5, d1[4]
    vdup.8     d6, d0[4]
    vdup.8     d7, d2[4]

    //Set the chroma MB
    vst2.32 {d4[0],d5[0]}, [r0]!
    vst2.32 {d4[0],d5[0]}, [r0]!
    vst2.32 {d4[0],d5[0]}, [r0]!
    vst2.32 {d4[0],d5[0]}, [r0]!
    vst2.32 {d6[0],d7[0]}, [r0]!
    vst2.32 {d6[0],d7[0]}, [r0]!
    vst2.32 {d6[0],d7[0]}, [r0]!
    vst2.32 {d6[0],d7[0]}, [r0]

WELS_ASM_FUNC_END


//Table {{1,2,3,4,1,2,3,4}*17}
CONST0_GET_I_CHROMA_PRED_PLANE: .long 0x44332211, 0x44332211//0x140f0a05, 0x28231e19
//Table {-3,-2,-1,0,1,2,3,4}
CONST1_GET_I_CHROMA_PRED_PLANE: .long 0xfffefffd, 0x0000ffff,0x00020001,0x00040003

WELS_ASM_FUNC_BEGIN WelsIChromaPredPlane_neon
    //stmdb sp!, { r2-r5, lr}
    //Load the top row data
    sub  r3, r1, #1
    sub  r3, r2
    vld1.32 {d1[0]}, [r3]
    add  r3, #5
    vld1.32 {d0[0]}, [r3]

    //Load the left column data
    sub  r3, #5
    vld1.8 {d1[4]}, [r3], r2
    vld1.8 {d1[5]}, [r3], r2
    vld1.8 {d1[6]}, [r3], r2
    vld1.8 {d1[7]}, [r3], r2 //d1:{LT,T0,T1,T2,LT,L0,L1,L2}
    add  r3, r2
    vld1.8 {d0[4]}, [r3], r2
    vld1.8 {d0[5]}, [r3], r2
    vld1.8 {d0[6]}, [r3], r2
    vld1.8 {d0[7]}, [r3]     //d0:{T4,T5,T6,T7,L4,L5,L6.L7}


    //Save T7 to d3 for next step
    vdup.u8   d3,   d0[3]
    //Save L7 to d4 for next step
    vdup.u8   d4,   d0[7]

    //Calculate the value of 'a' and save to q2
    vaddl.u8  q2, d3, d4
    vshl.u16  q2, #4

    //Load the table {{1,2,3,4,1,2,3,4}*17}
    adr r3, CONST0_GET_I_CHROMA_PRED_PLANE
    vld1.32   {d2}, [r3]

    //Calculate the 'b','c', and save to q0
    vrev32.8  d1, d1
    vsubl.u8  q0, d0, d1
    vmovl.u8   q1, d2
    vmul.s16   q0, q1
    vpaddl.s16 q0, q0
    vpaddl.s32 q0, q0
    vrshr.s64  q0, #5

    //Load the table {-3,-2,-1,0,1,2,3,4} to q3
    adr r3, CONST1_GET_I_CHROMA_PRED_PLANE
    vld1.32   {d6, d7}, [r3]

    //Duplicate the 'b','c' to q0, q1 for SIMD instruction
    vdup.s16   q1, d1[0]
    vdup.s16   q0, d0[0]

    //Calculate the "(a + b * (j - 3) + c * (- 3) + 16) >> 5;"
    vmla.s16   q2, q0, q3
    vmla.s16   q2, q1, d6[0]
    vqrshrun.s16 d0, q2, #5

    //Set a line of chroma MB
    vst1.u32  {d0}, [r0]!

    //Do the same processing for each line.
    mov  r3, #7
loop_0_get_i_chroma_pred_plane:
    vadd.s16   q2, q1
    vqrshrun.s16 d0, q2, #5
    vst1.u32  {d0}, [r0]!
    subs  r3, #1
    bne  loop_0_get_i_chroma_pred_plane

WELS_ASM_FUNC_END

#endif
