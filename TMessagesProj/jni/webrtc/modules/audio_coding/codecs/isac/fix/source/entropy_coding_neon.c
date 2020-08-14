/*
 *  Copyright (c) 2013 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

/* This file contains WebRtcIsacfix_MatrixProduct1Neon() and
 * WebRtcIsacfix_MatrixProduct2Neon() for ARM Neon platform. API's are in
 * entropy_coding.c. Results are bit exact with the c code for
 * generic platforms.
 */

#include <arm_neon.h>
#include <stddef.h>

#include "modules/audio_coding/codecs/isac/fix/source/entropy_coding.h"
#include "common_audio/signal_processing/include/signal_processing_library.h"
#include "rtc_base/checks.h"

void WebRtcIsacfix_MatrixProduct1Neon(const int16_t matrix0[],
                                      const int32_t matrix1[],
                                      int32_t matrix_product[],
                                      const int matrix1_index_factor1,
                                      const int matrix0_index_factor1,
                                      const int matrix1_index_init_case,
                                      const int matrix1_index_step,
                                      const int matrix0_index_step,
                                      const int inner_loop_count,
                                      const int mid_loop_count,
                                      const int shift) {
  int j = 0, k = 0, n = 0;
  int matrix1_index = 0, matrix0_index = 0, matrix_prod_index = 0;
  int* matrix1_index_factor2 = &j;
  int* matrix0_index_factor2 = &k;
  if (matrix1_index_init_case != 0) {
    matrix1_index_factor2 = &k;
    matrix0_index_factor2 = &j;
  }
  int32x4_t shift32x4 = vdupq_n_s32(shift);
  int32x2_t shift32x2 = vdup_n_s32(shift);
  int32x4_t sum_32x4 =  vdupq_n_s32(0);
  int32x2_t sum_32x2 =  vdup_n_s32(0);

  RTC_DCHECK_EQ(0, inner_loop_count % 2);
  RTC_DCHECK_EQ(0, mid_loop_count % 2);

  if (matrix1_index_init_case != 0 && matrix1_index_factor1 == 1) {
    for (j = 0; j < SUBFRAMES; j++) {
      matrix_prod_index = mid_loop_count * j;
      for (k = 0; k < (mid_loop_count >> 2) << 2; k += 4) {
        sum_32x4 = veorq_s32(sum_32x4, sum_32x4);  // Initialize to zeros.
        matrix1_index = k;
        matrix0_index = matrix0_index_factor1 * j;
        for (n = 0; n < inner_loop_count; n++) {
          int32x4_t matrix0_32x4 =
              vdupq_n_s32((int32_t)(matrix0[matrix0_index]) << 15);
          int32x4_t matrix1_32x4 =
              vshlq_s32(vld1q_s32(&matrix1[matrix1_index]), shift32x4);
          int32x4_t multi_32x4 = vqdmulhq_s32(matrix0_32x4, matrix1_32x4);
          sum_32x4 = vqaddq_s32(sum_32x4, multi_32x4);
          matrix1_index += matrix1_index_step;
          matrix0_index += matrix0_index_step;
        }
        vst1q_s32(&matrix_product[matrix_prod_index], sum_32x4);
        matrix_prod_index += 4;
      }
      if (mid_loop_count % 4 > 1) {
        sum_32x2 = veor_s32(sum_32x2, sum_32x2);  // Initialize to zeros.
        matrix1_index = k;
        k += 2;
        matrix0_index = matrix0_index_factor1 * j;
        for (n = 0; n < inner_loop_count; n++) {
          int32x2_t matrix0_32x2 =
              vdup_n_s32((int32_t)(matrix0[matrix0_index]) << 15);
          int32x2_t matrix1_32x2 =
              vshl_s32(vld1_s32(&matrix1[matrix1_index]), shift32x2);
          int32x2_t multi_32x2 = vqdmulh_s32(matrix0_32x2, matrix1_32x2);
          sum_32x2 = vqadd_s32(sum_32x2, multi_32x2);
          matrix1_index += matrix1_index_step;
          matrix0_index += matrix0_index_step;
        }
        vst1_s32(&matrix_product[matrix_prod_index], sum_32x2);
        matrix_prod_index += 2;
      }
    }
  }
  else if (matrix1_index_init_case == 0 && matrix0_index_factor1 == 1) {
    int32x2_t multi_32x2 = vdup_n_s32(0);
    int32x2_t matrix0_32x2 = vdup_n_s32(0);
    for (j = 0; j < SUBFRAMES; j++) {
      matrix_prod_index = mid_loop_count * j;
      for (k = 0; k < (mid_loop_count >> 2) << 2; k += 4) {
        sum_32x4 = veorq_s32(sum_32x4, sum_32x4);  // Initialize to zeros.
        matrix1_index = matrix1_index_factor1 * j;
        matrix0_index = k;
        for (n = 0; n < inner_loop_count; n++) {
          int32x4_t matrix1_32x4 = vdupq_n_s32(matrix1[matrix1_index] << shift);
          int32x4_t matrix0_32x4 =
              vshll_n_s16(vld1_s16(&matrix0[matrix0_index]), 15);
          int32x4_t multi_32x4 = vqdmulhq_s32(matrix0_32x4, matrix1_32x4);
          sum_32x4 = vqaddq_s32(sum_32x4, multi_32x4);
          matrix1_index += matrix1_index_step;
          matrix0_index += matrix0_index_step;
        }
        vst1q_s32(&matrix_product[matrix_prod_index], sum_32x4);
        matrix_prod_index += 4;
      }
      if (mid_loop_count % 4 > 1) {
        sum_32x2 = veor_s32(sum_32x2, sum_32x2);  // Initialize to zeros.
        matrix1_index = matrix1_index_factor1 * j;
        matrix0_index = k;
        for (n = 0; n < inner_loop_count; n++) {
          int32x2_t matrix1_32x2 = vdup_n_s32(matrix1[matrix1_index] << shift);
          matrix0_32x2 =
              vset_lane_s32((int32_t)matrix0[matrix0_index], matrix0_32x2, 0);
          matrix0_32x2 = vset_lane_s32((int32_t)matrix0[matrix0_index + 1],
                                     matrix0_32x2, 1);
          matrix0_32x2 = vshl_n_s32(matrix0_32x2, 15);
          multi_32x2 = vqdmulh_s32(matrix1_32x2, matrix0_32x2);
          sum_32x2 = vqadd_s32(sum_32x2, multi_32x2);
          matrix1_index += matrix1_index_step;
          matrix0_index += matrix0_index_step;
        }
        vst1_s32(&matrix_product[matrix_prod_index], sum_32x2);
        matrix_prod_index += 2;
      }
    }
  }
  else if (matrix1_index_init_case == 0 &&
           matrix1_index_step == 1 &&
           matrix0_index_step == 1) {
    int32x2_t multi_32x2 = vdup_n_s32(0);
    int32x2_t matrix0_32x2 = vdup_n_s32(0);
    for (j = 0; j < SUBFRAMES; j++) {
      matrix_prod_index = mid_loop_count * j;
      for (k = 0; k < mid_loop_count; k++) {
        sum_32x4 = veorq_s32(sum_32x4, sum_32x4);  // Initialize to zeros.
        matrix1_index = matrix1_index_factor1 * j;
        matrix0_index = matrix0_index_factor1 * k;
        for (n = 0; n < (inner_loop_count >> 2) << 2; n += 4) {
          int32x4_t matrix1_32x4 =
              vshlq_s32(vld1q_s32(&matrix1[matrix1_index]), shift32x4);
          int32x4_t matrix0_32x4 =
              vshll_n_s16(vld1_s16(&matrix0[matrix0_index]), 15);
          int32x4_t multi_32x4 = vqdmulhq_s32(matrix0_32x4, matrix1_32x4);
          sum_32x4 = vqaddq_s32(sum_32x4, multi_32x4);
          matrix1_index += 4;
          matrix0_index += 4;
        }
        sum_32x2 = vqadd_s32(vget_low_s32(sum_32x4), vget_high_s32(sum_32x4));
        if (inner_loop_count % 4 > 1) {
          int32x2_t matrix1_32x2 =
              vshl_s32(vld1_s32(&matrix1[matrix1_index]), shift32x2);
          matrix0_32x2 =
              vset_lane_s32((int32_t)matrix0[matrix0_index], matrix0_32x2, 0);
          matrix0_32x2 = vset_lane_s32((int32_t)matrix0[matrix0_index + 1],
                                     matrix0_32x2, 1);
          matrix0_32x2 = vshl_n_s32(matrix0_32x2, 15);
          multi_32x2 = vqdmulh_s32(matrix1_32x2, matrix0_32x2);
          sum_32x2 = vqadd_s32(sum_32x2, multi_32x2);
        }
        sum_32x2 = vpadd_s32(sum_32x2, sum_32x2);
        vst1_lane_s32(&matrix_product[matrix_prod_index], sum_32x2, 0);
        matrix_prod_index++;
      }
    }
  }
  else {
    for (j = 0; j < SUBFRAMES; j++) {
      matrix_prod_index = mid_loop_count * j;
      for (k=0; k < mid_loop_count; k++) {
        int32_t sum32 = 0;
        matrix1_index = matrix1_index_factor1 * (*matrix1_index_factor2);
        matrix0_index = matrix0_index_factor1 * (*matrix0_index_factor2);
        for (n = 0; n < inner_loop_count; n++) {
          sum32 += (WEBRTC_SPL_MUL_16_32_RSFT16(matrix0[matrix0_index],
              matrix1[matrix1_index] << shift));
          matrix1_index += matrix1_index_step;
          matrix0_index += matrix0_index_step;
        }
        matrix_product[matrix_prod_index] = sum32;
        matrix_prod_index++;
      }
    }
  }
}

void WebRtcIsacfix_MatrixProduct2Neon(const int16_t matrix0[],
                                      const int32_t matrix1[],
                                      int32_t matrix_product[],
                                      const int matrix0_index_factor,
                                      const int matrix0_index_step) {
  int j = 0, n = 0;
  int matrix1_index = 0, matrix0_index = 0, matrix_prod_index = 0;
  int32x2_t sum_32x2 = vdup_n_s32(0);
  for (j = 0; j < SUBFRAMES; j++) {
    sum_32x2 = veor_s32(sum_32x2, sum_32x2);  // Initialize to zeros.
    matrix1_index = 0;
    matrix0_index = matrix0_index_factor * j;
    for (n = SUBFRAMES; n > 0; n--) {
      int32x2_t matrix0_32x2 =
          vdup_n_s32((int32_t)(matrix0[matrix0_index]) << 15);
      int32x2_t matrix1_32x2 = vld1_s32(&matrix1[matrix1_index]);
      int32x2_t multi_32x2 = vqdmulh_s32(matrix0_32x2, matrix1_32x2);
      sum_32x2 = vqadd_s32(sum_32x2, multi_32x2);
      matrix1_index += 2;
      matrix0_index += matrix0_index_step;
    }
    sum_32x2 = vshr_n_s32(sum_32x2, 3);
    vst1_s32(&matrix_product[matrix_prod_index], sum_32x2);
    matrix_prod_index += 2;
  }
}
