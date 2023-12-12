/*
 *  Copyright (c) 2017 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */
#include "modules/audio_processing/aec3/matched_filter.h"

// Defines WEBRTC_ARCH_X86_FAMILY, used below.
#include "rtc_base/system/arch.h"

#if defined(WEBRTC_HAS_NEON)
#include <arm_neon.h>
#endif
#if defined(WEBRTC_ARCH_X86_FAMILY)
#include <emmintrin.h>
#endif
#include <algorithm>
#include <cstddef>
#include <initializer_list>
#include <iterator>
#include <numeric>

#include "absl/types/optional.h"
#include "api/array_view.h"
#include "modules/audio_processing/aec3/downsampled_render_buffer.h"
#include "modules/audio_processing/logging/apm_data_dumper.h"
#include "rtc_base/checks.h"
#include "rtc_base/logging.h"

namespace {

// Subsample rate used for computing the accumulated error.
// The implementation of some core functions depends on this constant being
// equal to 4.
constexpr int kAccumulatedErrorSubSampleRate = 4;

void UpdateAccumulatedError(
    const rtc::ArrayView<const float> instantaneous_accumulated_error,
    const rtc::ArrayView<float> accumulated_error,
    float one_over_error_sum_anchor) {
  for (size_t k = 0; k < instantaneous_accumulated_error.size(); ++k) {
    float error_norm =
        instantaneous_accumulated_error[k] * one_over_error_sum_anchor;
    if (error_norm < accumulated_error[k]) {
      accumulated_error[k] = error_norm;
    } else {
      accumulated_error[k] += 0.01f * (error_norm - accumulated_error[k]);
    }
  }
}

size_t ComputePreEchoLag(const rtc::ArrayView<float> accumulated_error,
                         size_t lag,
                         size_t alignment_shift_winner) {
  size_t pre_echo_lag_estimate = lag - alignment_shift_winner;
  size_t maximum_pre_echo_lag =
      std::min(pre_echo_lag_estimate / kAccumulatedErrorSubSampleRate,
               accumulated_error.size());
  for (size_t k = 1; k < maximum_pre_echo_lag; ++k) {
    if (accumulated_error[k] < 0.5f * accumulated_error[k - 1] &&
        accumulated_error[k] < 0.5f) {
      pre_echo_lag_estimate = (k + 1) * kAccumulatedErrorSubSampleRate - 1;
      break;
    }
  }
  return pre_echo_lag_estimate + alignment_shift_winner;
}

}  // namespace

namespace webrtc {
namespace aec3 {

#if defined(WEBRTC_HAS_NEON)

inline float SumAllElements(float32x4_t elements) {
  float32x2_t sum = vpadd_f32(vget_low_f32(elements), vget_high_f32(elements));
  sum = vpadd_f32(sum, sum);
  return vget_lane_f32(sum, 0);
}

void MatchedFilterCoreWithAccumulatedError_NEON(
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
  RTC_DCHECK_EQ(0, h_size % 4);
  std::fill(accumulated_error.begin(), accumulated_error.end(), 0.0f);
  // Process for all samples in the sub-block.
  for (size_t i = 0; i < y.size(); ++i) {
    // Apply the matched filter as filter * x, and compute x * x.
    RTC_DCHECK_GT(x_size, x_start_index);
    // Compute loop chunk sizes until, and after, the wraparound of the circular
    // buffer for x.
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
    float* accumulated_error_p = &accumulated_error[0];
    // Initialize values for the accumulation.
    float32x4_t x2_sum_128 = vdupq_n_f32(0);
    float x2_sum = 0.f;
    float s = 0;
    // Perform 128 bit vector operations.
    const int limit_by_4 = h_size >> 2;
    for (int k = limit_by_4; k > 0;
         --k, h_p += 4, x_p += 4, accumulated_error_p++) {
      // Load the data into 128 bit vectors.
      const float32x4_t x_k = vld1q_f32(x_p);
      const float32x4_t h_k = vld1q_f32(h_p);
      // Compute and accumulate x * x.
      x2_sum_128 = vmlaq_f32(x2_sum_128, x_k, x_k);
      // Compute x * h
      float32x4_t hk_xk_128 = vmulq_f32(h_k, x_k);
      s += SumAllElements(hk_xk_128);
      const float e = s - y[i];
      accumulated_error_p[0] += e * e;
    }
    // Combine the accumulated vector and scalar values.
    x2_sum += SumAllElements(x2_sum_128);
    // Compute the matched filter error.
    float e = y[i] - s;
    const bool saturation = y[i] >= 32000.f || y[i] <= -32000.f;
    (*error_sum) += e * e;
    // Update the matched filter estimate in an NLMS manner.
    if (x2_sum > x2_sum_threshold && !saturation) {
      RTC_DCHECK_LT(0.f, x2_sum);
      const float alpha = smoothing * e / x2_sum;
      const float32x4_t alpha_128 = vmovq_n_f32(alpha);
      // filter = filter + smoothing * (y - filter * x) * x / x * x.
      float* h_p = &h[0];
      x_p = chunk1 != h_size ? scratch_memory.data() : &x[x_start_index];
      // Perform 128 bit vector operations.
      const int limit_by_4 = h_size >> 2;
      for (int k = limit_by_4; k > 0; --k, h_p += 4, x_p += 4) {
        // Load the data into 128 bit vectors.
        float32x4_t h_k = vld1q_f32(h_p);
        const float32x4_t x_k = vld1q_f32(x_p);
        // Compute h = h + alpha * x.
        h_k = vmlaq_f32(h_k, alpha_128, x_k);
        // Store the result.
        vst1q_f32(h_p, h_k);
      }
      *filters_updated = true;
    }
    x_start_index = x_start_index > 0 ? x_start_index - 1 : x_size - 1;
  }
}

void MatchedFilterCore_NEON(size_t x_start_index,
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
  const int h_size = static_cast<int>(h.size());
  const int x_size = static_cast<int>(x.size());
  RTC_DCHECK_EQ(0, h_size % 4);

  if (compute_accumulated_error) {
    return MatchedFilterCoreWithAccumulatedError_NEON(
        x_start_index, x2_sum_threshold, smoothing, x, y, h, filters_updated,
        error_sum, accumulated_error, scratch_memory);
  }

  // Process for all samples in the sub-block.
  for (size_t i = 0; i < y.size(); ++i) {
    // Apply the matched filter as filter * x, and compute x * x.

    RTC_DCHECK_GT(x_size, x_start_index);
    const float* x_p = &x[x_start_index];
    const float* h_p = &h[0];

    // Initialize values for the accumulation.
    float32x4_t s_128 = vdupq_n_f32(0);
    float32x4_t x2_sum_128 = vdupq_n_f32(0);
    float x2_sum = 0.f;
    float s = 0;

    // Compute loop chunk sizes until, and after, the wraparound of the circular
    // buffer for x.
    const int chunk1 =
        std::min(h_size, static_cast<int>(x_size - x_start_index));

    // Perform the loop in two chunks.
    const int chunk2 = h_size - chunk1;
    for (int limit : {chunk1, chunk2}) {
      // Perform 128 bit vector operations.
      const int limit_by_4 = limit >> 2;
      for (int k = limit_by_4; k > 0; --k, h_p += 4, x_p += 4) {
        // Load the data into 128 bit vectors.
        const float32x4_t x_k = vld1q_f32(x_p);
        const float32x4_t h_k = vld1q_f32(h_p);
        // Compute and accumulate x * x and h * x.
        x2_sum_128 = vmlaq_f32(x2_sum_128, x_k, x_k);
        s_128 = vmlaq_f32(s_128, h_k, x_k);
      }

      // Perform non-vector operations for any remaining items.
      for (int k = limit - limit_by_4 * 4; k > 0; --k, ++h_p, ++x_p) {
        const float x_k = *x_p;
        x2_sum += x_k * x_k;
        s += *h_p * x_k;
      }

      x_p = &x[0];
    }

    // Combine the accumulated vector and scalar values.
    s += SumAllElements(s_128);
    x2_sum += SumAllElements(x2_sum_128);

    // Compute the matched filter error.
    float e = y[i] - s;
    const bool saturation = y[i] >= 32000.f || y[i] <= -32000.f;
    (*error_sum) += e * e;

    // Update the matched filter estimate in an NLMS manner.
    if (x2_sum > x2_sum_threshold && !saturation) {
      RTC_DCHECK_LT(0.f, x2_sum);
      const float alpha = smoothing * e / x2_sum;
      const float32x4_t alpha_128 = vmovq_n_f32(alpha);

      // filter = filter + smoothing * (y - filter * x) * x / x * x.
      float* h_p = &h[0];
      x_p = &x[x_start_index];

      // Perform the loop in two chunks.
      for (int limit : {chunk1, chunk2}) {
        // Perform 128 bit vector operations.
        const int limit_by_4 = limit >> 2;
        for (int k = limit_by_4; k > 0; --k, h_p += 4, x_p += 4) {
          // Load the data into 128 bit vectors.
          float32x4_t h_k = vld1q_f32(h_p);
          const float32x4_t x_k = vld1q_f32(x_p);
          // Compute h = h + alpha * x.
          h_k = vmlaq_f32(h_k, alpha_128, x_k);

          // Store the result.
          vst1q_f32(h_p, h_k);
        }

        // Perform non-vector operations for any remaining items.
        for (int k = limit - limit_by_4 * 4; k > 0; --k, ++h_p, ++x_p) {
          *h_p += alpha * *x_p;
        }

        x_p = &x[0];
      }

      *filters_updated = true;
    }

    x_start_index = x_start_index > 0 ? x_start_index - 1 : x_size - 1;
  }
}

#endif

#if defined(WEBRTC_ARCH_X86_FAMILY)

void MatchedFilterCore_AccumulatedError_SSE2(
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
  RTC_DCHECK_EQ(0, h_size % 8);
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
    __m128 s_inst_128;
    __m128 s_inst_128_4;
    __m128 x2_sum_128 = _mm_set1_ps(0);
    __m128 x2_sum_128_4 = _mm_set1_ps(0);
    __m128 e_128;
    float* const s_p = reinterpret_cast<float*>(&s_inst_128);
    float* const s_4_p = reinterpret_cast<float*>(&s_inst_128_4);
    float* const e_p = reinterpret_cast<float*>(&e_128);
    float x2_sum = 0.0f;
    float s_acum = 0;
    // Perform 128 bit vector operations.
    const int limit_by_8 = h_size >> 3;
    for (int k = limit_by_8; k > 0; --k, h_p += 8, x_p += 8, a_p += 2) {
      // Load the data into 128 bit vectors.
      const __m128 x_k = _mm_loadu_ps(x_p);
      const __m128 h_k = _mm_loadu_ps(h_p);
      const __m128 x_k_4 = _mm_loadu_ps(x_p + 4);
      const __m128 h_k_4 = _mm_loadu_ps(h_p + 4);
      const __m128 xx = _mm_mul_ps(x_k, x_k);
      const __m128 xx_4 = _mm_mul_ps(x_k_4, x_k_4);
      // Compute and accumulate x * x and h * x.
      x2_sum_128 = _mm_add_ps(x2_sum_128, xx);
      x2_sum_128_4 = _mm_add_ps(x2_sum_128_4, xx_4);
      s_inst_128 = _mm_mul_ps(h_k, x_k);
      s_inst_128_4 = _mm_mul_ps(h_k_4, x_k_4);
      s_acum += s_p[0] + s_p[1] + s_p[2] + s_p[3];
      e_p[0] = s_acum - y[i];
      s_acum += s_4_p[0] + s_4_p[1] + s_4_p[2] + s_4_p[3];
      e_p[1] = s_acum - y[i];
      a_p[0] += e_p[0] * e_p[0];
      a_p[1] += e_p[1] * e_p[1];
    }
    // Combine the accumulated vector and scalar values.
    x2_sum_128 = _mm_add_ps(x2_sum_128, x2_sum_128_4);
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
      const __m128 alpha_128 = _mm_set1_ps(alpha);
      // filter = filter + smoothing * (y - filter * x) * x / x * x.
      float* h_p = &h[0];
      const float* x_p =
          chunk1 != h_size ? scratch_memory.data() : &x[x_start_index];
      // Perform 128 bit vector operations.
      const int limit_by_4 = h_size >> 2;
      for (int k = limit_by_4; k > 0; --k, h_p += 4, x_p += 4) {
        // Load the data into 128 bit vectors.
        __m128 h_k = _mm_loadu_ps(h_p);
        const __m128 x_k = _mm_loadu_ps(x_p);
        // Compute h = h + alpha * x.
        const __m128 alpha_x = _mm_mul_ps(alpha_128, x_k);
        h_k = _mm_add_ps(h_k, alpha_x);
        // Store the result.
        _mm_storeu_ps(h_p, h_k);
      }
      *filters_updated = true;
    }
    x_start_index = x_start_index > 0 ? x_start_index - 1 : x_size - 1;
  }
}

void MatchedFilterCore_SSE2(size_t x_start_index,
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
    return MatchedFilterCore_AccumulatedError_SSE2(
        x_start_index, x2_sum_threshold, smoothing, x, y, h, filters_updated,
        error_sum, accumulated_error, scratch_memory);
  }
  const int h_size = static_cast<int>(h.size());
  const int x_size = static_cast<int>(x.size());
  RTC_DCHECK_EQ(0, h_size % 4);
  // Process for all samples in the sub-block.
  for (size_t i = 0; i < y.size(); ++i) {
    // Apply the matched filter as filter * x, and compute x * x.
    RTC_DCHECK_GT(x_size, x_start_index);
    const float* x_p = &x[x_start_index];
    const float* h_p = &h[0];
    // Initialize values for the accumulation.
    __m128 s_128 = _mm_set1_ps(0);
    __m128 s_128_4 = _mm_set1_ps(0);
    __m128 x2_sum_128 = _mm_set1_ps(0);
    __m128 x2_sum_128_4 = _mm_set1_ps(0);
    float x2_sum = 0.f;
    float s = 0;
    // Compute loop chunk sizes until, and after, the wraparound of the circular
    // buffer for x.
    const int chunk1 =
        std::min(h_size, static_cast<int>(x_size - x_start_index));
    // Perform the loop in two chunks.
    const int chunk2 = h_size - chunk1;
    for (int limit : {chunk1, chunk2}) {
      // Perform 128 bit vector operations.
      const int limit_by_8 = limit >> 3;
      for (int k = limit_by_8; k > 0; --k, h_p += 8, x_p += 8) {
        // Load the data into 128 bit vectors.
        const __m128 x_k = _mm_loadu_ps(x_p);
        const __m128 h_k = _mm_loadu_ps(h_p);
        const __m128 x_k_4 = _mm_loadu_ps(x_p + 4);
        const __m128 h_k_4 = _mm_loadu_ps(h_p + 4);
        const __m128 xx = _mm_mul_ps(x_k, x_k);
        const __m128 xx_4 = _mm_mul_ps(x_k_4, x_k_4);
        // Compute and accumulate x * x and h * x.
        x2_sum_128 = _mm_add_ps(x2_sum_128, xx);
        x2_sum_128_4 = _mm_add_ps(x2_sum_128_4, xx_4);
        const __m128 hx = _mm_mul_ps(h_k, x_k);
        const __m128 hx_4 = _mm_mul_ps(h_k_4, x_k_4);
        s_128 = _mm_add_ps(s_128, hx);
        s_128_4 = _mm_add_ps(s_128_4, hx_4);
      }
      // Perform non-vector operations for any remaining items.
      for (int k = limit - limit_by_8 * 8; k > 0; --k, ++h_p, ++x_p) {
        const float x_k = *x_p;
        x2_sum += x_k * x_k;
        s += *h_p * x_k;
      }
      x_p = &x[0];
    }
    // Combine the accumulated vector and scalar values.
    x2_sum_128 = _mm_add_ps(x2_sum_128, x2_sum_128_4);
    float* v = reinterpret_cast<float*>(&x2_sum_128);
    x2_sum += v[0] + v[1] + v[2] + v[3];
    s_128 = _mm_add_ps(s_128, s_128_4);
    v = reinterpret_cast<float*>(&s_128);
    s += v[0] + v[1] + v[2] + v[3];
    // Compute the matched filter error.
    float e = y[i] - s;
    const bool saturation = y[i] >= 32000.f || y[i] <= -32000.f;
    (*error_sum) += e * e;
    // Update the matched filter estimate in an NLMS manner.
    if (x2_sum > x2_sum_threshold && !saturation) {
      RTC_DCHECK_LT(0.f, x2_sum);
      const float alpha = smoothing * e / x2_sum;
      const __m128 alpha_128 = _mm_set1_ps(alpha);
      // filter = filter + smoothing * (y - filter * x) * x / x * x.
      float* h_p = &h[0];
      x_p = &x[x_start_index];
      // Perform the loop in two chunks.
      for (int limit : {chunk1, chunk2}) {
        // Perform 128 bit vector operations.
        const int limit_by_4 = limit >> 2;
        for (int k = limit_by_4; k > 0; --k, h_p += 4, x_p += 4) {
          // Load the data into 128 bit vectors.
          __m128 h_k = _mm_loadu_ps(h_p);
          const __m128 x_k = _mm_loadu_ps(x_p);

          // Compute h = h + alpha * x.
          const __m128 alpha_x = _mm_mul_ps(alpha_128, x_k);
          h_k = _mm_add_ps(h_k, alpha_x);
          // Store the result.
          _mm_storeu_ps(h_p, h_k);
        }
        // Perform non-vector operations for any remaining items.
        for (int k = limit - limit_by_4 * 4; k > 0; --k, ++h_p, ++x_p) {
          *h_p += alpha * *x_p;
        }
        x_p = &x[0];
      }
      *filters_updated = true;
    }
    x_start_index = x_start_index > 0 ? x_start_index - 1 : x_size - 1;
  }
}
#endif

void MatchedFilterCore(size_t x_start_index,
                       float x2_sum_threshold,
                       float smoothing,
                       rtc::ArrayView<const float> x,
                       rtc::ArrayView<const float> y,
                       rtc::ArrayView<float> h,
                       bool* filters_updated,
                       float* error_sum,
                       bool compute_accumulated_error,
                       rtc::ArrayView<float> accumulated_error) {
  if (compute_accumulated_error) {
    std::fill(accumulated_error.begin(), accumulated_error.end(), 0.0f);
  }

  // Process for all samples in the sub-block.
  for (size_t i = 0; i < y.size(); ++i) {
    // Apply the matched filter as filter * x, and compute x * x.
    float x2_sum = 0.f;
    float s = 0;
    size_t x_index = x_start_index;
    if (compute_accumulated_error) {
      for (size_t k = 0; k < h.size(); ++k) {
        x2_sum += x[x_index] * x[x_index];
        s += h[k] * x[x_index];
        x_index = x_index < (x.size() - 1) ? x_index + 1 : 0;
        if ((k + 1 & 0b11) == 0) {
          int idx = k >> 2;
          accumulated_error[idx] += (y[i] - s) * (y[i] - s);
        }
      }
    } else {
      for (size_t k = 0; k < h.size(); ++k) {
        x2_sum += x[x_index] * x[x_index];
        s += h[k] * x[x_index];
        x_index = x_index < (x.size() - 1) ? x_index + 1 : 0;
      }
    }

    // Compute the matched filter error.
    float e = y[i] - s;
    const bool saturation = y[i] >= 32000.f || y[i] <= -32000.f;
    (*error_sum) += e * e;

    // Update the matched filter estimate in an NLMS manner.
    if (x2_sum > x2_sum_threshold && !saturation) {
      RTC_DCHECK_LT(0.f, x2_sum);
      const float alpha = smoothing * e / x2_sum;

      // filter = filter + smoothing * (y - filter * x) * x / x * x.
      size_t x_index = x_start_index;
      for (size_t k = 0; k < h.size(); ++k) {
        h[k] += alpha * x[x_index];
        x_index = x_index < (x.size() - 1) ? x_index + 1 : 0;
      }
      *filters_updated = true;
    }

    x_start_index = x_start_index > 0 ? x_start_index - 1 : x.size() - 1;
  }
}

size_t MaxSquarePeakIndex(rtc::ArrayView<const float> h) {
  if (h.size() < 2) {
    return 0;
  }
  float max_element1 = h[0] * h[0];
  float max_element2 = h[1] * h[1];
  size_t lag_estimate1 = 0;
  size_t lag_estimate2 = 1;
  const size_t last_index = h.size() - 1;
  // Keeping track of even & odd max elements separately typically allows the
  // compiler to produce more efficient code.
  for (size_t k = 2; k < last_index; k += 2) {
    float element1 = h[k] * h[k];
    float element2 = h[k + 1] * h[k + 1];
    if (element1 > max_element1) {
      max_element1 = element1;
      lag_estimate1 = k;
    }
    if (element2 > max_element2) {
      max_element2 = element2;
      lag_estimate2 = k + 1;
    }
  }
  if (max_element2 > max_element1) {
    max_element1 = max_element2;
    lag_estimate1 = lag_estimate2;
  }
  // In case of odd h size, we have not yet checked the last element.
  float last_element = h[last_index] * h[last_index];
  if (last_element > max_element1) {
    return last_index;
  }
  return lag_estimate1;
}

}  // namespace aec3

MatchedFilter::MatchedFilter(ApmDataDumper* data_dumper,
                             Aec3Optimization optimization,
                             size_t sub_block_size,
                             size_t window_size_sub_blocks,
                             int num_matched_filters,
                             size_t alignment_shift_sub_blocks,
                             float excitation_limit,
                             float smoothing_fast,
                             float smoothing_slow,
                             float matching_filter_threshold,
                             bool detect_pre_echo)
    : data_dumper_(data_dumper),
      optimization_(optimization),
      sub_block_size_(sub_block_size),
      filter_intra_lag_shift_(alignment_shift_sub_blocks * sub_block_size_),
      filters_(
          num_matched_filters,
          std::vector<float>(window_size_sub_blocks * sub_block_size_, 0.f)),
      filters_offsets_(num_matched_filters, 0),
      excitation_limit_(excitation_limit),
      smoothing_fast_(smoothing_fast),
      smoothing_slow_(smoothing_slow),
      matching_filter_threshold_(matching_filter_threshold),
      detect_pre_echo_(detect_pre_echo) {
  RTC_DCHECK(data_dumper);
  RTC_DCHECK_LT(0, window_size_sub_blocks);
  RTC_DCHECK((kBlockSize % sub_block_size) == 0);
  RTC_DCHECK((sub_block_size % 4) == 0);
  static_assert(kAccumulatedErrorSubSampleRate == 4);
  if (detect_pre_echo_) {
    accumulated_error_ = std::vector<std::vector<float>>(
        num_matched_filters,
        std::vector<float>(window_size_sub_blocks * sub_block_size_ /
                               kAccumulatedErrorSubSampleRate,
                           1.0f));

    instantaneous_accumulated_error_ =
        std::vector<float>(window_size_sub_blocks * sub_block_size_ /
                               kAccumulatedErrorSubSampleRate,
                           0.0f);
    scratch_memory_ =
        std::vector<float>(window_size_sub_blocks * sub_block_size_);
  }
}

MatchedFilter::~MatchedFilter() = default;

void MatchedFilter::Reset() {
  for (auto& f : filters_) {
    std::fill(f.begin(), f.end(), 0.f);
  }

  for (auto& e : accumulated_error_) {
    std::fill(e.begin(), e.end(), 1.0f);
  }

  winner_lag_ = absl::nullopt;
  reported_lag_estimate_ = absl::nullopt;
}

void MatchedFilter::Update(const DownsampledRenderBuffer& render_buffer,
                           rtc::ArrayView<const float> capture,
                           bool use_slow_smoothing) {
  RTC_DCHECK_EQ(sub_block_size_, capture.size());
  auto& y = capture;

  const float smoothing =
      use_slow_smoothing ? smoothing_slow_ : smoothing_fast_;

  const float x2_sum_threshold =
      filters_[0].size() * excitation_limit_ * excitation_limit_;

  // Compute anchor for the matched filter error.
  float error_sum_anchor = 0.0f;
  for (size_t k = 0; k < y.size(); ++k) {
    error_sum_anchor += y[k] * y[k];
  }

  // Apply all matched filters.
  float winner_error_sum = error_sum_anchor;
  winner_lag_ = absl::nullopt;
  reported_lag_estimate_ = absl::nullopt;
  size_t alignment_shift = 0;
  absl::optional<size_t> previous_lag_estimate;
  const int num_filters = static_cast<int>(filters_.size());
  int winner_index = -1;
  for (int n = 0; n < num_filters; ++n) {
    float error_sum = 0.f;
    bool filters_updated = false;
    const bool compute_pre_echo =
        detect_pre_echo_ && n == last_detected_best_lag_filter_;

    size_t x_start_index =
        (render_buffer.read + alignment_shift + sub_block_size_ - 1) %
        render_buffer.buffer.size();

    switch (optimization_) {
#if defined(WEBRTC_ARCH_X86_FAMILY)
      case Aec3Optimization::kSse2:
        aec3::MatchedFilterCore_SSE2(
            x_start_index, x2_sum_threshold, smoothing, render_buffer.buffer, y,
            filters_[n], &filters_updated, &error_sum, compute_pre_echo,
            instantaneous_accumulated_error_, scratch_memory_);
        break;
#endif
#if defined(WEBRTC_HAS_NEON)
      case Aec3Optimization::kNeon:
        aec3::MatchedFilterCore_NEON(
            x_start_index, x2_sum_threshold, smoothing, render_buffer.buffer, y,
            filters_[n], &filters_updated, &error_sum, compute_pre_echo,
            instantaneous_accumulated_error_, scratch_memory_);
        break;
#endif
      default:
        aec3::MatchedFilterCore(x_start_index, x2_sum_threshold, smoothing,
                                render_buffer.buffer, y, filters_[n],
                                &filters_updated, &error_sum, compute_pre_echo,
                                instantaneous_accumulated_error_);
    }

    // Estimate the lag in the matched filter as the distance to the portion in
    // the filter that contributes the most to the matched filter output. This
    // is detected as the peak of the matched filter.
    const size_t lag_estimate = aec3::MaxSquarePeakIndex(filters_[n]);
    const bool reliable =
        lag_estimate > 2 && lag_estimate < (filters_[n].size() - 10) &&
        error_sum < matching_filter_threshold_ * error_sum_anchor;

    // Find the best estimate
    const size_t lag = lag_estimate + alignment_shift;
    if (filters_updated && reliable && error_sum < winner_error_sum) {
      winner_error_sum = error_sum;
      winner_index = n;
      // In case that 2 matched filters return the same winner candidate
      // (overlap region), the one with the smaller index is chosen in order
      // to search for pre-echoes.
      if (previous_lag_estimate && previous_lag_estimate == lag) {
        winner_lag_ = previous_lag_estimate;
        winner_index = n - 1;
      } else {
        winner_lag_ = lag;
      }
    }
    previous_lag_estimate = lag;
    alignment_shift += filter_intra_lag_shift_;
  }

  if (winner_index != -1) {
    RTC_DCHECK(winner_lag_.has_value());
    reported_lag_estimate_ =
        LagEstimate(winner_lag_.value(), /*pre_echo_lag=*/winner_lag_.value());
    if (detect_pre_echo_ && last_detected_best_lag_filter_ == winner_index) {
      if (error_sum_anchor > 30.0f * 30.0f * y.size()) {
        UpdateAccumulatedError(instantaneous_accumulated_error_,
                               accumulated_error_[winner_index],
                               1.0f / error_sum_anchor);
      }
      reported_lag_estimate_->pre_echo_lag = ComputePreEchoLag(
          accumulated_error_[winner_index], winner_lag_.value(),
          winner_index * filter_intra_lag_shift_ /*alignment_shift_winner*/);
    }
    last_detected_best_lag_filter_ = winner_index;
  }
  if (ApmDataDumper::IsAvailable()) {
    Dump();
  }
}

void MatchedFilter::LogFilterProperties(int sample_rate_hz,
                                        size_t shift,
                                        size_t downsampling_factor) const {
  size_t alignment_shift = 0;
  constexpr int kFsBy1000 = 16;
  for (size_t k = 0; k < filters_.size(); ++k) {
    int start = static_cast<int>(alignment_shift * downsampling_factor);
    int end = static_cast<int>((alignment_shift + filters_[k].size()) *
                               downsampling_factor);
    RTC_LOG(LS_VERBOSE) << "Filter " << k << ": start: "
                        << (start - static_cast<int>(shift)) / kFsBy1000
                        << " ms, end: "
                        << (end - static_cast<int>(shift)) / kFsBy1000
                        << " ms.";
    alignment_shift += filter_intra_lag_shift_;
  }
}

void MatchedFilter::Dump() {
  for (size_t n = 0; n < filters_.size(); ++n) {
    const size_t lag_estimate = aec3::MaxSquarePeakIndex(filters_[n]);
    std::string dumper_filter = "aec3_correlator_" + std::to_string(n) + "_h";
    data_dumper_->DumpRaw(dumper_filter.c_str(), filters_[n]);
    std::string dumper_lag = "aec3_correlator_lag_" + std::to_string(n);
    data_dumper_->DumpRaw(dumper_lag.c_str(),
                          lag_estimate + n * filter_intra_lag_shift_);
    if (detect_pre_echo_) {
      std::string dumper_error =
          "aec3_correlator_error_" + std::to_string(n) + "_h";
      data_dumper_->DumpRaw(dumper_error.c_str(), accumulated_error_[n]);

      size_t pre_echo_lag = ComputePreEchoLag(
          accumulated_error_[n], lag_estimate + n * filter_intra_lag_shift_,
          n * filter_intra_lag_shift_);
      std::string dumper_pre_lag =
          "aec3_correlator_pre_echo_lag_" + std::to_string(n);
      data_dumper_->DumpRaw(dumper_pre_lag.c_str(), pre_echo_lag);
    }
  }
}

}  // namespace webrtc
