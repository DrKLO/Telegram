;
; jchuff-sse2.asm - Huffman entropy encoding (64-bit SSE2)
;
; Copyright (C) 2009-2011, 2014-2016, D. R. Commander.
; Copyright (C) 2015, Matthieu Darbois.
;
; Based on the x86 SIMD extension for IJG JPEG library
; Copyright (C) 1999-2006, MIYASAKA Masaru.
; For conditions of distribution and use, see copyright notice in jsimdext.inc
;
; This file should be assembled with NASM (Netwide Assembler),
; can *not* be assembled with Microsoft's MASM or any compatible
; assembler (including Borland's Turbo Assembler).
; NASM is available from http://nasm.sourceforge.net/ or
; http://sourceforge.net/project/showfiles.php?group_id=6208
;
; This file contains an SSE2 implementation for Huffman coding of one block.
; The following code is based directly on jchuff.c; see jchuff.c for more
; details.

%include "jsimdext.inc"

; --------------------------------------------------------------------------
    SECTION     SEG_CONST

    alignz      32
    GLOBAL_DATA(jconst_huff_encode_one_block)

EXTN(jconst_huff_encode_one_block):

%include "jpeg_nbits_table.inc"

    alignz      32

; --------------------------------------------------------------------------
    SECTION     SEG_TEXT
    BITS        64

; These macros perform the same task as the emit_bits() function in the
; original libjpeg code.  In addition to reducing overhead by explicitly
; inlining the code, additional performance is achieved by taking into
; account the size of the bit buffer and waiting until it is almost full
; before emptying it.  This mostly benefits 64-bit platforms, since 6
; bytes can be stored in a 64-bit bit buffer before it has to be emptied.

%macro EMIT_BYTE 0
    sub         put_bits, 8             ; put_bits -= 8;
    mov         rdx, put_buffer
    mov         ecx, put_bits
    shr         rdx, cl                 ; c = (JOCTET)GETJOCTET(put_buffer >> put_bits);
    mov         byte [buffer], dl       ; *buffer++ = c;
    add         buffer, 1
    cmp         dl, 0xFF                ; need to stuff a zero byte?
    jne         %%.EMIT_BYTE_END
    mov         byte [buffer], 0        ; *buffer++ = 0;
    add         buffer, 1
%%.EMIT_BYTE_END:
%endmacro

%macro PUT_BITS 1
    add         put_bits, ecx           ; put_bits += size;
    shl         put_buffer, cl          ; put_buffer = (put_buffer << size);
    or          put_buffer, %1
%endmacro

%macro CHECKBUF31 0
    cmp         put_bits, 32            ; if (put_bits > 31) {
    jl          %%.CHECKBUF31_END
    EMIT_BYTE
    EMIT_BYTE
    EMIT_BYTE
    EMIT_BYTE
%%.CHECKBUF31_END:
%endmacro

%macro CHECKBUF47 0
    cmp         put_bits, 48            ; if (put_bits > 47) {
    jl          %%.CHECKBUF47_END
    EMIT_BYTE
    EMIT_BYTE
    EMIT_BYTE
    EMIT_BYTE
    EMIT_BYTE
    EMIT_BYTE
%%.CHECKBUF47_END:
%endmacro

%macro EMIT_BITS 2
    CHECKBUF47
    mov         ecx, %2
    PUT_BITS    %1
%endmacro

%macro kloop_prepare 37                 ;(ko, jno0, ..., jno31, xmm0, xmm1, xmm2, xmm3)
    pxor        xmm8, xmm8              ; __m128i neg = _mm_setzero_si128();
    pxor        xmm9, xmm9              ; __m128i neg = _mm_setzero_si128();
    pxor        xmm10, xmm10            ; __m128i neg = _mm_setzero_si128();
    pxor        xmm11, xmm11            ; __m128i neg = _mm_setzero_si128();
    pinsrw      %34, word [r12 + %2  * SIZEOF_WORD], 0  ; xmm_shadow[0] = block[jno0];
    pinsrw      %35, word [r12 + %10 * SIZEOF_WORD], 0  ; xmm_shadow[8] = block[jno8];
    pinsrw      %36, word [r12 + %18 * SIZEOF_WORD], 0  ; xmm_shadow[16] = block[jno16];
    pinsrw      %37, word [r12 + %26 * SIZEOF_WORD], 0  ; xmm_shadow[24] = block[jno24];
    pinsrw      %34, word [r12 + %3  * SIZEOF_WORD], 1  ; xmm_shadow[1] = block[jno1];
    pinsrw      %35, word [r12 + %11 * SIZEOF_WORD], 1  ; xmm_shadow[9] = block[jno9];
    pinsrw      %36, word [r12 + %19 * SIZEOF_WORD], 1  ; xmm_shadow[17] = block[jno17];
    pinsrw      %37, word [r12 + %27 * SIZEOF_WORD], 1  ; xmm_shadow[25] = block[jno25];
    pinsrw      %34, word [r12 + %4  * SIZEOF_WORD], 2  ; xmm_shadow[2] = block[jno2];
    pinsrw      %35, word [r12 + %12 * SIZEOF_WORD], 2  ; xmm_shadow[10] = block[jno10];
    pinsrw      %36, word [r12 + %20 * SIZEOF_WORD], 2  ; xmm_shadow[18] = block[jno18];
    pinsrw      %37, word [r12 + %28 * SIZEOF_WORD], 2  ; xmm_shadow[26] = block[jno26];
    pinsrw      %34, word [r12 + %5  * SIZEOF_WORD], 3  ; xmm_shadow[3] = block[jno3];
    pinsrw      %35, word [r12 + %13 * SIZEOF_WORD], 3  ; xmm_shadow[11] = block[jno11];
    pinsrw      %36, word [r12 + %21 * SIZEOF_WORD], 3  ; xmm_shadow[19] = block[jno19];
    pinsrw      %37, word [r12 + %29 * SIZEOF_WORD], 3  ; xmm_shadow[27] = block[jno27];
    pinsrw      %34, word [r12 + %6  * SIZEOF_WORD], 4  ; xmm_shadow[4] = block[jno4];
    pinsrw      %35, word [r12 + %14 * SIZEOF_WORD], 4  ; xmm_shadow[12] = block[jno12];
    pinsrw      %36, word [r12 + %22 * SIZEOF_WORD], 4  ; xmm_shadow[20] = block[jno20];
    pinsrw      %37, word [r12 + %30 * SIZEOF_WORD], 4  ; xmm_shadow[28] = block[jno28];
    pinsrw      %34, word [r12 + %7  * SIZEOF_WORD], 5  ; xmm_shadow[5] = block[jno5];
    pinsrw      %35, word [r12 + %15 * SIZEOF_WORD], 5  ; xmm_shadow[13] = block[jno13];
    pinsrw      %36, word [r12 + %23 * SIZEOF_WORD], 5  ; xmm_shadow[21] = block[jno21];
    pinsrw      %37, word [r12 + %31 * SIZEOF_WORD], 5  ; xmm_shadow[29] = block[jno29];
    pinsrw      %34, word [r12 + %8  * SIZEOF_WORD], 6  ; xmm_shadow[6] = block[jno6];
    pinsrw      %35, word [r12 + %16 * SIZEOF_WORD], 6  ; xmm_shadow[14] = block[jno14];
    pinsrw      %36, word [r12 + %24 * SIZEOF_WORD], 6  ; xmm_shadow[22] = block[jno22];
    pinsrw      %37, word [r12 + %32 * SIZEOF_WORD], 6  ; xmm_shadow[30] = block[jno30];
    pinsrw      %34, word [r12 + %9  * SIZEOF_WORD], 7  ; xmm_shadow[7] = block[jno7];
    pinsrw      %35, word [r12 + %17 * SIZEOF_WORD], 7  ; xmm_shadow[15] = block[jno15];
    pinsrw      %36, word [r12 + %25 * SIZEOF_WORD], 7  ; xmm_shadow[23] = block[jno23];
%if %1 != 32
    pinsrw      %37, word [r12 + %33 * SIZEOF_WORD], 7  ; xmm_shadow[31] = block[jno31];
%else
    pinsrw      %37, ebx, 7             ; xmm_shadow[31] = block[jno31];
%endif
    pcmpgtw     xmm8, %34               ; neg = _mm_cmpgt_epi16(neg, x1);
    pcmpgtw     xmm9, %35               ; neg = _mm_cmpgt_epi16(neg, x1);
    pcmpgtw     xmm10, %36              ; neg = _mm_cmpgt_epi16(neg, x1);
    pcmpgtw     xmm11, %37              ; neg = _mm_cmpgt_epi16(neg, x1);
    paddw       %34, xmm8               ; x1 = _mm_add_epi16(x1, neg);
    paddw       %35, xmm9               ; x1 = _mm_add_epi16(x1, neg);
    paddw       %36, xmm10              ; x1 = _mm_add_epi16(x1, neg);
    paddw       %37, xmm11              ; x1 = _mm_add_epi16(x1, neg);
    pxor        %34, xmm8               ; x1 = _mm_xor_si128(x1, neg);
    pxor        %35, xmm9               ; x1 = _mm_xor_si128(x1, neg);
    pxor        %36, xmm10              ; x1 = _mm_xor_si128(x1, neg);
    pxor        %37, xmm11              ; x1 = _mm_xor_si128(x1, neg);
    pxor        xmm8, %34               ; neg = _mm_xor_si128(neg, x1);
    pxor        xmm9, %35               ; neg = _mm_xor_si128(neg, x1);
    pxor        xmm10, %36              ; neg = _mm_xor_si128(neg, x1);
    pxor        xmm11, %37              ; neg = _mm_xor_si128(neg, x1);
    movdqa      XMMWORD [t1 + %1 * SIZEOF_WORD], %34           ; _mm_storeu_si128((__m128i *)(t1 + ko), x1);
    movdqa      XMMWORD [t1 + (%1 + 8) * SIZEOF_WORD], %35     ; _mm_storeu_si128((__m128i *)(t1 + ko + 8), x1);
    movdqa      XMMWORD [t1 + (%1 + 16) * SIZEOF_WORD], %36    ; _mm_storeu_si128((__m128i *)(t1 + ko + 16), x1);
    movdqa      XMMWORD [t1 + (%1 + 24) * SIZEOF_WORD], %37    ; _mm_storeu_si128((__m128i *)(t1 + ko + 24), x1);
    movdqa      XMMWORD [t2 + %1 * SIZEOF_WORD], xmm8          ; _mm_storeu_si128((__m128i *)(t2 + ko), neg);
    movdqa      XMMWORD [t2 + (%1 + 8) * SIZEOF_WORD], xmm9    ; _mm_storeu_si128((__m128i *)(t2 + ko + 8), neg);
    movdqa      XMMWORD [t2 + (%1 + 16) * SIZEOF_WORD], xmm10  ; _mm_storeu_si128((__m128i *)(t2 + ko + 16), neg);
    movdqa      XMMWORD [t2 + (%1 + 24) * SIZEOF_WORD], xmm11  ; _mm_storeu_si128((__m128i *)(t2 + ko + 24), neg);
%endmacro

;
; Encode a single block's worth of coefficients.
;
; GLOBAL(JOCTET *)
; jsimd_huff_encode_one_block_sse2(working_state *state, JOCTET *buffer,
;                                  JCOEFPTR block, int last_dc_val,
;                                  c_derived_tbl *dctbl, c_derived_tbl *actbl)
;

; r10 = working_state *state
; r11 = JOCTET *buffer
; r12 = JCOEFPTR block
; r13d = int last_dc_val
; r14 = c_derived_tbl *dctbl
; r15 = c_derived_tbl *actbl

%define t1          rbp - (DCTSIZE2 * SIZEOF_WORD)
%define t2          t1 - (DCTSIZE2 * SIZEOF_WORD)
%define put_buffer  r8
%define put_bits    r9d
%define buffer      rax

    align       32
    GLOBAL_FUNCTION(jsimd_huff_encode_one_block_sse2)

EXTN(jsimd_huff_encode_one_block_sse2):
    push        rbp
    mov         rax, rsp                     ; rax = original rbp
    sub         rsp, byte 4
    and         rsp, byte (-SIZEOF_XMMWORD)  ; align to 128 bits
    mov         [rsp], rax
    mov         rbp, rsp                     ; rbp = aligned rbp
    lea         rsp, [t2]
    push_xmm    4
    collect_args 6
    push        rbx

    mov         buffer, r11                  ; r11 is now sratch

    mov         put_buffer, MMWORD [r10+16]  ; put_buffer = state->cur.put_buffer;
    mov         put_bits,    dword [r10+24]  ; put_bits = state->cur.put_bits;
    push        r10                          ; r10 is now scratch

    ; Encode the DC coefficient difference per section F.1.2.1
    movsx       edi, word [r12]         ; temp = temp2 = block[0] - last_dc_val;
    sub         edi, r13d               ; r13 is not used anymore
    mov         ebx, edi

    ; This is a well-known technique for obtaining the absolute value
    ; without a branch.  It is derived from an assembly language technique
    ; presented in "How to Optimize for the Pentium Processors",
    ; Copyright (c) 1996, 1997 by Agner Fog.
    mov         esi, edi
    sar         esi, 31                 ; temp3 = temp >> (CHAR_BIT * sizeof(int) - 1);
    xor         edi, esi                ; temp ^= temp3;
    sub         edi, esi                ; temp -= temp3;

    ; For a negative input, want temp2 = bitwise complement of abs(input)
    ; This code assumes we are on a two's complement machine
    add         ebx, esi                ; temp2 += temp3;

    ; Find the number of bits needed for the magnitude of the coefficient
    lea         r11, [rel jpeg_nbits_table]
    movzx       rdi, byte [r11 + rdi]         ; nbits = JPEG_NBITS(temp);
    ; Emit the Huffman-coded symbol for the number of bits
    mov         r11d,  INT [r14 + rdi * 4]    ; code = dctbl->ehufco[nbits];
    movzx       esi, byte [r14 + rdi + 1024]  ; size = dctbl->ehufsi[nbits];
    EMIT_BITS   r11, esi                      ; EMIT_BITS(code, size)

    ; Mask off any extra bits in code
    mov         esi, 1
    mov         ecx, edi
    shl         esi, cl
    dec         esi
    and         ebx, esi                ; temp2 &= (((JLONG)1)<<nbits) - 1;

    ; Emit that number of bits of the value, if positive,
    ; or the complement of its magnitude, if negative.
    EMIT_BITS   rbx, edi                ; EMIT_BITS(temp2, nbits)

    ; Prepare data
    xor         ebx, ebx
    kloop_prepare  0,  1,  8,  16, 9,  2,  3,  10, 17, 24, 32, 25, \
                   18, 11, 4,  5,  12, 19, 26, 33, 40, 48, 41, 34, \
                   27, 20, 13, 6,  7,  14, 21, 28, 35, \
                   xmm0, xmm1, xmm2, xmm3
    kloop_prepare  32, 42, 49, 56, 57, 50, 43, 36, 29, 22, 15, 23, \
                   30, 37, 44, 51, 58, 59, 52, 45, 38, 31, 39, 46, \
                   53, 60, 61, 54, 47, 55, 62, 63, 63, \
                   xmm4, xmm5, xmm6, xmm7

    pxor        xmm8, xmm8
    pcmpeqw     xmm0, xmm8              ; tmp0 = _mm_cmpeq_epi16(tmp0, zero);
    pcmpeqw     xmm1, xmm8              ; tmp1 = _mm_cmpeq_epi16(tmp1, zero);
    pcmpeqw     xmm2, xmm8              ; tmp2 = _mm_cmpeq_epi16(tmp2, zero);
    pcmpeqw     xmm3, xmm8              ; tmp3 = _mm_cmpeq_epi16(tmp3, zero);
    pcmpeqw     xmm4, xmm8              ; tmp4 = _mm_cmpeq_epi16(tmp4, zero);
    pcmpeqw     xmm5, xmm8              ; tmp5 = _mm_cmpeq_epi16(tmp5, zero);
    pcmpeqw     xmm6, xmm8              ; tmp6 = _mm_cmpeq_epi16(tmp6, zero);
    pcmpeqw     xmm7, xmm8              ; tmp7 = _mm_cmpeq_epi16(tmp7, zero);
    packsswb    xmm0, xmm1              ; tmp0 = _mm_packs_epi16(tmp0, tmp1);
    packsswb    xmm2, xmm3              ; tmp2 = _mm_packs_epi16(tmp2, tmp3);
    packsswb    xmm4, xmm5              ; tmp4 = _mm_packs_epi16(tmp4, tmp5);
    packsswb    xmm6, xmm7              ; tmp6 = _mm_packs_epi16(tmp6, tmp7);
    pmovmskb    r11d, xmm0              ; index  = ((uint64_t)_mm_movemask_epi8(tmp0)) << 0;
    pmovmskb    r12d, xmm2              ; index  = ((uint64_t)_mm_movemask_epi8(tmp2)) << 16;
    pmovmskb    r13d, xmm4              ; index  = ((uint64_t)_mm_movemask_epi8(tmp4)) << 32;
    pmovmskb    r14d, xmm6              ; index  = ((uint64_t)_mm_movemask_epi8(tmp6)) << 48;
    shl         r12, 16
    shl         r14, 16
    or          r11, r12
    or          r13, r14
    shl         r13, 32
    or          r11, r13
    not         r11                     ; index = ~index;

    ;mov MMWORD [ t1 + DCTSIZE2 * SIZEOF_WORD ], r11
    ;jmp .EFN

    mov         r13d,  INT [r15 + 240 * 4]     ; code_0xf0 = actbl->ehufco[0xf0];
    movzx       r14d, byte [r15 + 1024 + 240]  ; size_0xf0 = actbl->ehufsi[0xf0];
    lea         rsi, [t1]
.BLOOP:
    bsf         r12, r11                     ; r = __builtin_ctzl(index);
    jz          .ELOOP
    mov         rcx, r12
    lea         rsi, [rsi+r12*2]             ; k += r;
    shr         r11, cl                      ; index >>= r;
    movzx       rdi, word [rsi]              ; temp = t1[k];
    lea         rbx, [rel jpeg_nbits_table]
    movzx       rdi, byte [rbx + rdi]        ; nbits = JPEG_NBITS(temp);
.BRLOOP:
    cmp         r12, 16                 ; while (r > 15) {
    jl          .ERLOOP
    EMIT_BITS   r13, r14d               ; EMIT_BITS(code_0xf0, size_0xf0)
    sub         r12, 16                 ; r -= 16;
    jmp         .BRLOOP
.ERLOOP:
    ; Emit Huffman symbol for run length / number of bits
    CHECKBUF31  ; uses rcx, rdx

    shl         r12, 4                        ; temp3 = (r << 4) + nbits;
    add         r12, rdi
    mov         ebx,  INT [r15 + r12 * 4]     ; code = actbl->ehufco[temp3];
    movzx       ecx, byte [r15 + r12 + 1024]  ; size = actbl->ehufsi[temp3];
    PUT_BITS    rbx

    ;EMIT_CODE(code, size)

    movsx       ebx, word [rsi-DCTSIZE2*2]    ; temp2 = t2[k];
    ; Mask off any extra bits in code
    mov         rcx, rdi
    mov         rdx, 1
    shl         rdx, cl
    dec         rdx
    and         rbx, rdx                ; temp2 &= (((JLONG)1)<<nbits) - 1;
    PUT_BITS    rbx                     ; PUT_BITS(temp2, nbits)

    shr         r11, 1                  ; index >>= 1;
    add         rsi, 2                  ; ++k;
    jmp         .BLOOP
.ELOOP:
    ; If the last coef(s) were zero, emit an end-of-block code
    lea         rdi, [t1 + (DCTSIZE2-1) * 2]  ; r = DCTSIZE2-1-k;
    cmp         rdi, rsi                      ; if (r > 0) {
    je          .EFN
    mov         ebx,  INT [r15]               ; code = actbl->ehufco[0];
    movzx       r12d, byte [r15 + 1024]       ; size = actbl->ehufsi[0];
    EMIT_BITS   rbx, r12d
.EFN:
    pop         r10
    ; Save put_buffer & put_bits
    mov         MMWORD [r10+16], put_buffer  ; state->cur.put_buffer = put_buffer;
    mov         dword  [r10+24], put_bits    ; state->cur.put_bits = put_bits;

    pop         rbx
    uncollect_args 6
    pop_xmm     4
    mov         rsp, rbp                ; rsp <- aligned rbp
    pop         rsp                     ; rsp <- original rbp
    pop         rbp
    ret

; For some reason, the OS X linker does not honor the request to align the
; segment unless we do this.
    align       32
