/*
 *  Copyright (c) 2020 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include <immintrin.h>

#include "modules/audio_processing/aec3/matched_filter.h"
#include "rtc_base/checks.h"

namespace webrtc {
namespace aec3 {

// Let ha denote the horizontal of a, and hb the horizontal sum of b
// returns [ha, hb, ha, hb]
inline __m128 hsum_ab(__m256 a, __m256 b) {
  __m256 s_256 = _mm256_hadd_ps(a, b);
  const __m256i mask = _mm256_set_epi32(7, 6, 3, 2, 5, 4, 1, 0);
  s_256 = _mm256_permutevar8x32_ps(s_256, mask);
  __m128 s = _mm_hadd_ps(_mm256_extractf128_ps(s_256, 0),
                         _mm256_extractf128_ps(s_256, 1));
  s = _mm_hadd_ps(s, s);
  return s;
}

void MatchedFilterCore_AccumulatedError_AVX2(
    size_t x_start_index,
    float x2_sum_threshold,
    float smoothing,
    rtc::ArrayView<const float> x,
    rtc::ArrayView<const float> y,
    rtc::ArrayView<float> h,
    bool* filters_updated,
    float* error_sum,
    rtc::ArrayView<float> accumulated_error,
    rtc::ArrayView<float> scratch_memory) {
  const int h_size = static_cast<int>(h.size());
  const int x_size = static_cast<int>(x.size());
  RTC_DCHECK_EQ(0, h_size % 16);
  std::fill(accumulated_error.begin(), accumulated_error.end(), 0.0f);

  // Process for all samples in the sub-block.
  for (size_t i = 0; i < y.size(); ++i) {
    // Apply the matched filter as filter * x, and compute x * x.
    RTC_DCHECK_GT(x_size, x_start_index);
    const int chunk1 =
        std::min(h_size, static_cast<int>(x_size - x_start_index));
    if (chunk1 != h_size) {
      const int chunk2 = h_size - chunk1;
      std::copy(x.begin() + x_start_index, x.end(), scratch_memory.begin());
      std::copy(x.begin(), x.begin() + chunk2, scratch_memory.begin() + chunk1);
    }
    const float* x_p =
        chunk1 != h_size ? scratch_memory.data() : &x[x_start_index];
    const float* h_p = &h[0];
    float* a_p = &accumulated_error[0];
    __m256 s_inst_hadd_256;
    __m256 s_inst_256;
    __m256 s_inst_256_8;
    __m256 x2_sum_256 = _mm256_set1_ps(0);
    __m256 x2_sum_256_8 = _mm256_set1_ps(0);
    __m128 e_128;
    float x2_sum = 0.0f;
    float s_acum = 0;
    const int limit_by_16 = h_size >> 4;
    for (int k = limit_by_16; k > 0; --k, h_p += 16, x_p += 16, a_p += 4) {
      // Load the data into 256 bit vectors.
      __m256 x_k = _mm256_loadu_ps(x_p);
      __m256 h_k = _mm256_loadu_ps(h_p);
      __m256 x_k_8 = _mm256_loadu_ps(x_p + 8);
      __m256 h_k_8 = _mm256_loadu_ps(h_p + 8);
      // Compute and accumulate x * x and h * x.
      x2_sum_256 = _mm256_fmadd_ps(x_k, x_k, x2_sum_256);
      x2_sum_256_8 = _mm256_fmadd_ps(x_k_8, x_k_8, x2_sum_256_8);
      s_inst_256 = _mm256_mul_ps(h_k, x_k);
      s_inst_256_8 = _mm256_mul_ps(h_k_8, x_k_8);
      s_inst_hadd_256 = _mm256_hadd_ps(s_inst_256, s_inst_256_8);
      s_inst_hadd_256 = _mm256_hadd_ps(s_inst_hadd_256, s_inst_hadd_256);
      s_acum += s_inst_hadd_256[0];
      e_128[0] = s_acum - y[i];
      s_acum += s_inst_hadd_256[4];
      e_128[1] = s_acum - y[i];
      s_acum += s_inst_hadd_256[1];
      e_128[2] = s_acum - y[i];
      s_acum += s_inst_hadd_256[5];
      e_128[3] = s_acum - y[i];

      __m128 accumulated_error = _mm_load_ps(a_p);
      accumulated_error = _mm_fmadd_ps(e_128, e_128, accumulated_error);
      _mm_storeu_ps(a_p, accumulated_error);
    }
    // Sum components together.
    x2_sum_256 = _mm256_add_ps(x2_sum_256, x2_sum_256_8);
    __m128 x2_sum_128 = _mm_add_ps(_mm256_extractf128_ps(x2_sum_256, 0),
                                   _mm256_extractf128_ps(x2_sum_256, 1));
    // Combine the accumulated vector and scalar values.
    float* v = reinterpret_cast<float*>(&x2_sum_128);
    x2_sum += v[0] + v[1] + v[2] + v[3];

    // Compute the matched filter error.
    float e = y[i] - s_acum;
    const bool saturation = y[i] >= 32000.f || y[i] <= -32000.f;
    (*error_sum) += e * e;

    // Update the matched filter estimate in an NLMS manner.
    if (x2_sum > x2_sum_threshold && !saturation) {
      RTC_DCHECK_LT(0.f, x2_sum);
      const float alpha = smoothing * e / x2_sum;
      const __m256 alpha_256 = _mm256_set1_ps(alpha);

      // filter = filter + smoothing * (y - filter * x) * x / x * x.
      float* h_p = &h[0];
      const float* x_p =
          chunk1 != h_size ? scratch_memory.data() : &x[x_start_index];
      // Perform 256 bit vector operations.
      const int limit_by_8 = h_size >> 3;
      for (int k = limit_by_8; k > 0; --k, h_p += 8, x_p += 8) {
        // Load the data into 256 bit vectors.
        __m256 h_k = _mm256_loadu_ps(h_p);
        __m256 x_k = _mm256_loadu_ps(x_p);
        // Compute h = h + alpha * x.
        h_k = _mm256_fmadd_ps(x_k, alpha_256, h_k);

        // Store the result.
        _mm256_storeu_ps(h_p, h_k);
      }
      *filters_updated = true;
    }

    x_start_index = x_start_index > 0 ? x_start_index - 1 : x_size - 1;
  }
}

void MatchedFilterCore_AVX2(size_t x_start_index,
                            float x2_sum_threshold,
                            float smoothing,
                            rtc::ArrayView<const float> x,
                            rtc::ArrayView<const float> y,
                            rtc::ArrayView<float> h,
                            bool* filters_updated,
                            float* error_sum,
                            bool compute_accumulated_error,
                            rtc::ArrayView<float> accumulated_error,
                            rtc::ArrayView<float> scratch_memory) {
  if (compute_accumulated_error) {
    return MatchedFilterCore_AccumulatedError_AVX2(
        x_start_index, x2_sum_threshold, smoothing, x, y, h, filters_updated,
        error_sum, accumulated_error, scratch_memory);
  }
  const int h_size = static_cast<int>(h.size());
  const int x_size = static_cast<int>(x.size());
  RTC_DCHECK_EQ(0, h_size % 8);

  // Process for all samples in the sub-block.
  for (size_t i = 0; i < y.size(); ++i) {
    // Apply the matched filter as filter * x, and compute x * x.

    RTC_DCHECK_GT(x_size, x_start_index);
    const float* x_p = &x[x_start_index];
    const float* h_p = &h[0];

    // Initialize values for the accumulation.
    __m256 s_256 = _mm256_set1_ps(0);
    __m256 s_256_8 = _mm256_set1_ps(0);
    __m256 x2_sum_256 = _mm256_set1_ps(0);
    __m256 x2_sum_256_8 = _mm256_set1_ps(0);
    float x2_sum = 0.f;
    float s = 0;

    // Compute loop chunk sizes until, and after, the wraparound of the circular
    // buffer for x.
    const int chunk1 =
        std::min(h_size, static_cast<int>(x_size - x_start_index));

    // Perform the loop in two chunks.
    const int chunk2 = h_size - chunk1;
    for (int limit : {chunk1, chunk2}) {
      // Perform 256 bit vector operations.
      const int limit_by_16 = limit >> 4;
      for (int k = limit_by_16; k > 0; --k, h_p += 16, x_p += 16) {
        // Load the data into 256 bit vectors.
        __m256 x_k = _mm256_loadu_ps(x_p);
        __m256 h_k = _mm256_loadu_ps(h_p);
        __m256 x_k_8 = _mm256_loadu_ps(x_p + 8);
        __m256 h_k_8 = _mm256_loadu_ps(h_p + 8);
        // Compute and accumulate x * x and h * x.
        x2_sum_256 = _mm256_fmadd_ps(x_k, x_k, x2_sum_256);
        x2_sum_256_8 = _mm256_fmadd_ps(x_k_8, x_k_8, x2_sum_256_8);
        s_256 = _mm256_fmadd_ps(h_k, x_k, s_256);
        s_256_8 = _mm256_fmadd_ps(h_k_8, x_k_8, s_256_8);
      }

      // Perform non-vector operations for any remaining items.
      for (int k = limit - limit_by_16 * 16; k > 0; --k, ++h_p, ++x_p) {
        const float x_k = *x_p;
        x2_sum += x_k * x_k;
        s += *h_p * x_k;
      }

      x_p = &x[0];
    }

    // Sum components together.
    x2_sum_256 = _mm256_add_ps(x2_sum_256, x2_sum_256_8);
    s_256 = _mm256_add_ps(s_256, s_256_8);
    __m128 sum = hsum_ab(x2_sum_256, s_256);
    x2_sum += sum[0];
    s += sum[1];

    // Compute the matched filter error.
    float e = y[i] - s;
    const bool saturation = y[i] >= 32000.f || y[i] <= -32000.f;
    (*error_sum) += e * e;

    // Update the matched filter estimate in an NLMS manner.
    if (x2_sum > x2_sum_threshold && !saturation) {
      RTC_DCHECK_LT(0.f, x2_sum);
      const float alpha = smoothing * e / x2_sum;
      const __m256 alpha_256 = _mm256_set1_ps(alpha);

      // filter = filter + smoothing * (y - filter * x) * x / x * x.
      float* h_p = &h[0];
      x_p = &x[x_start_index];

      // Perform the loop in two chunks.
      for (int limit : {chunk1, chunk2}) {
        // Perform 256 bit vector operations.
        const int limit_by_8 = limit >> 3;
        for (int k = limit_by_8; k > 0; --k, h_p += 8, x_p += 8) {
          // Load the data into 256 bit vectors.
          __m256 h_k = _mm256_loadu_ps(h_p);
          __m256 x_k = _mm256_loadu_ps(x_p);
          // Compute h = h + alpha * x.
          h_k = _mm256_fmadd_ps(x_k, alpha_256, h_k);

          // Store the result.
          _mm256_storeu_ps(h_p, h_k);
        }

        // Perform non-vector operations for any remaining items.
        for (int k = limit - limit_by_8 * 8; k > 0; --k, ++h_p, ++x_p) {
          *h_p += alpha * *x_p;
        }

        x_p = &x[0];
      }

      *filters_updated = true;
    }

    x_start_index = x_start_index > 0 ? x_start_index - 1 : x_size - 1;
  }
}

}  // namespace aec3
}  // namespace webrtc
