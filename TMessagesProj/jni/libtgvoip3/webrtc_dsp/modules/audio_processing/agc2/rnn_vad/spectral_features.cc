/*
 *  Copyright (c) 2018 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "modules/audio_processing/agc2/rnn_vad/spectral_features.h"

#include <algorithm>
#include <cmath>
#include <limits>
#include <numeric>

#include "modules/audio_processing/agc2/rnn_vad/spectral_features_internal.h"
#include "rtc_base/checks.h"

namespace webrtc {
namespace rnn_vad {
namespace {

constexpr float kSilenceThreshold = 0.04f;

// Computes the new spectral difference stats and pushes them into the passed
// symmetric matrix buffer.
void UpdateSpectralDifferenceStats(
    rtc::ArrayView<const float, kNumBands> new_spectral_coeffs,
    const RingBuffer<float, kNumBands, kSpectralCoeffsHistorySize>& ring_buf,
    SymmetricMatrixBuffer<float, kSpectralCoeffsHistorySize>* sym_matrix_buf) {
  RTC_DCHECK(sym_matrix_buf);
  // Compute the new spectral distance stats.
  std::array<float, kSpectralCoeffsHistorySize - 1> distances;
  for (size_t i = 0; i < kSpectralCoeffsHistorySize - 1; ++i) {
    const size_t delay = i + 1;
    auto old_spectral_coeffs = ring_buf.GetArrayView(delay);
    distances[i] = 0.f;
    for (size_t k = 0; k < kNumBands; ++k) {
      const float c = new_spectral_coeffs[k] - old_spectral_coeffs[k];
      distances[i] += c * c;
    }
  }
  // Push the new spectral distance stats into the symmetric matrix buffer.
  sym_matrix_buf->Push(distances);
}

}  // namespace

SpectralFeaturesView::SpectralFeaturesView(
    rtc::ArrayView<float, kNumBands - kNumLowerBands> coeffs,
    rtc::ArrayView<float, kNumLowerBands> average,
    rtc::ArrayView<float, kNumLowerBands> first_derivative,
    rtc::ArrayView<float, kNumLowerBands> second_derivative,
    rtc::ArrayView<float, kNumLowerBands> cross_correlations,
    float* variability)
    : coeffs(coeffs),
      average(average),
      first_derivative(first_derivative),
      second_derivative(second_derivative),
      cross_correlations(cross_correlations),
      variability(variability) {}

SpectralFeaturesView::SpectralFeaturesView(const SpectralFeaturesView&) =
    default;
SpectralFeaturesView::~SpectralFeaturesView() = default;

SpectralFeaturesExtractor::SpectralFeaturesExtractor()
    : fft_(),
      reference_frame_fft_(kFrameSize20ms24kHz),
      lagged_frame_fft_(kFrameSize20ms24kHz),
      band_boundaries_(
          ComputeBandBoundaryIndexes(kSampleRate24kHz, kFrameSize20ms24kHz)),
      dct_table_(ComputeDctTable()) {}

SpectralFeaturesExtractor::~SpectralFeaturesExtractor() = default;

void SpectralFeaturesExtractor::Reset() {
  spectral_coeffs_ring_buf_.Reset();
  spectral_diffs_buf_.Reset();
}

bool SpectralFeaturesExtractor::CheckSilenceComputeFeatures(
    rtc::ArrayView<const float, kFrameSize20ms24kHz> reference_frame,
    rtc::ArrayView<const float, kFrameSize20ms24kHz> lagged_frame,
    SpectralFeaturesView spectral_features) {
  // Analyze reference frame.
  fft_.ForwardFft(reference_frame, reference_frame_fft_);
  ComputeBandEnergies(reference_frame_fft_, band_boundaries_,
                      reference_frame_energy_coeffs_);
  // Check if the reference frame has silence.
  const float tot_energy =
      std::accumulate(reference_frame_energy_coeffs_.begin(),
                      reference_frame_energy_coeffs_.end(), 0.f);
  if (tot_energy < kSilenceThreshold)
    return true;
  // Analyze lagged frame.
  fft_.ForwardFft(lagged_frame, lagged_frame_fft_);
  ComputeBandEnergies(lagged_frame_fft_, band_boundaries_,
                      lagged_frame_energy_coeffs_);
  // Log of the band energies for the reference frame.
  std::array<float, kNumBands> log_band_energy_coeffs;
  ComputeLogBandEnergiesCoefficients(reference_frame_energy_coeffs_,
                                     log_band_energy_coeffs);
  // Decorrelate band-wise log energy coefficients via DCT.
  std::array<float, kNumBands> log_band_energy_coeffs_decorrelated;
  ComputeDct(log_band_energy_coeffs, dct_table_,
             log_band_energy_coeffs_decorrelated);
  // Normalize (based on training set stats).
  log_band_energy_coeffs_decorrelated[0] -= 12;
  log_band_energy_coeffs_decorrelated[1] -= 4;
  // Update the ring buffer and the spectral difference stats.
  spectral_coeffs_ring_buf_.Push(log_band_energy_coeffs_decorrelated);
  UpdateSpectralDifferenceStats(log_band_energy_coeffs_decorrelated,
                                spectral_coeffs_ring_buf_,
                                &spectral_diffs_buf_);
  // Write the higher bands spectral coefficients.
  auto coeffs_src = spectral_coeffs_ring_buf_.GetArrayView(0);
  RTC_DCHECK_EQ(coeffs_src.size() - kNumLowerBands,
                spectral_features.coeffs.size());
  std::copy(coeffs_src.begin() + kNumLowerBands, coeffs_src.end(),
            spectral_features.coeffs.begin());
  // Compute and write remaining features.
  ComputeAvgAndDerivatives(spectral_features.average,
                           spectral_features.first_derivative,
                           spectral_features.second_derivative);
  ComputeCrossCorrelation(spectral_features.cross_correlations);
  RTC_DCHECK(spectral_features.variability);
  *(spectral_features.variability) = ComputeVariability();
  return false;
}

void SpectralFeaturesExtractor::ComputeAvgAndDerivatives(
    rtc::ArrayView<float, kNumLowerBands> average,
    rtc::ArrayView<float, kNumLowerBands> first_derivative,
    rtc::ArrayView<float, kNumLowerBands> second_derivative) {
  auto curr = spectral_coeffs_ring_buf_.GetArrayView(0);
  auto prev1 = spectral_coeffs_ring_buf_.GetArrayView(1);
  auto prev2 = spectral_coeffs_ring_buf_.GetArrayView(2);
  RTC_DCHECK_EQ(average.size(), first_derivative.size());
  RTC_DCHECK_EQ(first_derivative.size(), second_derivative.size());
  RTC_DCHECK_LE(average.size(), curr.size());
  for (size_t i = 0; i < average.size(); ++i) {
    // Average, kernel: [1, 1, 1].
    average[i] = curr[i] + prev1[i] + prev2[i];
    // First derivative, kernel: [1, 0, - 1].
    first_derivative[i] = curr[i] - prev2[i];
    // Second derivative, Laplacian kernel: [1, -2, 1].
    second_derivative[i] = curr[i] - 2 * prev1[i] + prev2[i];
  }
}

void SpectralFeaturesExtractor::ComputeCrossCorrelation(
    rtc::ArrayView<float, kNumLowerBands> cross_correlations) {
  const auto& x = reference_frame_fft_;
  const auto& y = lagged_frame_fft_;
  auto cross_corr = [x, y](const size_t freq_bin_index) -> float {
    return (x[freq_bin_index].real() * y[freq_bin_index].real() +
            x[freq_bin_index].imag() * y[freq_bin_index].imag());
  };
  std::array<float, kNumBands> cross_corr_coeffs;
  constexpr size_t kNumFftPoints = kFrameSize20ms24kHz / 2 + 1;
  ComputeBandCoefficients(cross_corr, band_boundaries_, kNumFftPoints - 1,
                          cross_corr_coeffs);
  // Normalize.
  for (size_t i = 0; i < cross_corr_coeffs.size(); ++i) {
    cross_corr_coeffs[i] =
        cross_corr_coeffs[i] /
        std::sqrt(0.001f + reference_frame_energy_coeffs_[i] *
                               lagged_frame_energy_coeffs_[i]);
  }
  // Decorrelate.
  ComputeDct(cross_corr_coeffs, dct_table_, cross_correlations);
  // Normalize (based on training set stats).
  cross_correlations[0] -= 1.3f;
  cross_correlations[1] -= 0.9f;
}

float SpectralFeaturesExtractor::ComputeVariability() {
  // Compute spectral variability score.
  float spec_variability = 0.f;
  for (size_t delay1 = 0; delay1 < kSpectralCoeffsHistorySize; ++delay1) {
    float min_dist = std::numeric_limits<float>::max();
    for (size_t delay2 = 0; delay2 < kSpectralCoeffsHistorySize; ++delay2) {
      if (delay1 == delay2)  // The distance would be 0.
        continue;
      min_dist =
          std::min(min_dist, spectral_diffs_buf_.GetValue(delay1, delay2));
    }
    spec_variability += min_dist;
  }
  // Normalize (based on training set stats).
  return spec_variability / kSpectralCoeffsHistorySize - 2.1f;
}

}  // namespace rnn_vad
}  // namespace webrtc
