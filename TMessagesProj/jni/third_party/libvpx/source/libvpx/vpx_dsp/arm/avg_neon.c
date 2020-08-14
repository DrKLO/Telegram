/*
 *  Copyright (c) 2015 The WebM project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include <arm_neon.h>
#include <assert.h>

#include "./vpx_dsp_rtcd.h"
#include "./vpx_config.h"

#include "vpx/vpx_integer.h"
#include "vpx_dsp/arm/idct_neon.h"
#include "vpx_dsp/arm/mem_neon.h"
#include "vpx_dsp/arm/sum_neon.h"

uint32_t vpx_avg_4x4_neon(const uint8_t *a, int a_stride) {
  const uint8x16_t b = load_unaligned_u8q(a, a_stride);
  const uint16x8_t c = vaddl_u8(vget_low_u8(b), vget_high_u8(b));
  const uint32x2_t d = horizontal_add_uint16x8(c);
  return vget_lane_u32(vrshr_n_u32(d, 4), 0);
}

uint32_t vpx_avg_8x8_neon(const uint8_t *a, int a_stride) {
  int i;
  uint8x8_t b, c;
  uint16x8_t sum;
  uint32x2_t d;
  b = vld1_u8(a);
  a += a_stride;
  c = vld1_u8(a);
  a += a_stride;
  sum = vaddl_u8(b, c);

  for (i = 0; i < 6; ++i) {
    const uint8x8_t d = vld1_u8(a);
    a += a_stride;
    sum = vaddw_u8(sum, d);
  }

  d = horizontal_add_uint16x8(sum);

  return vget_lane_u32(vrshr_n_u32(d, 6), 0);
}

// coeff: 16 bits, dynamic range [-32640, 32640].
// length: value range {16, 64, 256, 1024}.
int vpx_satd_neon(const tran_low_t *coeff, int length) {
  const int16x4_t zero = vdup_n_s16(0);
  int32x4_t accum = vdupq_n_s32(0);

  do {
    const int16x8_t src0 = load_tran_low_to_s16q(coeff);
    const int16x8_t src8 = load_tran_low_to_s16q(coeff + 8);
    accum = vabal_s16(accum, vget_low_s16(src0), zero);
    accum = vabal_s16(accum, vget_high_s16(src0), zero);
    accum = vabal_s16(accum, vget_low_s16(src8), zero);
    accum = vabal_s16(accum, vget_high_s16(src8), zero);
    length -= 16;
    coeff += 16;
  } while (length != 0);

  {
    // satd: 26 bits, dynamic range [-32640 * 1024, 32640 * 1024]
    const int64x2_t s0 = vpaddlq_s32(accum);  // cascading summation of 'accum'.
    const int32x2_t s1 = vadd_s32(vreinterpret_s32_s64(vget_low_s64(s0)),
                                  vreinterpret_s32_s64(vget_high_s64(s0)));
    const int satd = vget_lane_s32(s1, 0);
    return satd;
  }
}

void vpx_int_pro_row_neon(int16_t hbuf[16], uint8_t const *ref,
                          const int ref_stride, const int height) {
  int i;
  uint16x8_t vec_sum_lo = vdupq_n_u16(0);
  uint16x8_t vec_sum_hi = vdupq_n_u16(0);
  const int shift_factor = ((height >> 5) + 3) * -1;
  const int16x8_t vec_shift = vdupq_n_s16(shift_factor);

  for (i = 0; i < height; i += 8) {
    const uint8x16_t vec_row1 = vld1q_u8(ref);
    const uint8x16_t vec_row2 = vld1q_u8(ref + ref_stride);
    const uint8x16_t vec_row3 = vld1q_u8(ref + ref_stride * 2);
    const uint8x16_t vec_row4 = vld1q_u8(ref + ref_stride * 3);
    const uint8x16_t vec_row5 = vld1q_u8(ref + ref_stride * 4);
    const uint8x16_t vec_row6 = vld1q_u8(ref + ref_stride * 5);
    const uint8x16_t vec_row7 = vld1q_u8(ref + ref_stride * 6);
    const uint8x16_t vec_row8 = vld1q_u8(ref + ref_stride * 7);

    vec_sum_lo = vaddw_u8(vec_sum_lo, vget_low_u8(vec_row1));
    vec_sum_hi = vaddw_u8(vec_sum_hi, vget_high_u8(vec_row1));

    vec_sum_lo = vaddw_u8(vec_sum_lo, vget_low_u8(vec_row2));
    vec_sum_hi = vaddw_u8(vec_sum_hi, vget_high_u8(vec_row2));

    vec_sum_lo = vaddw_u8(vec_sum_lo, vget_low_u8(vec_row3));
    vec_sum_hi = vaddw_u8(vec_sum_hi, vget_high_u8(vec_row3));

    vec_sum_lo = vaddw_u8(vec_sum_lo, vget_low_u8(vec_row4));
    vec_sum_hi = vaddw_u8(vec_sum_hi, vget_high_u8(vec_row4));

    vec_sum_lo = vaddw_u8(vec_sum_lo, vget_low_u8(vec_row5));
    vec_sum_hi = vaddw_u8(vec_sum_hi, vget_high_u8(vec_row5));

    vec_sum_lo = vaddw_u8(vec_sum_lo, vget_low_u8(vec_row6));
    vec_sum_hi = vaddw_u8(vec_sum_hi, vget_high_u8(vec_row6));

    vec_sum_lo = vaddw_u8(vec_sum_lo, vget_low_u8(vec_row7));
    vec_sum_hi = vaddw_u8(vec_sum_hi, vget_high_u8(vec_row7));

    vec_sum_lo = vaddw_u8(vec_sum_lo, vget_low_u8(vec_row8));
    vec_sum_hi = vaddw_u8(vec_sum_hi, vget_high_u8(vec_row8));

    ref += ref_stride * 8;
  }

  vec_sum_lo = vshlq_u16(vec_sum_lo, vec_shift);
  vec_sum_hi = vshlq_u16(vec_sum_hi, vec_shift);

  vst1q_s16(hbuf, vreinterpretq_s16_u16(vec_sum_lo));
  hbuf += 8;
  vst1q_s16(hbuf, vreinterpretq_s16_u16(vec_sum_hi));
}

int16_t vpx_int_pro_col_neon(uint8_t const *ref, const int width) {
  int i;
  uint16x8_t vec_sum = vdupq_n_u16(0);

  for (i = 0; i < width; i += 16) {
    const uint8x16_t vec_row = vld1q_u8(ref);
    vec_sum = vaddw_u8(vec_sum, vget_low_u8(vec_row));
    vec_sum = vaddw_u8(vec_sum, vget_high_u8(vec_row));
    ref += 16;
  }

  return vget_lane_s16(vreinterpret_s16_u32(horizontal_add_uint16x8(vec_sum)),
                       0);
}

// ref, src = [0, 510] - max diff = 16-bits
// bwl = {2, 3, 4}, width = {16, 32, 64}
int vpx_vector_var_neon(int16_t const *ref, int16_t const *src, const int bwl) {
  int width = 4 << bwl;
  int32x4_t sse = vdupq_n_s32(0);
  int16x8_t total = vdupq_n_s16(0);

  assert(width >= 8);
  assert((width % 8) == 0);

  do {
    const int16x8_t r = vld1q_s16(ref);
    const int16x8_t s = vld1q_s16(src);
    const int16x8_t diff = vsubq_s16(r, s);  // [-510, 510], 10 bits.
    const int16x4_t diff_lo = vget_low_s16(diff);
    const int16x4_t diff_hi = vget_high_s16(diff);
    sse = vmlal_s16(sse, diff_lo, diff_lo);  // dynamic range 26 bits.
    sse = vmlal_s16(sse, diff_hi, diff_hi);
    total = vaddq_s16(total, diff);  // dynamic range 16 bits.

    ref += 8;
    src += 8;
    width -= 8;
  } while (width != 0);

  {
    // Note: 'total''s pairwise addition could be implemented similarly to
    // horizontal_add_uint16x8(), but one less vpaddl with 'total' when paired
    // with the summation of 'sse' performed better on a Cortex-A15.
    const int32x4_t t0 = vpaddlq_s16(total);  // cascading summation of 'total'
    const int32x2_t t1 = vadd_s32(vget_low_s32(t0), vget_high_s32(t0));
    const int32x2_t t2 = vpadd_s32(t1, t1);
    const int t = vget_lane_s32(t2, 0);
    const int64x2_t s0 = vpaddlq_s32(sse);  // cascading summation of 'sse'.
    const int32x2_t s1 = vadd_s32(vreinterpret_s32_s64(vget_low_s64(s0)),
                                  vreinterpret_s32_s64(vget_high_s64(s0)));
    const int s = vget_lane_s32(s1, 0);
    const int shift_factor = bwl + 2;
    return s - ((t * t) >> shift_factor);
  }
}

void vpx_minmax_8x8_neon(const uint8_t *a, int a_stride, const uint8_t *b,
                         int b_stride, int *min, int *max) {
  // Load and concatenate.
  const uint8x16_t a01 = vcombine_u8(vld1_u8(a), vld1_u8(a + a_stride));
  const uint8x16_t a23 =
      vcombine_u8(vld1_u8(a + 2 * a_stride), vld1_u8(a + 3 * a_stride));
  const uint8x16_t a45 =
      vcombine_u8(vld1_u8(a + 4 * a_stride), vld1_u8(a + 5 * a_stride));
  const uint8x16_t a67 =
      vcombine_u8(vld1_u8(a + 6 * a_stride), vld1_u8(a + 7 * a_stride));

  const uint8x16_t b01 = vcombine_u8(vld1_u8(b), vld1_u8(b + b_stride));
  const uint8x16_t b23 =
      vcombine_u8(vld1_u8(b + 2 * b_stride), vld1_u8(b + 3 * b_stride));
  const uint8x16_t b45 =
      vcombine_u8(vld1_u8(b + 4 * b_stride), vld1_u8(b + 5 * b_stride));
  const uint8x16_t b67 =
      vcombine_u8(vld1_u8(b + 6 * b_stride), vld1_u8(b + 7 * b_stride));

  // Absolute difference.
  const uint8x16_t ab01_diff = vabdq_u8(a01, b01);
  const uint8x16_t ab23_diff = vabdq_u8(a23, b23);
  const uint8x16_t ab45_diff = vabdq_u8(a45, b45);
  const uint8x16_t ab67_diff = vabdq_u8(a67, b67);

  // Max values between the Q vectors.
  const uint8x16_t ab0123_max = vmaxq_u8(ab01_diff, ab23_diff);
  const uint8x16_t ab4567_max = vmaxq_u8(ab45_diff, ab67_diff);
  const uint8x16_t ab0123_min = vminq_u8(ab01_diff, ab23_diff);
  const uint8x16_t ab4567_min = vminq_u8(ab45_diff, ab67_diff);

  const uint8x16_t ab07_max = vmaxq_u8(ab0123_max, ab4567_max);
  const uint8x16_t ab07_min = vminq_u8(ab0123_min, ab4567_min);

  // Split to D and start doing pairwise.
  uint8x8_t ab_max = vmax_u8(vget_high_u8(ab07_max), vget_low_u8(ab07_max));
  uint8x8_t ab_min = vmin_u8(vget_high_u8(ab07_min), vget_low_u8(ab07_min));

  // Enough runs of vpmax/min propogate the max/min values to every position.
  ab_max = vpmax_u8(ab_max, ab_max);
  ab_min = vpmin_u8(ab_min, ab_min);

  ab_max = vpmax_u8(ab_max, ab_max);
  ab_min = vpmin_u8(ab_min, ab_min);

  ab_max = vpmax_u8(ab_max, ab_max);
  ab_min = vpmin_u8(ab_min, ab_min);

  *min = *max = 0;  // Clear high bits
  // Store directly to avoid costly neon->gpr transfer.
  vst1_lane_u8((uint8_t *)max, ab_max, 0);
  vst1_lane_u8((uint8_t *)min, ab_min, 0);
}
