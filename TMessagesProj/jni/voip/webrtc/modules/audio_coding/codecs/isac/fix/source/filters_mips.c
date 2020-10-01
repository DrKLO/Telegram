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

// MIPS optimized implementation of the Autocorrelation function in fixed point.
// NOTE! Different from SPLIB-version in how it scales the signal.
int WebRtcIsacfix_AutocorrMIPS(int32_t* __restrict r,
                               const int16_t* __restrict x,
                               int16_t N,
                               int16_t order,
                               int16_t* __restrict scale) {
  int i = 0;
  int16_t scaling = 0;
  int16_t* in = (int16_t*)x;
  int loop_size = (int)(N >> 3);
  int count = (int)(N & 7);
  // Declare temporary variables used as registry values.
  int32_t r0, r1, r2, r3;
#if !defined(MIPS_DSP_R2_LE)
  // For non-DSPR2 optimizations 4 more registers are used.
  int32_t r4, r5, r6, r7;
#endif

  // Calculate r[0] and scaling needed.
  __asm __volatile (
    ".set          push                                            \n\t"
    ".set          noreorder                                       \n\t"
    "mult          $0,             $0                              \n\t"
    // Loop is unrolled 8 times, set accumulator to zero in branch delay slot.
    "beqz          %[loop_size],   2f                              \n\t"
    " mult         $0,             $0                              \n\t"
   "1:                                                             \n\t"
    // Load 8 samples per loop iteration.
#if defined(MIPS_DSP_R2_LE)
    "ulw           %[r0],          0(%[in])                        \n\t"
    "ulw           %[r1],          4(%[in])                        \n\t"
    "ulw           %[r2],          8(%[in])                        \n\t"
    "ulw           %[r3],          12(%[in])                       \n\t"
#else
    "lh            %[r0],          0(%[in])                        \n\t"
    "lh            %[r1],          2(%[in])                        \n\t"
    "lh            %[r2],          4(%[in])                        \n\t"
    "lh            %[r3],          6(%[in])                        \n\t"
    "lh            %[r4],          8(%[in])                        \n\t"
    "lh            %[r5],          10(%[in])                       \n\t"
    "lh            %[r6],          12(%[in])                       \n\t"
    "lh            %[r7],          14(%[in])                       \n\t"
#endif
    "addiu         %[loop_size],   %[loop_size],   -1              \n\t"
    // Multiply and accumulate.
#if defined(MIPS_DSP_R2_LE)
    "dpa.w.ph      $ac0,           %[r0],          %[r0]           \n\t"
    "dpa.w.ph      $ac0,           %[r1],          %[r1]           \n\t"
    "dpa.w.ph      $ac0,           %[r2],          %[r2]           \n\t"
    "dpa.w.ph      $ac0,           %[r3],          %[r3]           \n\t"
#else
    "madd          %[r0],          %[r0]                           \n\t"
    "madd          %[r1],          %[r1]                           \n\t"
    "madd          %[r2],          %[r2]                           \n\t"
    "madd          %[r3],          %[r3]                           \n\t"
    "madd          %[r4],          %[r4]                           \n\t"
    "madd          %[r5],          %[r5]                           \n\t"
    "madd          %[r6],          %[r6]                           \n\t"
    "madd          %[r7],          %[r7]                           \n\t"
#endif
    "bnez          %[loop_size],   1b                              \n\t"
    " addiu        %[in],          %[in],          16              \n\t"
   "2:                                                             \n\t"
    "beqz          %[count],       4f                              \n\t"
#if defined(MIPS_DSP_R1_LE)
    " extr.w       %[r0],          $ac0,           31              \n\t"
#else
    " mfhi         %[r2]                                           \n\t"
#endif
    // Process remaining samples (if any).
   "3:                                                             \n\t"
    "lh            %[r0],          0(%[in])                        \n\t"
    "addiu         %[count],       %[count],       -1              \n\t"
    "madd          %[r0],          %[r0]                           \n\t"
    "bnez          %[count],       3b                              \n\t"
    " addiu        %[in],          %[in],          2               \n\t"
#if defined(MIPS_DSP_R1_LE)
    "extr.w        %[r0],          $ac0,           31              \n\t"
#else
    "mfhi          %[r2]                                           \n\t"
#endif
   "4:                                                             \n\t"
#if !defined(MIPS_DSP_R1_LE)
    "mflo          %[r3]                                           \n\t"
    "sll           %[r0],          %[r2],          1               \n\t"
    "srl           %[r1],          %[r3],          31              \n\t"
    "addu          %[r0],          %[r0],          %[r1]           \n\t"
#endif
    // Calculate scaling (the value of shifting).
    "clz           %[r1],          %[r0]                           \n\t"
    "addiu         %[r1],          %[r1],          -32             \n\t"
    "subu          %[scaling],     $0,             %[r1]           \n\t"
    "slti          %[r1],          %[r0],          0x1             \n\t"
    "movn          %[scaling],     $0,             %[r1]           \n\t"
#if defined(MIPS_DSP_R1_LE)
    "extrv.w       %[r0],          $ac0,           %[scaling]      \n\t"
    "mfhi          %[r2],          $ac0                            \n\t"
#else
    "addiu         %[r1],          %[scaling],     -32             \n\t"
    "subu          %[r1],          $0,             %[r1]           \n\t"
    "sllv          %[r1],          %[r2],          %[r1]           \n\t"
    "srlv          %[r0],          %[r3],          %[scaling]      \n\t"
    "addu          %[r0],          %[r0],          %[r1]           \n\t"
#endif
    "slti          %[r1],          %[scaling],     32              \n\t"
    "movz          %[r0],          %[r2],          %[r1]           \n\t"
    ".set          pop                                             \n\t"
    : [loop_size] "+r" (loop_size), [in] "+r" (in), [r0] "=&r" (r0),
      [r1] "=&r" (r1), [r2] "=&r" (r2), [r3] "=&r" (r3),
#if !defined(MIPS_DSP_R2_LE)
      [r4] "=&r" (r4), [r5] "=&r" (r5), [r6] "=&r" (r6), [r7] "=&r" (r7),
#endif
      [count] "+r" (count), [scaling] "=r" (scaling)
    : [N] "r" (N)
    : "memory", "hi", "lo"
  );
  r[0] = r0;

  // Correlation calculation is divided in 3 cases depending on the scaling
  // value (different accumulator manipulation needed). Three slightly different
  // loops are written in order to avoid branches inside the loop.
  if (scaling == 0) {
    // In this case, the result will be in low part of the accumulator.
    for (i = 1; i < order + 1; i++) {
      in = (int16_t*)x;
      int16_t* in1 = (int16_t*)x + i;
      count = N - i;
      loop_size = (count) >> 2;
      __asm  __volatile (
        ".set        push                                          \n\t"
        ".set        noreorder                                     \n\t"
        "mult        $0,             $0                            \n\t"
        "beqz        %[loop_size],   2f                            \n\t"
        " andi       %[count],       %[count],       0x3           \n\t"
        // Loop processing 4 pairs of samples per iteration.
       "1:                                                         \n\t"
#if defined(MIPS_DSP_R2_LE)
        "ulw         %[r0],          0(%[in])                      \n\t"
        "ulw         %[r1],          0(%[in1])                     \n\t"
        "ulw         %[r2],          4(%[in])                      \n\t"
        "ulw         %[r3],          4(%[in1])                     \n\t"
#else
        "lh          %[r0],          0(%[in])                      \n\t"
        "lh          %[r1],          0(%[in1])                     \n\t"
        "lh          %[r2],          2(%[in])                      \n\t"
        "lh          %[r3],          2(%[in1])                     \n\t"
        "lh          %[r4],          4(%[in])                      \n\t"
        "lh          %[r5],          4(%[in1])                     \n\t"
        "lh          %[r6],          6(%[in])                      \n\t"
        "lh          %[r7],          6(%[in1])                     \n\t"
#endif
        "addiu       %[loop_size],   %[loop_size],   -1            \n\t"
#if defined(MIPS_DSP_R2_LE)
        "dpa.w.ph    $ac0,           %[r0],          %[r1]         \n\t"
        "dpa.w.ph    $ac0,           %[r2],          %[r3]         \n\t"
#else
        "madd        %[r0],          %[r1]                         \n\t"
        "madd        %[r2],          %[r3]                         \n\t"
        "madd        %[r4],          %[r5]                         \n\t"
        "madd        %[r6],          %[r7]                         \n\t"
#endif
        "addiu       %[in],          %[in],          8             \n\t"
        "bnez        %[loop_size],   1b                            \n\t"
        " addiu      %[in1],         %[in1],         8             \n\t"
       "2:                                                         \n\t"
        "beqz        %[count],       4f                            \n\t"
        " mflo       %[r0]                                         \n\t"
        // Process remaining samples (if any).
       "3:                                                         \n\t"
        "lh          %[r0],          0(%[in])                      \n\t"
        "lh          %[r1],          0(%[in1])                     \n\t"
        "addiu       %[count],       %[count],       -1            \n\t"
        "addiu       %[in],          %[in],          2             \n\t"
        "madd        %[r0],          %[r1]                         \n\t"
        "bnez        %[count],       3b                            \n\t"
        " addiu      %[in1],         %[in1],         2             \n\t"
        "mflo        %[r0]                                         \n\t"
       "4:                                                         \n\t"
        ".set        pop                                           \n\t"
        : [loop_size] "+r" (loop_size), [in] "+r" (in), [in1] "+r" (in1),
#if !defined(MIPS_DSP_R2_LE)
          [r4] "=&r" (r4), [r5] "=&r" (r5), [r6] "=&r" (r6), [r7] "=&r" (r7),
#endif
          [r0] "=&r" (r0), [r1] "=&r" (r1), [r2] "=&r" (r2), [r3] "=&r" (r3),
          [count] "+r" (count)
        :
        : "memory", "hi", "lo"
      );
      r[i] = r0;
    }
  } else if (scaling == 32) {
    // In this case, the result will be high part of the accumulator.
    for (i = 1; i < order + 1; i++) {
      in = (int16_t*)x;
      int16_t* in1 = (int16_t*)x + i;
      count = N - i;
      loop_size = (count) >> 2;
      __asm __volatile (
        ".set        push                                          \n\t"
        ".set        noreorder                                     \n\t"
        "mult        $0,             $0                            \n\t"
        "beqz        %[loop_size],   2f                            \n\t"
        " andi       %[count],       %[count],       0x3           \n\t"
        // Loop processing 4 pairs of samples per iteration.
       "1:                                                         \n\t"
#if defined(MIPS_DSP_R2_LE)
        "ulw         %[r0],          0(%[in])                      \n\t"
        "ulw         %[r1],          0(%[in1])                     \n\t"
        "ulw         %[r2],          4(%[in])                      \n\t"
        "ulw         %[r3],          4(%[in1])                     \n\t"
#else
        "lh          %[r0],          0(%[in])                      \n\t"
        "lh          %[r1],          0(%[in1])                     \n\t"
        "lh          %[r2],          2(%[in])                      \n\t"
        "lh          %[r3],          2(%[in1])                     \n\t"
        "lh          %[r4],          4(%[in])                      \n\t"
        "lh          %[r5],          4(%[in1])                     \n\t"
        "lh          %[r6],          6(%[in])                      \n\t"
        "lh          %[r7],          6(%[in1])                     \n\t"
#endif
        "addiu       %[loop_size],   %[loop_size],   -1            \n\t"
#if defined(MIPS_DSP_R2_LE)
        "dpa.w.ph    $ac0,           %[r0],          %[r1]         \n\t"
        "dpa.w.ph    $ac0,           %[r2],          %[r3]         \n\t"
#else
        "madd        %[r0],          %[r1]                         \n\t"
        "madd        %[r2],          %[r3]                         \n\t"
        "madd        %[r4],          %[r5]                         \n\t"
        "madd        %[r6],          %[r7]                         \n\t"
#endif
        "addiu       %[in],          %[in],          8             \n\t"
        "bnez        %[loop_size],   1b                            \n\t"
        " addiu      %[in1],         %[in1],         8             \n\t"
       "2:                                                         \n\t"
        "beqz        %[count],       4f                            \n\t"
        " mfhi       %[r0]                                         \n\t"
        // Process remaining samples (if any).
       "3:                                                         \n\t"
        "lh          %[r0],          0(%[in])                      \n\t"
        "lh          %[r1],          0(%[in1])                     \n\t"
        "addiu       %[count],       %[count],       -1            \n\t"
        "addiu       %[in],          %[in],          2             \n\t"
        "madd        %[r0],          %[r1]                         \n\t"
        "bnez        %[count],       3b                            \n\t"
        " addiu      %[in1],         %[in1],         2             \n\t"
        "mfhi        %[r0]                                         \n\t"
       "4:                                                         \n\t"
        ".set        pop                                           \n\t"
        : [loop_size] "+r" (loop_size), [in] "+r" (in), [in1] "+r" (in1),
#if !defined(MIPS_DSP_R2_LE)
          [r4] "=&r" (r4), [r5] "=&r" (r5), [r6] "=&r" (r6), [r7] "=&r" (r7),
#endif
          [r0] "=&r" (r0), [r1] "=&r" (r1), [r2] "=&r" (r2), [r3] "=&r" (r3),
          [count] "+r" (count)
        :
        : "memory", "hi", "lo"
      );
      r[i] = r0;
    }
  } else {
    // In this case, the result is obtained by combining low and high parts
    // of the accumulator.
#if !defined(MIPS_DSP_R1_LE)
    int32_t tmp_shift = 32 - scaling;
#endif
    for (i = 1; i < order + 1; i++) {
      in = (int16_t*)x;
      int16_t* in1 = (int16_t*)x + i;
      count = N - i;
      loop_size = (count) >> 2;
      __asm __volatile (
        ".set        push                                          \n\t"
        ".set        noreorder                                     \n\t"
        "mult        $0,             $0                            \n\t"
        "beqz        %[loop_size],   2f                            \n\t"
        " andi       %[count],       %[count],       0x3           \n\t"
       "1:                                                         \n\t"
#if defined(MIPS_DSP_R2_LE)
        "ulw         %[r0],          0(%[in])                      \n\t"
        "ulw         %[r1],          0(%[in1])                     \n\t"
        "ulw         %[r2],          4(%[in])                      \n\t"
        "ulw         %[r3],          4(%[in1])                     \n\t"
#else
        "lh          %[r0],          0(%[in])                      \n\t"
        "lh          %[r1],          0(%[in1])                     \n\t"
        "lh          %[r2],          2(%[in])                      \n\t"
        "lh          %[r3],          2(%[in1])                     \n\t"
        "lh          %[r4],          4(%[in])                      \n\t"
        "lh          %[r5],          4(%[in1])                     \n\t"
        "lh          %[r6],          6(%[in])                      \n\t"
        "lh          %[r7],          6(%[in1])                     \n\t"
#endif
        "addiu       %[loop_size],   %[loop_size],   -1            \n\t"
#if defined(MIPS_DSP_R2_LE)
        "dpa.w.ph    $ac0,           %[r0],          %[r1]         \n\t"
        "dpa.w.ph    $ac0,           %[r2],          %[r3]         \n\t"
#else
        "madd        %[r0],          %[r1]                         \n\t"
        "madd        %[r2],          %[r3]                         \n\t"
        "madd        %[r4],          %[r5]                         \n\t"
        "madd        %[r6],          %[r7]                         \n\t"
#endif
        "addiu       %[in],          %[in],          8             \n\t"
        "bnez        %[loop_size],   1b                            \n\t"
        " addiu      %[in1],         %[in1],         8             \n\t"
       "2:                                                         \n\t"
        "beqz        %[count],       4f                            \n\t"
#if defined(MIPS_DSP_R1_LE)
        " extrv.w    %[r0],          $ac0,           %[scaling]    \n\t"
#else
        " mfhi       %[r0]                                         \n\t"
#endif
       "3:                                                         \n\t"
        "lh          %[r0],          0(%[in])                      \n\t"
        "lh          %[r1],          0(%[in1])                     \n\t"
        "addiu       %[count],       %[count],       -1            \n\t"
        "addiu       %[in],          %[in],          2             \n\t"
        "madd        %[r0],          %[r1]                         \n\t"
        "bnez        %[count],       3b                            \n\t"
        " addiu      %[in1],         %[in1],         2             \n\t"
#if defined(MIPS_DSP_R1_LE)
        "extrv.w     %[r0],          $ac0,           %[scaling]    \n\t"
#else
        "mfhi        %[r0]                                         \n\t"
#endif
       "4:                                                         \n\t"
#if !defined(MIPS_DSP_R1_LE)
        "mflo        %[r1]                                         \n\t"
        "sllv        %[r0],          %[r0],          %[tmp_shift]  \n\t"
        "srlv        %[r1],          %[r1],          %[scaling]    \n\t"
        "addu        %[r0],          %[r0],          %[r1]         \n\t"
#endif
        ".set        pop                                           \n\t"
        : [loop_size] "+r" (loop_size), [in] "+r" (in), [in1] "+r" (in1),
#if !defined(MIPS_DSP_R2_LE)
          [r4] "=&r" (r4), [r5] "=&r" (r5), [r6] "=&r" (r6), [r7] "=&r" (r7),
#endif
          [r0] "=&r" (r0), [r1] "=&r" (r1), [r2] "=&r" (r2), [r3] "=&r" (r3),
          [count] "+r" (count)
        : [scaling] "r" (scaling)
#if !defined(MIPS_DSP_R1_LE)
        , [tmp_shift] "r" (tmp_shift)
#endif
        : "memory", "hi", "lo"
      );
      r[i] = r0;
    }
  }
  *scale = scaling;

  return (order + 1);
}
