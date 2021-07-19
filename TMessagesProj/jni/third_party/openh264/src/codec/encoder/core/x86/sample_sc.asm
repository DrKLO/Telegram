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
;*************************************************************************/
%include "asm_inc.asm"

;***********************************************************************
; Local Data (Read Only)
;***********************************************************************
%ifdef X86_32_PICASM
SECTION .text align=16
%else
SECTION .rodata align=16
%endif

ALIGN 16
mv_x_inc_x4     dw  0x10, 0x10, 0x10, 0x10
mv_y_inc_x4     dw  0x04, 0x04, 0x04, 0x04
mx_x_offset_x4  dw  0x00, 0x04, 0x08, 0x0C

SECTION .text
%ifdef X86_32
;**********************************************************************************************************************
;void SumOf8x8BlockOfFrame_sse2(uint8_t* pRefPicture, const int32_t kiWidth, const int32_t kiHeight, const int32_t kiRefStride,
;                             uint16_t* pFeatureOfBlock, uint32_t pTimesOfFeatureValue[]);
;*********************************************************************************************************************
WELS_EXTERN SumOf8x8BlockOfFrame_sse2
%define     pushsize        16
%define     localsize       4
%define     ref             esp + pushsize + localsize + 4
%define     sum_ref         esp + pushsize + localsize + 20
%define     times_of_sum    esp + pushsize + localsize + 24
%define     width           esp + pushsize + localsize + 8
%define     height          esp + pushsize + localsize + 12
%define     linesize        esp + pushsize + localsize + 16
%define     tmp_width       esp + 0
    push    ebx
    push    ebp
    push    esi
    push    edi
    sub     esp,    localsize

    pxor    xmm0,   xmm0
    mov     esi,    [ref]
    mov     edi,    [sum_ref]
    mov     edx,    [times_of_sum]
    mov     ebx,    [linesize]
    mov     eax,    [width]
    lea     ecx,    [ebx+ebx*2] ; 3*linesize

    mov     [tmp_width],    eax
    lea     ebp,    [esi+ebx*4]
FIRST_ROW:
    movq    xmm1,   [esi]
    movq    xmm2,   [esi+ebx]
    movq    xmm3,   [esi+ebx*2]
    movq    xmm4,   [esi+ecx]

    shufps  xmm1,   xmm2,   01000100b
    shufps  xmm3,   xmm4,   01000100b
    psadbw  xmm1,   xmm0
    psadbw  xmm3,   xmm0
    paddd   xmm1,   xmm3

    movq    xmm2,   [ebp]
    movq    xmm3,   [ebp+ebx]
    movq    xmm4,   [ebp+ebx*2]
    movq    xmm5,   [ebp+ecx]

    shufps  xmm2,   xmm3,   01000100b
    shufps  xmm4,   xmm5,   01000100b
    psadbw  xmm2,   xmm0
    psadbw  xmm4,   xmm0
    paddd   xmm2,   xmm4

    paddd   xmm1,   xmm2
    pshufd  xmm2,   xmm1,   00001110b
    paddd   xmm1,   xmm2
    movd    eax,    xmm1
    mov     [edi],  ax
    inc     dword [edx+eax*4]

    inc     esi
    inc     ebp
    add     edi,    2

    dec     dword [tmp_width]
    jg      FIRST_ROW

    mov     esi,    [ref]
    mov     edi,    [sum_ref]
    mov     ebp,    [width]
    dec     dword [height]
HEIGHT_LOOP:
    mov     [tmp_width],    ebp
WIDTH_LOOP:
    movq    xmm1,   [esi+ebx*8]
    movq    xmm2,   [esi]
    psadbw  xmm1,   xmm0
    psadbw  xmm2,   xmm0
    psubd   xmm1,   xmm2
    movd    eax,    xmm1
    mov     cx,     [edi]
    add     eax,    ecx

    mov     [edi+ebp*2],    ax
    inc     dword [edx+eax*4]

    inc     esi
    add     edi,    2

    dec     dword [tmp_width]
    jg      WIDTH_LOOP

    add     esi,    ebx
    sub     esi,    ebp

    dec     dword [height]
    jg      HEIGHT_LOOP

    add     esp,    localsize
    pop     edi
    pop     esi
    pop     ebp
    pop     ebx
%undef      pushsize
%undef      localsize
%undef      ref
%undef      sum_ref
%undef      times_of_sum
%undef      width
%undef      height
%undef      linesize
%undef      tmp_width
    ret


%macro COUNT_SUM 3
%define xmm_reg %1
%define tmp_reg %2
    movd    tmp_reg,    xmm_reg
    inc     dword [edx+tmp_reg*4]
%if %3 == 1
    psrldq  xmm_reg,    4
%endif
%endmacro


;-----------------------------------------------------------------------------
; requires:  width % 8 == 0 && height > 1
;-----------------------------------------------------------------------------
;void SumOf8x8BlockOfFrame_sse4(uint8_t* pRefPicture, const int32_t kiWidth, const int32_t kiHeight, const int32_t kiRefStride,
;                             uint16_t* pFeatureOfBlock, uint32_t pTimesOfFeatureValue[]);
;-----------------------------------------------------------------------------
; read extra (16 - (width % 8) ) mod 16 bytes of every line
; write extra (16 - (width % 8)*2 ) mod 16 bytes in the end of sum_ref
WELS_EXTERN SumOf8x8BlockOfFrame_sse4
%define     pushsize        16
%define     localsize       4
%define     ref             esp + pushsize + localsize + 4
%define     sum_ref         esp + pushsize + localsize + 20
%define     times_of_sum    esp + pushsize + localsize + 24
%define     width           esp + pushsize + localsize + 8
%define     height          esp + pushsize + localsize + 12
%define     linesize        esp + pushsize + localsize + 16
%define     tmp_width       esp + 0
    push    ebx
    push    ebp
    push    esi
    push    edi
    sub     esp,    localsize

    pxor    xmm0,   xmm0
    mov     esi,    [ref]
    mov     edi,    [sum_ref]
    mov     edx,    [times_of_sum]
    mov     ebx,    [linesize]
    mov     eax,    [width]
    lea     ecx,    [ebx+ebx*2] ; 3*linesize

    mov     [tmp_width],    eax
    lea     ebp,    [esi+ebx*4]
FIRST_ROW_SSE4:
    movdqu  xmm1,   [esi]
    movdqu  xmm3,   [esi+ebx]
    movdqu  xmm5,   [esi+ebx*2]
    movdqu  xmm7,   [esi+ecx]

    movdqa  xmm2,   xmm1
    mpsadbw xmm1,   xmm0,   000b
    mpsadbw xmm2,   xmm0,   100b
    paddw   xmm1,   xmm2            ; 8 sums of line1

    movdqa  xmm4,   xmm3
    mpsadbw xmm3,   xmm0,   000b
    mpsadbw xmm4,   xmm0,   100b
    paddw   xmm3,   xmm4            ; 8 sums of line2

    movdqa  xmm2,   xmm5
    mpsadbw xmm5,   xmm0,   000b
    mpsadbw xmm2,   xmm0,   100b
    paddw   xmm5,   xmm2            ; 8 sums of line3

    movdqa  xmm4,   xmm7
    mpsadbw xmm7,   xmm0,   000b
    mpsadbw xmm4,   xmm0,   100b
    paddw   xmm7,   xmm4            ; 8 sums of line4

    paddw   xmm1,   xmm3
    paddw   xmm5,   xmm7
    paddw   xmm1,   xmm5            ; sum the upper 4 lines first

    movdqu  xmm2,   [ebp]
    movdqu  xmm3,   [ebp+ebx]
    movdqu  xmm4,   [ebp+ebx*2]
    movdqu  xmm5,   [ebp+ecx]

    movdqa  xmm6,   xmm2
    mpsadbw xmm2,   xmm0,   000b
    mpsadbw xmm6,   xmm0,   100b
    paddw   xmm2,   xmm6

    movdqa  xmm7,   xmm3
    mpsadbw xmm3,   xmm0,   000b
    mpsadbw xmm7,   xmm0,   100b
    paddw   xmm3,   xmm7

    movdqa  xmm6,   xmm4
    mpsadbw xmm4,   xmm0,   000b
    mpsadbw xmm6,   xmm0,   100b
    paddw   xmm4,   xmm6

    movdqa  xmm7,   xmm5
    mpsadbw xmm5,   xmm0,   000b
    mpsadbw xmm7,   xmm0,   100b
    paddw   xmm5,   xmm7

    paddw   xmm2,   xmm3
    paddw   xmm4,   xmm5
    paddw   xmm1,   xmm2
    paddw   xmm1,   xmm4            ; sum of lines 1- 8

    movdqu  [edi],  xmm1

    movdqa  xmm2,   xmm1
    punpcklwd   xmm1,   xmm0
    punpckhwd   xmm2,   xmm0

    COUNT_SUM   xmm1,   eax,    1
    COUNT_SUM   xmm1,   eax,    1
    COUNT_SUM   xmm1,   eax,    1
    COUNT_SUM   xmm1,   eax,    0
    COUNT_SUM   xmm2,   eax,    1
    COUNT_SUM   xmm2,   eax,    1
    COUNT_SUM   xmm2,   eax,    1
    COUNT_SUM   xmm2,   eax,    0

    lea     esi,    [esi+8]
    lea     ebp,    [ebp+8]
    lea     edi,    [edi+16]        ; element size is 2

    sub     dword [tmp_width], 8
    jg      near FIRST_ROW_SSE4

    mov     esi,    [ref]
    mov     edi,    [sum_ref]
    mov     ebp,    [width]
    dec     dword [height]
HEIGHT_LOOP_SSE4:
    mov     ecx,    ebp
WIDTH_LOOP_SSE4:
    movdqu  xmm1,   [esi+ebx*8]
    movdqu  xmm2,   [esi]
    movdqu  xmm7,   [edi]

    movdqa  xmm3,   xmm1
    mpsadbw xmm1,   xmm0,   000b
    mpsadbw xmm3,   xmm0,   100b
    paddw   xmm1,   xmm3

    movdqa  xmm4,   xmm2
    mpsadbw xmm2,   xmm0,   000b
    mpsadbw xmm4,   xmm0,   100b
    paddw   xmm2,   xmm4

    paddw   xmm7,   xmm1
    psubw   xmm7,   xmm2
    movdqu  [edi+ebp*2], xmm7

    movdqa  xmm6,   xmm7
    punpcklwd   xmm7,   xmm0
    punpckhwd   xmm6,   xmm0

    COUNT_SUM   xmm7,   eax,    1
    COUNT_SUM   xmm7,   eax,    1
    COUNT_SUM   xmm7,   eax,    1
    COUNT_SUM   xmm7,   eax,    0
    COUNT_SUM   xmm6,   eax,    1
    COUNT_SUM   xmm6,   eax,    1
    COUNT_SUM   xmm6,   eax,    1
    COUNT_SUM   xmm6,   eax,    0

    lea     esi,    [esi+8]
    lea     edi,    [edi+16]

    sub     ecx,    8
    jg      near WIDTH_LOOP_SSE4

    lea     esi,    [esi+ebx]
    sub     esi,    ebp

    dec     dword [height]
    jg      near HEIGHT_LOOP_SSE4

    add     esp,    localsize
    pop     edi
    pop     esi
    pop     ebp
    pop     ebx
%undef      pushsize
%undef      localsize
%undef      ref
%undef      sum_ref
%undef      times_of_sum
%undef      width
%undef      height
%undef      linesize
%undef      tmp_width
    ret


;****************************************************************************************************************************************************
;void SumOf16x16BlockOfFrame_sse2(uint8_t* pRefPicture, const int32_t kiWidth, const int32_t kiHeight, const int32_t kiRefStride,
;                             uint16_t* pFeatureOfBlock, uint32_t pTimesOfFeatureValue[]);
;****************************************************************************************************************************************************
WELS_EXTERN SumOf16x16BlockOfFrame_sse2
%define     pushsize        16
%define     localsize       4
%define     ref             esp + pushsize + localsize + 4
%define     sum_ref         esp + pushsize + localsize + 20
%define     times_of_sum    esp + pushsize + localsize + 24
%define     width           esp + pushsize + localsize + 8
%define     height          esp + pushsize + localsize + 12
%define     linesize        esp + pushsize + localsize + 16
%define     tmp_width       esp
    push    ebx
    push    ebp
    push    esi
    push    edi
    sub     esp,    localsize

    pxor    xmm0,   xmm0
    mov     esi,    [ref]
    mov     edi,    [sum_ref]
    mov     edx,    [times_of_sum]
    mov     ebx,    [linesize]
    mov     eax,    [width]

    lea     ecx,    [ebx+ebx*2]
    mov     [tmp_width],    eax
FIRST_ROW_X16H:
    movdqu  xmm1,   [esi]
    movdqu  xmm2,   [esi+ebx]
    movdqu  xmm3,   [esi+ebx*2]
    movdqu  xmm4,   [esi+ecx]

    psadbw  xmm1,   xmm0
    psadbw  xmm2,   xmm0
    psadbw  xmm3,   xmm0
    psadbw  xmm4,   xmm0
    paddw   xmm1,   xmm2
    paddw   xmm3,   xmm4
    paddw   xmm1,   xmm3

    lea     ebp,    [esi+ebx*4]
    movdqu  xmm2,   [ebp]
    movdqu  xmm3,   [ebp+ebx]
    movdqu  xmm4,   [ebp+ebx*2]
    movdqu  xmm5,   [ebp+ecx]

    psadbw  xmm2,   xmm0
    psadbw  xmm3,   xmm0
    psadbw  xmm4,   xmm0
    psadbw  xmm5,   xmm0
    paddw   xmm2,   xmm3
    paddw   xmm4,   xmm5
    paddw   xmm2,   xmm4

    paddw   xmm1,   xmm2

    lea     ebp,    [ebp+ebx*4]
    movdqu  xmm2,   [ebp]
    movdqu  xmm3,   [ebp+ebx]
    movdqu  xmm4,   [ebp+ebx*2]
    movdqu  xmm5,   [ebp+ecx]

    psadbw  xmm2,   xmm0
    psadbw  xmm3,   xmm0
    psadbw  xmm4,   xmm0
    psadbw  xmm5,   xmm0
    paddw   xmm2,   xmm3
    paddw   xmm4,   xmm5
    paddw   xmm2,   xmm4

    paddw   xmm1,   xmm2

    lea     ebp,    [ebp+ebx*4]
    movdqu  xmm2,   [ebp]
    movdqu  xmm3,   [ebp+ebx]
    movdqu  xmm4,   [ebp+ebx*2]
    movdqu  xmm5,   [ebp+ecx]

    psadbw  xmm2,   xmm0
    psadbw  xmm3,   xmm0
    psadbw  xmm4,   xmm0
    psadbw  xmm5,   xmm0
    paddw   xmm2,   xmm3
    paddw   xmm4,   xmm5
    paddw   xmm2,   xmm4

    paddw   xmm1,   xmm2
    movdqa  xmm2,   xmm1
    punpckhwd xmm2, xmm0
    paddw xmm1, xmm2
    movd    eax,    xmm1
    mov     [edi],  ax
    inc     dword [edx+eax*4]

    inc     esi
    lea     edi,    [edi+2]

    dec     dword [tmp_width]
    jg      near FIRST_ROW_X16H

    mov     esi,    [ref]
    mov     edi,    [sum_ref]
    mov     ebp,    [width]
    dec     dword [height]

    mov     ecx,    ebx
    sal     ecx,    4       ; succeeded 16th line
HEIGHT_LOOP_X16:
    mov     [tmp_width],    ebp
WIDTH_LOOP_X16:
    movdqu  xmm1,   [esi+ecx]
    movdqu  xmm2,   [esi]
    psadbw  xmm1,   xmm0
    psadbw  xmm2,   xmm0
    psubw   xmm1,   xmm2
    movdqa  xmm2,   xmm1
    punpckhwd xmm2, xmm0
    paddw   xmm1,   xmm2
    movd    eax,    xmm1
    add     ax, word [edi]
    mov     [edi+ebp*2],    ax
    inc     dword [edx+eax*4]

    inc     esi
    add     edi,    2

    dec     dword [tmp_width]
    jg      near WIDTH_LOOP_X16

    add     esi,    ebx
    sub     esi,    ebp

    dec     dword [height]
    jg      near HEIGHT_LOOP_X16

    add     esp,    localsize
    pop     edi
    pop     esi
    pop     ebp
    pop     ebx
%undef      pushsize
%undef      localsize
%undef      ref
%undef      sum_ref
%undef      times_of_sum
%undef      width
%undef      height
%undef      linesize
%undef      tmp_width
    ret

; requires:  width % 16 == 0 && height > 1
;-----------------------------------------------------------------------------------------------------------------------------
;void SumOf16x16BlockOfFrame_sse4(uint8_t* pRefPicture, const int32_t kiWidth, const int32_t kiHeight, const int32_t kiRefStride,
;                             uint16_t* pFeatureOfBlock, uint32_t pTimesOfFeatureValue[]);
;-----------------------------------------------------------------------------------------------------------------------------
; try 8 mv via offset
%macro SUM_LINE_X16_SSE41  5    ; ref, dst0, dst1, tmp0, tmp1
    movdqu  %2, [%1]
    movdqu  %3, [%1+8h]
    movdqa  %4, %2
    movdqa  %5, %3

    mpsadbw %2, xmm0,   0   ; 000 B
    mpsadbw %4, xmm0,   5   ; 101 B
    mpsadbw %3, xmm0,   2   ; 010 B
    mpsadbw %5, xmm0,   7   ; 111 B
    paddw   %2, %4
    paddw   %3, %5
    paddw   %2, %3  ; accumulate cost
%endmacro   ; end of SAD_16x16_LINE_SSE41

WELS_EXTERN SumOf16x16BlockOfFrame_sse4
%define     pushsize        16
%define     localsize       4
%define     ref             esp + pushsize + localsize + 4
%define     sum_ref         esp + pushsize + localsize + 20
%define     times_of_sum    esp + pushsize + localsize + 24
%define     width           esp + pushsize + localsize + 8
%define     height          esp + pushsize + localsize + 12
%define     linesize        esp + pushsize + localsize + 16
%define     tmp_width       esp
    push    ebx
    push    ebp
    push    esi
    push    edi
    sub     esp,    localsize

    pxor    xmm0,   xmm0
    mov     esi,    [ref]
    mov     edi,    [sum_ref]
    mov     edx,    [times_of_sum]
    mov     ebx,    [linesize]
    mov     eax,    [width]

    lea     ecx,    [ebx+ebx*2]
    mov     [tmp_width],    eax
FIRST_ROW_X16_SSE4:
    SUM_LINE_X16_SSE41  esi,        xmm1, xmm2, xmm3, xmm4
    SUM_LINE_X16_SSE41  esi+ebx,    xmm2, xmm3, xmm4, xmm5
    SUM_LINE_X16_SSE41  esi+ebx*2,  xmm3, xmm4, xmm5, xmm6
    SUM_LINE_X16_SSE41  esi+ecx,    xmm4, xmm5, xmm6, xmm7
    paddw   xmm1, xmm2
    paddw   xmm3, xmm4
    paddw   xmm1, xmm3

    lea     ebp,    [esi+ebx*4]
    SUM_LINE_X16_SSE41  ebp,        xmm2, xmm3, xmm4, xmm5
    paddw   xmm1, xmm2
    SUM_LINE_X16_SSE41  ebp+ebx,    xmm2, xmm3, xmm4, xmm5
    paddw   xmm1, xmm2
    SUM_LINE_X16_SSE41  ebp+ebx*2,  xmm2, xmm3, xmm4, xmm5
    paddw   xmm1, xmm2
    SUM_LINE_X16_SSE41  ebp+ecx,    xmm2, xmm3, xmm4, xmm5
    paddw   xmm1, xmm2

    lea     ebp,    [ebp+ebx*4]
    SUM_LINE_X16_SSE41  ebp,        xmm2, xmm3, xmm4, xmm5
    paddw   xmm1, xmm2
    SUM_LINE_X16_SSE41  ebp+ebx,    xmm2, xmm3, xmm4, xmm5
    paddw   xmm1, xmm2
    SUM_LINE_X16_SSE41  ebp+ebx*2,  xmm2, xmm3, xmm4, xmm5
    paddw   xmm1, xmm2
    SUM_LINE_X16_SSE41  ebp+ecx,    xmm2, xmm3, xmm4, xmm5
    paddw   xmm1, xmm2

    lea     ebp,    [ebp+ebx*4]
    SUM_LINE_X16_SSE41  ebp,        xmm2, xmm3, xmm4, xmm5
    paddw   xmm1, xmm2
    SUM_LINE_X16_SSE41  ebp+ebx,    xmm2, xmm3, xmm4, xmm5
    paddw   xmm1, xmm2
    SUM_LINE_X16_SSE41  ebp+ebx*2,  xmm2, xmm3, xmm4, xmm5
    paddw   xmm1, xmm2
    SUM_LINE_X16_SSE41  ebp+ecx,    xmm2, xmm3, xmm4, xmm5
    paddw   xmm1, xmm2

    movdqa  [edi],  xmm1
    movdqa  xmm2,   xmm1
    punpcklwd   xmm1,   xmm0
    punpckhwd   xmm2,   xmm0

    COUNT_SUM   xmm1,   eax,    1
    COUNT_SUM   xmm1,   eax,    1
    COUNT_SUM   xmm1,   eax,    1
    COUNT_SUM   xmm1,   eax,    0
    COUNT_SUM   xmm2,   eax,    1
    COUNT_SUM   xmm2,   eax,    1
    COUNT_SUM   xmm2,   eax,    1
    COUNT_SUM   xmm2,   eax,    0

    lea     esi,    [esi+8]
    lea     edi,    [edi+16]    ; element size is 2

    sub     dword [tmp_width], 8
    jg      near FIRST_ROW_X16_SSE4

    mov     esi,    [ref]
    mov     edi,    [sum_ref]
    mov     ebp,    [width]
    dec     dword [height]

    mov     ecx,    ebx
    sal     ecx,    4       ; succeeded 16th line

HEIGHT_LOOP_X16_SSE4:
    mov     [tmp_width],    ebp
WIDTH_LOOP_X16_SSE4:
    movdqa  xmm7,   [edi]
    SUM_LINE_X16_SSE41  esi+ecx, xmm1, xmm2, xmm3, xmm4
    SUM_LINE_X16_SSE41  esi, xmm2, xmm3, xmm4, xmm5

    paddw   xmm7,   xmm1
    psubw   xmm7,   xmm2
    movdqa  [edi+ebp*2], xmm7

    movdqa  xmm6,   xmm7
    punpcklwd   xmm7,   xmm0
    punpckhwd   xmm6,   xmm0

    COUNT_SUM   xmm7,   eax,    1
    COUNT_SUM   xmm7,   eax,    1
    COUNT_SUM   xmm7,   eax,    1
    COUNT_SUM   xmm7,   eax,    0
    COUNT_SUM   xmm6,   eax,    1
    COUNT_SUM   xmm6,   eax,    1
    COUNT_SUM   xmm6,   eax,    1
    COUNT_SUM   xmm6,   eax,    0

    lea     esi,    [esi+8]
    lea     edi,    [edi+16]

    sub     dword [tmp_width], 8
    jg      near WIDTH_LOOP_X16_SSE4

    add     esi,    ebx
    sub     esi,    ebp

    dec     dword [height]
    jg      near HEIGHT_LOOP_X16_SSE4

    add     esp,    localsize
    pop     edi
    pop     esi
    pop     ebp
    pop     ebx
%undef      pushsize
%undef      localsize
%undef      ref
%undef      sum_ref
%undef      times_of_sum
%undef      width
%undef      height
%undef      linesize
%undef      tmp_width
    ret


;-----------------------------------------------------------------------------------------------------------------------------
; void FillQpelLocationByFeatureValue_sse2(uint16_t* pFeatureOfBlock, const int32_t kiWidth, const int32_t kiHeight, uint16_t** pFeatureValuePointerList)
;-----------------------------------------------------------------------------------------------------------------------------
WELS_EXTERN FillQpelLocationByFeatureValue_sse2
    push    esi
    push    edi
    push    ebx
    push    ebp

    %define _ps         16              ; push size
    %define _ls         4               ; local size
    %define sum_ref     esp+_ps+_ls+4
    %define pos_list    esp+_ps+_ls+16
    %define width       esp+_ps+_ls+8
    %define height      esp+_ps+_ls+12
    %define i_height    esp
    sub     esp,    _ls

    mov     esi,    [sum_ref]
    mov     edi,    [pos_list]
    mov     ebp,    [width]
    mov     ebx,    [height]
    mov     [i_height], ebx

    %assign push_num 5
    INIT_X86_32_PIC_NOPRESERVE ecx
    movq    xmm7,   [pic(mv_x_inc_x4)]     ; x_qpel inc
    movq    xmm6,   [pic(mv_y_inc_x4)]     ; y_qpel inc
    movq    xmm5,   [pic(mx_x_offset_x4)]  ; x_qpel vector
    DEINIT_X86_32_PIC
    pxor    xmm4,   xmm4
    pxor    xmm3,   xmm3                ; y_qpel vector
HASH_HEIGHT_LOOP_SSE2:
    movdqa  xmm2,   xmm5    ; x_qpel vector
    mov     ecx,    ebp
HASH_WIDTH_LOOP_SSE2:
    movq    xmm0,   [esi]           ; load x8 sum
    punpcklwd   xmm0,   xmm4
    movdqa      xmm1,   xmm2
    punpcklwd   xmm1,   xmm3
%rep    3
    movd    edx,    xmm0
    lea     ebx,    [edi+edx*4]
    mov     eax,    [ebx]
    movd    [eax],  xmm1
    mov     edx,    [eax+4] ; explictly load eax+4 due cache miss from vtune observation
    lea     eax,    [eax+4]
    mov     [ebx],  eax
    psrldq  xmm1,   4
    psrldq  xmm0,   4
%endrep
    movd    edx,    xmm0
    lea     ebx,    [edi+edx*4]
    mov     eax,    [ebx]
    movd    [eax],  xmm1
    mov     edx,    [eax+4] ; explictly load eax+4 due cache miss from vtune observation
    lea     eax,    [eax+4]
    mov     [ebx],  eax

    paddw   xmm2,   xmm7
    lea     esi,    [esi+8]
    sub     ecx,    4
    jnz near HASH_WIDTH_LOOP_SSE2
    paddw   xmm3,   xmm6
    dec dword [i_height]
    jnz near HASH_HEIGHT_LOOP_SSE2

    add     esp,    _ls
    %undef  _ps
    %undef  _ls
    %undef  sum_ref
    %undef  pos_list
    %undef  width
    %undef  height
    %undef  i_height
    pop     ebp
    pop     ebx
    pop     edi
    pop     esi
    ret

;---------------------------------------------------------------------------------------------------------------------------------------------------
; void InitializeHashforFeature_sse2( uint32_t* pTimesOfFeatureValue, uint16_t* pBuf, const int32_t kiListSize,
;                        uint16_t** pLocationOfFeature, uint16_t** pFeatureValuePointerList )
;---------------------------------------------------------------------------------------------------------------------------------------------------
WELS_EXTERN InitializeHashforFeature_sse2
    push    ebx
    push    esi
    push    edi
    push    ebp
    %define _ps 16  ; push size
    mov     edi,    [esp+_ps+16]    ; pPositionOfSum
    mov     ebp,    [esp+_ps+20]    ; sum_idx_list
    mov     esi,    [esp+_ps+4]     ; pTimesOfSum
    mov     ebx,    [esp+_ps+8]     ; pBuf
    mov     edx,    [esp+_ps+12]    ; list_sz
    sar     edx,    2
    mov     ecx,    0
    pxor    xmm7,   xmm7
hash_assign_loop_x4_sse2:
    movdqa  xmm0,   [esi+ecx]
    pslld   xmm0,   2

    movdqa  xmm1,   xmm0
    pcmpeqd xmm1,   xmm7
    movmskps    eax,    xmm1
    cmp eax, 0x0f
    je  near hash_assign_with_copy_sse2

%assign x   0
%rep 4
    lea     eax,    [edi+ecx+x]
    mov     [eax],  ebx
    lea     eax,    [ebp+ecx+x]
    mov     [eax],  ebx
    movd    eax,    xmm0
    add     ebx,    eax
    psrldq  xmm0,   4
%assign x   x+4
%endrep
    jmp near assign_next_sse2

hash_assign_with_copy_sse2:
    movd    xmm1,   ebx
    pshufd  xmm2,   xmm1,   0
    movdqa  [edi+ecx], xmm2
    movdqa  [ebp+ecx], xmm2

assign_next_sse2:
    add     ecx,    16
    dec     edx
    jnz     near hash_assign_loop_x4_sse2

    mov     edx,    [esp+_ps+12]    ; list_sz
    and     edx,    3
    jz      near hash_assign_no_rem_sse2
hash_assign_loop_x4_rem_sse2:
    lea     eax,    [edi+ecx]
    mov     [eax],  ebx
    lea     eax,    [ebp+ecx]
    mov     [eax],  ebx
    mov     eax,    [esi+ecx]
    sal     eax,    2
    add     ebx,    eax
    add     ecx,    4
    dec     edx
    jnz     near hash_assign_loop_x4_rem_sse2

hash_assign_no_rem_sse2:
    %undef  _ps
    pop     ebp
    pop     edi
    pop     esi
    pop     ebx
    ret
%else

;**********************************************************************************************************************
;void SumOf8x8BlockOfFrame_sse2(uint8_t* pRefPicture, const int32_t kiWidth, const int32_t kiHeight, const int32_t kiRefStride,
;                             uint16_t* pFeatureOfBlock, uint32_t pTimesOfFeatureValue[]);
;*********************************************************************************************************************
WELS_EXTERN SumOf8x8BlockOfFrame_sse2
    %assign  push_num 0
    LOAD_6_PARA
    PUSH_XMM 6
    SIGN_EXTENSION  r1, r1d
    SIGN_EXTENSION  r2, r2d
    SIGN_EXTENSION  r3, r3d
    push r12
    push r13
    push r0
    push r2
    push r4

    pxor    xmm0,   xmm0
    lea     r6, [r3+r3*2]

    mov     r12,    r1              ;r12:tmp_width
    lea     r13,    [r0+r3*4]       ;rbp:r13
FIRST_ROW:
    movq    xmm1,   [r0]
    movq    xmm2,   [r0+r3]
    movq    xmm3,   [r0+r3*2]
    movq    xmm4,   [r0+r6]

    shufps  xmm1,   xmm2,   01000100b
    shufps  xmm3,   xmm4,   01000100b
    psadbw  xmm1,   xmm0
    psadbw  xmm3,   xmm0
    paddd   xmm1,   xmm3

    movq    xmm2,   [r13]
    movq    xmm3,   [r13+r3]
    movq    xmm4,   [r13+r3*2]
    movq    xmm5,   [r13+r6]

    shufps  xmm2,   xmm3,   01000100b
    shufps  xmm4,   xmm5,   01000100b
    psadbw  xmm2,   xmm0
    psadbw  xmm4,   xmm0
    paddd   xmm2,   xmm4

    paddd   xmm1,   xmm2
    pshufd  xmm2,   xmm1,   00001110b
    paddd   xmm1,   xmm2
    movd    r2d,    xmm1
    mov     [r4],   r2w
    inc     dword [r5+r2*4]

    inc     r0
    inc     r13
    add     r4, 2

    dec     r12
    jg      FIRST_ROW

    pop r4
    pop r2
    pop r0
    mov r13, r2
    dec r13
HEIGHT_LOOP:
    mov     r12,    r1
WIDTH_LOOP:
    movq    xmm1,   [r0+r3*8]
    movq    xmm2,   [r0]
    psadbw  xmm1,   xmm0
    psadbw  xmm2,   xmm0
    psubd   xmm1,   xmm2
    movd    r2d,    xmm1
    mov     r6w,    [r4]
    add     r2d,    r6d
    mov     [r4+r1*2],  r2w
    inc     dword [r5+r2*4]

    inc     r0
    add     r4, 2

    dec     r12
    jg      WIDTH_LOOP

    add     r0, r3
    sub     r0, r1


    dec     r13
    jg      HEIGHT_LOOP

    pop     r13
    pop     r12
    POP_XMM
    LOAD_6_PARA_POP
    ret


%macro COUNT_SUM 4
%define xmm_reg %1
%define tmp_dreg %2
%define tmp_qreg %3
    movd    tmp_dreg,   xmm_reg
    inc     dword [r5+tmp_qreg*4]
%if %4 == 1
    psrldq  xmm_reg,    4
%endif
%endmacro


;-----------------------------------------------------------------------------
; requires:  width % 8 == 0 && height > 1
;-----------------------------------------------------------------------------
;void SumOf8x8BlockOfFrame_sse4(uint8_t* pRefPicture, const int32_t kiWidth, const int32_t kiHeight, const int32_t kiRefStride,
;                             uint16_t* pFeatureOfBlock, uint32_t pTimesOfFeatureValue[]);
;-----------------------------------------------------------------------------
; read extra (16 - (width % 8) ) mod 16 bytes of every line
; write extra (16 - (width % 8)*2 ) mod 16 bytes in the end of sum_ref
WELS_EXTERN SumOf8x8BlockOfFrame_sse4
    %assign  push_num 0
    LOAD_6_PARA
    PUSH_XMM 8
    SIGN_EXTENSION  r1, r1d
    SIGN_EXTENSION  r2, r2d
    SIGN_EXTENSION  r3, r3d
    push r12
    push r13
    push r0
    push r2
    push r4

    pxor    xmm0,   xmm0
    lea     r6, [r3+r3*2]

    mov     r12,    r1              ;r12:tmp_width
    lea     r13,    [r0+r3*4]       ;rbp:r13
FIRST_ROW_SSE4:
    movdqu  xmm1,   [r0]
    movdqu  xmm3,   [r0+r3]
    movdqu  xmm5,   [r0+r3*2]
    movdqu  xmm7,   [r0+r6]

    movdqa  xmm2,   xmm1
    mpsadbw xmm1,   xmm0,   000b
    mpsadbw xmm2,   xmm0,   100b
    paddw   xmm1,   xmm2            ; 8 sums of line1

    movdqa  xmm4,   xmm3
    mpsadbw xmm3,   xmm0,   000b
    mpsadbw xmm4,   xmm0,   100b
    paddw   xmm3,   xmm4            ; 8 sums of line2

    movdqa  xmm2,   xmm5
    mpsadbw xmm5,   xmm0,   000b
    mpsadbw xmm2,   xmm0,   100b
    paddw   xmm5,   xmm2            ; 8 sums of line3

    movdqa  xmm4,   xmm7
    mpsadbw xmm7,   xmm0,   000b
    mpsadbw xmm4,   xmm0,   100b
    paddw   xmm7,   xmm4            ; 8 sums of line4

    paddw   xmm1,   xmm3
    paddw   xmm5,   xmm7
    paddw   xmm1,   xmm5            ; sum the upper 4 lines first

    movdqu  xmm2,   [r13]
    movdqu  xmm3,   [r13+r3]
    movdqu  xmm4,   [r13+r3*2]
    movdqu  xmm5,   [r13+r6]

    movdqa  xmm6,   xmm2
    mpsadbw xmm2,   xmm0,   000b
    mpsadbw xmm6,   xmm0,   100b
    paddw   xmm2,   xmm6

    movdqa  xmm7,   xmm3
    mpsadbw xmm3,   xmm0,   000b
    mpsadbw xmm7,   xmm0,   100b
    paddw   xmm3,   xmm7

    movdqa  xmm6,   xmm4
    mpsadbw xmm4,   xmm0,   000b
    mpsadbw xmm6,   xmm0,   100b
    paddw   xmm4,   xmm6

    movdqa  xmm7,   xmm5
    mpsadbw xmm5,   xmm0,   000b
    mpsadbw xmm7,   xmm0,   100b
    paddw   xmm5,   xmm7

    paddw   xmm2,   xmm3
    paddw   xmm4,   xmm5
    paddw   xmm1,   xmm2
    paddw   xmm1,   xmm4            ; sum of lines 1- 8

    movdqu  [r4],   xmm1

    movdqa  xmm2,   xmm1
    punpcklwd   xmm1,   xmm0
    punpckhwd   xmm2,   xmm0

    COUNT_SUM   xmm1,   r2d, r2, 1
    COUNT_SUM   xmm1,   r2d, r2, 1
    COUNT_SUM   xmm1,   r2d, r2, 1
    COUNT_SUM   xmm1,   r2d, r2, 0
    COUNT_SUM   xmm2,   r2d, r2 ,1
    COUNT_SUM   xmm2,   r2d, r2 ,1
    COUNT_SUM   xmm2,   r2d, r2 ,1
    COUNT_SUM   xmm2,   r2d, r2 ,0

    lea     r0,     [r0+8]
    lea     r13,    [r13+8]
    lea     r4,     [r4+16]     ; element size is 2

    sub     r12, 8
    jg      near FIRST_ROW_SSE4

    pop r4
    pop r2
    pop r0
    mov r13, r2
    dec r13
HEIGHT_LOOP_SSE4:
    mov     r12,    r1
WIDTH_LOOP_SSE4:
    movdqu  xmm1,   [r0+r3*8]
    movdqu  xmm2,   [r0]
    movdqu  xmm7,   [r4]

    movdqa  xmm3,   xmm1
    mpsadbw xmm1,   xmm0,   000b
    mpsadbw xmm3,   xmm0,   100b
    paddw   xmm1,   xmm3

    movdqa  xmm4,   xmm2
    mpsadbw xmm2,   xmm0,   000b
    mpsadbw xmm4,   xmm0,   100b
    paddw   xmm2,   xmm4

    paddw   xmm7,   xmm1
    psubw   xmm7,   xmm2
    movdqu  [r4+r1*2], xmm7

    movdqa  xmm6,   xmm7
    punpcklwd   xmm7,   xmm0
    punpckhwd   xmm6,   xmm0

    COUNT_SUM   xmm7,   r2d, r2, 1
    COUNT_SUM   xmm7,   r2d, r2, 1
    COUNT_SUM   xmm7,   r2d, r2, 1
    COUNT_SUM   xmm7,   r2d, r2, 0
    COUNT_SUM   xmm6,   r2d, r2, 1
    COUNT_SUM   xmm6,   r2d, r2, 1
    COUNT_SUM   xmm6,   r2d, r2, 1
    COUNT_SUM   xmm6,   r2d, r2, 0

    lea     r0, [r0+8]
    lea     r4, [r4+16]

    sub     r12,    8
    jg      near WIDTH_LOOP_SSE4

    lea     r0, [r0+r3]
    sub     r0, r1

    dec     r13
    jg      near HEIGHT_LOOP_SSE4

    pop     r13
    pop     r12
    POP_XMM
    LOAD_6_PARA_POP
    ret


;****************************************************************************************************************************************************
;void SumOf16x16BlockOfFrame_sse2(uint8_t* pRefPicture, const int32_t kiWidth, const int32_t kiHeight, const int32_t kiRefStride,
;                             uint16_t* pFeatureOfBlock, uint32_t pTimesOfFeatureValue[]);
;****************************************************************************************************************************************************
WELS_EXTERN SumOf16x16BlockOfFrame_sse2
    %assign  push_num 0
    LOAD_6_PARA
    PUSH_XMM 6
    SIGN_EXTENSION  r1, r1d
    SIGN_EXTENSION  r2, r2d
    SIGN_EXTENSION  r3, r3d
    push r12
    push r13
    push r0
    push r2
    push r4

    pxor    xmm0,   xmm0
    lea     r6, [r3+r3*2]

    mov     r12,    r1              ;r12:tmp_width
FIRST_ROW_X16H:
    movdqu  xmm1,   [r0]
    movdqu  xmm2,   [r0+r3]
    movdqu  xmm3,   [r0+r3*2]
    movdqu  xmm4,   [r0+r6]

    psadbw  xmm1,   xmm0
    psadbw  xmm2,   xmm0
    psadbw  xmm3,   xmm0
    psadbw  xmm4,   xmm0
    paddw   xmm1,   xmm2
    paddw   xmm3,   xmm4
    paddw   xmm1,   xmm3

    lea     r13,    [r0+r3*4]       ;ebp:r13
    movdqu  xmm2,   [r13]
    movdqu  xmm3,   [r13+r3]
    movdqu  xmm4,   [r13+r3*2]
    movdqu  xmm5,   [r13+r6]

    psadbw  xmm2,   xmm0
    psadbw  xmm3,   xmm0
    psadbw  xmm4,   xmm0
    psadbw  xmm5,   xmm0
    paddw   xmm2,   xmm3
    paddw   xmm4,   xmm5
    paddw   xmm2,   xmm4

    paddw   xmm1,   xmm2

    lea     r13,    [r13+r3*4]
    movdqu  xmm2,   [r13]
    movdqu  xmm3,   [r13+r3]
    movdqu  xmm4,   [r13+r3*2]
    movdqu  xmm5,   [r13+r6]

    psadbw  xmm2,   xmm0
    psadbw  xmm3,   xmm0
    psadbw  xmm4,   xmm0
    psadbw  xmm5,   xmm0
    paddw   xmm2,   xmm3
    paddw   xmm4,   xmm5
    paddw   xmm2,   xmm4

    paddw   xmm1,   xmm2

    lea     r13,    [r13+r3*4]
    movdqu  xmm2,   [r13]
    movdqu  xmm3,   [r13+r3]
    movdqu  xmm4,   [r13+r3*2]
    movdqu  xmm5,   [r13+r6]

    psadbw  xmm2,   xmm0
    psadbw  xmm3,   xmm0
    psadbw  xmm4,   xmm0
    psadbw  xmm5,   xmm0
    paddw   xmm2,   xmm3
    paddw   xmm4,   xmm5
    paddw   xmm2,   xmm4

    paddw   xmm1,   xmm2
    movdqa  xmm2,   xmm1
    punpckhwd xmm2, xmm0
    paddw xmm1, xmm2
    movd    r2d,    xmm1
    mov     [r4],   r2w
    inc     dword [r5+r2*4]

    inc     r0
    lea     r4, [r4+2]

    dec     r12
    jg      near FIRST_ROW_X16H

    pop r4
    pop r2
    pop r0
    mov r13, r2
    dec r13
    mov     r6, r3
    sal     r6, 4       ; succeeded 16th line
HEIGHT_LOOP_X16:
    mov     r12,    r1
WIDTH_LOOP_X16:
    movdqu  xmm1,   [r0+r6]
    movdqu  xmm2,   [r0]
    psadbw  xmm1,   xmm0
    psadbw  xmm2,   xmm0
    psubw   xmm1,   xmm2
    movdqa  xmm2,   xmm1
    punpckhwd xmm2, xmm0
    paddw   xmm1,   xmm2
    movd    r2d,    xmm1
    add     r2w,    word [r4]
    mov     [r4+r1*2],  r2w
    inc     dword [r5+r2*4]

    inc     r0
    add     r4, 2

    dec     r12
    jg      near WIDTH_LOOP_X16

    add     r0, r3
    sub     r0, r1

    dec     r13
    jg      near HEIGHT_LOOP_X16

    pop     r13
    pop     r12
    POP_XMM
    LOAD_6_PARA_POP
    ret

; requires:  width % 16 == 0 && height > 1
;-----------------------------------------------------------------------------------------------------------------------------
;void SumOf16x16BlockOfFrame_sse4(uint8_t* pRefPicture, const int32_t kiWidth, const int32_t kiHeight, const int32_t kiRefStride,
;                             uint16_t* pFeatureOfBlock, uint32_t pTimesOfFeatureValue[]);
;-----------------------------------------------------------------------------------------------------------------------------
; try 8 mv via offset
%macro SUM_LINE_X16_SSE41  5    ; ref, dst0, dst1, tmp0, tmp1
    movdqu  %2, [%1]
    movdqu  %3, [%1+8h]
    movdqa  %4, %2
    movdqa  %5, %3

    mpsadbw %2, xmm0,   0   ; 000 B
    mpsadbw %4, xmm0,   5   ; 101 B
    mpsadbw %3, xmm0,   2   ; 010 B
    mpsadbw %5, xmm0,   7   ; 111 B
    paddw   %2, %4
    paddw   %3, %5
    paddw   %2, %3  ; accumulate cost
%endmacro   ; end of SAD_16x16_LINE_SSE41

WELS_EXTERN SumOf16x16BlockOfFrame_sse4
    %assign  push_num 0
    LOAD_6_PARA
    PUSH_XMM 8
    SIGN_EXTENSION  r1, r1d
    SIGN_EXTENSION  r2, r2d
    SIGN_EXTENSION  r3, r3d
    push r12
    push r13
    push r0
    push r2
    push r4

    pxor    xmm0,   xmm0
    lea     r6, [r3+r3*2]

    mov     r12,    r1              ;r12:tmp_width
FIRST_ROW_X16_SSE4:
    SUM_LINE_X16_SSE41  r0,     xmm1, xmm2, xmm3, xmm4
    SUM_LINE_X16_SSE41  r0+r3,  xmm2, xmm3, xmm4, xmm5
    SUM_LINE_X16_SSE41  r0+r3*2,xmm3, xmm4, xmm5, xmm6
    SUM_LINE_X16_SSE41  r0+r6,  xmm4, xmm5, xmm6, xmm7
    paddw   xmm1, xmm2
    paddw   xmm3, xmm4
    paddw   xmm1, xmm3

    lea     r13,    [r0+r3*4]
    SUM_LINE_X16_SSE41  r13,        xmm2, xmm3, xmm4, xmm5
    paddw   xmm1, xmm2
    SUM_LINE_X16_SSE41  r13+r3,     xmm2, xmm3, xmm4, xmm5
    paddw   xmm1, xmm2
    SUM_LINE_X16_SSE41  r13+r3*2,   xmm2, xmm3, xmm4, xmm5
    paddw   xmm1, xmm2
    SUM_LINE_X16_SSE41  r13+r6,     xmm2, xmm3, xmm4, xmm5
    paddw   xmm1, xmm2

    lea     r13,    [r13+r3*4]
    SUM_LINE_X16_SSE41  r13,        xmm2, xmm3, xmm4, xmm5
    paddw   xmm1, xmm2
    SUM_LINE_X16_SSE41  r13+r3,     xmm2, xmm3, xmm4, xmm5
    paddw   xmm1, xmm2
    SUM_LINE_X16_SSE41  r13+r3*2,   xmm2, xmm3, xmm4, xmm5
    paddw   xmm1, xmm2
    SUM_LINE_X16_SSE41  r13+r6,     xmm2, xmm3, xmm4, xmm5
    paddw   xmm1, xmm2

    lea     r13,    [r13+r3*4]
    SUM_LINE_X16_SSE41  r13,        xmm2, xmm3, xmm4, xmm5
    paddw   xmm1, xmm2
    SUM_LINE_X16_SSE41  r13+r3,     xmm2, xmm3, xmm4, xmm5
    paddw   xmm1, xmm2
    SUM_LINE_X16_SSE41  r13+r3*2,   xmm2, xmm3, xmm4, xmm5
    paddw   xmm1, xmm2
    SUM_LINE_X16_SSE41  r13+r6,     xmm2, xmm3, xmm4, xmm5
    paddw   xmm1, xmm2

    movdqa  [r4],   xmm1
    movdqa  xmm2,   xmm1
    punpcklwd   xmm1,   xmm0
    punpckhwd   xmm2,   xmm0

    COUNT_SUM   xmm1,   r2d, r2, 1
    COUNT_SUM   xmm1,   r2d, r2, 1
    COUNT_SUM   xmm1,   r2d, r2, 1
    COUNT_SUM   xmm1,   r2d, r2, 0
    COUNT_SUM   xmm2,   r2d, r2, 1
    COUNT_SUM   xmm2,   r2d, r2, 1
    COUNT_SUM   xmm2,   r2d, r2, 1
    COUNT_SUM   xmm2,   r2d, r2, 0

    lea     r0, [r0+8]
    lea     r4, [r4+16] ; element size is 2

    sub     r12, 8
    jg      near FIRST_ROW_X16_SSE4

    pop r4
    pop r2
    pop r0
    mov r13, r2
    dec r13
    mov     r6, r3
    sal     r6, 4       ; succeeded 16th line

HEIGHT_LOOP_X16_SSE4:
    mov     r12,    r1
WIDTH_LOOP_X16_SSE4:
    movdqa  xmm7,   [r4]
    SUM_LINE_X16_SSE41  r0+r6, xmm1, xmm2, xmm3, xmm4
    SUM_LINE_X16_SSE41  r0, xmm2, xmm3, xmm4, xmm5

    paddw   xmm7,   xmm1
    psubw   xmm7,   xmm2
    movdqa  [r4+r1*2], xmm7

    movdqa  xmm6,   xmm7
    punpcklwd   xmm7,   xmm0
    punpckhwd   xmm6,   xmm0

    COUNT_SUM   xmm7,   r2d, r2, 1
    COUNT_SUM   xmm7,   r2d, r2, 1
    COUNT_SUM   xmm7,   r2d, r2, 1
    COUNT_SUM   xmm7,   r2d, r2, 0
    COUNT_SUM   xmm6,   r2d, r2, 1
    COUNT_SUM   xmm6,   r2d, r2, 1
    COUNT_SUM   xmm6,   r2d, r2, 1
    COUNT_SUM   xmm6,   r2d, r2, 0

    lea     r0, [r0+8]
    lea     r4, [r4+16]

    sub     r12, 8
    jg      near WIDTH_LOOP_X16_SSE4

    add     r0, r3
    sub     r0, r1

    dec     r13
    jg      near HEIGHT_LOOP_X16_SSE4

    pop     r13
    pop     r12
    POP_XMM
    LOAD_6_PARA_POP
    ret

;-----------------------------------------------------------------------------------------------------------------------------
; void FillQpelLocationByFeatureValue_sse2(uint16_t* pFeatureOfBlock, const int32_t kiWidth, const int32_t kiHeight, uint16_t** pFeatureValuePointerList)
;-----------------------------------------------------------------------------------------------------------------------------
WELS_EXTERN FillQpelLocationByFeatureValue_sse2
    %assign  push_num 0
    LOAD_4_PARA
    PUSH_XMM 8
    SIGN_EXTENSION  r1, r1d
    SIGN_EXTENSION  r2, r2d
    push r12
    push r13
    mov     r12,    r2

    movq    xmm7,   [mv_x_inc_x4]       ; x_qpel inc
    movq    xmm6,   [mv_y_inc_x4]       ; y_qpel inc
    movq    xmm5,   [mx_x_offset_x4]    ; x_qpel vector
    pxor    xmm4,   xmm4
    pxor    xmm3,   xmm3                ; y_qpel vector
HASH_HEIGHT_LOOP_SSE2:
    movdqa  xmm2,   xmm5    ; x_qpel vector
    mov     r4, r1
HASH_WIDTH_LOOP_SSE2:
    movq    xmm0,   [r0]            ; load x8 sum
    punpcklwd   xmm0,   xmm4
    movdqa      xmm1,   xmm2
    punpcklwd   xmm1,   xmm3
%rep    3
    movd    r2d,    xmm0        ;edx:r3
    lea     r5,     [r3+r2*8]   ;ebx:r5
    mov     r6,     [r5]        ;eax:r6
    movd    [r6],   xmm1
    mov     r13,    [r6+4]  ; explictly load eax+4 due cache miss from vtune observation
    lea     r6,     [r6+4]
    mov     [r5],   r6
    psrldq  xmm1,   4
    psrldq  xmm0,   4
%endrep
    movd    r2d,    xmm0
    lea     r5,     [r3+r2*8]   ;ebx:r5
    mov     r6,     [r5]        ;eax:r6
    movd    [r6],   xmm1
    mov     r13,    [r6+4]  ; explictly load eax+4 due cache miss from vtune observation
    lea     r6,     [r6+4]
    mov     [r5],   r6

    paddw   xmm2,   xmm7
    lea     r0,     [r0+8]
    sub     r4,     4
    jnz near HASH_WIDTH_LOOP_SSE2
    paddw   xmm3,   xmm6
    dec r12
    jnz near HASH_HEIGHT_LOOP_SSE2

    pop     r13
    pop     r12
    POP_XMM
    ret

;---------------------------------------------------------------------------------------------------------------------------------------------------
; void InitializeHashforFeature_sse2( uint32_t* pTimesOfFeatureValue, uint16_t* pBuf, const int32_t kiListSize,
;                                 uint16_t** pLocationOfFeature, uint16_t** pFeatureValuePointerList);
;uint16_t** pPositionOfSum, uint16_t** sum_idx_list, uint32_t* pTimesOfSum, uint16_t* pBuf, const int32_t list_sz )
;---------------------------------------------------------------------------------------------------------------------------------------------------
WELS_EXTERN InitializeHashforFeature_sse2
    %assign  push_num 0
    LOAD_5_PARA
    SIGN_EXTENSION  r2, r2d
    push r12
    push r13
    mov     r12,    r2
    sar     r2,     2
    mov     r5,     0       ;r5:ecx
    xor     r6,     r6
    pxor    xmm3,   xmm3
hash_assign_loop_x4_sse2:
    movdqa  xmm0,   [r0+r5]
    pslld   xmm0,   2

    movdqa  xmm1,   xmm0
    pcmpeqd xmm1,   xmm3
    movmskps    r6, xmm1
    cmp     r6,     0x0f
    jz  near hash_assign_with_copy_sse2

%assign x   0
%rep 4
    lea     r13,    [r3+r5*2+x]
    mov     [r13],  r1
    lea     r13,    [r4+r5*2+x]
    mov     [r13],  r1
    movd    r6d,    xmm0
    add     r1,     r6
    psrldq  xmm0,   4
%assign x   x+8
%endrep
    jmp near assign_next_sse2

hash_assign_with_copy_sse2:
    movq    xmm1,   r1
    pshufd  xmm2,   xmm1,   01000100b
    movdqa  [r3+r5*2], xmm2
    movdqa  [r4+r5*2], xmm2
    movdqa  [r3+r5*2+16], xmm2
    movdqa  [r4+r5*2+16], xmm2

assign_next_sse2:
    add     r5, 16
    dec     r2
    jnz     near hash_assign_loop_x4_sse2

    and     r12,    3
    jz      near hash_assign_no_rem_sse2
hash_assign_loop_x4_rem_sse2:
    lea     r13,    [r3+r5*2]
    mov     [r13],  r1
    lea     r13,    [r4+r5*2]
    mov     [r13],  r1
    mov     r6d,    [r0+r5]
    sal     r6,     2
    add     r1,     r6
    add     r5,     4
    dec     r12
    jnz     near hash_assign_loop_x4_rem_sse2

hash_assign_no_rem_sse2:
    pop     r13
    pop     r12
    ret

%endif

;**********************************************************************************************************************************
;   int32_t SumOf8x8SingleBlock_sse2(uint8_t* ref0, int32_t linesize)
;**********************************************************************************************************************************
WELS_EXTERN SumOf8x8SingleBlock_sse2
    %assign  push_num 0
    LOAD_2_PARA
    SIGN_EXTENSION  r1, r1d

    pxor xmm0, xmm0
    movq xmm1, [r0]
    movhps xmm1, [r0+r1]
    lea r0, [r0+2*r1]
    movq xmm2, [r0]
    movhps xmm2, [r0+r1]
    lea r0, [r0+2*r1]
    movq xmm3, [r0]
    movhps xmm3, [r0+r1]
    lea r0, [r0+2*r1]
    movq xmm4, [r0]
    movhps xmm4, [r0+r1]

    psadbw xmm1, xmm0
    psadbw xmm2, xmm0
    psadbw xmm3, xmm0
    psadbw xmm4, xmm0
    paddw xmm1, xmm2
    paddw xmm3, xmm4
    paddw xmm1, xmm3

    movdqa xmm2, xmm1
    punpckhwd xmm2, xmm0
    paddw xmm1, xmm2

    movd retrd, xmm1
    ret

;**********************************************************************************************************************************
;   int32_t SumOf16x16SingleBlock_sse2(uint8_t* ref0, int32_t linesize)
;**********************************************************************************************************************************
WELS_EXTERN SumOf16x16SingleBlock_sse2
    %assign  push_num 0
    LOAD_2_PARA
    PUSH_XMM 6
    SIGN_EXTENSION  r1, r1d

    pxor xmm0, xmm0
    movdqa xmm1, [r0]
    movdqa xmm2, [r0+r1]
    lea r0, [r0+2*r1]
    movdqa xmm3, [r0]
    movdqa xmm4, [r0+r1]
    psadbw xmm1, xmm0
    psadbw xmm2, xmm0
    psadbw xmm3, xmm0
    psadbw xmm4, xmm0
    paddw xmm1, xmm2
    paddw xmm3, xmm4
    paddw xmm1, xmm3

    lea r0, [r0+2*r1]
    movdqa xmm2, [r0]
    movdqa xmm3, [r0+r1]
    lea r0, [r0+2*r1]
    movdqa xmm4, [r0]
    movdqa xmm5, [r0+r1]
    psadbw xmm2, xmm0
    psadbw xmm3, xmm0
    psadbw xmm4, xmm0
    psadbw xmm5, xmm0
    paddw xmm2, xmm3
    paddw xmm4, xmm5
    paddw xmm2, xmm4

    paddw xmm1, xmm2

    lea r0, [r0+2*r1]
    movdqa xmm2, [r0]
    movdqa xmm3, [r0+r1]
    lea r0, [r0+2*r1]
    movdqa xmm4, [r0]
    movdqa xmm5, [r0+r1]
    psadbw xmm2, xmm0
    psadbw xmm3, xmm0
    psadbw xmm4, xmm0
    psadbw xmm5, xmm0
    paddw xmm2, xmm3
    paddw xmm4, xmm5
    paddw xmm2, xmm4

    paddw xmm1, xmm2

    lea r0, [r0+2*r1]
    movdqa xmm2, [r0]
    movdqa xmm3, [r0+r1]
    lea r0, [r0+2*r1]
    movdqa xmm4, [r0]
    movdqa xmm5, [r0+r1]
    psadbw xmm2, xmm0
    psadbw xmm3, xmm0
    psadbw xmm4, xmm0
    psadbw xmm5, xmm0
    paddw xmm2, xmm3
    paddw xmm4, xmm5
    paddw xmm2, xmm4

    paddw xmm1, xmm2

    movdqa xmm2, xmm1
    punpckhwd xmm2, xmm0
    paddw xmm1, xmm2

    movd retrd, xmm1
    POP_XMM
    ret

;**********************************************************************************************************************************
;
;   uint32_t SampleSad16x16Hor8_sse41( uint8_t *src, int32_t stride_src, uint8_t *ref, int32_t stride_ref, uint16 base_cost[8], int32_t *index_min_cost )
;
;   \note:
;       src need align with 16 bytes, ref is optional
;   \return value:
;       return minimal SAD cost, according index carried by index_min_cost
;**********************************************************************************************************************************
; try 8 mv via offset
; xmm7 store sad costs
%macro SAD_16x16_LINE_SSE41  4  ; src, ref, stride_src, stride_ref
    movdqa      xmm0, [%1]
    movdqu      xmm1, [%2]
    movdqu      xmm2, [%2+8h]
    movdqa      xmm3, xmm1
    movdqa      xmm4, xmm2

    mpsadbw     xmm1, xmm0, 0   ; 000 B
    paddw       xmm7, xmm1      ; accumulate cost

    mpsadbw     xmm3, xmm0, 5   ; 101 B
    paddw       xmm7, xmm3      ; accumulate cost

    mpsadbw     xmm2, xmm0, 2   ; 010 B
    paddw       xmm7, xmm2      ; accumulate cost

    mpsadbw     xmm4, xmm0, 7   ; 111 B
    paddw       xmm7, xmm4      ; accumulate cost

    add         %1, %3
    add         %2, %4
%endmacro   ; end of SAD_16x16_LINE_SSE41
%macro SAD_16x16_LINE_SSE41E  4 ; src, ref, stride_src, stride_ref
    movdqa      xmm0, [%1]
    movdqu      xmm1, [%2]
    movdqu      xmm2, [%2+8h]
    movdqa      xmm3, xmm1
    movdqa      xmm4, xmm2

    mpsadbw     xmm1, xmm0, 0   ; 000 B
    paddw       xmm7, xmm1      ; accumulate cost

    mpsadbw     xmm3, xmm0, 5   ; 101 B
    paddw       xmm7, xmm3      ; accumulate cost

    mpsadbw     xmm2, xmm0, 2   ; 010 B
    paddw       xmm7, xmm2      ; accumulate cost

    mpsadbw     xmm4, xmm0, 7   ; 111 B
    paddw       xmm7, xmm4      ; accumulate cost
%endmacro   ; end of SAD_16x16_LINE_SSE41E

WELS_EXTERN SampleSad16x16Hor8_sse41
    ;push ebx
    ;push esi
    ;mov eax, [esp+12]  ;   src
    ;mov ecx, [esp+16]  ;   stride_src
    ;mov ebx, [esp+20]  ;   ref
    ;mov edx, [esp+24]  ;   stride_ref
    ;mov esi, [esp+28]  ;   base_cost
    %assign  push_num 0
    LOAD_6_PARA
    PUSH_XMM 8
    SIGN_EXTENSION  r1, r1d
    SIGN_EXTENSION  r3, r3d
    pxor    xmm7,   xmm7

    SAD_16x16_LINE_SSE41    r0, r2, r1, r3
    SAD_16x16_LINE_SSE41    r0, r2, r1, r3
    SAD_16x16_LINE_SSE41    r0, r2, r1, r3
    SAD_16x16_LINE_SSE41    r0, r2, r1, r3

    SAD_16x16_LINE_SSE41    r0, r2, r1, r3
    SAD_16x16_LINE_SSE41    r0, r2, r1, r3
    SAD_16x16_LINE_SSE41    r0, r2, r1, r3
    SAD_16x16_LINE_SSE41    r0, r2, r1, r3

    SAD_16x16_LINE_SSE41    r0, r2, r1, r3
    SAD_16x16_LINE_SSE41    r0, r2, r1, r3
    SAD_16x16_LINE_SSE41    r0, r2, r1, r3
    SAD_16x16_LINE_SSE41    r0, r2, r1, r3

    SAD_16x16_LINE_SSE41    r0, r2, r1, r3
    SAD_16x16_LINE_SSE41    r0, r2, r1, r3
    SAD_16x16_LINE_SSE41    r0, r2, r1, r3
    SAD_16x16_LINE_SSE41E   r0, r2, r1, r3

    pxor    xmm0,   xmm0
    movdqa  xmm6,   xmm7
    punpcklwd   xmm6,   xmm0
    punpckhwd   xmm7,   xmm0

    movdqa  xmm5,   [r4]
    movdqa  xmm4,   xmm5
    punpcklwd   xmm4,   xmm0
    punpckhwd   xmm5,   xmm0

    paddd   xmm4,   xmm6
    paddd   xmm5,   xmm7
    movdqa  xmm3,   xmm4
    pminud  xmm3,   xmm5
    pshufd  xmm2,   xmm3,   01001110B
    pminud  xmm2,   xmm3
    pshufd  xmm3,   xmm2,   10110001B
    pminud  xmm2,   xmm3
    movd    retrd,  xmm2
    pcmpeqd xmm4,   xmm2
    movmskps    r2d, xmm4
    bsf     r1d,    r2d
    jnz near WRITE_INDEX

    pcmpeqd xmm5,   xmm2
    movmskps    r2d, xmm5
    bsf     r1d,    r2d
    add     r1d,    4

WRITE_INDEX:
    mov     [r5],   r1d
    POP_XMM
    LOAD_6_PARA_POP
    ret

;**********************************************************************************************************************************
;
;   uint32_t SampleSad8x8Hor8_sse41( uint8_t *src, int32_t stride_src, uint8_t *ref, int32_t stride_ref, uint16_t base_cost[8], int32_t *index_min_cost )
;
;   \note:
;       src and ref is optional to align with 16 due inter 8x8
;   \return value:
;       return minimal SAD cost, according index carried by index_min_cost
;
;**********************************************************************************************************************************
; try 8 mv via offset
; xmm7 store sad costs
%macro SAD_8x8_LINE_SSE41  4    ; src, ref, stride_src, stride_ref
    movdqu      xmm0, [%1]
    movdqu      xmm1, [%2]
    movdqa      xmm2, xmm1

    mpsadbw     xmm1, xmm0, 0   ; 000 B
    paddw       xmm7, xmm1      ; accumulate cost

    mpsadbw     xmm2, xmm0, 5   ; 101 B
    paddw       xmm7, xmm2      ; accumulate cost

    add         %1, %3
    add         %2, %4
%endmacro   ; end of SAD_8x8_LINE_SSE41
%macro SAD_8x8_LINE_SSE41E  4   ; src, ref, stride_src, stride_ref
    movdqu      xmm0, [%1]
    movdqu      xmm1, [%2]
    movdqa      xmm2, xmm1

    mpsadbw     xmm1, xmm0, 0   ; 000 B
    paddw       xmm7, xmm1      ; accumulate cost

    mpsadbw     xmm2, xmm0, 5   ; 101 B
    paddw       xmm7, xmm2      ; accumulate cost
%endmacro   ; end of SAD_8x8_LINE_SSE41E

WELS_EXTERN SampleSad8x8Hor8_sse41
    %assign  push_num 0
    LOAD_6_PARA
    PUSH_XMM 8
    SIGN_EXTENSION  r1, r1d
    SIGN_EXTENSION  r3, r3d
    movdqa xmm7, [r4]   ;   load base cost list

    SAD_8x8_LINE_SSE41  r0, r2, r1, r3
    SAD_8x8_LINE_SSE41  r0, r2, r1, r3
    SAD_8x8_LINE_SSE41  r0, r2, r1, r3
    SAD_8x8_LINE_SSE41  r0, r2, r1, r3

    SAD_8x8_LINE_SSE41  r0, r2, r1, r3
    SAD_8x8_LINE_SSE41  r0, r2, r1, r3
    SAD_8x8_LINE_SSE41  r0, r2, r1, r3
    SAD_8x8_LINE_SSE41E r0, r2, r1, r3

    phminposuw  xmm0, xmm7  ; horizon search the minimal sad cost and its index
    movd    retrd, xmm0 ; for return: DEST[15:0] <- MIN, DEST[31:16] <- INDEX
    mov     r1d, retrd
    and     retrd, 0xFFFF
    sar     r1d, 16
    mov     [r5], r1d

    POP_XMM
    LOAD_6_PARA_POP
    ret
