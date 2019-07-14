
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
#include "modules/audio_processing/aec3/reverb_model_fallback.h"
#include "rtc_base/checks.h"
#include "system_wrappers/include/field_trial.h"

namespace webrtc {
namespace {

bool EnableSoftTransparentMode() {
  return !field_trial::IsEnabled("WebRTC-Aec3SoftTransparentModeKillSwitch");
}

bool OverrideEstimatedEchoPathGain() {
  return !field_trial::IsEnabled("WebRTC-Aec3OverrideEchoPathGainKillSwitch");
}

bool UseFixedNonLinearReverbModel() {
  return field_trial::IsEnabled(
      "WebRTC-Aec3StandardNonlinearReverbModelKillSwitch");
}

// Computes the indexes that will be used for computing spectral power over
// the blocks surrounding the delay.
void GetRenderIndexesToAnalyze(
    const VectorBuffer& spectrum_buffer,
    const EchoCanceller3Config::EchoModel& echo_model,
    int filter_delay_blocks,
    bool gain_limiter_running,
    int headroom,
    int* idx_start,
    int* idx_stop) {
  RTC_DCHECK(idx_start);
  RTC_DCHECK(idx_stop);
  if (gain_limiter_running) {
    if (static_cast<size_t>(headroom) >
        echo_model.render_post_window_size_init) {
      *idx_start = spectrum_buffer.OffsetIndex(
          spectrum_buffer.read,
          -static_cast<int>(echo_model.render_post_window_size_init));
    } else {
      *idx_start = spectrum_buffer.IncIndex(spectrum_buffer.write);
    }

    *idx_stop = spectrum_buffer.OffsetIndex(
        spectrum_buffer.read, echo_model.render_pre_window_size_init);
  } else {
    size_t window_start;
    size_t window_end;
    window_start =
        std::max(0, filter_delay_blocks -
                        static_cast<int>(echo_model.render_pre_window_size));
    window_end = filter_delay_blocks +
                 static_cast<int>(echo_model.render_post_window_size);
    *idx_start =
        spectrum_buffer.OffsetIndex(spectrum_buffer.read, window_start);
    *idx_stop =
        spectrum_buffer.OffsetIndex(spectrum_buffer.read, window_end + 1);
  }
}

}  // namespace

ResidualEchoEstimator::ResidualEchoEstimator(const EchoCanceller3Config& config)
    : config_(config),
      soft_transparent_mode_(EnableSoftTransparentMode()),
      override_estimated_echo_path_gain_(OverrideEstimatedEchoPathGain()),
      use_fixed_nonlinear_reverb_model_(UseFixedNonLinearReverbModel()) {
  if (config_.ep_strength.reverb_based_on_render) {
    echo_reverb_.reset(new ReverbModel());
  } else {
    echo_reverb_fallback.reset(
        new ReverbModelFallback(config_.filter.main.length_blocks));
  }
  Reset();
}

ResidualEchoEstimator::~ResidualEchoEstimator() = default;

void ResidualEchoEstimator::Estimate(
    const AecState& aec_state,
    const RenderBuffer& render_buffer,
    const std::array<float, kFftLengthBy2Plus1>& S2_linear,
    const std::array<float, kFftLengthBy2Plus1>& Y2,
    std::array<float, kFftLengthBy2Plus1>* R2) {
  RTC_DCHECK(R2);

  // Estimate the power of the stationary noise in the render signal.
  RenderNoisePower(render_buffer, &X2_noise_floor_, &X2_noise_floor_counter_);

  // Estimate the residual echo power.
  if (aec_state.UsableLinearEstimate()) {
    LinearEstimate(S2_linear, aec_state.Erle(), aec_state.ErleUncertainty(),
                   R2);

    // When there is saturated echo, assume the same spectral content as is
    // present in the micropone signal.
    if (aec_state.SaturatedEcho()) {
      std::copy(Y2.begin(), Y2.end(), R2->begin());
    }

    // Adds the estimated unmodelled echo power to the residual echo power
    // estimate.
    if (echo_reverb_) {
      echo_reverb_->AddReverb(
          render_buffer.Spectrum(aec_state.FilterLengthBlocks() + 1),
          aec_state.GetReverbFrequencyResponse(), aec_state.ReverbDecay(), *R2);

    } else {
      RTC_DCHECK(echo_reverb_fallback);
      echo_reverb_fallback->AddEchoReverb(S2_linear,
                                          aec_state.FilterDelayBlocks(),
                                          aec_state.ReverbDecay(), R2);
    }

  } else {
    // Estimate the echo generating signal power.
    std::array<float, kFftLengthBy2Plus1> X2;

    EchoGeneratingPower(render_buffer.GetSpectrumBuffer(), config_.echo_model,
                        render_buffer.Headroom(), aec_state.FilterDelayBlocks(),
                        aec_state.IsSuppressionGainLimitActive(),
                        !aec_state.UseStationaryProperties(), &X2);

    // Subtract the stationary noise power to avoid stationary noise causing
    // excessive echo suppression.
    std::transform(X2.begin(), X2.end(), X2_noise_floor_.begin(), X2.begin(),
                   [&](float a, float b) {
                     return std::max(
                         0.f, a - config_.echo_model.stationary_gate_slope * b);
                   });

    float echo_path_gain;
    if (override_estimated_echo_path_gain_) {
      echo_path_gain = aec_state.TransparentMode() && soft_transparent_mode_
                           ? 0.01f
                           : config_.ep_strength.lf;
    } else {
      echo_path_gain = aec_state.TransparentMode() && soft_transparent_mode_
                           ? 0.01f
                           : aec_state.EchoPathGain();
    }
    NonLinearEstimate(echo_path_gain, X2, Y2, R2);

    // When there is saturated echo, assume the same spectral content as is
    // present in the micropone signal.
    if (aec_state.SaturatedEcho()) {
      std::copy(Y2.begin(), Y2.end(), R2->begin());
    }

    if (!(aec_state.TransparentMode() && soft_transparent_mode_)) {
      if (echo_reverb_) {
        echo_reverb_->AddReverbNoFreqShaping(
            render_buffer.Spectrum(aec_state.FilterDelayBlocks() + 1),
            echo_path_gain * echo_path_gain, aec_state.ReverbDecay(), *R2);
      } else {
        RTC_DCHECK(echo_reverb_fallback);
        echo_reverb_fallback->AddEchoReverb(*R2,
                                            config_.filter.main.length_blocks,
                                            aec_state.ReverbDecay(), R2);
      }
    }
  }

  if (aec_state.UseStationaryProperties()) {
    // Scale the echo according to echo audibility.
    std::array<float, kFftLengthBy2Plus1> residual_scaling;
    aec_state.GetResidualEchoScaling(residual_scaling);
    for (size_t k = 0; k < R2->size(); ++k) {
      (*R2)[k] *= residual_scaling[k];
      if (residual_scaling[k] == 0.f) {
        R2_hold_counter_[k] = 0;
      }
    }
  }
  if (!soft_transparent_mode_) {
    // If the echo is deemed inaudible, set the residual echo to zero.
    if (aec_state.TransparentMode()) {
      R2->fill(0.f);
      R2_old_.fill(0.f);
      R2_hold_counter_.fill(0.f);
    }
  }

  std::copy(R2->begin(), R2->end(), R2_old_.begin());
}

void ResidualEchoEstimator::Reset() {
  if (echo_reverb_) {
    echo_reverb_->Reset();
  } else {
    RTC_DCHECK(echo_reverb_fallback);
    echo_reverb_fallback->Reset();
  }
  X2_noise_floor_counter_.fill(config_.echo_model.noise_floor_hold);
  X2_noise_floor_.fill(config_.echo_model.min_noise_floor_power);
  R2_old_.fill(0.f);
  R2_hold_counter_.fill(0.f);
}

void ResidualEchoEstimator::LinearEstimate(
    const std::array<float, kFftLengthBy2Plus1>& S2_linear,
    const std::array<float, kFftLengthBy2Plus1>& erle,
    absl::optional<float> erle_uncertainty,
    std::array<float, kFftLengthBy2Plus1>* R2) {
  std::fill(R2_hold_counter_.begin(), R2_hold_counter_.end(), 10.f);
  if (erle_uncertainty) {
    for (size_t k = 0; k < R2->size(); ++k) {
      (*R2)[k] = S2_linear[k] * *erle_uncertainty;
    }
  } else {
    std::transform(erle.begin(), erle.end(), S2_linear.begin(), R2->begin(),
                   [](float a, float b) {
                     RTC_DCHECK_LT(0.f, a);
                     return b / a;
                   });
  }
}

void ResidualEchoEstimator::NonLinearEstimate(
    float echo_path_gain,
    const std::array<float, kFftLengthBy2Plus1>& X2,
    const std::array<float, kFftLengthBy2Plus1>& Y2,
    std::array<float, kFftLengthBy2Plus1>* R2) {
  // Compute preliminary residual echo.
  std::transform(X2.begin(), X2.end(), R2->begin(), [echo_path_gain](float a) {
    return a * echo_path_gain * echo_path_gain;
  });

  if (use_fixed_nonlinear_reverb_model_) {
    for (size_t k = 0; k < R2->size(); ++k) {
      // Update hold counter.
      R2_hold_counter_[k] = R2_old_[k] < (*R2)[k] ? 0 : R2_hold_counter_[k] + 1;

      // Compute the residual echo by holding a maximum echo powers and an echo
      // fading corresponding to a room with an RT60 value of about 50 ms.
      (*R2)[k] =
          R2_hold_counter_[k] < config_.echo_model.nonlinear_hold
              ? std::max((*R2)[k], R2_old_[k])
              : std::min((*R2)[k] +
                             R2_old_[k] * config_.echo_model.nonlinear_release,
                         Y2[k]);
    }
  }
}

void ResidualEchoEstimator::EchoGeneratingPower(
    const VectorBuffer& spectrum_buffer,
    const EchoCanceller3Config::EchoModel& echo_model,
    int headroom_spectrum_buffer,
    int filter_delay_blocks,
    bool gain_limiter_running,
    bool apply_noise_gating,
    std::array<float, kFftLengthBy2Plus1>* X2) const {
  int idx_stop, idx_start;

  RTC_DCHECK(X2);
  GetRenderIndexesToAnalyze(spectrum_buffer, config_.echo_model,
                            filter_delay_blocks, gain_limiter_running,
                            headroom_spectrum_buffer, &idx_start, &idx_stop);

  X2->fill(0.f);
  for (int k = idx_start; k != idx_stop; k = spectrum_buffer.IncIndex(k)) {
    std::transform(X2->begin(), X2->end(), spectrum_buffer.buffer[k].begin(),
                   X2->begin(),
                   [](float a, float b) { return std::max(a, b); });
  }

  if (apply_noise_gating) {
    // Apply soft noise gate.
    std::for_each(X2->begin(), X2->end(), [&](float& a) {
      if (config_.echo_model.noise_gate_power > a) {
        a = std::max(0.f, a - config_.echo_model.noise_gate_slope *
                                  (config_.echo_model.noise_gate_power - a));
      }
    });
  }
}

void ResidualEchoEstimator::RenderNoisePower(
    const RenderBuffer& render_buffer,
    std::array<float, kFftLengthBy2Plus1>* X2_noise_floor,
    std::array<int, kFftLengthBy2Plus1>* X2_noise_floor_counter) const {
  RTC_DCHECK(X2_noise_floor);
  RTC_DCHECK(X2_noise_floor_counter);

  const auto render_power = render_buffer.Spectrum(0);
  RTC_DCHECK_EQ(X2_noise_floor->size(), render_power.size());
  RTC_DCHECK_EQ(X2_noise_floor_counter->size(), render_power.size());

  // Estimate the stationary noise power in a minimum statistics manner.
  for (size_t k = 0; k < render_power.size(); ++k) {
    // Decrease rapidly.
    if (render_power[k] < (*X2_noise_floor)[k]) {
      (*X2_noise_floor)[k] = render_power[k];
      (*X2_noise_floor_counter)[k] = 0;
    } else {
      // Increase in a delayed, leaky manner.
      if ((*X2_noise_floor_counter)[k] >=
          static_cast<int>(config_.echo_model.noise_floor_hold)) {
        (*X2_noise_floor)[k] =
            std::max((*X2_noise_floor)[k] * 1.1f,
                     config_.echo_model.min_noise_floor_power);
      } else {
        ++(*X2_noise_floor_counter)[k];
      }
    }
  }
}

}  // namespace webrtc
