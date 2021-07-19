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
//int32_t SumOf8x8SingleBlock_AArch64_neon (uint8_t* pRef, const int32_t kiRefStride);
WELS_ASM_AARCH64_FUNC_BEGIN SumOf8x8SingleBlock_AArch64_neon
    SIGN_EXTENSION x1,w1
    ld1 {v0.d}[0], [x0], x1
    ld1 {v0.d}[1], [x0], x1
    ld1 {v1.d}[0], [x0], x1
    ld1 {v1.d}[1], [x0], x1
    ld1 {v2.d}[0], [x0], x1
    ld1 {v2.d}[1], [x0], x1
    ld1 {v3.d}[0], [x0], x1
    ld1 {v3.d}[1], [x0]
    uaddlp v0.8h, v0.16b
    uadalp v0.8h, v1.16b
    uadalp v0.8h, v2.16b
    uadalp v0.8h, v3.16b
    uaddlv s0, v0.8h
    mov    x0, v0.d[0]
WELS_ASM_AARCH64_FUNC_END

//int32_t SumOf16x16SingleBlock_AArch64_neon (uint8_t* pRef, const int32_t kiRefStride);
WELS_ASM_AARCH64_FUNC_BEGIN SumOf16x16SingleBlock_AArch64_neon
    SIGN_EXTENSION x1,w1
    ld1 {v0.16b}, [x0], x1
    uaddlp v0.8h, v0.16b
.rept 15
    ld1 {v1.16b}, [x0], x1
    uadalp v0.8h, v1.16b
.endr
    uaddlv s0, v0.8h
    mov    x0, v0.d[0]
WELS_ASM_AARCH64_FUNC_END

//void SumOf8x8BlockOfFrame_AArch64_neon (uint8_t* pRefPicture, const int32_t kiWidth, const int32_t kiHeight,
//                               const int32_t kiRefStride,
//                                uint16_t* pFeatureOfBlock, uint32_t pTimesOfFeatureValue[]);
WELS_ASM_AARCH64_FUNC_BEGIN SumOf8x8BlockOfFrame_AArch64_neon
//(uint8_t* pRefPicture, const int32_t kiWidth, const int32_t kiHeight,const int32_t kiRefStride,uint16_t* pFeatureOfBlock, uint32_t pTimesOfFeatureValue[])
    //x5: pTimesOfFeatureValue
    //x4: pFeatureOfBlock

    SIGN_EXTENSION x1,w1
    SIGN_EXTENSION x2,w2
    SIGN_EXTENSION x3,w3
    mov x8, x0
    mov x6, x1
    add x8, x8, x6
    add x4, x4, x6, lsl #1

    mov x7, x6
_width_loop8x8_1:
    subs x0, x8, x7
    ld1 {v0.d}[0], [x0], x3
    ld1 {v0.d}[1], [x0], x3
    ld1 {v1.d}[0], [x0], x3
    ld1 {v1.d}[1], [x0], x3
    ld1 {v2.d}[0], [x0], x3
    ld1 {v2.d}[1], [x0], x3
    ld1 {v3.d}[0], [x0], x3
    ld1 {v3.d}[1], [x0]
    uaddlp v0.8h, v0.16b
    uadalp v0.8h, v1.16b
    uadalp v0.8h, v2.16b
    uadalp v0.8h, v3.16b
    uaddlv s0, v0.8h

    subs x1, x4, x7, lsl #1
    st1 {v0.h}[0], [x1] // sum -> pFeatureOfBlock[i]
    mov w0, #0
    ins v0.s[1], w0
    mov    x0, v0.d[0]
    add x1, x5, x0, lsl #2
    ldr w0, [x1]
    add w0, w0, #1
    str w0, [x1]
    subs x7, x7, #1
    cbnz x7, _width_loop8x8_1

    add x8, x8, x3
    add x4, x4, x6, lsl #1
    subs x2, x2, #1
    cbz x2, _SumOf8x8BlockOfFrame_AArch64_neon_end

_height_loop8x8:
    mov x7, x6
_width_loop8x8_2:
    subs x0, x8, x7
    subs x1, x4, x7, lsl #1
    subs x9, x1, x6, lsl #1 // last line of pFeatureOfBlock[i]
    ldrh  w10, [x9] // sum of last line of pFeatureOfBlock[i]

    subs x11, x0, x3
    ld1 {v0.d}[1], [x11]
    add x0, x11, x3, lsl #3
    ld1 {v0.d}[0], [x0] //

    uaddlp v0.8h, v0.16b
    addp v0.8h, v0.8h, v1.8h
    uaddlp v0.4s, v0.8h
    umov w11, v0.s[0]
    umov w12, v0.s[1]

    subs w10, w10, w12
    mov x0, #0
    add w0, w10, w11
    strh w0, [x1] // sum -> pFeatureOfBlock[i]
    add x1, x5, x0, lsl #2
    ldr w0, [x1]
    add w0, w0, #1
    str w0, [x1]
    subs x7, x7, #1
    cbnz x7, _width_loop8x8_2

    add x8, x8, x3
    add x4, x4, x6, lsl #1
    subs x2, x2, #1
    cbnz x2, _height_loop8x8
_SumOf8x8BlockOfFrame_AArch64_neon_end:
WELS_ASM_AARCH64_FUNC_END

WELS_ASM_AARCH64_FUNC_BEGIN SumOf16x16BlockOfFrame_AArch64_neon
//(uint8_t* pRefPicture, const int32_t kiWidth, const int32_t kiHeight,const int32_t kiRefStride,uint16_t* pFeatureOfBlock, uint32_t pTimesOfFeatureValue[])
    //x5: pTimesOfFeatureValue
    //x4: pFeatureOfBlock

    SIGN_EXTENSION x1,w1
    SIGN_EXTENSION x2,w2
    SIGN_EXTENSION x3,w3
    mov x8, x0
    mov x6, x1
    add x8, x8, x6
    add x4, x4, x6, lsl #1

    mov x7, x6
_width_loop16x16_1:
    subs x0, x8, x7
    ld1 {v0.16b}, [x0], x3
    uaddlp v0.8h, v0.16b
.rept 15
    ld1 {v1.16b}, [x0], x3
    uadalp v0.8h, v1.16b
.endr
    uaddlv s0, v0.8h

    subs x1, x4, x7, lsl #1
    st1 {v0.h}[0], [x1] // sum -> pFeatureOfBlock[i]
    mov w0, #0
    ins v0.s[1], w0
    mov    x0, v0.d[0]
    add x1, x5, x0, lsl #2
    ldr w0, [x1]
    add w0, w0, #1
    str w0, [x1]
    subs x7, x7, #1
    cbnz x7, _width_loop16x16_1

    add x8, x8, x3
    add x4, x4, x6, lsl #1
    subs x2, x2, #1
    cbz x2, _SumOf16x16BlockOfFrame_AArch64_neon_end

_height_loop16x16:
    mov x7, x6
_width_loop16x16_2:
    subs x0, x8, x7

    subs x1, x4, x7, lsl #1
    subs x9, x1, x6, lsl #1 // last line of pFeatureOfBlock[i]
    ldrh  w10, [x9] // sum of last line of pFeatureOfBlock[i]

    subs x11, x0, x3
    ld1 {v1.16b}, [x11]
    add x0, x11, x3, lsl #4
    ld1 {v0.16b}, [x0] //

    uaddlv h0, v0.16b
    uaddlv h1, v1.16b
    umov w11, v0.h[0]
    umov w12, v1.h[0]

    subs w10, w10, w12
    mov x0, #0
    add w0, w10, w11
    strh w0, [x1] // sum -> pFeatureOfBlock[i]
    add x1, x5, x0, lsl #2
    ldr w0, [x1]
    add w0, w0, #1
    str w0, [x1]
    subs x7, x7, #1
    cbnz x7, _width_loop16x16_2

    add x8, x8, x3
    add x4, x4, x6, lsl #1
    subs x2, x2, #1
    cbnz x2, _height_loop16x16
_SumOf16x16BlockOfFrame_AArch64_neon_end:
WELS_ASM_AARCH64_FUNC_END

WELS_ASM_AARCH64_FUNC_BEGIN InitializeHashforFeature_AArch64_neon
// (uint32_t* pTimesOfFeatureValue, uint16_t* pBuf, const int32_t kiListSize, uint16_t** pLocationOfFeature, uint16_t** pFeatureValuePointerList);
    SIGN_EXTENSION x2,w2
    mov x9, #3
    bic x5, x2, x9
    mov x8, #0
_hash_assign_loop_x4:
    ld1 {v0.16b}, [x0], #16
    shl v0.4s, v0.4s, #2
    addv s1, v0.4s
    umov w7, v1.s[0]
    cbz w7, _hash_assign_with_copy_x4

    ins v2.d[0], x1
    umov w8, v0.s[0]
    add x1, x1, x8
    ins v2.d[1], x1
    umov w8, v0.s[1]
    add x1, x1, x8
    ins v3.d[0], x1
    umov w8, v0.s[2]
    add x1, x1, x8
    ins v3.d[1], x1
    umov w8, v0.s[3]
    add x1, x1, x8
    st1 {v2.16b, v3.16b}, [x3], #32
    st1 {v2.16b, v3.16b}, [x4], #32
    b _assign_next
_hash_assign_with_copy_x4:
    dup  v2.2d, x1
    dup  v3.2d, x1
    st1 {v2.16b, v3.16b}, [x3], #32
    st1 {v2.16b, v3.16b}, [x4], #32

_assign_next:
    subs x5, x5, #4
    cbnz x5, _hash_assign_loop_x4

    and x5, x2, x9
    cbz x5, _hash_assign_end


_hash_assign_loop_x4_rem:
    str x1, [x3], #8
    str x1, [x4], #8
    ldr w8, [x0], #4
    lsl w8, w8, #2
    add x1, x1, x8
    subs x5, x5, #1
    cbnz x5, _hash_assign_loop_x4_rem

_hash_assign_end:
WELS_ASM_AARCH64_FUNC_END

.align 4
mv_x_inc_x4: .short 0x10, 0x10, 0x10, 0x10, 0x00, 0x00, 0x00, 0x00
mv_y_inc_x4: .short 0x04, 0x04, 0x04, 0x04, 0x00, 0x00, 0x00, 0x00
mx_x_offset_x4: .short 0x00, 0x04, 0x08, 0x0c, 0x00, 0x00, 0x00, 0x00

WELS_ASM_AARCH64_FUNC_BEGIN FillQpelLocationByFeatureValue_AArch64_neon
// void  (uint16_t* pFeatureOfBlock, const int32_t kiWidth, const int32_t kiHeight, uint16_t** pFeatureValuePointerList)
    ldr q7, mv_x_inc_x4
    ldr q6, mv_y_inc_x4
    ldr q5, mx_x_offset_x4
    SIGN_EXTENSION x1,w1
    SIGN_EXTENSION x2,w2
    eor v4.16b, v4.16b, v4.16b
    eor v3.16b, v3.16b, v3.16b
    dup v16.2d, x3 // v8->v16

_hash_height_loop:
    mov x7, x1
    mov v2.16b, v5.16b //mx_x_offset_x4

_hash_width_loop:
    ld1 {v0.d}[0], [x0], #8

    ushll v0.4s, v0.4h, #3
    uaddw   v17.2d, v16.2d, v0.2s
    uaddw2  v18.2d, v16.2d, v0.4s
    zip1 v1.8h, v2.8h, v3.8h

    umov x4, v17.d[0]
    ldr x5, [x4]
    umov w6, v1.s[0]
    str w6, [x5]
    add x5, x5, #4
    str x5, [x4]

    umov x4, v17.d[1]
    ldr x5, [x4]
    umov w6, v1.s[1]
    str w6, [x5]
    add x5, x5, #4
    str x5, [x4]

    umov x4, v18.d[0]
    ldr x5, [x4]
    umov w6, v1.s[2]
    str w6, [x5]
    add x5, x5, #4
    str x5, [x4]

    umov x4, v18.d[1]
    ldr x5, [x4]
    umov w6, v1.s[3]
    str w6, [x5]
    add x5, x5, #4
    str x5, [x4]

    add v2.8h, v2.8h, v7.8h
    subs x7, x7, #4
    cbnz x7, _hash_width_loop

    add v3.8h, v3.8h, v6.8h
    subs x2, x2, #1
    cbnz x2, _hash_height_loop
WELS_ASM_AARCH64_FUNC_END
#endif
