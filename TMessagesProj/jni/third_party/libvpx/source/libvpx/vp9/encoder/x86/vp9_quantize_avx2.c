/*
 *  Copyright (c) 2017 The WebM project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include <assert.h>
#include <immintrin.h>  // AVX2

#include "./vp9_rtcd.h"
#include "vpx/vpx_integer.h"
#include "vpx_dsp/vpx_dsp_common.h"
#include "vpx_dsp/x86/bitdepth_conversion_avx2.h"
#include "vpx_dsp/x86/quantize_sse2.h"

// Zero fill 8 positions in the output buffer.
static INLINE void store_zero_tran_low(tran_low_t *a) {
  const __m256i zero = _mm256_setzero_si256();
#if CONFIG_VP9_HIGHBITDEPTH
  _mm256_storeu_si256((__m256i *)(a), zero);
  _mm256_storeu_si256((__m256i *)(a + 8), zero);
#else
  _mm256_storeu_si256((__m256i *)(a), zero);
#endif
}

static INLINE __m256i scan_eob_256(const __m256i *iscan_ptr,
                                   __m256i *coeff256) {
  const __m256i iscan = _mm256_loadu_si256(iscan_ptr);
  const __m256i zero256 = _mm256_setzero_si256();
#if CONFIG_VP9_HIGHBITDEPTH
  // The _mm256_packs_epi32() in load_tran_low() packs the 64 bit coeff as
  // B1 A1 B0 A0.  Shuffle to B1 B0 A1 A0 in order to scan eob correctly.
  const __m256i _coeff256 = _mm256_permute4x64_epi64(*coeff256, 0xd8);
  const __m256i zero_coeff0 = _mm256_cmpeq_epi16(_coeff256, zero256);
#else
  const __m256i zero_coeff0 = _mm256_cmpeq_epi16(*coeff256, zero256);
#endif
  const __m256i nzero_coeff0 = _mm256_cmpeq_epi16(zero_coeff0, zero256);
  // Add one to convert from indices to counts
  const __m256i iscan_plus_one = _mm256_sub_epi16(iscan, nzero_coeff0);
  return _mm256_and_si256(iscan_plus_one, nzero_coeff0);
}

void vp9_quantize_fp_avx2(const tran_low_t *coeff_ptr, intptr_t n_coeffs,
                          int skip_block, const int16_t *round_ptr,
                          const int16_t *quant_ptr, tran_low_t *qcoeff_ptr,
                          tran_low_t *dqcoeff_ptr, const int16_t *dequant_ptr,
                          uint16_t *eob_ptr, const int16_t *scan,
                          const int16_t *iscan) {
  __m128i eob;
  __m256i round256, quant256, dequant256;
  __m256i eob256, thr256;

  (void)scan;
  (void)skip_block;
  assert(!skip_block);

  coeff_ptr += n_coeffs;
  iscan += n_coeffs;
  qcoeff_ptr += n_coeffs;
  dqcoeff_ptr += n_coeffs;
  n_coeffs = -n_coeffs;

  {
    __m256i coeff256;

    // Setup global values
    {
      const __m128i round = _mm_load_si128((const __m128i *)round_ptr);
      const __m128i quant = _mm_load_si128((const __m128i *)quant_ptr);
      const __m128i dequant = _mm_load_si128((const __m128i *)dequant_ptr);
      round256 = _mm256_castsi128_si256(round);
      round256 = _mm256_permute4x64_epi64(round256, 0x54);

      quant256 = _mm256_castsi128_si256(quant);
      quant256 = _mm256_permute4x64_epi64(quant256, 0x54);

      dequant256 = _mm256_castsi128_si256(dequant);
      dequant256 = _mm256_permute4x64_epi64(dequant256, 0x54);
    }

    {
      __m256i qcoeff256;
      __m256i qtmp256;
      coeff256 = load_tran_low(coeff_ptr + n_coeffs);
      qcoeff256 = _mm256_abs_epi16(coeff256);
      qcoeff256 = _mm256_adds_epi16(qcoeff256, round256);
      qtmp256 = _mm256_mulhi_epi16(qcoeff256, quant256);
      qcoeff256 = _mm256_sign_epi16(qtmp256, coeff256);
      store_tran_low(qcoeff256, qcoeff_ptr + n_coeffs);
      coeff256 = _mm256_mullo_epi16(qcoeff256, dequant256);
      store_tran_low(coeff256, dqcoeff_ptr + n_coeffs);
    }

    eob256 = scan_eob_256((const __m256i *)(iscan + n_coeffs), &coeff256);
    n_coeffs += 8 * 2;
  }

  // remove dc constants
  dequant256 = _mm256_permute2x128_si256(dequant256, dequant256, 0x31);
  quant256 = _mm256_permute2x128_si256(quant256, quant256, 0x31);
  round256 = _mm256_permute2x128_si256(round256, round256, 0x31);

  thr256 = _mm256_srai_epi16(dequant256, 1);

  // AC only loop
  while (n_coeffs < 0) {
    __m256i coeff256 = load_tran_low(coeff_ptr + n_coeffs);
    __m256i qcoeff256 = _mm256_abs_epi16(coeff256);
    int32_t nzflag =
        _mm256_movemask_epi8(_mm256_cmpgt_epi16(qcoeff256, thr256));

    if (nzflag) {
      __m256i qtmp256;
      qcoeff256 = _mm256_adds_epi16(qcoeff256, round256);
      qtmp256 = _mm256_mulhi_epi16(qcoeff256, quant256);
      qcoeff256 = _mm256_sign_epi16(qtmp256, coeff256);
      store_tran_low(qcoeff256, qcoeff_ptr + n_coeffs);
      coeff256 = _mm256_mullo_epi16(qcoeff256, dequant256);
      store_tran_low(coeff256, dqcoeff_ptr + n_coeffs);
      eob256 = _mm256_max_epi16(
          eob256, scan_eob_256((const __m256i *)(iscan + n_coeffs), &coeff256));
    } else {
      store_zero_tran_low(qcoeff_ptr + n_coeffs);
      store_zero_tran_low(dqcoeff_ptr + n_coeffs);
    }
    n_coeffs += 8 * 2;
  }

  eob = _mm_max_epi16(_mm256_castsi256_si128(eob256),
                      _mm256_extracti128_si256(eob256, 1));

  *eob_ptr = accumulate_eob(eob);
}
