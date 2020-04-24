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

namespace webrtc {
namespace rnn_vad {
namespace {

// DCT scaling factor.
static_assert(
    kNumBands == 22,
    "kNumBands changed! Please update the value of kDctScalingFactor");
constexpr float kDctScalingFactor = 0.301511345f;  // sqrt(2 / kNumBands)

}  // namespace

std::array<size_t, kNumBands> ComputeBandBoundaryIndexes(
    size_t sample_rate_hz,
    size_t frame_size_samples) {
  std::array<size_t, kNumBands> indexes;
  for (size_t i = 0; i < kNumBands; ++i) {
    indexes[i] =
        kBandFrequencyBoundaries[i] * frame_size_samples / sample_rate_hz;
  }
  return indexes;
}

void ComputeBandCoefficients(
    rtc::FunctionView<float(size_t)> functor,
    rtc::ArrayView<const size_t, kNumBands> band_boundaries,
    size_t max_freq_bin_index,
    rtc::ArrayView<float, kNumBands> coefficients) {
  std::fill(coefficients.begin(), coefficients.end(), 0.f);
  for (size_t i = 0; i < coefficients.size() - 1; ++i) {
    RTC_DCHECK_EQ(0.f, coefficients[i + 1]);
    RTC_DCHECK_GT(band_boundaries[i + 1], band_boundaries[i]);
    const size_t first_freq_bin = band_boundaries[i];
    const size_t last_freq_bin =
        std::min(max_freq_bin_index, first_freq_bin + band_boundaries[i + 1] -
                                         band_boundaries[i] - 1);
    // Depending on the sample rate, the highest bands can have no FFT
    // coefficients. Stop the iteration when coming across the first empty band.
    if (first_freq_bin >= last_freq_bin)
      break;
    const size_t band_size = last_freq_bin - first_freq_bin + 1;
    // Compute the band coefficient using a triangular band with peak response
    // at the band boundary.
    for (size_t j = first_freq_bin; j <= last_freq_bin; ++j) {
      const float w = static_cast<float>(j - first_freq_bin) / band_size;
      const float coefficient = functor(j);
      coefficients[i] += (1.f - w) * coefficient;
      coefficients[i + 1] += w * coefficient;
    }
  }
  // The first and the last bands in the loop above only got half contribution.
  coefficients[0] *= 2.f;
  coefficients[coefficients.size() - 1] *= 2.f;
  // TODO(bugs.webrtc.org/9076): Replace the line above with
  // "coefficients[i] *= 2.f" (*) since we now assume that the last band is
  // always |kNumBands| - 1.
  // (*): "size_t i" must be declared before the main loop.
}

void ComputeBandEnergies(
    rtc::ArrayView<const std::complex<float>> fft_coeffs,
    rtc::ArrayView<const size_t, kNumBands> band_boundaries,
    rtc::ArrayView<float, kNumBands> band_energies) {
  RTC_DCHECK_EQ(band_boundaries.size(), band_energies.size());
  auto functor = [fft_coeffs](const size_t freq_bin_index) -> float {
    return std::norm(fft_coeffs[freq_bin_index]);
  };
  ComputeBandCoefficients(functor, band_boundaries, fft_coeffs.size() - 1,
                          band_energies);
}

void ComputeLogBandEnergiesCoefficients(
    rtc::ArrayView<const float, kNumBands> band_energy_coeffs,
    rtc::ArrayView<float, kNumBands> log_band_energy_coeffs) {
  float log_max = -2.f;
  float follow = -2.f;
  for (size_t i = 0; i < band_energy_coeffs.size(); ++i) {
    log_band_energy_coeffs[i] = std::log10(1e-2f + band_energy_coeffs[i]);
    // Smoothing across frequency bands.
    log_band_energy_coeffs[i] = std::max(
        log_max - 7.f, std::max(follow - 1.5f, log_band_energy_coeffs[i]));
    log_max = std::max(log_max, log_band_energy_coeffs[i]);
    follow = std::max(follow - 1.5f, log_band_energy_coeffs[i]);
  }
}

std::array<float, kNumBands * kNumBands> ComputeDctTable() {
  std::array<float, kNumBands * kNumBands> dct_table;
  const double k = std::sqrt(0.5);
  for (size_t i = 0; i < kNumBands; ++i) {
    for (size_t j = 0; j < kNumBands; ++j)
      dct_table[i * kNumBands + j] = std::cos((i + 0.5) * j * kPi / kNumBands);
    dct_table[i * kNumBands] *= k;
  }
  return dct_table;
}

void ComputeDct(rtc::ArrayView<const float, kNumBands> in,
                rtc::ArrayView<const float, kNumBands * kNumBands> dct_table,
                rtc::ArrayView<float> out) {
  RTC_DCHECK_NE(in.data(), out.data()) << "In-place DCT is not supported.";
  RTC_DCHECK_LE(1, out.size());
  RTC_DCHECK_LE(out.size(), in.size());
  std::fill(out.begin(), out.end(), 0.f);
  for (size_t i = 0; i < out.size(); ++i) {
    for (size_t j = 0; j < in.size(); ++j) {
      out[i] += in[j] * dct_table[j * in.size() + i];
    }
    out[i] *= kDctScalingFactor;
  }
}

}  // namespace rnn_vad
}  // namespace webrtc
