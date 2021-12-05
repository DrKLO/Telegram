;*!
;* \copy
;*     Copyright (c)  2009-2013, Cisco Systems
;*     All rights reserved.
;*
;*     Redistribution and use in source and binary forms, with or without
;*     modification, are permitted provided that the following conditions
;*     are met:
;*
;*        ?Redistributions of source code must retain the above copyright
;*          notice, this list of conditions and the following disclaimer.
;*
;*        ?Redistributions in binary form must reproduce the above copyright
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
;*  dct.asm
;*
;*  History
;*      8/4/2009 Created
;*
;*
;*************************************************************************/

%include "asm_inc.asm"

%macro LOAD_3_PARA_TO_5_PARA_IDCT 0
%ifdef X86_32
    push r3
    push r4
    %assign push_num push_num+2
    mov r0, [esp + push_num*4 + 4]
    mov r1, [esp + push_num*4 + 8]
    mov r4, [esp + push_num*4 + 12]
%else
    mov r4, r2
%endif
    mov r2, r0
    mov r3, r1
%endmacro

%ifdef PREFIX
    %define prefixed(a) _ %+ a
%else
    %define prefixed(a) a
%endif

%ifdef X86_32_PICASM
SECTION .text align=32
%else
SECTION .rodata align=32
%endif

;***********************************************************************
; Constant
;***********************************************************************

align 32
wels_shufb0312_movzxw_128:
    db 0, 80h, 3, 80h, 1, 80h, 2, 80h, 4, 80h, 7, 80h, 5, 80h, 6, 80h
wels_shufb2301_128:
    db 2, 3, 0, 1, 6, 7, 4, 5, 10, 11, 8, 9, 14, 15, 12, 13
wels_shufb0231_128:
    db 0, 2, 3, 1, 4, 6, 7, 5, 8, 10, 11, 9, 12, 14, 15, 13
wels_dw32_128:
    times 8 dw 32
wels_p1m1p1m1w_256:
    times 8 dw 1, -1
wels_p1p2m1m2w_256:
    times 4 dw 1, 2, -1, -2
wels_p1p1m1m1w_256:
    times 4 dw 1, 1, -1, -1
wels_8xp1w_8xm1w:
    times 8 dw  1
    times 8 dw -1
wels_4xp1w_4xm1w_256:
    times 4 dw  1
    times 4 dw -1
    times 4 dw  1
    times 4 dw -1
wels_4xp1w_4xp2w_4xm1w_4xm2w:
    times 4 dw  1
    times 4 dw  2
    times 4 dw -1
    times 4 dw -2

align 16
wels_p1m1p1m1w_128:
    times 4 dw 1, -1
wels_p1p2p1p2w_128:
    times 4 dw 1, 2
wels_p1m1m1p1w_128:
    times 2 dw 1, -1, -1, 1
wels_p0m8000p0m8000w_128:
    times 4 dw 0, -8000h
wels_p1p1m1m1w_128:
    times 2 dw 1, 1, -1, -1
wels_4xp1w_4xp2w:
    times 4 dw 1
    times 4 dw 2
wels_4xp0w_4xm8000w:
    times 4 dw 0
    times 4 dw -8000h

SECTION .text

;***********************************************************************
; MMX functions
;***********************************************************************

%macro MMX_LoadDiff4P 5
    movd        %1, [%3]
    movd        %2, [%4]
    punpcklbw   %1, %5
    punpcklbw   %2, %5
    psubw       %1, %2
%endmacro

%macro MMX_LoadDiff4x4P 10 ;d0, d1, d2, d3, pix1address, pix1stride, pix2address, pix2stride, tmp(mm), 0(mm)
    MMX_LoadDiff4P %1, %9, %5,    %7,    %10
    MMX_LoadDiff4P %2, %9, %5+%6, %7+%8, %10
    lea  %5, [%5+2*%6]
    lea  %7, [%7+2*%8]
    MMX_LoadDiff4P %3, %9, %5,    %7,    %10
    MMX_LoadDiff4P %4, %9, %5+%6, %7+%8, %10
%endmacro

%macro MMX_SumSubMul2 3
    movq    %3, %1
    psllw   %1, $01
    paddw   %1, %2
    psllw   %2, $01
    psubw   %3, %2
%endmacro

%macro MMX_SumSubDiv2 3
    movq    %3, %2
    psraw   %3, $01
    paddw   %3, %1
    psraw   %1, $01
    psubw   %1, %2
%endmacro

%macro MMX_SumSub 3
    movq    %3, %2
    psubw   %2, %1
    paddw   %1, %3
%endmacro

%macro MMX_DCT 6
    MMX_SumSub      %4, %1, %6
    MMX_SumSub      %3, %2, %6
    MMX_SumSub      %3, %4, %6
    MMX_SumSubMul2  %1, %2, %5
%endmacro

%macro MMX_IDCT 6
    MMX_SumSub      %4, %5, %6
    MMX_SumSubDiv2  %3, %2, %1
    MMX_SumSub      %1, %4, %6
    MMX_SumSub      %3, %5, %6
%endmacro

%macro MMX_StoreDiff4P 6
    movd       %2, %6
    punpcklbw  %2, %4
    paddw      %1, %3
    psraw      %1, $06
    paddsw     %1, %2
    packuswb   %1, %2
    movd       %5, %1
%endmacro

;***********************************************************************
;   void WelsDctT4_mmx( int16_t *pDct[4], uint8_t *pix1, int32_t i_pix1, uint8_t *pix2, int32_t i_pix2 )
;***********************************************************************
WELS_EXTERN WelsDctT4_mmx
    %assign push_num 0
    LOAD_5_PARA
    SIGN_EXTENSION r2, r2d
    SIGN_EXTENSION r4, r4d
    WELS_Zero    mm7

    MMX_LoadDiff4x4P mm1, mm2, mm3, mm4, r1, r2, r3, r4, mm0, mm7

    MMX_DCT         mm1, mm2, mm3 ,mm4, mm5, mm6
    MMX_Trans4x4W   mm3, mm1, mm4, mm5, mm2

    MMX_DCT         mm3, mm5, mm2 ,mm4, mm1, mm6
    MMX_Trans4x4W   mm2, mm3, mm4, mm1, mm5

    movq    [r0+ 0],   mm2
    movq    [r0+ 8],   mm1
    movq    [r0+16],   mm5
    movq    [r0+24],   mm4
    WELSEMMS
    LOAD_5_PARA_POP
    ret

;***********************************************************************
; void IdctResAddPred_mmx(uint8_t* pPred, int32_t iStride, int16_t* pDct);
;***********************************************************************
WELS_EXTERN IdctResAddPred_mmx
    %assign push_num 0
    LOAD_3_PARA_TO_5_PARA_IDCT
    jmp prefixed(WelsIDctT4Rec_mmx.begin)

;***********************************************************************
;   void WelsIDctT4Rec_mmx(uint8_t *rec, int32_t stride, uint8_t *pred, int32_t pred_stride, int16_t *rs)
;***********************************************************************
WELS_EXTERN WelsIDctT4Rec_mmx
    %assign push_num 0
    LOAD_5_PARA
.begin:
    SIGN_EXTENSION r1, r1d
    SIGN_EXTENSION r3, r3d
    movq    mm0, [r4+ 0]
    movq    mm1, [r4+ 8]
    movq    mm2, [r4+16]
    movq    mm3, [r4+24]

    MMX_Trans4x4W       mm0, mm1, mm2, mm3, mm4
    MMX_IDCT            mm1, mm2, mm3, mm4, mm0, mm6
    MMX_Trans4x4W       mm1, mm3, mm0, mm4, mm2
    MMX_IDCT            mm3, mm0, mm4, mm2, mm1, mm6

    WELS_Zero           mm7
    WELS_DW32           mm6

    MMX_StoreDiff4P     mm3, mm0, mm6, mm7, [r0], [r2]
    MMX_StoreDiff4P     mm4, mm0, mm6, mm7, [r0+r1], [r2+r3]
    lea     r0, [r0+2*r1]
    lea     r2, [r2+2*r3]
    MMX_StoreDiff4P     mm1, mm0, mm6, mm7, [r0], [r2]
    MMX_StoreDiff4P     mm2, mm0, mm6, mm7, [r0+r1], [r2+r3]

    WELSEMMS
    LOAD_5_PARA_POP
    ret


;***********************************************************************
; SSE2 functions
;***********************************************************************

%macro SSE2_Store4x8p 6
    movlps   [%1+0x00], %2
    movhps   [%1+0x20], %2
    movlps   [%1+0x08], %3
    movhps   [%1+0x28], %3
    movlps   [%1+0x10], %4
    movhps   [%1+0x30], %4
    movlps   [%1+0x18], %5
    movhps   [%1+0x38], %5
%endmacro

%macro SSE2_Load4x8p 6
    MOVDQ    %2,    [%1+0x00]
    MOVDQ    %4,    [%1+0x10]
    MOVDQ    %6,    [%1+0x20]
    MOVDQ    %3,    [%1+0x30]
    SSE2_XSawp qdq, %4, %3, %5
    SSE2_XSawp qdq, %2, %6, %3
%endmacro

%macro SSE2_SumSubMul2 3
    movdqa  %3, %1
    psllw   %1, 1
    paddw   %1, %2
    psllw   %2, 1
    psubw   %3, %2
%endmacro

%macro SSE2_SumSubDiv2 4
    movdqa  %4, %1
    movdqa  %3, %2
    psraw   %2, $01
    psraw   %4, $01
    paddw   %1, %2
    psubw   %4, %3
%endmacro

%macro SSE2_StoreDiff16p 9
    paddw       %1, %4
    psraw       %1, $06
    movq        %3, %7
    punpcklbw   %3, %5
    paddsw      %1, %3
    paddw       %2, %4
    psraw       %2, $06
    movq        %3, %9
    punpcklbw   %3, %5
    paddsw      %2, %3
    packuswb    %1, %2
    movlps      %6, %1
    movhps      %8, %1
%endmacro

%macro SSE2_StoreDiff8p 5
    movq        %2, %5
    punpcklbw   %2, %3
    paddsw      %2, %1
    packuswb    %2, %2
    movq        %4, %2
%endmacro

%macro SSE2_Load2x4P 2
    MOVDQ       %1, [%2]
%endmacro

%macro SSE2_Store2x4P 2
    MOVDQ       [%1], %2
%endmacro

; out=%1 pPixel1Line1=%2 pPixel1Line2=%3 pPixel2Line1=%4 pPixel2Line2=%5 zero=%6 clobber=%7,%8
%macro SSE2_LoadDiff2x4P 8
    movd        %1, [%2]
    movd        %7, [%3]
    punpckldq   %1, %7
    punpcklbw   %1, %6
    movd        %7, [%4]
    movd        %8, [%5]
    punpckldq   %7, %8
    punpcklbw   %7, %6
    psubw       %1, %7
%endmacro

; pRec1=%1 pRec2=%2 data=%3 pPred1=%4 pPred2=%5 dw32=%6 zero=%7 clobber=%8,%9
%macro SSE2_StoreDiff2x4P 9
    paddw       %3, %6
    psraw       %3, 6
    movd        %8, [%4]
    movd        %9, [%5]
    punpckldq   %8, %9
    punpcklbw   %8, %7
    paddsw      %3, %8
    packuswb    %3, %3
    movd        [%1], %3
    psrlq       %3, 32
    movd        [%2], %3
%endmacro

%macro SSE2_Load8DC 6
    movdqa      %1,     %6      ; %1 = dc0 dc1
    paddw       %1,     %5
    psraw       %1,     $06     ; (dc + 32) >> 6

    movdqa      %2,     %1
    psrldq      %2,     4
    punpcklwd   %2,     %2
    punpckldq   %2,     %2      ; %2 = dc2 dc2 dc2 dc2 dc3 dc3 dc3 dc3

    movdqa      %3,     %1
    psrldq      %3,     8
    punpcklwd   %3,     %3
    punpckldq   %3,     %3      ; %3 = dc4 dc4 dc4 dc4 dc5 dc5 dc5 dc5

    movdqa      %4,     %1
    psrldq      %4,     12
    punpcklwd   %4,     %4
    punpckldq   %4,     %4      ; %4 = dc6 dc6 dc6 dc6 dc7 dc7 dc7 dc7

    punpcklwd   %1,     %1
    punpckldq   %1,     %1      ; %1 = dc0 dc0 dc0 dc0 dc1 dc1 dc1 dc1
%endmacro

%macro SSE2_DCT 6
    SSE2_SumSub     %6, %3, %5
    SSE2_SumSub     %1, %2, %5
    SSE2_SumSub     %3, %2, %5
    SSE2_SumSubMul2     %6, %1, %4
%endmacro

%macro SSE2_IDCT 7
    SSE2_SumSub       %7, %2, %6
    SSE2_SumSubDiv2     %1, %3, %5, %4
    SSE2_SumSub      %2, %1, %5
    SSE2_SumSub      %7, %4, %5
%endmacro

; Do 2 horizontal 4-pt DCTs in parallel packed as 8 words in an xmm register.
; out=%1 in=%1 clobber=%2
%macro SSE2_DCT_HORIZONTAL 2
    pshuflw       %2, %1, 1bh                    ; [x[3],x[2],x[1],x[0]] low qw
    pmullw        %1, [pic(wels_p1m1p1m1w_128)]  ; [x[0],-x[1],x[2],-x[3], ...]
    pshufhw       %2, %2, 1bh                    ; [x[3],x[2],x[1],x[0]] high qw
    paddw         %1, %2                         ; s = [x[0]+x[3],-x[1]+x[2],x[2]+x[1],-x[3]+x[0], ...]
    pshufd        %2, %1, 0b1h                   ; [s[2],s[3],s[0],s[1], ...]
    pmullw        %1, [pic(wels_p1m1m1p1w_128)]  ; [s[0],-s[1],-s[2],s[3], ...]
    pmullw        %2, [pic(wels_p1p2p1p2w_128)]  ; [s[2],2*s[3],s[0],2*s[1], ...]]
    paddw         %1, %2                         ; y = [s[0]+s[2],-s[1]+2*s[3],-s[2]+s[0],s[3]+2*s[1], ...]
%endmacro

; Do 2 horizontal 4-pt IDCTs in parallel packed as 8 words in an xmm register.
;
; Use a multiply by reciprocal to get -x>>1, and x+=-x>>1 to get x>>1, which
; avoids a cumbersome blend with SSE2 to get a vector with right-shifted odd
; elements.
;
; out=%1 in=%1 wels_p1m1m1p1w_128=%2 clobber=%3,%4
%macro SSE2_IDCT_HORIZONTAL 4
    movdqa        %3, [pic(wels_p0m8000p0m8000w_128)]
    pmulhw        %3, %1                    ; x[0:7] * [0,-8000h,0,-8000h, ...] >> 16
    pshufd        %4, %1, 0b1h              ; [x[2],x[3],x[0],x[1], ...]
    pmullw        %4, %2                    ; [x[2],-x[3],-x[0],x[1], ...]
    paddw         %1, %3                    ; [x[0]+0,x[1]+(-x[1]>>1),x[2]+0,x[3]+(-x[3]>>1), ...]
    paddw         %1, %4                    ; s = [x[0]+x[2],(x[1]>>1)-x[3],x[2]-x[0],(x[3]>>1)+x[1], ...]
    pshuflw       %3, %1, 1bh               ; [s[3],s[2],s[1],s[0]] low qw
    pmullw        %1, [pic(wels_p1p1m1m1w_128)]  ; [s[0],s[1],-s[2],-s[3], ...]
    pshufhw       %3, %3, 1bh               ; [s[3],s[2],s[1],s[0]] high qw
    pmullw        %3, %2                    ; [s[3],-s[2],-s[1],s[0], ...]
    paddw         %1, %3                    ; y = [s[0]+s[3],s[1]-s[2],-s[2]-s[1],-s[3]+s[0], ...]
%endmacro

; Do 4 vertical 4-pt DCTs in parallel packed as 16 words in 2 xmm registers.
; Uses scrambled input to save a negation.
; [y0,y1]=%1 [y2,y3]=%2 [x1,x0]=%1 [x2,x3]=%2 clobber=%3
%macro SSE2_DCT_4x4P 3
    movdqa        %3, %1
    psubw         %1, %2                    ; [x1-x2,x0-x3]
    paddw         %2, %3                    ; [x1+x2,x0+x3]
    movdqa        %3, %2
    punpckhqdq    %2, %1                    ; s03 = [x0+x3,x0-x3]
    punpcklqdq    %3, %1                    ; s12 = [x1+x2,x1-x2]
    movdqa        %1, %2
    pmullw        %1, [pic(wels_4xp1w_4xp2w)] ; [s03[0],2*s03[1]]
    paddw         %1, %3                      ; [y0,y1] = [s03[0]+s12[0],2*s03[1]+s12[1]]
    pmullw        %3, [pic(wels_4xp1w_4xp2w)] ; [s12[0],2*s12[1]]
    psubw         %2, %3                    ; [y2,y3] = [s03[0]-s12[0],s03[1]-2*s12[1]]
%endmacro

; Do 4 vertical 4-pt IDCTs in parallel packed as 16 words in 2 xmm registers.
; Output is scrambled to save a negation.
; [y1,y0]=%1 [y2,y3]=%2 [x0,x1]=%1 [x2,x3]=%2 clobber=%3,%4
%macro SSE2_IDCT_4x4P 4
    movdqa        %4, [pic(wels_4xp0w_4xm8000w)]
    movdqa        %3, %1
    pmulhw        %3, %4                    ; x[0:1] * [0,-8000h] >> 16
    pmulhw        %4, %2                    ; x[2:3] * [0,-8000h] >> 16
    paddw         %3, %1                    ; [x[0],x[1]>>1]
    paddw         %4, %2                    ; [x[2],x[3]>>1]
    psubw         %3, %2                    ; [x[0]-x[2],(x[1]>>1)-x[3]]
    paddw         %1, %4                    ; [x[2]+x[0],(x[3]>>1)+x[1]]
    movdqa        %2, %3
    punpckhqdq    %3, %1                    ; s13 = [(x[1]>>1)-x[3],(x[3]>>1)+x[1]]
    punpcklqdq    %2, %1                    ; s02 = [x[0]-x[2], x[2]+x[0]]
    movdqa        %1, %2
    paddw         %1, %3                    ; [y1,y0] = [s02[0]+s13[0],s02[1]+s13[1]]
    psubw         %2, %3                    ; [y2,y3] = [s02[0]-s13[0],s02[1]-s13[1]]
%endmacro

;***********************************************************************
; void WelsDctFourT4_sse2(int16_t *pDct, uint8_t *pix1, int32_t i_pix1, uint8_t *pix2, int32_t i_pix2 )
;***********************************************************************
WELS_EXTERN WelsDctFourT4_sse2
    %assign push_num 0
    INIT_X86_32_PIC r5
    LOAD_5_PARA
    PUSH_XMM 8
    SIGN_EXTENSION r2, r2d
    SIGN_EXTENSION r4, r4d
    pxor    xmm7, xmm7
    ;Load 4x8
    SSE2_LoadDiff8P    xmm0, xmm6, xmm7, [r1], [r3]
    SSE2_LoadDiff8P    xmm1, xmm6, xmm7, [r1+r2], [r3+r4]
    lea     r1, [r1 + 2 * r2]
    lea     r3, [r3 + 2 * r4]
    SSE2_LoadDiff8P    xmm2, xmm6, xmm7, [r1], [r3]
    SSE2_LoadDiff8P    xmm3, xmm6, xmm7, [r1+r2], [r3+r4]

    SSE2_DCT            xmm1, xmm2, xmm3, xmm4, xmm5, xmm0
    SSE2_DCT_HORIZONTAL xmm2, xmm5
    SSE2_DCT_HORIZONTAL xmm0, xmm5
    SSE2_DCT_HORIZONTAL xmm3, xmm5
    SSE2_DCT_HORIZONTAL xmm4, xmm5

    SSE2_Store4x8p r0, xmm2, xmm0, xmm3, xmm4, xmm1

    lea     r1, [r1 + 2 * r2]
    lea     r3, [r3 + 2 * r4]

    ;Load 4x8
    SSE2_LoadDiff8P    xmm0, xmm6, xmm7, [r1      ], [r3    ]
    SSE2_LoadDiff8P    xmm1, xmm6, xmm7, [r1+r2  ], [r3+r4]
    lea     r1, [r1 + 2 * r2]
    lea     r3, [r3 + 2 * r4]
    SSE2_LoadDiff8P    xmm2, xmm6, xmm7, [r1], [r3]
    SSE2_LoadDiff8P    xmm3, xmm6, xmm7, [r1+r2], [r3+r4]

    SSE2_DCT            xmm1, xmm2, xmm3, xmm4, xmm5, xmm0
    SSE2_DCT_HORIZONTAL xmm2, xmm5
    SSE2_DCT_HORIZONTAL xmm0, xmm5
    SSE2_DCT_HORIZONTAL xmm3, xmm5
    SSE2_DCT_HORIZONTAL xmm4, xmm5

    SSE2_Store4x8p r0+64, xmm2, xmm0, xmm3, xmm4, xmm1

    POP_XMM
    LOAD_5_PARA_POP
    DEINIT_X86_32_PIC
    ret

;***********************************************************************
; void WelsIDctFourT4Rec_sse2(uint8_t *rec, int32_t stride, uint8_t *pred, int32_t pred_stride, int16_t *rs);
;***********************************************************************
WELS_EXTERN WelsIDctFourT4Rec_sse2
    %assign push_num 0
    INIT_X86_32_PIC r5
    LOAD_5_PARA
    PUSH_XMM 8
    SIGN_EXTENSION r1, r1d
    SIGN_EXTENSION r3, r3d
    ;Load 4x8
    SSE2_Load4x8p  r4, xmm0, xmm1, xmm4, xmm2, xmm5

    movdqa xmm7, [pic(wels_p1m1m1p1w_128)]
    SSE2_IDCT_HORIZONTAL xmm0, xmm7, xmm5, xmm6
    SSE2_IDCT_HORIZONTAL xmm1, xmm7, xmm5, xmm6
    SSE2_IDCT_HORIZONTAL xmm4, xmm7, xmm5, xmm6
    SSE2_IDCT_HORIZONTAL xmm2, xmm7, xmm5, xmm6
    SSE2_IDCT xmm1, xmm4, xmm2, xmm3, xmm5, xmm6, xmm0

    WELS_Zero           xmm7
    WELS_DW32           xmm6

    SSE2_StoreDiff16p xmm1, xmm3, xmm5, xmm6, xmm7, [r0], [r2], [r0 + r1], [r2 + r3]
    lea     r0, [r0 + 2 * r1]
    lea     r2, [r2 + 2 * r3]
    SSE2_StoreDiff16p xmm0, xmm4, xmm5, xmm6, xmm7, [r0], [r2], [r0 + r1], [r2 + r3]

    lea     r0, [r0 + 2 * r1]
    lea     r2, [r2 + 2 * r3]
    SSE2_Load4x8p  r4+64, xmm0, xmm1, xmm4, xmm2, xmm5

    movdqa xmm7, [pic(wels_p1m1m1p1w_128)]
    SSE2_IDCT_HORIZONTAL xmm0, xmm7, xmm5, xmm6
    SSE2_IDCT_HORIZONTAL xmm1, xmm7, xmm5, xmm6
    SSE2_IDCT_HORIZONTAL xmm4, xmm7, xmm5, xmm6
    SSE2_IDCT_HORIZONTAL xmm2, xmm7, xmm5, xmm6
    SSE2_IDCT xmm1, xmm4, xmm2, xmm3, xmm5, xmm6, xmm0

    WELS_Zero           xmm7
    WELS_DW32           xmm6

    SSE2_StoreDiff16p xmm1, xmm3, xmm5, xmm6, xmm7, [r0], [r2], [r0 + r1], [r2 + r3]
    lea     r0, [r0 + 2 * r1]
    lea     r2, [r2 + 2 * r3]
    SSE2_StoreDiff16p xmm0, xmm4, xmm5, xmm6, xmm7, [r0], [r2], [r0 + r1], [r2 + r3]
    POP_XMM
    LOAD_5_PARA_POP
    DEINIT_X86_32_PIC
    ret

;***********************************************************************
; void WelsDctT4_sse2(int16_t* pDct, uint8_t* pPixel1, int32_t iStride1, uint8_t* pPixel2, int32_t iStride2)
;***********************************************************************
WELS_EXTERN WelsDctT4_sse2
    %assign push_num 0
    INIT_X86_32_PIC r5
    LOAD_5_PARA
    PUSH_XMM 5
    SIGN_EXTENSION r2, r2d
    SIGN_EXTENSION r4, r4d

    WELS_Zero xmm2
    SSE2_LoadDiff2x4P xmm0, r1+r2, r1, r3+r4, r3, xmm2, xmm3, xmm4
    add r1, r2
    add r3, r4
    SSE2_LoadDiff2x4P xmm1, r1+r2, r1+2*r2, r3+r4, r3+2*r4, xmm2, xmm3, xmm4
    SSE2_DCT_HORIZONTAL xmm0, xmm3
    SSE2_DCT_HORIZONTAL xmm1, xmm3
    SSE2_DCT_4x4P xmm0, xmm1, xmm3
    SSE2_Store2x4P r0,    xmm0
    SSE2_Store2x4P r0+16, xmm1

    POP_XMM
    LOAD_5_PARA_POP
    DEINIT_X86_32_PIC
    ret

;***********************************************************************
; void IdctResAddPred_sse2(uint8_t* pPred, int32_t iStride, int16_t* pDct);
;***********************************************************************
WELS_EXTERN IdctResAddPred_sse2
    %assign push_num 0
    LOAD_3_PARA_TO_5_PARA_IDCT
    jmp prefixed(WelsIDctT4Rec_sse2.begin)

;***********************************************************************
; void WelsIDctT4Rec_sse2(uint8_t* pRec, int32_t iStride, uint8_t* pPred, int32_t iPredStride, int16_t* pDct);
;***********************************************************************
WELS_EXTERN WelsIDctT4Rec_sse2
    %assign push_num 0
    LOAD_5_PARA
.begin:
    INIT_X86_32_PIC r5
    PUSH_XMM 6
    SIGN_EXTENSION r1, r1d
    SIGN_EXTENSION r3, r3d

    SSE2_Load2x4P xmm0, r4
    SSE2_Load2x4P xmm1, r4+16
    movdqa xmm4, [pic(wels_p1m1m1p1w_128)]
    SSE2_IDCT_HORIZONTAL xmm0, xmm4, xmm2, xmm3
    SSE2_IDCT_HORIZONTAL xmm1, xmm4, xmm2, xmm3
    SSE2_IDCT_4x4P xmm0, xmm1, xmm2, xmm3
    WELS_Zero xmm4
    WELS_DW32 xmm5
    SSE2_StoreDiff2x4P r0+r1, r0, xmm0, r2+r3, r2, xmm5, xmm4, xmm2, xmm3
    add r0, r1
    add r2, r3
    SSE2_StoreDiff2x4P r0+r1, r0+2*r1, xmm1, r2+r3, r2+2*r3, xmm5, xmm4, xmm2, xmm3

    POP_XMM
    DEINIT_X86_32_PIC
    LOAD_5_PARA_POP
    ret

%macro SSE2_StoreDiff4x8p 8
    SSE2_StoreDiff8p    %1, %3, %4, [%5],           [%6]
    SSE2_StoreDiff8p    %1, %3, %4, [%5 + %7],      [%6 + %8]
    SSE2_StoreDiff8p    %2, %3, %4, [%5 + 8],       [%6 + 8]
    SSE2_StoreDiff8p    %2, %3, %4, [%5 + %7 + 8],  [%6 + %8 + 8]
%endmacro

 ;***********************************************************************
; void WelsIDctRecI16x16Dc_sse2(uint8_t *rec, int32_t stride, uint8_t *pred, int32_t pred_stride, int16_t *dct_dc)
;***********************************************************************
WELS_EXTERN WelsIDctRecI16x16Dc_sse2
    %assign push_num 0
    LOAD_5_PARA
    PUSH_XMM 8
    SIGN_EXTENSION r1, r1d
    SIGN_EXTENSION r3, r3d
    pxor        xmm7,       xmm7
    WELS_DW32   xmm6

    SSE2_Load8DC            xmm0, xmm1, xmm2, xmm3, xmm6, [r4]
    SSE2_StoreDiff4x8p      xmm0, xmm1, xmm5, xmm7, r0, r2, r1, r3

    lea         r0,     [r0 + 2 * r1]
    lea         r2,     [r2 + 2 * r3]
    SSE2_StoreDiff4x8p      xmm0, xmm1, xmm5, xmm7, r0, r2, r1, r3

    lea         r0,     [r0 + 2 * r1]
    lea         r2,     [r2 + 2 * r3]
    SSE2_StoreDiff4x8p      xmm2, xmm3, xmm5, xmm7, r0, r2, r1, r3

    lea         r0,     [r0 + 2 * r1]
    lea         r2,     [r2 + 2 * r3]
    SSE2_StoreDiff4x8p      xmm2, xmm3, xmm5, xmm7, r0, r2, r1, r3

    SSE2_Load8DC            xmm0, xmm1, xmm2, xmm3, xmm6, [r4 + 16]
    lea         r0,     [r0 + 2 * r1]
    lea         r2,     [r2 + 2 * r3]
    SSE2_StoreDiff4x8p      xmm0, xmm1, xmm5, xmm7, r0, r2, r1, r3

    lea         r0,     [r0 + 2 * r1]
    lea         r2,     [r2 + 2 * r3]
    SSE2_StoreDiff4x8p      xmm0, xmm1, xmm5, xmm7, r0, r2, r1, r3

    lea         r0,     [r0 + 2 * r1]
    lea         r2,     [r2 + 2 * r3]
    SSE2_StoreDiff4x8p      xmm2, xmm3, xmm5, xmm7, r0, r2, r1, r3

    lea         r0,     [r0 + 2 * r1]
    lea         r2,     [r2 + 2 * r3]
    SSE2_StoreDiff4x8p      xmm2, xmm3, xmm5, xmm7, r0, r2, r1, r3
    POP_XMM
    LOAD_5_PARA_POP
    ret


;***********************************************************************
; AVX2 functions
;***********************************************************************

%ifdef HAVE_AVX2
; out=%1 pPixel1=%2 iStride1=%3 pPixel2=%4 iStride2=%5 wels_shufb0312_movzxw=%6 clobber=%7,%8
%macro AVX2_LoadDiff16P 8
    vmovq         x%1, [%2         ]
    vpbroadcastq  y%7, [%2 + 4 * %3]
    vpblendd      y%1, y%1, y%7, 11110000b
    vpshufb       y%1, y%1, y%6
    vmovq         x%7, [%4         ]
    vpbroadcastq  y%8, [%4 + 4 * %5]
    vpblendd      y%7, y%7, y%8, 11110000b
    vpshufb       y%7, y%7, y%6
    vpsubw        y%1, y%1, y%7
%endmacro

; pRec=%1 iStride=%2 data=%3,%4 pPred=%5 iPredStride=%6 dw32=%7 wels_shufb0312_movzxw=%8 clobber=%9,%10
%macro AVX2_StoreDiff32P 10
    vpaddw        y%3, y%3, y%7
    vpsraw        y%3, y%3, 6
    vmovq         x%9,  [%5         ]
    vpbroadcastq  y%10, [%5 + 4 * %6]
    add           %5, %6
    vpblendd      y%9, y%9, y%10, 11110000b
    vpshufb       y%9, y%9, y%8
    vpaddsw       y%3, y%3, y%9
    vpaddw        y%4, y%4, y%7
    vpsraw        y%4, y%4, 6
    vmovq         x%9,  [%5         ]
    vpbroadcastq  y%10, [%5 + 4 * %6]
    vpblendd      y%9, y%9, y%10, 11110000b
    vpshufb       y%9, y%9, y%8
    vpaddsw       y%4, y%4, y%9
    vpackuswb     y%3, y%3, y%4
    vbroadcasti128 y%4, [pic(wels_shufb0231_128)]
    vpshufb       y%3, y%3, y%4
    vextracti128  x%4, y%3, 1
    vmovlps       [%1         ], x%3
    vmovlps       [%1 + 4 * %2], x%4
    add           %1, %2
    vmovhps       [%1         ], x%3
    vmovhps       [%1 + 4 * %2], x%4
%endmacro

; out=%1,%2,%3,%4 pDct=%5 clobber=%6
%macro AVX2_Load4x16P 6
    vmovdqa       x%2,      [%5+0x00]
    vinserti128   y%2, y%2, [%5+0x40], 1
    vmovdqa       x%6,      [%5+0x20]
    vinserti128   y%6, y%6, [%5+0x60], 1
    vpunpcklqdq   y%1, y%2, y%6
    vpunpckhqdq   y%2, y%2, y%6
    vmovdqa       x%4,      [%5+0x10]
    vinserti128   y%4, y%4, [%5+0x50], 1
    vmovdqa       x%6,      [%5+0x30]
    vinserti128   y%6, y%6, [%5+0x70], 1
    vpunpcklqdq   y%3, y%4, y%6
    vpunpckhqdq   y%4, y%4, y%6
%endmacro

; pDct=%1 data=%1,%2,%3,%4 clobber=%5
%macro AVX2_Store4x16P 6
    vpunpcklqdq   y%6, y%2,  y%3
    vmovdqa       [%1+0x00], x%6
    vextracti128  [%1+0x40], y%6, 1
    vpunpckhqdq   y%6, y%2,  y%3
    vmovdqa       [%1+0x20], x%6
    vextracti128  [%1+0x60], y%6, 1
    vpunpcklqdq   y%6, y%4,  y%5
    vmovdqa       [%1+0x10], x%6
    vextracti128  [%1+0x50], y%6, 1
    vpunpckhqdq   y%6, y%4,  y%5
    vmovdqa       [%1+0x30], x%6
    vextracti128  [%1+0x70], y%6, 1
%endmacro

%macro AVX2_Load4x4P 2
    vmovdqu       y%1, [%2]
%endmacro

%macro AVX2_Store4x4P 2
    vmovdqu       [%1], y%2
%endmacro

; Load 4 lines of 4 pixels, shuffle and zero extend to 16-bit.
; out=%1 pPixel=%2 iStride=%3 [wels_shufb0312_movzxw]=%4 clobber=%5,%6
%macro AVX2_Loadzx4x4P 6
    vmovd         x%1, [%2         ]
    add           %2, %3
    vpbroadcastd  x%5, [%2 + 2 * %3]
    vpblendd      x%1, x%1, x%5, 1010b
    vpbroadcastd  y%5, [%2         ]
    vpbroadcastd  y%6, [%2 +     %3]
    vpblendd      y%5, y%5, y%6, 10101010b
    vpblendd      y%1, y%1, y%5, 11110000b
    vpshufb       y%1, y%1, %4
%endmacro

; out=%1 pPixel1=%2 iStride1=%3 pPixel2=%4 iStride2=%5 wels_shufb0312_movzxw=%6 clobber=%7,%8,%9
%macro AVX2_LoadDiff4x4P 9
    AVX2_Loadzx4x4P %1, %2, %3, y%6, %7, %8
    AVX2_Loadzx4x4P %7, %4, %5, y%6, %8, %9
    vpsubw        y%1, y%1, y%7
%endmacro

; pRec=%1 iStride=%2 data=%3 pPred=%4 iPredStride=%5 dw32=%6 wels_shufb0312_movzxw=%7 clobber=%8,%9,%10
%macro AVX2_StoreDiff4x4P 10
    vpaddw         y%3, y%3, y%6
    vpsraw         y%3, y%3, 6
    AVX2_Loadzx4x4P %8, %4, %5, y%7, %9, %10
    vpaddsw        y%3, y%3, y%8
    vpackuswb      y%3, y%3, y%3
    vbroadcasti128 y%8, [pic(wels_shufb0231_128)]
    vpshufb        y%3, y%3, y%8
    vextracti128   x%8, y%3, 1
    vmovd          [%1         ], x%3
    add            %1, %2
    vmovd          [%1         ], x%8
    vpsrlq         x%8, x%8, 32
    vmovd          [%1     + %2], x%8
    vpsrlq         x%3, x%3, 32
    vmovd          [%1 + 2 * %2], x%3
%endmacro

; 4-pt DCT
; out=%1,%2,%3,%4 in=%1,%2,%3,%4 clobber=%5
%macro AVX2_DCT 5
    vpsubw        %5, %1, %4  ; s3 = x0 - x3
    vpaddw        %1, %1, %4  ; s0 = x0 + x3
    vpsubw        %4, %2, %3  ; s2 = x1 - x2
    vpaddw        %2, %2, %3  ; s1 = x1 + x2
    vpsubw        %3, %1, %2  ; y2 = s0 - s1
    vpaddw        %1, %1, %2  ; y0 = s0 + s1
    vpsllw        %2, %5, 1
    vpaddw        %2, %2, %4  ; y1 = 2 * s3 + s2
    vpsllw        %4, %4, 1
    vpsubw        %4, %5, %4  ; y3 = s3 - 2 * s2
%endmacro

; 4-pt IDCT
; out=%1,%2,%3,%4 in=%1,%2,%3,%4 clobber=%5
%macro AVX2_IDCT 5
    vpsraw        %5, %2, 1
    vpsubw        %5, %5, %4  ; t3 = (x1 >> 1) - x3
    vpsraw        %4, %4, 1
    vpaddw        %4, %2, %4  ; t2 = x1 + (x3 >> 1)
    vpaddw        %2, %1, %3  ; t0 = x0 + x2
    vpsubw        %3, %1, %3  ; t1 = x0 - x2
    vpaddw        %1, %2, %4  ; y0 = t0 + t2
    vpsubw        %4, %2, %4  ; y3 = t0 - t2
    vpaddw        %2, %3, %5  ; y1 = t1 + t3
    vpsubw        %3, %3, %5  ; y2 = t1 - t3
%endmacro

; Do 4 horizontal 4-pt DCTs in parallel packed as 16 words in a ymm register.
; Uses scrambled input to save a negation.
; [y0,y1,y2,y3]=%1 [x0,x3,x1,x2]=%1 wels_shufb2301=%2 clobber=%3
%macro AVX2_DCT_HORIZONTAL 3
    vpsignw       %3, %1, [pic(wels_p1m1p1m1w_256)]  ; [x0,-x3,x1,-x2]
    vpshufb       %1, %1, %2                    ; [x3,x0,x2,x1]
    vpaddw        %1, %1, %3                    ; s = [x0+x3,-x3+x0,x1+x2,-x2+x1]
    vpmullw       %3, %1, [pic(wels_p1p2m1m2w_256)]  ; [s[0],2*s[1],-s[2],-2*s[3], ...]
    vpshufd       %1, %1, 0b1h                  ; [s[2],s[3],s[0],s[1], ...]
    vpaddw        %1, %1, %3                    ; [y0,y1,y2,y3] = [s[0]+s[2],2*s[1]+s[3],-s[2]+s[0],-2*s[3]+s[1], ...]
%endmacro

; Do 4 horizontal 4-pt IDCTs in parallel packed as 16 words in a ymm register.
; Output is scrambled to save a negation.
; [y0,y3,y1,y2]=%1 [x0,x1,x2,x3]=%1 wels_shufb2301=%2 clobber=%3
%macro AVX2_IDCT_HORIZONTAL 3
    vpsraw        %3, %1, 1                     ; [x0>>1,x1>>1,x2>>1,x3>>1]
    vpblendw      %3, %1, %3, 10101010b         ; [x0,x1>>1,x2,x3>>1]
    vpsignw       %1, %1, [pic(wels_p1p1m1m1w_256)]  ; [x0,x1,-x2,-x3]
    vpshufd       %3, %3, 0b1h                  ; [x2,x3>>1,x0,x1>>1]
    vpaddw        %1, %3, %1                    ; s = [x2+x0,(x3>>1)+x1,x0-x2,(x1>>1)-x3]
    vpshufb       %3, %1, %2                    ; [s[1],s[0],s[3],s[2], ...]
    vpsignw       %1, %1, [pic(wels_p1m1p1m1w_256)]  ; [s[0],-s[1],s[2],-s[3], ...]
    vpaddw        %1, %1, %3                    ; [y0,y3,y1,y2] = [s[0]+s[1],-s[1]+s[0],s[2]+s[3],-s[3]+s[2], ...]
%endmacro

; Do 4 vertical 4-pt DCTs in parallel packed as 16 words in a ymm register.
; Uses scrambled input to save a negation.
; [y0,y1,y2,y3]=%1 [x0,x3,x1,x2]=%1 clobber=%2
%macro AVX2_DCT_4x4P 2
    vpsignw       %2, %1, [pic(wels_4xp1w_4xm1w_256)]    ; [x0,-x3,x1,-x2]
    vpshufd       %1, %1, 4eh                            ; [x3,x0,x2,x1]
    vpaddw        %1, %1, %2                             ; s = [x0+x3,-x3+x0,x1+x2,-x2+x1]
    vpmullw       %2, %1, [pic(wels_4xp1w_4xp2w_4xm1w_4xm2w)] ; [s[0],2*s[1],-s[2],-2*s[3]]
    vpermq        %1, %1, 4eh                            ; [s[2],s[3],s[0],s[1]]
    vpaddw        %1, %1, %2                             ; [y0,y1,y2,y3] = [s[0]+s[2],2*s[1]+s[3],-s[2]+s[0],-2*s[3]+s[1]]
%endmacro

; Do 4 vertical 4-pt IDCTs in parallel packed as 16 words in a ymm register.
; Output is scrambled to save a negation.
; [y0,y3,y1,y2]=%1 [x0,x1,x2,x3]=%1 clobber=%2
%macro AVX2_IDCT_4x4P 2
    vpsraw        %2, %1, 1                              ; [x0>>1,x1>>1,x2>>1,x3>>1]
    vpblendw      %2, %1, %2, 11110000b                  ; [x0,x1>>1,x2,x3>>1]
    vpsignw       %1, %1, [pic(wels_8xp1w_8xm1w)]        ; [x0,x1,-x2,-x3]
    vpermq        %2, %2, 4eh                            ; [x2,x3>>1,x0,x1>>1]
    vpaddw        %1, %2, %1                             ; s = [x2+x0,(x3>>1)+x1,x0-x2,(x1>>1)-x3]
    vpshufd       %2, %1, 4eh                            ; [s[1],s[0],s[3],s[2]]
    vpmullw       %1, %1, [pic(wels_4xp1w_4xm1w_256)]    ; [s[0],-s[1],s[2],-s[3], ...]
    vpaddw        %1, %1, %2                             ; [y0,y3,y1,y2] = [s[0]+s[1],-s[1]+s[0],s[2]+s[3],-s[3]+s[2]]
%endmacro

;***********************************************************************
; void WelsDctFourT4_avx2(int16_t* pDct, uint8_t* pPixel1, int32_t iStride1, uint8_t* pPixel2, int32_t iStride2)
;***********************************************************************
WELS_EXTERN WelsDctFourT4_avx2
    %assign push_num 0
    INIT_X86_32_PIC r5
    LOAD_5_PARA
    PUSH_XMM 7
    SIGN_EXTENSION r2, r2d
    SIGN_EXTENSION r4, r4d

    vbroadcasti128 ymm6, [pic(wels_shufb0312_movzxw_128)]

    ;Load 4x16
    AVX2_LoadDiff16P mm0, r1, r2, r3, r4, mm6, mm4, mm5
    add r1, r2
    add r3, r4
    AVX2_LoadDiff16P mm1, r1, r2, r3, r4, mm6, mm4, mm5
    add r1, r2
    add r3, r4
    AVX2_LoadDiff16P mm2, r1, r2, r3, r4, mm6, mm4, mm5
    add r1, r2
    add r3, r4
    AVX2_LoadDiff16P mm3, r1, r2, r3, r4, mm6, mm4, mm5

    AVX2_DCT ymm0, ymm1, ymm2, ymm3, ymm5
    vbroadcasti128 ymm6, [pic(wels_shufb2301_128)]
    AVX2_DCT_HORIZONTAL ymm0, ymm6, ymm5
    AVX2_DCT_HORIZONTAL ymm1, ymm6, ymm5
    AVX2_DCT_HORIZONTAL ymm2, ymm6, ymm5
    AVX2_DCT_HORIZONTAL ymm3, ymm6, ymm5

    AVX2_Store4x16P r0, mm0, mm1, mm2, mm3, mm5
    vzeroupper

    POP_XMM
    LOAD_5_PARA_POP
    DEINIT_X86_32_PIC
    ret

;***********************************************************************
; void IdctFourResAddPred_avx2(uint8_t* pPred, int32_t iStride, const int16_t* pDct, const int8_t* pNzc);
;***********************************************************************
WELS_EXTERN IdctFourResAddPred_avx2
    %assign push_num 0
    LOAD_3_PARA_TO_5_PARA_IDCT
    jmp prefixed(WelsIDctFourT4Rec_avx2.begin)

;***********************************************************************
; void WelsIDctFourT4Rec_avx2(uint8_t* pRec, int32_t iStride, uint8_t* pPred, int32_t iPredStride, int16_t* pDct);
;***********************************************************************
WELS_EXTERN WelsIDctFourT4Rec_avx2
    %assign push_num 0
    LOAD_5_PARA
.begin:
    INIT_X86_32_PIC r5
    PUSH_XMM 8
    SIGN_EXTENSION r1, r1d
    SIGN_EXTENSION r3, r3d

    AVX2_Load4x16P mm0, mm1, mm2, mm3, r4, mm5
    vbroadcasti128 ymm6, [pic(wels_shufb2301_128)]
    AVX2_IDCT_HORIZONTAL ymm0, ymm6, ymm5
    AVX2_IDCT_HORIZONTAL ymm1, ymm6, ymm5
    AVX2_IDCT_HORIZONTAL ymm2, ymm6, ymm5
    AVX2_IDCT_HORIZONTAL ymm3, ymm6, ymm5
    AVX2_IDCT ymm0, ymm1, ymm2, ymm3, ymm5

    vbroadcasti128 ymm6, [pic(wels_shufb0312_movzxw_128)]
    vbroadcasti128 ymm7, [pic(wels_dw32_128)]
    AVX2_StoreDiff32P r0, r1, mm0, mm1, r2, r3, mm7, mm6, mm5, mm4
    add r2, r3
    add r0, r1
    AVX2_StoreDiff32P r0, r1, mm2, mm3, r2, r3, mm7, mm6, mm5, mm4
    vzeroupper

    POP_XMM
    DEINIT_X86_32_PIC
    LOAD_5_PARA_POP
    ret

;***********************************************************************
; void WelsDctT4_avx2(int16_t* pDct, uint8_t* pPixel1, int32_t iStride1, uint8_t* pPixel2, int32_t iStride2)
;***********************************************************************
WELS_EXTERN WelsDctT4_avx2
    %assign push_num 0
    INIT_X86_32_PIC r5
    LOAD_5_PARA
    PUSH_XMM 5
    SIGN_EXTENSION r2, r2d
    SIGN_EXTENSION r4, r4d

    vbroadcasti128 ymm1, [pic(wels_shufb0312_movzxw_128)]
    AVX2_LoadDiff4x4P mm0, r1, r2, r3, r4, mm1, mm2, mm3, mm4
    AVX2_DCT_4x4P ymm0, ymm2
    vbroadcasti128 ymm1, [pic(wels_shufb2301_128)]
    AVX2_DCT_HORIZONTAL ymm0, ymm1, ymm2
    AVX2_Store4x4P r0, mm0
    vzeroupper

    POP_XMM
    LOAD_5_PARA_POP
    DEINIT_X86_32_PIC
    ret

;***********************************************************************
; void IdctResAddPred_avx2(uint8_t* pPred, int32_t iStride, int16_t* pDct);
;***********************************************************************
WELS_EXTERN IdctResAddPred_avx2
    %assign push_num 0
    LOAD_3_PARA_TO_5_PARA_IDCT
    jmp prefixed(WelsIDctT4Rec_avx2.begin)

;***********************************************************************
; void WelsIDctT4Rec_avx2(uint8_t* pRec, int32_t iStride, uint8_t* pPred, int32_t iPredStride, int16_t* pDct);
;***********************************************************************
WELS_EXTERN WelsIDctT4Rec_avx2
    %assign push_num 0
    LOAD_5_PARA
.begin:
    INIT_X86_32_PIC r5
    PUSH_XMM 6
    SIGN_EXTENSION r1, r1d
    SIGN_EXTENSION r3, r3d

    AVX2_Load4x4P mm0, r4
    vbroadcasti128 ymm4, [pic(wels_shufb2301_128)]
    AVX2_IDCT_HORIZONTAL ymm0, ymm4, ymm1
    AVX2_IDCT_4x4P ymm0, ymm1
    vbroadcasti128 ymm4, [pic(wels_shufb0312_movzxw_128)]
    vbroadcasti128 ymm5, [pic(wels_dw32_128)]
    AVX2_StoreDiff4x4P r0, r1, mm0, r2, r3, mm5, mm4, mm1, mm2, mm3
    vzeroupper

    POP_XMM
    DEINIT_X86_32_PIC
    LOAD_5_PARA_POP
    ret
%endif

