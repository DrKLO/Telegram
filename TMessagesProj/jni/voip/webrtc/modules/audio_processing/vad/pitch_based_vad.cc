/*
 *  Copyright (c) 2012 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "modules/audio_processing/vad/pitch_based_vad.h"

#include <string.h>

#include "modules/audio_processing/vad/common.h"
#include "modules/audio_processing/vad/noise_gmm_tables.h"
#include "modules/audio_processing/vad/vad_circular_buffer.h"
#include "modules/audio_processing/vad/voice_gmm_tables.h"

namespace webrtc {

static_assert(kNoiseGmmDim == kVoiceGmmDim,
              "noise and voice gmm dimension not equal");

// These values should match MATLAB counterparts for unit-tests to pass.
static const int kPosteriorHistorySize = 500;  // 5 sec of 10 ms frames.
static const double kInitialPriorProbability = 0.3;
static const int kTransientWidthThreshold = 7;
static const double kLowProbabilityThreshold = 0.2;

static double LimitProbability(double p) {
  const double kLimHigh = 0.99;
  const double kLimLow = 0.01;

  if (p > kLimHigh)
    p = kLimHigh;
  else if (p < kLimLow)
    p = kLimLow;
  return p;
}

PitchBasedVad::PitchBasedVad()
    : p_prior_(kInitialPriorProbability),
      circular_buffer_(VadCircularBuffer::Create(kPosteriorHistorySize)) {
  // Setup noise GMM.
  noise_gmm_.dimension = kNoiseGmmDim;
  noise_gmm_.num_mixtures = kNoiseGmmNumMixtures;
  noise_gmm_.weight = kNoiseGmmWeights;
  noise_gmm_.mean = &kNoiseGmmMean[0][0];
  noise_gmm_.covar_inverse = &kNoiseGmmCovarInverse[0][0][0];

  // Setup voice GMM.
  voice_gmm_.dimension = kVoiceGmmDim;
  voice_gmm_.num_mixtures = kVoiceGmmNumMixtures;
  voice_gmm_.weight = kVoiceGmmWeights;
  voice_gmm_.mean = &kVoiceGmmMean[0][0];
  voice_gmm_.covar_inverse = &kVoiceGmmCovarInverse[0][0][0];
}

PitchBasedVad::~PitchBasedVad() {}

int PitchBasedVad::VoicingProbability(const AudioFeatures& features,
                                      double* p_combined) {
  double p;
  double gmm_features[3];
  double pdf_features_given_voice;
  double pdf_features_given_noise;
  // These limits are the same in matlab implementation 'VoicingProbGMM().'
  const double kLimLowLogPitchGain = -2.0;
  const double kLimHighLogPitchGain = -0.9;
  const double kLimLowSpectralPeak = 200;
  const double kLimHighSpectralPeak = 2000;
  const double kEps = 1e-12;
  for (size_t n = 0; n < features.num_frames; n++) {
    gmm_features[0] = features.log_pitch_gain[n];
    gmm_features[1] = features.spectral_peak[n];
    gmm_features[2] = features.pitch_lag_hz[n];

    pdf_features_given_voice = EvaluateGmm(gmm_features, voice_gmm_);
    pdf_features_given_noise = EvaluateGmm(gmm_features, noise_gmm_);

    if (features.spectral_peak[n] < kLimLowSpectralPeak ||
        features.spectral_peak[n] > kLimHighSpectralPeak ||
        features.log_pitch_gain[n] < kLimLowLogPitchGain) {
      pdf_features_given_voice = kEps * pdf_features_given_noise;
    } else if (features.log_pitch_gain[n] > kLimHighLogPitchGain) {
      pdf_features_given_noise = kEps * pdf_features_given_voice;
    }

    p = p_prior_ * pdf_features_given_voice /
        (pdf_features_given_voice * p_prior_ +
         pdf_features_given_noise * (1 - p_prior_));

    p = LimitProbability(p);

    // Combine pitch-based probability with standalone probability, before
    // updating prior probabilities.
    double prod_active = p * p_combined[n];
    double prod_inactive = (1 - p) * (1 - p_combined[n]);
    p_combined[n] = prod_active / (prod_active + prod_inactive);

    if (UpdatePrior(p_combined[n]) < 0)
      return -1;
    // Limit prior probability. With a zero prior probability the posterior
    // probability is always zero.
    p_prior_ = LimitProbability(p_prior_);
  }
  return 0;
}

int PitchBasedVad::UpdatePrior(double p) {
  circular_buffer_->Insert(p);
  if (circular_buffer_->RemoveTransient(kTransientWidthThreshold,
                                        kLowProbabilityThreshold) < 0)
    return -1;
  p_prior_ = circular_buffer_->Mean();
  return 0;
}

}  // namespace webrtc
