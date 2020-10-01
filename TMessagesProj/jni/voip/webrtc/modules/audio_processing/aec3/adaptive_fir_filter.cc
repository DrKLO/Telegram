/*
 *  Copyright (c) 2017 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "modules/audio_processing/aec3/adaptive_fir_filter.h"

// Defines WEBRTC_ARCH_X86_FAMILY, used below.
#include "rtc_base/system/arch.h"

#if defined(WEBRTC_HAS_NEON)
#include <arm_neon.h>
#endif
#if defined(WEBRTC_ARCH_X86_FAMILY)
#include <emmintrin.h>
#endif
#include <math.h>

#include <algorithm>
#include <functional>

#include "modules/audio_processing/aec3/fft_data.h"
#include "rtc_base/checks.h"

namespace webrtc {

namespace aec3 {

// Computes and stores the frequency response of the filter.
void ComputeFrequencyResponse(
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
    for (size_t ch = 0; ch < num_render_channels; ++ch) {
      for (size_t j = 0; j < kFftLengthBy2Plus1; ++j) {
        float tmp =
            H[p][ch].re[j] * H[p][ch].re[j] + H[p][ch].im[j] * H[p][ch].im[j];
        (*H2)[p][j] = std::max((*H2)[p][j], tmp);
      }
    }
  }
}

#if defined(WEBRTC_HAS_NEON)
// Computes and stores the frequency response of the filter.
void ComputeFrequencyResponse_Neon(
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
    for (size_t ch = 0; ch < num_render_channels; ++ch) {
      for (size_t j = 0; j < kFftLengthBy2; j += 4) {
        const float32x4_t re = vld1q_f32(&H[p][ch].re[j]);
        const float32x4_t im = vld1q_f32(&H[p][ch].im[j]);
        float32x4_t H2_new = vmulq_f32(re, re);
        H2_new = vmlaq_f32(H2_new, im, im);
        float32x4_t H2_p_j = vld1q_f32(&(*H2)[p][j]);
        H2_p_j = vmaxq_f32(H2_p_j, H2_new);
        vst1q_f32(&(*H2)[p][j], H2_p_j);
      }
      float H2_new = H[p][ch].re[kFftLengthBy2] * H[p][ch].re[kFftLengthBy2] +
                     H[p][ch].im[kFftLengthBy2] * H[p][ch].im[kFftLengthBy2];
      (*H2)[p][kFftLengthBy2] = std::max((*H2)[p][kFftLengthBy2], H2_new);
    }
  }
}
#endif

#if defined(WEBRTC_ARCH_X86_FAMILY)
// Computes and stores the frequency response of the filter.
void ComputeFrequencyResponse_Sse2(
    size_t num_partitions,
    const std::vector<std::vector<FftData>>& H,
    std::vector<std::array<float, kFftLengthBy2Plus1>>* H2) {
  for (auto& H2_ch : *H2) {
    H2_ch.fill(0.f);
  }

  const size_t num_render_channels = H[0].size();
  RTC_DCHECK_EQ(H.size(), H2->capacity());
  // constexpr __mmmask8 kMaxMask = static_cast<__mmmask8>(256u);
  for (size_t p = 0; p < num_partitions; ++p) {
    RTC_DCHECK_EQ(kFftLengthBy2Plus1, (*H2)[p].size());
    for (size_t ch = 0; ch < num_render_channels; ++ch) {
      for (size_t j = 0; j < kFftLengthBy2; j += 4) {
        const __m128 re = _mm_loadu_ps(&H[p][ch].re[j]);
        const __m128 re2 = _mm_mul_ps(re, re);
        const __m128 im = _mm_loadu_ps(&H[p][ch].im[j]);
        const __m128 im2 = _mm_mul_ps(im, im);
        const __m128 H2_new = _mm_add_ps(re2, im2);
        __m128 H2_k_j = _mm_loadu_ps(&(*H2)[p][j]);
        H2_k_j = _mm_max_ps(H2_k_j, H2_new);
        _mm_storeu_ps(&(*H2)[p][j], H2_k_j);
      }
      float H2_new = H[p][ch].re[kFftLengthBy2] * H[p][ch].re[kFftLengthBy2] +
                     H[p][ch].im[kFftLengthBy2] * H[p][ch].im[kFftLengthBy2];
      (*H2)[p][kFftLengthBy2] = std::max((*H2)[p][kFftLengthBy2], H2_new);
    }
  }
}
#endif

// Adapts the filter partitions as H(t+1)=H(t)+G(t)*conj(X(t)).
void AdaptPartitions(const RenderBuffer& render_buffer,
                     const FftData& G,
                     size_t num_partitions,
                     std::vector<std::vector<FftData>>* H) {
  rtc::ArrayView<const std::vector<FftData>> render_buffer_data =
      render_buffer.GetFftBuffer();
  size_t index = render_buffer.Position();
  const size_t num_render_channels = render_buffer_data[index].size();
  for (size_t p = 0; p < num_partitions; ++p) {
    for (size_t ch = 0; ch < num_render_channels; ++ch) {
      const FftData& X_p_ch = render_buffer_data[index][ch];
      FftData& H_p_ch = (*H)[p][ch];
      for (size_t k = 0; k < kFftLengthBy2Plus1; ++k) {
        H_p_ch.re[k] += X_p_ch.re[k] * G.re[k] + X_p_ch.im[k] * G.im[k];
        H_p_ch.im[k] += X_p_ch.re[k] * G.im[k] - X_p_ch.im[k] * G.re[k];
      }
    }
    index = index < (render_buffer_data.size() - 1) ? index + 1 : 0;
  }
}

#if defined(WEBRTC_HAS_NEON)
// Adapts the filter partitions. (Neon variant)
void AdaptPartitions_Neon(const RenderBuffer& render_buffer,
                          const FftData& G,
                          size_t num_partitions,
                          std::vector<std::vector<FftData>>* H) {
  rtc::ArrayView<const std::vector<FftData>> render_buffer_data =
      render_buffer.GetFftBuffer();
  const size_t num_render_channels = render_buffer_data[0].size();
  const size_t lim1 = std::min(
      render_buffer_data.size() - render_buffer.Position(), num_partitions);
  const size_t lim2 = num_partitions;
  constexpr size_t kNumFourBinBands = kFftLengthBy2 / 4;

  size_t X_partition = render_buffer.Position();
  size_t limit = lim1;
  size_t p = 0;
  do {
    for (; p < limit; ++p, ++X_partition) {
      for (size_t ch = 0; ch < num_render_channels; ++ch) {
        FftData& H_p_ch = (*H)[p][ch];
        const FftData& X = render_buffer_data[X_partition][ch];
        for (size_t k = 0, n = 0; n < kNumFourBinBands; ++n, k += 4) {
          const float32x4_t G_re = vld1q_f32(&G.re[k]);
          const float32x4_t G_im = vld1q_f32(&G.im[k]);
          const float32x4_t X_re = vld1q_f32(&X.re[k]);
          const float32x4_t X_im = vld1q_f32(&X.im[k]);
          const float32x4_t H_re = vld1q_f32(&H_p_ch.re[k]);
          const float32x4_t H_im = vld1q_f32(&H_p_ch.im[k]);
          const float32x4_t a = vmulq_f32(X_re, G_re);
          const float32x4_t e = vmlaq_f32(a, X_im, G_im);
          const float32x4_t c = vmulq_f32(X_re, G_im);
          const float32x4_t f = vmlsq_f32(c, X_im, G_re);
          const float32x4_t g = vaddq_f32(H_re, e);
          const float32x4_t h = vaddq_f32(H_im, f);
          vst1q_f32(&H_p_ch.re[k], g);
          vst1q_f32(&H_p_ch.im[k], h);
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
#endif

#if defined(WEBRTC_ARCH_X86_FAMILY)
// Adapts the filter partitions. (SSE2 variant)
void AdaptPartitions_Sse2(const RenderBuffer& render_buffer,
                          const FftData& G,
                          size_t num_partitions,
                          std::vector<std::vector<FftData>>* H) {
  rtc::ArrayView<const std::vector<FftData>> render_buffer_data =
      render_buffer.GetFftBuffer();
  const size_t num_render_channels = render_buffer_data[0].size();
  const size_t lim1 = std::min(
      render_buffer_data.size() - render_buffer.Position(), num_partitions);
  const size_t lim2 = num_partitions;
  constexpr size_t kNumFourBinBands = kFftLengthBy2 / 4;

  size_t X_partition = render_buffer.Position();
  size_t limit = lim1;
  size_t p = 0;
  do {
    for (; p < limit; ++p, ++X_partition) {
      for (size_t ch = 0; ch < num_render_channels; ++ch) {
        FftData& H_p_ch = (*H)[p][ch];
        const FftData& X = render_buffer_data[X_partition][ch];

        for (size_t k = 0, n = 0; n < kNumFourBinBands; ++n, k += 4) {
          const __m128 G_re = _mm_loadu_ps(&G.re[k]);
          const __m128 G_im = _mm_loadu_ps(&G.im[k]);
          const __m128 X_re = _mm_loadu_ps(&X.re[k]);
          const __m128 X_im = _mm_loadu_ps(&X.im[k]);
          const __m128 H_re = _mm_loadu_ps(&H_p_ch.re[k]);
          const __m128 H_im = _mm_loadu_ps(&H_p_ch.im[k]);
          const __m128 a = _mm_mul_ps(X_re, G_re);
          const __m128 b = _mm_mul_ps(X_im, G_im);
          const __m128 c = _mm_mul_ps(X_re, G_im);
          const __m128 d = _mm_mul_ps(X_im, G_re);
          const __m128 e = _mm_add_ps(a, b);
          const __m128 f = _mm_sub_ps(c, d);
          const __m128 g = _mm_add_ps(H_re, e);
          const __m128 h = _mm_add_ps(H_im, f);
          _mm_storeu_ps(&H_p_ch.re[k], g);
          _mm_storeu_ps(&H_p_ch.im[k], h);
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
#endif

// Produces the filter output.
void ApplyFilter(const RenderBuffer& render_buffer,
                 size_t num_partitions,
                 const std::vector<std::vector<FftData>>& H,
                 FftData* S) {
  S->re.fill(0.f);
  S->im.fill(0.f);

  rtc::ArrayView<const std::vector<FftData>> render_buffer_data =
      render_buffer.GetFftBuffer();
  size_t index = render_buffer.Position();
  const size_t num_render_channels = render_buffer_data[index].size();
  for (size_t p = 0; p < num_partitions; ++p) {
    RTC_DCHECK_EQ(num_render_channels, H[p].size());
    for (size_t ch = 0; ch < num_render_channels; ++ch) {
      const FftData& X_p_ch = render_buffer_data[index][ch];
      const FftData& H_p_ch = H[p][ch];
      for (size_t k = 0; k < kFftLengthBy2Plus1; ++k) {
        S->re[k] += X_p_ch.re[k] * H_p_ch.re[k] - X_p_ch.im[k] * H_p_ch.im[k];
        S->im[k] += X_p_ch.re[k] * H_p_ch.im[k] + X_p_ch.im[k] * H_p_ch.re[k];
      }
    }
    index = index < (render_buffer_data.size() - 1) ? index + 1 : 0;
  }
}

#if defined(WEBRTC_HAS_NEON)
// Produces the filter output (Neon variant).
void ApplyFilter_Neon(const RenderBuffer& render_buffer,
                      size_t num_partitions,
                      const std::vector<std::vector<FftData>>& H,
                      FftData* S) {
  // const RenderBuffer& render_buffer,
  //                     rtc::ArrayView<const FftData> H,
  //                     FftData* S) {
  RTC_DCHECK_GE(H.size(), H.size() - 1);
  S->Clear();

  rtc::ArrayView<const std::vector<FftData>> render_buffer_data =
      render_buffer.GetFftBuffer();
  const size_t num_render_channels = render_buffer_data[0].size();
  const size_t lim1 = std::min(
      render_buffer_data.size() - render_buffer.Position(), num_partitions);
  const size_t lim2 = num_partitions;
  constexpr size_t kNumFourBinBands = kFftLengthBy2 / 4;

  size_t X_partition = render_buffer.Position();
  size_t p = 0;
  size_t limit = lim1;
  do {
    for (; p < limit; ++p, ++X_partition) {
      for (size_t ch = 0; ch < num_render_channels; ++ch) {
        const FftData& H_p_ch = H[p][ch];
        const FftData& X = render_buffer_data[X_partition][ch];
        for (size_t k = 0, n = 0; n < kNumFourBinBands; ++n, k += 4) {
          const float32x4_t X_re = vld1q_f32(&X.re[k]);
          const float32x4_t X_im = vld1q_f32(&X.im[k]);
          const float32x4_t H_re = vld1q_f32(&H_p_ch.re[k]);
          const float32x4_t H_im = vld1q_f32(&H_p_ch.im[k]);
          const float32x4_t S_re = vld1q_f32(&S->re[k]);
          const float32x4_t S_im = vld1q_f32(&S->im[k]);
          const float32x4_t a = vmulq_f32(X_re, H_re);
          const float32x4_t e = vmlsq_f32(a, X_im, H_im);
          const float32x4_t c = vmulq_f32(X_re, H_im);
          const float32x4_t f = vmlaq_f32(c, X_im, H_re);
          const float32x4_t g = vaddq_f32(S_re, e);
          const float32x4_t h = vaddq_f32(S_im, f);
          vst1q_f32(&S->re[k], g);
          vst1q_f32(&S->im[k], h);
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
#endif

#if defined(WEBRTC_ARCH_X86_FAMILY)
// Produces the filter output (SSE2 variant).
void ApplyFilter_Sse2(const RenderBuffer& render_buffer,
                      size_t num_partitions,
                      const std::vector<std::vector<FftData>>& H,
                      FftData* S) {
  // const RenderBuffer& render_buffer,
  //                     rtc::ArrayView<const FftData> H,
  //                     FftData* S) {
  RTC_DCHECK_GE(H.size(), H.size() - 1);
  S->re.fill(0.f);
  S->im.fill(0.f);

  rtc::ArrayView<const std::vector<FftData>> render_buffer_data =
      render_buffer.GetFftBuffer();
  const size_t num_render_channels = render_buffer_data[0].size();
  const size_t lim1 = std::min(
      render_buffer_data.size() - render_buffer.Position(), num_partitions);
  const size_t lim2 = num_partitions;
  constexpr size_t kNumFourBinBands = kFftLengthBy2 / 4;

  size_t X_partition = render_buffer.Position();
  size_t p = 0;
  size_t limit = lim1;
  do {
    for (; p < limit; ++p, ++X_partition) {
      for (size_t ch = 0; ch < num_render_channels; ++ch) {
        const FftData& H_p_ch = H[p][ch];
        const FftData& X = render_buffer_data[X_partition][ch];
        for (size_t k = 0, n = 0; n < kNumFourBinBands; ++n, k += 4) {
          const __m128 X_re = _mm_loadu_ps(&X.re[k]);
          const __m128 X_im = _mm_loadu_ps(&X.im[k]);
          const __m128 H_re = _mm_loadu_ps(&H_p_ch.re[k]);
          const __m128 H_im = _mm_loadu_ps(&H_p_ch.im[k]);
          const __m128 S_re = _mm_loadu_ps(&S->re[k]);
          const __m128 S_im = _mm_loadu_ps(&S->im[k]);
          const __m128 a = _mm_mul_ps(X_re, H_re);
          const __m128 b = _mm_mul_ps(X_im, H_im);
          const __m128 c = _mm_mul_ps(X_re, H_im);
          const __m128 d = _mm_mul_ps(X_im, H_re);
          const __m128 e = _mm_sub_ps(a, b);
          const __m128 f = _mm_add_ps(c, d);
          const __m128 g = _mm_add_ps(S_re, e);
          const __m128 h = _mm_add_ps(S_im, f);
          _mm_storeu_ps(&S->re[k], g);
          _mm_storeu_ps(&S->im[k], h);
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
#endif

}  // namespace aec3

namespace {

// Ensures that the newly added filter partitions after a size increase are set
// to zero.
void ZeroFilter(size_t old_size,
                size_t new_size,
                std::vector<std::vector<FftData>>* H) {
  RTC_DCHECK_GE(H->size(), old_size);
  RTC_DCHECK_GE(H->size(), new_size);

  for (size_t p = old_size; p < new_size; ++p) {
    RTC_DCHECK_EQ((*H)[p].size(), (*H)[0].size());
    for (size_t ch = 0; ch < (*H)[0].size(); ++ch) {
      (*H)[p][ch].Clear();
    }
  }
}

}  // namespace

AdaptiveFirFilter::AdaptiveFirFilter(size_t max_size_partitions,
                                     size_t initial_size_partitions,
                                     size_t size_change_duration_blocks,
                                     size_t num_render_channels,
                                     Aec3Optimization optimization,
                                     ApmDataDumper* data_dumper)
    : data_dumper_(data_dumper),
      fft_(),
      optimization_(optimization),
      num_render_channels_(num_render_channels),
      max_size_partitions_(max_size_partitions),
      size_change_duration_blocks_(
          static_cast<int>(size_change_duration_blocks)),
      current_size_partitions_(initial_size_partitions),
      target_size_partitions_(initial_size_partitions),
      old_target_size_partitions_(initial_size_partitions),
      H_(max_size_partitions_, std::vector<FftData>(num_render_channels_)) {
  RTC_DCHECK(data_dumper_);
  RTC_DCHECK_GE(max_size_partitions, initial_size_partitions);

  RTC_DCHECK_LT(0, size_change_duration_blocks_);
  one_by_size_change_duration_blocks_ = 1.f / size_change_duration_blocks_;

  ZeroFilter(0, max_size_partitions_, &H_);

  SetSizePartitions(current_size_partitions_, true);
}

AdaptiveFirFilter::~AdaptiveFirFilter() = default;

void AdaptiveFirFilter::HandleEchoPathChange() {
  // TODO(peah): Check the value and purpose of the code below.
  ZeroFilter(current_size_partitions_, max_size_partitions_, &H_);
}

void AdaptiveFirFilter::SetSizePartitions(size_t size, bool immediate_effect) {
  RTC_DCHECK_EQ(max_size_partitions_, H_.capacity());
  RTC_DCHECK_LE(size, max_size_partitions_);

  target_size_partitions_ = std::min(max_size_partitions_, size);
  if (immediate_effect) {
    size_t old_size_partitions_ = current_size_partitions_;
    current_size_partitions_ = old_target_size_partitions_ =
        target_size_partitions_;
    ZeroFilter(old_size_partitions_, current_size_partitions_, &H_);

    partition_to_constrain_ =
        std::min(partition_to_constrain_, current_size_partitions_ - 1);
    size_change_counter_ = 0;
  } else {
    size_change_counter_ = size_change_duration_blocks_;
  }
}

void AdaptiveFirFilter::UpdateSize() {
  RTC_DCHECK_GE(size_change_duration_blocks_, size_change_counter_);
  size_t old_size_partitions_ = current_size_partitions_;
  if (size_change_counter_ > 0) {
    --size_change_counter_;

    auto average = [](float from, float to, float from_weight) {
      return from * from_weight + to * (1.f - from_weight);
    };

    float change_factor =
        size_change_counter_ * one_by_size_change_duration_blocks_;

    current_size_partitions_ = average(old_target_size_partitions_,
                                       target_size_partitions_, change_factor);

    partition_to_constrain_ =
        std::min(partition_to_constrain_, current_size_partitions_ - 1);
  } else {
    current_size_partitions_ = old_target_size_partitions_ =
        target_size_partitions_;
  }
  ZeroFilter(old_size_partitions_, current_size_partitions_, &H_);
  RTC_DCHECK_LE(0, size_change_counter_);
}

void AdaptiveFirFilter::Filter(const RenderBuffer& render_buffer,
                               FftData* S) const {
  RTC_DCHECK(S);
  switch (optimization_) {
#if defined(WEBRTC_ARCH_X86_FAMILY)
    case Aec3Optimization::kSse2:
      aec3::ApplyFilter_Sse2(render_buffer, current_size_partitions_, H_, S);
      break;
#endif
#if defined(WEBRTC_HAS_NEON)
    case Aec3Optimization::kNeon:
      aec3::ApplyFilter_Neon(render_buffer, current_size_partitions_, H_, S);
      break;
#endif
    default:
      aec3::ApplyFilter(render_buffer, current_size_partitions_, H_, S);
  }
}

void AdaptiveFirFilter::Adapt(const RenderBuffer& render_buffer,
                              const FftData& G) {
  // Adapt the filter and update the filter size.
  AdaptAndUpdateSize(render_buffer, G);

  // Constrain the filter partitions in a cyclic manner.
  Constrain();
}

void AdaptiveFirFilter::Adapt(const RenderBuffer& render_buffer,
                              const FftData& G,
                              std::vector<float>* impulse_response) {
  // Adapt the filter and update the filter size.
  AdaptAndUpdateSize(render_buffer, G);

  // Constrain the filter partitions in a cyclic manner.
  ConstrainAndUpdateImpulseResponse(impulse_response);
}

void AdaptiveFirFilter::ComputeFrequencyResponse(
    std::vector<std::array<float, kFftLengthBy2Plus1>>* H2) const {
  RTC_DCHECK_GE(max_size_partitions_, H2->capacity());

  H2->resize(current_size_partitions_);

  switch (optimization_) {
#if defined(WEBRTC_ARCH_X86_FAMILY)
    case Aec3Optimization::kSse2:
      aec3::ComputeFrequencyResponse_Sse2(current_size_partitions_, H_, H2);
      break;
#endif
#if defined(WEBRTC_HAS_NEON)
    case Aec3Optimization::kNeon:
      aec3::ComputeFrequencyResponse_Neon(current_size_partitions_, H_, H2);
      break;
#endif
    default:
      aec3::ComputeFrequencyResponse(current_size_partitions_, H_, H2);
  }
}

void AdaptiveFirFilter::AdaptAndUpdateSize(const RenderBuffer& render_buffer,
                                           const FftData& G) {
  // Update the filter size if needed.
  UpdateSize();

  // Adapt the filter.
  switch (optimization_) {
#if defined(WEBRTC_ARCH_X86_FAMILY)
    case Aec3Optimization::kSse2:
      aec3::AdaptPartitions_Sse2(render_buffer, G, current_size_partitions_,
                                 &H_);
      break;
#endif
#if defined(WEBRTC_HAS_NEON)
    case Aec3Optimization::kNeon:
      aec3::AdaptPartitions_Neon(render_buffer, G, current_size_partitions_,
                                 &H_);
      break;
#endif
    default:
      aec3::AdaptPartitions(render_buffer, G, current_size_partitions_, &H_);
  }
}

// Constrains the partition of the frequency domain filter to be limited in
// time via setting the relevant time-domain coefficients to zero and updates
// the corresponding values in an externally stored impulse response estimate.
void AdaptiveFirFilter::ConstrainAndUpdateImpulseResponse(
    std::vector<float>* impulse_response) {
  RTC_DCHECK_EQ(GetTimeDomainLength(max_size_partitions_),
                impulse_response->capacity());
  impulse_response->resize(GetTimeDomainLength(current_size_partitions_));
  std::array<float, kFftLength> h;
  impulse_response->resize(GetTimeDomainLength(current_size_partitions_));
  std::fill(
      impulse_response->begin() + partition_to_constrain_ * kFftLengthBy2,
      impulse_response->begin() + (partition_to_constrain_ + 1) * kFftLengthBy2,
      0.f);

  for (size_t ch = 0; ch < num_render_channels_; ++ch) {
    fft_.Ifft(H_[partition_to_constrain_][ch], &h);

    static constexpr float kScale = 1.0f / kFftLengthBy2;
    std::for_each(h.begin(), h.begin() + kFftLengthBy2,
                  [](float& a) { a *= kScale; });
    std::fill(h.begin() + kFftLengthBy2, h.end(), 0.f);

    if (ch == 0) {
      std::copy(
          h.begin(), h.begin() + kFftLengthBy2,
          impulse_response->begin() + partition_to_constrain_ * kFftLengthBy2);
    } else {
      for (size_t k = 0, j = partition_to_constrain_ * kFftLengthBy2;
           k < kFftLengthBy2; ++k, ++j) {
        if (fabsf((*impulse_response)[j]) < fabsf(h[k])) {
          (*impulse_response)[j] = h[k];
        }
      }
    }

    fft_.Fft(&h, &H_[partition_to_constrain_][ch]);
  }

  partition_to_constrain_ =
      partition_to_constrain_ < (current_size_partitions_ - 1)
          ? partition_to_constrain_ + 1
          : 0;
}

// Constrains the a partiton of the frequency domain filter to be limited in
// time via setting the relevant time-domain coefficients to zero.
void AdaptiveFirFilter::Constrain() {
  std::array<float, kFftLength> h;
  for (size_t ch = 0; ch < num_render_channels_; ++ch) {
    fft_.Ifft(H_[partition_to_constrain_][ch], &h);

    static constexpr float kScale = 1.0f / kFftLengthBy2;
    std::for_each(h.begin(), h.begin() + kFftLengthBy2,
                  [](float& a) { a *= kScale; });
    std::fill(h.begin() + kFftLengthBy2, h.end(), 0.f);

    fft_.Fft(&h, &H_[partition_to_constrain_][ch]);
  }

  partition_to_constrain_ =
      partition_to_constrain_ < (current_size_partitions_ - 1)
          ? partition_to_constrain_ + 1
          : 0;
}

void AdaptiveFirFilter::ScaleFilter(float factor) {
  for (auto& H_p : H_) {
    for (auto& H_p_ch : H_p) {
      for (auto& re : H_p_ch.re) {
        re *= factor;
      }
      for (auto& im : H_p_ch.im) {
        im *= factor;
      }
    }
  }
}

// Set the filter coefficients.
void AdaptiveFirFilter::SetFilter(size_t num_partitions,
                                  const std::vector<std::vector<FftData>>& H) {
  const size_t min_num_partitions =
      std::min(current_size_partitions_, num_partitions);
  for (size_t p = 0; p < min_num_partitions; ++p) {
    RTC_DCHECK_EQ(H_[p].size(), H[p].size());
    RTC_DCHECK_EQ(num_render_channels_, H_[p].size());

    for (size_t ch = 0; ch < num_render_channels_; ++ch) {
      std::copy(H[p][ch].re.begin(), H[p][ch].re.end(), H_[p][ch].re.begin());
      std::copy(H[p][ch].im.begin(), H[p][ch].im.end(), H_[p][ch].im.begin());
    }
  }
}

}  // namespace webrtc
