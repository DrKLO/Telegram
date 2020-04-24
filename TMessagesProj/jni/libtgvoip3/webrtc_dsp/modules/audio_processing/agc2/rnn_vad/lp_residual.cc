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

namespace webrtc {
namespace rnn_vad {
namespace {

// Computes cross-correlation coefficients between |x| and |y| and writes them
// in |x_corr|. The lag values are in {0, ..., max_lag - 1}, where max_lag
// equals the size of |x_corr|.
// The |x| and |y| sub-arrays used to compute a cross-correlation coefficients
// for a lag l have both size "size of |x| - l" - i.e., the longest sub-array is
// used. |x| and |y| must have the same size.
void ComputeCrossCorrelation(
    rtc::ArrayView<const float> x,
    rtc::ArrayView<const float> y,
    rtc::ArrayView<float, kNumLpcCoefficients> x_corr) {
  constexpr size_t max_lag = x_corr.size();
  RTC_DCHECK_EQ(x.size(), y.size());
  RTC_DCHECK_LT(max_lag, x.size());
  for (size_t lag = 0; lag < max_lag; ++lag) {
    x_corr[lag] =
        std::inner_product(x.begin(), x.end() - lag, y.begin() + lag, 0.f);
  }
}

// Applies denoising to the auto-correlation coefficients.
void DenoiseAutoCorrelation(
    rtc::ArrayView<float, kNumLpcCoefficients> auto_corr) {
  // Assume -40 dB white noise floor.
  auto_corr[0] *= 1.0001f;
  for (size_t i = 1; i < kNumLpcCoefficients; ++i) {
    auto_corr[i] -= auto_corr[i] * (0.008f * i) * (0.008f * i);
  }
}

// Computes the initial inverse filter coefficients given the auto-correlation
// coefficients of an input frame.
void ComputeInitialInverseFilterCoefficients(
    rtc::ArrayView<const float, kNumLpcCoefficients> auto_corr,
    rtc::ArrayView<float, kNumLpcCoefficients - 1> lpc_coeffs) {
  float error = auto_corr[0];
  for (size_t i = 0; i < kNumLpcCoefficients - 1; ++i) {
    float reflection_coeff = 0.f;
    for (size_t j = 0; j < i; ++j) {
      reflection_coeff += lpc_coeffs[j] * auto_corr[i - j];
    }
    reflection_coeff += auto_corr[i + 1];

    // Avoid division by numbers close to zero.
    constexpr float kMinErrorMagnitude = 1e-6f;
    if (std::fabs(error) < kMinErrorMagnitude) {
      error =static_cast<float>(copysign(kMinErrorMagnitude, error));
    }

    reflection_coeff /= -error;
    // Update LPC coefficients and total error.
    lpc_coeffs[i] = reflection_coeff;
    for (size_t j = 0; j<(i + 1)>> 1; ++j) {
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
  ComputeCrossCorrelation(x, x, {auto_corr.data(), auto_corr.size()});
  if (auto_corr[0] == 0.f) {  // Empty frame.
    std::fill(lpc_coeffs.begin(), lpc_coeffs.end(), 0);
    return;
  }
  DenoiseAutoCorrelation({auto_corr.data(), auto_corr.size()});
  std::array<float, kNumLpcCoefficients - 1> lpc_coeffs_pre{};
  ComputeInitialInverseFilterCoefficients(auto_corr, lpc_coeffs_pre);
  // LPC coefficients post-processing.
  // TODO(bugs.webrtc.org/9076): Consider removing these steps.
  float c1 = 1.f;
  for (size_t i = 0; i < kNumLpcCoefficients - 1; ++i) {
    c1 *= 0.9f;
    lpc_coeffs_pre[i] *= c1;
  }
  const float c2 = 0.8f;
  lpc_coeffs[0] = lpc_coeffs_pre[0] + c2;
  lpc_coeffs[1] = lpc_coeffs_pre[1] + c2 * lpc_coeffs_pre[0];
  lpc_coeffs[2] = lpc_coeffs_pre[2] + c2 * lpc_coeffs_pre[1];
  lpc_coeffs[3] = lpc_coeffs_pre[3] + c2 * lpc_coeffs_pre[2];
  lpc_coeffs[4] = c2 * lpc_coeffs_pre[3];
}

void ComputeLpResidual(
    rtc::ArrayView<const float, kNumLpcCoefficients> lpc_coeffs,
    rtc::ArrayView<const float> x,
    rtc::ArrayView<float> y) {
  RTC_DCHECK_LT(kNumLpcCoefficients, x.size());
  RTC_DCHECK_EQ(x.size(), y.size());
  std::array<float, kNumLpcCoefficients> input_chunk;
  input_chunk.fill(0.f);
  for (size_t i = 0; i < y.size(); ++i) {
    const float sum = std::inner_product(input_chunk.begin(), input_chunk.end(),
                                         lpc_coeffs.begin(), x[i]);
    // Circular shift and add a new sample.
    for (size_t j = kNumLpcCoefficients - 1; j > 0; --j)
      input_chunk[j] = input_chunk[j - 1];
    input_chunk[0] = x[i];
    // Copy result.
    y[i] = sum;
  }
}

}  // namespace rnn_vad
}  // namespace webrtc
