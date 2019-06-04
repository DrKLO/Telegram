/*
 *  Copyright (c) 2014 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */
#if defined(__arm__) || defined(_M_ARM) || defined(__aarch64__)

#include "common_audio/signal_processing/include/signal_processing_library.h"
#include "rtc_base/system/arch.h"

#include <arm_neon.h>

static inline void DotProductWithScaleNeon(int32_t* cross_correlation,
                                           const int16_t* vector1,
                                           const int16_t* vector2,
                                           size_t length,
                                           int scaling) {
  size_t i = 0;
  size_t len1 = length >> 3;
  size_t len2 = length & 7;
  int64x2_t sum0 = vdupq_n_s64(0);
  int64x2_t sum1 = vdupq_n_s64(0);

  for (i = len1; i > 0; i -= 1) {
    int16x8_t seq1_16x8 = vld1q_s16(vector1);
    int16x8_t seq2_16x8 = vld1q_s16(vector2);
#if defined(WEBRTC_ARCH_ARM64)
    int32x4_t tmp0 = vmull_s16(vget_low_s16(seq1_16x8),
                               vget_low_s16(seq2_16x8));
    int32x4_t tmp1 = vmull_high_s16(seq1_16x8, seq2_16x8);
#else
    int32x4_t tmp0 = vmull_s16(vget_low_s16(seq1_16x8),
                               vget_low_s16(seq2_16x8));
    int32x4_t tmp1 = vmull_s16(vget_high_s16(seq1_16x8),
                               vget_high_s16(seq2_16x8));
#endif
    sum0 = vpadalq_s32(sum0, tmp0);
    sum1 = vpadalq_s32(sum1, tmp1);
    vector1 += 8;
    vector2 += 8;
  }

  // Calculate the rest of the samples.
  int64_t sum_res = 0;
  for (i = len2; i > 0; i -= 1) {
    sum_res += WEBRTC_SPL_MUL_16_16(*vector1, *vector2);
    vector1++;
    vector2++;
  }

  sum0 = vaddq_s64(sum0, sum1);
#if defined(WEBRTC_ARCH_ARM64)
  int64_t sum2 = vaddvq_s64(sum0);
  *cross_correlation = (int32_t)((sum2 + sum_res) >> scaling);
#else
  int64x1_t shift = vdup_n_s64(-scaling);
  int64x1_t sum2 = vadd_s64(vget_low_s64(sum0), vget_high_s64(sum0));
  sum2 = vadd_s64(sum2, vdup_n_s64(sum_res));
  sum2 = vshl_s64(sum2, shift);
  vst1_lane_s32(cross_correlation, vreinterpret_s32_s64(sum2), 0);
#endif
}

/* NEON version of WebRtcSpl_CrossCorrelation() for ARM32/64 platforms. */
void WebRtcSpl_CrossCorrelationNeon(int32_t* cross_correlation,
                                    const int16_t* seq1,
                                    const int16_t* seq2,
                                    size_t dim_seq,
                                    size_t dim_cross_correlation,
                                    int right_shifts,
                                    int step_seq2) {
  size_t i = 0;

  for (i = 0; i < dim_cross_correlation; i++) {
    const int16_t* seq1_ptr = seq1;
    const int16_t* seq2_ptr = seq2 + (step_seq2 * i);

    DotProductWithScaleNeon(cross_correlation,
                            seq1_ptr,
                            seq2_ptr,
                            dim_seq,
                            right_shifts);
    cross_correlation++;
  }
}

#endif
