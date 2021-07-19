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

WELS_ASM_AARCH64_FUNC_BEGIN DyadicBilinearDownsampler_AArch64_neon

    //Initialize the register
    mov x6, x2
    mov x8, x0
    mov w9, #0
    lsr w5, w5, #1

    //Save the tailer   for the unasigned   size
    smaddl  x7, w1, w5, x0
    ld1 {v4.16b}, [x7]

    add x7, x2, w3, sxtw
    //processing a colume   data
comp_ds_bilinear_loop0:

    ld1     {v0.16b, v1.16b}, [x2], #32
    ld1     {v2.16b, v3.16b}, [x7], #32
    uzp1    v4.16b, v0.16b, v1.16b
    uzp2    v5.16b, v0.16b, v1.16b
    uzp1    v6.16b, v2.16b, v3.16b
    uzp2    v7.16b, v2.16b, v3.16b
    urhadd  v0.16b, v4.16b, v5.16b
    urhadd  v1.16b, v6.16b, v7.16b
    urhadd  v2.16b, v0.16b, v1.16b
    st1     {v2.16b}, [x0], #16
    add     w9, w9, #32

    cmp     w9, w4
    b.cc    comp_ds_bilinear_loop0

    mov     w9, #0
    add     x6, x6, w3, sxtw #1
    mov     x2, x6
    add     x7, x2, w3, sxtw
    add     x8, x8, w1, sxtw
    mov     x0, x8
    sub     w5, w5, #1

    cbnz    w5, comp_ds_bilinear_loop0

    //restore   the tailer for the unasigned size
    st1     {v4.16b}, [x0]

WELS_ASM_AARCH64_FUNC_END

WELS_ASM_AARCH64_FUNC_BEGIN DyadicBilinearDownsamplerWidthx32_AArch64_neon
    sub     w9, w3, w4
    sub     w1, w1, w4, lsr #1
    lsr     w5, w5, #1

    //processing a colume   data
comp_ds_bilinear_w_x32_loop0:

    lsr     w6, w4, #5
    add     x7, x2, w3, sxtw
    //processing a line data
comp_ds_bilinear_w_x32_loop1:

    ld1     {v0.16b, v1.16b}, [x2], #32
    ld1     {v2.16b, v3.16b}, [x7], #32
    uzp1    v4.16b, v0.16b, v1.16b
    uzp2    v5.16b, v0.16b, v1.16b
    uzp1    v6.16b, v2.16b, v3.16b
    uzp2    v7.16b, v2.16b, v3.16b
    urhadd  v0.16b, v4.16b, v5.16b
    urhadd  v1.16b, v6.16b, v7.16b
    urhadd  v2.16b, v0.16b, v1.16b
    st1     {v2.16b}, [x0], #16

    sub     w6, w6, #1
    cbnz    w6, comp_ds_bilinear_w_x32_loop1

    add     x2, x7, w9, sxtw
    add     x0, x0, w1, sxtw
    sub     w5, w5, #1
    cbnz    w5, comp_ds_bilinear_w_x32_loop0
WELS_ASM_AARCH64_FUNC_END

WELS_ASM_AARCH64_FUNC_BEGIN DyadicBilinearOneThirdDownsampler_AArch64_neon

    //Initialize the register
    mov x6, x2
    mov x8, x0
    mov w9, #0

    //Save the tailer   for the unasigned   size
    smaddl  x7, w1, w5, x0
    ld1 {v16.16b}, [x7]

    add x7, x2, w3, sxtw
    //processing a colume   data
comp_ds_bilinear_onethird_loop0:

    ld3     {v0.16b, v1.16b, v2.16b}, [x2], #48
    ld3     {v4.16b, v5.16b, v6.16b}, [x7], #48

    uaddl   v2.8h, v0.8b, v1.8b
    uaddl2  v3.8h, v0.16b, v1.16b
    uaddl   v6.8h, v4.8b, v5.8b
    uaddl2  v7.8h, v4.16b, v5.16b
    urshr   v2.8h, v2.8h, #1
    urshr   v3.8h, v3.8h, #1
    urshr   v6.8h, v6.8h, #1
    urshr   v7.8h, v7.8h, #1

    urhadd  v0.8h, v2.8h, v6.8h
    urhadd  v1.8h, v3.8h, v7.8h
    xtn     v0.8b, v0.8h
    xtn     v1.8b, v1.8h
    st1     {v0.8b,v1.8b}, [x0], #16

    add     w9, w9, #48

    cmp     w9, w4
    b.cc    comp_ds_bilinear_onethird_loop0

    mov     w9, #0
    add     x6, x6, w3, sxtw #1
    add     x6, x6, w3, sxtw
    mov     x2, x6
    add     x7, x2, w3, sxtw
    add     x8, x8, w1, sxtw
    mov     x0, x8
    sub     w5, w5, #1

    cbnz    w5, comp_ds_bilinear_onethird_loop0

    //restore   the tailer for the unasigned size
    st1     {v16.16b}, [x0]
WELS_ASM_AARCH64_FUNC_END
//void DyadicBilinearQuarterDownsampler_AArch64_neon(uint8_t* pDst, const int32_t kiDstStride,
//uint8_t* pSrc, const int32_t kiSrcStride,
//const int32_t kiSrcWidth, const int32_t kiHeight);

WELS_ASM_AARCH64_FUNC_BEGIN DyadicBilinearQuarterDownsampler_AArch64_neon
    //Initialize the register
    mov x6, x2
    mov x8, x0
    mov w9, #0
    lsr w5, w5, #2

    //Save the tailer   for the unasigned   size
    smaddl  x7, w1, w5, x0
    ld1 {v16.16b}, [x7]

    add x7, x2, w3, sxtw
    //processing a colume   data
comp_ds_bilinear_quarter_loop0:

    ld2     {v0.8h, v1.8h}, [x2], #32
    ld2     {v2.8h, v3.8h}, [x2], #32
    ld2     {v4.8h, v5.8h}, [x7], #32
    ld2     {v6.8h, v7.8h}, [x7], #32

    uaddlp  v0.8h, v0.16b
    uaddlp  v1.8h, v2.16b
    uaddlp  v4.8h, v4.16b
    uaddlp  v5.8h, v6.16b
    urshr   v0.8h, v0.8h, #1
    urshr   v1.8h, v1.8h, #1
    urshr   v4.8h, v4.8h, #1
    urshr   v5.8h, v5.8h, #1

    urhadd  v0.8h, v0.8h, v4.8h
    urhadd  v1.8h, v1.8h, v5.8h
    xtn     v0.8b, v0.8h
    xtn     v1.8b, v1.8h
    st1     {v0.8b,v1.8b}, [x0], #16

    add     w9, w9, #64

    cmp     w9, w4
    b.cc    comp_ds_bilinear_quarter_loop0

    mov     w9, #0
    add     x6, x6, w3, sxtw #2
    mov     x2, x6
    add     x7, x2, w3, sxtw
    add     x8, x8, w1, sxtw
    mov     x0, x8
    sub     w5, w5, #1

    cbnz    w5, comp_ds_bilinear_quarter_loop0

    //restore   the tailer for the unasigned size
    st1     {v16.16b}, [x0]
WELS_ASM_AARCH64_FUNC_END

//void GeneralBilinearAccurateDownsampler_AArch64_neon (uint8_t* pDst, const int32_t kiDstStride,
//    const int32_t kiDstWidth, const int32_t kiDstHeight,
//   uint8_t* pSrc, const int32_t kiSrcStride, const uint32_t kuiScaleX, const uint32_t kuiScaleY);
WELS_ASM_AARCH64_FUNC_BEGIN GeneralBilinearAccurateDownsampler_AArch64_neon
    mov     w10, #32767
    and     w8, w6, w10
    mov     w11, #-1
    mul     w12, w11, w8

    dup     v2.4h, w8
    dup     v0.4h, w12
    zip1    v0.4h, v0.4h, v2.4h     // uinc -uinc uinc -uinc

    and     w9, w7, w10
    mul     w12, w11, w9

    dup     v2.4h, w9
    dup     v5.4h, w12
    ins     v5.s[1], v2.s[0]        // vinc vinc -vinc -vinc

    mov     w11, #0x40000000
    mov     w12, #0x3FFF
    add     w11, w11, w12
    dup     v1.2s, w11              //init u  16384 16383 16384 16383

    mov     w8, #16384
    dup     v7.4h, w8
    sub     w11, w8, #1
    dup     v2.4h, w11
    ins     v7.s[0], v2.s[0]        //init v  16384 16384 16383 16383

    eor     v26.16b, v26.16b, v26.16b
    eor     v27.16b, v27.16b, v27.16b
    SIGN_EXTENSION x1, w1
    SIGN_EXTENSION x2, w2
    SIGN_EXTENSION x3, w3
    SIGN_EXTENSION x5, w5
    SIGN_EXTENSION x6, w6
    SIGN_EXTENSION x7, w7

    sub     x1, x1, x2
    sub     x3, x3, #1

_HEIGHT:
    lsr     w11, w8, #15
    mul     w11, w11, w5
    add     x15, x4, w11, sxtw
    add     x12, x15, w5, sxtw

    mov     x9, #16384
    sub     x10, x2, #1
    orr     v6.8b, v1.8b, v1.8b

_WIDTH:
    lsr     x13, x9, #15
    add     x14, x15, x13
    ld2     {v26.b, v27.b}[0], [x14]  //q14: 0000000b0000000a;
    add     x14, x12, x13
    ld2     {v26.b, v27.b}[4], [x14]  //q14: 000d000b000c000a;
    zip1    v28.2s, v26.2s, v27.2s
    zip2    v29.2s, v26.2s, v27.2s

    umull   v20.4s, v6.4h, v7.4h
    umull   v21.2d, v28.2s, v20.2s
    ins     v20.d[0], v20.d[1]
    umlal   v21.2d, v29.2s, v20.2s

    addp    d21, v21.2d
    urshr   d21, d21, #30

    st1     {v21.b}[0], [x0], #1
    add     x9, x9, x6
    add     v6.4h, v6.4h, v0.4h
    shl     v6.4h, v6.4h, #1
    ushr    v6.4h, v6.4h, #1
    sub     x10, x10, #1
    cbnz    x10, _WIDTH

WIDTH_END:
    lsr     x9, x9, #15
    add     x14, x15, x9
    ld1     {v21.b}[0], [x14]
    st1     {v21.b}[0], [x0], #1
    add     w8, w8, w7
    add     x0, x0, x1
    add     v7.4h, v7.4h, v5.4h
    shl     v7.4h, v7.4h, #1
    ushr    v7.4h, v7.4h, #1
    sub     x3, x3, #1
    cbnz    x3, _HEIGHT

LAST_ROW:
    lsr     w8, w8, #15
    mul     w8, w8, w5
    add     x4, x4, w8, sxtw
    mov     x9, #16384

_LAST_ROW_WIDTH:
    mov     x11, x9
    lsr     x11, x11, #15
    add     x3, x4, x11
    ld1     {v21.b}[0], [x3]
    st1     {v21.b}[0], [x0], #1
    add     x9, x9, x6
    sub     x2, x2, #1
    cbnz    x2, _LAST_ROW_WIDTH

WELS_ASM_AARCH64_FUNC_END

#endif
