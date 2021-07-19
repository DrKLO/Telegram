/*
 *  Copyright (c) 2018 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "modules/audio_processing/agc2/rnn_vad/spectral_features_internal.h"

#include <algorithm>
#include <cmath>
#include <cstddef>

#include "rtc_base/checks.h"
#include "rtc_base/numerics/safe_compare.h"

namespace webrtc {
namespace rnn_vad {
namespace {

// Weights for each FFT coefficient for each Opus band (Nyquist frequency
// excluded). The size of each band is specified in
// |kOpusScaleNumBins24kHz20ms|.
constexpr std::array<float, kFrameSize20ms24kHz / 2> kOpusBandWeights24kHz20ms =
    {{
        0.f,       0.25f,      0.5f,       0.75f,  // Band 0
        0.f,       0.25f,      0.5f,       0.75f,  // Band 1
        0.f,       0.25f,      0.5f,       0.75f,  // Band 2
        0.f,       0.25f,      0.5f,       0.75f,  // Band 3
        0.f,       0.25f,      0.5f,       0.75f,  // Band 4
        0.f,       0.25f,      0.5f,       0.75f,  // Band 5
        0.f,       0.25f,      0.5f,       0.75f,  // Band 6
        0.f,       0.25f,      0.5f,       0.75f,  // Band 7
        0.f,       0.125f,     0.25f,      0.375f,    0.5f,
        0.625f,    0.75f,      0.875f,  // Band 8
        0.f,       0.125f,     0.25f,      0.375f,    0.5f,
        0.625f,    0.75f,      0.875f,  // Band 9
        0.f,       0.125f,     0.25f,      0.375f,    0.5f,
        0.625f,    0.75f,      0.875f,  // Band 10
        0.f,       0.125f,     0.25f,      0.375f,    0.5f,
        0.625f,    0.75f,      0.875f,  // Band 11
        0.f,       0.0625f,    0.125f,     0.1875f,   0.25f,
        0.3125f,   0.375f,     0.4375f,    0.5f,      0.5625f,
        0.625f,    0.6875f,    0.75f,      0.8125f,   0.875f,
        0.9375f,  // Band 12
        0.f,       0.0625f,    0.125f,     0.1875f,   0.25f,
        0.3125f,   0.375f,     0.4375f,    0.5f,      0.5625f,
        0.625f,    0.6875f,    0.75f,      0.8125f,   0.875f,
        0.9375f,  // Band 13
        0.f,       0.0625f,    0.125f,     0.1875f,   0.25f,
        0.3125f,   0.375f,     0.4375f,    0.5f,      0.5625f,
        0.625f,    0.6875f,    0.75f,      0.8125f,   0.875f,
        0.9375f,  // Band 14
        0.f,       0.0416667f, 0.0833333f, 0.125f,    0.166667f,
        0.208333f, 0.25f,      0.291667f,  0.333333f, 0.375f,
        0.416667f, 0.458333f,  0.5f,       0.541667f, 0.583333f,
        0.625f,    0.666667f,  0.708333f,  0.75f,     0.791667f,
        0.833333f, 0.875f,     0.916667f,  0.958333f,  // Band 15
        0.f,       0.0416667f, 0.0833333f, 0.125f,    0.166667f,
        0.208333f, 0.25f,      0.291667f,  0.333333f, 0.375f,
        0.416667f, 0.458333f,  0.5f,       0.541667f, 0.583333f,
        0.625f,    0.666667f,  0.708333f,  0.75f,     0.791667f,
        0.833333f, 0.875f,     0.916667f,  0.958333f,  // Band 16
        0.f,       0.03125f,   0.0625f,    0.09375f,  0.125f,
        0.15625f,  0.1875f,    0.21875f,   0.25f,     0.28125f,
        0.3125f,   0.34375f,   0.375f,     0.40625f,  0.4375f,
        0.46875f,  0.5f,       0.53125f,   0.5625f,   0.59375f,
        0.625f,    0.65625f,   0.6875f,    0.71875f,  0.75f,
        0.78125f,  0.8125f,    0.84375f,   0.875f,    0.90625f,
        0.9375f,   0.96875f,  // Band 17
        0.f,       0.0208333f, 0.0416667f, 0.0625f,   0.0833333f,
        0.104167f, 0.125f,     0.145833f,  0.166667f, 0.1875f,
        0.208333f, 0.229167f,  0.25f,      0.270833f, 0.291667f,
        0.3125f,   0.333333f,  0.354167f,  0.375f,    0.395833f,
        0.416667f, 0.4375f,    0.458333f,  0.479167f, 0.5f,
        0.520833f, 0.541667f,  0.5625f,    0.583333f, 0.604167f,
        0.625f,    0.645833f,  0.666667f,  0.6875f,   0.708333f,
        0.729167f, 0.75f,      0.770833f,  0.791667f, 0.8125f,
        0.833333f, 0.854167f,  0.875f,     0.895833f, 0.916667f,
        0.9375f,   0.958333f,  0.979167f  // Band 18
    }};

}  // namespace

SpectralCorrelator::SpectralCorrelator()
    : weights_(kOpusBandWeights24kHz20ms.begin(),
               kOpusBandWeights24kHz20ms.end()) {}

SpectralCorrelator::~SpectralCorrelator() = default;

void SpectralCorrelator::ComputeAutoCorrelation(
    rtc::ArrayView<const float> x,
    rtc::ArrayView<float, kOpusBands24kHz> auto_corr) const {
  ComputeCrossCorrelation(x, x, auto_corr);
}

void SpectralCorrelator::ComputeCrossCorrelation(
    rtc::ArrayView<const float> x,
    rtc::ArrayView<const float> y,
    rtc::ArrayView<float, kOpusBands24kHz> cross_corr) const {
  RTC_DCHECK_EQ(x.size(), kFrameSize20ms24kHz);
  RTC_DCHECK_EQ(x.size(), y.size());
  RTC_DCHECK_EQ(x[1], 0.f) << "The Nyquist coefficient must be zeroed.";
  RTC_DCHECK_EQ(y[1], 0.f) << "The Nyquist coefficient must be zeroed.";
  constexpr auto kOpusScaleNumBins24kHz20ms = GetOpusScaleNumBins24kHz20ms();
  int k = 0;  // Next Fourier coefficient index.
  cross_corr[0] = 0.f;
  for (int i = 0; i < kOpusBands24kHz - 1; ++i) {
    cross_corr[i + 1] = 0.f;
    for (int j = 0; j < kOpusScaleNumBins24kHz20ms[i]; ++j) {  // Band size.
      const float v = x[2 * k] * y[2 * k] + x[2 * k + 1] * y[2 * k + 1];
      const float tmp = weights_[k] * v;
      cross_corr[i] += v - tmp;
      cross_corr[i + 1] += tmp;
      k++;
    }
  }
  cross_corr[0] *= 2.f;  // The first band only gets half contribution.
  RTC_DCHECK_EQ(k, kFrameSize20ms24kHz / 2);  // Nyquist coefficient never used.
}

void ComputeSmoothedLogMagnitudeSpectrum(
    rtc::ArrayView<const float> bands_energy,
    rtc::ArrayView<float, kNumBands> log_bands_energy) {
  RTC_DCHECK_LE(bands_energy.size(), kNumBands);
  constexpr float kOneByHundred = 1e-2f;
  constexpr float kLogOneByHundred = -2.f;
  // Init.
  float log_max = kLogOneByHundred;
  float follow = kLogOneByHundred;
  const auto smooth = [&log_max, &follow](float x) {
    x = std::max(log_max - 7.f, std::max(follow - 1.5f, x));
    log_max = std::max(log_max, x);
    follow = std::max(follow - 1.5f, x);
    return x;
  };
  // Smoothing over the bands for which the band energy is defined.
  for (int i = 0; rtc::SafeLt(i, bands_energy.size()); ++i) {
    log_bands_energy[i] = smooth(std::log10(kOneByHundred + bands_energy[i]));
  }
  // Smoothing over the remaining bands (zero energy).
  for (int i = bands_energy.size(); i < kNumBands; ++i) {
    log_bands_energy[i] = smooth(kLogOneByHundred);
  }
}

std::array<float, kNumBands * kNumBands> ComputeDctTable() {
  std::array<float, kNumBands * kNumBands> dct_table;
  const double k = std::sqrt(0.5);
  for (int i = 0; i < kNumBands; ++i) {
    for (int j = 0; j < kNumBands; ++j)
      dct_table[i * kNumBands + j] = std::cos((i + 0.5) * j * kPi / kNumBands);
    dct_table[i * kNumBands] *= k;
  }
  return dct_table;
}

void ComputeDct(rtc::ArrayView<const float> in,
                rtc::ArrayView<const float, kNumBands * kNumBands> dct_table,
                rtc::ArrayView<float> out) {
  // DCT scaling factor - i.e., sqrt(2 / kNumBands).
  constexpr float kDctScalingFactor = 0.301511345f;
  constexpr float kDctScalingFactorError =
      kDctScalingFactor * kDctScalingFactor -
      2.f / static_cast<float>(kNumBands);
  static_assert(
      (kDctScalingFactorError >= 0.f && kDctScalingFactorError < 1e-1f) ||
          (kDctScalingFactorError < 0.f && kDctScalingFactorError > -1e-1f),
      "kNumBands changed and kDctScalingFactor has not been updated.");
  RTC_DCHECK_NE(in.data(), out.data()) << "In-place DCT is not supported.";
  RTC_DCHECK_LE(in.size(), kNumBands);
  RTC_DCHECK_LE(1, out.size());
  RTC_DCHECK_LE(out.size(), in.size());
  for (int i = 0; rtc::SafeLt(i, out.size()); ++i) {
    out[i] = 0.f;
    for (int j = 0; rtc::SafeLt(j, in.size()); ++j) {
      out[i] += in[j] * dct_table[j * kNumBands + i];
    }
    // TODO(bugs.webrtc.org/10480): Scaling factor in the DCT table.
    out[i] *= kDctScalingFactor;
  }
}

}  // namespace rnn_vad
}  // namespace webrtc
