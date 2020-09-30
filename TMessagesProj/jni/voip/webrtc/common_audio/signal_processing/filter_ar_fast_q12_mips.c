/*
 *  Copyright (c) 2013 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "rtc_base/checks.h"
#include "common_audio/signal_processing/include/signal_processing_library.h"

void WebRtcSpl_FilterARFastQ12(const int16_t* data_in,
                               int16_t* data_out,
                               const int16_t* __restrict coefficients,
                               size_t coefficients_length,
                               size_t data_length) {
  int r0, r1, r2, r3;
  int coef0, offset;
  int i, j, k;
  int coefptr, outptr, tmpout, inptr;
#if !defined(MIPS_DSP_R1_LE)
  int max16 = 0x7FFF;
  int min16 = 0xFFFF8000;
#endif  // #if !defined(MIPS_DSP_R1_LE)

  RTC_DCHECK_GT(data_length, 0);
  RTC_DCHECK_GT(coefficients_length, 1);

  __asm __volatile (
    ".set       push                                             \n\t"
    ".set       noreorder                                        \n\t"
    "addiu      %[i],       %[data_length],          0           \n\t"
    "lh         %[coef0],   0(%[coefficients])                   \n\t"
    "addiu      %[j],       %[coefficients_length],  -1          \n\t"
    "andi       %[k],       %[j],                    1           \n\t"
    "sll        %[offset],  %[j],                    1           \n\t"
    "subu       %[outptr],  %[data_out],             %[offset]   \n\t"
    "addiu      %[inptr],   %[data_in],              0           \n\t"
    "bgtz       %[k],       3f                                   \n\t"
    " addu      %[coefptr], %[coefficients],         %[offset]   \n\t"
   "1:                                                           \n\t"
    "lh         %[r0],      0(%[inptr])                          \n\t"
    "addiu      %[i],       %[i],                    -1          \n\t"
    "addiu      %[tmpout],  %[outptr],               0           \n\t"
    "mult       %[r0],      %[coef0]                             \n\t"
   "2:                                                           \n\t"
    "lh         %[r0],      0(%[tmpout])                         \n\t"
    "lh         %[r1],      0(%[coefptr])                        \n\t"
    "lh         %[r2],      2(%[tmpout])                         \n\t"
    "lh         %[r3],      -2(%[coefptr])                       \n\t"
    "addiu      %[tmpout],  %[tmpout],               4           \n\t"
    "msub       %[r0],      %[r1]                                \n\t"
    "msub       %[r2],      %[r3]                                \n\t"
    "addiu      %[j],       %[j],                    -2          \n\t"
    "bgtz       %[j],       2b                                   \n\t"
    " addiu     %[coefptr], %[coefptr],              -4          \n\t"
#if defined(MIPS_DSP_R1_LE)
    "extr_r.w   %[r0],      $ac0,                    12          \n\t"
#else  // #if defined(MIPS_DSP_R1_LE)
    "mflo       %[r0]                                            \n\t"
#endif  // #if defined(MIPS_DSP_R1_LE)
    "addu       %[coefptr], %[coefficients],         %[offset]   \n\t"
    "addiu      %[inptr],   %[inptr],                2           \n\t"
    "addiu      %[j],       %[coefficients_length],  -1          \n\t"
#if defined(MIPS_DSP_R1_LE)
    "shll_s.w   %[r0],      %[r0],                   16          \n\t"
    "sra        %[r0],      %[r0],                   16          \n\t"
#else  // #if defined(MIPS_DSP_R1_LE)
    "addiu      %[r0],      %[r0],                   2048        \n\t"
    "sra        %[r0],      %[r0],                   12          \n\t"
    "slt        %[r1],      %[max16],                %[r0]       \n\t"
    "movn       %[r0],      %[max16],                %[r1]       \n\t"
    "slt        %[r1],      %[r0],                   %[min16]    \n\t"
    "movn       %[r0],      %[min16],                %[r1]       \n\t"
#endif  // #if defined(MIPS_DSP_R1_LE)
    "sh         %[r0],      0(%[tmpout])                         \n\t"
    "bgtz       %[i],       1b                                   \n\t"
    " addiu     %[outptr],  %[outptr],               2           \n\t"
    "b          5f                                               \n\t"
    " nop                                                        \n\t"
   "3:                                                           \n\t"
    "lh         %[r0],      0(%[inptr])                          \n\t"
    "addiu      %[i],       %[i],                    -1          \n\t"
    "addiu      %[tmpout],  %[outptr],               0           \n\t"
    "mult       %[r0],      %[coef0]                             \n\t"
   "4:                                                           \n\t"
    "lh         %[r0],      0(%[tmpout])                         \n\t"
    "lh         %[r1],      0(%[coefptr])                        \n\t"
    "lh         %[r2],      2(%[tmpout])                         \n\t"
    "lh         %[r3],      -2(%[coefptr])                       \n\t"
    "addiu      %[tmpout],  %[tmpout],               4           \n\t"
    "msub       %[r0],      %[r1]                                \n\t"
    "msub       %[r2],      %[r3]                                \n\t"
    "addiu      %[j],       %[j],                    -2          \n\t"
    "bgtz       %[j],       4b                                   \n\t"
    " addiu     %[coefptr], %[coefptr],              -4          \n\t"
    "lh         %[r0],      0(%[tmpout])                         \n\t"
    "lh         %[r1],      0(%[coefptr])                        \n\t"
    "msub       %[r0],      %[r1]                                \n\t"
#if defined(MIPS_DSP_R1_LE)
    "extr_r.w   %[r0],      $ac0,                    12          \n\t"
#else  // #if defined(MIPS_DSP_R1_LE)
    "mflo       %[r0]                                            \n\t"
#endif  // #if defined(MIPS_DSP_R1_LE)
    "addu       %[coefptr], %[coefficients],         %[offset]   \n\t"
    "addiu      %[inptr],   %[inptr],                2           \n\t"
    "addiu      %[j],       %[coefficients_length],  -1          \n\t"
#if defined(MIPS_DSP_R1_LE)
    "shll_s.w   %[r0],      %[r0],                   16          \n\t"
    "sra        %[r0],      %[r0],                   16          \n\t"
#else  // #if defined(MIPS_DSP_R1_LE)
    "addiu      %[r0],      %[r0],                   2048        \n\t"
    "sra        %[r0],      %[r0],                   12          \n\t"
    "slt        %[r1],      %[max16],                %[r0]       \n\t"
    "movn       %[r0],      %[max16],                %[r1]       \n\t"
    "slt        %[r1],      %[r0],                   %[min16]    \n\t"
    "movn       %[r0],      %[min16],                %[r1]       \n\t"
#endif  // #if defined(MIPS_DSP_R1_LE)
    "sh         %[r0],      2(%[tmpout])                         \n\t"
    "bgtz       %[i],       3b                                   \n\t"
    " addiu     %[outptr],  %[outptr],               2           \n\t"
   "5:                                                           \n\t"
    ".set       pop                                              \n\t"
    : [i] "=&r" (i), [j] "=&r" (j), [k] "=&r" (k), [r0] "=&r" (r0),
      [r1] "=&r" (r1), [r2] "=&r" (r2), [r3] "=&r" (r3),
      [coef0] "=&r" (coef0), [offset] "=&r" (offset),
      [outptr] "=&r" (outptr), [inptr] "=&r" (inptr),
      [coefptr] "=&r" (coefptr), [tmpout] "=&r" (tmpout)
    : [coefficients] "r" (coefficients), [data_length] "r" (data_length),
      [coefficients_length] "r" (coefficients_length),
#if !defined(MIPS_DSP_R1_LE)
      [max16] "r" (max16), [min16] "r" (min16),
#endif
      [data_out] "r" (data_out), [data_in] "r" (data_in)
    : "hi", "lo", "memory"
  );
}

