/*
 *  Copyright (c) 2017 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "modules/audio_processing/aec3/aec_state.h"

#include <math.h>

#include <algorithm>
#include <numeric>
#include <vector>

#include "absl/types/optional.h"
#include "api/array_view.h"
#include "modules/audio_processing/aec3/aec3_common.h"
#include "modules/audio_processing/logging/apm_data_dumper.h"
#include "rtc_base/atomic_ops.h"
#include "rtc_base/checks.h"
#include "system_wrappers/include/field_trial.h"

namespace webrtc {
namespace {

constexpr size_t kBlocksSinceConvergencedFilterInit = 10000;
constexpr size_t kBlocksSinceConsistentEstimateInit = 10000;

bool DeactivateTransparentMode() {
  return field_trial::IsEnabled("WebRTC-Aec3TransparentModeKillSwitch");
}

bool DeactivateInitialStateResetAtEchoPathChange() {
  return field_trial::IsEnabled(
      "WebRTC-Aec3DeactivateInitialStateResetKillSwitch");
}

bool FullResetAtEchoPathChange() {
  return !field_trial::IsEnabled("WebRTC-Aec3AecStateFullResetKillSwitch");
}

bool SubtractorAnalyzerResetAtEchoPathChange() {
  return !field_trial::IsEnabled(
      "WebRTC-Aec3AecStateSubtractorAnalyzerResetKillSwitch");
}

void ComputeAvgRenderReverb(
    const SpectrumBuffer& spectrum_buffer,
    int delay_blocks,
    float reverb_decay,
    ReverbModel* reverb_model,
    rtc::ArrayView<float, kFftLengthBy2Plus1> reverb_power_spectrum) {
  RTC_DCHECK(reverb_model);
  const size_t num_render_channels = spectrum_buffer.buffer[0].size();
  int idx_at_delay =
      spectrum_buffer.OffsetIndex(spectrum_buffer.read, delay_blocks);
  int idx_past = spectrum_buffer.IncIndex(idx_at_delay);

  std::array<float, kFftLengthBy2Plus1> X2_data;
  rtc::ArrayView<const float> X2;
  if (num_render_channels > 1) {
    auto average_channels =
        [](size_t num_render_channels,
           rtc::ArrayView<const std::array<float, kFftLengthBy2Plus1>>
               spectrum_band_0,
           rtc::ArrayView<float, kFftLengthBy2Plus1> render_power) {
          std::fill(render_power.begin(), render_power.end(), 0.f);
          for (size_t ch = 0; ch < num_render_channels; ++ch) {
            for (size_t k = 0; k < kFftLengthBy2Plus1; ++k) {
              render_power[k] += spectrum_band_0[ch][k];
            }
          }
          const float normalizer = 1.f / num_render_channels;
          for (size_t k = 0; k < kFftLengthBy2Plus1; ++k) {
            render_power[k] *= normalizer;
          }
        };
    average_channels(num_render_channels, spectrum_buffer.buffer[idx_past],
                     X2_data);
    reverb_model->UpdateReverbNoFreqShaping(
        X2_data, /*power_spectrum_scaling=*/1.0f, reverb_decay);

    average_channels(num_render_channels, spectrum_buffer.buffer[idx_at_delay],
                     X2_data);
    X2 = X2_data;
  } else {
    reverb_model->UpdateReverbNoFreqShaping(
        spectrum_buffer.buffer[idx_past][/*channel=*/0],
        /*power_spectrum_scaling=*/1.0f, reverb_decay);

    X2 = spectrum_buffer.buffer[idx_at_delay][/*channel=*/0];
  }

  rtc::ArrayView<const float, kFftLengthBy2Plus1> reverb_power =
      reverb_model->reverb();
  for (size_t k = 0; k < X2.size(); ++k) {
    reverb_power_spectrum[k] = X2[k] + reverb_power[k];
  }
}

}  // namespace

int AecState::instance_count_ = 0;

void AecState::GetResidualEchoScaling(
    rtc::ArrayView<float> residual_scaling) const {
  bool filter_has_had_time_to_converge;
  if (config_.filter.conservative_initial_phase) {
    filter_has_had_time_to_converge =
        strong_not_saturated_render_blocks_ >= 1.5f * kNumBlocksPerSecond;
  } else {
    filter_has_had_time_to_converge =
        strong_not_saturated_render_blocks_ >= 0.8f * kNumBlocksPerSecond;
  }
  echo_audibility_.GetResidualEchoScaling(filter_has_had_time_to_converge,
                                          residual_scaling);
}

absl::optional<float> AecState::ErleUncertainty() const {
  if (SaturatedEcho()) {
    return 1.f;
  }

  return absl::nullopt;
}

AecState::AecState(const EchoCanceller3Config& config,
                   size_t num_capture_channels)
    : data_dumper_(
          new ApmDataDumper(rtc::AtomicOps::Increment(&instance_count_))),
      config_(config),
      num_capture_channels_(num_capture_channels),
      transparent_mode_activated_(!DeactivateTransparentMode()),
      deactivate_initial_state_reset_at_echo_path_change_(
          DeactivateInitialStateResetAtEchoPathChange()),
      full_reset_at_echo_path_change_(FullResetAtEchoPathChange()),
      subtractor_analyzer_reset_at_echo_path_change_(
          SubtractorAnalyzerResetAtEchoPathChange()),
      initial_state_(config_),
      delay_state_(config_, num_capture_channels_),
      transparent_state_(config_),
      filter_quality_state_(config_, num_capture_channels_),
      erl_estimator_(2 * kNumBlocksPerSecond),
      erle_estimator_(2 * kNumBlocksPerSecond, config_, num_capture_channels_),
      filter_analyzer_(config_, num_capture_channels_),
      echo_audibility_(
          config_.echo_audibility.use_stationarity_properties_at_init),
      reverb_model_estimator_(config_, num_capture_channels_),
      subtractor_output_analyzer_(num_capture_channels_) {}

AecState::~AecState() = default;

void AecState::HandleEchoPathChange(
    const EchoPathVariability& echo_path_variability) {
  const auto full_reset = [&]() {
    filter_analyzer_.Reset();
    capture_signal_saturation_ = false;
    strong_not_saturated_render_blocks_ = 0;
    blocks_with_active_render_ = 0;
    if (!deactivate_initial_state_reset_at_echo_path_change_) {
      initial_state_.Reset();
    }
    transparent_state_.Reset();
    erle_estimator_.Reset(true);
    erl_estimator_.Reset();
    filter_quality_state_.Reset();
  };

  // TODO(peah): Refine the reset scheme according to the type of gain and
  // delay adjustment.

  if (full_reset_at_echo_path_change_ &&
      echo_path_variability.delay_change !=
          EchoPathVariability::DelayAdjustment::kNone) {
    full_reset();
  } else if (echo_path_variability.gain_change) {
    erle_estimator_.Reset(false);
  }
  if (subtractor_analyzer_reset_at_echo_path_change_) {
    subtractor_output_analyzer_.HandleEchoPathChange();
  }
}

void AecState::Update(
    const absl::optional<DelayEstimate>& external_delay,
    rtc::ArrayView<const std::vector<std::array<float, kFftLengthBy2Plus1>>>
        adaptive_filter_frequency_responses,
    rtc::ArrayView<const std::vector<float>> adaptive_filter_impulse_responses,
    const RenderBuffer& render_buffer,
    rtc::ArrayView<const std::array<float, kFftLengthBy2Plus1>> E2_refined,
    rtc::ArrayView<const std::array<float, kFftLengthBy2Plus1>> Y2,
    rtc::ArrayView<const SubtractorOutput> subtractor_output) {
  RTC_DCHECK_EQ(num_capture_channels_, Y2.size());
  RTC_DCHECK_EQ(num_capture_channels_, subtractor_output.size());
  RTC_DCHECK_EQ(num_capture_channels_,
                adaptive_filter_frequency_responses.size());
  RTC_DCHECK_EQ(num_capture_channels_,
                adaptive_filter_impulse_responses.size());

  // Analyze the filter outputs and filters.
  bool any_filter_converged;
  bool all_filters_diverged;
  subtractor_output_analyzer_.Update(subtractor_output, &any_filter_converged,
                                     &all_filters_diverged);

  bool any_filter_consistent;
  float max_echo_path_gain;
  filter_analyzer_.Update(adaptive_filter_impulse_responses, render_buffer,
                          &any_filter_consistent, &max_echo_path_gain);

  // Estimate the direct path delay of the filter.
  if (config_.filter.use_linear_filter) {
    delay_state_.Update(filter_analyzer_.FilterDelaysBlocks(), external_delay,
                        strong_not_saturated_render_blocks_);
  }

  const std::vector<std::vector<float>>& aligned_render_block =
      render_buffer.Block(-delay_state_.MinDirectPathFilterDelay())[0];

  // Update render counters.
  bool active_render = false;
  for (size_t ch = 0; ch < aligned_render_block.size(); ++ch) {
    const float render_energy = std::inner_product(
        aligned_render_block[ch].begin(), aligned_render_block[ch].end(),
        aligned_render_block[ch].begin(), 0.f);
    if (render_energy > (config_.render_levels.active_render_limit *
                         config_.render_levels.active_render_limit) *
                            kFftLengthBy2) {
      active_render = true;
      break;
    }
  }
  blocks_with_active_render_ += active_render ? 1 : 0;
  strong_not_saturated_render_blocks_ +=
      active_render && !SaturatedCapture() ? 1 : 0;

  std::array<float, kFftLengthBy2Plus1> avg_render_spectrum_with_reverb;

  ComputeAvgRenderReverb(render_buffer.GetSpectrumBuffer(),
                         delay_state_.MinDirectPathFilterDelay(), ReverbDecay(),
                         &avg_render_reverb_, avg_render_spectrum_with_reverb);

  if (config_.echo_audibility.use_stationarity_properties) {
    // Update the echo audibility evaluator.
    echo_audibility_.Update(render_buffer, avg_render_reverb_.reverb(),
                            delay_state_.MinDirectPathFilterDelay(),
                            delay_state_.ExternalDelayReported());
  }

  // Update the ERL and ERLE measures.
  if (initial_state_.TransitionTriggered()) {
    erle_estimator_.Reset(false);
  }

  erle_estimator_.Update(render_buffer, adaptive_filter_frequency_responses,
                         avg_render_spectrum_with_reverb, Y2, E2_refined,
                         subtractor_output_analyzer_.ConvergedFilters());

  erl_estimator_.Update(
      subtractor_output_analyzer_.ConvergedFilters(),
      render_buffer.Spectrum(delay_state_.MinDirectPathFilterDelay()), Y2);

  // Detect and flag echo saturation.
  if (config_.ep_strength.echo_can_saturate) {
    saturation_detector_.Update(aligned_render_block, SaturatedCapture(),
                                UsableLinearEstimate(), subtractor_output,
                                max_echo_path_gain);
  } else {
    RTC_DCHECK(!saturation_detector_.SaturatedEcho());
  }

  // Update the decision on whether to use the initial state parameter set.
  initial_state_.Update(active_render, SaturatedCapture());

  // Detect whether the transparent mode should be activated.
  transparent_state_.Update(delay_state_.MinDirectPathFilterDelay(),
                            any_filter_consistent, any_filter_converged,
                            all_filters_diverged, active_render,
                            SaturatedCapture());

  // Analyze the quality of the filter.
  filter_quality_state_.Update(active_render, TransparentMode(),
                               SaturatedCapture(), external_delay,
                               any_filter_converged);

  // Update the reverb estimate.
  const bool stationary_block =
      config_.echo_audibility.use_stationarity_properties &&
      echo_audibility_.IsBlockStationary();

  reverb_model_estimator_.Update(
      filter_analyzer_.GetAdjustedFilters(),
      adaptive_filter_frequency_responses,
      erle_estimator_.GetInstLinearQualityEstimates(),
      delay_state_.DirectPathFilterDelays(),
      filter_quality_state_.UsableLinearFilterOutputs(), stationary_block);

  erle_estimator_.Dump(data_dumper_);
  reverb_model_estimator_.Dump(data_dumper_.get());
  data_dumper_->DumpRaw("aec3_erl", Erl());
  data_dumper_->DumpRaw("aec3_erl_time_domain", ErlTimeDomain());
  data_dumper_->DumpRaw("aec3_erle", Erle()[0]);
  data_dumper_->DumpRaw("aec3_usable_linear_estimate", UsableLinearEstimate());
  data_dumper_->DumpRaw("aec3_transparent_mode", TransparentMode());
  data_dumper_->DumpRaw("aec3_filter_delay",
                        filter_analyzer_.MinFilterDelayBlocks());

  data_dumper_->DumpRaw("aec3_any_filter_consistent", any_filter_consistent);
  data_dumper_->DumpRaw("aec3_initial_state",
                        initial_state_.InitialStateActive());
  data_dumper_->DumpRaw("aec3_capture_saturation", SaturatedCapture());
  data_dumper_->DumpRaw("aec3_echo_saturation", SaturatedEcho());
  data_dumper_->DumpRaw("aec3_any_filter_converged", any_filter_converged);
  data_dumper_->DumpRaw("aec3_all_filters_diverged", all_filters_diverged);

  data_dumper_->DumpRaw("aec3_external_delay_avaliable",
                        external_delay ? 1 : 0);
  data_dumper_->DumpRaw("aec3_filter_tail_freq_resp_est",
                        GetReverbFrequencyResponse());
}

AecState::InitialState::InitialState(const EchoCanceller3Config& config)
    : conservative_initial_phase_(config.filter.conservative_initial_phase),
      initial_state_seconds_(config.filter.initial_state_seconds) {
  Reset();
}
void AecState::InitialState::InitialState::Reset() {
  initial_state_ = true;
  strong_not_saturated_render_blocks_ = 0;
}
void AecState::InitialState::InitialState::Update(bool active_render,
                                                  bool saturated_capture) {
  strong_not_saturated_render_blocks_ +=
      active_render && !saturated_capture ? 1 : 0;

  // Flag whether the initial state is still active.
  bool prev_initial_state = initial_state_;
  if (conservative_initial_phase_) {
    initial_state_ =
        strong_not_saturated_render_blocks_ < 5 * kNumBlocksPerSecond;
  } else {
    initial_state_ = strong_not_saturated_render_blocks_ <
                     initial_state_seconds_ * kNumBlocksPerSecond;
  }

  // Flag whether the transition from the initial state has started.
  transition_triggered_ = !initial_state_ && prev_initial_state;
}

AecState::FilterDelay::FilterDelay(const EchoCanceller3Config& config,
                                   size_t num_capture_channels)
    : delay_headroom_samples_(config.delay.delay_headroom_samples),
      filter_delays_blocks_(num_capture_channels, 0) {}

void AecState::FilterDelay::Update(
    rtc::ArrayView<const int> analyzer_filter_delay_estimates_blocks,
    const absl::optional<DelayEstimate>& external_delay,
    size_t blocks_with_proper_filter_adaptation) {
  // Update the delay based on the external delay.
  if (external_delay &&
      (!external_delay_ || external_delay_->delay != external_delay->delay)) {
    external_delay_ = external_delay;
    external_delay_reported_ = true;
  }

  // Override the estimated delay if it is not certain that the filter has had
  // time to converge.
  const bool delay_estimator_may_not_have_converged =
      blocks_with_proper_filter_adaptation < 2 * kNumBlocksPerSecond;
  if (delay_estimator_may_not_have_converged && external_delay_) {
    int delay_guess = delay_headroom_samples_ / kBlockSize;
    std::fill(filter_delays_blocks_.begin(), filter_delays_blocks_.end(),
              delay_guess);
  } else {
    RTC_DCHECK_EQ(filter_delays_blocks_.size(),
                  analyzer_filter_delay_estimates_blocks.size());
    std::copy(analyzer_filter_delay_estimates_blocks.begin(),
              analyzer_filter_delay_estimates_blocks.end(),
              filter_delays_blocks_.begin());
  }

  min_filter_delay_ = *std::min_element(filter_delays_blocks_.begin(),
                                        filter_delays_blocks_.end());
}

AecState::TransparentMode::TransparentMode(const EchoCanceller3Config& config)
    : bounded_erl_(config.ep_strength.bounded_erl),
      linear_and_stable_echo_path_(
          config.echo_removal_control.linear_and_stable_echo_path),
      active_blocks_since_sane_filter_(kBlocksSinceConsistentEstimateInit),
      non_converged_sequence_size_(kBlocksSinceConvergencedFilterInit) {}

void AecState::TransparentMode::Reset() {
  non_converged_sequence_size_ = kBlocksSinceConvergencedFilterInit;
  diverged_sequence_size_ = 0;
  strong_not_saturated_render_blocks_ = 0;
  if (linear_and_stable_echo_path_) {
    recent_convergence_during_activity_ = false;
  }
}

void AecState::TransparentMode::Update(int filter_delay_blocks,
                                       bool any_filter_consistent,
                                       bool any_filter_converged,
                                       bool all_filters_diverged,
                                       bool active_render,
                                       bool saturated_capture) {
  ++capture_block_counter_;
  strong_not_saturated_render_blocks_ +=
      active_render && !saturated_capture ? 1 : 0;

  if (any_filter_consistent && filter_delay_blocks < 5) {
    sane_filter_observed_ = true;
    active_blocks_since_sane_filter_ = 0;
  } else if (active_render) {
    ++active_blocks_since_sane_filter_;
  }

  bool sane_filter_recently_seen;
  if (!sane_filter_observed_) {
    sane_filter_recently_seen =
        capture_block_counter_ <= 5 * kNumBlocksPerSecond;
  } else {
    sane_filter_recently_seen =
        active_blocks_since_sane_filter_ <= 30 * kNumBlocksPerSecond;
  }

  if (any_filter_converged) {
    recent_convergence_during_activity_ = true;
    active_non_converged_sequence_size_ = 0;
    non_converged_sequence_size_ = 0;
    ++num_converged_blocks_;
  } else {
    if (++non_converged_sequence_size_ > 20 * kNumBlocksPerSecond) {
      num_converged_blocks_ = 0;
    }

    if (active_render &&
        ++active_non_converged_sequence_size_ > 60 * kNumBlocksPerSecond) {
      recent_convergence_during_activity_ = false;
    }
  }

  if (!all_filters_diverged) {
    diverged_sequence_size_ = 0;
  } else if (++diverged_sequence_size_ >= 60) {
    // TODO(peah): Change these lines to ensure proper triggering of usable
    // filter.
    non_converged_sequence_size_ = kBlocksSinceConvergencedFilterInit;
  }

  if (active_non_converged_sequence_size_ > 60 * kNumBlocksPerSecond) {
    finite_erl_recently_detected_ = false;
  }
  if (num_converged_blocks_ > 50) {
    finite_erl_recently_detected_ = true;
  }

  if (bounded_erl_) {
    transparency_activated_ = false;
  } else if (finite_erl_recently_detected_) {
    transparency_activated_ = false;
  } else if (sane_filter_recently_seen && recent_convergence_during_activity_) {
    transparency_activated_ = false;
  } else {
    const bool filter_should_have_converged =
        strong_not_saturated_render_blocks_ > 6 * kNumBlocksPerSecond;
    transparency_activated_ = filter_should_have_converged;
  }
}

AecState::FilteringQualityAnalyzer::FilteringQualityAnalyzer(
    const EchoCanceller3Config& config,
    size_t num_capture_channels)
    : use_linear_filter_(config.filter.use_linear_filter),
      usable_linear_filter_estimates_(num_capture_channels, false) {}

void AecState::FilteringQualityAnalyzer::Reset() {
  std::fill(usable_linear_filter_estimates_.begin(),
            usable_linear_filter_estimates_.end(), false);
  overall_usable_linear_estimates_ = false;
  filter_update_blocks_since_reset_ = 0;
}

void AecState::FilteringQualityAnalyzer::Update(
    bool active_render,
    bool transparent_mode,
    bool saturated_capture,
    const absl::optional<DelayEstimate>& external_delay,
    bool any_filter_converged) {
  // Update blocks counter.
  const bool filter_update = active_render && !saturated_capture;
  filter_update_blocks_since_reset_ += filter_update ? 1 : 0;
  filter_update_blocks_since_start_ += filter_update ? 1 : 0;

  // Store convergence flag when observed.
  convergence_seen_ = convergence_seen_ || any_filter_converged;

  // Verify requirements for achieving a decent filter. The requirements for
  // filter adaptation at call startup are more restrictive than after an
  // in-call reset.
  const bool sufficient_data_to_converge_at_startup =
      filter_update_blocks_since_start_ > kNumBlocksPerSecond * 0.4f;
  const bool sufficient_data_to_converge_at_reset =
      sufficient_data_to_converge_at_startup &&
      filter_update_blocks_since_reset_ > kNumBlocksPerSecond * 0.2f;

  // The linear filter can only be used if it has had time to converge.
  overall_usable_linear_estimates_ = sufficient_data_to_converge_at_startup &&
                                     sufficient_data_to_converge_at_reset;

  // The linear filter can only be used if an external delay or convergence have
  // been identified
  overall_usable_linear_estimates_ =
      overall_usable_linear_estimates_ && (external_delay || convergence_seen_);

  // If transparent mode is on, deactivate usign the linear filter.
  overall_usable_linear_estimates_ =
      overall_usable_linear_estimates_ && !transparent_mode;

  if (use_linear_filter_) {
    std::fill(usable_linear_filter_estimates_.begin(),
              usable_linear_filter_estimates_.end(),
              overall_usable_linear_estimates_);
  }
}

void AecState::SaturationDetector::Update(
    rtc::ArrayView<const std::vector<float>> x,
    bool saturated_capture,
    bool usable_linear_estimate,
    rtc::ArrayView<const SubtractorOutput> subtractor_output,
    float echo_path_gain) {
  saturated_echo_ = false;
  if (!saturated_capture) {
    return;
  }

  if (usable_linear_estimate) {
    constexpr float kSaturationThreshold = 20000.f;
    for (size_t ch = 0; ch < subtractor_output.size(); ++ch) {
      saturated_echo_ =
          saturated_echo_ ||
          (subtractor_output[ch].s_refined_max_abs > kSaturationThreshold ||
           subtractor_output[ch].s_coarse_max_abs > kSaturationThreshold);
    }
  } else {
    float max_sample = 0.f;
    for (auto& channel : x) {
      for (float sample : channel) {
        max_sample = std::max(max_sample, fabsf(sample));
      }
    }

    const float kMargin = 10.f;
    float peak_echo_amplitude = max_sample * echo_path_gain * kMargin;
    saturated_echo_ = saturated_echo_ || peak_echo_amplitude > 32000;
  }
}

}  // namespace webrtc
