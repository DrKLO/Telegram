/*
 *  Copyright (c) 2014 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "modules/audio_coding/codecs/isac/fix/source/filterbank_internal.h"

// WebRtcIsacfix_AllpassFilter2FixDec16 function optimized for MIPSDSP platform.
// Bit-exact with WebRtcIsacfix_AllpassFilter2FixDec16C from filterbanks.c.
void WebRtcIsacfix_AllpassFilter2FixDec16MIPS(
    int16_t* data_ch1,            // Input and output in channel 1, in Q0.
    int16_t* data_ch2,            // Input and output in channel 2, in Q0.
    const int16_t* factor_ch1,    // Scaling factor for channel 1, in Q15.
    const int16_t* factor_ch2,    // Scaling factor for channel 2, in Q15.
    const int length,             // Length of the data buffers.
    int32_t* filter_state_ch1,    // Filter state for channel 1, in Q16.
    int32_t* filter_state_ch2) {  // Filter state for channel 2, in Q16.

  int32_t st0_ch1, st1_ch1;                // channel1 state variables.
  int32_t st0_ch2, st1_ch2;                // channel2 state variables.
  int32_t f_ch10, f_ch11, f_ch20, f_ch21;  // factor variables.
  int32_t r0, r1, r2, r3, r4, r5;          // temporary register variables.

  __asm __volatile (
    ".set           push                                                  \n\t"
    ".set           noreorder                                             \n\t"
    // Load all the state and factor variables.
    "lh             %[f_ch10],      0(%[factor_ch1])                      \n\t"
    "lh             %[f_ch20],      0(%[factor_ch2])                      \n\t"
    "lh             %[f_ch11],      2(%[factor_ch1])                      \n\t"
    "lh             %[f_ch21],      2(%[factor_ch2])                      \n\t"
    "lw             %[st0_ch1],     0(%[filter_state_ch1])                \n\t"
    "lw             %[st1_ch1],     4(%[filter_state_ch1])                \n\t"
    "lw             %[st0_ch2],     0(%[filter_state_ch2])                \n\t"
    "lw             %[st1_ch2],     4(%[filter_state_ch2])                \n\t"
    // Allpass filtering loop.
   "1:                                                                    \n\t"
    "lh             %[r0],          0(%[data_ch1])                        \n\t"
    "lh             %[r1],          0(%[data_ch2])                        \n\t"
    "addiu          %[length],      %[length],              -1            \n\t"
    "mul            %[r2],          %[r0],                  %[f_ch10]     \n\t"
    "mul            %[r3],          %[r1],                  %[f_ch20]     \n\t"
    "sll            %[r0],          %[r0],                  16            \n\t"
    "sll            %[r1],          %[r1],                  16            \n\t"
    "sll            %[r2],          %[r2],                  1             \n\t"
    "addq_s.w       %[r2],          %[r2],                  %[st0_ch1]    \n\t"
    "sll            %[r3],          %[r3],                  1             \n\t"
    "addq_s.w       %[r3],          %[r3],                  %[st0_ch2]    \n\t"
    "sra            %[r2],          %[r2],                  16            \n\t"
    "mul            %[st0_ch1],     %[f_ch10],              %[r2]         \n\t"
    "sra            %[r3],          %[r3],                  16            \n\t"
    "mul            %[st0_ch2],     %[f_ch20],              %[r3]         \n\t"
    "mul            %[r4],          %[r2],                  %[f_ch11]     \n\t"
    "mul            %[r5],          %[r3],                  %[f_ch21]     \n\t"
    "sll            %[st0_ch1],     %[st0_ch1],             1             \n\t"
    "subq_s.w       %[st0_ch1],     %[r0],                  %[st0_ch1]    \n\t"
    "sll            %[st0_ch2],     %[st0_ch2],             1             \n\t"
    "subq_s.w       %[st0_ch2],     %[r1],                  %[st0_ch2]    \n\t"
    "sll            %[r4],          %[r4],                  1             \n\t"
    "addq_s.w       %[r4],          %[r4],                  %[st1_ch1]    \n\t"
    "sll            %[r5],          %[r5],                  1             \n\t"
    "addq_s.w       %[r5],          %[r5],                  %[st1_ch2]    \n\t"
    "sra            %[r4],          %[r4],                  16            \n\t"
    "mul            %[r0],          %[r4],                  %[f_ch11]     \n\t"
    "sra            %[r5],          %[r5],                  16            \n\t"
    "mul            %[r1],          %[r5],                  %[f_ch21]     \n\t"
    "sh             %[r4],          0(%[data_ch1])                        \n\t"
    "sh             %[r5],          0(%[data_ch2])                        \n\t"
    "addiu          %[data_ch1],    %[data_ch1],            2             \n\t"
    "sll            %[r2],          %[r2],                  16            \n\t"
    "sll            %[r0],          %[r0],                  1             \n\t"
    "subq_s.w       %[st1_ch1],     %[r2],                  %[r0]         \n\t"
    "sll            %[r3],          %[r3],                  16            \n\t"
    "sll            %[r1],          %[r1],                  1             \n\t"
    "subq_s.w       %[st1_ch2],     %[r3],                  %[r1]         \n\t"
    "bgtz           %[length],      1b                                    \n\t"
    " addiu         %[data_ch2],    %[data_ch2],            2             \n\t"
    // Store channel states.
    "sw             %[st0_ch1],     0(%[filter_state_ch1])                \n\t"
    "sw             %[st1_ch1],     4(%[filter_state_ch1])                \n\t"
    "sw             %[st0_ch2],     0(%[filter_state_ch2])                \n\t"
    "sw             %[st1_ch2],     4(%[filter_state_ch2])                \n\t"
    ".set           pop                                                   \n\t"
    : [f_ch10] "=&r" (f_ch10), [f_ch20] "=&r" (f_ch20),
      [f_ch11] "=&r" (f_ch11), [f_ch21] "=&r" (f_ch21),
      [st0_ch1] "=&r" (st0_ch1), [st1_ch1] "=&r" (st1_ch1),
      [st0_ch2] "=&r" (st0_ch2), [st1_ch2] "=&r" (st1_ch2),
      [r0] "=&r" (r0), [r1] "=&r" (r1), [r2] "=&r" (r2),
      [r3] "=&r" (r3), [r4] "=&r" (r4), [r5] "=&r" (r5)
    : [factor_ch1] "r" (factor_ch1), [factor_ch2] "r" (factor_ch2),
      [filter_state_ch1] "r" (filter_state_ch1),
      [filter_state_ch2] "r" (filter_state_ch2),
      [data_ch1] "r" (data_ch1), [data_ch2] "r" (data_ch2),
      [length] "r" (length)
    : "memory", "hi", "lo"
  );
}

// WebRtcIsacfix_HighpassFilterFixDec32 function optimized for MIPSDSP platform.
// Bit-exact with WebRtcIsacfix_HighpassFilterFixDec32C from filterbanks.c.
void WebRtcIsacfix_HighpassFilterFixDec32MIPS(int16_t* io,
                                              int16_t len,
                                              const int16_t* coefficient,
                                              int32_t* state) {
  int k;
  int32_t a1, a2, b1, b2, in;
  int32_t state0 = state[0];
  int32_t state1 = state[1];

  int32_t c0, c1, c2, c3;
  int32_t c4, c5, c6, c7;
  int32_t state0_lo, state0_hi;
  int32_t state1_lo, state1_hi;
  int32_t t0, t1, t2, t3, t4, t5;

  __asm  __volatile (
    "lh         %[c0],         0(%[coeff_ptr])            \n\t"
    "lh         %[c1],         2(%[coeff_ptr])            \n\t"
    "lh         %[c2],         4(%[coeff_ptr])            \n\t"
    "lh         %[c3],         6(%[coeff_ptr])            \n\t"
    "sra        %[state0_hi],  %[state0],        16       \n\t"
    "sra        %[state1_hi],  %[state1],        16       \n\t"
    "andi       %[state0_lo],  %[state0],        0xFFFF   \n\t"
    "andi       %[state1_lo],  %[state1],        0xFFFF   \n\t"
    "lh         %[c4],         8(%[coeff_ptr])            \n\t"
    "lh         %[c5],         10(%[coeff_ptr])           \n\t"
    "lh         %[c6],         12(%[coeff_ptr])           \n\t"
    "lh         %[c7],         14(%[coeff_ptr])           \n\t"
    "sra        %[state0_lo],  %[state0_lo],     1        \n\t"
    "sra        %[state1_lo],  %[state1_lo],     1        \n\t"
    : [c0] "=&r" (c0), [c1] "=&r" (c1), [c2] "=&r" (c2), [c3] "=&r" (c3),
      [c4] "=&r" (c4), [c5] "=&r" (c5), [c6] "=&r" (c6), [c7] "=&r" (c7),
      [state0_hi] "=&r" (state0_hi), [state0_lo] "=&r" (state0_lo),
      [state1_hi] "=&r" (state1_hi), [state1_lo] "=&r" (state1_lo)
    : [coeff_ptr] "r" (coefficient), [state0] "r" (state0),
      [state1] "r" (state1)
    : "memory"
  );

  for (k = 0; k < len; k++) {
    in = (int32_t)io[k];

    __asm __volatile (
      ".set      push                                      \n\t"
      ".set      noreorder                                 \n\t"
      "mul       %[t2],        %[c4],        %[state0_lo]  \n\t"
      "mul       %[t0],        %[c5],        %[state0_lo]  \n\t"
      "mul       %[t1],        %[c4],        %[state0_hi]  \n\t"
      "mul       %[a1],        %[c5],        %[state0_hi]  \n\t"
      "mul       %[t5],        %[c6],        %[state1_lo]  \n\t"
      "mul       %[t3],        %[c7],        %[state1_lo]  \n\t"
      "mul       %[t4],        %[c6],        %[state1_hi]  \n\t"
      "mul       %[b1],        %[c7],        %[state1_hi]  \n\t"
      "shra_r.w  %[t2],        %[t2],        15            \n\t"
      "shra_r.w  %[t0],        %[t0],        15            \n\t"
      "addu      %[t1],        %[t1],        %[t2]         \n\t"
      "addu      %[a1],        %[a1],        %[t0]         \n\t"
      "sra       %[t1],        %[t1],        16            \n\t"
      "addu      %[a1],        %[a1],        %[t1]         \n\t"
      "shra_r.w  %[t5],        %[t5],        15            \n\t"
      "shra_r.w  %[t3],        %[t3],        15            \n\t"
      "addu      %[t4],        %[t4],        %[t5]         \n\t"
      "addu      %[b1],        %[b1],        %[t3]         \n\t"
      "sra       %[t4],        %[t4],        16            \n\t"
      "addu      %[b1],        %[b1],        %[t4]         \n\t"
      "mul       %[t2],        %[c0],        %[state0_lo]  \n\t"
      "mul       %[t0],        %[c1],        %[state0_lo]  \n\t"
      "mul       %[t1],        %[c0],        %[state0_hi]  \n\t"
      "mul       %[a2],        %[c1],        %[state0_hi]  \n\t"
      "mul       %[t5],        %[c2],        %[state1_lo]  \n\t"
      "mul       %[t3],        %[c3],        %[state1_lo]  \n\t"
      "mul       %[t4],        %[c2],        %[state1_hi]  \n\t"
      "mul       %[b2],        %[c3],        %[state1_hi]  \n\t"
      "shra_r.w  %[t2],        %[t2],        15            \n\t"
      "shra_r.w  %[t0],        %[t0],        15            \n\t"
      "addu      %[t1],        %[t1],        %[t2]         \n\t"
      "addu      %[a2],        %[a2],        %[t0]         \n\t"
      "sra       %[t1],        %[t1],        16            \n\t"
      "addu      %[a2],        %[a2],        %[t1]         \n\t"
      "shra_r.w  %[t5],        %[t5],        15            \n\t"
      "shra_r.w  %[t3],        %[t3],        15            \n\t"
      "addu      %[t4],        %[t4],        %[t5]         \n\t"
      "addu      %[b2],        %[b2],        %[t3]         \n\t"
      "sra       %[t4],        %[t4],        16            \n\t"
      "addu      %[b2],        %[b2],        %[t4]         \n\t"
      "addu      %[a1],        %[a1],        %[b1]         \n\t"
      "sra       %[a1],        %[a1],        7             \n\t"
      "addu      %[a1],        %[a1],        %[in]         \n\t"
      "sll       %[t0],        %[in],        2             \n\t"
      "addu      %[a2],        %[a2],        %[b2]         \n\t"
      "subu      %[t0],        %[t0],        %[a2]         \n\t"
      "shll_s.w  %[a1],        %[a1],        16            \n\t"
      "shll_s.w  %[t0],        %[t0],        2             \n\t"
      "sra       %[a1],        %[a1],        16            \n\t"
      "addu      %[state1_hi], %[state0_hi], $0            \n\t"
      "addu      %[state1_lo], %[state0_lo], $0            \n\t"
      "sra       %[state0_hi], %[t0],        16            \n\t"
      "andi      %[state0_lo], %[t0],        0xFFFF        \n\t"
      "sra       %[state0_lo], %[state0_lo], 1             \n\t"
      ".set      pop                                       \n\t"
      : [a1] "=&r" (a1), [b1] "=&r" (b1), [a2] "=&r" (a2), [b2] "=&r" (b2),
        [state0_hi] "+r" (state0_hi), [state0_lo] "+r" (state0_lo),
        [state1_hi] "+r" (state1_hi), [state1_lo] "+r" (state1_lo),
        [t0] "=&r" (t0), [t1] "=&r" (t1), [t2] "=&r" (t2),
        [t3] "=&r" (t3), [t4] "=&r" (t4), [t5] "=&r" (t5)
      : [c0] "r" (c0), [c1] "r" (c1), [c2] "r" (c2), [c3] "r" (c3),
        [c4] "r" (c4), [c5] "r" (c5), [c6] "r" (c6), [c7] "r" (c7),
        [in] "r" (in)
      : "hi", "lo"
    );
    io[k] = (int16_t)a1;
  }
  __asm __volatile (
    ".set            push                                            \n\t"
    ".set            noreorder                                       \n\t"
#if !defined(MIPS_DSP_R2_LE)
    "sll             %[state0_hi],   %[state0_hi],   16              \n\t"
    "sll             %[state0_lo],   %[state0_lo],   1               \n\t"
    "sll             %[state1_hi],   %[state1_hi],   16              \n\t"
    "sll             %[state1_lo],   %[state1_lo],   1               \n\t"
    "or              %[state0_hi],   %[state0_hi],   %[state0_lo]    \n\t"
    "or              %[state1_hi],   %[state1_hi],   %[state1_lo]    \n\t"
#else
    "sll             %[state0_lo],   %[state0_lo],   1               \n\t"
    "sll             %[state1_lo],   %[state1_lo],   1               \n\t"
    "precr_sra.ph.w  %[state0_hi],   %[state0_lo],   0               \n\t"
    "precr_sra.ph.w  %[state1_hi],   %[state1_lo],   0               \n\t"
#endif
    "sw              %[state0_hi],   0(%[state])                     \n\t"
    "sw              %[state1_hi],   4(%[state])                     \n\t"
    ".set            pop                                             \n\t"
    : [state0_hi] "+r" (state0_hi), [state0_lo] "+r" (state0_lo),
      [state1_hi] "+r" (state1_hi), [state1_lo] "+r" (state1_lo)
    : [state] "r" (state)
    : "memory"
  );
}
