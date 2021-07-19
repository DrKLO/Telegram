/*
 *  Copyright (c) 2017 The WebM project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#ifndef VPX_VPX_DSP_X86_QUANTIZE_SSE2_H_
#define VPX_VPX_DSP_X86_QUANTIZE_SSE2_H_

#include <emmintrin.h>

#include "./vpx_config.h"
#include "vpx/vpx_integer.h"

static INLINE void load_b_values(const int16_t *zbin_ptr, __m128i *zbin,
                                 const int16_t *round_ptr, __m128i *round,
                                 const int16_t *quant_ptr, __m128i *quant,
                                 const int16_t *dequant_ptr, __m128i *dequant,
                                 const int16_t *shift_ptr, __m128i *shift) {
  *zbin = _mm_load_si128((const __m128i *)zbin_ptr);
  *round = _mm_load_si128((const __m128i *)round_ptr);
  *quant = _mm_load_si128((const __m128i *)quant_ptr);
  *zbin = _mm_sub_epi16(*zbin, _mm_set1_epi16(1));
  *dequant = _mm_load_si128((const __m128i *)dequant_ptr);
  *shift = _mm_load_si128((const __m128i *)shift_ptr);
}

// With ssse3 and later abs() and sign() are preferred.
static INLINE __m128i invert_sign_sse2(__m128i a, __m128i sign) {
  a = _mm_xor_si128(a, sign);
  return _mm_sub_epi16(a, sign);
}

static INLINE void calculate_qcoeff(__m128i *coeff, const __m128i round,
                                    const __m128i quant, const __m128i shift) {
  __m128i tmp, qcoeff;
  qcoeff = _mm_adds_epi16(*coeff, round);
  tmp = _mm_mulhi_epi16(qcoeff, quant);
  qcoeff = _mm_add_epi16(tmp, qcoeff);
  *coeff = _mm_mulhi_epi16(qcoeff, shift);
}

static INLINE void calculate_dqcoeff_and_store(__m128i qcoeff, __m128i dequant,
                                               tran_low_t *dqcoeff) {
#if CONFIG_VP9_HIGHBITDEPTH
  const __m128i low = _mm_mullo_epi16(qcoeff, dequant);
  const __m128i high = _mm_mulhi_epi16(qcoeff, dequant);

  const __m128i dqcoeff32_0 = _mm_unpacklo_epi16(low, high);
  const __m128i dqcoeff32_1 = _mm_unpackhi_epi16(low, high);

  _mm_store_si128((__m128i *)(dqcoeff), dqcoeff32_0);
  _mm_store_si128((__m128i *)(dqcoeff + 4), dqcoeff32_1);
#else
  const __m128i dqcoeff16 = _mm_mullo_epi16(qcoeff, dequant);

  _mm_store_si128((__m128i *)(dqcoeff), dqcoeff16);
#endif  // CONFIG_VP9_HIGHBITDEPTH
}

// Scan 16 values for eob reference in scan. Use masks (-1) from comparing to
// zbin to add 1 to the index in 'scan'.
static INLINE __m128i scan_for_eob(__m128i *coeff0, __m128i *coeff1,
                                   const __m128i zbin_mask0,
                                   const __m128i zbin_mask1,
                                   const int16_t *scan, const int index,
                                   const __m128i zero) {
  const __m128i zero_coeff0 = _mm_cmpeq_epi16(*coeff0, zero);
  const __m128i zero_coeff1 = _mm_cmpeq_epi16(*coeff1, zero);
  __m128i scan0 = _mm_load_si128((const __m128i *)(scan + index));
  __m128i scan1 = _mm_load_si128((const __m128i *)(scan + index + 8));
  __m128i eob0, eob1;
  // Add one to convert from indices to counts
  scan0 = _mm_sub_epi16(scan0, zbin_mask0);
  scan1 = _mm_sub_epi16(scan1, zbin_mask1);
  eob0 = _mm_andnot_si128(zero_coeff0, scan0);
  eob1 = _mm_andnot_si128(zero_coeff1, scan1);
  return _mm_max_epi16(eob0, eob1);
}

static INLINE int16_t accumulate_eob(__m128i eob) {
  __m128i eob_shuffled;
  eob_shuffled = _mm_shuffle_epi32(eob, 0xe);
  eob = _mm_max_epi16(eob, eob_shuffled);
  eob_shuffled = _mm_shufflelo_epi16(eob, 0xe);
  eob = _mm_max_epi16(eob, eob_shuffled);
  eob_shuffled = _mm_shufflelo_epi16(eob, 0x1);
  eob = _mm_max_epi16(eob, eob_shuffled);
  return _mm_extract_epi16(eob, 1);
}

#endif  // VPX_VPX_DSP_X86_QUANTIZE_SSE2_H_
