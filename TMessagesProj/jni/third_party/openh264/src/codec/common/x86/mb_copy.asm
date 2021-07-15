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
;*  mb_copy.asm
;*
;*  Abstract
;*      mb_copy and mb_copy1
;*
;*  History
;*      15/09/2009 Created
;*      12/28/2009 Modified with larger throughput
;*      12/29/2011 Tuned WelsCopy16x16NotAligned_sse2, added UpdateMbMv_sse2 WelsCopy16x8NotAligned_sse2,
;*                 WelsCopy16x8_mmx, WelsCopy8x16_mmx etc;
;*
;*
;*********************************************************************************************/
%include "asm_inc.asm"

%ifdef __NASM_VER__
    %use smartalign
%endif

;***********************************************************************
; Macros and other preprocessor constants
;***********************************************************************

;***********************************************************************
; Code
;***********************************************************************

SECTION .text


;***********************************************************************
; void WelsCopy16x16_sse2(  uint8_t* Dst,
;                           int32_t  iStrideD,
;                           uint8_t* Src,
;                           int32_t  iStrideS )
;***********************************************************************
WELS_EXTERN WelsCopy16x16_sse2

    push r4
    push r5
    %assign  push_num 2
    LOAD_4_PARA
    PUSH_XMM 8
    SIGN_EXTENSION r1, r1d
    SIGN_EXTENSION r3, r3d

    lea r4, [r1+2*r1]   ;ebx, [eax+2*eax]   ; x3
    lea r5, [r3+2*r3]   ;edx, [ecx+2*ecx]   ; x3

    movdqa xmm0, [r2]
    movdqa xmm1, [r2+r3]
    movdqa xmm2, [r2+2*r3]
    movdqa xmm3, [r2+r5]
    lea r2, [r2+4*r3]
    movdqa xmm4, [r2]
    movdqa xmm5, [r2+r3]
    movdqa xmm6, [r2+2*r3]
    movdqa xmm7, [r2+r5]
    lea r2, [r2+4*r3]

    movdqa [r0], xmm0
    movdqa [r0+r1], xmm1
    movdqa [r0+2*r1], xmm2
    movdqa [r0+r4], xmm3
    lea r0, [r0+4*r1]
    movdqa [r0], xmm4
    movdqa [r0+r1], xmm5
    movdqa [r0+2*r1], xmm6
    movdqa [r0+r4], xmm7
    lea r0, [r0+4*r1]

    movdqa xmm0, [r2]
    movdqa xmm1, [r2+r3]
    movdqa xmm2, [r2+2*r3]
    movdqa xmm3, [r2+r5]
    lea r2, [r2+4*r3]
    movdqa xmm4, [r2]
    movdqa xmm5, [r2+r3]
    movdqa xmm6, [r2+2*r3]
    movdqa xmm7, [r2+r5]

    movdqa [r0], xmm0
    movdqa [r0+r1], xmm1
    movdqa [r0+2*r1], xmm2
    movdqa [r0+r4], xmm3
    lea r0, [r0+4*r1]
    movdqa [r0], xmm4
    movdqa [r0+r1], xmm5
    movdqa [r0+2*r1], xmm6
    movdqa [r0+r4], xmm7
    POP_XMM
    LOAD_4_PARA_POP
    pop r5
    pop r4
    ret

;***********************************************************************
; void WelsCopy16x16NotAligned_sse2(    uint8_t* Dst,
;                           int32_t  iStrideD,
;                           uint8_t* Src,
;                           int32_t  iStrideS )
;***********************************************************************
; dst can be align with 16 bytes, but not sure about pSrc, 12/29/2011
WELS_EXTERN WelsCopy16x16NotAligned_sse2
    push r4
    push r5
    %assign  push_num 2
    LOAD_4_PARA
    PUSH_XMM 8
    SIGN_EXTENSION r1, r1d
    SIGN_EXTENSION r3, r3d

    lea r4, [r1+2*r1]   ;ebx, [eax+2*eax]   ; x3
    lea r5, [r3+2*r3]   ;edx, [ecx+2*ecx]   ; x3

    movdqu xmm0, [r2]
    movdqu xmm1, [r2+r3]
    movdqu xmm2, [r2+2*r3]
    movdqu xmm3, [r2+r5]
    lea r2, [r2+4*r3]
    movdqu xmm4, [r2]
    movdqu xmm5, [r2+r3]
    movdqu xmm6, [r2+2*r3]
    movdqu xmm7, [r2+r5]
    lea r2, [r2+4*r3]

    movdqa [r0], xmm0
    movdqa [r0+r1], xmm1
    movdqa [r0+2*r1], xmm2
    movdqa [r0+r4], xmm3
    lea r0, [r0+4*r1]
    movdqa [r0], xmm4
    movdqa [r0+r1], xmm5
    movdqa [r0+2*r1], xmm6
    movdqa [r0+r4], xmm7
    lea r0, [r0+4*r1]

    movdqu xmm0, [r2]
    movdqu xmm1, [r2+r3]
    movdqu xmm2, [r2+2*r3]
    movdqu xmm3, [r2+r5]
    lea r2, [r2+4*r3]
    movdqu xmm4, [r2]
    movdqu xmm5, [r2+r3]
    movdqu xmm6, [r2+2*r3]
    movdqu xmm7, [r2+r5]

    movdqa [r0], xmm0
    movdqa [r0+r1], xmm1
    movdqa [r0+2*r1], xmm2
    movdqa [r0+r4], xmm3
    lea r0, [r0+4*r1]
    movdqa [r0], xmm4
    movdqa [r0+r1], xmm5
    movdqa [r0+2*r1], xmm6
    movdqa [r0+r4], xmm7
    POP_XMM
    LOAD_4_PARA_POP
    pop r5
    pop r4
    ret

; , 12/29/2011
;***********************************************************************
; void WelsCopy16x8NotAligned_sse2(uint8_t* Dst,
;                           int32_t  iStrideD,
;                           uint8_t* Src,
;                           int32_t  iStrideS )
;***********************************************************************
WELS_EXTERN WelsCopy16x8NotAligned_sse2
    push r4
    push r5
    %assign  push_num 2
    LOAD_4_PARA
    PUSH_XMM 8
    SIGN_EXTENSION r1, r1d
    SIGN_EXTENSION r3, r3d

    lea r4, [r1+2*r1]   ;ebx, [eax+2*eax]   ; x3
    lea r5, [r3+2*r3]   ;edx, [ecx+2*ecx]   ; x3

    movdqu xmm0, [r2]
    movdqu xmm1, [r2+r3]
    movdqu xmm2, [r2+2*r3]
    movdqu xmm3, [r2+r5]
    lea r2, [r2+4*r3]
    movdqu xmm4, [r2]
    movdqu xmm5, [r2+r3]
    movdqu xmm6, [r2+2*r3]
    movdqu xmm7, [r2+r5]

    movdqa [r0], xmm0
    movdqa [r0+r1], xmm1
    movdqa [r0+2*r1], xmm2
    movdqa [r0+r4], xmm3
    lea r0, [r0+4*r1]
    movdqa [r0], xmm4
    movdqa [r0+r1], xmm5
    movdqa [r0+2*r1], xmm6
    movdqa [r0+r4], xmm7
    POP_XMM
    LOAD_4_PARA_POP
    pop r5
    pop r4
    ret


;***********************************************************************
; void WelsCopy8x16_mmx(uint8_t* Dst,
;                       int32_t  iStrideD,
;                       uint8_t* Src,
;                       int32_t  iStrideS )
;***********************************************************************
WELS_EXTERN WelsCopy8x16_mmx
    %assign  push_num 0
    LOAD_4_PARA
    SIGN_EXTENSION r1, r1d
    SIGN_EXTENSION r3, r3d

    movq mm0, [r2]
    movq mm1, [r2+r3]
    lea r2, [r2+2*r3]
    movq mm2, [r2]
    movq mm3, [r2+r3]
    lea r2, [r2+2*r3]
    movq mm4, [r2]
    movq mm5, [r2+r3]
    lea r2, [r2+2*r3]
    movq mm6, [r2]
    movq mm7, [r2+r3]
    lea r2, [r2+2*r3]

    movq [r0], mm0
    movq [r0+r1], mm1
    lea r0, [r0+2*r1]
    movq [r0], mm2
    movq [r0+r1], mm3
    lea r0, [r0+2*r1]
    movq [r0], mm4
    movq [r0+r1], mm5
    lea r0, [r0+2*r1]
    movq [r0], mm6
    movq [r0+r1], mm7
    lea r0, [r0+2*r1]

    movq mm0, [r2]
    movq mm1, [r2+r3]
    lea r2, [r2+2*r3]
    movq mm2, [r2]
    movq mm3, [r2+r3]
    lea r2, [r2+2*r3]
    movq mm4, [r2]
    movq mm5, [r2+r3]
    lea r2, [r2+2*r3]
    movq mm6, [r2]
    movq mm7, [r2+r3]

    movq [r0], mm0
    movq [r0+r1], mm1
    lea r0, [r0+2*r1]
    movq [r0], mm2
    movq [r0+r1], mm3
    lea r0, [r0+2*r1]
    movq [r0], mm4
    movq [r0+r1], mm5
    lea r0, [r0+2*r1]
    movq [r0], mm6
    movq [r0+r1], mm7

    WELSEMMS
    LOAD_4_PARA_POP
    ret

;***********************************************************************
; void WelsCopy8x8_mmx(  uint8_t* Dst,
;                        int32_t  iStrideD,
;                        uint8_t* Src,
;                        int32_t  iStrideS )
;***********************************************************************
WELS_EXTERN WelsCopy8x8_mmx
    push r4
    %assign  push_num 1
    LOAD_4_PARA
    SIGN_EXTENSION r1, r1d
    SIGN_EXTENSION r3, r3d
    lea r4, [r3+2*r3]   ;edx, [ebx+2*ebx]

    ; to prefetch next loop
    prefetchnta [r2+2*r3]
    prefetchnta [r2+r4]
    movq mm0, [r2]
    movq mm1, [r2+r3]
    lea r2, [r2+2*r3]
    ; to prefetch next loop
    prefetchnta [r2+2*r3]
    prefetchnta [r2+r4]
    movq mm2, [r2]
    movq mm3, [r2+r3]
    lea r2, [r2+2*r3]
    ; to prefetch next loop
    prefetchnta [r2+2*r3]
    prefetchnta [r2+r4]
    movq mm4, [r2]
    movq mm5, [r2+r3]
    lea r2, [r2+2*r3]
    movq mm6, [r2]
    movq mm7, [r2+r3]

    movq [r0], mm0
    movq [r0+r1], mm1
    lea r0, [r0+2*r1]
    movq [r0], mm2
    movq [r0+r1], mm3
    lea r0, [r0+2*r1]
    movq [r0], mm4
    movq [r0+r1], mm5
    lea r0, [r0+2*r1]
    movq [r0], mm6
    movq [r0+r1], mm7

    WELSEMMS
    LOAD_4_PARA_POP
    pop r4
    ret

; (dunhuang@cisco), 12/21/2011
;***********************************************************************
; void UpdateMbMv_sse2( SMVUnitXY *pMvBuffer, const SMVUnitXY sMv )
;***********************************************************************
WELS_EXTERN UpdateMbMv_sse2

    %assign  push_num 0
    LOAD_2_PARA

    movd xmm0, r1d  ; _mv
    pshufd xmm1, xmm0, $00
    movdqa [r0     ], xmm1
    movdqa [r0+0x10], xmm1
    movdqa [r0+0x20], xmm1
    movdqa [r0+0x30], xmm1
    ret

;*******************************************************************************
; Macros and other preprocessor constants
;*******************************************************************************

;*******************************************************************************
; Code
;*******************************************************************************

SECTION .text




;*******************************************************************************
; void PixelAvgWidthEq4_mmx( uint8_t *pDst,  int iDstStride,
;                           uint8_t *pSrcA, int iSrcAStride,
;                           uint8_t *pSrcB, int iSrcBStride,
;                           int iHeight );
;*******************************************************************************
WELS_EXTERN PixelAvgWidthEq4_mmx

    %assign  push_num 0
    LOAD_7_PARA

    SIGN_EXTENSION  r1, r1d
    SIGN_EXTENSION  r3, r3d
    SIGN_EXTENSION  r5, r5d
    SIGN_EXTENSION  r6, r6d

ALIGN 4
.height_loop:
    movd        mm0, [r4]
    pavgb       mm0, [r2]
    movd        [r0], mm0

    dec         r6
    lea         r0, [r0+r1]
    lea         r2, [r2+r3]
    lea         r4, [r4+r5]
    jne         .height_loop

    WELSEMMS
    LOAD_7_PARA_POP
    ret


;*******************************************************************************
; void PixelAvgWidthEq8_mmx( uint8_t *pDst,  int iDstStride,
;                           uint8_t *pSrcA, int iSrcAStride,
;                           uint8_t *pSrcB, int iSrcBStride,
;                           int iHeight );
;*******************************************************************************
WELS_EXTERN PixelAvgWidthEq8_mmx
    %assign  push_num 0
    LOAD_7_PARA

    SIGN_EXTENSION  r1, r1d
    SIGN_EXTENSION  r3, r3d
    SIGN_EXTENSION  r5, r5d
    SIGN_EXTENSION  r6, r6d

ALIGN 4
.height_loop:
    movq        mm0, [r2]
    pavgb       mm0, [r4]
    movq        [r0], mm0
    movq        mm0, [r2+r3]
    pavgb       mm0, [r4+r5]
    movq        [r0+r1], mm0

    lea         r2,  [r2+2*r3]
    lea         r4,  [r4+2*r5]
    lea         r0,  [r0+2*r1]

    sub         r6, 2
    jnz         .height_loop

    WELSEMMS
    LOAD_7_PARA_POP
    ret



;*******************************************************************************
; void PixelAvgWidthEq16_sse2( uint8_t *pDst,  int iDstStride,
;                          uint8_t *pSrcA, int iSrcAStride,
;                          uint8_t *pSrcB, int iSrcBStride,
;                          int iHeight );
;*******************************************************************************
WELS_EXTERN PixelAvgWidthEq16_sse2

    %assign  push_num 0
    LOAD_7_PARA
    SIGN_EXTENSION  r1, r1d
    SIGN_EXTENSION  r3, r3d
    SIGN_EXTENSION  r5, r5d
    SIGN_EXTENSION  r6, r6d
ALIGN 4
.height_loop:
    movdqu      xmm0, [r2]
    movdqu      xmm1, [r4]
    pavgb       xmm0, xmm1
    ;pavgb       xmm0, [r4]
    movdqu      [r0], xmm0

    movdqu      xmm0, [r2+r3]
    movdqu      xmm1, [r4+r5]
    pavgb       xmm0, xmm1
    movdqu      [r0+r1], xmm0

    movdqu      xmm0, [r2+2*r3]
    movdqu       xmm1, [r4+2*r5]
    pavgb       xmm0, xmm1
    movdqu      [r0+2*r1], xmm0

    lea         r2, [r2+2*r3]
    lea         r4, [r4+2*r5]
    lea         r0, [r0+2*r1]

    movdqu      xmm0, [r2+r3]
    movdqu      xmm1, [r4+r5]
    pavgb       xmm0, xmm1
    movdqu      [r0+r1], xmm0

    lea         r2, [r2+2*r3]
    lea         r4, [r4+2*r5]
    lea         r0, [r0+2*r1]

    sub         r6, 4
    jne         .height_loop

    WELSEMMS
    LOAD_7_PARA_POP
    ret

; load_instr=%1 store_instr=%2 p_dst=%3 i_dststride=%4 p_src=%5 i_srcstride=%6 cnt=%7 r_tmp=%8,%9 mm_tmp=%10,%11
%macro CopyStrided4N 11
    lea             %8, [3 * %6]
    lea             %9, [3 * %4]
ALIGN 32
%%loop:
    %1              %10, [%5]
    %1              %11, [%5 + %6]
    %2              [%3], %10
    %2              [%3 + %4], %11
    %1              %10, [%5 + 2 * %6]
    %1              %11, [%5 + %8]
    %2              [%3 + 2 * %4], %10
    %2              [%3 + %9], %11
    lea             %5, [%5 + 4 * %6]
    lea             %3, [%3 + 4 * %4]
    sub             %7, 4
    jg              %%loop
%endmacro

;*******************************************************************************
;   void McCopyWidthEq8_mmx( uint8_t *pSrc, int iSrcStride,
;                            uint8_t *pDst, int iDstStride, int iHeight )
;*******************************************************************************
WELS_EXTERN McCopyWidthEq8_mmx
    %assign  push_num 0
%ifdef X86_32
    push            r5
    push            r6
    %assign  push_num 2
%endif
    LOAD_5_PARA

    SIGN_EXTENSION  r1, r1d
    SIGN_EXTENSION  r3, r3d
    SIGN_EXTENSION  r4, r4d

    CopyStrided4N   movq, movq, r2, r3, r0, r1, r4, r5, r6, mm0, mm1

    WELSEMMS
    LOAD_5_PARA_POP
%ifdef X86_32
    pop             r6
    pop             r5
%endif
    ret


;*******************************************************************************
;   void McCopyWidthEq16_sse2( uint8_t *pSrc, int iSrcStride, uint8_t *pDst, int iDstStride, int iHeight )
;*******************************************************************************
;read unaligned memory
%macro SSE_READ_UNA 2
    movq    %1, [%2]
    movhps  %1, [%2+8]
%endmacro

;write unaligned memory
%macro SSE_WRITE_UNA 2
    movq    [%1],   %2
    movhps  [%1+8], %2
%endmacro
WELS_EXTERN McCopyWidthEq16_sse2
    %assign  push_num 0
    LOAD_5_PARA
    SIGN_EXTENSION  r1, r1d
    SIGN_EXTENSION  r3, r3d
    SIGN_EXTENSION  r4, r4d
ALIGN 4
.height_loop:
    SSE_READ_UNA    xmm0, r0
    SSE_READ_UNA    xmm1, r0+r1
    SSE_WRITE_UNA   r2, xmm0
    SSE_WRITE_UNA   r2+r3, xmm1

    sub     r4, 2
    lea     r0, [r0+r1*2]
    lea     r2, [r2+r3*2]
    jnz     .height_loop

    LOAD_5_PARA_POP
    ret


;*******************************************************************************
;   void McCopyWidthEq16_sse3( uint8_t *pSrc, int iSrcStride, uint8_t *pDst, int iDstStride, int iHeight )
;*******************************************************************************
WELS_EXTERN McCopyWidthEq16_sse3
    %assign push_num 0
%ifdef X86_32
    push            r5
    push            r6
    %assign push_num 2
%endif
    LOAD_5_PARA
    SIGN_EXTENSION  r1, r1d
    SIGN_EXTENSION  r3, r3d
    SIGN_EXTENSION  r4, r4d

    CopyStrided4N   lddqu, MOVDQ, r2, r3, r0, r1, r4, r5, r6, xmm0, xmm1

    LOAD_5_PARA_POP
%ifdef X86_32
    pop             r6
    pop             r5
%endif
    ret
