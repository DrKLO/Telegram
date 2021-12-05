/*
 *  Copyright (c) 2014 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "modules/audio_coding/codecs/isac/fix/source/codec.h"
#include "modules/audio_coding/codecs/isac/fix/source/fft.h"
#include "modules/audio_coding/codecs/isac/fix/source/settings.h"

// The tables are defined in transform_tables.c file.
extern const int16_t WebRtcIsacfix_kCosTab1[FRAMESAMPLES/2];
extern const int16_t WebRtcIsacfix_kSinTab1[FRAMESAMPLES/2];
extern const int16_t WebRtcIsacfix_kCosTab2[FRAMESAMPLES/4];
extern const int16_t WebRtcIsacfix_kSinTab2[FRAMESAMPLES/4];

// MIPS DSPr2 version of the WebRtcIsacfix_Time2Spec function
// is not bit-exact with the C version.
// The accuracy of the MIPS DSPr2 version is same or better.
void WebRtcIsacfix_Time2SpecMIPS(int16_t* inre1Q9,
                                 int16_t* inre2Q9,
                                 int16_t* outreQ7,
                                 int16_t* outimQ7) {
  int k = FRAMESAMPLES / 2;
  int32_t tmpreQ16[FRAMESAMPLES / 2], tmpimQ16[FRAMESAMPLES / 2];
  int32_t r0, r1, r2, r3, r4, r5, r6, r7, r8, r9;
  int32_t inre1, inre2, tmpre, tmpim, factor, max, max1;
  int16_t* cosptr;
  int16_t* sinptr;

  cosptr = (int16_t*)WebRtcIsacfix_kCosTab1;
  sinptr = (int16_t*)WebRtcIsacfix_kSinTab1;

  __asm __volatile (
    ".set           push                                      \n\t"
    ".set           noreorder                                 \n\t"
    "addiu          %[inre1],     %[inre1Q9],   0             \n\t"
    "addiu          %[inre2],     %[inre2Q9],   0             \n\t"
    "addiu          %[tmpre],     %[tmpreQ16],  0             \n\t"
    "addiu          %[tmpim],     %[tmpimQ16],  0             \n\t"
    "addiu          %[factor],    $zero,        16921         \n\t"
    "mul            %[max],       $zero,        $zero         \n\t"
    // Multiply with complex exponentials and combine into one complex vector.
    // Also, calculate the maximal absolute value in the same loop.
   "1:                                                        \n\t"
#if defined(MIPS_DSP_R2_LE)
    "lwl            %[r0],        0(%[inre1])                 \n\t"
    "lwl            %[r2],        0(%[cosptr])                \n\t"
    "lwl            %[r3],        0(%[sinptr])                \n\t"
    "lwl            %[r1],        0(%[inre2])                 \n\t"
    "lwr            %[r0],        0(%[inre1])                 \n\t"
    "lwr            %[r2],        0(%[cosptr])                \n\t"
    "lwr            %[r3],        0(%[sinptr])                \n\t"
    "lwr            %[r1],        0(%[inre2])                 \n\t"
    "muleq_s.w.phr  %[r4],        %[r2],        %[r0]         \n\t"
    "muleq_s.w.phr  %[r5],        %[r3],        %[r0]         \n\t"
    "muleq_s.w.phr  %[r6],        %[r3],        %[r1]         \n\t"
    "muleq_s.w.phr  %[r7],        %[r2],        %[r1]         \n\t"
    "muleq_s.w.phl  %[r8],        %[r2],        %[r0]         \n\t"
    "muleq_s.w.phl  %[r0],        %[r3],        %[r0]         \n\t"
    "muleq_s.w.phl  %[r3],        %[r3],        %[r1]         \n\t"
    "muleq_s.w.phl  %[r1],        %[r2],        %[r1]         \n\t"
    "addiu          %[k],         %[k],         -2            \n\t"
    "addu           %[r4],        %[r4],        %[r6]         \n\t"
    "subu           %[r5],        %[r7],        %[r5]         \n\t"
    "sra            %[r4],        %[r4],        8             \n\t"
    "sra            %[r5],        %[r5],        8             \n\t"
    "mult           $ac0,         %[factor],    %[r4]         \n\t"
    "mult           $ac1,         %[factor],    %[r5]         \n\t"
    "addu           %[r3],        %[r8],        %[r3]         \n\t"
    "subu           %[r0],        %[r1],        %[r0]         \n\t"
    "sra            %[r3],        %[r3],        8             \n\t"
    "sra            %[r0],        %[r0],        8             \n\t"
    "mult           $ac2,         %[factor],    %[r3]         \n\t"
    "mult           $ac3,         %[factor],    %[r0]         \n\t"
    "extr_r.w       %[r4],        $ac0,         16            \n\t"
    "extr_r.w       %[r5],        $ac1,         16            \n\t"
    "addiu          %[inre1],     %[inre1],     4             \n\t"
    "addiu          %[inre2],     %[inre2],     4             \n\t"
    "extr_r.w       %[r6],        $ac2,         16            \n\t"
    "extr_r.w       %[r7],        $ac3,         16            \n\t"
    "addiu          %[cosptr],    %[cosptr],    4             \n\t"
    "addiu          %[sinptr],    %[sinptr],    4             \n\t"
    "shra_r.w       %[r4],        %[r4],        3             \n\t"
    "shra_r.w       %[r5],        %[r5],        3             \n\t"
    "sw             %[r4],        0(%[tmpre])                 \n\t"
    "absq_s.w       %[r4],        %[r4]                       \n\t"
    "sw             %[r5],        0(%[tmpim])                 \n\t"
    "absq_s.w       %[r5],        %[r5]                       \n\t"
    "shra_r.w       %[r6],        %[r6],        3             \n\t"
    "shra_r.w       %[r7],        %[r7],        3             \n\t"
    "sw             %[r6],        4(%[tmpre])                 \n\t"
    "absq_s.w       %[r6],        %[r6]                       \n\t"
    "sw             %[r7],        4(%[tmpim])                 \n\t"
    "absq_s.w       %[r7],        %[r7]                       \n\t"
    "slt            %[r0],        %[r4],        %[r5]         \n\t"
    "movn           %[r4],        %[r5],        %[r0]         \n\t"
    "slt            %[r1],        %[r6],        %[r7]         \n\t"
    "movn           %[r6],        %[r7],        %[r1]         \n\t"
    "slt            %[r0],        %[max],       %[r4]         \n\t"
    "movn           %[max],       %[r4],        %[r0]         \n\t"
    "slt            %[r1],        %[max],       %[r6]         \n\t"
    "movn           %[max],       %[r6],        %[r1]         \n\t"
    "addiu          %[tmpre],     %[tmpre],     8             \n\t"
    "bgtz           %[k],         1b                          \n\t"
    " addiu         %[tmpim],     %[tmpim],     8             \n\t"
#else  // #if defined(MIPS_DSP_R2_LE)
    "lh             %[r0],        0(%[inre1])                 \n\t"
    "lh             %[r1],        0(%[inre2])                 \n\t"
    "lh             %[r2],        0(%[cosptr])                \n\t"
    "lh             %[r3],        0(%[sinptr])                \n\t"
    "addiu          %[k],         %[k],         -1            \n\t"
    "mul            %[r4],        %[r0],        %[r2]         \n\t"
    "mul            %[r5],        %[r1],        %[r3]         \n\t"
    "mul            %[r0],        %[r0],        %[r3]         \n\t"
    "mul            %[r2],        %[r1],        %[r2]         \n\t"
    "addiu          %[inre1],     %[inre1],     2             \n\t"
    "addiu          %[inre2],     %[inre2],     2             \n\t"
    "addiu          %[cosptr],    %[cosptr],    2             \n\t"
    "addiu          %[sinptr],    %[sinptr],    2             \n\t"
    "addu           %[r1],        %[r4],        %[r5]         \n\t"
    "sra            %[r1],        %[r1],        7             \n\t"
    "sra            %[r3],        %[r1],        16            \n\t"
    "andi           %[r1],        %[r1],        0xFFFF        \n\t"
    "sra            %[r1],        %[r1],        1             \n\t"
    "mul            %[r1],        %[factor],    %[r1]         \n\t"
    "mul            %[r3],        %[factor],    %[r3]         \n\t"
    "subu           %[r0],        %[r2],        %[r0]         \n\t"
    "sra            %[r0],        %[r0],        7             \n\t"
    "sra            %[r2],        %[r0],        16            \n\t"
    "andi           %[r0],        %[r0],        0xFFFF        \n\t"
    "sra            %[r0],        %[r0],        1             \n\t"
    "mul            %[r0],        %[factor],    %[r0]         \n\t"
    "mul            %[r2],        %[factor],    %[r2]         \n\t"
#if defined(MIPS_DSP_R1_LE)
    "shra_r.w       %[r1],        %[r1],        15            \n\t"
#else  // #if defined(MIPS_DSP_R1_LE)
    "addiu          %[r1],        %[r1],        0x4000        \n\t"
    "sra            %[r1],        %[r1],        15            \n\t"
#endif  // #if defined(MIPS_DSP_R1_LE)
    "addu           %[r1],        %[r3],        %[r1]         \n\t"
#if defined(MIPS_DSP_R1_LE)
    "shra_r.w       %[r1],        %[r1],        3             \n\t"
#else  // #if defined(MIPS_DSP_R1_LE)
    "addiu          %[r1],        %[r1],        4             \n\t"
    "sra            %[r1],        %[r1],        3             \n\t"
#endif  // #if defined(MIPS_DSP_R1_LE)
    "sw             %[r1],        0(%[tmpre])                 \n\t"
    "addiu          %[tmpre],     %[tmpre],     4             \n\t"
#if defined(MIPS_DSP_R1_LE)
    "absq_s.w       %[r1],        %[r1]                       \n\t"
    "shra_r.w       %[r0],        %[r0],        15            \n\t"
#else  // #if defined(MIPS_DSP_R1_LE)
    "negu           %[r4],        %[r1]                       \n\t"
    "slt            %[r3],        %[r1],        $zero         \n\t"
    "movn           %[r1],        %[r4],        %[r3]         \n\t"
    "addiu          %[r0],        %[r0],        0x4000        \n\t"
    "sra            %[r0],        %[r0],        15            \n\t"
#endif  // #if defined(MIPS_DSP_R1_LE)
    "addu           %[r0],        %[r0],        %[r2]         \n\t"
#if defined(MIPS_DSP_R1_LE)
    "shra_r.w       %[r0],        %[r0],        3             \n\t"
    "sw             %[r0],        0(%[tmpim])                 \n\t"
    "absq_s.w       %[r0],        %[r0]                       \n\t"
#else  // #if defined(MIPS_DSP_R1_LE)
    "addiu          %[r0],        %[r0],        4             \n\t"
    "sra            %[r0],        %[r0],        3             \n\t"
    "sw             %[r0],        0(%[tmpim])                 \n\t"
    "negu           %[r2],        %[r0]                       \n\t"
    "slt            %[r3],        %[r0],        $zero         \n\t"
    "movn           %[r0],        %[r2],        %[r3]         \n\t"
#endif  // #if defined(MIPS_DSP_R1_LE)
    "slt            %[r2],        %[max],       %[r1]         \n\t"
    "movn           %[max],       %[r1],        %[r2]         \n\t"
    "slt            %[r2],        %[max],       %[r0]         \n\t"
    "movn           %[max],       %[r0],        %[r2]         \n\t"
    "bgtz           %[k],         1b                          \n\t"
    " addiu         %[tmpim],     %[tmpim],     4             \n\t"
#endif  // #if defined(MIPS_DSP_R2_LE)
    // Calculate WebRtcSpl_NormW32(max).
    // If max gets value >=0, we should shift max steps to the left, and the
    // domain will be Q(16+shift). If max gets value <0, we should shift -max
    // steps to the right, and the domain will be Q(16+max)
    "clz            %[max],       %[max]                      \n\t"
    "addiu          %[max],       %[max],       -25           \n\t"
    ".set           pop                                       \n\t"
    : [k] "+r" (k), [inre1] "=&r" (inre1), [inre2] "=&r" (inre2),
      [r0] "=&r" (r0), [r1] "=&r" (r1), [r2] "=&r" (r2),
      [r3] "=&r" (r3), [r4] "=&r" (r4), [tmpre] "=&r" (tmpre),
      [tmpim] "=&r" (tmpim), [max] "=&r" (max), [factor] "=&r" (factor),
#if defined(MIPS_DSP_R2_LE)
      [r6] "=&r" (r6), [r7] "=&r" (r7), [r8] "=&r" (r8),
#endif  // #if defined(MIPS_DSP_R2_LE)
      [r5] "=&r" (r5)
    : [inre1Q9] "r" (inre1Q9), [inre2Q9] "r" (inre2Q9),
      [tmpreQ16] "r" (tmpreQ16), [tmpimQ16] "r" (tmpimQ16),
      [cosptr] "r" (cosptr), [sinptr] "r" (sinptr)
    : "hi", "lo", "memory"
#if defined(MIPS_DSP_R2_LE)
    , "$ac1hi", "$ac1lo", "$ac2hi", "$ac2lo", "$ac3hi", "$ac3lo"
#endif  // #if defined(MIPS_DSP_R2_LE)
  );

  // "Fastest" vectors
  k = FRAMESAMPLES / 4;
  __asm __volatile (
    ".set           push                                      \n\t"
    ".set           noreorder                                 \n\t"
    "addiu          %[tmpre],     %[tmpreQ16],  0             \n\t"
    "addiu          %[tmpim],     %[tmpimQ16],  0             \n\t"
    "addiu          %[inre1],     %[inre1Q9],   0             \n\t"
    "addiu          %[inre2],     %[inre2Q9],   0             \n\t"
    "blez           %[max],       2f                          \n\t"
    " subu          %[max1],      $zero,        %[max]        \n\t"
   "1:                                                        \n\t"
    "lw             %[r0],        0(%[tmpre])                 \n\t"
    "lw             %[r1],        0(%[tmpim])                 \n\t"
    "lw             %[r2],        4(%[tmpre])                 \n\t"
    "lw             %[r3],        4(%[tmpim])                 \n\t"
    "addiu          %[k],         %[k],         -1            \n\t"
    "sllv           %[r0],        %[r0],        %[max]        \n\t"
    "sllv           %[r1],        %[r1],        %[max]        \n\t"
    "sllv           %[r2],        %[r2],        %[max]        \n\t"
    "sllv           %[r3],        %[r3],        %[max]        \n\t"
    "addiu          %[tmpre],     %[tmpre],     8             \n\t"
    "addiu          %[tmpim],     %[tmpim],     8             \n\t"
    "sh             %[r0],        0(%[inre1])                 \n\t"
    "sh             %[r1],        0(%[inre2])                 \n\t"
    "sh             %[r2],        2(%[inre1])                 \n\t"
    "sh             %[r3],        2(%[inre2])                 \n\t"
    "addiu          %[inre1],     %[inre1],     4             \n\t"
    "bgtz           %[k],         1b                          \n\t"
    " addiu         %[inre2],     %[inre2],     4             \n\t"
    "b              4f                                        \n\t"
    " nop                                                     \n\t"
   "2:                                                        \n\t"
#if !defined(MIPS_DSP_R1_LE)
    "addiu          %[r4],        %[max1],      -1            \n\t"
    "addiu          %[r5],        $zero,        1             \n\t"
    "sllv           %[r4],        %[r5],        %[r4]         \n\t"
#endif // #if !defined(MIPS_DSP_R1_LE)
   "3:                                                        \n\t"
    "lw             %[r0],        0(%[tmpre])                 \n\t"
    "lw             %[r1],        0(%[tmpim])                 \n\t"
    "lw             %[r2],        4(%[tmpre])                 \n\t"
    "lw             %[r3],        4(%[tmpim])                 \n\t"
    "addiu          %[k],         %[k],         -1            \n\t"
#if defined(MIPS_DSP_R1_LE)
    "shrav_r.w      %[r0],        %[r0],        %[max1]       \n\t"
    "shrav_r.w      %[r1],        %[r1],        %[max1]       \n\t"
    "shrav_r.w      %[r2],        %[r2],        %[max1]       \n\t"
    "shrav_r.w      %[r3],        %[r3],        %[max1]       \n\t"
#else // #if !defined(MIPS_DSP_R1_LE)
    "addu           %[r0],        %[r0],        %[r4]         \n\t"
    "addu           %[r1],        %[r1],        %[r4]         \n\t"
    "addu           %[r2],        %[r2],        %[r4]         \n\t"
    "addu           %[r3],        %[r3],        %[r4]         \n\t"
    "srav           %[r0],        %[r0],        %[max1]       \n\t"
    "srav           %[r1],        %[r1],        %[max1]       \n\t"
    "srav           %[r2],        %[r2],        %[max1]       \n\t"
    "srav           %[r3],        %[r3],        %[max1]       \n\t"
#endif // #if !defined(MIPS_DSP_R1_LE)
    "addiu          %[tmpre],     %[tmpre],     8             \n\t"
    "addiu          %[tmpim],     %[tmpim],     8             \n\t"
    "sh             %[r0],        0(%[inre1])                 \n\t"
    "sh             %[r1],        0(%[inre2])                 \n\t"
    "sh             %[r2],        2(%[inre1])                 \n\t"
    "sh             %[r3],        2(%[inre2])                 \n\t"
    "addiu          %[inre1],     %[inre1],     4             \n\t"
    "bgtz           %[k],         3b                          \n\t"
    " addiu         %[inre2],     %[inre2],     4             \n\t"
   "4:                                                        \n\t"
    ".set           pop                                       \n\t"
    : [tmpre] "=&r" (tmpre), [tmpim] "=&r" (tmpim), [inre1] "=&r" (inre1),
      [inre2] "=&r" (inre2), [k] "+r" (k), [max1] "=&r" (max1),
#if !defined(MIPS_DSP_R1_LE)
      [r4] "=&r" (r4), [r5] "=&r" (r5),
#endif // #if !defined(MIPS_DSP_R1_LE)
      [r0] "=&r" (r0), [r1] "=&r" (r1), [r2] "=&r" (r2), [r3] "=&r" (r3)
    : [tmpreQ16] "r" (tmpreQ16), [tmpimQ16] "r" (tmpimQ16),
      [inre1Q9] "r" (inre1Q9), [inre2Q9] "r" (inre2Q9), [max] "r" (max)
    : "memory"
  );

  // Get DFT
  WebRtcIsacfix_FftRadix16Fastest(inre1Q9, inre2Q9, -1); // real call

  // "Fastest" vectors and
  // Use symmetry to separate into two complex vectors
  // and center frames in time around zero
  // merged into one loop
  cosptr = (int16_t*)WebRtcIsacfix_kCosTab2;
  sinptr = (int16_t*)WebRtcIsacfix_kSinTab2;
  k = FRAMESAMPLES / 4;
  factor = FRAMESAMPLES - 2;  // offset for FRAMESAMPLES / 2 - 1 array member

  __asm __volatile (
    ".set           push                                      \n\t"
    ".set           noreorder                                 \n\t"
    "addiu          %[inre1],     %[inre1Q9],   0             \n\t"
    "addiu          %[inre2],     %[inre2Q9],   0             \n\t"
    "addiu          %[tmpre],     %[outreQ7],   0             \n\t"
    "addiu          %[tmpim],     %[outimQ7],   0             \n\t"
    "bltz           %[max],       2f                          \n\t"
    " subu          %[max1],      $zero,        %[max]        \n\t"
   "1:                                                        \n\t"
#if !defined(MIPS_DSP_R1_LE)
    "addu           %[r4],        %[inre1],     %[offset]     \n\t"
    "addu           %[r5],        %[inre2],     %[offset]     \n\t"
#endif  // #if !defined(MIPS_DSP_R1_LE)
    "lh             %[r0],        0(%[inre1])                 \n\t"
    "lh             %[r1],        0(%[inre2])                 \n\t"
#if defined(MIPS_DSP_R1_LE)
    "lhx            %[r2],        %[offset](%[inre1])         \n\t"
    "lhx            %[r3],        %[offset](%[inre2])         \n\t"
#else  // #if defined(MIPS_DSP_R1_LE)
    "lh             %[r2],        0(%[r4])                    \n\t"
    "lh             %[r3],        0(%[r5])                    \n\t"
#endif  // #if defined(MIPS_DSP_R1_LE)
    "srav           %[r0],        %[r0],        %[max]        \n\t"
    "srav           %[r1],        %[r1],        %[max]        \n\t"
    "srav           %[r2],        %[r2],        %[max]        \n\t"
    "srav           %[r3],        %[r3],        %[max]        \n\t"
    "addu           %[r4],        %[r0],        %[r2]         \n\t"
    "subu           %[r0],        %[r2],        %[r0]         \n\t"
    "subu           %[r2],        %[r1],        %[r3]         \n\t"
    "addu           %[r1],        %[r1],        %[r3]         \n\t"
    "lh             %[r3],        0(%[cosptr])                \n\t"
    "lh             %[r5],        0(%[sinptr])                \n\t"
    "andi           %[r6],        %[r4],        0xFFFF        \n\t"
    "sra            %[r4],        %[r4],        16            \n\t"
    "mul            %[r7],        %[r3],        %[r6]         \n\t"
    "mul            %[r8],        %[r3],        %[r4]         \n\t"
    "mul            %[r6],        %[r5],        %[r6]         \n\t"
    "mul            %[r4],        %[r5],        %[r4]         \n\t"
    "addiu          %[k],         %[k],         -1            \n\t"
    "addiu          %[inre1],     %[inre1],     2             \n\t"
    "addiu          %[inre2],     %[inre2],     2             \n\t"
#if defined(MIPS_DSP_R1_LE)
    "shra_r.w       %[r7],        %[r7],        14            \n\t"
#else  // #if defined(MIPS_DSP_R1_LE)
    "addiu          %[r7],        %[r7],        0x2000        \n\t"
    "sra            %[r7],        %[r7],        14            \n\t"
#endif  // #if defined(MIPS_DSP_R1_LE)
    "sll            %[r8],        %[r8],        2             \n\t"
    "addu           %[r8],        %[r8],        %[r7]         \n\t"
#if defined(MIPS_DSP_R1_LE)
    "shra_r.w       %[r6],        %[r6],        14            \n\t"
#else  // #if defined(MIPS_DSP_R1_LE)
    "addiu          %[r6],        %[r6],        0x2000        \n\t"
    "sra            %[r6],        %[r6],        14            \n\t"
#endif  // #if defined(MIPS_DSP_R1_LE)
    "sll            %[r4],        %[r4],        2             \n\t"
    "addu           %[r4],        %[r4],        %[r6]         \n\t"
    "andi           %[r6],        %[r2],        0xFFFF        \n\t"
    "sra            %[r2],        %[r2],        16            \n\t"
    "mul            %[r7],        %[r5],        %[r6]         \n\t"
    "mul            %[r9],        %[r5],        %[r2]         \n\t"
    "mul            %[r6],        %[r3],        %[r6]         \n\t"
    "mul            %[r2],        %[r3],        %[r2]         \n\t"
    "addiu          %[cosptr],    %[cosptr],    2             \n\t"
    "addiu          %[sinptr],    %[sinptr],    2             \n\t"
#if defined(MIPS_DSP_R1_LE)
    "shra_r.w       %[r7],        %[r7],        14            \n\t"
#else  // #if defined(MIPS_DSP_R1_LE)
    "addiu          %[r7],        %[r7],        0x2000        \n\t"
    "sra            %[r7],        %[r7],        14            \n\t"
#endif  // #if defined(MIPS_DSP_R1_LE)
    "sll            %[r9],        %[r9],        2             \n\t"
    "addu           %[r9],        %[r7],        %[r9]         \n\t"
#if defined(MIPS_DSP_R1_LE)
    "shra_r.w       %[r6],        %[r6],        14            \n\t"
#else  // #if defined(MIPS_DSP_R1_LE)
    "addiu          %[r6],        %[r6],        0x2000        \n\t"
    "sra            %[r6],        %[r6],        14            \n\t"
#endif  // #if defined(MIPS_DSP_R1_LE)
    "sll            %[r2],        %[r2],        2             \n\t"
    "addu           %[r2],        %[r6],        %[r2]         \n\t"
    "subu           %[r8],        %[r8],        %[r9]         \n\t"
    "sra            %[r8],        %[r8],        9             \n\t"
    "addu           %[r2],        %[r4],        %[r2]         \n\t"
    "sra            %[r2],        %[r2],        9             \n\t"
    "sh             %[r8],        0(%[tmpre])                 \n\t"
    "sh             %[r2],        0(%[tmpim])                 \n\t"

    "andi           %[r4],        %[r1],        0xFFFF        \n\t"
    "sra            %[r1],        %[r1],        16            \n\t"
    "andi           %[r6],        %[r0],        0xFFFF        \n\t"
    "sra            %[r0],        %[r0],        16            \n\t"
    "mul            %[r7],        %[r5],        %[r4]         \n\t"
    "mul            %[r9],        %[r5],        %[r1]         \n\t"
    "mul            %[r4],        %[r3],        %[r4]         \n\t"
    "mul            %[r1],        %[r3],        %[r1]         \n\t"
    "mul            %[r8],        %[r3],        %[r0]         \n\t"
    "mul            %[r3],        %[r3],        %[r6]         \n\t"
    "mul            %[r6],        %[r5],        %[r6]         \n\t"
    "mul            %[r0],        %[r5],        %[r0]         \n\t"
#if defined(MIPS_DSP_R1_LE)
    "shra_r.w       %[r7],        %[r7],        14            \n\t"
#else  // #if defined(MIPS_DSP_R1_LE)
    "addiu          %[r7],        %[r7],        0x2000        \n\t"
    "sra            %[r7],        %[r7],        14            \n\t"
#endif  // #if defined(MIPS_DSP_R1_LE)
    "sll            %[r9],        %[r9],        2             \n\t"
    "addu           %[r9],        %[r9],        %[r7]         \n\t"
#if defined(MIPS_DSP_R1_LE)
    "shra_r.w       %[r4],        %[r4],        14            \n\t"
#else  // #if defined(MIPS_DSP_R1_LE)
    "addiu          %[r4],        %[r4],        0x2000        \n\t"
    "sra            %[r4],        %[r4],        14            \n\t"
#endif  // #if defined(MIPS_DSP_R1_LE)
    "sll            %[r1],        %[r1],        2             \n\t"
    "addu           %[r1],        %[r1],        %[r4]         \n\t"
#if defined(MIPS_DSP_R1_LE)
    "shra_r.w       %[r3],        %[r3],        14            \n\t"
#else  // #if defined(MIPS_DSP_R1_LE)
    "addiu          %[r3],        %[r3],        0x2000        \n\t"
    "sra            %[r3],        %[r3],        14            \n\t"
#endif  // #if defined(MIPS_DSP_R1_LE)
    "sll            %[r8],        %[r8],        2             \n\t"
    "addu           %[r8],        %[r8],        %[r3]         \n\t"
#if defined(MIPS_DSP_R1_LE)
    "shra_r.w       %[r6],        %[r6],        14            \n\t"
#else  // #if defined(MIPS_DSP_R1_LE)
    "addiu          %[r6],        %[r6],        0x2000        \n\t"
    "sra            %[r6],        %[r6],        14            \n\t"
#endif  // #if defined(MIPS_DSP_R1_LE)
    "sll            %[r0],        %[r0],        2             \n\t"
    "addu           %[r0],        %[r0],        %[r6]         \n\t"
    "addu           %[r3],        %[tmpre],     %[offset]     \n\t"
    "addu           %[r2],        %[tmpim],     %[offset]     \n\t"
    "addu           %[r9],        %[r9],        %[r8]         \n\t"
    "negu           %[r9],        %[r9]                       \n\t"
    "sra            %[r9],        %[r9],        9             \n\t"
    "subu           %[r0],        %[r0],        %[r1]         \n\t"
    "addiu          %[offset],    %[offset],    -4            \n\t"
    "sh             %[r9],        0(%[r3])                    \n\t"
    "sh             %[r0],        0(%[r2])                    \n\t"
    "addiu          %[tmpre],     %[tmpre],     2             \n\t"
    "bgtz           %[k],         1b                          \n\t"
    " addiu         %[tmpim],     %[tmpim],     2             \n\t"
    "b              3f                                        \n\t"
    " nop                                                     \n\t"
   "2:                                                        \n\t"
#if !defined(MIPS_DSP_R1_LE)
    "addu           %[r4],        %[inre1],     %[offset]     \n\t"
    "addu           %[r5],        %[inre2],     %[offset]     \n\t"
#endif  // #if !defined(MIPS_DSP_R1_LE)
    "lh             %[r0],        0(%[inre1])                 \n\t"
    "lh             %[r1],        0(%[inre2])                 \n\t"
#if defined(MIPS_DSP_R1_LE)
    "lhx            %[r2],        %[offset](%[inre1])         \n\t"
    "lhx            %[r3],        %[offset](%[inre2])         \n\t"
#else  // #if defined(MIPS_DSP_R1_LE)
    "lh             %[r2],        0(%[r4])                    \n\t"
    "lh             %[r3],        0(%[r5])                    \n\t"
#endif  // #if defined(MIPS_DSP_R1_LE)
    "sllv           %[r0],        %[r0],        %[max1]       \n\t"
    "sllv           %[r1],        %[r1],        %[max1]       \n\t"
    "sllv           %[r2],        %[r2],        %[max1]       \n\t"
    "sllv           %[r3],        %[r3],        %[max1]       \n\t"
    "addu           %[r4],        %[r0],        %[r2]         \n\t"
    "subu           %[r0],        %[r2],        %[r0]         \n\t"
    "subu           %[r2],        %[r1],        %[r3]         \n\t"
    "addu           %[r1],        %[r1],        %[r3]         \n\t"
    "lh             %[r3],        0(%[cosptr])                \n\t"
    "lh             %[r5],        0(%[sinptr])                \n\t"
    "andi           %[r6],        %[r4],        0xFFFF        \n\t"
    "sra            %[r4],        %[r4],        16            \n\t"
    "mul            %[r7],        %[r3],        %[r6]         \n\t"
    "mul            %[r8],        %[r3],        %[r4]         \n\t"
    "mul            %[r6],        %[r5],        %[r6]         \n\t"
    "mul            %[r4],        %[r5],        %[r4]         \n\t"
    "addiu          %[k],         %[k],         -1            \n\t"
    "addiu          %[inre1],     %[inre1],     2             \n\t"
    "addiu          %[inre2],     %[inre2],     2             \n\t"
#if defined(MIPS_DSP_R1_LE)
    "shra_r.w       %[r7],        %[r7],        14            \n\t"
#else  // #if defined(MIPS_DSP_R1_LE)
    "addiu          %[r7],        %[r7],        0x2000        \n\t"
    "sra            %[r7],        %[r7],        14            \n\t"
#endif  // #if defined(MIPS_DSP_R1_LE)
    "sll            %[r8],        %[r8],        2             \n\t"
    "addu           %[r8],        %[r8],        %[r7]         \n\t"
#if defined(MIPS_DSP_R1_LE)
    "shra_r.w       %[r6],        %[r6],        14            \n\t"
#else  // #if defined(MIPS_DSP_R1_LE)
    "addiu          %[r6],        %[r6],        0x2000        \n\t"
    "sra            %[r6],        %[r6],        14            \n\t"
#endif  // #if defined(MIPS_DSP_R1_LE)
    "sll            %[r4],        %[r4],        2             \n\t"
    "addu           %[r4],        %[r4],        %[r6]         \n\t"
    "andi           %[r6],        %[r2],        0xFFFF        \n\t"
    "sra            %[r2],        %[r2],        16            \n\t"
    "mul            %[r7],        %[r5],        %[r6]         \n\t"
    "mul            %[r9],        %[r5],        %[r2]         \n\t"
    "mul            %[r6],        %[r3],        %[r6]         \n\t"
    "mul            %[r2],        %[r3],        %[r2]         \n\t"
    "addiu          %[cosptr],    %[cosptr],    2             \n\t"
    "addiu          %[sinptr],    %[sinptr],    2             \n\t"
#if defined(MIPS_DSP_R1_LE)
    "shra_r.w       %[r7],        %[r7],        14            \n\t"
#else  // #if defined(MIPS_DSP_R1_LE)
    "addiu          %[r7],        %[r7],        0x2000        \n\t"
    "sra            %[r7],        %[r7],        14            \n\t"
#endif  // #if defined(MIPS_DSP_R1_LE)
    "sll            %[r9],        %[r9],        2             \n\t"
    "addu           %[r9],        %[r7],        %[r9]         \n\t"
#if defined(MIPS_DSP_R1_LE)
    "shra_r.w       %[r6],        %[r6],        14            \n\t"
#else  // #if defined(MIPS_DSP_R1_LE)
    "addiu          %[r6],        %[r6],        0x2000        \n\t"
    "sra            %[r6],        %[r6],        14            \n\t"
#endif  // #if defined(MIPS_DSP_R1_LE)
    "sll            %[r2],        %[r2],        2             \n\t"
    "addu           %[r2],        %[r6],        %[r2]         \n\t"
    "subu           %[r8],        %[r8],        %[r9]         \n\t"
    "sra            %[r8],        %[r8],        9             \n\t"
    "addu           %[r2],        %[r4],        %[r2]         \n\t"
    "sra            %[r2],        %[r2],        9             \n\t"
    "sh             %[r8],        0(%[tmpre])                 \n\t"
    "sh             %[r2],        0(%[tmpim])                 \n\t"
    "andi           %[r4],        %[r1],        0xFFFF        \n\t"
    "sra            %[r1],        %[r1],        16            \n\t"
    "andi           %[r6],        %[r0],        0xFFFF        \n\t"
    "sra            %[r0],        %[r0],        16            \n\t"
    "mul            %[r7],        %[r5],        %[r4]         \n\t"
    "mul            %[r9],        %[r5],        %[r1]         \n\t"
    "mul            %[r4],        %[r3],        %[r4]         \n\t"
    "mul            %[r1],        %[r3],        %[r1]         \n\t"
    "mul            %[r8],        %[r3],        %[r0]         \n\t"
    "mul            %[r3],        %[r3],        %[r6]         \n\t"
    "mul            %[r6],        %[r5],        %[r6]         \n\t"
    "mul            %[r0],        %[r5],        %[r0]         \n\t"
#if defined(MIPS_DSP_R1_LE)
    "shra_r.w       %[r7],        %[r7],        14            \n\t"
#else  // #if defined(MIPS_DSP_R1_LE)
    "addiu          %[r7],        %[r7],        0x2000        \n\t"
    "sra            %[r7],        %[r7],        14            \n\t"
#endif  // #if defined(MIPS_DSP_R1_LE)
    "sll            %[r9],        %[r9],        2             \n\t"
    "addu           %[r9],        %[r9],        %[r7]         \n\t"
#if defined(MIPS_DSP_R1_LE)
    "shra_r.w       %[r4],        %[r4],        14            \n\t"
#else  // #if defined(MIPS_DSP_R1_LE)
    "addiu          %[r4],        %[r4],        0x2000        \n\t"
    "sra            %[r4],        %[r4],        14            \n\t"
#endif
    "sll            %[r1],        %[r1],        2             \n\t"
    "addu           %[r1],        %[r1],        %[r4]         \n\t"
#if defined(MIPS_DSP_R1_LE)
    "shra_r.w       %[r3],        %[r3],        14            \n\t"
#else  // #if defined(MIPS_DSP_R1_LE)
    "addiu          %[r3],        %[r3],        0x2000        \n\t"
    "sra            %[r3],        %[r3],        14            \n\t"
#endif  // #if defined(MIPS_DSP_R1_LE)
    "sll            %[r8],        %[r8],        2             \n\t"
    "addu           %[r8],        %[r8],        %[r3]         \n\t"
#if defined(MIPS_DSP_R1_LE)
    "shra_r.w       %[r6],        %[r6],        14            \n\t"
#else  // #if defined(MIPS_DSP_R1_LE)
    "addiu          %[r6],        %[r6],        0x2000        \n\t"
    "sra            %[r6],        %[r6],        14            \n\t"
#endif  // #if defined(MIPS_DSP_R1_LE)
    "sll            %[r0],        %[r0],        2             \n\t"
    "addu           %[r0],        %[r0],        %[r6]         \n\t"
    "addu           %[r3],        %[tmpre],     %[offset]     \n\t"
    "addu           %[r2],        %[tmpim],     %[offset]     \n\t"
    "addu           %[r9],        %[r9],        %[r8]         \n\t"
    "negu           %[r9],        %[r9]                       \n\t"
    "sra            %[r9],        %[r9],        9             \n\t"
    "subu           %[r0],        %[r0],        %[r1]         \n\t"
    "sra            %[r0],        %[r0],        9             \n\t"
    "addiu          %[offset],    %[offset],    -4            \n\t"
    "sh             %[r9],        0(%[r3])                    \n\t"
    "sh             %[r0],        0(%[r2])                    \n\t"
    "addiu          %[tmpre],     %[tmpre],     2             \n\t"
    "bgtz           %[k],         2b                          \n\t"
    " addiu         %[tmpim],     %[tmpim],     2             \n\t"
   "3:                                                        \n\t"
    ".set           pop                                       \n\t"
    : [inre1] "=&r" (inre1), [inre2] "=&r" (inre2), [tmpre] "=&r" (tmpre),
      [tmpim] "=&r" (tmpim), [offset] "+r" (factor), [k] "+r" (k),
      [r0] "=&r" (r0), [r1] "=&r" (r1), [r2] "=&r" (r2), [r3] "=&r" (r3),
      [r4] "=&r" (r4), [r5] "=&r" (r5), [r6] "=&r" (r6), [r7] "=&r" (r7),
      [r8] "=&r" (r8), [r9] "=&r" (r9), [max1] "=&r" (max1)
    : [inre1Q9] "r" (inre1Q9), [inre2Q9] "r" (inre2Q9),
      [outreQ7] "r" (outreQ7), [outimQ7] "r" (outimQ7),
      [max] "r" (max), [cosptr] "r" (cosptr), [sinptr] "r" (sinptr)
    : "hi", "lo", "memory"
  );
}

void WebRtcIsacfix_Spec2TimeMIPS(int16_t *inreQ7,
                                 int16_t *inimQ7,
                                 int32_t *outre1Q16,
                                 int32_t *outre2Q16) {
  int k = FRAMESAMPLES / 4;
  int16_t* inre;
  int16_t* inim;
  int32_t* outre1;
  int32_t* outre2;
  int16_t* cosptr = (int16_t*)WebRtcIsacfix_kCosTab2;
  int16_t* sinptr = (int16_t*)WebRtcIsacfix_kSinTab2;
  int32_t r0, r1, r2, r3, r4, r5, r6, r7, r8, r9, max, max1;
#if defined(MIPS_DSP_R1_LE)
  int32_t offset = FRAMESAMPLES - 4;
#else  // #if defined(MIPS_DSP_R1_LE)
  int32_t offset = FRAMESAMPLES - 2;
#endif  // #if defined(MIPS_DSP_R1_LE)

  __asm __volatile (
    ".set           push                                      \n\t"
    ".set           noreorder                                 \n\t"
    "addiu          %[inre],      %[inreQ7],    0             \n\t"
    "addiu          %[inim] ,     %[inimQ7],    0             \n\t"
    "addiu          %[outre1],    %[outre1Q16], 0             \n\t"
    "addiu          %[outre2],    %[outre2Q16], 0             \n\t"
    "mul            %[max],       $zero,        $zero         \n\t"
   "1:                                                        \n\t"
#if defined(MIPS_DSP_R1_LE)
    // Process two samples in one iteration avoiding left shift before
    // multiplication. MaxAbsValueW32 function inlined into the loop.
    "addu           %[r8],        %[inre],      %[offset]     \n\t"
    "addu           %[r9],        %[inim],      %[offset]     \n\t"
    "lwl            %[r4],        0(%[r8])                    \n\t"
    "lwl            %[r5],        0(%[r9])                    \n\t"
    "lwl            %[r0],        0(%[inre])                  \n\t"
    "lwl            %[r1],        0(%[inim])                  \n\t"
    "lwl            %[r2],        0(%[cosptr])                \n\t"
    "lwl            %[r3],        0(%[sinptr])                \n\t"
    "lwr            %[r4],        0(%[r8])                    \n\t"
    "lwr            %[r5],        0(%[r9])                    \n\t"
    "lwr            %[r0],        0(%[inre])                  \n\t"
    "lwr            %[r1],        0(%[inim])                  \n\t"
    "lwr            %[r2],        0(%[cosptr])                \n\t"
    "lwr            %[r3],        0(%[sinptr])                \n\t"
    "packrl.ph      %[r4],        %[r4],        %[r4]         \n\t"
    "packrl.ph      %[r5],        %[r5],        %[r5]         \n\t"
    "muleq_s.w.phr  %[r6],        %[r0],        %[r2]         \n\t"
    "muleq_s.w.phr  %[r7],        %[r1],        %[r3]         \n\t"
    "muleq_s.w.phr  %[r8],        %[r4],        %[r2]         \n\t"
    "muleq_s.w.phr  %[r9],        %[r5],        %[r3]         \n\t"
    "addiu          %[k],         %[k],         -2            \n\t"
    "addiu          %[cosptr],    %[cosptr],    4             \n\t"
    "addiu          %[sinptr],    %[sinptr],    4             \n\t"
    "addiu          %[inre],      %[inre],      4             \n\t"
    "addiu          %[inim],      %[inim],      4             \n\t"
    "shra_r.w       %[r6],        %[r6],        6             \n\t"
    "shra_r.w       %[r7],        %[r7],        6             \n\t"
    "shra_r.w       %[r8],        %[r8],        6             \n\t"
    "shra_r.w       %[r9],        %[r9],        6             \n\t"
    "addu           %[r6],        %[r6],        %[r7]         \n\t"
    "subu           %[r9],        %[r9],        %[r8]         \n\t"
    "subu           %[r7],        %[r6],        %[r9]         \n\t"
    "addu           %[r6],        %[r6],        %[r9]         \n\t"
    "sw             %[r7],        0(%[outre1])                \n\t"
    "absq_s.w       %[r7],        %[r7]                       \n\t"
    "slt            %[r8],        %[max],       %[r7]         \n\t"
    "movn           %[max],       %[r7],        %[r8]         \n\t"
    "sll            %[r7],        %[offset],    1             \n\t"
    "addu           %[r7],        %[outre1],    %[r7]         \n\t"
    "sw             %[r6],        4(%[r7])                    \n\t"
    "absq_s.w       %[r6],        %[r6]                       \n\t"
    "slt            %[r8],        %[max],       %[r6]         \n\t"
    "movn           %[max],       %[r6],        %[r8]         \n\t"
    "muleq_s.w.phl  %[r6],        %[r0],        %[r2]         \n\t"
    "muleq_s.w.phl  %[r7],        %[r1],        %[r3]         \n\t"
    "muleq_s.w.phl  %[r8],        %[r4],        %[r2]         \n\t"
    "muleq_s.w.phl  %[r9],        %[r5],        %[r3]         \n\t"
    "shra_r.w       %[r6],        %[r6],        6             \n\t"
    "shra_r.w       %[r7],        %[r7],        6             \n\t"
    "shra_r.w       %[r8],        %[r8],        6             \n\t"
    "shra_r.w       %[r9],        %[r9],        6             \n\t"
    "addu           %[r6],        %[r6],        %[r7]         \n\t"
    "subu           %[r9],        %[r9],        %[r8]         \n\t"
    "subu           %[r7],        %[r6],        %[r9]         \n\t"
    "addu           %[r6],        %[r6],        %[r9]         \n\t"
    "sw             %[r7],        4(%[outre1])                \n\t"
    "absq_s.w       %[r7],        %[r7]                       \n\t"
    "slt            %[r8],        %[max],       %[r7]         \n\t"
    "movn           %[max],       %[r7],        %[r8]         \n\t"
    "sll            %[r7],        %[offset],    1             \n\t"
    "addu           %[r7],        %[outre1],    %[r7]         \n\t"
    "sw             %[r6],        0(%[r7])                    \n\t"
    "absq_s.w       %[r6],        %[r6]                       \n\t"
    "slt            %[r8],        %[max],       %[r6]         \n\t"
    "movn           %[max],       %[r6],        %[r8]         \n\t"
    "muleq_s.w.phr  %[r6],        %[r1],        %[r2]         \n\t"
    "muleq_s.w.phr  %[r7],        %[r0],        %[r3]         \n\t"
    "muleq_s.w.phr  %[r8],        %[r5],        %[r2]         \n\t"
    "muleq_s.w.phr  %[r9],        %[r4],        %[r3]         \n\t"
    "addiu          %[outre1],    %[outre1],    8             \n\t"
    "shra_r.w       %[r6],        %[r6],        6             \n\t"
    "shra_r.w       %[r7],        %[r7],        6             \n\t"
    "shra_r.w       %[r8],        %[r8],        6             \n\t"
    "shra_r.w       %[r9],        %[r9],        6             \n\t"
    "subu           %[r6],        %[r6],        %[r7]         \n\t"
    "addu           %[r9],        %[r9],        %[r8]         \n\t"
    "subu           %[r7],        %[r6],        %[r9]         \n\t"
    "addu           %[r6],        %[r9],        %[r6]         \n\t"
    "negu           %[r6],        %[r6]                       \n\t"
    "sw             %[r7],        0(%[outre2])                \n\t"
    "absq_s.w       %[r7],        %[r7]                       \n\t"
    "slt            %[r8],        %[max],       %[r7]         \n\t"
    "movn           %[max],       %[r7],        %[r8]         \n\t"
    "sll            %[r7],        %[offset],    1             \n\t"
    "addu           %[r7],        %[outre2],    %[r7]         \n\t"
    "sw             %[r6],        4(%[r7])                    \n\t"
    "absq_s.w       %[r6],        %[r6]                       \n\t"
    "slt            %[r8],        %[max],       %[r6]         \n\t"
    "movn           %[max],       %[r6],        %[r8]         \n\t"
    "muleq_s.w.phl  %[r6],       %[r1],         %[r2]         \n\t"
    "muleq_s.w.phl  %[r7],       %[r0],         %[r3]         \n\t"
    "muleq_s.w.phl  %[r8],       %[r5],         %[r2]         \n\t"
    "muleq_s.w.phl  %[r9],       %[r4],         %[r3]         \n\t"
    "addiu          %[offset],   %[offset],     -8            \n\t"
    "shra_r.w       %[r6],       %[r6],         6             \n\t"
    "shra_r.w       %[r7],       %[r7],         6             \n\t"
    "shra_r.w       %[r8],       %[r8],         6             \n\t"
    "shra_r.w       %[r9],       %[r9],         6             \n\t"
    "subu           %[r6],       %[r6],         %[r7]         \n\t"
    "addu           %[r9],       %[r9],         %[r8]         \n\t"
    "subu           %[r7],       %[r6],         %[r9]         \n\t"
    "addu           %[r6],       %[r9],         %[r6]         \n\t"
    "negu           %[r6],       %[r6]                        \n\t"
    "sw             %[r7],       4(%[outre2])                 \n\t"
    "absq_s.w       %[r7],       %[r7]                        \n\t"
    "slt            %[r8],       %[max],        %[r7]         \n\t"
    "movn           %[max],      %[r7],         %[r8]         \n\t"
    "sll            %[r7],       %[offset],     1             \n\t"
    "addu           %[r7],       %[outre2],     %[r7]         \n\t"
    "sw             %[r6],       0(%[r7])                     \n\t"
    "absq_s.w       %[r6],       %[r6]                        \n\t"
    "slt            %[r8],       %[max],        %[r6]         \n\t"
    "movn           %[max],      %[r6],         %[r8]         \n\t"
    "bgtz           %[k],        1b                           \n\t"
    " addiu         %[outre2],   %[outre2],     8             \n\t"
#else  // #if defined(MIPS_DSP_R1_LE)
    "lh             %[r0],       0(%[inre])                   \n\t"
    "lh             %[r1],       0(%[inim])                   \n\t"
    "lh             %[r4],       0(%[cosptr])                 \n\t"
    "lh             %[r5],       0(%[sinptr])                 \n\t"
    "addiu          %[k],        %[k],          -1            \n\t"
    "mul            %[r2],       %[r0],         %[r4]         \n\t"
    "mul            %[r0],       %[r0],         %[r5]         \n\t"
    "mul            %[r3],       %[r1],         %[r5]         \n\t"
    "mul            %[r1],       %[r1],         %[r4]         \n\t"
    "addiu          %[cosptr],   %[cosptr],     2             \n\t"
    "addiu          %[sinptr],   %[sinptr],     2             \n\t"
    "addu           %[r8],       %[inre],       %[offset]     \n\t"
    "addu           %[r9],       %[inim],       %[offset]     \n\t"
    "addiu          %[r2],       %[r2],         16            \n\t"
    "sra            %[r2],       %[r2],         5             \n\t"
    "addiu          %[r0],       %[r0],         16            \n\t"
    "sra            %[r0],       %[r0],         5             \n\t"
    "addiu          %[r3],       %[r3],         16            \n\t"
    "sra            %[r3],       %[r3],         5             \n\t"
    "lh             %[r6],       0(%[r8])                     \n\t"
    "lh             %[r7],       0(%[r9])                     \n\t"
    "addiu          %[r1],       %[r1],         16            \n\t"
    "sra            %[r1],       %[r1],         5             \n\t"
    "mul            %[r8],       %[r7],         %[r4]         \n\t"
    "mul            %[r7],       %[r7],         %[r5]         \n\t"
    "mul            %[r9],       %[r6],         %[r4]         \n\t"
    "mul            %[r6],       %[r6],         %[r5]         \n\t"
    "addu           %[r2],       %[r2],         %[r3]         \n\t"
    "subu           %[r1],       %[r1],         %[r0]         \n\t"
    "sll            %[r0],       %[offset],     1             \n\t"
    "addu           %[r4],       %[outre1],     %[r0]         \n\t"
    "addu           %[r5],       %[outre2],     %[r0]         \n\t"
    "addiu          %[r8],       %[r8],         16            \n\t"
    "sra            %[r8],       %[r8],         5             \n\t"
    "addiu          %[r7],       %[r7],         16            \n\t"
    "sra            %[r7],       %[r7],         5             \n\t"
    "addiu          %[r6],       %[r6],         16            \n\t"
    "sra            %[r6],       %[r6],         5             \n\t"
    "addiu          %[r9],       %[r9],         16            \n\t"
    "sra            %[r9],       %[r9],         5             \n\t"
    "addu           %[r8],       %[r8],         %[r6]         \n\t"
    "negu           %[r8],       %[r8]                        \n\t"
    "subu           %[r7],       %[r7],         %[r9]         \n\t"
    "subu           %[r6],       %[r2],         %[r7]         \n\t"
    "addu           %[r0],       %[r2],         %[r7]         \n\t"
    "addu           %[r3],       %[r1],         %[r8]         \n\t"
    "subu           %[r1],       %[r8],         %[r1]         \n\t"
    "sw             %[r6],       0(%[outre1])                 \n\t"
    "sw             %[r0],       0(%[r4])                     \n\t"
    "sw             %[r3],       0(%[outre2])                 \n\t"
    "sw             %[r1],       0(%[r5])                     \n\t"
    "addiu          %[outre1],   %[outre1],     4             \n\t"
    "addiu          %[offset],   %[offset],     -4            \n\t"
    "addiu          %[inre],     %[inre],       2             \n\t"
    "addiu          %[inim],     %[inim],       2             \n\t"
    // Inlined WebRtcSpl_MaxAbsValueW32
    "negu           %[r5],       %[r6]                        \n\t"
    "slt            %[r2],       %[r6],         $zero         \n\t"
    "movn           %[r6],       %[r5],         %[r2]         \n\t"
    "negu           %[r5],       %[r0]                        \n\t"
    "slt            %[r2],       %[r0],         $zero         \n\t"
    "movn           %[r0],       %[r5],         %[r2]         \n\t"
    "negu           %[r5],       %[r3]                        \n\t"
    "slt            %[r2],       %[r3],         $zero         \n\t"
    "movn           %[r3],       %[r5],         %[r2]         \n\t"
    "negu           %[r5],       %[r1]                        \n\t"
    "slt            %[r2],       %[r1],         $zero         \n\t"
    "movn           %[r1],       %[r5],         %[r2]         \n\t"
    "slt            %[r2],       %[r6],         %[r0]         \n\t"
    "slt            %[r5],       %[r3],         %[r1]         \n\t"
    "movn           %[r6],       %[r0],         %[r2]         \n\t"
    "movn           %[r3],       %[r1],         %[r5]         \n\t"
    "slt            %[r2],       %[r6],         %[r3]         \n\t"
    "movn           %[r6],       %[r3],         %[r2]         \n\t"
    "slt            %[r2],       %[max],        %[r6]         \n\t"
    "movn           %[max],      %[r6],         %[r2]         \n\t"
    "bgtz           %[k],        1b                           \n\t"
    " addiu         %[outre2],   %[outre2],     4             \n\t"
#endif  // #if defined(MIPS_DSP_R1_LE)
    "clz            %[max],      %[max]                       \n\t"
    "addiu          %[max],      %[max],        -25           \n\t"
    ".set           pop                                       \n\t"
    : [inre] "=&r" (inre), [inim] "=&r" (inim),
      [outre1] "=&r" (outre1), [outre2] "=&r" (outre2),
      [offset] "+r" (offset), [k] "+r" (k), [r0] "=&r" (r0),
      [r1] "=&r" (r1), [r2] "=&r" (r2), [r3] "=&r" (r3),
      [r4] "=&r" (r4), [r5] "=&r" (r5), [r6] "=&r" (r6),
      [r7] "=&r" (r7), [r8] "=&r" (r8), [r9] "=&r" (r9),
      [max] "=&r" (max)
    : [inreQ7] "r" (inreQ7), [inimQ7] "r" (inimQ7),
      [cosptr] "r" (cosptr), [sinptr] "r" (sinptr),
      [outre1Q16] "r" (outre1Q16), [outre2Q16] "r" (outre2Q16)
    : "hi", "lo", "memory"
  );

  // "Fastest" vectors
  k = FRAMESAMPLES / 4;
  __asm __volatile (
    ".set           push                                      \n\t"
    ".set           noreorder                                 \n\t"
    "addiu          %[inre],      %[inreQ7],    0             \n\t"
    "addiu          %[inim],      %[inimQ7],    0             \n\t"
    "addiu          %[outre1],    %[outre1Q16], 0             \n\t"
    "addiu          %[outre2],    %[outre2Q16], 0             \n\t"
    "bltz           %[max],       2f                          \n\t"
    " subu          %[max1],      $zero,        %[max]        \n\t"
   "1:                                                        \n\t"
    "lw             %[r0],        0(%[outre1])                \n\t"
    "lw             %[r1],        0(%[outre2])                \n\t"
    "lw             %[r2],        4(%[outre1])                \n\t"
    "lw             %[r3],        4(%[outre2])                \n\t"
    "sllv           %[r0],        %[r0],        %[max]        \n\t"
    "sllv           %[r1],        %[r1],        %[max]        \n\t"
    "sllv           %[r2],        %[r2],        %[max]        \n\t"
    "sllv           %[r3],        %[r3],        %[max]        \n\t"
    "addiu          %[k],         %[k],         -1            \n\t"
    "addiu          %[outre1],    %[outre1],    8             \n\t"
    "addiu          %[outre2],    %[outre2],    8             \n\t"
    "sh             %[r0],        0(%[inre])                  \n\t"
    "sh             %[r1],        0(%[inim])                  \n\t"
    "sh             %[r2],        2(%[inre])                  \n\t"
    "sh             %[r3],        2(%[inim])                  \n\t"
    "addiu          %[inre],      %[inre],      4             \n\t"
    "bgtz           %[k],         1b                          \n\t"
    " addiu         %[inim],      %[inim],      4             \n\t"
    "b              4f                                        \n\t"
    " nop                                                     \n\t"
   "2:                                                        \n\t"
#if !defined(MIPS_DSP_R1_LE)
    "addiu          %[r4],        $zero,        1             \n\t"
    "addiu          %[r5],        %[max1],      -1            \n\t"
    "sllv           %[r4],        %[r4],        %[r5]         \n\t"
#endif  // #if !defined(MIPS_DSP_R1_LE)
   "3:                                                        \n\t"
    "lw             %[r0],        0(%[outre1])                \n\t"
    "lw             %[r1],        0(%[outre2])                \n\t"
    "lw             %[r2],        4(%[outre1])                \n\t"
    "lw             %[r3],        4(%[outre2])                \n\t"
#if defined(MIPS_DSP_R1_LE)
    "shrav_r.w      %[r0],        %[r0],        %[max1]       \n\t"
    "shrav_r.w      %[r1],        %[r1],        %[max1]       \n\t"
    "shrav_r.w      %[r2],        %[r2],        %[max1]       \n\t"
    "shrav_r.w      %[r3],        %[r3],        %[max1]       \n\t"
#else  // #if defined(MIPS_DSP_R1_LE)
    "addu           %[r0],        %[r0],        %[r4]         \n\t"
    "addu           %[r1],        %[r1],        %[r4]         \n\t"
    "addu           %[r2],        %[r2],        %[r4]         \n\t"
    "addu           %[r3],        %[r3],        %[r4]         \n\t"
    "srav           %[r0],        %[r0],        %[max1]       \n\t"
    "srav           %[r1],        %[r1],        %[max1]       \n\t"
    "srav           %[r2],        %[r2],        %[max1]       \n\t"
    "srav           %[r3],        %[r3],        %[max1]       \n\t"
#endif  // #if defined(MIPS_DSP_R1_LE)
    "addiu          %[outre1],    %[outre1],    8             \n\t"
    "addiu          %[outre2],    %[outre2],    8             \n\t"
    "sh             %[r0],        0(%[inre])                  \n\t"
    "sh             %[r1],        0(%[inim])                  \n\t"
    "sh             %[r2],        2(%[inre])                  \n\t"
    "sh             %[r3],        2(%[inim])                  \n\t"
    "addiu          %[k],         %[k],         -1            \n\t"
    "addiu          %[inre],      %[inre],      4             \n\t"
    "bgtz           %[k],         3b                          \n\t"
    " addiu         %[inim],      %[inim],      4             \n\t"
   "4:                                                        \n\t"
    ".set           pop                                       \n\t"
    : [k] "+r" (k), [max1] "=&r" (max1), [r0] "=&r" (r0),
      [inre] "=&r" (inre), [inim] "=&r" (inim),
      [outre1] "=&r" (outre1), [outre2] "=&r" (outre2),
#if !defined(MIPS_DSP_R1_LE)
      [r4] "=&r" (r4), [r5] "=&r" (r5),
#endif  // #if !defined(MIPS_DSP_R1_LE)
      [r1] "=&r" (r1), [r2] "=&r" (r2), [r3] "=&r" (r3)
    : [max] "r" (max), [inreQ7] "r" (inreQ7),
      [inimQ7] "r" (inimQ7), [outre1Q16] "r" (outre1Q16),
      [outre2Q16] "r" (outre2Q16)
    : "memory"
  );

  WebRtcIsacfix_FftRadix16Fastest(inreQ7, inimQ7, 1); // real call

  // All the remaining processing is done inside a single loop to avoid
  // unnecessary memory accesses. MIPS DSPr2 version processes two samples
  // at a time.
  cosptr = (int16_t*)WebRtcIsacfix_kCosTab1;
  sinptr = (int16_t*)WebRtcIsacfix_kSinTab1;
  k = FRAMESAMPLES / 2;
  __asm __volatile (
    ".set           push                                      \n\t"
    ".set           noreorder                                 \n\t"
    "addiu          %[inre],      %[inreQ7],    0             \n\t"
    "addiu          %[inim],      %[inimQ7],    0             \n\t"
    "addiu          %[outre1],    %[outre1Q16], 0             \n\t"
    "addiu          %[outre2],    %[outre2Q16], 0             \n\t"
    "addiu          %[r4],        $zero,        273           \n\t"
    "addiu          %[r5],        $zero,        31727         \n\t"
#if defined(MIPS_DSP_R2_LE)
    "addiu          %[max],       %[max],       16            \n\t"
    "replv.ph       %[r4],        %[r4]                       \n\t"
#endif  // #if defined(MIPS_DSP_R2_LE)
    "bltz           %[max],       2f                          \n\t"
    " subu          %[max1],      $zero,        %[max]        \n\t"
#if defined(MIPS_DSP_R2_LE)
    "addiu          %[max],       %[max],       1             \n\t"
#endif  // #if defined(MIPS_DSP_R2_LE)
   "1:                                                        \n\t"
#if defined(MIPS_DSP_R2_LE)
    "lwl            %[r0],        0(%[inre])                  \n\t"
    "lwl            %[r1],        0(%[inim])                  \n\t"
    "lh             %[r2],        0(%[cosptr])                \n\t"
    "lwr            %[r0],        0(%[inre])                  \n\t"
    "lwr            %[r1],        0(%[inim])                  \n\t"
    "lh             %[r3],        0(%[sinptr])                \n\t"
    "muleq_s.w.phr  %[r6],        %[r0],        %[r4]         \n\t"
    "muleq_s.w.phr  %[r7],        %[r1],        %[r4]         \n\t"
    "muleq_s.w.phl  %[r0],        %[r0],        %[r4]         \n\t"
    "muleq_s.w.phl  %[r1],        %[r1],        %[r4]         \n\t"
    "addiu          %[k],         %[k],         -2            \n\t"
    "addiu          %[inre],      %[inre],      4             \n\t"
    "addiu          %[inim],      %[inim],      4             \n\t"
    "shrav_r.w      %[r6],        %[r6],        %[max]        \n\t"
    "shrav_r.w      %[r7],        %[r7],        %[max]        \n\t"
    "mult           $ac0,         %[r2],        %[r6]         \n\t"
    "mult           $ac1,         %[r3],        %[r7]         \n\t"
    "mult           $ac2,         %[r2],        %[r7]         \n\t"
    "mult           $ac3,         %[r3],        %[r6]         \n\t"
    "lh             %[r2],        2(%[cosptr])                \n\t"
    "lh             %[r3],        2(%[sinptr])                \n\t"
    "extr_r.w       %[r6],        $ac0,         14            \n\t"
    "extr_r.w       %[r7],        $ac1,         14            \n\t"
    "extr_r.w       %[r8],        $ac2,         14            \n\t"
    "extr_r.w       %[r9],        $ac3,         14            \n\t"
    "shrav_r.w      %[r0],        %[r0],        %[max]        \n\t"
    "shrav_r.w      %[r1],        %[r1],        %[max]        \n\t"
    "mult           $ac0,         %[r2],        %[r0]         \n\t"
    "mult           $ac1,         %[r3],        %[r1]         \n\t"
    "mult           $ac2,         %[r2],        %[r1]         \n\t"
    "mult           $ac3,         %[r3],        %[r0]         \n\t"
    "addiu          %[cosptr],    %[cosptr],    4             \n\t"
    "extr_r.w       %[r0],        $ac0,         14            \n\t"
    "extr_r.w       %[r1],        $ac1,         14            \n\t"
    "extr_r.w       %[r2],        $ac2,         14            \n\t"
    "extr_r.w       %[r3],        $ac3,         14            \n\t"
    "subu           %[r6],        %[r6],        %[r7]         \n\t"
    "addu           %[r8],        %[r8],        %[r9]         \n\t"
    "mult           $ac0,         %[r5],        %[r6]         \n\t"
    "mult           $ac1,         %[r5],        %[r8]         \n\t"
    "addiu          %[sinptr],    %[sinptr],    4             \n\t"
    "subu           %[r0],        %[r0],        %[r1]         \n\t"
    "addu           %[r2],        %[r2],        %[r3]         \n\t"
    "extr_r.w       %[r1],        $ac0,         11            \n\t"
    "extr_r.w       %[r3],        $ac1,         11            \n\t"
    "mult           $ac2,         %[r5],        %[r0]         \n\t"
    "mult           $ac3,         %[r5],        %[r2]         \n\t"
    "sw             %[r1],        0(%[outre1])                \n\t"
    "sw             %[r3],        0(%[outre2])                \n\t"
    "addiu          %[outre1],    %[outre1],    8             \n\t"
    "extr_r.w       %[r0],        $ac2,         11            \n\t"
    "extr_r.w       %[r2],        $ac3,         11            \n\t"
    "sw             %[r0],        -4(%[outre1])               \n\t"
    "sw             %[r2],        4(%[outre2])                \n\t"
    "bgtz           %[k],         1b                          \n\t"
    " addiu         %[outre2],    %[outre2],    8             \n\t"
    "b              3f                                        \n\t"
#else  // #if defined(MIPS_DSP_R2_LE)
    "lh             %[r0],        0(%[inre])                  \n\t"
    "lh             %[r1],        0(%[inim])                  \n\t"
    "addiu          %[k],         %[k],         -1            \n\t"
    "srav           %[r0],        %[r0],        %[max]        \n\t"
    "srav           %[r1],        %[r1],        %[max]        \n\t"
    "sra            %[r2],        %[r0],        16            \n\t"
    "andi           %[r0],        %[r0],        0xFFFF        \n\t"
    "sra            %[r0],        %[r0],        1             \n\t"
    "sra            %[r3],        %[r1],        16            \n\t"
    "andi           %[r1],        %[r1],        0xFFFF        \n\t"
    "sra            %[r1],        %[r1],        1             \n\t"
    "mul            %[r2],        %[r2],        %[r4]         \n\t"
    "mul            %[r0],        %[r0],        %[r4]         \n\t"
    "mul            %[r3],        %[r3],        %[r4]         \n\t"
    "mul            %[r1],        %[r1],        %[r4]         \n\t"
    "addiu          %[inre],      %[inre],      2             \n\t"
    "addiu          %[inim],      %[inim],      2             \n\t"
    "lh             %[r6],        0(%[cosptr])                \n\t"
    "lh             %[r7],        0(%[sinptr])                \n\t"
#if defined(MIPS_DSP_R1_LE)
    "shra_r.w       %[r0],        %[r0],        15            \n\t"
    "shra_r.w       %[r1],        %[r1],        15            \n\t"
#else  // #if defined(MIPS_DSP_R1_LE)
    "addiu          %[r0],        %[r0],        0x4000        \n\t"
    "addiu          %[r1],        %[r1],        0x4000        \n\t"
    "sra            %[r0],        %[r0],        15            \n\t"
    "sra            %[r1],        %[r1],        15            \n\t"
#endif  // #if defined(MIPS_DSP_R1_LE)
    "addu           %[r0],        %[r2],        %[r0]         \n\t"
    "addu           %[r1],        %[r3],        %[r1]         \n\t"
    "sra            %[r2],        %[r0],        16            \n\t"
    "andi           %[r0],        %[r0],        0xFFFF        \n\t"
    "mul            %[r9],        %[r2],        %[r6]         \n\t"
    "mul            %[r2],        %[r2],        %[r7]         \n\t"
    "mul            %[r8],        %[r0],        %[r6]         \n\t"
    "mul            %[r0],        %[r0],        %[r7]         \n\t"
    "sra            %[r3],        %[r3],        16            \n\t"
    "andi           %[r1],        %[r1],        0xFFFF        \n\t"
    "sll            %[r9],        %[r9],        2             \n\t"
    "sll            %[r2],        %[r2],        2             \n\t"
#if defined(MIPS_DSP_R1_LE)
    "shra_r.w       %[r8],        %[r8],        14            \n\t"
    "shra_r.w       %[r0],        %[r0],        14            \n\t"
#else  // #if defined(MIPS_DSP_R1_LE)
    "addiu          %[r8],        %[r8],        0x2000        \n\t"
    "addiu          %[r0],        %[r0],        0x2000        \n\t"
    "sra            %[r8],        %[r8],        14            \n\t"
    "sra            %[r0],        %[r0],        14            \n\t"
#endif  // #if defined(MIPS_DSP_R1_LE)
    "addu           %[r9],        %[r9],        %[r8]         \n\t"
    "addu           %[r2],        %[r2],        %[r0]         \n\t"
    "mul            %[r0],        %[r3],        %[r6]         \n\t"
    "mul            %[r3],        %[r3],        %[r7]         \n\t"
    "mul            %[r8],        %[r1],        %[r6]         \n\t"
    "mul            %[r1],        %[r1],        %[r8]         \n\t"
    "addiu          %[cosptr],    %[cosptr],    2             \n\t"
    "addiu          %[sinptr],    %[sinptr],    2             \n\t"
    "sll            %[r0],        %[r0],        2             \n\t"
    "sll            %[r3],        %[r3],        2             \n\t"
#if defined(MIPS_DSP_R1_LE)
    "shra_r.w       %[r8],        %[r8],        14            \n\t"
    "shra_r.w       %[r1],        %[r1],        14            \n\t"
#else  // #if defined(MIPS_DSP_R1_LE)
    "addiu          %[r8],        %[r8],        0x2000        \n\t"
    "addiu          %[r1],        %[r1],        0x2000        \n\t"
    "sra            %[r8],        %[r8],        14            \n\t"
    "sra            %[r1],        %[r1],        14            \n\t"
#endif  // #if defined(MIPS_DSP_R1_LE)
    "addu           %[r0],        %[r0],        %[r8]         \n\t"
    "addu           %[r3],        %[r3],        %[r1]         \n\t"
    "subu           %[r9],        %[r9],        %[r3]         \n\t"
    "addu           %[r0],        %[r0],        %[r2]         \n\t"
    "sra            %[r1],        %[r9],        16            \n\t"
    "andi           %[r9],        %[r9],        0xFFFF        \n\t"
    "mul            %[r1],        %[r1],        %[r5]         \n\t"
    "mul            %[r9],        %[r9],        %[r5]         \n\t"
    "sra            %[r2],        %[r0],        16            \n\t"
    "andi           %[r0],        %[r0],        0xFFFF        \n\t"
    "mul            %[r2],        %[r2],        %[r5]         \n\t"
    "mul            %[r0],        %[r0],        %[r5]         \n\t"
    "sll            %[r1],        %[r1],        5             \n\t"
#if defined(MIPS_DSP_R1_LE)
    "shra_r.w       %[r9],        %[r9],        11            \n\t"
#else  // #if defined(MIPS_DSP_R1_LE)
    "addiu          %[r9],        %[r9],        0x400         \n\t"
    "sra            %[r9],        %[r9],        11            \n\t"
#endif  // #if defined(MIPS_DSP_R1_LE)
    "addu           %[r1],        %[r1],        %[r9]         \n\t"
    "sll            %[r2],        %[r2],        5             \n\t"
#if defined(MIPS_DSP_R1_LE)
    "shra_r.w       %[r0],        %[r0],        11            \n\t"
#else  // #if defined(MIPS_DSP_R1_LE)
    "addiu          %[r0],        %[r0],        0x400         \n\t"
    "sra            %[r0],        %[r0],        11            \n\t"
#endif  // #if defined(MIPS_DSP_R1_LE)
    "addu           %[r0],        %[r0],        %[r2]         \n\t"
    "sw             %[r1],        0(%[outre1])                \n\t"
    "addiu          %[outre1],    %[outre1],    4             \n\t"
    "sw             %[r0],        0(%[outre2])                \n\t"
    "bgtz           %[k],         1b                          \n\t"
    " addiu         %[outre2],    %[outre2],    4             \n\t"
    "b              3f                                        \n\t"
    " nop                                                     \n\t"
#endif  // #if defined(MIPS_DSP_R2_LE)
   "2:                                                        \n\t"
#if defined(MIPS_DSP_R2_LE)
    "addiu          %[max1],      %[max1],      -1            \n\t"
   "21:                                                       \n\t"
    "lwl            %[r0],        0(%[inre])                  \n\t"
    "lwl            %[r1],        0(%[inim])                  \n\t"
    "lh             %[r2],        0(%[cosptr])                \n\t"
    "lwr            %[r0],        0(%[inre])                  \n\t"
    "lwr            %[r1],        0(%[inim])                  \n\t"
    "lh             %[r3],        0(%[sinptr])                \n\t"
    "muleq_s.w.phr  %[r6],        %[r0],        %[r4]         \n\t"
    "muleq_s.w.phr  %[r7],        %[r1],        %[r4]         \n\t"
    "muleq_s.w.phl  %[r0],        %[r0],        %[r4]         \n\t"
    "muleq_s.w.phl  %[r1],        %[r1],        %[r4]         \n\t"
    "addiu          %[k],         %[k],         -2            \n\t"
    "addiu          %[inre],      %[inre],      4             \n\t"
    "addiu          %[inim],      %[inim],      4             \n\t"
    "sllv           %[r6],        %[r6],        %[max1]       \n\t"
    "sllv           %[r7],        %[r7],        %[max1]       \n\t"
    "mult           $ac0,         %[r2],        %[r6]         \n\t"
    "mult           $ac1,         %[r3],        %[r7]         \n\t"
    "mult           $ac2,         %[r2],        %[r7]         \n\t"
    "mult           $ac3,         %[r3],        %[r6]         \n\t"
    "lh             %[r2],        2(%[cosptr])                \n\t"
    "lh             %[r3],        2(%[sinptr])                \n\t"
    "extr_r.w       %[r6],        $ac0,         14            \n\t"
    "extr_r.w       %[r7],        $ac1,         14            \n\t"
    "extr_r.w       %[r8],        $ac2,         14            \n\t"
    "extr_r.w       %[r9],        $ac3,         14            \n\t"
    "sllv           %[r0],        %[r0],        %[max1]       \n\t"
    "sllv           %[r1],        %[r1],        %[max1]       \n\t"
    "mult           $ac0,         %[r2],        %[r0]         \n\t"
    "mult           $ac1,         %[r3],        %[r1]         \n\t"
    "mult           $ac2,         %[r2],        %[r1]         \n\t"
    "mult           $ac3,         %[r3],        %[r0]         \n\t"
    "addiu          %[cosptr],    %[cosptr],    4             \n\t"
    "extr_r.w       %[r0],        $ac0,         14            \n\t"
    "extr_r.w       %[r1],        $ac1,         14            \n\t"
    "extr_r.w       %[r2],        $ac2,         14            \n\t"
    "extr_r.w       %[r3],        $ac3,         14            \n\t"
    "subu           %[r6],        %[r6],        %[r7]         \n\t"
    "addu           %[r8],        %[r8],        %[r9]         \n\t"
    "mult           $ac0,         %[r5],        %[r6]         \n\t"
    "mult           $ac1,         %[r5],        %[r8]         \n\t"
    "addiu          %[sinptr],    %[sinptr],    4             \n\t"
    "subu           %[r0],        %[r0],        %[r1]         \n\t"
    "addu           %[r2],        %[r2],        %[r3]         \n\t"
    "extr_r.w       %[r1],        $ac0,         11            \n\t"
    "extr_r.w       %[r3],        $ac1,         11            \n\t"
    "mult           $ac2,         %[r5],        %[r0]         \n\t"
    "mult           $ac3,         %[r5],        %[r2]         \n\t"
    "sw             %[r1],        0(%[outre1])                \n\t"
    "sw             %[r3],        0(%[outre2])                \n\t"
    "addiu          %[outre1],    %[outre1],    8             \n\t"
    "extr_r.w       %[r0],        $ac2,         11            \n\t"
    "extr_r.w       %[r2],        $ac3,         11            \n\t"
    "sw             %[r0],        -4(%[outre1])               \n\t"
    "sw             %[r2],        4(%[outre2])                \n\t"
    "bgtz           %[k],         21b                         \n\t"
    " addiu         %[outre2],    %[outre2],    8             \n\t"
    "b              3f                                        \n\t"
    " nop                                                     \n\t"
#else  // #if defined(MIPS_DSP_R2_LE)
    "lh             %[r0],        0(%[inre])                  \n\t"
    "lh             %[r1],        0(%[inim])                  \n\t"
    "addiu          %[k],         %[k],         -1            \n\t"
    "sllv           %[r0],        %[r0],        %[max1]       \n\t"
    "sllv           %[r1],        %[r1],        %[max1]       \n\t"
    "sra            %[r2],        %[r0],        16            \n\t"
    "andi           %[r0],        %[r0],        0xFFFF        \n\t"
    "sra            %[r0],        %[r0],        1             \n\t"
    "sra            %[r3],        %[r1],        16            \n\t"
    "andi           %[r1],        %[r1],        0xFFFF        \n\t"
    "sra            %[r1],        %[r1],        1             \n\t"
    "mul            %[r2],        %[r2],        %[r4]         \n\t"
    "mul            %[r0],        %[r0],        %[r4]         \n\t"
    "mul            %[r3],        %[r3],        %[r4]         \n\t"
    "mul            %[r1],        %[r1],        %[r4]         \n\t"
    "addiu          %[inre],      %[inre],      2             \n\t"
    "addiu          %[inim],      %[inim],      2             \n\t"
    "lh             %[r6],        0(%[cosptr])                \n\t"
    "lh             %[r7],        0(%[sinptr])                \n\t"
#if defined(MIPS_DSP_R1_LE)
    "shra_r.w       %[r0],        %[r0],        15            \n\t"
    "shra_r.w       %[r1],        %[r1],        15            \n\t"
#else  // #if defined(MIPS_DSP_R1_LE)
    "addiu          %[r0],        %[r0],        0x4000        \n\t"
    "addiu          %[r1],        %[r1],        0x4000        \n\t"
    "sra            %[r0],        %[r0],        15            \n\t"
    "sra            %[r1],        %[r1],        15            \n\t"
#endif  // #if defined(MIPS_DSP_R1_LE)
    "addu           %[r0],        %[r2],        %[r0]         \n\t"
    "addu           %[r1],        %[r3],        %[r1]         \n\t"
    "sra            %[r2],        %[r0],        16            \n\t"
    "andi           %[r0],        %[r0],        0xFFFF        \n\t"
    "mul            %[r9],        %[r2],        %[r6]         \n\t"
    "mul            %[r2],        %[r2],        %[r7]         \n\t"
    "mul            %[r8],        %[r0],        %[r6]         \n\t"
    "mul            %[r0],        %[r0],        %[r7]         \n\t"
    "sra            %[r3],        %[r1],        16            \n\t"
    "andi           %[r1],        %[r1],        0xFFFF        \n\t"
    "sll            %[r9],        %[r9],        2             \n\t"
    "sll            %[r2],        %[r2],        2             \n\t"
#if defined(MIPS_DSP_R1_LE)
    "shra_r.w       %[r8],        %[r8],        14            \n\t"
    "shra_r.w       %[r0],        %[r0],        14            \n\t"
#else  // #if defined(MIPS_DSP_R1_LE)
    "addiu          %[r8],        %[r8],        0x2000        \n\t"
    "addiu          %[r0],        %[r0],        0x2000        \n\t"
    "sra            %[r8],        %[r8],        14            \n\t"
    "sra            %[r0],        %[r0],        14            \n\t"
#endif  // #if defined(MIPS_DSP_R1_LE)
    "addu           %[r9],        %[r9],        %[r8]         \n\t"
    "addu           %[r2],        %[r2],        %[r0]         \n\t"
    "mul            %[r0],        %[r3],        %[r6]         \n\t"
    "mul            %[r3],        %[r3],        %[r7]         \n\t"
    "mul            %[r8],        %[r1],        %[r6]         \n\t"
    "mul            %[r1],        %[r1],        %[r7]         \n\t"
    "addiu          %[cosptr],    %[cosptr],    2             \n\t"
    "addiu          %[sinptr],    %[sinptr],    2             \n\t"
    "sll            %[r0],        %[r0],        2             \n\t"
    "sll            %[r3],        %[r3],        2             \n\t"
#if defined(MIPS_DSP_R1_LE)
    "shra_r.w       %[r8],        %[r8],        14            \n\t"
    "shra_r.w       %[r1],        %[r1],        14            \n\t"
#else  // #if defined(MIPS_DSP_R1_LE)
    "addiu          %[r8],        %[r8],        0x2000        \n\t"
    "addiu          %[r1],        %[r1],        0x2000        \n\t"
    "sra            %[r8],        %[r8],        14            \n\t"
    "sra            %[r1],        %[r1],        14            \n\t"
#endif  // #if defined(MIPS_DSP_R1_LE)
    "addu           %[r0],        %[r0],        %[r8]         \n\t"
    "addu           %[r3],        %[r3],        %[r1]         \n\t"
    "subu           %[r9],        %[r9],        %[r3]         \n\t"
    "addu           %[r0],        %[r0],        %[r2]         \n\t"
    "sra            %[r1],        %[r9],        16            \n\t"
    "andi           %[r9],        %[r9],        0xFFFF        \n\t"
    "mul            %[r1],        %[r1],        %[r5]         \n\t"
    "mul            %[r9],        %[r9],        %[r5]         \n\t"
    "sra            %[r2],        %[r0],        16            \n\t"
    "andi           %[r0],        %[r0],        0xFFFF        \n\t"
    "mul            %[r2],        %[r2],        %[r5]         \n\t"
    "mul            %[r0],        %[r0],        %[r5]         \n\t"
    "sll            %[r1],        %[r1],        5             \n\t"
#if defined(MIPS_DSP_R1_LE)
    "shra_r.w       %[r9],        %[r9],        11            \n\t"
#else  // #if defined(MIPS_DSP_R1_LE)
    "addiu          %[r9],        %[r9],        0x400         \n\t"
    "sra            %[r9],        %[r9],        11            \n\t"
#endif  // #if defined(MIPS_DSP_R1_LE)
    "addu           %[r1],        %[r1],        %[r9]         \n\t"
    "sll            %[r2],        %[r2],        5             \n\t"
#if defined(MIPS_DSP_R1_LE)
    "shra_r.w       %[r0],        %[r0],        11            \n\t"
#else  // #if defined(MIPS_DSP_R1_LE)
    "addiu          %[r0],        %[r0],        0x400         \n\t"
    "sra            %[r0],        %[r0],        11            \n\t"
#endif  // #if defined(MIPS_DSP_R1_LE)
    "addu           %[r0],        %[r0],        %[r2]         \n\t"
    "sw             %[r1],        0(%[outre1])                \n\t"
    "addiu          %[outre1],    %[outre1],    4             \n\t"
    "sw             %[r0],        0(%[outre2])                \n\t"
    "bgtz           %[k],         2b                          \n\t"
    " addiu         %[outre2],    %[outre2],    4             \n\t"
#endif  // #if defined(MIPS_DSP_R2_LE)
   "3:                                                        \n\t"
    ".set           pop                                       \n\t"
    : [k] "+r" (k), [r0] "=&r" (r0), [r1] "=&r" (r1),
      [r2] "=&r" (r2), [r3] "=&r" (r3), [r4] "=&r" (r4),
      [r5] "=&r" (r5), [r6] "=&r" (r6), [r7] "=&r" (r7),
      [r8] "=&r" (r8), [r9] "=&r" (r9), [max1] "=&r" (max1),
      [inre] "=&r" (inre), [inim] "=&r" (inim),
      [outre1] "=&r" (outre1), [outre2] "=&r" (outre2)
    : [max] "r" (max), [inreQ7] "r" (inreQ7),
      [inimQ7] "r" (inimQ7), [cosptr] "r" (cosptr),
      [sinptr] "r" (sinptr), [outre1Q16] "r" (outre1Q16),
      [outre2Q16] "r" (outre2Q16)
    : "hi", "lo", "memory"
#if defined(MIPS_DSP_R2_LE)
    , "$ac1hi", "$ac1lo", "$ac2hi", "$ac2lo", "$ac3hi", "$ac3lo"
#endif  // #if defined(MIPS_DSP_R2_LE)
  );
}
