;
;  Copyright (c) 2010 The WebM project authors. All Rights Reserved.
;
;  Use of this source code is governed by a BSD-style license
;  that can be found in the LICENSE file in the root of the source
;  tree. An additional intellectual property rights grant can be found
;  in the file PATENTS.  All contributing project authors may
;  be found in the AUTHORS file in the root of the source tree.
;

%include "vpx_ports/x86_abi_support.asm"

%macro STACK_FRAME_CREATE_X3 0
%if ABI_IS_32BIT
  %define     src_ptr       rsi
  %define     src_stride    rax
  %define     ref_ptr       rdi
  %define     ref_stride    rdx
  %define     end_ptr       rcx
  %define     ret_var       rbx
  %define     result_ptr    arg(4)
  %define     height        dword ptr arg(4)
    push        rbp
    mov         rbp,        rsp
    push        rsi
    push        rdi
    push        rbx

    mov         rsi,        arg(0)              ; src_ptr
    mov         rdi,        arg(2)              ; ref_ptr

    movsxd      rax,        dword ptr arg(1)    ; src_stride
    movsxd      rdx,        dword ptr arg(3)    ; ref_stride
%else
  %if LIBVPX_YASM_WIN64
    SAVE_XMM 7, u
    %define     src_ptr     rcx
    %define     src_stride  rdx
    %define     ref_ptr     r8
    %define     ref_stride  r9
    %define     end_ptr     r10
    %define     ret_var     r11
    %define     result_ptr  [rsp+xmm_stack_space+8+4*8]
    %define     height      dword ptr [rsp+xmm_stack_space+8+4*8]
  %else
    %define     src_ptr     rdi
    %define     src_stride  rsi
    %define     ref_ptr     rdx
    %define     ref_stride  rcx
    %define     end_ptr     r9
    %define     ret_var     r10
    %define     result_ptr  r8
    %define     height      r8
  %endif
%endif

%endmacro

%macro STACK_FRAME_DESTROY_X3 0
  %define     src_ptr
  %define     src_stride
  %define     ref_ptr
  %define     ref_stride
  %define     end_ptr
  %define     ret_var
  %define     result_ptr
  %define     height

%if ABI_IS_32BIT
    pop         rbx
    pop         rdi
    pop         rsi
    pop         rbp
%else
  %if LIBVPX_YASM_WIN64
    RESTORE_XMM
  %endif
%endif
    ret
%endmacro

%macro PROCESS_16X2X3 5
%if %1==0
        movdqa          xmm0,       XMMWORD PTR [%2]
        lddqu           xmm5,       XMMWORD PTR [%3]
        lddqu           xmm6,       XMMWORD PTR [%3+1]
        lddqu           xmm7,       XMMWORD PTR [%3+2]

        psadbw          xmm5,       xmm0
        psadbw          xmm6,       xmm0
        psadbw          xmm7,       xmm0
%else
        movdqa          xmm0,       XMMWORD PTR [%2]
        lddqu           xmm1,       XMMWORD PTR [%3]
        lddqu           xmm2,       XMMWORD PTR [%3+1]
        lddqu           xmm3,       XMMWORD PTR [%3+2]

        psadbw          xmm1,       xmm0
        psadbw          xmm2,       xmm0
        psadbw          xmm3,       xmm0

        paddw           xmm5,       xmm1
        paddw           xmm6,       xmm2
        paddw           xmm7,       xmm3
%endif
        movdqa          xmm0,       XMMWORD PTR [%2+%4]
        lddqu           xmm1,       XMMWORD PTR [%3+%5]
        lddqu           xmm2,       XMMWORD PTR [%3+%5+1]
        lddqu           xmm3,       XMMWORD PTR [%3+%5+2]

%if %1==0 || %1==1
        lea             %2,         [%2+%4*2]
        lea             %3,         [%3+%5*2]
%endif

        psadbw          xmm1,       xmm0
        psadbw          xmm2,       xmm0
        psadbw          xmm3,       xmm0

        paddw           xmm5,       xmm1
        paddw           xmm6,       xmm2
        paddw           xmm7,       xmm3
%endmacro

%macro PROCESS_8X2X3 5
%if %1==0
        movq            mm0,       QWORD PTR [%2]
        movq            mm5,       QWORD PTR [%3]
        movq            mm6,       QWORD PTR [%3+1]
        movq            mm7,       QWORD PTR [%3+2]

        psadbw          mm5,       mm0
        psadbw          mm6,       mm0
        psadbw          mm7,       mm0
%else
        movq            mm0,       QWORD PTR [%2]
        movq            mm1,       QWORD PTR [%3]
        movq            mm2,       QWORD PTR [%3+1]
        movq            mm3,       QWORD PTR [%3+2]

        psadbw          mm1,       mm0
        psadbw          mm2,       mm0
        psadbw          mm3,       mm0

        paddw           mm5,       mm1
        paddw           mm6,       mm2
        paddw           mm7,       mm3
%endif
        movq            mm0,       QWORD PTR [%2+%4]
        movq            mm1,       QWORD PTR [%3+%5]
        movq            mm2,       QWORD PTR [%3+%5+1]
        movq            mm3,       QWORD PTR [%3+%5+2]

%if %1==0 || %1==1
        lea             %2,        [%2+%4*2]
        lea             %3,        [%3+%5*2]
%endif

        psadbw          mm1,       mm0
        psadbw          mm2,       mm0
        psadbw          mm3,       mm0

        paddw           mm5,       mm1
        paddw           mm6,       mm2
        paddw           mm7,       mm3
%endmacro

SECTION .text

;void int vpx_sad16x16x3_sse3(
;    unsigned char *src_ptr,
;    int  src_stride,
;    unsigned char *ref_ptr,
;    int  ref_stride,
;    int  *results)
global sym(vpx_sad16x16x3_sse3) PRIVATE
sym(vpx_sad16x16x3_sse3):

    STACK_FRAME_CREATE_X3

        PROCESS_16X2X3 0, src_ptr, ref_ptr, src_stride, ref_stride
        PROCESS_16X2X3 1, src_ptr, ref_ptr, src_stride, ref_stride
        PROCESS_16X2X3 1, src_ptr, ref_ptr, src_stride, ref_stride
        PROCESS_16X2X3 1, src_ptr, ref_ptr, src_stride, ref_stride
        PROCESS_16X2X3 1, src_ptr, ref_ptr, src_stride, ref_stride
        PROCESS_16X2X3 1, src_ptr, ref_ptr, src_stride, ref_stride
        PROCESS_16X2X3 1, src_ptr, ref_ptr, src_stride, ref_stride
        PROCESS_16X2X3 2, src_ptr, ref_ptr, src_stride, ref_stride

        mov             rcx,        result_ptr

        movq            xmm0,       xmm5
        psrldq          xmm5,       8

        paddw           xmm0,       xmm5
        movd            [rcx],      xmm0
;-
        movq            xmm0,       xmm6
        psrldq          xmm6,       8

        paddw           xmm0,       xmm6
        movd            [rcx+4],    xmm0
;-
        movq            xmm0,       xmm7
        psrldq          xmm7,       8

        paddw           xmm0,       xmm7
        movd            [rcx+8],    xmm0

    STACK_FRAME_DESTROY_X3

;void int vpx_sad16x8x3_sse3(
;    unsigned char *src_ptr,
;    int  src_stride,
;    unsigned char *ref_ptr,
;    int  ref_stride,
;    int  *results)
global sym(vpx_sad16x8x3_sse3) PRIVATE
sym(vpx_sad16x8x3_sse3):

    STACK_FRAME_CREATE_X3

        PROCESS_16X2X3 0, src_ptr, ref_ptr, src_stride, ref_stride
        PROCESS_16X2X3 1, src_ptr, ref_ptr, src_stride, ref_stride
        PROCESS_16X2X3 1, src_ptr, ref_ptr, src_stride, ref_stride
        PROCESS_16X2X3 2, src_ptr, ref_ptr, src_stride, ref_stride

        mov             rcx,        result_ptr

        movq            xmm0,       xmm5
        psrldq          xmm5,       8

        paddw           xmm0,       xmm5
        movd            [rcx],      xmm0
;-
        movq            xmm0,       xmm6
        psrldq          xmm6,       8

        paddw           xmm0,       xmm6
        movd            [rcx+4],    xmm0
;-
        movq            xmm0,       xmm7
        psrldq          xmm7,       8

        paddw           xmm0,       xmm7
        movd            [rcx+8],    xmm0

    STACK_FRAME_DESTROY_X3

;void int vpx_sad8x16x3_sse3(
;    unsigned char *src_ptr,
;    int  src_stride,
;    unsigned char *ref_ptr,
;    int  ref_stride,
;    int  *results)
global sym(vpx_sad8x16x3_sse3) PRIVATE
sym(vpx_sad8x16x3_sse3):

    STACK_FRAME_CREATE_X3

        PROCESS_8X2X3 0, src_ptr, ref_ptr, src_stride, ref_stride
        PROCESS_8X2X3 1, src_ptr, ref_ptr, src_stride, ref_stride
        PROCESS_8X2X3 1, src_ptr, ref_ptr, src_stride, ref_stride
        PROCESS_8X2X3 1, src_ptr, ref_ptr, src_stride, ref_stride
        PROCESS_8X2X3 1, src_ptr, ref_ptr, src_stride, ref_stride
        PROCESS_8X2X3 1, src_ptr, ref_ptr, src_stride, ref_stride
        PROCESS_8X2X3 1, src_ptr, ref_ptr, src_stride, ref_stride
        PROCESS_8X2X3 2, src_ptr, ref_ptr, src_stride, ref_stride

        mov             rcx,        result_ptr

        punpckldq       mm5,        mm6

        movq            [rcx],      mm5
        movd            [rcx+8],    mm7

    STACK_FRAME_DESTROY_X3

;void int vpx_sad8x8x3_sse3(
;    unsigned char *src_ptr,
;    int  src_stride,
;    unsigned char *ref_ptr,
;    int  ref_stride,
;    int  *results)
global sym(vpx_sad8x8x3_sse3) PRIVATE
sym(vpx_sad8x8x3_sse3):

    STACK_FRAME_CREATE_X3

        PROCESS_8X2X3 0, src_ptr, ref_ptr, src_stride, ref_stride
        PROCESS_8X2X3 1, src_ptr, ref_ptr, src_stride, ref_stride
        PROCESS_8X2X3 1, src_ptr, ref_ptr, src_stride, ref_stride
        PROCESS_8X2X3 2, src_ptr, ref_ptr, src_stride, ref_stride

        mov             rcx,        result_ptr

        punpckldq       mm5,        mm6

        movq            [rcx],      mm5
        movd            [rcx+8],    mm7

    STACK_FRAME_DESTROY_X3

;void int vpx_sad4x4x3_sse3(
;    unsigned char *src_ptr,
;    int  src_stride,
;    unsigned char *ref_ptr,
;    int  ref_stride,
;    int  *results)
global sym(vpx_sad4x4x3_sse3) PRIVATE
sym(vpx_sad4x4x3_sse3):

    STACK_FRAME_CREATE_X3

        movd            mm0,        DWORD PTR [src_ptr]
        movd            mm1,        DWORD PTR [ref_ptr]

        movd            mm2,        DWORD PTR [src_ptr+src_stride]
        movd            mm3,        DWORD PTR [ref_ptr+ref_stride]

        punpcklbw       mm0,        mm2
        punpcklbw       mm1,        mm3

        movd            mm4,        DWORD PTR [ref_ptr+1]
        movd            mm5,        DWORD PTR [ref_ptr+2]

        movd            mm2,        DWORD PTR [ref_ptr+ref_stride+1]
        movd            mm3,        DWORD PTR [ref_ptr+ref_stride+2]

        psadbw          mm1,        mm0

        punpcklbw       mm4,        mm2
        punpcklbw       mm5,        mm3

        psadbw          mm4,        mm0
        psadbw          mm5,        mm0

        lea             src_ptr,    [src_ptr+src_stride*2]
        lea             ref_ptr,    [ref_ptr+ref_stride*2]

        movd            mm0,        DWORD PTR [src_ptr]
        movd            mm2,        DWORD PTR [ref_ptr]

        movd            mm3,        DWORD PTR [src_ptr+src_stride]
        movd            mm6,        DWORD PTR [ref_ptr+ref_stride]

        punpcklbw       mm0,        mm3
        punpcklbw       mm2,        mm6

        movd            mm3,        DWORD PTR [ref_ptr+1]
        movd            mm7,        DWORD PTR [ref_ptr+2]

        psadbw          mm2,        mm0

        paddw           mm1,        mm2

        movd            mm2,        DWORD PTR [ref_ptr+ref_stride+1]
        movd            mm6,        DWORD PTR [ref_ptr+ref_stride+2]

        punpcklbw       mm3,        mm2
        punpcklbw       mm7,        mm6

        psadbw          mm3,        mm0
        psadbw          mm7,        mm0

        paddw           mm3,        mm4
        paddw           mm7,        mm5

        mov             rcx,        result_ptr

        punpckldq       mm1,        mm3

        movq            [rcx],      mm1
        movd            [rcx+8],    mm7

    STACK_FRAME_DESTROY_X3
