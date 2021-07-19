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
;*  Abstract
;*      WelsDctFourT4_sse2
;*
;*  History
;*      8/4/2009 Created
;*
;*
;*************************************************************************/

%include "asm_inc.asm"

SECTION .text

;void WelsBlockZero16x16_sse2(int16_t * block, int32_t stride);
WELS_EXTERN WelsBlockZero16x16_sse2
    %assign  push_num 0
    LOAD_2_PARA
    SIGN_EXTENSION r1, r1d
    shl     r1, 1
    pxor    xmm0, xmm0
%rep 16
    movdqa  [r0], xmm0
    movdqa  [r0+16], xmm0
    add     r0, r1
%endrep
    ret

;void WelsBlockZero8x8_sse2(int16_t * block, int32_t stride);
WELS_EXTERN WelsBlockZero8x8_sse2
    %assign  push_num 0
    LOAD_2_PARA
    SIGN_EXTENSION r1, r1d
    shl     r1, 1
    pxor    xmm0, xmm0
%rep 8
    movdqa  [r0], xmm0
    add     r0, r1
%endrep
    ret
