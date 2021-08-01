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

.macro ROW_TRANSFORM_1_STEP arg0, arg1, arg2, arg3, arg4, arg5, arg6, arg7, arg8, arg9
//  {   //  input: src_d[0]~[3], output: e_q[0]~[3]; working: \arg8\() \arg9\()

    saddl       \arg4\().4s, \arg0\().4h, \arg2\().4h          //int32 e[i][0] = src[0] + src[2];
    ssubl       \arg5\().4s, \arg0\().4h, \arg2\().4h          //int32 e[i][1] = src[0] - src[2];
    sshr        \arg8\().4h, \arg1\().4h, #1
    sshr        \arg9\().4h, \arg3\().4h, #1
    ssubl       \arg6\().4s, \arg8\().4h, \arg3\().4h          //int32 e[i][2] = (src[1]>>1)-src[3];
    saddl       \arg7\().4s, \arg1\().4h, \arg9\().4h          //int32 e[i][3] = src[1] + (src[3]>>1);
//  }
.endm

.macro TRANSFORM_4BYTES arg0, arg1, arg2, arg3, arg4, arg5, arg6, arg7
// both row & col transform used
//  {   //  output: f_q[0]~[3], input: e_q[0]~[3];
    add       \arg0\().4s, \arg4\().4s, \arg7\().4s          //int16 f[i][0] = e[i][0] + e[i][3];
    add       \arg1\().4s, \arg5\().4s, \arg6\().4s          //int16 f[i][1] = e[i][1] + e[i][2];
    sub       \arg2\().4s, \arg5\().4s, \arg6\().4s          //int16 f[i][2] = e[i][1] - e[i][2];
    sub       \arg3\().4s, \arg4\().4s, \arg7\().4s          //int16 f[i][3] = e[i][0] - e[i][3];
//  }
.endm

.macro COL_TRANSFORM_1_STEP arg0, arg1, arg2, arg3, arg4, arg5, arg6, arg7
//  {   //  input: src_q[0]~[3], output: e_q[0]~[3];
    add        \arg4\().4s, \arg0\().4s, \arg2\().4s          //int32 e[0][j] = f[0][j] + f[2][j];
    sub        \arg5\().4s, \arg0\().4s, \arg2\().4s          //int32 e[1][j] = f[0][j] - f[2][j];
    sshr        \arg6\().4s, \arg1\().4s, #1
    sshr        \arg7\().4s, \arg3\().4s, #1
    sub        \arg6\().4s, \arg6\().4s, \arg3\().4s          //int32 e[2][j] = (f[1][j]>>1) - f[3][j];
    add        \arg7\().4s, \arg1\().4s, \arg7\().4s          //int32 e[3][j] = f[1][j] + (f[3][j]>>1);
//  }
.endm

//  uint8_t *pred, const int32_t stride, int16_t *rs
WELS_ASM_AARCH64_FUNC_BEGIN IdctResAddPred_AArch64_neon
    SIGN_EXTENSION x1,w1
    ld4        {v0.4h, v1.4h, v2.4h, v3.4h}, [x2]      // cost 3 cycles!
    ROW_TRANSFORM_1_STEP        v0, v1, v2, v3, v16, v17, v18, v19, v4, v5
    TRANSFORM_4BYTES        v0, v1, v2, v3, v16, v17, v18, v19
    // transform element 32bits
    trn1        v16.4s, v0.4s, v1.4s //[0 1 2 3]+[4 5 6 7]-->[0 4 2 6]
    trn2        v17.4s, v0.4s, v1.4s //[0 1 2 3]+[4 5 6 7]-->[1 5 3 7]
    trn1        v18.4s, v2.4s, v3.4s //[8 9 10 11]+[12 13 14 15]-->[8 12 10 14]
    trn2        v19.4s, v2.4s, v3.4s //[8 9 10 11]+[12 13 14 15]-->[9 13 11 15]
    trn1        v0.2d, v16.2d, v18.2d //[0 4 2 6]+[8 12 10 14]-->[0 4 8 12]
    trn2        v2.2d, v16.2d, v18.2d //[0 4 2 6]+[8 12 10 14]-->[2 6 10 14]
    trn1        v1.2d, v17.2d, v19.2d //[1 5 3 7]+[9 13 11 15]-->[1 5 9 13]
    trn2        v3.2d, v17.2d, v19.2d //[1 5 3 7]+[9 13 11 15]-->[3 7 11 15]
    COL_TRANSFORM_1_STEP        v0, v1, v2, v3, v16, v17, v18, v19

    TRANSFORM_4BYTES        v0, v1, v2, v3, v16, v17, v18, v19
    //after clip_table[MAX_NEG_CROP] into [0, 255]
    mov         x2, x0
    ld1     {v16.s}[0],[x0],x1
    ld1     {v16.s}[1],[x0],x1
    ld1     {v17.s}[0],[x0],x1
    ld1     {v17.s}[1],[x0]

    rshrn     v0.4h, v0.4s, #6
    rshrn2    v0.8h, v1.4s, #6
    rshrn     v1.4h, v2.4s, #6
    rshrn2    v1.8h, v3.4s, #6

    uxtl      v2.8h,v16.8b
    uxtl      v3.8h,v17.8b
    add        v2.8h, v2.8h, v0.8h
    add        v3.8h, v3.8h, v1.8h

    sqxtun     v0.8b,v2.8h
    sqxtun     v1.8b,v3.8h

    st1     {v0.s}[0],[x2],x1
    st1     {v0.s}[1],[x2],x1
    st1     {v1.s}[0],[x2],x1
    st1     {v1.s}[1],[x2]
WELS_ASM_AARCH64_FUNC_END

WELS_ASM_AARCH64_FUNC_BEGIN WelsBlockZero16x16_AArch64_neon
    eor v0.16b, v0.16b, v0.16b
    eor v1.16b, v1.16b, v1.16b
    SIGN_EXTENSION x1,w1
    lsl x1, x1, 1
.rept 16
    st1 {v0.16b, v1.16b}, [x0], x1
.endr
WELS_ASM_AARCH64_FUNC_END

WELS_ASM_AARCH64_FUNC_BEGIN WelsBlockZero8x8_AArch64_neon
    eor v0.16b, v0.16b, v0.16b
    SIGN_EXTENSION x1, w1
    lsl x1, x1, 1
.rept 8
    st1 {v0.16b}, [x0], x1
.endr
WELS_ASM_AARCH64_FUNC_END
#endif
