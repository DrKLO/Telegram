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

// for Luma 4x4
WELS_ASM_AARCH64_FUNC_BEGIN WelsDecoderI4x4LumaPredH_AArch64_neon
    sxtw    x1, w1
    sub     x2, x0, #1
.rept 4
    ld1r    {v0.8b}, [x2], x1
    st1     {v0.S}[0], [x0], x1
.endr
WELS_ASM_AARCH64_FUNC_END

WELS_ASM_AARCH64_FUNC_BEGIN WelsDecoderI4x4LumaPredDc_AArch64_neon
    sxtw    x1, w1
    sub     x2, x0, x1
    sub     x3, x0, #1
    ldr     s0, [x2]
    ld1     {v0.b}[4], [x3], x1
    ld1     {v0.b}[5], [x3], x1
    ld1     {v0.b}[6], [x3], x1
    ld1     {v0.b}[7], [x3]
    uaddlv  h0, v0.8b
    uqrshrn b0, h0, #3
    dup     v0.8b, v0.b[0]
.rept 4
    st1     {v0.S}[0], [x0], x1
.endr
WELS_ASM_AARCH64_FUNC_END

WELS_ASM_AARCH64_FUNC_BEGIN WelsDecoderI4x4LumaPredDcTop_AArch64_neon
    sxtw    x1, w1
    sub     x2, x0, x1
    sub     v0.8b, v0.8b, v0.8b
    ldr     s0, [x2]
    uaddlv  h0, v0.8b
    uqrshrn v0.8b, v0.8h, #2
    dup     v0.8b, v0.b[0]
.rept 4
    st1     {v0.S}[0], [x0], x1
.endr
WELS_ASM_AARCH64_FUNC_END

WELS_ASM_AARCH64_FUNC_BEGIN WelsDecoderI4x4LumaPredDDL_AArch64_neon
    sxtw    x1, w1
    sub     x2, x0, x1
    ld1     {v0.8b}, [x2]
    dup     v1.8b, v0.b[7]
    ext     v2.8b, v0.8b, v1.8b, #1
    ext     v3.8b, v0.8b, v1.8b, #2
    ushll   v2.8h, v2.8b, #1
    uaddl   v1.8h, v3.8b, v0.8b
    add     v1.8h, v1.8h, v2.8h
    uqrshrn v1.8b, v1.8h, #2
    st1     {v1.S}[0], [x0], x1
    ext     v0.8b, v1.8b, v2.8b, #1
    st1     {v0.S}[0], [x0], x1
    ext     v0.8b, v1.8b, v2.8b, #2
    st1     {v0.S}[0], [x0], x1
    ext     v0.8b, v1.8b, v2.8b, #3
    st1     {v0.S}[0], [x0]
WELS_ASM_AARCH64_FUNC_END

WELS_ASM_AARCH64_FUNC_BEGIN WelsDecoderI4x4LumaPredDDLTop_AArch64_neon
    sxtw    x1, w1
    sub     x2, x0, x1
    ld1     {v0.8b}, [x2]
    dup     v1.8b, v0.b[3]
    mov     v0.S[1], v1.S[0]
    ext     v2.8b, v0.8b, v1.8b, #1
    ext     v3.8b, v0.8b, v1.8b, #2
    ushll   v2.8h, v2.8b, #1
    uaddl   v1.8h, v3.8b, v0.8b
    add     v1.8h, v1.8h, v2.8h
    uqrshrn v1.8b, v1.8h, #2
    st1     {v1.S}[0], [x0], x1
    ext     v0.8b, v1.8b, v2.8b, #1
    st1     {v0.S}[0], [x0], x1
    ext     v0.8b, v1.8b, v2.8b, #2
    st1     {v0.S}[0], [x0], x1
    ext     v0.8b, v1.8b, v2.8b, #3
    st1     {v0.S}[0], [x0]
WELS_ASM_AARCH64_FUNC_END

WELS_ASM_AARCH64_FUNC_BEGIN WelsDecoderI4x4LumaPredVL_AArch64_neon
    sxtw    x1, w1
    sub     x2, x0, x1
    ld1     {v0.8b}, [x2]
    ext     v1.8b, v0.8b, v0.8b, #1
    uaddl   v1.8h, v1.8b, v0.8b
    uqrshrn v0.8b, v1.8h, #1           // v0.8b is VL0, VL1, VL2, VL3, VL4, ...
    ext     v2.16b, v1.16b, v1.16b, #2
    add     v1.8h, v2.8h, v1.8h
    uqrshrn v1.8b, v1.8h, #2          // v1.8b is VL5, VL6, VL7, VL8, VL9
    st1     {v0.s}[0], [x0], x1 // write the first row
    st1     {v1.s}[0], [x0], x1 // write the second row
    ext     v3.8b, v0.8b, v0.8b, #1
    ext     v2.8b, v1.8b, v1.8b, #1
    st1     {v3.s}[0], [x0], x1 // write the third row
    st1     {v2.s}[0], [x0]     // write the fourth row
WELS_ASM_AARCH64_FUNC_END

WELS_ASM_AARCH64_FUNC_BEGIN WelsDecoderI4x4LumaPredVLTop_AArch64_neon
    sxtw    x1, w1
    sub     x2, x0, x1
    ld1     {v0.8b}, [x2]
    dup     v1.8b, v0.b[3]
    mov     v0.s[1], v1.s[0]
    ext     v1.8b, v0.8b, v0.8b, #1
    uaddl   v1.8h, v1.8b, v0.8b
    uqrshrn v0.8b, v1.8h, #1           // v0.8b is VL0, VL1, VL2, VL3, VL4, ...
    ext     v2.16b, v1.16b, v1.16b, #2
    add     v1.8h, v2.8h, v1.8h
    uqrshrn v1.8b, v1.8h, #2          // v1.8b is VL5, VL6, VL7, VL8, VL9
    st1     {v0.s}[0], [x0], x1 // write the first row
    st1     {v1.s}[0], [x0], x1 // write the second row
    ext     v3.8b, v0.8b, v0.8b, #1
    ext     v2.8b, v1.8b, v1.8b, #1
    st1     {v3.s}[0], [x0], x1 // write the third row
    st1     {v2.s}[0], [x0]     // write the fourth row
WELS_ASM_AARCH64_FUNC_END

WELS_ASM_AARCH64_FUNC_BEGIN WelsDecoderI4x4LumaPredVR_AArch64_neon
    sxtw    x1, w1
    sub     x2, x0, x1
    ld1     {v0.s}[1], [x2]
    sub     x2, x2, #1
    ld1     {v0.b}[3], [x2], x1
    ld1     {v0.b}[2], [x2], x1
    ld1     {v0.b}[1], [x2], x1
    ld1     {v0.b}[0], [x2]         // v0.8b l2, l1, l0, lt, t0, t1, t2, t3

    ext     v1.8b, v0.8b, v0.8b, #7
    uaddl   v2.8h, v1.8b, v0.8b     //v2:{X,L2+L1,L1+L0,L0+LT,LT+T0,T0+T1,T1+T2,T2+T3}
    ext     v1.16b, v2.16b, v2.16b, #14
    add     v3.8h, v2.8h, v1.8h     //v3:{X,L2+L1+L1+L0,L1+L0+L0+LT,...T1+T2+T2+T3}

    uqrshrn v3.8b, v3.8h, #2
    uqrshrn v2.8b, v2.8h, #1

    st1     {v2.s}[1], [x0], x1
    st1     {v3.s}[1], [x0], x1

    ext     v2.8b, v2.8b, v2.8b, #7
    ins     v2.b[4], v3.b[3]
    st1     {v2.s}[1], [x0], x1

    ext     v3.8b, v3.8b, v3.8b, #7
    ins     v3.b[4], v3.b[3]
    st1     {v3.s}[1], [x0], x1

WELS_ASM_AARCH64_FUNC_END


WELS_ASM_AARCH64_FUNC_BEGIN WelsDecoderI4x4LumaPredHU_AArch64_neon
    sxtw    x1, w1
    sub     x2, x0, #1
    mov     x3, #3
    mul     x3, x3, x1
    add     x3, x3, x2
    ld1r    {v0.8b}, [x3]
    ld1     {v0.b}[4], [x2], x1
    ld1     {v0.b}[5], [x2], x1
    ld1     {v0.b}[6], [x2], x1     //d0:{L3,L3,L3,L3,L0,L1,L2,L3}

    ext     v1.8b, v0.8b, v0.8b, #1
    uaddl   v2.8h, v0.8b, v1.8b     //v2:{L3+L3,L3+L3,L3+L3,L3+L0,L0+L1,L1+L2,L2+L3,L3+L3}
    ext     v3.16b, v2.16b, v2.16b, #2
    add     v3.8h, v3.8h, v2.8h     //v2:{x, HU1, HU3, HU5, x}

    uqrshrn v2.8b, v2.8h, #1 // HU0, HU2, HU4
    uqrshrn v3.8b, v3.8h, #2 // HU1, HU3, HU5
    zip2    v3.8b, v2.8b, v3.8b // HU0, HU1, HU2, HU3, HU4, HU5
    mov     v3.h[3], v0.h[0] // v0.8b is hu0, hu1, hu2, hu3, hu4, hu5, l3, l3
    ext     v2.8b, v3.8b, v0.8b, #2
    st1     {v3.s}[0], [x0], x1
    st1     {v2.s}[0], [x0], x1
    st1     {v3.s}[1], [x0], x1
    st1     {v0.s}[0], [x0]
WELS_ASM_AARCH64_FUNC_END

WELS_ASM_AARCH64_FUNC_BEGIN WelsDecoderI4x4LumaPredHD_AArch64_neon
    sxtw    x1, w1
    sub     x2, x0, #1
    sub     x2, x2, x1 // x2 points to top left
    ld1     {v0.s}[1], [x2], x1
    ld1     {v0.b}[3], [x2], x1
    ld1     {v0.b}[2], [x2], x1
    ld1     {v0.b}[1], [x2], x1
    ld1     {v0.b}[0], [x2]         // v0.8b: l3, l2, l1, l0, lt, t0, t1, t2
    ext     v1.8b, v0.8b, v0.8b, #1 // v1.8b: l2, l1, l0, lt, t0, t1, t2, l3
    uaddl   v2.8h, v0.8b, v1.8b
    ext     v3.16b, v2.16b, v2.16b, #2
    add     v3.8h, v3.8h, v2.8h
    uqrshrn v2.8b, v2.8h, #1  // hd8, hd6, hd4, hd0, xxx
    uqrshrn v3.8b, v3.8h, #2 // hd9, hd7, hd5, hd1, hd2, hd3
    zip1    v2.8b, v2.8b, v3.8b // hd8, hd9, hd6, hd7, hd4, hd5, hd0, hd1
    mov     v1.h[0], v3.h[2]
    ext     v3.8b, v2.8b, v1.8b, #6
    st1     {v3.s}[0], [x0], x1
    st1     {v2.s}[1], [x0], x1
    ext     v3.8b, v2.8b, v1.8b, #2
    st1     {v3.s}[0], [x0], x1
    st1     {v2.s}[0], [x0]
WELS_ASM_AARCH64_FUNC_END

// for Chroma 8x8
WELS_ASM_AARCH64_FUNC_BEGIN WelsDecoderIChromaPredV_AArch64_neon
    sxtw    x1, w1
    sub     x2, x0, x1
    ld1     {v0.8b}, [x2]
.rept   8
    st1     {v0.8b}, [x0], x1
.endr
WELS_ASM_AARCH64_FUNC_END

WELS_ASM_AARCH64_FUNC_BEGIN WelsDecoderIChromaPredH_AArch64_neon
    sxtw    x1, w1
    sub     x2, x0, #1
.rept 8
    ld1r    {v0.8b}, [x2], x1
    st1     {v0.8b}, [x0], x1
.endr
WELS_ASM_AARCH64_FUNC_END


WELS_ASM_AARCH64_FUNC_BEGIN WelsDecoderIChromaPredDc_AArch64_neon
    sxtw    x1, w1
    sub     x2, x0, x1
    sub     x3, x0, #1
    ld1     {v0.8b}, [x2]
    ld1     {v0.b}[8], [x3], x1
    ld1     {v0.b}[9], [x3], x1
    ld1     {v0.b}[10], [x3], x1
    ld1     {v0.b}[11], [x3], x1
    ld1     {v0.b}[12], [x3], x1
    ld1     {v0.b}[13], [x3], x1
    ld1     {v0.b}[14], [x3], x1
    ld1     {v0.b}[15], [x3]

    uaddlp  v1.8h, v0.16b
    uaddlp  v2.4s, v1.8h
    ins     v3.d[0], v2.d[1]
    add     v3.2s, v2.2s, v3.2s
    urshr   v2.4s, v2.4s, #2
    urshr   v3.2s, v3.2s, #3

    dup     v0.8b, v3.b[0]
    dup     v1.8b, v2.b[4]
    dup     v2.8b, v2.b[12]
    dup     v3.8b, v3.b[4]
    ins     v0.s[1], v1.s[0]
    ins     v2.s[1], v3.s[0]
.rept 4
    st1     {v0.8b}, [x0], x1
.endr
.rept 4
    st1     {v2.8b}, [x0], x1
.endr

WELS_ASM_AARCH64_FUNC_END

WELS_ASM_AARCH64_FUNC_BEGIN WelsDecoderIChromaPredDcTop_AArch64_neon
    sxtw    x1, w1
    sub     x2, x0, x1
    ld1     {v0.8b}, [x2]
    uaddlp  v0.4h, v0.8b
    addp    v0.8h, v0.8h, v0.8h
    dup     v1.8h, v0.h[0]
    dup     v2.8h, v0.h[1]
    mov     v1.D[1], v2.D[0]
    uqrshrn  v1.8b, v1.8h, #2
.rept 8
    st1     {v1.8b}, [x0], x1
.endr
WELS_ASM_AARCH64_FUNC_END

.align 4
intra_1_to_4: .short 17*1, 17*2, 17*3, 17*4, 17*1, 17*2, 17*3, 17*4
intra_m3_to_p4: .short -3, -2, -1, 0, 1, 2, 3, 4

WELS_ASM_AARCH64_FUNC_BEGIN WelsDecoderIChromaPredPlane_AArch64_neon
    sxtw    x1, w1
    sub     x2, x0, x1
    sub     x2, x2, #1
    mov     x3, x2
    // load pTop[2-i] and pLeft[(2-i)*kiStride]
    ld1     {v1.b}[3], [x2], #1
    ld1     {v1.b}[2], [x2], #1
    ld1     {v1.b}[1], [x2], #1
    ld1     {v1.b}[0], [x2], #1
    ld1     {v1.b}[7], [x3], x1
    ld1     {v1.b}[6], [x3], x1
    ld1     {v1.b}[5], [x3], x1
    ld1     {v1.b}[4], [x3], x1
    add     x2, x2, #1
    add     x3, x3, x1
    // load pTop[4+i] and pLeft[(4+i)*kiStride]
    ld1     {v0.b}[0], [x2], #1
    ld1     {v0.b}[1], [x2], #1
    ld1     {v0.b}[2], [x2], #1
    ld1     {v0.b}[3], [x2], #1
    ld1     {v0.b}[4], [x3], x1
    ld1     {v0.b}[5], [x3], x1
    ld1     {v0.b}[6], [x3], x1
    ld1     {v0.b}[7], [x3], x1

    uxtl    v1.8h, v1.8b
    uxtl    v0.8h, v0.8b
    ldr     q2, intra_1_to_4
    ldr     q3, intra_m3_to_p4
    dup     v4.8h, v0.h[3]
    dup     v5.8h, v0.h[7]
    add     v4.8h, v4.8h, v5.8h
    sub     v0.8h, v0.8h, v1.8h
    shl     v4.8h, v4.8h, #4 // v4.8h is a
    mul     v0.8h, v0.8h, v2.8h // v0.h[0-3] is H, v0.h[4-7] is V
    saddlp  v0.4s, v0.8h
    addp    v0.4s, v0.4s, v0.4s // v0.s[0] is H, v0.s[1] is V
    sqrshrn v0.4h, v0.4s, #5
    dup     v1.8h, v0.h[0]      // v1.8h is b
    dup     v0.8h, v0.h[1]      // v0.8h is c
    mla     v4.8h, v1.8h, v3.8h
    mla     v4.8h, v0.8h, v3.h[0]
    sqrshrun v1.8b, v4.8h, #5
    st1     {v1.8b}, [x0], x1
.rept 7
    add     v4.8h, v4.8h, v0.8h
    sqrshrun v1.8b, v4.8h, #5
    st1     {v1.8b}, [x0], x1
.endr
WELS_ASM_AARCH64_FUNC_END

//for Luma 16x16
WELS_ASM_AARCH64_FUNC_BEGIN WelsDecoderI16x16LumaPredV_AArch64_neon
    sxtw    x1, w1
    sub     x2, x0, x1
    ld1     {v0.16b}, [x2]
.rept 16
    st1     {v0.16b}, [x0], x1
.endr
WELS_ASM_AARCH64_FUNC_END

WELS_ASM_AARCH64_FUNC_BEGIN WelsDecoderI16x16LumaPredH_AArch64_neon
    sxtw    x1, w1
    sub     x2, x0, #1
.rept 16
    ld1r    {v0.16b}, [x2], x1
    st1     {v0.16b}, [x0], x1
.endr
WELS_ASM_AARCH64_FUNC_END

WELS_ASM_AARCH64_FUNC_BEGIN WelsDecoderI16x16LumaPredDc_AArch64_neon
    sxtw    x1, w1
    sub     x2, x0, x1
    sub     x3, x0, #1
    ld1     {v0.16b}, [x2]
    ld1     {v1.b}[0], [x3], x1
    ld1     {v1.b}[1], [x3], x1
    ld1     {v1.b}[2], [x3], x1
    ld1     {v1.b}[3], [x3], x1
    ld1     {v1.b}[4], [x3], x1
    ld1     {v1.b}[5], [x3], x1
    ld1     {v1.b}[6], [x3], x1
    ld1     {v1.b}[7], [x3], x1
    ld1     {v1.b}[8], [x3], x1
    ld1     {v1.b}[9], [x3], x1
    ld1     {v1.b}[10], [x3], x1
    ld1     {v1.b}[11], [x3], x1
    ld1     {v1.b}[12], [x3], x1
    ld1     {v1.b}[13], [x3], x1
    ld1     {v1.b}[14], [x3], x1
    ld1     {v1.b}[15], [x3]
    // reduce instruction
    uaddlv    h0, v0.16b
    uaddlv    h1, v1.16b
    add       v0.8h, v0.8h, v1.8h
    uqrshrn    b0, h0, #5
    dup       v0.16b, v0.b[0]
.rept 16
    st1     {v0.16b}, [x0], x1
.endr
WELS_ASM_AARCH64_FUNC_END

WELS_ASM_AARCH64_FUNC_BEGIN WelsDecoderI16x16LumaPredDcTop_AArch64_neon
    sxtw    x1, w1
    sub     x2, x0, x1
    ld1     {v0.16b}, [x2]
    // reduce instruction
    uaddlv    h0, v0.16b
    uqrshrn    v0.8b, v0.8h, 4
    dup       v0.16b, v0.b[0]
.rept 16
    st1     {v0.16b}, [x0], x1
.endr
WELS_ASM_AARCH64_FUNC_END

WELS_ASM_AARCH64_FUNC_BEGIN WelsDecoderI16x16LumaPredDcLeft_AArch64_neon
    sxtw    x1, w1
    sub     x3, x0, #1
    ld1     {v1.b}[0], [x3], x1
    ld1     {v1.b}[1], [x3], x1
    ld1     {v1.b}[2], [x3], x1
    ld1     {v1.b}[3], [x3], x1
    ld1     {v1.b}[4], [x3], x1
    ld1     {v1.b}[5], [x3], x1
    ld1     {v1.b}[6], [x3], x1
    ld1     {v1.b}[7], [x3], x1
    ld1     {v1.b}[8], [x3], x1
    ld1     {v1.b}[9], [x3], x1
    ld1     {v1.b}[10], [x3], x1
    ld1     {v1.b}[11], [x3], x1
    ld1     {v1.b}[12], [x3], x1
    ld1     {v1.b}[13], [x3], x1
    ld1     {v1.b}[14], [x3], x1
    ld1     {v1.b}[15], [x3]
    // reduce instruction
    uaddlv    h1, v1.16b
    uqrshrn    v0.8b, v1.8h, #4
    dup       v0.16b, v0.b[0]
.rept 16
    st1     {v0.16b}, [x0], x1
.endr
WELS_ASM_AARCH64_FUNC_END


.align 4
intra_1_to_8: .short 5, 10, 15, 20, 25, 30, 35, 40
intra_m7_to_p8: .short -7, -6, -5, -4, -3, -2, -1, 0, 1, 2, 3, 4, 5, 6, 7, 8

WELS_ASM_AARCH64_FUNC_BEGIN WelsDecoderI16x16LumaPredPlane_AArch64_neon
    sxtw    x1, w1
    sub     x2, x0, x1
    sub     x2, x2, #1
    mov     x3, x2
    ld1     {v0.8b}, [x3] // v0 low 8 bit in top(reverse order)
    add     x3, x3, #9
    rev64   v0.8b, v0.8b  // reverse v0
    ld1     {v1.8b}, [x3] // v1 high 8 bit in top
    uxtl    v0.8h, v0.8b  // extend to 16 bit integer
    uxtl    v1.8h, v1.8b  // extend to 16 bit integer
    ld1     {v2.b}[7], [x2], x1
    ld1     {v2.b}[6], [x2], x1
    ld1     {v2.b}[5], [x2], x1
    ld1     {v2.b}[4], [x2], x1
    ld1     {v2.b}[3], [x2], x1
    ld1     {v2.b}[2], [x2], x1
    ld1     {v2.b}[1], [x2], x1
    ld1     {v2.b}[0], [x2], x1 // v2.8b low 8 bit in left
    add     x2, x2, x1
    ld1     {v3.b}[0], [x2], x1
    ld1     {v3.b}[1], [x2], x1
    ld1     {v3.b}[2], [x2], x1
    ld1     {v3.b}[3], [x2], x1
    ld1     {v3.b}[4], [x2], x1
    ld1     {v3.b}[5], [x2], x1
    ld1     {v3.b}[6], [x2], x1
    ld1     {v3.b}[7], [x2]      // v3.8b high 8bit in left
    uxtl    v2.8h, v2.8b
    uxtl    v3.8h, v3.8b
    sub     v0.8h, v1.8h, v0.8h
    sub     v2.8h, v3.8h, v2.8h
    ldr     q4, intra_1_to_8
    mul     v0.8h, v0.8h, v4.8h
    mul     v2.8h, v2.8h, v4.8h
    saddlv  s0, v0.8h
    saddlv  s2, v2.8h
    add     v1.8h, v1.8h, v3.8h
    sqrshrn v0.4h, v0.4S, #6  // b is in v0.h[0]
    sqrshrn v2.4h, v2.4S, #6  // c is in v2.h[0]
    shl     v1.8h, v1.8h, #4   // a is in v1.h[7]
    ldr     q4, intra_m7_to_p8
    ldr     q5, intra_m7_to_p8 + 16
    dup     v1.8h, v1.h[7]
    dup     v3.8h, v1.h[7]
    mla     v1.8h, v4.8h, v0.h[0]
    mla     v3.8h, v5.8h, v0.h[0]
    dup     v2.8h, v2.h[0] // v2.8h is [cccccccc]
    mla     v1.8h, v2.8h, v4.h[0]
    mla     v3.8h, v2.8h, v4.h[0]
    sqrshrun v4.8b, v1.8h, #5
    sqrshrun2 v4.16b, v3.8h, #5
    st1     {v4.16b}, [x0], x1
.rept 15
    add     v1.8h, v1.8h, v2.8h
    add     v3.8h, v3.8h, v2.8h
    sqrshrun v4.8b, v1.8h, #5
    sqrshrun2 v4.16b, v3.8h, #5
    st1     {v4.16b}, [x0], x1
.endr
WELS_ASM_AARCH64_FUNC_END
#endif
