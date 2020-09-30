/*
 *  Copyright (c) 2014 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "modules/audio_coding/codecs/isac/fix/source/pitch_estimator.h"

void WebRtcIsacfix_PitchFilterCore(int loopNumber,
                                   int16_t gain,
                                   size_t index,
                                   int16_t sign,
                                   int16_t* inputState,
                                   int16_t* outputBuf2,
                                   const int16_t* coefficient,
                                   int16_t* inputBuf,
                                   int16_t* outputBuf,
                                   int* index2) {
  int ind2t = *index2;
  int i = 0;
  int16_t* out2_pos2 = &outputBuf2[PITCH_BUFFSIZE - (index + 2)] + ind2t;
  int32_t w1, w2, w3, w4, w5, gain32, sign32;
  int32_t coef1, coef2, coef3, coef4, coef5 = 0;
  // Define damp factors as int32_t (pair of int16_t)
  int32_t kDampF0 = 0x0000F70A;
  int32_t kDampF1 = 0x51EC2000;
  int32_t kDampF2 = 0xF70A2000;
  int16_t* input1 = inputBuf + ind2t;
  int16_t* output1 = outputBuf + ind2t;
  int16_t* output2 = outputBuf2 + ind2t + PITCH_BUFFSIZE;

  // Load coefficients outside the loop and sign-extend gain and sign
  __asm __volatile (
    ".set     push                                        \n\t"
    ".set     noreorder                                   \n\t"
    "lwl      %[coef1],       3(%[coefficient])           \n\t"
    "lwl      %[coef2],       7(%[coefficient])           \n\t"
    "lwl      %[coef3],       11(%[coefficient])          \n\t"
    "lwl      %[coef4],       15(%[coefficient])          \n\t"
    "lwr      %[coef1],       0(%[coefficient])           \n\t"
    "lwr      %[coef2],       4(%[coefficient])           \n\t"
    "lwr      %[coef3],       8(%[coefficient])           \n\t"
    "lwr      %[coef4],       12(%[coefficient])          \n\t"
    "lhu      %[coef5],       16(%[coefficient])          \n\t"
    "seh      %[gain32],      %[gain]                     \n\t"
    "seh      %[sign32],      %[sign]                     \n\t"
    ".set     pop                                         \n\t"
    : [coef1] "=&r" (coef1), [coef2] "=&r" (coef2), [coef3] "=&r" (coef3),
      [coef4] "=&r" (coef4), [coef5] "=&r" (coef5), [gain32] "=&r" (gain32),
      [sign32] "=&r" (sign32)
    : [coefficient] "r" (coefficient), [gain] "r" (gain),
      [sign] "r" (sign)
    : "memory"
  );

  for (i = 0; i < loopNumber; i++) {
    __asm __volatile (
      ".set       push                                            \n\t"
      ".set       noreorder                                       \n\t"
      // Filter to get fractional pitch
      "li         %[w1],          8192                            \n\t"
      "mtlo       %[w1]                                           \n\t"
      "mthi       $0                                              \n\t"
      "lwl        %[w1],          3(%[out2_pos2])                 \n\t"
      "lwl        %[w2],          7(%[out2_pos2])                 \n\t"
      "lwl        %[w3],          11(%[out2_pos2])                \n\t"
      "lwl        %[w4],          15(%[out2_pos2])                \n\t"
      "lwr        %[w1],          0(%[out2_pos2])                 \n\t"
      "lwr        %[w2],          4(%[out2_pos2])                 \n\t"
      "lwr        %[w3],          8(%[out2_pos2])                 \n\t"
      "lwr        %[w4],          12(%[out2_pos2])                \n\t"
      "lhu        %[w5],          16(%[out2_pos2])                \n\t"
      "dpa.w.ph   $ac0,           %[w1],              %[coef1]    \n\t"
      "dpa.w.ph   $ac0,           %[w2],              %[coef2]    \n\t"
      "dpa.w.ph   $ac0,           %[w3],              %[coef3]    \n\t"
      "dpa.w.ph   $ac0,           %[w4],              %[coef4]    \n\t"
      "dpa.w.ph   $ac0,           %[w5],              %[coef5]    \n\t"
      "addiu      %[out2_pos2],   %[out2_pos2],       2           \n\t"
      "mthi       $0,             $ac1                            \n\t"
      "lwl        %[w2],          3(%[inputState])                \n\t"
      "lwl        %[w3],          7(%[inputState])                \n\t"
      // Fractional pitch shift & saturation
      "extr_s.h   %[w1],          $ac0,               14          \n\t"
      "li         %[w4],          16384                           \n\t"
      "lwr        %[w2],          0(%[inputState])                \n\t"
      "lwr        %[w3],          4(%[inputState])                \n\t"
      "mtlo       %[w4],          $ac1                            \n\t"
      // Shift low pass filter state
      "swl        %[w2],          5(%[inputState])                \n\t"
      "swl        %[w3],          9(%[inputState])                \n\t"
      "mul        %[w1],          %[gain32],          %[w1]       \n\t"
      "swr        %[w2],          2(%[inputState])                \n\t"
      "swr        %[w3],          6(%[inputState])                \n\t"
      // Low pass filter accumulation
      "dpa.w.ph   $ac1,           %[kDampF1],         %[w2]       \n\t"
      "dpa.w.ph   $ac1,           %[kDampF2],         %[w3]       \n\t"
      "lh         %[w4],          0(%[input1])                    \n\t"
      "addiu      %[input1],      %[input1],          2           \n\t"
      "shra_r.w   %[w1],          %[w1],              12          \n\t"
      "sh         %[w1],          0(%[inputState])                \n\t"
      "dpa.w.ph   $ac1,           %[kDampF0],         %[w1]       \n\t"
      // Low pass filter shift & saturation
      "extr_s.h   %[w2],          $ac1,               15          \n\t"
      "mul        %[w2],          %[w2],              %[sign32]   \n\t"
      // Buffer update
      "subu       %[w2],          %[w4],              %[w2]       \n\t"
      "shll_s.w   %[w2],          %[w2],              16          \n\t"
      "sra        %[w2],          %[w2],              16          \n\t"
      "sh         %[w2],          0(%[output1])                   \n\t"
      "addu       %[w2],          %[w2],              %[w4]       \n\t"
      "shll_s.w   %[w2],          %[w2],              16          \n\t"
      "addiu      %[output1],     %[output1],         2           \n\t"
      "sra        %[w2],          %[w2],              16          \n\t"
      "sh         %[w2],          0(%[output2])                   \n\t"
      "addiu      %[output2],     %[output2],         2           \n\t"
      ".set       pop                                             \n\t"
      : [w1] "=&r" (w1), [w2] "=&r" (w2), [w3] "=&r" (w3), [w4] "=&r" (w4),
        [w5] "=&r" (w5), [input1] "+r" (input1), [out2_pos2] "+r" (out2_pos2),
        [output1] "+r" (output1), [output2] "+r" (output2)
      : [coefficient] "r" (coefficient), [inputState] "r" (inputState),
        [gain32] "r" (gain32), [sign32] "r" (sign32), [kDampF0] "r" (kDampF0),
        [kDampF1] "r" (kDampF1), [kDampF2] "r" (kDampF2),
        [coef1] "r" (coef1), [coef2] "r" (coef2), [coef3] "r" (coef3),
        [coef4] "r" (coef4), [coef5] "r" (coef5)
      : "hi", "lo", "$ac1hi", "$ac1lo", "memory"
    );
  }
  (*index2) += loopNumber;
}
