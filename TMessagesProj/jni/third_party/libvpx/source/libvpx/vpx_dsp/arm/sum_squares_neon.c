/*
 *  Copyright (c) 2018 The WebM project authors. All Rights Reserved.
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

uint64_t vpx_sum_squares_2d_i16_neon(const int16_t *src, int stride, int size) {
  uint64x1_t s2;

  if (size == 4) {
    int16x4_t s[4];
    int32x4_t s0;
    uint32x2_t s1;

    s[0] = vld1_s16(src + 0 * stride);
    s[1] = vld1_s16(src + 1 * stride);
    s[2] = vld1_s16(src + 2 * stride);
    s[3] = vld1_s16(src + 3 * stride);
    s0 = vmull_s16(s[0], s[0]);
    s0 = vmlal_s16(s0, s[1], s[1]);
    s0 = vmlal_s16(s0, s[2], s[2]);
    s0 = vmlal_s16(s0, s[3], s[3]);
    s1 = vpadd_u32(vget_low_u32(vreinterpretq_u32_s32(s0)),
                   vget_high_u32(vreinterpretq_u32_s32(s0)));
    s2 = vpaddl_u32(s1);
  } else {
    int r = size;
    uint64x2_t s1 = vdupq_n_u64(0);

    do {
      int c = size;
      int32x4_t s0 = vdupq_n_s32(0);
      const int16_t *src_t = src;

      do {
        int16x8_t s[8];

        s[0] = vld1q_s16(src_t + 0 * stride);
        s[1] = vld1q_s16(src_t + 1 * stride);
        s[2] = vld1q_s16(src_t + 2 * stride);
        s[3] = vld1q_s16(src_t + 3 * stride);
        s[4] = vld1q_s16(src_t + 4 * stride);
        s[5] = vld1q_s16(src_t + 5 * stride);
        s[6] = vld1q_s16(src_t + 6 * stride);
        s[7] = vld1q_s16(src_t + 7 * stride);
        s0 = vmlal_s16(s0, vget_low_s16(s[0]), vget_low_s16(s[0]));
        s0 = vmlal_s16(s0, vget_low_s16(s[1]), vget_low_s16(s[1]));
        s0 = vmlal_s16(s0, vget_low_s16(s[2]), vget_low_s16(s[2]));
        s0 = vmlal_s16(s0, vget_low_s16(s[3]), vget_low_s16(s[3]));
        s0 = vmlal_s16(s0, vget_low_s16(s[4]), vget_low_s16(s[4]));
        s0 = vmlal_s16(s0, vget_low_s16(s[5]), vget_low_s16(s[5]));
        s0 = vmlal_s16(s0, vget_low_s16(s[6]), vget_low_s16(s[6]));
        s0 = vmlal_s16(s0, vget_low_s16(s[7]), vget_low_s16(s[7]));
        s0 = vmlal_s16(s0, vget_high_s16(s[0]), vget_high_s16(s[0]));
        s0 = vmlal_s16(s0, vget_high_s16(s[1]), vget_high_s16(s[1]));
        s0 = vmlal_s16(s0, vget_high_s16(s[2]), vget_high_s16(s[2]));
        s0 = vmlal_s16(s0, vget_high_s16(s[3]), vget_high_s16(s[3]));
        s0 = vmlal_s16(s0, vget_high_s16(s[4]), vget_high_s16(s[4]));
        s0 = vmlal_s16(s0, vget_high_s16(s[5]), vget_high_s16(s[5]));
        s0 = vmlal_s16(s0, vget_high_s16(s[6]), vget_high_s16(s[6]));
        s0 = vmlal_s16(s0, vget_high_s16(s[7]), vget_high_s16(s[7]));
        src_t += 8;
        c -= 8;
      } while (c);

      s1 = vaddw_u32(s1, vget_low_u32(vreinterpretq_u32_s32(s0)));
      s1 = vaddw_u32(s1, vget_high_u32(vreinterpretq_u32_s32(s0)));
      src += 8 * stride;
      r -= 8;
    } while (r);

    s2 = vadd_u64(vget_low_u64(s1), vget_high_u64(s1));
  }

  return vget_lane_u64(s2, 0);
}
