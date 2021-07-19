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
;*  quant.asm
;*
;*  Abstract
;*      sse2 quantize inter-block
;*
;*  History
;*      7/6/2009 Created
;*
;*
;*************************************************************************/

%include "asm_inc.asm"


SECTION .text
;************************************************
;NEW_QUANT
;************************************************

%macro SSE2_Quant8  5
    MOVDQ   %1, %5
    pxor    %2, %2
    pcmpgtw %2, %1
    pxor    %1, %2
    psubw   %1, %2
    paddusw %1, %3
    pmulhuw %1, %4
    pxor    %1, %2
    psubw   %1, %2
    MOVDQ   %5, %1
%endmacro

%macro SSE2_QuantMax8  6
    MOVDQ   %1, %5
    pxor    %2, %2
    pcmpgtw %2, %1
    pxor    %1, %2
    psubw   %1, %2
    paddusw %1, %3
    pmulhuw %1, %4
    pmaxsw  %6, %1
    pxor    %1, %2
    psubw   %1, %2
    MOVDQ   %5, %1
%endmacro

%define pDct                esp + 4
%define ff                  esp + 8
%define mf                  esp + 12
%define max                 esp + 16
;***********************************************************************
;   void WelsQuant4x4_sse2(int16_t *pDct, int16_t* ff,  int16_t *mf);
;***********************************************************************
WELS_EXTERN WelsQuant4x4_sse2
    %assign push_num 0
    LOAD_3_PARA
    movdqa  xmm2, [r1]
    movdqa  xmm3, [r2]

    SSE2_Quant8 xmm0, xmm1, xmm2, xmm3, [r0]
    SSE2_Quant8 xmm0, xmm1, xmm2, xmm3, [r0 + 0x10]

    ret

;***********************************************************************
;void WelsQuant4x4Dc_sse2(int16_t *pDct, const int16_t ff, int16_t mf);
;***********************************************************************
WELS_EXTERN WelsQuant4x4Dc_sse2
    %assign push_num 0
    LOAD_3_PARA
    SIGN_EXTENSIONW r1, r1w
    SIGN_EXTENSIONW r2, r2w
    SSE2_Copy8Times xmm3, r2d

    SSE2_Copy8Times xmm2, r1d

    SSE2_Quant8 xmm0, xmm1, xmm2, xmm3, [r0]
    SSE2_Quant8 xmm0, xmm1, xmm2, xmm3, [r0 + 0x10]

    ret

;***********************************************************************
;   void WelsQuantFour4x4_sse2(int16_t *pDct, int16_t* ff,  int16_t *mf);
;***********************************************************************
WELS_EXTERN WelsQuantFour4x4_sse2
    %assign push_num 0
    LOAD_3_PARA
    MOVDQ   xmm2, [r1]
    MOVDQ   xmm3, [r2]

    SSE2_Quant8 xmm0, xmm1, xmm2, xmm3, [r0]
    SSE2_Quant8 xmm0, xmm1, xmm2, xmm3, [r0 + 0x10]
    SSE2_Quant8 xmm0, xmm1, xmm2, xmm3, [r0 + 0x20]
    SSE2_Quant8 xmm0, xmm1, xmm2, xmm3, [r0 + 0x30]
    SSE2_Quant8 xmm0, xmm1, xmm2, xmm3, [r0 + 0x40]
    SSE2_Quant8 xmm0, xmm1, xmm2, xmm3, [r0 + 0x50]
    SSE2_Quant8 xmm0, xmm1, xmm2, xmm3, [r0 + 0x60]
    SSE2_Quant8 xmm0, xmm1, xmm2, xmm3, [r0 + 0x70]

    ret

;***********************************************************************
;   void WelsQuantFour4x4Max_sse2(int16_t *pDct, int32_t* f,  int16_t *mf, int16_t *max);
;***********************************************************************
WELS_EXTERN WelsQuantFour4x4Max_sse2
    %assign push_num 0
    LOAD_4_PARA
    PUSH_XMM 8
    MOVDQ   xmm2, [r1]
    MOVDQ   xmm3, [r2]

    pxor    xmm4, xmm4
    pxor    xmm5, xmm5
    pxor    xmm6, xmm6
    pxor    xmm7, xmm7
    SSE2_QuantMax8  xmm0, xmm1, xmm2, xmm3, [r0   ], xmm4
    SSE2_QuantMax8  xmm0, xmm1, xmm2, xmm3, [r0 + 0x10], xmm4
    SSE2_QuantMax8  xmm0, xmm1, xmm2, xmm3, [r0 + 0x20], xmm5
    SSE2_QuantMax8  xmm0, xmm1, xmm2, xmm3, [r0 + 0x30], xmm5
    SSE2_QuantMax8  xmm0, xmm1, xmm2, xmm3, [r0 + 0x40], xmm6
    SSE2_QuantMax8  xmm0, xmm1, xmm2, xmm3, [r0 + 0x50], xmm6
    SSE2_QuantMax8  xmm0, xmm1, xmm2, xmm3, [r0 + 0x60], xmm7
    SSE2_QuantMax8  xmm0, xmm1, xmm2, xmm3, [r0 + 0x70], xmm7

    SSE2_TransTwo4x4W xmm4, xmm5, xmm6, xmm7, xmm0
    pmaxsw  xmm0,  xmm4
    pmaxsw  xmm0,  xmm5
    pmaxsw  xmm0,  xmm7
    movdqa  xmm1,  xmm0
    punpckhqdq  xmm0, xmm1
    pmaxsw  xmm0, xmm1

    movq    [r3], xmm0
    POP_XMM
    LOAD_4_PARA_POP
    ret

%macro MMX_Copy4Times 2
    movd        %1, %2
    punpcklwd   %1, %1
    punpckldq   %1, %1
%endmacro

SECTION .text

%macro MMX_Quant4  4
    pxor    %2, %2
    pcmpgtw %2, %1
    pxor    %1, %2
    psubw   %1, %2
    paddusw %1, %3
    pmulhuw %1, %4
    pxor    %1, %2
    psubw   %1, %2
%endmacro

;***********************************************************************
;int32_t WelsHadamardQuant2x2_mmx(int16_t *rs, const int16_t ff, int16_t mf, int16_t * pDct, int16_t * block);
;***********************************************************************
WELS_EXTERN WelsHadamardQuant2x2_mmx
    %assign push_num 0
    LOAD_5_PARA
    SIGN_EXTENSIONW r1, r1w
    SIGN_EXTENSIONW r2, r2w
    movd        mm0,            [r0]
    movd        mm1,            [r0 + 0x20]
    punpcklwd   mm0,            mm1
    movd        mm3,            [r0 + 0x40]
    movd        mm1,            [r0 + 0x60]
    punpcklwd   mm3,            mm1

    ;hdm_2x2,   mm0 = dct0 dct1, mm3 = dct2 dct3
    movq        mm5,            mm3
    paddw       mm3,            mm0
    psubw       mm0,            mm5
    punpcklwd   mm3,            mm0
    movq        mm1,            mm3
    psrlq       mm1,            32
    movq        mm5,            mm1
    paddw       mm1,            mm3
    psubw       mm3,            mm5
    punpcklwd   mm1,            mm3

    ;quant_2x2_dc
    MMX_Copy4Times  mm3,        r2d
    MMX_Copy4Times  mm2,        r1d
    MMX_Quant4      mm1,    mm0,    mm2,    mm3

    ; store dct_2x2
    movq        [r3],           mm1
    movq        [r4],           mm1

    ; pNonZeroCount of dct_2x2
    pcmpeqb     mm2,            mm2     ; mm2 = FF
    pxor        mm3,            mm3
    packsswb    mm1,            mm3
    pcmpeqb     mm1,            mm3     ; set FF if equal, 0 if not equal
    psubsb      mm1,            mm2     ; set 0 if equal, 1 if not equal
    psadbw      mm1,            mm3     ;
    mov         r1w,                0
    mov         [r0],           r1w
    mov         [r0 + 0x20],    r1w
    mov         [r0 + 0x40],    r1w
    mov         [r0 + 0x60],    r1w


    movd        retrd,      mm1

    WELSEMMS
    LOAD_5_PARA_POP
    ret

;***********************************************************************
;int32_t WelsHadamardQuant2x2Skip_mmx(int16_t *pDct, int16_t ff,  int16_t mf);
;***********************************************************************
WELS_EXTERN WelsHadamardQuant2x2Skip_mmx
    %assign push_num 0
    LOAD_3_PARA
    SIGN_EXTENSIONW r1, r1w
    SIGN_EXTENSIONW r2, r2w
    movd        mm0,            [r0]
    movd        mm1,            [r0 + 0x20]
    punpcklwd   mm0,            mm1
    movd        mm3,            [r0 + 0x40]
    movd        mm1,            [r0 + 0x60]
    punpcklwd   mm3,            mm1

    ;hdm_2x2,   mm0 = dct0 dct1, mm3 = dct2 dct3
    movq        mm5,            mm3
    paddw       mm3,            mm0
    psubw       mm0,            mm5
    punpcklwd   mm3,            mm0
    movq        mm1,            mm3
    psrlq       mm1,            32
    movq        mm5,            mm1
    paddw       mm1,            mm3
    psubw       mm3,            mm5
    punpcklwd   mm1,            mm3

    ;quant_2x2_dc
    MMX_Copy4Times  mm3,        r2d
    MMX_Copy4Times  mm2,        r1d
    MMX_Quant4      mm1,    mm0,    mm2,    mm3

    ; pNonZeroCount of dct_2x2
    pcmpeqb     mm2,            mm2     ; mm2 = FF
    pxor        mm3,            mm3
    packsswb    mm1,            mm3
    pcmpeqb     mm1,            mm3     ; set FF if equal, 0 if not equal
    psubsb      mm1,            mm2     ; set 0 if equal, 1 if not equal
    psadbw      mm1,            mm3     ;
    movd        retrd,          mm1

    WELSEMMS
    ret


%macro SSE2_DeQuant8 3
    MOVDQ  %2, %1
    pmullw %2, %3
    MOVDQ  %1, %2
%endmacro


;***********************************************************************
; void WelsDequant4x4_sse2(int16_t *pDct, const uint16_t* mf);
;***********************************************************************
WELS_EXTERN WelsDequant4x4_sse2
    %assign push_num 0
    LOAD_2_PARA

    movdqa  xmm1, [r1]
    SSE2_DeQuant8 [r0   ],  xmm0, xmm1
    SSE2_DeQuant8 [r0 + 0x10],  xmm0, xmm1

    ret

;***********************************************************************
;void WelsDequantFour4x4_sse2(int16_t *pDct, const uint16_t* mf);
;***********************************************************************

WELS_EXTERN WelsDequantFour4x4_sse2
    %assign push_num 0
    LOAD_2_PARA

    movdqa  xmm1, [r1]
    SSE2_DeQuant8 [r0   ],  xmm0, xmm1
    SSE2_DeQuant8 [r0+0x10  ],  xmm0, xmm1
    SSE2_DeQuant8 [r0+0x20  ],  xmm0, xmm1
    SSE2_DeQuant8 [r0+0x30  ],  xmm0, xmm1
    SSE2_DeQuant8 [r0+0x40  ],  xmm0, xmm1
    SSE2_DeQuant8 [r0+0x50  ],  xmm0, xmm1
    SSE2_DeQuant8 [r0+0x60  ],  xmm0, xmm1
    SSE2_DeQuant8 [r0+0x70  ],  xmm0, xmm1

    ret

;***********************************************************************
;void WelsDequantIHadamard4x4_sse2(int16_t *rs, const uint16_t mf);
;***********************************************************************
WELS_EXTERN WelsDequantIHadamard4x4_sse2
    %assign push_num 0
    LOAD_2_PARA
    %ifndef X86_32
    movzx r1, r1w
    %endif

    ; WelsDequantLumaDc4x4
    SSE2_Copy8Times xmm1,       r1d
    ;psrlw      xmm1,       2       ; for the (>>2) in ihdm
    MOVDQ       xmm0,       [r0]
    MOVDQ       xmm2,       [r0+0x10]
    pmullw      xmm0,       xmm1
    pmullw      xmm2,       xmm1

    ; ihdm_4x4
    movdqa      xmm1,       xmm0
    psrldq      xmm1,       8
    movdqa      xmm3,       xmm2
    psrldq      xmm3,       8

    SSE2_SumSub     xmm0, xmm3, xmm5                    ; xmm0 = xmm0 - xmm3, xmm3 = xmm0 + xmm3
    SSE2_SumSub     xmm1, xmm2, xmm5                    ; xmm1 = xmm1 - xmm2, xmm2 = xmm1 + xmm2
    SSE2_SumSub     xmm3, xmm2, xmm5                    ; xmm3 = xmm3 - xmm2, xmm2 = xmm3 + xmm2
    SSE2_SumSub     xmm0, xmm1, xmm5                    ; xmm0 = xmm0 - xmm1, xmm1 = xmm0 + xmm1

    SSE2_TransTwo4x4W   xmm2, xmm1, xmm3, xmm0, xmm4
    SSE2_SumSub     xmm2, xmm4, xmm5
    SSE2_SumSub     xmm1, xmm0, xmm5
    SSE2_SumSub     xmm4, xmm0, xmm5
    SSE2_SumSub     xmm2, xmm1, xmm5
    SSE2_TransTwo4x4W   xmm0, xmm1, xmm4, xmm2, xmm3

    punpcklqdq  xmm0,       xmm1
    MOVDQ       [r0],       xmm0

    punpcklqdq  xmm2,       xmm3
    MOVDQ       [r0+16],    xmm2
    ret


%ifdef HAVE_AVX2
; data=%1 abs_out=%2 ff=%3 mf=%4 7FFFh=%5
%macro AVX2_Quant 5
    vpabsw          %2, %1
    vpor            %1, %1, %5  ; ensure non-zero before vpsignw
    vpaddusw        %2, %2, %3
    vpmulhuw        %2, %2, %4
    vpsignw         %1, %2, %1
%endmacro


;***********************************************************************
;   void WelsQuant4x4_avx2(int16_t *pDct, int16_t* ff, int16_t *mf);
;***********************************************************************

WELS_EXTERN WelsQuant4x4_avx2
    %assign push_num 0
    LOAD_3_PARA
    PUSH_XMM 5
    vbroadcasti128  ymm0, [r1]
    vbroadcasti128  ymm1, [r2]
    WELS_DW32767_VEX ymm2
    vmovdqu         ymm3, [r0]
    AVX2_Quant      ymm3, ymm4, ymm0, ymm1, ymm2
    vmovdqu         [r0], ymm3
    vzeroupper
    POP_XMM
    ret


;***********************************************************************
;void WelsQuant4x4Dc_avx2(int16_t *pDct, int16_t ff, int16_t mf);
;***********************************************************************

WELS_EXTERN WelsQuant4x4Dc_avx2
    %assign push_num 0
    LOAD_1_PARA
    PUSH_XMM 5
%ifidni r1, arg2
    vmovd           xmm0, arg2d
    vpbroadcastw    ymm0, xmm0
%else
    vpbroadcastw    ymm0, arg2
%endif
%ifidni r2, arg3
    vmovd           xmm1, arg3d
    vpbroadcastw    ymm1, xmm1
%else
    vpbroadcastw    ymm1, arg3
%endif
    WELS_DW32767_VEX ymm2
    vmovdqu         ymm3, [r0]
    AVX2_Quant      ymm3, ymm4, ymm0, ymm1, ymm2
    vmovdqu         [r0], ymm3
    vzeroupper
    POP_XMM
    ret


;***********************************************************************
;   void WelsQuantFour4x4_avx2(int16_t *pDct, int16_t* ff, int16_t *mf);
;***********************************************************************

WELS_EXTERN WelsQuantFour4x4_avx2
    %assign push_num 0
    LOAD_3_PARA
    PUSH_XMM 6
    vbroadcasti128  ymm0, [r1]
    vbroadcasti128  ymm1, [r2]
    WELS_DW32767_VEX ymm4
    vmovdqu         ymm3, [r0 + 0x00]
    vmovdqu         ymm5, [r0 + 0x20]
    AVX2_Quant      ymm3, ymm2, ymm0, ymm1, ymm4
    vmovdqu         [r0 + 0x00], ymm3
    AVX2_Quant      ymm5, ymm2, ymm0, ymm1, ymm4
    vmovdqu         [r0 + 0x20], ymm5
    vmovdqu         ymm3, [r0 + 0x40]
    vmovdqu         ymm5, [r0 + 0x60]
    AVX2_Quant      ymm3, ymm2, ymm0, ymm1, ymm4
    vmovdqu         [r0 + 0x40], ymm3
    AVX2_Quant      ymm5, ymm2, ymm0, ymm1, ymm4
    vmovdqu         [r0 + 0x60], ymm5
    vzeroupper
    POP_XMM
    ret


;***********************************************************************
;   void WelsQuantFour4x4Max_avx2(int16_t *pDct, int32_t* ff, int16_t *mf, int16_t *max);
;***********************************************************************

WELS_EXTERN WelsQuantFour4x4Max_avx2
    %assign push_num 0
    LOAD_4_PARA
    PUSH_XMM 7
    vbroadcasti128  ymm0, [r1]
    vbroadcasti128  ymm1, [r2]
    WELS_DW32767_VEX ymm6
    vmovdqu         ymm4, [r0 + 0x00]
    vmovdqu         ymm5, [r0 + 0x20]
    AVX2_Quant      ymm4, ymm2, ymm0, ymm1, ymm6
    vmovdqu         [r0 + 0x00], ymm4
    AVX2_Quant      ymm5, ymm3, ymm0, ymm1, ymm6
    vmovdqu         [r0 + 0x20], ymm5
    vperm2i128      ymm4, ymm2, ymm3, 00100000b
    vperm2i128      ymm3, ymm2, ymm3, 00110001b
    vpmaxsw         ymm2, ymm4, ymm3
    vmovdqu         ymm4, [r0 + 0x40]
    vmovdqu         ymm5, [r0 + 0x60]
    AVX2_Quant      ymm4, ymm3, ymm0, ymm1, ymm6
    vmovdqu         [r0 + 0x40], ymm4
    AVX2_Quant      ymm5, ymm4, ymm0, ymm1, ymm6
    vmovdqu         [r0 + 0x60], ymm5
    vperm2i128      ymm5, ymm3, ymm4, 00100000b
    vperm2i128      ymm4, ymm3, ymm4, 00110001b
    vpmaxsw         ymm3, ymm5, ymm4
    vpxor           ymm2, ymm2, ymm6  ; flip bits so as to enable use of vphminposuw to find max value.
    vpxor           ymm3, ymm3, ymm6  ; flip bits so as to enable use of vphminposuw to find max value.
    vextracti128    xmm4, ymm2, 1
    vextracti128    xmm5, ymm3, 1
    vphminposuw     xmm2, xmm2
    vphminposuw     xmm3, xmm3
    vphminposuw     xmm4, xmm4
    vphminposuw     xmm5, xmm5
    vpunpcklwd      xmm2, xmm2, xmm4
    vpunpcklwd      xmm3, xmm3, xmm5
    vpunpckldq      xmm2, xmm2, xmm3
    vpxor           xmm2, xmm2, xmm6  ; restore non-flipped values.
    vmovq           [r3], xmm2        ; store max values.
    vzeroupper
    POP_XMM
    LOAD_4_PARA_POP
    ret
%endif

