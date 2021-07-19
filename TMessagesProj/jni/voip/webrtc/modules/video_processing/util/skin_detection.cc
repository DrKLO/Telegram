/*
 *  Copyright (c) 2015 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "modules/video_processing/util/skin_detection.h"

namespace webrtc {

// Fixed-point skin color model parameters.
static const int skin_mean[5][2] = {{7463, 9614},
                                    {6400, 10240},
                                    {7040, 10240},
                                    {8320, 9280},
                                    {6800, 9614}};
static const int skin_inv_cov[4] = {4107, 1663, 1663, 2157};  // q16
static const int skin_threshold[6] = {1570636, 1400000, 800000,
                                      800000,  800000,  800000};  // q18

// Thresholds on luminance.
static const int y_low = 40;
static const int y_high = 220;

// Evaluates the Mahalanobis distance measure for the input CbCr values.
static int EvaluateSkinColorDifference(int cb, int cr, int idx) {
  const int cb_q6 = cb << 6;
  const int cr_q6 = cr << 6;
  const int cb_diff_q12 =
      (cb_q6 - skin_mean[idx][0]) * (cb_q6 - skin_mean[idx][0]);
  const int cbcr_diff_q12 =
      (cb_q6 - skin_mean[idx][0]) * (cr_q6 - skin_mean[idx][1]);
  const int cr_diff_q12 =
      (cr_q6 - skin_mean[idx][1]) * (cr_q6 - skin_mean[idx][1]);
  const int cb_diff_q2 = (cb_diff_q12 + (1 << 9)) >> 10;
  const int cbcr_diff_q2 = (cbcr_diff_q12 + (1 << 9)) >> 10;
  const int cr_diff_q2 = (cr_diff_q12 + (1 << 9)) >> 10;
  const int skin_diff =
      skin_inv_cov[0] * cb_diff_q2 + skin_inv_cov[1] * cbcr_diff_q2 +
      skin_inv_cov[2] * cbcr_diff_q2 + skin_inv_cov[3] * cr_diff_q2;
  return skin_diff;
}

static int SkinPixel(const uint8_t y, const uint8_t cb, const uint8_t cr) {
  if (y < y_low || y > y_high) {
    return 0;
  } else {
    if (MODEL_MODE == 0) {
      return (EvaluateSkinColorDifference(cb, cr, 0) < skin_threshold[0]);
    } else {
      // Exit on grey.
      if (cb == 128 && cr == 128)
        return 0;
      // Exit on very strong cb.
      if (cb > 150 && cr < 110)
        return 0;
      // Exit on (another) low luminance threshold if either color is high.
      if (y < 50 && (cb > 140 || cr > 140))
        return 0;
      for (int i = 0; i < 5; i++) {
        int diff = EvaluateSkinColorDifference(cb, cr, i);
        if (diff < skin_threshold[i + 1]) {
          return 1;
        } else if (diff > (skin_threshold[i + 1] << 3)) {
          // Exit if difference is much large than the threshold.
          return 0;
        }
      }
      return 0;
    }
  }
}

bool MbHasSkinColor(const uint8_t* y_src,
                    const uint8_t* u_src,
                    const uint8_t* v_src,
                    const int stride_y,
                    const int stride_u,
                    const int stride_v,
                    const int mb_row,
                    const int mb_col) {
  const uint8_t* y = y_src + ((mb_row << 4) + 8) * stride_y + (mb_col << 4) + 8;
  const uint8_t* u = u_src + ((mb_row << 3) + 4) * stride_u + (mb_col << 3) + 4;
  const uint8_t* v = v_src + ((mb_row << 3) + 4) * stride_v + (mb_col << 3) + 4;
  // Use 2x2 average of center pixel to compute skin area.
  uint8_t y_avg = (*y + *(y + 1) + *(y + stride_y) + *(y + stride_y + 1)) >> 2;
  uint8_t u_avg = (*u + *(u + 1) + *(u + stride_u) + *(u + stride_u + 1)) >> 2;
  uint8_t v_avg = (*v + *(v + 1) + *(v + stride_v) + *(v + stride_v + 1)) >> 2;
  return SkinPixel(y_avg, u_avg, v_avg) == 1;
}

}  // namespace webrtc
