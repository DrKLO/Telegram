/*
 *  Copyright (c) 2018 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "modules/audio_processing/agc2/rnn_vad/lp_residual.h"

#include <algorithm>
#include <array>
#include <cmath>
#include <numeric>

#include "rtc_base/checks.h"
#include "rtc_base/numerics/safe_compare.h"

namespace webrtc {
namespace rnn_vad {
namespace {

// Computes auto-correlation coefficients for |x| and writes them in
// |auto_corr|. The lag values are in {0, ..., max_lag - 1}, where max_lag
// equals the size of |auto_corr|.
void ComputeAutoCorrelation(
    rtc::ArrayView<const float> x,
    rtc::ArrayView<float, kNumLpcCoefficients> auto_corr) {
  constexpr int max_lag = auto_corr.size();
  RTC_DCHECK_LT(max_lag, x.size());
  for (int lag = 0; lag < max_lag; ++lag) {
    auto_corr[lag] =
        std::inner_product(x.begin(), x.end() - lag, x.begin() + lag, 0.f);
  }
}

// Applies denoising to the auto-correlation coefficients.
void DenoiseAutoCorrelation(
    rtc::ArrayView<float, kNumLpcCoefficients> auto_corr) {
  // Assume -40 dB white noise floor.
  auto_corr[0] *= 1.0001f;
  // Hard-coded values obtained as
  // [np.float32((0.008*0.008*i*i)) for i in range(1,5)].
  auto_corr[1] -= auto_corr[1] * 0.000064f;
  auto_corr[2] -= auto_corr[2] * 0.000256f;
  auto_corr[3] -= auto_corr[3] * 0.000576f;
  auto_corr[4] -= auto_corr[4] * 0.001024f;
  static_assert(kNumLpcCoefficients == 5, "Update `auto_corr`.");
}

// Computes the initial inverse filter coefficients given the auto-correlation
// coefficients of an input frame.
void ComputeInitialInverseFilterCoefficients(
    rtc::ArrayView<const float, kNumLpcCoefficients> auto_corr,
    rtc::ArrayView<float, kNumLpcCoefficients - 1> lpc_coeffs) {
  float error = auto_corr[0];
  for (int i = 0; i < kNumLpcCoefficients - 1; ++i) {
    float reflection_coeff = 0.f;
    for (int j = 0; j < i; ++j) {
      reflection_coeff += lpc_coeffs[j] * auto_corr[i - j];
    }
    reflection_coeff += auto_corr[i + 1];

    // Avoid division by numbers close to zero.
    constexpr float kMinErrorMagnitude = 1e-6f;
    if (std::fabs(error) < kMinErrorMagnitude) {
      error = std::copysign(kMinErrorMagnitude, error);
    }

    reflection_coeff /= -error;
    // Update LPC coefficients and total error.
    lpc_coeffs[i] = reflection_coeff;
    for (int j = 0; j < ((i + 1) >> 1); ++j) {
      const float tmp1 = lpc_coeffs[j];
      const float tmp2 = lpc_coeffs[i - 1 - j];
      lpc_coeffs[j] = tmp1 + reflection_coeff * tmp2;
      lpc_coeffs[i - 1 - j] = tmp2 + reflection_coeff * tmp1;
    }
    error -= reflection_coeff * reflection_coeff * error;
    if (error < 0.001f * auto_corr[0]) {
      break;
    }
  }
}

}  // namespace

void ComputeAndPostProcessLpcCoefficients(
    rtc::ArrayView<const float> x,
    rtc::ArrayView<float, kNumLpcCoefficients> lpc_coeffs) {
  std::array<float, kNumLpcCoefficients> auto_corr;
  ComputeAutoCorrelation(x, auto_corr);
  if (auto_corr[0] == 0.f) {  // Empty frame.
    std::fill(lpc_coeffs.begin(), lpc_coeffs.end(), 0);
    return;
  }
  DenoiseAutoCorrelation(auto_corr);
  std::array<float, kNumLpcCoefficients - 1> lpc_coeffs_pre{};
  ComputeInitialInverseFilterCoefficients(auto_corr, lpc_coeffs_pre);
  // LPC coefficients post-processing.
  // TODO(bugs.webrtc.org/9076): Consider removing these steps.
  lpc_coeffs_pre[0] *= 0.9f;
  lpc_coeffs_pre[1] *= 0.9f * 0.9f;
  lpc_coeffs_pre[2] *= 0.9f * 0.9f * 0.9f;
  lpc_coeffs_pre[3] *= 0.9f * 0.9f * 0.9f * 0.9f;
  constexpr float kC = 0.8f;
  lpc_coeffs[0] = lpc_coeffs_pre[0] + kC;
  lpc_coeffs[1] = lpc_coeffs_pre[1] + kC * lpc_coeffs_pre[0];
  lpc_coeffs[2] = lpc_coeffs_pre[2] + kC * lpc_coeffs_pre[1];
  lpc_coeffs[3] = lpc_coeffs_pre[3] + kC * lpc_coeffs_pre[2];
  lpc_coeffs[4] = kC * lpc_coeffs_pre[3];
  static_assert(kNumLpcCoefficients == 5, "Update `lpc_coeffs(_pre)`.");
}

void ComputeLpResidual(
    rtc::ArrayView<const float, kNumLpcCoefficients> lpc_coeffs,
    rtc::ArrayView<const float> x,
    rtc::ArrayView<float> y) {
  RTC_DCHECK_GT(x.size(), kNumLpcCoefficients);
  RTC_DCHECK_EQ(x.size(), y.size());
  // The code below implements the following operation:
  // y[i] = x[i] + dot_product({x[i], ..., x[i - kNumLpcCoefficients + 1]},
  //                           lpc_coeffs)
  // Edge case: i < kNumLpcCoefficients.
  y[0] = x[0];
  for (int i = 1; i < kNumLpcCoefficients; ++i) {
    y[i] =
        std::inner_product(x.crend() - i, x.crend(), lpc_coeffs.cbegin(), x[i]);
  }
  // Regular case.
  auto last = x.crend();
  for (int i = kNumLpcCoefficients; rtc::SafeLt(i, y.size()); ++i, --last) {
    y[i] = std::inner_product(last - kNumLpcCoefficients, last,
                              lpc_coeffs.cbegin(), x[i]);
  }
}

}  // namespace rnn_vad
}  // namespace webrtc
