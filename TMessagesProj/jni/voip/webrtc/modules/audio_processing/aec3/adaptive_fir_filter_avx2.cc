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

#include "modules/audio_processing/aec3/adaptive_fir_filter.h"
#include "rtc_base/checks.h"

namespace webrtc {

namespace aec3 {

// Computes and stores the frequency response of the filter.
void ComputeFrequencyResponse_Avx2(
    size_t num_partitions,
    const std::vector<std::vector<FftData>>& H,
    std::vector<std::array<float, kFftLengthBy2Plus1>>* H2) {
  for (auto& H2_ch : *H2) {
    H2_ch.fill(0.f);
  }

  const size_t num_render_channels = H[0].size();
  RTC_DCHECK_EQ(H.size(), H2->capacity());
  for (size_t p = 0; p < num_partitions; ++p) {
    RTC_DCHECK_EQ(kFftLengthBy2Plus1, (*H2)[p].size());
    auto& H2_p = (*H2)[p];
    for (size_t ch = 0; ch < num_render_channels; ++ch) {
      const FftData& H_p_ch = H[p][ch];
      for (size_t j = 0; j < kFftLengthBy2; j += 8) {
        __m256 re = _mm256_loadu_ps(&H_p_ch.re[j]);
        __m256 re2 = _mm256_mul_ps(re, re);
        __m256 im = _mm256_loadu_ps(&H_p_ch.im[j]);
        re2 = _mm256_fmadd_ps(im, im, re2);
        __m256 H2_k_j = _mm256_loadu_ps(&H2_p[j]);
        H2_k_j = _mm256_max_ps(H2_k_j, re2);
        _mm256_storeu_ps(&H2_p[j], H2_k_j);
      }
      float H2_new = H_p_ch.re[kFftLengthBy2] * H_p_ch.re[kFftLengthBy2] +
                     H_p_ch.im[kFftLengthBy2] * H_p_ch.im[kFftLengthBy2];
      H2_p[kFftLengthBy2] = std::max(H2_p[kFftLengthBy2], H2_new);
    }
  }
}

// Adapts the filter partitions.
void AdaptPartitions_Avx2(const RenderBuffer& render_buffer,
                          const FftData& G,
                          size_t num_partitions,
                          std::vector<std::vector<FftData>>* H) {
  rtc::ArrayView<const std::vector<FftData>> render_buffer_data =
      render_buffer.GetFftBuffer();
  const size_t num_render_channels = render_buffer_data[0].size();
  const size_t lim1 = std::min(
      render_buffer_data.size() - render_buffer.Position(), num_partitions);
  const size_t lim2 = num_partitions;
  constexpr size_t kNumEightBinBands = kFftLengthBy2 / 8;

  size_t X_partition = render_buffer.Position();
  size_t limit = lim1;
  size_t p = 0;
  do {
    for (; p < limit; ++p, ++X_partition) {
      for (size_t ch = 0; ch < num_render_channels; ++ch) {
        FftData& H_p_ch = (*H)[p][ch];
        const FftData& X = render_buffer_data[X_partition][ch];

        for (size_t k = 0, n = 0; n < kNumEightBinBands; ++n, k += 8) {
          const __m256 G_re = _mm256_loadu_ps(&G.re[k]);
          const __m256 G_im = _mm256_loadu_ps(&G.im[k]);
          const __m256 X_re = _mm256_loadu_ps(&X.re[k]);
          const __m256 X_im = _mm256_loadu_ps(&X.im[k]);
          const __m256 H_re = _mm256_loadu_ps(&H_p_ch.re[k]);
          const __m256 H_im = _mm256_loadu_ps(&H_p_ch.im[k]);
          const __m256 a = _mm256_mul_ps(X_re, G_re);
          const __m256 b = _mm256_mul_ps(X_im, G_im);
          const __m256 c = _mm256_mul_ps(X_re, G_im);
          const __m256 d = _mm256_mul_ps(X_im, G_re);
          const __m256 e = _mm256_add_ps(a, b);
          const __m256 f = _mm256_sub_ps(c, d);
          const __m256 g = _mm256_add_ps(H_re, e);
          const __m256 h = _mm256_add_ps(H_im, f);
          _mm256_storeu_ps(&H_p_ch.re[k], g);
          _mm256_storeu_ps(&H_p_ch.im[k], h);
        }
      }
    }
    X_partition = 0;
    limit = lim2;
  } while (p < lim2);

  X_partition = render_buffer.Position();
  limit = lim1;
  p = 0;
  do {
    for (; p < limit; ++p, ++X_partition) {
      for (size_t ch = 0; ch < num_render_channels; ++ch) {
        FftData& H_p_ch = (*H)[p][ch];
        const FftData& X = render_buffer_data[X_partition][ch];

        H_p_ch.re[kFftLengthBy2] += X.re[kFftLengthBy2] * G.re[kFftLengthBy2] +
                                    X.im[kFftLengthBy2] * G.im[kFftLengthBy2];
        H_p_ch.im[kFftLengthBy2] += X.re[kFftLengthBy2] * G.im[kFftLengthBy2] -
                                    X.im[kFftLengthBy2] * G.re[kFftLengthBy2];
      }
    }

    X_partition = 0;
    limit = lim2;
  } while (p < lim2);
}

// Produces the filter output (AVX2 variant).
void ApplyFilter_Avx2(const RenderBuffer& render_buffer,
                      size_t num_partitions,
                      const std::vector<std::vector<FftData>>& H,
                      FftData* S) {
  RTC_DCHECK_GE(H.size(), H.size() - 1);
  S->re.fill(0.f);
  S->im.fill(0.f);

  rtc::ArrayView<const std::vector<FftData>> render_buffer_data =
      render_buffer.GetFftBuffer();
  const size_t num_render_channels = render_buffer_data[0].size();
  const size_t lim1 = std::min(
      render_buffer_data.size() - render_buffer.Position(), num_partitions);
  const size_t lim2 = num_partitions;
  constexpr size_t kNumEightBinBands = kFftLengthBy2 / 8;

  size_t X_partition = render_buffer.Position();
  size_t p = 0;
  size_t limit = lim1;
  do {
    for (; p < limit; ++p, ++X_partition) {
      for (size_t ch = 0; ch < num_render_channels; ++ch) {
        const FftData& H_p_ch = H[p][ch];
        const FftData& X = render_buffer_data[X_partition][ch];
        for (size_t k = 0, n = 0; n < kNumEightBinBands; ++n, k += 8) {
          const __m256 X_re = _mm256_loadu_ps(&X.re[k]);
          const __m256 X_im = _mm256_loadu_ps(&X.im[k]);
          const __m256 H_re = _mm256_loadu_ps(&H_p_ch.re[k]);
          const __m256 H_im = _mm256_loadu_ps(&H_p_ch.im[k]);
          const __m256 S_re = _mm256_loadu_ps(&S->re[k]);
          const __m256 S_im = _mm256_loadu_ps(&S->im[k]);
          const __m256 a = _mm256_mul_ps(X_re, H_re);
          const __m256 b = _mm256_mul_ps(X_im, H_im);
          const __m256 c = _mm256_mul_ps(X_re, H_im);
          const __m256 d = _mm256_mul_ps(X_im, H_re);
          const __m256 e = _mm256_sub_ps(a, b);
          const __m256 f = _mm256_add_ps(c, d);
          const __m256 g = _mm256_add_ps(S_re, e);
          const __m256 h = _mm256_add_ps(S_im, f);
          _mm256_storeu_ps(&S->re[k], g);
          _mm256_storeu_ps(&S->im[k], h);
        }
      }
    }
    limit = lim2;
    X_partition = 0;
  } while (p < lim2);

  X_partition = render_buffer.Position();
  p = 0;
  limit = lim1;
  do {
    for (; p < limit; ++p, ++X_partition) {
      for (size_t ch = 0; ch < num_render_channels; ++ch) {
        const FftData& H_p_ch = H[p][ch];
        const FftData& X = render_buffer_data[X_partition][ch];
        S->re[kFftLengthBy2] += X.re[kFftLengthBy2] * H_p_ch.re[kFftLengthBy2] -
                                X.im[kFftLengthBy2] * H_p_ch.im[kFftLengthBy2];
        S->im[kFftLengthBy2] += X.re[kFftLengthBy2] * H_p_ch.im[kFftLengthBy2] +
                                X.im[kFftLengthBy2] * H_p_ch.re[kFftLengthBy2];
      }
    }
    limit = lim2;
    X_partition = 0;
  } while (p < lim2);
}

}  // namespace aec3
}  // namespace webrtc
