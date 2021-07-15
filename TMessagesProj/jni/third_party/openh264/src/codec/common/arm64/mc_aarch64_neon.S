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

#ifdef HAVE_NEON_AARCH64
#include "arm_arch64_common_macro.S"
.align 4
filter_para: .short 0, 1, -5, 20, 0, 0, 0, 0

.macro FILTER_6TAG_8BITS1 arg0, arg1, arg2, arg3, arg4, arg5, arg6, arg7, arg8
//  {   // input:src[-2], src[-1], src[0], src[1], src[2], src[3], dst_d, multiplier a/b; working: v18, v19
    uaddl v18.8h, \arg0\().8b, \arg5\().8b //v18=src[-2]+src[3]
    uaddl v19.8h, \arg2\().8b, \arg3\().8b  //src[0]+src[1]
    mla v18.8h, v19.8h, \arg7\().8h  //v18 += 20*(src[0]+src[1]), 2 cycles
    uaddl v19.8h, \arg1\().8b, \arg4\().8b  //src[-1]+src[2]
    mls v18.8h, v19.8h, \arg8\().8h  //v18 -= 5*(src[-1]+src[2]), 2 cycles
    sqrshrun \arg6\().8b, v18.8h, #5
//  }
.endm

.macro FILTER_6TAG_8BITS2 arg0, arg1, arg2, arg3, arg4, arg5, arg6, arg7, arg8
//  {   // input:src[-2], src[-1], src[0], src[1], src[2], src[3], dst_d, multiplier a/b; working: v18, v19
    uaddl2 v18.8h, \arg0\().16b, \arg5\().16b //v18=src[-2]+src[3]
    uaddl2 v19.8h, \arg2\().16b, \arg3\().16b   //src[0]+src[1]
    mla v18.8h, v19.8h, \arg7\().8h  //v18 += 20*(src[0]+src[1]), 2 cycles
    uaddl2 v19.8h, \arg1\().16b, \arg4\().16b  //src[-1]+src[2]
    mls v18.8h, v19.8h, \arg8\().8h  //v18 -= 5*(src[-1]+src[2]), 2 cycles
    sqrshrun2 \arg6\().16b, v18.8h, #5
//  }
.endm

.macro FILTER_6TAG_8BITS1_AVERAGE_WITH_0 arg0, arg1, arg2, arg3, arg4, arg5, arg6, arg7, arg8
//  {   // input:src[-2], src[-1], src[0], src[1], src[2], src[3], dst_d, multiplier a/b; working: v18, v19
    uaddl v18.8h, \arg0\().8b, \arg5\().8b //v18=src[-2]+src[3]
    uaddl v19.8h, \arg2\().8b, \arg3\().8b  //src[0]+src[1]
    mla v18.8h, v19.8h, \arg7\().8h  //v18 += 20*(src[0]+src[1]), 2 cycles
    uaddl v19.8h, \arg1\().8b, \arg4\().8b  //src[-1]+src[2]
    mls v18.8h, v19.8h, \arg8\().8h  //v18 -= 5*(src[-1]+src[2]), 2 cycles
    sqrshrun \arg6\().8b, v18.8h, #5
    uaddl  v19.8h, \arg2\().8b, \arg6\().8b
    rshrn \arg6\().8b, v19.8h, #1
//  }
.endm

.macro FILTER_6TAG_8BITS2_AVERAGE_WITH_0 arg0, arg1, arg2, arg3, arg4, arg5, arg6, arg7, arg8
//  {   // input:src[-2], src[-1], src[0], src[1], src[2], src[3], dst_d, multiplier a/b; working: v18, v19
    uaddl2 v18.8h, \arg0\().16b, \arg5\().16b //v18=src[-2]+src[3]
    uaddl2 v19.8h, \arg2\().16b, \arg3\().16b   //src[0]+src[1]
    mla v18.8h, v19.8h, \arg7\().8h  //v18 += 20*(src[0]+src[1]), 2 cycles
    uaddl2 v19.8h, \arg1\().16b, \arg4\().16b  //src[-1]+src[2]
    mls v18.8h, v19.8h, \arg8\().8h  //v18 -= 5*(src[-1]+src[2]), 2 cycles
    sqrshrun2 \arg6\().16b, v18.8h, #5
    uaddl2  v19.8h, \arg2\().16b, \arg6\().16b
    rshrn2 \arg6\().16b, v19.8h, #1
//  }
.endm

.macro FILTER_6TAG_8BITS1_AVERAGE_WITH_1 arg0, arg1, arg2, arg3, arg4, arg5, arg6, arg7, arg8
//  {   // input:src[-2], src[-1], src[0], src[1], src[2], src[3], dst_d, multiplier a/b; working: v18, v19
    uaddl v18.8h, \arg0\().8b, \arg5\().8b //v18=src[-2]+src[3]
    uaddl v19.8h, \arg2\().8b, \arg3\().8b  //src[0]+src[1]
    mla v18.8h, v19.8h, \arg7\().8h  //v18 += 20*(src[0]+src[1]), 2 cycles
    uaddl v19.8h, \arg1\().8b, \arg4\().8b  //src[-1]+src[2]
    mls v18.8h, v19.8h, \arg8\().8h  //v18 -= 5*(src[-1]+src[2]), 2 cycles
    sqrshrun \arg6\().8b, v18.8h, #5
    uaddl  v19.8h, \arg3\().8b, \arg6\().8b
    rshrn \arg6\().8b, v19.8h, #1
//  }
.endm

.macro FILTER_6TAG_8BITS2_AVERAGE_WITH_1 arg0, arg1, arg2, arg3, arg4, arg5, arg6, arg7, arg8
//  {   // input:src[-2], src[-1], src[0], src[1], src[2], src[3], dst_d, multiplier a/b; working: v18, v19
    uaddl2 v18.8h, \arg0\().16b, \arg5\().16b //v18=src[-2]+src[3]
    uaddl2 v19.8h, \arg2\().16b, \arg3\().16b   //src[0]+src[1]
    mla v18.8h, v19.8h, \arg7\().8h  //v18 += 20*(src[0]+src[1]), 2 cycles
    uaddl2 v19.8h, \arg1\().16b, \arg4\().16b  //src[-1]+src[2]
    mls v18.8h, v19.8h, \arg8\().8h  //v18 -= 5*(src[-1]+src[2]), 2 cycles
    sqrshrun2 \arg6\().16b, v18.8h, #5
    uaddl2  v19.8h, \arg3\().16b, \arg6\().16b
    rshrn2 \arg6\().16b, v19.8h, #1
//  }
.endm

.macro FILTER_6TAG_8BITS_TO_16BITS1 arg0, arg1, arg2, arg3, arg4, arg5, arg6, arg7, arg8
//  {   // input:d_src[-2], d_src[-1], d_src[0], d_src[1], d_src[2], d_src[3], dst_q, multiplier a/b; working:v31
    uaddl   \arg6\().8h, \arg0\().8b, \arg5\().8b       //dst_q=src[-2]+src[3]
    uaddl   v31.8h, \arg2\().8b, \arg3\().8b    //src[0]+src[1]
    mla \arg6\().8h, v31.8h, \arg7\().8h    //dst_q += 20*(src[0]+src[1]), 2 cycles
    uaddl   v31.8h, \arg1\().8b, \arg4\().8b    //src[-1]+src[2]
    mls \arg6\().8h, v31.8h, \arg8\().8h    //dst_q -= 5*(src[-1]+src[2]), 2 cycles
//  }
.endm

.macro FILTER_6TAG_8BITS_TO_16BITS2 arg0, arg1, arg2, arg3, arg4, arg5, arg6, arg7, arg8
//  {   // input:d_src[-2], d_src[-1], d_src[0], d_src[1], d_src[2], d_src[3], dst_q, multiplier a/b; working:v31
    uaddl2  \arg6\().8h, \arg0\().16b, \arg5\().16b     //dst_q=src[-2]+src[3]
    uaddl2  v31.8h, \arg2\().16b, \arg3\().16b  //src[0]+src[1]
    mla \arg6\().8h, v31.8h, \arg7\().8h    //dst_q += 20*(src[0]+src[1]), 2 cycles
    uaddl2  v31.8h, \arg1\().16b, \arg4\().16b  //src[-1]+src[2]
    mls \arg6\().8h, v31.8h, \arg8\().8h    //dst_q -= 5*(src[-1]+src[2]), 2 cycles
//  }
.endm

.macro FILTER_3_IN_16BITS_TO_8BITS1 arg0, arg1, arg2, arg3
//  {   // input:a, b, c, dst_d;
    sub \arg0\().8h, \arg0\().8h, \arg1\().8h           //a-b
    sshr    \arg0\().8h, \arg0\().8h, #2            //(a-b)/4
    sub \arg0\().8h, \arg0\().8h, \arg1\().8h           //(a-b)/4-b
    add \arg0\().8h, \arg0\().8h, \arg2\().8h           //(a-b)/4-b+c
    sshr    \arg0\().8h, \arg0\().8h, #2            //((a-b)/4-b+c)/4
    add \arg0\().8h, \arg0\().8h, \arg2\().8h           //((a-b)/4-b+c)/4+c = (a-5*b+20*c)/16
    sqrshrun    \arg3\().8b, \arg0\().8h, #6        //(+32)>>6
//  }
.endm

.macro FILTER_3_IN_16BITS_TO_8BITS2 arg0, arg1, arg2, arg3
//  {   // input:a, b, c, dst_d;
    sub \arg0\().8h, \arg0\().8h, \arg1\().8h           //a-b
    sshr    \arg0\().8h, \arg0\().8h, #2            //(a-b)/4
    sub \arg0\().8h, \arg0\().8h, \arg1\().8h           //(a-b)/4-b
    add \arg0\().8h, \arg0\().8h, \arg2\().8h           //(a-b)/4-b+c
    sshr    \arg0\().8h, \arg0\().8h, #2            //((a-b)/4-b+c)/4
    add \arg0\().8h, \arg0\().8h, \arg2\().8h           //((a-b)/4-b+c)/4+c = (a-5*b+20*c)/16
    sqrshrun2   \arg3\().16b, \arg0\().8h, #6       //(+32)>>6
//  }
.endm

.macro UNPACK_2_16BITS_TO_ABC arg0, arg1, arg2, arg3, arg4
//  {   // input:q_src[-2:5], q_src[6:13](avail 8+5)/q_src[6:**](avail 4+5), dst_a, dst_b, dst_c;
    ext \arg4\().16b, \arg0\().16b, \arg1\().16b, #4        //src[0]
    ext \arg3\().16b, \arg0\().16b, \arg1\().16b, #6        //src[1]
    add \arg4\().8h, \arg4\().8h, \arg3\().8h                   //c=src[0]+src[1]

    ext \arg3\().16b, \arg0\().16b, \arg1\().16b, #2        //src[-1]
    ext \arg2\().16b, \arg0\().16b, \arg1\().16b, #8        //src[2]
    add \arg3\().8h, \arg3\().8h, \arg2\().8h                   //b=src[-1]+src[2]

    ext \arg2\().16b, \arg0\().16b, \arg1\().16b, #10       //src[3]
    add \arg2\().8h, \arg2\().8h, \arg0\().8h                   //a=src[-2]+src[3]
//  }
.endm

.macro AVERAGE_TWO_8BITS1 arg0, arg1, arg2
//  {   // input:dst_d, src_d A and B; working: v5
    uaddl   v30.8h, \arg2\().8b, \arg1\().8b
    rshrn   \arg0\().8b, v30.8h, #1
//  }
.endm

.macro AVERAGE_TWO_8BITS2 arg0, arg1, arg2
//  {   // input:dst_d, src_d A and B; working: v5
    uaddl2  v30.8h, \arg2\().16b, \arg1\().16b
    rshrn2  \arg0\().16b, v30.8h, #1
//  }
.endm

.macro FILTER_SINGLE_TAG_8BITS arg0, arg1, arg2, arg3
// when width=17/9, used
//  {   // input: src_d{Y[0][1][2][3][4][5]X},
    rev64   \arg2\().8b, \arg0\().8b                // X[5][4][3][2][1][0]O
    uaddl   \arg2\().8h, \arg0\().8b, \arg2\().8b           // each 16bits, *[50][41][32][23][14][05]*
    mul \arg2\().4h, \arg2\().4h, \arg1\().4h           // 0+1*[50]-5*[41]+20[32]
    addv \arg3, \arg2\().4h
    sqrshrun \arg0\().8b, \arg0\().8h, #5
//  }
.endm

.macro UNPACK_FILTER_SINGLE_TAG_16BITS arg0, arg1, arg2, arg3, arg4, arg5
//  {   // each 16bits; input: d_dst, d_src[0:5], para, working, working, d(low part of d_dst)
    ext \arg3\().16b, \arg1\().16b, \arg1\().16b, #14       // X[0][1][2][3][4][5]O
    ext \arg4\().16b, \arg3\().16b, \arg3\().16b, #8      // [3][4][5]OX[0][1][2]
    rev64  \arg4\().8h, \arg4\().8h         // X[5][4][3][2][1][0]O
    add   \arg3\().8h, \arg3\().8h, \arg4\().8h    // each 16bits, *[50][41][32][23][14][05]*
    smull \arg3\().4s, \arg3\().4h, \arg2\().4h         // 0+1*[50]-5*[41]+20[32]
    saddlv \arg5, \arg3\().4s
    //sshr \arg0\().2d, \arg0\().2d, #4
    sqrshrun \arg0\().2s, \arg0\().2d, #10
    uqxtn \arg0\().4h, \arg0\().4s
    uqxtn \arg0\().8b, \arg0\().8h
    //   }
.endm

.macro VEC4_LD1_8BITS_16ELEMENT arg0, arg1, arg2, arg3, arg4, arg5
//{//load 16bytes * 4rows
    ld1 {\arg2\().16b}, [\arg0], \arg1
    ld1 {\arg3\().16b}, [\arg0], \arg1
    ld1 {\arg4\().16b}, [\arg0], \arg1
    ld1 {\arg5\().16b}, [\arg0], \arg1
//}
.endm

.macro VEC4_ST1_8BITS_8ELEMENT arg0, arg1, arg2, arg3, arg4, arg5
//{
    st1 {\arg2\().8b}, [\arg0], \arg1
    st1 {\arg3\().8b}, [\arg0], \arg1
    st1 {\arg4\().8b}, [\arg0], \arg1
    st1 {\arg5\().8b}, [\arg0], \arg1
//}
.endm

.macro VEC4_UADDL_8BITS arg0, arg1, arg2, arg3, arg4, arg5, arg6, arg7, arg8, arg9, arg10, arg11
//{
    uaddl \arg8\().8h, \arg0\().8b, \arg1\().8b
    uaddl \arg9\().8h, \arg2\().8b, \arg3\().8b
    uaddl \arg10\().8h, \arg4\().8b, \arg5\().8b
    uaddl \arg11\().8h, \arg6\().8b, \arg7\().8b
//}
.endm

.macro VEC4_UADDL2_8BITS arg0, arg1, arg2, arg3, arg4, arg5, arg6, arg7, arg8, arg9, arg10, arg11
//{
    uaddl \arg8\().8h, \arg0\().16b, \arg1\().16b
    uaddl \arg9\().8h, \arg2\().16b, \arg3\().16b
    uaddl \arg10\().8h, \arg4\().16b, \arg5\().16b
    uaddl \arg11\().8h, \arg6\().16b, \arg7\().16b
//}
.endm

.macro VEC4_MLS_16BITS arg0, arg1, arg2, arg3, arg4, arg5, arg6, arg7, arg8, arg9, arg10, arg11
//{
    mls   \arg8\().8h, \arg0\().8h, \arg1\().8h
    mls   \arg9\().8h, \arg2\().8h, \arg3\().8h
    mls   \arg10\().8h, \arg4\().8h, \arg5\().8h
    mls   \arg11\().8h, \arg6\().8h, \arg7\().8h
//}
.endm

.macro VEC4_MLA_16BITS arg0, arg1, arg2, arg3, arg4, arg5, arg6, arg7, arg8, arg9, arg10, arg11
//{
    mla   \arg8\().8h, \arg0\().8h, \arg1\().8h
    mla   \arg9\().8h, \arg2\().8h, \arg3\().8h
    mla   \arg10\().8h, \arg4\().8h, \arg5\().8h
    mla   \arg11\().8h, \arg6\().8h, \arg7\().8h
//}
.endm

.macro VEC4_SQRSHRUN_16BITS_SHIFT5 arg0, arg1, arg2, arg3, arg4, arg5, arg6, arg7
//{
    sqrshrun \arg4\().8b, \arg0\().8h, #5
    sqrshrun \arg5\().8b, \arg1\().8h, #5
    sqrshrun \arg6\().8b, \arg2\().8h, #5
    sqrshrun \arg7\().8b, \arg3\().8h, #5
//}
.endm

.macro VEC4_SQRSHRUN2_16BITS_SHIFT5 arg0, arg1, arg2, arg3, arg4, arg5, arg6, arg7
//{
    sqrshrun2 \arg4\().16b, \arg0\().8h, #5
    sqrshrun2 \arg5\().16b, \arg1\().8h, #5
    sqrshrun2 \arg6\().16b, \arg2\().8h, #5
    sqrshrun2 \arg7\().16b, \arg3\().8h, #5
//}
.endm

.macro VEC4_RSHRN_16BITS_SHIFT1 arg0, arg1, arg2, arg3, arg4, arg5, arg6, arg7
//{
    rshrn \arg4\().8b, \arg0\().8h, #1
    rshrn \arg5\().8b, \arg1\().8h, #1
    rshrn \arg6\().8b, \arg2\().8h, #1
    rshrn \arg7\().8b, \arg3\().8h, #1
//}
.endm

//(const uint8_t* pSrc {x0}, int32_t iSrcStride{x1}, uint8_t* pDst{x2}, int32_t iDstStride{x3}, int32_t iHeight{x4})
WELS_ASM_AARCH64_FUNC_BEGIN McHorVer20WidthEq16_AArch64_neon
    sub x0, x0, #2
    movi v0.8h, #20, lsl #0
    movi v1.8h, #5, lsl #0
    SIGN_EXTENSION x1,w1
    SIGN_EXTENSION x3,w3
    SIGN_EXTENSION x4,w4
w16_h_mc_luma_loop:
    ld1 {v2.8b, v3.8b, v4.8b}, [x0], x1 //only use 21(16+5); v2=src[-2]
    trn1 v2.2d, v2.2d, v3.2d
    //prfm pldl1strm, [x0]
    ext v5.16b, v2.16b, v4.16b, #1    //v5=src[-1]
    ext v6.16b, v2.16b, v4.16b, #2    //v6=src[0]
    ext v7.16b, v2.16b, v4.16b, #3    //v7=src[1]
    ext v16.16b, v2.16b, v4.16b, #4   //v16=src[2]
    ext v17.16b, v2.16b, v4.16b, #5   //v17=src[3]

    FILTER_6TAG_8BITS1 v2, v5, v6, v7, v16, v17, v20, v0, v1
    FILTER_6TAG_8BITS2 v2, v5, v6, v7, v16, v17, v20, v0, v1

    sub x4, x4, #1
    st1 {v20.16b}, [x2], x3 //write 16Byte
    cbnz x4, w16_h_mc_luma_loop
WELS_ASM_AARCH64_FUNC_END

//void McHorVer20WidthEq8_AArch64_neon (const uint8_t* pSrc, int32_t iSrcStride, uint8_t* pDst, int32_t iDstStride,int32_t iHeight);
WELS_ASM_AARCH64_FUNC_BEGIN McHorVer20WidthEq8_AArch64_neon
    sub x0, x0, #2
    stp d8,d9, [sp,#-16]!
    movi v8.8h, #20, lsl #0
    movi v9.8h, #5, lsl #0
    SIGN_EXTENSION x1,w1
    SIGN_EXTENSION x3,w3
    SIGN_EXTENSION x4,w4
w8_h_mc_luma_loop:
    VEC4_LD1_8BITS_16ELEMENT x0, x1, v16, v20, v24, v28    //load src[-2] in v16,v20,v24,v28 for 4 row; only use 13(8+5);
    sub x4, x4, #4

    //1st row:
    ext v17.16b, v16.16b, v16.16b, #5  //src[3]
    ext v18.16b, v16.16b, v16.16b, #1  //src[-1]
    ext v19.16b, v16.16b, v16.16b, #4  //src[2]
    //2nd row:
    ext v21.16b, v20.16b, v20.16b, #5  //src[3]
    ext v22.16b, v20.16b, v20.16b, #1  //src[-1]
    ext v23.16b, v20.16b, v20.16b, #4  //src[2]
    //3rd row:
    ext v25.16b, v24.16b, v24.16b, #5  //src[3]
    ext v26.16b, v24.16b, v24.16b, #1  //src[-1]
    ext v27.16b, v24.16b, v24.16b, #4  //src[2]
    //4th row:
    ext v29.16b, v28.16b, v28.16b, #5  //src[3]
    ext v30.16b, v28.16b, v28.16b, #1  //src[-1]
    ext v31.16b, v28.16b, v28.16b, #4  //src[2]

    VEC4_UADDL_8BITS v16, v17, v20, v21, v24, v25, v28, v29, v0, v2, v4, v6   //v0/v2/v4/v6=src[-2]+src[3]
    VEC4_UADDL_8BITS v18, v19, v22, v23, v26, v27, v30, v31, v1, v3, v5, v7 //v1/v3/v5/v7=src[-1]+src[2]
    VEC4_MLS_16BITS v1, v9, v3, v9, v5, v9, v7, v9, v0, v2, v4, v6  //v0/v2/v4/v6 -= 5*(src[-1]+src[2])

    //1st row:
    ext v18.16b, v16.16b, v16.16b, #2  //src[0]
    ext v19.16b, v16.16b, v16.16b, #3  //src[1]
    //2nd row:
    ext v22.16b, v20.16b, v20.16b, #2  //src[0]
    ext v23.16b, v20.16b, v20.16b, #3  //src[1]
    //3rd row:
    ext v26.16b, v24.16b, v24.16b, #2  //src[0]
    ext v27.16b, v24.16b, v24.16b, #3  //src[1]
    //4th row:
    ext v30.16b, v28.16b, v28.16b, #2  //src[0]
    ext v31.16b, v28.16b, v28.16b, #3  //src[1]

    VEC4_UADDL_8BITS v18, v19, v22, v23, v26, v27, v30, v31, v1, v3, v5, v7 //v1/v3/v5/v7=src[0]+src[1]
    VEC4_MLA_16BITS v1, v8, v3, v8, v5, v8, v7, v8, v0, v2, v4, v6  //v0/v2/v4/v6+=20*(src[0]+src[1])

    VEC4_SQRSHRUN_16BITS_SHIFT5 v0, v2, v4, v6, v1, v3, v5, v7

    VEC4_ST1_8BITS_8ELEMENT x2, x3, v1, v3, v5, v7
    cbnz x4, w8_h_mc_luma_loop

    ldp d8,d9,[sp],#16
WELS_ASM_AARCH64_FUNC_END

//void McHorVer20WidthEq4_AArch64_neon (const uint8_t* pSrc, int32_t iSrcStride, uint8_t* pDst, int32_t iDstStride,
//                                      int32_t iHeight);
WELS_ASM_AARCH64_FUNC_BEGIN McHorVer20WidthEq4_AArch64_neon
    sub x0, x0, #2
    movi v0.8h, #20, lsl #0
    movi v1.8h, #5, lsl #0
    SIGN_EXTENSION x1,w1
    SIGN_EXTENSION x3,w3
    SIGN_EXTENSION x4,w4
    asr x4, x4, #1
w4_h_mc_luma_loop:
    ld1 {v2.16b}, [x0], x1 //only use 9(4+5); 1st row src[-2:6]
    //prfm pldl1strm, [x0]
    ld1 {v3.16b}, [x0], x1 //only use 9(4+5); 2nd row src[-2:6]
    //prfm pldl1strm, [x0]

    zip1 v4.4s, v2.4s, v3.4s  // v4=src[-2] 1st:2nd
    ext v17.16b, v4.16b, v4.16b, #8 // v17=src[2:5] 1st:2nd

    ext v2.16b, v2.16b, v4.16b, #1    //1st row src[-1:6]
    ext v3.16b, v3.16b, v4.16b, #1    //2nd row src[-1:6]
    zip1 v5.4s, v2.4s, v3.4s  // v5=src[-1:2] 1st:2nd
    ext v7.16b, v5.16b, v4.16b, #8    //v7=src[3:6] 1st:2nd

    ext v2.16b, v2.16b, v4.16b, #1    //1st row src[0:6]
    ext v3.16b, v3.16b, v4.16b, #1    //2nd row src[0:6]
    zip1 v6.4s, v2.4s, v3.4s  // v6=src[0:3] 1st:2nd

    ext v2.16b, v2.16b, v4.16b, #1    //1st row src[1:6]
    ext v3.16b, v3.16b, v4.16b, #1    //2nd row src[1:6]
    zip1 v16.4s, v2.4s, v3.4s  // v16=src[1:4] 1st:2nd

    FILTER_6TAG_8BITS1 v4, v5, v6, v16, v17, v7, v20, v0, v1

    st1 {v20.s}[0], [x2], x3 //write 4Byte
    st1 {v20.s}[1], [x2], x3 //write 4Byte
    sub x4, x4, #1
    cbnz x4, w4_h_mc_luma_loop
WELS_ASM_AARCH64_FUNC_END

//void McHorVer10WidthEq16_AArch64_neon (const uint8_t* pSrc, int32_t iSrcStride, uint8_t* pDst, int32_t iDstStride,
//                                       int32_t iHeight);
WELS_ASM_AARCH64_FUNC_BEGIN McHorVer10WidthEq16_AArch64_neon
    sub x0, x0, #2
    movi v0.8h, #20, lsl #0
    movi v1.8h, #5, lsl #0
    SIGN_EXTENSION x1,w1
    SIGN_EXTENSION x3,w3
    SIGN_EXTENSION x4,w4
w16_xy_10_mc_luma_loop:
    ld1 {v2.8b, v3.8b, v4.8b}, [x0], x1 //only use 21(16+5); v2=src[-2]
    trn1 v2.2d, v2.2d, v3.2d
    //prfm pldl1strm, [x0]
    ext v5.16b, v2.16b, v4.16b, #1    //v5=src[-1]
    ext v6.16b, v2.16b, v4.16b, #2    //v6=src[0]
    ext v7.16b, v2.16b, v4.16b, #3    //v7=src[1]
    ext v16.16b, v2.16b, v4.16b, #4   //v16=src[2]
    ext v17.16b, v2.16b, v4.16b, #5   //v17=src[3]

    FILTER_6TAG_8BITS1_AVERAGE_WITH_0 v2, v5, v6, v7, v16, v17, v20, v0, v1
    FILTER_6TAG_8BITS2_AVERAGE_WITH_0 v2, v5, v6, v7, v16, v17, v20, v0, v1

    sub x4, x4, #1
    st1 {v20.16b}, [x2], x3 //write 16Byte
    cbnz x4, w16_xy_10_mc_luma_loop
WELS_ASM_AARCH64_FUNC_END

//void McHorVer10WidthEq8_AArch64_neon (const uint8_t* pSrc, int32_t iSrcStride, uint8_t* pDst, int32_t iDstStride,
//                                      int32_t iHeight);
WELS_ASM_AARCH64_FUNC_BEGIN McHorVer10WidthEq8_AArch64_neon
    sub x0, x0, #2
    stp d8,d9, [sp,#-16]!
    movi v8.8h, #20, lsl #0
    movi v9.8h, #5, lsl #0
    SIGN_EXTENSION x1,w1
    SIGN_EXTENSION x3,w3
    SIGN_EXTENSION x4,w4
w8_xy_10_mc_luma_loop:
    VEC4_LD1_8BITS_16ELEMENT x0, x1, v16, v20, v24, v28    //load src[-2] in v16,v20,v24,v28 for 4 row; only use 13(8+5);
    sub x4, x4, #4

    //1st row:
    ext v17.16b, v16.16b, v16.16b, #5  //src[3]
    ext v18.16b, v16.16b, v16.16b, #1  //src[-1]
    ext v19.16b, v16.16b, v16.16b, #4  //src[2]
    //2nd row:
    ext v21.16b, v20.16b, v20.16b, #5  //src[3]
    ext v22.16b, v20.16b, v20.16b, #1  //src[-1]
    ext v23.16b, v20.16b, v20.16b, #4  //src[2]
    //3rd row:
    ext v25.16b, v24.16b, v24.16b, #5  //src[3]
    ext v26.16b, v24.16b, v24.16b, #1  //src[-1]
    ext v27.16b, v24.16b, v24.16b, #4  //src[2]
    //4th row:
    ext v29.16b, v28.16b, v28.16b, #5  //src[3]
    ext v30.16b, v28.16b, v28.16b, #1  //src[-1]
    ext v31.16b, v28.16b, v28.16b, #4  //src[2]

    VEC4_UADDL_8BITS v16, v17, v20, v21, v24, v25, v28, v29, v0, v2, v4, v6   //v0/v2/v4/v6=src[-2]+src[3]
    VEC4_UADDL_8BITS v18, v19, v22, v23, v26, v27, v30, v31, v1, v3, v5, v7 //v1/v3/v5/v7=src[-1]+src[2]
    VEC4_MLS_16BITS v1, v9, v3, v9, v5, v9, v7, v9, v0, v2, v4, v6  //v0/v2/v4/v6 -= 5*(src[-1]+src[2])

    //1st row:
    ext v18.16b, v16.16b, v16.16b, #2  //src[0]
    ext v19.16b, v16.16b, v16.16b, #3  //src[1]
    //2nd row:
    ext v22.16b, v20.16b, v20.16b, #2  //src[0]
    ext v23.16b, v20.16b, v20.16b, #3  //src[1]
    //3rd row:
    ext v26.16b, v24.16b, v24.16b, #2  //src[0]
    ext v27.16b, v24.16b, v24.16b, #3  //src[1]
    //4th row:
    ext v30.16b, v28.16b, v28.16b, #2  //src[0]
    ext v31.16b, v28.16b, v28.16b, #3  //src[1]

    VEC4_UADDL_8BITS v18, v19, v22, v23, v26, v27, v30, v31, v1, v3, v5, v7 //v1/v3/v5/v7=src[0]+src[1]
    VEC4_MLA_16BITS v1, v8, v3, v8, v5, v8, v7, v8, v0, v2, v4, v6  //v0/v2/v4/v6+=20*(src[0]+src[1])
    VEC4_SQRSHRUN_16BITS_SHIFT5 v0, v2, v4, v6, v1, v3, v5, v7

    VEC4_UADDL_8BITS v1, v18, v3, v22, v5, v26, v7, v30, v0, v2, v4, v6   //average with arc[0]
    VEC4_RSHRN_16BITS_SHIFT1 v0, v2, v4, v6, v1, v3, v5, v7

    VEC4_ST1_8BITS_8ELEMENT x2, x3, v1, v3, v5, v7
    cbnz x4, w8_xy_10_mc_luma_loop

    ldp d8,d9,[sp],#16
WELS_ASM_AARCH64_FUNC_END

//void McHorVer10WidthEq4_AArch64_neon (const uint8_t* pSrc, int32_t iSrcStride, uint8_t* pDst, int32_t iDstStride,
//                                      int32_t iHeight);
WELS_ASM_AARCH64_FUNC_BEGIN McHorVer10WidthEq4_AArch64_neon
    sub x0, x0, #2
    movi v0.8h, #20, lsl #0
    movi v1.8h, #5, lsl #0
    SIGN_EXTENSION x1,w1
    SIGN_EXTENSION x3,w3
    SIGN_EXTENSION x4,w4
    asr x4, x4, #1
w4_xy_10_mc_luma_loop:
    ld1 {v2.16b}, [x0], x1 //only use 9(4+5); 1st row src[-2:6]
    //prfm pldl1strm, [x0]
    ld1 {v3.16b}, [x0], x1 //only use 9(4+5); 2nd row src[-2:6]
    //prfm pldl1strm, [x0]

    zip1 v4.4s, v2.4s, v3.4s  // v4=src[-2] 1st:2nd
    ext v17.16b, v4.16b, v4.16b, #8 // v17=src[2:5] 1st:2nd

    ext v2.16b, v2.16b, v4.16b, #1    //1st row src[-1:6]
    ext v3.16b, v3.16b, v4.16b, #1    //2nd row src[-1:6]
    zip1 v5.4s, v2.4s, v3.4s  // v5=src[-1:2] 1st:2nd
    ext v7.16b, v5.16b, v4.16b, #8    //v7=src[3:6] 1st:2nd

    ext v2.16b, v2.16b, v4.16b, #1    //1st row src[0:6]
    ext v3.16b, v3.16b, v4.16b, #1    //2nd row src[0:6]
    zip1 v6.4s, v2.4s, v3.4s  // v6=src[0:3] 1st:2nd

    ext v2.16b, v2.16b, v4.16b, #1    //1st row src[1:6]
    ext v3.16b, v3.16b, v4.16b, #1    //2nd row src[1:6]
    zip1 v16.4s, v2.4s, v3.4s  // v16=src[1:4] 1st:2nd

    FILTER_6TAG_8BITS1_AVERAGE_WITH_0 v4, v5, v6, v16, v17, v7, v20, v0, v1

    st1 {v20.s}[0], [x2], x3 //write 4Byte
    st1 {v20.s}[1], [x2], x3 //write 4Byte
    sub x4, x4, #1
    cbnz x4, w4_xy_10_mc_luma_loop
WELS_ASM_AARCH64_FUNC_END

//void McHorVer30WidthEq16_AArch64_neon (const uint8_t* pSrc, int32_t iSrcStride, uint8_t* pDst, int32_t iDstStride,
//                                       int32_t iHeight);
WELS_ASM_AARCH64_FUNC_BEGIN McHorVer30WidthEq16_AArch64_neon
    sub x0, x0, #2
    movi v0.8h, #20, lsl #0
    movi v1.8h, #5, lsl #0
    SIGN_EXTENSION x1,w1
    SIGN_EXTENSION x3,w3
    SIGN_EXTENSION x4,w4
w16_xy_30_mc_luma_loop:
    ld1 {v2.8b, v3.8b, v4.8b}, [x0], x1 //only use 21(16+5); v2=src[-2]
    trn1 v2.2d, v2.2d, v3.2d
    //prfm pldl1strm, [x0]
    ext v5.16b, v2.16b, v4.16b, #1    //v5=src[-1]
    ext v6.16b, v2.16b, v4.16b, #2    //v6=src[0]
    ext v7.16b, v2.16b, v4.16b, #3    //v7=src[1]
    ext v16.16b, v2.16b, v4.16b, #4   //v16=src[2]
    ext v17.16b, v2.16b, v4.16b, #5   //v17=src[3]

    FILTER_6TAG_8BITS1_AVERAGE_WITH_1 v2, v5, v6, v7, v16, v17, v20, v0, v1
    FILTER_6TAG_8BITS2_AVERAGE_WITH_1 v2, v5, v6, v7, v16, v17, v20, v0, v1

    sub x4, x4, #1
    st1 {v20.16b}, [x2], x3 //write 16Byte
    cbnz x4, w16_xy_30_mc_luma_loop
WELS_ASM_AARCH64_FUNC_END

//void McHorVer30WidthEq8_AArch64_neon (const uint8_t* pSrc, int32_t iSrcStride, uint8_t* pDst, int32_t iDstStride,
//                                      int32_t iHeight);
WELS_ASM_AARCH64_FUNC_BEGIN McHorVer30WidthEq8_AArch64_neon
    sub x0, x0, #2
    stp d8,d9, [sp,#-16]!
    movi v8.8h, #20, lsl #0
    movi v9.8h, #5, lsl #0
    SIGN_EXTENSION x1,w1
    SIGN_EXTENSION x3,w3
    SIGN_EXTENSION x4,w4
w8_xy_30_mc_luma_loop:
    VEC4_LD1_8BITS_16ELEMENT x0, x1, v16, v20, v24, v28    //load src[-2] in v16,v20,v24,v28 for 4 row; only use 13(8+5);
    sub x4, x4, #4

    //1st row:
    ext v17.16b, v16.16b, v16.16b, #5  //src[3]
    ext v18.16b, v16.16b, v16.16b, #1  //src[-1]
    ext v19.16b, v16.16b, v16.16b, #4  //src[2]
    //2nd row:
    ext v21.16b, v20.16b, v20.16b, #5  //src[3]
    ext v22.16b, v20.16b, v20.16b, #1  //src[-1]
    ext v23.16b, v20.16b, v20.16b, #4  //src[2]
    //3rd row:
    ext v25.16b, v24.16b, v24.16b, #5  //src[3]
    ext v26.16b, v24.16b, v24.16b, #1  //src[-1]
    ext v27.16b, v24.16b, v24.16b, #4  //src[2]
    //4th row:
    ext v29.16b, v28.16b, v28.16b, #5  //src[3]
    ext v30.16b, v28.16b, v28.16b, #1  //src[-1]
    ext v31.16b, v28.16b, v28.16b, #4  //src[2]

    VEC4_UADDL_8BITS v16, v17, v20, v21, v24, v25, v28, v29, v0, v2, v4, v6   //v0/v2/v4/v6=src[-2]+src[3]
    VEC4_UADDL_8BITS v18, v19, v22, v23, v26, v27, v30, v31, v1, v3, v5, v7 //v1/v3/v5/v7=src[-1]+src[2]
    VEC4_MLS_16BITS v1, v9, v3, v9, v5, v9, v7, v9, v0, v2, v4, v6  //v0/v2/v4/v6 -= 5*(src[-1]+src[2])

    //1st row:
    ext v18.16b, v16.16b, v16.16b, #2  //src[0]
    ext v19.16b, v16.16b, v16.16b, #3  //src[1]
    //2nd row:
    ext v22.16b, v20.16b, v20.16b, #2  //src[0]
    ext v23.16b, v20.16b, v20.16b, #3  //src[1]
    //3rd row:
    ext v26.16b, v24.16b, v24.16b, #2  //src[0]
    ext v27.16b, v24.16b, v24.16b, #3  //src[1]
    //4th row:
    ext v30.16b, v28.16b, v28.16b, #2  //src[0]
    ext v31.16b, v28.16b, v28.16b, #3  //src[1]

    VEC4_UADDL_8BITS v18, v19, v22, v23, v26, v27, v30, v31, v1, v3, v5, v7 //v1/v3/v5/v7=src[0]+src[1]
    VEC4_MLA_16BITS v1, v8, v3, v8, v5, v8, v7, v8, v0, v2, v4, v6  //v0/v2/v4/v6+=20*(src[0]+src[1])
    VEC4_SQRSHRUN_16BITS_SHIFT5 v0, v2, v4, v6, v1, v3, v5, v7

    VEC4_UADDL_8BITS v1, v19, v3, v23, v5, v27, v7, v31, v0, v2, v4, v6   //average with arc[0]
    VEC4_RSHRN_16BITS_SHIFT1 v0, v2, v4, v6, v1, v3, v5, v7

    VEC4_ST1_8BITS_8ELEMENT x2, x3, v1, v3, v5, v7
    cbnz x4, w8_xy_30_mc_luma_loop

    ldp d8,d9,[sp],#16
WELS_ASM_AARCH64_FUNC_END

//void McHorVer30WidthEq4_AArch64_neon (const uint8_t* pSrc, int32_t iSrcStride, uint8_t* pDst, int32_t iDstStride,
//                                      int32_t iHeight);
WELS_ASM_AARCH64_FUNC_BEGIN McHorVer30WidthEq4_AArch64_neon
    sub x0, x0, #2
    movi v0.8h, #20, lsl #0
    movi v1.8h, #5, lsl #0
    SIGN_EXTENSION x1,w1
    SIGN_EXTENSION x3,w3
    SIGN_EXTENSION x4,w4
    asr x4, x4, #1
w4_xy_30_mc_luma_loop:
    ld1 {v2.16b}, [x0], x1 //only use 9(4+5); 1st row src[-2:6]
    //prfm pldl1strm, [x0]
    ld1 {v3.16b}, [x0], x1 //only use 9(4+5); 2nd row src[-2:6]
    //prfm pldl1strm, [x0]

    zip1 v4.4s, v2.4s, v3.4s  // v4=src[-2] 1st:2nd
    ext v17.16b, v4.16b, v4.16b, #8 // v17=src[2:5] 1st:2nd

    ext v2.16b, v2.16b, v4.16b, #1    //1st row src[-1:6]
    ext v3.16b, v3.16b, v4.16b, #1    //2nd row src[-1:6]
    zip1 v5.4s, v2.4s, v3.4s  // v5=src[-1:2] 1st:2nd
    ext v7.16b, v5.16b, v4.16b, #8    //v7=src[3:6] 1st:2nd

    ext v2.16b, v2.16b, v4.16b, #1    //1st row src[0:6]
    ext v3.16b, v3.16b, v4.16b, #1    //2nd row src[0:6]
    zip1 v6.4s, v2.4s, v3.4s  // v6=src[0:3] 1st:2nd

    ext v2.16b, v2.16b, v4.16b, #1    //1st row src[1:6]
    ext v3.16b, v3.16b, v4.16b, #1    //2nd row src[1:6]
    zip1 v16.4s, v2.4s, v3.4s  // v16=src[1:4] 1st:2nd

    FILTER_6TAG_8BITS1_AVERAGE_WITH_1 v4, v5, v6, v16, v17, v7, v20, v0, v1

    st1 {v20.s}[0], [x2], x3 //write 4Byte
    st1 {v20.s}[1], [x2], x3 //write 4Byte
    sub x4, x4, #1
    cbnz x4, w4_xy_30_mc_luma_loop
WELS_ASM_AARCH64_FUNC_END

//void McHorVer01WidthEq16_AArch64_neon (const uint8_t* pSrc, int32_t iSrcStride, uint8_t* pDst, int32_t iDstStride,
//                                       int32_t iHeight);
WELS_ASM_AARCH64_FUNC_BEGIN McHorVer01WidthEq16_AArch64_neon
    SIGN_EXTENSION x1,w1
    SIGN_EXTENSION x3,w3
    SIGN_EXTENSION x4,w4
    sub x0, x0, x1, lsl #1
    movi v0.8h, #20, lsl #0
    movi v1.8h, #5, lsl #0

    //prfm pldl1strm, [x0]
    //prfm pldl1strm, [x0, x1]
    ld1 {v2.16b}, [x0], x1 // v2=src[-2*stride]
    //prfm pldl1strm, [x0, x1]
    ld1 {v3.16b}, [x0], x1 // v3=src[-1*stride]
    //prfm pldl1strm, [x0, x1]
    ld1 {v4.16b}, [x0], x1 // v4=src[0*stride]
    //prfm pldl1strm, [x0, x1]
    ld1 {v5.16b}, [x0], x1 // v5=src[1*stride]
    //prfm pldl1strm, [x0, x1]
    ld1 {v6.16b}, [x0], x1 // v6=src[2*stride]


w16_xy_01_mc_luma_loop:
    //prfm pldl1strm, [x0, x1]
    ld1 {v7.16b}, [x0], x1 // v7=src[3*stride]
    FILTER_6TAG_8BITS1_AVERAGE_WITH_0 v2, v3, v4, v5, v6, v7, v20, v0, v1
    FILTER_6TAG_8BITS2_AVERAGE_WITH_0 v2, v3, v4, v5, v6, v7, v20, v0, v1
    st1 {v20.16b}, [x2], x3 //write 16Byte : 0 line


    //prfm pldl1strm, [x0, x1]
    ld1 {v2.16b}, [x0], x1 // v2=src[3*stride]
    FILTER_6TAG_8BITS1_AVERAGE_WITH_0 v3, v4, v5, v6, v7, v2, v20, v0, v1
    FILTER_6TAG_8BITS2_AVERAGE_WITH_0 v3, v4, v5, v6, v7, v2, v20, v0, v1
    st1 {v20.16b}, [x2], x3 //write 16Byte : 1 line


    //prfm pldl1strm, [x0, x1]
    ld1 {v3.16b}, [x0], x1 // v3=src[3*stride]
    FILTER_6TAG_8BITS1_AVERAGE_WITH_0 v4, v5, v6, v7, v2, v3, v20, v0, v1
    FILTER_6TAG_8BITS2_AVERAGE_WITH_0 v4, v5, v6, v7, v2, v3, v20, v0, v1
    st1 {v20.16b}, [x2], x3 //write 16Byte : 2 line


    //prfm pldl1strm, [x0, x1]
    ld1 {v4.16b}, [x0], x1 // v4=src[3*stride]
    FILTER_6TAG_8BITS1_AVERAGE_WITH_0 v5, v6, v7, v2, v3, v4, v20, v0, v1
    FILTER_6TAG_8BITS2_AVERAGE_WITH_0 v5, v6, v7, v2, v3, v4, v20, v0, v1
    st1 {v20.16b}, [x2], x3 //write 16Byte : 3 line


    //prfm pldl1strm, [x0, x1]
    ld1 {v5.16b}, [x0], x1 // v5=src[3*stride]
    FILTER_6TAG_8BITS1_AVERAGE_WITH_0 v6, v7, v2, v3, v4, v5, v20, v0, v1
    FILTER_6TAG_8BITS2_AVERAGE_WITH_0 v6, v7, v2, v3, v4, v5, v20, v0, v1
    st1 {v20.16b}, [x2], x3 //write 16Byte : 4 line


    //prfm pldl1strm, [x0, x1]
    ld1 {v6.16b}, [x0], x1 // v6=src[3*stride]
    FILTER_6TAG_8BITS1_AVERAGE_WITH_0 v7, v2, v3, v4, v5, v6, v20, v0, v1
    FILTER_6TAG_8BITS2_AVERAGE_WITH_0 v7, v2, v3, v4, v5, v6, v20, v0, v1
    st1 {v20.16b}, [x2], x3 //write 16Byte : 5 line

    //prfm pldl1strm, [x0, x1]
    ld1 {v7.16b}, [x0], x1 // v7=src[3*stride]
    FILTER_6TAG_8BITS1_AVERAGE_WITH_0 v2, v3, v4, v5, v6, v7, v20, v0, v1
    FILTER_6TAG_8BITS2_AVERAGE_WITH_0 v2, v3, v4, v5, v6, v7, v20, v0, v1
    st1 {v20.16b}, [x2], x3 //write 16Byte : 6 line

    //prfm pldl1strm, [x0, x1]
    ld1 {v2.16b}, [x0], x1 // v2=src[3*stride]
    FILTER_6TAG_8BITS1_AVERAGE_WITH_0 v3, v4, v5, v6, v7, v2, v20, v0, v1
    FILTER_6TAG_8BITS2_AVERAGE_WITH_0 v3, v4, v5, v6, v7, v2, v20, v0, v1
    st1 {v20.16b}, [x2], x3 //write 16Byte : 7 line

    mov v3.16b, v5.16b
    mov v5.16b, v7.16b
    mov v7.16b, v2.16b
    mov v2.16b, v4.16b
    mov v4.16b, v6.16b
    mov v6.16b, v7.16b
    sub x4, x4, #8
    cbnz x4, w16_xy_01_mc_luma_loop
WELS_ASM_AARCH64_FUNC_END

//void McHorVer01WidthEq8_AArch64_neon (const uint8_t* pSrc, int32_t iSrcStride, uint8_t* pDst, int32_t iDstStride,
//                                      int32_t iHeight);
WELS_ASM_AARCH64_FUNC_BEGIN McHorVer01WidthEq8_AArch64_neon
    SIGN_EXTENSION x1,w1
    SIGN_EXTENSION x3,w3
    SIGN_EXTENSION x4,w4
    sub x0, x0, x1, lsl #1
    movi v30.8h, #20, lsl #0
    movi v31.8h, #5, lsl #0

    ld1 {v16.8b}, [x0], x1 // v16=src[-2*stride]
    ld1 {v17.8b}, [x0], x1 // v17=src[-1*stride]
    ld1 {v18.8b}, [x0], x1 // v18=src[0*stride]
    ld1 {v19.8b}, [x0], x1 // v19=src[1*stride]
    ld1 {v20.8b}, [x0], x1 // v20=src[2*stride]

w8_xy_01_mc_luma_loop:
    ld1 {v21.8b}, [x0], x1 // v21=src[3*stride]
    ld1 {v22.8b}, [x0], x1 // v22=src[4*stride]
    ld1 {v23.8b}, [x0], x1 // v23=src[5*stride]
    ld1 {v24.8b}, [x0], x1 // v24=src[6*stride]

    VEC4_UADDL_8BITS v16, v21, v17, v22, v18, v23, v19, v24, v0, v2, v4, v6 //v0/v2/v4/v6 =src[-2]+src[3]
    VEC4_UADDL_8BITS v17, v20, v18, v21, v19, v22, v20, v23, v1, v3, v5, v7 //v1/v3/v5/v7 =src[-1]+src[2]
    VEC4_MLS_16BITS v1, v31, v3, v31, v5, v31, v7, v31, v0, v2, v4, v6  //v0/v2/v4/v6 -=5*(src[-1]+src[2])
    VEC4_UADDL_8BITS v18, v19, v19, v20, v20, v21, v21, v22, v1, v3, v5, v7 //v1/v3/v5/v7 =src[0]+src[1]
    VEC4_MLA_16BITS v1, v30, v3, v30, v5, v30, v7, v30, v0, v2, v4, v6  //v0/v2/v4/v6 += 20*(src[0]+src[1])
    VEC4_SQRSHRUN_16BITS_SHIFT5 v0, v2, v4, v6, v1, v3, v5, v7

    VEC4_UADDL_8BITS v1, v18, v3, v19, v5, v20, v7, v21, v0, v2, v4, v6 //v0/v2/v4/v6 = average with src[0]
    VEC4_RSHRN_16BITS_SHIFT1 v0, v2, v4, v6, v1, v3, v5, v7

    VEC4_ST1_8BITS_8ELEMENT x2, x3, v1, v3, v5, v7  //store 8bytes*4row

    sub x4, x4, #4
    mov v16.16b, v20.16b
    mov v17.16b, v21.16b
    mov v18.16b, v22.16b
    mov v19.16b, v23.16b
    mov v20.16b, v24.16b

    cbnz x4, w8_xy_01_mc_luma_loop
WELS_ASM_AARCH64_FUNC_END

//void McHorVer01WidthEq4_AArch64_neon (const uint8_t* pSrc, int32_t iSrcStride, uint8_t* pDst, int32_t iDstStride,
//                                      int32_t iHeight);
WELS_ASM_AARCH64_FUNC_BEGIN McHorVer01WidthEq4_AArch64_neon
    SIGN_EXTENSION x1,w1
    SIGN_EXTENSION x3,w3
    SIGN_EXTENSION x4,w4
    sub x0, x0, x1, lsl #1
    movi v0.8h, #20, lsl #0
    movi v1.8h, #5, lsl #0

    //prfm pldl1strm, [x0]
    //prfm pldl1strm, [x0, x1]
    ld1 {v2.s}[0], [x0], x1 // v2=src[-2*stride]
    //prfm pldl1strm, [x0, x1]
    ld1 {v3.s}[0], [x0], x1 // v3=src[-1*stride]
    mov v2.s[1], v3.s[0]
    //prfm pldl1strm, [x0, x1]
    ld1 {v4.s}[0], [x0], x1 // v4=src[0*stride]
    mov v3.s[1], v4.s[0]
    //prfm pldl1strm, [x0, x1]
    ld1 {v5.s}[0], [x0], x1 // v5=src[1*stride]
    mov v4.s[1], v5.s[0]
    //prfm pldl1strm, [x0, x1]
    ld1 {v6.s}[0], [x0], x1 // v6=src[2*stride]
    mov v5.s[1], v6.s[0]

w4_xy_01_mc_luma_loop:
    //prfm pldl1strm, [x0, x1]
    ld1 {v7.s}[0], [x0], x1 // v7=src[3*stride]
    mov v6.s[1], v7.s[0]
    //prfm pldl1strm, [x0, x1]
    ld1 {v7.s}[1], [x0], x1 // v7=src[4*stride]
    FILTER_6TAG_8BITS1_AVERAGE_WITH_0 v2, v3, v4, v5, v6, v7, v20, v0, v1
    st1 {v20.s}[0], [x2], x3 //write 4Byte : 0 line
    st1 {v20.s}[1], [x2], x3 //write 4Byte : 1 line
    mov v2.s[0], v7.s[1]

    //prfm pldl1strm, [x0, x1]
    ld1 {v2.s}[1], [x0], x1 // v2=src[5*stride]
    //prfm pldl1strm, [x0, x1]
    ld1 {v3.s}[1], [x0], x1 // v2=src[6*stride]
    mov v3.s[0], v2.s[1]
    FILTER_6TAG_8BITS1_AVERAGE_WITH_0 v4, v5, v6, v7, v2, v3, v20, v0, v1
    st1 {v20.s}[0], [x2], x3 //write 4Byte : 2 line
    st1 {v20.s}[1], [x2], x3 //write 4Byte : 3 line
    mov v4.s[0], v3.s[1]

    mov v21.8b, v6.8b
    mov v6.8b, v4.8b
    mov v4.8b, v2.8b
    mov v2.8b, v21.8b
    mov v21.8b, v3.8b
    mov v3.8b, v7.8b
    mov v7.8b, v5.8b
    mov v5.8b, v21.8b

    sub x4, x4, #4
    cbnz x4, w4_xy_01_mc_luma_loop
WELS_ASM_AARCH64_FUNC_END

//void McHorVer03WidthEq16_AArch64_neon (const uint8_t* pSrc, int32_t iSrcStride, uint8_t* pDst, int32_t iDstStride,
//                                       int32_t iHeight);
WELS_ASM_AARCH64_FUNC_BEGIN McHorVer03WidthEq16_AArch64_neon
    SIGN_EXTENSION x1,w1
    SIGN_EXTENSION x3,w3
    SIGN_EXTENSION x4,w4
    sub x0, x0, x1, lsl #1
    movi v0.8h, #20, lsl #0
    movi v1.8h, #5, lsl #0

    //prfm pldl1strm, [x0]
    //prfm pldl1strm, [x0, x1]
    ld1 {v2.16b}, [x0], x1 // v2=src[-2*stride]
    //prfm pldl1strm, [x0, x1]
    ld1 {v3.16b}, [x0], x1 // v3=src[-1*stride]
    //prfm pldl1strm, [x0, x1]
    ld1 {v4.16b}, [x0], x1 // v4=src[0*stride]
    //prfm pldl1strm, [x0, x1]
    ld1 {v5.16b}, [x0], x1 // v5=src[1*stride]
    //prfm pldl1strm, [x0, x1]
    ld1 {v6.16b}, [x0], x1 // v6=src[2*stride]


w16_xy_03_mc_luma_loop:
    //prfm pldl1strm, [x0, x1]
    ld1 {v7.16b}, [x0], x1 // v7=src[3*stride]
    FILTER_6TAG_8BITS1_AVERAGE_WITH_1 v2, v3, v4, v5, v6, v7, v20, v0, v1
    FILTER_6TAG_8BITS2_AVERAGE_WITH_1 v2, v3, v4, v5, v6, v7, v20, v0, v1
    st1 {v20.16b}, [x2], x3 //write 16Byte : 0 line


    //prfm pldl1strm, [x0, x1]
    ld1 {v2.16b}, [x0], x1 // v2=src[3*stride]
    FILTER_6TAG_8BITS1_AVERAGE_WITH_1 v3, v4, v5, v6, v7, v2, v20, v0, v1
    FILTER_6TAG_8BITS2_AVERAGE_WITH_1 v3, v4, v5, v6, v7, v2, v20, v0, v1
    st1 {v20.16b}, [x2], x3 //write 16Byte : 1 line


    //prfm pldl1strm, [x0, x1]
    ld1 {v3.16b}, [x0], x1 // v3=src[3*stride]
    FILTER_6TAG_8BITS1_AVERAGE_WITH_1 v4, v5, v6, v7, v2, v3, v20, v0, v1
    FILTER_6TAG_8BITS2_AVERAGE_WITH_1 v4, v5, v6, v7, v2, v3, v20, v0, v1
    st1 {v20.16b}, [x2], x3 //write 16Byte : 2 line


    //prfm pldl1strm, [x0, x1]
    ld1 {v4.16b}, [x0], x1 // v4=src[3*stride]
    FILTER_6TAG_8BITS1_AVERAGE_WITH_1 v5, v6, v7, v2, v3, v4, v20, v0, v1
    FILTER_6TAG_8BITS2_AVERAGE_WITH_1 v5, v6, v7, v2, v3, v4, v20, v0, v1
    st1 {v20.16b}, [x2], x3 //write 16Byte : 3 line


    //prfm pldl1strm, [x0, x1]
    ld1 {v5.16b}, [x0], x1 // v5=src[3*stride]
    FILTER_6TAG_8BITS1_AVERAGE_WITH_1 v6, v7, v2, v3, v4, v5, v20, v0, v1
    FILTER_6TAG_8BITS2_AVERAGE_WITH_1 v6, v7, v2, v3, v4, v5, v20, v0, v1
    st1 {v20.16b}, [x2], x3 //write 16Byte : 4 line


    //prfm pldl1strm, [x0, x1]
    ld1 {v6.16b}, [x0], x1 // v6=src[3*stride]
    FILTER_6TAG_8BITS1_AVERAGE_WITH_1 v7, v2, v3, v4, v5, v6, v20, v0, v1
    FILTER_6TAG_8BITS2_AVERAGE_WITH_1 v7, v2, v3, v4, v5, v6, v20, v0, v1
    st1 {v20.16b}, [x2], x3 //write 16Byte : 5 line

    //prfm pldl1strm, [x0, x1]
    ld1 {v7.16b}, [x0], x1 // v7=src[3*stride]
    FILTER_6TAG_8BITS1_AVERAGE_WITH_1 v2, v3, v4, v5, v6, v7, v20, v0, v1
    FILTER_6TAG_8BITS2_AVERAGE_WITH_1 v2, v3, v4, v5, v6, v7, v20, v0, v1
    st1 {v20.16b}, [x2], x3 //write 16Byte : 6 line

    //prfm pldl1strm, [x0, x1]
    ld1 {v2.16b}, [x0], x1 // v2=src[3*stride]
    FILTER_6TAG_8BITS1_AVERAGE_WITH_1 v3, v4, v5, v6, v7, v2, v20, v0, v1
    FILTER_6TAG_8BITS2_AVERAGE_WITH_1 v3, v4, v5, v6, v7, v2, v20, v0, v1
    st1 {v20.16b}, [x2], x3 //write 16Byte : 7 line

    mov v3.16b, v5.16b
    mov v5.16b, v7.16b
    mov v7.16b, v2.16b
    mov v2.16b, v4.16b
    mov v4.16b, v6.16b
    mov v6.16b, v7.16b
    sub x4, x4, #8
    cbnz x4, w16_xy_03_mc_luma_loop
WELS_ASM_AARCH64_FUNC_END

//void McHorVer03WidthEq8_AArch64_neon (const uint8_t* pSrc, int32_t iSrcStride, uint8_t* pDst, int32_t iDstStride,
//                                      int32_t iHeight);
WELS_ASM_AARCH64_FUNC_BEGIN McHorVer03WidthEq8_AArch64_neon
    SIGN_EXTENSION x1,w1
    SIGN_EXTENSION x3,w3
    SIGN_EXTENSION x4,w4
    sub x0, x0, x1, lsl #1
    movi v30.8h, #20, lsl #0
    movi v31.8h, #5, lsl #0

    ld1 {v16.8b}, [x0], x1 // v16=src[-2*stride]
    ld1 {v17.8b}, [x0], x1 // v17=src[-1*stride]
    ld1 {v18.8b}, [x0], x1 // v18=src[0*stride]
    ld1 {v19.8b}, [x0], x1 // v19=src[1*stride]
    ld1 {v20.8b}, [x0], x1 // v20=src[2*stride]

w8_xy_03_mc_luma_loop:
    ld1 {v21.8b}, [x0], x1 // v21=src[3*stride]
    ld1 {v22.8b}, [x0], x1 // v22=src[4*stride]
    ld1 {v23.8b}, [x0], x1 // v23=src[5*stride]
    ld1 {v24.8b}, [x0], x1 // v24=src[6*stride]

    VEC4_UADDL_8BITS v16, v21, v17, v22, v18, v23, v19, v24, v0, v2, v4, v6 //v0/v2/v4/v6 =src[-2]+src[3]
    VEC4_UADDL_8BITS v17, v20, v18, v21, v19, v22, v20, v23, v1, v3, v5, v7 //v1/v3/v5/v7 =src[-1]+src[2]
    VEC4_MLS_16BITS v1, v31, v3, v31, v5, v31, v7, v31, v0, v2, v4, v6  //v0/v2/v4/v6 -=5*(src[-1]+src[2])
    VEC4_UADDL_8BITS v18, v19, v19, v20, v20, v21, v21, v22, v1, v3, v5, v7 //v1/v3/v5/v7 =src[0]+src[1]
    VEC4_MLA_16BITS v1, v30, v3, v30, v5, v30, v7, v30, v0, v2, v4, v6  //v0/v2/v4/v6 += 20*(src[0]+src[1])
    VEC4_SQRSHRUN_16BITS_SHIFT5 v0, v2, v4, v6, v1, v3, v5, v7

    VEC4_UADDL_8BITS v1, v19, v3, v20, v5, v21, v7, v22, v0, v2, v4, v6 //v0/v2/v4/v6 = average with src[0]
    VEC4_RSHRN_16BITS_SHIFT1 v0, v2, v4, v6, v1, v3, v5, v7

    VEC4_ST1_8BITS_8ELEMENT x2, x3, v1, v3, v5, v7  //store 8bytes*4row

    sub x4, x4, #4
    mov v16.16b, v20.16b
    mov v17.16b, v21.16b
    mov v18.16b, v22.16b
    mov v19.16b, v23.16b
    mov v20.16b, v24.16b

    cbnz x4, w8_xy_03_mc_luma_loop
WELS_ASM_AARCH64_FUNC_END

//void McHorVer03WidthEq4_AArch64_neon (const uint8_t* pSrc, int32_t iSrcStride, uint8_t* pDst, int32_t iDstStride,
//                                      int32_t iHeight);
WELS_ASM_AARCH64_FUNC_BEGIN McHorVer03WidthEq4_AArch64_neon
    SIGN_EXTENSION x1,w1
    SIGN_EXTENSION x3,w3
    SIGN_EXTENSION x4,w4
    sub x0, x0, x1, lsl #1
    movi v0.8h, #20, lsl #0
    movi v1.8h, #5, lsl #0

    //prfm pldl1strm, [x0]
    //prfm pldl1strm, [x0, x1]
    ld1 {v2.s}[0], [x0], x1 // v2=src[-2*stride]
    //prfm pldl1strm, [x0, x1]
    ld1 {v3.s}[0], [x0], x1 // v3=src[-1*stride]
    mov v2.s[1], v3.s[0]
    //prfm pldl1strm, [x0, x1]
    ld1 {v4.s}[0], [x0], x1 // v4=src[0*stride]
    mov v3.s[1], v4.s[0]
    //prfm pldl1strm, [x0, x1]
    ld1 {v5.s}[0], [x0], x1 // v5=src[1*stride]
    mov v4.s[1], v5.s[0]
    //prfm pldl1strm, [x0, x1]
    ld1 {v6.s}[0], [x0], x1 // v6=src[2*stride]
    mov v5.s[1], v6.s[0]

w4_xy_03_mc_luma_loop:
    //prfm pldl1strm, [x0, x1]
    ld1 {v7.s}[0], [x0], x1 // v7=src[3*stride]
    mov v6.s[1], v7.s[0]
    //prfm pldl1strm, [x0, x1]
    ld1 {v7.s}[1], [x0], x1 // v7=src[4*stride]
    FILTER_6TAG_8BITS1_AVERAGE_WITH_1 v2, v3, v4, v5, v6, v7, v20, v0, v1
    st1 {v20.s}[0], [x2], x3 //write 4Byte : 0 line
    st1 {v20.s}[1], [x2], x3 //write 4Byte : 1 line
    mov v2.s[0], v7.s[1]

    //prfm pldl1strm, [x0, x1]
    ld1 {v2.s}[1], [x0], x1 // v2=src[5*stride]
    //prfm pldl1strm, [x0, x1]
    ld1 {v3.s}[1], [x0], x1 // v2=src[6*stride]
    mov v3.s[0], v2.s[1]
    FILTER_6TAG_8BITS1_AVERAGE_WITH_1 v4, v5, v6, v7, v2, v3, v20, v0, v1
    st1 {v20.s}[0], [x2], x3 //write 4Byte : 2 line
    st1 {v20.s}[1], [x2], x3 //write 4Byte : 3 line
    mov v4.s[0], v3.s[1]

    mov v21.8b, v6.8b
    mov v6.8b, v4.8b
    mov v4.8b, v2.8b
    mov v2.8b, v21.8b
    mov v21.8b, v3.8b
    mov v3.8b, v7.8b
    mov v7.8b, v5.8b
    mov v5.8b, v21.8b

    sub x4, x4, #4
    cbnz x4, w4_xy_03_mc_luma_loop
WELS_ASM_AARCH64_FUNC_END

//void McHorVer02WidthEq16_AArch64_neon (const uint8_t* pSrc, int32_t iSrcStride, uint8_t* pDst, int32_t iDstStride,
//                                       int32_t iHeight);
WELS_ASM_AARCH64_FUNC_BEGIN McHorVer02WidthEq16_AArch64_neon
    SIGN_EXTENSION x1,w1
    SIGN_EXTENSION x3,w3
    SIGN_EXTENSION x4,w4
    sub x0, x0, x1, lsl #1
    movi v0.8h, #20, lsl #0
    movi v1.8h, #5, lsl #0

    //prfm pldl1strm, [x0]
    //prfm pldl1strm, [x0, x1]
    ld1 {v2.16b}, [x0], x1 // v2=src[-2*stride]
    //prfm pldl1strm, [x0, x1]
    ld1 {v3.16b}, [x0], x1 // v3=src[-1*stride]
    //prfm pldl1strm, [x0, x1]
    ld1 {v4.16b}, [x0], x1 // v4=src[0*stride]
    //prfm pldl1strm, [x0, x1]
    ld1 {v5.16b}, [x0], x1 // v5=src[1*stride]
    //prfm pldl1strm, [x0, x1]
    ld1 {v6.16b}, [x0], x1 // v6=src[2*stride]


w16_xy_02_mc_luma_loop:
    //prfm pldl1strm, [x0, x1]
    ld1 {v7.16b}, [x0], x1 // v7=src[3*stride]
    FILTER_6TAG_8BITS1 v2, v3, v4, v5, v6, v7, v20, v0, v1
    FILTER_6TAG_8BITS2 v2, v3, v4, v5, v6, v7, v20, v0, v1
    st1 {v20.16b}, [x2], x3 //write 16Byte : 0 line


    //prfm pldl1strm, [x0, x1]
    ld1 {v2.16b}, [x0], x1 // v2=src[3*stride]
    FILTER_6TAG_8BITS1 v3, v4, v5, v6, v7, v2, v20, v0, v1
    FILTER_6TAG_8BITS2 v3, v4, v5, v6, v7, v2, v20, v0, v1
    st1 {v20.16b}, [x2], x3 //write 16Byte : 1 line


    //prfm pldl1strm, [x0, x1]
    ld1 {v3.16b}, [x0], x1 // v3=src[3*stride]
    FILTER_6TAG_8BITS1 v4, v5, v6, v7, v2, v3, v20, v0, v1
    FILTER_6TAG_8BITS2 v4, v5, v6, v7, v2, v3, v20, v0, v1
    st1 {v20.16b}, [x2], x3 //write 16Byte : 2 line


    //prfm pldl1strm, [x0, x1]
    ld1 {v4.16b}, [x0], x1 // v4=src[3*stride]
    FILTER_6TAG_8BITS1 v5, v6, v7, v2, v3, v4, v20, v0, v1
    FILTER_6TAG_8BITS2 v5, v6, v7, v2, v3, v4, v20, v0, v1
    st1 {v20.16b}, [x2], x3 //write 16Byte : 3 line


    //prfm pldl1strm, [x0, x1]
    ld1 {v5.16b}, [x0], x1 // v5=src[3*stride]
    FILTER_6TAG_8BITS1 v6, v7, v2, v3, v4, v5, v20, v0, v1
    FILTER_6TAG_8BITS2 v6, v7, v2, v3, v4, v5, v20, v0, v1
    st1 {v20.16b}, [x2], x3 //write 16Byte : 4 line


    //prfm pldl1strm, [x0, x1]
    ld1 {v6.16b}, [x0], x1 // v6=src[3*stride]
    FILTER_6TAG_8BITS1 v7, v2, v3, v4, v5, v6, v20, v0, v1
    FILTER_6TAG_8BITS2 v7, v2, v3, v4, v5, v6, v20, v0, v1
    st1 {v20.16b}, [x2], x3 //write 16Byte : 5 line

    //prfm pldl1strm, [x0, x1]
    ld1 {v7.16b}, [x0], x1 // v7=src[3*stride]
    FILTER_6TAG_8BITS1 v2, v3, v4, v5, v6, v7, v20, v0, v1
    FILTER_6TAG_8BITS2 v2, v3, v4, v5, v6, v7, v20, v0, v1
    st1 {v20.16b}, [x2], x3 //write 16Byte : 6 line

    //prfm pldl1strm, [x0, x1]
    ld1 {v2.16b}, [x0], x1 // v2=src[3*stride]
    FILTER_6TAG_8BITS1 v3, v4, v5, v6, v7, v2, v20, v0, v1
    FILTER_6TAG_8BITS2 v3, v4, v5, v6, v7, v2, v20, v0, v1
    st1 {v20.16b}, [x2], x3 //write 16Byte : 7 line

    mov v3.16b, v5.16b
    mov v5.16b, v7.16b
    mov v7.16b, v2.16b
    mov v2.16b, v4.16b
    mov v4.16b, v6.16b
    mov v6.16b, v7.16b
    sub x4, x4, #8
    cbnz x4, w16_xy_02_mc_luma_loop
WELS_ASM_AARCH64_FUNC_END

//void McHorVer02WidthEq8_AArch64_neon (const uint8_t* pSrc, int32_t iSrcStride, uint8_t* pDst, int32_t iDstStride,
//                                      int32_t iHeight);
WELS_ASM_AARCH64_FUNC_BEGIN McHorVer02WidthEq8_AArch64_neon
    SIGN_EXTENSION x1,w1
    SIGN_EXTENSION x3,w3
    SIGN_EXTENSION x4,w4
    sub x0, x0, x1, lsl #1
    movi v30.8h, #20, lsl #0
    movi v31.8h, #5, lsl #0

    ld1 {v16.8b}, [x0], x1 // v16=src[-2*stride]
    ld1 {v17.8b}, [x0], x1 // v17=src[-1*stride]
    ld1 {v18.8b}, [x0], x1 // v18=src[0*stride]
    ld1 {v19.8b}, [x0], x1 // v19=src[1*stride]
    ld1 {v20.8b}, [x0], x1 // v20=src[2*stride]

w8_xy_02_mc_luma_loop:
    ld1 {v21.8b}, [x0], x1 // v21=src[3*stride]
    ld1 {v22.8b}, [x0], x1 // v22=src[4*stride]
    ld1 {v23.8b}, [x0], x1 // v23=src[5*stride]
    ld1 {v24.8b}, [x0], x1 // v24=src[6*stride]

    VEC4_UADDL_8BITS v16, v21, v17, v22, v18, v23, v19, v24, v0, v2, v4, v6 //v0/v2/v4/v6 =src[-2]+src[3]
    VEC4_UADDL_8BITS v17, v20, v18, v21, v19, v22, v20, v23, v1, v3, v5, v7 //v1/v3/v5/v7 =src[-1]+src[2]
    VEC4_MLS_16BITS v1, v31, v3, v31, v5, v31, v7, v31, v0, v2, v4, v6  //v0/v2/v4/v6 -=5*(src[-1]+src[2])
    VEC4_UADDL_8BITS v18, v19, v19, v20, v20, v21, v21, v22, v1, v3, v5, v7 //v1/v3/v5/v7 =src[0]+src[1]
    VEC4_MLA_16BITS v1, v30, v3, v30, v5, v30, v7, v30, v0, v2, v4, v6  //v0/v2/v4/v6 += 20*(src[0]+src[1])
    VEC4_SQRSHRUN_16BITS_SHIFT5 v0, v2, v4, v6, v1, v3, v5, v7
    VEC4_ST1_8BITS_8ELEMENT x2, x3, v1, v3, v5, v7  //store 8bytes*4row

    sub x4, x4, #4
    mov v16.16b, v20.16b
    mov v17.16b, v21.16b
    mov v18.16b, v22.16b
    mov v19.16b, v23.16b
    mov v20.16b, v24.16b

    cbnz x4, w8_xy_02_mc_luma_loop
WELS_ASM_AARCH64_FUNC_END

//void McHorVer02WidthEq4_AArch64_neon (const uint8_t* pSrc, int32_t iSrcStride, uint8_t* pDst, int32_t iDstStride,
//                                      int32_t iHeight);
WELS_ASM_AARCH64_FUNC_BEGIN McHorVer02WidthEq4_AArch64_neon
    SIGN_EXTENSION x1,w1
    SIGN_EXTENSION x3,w3
    SIGN_EXTENSION x4,w4
    sub x0, x0, x1, lsl #1
    movi v0.8h, #20, lsl #0
    movi v1.8h, #5, lsl #0

    //prfm pldl1strm, [x0]
    //prfm pldl1strm, [x0, x1]
    ld1 {v2.s}[0], [x0], x1 // v2=src[-2*stride]
    //prfm pldl1strm, [x0, x1]
    ld1 {v3.s}[0], [x0], x1 // v3=src[-1*stride]
    mov v2.s[1], v3.s[0]
    //prfm pldl1strm, [x0, x1]
    ld1 {v4.s}[0], [x0], x1 // v4=src[0*stride]
    mov v3.s[1], v4.s[0]
    //prfm pldl1strm, [x0, x1]
    ld1 {v5.s}[0], [x0], x1 // v5=src[1*stride]
    mov v4.s[1], v5.s[0]
    //prfm pldl1strm, [x0, x1]
    ld1 {v6.s}[0], [x0], x1 // v6=src[2*stride]
    mov v5.s[1], v6.s[0]

w4_xy_02_mc_luma_loop:
    //prfm pldl1strm, [x0, x1]
    ld1 {v7.s}[0], [x0], x1 // v7=src[3*stride]
    mov v6.s[1], v7.s[0]
    //prfm pldl1strm, [x0, x1]
    ld1 {v7.s}[1], [x0], x1 // v7=src[4*stride]
    FILTER_6TAG_8BITS1 v2, v3, v4, v5, v6, v7, v20, v0, v1
    st1 {v20.s}[0], [x2], x3 //write 4Byte : 0 line
    st1 {v20.s}[1], [x2], x3 //write 4Byte : 1 line
    mov v2.s[0], v7.s[1]

    //prfm pldl1strm, [x0, x1]
    ld1 {v2.s}[1], [x0], x1 // v2=src[5*stride]
    //prfm pldl1strm, [x0, x1]
    ld1 {v3.s}[1], [x0], x1 // v2=src[6*stride]
    mov v3.s[0], v2.s[1]
    FILTER_6TAG_8BITS1 v4, v5, v6, v7, v2, v3, v20, v0, v1
    st1 {v20.s}[0], [x2], x3 //write 4Byte : 2 line
    st1 {v20.s}[1], [x2], x3 //write 4Byte : 3 line
    mov v4.s[0], v3.s[1]

    mov v21.8b, v6.8b
    mov v6.8b, v4.8b
    mov v4.8b, v2.8b
    mov v2.8b, v21.8b
    mov v21.8b, v3.8b
    mov v3.8b, v7.8b
    mov v7.8b, v5.8b
    mov v5.8b, v21.8b

    sub x4, x4, #4
    cbnz x4, w4_xy_02_mc_luma_loop
WELS_ASM_AARCH64_FUNC_END

//void McHorVer22WidthEq16_AArch64_neon (const uint8_t* pSrc, int32_t iSrcStride, uint8_t* pDst, int32_t iDstStride,
//                                       int32_t iHeight);
WELS_ASM_AARCH64_FUNC_BEGIN McHorVer22WidthEq16_AArch64_neon
    SIGN_EXTENSION x1,w1
    SIGN_EXTENSION x3,w3
    SIGN_EXTENSION x4,w4
    stp d8, d9, [sp,#-16]!
    stp d10, d11, [sp,#-16]!
    stp d12, d13, [sp,#-16]!
    stp d14, d15, [sp,#-16]!
    sub x0, x0, #2
    sub x0, x0, x1, lsl #1
    movi v0.8h, #20, lsl #0
    movi v1.8h, #5, lsl #0

    //prfm pldl1strm, [x0]
    //prfm pldl1strm, [x0, x1]
    ld1 {v2.8b, v3.8b, v4.8b}, [x0], x1 // v2=src[-2*stride]
    //prfm pldl1strm, [x0, x1]
    ld1 {v5.8b, v6.8b, v7.8b}, [x0], x1 // v5=src[-1*stride]
    //prfm pldl1strm, [x0, x1]
    ld1 {v8.8b, v9.8b, v10.8b}, [x0], x1 // v8=src[0*stride]
    //prfm pldl1strm, [x0, x1]
    ld1 {v11.8b, v12.8b, v13.8b}, [x0], x1 // v11=src[1*stride]
    //prfm pldl1strm, [x0, x1]
    ld1 {v14.8b, v15.8b, v16.8b}, [x0], x1 // v14=src[2*stride]

w16_hv_mc_luma_loop:
    //prfm pldl1strm, [x0, x1]
    ld1 {v17.8b, v18.8b, v19.8b}, [x0], x1 // v17=src[3*stride]
    // vertical filtered into v20/v21
    FILTER_6TAG_8BITS_TO_16BITS1 v2, v5, v8, v11, v14, v17, v20, v0, v1
    FILTER_6TAG_8BITS_TO_16BITS1 v3, v6, v9, v12, v15, v18, v21, v0, v1
    // horizon filtered
    UNPACK_2_16BITS_TO_ABC  v20, v21, v23, v24, v25
    FILTER_3_IN_16BITS_TO_8BITS1 v23, v24, v25, v26 //output to v26[0]
    // vertical filtered into v21/v22
    FILTER_6TAG_8BITS_TO_16BITS1 v4, v7, v10, v13, v16, v19, v22, v0, v1
    UNPACK_2_16BITS_TO_ABC  v21, v22, v23, v24, v25
    FILTER_3_IN_16BITS_TO_8BITS2 v23, v24, v25, v26 //output to v26[1]
    st1 {v26.16b}, [x2], x3 //write 16Byte : 0 line

    //prfm pldl1strm, [x0, x1]
    ld1 {v2.8b, v3.8b, v4.8b}, [x0], x1 // v2=src[3*stride]
    // vertical filtered into v20/v21
    FILTER_6TAG_8BITS_TO_16BITS1 v5, v8, v11, v14, v17, v2, v20, v0, v1
    FILTER_6TAG_8BITS_TO_16BITS1 v6, v9, v12, v15, v18, v3, v21, v0, v1
    // horizon filtered
    UNPACK_2_16BITS_TO_ABC  v20, v21, v23, v24, v25
    FILTER_3_IN_16BITS_TO_8BITS1 v23, v24, v25, v26 //output to v26[0]
    // vertical filtered into v21/v22
    FILTER_6TAG_8BITS_TO_16BITS1 v7, v10, v13, v16, v19, v4, v22, v0, v1
    UNPACK_2_16BITS_TO_ABC  v21, v22, v23, v24, v25
    FILTER_3_IN_16BITS_TO_8BITS2 v23, v24, v25, v26 //output to v26[1]
    st1 {v26.16b}, [x2], x3 //write 16Byte : 1 line

    //prfm pldl1strm, [x0, x1]
    ld1 {v5.8b, v6.8b, v7.8b}, [x0], x1 // v2=src[3*stride]
    // vertical filtered into v20/v21
    FILTER_6TAG_8BITS_TO_16BITS1 v8, v11, v14, v17, v2, v5, v20, v0, v1
    FILTER_6TAG_8BITS_TO_16BITS1 v9, v12, v15, v18, v3, v6, v21, v0, v1
    // horizon filtered
    UNPACK_2_16BITS_TO_ABC  v20, v21, v23, v24, v25
    FILTER_3_IN_16BITS_TO_8BITS1 v23, v24, v25, v26 //output to v26[0]
    // vertical filtered into v21/v22
    FILTER_6TAG_8BITS_TO_16BITS1 v10, v13, v16, v19, v4, v7, v22, v0, v1
    UNPACK_2_16BITS_TO_ABC  v21, v22, v23, v24, v25
    FILTER_3_IN_16BITS_TO_8BITS2 v23, v24, v25, v26 //output to v26[1]
    st1 {v26.16b}, [x2], x3 //write 16Byte : 2 line

    //prfm pldl1strm, [x0, x1]
    ld1 {v8.8b, v9.8b, v10.8b}, [x0], x1 // v2=src[3*stride]
    // vertical filtered into v20/v21
    FILTER_6TAG_8BITS_TO_16BITS1 v11, v14, v17, v2, v5, v8, v20, v0, v1
    FILTER_6TAG_8BITS_TO_16BITS1 v12, v15, v18, v3, v6, v9, v21, v0, v1
    // horizon filtered
    UNPACK_2_16BITS_TO_ABC  v20, v21, v23, v24, v25
    FILTER_3_IN_16BITS_TO_8BITS1 v23, v24, v25, v26 //output to v26[0]
    // vertical filtered into v21/v22
    FILTER_6TAG_8BITS_TO_16BITS1 v13, v16, v19, v4, v7, v10, v22, v0, v1
    UNPACK_2_16BITS_TO_ABC  v21, v22, v23, v24, v25
    FILTER_3_IN_16BITS_TO_8BITS2 v23, v24, v25, v26 //output to v26[1]
    st1 {v26.16b}, [x2], x3 //write 16Byte : 3 line

    //prfm pldl1strm, [x0, x1]
    ld1 {v11.8b, v12.8b, v13.8b}, [x0], x1 // v2=src[3*stride]
    // vertical filtered into v20/v21
    FILTER_6TAG_8BITS_TO_16BITS1 v14, v17, v2, v5, v8, v11, v20, v0, v1
    FILTER_6TAG_8BITS_TO_16BITS1 v15, v18, v3, v6, v9, v12, v21, v0, v1
    // horizon filtered
    UNPACK_2_16BITS_TO_ABC  v20, v21, v23, v24, v25
    FILTER_3_IN_16BITS_TO_8BITS1 v23, v24, v25, v26 //output to v26[0]
    // vertical filtered into v21/v22
    FILTER_6TAG_8BITS_TO_16BITS1 v16, v19, v4, v7, v10, v13, v22, v0, v1
    UNPACK_2_16BITS_TO_ABC  v21, v22, v23, v24, v25
    FILTER_3_IN_16BITS_TO_8BITS2 v23, v24, v25, v26 //output to v26[1]
    st1 {v26.16b}, [x2], x3 //write 16Byte : 4 line

    //prfm pldl1strm, [x0, x1]
    ld1 {v14.8b, v15.8b, v16.8b}, [x0], x1 // v2=src[3*stride]
    // vertical filtered into v20/v21
    FILTER_6TAG_8BITS_TO_16BITS1 v17, v2, v5, v8, v11, v14, v20, v0, v1
    FILTER_6TAG_8BITS_TO_16BITS1 v18, v3, v6, v9, v12, v15, v21, v0, v1
    // horizon filtered
    UNPACK_2_16BITS_TO_ABC  v20, v21, v23, v24, v25
    FILTER_3_IN_16BITS_TO_8BITS1 v23, v24, v25, v26 //output to v26[0]
    // vertical filtered into v21/v22
    FILTER_6TAG_8BITS_TO_16BITS1 v19, v4, v7, v10, v13, v16, v22, v0, v1
    UNPACK_2_16BITS_TO_ABC  v21, v22, v23, v24, v25
    FILTER_3_IN_16BITS_TO_8BITS2 v23, v24, v25, v26 //output to v26[1]
    st1 {v26.16b}, [x2], x3 //write 16Byte : 5 line

    //prfm pldl1strm, [x0, x1]
    ld1 {v17.8b, v18.8b, v19.8b}, [x0], x1 // v2=src[3*stride]
    // vertical filtered into v20/v21
    FILTER_6TAG_8BITS_TO_16BITS1 v2, v5, v8, v11, v14, v17, v20, v0, v1
    FILTER_6TAG_8BITS_TO_16BITS1 v3, v6, v9, v12, v15, v18, v21, v0, v1
    // horizon filtered
    UNPACK_2_16BITS_TO_ABC  v20, v21, v23, v24, v25
    FILTER_3_IN_16BITS_TO_8BITS1 v23, v24, v25, v26 //output to v26[0]
    // vertical filtered into v21/v22
    FILTER_6TAG_8BITS_TO_16BITS1 v4, v7, v10, v13, v16, v19, v22, v0, v1
    UNPACK_2_16BITS_TO_ABC  v21, v22, v23, v24, v25
    FILTER_3_IN_16BITS_TO_8BITS2 v23, v24, v25, v26 //output to v26[1]
    st1 {v26.16b}, [x2], x3 //write 16Byte : 6 line

    //prfm pldl1strm, [x0, x1]
    ld1 {v2.8b, v3.8b, v4.8b}, [x0], x1 // v2=src[3*stride]
    // vertical filtered into v20/v21
    FILTER_6TAG_8BITS_TO_16BITS1 v5, v8, v11, v14, v17, v2, v20, v0, v1
    FILTER_6TAG_8BITS_TO_16BITS1 v6, v9, v12, v15, v18, v3, v21, v0, v1
    // horizon filtered
    UNPACK_2_16BITS_TO_ABC  v20, v21, v23, v24, v25
    FILTER_3_IN_16BITS_TO_8BITS1 v23, v24, v25, v26 //output to v26[0]
    // vertical filtered into v21/v22
    FILTER_6TAG_8BITS_TO_16BITS1 v7, v10, v13, v16, v19, v4, v22, v0, v1
    UNPACK_2_16BITS_TO_ABC  v21, v22, v23, v24, v25
    FILTER_3_IN_16BITS_TO_8BITS2 v23, v24, v25, v26 //output to v26[1]
    st1 {v26.16b}, [x2], x3 //write 16Byte : 7 line

    mov v5.16b, v11.16b
    mov v11.16b, v17.16b
    mov v30.16b, v2.16b
    mov v2.16b, v8.16b
    mov v8.16b, v14.16b
    mov v14.16b, v30.16b

    mov v6.16b, v12.16b
    mov v12.16b, v18.16b
    mov v30.16b, v3.16b
    mov v3.16b, v9.16b
    mov v9.16b, v15.16b
    mov v15.16b, v30.16b

    mov v7.16b, v13.16b
    mov v13.16b, v19.16b
    mov v30.16b, v4.16b
    mov v4.16b, v10.16b
    mov v10.16b, v16.16b
    mov v16.16b, v30.16b

    sub x4, x4, #8
    cbnz x4, w16_hv_mc_luma_loop

    ldp d14, d15, [sp], #16
    ldp d12, d13, [sp], #16
    ldp d10, d11, [sp], #16
    ldp d8, d9, [sp], #16
WELS_ASM_AARCH64_FUNC_END

//void McHorVer22WidthEq8_AArch64_neon (const uint8_t* pSrc, int32_t iSrcStride, uint8_t* pDst, int32_t iDstStride,
//                                      int32_t iHeight);
WELS_ASM_AARCH64_FUNC_BEGIN McHorVer22WidthEq8_AArch64_neon
    SIGN_EXTENSION x1,w1
    SIGN_EXTENSION x3,w3
    SIGN_EXTENSION x4,w4
    sub x0, x0, #2
    sub x0, x0, x1, lsl #1
    movi v0.8h, #20, lsl #0
    movi v1.8h, #5, lsl #0

    //prfm pldl1strm, [x0]
    //prfm pldl1strm, [x0, x1]
    ld1 {v2.16b}, [x0], x1 // v2=src[-2*stride]
    //prfm pldl1strm, [x0, x1]
    ld1 {v3.16b}, [x0], x1 // v5=src[-1*stride]
    //prfm pldl1strm, [x0, x1]
    ld1 {v4.16b}, [x0], x1 // v8=src[0*stride]
    //prfm pldl1strm, [x0, x1]
    ld1 {v5.16b}, [x0], x1 // v11=src[1*stride]
    //prfm pldl1strm, [x0, x1]
    ld1 {v6.16b}, [x0], x1 // v14=src[2*stride]

w8_hv_mc_luma_loop:
    //prfm pldl1strm, [x0, x1]
    ld1 {v7.16b}, [x0], x1 // v7=src[3*stride]
    // vertical filtered into v20/v21
    FILTER_6TAG_8BITS_TO_16BITS1 v2, v3, v4, v5, v6, v7, v20, v0, v1
    FILTER_6TAG_8BITS_TO_16BITS2 v2, v3, v4, v5, v6, v7, v21, v0, v1
    // horizon filtered
    UNPACK_2_16BITS_TO_ABC  v20, v21, v23, v24, v25
    FILTER_3_IN_16BITS_TO_8BITS1 v23, v24, v25, v26 //output to v26[0]
    st1 {v26.8b}, [x2], x3 //write 8Byte : 0 line

    //prfm pldl1strm, [x0, x1]
    ld1 {v2.16b}, [x0], x1 // v2=src[3*stride]
    // vertical filtered into v20/v21
    FILTER_6TAG_8BITS_TO_16BITS1 v3, v4, v5, v6, v7, v2, v20, v0, v1
    FILTER_6TAG_8BITS_TO_16BITS2 v3, v4, v5, v6, v7, v2, v21, v0, v1
    // horizon filtered
    UNPACK_2_16BITS_TO_ABC  v20, v21, v23, v24, v25
    FILTER_3_IN_16BITS_TO_8BITS1 v23, v24, v25, v26 //output to v26[0]
    st1 {v26.8b}, [x2], x3 //write 8Byte : 1 line

    //prfm pldl1strm, [x0, x1]
    ld1 {v3.16b}, [x0], x1 // v3=src[3*stride]
    // vertical filtered into v20/v21
    FILTER_6TAG_8BITS_TO_16BITS1 v4, v5, v6, v7, v2, v3, v20, v0, v1
    FILTER_6TAG_8BITS_TO_16BITS2 v4, v5, v6, v7, v2, v3, v21, v0, v1
    // horizon filtered
    UNPACK_2_16BITS_TO_ABC  v20, v21, v23, v24, v25
    FILTER_3_IN_16BITS_TO_8BITS1 v23, v24, v25, v26 //output to v26[0]
    st1 {v26.8b}, [x2], x3 //write 8Byte : 2 line

    //prfm pldl1strm, [x0, x1]
    ld1 {v4.16b}, [x0], x1 // v4=src[3*stride]
    // vertical filtered into v20/v21
    FILTER_6TAG_8BITS_TO_16BITS1 v5, v6, v7, v2, v3, v4, v20, v0, v1
    FILTER_6TAG_8BITS_TO_16BITS2 v5, v6, v7, v2, v3, v4, v21, v0, v1
    // horizon filtered
    UNPACK_2_16BITS_TO_ABC  v20, v21, v23, v24, v25
    FILTER_3_IN_16BITS_TO_8BITS1 v23, v24, v25, v26 //output to v26[0]
    st1 {v26.8b}, [x2], x3 //write 8Byte : 3 line


    mov v5.16b, v3.16b
    mov v3.16b, v7.16b
    mov v30.16b, v2.16b
    mov v2.16b, v6.16b
    mov v6.16b, v4.16b
    mov v4.16b, v30.16b

    sub x4, x4, #4
    cbnz x4, w8_hv_mc_luma_loop
WELS_ASM_AARCH64_FUNC_END
//void McHorVer22WidthEq4_AArch64_neon (const uint8_t* pSrc, int32_t iSrcStride, uint8_t* pDst, int32_t iDstStride,
//                                      int32_t iHeight);

WELS_ASM_AARCH64_FUNC_BEGIN McHorVer22WidthEq4_AArch64_neon
    SIGN_EXTENSION x1,w1
    SIGN_EXTENSION x3,w3
    SIGN_EXTENSION x4,w4
    sub x0, x0, #2
    sub x0, x0, x1, lsl #1
    movi v0.8h, #20, lsl #0
    movi v1.8h, #5, lsl #0

    //prfm pldl1strm, [x0]
    //prfm pldl1strm, [x0, x1]
    ld1 {v2.16b}, [x0], x1 // v2=src[-2*stride]
    //prfm pldl1strm, [x0, x1]
    ld1 {v3.16b}, [x0], x1 // v3=src[-1*stride]
    //prfm pldl1strm, [x0, x1]
    ld1 {v4.16b}, [x0], x1 // v4=src[0*stride]
    //prfm pldl1strm, [x0, x1]
    ld1 {v5.16b}, [x0], x1 // v5=src[1*stride]
    //prfm pldl1strm, [x0, x1]
    ld1 {v6.16b}, [x0], x1 // v6=src[2*stride]

w4_hv_mc_luma_loop:
    //prfm pldl1strm, [x0, x1]
    ld1 {v7.16b}, [x0], x1 // v7=src[3*stride]
    // vertical filtered into v20/v21 1st line
    FILTER_6TAG_8BITS_TO_16BITS1 v2, v3, v4, v5, v6, v7, v20, v0, v1
    FILTER_6TAG_8BITS_TO_16BITS2 v2, v3, v4, v5, v6, v7, v21, v0, v1
    //prfm pldl1strm, [x0, x1]
    ld1 {v2.16b}, [x0], x1 // v16=src[4*stride]
    // vertical filtered into v22/v23 2nd line
    FILTER_6TAG_8BITS_TO_16BITS1 v3, v4, v5, v6, v7, v2, v22, v0, v1
    FILTER_6TAG_8BITS_TO_16BITS2 v3, v4, v5, v6, v7, v2, v23, v0, v1
    // horizon filtered
    UNPACK_2_16BITS_TO_ABC  v20, v21, v24, v25, v26
    UNPACK_2_16BITS_TO_ABC  v22, v23, v28, v29, v30
    zip1 v24.2d, v24.2d, v28.2d
    zip1 v25.2d, v25.2d, v29.2d
    zip1 v26.2d, v26.2d, v30.2d
    FILTER_3_IN_16BITS_TO_8BITS1 v24, v25, v26, v27 //output to v27[0]
    st1 {v27.s}[0], [x2], x3 //write 4Byte : 0 line
    st1 {v27.s}[1], [x2], x3 //write 4Byte : 1 line


    //prfm pldl1strm, [x0, x1]
    ld1 {v3.16b}, [x0], x1 // v3=src[5*stride]
    // vertical filtered into v20/v21
    FILTER_6TAG_8BITS_TO_16BITS1 v4, v5, v6, v7, v2, v3, v20, v0, v1
    FILTER_6TAG_8BITS_TO_16BITS2 v4, v5, v6, v7, v2, v3, v21, v0, v1
    //prfm pldl1strm, [x0, x1]
    ld1 {v4.16b}, [x0], x1 // v4=src[6*stride]
    FILTER_6TAG_8BITS_TO_16BITS1 v5, v6, v7, v2, v3, v4, v22, v0, v1
    FILTER_6TAG_8BITS_TO_16BITS2 v5, v6, v7, v2, v3, v4, v23, v0, v1
    // horizon filtered
    UNPACK_2_16BITS_TO_ABC  v20, v21, v24, v25, v26
    UNPACK_2_16BITS_TO_ABC  v22, v23, v28, v29, v30
    zip1 v24.2d, v24.2d, v28.2d
    zip1 v25.2d, v25.2d, v29.2d
    zip1 v26.2d, v26.2d, v30.2d
    FILTER_3_IN_16BITS_TO_8BITS1 v24, v25, v26, v27 //output to v27[0]
    st1 {v27.s}[0], [x2], x3 //write 4Byte : 2 line
    st1 {v27.s}[1], [x2], x3 //write 4Byte : 3 line

    mov v5.16b, v3.16b
    mov v3.16b, v7.16b
    mov v30.16b, v2.16b
    mov v2.16b, v6.16b
    mov v6.16b, v4.16b
    mov v4.16b, v30.16b

    sub x4, x4, #4
    cbnz x4, w4_hv_mc_luma_loop
WELS_ASM_AARCH64_FUNC_END
//void McCopyWidthEq16_AArch64_neon (const uint8_t* pSrc, int32_t iSrcStride, uint8_t* pDst, int32_t iDstStride,
//                                   int32_t iHeight);
WELS_ASM_AARCH64_FUNC_BEGIN McCopyWidthEq16_AArch64_neon
    //prfm pldl1strm, [x0]
    SIGN_EXTENSION x1,w1
    SIGN_EXTENSION x3,w3
    SIGN_EXTENSION x4,w4
w16_copy_loop:
    //prfm pldl1strm, [x0, x1]
    ld1 {v0.16b}, [x0], x1  //read 16Byte : 0 line
    st1 {v0.16b}, [x2], x3 //write 16Byte : 0 line
    //prfm pldl1strm, [x0, x1]
    ld1 {v1.16b}, [x0], x1  //read 16Byte : 1 line
    st1 {v1.16b}, [x2], x3 //write 16Byte : 1 line

    sub x4, x4, #2
    cbnz x4, w16_copy_loop
WELS_ASM_AARCH64_FUNC_END
//void McCopyWidthEq8_AArch64_neon (const uint8_t* pSrc, int32_t iSrcStride, uint8_t* pDst, int32_t iDstStride,
//                                  int32_t iHeight);
WELS_ASM_AARCH64_FUNC_BEGIN McCopyWidthEq8_AArch64_neon
    //prfm pldl1strm, [x0]
    SIGN_EXTENSION x1,w1
    SIGN_EXTENSION x3,w3
    SIGN_EXTENSION x4,w4
w8_copy_loop:
    //prfm pldl1strm, [x0, x1]
    ld1 {v0.8b}, [x0], x1  //read 16Byte : 0 line
    st1 {v0.8b}, [x2], x3 //write 16Byte : 0 line
    //prfm pldl1strm, [x0, x1]
    ld1 {v1.8b}, [x0], x1  //read 16Byte : 1 line
    st1 {v1.8b}, [x2], x3 //write 16Byte : 1 line

    sub x4, x4, #2
    cbnz x4, w8_copy_loop
WELS_ASM_AARCH64_FUNC_END

WELS_ASM_AARCH64_FUNC_BEGIN McCopyWidthEq4_AArch64_neon
    //prfm pldl1strm, [x0]
    SIGN_EXTENSION x1,w1
    SIGN_EXTENSION x3,w3
    SIGN_EXTENSION x4,w4
w4_copy_loop:
    //prfm pldl1strm, [x0, x1]
    ld1 {v0.s}[0], [x0], x1  //read 16Byte : 0 line
    st1 {v0.s}[0], [x2], x3 //write 16Byte : 0 line
    //prfm pldl1strm, [x0, x1]
    ld1 {v1.s}[0], [x0], x1  //read 16Byte : 1 line
    st1 {v1.s}[0], [x2], x3 //write 16Byte : 1 line

    sub x4, x4, #2
    cbnz x4, w4_copy_loop
WELS_ASM_AARCH64_FUNC_END

//void PixStrideAvgWidthEq16_AArch64_neon (uint8_t* pDst, int32_t iDstStride, const uint8_t* pSrcA, int32_t iSrcStrideA,
//const uint8_t* pSrcB, int32_t iSrcStrideB, int32_t iHeight);

WELS_ASM_AARCH64_FUNC_BEGIN PixStrideAvgWidthEq16_AArch64_neon
    SIGN_EXTENSION x1,w1
    SIGN_EXTENSION x3,w3
    SIGN_EXTENSION x5,w5
    SIGN_EXTENSION x6,w6
enc_w16_pix_avg_loop:
    ld1 {v0.16b}, [x2], x3  //read 16Byte : src0: 0 line
    ld1 {v1.16b}, [x4], x5  //read 16Byte : src1: 0 line
    ld1 {v2.16b}, [x2], x3  //read 16Byte : src0: 1 line
    ld1 {v3.16b}, [x4], x5  //read 16Byte : src1: 1 line
    ld1 {v4.16b}, [x2], x3  //read 16Byte : src0: 2 line
    ld1 {v5.16b}, [x4], x5  //read 16Byte : src1: 2 line
    ld1 {v6.16b}, [x2], x3  //read 16Byte : src0: 3 line
    ld1 {v7.16b}, [x4], x5  //read 16Byte : src1: 3 line
    AVERAGE_TWO_8BITS1  v16, v0, v1
    AVERAGE_TWO_8BITS2  v16, v0, v1
    st1 {v16.16b}, [x0], x1 //write 16Byte : 0 line


    AVERAGE_TWO_8BITS1  v16, v2, v3
    AVERAGE_TWO_8BITS2  v16, v2, v3
    st1 {v16.16b}, [x0], x1 //write 16Byte : 1 line


    AVERAGE_TWO_8BITS1  v16, v4, v5
    AVERAGE_TWO_8BITS2  v16, v4, v5
    st1 {v16.16b}, [x0], x1 //write 16Byte : 2 line

    AVERAGE_TWO_8BITS1  v16, v6, v7
    AVERAGE_TWO_8BITS2  v16, v6, v7
    st1 {v16.16b}, [x0], x1 //write 16Byte : 3 line

    sub x6, x6, #4
    cbnz x6, enc_w16_pix_avg_loop
WELS_ASM_AARCH64_FUNC_END

//void PixStrideAvgWidthEq8_AArch64_neon (uint8_t* pDst, int32_t iDstStride, const uint8_t* pSrcA, int32_t iSrcStrideA,
//                                        const uint8_t* pSrcB, int32_t iSrcStrideB, int32_t iHeight);
WELS_ASM_AARCH64_FUNC_BEGIN PixStrideAvgWidthEq8_AArch64_neon
    //prfm pldl1strm, [x2]
    //prfm pldl1strm, [x4]
    SIGN_EXTENSION x1,w1
    SIGN_EXTENSION x3,w3
    SIGN_EXTENSION x5,w5
    SIGN_EXTENSION x6,w6
enc_w8_pix_avg_loop:
    //prfm pldl1strm, [x2, x3]
    //prfm pldl1strm, [x4, x5]
    ld1 {v0.8b}, [x2], x3  //read 8Byte : src0: 0 line
    ld1 {v1.8b}, [x4], x5  //read 8Byte : src1: 0 line
    //prfm pldl1strm, [x2, x3]
    //prfm pldl1strm, [x4, x5]
    ld1 {v2.8b}, [x2], x3  //read 8Byte : src0: 1 line
    ld1 {v3.8b}, [x4], x5  //read 8Byte : src1: 1 line
    //prfm pldl1strm, [x2, x3]
    //prfm pldl1strm, [x4, x5]
    ld1 {v4.8b}, [x2], x3  //read 8Byte : src0: 2 line
    ld1 {v5.8b}, [x4], x5  //read 8Byte : src1: 2 line
    //prfm pldl1strm, [x2, x3]
    //prfm pldl1strm, [x4, x5]
    ld1 {v6.8b}, [x2], x3  //read 8Byte : src0: 3 line
    ld1 {v7.8b}, [x4], x5  //read 8Byte : src1: 3 line
    AVERAGE_TWO_8BITS1  v16, v0, v1
    st1 {v16.8b}, [x0], x1 //write 8Byte : 0 line

    AVERAGE_TWO_8BITS1  v16, v2, v3
    st1 {v16.8b}, [x0], x1 //write 8Byte : 1 line


    AVERAGE_TWO_8BITS1  v16, v4, v5
    st1 {v16.8b}, [x0], x1 //write 8Byte : 2 line

    AVERAGE_TWO_8BITS1  v16, v6, v7
    st1 {v16.8b}, [x0], x1 //write 8Byte : 3 line

    sub x6, x6, #4
    cbnz x6, enc_w8_pix_avg_loop
WELS_ASM_AARCH64_FUNC_END
//void PixelAvgWidthEq16_AArch64_neon (uint8_t* pDst, int32_t iDstStride, const uint8_t* pSrcA, int32_t iSrcAStride,
//                                     const uint8_t* pSrcB, int32_t iSrcBStride, int32_t iHeight);
WELS_ASM_AARCH64_FUNC_BEGIN PixelAvgWidthEq16_AArch64_neon
    //prfm pldl1strm, [x2]
    //prfm pldl1strm, [x4]
    SIGN_EXTENSION x1,w1
    SIGN_EXTENSION x3,w3
    SIGN_EXTENSION x5,w5
    SIGN_EXTENSION x6,w6
w16_pix_avg_loop:
    //prfm pldl1strm, [x2, x3]
    //prfm pldl1strm, [x4, x5]
    ld1 {v0.16b}, [x2], x3  //read 16Byte : src0: 0 line
    ld1 {v1.16b}, [x4], x5  //read 16Byte : src1: 0 line
    //prfm pldl1strm, [x2, x3]
    //prfm pldl1strm, [x4, x5]
    ld1 {v2.16b}, [x2], x3  //read 16Byte : src0: 1 line
    ld1 {v3.16b}, [x4], x5  //read 16Byte : src1: 1 line
    //prfm pldl1strm, [x2, x3]
    //prfm pldl1strm, [x4, x5]
    ld1 {v4.16b}, [x2], x3  //read 16Byte : src0: 2 line
    ld1 {v5.16b}, [x4], x5  //read 16Byte : src1: 2 line
    //prfm pldl1strm, [x2, x3]
    //prfm pldl1strm, [x4, x5]
    ld1 {v6.16b}, [x2], x3  //read 16Byte : src0: 3 line
    ld1 {v7.16b}, [x4], x5  //read 16Byte : src1: 3 line
    AVERAGE_TWO_8BITS1  v16, v0, v1
    AVERAGE_TWO_8BITS2  v16, v0, v1
    st1 {v16.16b}, [x0], x1 //write 16Byte : 0 line


    AVERAGE_TWO_8BITS1  v16, v2, v3
    AVERAGE_TWO_8BITS2  v16, v2, v3
    st1 {v16.16b}, [x0], x1 //write 16Byte : 1 line


    AVERAGE_TWO_8BITS1  v16, v4, v5
    AVERAGE_TWO_8BITS2  v16, v4, v5
    st1 {v16.16b}, [x0], x1 //write 16Byte : 2 line

    AVERAGE_TWO_8BITS1  v16, v6, v7
    AVERAGE_TWO_8BITS2  v16, v6, v7
    st1 {v16.16b}, [x0], x1 //write 16Byte : 3 line

    sub x6, x6, #4
    cbnz x6, w16_pix_avg_loop
WELS_ASM_AARCH64_FUNC_END
//void PixelAvgWidthEq8_AArch64_neon (uint8_t* pDst, int32_t iDstStride, const uint8_t* pSrcA, int32_t iSrcAStride,
//                                   const uint8_t* pSrcB, int32_t iSrcBStride, int32_t iHeight);
WELS_ASM_AARCH64_FUNC_BEGIN PixelAvgWidthEq8_AArch64_neon
    //prfm pldl1strm, [x2]
    //prfm pldl1strm, [x4]
    SIGN_EXTENSION x1,w1
    SIGN_EXTENSION x3,w3
    SIGN_EXTENSION x5,w5
    SIGN_EXTENSION x6,w6
w8_pix_avg_loop:
    //prfm pldl1strm, [x2, x3]
    //prfm pldl1strm, [x4, x5]
    ld1 {v0.8b}, [x2], x3  //read 8Byte : src0: 0 line
    ld1 {v1.8b}, [x4], x5  //read 8Byte : src1: 0 line
    //prfm pldl1strm, [x2, x3]
    //prfm pldl1strm, [x4, x5]
    ld1 {v2.8b}, [x2], x3  //read 8Byte : src0: 1 line
    ld1 {v3.8b}, [x4], x5  //read 8Byte : src1: 1 line
    //prfm pldl1strm, [x2, x3]
    //prfm pldl1strm, [x4, x5]
    ld1 {v4.8b}, [x2], x3  //read 8Byte : src0: 2 line
    ld1 {v5.8b}, [x4], x5  //read 8Byte : src1: 2 line
    //prfm pldl1strm, [x2, x3]
    //prfm pldl1strm, [x4, x5]
    ld1 {v6.8b}, [x2], x3  //read 8Byte : src0: 3 line
    ld1 {v7.8b}, [x4], x5  //read 8Byte : src1: 3 line
    AVERAGE_TWO_8BITS1  v16, v0, v1
    st1 {v16.8b}, [x0], x1 //write 8Byte : 0 line

    AVERAGE_TWO_8BITS1  v16, v2, v3
    st1 {v16.8b}, [x0], x1 //write 8Byte : 1 line


    AVERAGE_TWO_8BITS1  v16, v4, v5
    st1 {v16.8b}, [x0], x1 //write 8Byte : 2 line

    AVERAGE_TWO_8BITS1  v16, v6, v7
    st1 {v16.8b}, [x0], x1 //write 8Byte : 3 line

    sub x6, x6, #4
    cbnz x6, w8_pix_avg_loop
WELS_ASM_AARCH64_FUNC_END

//void PixelAvgWidthEq4_AArch64_neon (uint8_t* pDst, int32_t iDstStride, const uint8_t* pSrcA, int32_t iSrcAStride,
//                                    const uint8_t* pSrcB, int32_t iSrcBStride, int32_t iHeight);
WELS_ASM_AARCH64_FUNC_BEGIN PixelAvgWidthEq4_AArch64_neon
    //prfm pldl1strm, [x2]
    //prfm pldl1strm, [x4]
    SIGN_EXTENSION x1,w1
    SIGN_EXTENSION x3,w3
    SIGN_EXTENSION x5,w5
    SIGN_EXTENSION x6,w6
w4_pix_avg_loop:
    //prfm pldl1strm, [x2, x3]
    //prfm pldl1strm, [x4, x5]
    ld1 {v0.s}[0], [x2], x3  //read 4Byte : src0: 0 line
    ld1 {v1.s}[0], [x4], x5  //read 4Byte : src1: 0 line
    //prfm pldl1strm, [x2, x3]
    //prfm pldl1strm, [x4, x5]
    ld1 {v0.s}[1], [x2], x3  //read 4Byte : src0: 1 line
    ld1 {v1.s}[1], [x4], x5  //read 4Byte : src1: 1 line
    AVERAGE_TWO_8BITS1  v2, v0, v1
    st1 {v2.s}[0], [x0], x1 //write 4Byte : 0 line
    st1 {v2.s}[1], [x0], x1 //write 4Byte : 1 line

    sub x6, x6, #2
    cbnz x6, w4_pix_avg_loop
WELS_ASM_AARCH64_FUNC_END
//void McChromaWidthEq8_AArch64_neon (const uint8_t* pSrc, int32_t iSrcStride, uint8_t* pDst, int32_t iDstStride,
//                                    int32_t* pWeights, int32_t iHeight);
WELS_ASM_AARCH64_FUNC_BEGIN McChromaWidthEq8_AArch64_neon
    SIGN_EXTENSION x1,w1
    SIGN_EXTENSION x3,w3
    SIGN_EXTENSION x5,w5
    ld4r {v28.8b, v29.8b, v30.8b, v31.8b}, [x4] //load A/B/C/D
    ld1 {v16.16b}, [x0], x1  // src[x]
    ext v17.16b, v16.16b, v16.16b, #1  // src[x+1]
w8_mc_chroma_loop:
    ld1 {v18.16b}, [x0], x1  // src[x+stride]
    ext v19.16b, v18.16b, v18.16b, #1  // src[x+stride+1]

    ld1 {v20.16b}, [x0], x1  // src[x+2*stride]
    ext v21.16b, v20.16b, v20.16b, #1  // src[x+2*stride+1]

    ld1 {v22.16b}, [x0], x1  // src[x+3*stride]
    ext v23.16b, v22.16b, v22.16b, #1  // src[x+3*stride+1]

    ld1 {v24.16b}, [x0], x1  // src[x+4*stride]
    ext v25.16b, v24.16b, v24.16b, #1  // src[x+4*stride+1]

    umull v0.8h, v16.8b, v28.8b
    umull v2.8h, v18.8b, v28.8b
    umull v4.8h, v20.8b, v28.8b
    umull v6.8h, v22.8b, v28.8b

    umlal v0.8h, v17.8b, v29.8b
    umlal v2.8h, v19.8b, v29.8b
    umlal v4.8h, v21.8b, v29.8b
    umlal v6.8h, v23.8b, v29.8b

    umlal v0.8h, v18.8b, v30.8b
    umlal v2.8h, v20.8b, v30.8b
    umlal v4.8h, v22.8b, v30.8b
    umlal v6.8h, v24.8b, v30.8b

    umlal v0.8h, v19.8b, v31.8b
    umlal v2.8h, v21.8b, v31.8b
    umlal v4.8h, v23.8b, v31.8b
    umlal v6.8h, v25.8b, v31.8b

    rshrn v1.8b, v0.8h, #6
    st1 {v1.8b}, [x2], x3

    rshrn v3.8b, v2.8h, #6
    st1 {v3.8b}, [x2], x3

    rshrn v5.8b, v4.8h, #6
    st1 {v5.8b}, [x2], x3

    rshrn v7.8b, v6.8h, #6
    st1 {v7.8b}, [x2], x3

    mov v16.16b, v24.16b
    mov v17.16b, v25.16b
    sub x5, x5, #4
    cbnz x5, w8_mc_chroma_loop
WELS_ASM_AARCH64_FUNC_END
//void McChromaWidthEq4_AArch64_neon (const uint8_t* pSrc, int32_t iSrcStride, uint8_t* pDst, int32_t iDstStride,
//                                    int32_t* pWeights, int32_t iHeight);
WELS_ASM_AARCH64_FUNC_BEGIN McChromaWidthEq4_AArch64_neon
    SIGN_EXTENSION x1,w1
    SIGN_EXTENSION x3,w3
    SIGN_EXTENSION x5,w5
    ld4r {v4.8b, v5.8b, v6.8b, v7.8b}, [x4] //load A/B/C/D
    ld1 {v0.8b}, [x0], x1  // src[x]
    ext v1.8b, v0.8b, v0.8b, #1  // src[x+1]
w4_mc_chroma_loop:
    ld1 {v2.8b}, [x0], x1  // src[x+stride]
    ext v3.8b, v2.8b, v2.8b, #1  // src[x+stride+1]
    ld1 {v18.8b}, [x0], x1  // src[x+2*stride]
    ext v19.8b, v18.8b, v18.8b, #1  // src[x+2*stride+1]

    zip1 v0.4s, v0.4s, v2.4s
    zip1 v1.4s, v1.4s, v3.4s
    zip1 v2.4s, v2.4s, v18.4s
    zip1 v3.4s, v3.4s, v19.4s

    umull v16.8h, v0.8b, v4.8b
    umlal v16.8h, v1.8b, v5.8b
    umlal v16.8h, v2.8b, v6.8b
    umlal v16.8h, v3.8b, v7.8b
    rshrn v17.8b, v16.8h, #6
    st1 {v17.s}[0], [x2], x3
    st1 {v17.s}[1], [x2], x3

    mov v0.8b, v18.8b
    mov v1.8b, v19.8b
    sub x5, x5, #2
    cbnz x5, w4_mc_chroma_loop
WELS_ASM_AARCH64_FUNC_END

//void McHorVer20Width17_AArch64_neon (const uint8_t* pSrc, int32_t iSrcStride, uint8_t* pDst, int32_t iDstStride,
//                                    int32_t iHeight);// width+1
WELS_ASM_AARCH64_FUNC_BEGIN McHorVer20Width17_AArch64_neon
    SIGN_EXTENSION x1,w1
    SIGN_EXTENSION x3,w3
    SIGN_EXTENSION x4,w4
    sub x0, x0, #2
    sub x3, x3, #16
    mov x5, #16
    movi v0.8h, #20, lsl #0
    movi v1.8h, #5, lsl #0
    ldr q22, filter_para
w17_h_mc_luma_loop:
    ld1 {v2.16b, v3.16b}, [x0], x1 //only use 22(17+5); v2=src[-2]

    //prfm pldl1strm, [x0]
    ext v5.16b, v2.16b, v3.16b, #1    //v5=src[-1]
    ext v6.16b, v2.16b, v3.16b, #2    //v6=src[0]
    ext v7.16b, v2.16b, v3.16b, #3    //v7=src[1]
    ext v16.16b, v2.16b, v3.16b, #4   //v16=src[2]
    ext v17.16b, v2.16b, v3.16b, #5   //v17=src[3]

    FILTER_6TAG_8BITS1 v2, v5, v6, v7, v16, v17, v20, v0, v1
    FILTER_6TAG_8BITS2 v2, v5, v6, v7, v16, v17, v20, v0, v1
    st1 {v20.16b}, [x2], x5 //write 16Byte

    ext v21.8b, v3.8b, v3.8b, #7 // [0][1][2][3][4][5]XY-->O[0][1][2][3][4][5]X
    FILTER_SINGLE_TAG_8BITS v21, v22, v23, h21
    st1 {v21.b}[0], [x2], x3 //write 16th Byte

    sub x4, x4, #1
    cbnz x4, w17_h_mc_luma_loop
WELS_ASM_AARCH64_FUNC_END

//void McHorVer20Width9_AArch64_neon (const uint8_t* pSrc, int32_t iSrcStride, uint8_t* pDst, int32_t iDstStride,
//                                    int32_t iHeight);// width+1
WELS_ASM_AARCH64_FUNC_BEGIN McHorVer20Width9_AArch64_neon
    SIGN_EXTENSION x1,w1
    SIGN_EXTENSION x3,w3
    SIGN_EXTENSION x4,w4
    sub x0, x0, #2
    sub x3, x3, #8
    mov x5, #8
    movi v0.8h, #20, lsl #0
    movi v1.8h, #5, lsl #0
    ldr q22, filter_para
w9_h_mc_luma_loop:
    ld1 {v2.16b}, [x0], x1 //only use 14(9+5); v2=src[-2]
    mov v3.d[0], v2.d[1]
    //prfm pldl1strm, [x0]
    ext v5.16b, v2.16b, v4.16b, #1    //v5=src[-1]
    ext v6.16b, v2.16b, v4.16b, #2    //v6=src[0]
    ext v7.16b, v2.16b, v4.16b, #3    //v7=src[1]
    ext v16.16b, v2.16b, v4.16b, #4   //v16=src[2]
    ext v17.16b, v2.16b, v4.16b, #5   //v17=src[3]

    FILTER_6TAG_8BITS1 v2, v5, v6, v7, v16, v17, v20, v0, v1
    st1 {v20.8b}, [x2], x5 //write 8Byte

    ext v21.8b, v3.8b, v3.8b, #7 // [0][1][2][3][4][5]XY-->O[0][1][2][3][4][5]X
    FILTER_SINGLE_TAG_8BITS v21, v22, v23, h21
    st1 {v21.b}[0], [x2], x3 //write 9th Byte

    sub x4, x4, #1
    cbnz x4, w9_h_mc_luma_loop
WELS_ASM_AARCH64_FUNC_END

//void McHorVer20Width5_AArch64_neon (const uint8_t* pSrc, int32_t iSrcStride, uint8_t* pDst, int32_t iDstStride,
//                                    int32_t iHeight);// width+1
WELS_ASM_AARCH64_FUNC_BEGIN McHorVer20Width5_AArch64_neon
    SIGN_EXTENSION x1,w1
    SIGN_EXTENSION x3,w3
    SIGN_EXTENSION x4,w4
    sub x0, x0, #2
    sub x3, x3, #4
    mov x5, #4
    movi v0.8h, #20, lsl #0
    movi v1.8h, #5, lsl #0
w5_h_mc_luma_loop:
    ld1 {v2.16b}, [x0], x1 //only use 10(5+5); v2=src[-2]

    ext v5.16b, v2.16b, v4.16b, #1    //v5=src[-1]
    ext v6.16b, v2.16b, v4.16b, #2    //v6=src[0]
    ext v7.16b, v2.16b, v4.16b, #3    //v7=src[1]
    ext v16.16b, v2.16b, v4.16b, #4   //v16=src[2]
    ext v17.16b, v2.16b, v4.16b, #5   //v17=src[3]

    FILTER_6TAG_8BITS1 v2, v5, v6, v7, v16, v17, v20, v0, v1
    st1 {v20.s}[0], [x2], x5 //write 4Byte
    st1 {v20.b}[4], [x2], x3 //write 5th Byte

    sub x4, x4, #1
    cbnz x4, w5_h_mc_luma_loop
WELS_ASM_AARCH64_FUNC_END

//void McHorVer22Width17_AArch64_neon (const uint8_t* pSrc, int32_t iSrcStride, uint8_t* pDst, int32_t iDstStride,
//                                     int32_t iHeight);
WELS_ASM_AARCH64_FUNC_BEGIN McHorVer22Width17_AArch64_neon
    stp d8, d9, [sp,#-16]!
    stp d10, d11, [sp,#-16]!
    stp d12, d13, [sp,#-16]!
    stp d14, d15, [sp,#-16]!
    SIGN_EXTENSION x1,w1
    SIGN_EXTENSION x3,w3
    SIGN_EXTENSION x4,w4
    sub x0, x0, #2
    sub x0, x0, x1, lsl #1
    movi v0.8h, #20, lsl #0
    movi v1.8h, #5, lsl #0
    sub x3, x3, #16
    mov x5, #16
    ldr q29, filter_para

    sub x4, x4, #1

    //prfm pldl1strm, [x0]
    //prfm pldl1strm, [x0, x1]
    ld1 {v2.8b, v3.8b, v4.8b}, [x0], x1 // v2=src[-2*stride]
    //prfm pldl1strm, [x0, x1]
    ld1 {v5.8b, v6.8b, v7.8b}, [x0], x1 // v5=src[-1*stride]
    //prfm pldl1strm, [x0, x1]
    ld1 {v8.8b, v9.8b, v10.8b}, [x0], x1 // v8=src[0*stride]
    //prfm pldl1strm, [x0, x1]
    ld1 {v11.8b, v12.8b, v13.8b}, [x0], x1 // v11=src[1*stride]
    //prfm pldl1strm, [x0, x1]
    ld1 {v14.8b, v15.8b, v16.8b}, [x0], x1 // v14=src[2*stride]

w17_hv_mc_luma_loop:
    //prfm pldl1strm, [x0, x1]
    ld1 {v17.8b, v18.8b, v19.8b}, [x0], x1 // v17=src[3*stride]
    // vertical filtered into v20/v21
    FILTER_6TAG_8BITS_TO_16BITS1 v2, v5, v8, v11, v14, v17, v20, v0, v1
    FILTER_6TAG_8BITS_TO_16BITS1 v3, v6, v9, v12, v15, v18, v21, v0, v1
    // horizon filtered
    UNPACK_2_16BITS_TO_ABC  v20, v21, v23, v24, v25
    FILTER_3_IN_16BITS_TO_8BITS1 v23, v24, v25, v26 //output to v26[0]
    // vertical filtered into v21/v22
    FILTER_6TAG_8BITS_TO_16BITS1 v4, v7, v10, v13, v16, v19, v22, v0, v1
    UNPACK_2_16BITS_TO_ABC  v21, v22, v23, v24, v25
    FILTER_3_IN_16BITS_TO_8BITS2 v23, v24, v25, v26 //output to v26[1]
    st1 {v26.16b}, [x2], x5 //write 0:15 Byte : 0 line
    UNPACK_FILTER_SINGLE_TAG_16BITS v26, v22, v29, v27, v28, d26
    st1 {v26.b}[0], [x2], x3 //write 16th Byte : 0 line

    //prfm pldl1strm, [x0, x1]
    ld1 {v2.8b, v3.8b, v4.8b}, [x0], x1 // v2=src[4*stride]
    // vertical filtered into v20/v21
    FILTER_6TAG_8BITS_TO_16BITS1 v5, v8, v11, v14, v17, v2, v20, v0, v1
    FILTER_6TAG_8BITS_TO_16BITS1 v6, v9, v12, v15, v18, v3, v21, v0, v1
    // horizon filtered
    UNPACK_2_16BITS_TO_ABC  v20, v21, v23, v24, v25
    FILTER_3_IN_16BITS_TO_8BITS1 v23, v24, v25, v26 //output to v26[0]
    // vertical filtered into v21/v22
    FILTER_6TAG_8BITS_TO_16BITS1 v7, v10, v13, v16, v19, v4, v22, v0, v1
    UNPACK_2_16BITS_TO_ABC  v21, v22, v23, v24, v25
    FILTER_3_IN_16BITS_TO_8BITS2 v23, v24, v25, v26 //output to v26[1]
    st1 {v26.16b}, [x2], x5 //write 0:15Byte : 1 line
    UNPACK_FILTER_SINGLE_TAG_16BITS v26, v22, v29, v27, v28, d26
    st1 {v26.b}[0], [x2], x3 //write 16th Byte : 1 line

    //prfm pldl1strm, [x0, x1]
    ld1 {v5.8b, v6.8b, v7.8b}, [x0], x1 // v2=src[5*stride]
    // vertical filtered into v20/v21
    FILTER_6TAG_8BITS_TO_16BITS1 v8, v11, v14, v17, v2, v5, v20, v0, v1
    FILTER_6TAG_8BITS_TO_16BITS1 v9, v12, v15, v18, v3, v6, v21, v0, v1
    // horizon filtered
    UNPACK_2_16BITS_TO_ABC  v20, v21, v23, v24, v25
    FILTER_3_IN_16BITS_TO_8BITS1 v23, v24, v25, v26 //output to v26[0]
    // vertical filtered into v21/v22
    FILTER_6TAG_8BITS_TO_16BITS1 v10, v13, v16, v19, v4, v7, v22, v0, v1
    UNPACK_2_16BITS_TO_ABC  v21, v22, v23, v24, v25
    FILTER_3_IN_16BITS_TO_8BITS2 v23, v24, v25, v26 //output to v26[1]
    st1 {v26.16b}, [x2], x5 //write 0:15Byte : 2 line
    UNPACK_FILTER_SINGLE_TAG_16BITS v26, v22, v29, v27, v28, d26
    st1 {v26.b}[0], [x2], x3 //write 16th Byte : 2 line

    //prfm pldl1strm, [x0, x1]
    ld1 {v8.8b, v9.8b, v10.8b}, [x0], x1 // v2=src[6*stride]
    // vertical filtered into v20/v21
    FILTER_6TAG_8BITS_TO_16BITS1 v11, v14, v17, v2, v5, v8, v20, v0, v1
    FILTER_6TAG_8BITS_TO_16BITS1 v12, v15, v18, v3, v6, v9, v21, v0, v1
    // horizon filtered
    UNPACK_2_16BITS_TO_ABC  v20, v21, v23, v24, v25
    FILTER_3_IN_16BITS_TO_8BITS1 v23, v24, v25, v26 //output to v26[0]
    // vertical filtered into v21/v22
    FILTER_6TAG_8BITS_TO_16BITS1 v13, v16, v19, v4, v7, v10, v22, v0, v1
    UNPACK_2_16BITS_TO_ABC  v21, v22, v23, v24, v25
    FILTER_3_IN_16BITS_TO_8BITS2 v23, v24, v25, v26 //output to v26[1]
    st1 {v26.16b}, [x2], x5 //write 0:15Byte : 3 line
    UNPACK_FILTER_SINGLE_TAG_16BITS v26, v22, v29, v27, v28, d26
    st1 {v26.b}[0], [x2], x3 //write 16th Byte : 3 line

    //prfm pldl1strm, [x0, x1]
    ld1 {v11.8b, v12.8b, v13.8b}, [x0], x1 // v2=src[7*stride]
    // vertical filtered into v20/v21
    FILTER_6TAG_8BITS_TO_16BITS1 v14, v17, v2, v5, v8, v11, v20, v0, v1
    FILTER_6TAG_8BITS_TO_16BITS1 v15, v18, v3, v6, v9, v12, v21, v0, v1
    // horizon filtered
    UNPACK_2_16BITS_TO_ABC  v20, v21, v23, v24, v25
    FILTER_3_IN_16BITS_TO_8BITS1 v23, v24, v25, v26 //output to v26[0]
    // vertical filtered into v21/v22
    FILTER_6TAG_8BITS_TO_16BITS1 v16, v19, v4, v7, v10, v13, v22, v0, v1
    UNPACK_2_16BITS_TO_ABC  v21, v22, v23, v24, v25
    FILTER_3_IN_16BITS_TO_8BITS2 v23, v24, v25, v26 //output to v26[1]
    st1 {v26.16b}, [x2], x5 //write 0:15Byte : 4 line
    UNPACK_FILTER_SINGLE_TAG_16BITS v26, v22, v29, v27, v28, d26
    st1 {v26.b}[0], [x2], x3 //write 16th Byte : 4 line

    //prfm pldl1strm, [x0, x1]
    ld1 {v14.8b, v15.8b, v16.8b}, [x0], x1 // v2=src[8*stride]
    // vertical filtered into v20/v21
    FILTER_6TAG_8BITS_TO_16BITS1 v17, v2, v5, v8, v11, v14, v20, v0, v1
    FILTER_6TAG_8BITS_TO_16BITS1 v18, v3, v6, v9, v12, v15, v21, v0, v1
    // horizon filtered
    UNPACK_2_16BITS_TO_ABC  v20, v21, v23, v24, v25
    FILTER_3_IN_16BITS_TO_8BITS1 v23, v24, v25, v26 //output to v26[0]
    // vertical filtered into v21/v22
    FILTER_6TAG_8BITS_TO_16BITS1 v19, v4, v7, v10, v13, v16, v22, v0, v1
    UNPACK_2_16BITS_TO_ABC  v21, v22, v23, v24, v25
    FILTER_3_IN_16BITS_TO_8BITS2 v23, v24, v25, v26 //output to v26[1]
    st1 {v26.16b}, [x2], x5 //write 0:15Byte : 5 line
    UNPACK_FILTER_SINGLE_TAG_16BITS v26, v22, v29, v27, v28, d26
    st1 {v26.b}[0], [x2], x3 //write 16th Byte : 5 line

    //prfm pldl1strm, [x0, x1]
    ld1 {v17.8b, v18.8b, v19.8b}, [x0], x1 // v2=src[9*stride]
    // vertical filtered into v20/v21
    FILTER_6TAG_8BITS_TO_16BITS1 v2, v5, v8, v11, v14, v17, v20, v0, v1
    FILTER_6TAG_8BITS_TO_16BITS1 v3, v6, v9, v12, v15, v18, v21, v0, v1
    // horizon filtered
    UNPACK_2_16BITS_TO_ABC  v20, v21, v23, v24, v25
    FILTER_3_IN_16BITS_TO_8BITS1 v23, v24, v25, v26 //output to v26[0]
    // vertical filtered into v21/v22
    FILTER_6TAG_8BITS_TO_16BITS1 v4, v7, v10, v13, v16, v19, v22, v0, v1
    UNPACK_2_16BITS_TO_ABC  v21, v22, v23, v24, v25
    FILTER_3_IN_16BITS_TO_8BITS2 v23, v24, v25, v26 //output to v26[1]
    st1 {v26.16b}, [x2], x5 //write 0:15Byte : 6 line
    UNPACK_FILTER_SINGLE_TAG_16BITS v26, v22, v29, v27, v28, d26
    st1 {v26.b}[0], [x2], x3 //write 16th Byte : 6 line

    //prfm pldl1strm, [x0, x1]
    ld1 {v2.8b, v3.8b, v4.8b}, [x0], x1 // v2=src[10*stride]
    // vertical filtered into v20/v21
    FILTER_6TAG_8BITS_TO_16BITS1 v5, v8, v11, v14, v17, v2, v20, v0, v1
    FILTER_6TAG_8BITS_TO_16BITS1 v6, v9, v12, v15, v18, v3, v21, v0, v1
    // horizon filtered
    UNPACK_2_16BITS_TO_ABC  v20, v21, v23, v24, v25
    FILTER_3_IN_16BITS_TO_8BITS1 v23, v24, v25, v26 //output to v26[0]
    // vertical filtered into v21/v22
    FILTER_6TAG_8BITS_TO_16BITS1 v7, v10, v13, v16, v19, v4, v22, v0, v1
    UNPACK_2_16BITS_TO_ABC  v21, v22, v23, v24, v25
    FILTER_3_IN_16BITS_TO_8BITS2 v23, v24, v25, v26 //output to v26[1]
    st1 {v26.16b}, [x2], x5 //write 0:15Byte : 7 line
    UNPACK_FILTER_SINGLE_TAG_16BITS v26, v22, v29, v27, v28, d26
    st1 {v26.b}[0], [x2], x3 //write 16th Byte : 7 line

    mov v5.16b, v11.16b
    mov v11.16b, v17.16b
    mov v30.16b, v2.16b
    mov v2.16b, v8.16b
    mov v8.16b, v14.16b
    mov v14.16b, v30.16b

    mov v6.16b, v12.16b
    mov v12.16b, v18.16b
    mov v30.16b, v3.16b
    mov v3.16b, v9.16b
    mov v9.16b, v15.16b
    mov v15.16b, v30.16b

    mov v7.16b, v13.16b
    mov v13.16b, v19.16b
    mov v30.16b, v4.16b
    mov v4.16b, v10.16b
    mov v10.16b, v16.16b
    mov v16.16b, v30.16b

    sub x4, x4, #8
    cbnz x4, w17_hv_mc_luma_loop

    //prfm pldl1strm, [x0, x1]
    ld1 {v17.8b, v18.8b, v19.8b}, [x0], x1 // v17=src[3*stride]
    // vertical filtered into v20/v21
    FILTER_6TAG_8BITS_TO_16BITS1 v2, v5, v8, v11, v14, v17, v20, v0, v1
    FILTER_6TAG_8BITS_TO_16BITS1 v3, v6, v9, v12, v15, v18, v21, v0, v1
    // horizon filtered
    UNPACK_2_16BITS_TO_ABC  v20, v21, v23, v24, v25
    FILTER_3_IN_16BITS_TO_8BITS1 v23, v24, v25, v26 //output to v26[0]
    // vertical filtered into v21/v22
    FILTER_6TAG_8BITS_TO_16BITS1 v4, v7, v10, v13, v16, v19, v22, v0, v1
    UNPACK_2_16BITS_TO_ABC  v21, v22, v23, v24, v25
    FILTER_3_IN_16BITS_TO_8BITS2 v23, v24, v25, v26 //output to v26[1]
    st1 {v26.16b}, [x2], x5 //write 0:15 Byte : 0 line
    UNPACK_FILTER_SINGLE_TAG_16BITS v26, v22, v29, v27, v28, d26
    st1 {v26.b}[0], [x2], x3 //write 16th Byte : 0 line

    ldp d14, d15, [sp], #16
    ldp d12, d13, [sp], #16
    ldp d10, d11, [sp], #16
    ldp d8, d9, [sp], #16
WELS_ASM_AARCH64_FUNC_END

//void McHorVer22Width9_AArch64_neon (const uint8_t* pSrc, int32_t iSrcStride, uint8_t* pDst, int32_t iDstStride,
//                                    int32_t iHeight);//width+1&&height+1
WELS_ASM_AARCH64_FUNC_BEGIN McHorVer22Width9_AArch64_neon
    SIGN_EXTENSION x1,w1
    SIGN_EXTENSION x3,w3
    SIGN_EXTENSION x4,w4
    sub x0, x0, #2
    sub x0, x0, x1, lsl #1
    movi v0.8h, #20, lsl #0
    movi v1.8h, #5, lsl #0
    sub x3, x3, #8
    mov x5, #8
    ldr q29, filter_para
    sub x4, x4, #1

    //prfm pldl1strm, [x0]
    //prfm pldl1strm, [x0, x1]
    ld1 {v2.16b}, [x0], x1 // v2=src[-2*stride]
    //prfm pldl1strm, [x0, x1]
    ld1 {v3.16b}, [x0], x1 // v5=src[-1*stride]
    //prfm pldl1strm, [x0, x1]
    ld1 {v4.16b}, [x0], x1 // v8=src[0*stride]
    //prfm pldl1strm, [x0, x1]
    ld1 {v5.16b}, [x0], x1 // v11=src[1*stride]
    //prfm pldl1strm, [x0, x1]
    ld1 {v6.16b}, [x0], x1 // v14=src[2*stride]

w9_hv_mc_luma_loop:
    //prfm pldl1strm, [x0, x1]
    ld1 {v7.16b}, [x0], x1 // v7=src[3*stride]
    // vertical filtered into v20/v21
    FILTER_6TAG_8BITS_TO_16BITS1 v2, v3, v4, v5, v6, v7, v20, v0, v1
    FILTER_6TAG_8BITS_TO_16BITS2 v2, v3, v4, v5, v6, v7, v21, v0, v1
    // horizon filtered
    UNPACK_2_16BITS_TO_ABC  v20, v21, v23, v24, v25
    FILTER_3_IN_16BITS_TO_8BITS1 v23, v24, v25, v26 //output to v26[0]
    st1 {v26.8b}, [x2], x5 //write 0:7Byte : 0 line
    UNPACK_FILTER_SINGLE_TAG_16BITS v26, v21, v29, v27, v28, d26
    st1 {v26.b}[0], [x2], x3 //write 8th Byte : 0 line

    //prfm pldl1strm, [x0, x1]
    ld1 {v2.16b}, [x0], x1 // v2=src[4*stride]
    // vertical filtered into v20/v21
    FILTER_6TAG_8BITS_TO_16BITS1 v3, v4, v5, v6, v7, v2, v20, v0, v1
    FILTER_6TAG_8BITS_TO_16BITS2 v3, v4, v5, v6, v7, v2, v21, v0, v1
    // horizon filtered
    UNPACK_2_16BITS_TO_ABC  v20, v21, v23, v24, v25
    FILTER_3_IN_16BITS_TO_8BITS1 v23, v24, v25, v26 //output to v26[0]
    st1 {v26.8b}, [x2], x5 //write 0:7Byte : 1 line
    UNPACK_FILTER_SINGLE_TAG_16BITS v26, v21, v29, v27, v28, d26
    st1 {v26.b}[0], [x2], x3 //write 8th Byte : 1 line

    //prfm pldl1strm, [x0, x1]
    ld1 {v3.16b}, [x0], x1 // v3=src[5*stride]
    // vertical filtered into v20/v21
    FILTER_6TAG_8BITS_TO_16BITS1 v4, v5, v6, v7, v2, v3, v20, v0, v1
    FILTER_6TAG_8BITS_TO_16BITS2 v4, v5, v6, v7, v2, v3, v21, v0, v1
    // horizon filtered
    UNPACK_2_16BITS_TO_ABC  v20, v21, v23, v24, v25
    FILTER_3_IN_16BITS_TO_8BITS1 v23, v24, v25, v26 //output to v26[0]
    st1 {v26.8b}, [x2], x5 //write 0:7Byte : 2 line
    UNPACK_FILTER_SINGLE_TAG_16BITS v26, v21, v29, v27, v28, d26
    st1 {v26.b}[0], [x2], x3 //write 8th Byte : 2 line

    //prfm pldl1strm, [x0, x1]
    ld1 {v4.16b}, [x0], x1 // v4=src[6*stride]
    // vertical filtered into v20/v21
    FILTER_6TAG_8BITS_TO_16BITS1 v5, v6, v7, v2, v3, v4, v20, v0, v1
    FILTER_6TAG_8BITS_TO_16BITS2 v5, v6, v7, v2, v3, v4, v21, v0, v1
    // horizon filtered
    UNPACK_2_16BITS_TO_ABC  v20, v21, v23, v24, v25
    FILTER_3_IN_16BITS_TO_8BITS1 v23, v24, v25, v26 //output to v26[0]
    st1 {v26.8b}, [x2], x5 //write 0:7Byte : 3 line
    UNPACK_FILTER_SINGLE_TAG_16BITS v26, v21, v29, v27, v28, d26
    st1 {v26.b}[0], [x2], x3 //write 8th Byte : 3 line


    mov v5.16b, v3.16b
    mov v3.16b, v7.16b
    mov v30.16b, v2.16b
    mov v2.16b, v6.16b
    mov v6.16b, v4.16b
    mov v4.16b, v30.16b

    sub x4, x4, #4
    cbnz x4, w9_hv_mc_luma_loop

    //prfm pldl1strm, [x0, x1]
    ld1 {v7.16b}, [x0], x1 // v7=src[3*stride]
    // vertical filtered into v20/v21
    FILTER_6TAG_8BITS_TO_16BITS1 v2, v3, v4, v5, v6, v7, v20, v0, v1
    FILTER_6TAG_8BITS_TO_16BITS2 v2, v3, v4, v5, v6, v7, v21, v0, v1
    // horizon filtered
    UNPACK_2_16BITS_TO_ABC  v20, v21, v23, v24, v25
    FILTER_3_IN_16BITS_TO_8BITS1 v23, v24, v25, v26 //output to v26[0]
    st1 {v26.8b}, [x2], x5 //write 0:7Byte : 0 line
    UNPACK_FILTER_SINGLE_TAG_16BITS v26, v21, v29, v27, v28, d26
    st1 {v26.b}[0], [x2], x3 //write 8th Byte : 0 line
WELS_ASM_AARCH64_FUNC_END

//void McHorVer22Width5_AArch64_neon (const uint8_t* pSrc, int32_t iSrcStride, uint8_t* pDst, int32_t iDstStride,
//                                    int32_t iHeight);//width+1&&height+1
WELS_ASM_AARCH64_FUNC_BEGIN McHorVer22Width5_AArch64_neon
    SIGN_EXTENSION x1,w1
    SIGN_EXTENSION x3,w3
    SIGN_EXTENSION x4,w4
    sub x0, x0, #2
    sub x0, x0, x1, lsl #1
    movi v0.8h, #20, lsl #0
    movi v1.8h, #5, lsl #0
    sub x3, x3, #4
    mov x5, #4
    ldr q29, filter_para
    sub x4, x4, #1

    //prfm pldl1strm, [x0]
    //prfm pldl1strm, [x0, x1]
    ld1 {v2.16b}, [x0], x1 // v2=src[-2*stride]
    //prfm pldl1strm, [x0, x1]
    ld1 {v3.16b}, [x0], x1 // v5=src[-1*stride]
    //prfm pldl1strm, [x0, x1]
    ld1 {v4.16b}, [x0], x1 // v8=src[0*stride]
    //prfm pldl1strm, [x0, x1]
    ld1 {v5.16b}, [x0], x1 // v11=src[1*stride]
    //prfm pldl1strm, [x0, x1]
    ld1 {v6.16b}, [x0], x1 // v14=src[2*stride]

w5_hv_mc_luma_loop:
    //prfm pldl1strm, [x0, x1]
    ld1 {v7.16b}, [x0], x1 // v7=src[3*stride]
    // vertical filtered into v20/v21
    FILTER_6TAG_8BITS_TO_16BITS1 v2, v3, v4, v5, v6, v7, v20, v0, v1
    FILTER_6TAG_8BITS_TO_16BITS2 v2, v3, v4, v5, v6, v7, v21, v0, v1
    // horizon filtered
    UNPACK_2_16BITS_TO_ABC  v20, v21, v23, v24, v25
    FILTER_3_IN_16BITS_TO_8BITS1 v23, v24, v25, v26 //output to v26[0]
    st1 {v26.s}[0], [x2], x5 //write 0:3Byte : 0 line
    st1 {v26.b}[4], [x2], x3 //write 5th Byte : 0 line

    //prfm pldl1strm, [x0, x1]
    ld1 {v2.16b}, [x0], x1 // v2=src[4*stride]
    // vertical filtered into v20/v21
    FILTER_6TAG_8BITS_TO_16BITS1 v3, v4, v5, v6, v7, v2, v20, v0, v1
    FILTER_6TAG_8BITS_TO_16BITS2 v3, v4, v5, v6, v7, v2, v21, v0, v1
    // horizon filtered
    UNPACK_2_16BITS_TO_ABC  v20, v21, v23, v24, v25
    FILTER_3_IN_16BITS_TO_8BITS1 v23, v24, v25, v26 //output to v26[0]
    st1 {v26.s}[0], [x2], x5 //write 0:3Byte : 1 line
    st1 {v26.b}[4], [x2], x3 //write 5th Byte : 1 line

    //prfm pldl1strm, [x0, x1]
    ld1 {v3.16b}, [x0], x1 // v3=src[5*stride]
    // vertical filtered into v20/v21
    FILTER_6TAG_8BITS_TO_16BITS1 v4, v5, v6, v7, v2, v3, v20, v0, v1
    FILTER_6TAG_8BITS_TO_16BITS2 v4, v5, v6, v7, v2, v3, v21, v0, v1
    // horizon filtered
    UNPACK_2_16BITS_TO_ABC  v20, v21, v23, v24, v25
    FILTER_3_IN_16BITS_TO_8BITS1 v23, v24, v25, v26 //output to v26[0]
    st1 {v26.s}[0], [x2], x5 //write 0:3Byte : 2 line
    st1 {v26.b}[4], [x2], x3 //write 5th Byte : 2 line

    //prfm pldl1strm, [x0, x1]
    ld1 {v4.16b}, [x0], x1 // v4=src[6*stride]
    // vertical filtered into v20/v21
    FILTER_6TAG_8BITS_TO_16BITS1 v5, v6, v7, v2, v3, v4, v20, v0, v1
    FILTER_6TAG_8BITS_TO_16BITS2 v5, v6, v7, v2, v3, v4, v21, v0, v1
    // horizon filtered
    UNPACK_2_16BITS_TO_ABC  v20, v21, v23, v24, v25
    FILTER_3_IN_16BITS_TO_8BITS1 v23, v24, v25, v26 //output to v26[0]
    st1 {v26.s}[0], [x2], x5 //write 0:3Byte : 3 line
    st1 {v26.b}[4], [x2], x3 //write 5th Byte : 3 line


    mov v5.16b, v3.16b
    mov v3.16b, v7.16b
    mov v30.16b, v2.16b
    mov v2.16b, v6.16b
    mov v6.16b, v4.16b
    mov v4.16b, v30.16b

    sub x4, x4, #4
    cbnz x4, w5_hv_mc_luma_loop

    //prfm pldl1strm, [x0, x1]
    ld1 {v7.16b}, [x0], x1 // v7=src[3*stride]
    // vertical filtered into v20/v21
    FILTER_6TAG_8BITS_TO_16BITS1 v2, v3, v4, v5, v6, v7, v20, v0, v1
    FILTER_6TAG_8BITS_TO_16BITS2 v2, v3, v4, v5, v6, v7, v21, v0, v1
    // horizon filtered
    UNPACK_2_16BITS_TO_ABC  v20, v21, v23, v24, v25
    FILTER_3_IN_16BITS_TO_8BITS1 v23, v24, v25, v26 //output to v26[0]
    st1 {v26.s}[0], [x2], x5 //write 0:3Byte : 0 line
    st1 {v26.b}[4], [x2], x3 //write 5th Byte : 0 line
WELS_ASM_AARCH64_FUNC_END

//void McHorVer02Height17_AArch64_neon (const uint8_t* pSrc, int32_t iSrcStride, uint8_t* pDst, int32_t iDstStride,
//                                      int32_t iHeight);// height+1
WELS_ASM_AARCH64_FUNC_BEGIN McHorVer02Height17_AArch64_neon
    SIGN_EXTENSION x1,w1
    SIGN_EXTENSION x3,w3
    SIGN_EXTENSION x4,w4
    sub x0, x0, x1, lsl #1
    movi v0.8h, #20, lsl #0
    movi v1.8h, #5, lsl #0
    sub x4, x4, #1

    //prfm pldl1strm, [x0]
    //prfm pldl1strm, [x0, x1]
    ld1 {v2.16b}, [x0], x1 // v2=src[-2*stride]
    //prfm pldl1strm, [x0, x1]
    ld1 {v3.16b}, [x0], x1 // v3=src[-1*stride]
    //prfm pldl1strm, [x0, x1]
    ld1 {v4.16b}, [x0], x1 // v4=src[0*stride]
    //prfm pldl1strm, [x0, x1]
    ld1 {v5.16b}, [x0], x1 // v5=src[1*stride]
    //prfm pldl1strm, [x0, x1]
    ld1 {v6.16b}, [x0], x1 // v6=src[2*stride]


w17_v_mc_luma_loop:
    //prfm pldl1strm, [x0, x1]
    ld1 {v7.16b}, [x0], x1 // v7=src[3*stride]
    FILTER_6TAG_8BITS1 v2, v3, v4, v5, v6, v7, v20, v0, v1
    FILTER_6TAG_8BITS2 v2, v3, v4, v5, v6, v7, v20, v0, v1
    st1 {v20.16b}, [x2], x3 //write 16Byte : 0 line


    //prfm pldl1strm, [x0, x1]
    ld1 {v2.16b}, [x0], x1 // v2=src[4*stride]
    FILTER_6TAG_8BITS1 v3, v4, v5, v6, v7, v2, v20, v0, v1
    FILTER_6TAG_8BITS2 v3, v4, v5, v6, v7, v2, v20, v0, v1
    st1 {v20.16b}, [x2], x3 //write 16Byte : 1 line


    //prfm pldl1strm, [x0, x1]
    ld1 {v3.16b}, [x0], x1 // v3=src[5*stride]
    FILTER_6TAG_8BITS1 v4, v5, v6, v7, v2, v3, v20, v0, v1
    FILTER_6TAG_8BITS2 v4, v5, v6, v7, v2, v3, v20, v0, v1
    st1 {v20.16b}, [x2], x3 //write 16Byte : 2 line


    //prfm pldl1strm, [x0, x1]
    ld1 {v4.16b}, [x0], x1 // v4=src[6*stride]
    FILTER_6TAG_8BITS1 v5, v6, v7, v2, v3, v4, v20, v0, v1
    FILTER_6TAG_8BITS2 v5, v6, v7, v2, v3, v4, v20, v0, v1
    st1 {v20.16b}, [x2], x3 //write 16Byte : 3 line


    //prfm pldl1strm, [x0, x1]
    ld1 {v5.16b}, [x0], x1 // v5=src[7*stride]
    FILTER_6TAG_8BITS1 v6, v7, v2, v3, v4, v5, v20, v0, v1
    FILTER_6TAG_8BITS2 v6, v7, v2, v3, v4, v5, v20, v0, v1
    st1 {v20.16b}, [x2], x3 //write 16Byte : 4 line


    //prfm pldl1strm, [x0, x1]
    ld1 {v6.16b}, [x0], x1 // v6=src[8*stride]
    FILTER_6TAG_8BITS1 v7, v2, v3, v4, v5, v6, v20, v0, v1
    FILTER_6TAG_8BITS2 v7, v2, v3, v4, v5, v6, v20, v0, v1
    st1 {v20.16b}, [x2], x3 //write 16Byte : 5 line

    //prfm pldl1strm, [x0, x1]
    ld1 {v7.16b}, [x0], x1 // v7=src[9*stride]
    FILTER_6TAG_8BITS1 v2, v3, v4, v5, v6, v7, v20, v0, v1
    FILTER_6TAG_8BITS2 v2, v3, v4, v5, v6, v7, v20, v0, v1
    st1 {v20.16b}, [x2], x3 //write 16Byte : 6 line

    //prfm pldl1strm, [x0, x1]
    ld1 {v2.16b}, [x0], x1 // v2=src[10*stride]
    FILTER_6TAG_8BITS1 v3, v4, v5, v6, v7, v2, v20, v0, v1
    FILTER_6TAG_8BITS2 v3, v4, v5, v6, v7, v2, v20, v0, v1
    st1 {v20.16b}, [x2], x3 //write 16Byte : 7 line

    mov v3.16b, v5.16b
    mov v5.16b, v7.16b
    mov v7.16b, v2.16b
    mov v2.16b, v4.16b
    mov v4.16b, v6.16b
    mov v6.16b, v7.16b
    sub x4, x4, #8
    cbnz x4, w17_v_mc_luma_loop

    //prfm pldl1strm, [x0, x1]
    ld1 {v7.16b}, [x0], x1 // v7=src[3*stride]
    FILTER_6TAG_8BITS1 v2, v3, v4, v5, v6, v7, v20, v0, v1
    FILTER_6TAG_8BITS2 v2, v3, v4, v5, v6, v7, v20, v0, v1
    st1 {v20.16b}, [x2], x3 //write 16Byte : last line
WELS_ASM_AARCH64_FUNC_END
//void McHorVer02Height9_AArch64_neon (const uint8_t* pSrc, int32_t iSrcStride, uint8_t* pDst, int32_t iDstStride,
//                                     int32_t iHeight);// height+1
WELS_ASM_AARCH64_FUNC_BEGIN McHorVer02Height9_AArch64_neon
    SIGN_EXTENSION x1,w1
    SIGN_EXTENSION x3,w3
    SIGN_EXTENSION x4,w4
    sub x0, x0, x1, lsl #1
    movi v0.8h, #20, lsl #0
    movi v1.8h, #5, lsl #0
    sub x4, x4, #1

    //prfm pldl1strm, [x0]
    //prfm pldl1strm, [x0, x1]
    ld1 {v2.8b}, [x0], x1 // v2=src[-2*stride]
    //prfm pldl1strm, [x0, x1]
    ld1 {v3.8b}, [x0], x1 // v3=src[-1*stride]
    //prfm pldl1strm, [x0, x1]
    ld1 {v4.8b}, [x0], x1 // v4=src[0*stride]
    //prfm pldl1strm, [x0, x1]
    ld1 {v5.8b}, [x0], x1 // v5=src[1*stride]
    //prfm pldl1strm, [x0, x1]
    ld1 {v6.8b}, [x0], x1 // v6=src[2*stride]

w9_v_mc_luma_loop:
    //prfm pldl1strm, [x0, x1]
    ld1 {v7.8b}, [x0], x1 // v7=src[3*stride]
    FILTER_6TAG_8BITS1 v2, v3, v4, v5, v6, v7, v20, v0, v1
    st1 {v20.8b}, [x2], x3 //write 8Byte : 0 line

    //prfm pldl1strm, [x0, x1]
    ld1 {v2.8b}, [x0], x1 // v2=src[4*stride]
    FILTER_6TAG_8BITS1 v3, v4, v5, v6, v7, v2, v20, v0, v1
    st1 {v20.8b}, [x2], x3 //write 8Byte : 1 line

    //prfm pldl1strm, [x0, x1]
    ld1 {v3.8b}, [x0], x1 // v3=src[5*stride]
    FILTER_6TAG_8BITS1 v4, v5, v6, v7, v2, v3, v20, v0, v1
    st1 {v20.8b}, [x2], x3 //write 8Byte : 2 line

    //prfm pldl1strm, [x0, x1]
    ld1 {v4.8b}, [x0], x1 // v4=src[6*stride]
    FILTER_6TAG_8BITS1 v5, v6, v7, v2, v3, v4, v20, v0, v1
    st1 {v20.8b}, [x2], x3 //write 8Byte : 3 line

    mov v5.16b, v3.16b
    mov v3.16b, v7.16b
    mov v7.16b, v2.16b
    mov v2.16b, v6.16b
    mov v6.16b, v4.16b
    mov v4.16b, v7.16b
    sub x4, x4, #4
    cbnz x4, w9_v_mc_luma_loop

    //prfm pldl1strm, [x0, x1]
    ld1 {v7.8b}, [x0], x1 // v7=src[3*stride]
    FILTER_6TAG_8BITS1 v2, v3, v4, v5, v6, v7, v20, v0, v1
    st1 {v20.8b}, [x2], x3 //write 8Byte : 0 line
WELS_ASM_AARCH64_FUNC_END

//void McHorVer02Height5_AArch64_neon (const uint8_t* pSrc, int32_t iSrcStride, uint8_t* pDst, int32_t iDstStride,
//                                     int32_t iHeight);// height+1
WELS_ASM_AARCH64_FUNC_BEGIN McHorVer02Height5_AArch64_neon
    SIGN_EXTENSION x1,w1
    SIGN_EXTENSION x3,w3
    SIGN_EXTENSION x4,w4
    sub x0, x0, x1, lsl #1
    movi v0.8h, #20, lsl #0
    movi v1.8h, #5, lsl #0
    sub x4, x4, #1

    //prfm pldl1strm, [x0]
    //prfm pldl1strm, [x0, x1]
    ld1 {v2.8b}, [x0], x1 // v2=src[-2*stride]
    //prfm pldl1strm, [x0, x1]
    ld1 {v3.8b}, [x0], x1 // v3=src[-1*stride]
    //prfm pldl1strm, [x0, x1]
    ld1 {v4.8b}, [x0], x1 // v4=src[0*stride]
    //prfm pldl1strm, [x0, x1]
    ld1 {v5.8b}, [x0], x1 // v5=src[1*stride]
    //prfm pldl1strm, [x0, x1]
    ld1 {v6.8b}, [x0], x1 // v6=src[2*stride]

w5_v_mc_luma_loop:
    //prfm pldl1strm, [x0, x1]
    ld1 {v7.8b}, [x0], x1 // v7=src[3*stride]
    FILTER_6TAG_8BITS1 v2, v3, v4, v5, v6, v7, v20, v0, v1
    st1 {v20.s}[0], [x2], x3 //write 4Byte : 0 line

    //prfm pldl1strm, [x0, x1]
    ld1 {v2.8b}, [x0], x1 // v2=src[4*stride]
    FILTER_6TAG_8BITS1 v3, v4, v5, v6, v7, v2, v20, v0, v1
    st1 {v20.s}[0], [x2], x3 //write 4Byte : 1 line

    //prfm pldl1strm, [x0, x1]
    ld1 {v3.8b}, [x0], x1 // v3=src[5*stride]
    FILTER_6TAG_8BITS1 v4, v5, v6, v7, v2, v3, v20, v0, v1
    st1 {v20.s}[0], [x2], x3 //write 4Byte : 2 line

    //prfm pldl1strm, [x0, x1]
    ld1 {v4.8b}, [x0], x1 // v4=src[6*stride]
    FILTER_6TAG_8BITS1 v5, v6, v7, v2, v3, v4, v20, v0, v1
    st1 {v20.s}[0], [x2], x3 //write 4Byte : 3 line

    mov v5.16b, v3.16b
    mov v3.16b, v7.16b
    mov v7.16b, v2.16b
    mov v2.16b, v6.16b
    mov v6.16b, v4.16b
    mov v4.16b, v7.16b
    sub x4, x4, #4
    cbnz x4, w5_v_mc_luma_loop

    //prfm pldl1strm, [x0, x1]
    ld1 {v7.8b}, [x0], x1 // v7=src[3*stride]
    FILTER_6TAG_8BITS1 v2, v3, v4, v5, v6, v7, v20, v0, v1
    st1 {v20.s}[0], [x2], x3 //write 4Byte : 0 line

WELS_ASM_AARCH64_FUNC_END

#endif

