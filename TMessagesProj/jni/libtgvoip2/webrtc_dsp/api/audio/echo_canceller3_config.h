/*
 *  Copyright (c) 2018 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#ifndef API_AUDIO_ECHO_CANCELLER3_CONFIG_H_
#define API_AUDIO_ECHO_CANCELLER3_CONFIG_H_

#include <stddef.h>  // size_t

#include "rtc_base/system/rtc_export.h"

namespace webrtc {

// Configuration struct for EchoCanceller3
struct RTC_EXPORT EchoCanceller3Config {
  // Checks and updates the config parameters to lie within (mostly) reasonable
  // ranges. Returns true if and only of the config did not need to be changed.
  static bool Validate(EchoCanceller3Config* config);

  EchoCanceller3Config();
  EchoCanceller3Config(const EchoCanceller3Config& e);

  struct Buffering {
    bool use_new_render_buffering = true;
    size_t excess_render_detection_interval_blocks = 250;
    size_t max_allowed_excess_render_blocks = 8;
  } buffering;

  struct Delay {
    Delay();
    Delay(const Delay& e);
    size_t default_delay = 5;
    size_t down_sampling_factor = 4;
    size_t num_filters = 5;
    size_t api_call_jitter_blocks = 26;
    size_t min_echo_path_delay_blocks = 0;
    size_t delay_headroom_blocks = 2;
    size_t hysteresis_limit_1_blocks = 1;
    size_t hysteresis_limit_2_blocks = 1;
    size_t skew_hysteresis_blocks = 3;
    size_t fixed_capture_delay_samples = 0;
    float delay_estimate_smoothing = 0.7f;
    float delay_candidate_detection_threshold = 0.2f;
    struct DelaySelectionThresholds {
      int initial;
      int converged;
    } delay_selection_thresholds = {5, 20};
  } delay;

  struct Filter {
    struct MainConfiguration {
      size_t length_blocks;
      float leakage_converged;
      float leakage_diverged;
      float error_floor;
      float error_ceil;
      float noise_gate;
    };

    struct ShadowConfiguration {
      size_t length_blocks;
      float rate;
      float noise_gate;
    };

    MainConfiguration main = {13, 0.00005f, 0.05f, 0.001f, 2.f, 20075344.f};
    ShadowConfiguration shadow = {13, 0.7f, 20075344.f};

    MainConfiguration main_initial = {12,     0.005f, 0.5f,
                                      0.001f, 2.f,    20075344.f};
    ShadowConfiguration shadow_initial = {12, 0.9f, 20075344.f};

    size_t config_change_duration_blocks = 250;
    float initial_state_seconds = 2.5f;
    bool conservative_initial_phase = false;
    bool enable_shadow_filter_output_usage = true;
  } filter;

  struct Erle {
    float min = 1.f;
    float max_l = 4.f;
    float max_h = 1.5f;
    bool onset_detection = true;
  } erle;

  struct EpStrength {
    float lf = 1.f;
    float mf = 1.f;
    float hf = 1.f;
    float default_len = 0.83f;
    bool reverb_based_on_render = true;
    bool echo_can_saturate = true;
    bool bounded_erl = false;
  } ep_strength;

  struct EchoAudibility {
    float low_render_limit = 4 * 64.f;
    float normal_render_limit = 64.f;
    float floor_power = 2 * 64.f;
    float audibility_threshold_lf = 10;
    float audibility_threshold_mf = 10;
    float audibility_threshold_hf = 10;
    bool use_stationary_properties = false;
    bool use_stationarity_properties_at_init = false;
  } echo_audibility;

  struct RenderLevels {
    float active_render_limit = 100.f;
    float poor_excitation_render_limit = 150.f;
    float poor_excitation_render_limit_ds8 = 20.f;
  } render_levels;

  struct EchoRemovalControl {
    struct GainRampup {
      float initial_gain = 0.0f;
      float first_non_zero_gain = 0.001f;
      int non_zero_gain_blocks = 187;
      int full_gain_blocks = 312;
    } gain_rampup;
    bool has_clock_drift = false;
    bool linear_and_stable_echo_path = false;
  } echo_removal_control;

  struct EchoModel {
    EchoModel();
    EchoModel(const EchoModel& e);
    size_t noise_floor_hold = 50;
    float min_noise_floor_power = 1638400.f;
    float stationary_gate_slope = 10.f;
    float noise_gate_power = 27509.42f;
    float noise_gate_slope = 0.3f;
    size_t render_pre_window_size = 1;
    size_t render_post_window_size = 1;
    size_t render_pre_window_size_init = 10;
    size_t render_post_window_size_init = 10;
    float nonlinear_hold = 1;
    float nonlinear_release = 0.001f;
  } echo_model;

  struct Suppressor {
    Suppressor();
    Suppressor(const Suppressor& e);

    size_t nearend_average_blocks = 4;

    struct MaskingThresholds {
      MaskingThresholds(float enr_transparent,
                        float enr_suppress,
                        float emr_transparent);
      MaskingThresholds(const MaskingThresholds& e);
      float enr_transparent;
      float enr_suppress;
      float emr_transparent;
    };

    struct Tuning {
      Tuning(MaskingThresholds mask_lf,
             MaskingThresholds mask_hf,
             float max_inc_factor,
             float max_dec_factor_lf);
      Tuning(const Tuning& e);
      MaskingThresholds mask_lf;
      MaskingThresholds mask_hf;
      float max_inc_factor;
      float max_dec_factor_lf;
    };

    Tuning normal_tuning = Tuning(MaskingThresholds(.3f, .4f, .3f),
                                  MaskingThresholds(.07f, .1f, .3f),
                                  2.0f,
                                  0.25f);
    Tuning nearend_tuning = Tuning(MaskingThresholds(1.09f, 1.1f, .3f),
                                   MaskingThresholds(.1f, .3f, .3f),
                                   2.0f,
                                   0.25f);

    struct DominantNearendDetection {
      float enr_threshold = 4.f;
      float enr_exit_threshold = .1f;
      float snr_threshold = 30.f;
      int hold_duration = 50;
      int trigger_threshold = 12;
      bool use_during_initial_phase = true;
    } dominant_nearend_detection;

    struct HighBandsSuppression {
      float enr_threshold = 1.f;
      float max_gain_during_echo = 1.f;
    } high_bands_suppression;

    float floor_first_increase = 0.00001f;
    bool enforce_transparent = false;
    bool enforce_empty_higher_bands = false;
  } suppressor;
};
}  // namespace webrtc

#endif  // API_AUDIO_ECHO_CANCELLER3_CONFIG_H_
