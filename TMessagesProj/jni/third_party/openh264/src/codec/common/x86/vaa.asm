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
;*  vaa.asm
;*
;*  Abstract
;*      sse2 for pVaa routines
;*
;*  History
;*      04/14/2010  Created
;*      06/07/2010  Added AnalysisVaaInfoIntra_sse2(ssse3)
;*      06/10/2010  Tune rc_sad_frame_sse2 and got about 40% improvement
;*      08/11/2010  Added abs_difference_mbrow_sse2 & sum_sqrsum_mbrow_sse2
;*
;*************************************************************************/
%include "asm_inc.asm"


;***********************************************************************
; Macros and other preprocessor constants
;***********************************************************************

; by comparing it outperforms than phaddw(SSSE3) sets
%macro SUM_WORD_8x2_SSE2    2   ; dst(pSrc), tmp
    ; @sum_8x2 begin
    pshufd %2, %1, 04Eh ; 01001110 B
    paddw %1, %2
    pshuflw %2, %1, 04Eh    ; 01001110 B
    paddw %1, %2
    pshuflw %2, %1, 0B1h    ; 10110001 B
    paddw %1, %2
    ; end of @sum_8x2
%endmacro   ; END of SUM_WORD_8x2_SSE2


%macro VAA_AVG_BLOCK_SSE2 6 ; dst, t0, t1, t2, t3, t4
    movdqa %1, [r0    ] ; line 0
    movdqa %2, [r0+r1]  ; line 1
    movdqa %3, %1
    punpcklbw %1, xmm7
    punpckhbw %3, xmm7
    movdqa %4, %2
    punpcklbw %4, xmm7
    punpckhbw %2, xmm7
    paddw %1, %4
    paddw %2, %3
    movdqa %3, [r0+r2]  ; line 2
    movdqa %4, [r0+r3]  ; line 3
    movdqa %5, %3
    punpcklbw %3, xmm7
    punpckhbw %5, xmm7
    movdqa %6, %4
    punpcklbw %6, xmm7
    punpckhbw %4, xmm7
    paddw %3, %6
    paddw %4, %5
    paddw %1, %3    ; block 0, 1
    paddw %2, %4    ; block 2, 3
    pshufd %3, %1, 0B1h
    pshufd %4, %2, 0B1h
    paddw %1, %3
    paddw %2, %4
    movdqa %3, %1
    movdqa %4, %2
    pshuflw %5, %1, 0B1h
    pshufhw %6, %3, 0B1h
    paddw %1, %5
    paddw %3, %6
    pshuflw %5, %2, 0B1h
    pshufhw %6, %4, 0B1h
    paddw %2, %5
    paddw %4, %6
    punpcklwd %1, %2
    punpckhwd %3, %4
    punpcklwd %1, %3
    psraw %1, $04
%endmacro

%macro VAA_AVG_BLOCK_SSSE3 6 ; dst, t0, t1, t2, t3, t4
    movdqa %1, [r0    ] ; line 0
    movdqa %2, [r0+r1]  ; line 1
    movdqa %3, %1
    punpcklbw %1, xmm7
    punpckhbw %3, xmm7
    movdqa %4, %2
    punpcklbw %4, xmm7
    punpckhbw %2, xmm7
    paddw %1, %4
    paddw %2, %3
    movdqa %3, [r0+r2]  ; line 2
    movdqa %4, [r0+r3]  ; line 3
    movdqa %5, %3
    punpcklbw %3, xmm7
    punpckhbw %5, xmm7
    movdqa %6, %4
    punpcklbw %6, xmm7
    punpckhbw %4, xmm7
    paddw %3, %6
    paddw %4, %5
    paddw %1, %3    ; block 0, 1
    paddw %2, %4    ; block 2, 3
    phaddw %1, %2   ; block[0]: 0-15, 16-31; block[1]: 32-47, 48-63; ..
    phaddw %1, xmm7 ; block[0]: 0-15; block[1]: 16-31; block[2]: 32-47; block[3]: 48-63; ....
    psraw %1, $04
%endmacro



;***********************************************************************
; Code
;***********************************************************************

SECTION .text

; , 6/7/2010

;***********************************************************************
;   int32_t AnalysisVaaInfoIntra_sse2(  uint8_t *pDataY, const int32_t iLineSize );
;***********************************************************************
WELS_EXTERN AnalysisVaaInfoIntra_sse2

    %assign push_num 0
    LOAD_2_PARA
    PUSH_XMM 8
    SIGN_EXTENSION r1,r1d

%ifdef X86_32
    push r3
    push r4
    push r5
    push r6
    %assign push_num push_num+4
%endif

    mov  r5,r7
    and  r5,0fh
    sub  r7,r5
    sub  r7,32


    mov r2,r1
    sal r2,$01   ;r2 = 2*iLineSize
    mov r3,r2
    add r3,r1   ;r3 = 3*iLineSize

    mov r4,r2
    sal r4,$01   ;r4 = 4*iLineSize

    pxor xmm7, xmm7

    ; loops
    VAA_AVG_BLOCK_SSE2 xmm0, xmm1, xmm2, xmm3, xmm4, xmm5
    movq [r7], xmm0

    lea r0, [r0+r4]
    VAA_AVG_BLOCK_SSE2 xmm0, xmm1, xmm2, xmm3, xmm4, xmm5
    movq [r7+8], xmm0

    lea r0, [r0+r4]
    VAA_AVG_BLOCK_SSE2 xmm0, xmm1, xmm2, xmm3, xmm4, xmm5
    movq [r7+16], xmm0

    lea r0, [r0+r4]
    VAA_AVG_BLOCK_SSE2 xmm0, xmm1, xmm2, xmm3, xmm4, xmm5
    movq [r7+24], xmm0

    movdqa xmm0, [r7]       ; block 0~7
    movdqa xmm1, [r7+16]    ; block 8~15
    movdqa xmm2, xmm0
    paddw xmm0, xmm1
    SUM_WORD_8x2_SSE2 xmm0, xmm3

    pmullw xmm1, xmm1
    pmullw xmm2, xmm2
    movdqa xmm3, xmm1
    movdqa xmm4, xmm2
    punpcklwd xmm1, xmm7
    punpckhwd xmm3, xmm7
    punpcklwd xmm2, xmm7
    punpckhwd xmm4, xmm7
    paddd xmm1, xmm2
    paddd xmm3, xmm4
    paddd xmm1, xmm3
    pshufd xmm2, xmm1, 01Bh
    paddd xmm1, xmm2
    pshufd xmm2, xmm1, 0B1h
    paddd xmm1, xmm2



    movd r2d, xmm0
    and r2, 0ffffh      ; effective low work truncated
    mov r3, r2
    imul r2, r3
    sar r2, $04
    movd retrd, xmm1
    sub retrd, r2d

    add r7,32
    add r7,r5

%ifdef X86_32
    pop r6
    pop r5
    pop r4
    pop r3
%endif
    POP_XMM

    ret

;***********************************************************************
;   int32_t AnalysisVaaInfoIntra_ssse3( uint8_t *pDataY, const int32_t iLineSize );
;***********************************************************************
WELS_EXTERN AnalysisVaaInfoIntra_ssse3

    %assign push_num 0
    LOAD_2_PARA
    PUSH_XMM 8
    SIGN_EXTENSION r1,r1d

%ifdef X86_32
    push r3
    push r4
    push r5
    push r6
    %assign push_num push_num+4
%endif

    mov  r5,r7
    and  r5,0fh
    sub  r7,r5
    sub  r7,32


    mov r2,r1
    sal r2,$01   ;r2 = 2*iLineSize
    mov r3,r2
    add r3,r1   ;r3 = 3*iLineSize

    mov r4,r2
    sal r4,$01   ;r4 = 4*iLineSize

    pxor xmm7, xmm7

    ; loops
    VAA_AVG_BLOCK_SSSE3 xmm0, xmm1, xmm2, xmm3, xmm4, xmm5
    movq [r7],xmm0

    lea r0,[r0+r4]
    VAA_AVG_BLOCK_SSSE3 xmm1, xmm2, xmm3, xmm4, xmm5, xmm6
    movq [r7+8],xmm1


    lea r0,[r0+r4]
    VAA_AVG_BLOCK_SSSE3 xmm0, xmm1, xmm2, xmm3, xmm4, xmm5
    movq [r7+16],xmm0

    lea r0,[r0+r4]
    VAA_AVG_BLOCK_SSSE3 xmm1, xmm2, xmm3, xmm4, xmm5, xmm6
    movq [r7+24],xmm1


    movdqa xmm0,[r7]
    movdqa xmm1,[r7+16]
    movdqa xmm2, xmm0
    paddw xmm0, xmm1
    SUM_WORD_8x2_SSE2 xmm0, xmm3    ; better performance than that of phaddw sets

    pmullw xmm1, xmm1
    pmullw xmm2, xmm2
    movdqa xmm3, xmm1
    movdqa xmm4, xmm2
    punpcklwd xmm1, xmm7
    punpckhwd xmm3, xmm7
    punpcklwd xmm2, xmm7
    punpckhwd xmm4, xmm7
    paddd xmm1, xmm2
    paddd xmm3, xmm4
    paddd xmm1, xmm3
    pshufd xmm2, xmm1, 01Bh
    paddd xmm1, xmm2
    pshufd xmm2, xmm1, 0B1h
    paddd xmm1, xmm2


    movd r2d, xmm0
    and r2, 0ffffh          ; effective low work truncated
    mov r3, r2
    imul r2, r3
    sar r2, $04
    movd retrd, xmm1
    sub retrd, r2d

    add r7,32
    add r7,r5
%ifdef X86_32
    pop r6
    pop r5
    pop r4
    pop r3
%endif
    POP_XMM

    ret

;***********************************************************************
;   uint8_t MdInterAnalysisVaaInfo_sse41( int32_t *pSad8x8 )
;***********************************************************************
WELS_EXTERN MdInterAnalysisVaaInfo_sse41
    %assign push_num 0
    LOAD_1_PARA
    movdqa xmm0,[r0]
    pshufd xmm1, xmm0, 01Bh
    paddd xmm1, xmm0
    pshufd xmm2, xmm1, 0B1h
    paddd xmm1, xmm2
    psrad xmm1, 02h     ; iAverageSad
    movdqa xmm2, xmm1
    psrad xmm2, 06h
    movdqa xmm3, xmm0   ; iSadBlock
    psrad xmm3, 06h
    psubd xmm3, xmm2
    pmulld xmm3, xmm3   ; [comment]: pmulld from SSE4.1 instruction sets
    pshufd xmm4, xmm3, 01Bh
    paddd xmm4, xmm3
    pshufd xmm3, xmm4, 0B1h
    paddd xmm3, xmm4
    movd r0d, xmm3
    cmp r0d, 20 ; INTER_VARIANCE_SAD_THRESHOLD

    jb near .threshold_exit
    pshufd xmm0, xmm0, 01Bh
    pcmpgtd xmm0, xmm1  ; iSadBlock > iAverageSad
    movmskps retrd, xmm0
    ret
.threshold_exit:
    mov retrd, 15
    ret

;***********************************************************************
;   uint8_t MdInterAnalysisVaaInfo_sse2( int32_t *pSad8x8 )
;***********************************************************************
WELS_EXTERN MdInterAnalysisVaaInfo_sse2
    %assign push_num 0
    LOAD_1_PARA
    movdqa xmm0, [r0]
    pshufd xmm1, xmm0, 01Bh
    paddd xmm1, xmm0
    pshufd xmm2, xmm1, 0B1h
    paddd xmm1, xmm2
    psrad xmm1, 02h     ; iAverageSad
    movdqa xmm2, xmm1
    psrad xmm2, 06h
    movdqa xmm3, xmm0   ; iSadBlock
    psrad xmm3, 06h
    psubd xmm3, xmm2

    ; to replace pmulld functionality as below
    movdqa xmm2, xmm3
    pmuludq xmm2, xmm3
    pshufd xmm4, xmm3, 0B1h
    pmuludq xmm4, xmm4
    movdqa xmm5, xmm2
    punpckldq xmm5, xmm4
    punpckhdq xmm2, xmm4
    punpcklqdq xmm5, xmm2

    pshufd xmm4, xmm5, 01Bh
    paddd xmm4, xmm5
    pshufd xmm5, xmm4, 0B1h
    paddd xmm5, xmm4

    movd r0d, xmm5
    cmp r0d, 20 ; INTER_VARIANCE_SAD_THRESHOLD
    jb near .threshold_exit
    pshufd xmm0, xmm0, 01Bh
    pcmpgtd xmm0, xmm1  ; iSadBlock > iAverageSad
    movmskps retrd, xmm0
    ret
.threshold_exit:
    mov retrd, 15
    ret
