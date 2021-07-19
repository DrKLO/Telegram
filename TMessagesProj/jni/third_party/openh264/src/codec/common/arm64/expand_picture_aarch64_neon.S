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
//void ExpandPictureLuma_AArch64_neon (uint8_t* pDst, const int32_t kiStride, const int32_t kiPicW, const int32_t kiPicH);
WELS_ASM_AARCH64_FUNC_BEGIN ExpandPictureLuma_AArch64_neon
    SIGN_EXTENSION x1,w1
    SIGN_EXTENSION x2,w2
    SIGN_EXTENSION x3,w3
    mov x7, x0
    mov x8, x3
    add x4, x7, x2
    sub x4, x4, #1
    mov x10, #16
    //For the left and right expand
_expand_picture_luma_loop2:
    sub x5, x7, #32
    add x6, x4, #1
    ld1r {v0.16b}, [x7], x1
    ld1r {v2.16b}, [x4], x1
    mov v1.16b, v0.16b
    mov v3.16b, v2.16b
    st2 {v0.16b, v1.16b}, [x5]
    st2 {v2.16b, v3.16b}, [x6]
    sub x8, x8, #1
    cbnz x8, _expand_picture_luma_loop2
    //for the top and bottom expand
    add x2, x2, #64
    sub x0, x0, #32
    madd x4, x1, x3, x0
    sub x4, x4, x1
_expand_picture_luma_loop0:
    mov x5, #32
    msub x5, x5, x1, x0
    add x6, x4, x1
    ld1 {v0.16b}, [x0], x10
    ld1 {v1.16b}, [x4], x10
    mov x8, #32
_expand_picture_luma_loop1:
    st1 {v0.16b}, [x5], x1
    st1 {v1.16b}, [x6], x1
    sub x8, x8, #1
    cbnz x8, _expand_picture_luma_loop1

    sub x2, x2, #16
    cbnz x2, _expand_picture_luma_loop0
WELS_ASM_AARCH64_FUNC_END

//void ExpandPictureChroma_AArch64_neon (uint8_t* pDst, const int32_t kiStride, const int32_t kiPicW,
//                                       const int32_t kiPicH);
WELS_ASM_AARCH64_FUNC_BEGIN ExpandPictureChroma_AArch64_neon
    //Save the dst
    SIGN_EXTENSION x1,w1
    SIGN_EXTENSION x2,w2
    SIGN_EXTENSION x3,w3
    mov x7, x0
    mov x8, x3
    mov x10, #16
    add x4, x7, x2
    sub x4, x4, #1
    //For the left and right expand
_expand_picture_chroma_loop2:
    sub x5, x7, #16
    add x6, x4, #1

    ld1r {v0.16b}, [x7], x1
    ld1r {v1.16b}, [x4], x1

    st1 {v0.16b}, [x5]
    st1 {v1.16b}, [x6]
    sub x8, x8, #1
    cbnz x8, _expand_picture_chroma_loop2

    //for the top and bottom expand
    add x2, x2, #32
    //
    mov x9, x2
    mov x11, #15
    bic x2, x2, x11
    //
    sub x0, x0, #16
    madd x4, x1, x3, x0
    sub x4, x4, x1
_expand_picture_chroma_loop0:
    mov x5, #16
    msub x5, x5, x1, x0
    add x6, x4, x1
    ld1 {v0.16b}, [x0], x10
    ld1 {v1.16b}, [x4], x10

    mov x8, #16
_expand_picture_chroma_loop1:
    st1 {v0.16b}, [x5], x1
    st1 {v1.16b}, [x6], x1
    sub x8, x8, #1
    cbnz x8, _expand_picture_chroma_loop1

    sub x2, x2, #16
    cbnz x2, _expand_picture_chroma_loop0

    and x9, x9, #15
    sub x9, x9, #8
    cbnz x9, _expand_picture_chroma_end
    mov x5, #16
    msub x5, x5, x1, x0
    add x6, x4, x1
    ld1 {v0.8b}, [x0]
    ld1 {v1.8b}, [x4]

    mov x8, #16
_expand_picture_chroma_loop3:
    st1 {v0.8b}, [x5], x1
    st1 {v1.8b}, [x6], x1
    sub x8, x8, #1
    cbnz x8, _expand_picture_chroma_loop3
_expand_picture_chroma_end:

WELS_ASM_AARCH64_FUNC_END
#endif

