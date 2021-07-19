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

.macro MASK_MATRIX arg0, arg1, arg2, arg3, arg4, arg5, arg6
    uabd    \arg6\().16b, \arg1\().16b, \arg2\().16b
    cmhi    \arg6\().16b, \arg4\().16b, \arg6\().16b

    uabd    \arg4\().16b, \arg0\().16b, \arg1\().16b
    cmhi    \arg4\().16b, \arg5\().16b, \arg4\().16b
    and     \arg6\().16b, \arg6\().16b, \arg4\().16b

    uabd    \arg4\().16b, \arg3\().16b, \arg2\().16b
    cmhi    \arg4\().16b, \arg5\().16b, \arg4\().16b
    and     \arg6\().16b, \arg6\().16b, \arg4\().16b
.endm

.macro DIFF_LUMA_LT4_P1_Q1 arg0, arg1, arg2, arg3, arg4, arg5, arg6, arg7, arg8, arg9
    //v0, v1, v2, v3, v17(beta), v18(-Tc0), v6(Tc0), v7(flag), v19, v20
    urhadd    \arg8\().16b, \arg2\().16b, \arg3\().16b
    uhadd   \arg8\().16b, \arg0\().16b, \arg8\().16b
    usubl   \arg9\().8h, \arg8\().8b, \arg1\().8b
    sqxtn   \arg9\().8b, \arg9\().8h
    usubl2  \arg8\().8h, \arg8\().16b, \arg1\().16b
    sqxtn2  \arg9\().16b, \arg8\().8h
    smax    \arg8\().16b, \arg9\().16b, \arg5\().16b
    //
    smin  \arg8\().16b, \arg8\().16b, \arg6\().16b
    uabd  \arg9\().16b, \arg0\().16b, \arg2\().16b
    cmhi  \arg9\().16b, \arg4\().16b, \arg9\().16b
    and     \arg8\().16b, \arg8\().16b, \arg9\().16b
    and     \arg8\().16b, \arg8\().16b, \arg7\().16b
    add     \arg8\().16b, \arg1\().16b, \arg8\().16b
    abs     \arg9\().16b, \arg9\().16b
.endm

.macro DIFF_LUMA_LT4_P0_Q0_1 arg0, arg1, arg2, arg3, arg4, arg5, arg6
    usubl \arg5\().8h, \arg0\().8b, \arg3\().8b
    usubl \arg6\().8h, \arg2\().8b, \arg1\().8b
    shl     \arg6\().8h, \arg6\().8h, #2
    add     \arg5\().8h, \arg5\().8h, \arg6\().8h
    sqrshrn  \arg4\().8b, \arg5\().8h, #3
.endm

.macro DIFF_LUMA_LT4_P0_Q0_2 arg0, arg1, arg2, arg3, arg4, arg5, arg6
    usubl2    \arg5\().8h, \arg0\().16b, \arg3\().16b
    usubl2    \arg6\().8h, \arg2\().16b, \arg1\().16b
    shl     \arg6\().8h, \arg6\().8h, #2
    add     \arg5\().8h, \arg5\().8h, \arg6\().8h
    sqrshrn2  \arg4\().16b, \arg5\().8h, #3
.endm

.macro EXTRACT_DELTA_INTO_TWO_PART arg0, arg1
    cmge  \arg1\().16b, \arg0\().16b, #0
    and     \arg1\().16b, \arg0\().16b, \arg1\().16b
    sub     \arg0\().16b, \arg1\().16b, \arg0\().16b
.endm

.macro DIFF_LUMA_EQ4_P2P1P0_1 arg0, arg1, arg2, arg3, arg4, arg5, arg6, arg7, arg8, arg9
    uaddl \arg8\().8h, \arg1\().8b, \arg2\().8b
    uaddl \arg9\().8h, \arg3\().8b, \arg4\().8b
    add   \arg9\().8h, \arg9\().8h, \arg8\().8h

    uaddl \arg8\().8h, \arg0\().8b, \arg1\().8b
    shl   \arg8\().8h, \arg8\().8h, #1
    add   \arg8\().8h, \arg9\().8h, \arg8\().8h

    rshrn \arg0\().8b, \arg9\().8h, #2
    rshrn \arg7\().8b, \arg8\().8h, #3
    shl     \arg9\().8h, \arg9\().8h, #1
    usubl   \arg8\().8h, \arg5\().8b, \arg1\().8b
    add     \arg9\().8h, \arg8\().8h, \arg9\().8h

    uaddl \arg8\().8h, \arg2\().8b, \arg5\().8b
    uaddw \arg8\().8h, \arg8\().8h, \arg2\().8b
    uaddw \arg8\().8h, \arg8\().8h, \arg3\().8b

    rshrn \arg9\().8b, \arg9\().8h, #3
    rshrn \arg8\().8b, \arg8\().8h, #2
    bsl       \arg6\().8b, \arg9\().8b, \arg8\().8b
.endm

.macro DIFF_LUMA_EQ4_P2P1P0_2 arg0, arg1, arg2, arg3, arg4, arg5, arg6, arg7, arg8, arg9
    uaddl2 \arg8\().8h, \arg1\().16b, \arg2\().16b
    uaddl2 \arg9\().8h, \arg3\().16b, \arg4\().16b
    add   \arg9\().8h, \arg9\().8h, \arg8\().8h

    uaddl2 \arg8\().8h, \arg0\().16b, \arg1\().16b
    shl   \arg8\().8h, \arg8\().8h, #1
    add   \arg8\().8h, \arg9\().8h, \arg8\().8h

    rshrn2    \arg0\().16b, \arg9\().8h, #2
    rshrn2    \arg7\().16b, \arg8\().8h, #3
    shl     \arg9\().8h, \arg9\().8h, #1
    usubl2   \arg8\().8h, \arg5\().16b, \arg1\().16b
    add     \arg9\().8h, \arg8\().8h, \arg9\().8h

    uaddl2    \arg8\().8h, \arg2\().16b, \arg5\().16b
    uaddw2    \arg8\().8h, \arg8\().8h, \arg2\().16b
    uaddw2    \arg8\().8h, \arg8\().8h, \arg3\().16b

    rshrn2    \arg9\().16b, \arg9\().8h, #3
    rshrn2    \arg8\().16b, \arg8\().8h, #2
    bsl       \arg6\().16b, \arg9\().16b, \arg8\().16b
.endm


.macro DIFF_CHROMA_EQ4_P0Q0_1 arg0, arg1, arg2, arg3, arg4, arg5, arg6, arg7
    uaddl \arg4\().8h, \arg0\().8b, \arg3\().8b
    shl   \arg4\().8h, \arg4\().8h, #1
    usubl \arg5\().8h, \arg1\().8b, \arg3\().8b
    add   \arg5\().8h, \arg5\().8h, \arg4\().8h
    rshrn \arg6\().8b, \arg5\().8h, #2
    usubl \arg5\().8h, \arg2\().8b, \arg0\().8b
    add   \arg5\().8h, \arg5\().8h, \arg4\().8h
    rshrn \arg7\().8b, \arg5\().8h, #2
.endm

.macro DIFF_CHROMA_EQ4_P0Q0_2  arg0, arg1, arg2, arg3, arg4, arg5, arg6, arg7
    uaddl2 \arg4\().8h, \arg0\().16b, \arg3\().16b
    shl   \arg4\().8h, \arg4\().8h, #1
    usubl2 \arg5\().8h, \arg1\().16b, \arg3\().16b
    add   \arg5\().8h, \arg5\().8h, \arg4\().8h
    rshrn2 \arg6\().16b, \arg5\().8h, #2
    usubl2 \arg5\().8h, \arg2\().16b, \arg0\().16b
    add   \arg5\().8h, \arg5\().8h, \arg4\().8h
    rshrn2 \arg7\().16b, \arg5\().8h, #2
.endm

.macro DIFF_LUMA_EQ4_MASK arg0, arg1, arg2, arg3
    mov   \arg3\().16b, \arg2\().16b
    bsl   \arg3\().16b, \arg0\().16b, \arg1\().16b
.endm

.macro LOAD_LUMA_DATA_3 arg0, arg1, arg2, arg3, arg4, arg5, arg6
    ld3   {\arg0\().b, \arg1\().b, \arg2\().b} [\arg6], [x2], x1
    ld3   {\arg3\().b, \arg4\().b, \arg5\().b} [\arg6], [x0], x1
.endm

.macro LOAD_LUMA_DATA_4 arg0, arg1, arg2, arg3, arg4, arg5, arg6, arg7, arg8
    ld4   {\arg0\().b, \arg1\().b, \arg2\().b, \arg3\().b} [\arg8], [x3], x1
    ld4   {\arg4\().b, \arg5\().b, \arg6\().b, \arg7\().b} [\arg8], [x0], x1
.endm

.macro STORE_LUMA_DATA_4 arg0, arg1, arg2, arg3, arg4, arg5
    st4   {\arg0\().b, \arg1\().b, \arg2\().b, \arg3\().b} [\arg4], [x0], x1
    st4   {\arg0\().b, \arg1\().b, \arg2\().b, \arg3\().b} [\arg5], [x2], x1
.endm

.macro STORE_LUMA_DATA_3 arg0, arg1, arg2, arg3, arg4, arg5, arg6
    st3   {\arg0\().b, \arg1\().b, \arg2\().b} [\arg6], [x3], x1
    st3   {\arg3\().b, \arg4\().b, \arg5\().b} [\arg6], [x0], x1
.endm

.macro LOAD_CHROMA_DATA_4 arg0, arg1, arg2, arg3, arg4, arg5
    ld4   {\arg0\().b, \arg1\().b, \arg2\().b, \arg3\().b} [\arg5], [\arg4], x2
.endm

.macro STORE_CHROMA_DATA_2 arg0, arg1, arg2, arg3
    st2   {\arg0\().b, \arg1\().b} [\arg3], [\arg2], x2
.endm

.macro ZERO_JUMP_END arg0, arg1, arg2, arg3
    mov \arg1, \arg0\().d[0]
    mov \arg2, \arg0\().d[1]
    orr \arg1, \arg1, \arg2
    cbz \arg1, \arg3
.endm

.macro BS_NZC_CHECK arg0, arg1, arg2, arg3, arg4
    ld1 {v0.16b}, [\arg0]
    //Arrange the input data --- TOP
    ands     x6, \arg1, #2
    cbz      x6, bs_nzc_check_jump0
    sub      x6, \arg0, \arg2, lsl #4
    sub      x6, x6, \arg2, lsl #3
    add      x6, x6, #12
    ld1      {v1.s} [3], [x6]

bs_nzc_check_jump0:
    ext      v1.16b, v1.16b, v0.16b, #12
    add      \arg3\().16b, v0.16b, v1.16b

    // Arrange the input data --- LEFT
    ands     x6, \arg1, #1
    cbz      x6, bs_nzc_check_jump1

    sub      x6, \arg0, #21
    add      x7, x6, #4
    ld1      {v1.b} [12], [x6]
    add      x6, x7, #4
    ld1      {v1.b} [13], [x7]
    add      x7, x6, #4
    ld1      {v1.b} [14], [x6]
    ld1      {v1.b} [15], [x7]

bs_nzc_check_jump1:
    ins      v2.d[0], v0.d[1]
    zip1     v0.16b, v0.16b, v2.16b
    ins      v2.d[0], v0.d[1]
    zip1     v0.16b, v0.16b, v2.16b
    ext      v1.16b, v1.16b, v0.16b, #12
    add      \arg4\().16b, v0.16b, v1.16b
.endm

.macro BS_COMPARE_MV arg0, arg1, arg2, arg3, arg4, arg5
    //in: \arg0,\arg1(const),\arg2(const),\arg3(const),\arg4(const); out:\arg5
    mov   w6, #4
    sabd  v20.8h, \arg0\().8h, \arg1\().8h
    sabd  v21.8h, \arg1\().8h, \arg2\().8h
    dup   \arg0\().8h, w6
    sabd  v22.8h, \arg2\().8h, \arg3\().8h
    sabd  v23.8h, \arg3\().8h, \arg4\().8h

    cmge  v20.8h, v20.8h, \arg0\().8h
    cmge  v21.8h, v21.8h, \arg0\().8h
    cmge  v22.8h, v22.8h, \arg0\().8h
    cmge  v23.8h, v23.8h, \arg0\().8h

    addp v20.8h, v20.8h, v21.8h
    addp v21.8h, v22.8h, v23.8h

    addhn  \arg5\().8b, v20.8h, v20.8h
    addhn2  \arg5\().16b, v21.8h, v21.8h
.endm

.macro BS_MV_CHECK arg0, arg1, arg2, arg3, arg4, arg5, arg6
    ldp q0, q1, [\arg0], #32
    ldp q2, q3, [\arg0]
    sub \arg0, \arg0, #32
    // Arrenge the input data --- TOP
    ands     x6, \arg1, #2
    cbz     x6, bs_mv_check_jump0
    sub      x6, \arg0, \arg2, lsl #6
    add      x6, x6, #48
    ld1      {v4.16b}, [x6]
bs_mv_check_jump0:
    BS_COMPARE_MV  v4, v0, v1, v2, v3, \arg3
    // Arrange the input data --- LEFT
    ands     x6, \arg1, #1
    cbz      x6, bs_mv_check_jump1
    sub      x6, \arg0, #52
    add      x7, x6, #16
    ld1      {v4.s} [0], [x6]
    add      x6, x7, #16
    ld1      {v4.s} [1], [x7]
    add      x7, x6, #16
    ld1      {v4.s} [2], [x6]
    ld1      {v4.s} [3], [x7]
bs_mv_check_jump1:
    zip1  \arg5\().4s, v0.4s, v2.4s
    zip2  \arg6\().4s, v0.4s, v2.4s
    zip1  v0.4s, v1.4s, v3.4s
    zip2  v2.4s, v1.4s, v3.4s
    zip2  v1.4s, \arg5\().4s, v0.4s
    zip1  v0.4s, \arg5\().4s, v0.4s
    zip2  v3.4s, \arg6\().4s, v2.4s
    zip1  v2.4s, \arg6\().4s, v2.4s
    BS_COMPARE_MV  v4, v0, v1, v2, v3, \arg4
.endm

WELS_ASM_AARCH64_FUNC_BEGIN WelsNonZeroCount_AArch64_neon
    mov w1, #1
    dup v3.8b, w1
    ld1 {v0.8b, v1.8b, v2.8b}, [x0]
    umin  v0.8b, v0.8b, v3.8b
    umin  v1.8b, v1.8b, v3.8b
    umin  v2.8b, v2.8b, v3.8b
    st1 {v0.8b, v1.8b, v2.8b}, [x0]
WELS_ASM_AARCH64_FUNC_END


WELS_ASM_AARCH64_FUNC_BEGIN DeblockLumaLt4V_AArch64_neon //uint8_t* pPix, int32_t iStride, int32_t iAlpha, int32_t iBeta, int8_t* tc
    dup v16.16b, w2 //alpha
    dup v17.16b, w3 //beta
    SIGN_EXTENSION x1,w1
    add x2, x1, x1, lsl #1
    sub x2, x0, x2
    movi v23.16b, #128
    ld1 {v0.16b}, [x2], x1
    ld1 {v1.16b}, [x2], x1
    ld1 {v2.16b}, [x2]
    ld1 {v3.16b}, [x0], x1
    ld1 {v4.16b}, [x0], x1
    ld1 {v5.16b}, [x0]
    sub   x2, x2, x1
    ld4r {v18.8b, v19.8b, v20.8b, v21.8b}, [x4]
    trn1 v18.2s, v18.2s, v19.2s
    trn1 v20.2s, v20.2s, v21.2s
    trn1 v6.2d, v18.2d, v20.2d // iTc0: 0000, 1111, 2222, 3333
    cmge v7.16b, v6.16b, #0 // iTc0 Flag

    MASK_MATRIX   v1, v2, v3, v4, v16, v17, v18
    and   v7.16b, v7.16b, v18.16b // need filter flag

    ZERO_JUMP_END v7, x3, x4, DeblockLumaLt4V_AArch64_neon_end

    eor   v18.16b, v18.16b, v18.16b
    sub v18.16b, v18.16b, v6.16b // -iTc0: 0000, 1111, 2222, 3333

    DIFF_LUMA_LT4_P1_Q1   v0, v1, v2, v3, v17, v18, v6, v7, v19, v20
    st1   {v19.16b}, [x2], x1

    DIFF_LUMA_LT4_P1_Q1   v5, v4, v3, v2, v17, v18, v6, v7, v21, v22

    abs   v20.16b, v20.16b
    abs   v22.16b, v22.16b
    add   v6.16b, v6.16b, v20.16b
    add   v6.16b, v6.16b, v22.16b
    eor   v18.16b, v18.16b, v18.16b
    sub   v18.16b, v18.16b, v6.16b

    DIFF_LUMA_LT4_P0_Q0_1 v1, v2, v3, v4, v19, v20, v22
    DIFF_LUMA_LT4_P0_Q0_2 v1, v2, v3, v4, v19, v20, v22

    smax  v19.16b, v19.16b, v18.16b
    smin  v19.16b, v19.16b, v6.16b
    and     v19.16b, v19.16b, v7.16b

    EXTRACT_DELTA_INTO_TWO_PART   v19, v20
    uqadd v2.16b, v2.16b, v20.16b
    uqsub v2.16b, v2.16b, v19.16b
    st1     {v2.16b}, [x2], x1
    uqsub v3.16b, v3.16b, v20.16b
    uqadd v3.16b, v3.16b, v19.16b
    st1     {v3.16b}, [x2], x1
    st1     {v21.16b}, [x2]
DeblockLumaLt4V_AArch64_neon_end:
WELS_ASM_AARCH64_FUNC_END


WELS_ASM_AARCH64_FUNC_BEGIN DeblockLumaEq4V_AArch64_neon
    dup     v16.16b, w2 //alpha
    dup     v17.16b, w3 //beta
    SIGN_EXTENSION x1,w1
    sub     x3, x0, x1, lsl #2
    ld1     {v0.16b}, [x3], x1
    ld1     {v4.16b}, [x0], x1
    ld1     {v1.16b}, [x3], x1
    ld1     {v5.16b}, [x0], x1
    ld1     {v2.16b}, [x3], x1
    ld1     {v6.16b}, [x0], x1
    ld1     {v3.16b}, [x3]
    ld1     {v7.16b}, [x0]

    sub     x3, x3, x1, lsl #1
    MASK_MATRIX   v2, v3, v4, v5, v16, v17, v18
    lsr       w2, w2, #2
    add       w2, w2, #2
    dup     v16.16b, w2 //((alpha >> 2) + 2)
    uabd  v19.16b, v3.16b, v4.16b
    cmhi  v20.16b, v16.16b, v19.16b //iDetaP0Q0 < ((iAlpha >> 2) + 2)

    uabd  v21.16b, v1.16b, v3.16b
    cmhi  v21.16b, v17.16b, v21.16b //bDetaP2P0
    and     v21.16b, v21.16b, v20.16b //(iDetaP0Q0 < ((iAlpha >> 2) + 2))&&bDetaP2P0

    uabd  v22.16b, v6.16b, v4.16b
    cmhi  v22.16b, v17.16b, v22.16b //bDetaQ2Q0
    and     v22.16b, v22.16b, v20.16b //(iDetaP0Q0 < ((iAlpha >> 2) + 2))&&bDetaQ2Q0
    and     v20.16b, v20.16b, v18.16b //(iDetaP0Q0 < iAlpha) && bDetaP1P0 && bDetaQ1Q0&&(iDetaP0Q0 < ((iAlpha >> 2) + 2))

    mov v23.16b, v21.16b
    mov v24.16b, v21.16b

    mov v25.16b, v0.16b
    DIFF_LUMA_EQ4_P2P1P0_1        v0, v1, v2, v3, v4, v5, v23, v19, v17, v16
    DIFF_LUMA_EQ4_P2P1P0_2        v25, v1, v2, v3, v4, v5, v24, v19, v17, v16
    ins v0.d[1], v25.d[1]
    ins v23.d[1], v24.d[1]
    and   v21.16b, v20.16b, v21.16b
    DIFF_LUMA_EQ4_MASK    v19, v1, v21, v17
    st1   {v17.16b}, [x3], x1
    DIFF_LUMA_EQ4_MASK    v0, v2, v21, v17
    st1   {v17.16b}, [x3], x1
    DIFF_LUMA_EQ4_MASK    v23, v3, v18, v17
    st1   {v17.16b}, [x3], x1


    mov v23.16b, v22.16b
    mov v24.16b, v22.16b
    mov v25.16b, v7.16b
    DIFF_LUMA_EQ4_P2P1P0_1        v7, v6, v5, v4, v3, v2, v23, v19, v17, v16
    DIFF_LUMA_EQ4_P2P1P0_2        v25, v6, v5, v4, v3, v2, v24, v19, v17, v16
    ins v7.d[1], v25.d[1]
    ins v23.d[1], v24.d[1]
    and   v22.16b, v20.16b, v22.16b
    DIFF_LUMA_EQ4_MASK    v23, v4, v18, v17
    st1   {v17.16b}, [x3], x1
    DIFF_LUMA_EQ4_MASK    v7, v5, v22, v17
    st1   {v17.16b}, [x3], x1
    DIFF_LUMA_EQ4_MASK    v19, v6, v22, v17
    st1   {v17.16b}, [x3], x1
DeblockLumaEq4V_AArch64_neon_end:
WELS_ASM_AARCH64_FUNC_END


WELS_ASM_AARCH64_FUNC_BEGIN DeblockLumaLt4H_AArch64_neon //uint8_t* pPix, int32_t iStride, int32_t iAlpha, int32_t iBeta, int8_t* tc
    dup v16.16b, w2 //alpha
    dup v17.16b, w3 //beta
    sub x2, x0, #3
    movi v23.16b, #128
    SIGN_EXTENSION x1,w1
    LOAD_LUMA_DATA_3      v0, v1, v2, v3, v4, v5, 0
    LOAD_LUMA_DATA_3      v0, v1, v2, v3, v4, v5, 1
    LOAD_LUMA_DATA_3      v0, v1, v2, v3, v4, v5, 2
    LOAD_LUMA_DATA_3      v0, v1, v2, v3, v4, v5, 3
    LOAD_LUMA_DATA_3      v0, v1, v2, v3, v4, v5, 4
    LOAD_LUMA_DATA_3      v0, v1, v2, v3, v4, v5, 5
    LOAD_LUMA_DATA_3      v0, v1, v2, v3, v4, v5, 6
    LOAD_LUMA_DATA_3      v0, v1, v2, v3, v4, v5, 7

    LOAD_LUMA_DATA_3      v0, v1, v2, v3, v4, v5, 8
    LOAD_LUMA_DATA_3      v0, v1, v2, v3, v4, v5, 9
    LOAD_LUMA_DATA_3      v0, v1, v2, v3, v4, v5, 10
    LOAD_LUMA_DATA_3      v0, v1, v2, v3, v4, v5, 11
    LOAD_LUMA_DATA_3      v0, v1, v2, v3, v4, v5, 12
    LOAD_LUMA_DATA_3      v0, v1, v2, v3, v4, v5, 13
    LOAD_LUMA_DATA_3      v0, v1, v2, v3, v4, v5, 14
    LOAD_LUMA_DATA_3      v0, v1, v2, v3, v4, v5, 15

    sub x0, x0, x1, lsl #4

    ld4r {v18.8b, v19.8b, v20.8b, v21.8b}, [x4]
    trn1 v18.2s, v18.2s, v19.2s
    trn1 v20.2s, v20.2s, v21.2s
    trn1 v6.2d, v18.2d, v20.2d // iTc0: 0000, 1111, 2222, 3333
    cmge v7.16b, v6.16b, #0 // iTc0 Flag

    MASK_MATRIX   v1, v2, v3, v4, v16, v17, v18
    and   v7.16b, v7.16b, v18.16b // need filter flag

    ZERO_JUMP_END v7, x3, x4, DeblockLumaLt4H_AArch64_neon_end

    eor   v18.16b, v18.16b, v18.16b
    sub v18.16b, v18.16b, v6.16b // -iTc0: 0000, 1111, 2222, 3333

    DIFF_LUMA_LT4_P1_Q1   v0, v1, v2, v3, v17, v18, v6, v7, v19, v20 //Use Tmp v23,v24
    mov v25.16b, v19.16b

    DIFF_LUMA_LT4_P1_Q1   v5, v4, v3, v2, v17, v18, v6, v7, v21, v22 //Use Tmp v23,v24

    abs   v20.16b, v20.16b
    abs   v22.16b, v22.16b
    add   v6.16b, v6.16b, v20.16b
    add   v6.16b, v6.16b, v22.16b
    eor   v18.16b, v18.16b, v18.16b
    sub   v18.16b, v18.16b, v6.16b

    DIFF_LUMA_LT4_P0_Q0_1 v1, v2, v3, v4, v19, v20, v22
    DIFF_LUMA_LT4_P0_Q0_2 v1, v2, v3, v4, v19, v20, v22

    smax  v19.16b, v19.16b, v18.16b
    smin  v19.16b, v19.16b, v6.16b
    and     v19.16b, v19.16b, v7.16b

    EXTRACT_DELTA_INTO_TWO_PART   v19, v20
    uqadd v2.16b, v2.16b, v20.16b
    uqsub v2.16b, v2.16b, v19.16b
    mov v26.16b, v2.16b
    uqsub v3.16b, v3.16b, v20.16b
    uqadd v3.16b, v3.16b, v19.16b
    mov v27.16b, v3.16b
    mov v28.16b, v21.16b

    sub   x0, x0, #2
    add   x2, x0, x1
    lsl   x1, x1, #1

    STORE_LUMA_DATA_4     v25, v26, v27, v28, 0, 1
    STORE_LUMA_DATA_4     v25, v26, v27, v28, 2, 3
    STORE_LUMA_DATA_4     v25, v26, v27, v28, 4, 5
    STORE_LUMA_DATA_4     v25, v26, v27, v28, 6, 7

    STORE_LUMA_DATA_4     v25, v26, v27, v28, 8, 9
    STORE_LUMA_DATA_4     v25, v26, v27, v28, 10, 11
    STORE_LUMA_DATA_4     v25, v26, v27, v28, 12, 13
    STORE_LUMA_DATA_4     v25, v26, v27, v28, 14, 15
DeblockLumaLt4H_AArch64_neon_end:
WELS_ASM_AARCH64_FUNC_END


WELS_ASM_AARCH64_FUNC_BEGIN DeblockLumaEq4H_AArch64_neon
    dup     v16.16b, w2 //alpha
    dup     v17.16b, w3 //beta
    sub     x3, x0, #4
    SIGN_EXTENSION x1,w1
    LOAD_LUMA_DATA_4      v0, v1, v2, v3, v4, v5, v6, v7, 0
    LOAD_LUMA_DATA_4      v0, v1, v2, v3, v4, v5, v6, v7, 1
    LOAD_LUMA_DATA_4      v0, v1, v2, v3, v4, v5, v6, v7, 2
    LOAD_LUMA_DATA_4      v0, v1, v2, v3, v4, v5, v6, v7, 3
    LOAD_LUMA_DATA_4      v0, v1, v2, v3, v4, v5, v6, v7, 4
    LOAD_LUMA_DATA_4      v0, v1, v2, v3, v4, v5, v6, v7, 5
    LOAD_LUMA_DATA_4      v0, v1, v2, v3, v4, v5, v6, v7, 6
    LOAD_LUMA_DATA_4      v0, v1, v2, v3, v4, v5, v6, v7, 7

    LOAD_LUMA_DATA_4      v0, v1, v2, v3, v4, v5, v6, v7, 8
    LOAD_LUMA_DATA_4      v0, v1, v2, v3, v4, v5, v6, v7, 9
    LOAD_LUMA_DATA_4      v0, v1, v2, v3, v4, v5, v6, v7, 10
    LOAD_LUMA_DATA_4      v0, v1, v2, v3, v4, v5, v6, v7, 11
    LOAD_LUMA_DATA_4      v0, v1, v2, v3, v4, v5, v6, v7, 12
    LOAD_LUMA_DATA_4      v0, v1, v2, v3, v4, v5, v6, v7, 13
    LOAD_LUMA_DATA_4      v0, v1, v2, v3, v4, v5, v6, v7, 14
    LOAD_LUMA_DATA_4      v0, v1, v2, v3, v4, v5, v6, v7, 15

    sub x0, x0, x1, lsl #4
    sub x3, x0, #3
    MASK_MATRIX   v2, v3, v4, v5, v16, v17, v18

    ZERO_JUMP_END v18, x4, x5, DeblockLumaEq4H_AArch64_neon_end

    lsr       w2, w2, #2
    add       w2, w2, #2
    dup     v16.16b, w2 //((alpha >> 2) + 2)
    uabd  v19.16b, v3.16b, v4.16b
    cmhi  v20.16b, v16.16b, v19.16b //iDetaP0Q0 < ((iAlpha >> 2) + 2)

    uabd  v21.16b, v1.16b, v3.16b
    cmhi  v21.16b, v17.16b, v21.16b //bDetaP2P0
    and     v21.16b, v21.16b, v20.16b //(iDetaP0Q0 < ((iAlpha >> 2) + 2))&&bDetaP2P0

    uabd  v22.16b, v6.16b, v4.16b
    cmhi  v22.16b, v17.16b, v22.16b //bDetaQ2Q0
    and     v22.16b, v22.16b, v20.16b //(iDetaP0Q0 < ((iAlpha >> 2) + 2))&&bDetaQ2Q0
    and     v20.16b, v20.16b, v18.16b //(iDetaP0Q0 < iAlpha) && bDetaP1P0 && bDetaQ1Q0&&(iDetaP0Q0 < ((iAlpha >> 2) + 2))

    mov v23.16b, v21.16b
    mov v24.16b, v21.16b

    mov v25.16b, v0.16b
    DIFF_LUMA_EQ4_P2P1P0_1        v0, v1, v2, v3, v4, v5, v23, v19, v17, v16
    DIFF_LUMA_EQ4_P2P1P0_2        v25, v1, v2, v3, v4, v5, v24, v19, v17, v16
    ins v0.d[1], v25.d[1]
    ins v23.d[1], v24.d[1]
    and   v21.16b, v20.16b, v21.16b
    DIFF_LUMA_EQ4_MASK    v19, v1, v21, v17
    mov v26.16b, v17.16b
    DIFF_LUMA_EQ4_MASK    v0, v2, v21, v17
    mov v27.16b, v17.16b
    DIFF_LUMA_EQ4_MASK    v23, v3, v18, v17
    mov v28.16b, v17.16b


    mov v23.16b, v22.16b
    mov v24.16b, v22.16b
    mov v25.16b, v7.16b
    DIFF_LUMA_EQ4_P2P1P0_1        v7, v6, v5, v4, v3, v2, v23, v19, v17, v16
    DIFF_LUMA_EQ4_P2P1P0_2        v25, v6, v5, v4, v3, v2, v24, v19, v17, v16
    ins v7.d[1], v25.d[1]
    ins v23.d[1], v24.d[1]
    and   v22.16b, v20.16b, v22.16b
    DIFF_LUMA_EQ4_MASK    v23, v4, v18, v17
    mov v29.16b, v17.16b
    DIFF_LUMA_EQ4_MASK    v7, v5, v22, v17
    mov v30.16b, v17.16b
    DIFF_LUMA_EQ4_MASK    v19, v6, v22, v17
    mov v31.16b, v17.16b

    STORE_LUMA_DATA_3     v26, v27, v28, v29, v30, v31, 0
    STORE_LUMA_DATA_3     v26, v27, v28, v29, v30, v31, 1
    STORE_LUMA_DATA_3     v26, v27, v28, v29, v30, v31, 2
    STORE_LUMA_DATA_3     v26, v27, v28, v29, v30, v31, 3
    STORE_LUMA_DATA_3     v26, v27, v28, v29, v30, v31, 4
    STORE_LUMA_DATA_3     v26, v27, v28, v29, v30, v31, 5
    STORE_LUMA_DATA_3     v26, v27, v28, v29, v30, v31, 6
    STORE_LUMA_DATA_3     v26, v27, v28, v29, v30, v31, 7
    STORE_LUMA_DATA_3     v26, v27, v28, v29, v30, v31, 8
    STORE_LUMA_DATA_3     v26, v27, v28, v29, v30, v31, 9
    STORE_LUMA_DATA_3     v26, v27, v28, v29, v30, v31, 10
    STORE_LUMA_DATA_3     v26, v27, v28, v29, v30, v31, 11
    STORE_LUMA_DATA_3     v26, v27, v28, v29, v30, v31, 12
    STORE_LUMA_DATA_3     v26, v27, v28, v29, v30, v31, 13
    STORE_LUMA_DATA_3     v26, v27, v28, v29, v30, v31, 14
    STORE_LUMA_DATA_3     v26, v27, v28, v29, v30, v31, 15
DeblockLumaEq4H_AArch64_neon_end:
WELS_ASM_AARCH64_FUNC_END


WELS_ASM_AARCH64_FUNC_BEGIN DeblockChromaLt4V_AArch64_neon //uint8_t* pPixCb, uint8_t* pPixCr, int32_t iStrideX, int32_t iAlpha, int32_t iBeta, int8_t* pTc
    dup v16.16b, w3 //alpha
    dup v17.16b, w4 //beta
    lsl x3, x2, #1
    sub x6, x0, x3 //pPixCb-2*Stride
    sub x7, x1, x3 //pPixCr-2*Stride

    ld1 {v0.d} [0], [x6], x2
    ld1 {v1.d} [0], [x6]
    ld1 {v2.d} [0], [x0], x2
    ld1 {v3.d} [0], [x0]
    ld1 {v0.d} [1], [x7], x2
    ld1 {v1.d} [1], [x7]
    ld1 {v2.d} [1], [x1], x2
    ld1 {v3.d} [1], [x1]

    ld4r {v18.8b, v19.8b, v20.8b, v21.8b}, [x5]
    trn1 v18.4h, v18.4h, v19.4h //0011,0011,
    trn1 v20.4h, v20.4h, v21.4h //2233,2233
    zip1 v6.4s, v18.4s, v20.4s //iTc0: 0011,2233,0011,2233
    cmgt v7.16b, v6.16b, #0 // iTc0 Flag

    MASK_MATRIX   v0, v1, v2, v3, v16, v17, v18
    and   v7.16b, v7.16b, v18.16b // need filter flag

    ZERO_JUMP_END v7, x4, x5, DeblockChromaLt4V_AArch64_neon_end

    eor   v18.16b, v18.16b, v18.16b
    sub v18.16b, v18.16b, v6.16b //-iTc0: 0011,2233,0011,2233

    DIFF_LUMA_LT4_P0_Q0_1 v0, v1, v2, v3, v19, v20, v22
    DIFF_LUMA_LT4_P0_Q0_2 v0, v1, v2, v3, v19, v20, v22

    smax  v19.16b, v19.16b, v18.16b
    smin  v19.16b, v19.16b, v6.16b
    and     v19.16b, v19.16b, v7.16b

    EXTRACT_DELTA_INTO_TWO_PART   v19, v20
    uqadd v1.16b, v1.16b, v20.16b
    uqsub v1.16b, v1.16b, v19.16b
    st1     {v1.d} [0], [x6], x2
    st1     {v1.d} [1], [x7], x2
    uqsub v2.16b, v2.16b, v20.16b
    uqadd v2.16b, v2.16b, v19.16b
    st1     {v2.d} [0], [x6]
    st1     {v2.d} [1], [x7]
DeblockChromaLt4V_AArch64_neon_end:
WELS_ASM_AARCH64_FUNC_END

WELS_ASM_AARCH64_FUNC_BEGIN DeblockChromaLt4H_AArch64_neon //uint8_t* pPixCb, uint8_t* pPixCr, int32_t iStrideX, int32_t iAlpha, int32_t iBeta, int8_t* pTc
    dup v16.16b, w3 //alpha
    dup v17.16b, w4 //beta
    sub x6, x0, #2 //pPixCb-2
    sub x7, x1, #2 //pPixCr-2

    LOAD_CHROMA_DATA_4        v0, v1, v2, v3, x6, 0
    LOAD_CHROMA_DATA_4        v0, v1, v2, v3, x6, 1
    LOAD_CHROMA_DATA_4        v0, v1, v2, v3, x6, 2
    LOAD_CHROMA_DATA_4        v0, v1, v2, v3, x6, 3
    LOAD_CHROMA_DATA_4        v0, v1, v2, v3, x6, 4
    LOAD_CHROMA_DATA_4        v0, v1, v2, v3, x6, 5
    LOAD_CHROMA_DATA_4        v0, v1, v2, v3, x6, 6
    LOAD_CHROMA_DATA_4        v0, v1, v2, v3, x6, 7

    LOAD_CHROMA_DATA_4        v0, v1, v2, v3, x7, 8
    LOAD_CHROMA_DATA_4        v0, v1, v2, v3, x7, 9
    LOAD_CHROMA_DATA_4        v0, v1, v2, v3, x7, 10
    LOAD_CHROMA_DATA_4        v0, v1, v2, v3, x7, 11
    LOAD_CHROMA_DATA_4        v0, v1, v2, v3, x7, 12
    LOAD_CHROMA_DATA_4        v0, v1, v2, v3, x7, 13
    LOAD_CHROMA_DATA_4        v0, v1, v2, v3, x7, 14
    LOAD_CHROMA_DATA_4        v0, v1, v2, v3, x7, 15

    sub x0, x0, #1
    sub x1, x1, #1

    ld4r {v18.8b, v19.8b, v20.8b, v21.8b}, [x5]
    trn1 v18.4h, v18.4h, v19.4h //0011,0011,
    trn1 v20.4h, v20.4h, v21.4h //2233,2233
    zip1 v6.4s, v18.4s, v20.4s //iTc0: 0011,2233,0011,2233
    cmgt v7.16b, v6.16b, #0 // iTc0 Flag

    MASK_MATRIX   v0, v1, v2, v3, v16, v17, v18
    and   v7.16b, v7.16b, v18.16b // need filter flag

    ZERO_JUMP_END v7, x4, x5, DeblockChromaLt4H_AArch64_neon_end
    eor   v18.16b, v18.16b, v18.16b
    sub v18.16b, v18.16b, v6.16b //-iTc0: 0011,2233,0011,2233

    DIFF_LUMA_LT4_P0_Q0_1 v0, v1, v2, v3, v19, v20, v22
    DIFF_LUMA_LT4_P0_Q0_2 v0, v1, v2, v3, v19, v20, v22

    smax  v19.16b, v19.16b, v18.16b
    smin  v19.16b, v19.16b, v6.16b
    and     v19.16b, v19.16b, v7.16b

    EXTRACT_DELTA_INTO_TWO_PART   v19, v20
    uqadd v1.16b, v1.16b, v20.16b
    uqsub v1.16b, v1.16b, v19.16b
    uqsub v2.16b, v2.16b, v20.16b
    uqadd v2.16b, v2.16b, v19.16b

    STORE_CHROMA_DATA_2 v1, v2, x0, 0
    STORE_CHROMA_DATA_2 v1, v2, x0, 1
    STORE_CHROMA_DATA_2 v1, v2, x0, 2
    STORE_CHROMA_DATA_2 v1, v2, x0, 3
    STORE_CHROMA_DATA_2 v1, v2, x0, 4
    STORE_CHROMA_DATA_2 v1, v2, x0, 5
    STORE_CHROMA_DATA_2 v1, v2, x0, 6
    STORE_CHROMA_DATA_2 v1, v2, x0, 7

    STORE_CHROMA_DATA_2 v1, v2, x1, 8
    STORE_CHROMA_DATA_2 v1, v2, x1, 9
    STORE_CHROMA_DATA_2 v1, v2, x1, 10
    STORE_CHROMA_DATA_2 v1, v2, x1, 11
    STORE_CHROMA_DATA_2 v1, v2, x1, 12
    STORE_CHROMA_DATA_2 v1, v2, x1, 13
    STORE_CHROMA_DATA_2 v1, v2, x1, 14
    STORE_CHROMA_DATA_2 v1, v2, x1, 15
DeblockChromaLt4H_AArch64_neon_end:
WELS_ASM_AARCH64_FUNC_END

WELS_ASM_AARCH64_FUNC_BEGIN DeblockChromaEq4V_AArch64_neon //uint8_t* pPixCb, uint8_t* pPixCr, int32_t iStrideX, int32_t iAlpha, int32_t iBeta
    dup v16.16b, w3 //alpha
    dup v17.16b, w4 //beta
    lsl x3, x2, #1
    sub x6, x0, x3 //pPixCb-2*Stride
    sub x7, x1, x3 //pPixCr-2*Stride

    ld1 {v0.d} [0], [x6], x2
    ld1 {v1.d} [0], [x6]
    ld1 {v2.d} [0], [x0], x2
    ld1 {v3.d} [0], [x0]
    ld1 {v0.d} [1], [x7], x2
    ld1 {v1.d} [1], [x7]
    ld1 {v2.d} [1], [x1], x2
    ld1 {v3.d} [1], [x1]

    MASK_MATRIX   v0, v1, v2, v3, v16, v17, v7

    ZERO_JUMP_END v7, x3, x4, DeblockChromaEq4V_AArch64_neon_end

    DIFF_CHROMA_EQ4_P0Q0_1 v0, v1, v2, v3, v18, v19, v20, v21
    DIFF_CHROMA_EQ4_P0Q0_2 v0, v1, v2, v3, v18, v19, v20, v21

    mov v6.16b, v7.16b
    bsl v6.16b, v20.16b, v1.16b
    bsl v7.16b, v21.16b, v2.16b

    st1     {v6.d} [0], [x6], x2
    st1     {v6.d} [1], [x7], x2

    st1     {v7.d} [0], [x6]
    st1     {v7.d} [1], [x7]
DeblockChromaEq4V_AArch64_neon_end:
WELS_ASM_AARCH64_FUNC_END

WELS_ASM_AARCH64_FUNC_BEGIN DeblockChromaEq4H_AArch64_neon //uint8_t* pPixCb, uint8_t* pPixCr, int32_t iStrideX, int32_t iAlpha, int32_t iBeta
    dup v16.16b, w3 //alpha
    dup v17.16b, w4 //beta

    sub x6, x0, #2 //pPixCb-2
    sub x7, x1, #2 //pPixCr-2

    LOAD_CHROMA_DATA_4        v0, v1, v2, v3, x6, 0
    LOAD_CHROMA_DATA_4        v0, v1, v2, v3, x6, 1
    LOAD_CHROMA_DATA_4        v0, v1, v2, v3, x6, 2
    LOAD_CHROMA_DATA_4        v0, v1, v2, v3, x6, 3
    LOAD_CHROMA_DATA_4        v0, v1, v2, v3, x6, 4
    LOAD_CHROMA_DATA_4        v0, v1, v2, v3, x6, 5
    LOAD_CHROMA_DATA_4        v0, v1, v2, v3, x6, 6
    LOAD_CHROMA_DATA_4        v0, v1, v2, v3, x6, 7

    LOAD_CHROMA_DATA_4        v0, v1, v2, v3, x7, 8
    LOAD_CHROMA_DATA_4        v0, v1, v2, v3, x7, 9
    LOAD_CHROMA_DATA_4        v0, v1, v2, v3, x7, 10
    LOAD_CHROMA_DATA_4        v0, v1, v2, v3, x7, 11
    LOAD_CHROMA_DATA_4        v0, v1, v2, v3, x7, 12
    LOAD_CHROMA_DATA_4        v0, v1, v2, v3, x7, 13
    LOAD_CHROMA_DATA_4        v0, v1, v2, v3, x7, 14
    LOAD_CHROMA_DATA_4        v0, v1, v2, v3, x7, 15
    sub x0, x0, #1
    sub x1, x1, #1

    MASK_MATRIX   v0, v1, v2, v3, v16, v17, v7

    ZERO_JUMP_END v7, x3, x4, DeblockChromaEq4H_AArch64_neon_end

    DIFF_CHROMA_EQ4_P0Q0_1 v0, v1, v2, v3, v18, v19, v20, v21
    DIFF_CHROMA_EQ4_P0Q0_2 v0, v1, v2, v3, v18, v19, v20, v21

    mov v6.16b, v7.16b
    bsl v6.16b, v20.16b, v1.16b
    bsl v7.16b, v21.16b, v2.16b

    STORE_CHROMA_DATA_2 v6, v7, x0, 0
    STORE_CHROMA_DATA_2 v6, v7, x0, 1
    STORE_CHROMA_DATA_2 v6, v7, x0, 2
    STORE_CHROMA_DATA_2 v6, v7, x0, 3
    STORE_CHROMA_DATA_2 v6, v7, x0, 4
    STORE_CHROMA_DATA_2 v6, v7, x0, 5
    STORE_CHROMA_DATA_2 v6, v7, x0, 6
    STORE_CHROMA_DATA_2 v6, v7, x0, 7

    STORE_CHROMA_DATA_2 v6, v7, x1, 8
    STORE_CHROMA_DATA_2 v6, v7, x1, 9
    STORE_CHROMA_DATA_2 v6, v7, x1, 10
    STORE_CHROMA_DATA_2 v6, v7, x1, 11
    STORE_CHROMA_DATA_2 v6, v7, x1, 12
    STORE_CHROMA_DATA_2 v6, v7, x1, 13
    STORE_CHROMA_DATA_2 v6, v7, x1, 14
    STORE_CHROMA_DATA_2 v6, v7, x1, 15
DeblockChromaEq4H_AArch64_neon_end:
WELS_ASM_AARCH64_FUNC_END


WELS_ASM_AARCH64_FUNC_BEGIN DeblockingBSCalcEnc_AArch64_neon
    // Checking the nzc status
    BS_NZC_CHECK x0, x2, x3, v16, v17 //v16,v17 save the nzc status
    // For checking bS[I] = 2
    movi     v0.16b, #0
    cmgt     v16.16b, v16.16b, v0.16b
    cmgt     v17.16b, v17.16b, v0.16b
    movi     v0.16b, #2

    and  v16.16b, v16.16b, v0.16b //v16 save the nzc check result all the time --- for dir is top
    and  v17.16b, v17.16b, v0.16b //v17 save the nzc check result all the time --- for dir is left

    // Checking the mv status
    BS_MV_CHECK x1, x2, x3, v18, v19, v5 , v6 //v18, v19 save the mv status
    // For checking bS[I] = 1
    movi   v0.16b, #1
    and  v18.16b, v18.16b, v0.16b //v18 save the nzc check result all the time --- for dir is top
    and  v19.16b, v19.16b, v0.16b //v19 save the nzc check result all the time --- for dir is left
    // Check bS[I] is '1' or '2'
    umax v1.16b, v18.16b, v16.16b
    umax v0.16b, v19.16b, v17.16b
    st1 {v0.16b, v1.16b}, [x4]
WELS_ASM_AARCH64_FUNC_END


#endif
