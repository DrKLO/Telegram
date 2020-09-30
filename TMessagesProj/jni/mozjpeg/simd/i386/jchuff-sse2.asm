;
; jchuff-sse2.asm - Huffman entropy encoding (SSE2)
;
; Copyright (C) 2009-2011, 2014-2017, D. R. Commander.
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
    BITS        32

; These macros perform the same task as the emit_bits() function in the
; original libjpeg code.  In addition to reducing overhead by explicitly
; inlining the code, additional performance is achieved by taking into
; account the size of the bit buffer and waiting until it is almost full
; before emptying it.  This mostly benefits 64-bit platforms, since 6
; bytes can be stored in a 64-bit bit buffer before it has to be emptied.

%macro EMIT_BYTE 0
    sub         put_bits, 8             ; put_bits -= 8;
    mov         edx, put_buffer
    mov         ecx, put_bits
    shr         edx, cl                 ; c = (JOCTET)GETJOCTET(put_buffer >> put_bits);
    mov         byte [eax], dl          ; *buffer++ = c;
    add         eax, 1
    cmp         dl, 0xFF                ; need to stuff a zero byte?
    jne         %%.EMIT_BYTE_END
    mov         byte [eax], 0           ; *buffer++ = 0;
    add         eax, 1
%%.EMIT_BYTE_END:
%endmacro

%macro PUT_BITS 1
    add         put_bits, ecx           ; put_bits += size;
    shl         put_buffer, cl          ; put_buffer = (put_buffer << size);
    or          put_buffer, %1
%endmacro

%macro CHECKBUF15 0
    cmp         put_bits, 16            ; if (put_bits > 31) {
    jl          %%.CHECKBUF15_END
    mov         eax, POINTER [esp+buffer]
    EMIT_BYTE
    EMIT_BYTE
    mov         POINTER [esp+buffer], eax
%%.CHECKBUF15_END:
%endmacro

%macro EMIT_BITS 1
    PUT_BITS    %1
    CHECKBUF15
%endmacro

%macro kloop_prepare 37                 ;(ko, jno0, ..., jno31, xmm0, xmm1, xmm2, xmm3)
    pxor        xmm4, xmm4              ; __m128i neg = _mm_setzero_si128();
    pxor        xmm5, xmm5              ; __m128i neg = _mm_setzero_si128();
    pxor        xmm6, xmm6              ; __m128i neg = _mm_setzero_si128();
    pxor        xmm7, xmm7              ; __m128i neg = _mm_setzero_si128();
    pinsrw      %34, word [esi + %2  * SIZEOF_WORD], 0  ; xmm_shadow[0] = block[jno0];
    pinsrw      %35, word [esi + %10 * SIZEOF_WORD], 0  ; xmm_shadow[8] = block[jno8];
    pinsrw      %36, word [esi + %18 * SIZEOF_WORD], 0  ; xmm_shadow[16] = block[jno16];
    pinsrw      %37, word [esi + %26 * SIZEOF_WORD], 0  ; xmm_shadow[24] = block[jno24];
    pinsrw      %34, word [esi + %3  * SIZEOF_WORD], 1  ; xmm_shadow[1] = block[jno1];
    pinsrw      %35, word [esi + %11 * SIZEOF_WORD], 1  ; xmm_shadow[9] = block[jno9];
    pinsrw      %36, word [esi + %19 * SIZEOF_WORD], 1  ; xmm_shadow[17] = block[jno17];
    pinsrw      %37, word [esi + %27 * SIZEOF_WORD], 1  ; xmm_shadow[25] = block[jno25];
    pinsrw      %34, word [esi + %4  * SIZEOF_WORD], 2  ; xmm_shadow[2] = block[jno2];
    pinsrw      %35, word [esi + %12 * SIZEOF_WORD], 2  ; xmm_shadow[10] = block[jno10];
    pinsrw      %36, word [esi + %20 * SIZEOF_WORD], 2  ; xmm_shadow[18] = block[jno18];
    pinsrw      %37, word [esi + %28 * SIZEOF_WORD], 2  ; xmm_shadow[26] = block[jno26];
    pinsrw      %34, word [esi + %5  * SIZEOF_WORD], 3  ; xmm_shadow[3] = block[jno3];
    pinsrw      %35, word [esi + %13 * SIZEOF_WORD], 3  ; xmm_shadow[11] = block[jno11];
    pinsrw      %36, word [esi + %21 * SIZEOF_WORD], 3  ; xmm_shadow[19] = block[jno19];
    pinsrw      %37, word [esi + %29 * SIZEOF_WORD], 3  ; xmm_shadow[27] = block[jno27];
    pinsrw      %34, word [esi + %6  * SIZEOF_WORD], 4  ; xmm_shadow[4] = block[jno4];
    pinsrw      %35, word [esi + %14 * SIZEOF_WORD], 4  ; xmm_shadow[12] = block[jno12];
    pinsrw      %36, word [esi + %22 * SIZEOF_WORD], 4  ; xmm_shadow[20] = block[jno20];
    pinsrw      %37, word [esi + %30 * SIZEOF_WORD], 4  ; xmm_shadow[28] = block[jno28];
    pinsrw      %34, word [esi + %7  * SIZEOF_WORD], 5  ; xmm_shadow[5] = block[jno5];
    pinsrw      %35, word [esi + %15 * SIZEOF_WORD], 5  ; xmm_shadow[13] = block[jno13];
    pinsrw      %36, word [esi + %23 * SIZEOF_WORD], 5  ; xmm_shadow[21] = block[jno21];
    pinsrw      %37, word [esi + %31 * SIZEOF_WORD], 5  ; xmm_shadow[29] = block[jno29];
    pinsrw      %34, word [esi + %8  * SIZEOF_WORD], 6  ; xmm_shadow[6] = block[jno6];
    pinsrw      %35, word [esi + %16 * SIZEOF_WORD], 6  ; xmm_shadow[14] = block[jno14];
    pinsrw      %36, word [esi + %24 * SIZEOF_WORD], 6  ; xmm_shadow[22] = block[jno22];
    pinsrw      %37, word [esi + %32 * SIZEOF_WORD], 6  ; xmm_shadow[30] = block[jno30];
    pinsrw      %34, word [esi + %9  * SIZEOF_WORD], 7  ; xmm_shadow[7] = block[jno7];
    pinsrw      %35, word [esi + %17 * SIZEOF_WORD], 7  ; xmm_shadow[15] = block[jno15];
    pinsrw      %36, word [esi + %25 * SIZEOF_WORD], 7  ; xmm_shadow[23] = block[jno23];
%if %1 != 32
    pinsrw      %37, word [esi + %33 * SIZEOF_WORD], 7  ; xmm_shadow[31] = block[jno31];
%else
    pinsrw      %37, ecx, 7             ; xmm_shadow[31] = block[jno31];
%endif
    pcmpgtw     xmm4, %34               ; neg = _mm_cmpgt_epi16(neg, x1);
    pcmpgtw     xmm5, %35               ; neg = _mm_cmpgt_epi16(neg, x1);
    pcmpgtw     xmm6, %36               ; neg = _mm_cmpgt_epi16(neg, x1);
    pcmpgtw     xmm7, %37               ; neg = _mm_cmpgt_epi16(neg, x1);
    paddw       %34, xmm4               ; x1 = _mm_add_epi16(x1, neg);
    paddw       %35, xmm5               ; x1 = _mm_add_epi16(x1, neg);
    paddw       %36, xmm6               ; x1 = _mm_add_epi16(x1, neg);
    paddw       %37, xmm7               ; x1 = _mm_add_epi16(x1, neg);
    pxor        %34, xmm4               ; x1 = _mm_xor_si128(x1, neg);
    pxor        %35, xmm5               ; x1 = _mm_xor_si128(x1, neg);
    pxor        %36, xmm6               ; x1 = _mm_xor_si128(x1, neg);
    pxor        %37, xmm7               ; x1 = _mm_xor_si128(x1, neg);
    pxor        xmm4, %34               ; neg = _mm_xor_si128(neg, x1);
    pxor        xmm5, %35               ; neg = _mm_xor_si128(neg, x1);
    pxor        xmm6, %36               ; neg = _mm_xor_si128(neg, x1);
    pxor        xmm7, %37               ; neg = _mm_xor_si128(neg, x1);
    movdqa      XMMWORD [esp + t1 + %1 * SIZEOF_WORD], %34          ; _mm_storeu_si128((__m128i *)(t1 + ko), x1);
    movdqa      XMMWORD [esp + t1 + (%1 + 8) * SIZEOF_WORD], %35    ; _mm_storeu_si128((__m128i *)(t1 + ko + 8), x1);
    movdqa      XMMWORD [esp + t1 + (%1 + 16) * SIZEOF_WORD], %36   ; _mm_storeu_si128((__m128i *)(t1 + ko + 16), x1);
    movdqa      XMMWORD [esp + t1 + (%1 + 24) * SIZEOF_WORD], %37   ; _mm_storeu_si128((__m128i *)(t1 + ko + 24), x1);
    movdqa      XMMWORD [esp + t2 + %1 * SIZEOF_WORD], xmm4         ; _mm_storeu_si128((__m128i *)(t2 + ko), neg);
    movdqa      XMMWORD [esp + t2 + (%1 + 8) * SIZEOF_WORD], xmm5   ; _mm_storeu_si128((__m128i *)(t2 + ko + 8), neg);
    movdqa      XMMWORD [esp + t2 + (%1 + 16) * SIZEOF_WORD], xmm6  ; _mm_storeu_si128((__m128i *)(t2 + ko + 16), neg);
    movdqa      XMMWORD [esp + t2 + (%1 + 24) * SIZEOF_WORD], xmm7  ; _mm_storeu_si128((__m128i *)(t2 + ko + 24), neg);
%endmacro

;
; Encode a single block's worth of coefficients.
;
; GLOBAL(JOCTET *)
; jsimd_huff_encode_one_block_sse2(working_state *state, JOCTET *buffer,
;                                  JCOEFPTR block, int last_dc_val,
;                                  c_derived_tbl *dctbl, c_derived_tbl *actbl)
;

; eax + 8 = working_state *state
; eax + 12 = JOCTET *buffer
; eax + 16 = JCOEFPTR block
; eax + 20 = int last_dc_val
; eax + 24 = c_derived_tbl *dctbl
; eax + 28 = c_derived_tbl *actbl

%define pad         6 * SIZEOF_DWORD    ; Align to 16 bytes
%define t1          pad
%define t2          t1 + (DCTSIZE2 * SIZEOF_WORD)
%define block       t2 + (DCTSIZE2 * SIZEOF_WORD)
%define actbl       block + SIZEOF_DWORD
%define buffer      actbl + SIZEOF_DWORD
%define temp        buffer + SIZEOF_DWORD
%define temp2       temp + SIZEOF_DWORD
%define temp3       temp2 + SIZEOF_DWORD
%define temp4       temp3 + SIZEOF_DWORD
%define temp5       temp4 + SIZEOF_DWORD
%define gotptr      temp5 + SIZEOF_DWORD  ; void *gotptr
%define put_buffer  ebx
%define put_bits    edi

    align       32
    GLOBAL_FUNCTION(jsimd_huff_encode_one_block_sse2)

EXTN(jsimd_huff_encode_one_block_sse2):
    push        ebp
    mov         eax, esp                     ; eax = original ebp
    sub         esp, byte 4
    and         esp, byte (-SIZEOF_XMMWORD)  ; align to 128 bits
    mov         [esp], eax
    mov         ebp, esp                     ; ebp = aligned ebp
    sub         esp, temp5+9*SIZEOF_DWORD-pad
    push        ebx
    push        ecx
;   push        edx                     ; need not be preserved
    push        esi
    push        edi
    push        ebp

    mov         esi, POINTER [eax+8]       ; (working_state *state)
    mov         put_buffer, dword [esi+8]  ; put_buffer = state->cur.put_buffer;
    mov         put_bits, dword [esi+12]   ; put_bits = state->cur.put_bits;
    push        esi                        ; esi is now scratch

    get_GOT     edx                        ; get GOT address
    movpic      POINTER [esp+gotptr], edx  ; save GOT address

    mov         ecx, POINTER [eax+28]
    mov         edx, POINTER [eax+16]
    mov         esi, POINTER [eax+12]
    mov         POINTER [esp+actbl], ecx
    mov         POINTER [esp+block], edx
    mov         POINTER [esp+buffer], esi

    ; Encode the DC coefficient difference per section F.1.2.1
    mov         esi, POINTER [esp+block]  ; block
    movsx       ecx, word [esi]           ; temp = temp2 = block[0] - last_dc_val;
    sub         ecx, dword [eax+20]
    mov         esi, ecx

    ; This is a well-known technique for obtaining the absolute value
    ; with out a branch.  It is derived from an assembly language technique
    ; presented in "How to Optimize for the Pentium Processors",
    ; Copyright (c) 1996, 1997 by Agner Fog.
    mov         edx, ecx
    sar         edx, 31                 ; temp3 = temp >> (CHAR_BIT * sizeof(int) - 1);
    xor         ecx, edx                ; temp ^= temp3;
    sub         ecx, edx                ; temp -= temp3;

    ; For a negative input, want temp2 = bitwise complement of abs(input)
    ; This code assumes we are on a two's complement machine
    add         esi, edx                ; temp2 += temp3;
    mov         dword [esp+temp], esi   ; backup temp2 in temp

    ; Find the number of bits needed for the magnitude of the coefficient
    movpic      ebp, POINTER [esp+gotptr]                        ; load GOT address (ebp)
    movzx       edx, byte [GOTOFF(ebp, jpeg_nbits_table + ecx)]  ; nbits = JPEG_NBITS(temp);
    mov         dword [esp+temp2], edx                           ; backup nbits in temp2

    ; Emit the Huffman-coded symbol for the number of bits
    mov         ebp, POINTER [eax+24]         ; After this point, arguments are not accessible anymore
    mov         eax,  INT [ebp + edx * 4]     ; code = dctbl->ehufco[nbits];
    movzx       ecx, byte [ebp + edx + 1024]  ; size = dctbl->ehufsi[nbits];
    EMIT_BITS   eax                           ; EMIT_BITS(code, size)

    mov         ecx, dword [esp+temp2]        ; restore nbits

    ; Mask off any extra bits in code
    mov         eax, 1
    shl         eax, cl
    dec         eax
    and         eax, dword [esp+temp]   ; temp2 &= (((JLONG)1)<<nbits) - 1;

    ; Emit that number of bits of the value, if positive,
    ; or the complement of its magnitude, if negative.
    EMIT_BITS   eax                     ; EMIT_BITS(temp2, nbits)

    ; Prepare data
    xor         ecx, ecx
    mov         esi, POINTER [esp+block]
    kloop_prepare  0,  1,  8,  16, 9,  2,  3,  10, 17, 24, 32, 25, \
                   18, 11, 4,  5,  12, 19, 26, 33, 40, 48, 41, 34, \
                   27, 20, 13, 6,  7,  14, 21, 28, 35, \
                   xmm0, xmm1, xmm2, xmm3
    kloop_prepare  32, 42, 49, 56, 57, 50, 43, 36, 29, 22, 15, 23, \
                   30, 37, 44, 51, 58, 59, 52, 45, 38, 31, 39, 46, \
                   53, 60, 61, 54, 47, 55, 62, 63, 63, \
                   xmm0, xmm1, xmm2, xmm3

    pxor        xmm7, xmm7
    movdqa      xmm0, XMMWORD [esp + t1 + 0 * SIZEOF_WORD]   ; __m128i tmp0 = _mm_loadu_si128((__m128i *)(t1 + 0));
    movdqa      xmm1, XMMWORD [esp + t1 + 8 * SIZEOF_WORD]   ; __m128i tmp1 = _mm_loadu_si128((__m128i *)(t1 + 8));
    movdqa      xmm2, XMMWORD [esp + t1 + 16 * SIZEOF_WORD]  ; __m128i tmp2 = _mm_loadu_si128((__m128i *)(t1 + 16));
    movdqa      xmm3, XMMWORD [esp + t1 + 24 * SIZEOF_WORD]  ; __m128i tmp3 = _mm_loadu_si128((__m128i *)(t1 + 24));
    pcmpeqw     xmm0, xmm7              ; tmp0 = _mm_cmpeq_epi16(tmp0, zero);
    pcmpeqw     xmm1, xmm7              ; tmp1 = _mm_cmpeq_epi16(tmp1, zero);
    pcmpeqw     xmm2, xmm7              ; tmp2 = _mm_cmpeq_epi16(tmp2, zero);
    pcmpeqw     xmm3, xmm7              ; tmp3 = _mm_cmpeq_epi16(tmp3, zero);
    packsswb    xmm0, xmm1              ; tmp0 = _mm_packs_epi16(tmp0, tmp1);
    packsswb    xmm2, xmm3              ; tmp2 = _mm_packs_epi16(tmp2, tmp3);
    pmovmskb    edx, xmm0               ; index  = ((uint64_t)_mm_movemask_epi8(tmp0)) << 0;
    pmovmskb    ecx, xmm2               ; index  = ((uint64_t)_mm_movemask_epi8(tmp2)) << 16;
    shl         ecx, 16
    or          edx, ecx
    not         edx                     ; index = ~index;

    lea         esi, [esp+t1]
    mov         ebp, POINTER [esp+actbl]  ; ebp = actbl

.BLOOP:
    bsf         ecx, edx                ; r = __builtin_ctzl(index);
    jz          near .ELOOP
    lea         esi, [esi+ecx*2]        ; k += r;
    shr         edx, cl                 ; index >>= r;
    mov         dword [esp+temp3], edx
.BRLOOP:
    cmp         ecx, 16                       ; while (r > 15) {
    jl          near .ERLOOP
    sub         ecx, 16                       ; r -= 16;
    mov         dword [esp+temp], ecx
    mov         eax, INT [ebp + 240 * 4]      ; code_0xf0 = actbl->ehufco[0xf0];
    movzx       ecx, byte [ebp + 1024 + 240]  ; size_0xf0 = actbl->ehufsi[0xf0];
    EMIT_BITS   eax                           ; EMIT_BITS(code_0xf0, size_0xf0)
    mov         ecx, dword [esp+temp]
    jmp         .BRLOOP
.ERLOOP:
    movsx       eax, word [esi]                                  ; temp = t1[k];
    movpic      edx, POINTER [esp+gotptr]                        ; load GOT address (edx)
    movzx       eax, byte [GOTOFF(edx, jpeg_nbits_table + eax)]  ; nbits = JPEG_NBITS(temp);
    mov         dword [esp+temp2], eax
    ; Emit Huffman symbol for run length / number of bits
    shl         ecx, 4                        ; temp3 = (r << 4) + nbits;
    add         ecx, eax
    mov         eax,  INT [ebp + ecx * 4]     ; code = actbl->ehufco[temp3];
    movzx       ecx, byte [ebp + ecx + 1024]  ; size = actbl->ehufsi[temp3];
    EMIT_BITS   eax

    movsx       edx, word [esi+DCTSIZE2*2]    ; temp2 = t2[k];
    ; Mask off any extra bits in code
    mov         ecx, dword [esp+temp2]
    mov         eax, 1
    shl         eax, cl
    dec         eax
    and         eax, edx                ; temp2 &= (((JLONG)1)<<nbits) - 1;
    EMIT_BITS   eax                     ; PUT_BITS(temp2, nbits)
    mov         edx, dword [esp+temp3]
    add         esi, 2                  ; ++k;
    shr         edx, 1                  ; index >>= 1;

    jmp         .BLOOP
.ELOOP:
    movdqa      xmm0, XMMWORD [esp + t1 + 32 * SIZEOF_WORD]  ; __m128i tmp0 = _mm_loadu_si128((__m128i *)(t1 + 0));
    movdqa      xmm1, XMMWORD [esp + t1 + 40 * SIZEOF_WORD]  ; __m128i tmp1 = _mm_loadu_si128((__m128i *)(t1 + 8));
    movdqa      xmm2, XMMWORD [esp + t1 + 48 * SIZEOF_WORD]  ; __m128i tmp2 = _mm_loadu_si128((__m128i *)(t1 + 16));
    movdqa      xmm3, XMMWORD [esp + t1 + 56 * SIZEOF_WORD]  ; __m128i tmp3 = _mm_loadu_si128((__m128i *)(t1 + 24));
    pcmpeqw     xmm0, xmm7              ; tmp0 = _mm_cmpeq_epi16(tmp0, zero);
    pcmpeqw     xmm1, xmm7              ; tmp1 = _mm_cmpeq_epi16(tmp1, zero);
    pcmpeqw     xmm2, xmm7              ; tmp2 = _mm_cmpeq_epi16(tmp2, zero);
    pcmpeqw     xmm3, xmm7              ; tmp3 = _mm_cmpeq_epi16(tmp3, zero);
    packsswb    xmm0, xmm1              ; tmp0 = _mm_packs_epi16(tmp0, tmp1);
    packsswb    xmm2, xmm3              ; tmp2 = _mm_packs_epi16(tmp2, tmp3);
    pmovmskb    edx, xmm0               ; index  = ((uint64_t)_mm_movemask_epi8(tmp0)) << 0;
    pmovmskb    ecx, xmm2               ; index  = ((uint64_t)_mm_movemask_epi8(tmp2)) << 16;
    shl         ecx, 16
    or          edx, ecx
    not         edx                     ; index = ~index;

    lea         eax, [esp + t1 + (DCTSIZE2/2) * 2]
    sub         eax, esi
    shr         eax, 1
    bsf         ecx, edx                ; r = __builtin_ctzl(index);
    jz          near .ELOOP2
    shr         edx, cl                 ; index >>= r;
    add         ecx, eax
    lea         esi, [esi+ecx*2]        ; k += r;
    mov         dword [esp+temp3], edx
    jmp         .BRLOOP2
.BLOOP2:
    bsf         ecx, edx                ; r = __builtin_ctzl(index);
    jz          near .ELOOP2
    lea         esi, [esi+ecx*2]        ; k += r;
    shr         edx, cl                 ; index >>= r;
    mov         dword [esp+temp3], edx
.BRLOOP2:
    cmp         ecx, 16                       ; while (r > 15) {
    jl          near .ERLOOP2
    sub         ecx, 16                       ; r -= 16;
    mov         dword [esp+temp], ecx
    mov         eax, INT [ebp + 240 * 4]      ; code_0xf0 = actbl->ehufco[0xf0];
    movzx       ecx, byte [ebp + 1024 + 240]  ; size_0xf0 = actbl->ehufsi[0xf0];
    EMIT_BITS   eax                           ; EMIT_BITS(code_0xf0, size_0xf0)
    mov         ecx, dword [esp+temp]
    jmp         .BRLOOP2
.ERLOOP2:
    movsx       eax, word [esi]         ; temp = t1[k];
    bsr         eax, eax                ; nbits = 32 - __builtin_clz(temp);
    inc         eax
    mov         dword [esp+temp2], eax
    ; Emit Huffman symbol for run length / number of bits
    shl         ecx, 4                        ; temp3 = (r << 4) + nbits;
    add         ecx, eax
    mov         eax,  INT [ebp + ecx * 4]     ; code = actbl->ehufco[temp3];
    movzx       ecx, byte [ebp + ecx + 1024]  ; size = actbl->ehufsi[temp3];
    EMIT_BITS   eax

    movsx       edx, word [esi+DCTSIZE2*2]    ; temp2 = t2[k];
    ; Mask off any extra bits in code
    mov         ecx, dword [esp+temp2]
    mov         eax, 1
    shl         eax, cl
    dec         eax
    and         eax, edx                ; temp2 &= (((JLONG)1)<<nbits) - 1;
    EMIT_BITS   eax                     ; PUT_BITS(temp2, nbits)
    mov         edx, dword [esp+temp3]
    add         esi, 2                  ; ++k;
    shr         edx, 1                  ; index >>= 1;

    jmp         .BLOOP2
.ELOOP2:
    ; If the last coef(s) were zero, emit an end-of-block code
    lea         edx, [esp + t1 + (DCTSIZE2-1) * 2]  ; r = DCTSIZE2-1-k;
    cmp         edx, esi                            ; if (r > 0) {
    je          .EFN
    mov         eax,  INT [ebp]                     ; code = actbl->ehufco[0];
    movzx       ecx, byte [ebp + 1024]              ; size = actbl->ehufsi[0];
    EMIT_BITS   eax
.EFN:
    mov         eax, [esp+buffer]
    pop         esi
    ; Save put_buffer & put_bits
    mov         dword [esi+8], put_buffer  ; state->cur.put_buffer = put_buffer;
    mov         dword [esi+12], put_bits   ; state->cur.put_bits = put_bits;

    pop         ebp
    pop         edi
    pop         esi
;   pop         edx                     ; need not be preserved
    pop         ecx
    pop         ebx
    mov         esp, ebp                ; esp <- aligned ebp
    pop         esp                     ; esp <- original ebp
    pop         ebp
    ret

; For some reason, the OS X linker does not honor the request to align the
; segment unless we do this.
    align       32
