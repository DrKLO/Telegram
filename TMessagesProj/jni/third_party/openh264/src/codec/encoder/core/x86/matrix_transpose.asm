;*!
;* \copy
;*     Copyright (c)  2009-2013, Cisco Systems
;*     All rights reserved.
;*
;*     Redistribution and use in source and binary forms, with or without
;*     modification, are permitted provided that the following conditions
;*     are met:
;*
;*        ?Redistributions of source code must retain the above copyright
;*          notice, this list of conditions and the following disclaimer.
;*
;*        ?Redistributions in binary form must reproduce the above copyright
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
;*************************************************************************/

%include "asm_inc.asm"

;in:  m0, m1, m2, m3, m4, m5, m6, m7
;out: m0, m3, m5, m2, m7, m1, m6, m4
%macro TRANSPOSE_8x8B_MMX 10
    MMX_XSwap bw,  %1, %2, %8
    MMX_XSwap bw,  %3, %4, %2
    MMX_XSwap bw,  %5, %6, %4
    movq    %6, %9
    movq    %10, %4
    MMX_XSwap bw,  %7, %6, %4

    MMX_XSwap wd,  %1, %3, %6
    MMX_XSwap wd,  %8, %2, %3
    MMX_XSwap wd,  %5, %7, %2
    movq    %7, %10
    movq    %10, %3
    MMX_XSwap wd,  %7, %4, %3

    MMX_XSwap dq,  %1, %5, %4
    MMX_XSwap dq,  %6, %2, %5
    MMX_XSwap dq,  %8, %7, %2
    movq    %7, %10
    movq    %10, %5
    MMX_XSwap dq,  %7, %3, %5

    movq    %3, %10
%endmacro

;in: m0, m3, m5, m2, m7, m1, m6, m4
%macro TRANSPOSE8x8_WRITE_MMX 2 ; dst, dst_stride
    movq [%1], mm0          ; result of line 1, x8 bytes
    movq [%1+%2], mm3       ; result of line 2
    lea %1, [%1+2*%2]
    movq [%1], mm5          ; result of line 3
    movq [%1+%2], mm2       ; result of line 4
    lea %1, [%1+2*%2]
    movq [%1], mm7          ; result of line 5
    movq [%1+%2], mm1       ; result of line 6
    lea %1, [%1+2*%2]
    movq [%1], mm6          ; result of line 7
    movq [%1+%2], mm4       ; result of line 8
%endmacro

;in: m0, m3, m5, m2, m7, m1, m6, m4
%macro TRANSPOSE8x8_WRITE_ALT_MMX 3 ; dst, dst_stride, reg32
    movq [%1], mm0          ; result of line 1, x8 bytes
    movq [%1+%2], mm3       ; result of line 2
    lea %3, [%1+2*%2]
    movq [%3], mm5          ; result of line 3
    movq [%3+%2], mm2       ; result of line 4
    lea %3, [%3+2*%2]
    movq [%3], mm7          ; result of line 5
    movq [%3+%2], mm1       ; result of line 6
    lea %3, [%3+2*%2]
    movq [%3], mm6          ; result of line 7
    movq [%3+%2], mm4       ; result of line 8
%endmacro   ; end of TRANSPOSE8x8_WRITE_ALT_MMX

; for transpose 16x8

;in:  m0, m1, m2, m3, m4, m5, m6, m7
;out: m4, m2, m3, m7, m5, m1, m6, m0
%macro TRANSPOSE_8x16B_SSE2     10
    SSE2_XSawp bw,  %1, %2, %8
    SSE2_XSawp bw,  %3, %4, %2
    SSE2_XSawp bw,  %5, %6, %4
    movdqa  %6, %9
    movdqa  %10, %4
    SSE2_XSawp bw,  %7, %6, %4

    SSE2_XSawp wd,  %1, %3, %6
    SSE2_XSawp wd,  %8, %2, %3
    SSE2_XSawp wd,  %5, %7, %2
    movdqa  %7, %10
    movdqa  %10, %3
    SSE2_XSawp wd,  %7, %4, %3

    SSE2_XSawp dq,  %1, %5, %4
    SSE2_XSawp dq,  %6, %2, %5
    SSE2_XSawp dq,  %8, %7, %2
    movdqa  %7, %10
    movdqa  %10, %5
    SSE2_XSawp dq,  %7, %3, %5

    SSE2_XSawp qdq,  %1, %8, %3
    SSE2_XSawp qdq,  %4, %2, %8
    SSE2_XSawp qdq,  %6, %7, %2
    movdqa  %7, %10
    movdqa  %10, %1
    SSE2_XSawp qdq,  %7, %5, %1
    movdqa  %5, %10
%endmacro   ; end of TRANSPOSE_8x16B_SSE2


%macro TRANSPOSE8x16_WRITE_SSE2 2   ; dst, dst_stride
    movq [%1], xmm4         ; result of line 1, x8 bytes
    movq [%1+%2], xmm2      ; result of line 2
    lea %1, [%1+2*%2]
    movq [%1], xmm3         ; result of line 3
    movq [%1+%2], xmm7      ; result of line 4

    lea %1, [%1+2*%2]
    movq [%1], xmm5         ; result of line 5
    movq [%1+%2], xmm1      ; result of line 6
    lea %1, [%1+2*%2]
    movq [%1], xmm6         ; result of line 7
    movq [%1+%2], xmm0      ; result of line 8

    lea %1, [%1+2*%2]
    movhpd [%1], xmm4       ; result of line 9
    movhpd [%1+%2], xmm2    ; result of line 10
    lea %1, [%1+2*%2]
    movhpd [%1], xmm3       ; result of line 11
    movhpd [%1+%2], xmm7    ; result of line 12

    lea %1, [%1+2*%2]
    movhpd [%1], xmm5       ; result of line 13
    movhpd [%1+%2], xmm1    ; result of line 14
    lea %1, [%1+2*%2]
    movhpd [%1], xmm6       ; result of line 15
    movhpd [%1+%2], xmm0    ; result of line 16
%endmacro   ; end of TRANSPOSE_WRITE_RESULT_SSE2

%macro TRANSPOSE8x16_WRITE_ALT_SSE2 3   ; dst, dst_stride, reg32
    movq [%1], xmm4         ; result of line 1, x8 bytes
    movq [%1+%2], xmm2      ; result of line 2
    lea %3, [%1+2*%2]
    movq [%3], xmm3         ; result of line 3
    movq [%3+%2], xmm7      ; result of line 4

    lea %3, [%3+2*%2]
    movq [%3], xmm5         ; result of line 5
    movq [%3+%2], xmm1      ; result of line 6
    lea %3, [%3+2*%2]
    movq [%3], xmm6         ; result of line 7
    movq [%3+%2], xmm0      ; result of line 8

    lea %3, [%3+2*%2]
    movhpd [%3], xmm4       ; result of line 9
    movhpd [%3+%2], xmm2    ; result of line 10
    lea %3, [%3+2*%2]
    movhpd [%3], xmm3       ; result of line 11
    movhpd [%3+%2], xmm7    ; result of line 12

    lea %3, [%3+2*%2]
    movhpd [%3], xmm5       ; result of line 13
    movhpd [%3+%2], xmm1    ; result of line 14
    lea %3, [%3+2*%2]
    movhpd [%3], xmm6       ; result of line 15
    movhpd [%3+%2], xmm0    ; result of line 16
%endmacro   ; end of TRANSPOSE8x16_WRITE_ALT_SSE2


SECTION .text

WELS_EXTERN TransposeMatrixBlock16x16_sse2
; void TransposeMatrixBlock16x16_sse2( void *dst/*16x16*/, const int32_t dst_stride, void *src/*16x16*/, const int32_t src_stride );
    push r4
    push r5
    %assign push_num 2
    LOAD_4_PARA
    PUSH_XMM 8
    SIGN_EXTENSION  r1, r1d
    SIGN_EXTENSION  r3, r3d

    mov r4, r7
    and r4, 0Fh
    sub r7, 10h
    sub r7, r4
    lea r5, [r3+r3*2]
    ; top 8x16 block
    movdqa xmm0, [r2]
    movdqa xmm1, [r2+r3]
    movdqa xmm2, [r2+r3*2]
    movdqa xmm3, [r2+r5]
    lea r2, [r2+r3*4]
    movdqa xmm4, [r2]
    movdqa xmm5, [r2+r3]
    movdqa xmm6, [r2+r3*2]

    ;in:  m0, m1, m2, m3, m4, m5, m6, m7
    ;out: m4, m2, m3, m7, m5, m1, m6, m0
    TRANSPOSE_8x16B_SSE2    xmm0, xmm1, xmm2, xmm3, xmm4, xmm5, xmm6, xmm7, [r2+r5], [r7]

    TRANSPOSE8x16_WRITE_SSE2        r0, r1

    ; bottom 8x16 block
    lea r2, [r2+r3*4]
    movdqa xmm0, [r2]
    movdqa xmm1, [r2+r3]
    movdqa xmm2, [r2+r3*2]
    movdqa xmm3, [r2+r5]
    lea r2, [r2+r3*4]
    movdqa xmm4, [r2]
    movdqa xmm5, [r2+r3]
    movdqa xmm6, [r2+r3*2]

    ;in:  m0, m1, m2, m3, m4, m5, m6, m7
    ;out: m4, m2, m3, m7, m5, m1, m6, m0
    TRANSPOSE_8x16B_SSE2    xmm0, xmm1, xmm2, xmm3, xmm4, xmm5, xmm6, xmm7, [r2+r5], [r7]

    mov r5, r1
    sal r5, 4
    sub r0, r5
    lea r0, [r0+r1*2+8]
    TRANSPOSE8x16_WRITE_SSE2        r0, r1

    add r7, r4
    add r7, 10h
    POP_XMM
    LOAD_4_PARA_POP
    pop r5
    pop r4
    ret

WELS_EXTERN TransposeMatrixBlocksx16_sse2
; void TransposeMatrixBlocksx16_sse2( void *dst/*W16x16*/, const int32_t dst_stride, void *src/*16xW16*/, const int32_t src_stride, const int32_t num_blocks );
    push r5
    push r6
    %assign push_num 2
    LOAD_5_PARA
    PUSH_XMM 8
    SIGN_EXTENSION  r1, r1d
    SIGN_EXTENSION  r3, r3d
    SIGN_EXTENSION  r4, r4d
    mov r5, r7
    and r5, 0Fh
    sub r7, 10h
    sub r7, r5
TRANSPOSE_LOOP_SSE2:
    ; explictly loading next loop data
    lea r6, [r2+r3*8]
    push r4
%rep 8
    mov r4, [r6]
    mov r4, [r6+r3]
    lea r6, [r6+r3*2]
%endrep
    pop r4
    ; top 8x16 block
    movdqa xmm0, [r2]
    movdqa xmm1, [r2+r3]
    lea r2, [r2+r3*2]
    movdqa xmm2, [r2]
    movdqa xmm3, [r2+r3]
    lea r2, [r2+r3*2]
    movdqa xmm4, [r2]
    movdqa xmm5, [r2+r3]
    lea r2, [r2+r3*2]
    movdqa xmm6, [r2]

    ;in:  m0, m1, m2, m3, m4, m5, m6, m7
    ;out: m4, m2, m3, m7, m5, m1, m6, m0
    TRANSPOSE_8x16B_SSE2    xmm0, xmm1, xmm2, xmm3, xmm4, xmm5, xmm6, xmm7, [r2+r3], [r7]
    TRANSPOSE8x16_WRITE_ALT_SSE2        r0, r1, r6
    lea r2, [r2+r3*2]

    ; bottom 8x16 block
    movdqa xmm0, [r2]
    movdqa xmm1, [r2+r3]
    lea r2, [r2+r3*2]
    movdqa xmm2, [r2]
    movdqa xmm3, [r2+r3]
    lea r2, [r2+r3*2]
    movdqa xmm4, [r2]
    movdqa xmm5, [r2+r3]
    lea r2, [r2+r3*2]
    movdqa xmm6, [r2]

    ;in:  m0, m1, m2, m3, m4, m5, m6, m7
    ;out: m4, m2, m3, m7, m5, m1, m6, m0
    TRANSPOSE_8x16B_SSE2    xmm0, xmm1, xmm2, xmm3, xmm4, xmm5, xmm6, xmm7, [r2+r3], [r7]
    TRANSPOSE8x16_WRITE_ALT_SSE2        r0+8, r1, r6
    lea r2, [r2+r3*2]
    lea r0, [r0+16]
    dec r4
    jg near TRANSPOSE_LOOP_SSE2

    add r7, r5
    add r7, 10h
    POP_XMM
    LOAD_5_PARA_POP
    pop r6
    pop r5
    ret

WELS_EXTERN TransposeMatrixBlock8x8_mmx
; void TransposeMatrixBlock8x8_mmx( void *dst/*8x8*/, const int32_t dst_stride, void *src/*8x8*/, const int32_t src_stride );
    %assign push_num 0
    LOAD_4_PARA
    SIGN_EXTENSION  r1, r1d
    SIGN_EXTENSION  r3, r3d
    sub r7, 8

    movq mm0, [r2]
    movq mm1, [r2+r3]
    lea r2, [r2+2*r3]
    movq mm2, [r2]
    movq mm3, [r2+r3]
    lea r2, [r2+2*r3]
    movq mm4, [r2]
    movq mm5, [r2+r3]
    lea r2, [r2+2*r3]
    movq mm6, [r2]

    ;in:  m0, m1, m2, m3, m4, m5, m6, m7
    ;out: m0, m3, m5, m2, m7, m1, m6, m4
    TRANSPOSE_8x8B_MMX mm0, mm1, mm2, mm3, mm4, mm5, mm6, mm7, [r2+r3], [r7]

    TRANSPOSE8x8_WRITE_MMX r0, r1

    emms
    add r7, 8
    LOAD_4_PARA_POP
    ret

WELS_EXTERN TransposeMatrixBlocksx8_mmx
; void TransposeMatrixBlocksx8_mmx( void *dst/*8xW8*/, const int32_t dst_stride, void *src/*W8x8*/, const int32_t src_stride, const int32_t num_blocks );
    push r5
    push r6
    %assign push_num 2
    LOAD_5_PARA
    SIGN_EXTENSION  r1, r1d
    SIGN_EXTENSION  r3, r3d
    SIGN_EXTENSION  r4, r4d
    sub r7, 8

    lea r5, [r2+r3*8]

TRANSPOSE_BLOCKS_X8_LOOP_MMX:
    ; explictly loading next loop data
%rep 4
    mov r6, [r5]
    mov r6, [r5+r3]
    lea r5, [r5+r3*2]
%endrep
    movq mm0, [r2]
    movq mm1, [r2+r3]
    lea r2, [r2+2*r3]
    movq mm2, [r2]
    movq mm3, [r2+r3]
    lea r2, [r2+2*r3]
    movq mm4, [r2]
    movq mm5, [r2+r3]
    lea r2, [r2+2*r3]
    movq mm6, [r2]

    ;in:  m0, m1, m2, m3, m4, m5, m6, m7
    ;out: m0, m3, m5, m2, m7, m1, m6, m4
    TRANSPOSE_8x8B_MMX mm0, mm1, mm2, mm3, mm4, mm5, mm6, mm7, [r2+r3], [r7]

    TRANSPOSE8x8_WRITE_ALT_MMX r0, r1, r6
    lea r0, [r0+8]
    lea r2, [r2+2*r3]
    dec r4
    jg near TRANSPOSE_BLOCKS_X8_LOOP_MMX

    emms
    add r7, 8
    LOAD_5_PARA_POP
    pop r6
    pop r5
    ret
