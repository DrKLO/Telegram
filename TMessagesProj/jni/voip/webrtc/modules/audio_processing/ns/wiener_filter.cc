/*
 *  Copyright (c) 2019 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "modules/audio_processing/ns/wiener_filter.h"

#include <math.h>
#include <stdlib.h>
#include <string.h>

#include <algorithm>

#include "modules/audio_processing/ns/fast_math.h"
#include "rtc_base/checks.h"

namespace webrtc {

WienerFilter::WienerFilter(const SuppressionParams& suppression_params)
    : suppression_params_(suppression_params) {
  filter_.fill(1.f);
  initial_spectral_estimate_.fill(0.f);
  spectrum_prev_process_.fill(0.f);
}

void WienerFilter::Update(
    int32_t num_analyzed_frames,
    rtc::ArrayView<const float, kFftSizeBy2Plus1> noise_spectrum,
    rtc::ArrayView<const float, kFftSizeBy2Plus1> prev_noise_spectrum,
    rtc::ArrayView<const float, kFftSizeBy2Plus1> parametric_noise_spectrum,
    rtc::ArrayView<const float, kFftSizeBy2Plus1> signal_spectrum) {
  for (size_t i = 0; i < kFftSizeBy2Plus1; ++i) {
    // Previous estimate based on previous frame with gain filter.
    float prev_tsa = spectrum_prev_process_[i] /
                     (prev_noise_spectrum[i] + 0.0001f) * filter_[i];

    // Current estimate.
    float current_tsa;
    if (signal_spectrum[i] > noise_spectrum[i]) {
      current_tsa = signal_spectrum[i] / (noise_spectrum[i] + 0.0001f) - 1.f;
    } else {
      current_tsa = 0.f;
    }

    // Directed decision estimate is sum of two terms: current estimate and
    // previous estimate.
    float snr_prior = 0.98f * prev_tsa + (1.f - 0.98f) * current_tsa;
    filter_[i] =
        snr_prior / (suppression_params_.over_subtraction_factor + snr_prior);
    filter_[i] = std::max(std::min(filter_[i], 1.f),
                          suppression_params_.minimum_attenuating_gain);
  }

  if (num_analyzed_frames < kShortStartupPhaseBlocks) {
    for (size_t i = 0; i < kFftSizeBy2Plus1; ++i) {
      initial_spectral_estimate_[i] += signal_spectrum[i];
      float filter_initial = initial_spectral_estimate_[i] -
                             suppression_params_.over_subtraction_factor *
                                 parametric_noise_spectrum[i];
      filter_initial /= initial_spectral_estimate_[i] + 0.0001f;

      filter_initial = std::max(std::min(filter_initial, 1.f),
                                suppression_params_.minimum_attenuating_gain);

      // Weight the two suppression filters.
      constexpr float kOnyByShortStartupPhaseBlocks =
          1.f / kShortStartupPhaseBlocks;
      filter_initial *= kShortStartupPhaseBlocks - num_analyzed_frames;
      filter_[i] *= num_analyzed_frames;
      filter_[i] += filter_initial;
      filter_[i] *= kOnyByShortStartupPhaseBlocks;
    }
  }

  std::copy(signal_spectrum.begin(), signal_spectrum.end(),
            spectrum_prev_process_.begin());
}

float WienerFilter::ComputeOverallScalingFactor(
    int32_t num_analyzed_frames,
    float prior_speech_probability,
    float energy_before_filtering,
    float energy_after_filtering) const {
  if (!suppression_params_.use_attenuation_adjustment ||
      num_analyzed_frames <= kLongStartupPhaseBlocks) {
    return 1.f;
  }

  float gain = SqrtFastApproximation(energy_after_filtering /
                                     (energy_before_filtering + 1.f));

  // Scaling for new version. Threshold in final energy gain factor calculation.
  constexpr float kBLim = 0.5f;
  float scale_factor1 = 1.f;
  if (gain > kBLim) {
    scale_factor1 = 1.f + 1.3f * (gain - kBLim);
    if (gain * scale_factor1 > 1.f) {
      scale_factor1 = 1.f / gain;
    }
  }

  float scale_factor2 = 1.f;
  if (gain < kBLim) {
    // Do not reduce scale too much for pause regions: attenuation here should
    // be controlled by flooring.
    gain = std::max(gain, suppression_params_.minimum_attenuating_gain);
    scale_factor2 = 1.f - 0.3f * (kBLim - gain);
  }

  // Combine both scales with speech/noise prob: note prior
  // (prior_speech_probability) is not frequency dependent.
  return prior_speech_probability * scale_factor1 +
         (1.f - prior_speech_probability) * scale_factor2;
}

}  // namespace webrtc
