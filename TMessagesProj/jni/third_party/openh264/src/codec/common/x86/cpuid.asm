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
;*  cpu_mmx.asm
;*
;*  Abstract
;*      verify cpuid feature support and cpuid detection
;*
;*  History
;*      04/29/2009  Created
;*
;*************************************************************************/

%include "asm_inc.asm"

;******************************************************************************************
; Macros
;******************************************************************************************


;******************************************************************************************
; Code
;******************************************************************************************

SECTION .text

; refer to "The IA-32 Intel(R) Architecture Software Developers Manual, Volume 2A A-M"
; section CPUID - CPU Identification

;******************************************************************************************
;   int32_t WelsCPUIdVerify()
;******************************************************************************************
WELS_EXTERN WelsCPUIdVerify
    push    r1
    PUSHRFLAGS
    PUSHRFLAGS

    pop      r1
    mov      eax, r1d
    xor      eax, 00200000h
    xor      eax, r1d
    POPRFLAGS
    pop      r1
    ret

;****************************************************************************************************
;   void WelsCPUId( int32_t uiIndex, int32_t *pFeatureA, int32_t *pFeatureB, int32_t *pFeatureC, int32_t *pFeatureD )
;****************************************************************************************************
%ifdef       WIN64

WELS_EXTERN WelsCPUId
    push     rbx
    push     rdx

    mov      eax,     ecx
    mov      ecx,     [r9]
    cpuid
    mov      [r9],    ecx
    mov      [r8],    ebx
    mov      rcx,    [rsp + 2*8 + 40]
    mov      [rcx],   edx
    pop      rdx
    mov      [rdx],   eax

    pop      rbx
    ret

%elifdef     UNIX64
WELS_EXTERN WelsCPUId
    push     rbx
    push     rcx
    push     rdx

    mov      eax,     edi
    mov      ecx,     [rcx]
    cpuid
    mov      [r8],    edx
    pop      rdx
    pop      r8
    mov      [r8],   ecx
    mov      [rdx],   ebx
    mov      [rsi],   eax

    pop      rbx
    ret

%elifdef     X86_32

WELS_EXTERN WelsCPUId
    push    ebx
    push    edi

    mov     eax, [esp+12]   ; operating index
    mov     edi, [esp+24]
    mov     ecx, [edi]
    cpuid                   ; cpuid

    ; processing various information return
    mov     edi, [esp+16]
    mov     [edi], eax
    mov     edi, [esp+20]
    mov     [edi], ebx
    mov     edi, [esp+24]
    mov     [edi], ecx
    mov     edi, [esp+28]
    mov     [edi], edx

    pop     edi
    pop     ebx
    ret

%endif

; need call after cpuid=1 and eax, ecx flag got then
;****************************************************************************************************
;   int32_t WelsCPUSupportAVX( uint32_t eax, uint32_t ecx )
;****************************************************************************************************
WELS_EXTERN WelsCPUSupportAVX
%ifdef     WIN64
    mov   eax,    ecx
    mov   ecx,    edx
%elifdef   UNIX64
    mov eax, edi
    mov ecx, esi
%else
    mov eax, [esp+4]
    mov ecx, [esp+8]
%endif

    ; refer to detection of AVX addressed in INTEL AVX manual document
    and ecx, 018000000H
    cmp ecx, 018000000H             ; check both OSXSAVE and AVX feature flags
    jne avx_not_supported
    ; processor supports AVX instructions and XGETBV is enabled by OS
    mov ecx, 0                              ; specify 0 for XFEATURE_ENABLED_MASK register
    XGETBV                                  ; result in EDX:EAX
    and eax, 06H
    cmp eax, 06H                    ; check OS has enabled both XMM and YMM state support
    jne avx_not_supported
    mov eax, 1
    ret
avx_not_supported:
    mov eax, 0
    ret


; need call after cpuid=1 and eax, ecx flag got then
;****************************************************************************************************
;   int32_t WelsCPUSupportFMA( uint32_t eax, uint32_t ecx )
;****************************************************************************************************
WELS_EXTERN WelsCPUSupportFMA
%ifdef     WIN64
    mov   eax,   ecx
    mov   ecx,   edx
%elifdef   UNIX64
    mov   eax,   edi
    mov   ecx,   esi
%else
    mov eax, [esp+4]
    mov ecx, [esp+8]
%endif
    ; refer to detection of FMA addressed in INTEL AVX manual document
    and ecx, 018001000H
    cmp ecx, 018001000H     ; check OSXSAVE, AVX, FMA feature flags
    jne fma_not_supported
    ; processor supports AVX,FMA instructions and XGETBV is enabled by OS
    mov ecx, 0              ; specify 0 for XFEATURE_ENABLED_MASK register
    XGETBV                  ; result in EDX:EAX
    and eax, 06H
    cmp eax, 06H            ; check OS has enabled both XMM and YMM state support
    jne fma_not_supported
    mov eax, 1
    ret
fma_not_supported:
    mov eax, 0
    ret

;******************************************************************************************
;   void WelsEmms()
;******************************************************************************************
WELS_EXTERN WelsEmms
    emms    ; empty mmx technology states
    ret

