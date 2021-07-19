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
;*  sse2inc.asm
;*
;*  Abstract
;*      macro and constant
;*
;*  History
;*      8/5/2009 Created
;*
;*
;*************************************************************************/
;***********************************************************************
; Options, for DEBUG
;***********************************************************************

%if 1
    %define MOVDQ movdqa
%else
    %define MOVDQ movdqu
%endif

%if 1
    %define WELSEMMS emms
%else
    %define WELSEMMS
%endif


;***********************************************************************
; Macros
;***********************************************************************

%ifdef WIN64 ; Windows x64 ;************************************

DEFAULT REL

BITS 64

%define arg1 rcx
%define arg2 rdx
%define arg3 r8
%define arg4 r9
%define arg5 [rsp + push_num*8 + 40]
%define arg6 [rsp + push_num*8 + 48]
%define arg7 [rsp + push_num*8 + 56]
%define arg8 [rsp + push_num*8 + 64]
%define arg9 [rsp + push_num*8 + 72]
%define arg10 [rsp + push_num*8 + 80]
%define arg11 [rsp + push_num*8 + 88]
%define arg12 [rsp + push_num*8 + 96]

%define arg1d ecx
%define arg2d edx
%define arg3d r8d
%define arg4d r9d
%define arg5d arg5
%define arg6d arg6
%define arg7d arg7
%define arg8d arg8
%define arg9d arg9
%define arg10d arg10
%define arg11d arg11
%define arg12d arg12

%define r0 rcx
%define r1 rdx
%define r2 r8
%define r3 r9
%define r4 rax
%define r5 r10
%define r6 r11
%define r7 rsp

%define r0d ecx
%define r1d edx
%define r2d r8d
%define r3d r9d
%define r4d eax
%define r5d r10d
%define r6d r11d

%define r0w  cx
%define r1w  dx
%define r2w  r8w
%define r3w  r9w
%define r4w  ax
%define r6w  r11w

%define r0b  cl
%define r1b  dl
%define r2b  r8l
%define r3b  r9l

%define  PUSHRFLAGS     pushfq
%define  POPRFLAGS      popfq
%define  retrq          rax
%define  retrd          eax

%elifdef UNIX64 ; Unix x64 ;************************************

DEFAULT REL

BITS 64

%ifidn __OUTPUT_FORMAT__,elf64
SECTION .note.GNU-stack noalloc noexec nowrite progbits ; Mark the stack as non-executable
%endif

%define arg1 rdi
%define arg2 rsi
%define arg3 rdx
%define arg4 rcx
%define arg5 r8
%define arg6 r9
%define arg7 [rsp + push_num*8 + 8]
%define arg8 [rsp + push_num*8 + 16]
%define arg9 [rsp + push_num*8 + 24]
%define arg10 [rsp + push_num*8 + 32]
%define arg11 [rsp + push_num*8 + 40]
%define arg12 [rsp + push_num*8 + 48]

%define arg1d edi
%define arg2d esi
%define arg3d edx
%define arg4d ecx
%define arg5d r8d
%define arg6d r9d
%define arg7d arg7
%define arg8d arg8
%define arg9d arg9
%define arg10d arg10
%define arg11d arg11
%define arg12d arg12

%define r0 rdi
%define r1 rsi
%define r2 rdx
%define r3 rcx
%define r4 r8
%define r5 r9
%define r6 r10
%define r7 rsp

%define r0d edi
%define r1d esi
%define r2d edx
%define r3d ecx
%define r4d r8d
%define r5d r9d
%define r6d r10d

%define r0w  di
%define r1w  si
%define r2w  dx
%define r3w  cx
%define r4w  r8w
%define r6w  r10w

%define r0b  dil
%define r1b  sil
%define r2b  dl
%define r3b  cl

%define  PUSHRFLAGS     pushfq
%define  POPRFLAGS      popfq
%define  retrq          rax
%define  retrd          eax

%elifdef X86_32 ; X86_32 ;************************************

BITS 32

%ifidn __OUTPUT_FORMAT__,elf
SECTION .note.GNU-stack noalloc noexec nowrite progbits ; Mark the stack as non-executable
%endif

%define arg1 [esp + push_num*4 + 4]
%define arg2 [esp + push_num*4 + 8]
%define arg3 [esp + push_num*4 + 12]
%define arg4 [esp + push_num*4 + 16]
%define arg5 [esp + push_num*4 + 20]
%define arg6 [esp + push_num*4 + 24]
%define arg7 [esp + push_num*4 + 28]
%define arg8 [esp + push_num*4 + 32]
%define arg9 [esp + push_num*4 + 36]
%define arg10 [esp + push_num*4 + 40]
%define arg11 [esp + push_num*4 + 44]
%define arg12 [esp + push_num*4 + 48]

%define arg1d arg1
%define arg2d arg2
%define arg3d arg3
%define arg4d arg4
%define arg5d arg5
%define arg6d arg6
%define arg7d arg7
%define arg8d arg8
%define arg9d arg9
%define arg10d arg10
%define arg11d arg11
%define arg12d arg12

%define r0 eax
%define r1 ecx
%define r2 edx
%define r3 ebx
%define r4 esi
%define r5 edi
%define r6 ebp
%define r7 esp

%define r0d eax
%define r1d ecx
%define r2d edx
%define r3d ebx
%define r4d esi
%define r5d edi
%define r6d ebp

%define r0w ax
%define r1w cx
%define r2w dx
%define r3w bx
%define r4w si
%define r6w bp

%define r0b al
%define r1b cl
%define r2b dl
%define r3b bl

%define  PUSHRFLAGS     pushfd
%define  POPRFLAGS      popfd
%define  retrq          eax      ; 32 bit mode do not support 64 bits regesters
%define  retrd          eax

%endif

%macro LOAD_PARA 2
    mov %1, %2
%endmacro

%macro LOAD_1_PARA 0
    %ifdef X86_32
        mov r0, [esp + push_num*4 + 4]
    %endif
%endmacro

%macro LOAD_2_PARA 0
    %ifdef X86_32
        mov r0, [esp + push_num*4 + 4]
        mov r1, [esp + push_num*4 + 8]
    %endif
%endmacro

%macro LOAD_3_PARA 0
    %ifdef X86_32
        mov r0, [esp + push_num*4 + 4]
        mov r1, [esp + push_num*4 + 8]
        mov r2, [esp + push_num*4 + 12]
    %endif
%endmacro

%macro LOAD_4_PARA 0
    %ifdef X86_32
        push r3
        %assign  push_num push_num+1
        mov r0, [esp + push_num*4 + 4]
        mov r1, [esp + push_num*4 + 8]
        mov r2, [esp + push_num*4 + 12]
        mov r3, [esp + push_num*4 + 16]
    %endif
%endmacro

%macro LOAD_5_PARA 0
    %ifdef X86_32
        push r3
        push r4
        %assign  push_num push_num+2
        mov r0, [esp + push_num*4 + 4]
        mov r1, [esp + push_num*4 + 8]
        mov r2, [esp + push_num*4 + 12]
        mov r3, [esp + push_num*4 + 16]
        mov r4, [esp + push_num*4 + 20]
    %elifdef WIN64
        mov r4, [rsp + push_num*8 + 40]
    %endif
%endmacro

%macro LOAD_6_PARA 0
    %ifdef X86_32
        push r3
        push r4
        push r5
        %assign  push_num push_num+3
        mov r0, [esp + push_num*4 + 4]
        mov r1, [esp + push_num*4 + 8]
        mov r2, [esp + push_num*4 + 12]
        mov r3, [esp + push_num*4 + 16]
        mov r4, [esp + push_num*4 + 20]
        mov r5, [esp + push_num*4 + 24]
    %elifdef WIN64
        mov r4, [rsp + push_num*8 + 40]
        mov r5, [rsp + push_num*8 + 48]
    %endif
%endmacro

%macro LOAD_7_PARA 0
    %ifdef X86_32
        push r3
        push r4
        push r5
        push r6
        %assign  push_num push_num+4
        mov r0, [esp + push_num*4 + 4]
        mov r1, [esp + push_num*4 + 8]
        mov r2, [esp + push_num*4 + 12]
        mov r3, [esp + push_num*4 + 16]
        mov r4, [esp + push_num*4 + 20]
        mov r5, [esp + push_num*4 + 24]
        mov r6, [esp + push_num*4 + 28]
    %elifdef WIN64
        mov r4, [rsp + push_num*8 + 40]
        mov r5, [rsp + push_num*8 + 48]
        mov r6, [rsp + push_num*8 + 56]
    %elifdef UNIX64
        mov r6, [rsp + push_num*8 + 8]
    %endif
%endmacro



%macro LOAD_4_PARA_POP 0
    %ifdef X86_32
        pop r3
    %endif
%endmacro

%macro LOAD_5_PARA_POP 0
    %ifdef X86_32
        pop r4
        pop r3
    %endif
%endmacro

%macro LOAD_6_PARA_POP 0
    %ifdef X86_32
        pop r5
        pop r4
        pop r3
    %endif
%endmacro

%macro LOAD_7_PARA_POP 0
    %ifdef X86_32
        pop r6
        pop r5
        pop r4
        pop r3
    %endif
%endmacro

%macro PUSH_XMM 1
    %ifdef WIN64
        %assign xmm_num_regs %1
        %if xmm_num_regs > 6
            %ifdef push_num
                %assign push_num push_num+2*(%1-6)
            %endif
            sub rsp, 16*(%1 - 6)
            movdqu [rsp], xmm6
        %endif
        %if xmm_num_regs > 7
            movdqu [rsp+16], xmm7
        %endif
        %if xmm_num_regs > 8
            movdqu [rsp+32], xmm8
        %endif
        %if xmm_num_regs > 9
            movdqu [rsp+48], xmm9
        %endif
        %if xmm_num_regs > 10
            movdqu [rsp+64], xmm10
        %endif
        %if xmm_num_regs > 11
            movdqu [rsp+80], xmm11
        %endif
        %if xmm_num_regs > 12
            movdqu [rsp+96], xmm12
        %endif
        %if xmm_num_regs > 13
            movdqu [rsp+112], xmm13
        %endif
        %if xmm_num_regs > 14
            movdqu [rsp+128], xmm14
        %endif
        %if xmm_num_regs > 15
            movdqu [rsp+144], xmm15
        %endif
    %endif
%endmacro

%macro POP_XMM 0
    %ifdef WIN64
        %if xmm_num_regs > 15
            movdqu xmm15, [rsp+144]
        %endif
        %if xmm_num_regs > 14
            movdqu xmm14, [rsp+128]
        %endif
        %if xmm_num_regs > 13
            movdqu xmm13, [rsp+112]
        %endif
        %if xmm_num_regs > 12
            movdqu xmm12, [rsp+96]
        %endif
        %if xmm_num_regs > 11
            movdqu xmm11, [rsp+80]
        %endif
        %if xmm_num_regs > 10
            movdqu xmm10, [rsp+64]
        %endif
        %if xmm_num_regs > 9
            movdqu xmm9, [rsp+48]
        %endif
        %if xmm_num_regs > 8
            movdqu xmm8, [rsp+32]
        %endif
        %if xmm_num_regs > 7
            movdqu xmm7, [rsp+16]
        %endif
        %if xmm_num_regs > 6
            movdqu xmm6, [rsp]
            add rsp, 16*(xmm_num_regs - 6)
        %endif
    %endif
%endmacro

%macro SIGN_EXTENSION 2
    %ifndef X86_32
        movsxd %1, %2
    %endif
%endmacro

%macro SIGN_EXTENSIONW 2
    %ifndef X86_32
        movsx %1, %2
    %endif
%endmacro

%macro ZERO_EXTENSION 1
    %ifndef X86_32
        mov dword %1, %1
    %endif
%endmacro

%macro WELS_EXTERN 1
    ALIGN 16, nop
    %ifdef PREFIX
        %ifdef WELS_PRIVATE_EXTERN
            global _%1: WELS_PRIVATE_EXTERN
        %else
            global _%1
        %endif
        %define %1 _%1
    %else
        %ifdef WELS_PRIVATE_EXTERN
            global %1: WELS_PRIVATE_EXTERN
        %else
            global %1
        %endif
    %endif
    %1:
%endmacro

%macro WELS_AbsW 2
    pxor        %2, %2
    psubw       %2, %1
    pmaxsw      %1, %2
%endmacro

%macro MMX_XSwap  4
    movq        %4, %2
    punpckh%1   %4, %3
    punpckl%1   %2, %3
%endmacro

; pOut mm1, mm4, mm5, mm3
%macro MMX_Trans4x4W 5
    MMX_XSwap wd, %1, %2, %5
    MMX_XSwap wd, %3, %4, %2
    MMX_XSwap dq, %1, %3, %4
    MMX_XSwap dq, %5, %2, %3
%endmacro

;for TRANSPOSE
%macro SSE2_XSawp 4
    movdqa      %4, %2
    punpckl%1   %2, %3
    punpckh%1   %4, %3
%endmacro

; in: xmm1, xmm2, xmm3, xmm4  pOut:  xmm1, xmm4, xmm5, mm3
%macro SSE2_Trans4x4D 5
    SSE2_XSawp dq,  %1, %2, %5
    SSE2_XSawp dq,  %3, %4, %2
    SSE2_XSawp qdq, %1, %3, %4
    SSE2_XSawp qdq, %5, %2, %3
%endmacro

;in: xmm0, xmm1, xmm2, xmm3  pOut:  xmm0, xmm1, xmm3, xmm4
%macro SSE2_TransTwo4x4W 5
    SSE2_XSawp wd,  %1, %2, %5
    SSE2_XSawp wd,  %3, %4, %2
    SSE2_XSawp dq,  %1, %3, %4
    SSE2_XSawp dq,  %5, %2, %3
    SSE2_XSawp qdq, %1, %5, %2
    SSE2_XSawp qdq, %4, %3, %5
%endmacro

;in:  m1, m2, m3, m4, m5, m6, m7, m8
;pOut: m5, m3, m4, m8, m6, m2, m7, m1
%macro SSE2_TransTwo8x8B 9
    movdqa  %9,     %8
    SSE2_XSawp bw,  %1, %2, %8
    SSE2_XSawp bw,  %3, %4, %2
    SSE2_XSawp bw,  %5, %6, %4
    movdqa  %6, %9
    movdqa  %9, %4
    SSE2_XSawp bw,  %7, %6, %4

    SSE2_XSawp wd,  %1, %3, %6
    SSE2_XSawp wd,  %8, %2, %3
    SSE2_XSawp wd,  %5, %7, %2
    movdqa  %7, %9
    movdqa  %9, %3
    SSE2_XSawp wd,  %7, %4, %3

    SSE2_XSawp dq,  %1, %5, %4
    SSE2_XSawp dq,  %6, %2, %5
    SSE2_XSawp dq,  %8, %7, %2
    movdqa  %7, %9
    movdqa  %9, %5
    SSE2_XSawp dq,  %7, %3, %5

    SSE2_XSawp qdq,  %1, %8, %3
    SSE2_XSawp qdq,  %4, %2, %8
    SSE2_XSawp qdq,  %6, %7, %2
    movdqa  %7, %9
    movdqa  %9, %1
    SSE2_XSawp qdq,  %7, %5, %1
    movdqa  %5, %9
%endmacro

;xmm0, xmm6, xmm7, [eax], [ecx]
;xmm7 = 0, eax = pix1, ecx = pix2, xmm0 save the result
%macro SSE2_LoadDiff8P 5
    movq         %1, %4
    punpcklbw    %1, %3
    movq         %2, %5
    punpcklbw    %2, %3
    psubw        %1, %2
%endmacro

; m2 = m1 + m2, m1 = m1 - m2
%macro SSE2_SumSub 3
    movdqa  %3, %2
    paddw   %2, %1
    psubw   %1, %3
%endmacro


%macro butterfly_1to16_sse      3       ; xmm? for dst, xmm? for tmp, one byte for pSrc [generic register name: a/b/c/d]
    mov %3h, %3l
    movd %1, e%3x           ; i.e, 1% = eax (=b0)
    pshuflw %2, %1, 00h     ; ..., b0 b0 b0 b0 b0 b0 b0 b0
    pshufd %1, %2, 00h      ; b0 b0 b0 b0, b0 b0 b0 b0, b0 b0 b0 b0, b0 b0 b0 b0
%endmacro

;copy a dw into a xmm for 8 times
%macro SSE2_Copy8Times 2
    movd    %1, %2
    punpcklwd %1, %1
    pshufd  %1,     %1,     0
%endmacro

;copy a db into a xmm for 16 times
%macro SSE2_Copy16Times 2
    movd            %1, %2
    pshuflw         %1, %1, 0
    punpcklqdq      %1, %1
    packuswb        %1,     %1
%endmacro



;***********************************************************************
;preprocessor constants
;***********************************************************************
;dw 32,32,32,32,32,32,32,32 for xmm
;dw 32,32,32,32 for mm
%macro WELS_DW32 1
    pcmpeqw %1,%1
    psrlw %1,15
    psllw %1,5
%endmacro

;dw 1, 1, 1, 1, 1, 1, 1, 1 for xmm
;dw 1, 1, 1, 1 for mm
%macro WELS_DW1 1
    pcmpeqw %1,%1
    psrlw %1,15
%endmacro

;all 0 for xmm and mm
%macro WELS_Zero 1
    pxor %1, %1
%endmacro

;dd 1, 1, 1, 1 for xmm
;dd 1, 1 for mm
%macro WELS_DD1 1
    pcmpeqw %1,%1
    psrld %1,31
%endmacro

;dB 1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1
%macro WELS_DB1 1
    pcmpeqw %1,%1
    psrlw %1,15
    packuswb %1,%1
%endmacro

%macro WELS_DW1_VEX 1
    vpcmpeqw %1, %1, %1
    vpsrlw   %1, %1, 15
%endmacro

%macro WELS_DW32_VEX 1
    vpcmpeqw %1, %1, %1
    vpsrlw   %1, %1, 15
    vpsllw   %1, %1,  5
%endmacro

%macro WELS_DW32767_VEX 1
    vpcmpeqw %1, %1, %1
    vpsrlw   %1, %1,  1
%endmacro


;***********************************************************************
; Utility macros for X86_32 PIC support
;***********************************************************************

; Used internally by other macros.
%macro INIT_X86_32_PIC_ 2
%ifdef X86_32_PICASM
    %xdefine pic_ptr %1
    %xdefine pic_ptr_preserve %2
  %if pic_ptr_preserve
    %assign push_num push_num+1
    push            pic_ptr
  %endif
    call            %%get_pc
%%pic_refpoint:
    jmp             %%pic_init_done
%%get_pc:
    mov             pic_ptr, [esp]
    ret
%%pic_init_done:
    %define pic(data_addr) (pic_ptr+(data_addr)-%%pic_refpoint)
%else
    %define pic(data_addr) (data_addr)
%endif
%endmacro

; Get program counter and define a helper macro "pic(addr)" to convert absolute
; addresses to program counter-relative addresses if X86_32_PICASM is defined.
; Otherwise define "pic(addr)" as an identity function.
; %1=register to store PC/EIP in.
%macro INIT_X86_32_PIC 1
    INIT_X86_32_PIC_ %1, 1
%endmacro

; Equivalent as above, but without preserving the value of the register argument.
%macro INIT_X86_32_PIC_NOPRESERVE 1
    INIT_X86_32_PIC_ %1, 0
%endmacro

; Clean up after INIT_X86_32_PIC.
; Restore the register used to hold PC/EIP if applicable, and undefine defines.
%macro DEINIT_X86_32_PIC 0
%ifdef X86_32_PICASM
  %if pic_ptr_preserve
    pop             pic_ptr
    %assign push_num push_num-1
  %endif
    %undef pic_ptr
    %undef pic_ptr_preserve
%endif
    %undef pic
%endmacro

; Equivalent as above, but without undefining. Useful for functions with
; multiple epilogues.
%macro DEINIT_X86_32_PIC_KEEPDEF 0
%ifdef X86_32_PICASM
  %if pic_ptr_preserve
    pop             pic_ptr
    %assign push_num push_num-1
  %endif
%endif
%endmacro
