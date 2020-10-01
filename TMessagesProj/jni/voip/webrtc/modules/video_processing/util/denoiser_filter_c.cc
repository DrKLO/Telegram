/*
 *  Copyright (c) 2015 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "modules/video_processing/util/denoiser_filter_c.h"

#include <stdlib.h>
#include <string.h>

namespace webrtc {

void DenoiserFilterC::CopyMem16x16(const uint8_t* src,
                                   int src_stride,
                                   uint8_t* dst,
                                   int dst_stride) {
  for (int i = 0; i < 16; i++) {
    memcpy(dst, src, 16);
    src += src_stride;
    dst += dst_stride;
  }
}

uint32_t DenoiserFilterC::Variance16x8(const uint8_t* a,
                                       int a_stride,
                                       const uint8_t* b,
                                       int b_stride,
                                       uint32_t* sse) {
  int sum = 0;
  *sse = 0;
  a_stride <<= 1;
  b_stride <<= 1;

  for (int i = 0; i < 8; i++) {
    for (int j = 0; j < 16; j++) {
      const int diff = a[j] - b[j];
      sum += diff;
      *sse += diff * diff;
    }

    a += a_stride;
    b += b_stride;
  }
  return *sse - ((static_cast<int64_t>(sum) * sum) >> 7);
}

DenoiserDecision DenoiserFilterC::MbDenoise(const uint8_t* mc_running_avg_y,
                                            int mc_avg_y_stride,
                                            uint8_t* running_avg_y,
                                            int avg_y_stride,
                                            const uint8_t* sig,
                                            int sig_stride,
                                            uint8_t motion_magnitude,
                                            int increase_denoising) {
  int sum_diff_thresh = 0;
  int sum_diff = 0;
  int adj_val[3] = {3, 4, 6};
  int shift_inc1 = 0;
  int shift_inc2 = 1;
  int col_sum[16] = {0};
  if (motion_magnitude <= kMotionMagnitudeThreshold) {
    if (increase_denoising) {
      shift_inc1 = 1;
      shift_inc2 = 2;
    }
    adj_val[0] += shift_inc2;
    adj_val[1] += shift_inc2;
    adj_val[2] += shift_inc2;
  }

  for (int r = 0; r < 16; ++r) {
    for (int c = 0; c < 16; ++c) {
      int diff = 0;
      int adjustment = 0;
      int absdiff = 0;

      diff = mc_running_avg_y[c] - sig[c];
      absdiff = abs(diff);

      // When |diff| <= |3 + shift_inc1|, use pixel value from
      // last denoised raw.
      if (absdiff <= 3 + shift_inc1) {
        running_avg_y[c] = mc_running_avg_y[c];
        col_sum[c] += diff;
      } else {
        if (absdiff >= 4 + shift_inc1 && absdiff <= 7)
          adjustment = adj_val[0];
        else if (absdiff >= 8 && absdiff <= 15)
          adjustment = adj_val[1];
        else
          adjustment = adj_val[2];

        if (diff > 0) {
          if ((sig[c] + adjustment) > 255)
            running_avg_y[c] = 255;
          else
            running_avg_y[c] = sig[c] + adjustment;

          col_sum[c] += adjustment;
        } else {
          if ((sig[c] - adjustment) < 0)
            running_avg_y[c] = 0;
          else
            running_avg_y[c] = sig[c] - adjustment;

          col_sum[c] -= adjustment;
        }
      }
    }

    // Update pointers for next iteration.
    sig += sig_stride;
    mc_running_avg_y += mc_avg_y_stride;
    running_avg_y += avg_y_stride;
  }

  for (int c = 0; c < 16; ++c) {
    if (col_sum[c] >= 128) {
      col_sum[c] = 127;
    }
    sum_diff += col_sum[c];
  }

  sum_diff_thresh =
      increase_denoising ? kSumDiffThresholdHigh : kSumDiffThreshold;
  if (abs(sum_diff) > sum_diff_thresh)
    return COPY_BLOCK;

  return FILTER_BLOCK;
}

}  // namespace webrtc
