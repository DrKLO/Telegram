/*
 *  Copyright (c) 2017 The WebM project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include <arm_neon.h>

#include "./vpx_dsp_rtcd.h"
#include "./vpx_config.h"
#include "vpx_dsp/arm/mem_neon.h"
#include "vpx_dsp/arm/sum_neon.h"

static INLINE tran_low_t get_lane(const int32x2_t a) {
#if CONFIG_VP9_HIGHBITDEPTH
  return vget_lane_s32(a, 0);
#else
  return vget_lane_s16(vreinterpret_s16_s32(a), 0);
#endif  // CONFIG_VP9_HIGHBITDETPH
}

void vpx_fdct4x4_1_neon(const int16_t *input, tran_low_t *output, int stride) {
  int16x4_t a0, a1, a2, a3;
  int16x8_t b0, b1;
  int16x8_t c;
  int32x2_t d;

  a0 = vld1_s16(input);
  input += stride;
  a1 = vld1_s16(input);
  input += stride;
  a2 = vld1_s16(input);
  input += stride;
  a3 = vld1_s16(input);

  b0 = vcombine_s16(a0, a1);
  b1 = vcombine_s16(a2, a3);

  c = vaddq_s16(b0, b1);

  d = horizontal_add_int16x8(c);

  output[0] = get_lane(vshl_n_s32(d, 1));
  output[1] = 0;
}

void vpx_fdct8x8_1_neon(const int16_t *input, tran_low_t *output, int stride) {
  int r;
  int16x8_t sum = vld1q_s16(&input[0]);

  for (r = 1; r < 8; ++r) {
    const int16x8_t input_00 = vld1q_s16(&input[r * stride]);
    sum = vaddq_s16(sum, input_00);
  }

  output[0] = get_lane(horizontal_add_int16x8(sum));
  output[1] = 0;
}

void vpx_fdct16x16_1_neon(const int16_t *input, tran_low_t *output,
                          int stride) {
  int r;
  int16x8_t left = vld1q_s16(input);
  int16x8_t right = vld1q_s16(input + 8);
  int32x2_t sum;
  input += stride;

  for (r = 1; r < 16; ++r) {
    const int16x8_t a = vld1q_s16(input);
    const int16x8_t b = vld1q_s16(input + 8);
    input += stride;
    left = vaddq_s16(left, a);
    right = vaddq_s16(right, b);
  }

  sum = vadd_s32(horizontal_add_int16x8(left), horizontal_add_int16x8(right));

  output[0] = get_lane(vshr_n_s32(sum, 1));
  output[1] = 0;
}

void vpx_fdct32x32_1_neon(const int16_t *input, tran_low_t *output,
                          int stride) {
  int r;
  int16x8_t a0 = vld1q_s16(input);
  int16x8_t a1 = vld1q_s16(input + 8);
  int16x8_t a2 = vld1q_s16(input + 16);
  int16x8_t a3 = vld1q_s16(input + 24);
  int32x2_t sum;
  input += stride;

  for (r = 1; r < 32; ++r) {
    const int16x8_t b0 = vld1q_s16(input);
    const int16x8_t b1 = vld1q_s16(input + 8);
    const int16x8_t b2 = vld1q_s16(input + 16);
    const int16x8_t b3 = vld1q_s16(input + 24);
    input += stride;
    a0 = vaddq_s16(a0, b0);
    a1 = vaddq_s16(a1, b1);
    a2 = vaddq_s16(a2, b2);
    a3 = vaddq_s16(a3, b3);
  }

  sum = vadd_s32(horizontal_add_int16x8(a0), horizontal_add_int16x8(a1));
  sum = vadd_s32(sum, horizontal_add_int16x8(a2));
  sum = vadd_s32(sum, horizontal_add_int16x8(a3));
  output[0] = get_lane(vshr_n_s32(sum, 3));
  output[1] = 0;
}
