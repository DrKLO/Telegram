/*
 *  Copyright (c) 2014 The WebM project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#ifndef VPX_VPX_DSP_ARM_VPX_CONVOLVE8_NEON_H_
#define VPX_VPX_DSP_ARM_VPX_CONVOLVE8_NEON_H_

#include <arm_neon.h>

#include "./vpx_config.h"
#include "./vpx_dsp_rtcd.h"

static INLINE void load_u8_8x4(const uint8_t *s, const ptrdiff_t p,
                               uint8x8_t *const s0, uint8x8_t *const s1,
                               uint8x8_t *const s2, uint8x8_t *const s3) {
  *s0 = vld1_u8(s);
  s += p;
  *s1 = vld1_u8(s);
  s += p;
  *s2 = vld1_u8(s);
  s += p;
  *s3 = vld1_u8(s);
}

static INLINE void load_u8_8x8(const uint8_t *s, const ptrdiff_t p,
                               uint8x8_t *const s0, uint8x8_t *const s1,
                               uint8x8_t *const s2, uint8x8_t *const s3,
                               uint8x8_t *const s4, uint8x8_t *const s5,
                               uint8x8_t *const s6, uint8x8_t *const s7) {
  *s0 = vld1_u8(s);
  s += p;
  *s1 = vld1_u8(s);
  s += p;
  *s2 = vld1_u8(s);
  s += p;
  *s3 = vld1_u8(s);
  s += p;
  *s4 = vld1_u8(s);
  s += p;
  *s5 = vld1_u8(s);
  s += p;
  *s6 = vld1_u8(s);
  s += p;
  *s7 = vld1_u8(s);
}

static INLINE void load_u8_16x8(const uint8_t *s, const ptrdiff_t p,
                                uint8x16_t *const s0, uint8x16_t *const s1,
                                uint8x16_t *const s2, uint8x16_t *const s3,
                                uint8x16_t *const s4, uint8x16_t *const s5,
                                uint8x16_t *const s6, uint8x16_t *const s7) {
  *s0 = vld1q_u8(s);
  s += p;
  *s1 = vld1q_u8(s);
  s += p;
  *s2 = vld1q_u8(s);
  s += p;
  *s3 = vld1q_u8(s);
  s += p;
  *s4 = vld1q_u8(s);
  s += p;
  *s5 = vld1q_u8(s);
  s += p;
  *s6 = vld1q_u8(s);
  s += p;
  *s7 = vld1q_u8(s);
}

static INLINE int16x4_t convolve8_4(const int16x4_t s0, const int16x4_t s1,
                                    const int16x4_t s2, const int16x4_t s3,
                                    const int16x4_t s4, const int16x4_t s5,
                                    const int16x4_t s6, const int16x4_t s7,
                                    const int16x8_t filters,
                                    const int16x4_t filter3,
                                    const int16x4_t filter4) {
  const int16x4_t filters_lo = vget_low_s16(filters);
  const int16x4_t filters_hi = vget_high_s16(filters);
  int16x4_t sum;

  sum = vmul_lane_s16(s0, filters_lo, 0);
  sum = vmla_lane_s16(sum, s1, filters_lo, 1);
  sum = vmla_lane_s16(sum, s2, filters_lo, 2);
  sum = vmla_lane_s16(sum, s5, filters_hi, 1);
  sum = vmla_lane_s16(sum, s6, filters_hi, 2);
  sum = vmla_lane_s16(sum, s7, filters_hi, 3);
  sum = vqadd_s16(sum, vmul_s16(s3, filter3));
  sum = vqadd_s16(sum, vmul_s16(s4, filter4));
  return sum;
}

static INLINE uint8x8_t convolve8_8(const int16x8_t s0, const int16x8_t s1,
                                    const int16x8_t s2, const int16x8_t s3,
                                    const int16x8_t s4, const int16x8_t s5,
                                    const int16x8_t s6, const int16x8_t s7,
                                    const int16x8_t filters,
                                    const int16x8_t filter3,
                                    const int16x8_t filter4) {
  const int16x4_t filters_lo = vget_low_s16(filters);
  const int16x4_t filters_hi = vget_high_s16(filters);
  int16x8_t sum;

  sum = vmulq_lane_s16(s0, filters_lo, 0);
  sum = vmlaq_lane_s16(sum, s1, filters_lo, 1);
  sum = vmlaq_lane_s16(sum, s2, filters_lo, 2);
  sum = vmlaq_lane_s16(sum, s5, filters_hi, 1);
  sum = vmlaq_lane_s16(sum, s6, filters_hi, 2);
  sum = vmlaq_lane_s16(sum, s7, filters_hi, 3);
  sum = vqaddq_s16(sum, vmulq_s16(s3, filter3));
  sum = vqaddq_s16(sum, vmulq_s16(s4, filter4));
  return vqrshrun_n_s16(sum, 7);
}

static INLINE uint8x8_t scale_filter_8(const uint8x8_t *const s,
                                       const int16x8_t filters) {
  const int16x8_t filter3 = vdupq_lane_s16(vget_low_s16(filters), 3);
  const int16x8_t filter4 = vdupq_lane_s16(vget_high_s16(filters), 0);
  int16x8_t ss[8];

  ss[0] = vreinterpretq_s16_u16(vmovl_u8(s[0]));
  ss[1] = vreinterpretq_s16_u16(vmovl_u8(s[1]));
  ss[2] = vreinterpretq_s16_u16(vmovl_u8(s[2]));
  ss[3] = vreinterpretq_s16_u16(vmovl_u8(s[3]));
  ss[4] = vreinterpretq_s16_u16(vmovl_u8(s[4]));
  ss[5] = vreinterpretq_s16_u16(vmovl_u8(s[5]));
  ss[6] = vreinterpretq_s16_u16(vmovl_u8(s[6]));
  ss[7] = vreinterpretq_s16_u16(vmovl_u8(s[7]));

  return convolve8_8(ss[0], ss[1], ss[2], ss[3], ss[4], ss[5], ss[6], ss[7],
                     filters, filter3, filter4);
}

#endif  // VPX_VPX_DSP_ARM_VPX_CONVOLVE8_NEON_H_
