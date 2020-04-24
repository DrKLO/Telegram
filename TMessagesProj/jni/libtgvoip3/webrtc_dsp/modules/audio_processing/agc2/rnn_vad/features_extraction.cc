/*
 *  Copyright (c) 2018 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "modules/audio_processing/agc2/rnn_vad/features_extraction.h"

#include <array>

#include "modules/audio_processing/agc2/rnn_vad/lp_residual.h"
#include "rtc_base/checks.h"

namespace webrtc {
namespace rnn_vad {
namespace {

// Generated via "B, A = scipy.signal.butter(2, 30/12000, btype='highpass')"
const BiQuadFilter::BiQuadCoefficients kHpfConfig24k = {
    {0.99446179f, -1.98892358f, 0.99446179f},
    {-1.98889291f, 0.98895425f}};

}  // namespace

FeaturesExtractor::FeaturesExtractor()
    : use_high_pass_filter_(false),
      pitch_buf_24kHz_(),
      pitch_buf_24kHz_view_(pitch_buf_24kHz_.GetBufferView()),
      lp_residual_(kBufSize24kHz),
      lp_residual_view_(lp_residual_.data(), kBufSize24kHz),
      pitch_estimator_(),
      reference_frame_view_(pitch_buf_24kHz_.GetMostRecentValuesView()) {
  RTC_DCHECK_EQ(kBufSize24kHz, lp_residual_.size());
  hpf_.Initialize(kHpfConfig24k);
  Reset();
}

FeaturesExtractor::~FeaturesExtractor() = default;

void FeaturesExtractor::Reset() {
  pitch_buf_24kHz_.Reset();
  spectral_features_extractor_.Reset();
  if (use_high_pass_filter_)
    hpf_.Reset();
}

bool FeaturesExtractor::CheckSilenceComputeFeatures(
    rtc::ArrayView<const float, kFrameSize10ms24kHz> samples,
    rtc::ArrayView<float, kFeatureVectorSize> feature_vector) {
  // Pre-processing.
  if (use_high_pass_filter_) {
    std::array<float, kFrameSize10ms24kHz> samples_filtered;
    hpf_.Process(samples, samples_filtered);
    // Feed buffer with the pre-processed version of |samples|.
    pitch_buf_24kHz_.Push(samples_filtered);
  } else {
    // Feed buffer with |samples|.
    pitch_buf_24kHz_.Push(samples);
  }
  // Extract the LP residual.
  float lpc_coeffs[kNumLpcCoefficients];
  ComputeAndPostProcessLpcCoefficients(pitch_buf_24kHz_view_, lpc_coeffs);
  ComputeLpResidual(lpc_coeffs, pitch_buf_24kHz_view_, lp_residual_view_);
  // Estimate pitch on the LP-residual and write the normalized pitch period
  // into the output vector (normalization based on training data stats).
  pitch_info_48kHz_ = pitch_estimator_.Estimate(lp_residual_view_);
  feature_vector[kFeatureVectorSize - 2] =
      0.01f * (static_cast<int>(pitch_info_48kHz_.period) - 300);
  // Extract lagged frames (according to the estimated pitch period).
  RTC_DCHECK_LE(pitch_info_48kHz_.period / 2, kMaxPitch24kHz);
  auto lagged_frame = pitch_buf_24kHz_view_.subview(
      kMaxPitch24kHz - pitch_info_48kHz_.period / 2, kFrameSize20ms24kHz);
  // Analyze reference and lagged frames checking if silence has been detected
  // and write the feature vector.
  return spectral_features_extractor_.CheckSilenceComputeFeatures(
      reference_frame_view_, {lagged_frame.data(), kFrameSize20ms24kHz},
      {{feature_vector.data() + kNumLowerBands, kNumBands - kNumLowerBands},
       {feature_vector.data(), kNumLowerBands},
       {feature_vector.data() + kNumBands, kNumLowerBands},
       {feature_vector.data() + kNumBands + kNumLowerBands, kNumLowerBands},
       {feature_vector.data() + kNumBands + 2 * kNumLowerBands, kNumLowerBands},
       &feature_vector[kFeatureVectorSize - 1]});
}

}  // namespace rnn_vad
}  // namespace webrtc
