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

#include "./vp9_rtcd.h"

int64_t vp9_block_error_fp_neon(const int16_t *coeff, const int16_t *dqcoeff,
                                int block_size) {
  int64x2_t error = vdupq_n_s64(0);

  assert(block_size >= 8);
  assert((block_size % 8) == 0);

  do {
    const int16x8_t c = vld1q_s16(coeff);
    const int16x8_t d = vld1q_s16(dqcoeff);
    const int16x8_t diff = vsubq_s16(c, d);
    const int16x4_t diff_lo = vget_low_s16(diff);
    const int16x4_t diff_hi = vget_high_s16(diff);
    // diff is 15-bits, the squares 30, so we can store 2 in 31-bits before
    // accumulating them in 64-bits.
    const int32x4_t err0 = vmull_s16(diff_lo, diff_lo);
    const int32x4_t err1 = vmlal_s16(err0, diff_hi, diff_hi);
    const int64x2_t err2 = vaddl_s32(vget_low_s32(err1), vget_high_s32(err1));
    error = vaddq_s64(error, err2);
    coeff += 8;
    dqcoeff += 8;
    block_size -= 8;
  } while (block_size != 0);

  return vgetq_lane_s64(error, 0) + vgetq_lane_s64(error, 1);
}
