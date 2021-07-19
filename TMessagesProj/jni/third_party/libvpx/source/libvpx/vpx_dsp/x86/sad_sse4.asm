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

%macro PROCESS_16X2X8 1
%if %1
        movdqa          xmm0,       XMMWORD PTR [rsi]
        movq            xmm1,       MMWORD PTR [rdi]
        movq            xmm3,       MMWORD PTR [rdi+8]
        movq            xmm2,       MMWORD PTR [rdi+16]
        punpcklqdq      xmm1,       xmm3
        punpcklqdq      xmm3,       xmm2

        movdqa          xmm2,       xmm1
        mpsadbw         xmm1,       xmm0,  0x0
        mpsadbw         xmm2,       xmm0,  0x5

        psrldq          xmm0,       8

        movdqa          xmm4,       xmm3
        mpsadbw         xmm3,       xmm0,  0x0
        mpsadbw         xmm4,       xmm0,  0x5

        paddw           xmm1,       xmm2
        paddw           xmm1,       xmm3
        paddw           xmm1,       xmm4
%else
        movdqa          xmm0,       XMMWORD PTR [rsi]
        movq            xmm5,       MMWORD PTR [rdi]
        movq            xmm3,       MMWORD PTR [rdi+8]
        movq            xmm2,       MMWORD PTR [rdi+16]
        punpcklqdq      xmm5,       xmm3
        punpcklqdq      xmm3,       xmm2

        movdqa          xmm2,       xmm5
        mpsadbw         xmm5,       xmm0,  0x0
        mpsadbw         xmm2,       xmm0,  0x5

        psrldq          xmm0,       8

        movdqa          xmm4,       xmm3
        mpsadbw         xmm3,       xmm0,  0x0
        mpsadbw         xmm4,       xmm0,  0x5

        paddw           xmm5,       xmm2
        paddw           xmm5,       xmm3
        paddw           xmm5,       xmm4

        paddw           xmm1,       xmm5
%endif
        movdqa          xmm0,       XMMWORD PTR [rsi + rax]
        movq            xmm5,       MMWORD PTR [rdi+ rdx]
        movq            xmm3,       MMWORD PTR [rdi+ rdx+8]
        movq            xmm2,       MMWORD PTR [rdi+ rdx+16]
        punpcklqdq      xmm5,       xmm3
        punpcklqdq      xmm3,       xmm2

        lea             rsi,        [rsi+rax*2]
        lea             rdi,        [rdi+rdx*2]

        movdqa          xmm2,       xmm5
        mpsadbw         xmm5,       xmm0,  0x0
        mpsadbw         xmm2,       xmm0,  0x5

        psrldq          xmm0,       8
        movdqa          xmm4,       xmm3
        mpsadbw         xmm3,       xmm0,  0x0
        mpsadbw         xmm4,       xmm0,  0x5

        paddw           xmm5,       xmm2
        paddw           xmm5,       xmm3
        paddw           xmm5,       xmm4

        paddw           xmm1,       xmm5
%endmacro

%macro PROCESS_8X2X8 1
%if %1
        movq            xmm0,       MMWORD PTR [rsi]
        movq            xmm1,       MMWORD PTR [rdi]
        movq            xmm3,       MMWORD PTR [rdi+8]
        punpcklqdq      xmm1,       xmm3

        movdqa          xmm2,       xmm1
        mpsadbw         xmm1,       xmm0,  0x0
        mpsadbw         xmm2,       xmm0,  0x5
        paddw           xmm1,       xmm2
%else
        movq            xmm0,       MMWORD PTR [rsi]
        movq            xmm5,       MMWORD PTR [rdi]
        movq            xmm3,       MMWORD PTR [rdi+8]
        punpcklqdq      xmm5,       xmm3

        movdqa          xmm2,       xmm5
        mpsadbw         xmm5,       xmm0,  0x0
        mpsadbw         xmm2,       xmm0,  0x5
        paddw           xmm5,       xmm2

        paddw           xmm1,       xmm5
%endif
        movq            xmm0,       MMWORD PTR [rsi + rax]
        movq            xmm5,       MMWORD PTR [rdi+ rdx]
        movq            xmm3,       MMWORD PTR [rdi+ rdx+8]
        punpcklqdq      xmm5,       xmm3

        lea             rsi,        [rsi+rax*2]
        lea             rdi,        [rdi+rdx*2]

        movdqa          xmm2,       xmm5
        mpsadbw         xmm5,       xmm0,  0x0
        mpsadbw         xmm2,       xmm0,  0x5
        paddw           xmm5,       xmm2

        paddw           xmm1,       xmm5
%endmacro

%macro PROCESS_4X2X8 1
%if %1
        movd            xmm0,       [rsi]
        movq            xmm1,       MMWORD PTR [rdi]
        movq            xmm3,       MMWORD PTR [rdi+8]
        punpcklqdq      xmm1,       xmm3

        mpsadbw         xmm1,       xmm0,  0x0
%else
        movd            xmm0,       [rsi]
        movq            xmm5,       MMWORD PTR [rdi]
        movq            xmm3,       MMWORD PTR [rdi+8]
        punpcklqdq      xmm5,       xmm3

        mpsadbw         xmm5,       xmm0,  0x0

        paddw           xmm1,       xmm5
%endif
        movd            xmm0,       [rsi + rax]
        movq            xmm5,       MMWORD PTR [rdi+ rdx]
        movq            xmm3,       MMWORD PTR [rdi+ rdx+8]
        punpcklqdq      xmm5,       xmm3

        lea             rsi,        [rsi+rax*2]
        lea             rdi,        [rdi+rdx*2]

        mpsadbw         xmm5,       xmm0,  0x0

        paddw           xmm1,       xmm5
%endmacro

%macro WRITE_AS_INTS 0
    mov             rdi,        arg(4)           ;Results
    pxor            xmm0, xmm0
    movdqa          xmm2, xmm1
    punpcklwd       xmm1, xmm0
    punpckhwd       xmm2, xmm0

    movdqa          [rdi],    xmm1
    movdqa          [rdi + 16],    xmm2
%endmacro

SECTION .text

;void vpx_sad16x16x8_sse4_1(
;    const unsigned char *src_ptr,
;    int  src_stride,
;    const unsigned char *ref_ptr,
;    int  ref_stride,
;    unsigned short *sad_array);
globalsym(vpx_sad16x16x8_sse4_1)
sym(vpx_sad16x16x8_sse4_1):
    push        rbp
    mov         rbp, rsp
    SHADOW_ARGS_TO_STACK 5
    push        rsi
    push        rdi
    ; end prolog

    mov             rsi,        arg(0)           ;src_ptr
    mov             rdi,        arg(2)           ;ref_ptr

    movsxd          rax,        dword ptr arg(1) ;src_stride
    movsxd          rdx,        dword ptr arg(3) ;ref_stride

    PROCESS_16X2X8 1
    PROCESS_16X2X8 0
    PROCESS_16X2X8 0
    PROCESS_16X2X8 0
    PROCESS_16X2X8 0
    PROCESS_16X2X8 0
    PROCESS_16X2X8 0
    PROCESS_16X2X8 0

    WRITE_AS_INTS

    ; begin epilog
    pop         rdi
    pop         rsi
    UNSHADOW_ARGS
    pop         rbp
    ret


;void vpx_sad16x8x8_sse4_1(
;    const unsigned char *src_ptr,
;    int  src_stride,
;    const unsigned char *ref_ptr,
;    int  ref_stride,
;    unsigned short *sad_array
;);
globalsym(vpx_sad16x8x8_sse4_1)
sym(vpx_sad16x8x8_sse4_1):
    push        rbp
    mov         rbp, rsp
    SHADOW_ARGS_TO_STACK 5
    push        rsi
    push        rdi
    ; end prolog

    mov             rsi,        arg(0)           ;src_ptr
    mov             rdi,        arg(2)           ;ref_ptr

    movsxd          rax,        dword ptr arg(1) ;src_stride
    movsxd          rdx,        dword ptr arg(3) ;ref_stride

    PROCESS_16X2X8 1
    PROCESS_16X2X8 0
    PROCESS_16X2X8 0
    PROCESS_16X2X8 0

    WRITE_AS_INTS

    ; begin epilog
    pop         rdi
    pop         rsi
    UNSHADOW_ARGS
    pop         rbp
    ret


;void vpx_sad8x8x8_sse4_1(
;    const unsigned char *src_ptr,
;    int  src_stride,
;    const unsigned char *ref_ptr,
;    int  ref_stride,
;    unsigned short *sad_array
;);
globalsym(vpx_sad8x8x8_sse4_1)
sym(vpx_sad8x8x8_sse4_1):
    push        rbp
    mov         rbp, rsp
    SHADOW_ARGS_TO_STACK 5
    push        rsi
    push        rdi
    ; end prolog

    mov             rsi,        arg(0)           ;src_ptr
    mov             rdi,        arg(2)           ;ref_ptr

    movsxd          rax,        dword ptr arg(1) ;src_stride
    movsxd          rdx,        dword ptr arg(3) ;ref_stride

    PROCESS_8X2X8 1
    PROCESS_8X2X8 0
    PROCESS_8X2X8 0
    PROCESS_8X2X8 0

    WRITE_AS_INTS

    ; begin epilog
    pop         rdi
    pop         rsi
    UNSHADOW_ARGS
    pop         rbp
    ret


;void vpx_sad8x16x8_sse4_1(
;    const unsigned char *src_ptr,
;    int  src_stride,
;    const unsigned char *ref_ptr,
;    int  ref_stride,
;    unsigned short *sad_array
;);
globalsym(vpx_sad8x16x8_sse4_1)
sym(vpx_sad8x16x8_sse4_1):
    push        rbp
    mov         rbp, rsp
    SHADOW_ARGS_TO_STACK 5
    push        rsi
    push        rdi
    ; end prolog

    mov             rsi,        arg(0)           ;src_ptr
    mov             rdi,        arg(2)           ;ref_ptr

    movsxd          rax,        dword ptr arg(1) ;src_stride
    movsxd          rdx,        dword ptr arg(3) ;ref_stride

    PROCESS_8X2X8 1
    PROCESS_8X2X8 0
    PROCESS_8X2X8 0
    PROCESS_8X2X8 0
    PROCESS_8X2X8 0
    PROCESS_8X2X8 0
    PROCESS_8X2X8 0
    PROCESS_8X2X8 0

    WRITE_AS_INTS

    ; begin epilog
    pop         rdi
    pop         rsi
    UNSHADOW_ARGS
    pop         rbp
    ret


;void vpx_sad4x4x8_sse4_1(
;    const unsigned char *src_ptr,
;    int  src_stride,
;    const unsigned char *ref_ptr,
;    int  ref_stride,
;    unsigned short *sad_array
;);
globalsym(vpx_sad4x4x8_sse4_1)
sym(vpx_sad4x4x8_sse4_1):
    push        rbp
    mov         rbp, rsp
    SHADOW_ARGS_TO_STACK 5
    push        rsi
    push        rdi
    ; end prolog

    mov             rsi,        arg(0)           ;src_ptr
    mov             rdi,        arg(2)           ;ref_ptr

    movsxd          rax,        dword ptr arg(1) ;src_stride
    movsxd          rdx,        dword ptr arg(3) ;ref_stride

    PROCESS_4X2X8 1
    PROCESS_4X2X8 0

    WRITE_AS_INTS

    ; begin epilog
    pop         rdi
    pop         rsi
    UNSHADOW_ARGS
    pop         rbp
    ret




