/*
 *  Copyright (c) 2015 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "modules/video_processing/util/denoiser_filter_sse2.h"

#include <emmintrin.h>
#include <stdlib.h>
#include <string.h>

namespace webrtc {

static void Get8x8varSse2(const uint8_t* src,
                          int src_stride,
                          const uint8_t* ref,
                          int ref_stride,
                          unsigned int* sse,
                          int* sum) {
  const __m128i zero = _mm_setzero_si128();
  __m128i vsum = _mm_setzero_si128();
  __m128i vsse = _mm_setzero_si128();

  for (int i = 0; i < 8; i += 2) {
    const __m128i src0 = _mm_unpacklo_epi8(
        _mm_loadl_epi64((const __m128i*)(src + i * src_stride)), zero);
    const __m128i ref0 = _mm_unpacklo_epi8(
        _mm_loadl_epi64((const __m128i*)(ref + i * ref_stride)), zero);
    const __m128i diff0 = _mm_sub_epi16(src0, ref0);

    const __m128i src1 = _mm_unpacklo_epi8(
        _mm_loadl_epi64((const __m128i*)(src + (i + 1) * src_stride)), zero);
    const __m128i ref1 = _mm_unpacklo_epi8(
        _mm_loadl_epi64((const __m128i*)(ref + (i + 1) * ref_stride)), zero);
    const __m128i diff1 = _mm_sub_epi16(src1, ref1);

    vsum = _mm_add_epi16(vsum, diff0);
    vsum = _mm_add_epi16(vsum, diff1);
    vsse = _mm_add_epi32(vsse, _mm_madd_epi16(diff0, diff0));
    vsse = _mm_add_epi32(vsse, _mm_madd_epi16(diff1, diff1));
  }

  // sum
  vsum = _mm_add_epi16(vsum, _mm_srli_si128(vsum, 8));
  vsum = _mm_add_epi16(vsum, _mm_srli_si128(vsum, 4));
  vsum = _mm_add_epi16(vsum, _mm_srli_si128(vsum, 2));
  *sum = static_cast<int16_t>(_mm_extract_epi16(vsum, 0));

  // sse
  vsse = _mm_add_epi32(vsse, _mm_srli_si128(vsse, 8));
  vsse = _mm_add_epi32(vsse, _mm_srli_si128(vsse, 4));
  *sse = _mm_cvtsi128_si32(vsse);
}

static void VarianceSSE2(const unsigned char* src,
                         int src_stride,
                         const unsigned char* ref,
                         int ref_stride,
                         int w,
                         int h,
                         uint32_t* sse,
                         int64_t* sum,
                         int block_size) {
  *sse = 0;
  *sum = 0;

  for (int i = 0; i < h; i += block_size) {
    for (int j = 0; j < w; j += block_size) {
      uint32_t sse0 = 0;
      int32_t sum0 = 0;

      Get8x8varSse2(src + src_stride * i + j, src_stride,
                    ref + ref_stride * i + j, ref_stride, &sse0, &sum0);
      *sse += sse0;
      *sum += sum0;
    }
  }
}

// Compute the sum of all pixel differences of this MB.
static uint32_t AbsSumDiff16x1(__m128i acc_diff) {
  const __m128i k_1 = _mm_set1_epi16(1);
  const __m128i acc_diff_lo =
      _mm_srai_epi16(_mm_unpacklo_epi8(acc_diff, acc_diff), 8);
  const __m128i acc_diff_hi =
      _mm_srai_epi16(_mm_unpackhi_epi8(acc_diff, acc_diff), 8);
  const __m128i acc_diff_16 = _mm_add_epi16(acc_diff_lo, acc_diff_hi);
  const __m128i hg_fe_dc_ba = _mm_madd_epi16(acc_diff_16, k_1);
  const __m128i hgfe_dcba =
      _mm_add_epi32(hg_fe_dc_ba, _mm_srli_si128(hg_fe_dc_ba, 8));
  const __m128i hgfedcba =
      _mm_add_epi32(hgfe_dcba, _mm_srli_si128(hgfe_dcba, 4));
  unsigned int sum_diff = abs(_mm_cvtsi128_si32(hgfedcba));

  return sum_diff;
}

uint32_t DenoiserFilterSSE2::Variance16x8(const uint8_t* src,
                                          int src_stride,
                                          const uint8_t* ref,
                                          int ref_stride,
                                          uint32_t* sse) {
  int64_t sum = 0;
  VarianceSSE2(src, src_stride << 1, ref, ref_stride << 1, 16, 8, sse, &sum, 8);
  return *sse - ((sum * sum) >> 7);
}

DenoiserDecision DenoiserFilterSSE2::MbDenoise(const uint8_t* mc_running_avg_y,
                                               int mc_avg_y_stride,
                                               uint8_t* running_avg_y,
                                               int avg_y_stride,
                                               const uint8_t* sig,
                                               int sig_stride,
                                               uint8_t motion_magnitude,
                                               int increase_denoising) {
  DenoiserDecision decision = FILTER_BLOCK;
  unsigned int sum_diff_thresh = 0;
  int shift_inc =
      (increase_denoising && motion_magnitude <= kMotionMagnitudeThreshold) ? 1
                                                                            : 0;
  __m128i acc_diff = _mm_setzero_si128();
  const __m128i k_0 = _mm_setzero_si128();
  const __m128i k_4 = _mm_set1_epi8(4 + shift_inc);
  const __m128i k_8 = _mm_set1_epi8(8);
  const __m128i k_16 = _mm_set1_epi8(16);
  // Modify each level's adjustment according to motion_magnitude.
  const __m128i l3 = _mm_set1_epi8(
      (motion_magnitude <= kMotionMagnitudeThreshold) ? 7 + shift_inc : 6);
  // Difference between level 3 and level 2 is 2.
  const __m128i l32 = _mm_set1_epi8(2);
  // Difference between level 2 and level 1 is 1.
  const __m128i l21 = _mm_set1_epi8(1);

  for (int r = 0; r < 16; ++r) {
    // Calculate differences.
    const __m128i v_sig =
        _mm_loadu_si128(reinterpret_cast<const __m128i*>(&sig[0]));
    const __m128i v_mc_running_avg_y =
        _mm_loadu_si128(reinterpret_cast<const __m128i*>(&mc_running_avg_y[0]));
    __m128i v_running_avg_y;
    const __m128i pdiff = _mm_subs_epu8(v_mc_running_avg_y, v_sig);
    const __m128i ndiff = _mm_subs_epu8(v_sig, v_mc_running_avg_y);
    // Obtain the sign. FF if diff is negative.
    const __m128i diff_sign = _mm_cmpeq_epi8(pdiff, k_0);
    // Clamp absolute difference to 16 to be used to get mask. Doing this
    // allows us to use _mm_cmpgt_epi8, which operates on signed byte.
    const __m128i clamped_absdiff =
        _mm_min_epu8(_mm_or_si128(pdiff, ndiff), k_16);
    // Get masks for l2 l1 and l0 adjustments.
    const __m128i mask2 = _mm_cmpgt_epi8(k_16, clamped_absdiff);
    const __m128i mask1 = _mm_cmpgt_epi8(k_8, clamped_absdiff);
    const __m128i mask0 = _mm_cmpgt_epi8(k_4, clamped_absdiff);
    // Get adjustments for l2, l1, and l0.
    __m128i adj2 = _mm_and_si128(mask2, l32);
    const __m128i adj1 = _mm_and_si128(mask1, l21);
    const __m128i adj0 = _mm_and_si128(mask0, clamped_absdiff);
    __m128i adj, padj, nadj;

    // Combine the adjustments and get absolute adjustments.
    adj2 = _mm_add_epi8(adj2, adj1);
    adj = _mm_sub_epi8(l3, adj2);
    adj = _mm_andnot_si128(mask0, adj);
    adj = _mm_or_si128(adj, adj0);

    // Restore the sign and get positive and negative adjustments.
    padj = _mm_andnot_si128(diff_sign, adj);
    nadj = _mm_and_si128(diff_sign, adj);

    // Calculate filtered value.
    v_running_avg_y = _mm_adds_epu8(v_sig, padj);
    v_running_avg_y = _mm_subs_epu8(v_running_avg_y, nadj);
    _mm_storeu_si128(reinterpret_cast<__m128i*>(running_avg_y),
                     v_running_avg_y);

    // Adjustments <=7, and each element in acc_diff can fit in signed
    // char.
    acc_diff = _mm_adds_epi8(acc_diff, padj);
    acc_diff = _mm_subs_epi8(acc_diff, nadj);

    // Update pointers for next iteration.
    sig += sig_stride;
    mc_running_avg_y += mc_avg_y_stride;
    running_avg_y += avg_y_stride;
  }

  // Compute the sum of all pixel differences of this MB.
  unsigned int abs_sum_diff = AbsSumDiff16x1(acc_diff);
  sum_diff_thresh =
      increase_denoising ? kSumDiffThresholdHigh : kSumDiffThreshold;
  if (abs_sum_diff > sum_diff_thresh)
    decision = COPY_BLOCK;
  return decision;
}

}  // namespace webrtc
