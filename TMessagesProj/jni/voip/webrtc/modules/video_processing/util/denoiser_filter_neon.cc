/*
 *  Copyright (c) 2015 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "modules/video_processing/util/denoiser_filter_neon.h"

#include <arm_neon.h>

namespace webrtc {

const int kSumDiffThresholdHighNeon = 600;

static int HorizontalAddS16x8(const int16x8_t v_16x8) {
  const int32x4_t a = vpaddlq_s16(v_16x8);
  const int64x2_t b = vpaddlq_s32(a);
  const int32x2_t c = vadd_s32(vreinterpret_s32_s64(vget_low_s64(b)),
                               vreinterpret_s32_s64(vget_high_s64(b)));
  return vget_lane_s32(c, 0);
}

static int HorizontalAddS32x4(const int32x4_t v_32x4) {
  const int64x2_t b = vpaddlq_s32(v_32x4);
  const int32x2_t c = vadd_s32(vreinterpret_s32_s64(vget_low_s64(b)),
                               vreinterpret_s32_s64(vget_high_s64(b)));
  return vget_lane_s32(c, 0);
}

static void VarianceNeonW8(const uint8_t* a,
                           int a_stride,
                           const uint8_t* b,
                           int b_stride,
                           int w,
                           int h,
                           uint32_t* sse,
                           int64_t* sum) {
  int16x8_t v_sum = vdupq_n_s16(0);
  int32x4_t v_sse_lo = vdupq_n_s32(0);
  int32x4_t v_sse_hi = vdupq_n_s32(0);

  for (int i = 0; i < h; ++i) {
    for (int j = 0; j < w; j += 8) {
      const uint8x8_t v_a = vld1_u8(&a[j]);
      const uint8x8_t v_b = vld1_u8(&b[j]);
      const uint16x8_t v_diff = vsubl_u8(v_a, v_b);
      const int16x8_t sv_diff = vreinterpretq_s16_u16(v_diff);
      v_sum = vaddq_s16(v_sum, sv_diff);
      v_sse_lo =
          vmlal_s16(v_sse_lo, vget_low_s16(sv_diff), vget_low_s16(sv_diff));
      v_sse_hi =
          vmlal_s16(v_sse_hi, vget_high_s16(sv_diff), vget_high_s16(sv_diff));
    }
    a += a_stride;
    b += b_stride;
  }

  *sum = HorizontalAddS16x8(v_sum);
  *sse =
      static_cast<uint32_t>(HorizontalAddS32x4(vaddq_s32(v_sse_lo, v_sse_hi)));
}

void DenoiserFilterNEON::CopyMem16x16(const uint8_t* src,
                                      int src_stride,
                                      uint8_t* dst,
                                      int dst_stride) {
  uint8x16_t qtmp;
  for (int r = 0; r < 16; r++) {
    qtmp = vld1q_u8(src);
    vst1q_u8(dst, qtmp);
    src += src_stride;
    dst += dst_stride;
  }
}

uint32_t DenoiserFilterNEON::Variance16x8(const uint8_t* a,
                                          int a_stride,
                                          const uint8_t* b,
                                          int b_stride,
                                          uint32_t* sse) {
  int64_t sum = 0;
  VarianceNeonW8(a, a_stride << 1, b, b_stride << 1, 16, 8, sse, &sum);
  return *sse - ((sum * sum) >> 7);
}

DenoiserDecision DenoiserFilterNEON::MbDenoise(const uint8_t* mc_running_avg_y,
                                               int mc_running_avg_y_stride,
                                               uint8_t* running_avg_y,
                                               int running_avg_y_stride,
                                               const uint8_t* sig,
                                               int sig_stride,
                                               uint8_t motion_magnitude,
                                               int increase_denoising) {
  // If motion_magnitude is small, making the denoiser more aggressive by
  // increasing the adjustment for each level, level1 adjustment is
  // increased, the deltas stay the same.
  int shift_inc =
      (increase_denoising && motion_magnitude <= kMotionMagnitudeThreshold) ? 1
                                                                            : 0;
  int sum_diff_thresh = 0;
  const uint8x16_t v_level1_adjustment = vmovq_n_u8(
      (motion_magnitude <= kMotionMagnitudeThreshold) ? 4 + shift_inc : 3);
  const uint8x16_t v_delta_level_1_and_2 = vdupq_n_u8(1);
  const uint8x16_t v_delta_level_2_and_3 = vdupq_n_u8(2);
  const uint8x16_t v_level1_threshold = vmovq_n_u8(4 + shift_inc);
  const uint8x16_t v_level2_threshold = vdupq_n_u8(8);
  const uint8x16_t v_level3_threshold = vdupq_n_u8(16);
  int64x2_t v_sum_diff_total = vdupq_n_s64(0);

  // Go over lines.
  for (int r = 0; r < 16; ++r) {
    // Load inputs.
    const uint8x16_t v_sig = vld1q_u8(sig);
    const uint8x16_t v_mc_running_avg_y = vld1q_u8(mc_running_avg_y);

    // Calculate absolute difference and sign masks.
    const uint8x16_t v_abs_diff = vabdq_u8(v_sig, v_mc_running_avg_y);
    const uint8x16_t v_diff_pos_mask = vcltq_u8(v_sig, v_mc_running_avg_y);
    const uint8x16_t v_diff_neg_mask = vcgtq_u8(v_sig, v_mc_running_avg_y);

    // Figure out which level that put us in.
    const uint8x16_t v_level1_mask = vcleq_u8(v_level1_threshold, v_abs_diff);
    const uint8x16_t v_level2_mask = vcleq_u8(v_level2_threshold, v_abs_diff);
    const uint8x16_t v_level3_mask = vcleq_u8(v_level3_threshold, v_abs_diff);

    // Calculate absolute adjustments for level 1, 2 and 3.
    const uint8x16_t v_level2_adjustment =
        vandq_u8(v_level2_mask, v_delta_level_1_and_2);
    const uint8x16_t v_level3_adjustment =
        vandq_u8(v_level3_mask, v_delta_level_2_and_3);
    const uint8x16_t v_level1and2_adjustment =
        vaddq_u8(v_level1_adjustment, v_level2_adjustment);
    const uint8x16_t v_level1and2and3_adjustment =
        vaddq_u8(v_level1and2_adjustment, v_level3_adjustment);

    // Figure adjustment absolute value by selecting between the absolute
    // difference if in level0 or the value for level 1, 2 and 3.
    const uint8x16_t v_abs_adjustment =
        vbslq_u8(v_level1_mask, v_level1and2and3_adjustment, v_abs_diff);

    // Calculate positive and negative adjustments. Apply them to the signal
    // and accumulate them. Adjustments are less than eight and the maximum
    // sum of them (7 * 16) can fit in a signed char.
    const uint8x16_t v_pos_adjustment =
        vandq_u8(v_diff_pos_mask, v_abs_adjustment);
    const uint8x16_t v_neg_adjustment =
        vandq_u8(v_diff_neg_mask, v_abs_adjustment);

    uint8x16_t v_running_avg_y = vqaddq_u8(v_sig, v_pos_adjustment);
    v_running_avg_y = vqsubq_u8(v_running_avg_y, v_neg_adjustment);

    // Store results.
    vst1q_u8(running_avg_y, v_running_avg_y);

    // Sum all the accumulators to have the sum of all pixel differences
    // for this macroblock.
    {
      const int8x16_t v_sum_diff =
          vqsubq_s8(vreinterpretq_s8_u8(v_pos_adjustment),
                    vreinterpretq_s8_u8(v_neg_adjustment));
      const int16x8_t fe_dc_ba_98_76_54_32_10 = vpaddlq_s8(v_sum_diff);
      const int32x4_t fedc_ba98_7654_3210 =
          vpaddlq_s16(fe_dc_ba_98_76_54_32_10);
      const int64x2_t fedcba98_76543210 = vpaddlq_s32(fedc_ba98_7654_3210);

      v_sum_diff_total = vqaddq_s64(v_sum_diff_total, fedcba98_76543210);
    }

    // Update pointers for next iteration.
    sig += sig_stride;
    mc_running_avg_y += mc_running_avg_y_stride;
    running_avg_y += running_avg_y_stride;
  }

  // Too much adjustments => copy block.
  int64x1_t x = vqadd_s64(vget_high_s64(v_sum_diff_total),
                          vget_low_s64(v_sum_diff_total));
  int sum_diff = vget_lane_s32(vabs_s32(vreinterpret_s32_s64(x)), 0);
  sum_diff_thresh =
      increase_denoising ? kSumDiffThresholdHighNeon : kSumDiffThreshold;
  if (sum_diff > sum_diff_thresh)
    return COPY_BLOCK;

  // Tell above level that block was filtered.
  running_avg_y -= running_avg_y_stride * 16;
  sig -= sig_stride * 16;

  return FILTER_BLOCK;
}

}  // namespace webrtc
