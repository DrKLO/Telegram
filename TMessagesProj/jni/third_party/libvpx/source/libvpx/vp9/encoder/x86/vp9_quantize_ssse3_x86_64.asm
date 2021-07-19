;
;  Copyright (c) 2010 The WebM project authors. All Rights Reserved.
;
;  Use of this source code is governed by a BSD-style license
;  that can be found in the LICENSE file in the root of the source
;  tree. An additional intellectual property rights grant can be found
;  in the file PATENTS.  All contributing project authors may
;  be found in the AUTHORS file in the root of the source tree.
;

%define private_prefix vp9

%include "third_party/x86inc/x86inc.asm"
%include "vpx_dsp/x86/bitdepth_conversion_sse2.asm"

SECTION_RODATA
pw_1: times 8 dw 1

SECTION .text

%macro QUANTIZE_FP 2
cglobal quantize_%1, 0, %2, 15, coeff, ncoeff, skip, round, quant, \
                                qcoeff, dqcoeff, dequant, \
                                eob, scan, iscan

  ; actual quantize loop - setup pointers, rounders, etc.
  movifnidn                   coeffq, coeffmp
  movifnidn                  ncoeffq, ncoeffmp
  mov                             r2, dequantmp
  movifnidn                   roundq, roundmp
  movifnidn                   quantq, quantmp
  mova                            m1, [roundq]             ; m1 = round
  mova                            m2, [quantq]             ; m2 = quant
%ifidn %1, fp_32x32
  pcmpeqw                         m5, m5
  psrlw                           m5, 15
  paddw                           m1, m5
  psrlw                           m1, 1                    ; m1 = (m1 + 1) / 2
%endif
  mova                            m3, [r2q]                ; m3 = dequant
  mov                             r3, qcoeffmp
  mov                             r4, dqcoeffmp
  mov                             r5, iscanmp
%ifidn %1, fp_32x32
  psllw                           m2, 1
%endif
  pxor                            m5, m5                   ; m5 = dedicated zero

  INCREMENT_ELEMENTS_TRAN_LOW coeffq, ncoeffq
  lea                            r5q, [r5q+ncoeffq*2]
  INCREMENT_ELEMENTS_TRAN_LOW    r3q, ncoeffq
  INCREMENT_ELEMENTS_TRAN_LOW    r4q, ncoeffq
  neg                        ncoeffq

  ; get DC and first 15 AC coeffs
  LOAD_TRAN_LOW  9, coeffq, ncoeffq                        ; m9 = c[i]
  LOAD_TRAN_LOW 10, coeffq, ncoeffq + 8                    ; m10 = c[i]
  pabsw                           m6, m9                   ; m6 = abs(m9)
  pabsw                          m11, m10                  ; m11 = abs(m10)
  pcmpeqw                         m7, m7

  paddsw                          m6, m1                   ; m6 += round
  punpckhqdq                      m1, m1
  paddsw                         m11, m1                   ; m11 += round
  pmulhw                          m8, m6, m2               ; m8 = m6*q>>16
  punpckhqdq                      m2, m2
  pmulhw                         m13, m11, m2              ; m13 = m11*q>>16
  psignw                          m8, m9                   ; m8 = reinsert sign
  psignw                         m13, m10                  ; m13 = reinsert sign
  STORE_TRAN_LOW  8, r3q, ncoeffq,     6, 11, 12
  STORE_TRAN_LOW 13, r3q, ncoeffq + 8, 6, 11, 12
%ifidn %1, fp_32x32
  pabsw                           m8, m8
  pabsw                          m13, m13
%endif
  pmullw                          m8, m3                   ; r4[i] = r3[i] * q
  punpckhqdq                      m3, m3
  pmullw                         m13, m3                   ; r4[i] = r3[i] * q
%ifidn %1, fp_32x32
  psrlw                           m8, 1
  psrlw                          m13, 1
  psignw                          m8, m9
  psignw                         m13, m10
  psrlw                           m0, m3, 2
%else
  psrlw                           m0, m3, 1
%endif
  STORE_TRAN_LOW  8, r4q, ncoeffq,     6, 11, 12
  STORE_TRAN_LOW 13, r4q, ncoeffq + 8, 6, 11, 12
  pcmpeqw                         m8, m5                   ; m8 = c[i] == 0
  pcmpeqw                        m13, m5                   ; m13 = c[i] == 0
  mova                            m6, [  r5q+ncoeffq*2+ 0] ; m6 = scan[i]
  mova                           m11, [  r5q+ncoeffq*2+16] ; m11 = scan[i]
  psubw                           m6, m7                   ; m6 = scan[i] + 1
  psubw                          m11, m7                   ; m11 = scan[i] + 1
  pandn                           m8, m6                   ; m8 = max(eob)
  pandn                          m13, m11                  ; m13 = max(eob)
  pmaxsw                          m8, m13
  add                        ncoeffq, mmsize
  jz .accumulate_eob

.ac_only_loop:
  LOAD_TRAN_LOW  9, coeffq, ncoeffq                        ; m9 = c[i]
  LOAD_TRAN_LOW 10, coeffq, ncoeffq + 8                    ; m10 = c[i]
  pabsw                           m6, m9                   ; m6 = abs(m9)
  pabsw                          m11, m10                  ; m11 = abs(m10)

  pcmpgtw                         m7, m6,  m0
  pcmpgtw                        m12, m11, m0
  pmovmskb                       r6d, m7
  pmovmskb                       r2d, m12

  or                              r6, r2
  jz .skip_iter

  pcmpeqw                         m7, m7

  paddsw                          m6, m1                   ; m6 += round
  paddsw                         m11, m1                   ; m11 += round
  pmulhw                         m14, m6, m2               ; m14 = m6*q>>16
  pmulhw                         m13, m11, m2              ; m13 = m11*q>>16
  psignw                         m14, m9                   ; m14 = reinsert sign
  psignw                         m13, m10                  ; m13 = reinsert sign
  STORE_TRAN_LOW 14, r3q, ncoeffq,     6, 11, 12
  STORE_TRAN_LOW 13, r3q, ncoeffq + 8, 6, 11, 12
%ifidn %1, fp_32x32
  pabsw                          m14, m14
  pabsw                          m13, m13
%endif
  pmullw                         m14, m3                   ; r4[i] = r3[i] * q
  pmullw                         m13, m3                   ; r4[i] = r3[i] * q
%ifidn %1, fp_32x32
  psrlw                          m14, 1
  psrlw                          m13, 1
  psignw                         m14, m9
  psignw                         m13, m10
%endif
  STORE_TRAN_LOW 14, r4q, ncoeffq,     6, 11, 12
  STORE_TRAN_LOW 13, r4q, ncoeffq + 8, 6, 11, 12
  pcmpeqw                        m14, m5                   ; m14 = c[i] == 0
  pcmpeqw                        m13, m5                   ; m13 = c[i] == 0
  mova                            m6, [  r5q+ncoeffq*2+ 0] ; m6 = scan[i]
  mova                           m11, [  r5q+ncoeffq*2+16] ; m11 = scan[i]
  psubw                           m6, m7                   ; m6 = scan[i] + 1
  psubw                          m11, m7                   ; m11 = scan[i] + 1
  pandn                          m14, m6                   ; m14 = max(eob)
  pandn                          m13, m11                  ; m13 = max(eob)
  pmaxsw                          m8, m14
  pmaxsw                          m8, m13
  add                        ncoeffq, mmsize
  jl .ac_only_loop

  jmp .accumulate_eob
.skip_iter:
  STORE_ZERO_TRAN_LOW 5, r3q, ncoeffq
  STORE_ZERO_TRAN_LOW 5, r3q, ncoeffq + 8
  STORE_ZERO_TRAN_LOW 5, r4q, ncoeffq
  STORE_ZERO_TRAN_LOW 5, r4q, ncoeffq + 8
  add                        ncoeffq, mmsize
  jl .ac_only_loop

.accumulate_eob:
  ; horizontally accumulate/max eobs and write into [eob] memory pointer
  mov                             r2, eobmp
  pshufd                          m7, m8, 0xe
  pmaxsw                          m8, m7
  pshuflw                         m7, m8, 0xe
  pmaxsw                          m8, m7
  pshuflw                         m7, m8, 0x1
  pmaxsw                          m8, m7
  pextrw                          r6, m8, 0
  mov                           [r2], r6w
  RET
%endmacro

INIT_XMM ssse3
QUANTIZE_FP fp, 7
QUANTIZE_FP fp_32x32, 7
