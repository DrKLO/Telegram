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
;*      vaa.asm
;*
;*      Abstract
;*      sse2 for pVaa routines
;*
;*  History
;*      04/14/2010      Created
;*              06/07/2010      Added AnalysisVaaInfoIntra_sse2(ssse3)
;*              06/10/2010      Tune rc_sad_frame_sse2 and got about 40% improvement
;*              08/11/2010      Added abs_difference_mbrow_sse2 & sum_sqrsum_mbrow_sse2
;*
;*************************************************************************/
%include "asm_inc.asm"


;***********************************************************************
; Macros and other preprocessor constants
;***********************************************************************
%macro SUM_SQR_SSE2     3       ; dst, pSrc, zero
    movdqa %1, %2
    punpcklbw %1, %3
    punpckhbw %2, %3
    pmaddwd %1, %1
    pmaddwd %2, %2
    paddd %1, %2
    pshufd %2, %1, 04Eh   ; 01001110 B
    paddd %1, %2
    pshufd %2, %1, 0B1h   ; 10110001 B
    paddd %1, %2
%endmacro       ; END OF SUM_SQR_SSE2

%macro WELS_SAD_16x2_SSE2  3 ;esi :%1 edi:%2 ebx:%3
    movdqa        xmm1,   [%1]
    movdqa        xmm2,   [%2]
    movdqa        xmm3,   [%1+%3]
    movdqa        xmm4,   [%2+%3]
    psadbw        xmm1,   xmm2
    psadbw        xmm3,   xmm4
    paddd xmm6,   xmm1
    paddd xmm6,   xmm3
    lea           %1,     [%1+%3*2]
    lea           %2,     [%2+%3*2]
%endmacro

; by comparing it outperforms than phaddw(SSSE3) sets
%macro SUM_WORD_8x2_SSE2        2       ; dst(pSrc), tmp
    ; @sum_8x2 begin
    pshufd %2, %1, 04Eh   ; 01001110 B
    paddw %1, %2
    pshuflw %2, %1, 04Eh  ; 01001110 B
    paddw %1, %2
    pshuflw %2, %1, 0B1h  ; 10110001 B
    paddw %1, %2
    ; end of @sum_8x2
%endmacro       ; END of SUM_WORD_8x2_SSE2

%macro WELS_SAD_SUM_SQSUM_16x1_SSE2 3 ;esi:%1,edi:%2,ebx:%3
    movdqa        xmm1,   [%1]
    movdqa        xmm2,   [%2]
    movdqa        xmm3,   xmm1
    psadbw        xmm3,   xmm2
    paddd         xmm6,   xmm3

    movdqa        xmm3,   xmm1
    psadbw        xmm3,   xmm0
    paddd         xmm5,   xmm3

    movdqa        xmm2,   xmm1
    punpcklbw     xmm1,   xmm0
    punpckhbw     xmm2,   xmm0
    pmaddwd               xmm1,   xmm1
    pmaddwd               xmm2,   xmm2
    paddd         xmm4,   xmm1
    paddd         xmm4,   xmm2

    add           %1,     %3
    add           %2,     %3
%endmacro

%macro WELS_SAD_SUM_SQSUM_SQDIFF_16x1_SSE2 3 ;esi:%1 edi:%2 ebx:%3
    movdqa        xmm1,   [%1]
    movdqa        xmm2,   [%2]
    movdqa        xmm3,   xmm1
    psadbw        xmm3,   xmm2
    paddd         xmm7,   xmm3    ; sad

    movdqa        xmm3,   xmm1
    pmaxub        xmm3,   xmm2
    pminub        xmm2,   xmm1
    psubb xmm3,   xmm2    ; diff

    movdqa        xmm2,   xmm1
    psadbw        xmm2,   xmm0
    paddd xmm6,   xmm2    ; sum

    movdqa                xmm2,   xmm1
    punpcklbw     xmm1,   xmm0
    punpckhbw     xmm2,   xmm0
    pmaddwd               xmm1,   xmm1
    pmaddwd               xmm2,   xmm2
    paddd         xmm5,   xmm1
    paddd         xmm5,   xmm2    ; sqsum

    movdqa                xmm1,   xmm3
    punpcklbw     xmm1,   xmm0
    punpckhbw     xmm3,   xmm0
    pmaddwd               xmm1,   xmm1
    pmaddwd               xmm3,   xmm3
    paddd         xmm4,   xmm1
    paddd         xmm4,   xmm3    ; sqdiff

    add           %1,     %3
    add           %2,     %3
%endmacro

%macro WELS_SAD_SD_MAD_16x1_SSE2       7 ;esi:%5 edi:%6 ebx:%7
%define sad_reg                 %1
%define sum_cur_reg             %2
%define sum_ref_reg             %3
%define mad_reg                 %4
    movdqa        xmm1,           [%5]
    movdqa        xmm2,           [%6]
    movdqa        xmm3,           xmm1
    psadbw        xmm3,           xmm0
    paddd         sum_cur_reg,    xmm3    ; sum_cur
    movdqa        xmm3,           xmm2
    psadbw        xmm3,           xmm0
    paddd sum_ref_reg,                    xmm3    ; sum_ref

    movdqa        xmm3,           xmm1
    pmaxub        xmm3,           xmm2
    pminub        xmm2,           xmm1
    psubb xmm3,           xmm2    ; abs diff
    pmaxub        mad_reg,        xmm3    ; max abs diff

    psadbw        xmm3,           xmm0
    paddd sad_reg,        xmm3    ; sad

    add                   %5,             %7
    add                   %6,             %7
%endmacro


%macro WELS_MAX_REG_SSE2       1       ; xmm1, xmm2, xmm3 can be used
%define max_reg  %1
    movdqa        xmm1,           max_reg
    psrldq        xmm1,           4
    pmaxub        max_reg,        xmm1
    movdqa        xmm1,           max_reg
    psrldq        xmm1,           2
    pmaxub        max_reg,        xmm1
    movdqa        xmm1,           max_reg
    psrldq        xmm1,           1
    pmaxub        max_reg,        xmm1
%endmacro

%macro WELS_SAD_BGD_SQDIFF_16x1_SSE2   7 ;esi:%5 edi:%6 ebx:%7
%define sad_reg         %1
%define sum_reg         %2
%define mad_reg         %3
%define sqdiff_reg      %4
    movdqa                xmm1,           [%5]
    movdqa                xmm2,           xmm1
    movdqa                xmm3,           xmm1
    punpcklbw     xmm2,           xmm0
    punpckhbw     xmm3,           xmm0
    pmaddwd               xmm2,           xmm2
    pmaddwd               xmm3,           xmm3
    paddd         xmm2,           xmm3
    movdqa                xmm3,           xmm2
    psllq         xmm2,           32
    psrlq         xmm3,           32
    psllq         xmm3,           32
    paddd         xmm2,           xmm3
    paddd         sad_reg,        xmm2            ; sqsum

    movdqa        xmm2,           [%6]
    movdqa        xmm3,           xmm1
    psadbw        xmm3,           xmm0
    paddd sum_reg,                        xmm3    ; sum_cur
    movdqa        xmm3,           xmm2
    psadbw        xmm3,           xmm0
    pslldq        xmm3,           4
    paddd sum_reg,                        xmm3    ; sum_ref

    movdqa        xmm3,           xmm1
    pmaxub        xmm3,           xmm2
    pminub        xmm2,           xmm1
    psubb xmm3,           xmm2    ; abs diff
    pmaxub        mad_reg,        xmm3    ; max abs diff

    movdqa        xmm1,           xmm3
    psadbw        xmm3,           xmm0
    paddd sad_reg,        xmm3    ; sad

    movdqa                xmm3,   xmm1
    punpcklbw     xmm1,   xmm0
    punpckhbw     xmm3,   xmm0
    pmaddwd               xmm1,   xmm1
    pmaddwd               xmm3,   xmm3
    paddd         sqdiff_reg,     xmm1
    paddd         sqdiff_reg,     xmm3    ; sqdiff

    add           %5,     %7
    add           %6,     %7
%endmacro


;***********************************************************************
; Code
;***********************************************************************

SECTION .text

%ifdef X86_32

;***********************************************************************
;   void SampleVariance16x16_sse2(      uint8_t * y_ref, int32_t y_ref_stride, uint8_t * y_src, int32_t y_src_stride,SMotionTextureUnit* pMotionTexture );
;***********************************************************************
WELS_EXTERN SampleVariance16x16_sse2
    push esi
    push edi
    push ebx

    sub esp, 16
    %define SUM                   [esp]
    %define SUM_CUR               [esp+4]
    %define SQR                   [esp+8]
    %define SQR_CUR               [esp+12]
    %define PUSH_SIZE     28      ; 12 + 16

    mov edi, [esp+PUSH_SIZE+4]    ; y_ref
    mov edx, [esp+PUSH_SIZE+8]    ; y_ref_stride
    mov esi, [esp+PUSH_SIZE+12]   ; y_src
    mov eax, [esp+PUSH_SIZE+16]   ; y_src_stride
    mov ecx, 010h                         ; height = 16

    pxor xmm7, xmm7
    movdqu SUM, xmm7

.hloops:
    movdqa xmm0, [edi]            ; y_ref
    movdqa xmm1, [esi]            ; y_src
    movdqa xmm2, xmm0             ; store first for future process
    movdqa xmm3, xmm1
    ; sum += diff;
    movdqa xmm4, xmm0
    psadbw xmm4, xmm1             ; 2 parts, [0,..,15], [64,..,79]
    ; to be continued for sum
    pshufd xmm5, xmm4, 0C6h       ; 11000110 B
    paddw xmm4, xmm5
    movd ebx, xmm4
    add SUM, ebx

    ; sqr += diff * diff;
    pmaxub xmm0, xmm1
    pminub xmm1, xmm2
    psubb xmm0, xmm1                              ; diff
    SUM_SQR_SSE2 xmm1, xmm0, xmm7 ; dst, pSrc, zero
    movd ebx, xmm1
    add SQR, ebx

    ; sum_cur += y_src[x];
    movdqa xmm0, xmm3             ; cur_orig
    movdqa xmm1, xmm0
    punpcklbw xmm0, xmm7
    punpckhbw xmm1, xmm7
    paddw xmm0, xmm1              ; 8x2
    SUM_WORD_8x2_SSE2 xmm0, xmm1
    movd ebx, xmm0
    and ebx, 0ffffh
    add SUM_CUR, ebx

    ; sqr_cur += y_src[x] * y_src[x];
    SUM_SQR_SSE2 xmm0, xmm3, xmm7 ; dst, pSrc, zero
    movd ebx, xmm0
    add SQR_CUR, ebx

    lea edi, [edi+edx]
    lea esi, [esi+eax]
    dec ecx
    jnz near .hloops

    mov ebx, 0
    mov bx, word SUM
    sar ebx, 8
    imul ebx, ebx
    mov ecx, SQR
    sar ecx, 8
    sub ecx, ebx
    mov edi, [esp+PUSH_SIZE+20]   ; pMotionTexture
    mov [edi], cx                         ; to store uiMotionIndex
    mov ebx, 0
    mov bx, word SUM_CUR
    sar ebx, 8
    imul ebx, ebx
    mov ecx, SQR_CUR
    sar ecx, 8
    sub ecx, ebx
    mov [edi+2], cx                               ; to store uiTextureIndex

    %undef SUM
    %undef SUM_CUR
    %undef SQR
    %undef SQR_CUR
    %undef PUSH_SIZE

    add esp, 16
    pop ebx
    pop edi
    pop esi

    ret



;*************************************************************************************************************
;void VAACalcSad_sse2( const uint8_t *cur_data, const uint8_t *ref_data, int32_t iPicWidth, int32_t iPicHeight
;                                                               int32_t iPicStride, int32_t *psadframe, int32_t *psad8x8)
;*************************************************************************************************************


WELS_EXTERN VAACalcSad_sse2
%define         cur_data                        esp + pushsize + 4
%define         ref_data                        esp + pushsize + 8
%define         iPicWidth                       esp + pushsize + 12
%define         iPicHeight                      esp + pushsize + 16
%define         iPicStride                      esp + pushsize + 20
%define         psadframe                       esp + pushsize + 24
%define         psad8x8                         esp + pushsize + 28
%define         pushsize        12
    push  esi
    push  edi
    push  ebx
    mov           esi,    [cur_data]
    mov           edi,    [ref_data]
    mov           ebx,    [iPicStride]
    mov           edx,    [psad8x8]
    mov           eax,    ebx

    shr           dword [iPicWidth],      4                                       ; iPicWidth/16
    shr           dword [iPicHeight],     4                                       ; iPicHeight/16
    shl           eax,    4                                                               ; iPicStride*16
    pxor  xmm0,   xmm0
    pxor  xmm7,   xmm7            ; iFrameSad
height_loop:
    mov           ecx,    dword [iPicWidth]
    push  esi
    push  edi
width_loop:
    pxor  xmm6,   xmm6            ;
    WELS_SAD_16x2_SSE2 esi,edi,ebx
    WELS_SAD_16x2_SSE2 esi,edi,ebx
    WELS_SAD_16x2_SSE2 esi,edi,ebx
    WELS_SAD_16x2_SSE2 esi,edi,ebx
    paddd xmm7,           xmm6
    movd  [edx],          xmm6
    psrldq        xmm6,           8
    movd  [edx+4],        xmm6

    pxor  xmm6,   xmm6
    WELS_SAD_16x2_SSE2 esi,edi,ebx
    WELS_SAD_16x2_SSE2 esi,edi,ebx
    WELS_SAD_16x2_SSE2 esi,edi,ebx
    WELS_SAD_16x2_SSE2 esi,edi,ebx
    paddd xmm7,           xmm6
    movd  [edx+8],        xmm6
    psrldq        xmm6,           8
    movd  [edx+12],       xmm6

    add           edx,    16
    sub           esi,    eax
    sub           edi,    eax
    add           esi,    16
    add           edi,    16

    dec           ecx
    jnz           width_loop

    pop           edi
    pop           esi
    add           esi,    eax
    add           edi,    eax

    dec   dword [iPicHeight]
    jnz           height_loop

    mov           edx,    [psadframe]
    movdqa        xmm5,   xmm7
    psrldq        xmm7,   8
    paddd xmm7,   xmm5
    movd  [edx],  xmm7

%undef          cur_data
%undef          ref_data
%undef          iPicWidth
%undef          iPicHeight
%undef          iPicStride
%undef          psadframe
%undef          psad8x8
%undef          pushsize
    pop           ebx
    pop           edi
    pop           esi
    ret

%else  ;64-bit

;***********************************************************************
;   void SampleVariance16x16_sse2(      uint8_t * y_ref, int32_t y_ref_stride, uint8_t * y_src, int32_t y_src_stride,SMotionTextureUnit* pMotionTexture );
;***********************************************************************
WELS_EXTERN SampleVariance16x16_sse2
    %define SUM                   r10;[esp]
    %define SUM_CUR               r11;[esp+4]
    %define SQR                   r13;[esp+8]
    %define SQR_CUR               r15;[esp+12]

    push r12
    push r13
    push r14
    push r15
    %assign push_num 4
    LOAD_5_PARA
    PUSH_XMM 8
    SIGN_EXTENSION r1,r1d
    SIGN_EXTENSION r3,r3d

    mov r12,010h
    pxor xmm7, xmm7
    movq SUM, xmm7
    movq SUM_CUR,xmm7
    movq SQR,xmm7
    movq SQR_CUR,xmm7

.hloops:
    mov r14,0
    movdqa xmm0, [r0]             ; y_ref
    movdqa xmm1, [r2]             ; y_src
    movdqa xmm2, xmm0             ; store first for future process
    movdqa xmm3, xmm1
    ; sum += diff;
    movdqa xmm4, xmm0
    psadbw xmm4, xmm1             ; 2 parts, [0,..,15], [64,..,79]
    ; to be continued for sum
    pshufd xmm5, xmm4, 0C6h       ; 11000110 B
    paddw xmm4, xmm5
    movd r14d, xmm4
    add SUM, r14

    ; sqr += diff * diff;
    pmaxub xmm0, xmm1
    pminub xmm1, xmm2
    psubb xmm0, xmm1                              ; diff
    SUM_SQR_SSE2 xmm1, xmm0, xmm7 ; dst, pSrc, zero
    movd r14d, xmm1
    add SQR, r14

    ; sum_cur += y_src[x];
    movdqa xmm0, xmm3             ; cur_orig
    movdqa xmm1, xmm0
    punpcklbw xmm0, xmm7
    punpckhbw xmm1, xmm7
    paddw xmm0, xmm1              ; 8x2
    SUM_WORD_8x2_SSE2 xmm0, xmm1
    movd r14d, xmm0
    and r14, 0ffffh
    add SUM_CUR, r14

    ; sqr_cur += y_src[x] * y_src[x];
    SUM_SQR_SSE2 xmm0, xmm3, xmm7 ; dst, pSrc, zero
    movd r14d, xmm0
    add SQR_CUR, r14

    lea r0, [r0+r1]
    lea r2, [r2+r3]
    dec r12
    jnz near .hloops

    mov r0, SUM
    sar r0, 8
    imul r0, r0
    mov r1, SQR
    sar r1, 8
    sub r1, r0
    mov [r4], r1w                         ; to store uiMotionIndex
    mov r0, SUM_CUR
    sar r0, 8
    imul r0, r0
    mov r1, SQR_CUR
    sar r1, 8
    sub r1, r0
    mov [r4+2], r1w                               ; to store uiTextureIndex

    POP_XMM
    LOAD_5_PARA_POP
    pop r15
    pop r14
    pop r13
    pop r12


    %assign push_num 0

    ret


;*************************************************************************************************************
;void VAACalcSad_sse2( const uint8_t *cur_data, const uint8_t *ref_data, int32_t iPicWidth, int32_t iPicHeight
;                                                               int32_t iPicStride, int32_t *psadframe, int32_t *psad8x8)
;*************************************************************************************************************


WELS_EXTERN VAACalcSad_sse2
%define         cur_data                        r0
%define         ref_data                        r1
%define         iPicWidth                       r2
%define         iPicHeight              r3
%define         iPicStride              r4
%define         psadframe                       r5
%define         psad8x8                         r6

    push r12
    push r13
    %assign push_num 2
    LOAD_7_PARA
    PUSH_XMM 8
    SIGN_EXTENSION r2,r2d
    SIGN_EXTENSION r3,r3d
    SIGN_EXTENSION r4,r4d

    mov   r12,r4
    shr           r2,     4                                       ; iPicWidth/16
    shr           r3,     4                                       ; iPicHeight/16

    shl           r12,    4                                                               ; iPicStride*16
    pxor  xmm0,   xmm0
    pxor  xmm7,   xmm7            ; iFrameSad
height_loop:
    mov           r13,    r2
    push  r0
    push  r1
width_loop:
    pxor  xmm6,   xmm6
    WELS_SAD_16x2_SSE2 r0,r1,r4
    WELS_SAD_16x2_SSE2 r0,r1,r4
    WELS_SAD_16x2_SSE2 r0,r1,r4
    WELS_SAD_16x2_SSE2 r0,r1,r4
    paddd xmm7,           xmm6
    movd  [r6],           xmm6
    psrldq        xmm6,           8
    movd  [r6+4], xmm6

    pxor  xmm6,   xmm6
    WELS_SAD_16x2_SSE2 r0,r1,r4
    WELS_SAD_16x2_SSE2 r0,r1,r4
    WELS_SAD_16x2_SSE2 r0,r1,r4
    WELS_SAD_16x2_SSE2 r0,r1,r4
    paddd xmm7,           xmm6
    movd  [r6+8], xmm6
    psrldq        xmm6,           8
    movd  [r6+12],        xmm6

    add           r6,     16
    sub           r0,     r12
    sub           r1,     r12
    add           r0,     16
    add           r1,     16

    dec           r13
    jnz           width_loop

    pop           r1
    pop           r0
    add           r0,     r12
    add           r1,     r12

    dec   r3
    jnz           height_loop

    ;mov          r13,    [psadframe]
    movdqa        xmm5,   xmm7
    psrldq        xmm7,   8
    paddd xmm7,   xmm5
    movd  [psadframe],    xmm7

%undef          cur_data
%undef          ref_data
%undef          iPicWidth
%undef          iPicHeight
%undef          iPicStride
%undef          psadframe
%undef          psad8x8
%undef          pushsize
    POP_XMM
    LOAD_7_PARA_POP
    pop r13
    pop r12
    %assign push_num 0
    ret

%endif


%ifdef X86_32
;*************************************************************************************************************
;void VAACalcSadVar_sse2( const uint8_t *cur_data, const uint8_t *ref_data, int32_t iPicWidth, int32_t iPicHeight
;               int32_t iPicStride, int32_t *psadframe, int32_t *psad8x8, int32_t *psum16x16, int32_t *psqsum16x16)
;*************************************************************************************************************


WELS_EXTERN VAACalcSadVar_sse2
%define         localsize               8
%define         cur_data                        esp + pushsize + localsize + 4
%define         ref_data                        esp + pushsize + localsize + 8
%define         iPicWidth                       esp + pushsize + localsize + 12
%define         iPicHeight                      esp + pushsize + localsize + 16
%define         iPicStride                      esp + pushsize + localsize + 20
%define         psadframe                       esp + pushsize + localsize + 24
%define         psad8x8                         esp + pushsize + localsize + 28
%define         psum16x16                       esp + pushsize + localsize + 32
%define         psqsum16x16                     esp + pushsize + localsize + 36
%define         tmp_esi                         esp + 0
%define         tmp_edi                         esp + 4
%define         pushsize                16
    push  ebp
    push  esi
    push  edi
    push  ebx
    sub           esp,    localsize
    mov           esi,    [cur_data]
    mov           edi,    [ref_data]
    mov           ebx,    [iPicStride]
    mov           edx,    [psad8x8]
    mov           eax,    ebx

    shr           dword [iPicWidth],      4                                       ; iPicWidth/16
    shr           dword [iPicHeight],     4                                       ; iPicHeight/16
    shl           eax,    4                                                       ; iPicStride*16
    pxor  xmm0,   xmm0
    pxor  xmm7,   xmm7            ; iFrameSad
var_height_loop:
    mov           ecx,    dword [iPicWidth]
    mov           [tmp_esi],      esi
    mov           [tmp_edi],      edi
var_width_loop:
    pxor  xmm6,   xmm6            ; hiQuad_loQuad pSad8x8
    pxor  xmm5,   xmm5            ; pSum16x16
    pxor  xmm4,   xmm4            ; sqsum_16x16
    WELS_SAD_SUM_SQSUM_16x1_SSE2 esi,edi,ebx
    WELS_SAD_SUM_SQSUM_16x1_SSE2 esi,edi,ebx
    WELS_SAD_SUM_SQSUM_16x1_SSE2 esi,edi,ebx
    WELS_SAD_SUM_SQSUM_16x1_SSE2 esi,edi,ebx
    WELS_SAD_SUM_SQSUM_16x1_SSE2 esi,edi,ebx
    WELS_SAD_SUM_SQSUM_16x1_SSE2 esi,edi,ebx
    WELS_SAD_SUM_SQSUM_16x1_SSE2 esi,edi,ebx
    WELS_SAD_SUM_SQSUM_16x1_SSE2 esi,edi,ebx
    paddd xmm7,           xmm6
    movd  [edx],          xmm6
    psrldq        xmm6,           8
    movd  [edx+4],        xmm6

    pxor  xmm6,   xmm6
    WELS_SAD_SUM_SQSUM_16x1_SSE2 esi,edi,ebx
    WELS_SAD_SUM_SQSUM_16x1_SSE2 esi,edi,ebx
    WELS_SAD_SUM_SQSUM_16x1_SSE2 esi,edi,ebx
    WELS_SAD_SUM_SQSUM_16x1_SSE2 esi,edi,ebx
    WELS_SAD_SUM_SQSUM_16x1_SSE2 esi,edi,ebx
    WELS_SAD_SUM_SQSUM_16x1_SSE2 esi,edi,ebx
    WELS_SAD_SUM_SQSUM_16x1_SSE2 esi,edi,ebx
    WELS_SAD_SUM_SQSUM_16x1_SSE2 esi,edi,ebx
    paddd xmm7,           xmm6
    movd  [edx+8],        xmm6
    psrldq        xmm6,           8
    movd  [edx+12],       xmm6

    mov           ebp,    [psum16x16]
    movdqa        xmm1,   xmm5
    psrldq        xmm1,   8
    paddd xmm5,   xmm1
    movd  [ebp],  xmm5
    add           dword [psum16x16], 4

    movdqa        xmm5,   xmm4
    psrldq        xmm5,   8
    paddd xmm4,   xmm5
    movdqa        xmm3,   xmm4
    psrldq        xmm3,   4
    paddd xmm4,   xmm3

    mov           ebp,    [psqsum16x16]
    movd  [ebp],  xmm4
    add           dword [psqsum16x16], 4

    add           edx,    16
    sub           esi,    eax
    sub           edi,    eax
    add           esi,    16
    add           edi,    16

    dec           ecx
    jnz           var_width_loop

    mov           esi,    [tmp_esi]
    mov           edi,    [tmp_edi]
    add           esi,    eax
    add           edi,    eax

    dec   dword [iPicHeight]
    jnz           var_height_loop

    mov           edx,    [psadframe]
    movdqa        xmm5,   xmm7
    psrldq        xmm7,   8
    paddd xmm7,   xmm5
    movd  [edx],  xmm7

    add           esp,    localsize
    pop           ebx
    pop           edi
    pop           esi
    pop           ebp
%undef          cur_data
%undef          ref_data
%undef          iPicWidth
%undef          iPicHeight
%undef          iPicStride
%undef          psadframe
%undef          psad8x8
%undef          psum16x16
%undef          psqsum16x16
%undef          tmp_esi
%undef          tmp_edi
%undef          pushsize
%undef          localsize
    ret

%else  ;64-bit

;*************************************************************************************************************
;void VAACalcSadVar_sse2( const uint8_t *cur_data, const uint8_t *ref_data, int32_t iPicWidth, int32_t iPicHeight
;               int32_t iPicStride, int32_t *psadframe, int32_t *psad8x8, int32_t *psum16x16, int32_t *psqsum16x16)
;*************************************************************************************************************


WELS_EXTERN VAACalcSadVar_sse2
%define         cur_data                        arg1 ;r0
%define         ref_data                        arg2 ;r1
%define         iPicWidth                       arg3 ;r2
%define         iPicHeight                  arg4 ;r3
%define         iPicStride                  arg5
%define         psadframe                       arg6
%define         psad8x8                         arg7
%define         psum16x16                       arg8
%define         psqsum16x16                 arg9

    push r12
    push r13
    push r14
    push r15
    %assign push_num 4
    PUSH_XMM 8

%ifdef WIN64
    mov r4, arg5  ;iPicStride
    mov r5, arg6  ;psad8x8
%endif
    mov r14,arg7
    SIGN_EXTENSION r2,r2d
    SIGN_EXTENSION r3,r3d
    SIGN_EXTENSION r4,r4d

    mov   r13,r4
    shr   r2,4
    shr   r3,4

    shl   r13,4   ; iPicStride*16
    pxor  xmm0,   xmm0
    pxor  xmm7,   xmm7            ; iFrameSad
var_height_loop:
    push    r2
    %assign push_num push_num+1
    mov           r11,    r0
    mov           r12,    r1
var_width_loop:
    pxor  xmm6,   xmm6            ; hiQuad_loQuad pSad8x8
    pxor  xmm5,   xmm5            ; pSum16x16
    pxor  xmm4,   xmm4            ; sqsum_16x16
    WELS_SAD_SUM_SQSUM_16x1_SSE2 r0,r1,r4
    WELS_SAD_SUM_SQSUM_16x1_SSE2 r0,r1,r4
    WELS_SAD_SUM_SQSUM_16x1_SSE2 r0,r1,r4
    WELS_SAD_SUM_SQSUM_16x1_SSE2 r0,r1,r4
    WELS_SAD_SUM_SQSUM_16x1_SSE2 r0,r1,r4
    WELS_SAD_SUM_SQSUM_16x1_SSE2 r0,r1,r4
    WELS_SAD_SUM_SQSUM_16x1_SSE2 r0,r1,r4
    WELS_SAD_SUM_SQSUM_16x1_SSE2 r0,r1,r4
    paddd xmm7,           xmm6
    movd  [r14],          xmm6
    psrldq        xmm6,           8
    movd  [r14+4],        xmm6

    pxor  xmm6,   xmm6
    WELS_SAD_SUM_SQSUM_16x1_SSE2 r0,r1,r4
    WELS_SAD_SUM_SQSUM_16x1_SSE2 r0,r1,r4
    WELS_SAD_SUM_SQSUM_16x1_SSE2 r0,r1,r4
    WELS_SAD_SUM_SQSUM_16x1_SSE2 r0,r1,r4
    WELS_SAD_SUM_SQSUM_16x1_SSE2 r0,r1,r4
    WELS_SAD_SUM_SQSUM_16x1_SSE2 r0,r1,r4
    WELS_SAD_SUM_SQSUM_16x1_SSE2 r0,r1,r4
    WELS_SAD_SUM_SQSUM_16x1_SSE2 r0,r1,r4
    paddd   xmm7,           xmm6
    movd    [r14+8],        xmm6
    psrldq  xmm6,           8
    movd    [r14+12],       xmm6

    mov             r15,    psum16x16
    movdqa  xmm1,   xmm5
    psrldq  xmm1,   8
    paddd   xmm5,   xmm1
    movd    [r15],  xmm5
    add             dword psum16x16, 4

    movdqa  xmm5,   xmm4
    psrldq  xmm5,   8
    paddd   xmm4,   xmm5
    movdqa  xmm3,   xmm4
    psrldq  xmm3,   4
    paddd   xmm4,   xmm3

    mov             r15,    psqsum16x16
    movd    [r15],  xmm4
    add             dword psqsum16x16, 4

    add             r14,16
    sub             r0,     r13
    sub             r1,     r13
    add             r0,     16
    add             r1,     16

    dec             r2
    jnz             var_width_loop

    pop     r2
    %assign push_num push_num-1
    mov             r0,     r11
    mov             r1,     r12
    add             r0,     r13
    add             r1,     r13
    dec     r3
    jnz             var_height_loop

    mov             r15,    psadframe
    movdqa  xmm5,   xmm7
    psrldq  xmm7,   8
    paddd   xmm7,   xmm5
    movd    [r15],  xmm7

    POP_XMM
    pop r15
    pop r14
    pop r13
    pop r12
%assign push_num 0
%undef          cur_data
%undef          ref_data
%undef          iPicWidth
%undef          iPicHeight
%undef          iPicStride
%undef          psadframe
%undef          psad8x8
%undef          psum16x16
%undef          psqsum16x16
%undef          tmp_esi
%undef          tmp_edi
%undef          pushsize
%undef          localsize
    ret

%endif

%ifdef X86_32

;*************************************************************************************************************
;void VAACalcSadSsd_sse2(const uint8_t *cur_data, const uint8_t *ref_data, int32_t iPicWidth, int32_t iPicHeight,
;       int32_t iPicStride,int32_t *psadframe, int32_t *psad8x8, int32_t *psum16x16, int32_t *psqsum16x16, int32_t *psqdiff16x16)
;*************************************************************************************************************


WELS_EXTERN VAACalcSadSsd_sse2
%define         localsize               12
%define         cur_data                        esp + pushsize + localsize + 4
%define         ref_data                        esp + pushsize + localsize + 8
%define         iPicWidth                       esp + pushsize + localsize + 12
%define         iPicHeight                      esp + pushsize + localsize + 16
%define         iPicStride                      esp + pushsize + localsize + 20
%define         psadframe                       esp + pushsize + localsize + 24
%define         psad8x8                         esp + pushsize + localsize + 28
%define         psum16x16                       esp + pushsize + localsize + 32
%define         psqsum16x16                     esp + pushsize + localsize + 36
%define         psqdiff16x16            esp + pushsize + localsize + 40
%define         tmp_esi                         esp + 0
%define         tmp_edi                         esp + 4
%define         tmp_sadframe            esp + 8
%define         pushsize                16
    push    ebp
    push    esi
    push    edi
    push    ebx
    sub             esp,    localsize

    mov             ecx,    [iPicWidth]
    mov             ecx,    [iPicHeight]
    mov             esi,    [cur_data]
    mov             edi,    [ref_data]
    mov             ebx,    [iPicStride]
    mov             edx,    [psad8x8]
    mov             eax,    ebx

    shr             dword [iPicWidth],      4                                       ; iPicWidth/16
    shr             dword [iPicHeight],     4                                       ; iPicHeight/16
    shl             eax,    4                                                       ; iPicStride*16
    mov             ecx,    [iPicWidth]
    mov             ecx,    [iPicHeight]
    pxor    xmm0,   xmm0
    movd    [tmp_sadframe], xmm0
sqdiff_height_loop:
    mov             ecx,    dword [iPicWidth]
    mov             [tmp_esi],      esi
    mov             [tmp_edi],      edi
sqdiff_width_loop:
    pxor    xmm7,   xmm7            ; hiQuad_loQuad pSad8x8
    pxor    xmm6,   xmm6            ; pSum16x16
    pxor    xmm5,   xmm5            ; sqsum_16x16  four dword
    pxor    xmm4,   xmm4            ; sqdiff_16x16  four Dword
    WELS_SAD_SUM_SQSUM_SQDIFF_16x1_SSE2 esi,edi,ebx
    WELS_SAD_SUM_SQSUM_SQDIFF_16x1_SSE2 esi,edi,ebx
    WELS_SAD_SUM_SQSUM_SQDIFF_16x1_SSE2 esi,edi,ebx
    WELS_SAD_SUM_SQSUM_SQDIFF_16x1_SSE2 esi,edi,ebx
    WELS_SAD_SUM_SQSUM_SQDIFF_16x1_SSE2 esi,edi,ebx
    WELS_SAD_SUM_SQSUM_SQDIFF_16x1_SSE2 esi,edi,ebx
    WELS_SAD_SUM_SQSUM_SQDIFF_16x1_SSE2 esi,edi,ebx
    WELS_SAD_SUM_SQSUM_SQDIFF_16x1_SSE2 esi,edi,ebx
    movdqa  xmm1,           xmm7
    movd    [edx],          xmm7
    psrldq  xmm7,           8
    paddd   xmm1,           xmm7
    movd    [edx+4],        xmm7
    movd    ebp,            xmm1
    add             [tmp_sadframe], ebp

    pxor    xmm7,   xmm7
    WELS_SAD_SUM_SQSUM_SQDIFF_16x1_SSE2 esi,edi,ebx
    WELS_SAD_SUM_SQSUM_SQDIFF_16x1_SSE2 esi,edi,ebx
    WELS_SAD_SUM_SQSUM_SQDIFF_16x1_SSE2 esi,edi,ebx
    WELS_SAD_SUM_SQSUM_SQDIFF_16x1_SSE2 esi,edi,ebx
    WELS_SAD_SUM_SQSUM_SQDIFF_16x1_SSE2 esi,edi,ebx
    WELS_SAD_SUM_SQSUM_SQDIFF_16x1_SSE2 esi,edi,ebx
    WELS_SAD_SUM_SQSUM_SQDIFF_16x1_SSE2 esi,edi,ebx
    WELS_SAD_SUM_SQSUM_SQDIFF_16x1_SSE2 esi,edi,ebx
    movdqa  xmm1,           xmm7
    movd    [edx+8],        xmm7
    psrldq  xmm7,           8
    paddd   xmm1,           xmm7
    movd    [edx+12],       xmm7
    movd    ebp,            xmm1
    add             [tmp_sadframe], ebp

    mov             ebp,    [psum16x16]
    movdqa  xmm1,   xmm6
    psrldq  xmm1,   8
    paddd   xmm6,   xmm1
    movd    [ebp],  xmm6
    add             dword [psum16x16], 4

    mov             ebp,    [psqsum16x16]
    pshufd  xmm6,   xmm5,   14 ;00001110
    paddd   xmm6,   xmm5
    pshufd  xmm5,   xmm6,   1  ;00000001
    paddd   xmm5,   xmm6
    movd    [ebp],  xmm5
    add             dword [psqsum16x16], 4

    mov             ebp,    [psqdiff16x16]
    pshufd  xmm5,   xmm4,   14      ; 00001110
    paddd   xmm5,   xmm4
    pshufd  xmm4,   xmm5,   1       ; 00000001
    paddd   xmm4,   xmm5
    movd    [ebp],  xmm4
    add             dword   [psqdiff16x16], 4

    add             edx,    16
    sub             esi,    eax
    sub             edi,    eax
    add             esi,    16
    add             edi,    16

    dec             ecx
    jnz             sqdiff_width_loop

    mov             esi,    [tmp_esi]
    mov             edi,    [tmp_edi]
    add             esi,    eax
    add             edi,    eax

    dec     dword [iPicHeight]
    jnz             sqdiff_height_loop

    mov             ebx,    [tmp_sadframe]
    mov             eax,    [psadframe]
    mov             [eax],  ebx

    add             esp,    localsize
    pop             ebx
    pop             edi
    pop             esi
    pop             ebp
%undef          cur_data
%undef          ref_data
%undef          iPicWidth
%undef          iPicHeight
%undef          iPicStride
%undef          psadframe
%undef          psad8x8
%undef          psum16x16
%undef          psqsum16x16
%undef          psqdiff16x16
%undef          tmp_esi
%undef          tmp_edi
%undef          tmp_sadframe
%undef          pushsize
%undef          localsize
    ret

%else


;*************************************************************************************************************
;void VAACalcSadSsd_sse2(const uint8_t *cur_data, const uint8_t *ref_data, int32_t iPicWidth, int32_t iPicHeight,
;       int32_t iPicStride,int32_t *psadframe, int32_t *psad8x8, int32_t *psum16x16, int32_t *psqsum16x16, int32_t *psqdiff16x16)
;*************************************************************************************************************


WELS_EXTERN VAACalcSadSsd_sse2
%define         localsize               12
%define         cur_data                        arg1;r0
%define         ref_data                        arg2;r1
%define         iPicWidth                       arg3;r2
%define         iPicHeight                      arg4;r3
%define         iPicStride                      arg5;
%define         psadframe                       arg6;
%define         psad8x8                         arg7;
%define         psum16x16                       arg8;
%define         psqsum16x16                     arg9;
%define         psqdiff16x16                    arg10

    push r12
    push r13
    push r14
    push r15
    %assign push_num 4
    PUSH_XMM 10

%ifdef WIN64
    mov r4,arg5
%endif
    mov r14,arg7
    SIGN_EXTENSION r2,r2d
    SIGN_EXTENSION r3,r3d
    SIGN_EXTENSION r4,r4d

    mov        r13,r4
    shr     r2,4   ; iPicWidth/16
    shr     r3,4   ; iPicHeight/16
    shl     r13,4   ; iPicStride*16
    pxor    xmm0,   xmm0
    pxor  xmm8, xmm8  ;framesad
    pxor  xmm9, xmm9
sqdiff_height_loop:
    ;mov            ecx,    dword [iPicWidth]
    ;mov      r14,r2
    push r2
    %assign push_num push_num +1
    mov             r10,    r0
    mov             r11,    r1
sqdiff_width_loop:
    pxor    xmm7,   xmm7            ; hiQuad_loQuad pSad8x8
    pxor    xmm6,   xmm6            ; pSum16x16
    pxor    xmm5,   xmm5            ; sqsum_16x16  four dword
    pxor    xmm4,   xmm4            ; sqdiff_16x16  four Dword
    WELS_SAD_SUM_SQSUM_SQDIFF_16x1_SSE2 r0,r1,r4
    WELS_SAD_SUM_SQSUM_SQDIFF_16x1_SSE2 r0,r1,r4
    WELS_SAD_SUM_SQSUM_SQDIFF_16x1_SSE2 r0,r1,r4
    WELS_SAD_SUM_SQSUM_SQDIFF_16x1_SSE2 r0,r1,r4
    WELS_SAD_SUM_SQSUM_SQDIFF_16x1_SSE2 r0,r1,r4
    WELS_SAD_SUM_SQSUM_SQDIFF_16x1_SSE2 r0,r1,r4
    WELS_SAD_SUM_SQSUM_SQDIFF_16x1_SSE2 r0,r1,r4
    WELS_SAD_SUM_SQSUM_SQDIFF_16x1_SSE2 r0,r1,r4
    movdqa  xmm1,           xmm7
    movd    [r14],          xmm7
    psrldq  xmm7,           8
    paddd   xmm1,           xmm7
    movd    [r14+4],        xmm7
    movd    r15d,           xmm1
    movd  xmm9, r15d
    paddd xmm8,xmm9


    pxor    xmm7,   xmm7
    WELS_SAD_SUM_SQSUM_SQDIFF_16x1_SSE2 r0,r1,r4
    WELS_SAD_SUM_SQSUM_SQDIFF_16x1_SSE2 r0,r1,r4
    WELS_SAD_SUM_SQSUM_SQDIFF_16x1_SSE2 r0,r1,r4
    WELS_SAD_SUM_SQSUM_SQDIFF_16x1_SSE2 r0,r1,r4
    WELS_SAD_SUM_SQSUM_SQDIFF_16x1_SSE2 r0,r1,r4
    WELS_SAD_SUM_SQSUM_SQDIFF_16x1_SSE2 r0,r1,r4
    WELS_SAD_SUM_SQSUM_SQDIFF_16x1_SSE2 r0,r1,r4
    WELS_SAD_SUM_SQSUM_SQDIFF_16x1_SSE2 r0,r1,r4
    movdqa  xmm1,           xmm7
    movd    [r14+8],        xmm7
    psrldq  xmm7,           8
    paddd   xmm1,           xmm7
    movd    [r14+12],       xmm7
    movd    r15d,           xmm1
    movd  xmm9, r15d
    paddd xmm8,xmm9

    mov             r15,    psum16x16
    movdqa  xmm1,   xmm6
    psrldq  xmm1,   8
    paddd   xmm6,   xmm1
    movd    [r15],  xmm6
    add             dword psum16x16, 4

    mov             r15,    psqsum16x16
    pshufd  xmm6,   xmm5,   14 ;00001110
    paddd   xmm6,   xmm5
    pshufd  xmm5,   xmm6,   1  ;00000001
    paddd   xmm5,   xmm6
    movd    [r15],  xmm5
    add             dword psqsum16x16, 4

    mov             r15,    psqdiff16x16
    pshufd  xmm5,   xmm4,   14      ; 00001110
    paddd   xmm5,   xmm4
    pshufd  xmm4,   xmm5,   1       ; 00000001
    paddd   xmm4,   xmm5
    movd    [r15],  xmm4
    add             dword   psqdiff16x16,   4

    add             r14,16
    sub             r0,     r13
    sub             r1,     r13
    add             r0,     16
    add             r1,     16

    dec             r2
    jnz             sqdiff_width_loop

    pop r2
    %assign push_num push_num -1

    mov             r0,     r10
    mov             r1,     r11
    add             r0,     r13
    add             r1,     r13

    dec     r3
    jnz             sqdiff_height_loop

    mov             r13,    psadframe
    movd    [r13],  xmm8

    POP_XMM
    pop r15
    pop r14
    pop r13
    pop r12
    %assign push_num 0

%undef          cur_data
%undef          ref_data
%undef          iPicWidth
%undef          iPicHeight
%undef          iPicStride
%undef          psadframe
%undef          psad8x8
%undef          psum16x16
%undef          psqsum16x16
%undef          psqdiff16x16
%undef          tmp_esi
%undef          tmp_edi
%undef          tmp_sadframe
%undef          pushsize
%undef          localsize
    ret



%endif

%ifdef X86_32
;*************************************************************************************************************
;void VAACalcSadBgd_sse2(const uint8_t *cur_data, const uint8_t *ref_data, int32_t iPicWidth, int32_t iPicHeight,
;                               int32_t iPicStride, int32_t *psadframe, int32_t *psad8x8, int32_t *p_sd8x8, uint8_t *p_mad8x8)
;*************************************************************************************************************


WELS_EXTERN VAACalcSadBgd_sse2
%define         localsize               12
%define         cur_data                        esp + pushsize + localsize + 4
%define         ref_data                        esp + pushsize + localsize + 8
%define         iPicWidth                       esp + pushsize + localsize + 12
%define         iPicHeight                      esp + pushsize + localsize + 16
%define         iPicStride                      esp + pushsize + localsize + 20
%define         psadframe                       esp + pushsize + localsize + 24
%define         psad8x8                         esp + pushsize + localsize + 28
%define         p_sd8x8                         esp + pushsize + localsize + 32
%define         p_mad8x8                        esp + pushsize + localsize + 36
%define         tmp_esi                         esp + 0
%define         tmp_edi                         esp + 4
%define         tmp_ecx                         esp + 8
%define         pushsize                16
    push    ebp
    push    esi
    push    edi
    push    ebx
    sub             esp,    localsize
    mov             esi,    [cur_data]
    mov             edi,    [ref_data]
    mov             ebx,    [iPicStride]
    mov             eax,    ebx

    shr             dword [iPicWidth],      4                                       ; iPicWidth/16
    shr             dword [iPicHeight],     4                                       ; iPicHeight/16
    shl             eax,    4                                                       ; iPicStride*16
    xor             ebp,    ebp
    pxor    xmm0,   xmm0
bgd_height_loop:
    mov             ecx,    dword [iPicWidth]
    mov             [tmp_esi],      esi
    mov             [tmp_edi],      edi
bgd_width_loop:
    pxor    xmm7,   xmm7            ; pSad8x8
    pxor    xmm6,   xmm6            ; sum_cur_8x8
    pxor    xmm5,   xmm5            ; sum_ref_8x8
    pxor    xmm4,   xmm4            ; pMad8x8
    WELS_SAD_SD_MAD_16x1_SSE2       xmm7,   xmm6,   xmm5,   xmm4 ,esi ,edi, ebx
    WELS_SAD_SD_MAD_16x1_SSE2       xmm7,   xmm6,   xmm5,   xmm4 ,esi ,edi, ebx
    WELS_SAD_SD_MAD_16x1_SSE2       xmm7,   xmm6,   xmm5,   xmm4 ,esi ,edi, ebx
    WELS_SAD_SD_MAD_16x1_SSE2       xmm7,   xmm6,   xmm5,   xmm4 ,esi ,edi, ebx
    WELS_SAD_SD_MAD_16x1_SSE2       xmm7,   xmm6,   xmm5,   xmm4 ,esi ,edi, ebx
    WELS_SAD_SD_MAD_16x1_SSE2       xmm7,   xmm6,   xmm5,   xmm4 ,esi ,edi, ebx
    WELS_SAD_SD_MAD_16x1_SSE2       xmm7,   xmm6,   xmm5,   xmm4 ,esi ,edi, ebx
    WELS_SAD_SD_MAD_16x1_SSE2       xmm7,   xmm6,   xmm5,   xmm4 ,esi ,edi, ebx


    mov                     edx,            [p_mad8x8]
    WELS_MAX_REG_SSE2       xmm4

    ;movdqa         xmm1,   xmm4
    ;punpcklbw      xmm1,   xmm0
    ;punpcklwd      xmm1,   xmm0
    ;movd           [edx],  xmm1
    ;punpckhbw      xmm4,   xmm0
    ;punpcklwd      xmm4,   xmm0
    ;movd           [edx+4],        xmm4
    ;add                    edx,            8
    ;mov                    [p_mad8x8],     edx
    mov                     [tmp_ecx],      ecx
    movhlps         xmm1,   xmm4
    movd            ecx,    xmm4
    mov                     [edx],  cl
    movd            ecx,    xmm1
    mov                     [edx+1],cl
    add                     edx,    2
    mov                     [p_mad8x8],     edx


    pslldq          xmm7,   4
    pslldq          xmm6,   4
    pslldq          xmm5,   4


    pxor    xmm4,   xmm4            ; pMad8x8
    WELS_SAD_SD_MAD_16x1_SSE2       xmm7,   xmm6,   xmm5,   xmm4 ,esi ,edi, ebx
    WELS_SAD_SD_MAD_16x1_SSE2       xmm7,   xmm6,   xmm5,   xmm4 ,esi ,edi, ebx
    WELS_SAD_SD_MAD_16x1_SSE2       xmm7,   xmm6,   xmm5,   xmm4 ,esi ,edi, ebx
    WELS_SAD_SD_MAD_16x1_SSE2       xmm7,   xmm6,   xmm5,   xmm4 ,esi ,edi, ebx
    WELS_SAD_SD_MAD_16x1_SSE2       xmm7,   xmm6,   xmm5,   xmm4 ,esi ,edi, ebx
    WELS_SAD_SD_MAD_16x1_SSE2       xmm7,   xmm6,   xmm5,   xmm4 ,esi ,edi, ebx
    WELS_SAD_SD_MAD_16x1_SSE2       xmm7,   xmm6,   xmm5,   xmm4 ,esi ,edi, ebx
    WELS_SAD_SD_MAD_16x1_SSE2       xmm7,   xmm6,   xmm5,   xmm4 ,esi ,edi, ebx

    mov                     edx,            [p_mad8x8]
    WELS_MAX_REG_SSE2       xmm4

    ;movdqa         xmm1,   xmm4
    ;punpcklbw      xmm1,   xmm0
    ;punpcklwd      xmm1,   xmm0
    ;movd           [edx],  xmm1
    ;punpckhbw      xmm4,   xmm0
    ;punpcklwd      xmm4,   xmm0
    ;movd           [edx+4],        xmm4
    ;add                    edx,            8
    ;mov                    [p_mad8x8],     edx
    movhlps         xmm1,   xmm4
    movd            ecx,    xmm4
    mov                     [edx],  cl
    movd            ecx,    xmm1
    mov                     [edx+1],cl
    add                     edx,    2
    mov                     [p_mad8x8],     edx

    ; data in xmm7, xmm6, xmm5:  D1 D3 D0 D2

    mov             edx,    [psad8x8]
    pshufd  xmm1,   xmm7,   10001101b               ; D3 D2 D1 D0
    movdqa  [edx],  xmm1
    add             edx,    16
    mov             [psad8x8],      edx                                     ; sad8x8

    paddd   xmm1,   xmm7                                    ; D1+3 D3+2 D0+1 D2+0
    pshufd  xmm2,   xmm1,   00000011b
    paddd   xmm1,   xmm2
    movd    edx,    xmm1
    add             ebp,    edx                                             ; sad frame

    mov             edx,    [p_sd8x8]
    psubd   xmm6,   xmm5
    pshufd  xmm1,   xmm6,   10001101b
    movdqa  [edx],  xmm1
    add             edx,    16
    mov             [p_sd8x8],      edx


    add             edx,    16
    sub             esi,    eax
    sub             edi,    eax
    add             esi,    16
    add             edi,    16

    mov             ecx,    [tmp_ecx]
    dec             ecx
    jnz             bgd_width_loop

    mov             esi,    [tmp_esi]
    mov             edi,    [tmp_edi]
    add             esi,    eax
    add             edi,    eax

    dec             dword [iPicHeight]
    jnz             bgd_height_loop

    mov             edx,    [psadframe]
    mov             [edx],  ebp

    add             esp,    localsize
    pop             ebx
    pop             edi
    pop             esi
    pop             ebp
%undef          cur_data
%undef          ref_data
%undef          iPicWidth
%undef          iPicHeight
%undef          iPicStride
%undef          psadframe
%undef          psad8x8
%undef          p_sd8x8
%undef          p_mad8x8
%undef          tmp_esi
%undef          tmp_edi
%undef          pushsize
%undef          localsize
    ret



;*************************************************************************************************************
;void VAACalcSadSsdBgd_sse2(const uint8_t *cur_data, const uint8_t *ref_data, int32_t iPicWidth, int32_t iPicHeight,
;                int32_t iPicStride, int32_t *psadframe, int32_t *psad8x8, int32_t *psum16x16, int32_t *psqsum16x16,
;                       int32_t *psqdiff16x16, int32_t *p_sd8x8, uint8_t *p_mad8x8)
;*************************************************************************************************************


WELS_EXTERN VAACalcSadSsdBgd_sse2
%define         localsize               16
%define         cur_data                        esp + pushsize + localsize + 4
%define         ref_data                        esp + pushsize + localsize + 8
%define         iPicWidth                       esp + pushsize + localsize + 12
%define         iPicHeight                      esp + pushsize + localsize + 16
%define         iPicStride                      esp + pushsize + localsize + 20
%define         psadframe                       esp + pushsize + localsize + 24
%define         psad8x8                         esp + pushsize + localsize + 28
%define         psum16x16                       esp + pushsize + localsize + 32
%define         psqsum16x16                     esp + pushsize + localsize + 36
%define         psqdiff16x16            esp + pushsize + localsize + 40
%define         p_sd8x8                         esp + pushsize + localsize + 44
%define         p_mad8x8                        esp + pushsize + localsize + 48
%define         tmp_esi                         esp + 0
%define         tmp_edi                         esp + 4
%define         tmp_sadframe            esp + 8
%define         tmp_ecx                         esp + 12
%define         pushsize                16
    push    ebp
    push    esi
    push    edi
    push    ebx
    sub             esp,    localsize
    mov             esi,    [cur_data]
    mov             edi,    [ref_data]
    mov             ebx,    [iPicStride]
    mov             eax,    ebx

    shr             dword [iPicWidth],      4                                       ; iPicWidth/16
    shr             dword [iPicHeight],     4                                       ; iPicHeight/16
    shl             eax,    4                                                       ; iPicStride*16
    pxor    xmm0,   xmm0
    movd    [tmp_sadframe], xmm0
sqdiff_bgd_height_loop:
    mov             ecx,    dword [iPicWidth]
    mov             [tmp_esi],      esi
    mov             [tmp_edi],      edi
sqdiff_bgd_width_loop:
    pxor    xmm7,   xmm7            ; pSad8x8 interleaves sqsum16x16:  sqsum1 sad1 sqsum0 sad0
    pxor    xmm6,   xmm6            ; sum_8x8 interleaves cur and pRef in Dword,  Sref1 Scur1 Sref0 Scur0
    pxor    xmm5,   xmm5            ; pMad8x8
    pxor    xmm4,   xmm4            ; sqdiff_16x16  four Dword
    WELS_SAD_BGD_SQDIFF_16x1_SSE2   xmm7,   xmm6,   xmm5,   xmm4, esi , edi , ebx
    WELS_SAD_BGD_SQDIFF_16x1_SSE2   xmm7,   xmm6,   xmm5,   xmm4, esi , edi , ebx
    WELS_SAD_BGD_SQDIFF_16x1_SSE2   xmm7,   xmm6,   xmm5,   xmm4, esi , edi , ebx
    WELS_SAD_BGD_SQDIFF_16x1_SSE2   xmm7,   xmm6,   xmm5,   xmm4, esi , edi , ebx
    WELS_SAD_BGD_SQDIFF_16x1_SSE2   xmm7,   xmm6,   xmm5,   xmm4, esi , edi , ebx
    WELS_SAD_BGD_SQDIFF_16x1_SSE2   xmm7,   xmm6,   xmm5,   xmm4, esi , edi , ebx
    WELS_SAD_BGD_SQDIFF_16x1_SSE2   xmm7,   xmm6,   xmm5,   xmm4, esi , edi , ebx
    WELS_SAD_BGD_SQDIFF_16x1_SSE2   xmm7,   xmm6,   xmm5,   xmm4, esi , edi , ebx

    mov             edx,            [psad8x8]
    movdqa  xmm2,           xmm7
    pshufd  xmm1,           xmm2,           00001110b
    movd    [edx],          xmm2
    movd    [edx+4],        xmm1
    add             edx,            8
    mov             [psad8x8],      edx                     ; sad8x8

    paddd   xmm1,                           xmm2
    movd    edx,                            xmm1
    add             [tmp_sadframe],         edx                     ; iFrameSad

    mov             edx,            [psum16x16]
    movdqa  xmm1,           xmm6
    pshufd  xmm2,           xmm1,           00001110b
    paddd   xmm1,           xmm2
    movd    [edx],          xmm1                            ; sum

    mov             edx,            [p_sd8x8]
    pshufd  xmm1,           xmm6,           11110101b                       ; Sref1 Sref1 Sref0 Sref0
    psubd   xmm6,           xmm1            ; 00 diff1 00 diff0
    pshufd  xmm1,           xmm6,           00001000b                       ;  xx xx diff1 diff0
    movq    [edx],          xmm1
    add             edx,            8
    mov             [p_sd8x8],      edx

    mov                     edx,            [p_mad8x8]
    WELS_MAX_REG_SSE2       xmm5
    ;movdqa         xmm1,   xmm5
    ;punpcklbw      xmm1,   xmm0
    ;punpcklwd      xmm1,   xmm0
    ;movd           [edx],  xmm1
    ;punpckhbw      xmm5,   xmm0
    ;punpcklwd      xmm5,   xmm0
    ;movd           [edx+4],        xmm5
    ;add                    edx,            8
    ;mov                    [p_mad8x8],     edx
    mov                     [tmp_ecx],      ecx
    movhlps         xmm1,   xmm5
    movd            ecx,    xmm5
    mov                     [edx],  cl
    movd            ecx,    xmm1
    mov                     [edx+1],cl
    add                     edx,    2
    mov                     [p_mad8x8],     edx

    psrlq   xmm7,   32
    psllq   xmm7,   32                      ; clear sad
    pxor    xmm6,   xmm6            ; sum_8x8 interleaves cur and pRef in Dword,  Sref1 Scur1 Sref0 Scur0
    pxor    xmm5,   xmm5            ; pMad8x8
    WELS_SAD_BGD_SQDIFF_16x1_SSE2   xmm7,   xmm6,   xmm5,   xmm4, esi , edi , ebx
    WELS_SAD_BGD_SQDIFF_16x1_SSE2   xmm7,   xmm6,   xmm5,   xmm4, esi , edi , ebx
    WELS_SAD_BGD_SQDIFF_16x1_SSE2   xmm7,   xmm6,   xmm5,   xmm4, esi , edi , ebx
    WELS_SAD_BGD_SQDIFF_16x1_SSE2   xmm7,   xmm6,   xmm5,   xmm4, esi , edi , ebx
    WELS_SAD_BGD_SQDIFF_16x1_SSE2   xmm7,   xmm6,   xmm5,   xmm4, esi , edi , ebx
    WELS_SAD_BGD_SQDIFF_16x1_SSE2   xmm7,   xmm6,   xmm5,   xmm4, esi , edi , ebx
    WELS_SAD_BGD_SQDIFF_16x1_SSE2   xmm7,   xmm6,   xmm5,   xmm4, esi , edi , ebx
    WELS_SAD_BGD_SQDIFF_16x1_SSE2   xmm7,   xmm6,   xmm5,   xmm4, esi , edi , ebx

    mov             edx,            [psad8x8]
    movdqa  xmm2,           xmm7
    pshufd  xmm1,           xmm2,           00001110b
    movd    [edx],          xmm2
    movd    [edx+4],        xmm1
    add             edx,            8
    mov             [psad8x8],      edx                     ; sad8x8

    paddd   xmm1,                           xmm2
    movd    edx,                            xmm1
    add             [tmp_sadframe],         edx                     ; iFrameSad

    mov             edx,                    [psum16x16]
    movdqa  xmm1,                   xmm6
    pshufd  xmm2,                   xmm1,           00001110b
    paddd   xmm1,                   xmm2
    movd    ebp,                    xmm1                            ; sum
    add             [edx],                  ebp
    add             edx,                    4
    mov             [psum16x16],    edx

    mov             edx,                    [psqsum16x16]
    psrlq   xmm7,                   32
    pshufd  xmm2,                   xmm7,           00001110b
    paddd   xmm2,                   xmm7
    movd    [edx],                  xmm2                            ; sqsum
    add             edx,                    4
    mov             [psqsum16x16],  edx

    mov             edx,            [p_sd8x8]
    pshufd  xmm1,           xmm6,           11110101b                       ; Sref1 Sref1 Sref0 Sref0
    psubd   xmm6,           xmm1            ; 00 diff1 00 diff0
    pshufd  xmm1,           xmm6,           00001000b                       ;  xx xx diff1 diff0
    movq    [edx],          xmm1
    add             edx,            8
    mov             [p_sd8x8],      edx

    mov             edx,            [p_mad8x8]
    WELS_MAX_REG_SSE2       xmm5
    ;movdqa         xmm1,   xmm5
    ;punpcklbw      xmm1,   xmm0
    ;punpcklwd      xmm1,   xmm0
    ;movd           [edx],  xmm1
    ;punpckhbw      xmm5,   xmm0
    ;punpcklwd      xmm5,   xmm0
    ;movd           [edx+4],        xmm5
    ;add                    edx,            8
    ;mov                    [p_mad8x8],     edx
    movhlps         xmm1,   xmm5
    movd            ecx,    xmm5
    mov                     [edx],  cl
    movd            ecx,    xmm1
    mov                     [edx+1],cl
    add                     edx,    2
    mov                     [p_mad8x8],     edx

    mov             edx,            [psqdiff16x16]
    pshufd  xmm1,           xmm4,           00001110b
    paddd   xmm4,           xmm1
    pshufd  xmm1,           xmm4,           00000001b
    paddd   xmm4,           xmm1
    movd    [edx],          xmm4
    add             edx,            4
    mov             [psqdiff16x16], edx

    add             edx,    16
    sub             esi,    eax
    sub             edi,    eax
    add             esi,    16
    add             edi,    16

    mov             ecx,    [tmp_ecx]
    dec             ecx
    jnz             sqdiff_bgd_width_loop

    mov             esi,    [tmp_esi]
    mov             edi,    [tmp_edi]
    add             esi,    eax
    add             edi,    eax

    dec     dword [iPicHeight]
    jnz             sqdiff_bgd_height_loop

    mov             edx,    [psadframe]
    mov             ebp,    [tmp_sadframe]
    mov             [edx],  ebp

    add             esp,    localsize
    pop             ebx
    pop             edi
    pop             esi
    pop             ebp
%undef          cur_data
%undef          ref_data
%undef          iPicWidth
%undef          iPicHeight
%undef          iPicStride
%undef          psadframe
%undef          psad8x8
%undef          psum16x16
%undef          psqsum16x16
%undef          psqdiff16x16
%undef          p_sd8x8
%undef          p_mad8x8
%undef          tmp_esi
%undef          tmp_edi
%undef          pushsize
%undef          localsize
    ret
%else

;*************************************************************************************************************
;void VAACalcSadBgd_sse2(const uint8_t *cur_data, const uint8_t *ref_data, int32_t iPicWidth, int32_t iPicHeight,
;                               int32_t iPicStride, int32_t *psadframe, int32_t *psad8x8, int32_t *p_sd8x8, uint8_t *p_mad8x8)
;*************************************************************************************************************


WELS_EXTERN VAACalcSadBgd_sse2
%define         cur_data                        arg1;
%define         ref_data                        arg2;
%define         iPicWidth                       arg3;
%define         iPicHeight                      arg4;
%define         iPicStride                      arg5;
%define         psadframe                       arg6;
%define         psad8x8                         arg7;
%define         p_sd8x8                         arg8;
%define         p_mad8x8                        arg9;

    push r12
    push r13
    push r14
    push r15
%assign push_num 4
    PUSH_XMM 10
%ifdef WIN64
    mov r4,arg5
    ;  mov r5,arg6
%endif
    mov r14,arg7
    SIGN_EXTENSION r2,r2d
    SIGN_EXTENSION r3,r3d
    SIGN_EXTENSION r4,r4d


    mov     r13,r4
    mov     r15,r0
    shr     r2,4
    shr     r3,4
    shl     r13,4
    pxor    xmm0,   xmm0
    pxor    xmm8,   xmm8
    pxor    xmm9,   xmm9
bgd_height_loop:
    ;mov            ecx,    dword [iPicWidth]
    push r2
    %assign push_num push_num+1
    mov             r10,    r15
    mov             r11,    r1
bgd_width_loop:
    pxor    xmm7,   xmm7            ; pSad8x8
    pxor    xmm6,   xmm6            ; sum_cur_8x8
    pxor    xmm5,   xmm5            ; sum_ref_8x8
    pxor    xmm4,   xmm4            ; pMad8x8
    WELS_SAD_SD_MAD_16x1_SSE2       xmm7,   xmm6,   xmm5,   xmm4 ,r15 ,r1, r4
    WELS_SAD_SD_MAD_16x1_SSE2       xmm7,   xmm6,   xmm5,   xmm4 ,r15 ,r1, r4
    WELS_SAD_SD_MAD_16x1_SSE2       xmm7,   xmm6,   xmm5,   xmm4 ,r15 ,r1, r4
    WELS_SAD_SD_MAD_16x1_SSE2       xmm7,   xmm6,   xmm5,   xmm4 ,r15 ,r1, r4
    WELS_SAD_SD_MAD_16x1_SSE2       xmm7,   xmm6,   xmm5,   xmm4 ,r15 ,r1, r4
    WELS_SAD_SD_MAD_16x1_SSE2       xmm7,   xmm6,   xmm5,   xmm4 ,r15 ,r1, r4
    WELS_SAD_SD_MAD_16x1_SSE2       xmm7,   xmm6,   xmm5,   xmm4 ,r15 ,r1, r4
    WELS_SAD_SD_MAD_16x1_SSE2       xmm7,   xmm6,   xmm5,   xmm4 ,r15 ,r1, r4


    mov                     r14,            p_mad8x8
    WELS_MAX_REG_SSE2       xmm4

    ;mov                    [tmp_ecx],      ecx
    movhlps         xmm1,   xmm4
    movd            r0d,    xmm4


    mov                     [r14],  r0b
    movd            r0d,    xmm1
    mov                     [r14+1],r0b
    add                     r14,    2
    ;mov                     p_mad8x8,       r14


    pslldq          xmm7,   4
    pslldq          xmm6,   4
    pslldq          xmm5,   4


    pxor    xmm4,   xmm4            ; pMad8x8
    WELS_SAD_SD_MAD_16x1_SSE2       xmm7,   xmm6,   xmm5,   xmm4 ,r15 ,r1, r4
    WELS_SAD_SD_MAD_16x1_SSE2       xmm7,   xmm6,   xmm5,   xmm4 ,r15 ,r1, r4
    WELS_SAD_SD_MAD_16x1_SSE2       xmm7,   xmm6,   xmm5,   xmm4 ,r15 ,r1, r4
    WELS_SAD_SD_MAD_16x1_SSE2       xmm7,   xmm6,   xmm5,   xmm4 ,r15 ,r1, r4
    WELS_SAD_SD_MAD_16x1_SSE2       xmm7,   xmm6,   xmm5,   xmm4 ,r15 ,r1, r4
    WELS_SAD_SD_MAD_16x1_SSE2       xmm7,   xmm6,   xmm5,   xmm4 ,r15 ,r1, r4
    WELS_SAD_SD_MAD_16x1_SSE2       xmm7,   xmm6,   xmm5,   xmm4 ,r15 ,r1, r4
    WELS_SAD_SD_MAD_16x1_SSE2       xmm7,   xmm6,   xmm5,   xmm4 ,r15 ,r1, r4

    ;mov                     r14,            [p_mad8x8]
    WELS_MAX_REG_SSE2       xmm4

    movhlps         xmm1,   xmm4
    movd            r0d,    xmm4
    mov                     [r14],  r0b
    movd            r0d,    xmm1
    mov                     [r14+1],r0b
    add                     r14,    2
    mov                     p_mad8x8,       r14

    ; data in xmm7, xmm6, xmm5:  D1 D3 D0 D2

    mov             r14,    psad8x8
    pshufd  xmm1,   xmm7,   10001101b               ; D3 D2 D1 D0
    movdqa  [r14],  xmm1
    add             r14,    16
    mov             psad8x8,        r14                                     ; sad8x8

    paddd   xmm1,   xmm7                                    ; D1+3 D3+2 D0+1 D2+0
    pshufd  xmm2,   xmm1,   00000011b
    paddd   xmm1,   xmm2
    movd    r14d,   xmm1
    movd    xmm9, r14d
    paddd   xmm8,   xmm9                                            ; sad frame

    mov             r14,    p_sd8x8
    psubd   xmm6,   xmm5
    pshufd  xmm1,   xmm6,   10001101b
    movdqa  [r14],  xmm1
    add             r14,    16
    mov             p_sd8x8,        r14


    ;add            edx,    16
    sub             r15,    r13
    sub             r1,     r13
    add             r15,    16
    add             r1,     16


    dec             r2
    jnz             bgd_width_loop
    pop     r2
%assign push_num push_num-1
    mov             r15,    r10
    mov             r1,     r11
    add             r15,    r13
    add             r1,     r13

    dec             r3
    jnz             bgd_height_loop

    mov             r13,    psadframe
    movd    [r13],  xmm8

    POP_XMM
    pop r15
    pop r14
    pop r13
    pop r12
%assign push_num 0
%undef          cur_data
%undef          ref_data
%undef          iPicWidth
%undef          iPicHeight
%undef          iPicStride
%undef          psadframe
%undef          psad8x8
%undef          p_sd8x8
%undef          p_mad8x8
%undef          tmp_esi
%undef          tmp_edi
%undef          pushsize
%undef          localsize
    ret



;*************************************************************************************************************
;void VAACalcSadSsdBgd_sse2(const uint8_t *cur_data, const uint8_t *ref_data, int32_t iPicWidth, int32_t iPicHeight,
;                int32_t iPicStride, int32_t *psadframe, int32_t *psad8x8, int32_t *psum16x16, int32_t *psqsum16x16,
;                       int32_t *psqdiff16x16, int32_t *p_sd8x8, uint8_t *p_mad8x8)
;*************************************************************************************************************


WELS_EXTERN VAACalcSadSsdBgd_sse2
%define         cur_data                        arg1;
%define         ref_data                        arg2;
%define         iPicWidth                       arg3;
%define         iPicHeight                      arg4;
%define         iPicStride                      arg5;
%define         psadframe                       arg6;
%define         psad8x8                         arg7;
%define         psum16x16                       arg8;
%define         psqsum16x16                     arg9;
%define         psqdiff16x16                    arg10;
%define         p_sd8x8                         arg11
%define         p_mad8x8                        arg12

    push r12
    push r13
    push r14
    push r15
%assign push_num 4
    PUSH_XMM 10
%ifdef WIN64
    mov r4,arg5
    ;mov r5,arg6
%endif
    SIGN_EXTENSION r2,r2d
    SIGN_EXTENSION r3,r3d
    SIGN_EXTENSION r4,r4d

    mov     r13,r4
    shr             r2,     4                                       ; iPicWidth/16
    shr             r3,     4                                       ; iPicHeight/16
    shl             r13,    4                                                       ; iPicStride*16
    pxor    xmm0,   xmm0
    pxor    xmm8,   xmm8
    pxor    xmm9,   xmm9


sqdiff_bgd_height_loop:
    mov             r10,    r0
    mov             r11,    r1
    push r2
%assign push_num push_num+1
sqdiff_bgd_width_loop:

    pxor    xmm7,   xmm7            ; pSad8x8 interleaves sqsum16x16:  sqsum1 sad1 sqsum0 sad0
    pxor    xmm6,   xmm6            ; sum_8x8 interleaves cur and pRef in Dword,  Sref1 Scur1 Sref0 Scur0
    pxor    xmm5,   xmm5            ; pMad8x8
    pxor    xmm4,   xmm4            ; sqdiff_16x16  four Dword
    WELS_SAD_BGD_SQDIFF_16x1_SSE2   xmm7,   xmm6,   xmm5,   xmm4, r0 , r1 , r4
    WELS_SAD_BGD_SQDIFF_16x1_SSE2   xmm7,   xmm6,   xmm5,   xmm4, r0 , r1 , r4
    WELS_SAD_BGD_SQDIFF_16x1_SSE2   xmm7,   xmm6,   xmm5,   xmm4, r0 , r1 , r4
    WELS_SAD_BGD_SQDIFF_16x1_SSE2   xmm7,   xmm6,   xmm5,   xmm4, r0 , r1 , r4
    WELS_SAD_BGD_SQDIFF_16x1_SSE2   xmm7,   xmm6,   xmm5,   xmm4, r0 , r1 , r4
    WELS_SAD_BGD_SQDIFF_16x1_SSE2   xmm7,   xmm6,   xmm5,   xmm4, r0 , r1 , r4
    WELS_SAD_BGD_SQDIFF_16x1_SSE2   xmm7,   xmm6,   xmm5,   xmm4, r0 , r1 , r4
    WELS_SAD_BGD_SQDIFF_16x1_SSE2   xmm7,   xmm6,   xmm5,   xmm4, r0 , r1 , r4

    mov             r14,            psad8x8
    movdqa  xmm2,           xmm7
    pshufd  xmm1,           xmm2,           00001110b
    movd    [r14],          xmm2
    movd    [r14+4],        xmm1
    add             r14,            8
    mov             psad8x8,        r14                     ; sad8x8

    paddd   xmm1,                           xmm2
    movd    r14d,                           xmm1
    movd    xmm9,r14d
    paddd           xmm8,           xmm9                    ; iFrameSad

    mov             r14,            psum16x16
    movdqa  xmm1,           xmm6
    pshufd  xmm2,           xmm1,           00001110b
    paddd   xmm1,           xmm2
    movd    [r14],          xmm1                            ; sum

    mov             r14,            p_sd8x8
    pshufd  xmm1,           xmm6,           11110101b                       ; Sref1 Sref1 Sref0 Sref0
    psubd   xmm6,           xmm1            ; 00 diff1 00 diff0
    pshufd  xmm1,           xmm6,           00001000b                       ;  xx xx diff1 diff0
    movq    [r14],          xmm1
    add             r14,            8
    mov             p_sd8x8,        r14

    mov                     r14,            p_mad8x8
    WELS_MAX_REG_SSE2       xmm5

    movhlps         xmm1,   xmm5
    push r0
    movd            r0d,    xmm5
    mov                     [r14],  r0b
    movd            r0d,    xmm1
    mov                     [r14+1],r0b
    pop r0
    add                     r14,    2
    mov                     p_mad8x8,       r14

    psrlq   xmm7,   32
    psllq   xmm7,   32                      ; clear sad
    pxor    xmm6,   xmm6            ; sum_8x8 interleaves cur and pRef in Dword,  Sref1 Scur1 Sref0 Scur0
    pxor    xmm5,   xmm5            ; pMad8x8
    WELS_SAD_BGD_SQDIFF_16x1_SSE2   xmm7,   xmm6,   xmm5,   xmm4, r0 , r1 , r4
    WELS_SAD_BGD_SQDIFF_16x1_SSE2   xmm7,   xmm6,   xmm5,   xmm4, r0 , r1 , r4
    WELS_SAD_BGD_SQDIFF_16x1_SSE2   xmm7,   xmm6,   xmm5,   xmm4, r0 , r1 , r4
    WELS_SAD_BGD_SQDIFF_16x1_SSE2   xmm7,   xmm6,   xmm5,   xmm4, r0 , r1 , r4
    WELS_SAD_BGD_SQDIFF_16x1_SSE2   xmm7,   xmm6,   xmm5,   xmm4, r0 , r1 , r4
    WELS_SAD_BGD_SQDIFF_16x1_SSE2   xmm7,   xmm6,   xmm5,   xmm4, r0 , r1 , r4
    WELS_SAD_BGD_SQDIFF_16x1_SSE2   xmm7,   xmm6,   xmm5,   xmm4, r0 , r1 , r4
    WELS_SAD_BGD_SQDIFF_16x1_SSE2   xmm7,   xmm6,   xmm5,   xmm4, r0 , r1 , r4

    mov             r14,            psad8x8
    movdqa  xmm2,           xmm7
    pshufd  xmm1,           xmm2,           00001110b
    movd    [r14],          xmm2
    movd    [r14+4],        xmm1
    add             r14,            8
    mov             psad8x8,        r14                     ; sad8x8

    paddd   xmm1,                           xmm2
    movd    r14d,                           xmm1
    movd    xmm9, r14d
    paddd   xmm8,           xmm9            ; iFrameSad

    mov             r14,                    psum16x16
    movdqa  xmm1,                   xmm6
    pshufd  xmm2,                   xmm1,           00001110b
    paddd   xmm1,                   xmm2
    movd    r15d,                   xmm1                            ; sum
    add             [r14],                  r15d
    add             r14,                    4
    mov             psum16x16,      r14

    mov             r14,                    psqsum16x16
    psrlq   xmm7,                   32
    pshufd  xmm2,                   xmm7,           00001110b
    paddd   xmm2,                   xmm7
    movd    [r14],                  xmm2                            ; sqsum
    add             r14,                    4
    mov             psqsum16x16,    r14

    mov             r14,            p_sd8x8
    pshufd  xmm1,           xmm6,           11110101b                       ; Sref1 Sref1 Sref0 Sref0
    psubd   xmm6,           xmm1            ; 00 diff1 00 diff0
    pshufd  xmm1,           xmm6,           00001000b                       ;  xx xx diff1 diff0
    movq    [r14],          xmm1
    add             r14,            8
    mov             p_sd8x8,        r14

    mov             r14,            p_mad8x8
    WELS_MAX_REG_SSE2       xmm5


    movhlps         xmm1,   xmm5
    push r0
    movd            r0d,    xmm5
    mov                     [r14],  r0b
    movd            r0d,    xmm1
    mov                     [r14+1],r0b
    pop r0
    add                     r14,    2
    mov                     p_mad8x8,       r14

    mov             r14,            psqdiff16x16
    pshufd  xmm1,           xmm4,           00001110b
    paddd   xmm4,           xmm1
    pshufd  xmm1,           xmm4,           00000001b
    paddd   xmm4,           xmm1
    movd    [r14],          xmm4
    add             r14,            4
    mov             psqdiff16x16,   r14

    add             r14,    16
    sub             r0,     r13
    sub             r1,     r13
    add             r0,     16
    add             r1,     16

    dec             r2
    jnz             sqdiff_bgd_width_loop
    pop r2
    %assign push_num push_num-1
    mov             r0,     r10
    mov             r1,     r11
    add             r0,     r13
    add             r1,     r13

    dec     r3
    jnz             sqdiff_bgd_height_loop

    mov             r14,    psadframe
    movd    [r14],  xmm8

    POP_XMM
    pop r15
    pop r14
    pop r13
    pop r12
%assign push_num 0
%undef          cur_data
%undef          ref_data
%undef          iPicWidth
%undef          iPicHeight
%undef          iPicStride
%undef          psadframe
%undef          psad8x8
%undef          psum16x16
%undef          psqsum16x16
%undef          psqdiff16x16
%undef          p_sd8x8
%undef          p_mad8x8
%undef          tmp_esi
%undef          tmp_edi
%undef          pushsize
%undef          localsize
    ret
%endif

%ifdef X86_32
%define ptrword dword
%else
%define ptrword qword
%endif

%define xmm_width 16
%define ymm_width 32

%macro PUSHM 1-*
    %rep %0
        push           %1
        %rotate 1
    %endrep
    %assign push_num push_num + %0
%endmacro

%macro POPM 1-*
    %rep %0
        %rotate -1
        pop            %1
    %endrep
    %assign push_num push_num - %0
%endmacro

%ifdef X86_32
%define stack_alloc_min 4
%else
%define stack_alloc_min 8
%endif

; Allocate aligned stack space.
; address_out=%1 size=%2 alignment=%3
%macro STACK_ALLOC 3
%if (%3) & ((%3) - 1)
    %error non-power-of-2 alignment requested.
%endif
%if (%3) > 0
    %assign stack_alloc_align ((%3) + stack_alloc_min - 1) / stack_alloc_min
%else
    %assign stack_alloc_align 1
%endif
    %assign stack_alloc_num ((%2) + stack_alloc_min - 1) / stack_alloc_min + stack_alloc_align - 1
    %assign push_num push_num + stack_alloc_num
    sub            r7, stack_alloc_min * stack_alloc_num
%if stack_alloc_align == 1
    mov            %1, r7
%else
    lea            %1, [r7 + stack_alloc_min * (stack_alloc_align - 1)]
    and            %1, -(stack_alloc_min * stack_alloc_align)
%endif
%endmacro

; Deallocate stack space allocated with STACK_ALLOC.
%macro STACK_DEALLOC 0
    add            r7, stack_alloc_min * stack_alloc_num
    %assign push_num push_num - stack_alloc_num
%endmacro

%ifdef HAVE_AVX2
; Max unsigned byte per quadword
; out=%1 in=%2 tmp=%3
%macro AVX2_Maxubq 3
    vpsrlq         %3, %2, 32
    vpmaxub        %1, %2, %3
    vpsrlq         %3, %1, 16
    vpmaxub        %1, %1, %3
    vpsrlq         %3, %1,  8
    vpmaxub        %1, %1, %3
%endmacro

; Max unsigned byte per quadword. 2 register input.
; Results interleaved as least significant byte of even/odd doublewords.
; out=%1 in_a=%2 in_b=%3 tmp=%4
%macro AVX2_Maxubq2 4
    vpblendd       %4, %2, %3, 10101010b
    vpshufd        %4, %4, 10110001b
    vpblendd       %1, %2, %3, 01010101b
    vpmaxub        %1, %4, %1
    vpsrld         %4, %1, 16
    vpmaxub        %1, %1, %4
    vpsrld         %4, %1,  8
    vpmaxub        %1, %1, %4
%endmacro

; res=%1 src=%2 zero=%3 tmp=%4 add_to_res=%5
%macro AVX2_Sqsumbdw 5
    vpunpcklbw     %4, %2, %3
%if %5
    vpmaddwd       %4, %4, %4
    vpaddd         %1, %1, %4
%else
    vpmaddwd       %1, %4, %4
%endif
    vpunpckhbw     %4, %2, %3
    vpmaddwd       %4, %4, %4
    vpaddd         %1, %1, %4
%endmacro

; res=%1 src=%2 zero=%3 tmp=%4 add_to_res=%5
%macro AVX2_Sumbdw 5
%if %5
    vpsadbw        %4, %2, %3
    vpaddd         %1, %1, %4
%else
    vpsadbw        %1, %2, %3
%endif
%endmacro

; res=%1 a=%2 b=%3 a=%4 tmp=%5
%macro AVX2_AbsDiffub 5
    vpsubusb       %5, %2, %3
    vpsubusb       %1, %3, %4
    vpor           %1, %5, %1
%endmacro

; sad=%1 cur_data=%2 ref_data=%3 tmp=%4 accumulate_results=%5
%macro AVX2_Sadbdw 5
%if %5
    vpsadbw        %4, %2, %3
    vpaddd         %1, %1, %4
%else
    vpsadbw        %1, %2, %3
%endif
%endmacro

; sad=%1 sum_cur=%2 sqsum_cur=%3 cur_data=%4 ref_data=%5 zero=%6 tmp=%7 accumulate_results=%8
%macro AVX2_SadSumSqsumbdw 8
    AVX2_Sadbdw    %1, %4, %5, %7, %8
    AVX2_Sumbdw    %2, %4, %6, %7, %8
    AVX2_Sqsumbdw  %3, %4, %6, %7, %8
%endmacro

; sad=%1 pCur=%2 pRef=%3 tmp=%4 accumulate_results=%5
%macro AVX2_Sad 5
    vmovdqu        %4, [%2]
    AVX2_Sadbdw    %1, %4, [%3], %4, %5
%endmacro

; sad=%1 sum_cur=%2 sqsum_cur=%3 pCur=%4 pRef=%5 zero=%6 tmp=%7,%8 accumulate_results=%9
%macro AVX2_SadSumSqsum 9
    vmovdqu        %7, [%4]
    AVX2_SadSumSqsumbdw %1, %2, %3, %7, [%5], %6, %8, %9
%endmacro

; sad=%1 sum_cur=%2 sqsum_cur=%3 sqdiff=%4 pCur=%5 pRef=%6 zero=%7 tmp=%8,%9,%10 accumulate_results=%11
%macro AVX2_SadSumSqsumSqdiff 11
    vmovdqu        %8,  [%5]
    vmovdqu        %9,  [%6]
    AVX2_SadSumSqsumbdw %1, %2, %3, %8, %9, %7, %10, %11
    AVX2_AbsDiffub %9,  %8,  %9,  %8,  %10
    AVX2_Sqsumbdw  %4,  %9,  %7,  %10, %11
%endmacro

; sad=%1 sum_cur=%2 sum_ref=%3 mad=%4 pCur=%5 pRef=%6 zero=%7 tmp=%8,%9,%10 accumulate_results=%11
%macro AVX2_SadSdMad 11
    vmovdqu        %8,  [%5]
    vmovdqu        %9,  [%6]
    AVX2_Sumbdw    %2,  %8,  %7,  %10, %11
    AVX2_Sumbdw    %3,  %9,  %7,  %10, %11
    AVX2_Sadbdw    %1,  %8,  %9,  %10, %11
%if %11
    AVX2_AbsDiffub %9,  %8,  %9,  %8, %10
    vpmaxub        %4,  %4,  %9
%else
    AVX2_AbsDiffub %4,  %8,  %9,  %8, %10
%endif
%endmacro

; sad=%1 sum_cur=%2 sum_ref=%3 mad=%4 sqdiff=%5 sqsum_cur=%6 pCur=%7 pRef=%8 zero=%9 tmp=%10,%11,%12 accumulate_results=%13
%macro AVX2_SadBgdSqdiff 13
%ifidn %12, 0
    vmovdqu        %10, [%7]
    AVX2_Sumbdw    %2,  %10, %9,  %11, %13
    AVX2_Sqsumbdw  %6,  %10, %9,  %11, %13
    vmovdqu        %11, [%8]
    AVX2_Sadbdw    %1,  %10, %11, %10, %13
    AVX2_Sumbdw    %3,  %11, %9,  %10, %13
    vmovdqu        %10, [%7]
%if %13
    AVX2_AbsDiffub %11, %10, %11, [%7], %10
    vpmaxub        %4,  %4,  %11
    AVX2_Sqsumbdw  %5,  %11, %9,  %10, %13
%else
    AVX2_AbsDiffub %4,  %10, %11, [%7], %10
    AVX2_Sqsumbdw  %5,  %4,  %9,  %10, %13
%endif
%else
    vmovdqu        %10, [%7]
    vmovdqu        %11, [%8]
    AVX2_Sadbdw    %1,  %10, %11, %12, %13
    AVX2_Sumbdw    %2,  %10, %9,  %12, %13
    AVX2_Sumbdw    %3,  %11, %9,  %12, %13
    AVX2_Sqsumbdw  %6,  %10, %9,  %12, %13
%if %13
    AVX2_AbsDiffub %11, %10, %11, %10, %12
    vpmaxub        %4,  %4,  %11
    AVX2_Sqsumbdw  %5,  %11, %9,  %10, %13
%else
    AVX2_AbsDiffub %4,  %10, %11, %10, %12
    AVX2_Sqsumbdw  %5,  %4,  %9,  %10, %13
%endif
%endif
%endmacro

; p_dst=%1 mmreg_prefix=%2 data=%3 tmp=%4 second_blocks=%5
%macro AVX2_Store8x8Accdw 5
    vpshufd        %2%4, %2%3, 1000b
%ifidni %2, x
    vmovlps        [%1 + 8 * %5], x%4
%elif %5 == 0
    vmovdqu        [%1], %2%4
%else
    vmovlps        [%1 +  8], x%4
    vextracti128   x%4, %2%4, 1
    vmovlps        [%1 + 24], x%4
%endif
%endmacro

; p_dst=%1 mmreg_prefix=%2 data=%3 tmp=%4 second_blocks=%5
%macro AVX2_Store8x8Accb 5
    vpunpckhqdq    %2%4, %2%3, %2%3
    vpunpcklbw     %2%4, %2%3, %2%4
%if %5 == 0
    vmovd          [%1 + 0], x%4
%ifidni %2, y
    vextracti128   x%4, %2%4, 1
    vmovd          [%1 + 4], x%4
%endif
%else
    vpextrw        [%1 + 2], x%4, 0
%ifidni %2, y
    vextracti128   x%4, %2%4, 1
    vpextrw        [%1 + 6], x%4, 0
%endif
%endif
%endmacro

; p_dst=%1 data=%2 tmp=%3,%4 second_blocks=%5
%macro AVX2_Store2x8x8Accb 5
    vpunpckhqdq    y%3, y%2, y%2
    vpunpcklbw     y%3, y%2, y%3
    vextracti128   x%4, y%3, 1
    vpsllq         x%4, x%4, 32
    vpblendd       x%4, x%3, x%4, 1010b
%if %5
    vpslld         x%4, x%4, 16
    vpblendw       x%4, x%4, [%1], 01010101b
%endif
    vmovdqu        [%1], x%4
%endmacro

; p_dst=%1 mmreg_prefix=%2 data=%3 tmp=%4 add_to_dst=%5
%macro AVX2_Store16x16Accdw 5
%ifidni %2, x
%if %5
    vmovd          x%4, [%1 + 0]
    vpaddd         x%3, x%4, x%3
%endif
    vmovd          [%1 + 0], x%3
%elif %5 == 0
    vmovd          [%1 + 0], x%3
    vextracti128   x%3, %2%3, 1
    vmovd          [%1 + 4], x%3
%else
    vextracti128   x%4, %2%3, 1
    vpunpckldq     x%4, x%3, x%4
    vmovq          x%3, [%1 + 0]
    vpaddd         x%3, x%3, x%4
    vmovlps        [%1 + 0], x%3
%endif
%endmacro

; p_dst1=%1 p_dst2=%2 i_dst_offset=%3 gpr_tmp=%4 mmreg_prefix=%5 data=%6 mm_tmp=%7 add_to_dst=%8
%macro AVX2_Store2x16x16Accdw 8
%ifidni %5, x
    mov            %4, %1
%if %8 == 0
    vmovd          [%4 + %3], x%6
    mov            %4, %2
    vpextrd        [%4 + %3], x%6, 2
%else
    vmovd          x%7, [%4 + %3]
    vpaddd         x%7, x%7, x%6
    vmovd          [%4 + %3], x%7
    mov            %4, %2
    vpbroadcastd   x%7, [%4 + %3]
    vpaddd         x%7, x%7, x%6
    vpextrd        [%4 + %3], x%7, 2
%endif
%else
    vextracti128   x%7, %5%6, 1
    vpblendd       x%6, x%6, x%7, 1010b
    mov            %4, %1
%if %8 == 0
    vmovlps        [%4 + %3], x%6
    mov            %4, %2
    vmovhps        [%4 + %3], x%6
%else
    vmovq          x%7, [%4 + %3]
    vpaddd         x%7, x%7, x%6
    vmovlps        [%4 + %3], x%7
    mov            %4, %2
    vpbroadcastq   x%7, [%4 + %3]
    vpaddd         x%7, x%7, x%6
    vmovhps        [%4 + %3], x%7
%endif
%endif
%endmacro


; x/y-mm_prefix=%1 mm_clobber=%2,%3,%4,%5,%6 b_second_blocks=%7
%macro AVX2_CalcSad_8Lines 7
%define mm_tmp0    %2
%define mm_sad     %3
%define mm_sad2    %4
%define mm_sad3    %5
%define mm_sad4    %6
%define b_second_blocks %7
%ifdef i_stride5
    %define i_stride5_ i_stride5
%else
    lea            r_tmp, [5 * i_stride]
    %define i_stride5_ r_tmp
%endif
    ; Use multiple accumulators to shorten dependency chains and enable more parallelism.
    AVX2_Sad       %1 %+ mm_sad,  p_cur,                  p_ref,                  %1 %+ mm_tmp0, 0
    AVX2_Sad       %1 %+ mm_sad2, p_cur + 1 * i_stride,   p_ref + 1 * i_stride,   %1 %+ mm_tmp0, 0
    AVX2_Sad       %1 %+ mm_sad3, p_cur + 2 * i_stride,   p_ref + 2 * i_stride,   %1 %+ mm_tmp0, 0
    AVX2_Sad       %1 %+ mm_sad4, p_cur + 1 * i_stride3,  p_ref + 1 * i_stride3,  %1 %+ mm_tmp0, 0
    AVX2_Sad       %1 %+ mm_sad,  p_cur + 4 * i_stride,   p_ref + 4 * i_stride,   %1 %+ mm_tmp0, 1
    AVX2_Sad       %1 %+ mm_sad2, p_cur + 1 * i_stride5_, p_ref + 1 * i_stride5_, %1 %+ mm_tmp0, 1
%ifdef i_stride7
    %define i_stride7_ i_stride7
%else
    lea            r_tmp, [i_stride + 2 * i_stride3]
    %define i_stride7_ r_tmp
%endif
    AVX2_Sad       %1 %+ mm_sad3, p_cur + 2 * i_stride3,  p_ref + 2 * i_stride3,  %1 %+ mm_tmp0, 1
    AVX2_Sad       %1 %+ mm_sad4, p_cur + 1 * i_stride7_, p_ref + 1 * i_stride7_, %1 %+ mm_tmp0, 1
%undef i_stride5_
%undef i_stride7_
    ; Increment addresses for the next iteration. Doing this early is beneficial on Haswell.
    add            p_cur, %1 %+ mm_width
    add            p_ref, %1 %+ mm_width
    ; Collapse accumulators.
    vpaddd         %1 %+ mm_sad,  %1 %+ mm_sad,  %1 %+ mm_sad2
    vpaddd         %1 %+ mm_sad3, %1 %+ mm_sad3, %1 %+ mm_sad4
    vpaddd         %1 %+ mm_sad,  %1 %+ mm_sad,  %1 %+ mm_sad3
    AVX2_Store8x8Accdw p_sad8x8 + xcnt_unit * i_xcnt, %1, mm_sad, mm_tmp0, b_second_blocks
    vpaddd         y %+ mm_sadframe, y %+ mm_sadframe, y %+ mm_sad
%undef mm_tmp0
%undef mm_sad
%undef mm_sad2
%undef mm_sad3
%undef mm_sad4
%undef b_second_blocks
%endmacro

;*************************************************************************************************************
;void VAACalcSad_avx2( const uint8_t *cur_data, const uint8_t *ref_data, int32_t iPicWidth, int32_t iPicHeight
;                                                               int32_t iPicStride, int32_t *psadframe, int32_t *psad8x8)
;*************************************************************************************************************

WELS_EXTERN VAACalcSad_avx2
%define          p_sadframe                    ptrword arg6
%define          p_sad8x8                      ptrword arg7
%ifdef X86_32
%define          saveregs                      r5, r6
%else
%define          saveregs                      rbx, rbp, r12
%endif

%assign push_num 0
    LOAD_5_PARA
    PUSH_XMM 7
    SIGN_EXTENSION r2, r2d
    SIGN_EXTENSION r3, r3d
    SIGN_EXTENSION r4, r4d
    PUSHM          saveregs

%define mm_zero mm0
%define mm_sadframe mm6
    vpxor          x %+ mm_zero, x %+ mm_zero, x %+ mm_zero
    vmovdqa        y %+ mm_sadframe, y %+ mm_zero

    and            r2, -16                     ; iPicWidth &= -16
    jle            .done                       ; bail if iPicWidth < 16
    sar            r3, 4                       ; iPicHeight / 16
    jle            .done                       ; bail if iPicHeight < 16
    shr            r2, 2                       ; iPicWidth / 4

%define p_cur     r0
%define p_ref     r1
%define i_xcnt    r2
%define i_ycnt    ptrword arg4
%define i_stride  r4
%define xcnt_unit 4
%ifdef X86_32
    mov            i_ycnt, r3
    mov            r5, p_sad8x8
    %define i_stride3 r3
    %undef  p_sad8x8
    %define p_sad8x8  r5
    %define r_tmp     r6
    lea            i_stride3, [3 * i_stride]
%else
    mov            rbp, p_sad8x8
    %define i_stride3 rbx
    %define i_stride5 r12
    %define i_stride7 r6
    %undef  p_sad8x8
    %define p_sad8x8  rbp
    lea            i_stride3, [3 * i_stride]
    lea            i_stride5, [5 * i_stride]
    lea            i_stride7, [i_stride + 2 * i_stride3]
%endif

    ; offset pointer so as to compensate for the i_xcnt offset below.
    sub            p_sad8x8, 4 * 16 / xcnt_unit

    push           i_xcnt
%assign push_num push_num + 1
%define i_xcnt_load ptrword [r7]

.height_loop:
    ; use end-of-line pointers so as to enable use of a negative counter as index.
    lea            p_sad8x8, [p_sad8x8 + xcnt_unit * i_xcnt]
    ; use a negative loop counter so as to enable counting toward zero and indexing with the same counter.
    neg            i_xcnt
    add            i_xcnt, 16 / xcnt_unit
    jz             .width_loop_upper8_remaining16
.width_loop_upper8:
    AVX2_CalcSad_8Lines y, mm1, mm2, mm3, mm4, mm5, 0
    add            i_xcnt, 32 / xcnt_unit
    jl             .width_loop_upper8
    jg             .width_loop_upper8_end
.width_loop_upper8_remaining16:
    AVX2_CalcSad_8Lines x, mm1, mm2, mm3, mm4, mm5, 0
.width_loop_upper8_end:
    lea            p_cur, [p_cur + 8 * i_stride]
    lea            p_ref, [p_ref + 8 * i_stride]
    xor            i_xcnt, i_xcnt
    sub            i_xcnt, i_xcnt_load
    lea            p_cur, [p_cur + xcnt_unit * i_xcnt]
    lea            p_ref, [p_ref + xcnt_unit * i_xcnt]
    add            i_xcnt, 16 / xcnt_unit
    jz             .width_loop_lower8_remaining16
.width_loop_lower8:
    AVX2_CalcSad_8Lines y, mm1, mm2, mm3, mm4, mm5, 1
    add            i_xcnt, 32 / xcnt_unit
    jl             .width_loop_lower8
    jg             .width_loop_lower8_end
.width_loop_lower8_remaining16:
    AVX2_CalcSad_8Lines x, mm1, mm2, mm3, mm4, mm5, 1
.width_loop_lower8_end:
    lea            p_cur, [p_cur + 8 * i_stride]
    lea            p_ref, [p_ref + 8 * i_stride]
    xor            i_xcnt, i_xcnt
    sub            i_xcnt, i_xcnt_load
    lea            p_cur, [p_cur + xcnt_unit * i_xcnt]
    lea            p_ref, [p_ref + xcnt_unit * i_xcnt]
    neg            i_xcnt
    sub            i_ycnt, 1
    jnz            .height_loop

    pop            i_xcnt
%assign push_num push_num - 1
%undef i_xcnt_load

.done:
    mov            r6, p_sadframe
    vextracti128   xmm2, y %+ mm_sadframe, 1
    vpaddd         xmm2, x %+ mm_sadframe, xmm2
    vpunpckhqdq    xmm1, xmm2, xmm2
    vpaddd         xmm2, xmm2, xmm1
    vmovd          [r6], xmm2
    vzeroupper

    POPM           saveregs
    POP_XMM
    LOAD_5_PARA_POP
%undef           p_cur
%undef           p_ref
%undef           i_xcnt
%undef           i_ycnt
%undef           i_stride
%undef           r_tmp
%undef           xcnt_unit
%undef           i_stride3
%undef           i_stride5
%undef           i_stride7
%undef           mm_sadframe
%undef           mm_zero
%undef           saveregs
%undef           p_sadframe
%undef           p_sad8x8
    ret


; x/y-mm_prefix=%1 mm_clobber=%2,%3,%4,%5,%6 b_second_blocks=%7
%macro AVX2_CalcSadVar_8Lines 7
%define mm_tmp0    %2
%define mm_tmp1    %3
%define mm_sad     %4
%define mm_sum     %5
%define mm_sqsum   %6
%define b_second_blocks %7
    ; Unroll for better performance on Haswell.
    ; Avoid unrolling for the 16 px case so as to reduce the code footprint.
%ifidni %1, y
    lea            r_tmp, [5 * i_stride]
    AVX2_SadSumSqsum %1 %+ mm_sad, %1 %+ mm_sum, %1 %+ mm_sqsum, p_cur,                 p_ref,                 %1 %+ mm_zero, %1 %+ mm_tmp0, %1 %+ mm_tmp1, 0
    AVX2_SadSumSqsum %1 %+ mm_sad, %1 %+ mm_sum, %1 %+ mm_sqsum, p_cur + 1 * i_stride,  p_ref + 1 * i_stride,  %1 %+ mm_zero, %1 %+ mm_tmp0, %1 %+ mm_tmp1, 1
    AVX2_SadSumSqsum %1 %+ mm_sad, %1 %+ mm_sum, %1 %+ mm_sqsum, p_cur + 2 * i_stride,  p_ref + 2 * i_stride,  %1 %+ mm_zero, %1 %+ mm_tmp0, %1 %+ mm_tmp1, 1
    AVX2_SadSumSqsum %1 %+ mm_sad, %1 %+ mm_sum, %1 %+ mm_sqsum, p_cur + 1 * i_stride3, p_ref + 1 * i_stride3, %1 %+ mm_zero, %1 %+ mm_tmp0, %1 %+ mm_tmp1, 1
    AVX2_SadSumSqsum %1 %+ mm_sad, %1 %+ mm_sum, %1 %+ mm_sqsum, p_cur + 4 * i_stride,  p_ref + 4 * i_stride,  %1 %+ mm_zero, %1 %+ mm_tmp0, %1 %+ mm_tmp1, 1
    AVX2_SadSumSqsum %1 %+ mm_sad, %1 %+ mm_sum, %1 %+ mm_sqsum, p_cur + r_tmp,         p_ref + r_tmp,         %1 %+ mm_zero, %1 %+ mm_tmp0, %1 %+ mm_tmp1, 1
    lea            r_tmp, [i_stride + 2 * i_stride3]
    AVX2_SadSumSqsum %1 %+ mm_sad, %1 %+ mm_sum, %1 %+ mm_sqsum, p_cur + 2 * i_stride3, p_ref + 2 * i_stride3, %1 %+ mm_zero, %1 %+ mm_tmp0, %1 %+ mm_tmp1, 1
    AVX2_SadSumSqsum %1 %+ mm_sad, %1 %+ mm_sum, %1 %+ mm_sqsum, p_cur + r_tmp,         p_ref + r_tmp,         %1 %+ mm_zero, %1 %+ mm_tmp0, %1 %+ mm_tmp1, 1
    ; Increment addresses for the next iteration. Doing this early is beneficial on Haswell.
    add            p_cur, %1 %+ mm_width
    add            p_ref, %1 %+ mm_width
%else
    vpxor          x %+ mm_sad, x %+ mm_sad, x %+ mm_sad
    vpxor          x %+ mm_sum, x %+ mm_sum, x %+ mm_sum
    vpxor          x %+ mm_sqsum, x %+ mm_sqsum, x %+ mm_sqsum
    lea            r_tmp, [8 * i_stride]
    add            p_cur, r_tmp
    add            p_ref, r_tmp
    neg            r_tmp
%%loop:
    AVX2_SadSumSqsum %1 %+ mm_sad, %1 %+ mm_sum, %1 %+ mm_sqsum, p_cur + r_tmp, p_ref + r_tmp, %1 %+ mm_zero, %1 %+ mm_tmp0, %1 %+ mm_tmp1, 1
    add            r_tmp, i_stride
    jl             %%loop
    ; Increment addresses for the next iteration. Doing this early is beneficial on Haswell.
    lea            r_tmp, [8 * i_stride - %1 %+ mm_width]
    sub            p_cur, r_tmp
    sub            p_ref, r_tmp
%endif
    AVX2_Store8x8Accdw p_sad8x8 + 4 * i_xcnt, %1, mm_sad, mm_tmp1, b_second_blocks
    vpaddd         y %+ mm_sadframe, y %+ mm_sadframe, y %+ mm_sad
    vpunpcklqdq    %1 %+ mm_tmp0, %1 %+ mm_sum, %1 %+ mm_sqsum
    vpunpckhqdq    %1 %+ mm_tmp1, %1 %+ mm_sum, %1 %+ mm_sqsum
    vpaddd         %1 %+ mm_tmp0, %1 %+ mm_tmp0, %1 %+ mm_tmp1
    vpshufd        %1 %+ mm_tmp1, %1 %+ mm_tmp0, 10110001b
    vpaddd         %1 %+ mm_tmp0, %1 %+ mm_tmp0, %1 %+ mm_tmp1
    AVX2_Store2x16x16Accdw p_sum16x16, p_sqsum16x16, i_xcnt, r_tmp, %1, mm_tmp0, mm_tmp1, b_second_blocks
%undef mm_tmp0
%undef mm_tmp1
%undef mm_sad
%undef mm_sum
%undef mm_sqsum
%undef b_second_blocks
%endmacro

;*************************************************************************************************************
;void VAACalcSadVar_avx2( const uint8_t *cur_data, const uint8_t *ref_data, int32_t iPicWidth, int32_t iPicHeight
;               int32_t iPicStride, int32_t *psadframe, int32_t *psad8x8, int32_t *psum16x16, int32_t *psqsum16x16)
;*************************************************************************************************************

WELS_EXTERN VAACalcSadVar_avx2
%define          p_sadframe                    ptrword arg6
%define          p_sad8x8                      ptrword arg7
%define          p_sum16x16                    ptrword arg8
%define          p_sqsum16x16                  ptrword arg9
%ifdef X86_32
%define          saveregs                      r5, r6
%else
%define          saveregs                      rbx, rbp, r12, r13
%endif

%assign push_num 0
    LOAD_5_PARA
    PUSH_XMM 7
    SIGN_EXTENSION r2, r2d
    SIGN_EXTENSION r3, r3d
    SIGN_EXTENSION r4, r4d
    PUSHM          saveregs

%define mm_zero mm0
%define mm_sadframe mm6
    vpxor          x %+ mm_zero, x %+ mm_zero, x %+ mm_zero
    vmovdqa        y %+ mm_sadframe, y %+ mm_zero

    and            r2, -16                     ; iPicWidth &= -16
    jle            .done                       ; bail if iPicWidth < 16
    sar            r3, 4                       ; iPicHeight / 16
    jle            .done                       ; bail if iPicHeight < 16
    shr            r2, 2                       ; iPicWidth / 4

%define p_cur     r0
%define p_ref     r1
%define i_xcnt    r2
%define i_ycnt    ptrword arg4
%define i_stride  r4
%define r_tmp     r6
%define xcnt_unit 4
%ifdef X86_32
    mov            i_ycnt, r3
    mov            r3, p_sad8x8
    %undef  p_sad8x8
    %define p_sad8x8 r3
    %define i_stride3 r5
%else
    mov            rbp, p_sad8x8
    mov            r12, p_sum16x16
    mov            r13, p_sqsum16x16
    %undef  p_sad8x8
    %undef  p_sum16x16
    %undef  p_sqsum16x16
    %define p_sad8x8 rbp
    %define p_sum16x16 r12
    %define p_sqsum16x16 r13
    %define i_stride3 rbx
%endif
    lea            i_stride3, [3 * i_stride]

    ; offset pointers so as to compensate for the i_xcnt offset below.
    sub            p_sad8x8,      4 * 16 / xcnt_unit
    sub            p_sum16x16,    1 * 16 / xcnt_unit
    sub            p_sqsum16x16,  1 * 16 / xcnt_unit

    ; use a negative loop counter so as to enable counting toward zero and indexing with the same counter.
    neg            i_xcnt

.height_loop:
    push           i_xcnt
%assign push_num push_num + 1
%define i_xcnt_load ptrword [r7]
    ; use end-of-line pointers so as to enable use of a negative counter as index.
    lea            r_tmp, [xcnt_unit * i_xcnt]
    sub            p_sad8x8, r_tmp
    sub            p_sum16x16, i_xcnt
    sub            p_sqsum16x16, i_xcnt
    add            i_xcnt, 16 / xcnt_unit
    jz             .width_loop_upper8_remaining16
.width_loop_upper8:
    AVX2_CalcSadVar_8Lines y, mm1, mm2, mm3, mm4, mm5, 0
    add            i_xcnt, 32 / xcnt_unit
    jl             .width_loop_upper8
    jg             .width_loop_upper8_end
.width_loop_upper8_remaining16:
    AVX2_CalcSadVar_8Lines x, mm1, mm2, mm3, mm4, mm5, 0
.width_loop_upper8_end:
    lea            p_cur, [p_cur + 8 * i_stride]
    lea            p_ref, [p_ref + 8 * i_stride]
    mov            i_xcnt, i_xcnt_load
    lea            p_cur, [p_cur + xcnt_unit * i_xcnt]
    lea            p_ref, [p_ref + xcnt_unit * i_xcnt]
    add            i_xcnt, 16 / xcnt_unit
    jz             .width_loop_lower8_remaining16
.width_loop_lower8:
    AVX2_CalcSadVar_8Lines y, mm1, mm2, mm3, mm4, mm5, 1
    add            i_xcnt, 32 / xcnt_unit
    jl             .width_loop_lower8
    jg             .width_loop_lower8_end
.width_loop_lower8_remaining16:
    AVX2_CalcSadVar_8Lines x, mm1, mm2, mm3, mm4, mm5, 1
.width_loop_lower8_end:
    lea            p_cur, [p_cur + 8 * i_stride]
    lea            p_ref, [p_ref + 8 * i_stride]
%undef i_xcnt_load
    pop            i_xcnt
    %assign push_num push_num - 1
    lea            p_cur, [p_cur + xcnt_unit * i_xcnt]
    lea            p_ref, [p_ref + xcnt_unit * i_xcnt]
    sub            i_ycnt, 1
    jnz            .height_loop

.done:
    mov            r_tmp, p_sadframe
    vextracti128   xmm2, y %+ mm_sadframe, 1
    vpaddd         xmm2, x %+ mm_sadframe, xmm2
    vpunpckhqdq    xmm1, xmm2, xmm2
    vpaddd         xmm2, xmm2, xmm1
    vmovd          [r_tmp], xmm2
    vzeroupper

    POPM           saveregs
    POP_XMM
    LOAD_5_PARA_POP
%undef           p_cur
%undef           p_ref
%undef           i_xcnt
%undef           i_ycnt
%undef           i_stride
%undef           i_stride3
%undef           r_tmp
%undef           xcnt_unit
%undef           mm_sadframe
%undef           mm_zero
%undef           saveregs
%undef           p_sadframe
%undef           p_sad8x8
%undef           p_sum16x16
%undef           p_sqsum16x16
    ret


; x/y-mm_prefix=%1 mm_clobber=%2,%3,%4,%5,%6,%7,%8 b_second_blocks=%9
%macro AVX2_CalcSadSsd_8Lines 9
%define mm_tmp0    %2
%define mm_tmp1    %3
%define mm_tmp2    %4
%define mm_sad     %5
%define mm_sum     %6
%define mm_sqsum   %7
%define mm_sqdiff  %8
%define b_second_blocks %9
    ; Unroll for better performance on Haswell.
    ; Avoid unrolling for the 16 px case so as to reduce the code footprint.
%ifidni %1, y
%ifdef i_stride5
    lea            r_tmp, [i_stride + 2 * i_stride3]
    %define i_stride5_ i_stride5
%else
    lea            r_tmp, [5 * i_stride]
    %define i_stride5_ r_tmp
%endif
    AVX2_SadSumSqsumSqdiff %1 %+ mm_sad, %1 %+ mm_sum, %1 %+ mm_sqsum, %1 %+ mm_sqdiff, p_cur,                  p_ref,                  %1 %+ mm_zero, %1 %+ mm_tmp0, %1 %+ mm_tmp1, %1 %+ mm_tmp2, 0
    AVX2_SadSumSqsumSqdiff %1 %+ mm_sad, %1 %+ mm_sum, %1 %+ mm_sqsum, %1 %+ mm_sqdiff, p_cur + 1 * i_stride,   p_ref + 1 * i_stride,   %1 %+ mm_zero, %1 %+ mm_tmp0, %1 %+ mm_tmp1, %1 %+ mm_tmp2, 1
    AVX2_SadSumSqsumSqdiff %1 %+ mm_sad, %1 %+ mm_sum, %1 %+ mm_sqsum, %1 %+ mm_sqdiff, p_cur + 2 * i_stride,   p_ref + 2 * i_stride,   %1 %+ mm_zero, %1 %+ mm_tmp0, %1 %+ mm_tmp1, %1 %+ mm_tmp2, 1
    AVX2_SadSumSqsumSqdiff %1 %+ mm_sad, %1 %+ mm_sum, %1 %+ mm_sqsum, %1 %+ mm_sqdiff, p_cur + 1 * i_stride3,  p_ref + 1 * i_stride3,  %1 %+ mm_zero, %1 %+ mm_tmp0, %1 %+ mm_tmp1, %1 %+ mm_tmp2, 1
    AVX2_SadSumSqsumSqdiff %1 %+ mm_sad, %1 %+ mm_sum, %1 %+ mm_sqsum, %1 %+ mm_sqdiff, p_cur + 4 * i_stride,   p_ref + 4 * i_stride,   %1 %+ mm_zero, %1 %+ mm_tmp0, %1 %+ mm_tmp1, %1 %+ mm_tmp2, 1
    AVX2_SadSumSqsumSqdiff %1 %+ mm_sad, %1 %+ mm_sum, %1 %+ mm_sqsum, %1 %+ mm_sqdiff, p_cur + 1 * i_stride5_, p_ref + 1 * i_stride5_, %1 %+ mm_zero, %1 %+ mm_tmp0, %1 %+ mm_tmp1, %1 %+ mm_tmp2, 1
%ifndef i_stride5
    lea            r_tmp, [i_stride + 2 * i_stride3]
%endif
%undef i_stride5_
    AVX2_SadSumSqsumSqdiff %1 %+ mm_sad, %1 %+ mm_sum, %1 %+ mm_sqsum, %1 %+ mm_sqdiff, p_cur + 2 * i_stride3,  p_ref + 2 * i_stride3,  %1 %+ mm_zero, %1 %+ mm_tmp0, %1 %+ mm_tmp1, %1 %+ mm_tmp2, 1
    AVX2_SadSumSqsumSqdiff %1 %+ mm_sad, %1 %+ mm_sum, %1 %+ mm_sqsum, %1 %+ mm_sqdiff, p_cur + r_tmp,          p_ref + r_tmp,          %1 %+ mm_zero, %1 %+ mm_tmp0, %1 %+ mm_tmp1, %1 %+ mm_tmp2, 1
    ; Increment addresses for the next iteration. Doing this early is beneficial on Haswell.
    add            p_cur, %1 %+ mm_width
    add            p_ref, %1 %+ mm_width
%else
    vpxor          x %+ mm_sad, x %+ mm_sad, x %+ mm_sad
    vpxor          x %+ mm_sum, x %+ mm_sum, x %+ mm_sum
    vpxor          x %+ mm_sqsum, x %+ mm_sqsum, x %+ mm_sqsum
    vpxor          x %+ mm_sqdiff, x %+ mm_sqdiff, x %+ mm_sqdiff
    lea            r_tmp, [8 * i_stride]
    add            p_cur, r_tmp
    add            p_ref, r_tmp
    neg            r_tmp
%%loop:
    AVX2_SadSumSqsumSqdiff %1 %+ mm_sad, %1 %+ mm_sum, %1 %+ mm_sqsum, %1 %+ mm_sqdiff, p_cur + r_tmp, p_ref + r_tmp, %1 %+ mm_zero, %1 %+ mm_tmp0, %1 %+ mm_tmp1, %1 %+ mm_tmp2, 1
    add            r_tmp, i_stride
    jl             %%loop
    ; Increment addresses for the next iteration. Doing this early is beneficial on Haswell.
    lea            r_tmp, [8 * i_stride - %1 %+ mm_width]
    sub            p_cur, r_tmp
    sub            p_ref, r_tmp
%endif
    mov            r_tmp, p_sad8x8
    AVX2_Store8x8Accdw r_tmp + 4 * i_xcnt, %1, mm_sad, mm_tmp1, b_second_blocks
%ifdef X86_32
    vpaddd         y %+ mm_tmp1, y %+ mm_sad, sadframe_acc
    vmovdqa        sadframe_acc, y %+ mm_tmp1
%else
    vpaddd         sadframe_acc, sadframe_acc, y %+ mm_sad
%endif
    mov            r_tmp, i_xcnt
    add            r_tmp, p_sum16x16
    vpunpckhqdq    %1 %+ mm_tmp1, %1 %+ mm_sum, %1 %+ mm_sum
    vpaddd         %1 %+ mm_tmp0, %1 %+ mm_sum, %1 %+ mm_tmp1
    AVX2_Store16x16Accdw r_tmp, %1, mm_tmp0, mm_tmp1, b_second_blocks
    vpunpcklqdq    %1 %+ mm_tmp0, %1 %+ mm_sqsum, %1 %+ mm_sqdiff
    vpunpckhqdq    %1 %+ mm_tmp1, %1 %+ mm_sqsum, %1 %+ mm_sqdiff
    vpaddd         %1 %+ mm_tmp0, %1 %+ mm_tmp0, %1 %+ mm_tmp1
    vpshufd        %1 %+ mm_tmp1, %1 %+ mm_tmp0, 10110001b
    vpaddd         %1 %+ mm_tmp0, %1 %+ mm_tmp0, %1 %+ mm_tmp1
    AVX2_Store2x16x16Accdw p_sqsum16x16, p_sqdiff16x16, i_xcnt, r_tmp, %1, mm_tmp0, mm_tmp1, b_second_blocks
%undef mm_tmp0
%undef mm_tmp1
%undef mm_tmp2
%undef mm_sad
%undef mm_sum
%undef mm_sqsum
%undef mm_sqdiff
%undef b_second_blocks
%endmacro

;*************************************************************************************************************
;void VAACalcSadSsd_avx2(const uint8_t *cur_data, const uint8_t *ref_data, int32_t iPicWidth, int32_t iPicHeight,
;       int32_t iPicStride,int32_t *psadframe, int32_t *psad8x8, int32_t *psum16x16, int32_t *psqsum16x16, int32_t *psqdiff16x16)
;*************************************************************************************************************

WELS_EXTERN VAACalcSadSsd_avx2
%define          p_sadframe                    ptrword arg6
%define          p_sad8x8                      ptrword arg7
%define          p_sum16x16                    ptrword arg8
%define          p_sqsum16x16                  ptrword arg9
%define          p_sqdiff16x16                 ptrword arg10
%ifdef X86_32
%define          saveregs                      r5, r6
%else
%define          saveregs                      rbx, rbp, r12, r13, r14, r15
%endif

%assign push_num 0
    LOAD_5_PARA
    PUSH_XMM 9
    SIGN_EXTENSION r2, r2d
    SIGN_EXTENSION r3, r3d
    SIGN_EXTENSION r4, r4d
    PUSHM          saveregs

%define mm_zero mm0
    vpxor          x %+ mm_zero, x %+ mm_zero, x %+ mm_zero

%ifdef X86_32
    STACK_ALLOC    r5, ymm_width, ymm_width
    %define sadframe_acc_addr r5
    %define sadframe_acc [sadframe_acc_addr]
%else
    %define sadframe_acc ymm8
    %define xsadframe_acc xmm8
%endif
    vmovdqa        sadframe_acc, y %+ mm_zero

    and            r2, -16                     ; iPicWidth &= -16
    jle            .done                       ; bail if iPicWidth < 16
    sar            r3, 4                       ; iPicHeight / 16
    jle            .done                       ; bail if iPicHeight < 16
    shr            r2, 2                       ; iPicWidth / 4

%define p_cur     r0
%define p_ref     r1
%define i_xcnt    r2
%define i_ycnt    ptrword arg4
%define i_stride  r4
%define r_tmp     r6
%define xcnt_unit 4
%ifdef X86_32
    mov            i_ycnt, r3
    %define i_stride3 r3
%else
    mov            r12, p_sad8x8
    mov            r13, p_sum16x16
    mov            r14, p_sqsum16x16
    mov            r15, p_sqdiff16x16
    %undef  p_sad8x8
    %undef  p_sum16x16
    %undef  p_sqsum16x16
    %undef  p_sqdiff16x16
    %define p_sad8x8 r12
    %define p_sum16x16 r13
    %define p_sqsum16x16 r14
    %define p_sqdiff16x16 r15
    %define i_stride3 rbx
    %define i_stride5 rbp
    lea            i_stride5, [5 * i_stride]
%endif
    lea            i_stride3, [3 * i_stride]

    ; offset pointers so as to compensate for i_xcnt offset below.
    sub            p_sad8x8,      4 * 16 / xcnt_unit
    sub            p_sum16x16,    1 * 16 / xcnt_unit
    sub            p_sqsum16x16,  1 * 16 / xcnt_unit
    sub            p_sqdiff16x16, 1 * 16 / xcnt_unit

    ; use a negative loop counter so as to enable counting toward zero and indexing with the same counter.
    neg            i_xcnt

.height_loop:
    push           i_xcnt
%assign push_num push_num + 1
%define i_xcnt_load ptrword [r7]
    ; use end-of-line pointers so as to enable use of a negative counter as index.
    lea            r_tmp, [xcnt_unit * i_xcnt]
    sub            p_sad8x8, r_tmp
    sub            p_sum16x16, i_xcnt
    sub            p_sqsum16x16, i_xcnt
    sub            p_sqdiff16x16, i_xcnt
    add            i_xcnt, 16 / xcnt_unit
    jz             .width_loop_upper8_remaining16
.width_loop_upper8:
    AVX2_CalcSadSsd_8Lines y, mm1, mm2, mm3, mm4, mm5, mm6, mm7, 0
    add            i_xcnt, 32 / xcnt_unit
    jl             .width_loop_upper8
    jg             .width_loop_upper8_end
.width_loop_upper8_remaining16:
    AVX2_CalcSadSsd_8Lines x, mm1, mm2, mm3, mm4, mm5, mm6, mm7, 0
.width_loop_upper8_end:
    lea            p_cur, [p_cur + 8 * i_stride]
    lea            p_ref, [p_ref + 8 * i_stride]
    mov            i_xcnt, i_xcnt_load
    lea            p_cur, [p_cur + xcnt_unit * i_xcnt]
    lea            p_ref, [p_ref + xcnt_unit * i_xcnt]
    add            i_xcnt, 16 / xcnt_unit
    jz             .width_loop_lower8_remaining16
.width_loop_lower8:
    AVX2_CalcSadSsd_8Lines y, mm1, mm2, mm3, mm4, mm5, mm6, mm7, 1
    add            i_xcnt, 32 / xcnt_unit
    jl             .width_loop_lower8
    jg             .width_loop_lower8_end
.width_loop_lower8_remaining16:
    AVX2_CalcSadSsd_8Lines x, mm1, mm2, mm3, mm4, mm5, mm6, mm7, 1
.width_loop_lower8_end:
    lea            p_cur, [p_cur + 8 * i_stride]
    lea            p_ref, [p_ref + 8 * i_stride]
%undef i_xcnt_load
    pop            i_xcnt
    %assign push_num push_num - 1
    lea            p_cur, [p_cur + xcnt_unit * i_xcnt]
    lea            p_ref, [p_ref + xcnt_unit * i_xcnt]
    sub            i_ycnt, 1
    jnz            .height_loop

.done:
    mov            r_tmp, p_sadframe
%ifdef X86_32
    vmovdqa        xmm2, sadframe_acc
    vpaddd         xmm2, xmm2, [sadframe_acc_addr + xmm_width]
%else
    vextracti128   xmm2, sadframe_acc, 1
    vpaddd         xmm2, xsadframe_acc, xmm2
%endif
    vpunpckhqdq    xmm1, xmm2, xmm2
    vpaddd         xmm2, xmm2, xmm1
    vmovd          [r_tmp], xmm2
    vzeroupper
%ifdef X86_32
    STACK_DEALLOC
%endif
    POPM           saveregs
    POP_XMM
    LOAD_5_PARA_POP
%undef           p_cur
%undef           p_ref
%undef           i_xcnt
%undef           i_ycnt
%undef           i_stride
%undef           i_stride3
%undef           i_stride5
%undef           r_tmp
%undef           xcnt_unit
%undef           sadframe_acc
%undef           sadframe_acc_addr
%undef           xsadframe_acc
%undef           mm_zero
%undef           saveregs
%undef           p_sadframe
%undef           p_sad8x8
%undef           p_sum16x16
%undef           p_sqsum16x16
%undef           p_sqdiff16x16
    ret


; x/y-mm_prefix=%1 mm_clobber=%2,%3,%4,%5,%6,%7,%8 b_second_blocks=%9
%macro AVX2_CalcSadBgd_8Lines 9
%define mm_tmp0    %2
%define mm_tmp1    %3
%define mm_tmp2    %8
%define mm_mad     %4
%define mm_sumcur  %5
%define mm_sumref  %6
%define mm_sad     %7
%define b_second_blocks %9
    ; Unroll for better performance on Haswell.
    ; Avoid unrolling for the 16 px case so as to reduce the code footprint.
%ifidni %1, y
    lea            r_tmp, [5 * i_stride]
    AVX2_SadSdMad  %1 %+ mm_sad, %1 %+ mm_sumcur, %1 %+ mm_sumref, %1 %+ mm_mad, p_cur,                 p_ref,                 %1 %+ mm_zero, %1 %+ mm_tmp0, %1 %+ mm_tmp1, %1 %+ mm_tmp2, 0
    AVX2_SadSdMad  %1 %+ mm_sad, %1 %+ mm_sumcur, %1 %+ mm_sumref, %1 %+ mm_mad, p_cur + 1 * i_stride,  p_ref + 1 * i_stride,  %1 %+ mm_zero, %1 %+ mm_tmp0, %1 %+ mm_tmp1, %1 %+ mm_tmp2, 1
    AVX2_SadSdMad  %1 %+ mm_sad, %1 %+ mm_sumcur, %1 %+ mm_sumref, %1 %+ mm_mad, p_cur + 2 * i_stride,  p_ref + 2 * i_stride,  %1 %+ mm_zero, %1 %+ mm_tmp0, %1 %+ mm_tmp1, %1 %+ mm_tmp2, 1
    AVX2_SadSdMad  %1 %+ mm_sad, %1 %+ mm_sumcur, %1 %+ mm_sumref, %1 %+ mm_mad, p_cur + 1 * i_stride3, p_ref + 1 * i_stride3, %1 %+ mm_zero, %1 %+ mm_tmp0, %1 %+ mm_tmp1, %1 %+ mm_tmp2, 1
    AVX2_SadSdMad  %1 %+ mm_sad, %1 %+ mm_sumcur, %1 %+ mm_sumref, %1 %+ mm_mad, p_cur + 4 * i_stride,  p_ref + 4 * i_stride,  %1 %+ mm_zero, %1 %+ mm_tmp0, %1 %+ mm_tmp1, %1 %+ mm_tmp2, 1
    AVX2_SadSdMad  %1 %+ mm_sad, %1 %+ mm_sumcur, %1 %+ mm_sumref, %1 %+ mm_mad, p_cur + r_tmp,         p_ref + r_tmp,         %1 %+ mm_zero, %1 %+ mm_tmp0, %1 %+ mm_tmp1, %1 %+ mm_tmp2, 1
    lea            r_tmp, [i_stride + 2 * i_stride3]
    AVX2_SadSdMad  %1 %+ mm_sad, %1 %+ mm_sumcur, %1 %+ mm_sumref, %1 %+ mm_mad, p_cur + 2 * i_stride3, p_ref + 2 * i_stride3, %1 %+ mm_zero, %1 %+ mm_tmp0, %1 %+ mm_tmp1, %1 %+ mm_tmp2, 1
    AVX2_SadSdMad  %1 %+ mm_sad, %1 %+ mm_sumcur, %1 %+ mm_sumref, %1 %+ mm_mad, p_cur + r_tmp,         p_ref + r_tmp,         %1 %+ mm_zero, %1 %+ mm_tmp0, %1 %+ mm_tmp1, %1 %+ mm_tmp2, 1
    ; Increment addresses for the next iteration. Doing this early is beneficial on Haswell.
    add            p_cur, %1 %+ mm_width
    add            p_ref, %1 %+ mm_width
%else
    vpxor          x %+ mm_sad, x %+ mm_sad, x %+ mm_sad
    vpxor          x %+ mm_sumcur, x %+ mm_sumcur, x %+ mm_sumcur
    vpxor          x %+ mm_sumref, x %+ mm_sumref, x %+ mm_sumref
    vpxor          x %+ mm_mad, x %+ mm_mad, x %+ mm_mad
    lea            r_tmp, [8 * i_stride]
    add            p_cur, r_tmp
    add            p_ref, r_tmp
    neg            r_tmp
%%loop:
    AVX2_SadSdMad  %1 %+ mm_sad, %1 %+ mm_sumcur, %1 %+ mm_sumref, %1 %+ mm_mad, p_cur + r_tmp, p_ref + r_tmp, %1 %+ mm_zero, %1 %+ mm_tmp0, %1 %+ mm_tmp1, %1 %+ mm_tmp2, 1
    add            r_tmp, i_stride
    jl             %%loop
    ; Increment addresses for the next iteration. Doing this early is beneficial on Haswell.
    lea            r_tmp, [8 * i_stride - %1 %+ mm_width]
    sub            p_cur, r_tmp
    sub            p_ref, r_tmp
%endif
    mov            r_tmp, p_sad8x8
    AVX2_Store8x8Accdw r_tmp + 4 * i_xcnt, %1, mm_sad, mm_tmp1, b_second_blocks
%ifdef X86_32
    vpaddd         y %+ mm_tmp1, y %+ mm_sad, sadframe_acc
    vmovdqa        sadframe_acc, y %+ mm_tmp1
%else
    vpaddd         sadframe_acc, sadframe_acc, y %+ mm_sad
%endif
    mov            r_tmp, p_sd8x8
    vpsubd         %1 %+ mm_tmp0, %1 %+ mm_sumcur, %1 %+ mm_sumref
    AVX2_Store8x8Accdw r_tmp + 4 * i_xcnt, %1, mm_tmp0, mm_tmp1, b_second_blocks
    ; Coalesce store and horizontal reduction of MAD accumulator for even and
    ; odd iterations so as to enable more parallelism.
%ifidni %1, y
    test           i_xcnt, 32 / xcnt_unit
    jz             %%preserve_mad
    mov            r_tmp, p_mad8x8
    AVX2_Maxubq2   y %+ mm_mad, y %+ mm_mad, prev_mad, y %+ mm_tmp0
    AVX2_Store2x8x8Accb r_tmp + i_xcnt - 8, mm_mad, mm_tmp0, mm_tmp1, b_second_blocks
%%preserve_mad:
    vmovdqa        prev_mad, y %+ mm_mad
%else
    mov            r_tmp, p_mad8x8
    AVX2_Maxubq    %1 %+ mm_mad, %1 %+ mm_mad, %1 %+ mm_tmp0
    AVX2_Store8x8Accb r_tmp + i_xcnt, %1, mm_mad, mm_tmp0, b_second_blocks
%endif
%undef mm_tmp0
%undef mm_tmp1
%undef mm_tmp2
%undef mm_mad
%undef mm_sumcur
%undef mm_sumref
%undef mm_sad
%undef b_second_blocks
%endmacro

; Store remaining MAD accumulator for width & 32 cases.
; width/xcnt_unit=%1 mm_tmp=%2,%3 b_second_blocks=%4
%macro AVX2_StoreRemainingSingleMad 4
    test           %1, 32 / xcnt_unit
    jz             %%skip
    mov            r_tmp, p_mad8x8
    vmovdqa        y%2, prev_mad
    AVX2_Maxubq    y%2, y%2, y%3
    AVX2_Store8x8Accb r_tmp + i_xcnt - 8, y, %2, %3, %4
%%skip:
%endmacro

;*************************************************************************************************************
;void VAACalcSadBgd_avx2(const uint8_t *cur_data, const uint8_t *ref_data, int32_t iPicWidth, int32_t iPicHeight,
;                        int32_t iPicStride, int32_t *psadframe, int32_t *psad8x8, int32_t *p_sd8x8, uint8_t *p_mad8x8)
;*************************************************************************************************************

WELS_EXTERN VAACalcSadBgd_avx2
%define          p_sadframe                    arg6
%define          p_sad8x8                      arg7
%define          p_sd8x8                       arg8
%define          p_mad8x8                      arg9
%ifdef X86_32
%define          saveregs                      r5, r6
%else
%define          saveregs                      rbx, rbp, r12, r13
%endif

%assign push_num 0
    LOAD_5_PARA
    PUSH_XMM 10
    SIGN_EXTENSION r2, r2d
    SIGN_EXTENSION r3, r3d
    SIGN_EXTENSION r4, r4d
    PUSHM          saveregs

%define mm_zero mm0
    vpxor          x %+ mm_zero, x %+ mm_zero, x %+ mm_zero

%ifdef X86_32
    STACK_ALLOC    r5, 2 * ymm_width, ymm_width
    %define sadframe_acc_addr r5
    %define sadframe_acc [sadframe_acc_addr]
    %define prev_mad [r5 + ymm_width]
%else
    %define sadframe_acc ymm8
    %define xsadframe_acc xmm8
    %define prev_mad ymm9
%endif
    vmovdqa        sadframe_acc, y %+ mm_zero

    and            r2, -16                     ; iPicWidth &= -16
    jle            .done                       ; bail if iPicWidth < 16
    sar            r3, 4                       ; iPicHeight / 16
    jle            .done                       ; bail if iPicHeight < 16
    shr            r2, 2                       ; iPicWidth / 4

%define p_cur     r0
%define p_ref     r1
%define i_xcnt    r2
%define i_ycnt    ptrword arg4
%define i_stride  r4
%define r_tmp     r6
%define xcnt_unit 4
%ifdef X86_32
    mov            i_ycnt, r3
    %define i_stride3 r3
%else
    mov            rbp, p_sad8x8
    mov            r12, p_sd8x8
    mov            r13, p_mad8x8
    %undef  p_sad8x8
    %undef  p_sd8x8
    %undef  p_mad8x8
    %define p_sad8x8 rbp
    %define p_sd8x8 r12
    %define p_mad8x8 r13
    %define i_stride3 rbx
%endif
    lea            i_stride3, [3 * i_stride]

    ; offset pointers to compensate for the i_xcnt offset below.
    mov            r_tmp, i_xcnt
    and            r_tmp, 64 / xcnt_unit - 1
    sub            p_mad8x8, r_tmp
    shl            r_tmp, 2
    sub            p_sad8x8, r_tmp
    sub            p_sd8x8, r_tmp

.height_loop:
    push           i_xcnt
%assign push_num push_num + 1
%define i_xcnt_load ptrword [r7]
    ; use end-of-line pointers so as to enable use of a negative counter as index.
    lea            r_tmp, [xcnt_unit * i_xcnt]
    add            p_sad8x8, r_tmp
    add            p_sd8x8, r_tmp
    add            p_mad8x8, i_xcnt
    and            i_xcnt, -(64 / xcnt_unit)
    jz             .width_loop_upper8_64x_end
    ; use a negative loop counter to enable counting toward zero and indexing with the same counter.
    neg            i_xcnt
.width_loop_upper8:
    AVX2_CalcSadBgd_8Lines y, mm1, mm2, mm3, mm4, mm5, mm6, mm7, 0
    add            i_xcnt, 32 / xcnt_unit
    jl             .width_loop_upper8
    jg             .width_loop_upper8_32x_end
.width_loop_upper8_64x_end:
    test           i_xcnt_load, 32 / xcnt_unit
    jnz            .width_loop_upper8
.width_loop_upper8_32x_end:
    AVX2_StoreRemainingSingleMad i_xcnt_load, mm1, mm2, 0
    test           i_xcnt_load, 16 / xcnt_unit
    jz             .width_loop_upper8_end
    ; remaining 16.
    AVX2_CalcSadBgd_8Lines x, mm1, mm2, mm3, mm4, mm5, mm6, mm7, 0
.width_loop_upper8_end:
    lea            p_cur, [p_cur + 8 * i_stride]
    lea            p_ref, [p_ref + 8 * i_stride]
    mov            i_xcnt, i_xcnt_load
    lea            r_tmp, [xcnt_unit * i_xcnt]
    sub            p_cur, r_tmp
    sub            p_ref, r_tmp
    and            i_xcnt, -(64 / xcnt_unit)
    jz             .width_loop_lower8_64x_end
    neg            i_xcnt
.width_loop_lower8:
    AVX2_CalcSadBgd_8Lines y, mm1, mm2, mm3, mm4, mm5, mm6, mm7, 1
    add            i_xcnt, 32 / xcnt_unit
    jl             .width_loop_lower8
    jg             .width_loop_lower8_32x_end
.width_loop_lower8_64x_end:
    test           i_xcnt_load, 32 / xcnt_unit
    jnz            .width_loop_lower8
.width_loop_lower8_32x_end:
    AVX2_StoreRemainingSingleMad i_xcnt_load, mm1, mm2, 1
    test           i_xcnt_load, 16 / xcnt_unit
    jz             .width_loop_lower8_end
    ; remaining 16.
    AVX2_CalcSadBgd_8Lines x, mm1, mm2, mm3, mm4, mm5, mm6, mm7, 1
.width_loop_lower8_end:
    lea            p_cur, [p_cur + 8 * i_stride]
    lea            p_ref, [p_ref + 8 * i_stride]
    pop            i_xcnt
%undef i_xcnt_load
    %assign push_num push_num - 1
    lea            r_tmp, [xcnt_unit * i_xcnt]
    sub            p_cur, r_tmp
    sub            p_ref, r_tmp
    sub            i_ycnt, 1
    jnz            .height_loop

.done:
    mov            r_tmp, p_sadframe
%ifdef X86_32
    vmovdqa        xmm2, sadframe_acc
    vpaddd         xmm2, xmm2, [sadframe_acc_addr + xmm_width]
%else
    vextracti128   xmm2, sadframe_acc, 1
    vpaddd         xmm2, xsadframe_acc, xmm2
%endif
    vpunpckhqdq    xmm1, xmm2, xmm2
    vpaddd         xmm2, xmm2, xmm1
    vmovd          [r_tmp], xmm2
    vzeroupper
%ifdef X86_32
    STACK_DEALLOC
%endif
    POPM           saveregs
    POP_XMM
    LOAD_5_PARA_POP
%undef           p_cur
%undef           p_ref
%undef           i_xcnt
%undef           i_ycnt
%undef           i_stride
%undef           i_stride3
%undef           r_tmp
%undef           xcnt_unit
%undef           sadframe_acc
%undef           sadframe_acc_addr
%undef           xsadframe_acc
%undef           prev_mad
%undef           mm_zero
%undef           saveregs
%undef           p_sadframe
%undef           p_sad8x8
%undef           p_sd8x8
%undef           p_mad8x8
    ret


; x/y-mm_prefix=%1 mm_clobber=%2,%3,%4,%5,%6,%7,%8,%9,%10 b_second_blocks=%11
%macro AVX2_CalcSadSsdBgd_8Lines 11
%define mm_tmp0    %2
%define mm_tmp1    %3
%define mm_sad     %4
%define mm_sum     %5
%define mm_sumref  %6
%define mm_mad     %7
%define mm_sqsum   %8
%define mm_sqdiff  %9
%ifidn %10, 0
%define tmp2       0
%else
%define tmp2       %1 %+ %10
%endif
%define b_second_blocks %11
    ; Unroll for better performance on Haswell.
    ; Avoid unrolling for the 16 px case so as to reduce the code footprint.
%ifidni %1, y
    lea            r_tmp, [5 * i_stride]
    AVX2_SadBgdSqdiff %1 %+ mm_sad, %1 %+ mm_sum, %1 %+ mm_sumref, %1 %+ mm_mad, %1 %+ mm_sqdiff, %1 %+ mm_sqsum, p_cur,                 p_ref,                 %1 %+ mm_zero, %1 %+ mm_tmp0, %1 %+ mm_tmp1, tmp2, 0
    AVX2_SadBgdSqdiff %1 %+ mm_sad, %1 %+ mm_sum, %1 %+ mm_sumref, %1 %+ mm_mad, %1 %+ mm_sqdiff, %1 %+ mm_sqsum, p_cur + 1 * i_stride,  p_ref + 1 * i_stride,  %1 %+ mm_zero, %1 %+ mm_tmp0, %1 %+ mm_tmp1, tmp2, 1
    AVX2_SadBgdSqdiff %1 %+ mm_sad, %1 %+ mm_sum, %1 %+ mm_sumref, %1 %+ mm_mad, %1 %+ mm_sqdiff, %1 %+ mm_sqsum, p_cur + 2 * i_stride,  p_ref + 2 * i_stride,  %1 %+ mm_zero, %1 %+ mm_tmp0, %1 %+ mm_tmp1, tmp2, 1
    AVX2_SadBgdSqdiff %1 %+ mm_sad, %1 %+ mm_sum, %1 %+ mm_sumref, %1 %+ mm_mad, %1 %+ mm_sqdiff, %1 %+ mm_sqsum, p_cur + 1 * i_stride3, p_ref + 1 * i_stride3, %1 %+ mm_zero, %1 %+ mm_tmp0, %1 %+ mm_tmp1, tmp2, 1
    AVX2_SadBgdSqdiff %1 %+ mm_sad, %1 %+ mm_sum, %1 %+ mm_sumref, %1 %+ mm_mad, %1 %+ mm_sqdiff, %1 %+ mm_sqsum, p_cur + 4 * i_stride,  p_ref + 4 * i_stride,  %1 %+ mm_zero, %1 %+ mm_tmp0, %1 %+ mm_tmp1, tmp2, 1
    AVX2_SadBgdSqdiff %1 %+ mm_sad, %1 %+ mm_sum, %1 %+ mm_sumref, %1 %+ mm_mad, %1 %+ mm_sqdiff, %1 %+ mm_sqsum, p_cur + r_tmp,         p_ref + r_tmp,         %1 %+ mm_zero, %1 %+ mm_tmp0, %1 %+ mm_tmp1, tmp2, 1
    lea            r_tmp, [i_stride + 2 * i_stride3]
    AVX2_SadBgdSqdiff %1 %+ mm_sad, %1 %+ mm_sum, %1 %+ mm_sumref, %1 %+ mm_mad, %1 %+ mm_sqdiff, %1 %+ mm_sqsum, p_cur + 2 * i_stride3, p_ref + 2 * i_stride3, %1 %+ mm_zero, %1 %+ mm_tmp0, %1 %+ mm_tmp1, tmp2, 1
    AVX2_SadBgdSqdiff %1 %+ mm_sad, %1 %+ mm_sum, %1 %+ mm_sumref, %1 %+ mm_mad, %1 %+ mm_sqdiff, %1 %+ mm_sqsum, p_cur + r_tmp,         p_ref + r_tmp,         %1 %+ mm_zero, %1 %+ mm_tmp0, %1 %+ mm_tmp1, tmp2, 1
    ; Increment addresses for the next iteration. Doing this early is beneficial on Haswell.
    add            p_cur, %1 %+ mm_width
    add            p_ref, %1 %+ mm_width
%else
    vpxor          x %+ mm_sad, x %+ mm_sad, x %+ mm_sad
    vpxor          x %+ mm_sum, x %+ mm_sum, x %+ mm_sum
    vpxor          x %+ mm_sumref, x %+ mm_sumref, x %+ mm_sumref
    vpxor          x %+ mm_mad, x %+ mm_mad, x %+ mm_mad
    vpxor          x %+ mm_sqsum, x %+ mm_sqsum, x %+ mm_sqsum
    vpxor          x %+ mm_sqdiff, x %+ mm_sqdiff, x %+ mm_sqdiff
    lea            r_tmp, [8 * i_stride]
    add            p_cur, r_tmp
    add            p_ref, r_tmp
    neg            r_tmp
%%loop:
    AVX2_SadBgdSqdiff %1 %+ mm_sad, %1 %+ mm_sum, %1 %+ mm_sumref, %1 %+ mm_mad, %1 %+ mm_sqdiff, %1 %+ mm_sqsum, p_cur + r_tmp, p_ref + r_tmp, %1 %+ mm_zero, %1 %+ mm_tmp0, %1 %+ mm_tmp1, tmp2, 1
    add            r_tmp, i_stride
    jl             %%loop
    ; Increment addresses for the next iteration. Doing this early is beneficial on Haswell.
    lea            r_tmp, [8 * i_stride - %1 %+ mm_width]
    sub            p_cur, r_tmp
    sub            p_ref, r_tmp
%endif
    mov            r_tmp, p_sad8x8
    AVX2_Store8x8Accdw r_tmp + 4 * i_xcnt, %1, mm_sad, mm_tmp1, b_second_blocks
%ifdef X86_32
    vpaddd         y %+ mm_tmp1, y %+ mm_sad, sadframe_acc
    vmovdqa        sadframe_acc, y %+ mm_tmp1
%else
    vpaddd         sadframe_acc, sadframe_acc, y %+ mm_sad
%endif
    mov            r_tmp, i_xcnt
    add            r_tmp, p_sum16x16
    vpunpckhqdq    %1 %+ mm_tmp1, %1 %+ mm_sum, %1 %+ mm_sum
    vpaddd         %1 %+ mm_tmp0, %1 %+ mm_sum, %1 %+ mm_tmp1
    AVX2_Store16x16Accdw r_tmp, %1, mm_tmp0, mm_tmp1, b_second_blocks
    mov            r_tmp, p_sd8x8
    vpsubd         %1 %+ mm_sum,  %1 %+ mm_sum, %1 %+ mm_sumref
    AVX2_Store8x8Accdw r_tmp + 4 * i_xcnt, %1, mm_sum, mm_tmp0, b_second_blocks
    ; Coalesce store and horizontal reduction of MAD accumulator for even and
    ; odd iterations so as to enable more parallelism.
%ifidni %1, y
    test           i_xcnt, 32 / xcnt_unit
    jz             %%preserve_mad
    mov            r_tmp, p_mad8x8
    AVX2_Maxubq2   y %+ mm_mad, y %+ mm_mad, prev_mad, y %+ mm_tmp0
    AVX2_Store2x8x8Accb r_tmp + i_xcnt - 8, mm_mad, mm_tmp0, mm_tmp1, b_second_blocks
%%preserve_mad:
    vmovdqa        prev_mad, y %+ mm_mad
%else
    mov            r_tmp, p_mad8x8
    AVX2_Maxubq    %1 %+ mm_mad, %1 %+ mm_mad, %1 %+ mm_tmp0
    AVX2_Store8x8Accb r_tmp + i_xcnt, %1, mm_mad, mm_tmp0, b_second_blocks
%endif
    vpunpcklqdq    %1 %+ mm_tmp0, %1 %+ mm_sqsum, %1 %+ mm_sqdiff
    vpunpckhqdq    %1 %+ mm_tmp1, %1 %+ mm_sqsum, %1 %+ mm_sqdiff
    vpaddd         %1 %+ mm_tmp0, %1 %+ mm_tmp0,  %1 %+ mm_tmp1
    vpshufd        %1 %+ mm_tmp1, %1 %+ mm_tmp0,  10110001b
    vpaddd         %1 %+ mm_tmp0, %1 %+ mm_tmp0,  %1 %+ mm_tmp1
    AVX2_Store2x16x16Accdw p_sqsum16x16, p_sqdiff16x16, i_xcnt, r_tmp, %1, mm_tmp0, mm_tmp1, b_second_blocks
%undef mm_tmp0
%undef mm_tmp1
%undef mm_sqsum
%undef mm_sqdiff
%undef mm_mad
%undef mm_sum
%undef mm_sumref
%undef mm_sad
%undef tmp2
%undef b_second_blocks
%endmacro

;*************************************************************************************************************
;void VAACalcSadSsdBgd_avx2(const uint8_t *cur_data, const uint8_t *ref_data, int32_t iPicWidth, int32_t iPicHeight,
;                int32_t iPicStride, int32_t *psadframe, int32_t *psad8x8, int32_t *psum16x16, int32_t *psqsum16x16,
;                       int32_t *psqdiff16x16, int32_t *p_sd8x8, uint8_t *p_mad8x8)
;*************************************************************************************************************

WELS_EXTERN VAACalcSadSsdBgd_avx2
%define         p_sadframe                      arg6
%define         p_sad8x8                        arg7
%define         p_sum16x16                      arg8
%define         p_sqsum16x16                    arg9
%define         p_sqdiff16x16                   arg10
%define         p_sd8x8                         arg11
%define         p_mad8x8                        arg12
%ifdef X86_32
%define         saveregs                        r5, r6
%else
%define         saveregs                        rbx, rbp, r12, r13, r14, r15
%endif

%assign push_num 0
    LOAD_5_PARA
    PUSH_XMM 12
    SIGN_EXTENSION r2, r2d
    SIGN_EXTENSION r3, r3d
    SIGN_EXTENSION r4, r4d
    PUSHM          saveregs

%ifdef X86_32
    STACK_ALLOC    r5, 3 * ymm_width, ymm_width
    %define mm8 0
    %define sadframe_acc_addr r5
    %define sadframe_acc [sadframe_acc_addr]
    %define prev_mad [r5 + ymm_width]
    %define ymm_zero [r5 + 2 * ymm_width]
    %define xmm_zero ymm_zero
    vpxor          xmm0, xmm0, xmm0
    vmovdqa        sadframe_acc, ymm0
    vmovdqa        ymm_zero, ymm0
%else
    %define sadframe_acc ymm9
    %define xsadframe_acc xmm9
    %define prev_mad ymm10
    %define ymm_zero ymm11
    %define xmm_zero xmm11
    vpxor          xmm_zero, xmm_zero, xmm_zero
    vpxor          xsadframe_acc, xsadframe_acc, xsadframe_acc
%endif

    and            r2, -16                     ; iPicWidth &= -16
    jle            .done                       ; bail if iPicWidth < 16
    sar            r3, 4                       ; iPicHeight / 16
    jle            .done                       ; bail if iPicHeight < 16
    shr            r2, 2                       ; iPicWidth / 4

%define p_cur     r0
%define p_ref     r1
%define i_xcnt    r2
%define i_ycnt    ptrword arg4
%define i_stride  r4
%define r_tmp     r6
%define xcnt_unit 4
%ifdef X86_32
    mov            i_ycnt, r3
    %define i_stride3 r3
%else
    mov            rbp, p_sad8x8
    mov            r12, p_sum16x16
    mov            r13, p_sqsum16x16
    mov            r14, p_sqdiff16x16
    mov            r15, p_sd8x8
    %undef p_sad8x8
    %undef p_sum16x16
    %undef p_sqsum16x16
    %undef p_sqdiff16x16
    %undef p_sd8x8
    %define p_sad8x8 rbp
    %define p_sum16x16 r12
    %define p_sqsum16x16 r13
    %define p_sqdiff16x16 r14
    %define p_sd8x8 r15
    %define i_stride3 rbx
%endif
    lea            i_stride3, [3 * i_stride]

    ; offset pointers so as to compensate for the i_xcnt offset below.
    mov            r_tmp, i_xcnt
    and            r_tmp, 64 / xcnt_unit - 1
    sub            p_sum16x16, r_tmp
    sub            p_sqsum16x16, r_tmp
    sub            p_sqdiff16x16, r_tmp
    sub            p_mad8x8, r_tmp
    shl            r_tmp, 2
    sub            p_sad8x8, r_tmp
    sub            p_sd8x8, r_tmp

.height_loop:
    push           i_xcnt
%assign push_num push_num + 1
%define i_xcnt_load ptrword [r7]
    ; use end-of-line pointers so as to enable use of a negative counter as index.
    lea            r_tmp, [xcnt_unit * i_xcnt]
    add            p_sad8x8, r_tmp
    add            p_sum16x16, i_xcnt
    add            p_sqsum16x16, i_xcnt
    add            p_sqdiff16x16, i_xcnt
    add            p_sd8x8, r_tmp
    add            p_mad8x8, i_xcnt
    and            i_xcnt, -(64 / xcnt_unit)
    jz             .width_loop_upper8_64x_end
    ; use a negative loop counter to enable counting toward zero and indexing with the same counter.
    neg            i_xcnt
.width_loop_upper8:
    AVX2_CalcSadSsdBgd_8Lines y, mm0, mm1, mm2, mm3, mm4, mm5, mm6, mm7, mm8, 0
    add            i_xcnt, 32 / xcnt_unit
    jl             .width_loop_upper8
    jg             .width_loop_upper8_32x_end
.width_loop_upper8_64x_end:
    test           i_xcnt_load, 32 / xcnt_unit
    jnz            .width_loop_upper8
.width_loop_upper8_32x_end:
    AVX2_StoreRemainingSingleMad i_xcnt_load, mm1, mm2, 0
    test           i_xcnt_load, 16 / xcnt_unit
    jz             .width_loop_upper8_end
    ; remaining 16.
    AVX2_CalcSadSsdBgd_8Lines x, mm0, mm1, mm2, mm3, mm4, mm5, mm6, mm7, mm8, 0
.width_loop_upper8_end:
    lea            p_cur, [p_cur + 8 * i_stride]
    lea            p_ref, [p_ref + 8 * i_stride]
    mov            i_xcnt, i_xcnt_load
    lea            r_tmp, [xcnt_unit * i_xcnt]
    sub            p_cur, r_tmp
    sub            p_ref, r_tmp
    and            i_xcnt, -(64 / xcnt_unit)
    jz             .width_loop_lower8_64x_end
    neg            i_xcnt
.width_loop_lower8:
    AVX2_CalcSadSsdBgd_8Lines y, mm0, mm1, mm2, mm3, mm4, mm5, mm6, mm7, mm8, 1
    add            i_xcnt, 32 / xcnt_unit
    jl             .width_loop_lower8
    jg             .width_loop_lower8_32x_end
.width_loop_lower8_64x_end:
    test           i_xcnt_load, 32 / xcnt_unit
    jnz            .width_loop_lower8
.width_loop_lower8_32x_end:
    AVX2_StoreRemainingSingleMad i_xcnt_load, mm1, mm2, 1
    test           i_xcnt_load, 16 / xcnt_unit
    jz             .width_loop_lower8_end
    ; remaining 16.
    AVX2_CalcSadSsdBgd_8Lines x, mm0, mm1, mm2, mm3, mm4, mm5, mm6, mm7, mm8, 1
.width_loop_lower8_end:
    lea            p_cur, [p_cur + 8 * i_stride]
    lea            p_ref, [p_ref + 8 * i_stride]
    pop            i_xcnt
%undef i_xcnt_load
    %assign push_num push_num - 1
    lea            r_tmp, [xcnt_unit * i_xcnt]
    sub            p_cur, r_tmp
    sub            p_ref, r_tmp
    sub            i_ycnt, 1
    jnz            .height_loop

.done:
    mov            r_tmp, p_sadframe
%ifdef X86_32
    vmovdqa        xmm2, sadframe_acc
    vpaddd         xmm2, xmm2, [sadframe_acc_addr + xmm_width]
%else
    vextracti128   xmm2, sadframe_acc, 1
    vpaddd         xmm2, xsadframe_acc, xmm2
%endif
    vpunpckhqdq    xmm1, xmm2, xmm2
    vpaddd         xmm2, xmm2, xmm1
    vmovd          [r_tmp], xmm2
    vzeroupper
%ifdef X86_32
    STACK_DEALLOC
%endif
    POPM           saveregs
    POP_XMM
    LOAD_5_PARA_POP
%undef           p_cur
%undef           p_ref
%undef           i_xcnt
%undef           i_ycnt
%undef           i_stride
%undef           i_stride3
%undef           r_tmp
%undef           xcnt_unit
%undef           mm8
%undef           sadframe_acc
%undef           sadframe_acc_addr
%undef           xsadframe_acc
%undef           prev_mad
%undef           ymm_zero
%undef           xmm_zero
%undef           saveregs
%undef           p_sadframe
%undef           p_sad8x8
%undef           p_sum16x16
%undef           p_sqsum16x16
%undef           p_sqdiff16x16
%undef           p_sd8x8
%undef           p_mad8x8
    ret

%endif

