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

#include "modules/audio_processing/aec3/downsampled_render_buffer.h"
#include "modules/audio_processing/logging/apm_data_dumper.h"
#include "rtc_base/checks.h"
#include "rtc_base/logging.h"

namespace webrtc {
namespace aec3 {

#if defined(WEBRTC_HAS_NEON)

void MatchedFilterCore_NEON(size_t x_start_index,
                            float x2_sum_threshold,
                            float smoothing,
                            rtc::ArrayView<const float> x,
                            rtc::ArrayView<const float> y,
                            rtc::ArrayView<float> h,
                            bool* filters_updated,
                            float* error_sum) {
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
    float* v = reinterpret_cast<float*>(&x2_sum_128);
    x2_sum += v[0] + v[1] + v[2] + v[3];
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

void MatchedFilterCore_SSE2(size_t x_start_index,
                            float x2_sum_threshold,
                            float smoothing,
                            rtc::ArrayView<const float> x,
                            rtc::ArrayView<const float> y,
                            rtc::ArrayView<float> h,
                            bool* filters_updated,
                            float* error_sum) {
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
    __m128 x2_sum_128 = _mm_set1_ps(0);
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
        const __m128 x_k = _mm_loadu_ps(x_p);
        const __m128 h_k = _mm_loadu_ps(h_p);
        const __m128 xx = _mm_mul_ps(x_k, x_k);
        // Compute and accumulate x * x and h * x.
        x2_sum_128 = _mm_add_ps(x2_sum_128, xx);
        const __m128 hx = _mm_mul_ps(h_k, x_k);
        s_128 = _mm_add_ps(s_128, hx);
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
    float* v = reinterpret_cast<float*>(&x2_sum_128);
    x2_sum += v[0] + v[1] + v[2] + v[3];
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
                       float* error_sum) {
  // Process for all samples in the sub-block.
  for (size_t i = 0; i < y.size(); ++i) {
    // Apply the matched filter as filter * x, and compute x * x.
    float x2_sum = 0.f;
    float s = 0;
    size_t x_index = x_start_index;
    for (size_t k = 0; k < h.size(); ++k) {
      x2_sum += x[x_index] * x[x_index];
      s += h[k] * x[x_index];
      x_index = x_index < (x.size() - 1) ? x_index + 1 : 0;
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
                             float matching_filter_threshold)
    : data_dumper_(data_dumper),
      optimization_(optimization),
      sub_block_size_(sub_block_size),
      filter_intra_lag_shift_(alignment_shift_sub_blocks * sub_block_size_),
      filters_(
          num_matched_filters,
          std::vector<float>(window_size_sub_blocks * sub_block_size_, 0.f)),
      lag_estimates_(num_matched_filters),
      filters_offsets_(num_matched_filters, 0),
      excitation_limit_(excitation_limit),
      smoothing_fast_(smoothing_fast),
      smoothing_slow_(smoothing_slow),
      matching_filter_threshold_(matching_filter_threshold) {
  RTC_DCHECK(data_dumper);
  RTC_DCHECK_LT(0, window_size_sub_blocks);
  RTC_DCHECK((kBlockSize % sub_block_size) == 0);
  RTC_DCHECK((sub_block_size % 4) == 0);
}

MatchedFilter::~MatchedFilter() = default;

void MatchedFilter::Reset() {
  for (auto& f : filters_) {
    std::fill(f.begin(), f.end(), 0.f);
  }

  for (auto& l : lag_estimates_) {
    l = MatchedFilter::LagEstimate();
  }
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

  // Apply all matched filters.
  size_t alignment_shift = 0;
  for (size_t n = 0; n < filters_.size(); ++n) {
    float error_sum = 0.f;
    bool filters_updated = false;

    size_t x_start_index =
        (render_buffer.read + alignment_shift + sub_block_size_ - 1) %
        render_buffer.buffer.size();

    switch (optimization_) {
#if defined(WEBRTC_ARCH_X86_FAMILY)
      case Aec3Optimization::kSse2:
        aec3::MatchedFilterCore_SSE2(x_start_index, x2_sum_threshold, smoothing,
                                     render_buffer.buffer, y, filters_[n],
                                     &filters_updated, &error_sum);
        break;
#endif
#if defined(WEBRTC_HAS_NEON)
      case Aec3Optimization::kNeon:
        aec3::MatchedFilterCore_NEON(x_start_index, x2_sum_threshold, smoothing,
                                     render_buffer.buffer, y, filters_[n],
                                     &filters_updated, &error_sum);
        break;
#endif
      default:
        aec3::MatchedFilterCore(x_start_index, x2_sum_threshold, smoothing,
                                render_buffer.buffer, y, filters_[n],
                                &filters_updated, &error_sum);
    }

    // Compute anchor for the matched filter error.
    const float error_sum_anchor =
        std::inner_product(y.begin(), y.end(), y.begin(), 0.f);

    // Estimate the lag in the matched filter as the distance to the portion in
    // the filter that contributes the most to the matched filter output. This
    // is detected as the peak of the matched filter.
    const size_t lag_estimate = std::distance(
        filters_[n].begin(),
        std::max_element(
            filters_[n].begin(), filters_[n].end(),
            [](float a, float b) -> bool { return a * a < b * b; }));

    // Update the lag estimates for the matched filter.
    lag_estimates_[n] = LagEstimate(
        error_sum_anchor - error_sum,
        (lag_estimate > 2 && lag_estimate < (filters_[n].size() - 10) &&
         error_sum < matching_filter_threshold_ * error_sum_anchor),
        lag_estimate + alignment_shift, filters_updated);

    RTC_DCHECK_GE(10, filters_.size());
    switch (n) {
      case 0:
        data_dumper_->DumpRaw("aec3_correlator_0_h", filters_[0]);
        break;
      case 1:
        data_dumper_->DumpRaw("aec3_correlator_1_h", filters_[1]);
        break;
      case 2:
        data_dumper_->DumpRaw("aec3_correlator_2_h", filters_[2]);
        break;
      case 3:
        data_dumper_->DumpRaw("aec3_correlator_3_h", filters_[3]);
        break;
      case 4:
        data_dumper_->DumpRaw("aec3_correlator_4_h", filters_[4]);
        break;
      case 5:
        data_dumper_->DumpRaw("aec3_correlator_5_h", filters_[5]);
        break;
      case 6:
        data_dumper_->DumpRaw("aec3_correlator_6_h", filters_[6]);
        break;
      case 7:
        data_dumper_->DumpRaw("aec3_correlator_7_h", filters_[7]);
        break;
      case 8:
        data_dumper_->DumpRaw("aec3_correlator_8_h", filters_[8]);
        break;
      case 9:
        data_dumper_->DumpRaw("aec3_correlator_9_h", filters_[9]);
        break;
      default:
        RTC_NOTREACHED();
    }

    alignment_shift += filter_intra_lag_shift_;
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

}  // namespace webrtc
