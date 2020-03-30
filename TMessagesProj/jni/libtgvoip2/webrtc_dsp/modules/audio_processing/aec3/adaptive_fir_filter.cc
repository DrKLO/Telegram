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

#if defined(WEBRTC_ARCH_ARM_FAMILY) && defined(WEBRTC_HAS_NEON)
#include <arm_neon.h>
#endif
#if defined(WEBRTC_ARCH_X86_FAMILY)
#include <emmintrin.h>
#endif
#include <algorithm>
#include <functional>

#include "modules/audio_processing/aec3/fft_data.h"
#include "rtc_base/checks.h"
#include "system_wrappers/include/field_trial.h"

namespace webrtc {

namespace aec3 {

// Computes and stores the frequency response of the filter.
void UpdateFrequencyResponse(
    rtc::ArrayView<const FftData> H,
    std::vector<std::array<float, kFftLengthBy2Plus1>>* H2) {
  RTC_DCHECK_EQ(H.size(), H2->size());
  for (size_t k = 0; k < H.size(); ++k) {
    std::transform(H[k].re.begin(), H[k].re.end(), H[k].im.begin(),
                   (*H2)[k].begin(),
                   [](float a, float b) { return a * a + b * b; });
  }
}

#if defined(WEBRTC_ARCH_ARM_FAMILY) && defined(WEBRTC_HAS_NEON)
// Computes and stores the frequency response of the filter.
void UpdateFrequencyResponse_NEON(
    rtc::ArrayView<const FftData> H,
    std::vector<std::array<float, kFftLengthBy2Plus1>>* H2) {
  RTC_DCHECK_EQ(H.size(), H2->size());
  for (size_t k = 0; k < H.size(); ++k) {
    for (size_t j = 0; j < kFftLengthBy2; j += 4) {
      const float32x4_t re = vld1q_f32(&H[k].re[j]);
      const float32x4_t im = vld1q_f32(&H[k].im[j]);
      float32x4_t H2_k_j = vmulq_f32(re, re);
      H2_k_j = vmlaq_f32(H2_k_j, im, im);
      vst1q_f32(&(*H2)[k][j], H2_k_j);
    }
    (*H2)[k][kFftLengthBy2] = H[k].re[kFftLengthBy2] * H[k].re[kFftLengthBy2] +
                              H[k].im[kFftLengthBy2] * H[k].im[kFftLengthBy2];
  }
}
#endif

#if defined(WEBRTC_ARCH_X86_FAMILY)
// Computes and stores the frequency response of the filter.
void UpdateFrequencyResponse_SSE2(
    rtc::ArrayView<const FftData> H,
    std::vector<std::array<float, kFftLengthBy2Plus1>>* H2) {
  RTC_DCHECK_EQ(H.size(), H2->size());
  for (size_t k = 0; k < H.size(); ++k) {
    for (size_t j = 0; j < kFftLengthBy2; j += 4) {
      const __m128 re = _mm_loadu_ps(&H[k].re[j]);
      const __m128 re2 = _mm_mul_ps(re, re);
      const __m128 im = _mm_loadu_ps(&H[k].im[j]);
      const __m128 im2 = _mm_mul_ps(im, im);
      const __m128 H2_k_j = _mm_add_ps(re2, im2);
      _mm_storeu_ps(&(*H2)[k][j], H2_k_j);
    }
    (*H2)[k][kFftLengthBy2] = H[k].re[kFftLengthBy2] * H[k].re[kFftLengthBy2] +
                              H[k].im[kFftLengthBy2] * H[k].im[kFftLengthBy2];
  }
}
#endif

// Computes and stores the echo return loss estimate of the filter, which is the
// sum of the partition frequency responses.
void UpdateErlEstimator(
    const std::vector<std::array<float, kFftLengthBy2Plus1>>& H2,
    std::array<float, kFftLengthBy2Plus1>* erl) {
  erl->fill(0.f);
  for (auto& H2_j : H2) {
    std::transform(H2_j.begin(), H2_j.end(), erl->begin(), erl->begin(),
                   std::plus<float>());
  }
}

#if defined(WEBRTC_ARCH_ARM_FAMILY) && defined(WEBRTC_HAS_NEON)
// Computes and stores the echo return loss estimate of the filter, which is the
// sum of the partition frequency responses.
void UpdateErlEstimator_NEON(
    const std::vector<std::array<float, kFftLengthBy2Plus1>>& H2,
    std::array<float, kFftLengthBy2Plus1>* erl) {
  erl->fill(0.f);
  for (auto& H2_j : H2) {
    for (size_t k = 0; k < kFftLengthBy2; k += 4) {
      const float32x4_t H2_j_k = vld1q_f32(&H2_j[k]);
      float32x4_t erl_k = vld1q_f32(&(*erl)[k]);
      erl_k = vaddq_f32(erl_k, H2_j_k);
      vst1q_f32(&(*erl)[k], erl_k);
    }
    (*erl)[kFftLengthBy2] += H2_j[kFftLengthBy2];
  }
}
#endif

#if defined(WEBRTC_ARCH_X86_FAMILY)
// Computes and stores the echo return loss estimate of the filter, which is the
// sum of the partition frequency responses.
void UpdateErlEstimator_SSE2(
    const std::vector<std::array<float, kFftLengthBy2Plus1>>& H2,
    std::array<float, kFftLengthBy2Plus1>* erl) {
  erl->fill(0.f);
  for (auto& H2_j : H2) {
    for (size_t k = 0; k < kFftLengthBy2; k += 4) {
      const __m128 H2_j_k = _mm_loadu_ps(&H2_j[k]);
      __m128 erl_k = _mm_loadu_ps(&(*erl)[k]);
      erl_k = _mm_add_ps(erl_k, H2_j_k);
      _mm_storeu_ps(&(*erl)[k], erl_k);
    }
    (*erl)[kFftLengthBy2] += H2_j[kFftLengthBy2];
  }
}
#endif

// Adapts the filter partitions as H(t+1)=H(t)+G(t)*conj(X(t)).
void AdaptPartitions(const RenderBuffer& render_buffer,
                     const FftData& G,
                     rtc::ArrayView<FftData> H) {
  rtc::ArrayView<const FftData> render_buffer_data =
      render_buffer.GetFftBuffer();
  size_t index = render_buffer.Position();
  for (auto& H_j : H) {
    const FftData& X = render_buffer_data[index];
    for (size_t k = 0; k < kFftLengthBy2Plus1; ++k) {
      H_j.re[k] += X.re[k] * G.re[k] + X.im[k] * G.im[k];
      H_j.im[k] += X.re[k] * G.im[k] - X.im[k] * G.re[k];
    }

    index = index < (render_buffer_data.size() - 1) ? index + 1 : 0;
  }
}

#if defined(WEBRTC_ARCH_ARM_FAMILY) && defined(WEBRTC_HAS_NEON)
// Adapts the filter partitions. (NEON variant)
void AdaptPartitions_NEON(const RenderBuffer& render_buffer,
                          const FftData& G,
                          rtc::ArrayView<FftData> H) {
  rtc::ArrayView<const FftData> render_buffer_data =
      render_buffer.GetFftBuffer();
  const int lim1 =
      std::min(render_buffer_data.size() - render_buffer.Position(), H.size());
  const int lim2 = H.size();
  constexpr int kNumFourBinBands = kFftLengthBy2 / 4;
  FftData* H_j = &H[0];
  const FftData* X = &render_buffer_data[render_buffer.Position()];
  int limit = lim1;
  int j = 0;
  do {
    for (; j < limit; ++j, ++H_j, ++X) {
      for (int k = 0, n = 0; n < kNumFourBinBands; ++n, k += 4) {
        const float32x4_t G_re = vld1q_f32(&G.re[k]);
        const float32x4_t G_im = vld1q_f32(&G.im[k]);
        const float32x4_t X_re = vld1q_f32(&X->re[k]);
        const float32x4_t X_im = vld1q_f32(&X->im[k]);
        const float32x4_t H_re = vld1q_f32(&H_j->re[k]);
        const float32x4_t H_im = vld1q_f32(&H_j->im[k]);
        const float32x4_t a = vmulq_f32(X_re, G_re);
        const float32x4_t e = vmlaq_f32(a, X_im, G_im);
        const float32x4_t c = vmulq_f32(X_re, G_im);
        const float32x4_t f = vmlsq_f32(c, X_im, G_re);
        const float32x4_t g = vaddq_f32(H_re, e);
        const float32x4_t h = vaddq_f32(H_im, f);

        vst1q_f32(&H_j->re[k], g);
        vst1q_f32(&H_j->im[k], h);
      }
    }

    X = &render_buffer_data[0];
    limit = lim2;
  } while (j < lim2);

  H_j = &H[0];
  X = &render_buffer_data[render_buffer.Position()];
  limit = lim1;
  j = 0;
  do {
    for (; j < limit; ++j, ++H_j, ++X) {
      H_j->re[kFftLengthBy2] += X->re[kFftLengthBy2] * G.re[kFftLengthBy2] +
                                X->im[kFftLengthBy2] * G.im[kFftLengthBy2];
      H_j->im[kFftLengthBy2] += X->re[kFftLengthBy2] * G.im[kFftLengthBy2] -
                                X->im[kFftLengthBy2] * G.re[kFftLengthBy2];
    }

    X = &render_buffer_data[0];
    limit = lim2;
  } while (j < lim2);
}
#endif

#if defined(WEBRTC_ARCH_X86_FAMILY)
// Adapts the filter partitions. (SSE2 variant)
void AdaptPartitions_SSE2(const RenderBuffer& render_buffer,
                          const FftData& G,
                          rtc::ArrayView<FftData> H) {
  rtc::ArrayView<const FftData> render_buffer_data =
      render_buffer.GetFftBuffer();
  const int lim1 =
      std::min(render_buffer_data.size() - render_buffer.Position(), H.size());
  const int lim2 = H.size();
  constexpr int kNumFourBinBands = kFftLengthBy2 / 4;
  FftData* H_j;
  const FftData* X;
  int limit;
  int j;
  for (int k = 0, n = 0; n < kNumFourBinBands; ++n, k += 4) {
    const __m128 G_re = _mm_loadu_ps(&G.re[k]);
    const __m128 G_im = _mm_loadu_ps(&G.im[k]);

    H_j = &H[0];
    X = &render_buffer_data[render_buffer.Position()];
    limit = lim1;
    j = 0;
    do {
      for (; j < limit; ++j, ++H_j, ++X) {
        const __m128 X_re = _mm_loadu_ps(&X->re[k]);
        const __m128 X_im = _mm_loadu_ps(&X->im[k]);
        const __m128 H_re = _mm_loadu_ps(&H_j->re[k]);
        const __m128 H_im = _mm_loadu_ps(&H_j->im[k]);
        const __m128 a = _mm_mul_ps(X_re, G_re);
        const __m128 b = _mm_mul_ps(X_im, G_im);
        const __m128 c = _mm_mul_ps(X_re, G_im);
        const __m128 d = _mm_mul_ps(X_im, G_re);
        const __m128 e = _mm_add_ps(a, b);
        const __m128 f = _mm_sub_ps(c, d);
        const __m128 g = _mm_add_ps(H_re, e);
        const __m128 h = _mm_add_ps(H_im, f);
        _mm_storeu_ps(&H_j->re[k], g);
        _mm_storeu_ps(&H_j->im[k], h);
      }

      X = &render_buffer_data[0];
      limit = lim2;
    } while (j < lim2);
  }

  H_j = &H[0];
  X = &render_buffer_data[render_buffer.Position()];
  limit = lim1;
  j = 0;
  do {
    for (; j < limit; ++j, ++H_j, ++X) {
      H_j->re[kFftLengthBy2] += X->re[kFftLengthBy2] * G.re[kFftLengthBy2] +
                                X->im[kFftLengthBy2] * G.im[kFftLengthBy2];
      H_j->im[kFftLengthBy2] += X->re[kFftLengthBy2] * G.im[kFftLengthBy2] -
                                X->im[kFftLengthBy2] * G.re[kFftLengthBy2];
    }

    X = &render_buffer_data[0];
    limit = lim2;
  } while (j < lim2);
}
#endif

// Produces the filter output.
void ApplyFilter(const RenderBuffer& render_buffer,
                 rtc::ArrayView<const FftData> H,
                 FftData* S) {
  S->re.fill(0.f);
  S->im.fill(0.f);

  rtc::ArrayView<const FftData> render_buffer_data =
      render_buffer.GetFftBuffer();
  size_t index = render_buffer.Position();
  for (auto& H_j : H) {
    const FftData& X = render_buffer_data[index];
    for (size_t k = 0; k < kFftLengthBy2Plus1; ++k) {
      S->re[k] += X.re[k] * H_j.re[k] - X.im[k] * H_j.im[k];
      S->im[k] += X.re[k] * H_j.im[k] + X.im[k] * H_j.re[k];
    }
    index = index < (render_buffer_data.size() - 1) ? index + 1 : 0;
  }
}

#if defined(WEBRTC_ARCH_ARM_FAMILY) && defined(WEBRTC_HAS_NEON)
// Produces the filter output (NEON variant).
void ApplyFilter_NEON(const RenderBuffer& render_buffer,
                      rtc::ArrayView<const FftData> H,
                      FftData* S) {
  RTC_DCHECK_GE(H.size(), H.size() - 1);
  S->re.fill(0.f);
  S->im.fill(0.f);

  rtc::ArrayView<const FftData> render_buffer_data =
      render_buffer.GetFftBuffer();
  const int lim1 =
      std::min(render_buffer_data.size() - render_buffer.Position(), H.size());
  const int lim2 = H.size();
  constexpr int kNumFourBinBands = kFftLengthBy2 / 4;
  const FftData* H_j = &H[0];
  const FftData* X = &render_buffer_data[render_buffer.Position()];

  int j = 0;
  int limit = lim1;
  do {
    for (; j < limit; ++j, ++H_j, ++X) {
      for (int k = 0, n = 0; n < kNumFourBinBands; ++n, k += 4) {
        const float32x4_t X_re = vld1q_f32(&X->re[k]);
        const float32x4_t X_im = vld1q_f32(&X->im[k]);
        const float32x4_t H_re = vld1q_f32(&H_j->re[k]);
        const float32x4_t H_im = vld1q_f32(&H_j->im[k]);
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
    limit = lim2;
    X = &render_buffer_data[0];
  } while (j < lim2);

  H_j = &H[0];
  X = &render_buffer_data[render_buffer.Position()];
  j = 0;
  limit = lim1;
  do {
    for (; j < limit; ++j, ++H_j, ++X) {
      S->re[kFftLengthBy2] += X->re[kFftLengthBy2] * H_j->re[kFftLengthBy2] -
                              X->im[kFftLengthBy2] * H_j->im[kFftLengthBy2];
      S->im[kFftLengthBy2] += X->re[kFftLengthBy2] * H_j->im[kFftLengthBy2] +
                              X->im[kFftLengthBy2] * H_j->re[kFftLengthBy2];
    }
    limit = lim2;
    X = &render_buffer_data[0];
  } while (j < lim2);
}
#endif

#if defined(WEBRTC_ARCH_X86_FAMILY)
// Produces the filter output (SSE2 variant).
void ApplyFilter_SSE2(const RenderBuffer& render_buffer,
                      rtc::ArrayView<const FftData> H,
                      FftData* S) {
  RTC_DCHECK_GE(H.size(), H.size() - 1);
  S->re.fill(0.f);
  S->im.fill(0.f);

  rtc::ArrayView<const FftData> render_buffer_data =
      render_buffer.GetFftBuffer();
  const int lim1 =
      std::min(render_buffer_data.size() - render_buffer.Position(), H.size());
  const int lim2 = H.size();
  constexpr int kNumFourBinBands = kFftLengthBy2 / 4;
  const FftData* H_j = &H[0];
  const FftData* X = &render_buffer_data[render_buffer.Position()];

  int j = 0;
  int limit = lim1;
  do {
    for (; j < limit; ++j, ++H_j, ++X) {
      for (int k = 0, n = 0; n < kNumFourBinBands; ++n, k += 4) {
        const __m128 X_re = _mm_loadu_ps(&X->re[k]);
        const __m128 X_im = _mm_loadu_ps(&X->im[k]);
        const __m128 H_re = _mm_loadu_ps(&H_j->re[k]);
        const __m128 H_im = _mm_loadu_ps(&H_j->im[k]);
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
    limit = lim2;
    X = &render_buffer_data[0];
  } while (j < lim2);

  H_j = &H[0];
  X = &render_buffer_data[render_buffer.Position()];
  j = 0;
  limit = lim1;
  do {
    for (; j < limit; ++j, ++H_j, ++X) {
      S->re[kFftLengthBy2] += X->re[kFftLengthBy2] * H_j->re[kFftLengthBy2] -
                              X->im[kFftLengthBy2] * H_j->im[kFftLengthBy2];
      S->im[kFftLengthBy2] += X->re[kFftLengthBy2] * H_j->im[kFftLengthBy2] +
                              X->im[kFftLengthBy2] * H_j->re[kFftLengthBy2];
    }
    limit = lim2;
    X = &render_buffer_data[0];
  } while (j < lim2);
}
#endif

}  // namespace aec3

namespace {

bool EnablePartialFilterReset() {
  return !field_trial::IsEnabled("WebRTC-Aec3PartialFilterResetKillSwitch");
}

}  // namespace

AdaptiveFirFilter::AdaptiveFirFilter(size_t max_size_partitions,
                                     size_t initial_size_partitions,
                                     size_t size_change_duration_blocks,
                                     Aec3Optimization optimization,
                                     ApmDataDumper* data_dumper)
    : data_dumper_(data_dumper),
      use_partial_filter_reset_(EnablePartialFilterReset()),
      fft_(),
      optimization_(optimization),
      max_size_partitions_(max_size_partitions),
      size_change_duration_blocks_(
          static_cast<int>(size_change_duration_blocks)),
      current_size_partitions_(initial_size_partitions),
      target_size_partitions_(initial_size_partitions),
      old_target_size_partitions_(initial_size_partitions),
      H_(max_size_partitions_),
      H2_(max_size_partitions_, std::array<float, kFftLengthBy2Plus1>()),
      h_(GetTimeDomainLength(max_size_partitions_), 0.f) {
  RTC_DCHECK(data_dumper_);
  RTC_DCHECK_GE(max_size_partitions, initial_size_partitions);

  RTC_DCHECK_LT(0, size_change_duration_blocks_);
  one_by_size_change_duration_blocks_ = 1.f / size_change_duration_blocks_;

  for (auto& H_j : H_) {
    H_j.Clear();
  }
  for (auto& H2_k : H2_) {
    H2_k.fill(0.f);
  }
  erl_.fill(0.f);
  SetSizePartitions(current_size_partitions_, true);
}

AdaptiveFirFilter::~AdaptiveFirFilter() = default;

void AdaptiveFirFilter::HandleEchoPathChange() {
  size_t current_h_size = h_.size();
  h_.resize(GetTimeDomainLength(max_size_partitions_));
  const size_t begin_coeffficient =
      use_partial_filter_reset_ ? current_h_size : 0;
  std::fill(h_.begin() + begin_coeffficient, h_.end(), 0.f);
  h_.resize(current_h_size);

  size_t current_size_partitions = H_.size();
  H_.resize(max_size_partitions_);
  H2_.resize(max_size_partitions_);

  const size_t begin_partition =
      use_partial_filter_reset_ ? current_size_partitions : 0;
  for (size_t k = begin_partition; k < max_size_partitions_; ++k) {
    H_[k].Clear();
    H2_[k].fill(0.f);
  }
  H_.resize(current_size_partitions);
  H2_.resize(current_size_partitions);

  erl_.fill(0.f);
}

void AdaptiveFirFilter::SetSizePartitions(size_t size, bool immediate_effect) {
  RTC_DCHECK_EQ(max_size_partitions_, H_.capacity());
  RTC_DCHECK_EQ(max_size_partitions_, H2_.capacity());
  RTC_DCHECK_EQ(GetTimeDomainLength(max_size_partitions_), h_.capacity());
  RTC_DCHECK_EQ(H_.size(), H2_.size());
  RTC_DCHECK_EQ(h_.size(), GetTimeDomainLength(H_.size()));
  RTC_DCHECK_LE(size, max_size_partitions_);

  target_size_partitions_ = std::min(max_size_partitions_, size);
  if (immediate_effect) {
    current_size_partitions_ = old_target_size_partitions_ =
        target_size_partitions_;
    ResetFilterBuffersToCurrentSize();
    size_change_counter_ = 0;
  } else {
    size_change_counter_ = size_change_duration_blocks_;
  }
}

void AdaptiveFirFilter::ResetFilterBuffersToCurrentSize() {
  if (current_size_partitions_ < H_.size()) {
    for (size_t k = current_size_partitions_; k < H_.size(); ++k) {
      H_[k].Clear();
      H2_[k].fill(0.f);
    }
    std::fill(h_.begin() + GetTimeDomainLength(current_size_partitions_),
              h_.end(), 0.f);
  }

  H_.resize(current_size_partitions_);
  H2_.resize(current_size_partitions_);
  h_.resize(GetTimeDomainLength(current_size_partitions_));
  RTC_DCHECK_LT(0, current_size_partitions_);
  partition_to_constrain_ =
      std::min(partition_to_constrain_, current_size_partitions_ - 1);
}

void AdaptiveFirFilter::UpdateSize() {
  RTC_DCHECK_GE(size_change_duration_blocks_, size_change_counter_);
  if (size_change_counter_ > 0) {
    --size_change_counter_;

    auto average = [](float from, float to, float from_weight) {
      return from * from_weight + to * (1.f - from_weight);
    };

    float change_factor =
        size_change_counter_ * one_by_size_change_duration_blocks_;

    current_size_partitions_ = average(old_target_size_partitions_,
                                       target_size_partitions_, change_factor);

    ResetFilterBuffersToCurrentSize();
  } else {
    current_size_partitions_ = old_target_size_partitions_ =
        target_size_partitions_;
  }
  RTC_DCHECK_LE(0, size_change_counter_);
}

void AdaptiveFirFilter::Filter(const RenderBuffer& render_buffer,
                               FftData* S) const {
  RTC_DCHECK(S);
  switch (optimization_) {
#if defined(WEBRTC_ARCH_X86_FAMILY)
    case Aec3Optimization::kSse2:
      aec3::ApplyFilter_SSE2(render_buffer, H_, S);
      break;
#endif
#if defined(WEBRTC_HAS_NEON)
    case Aec3Optimization::kNeon:
      aec3::ApplyFilter_NEON(render_buffer, H_, S);
      break;
#endif
    default:
      aec3::ApplyFilter(render_buffer, H_, S);
  }
}

void AdaptiveFirFilter::Adapt(const RenderBuffer& render_buffer,
                              const FftData& G) {
  // Update the filter size if needed.
  UpdateSize();

  // Adapt the filter.
  switch (optimization_) {
#if defined(WEBRTC_ARCH_X86_FAMILY)
    case Aec3Optimization::kSse2:
      aec3::AdaptPartitions_SSE2(render_buffer, G, H_);
      break;
#endif
#if defined(WEBRTC_HAS_NEON)
    case Aec3Optimization::kNeon:
      aec3::AdaptPartitions_NEON(render_buffer, G, H_);
      break;
#endif
    default:
      aec3::AdaptPartitions(render_buffer, G, H_);
  }

  // Constrain the filter partitions in a cyclic manner.
  Constrain();

  // Update the frequency response and echo return loss for the filter.
  switch (optimization_) {
#if defined(WEBRTC_ARCH_X86_FAMILY)
    case Aec3Optimization::kSse2:
      aec3::UpdateFrequencyResponse_SSE2(H_, &H2_);
      aec3::UpdateErlEstimator_SSE2(H2_, &erl_);
      break;
#endif
#if defined(WEBRTC_HAS_NEON)
    case Aec3Optimization::kNeon:
      aec3::UpdateFrequencyResponse_NEON(H_, &H2_);
      aec3::UpdateErlEstimator_NEON(H2_, &erl_);
      break;
#endif
    default:
      aec3::UpdateFrequencyResponse(H_, &H2_);
      aec3::UpdateErlEstimator(H2_, &erl_);
  }
}

// Constrains the a partiton of the frequency domain filter to be limited in
// time via setting the relevant time-domain coefficients to zero.
void AdaptiveFirFilter::Constrain() {
  std::array<float, kFftLength> h;
  fft_.Ifft(H_[partition_to_constrain_], &h);

  static constexpr float kScale = 1.0f / kFftLengthBy2;
  std::for_each(h.begin(), h.begin() + kFftLengthBy2,
                [](float& a) { a *= kScale; });
  std::fill(h.begin() + kFftLengthBy2, h.end(), 0.f);

  std::copy(h.begin(), h.begin() + kFftLengthBy2,
            h_.begin() + partition_to_constrain_ * kFftLengthBy2);

  fft_.Fft(&h, &H_[partition_to_constrain_]);

  partition_to_constrain_ = partition_to_constrain_ < (H_.size() - 1)
                                ? partition_to_constrain_ + 1
                                : 0;
}

void AdaptiveFirFilter::ScaleFilter(float factor) {
  for (auto& H : H_) {
    for (auto& re : H.re) {
      re *= factor;
    }
    for (auto& im : H.im) {
      im *= factor;
    }
  }
  for (auto& h : h_) {
    h *= factor;
  }
}

// Set the filter coefficients.
void AdaptiveFirFilter::SetFilter(const std::vector<FftData>& H) {
  const size_t num_partitions = std::min(H_.size(), H.size());
  for (size_t k = 0; k < num_partitions; ++k) {
    std::copy(H[k].re.begin(), H[k].re.end(), H_[k].re.begin());
    std::copy(H[k].im.begin(), H[k].im.end(), H_[k].im.begin());
  }
}

}  // namespace webrtc
