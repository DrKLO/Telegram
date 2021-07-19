;*!
;* \copy
;*     Copyright (c)  2009-2013, Cisco Systems
;*     All rights reserved.
;*
;*     Redistribution and use in source and binary forms, with or without
;*     modification, are permitted provided that the following conditions
;*     are met:
;*
;*        * Redistributions of source code must retain the above copyright
;*          notice, this list of conditions and the following disclaimer.
;*
;*        * Redistributions in binary form must reproduce the above copyright
;*          notice, this list of conditions and the following disclaimer in
;*          the documentation and/or other materials provided with the
;*          distribution.
;*
;*     THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
;*     "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT
;*     LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS
;*     FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE
;*     COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT,
;*     INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING,
;*     BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
;*     LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER
;*     CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT
;*     LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN
;*     ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
;*     POSSIBILITY OF SUCH DAMAGE.
;*
;*
;*  memzero.asm
;*
;*  Abstract
;*
;*
;*  History
;*      9/16/2009 Created
;*
;*
;*************************************************************************/

%include "asm_inc.asm"
;***********************************************************************
; Code
;***********************************************************************

SECTION .text

;***********************************************************************
;void WelsPrefetchZero_mmx(int8_t const*_A);
;***********************************************************************
WELS_EXTERN WelsPrefetchZero_mmx
    %assign  push_num 0
    LOAD_1_PARA
    prefetchnta [r0]
    ret


;***********************************************************************
;   void WelsSetMemZeroAligned64_sse2(void *dst, int32_t size)
;***********************************************************************
WELS_EXTERN WelsSetMemZeroAligned64_sse2

    %assign  push_num 0
    LOAD_2_PARA
    SIGN_EXTENSION r1, r1d
    neg     r1

    pxor    xmm0,       xmm0
.memzeroa64_sse2_loops:
    movdqa  [r0],       xmm0
    movdqa  [r0+16],    xmm0
    movdqa  [r0+32],    xmm0
    movdqa  [r0+48],    xmm0
    add     r0, 0x40

    add r1, 0x40
    jnz near .memzeroa64_sse2_loops

    ret

;***********************************************************************
;   void WelsSetMemZeroSize64_mmx(void *dst, int32_t size)
;***********************************************************************
WELS_EXTERN WelsSetMemZeroSize64_mmx

    %assign  push_num 0
    LOAD_2_PARA
    SIGN_EXTENSION r1, r1d
    neg     r1

    pxor    mm0,        mm0
.memzero64_mmx_loops:
    movq    [r0],       mm0
    movq    [r0+8], mm0
    movq    [r0+16],    mm0
    movq    [r0+24],    mm0
    movq    [r0+32],    mm0
    movq    [r0+40],    mm0
    movq    [r0+48],    mm0
    movq    [r0+56],    mm0
    add     r0,     0x40

    add r1, 0x40
    jnz near .memzero64_mmx_loops

    WELSEMMS
    ret

;***********************************************************************
;   void WelsSetMemZeroSize8_mmx(void *dst, int32_t size)
;***********************************************************************
WELS_EXTERN WelsSetMemZeroSize8_mmx

    %assign  push_num 0
    LOAD_2_PARA
    SIGN_EXTENSION r1, r1d
    neg     r1
    pxor    mm0,        mm0

.memzero8_mmx_loops:
    movq    [r0],       mm0
    add     r0,     0x08

    add     r1,     0x08
    jnz near .memzero8_mmx_loops

    WELSEMMS
    ret


