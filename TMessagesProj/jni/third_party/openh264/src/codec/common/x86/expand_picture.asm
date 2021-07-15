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
;*  expand_picture.asm
;*
;*  Abstract
;*      mmxext/sse for expand_frame
;*
;*  History
;*      09/25/2009 Created
;*
;*
;*************************************************************************/

%include "asm_inc.asm"



;***********************************************************************
; Macros and other preprocessor constants
;***********************************************************************

;***********************************************************************
; Code
;***********************************************************************



SECTION .text


;;;;;;;expanding result;;;;;;;

;aaaa|attttttttttttttttb|bbbb
;aaaa|attttttttttttttttb|bbbb
;aaaa|attttttttttttttttb|bbbb
;aaaa|attttttttttttttttb|bbbb
;----------------------------
;aaaa|attttttttttttttttb|bbbb
;llll|l                r|rrrr
;llll|l                r|rrrr
;llll|l                r|rrrr
;llll|l                r|rrrr
;llll|l                r|rrrr
;cccc|ceeeeeeeeeeeeeeeed|dddd
;----------------------------
;cccc|ceeeeeeeeeeeeeeeed|dddd
;cccc|ceeeeeeeeeeeeeeeed|dddd
;cccc|ceeeeeeeeeeeeeeeed|dddd
;cccc|ceeeeeeeeeeeeeeeed|dddd

%macro mov_line_8x4_mmx     3   ; dst, stride, mm?
    movq [%1], %3
    movq [%1+%2], %3
    lea %1, [%1+2*%2]
    movq [%1], %3
    movq [%1+%2], %3
    lea %1, [%1+2*%2]
%endmacro

%macro mov_line_end8x4_mmx      3   ; dst, stride, mm?
    movq [%1], %3
    movq [%1+%2], %3
    lea %1, [%1+2*%2]
    movq [%1], %3
    movq [%1+%2], %3
    lea %1, [%1+%2]
%endmacro

%macro mov_line_16x4_sse2   4   ; dst, stride, xmm?, u/a
    movdq%4 [%1], %3        ; top(bottom)_0
    movdq%4 [%1+%2], %3     ; top(bottom)_1
    lea %1, [%1+2*%2]
    movdq%4 [%1], %3        ; top(bottom)_2
    movdq%4 [%1+%2], %3     ; top(bottom)_3
    lea %1, [%1+2*%2]
%endmacro

%macro mov_line_end16x4_sse2    4   ; dst, stride, xmm?, u/a
    movdq%4 [%1], %3        ; top(bottom)_0
    movdq%4 [%1+%2], %3     ; top(bottom)_1
    lea %1, [%1+2*%2]
    movdq%4 [%1], %3        ; top(bottom)_2
    movdq%4 [%1+%2], %3     ; top(bottom)_3
    lea %1, [%1+%2]
%endmacro

%macro mov_line_32x4_sse2   3   ; dst, stride, xmm?
    movdqa [%1], %3         ; top(bottom)_0
    movdqa [%1+16], %3      ; top(bottom)_0
    movdqa [%1+%2], %3      ; top(bottom)_1
    movdqa [%1+%2+16], %3       ; top(bottom)_1
    lea %1, [%1+2*%2]
    movdqa [%1], %3         ; top(bottom)_2
    movdqa [%1+16], %3      ; top(bottom)_2
    movdqa [%1+%2], %3      ; top(bottom)_3
    movdqa [%1+%2+16], %3       ; top(bottom)_3
    lea %1, [%1+2*%2]
%endmacro

%macro mov_line_end32x4_sse2    3   ; dst, stride, xmm?
    movdqa [%1], %3         ; top(bottom)_0
    movdqa [%1+16], %3      ; top(bottom)_0
    movdqa [%1+%2], %3      ; top(bottom)_1
    movdqa [%1+%2+16], %3       ; top(bottom)_1
    lea %1, [%1+2*%2]
    movdqa [%1], %3         ; top(bottom)_2
    movdqa [%1+16], %3      ; top(bottom)_2
    movdqa [%1+%2], %3      ; top(bottom)_3
    movdqa [%1+%2+16], %3       ; top(bottom)_3
    lea %1, [%1+%2]
%endmacro

%macro exp_top_bottom_sse2  1   ; iPaddingSize [luma(32)/chroma(16)]
    ;r2 [width/16(8)]
    ;r0 [pSrc +0], r5 [pSrc -width] r1[-stride], 32(16) ;top
    ;r3 [pSrc +(h-1)*stride], r4 [pSrc + (h+31)*stride],32(16); bottom

%if %1 == 32        ; for luma
    sar r2, 04h     ; width / 16(8) pixels
.top_bottom_loops:
    ; top
    movdqa xmm0, [r0]       ; first line of picture pData
    mov_line_16x4_sse2 r5, r1, xmm0, a  ; dst, stride, xmm?
    mov_line_16x4_sse2 r5, r1, xmm0, a
    mov_line_16x4_sse2 r5, r1, xmm0, a
    mov_line_16x4_sse2 r5, r1, xmm0, a
    mov_line_16x4_sse2 r5, r1, xmm0, a  ; dst, stride, xmm?
    mov_line_16x4_sse2 r5, r1, xmm0, a
    mov_line_16x4_sse2 r5, r1, xmm0, a
    mov_line_end16x4_sse2 r5, r1, xmm0, a

    ; bottom
    movdqa xmm1, [r3]       ; last line of picture pData
    mov_line_16x4_sse2 r4, r1, xmm1, a  ; dst, stride, xmm?
    mov_line_16x4_sse2 r4, r1, xmm1, a
    mov_line_16x4_sse2 r4, r1, xmm1, a
    mov_line_16x4_sse2 r4, r1, xmm1, a
    mov_line_16x4_sse2 r4, r1, xmm1, a  ; dst, stride, xmm?
    mov_line_16x4_sse2 r4, r1, xmm1, a
    mov_line_16x4_sse2 r4, r1, xmm1, a
    mov_line_end16x4_sse2 r4, r1, xmm1, a

    lea r0, [r0+16]     ; top pSrc
    lea r5, [r5+16]     ; top dst
    lea r3, [r3+16]     ; bottom pSrc
    lea r4, [r4+16]     ; bottom dst
    neg r1          ; positive/negative stride need for next loop?

    dec r2
    jnz near .top_bottom_loops
%elif %1 == 16  ; for chroma ??
    mov r6, r2
    sar r2, 04h     ; (width / 16) pixels
.top_bottom_loops:
    ; top
    movdqa xmm0, [r0]       ; first line of picture pData
    mov_line_16x4_sse2 r5, r1, xmm0, a  ; dst, stride, xmm?
    mov_line_16x4_sse2 r5, r1, xmm0, a
    mov_line_16x4_sse2 r5, r1, xmm0, a
    mov_line_end16x4_sse2 r5, r1, xmm0, a

    ; bottom
    movdqa xmm1, [r3]       ; last line of picture pData
    mov_line_16x4_sse2 r4, r1, xmm1, a  ; dst, stride, xmm?
    mov_line_16x4_sse2 r4, r1, xmm1, a
    mov_line_16x4_sse2 r4, r1, xmm1, a
    mov_line_end16x4_sse2 r4, r1, xmm1, a

    lea r0, [r0+16]     ; top pSrc
    lea r5, [r5+16]     ; top dst
    lea r3, [r3+16]     ; bottom pSrc
    lea r4, [r4+16]     ; bottom dst
    neg r1          ; positive/negative stride need for next loop?

    dec r2
    jnz near .top_bottom_loops

    ; for remaining 8 bytes
    and r6, 0fh     ; any 8 bytes left?
    test r6, r6
    jz near .to_be_continued    ; no left to exit here

    ; top
    movq mm0, [r0]      ; remained 8 byte
    mov_line_8x4_mmx r5, r1, mm0    ; dst, stride, mm?
    mov_line_8x4_mmx r5, r1, mm0    ; dst, stride, mm?
    mov_line_8x4_mmx r5, r1, mm0    ; dst, stride, mm?
    mov_line_end8x4_mmx r5, r1, mm0 ; dst, stride, mm?
    ; bottom
    movq mm1, [r3]
    mov_line_8x4_mmx r4, r1, mm1    ; dst, stride, mm?
    mov_line_8x4_mmx r4, r1, mm1    ; dst, stride, mm?
    mov_line_8x4_mmx r4, r1, mm1    ; dst, stride, mm?
    mov_line_end8x4_mmx r4, r1, mm1 ; dst, stride, mm?
    WELSEMMS

.to_be_continued:
%endif
%endmacro

%macro exp_left_right_sse2  2   ; iPaddingSize [luma(32)/chroma(16)], u/a
    ;r6 [height]
    ;r0 [pSrc+0]  r5[pSrc-32] r1[stride]
    ;r3 [pSrc+(w-1)] r4[pSrc+w]

%if %1 == 32        ; for luma
.left_right_loops:
    ; left
    movzx r2d, byte [r0]        ; pixel pData for left border
    SSE2_Copy16Times    xmm0, r2d               ; dst, tmp, pSrc [generic register name: a/b/c/d]
    movdqa [r5], xmm0
    movdqa [r5+16], xmm0

    ; right
    movzx r2d, byte [r3]
    SSE2_Copy16Times    xmm1, r2d               ; dst, tmp, pSrc [generic register name: a/b/c/d]
    movdqa [r4], xmm1
    movdqa [r4+16], xmm1

    lea r0, [r0+r1]     ; left pSrc
    lea r5, [r5+r1]     ; left dst
    lea r3, [r3+r1]     ; right pSrc
    lea r4, [r4+r1]     ; right dst

    dec r6
    jnz near .left_right_loops
%elif %1 == 16  ; for chroma ??
.left_right_loops:
    ; left
    movzx r2d, byte [r0]        ; pixel pData for left border
    SSE2_Copy16Times    xmm0, r2d               ; dst, tmp, pSrc [generic register name: a/b/c/d]
    movdqa [r5], xmm0

    ; right
    movzx r2d, byte [r3]
    SSE2_Copy16Times    xmm1, r2d               ; dst, tmp, pSrc [generic register name: a/b/c/d]
    movdq%2 [r4], xmm1                              ; might not be aligned 16 bytes in case chroma planes

    lea r0, [r0+r1]     ; left pSrc
    lea r5, [r5+r1]     ; left dst
    lea r3, [r3+r1]     ; right pSrc
    lea r4, [r4+r1]     ; right dst

    dec r6
    jnz near .left_right_loops
%endif
%endmacro

%macro exp_cross_sse2   2   ; iPaddingSize [luma(32)/chroma(16)], u/a
    ; top-left: (x)mm3, top-right: (x)mm4, bottom-left: (x)mm5, bottom-right: (x)mm6
    ; edi: TL, ebp: TR, eax: BL, ebx: BR, ecx, -stride
    ;r3:TL ,r4:TR,r5:BL,r6:BR r1:-stride
%if %1 == 32        ; luma
    ; TL
    mov_line_32x4_sse2  r3, r1, xmm3    ; dst, stride, xmm?
    mov_line_32x4_sse2  r3, r1, xmm3    ; dst, stride, xmm?
    mov_line_32x4_sse2  r3, r1, xmm3    ; dst, stride, xmm?
    mov_line_32x4_sse2  r3, r1, xmm3    ; dst, stride, xmm?
    mov_line_32x4_sse2  r3, r1, xmm3    ; dst, stride, xmm?
    mov_line_32x4_sse2  r3, r1, xmm3    ; dst, stride, xmm?
    mov_line_32x4_sse2  r3, r1, xmm3    ; dst, stride, xmm?
    mov_line_end32x4_sse2   r3, r1, xmm3    ; dst, stride, xmm?

    ; TR
    mov_line_32x4_sse2  r4, r1, xmm4    ; dst, stride, xmm?
    mov_line_32x4_sse2  r4, r1, xmm4    ; dst, stride, xmm?
    mov_line_32x4_sse2  r4, r1, xmm4    ; dst, stride, xmm?
    mov_line_32x4_sse2  r4, r1, xmm4    ; dst, stride, xmm?
    mov_line_32x4_sse2  r4, r1, xmm4    ; dst, stride, xmm?
    mov_line_32x4_sse2  r4, r1, xmm4    ; dst, stride, xmm?
    mov_line_32x4_sse2  r4, r1, xmm4    ; dst, stride, xmm?
    mov_line_end32x4_sse2   r4, r1, xmm4    ; dst, stride, xmm?

    ; BL
    mov_line_32x4_sse2  r5, r1, xmm5    ; dst, stride, xmm?
    mov_line_32x4_sse2  r5, r1, xmm5    ; dst, stride, xmm?
    mov_line_32x4_sse2  r5, r1, xmm5    ; dst, stride, xmm?
    mov_line_32x4_sse2  r5, r1, xmm5    ; dst, stride, xmm?
    mov_line_32x4_sse2  r5, r1, xmm5    ; dst, stride, xmm?
    mov_line_32x4_sse2  r5, r1, xmm5    ; dst, stride, xmm?
    mov_line_32x4_sse2  r5, r1, xmm5    ; dst, stride, xmm?
    mov_line_end32x4_sse2   r5, r1, xmm5    ; dst, stride, xmm?

    ; BR
    mov_line_32x4_sse2  r6, r1, xmm6    ; dst, stride, xmm?
    mov_line_32x4_sse2  r6, r1, xmm6    ; dst, stride, xmm?
    mov_line_32x4_sse2  r6, r1, xmm6    ; dst, stride, xmm?
    mov_line_32x4_sse2  r6, r1, xmm6    ; dst, stride, xmm?
    mov_line_32x4_sse2  r6, r1, xmm6    ; dst, stride, xmm?
    mov_line_32x4_sse2  r6, r1, xmm6    ; dst, stride, xmm?
    mov_line_32x4_sse2  r6, r1, xmm6    ; dst, stride, xmm?
    mov_line_end32x4_sse2   r6, r1, xmm6    ; dst, stride, xmm?
%elif %1 == 16  ; chroma
    ; TL
    mov_line_16x4_sse2  r3, r1, xmm3, a ; dst, stride, xmm?
    mov_line_16x4_sse2  r3, r1, xmm3, a ; dst, stride, xmm?
    mov_line_16x4_sse2  r3, r1, xmm3, a ; dst, stride, xmm?
    mov_line_end16x4_sse2   r3, r1, xmm3, a ; dst, stride, xmm?

    ; TR
    mov_line_16x4_sse2  r4, r1, xmm4, %2    ; dst, stride, xmm?
    mov_line_16x4_sse2  r4, r1, xmm4, %2    ; dst, stride, xmm?
    mov_line_16x4_sse2  r4, r1, xmm4, %2    ; dst, stride, xmm?
    mov_line_end16x4_sse2 r4, r1, xmm4, %2  ; dst, stride, xmm?

    ; BL
    mov_line_16x4_sse2  r5, r1, xmm5, a ; dst, stride, xmm?
    mov_line_16x4_sse2  r5, r1, xmm5, a ; dst, stride, xmm?
    mov_line_16x4_sse2  r5, r1, xmm5, a ; dst, stride, xmm?
    mov_line_end16x4_sse2   r5, r1, xmm5, a ; dst, stride, xmm?

    ; BR
    mov_line_16x4_sse2  r6, r1, xmm6, %2    ; dst, stride, xmm?
    mov_line_16x4_sse2  r6, r1, xmm6, %2    ; dst, stride, xmm?
    mov_line_16x4_sse2  r6, r1, xmm6, %2    ; dst, stride, xmm?
    mov_line_end16x4_sse2   r6, r1, xmm6, %2    ; dst, stride, xmm?
%endif
%endmacro

;***********************************************************************----------------
; void ExpandPictureLuma_sse2(  uint8_t *pDst,
;                                   const int32_t iStride,
;                                   const int32_t iWidth,
;                                   const int32_t iHeight   );
;***********************************************************************----------------
WELS_EXTERN ExpandPictureLuma_sse2

    push r4
    push r5
    push r6

    %assign push_num 3
    LOAD_4_PARA
    PUSH_XMM 7

    SIGN_EXTENSION r1, r1d
    SIGN_EXTENSION r2, r2d
    SIGN_EXTENSION r3, r3d

    ;also prepare for cross border pData top-left:xmm3

    movzx r6d,byte[r0]
    SSE2_Copy16Times xmm3,r6d         ;xmm3: pSrc[0]

    neg r1
    lea r5,[r0+r1]              ;last line of top border r5= dst top  pSrc[-stride]
    neg r1

    push r3


    dec r3                      ;h-1
    imul r3,r1                  ;(h-1)*stride
    lea  r3,[r0+r3]             ;pSrc[(h-1)*stride]  r3 = src bottom

    mov r6,r1                    ;r6 = stride
    sal r6,05h                   ;r6 = 32*stride
    lea r4,[r3+r6]               ;r4 = dst bottom

    ;also prepare for cross border data: bottom-left with xmm5,bottom-right xmm6

    movzx r6d,byte [r3]             ;bottom-left
    SSE2_Copy16Times xmm5,r6d

    lea r6,[r3+r2-1]
    movzx r6d,byte [r6]
    SSE2_Copy16Times xmm6,r6d ;bottom-right

    neg r1  ;r1 = -stride

    push r0
    push r1
    push r2

    exp_top_bottom_sse2 32

    ; for both left and right border
    ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

    pop r2
    pop r1
    pop r0

    lea r5,[r0-32]                          ;left border dst  luma =32 chroma = -16

    lea r3,[r0+r2-1]                        ;right border src
    lea r4,[r3+1]                           ;right border dst

    ;prepare for cross border data: top-rigth with xmm4
    movzx r6d,byte [r3]                         ;top -rigth
    SSE2_Copy16Times xmm4,r6d

    neg r1   ;r1 = stride


    pop r6  ;  r6 = height



    push r0
    push r1
    push r2
    push r6

    exp_left_right_sse2  32,a

    pop r6
    pop r2
    pop r1
    pop r0

    ; for cross border [top-left, top-right, bottom-left, bottom-right]
    ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
    ; have done xmm3,..,xmm6 cross pData initialization above, perform pading as below, To be continued..

    neg r1  ;r1 = -stride
    lea r3,[r0-32]
    lea r3,[r3+r1]    ;last line of top-left border

    lea r4,[r0+r2]    ;psrc +width
    lea r4,[r4+r1]    ;psrc +width -stride


    neg r1  ;r1 = stride
    add r6,32         ;height +32(16) ,luma = 32, chroma = 16
    imul r6,r1

    lea r5,[r3+r6]    ;last line of bottom-left border
    lea r6,[r4+r6]    ;last line of botoom-right border

    neg r1 ; r1 = -stride

    ; for left & right border expanding
    exp_cross_sse2 32,a

    POP_XMM
    LOAD_4_PARA_POP

    pop r6
    pop r5
    pop r4

    %assign push_num 0


    ret

;***********************************************************************----------------
; void ExpandPictureChromaAlign_sse2(   uint8_t *pDst,
;                                       const int32_t iStride,
;                                       const int32_t iWidth,
;                                       const int32_t iHeight   );
;***********************************************************************----------------
WELS_EXTERN ExpandPictureChromaAlign_sse2

    push r4
    push r5
    push r6

    %assign push_num 3
    LOAD_4_PARA
    PUSH_XMM 7

    SIGN_EXTENSION r1,r1d
    SIGN_EXTENSION r2,r2d
    SIGN_EXTENSION r3,r3d

    ;also prepare for cross border pData top-left:xmm3

    movzx r6d,byte [r0]
    SSE2_Copy16Times xmm3,r6d         ;xmm3: pSrc[0]

    neg r1
    lea r5,[r0+r1]              ;last line of top border r5= dst top  pSrc[-stride]
    neg r1

    push r3


    dec r3                      ;h-1
    imul r3,r1                  ;(h-1)*stride
    lea  r3,[r0+r3]             ;pSrc[(h-1)*stride]  r3 = src bottom

    mov r6,r1                    ;r6 = stride
    sal r6,04h                   ;r6 = 32*stride
    lea r4,[r3+r6]               ;r4 = dst bottom

    ;also prepare for cross border data: bottom-left with xmm5,bottom-right xmm6

    movzx r6d,byte [r3]             ;bottom-left
    SSE2_Copy16Times xmm5,r6d

    lea r6,[r3+r2-1]
    movzx r6d,byte [r6]
    SSE2_Copy16Times xmm6,r6d ;bottom-right

    neg r1  ;r1 = -stride

    push r0
    push r1
    push r2

    exp_top_bottom_sse2 16

    ; for both left and right border
    ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

    pop r2
    pop r1
    pop r0

    lea r5,[r0-16]                          ;left border dst  luma =32 chroma = -16

    lea r3,[r0+r2-1]                        ;right border src
    lea r4,[r3+1]                           ;right border dst

    ;prepare for cross border data: top-rigth with xmm4
    movzx r6d,byte [r3]                         ;top -rigth
    SSE2_Copy16Times xmm4,r6d

    neg r1   ;r1 = stride


    pop r6  ;  r6 = height



    push r0
    push r1
    push r2
    push r6
    exp_left_right_sse2 16,a

    pop r6
    pop r2
    pop r1
    pop r0

    ; for cross border [top-left, top-right, bottom-left, bottom-right]
    ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
    ; have done xmm3,..,xmm6 cross pData initialization above, perform pading as below, To be continued..

    neg r1  ;r1 = -stride
    lea r3,[r0-16]
    lea r3,[r3+r1]    ;last line of top-left border

    lea r4,[r0+r2]    ;psrc +width
    lea r4,[r4+r1]    ;psrc +width -stride


    neg r1  ;r1 = stride
    add r6,16         ;height +32(16) ,luma = 32, chroma = 16
    imul r6,r1

    lea r5,[r3+r6]    ;last line of bottom-left border
    lea r6,[r4+r6]    ;last line of botoom-right border

    neg r1 ; r1 = -stride

    ; for left & right border expanding
    exp_cross_sse2 16,a

    POP_XMM
    LOAD_4_PARA_POP

    pop r6
    pop r5
    pop r4

    %assign push_num 0


    ret

;***********************************************************************----------------
; void ExpandPictureChromaUnalign_sse2( uint8_t *pDst,
;                                       const int32_t iStride,
;                                       const int32_t iWidth,
;                                       const int32_t iHeight   );
;***********************************************************************----------------
WELS_EXTERN ExpandPictureChromaUnalign_sse2
    push r4
    push r5
    push r6

    %assign push_num 3
    LOAD_4_PARA
    PUSH_XMM 7

    SIGN_EXTENSION r1,r1d
    SIGN_EXTENSION r2,r2d
    SIGN_EXTENSION r3,r3d

    ;also prepare for cross border pData top-left:xmm3

    movzx r6d,byte [r0]
    SSE2_Copy16Times xmm3,r6d         ;xmm3: pSrc[0]

    neg r1
    lea r5,[r0+r1]              ;last line of top border r5= dst top  pSrc[-stride]
    neg r1

    push r3


    dec r3                      ;h-1
    imul r3,r1                  ;(h-1)*stride
    lea  r3,[r0+r3]             ;pSrc[(h-1)*stride]  r3 = src bottom

    mov r6,r1                    ;r6 = stride
    sal r6,04h                   ;r6 = 32*stride
    lea r4,[r3+r6]               ;r4 = dst bottom

    ;also prepare for cross border data: bottom-left with xmm5,bottom-right xmm6

    movzx r6d,byte [r3]             ;bottom-left
    SSE2_Copy16Times xmm5,r6d

    lea r6,[r3+r2-1]
    movzx r6d,byte [r6]
    SSE2_Copy16Times xmm6,r6d ;bottom-right

    neg r1  ;r1 = -stride

    push r0
    push r1
    push r2

    exp_top_bottom_sse2 16

    ; for both left and right border
    ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

    pop r2
    pop r1
    pop r0

    lea r5,[r0-16]                          ;left border dst  luma =32 chroma = -16

    lea r3,[r0+r2-1]                        ;right border src
    lea r4,[r3+1]                           ;right border dst

    ;prepare for cross border data: top-rigth with xmm4
    movzx r6d,byte [r3]                         ;top -rigth
    SSE2_Copy16Times xmm4,r6d

    neg r1   ;r1 = stride


    pop r6  ;  r6 = height



    push r0
    push r1
    push r2
    push r6
    exp_left_right_sse2 16,u

    pop r6
    pop r2
    pop r1
    pop r0

    ; for cross border [top-left, top-right, bottom-left, bottom-right]
    ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
    ; have done xmm3,..,xmm6 cross pData initialization above, perform pading as below, To be continued..

    neg r1  ;r1 = -stride
    lea r3,[r0-16]
    lea r3,[r3+r1]    ;last line of top-left border

    lea r4,[r0+r2]    ;psrc +width
    lea r4,[r4+r1]    ;psrc +width -stride


    neg r1  ;r1 = stride
    add r6,16         ;height +32(16) ,luma = 32, chroma = 16
    imul r6,r1

    lea r5,[r3+r6]    ;last line of bottom-left border
    lea r6,[r4+r6]    ;last line of botoom-right border

    neg r1 ; r1 = -stride

    ; for left & right border expanding
    exp_cross_sse2 16,u

    POP_XMM
    LOAD_4_PARA_POP

    pop r6
    pop r5
    pop r4

    %assign push_num 0


    ret
