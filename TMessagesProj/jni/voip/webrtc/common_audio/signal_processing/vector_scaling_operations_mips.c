/*
 *  Copyright (c) 2013 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */


/*
 * This file contains implementations of the functions
 * WebRtcSpl_ScaleAndAddVectorsWithRound_mips()
 */

#include "common_audio/signal_processing/include/signal_processing_library.h"

int WebRtcSpl_ScaleAndAddVectorsWithRound_mips(const int16_t* in_vector1,
                                               int16_t in_vector1_scale,
                                               const int16_t* in_vector2,
                                               int16_t in_vector2_scale,
                                               int right_shifts,
                                               int16_t* out_vector,
                                               size_t length) {
  int16_t r0 = 0, r1 = 0;
  int16_t *in1 = (int16_t*)in_vector1;
  int16_t *in2 = (int16_t*)in_vector2;
  int16_t *out = out_vector;
  size_t i = 0;
  int value32 = 0;

  if (in_vector1 == NULL || in_vector2 == NULL || out_vector == NULL ||
      length == 0 || right_shifts < 0) {
    return -1;
  }
  for (i = 0; i < length; i++) {
    __asm __volatile (
      "lh         %[r0],          0(%[in1])                               \n\t"
      "lh         %[r1],          0(%[in2])                               \n\t"
      "mult       %[r0],          %[in_vector1_scale]                     \n\t"
      "madd       %[r1],          %[in_vector2_scale]                     \n\t"
      "extrv_r.w  %[value32],     $ac0,               %[right_shifts]     \n\t"
      "addiu      %[in1],         %[in1],             2                   \n\t"
      "addiu      %[in2],         %[in2],             2                   \n\t"
      "sh         %[value32],     0(%[out])                               \n\t"
      "addiu      %[out],         %[out],             2                   \n\t"
      : [value32] "=&r" (value32), [out] "+r" (out), [in1] "+r" (in1),
        [in2] "+r" (in2), [r0] "=&r" (r0), [r1] "=&r" (r1)
      : [in_vector1_scale] "r" (in_vector1_scale),
        [in_vector2_scale] "r" (in_vector2_scale),
        [right_shifts] "r" (right_shifts)
      : "hi", "lo", "memory"
    );
  }
  return 0;
}
