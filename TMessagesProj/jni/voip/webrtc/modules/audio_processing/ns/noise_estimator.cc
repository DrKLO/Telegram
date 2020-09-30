/*
 *  Copyright (c) 2019 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "modules/audio_processing/ns/noise_estimator.h"

#include <algorithm>

#include "modules/audio_processing/ns/fast_math.h"
#include "rtc_base/checks.h"

namespace webrtc {

namespace {

// Log(i).
constexpr std::array<float, 129> log_table = {
    0.f,       0.f,       0.f,       0.f,       0.f,       1.609438f, 1.791759f,
    1.945910f, 2.079442f, 2.197225f, 2.302585f, 2.397895f, 2.484907f, 2.564949f,
    2.639057f, 2.708050f, 2.772589f, 2.833213f, 2.890372f, 2.944439f, 2.995732f,
    3.044522f, 3.091043f, 3.135494f, 3.178054f, 3.218876f, 3.258097f, 3.295837f,
    3.332205f, 3.367296f, 3.401197f, 3.433987f, 3.465736f, 3.496507f, 3.526361f,
    3.555348f, 3.583519f, 3.610918f, 3.637586f, 3.663562f, 3.688879f, 3.713572f,
    3.737669f, 3.761200f, 3.784190f, 3.806663f, 3.828641f, 3.850147f, 3.871201f,
    3.891820f, 3.912023f, 3.931826f, 3.951244f, 3.970292f, 3.988984f, 4.007333f,
    4.025352f, 4.043051f, 4.060443f, 4.077538f, 4.094345f, 4.110874f, 4.127134f,
    4.143135f, 4.158883f, 4.174387f, 4.189655f, 4.204693f, 4.219508f, 4.234107f,
    4.248495f, 4.262680f, 4.276666f, 4.290460f, 4.304065f, 4.317488f, 4.330733f,
    4.343805f, 4.356709f, 4.369448f, 4.382027f, 4.394449f, 4.406719f, 4.418841f,
    4.430817f, 4.442651f, 4.454347f, 4.465908f, 4.477337f, 4.488636f, 4.499810f,
    4.510859f, 4.521789f, 4.532599f, 4.543295f, 4.553877f, 4.564348f, 4.574711f,
    4.584968f, 4.595119f, 4.605170f, 4.615121f, 4.624973f, 4.634729f, 4.644391f,
    4.653960f, 4.663439f, 4.672829f, 4.682131f, 4.691348f, 4.700480f, 4.709530f,
    4.718499f, 4.727388f, 4.736198f, 4.744932f, 4.753591f, 4.762174f, 4.770685f,
    4.779124f, 4.787492f, 4.795791f, 4.804021f, 4.812184f, 4.820282f, 4.828314f,
    4.836282f, 4.844187f, 4.852030f};

}  // namespace

NoiseEstimator::NoiseEstimator(const SuppressionParams& suppression_params)
    : suppression_params_(suppression_params) {
  noise_spectrum_.fill(0.f);
  prev_noise_spectrum_.fill(0.f);
  conservative_noise_spectrum_.fill(0.f);
  parametric_noise_spectrum_.fill(0.f);
}

void NoiseEstimator::PrepareAnalysis() {
  std::copy(noise_spectrum_.begin(), noise_spectrum_.end(),
            prev_noise_spectrum_.begin());
}

void NoiseEstimator::PreUpdate(
    int32_t num_analyzed_frames,
    rtc::ArrayView<const float, kFftSizeBy2Plus1> signal_spectrum,
    float signal_spectral_sum) {
  quantile_noise_estimator_.Estimate(signal_spectrum, noise_spectrum_);

  if (num_analyzed_frames < kShortStartupPhaseBlocks) {
    // Compute simplified noise model during startup.
    const size_t kStartBand = 5;
    float sum_log_i_log_magn = 0.f;
    float sum_log_i = 0.f;
    float sum_log_i_square = 0.f;
    float sum_log_magn = 0.f;
    for (size_t i = kStartBand; i < kFftSizeBy2Plus1; ++i) {
      float log_i = log_table[i];
      sum_log_i += log_i;
      sum_log_i_square += log_i * log_i;
      float log_signal = LogApproximation(signal_spectrum[i]);
      sum_log_magn += log_signal;
      sum_log_i_log_magn += log_i * log_signal;
    }

    // Estimate the parameter for the level of the white noise.
    constexpr float kOneByFftSizeBy2Plus1 = 1.f / kFftSizeBy2Plus1;
    white_noise_level_ += signal_spectral_sum * kOneByFftSizeBy2Plus1 *
                          suppression_params_.over_subtraction_factor;

    // Estimate pink noise parameters.
    float denom = sum_log_i_square * (kFftSizeBy2Plus1 - kStartBand) -
                  sum_log_i * sum_log_i;
    float num =
        sum_log_i_square * sum_log_magn - sum_log_i * sum_log_i_log_magn;
    RTC_DCHECK_NE(denom, 0.f);
    float pink_noise_adjustment = num / denom;

    // Constrain the estimated spectrum to be positive.
    pink_noise_adjustment = std::max(pink_noise_adjustment, 0.f);
    pink_noise_numerator_ += pink_noise_adjustment;
    num = sum_log_i * sum_log_magn -
          (kFftSizeBy2Plus1 - kStartBand) * sum_log_i_log_magn;
    RTC_DCHECK_NE(denom, 0.f);
    pink_noise_adjustment = num / denom;

    // Constrain the pink noise power to be in the interval [0, 1].
    pink_noise_adjustment = std::max(std::min(pink_noise_adjustment, 1.f), 0.f);

    pink_noise_exp_ += pink_noise_adjustment;

    const float one_by_num_analyzed_frames_plus_1 =
        1.f / (num_analyzed_frames + 1.f);

    // Calculate the frequency-independent parts of parametric noise estimate.
    float parametric_exp = 0.f;
    float parametric_num = 0.f;
    if (pink_noise_exp_ > 0.f) {
      // Use pink noise estimate.
      parametric_num = ExpApproximation(pink_noise_numerator_ *
                                        one_by_num_analyzed_frames_plus_1);
      parametric_num *= num_analyzed_frames + 1.f;
      parametric_exp = pink_noise_exp_ * one_by_num_analyzed_frames_plus_1;
    }

    constexpr float kOneByShortStartupPhaseBlocks =
        1.f / kShortStartupPhaseBlocks;
    for (size_t i = 0; i < kFftSizeBy2Plus1; ++i) {
      // Estimate the background noise using the white and pink noise
      // parameters.
      if (pink_noise_exp_ == 0.f) {
        // Use white noise estimate.
        parametric_noise_spectrum_[i] = white_noise_level_;
      } else {
        // Use pink noise estimate.
        float use_band = i < kStartBand ? kStartBand : i;
        float denom = PowApproximation(use_band, parametric_exp);
        RTC_DCHECK_NE(denom, 0.f);
        parametric_noise_spectrum_[i] = parametric_num / denom;
      }
    }

    // Weight quantile noise with modeled noise.
    for (size_t i = 0; i < kFftSizeBy2Plus1; ++i) {
      noise_spectrum_[i] *= num_analyzed_frames;
      float tmp = parametric_noise_spectrum_[i] *
                  (kShortStartupPhaseBlocks - num_analyzed_frames);
      noise_spectrum_[i] += tmp * one_by_num_analyzed_frames_plus_1;
      noise_spectrum_[i] *= kOneByShortStartupPhaseBlocks;
    }
  }
}

void NoiseEstimator::PostUpdate(
    rtc::ArrayView<const float> speech_probability,
    rtc::ArrayView<const float, kFftSizeBy2Plus1> signal_spectrum) {
  // Time-avg parameter for noise_spectrum update.
  constexpr float kNoiseUpdate = 0.9f;

  float gamma = kNoiseUpdate;
  for (size_t i = 0; i < kFftSizeBy2Plus1; ++i) {
    const float prob_speech = speech_probability[i];
    const float prob_non_speech = 1.f - prob_speech;

    // Temporary noise update used for speech frames if update value is less
    // than previous.
    float noise_update_tmp =
        gamma * prev_noise_spectrum_[i] +
        (1.f - gamma) * (prob_non_speech * signal_spectrum[i] +
                         prob_speech * prev_noise_spectrum_[i]);

    // Time-constant based on speech/noise_spectrum state.
    float gamma_old = gamma;

    // Increase gamma for frame likely to be seech.
    constexpr float kProbRange = .2f;
    gamma = prob_speech > kProbRange ? .99f : kNoiseUpdate;

    // Conservative noise_spectrum update.
    if (prob_speech < kProbRange) {
      conservative_noise_spectrum_[i] +=
          0.05f * (signal_spectrum[i] - conservative_noise_spectrum_[i]);
    }

    // Noise_spectrum update.
    if (gamma == gamma_old) {
      noise_spectrum_[i] = noise_update_tmp;
    } else {
      noise_spectrum_[i] =
          gamma * prev_noise_spectrum_[i] +
          (1.f - gamma) * (prob_non_speech * signal_spectrum[i] +
                           prob_speech * prev_noise_spectrum_[i]);
      // Allow for noise_spectrum update downwards: If noise_spectrum update
      // decreases the noise_spectrum, it is safe, so allow it to happen.
      noise_spectrum_[i] = std::min(noise_spectrum_[i], noise_update_tmp);
    }
  }
}

}  // namespace webrtc
