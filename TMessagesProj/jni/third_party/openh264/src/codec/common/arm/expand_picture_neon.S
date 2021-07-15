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

#ifdef HAVE_NEON
#include "arm_arch_common_macro.S"


WELS_ASM_FUNC_BEGIN ExpandPictureLuma_neon
    stmdb sp!, {r4-r8}
    //Save the dst
    mov r7, r0
    mov r8, r3

    add r4, r7, r2
    sub r4, #1
    //For the left and right expand
_expand_picture_luma_loop2:
    sub r5, r7, #32
    add r6, r4, #1

    vld1.8 {d0[], d1[]}, [r7], r1
    vld1.8 {d2[], d3[]}, [r4], r1

    vst1.8 {q0}, [r5]!
    vst1.8 {q0}, [r5]
    vst1.8 {q1}, [r6]!
    vst1.8 {q1}, [r6]
    subs r8, #1
    bne _expand_picture_luma_loop2

    //for the top and bottom expand
    add r2, #64
    sub r0, #32
    mla r4, r1, r3, r0
    sub r4, r1
_expand_picture_luma_loop0:
    mov r5, #32
    mls r5, r5, r1, r0
    add r6, r4, r1
    vld1.8 {q0}, [r0]!
    vld1.8 {q1}, [r4]!

    mov r8, #32
_expand_picture_luma_loop1:
    vst1.8 {q0}, [r5], r1
    vst1.8 {q1}, [r6], r1
    subs r8, #1
    bne _expand_picture_luma_loop1

    subs r2, #16
    bne _expand_picture_luma_loop0

    //vldreq.32 d0, [r0]

    ldmia sp!, {r4-r8}
WELS_ASM_FUNC_END


WELS_ASM_FUNC_BEGIN ExpandPictureChroma_neon
    stmdb sp!, {r4-r9}
    //Save the dst
    mov r7, r0
    mov r8, r3

    add r4, r7, r2
    sub r4, #1
    //For the left and right expand
_expand_picture_chroma_loop2:
    sub r5, r7, #16
    add r6, r4, #1

    vld1.8 {d0[], d1[]}, [r7], r1
    vld1.8 {d2[], d3[]}, [r4], r1

    vst1.8 {q0}, [r5]
    vst1.8 {q1}, [r6]
    subs r8, #1
    bne _expand_picture_chroma_loop2

    //for the top and bottom expand
    add r2, #32
    mov r9, r2
    bic r2, #15
    sub r0, #16
    mla r4, r1, r3, r0
    sub r4, r1
_expand_picture_chroma_loop0:
    mov r5, #16
    mls r5, r5, r1, r0
    add r6, r4, r1
    vld1.8 {q0}, [r0]!
    vld1.8 {q1}, [r4]!

    mov r8, #16
_expand_picture_chroma_loop1:
    vst1.8 {q0}, [r5], r1
    vst1.8 {q1}, [r6], r1
    subs r8, #1
    bne _expand_picture_chroma_loop1

    subs r2, #16
    bne _expand_picture_chroma_loop0

    //vldreq.32 d0, [r0]

    and r9, #15
    cmp r9, #8
    bne _expand_picture_chroma_end
    mov r5, #16
    mls r5, r5, r1, r0
    add r6, r4, r1
    vld1.8 {d0}, [r0]!
    vld1.8 {d2}, [r4]!
    mov r8, #16
_expand_picture_chroma_loop3:
    vst1.8 {d0}, [r5], r1
    vst1.8 {d2}, [r6], r1
    subs r8, #1
    bne _expand_picture_chroma_loop3
_expand_picture_chroma_end:

    ldmia sp!, {r4-r9}
WELS_ASM_FUNC_END

#endif
