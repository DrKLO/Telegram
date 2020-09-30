/*
 *  Copyright (c) 2013 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */


#include "common_audio/signal_processing/complex_fft_tables.h"
#include "common_audio/signal_processing/include/signal_processing_library.h"

#define CFFTSFT 14
#define CFFTRND 1
#define CFFTRND2 16384

#define CIFFTSFT 14
#define CIFFTRND 1

int WebRtcSpl_ComplexFFT(int16_t frfi[], int stages, int mode) {
  int i = 0;
  int l = 0;
  int k = 0;
  int istep = 0;
  int n = 0;
  int m = 0;
  int32_t wr = 0, wi = 0;
  int32_t tmp1 = 0;
  int32_t tmp2 = 0;
  int32_t tmp3 = 0;
  int32_t tmp4 = 0;
  int32_t tmp5 = 0;
  int32_t tmp6 = 0;
  int32_t tmp = 0;
  int16_t* ptr_j = NULL;
  int16_t* ptr_i = NULL;

  n = 1 << stages;
  if (n > 1024) {
    return -1;
  }

  __asm __volatile (
    ".set push                                                         \n\t"
    ".set noreorder                                                    \n\t"

    "addiu      %[k],           $zero,            10                   \n\t"
    "addiu      %[l],           $zero,            1                    \n\t"
   "3:                                                                 \n\t"
    "sll        %[istep],       %[l],             1                    \n\t"
    "move       %[m],           $zero                                  \n\t"
    "sll        %[tmp],         %[l],             2                    \n\t"
    "move       %[i],           $zero                                  \n\t"
   "2:                                                                 \n\t"
#if defined(MIPS_DSP_R1_LE)
    "sllv       %[tmp3],        %[m],             %[k]                 \n\t"
    "addiu      %[tmp2],        %[tmp3],          512                  \n\t"
    "addiu      %[m],           %[m],             1                    \n\t"
    "lhx        %[wi],          %[tmp3](%[kSinTable1024])              \n\t"
    "lhx        %[wr],          %[tmp2](%[kSinTable1024])              \n\t"
#else  // #if defined(MIPS_DSP_R1_LE)
    "sllv       %[tmp3],        %[m],             %[k]                 \n\t"
    "addu       %[ptr_j],       %[tmp3],          %[kSinTable1024]     \n\t"
    "addiu      %[ptr_i],       %[ptr_j],         512                  \n\t"
    "addiu      %[m],           %[m],             1                    \n\t"
    "lh         %[wi],          0(%[ptr_j])                            \n\t"
    "lh         %[wr],          0(%[ptr_i])                            \n\t"
#endif  // #if defined(MIPS_DSP_R1_LE)
   "1:                                                                 \n\t"
    "sll        %[tmp1],        %[i],             2                    \n\t"
    "addu       %[ptr_i],       %[frfi],          %[tmp1]              \n\t"
    "addu       %[ptr_j],       %[ptr_i],         %[tmp]               \n\t"
    "lh         %[tmp6],        0(%[ptr_i])                            \n\t"
    "lh         %[tmp5],        2(%[ptr_i])                            \n\t"
    "lh         %[tmp3],        0(%[ptr_j])                            \n\t"
    "lh         %[tmp4],        2(%[ptr_j])                            \n\t"
    "addu       %[i],           %[i],             %[istep]             \n\t"
#if defined(MIPS_DSP_R2_LE)
    "mult       %[wr],          %[tmp3]                                \n\t"
    "madd       %[wi],          %[tmp4]                                \n\t"
    "mult       $ac1,           %[wr],            %[tmp4]              \n\t"
    "msub       $ac1,           %[wi],            %[tmp3]              \n\t"
    "mflo       %[tmp1]                                                \n\t"
    "mflo       %[tmp2],        $ac1                                   \n\t"
    "sll        %[tmp6],        %[tmp6],          14                   \n\t"
    "sll        %[tmp5],        %[tmp5],          14                   \n\t"
    "shra_r.w   %[tmp1],        %[tmp1],          1                    \n\t"
    "shra_r.w   %[tmp2],        %[tmp2],          1                    \n\t"
    "subu       %[tmp4],        %[tmp6],          %[tmp1]              \n\t"
    "addu       %[tmp1],        %[tmp6],          %[tmp1]              \n\t"
    "addu       %[tmp6],        %[tmp5],          %[tmp2]              \n\t"
    "subu       %[tmp5],        %[tmp5],          %[tmp2]              \n\t"
    "shra_r.w   %[tmp1],        %[tmp1],          15                   \n\t"
    "shra_r.w   %[tmp6],        %[tmp6],          15                   \n\t"
    "shra_r.w   %[tmp4],        %[tmp4],          15                   \n\t"
    "shra_r.w   %[tmp5],        %[tmp5],          15                   \n\t"
#else  // #if defined(MIPS_DSP_R2_LE)
    "mul        %[tmp2],        %[wr],            %[tmp4]              \n\t"
    "mul        %[tmp1],        %[wr],            %[tmp3]              \n\t"
    "mul        %[tmp4],        %[wi],            %[tmp4]              \n\t"
    "mul        %[tmp3],        %[wi],            %[tmp3]              \n\t"
    "sll        %[tmp6],        %[tmp6],          14                   \n\t"
    "sll        %[tmp5],        %[tmp5],          14                   \n\t"
    "addiu      %[tmp6],        %[tmp6],          16384                \n\t"
    "addiu      %[tmp5],        %[tmp5],          16384                \n\t"
    "addu       %[tmp1],        %[tmp1],          %[tmp4]              \n\t"
    "subu       %[tmp2],        %[tmp2],          %[tmp3]              \n\t"
    "addiu      %[tmp1],        %[tmp1],          1                    \n\t"
    "addiu      %[tmp2],        %[tmp2],          1                    \n\t"
    "sra        %[tmp1],        %[tmp1],          1                    \n\t"
    "sra        %[tmp2],        %[tmp2],          1                    \n\t"
    "subu       %[tmp4],        %[tmp6],          %[tmp1]              \n\t"
    "addu       %[tmp1],        %[tmp6],          %[tmp1]              \n\t"
    "addu       %[tmp6],        %[tmp5],          %[tmp2]              \n\t"
    "subu       %[tmp5],        %[tmp5],          %[tmp2]              \n\t"
    "sra        %[tmp4],        %[tmp4],          15                   \n\t"
    "sra        %[tmp1],        %[tmp1],          15                   \n\t"
    "sra        %[tmp6],        %[tmp6],          15                   \n\t"
    "sra        %[tmp5],        %[tmp5],          15                   \n\t"
#endif  // #if defined(MIPS_DSP_R2_LE)
    "sh         %[tmp1],        0(%[ptr_i])                            \n\t"
    "sh         %[tmp6],        2(%[ptr_i])                            \n\t"
    "sh         %[tmp4],        0(%[ptr_j])                            \n\t"
    "blt        %[i],           %[n],             1b                   \n\t"
    " sh        %[tmp5],        2(%[ptr_j])                            \n\t"
    "blt        %[m],           %[l],             2b                   \n\t"
    " addu      %[i],           $zero,            %[m]                 \n\t"
    "move       %[l],           %[istep]                               \n\t"
    "blt        %[l],           %[n],             3b                   \n\t"
    " addiu     %[k],           %[k],             -1                   \n\t"

    ".set pop                                                          \n\t"

    : [tmp1] "=&r" (tmp1), [tmp2] "=&r" (tmp2), [tmp3] "=&r" (tmp3),
      [tmp4] "=&r" (tmp4), [tmp5] "=&r" (tmp5), [tmp6] "=&r" (tmp6),
      [ptr_i] "=&r" (ptr_i), [i] "=&r" (i), [wi] "=&r" (wi), [wr] "=&r" (wr),
      [m] "=&r" (m), [istep] "=&r" (istep), [l] "=&r" (l), [k] "=&r" (k),
      [ptr_j] "=&r" (ptr_j), [tmp] "=&r" (tmp)
    : [n] "r" (n), [frfi] "r" (frfi), [kSinTable1024] "r" (kSinTable1024)
    : "hi", "lo", "memory"
#if defined(MIPS_DSP_R2_LE)
    , "$ac1hi", "$ac1lo"
#endif  // #if defined(MIPS_DSP_R2_LE)
  );

  return 0;
}

int WebRtcSpl_ComplexIFFT(int16_t frfi[], int stages, int mode) {
  int i = 0, l = 0, k = 0;
  int istep = 0, n = 0, m = 0;
  int scale = 0, shift = 0;
  int32_t wr = 0, wi = 0;
  int32_t tmp1 = 0, tmp2 = 0, tmp3 = 0, tmp4 = 0;
  int32_t tmp5 = 0, tmp6 = 0, tmp = 0, tempMax = 0, round2 = 0;
  int16_t* ptr_j = NULL;
  int16_t* ptr_i = NULL;

  n = 1 << stages;
  if (n > 1024) {
    return -1;
  }

  __asm __volatile (
    ".set push                                                         \n\t"
    ".set noreorder                                                    \n\t"

    "addiu      %[k],           $zero,            10                   \n\t"
    "addiu      %[l],           $zero,            1                    \n\t"
    "move       %[scale],       $zero                                  \n\t"
   "3:                                                                 \n\t"
    "addiu      %[shift],       $zero,            14                   \n\t"
    "addiu      %[round2],      $zero,            8192                 \n\t"
    "move       %[ptr_i],       %[frfi]                                \n\t"
    "move       %[tempMax],     $zero                                  \n\t"
    "addu       %[i],           %[n],             %[n]                 \n\t"
   "5:                                                                 \n\t"
    "lh         %[tmp1],        0(%[ptr_i])                            \n\t"
    "lh         %[tmp2],        2(%[ptr_i])                            \n\t"
    "lh         %[tmp3],        4(%[ptr_i])                            \n\t"
    "lh         %[tmp4],        6(%[ptr_i])                            \n\t"
#if defined(MIPS_DSP_R1_LE)
    "absq_s.w   %[tmp1],        %[tmp1]                                \n\t"
    "absq_s.w   %[tmp2],        %[tmp2]                                \n\t"
    "absq_s.w   %[tmp3],        %[tmp3]                                \n\t"
    "absq_s.w   %[tmp4],        %[tmp4]                                \n\t"
#else  // #if defined(MIPS_DSP_R1_LE)
    "slt        %[tmp5],        %[tmp1],          $zero                \n\t"
    "subu       %[tmp6],        $zero,            %[tmp1]              \n\t"
    "movn       %[tmp1],        %[tmp6],          %[tmp5]              \n\t"
    "slt        %[tmp5],        %[tmp2],          $zero                \n\t"
    "subu       %[tmp6],        $zero,            %[tmp2]              \n\t"
    "movn       %[tmp2],        %[tmp6],          %[tmp5]              \n\t"
    "slt        %[tmp5],        %[tmp3],          $zero                \n\t"
    "subu       %[tmp6],        $zero,            %[tmp3]              \n\t"
    "movn       %[tmp3],        %[tmp6],          %[tmp5]              \n\t"
    "slt        %[tmp5],        %[tmp4],          $zero                \n\t"
    "subu       %[tmp6],        $zero,            %[tmp4]              \n\t"
    "movn       %[tmp4],        %[tmp6],          %[tmp5]              \n\t"
#endif  // #if defined(MIPS_DSP_R1_LE)
    "slt        %[tmp5],        %[tempMax],       %[tmp1]              \n\t"
    "movn       %[tempMax],     %[tmp1],          %[tmp5]              \n\t"
    "addiu      %[i],           %[i],             -4                   \n\t"
    "slt        %[tmp5],        %[tempMax],       %[tmp2]              \n\t"
    "movn       %[tempMax],     %[tmp2],          %[tmp5]              \n\t"
    "slt        %[tmp5],        %[tempMax],       %[tmp3]              \n\t"
    "movn       %[tempMax],     %[tmp3],          %[tmp5]              \n\t"
    "slt        %[tmp5],        %[tempMax],       %[tmp4]              \n\t"
    "movn       %[tempMax],     %[tmp4],          %[tmp5]              \n\t"
    "bgtz       %[i],                             5b                   \n\t"
    " addiu     %[ptr_i],       %[ptr_i],         8                    \n\t"
    "addiu      %[tmp1],        $zero,            13573                \n\t"
    "addiu      %[tmp2],        $zero,            27146                \n\t"
#if !defined(MIPS32_R2_LE)
    "sll        %[tempMax],     %[tempMax],       16                   \n\t"
    "sra        %[tempMax],     %[tempMax],       16                   \n\t"
#else  // #if !defined(MIPS32_R2_LE)
    "seh        %[tempMax]                                             \n\t"
#endif  // #if !defined(MIPS32_R2_LE)
    "slt        %[tmp1],        %[tmp1],          %[tempMax]           \n\t"
    "slt        %[tmp2],        %[tmp2],          %[tempMax]           \n\t"
    "addu       %[tmp1],        %[tmp1],          %[tmp2]              \n\t"
    "addu       %[shift],       %[shift],         %[tmp1]              \n\t"
    "addu       %[scale],       %[scale],         %[tmp1]              \n\t"
    "sllv       %[round2],      %[round2],        %[tmp1]              \n\t"
    "sll        %[istep],       %[l],             1                    \n\t"
    "move       %[m],           $zero                                  \n\t"
    "sll        %[tmp],         %[l],             2                    \n\t"
   "2:                                                                 \n\t"
#if defined(MIPS_DSP_R1_LE)
    "sllv       %[tmp3],        %[m],             %[k]                 \n\t"
    "addiu      %[tmp2],        %[tmp3],          512                  \n\t"
    "addiu      %[m],           %[m],             1                    \n\t"
    "lhx        %[wi],          %[tmp3](%[kSinTable1024])              \n\t"
    "lhx        %[wr],          %[tmp2](%[kSinTable1024])              \n\t"
#else  // #if defined(MIPS_DSP_R1_LE)
    "sllv       %[tmp3],        %[m],             %[k]                 \n\t"
    "addu       %[ptr_j],       %[tmp3],          %[kSinTable1024]     \n\t"
    "addiu      %[ptr_i],       %[ptr_j],         512                  \n\t"
    "addiu      %[m],           %[m],             1                    \n\t"
    "lh         %[wi],          0(%[ptr_j])                            \n\t"
    "lh         %[wr],          0(%[ptr_i])                            \n\t"
#endif  // #if defined(MIPS_DSP_R1_LE)
   "1:                                                                 \n\t"
    "sll        %[tmp1],        %[i],             2                    \n\t"
    "addu       %[ptr_i],       %[frfi],          %[tmp1]              \n\t"
    "addu       %[ptr_j],       %[ptr_i],         %[tmp]               \n\t"
    "lh         %[tmp3],        0(%[ptr_j])                            \n\t"
    "lh         %[tmp4],        2(%[ptr_j])                            \n\t"
    "lh         %[tmp6],        0(%[ptr_i])                            \n\t"
    "lh         %[tmp5],        2(%[ptr_i])                            \n\t"
    "addu       %[i],           %[i],             %[istep]             \n\t"
#if defined(MIPS_DSP_R2_LE)
    "mult       %[wr],          %[tmp3]                                \n\t"
    "msub       %[wi],          %[tmp4]                                \n\t"
    "mult       $ac1,           %[wr],            %[tmp4]              \n\t"
    "madd       $ac1,           %[wi],            %[tmp3]              \n\t"
    "mflo       %[tmp1]                                                \n\t"
    "mflo       %[tmp2],        $ac1                                   \n\t"
    "sll        %[tmp6],        %[tmp6],          14                   \n\t"
    "sll        %[tmp5],        %[tmp5],          14                   \n\t"
    "shra_r.w   %[tmp1],        %[tmp1],          1                    \n\t"
    "shra_r.w   %[tmp2],        %[tmp2],          1                    \n\t"
    "addu       %[tmp6],        %[tmp6],          %[round2]            \n\t"
    "addu       %[tmp5],        %[tmp5],          %[round2]            \n\t"
    "subu       %[tmp4],        %[tmp6],          %[tmp1]              \n\t"
    "addu       %[tmp1],        %[tmp6],          %[tmp1]              \n\t"
    "addu       %[tmp6],        %[tmp5],          %[tmp2]              \n\t"
    "subu       %[tmp5],        %[tmp5],          %[tmp2]              \n\t"
    "srav       %[tmp4],        %[tmp4],          %[shift]             \n\t"
    "srav       %[tmp1],        %[tmp1],          %[shift]             \n\t"
    "srav       %[tmp6],        %[tmp6],          %[shift]             \n\t"
    "srav       %[tmp5],        %[tmp5],          %[shift]             \n\t"
#else  // #if defined(MIPS_DSP_R2_LE)
    "mul        %[tmp1],        %[wr],            %[tmp3]              \n\t"
    "mul        %[tmp2],        %[wr],            %[tmp4]              \n\t"
    "mul        %[tmp4],        %[wi],            %[tmp4]              \n\t"
    "mul        %[tmp3],        %[wi],            %[tmp3]              \n\t"
    "sll        %[tmp6],        %[tmp6],          14                   \n\t"
    "sll        %[tmp5],        %[tmp5],          14                   \n\t"
    "sub        %[tmp1],        %[tmp1],          %[tmp4]              \n\t"
    "addu       %[tmp2],        %[tmp2],          %[tmp3]              \n\t"
    "addiu      %[tmp1],        %[tmp1],          1                    \n\t"
    "addiu      %[tmp2],        %[tmp2],          1                    \n\t"
    "sra        %[tmp2],        %[tmp2],          1                    \n\t"
    "sra        %[tmp1],        %[tmp1],          1                    \n\t"
    "addu       %[tmp6],        %[tmp6],          %[round2]            \n\t"
    "addu       %[tmp5],        %[tmp5],          %[round2]            \n\t"
    "subu       %[tmp4],        %[tmp6],          %[tmp1]              \n\t"
    "addu       %[tmp1],        %[tmp6],          %[tmp1]              \n\t"
    "addu       %[tmp6],        %[tmp5],          %[tmp2]              \n\t"
    "subu       %[tmp5],        %[tmp5],          %[tmp2]              \n\t"
    "sra        %[tmp4],        %[tmp4],          %[shift]             \n\t"
    "sra        %[tmp1],        %[tmp1],          %[shift]             \n\t"
    "sra        %[tmp6],        %[tmp6],          %[shift]             \n\t"
    "sra        %[tmp5],        %[tmp5],          %[shift]             \n\t"
#endif  // #if defined(MIPS_DSP_R2_LE)
    "sh         %[tmp1],         0(%[ptr_i])                           \n\t"
    "sh         %[tmp6],         2(%[ptr_i])                           \n\t"
    "sh         %[tmp4],         0(%[ptr_j])                           \n\t"
    "blt        %[i],            %[n],            1b                   \n\t"
    " sh        %[tmp5],         2(%[ptr_j])                           \n\t"
    "blt        %[m],            %[l],            2b                   \n\t"
    " addu      %[i],            $zero,           %[m]                 \n\t"
    "move       %[l],            %[istep]                              \n\t"
    "blt        %[l],            %[n],            3b                   \n\t"
    " addiu     %[k],            %[k],            -1                   \n\t"

    ".set pop                                                          \n\t"

    : [tmp1] "=&r" (tmp1), [tmp2] "=&r" (tmp2), [tmp3] "=&r" (tmp3),
      [tmp4] "=&r" (tmp4), [tmp5] "=&r" (tmp5), [tmp6] "=&r" (tmp6),
      [ptr_i] "=&r" (ptr_i), [i] "=&r" (i), [m] "=&r" (m), [tmp] "=&r" (tmp),
      [istep] "=&r" (istep), [wi] "=&r" (wi), [wr] "=&r" (wr), [l] "=&r" (l),
      [k] "=&r" (k), [round2] "=&r" (round2), [ptr_j] "=&r" (ptr_j),
      [shift] "=&r" (shift), [scale] "=&r" (scale), [tempMax] "=&r" (tempMax)
    : [n] "r" (n), [frfi] "r" (frfi), [kSinTable1024] "r" (kSinTable1024)
    : "hi", "lo", "memory"
#if defined(MIPS_DSP_R2_LE)
    , "$ac1hi", "$ac1lo"
#endif  // #if defined(MIPS_DSP_R2_LE)
  );

  return scale;

}
