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

.macro ZERO_COUNT_IN_2_QUARWORD arg0, arg1, arg2
//  {   //  input:  coef_0 (identy to \arg3\() \arg4\()), coef_1(identy to \arg5\() \arg6\()), mask_q
    cmeq    \arg0\().8h, \arg0\().8h, #0
    cmeq    \arg1\().8h, \arg1\().8h, #0
    uzp1    \arg0\().16b, \arg0\().16b, \arg1\().16b
    ushr    \arg0\().16b, \arg0\().16b, 7
    addv    \arg2\(), \arg0\().16b
//  }
.endm

.macro NEWQUANT_COEF_EACH_16BITS arg0, arg1, arg2, arg3, arg4, arg5
// if coef <= 0, - coef; else , coef;
//  {   //  input:  coef, ff (dst), mf
    eor     \arg3\().16b, \arg3\().16b, \arg3\().16b          // init 0 , and keep 0;
    saba    \arg1\().8h, \arg0\().8h, \arg3\().8h      // f + abs(coef - 0)
    smull   \arg4\().4s, \arg1\().4h, \arg2\().4h
    smull2  \arg5\().4s, \arg1\().8h, \arg2\().8h
    shrn    \arg1\().4h, \arg4\().4s, #16
    shrn2   \arg1\().8h, \arg5\().4s, #16

    cmgt    \arg4\().8h, \arg0\().8h, #0      // if true, location of coef == 11111111
    bif     \arg3\().16b, \arg1\().16b, \arg4\().16b      // if (x<0) reserved part; else keep 0 untouched
    shl     \arg3\().8h, \arg3\().8h, #1
    sub     \arg1\().8h, \arg1\().8h, \arg3\().8h      // if x > 0, -= 0; else x-= 2x
//  }
.endm

.macro NEWQUANT_COEF_EACH_16BITS_MAX arg0, arg1, arg2, arg3, arg4, arg5, arg6
// if coef <= 0, - coef; else , coef;
//  {   //  input:  coef, ff (dst), mf
    eor     \arg3\().16b, \arg3\().16b, \arg3\().16b          // init 0 , and keep 0;
    saba    \arg1\().8h, \arg0\().8h, \arg3\().8h      // f + abs(coef - 0)
    smull   \arg4\().4s, \arg1\().4h, \arg2\().4h
    smull2  \arg5\().4s, \arg1\().8h, \arg2\().8h
    shrn    \arg1\().4h, \arg4\().4s, #16
    shrn2   \arg1\().8h, \arg5\().4s, #16

    cmgt    \arg4\().8h, \arg0\().8h, #0      // if true, location of coef == 11111111
    bif     \arg3\().16b, \arg1\().16b, \arg4\().16b      // if (x<0) reserved part; else keep 0 untouched
    shl     \arg3\().8h, \arg3\().8h, #1
    mov     \arg6\().16b, \arg1\().16b
    sub     \arg1\().8h, \arg1\().8h, \arg3\().8h      // if x > 0, -= 0; else x-= 2x
//  }
.endm

.macro QUANT_DUALWORD_COEF_EACH_16BITS arg0, arg1, arg2, arg3, arg4
// if coef <= 0, - coef; else , coef;
//  {   //  input:  coef, ff (dst), mf
    saba    \arg1\().8h, \arg0\().8h, \arg3\().8h      // f + abs(coef - 0)
    smull   \arg4\().4s, \arg1\().4h, \arg2\().4h
    shrn    \arg1\().4h, \arg4\().4s, #16

    cmgt    \arg4\().8h, \arg0\().8h, #0      // if true, location of coef == 11111111
    bif     \arg3\().16b, \arg1\().16b, \arg4\().16b      // if (x<0) reserved part; else keep 0 untouched
    shl     \arg3\().8h, \arg3\().8h, #1
    sub     \arg1\().8h, \arg1\().8h, \arg3\().8h      // if x > 0, -= 0; else x-= 2x
//  }
.endm

.macro SELECT_MAX_IN_ABS_COEF arg0, arg1, arg2, arg3, arg4, arg5
//  {   //  input:  coef_0, coef_1, coef_2, coef_3, max_q (identy to follow two)
    umax    \arg0\().8h, \arg0\().8h, \arg1\().8h
    umaxv   \arg4\(), \arg0\().8h
    umax    \arg2\().8h, \arg2\().8h, \arg3\().8h
    umaxv   \arg5\(), \arg2\().8h
//  }
.endm

.macro HDM_QUANT_2x2_TOTAL_16BITS arg0, arg1, arg2
//  {   //  input: src_d[0][16][32][48], dst_d[0][16][32][48], working
    sshr  \arg1\().2d, \arg0\().2d, #32
    add   \arg2\().4h, \arg0\().4h, \arg1\().4h      // [0] = rs[0] + rs[32];[1] = rs[16] + rs[48];
    sub   \arg1\().4h, \arg0\().4h, \arg1\().4h      // [0] = rs[0] - rs[32];[1] = rs[16] - rs[48];
    zip1  \arg1\().4h, \arg2\().4h, \arg1\().4h
//  }
.endm


.macro DC_ZERO_COUNT_IN_DUALWORD arg0, arg1, arg2
//  {   //  input:  coef, dst_d, working_d (all 0x01)
    cmeq    \arg0\().4h, \arg0\().4h, #0
    and     \arg0\().8b, \arg0\().8b, \arg2\().8b
    addv    \arg1\(), \arg0\().4h
//  }
.endm

.macro IHDM_4x4_TOTAL_16BITS arg0, arg1, arg2
//  {   //  input: each src_d[0]~[3](dst), working_q0, working_q1
    uzp2  \arg1\().4s, \arg0\().4s, \arg0\().4s
    uzp1  \arg0\().4s, \arg0\().4s, \arg0\().4s
    add   \arg2\().8h, \arg0\().8h, \arg1\().8h      // [0] = rs[0] + rs[2];[1] = rs[1] + rs[3];[2] = rs[4] + rs[6];[3] = rs[5] + rs[7];
    sub   \arg1\().8h, \arg0\().8h, \arg1\().8h      // [0] = rs[0] - rs[2];[1] = rs[1] - rs[3];[2] = rs[4] - rs[6];[3] = rs[5] - rs[7];
    zip1  \arg2\().8h, \arg2\().8h, \arg1\().8h      // [0] = rs[0] + rs[2]; [1] = rs[0] - rs[2]; ... [2]; [3]

    uzp2  \arg1\().4s, \arg2\().4s, \arg2\().4s
    uzp1  \arg0\().4s, \arg2\().4s, \arg2\().4s
    add   \arg2\().8h, \arg0\().8h, \arg1\().8h      // [0] = rs[0] + rs[2];[1] = rs[1] + rs[3];[2] = rs[4] + rs[6];[3] = rs[5] + rs[7];
    sub   \arg1\().8h, \arg0\().8h, \arg1\().8h      // [0] = rs[0] - rs[2];[1] = rs[1] - rs[3];[2] = rs[4] - rs[6];[3] = rs[5] - rs[7];
    rev32 \arg1\().4h, \arg1\().4h             // [0] = rs[1] - rs[3];[1] = rs[0] - rs[2];[2] = rs[5] - rs[7];[3] = rs[4] - rs[6];
    zip1  \arg0\().4s, \arg2\().4s, \arg1\().4s
    //  }
.endm

.macro MATRIX_TRANSFORM_EACH_16BITS_2x8_OUT2 arg0, arg1, arg2, arg3
//  {   //  input & output: src_d[0]~[3];[0 1 2 3]+[4 5 6 7]+[8 9 10 11]+[12 13 14 15]
    uzp1 \arg2\().4s, \arg0\().4s, \arg1\().4s   //[0 1 4 5]+[8 9 12 13]
    uzp2 \arg3\().4s, \arg0\().4s, \arg1\().4s   //[2 3 6 7]+[10 11 14 15]

    uzp1 \arg0\().8h, \arg2\().8h, \arg3\().8h   //[0 4 8 12]+[2 6 10 14]
    uzp2 \arg2\().8h, \arg2\().8h, \arg3\().8h   //[1 5 9 13]+[3 7 11 15]
    zip2 \arg1\().2d, \arg0\().2d, \arg2\().2d   //[2 6 10 14]+[3 7 11 15]
    zip1 \arg0\().2d, \arg0\().2d, \arg2\().2d   //[0 4 8 12]+[1 5 9 13]
//  }
.endm

.macro MATRIX_TRANSFORM_EACH_16BITS_OUT4 arg0, arg1, arg2, arg3, arg4, arg5, arg6, arg7
//  {   //  input & output: src_d[0]~[3];[0 4 8 12],[1 5 9 13],[2 6 10 14],[3 7 11 15]
    trn1 \arg4\().8h, v0.8h, v1.8h
    trn2 \arg5\().8h, v0.8h, v1.8h
    trn1 \arg6\().8h, v2.8h, v3.8h
    trn2 \arg7\().8h, v2.8h, v3.8h

    trn1 \arg0\().4s, v4.4s, v6.4s
    trn2 \arg2\().4s, v4.4s, v6.4s
    trn1 \arg1\().4s, v5.4s, v7.4s
    trn2 \arg3\().4s, v5.4s, v7.4s
//  }
.endm

.macro MATRIX_TRANSFORM_EACH_16BITS_4x4_OUT2 arg0, arg1, arg2, arg3
//  {   //  input & output: src_d[0]~[3];[0 1 2 3],[4 5 6 7],[8 9 10 11],[12 13 14 15]
    mov  \arg0\().d[1], \arg1\().d[0]  //[0 1 2 3]+[4 5 6 7]
    mov  \arg2\().d[1], \arg3\().d[0]  //[8 9 10 11]+[12 13 14 15]
    uzp1 \arg1\().4s, \arg0\().4s, \arg2\().4s   //[0 1 4 5]+[8 9 12 13]
    uzp2 \arg3\().4s, \arg0\().4s, \arg2\().4s   //[2 3 6 7]+[10 11 14 15]

    uzp1 \arg0\().8h, \arg1\().8h, \arg3\().8h   //[0 4 8 12]+[2 6 10 14]
    uzp2 \arg2\().8h, \arg1\().8h, \arg3\().8h   //[1 5 9 13]+[3 7 11 15]
    zip2 \arg1\().2d, \arg0\().2d, \arg2\().2d   //[2 6 10 14]+[3 7 11 15]
    zip1 \arg0\().2d, \arg0\().2d, \arg2\().2d   //[0 4 8 12]+[1 5 9 13]
//  }
.endm

.macro LOAD_4x4_DATA_FOR_DCT arg0, arg1, arg2, arg3, arg4, arg5
    ld1   {\arg0\().s}[0], [\arg2\()], \arg3\()
    ld1   {\arg0\().s}[1], [\arg2\()], \arg3\()
    ld1   {\arg0\().s}[2], [\arg2\()], \arg3\()
    ld1   {\arg0\().s}[3], [\arg2\()]

    ld1   {\arg1\().s}[0], [\arg4\()], \arg5\()
    ld1   {\arg1\().s}[1], [\arg4\()], \arg5\()
    ld1   {\arg1\().s}[2], [\arg4\()], \arg5\()
    ld1   {\arg1\().s}[3], [\arg4\()]
.endm

.macro DCT_ROW_TRANSFORM_TOTAL_16BITS arg0, arg1, arg2, arg3, arg4, arg5, arg6, arg7
//  {   //  input: src_d[0]~[3], working: [4]~[7]
    add     \arg4\().8h, \arg0\().8h, \arg3\().8h   //int16 s[0] = data[i] + data[i3];
    sub     \arg7\().8h, \arg0\().8h, \arg3\().8h   //int16 s[3] = data[i] - data[i3];
    add     \arg5\().8h, \arg1\().8h, \arg2\().8h   //int16 s[1] = data[i1] + data[i2];
    sub     \arg6\().8h, \arg1\().8h, \arg2\().8h   //int16 s[2] = data[i1] - data[i2];

    add     \arg0\().8h, \arg4\().8h, \arg5\().8h   //int16 dct[i ] = s[0] + s[1];
    sub     \arg2\().8h, \arg4\().8h, \arg5\().8h   //int16 dct[i2] = s[0] - s[1];
    shl     \arg1\().8h, \arg7\().8h, #1
    shl     \arg3\().8h, \arg6\().8h, #1
    add     \arg1\().8h, \arg1\().8h, \arg6\().8h   //int16 dct[i1] = (s[3] << 1) + s[2];
    sub     \arg3\().8h, \arg7\().8h, \arg3\().8h   //int16 dct[i3] = s[3] - (s[2] << 1);
//  }
.endm

.macro LOAD_8x4_DATA_FOR_DCT arg0, arg1, arg2, arg3, arg4, arg5, arg6, arg7, arg8, arg9
//  {   //  input: \arg0\()~\arg3\(), src1*, src2*; untouched r2:src1_stride &r4:src2_stride
    ld1   {\arg0\().d}[0], [\arg8\()], x2
    ld1   {\arg1\().d}[0], [\arg8\()], x2
    ld1   {\arg2\().d}[0], [\arg8\()], x2
    ld1   {\arg3\().d}[0], [\arg8\()], x2

    ld1   {\arg4\().d}[0], [\arg9\()], x4
    ld1   {\arg5\().d}[0], [\arg9\()], x4
    ld1   {\arg6\().d}[0], [\arg9\()], x4
    ld1   {\arg7\().d}[0], [\arg9\()], x4
//  }
.endm

.macro ROW_TRANSFORM_1_STEP_TOTAL_16BITS arg0, arg1, arg2, arg3, arg4, arg5, arg6, arg7
//  {   //  input: src_d[0]~[3], output: e_d[0]~[3];
    add   \arg4\().8h, \arg0\().8h, \arg2\().8h          //int16 e[i][0] = src[0] + src[2];
    sub   \arg5\().8h, \arg0\().8h, \arg2\().8h          //int16 e[i][1] = src[0] - src[2];
    sshr  \arg6\().8h, \arg1\().8h, #1
    sshr  \arg7\().8h, \arg3\().8h, #1
    sub   \arg6\().8h, \arg6\().8h, \arg3\().8h          //int16 e[i][2] = (src[1]>>1)-src[3];
    add   \arg7\().8h, \arg1\().8h, \arg7\().8h          //int16 e[i][3] = src[1] + (src[3]>>1);
//  }
.endm

.macro TRANSFORM_TOTAL_16BITS arg0, arg1, arg2, arg3, arg4, arg5, arg6, arg7
// both row & col transform used
//  {   //  output: f_q[0]~[3], input: e_q[0]~[3];
    add   \arg0\().8h, \arg4\().8h, \arg7\().8h          //int16 f[i][0] = e[i][0] + e[i][3];
    add   \arg1\().8h, \arg5\().8h, \arg6\().8h          //int16 f[i][1] = e[i][1] + e[i][2];
    sub   \arg2\().8h, \arg5\().8h, \arg6\().8h          //int16 f[i][2] = e[i][1] - e[i][2];
    sub   \arg3\().8h, \arg4\().8h, \arg7\().8h          //int16 f[i][3] = e[i][0] - e[i][3];
//  }
.endm

.macro ROW_TRANSFORM_0_STEP arg0, arg1, arg2, arg3, arg4, arg5, arg6, arg7
//  {   //  input: src_d[0]~[3], output: e_q[0]~[3];
    saddl   \arg4\().4s, \arg0\().4h, \arg2\().4h          //int32 e[i][0] = src[0] + src[2];
    ssubl   \arg5\().4s, \arg0\().4h, \arg2\().4h          //int32 e[i][1] = src[0] - src[2];
    ssubl   \arg6\().4s, \arg1\().4h, \arg3\().4h          //int32 e[i][2] = src[1] - src[3];
    saddl   \arg7\().4s, \arg1\().4h, \arg3\().4h          //int32 e[i][3] = src[1] + src[3];
//  }
.endm

.macro COL_TRANSFORM_0_STEP arg0, arg1, arg2, arg3, arg4, arg5, arg6, arg7
//  {   //  input: src_q[0]~[3], output: e_q[0]~[3];
    add     \arg4\().4s, \arg0\().4s, \arg2\().4s          //int32 e[0][j] = f[0][j] + f[2][j];
    sub     \arg5\().4s, \arg0\().4s, \arg2\().4s          //int32 e[1][j] = f[0][j] - f[2][j];
    sub     \arg6\().4s, \arg1\().4s, \arg3\().4s          //int32 e[2][j] = (f[1][j]>>1) - f[3][j];
    add     \arg7\().4s, \arg1\().4s, \arg3\().4s          //int32 e[3][j] = f[1][j] + (f[3][j]>>1);
//  }
.endm

.macro TRANSFORM_4BYTES arg0, arg1, arg2, arg3, arg4, arg5, arg6, arg7
// both row & col transform used
//  {   //  output: f_q[0]~[3], input: e_q[0]~[3];
    add     \arg0\().4s, \arg4\().4s, \arg7\().4s          //int16 f[i][0] = e[i][0] + e[i][3];
    add     \arg1\().4s, \arg5\().4s, \arg6\().4s          //int16 f[i][1] = e[i][1] + e[i][2];
    sub     \arg2\().4s, \arg5\().4s, \arg6\().4s          //int16 f[i][2] = e[i][1] - e[i][2];
    sub     \arg3\().4s, \arg4\().4s, \arg7\().4s          //int16 f[i][3] = e[i][0] - e[i][3];
//  }
.endm

.macro MB_PRED_8BITS_ADD_DCT_16BITS_CLIP arg0, arg1, arg2, arg3, arg4
//  {   //  input: pred_d[0](output), dct_q0/1, working_q0/1;
    uxtl      \arg3\().8h, \arg0\().8b
    uxtl2     \arg4\().8h, \arg0\().16b
    add       \arg3\().8h, \arg3\().8h, \arg1\().8h
    add       \arg4\().8h, \arg4\().8h, \arg2\().8h
    sqxtun    \arg0\().8b, \arg3\().8h
    sqxtun2   \arg0\().16b,\arg4\().8h
//  }
.endm

WELS_ASM_AARCH64_FUNC_BEGIN WelsGetNoneZeroCount_AArch64_neon
    ld1     {v0.8h, v1.8h}, [x0]
    ZERO_COUNT_IN_2_QUARWORD    v0, v1, b0
    mov     x0, v0.d[0]
    mov     x1, #16
    subs    x0, x1, x0
WELS_ASM_AARCH64_FUNC_END

WELS_ASM_AARCH64_FUNC_BEGIN WelsQuant4x4_AArch64_neon
    ld1     {v2.8h}, [x1]
    ld1     {v0.8h, v1.8h}, [x0]
    ld1     {v3.8h}, [x2]
    mov     v4.16b, v2.16b
    NEWQUANT_COEF_EACH_16BITS   v0, v2, v3, v5, v6, v7
    st1     {v2.8h}, [x0], #16
    NEWQUANT_COEF_EACH_16BITS   v1, v4, v3, v5, v6, v7
    st1     {v4.8h}, [x0], #16
WELS_ASM_AARCH64_FUNC_END


WELS_ASM_AARCH64_FUNC_BEGIN WelsQuant4x4Dc_AArch64_neon
    ld1     {v0.8h, v1.8h}, [x0]
    dup     v2.8h, w1      // even ff range [0, 768]
    dup     v3.8h, w2
    mov     v4.16b, v2.16b
    NEWQUANT_COEF_EACH_16BITS   v0, v2, v3, v5, v6, v7
    st1     {v2.8h}, [x0], #16
    NEWQUANT_COEF_EACH_16BITS   v1, v4, v3, v5, v6, v7
    st1     {v4.8h}, [x0], #16
WELS_ASM_AARCH64_FUNC_END

WELS_ASM_AARCH64_FUNC_BEGIN WelsQuantFour4x4_AArch64_neon
    ld1     {v2.8h}, [x1]
    ld1     {v3.8h}, [x2]
    mov     x1, x0

.rept 4
    ld1     {v0.8h, v1.8h}, [x0], #32
    mov     v4.16b, v2.16b
    NEWQUANT_COEF_EACH_16BITS   v0, v4, v3, v5, v6, v7
    st1     {v4.8h}, [x1], #16
    mov     v4.16b, v2.16b
    NEWQUANT_COEF_EACH_16BITS   v1, v4, v3, v5, v6, v7
    st1     {v4.8h}, [x1], #16
.endr
WELS_ASM_AARCH64_FUNC_END


WELS_ASM_AARCH64_FUNC_BEGIN WelsQuantFour4x4Max_AArch64_neon
    ld1     {v2.8h}, [x1]
    ld1     {v3.8h}, [x2]
    mov     x1, x0

    ld1     {v0.8h, v1.8h}, [x0], #32
    mov     v4.16b, v2.16b
    NEWQUANT_COEF_EACH_16BITS_MAX   v0, v4, v3, v5, v6, v7, v16
    st1     {v4.8h}, [x1], #16
    mov     v4.16b, v2.16b
    NEWQUANT_COEF_EACH_16BITS_MAX   v1, v4, v3, v5, v6, v7, v17
    st1     {v4.8h}, [x1], #16   // then 1st 16 elem in v16  & v17

    ld1     {v0.8h, v1.8h}, [x0], #32
    mov     v4.16b, v2.16b
    NEWQUANT_COEF_EACH_16BITS_MAX   v0, v4, v3, v5, v6, v7, v18
    st1     {v4.8h}, [x1], #16
    mov     v4.16b, v2.16b
    NEWQUANT_COEF_EACH_16BITS_MAX   v1, v4, v3, v5, v6, v7, v19
    st1     {v4.8h}, [x1], #16   // then 2st 16 elem in v18 & v19

    SELECT_MAX_IN_ABS_COEF  v16, v17, v18, v19, h20, h21

    ld1     {v0.8h, v1.8h}, [x0], #32
    mov     v4.16b, v2.16b
    NEWQUANT_COEF_EACH_16BITS_MAX   v0, v4, v3, v5, v6, v7, v16
    st1     {v4.8h}, [x1], #16
    mov     v4.16b, v2.16b
    NEWQUANT_COEF_EACH_16BITS_MAX   v1, v4, v3, v5, v6, v7, v17
    st1     {v4.8h}, [x1], #16   // then 1st 16 elem in v16  & v17

    ld1     {v0.8h, v1.8h}, [x0], #32
    mov     v4.16b, v2.16b
    NEWQUANT_COEF_EACH_16BITS_MAX   v0, v4, v3, v5, v6, v7, v18
    st1     {v4.8h}, [x1], #16
    mov     v4.16b, v2.16b
    NEWQUANT_COEF_EACH_16BITS_MAX   v1, v4, v3, v5, v6, v7, v19
    st1     {v4.8h}, [x1], #16   // then 2st 16 elem in v18 & v19

    SELECT_MAX_IN_ABS_COEF  v16, v17, v18, v19, h22, h23

    st4 {v20.h,v21.h,v22.h,v23.h}[0], [x3]
WELS_ASM_AARCH64_FUNC_END


WELS_ASM_AARCH64_FUNC_BEGIN WelsDequant4x4_AArch64_neon
    ld1    {v0.8h, v1.8h}, [x0]
    ld1    {v2.8h}, [x1]
    mul    v3.8h, v0.8h, v2.8h
    mul    v4.8h, v1.8h, v2.8h
    st1    {v3.8h, v4.8h}, [x0]
WELS_ASM_AARCH64_FUNC_END

WELS_ASM_AARCH64_FUNC_BEGIN WelsDequantFour4x4_AArch64_neon
    ld1    {v2.8h}, [x1]
    mov    x1, x0
.rept 4
    ld1   {v0.8h,v1.8h}, [x0], #32
    mul   v3.8h, v0.8h, v2.8h
    mul   v4.8h, v1.8h, v2.8h
    st1   {v3.8h,v4.8h}, [x1], #32
.endr
WELS_ASM_AARCH64_FUNC_END

WELS_ASM_AARCH64_FUNC_BEGIN WelsHadamardQuant2x2SkipKernel_AArch64_neon
    dup   v4.8h, w1
    mov   x1, #32
    ld1   {v0.h}[0], [x0], x1       //rs[0]
    ld1   {v0.h}[1], [x0], x1       //rs[16]
    ld1   {v0.h}[2], [x0], x1       //rs[32]
    ld1   {v0.h}[3], [x0], x1       //rs[48]

    HDM_QUANT_2x2_TOTAL_16BITS  v0, v1, v2      // output v1

    HDM_QUANT_2x2_TOTAL_16BITS  v1, v0, v2      // output v0

    abs   v1.4h, v0.4h
    cmhi  v0.4h, v1.4h, v4.4h         // abs(dct[i])>threshold;
    mov   w0, v0.s[0]
    mov   w1, v0.s[1]
    orr   w0, w0, w1
WELS_ASM_AARCH64_FUNC_END


WELS_ASM_AARCH64_FUNC_BEGIN WelsHadamardQuant2x2_AArch64_neon

    dup   v1.8h, w1 //ff
    dup   v2.8h, w2 //mf
    eor   v3.16b, v3.16b, v3.16b

    mov   x1, #32
    mov   x2, x0
    ld1   {v0.h}[0], [x0], x1       //rs[0]
    st1   {v3.h}[0], [x2], x1      //rs[00]=0
    ld1   {v0.h}[1], [x0], x1       //rs[16]
    st1   {v3.h}[1], [x2], x1      //rs[16]=0
    ld1   {v0.h}[2], [x0], x1       //rs[32]
    st1   {v3.h}[2], [x2], x1      //rs[32]=0
    ld1   {v0.h}[3], [x0], x1       //rs[48]
    st1   {v3.h}[3], [x2], x1      //rs[48]=0


    HDM_QUANT_2x2_TOTAL_16BITS  v0, v4, v5      // output v4

    HDM_QUANT_2x2_TOTAL_16BITS  v4, v0, v5      // output v0

    QUANT_DUALWORD_COEF_EACH_16BITS v0, v1, v2, v3, v4

    st1    {v1.d}[0], [x3]        // store to dct
    st1    {v1.d}[0], [x4]        // store to block

    movi v3.8h, #1, lsl #0

    movi v0.16b, #255

    DC_ZERO_COUNT_IN_DUALWORD   v1, h0, v3

    mov     x0, v0.d[0]
    mov     x1, #4
    subs    x0, x1, x0
WELS_ASM_AARCH64_FUNC_END



WELS_ASM_AARCH64_FUNC_BEGIN WelsDequantIHadamard4x4_AArch64_neon
    ld1    {v0.8h, v1.8h}, [x0]
    dup    v4.8h, w1

    IHDM_4x4_TOTAL_16BITS   v0, v2, v3
    IHDM_4x4_TOTAL_16BITS   v1, v2, v3

    MATRIX_TRANSFORM_EACH_16BITS_2x8_OUT2    v0, v1, v2, v3

    IHDM_4x4_TOTAL_16BITS   v0, v2, v3
    mul   v0.8h, v0.8h, v4.8h

    IHDM_4x4_TOTAL_16BITS   v1, v2, v3
    mul   v1.8h, v1.8h, v4.8h

    MATRIX_TRANSFORM_EACH_16BITS_2x8_OUT2    v0, v1, v2, v3
    st1    {v0.16b, v1.16b}, [x0]
WELS_ASM_AARCH64_FUNC_END

//void WelsDctT4_AArch64_neon (int16_t* pDct,  uint8_t* pPixel1, int32_t iStride1, uint8_t* pPixel2, int32_t iStride2);
WELS_ASM_AARCH64_FUNC_BEGIN WelsDctT4_AArch64_neon
    SIGN_EXTENSION x2, w2
    SIGN_EXTENSION x4, w4
    LOAD_4x4_DATA_FOR_DCT   v0, v1, x1, x2, x3, x4
    usubl  v2.8h, v0.8b, v1.8b
    usubl2 v4.8h, v0.16b, v1.16b
    uzp1  v3.8h, v2.8h, v4.8h
    uzp2  v5.8h, v2.8h, v4.8h
    uzp2  v2.8h, v3.8h, v5.8h // s[2, 6, 10, 14] [3, 7, 11, 15]
    uzp1  v0.8h, v3.8h, v5.8h // s[0, 4, 8, 12] [1, 5, 9, 13]
    mov    v3.d[0], v2.d[1]   // s[3, 7, 11, 15]
    mov    v1.d[0], v0.d[1]   // s[1, 5, 9, 13]

    // horizontal transform
    DCT_ROW_TRANSFORM_TOTAL_16BITS          v0, v1, v2, v3, v4, v5, v6, v7
    // transform element
    MATRIX_TRANSFORM_EACH_16BITS_OUT4   v0, v1, v2, v3, v4, v5, v6, v7
    // vertical transform
    DCT_ROW_TRANSFORM_TOTAL_16BITS          v0, v1, v2, v3, v4, v5, v6, v7

    st4       {v0.d, v1.d, v2.d, v3.d}[0], [x0]
WELS_ASM_AARCH64_FUNC_END

//void WelsDctFourT4_AArch64_neon (int16_t* pDct,  uint8_t* pPixel1, int32_t iStride1, uint8_t* pPixel2, int32_t iStride2);
WELS_ASM_AARCH64_FUNC_BEGIN WelsDctFourT4_AArch64_neon
    SIGN_EXTENSION x2,w2
    SIGN_EXTENSION x4,w4
.rept 2
    LOAD_8x4_DATA_FOR_DCT   v0, v1, v2, v3, v4, v5, v6, v7, x1, x3
    usubl    v0.8h, v0.8b, v4.8b
    usubl    v1.8h, v1.8b, v5.8b
    usubl    v2.8h, v2.8b, v6.8b
    usubl    v3.8h, v3.8b, v7.8b

    MATRIX_TRANSFORM_EACH_16BITS_OUT4   v0, v1, v2, v3, v4, v5, v6, v7

    // horizontal transform
    DCT_ROW_TRANSFORM_TOTAL_16BITS      v0, v1, v2, v3, v4, v5, v6, v7

    // transform element
    MATRIX_TRANSFORM_EACH_16BITS_OUT4   v0, v1, v2, v3, v4, v5, v6, v7

    //  vertical transform
    DCT_ROW_TRANSFORM_TOTAL_16BITS      v0, v1, v2, v3, v4, v5, v6, v7

    uzp1    v4.2d, v0.2d, v1.2d
    uzp2    v6.2d, v0.2d, v1.2d
    uzp1    v5.2d, v2.2d, v3.2d
    uzp2    v7.2d, v2.2d, v3.2d
    st1     {v4.16b, v5.16b}, [x0], #32
    st1     {v6.16b, v7.16b}, [x0], #32
.endr
WELS_ASM_AARCH64_FUNC_END
//void WelsIDctT4Rec_AArch64_neon (uint8_t* pRec, int32_t iStride, uint8_t* pPrediction, int32_t iPredStride, int16_t* pDct)
WELS_ASM_AARCH64_FUNC_BEGIN WelsIDctT4Rec_AArch64_neon
    SIGN_EXTENSION x1,w1
    SIGN_EXTENSION x3,w3
    ld1     {v16.s}[0], [x2], x3
    ld1     {v16.s}[1], [x2], x3
    ld1     {v16.s}[2], [x2], x3
    ld1     {v16.s}[3], [x2], x3                   // Pred
    ld4     {v0.4h, v1.4h, v2.4h, v3.4h}, [x4]      // dct coeff

    ROW_TRANSFORM_1_STEP_TOTAL_16BITS   v0, v1, v2, v3, v4, v5, v6, v7

    TRANSFORM_TOTAL_16BITS              v0, v1, v2, v3, v4, v5, v6, v7

    MATRIX_TRANSFORM_EACH_16BITS_OUT4   v0, v1, v2, v3, v4, v5, v6, v7

    ROW_TRANSFORM_1_STEP_TOTAL_16BITS   v0, v1, v2, v3, v4, v5, v6, v7

    TRANSFORM_TOTAL_16BITS              v0, v1, v2, v3, v4, v5, v6, v7
    ins     v0.d[1], v1.d[0]
    ins     v2.d[1], v3.d[0]
    srshr   v0.8h, v0.8h, #6
    srshr   v2.8h, v2.8h, #6
    //after rounding 6, clip into [0, 255]
    uxtl    v1.8h, v16.8b
    add     v0.8h, v0.8h, v1.8h
    sqxtun  v1.8b, v0.8h
    st1     {v1.s}[0],[x0],x1
    st1     {v1.s}[1],[x0],x1

    uxtl2   v1.8h, v16.16b
    add     v2.8h, v2.8h, v1.8h
    sqxtun  v1.8b, v2.8h
    st1     {v1.s}[0],[x0],x1
    st1     {v1.s}[1],[x0],x1
WELS_ASM_AARCH64_FUNC_END
//void WelsIDctFourT4Rec_AArch64_neon (uint8_t* pRec, int32_t iStride, uint8_t* pPrediction, int32_t iPredStride, int16_t* pDct);
WELS_ASM_AARCH64_FUNC_BEGIN WelsIDctFourT4Rec_AArch64_neon
    SIGN_EXTENSION x1,w1
    SIGN_EXTENSION x3,w3
.rept 2
    ld1     {v16.d}[0], [x2], x3
    ld1     {v16.d}[1], [x2], x3
    ld1     {v17.d}[0], [x2], x3
    ld1     {v17.d}[1], [x2], x3                   // Pred
    ld4     {v0.8h, v1.8h, v2.8h, v3.8h}, [x4], #64     // dct coeff

    ROW_TRANSFORM_1_STEP_TOTAL_16BITS   v0, v1, v2, v3, v4, v5, v6, v7

    TRANSFORM_TOTAL_16BITS    v0, v1, v2, v3, v4, v5, v6, v7

    MATRIX_TRANSFORM_EACH_16BITS_OUT4    v0, v1, v2, v3, v4, v5, v6, v7

    ROW_TRANSFORM_1_STEP_TOTAL_16BITS   v0, v1, v2, v3, v4, v5, v6, v7

    TRANSFORM_TOTAL_16BITS    v0, v1, v2, v3, v4, v5, v6, v7
    srshr   v0.8h, v0.8h, #6
    srshr   v1.8h, v1.8h, #6
    srshr   v2.8h, v2.8h, #6
    srshr   v3.8h, v3.8h, #6

    //after rounding 6, clip into [0, 255]
    uxtl    v4.8h, v16.8b
    add     v0.8h, v0.8h, v4.8h
    sqxtun  v0.8b, v0.8h
    st1     {v0.d}[0],[x0],x1

    uxtl2   v5.8h, v16.16b
    add     v1.8h, v1.8h, v5.8h
    sqxtun  v1.8b, v1.8h
    st1     {v1.d}[0],[x0],x1

    uxtl    v6.8h, v17.8b
    add     v2.8h, v2.8h, v6.8h
    sqxtun  v2.8b, v2.8h
    st1     {v2.d}[0],[x0],x1

    uxtl2   v7.8h, v17.16b
    add     v3.8h, v3.8h, v7.8h
    sqxtun  v3.8b, v3.8h
    st1     {v3.d}[0],[x0],x1
 .endr
WELS_ASM_AARCH64_FUNC_END

WELS_ASM_AARCH64_FUNC_BEGIN WelsHadamardT4Dc_AArch64_neon

    mov     x2, #32
    ld1     {v0.h}[0], [x1], x2
    ld1     {v1.h}[0], [x1], x2
    ld1     {v0.h}[1], [x1], x2
    ld1     {v1.h}[1], [x1], x2

    ld1     {v2.h}[0], [x1], x2
    ld1     {v3.h}[0], [x1], x2
    ld1     {v2.h}[1], [x1], x2
    ld1     {v3.h}[1], [x1], x2

    ld1     {v0.h}[2], [x1], x2
    ld1     {v1.h}[2], [x1], x2
    ld1     {v0.h}[3], [x1], x2
    ld1     {v1.h}[3], [x1], x2

    ld1     {v2.h}[2], [x1], x2
    ld1     {v3.h}[2], [x1], x2
    ld1     {v2.h}[3], [x1], x2
    ld1     {v3.h}[3], [x1], x2 // v0[0 4 08 12],v1[1 5 09 13],v2[2 6 10 14],v3[3 7 11 15]

    ROW_TRANSFORM_0_STEP    v0, v1, v3, v2, v4, v7, v6, v5
    TRANSFORM_4BYTES        v0, v1, v3, v2, v4, v7, v6, v5

    // transform element 32bits
    uzp1    v4.4s, v0.4s, v1.4s // 0 2 4 6
    uzp2    v5.4s, v0.4s, v1.4s // 1 3 5 7
    uzp1    v6.4s, v2.4s, v3.4s // 8 10 12 14
    uzp2    v7.4s, v2.4s, v3.4s // 9 11 13 15

    uzp1    v0.4s, v4.4s, v6.4s // 0 4  8 12
    uzp2    v2.4s, v4.4s, v6.4s // 2 6 10 14
    uzp1    v1.4s, v5.4s, v7.4s // 1 5  9 13
    uzp2    v3.4s, v5.4s, v7.4s // 3 7 11 15

    COL_TRANSFORM_0_STEP    v0, v1, v3, v2, v4, v7, v6, v5
    TRANSFORM_4BYTES        v0, v1, v3, v2, v4, v7, v6, v5
    sqrshrn   v4.4h, v0.4s, #1
    sqrshrn2  v4.8h, v1.4s, #1
    sqrshrn   v5.4h, v2.4s, #1
    sqrshrn2  v5.8h, v3.4s, #1
    st1       {v4.16b, v5.16b}, [x0]  //store
WELS_ASM_AARCH64_FUNC_END

//void WelsIDctRecI16x16Dc_AArch64_neon (uint8_t* pRec, int32_t iStride, uint8_t* pPrediction, int32_t iPredStride,
//                int16_t* pDctDc);
WELS_ASM_AARCH64_FUNC_BEGIN WelsIDctRecI16x16Dc_AArch64_neon
    SIGN_EXTENSION x1,w1
    SIGN_EXTENSION x3,w3
    ld1       {v16.16b,v17.16b}, [x4]
    srshr     v16.8h, v16.8h, #6
    srshr     v17.8h, v17.8h, #6

    dup       v0.8h, v16.h[0]
    dup       v1.8h, v16.h[1]
    ins       v0.d[1], v1.d[0]
    dup       v1.8h, v16.h[2]
    dup       v2.8h, v16.h[3]
    ins       v1.d[1], v2.d[0]

.rept 4
    ld1       {v3.16b}, [x2], x3
    MB_PRED_8BITS_ADD_DCT_16BITS_CLIP   v3, v0, v1, v4, v5
    st1       {v3.16b}, [x0], x1
.endr

    dup       v0.8h, v16.h[4]
    dup       v1.8h, v16.h[5]
    ins       v0.d[1], v1.d[0]
    dup       v1.8h, v16.h[6]
    dup       v2.8h, v16.h[7]
    ins       v1.d[1], v2.d[0]

.rept 4
    ld1       {v3.16b}, [x2], x3
    MB_PRED_8BITS_ADD_DCT_16BITS_CLIP   v3, v0, v1, v4, v5
    st1       {v3.16b}, [x0], x1
.endr

    dup       v0.8h, v17.h[0]
    dup       v1.8h, v17.h[1]
    ins       v0.d[1], v1.d[0]
    dup       v1.8h, v17.h[2]
    dup       v2.8h, v17.h[3]
    ins       v1.d[1], v2.d[0]

.rept 4
    ld1       {v3.16b}, [x2], x3
    MB_PRED_8BITS_ADD_DCT_16BITS_CLIP   v3, v0, v1, v4, v5
    st1       {v3.16b}, [x0], x1
.endr

    dup       v0.8h, v17.h[4]
    dup       v1.8h, v17.h[5]
    ins       v0.d[1], v1.d[0]
    dup       v1.8h, v17.h[6]
    dup       v2.8h, v17.h[7]
    ins       v1.d[1], v2.d[0]

.rept 4
    ld1       {v3.16b}, [x2], x3
    MB_PRED_8BITS_ADD_DCT_16BITS_CLIP   v3, v0, v1, v4, v5
    st1       {v3.16b}, [x0], x1
.endr
WELS_ASM_AARCH64_FUNC_END
#endif
