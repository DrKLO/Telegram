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

PitchEstimator::PitchEstimator()
    : pitch_buf_decimated_(kBufSize12kHz),
      pitch_buf_decimated_view_(pitch_buf_decimated_.data(), kBufSize12kHz),
      auto_corr_(kNumInvertedLags12kHz),
      auto_corr_view_(auto_corr_.data(), kNumInvertedLags12kHz) {
  RTC_DCHECK_EQ(kBufSize12kHz, pitch_buf_decimated_.size());
  RTC_DCHECK_EQ(kNumInvertedLags12kHz, auto_corr_view_.size());
}

PitchEstimator::~PitchEstimator() = default;

PitchInfo PitchEstimator::Estimate(
    rtc::ArrayView<const float, kBufSize24kHz> pitch_buf) {
  // Perform the initial pitch search at 12 kHz.
  Decimate2x(pitch_buf, pitch_buf_decimated_view_);
  auto_corr_calculator_.ComputeOnPitchBuffer(pitch_buf_decimated_view_,
                                             auto_corr_view_);
  std::array<size_t, 2> pitch_candidates_inv_lags = FindBestPitchPeriods(
      auto_corr_view_, pitch_buf_decimated_view_, kMaxPitch12kHz);
  // Refine the pitch period estimation.
  // The refinement is done using the pitch buffer that contains 24 kHz samples.
  // Therefore, adapt the inverted lags in |pitch_candidates_inv_lags| from 12
  // to 24 kHz.
  pitch_candidates_inv_lags[0] *= 2;
  pitch_candidates_inv_lags[1] *= 2;
  size_t pitch_inv_lag_48kHz =
      RefinePitchPeriod48kHz(pitch_buf, pitch_candidates_inv_lags);
  // Look for stronger harmonics to find the final pitch period and its gain.
  RTC_DCHECK_LT(pitch_inv_lag_48kHz, kMaxPitch48kHz);
  last_pitch_48kHz_ = CheckLowerPitchPeriodsAndComputePitchGain(
      pitch_buf, kMaxPitch48kHz - pitch_inv_lag_48kHz, last_pitch_48kHz_);
  return last_pitch_48kHz_;
}

}  // namespace rnn_vad
}  // namespace webrtc
