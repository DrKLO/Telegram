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

#include "./vpx_config.h"
#include "./vpx_dsp_rtcd.h"
#include "vpx_dsp/txfm_common.h"
#include "vpx_dsp/vpx_dsp_common.h"
#include "vpx_dsp/arm/idct_neon.h"
#include "vpx_dsp/arm/mem_neon.h"
#include "vpx_dsp/arm/transpose_neon.h"

void vpx_fdct4x4_neon(const int16_t *input, tran_low_t *final_output,
                      int stride) {
  int i;
  // input[M * stride] * 16
  int16x4_t input_0 = vshl_n_s16(vld1_s16(input + 0 * stride), 4);
  int16x4_t input_1 = vshl_n_s16(vld1_s16(input + 1 * stride), 4);
  int16x4_t input_2 = vshl_n_s16(vld1_s16(input + 2 * stride), 4);
  int16x4_t input_3 = vshl_n_s16(vld1_s16(input + 3 * stride), 4);

  // If the very first value != 0, then add 1.
  if (input[0] != 0) {
    const int16x4_t one = vreinterpret_s16_s64(vdup_n_s64(1));
    input_0 = vadd_s16(input_0, one);
  }

  for (i = 0; i < 2; ++i) {
    const int16x8_t input_01 = vcombine_s16(input_0, input_1);
    const int16x8_t input_32 = vcombine_s16(input_3, input_2);

    // in_0 +/- in_3, in_1 +/- in_2
    const int16x8_t s_01 = vaddq_s16(input_01, input_32);
    const int16x8_t s_32 = vsubq_s16(input_01, input_32);

    // step_0 +/- step_1, step_2 +/- step_3
    const int16x4_t s_0 = vget_low_s16(s_01);
    const int16x4_t s_1 = vget_high_s16(s_01);
    const int16x4_t s_2 = vget_high_s16(s_32);
    const int16x4_t s_3 = vget_low_s16(s_32);

    // (s_0 +/- s_1) * cospi_16_64
    // Must expand all elements to s32. See 'needs32' comment in fwd_txfm.c.
    const int32x4_t s_0_p_s_1 = vaddl_s16(s_0, s_1);
    const int32x4_t s_0_m_s_1 = vsubl_s16(s_0, s_1);
    const int32x4_t temp1 = vmulq_n_s32(s_0_p_s_1, cospi_16_64);
    const int32x4_t temp2 = vmulq_n_s32(s_0_m_s_1, cospi_16_64);

    // fdct_round_shift
    int16x4_t out_0 = vrshrn_n_s32(temp1, DCT_CONST_BITS);
    int16x4_t out_2 = vrshrn_n_s32(temp2, DCT_CONST_BITS);

    // s_3 * cospi_8_64 + s_2 * cospi_24_64
    // s_3 * cospi_24_64 - s_2 * cospi_8_64
    const int32x4_t s_3_cospi_8_64 = vmull_n_s16(s_3, cospi_8_64);
    const int32x4_t s_3_cospi_24_64 = vmull_n_s16(s_3, cospi_24_64);

    const int32x4_t temp3 = vmlal_n_s16(s_3_cospi_8_64, s_2, cospi_24_64);
    const int32x4_t temp4 = vmlsl_n_s16(s_3_cospi_24_64, s_2, cospi_8_64);

    // fdct_round_shift
    int16x4_t out_1 = vrshrn_n_s32(temp3, DCT_CONST_BITS);
    int16x4_t out_3 = vrshrn_n_s32(temp4, DCT_CONST_BITS);

    transpose_s16_4x4d(&out_0, &out_1, &out_2, &out_3);

    input_0 = out_0;
    input_1 = out_1;
    input_2 = out_2;
    input_3 = out_3;
  }

  {
    // Not quite a rounding shift. Only add 1 despite shifting by 2.
    const int16x8_t one = vdupq_n_s16(1);
    int16x8_t out_01 = vcombine_s16(input_0, input_1);
    int16x8_t out_23 = vcombine_s16(input_2, input_3);
    out_01 = vshrq_n_s16(vaddq_s16(out_01, one), 2);
    out_23 = vshrq_n_s16(vaddq_s16(out_23, one), 2);
    store_s16q_to_tran_low(final_output + 0 * 8, out_01);
    store_s16q_to_tran_low(final_output + 1 * 8, out_23);
  }
}
