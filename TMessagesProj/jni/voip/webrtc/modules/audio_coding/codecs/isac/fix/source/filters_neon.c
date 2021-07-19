/*
 *  Copyright (c) 2014 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include <arm_neon.h>

#include "rtc_base/checks.h"
#include "modules/audio_coding/codecs/isac/fix/source/codec.h"

// Autocorrelation function in fixed point.
// NOTE! Different from SPLIB-version in how it scales the signal.
int WebRtcIsacfix_AutocorrNeon(int32_t* __restrict r,
                               const int16_t* x,
                               int16_t n,
                               int16_t order,
                               int16_t* __restrict scale) {
  int i = 0;
  int16_t scaling = 0;
  uint32_t temp = 0;
  int64_t prod = 0;
  int64_t prod_tail = 0;

  RTC_DCHECK_EQ(0, n % 4);
  RTC_DCHECK_GE(n, 8);

  // Calculate r[0].
  int16x4_t x0_v;
  int32x4_t tmpa0_v;
  int64x2_t tmpb_v;

  tmpb_v = vdupq_n_s64(0);
  const int16_t* x_start = x;
  const int16_t* x_end0 = x_start + n;
  while (x_start < x_end0) {
    x0_v = vld1_s16(x_start);
    tmpa0_v = vmull_s16(x0_v, x0_v);
    tmpb_v = vpadalq_s32(tmpb_v, tmpa0_v);
    x_start += 4;
  }

#ifdef WEBRTC_ARCH_ARM64
  prod = vaddvq_s64(tmpb_v);
#else
  prod = vget_lane_s64(vadd_s64(vget_low_s64(tmpb_v), vget_high_s64(tmpb_v)),
                       0);
#endif
  // Calculate scaling (the value of shifting).
  temp = (uint32_t)(prod >> 31);

  scaling = temp ? 32 - WebRtcSpl_NormU32(temp) : 0;
  r[0] = (int32_t)(prod >> scaling);

  int16x8_t x1_v;
  int16x8_t y_v;
  int32x4_t tmpa1_v;
  // Perform the actual correlation calculation.
  for (i = 1; i < order + 1; i++) {
    tmpb_v = vdupq_n_s64(0);
    int rest = (n - i) % 8;
    x_start = x;
    x_end0 = x_start + n - i - rest;
    const int16_t* y_start = x_start + i;
    while (x_start < x_end0) {
      x1_v = vld1q_s16(x_start);
      y_v = vld1q_s16(y_start);
      tmpa0_v = vmull_s16(vget_low_s16(x1_v), vget_low_s16(y_v));
#ifdef WEBRTC_ARCH_ARM64
      tmpa1_v = vmull_high_s16(x1_v, y_v);
#else
      tmpa1_v = vmull_s16(vget_high_s16(x1_v), vget_high_s16(y_v));
#endif
      tmpb_v = vpadalq_s32(tmpb_v, tmpa0_v);
      tmpb_v = vpadalq_s32(tmpb_v, tmpa1_v);
      x_start += 8;
      y_start += 8;
    }
    // The remaining calculation.
    const int16_t* x_end1 = x + n - i;
    if (rest >= 4) {
        int16x4_t x2_v = vld1_s16(x_start);
        int16x4_t y2_v = vld1_s16(y_start);
        tmpa0_v = vmull_s16(x2_v, y2_v);
        tmpb_v = vpadalq_s32(tmpb_v, tmpa0_v);
        x_start += 4;
        y_start += 4;
    }
#ifdef WEBRTC_ARCH_ARM64
    prod = vaddvq_s64(tmpb_v);
#else
    prod = vget_lane_s64(vadd_s64(vget_low_s64(tmpb_v), vget_high_s64(tmpb_v)),
                         0);
#endif

    prod_tail = 0;
    while (x_start < x_end1) {
      prod_tail += *x_start * *y_start;
      ++x_start;
      ++y_start;
    }

    r[i] = (int32_t)((prod + prod_tail) >> scaling);
  }

  *scale = scaling;

  return order + 1;
}

