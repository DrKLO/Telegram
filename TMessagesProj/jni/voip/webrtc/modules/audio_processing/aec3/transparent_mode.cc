/*
 *  Copyright (c) 2020 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "modules/audio_processing/aec3/transparent_mode.h"

#include "rtc_base/checks.h"
#include "rtc_base/logging.h"
#include "system_wrappers/include/field_trial.h"

namespace webrtc {
namespace {

constexpr size_t kBlocksSinceConvergencedFilterInit = 10000;
constexpr size_t kBlocksSinceConsistentEstimateInit = 10000;

bool DeactivateTransparentMode() {
  return field_trial::IsEnabled("WebRTC-Aec3TransparentModeKillSwitch");
}

bool ActivateTransparentModeHmm() {
  return field_trial::IsEnabled("WebRTC-Aec3TransparentModeHmm");
}

}  // namespace

// Classifier that toggles transparent mode which reduces echo suppression when
// headsets are used.
class TransparentModeImpl : public TransparentMode {
 public:
  bool Active() const override { return transparency_activated_; }

  void Reset() override {
    // Determines if transparent mode is used.
    transparency_activated_ = false;

    // The estimated probability of being transparent mode.
    prob_transparent_state_ = 0.f;
  }

  void Update(int filter_delay_blocks,
              bool any_filter_consistent,
              bool any_filter_converged,
              bool any_coarse_filter_converged,
              bool all_filters_diverged,
              bool active_render,
              bool saturated_capture) override {
    // The classifier is implemented as a Hidden Markov Model (HMM) with two
    // hidden states: "normal" and "transparent". The estimated probabilities of
    // the two states are updated by observing filter convergence during active
    // render. The filters are less likely to be reported as converged when
    // there is no echo present in the microphone signal.

    // The constants have been obtained by observing active_render and
    // any_coarse_filter_converged under varying call scenarios. They
    // have further been hand tuned to prefer normal state during uncertain
    // regions (to avoid echo leaks).

    // The model is only updated during active render.
    if (!active_render)
      return;

    // Probability of switching from one state to the other.
    constexpr float kSwitch = 0.000001f;

    // Probability of observing converged filters in states "normal" and
    // "transparent" during active render.
    constexpr float kConvergedNormal = 0.01f;
    constexpr float kConvergedTransparent = 0.001f;

    // Probability of transitioning to transparent state from normal state and
    // transparent state respectively.
    constexpr float kA[2] = {kSwitch, 1.f - kSwitch};

    // Probability of the two observations (converged filter or not converged
    // filter) in normal state and transparent state respectively.
    constexpr float kB[2][2] = {
        {1.f - kConvergedNormal, kConvergedNormal},
        {1.f - kConvergedTransparent, kConvergedTransparent}};

    // Probability of the two states before the update.
    const float prob_transparent = prob_transparent_state_;
    const float prob_normal = 1.f - prob_transparent;

    // Probability of transitioning to transparent state.
    const float prob_transition_transparent =
        prob_normal * kA[0] + prob_transparent * kA[1];
    const float prob_transition_normal = 1.f - prob_transition_transparent;

    // Observed output.
    const int out = static_cast<int>(any_coarse_filter_converged);

    // Joint probabilites of the observed output and respective states.
    const float prob_joint_normal = prob_transition_normal * kB[0][out];
    const float prob_joint_transparent =
        prob_transition_transparent * kB[1][out];

    // Conditional probability of transparent state and the observed output.
    RTC_DCHECK_GT(prob_joint_normal + prob_joint_transparent, 0.f);
    prob_transparent_state_ =
        prob_joint_transparent / (prob_joint_normal + prob_joint_transparent);

    // Transparent mode is only activated when its state probability is high.
    // Dead zone between activation/deactivation thresholds to avoid switching
    // back and forth.
    if (prob_transparent_state_ > 0.95f) {
      transparency_activated_ = true;
    } else if (prob_transparent_state_ < 0.5f) {
      transparency_activated_ = false;
    }
  }

 private:
  bool transparency_activated_ = false;
  float prob_transparent_state_ = 0.f;
};

// Legacy classifier for toggling transparent mode.
class LegacyTransparentModeImpl : public TransparentMode {
 public:
  explicit LegacyTransparentModeImpl(const EchoCanceller3Config& config)
      : linear_and_stable_echo_path_(
            config.echo_removal_control.linear_and_stable_echo_path),
        active_blocks_since_sane_filter_(kBlocksSinceConsistentEstimateInit),
        non_converged_sequence_size_(kBlocksSinceConvergencedFilterInit) {}

  bool Active() const override { return transparency_activated_; }

  void Reset() override {
    non_converged_sequence_size_ = kBlocksSinceConvergencedFilterInit;
    diverged_sequence_size_ = 0;
    strong_not_saturated_render_blocks_ = 0;
    if (linear_and_stable_echo_path_) {
      recent_convergence_during_activity_ = false;
    }
  }

  void Update(int filter_delay_blocks,
              bool any_filter_consistent,
              bool any_filter_converged,
              bool any_coarse_filter_converged,
              bool all_filters_diverged,
              bool active_render,
              bool saturated_capture) override {
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

    if (finite_erl_recently_detected_) {
      transparency_activated_ = false;
    } else if (sane_filter_recently_seen &&
               recent_convergence_during_activity_) {
      transparency_activated_ = false;
    } else {
      const bool filter_should_have_converged =
          strong_not_saturated_render_blocks_ > 6 * kNumBlocksPerSecond;
      transparency_activated_ = filter_should_have_converged;
    }
  }

 private:
  const bool linear_and_stable_echo_path_;
  size_t capture_block_counter_ = 0;
  bool transparency_activated_ = false;
  size_t active_blocks_since_sane_filter_;
  bool sane_filter_observed_ = false;
  bool finite_erl_recently_detected_ = false;
  size_t non_converged_sequence_size_;
  size_t diverged_sequence_size_ = 0;
  size_t active_non_converged_sequence_size_ = 0;
  size_t num_converged_blocks_ = 0;
  bool recent_convergence_during_activity_ = false;
  size_t strong_not_saturated_render_blocks_ = 0;
};

std::unique_ptr<TransparentMode> TransparentMode::Create(
    const EchoCanceller3Config& config) {
  if (config.ep_strength.bounded_erl || DeactivateTransparentMode()) {
    RTC_LOG(LS_INFO) << "AEC3 Transparent Mode: Disabled";
    return nullptr;
  }
  if (ActivateTransparentModeHmm()) {
    RTC_LOG(LS_INFO) << "AEC3 Transparent Mode: HMM";
    return std::make_unique<TransparentModeImpl>();
  }
  RTC_LOG(LS_INFO) << "AEC3 Transparent Mode: Legacy";
  return std::make_unique<LegacyTransparentModeImpl>(config);
}

}  // namespace webrtc
