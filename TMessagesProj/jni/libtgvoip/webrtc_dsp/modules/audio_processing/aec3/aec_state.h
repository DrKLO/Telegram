/*
 *  Copyright (c) 2017 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#ifndef MODULES_AUDIO_PROCESSING_AEC3_AEC_STATE_H_
#define MODULES_AUDIO_PROCESSING_AEC3_AEC_STATE_H_

#include <stddef.h>
#include <array>
#include <memory>
#include <vector>

#include "absl/types/optional.h"
#include "api/array_view.h"
#include "api/audio/echo_canceller3_config.h"
#include "modules/audio_processing/aec3/aec3_common.h"
#include "modules/audio_processing/aec3/delay_estimate.h"
#include "modules/audio_processing/aec3/echo_audibility.h"
#include "modules/audio_processing/aec3/echo_path_variability.h"
#include "modules/audio_processing/aec3/erl_estimator.h"
#include "modules/audio_processing/aec3/erle_estimator.h"
#include "modules/audio_processing/aec3/filter_analyzer.h"
#include "modules/audio_processing/aec3/render_buffer.h"
#include "modules/audio_processing/aec3/render_reverb_model.h"
#include "modules/audio_processing/aec3/reverb_model_estimator.h"
#include "modules/audio_processing/aec3/subtractor_output.h"
#include "modules/audio_processing/aec3/subtractor_output_analyzer.h"
#include "modules/audio_processing/aec3/suppression_gain_limiter.h"

namespace webrtc {

class ApmDataDumper;

// Handles the state and the conditions for the echo removal functionality.
class AecState {
 public:
  explicit AecState(const EchoCanceller3Config& config);
  ~AecState();

  // Returns whether the echo subtractor can be used to determine the residual
  // echo.
  bool UsableLinearEstimate() const {
    if (use_legacy_filter_quality_) {
      return legacy_filter_quality_state_.LinearFilterUsable();
    }
    return filter_quality_state_.LinearFilterUsable();
  }

  // Returns whether the echo subtractor output should be used as output.
  bool UseLinearFilterOutput() const {
    if (use_legacy_filter_quality_) {
      return legacy_filter_quality_state_.LinearFilterUsable();
    }
    return filter_quality_state_.LinearFilterUsable();
  }

  // Returns the estimated echo path gain.
  float EchoPathGain() const { return filter_analyzer_.Gain(); }

  // Returns whether the render signal is currently active.
  bool ActiveRender() const { return blocks_with_active_render_ > 200; }

  // Returns the appropriate scaling of the residual echo to match the
  // audibility.
  void GetResidualEchoScaling(rtc::ArrayView<float> residual_scaling) const;

  // Returns whether the stationary properties of the signals are used in the
  // aec.
  bool UseStationaryProperties() const {
    return config_.echo_audibility.use_stationary_properties;
  }

  // Returns the ERLE.
  const std::array<float, kFftLengthBy2Plus1>& Erle() const {
    return erle_estimator_.Erle();
  }

  // Returns an offset to apply to the estimation of the residual echo
  // computation. Returning nullopt means that no offset should be used, while
  // any other value will be applied as a multiplier to the estimated residual
  // echo.
  absl::optional<float> ErleUncertainty() const;

  // Returns the fullband ERLE estimate in log2 units.
  float FullBandErleLog2() const { return erle_estimator_.FullbandErleLog2(); }

  // Returns the ERL.
  const std::array<float, kFftLengthBy2Plus1>& Erl() const {
    return erl_estimator_.Erl();
  }

  // Returns the time-domain ERL.
  float ErlTimeDomain() const { return erl_estimator_.ErlTimeDomain(); }

  // Returns the delay estimate based on the linear filter.
  int FilterDelayBlocks() const { return delay_state_.DirectPathFilterDelay(); }

  // Returns whether the capture signal is saturated.
  bool SaturatedCapture() const { return capture_signal_saturation_; }

  // Returns whether the echo signal is saturated.
  bool SaturatedEcho() const {
    return use_legacy_saturation_behavior_
               ? legacy_saturation_detector_.SaturatedEcho()
               : saturation_detector_.SaturatedEcho();
  }

  // Updates the capture signal saturation.
  void UpdateCaptureSaturation(bool capture_signal_saturation) {
    capture_signal_saturation_ = capture_signal_saturation;
  }

  // Returns whether the transparent mode is active
  bool TransparentMode() const { return transparent_state_.Active(); }

  // Takes appropriate action at an echo path change.
  void HandleEchoPathChange(const EchoPathVariability& echo_path_variability);

  // Returns the decay factor for the echo reverberation.
  float ReverbDecay() const { return reverb_model_estimator_.ReverbDecay(); }

  // Return the frequency response of the reverberant echo.
  rtc::ArrayView<const float> GetReverbFrequencyResponse() const {
    return reverb_model_estimator_.GetReverbFrequencyResponse();
  }

  // Returns the upper limit for the echo suppression gain.
  float SuppressionGainLimit() const {
    if (use_suppressor_gain_limiter_) {
      return suppression_gain_limiter_.Limit();
    } else {
      return 1.f;
    }
  }

  // Returns whether the suppression gain limiter is active.
  bool IsSuppressionGainLimitActive() const {
    return suppression_gain_limiter_.IsActive();
  }

  // Returns whether the transition for going out of the initial stated has
  // been triggered.
  bool TransitionTriggered() const {
    return initial_state_.TransitionTriggered();
  }

  // Updates the aec state.
  void Update(const absl::optional<DelayEstimate>& external_delay,
              const std::vector<std::array<float, kFftLengthBy2Plus1>>&
                  adaptive_filter_frequency_response,
              const std::vector<float>& adaptive_filter_impulse_response,
              const RenderBuffer& render_buffer,
              const std::array<float, kFftLengthBy2Plus1>& E2_main,
              const std::array<float, kFftLengthBy2Plus1>& Y2,
              const SubtractorOutput& subtractor_output,
              rtc::ArrayView<const float> y);

  // Returns filter length in blocks.
  int FilterLengthBlocks() const {
    return filter_analyzer_.FilterLengthBlocks();
  }

 private:
  static int instance_count_;
  std::unique_ptr<ApmDataDumper> data_dumper_;
  const EchoCanceller3Config config_;
  const bool use_legacy_saturation_behavior_;
  const bool enable_erle_resets_at_gain_changes_;
  const bool enable_erle_updates_during_reverb_;
  const bool use_legacy_filter_quality_;
  const bool use_suppressor_gain_limiter_;

  // Class for controlling the transition from the intial state, which in turn
  // controls when the filter parameters for the initial state should be used.
  class InitialState {
   public:
    explicit InitialState(const EchoCanceller3Config& config);
    // Resets the state to again begin in the initial state.
    void Reset();

    // Updates the state based on new data.
    void Update(bool active_render, bool saturated_capture);

    // Returns whether the initial state is active or not.
    bool InitialStateActive() const { return initial_state_; }

    // Returns that the transition from the initial state has was started.
    bool TransitionTriggered() const { return transition_triggered_; }

   private:
    const bool conservative_initial_phase_;
    const float initial_state_seconds_;
    bool transition_triggered_ = false;
    bool initial_state_ = true;
    size_t strong_not_saturated_render_blocks_ = 0;
  } initial_state_;

  // Class for choosing the direct-path delay relative to the beginning of the
  // filter, as well as any other data related to the delay used within
  // AecState.
  class FilterDelay {
   public:
    explicit FilterDelay(const EchoCanceller3Config& config);

    // Returns whether an external delay has been reported to the AecState (from
    // the delay estimator).
    bool ExternalDelayReported() const { return external_delay_reported_; }

    // Returns the delay in blocks relative to the beginning of the filter that
    // corresponds to the direct path of the echo.
    int DirectPathFilterDelay() const { return filter_delay_blocks_; }

    // Updates the delay estimates based on new data.
    void Update(const FilterAnalyzer& filter_analyzer,
                const absl::optional<DelayEstimate>& external_delay,
                size_t blocks_with_proper_filter_adaptation);

   private:
    const int delay_headroom_blocks_;
    bool external_delay_reported_ = false;
    int filter_delay_blocks_ = 0;
    absl::optional<DelayEstimate> external_delay_;
  } delay_state_;

  // Class for detecting and toggling the transparent mode which causes the
  // suppressor to apply no suppression.
  class TransparentMode {
   public:
    explicit TransparentMode(const EchoCanceller3Config& config);

    // Returns whether the transparent mode should be active.
    bool Active() const { return transparency_activated_; }

    // Resets the state of the detector.
    void Reset();

    // Updates the detection deciscion based on new data.
    void Update(int filter_delay_blocks,
                bool consistent_filter,
                bool converged_filter,
                bool diverged_filter,
                bool active_render,
                bool saturated_capture);

   private:
    const bool bounded_erl_;
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
  } transparent_state_;

  // Class for analyzing how well the linear filter is, and can be expected to,
  // perform on the current signals. The purpose of this is for using to
  // select the echo suppression functionality as well as the input to the echo
  // suppressor.
  class FilteringQualityAnalyzer {
   public:
    FilteringQualityAnalyzer(const EchoCanceller3Config& config);

    // Returns whether the the linear filter can be used for the echo
    // canceller output.
    bool LinearFilterUsable() const { return usable_linear_estimate_; }

    // Resets the state of the analyzer.
    void Reset();

    // Updates the analysis based on new data.
    void Update(bool active_render,
                bool transparent_mode,
                bool saturated_capture,
                bool consistent_estimate_,
                const absl::optional<DelayEstimate>& external_delay,
                bool converged_filter);

   private:
    bool usable_linear_estimate_ = false;
    size_t filter_update_blocks_since_reset_ = 0;
    size_t filter_update_blocks_since_start_ = 0;
    bool convergence_seen_ = false;
  } filter_quality_state_;

  // Class containing the legacy functionality for analyzing how well the linear
  // filter is, and can be expected to perform on the current signals. The
  // purpose of this is for using to select the echo suppression functionality
  // as well as the input to the echo suppressor.
  class LegacyFilteringQualityAnalyzer {
   public:
    explicit LegacyFilteringQualityAnalyzer(const EchoCanceller3Config& config);

    // Returns whether the the linear filter is can be used for the echo
    // canceller output.
    bool LinearFilterUsable() const { return usable_linear_estimate_; }

    // Resets the state of the analyzer.
    void Reset();

    // Updates the analysis based on new data.
    void Update(bool saturated_echo,
                bool active_render,
                bool saturated_capture,
                bool transparent_mode,
                const absl::optional<DelayEstimate>& external_delay,
                bool converged_filter,
                bool diverged_filter);

   private:
    const bool conservative_initial_phase_;
    const float required_blocks_for_convergence_;
    const bool linear_and_stable_echo_path_;
    bool usable_linear_estimate_ = false;
    size_t strong_not_saturated_render_blocks_ = 0;
    size_t non_converged_sequence_size_;
    size_t diverged_sequence_size_ = 0;
    size_t active_non_converged_sequence_size_ = 0;
    bool recent_convergence_during_activity_ = false;
    bool recent_convergence_ = false;
  } legacy_filter_quality_state_;

  // Class for detecting whether the echo is to be considered to be
  // saturated.
  class SaturationDetector {
   public:
    // Returns whether the echo is to be considered saturated.
    bool SaturatedEcho() const { return saturated_echo_; };

    // Updates the detection decision based on new data.
    void Update(rtc::ArrayView<const float> x,
                bool saturated_capture,
                bool usable_linear_estimate,
                const SubtractorOutput& subtractor_output,
                float echo_path_gain);

   private:
    bool saturated_echo_ = false;
  } saturation_detector_;

  // Legacy class for detecting whether the echo is to be considered to be
  // saturated. This is kept as a fallback solution to use instead of the class
  // SaturationDetector,
  class LegacySaturationDetector {
   public:
    explicit LegacySaturationDetector(const EchoCanceller3Config& config);

    // Returns whether the echo is to be considered saturated.
    bool SaturatedEcho() const { return saturated_echo_; };

    // Resets the state of the detector.
    void Reset();

    // Updates the detection decision based on new data.
    void Update(rtc::ArrayView<const float> x,
                bool saturated_capture,
                float echo_path_gain);

   private:
    const bool echo_can_saturate_;
    size_t not_saturated_sequence_size_;
    bool saturated_echo_ = false;
  } legacy_saturation_detector_;

  ErlEstimator erl_estimator_;
  ErleEstimator erle_estimator_;
  size_t strong_not_saturated_render_blocks_ = 0;
  size_t blocks_with_active_render_ = 0;
  bool capture_signal_saturation_ = false;

  SuppressionGainUpperLimiter suppression_gain_limiter_;
  FilterAnalyzer filter_analyzer_;
  absl::optional<DelayEstimate> external_delay_;
  EchoAudibility echo_audibility_;
  ReverbModelEstimator reverb_model_estimator_;
  RenderReverbModel render_reverb_;
  SubtractorOutputAnalyzer subtractor_output_analyzer_;
};

}  // namespace webrtc

#endif  // MODULES_AUDIO_PROCESSING_AEC3_AEC_STATE_H_
