/*
 *  Copyright (c) 2018 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "modules/audio_processing/agc2/rnn_vad/pitch_search.h"

#include <array>
#include <cstddef>

#include "rtc_base/checks.h"

namespace webrtc {
namespace rnn_vad {

PitchEstimator::PitchEstimator(const AvailableCpuFeatures& cpu_features)
    : cpu_features_(cpu_features),
      y_energy_24kHz_(kRefineNumLags24kHz, 0.f),
      pitch_buffer_12kHz_(kBufSize12kHz),
      auto_correlation_12kHz_(kNumLags12kHz) {}

PitchEstimator::~PitchEstimator() = default;

int PitchEstimator::Estimate(
    rtc::ArrayView<const float, kBufSize24kHz> pitch_buffer) {
  rtc::ArrayView<float, kBufSize12kHz> pitch_buffer_12kHz_view(
      pitch_buffer_12kHz_.data(), kBufSize12kHz);
  RTC_DCHECK_EQ(pitch_buffer_12kHz_.size(), pitch_buffer_12kHz_view.size());
  rtc::ArrayView<float, kNumLags12kHz> auto_correlation_12kHz_view(
      auto_correlation_12kHz_.data(), kNumLags12kHz);
  RTC_DCHECK_EQ(auto_correlation_12kHz_.size(),
                auto_correlation_12kHz_view.size());

  // TODO(bugs.chromium.org/10480): Use `cpu_features_` to estimate pitch.
  // Perform the initial pitch search at 12 kHz.
  Decimate2x(pitch_buffer, pitch_buffer_12kHz_view);
  auto_corr_calculator_.ComputeOnPitchBuffer(pitch_buffer_12kHz_view,
                                             auto_correlation_12kHz_view);
  CandidatePitchPeriods pitch_periods = ComputePitchPeriod12kHz(
      pitch_buffer_12kHz_view, auto_correlation_12kHz_view, cpu_features_);
  // The refinement is done using the pitch buffer that contains 24 kHz samples.
  // Therefore, adapt the inverted lags in |pitch_candidates_inv_lags| from 12
  // to 24 kHz.
  pitch_periods.best *= 2;
  pitch_periods.second_best *= 2;

  // Refine the initial pitch period estimation from 12 kHz to 48 kHz.
  // Pre-compute frame energies at 24 kHz.
  rtc::ArrayView<float, kRefineNumLags24kHz> y_energy_24kHz_view(
      y_energy_24kHz_.data(), kRefineNumLags24kHz);
  RTC_DCHECK_EQ(y_energy_24kHz_.size(), y_energy_24kHz_view.size());
  ComputeSlidingFrameSquareEnergies24kHz(pitch_buffer, y_energy_24kHz_view,
                                         cpu_features_);
  // Estimation at 48 kHz.
  const int pitch_lag_48kHz = ComputePitchPeriod48kHz(
      pitch_buffer, y_energy_24kHz_view, pitch_periods, cpu_features_);
  last_pitch_48kHz_ = ComputeExtendedPitchPeriod48kHz(
      pitch_buffer, y_energy_24kHz_view,
      /*initial_pitch_period_48kHz=*/kMaxPitch48kHz - pitch_lag_48kHz,
      last_pitch_48kHz_, cpu_features_);
  return last_pitch_48kHz_.period;
}

}  // namespace rnn_vad
}  // namespace webrtc
