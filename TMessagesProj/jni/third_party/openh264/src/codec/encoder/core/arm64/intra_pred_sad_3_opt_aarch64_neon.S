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

.macro LOAD_LUMA_DATA
    sub     x7, x0, x1
    ld1     {v0.16b}, [x7]      //top
    sub     x7, x0, #1
    ld1     {v1.b}[0], [x7], x1
    ld1     {v1.b}[1], [x7], x1
    ld1     {v1.b}[2], [x7], x1
    ld1     {v1.b}[3], [x7], x1
    ld1     {v1.b}[4], [x7], x1
    ld1     {v1.b}[5], [x7], x1
    ld1     {v1.b}[6], [x7], x1
    ld1     {v1.b}[7], [x7], x1
    ld1     {v1.b}[8], [x7], x1
    ld1     {v1.b}[9], [x7], x1
    ld1     {v1.b}[10], [x7], x1
    ld1     {v1.b}[11], [x7], x1
    ld1     {v1.b}[12], [x7], x1
    ld1     {v1.b}[13], [x7], x1
    ld1     {v1.b}[14], [x7], x1
    ld1     {v1.b}[15], [x7]    //left
.endm

.macro LOAD_16X4_DATA
    //Load the p_enc data and save to "v22 ~ v25"--- 16X4 bytes
    ld1     {v0.16b}, [x2], x3
    ld1     {v1.16b}, [x2], x3
    ld1     {v20.16b}, [x2], x3
    ld1     {v21.16b}, [x2], x3
    trn1    v22.4s, v0.4s, v1.4s
    trn2    v23.4s, v0.4s, v1.4s
    trn1    v24.4s, v20.4s, v21.4s
    trn2    v25.4s, v20.4s, v21.4s
.endm

.macro GET_16X16_V_SATD
    trn1    v6.4s, v4.4s, v5.4s
    trn2    v7.4s, v4.4s, v5.4s
    add     v4.8h, v6.8h, v7.8h
    sub     v5.8h, v6.8h, v7.8h
    trn1    v6.8h, v4.8h, v5.8h
    trn2    v7.8h, v4.8h, v5.8h
    add     v4.8h, v6.8h, v7.8h
    sub     v5.8h, v6.8h, v7.8h
    trn1    v6.4s, v4.4s, v5.4s
    trn2    v7.4s, v4.4s, v5.4s     //{0,1,3,2, 4,5,7,6} v6 {8,9,11,10, 12,13,15,14} v7
.endm

.macro GET_16X16_H_SATD
    trn1    v16.4s, v4.4s, v5.4s
    trn2    v17.4s, v4.4s, v5.4s
    add     v4.8h, v16.8h, v17.8h
    sub     v5.8h, v16.8h, v17.8h
    trn1    v16.8h, v4.8h, v5.8h
    trn2    v17.8h, v4.8h, v5.8h
    add     v4.8h, v16.8h, v17.8h
    sub     v5.8h, v16.8h, v17.8h
    trn1    v16.4s, v4.4s, v5.4s
    trn2    v17.4s, v4.4s, v5.4s    //{0,1,3,2, 4,5,7,6} v16 {8,9,11,10, 12,13,15,14} v17
.endm

.macro SELECT_BEST_COST arg0, arg1, arg2
    cmp     w1, \arg0
    csel    \arg0, \arg0, w1, \arg2
    cset    w7, \arg1
    cmp     w2, \arg0
    mov     w6, #2
    csel    \arg0, \arg0, w2, \arg2
    csel    w7, w7, w6, \arg2
.endm

.macro SELECT_BEST_COST_PREFER_HIGHER arg0
    SELECT_BEST_COST \arg0, ls, hi
.endm

.macro SELECT_BEST_COST_PREFER_LOWER arg0
    SELECT_BEST_COST \arg0, lo, hs
.endm

.macro LOAD_CHROMA_DATA arg0, arg1, arg2
    sub     x9, \arg0, x1
    ld1     {\arg1}, [x9]      //top_cb
    sub     x9, \arg0, #1
    ld1     {\arg2}[8], [x9], x1
    ld1     {\arg2}[9], [x9], x1
    ld1     {\arg2}[10], [x9], x1
    ld1     {\arg2}[11], [x9], x1
    ld1     {\arg2}[12], [x9], x1
    ld1     {\arg2}[13], [x9], x1
    ld1     {\arg2}[14], [x9], x1
    ld1     {\arg2}[15], [x9], x1 //left_cb
.endm

.macro LOAD_8X4_DATA arg0
    //Load the p_enc data and save to "v20 ~ v21"--- 8X4 bytes
    ld1     {v0.8b}, [\arg0], x3
    ld1     {v1.8b}, [\arg0], x3
    ld1     {v0.d}[1], [\arg0], x3
    ld1     {v1.d}[1], [\arg0], x3
    trn1    v2.4s, v0.4s, v1.4s
    trn2    v1.4s, v0.4s, v1.4s
    trn1    v20.2d, v2.2d, v1.2d
    trn2    v21.2d, v2.2d, v1.2d
.endm

.macro HDM_TRANSFORM_4X4_L0 arg0, arg1, arg2, arg3, arg4, arg5, arg6, arg7, arg8, arg9
    //Do the vertical transform
    uadd\arg9\()   v0.8h, \arg0, \arg1
    usub\arg9\()   v1.8h, \arg0, \arg1
    trn1    v3.2d, v0.2d, v1.2d
    trn2    v1.2d, v0.2d, v1.2d
    add     v4.8h, v3.8h, v1.8h //{0,1,2,3,4,5,6,7}
    sub     v5.8h, v3.8h, v1.8h //{12,13,14,15,8,9,10,11}

    //Do the horizontal transform
    trn1    v0.4s, v4.4s, v5.4s
    trn2    v1.4s, v4.4s, v5.4s
    add     v4.8h, v0.8h, v1.8h
    sub     v5.8h, v0.8h, v1.8h
    trn1    v0.8h, v4.8h, v5.8h
    trn2    v1.8h, v4.8h, v5.8h
    add     v4.8h, v0.8h, v1.8h
    sub     v5.8h, v0.8h, v1.8h

    //16x16_v
    trn1    v0.2s, v4.2s, v5.2s
    trn2    v1.2s, v4.2s, v5.2s
    sabal   \arg5, v0.4h, \arg2
    sabal   \arg5, v1.4h, \arg8\().4h
    sabal2  \arg5, v4.8h, \arg8\().8h
    sabal2  \arg5, v5.8h, \arg8\().8h

    //16x16_h
    ins     v3.d[0], v4.d[1]
    trn1    v0.4h, v4.4h, v3.4h
    trn2    v1.4h, v4.4h, v3.4h
    sabal   \arg6, v0.4h, \arg3
    sabdl   v4.4s, v1.4h, \arg8\().4h
    sabal   v4.4s, v5.4h, \arg8\().4h
    sabal2  v4.4s, v5.8h, \arg8\().8h
    add     \arg6, \arg6, v4.4s

    //16x16_dc_both
    sabal   \arg7, v0.4h, \arg4
    add     \arg7, \arg7, v4.4s
.endm

//int32_t WelsIntra8x8Combined3Sad_AArch64_neon (uint8_t*, int32_t, uint8_t*, int32_t, int32_t*, int32_t, uint8_t*, uint8_t*,uint8_t*);
WELS_ASM_AARCH64_FUNC_BEGIN WelsIntra8x8Combined3Sad_AArch64_neon
    ldr     x11, [sp, #0]
    SIGN_EXTENSION x1,w1
    SIGN_EXTENSION x3,w3
    SIGN_EXTENSION x5,w5
    LOAD_CHROMA_DATA x0, v0.8b, v0.b

    uaddlp  v1.8h, v0.16b
    uaddlp  v2.4s, v1.8h
    ins     v3.d[0], v2.d[1]
    add     v3.2s, v2.2s, v3.2s
    urshr   v2.4s, v2.4s, #2
    urshr   v3.2s, v3.2s, #3

    dup     v20.8b, v3.b[0]
    dup     v21.8b, v2.b[4]
    dup     v22.8b, v2.b[12]
    dup     v23.8b, v3.b[4]
    ins     v20.s[1], v21.s[0]
    ins     v22.s[1], v23.s[0]

    LOAD_CHROMA_DATA x7, v4.8b, v4.b

    uaddlp  v5.8h, v4.16b
    uaddlp  v6.4s, v5.8h
    ins     v7.d[0], v6.d[1]
    add     v7.2s, v6.2s, v7.2s
    urshr   v6.4s, v6.4s, #2
    urshr   v7.2s, v7.2s, #3

    dup     v24.8b, v7.b[0]
    dup     v25.8b, v6.b[4]
    dup     v26.8b, v6.b[12]
    dup     v27.8b, v7.b[4]
    ins     v24.s[1], v25.s[0]
    ins     v26.s[1], v27.s[0]

    sub     x9, x0, #1
    sub     x10, x7, #1

    ld1     {v3.8b}, [x2], x3
    ld1     {v5.8b}, [x11], x3

    ld1r    {v6.8b}, [x9], x1
    ld1r    {v7.8b}, [x10], x1

    uabdl   v29.8h, v0.8b, v3.8b
    uabal   v29.8h, v4.8b, v5.8b   //top

    uabdl   v30.8h, v6.8b, v3.8b
    uabal   v30.8h, v7.8b, v5.8b   //left

    uabdl   v31.8h, v20.8b, v3.8b
    uabal   v31.8h, v24.8b, v5.8b   //Dc
.rept 3
    ld1     {v3.8b}, [x2], x3
    ld1     {v5.8b}, [x11], x3

    ld1r    {v6.8b}, [x9], x1
    ld1r    {v7.8b}, [x10], x1

    uabal   v29.8h, v0.8b, v3.8b
    uabal   v29.8h, v4.8b, v5.8b   //top

    uabal   v30.8h, v6.8b, v3.8b
    uabal   v30.8h, v7.8b, v5.8b   //left

    uabal   v31.8h, v20.8b, v3.8b
    uabal   v31.8h, v24.8b, v5.8b   //Dc
.endr

.rept 4
    ld1     {v3.8b}, [x2], x3
    ld1     {v5.8b}, [x11], x3

    ld1r    {v6.8b}, [x9], x1
    ld1r    {v7.8b}, [x10], x1

    uabal   v29.8h, v0.8b, v3.8b
    uabal   v29.8h, v4.8b, v5.8b   //top

    uabal   v30.8h, v6.8b, v3.8b
    uabal   v30.8h, v7.8b, v5.8b   //left

    uabal   v31.8h, v22.8b, v3.8b
    uabal   v31.8h, v26.8b, v5.8b   //Dc
.endr

    saddlv  s29, v29.8h
    fmov    w2, s29
    add     w2, w2, w5, lsl #1
    saddlv  s30, v30.8h
    fmov    w1, s30
    add     w1, w1, w5, lsl #1
    saddlv  s31, v31.8h
    fmov    w0, s31

    SELECT_BEST_COST_PREFER_HIGHER w0

    str     w7, [x4]
WELS_ASM_AARCH64_FUNC_END

//int32_t WelsIntra16x16Combined3Sad_AArch64_neon (uint8_t*, int32_t, uint8_t*, int32_t, int32_t*, int32_t, uint8_t*);
WELS_ASM_AARCH64_FUNC_BEGIN WelsIntra16x16Combined3Sad_AArch64_neon
    SIGN_EXTENSION x1,w1
    SIGN_EXTENSION x3,w3
    SIGN_EXTENSION x5,w5
    LOAD_LUMA_DATA

    uaddlv    h2, v0.16b
    uaddlv    h3, v1.16b
    add       v2.8h, v2.8h, v3.8h
    uqrshrn   b2, h2, #5
    dup       v2.16b, v2.b[0]   //Dc

    sub     x7, x0, #1
    ld1     {v3.16b}, [x2], x3
    ld1r    {v4.16b}, [x7], x1

    uabdl   v29.8h, v0.8b, v3.8b
    uabal2  v29.8h, v0.16b,v3.16b   //top

    uabdl   v30.8h, v4.8b, v3.8b
    uabal2  v30.8h, v4.16b,v3.16b   //left

    uabdl   v31.8h, v2.8b, v3.8b
    uabal2  v31.8h, v2.16b,v3.16b   //Dc
    mov     x6, #15
sad_intra_16x16_x3_opt_loop0:
    ld1     {v3.16b}, [x2], x3
    ld1r    {v4.16b}, [x7], x1

    uabal   v29.8h, v0.8b, v3.8b
    uabal2  v29.8h, v0.16b,v3.16b   //top

    uabal   v30.8h, v4.8b, v3.8b
    uabal2  v30.8h, v4.16b,v3.16b   //left

    uabal   v31.8h, v2.8b, v3.8b
    uabal2  v31.8h, v2.16b,v3.16b   //Dc
    sub     x6, x6, #1
    cbnz    x6,  sad_intra_16x16_x3_opt_loop0

    saddlv  s29, v29.8h
    fmov    w0, s29
    saddlv  s30, v30.8h
    fmov    w1, s30
    add     w1, w1, w5, lsl #1
    saddlv  s31, v31.8h
    fmov    w2, s31
    add     w2, w2, w5, lsl #1

    SELECT_BEST_COST_PREFER_LOWER w0

    str     w7, [x4]
WELS_ASM_AARCH64_FUNC_END

//int32_t WelsIntra4x4Combined3Satd_AArch64_neon (uint8_t*, int32_t, uint8_t*, int32_t, uint8_t*, int32_t*, int32_t, int32_t,int32_t);
WELS_ASM_AARCH64_FUNC_BEGIN WelsIntra4x4Combined3Satd_AArch64_neon
    SIGN_EXTENSION x1,w1
    SIGN_EXTENSION x3,w3
    SIGN_EXTENSION x6,w6
    SIGN_EXTENSION x7,w7

    sub     x9, x0, x1
    ld1     {v16.s}[0], [x9]      //top
    sub     x9, x0, #1
    ld1     {v16.b}[4], [x9], x1
    ld1     {v16.b}[5], [x9], x1
    ld1     {v16.b}[6], [x9], x1
    ld1     {v16.b}[7], [x9], x1


    uaddlv  h2, v16.8b
    uqrshrn b17, h2, #3
    urshr   v2.4h, v2.4h, #3
    shl     v2.4h, v2.4h, #4

    //Calculate the 4x4_v 4x4_h mode SATD and save to "v6, v7"
    ushll   v4.8h, v16.8b, #2
    ins     v5.d[0], v4.d[1]
    trn1    v6.2s, v4.2s, v5.2s
    trn2    v7.2s, v4.2s, v5.2s

    add     v4.4h, v6.4h, v7.4h
    sub     v5.4h, v6.4h, v7.4h
    trn1    v6.4h, v4.4h, v5.4h
    trn2    v7.4h, v4.4h, v5.4h
    add     v4.4h, v6.4h, v7.4h
    sub     v5.4h, v6.4h, v7.4h
    trn1    v6.2s, v4.2s, v5.2s
    trn2    v7.2s, v4.2s, v5.2s     //{0,1,3,2,top} v6 {0,1,3,2,left} v7

    eor     v31.16b, v31.16b, v31.16b  //Save the SATD of DC_BOTH
    eor     v30.16b, v30.16b, v30.16b  //Save the SATD of H
    eor     v29.16b, v29.16b, v29.16b  //Save the SATD of V
    eor     v28.16b, v28.16b, v28.16b  //For zero register

    //Load the p_enc data and save to "v22 ~ v23"--- 16X4 bytes
    ld1     {v22.s}[0], [x2], x3
    ld1     {v22.s}[1], [x2], x3
    ld1     {v23.s}[0], [x2], x3
    ld1     {v23.s}[1], [x2], x3

    HDM_TRANSFORM_4X4_L0 v22.8b, v23.8b, v6.4h, v7.4h, v2.4h, v29.4s, v30.4s, v31.4s, v28, l

    ldr     x11, [sp, #0]
    urshr   v29.4s, v29.4s, #1
    addv    s29, v29.4s
    fmov    w0, s29
    add     w0, w0, w11

    urshr   v30.4s, v30.4s, #1
    addv    s30, v30.4s
    fmov    w1, s30
    add     w1, w1, w7

    urshr   v31.4s, v31.4s, #1
    addv    s31, v31.4s
    fmov    w2, s31
    add     w2, w2, w6

    mov     w10, w0
    SELECT_BEST_COST_PREFER_HIGHER w10

    str     w7, [x5]

    sub     w9, w10, w2
    cbnz    w9, satd_intra_4x4_x3_opt_jump0
    dup     v0.16b, v17.b[0]
    st1     {v0.16b}, [x4]
    b       satd_intra_4x4_x3_opt_end

satd_intra_4x4_x3_opt_jump0:
    sub     w8, w10, w1
    cbnz    w8, satd_intra_4x4_x3_opt_jump1
    dup     v0.16b, v16.b[4]
    dup     v1.16b, v16.b[5]
    dup     v2.16b, v16.b[6]
    dup     v3.16b, v16.b[7]
    st4     {v0.s,v1.s,v2.s,v3.s}[0], [x4]
    b       satd_intra_4x4_x3_opt_end

satd_intra_4x4_x3_opt_jump1:
    st1     {v16.S}[0], [x4], #4
    st1     {v16.S}[0], [x4], #4
    st1     {v16.S}[0], [x4], #4
    st1     {v16.S}[0], [x4]
satd_intra_4x4_x3_opt_end:
    mov     w0, w10

WELS_ASM_AARCH64_FUNC_END

//int32_t WelsIntra8x8Combined3Satd_AArch64_neon (uint8_t*, int32_t, uint8_t*, int32_t, int32_t*, int32_t, uint8_t*, uint8_t*,uint8_t*);
WELS_ASM_AARCH64_FUNC_BEGIN WelsIntra8x8Combined3Satd_AArch64_neon
    ldr     x11, [sp, #0]

    SIGN_EXTENSION x1,w1
    SIGN_EXTENSION x3,w3
    SIGN_EXTENSION x5,w5
    LOAD_CHROMA_DATA x0, v0.8b, v0.b

    LOAD_CHROMA_DATA x7, v1.8b, v1.b

    //Calculate the 16x16_v mode SATD and save to "v6, v7"
    ushll   v4.8h, v0.8b, #2
    ushll   v5.8h, v1.8b, #2
    GET_16X16_V_SATD

    //Calculate the 16x16_h mode SATD and save to "v16, v17"
    ushll2  v4.8h, v0.16b, #2
    ushll2  v5.8h, v1.16b, #2
    GET_16X16_H_SATD

    uaddlp  v0.8h, v0.16b
    uaddlp  v2.4s, v0.8h
    ins     v3.d[0], v2.d[1]
    add     v3.2s, v2.2s, v3.2s

    uaddlp  v1.8h, v1.16b
    uaddlp  v4.4s, v1.8h
    ins     v5.d[0], v4.d[1]
    add     v5.2s, v4.2s, v5.2s

    trn2    v0.4s, v2.4s, v4.4s
    urshr   v0.4s, v0.4s, #2
    urshr   v3.2s, v3.2s, #3
    urshr   v5.2s, v5.2s, #3

    ushll   v22.2d, v0.2s, #4    //{1cb, 1cr}
    ushll2  v23.2d, v0.4s, #4    //{2cb, 2cr}
    ushll   v24.2d, v3.2s, #4   //{0cb, 3cb}
    ushll   v25.2d, v5.2s, #4   //{0cr, 3cr}

    eor     v31.16b, v31.16b, v31.16b  //Save the SATD of DC_BOTH
    eor     v30.16b, v30.16b, v30.16b  //Save the SATD of H
    eor     v29.16b, v29.16b, v29.16b  //Save the SATD of V
    eor     v28.16b, v28.16b, v28.16b  //For zero register

    ins     v18.d[0], v6.d[1]
    ins     v19.d[0], v7.d[1]
    ins     v26.d[0], v16.d[1]
    ins     v27.d[0], v17.d[1]

    LOAD_8X4_DATA x2

    HDM_TRANSFORM_4X4_L0 v20.8b, v21.8b, v6.4h, v16.4h, v24.4h, v29.4s, v30.4s, v31.4s, v28, l
    HDM_TRANSFORM_4X4_L0 v20.16b, v21.16b, v18.4h, v16.4h, v22.4h, v29.4s, v30.4s, v31.4s, v28, l2

    LOAD_8X4_DATA x11

    ins     v22.d[0], v22.d[1]
    HDM_TRANSFORM_4X4_L0 v20.8b, v21.8b, v7.4h, v17.4h, v25.4h, v29.4s, v30.4s, v31.4s, v28, l
    HDM_TRANSFORM_4X4_L0 v20.16b, v21.16b, v19.4h, v17.4h, v22.4h, v29.4s, v30.4s, v31.4s, v28, l2

    LOAD_8X4_DATA x2

    ins     v24.d[0], v24.d[1]
    HDM_TRANSFORM_4X4_L0 v20.8b, v21.8b, v6.4h, v26.4h, v23.4h, v29.4s, v30.4s, v31.4s, v28, l
    HDM_TRANSFORM_4X4_L0 v20.16b, v21.16b, v18.4h, v26.4h, v24.4h, v29.4s, v30.4s, v31.4s, v28, l2

    LOAD_8X4_DATA x11

    ins     v23.d[0], v23.d[1]
    ins     v25.d[0], v25.d[1]
    HDM_TRANSFORM_4X4_L0 v20.8b, v21.8b, v7.4h, v27.4h, v23.4h, v29.4s, v30.4s, v31.4s, v28, l
    HDM_TRANSFORM_4X4_L0 v20.16b, v21.16b, v19.4h, v27.4h, v25.4h, v29.4s, v30.4s, v31.4s, v28, l2

    urshr   v29.4s, v29.4s, #1
    addv    s29, v29.4s
    fmov    w2, s29
    add     w2, w2, w5, lsl #1

    urshr   v30.4s, v30.4s, #1
    addv    s30, v30.4s
    fmov    w1, s30
    add     w1, w1, w5, lsl #1

    urshr   v31.4s, v31.4s, #1
    addv    s31, v31.4s
    fmov    w0, s31

    SELECT_BEST_COST_PREFER_HIGHER w0

    str     w7, [x4]
WELS_ASM_AARCH64_FUNC_END

//int32_t WelsIntra16x16Combined3Satd_AArch64_neon (uint8_t*, int32_t, uint8_t*, int32_t, int32_t*, int32_t, uint8_t*);
WELS_ASM_AARCH64_FUNC_BEGIN WelsIntra16x16Combined3Satd_AArch64_neon
    SIGN_EXTENSION x1,w1
    SIGN_EXTENSION x3,w3
    SIGN_EXTENSION x5,w5
    LOAD_LUMA_DATA

    uaddlv  h2, v0.16b
    uaddlv  h3, v1.16b
    add     v2.8h, v2.8h, v3.8h
    urshr   v2.4h, v2.4h, #5
    shl     v2.4h, v2.4h, #4

    //Calculate the 16x16_v mode SATD and save to "v6, v7"
    ushll   v4.8h, v0.8b, #2
    ushll2  v5.8h, v0.16b, #2
    GET_16X16_V_SATD

    //Calculate the 16x16_h mode SATD and save to "v16, v17"
    ushll   v4.8h, v1.8b, #2
    ushll2  v5.8h, v1.16b, #2
    GET_16X16_H_SATD

    eor     v31.16b, v31.16b, v31.16b  //Save the SATD of DC_BOTH
    eor     v30.16b, v30.16b, v30.16b  //Save the SATD of H
    eor     v29.16b, v29.16b, v29.16b  //Save the SATD of V
    eor     v28.16b, v28.16b, v28.16b  //For zero register

    ins     v18.d[0], v6.d[1]
    ins     v19.d[0], v7.d[1]
    ins     v26.d[0], v16.d[1]
    ins     v27.d[0], v17.d[1]

    LOAD_16X4_DATA

    HDM_TRANSFORM_4X4_L0 v22.8b, v24.8b, v6.4h, v16.4h, v2.4h, v29.4s, v30.4s, v31.4s, v28, l
    HDM_TRANSFORM_4X4_L0 v22.16b, v24.16b, v7.4h, v16.4h, v2.4h, v29.4s, v30.4s, v31.4s, v28, l2
    HDM_TRANSFORM_4X4_L0 v23.8b, v25.8b, v18.4h, v16.4h, v2.4h, v29.4s, v30.4s, v31.4s, v28, l
    HDM_TRANSFORM_4X4_L0 v23.16b, v25.16b, v19.4h, v16.4h, v2.4h, v29.4s, v30.4s, v31.4s, v28, l2

    LOAD_16X4_DATA

    HDM_TRANSFORM_4X4_L0 v22.8b, v24.8b, v6.4h, v26.4h, v2.4h, v29.4s, v30.4s, v31.4s, v28, l
    HDM_TRANSFORM_4X4_L0 v22.16b, v24.16b, v7.4h, v26.4h, v2.4h, v29.4s, v30.4s, v31.4s, v28, l2
    HDM_TRANSFORM_4X4_L0 v23.8b, v25.8b, v18.4h, v26.4h, v2.4h, v29.4s, v30.4s, v31.4s, v28, l
    HDM_TRANSFORM_4X4_L0 v23.16b, v25.16b, v19.4h, v26.4h, v2.4h, v29.4s, v30.4s, v31.4s, v28, l2

    LOAD_16X4_DATA

    HDM_TRANSFORM_4X4_L0 v22.8b, v24.8b, v6.4h, v17.4h, v2.4h, v29.4s, v30.4s, v31.4s, v28, l
    HDM_TRANSFORM_4X4_L0 v22.16b, v24.16b, v7.4h, v17.4h, v2.4h, v29.4s, v30.4s, v31.4s, v28, l2
    HDM_TRANSFORM_4X4_L0 v23.8b, v25.8b, v18.4h, v17.4h, v2.4h, v29.4s, v30.4s, v31.4s, v28, l
    HDM_TRANSFORM_4X4_L0 v23.16b, v25.16b, v19.4h, v17.4h, v2.4h, v29.4s, v30.4s, v31.4s, v28, l2

    LOAD_16X4_DATA

    HDM_TRANSFORM_4X4_L0 v22.8b, v24.8b, v6.4h, v27.4h, v2.4h, v29.4s, v30.4s, v31.4s, v28, l
    HDM_TRANSFORM_4X4_L0 v22.16b, v24.16b, v7.4h, v27.4h, v2.4h, v29.4s, v30.4s, v31.4s, v28, l2
    HDM_TRANSFORM_4X4_L0 v23.8b, v25.8b, v18.4h, v27.4h, v2.4h, v29.4s, v30.4s, v31.4s, v28, l
    HDM_TRANSFORM_4X4_L0 v23.16b, v25.16b, v19.4h, v27.4h, v2.4h, v29.4s, v30.4s, v31.4s, v28, l2

    urshr   v29.4s, v29.4s, #1
    addv    s29, v29.4s
    fmov    w0, s29

    urshr   v30.4s, v30.4s, #1
    addv    s30, v30.4s
    fmov    w1, s30
    add     w1, w1, w5, lsl #1

    urshr   v31.4s, v31.4s, #1
    addv    s31, v31.4s
    fmov    w2, s31
    add     w2, w2, w5, lsl #1

    SELECT_BEST_COST_PREFER_LOWER w0

    str     w7, [x4]

WELS_ASM_AARCH64_FUNC_END

#endif
