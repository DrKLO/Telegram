/*
 *  Copyright (c) 2013 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "common_audio/signal_processing/include/signal_processing_library.h"

// Version of WebRtcSpl_DownsampleFast() for MIPS platforms.
int WebRtcSpl_DownsampleFast_mips(const int16_t* data_in,
                                  size_t data_in_length,
                                  int16_t* data_out,
                                  size_t data_out_length,
                                  const int16_t* __restrict coefficients,
                                  size_t coefficients_length,
                                  int factor,
                                  size_t delay) {
  int i;
  int j;
  int k;
  int32_t out_s32 = 0;
  size_t endpos = delay + factor * (data_out_length - 1) + 1;

  int32_t  tmp1, tmp2, tmp3, tmp4, factor_2;
  int16_t* p_coefficients;
  int16_t* p_data_in;
  int16_t* p_data_in_0 = (int16_t*)&data_in[delay];
  int16_t* p_coefficients_0 = (int16_t*)&coefficients[0];
#if !defined(MIPS_DSP_R1_LE)
  int32_t max_16 = 0x7FFF;
  int32_t min_16 = 0xFFFF8000;
#endif  // #if !defined(MIPS_DSP_R1_LE)

  // Return error if any of the running conditions doesn't meet.
  if (data_out_length == 0 || coefficients_length == 0
                           || data_in_length < endpos) {
    return -1;
  }
#if defined(MIPS_DSP_R2_LE)
  __asm __volatile (
    ".set        push                                                \n\t"
    ".set        noreorder                                           \n\t"
    "subu        %[i],            %[endpos],       %[delay]          \n\t"
    "sll         %[factor_2],     %[factor],       1                 \n\t"
   "1:                                                               \n\t"
    "move        %[p_data_in],    %[p_data_in_0]                     \n\t"
    "mult        $zero,           $zero                              \n\t"
    "move        %[p_coefs],      %[p_coefs_0]                       \n\t"
    "sra         %[j],            %[coef_length],  2                 \n\t"
    "beq         %[j],            $zero,           3f                \n\t"
    " andi       %[k],            %[coef_length],  3                 \n\t"
   "2:                                                               \n\t"
    "lwl         %[tmp1],         1(%[p_data_in])                    \n\t"
    "lwl         %[tmp2],         3(%[p_coefs])                      \n\t"
    "lwl         %[tmp3],         -3(%[p_data_in])                   \n\t"
    "lwl         %[tmp4],         7(%[p_coefs])                      \n\t"
    "lwr         %[tmp1],         -2(%[p_data_in])                   \n\t"
    "lwr         %[tmp2],         0(%[p_coefs])                      \n\t"
    "lwr         %[tmp3],         -6(%[p_data_in])                   \n\t"
    "lwr         %[tmp4],         4(%[p_coefs])                      \n\t"
    "packrl.ph   %[tmp1],         %[tmp1],         %[tmp1]           \n\t"
    "packrl.ph   %[tmp3],         %[tmp3],         %[tmp3]           \n\t"
    "dpa.w.ph    $ac0,            %[tmp1],         %[tmp2]           \n\t"
    "dpa.w.ph    $ac0,            %[tmp3],         %[tmp4]           \n\t"
    "addiu       %[j],            %[j],            -1                \n\t"
    "addiu       %[p_data_in],    %[p_data_in],    -8                \n\t"
    "bgtz        %[j],            2b                                 \n\t"
    " addiu      %[p_coefs],      %[p_coefs],      8                 \n\t"
   "3:                                                               \n\t"
    "beq         %[k],            $zero,           5f                \n\t"
    " nop                                                            \n\t"
   "4:                                                               \n\t"
    "lhu         %[tmp1],         0(%[p_data_in])                    \n\t"
    "lhu         %[tmp2],         0(%[p_coefs])                      \n\t"
    "addiu       %[p_data_in],    %[p_data_in],    -2                \n\t"
    "addiu       %[k],            %[k],            -1                \n\t"
    "dpa.w.ph    $ac0,            %[tmp1],         %[tmp2]           \n\t"
    "bgtz        %[k],            4b                                 \n\t"
    " addiu      %[p_coefs],      %[p_coefs],      2                 \n\t"
   "5:                                                               \n\t"
    "extr_r.w    %[out_s32],      $ac0,            12                \n\t"
    "addu        %[p_data_in_0],  %[p_data_in_0],  %[factor_2]       \n\t"
    "subu        %[i],            %[i],            %[factor]         \n\t"
    "shll_s.w    %[out_s32],      %[out_s32],      16                \n\t"
    "sra         %[out_s32],      %[out_s32],      16                \n\t"
    "sh          %[out_s32],      0(%[data_out])                     \n\t"
    "bgtz        %[i],            1b                                 \n\t"
    " addiu      %[data_out],     %[data_out],     2                 \n\t"
    ".set        pop                                                 \n\t"
    : [tmp1] "=&r" (tmp1), [tmp2] "=&r" (tmp2), [tmp3] "=&r" (tmp3),
      [tmp4] "=&r" (tmp4), [p_data_in] "=&r" (p_data_in),
      [p_data_in_0] "+r" (p_data_in_0), [p_coefs] "=&r" (p_coefficients),
      [j] "=&r" (j), [out_s32] "=&r" (out_s32), [factor_2] "=&r" (factor_2),
      [i] "=&r" (i), [k] "=&r" (k)
    : [coef_length] "r" (coefficients_length), [data_out] "r" (data_out),
      [p_coefs_0] "r" (p_coefficients_0), [endpos] "r" (endpos),
      [delay] "r" (delay), [factor] "r" (factor)
    : "memory", "hi", "lo"
 );
#else  // #if defined(MIPS_DSP_R2_LE)
  __asm __volatile (
    ".set        push                                                \n\t"
    ".set        noreorder                                           \n\t"
    "sll         %[factor_2],     %[factor],       1                 \n\t"
    "subu        %[i],            %[endpos],       %[delay]          \n\t"
   "1:                                                               \n\t"
    "move        %[p_data_in],    %[p_data_in_0]                     \n\t"
    "addiu       %[out_s32],      $zero,           2048              \n\t"
    "move        %[p_coefs],      %[p_coefs_0]                       \n\t"
    "sra         %[j],            %[coef_length],  1                 \n\t"
    "beq         %[j],            $zero,           3f                \n\t"
    " andi       %[k],            %[coef_length],  1                 \n\t"
   "2:                                                               \n\t"
    "lh          %[tmp1],         0(%[p_data_in])                    \n\t"
    "lh          %[tmp2],         0(%[p_coefs])                      \n\t"
    "lh          %[tmp3],         -2(%[p_data_in])                   \n\t"
    "lh          %[tmp4],         2(%[p_coefs])                      \n\t"
    "mul         %[tmp1],         %[tmp1],         %[tmp2]           \n\t"
    "addiu       %[p_coefs],      %[p_coefs],      4                 \n\t"
    "mul         %[tmp3],         %[tmp3],         %[tmp4]           \n\t"
    "addiu       %[j],            %[j],            -1                \n\t"
    "addiu       %[p_data_in],    %[p_data_in],    -4                \n\t"
    "addu        %[tmp1],         %[tmp1],         %[tmp3]           \n\t"
    "bgtz        %[j],            2b                                 \n\t"
    " addu       %[out_s32],      %[out_s32],      %[tmp1]           \n\t"
   "3:                                                               \n\t"
    "beq         %[k],            $zero,           4f                \n\t"
    " nop                                                            \n\t"
    "lh          %[tmp1],         0(%[p_data_in])                    \n\t"
    "lh          %[tmp2],         0(%[p_coefs])                      \n\t"
    "mul         %[tmp1],         %[tmp1],         %[tmp2]           \n\t"
    "addu        %[out_s32],      %[out_s32],      %[tmp1]           \n\t"
   "4:                                                               \n\t"
    "sra         %[out_s32],      %[out_s32],      12                \n\t"
    "addu        %[p_data_in_0],  %[p_data_in_0],  %[factor_2]       \n\t"
#if defined(MIPS_DSP_R1_LE)
    "shll_s.w    %[out_s32],      %[out_s32],      16                \n\t"
    "sra         %[out_s32],      %[out_s32],      16                \n\t"
#else  // #if defined(MIPS_DSP_R1_LE)
    "slt         %[tmp1],         %[max_16],       %[out_s32]        \n\t"
    "movn        %[out_s32],      %[max_16],       %[tmp1]           \n\t"
    "slt         %[tmp1],         %[out_s32],      %[min_16]         \n\t"
    "movn        %[out_s32],      %[min_16],       %[tmp1]           \n\t"
#endif  // #if defined(MIPS_DSP_R1_LE)
    "subu        %[i],            %[i],            %[factor]         \n\t"
    "sh          %[out_s32],      0(%[data_out])                     \n\t"
    "bgtz        %[i],            1b                                 \n\t"
    " addiu      %[data_out],     %[data_out],     2                 \n\t"
    ".set        pop                                                 \n\t"
    : [tmp1] "=&r" (tmp1), [tmp2] "=&r" (tmp2), [tmp3] "=&r" (tmp3),
      [tmp4] "=&r" (tmp4), [p_data_in] "=&r" (p_data_in), [k] "=&r" (k),
      [p_data_in_0] "+r" (p_data_in_0), [p_coefs] "=&r" (p_coefficients),
      [j] "=&r" (j), [out_s32] "=&r" (out_s32), [factor_2] "=&r" (factor_2),
      [i] "=&r" (i)
    : [coef_length] "r" (coefficients_length), [data_out] "r" (data_out),
      [p_coefs_0] "r" (p_coefficients_0), [endpos] "r" (endpos),
#if !defined(MIPS_DSP_R1_LE)
      [max_16] "r" (max_16), [min_16] "r" (min_16),
#endif  // #if !defined(MIPS_DSP_R1_LE)
      [delay] "r" (delay), [factor] "r" (factor)
    : "memory", "hi", "lo"
  );
#endif  // #if defined(MIPS_DSP_R2_LE)
  return 0;
}
