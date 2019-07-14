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
#include "rtc_base/atomicops.h"
#include "rtc_base/checks.h"
#include "system_wrappers/include/field_trial.h"

namespace webrtc {
namespace {

bool EnableErleResetsAtGainChanges() {
  return !field_trial::IsEnabled("WebRTC-Aec3ResetErleAtGainChangesKillSwitch");
}

bool UseLegacyFilterQualityState() {
  return field_trial::IsEnabled("WebRTC-Aec3FilterQualityStateKillSwitch");
}

bool EnableLegacySaturationBehavior() {
  return field_trial::IsEnabled("WebRTC-Aec3NewSaturationBehaviorKillSwitch");
}

bool UseSuppressionGainLimiter() {
  return field_trial::IsEnabled("WebRTC-Aec3GainLimiterDeactivationKillSwitch");
}
bool EnableErleUpdatesDuringReverb() {
  return !field_trial::IsEnabled(
      "WebRTC-Aec3EnableErleUpdatesDuringReverbKillSwitch");
}

constexpr size_t kBlocksSinceConvergencedFilterInit = 10000;
constexpr size_t kBlocksSinceConsistentEstimateInit = 10000;

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
  if (SaturatedEcho() && use_legacy_saturation_behavior_) {
    return 1.f;
  }

  return absl::nullopt;
}

AecState::AecState(const EchoCanceller3Config& config)
    : data_dumper_(
          new ApmDataDumper(rtc::AtomicOps::Increment(&instance_count_))),
      config_(config),
      use_legacy_saturation_behavior_(EnableLegacySaturationBehavior()),
      enable_erle_resets_at_gain_changes_(EnableErleResetsAtGainChanges()),
      enable_erle_updates_during_reverb_(EnableErleUpdatesDuringReverb()),
      use_legacy_filter_quality_(UseLegacyFilterQualityState()),
      use_suppressor_gain_limiter_(UseSuppressionGainLimiter()),
      initial_state_(config_),
      delay_state_(config_),
      transparent_state_(config_),
      filter_quality_state_(config_),
      legacy_filter_quality_state_(config_),
      legacy_saturation_detector_(config_),
      erl_estimator_(2 * kNumBlocksPerSecond),
      erle_estimator_(2 * kNumBlocksPerSecond,
                      config_.erle.min,
                      config_.erle.max_l,
                      config_.erle.max_h),
      suppression_gain_limiter_(config_),
      filter_analyzer_(config_),
      echo_audibility_(
          config_.echo_audibility.use_stationarity_properties_at_init),
      reverb_model_estimator_(config_) {}

AecState::~AecState() = default;

void AecState::HandleEchoPathChange(
    const EchoPathVariability& echo_path_variability) {
  const auto full_reset = [&]() {
    filter_analyzer_.Reset();
    capture_signal_saturation_ = false;
    strong_not_saturated_render_blocks_ = 0;
    blocks_with_active_render_ = 0;
    if (use_suppressor_gain_limiter_) {
      suppression_gain_limiter_.Reset();
    }
    initial_state_.Reset();
    transparent_state_.Reset();
    if (use_legacy_saturation_behavior_) {
      legacy_saturation_detector_.Reset();
    }
    erle_estimator_.Reset(true);
    erl_estimator_.Reset();
    if (use_legacy_filter_quality_) {
      legacy_filter_quality_state_.Reset();
    } else {
      filter_quality_state_.Reset();
    }
  };

  // TODO(peah): Refine the reset scheme according to the type of gain and
  // delay adjustment.

  if (echo_path_variability.delay_change !=
      EchoPathVariability::DelayAdjustment::kNone) {
    full_reset();
  } else if (enable_erle_resets_at_gain_changes_ &&
             echo_path_variability.gain_change) {
    erle_estimator_.Reset(false);
  }
  subtractor_output_analyzer_.HandleEchoPathChange();
}

void AecState::Update(
    const absl::optional<DelayEstimate>& external_delay,
    const std::vector<std::array<float, kFftLengthBy2Plus1>>&
        adaptive_filter_frequency_response,
    const std::vector<float>& adaptive_filter_impulse_response,
    const RenderBuffer& render_buffer,
    const std::array<float, kFftLengthBy2Plus1>& E2_main,
    const std::array<float, kFftLengthBy2Plus1>& Y2,
    const SubtractorOutput& subtractor_output,
    rtc::ArrayView<const float> y) {
  // Analyze the filter output.
  subtractor_output_analyzer_.Update(subtractor_output);

  // Analyze the properties of the filter.
  filter_analyzer_.Update(adaptive_filter_impulse_response,
                          adaptive_filter_frequency_response, render_buffer);

  // Estimate the direct path delay of the filter.
  delay_state_.Update(filter_analyzer_, external_delay,
                      strong_not_saturated_render_blocks_);

  const std::vector<float>& aligned_render_block =
      render_buffer.Block(-delay_state_.DirectPathFilterDelay())[0];

  // Update render counters.
  const float render_energy = std::inner_product(
      aligned_render_block.begin(), aligned_render_block.end(),
      aligned_render_block.begin(), 0.f);
  const bool active_render =
      render_energy > (config_.render_levels.active_render_limit *
                       config_.render_levels.active_render_limit) *
                          kFftLengthBy2;
  blocks_with_active_render_ += active_render ? 1 : 0;
  strong_not_saturated_render_blocks_ +=
      active_render && !SaturatedCapture() ? 1 : 0;

  if (use_suppressor_gain_limiter_) {
    // Update the limit on the echo suppression after an echo path change to
    // avoid an initial echo burst.
    suppression_gain_limiter_.Update(render_buffer.GetRenderActivity(),
                                     TransparentMode());

    if (subtractor_output_analyzer_.ConvergedFilter()) {
      suppression_gain_limiter_.Deactivate();
    }
  }

  std::array<float, kFftLengthBy2Plus1> X2_reverb;
  render_reverb_.Apply(
      render_buffer.GetSpectrumBuffer(), delay_state_.DirectPathFilterDelay(),
      config_.ep_strength.reverb_based_on_render ? ReverbDecay() : 0.f,
      X2_reverb);

  if (config_.echo_audibility.use_stationary_properties) {
    // Update the echo audibility evaluator.
    echo_audibility_.Update(render_buffer,
                            render_reverb_.GetReverbContributionPowerSpectrum(),
                            delay_state_.DirectPathFilterDelay(),
                            delay_state_.ExternalDelayReported());
  }

  // Update the ERL and ERLE measures.
  if (initial_state_.TransitionTriggered()) {
    erle_estimator_.Reset(false);
  }

  const auto& X2 = render_buffer.Spectrum(delay_state_.DirectPathFilterDelay());
  const auto& X2_input_erle =
      enable_erle_updates_during_reverb_ ? X2_reverb : X2;

  erle_estimator_.Update(X2_input_erle, Y2, E2_main,
                         subtractor_output_analyzer_.ConvergedFilter(),
                         config_.erle.onset_detection);

  erl_estimator_.Update(subtractor_output_analyzer_.ConvergedFilter(), X2, Y2);

  // Detect and flag echo saturation.
  if (use_legacy_saturation_behavior_) {
    legacy_saturation_detector_.Update(aligned_render_block, SaturatedCapture(),
                                       EchoPathGain());
  } else {
    saturation_detector_.Update(aligned_render_block, SaturatedCapture(),
                                UsableLinearEstimate(), subtractor_output,
                                EchoPathGain());
  }

  // Update the decision on whether to use the initial state parameter set.
  initial_state_.Update(active_render, SaturatedCapture());

  // Detect whether the transparent mode should be activated.
  transparent_state_.Update(delay_state_.DirectPathFilterDelay(),
                            filter_analyzer_.Consistent(),
                            subtractor_output_analyzer_.ConvergedFilter(),
                            subtractor_output_analyzer_.DivergedFilter(),
                            active_render, SaturatedCapture());

  // Analyze the quality of the filter.
  if (use_legacy_filter_quality_) {
    legacy_filter_quality_state_.Update(
        SaturatedEcho(), active_render, SaturatedCapture(), TransparentMode(),
        external_delay, subtractor_output_analyzer_.ConvergedFilter(),
        subtractor_output_analyzer_.DivergedFilter());
  } else {
    filter_quality_state_.Update(active_render, TransparentMode(),
                                 SaturatedCapture(),
                                 filter_analyzer_.Consistent(), external_delay,
                                 subtractor_output_analyzer_.ConvergedFilter());
  }

  // Update the reverb estimate.
  const bool stationary_block =
      config_.echo_audibility.use_stationary_properties &&
      echo_audibility_.IsBlockStationary();

  reverb_model_estimator_.Update(filter_analyzer_.GetAdjustedFilter(),
                                 adaptive_filter_frequency_response,
                                 erle_estimator_.GetInstLinearQualityEstimate(),
                                 delay_state_.DirectPathFilterDelay(),
                                 UsableLinearEstimate(), stationary_block);

  erle_estimator_.Dump(data_dumper_);
  reverb_model_estimator_.Dump(data_dumper_.get());
  data_dumper_->DumpRaw("aec3_erl", Erl());
  data_dumper_->DumpRaw("aec3_erl_time_domain", ErlTimeDomain());
  data_dumper_->DumpRaw("aec3_usable_linear_estimate", UsableLinearEstimate());
  data_dumper_->DumpRaw("aec3_transparent_mode", TransparentMode());
  data_dumper_->DumpRaw("aec3_filter_delay", filter_analyzer_.DelayBlocks());

  data_dumper_->DumpRaw("aec3_consistent_filter",
                        filter_analyzer_.Consistent());
  data_dumper_->DumpRaw("aec3_suppression_gain_limit", SuppressionGainLimit());
  data_dumper_->DumpRaw("aec3_initial_state",
                        initial_state_.InitialStateActive());
  data_dumper_->DumpRaw("aec3_capture_saturation", SaturatedCapture());
  data_dumper_->DumpRaw("aec3_echo_saturation", SaturatedEcho());
  data_dumper_->DumpRaw("aec3_converged_filter",
                        subtractor_output_analyzer_.ConvergedFilter());
  data_dumper_->DumpRaw("aec3_diverged_filter",
                        subtractor_output_analyzer_.DivergedFilter());

  data_dumper_->DumpRaw("aec3_external_delay_avaliable",
                        external_delay ? 1 : 0);
  data_dumper_->DumpRaw("aec3_suppresion_gain_limiter_running",
                        IsSuppressionGainLimitActive());
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

AecState::FilterDelay::FilterDelay(const EchoCanceller3Config& config)
    : delay_headroom_blocks_(config.delay.delay_headroom_blocks) {}

void AecState::FilterDelay::Update(
    const FilterAnalyzer& filter_analyzer,
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
    filter_delay_blocks_ = delay_headroom_blocks_;
  } else {
    filter_delay_blocks_ = filter_analyzer.DelayBlocks();
  }
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
                                       bool consistent_filter,
                                       bool converged_filter,
                                       bool diverged_filter,
                                       bool active_render,
                                       bool saturated_capture) {
  ++capture_block_counter_;
  strong_not_saturated_render_blocks_ +=
      active_render && !saturated_capture ? 1 : 0;

  if (consistent_filter && filter_delay_blocks < 5) {
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

  if (converged_filter) {
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

  if (!diverged_filter) {
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
    const EchoCanceller3Config& config) {}

void AecState::FilteringQualityAnalyzer::Reset() {
  usable_linear_estimate_ = false;
  filter_update_blocks_since_reset_ = 0;
}

void AecState::FilteringQualityAnalyzer::Update(
    bool active_render,
    bool transparent_mode,
    bool saturated_capture,
    bool consistent_estimate_,
    const absl::optional<DelayEstimate>& external_delay,
    bool converged_filter) {
  // Update blocks counter.
  const bool filter_update = active_render && !saturated_capture;
  filter_update_blocks_since_reset_ += filter_update ? 1 : 0;
  filter_update_blocks_since_start_ += filter_update ? 1 : 0;

  // Store convergence flag when observed.
  convergence_seen_ = convergence_seen_ || converged_filter;

  // Verify requirements for achieving a decent filter. The requirements for
  // filter adaptation at call startup are more restrictive than after an
  // in-call reset.
  const bool sufficient_data_to_converge_at_startup =
      filter_update_blocks_since_start_ > kNumBlocksPerSecond * 0.4f;
  const bool sufficient_data_to_converge_at_reset =
      sufficient_data_to_converge_at_startup &&
      filter_update_blocks_since_reset_ > kNumBlocksPerSecond * 0.2f;

  // The linear filter can only be used it has had time to converge.
  usable_linear_estimate_ = sufficient_data_to_converge_at_startup &&
                            sufficient_data_to_converge_at_reset;

  // The linear filter can only be used if an external delay or convergence have
  // been identified
  usable_linear_estimate_ =
      usable_linear_estimate_ && (external_delay || convergence_seen_);

  // If transparent mode is on, deactivate usign the linear filter.
  usable_linear_estimate_ = usable_linear_estimate_ && !transparent_mode;
}

AecState::LegacyFilteringQualityAnalyzer::LegacyFilteringQualityAnalyzer(
    const EchoCanceller3Config& config)
    : conservative_initial_phase_(config.filter.conservative_initial_phase),
      required_blocks_for_convergence_(
          kNumBlocksPerSecond * (conservative_initial_phase_ ? 1.5f : 0.8f)),
      linear_and_stable_echo_path_(
          config.echo_removal_control.linear_and_stable_echo_path),
      non_converged_sequence_size_(kBlocksSinceConvergencedFilterInit) {}

void AecState::LegacyFilteringQualityAnalyzer::Reset() {
  usable_linear_estimate_ = false;
  strong_not_saturated_render_blocks_ = 0;
  if (linear_and_stable_echo_path_) {
    recent_convergence_during_activity_ = false;
  }
  diverged_sequence_size_ = 0;
  // TODO(peah): Change to ensure proper triggering of usable filter.
  non_converged_sequence_size_ = 10000;
  recent_convergence_ = true;
}

void AecState::LegacyFilteringQualityAnalyzer::Update(
    bool saturated_echo,
    bool active_render,
    bool saturated_capture,
    bool transparent_mode,
    const absl::optional<DelayEstimate>& external_delay,
    bool converged_filter,
    bool diverged_filter) {
  diverged_sequence_size_ = diverged_filter ? diverged_sequence_size_ + 1 : 0;
  if (diverged_sequence_size_ >= 60) {
    // TODO(peah): Change these lines to ensure proper triggering of usable
    // filter.
    non_converged_sequence_size_ = 10000;
    recent_convergence_ = true;
  }

  if (converged_filter) {
    non_converged_sequence_size_ = 0;
    recent_convergence_ = true;
    active_non_converged_sequence_size_ = 0;
    recent_convergence_during_activity_ = true;
  } else {
    if (++non_converged_sequence_size_ >= 60 * kNumBlocksPerSecond) {
      recent_convergence_ = false;
    }

    if (active_render &&
        ++active_non_converged_sequence_size_ > 60 * kNumBlocksPerSecond) {
      recent_convergence_during_activity_ = false;
    }
  }

  strong_not_saturated_render_blocks_ +=
      active_render && !saturated_capture ? 1 : 0;
  const bool filter_has_had_time_to_converge =
      strong_not_saturated_render_blocks_ > required_blocks_for_convergence_;

  usable_linear_estimate_ = filter_has_had_time_to_converge && external_delay;

  if (!conservative_initial_phase_ && recent_convergence_during_activity_) {
    usable_linear_estimate_ = true;
  }

  if (!linear_and_stable_echo_path_ && !recent_convergence_) {
    usable_linear_estimate_ = false;
  }

  if (saturated_echo || transparent_mode) {
    usable_linear_estimate_ = false;
  }
}

void AecState::SaturationDetector::Update(
    rtc::ArrayView<const float> x,
    bool saturated_capture,
    bool usable_linear_estimate,
    const SubtractorOutput& subtractor_output,
    float echo_path_gain) {
  saturated_echo_ = saturated_capture;
  if (usable_linear_estimate) {
    constexpr float kSaturationThreshold = 20000.f;
    saturated_echo_ =
        saturated_echo_ &&
        (subtractor_output.s_main_max_abs > kSaturationThreshold ||
         subtractor_output.s_shadow_max_abs > kSaturationThreshold);
  } else {
    const float max_sample = fabs(*std::max_element(
        x.begin(), x.end(), [](float a, float b) { return a * a < b * b; }));

    const float kMargin = 10.f;
    float peak_echo_amplitude = max_sample * echo_path_gain * kMargin;
    saturated_echo_ = saturated_echo_ && peak_echo_amplitude > 32000;
  }
}

AecState::LegacySaturationDetector::LegacySaturationDetector(
    const EchoCanceller3Config& config)
    : echo_can_saturate_(config.ep_strength.echo_can_saturate),
      not_saturated_sequence_size_(1000) {}

void AecState::LegacySaturationDetector::Reset() {
  not_saturated_sequence_size_ = 0;
}

void AecState::LegacySaturationDetector::Update(rtc::ArrayView<const float> x,
                                                bool saturated_capture,
                                                float echo_path_gain) {
  if (!echo_can_saturate_) {
    saturated_echo_ = false;
    return;
  }

  RTC_DCHECK_LT(0, x.size());
  if (saturated_capture) {
    const float max_sample = fabs(*std::max_element(
        x.begin(), x.end(), [](float a, float b) { return a * a < b * b; }));

    // Set flag for potential presence of saturated echo
    const float kMargin = 10.f;
    float peak_echo_amplitude = max_sample * echo_path_gain * kMargin;
    if (peak_echo_amplitude > 32000) {
      not_saturated_sequence_size_ = 0;
      saturated_echo_ = true;
      return;
    }
  }

  saturated_echo_ = ++not_saturated_sequence_size_ < 5;
}

}  // namespace webrtc
