;*!
;* \copy
;*     Copyright (c)  2010-2013, Cisco Systems
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
;*  predenoise.asm
;*
;*  Abstract
;*      denoise for SVC2.1
;*  History
;*      4/13/2010 Created
;*      7/30/2010 Modified
;*
;*
;*************************************************************************/
%include "asm_inc.asm"

;***********************************************************************
; Constant
;***********************************************************************
%ifdef X86_32_PICASM
SECTION .text align=16
%else
SECTION .rodata align=16
%endif

sse2_32 times 8 dw 32
sse2_20 times 8 dw 20



;***********************************************************************
; Code
;***********************************************************************
SECTION .text

%macro WEIGHT_LINE  9
    movq        %2, %9
    punpcklbw   %2, %7
    movdqa      %8, %2

    movdqa      %1, %6
    psubusb     %1, %8
    psubusb     %8, %6
    por         %8, %1      ; ABS(curPixel - centerPixel);

    movdqa      %1, %3
    psubusb     %1, %8

    pmullw      %1, %1
    psrlw       %1, 5
    pmullw      %2, %1
    paddusw     %4, %1
    paddusw     %5, %2
%endmacro

%macro WEIGHT_LINE1_UV  4
    movdqa      %2, %1
    punpcklbw   %2, %4
    paddw       %3, %2

    movdqa      %2, %1
    psrldq      %2, 1
    punpcklbw   %2, %4
    paddw       %3, %2

    movdqa      %2, %1
    psrldq      %2, 2
    punpcklbw   %2, %4
    psllw       %2, 1
    paddw       %3, %2

    movdqa      %2, %1
    psrldq      %2, 3
    punpcklbw   %2, %4
    paddw       %3, %2

    movdqa      %2, %1
    psrldq      %2, 4
    punpcklbw   %2, %4
    paddw       %3, %2
%endmacro

%macro WEIGHT_LINE2_UV  4
    movdqa      %2, %1
    punpcklbw   %2, %4
    paddw       %3, %2

    movdqa      %2, %1
    psrldq      %2, 1
    punpcklbw   %2, %4
    psllw       %2, 1
    paddw       %3, %2

    movdqa      %2, %1
    psrldq      %2, 2
    punpcklbw   %2, %4
    psllw       %2, 2
    paddw       %3, %2

    movdqa      %2, %1
    psrldq      %2, 3
    punpcklbw   %2, %4
    psllw       %2, 1
    paddw       %3, %2

    movdqa      %2, %1
    psrldq      %2, 4
    punpcklbw   %2, %4
    paddw       %3, %2
%endmacro

%macro WEIGHT_LINE3_UV  4
    movdqa      %2, %1
    punpcklbw   %2, %4
    psllw       %2, 1
    paddw       %3, %2

    movdqa      %2, %1
    psrldq      %2, 1
    punpcklbw   %2, %4
    psllw       %2, 2
    paddw       %3, %2

    movdqa      %2, %1
    psrldq      %2, 2
    punpcklbw   %2, %4
    pmullw      %2, [pic(sse2_20)]
    paddw       %3, %2

    movdqa      %2, %1
    psrldq      %2, 3
    punpcklbw   %2, %4
    psllw       %2, 2
    paddw       %3, %2

    movdqa      %2, %1
    psrldq      %2, 4
    punpcklbw   %2, %4
    psllw       %2, 1
    paddw       %3, %2
%endmacro

;***********************************************************************
;  BilateralLumaFilter8_sse2(uint8_t *pixels, int stride);
;***********************************************************************
;   1   2   3
;   4   0   5
;   6   7   8
;   0:  the center point

WELS_EXTERN BilateralLumaFilter8_sse2

    push r3
    %assign push_num 1
    LOAD_2_PARA
    PUSH_XMM 8

    pxor        xmm7,   xmm7

    mov         r3,     r0

    movq        xmm6,   [r0]
    punpcklbw   xmm6,   xmm7
%ifdef X86_32_PICASM
    pcmpeqw     xmm3,   xmm3
    psrlw       xmm3,   15
    psllw       xmm3,   5
%else
    movdqa      xmm3,   [sse2_32]
%endif
    pxor        xmm4,   xmm4        ; nTotWeight
    pxor        xmm5,   xmm5        ; nSum

    dec         r0
    WEIGHT_LINE xmm1, xmm2, xmm3, xmm4, xmm5, xmm6, xmm7, xmm0,  [r0]           ; pixel 4
    WEIGHT_LINE xmm1, xmm2, xmm3, xmm4, xmm5, xmm6, xmm7, xmm0,  [r0 + 2]       ; pixel 5

    sub         r0, r1
    WEIGHT_LINE xmm1, xmm2, xmm3, xmm4, xmm5, xmm6, xmm7, xmm0,  [r0]           ; pixel 1
    WEIGHT_LINE xmm1, xmm2, xmm3, xmm4, xmm5, xmm6, xmm7, xmm0,  [r0 + 1]       ; pixel 2
    WEIGHT_LINE xmm1, xmm2, xmm3, xmm4, xmm5, xmm6, xmm7, xmm0,  [r0 + 2]       ; pixel 3

    lea         r0, [r0 + r1 * 2]
    WEIGHT_LINE xmm1, xmm2, xmm3, xmm4, xmm5, xmm6, xmm7, xmm0,  [r0]           ; pixel 6
    WEIGHT_LINE xmm1, xmm2, xmm3, xmm4, xmm5, xmm6, xmm7, xmm0,  [r0 + 1]       ; pixel 7
    WEIGHT_LINE xmm1, xmm2, xmm3, xmm4, xmm5, xmm6, xmm7, xmm0,  [r0 + 2]       ; pixel 8

    pcmpeqw     xmm0,   xmm0
    psrlw       xmm0,   15
    psllw       xmm0,   8
    psubusw     xmm0,   xmm4
    pmullw      xmm0,   xmm6
    paddusw     xmm5,   xmm0
    psrlw       xmm5,   8
    packuswb    xmm5,   xmm5
    movq        [r3],   xmm5


    POP_XMM
    pop r3
    %assign push_num 0

    ret

;***********************************************************************
; void      WaverageChromaFilter8_sse2(uint8_t *pixels, int stride);
;***********************************************************************
;5x5 filter:
;1  1   2   1   1
;1  2   4   2   1
;2  4   20  4   2
;1  2   4   2   1
;1  1   2   1   1

WELS_EXTERN WaverageChromaFilter8_sse2

    push r3

    %assign push_num 1

    INIT_X86_32_PIC r4
    LOAD_2_PARA

    mov     r3, r1
    add     r3, r3
    sub     r0, r3          ; pixels - 2 * stride
    sub     r0, 2

    pxor    xmm0,   xmm0
    pxor    xmm3,   xmm3

    movdqu      xmm1,   [r0]
    WEIGHT_LINE1_UV xmm1,   xmm2,   xmm3,   xmm0

    movdqu      xmm1,   [r0 + r1]
    WEIGHT_LINE2_UV xmm1,   xmm2,   xmm3,   xmm0

    add     r0, r3
    movdqu      xmm1,   [r0]
    WEIGHT_LINE3_UV xmm1,   xmm2,   xmm3,   xmm0

    movdqu      xmm1,   [r0 + r1]
    WEIGHT_LINE2_UV xmm1,   xmm2,   xmm3,   xmm0

    movdqu      xmm1,   [r0 + r1 * 2]
    WEIGHT_LINE1_UV xmm1,   xmm2,   xmm3,   xmm0

    psrlw       xmm3,       6
    packuswb    xmm3,       xmm3
    movq        [r0 + 2],       xmm3


    DEINIT_X86_32_PIC
    pop r3

    %assign push_num 0
    ret
