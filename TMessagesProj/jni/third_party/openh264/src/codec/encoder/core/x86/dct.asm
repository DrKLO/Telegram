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

SECTION .text

;***********************************************************************
; SSE2 functions
;***********************************************************************

%macro SSE2_SumSubD 3
    movdqa  %3, %2
    paddd   %2, %1
    psubd   %1, %3
%endmacro

%macro SSE2_SumSubDiv2D 4
    paddd   %1, %2
    paddd   %1, %3
    psrad   %1,  1
    movdqa  %4, %1
    psubd   %4, %2
%endmacro
%macro SSE2_Load4Col    5
    movsx       r2,     WORD[%5]
    movd        %1,         r2d
    movsx       r2,     WORD[%5 + 0x20]
    movd        %2,         r2d
    punpckldq   %1,         %2
    movsx       r2,     WORD[%5 + 0x80]
    movd        %3,         r2d
    movsx       r2,     WORD[%5 + 0xa0]
    movd        %4,         r2d
    punpckldq   %3,         %4
    punpcklqdq  %1,         %3
%endmacro

;***********************************************************************
;void WelsHadamardT4Dc_sse2( int16_t *luma_dc, int16_t *pDct)
;***********************************************************************
WELS_EXTERN WelsHadamardT4Dc_sse2
    %assign push_num 0
    LOAD_2_PARA
    PUSH_XMM 8
    SSE2_Load4Col       xmm1, xmm5, xmm6, xmm0, r1
    SSE2_Load4Col       xmm2, xmm5, xmm6, xmm0, r1 + 0x40
    SSE2_Load4Col       xmm3, xmm5, xmm6, xmm0, r1 + 0x100
    SSE2_Load4Col       xmm4, xmm5, xmm6, xmm0, r1 + 0x140

    SSE2_SumSubD        xmm1, xmm2, xmm7
    SSE2_SumSubD        xmm3, xmm4, xmm7
    SSE2_SumSubD        xmm2, xmm4, xmm7
    SSE2_SumSubD        xmm1, xmm3, xmm7

    SSE2_Trans4x4D      xmm4, xmm2, xmm1, xmm3, xmm5    ; pOut: xmm4,xmm3,xmm5,xmm1

    SSE2_SumSubD        xmm4, xmm3, xmm7
    SSE2_SumSubD        xmm5, xmm1, xmm7

    WELS_DD1 xmm6
    SSE2_SumSubDiv2D    xmm3, xmm1, xmm6, xmm0          ; pOut: xmm3 = (xmm3+xmm1+1)/2, xmm0 = (xmm3-xmm1+1)/2
    SSE2_SumSubDiv2D    xmm4, xmm5, xmm6, xmm1          ; pOut: xmm4 = (xmm4+xmm5+1)/2, xmm1 = (xmm4-xmm5+1)/2
    SSE2_Trans4x4D      xmm3, xmm0, xmm1, xmm4, xmm2    ; pOut: xmm3,xmm4,xmm2,xmm1

    packssdw    xmm3,   xmm4
    packssdw    xmm2,   xmm1
    movdqa  [r0+ 0],   xmm3
    movdqa  [r0+16],   xmm2

    POP_XMM
    ret
