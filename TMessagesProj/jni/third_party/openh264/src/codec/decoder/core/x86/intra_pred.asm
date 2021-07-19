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
;*  intra_pred.asm
;*
;*  Abstract
;*      sse2 and mmx function for intra predict operations(decoder)
;*
;*  History
;*      18/09/2009 Created
;*      19/11/2010 Added
;*                  WelsDecoderI16x16LumaPredDcTop_sse2, WelsDecoderI16x16LumaPredDcNA_sse2,
;*                  WelsDecoderIChromaPredDcLeft_mmx, WelsDecoderIChromaPredDcTop_sse2
;*                  and WelsDecoderIChromaPredDcNA_mmx
;*
;*
;*************************************************************************/

%include "asm_inc.asm"
;*******************************************************************************
; Local Data (Read Only)
;*******************************************************************************

%ifdef X86_32_PICASM
SECTION .text align=16
%else
SECTION .rodata align=16
%endif

align 16
sse2_plane_inc_minus dw -7, -6, -5, -4, -3, -2, -1, 0
align 16
sse2_plane_inc dw 1, 2, 3, 4, 5, 6, 7, 8
align 16
sse2_plane_dec dw 8, 7, 6, 5, 4, 3, 2, 1

; for chroma plane mode
sse2_plane_inc_c dw 1, 2, 3, 4
sse2_plane_dec_c dw 4, 3, 2, 1
align 16
sse2_plane_mul_b_c dw -3, -2, -1, 0, 1, 2, 3, 4

align 16
mmx_01bytes:        times 16    db 1

align 16
mmx_0x02: dw 0x02, 0x00, 0x00, 0x00

align 16
sse2_dc_0x80: times 16 db 0x80
align 16
sse2_wd_0x02: times 8 dw 0x02

;*******************************************************************************
; macros
;*******************************************************************************
;xmm0, xmm1, xmm2, eax, ecx
;lower 64 bits of xmm0 save the result
%macro SSE2_PRED_H_4X4_TWO_LINE 5
    movd        %1, [%4-1]
    movdqa      %3, %1
    punpcklbw   %1, %3
    movdqa      %3, %1
    punpcklbw   %1, %3

    ;add            %4, %5
    movd        %2, [%4+%5-1]
    movdqa      %3, %2
    punpcklbw   %2, %3
    movdqa      %3, %2
    punpcklbw   %2, %3
    punpckldq   %1, %2
%endmacro


%macro LOAD_COLUMN 6
    movd    %1, [%5]
    movd    %2, [%5+%6]
    punpcklbw %1,   %2
    lea     %5, [%5+2*%6]
    movd    %3, [%5]
    movd    %2, [%5+%6]
    punpcklbw %3,   %2
    punpcklwd %1,   %3
    lea     %5, [%5+2*%6]
    movd    %4, [%5]
    movd    %2, [%5+%6]
    punpcklbw %4,   %2
    lea     %5, [%5+2*%6]
    movd    %3, [%5]
    movd    %2, [%5+%6]
    lea     %5, [%5+2*%6]
    punpcklbw %3,   %2
    punpcklwd %4,   %3
    punpckhdq %1,   %4
%endmacro

%macro SUMW_HORIZON 3
    movhlps     %2, %1          ; x2 = xx xx xx xx d7 d6 d5 d4
    paddw       %1, %2          ; x1 = xx xx xx xx d37 d26 d15 d04
    punpcklwd   %1, %3          ; x1 =  d37  d26 d15 d04
    movhlps     %2, %1          ; x2 = xxxx xxxx d37 d26
    paddd       %1, %2          ; x1 = xxxx xxxx d1357 d0246
    pshuflw     %2, %1, 0x4e    ; x2 = xxxx xxxx d0246 d1357
    paddd       %1, %2          ; x1 = xxxx xxxx xxxx  d01234567
%endmacro

%macro COPY_16_TIMES 2
    movdqa      %2, [%1-16]
    psrldq      %2, 15
    pmuludq     %2, [pic(mmx_01bytes)]
    pshufd      %2, %2, 0
%endmacro

%macro COPY_16_TIMESS 3
    movdqa      %2, [%1+%3-16]
    psrldq      %2, 15
    pmuludq     %2, [pic(mmx_01bytes)]
    pshufd      %2, %2, 0
%endmacro

%macro LOAD_COLUMN_C 6
    movd    %1, [%5]
    movd    %2, [%5+%6]
    punpcklbw %1,%2
    lea     %5, [%5+2*%6]
    movd    %3, [%5]
    movd    %2, [%5+%6]
    punpcklbw %3,   %2
    punpckhwd %1,   %3
    lea     %5, [%5+2*%6]
%endmacro

%macro LOAD_2_LEFT_AND_ADD 0
    lea         r0, [r0+2*r1]
    movzx       r3, byte [r0-0x01]
    add         r2, r3
    movzx       r3, byte [r0+r1-0x01]
    add         r2, r3
%endmacro

;*******************************************************************************
; Code
;*******************************************************************************

SECTION .text


;*******************************************************************************
;   void WelsDecoderI4x4LumaPredH_sse2(uint8_t *pPred, const int32_t kiStride)
;
;   pPred must align to 16
;*******************************************************************************
WELS_EXTERN WelsDecoderI4x4LumaPredH_sse2
    %assign push_num 0
    INIT_X86_32_PIC r3
    LOAD_2_PARA
    SIGN_EXTENSION r1, r1d

    movzx       r2, byte [r0-1]
    movd        xmm0,   r2d
    pmuludq     xmm0,   [pic(mmx_01bytes)]

    movzx       r2, byte [r0+r1-1]
    movd        xmm1,   r2d
    pmuludq     xmm1,   [pic(mmx_01bytes)]

    lea         r0, [r0+r1]
    movzx       r2, byte [r0+r1-1]
    movd        xmm2,   r2d
    pmuludq     xmm2,   [pic(mmx_01bytes)]

    movzx       r2, byte [r0+2*r1-1]
    movd        xmm3,   r2d
    pmuludq     xmm3,   [pic(mmx_01bytes)]

    sub         r0,    r1
    movd        [r0], xmm0
    movd        [r0+r1], xmm1
    lea         r0, [r0+2*r1]
    movd        [r0], xmm2
    movd        [r0+r1], xmm3

    DEINIT_X86_32_PIC
    ret

;*******************************************************************************
; void WelsDecoderI16x16LumaPredPlane_sse2(uint8_t *pPred, const int32_t kiStride);
;*******************************************************************************
WELS_EXTERN WelsDecoderI16x16LumaPredPlane_sse2
    push r3
    push r4
    %assign push_num 2
    INIT_X86_32_PIC r5
    LOAD_2_PARA
    PUSH_XMM 8
    SIGN_EXTENSION r1, r1d
    mov r4, r0 ; save r0 in r4
    sub     r0, 1
    sub     r0, r1

    ;for H
    pxor    xmm7,   xmm7
    movq    xmm0,   [r0]
    movdqa  xmm5,   [pic(sse2_plane_dec)]
    punpcklbw xmm0, xmm7
    pmullw  xmm0,   xmm5
    movq    xmm1,   [r0 + 9]
    movdqa  xmm6,   [pic(sse2_plane_inc)]
    punpcklbw xmm1, xmm7
    pmullw  xmm1,   xmm6
    psubw   xmm1,   xmm0

    SUMW_HORIZON    xmm1,xmm0,xmm2
    movd    r2d,    xmm1        ; H += (i + 1) * (top[8 + i] - top[6 - i]);
    movsx   r2, r2w
    imul    r2, 5
    add     r2, 32
    sar     r2, 6           ; b = (5 * H + 32) >> 6;
    SSE2_Copy8Times xmm1, r2d   ; xmm1 = b,b,b,b,b,b,b,b

    movzx   r3, BYTE [r0+16]
    sub r0, 3
    LOAD_COLUMN     xmm0, xmm2, xmm3, xmm4, r0, r1

    add     r0, 3
    movzx   r2, BYTE [r0+8*r1]
    add     r3, r2
    shl     r3, 4           ;   a = (left[15*kiStride] + top[15]) << 4;

    sub r0, 3
    add     r0, r1
    LOAD_COLUMN     xmm7, xmm2, xmm3, xmm4, r0, r1
    pxor    xmm4,   xmm4
    punpckhbw xmm0, xmm4
    pmullw  xmm0,   xmm5
    punpckhbw xmm7, xmm4
    pmullw  xmm7,   xmm6
    psubw   xmm7,   xmm0

    ; Indicate that xmm2 is fully initialized. Its actual value doesn't
    ; matter in SUMW_HORIZON below, but after being used in LOAD_COLUMN above,
    ; valgrind thinks that xmm2 contains uninitalized data (if the columns outside
    ; of the left are uninitialized, such as in DecUT_IntraPrediction), which taints
    ; r2d below, even if actually isn't based on the uninitialized data.
    pxor xmm2, xmm2

    SUMW_HORIZON   xmm7,xmm0,xmm2
    movd    r2d,   xmm7         ; V
    movsx   r2, r2w

    imul    r2, 5
    add     r2, 32
    sar     r2, 6               ; c = (5 * V + 32) >> 6;
    SSE2_Copy8Times xmm4, r2d       ; xmm4 = c,c,c,c,c,c,c,c

    mov r0, r4
    add     r3, 16
    imul    r2, -7
    add     r3, r2      ; s = a + 16 + (-7)*c
    SSE2_Copy8Times xmm0, r3d       ; xmm0 = s,s,s,s,s,s,s,s

    xor     r2, r2
    movdqa  xmm5,   [pic(sse2_plane_inc_minus)]

get_i16x16_luma_pred_plane_sse2_1:
    movdqa  xmm2,   xmm1
    pmullw  xmm2,   xmm5
    paddw   xmm2,   xmm0
    psraw   xmm2,   5
    movdqa  xmm3,   xmm1
    pmullw  xmm3,   xmm6
    paddw   xmm3,   xmm0
    psraw   xmm3,   5
    packuswb xmm2,  xmm3
    movdqa  [r0],   xmm2
    paddw   xmm0,   xmm4
    add     r0, r1
    inc     r2
    cmp     r2, 16
    jnz get_i16x16_luma_pred_plane_sse2_1

    POP_XMM
    DEINIT_X86_32_PIC
    pop r4
    pop r3
    ret



;*******************************************************************************
; void WelsDecoderI16x16LumaPredH_sse2(uint8_t *pPred, const int32_t kiStride);
;*******************************************************************************

%macro SSE2_PRED_H_16X16_TWO_LINE_DEC 2
    lea     %1, [%1+%2*2]

    COPY_16_TIMES %1,   xmm0
    movdqa  [%1],   xmm0
    COPY_16_TIMESS %1,  xmm0,   %2
    movdqa  [%1+%2],    xmm0
%endmacro

WELS_EXTERN WelsDecoderI16x16LumaPredH_sse2
    %assign push_num 0
    INIT_X86_32_PIC_NOPRESERVE r2
    LOAD_2_PARA
    SIGN_EXTENSION r1, r1d

    COPY_16_TIMES r0,   xmm0
    movdqa  [r0],       xmm0
    COPY_16_TIMESS r0,  xmm0,   r1
    movdqa  [r0+r1],    xmm0

    SSE2_PRED_H_16X16_TWO_LINE_DEC r0, r1
    SSE2_PRED_H_16X16_TWO_LINE_DEC r0, r1
    SSE2_PRED_H_16X16_TWO_LINE_DEC r0, r1
    SSE2_PRED_H_16X16_TWO_LINE_DEC r0, r1
    SSE2_PRED_H_16X16_TWO_LINE_DEC r0, r1
    SSE2_PRED_H_16X16_TWO_LINE_DEC r0, r1
    SSE2_PRED_H_16X16_TWO_LINE_DEC r0, r1

    DEINIT_X86_32_PIC
    ret

;*******************************************************************************
; void WelsDecoderI16x16LumaPredV_sse2(uint8_t *pPred, const int32_t kiStride);
;*******************************************************************************
WELS_EXTERN WelsDecoderI16x16LumaPredV_sse2
    %assign push_num 0
    LOAD_2_PARA
    SIGN_EXTENSION r1, r1d

    sub     r0, r1
    movdqa  xmm0, [r0]

    movdqa  [r0+r1], xmm0
    lea     r0, [r0+2*r1]
    movdqa  [r0],     xmm0
    movdqa  [r0+r1], xmm0
    lea     r0, [r0+2*r1]
    movdqa  [r0],     xmm0
    movdqa  [r0+r1], xmm0
    lea     r0, [r0+2*r1]
    movdqa  [r0],     xmm0
    movdqa  [r0+r1], xmm0
    lea     r0, [r0+2*r1]
    movdqa  [r0],     xmm0
    movdqa  [r0+r1], xmm0
    lea     r0, [r0+2*r1]
    movdqa  [r0],     xmm0
    movdqa  [r0+r1], xmm0
    lea     r0, [r0+2*r1]
    movdqa  [r0],     xmm0
    movdqa  [r0+r1], xmm0
    lea     r0, [r0+2*r1]
    movdqa  [r0],     xmm0
    movdqa  [r0+r1], xmm0
    lea     r0, [r0+2*r1]
    movdqa  [r0],     xmm0

    ret

;*******************************************************************************
; void WelsDecoderIChromaPredPlane_sse2(uint8_t *pPred, const int32_t kiStride);
;*******************************************************************************
WELS_EXTERN WelsDecoderIChromaPredPlane_sse2
    push r3
    push r4
    %assign push_num 2
    INIT_X86_32_PIC r5
    LOAD_2_PARA
    PUSH_XMM 8
    SIGN_EXTENSION r1, r1d
    mov r4, r0
    sub     r0, 1
    sub     r0, r1

    pxor    mm7,    mm7
    movq    mm0,    [r0]
    movq    mm5,    [pic(sse2_plane_dec_c)]
    punpcklbw mm0,  mm7
    pmullw  mm0,    mm5
    movq    mm1,    [r0 + 5]
    movq    mm6,    [pic(sse2_plane_inc_c)]
    punpcklbw mm1,  mm7
    pmullw  mm1,    mm6
    psubw   mm1,    mm0

    movq2dq xmm1,   mm1
    pxor    xmm2,   xmm2
    SUMW_HORIZON    xmm1,xmm0,xmm2
    movd    r2d,    xmm1
    movsx   r2, r2w
    imul    r2, 17
    add     r2, 16
    sar     r2, 5           ; b = (17 * H + 16) >> 5;
    SSE2_Copy8Times xmm1, r2d   ; mm1 = b,b,b,b,b,b,b,b

    movzx   r3, BYTE [r0+8]
    sub r0, 3
    LOAD_COLUMN_C   mm0, mm2, mm3, mm4, r0, r1

    add     r0, 3
    movzx   r2, BYTE [r0+4*r1]
    add     r3, r2
    shl     r3, 4           ; a = (left[7*kiStride] + top[7]) << 4;

    sub r0, 3
    add     r0, r1
    LOAD_COLUMN_C   mm7, mm2, mm3, mm4, r0, r1
    pxor    mm4,    mm4
    punpckhbw mm0,  mm4
    pmullw  mm0,    mm5
    punpckhbw mm7,  mm4
    pmullw  mm7,    mm6
    psubw   mm7,    mm0

    movq2dq xmm7,   mm7
    pxor    xmm2,   xmm2
    SUMW_HORIZON    xmm7,xmm0,xmm2
    movd    r2d,    xmm7            ; V
    movsx   r2, r2w

    imul    r2, 17
    add     r2, 16
    sar     r2, 5               ; c = (17 * V + 16) >> 5;
    SSE2_Copy8Times xmm4, r2d       ; mm4 = c,c,c,c,c,c,c,c

    mov     r0, r4
    add     r3, 16
    imul    r2, -3
    add     r3, r2              ; s = a + 16 + (-3)*c
    SSE2_Copy8Times xmm0, r3d       ; xmm0 = s,s,s,s,s,s,s,s

    xor     r2, r2
    movdqa  xmm5,   [pic(sse2_plane_mul_b_c)]

get_i_chroma_pred_plane_sse2_1:
    movdqa  xmm2,   xmm1
    pmullw  xmm2,   xmm5
    paddw   xmm2,   xmm0
    psraw   xmm2,   5
    packuswb xmm2,  xmm2
    movq    [r0],   xmm2
    paddw   xmm0,   xmm4
    add     r0, r1
    inc     r2
    cmp     r2, 8
    jnz get_i_chroma_pred_plane_sse2_1

    POP_XMM
    DEINIT_X86_32_PIC
    pop r4
    pop r3
    WELSEMMS
    ret

;*******************************************************************************
;   0 |1 |2 |3 |4 |
;   6 |7 |8 |9 |10|
;   11|12|13|14|15|
;   16|17|18|19|20|
;   21|22|23|24|25|
;   7 is the start pixel of current 4x4 block
;   pPred[7] = ([6]+[0]*2+[1]+2)/4
;
;   void WelsDecoderI4x4LumaPredDDR_mmx(uint8_t *pPred, const int32_t kiStride)
;
;*******************************************************************************
WELS_EXTERN WelsDecoderI4x4LumaPredDDR_mmx
    %assign push_num 0
    INIT_X86_32_PIC r3
    LOAD_2_PARA
    SIGN_EXTENSION r1, r1d
    mov r2, r0

    movq        mm1,[r2+r1-8]       ;get value of 11,decreasing 8 is trying to improve the performance of movq mm1[8] = 11
    movq        mm2,[r2-8]          ;get value of 6 mm2[8] = 6
    sub     r2, r1          ;mov eax to above line of current block(postion of 1)
    punpckhbw   mm2,[r2-8]          ;mm2[8](high 8th byte of mm2) = [0](value of 0), mm2[7]= [6]
    movd        mm3,[r2]            ;get value 1, mm3[1] = [1],mm3[2]=[2],mm3[3]=[3]
    punpckhwd   mm1,mm2             ;mm1[8]=[0],mm1[7]=[6],mm1[6]=[11]
    psllq       mm3,18h             ;mm3[5]=[1]
    psrlq       mm1,28h             ;mm1[3]=[0],mm1[2]=[6],mm1[1]=[11]
    por         mm3,mm1             ;mm3[6]=[3],mm3[5]=[2],mm3[4]=[1],mm3[3]=[0],mm3[2]=[6],mm3[1]=[11]
    movq        mm1,mm3             ;mm1[6]=[3],mm1[5]=[2],mm1[4]=[1],mm1[3]=[0],mm1[2]=[6],mm1[1]=[11]
    lea         r2,[r2+r1*2-8h]     ;set eax point to 12
    movq        mm4,[r2+r1]     ;get value of 16, mm4[8]=[16]
    psllq       mm3,8               ;mm3[7]=[3],mm3[6]=[2],mm3[5]=[1],mm3[4]=[0],mm3[3]=[6],mm3[2]=[11],mm3[1]=0
    psrlq       mm4,38h             ;mm4[1]=[16]
    por         mm3,mm4             ;mm3[7]=[3],mm3[6]=[2],mm3[5]=[1],mm3[4]=[0],mm3[3]=[6],mm3[2]=[11],mm3[1]=[16]
    movq        mm2,mm3             ;mm2[7]=[3],mm2[6]=[2],mm2[5]=[1],mm2[4]=[0],mm2[3]=[6],mm2[2]=[11],mm2[1]=[16]
    movq        mm4,[r2+r1*2]       ;mm4[8]=[21]
    psllq       mm3,8               ;mm3[8]=[3],mm3[7]=[2],mm3[6]=[1],mm3[5]=[0],mm3[4]=[6],mm3[3]=[11],mm3[2]=[16],mm3[1]=0
    psrlq       mm4,38h             ;mm4[1]=[21]
    por         mm3,mm4             ;mm3[8]=[3],mm3[7]=[2],mm3[6]=[1],mm3[5]=[0],mm3[4]=[6],mm3[3]=[11],mm3[2]=[16],mm3[1]=[21]
    movq        mm4,mm3             ;mm4[8]=[3],mm4[7]=[2],mm4[6]=[1],mm4[5]=[0],mm4[4]=[6],mm4[3]=[11],mm4[2]=[16],mm4[1]=[21]
    pavgb       mm3,mm1             ;mm3=([11]+[21]+1)/2
    pxor        mm1,mm4             ;find odd value in the lowest bit of each byte
    pand        mm1,[pic(mmx_01bytes)]   ;set the odd bit
    psubusb     mm3,mm1             ;decrease 1 from odd bytes
    pavgb       mm2,mm3             ;mm2=(([11]+[21]+1)/2+1+[16])/2

    lea         r0,[r0+r1]
    movd        [r0+2*r1],mm2
    sub         r0,r1
    psrlq       mm2,8
    movd        [r0+2*r1],mm2
    psrlq       mm2,8
    movd        [r0+r1],mm2
    psrlq       mm2,8
    movd        [r0],mm2
    DEINIT_X86_32_PIC
    WELSEMMS
    ret


;*******************************************************************************
;   void WelsDecoderIChromaPredH_mmx(uint8_t *pPred, const int32_t kiStride)
;   copy 8 pixel of 8 line from left
;*******************************************************************************
%macro MMX_PRED_H_8X8_ONE_LINE 4
    movq        %1,     [%3-8]
    psrlq       %1,     38h

    pmullw      %1,     [pic(mmx_01bytes)]
    pshufw      %1,     %1, 0
    movq        [%4],   %1
%endmacro

%macro MMX_PRED_H_8X8_ONE_LINEE 4
    movq        %1,     [%3+r1-8]
    psrlq       %1,     38h

    pmullw      %1,     [pic(mmx_01bytes)]
    pshufw      %1,     %1, 0
    movq        [%4],   %1
%endmacro

WELS_EXTERN WelsDecoderIChromaPredH_mmx
    %assign push_num 0
    INIT_X86_32_PIC r3
    LOAD_2_PARA
    SIGN_EXTENSION r1, r1d
    mov r2, r0

    movq        mm0,    [r2-8]
    psrlq       mm0,    38h

    pmullw      mm0,        [pic(mmx_01bytes)]
    pshufw      mm0,    mm0,    0
    movq        [r0],   mm0

    MMX_PRED_H_8X8_ONE_LINEE mm0, mm1, r2, r0+r1

    lea         r2, [r2+r1*2]
    MMX_PRED_H_8X8_ONE_LINE mm0, mm1, r2, r0+2*r1

    lea         r0, [r0+2*r1]
    MMX_PRED_H_8X8_ONE_LINEE mm0, mm1, r2, r0+r1

    lea         r2, [r2+r1*2]
    MMX_PRED_H_8X8_ONE_LINE mm0, mm1, r2, r0+2*r1

    lea         r0, [r0+2*r1]
    MMX_PRED_H_8X8_ONE_LINEE mm0, mm1, r2, r0+r1

    lea         r2, [r2+r1*2]
    MMX_PRED_H_8X8_ONE_LINE mm0, mm1, r2, r0+2*r1

    lea         r0, [r0+2*r1]
    MMX_PRED_H_8X8_ONE_LINEE mm0, mm1, r2, r0+r1

    DEINIT_X86_32_PIC
    WELSEMMS
    ret


;*******************************************************************************
;   void WelsDecoderIChromaPredV_mmx(uint8_t *pPred, const int32_t kiStride)
;   copy 8 pixels from top 8 pixels
;*******************************************************************************
WELS_EXTERN WelsDecoderIChromaPredV_mmx
    %assign push_num 0
    LOAD_2_PARA
    SIGN_EXTENSION r1, r1d

    sub         r0,     r1
    movq        mm0,        [r0]

    movq        [r0+r1],        mm0
    movq        [r0+2*r1],  mm0
    lea         r0, [r0+2*r1]
    movq        [r0+r1],      mm0
    movq        [r0+2*r1],    mm0
    lea         r0, [r0+2*r1]
    movq        [r0+r1],      mm0
    movq        [r0+2*r1],    mm0
    lea         r0, [r0+2*r1]
    movq        [r0+r1],      mm0
    movq        [r0+2*r1],    mm0

    WELSEMMS
    ret


;*******************************************************************************
;   lt|t0|t1|t2|t3|
;   l0|
;   l1|
;   l2|
;   l3|
;   t3 will never been used
;   destination:
;   |a |b |c |d |
;   |e |f |a |b |
;   |g |h |e |f |
;   |i |j |g |h |

;   a = (1 + lt + l0)>>1
;   e = (1 + l0 + l1)>>1
;   g = (1 + l1 + l2)>>1
;   i = (1 + l2 + l3)>>1

;   d = (2 + t0 + (t1<<1) + t2)>>2
;   c = (2 + lt + (t0<<1) + t1)>>2
;   b = (2 + l0 + (lt<<1) + t0)>>2

;   f = (2 + l1 + (l0<<1) + lt)>>2
;   h = (2 + l2 + (l1<<1) + l0)>>2
;   j = (2 + l3 + (l2<<1) + l1)>>2
;   [b a f e h g j i] + [d c b a] --> mov to memory
;
;   void WelsDecoderI4x4LumaPredHD_mmx(uint8_t *pPred, const int32_t kiStride)
;*******************************************************************************
WELS_EXTERN WelsDecoderI4x4LumaPredHD_mmx
    %assign push_num 0
    INIT_X86_32_PIC r3
    LOAD_2_PARA
    SIGN_EXTENSION r1, r1d
    mov r2, r0
    sub         r2, r1
    movd        mm0, [r2-1]            ; mm0 = [xx xx xx xx t2 t1 t0 lt]
    psllq       mm0, 20h                ; mm0 = [t2 t1 t0 lt xx xx xx xx]

    movd        mm1, [r2+2*r1-4]
    punpcklbw   mm1, [r2+r1-4]        ; mm1[7] = l0, mm1[6] = l1
    lea         r2, [r2+2*r1]
    movd        mm2, [r2+2*r1-4]
    punpcklbw   mm2, [r2+r1-4]        ; mm2[7] = l2, mm2[6] = l3
    punpckhwd   mm2, mm1                ; mm2 = [l0 l1 l2 l3 xx xx xx xx]
    psrlq       mm2, 20h
    pxor        mm0, mm2                ; mm0 = [t2 t1 t0 lt l0 l1 l2 l3]

    movq        mm1, mm0
    psrlq       mm1, 10h                ; mm1 = [xx xx t2 t1 t0 lt l0 l1]
    movq        mm2, mm0
    psrlq       mm2, 8h                 ; mm2 = [xx t2 t1 t0 lt l0 l1 l2]
    movq        mm3, mm2
    movq        mm4, mm1
    pavgb       mm1, mm0

    pxor        mm4, mm0                ; find odd value in the lowest bit of each byte
    pand        mm4, [pic(mmx_01bytes)] ; set the odd bit
    psubusb     mm1, mm4                ; decrease 1 from odd bytes

    pavgb       mm2, mm1                ; mm2 = [xx xx d  c  b  f  h  j]

    movq        mm4, mm0
    pavgb       mm3, mm4                ; mm3 = [xx xx xx xx a  e  g  i]
    punpcklbw   mm3, mm2                ; mm3 = [b  a  f  e  h  g  j  i]

    psrlq       mm2, 20h
    psllq       mm2, 30h                ; mm2 = [d  c  0  0  0  0  0  0]
    movq        mm4, mm3
    psrlq       mm4, 10h                ; mm4 = [0  0  b  a  f  e  h  j]
    pxor        mm2, mm4                ; mm2 = [d  c  b  a  xx xx xx xx]
    psrlq       mm2, 20h                ; mm2 = [xx xx xx xx  d  c  b  a]

    movd        [r0], mm2
    lea         r0, [r0+r1]
    movd        [r0+2*r1], mm3
    sub         r0, r1
    psrlq       mm3, 10h
    movd        [r0+2*r1], mm3
    psrlq       mm3, 10h
    movd        [r0+r1], mm3
    DEINIT_X86_32_PIC
    WELSEMMS
    ret



;*******************************************************************************
;   lt|t0|t1|t2|t3|
;   l0|
;   l1|
;   l2|
;   l3|
;   t3 will never been used
;   destination:
;   |a |b |c |d |
;   |c |d |e |f |
;   |e |f |g |g |
;   |g |g |g |g |

;   a = (1 + l0 + l1)>>1
;   c = (1 + l1 + l2)>>1
;   e = (1 + l2 + l3)>>1
;   g = l3

;   b = (2 + l0 + (l1<<1) + l2)>>2
;   d = (2 + l1 + (l2<<1) + l3)>>2
;   f = (2 + l2 + (l3<<1) + l3)>>2

;   [g g f e d c b a] + [g g g g] --> mov to memory
;
;   void WelsDecoderI4x4LumaPredHU_mmx(uint8_t *pPred, const int32_t kiStride)
;*******************************************************************************
WELS_EXTERN WelsDecoderI4x4LumaPredHU_mmx
    %assign push_num 0
    INIT_X86_32_PIC r3
    LOAD_2_PARA
    SIGN_EXTENSION r1, r1d
    mov r2, r0

    movd        mm0, [r2-4]            ; mm0[3] = l0
    punpcklbw   mm0, [r2+r1-4]        ; mm0[7] = l1, mm0[6] = l0
    lea         r2, [r2+2*r1]
    movd        mm2, [r2-4]            ; mm2[3] = l2
    movd        mm4, [r2+r1-4]        ; mm4[3] = l3
    punpcklbw   mm2, mm4
    punpckhwd   mm0, mm2                ; mm0 = [l3 l2 l1 l0 xx xx xx xx]

    psrlq       mm4, 18h
    psllq       mm4, 38h                ; mm4 = [l3 xx xx xx xx xx xx xx]
    psrlq       mm0, 8h
    pxor        mm0, mm4                ; mm0 = [l3 l3 l2 l1 l0 xx xx xx]

    movq        mm1, mm0
    psllq       mm1, 8h                 ; mm1 = [l3 l2 l1 l0 xx xx xx xx]
    movq        mm3, mm1                ; mm3 = [l3 l2 l1 l0 xx xx xx xx]
    pavgb       mm1, mm0                ; mm1 = [g  e  c  a  xx xx xx xx]

    movq        mm2, mm0
    psllq       mm2, 10h                ; mm2 = [l2 l1 l0 xx xx xx xx xx]
    movq        mm5, mm2
    pavgb       mm2, mm0

    pxor        mm5, mm0                ; find odd value in the lowest bit of each byte
    pand        mm5, [pic(mmx_01bytes)] ; set the odd bit
    psubusb     mm2, mm5                ; decrease 1 from odd bytes

    pavgb       mm2, mm3                ; mm2 = [f  d  b  xx xx xx xx xx]

    psrlq       mm2, 8h
    pxor        mm2, mm4                ; mm2 = [g  f  d  b  xx xx xx xx]

    punpckhbw   mm1, mm2                ; mm1 = [g  g  f  e  d  c  b  a]
    punpckhbw   mm4, mm4                ; mm4 = [g  g  xx xx xx xx xx xx]
    punpckhbw   mm4, mm4                ; mm4 = [g  g  g  g  xx xx xx xx]

    psrlq       mm4, 20h
    lea         r0, [r0+r1]
    movd        [r0+2*r1], mm4

    sub         r0, r1
    movd        [r0], mm1
    psrlq       mm1, 10h
    movd        [r0+r1], mm1
    psrlq       mm1, 10h
    movd        [r0+2*r1], mm1
    DEINIT_X86_32_PIC
    WELSEMMS
    ret



;*******************************************************************************
;   lt|t0|t1|t2|t3|
;   l0|
;   l1|
;   l2|
;   l3|
;   l3 will never been used
;   destination:
;   |a |b |c |d |
;   |e |f |g |h |
;   |i |a |b |c |
;   |j |e |f |g |

;   a = (1 + lt + t0)>>1
;   b = (1 + t0 + t1)>>1
;   c = (1 + t1 + t2)>>1
;   d = (1 + t2 + t3)>>1

;   e = (2 + l0 + (lt<<1) + t0)>>2
;   f = (2 + lt + (t0<<1) + t1)>>2
;   g = (2 + t0 + (t1<<1) + t2)>>2

;   h = (2 + t1 + (t2<<1) + t3)>>2
;   i = (2 + lt + (l0<<1) + l1)>>2
;   j = (2 + l0 + (l1<<1) + l2)>>2
;
;   void WelsDecoderI4x4LumaPredVR_mmx(uint8_t *pPred, const int32_t kiStride)
;*******************************************************************************
WELS_EXTERN WelsDecoderI4x4LumaPredVR_mmx
    %assign push_num 0
    INIT_X86_32_PIC r3
    LOAD_2_PARA
    SIGN_EXTENSION r1, r1d
    mov r2, r0
    sub         r2, r1
    movq        mm0, [r2-1]            ; mm0 = [xx xx xx t3 t2 t1 t0 lt]
    psllq       mm0, 18h                ; mm0 = [t3 t2 t1 t0 lt xx xx xx]

    movd        mm1, [r2+2*r1-4]
    punpcklbw   mm1, [r2+r1-4]        ; mm1[7] = l0, mm1[6] = l1
    lea         r2, [r2+2*r1]
    movq        mm2, [r2+r1-8]        ; mm2[7] = l2
    punpckhwd   mm2, mm1                ; mm2 = [l0 l1 l2 xx xx xx xx xx]
    psrlq       mm2, 28h
    pxor        mm0, mm2                ; mm0 = [t3 t2 t1 t0 lt l0 l1 l2]

    movq        mm1, mm0
    psllq       mm1, 8h                 ; mm1 = [t2 t1 t0 lt l0 l1 l2 xx]
    pavgb       mm1, mm0                ; mm1 = [d  c  b  a  xx xx xx xx]

    movq        mm2, mm0
    psllq       mm2, 10h                ; mm2 = [t1 t0 lt l0 l1 l2 xx xx]
    movq        mm3, mm2
    pavgb       mm2, mm0

    pxor        mm3, mm0                ; find odd value in the lowest bit of each byte
    pand        mm3, [pic(mmx_01bytes)] ; set the odd bit
    psubusb     mm2, mm3                ; decrease 1 from odd bytes

    movq        mm3, mm0
    psllq       mm3, 8h                 ; mm3 = [t2 t1 t0 lt l0 l1 l2 xx]
    pavgb       mm3, mm2                ; mm3 = [h  g  f  e  i  j  xx xx]
    movq        mm2, mm3

    psrlq       mm1, 20h                ; mm1 = [xx xx xx xx d  c  b  a]
    movd        [r0], mm1

    psrlq       mm2, 20h                ; mm2 = [xx xx xx xx h  g  f  e]
    movd        [r0+r1], mm2

    movq        mm4, mm3
    psllq       mm4, 20h
    psrlq       mm4, 38h                ; mm4 = [xx xx xx xx xx xx xx i]

    movq        mm5, mm3
    psllq       mm5, 28h
    psrlq       mm5, 38h                ; mm5 = [xx xx xx xx xx xx xx j]

    psllq       mm1, 8h
    pxor        mm4, mm1                ; mm4 = [xx xx xx xx c  b  a  i]
    movd        [r0+2*r1], mm4

    psllq       mm2, 8h
    pxor        mm5, mm2                ; mm5 = [xx xx xx xx g  f  e  j]
    lea         r0, [r0+2*r1]
    movd        [r0+r1], mm5
    DEINIT_X86_32_PIC
    WELSEMMS
    ret

;*******************************************************************************
;   lt|t0|t1|t2|t3|t4|t5|t6|t7
;   l0|
;   l1|
;   l2|
;   l3|
;   lt,t0,t1,t2,t3 will never been used
;   destination:
;   |a |b |c |d |
;   |b |c |d |e |
;   |c |d |e |f |
;   |d |e |f |g |

;   a = (2 + t0 + t2 + (t1<<1))>>2
;   b = (2 + t1 + t3 + (t2<<1))>>2
;   c = (2 + t2 + t4 + (t3<<1))>>2
;   d = (2 + t3 + t5 + (t4<<1))>>2

;   e = (2 + t4 + t6 + (t5<<1))>>2
;   f = (2 + t5 + t7 + (t6<<1))>>2
;   g = (2 + t6 + t7 + (t7<<1))>>2

;   [g f e d c b a] --> mov to memory
;
;   void WelsDecoderI4x4LumaPredDDL_mmx(uint8_t *pPred, const int32_t kiStride)
;*******************************************************************************
WELS_EXTERN WelsDecoderI4x4LumaPredDDL_mmx
    %assign push_num 0
    INIT_X86_32_PIC r3
    LOAD_2_PARA
    SIGN_EXTENSION r1, r1d
    mov r2, r0
    sub         r2, r1
    movq        mm0, [r2]              ; mm0 = [t7 t6 t5 t4 t3 t2 t1 t0]
    movq        mm1, mm0
    movq        mm2, mm0

    movq        mm3, mm0
    psrlq       mm3, 38h
    psllq       mm3, 38h                ; mm3 = [t7 xx xx xx xx xx xx xx]

    psllq       mm1, 8h                 ; mm1 = [t6 t5 t4 t3 t2 t1 t0 xx]
    psrlq       mm2, 8h
    pxor        mm2, mm3                ; mm2 = [t7 t7 t6 t5 t4 t3 t2 t1]

    movq        mm3, mm1
    pavgb       mm1, mm2
    pxor        mm3, mm2                ; find odd value in the lowest bit of each byte
    pand        mm3, [pic(mmx_01bytes)] ; set the odd bit
    psubusb     mm1, mm3                ; decrease 1 from odd bytes

    pavgb       mm0, mm1                ; mm0 = [g f e d c b a xx]

    psrlq       mm0, 8h
    movd        [r0], mm0
    psrlq       mm0, 8h
    movd        [r0+r1], mm0
    psrlq       mm0, 8h
    movd        [r0+2*r1], mm0
    psrlq       mm0, 8h
    lea         r0, [r0+2*r1]
    movd        [r0+r1], mm0
    DEINIT_X86_32_PIC
    WELSEMMS
    ret


;*******************************************************************************
;   lt|t0|t1|t2|t3|t4|t5|t6|t7
;   l0|
;   l1|
;   l2|
;   l3|
;   lt,t0,t1,t2,t3 will never been used
;   destination:
;   |a |b |c |d |
;   |e |f |g |h |
;   |b |c |d |i |
;   |f |g |h |j |

;   a = (1 + t0 + t1)>>1
;   b = (1 + t1 + t2)>>1
;   c = (1 + t2 + t3)>>1
;   d = (1 + t3 + t4)>>1
;   i = (1 + t4 + t5)>>1

;   e = (2 + t0 + (t1<<1) + t2)>>2
;   f = (2 + t1 + (t2<<1) + t3)>>2
;   g = (2 + t2 + (t3<<1) + t4)>>2
;   h = (2 + t3 + (t4<<1) + t5)>>2
;   j = (2 + t4 + (t5<<1) + t6)>>2

;   [i d c b a] + [j h g f e] --> mov to memory
;
;   void WelsDecoderI4x4LumaPredVL_mmx(uint8_t *pPred, const int32_t kiStride)
;*******************************************************************************
WELS_EXTERN WelsDecoderI4x4LumaPredVL_mmx
    %assign push_num 0
    INIT_X86_32_PIC r3
    LOAD_2_PARA
    SIGN_EXTENSION r1, r1d
    mov r2, r0

    sub         r2, r1
    movq        mm0, [r2]              ; mm0 = [t7 t6 t5 t4 t3 t2 t1 t0]
    movq        mm1, mm0
    movq        mm2, mm0

    psrlq       mm1, 8h                 ; mm1 = [xx t7 t6 t5 t4 t3 t2 t1]
    psrlq       mm2, 10h                ; mm2 = [xx xx t7 t6 t5 t4 t3 t2]

    movq        mm3, mm1
    pavgb       mm3, mm0                ; mm3 = [xx xx xx i  d  c  b  a]

    movq        mm4, mm2
    pavgb       mm2, mm0
    pxor        mm4, mm0                ; find odd value in the lowest bit of each byte
    pand        mm4, [pic(mmx_01bytes)] ; set the odd bit
    psubusb     mm2, mm4                ; decrease 1 from odd bytes

    pavgb       mm2, mm1                ; mm2 = [xx xx xx j  h  g  f  e]

    movd        [r0], mm3
    psrlq       mm3, 8h
    movd        [r0+2*r1], mm3

    movd        [r0+r1], mm2
    psrlq       mm2, 8h
    lea         r0, [r0+2*r1]
    movd        [r0+r1], mm2
    DEINIT_X86_32_PIC
    WELSEMMS
    ret

;*******************************************************************************
;
;   void WelsDecoderIChromaPredDc_sse2(uint8_t *pPred, const int32_t kiStride)
;*******************************************************************************
WELS_EXTERN WelsDecoderIChromaPredDc_sse2
    push    r3
    push    r4
    %assign push_num 2
    INIT_X86_32_PIC r5
    LOAD_2_PARA
    SIGN_EXTENSION r1, r1d
    mov r4, r0

    sub         r0, r1
    movq        mm0, [r0]

    movzx       r2, byte [r0+r1-0x01] ; l1
    lea         r0, [r0+2*r1]
    movzx       r3, byte [r0-0x01]     ; l2
    add         r2, r3
    movzx       r3, byte [r0+r1-0x01] ; l3
    add         r2, r3
    lea         r0, [r0+2*r1]
    movzx       r3, byte [r0-0x01]     ; l4
    add         r2, r3
    movd        mm1, r2d                 ; mm1 = l1+l2+l3+l4

    movzx       r2, byte [r0+r1-0x01] ; l5
    lea         r0, [r0+2*r1]
    movzx       r3, byte [r0-0x01]     ; l6
    add         r2, r3
    movzx       r3, byte [r0+r1-0x01] ; l7
    add         r2, r3
    lea         r0, [r0+2*r1]
    movzx       r3, byte [r0-0x01]     ; l8
    add         r2, r3
    movd        mm2, r2d                 ; mm2 = l5+l6+l7+l8

    movq        mm3, mm0
    psrlq       mm0, 0x20
    psllq       mm3, 0x20
    psrlq       mm3, 0x20
    pxor        mm4, mm4
    psadbw      mm0, mm4
    psadbw      mm3, mm4                 ; sum1 = mm3+mm1, sum2 = mm0, sum3 = mm2

    paddq       mm3, mm1
    movq        mm1, mm2
    paddq       mm1, mm0;                ; sum1 = mm3, sum2 = mm0, sum3 = mm2, sum4 = mm1

    movq        mm4, [pic(mmx_0x02)]

    paddq       mm0, mm4
    psrlq       mm0, 0x02

    paddq       mm2, mm4
    psrlq       mm2, 0x02

    paddq       mm3, mm4
    paddq       mm3, mm4
    psrlq       mm3, 0x03

    paddq       mm1, mm4
    paddq       mm1, mm4
    psrlq       mm1, 0x03

    pmuludq     mm0, [pic(mmx_01bytes)]
    pmuludq     mm3, [pic(mmx_01bytes)]
    psllq       mm0, 0x20
    pxor        mm0, mm3                 ; mm0 = m_up

    pmuludq     mm2, [pic(mmx_01bytes)]
    pmuludq     mm1, [pic(mmx_01bytes)]
    psllq       mm1, 0x20
    pxor        mm1, mm2                 ; mm2 = m_down

    movq        [r4],       mm0
    movq        [r4+r1],   mm0
    movq        [r4+2*r1], mm0
    lea         r4, [r4+2*r1]
    movq        [r4+r1],   mm0

    movq        [r4+2*r1], mm1
    lea         r4, [r4+2*r1]
    movq        [r4+r1],   mm1
    movq        [r4+2*r1], mm1
    lea         r4, [r4+2*r1]
    movq        [r4+r1],   mm1

    DEINIT_X86_32_PIC
    pop r4
    pop r3
    WELSEMMS
    ret



;*******************************************************************************
;
;   void WelsDecoderI16x16LumaPredDc_sse2(uint8_t *pPred, const int32_t kiStride)
;*******************************************************************************
WELS_EXTERN WelsDecoderI16x16LumaPredDc_sse2
    push    r3
    push    r4
    %assign push_num 2
    INIT_X86_32_PIC r5
    LOAD_2_PARA
    SIGN_EXTENSION r1, r1d
    mov r4, r0
    sub         r0, r1
    movdqa      xmm0, [r0]             ; read one row
    pxor        xmm1, xmm1
    psadbw      xmm0, xmm1
    movdqa      xmm1, xmm0
    psrldq      xmm1, 0x08
    pslldq      xmm0, 0x08
    psrldq      xmm0, 0x08
    paddw       xmm0, xmm1

    movzx       r2, byte [r0+r1-0x01]
    movzx       r3, byte [r0+2*r1-0x01]
    add     r2, r3
    lea         r0, [r0+r1]
    LOAD_2_LEFT_AND_ADD
    LOAD_2_LEFT_AND_ADD
    LOAD_2_LEFT_AND_ADD
    LOAD_2_LEFT_AND_ADD
    LOAD_2_LEFT_AND_ADD
    LOAD_2_LEFT_AND_ADD
    LOAD_2_LEFT_AND_ADD
    add         r2, 0x10
    movd        xmm1, r2d
    paddw       xmm0, xmm1
    psrld       xmm0, 0x05
    pmuludq     xmm0, [pic(mmx_01bytes)]
    pshufd      xmm0, xmm0, 0

    movdqa      [r4],       xmm0
    movdqa      [r4+r1],   xmm0
    movdqa      [r4+2*r1], xmm0
    lea         r4,         [r4+2*r1]

    movdqa      [r4+r1],   xmm0
    movdqa      [r4+2*r1], xmm0
    lea         r4,         [r4+2*r1]

    movdqa      [r4+r1],   xmm0
    movdqa      [r4+2*r1], xmm0
    lea         r4,         [r4+2*r1]

    movdqa      [r4+r1],   xmm0
    movdqa      [r4+2*r1], xmm0
    lea         r4,         [r4+2*r1]

    movdqa      [r4+r1],   xmm0
    movdqa      [r4+2*r1], xmm0
    lea         r4,         [r4+2*r1]

    movdqa      [r4+r1],   xmm0
    movdqa      [r4+2*r1], xmm0
    lea         r4,         [r4+2*r1]

    movdqa      [r4+r1],   xmm0
    movdqa      [r4+2*r1], xmm0
    lea         r4,         [r4+2*r1]

    movdqa      [r4+r1],   xmm0

    DEINIT_X86_32_PIC
    pop r4
    pop r3

    ret

;*******************************************************************************
; for intra prediction as follows, 11/19/2010
;*******************************************************************************

;*******************************************************************************
;   void WelsDecoderI16x16LumaPredDcTop_sse2(uint8_t *pPred, const int32_t kiStride)
;*******************************************************************************
WELS_EXTERN WelsDecoderI16x16LumaPredDcTop_sse2
    %assign push_num 0
    LOAD_2_PARA
    PUSH_XMM 8
    SIGN_EXTENSION r1, r1d
    mov r2, r0
    sub r2, r1
    movdqa xmm0, [r2]       ; pPred-kiStride, top line
    pxor xmm7, xmm7
    psadbw xmm0, xmm7
    movdqa xmm1, xmm0
    psrldq xmm1, 8
    paddw  xmm0, xmm1
    xor r2, r2
    movd r2d, xmm0
    ;movdqa xmm1, xmm0
    ;punpcklbw xmm0, xmm7
    ;punpckhbw xmm1, xmm7

    ;paddw xmm0, xmm1           ; (ub.max(ff) << 4) will not excceed of uw, so can perform it in unit of unsigned word scope
    ;pshufd xmm1, xmm0, 04eh        ; 01001110, w3w2w1w0,w7w6w5w4
    ;paddw xmm0, xmm1           ; w3+7 w2+6 w1+5 w0+4 w3+7 w2+6 w1+5 w0+4
    ;pshufd xmm1, xmm0, 0b1h        ; 10110001, w1+5 w0+4 w3+7 w2+6 w1+5 w0+4 w3+7 w2+6
    ;paddw xmm0, xmm1           ; w_o w_e w_o w_e w_o w_e w_o w_e (w_o=1+3+5+7, w_e=0+2+4+6)
    ;pshuflw xmm1, xmm0, 0b1h   ; 10110001
    ;paddw xmm0, xmm1           ; sum in word unit (x8)
    ;xor r3, r3
    ;movd r3d, xmm0
    ;and edx, 0ffffh

    add r2, 8
    sar r2, 4
    SSE2_Copy16Times xmm1, r2d
    ;mov dh, dl
    ;mov r2, edx
    ;shl r2, 010h
    ;or edx, r2
    ;movd xmm1, edx
    ;pshufd xmm0, xmm1, 00h
    ;movdqa xmm1, xmm0
    movdqa xmm0, xmm1
    lea r2, [2*r1+r1]       ; 3*kiStride

    movdqa [r0], xmm0
    movdqa [r0+r1], xmm1
    movdqa [r0+2*r1], xmm0
    movdqa [r0+r2], xmm1

    lea r0, [r0+4*r1]
    movdqa [r0], xmm0
    movdqa [r0+r1], xmm1
    movdqa [r0+2*r1], xmm0
    movdqa [r0+r2], xmm1

    lea r0, [r0+4*r1]
    movdqa [r0], xmm0
    movdqa [r0+r1], xmm1
    movdqa [r0+2*r1], xmm0
    movdqa [r0+r2], xmm1

    lea r0, [r0+4*r1]
    movdqa [r0], xmm0
    movdqa [r0+r1], xmm1
    movdqa [r0+2*r1], xmm0
    movdqa [r0+r2], xmm1

    POP_XMM
    ret

;*******************************************************************************
;   void WelsDecoderI16x16LumaPredDcNA_sse2(uint8_t *pPred, const int32_t kiStride)
;*******************************************************************************
WELS_EXTERN WelsDecoderI16x16LumaPredDcNA_sse2
    %assign push_num 0
    INIT_X86_32_PIC r3
    LOAD_2_PARA
    SIGN_EXTENSION r1, r1d
    lea r2, [2*r1+r1]       ; 3*kiStride

    movdqa xmm0, [pic(sse2_dc_0x80)]
    movdqa xmm1, xmm0
    movdqa [r0], xmm0
    movdqa [r0+r1], xmm1
    movdqa [r0+2*r1], xmm0
    movdqa [r0+r2], xmm1
    lea r0, [r0+4*r1]
    movdqa [r0], xmm0
    movdqa [r0+r1], xmm1
    movdqa [r0+2*r1], xmm0
    movdqa [r0+r2], xmm1
    lea r0, [r0+4*r1]
    movdqa [r0], xmm0
    movdqa [r0+r1], xmm1
    movdqa [r0+2*r1], xmm0
    movdqa [r0+r2], xmm1
    lea r0, [r0+4*r1]
    movdqa [r0], xmm0
    movdqa [r0+r1], xmm1
    movdqa [r0+2*r1], xmm0
    movdqa [r0+r2], xmm1

    DEINIT_X86_32_PIC
    ret

;*******************************************************************************
;   void WelsDecoderIChromaPredDcLeft_mmx(uint8_t *pPred, const int32_t kiStride)
;*******************************************************************************
WELS_EXTERN WelsDecoderIChromaPredDcLeft_mmx
    push r3
    push r4
    %assign push_num 2
    LOAD_2_PARA
    SIGN_EXTENSION r1, r1d
    mov r4, r0
    ; for left
    dec r0
    xor r2, r2
    xor r3, r3
    movzx r2, byte [r0]
    movzx r3, byte [r0+r1]
    add r2, r3
    lea r0, [r0+2*r1]
    movzx r3, byte [r0]
    add r2, r3
    movzx r3, byte [r0+r1]
    add r2, r3
    add r2, 02h
    sar r2, 02h
    ;SSE2_Copy16Times mm0, r2d
    mov r3, r2
    sal r3, 8
    or r2, r3
    movd mm1, r2d
    pshufw mm0, mm1, 00h
    ;mov bh, bl
    ;movd mm1, ebx
    ;pshufw mm0, mm1, 00h   ; up64
    movq mm1, mm0
    xor r2, r2
    lea r0, [r0+2*r1]
    movzx r2, byte [r0]
    movzx r3, byte [r0+r1]
    add r2, r3
    lea r0, [r0+2*r1]
    movzx r3, byte [r0]
    add r2, r3
    movzx r3, byte [r0+r1]
    add r2, r3
    add r2, 02h
    sar r2, 02h
    mov r3, r2
    sal r3, 8
    or r2, r3
    movd mm3, r2d
    pshufw mm2, mm3, 00h
    ;mov bh, bl
    ;movd mm3, ebx
    ;pshufw mm2, mm3, 00h   ; down64
    ;SSE2_Copy16Times mm2, r2d
    movq mm3, mm2
    lea r2, [2*r1+r1]
    movq [r4], mm0
    movq [r4+r1], mm1
    movq [r4+2*r1], mm0
    movq [r4+r2], mm1
    lea r4, [r4+4*r1]
    movq [r4], mm2
    movq [r4+r1], mm3
    movq [r4+2*r1], mm2
    movq [r4+r2], mm3
    pop r4
    pop r3
    emms
    ret

;*******************************************************************************
;   void WelsDecoderIChromaPredDcTop_sse2(uint8_t *pPred, const int32_t kiStride)
;*******************************************************************************
WELS_EXTERN WelsDecoderIChromaPredDcTop_sse2
    %assign push_num 0
    LOAD_2_PARA
    PUSH_XMM 8
    SIGN_EXTENSION r1, r1d
    mov r2, r0
    sub r2, r1
    movq xmm0, [r2]     ; top: 8x1 pixels
    pxor xmm7, xmm7
    punpcklbw xmm0, xmm7        ; ext 8x2 words
    pshufd xmm1, xmm0, 0B1h     ; 10110001 B, w5 w4 w7 w6 w1 w0 w3 w2
    paddw xmm0, xmm1            ; w5+7 w4+6 w5+7 w4+6 w1+3 w0+2 w1+3 w0+2
    movdqa xmm1, xmm0
    pshuflw xmm2, xmm0, 0B1h    ; 10110001 B, .. w0+2 w1+3 w0+2 w1+3
    pshufhw xmm3, xmm1, 0B1h    ; 10110001 B, w4+6 w5+7 w4+6 w5+7 ..
    paddw xmm0, xmm2            ; .. w0+..+3 w0+..+3 w0+..+3 w0+..+3
    paddw xmm1, xmm3            ; w4+..+7 w4+..+7 w4+..+7 w4+..+7 ..
    punpckhqdq xmm1, xmm7
    punpcklqdq xmm0, xmm1       ; sum1 sum1 sum1 sum1 sum0 sum0 sum0 sum0
%ifdef X86_32_PICASM
    pcmpeqw  xmm6, xmm6
    psrlw    xmm6, 15
    psllw    xmm6, 1
%else
    movdqa xmm6, [sse2_wd_0x02]
%endif
    paddw xmm0, xmm6
    psraw xmm0, 02h
    packuswb xmm0, xmm7
    lea r2, [2*r1+r1]
    movq [r0], xmm0
    movq [r0+r1], xmm0
    movq [r0+2*r1], xmm0
    movq [r0+r2], xmm0
    lea r0, [r0+4*r1]
    movq [r0], xmm0
    movq [r0+r1], xmm0
    movq [r0+2*r1], xmm0
    movq [r0+r2], xmm0
    POP_XMM
    ret

;*******************************************************************************
;   void WelsDecoderIChromaPredDcNA_mmx(uint8_t *pPred, const int32_t kiStride)
;*******************************************************************************
WELS_EXTERN WelsDecoderIChromaPredDcNA_mmx
    %assign push_num 0
    INIT_X86_32_PIC r3
    LOAD_2_PARA
    SIGN_EXTENSION r1, r1d
    lea r2, [2*r1+r1]
    movq mm0, [pic(sse2_dc_0x80)]
    movq mm1, mm0
    movq [r0], mm0
    movq [r0+r1], mm1
    movq [r0+2*r1], mm0
    movq [r0+r2], mm1
    lea r0, [r0+4*r1]
    movq [r0], mm0
    movq [r0+r1], mm1
    movq [r0+2*r1], mm0
    movq [r0+r2], mm1
    DEINIT_X86_32_PIC
    emms
    ret

