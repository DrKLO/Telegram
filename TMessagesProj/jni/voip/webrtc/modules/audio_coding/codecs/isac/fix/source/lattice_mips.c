/*
 *  Copyright (c) 2014 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include <stddef.h>
#include <stdint.h>

#include "modules/audio_coding/codecs/isac/fix/source/settings.h"

// Filter ar_g_Q0[] and ar_f_Q0[] through an AR filter with coefficients
// cth_Q15[] and sth_Q15[].
void WebRtcIsacfix_FilterArLoop(int16_t* ar_g_Q0,     // Input samples
                                int16_t* ar_f_Q0,     // Input samples
                                int16_t* cth_Q15,     // Filter coefficients
                                int16_t* sth_Q15,     // Filter coefficients
                                size_t order_coef) { // order of the filter
  int n = 0;

  for (n = 0; n < HALF_SUBFRAMELEN - 1; n++) {
    int count = (int)(order_coef - 1);
    int offset;
#if !defined(MIPS_DSP_R1_LE)
    int16_t* tmp_cth;
    int16_t* tmp_sth;
    int16_t* tmp_arg;
    int32_t max_q16 = 0x7fff;
    int32_t min_q16 = 0xffff8000;
#endif
    // Declare variables used as temporary registers.
    int32_t r0, r1, r2, t0, t1, t2, t_ar;

    __asm __volatile (
      ".set          push                                                \n\t"
      ".set          noreorder                                           \n\t"
      "bltz          %[count],     2f                                    \n\t"
      " lh           %[t_ar],      0(%[tmp])                             \n\t"
      // Inner loop
     "1:                                                                 \n\t"
      "sll           %[offset],    %[count],               1             \n\t"
#if defined(MIPS_DSP_R1_LE)
      "lhx           %[r0],        %[offset](%[cth_Q15])                 \n\t"
      "lhx           %[r1],        %[offset](%[sth_Q15])                 \n\t"
      "lhx           %[r2],        %[offset](%[ar_g_Q0])                 \n\t"
#else
      "addu          %[tmp_cth],   %[cth_Q15],             %[offset]     \n\t"
      "addu          %[tmp_sth],   %[sth_Q15],             %[offset]     \n\t"
      "addu          %[tmp_arg],   %[ar_g_Q0],             %[offset]     \n\t"
      "lh            %[r0],        0(%[tmp_cth])                         \n\t"
      "lh            %[r1],        0(%[tmp_sth])                         \n\t"
      "lh            %[r2],        0(%[tmp_arg])                         \n\t"
#endif
      "mul           %[t0],        %[r0],                  %[t_ar]       \n\t"
      "mul           %[t1],        %[r1],                  %[t_ar]       \n\t"
      "mul           %[t2],        %[r1],                  %[r2]         \n\t"
      "mul           %[r0],        %[r0],                  %[r2]         \n\t"
      "subu          %[t0],        %[t0],                  %[t2]         \n\t"
      "addu          %[t1],        %[t1],                  %[r0]         \n\t"
#if defined(MIPS_DSP_R1_LE)
      "shra_r.w      %[t1],        %[t1],                  15            \n\t"
      "shra_r.w      %[t0],        %[t0],                  15            \n\t"
#else
      "addiu         %[t1],        %[t1],                  0x4000        \n\t"
      "sra           %[t1],        %[t1],                  15            \n\t"
      "addiu         %[t0],        %[t0],                  0x4000        \n\t"
      "sra           %[t0],        %[t0],                  15            \n\t"
#endif
      "addiu         %[offset],    %[offset],              2             \n\t"
#if defined(MIPS_DSP_R1_LE)
      "shll_s.w      %[t1],        %[t1],                  16            \n\t"
      "shll_s.w      %[t_ar],      %[t0],                  16            \n\t"
#else
      "slt           %[r0],        %[t1],                  %[max_q16]    \n\t"
      "slt           %[r1],        %[t0],                  %[max_q16]    \n\t"
      "movz          %[t1],        %[max_q16],             %[r0]         \n\t"
      "movz          %[t0],        %[max_q16],             %[r1]         \n\t"
#endif
      "addu          %[offset],    %[offset],              %[ar_g_Q0]    \n\t"
#if defined(MIPS_DSP_R1_LE)
      "sra           %[t1],        %[t1],                  16            \n\t"
      "sra           %[t_ar],      %[t_ar],                16            \n\t"
#else
      "slt           %[r0],        %[t1],                  %[min_q16]    \n\t"
      "slt           %[r1],        %[t0],                  %[min_q16]    \n\t"
      "movn          %[t1],        %[min_q16],             %[r0]         \n\t"
      "movn          %[t0],        %[min_q16],             %[r1]         \n\t"
      "addu          %[t_ar],      $zero,                  %[t0]         \n\t"
#endif
      "sh            %[t1],        0(%[offset])                          \n\t"
      "bgtz          %[count],     1b                                    \n\t"
      " addiu        %[count],     %[count],               -1            \n\t"
     "2:                                                                 \n\t"
      "sh            %[t_ar],      0(%[tmp])                             \n\t"
      "sh            %[t_ar],      0(%[ar_g_Q0])                         \n\t"
      ".set          pop                                                 \n\t"
      : [t_ar] "=&r" (t_ar), [count] "+r" (count), [offset] "=&r" (offset),
        [r0] "=&r" (r0), [r1] "=&r" (r1), [r2] "=&r" (r2), [t0] "=&r" (t0),
#if !defined(MIPS_DSP_R1_LE)
        [tmp_cth] "=&r" (tmp_cth), [tmp_sth] "=&r" (tmp_sth),
        [tmp_arg] "=&r" (tmp_arg),
#endif
        [t1] "=&r" (t1), [t2] "=&r" (t2)
      : [tmp] "r" (&ar_f_Q0[n+1]), [cth_Q15] "r" (cth_Q15),
#if !defined(MIPS_DSP_R1_LE)
        [max_q16] "r" (max_q16), [min_q16] "r" (min_q16),
#endif
        [sth_Q15] "r" (sth_Q15), [ar_g_Q0] "r" (ar_g_Q0)
      : "memory", "hi", "lo"
    );
  }
}

// MIPS optimization of the inner loop used for function
// WebRtcIsacfix_NormLatticeFilterMa(). It does:
//
// for 0 <= n < HALF_SUBFRAMELEN - 1:
//   *ptr2 = input2 * (*ptr2) + input0 * (*ptr0));
//   *ptr1 = input1 * (*ptr0) + input0 * (*ptr2);
//
// Note, function WebRtcIsacfix_FilterMaLoopMIPS and WebRtcIsacfix_FilterMaLoopC
// are not bit-exact. The accuracy of the MIPS function is same or better.
void WebRtcIsacfix_FilterMaLoopMIPS(int16_t input0,  // Filter coefficient
                                    int16_t input1,  // Filter coefficient
                                    int32_t input2,  // Inverse coeff (1/input1)
                                    int32_t* ptr0,   // Sample buffer
                                    int32_t* ptr1,   // Sample buffer
                                    int32_t* ptr2) { // Sample buffer
#if defined(MIPS_DSP_R2_LE)
  // MIPS DSPR2 version. 4 available accumulators allows loop unrolling 4 times.
  // This variant is not bit-exact with WebRtcIsacfix_FilterMaLoopC, since we
  // are exploiting 64-bit accumulators. The accuracy of the MIPS DSPR2 function
  // is same or better.
  int n = (HALF_SUBFRAMELEN - 1) >> 2;
  int m = (HALF_SUBFRAMELEN - 1) & 3;

  int r0, r1, r2, r3;
  int t0, t1, t2, t3;
  int s0, s1, s2, s3;

  __asm __volatile (
    ".set          push                                      \n\t"
    ".set          noreorder                                 \n\t"
   "1:                                                       \n\t"
    "lw            %[r0],        0(%[ptr0])                  \n\t"
    "lw            %[r1],        4(%[ptr0])                  \n\t"
    "lw            %[r2],        8(%[ptr0])                  \n\t"
    "lw            %[r3],        12(%[ptr0])                 \n\t"
    "mult          $ac0,         %[r0],        %[input0]     \n\t"
    "mult          $ac1,         %[r1],        %[input0]     \n\t"
    "mult          $ac2,         %[r2],        %[input0]     \n\t"
    "mult          $ac3,         %[r3],        %[input0]     \n\t"
    "lw            %[t0],        0(%[ptr2])                  \n\t"
    "extr_rs.w     %[s0],        $ac0,         15            \n\t"
    "extr_rs.w     %[s1],        $ac1,         15            \n\t"
    "extr_rs.w     %[s2],        $ac2,         15            \n\t"
    "extr_rs.w     %[s3],        $ac3,         15            \n\t"
    "lw            %[t1],        4(%[ptr2])                  \n\t"
    "lw            %[t2],        8(%[ptr2])                  \n\t"
    "lw            %[t3],        12(%[ptr2])                 \n\t"
    "addu          %[t0],        %[t0],        %[s0]         \n\t"
    "addu          %[t1],        %[t1],        %[s1]         \n\t"
    "addu          %[t2],        %[t2],        %[s2]         \n\t"
    "addu          %[t3],        %[t3],        %[s3]         \n\t"
    "mult          $ac0,         %[t0],        %[input2]     \n\t"
    "mult          $ac1,         %[t1],        %[input2]     \n\t"
    "mult          $ac2,         %[t2],        %[input2]     \n\t"
    "mult          $ac3,         %[t3],        %[input2]     \n\t"
    "addiu         %[ptr0],      %[ptr0],      16            \n\t"
    "extr_rs.w     %[t0],        $ac0,         16            \n\t"
    "extr_rs.w     %[t1],        $ac1,         16            \n\t"
    "extr_rs.w     %[t2],        $ac2,         16            \n\t"
    "extr_rs.w     %[t3],        $ac3,         16            \n\t"
    "addiu         %[n],         %[n],         -1            \n\t"
    "mult          $ac0,         %[r0],        %[input1]     \n\t"
    "mult          $ac1,         %[r1],        %[input1]     \n\t"
    "mult          $ac2,         %[r2],        %[input1]     \n\t"
    "mult          $ac3,         %[r3],        %[input1]     \n\t"
    "sw            %[t0],        0(%[ptr2])                  \n\t"
    "extr_rs.w     %[s0],        $ac0,         15            \n\t"
    "extr_rs.w     %[s1],        $ac1,         15            \n\t"
    "extr_rs.w     %[s2],        $ac2,         15            \n\t"
    "extr_rs.w     %[s3],        $ac3,         15            \n\t"
    "sw            %[t1],        4(%[ptr2])                  \n\t"
    "sw            %[t2],        8(%[ptr2])                  \n\t"
    "sw            %[t3],        12(%[ptr2])                 \n\t"
    "mult          $ac0,         %[t0],        %[input0]     \n\t"
    "mult          $ac1,         %[t1],        %[input0]     \n\t"
    "mult          $ac2,         %[t2],        %[input0]     \n\t"
    "mult          $ac3,         %[t3],        %[input0]     \n\t"
    "addiu         %[ptr2],      %[ptr2],      16            \n\t"
    "extr_rs.w     %[t0],        $ac0,         15            \n\t"
    "extr_rs.w     %[t1],        $ac1,         15            \n\t"
    "extr_rs.w     %[t2],        $ac2,         15            \n\t"
    "extr_rs.w     %[t3],        $ac3,         15            \n\t"
    "addu          %[t0],        %[t0],        %[s0]         \n\t"
    "addu          %[t1],        %[t1],        %[s1]         \n\t"
    "addu          %[t2],        %[t2],        %[s2]         \n\t"
    "addu          %[t3],        %[t3],        %[s3]         \n\t"
    "sw            %[t0],        0(%[ptr1])                  \n\t"
    "sw            %[t1],        4(%[ptr1])                  \n\t"
    "sw            %[t2],        8(%[ptr1])                  \n\t"
    "sw            %[t3],        12(%[ptr1])                 \n\t"
    "bgtz          %[n],         1b                          \n\t"
    " addiu        %[ptr1],      %[ptr1],      16            \n\t"
    "beq           %[m],         %0,           3f            \n\t"
    " nop                                                    \n\t"
   "2:                                                       \n\t"
    "lw            %[r0],        0(%[ptr0])                  \n\t"
    "lw            %[t0],        0(%[ptr2])                  \n\t"
    "addiu         %[ptr0],      %[ptr0],      4             \n\t"
    "mult          $ac0,         %[r0],        %[input0]     \n\t"
    "mult          $ac1,         %[r0],        %[input1]     \n\t"
    "extr_rs.w     %[r1],        $ac0,         15            \n\t"
    "extr_rs.w     %[t1],        $ac1,         15            \n\t"
    "addu          %[t0],        %[t0],        %[r1]         \n\t"
    "mult          $ac0,         %[t0],        %[input2]     \n\t"
    "extr_rs.w     %[t0],        $ac0,         16            \n\t"
    "sw            %[t0],        0(%[ptr2])                  \n\t"
    "mult          $ac0,         %[t0],        %[input0]     \n\t"
    "addiu         %[ptr2],      %[ptr2],      4             \n\t"
    "addiu         %[m],         %[m],         -1            \n\t"
    "extr_rs.w     %[t0],        $ac0,         15            \n\t"
    "addu          %[t0],        %[t0],        %[t1]         \n\t"
    "sw            %[t0],        0(%[ptr1])                  \n\t"
    "bgtz          %[m],         2b                          \n\t"
    " addiu        %[ptr1],      %[ptr1],      4             \n\t"
   "3:                                                       \n\t"
    ".set          pop                                       \n\t"
    : [r0] "=&r" (r0), [r1] "=&r" (r1), [r2] "=&r" (r2),
      [r3] "=&r" (r3), [t0] "=&r" (t0), [t1] "=&r" (t1),
      [t2] "=&r" (t2), [t3] "=&r" (t3), [s0] "=&r" (s0),
      [s1] "=&r" (s1), [s2] "=&r" (s2), [s3] "=&r" (s3),
      [ptr0] "+r" (ptr0), [ptr1] "+r" (ptr1), [m] "+r" (m),
      [ptr2] "+r" (ptr2), [n] "+r" (n)
    : [input0] "r" (input0), [input1] "r" (input1),
      [input2] "r" (input2)
    : "memory", "hi", "lo", "$ac1hi", "$ac1lo", "$ac2hi",
      "$ac2lo", "$ac3hi", "$ac3lo"
  );
#else
  // Non-DSPR2 version of the function. Avoiding the accumulator usage due to
  // large latencies. This variant is bit-exact with C code.
  int n = HALF_SUBFRAMELEN - 1;
  int32_t t16a, t16b;
  int32_t r0, r1, r2, r3, r4;

  __asm __volatile (
    ".set          push                                      \n\t"
    ".set          noreorder                                 \n\t"
    "sra           %[t16a],      %[input2],     16           \n\t"
    "andi          %[t16b],      %[input2],     0xFFFF       \n\t"
#if defined(MIPS32R2_LE)
    "seh           %[t16b],      %[t16b]                     \n\t"
    "seh           %[input0],    %[input0]                   \n\t"
    "seh           %[input1],    %[input1]                   \n\t"
#else
    "sll           %[t16b],      %[t16b],       16           \n\t"
    "sra           %[t16b],      %[t16b],       16           \n\t"
    "sll           %[input0],    %[input0],     16           \n\t"
    "sra           %[input0],    %[input0],     16           \n\t"
    "sll           %[input1],    %[input1],     16           \n\t"
    "sra           %[input1],    %[input1],     16           \n\t"
#endif
    "addiu         %[r0],        %[t16a],       1            \n\t"
    "slt           %[r1],        %[t16b],       $zero        \n\t"
    "movn          %[t16a],      %[r0],         %[r1]        \n\t"
   "1:                                                       \n\t"
    "lw            %[r0],        0(%[ptr0])                  \n\t"
    "lw            %[r1],        0(%[ptr2])                  \n\t"
    "addiu         %[ptr0],      %[ptr0],       4            \n\t"
    "sra           %[r2],        %[r0],         16           \n\t"
    "andi          %[r0],        %[r0],         0xFFFF       \n\t"
    "mul           %[r3],        %[r2],         %[input0]    \n\t"
    "mul           %[r4],        %[r0],         %[input0]    \n\t"
    "mul           %[r2],        %[r2],         %[input1]    \n\t"
    "mul           %[r0],        %[r0],         %[input1]    \n\t"
    "addiu         %[ptr2],      %[ptr2],       4            \n\t"
    "sll           %[r3],        %[r3],         1            \n\t"
    "sra           %[r4],        %[r4],         1            \n\t"
    "addiu         %[r4],        %[r4],         0x2000       \n\t"
    "sra           %[r4],        %[r4],         14           \n\t"
    "addu          %[r3],        %[r3],         %[r4]        \n\t"
    "addu          %[r1],        %[r1],         %[r3]        \n\t"
    "sra           %[r3],        %[r1],         16           \n\t"
    "andi          %[r4],        %[r1],         0xFFFF       \n\t"
    "sra           %[r4],        %[r4],         1            \n\t"
    "mul           %[r1],        %[r1],         %[t16a]      \n\t"
    "mul           %[r3],        %[r3],         %[t16b]      \n\t"
    "mul           %[r4],        %[r4],         %[t16b]      \n\t"
    "sll           %[r2],        %[r2],         1            \n\t"
    "sra           %[r0],        %[r0],         1            \n\t"
    "addiu         %[r0],        %[r0],         0x2000       \n\t"
    "sra           %[r0],        %[r0],         14           \n\t"
    "addu          %[r0],        %[r0],         %[r2]        \n\t"
    "addiu         %[n],         %[n],          -1           \n\t"
    "addu          %[r1],        %[r1],         %[r3]        \n\t"
    "addiu         %[r4],        %[r4],         0x4000       \n\t"
    "sra           %[r4],        %[r4],         15           \n\t"
    "addu          %[r1],        %[r1],         %[r4]        \n\t"
    "sra           %[r2],        %[r1],         16           \n\t"
    "andi          %[r3],        %[r1],         0xFFFF       \n\t"
    "mul           %[r3],        %[r3],         %[input0]    \n\t"
    "mul           %[r2],        %[r2],         %[input0]    \n\t"
    "sw            %[r1],        -4(%[ptr2])                 \n\t"
    "sra           %[r3],        %[r3],         1            \n\t"
    "addiu         %[r3],        %[r3],         0x2000       \n\t"
    "sra           %[r3],        %[r3],         14           \n\t"
    "addu          %[r0],        %[r0],         %[r3]        \n\t"
    "sll           %[r2],        %[r2],         1            \n\t"
    "addu          %[r0],        %[r0],         %[r2]        \n\t"
    "sw            %[r0],        0(%[ptr1])                  \n\t"
    "bgtz          %[n],         1b                          \n\t"
    " addiu        %[ptr1],      %[ptr1],       4            \n\t"
    ".set          pop                                       \n\t"
    : [t16a] "=&r" (t16a), [t16b] "=&r" (t16b), [r0] "=&r" (r0),
      [r1] "=&r" (r1), [r2] "=&r" (r2), [r3] "=&r" (r3),
      [r4] "=&r" (r4), [ptr0] "+r" (ptr0), [ptr1] "+r" (ptr1),
      [ptr2] "+r" (ptr2), [n] "+r" (n)
    : [input0] "r" (input0), [input1] "r" (input1),
      [input2] "r" (input2)
    : "hi", "lo", "memory"
  );
#endif
}
