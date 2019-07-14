/*
 *  Copyright (c) 2017 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "modules/audio_processing/aec3/suppression_gain.h"

#include <math.h>
#include <stddef.h>
#include <algorithm>
#include <numeric>

#include "modules/audio_processing/aec3/moving_average.h"
#include "modules/audio_processing/aec3/vector_math.h"
#include "modules/audio_processing/logging/apm_data_dumper.h"
#include "rtc_base/atomicops.h"
#include "rtc_base/checks.h"

namespace webrtc {
namespace {

// Adjust the gains according to the presence of known external filters.
void AdjustForExternalFilters(std::array<float, kFftLengthBy2Plus1>* gain) {
  // Limit the low frequency gains to avoid the impact of the high-pass filter
  // on the lower-frequency gain influencing the overall achieved gain.
  (*gain)[0] = (*gain)[1] = std::min((*gain)[1], (*gain)[2]);

  // Limit the high frequency gains to avoid the impact of the anti-aliasing
  // filter on the upper-frequency gains influencing the overall achieved
  // gain. TODO(peah): Update this when new anti-aliasing filters are
  // implemented.
  constexpr size_t kAntiAliasingImpactLimit = (64 * 2000) / 8000;
  const float min_upper_gain = (*gain)[kAntiAliasingImpactLimit];
  std::for_each(
      gain->begin() + kAntiAliasingImpactLimit, gain->end() - 1,
      [min_upper_gain](float& a) { a = std::min(a, min_upper_gain); });
  (*gain)[kFftLengthBy2] = (*gain)[kFftLengthBy2Minus1];
}

// Scales the echo according to assessed audibility at the other end.
void WeightEchoForAudibility(const EchoCanceller3Config& config,
                             rtc::ArrayView<const float> echo,
                             rtc::ArrayView<float> weighted_echo) {
  RTC_DCHECK_EQ(kFftLengthBy2Plus1, echo.size());
  RTC_DCHECK_EQ(kFftLengthBy2Plus1, weighted_echo.size());

  auto weigh = [](float threshold, float normalizer, size_t begin, size_t end,
                  rtc::ArrayView<const float> echo,
                  rtc::ArrayView<float> weighted_echo) {
    for (size_t k = begin; k < end; ++k) {
      if (echo[k] < threshold) {
        float tmp = (threshold - echo[k]) * normalizer;
        weighted_echo[k] = echo[k] * std::max(0.f, 1.f - tmp * tmp);
      } else {
        weighted_echo[k] = echo[k];
      }
    }
  };

  float threshold = config.echo_audibility.floor_power *
                    config.echo_audibility.audibility_threshold_lf;
  float normalizer = 1.f / (threshold - config.echo_audibility.floor_power);
  weigh(threshold, normalizer, 0, 3, echo, weighted_echo);

  threshold = config.echo_audibility.floor_power *
              config.echo_audibility.audibility_threshold_mf;
  normalizer = 1.f / (threshold - config.echo_audibility.floor_power);
  weigh(threshold, normalizer, 3, 7, echo, weighted_echo);

  threshold = config.echo_audibility.floor_power *
              config.echo_audibility.audibility_threshold_hf;
  normalizer = 1.f / (threshold - config.echo_audibility.floor_power);
  weigh(threshold, normalizer, 7, kFftLengthBy2Plus1, echo, weighted_echo);
}

// TODO(peah): Make adaptive to take the actual filter error into account.
constexpr size_t kUpperAccurateBandPlus1 = 29;

// Limits the gain in the frequencies for which the adaptive filter has not
// converged. Currently, these frequencies are not hardcoded to the frequencies
// which are typically not excited by speech.
// TODO(peah): Make adaptive to take the actual filter error into account.
void AdjustNonConvergedFrequencies(
    std::array<float, kFftLengthBy2Plus1>* gain) {
  constexpr float oneByBandsInSum =
      1 / static_cast<float>(kUpperAccurateBandPlus1 - 20);
  const float hf_gain_bound =
      std::accumulate(gain->begin() + 20,
                      gain->begin() + kUpperAccurateBandPlus1, 0.f) *
      oneByBandsInSum;

  std::for_each(gain->begin() + kUpperAccurateBandPlus1, gain->end(),
                [hf_gain_bound](float& a) { a = std::min(a, hf_gain_bound); });
}

}  // namespace

int SuppressionGain::instance_count_ = 0;

float SuppressionGain::UpperBandsGain(
    const std::array<float, kFftLengthBy2Plus1>& echo_spectrum,
    const std::array<float, kFftLengthBy2Plus1>& comfort_noise_spectrum,
    const absl::optional<int>& narrow_peak_band,
    bool saturated_echo,
    const std::vector<std::vector<float>>& render,
    const std::array<float, kFftLengthBy2Plus1>& low_band_gain) const {
  RTC_DCHECK_LT(0, render.size());
  if (render.size() == 1) {
    return 1.f;
  }

  if (narrow_peak_band &&
      (*narrow_peak_band > static_cast<int>(kFftLengthBy2Plus1 - 10))) {
    return 0.001f;
  }

  constexpr size_t kLowBandGainLimit = kFftLengthBy2 / 2;
  const float gain_below_8_khz = *std::min_element(
      low_band_gain.begin() + kLowBandGainLimit, low_band_gain.end());

  // Always attenuate the upper bands when there is saturated echo.
  if (saturated_echo) {
    return std::min(0.001f, gain_below_8_khz);
  }

  // Compute the upper and lower band energies.
  const auto sum_of_squares = [](float a, float b) { return a + b * b; };
  const float low_band_energy =
      std::accumulate(render[0].begin(), render[0].end(), 0.f, sum_of_squares);
  float high_band_energy = 0.f;
  for (size_t k = 1; k < render.size(); ++k) {
    const float energy = std::accumulate(render[k].begin(), render[k].end(),
                                         0.f, sum_of_squares);
    high_band_energy = std::max(high_band_energy, energy);
  }

  // If there is more power in the lower frequencies than the upper frequencies,
  // or if the power in upper frequencies is low, do not bound the gain in the
  // upper bands.
  float anti_howling_gain;
  constexpr float kThreshold = kBlockSize * 10.f * 10.f / 4.f;
  if (high_band_energy < std::max(low_band_energy, kThreshold)) {
    anti_howling_gain = 1.f;
  } else {
    // In all other cases, bound the gain for upper frequencies.
    RTC_DCHECK_LE(low_band_energy, high_band_energy);
    RTC_DCHECK_NE(0.f, high_band_energy);
    anti_howling_gain = 0.01f * sqrtf(low_band_energy / high_band_energy);
  }

  // Bound the upper gain during significant echo activity.
  auto low_frequency_energy = [](rtc::ArrayView<const float> spectrum) {
    RTC_DCHECK_LE(16, spectrum.size());
    return std::accumulate(spectrum.begin() + 1, spectrum.begin() + 16, 0.f);
  };
  const float echo_sum = low_frequency_energy(echo_spectrum);
  const float noise_sum = low_frequency_energy(comfort_noise_spectrum);
  const auto& cfg = config_.suppressor.high_bands_suppression;
  float gain_bound = 1.f;
  if (echo_sum > cfg.enr_threshold * noise_sum &&
      !dominant_nearend_detector_.IsNearendState()) {
    gain_bound = cfg.max_gain_during_echo;
  }

  // Choose the gain as the minimum of the lower and upper gains.
  return std::min(std::min(gain_below_8_khz, anti_howling_gain), gain_bound);
}

// Computes the gain to reduce the echo to a non audible level.
void SuppressionGain::GainToNoAudibleEcho(
    const std::array<float, kFftLengthBy2Plus1>& nearend,
    const std::array<float, kFftLengthBy2Plus1>& echo,
    const std::array<float, kFftLengthBy2Plus1>& masker,
    const std::array<float, kFftLengthBy2Plus1>& min_gain,
    const std::array<float, kFftLengthBy2Plus1>& max_gain,
    std::array<float, kFftLengthBy2Plus1>* gain) const {
  const auto& p = dominant_nearend_detector_.IsNearendState() ? nearend_params_
                                                              : normal_params_;
  for (size_t k = 0; k < gain->size(); ++k) {
    float enr = echo[k] / (nearend[k] + 1.f);  // Echo-to-nearend ratio.
    float emr = echo[k] / (masker[k] + 1.f);   // Echo-to-masker (noise) ratio.
    float g = 1.0f;
    if (enr > p.enr_transparent_[k] && emr > p.emr_transparent_[k]) {
      g = (p.enr_suppress_[k] - enr) /
          (p.enr_suppress_[k] - p.enr_transparent_[k]);
      g = std::max(g, p.emr_transparent_[k] / emr);
    }
    (*gain)[k] = std::max(std::min(g, max_gain[k]), min_gain[k]);
  }
}

// Compute the minimum gain as the attenuating gain to put the signal just
// above the zero sample values.
void SuppressionGain::GetMinGain(
    rtc::ArrayView<const float> suppressor_input,
    rtc::ArrayView<const float> weighted_residual_echo,
    bool low_noise_render,
    bool saturated_echo,
    rtc::ArrayView<float> min_gain) const {
  if (!saturated_echo) {
    const float min_echo_power =
        low_noise_render ? config_.echo_audibility.low_render_limit
                         : config_.echo_audibility.normal_render_limit;

    for (size_t k = 0; k < suppressor_input.size(); ++k) {
      const float denom =
          std::min(suppressor_input[k], weighted_residual_echo[k]);
      min_gain[k] = denom > 0.f ? min_echo_power / denom : 1.f;
      min_gain[k] = std::min(min_gain[k], 1.f);
    }
    for (size_t k = 0; k < 6; ++k) {
      const auto& dec = dominant_nearend_detector_.IsNearendState()
                            ? nearend_params_.max_dec_factor_lf
                            : normal_params_.max_dec_factor_lf;

      // Make sure the gains of the low frequencies do not decrease too
      // quickly after strong nearend.
      if (last_nearend_[k] > last_echo_[k]) {
        min_gain[k] = std::max(min_gain[k], last_gain_[k] * dec);
        min_gain[k] = std::min(min_gain[k], 1.f);
      }
    }
  } else {
    std::fill(min_gain.begin(), min_gain.end(), 0.f);
  }
}

// Compute the maximum gain by limiting the gain increase from the previous
// gain.
void SuppressionGain::GetMaxGain(rtc::ArrayView<float> max_gain) const {
  const auto& inc = dominant_nearend_detector_.IsNearendState()
                        ? nearend_params_.max_inc_factor
                        : normal_params_.max_inc_factor;
  const auto& floor = config_.suppressor.floor_first_increase;
  for (size_t k = 0; k < max_gain.size(); ++k) {
    max_gain[k] = std::min(std::max(last_gain_[k] * inc, floor), 1.f);
  }
}

// TODO(peah): Add further optimizations, in particular for the divisions.
void SuppressionGain::LowerBandGain(
    bool low_noise_render,
    const AecState& aec_state,
    const std::array<float, kFftLengthBy2Plus1>& suppressor_input,
    const std::array<float, kFftLengthBy2Plus1>& nearend,
    const std::array<float, kFftLengthBy2Plus1>& residual_echo,
    const std::array<float, kFftLengthBy2Plus1>& comfort_noise,
    std::array<float, kFftLengthBy2Plus1>* gain) {
  const bool saturated_echo = aec_state.SaturatedEcho();

  // Weight echo power in terms of audibility. // Precompute 1/weighted echo
  // (note that when the echo is zero, the precomputed value is never used).
  std::array<float, kFftLengthBy2Plus1> weighted_residual_echo;
  WeightEchoForAudibility(config_, residual_echo, weighted_residual_echo);

  std::array<float, kFftLengthBy2Plus1> min_gain;
  GetMinGain(suppressor_input, weighted_residual_echo, low_noise_render,
             saturated_echo, min_gain);

  std::array<float, kFftLengthBy2Plus1> max_gain;
  GetMaxGain(max_gain);

    GainToNoAudibleEcho(nearend, weighted_residual_echo, comfort_noise,
                        min_gain, max_gain, gain);
    AdjustForExternalFilters(gain);

  // Adjust the gain for frequencies which have not yet converged.
  AdjustNonConvergedFrequencies(gain);

  // Store data required for the gain computation of the next block.
  std::copy(nearend.begin(), nearend.end(), last_nearend_.begin());
  std::copy(weighted_residual_echo.begin(), weighted_residual_echo.end(),
            last_echo_.begin());
  std::copy(gain->begin(), gain->end(), last_gain_.begin());
  aec3::VectorMath(optimization_).Sqrt(*gain);

  // Debug outputs for the purpose of development and analysis.
  data_dumper_->DumpRaw("aec3_suppressor_min_gain", min_gain);
  data_dumper_->DumpRaw("aec3_suppressor_max_gain", max_gain);
  data_dumper_->DumpRaw("aec3_dominant_nearend",
                        dominant_nearend_detector_.IsNearendState());
}

SuppressionGain::SuppressionGain(const EchoCanceller3Config& config,
                                 Aec3Optimization optimization,
                                 int sample_rate_hz)
    : data_dumper_(
          new ApmDataDumper(rtc::AtomicOps::Increment(&instance_count_))),
      optimization_(optimization),
      config_(config),
      state_change_duration_blocks_(
          static_cast<int>(config_.filter.config_change_duration_blocks)),
      moving_average_(kFftLengthBy2Plus1,
                      config.suppressor.nearend_average_blocks),
      nearend_params_(config_.suppressor.nearend_tuning),
      normal_params_(config_.suppressor.normal_tuning),
      dominant_nearend_detector_(
          config_.suppressor.dominant_nearend_detection) {
  RTC_DCHECK_LT(0, state_change_duration_blocks_);
  one_by_state_change_duration_blocks_ = 1.f / state_change_duration_blocks_;
  last_gain_.fill(1.f);
  last_nearend_.fill(0.f);
  last_echo_.fill(0.f);
}

SuppressionGain::~SuppressionGain() = default;

void SuppressionGain::GetGain(
    const std::array<float, kFftLengthBy2Plus1>& suppressor_input_spectrum,
    const std::array<float, kFftLengthBy2Plus1>& nearend_spectrum,
    const std::array<float, kFftLengthBy2Plus1>& echo_spectrum,
    const std::array<float, kFftLengthBy2Plus1>& residual_echo_spectrum,
    const std::array<float, kFftLengthBy2Plus1>& comfort_noise_spectrum,
    const FftData& linear_aec_fft,
    const FftData& capture_fft,
    const RenderSignalAnalyzer& render_signal_analyzer,
    const AecState& aec_state,
    const std::vector<std::vector<float>>& render,
    float* high_bands_gain,
    std::array<float, kFftLengthBy2Plus1>* low_band_gain) {
  RTC_DCHECK(high_bands_gain);
  RTC_DCHECK(low_band_gain);
  const auto& cfg = config_.suppressor;

  if (cfg.enforce_transparent) {
    low_band_gain->fill(1.f);
    *high_bands_gain = cfg.enforce_empty_higher_bands ? 0.f : 1.f;
    return;
  }

  std::array<float, kFftLengthBy2Plus1> nearend_average;
  moving_average_.Average(nearend_spectrum, nearend_average);

  // Update the state selection.
  dominant_nearend_detector_.Update(nearend_spectrum, residual_echo_spectrum,
                                    comfort_noise_spectrum, initial_state_);

  // Compute gain for the lower band.
  bool low_noise_render = low_render_detector_.Detect(render);
  LowerBandGain(low_noise_render, aec_state, suppressor_input_spectrum,
                nearend_average, residual_echo_spectrum, comfort_noise_spectrum,
                low_band_gain);

  // Limit the gain of the lower bands during start up and after resets.
  const float gain_upper_bound = aec_state.SuppressionGainLimit();
  if (gain_upper_bound < 1.f) {
    for (size_t k = 0; k < low_band_gain->size(); ++k) {
      (*low_band_gain)[k] = std::min((*low_band_gain)[k], gain_upper_bound);
    }
  }

  // Compute the gain for the upper bands.
  const absl::optional<int> narrow_peak_band =
      render_signal_analyzer.NarrowPeakBand();

  *high_bands_gain =
      UpperBandsGain(echo_spectrum, comfort_noise_spectrum, narrow_peak_band,
                     aec_state.SaturatedEcho(), render, *low_band_gain);
  if (cfg.enforce_empty_higher_bands) {
    *high_bands_gain = 0.f;
  }
}

void SuppressionGain::SetInitialState(bool state) {
  initial_state_ = state;
  if (state) {
    initial_state_change_counter_ = state_change_duration_blocks_;
  } else {
    initial_state_change_counter_ = 0;
  }
}

// Detects when the render signal can be considered to have low power and
// consist of stationary noise.
bool SuppressionGain::LowNoiseRenderDetector::Detect(
    const std::vector<std::vector<float>>& render) {
  float x2_sum = 0.f;
  float x2_max = 0.f;
  for (auto x_k : render[0]) {
    const float x2 = x_k * x_k;
    x2_sum += x2;
    x2_max = std::max(x2_max, x2);
  }

  constexpr float kThreshold = 50.f * 50.f * 64.f;
  const bool low_noise_render =
      average_power_ < kThreshold && x2_max < 3 * average_power_;
  average_power_ = average_power_ * 0.9f + x2_sum * 0.1f;
  return low_noise_render;
}

SuppressionGain::DominantNearendDetector::DominantNearendDetector(
    const EchoCanceller3Config::Suppressor::DominantNearendDetection config)
    : enr_threshold_(config.enr_threshold),
      enr_exit_threshold_(config.enr_exit_threshold),
      snr_threshold_(config.snr_threshold),
      hold_duration_(config.hold_duration),
      trigger_threshold_(config.trigger_threshold),
      use_during_initial_phase_(config.use_during_initial_phase) {}

void SuppressionGain::DominantNearendDetector::Update(
    rtc::ArrayView<const float> nearend_spectrum,
    rtc::ArrayView<const float> residual_echo_spectrum,
    rtc::ArrayView<const float> comfort_noise_spectrum,
    bool initial_state) {
  auto low_frequency_energy = [](rtc::ArrayView<const float> spectrum) {
    RTC_DCHECK_LE(16, spectrum.size());
    return std::accumulate(spectrum.begin() + 1, spectrum.begin() + 16, 0.f);
  };
  const float ne_sum = low_frequency_energy(nearend_spectrum);
  const float echo_sum = low_frequency_energy(residual_echo_spectrum);
  const float noise_sum = low_frequency_energy(comfort_noise_spectrum);

  // Detect strong active nearend if the nearend is sufficiently stronger than
  // the echo and the nearend noise.
  if ((!initial_state || use_during_initial_phase_) &&
      ne_sum > enr_threshold_ * echo_sum &&
      ne_sum > snr_threshold_ * noise_sum) {
    if (++trigger_counter_ >= trigger_threshold_) {
      // After a period of strong active nearend activity, flag nearend mode.
      hold_counter_ = hold_duration_;
      trigger_counter_ = trigger_threshold_;
    }
  } else {
    // Forget previously detected strong active nearend activity.
    trigger_counter_ = std::max(0, trigger_counter_ - 1);
  }

  // Exit nearend-state early at strong echo.
  if (ne_sum < enr_exit_threshold_ * echo_sum &&
      echo_sum > snr_threshold_ * noise_sum) {
    hold_counter_ = 0;
  }

  // Remain in any nearend mode for a certain duration.
  hold_counter_ = std::max(0, hold_counter_ - 1);
  nearend_state_ = hold_counter_ > 0;
}

SuppressionGain::GainParameters::GainParameters(
    const EchoCanceller3Config::Suppressor::Tuning& tuning)
    : max_inc_factor(tuning.max_inc_factor),
      max_dec_factor_lf(tuning.max_dec_factor_lf) {
  // Compute per-band masking thresholds.
  constexpr size_t kLastLfBand = 5;
  constexpr size_t kFirstHfBand = 8;
  RTC_DCHECK_LT(kLastLfBand, kFirstHfBand);
  auto& lf = tuning.mask_lf;
  auto& hf = tuning.mask_hf;
  RTC_DCHECK_LT(lf.enr_transparent, lf.enr_suppress);
  RTC_DCHECK_LT(hf.enr_transparent, hf.enr_suppress);
  for (size_t k = 0; k < kFftLengthBy2Plus1; k++) {
    float a;
    if (k <= kLastLfBand) {
      a = 0.f;
    } else if (k < kFirstHfBand) {
      a = (k - kLastLfBand) / static_cast<float>(kFirstHfBand - kLastLfBand);
    } else {
      a = 1.f;
    }
    enr_transparent_[k] = (1 - a) * lf.enr_transparent + a * hf.enr_transparent;
    enr_suppress_[k] = (1 - a) * lf.enr_suppress + a * hf.enr_suppress;
    emr_transparent_[k] = (1 - a) * lf.emr_transparent + a * hf.emr_transparent;
  }
}

}  // namespace webrtc
