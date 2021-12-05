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
;*  satd_sad.asm
;*
;*  Abstract
;*      WelsSampleSatd4x4_sse2
;*      WelsSampleSatd8x8_sse2
;*      WelsSampleSatd16x8_sse2
;*      WelsSampleSatd8x16_sse2
;*      WelsSampleSatd16x16_sse2
;*
;*      WelsSampleSad16x8_sse2
;*      WelsSampleSad16x16_sse2
;*
;*  History
;*      8/5/2009 Created
;*     24/9/2009 modified
;*
;*
;*************************************************************************/

%include "asm_inc.asm"

;***********************************************************************
; Data
;***********************************************************************
%ifdef X86_32_PICASM
SECTION .text align=16
%else
SECTION .rodata align=16
%endif

align 16
HSumSubDB1:   db 1,1,1,1,1,1,1,1,1,-1,1,-1,1,-1,1,-1
align 16
HSumSubDW1:   dw 1,-1,1,-1,1,-1,1,-1
align 16
PDW1:  dw 1,1,1,1,1,1,1,1
align 16
PDQ2:  dw 2,0,0,0,2,0,0,0
align 16
HSwapSumSubDB1:   times 2 db 1, 1, 1, 1, 1, -1, 1, -1

;***********************************************************************
; Code
;***********************************************************************
SECTION .text

;***********************************************************************
;
;Pixel_satd_wxh_sse2 BEGIN
;
;***********************************************************************
%macro MMX_DW_1_2REG 2
    pxor %1, %1
    pcmpeqw %2, %2
    psubw %1, %2
%endmacro

%macro SSE2_SumWHorizon1 2
    movdqa      %2, %1
    psrldq      %2, 8
    paddusw     %1, %2
    movdqa      %2, %1
    psrldq      %2, 4
    paddusw     %1, %2
    movdqa      %2, %1
    psrldq      %2, 2
    paddusw     %1, %2
%endmacro

%macro SSE2_HDMTwo4x4 5 ;in: xmm1,xmm2,xmm3,xmm4  pOut: xmm4,xmm2,xmm1,xmm3
    SSE2_SumSub %1, %2, %5
    SSE2_SumSub %3, %4, %5
    SSE2_SumSub %2, %4, %5
    SSE2_SumSub %1, %3, %5
%endmacro

%macro SSE2_SumAbs4 7
    WELS_AbsW %1, %3
    WELS_AbsW %2, %3
    WELS_AbsW %4, %6
    WELS_AbsW %5, %6
    paddusw       %1, %2
    paddusw       %4, %5
    paddusw       %7, %1
    paddusw       %7, %4
%endmacro

%macro SSE2_SumWHorizon 3
    movhlps     %2, %1          ; x2 = xx xx xx xx d7 d6 d5 d4
    paddw       %1, %2          ; x1 = xx xx xx xx d37 d26 d15 d04
    punpcklwd   %1, %3          ; x1 =  d37  d26 d15 d04
    movhlps     %2, %1          ; x2 = xxxx xxxx d37 d26
    paddd       %1, %2          ; x1 = xxxx xxxx d1357 d0246
    pshuflw     %2, %1, 0x4e    ; x2 = xxxx xxxx d0246 d1357
    paddd       %1, %2          ; x1 = xxxx xxxx xxxx  d01234567
%endmacro

%macro SSE2_GetSatd8x8 0
    SSE2_LoadDiff8P    xmm0,xmm4,xmm7,[r0],[r2]
    SSE2_LoadDiff8P    xmm1,xmm5,xmm7,[r0+r1],[r2+r3]
    lea                 r0, [r0+2*r1]
    lea                 r2, [r2+2*r3]
    SSE2_LoadDiff8P    xmm2,xmm4,xmm7,[r0],[r2]
    SSE2_LoadDiff8P    xmm3,xmm5,xmm7,[r0+r1],[r2+r3]

    SSE2_HDMTwo4x4       xmm0,xmm1,xmm2,xmm3,xmm4
    SSE2_TransTwo4x4W     xmm3,xmm1,xmm0,xmm2,xmm4
    SSE2_HDMTwo4x4       xmm3,xmm1,xmm2,xmm4,xmm5
    SSE2_SumAbs4         xmm4,xmm1,xmm0,xmm2,xmm3,xmm5,xmm6

    lea                 r0,    [r0+2*r1]
    lea                 r2,    [r2+2*r3]
    SSE2_LoadDiff8P    xmm0,xmm4,xmm7,[r0],[r2]
    SSE2_LoadDiff8P    xmm1,xmm5,xmm7,[r0+r1],[r2+r3]
    lea                 r0, [r0+2*r1]
    lea                 r2, [r2+2*r3]
    SSE2_LoadDiff8P    xmm2,xmm4,xmm7,[r0],[r2]
    SSE2_LoadDiff8P    xmm3,xmm5,xmm7,[r0+r1],[r2+r3]

    SSE2_HDMTwo4x4       xmm0,xmm1,xmm2,xmm3,xmm4
    SSE2_TransTwo4x4W     xmm3,xmm1,xmm0,xmm2,xmm4
    SSE2_HDMTwo4x4       xmm3,xmm1,xmm2,xmm4,xmm5
    SSE2_SumAbs4         xmm4,xmm1,xmm0,xmm2,xmm3,xmm5,xmm6
%endmacro

;***********************************************************************
;
;int32_t WelsSampleSatd4x4_sse2( uint8_t *, int32_t, uint8_t *, int32_t );
;
;***********************************************************************
WELS_EXTERN WelsSampleSatd4x4_sse2
    %assign  push_num 0
    LOAD_4_PARA
    PUSH_XMM 8
    SIGN_EXTENSION r1, r1d
    SIGN_EXTENSION r3, r3d
    movd      xmm0, [r0]
    movd      xmm1, [r0+r1]
    lea       r0 , [r0+2*r1]
    movd      xmm2, [r0]
    movd      xmm3, [r0+r1]
    punpckldq xmm0, xmm2
    punpckldq xmm1, xmm3

    movd      xmm4, [r2]
    movd      xmm5, [r2+r3]
    lea       r2 , [r2+2*r3]
    movd      xmm6, [r2]
    movd      xmm7, [r2+r3]
    punpckldq xmm4, xmm6
    punpckldq xmm5, xmm7

    pxor      xmm6, xmm6
    punpcklbw xmm0, xmm6
    punpcklbw xmm1, xmm6
    punpcklbw xmm4, xmm6
    punpcklbw xmm5, xmm6

    psubw     xmm0, xmm4
    psubw     xmm1, xmm5

    movdqa    xmm2, xmm0
    paddw     xmm0, xmm1
    psubw     xmm2, xmm1
    SSE2_XSawp qdq, xmm0, xmm2, xmm3

    movdqa     xmm4, xmm0
    paddw      xmm0, xmm3
    psubw      xmm4, xmm3

    movdqa         xmm2, xmm0
    punpcklwd      xmm0, xmm4
    punpckhwd      xmm4, xmm2

    SSE2_XSawp     dq,  xmm0, xmm4, xmm3
    SSE2_XSawp     qdq, xmm0, xmm3, xmm5

    movdqa         xmm7, xmm0
    paddw          xmm0, xmm5
    psubw          xmm7, xmm5

    SSE2_XSawp     qdq,  xmm0, xmm7, xmm1

    movdqa         xmm2, xmm0
    paddw          xmm0, xmm1
    psubw          xmm2, xmm1

    WELS_AbsW  xmm0, xmm3
    paddusw        xmm6, xmm0
    WELS_AbsW  xmm2, xmm4
    paddusw        xmm6, xmm2
    SSE2_SumWHorizon1  xmm6, xmm4
    movd           retrd,  xmm6
    and            retrd,  0xffff
    shr            retrd,  1
    POP_XMM
    LOAD_4_PARA_POP
    ret

 ;***********************************************************************
 ;
 ;int32_t WelsSampleSatd8x8_sse2( uint8_t *, int32_t, uint8_t *, int32_t, );
 ;
 ;***********************************************************************
WELS_EXTERN WelsSampleSatd8x8_sse2
    %assign  push_num 0
    LOAD_4_PARA
    PUSH_XMM 8
    SIGN_EXTENSION r1, r1d
    SIGN_EXTENSION r3, r3d
    pxor   xmm6,   xmm6
    pxor   xmm7,   xmm7
    SSE2_GetSatd8x8
    psrlw   xmm6,  1
    SSE2_SumWHorizon   xmm6,xmm4,xmm7
    movd    retrd,   xmm6
    POP_XMM
    LOAD_4_PARA_POP
    ret

 ;***********************************************************************
 ;
 ;int32_t WelsSampleSatd8x16_sse2( uint8_t *, int32_t, uint8_t *, int32_t, );
 ;
 ;***********************************************************************
WELS_EXTERN WelsSampleSatd8x16_sse2
    %assign  push_num 0
    LOAD_4_PARA
    PUSH_XMM 8
    SIGN_EXTENSION r1, r1d
    SIGN_EXTENSION r3, r3d
    pxor   xmm6,   xmm6
    pxor   xmm7,   xmm7

    SSE2_GetSatd8x8
    lea    r0,    [r0+2*r1]
    lea    r2,    [r2+2*r3]
    SSE2_GetSatd8x8

    psrlw   xmm6,  1
    SSE2_SumWHorizon   xmm6,xmm4,xmm7
    movd    retrd,   xmm6
    POP_XMM
    LOAD_4_PARA_POP
    ret

;***********************************************************************
;
;int32_t WelsSampleSatd16x8_sse2( uint8_t *, int32_t, uint8_t *, int32_t, );
;
;***********************************************************************
WELS_EXTERN WelsSampleSatd16x8_sse2
    %assign  push_num 0
    LOAD_4_PARA
    PUSH_XMM 8
    SIGN_EXTENSION r1, r1d
    SIGN_EXTENSION r3, r3d
    push r0
    push r2
    pxor   xmm6,   xmm6
    pxor   xmm7,   xmm7

    SSE2_GetSatd8x8

    pop r2
    pop r0
    add    r0,    8
    add    r2,    8
    SSE2_GetSatd8x8

    psrlw   xmm6,  1
    SSE2_SumWHorizon   xmm6,xmm4,xmm7
    movd    retrd,   xmm6
    POP_XMM
    LOAD_4_PARA_POP
    ret

;***********************************************************************
;
;int32_t WelsSampleSatd16x16_sse2( uint8_t *, int32_t, uint8_t *, int32_t, );
;
;***********************************************************************
WELS_EXTERN WelsSampleSatd16x16_sse2
    %assign  push_num 0
    LOAD_4_PARA
    PUSH_XMM 8
    SIGN_EXTENSION r1, r1d
    SIGN_EXTENSION r3, r3d
    push r0
    push r2
    pxor   xmm6,   xmm6
    pxor   xmm7,   xmm7

    SSE2_GetSatd8x8
    lea    r0,    [r0+2*r1]
    lea    r2,    [r2+2*r3]
    SSE2_GetSatd8x8

    pop r2
    pop r0
    add    r0,    8
    add    r2,    8

    SSE2_GetSatd8x8
    lea    r0,    [r0+2*r1]
    lea    r2,    [r2+2*r3]
    SSE2_GetSatd8x8

 ; each column sum of SATD is necessarily even, so we don't lose any precision by shifting first.
    psrlw   xmm6,  1
    SSE2_SumWHorizon   xmm6,xmm4,xmm7
    movd    retrd,   xmm6
    POP_XMM
    LOAD_4_PARA_POP
    ret

;***********************************************************************
;
;Pixel_satd_wxh_sse2 END
;
;***********************************************************************

;***********************************************************************
;
;Pixel_satd_intra_sse2 BEGIN
;
;***********************************************************************


%macro SSE_DB_1_2REG 2
    pxor %1, %1
    pcmpeqw %2, %2
    psubb %1, %2
%endmacro

;***********************************************************************
;
;int32_t WelsSampleSatdThree4x4_sse2( uint8_t *pDec, int32_t iLineSizeDec, uint8_t *pEnc, int32_t iLinesizeEnc,
;                             uint8_t* pRed, int32_t* pBestMode, int32_t, int32_t, int32_t);
;
;***********************************************************************
WELS_EXTERN WelsSampleSatdThree4x4_sse2

%ifdef X86_32
    push r3
    push r4
    push r5
    push r6
    %assign  push_num 4
%else
    %assign  push_num 0
%endif
    PUSH_XMM 8

    mov  r2, arg3
    mov  r3, arg4
    SIGN_EXTENSION r3, r3d

    ; load source 4x4 samples and Hadamard transform
    movd      xmm0, [r2]
    movd      xmm1, [r2+r3]
    lea       r2 , [r2+2*r3]
    movd      xmm2, [r2]
    movd      xmm3, [r2+r3]
    punpckldq xmm0, xmm2
    punpckldq xmm1, xmm3

    pxor      xmm6, xmm6
    punpcklbw xmm0, xmm6
    punpcklbw xmm1, xmm6

    movdqa    xmm2, xmm0
    paddw     xmm0, xmm1
    psubw     xmm2, xmm1
    SSE2_XSawp  qdq, xmm0, xmm2, xmm3

    movdqa    xmm4, xmm0
    paddw     xmm0, xmm3
    psubw     xmm4, xmm3

    movdqa    xmm2, xmm0
    punpcklwd xmm0, xmm4
    punpckhwd xmm4, xmm2

    SSE2_XSawp  dq,  xmm0, xmm4, xmm3
    SSE2_XSawp  qdq, xmm0, xmm3, xmm5

    movdqa    xmm7, xmm0
    paddw     xmm0, xmm5
    psubw     xmm7, xmm5

    SSE2_XSawp  qdq,  xmm0, xmm7, xmm1

    ; Hadamard transform results are saved in xmm0 and xmm2
    movdqa    xmm2, xmm0
    paddw     xmm0, xmm1
    psubw     xmm2, xmm1

    ;load top boundary samples: [a b c d]
    mov r0, arg1
    mov r1, arg2
    SIGN_EXTENSION r1, r1d
    sub r0, r1
%ifdef UNIX64
    push r4
    push r5
%endif

    movzx     r2d,  byte [r0]
    movzx     r3d,  byte [r0+1]
    movzx     r4d,  byte [r0+2]
    movzx     r5d,  byte [r0+3]

    ; get the transform results of top boundary samples: [a b c d]
    add       r3d, r2d ; r3d = a + b
    add       r5d, r4d ; r5d = c + d
    add       r2d, r2d ; r2d = a + a
    add       r4d, r4d ; r4d = c + c
    sub       r2d, r3d ; r2d = a + a - a - b = a - b
    sub       r4d, r5d ; r4d = c + c - c - d = c - d
    add       r5d, r3d ; r5d = (a + b) + (c + d)
    add       r3d, r3d
    sub       r3d, r5d ; r3d = (a + b) - (c + d)
    add       r4d, r2d ; r4d = (a - b) + (c - d)
    add       r2d, r2d
    sub       r2d, r4d ; r2d = (a - b) - (c - d) ; [r5d r3d r2d r4d]

    movdqa    xmm6, xmm0
    movdqa    xmm7, xmm2
    movd      xmm5, r5d ; store the edi for DC mode
    pxor      xmm3, xmm3
    pxor      xmm4, xmm4
    pinsrw    xmm3, r5d, 0
    pinsrw    xmm3, r4d, 4
    psllw     xmm3, 2
    pinsrw    xmm4, r3d, 0
    pinsrw    xmm4, r2d, 4
    psllw     xmm4, 2

    ; get the satd of H
    psubw     xmm0, xmm3
    psubw     xmm2, xmm4

    WELS_AbsW  xmm0, xmm1
    WELS_AbsW  xmm2, xmm1
    paddusw        xmm0, xmm2
    SSE2_SumWHorizon1  xmm0, xmm1 ; satd of V is stored in xmm0

    ;load left boundary samples: [a b c d]'
    add r0, r1

    movzx     r2d,  byte [r0-1]
    movzx     r3d,  byte [r0+r1-1]
    lea       r0 , [r0+2*r1]
    movzx     r4d,  byte [r0-1]
    movzx     r5d,  byte [r0+r1-1]

    ; get the transform results of left boundary samples: [a b c d]'
    add       r3d, r2d ; r3d = a + b
    add       r5d, r4d ; r5d = c + d
    add       r2d, r2d ; r2d = a + a
    add       r4d, r4d ; r4d = c + c
    sub       r2d, r3d ; r2d = a + a - a - b = a - b
    sub       r4d, r5d ; r4d = c + c - c - d = c - d
    add       r5d, r3d ; r5d = (a + b) + (c + d)
    add       r3d, r3d
    sub       r3d, r5d ; r3d = (a + b) - (c + d)
    add       r4d, r2d ; r4d = (a - b) + (c - d)
    add       r2d, r2d
    sub       r2d, r4d ; r2d = (a - b) - (c - d) ; [r5d r3d r2d r4d]

    ; store the transform results in xmm3
    movd      xmm3, r5d
    pinsrw    xmm3, r3d, 1
    pinsrw    xmm3, r2d, 2
    pinsrw    xmm3, r4d, 3
    psllw     xmm3, 2

    ; get the satd of V
    movdqa    xmm2, xmm6
    movdqa    xmm4, xmm7
    psubw     xmm2, xmm3
    WELS_AbsW  xmm2, xmm1
    WELS_AbsW  xmm4, xmm1
    paddusw        xmm2, xmm4
    SSE2_SumWHorizon1  xmm2, xmm1 ; satd of H is stored in xmm2

    ; DC result is stored in xmm1
    add       r5d, 4
    movd      xmm1, r5d
    paddw     xmm1, xmm5
    psrlw     xmm1, 3
    movdqa    xmm5, xmm1
    psllw     xmm1, 4

    ; get the satd of DC
    psubw          xmm6, xmm1
    WELS_AbsW  xmm6, xmm1
    WELS_AbsW  xmm7, xmm1
    paddusw        xmm6, xmm7
    SSE2_SumWHorizon1  xmm6, xmm1 ; satd of DC is stored in xmm6
%ifdef UNIX64
    pop r5
    pop r4
%endif
    ; comparing order: DC H V

    mov  r4, arg5
    movd      r2d, xmm6
    movd      r3d, xmm2
    movd      r6d, xmm0

    and       r2d, 0xffff
    shr       r2d, 1
    and       r3d, 0xffff
    shr       r3d, 1
    and       r6d, 0xffff
    shr       r6d, 1
    add       r2d, dword arg7
    add       r3d, dword arg8
    add       r6d, dword arg9
    cmp       r2w, r3w
    jg near   not_dc
    cmp       r2w, r6w
    jg near   not_dc_h

    ; for DC mode
    movd      r3d, xmm5
    imul      r3d, 0x01010101
    movd      xmm5, r3d
    pshufd    xmm5, xmm5, 0
    movdqa    [r4], xmm5
    mov r5, arg6
    mov       dword [r5], 0x02
    mov retrd, r2d
    POP_XMM
%ifdef X86_32
    pop r6
    pop r5
    pop r4
    pop r3
%endif
    ret

not_dc:
    cmp       r3w, r6w
    jg near   not_dc_h

    ; for H mode
    SSE_DB_1_2REG  xmm6, xmm7
    sub        r0, r1
    sub        r0, r1
    movzx      r6d,  byte [r0-1]
    movd       xmm0, r6d
    pmuludq    xmm0, xmm6

    movzx     r6d,  byte [r0+r1-1]
    movd      xmm1, r6d
    pmuludq   xmm1, xmm6
    punpckldq xmm0, xmm1

    lea       r0,   [r0+r1*2]
    movzx     r6d,  byte [r0-1]
    movd      xmm2, r6d
    pmuludq   xmm2, xmm6

    movzx     r6d,  byte [r0+r1-1]
    movd      xmm3, r6d
    pmuludq   xmm3, xmm6
    punpckldq  xmm2, xmm3
    punpcklqdq xmm0, xmm2

    movdqa    [r4],xmm0

    mov       retrd, r3d
    mov r5, arg6
    mov       dword [r5], 0x01
    POP_XMM
%ifdef X86_32
    pop r6
    pop r5
    pop r4
    pop r3
%endif
    ret
not_dc_h:
    sub        r0, r1
    sub        r0, r1
    sub        r0, r1
    movd      xmm0, [r0]
    pshufd    xmm0, xmm0, 0
    movdqa    [r4],xmm0
    mov       retrd, r6d
    mov r5, arg6
    mov       dword [r5], 0x00
    POP_XMM
%ifdef X86_32
    pop r6
    pop r5
    pop r4
    pop r3
%endif
    ret


%macro SSE41_I16x16Get8WSumSub 3 ;xmm5 HSumSubDB1, xmm6 HSumSubDW1, xmm7 PDW1 : in %1, pOut %1, %3
    pmaddubsw    %1, xmm5
    movdqa       %2, %1
    pmaddwd      %1, xmm7
    pmaddwd      %2, xmm6
    movdqa       %3, %1
    punpckldq    %1, %2
    punpckhdq    %2, %3
    movdqa       %3, %1
    punpcklqdq   %1, %2
    punpckhqdq   %3, %2
    paddd        xmm4, %1 ;for dc
    paddd        xmm4, %3 ;for dc
    packssdw     %1, %3
    psllw        %1, 2
%endmacro
%macro SSE41_ChromaGet8WSumSub 4 ;xmm5 HSumSubDB1, xmm6 HSumSubDW1, xmm7 PDW1 : in %1, pOut %1, %3 : %4 tempsse2
    pmaddubsw    %1, xmm5
    movdqa       %2, %1
    pmaddwd      %1, xmm7
    pmaddwd      %2, xmm6
    movdqa       %3, %1
    punpckldq    %1, %2
    punpckhdq    %2, %3
    movdqa       %3, %1
    punpcklqdq   %1, %2
    punpckhqdq   %3, %2
;    paddd        xmm4, %1 ;for dc
;    paddd        xmm4, %3 ;for dc
    movdqa       %4, %1
    punpcklqdq   %4, %3
    packssdw     %1, %3
    psllw        %1, 2
%endmacro

%macro SSE41_GetX38x4SatdDec 0
    pxor        xmm7,   xmm7
    movq        xmm0,   [r2]
    movq        xmm1,   [r2+r3]
    lea         r2,    [r2+2*r3]
    movq        xmm2,   [r2]
    movq        xmm3,   [r2+r3]
    lea         r2,    [r2+2*r3]
    punpcklbw   xmm0,   xmm7
    punpcklbw   xmm1,   xmm7
    punpcklbw   xmm2,   xmm7
    punpcklbw   xmm3,   xmm7
    SSE2_HDMTwo4x4       xmm0,xmm1,xmm2,xmm3,xmm7
    SSE2_TransTwo4x4W     xmm3,xmm1,xmm0,xmm2,xmm7
    SSE2_HDMTwo4x4       xmm3,xmm1,xmm2,xmm7,xmm0 ;pOut xmm7,xmm1,xmm3,xmm2
    ;doesn't need another transpose
%endmacro

%macro SSE41_GetX38x4SatdV 2
    pxor        xmm0,   xmm0
    pinsrw      xmm0,   word[r6+%2],   0
    pinsrw      xmm0,   word[r6+%2+8], 4
    psubsw      xmm0,   xmm7
    pabsw       xmm0,   xmm0
    paddw       xmm4,   xmm0
    pxor        xmm0,   xmm0
    pinsrw      xmm0,   word[r6+%2+2],  0
    pinsrw      xmm0,   word[r6+%2+10], 4
    psubsw      xmm0,   xmm1
    pabsw       xmm0,   xmm0
    paddw       xmm4,   xmm0
    pxor        xmm0,   xmm0
    pinsrw      xmm0,   word[r6+%2+4],  0
    pinsrw      xmm0,   word[r6+%2+12], 4
    psubsw      xmm0,   xmm3
    pabsw       xmm0,   xmm0
    paddw       xmm4,   xmm0
    pxor        xmm0,   xmm0
    pinsrw      xmm0,   word[r6+%2+6],  0
    pinsrw      xmm0,   word[r6+%2+14], 4
    psubsw      xmm0,   xmm2
    pabsw       xmm0,   xmm0
    paddw       xmm4,   xmm0
%endmacro
%macro SSE41_GetX38x4SatdH  3
    movq        xmm0,   [r6+%3+8*%1]
    punpcklqdq  xmm0,   xmm0
    psubsw      xmm0,   xmm7
    pabsw       xmm0,   xmm0
    paddw       xmm5,   xmm0
    pabsw       xmm1,   xmm1
    pabsw       xmm2,   xmm2
    pabsw       xmm3,   xmm3
    paddw       xmm2,   xmm1;for DC
    paddw       xmm2,   xmm3;for DC
    paddw       xmm5,   xmm2
%endmacro
%macro SSE41_I16X16GetX38x4SatdDC 0
    pxor        xmm0,   xmm0
    movq2dq     xmm0,   mm4
    punpcklqdq  xmm0,   xmm0
    psubsw      xmm0,   xmm7
    pabsw       xmm0,   xmm0
    paddw       xmm6,   xmm0
    paddw       xmm6,   xmm2
%endmacro
%macro SSE41_ChromaGetX38x4SatdDC 1
    shl         %1,     4
    movdqa      xmm0,   [r6+32+%1]
    psubsw      xmm0,   xmm7
    pabsw       xmm0,   xmm0
    paddw       xmm6,   xmm0
    paddw       xmm6,   xmm2
%endmacro
%macro SSE41_I16x16GetX38x4Satd 2
    SSE41_GetX38x4SatdDec
    SSE41_GetX38x4SatdV   %1, %2
    SSE41_GetX38x4SatdH   %1, %2, 32
    SSE41_I16X16GetX38x4SatdDC
%endmacro
%macro SSE41_ChromaGetX38x4Satd 2
    SSE41_GetX38x4SatdDec
    SSE41_GetX38x4SatdV   %1, %2
    SSE41_GetX38x4SatdH   %1, %2, 16
    SSE41_ChromaGetX38x4SatdDC %1
%endmacro
%macro SSE41_HSum8W 3
    pmaddwd     %1, %2
    movhlps     %3, %1
    paddd       %1, %3
    pshuflw     %3, %1,0Eh
    paddd       %1, %3
%endmacro

WELS_EXTERN WelsIntra16x16Combined3Satd_sse41
    %assign  push_num 0
    LOAD_7_PARA
    PUSH_XMM 8
    SIGN_EXTENSION r1, r1d
    SIGN_EXTENSION r3, r3d
    SIGN_EXTENSION r5, r5d

%ifndef X86_32
    push r12
    mov  r12, r2
%endif

    INIT_X86_32_PIC r2
    pxor        xmm4,   xmm4
    movdqa      xmm5,   [pic(HSumSubDB1)]
    movdqa      xmm6,   [pic(HSumSubDW1)]
    movdqa      xmm7,   [pic(PDW1)]
    DEINIT_X86_32_PIC
    sub         r0,    r1
    movdqu      xmm0,   [r0]
    movhlps     xmm1,   xmm0
    punpcklqdq  xmm0,   xmm0
    punpcklqdq  xmm1,   xmm1
    SSE41_I16x16Get8WSumSub xmm0, xmm2, xmm3
    SSE41_I16x16Get8WSumSub xmm1, xmm2, xmm3
    movdqa      [r6],  xmm0 ;V
    movdqa      [r6+16], xmm1
    add         r0,    r1
    pinsrb      xmm0,   byte[r0-1], 0
    pinsrb      xmm0,   byte[r0+r1-1], 1
    lea         r0,    [r0+2*r1]
    pinsrb      xmm0,   byte[r0-1],     2
    pinsrb      xmm0,   byte[r0+r1-1], 3
    lea         r0,    [r0+2*r1]
    pinsrb      xmm0,   byte[r0-1],     4
    pinsrb      xmm0,   byte[r0+r1-1], 5
    lea         r0,    [r0+2*r1]
    pinsrb      xmm0,   byte[r0-1],     6
    pinsrb      xmm0,   byte[r0+r1-1], 7
    lea         r0,    [r0+2*r1]
    pinsrb      xmm0,   byte[r0-1],     8
    pinsrb      xmm0,   byte[r0+r1-1], 9
    lea         r0,    [r0+2*r1]
    pinsrb      xmm0,   byte[r0-1],     10
    pinsrb      xmm0,   byte[r0+r1-1], 11
    lea         r0,    [r0+2*r1]
    pinsrb      xmm0,   byte[r0-1],     12
    pinsrb      xmm0,   byte[r0+r1-1], 13
    lea         r0,    [r0+2*r1]
    pinsrb      xmm0,   byte[r0-1],     14
    pinsrb      xmm0,   byte[r0+r1-1], 15
    movhlps     xmm1,   xmm0
    punpcklqdq  xmm0,   xmm0
    punpcklqdq  xmm1,   xmm1
    SSE41_I16x16Get8WSumSub xmm0, xmm2, xmm3
    SSE41_I16x16Get8WSumSub xmm1, xmm2, xmm3
    movdqa      [r6+32], xmm0 ;H
    movdqa      [r6+48], xmm1
    movd        r0d,    xmm4 ;dc
    add         r0d,    16   ;(sum+16)
    shr         r0d,    5    ;((sum+16)>>5)
    shl         r0d,    4    ;
    movd        mm4,    r0d  ; mm4 copy DC
    pxor        xmm4,   xmm4 ;V
    pxor        xmm5,   xmm5 ;H
    pxor        xmm6,   xmm6 ;DC
%ifdef UNIX64
    push r4
%endif
    mov         r0,    0
    mov         r4,    0

.loop16x16_get_satd:
.loopStart1:
    SSE41_I16x16GetX38x4Satd r0, r4
    inc          r0
    cmp         r0, 4
    jl          .loopStart1
    cmp         r4, 16
    je          .loop16x16_get_satd_end
%ifdef X86_32
    mov r2, arg3
%else
    mov r2, r12
%endif
    add         r2, 8
    mov         r0, 0
    add         r4, 16
    jmp         .loop16x16_get_satd
 .loop16x16_get_satd_end:
    MMX_DW_1_2REG    xmm0, xmm1
    psrlw       xmm4, 1 ;/2
    psrlw       xmm5, 1 ;/2
    psrlw       xmm6, 1 ;/2
    SSE41_HSum8W     xmm4, xmm0, xmm1
    SSE41_HSum8W     xmm5, xmm0, xmm1
    SSE41_HSum8W     xmm6, xmm0, xmm1

%ifdef UNIX64
    pop r4
%endif
    ; comparing order: DC H V
    movd      r3d, xmm6 ;DC
    movd      r1d, xmm5 ;H
    movd      r0d, xmm4 ;V
%ifndef X86_32
    pop r12
%endif
    shl       r5d, 1
    add       r1d, r5d
    add       r3d, r5d
    mov       r4, arg5
    cmp       r3d, r1d
    jge near   not_dc_16x16
    cmp        r3d, r0d
    jge near   not_dc_h_16x16

    ; for DC mode
    mov       dword[r4], 2;I16_PRED_DC
    mov       retrd, r3d
    jmp near return_satd_intra_16x16_x3
not_dc_16x16:
    ; for H mode
    cmp       r1d, r0d
    jge near   not_dc_h_16x16
    mov       dword[r4], 1;I16_PRED_H
    mov       retrd, r1d
    jmp near return_satd_intra_16x16_x3
not_dc_h_16x16:
    ; for V mode
    mov       dword[r4], 0;I16_PRED_V
    mov       retrd, r0d
return_satd_intra_16x16_x3:
    WELSEMMS
    POP_XMM
    LOAD_7_PARA_POP
ret

%macro SSE41_ChromaGetX38x8Satd 0
    movdqa      xmm5,   [pic(HSumSubDB1)]
    movdqa      xmm6,   [pic(HSumSubDW1)]
    movdqa      xmm7,   [pic(PDW1)]
    sub         r0,    r1
    movq        xmm0,   [r0]
    punpcklqdq  xmm0,   xmm0
    SSE41_ChromaGet8WSumSub xmm0, xmm2, xmm3, xmm4
    movdqa      [r6],  xmm0 ;V
    add         r0,    r1
    pinsrb      xmm0,   byte[r0-1], 0
    pinsrb      xmm0,   byte[r0+r1-1], 1
    lea         r0,    [r0+2*r1]
    pinsrb      xmm0,   byte[r0-1],     2
    pinsrb      xmm0,   byte[r0+r1-1], 3
    lea         r0,    [r0+2*r1]
    pinsrb      xmm0,   byte[r0-1],     4
    pinsrb      xmm0,   byte[r0+r1-1], 5
    lea         r0,    [r0+2*r1]
    pinsrb      xmm0,   byte[r0-1],     6
    pinsrb      xmm0,   byte[r0+r1-1], 7
    punpcklqdq  xmm0,   xmm0
    SSE41_ChromaGet8WSumSub xmm0, xmm2, xmm3, xmm1
    movdqa      [r6+16], xmm0 ;H
;(sum+2)>>2
    movdqa      xmm6,   [pic(PDQ2)]
    movdqa      xmm5,   xmm4
    punpckhqdq  xmm5,   xmm1
    paddd       xmm5,   xmm6
    psrld       xmm5,   2
;(sum1+sum2+4)>>3
    paddd       xmm6,   xmm6
    paddd       xmm4,   xmm1
    paddd       xmm4,   xmm6
    psrld       xmm4,   3
;satd *16
    pslld       xmm5,   4
    pslld       xmm4,   4
;temp satd
    movdqa      xmm6,   xmm4
    punpcklqdq  xmm4,   xmm5
    psllq       xmm4,   32
    psrlq       xmm4,   32
    movdqa      [r6+32], xmm4
    punpckhqdq  xmm5,   xmm6
    psllq       xmm5,   32
    psrlq       xmm5,   32
    movdqa      [r6+48], xmm5

    pxor        xmm4,   xmm4 ;V
    pxor        xmm5,   xmm5 ;H
    pxor        xmm6,   xmm6 ;DC
    mov         r0,    0
    SSE41_ChromaGetX38x4Satd r0, 0
    inc             r0
    SSE41_ChromaGetX38x4Satd r0, 0
%endmacro

%macro SSEReg2MMX 3
    movdq2q     %2, %1
    movhlps     %1, %1
    movdq2q     %3, %1
%endmacro
%macro MMXReg2SSE 4
    movq2dq     %1, %3
    movq2dq     %2, %4
    punpcklqdq  %1, %2
%endmacro
;for reduce the code size of WelsIntraChroma8x8Combined3Satd_sse41

WELS_EXTERN WelsIntraChroma8x8Combined3Satd_sse41
    %assign  push_num 0
    LOAD_7_PARA
    PUSH_XMM 8
    SIGN_EXTENSION r1, r1d
    SIGN_EXTENSION r3, r3d
    SIGN_EXTENSION r5, r5d
loop_chroma_satdx3:
    INIT_X86_32_PIC r4
    SSE41_ChromaGetX38x8Satd
    SSEReg2MMX  xmm4, mm0,mm1
    SSEReg2MMX  xmm5, mm2,mm3
    SSEReg2MMX  xmm6, mm5,mm6
    mov r0,     arg8
    mov r2,     arg9

    SSE41_ChromaGetX38x8Satd
    DEINIT_X86_32_PIC

    MMXReg2SSE  xmm0, xmm3, mm0, mm1
    MMXReg2SSE  xmm1, xmm3, mm2, mm3
    MMXReg2SSE  xmm2, xmm3, mm5, mm6

    paddw       xmm4, xmm0
    paddw       xmm5, xmm1
    paddw       xmm6, xmm2

    MMX_DW_1_2REG    xmm0, xmm1
    psrlw       xmm4, 1 ;/2
    psrlw       xmm5, 1 ;/2
    psrlw       xmm6, 1 ;/2
    SSE41_HSum8W     xmm4, xmm0, xmm1
    SSE41_HSum8W     xmm5, xmm0, xmm1
    SSE41_HSum8W     xmm6, xmm0, xmm1
    ; comparing order: DC H V
    movd      r3d, xmm6 ;DC
    movd      r1d, xmm5 ;H
    movd      r0d, xmm4 ;V


    shl       r5d, 1
    add       r1d, r5d
    add       r0d, r5d
    cmp       r3d, r1d
    jge near   not_dc_8x8
    cmp        r3d, r0d
    jge near   not_dc_h_8x8

    ; for DC mode
    mov       dword[r4], 0;I8_PRED_DC
    mov       retrd, r3d
    jmp near return_satd_intra_8x8_x3
not_dc_8x8:
    ; for H mode
    cmp       r1d, r0d
    jge near   not_dc_h_8x8
    mov       dword[r4], 1;I8_PRED_H
    mov       retrd, r1d
    jmp near return_satd_intra_8x8_x3
not_dc_h_8x8:
    ; for V mode
    mov       dword[r4], 2;I8_PRED_V
    mov       retrd, r0d
return_satd_intra_8x8_x3:
    WELSEMMS
    POP_XMM
    LOAD_7_PARA_POP
ret


;***********************************************************************
;
;Pixel_satd_intra_sse2 END
;
;***********************************************************************
%macro SSSE3_Get16BSadHVDC 2
    movd        xmm6,%1
    pshufb      xmm6,xmm1
    movdqa      %1,  xmm6
    movdqa      xmm0,%2
    psadbw      xmm0,xmm7
    paddw       xmm4,xmm0
    movdqa      xmm0,%2
    psadbw      xmm0,xmm5
    paddw       xmm2,xmm0
    psadbw      xmm6,%2
    paddw       xmm3,xmm6
%endmacro
%macro WelsAddDCValue 4
    movzx   %2, byte %1
    mov    %3, %2
    add     %4, %2
%endmacro

;***********************************************************************
;
;Pixel_sad_intra_ssse3 BEGIN
;
;***********************************************************************
WELS_EXTERN WelsIntra16x16Combined3Sad_ssse3
    %assign  push_num 0
    LOAD_7_PARA
    PUSH_XMM 8
    SIGN_EXTENSION r1, r1d
    SIGN_EXTENSION r3, r3d
    SIGN_EXTENSION r5, r5d

    push  r5
    push  r4
    push  r3

    sub    r0,    r1
    movdqa      xmm5,[r0]
    pxor        xmm0,xmm0
    psadbw      xmm0,xmm5
    movhlps     xmm1,xmm0
    paddw       xmm0,xmm1
    movd        r5d, xmm0

    add         r0,r1
    lea         r3,[r1+2*r1]    ;ebx r3
    WelsAddDCValue [r0-1     ], r4d, [r6   ], r5d    ; esi r4d, eax r5d
    WelsAddDCValue [r0-1+r1  ], r4d, [r6+16], r5d
    WelsAddDCValue [r0-1+r1*2], r4d, [r6+32], r5d
    WelsAddDCValue [r0-1+r3  ], r4d, [r6+48], r5d
    lea         r0, [r0+4*r1]
    add         r6, 64
    WelsAddDCValue [r0-1     ], r4d, [r6   ], r5d
    WelsAddDCValue [r0-1+r1  ], r4d, [r6+16], r5d
    WelsAddDCValue [r0-1+r1*2], r4d, [r6+32], r5d
    WelsAddDCValue [r0-1+r3  ], r4d, [r6+48], r5d
    lea         r0, [r0+4*r1]
    add         r6, 64
    WelsAddDCValue [r0-1     ], r4d, [r6   ], r5d
    WelsAddDCValue [r0-1+r1  ], r4d, [r6+16], r5d
    WelsAddDCValue [r0-1+r1*2], r4d, [r6+32], r5d
    WelsAddDCValue [r0-1+r3  ], r4d, [r6+48], r5d
    lea         r0, [r0+4*r1]
    add         r6, 64
    WelsAddDCValue [r0-1     ], r4d, [r6   ], r5d
    WelsAddDCValue [r0-1+r1  ], r4d, [r6+16], r5d
    WelsAddDCValue [r0-1+r1*2], r4d, [r6+32], r5d
    WelsAddDCValue [r0-1+r3  ], r4d, [r6+48], r5d
    sub         r6, 192
    add         r5d,10h
    shr         r5d,5
    movd        xmm7,r5d
    pxor        xmm1,xmm1
    pshufb      xmm7,xmm1
    pxor        xmm4,xmm4
    pxor        xmm3,xmm3
    pxor        xmm2,xmm2
    ;sad begin
    pop   r3
    lea         r4, [r3+2*r3] ;esi r4
    SSSE3_Get16BSadHVDC [r6], [r2]
    SSSE3_Get16BSadHVDC [r6+16], [r2+r3]
    SSSE3_Get16BSadHVDC [r6+32], [r2+2*r3]
    SSSE3_Get16BSadHVDC [r6+48], [r2+r4]
    add         r6, 64
    lea         r2, [r2+4*r3]
    SSSE3_Get16BSadHVDC [r6], [r2]
    SSSE3_Get16BSadHVDC [r6+16], [r2+r3]
    SSSE3_Get16BSadHVDC [r6+32], [r2+2*r3]
    SSSE3_Get16BSadHVDC [r6+48], [r2+r4]
    add         r6, 64
    lea         r2, [r2+4*r3]
    SSSE3_Get16BSadHVDC [r6], [r2]
    SSSE3_Get16BSadHVDC [r6+16], [r2+r3]
    SSSE3_Get16BSadHVDC [r6+32], [r2+2*r3]
    SSSE3_Get16BSadHVDC [r6+48], [r2+r4]
    add         r6, 64
    lea         r2, [r2+4*r3]
    SSSE3_Get16BSadHVDC [r6], [r2]
    SSSE3_Get16BSadHVDC [r6+16], [r2+r3]
    SSSE3_Get16BSadHVDC [r6+32], [r2+2*r3]
    SSSE3_Get16BSadHVDC [r6+48], [r2+r4]

    pop r4
    pop r5
    pslldq      xmm3,4
    por         xmm3,xmm2
    movhlps     xmm1,xmm3
    paddw       xmm3,xmm1
    movhlps     xmm0,xmm4
    paddw       xmm4,xmm0
    ; comparing order: DC H V
    movd        r1d, xmm4 ;DC   ;ebx r1d
    movd        r0d, xmm3 ;V    ;ecx r0d
    psrldq      xmm3, 4
    movd        r2d, xmm3 ;H    ;esi r2d

    ;mov         eax, [esp+36] ;lamda ;eax r5
    shl         r5d, 1
    add         r2d, r5d
    add         r1d, r5d
    ;mov         edx, [esp+32]  ;edx r4
    cmp         r1d, r2d
    jge near   not_dc_16x16_sad
    cmp        r1d, r0d
    jge near   not_dc_h_16x16_sad
    ; for DC mode
    mov       dword[r4], 2;I16_PRED_DC
    mov       retrd, r1d
    sub        r6, 192
%assign x 0
%rep 16
    movdqa    [r6+16*x], xmm7
%assign x x+1
%endrep
    jmp near return_sad_intra_16x16_x3
not_dc_16x16_sad:
    ; for H mode
    cmp       r2d, r0d
    jge near   not_dc_h_16x16_sad
    mov       dword[r4], 1;I16_PRED_H
    mov       retrd, r2d
    jmp near return_sad_intra_16x16_x3
not_dc_h_16x16_sad:
    ; for V mode
    mov       dword[r4], 0;I16_PRED_V
    mov       retrd, r0d
    sub       r6, 192
%assign x 0
%rep 16
    movdqa    [r6+16*x], xmm5
%assign x x+1
%endrep
return_sad_intra_16x16_x3:
    POP_XMM
    LOAD_7_PARA_POP
    ret

;***********************************************************************
;
;Pixel_sad_intra_ssse3 END
;
;***********************************************************************
;***********************************************************************
;
;Pixel_satd_wxh_sse41 BEGIN
;
;***********************************************************************

;SSE4.1
%macro SSE41_GetSatd8x4 0
    movq             xmm0, [r0]
    punpcklqdq       xmm0, xmm0
    pmaddubsw        xmm0, xmm7
    movq             xmm1, [r0+r1]
    punpcklqdq       xmm1, xmm1
    pmaddubsw        xmm1, xmm7
    movq             xmm2, [r2]
    punpcklqdq       xmm2, xmm2
    pmaddubsw        xmm2, xmm7
    movq             xmm3, [r2+r3]
    punpcklqdq       xmm3, xmm3
    pmaddubsw        xmm3, xmm7
    psubsw           xmm0, xmm2
    psubsw           xmm1, xmm3
    movq             xmm2, [r0+2*r1]
    punpcklqdq       xmm2, xmm2
    pmaddubsw        xmm2, xmm7
    movq             xmm3, [r0+r4]
    punpcklqdq       xmm3, xmm3
    pmaddubsw        xmm3, xmm7
    movq             xmm4, [r2+2*r3]
    punpcklqdq       xmm4, xmm4
    pmaddubsw        xmm4, xmm7
    movq             xmm5, [r2+r5]
    punpcklqdq       xmm5, xmm5
    pmaddubsw        xmm5, xmm7
    psubsw           xmm2, xmm4
    psubsw           xmm3, xmm5
    SSE2_HDMTwo4x4   xmm0, xmm1, xmm2, xmm3, xmm4
    pabsw            xmm0, xmm0
    pabsw            xmm2, xmm2
    pabsw            xmm1, xmm1
    pabsw            xmm3, xmm3
    movdqa           xmm4, xmm3
    pblendw          xmm3, xmm1, 0xAA
    pslld            xmm1, 16
    psrld            xmm4, 16
    por              xmm1, xmm4
    pmaxuw           xmm1, xmm3
    paddw            xmm6, xmm1
    movdqa           xmm4, xmm0
    pblendw          xmm0, xmm2, 0xAA
    pslld            xmm2, 16
    psrld            xmm4, 16
    por              xmm2, xmm4
    pmaxuw           xmm0, xmm2
    paddw            xmm6, xmm0
%endmacro

%macro SSSE3_SumWHorizon 4 ;eax, srcSSE, tempSSE, tempSSE
    MMX_DW_1_2REG    %3, %4
    pmaddwd     %2, %3
    movhlps     %4, %2
    paddd       %2, %4
    pshuflw     %4, %2,0Eh
    paddd       %2, %4
    movd        %1, %2
%endmacro
;***********************************************************************
;
;int32_t WelsSampleSatd4x4_sse41( uint8_t *, int32_t, uint8_t *, int32_t );
;
;***********************************************************************
WELS_EXTERN WelsSampleSatd4x4_sse41
    %assign  push_num 0
    INIT_X86_32_PIC r5
    LOAD_4_PARA
    PUSH_XMM 8
    SIGN_EXTENSION r1, r1d
    SIGN_EXTENSION r3, r3d
    movdqa      xmm4,[pic(HSwapSumSubDB1)]
    movd        xmm2,[r2]
    movd        xmm5,[r2+r3]
    shufps      xmm2,xmm5,0
    movd        xmm3,[r2+r3*2]
    lea         r2, [r3*2+r2]
    movd        xmm5,[r2+r3]
    shufps      xmm3,xmm5,0
    movd        xmm0,[r0]
    movd        xmm5,[r0+r1]
    shufps      xmm0,xmm5,0
    movd        xmm1,[r0+r1*2]
    lea         r0, [r1*2+r0]
    movd        xmm5,[r0+r1]
    shufps      xmm1,xmm5,0
    pmaddubsw   xmm0,xmm4
    pmaddubsw   xmm1,xmm4
    pmaddubsw   xmm2,xmm4
    pmaddubsw   xmm3,xmm4
    psubw       xmm0,xmm2
    psubw       xmm1,xmm3
    movdqa      xmm2,xmm0
    paddw       xmm0,xmm1
    psubw       xmm1,xmm2
    movdqa      xmm2,xmm0
    punpcklqdq  xmm0,xmm1
    punpckhqdq  xmm2,xmm1
    movdqa      xmm1,xmm0
    paddw       xmm0,xmm2
    psubw       xmm2,xmm1
    movdqa      xmm1,xmm0
    pblendw     xmm0,xmm2,0AAh
    pslld       xmm2,16
    psrld       xmm1,16
    por         xmm2,xmm1
    pabsw       xmm0,xmm0
    pabsw       xmm2,xmm2
    pmaxsw      xmm0,xmm2
    SSSE3_SumWHorizon retrd, xmm0, xmm5, xmm7
    POP_XMM
    LOAD_4_PARA_POP
    DEINIT_X86_32_PIC
    ret

;***********************************************************************
;
;int32_t WelsSampleSatd8x8_sse41( uint8_t *, int32_t, uint8_t *, int32_t, );
;
;***********************************************************************
WELS_EXTERN WelsSampleSatd8x8_sse41
%ifdef X86_32
    push  r4
    push  r5
%endif
    %assign  push_num 2
    INIT_X86_32_PIC r6
    LOAD_4_PARA
    PUSH_XMM 8
    SIGN_EXTENSION r1, r1d
    SIGN_EXTENSION r3, r3d

    movdqa      xmm7, [pic(HSumSubDB1)]
    lea         r4,  [r1+r1*2]
    lea         r5,  [r3+r3*2]
    pxor        xmm6, xmm6
    SSE41_GetSatd8x4
    lea         r0,  [r0+4*r1]
    lea         r2,  [r2+4*r3]
    SSE41_GetSatd8x4
    SSSE3_SumWHorizon retrd, xmm6, xmm5, xmm7
    POP_XMM
    LOAD_4_PARA_POP
    DEINIT_X86_32_PIC
%ifdef X86_32
    pop  r5
    pop  r4
%endif
    ret

;***********************************************************************
;
;int32_t WelsSampleSatd8x16_sse41( uint8_t *, int32_t, uint8_t *, int32_t, );
;
;***********************************************************************
WELS_EXTERN WelsSampleSatd8x16_sse41
%ifdef X86_32
    push  r4
    push  r5
    push  r6
%endif
    %assign  push_num 3
    LOAD_4_PARA
    PUSH_XMM 8
    SIGN_EXTENSION r1, r1d
    SIGN_EXTENSION r3, r3d

    INIT_X86_32_PIC_NOPRESERVE r4
    movdqa      xmm7, [pic(HSumSubDB1)]
    DEINIT_X86_32_PIC
    lea         r4,  [r1+r1*2]
    lea         r5,  [r3+r3*2]
    pxor        xmm6, xmm6
    mov         r6,    0
loop_get_satd_8x16:
    SSE41_GetSatd8x4
    lea         r0,  [r0+4*r1]
    lea         r2,  [r2+4*r3]
    inc         r6
    cmp         r6,  4
    jl          loop_get_satd_8x16
    SSSE3_SumWHorizon retrd, xmm6, xmm5, xmm7
    POP_XMM
    LOAD_4_PARA_POP
%ifdef X86_32
    pop  r6
    pop  r5
    pop  r4
%endif
    ret

;***********************************************************************
;
;int32_t WelsSampleSatd16x8_sse41( uint8_t *, int32_t, uint8_t *, int32_t, );
;
;***********************************************************************
WELS_EXTERN WelsSampleSatd16x8_sse41
%ifdef X86_32
    push  r4
    push  r5
%endif
    %assign  push_num 2
    INIT_X86_32_PIC r6
    LOAD_4_PARA
    PUSH_XMM 8
    SIGN_EXTENSION r1, r1d
    SIGN_EXTENSION r3, r3d
    push  r0
    push  r2

    movdqa      xmm7, [pic(HSumSubDB1)]
    lea         r4,  [r1+r1*2]
    lea         r5,  [r3+r3*2]
    pxor        xmm6,   xmm6
    SSE41_GetSatd8x4
    lea         r0,  [r0+4*r1]
    lea         r2,  [r2+4*r3]
    SSE41_GetSatd8x4

    pop  r2
    pop  r0
    add         r0,    8
    add         r2,    8
    SSE41_GetSatd8x4
    lea         r0,  [r0+4*r1]
    lea         r2,  [r2+4*r3]
    SSE41_GetSatd8x4
    SSSE3_SumWHorizon retrd, xmm6, xmm5, xmm7
    POP_XMM
    LOAD_4_PARA_POP
    DEINIT_X86_32_PIC
%ifdef X86_32
    pop  r5
    pop  r4
%endif
    ret

;***********************************************************************
;
;int32_t WelsSampleSatd16x16_sse41( uint8_t *, int32_t, uint8_t *, int32_t, );
;
;***********************************************************************

WELS_EXTERN WelsSampleSatd16x16_sse41
%ifdef X86_32
    push  r4
    push  r5
    push  r6
%endif
    %assign  push_num 3
    LOAD_4_PARA
    PUSH_XMM 8
    SIGN_EXTENSION r1, r1d
    SIGN_EXTENSION r3, r3d

    push  r0
    push  r2

    INIT_X86_32_PIC_NOPRESERVE r4
    movdqa      xmm7, [pic(HSumSubDB1)]
    DEINIT_X86_32_PIC
    lea         r4,  [r1+r1*2]
    lea         r5,  [r3+r3*2]
    pxor        xmm6,   xmm6
    mov         r6,    0
loop_get_satd_16x16_left:
    SSE41_GetSatd8x4
    lea         r0,  [r0+4*r1]
    lea         r2,  [r2+4*r3]
    inc         r6
    cmp         r6,  4
    jl          loop_get_satd_16x16_left

    pop  r2
    pop  r0
    add         r0,    8
    add         r2,    8
    mov         r6,    0
loop_get_satd_16x16_right:
    SSE41_GetSatd8x4
    lea         r0,  [r0+4*r1]
    lea         r2,  [r2+4*r3]
    inc         r6
    cmp         r6,  4
    jl          loop_get_satd_16x16_right
    SSSE3_SumWHorizon retrd, xmm6, xmm5, xmm7
    POP_XMM
    LOAD_4_PARA_POP
%ifdef X86_32
    pop  r6
    pop  r5
    pop  r4
%endif
    ret

;***********************************************************************
;
;Pixel_satd_wxh_sse41 END
;
;***********************************************************************

;***********************************************************************
;
;Pixel_satd_wxh_avx2 BEGIN
;
;***********************************************************************

%ifdef HAVE_AVX2
; out=%1 pSrcA=%2 pSrcB=%3 HSumSubDB1_256=%4 ymm_clobber=%5
%macro AVX2_LoadDiffSatd16x1 5
    vbroadcasti128   %1, [%2]
    vpmaddubsw       %1, %1, %4             ; hadamard neighboring horizontal sums and differences
    vbroadcasti128   %5, [%3]
    vpmaddubsw       %5, %5, %4             ; hadamard neighboring horizontal sums and differences
    vpsubw           %1, %1, %5             ; diff srcA srcB
%endmacro

; out=%1 pSrcA=%2 pSrcA+4*iStride=%3 pSrcB=%4 pSrcB+4*iStride=%5 HSumSubDB1_128x2=%6 ymm_clobber=%7,%8
%macro AVX2_LoadDiffSatd8x2 8
    vpbroadcastq     %1, [%2]
    vpbroadcastq     %7, [%3]
    vpblendd         %1, %1, %7, 11110000b
    vpmaddubsw       %1, %1, %6             ; hadamard neighboring horizontal sums and differences
    vpbroadcastq     %7, [%4]
    vpbroadcastq     %8, [%5]
    vpblendd         %7, %7, %8, 11110000b
    vpmaddubsw       %7, %7, %6             ; hadamard neighboring horizontal sums and differences
    vpsubw           %1, %1, %7             ; diff srcA srcB
%endmacro

; in/out=%1,%2,%3,%4 clobber=%5
%macro AVX2_HDMFour4x4 5
    vpsubw           %5, %1, %4             ; s3 = x0 - x3
    vpaddw           %1, %1, %4             ; s0 = x0 + x3
    vpsubw           %4, %2, %3             ; s2 = x1 - x2
    vpaddw           %2, %2, %3             ; s1 = x1 + x2
    vpsubw           %3, %1, %2             ; y2 = s0 - s1
    vpaddw           %1, %1, %2             ; y0 = s0 + s1
    vpaddw           %2, %5, %4             ; y1 = s3 + s2
    vpsubw           %4, %5, %4             ; y3 = s3 - s2
%endmacro

; out=%1 in=%1,%2,%3,%4 clobber=%5
%macro AVX2_SatdFour4x4 5
    AVX2_HDMFour4x4  %1, %2, %3, %4, %5
    vpabsw           %1, %1
    vpabsw           %2, %2
    vpabsw           %3, %3
    vpabsw           %4, %4
    ; second stage of horizontal hadamard.
    ; utilizes that |a + b| + |a - b| = 2 * max(|a|, |b|)
    vpblendw         %5, %1, %2, 10101010b
    vpslld           %2, %2, 16
    vpsrld           %1, %1, 16
    vpor             %2, %2, %1
    vpmaxuw          %2, %2, %5
    vpblendw         %5, %3, %4, 10101010b
    vpslld           %4, %4, 16
    vpsrld           %3, %3, 16
    vpor             %4, %4, %3
    vpmaxuw          %3, %5, %4
    vpaddw           %1, %2, %3
%endmacro

; out=%1 pSrcA=%2 iStrideA=%3 3*iStrideA=%4 pSrcB=%5 iStrideB=%6 3*iStrideB=%7 HSumSubDB1_256=%8 ymm_clobber=%9,%10,%11,%12
%macro AVX2_GetSatd16x4 12
    AVX2_LoadDiffSatd16x1  %1, %2 + 0 * %3, %5 + 0 * %6, %8, %12
    AVX2_LoadDiffSatd16x1  %9, %2 + 1 * %3, %5 + 1 * %6, %8, %12
    AVX2_LoadDiffSatd16x1 %10, %2 + 2 * %3, %5 + 2 * %6, %8, %12
    AVX2_LoadDiffSatd16x1 %11, %2 + 1 * %4, %5 + 1 * %7, %8, %12
    AVX2_SatdFour4x4 %1, %9, %10, %11, %12
%endmacro

; out=%1 pSrcA=%2 iStrideA=%3 3*iStrideA=%4 pSrcB=%5 iStrideB=%6 3*iStrideB=%7 HSumSubDB1_128x2=%8 ymm_clobber=%9,%10,%11,%12,%13
%macro AVX2_GetSatd8x8 13
    AVX2_LoadDiffSatd8x2  %1, %2 + 0 * %3, %2 + 4 * %3, %5 + 0 * %6, %5 + 4 * %6, %8, %12, %13
    AVX2_LoadDiffSatd8x2 %10, %2 + 2 * %3, %2 + 2 * %4, %5 + 2 * %6, %5 + 2 * %7, %8, %12, %13
    add              %2, %3
    add              %5, %6
    AVX2_LoadDiffSatd8x2  %9, %2 + 0 * %3, %2 + 4 * %3, %5 + 0 * %6, %5 + 4 * %6, %8, %12, %13
    AVX2_LoadDiffSatd8x2 %11, %2 + 2 * %3, %2 + 2 * %4, %5 + 2 * %6, %5 + 2 * %7, %8, %12, %13
    AVX2_SatdFour4x4 %1, %9, %10, %11, %12
%endmacro

; d_out=%1 mm_in=%2 mm_clobber=%3
%macro AVX2_SumWHorizon 3
    WELS_DW1_VEX     y%3
    vpmaddwd         y%2, y%2, y%3
    vextracti128     x%3, y%2, 1
    vpaddd           x%2, x%2, x%3
    vpunpckhqdq      x%3, x%2, x%2
    vpaddd           x%2, x%2, x%3
    vpsrldq          x%3, x%2, 4
    vpaddd           x%2, x%2, x%3
    vmovd            %1, x%2
%endmacro

;***********************************************************************
;
;int32_t WelsSampleSatd8x16_avx2( uint8_t *, int32_t, uint8_t *, int32_t, );
;
;***********************************************************************

WELS_EXTERN WelsSampleSatd8x16_avx2
    %assign push_num 0
%ifdef X86_32
    push r4
    %assign push_num 1
%endif
    mov r4, 2                      ; loop cnt
    jmp WelsSampleSatd8x8N_avx2

;***********************************************************************
;
;int32_t WelsSampleSatd8x8_avx2( uint8_t *, int32_t, uint8_t *, int32_t, );
;
;***********************************************************************

WELS_EXTERN WelsSampleSatd8x8_avx2
    %assign push_num 0
%ifdef X86_32
    push           r4
    %assign push_num 1
%endif
    mov            r4, 1           ; loop cnt
                                   ; fall through
WelsSampleSatd8x8N_avx2:
%ifdef X86_32
    push           r5
    push           r6
    %assign push_num push_num+2
%endif
    LOAD_4_PARA
    PUSH_XMM 8
    SIGN_EXTENSION r1, r1d
    SIGN_EXTENSION r3, r3d

    INIT_X86_32_PIC_NOPRESERVE r5
    vbroadcasti128 ymm7, [pic(HSumSubDB1)]
    DEINIT_X86_32_PIC
    lea            r5, [3 * r1]
    lea            r6, [3 * r3]
    vpxor          ymm6, ymm6, ymm6
.loop:
    AVX2_GetSatd8x8 ymm0, r0, r1, r5, r2, r3, r6, ymm7, ymm1, ymm2, ymm3, ymm4, ymm5
    vpaddw         ymm6, ymm6, ymm0
    sub            r4, 1
    jbe            .loop_end
    add            r0, r5
    add            r2, r6
    lea            r0, [r0 + 4 * r1]
    lea            r2, [r2 + 4 * r3]
    jmp            .loop
.loop_end:
    AVX2_SumWHorizon retrd, mm6, mm5
    vzeroupper
    POP_XMM
    LOAD_4_PARA_POP
%ifdef X86_32
    pop            r6
    pop            r5
    pop            r4
%endif
    ret

;***********************************************************************
;
;int32_t WelsSampleSatd16x16_avx2( uint8_t *, int32_t, uint8_t *, int32_t, );
;
;***********************************************************************

WELS_EXTERN WelsSampleSatd16x16_avx2
    %assign push_num 0
%ifdef X86_32
    push r4
    %assign push_num 1
%endif
    mov r4, 4                      ; loop cnt
    jmp WelsSampleSatd16x4N_avx2

;***********************************************************************
;
;int32_t WelsSampleSatd16x8_avx2( uint8_t *, int32_t, uint8_t *, int32_t, );
;
;***********************************************************************

WELS_EXTERN WelsSampleSatd16x8_avx2
    %assign push_num 0
%ifdef X86_32
    push r4
    %assign push_num 1
%endif
    mov r4, 2                      ; loop cnt
                                   ; fall through
WelsSampleSatd16x4N_avx2:
%ifdef X86_32
    push r5
    push r6
    %assign push_num push_num+2
%endif
    LOAD_4_PARA
    PUSH_XMM 7
    SIGN_EXTENSION r1, r1d
    SIGN_EXTENSION r3, r3d

    INIT_X86_32_PIC_NOPRESERVE r5
    vpbroadcastq xmm0, [pic(HSumSubDB1)]
    vpbroadcastq ymm6, [pic(HSumSubDB1 + 8)]
    vpblendd     ymm6, ymm0, ymm6, 11110000b
    DEINIT_X86_32_PIC
    lea          r5, [3 * r1]
    lea          r6, [3 * r3]
    vpxor        ymm5, ymm5, ymm5
.loop:
    AVX2_GetSatd16x4 ymm0, r0, r1, r5, r2, r3, r6, ymm6, ymm1, ymm2, ymm3, ymm4
    vpaddw       ymm5, ymm5, ymm0
    lea          r0, [r0 + 4 * r1]
    lea          r2, [r2 + 4 * r3]
    sub          r4, 1
    ja           .loop
    AVX2_SumWHorizon retrd, mm5, mm0
    vzeroupper
    POP_XMM
    LOAD_4_PARA_POP
%ifdef X86_32
    pop r6
    pop r5
    pop r4
%endif
    ret

%endif

;***********************************************************************
;
;Pixel_satd_wxh_avx2 END
;
;***********************************************************************

;***********************************************************************
;
;Pixel_sad_wxh_sse2 BEGIN
;
;***********************************************************************

%macro SSE2_GetSad2x16 0
    lea    r0,    [r0+2*r1]
    lea    r2,    [r2+2*r3]
    movdqu xmm1,   [r2]
    MOVDQ  xmm2,   [r0];[eax] must aligned 16
    psadbw xmm1,   xmm2
    paddw  xmm0,   xmm1
    movdqu xmm1,   [r2+r3]
    MOVDQ  xmm2,   [r0+r1]
    psadbw xmm1,   xmm2
    paddw  xmm0,   xmm1
%endmacro


%macro SSE2_GetSad4x16 0
    movdqu xmm0,   [r2]
    MOVDQ  xmm2,   [r0]
    psadbw xmm0,   xmm2
    paddw  xmm7,   xmm0
    movdqu xmm1,   [r2+r3]
    MOVDQ  xmm2,   [r0+r1]
    psadbw xmm1,   xmm2
    paddw  xmm7,   xmm1
    movdqu xmm1,   [r2+2*r3]
    MOVDQ  xmm2,   [r0+2*r1];[eax] must aligned 16
    psadbw xmm1,   xmm2
    paddw  xmm7,   xmm1
    movdqu xmm1,   [r2+r5]
    MOVDQ  xmm2,   [r0+r4]
    psadbw xmm1,   xmm2
    paddw  xmm7,   xmm1
%endmacro


%macro SSE2_GetSad8x4 0
    movq   xmm0,   [r0]
    movq   xmm1,   [r0+r1]
    lea    r0,     [r0+2*r1]
    movhps xmm0,   [r0]
    movhps xmm1,   [r0+r1]

    movq   xmm2,   [r2]
    movq   xmm3,   [r2+r3]
    lea    r2,     [r2+2*r3]
    movhps xmm2,   [r2]
    movhps xmm3,   [r2+r3]
    psadbw xmm0,   xmm2
    psadbw xmm1,   xmm3
    paddw  xmm6,   xmm0
    paddw  xmm6,   xmm1
%endmacro

;***********************************************************************
;
;int32_t WelsSampleSad16x16_sse2( uint8_t *, int32_t, uint8_t *, int32_t, )
;First parameter can align to 16 bytes,
;In wels, the third parameter can't align to 16 bytes.
;
;***********************************************************************
WELS_EXTERN WelsSampleSad16x16_sse2
%ifdef X86_32
    push  r4
    push  r5
%endif

    %assign  push_num 2
    LOAD_4_PARA
    PUSH_XMM 8
    SIGN_EXTENSION r1, r1d
    SIGN_EXTENSION r3, r3d
    lea r4, [3*r1]
    lea r5, [3*r3]

    pxor   xmm7,   xmm7
    SSE2_GetSad4x16
    lea    r0,  [r0+4*r1]
    lea    r2,  [r2+4*r3]
    SSE2_GetSad4x16
    lea    r0,  [r0+4*r1]
    lea    r2,  [r2+4*r3]
    SSE2_GetSad4x16
    lea    r0,  [r0+4*r1]
    lea    r2,  [r2+4*r3]
    SSE2_GetSad4x16
    movhlps xmm0, xmm7
    paddw xmm0, xmm7
    movd retrd, xmm0
    POP_XMM
    LOAD_4_PARA_POP
%ifdef X86_32
    pop  r5
    pop  r4
%endif
    ret

;***********************************************************************
;
;int32_t WelsSampleSad16x8_sse2( uint8_t *, int32_t, uint8_t *, int32_t, )
;First parameter can align to 16 bytes,
;In wels, the third parameter can't align to 16 bytes.
;
;***********************************************************************
WELS_EXTERN WelsSampleSad16x8_sse2
    %assign  push_num 0
    LOAD_4_PARA
    SIGN_EXTENSION r1, r1d
    SIGN_EXTENSION r3, r3d
    movdqu xmm0,   [r2]
    MOVDQ  xmm2,   [r0]
    psadbw xmm0,   xmm2
    movdqu xmm1,   [r2+r3]
    MOVDQ  xmm2,   [r0+r1]
    psadbw xmm1,   xmm2
    paddw  xmm0,   xmm1

    SSE2_GetSad2x16
    SSE2_GetSad2x16
    SSE2_GetSad2x16

    movhlps     xmm1, xmm0
    paddw       xmm0, xmm1
    movd        retrd,  xmm0
    LOAD_4_PARA_POP
    ret



WELS_EXTERN WelsSampleSad8x16_sse2
    %assign  push_num 0
    LOAD_4_PARA
    PUSH_XMM 7
    SIGN_EXTENSION r1, r1d
    SIGN_EXTENSION r3, r3d
    pxor   xmm6,   xmm6

    SSE2_GetSad8x4
    lea    r0,    [r0+2*r1]
    lea    r2,    [r2+2*r3]
    SSE2_GetSad8x4
    lea    r0,    [r0+2*r1]
    lea    r2,    [r2+2*r3]
    SSE2_GetSad8x4
    lea    r0,    [r0+2*r1]
    lea    r2,    [r2+2*r3]
    SSE2_GetSad8x4

    movhlps    xmm0, xmm6
    paddw      xmm0, xmm6
    movd       retrd,  xmm0
    POP_XMM
    LOAD_4_PARA_POP
    ret


%macro CACHE_SPLIT_CHECK 3 ; address, width, cacheline
and    %1,  0x1f|(%3>>1)
cmp    %1,  (32-%2)|(%3>>1)
%endmacro

WELS_EXTERN WelsSampleSad8x8_sse21
    %assign  push_num 0
    mov     r2,  arg3
    push    r2
    CACHE_SPLIT_CHECK r2, 8, 64
    jle    near   .pixel_sad_8x8_nsplit
    pop     r2
%ifdef X86_32
    push    r3
    push    r4
    push    r5
%endif
    %assign  push_num 3
    PUSH_XMM 8
    mov     r0,  arg1
    mov     r1,  arg2
    SIGN_EXTENSION r1, r1d
    pxor   xmm7,   xmm7

    ;ecx r2, edx r4, edi r5

    mov    r5,    r2
    and    r5,    0x07
    sub    r2,    r5
    mov    r4,    8
    sub    r4,    r5

    shl    r5,    3
    shl    r4,    3
    movd   xmm5,   r5d
    movd   xmm6,   r4d
    mov    r5,    8
    add    r5,    r2
    mov    r3,    arg4
    SIGN_EXTENSION r3, r3d
    movq   xmm0,   [r0]
    movhps xmm0,   [r0+r1]

    movq   xmm1,   [r2]
    movq   xmm2,   [r5]
    movhps xmm1,   [r2+r3]
    movhps xmm2,   [r5+r3]
    psrlq  xmm1,   xmm5
    psllq  xmm2,   xmm6
    por    xmm1,   xmm2

    psadbw xmm0,   xmm1
    paddw  xmm7,   xmm0

    lea    r0,    [r0+2*r1]
    lea    r2,    [r2+2*r3]
    lea    r5,    [r5+2*r3]

    movq   xmm0,   [r0]
    movhps xmm0,   [r0+r1]

    movq   xmm1,   [r2]
    movq   xmm2,   [r5]
    movhps xmm1,   [r2+r3]
    movhps xmm2,   [r5+r3]
    psrlq  xmm1,   xmm5
    psllq  xmm2,   xmm6
    por    xmm1,   xmm2

    psadbw xmm0,   xmm1
    paddw  xmm7,   xmm0

    lea    r0,    [r0+2*r1]
    lea    r2,    [r2+2*r3]
    lea    r5,    [r5+2*r3]

    movq   xmm0,   [r0]
    movhps xmm0,   [r0+r1]

    movq   xmm1,   [r2]
    movq   xmm2,   [r5]
    movhps xmm1,   [r2+r3]
    movhps xmm2,   [r5+r3]
    psrlq  xmm1,   xmm5
    psllq  xmm2,   xmm6
    por    xmm1,   xmm2

    psadbw xmm0,   xmm1
    paddw  xmm7,   xmm0

    lea    r0,    [r0+2*r1]
    lea    r2,    [r2+2*r3]
    lea    r5,    [r5+2*r3]

    movq   xmm0,   [r0]
    movhps xmm0,   [r0+r1]

    movq   xmm1,   [r2]
    movq   xmm2,   [r5]
    movhps xmm1,   [r2+r3]
    movhps xmm2,   [r5+r3]
    psrlq  xmm1,   xmm5
    psllq  xmm2,   xmm6
    por    xmm1,   xmm2

    psadbw xmm0,   xmm1
    paddw  xmm7,   xmm0

    movhlps    xmm0, xmm7
    paddw      xmm0, xmm7
    movd       retrd,  xmm0
    POP_XMM
%ifdef X86_32
    pop  r5
    pop  r4
    pop  r3
%endif
    jmp        .return

.pixel_sad_8x8_nsplit:

    pop r2
    %assign  push_num 0
    LOAD_4_PARA
    PUSH_XMM 7
    SIGN_EXTENSION r1, r1d
    SIGN_EXTENSION r3, r3d
    pxor   xmm6,   xmm6
    SSE2_GetSad8x4
    lea    r0,    [r0+2*r1]
    lea    r2,    [r2+2*r3]
    SSE2_GetSad8x4
    movhlps    xmm0, xmm6
    paddw      xmm0, xmm6
    movd       retrd,  xmm0
    POP_XMM
    LOAD_4_PARA_POP
.return:
    ret


;***********************************************************************
;
;Pixel_sad_wxh_sse2 END
;
;***********************************************************************


;***********************************************************************
;
;Pixel_sad_4_wxh_sse2 BEGIN
;
;***********************************************************************


%macro SSE2_Get4LW16Sad 5 ;s-1l, s, s+1l, d, address
    psadbw %1,   %4
    paddw  xmm5, %1
    psadbw %4,   %3
    paddw  xmm4, %4
    movdqu %4,   [%5-1]
    psadbw %4,   %2
    paddw  xmm6, %4
    movdqu %4,   [%5+1]
    psadbw %4,   %2
    paddw  xmm7, %4
%endmacro
WELS_EXTERN WelsSampleSadFour16x16_sse2
    %assign  push_num 0
    LOAD_5_PARA
    PUSH_XMM 8
    SIGN_EXTENSION r1, r1d
    SIGN_EXTENSION r3, r3d
    pxor   xmm4,   xmm4    ;sad pRefMb-i_stride_ref
    pxor   xmm5,   xmm5    ;sad pRefMb+i_stride_ref
    pxor   xmm6,   xmm6    ;sad pRefMb-1
    pxor   xmm7,   xmm7    ;sad pRefMb+1
    movdqa xmm0,   [r0]
    sub    r2,    r3
    movdqu xmm3,   [r2]
    psadbw xmm3,   xmm0
    paddw  xmm4,   xmm3

    movdqa xmm1,   [r0+r1]
    movdqu xmm3,   [r2+r3]
    psadbw xmm3,   xmm1
    paddw  xmm4,   xmm3

    movdqu xmm2,   [r2+r3-1]
    psadbw xmm2,   xmm0
    paddw  xmm6,   xmm2

    movdqu xmm3,   [r2+r3+1]
    psadbw xmm3,   xmm0
    paddw  xmm7,   xmm3

    lea    r0,    [r0+2*r1]
    lea    r2,    [r2+2*r3]
    movdqa xmm2,   [r0]
    movdqu xmm3,   [r2]
    SSE2_Get4LW16Sad xmm0, xmm1, xmm2, xmm3, r2
    movdqa xmm0,   [r0+r1]
    movdqu xmm3,   [r2+r3]
    SSE2_Get4LW16Sad xmm1, xmm2, xmm0, xmm3, r2+r3
    lea    r0,    [r0+2*r1]
    lea    r2,    [r2+2*r3]
    movdqa xmm1,   [r0]
    movdqu xmm3,   [r2]
    SSE2_Get4LW16Sad xmm2, xmm0, xmm1, xmm3, r2
    movdqa xmm2,   [r0+r1]
    movdqu xmm3,   [r2+r3]
    SSE2_Get4LW16Sad xmm0, xmm1, xmm2, xmm3, r2+r3
    lea    r0,    [r0+2*r1]
    lea    r2,    [r2+2*r3]
    movdqa xmm0,   [r0]
    movdqu xmm3,   [r2]
    SSE2_Get4LW16Sad xmm1, xmm2, xmm0, xmm3, r2
    movdqa xmm1,   [r0+r1]
    movdqu xmm3,   [r2+r3]
    SSE2_Get4LW16Sad xmm2, xmm0, xmm1, xmm3, r2+r3
    lea    r0,    [r0+2*r1]
    lea    r2,    [r2+2*r3]
    movdqa xmm2,   [r0]
    movdqu xmm3,   [r2]
    SSE2_Get4LW16Sad xmm0, xmm1, xmm2, xmm3, r2
    movdqa xmm0,   [r0+r1]
    movdqu xmm3,   [r2+r3]
    SSE2_Get4LW16Sad xmm1, xmm2, xmm0, xmm3, r2+r3
    lea    r0,    [r0+2*r1]
    lea    r2,    [r2+2*r3]
    movdqa xmm1,   [r0]
    movdqu xmm3,   [r2]
    SSE2_Get4LW16Sad xmm2, xmm0, xmm1, xmm3, r2
    movdqa xmm2,   [r0+r1]
    movdqu xmm3,   [r2+r3]
    SSE2_Get4LW16Sad xmm0, xmm1, xmm2, xmm3, r2+r3
    lea    r0,    [r0+2*r1]
    lea    r2,    [r2+2*r3]
    movdqa xmm0,   [r0]
    movdqu xmm3,   [r2]
    SSE2_Get4LW16Sad xmm1, xmm2, xmm0, xmm3, r2
    movdqa xmm1,   [r0+r1]
    movdqu xmm3,   [r2+r3]
    SSE2_Get4LW16Sad xmm2, xmm0, xmm1, xmm3, r2+r3
    lea    r0,    [r0+2*r1]
    lea    r2,    [r2+2*r3]
    movdqa xmm2,   [r0]
    movdqu xmm3,   [r2]
    SSE2_Get4LW16Sad xmm0, xmm1, xmm2, xmm3, r2
    movdqa xmm0,   [r0+r1]
    movdqu xmm3,   [r2+r3]
    SSE2_Get4LW16Sad xmm1, xmm2, xmm0, xmm3, r2+r3
    lea    r2,    [r2+2*r3]
    movdqu xmm3,   [r2]
    psadbw xmm2,   xmm3
    paddw xmm5,   xmm2

    movdqu xmm2,   [r2-1]
    psadbw xmm2,   xmm0
    paddw xmm6,   xmm2

    movdqu xmm3,   [r2+1]
    psadbw xmm3,   xmm0
    paddw xmm7,   xmm3

    movdqu xmm3,   [r2+r3]
    psadbw xmm0,   xmm3
    paddw xmm5,   xmm0

    movhlps    xmm0, xmm4
    paddw      xmm4, xmm0
    movhlps    xmm0, xmm5
    paddw      xmm5, xmm0
    movhlps    xmm0, xmm6
    paddw      xmm6, xmm0
    movhlps    xmm0, xmm7
    paddw      xmm7, xmm0
    punpckldq  xmm4, xmm5
    punpckldq  xmm6, xmm7
    punpcklqdq xmm4, xmm6
    movdqa     [r4],xmm4
    POP_XMM
    LOAD_5_PARA_POP
    ret


WELS_EXTERN WelsSampleSadFour16x8_sse2
    %assign  push_num 0
    LOAD_5_PARA
    PUSH_XMM 8
    SIGN_EXTENSION r1, r1d
    SIGN_EXTENSION r3, r3d
    pxor   xmm4,   xmm4    ;sad pRefMb-i_stride_ref
    pxor   xmm5,   xmm5    ;sad pRefMb+i_stride_ref
    pxor   xmm6,   xmm6    ;sad pRefMb-1
    pxor   xmm7,   xmm7    ;sad pRefMb+1
    movdqa xmm0,   [r0]
    sub    r2,    r3
    movdqu xmm3,   [r2]
    psadbw xmm3,   xmm0
    paddw xmm4,   xmm3

    movdqa xmm1,   [r0+r1]
    movdqu xmm3,   [r2+r3]
    psadbw xmm3,   xmm1
    paddw xmm4,   xmm3

    movdqu xmm2,   [r2+r3-1]
    psadbw xmm2,   xmm0
    paddw xmm6,   xmm2

    movdqu xmm3,   [r2+r3+1]
    psadbw xmm3,   xmm0
    paddw xmm7,   xmm3

    lea    r0,    [r0+2*r1]
    lea    r2,    [r2+2*r3]
    movdqa xmm2,   [r0]
    movdqu xmm3,   [r2]
    SSE2_Get4LW16Sad xmm0, xmm1, xmm2, xmm3, r2
    movdqa xmm0,   [r0+r1]
    movdqu xmm3,   [r2+r3]
    SSE2_Get4LW16Sad xmm1, xmm2, xmm0, xmm3, r2+r3
    lea    r0,    [r0+2*r1]
    lea    r2,    [r2+2*r3]
    movdqa xmm1,   [r0]
    movdqu xmm3,   [r2]
    SSE2_Get4LW16Sad xmm2, xmm0, xmm1, xmm3, r2
    movdqa xmm2,   [r0+r1]
    movdqu xmm3,   [r2+r3]
    SSE2_Get4LW16Sad xmm0, xmm1, xmm2, xmm3, r2+r3
    lea    r0,    [r0+2*r1]
    lea    r2,    [r2+2*r3]
    movdqa xmm0,   [r0]
    movdqu xmm3,   [r2]
    SSE2_Get4LW16Sad xmm1, xmm2, xmm0, xmm3, r2
    movdqa xmm1,   [r0+r1]
    movdqu xmm3,   [r2+r3]
    SSE2_Get4LW16Sad xmm2, xmm0, xmm1, xmm3, r2+r3
    lea    r2,    [r2+2*r3]
    movdqu xmm3,   [r2]
    psadbw xmm0,   xmm3
    paddw xmm5,   xmm0

    movdqu xmm0,   [r2-1]
    psadbw xmm0,   xmm1
    paddw xmm6,   xmm0

    movdqu xmm3,   [r2+1]
    psadbw xmm3,   xmm1
    paddw xmm7,   xmm3

    movdqu xmm3,   [r2+r3]
    psadbw xmm1,   xmm3
    paddw xmm5,   xmm1

    movhlps    xmm0, xmm4
    paddw      xmm4, xmm0
    movhlps    xmm0, xmm5
    paddw      xmm5, xmm0
    movhlps    xmm0, xmm6
    paddw      xmm6, xmm0
    movhlps    xmm0, xmm7
    paddw      xmm7, xmm0
    punpckldq  xmm4, xmm5
    punpckldq  xmm6, xmm7
    punpcklqdq xmm4, xmm6
    movdqa     [r4],xmm4
    POP_XMM
    LOAD_5_PARA_POP
    ret

WELS_EXTERN WelsSampleSadFour8x16_sse2
    %assign  push_num 0
    LOAD_5_PARA
    PUSH_XMM 8
    SIGN_EXTENSION r1, r1d
    SIGN_EXTENSION r3, r3d
    pxor   xmm4,   xmm4    ;sad pRefMb-i_stride_ref
    pxor   xmm5,   xmm5    ;sad pRefMb+i_stride_ref
    pxor   xmm6,   xmm6    ;sad pRefMb-1
    pxor   xmm7,   xmm7    ;sad pRefMb+1
    movq   xmm0,   [r0]
    movhps xmm0,   [r0+r1]
    sub    r2,    r3
    movq   xmm3,   [r2]
    movhps xmm3,   [r2+r3]
    psadbw xmm3,   xmm0
    paddw  xmm4,   xmm3

    movq   xmm1,  [r2+r3-1]
    movq   xmm3,  [r2+r3+1]

    lea    r0,    [r0+2*r1]
    lea    r2,    [r2+2*r3]
    movhps xmm1,  [r2-1]
    movhps xmm3,  [r2+1]
    psadbw xmm1,  xmm0
    paddw  xmm6,  xmm1
    psadbw xmm3,  xmm0
    paddw  xmm7,  xmm3

    movq   xmm3,  [r2]
    movhps xmm3,  [r2+r3]
    psadbw xmm0,  xmm3
    paddw  xmm5,  xmm0

    movq   xmm0,  [r0]
    movhps xmm0,  [r0+r1]
    psadbw xmm3,  xmm0
    paddw  xmm4,  xmm3

    movq   xmm1,  [r2+r3-1]
    movq   xmm3,  [r2+r3+1]

    lea    r0,    [r0+2*r1]
    lea    r2,    [r2+2*r3]
    movhps xmm1,  [r2-1]
    movhps xmm3,  [r2+1]

    psadbw xmm1,  xmm0
    paddw  xmm6,  xmm1
    psadbw xmm3,  xmm0
    paddw  xmm7,  xmm3

    movq   xmm3,  [r2]
    movhps xmm3,  [r2+r3]
    psadbw xmm0,  xmm3
    paddw  xmm5,  xmm0

    movq   xmm0,  [r0]
    movhps xmm0,  [r0+r1]
    psadbw xmm3,  xmm0
    paddw  xmm4,  xmm3

    movq   xmm1,  [r2+r3-1]
    movq   xmm3,  [r2+r3+1]

    lea    r0,    [r0+2*r1]
    lea    r2,    [r2+2*r3]
    movhps xmm1,  [r2-1]
    movhps xmm3,  [r2+1]

    psadbw xmm1,  xmm0
    paddw  xmm6,  xmm1
    psadbw xmm3,  xmm0
    paddw  xmm7,  xmm3

    movq   xmm3,  [r2]
    movhps xmm3,  [r2+r3]
    psadbw xmm0,  xmm3
    paddw  xmm5,  xmm0

    movq   xmm0,  [r0]
    movhps xmm0,  [r0+r1]
    psadbw xmm3,  xmm0
    paddw  xmm4,  xmm3

    movq   xmm1,  [r2+r3-1]
    movq   xmm3,  [r2+r3+1]

    lea    r0,    [r0+2*r1]
    lea    r2,    [r2+2*r3]
    movhps xmm1,  [r2-1]
    movhps xmm3,  [r2+1]

    psadbw xmm1,  xmm0
    paddw  xmm6,  xmm1
    psadbw xmm3,  xmm0
    paddw  xmm7,  xmm3

    movq   xmm3,  [r2]
    movhps xmm3,  [r2+r3]
    psadbw xmm0,  xmm3
    paddw  xmm5,  xmm0

    movq   xmm0,  [r0]
    movhps xmm0,  [r0+r1]
    psadbw xmm3,  xmm0
    paddw  xmm4,  xmm3

    movq   xmm1,  [r2+r3-1]
    movq   xmm3,  [r2+r3+1]

    lea    r0,    [r0+2*r1]
    lea    r2,    [r2+2*r3]
    movhps xmm1,  [r2-1]
    movhps xmm3,  [r2+1]

    psadbw xmm1,  xmm0
    paddw  xmm6,  xmm1
    psadbw xmm3,  xmm0
    paddw  xmm7,  xmm3

    movq   xmm3,  [r2]
    movhps xmm3,  [r2+r3]
    psadbw xmm0,  xmm3
    paddw  xmm5,  xmm0

    movq   xmm0,  [r0]
    movhps xmm0,  [r0+r1]
    psadbw xmm3,  xmm0
    paddw  xmm4,  xmm3

    movq   xmm1,  [r2+r3-1]
    movq   xmm3,  [r2+r3+1]

    lea    r0,    [r0+2*r1]
    lea    r2,    [r2+2*r3]
    movhps xmm1,  [r2-1]
    movhps xmm3,  [r2+1]

    psadbw xmm1,  xmm0
    paddw  xmm6,  xmm1
    psadbw xmm3,  xmm0
    paddw  xmm7,  xmm3

    movq   xmm3,  [r2]
    movhps xmm3,  [r2+r3]
    psadbw xmm0,  xmm3
    paddw  xmm5,  xmm0

    movq   xmm0,  [r0]
    movhps xmm0,  [r0+r1]
    psadbw xmm3,  xmm0
    paddw  xmm4,  xmm3

    movq   xmm1,  [r2+r3-1]
    movq   xmm3,  [r2+r3+1]

    lea    r0,    [r0+2*r1]
    lea    r2,    [r2+2*r3]
    movhps xmm1,  [r2-1]
    movhps xmm3,  [r2+1]

    psadbw xmm1,  xmm0
    paddw  xmm6,  xmm1
    psadbw xmm3,  xmm0
    paddw  xmm7,  xmm3

    movq   xmm3,  [r2]
    movhps xmm3,  [r2+r3]
    psadbw xmm0,  xmm3
    paddw  xmm5,  xmm0

    movq   xmm0,  [r0]
    movhps xmm0,  [r0+r1]
    psadbw xmm3,  xmm0
    paddw  xmm4,  xmm3

    movq   xmm1,  [r2+r3-1]
    movq   xmm3,  [r2+r3+1]

    lea    r0,    [r0+2*r1]
    lea    r2,    [r2+2*r3]
    movhps xmm1,  [r2-1]
    movhps xmm3,  [r2+1]

    psadbw xmm1,  xmm0
    paddw  xmm6,  xmm1
    psadbw xmm3,  xmm0
    paddw  xmm7,  xmm3

    movq   xmm3,  [r2]
    movhps xmm3,  [r2+r3]
    psadbw xmm0,  xmm3
    paddw  xmm5,  xmm0

    movhlps    xmm0, xmm4
    paddw      xmm4, xmm0
    movhlps    xmm0, xmm5
    paddw      xmm5, xmm0
    movhlps    xmm0, xmm6
    paddw      xmm6, xmm0
    movhlps    xmm0, xmm7
    paddw      xmm7, xmm0
    punpckldq  xmm4, xmm5
    punpckldq  xmm6, xmm7
    punpcklqdq xmm4, xmm6
    movdqa     [r4],xmm4
    POP_XMM
    LOAD_5_PARA_POP
    ret


WELS_EXTERN WelsSampleSadFour8x8_sse2
    %assign  push_num 0
    LOAD_5_PARA
    PUSH_XMM 8
    SIGN_EXTENSION r1, r1d
    SIGN_EXTENSION r3, r3d
    pxor   xmm4,   xmm4    ;sad pRefMb-i_stride_ref
    pxor   xmm5,   xmm5    ;sad pRefMb+i_stride_ref
    pxor   xmm6,   xmm6    ;sad pRefMb-1
    pxor   xmm7,   xmm7    ;sad pRefMb+1
    movq   xmm0,   [r0]
    movhps xmm0,   [r0+r1]
    sub    r2,    r3
    movq   xmm3,   [r2]
    movhps xmm3,   [r2+r3]
    psadbw xmm3,   xmm0
    paddw  xmm4,   xmm3

    movq   xmm1,  [r2+r3-1]
    movq   xmm3,  [r2+r3+1]

    lea    r0,    [r0+2*r1]
    lea    r2,    [r2+2*r3]
    movhps xmm1,  [r2-1]
    movhps xmm3,  [r2+1]
    psadbw xmm1,  xmm0
    paddw  xmm6,  xmm1
    psadbw xmm3,  xmm0
    paddw  xmm7,  xmm3

    movq   xmm3,  [r2]
    movhps xmm3,  [r2+r3]
    psadbw xmm0,  xmm3
    paddw  xmm5,  xmm0

    movq   xmm0,  [r0]
    movhps xmm0,  [r0+r1]
    psadbw xmm3,  xmm0
    paddw  xmm4,  xmm3

    movq   xmm1,  [r2+r3-1]
    movq   xmm3,  [r2+r3+1]

    lea    r0,    [r0+2*r1]
    lea    r2,    [r2+2*r3]
    movhps xmm1,  [r2-1]
    movhps xmm3,  [r2+1]

    psadbw xmm1,  xmm0
    paddw  xmm6,  xmm1
    psadbw xmm3,  xmm0
    paddw  xmm7,  xmm3

    movq   xmm3,  [r2]
    movhps xmm3,  [r2+r3]
    psadbw xmm0,  xmm3
    paddw  xmm5,  xmm0

    movq   xmm0,  [r0]
    movhps xmm0,  [r0+r1]
    psadbw xmm3,  xmm0
    paddw  xmm4,  xmm3

    movq   xmm1,  [r2+r3-1]
    movq   xmm3,  [r2+r3+1]

    lea    r0,    [r0+2*r1]
    lea    r2,    [r2+2*r3]
    movhps xmm1,  [r2-1]
    movhps xmm3,  [r2+1]

    psadbw xmm1,  xmm0
    paddw  xmm6,  xmm1
    psadbw xmm3,  xmm0
    paddw  xmm7,  xmm3

    movq   xmm3,  [r2]
    movhps xmm3,  [r2+r3]
    psadbw xmm0,  xmm3
    paddw  xmm5,  xmm0

    movq   xmm0,  [r0]
    movhps xmm0,  [r0+r1]
    psadbw xmm3,  xmm0
    paddw  xmm4,  xmm3


    movq   xmm1,  [r2+r3-1]
    movq   xmm3,  [r2+r3+1]

    lea    r0,    [r0+2*r1]
    lea    r2,    [r2+2*r3]
    movhps xmm1,  [r2-1]
    movhps xmm3,  [r2+1]

    psadbw xmm1,  xmm0
    paddw  xmm6,  xmm1
    psadbw xmm3,  xmm0
    paddw  xmm7,  xmm3

    movq   xmm3,  [r2]
    movhps xmm3,  [r2+r3]
    psadbw xmm0,  xmm3
    paddw  xmm5,  xmm0

    movhlps    xmm0, xmm4
    paddw      xmm4, xmm0
    movhlps    xmm0, xmm5
    paddw      xmm5, xmm0
    movhlps    xmm0, xmm6
    paddw      xmm6, xmm0
    movhlps    xmm0, xmm7
    paddw      xmm7, xmm0
    punpckldq  xmm4, xmm5
    punpckldq  xmm6, xmm7
    punpcklqdq xmm4, xmm6
    movdqa     [r4],xmm4
    POP_XMM
    LOAD_5_PARA_POP
    ret

WELS_EXTERN WelsSampleSadFour4x4_sse2
    %assign  push_num 0
    LOAD_5_PARA
    PUSH_XMM 8
    SIGN_EXTENSION r1, r1d
    SIGN_EXTENSION r3, r3d
    movd   xmm0,   [r0]
    movd   xmm1,   [r0+r1]
    lea        r0,    [r0+2*r1]
    movd       xmm2,   [r0]
    movd       xmm3,   [r0+r1]
    punpckldq  xmm0, xmm1
    punpckldq  xmm2, xmm3
    punpcklqdq xmm0, xmm2
    sub        r2,  r3
    movd       xmm1, [r2]
    movd       xmm2, [r2+r3]
    punpckldq  xmm1, xmm2
    movd       xmm2, [r2+r3-1]
    movd       xmm3, [r2+r3+1]

    lea        r2,  [r2+2*r3]

    movd       xmm4, [r2]
    movd       xmm5, [r2-1]
    punpckldq  xmm2, xmm5
    movd       xmm5, [r2+1]
    punpckldq  xmm3, xmm5

    movd       xmm5, [r2+r3]
    punpckldq  xmm4, xmm5

    punpcklqdq xmm1, xmm4 ;-L

    movd       xmm5, [r2+r3-1]
    movd       xmm6, [r2+r3+1]

    lea        r2,  [r2+2*r3]
    movd       xmm7, [r2-1]
    punpckldq  xmm5, xmm7
    punpcklqdq xmm2, xmm5 ;-1
    movd       xmm7, [r2+1]
    punpckldq  xmm6, xmm7
    punpcklqdq xmm3, xmm6 ;+1
    movd       xmm6, [r2]
    movd       xmm7, [r2+r3]
    punpckldq  xmm6, xmm7
    punpcklqdq xmm4, xmm6 ;+L
    psadbw     xmm1, xmm0
    psadbw     xmm2, xmm0
    psadbw     xmm3, xmm0
    psadbw     xmm4, xmm0

    movhlps    xmm0, xmm1
    paddw      xmm1, xmm0
    movhlps    xmm0, xmm2
    paddw      xmm2, xmm0
    movhlps    xmm0, xmm3
    paddw      xmm3, xmm0
    movhlps    xmm0, xmm4
    paddw      xmm4, xmm0
    punpckldq  xmm1, xmm4
    punpckldq  xmm2, xmm3
    punpcklqdq xmm1, xmm2
    movdqa     [r4],xmm1
    POP_XMM
    LOAD_5_PARA_POP
    ret

;***********************************************************************
;
;Pixel_sad_4_wxh_sse2 END
;
;***********************************************************************

;***********************************************************************
;   int32_t WelsSampleSad4x4_mmx (uint8_t *, int32_t, uint8_t *, int32_t )
;***********************************************************************
WELS_EXTERN WelsSampleSad4x4_mmx
    %assign  push_num 0
    LOAD_4_PARA
    SIGN_EXTENSION r1, r1d
    SIGN_EXTENSION r3, r3d
    movd      mm0, [r0]
    movd      mm1, [r0+r1]
    punpckldq mm0, mm1

    movd      mm3, [r2]
    movd      mm4, [r2+r3]
    punpckldq mm3, mm4
    psadbw    mm0, mm3

    lea       r0, [r0+2*r1]
    lea       r2, [r2+2*r3]

    movd      mm1, [r0]
    movd      mm2, [r0+r1]
    punpckldq mm1, mm2

    movd      mm3, [r2]
    movd      mm4, [r2+r3]
    punpckldq mm3, mm4
    psadbw    mm1, mm3
    paddw     mm0, mm1

    movd      retrd, mm0

    WELSEMMS
    LOAD_4_PARA_POP
    ret
