;*!
;* \copy
;*     Copyright (c)  2004-2013, Cisco Systems
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
;*  mc_chroma.asm
;*
;*  Abstract
;*      mmx motion compensation for chroma
;*
;*  History
;*      10/13/2004 Created
;*
;*
;*************************************************************************/
%include "asm_inc.asm"

;***********************************************************************
; Local Data (Read Only)
;***********************************************************************

SECTION .rodata align=16

;***********************************************************************
; Various memory constants (trigonometric values or rounding values)
;***********************************************************************

ALIGN 16
h264_d0x20_sse2:
    dw 32,32,32,32,32,32,32,32
ALIGN 16
h264_d0x20_mmx:
    dw 32,32,32,32


;=============================================================================
; Code
;=============================================================================

SECTION .text

;*******************************************************************************
; void McChromaWidthEq4_mmx( const uint8_t *src,
;                           int32_t iSrcStride,
;                           uint8_t *pDst,
;                           int32_t iDstStride,
;                           const uint8_t *pABCD,
;                           int32_t iHeigh );
;*******************************************************************************
WELS_EXTERN McChromaWidthEq4_mmx
    %assign  push_num 0
    LOAD_6_PARA
    SIGN_EXTENSION  r1, r1d
    SIGN_EXTENSION  r3, r3d
    SIGN_EXTENSION  r5, r5d

    movd mm3, [r4]; [eax]
    WELS_Zero mm7
    punpcklbw mm3, mm3
    movq      mm4, mm3
    punpcklwd mm3, mm3
    punpckhwd mm4, mm4

    movq      mm5, mm3
    punpcklbw mm3, mm7
    punpckhbw mm5, mm7

    movq      mm6, mm4
    punpcklbw mm4, mm7
    punpckhbw mm6, mm7

    lea r4, [r0 + r1] ;lea ebx, [esi + eax]
    movd mm0, [r0]
    movd mm1, [r0+1]
    punpcklbw mm0, mm7
    punpcklbw mm1, mm7
.xloop:

    pmullw mm0, mm3
    pmullw mm1, mm5
    paddw  mm0, mm1

    movd  mm1, [r4]
    punpcklbw mm1, mm7
    movq mm2, mm1
    pmullw mm1, mm4
    paddw mm0, mm1

    movd mm1, [r4+1]
    punpcklbw mm1, mm7
    movq mm7, mm1
    pmullw mm1,mm6
    paddw mm0, mm1
    movq mm1,mm7

%ifdef X86_32_PICASM
    pcmpeqw mm7, mm7
    psrlw   mm7, 15
    psllw   mm7, 5
    paddw   mm0, mm7
%else
    paddw mm0, [h264_d0x20_mmx]
%endif
    psrlw mm0, 6

    WELS_Zero mm7
    packuswb mm0, mm7
    movd [r2], mm0

    movq mm0, mm2

    lea r2, [r2 + r3]
    lea r4, [r4 + r1]

    dec r5
    jnz near .xloop
    WELSEMMS
    LOAD_6_PARA_POP
    ret


;*******************************************************************************
; void McChromaWidthEq8_sse2( const uint8_t *pSrc,
;                       int32_t iSrcStride,
;                       uint8_t *pDst,
;                       int32_t iDstStride,
;                       const uint8_t *pABCD,
;                       int32_t iheigh );
;*******************************************************************************
WELS_EXTERN McChromaWidthEq8_sse2
    %assign  push_num 0
    LOAD_6_PARA
    PUSH_XMM 8
    SIGN_EXTENSION  r1, r1d
    SIGN_EXTENSION  r3, r3d
    SIGN_EXTENSION  r5, r5d

    movd xmm3, [r4]
    WELS_Zero xmm7
    punpcklbw  xmm3, xmm3
    punpcklwd  xmm3, xmm3

    movdqa     xmm4, xmm3
    punpckldq  xmm3, xmm3
    punpckhdq  xmm4, xmm4
    movdqa     xmm5, xmm3
    movdqa     xmm6, xmm4

    punpcklbw  xmm3, xmm7
    punpckhbw  xmm5, xmm7
    punpcklbw  xmm4, xmm7
    punpckhbw  xmm6, xmm7

    lea r4, [r0 + r1] ;lea ebx, [esi + eax]
    movq xmm0, [r0]
    movq xmm1, [r0+1]
    punpcklbw xmm0, xmm7
    punpcklbw xmm1, xmm7
.xloop:

    pmullw xmm0, xmm3
    pmullw xmm1, xmm5
    paddw  xmm0, xmm1

    movq  xmm1, [r4]
    punpcklbw xmm1, xmm7
    movdqa xmm2, xmm1
    pmullw xmm1, xmm4
    paddw xmm0, xmm1

    movq xmm1, [r4+1]
    punpcklbw xmm1, xmm7
    movdqa xmm7, xmm1
    pmullw xmm1, xmm6
    paddw xmm0, xmm1
    movdqa xmm1,xmm7

%ifdef X86_32_PICASM
    pcmpeqw xmm7, xmm7
    psrlw   xmm7, 15
    psllw   xmm7, 5
    paddw   xmm0, xmm7
%else
    paddw xmm0, [h264_d0x20_sse2]
%endif
    psrlw xmm0, 6

    WELS_Zero xmm7
    packuswb xmm0, xmm7
    movq [r2], xmm0

    movdqa xmm0, xmm2

    lea r2, [r2 + r3]
    lea r4, [r4 + r1]

    dec r5
    jnz near .xloop

    POP_XMM
    LOAD_6_PARA_POP

    ret




;***********************************************************************
; void McChromaWidthEq8_ssse3( const uint8_t *pSrc,
;                        int32_t iSrcStride,
;                        uint8_t *pDst,
;                        int32_t iDstStride,
;                        const uint8_t *pABCD,
;                        int32_t iHeigh);
;***********************************************************************
WELS_EXTERN McChromaWidthEq8_ssse3
    %assign  push_num 0
    LOAD_6_PARA
    PUSH_XMM 8
    SIGN_EXTENSION  r1, r1d
    SIGN_EXTENSION  r3, r3d
    SIGN_EXTENSION  r5, r5d

    pxor      xmm7, xmm7
    movd   xmm5, [r4]
    punpcklwd xmm5, xmm5
    punpckldq xmm5, xmm5
    movdqa    xmm6, xmm5
    punpcklqdq xmm5, xmm5
    punpckhqdq xmm6, xmm6

    sub r2, r3 ;sub esi, edi
    sub r2, r3
%ifdef X86_32_PICASM
    pcmpeqw xmm7, xmm7
    psrlw   xmm7, 15
    psllw   xmm7, 5
%else
    movdqa xmm7, [h264_d0x20_sse2]
%endif

    movdqu xmm0, [r0]
    movdqa xmm1, xmm0
    psrldq xmm1, 1
    punpcklbw xmm0, xmm1

.hloop_chroma:
    lea r2, [r2+2*r3]

    movdqu xmm2, [r0+r1]
    movdqa xmm3, xmm2
    psrldq xmm3, 1
    punpcklbw xmm2, xmm3
    movdqa      xmm4, xmm2

    pmaddubsw  xmm0, xmm5
    pmaddubsw  xmm2, xmm6
    paddw      xmm0, xmm2
    paddw      xmm0, xmm7
    psrlw      xmm0, 6
    packuswb   xmm0, xmm0
    movq       [r2],xmm0

    lea r0, [r0+2*r1]
    movdqu xmm2, [r0]
    movdqa xmm3, xmm2
    psrldq xmm3, 1
    punpcklbw xmm2, xmm3
    movdqa      xmm0, xmm2

    pmaddubsw  xmm4, xmm5
    pmaddubsw  xmm2, xmm6
    paddw      xmm4, xmm2
    paddw      xmm4, xmm7
    psrlw      xmm4, 6
    packuswb   xmm4, xmm4
    movq       [r2+r3],xmm4

    sub r5, 2
    jnz .hloop_chroma

    POP_XMM
    LOAD_6_PARA_POP

    ret


