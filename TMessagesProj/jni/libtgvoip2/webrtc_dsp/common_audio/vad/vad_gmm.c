/*
 *  Copyright (c) 2011 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "common_audio/vad/vad_gmm.h"

#include "common_audio/signal_processing/include/signal_processing_library.h"

static const int32_t kCompVar = 22005;
static const int16_t kLog2Exp = 5909;  // log2(exp(1)) in Q12.

// For a normal distribution, the probability of |input| is calculated and
// returned (in Q20). The formula for normal distributed probability is
//
// 1 / s * exp(-(x - m)^2 / (2 * s^2))
//
// where the parameters are given in the following Q domains:
// m = |mean| (Q7)
// s = |std| (Q7)
// x = |input| (Q4)
// in addition to the probability we output |delta| (in Q11) used when updating
// the noise/speech model.
int32_t WebRtcVad_GaussianProbability(int16_t input,
                                      int16_t mean,
                                      int16_t std,
                                      int16_t* delta) {
  int16_t tmp16, inv_std, inv_std2, exp_value = 0;
  int32_t tmp32;

  // Calculate |inv_std| = 1 / s, in Q10.
  // 131072 = 1 in Q17, and (|std| >> 1) is for rounding instead of truncation.
  // Q-domain: Q17 / Q7 = Q10.
  tmp32 = (int32_t) 131072 + (int32_t) (std >> 1);
  inv_std = (int16_t) WebRtcSpl_DivW32W16(tmp32, std);

  // Calculate |inv_std2| = 1 / s^2, in Q14.
  tmp16 = (inv_std >> 2);  // Q10 -> Q8.
  // Q-domain: (Q8 * Q8) >> 2 = Q14.
  inv_std2 = (int16_t)((tmp16 * tmp16) >> 2);
  // TODO(bjornv): Investigate if changing to
  // inv_std2 = (int16_t)((inv_std * inv_std) >> 6);
  // gives better accuracy.

  tmp16 = (input << 3);  // Q4 -> Q7
  tmp16 = tmp16 - mean;  // Q7 - Q7 = Q7

  // To be used later, when updating noise/speech model.
  // |delta| = (x - m) / s^2, in Q11.
  // Q-domain: (Q14 * Q7) >> 10 = Q11.
  *delta = (int16_t)((inv_std2 * tmp16) >> 10);

  // Calculate the exponent |tmp32| = (x - m)^2 / (2 * s^2), in Q10. Replacing
  // division by two with one shift.
  // Q-domain: (Q11 * Q7) >> 8 = Q10.
  tmp32 = (*delta * tmp16) >> 9;

  // If the exponent is small enough to give a non-zero probability we calculate
  // |exp_value| ~= exp(-(x - m)^2 / (2 * s^2))
  //             ~= exp2(-log2(exp(1)) * |tmp32|).
  if (tmp32 < kCompVar) {
    // Calculate |tmp16| = log2(exp(1)) * |tmp32|, in Q10.
    // Q-domain: (Q12 * Q10) >> 12 = Q10.
    tmp16 = (int16_t)((kLog2Exp * tmp32) >> 12);
    tmp16 = -tmp16;
    exp_value = (0x0400 | (tmp16 & 0x03FF));
    tmp16 ^= 0xFFFF;
    tmp16 >>= 10;
    tmp16 += 1;
    // Get |exp_value| = exp(-|tmp32|) in Q10.
    exp_value >>= tmp16;
  }

  // Calculate and return (1 / s) * exp(-(x - m)^2 / (2 * s^2)), in Q20.
  // Q-domain: Q10 * Q10 = Q20.
  return inv_std * exp_value;
}
