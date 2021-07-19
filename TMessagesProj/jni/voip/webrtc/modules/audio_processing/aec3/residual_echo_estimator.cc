/*
 *  Copyright (c) 2017 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "modules/audio_processing/aec3/residual_echo_estimator.h"

#include <stddef.h>

#include <algorithm>
#include <vector>

#include "api/array_view.h"
#include "modules/audio_processing/aec3/reverb_model.h"
#include "rtc_base/checks.h"
#include "system_wrappers/include/field_trial.h"

namespace webrtc {
namespace {

constexpr float kDefaultTransparentModeGain = 0.01f;

float GetTransparentModeGain() {
  return kDefaultTransparentModeGain;
}

float GetEarlyReflectionsDefaultModeGain(
    const EchoCanceller3Config::EpStrength& config) {
  if (field_trial::IsEnabled("WebRTC-Aec3UseLowEarlyReflectionsDefaultGain")) {
    return 0.1f;
  }
  return config.default_gain;
}

float GetLateReflectionsDefaultModeGain(
    const EchoCanceller3Config::EpStrength& config) {
  if (field_trial::IsEnabled("WebRTC-Aec3UseLowLateReflectionsDefaultGain")) {
    return 0.1f;
  }
  return config.default_gain;
}

bool UseErleOnsetCompensationInDominantNearend(
    const EchoCanceller3Config::EpStrength& config) {
  return config.erle_onset_compensation_in_dominant_nearend ||
         field_trial::IsEnabled(
             "WebRTC-Aec3UseErleOnsetCompensationInDominantNearend");
}

// Computes the indexes that will be used for computing spectral power over
// the blocks surrounding the delay.
void GetRenderIndexesToAnalyze(
    const SpectrumBuffer& spectrum_buffer,
    const EchoCanceller3Config::EchoModel& echo_model,
    int filter_delay_blocks,
    int* idx_start,
    int* idx_stop) {
  RTC_DCHECK(idx_start);
  RTC_DCHECK(idx_stop);
  size_t window_start;
  size_t window_end;
  window_start =
      std::max(0, filter_delay_blocks -
                      static_cast<int>(echo_model.render_pre_window_size));
  window_end = filter_delay_blocks +
               static_cast<int>(echo_model.render_post_window_size);
  *idx_start = spectrum_buffer.OffsetIndex(spectrum_buffer.read, window_start);
  *idx_stop = spectrum_buffer.OffsetIndex(spectrum_buffer.read, window_end + 1);
}

// Estimates the residual echo power based on the echo return loss enhancement
// (ERLE) and the linear power estimate.
void LinearEstimate(
    rtc::ArrayView<const std::array<float, kFftLengthBy2Plus1>> S2_linear,
    rtc::ArrayView<const std::array<float, kFftLengthBy2Plus1>> erle,
    rtc::ArrayView<std::array<float, kFftLengthBy2Plus1>> R2) {
  RTC_DCHECK_EQ(S2_linear.size(), erle.size());
  RTC_DCHECK_EQ(S2_linear.size(), R2.size());

  const size_t num_capture_channels = R2.size();
  for (size_t ch = 0; ch < num_capture_channels; ++ch) {
    for (size_t k = 0; k < kFftLengthBy2Plus1; ++k) {
      RTC_DCHECK_LT(0.f, erle[ch][k]);
      R2[ch][k] = S2_linear[ch][k] / erle[ch][k];
    }
  }
}

// Estimates the residual echo power based on the estimate of the echo path
// gain.
void NonLinearEstimate(
    float echo_path_gain,
    const std::array<float, kFftLengthBy2Plus1>& X2,
    rtc::ArrayView<std::array<float, kFftLengthBy2Plus1>> R2) {
  const size_t num_capture_channels = R2.size();
  for (size_t ch = 0; ch < num_capture_channels; ++ch) {
    for (size_t k = 0; k < kFftLengthBy2Plus1; ++k) {
      R2[ch][k] = X2[k] * echo_path_gain;
    }
  }
}

// Applies a soft noise gate to the echo generating power.
void ApplyNoiseGate(const EchoCanceller3Config::EchoModel& config,
                    rtc::ArrayView<float, kFftLengthBy2Plus1> X2) {
  for (size_t k = 0; k < kFftLengthBy2Plus1; ++k) {
    if (config.noise_gate_power > X2[k]) {
      X2[k] = std::max(0.f, X2[k] - config.noise_gate_slope *
                                        (config.noise_gate_power - X2[k]));
    }
  }
}

// Estimates the echo generating signal power as gated maximal power over a
// time window.
void EchoGeneratingPower(size_t num_render_channels,
                         const SpectrumBuffer& spectrum_buffer,
                         const EchoCanceller3Config::EchoModel& echo_model,
                         int filter_delay_blocks,
                         rtc::ArrayView<float, kFftLengthBy2Plus1> X2) {
  int idx_stop;
  int idx_start;
  GetRenderIndexesToAnalyze(spectrum_buffer, echo_model, filter_delay_blocks,
                            &idx_start, &idx_stop);

  std::fill(X2.begin(), X2.end(), 0.f);
  if (num_render_channels == 1) {
    for (int k = idx_start; k != idx_stop; k = spectrum_buffer.IncIndex(k)) {
      for (size_t j = 0; j < kFftLengthBy2Plus1; ++j) {
        X2[j] = std::max(X2[j], spectrum_buffer.buffer[k][/*channel=*/0][j]);
      }
    }
  } else {
    for (int k = idx_start; k != idx_stop; k = spectrum_buffer.IncIndex(k)) {
      std::array<float, kFftLengthBy2Plus1> render_power;
      render_power.fill(0.f);
      for (size_t ch = 0; ch < num_render_channels; ++ch) {
        const auto& channel_power = spectrum_buffer.buffer[k][ch];
        for (size_t j = 0; j < kFftLengthBy2Plus1; ++j) {
          render_power[j] += channel_power[j];
        }
      }
      for (size_t j = 0; j < kFftLengthBy2Plus1; ++j) {
        X2[j] = std::max(X2[j], render_power[j]);
      }
    }
  }
}

}  // namespace

ResidualEchoEstimator::ResidualEchoEstimator(const EchoCanceller3Config& config,
                                             size_t num_render_channels)
    : config_(config),
      num_render_channels_(num_render_channels),
      early_reflections_transparent_mode_gain_(GetTransparentModeGain()),
      late_reflections_transparent_mode_gain_(GetTransparentModeGain()),
      early_reflections_general_gain_(
          GetEarlyReflectionsDefaultModeGain(config_.ep_strength)),
      late_reflections_general_gain_(
          GetLateReflectionsDefaultModeGain(config_.ep_strength)),
      erle_onset_compensation_in_dominant_nearend_(
          UseErleOnsetCompensationInDominantNearend(config_.ep_strength)) {
  Reset();
}

ResidualEchoEstimator::~ResidualEchoEstimator() = default;

void ResidualEchoEstimator::Estimate(
    const AecState& aec_state,
    const RenderBuffer& render_buffer,
    rtc::ArrayView<const std::array<float, kFftLengthBy2Plus1>> S2_linear,
    rtc::ArrayView<const std::array<float, kFftLengthBy2Plus1>> Y2,
    bool dominant_nearend,
    rtc::ArrayView<std::array<float, kFftLengthBy2Plus1>> R2) {
  RTC_DCHECK_EQ(R2.size(), Y2.size());
  RTC_DCHECK_EQ(R2.size(), S2_linear.size());

  const size_t num_capture_channels = R2.size();

  // Estimate the power of the stationary noise in the render signal.
  UpdateRenderNoisePower(render_buffer);

  // Estimate the residual echo power.
  if (aec_state.UsableLinearEstimate()) {
    // When there is saturated echo, assume the same spectral content as is
    // present in the microphone signal.
    if (aec_state.SaturatedEcho()) {
      for (size_t ch = 0; ch < num_capture_channels; ++ch) {
        std::copy(Y2[ch].begin(), Y2[ch].end(), R2[ch].begin());
      }
    } else {
      const bool onset_compensated =
          erle_onset_compensation_in_dominant_nearend_ || !dominant_nearend;
      LinearEstimate(S2_linear, aec_state.Erle(onset_compensated), R2);
    }

    AddReverb(ReverbType::kLinear, aec_state, render_buffer, R2);
  } else {
    const float echo_path_gain =
        GetEchoPathGain(aec_state, /*gain_for_early_reflections=*/true);

    // When there is saturated echo, assume the same spectral content as is
    // present in the microphone signal.
    if (aec_state.SaturatedEcho()) {
      for (size_t ch = 0; ch < num_capture_channels; ++ch) {
        std::copy(Y2[ch].begin(), Y2[ch].end(), R2[ch].begin());
      }
    } else {
      // Estimate the echo generating signal power.
      std::array<float, kFftLengthBy2Plus1> X2;
      EchoGeneratingPower(num_render_channels_,
                          render_buffer.GetSpectrumBuffer(), config_.echo_model,
                          aec_state.MinDirectPathFilterDelay(), X2);
      if (!aec_state.UseStationarityProperties()) {
        ApplyNoiseGate(config_.echo_model, X2);
      }

      // Subtract the stationary noise power to avoid stationary noise causing
      // excessive echo suppression.
      for (size_t k = 0; k < kFftLengthBy2Plus1; ++k) {
        X2[k] -= config_.echo_model.stationary_gate_slope * X2_noise_floor_[k];
        X2[k] = std::max(0.f, X2[k]);
      }

      NonLinearEstimate(echo_path_gain, X2, R2);
    }

    if (config_.echo_model.model_reverb_in_nonlinear_mode &&
        !aec_state.TransparentModeActive()) {
      AddReverb(ReverbType::kNonLinear, aec_state, render_buffer, R2);
    }
  }

  if (aec_state.UseStationarityProperties()) {
    // Scale the echo according to echo audibility.
    std::array<float, kFftLengthBy2Plus1> residual_scaling;
    aec_state.GetResidualEchoScaling(residual_scaling);
    for (size_t ch = 0; ch < num_capture_channels; ++ch) {
      for (size_t k = 0; k < kFftLengthBy2Plus1; ++k) {
        R2[ch][k] *= residual_scaling[k];
      }
    }
  }
}

void ResidualEchoEstimator::Reset() {
  echo_reverb_.Reset();
  X2_noise_floor_counter_.fill(config_.echo_model.noise_floor_hold);
  X2_noise_floor_.fill(config_.echo_model.min_noise_floor_power);
}

void ResidualEchoEstimator::UpdateRenderNoisePower(
    const RenderBuffer& render_buffer) {
  std::array<float, kFftLengthBy2Plus1> render_power_data;
  rtc::ArrayView<const std::array<float, kFftLengthBy2Plus1>> X2 =
      render_buffer.Spectrum(0);
  rtc::ArrayView<const float, kFftLengthBy2Plus1> render_power =
      X2[/*channel=*/0];
  if (num_render_channels_ > 1) {
    render_power_data.fill(0.f);
    for (size_t ch = 0; ch < num_render_channels_; ++ch) {
      const auto& channel_power = X2[ch];
      for (size_t k = 0; k < kFftLengthBy2Plus1; ++k) {
        render_power_data[k] += channel_power[k];
      }
    }
    render_power = render_power_data;
  }

  // Estimate the stationary noise power in a minimum statistics manner.
  for (size_t k = 0; k < kFftLengthBy2Plus1; ++k) {
    // Decrease rapidly.
    if (render_power[k] < X2_noise_floor_[k]) {
      X2_noise_floor_[k] = render_power[k];
      X2_noise_floor_counter_[k] = 0;
    } else {
      // Increase in a delayed, leaky manner.
      if (X2_noise_floor_counter_[k] >=
          static_cast<int>(config_.echo_model.noise_floor_hold)) {
        X2_noise_floor_[k] = std::max(X2_noise_floor_[k] * 1.1f,
                                      config_.echo_model.min_noise_floor_power);
      } else {
        ++X2_noise_floor_counter_[k];
      }
    }
  }
}

// Adds the estimated power of the reverb to the residual echo power.
void ResidualEchoEstimator::AddReverb(
    ReverbType reverb_type,
    const AecState& aec_state,
    const RenderBuffer& render_buffer,
    rtc::ArrayView<std::array<float, kFftLengthBy2Plus1>> R2) {
  const size_t num_capture_channels = R2.size();

  // Choose reverb partition based on what type of echo power model is used.
  const size_t first_reverb_partition =
      reverb_type == ReverbType::kLinear
          ? aec_state.FilterLengthBlocks() + 1
          : aec_state.MinDirectPathFilterDelay() + 1;

  // Compute render power for the reverb.
  std::array<float, kFftLengthBy2Plus1> render_power_data;
  rtc::ArrayView<const std::array<float, kFftLengthBy2Plus1>> X2 =
      render_buffer.Spectrum(first_reverb_partition);
  rtc::ArrayView<const float, kFftLengthBy2Plus1> render_power =
      X2[/*channel=*/0];
  if (num_render_channels_ > 1) {
    render_power_data.fill(0.f);
    for (size_t ch = 0; ch < num_render_channels_; ++ch) {
      const auto& channel_power = X2[ch];
      for (size_t k = 0; k < kFftLengthBy2Plus1; ++k) {
        render_power_data[k] += channel_power[k];
      }
    }
    render_power = render_power_data;
  }

  // Update the reverb estimate.
  if (reverb_type == ReverbType::kLinear) {
    echo_reverb_.UpdateReverb(render_power,
                              aec_state.GetReverbFrequencyResponse(),
                              aec_state.ReverbDecay());
  } else {
    const float echo_path_gain =
        GetEchoPathGain(aec_state, /*gain_for_early_reflections=*/false);
    echo_reverb_.UpdateReverbNoFreqShaping(render_power, echo_path_gain,
                                           aec_state.ReverbDecay());
  }

  // Add the reverb power.
  rtc::ArrayView<const float, kFftLengthBy2Plus1> reverb_power =
      echo_reverb_.reverb();
  for (size_t ch = 0; ch < num_capture_channels; ++ch) {
    for (size_t k = 0; k < kFftLengthBy2Plus1; ++k) {
      R2[ch][k] += reverb_power[k];
    }
  }
}

// Chooses the echo path gain to use.
float ResidualEchoEstimator::GetEchoPathGain(
    const AecState& aec_state,
    bool gain_for_early_reflections) const {
  float gain_amplitude;
  if (aec_state.TransparentModeActive()) {
    gain_amplitude = gain_for_early_reflections
                         ? early_reflections_transparent_mode_gain_
                         : late_reflections_transparent_mode_gain_;
  } else {
    gain_amplitude = gain_for_early_reflections
                         ? early_reflections_general_gain_
                         : late_reflections_general_gain_;
  }
  return gain_amplitude * gain_amplitude;
}

}  // namespace webrtc
