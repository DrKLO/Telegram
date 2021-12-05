/*
 *  Copyright (c) 2019 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "modules/audio_processing/ns/speech_probability_estimator.h"

#include <math.h>
#include <algorithm>

#include "modules/audio_processing/ns/fast_math.h"
#include "rtc_base/checks.h"

namespace webrtc {

SpeechProbabilityEstimator::SpeechProbabilityEstimator() {
  speech_probability_.fill(0.f);
}

void SpeechProbabilityEstimator::Update(
    int32_t num_analyzed_frames,
    rtc::ArrayView<const float, kFftSizeBy2Plus1> prior_snr,
    rtc::ArrayView<const float, kFftSizeBy2Plus1> post_snr,
    rtc::ArrayView<const float, kFftSizeBy2Plus1> conservative_noise_spectrum,
    rtc::ArrayView<const float, kFftSizeBy2Plus1> signal_spectrum,
    float signal_spectral_sum,
    float signal_energy) {
  // Update models.
  if (num_analyzed_frames < kLongStartupPhaseBlocks) {
    signal_model_estimator_.AdjustNormalization(num_analyzed_frames,
                                                signal_energy);
  }
  signal_model_estimator_.Update(prior_snr, post_snr,
                                 conservative_noise_spectrum, signal_spectrum,
                                 signal_spectral_sum, signal_energy);

  const SignalModel& model = signal_model_estimator_.get_model();
  const PriorSignalModel& prior_model =
      signal_model_estimator_.get_prior_model();

  // Width parameter in sigmoid map for prior model.
  constexpr float kWidthPrior0 = 4.f;
  // Width for pause region: lower range, so increase width in tanh map.
  constexpr float kWidthPrior1 = 2.f * kWidthPrior0;

  // Average LRT feature: use larger width in tanh map for pause regions.
  float width_prior = model.lrt < prior_model.lrt ? kWidthPrior1 : kWidthPrior0;

  // Compute indicator function: sigmoid map.
  float indicator0 =
      0.5f * (tanh(width_prior * (model.lrt - prior_model.lrt)) + 1.f);

  // Spectral flatness feature: use larger width in tanh map for pause regions.
  width_prior = model.spectral_flatness > prior_model.flatness_threshold
                    ? kWidthPrior1
                    : kWidthPrior0;

  // Compute indicator function: sigmoid map.
  float indicator1 =
      0.5f * (tanh(1.f * width_prior *
                   (prior_model.flatness_threshold - model.spectral_flatness)) +
              1.f);

  // For template spectrum-difference : use larger width in tanh map for pause
  // regions.
  width_prior = model.spectral_diff < prior_model.template_diff_threshold
                    ? kWidthPrior1
                    : kWidthPrior0;

  // Compute indicator function: sigmoid map.
  float indicator2 =
      0.5f * (tanh(width_prior * (model.spectral_diff -
                                  prior_model.template_diff_threshold)) +
              1.f);

  // Combine the indicator function with the feature weights.
  float ind_prior = prior_model.lrt_weighting * indicator0 +
                    prior_model.flatness_weighting * indicator1 +
                    prior_model.difference_weighting * indicator2;

  // Compute the prior probability.
  prior_speech_prob_ += 0.1f * (ind_prior - prior_speech_prob_);

  // Make sure probabilities are within range: keep floor to 0.01.
  prior_speech_prob_ = std::max(std::min(prior_speech_prob_, 1.f), 0.01f);

  // Final speech probability: combine prior model with LR factor:.
  float gain_prior =
      (1.f - prior_speech_prob_) / (prior_speech_prob_ + 0.0001f);

  std::array<float, kFftSizeBy2Plus1> inv_lrt;
  ExpApproximationSignFlip(model.avg_log_lrt, inv_lrt);
  for (size_t i = 0; i < kFftSizeBy2Plus1; ++i) {
    speech_probability_[i] = 1.f / (1.f + gain_prior * inv_lrt[i]);
  }
}

}  // namespace webrtc
