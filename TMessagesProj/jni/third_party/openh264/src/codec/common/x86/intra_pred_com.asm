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
;*  intra_pred_common.asm
;*
;*  Abstract
;*      sse2 function for intra predict operations
;*
;*  History
;*      18/09/2009 Created
;*
;*
;*************************************************************************/
%include "asm_inc.asm"

;***********************************************************************
; Code
;***********************************************************************

SECTION .text

;***********************************************************************
; void WelsI16x16LumaPredH_sse2(uint8_t *pred, uint8_t *pRef, int32_t stride);
;***********************************************************************

%macro SSE2_PRED_H_16X16_ONE_LINE 0
    add r0, 16
    add r1, r2
    movzx r3, byte [r1]
    SSE2_Copy16Times xmm0, r3d
    movdqa [r0], xmm0
%endmacro

WELS_EXTERN WelsI16x16LumaPredH_sse2
    push r3
    %assign push_num 1
    LOAD_3_PARA
    SIGN_EXTENSION r2, r2d
    dec r1
    movzx r3, byte [r1]
    SSE2_Copy16Times xmm0, r3d
    movdqa [r0], xmm0
    SSE2_PRED_H_16X16_ONE_LINE
    SSE2_PRED_H_16X16_ONE_LINE
    SSE2_PRED_H_16X16_ONE_LINE
    SSE2_PRED_H_16X16_ONE_LINE
    SSE2_PRED_H_16X16_ONE_LINE
    SSE2_PRED_H_16X16_ONE_LINE
    SSE2_PRED_H_16X16_ONE_LINE
    SSE2_PRED_H_16X16_ONE_LINE
    SSE2_PRED_H_16X16_ONE_LINE
    SSE2_PRED_H_16X16_ONE_LINE
    SSE2_PRED_H_16X16_ONE_LINE
    SSE2_PRED_H_16X16_ONE_LINE
    SSE2_PRED_H_16X16_ONE_LINE
    SSE2_PRED_H_16X16_ONE_LINE
    SSE2_PRED_H_16X16_ONE_LINE
    pop r3
    ret

;***********************************************************************
; void WelsI16x16LumaPredV_sse2(uint8_t *pred, uint8_t *pRef, int32_t stride);
;***********************************************************************
WELS_EXTERN WelsI16x16LumaPredV_sse2
    %assign push_num 0
    LOAD_3_PARA
    SIGN_EXTENSION r2, r2d
    sub     r1, r2
    movdqa  xmm0, [r1]

    movdqa  [r0], xmm0
    movdqa  [r0+10h], xmm0
    movdqa  [r0+20h], xmm0
    movdqa  [r0+30h], xmm0
    movdqa  [r0+40h], xmm0
    movdqa  [r0+50h], xmm0
    movdqa  [r0+60h], xmm0
    movdqa  [r0+70h], xmm0
    movdqa  [r0+80h], xmm0
    movdqa  [r0+90h], xmm0
    movdqa  [r0+160], xmm0
    movdqa  [r0+176], xmm0
    movdqa  [r0+192], xmm0
    movdqa  [r0+208], xmm0
    movdqa  [r0+224], xmm0
    movdqa  [r0+240], xmm0

    ret

