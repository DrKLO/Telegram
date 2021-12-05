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

.macro CALC_AND_STORE_SAD
    saddlv  s2, v2.8h
    fmov    w0, s2
.endm

.macro CALC_AND_STORE_SAD_FOUR
    saddlv  s28, v28.8h
    saddlv  s29, v29.8h
    saddlv  s30, v30.8h
    saddlv  s31, v31.8h
    st4     {v28.s, v29.s, v30.s, v31.s}[0], [x4]
.endm

.macro LOAD_8X8_1
    ld1     {v0.8b}, [x0], x1
    ld1     {v1.8b}, [x0], x1
    ld1     {v2.8b}, [x0], x1
    ld1     {v3.8b}, [x0], x1
    ld1     {v4.8b}, [x0], x1
    ld1     {v5.8b}, [x0], x1
    ld1     {v6.8b}, [x0], x1
    ld1     {v7.8b}, [x0], x1
.endm

.macro LOAD_16X8_1
    ld1     {v0.16b}, [x0], x1
    ld1     {v1.16b}, [x0], x1
    ld1     {v2.16b}, [x0], x1
    ld1     {v3.16b}, [x0], x1
    ld1     {v4.16b}, [x0], x1
    ld1     {v5.16b}, [x0], x1
    ld1     {v6.16b}, [x0], x1
    ld1     {v7.16b}, [x0], x1
.endm

.macro LOAD_8X8_2 arg0
    ld1     {v16.8b}, [\arg0], x3
    ld1     {v17.8b}, [\arg0], x3
    ld1     {v18.8b}, [\arg0], x3
    ld1     {v19.8b}, [\arg0], x3
    ld1     {v20.8b}, [\arg0], x3
    ld1     {v21.8b}, [\arg0], x3
    ld1     {v22.8b}, [\arg0], x3
    ld1     {v23.8b}, [\arg0], x3
.endm

.macro CALC_ABS_8X8_1 arg0, arg1
    uab\arg1\()l    \arg0, v0.8b, v16.8b
    uabal   \arg0, v1.8b, v17.8b
    uabal   \arg0, v2.8b, v18.8b
    uabal   \arg0, v3.8b, v19.8b
    uabal   \arg0, v4.8b, v20.8b
    uabal   \arg0, v5.8b, v21.8b
    uabal   \arg0, v6.8b, v22.8b
    uabal   \arg0, v7.8b, v23.8b
.endm

.macro CALC_ABS_8X8_2 arg0
    uab\arg0\()l    v29.8h, v0.8b, v18.8b
    uabal   v29.8h, v1.8b, v19.8b
    uabal   v29.8h, v2.8b, v20.8b
    uabal   v29.8h, v3.8b, v21.8b
    uabal   v29.8h, v4.8b, v22.8b
    uabal   v29.8h, v5.8b, v23.8b
    uabal   v29.8h, v6.8b, v24.8b
    uabal   v29.8h, v7.8b, v25.8b
.endm

.macro LOAD_16X8_2 arg0
    ld1     {v16.16b}, [\arg0], x3
    ld1     {v17.16b}, [\arg0], x3
    ld1     {v18.16b}, [\arg0], x3
    ld1     {v19.16b}, [\arg0], x3
    ld1     {v20.16b}, [\arg0], x3
    ld1     {v21.16b}, [\arg0], x3
    ld1     {v22.16b}, [\arg0], x3
    ld1     {v23.16b}, [\arg0], x3
.endm

.macro CALC_ABS_16X8_1 arg0, arg1
    uab\arg1\()l  \arg0, v0.8b, v16.8b
    uabal2  \arg0, v0.16b,v16.16b
    uabal   \arg0, v1.8b, v17.8b
    uabal2  \arg0, v1.16b,v17.16b
    uabal   \arg0, v2.8b, v18.8b
    uabal2  \arg0, v2.16b,v18.16b
    uabal   \arg0, v3.8b, v19.8b
    uabal2  \arg0, v3.16b,v19.16b
    uabal   \arg0, v4.8b, v20.8b
    uabal2  \arg0, v4.16b,v20.16b
    uabal   \arg0, v5.8b, v21.8b
    uabal2  \arg0, v5.16b,v21.16b
    uabal   \arg0, v6.8b, v22.8b
    uabal2  \arg0, v6.16b,v22.16b
    uabal   \arg0, v7.8b, v23.8b
    uabal2  \arg0, v7.16b,v23.16b
.endm

.macro CALC_ABS_16X8_2 arg0
    uab\arg0\()l  v29.8h, v0.8b, v18.8b
    uabal2  v29.8h, v0.16b,v18.16b
    uabal   v29.8h, v1.8b, v19.8b
    uabal2  v29.8h, v1.16b,v19.16b
    uabal   v29.8h, v2.8b, v20.8b
    uabal2  v29.8h, v2.16b,v20.16b
    uabal   v29.8h, v3.8b, v21.8b
    uabal2  v29.8h, v3.16b,v21.16b
    uabal   v29.8h, v4.8b, v22.8b
    uabal2  v29.8h, v4.16b,v22.16b
    uabal   v29.8h, v5.8b, v23.8b
    uabal2  v29.8h, v5.16b,v23.16b
    uabal   v29.8h, v6.8b, v24.8b
    uabal2  v29.8h, v6.16b,v24.16b
    uabal   v29.8h, v7.8b, v25.8b
    uabal2  v29.8h, v7.16b,v25.16b
.endm

WELS_ASM_AARCH64_FUNC_BEGIN WelsSampleSad4x4_AArch64_neon
    sxtw    x1, w1
    sxtw    x3, w3
    ld1     {v0.s}[0], [x0], x1
    ld1     {v1.s}[0], [x2], x3
    uabdl   v2.8h, v0.8b, v1.8b
.rept 3
    ld1     {v0.s}[0], [x0], x1
    ld1     {v1.s}[0], [x2], x3
    uabal   v2.8h, v0.8b, v1.8b
.endr
    saddlv  s2, v2.4h
    fmov    w0, s2
WELS_ASM_AARCH64_FUNC_END

WELS_ASM_AARCH64_FUNC_BEGIN WelsSampleSad8x8_AArch64_neon
    sxtw    x1, w1
    sxtw    x3, w3
    ld1     {v0.8b}, [x0], x1
    ld1     {v1.8b}, [x2], x3
    uabdl   v2.8h, v0.8b, v1.8b
.rept 7
    ld1     {v0.8b}, [x0], x1
    ld1     {v1.8b}, [x2], x3
    uabal   v2.8h, v0.8b, v1.8b
.endr
    CALC_AND_STORE_SAD
WELS_ASM_AARCH64_FUNC_END

WELS_ASM_AARCH64_FUNC_BEGIN WelsSampleSad8x16_AArch64_neon
    sxtw    x1, w1
    sxtw    x3, w3
    ld1     {v0.8b}, [x0], x1
    ld1     {v1.8b}, [x2], x3
    uabdl   v2.8h, v0.8b, v1.8b
.rept 15
    ld1     {v0.8b}, [x0], x1
    ld1     {v1.8b}, [x2], x3
    uabal   v2.8h, v0.8b, v1.8b
.endr
    CALC_AND_STORE_SAD
WELS_ASM_AARCH64_FUNC_END

WELS_ASM_AARCH64_FUNC_BEGIN WelsSampleSad16x8_AArch64_neon
    sxtw    x1, w1
    sxtw    x3, w3
    ld1     {v0.16b}, [x0], x1
    ld1     {v1.16b}, [x2], x3
    uabdl   v2.8h, v0.8b, v1.8b
    uabal2  v2.8h, v0.16b, v1.16b
.rept 7
    ld1     {v0.16b}, [x0], x1
    ld1     {v1.16b}, [x2], x3
    uabal   v2.8h, v0.8b, v1.8b
    uabal2  v2.8h, v0.16b, v1.16b
.endr
    CALC_AND_STORE_SAD
WELS_ASM_AARCH64_FUNC_END

WELS_ASM_AARCH64_FUNC_BEGIN WelsSampleSad16x16_AArch64_neon
    sxtw    x1, w1
    sxtw    x3, w3
    ld1     {v0.16b}, [x0], x1
    ld1     {v1.16b}, [x2], x3
    uabdl   v2.8h, v0.8b, v1.8b
    uabal2  v2.8h, v0.16b, v1.16b
.rept 15
    ld1     {v0.16b}, [x0], x1
    ld1     {v1.16b}, [x2], x3
    uabal   v2.8h, v0.8b, v1.8b
    uabal2  v2.8h, v0.16b, v1.16b
.endr
    CALC_AND_STORE_SAD
WELS_ASM_AARCH64_FUNC_END

WELS_ASM_AARCH64_FUNC_BEGIN WelsSampleSadFour4x4_AArch64_neon
    sxtw    x1, w1
    sxtw    x3, w3
    ld1     {v0.s}[0], [x0], x1
    ld1     {v0.s}[1], [x0], x1
    ld1     {v1.s}[0], [x0], x1
    ld1     {v1.s}[1], [x0]
    sub     x0, x2, x3
    ld1     {v2.s}[0], [x0], x3
    ld1     {v2.s}[1], [x0], x3
    ld1     {v3.s}[0], [x0], x3
    ld1     {v3.s}[1], [x0], x3
    ld1     {v4.s}[0], [x0], x3
    ld1     {v4.s}[1], [x0], x3

    uabdl   v28.8h, v0.8b, v2.8b
    uabal   v28.8h, v1.8b, v3.8b

    uabdl   v29.8h, v0.8b, v3.8b
    uabal   v29.8h, v1.8b, v4.8b

    sub     x0, x2, #1
    ld1     {v2.s}[0], [x0], x3
    ld1     {v2.s}[1], [x0], x3
    ld1     {v3.s}[0], [x0], x3
    ld1     {v3.s}[1], [x0]
    uabdl   v30.8h, v0.8b, v2.8b
    uabal   v30.8h, v1.8b, v3.8b

    add     x0, x2, #1
    ld1     {v2.s}[0], [x0], x3
    ld1     {v2.s}[1], [x0], x3
    ld1     {v3.s}[0], [x0], x3
    ld1     {v3.s}[1], [x0]
    uabdl   v31.8h, v0.8b, v2.8b
    uabal   v31.8h, v1.8b, v3.8b

    CALC_AND_STORE_SAD_FOUR
WELS_ASM_AARCH64_FUNC_END

WELS_ASM_AARCH64_FUNC_BEGIN WelsSampleSadFour8x8_AArch64_neon
    sxtw    x1, w1
    sxtw    x3, w3
    LOAD_8X8_1
    sub     x0, x2, x3
    LOAD_8X8_2 x0
    ld1     {v24.8b}, [x0], x3
    ld1     {v25.8b}, [x0]

    CALC_ABS_8X8_1 v28.8h, d
    CALC_ABS_8X8_2 d

    sub     x0, x2, #1
    LOAD_8X8_2 x0
    CALC_ABS_8X8_1 v30.8h, d

    add     x0, x2, #1
    LOAD_8X8_2 x0
    CALC_ABS_8X8_1 v31.8h, d

    CALC_AND_STORE_SAD_FOUR
WELS_ASM_AARCH64_FUNC_END

WELS_ASM_AARCH64_FUNC_BEGIN WelsSampleSadFour8x16_AArch64_neon
    sxtw    x1, w1
    sxtw    x3, w3
    LOAD_8X8_1
    sub     x5, x2, x3
    LOAD_8X8_2 x5
    ld1     {v24.8b}, [x5], x3
    ld1     {v25.8b}, [x5], x3

    CALC_ABS_8X8_1 v28.8h, d
    CALC_ABS_8X8_2 d

    sub     x6, x2, #1
    LOAD_8X8_2 x6
    CALC_ABS_8X8_1 v30.8h, d

    add     x7, x2, #1
    LOAD_8X8_2 x7
    CALC_ABS_8X8_1 v31.8h, d

    LOAD_8X8_1
    sub     x5, x5, x3
    sub     x5, x5, x3
    LOAD_8X8_2 x5
    ld1     {v24.8b}, [x5], x3
    ld1     {v25.8b}, [x5]

    CALC_ABS_8X8_1 v28.8h, a
    CALC_ABS_8X8_2 a

    LOAD_8X8_2 x6
    CALC_ABS_8X8_1 v30.8h, a

    LOAD_8X8_2 x7
    CALC_ABS_8X8_1 v31.8h, a

    CALC_AND_STORE_SAD_FOUR
WELS_ASM_AARCH64_FUNC_END

WELS_ASM_AARCH64_FUNC_BEGIN WelsSampleSadFour16x8_AArch64_neon
    sxtw    x1, w1
    sxtw    x3, w3
    LOAD_16X8_1
    sub     x0, x2, x3
    LOAD_16X8_2 x0
    ld1     {v24.16b}, [x0], x3
    ld1     {v25.16b}, [x0]

    CALC_ABS_16X8_1 v28.8h, d
    CALC_ABS_16X8_2 d

    sub     x0, x2, #1
    LOAD_16X8_2 x0
    CALC_ABS_16X8_1 v30.8h, d

    add     x0, x2, #1
    LOAD_16X8_2 x0
    CALC_ABS_16X8_1 v31.8h, d

    CALC_AND_STORE_SAD_FOUR
WELS_ASM_AARCH64_FUNC_END

WELS_ASM_AARCH64_FUNC_BEGIN WelsSampleSadFour16x16_AArch64_neon
    sxtw    x1, w1
    sxtw    x3, w3

    LOAD_16X8_1
    sub     x5, x2, x3
    LOAD_16X8_2 x5
    ld1     {v24.16b}, [x5], x3
    ld1     {v25.16b}, [x5], x3

    CALC_ABS_16X8_1 v28.8h, d
    CALC_ABS_16X8_2 d

    sub     x6, x2, #1
    LOAD_16X8_2 x6
    CALC_ABS_16X8_1 v30.8h, d

    add     x7, x2, #1
    LOAD_16X8_2 x7
    CALC_ABS_16X8_1 v31.8h, d

    LOAD_16X8_1
    sub     x5, x5, x3
    sub     x5, x5, x3
    LOAD_16X8_2 x5
    ld1     {v24.16b}, [x5], x3
    ld1     {v25.16b}, [x5]

    CALC_ABS_16X8_1 v28.8h, a
    CALC_ABS_16X8_2 a

    LOAD_16X8_2 x6
    CALC_ABS_16X8_1 v30.8h, a

    LOAD_16X8_2 x7
    CALC_ABS_16X8_1 v31.8h, a

    CALC_AND_STORE_SAD_FOUR
WELS_ASM_AARCH64_FUNC_END

WELS_ASM_AARCH64_FUNC_BEGIN WelsSampleSatd4x4_AArch64_neon
    sxtw    x1, w1
    sxtw    x3, w3
    ld1     {v0.s}[0], [x0], x1
    ld1     {v0.s}[1], [x0], x1
    ld1     {v1.s}[0], [x0], x1
    ld1     {v1.s}[1], [x0]

    ld1     {v2.s}[0], [x2], x3
    ld1     {v2.s}[1], [x2], x3
    ld1     {v3.s}[0], [x2], x3
    ld1     {v3.s}[1], [x2]
    usubl   v4.8h, v0.8b, v2.8b //{0,1,2,3,4,5,6,7}
    usubl   v5.8h, v1.8b, v3.8b //{8,9,10,11,12,13,14,15}

    //Do the vertical transform
    add     v6.8h, v4.8h, v5.8h //{0,4,8,12,1,5,9,13}
    sub     v7.8h, v4.8h, v5.8h //{2,6,10,14,3,7,11,15}
    mov     x4,      v6.d[1]
    mov     v6.d[1], v7.d[0]
    ins     v7.d[0], x4
    add     v4.8h, v6.8h, v7.8h
    sub     v5.8h, v6.8h, v7.8h

    //Do the horizontal transform
    trn1    v6.4s, v4.4s, v5.4s
    trn2    v7.4s, v4.4s, v5.4s
    add     v4.8h, v6.8h, v7.8h
    sub     v5.8h, v6.8h, v7.8h
    trn1    v6.8h, v4.8h, v5.8h
    trn2    v7.8h, v4.8h, v5.8h
    add     v4.8h, v6.8h, v7.8h
    abs     v4.8h, v4.8h
    saba    v4.8h, v6.8h, v7.8h
    uaddlv  s4, v4.8h
    fmov    w0, s4
    add     w0, w0, #1
    lsr     w0, w0, #1

WELS_ASM_AARCH64_FUNC_END

.macro SATD_8x4
    ld1     {v0.8b}, [x0], x1
    ld1     {v1.8b}, [x2], x3
    ld1     {v2.8b}, [x0], x1
    usubl   v16.8h,  v0.8b, v1.8b

    ld1     {v3.8b}, [x2], x3
    usubl   v17.8h,  v2.8b, v3.8b
    ld1     {v4.8b}, [x0], x1
    ld1     {v5.8b}, [x2], x3

    add     v25.8h,  v16.8h, v17.8h
    usubl   v18.8h,  v4.8b,  v5.8b

    ld1     {v6.8b}, [x0], x1
    ld1     {v7.8b}, [x2], x3

    usubl   v19.8h,  v6.8b,  v7.8b
    sub     v26.8h,  v16.8h, v17.8h

    add     v27.8h,  v18.8h, v19.8h
    sub     v28.8h,  v18.8h, v19.8h

    add     v0.8h,  v25.8h, v27.8h
    sub     v1.8h,  v25.8h, v27.8h

    add     v2.8h,  v26.8h, v28.8h
    sub     v3.8h,  v26.8h, v28.8h

    trn1    v4.8h, v0.8h, v1.8h
    trn2    v5.8h, v0.8h, v1.8h
    trn1    v6.8h, v2.8h, v3.8h
    trn2    v7.8h, v2.8h, v3.8h

    add     v16.8h, v4.8h, v5.8h
    sabd    v17.8h, v4.8h, v5.8h
    abs     v16.8h, v16.8h
    add     v18.8h, v6.8h, v7.8h
    sabd    v19.8h, v6.8h, v7.8h
    abs     v18.8h, v18.8h

    trn1    v4.4s, v16.4s, v17.4s
    trn2    v5.4s, v16.4s, v17.4s
    trn1    v6.4s, v18.4s, v19.4s
    trn2    v7.4s, v18.4s, v19.4s

    smax    v0.8h, v4.8h, v5.8h
    smax    v1.8h, v6.8h, v7.8h
.endm

.macro SATD_16x4
    ld1     {v0.16b}, [x0], x1
    ld1     {v1.16b}, [x2], x3
    ld1     {v2.16b}, [x0], x1
    usubl   v16.8h,  v0.8b, v1.8b
    usubl2  v24.8h,  v0.16b, v1.16b

    ld1     {v3.16b}, [x2], x3
    usubl   v17.8h,  v2.8b, v3.8b
    usubl2  v25.8h,  v2.16b, v3.16b

    ld1     {v4.16b}, [x0], x1
    ld1     {v5.16b}, [x2], x3
    usubl   v18.8h,  v4.8b, v5.8b
    usubl2  v26.8h,  v4.16b, v5.16b

    ld1     {v6.16b}, [x0], x1
    ld1     {v7.16b}, [x2], x3
    usubl   v19.8h,  v6.8b, v7.8b
    usubl2  v27.8h,  v6.16b, v7.16b

    add     v0.8h,  v16.8h, v17.8h
    sub     v1.8h,  v16.8h, v17.8h
    add     v2.8h,  v18.8h, v19.8h
    sub     v3.8h,  v18.8h, v19.8h

    add     v4.8h,  v24.8h, v25.8h
    sub     v5.8h,  v24.8h, v25.8h
    add     v6.8h,  v26.8h, v27.8h
    sub     v7.8h,  v26.8h, v27.8h

    add     v16.8h,  v0.8h, v2.8h
    sub     v18.8h,  v0.8h, v2.8h
    add     v17.8h,  v4.8h, v6.8h
    sub     v19.8h,  v4.8h, v6.8h

    add     v0.8h,  v1.8h, v3.8h
    sub     v2.8h,  v1.8h, v3.8h
    add     v1.8h,  v5.8h, v7.8h
    sub     v3.8h,  v5.8h, v7.8h

    trn1    v4.8h, v16.8h, v18.8h
    trn2    v6.8h, v16.8h, v18.8h
    trn1    v5.8h, v17.8h, v19.8h
    trn2    v7.8h, v17.8h, v19.8h

    add     v16.8h, v4.8h, v6.8h
    sabd    v18.8h, v4.8h, v6.8h
    add     v17.8h, v5.8h, v7.8h
    sabd    v19.8h, v5.8h, v7.8h
    abs     v16.8h, v16.8h
    abs     v17.8h, v17.8h

    trn1    v4.8h, v0.8h, v2.8h
    trn2    v6.8h, v0.8h, v2.8h
    trn1    v5.8h, v1.8h, v3.8h
    trn2    v7.8h, v1.8h, v3.8h

    add     v0.8h, v4.8h, v6.8h
    sabd    v2.8h, v4.8h, v6.8h
    add     v1.8h, v5.8h, v7.8h
    sabd    v3.8h, v5.8h, v7.8h
    abs     v0.8h, v0.8h
    abs     v1.8h, v1.8h

    trn1    v4.4s, v16.4s, v18.4s
    trn2    v6.4s, v16.4s, v18.4s
    trn1    v5.4s, v17.4s, v19.4s
    trn2    v7.4s, v17.4s, v19.4s

    trn1    v16.4s, v0.4s, v2.4s
    trn2    v18.4s, v0.4s, v2.4s
    trn1    v17.4s, v1.4s, v3.4s
    trn2    v19.4s, v1.4s, v3.4s

    smax    v0.8h, v4.8h, v6.8h
    smax    v1.8h, v5.8h, v7.8h
    smax    v2.8h, v16.8h, v18.8h
    smax    v3.8h, v17.8h, v19.8h
    add     v0.8h, v0.8h, v1.8h
    add     v2.8h, v2.8h, v3.8h
.endm

WELS_ASM_AARCH64_FUNC_BEGIN WelsSampleSatd16x16_AArch64_neon
    sxtw    x1, w1
    sxtw    x3, w3
    SATD_16x4
    add     v31.8h, v0.8h, v2.8h
.rept 3
    SATD_16x4
    add     v31.8h, v31.8h, v0.8h
    add     v31.8h, v31.8h, v2.8h
.endr
    uaddlv  s4, v31.8h
    fmov    w0, s4
WELS_ASM_AARCH64_FUNC_END

WELS_ASM_AARCH64_FUNC_BEGIN WelsSampleSatd16x8_AArch64_neon
    sxtw    x1, w1
    sxtw    x3, w3
    SATD_16x4
    add     v31.8h, v0.8h, v2.8h

    SATD_16x4
    add     v31.8h, v31.8h, v0.8h
    add     v31.8h, v31.8h, v2.8h

    uaddlv  s4, v31.8h
    fmov    w0, s4
WELS_ASM_AARCH64_FUNC_END

WELS_ASM_AARCH64_FUNC_BEGIN WelsSampleSatd8x16_AArch64_neon
    sxtw    x1, w1
    sxtw    x3, w3
    SATD_8x4
    add     v31.8h, v0.8h, v1.8h
.rept 3
    SATD_8x4
    add     v31.8h, v31.8h, v0.8h
    add     v31.8h, v31.8h, v1.8h
.endr
    uaddlv  s4, v31.8h
    fmov    w0, s4
WELS_ASM_AARCH64_FUNC_END

WELS_ASM_AARCH64_FUNC_BEGIN WelsSampleSatd8x8_AArch64_neon
    sxtw    x1, w1
    sxtw    x3, w3
    SATD_8x4
    add     v31.8h, v0.8h, v1.8h

    SATD_8x4
    add     v31.8h, v31.8h, v0.8h
    add     v31.8h, v31.8h, v1.8h
    uaddlv  s4, v31.8h
    fmov    w0, s4
WELS_ASM_AARCH64_FUNC_END
#endif
