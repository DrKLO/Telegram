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

%macro PROCESS_16X2X3 1
%if %1
        movdqa          xmm0,       XMMWORD PTR [rsi]
        lddqu           xmm5,       XMMWORD PTR [rdi]
        lddqu           xmm6,       XMMWORD PTR [rdi+1]
        lddqu           xmm7,       XMMWORD PTR [rdi+2]

        psadbw          xmm5,       xmm0
        psadbw          xmm6,       xmm0
        psadbw          xmm7,       xmm0
%else
        movdqa          xmm0,       XMMWORD PTR [rsi]
        lddqu           xmm1,       XMMWORD PTR [rdi]
        lddqu           xmm2,       XMMWORD PTR [rdi+1]
        lddqu           xmm3,       XMMWORD PTR [rdi+2]

        psadbw          xmm1,       xmm0
        psadbw          xmm2,       xmm0
        psadbw          xmm3,       xmm0

        paddw           xmm5,       xmm1
        paddw           xmm6,       xmm2
        paddw           xmm7,       xmm3
%endif
        movdqa          xmm0,       XMMWORD PTR [rsi+rax]
        lddqu           xmm1,       XMMWORD PTR [rdi+rdx]
        lddqu           xmm2,       XMMWORD PTR [rdi+rdx+1]
        lddqu           xmm3,       XMMWORD PTR [rdi+rdx+2]

        lea             rsi,        [rsi+rax*2]
        lea             rdi,        [rdi+rdx*2]

        psadbw          xmm1,       xmm0
        psadbw          xmm2,       xmm0
        psadbw          xmm3,       xmm0

        paddw           xmm5,       xmm1
        paddw           xmm6,       xmm2
        paddw           xmm7,       xmm3
%endmacro

%macro PROCESS_16X2X3_OFFSET 2
%if %1
        movdqa          xmm0,       XMMWORD PTR [rsi]
        movdqa          xmm4,       XMMWORD PTR [rdi]
        movdqa          xmm7,       XMMWORD PTR [rdi+16]

        movdqa          xmm5,       xmm7
        palignr         xmm5,       xmm4,       %2

        movdqa          xmm6,       xmm7
        palignr         xmm6,       xmm4,       (%2+1)

        palignr         xmm7,       xmm4,       (%2+2)

        psadbw          xmm5,       xmm0
        psadbw          xmm6,       xmm0
        psadbw          xmm7,       xmm0
%else
        movdqa          xmm0,       XMMWORD PTR [rsi]
        movdqa          xmm4,       XMMWORD PTR [rdi]
        movdqa          xmm3,       XMMWORD PTR [rdi+16]

        movdqa          xmm1,       xmm3
        palignr         xmm1,       xmm4,       %2

        movdqa          xmm2,       xmm3
        palignr         xmm2,       xmm4,       (%2+1)

        palignr         xmm3,       xmm4,       (%2+2)

        psadbw          xmm1,       xmm0
        psadbw          xmm2,       xmm0
        psadbw          xmm3,       xmm0

        paddw           xmm5,       xmm1
        paddw           xmm6,       xmm2
        paddw           xmm7,       xmm3
%endif
        movdqa          xmm0,       XMMWORD PTR [rsi+rax]
        movdqa          xmm4,       XMMWORD PTR [rdi+rdx]
        movdqa          xmm3,       XMMWORD PTR [rdi+rdx+16]

        movdqa          xmm1,       xmm3
        palignr         xmm1,       xmm4,       %2

        movdqa          xmm2,       xmm3
        palignr         xmm2,       xmm4,       (%2+1)

        palignr         xmm3,       xmm4,       (%2+2)

        lea             rsi,        [rsi+rax*2]
        lea             rdi,        [rdi+rdx*2]

        psadbw          xmm1,       xmm0
        psadbw          xmm2,       xmm0
        psadbw          xmm3,       xmm0

        paddw           xmm5,       xmm1
        paddw           xmm6,       xmm2
        paddw           xmm7,       xmm3
%endmacro

%macro PROCESS_16X16X3_OFFSET 2
%2_aligned_by_%1:

        sub             rdi,        %1

        PROCESS_16X2X3_OFFSET 1, %1
        PROCESS_16X2X3_OFFSET 0, %1
        PROCESS_16X2X3_OFFSET 0, %1
        PROCESS_16X2X3_OFFSET 0, %1
        PROCESS_16X2X3_OFFSET 0, %1
        PROCESS_16X2X3_OFFSET 0, %1
        PROCESS_16X2X3_OFFSET 0, %1
        PROCESS_16X2X3_OFFSET 0, %1

        jmp             %2_store_off

%endmacro

%macro PROCESS_16X8X3_OFFSET 2
%2_aligned_by_%1:

        sub             rdi,        %1

        PROCESS_16X2X3_OFFSET 1, %1
        PROCESS_16X2X3_OFFSET 0, %1
        PROCESS_16X2X3_OFFSET 0, %1
        PROCESS_16X2X3_OFFSET 0, %1

        jmp             %2_store_off

%endmacro

SECTION .text

;void int vpx_sad16x16x3_ssse3(
;    unsigned char *src_ptr,
;    int  src_stride,
;    unsigned char *ref_ptr,
;    int  ref_stride,
;    int  *results)
global sym(vpx_sad16x16x3_ssse3) PRIVATE
sym(vpx_sad16x16x3_ssse3):
    push        rbp
    mov         rbp, rsp
    SHADOW_ARGS_TO_STACK 5
    SAVE_XMM 7
    push        rsi
    push        rdi
    push        rcx
    ; end prolog

        mov             rsi,        arg(0) ;src_ptr
        mov             rdi,        arg(2) ;ref_ptr

        mov             rdx,        0xf
        and             rdx,        rdi

        jmp .vpx_sad16x16x3_ssse3_skiptable
.vpx_sad16x16x3_ssse3_jumptable:
        dd .vpx_sad16x16x3_ssse3_aligned_by_0  - .vpx_sad16x16x3_ssse3_do_jump
        dd .vpx_sad16x16x3_ssse3_aligned_by_1  - .vpx_sad16x16x3_ssse3_do_jump
        dd .vpx_sad16x16x3_ssse3_aligned_by_2  - .vpx_sad16x16x3_ssse3_do_jump
        dd .vpx_sad16x16x3_ssse3_aligned_by_3  - .vpx_sad16x16x3_ssse3_do_jump
        dd .vpx_sad16x16x3_ssse3_aligned_by_4  - .vpx_sad16x16x3_ssse3_do_jump
        dd .vpx_sad16x16x3_ssse3_aligned_by_5  - .vpx_sad16x16x3_ssse3_do_jump
        dd .vpx_sad16x16x3_ssse3_aligned_by_6  - .vpx_sad16x16x3_ssse3_do_jump
        dd .vpx_sad16x16x3_ssse3_aligned_by_7  - .vpx_sad16x16x3_ssse3_do_jump
        dd .vpx_sad16x16x3_ssse3_aligned_by_8  - .vpx_sad16x16x3_ssse3_do_jump
        dd .vpx_sad16x16x3_ssse3_aligned_by_9  - .vpx_sad16x16x3_ssse3_do_jump
        dd .vpx_sad16x16x3_ssse3_aligned_by_10 - .vpx_sad16x16x3_ssse3_do_jump
        dd .vpx_sad16x16x3_ssse3_aligned_by_11 - .vpx_sad16x16x3_ssse3_do_jump
        dd .vpx_sad16x16x3_ssse3_aligned_by_12 - .vpx_sad16x16x3_ssse3_do_jump
        dd .vpx_sad16x16x3_ssse3_aligned_by_13 - .vpx_sad16x16x3_ssse3_do_jump
        dd .vpx_sad16x16x3_ssse3_aligned_by_14 - .vpx_sad16x16x3_ssse3_do_jump
        dd .vpx_sad16x16x3_ssse3_aligned_by_15 - .vpx_sad16x16x3_ssse3_do_jump
.vpx_sad16x16x3_ssse3_skiptable:

        call .vpx_sad16x16x3_ssse3_do_jump
.vpx_sad16x16x3_ssse3_do_jump:
        pop             rcx                         ; get the address of do_jump
        mov             rax,  .vpx_sad16x16x3_ssse3_jumptable - .vpx_sad16x16x3_ssse3_do_jump
        add             rax,  rcx  ; get the absolute address of vpx_sad16x16x3_ssse3_jumptable

        movsxd          rax,  dword [rax + 4*rdx]   ; get the 32 bit offset from the jumptable
        add             rcx,        rax

        movsxd          rax,        dword ptr arg(1) ;src_stride
        movsxd          rdx,        dword ptr arg(3) ;ref_stride

        jmp             rcx

        PROCESS_16X16X3_OFFSET 0,  .vpx_sad16x16x3_ssse3
        PROCESS_16X16X3_OFFSET 1,  .vpx_sad16x16x3_ssse3
        PROCESS_16X16X3_OFFSET 2,  .vpx_sad16x16x3_ssse3
        PROCESS_16X16X3_OFFSET 3,  .vpx_sad16x16x3_ssse3
        PROCESS_16X16X3_OFFSET 4,  .vpx_sad16x16x3_ssse3
        PROCESS_16X16X3_OFFSET 5,  .vpx_sad16x16x3_ssse3
        PROCESS_16X16X3_OFFSET 6,  .vpx_sad16x16x3_ssse3
        PROCESS_16X16X3_OFFSET 7,  .vpx_sad16x16x3_ssse3
        PROCESS_16X16X3_OFFSET 8,  .vpx_sad16x16x3_ssse3
        PROCESS_16X16X3_OFFSET 9,  .vpx_sad16x16x3_ssse3
        PROCESS_16X16X3_OFFSET 10, .vpx_sad16x16x3_ssse3
        PROCESS_16X16X3_OFFSET 11, .vpx_sad16x16x3_ssse3
        PROCESS_16X16X3_OFFSET 12, .vpx_sad16x16x3_ssse3
        PROCESS_16X16X3_OFFSET 13, .vpx_sad16x16x3_ssse3
        PROCESS_16X16X3_OFFSET 14, .vpx_sad16x16x3_ssse3

.vpx_sad16x16x3_ssse3_aligned_by_15:
        PROCESS_16X2X3 1
        PROCESS_16X2X3 0
        PROCESS_16X2X3 0
        PROCESS_16X2X3 0
        PROCESS_16X2X3 0
        PROCESS_16X2X3 0
        PROCESS_16X2X3 0
        PROCESS_16X2X3 0

.vpx_sad16x16x3_ssse3_store_off:
        mov             rdi,        arg(4) ;Results

        movq            xmm0,       xmm5
        psrldq          xmm5,       8

        paddw           xmm0,       xmm5
        movd            [rdi],      xmm0
;-
        movq            xmm0,       xmm6
        psrldq          xmm6,       8

        paddw           xmm0,       xmm6
        movd            [rdi+4],    xmm0
;-
        movq            xmm0,       xmm7
        psrldq          xmm7,       8

        paddw           xmm0,       xmm7
        movd            [rdi+8],    xmm0

    ; begin epilog
    pop         rcx
    pop         rdi
    pop         rsi
    RESTORE_XMM
    UNSHADOW_ARGS
    pop         rbp
    ret

;void int vpx_sad16x8x3_ssse3(
;    unsigned char *src_ptr,
;    int  src_stride,
;    unsigned char *ref_ptr,
;    int  ref_stride,
;    int  *results)
global sym(vpx_sad16x8x3_ssse3) PRIVATE
sym(vpx_sad16x8x3_ssse3):
    push        rbp
    mov         rbp, rsp
    SHADOW_ARGS_TO_STACK 5
    SAVE_XMM 7
    push        rsi
    push        rdi
    push        rcx
    ; end prolog

        mov             rsi,        arg(0) ;src_ptr
        mov             rdi,        arg(2) ;ref_ptr

        mov             rdx,        0xf
        and             rdx,        rdi

        jmp .vpx_sad16x8x3_ssse3_skiptable
.vpx_sad16x8x3_ssse3_jumptable:
        dd .vpx_sad16x8x3_ssse3_aligned_by_0  - .vpx_sad16x8x3_ssse3_do_jump
        dd .vpx_sad16x8x3_ssse3_aligned_by_1  - .vpx_sad16x8x3_ssse3_do_jump
        dd .vpx_sad16x8x3_ssse3_aligned_by_2  - .vpx_sad16x8x3_ssse3_do_jump
        dd .vpx_sad16x8x3_ssse3_aligned_by_3  - .vpx_sad16x8x3_ssse3_do_jump
        dd .vpx_sad16x8x3_ssse3_aligned_by_4  - .vpx_sad16x8x3_ssse3_do_jump
        dd .vpx_sad16x8x3_ssse3_aligned_by_5  - .vpx_sad16x8x3_ssse3_do_jump
        dd .vpx_sad16x8x3_ssse3_aligned_by_6  - .vpx_sad16x8x3_ssse3_do_jump
        dd .vpx_sad16x8x3_ssse3_aligned_by_7  - .vpx_sad16x8x3_ssse3_do_jump
        dd .vpx_sad16x8x3_ssse3_aligned_by_8  - .vpx_sad16x8x3_ssse3_do_jump
        dd .vpx_sad16x8x3_ssse3_aligned_by_9  - .vpx_sad16x8x3_ssse3_do_jump
        dd .vpx_sad16x8x3_ssse3_aligned_by_10 - .vpx_sad16x8x3_ssse3_do_jump
        dd .vpx_sad16x8x3_ssse3_aligned_by_11 - .vpx_sad16x8x3_ssse3_do_jump
        dd .vpx_sad16x8x3_ssse3_aligned_by_12 - .vpx_sad16x8x3_ssse3_do_jump
        dd .vpx_sad16x8x3_ssse3_aligned_by_13 - .vpx_sad16x8x3_ssse3_do_jump
        dd .vpx_sad16x8x3_ssse3_aligned_by_14 - .vpx_sad16x8x3_ssse3_do_jump
        dd .vpx_sad16x8x3_ssse3_aligned_by_15 - .vpx_sad16x8x3_ssse3_do_jump
.vpx_sad16x8x3_ssse3_skiptable:

        call .vpx_sad16x8x3_ssse3_do_jump
.vpx_sad16x8x3_ssse3_do_jump:
        pop             rcx                         ; get the address of do_jump
        mov             rax,  .vpx_sad16x8x3_ssse3_jumptable - .vpx_sad16x8x3_ssse3_do_jump
        add             rax,  rcx  ; get the absolute address of vpx_sad16x8x3_ssse3_jumptable

        movsxd          rax,  dword [rax + 4*rdx]   ; get the 32 bit offset from the jumptable
        add             rcx,        rax

        movsxd          rax,        dword ptr arg(1) ;src_stride
        movsxd          rdx,        dword ptr arg(3) ;ref_stride

        jmp             rcx

        PROCESS_16X8X3_OFFSET 0,  .vpx_sad16x8x3_ssse3
        PROCESS_16X8X3_OFFSET 1,  .vpx_sad16x8x3_ssse3
        PROCESS_16X8X3_OFFSET 2,  .vpx_sad16x8x3_ssse3
        PROCESS_16X8X3_OFFSET 3,  .vpx_sad16x8x3_ssse3
        PROCESS_16X8X3_OFFSET 4,  .vpx_sad16x8x3_ssse3
        PROCESS_16X8X3_OFFSET 5,  .vpx_sad16x8x3_ssse3
        PROCESS_16X8X3_OFFSET 6,  .vpx_sad16x8x3_ssse3
        PROCESS_16X8X3_OFFSET 7,  .vpx_sad16x8x3_ssse3
        PROCESS_16X8X3_OFFSET 8,  .vpx_sad16x8x3_ssse3
        PROCESS_16X8X3_OFFSET 9,  .vpx_sad16x8x3_ssse3
        PROCESS_16X8X3_OFFSET 10, .vpx_sad16x8x3_ssse3
        PROCESS_16X8X3_OFFSET 11, .vpx_sad16x8x3_ssse3
        PROCESS_16X8X3_OFFSET 12, .vpx_sad16x8x3_ssse3
        PROCESS_16X8X3_OFFSET 13, .vpx_sad16x8x3_ssse3
        PROCESS_16X8X3_OFFSET 14, .vpx_sad16x8x3_ssse3

.vpx_sad16x8x3_ssse3_aligned_by_15:

        PROCESS_16X2X3 1
        PROCESS_16X2X3 0
        PROCESS_16X2X3 0
        PROCESS_16X2X3 0

.vpx_sad16x8x3_ssse3_store_off:
        mov             rdi,        arg(4) ;Results

        movq            xmm0,       xmm5
        psrldq          xmm5,       8

        paddw           xmm0,       xmm5
        movd            [rdi],      xmm0
;-
        movq            xmm0,       xmm6
        psrldq          xmm6,       8

        paddw           xmm0,       xmm6
        movd            [rdi+4],    xmm0
;-
        movq            xmm0,       xmm7
        psrldq          xmm7,       8

        paddw           xmm0,       xmm7
        movd            [rdi+8],    xmm0

    ; begin epilog
    pop         rcx
    pop         rdi
    pop         rsi
    RESTORE_XMM
    UNSHADOW_ARGS
    pop         rbp
    ret
