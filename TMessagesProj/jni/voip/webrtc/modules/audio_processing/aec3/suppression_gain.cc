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

#include "modules/audio_processing/aec3/dominant_nearend_detector.h"
#include "modules/audio_processing/aec3/moving_average.h"
#include "modules/audio_processing/aec3/subband_nearend_detector.h"
#include "modules/audio_processing/aec3/vector_math.h"
#include "modules/audio_processing/logging/apm_data_dumper.h"
#include "rtc_base/atomic_ops.h"
#include "rtc_base/checks.h"

namespace webrtc {
namespace {

void LimitLowFrequencyGains(std::array<float, kFftLengthBy2Plus1>* gain) {
  // Limit the low frequency gains to avoid the impact of the high-pass filter
  // on the lower-frequency gain influencing the overall achieved gain.
  (*gain)[0] = (*gain)[1] = std::min((*gain)[1], (*gain)[2]);
}

void LimitHighFrequencyGains(bool conservative_hf_suppression,
                             std::array<float, kFftLengthBy2Plus1>* gain) {
  // Limit the high frequency gains to avoid echo leakage due to an imperfect
  // filter.
  constexpr size_t kFirstBandToLimit = (64 * 2000) / 8000;
  const float min_upper_gain = (*gain)[kFirstBandToLimit];
  std::for_each(
      gain->begin() + kFirstBandToLimit + 1, gain->end(),
      [min_upper_gain](float& a) { a = std::min(a, min_upper_gain); });
  (*gain)[kFftLengthBy2] = (*gain)[kFftLengthBy2Minus1];

  if (conservative_hf_suppression) {
    // Limits the gain in the frequencies for which the adaptive filter has not
    // converged.
    // TODO(peah): Make adaptive to take the actual filter error into account.
    constexpr size_t kUpperAccurateBandPlus1 = 29;

    constexpr float oneByBandsInSum =
        1 / static_cast<float>(kUpperAccurateBandPlus1 - 20);
    const float hf_gain_bound =
        std::accumulate(gain->begin() + 20,
                        gain->begin() + kUpperAccurateBandPlus1, 0.f) *
        oneByBandsInSum;

    std::for_each(
        gain->begin() + kUpperAccurateBandPlus1, gain->end(),
        [hf_gain_bound](float& a) { a = std::min(a, hf_gain_bound); });
  }
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

}  // namespace

int SuppressionGain::instance_count_ = 0;

float SuppressionGain::UpperBandsGain(
    rtc::ArrayView<const std::array<float, kFftLengthBy2Plus1>> echo_spectrum,
    rtc::ArrayView<const std::array<float, kFftLengthBy2Plus1>>
        comfort_noise_spectrum,
    const absl::optional<int>& narrow_peak_band,
    bool saturated_echo,
    const std::vector<std::vector<std::vector<float>>>& render,
    const std::array<float, kFftLengthBy2Plus1>& low_band_gain) const {
  RTC_DCHECK_LT(0, render.size());
  if (render.size() == 1) {
    return 1.f;
  }
  const size_t num_render_channels = render[0].size();

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
  float low_band_energy = 0.f;
  for (size_t ch = 0; ch < num_render_channels; ++ch) {
    const float channel_energy = std::accumulate(
        render[0][0].begin(), render[0][0].end(), 0.f, sum_of_squares);
    low_band_energy = std::max(low_band_energy, channel_energy);
  }
  float high_band_energy = 0.f;
  for (size_t k = 1; k < render.size(); ++k) {
    for (size_t ch = 0; ch < num_render_channels; ++ch) {
      const float energy = std::accumulate(
          render[k][ch].begin(), render[k][ch].end(), 0.f, sum_of_squares);
      high_band_energy = std::max(high_band_energy, energy);
    }
  }

  // If there is more power in the lower frequencies than the upper frequencies,
  // or if the power in upper frequencies is low, do not bound the gain in the
  // upper bands.
  float anti_howling_gain;
  const float activation_threshold =
      kBlockSize * config_.suppressor.high_bands_suppression
                       .anti_howling_activation_threshold;
  if (high_band_energy < std::max(low_band_energy, activation_threshold)) {
    anti_howling_gain = 1.f;
  } else {
    // In all other cases, bound the gain for upper frequencies.
    RTC_DCHECK_LE(low_band_energy, high_band_energy);
    RTC_DCHECK_NE(0.f, high_band_energy);
    anti_howling_gain =
        config_.suppressor.high_bands_suppression.anti_howling_gain *
        sqrtf(low_band_energy / high_band_energy);
  }

  float gain_bound = 1.f;
  if (!dominant_nearend_detector_->IsNearendState()) {
    // Bound the upper gain during significant echo activity.
    const auto& cfg = config_.suppressor.high_bands_suppression;
    auto low_frequency_energy = [](rtc::ArrayView<const float> spectrum) {
      RTC_DCHECK_LE(16, spectrum.size());
      return std::accumulate(spectrum.begin() + 1, spectrum.begin() + 16, 0.f);
    };
    for (size_t ch = 0; ch < num_capture_channels_; ++ch) {
      const float echo_sum = low_frequency_energy(echo_spectrum[ch]);
      const float noise_sum = low_frequency_energy(comfort_noise_spectrum[ch]);
      if (echo_sum > cfg.enr_threshold * noise_sum) {
        gain_bound = cfg.max_gain_during_echo;
        break;
      }
    }
  }

  // Choose the gain as the minimum of the lower and upper gains.
  return std::min(std::min(gain_below_8_khz, anti_howling_gain), gain_bound);
}

// Computes the gain to reduce the echo to a non audible level.
void SuppressionGain::GainToNoAudibleEcho(
    const std::array<float, kFftLengthBy2Plus1>& nearend,
    const std::array<float, kFftLengthBy2Plus1>& echo,
    const std::array<float, kFftLengthBy2Plus1>& masker,
    std::array<float, kFftLengthBy2Plus1>* gain) const {
  const auto& p = dominant_nearend_detector_->IsNearendState() ? nearend_params_
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
    (*gain)[k] = g;
  }
}

// Compute the minimum gain as the attenuating gain to put the signal just
// above the zero sample values.
void SuppressionGain::GetMinGain(
    rtc::ArrayView<const float> weighted_residual_echo,
    rtc::ArrayView<const float> last_nearend,
    rtc::ArrayView<const float> last_echo,
    bool low_noise_render,
    bool saturated_echo,
    rtc::ArrayView<float> min_gain) const {
  if (!saturated_echo) {
    const float min_echo_power =
        low_noise_render ? config_.echo_audibility.low_render_limit
                         : config_.echo_audibility.normal_render_limit;

    for (size_t k = 0; k < min_gain.size(); ++k) {
      min_gain[k] = weighted_residual_echo[k] > 0.f
                        ? min_echo_power / weighted_residual_echo[k]
                        : 1.f;
      min_gain[k] = std::min(min_gain[k], 1.f);
    }

    const bool is_nearend_state = dominant_nearend_detector_->IsNearendState();
    for (size_t k = 0; k < 6; ++k) {
      const auto& dec = is_nearend_state ? nearend_params_.max_dec_factor_lf
                                         : normal_params_.max_dec_factor_lf;

      // Make sure the gains of the low frequencies do not decrease too
      // quickly after strong nearend.
      if (last_nearend[k] > last_echo[k]) {
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
  const auto& inc = dominant_nearend_detector_->IsNearendState()
                        ? nearend_params_.max_inc_factor
                        : normal_params_.max_inc_factor;
  const auto& floor = config_.suppressor.floor_first_increase;
  for (size_t k = 0; k < max_gain.size(); ++k) {
    max_gain[k] = std::min(std::max(last_gain_[k] * inc, floor), 1.f);
  }
}

void SuppressionGain::LowerBandGain(
    bool low_noise_render,
    const AecState& aec_state,
    rtc::ArrayView<const std::array<float, kFftLengthBy2Plus1>>
        suppressor_input,
    rtc::ArrayView<const std::array<float, kFftLengthBy2Plus1>> residual_echo,
    rtc::ArrayView<const std::array<float, kFftLengthBy2Plus1>> comfort_noise,
    bool clock_drift,
    std::array<float, kFftLengthBy2Plus1>* gain) {
  gain->fill(1.f);
  const bool saturated_echo = aec_state.SaturatedEcho();
  std::array<float, kFftLengthBy2Plus1> max_gain;
  GetMaxGain(max_gain);

  for (size_t ch = 0; ch < num_capture_channels_; ++ch) {
    std::array<float, kFftLengthBy2Plus1> G;
    std::array<float, kFftLengthBy2Plus1> nearend;
    nearend_smoothers_[ch].Average(suppressor_input[ch], nearend);

    // Weight echo power in terms of audibility.
    std::array<float, kFftLengthBy2Plus1> weighted_residual_echo;
    WeightEchoForAudibility(config_, residual_echo[ch], weighted_residual_echo);

    std::array<float, kFftLengthBy2Plus1> min_gain;
    GetMinGain(weighted_residual_echo, last_nearend_[ch], last_echo_[ch],
               low_noise_render, saturated_echo, min_gain);

    GainToNoAudibleEcho(nearend, weighted_residual_echo, comfort_noise[0], &G);

    // Clamp gains.
    for (size_t k = 0; k < gain->size(); ++k) {
      G[k] = std::max(std::min(G[k], max_gain[k]), min_gain[k]);
      (*gain)[k] = std::min((*gain)[k], G[k]);
    }

    // Store data required for the gain computation of the next block.
    std::copy(nearend.begin(), nearend.end(), last_nearend_[ch].begin());
    std::copy(weighted_residual_echo.begin(), weighted_residual_echo.end(),
              last_echo_[ch].begin());
  }

  LimitLowFrequencyGains(gain);
  // Use conservative high-frequency gains during clock-drift or when not in
  // dominant nearend.
  if (!dominant_nearend_detector_->IsNearendState() || clock_drift ||
      config_.suppressor.conservative_hf_suppression) {
    LimitHighFrequencyGains(config_.suppressor.conservative_hf_suppression,
                            gain);
  }

  // Store computed gains.
  std::copy(gain->begin(), gain->end(), last_gain_.begin());

  // Transform gains to amplitude domain.
  aec3::VectorMath(optimization_).Sqrt(*gain);
}

SuppressionGain::SuppressionGain(const EchoCanceller3Config& config,
                                 Aec3Optimization optimization,
                                 int sample_rate_hz,
                                 size_t num_capture_channels)
    : data_dumper_(
          new ApmDataDumper(rtc::AtomicOps::Increment(&instance_count_))),
      optimization_(optimization),
      config_(config),
      num_capture_channels_(num_capture_channels),
      state_change_duration_blocks_(
          static_cast<int>(config_.filter.config_change_duration_blocks)),
      last_nearend_(num_capture_channels_, {0}),
      last_echo_(num_capture_channels_, {0}),
      nearend_smoothers_(
          num_capture_channels_,
          aec3::MovingAverage(kFftLengthBy2Plus1,
                              config.suppressor.nearend_average_blocks)),
      nearend_params_(config_.suppressor.nearend_tuning),
      normal_params_(config_.suppressor.normal_tuning) {
  RTC_DCHECK_LT(0, state_change_duration_blocks_);
  last_gain_.fill(1.f);
  if (config_.suppressor.use_subband_nearend_detection) {
    dominant_nearend_detector_ = std::make_unique<SubbandNearendDetector>(
        config_.suppressor.subband_nearend_detection, num_capture_channels_);
  } else {
    dominant_nearend_detector_ = std::make_unique<DominantNearendDetector>(
        config_.suppressor.dominant_nearend_detection, num_capture_channels_);
  }
  RTC_DCHECK(dominant_nearend_detector_);
}

SuppressionGain::~SuppressionGain() = default;

void SuppressionGain::GetGain(
    rtc::ArrayView<const std::array<float, kFftLengthBy2Plus1>>
        nearend_spectrum,
    rtc::ArrayView<const std::array<float, kFftLengthBy2Plus1>> echo_spectrum,
    rtc::ArrayView<const std::array<float, kFftLengthBy2Plus1>>
        residual_echo_spectrum,
    rtc::ArrayView<const std::array<float, kFftLengthBy2Plus1>>
        comfort_noise_spectrum,
    const RenderSignalAnalyzer& render_signal_analyzer,
    const AecState& aec_state,
    const std::vector<std::vector<std::vector<float>>>& render,
    bool clock_drift,
    float* high_bands_gain,
    std::array<float, kFftLengthBy2Plus1>* low_band_gain) {
  RTC_DCHECK(high_bands_gain);
  RTC_DCHECK(low_band_gain);

  // Update the nearend state selection.
  dominant_nearend_detector_->Update(nearend_spectrum, residual_echo_spectrum,
                                     comfort_noise_spectrum, initial_state_);

  // Compute gain for the lower band.
  bool low_noise_render = low_render_detector_.Detect(render);
  LowerBandGain(low_noise_render, aec_state, nearend_spectrum,
                residual_echo_spectrum, comfort_noise_spectrum, clock_drift,
                low_band_gain);

  // Compute the gain for the upper bands.
  const absl::optional<int> narrow_peak_band =
      render_signal_analyzer.NarrowPeakBand();

  *high_bands_gain =
      UpperBandsGain(echo_spectrum, comfort_noise_spectrum, narrow_peak_band,
                     aec_state.SaturatedEcho(), render, *low_band_gain);
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
    const std::vector<std::vector<std::vector<float>>>& render) {
  float x2_sum = 0.f;
  float x2_max = 0.f;
  for (const auto& x_ch : render[0]) {
    for (const auto& x_k : x_ch) {
      const float x2 = x_k * x_k;
      x2_sum += x2;
      x2_max = std::max(x2_max, x2);
    }
  }
  const size_t num_render_channels = render[0].size();
  x2_sum = x2_sum / num_render_channels;
  ;

  constexpr float kThreshold = 50.f * 50.f * 64.f;
  const bool low_noise_render =
      average_power_ < kThreshold && x2_max < 3 * average_power_;
  average_power_ = average_power_ * 0.9f + x2_sum * 0.1f;
  return low_noise_render;
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
