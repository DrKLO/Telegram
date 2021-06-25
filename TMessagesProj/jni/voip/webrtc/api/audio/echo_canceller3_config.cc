/*
 *  Copyright (c) 2018 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */
#include "api/audio/echo_canceller3_config.h"

#include <algorithm>
#include <cmath>

#include "rtc_base/checks.h"
#include "rtc_base/numerics/safe_minmax.h"

namespace webrtc {
namespace {
bool Limit(float* value, float min, float max) {
  float clamped = rtc::SafeClamp(*value, min, max);
  clamped = std::isfinite(clamped) ? clamped : min;
  bool res = *value == clamped;
  *value = clamped;
  return res;
}

bool Limit(size_t* value, size_t min, size_t max) {
  size_t clamped = rtc::SafeClamp(*value, min, max);
  bool res = *value == clamped;
  *value = clamped;
  return res;
}

bool Limit(int* value, int min, int max) {
  int clamped = rtc::SafeClamp(*value, min, max);
  bool res = *value == clamped;
  *value = clamped;
  return res;
}

bool FloorLimit(size_t* value, size_t min) {
  size_t clamped = *value >= min ? *value : min;
  bool res = *value == clamped;
  *value = clamped;
  return res;
}

}  // namespace

EchoCanceller3Config::EchoCanceller3Config() = default;
EchoCanceller3Config::EchoCanceller3Config(const EchoCanceller3Config& e) =
    default;
EchoCanceller3Config& EchoCanceller3Config::operator=(
    const EchoCanceller3Config& e) = default;
EchoCanceller3Config::Delay::Delay() = default;
EchoCanceller3Config::Delay::Delay(const EchoCanceller3Config::Delay& e) =
    default;
EchoCanceller3Config::Delay& EchoCanceller3Config::Delay::operator=(
    const Delay& e) = default;

EchoCanceller3Config::EchoModel::EchoModel() = default;
EchoCanceller3Config::EchoModel::EchoModel(
    const EchoCanceller3Config::EchoModel& e) = default;
EchoCanceller3Config::EchoModel& EchoCanceller3Config::EchoModel::operator=(
    const EchoModel& e) = default;

EchoCanceller3Config::Suppressor::Suppressor() = default;
EchoCanceller3Config::Suppressor::Suppressor(
    const EchoCanceller3Config::Suppressor& e) = default;
EchoCanceller3Config::Suppressor& EchoCanceller3Config::Suppressor::operator=(
    const Suppressor& e) = default;

EchoCanceller3Config::Suppressor::MaskingThresholds::MaskingThresholds(
    float enr_transparent,
    float enr_suppress,
    float emr_transparent)
    : enr_transparent(enr_transparent),
      enr_suppress(enr_suppress),
      emr_transparent(emr_transparent) {}
EchoCanceller3Config::Suppressor::MaskingThresholds::MaskingThresholds(
    const EchoCanceller3Config::Suppressor::MaskingThresholds& e) = default;
EchoCanceller3Config::Suppressor::MaskingThresholds&
EchoCanceller3Config::Suppressor::MaskingThresholds::operator=(
    const MaskingThresholds& e) = default;

EchoCanceller3Config::Suppressor::Tuning::Tuning(MaskingThresholds mask_lf,
                                                 MaskingThresholds mask_hf,
                                                 float max_inc_factor,
                                                 float max_dec_factor_lf)
    : mask_lf(mask_lf),
      mask_hf(mask_hf),
      max_inc_factor(max_inc_factor),
      max_dec_factor_lf(max_dec_factor_lf) {}
EchoCanceller3Config::Suppressor::Tuning::Tuning(
    const EchoCanceller3Config::Suppressor::Tuning& e) = default;
EchoCanceller3Config::Suppressor::Tuning&
EchoCanceller3Config::Suppressor::Tuning::operator=(const Tuning& e) = default;

bool EchoCanceller3Config::Validate(EchoCanceller3Config* config) {
  RTC_DCHECK(config);
  EchoCanceller3Config* c = config;
  bool res = true;

  if (c->delay.down_sampling_factor != 4 &&
      c->delay.down_sampling_factor != 8) {
    c->delay.down_sampling_factor = 4;
    res = false;
  }

  res = res & Limit(&c->delay.default_delay, 0, 5000);
  res = res & Limit(&c->delay.num_filters, 0, 5000);
  res = res & Limit(&c->delay.delay_headroom_samples, 0, 5000);
  res = res & Limit(&c->delay.hysteresis_limit_blocks, 0, 5000);
  res = res & Limit(&c->delay.fixed_capture_delay_samples, 0, 5000);
  res = res & Limit(&c->delay.delay_estimate_smoothing, 0.f, 1.f);
  res = res & Limit(&c->delay.delay_candidate_detection_threshold, 0.f, 1.f);
  res = res & Limit(&c->delay.delay_selection_thresholds.initial, 1, 250);
  res = res & Limit(&c->delay.delay_selection_thresholds.converged, 1, 250);

  res = res & FloorLimit(&c->filter.refined.length_blocks, 1);
  res = res & Limit(&c->filter.refined.leakage_converged, 0.f, 1000.f);
  res = res & Limit(&c->filter.refined.leakage_diverged, 0.f, 1000.f);
  res = res & Limit(&c->filter.refined.error_floor, 0.f, 1000.f);
  res = res & Limit(&c->filter.refined.error_ceil, 0.f, 100000000.f);
  res = res & Limit(&c->filter.refined.noise_gate, 0.f, 100000000.f);

  res = res & FloorLimit(&c->filter.refined_initial.length_blocks, 1);
  res = res & Limit(&c->filter.refined_initial.leakage_converged, 0.f, 1000.f);
  res = res & Limit(&c->filter.refined_initial.leakage_diverged, 0.f, 1000.f);
  res = res & Limit(&c->filter.refined_initial.error_floor, 0.f, 1000.f);
  res = res & Limit(&c->filter.refined_initial.error_ceil, 0.f, 100000000.f);
  res = res & Limit(&c->filter.refined_initial.noise_gate, 0.f, 100000000.f);

  if (c->filter.refined.length_blocks <
      c->filter.refined_initial.length_blocks) {
    c->filter.refined_initial.length_blocks = c->filter.refined.length_blocks;
    res = false;
  }

  res = res & FloorLimit(&c->filter.coarse.length_blocks, 1);
  res = res & Limit(&c->filter.coarse.rate, 0.f, 1.f);
  res = res & Limit(&c->filter.coarse.noise_gate, 0.f, 100000000.f);

  res = res & FloorLimit(&c->filter.coarse_initial.length_blocks, 1);
  res = res & Limit(&c->filter.coarse_initial.rate, 0.f, 1.f);
  res = res & Limit(&c->filter.coarse_initial.noise_gate, 0.f, 100000000.f);

  if (c->filter.coarse.length_blocks < c->filter.coarse_initial.length_blocks) {
    c->filter.coarse_initial.length_blocks = c->filter.coarse.length_blocks;
    res = false;
  }

  res = res & Limit(&c->filter.config_change_duration_blocks, 0, 100000);
  res = res & Limit(&c->filter.initial_state_seconds, 0.f, 100.f);
  res = res & Limit(&c->filter.coarse_reset_hangover_blocks, 0, 250000);

  res = res & Limit(&c->erle.min, 1.f, 100000.f);
  res = res & Limit(&c->erle.max_l, 1.f, 100000.f);
  res = res & Limit(&c->erle.max_h, 1.f, 100000.f);
  if (c->erle.min > c->erle.max_l || c->erle.min > c->erle.max_h) {
    c->erle.min = std::min(c->erle.max_l, c->erle.max_h);
    res = false;
  }
  res = res & Limit(&c->erle.num_sections, 1, c->filter.refined.length_blocks);

  res = res & Limit(&c->ep_strength.default_gain, 0.f, 1000000.f);
  res = res & Limit(&c->ep_strength.default_len, -1.f, 1.f);

  res =
      res & Limit(&c->echo_audibility.low_render_limit, 0.f, 32768.f * 32768.f);
  res = res &
        Limit(&c->echo_audibility.normal_render_limit, 0.f, 32768.f * 32768.f);
  res = res & Limit(&c->echo_audibility.floor_power, 0.f, 32768.f * 32768.f);
  res = res & Limit(&c->echo_audibility.audibility_threshold_lf, 0.f,
                    32768.f * 32768.f);
  res = res & Limit(&c->echo_audibility.audibility_threshold_mf, 0.f,
                    32768.f * 32768.f);
  res = res & Limit(&c->echo_audibility.audibility_threshold_hf, 0.f,
                    32768.f * 32768.f);

  res = res &
        Limit(&c->render_levels.active_render_limit, 0.f, 32768.f * 32768.f);
  res = res & Limit(&c->render_levels.poor_excitation_render_limit, 0.f,
                    32768.f * 32768.f);
  res = res & Limit(&c->render_levels.poor_excitation_render_limit_ds8, 0.f,
                    32768.f * 32768.f);

  res = res & Limit(&c->echo_model.noise_floor_hold, 0, 1000);
  res = res & Limit(&c->echo_model.min_noise_floor_power, 0, 2000000.f);
  res = res & Limit(&c->echo_model.stationary_gate_slope, 0, 1000000.f);
  res = res & Limit(&c->echo_model.noise_gate_power, 0, 1000000.f);
  res = res & Limit(&c->echo_model.noise_gate_slope, 0, 1000000.f);
  res = res & Limit(&c->echo_model.render_pre_window_size, 0, 100);
  res = res & Limit(&c->echo_model.render_post_window_size, 0, 100);

  res = res & Limit(&c->comfort_noise.noise_floor_dbfs, -200.f, 0.f);

  res = res & Limit(&c->suppressor.nearend_average_blocks, 1, 5000);

  res = res &
        Limit(&c->suppressor.normal_tuning.mask_lf.enr_transparent, 0.f, 100.f);
  res = res &
        Limit(&c->suppressor.normal_tuning.mask_lf.enr_suppress, 0.f, 100.f);
  res = res &
        Limit(&c->suppressor.normal_tuning.mask_lf.emr_transparent, 0.f, 100.f);
  res = res &
        Limit(&c->suppressor.normal_tuning.mask_hf.enr_transparent, 0.f, 100.f);
  res = res &
        Limit(&c->suppressor.normal_tuning.mask_hf.enr_suppress, 0.f, 100.f);
  res = res &
        Limit(&c->suppressor.normal_tuning.mask_hf.emr_transparent, 0.f, 100.f);
  res = res & Limit(&c->suppressor.normal_tuning.max_inc_factor, 0.f, 100.f);
  res = res & Limit(&c->suppressor.normal_tuning.max_dec_factor_lf, 0.f, 100.f);

  res = res & Limit(&c->suppressor.nearend_tuning.mask_lf.enr_transparent, 0.f,
                    100.f);
  res = res &
        Limit(&c->suppressor.nearend_tuning.mask_lf.enr_suppress, 0.f, 100.f);
  res = res & Limit(&c->suppressor.nearend_tuning.mask_lf.emr_transparent, 0.f,
                    100.f);
  res = res & Limit(&c->suppressor.nearend_tuning.mask_hf.enr_transparent, 0.f,
                    100.f);
  res = res &
        Limit(&c->suppressor.nearend_tuning.mask_hf.enr_suppress, 0.f, 100.f);
  res = res & Limit(&c->suppressor.nearend_tuning.mask_hf.emr_transparent, 0.f,
                    100.f);
  res = res & Limit(&c->suppressor.nearend_tuning.max_inc_factor, 0.f, 100.f);
  res =
      res & Limit(&c->suppressor.nearend_tuning.max_dec_factor_lf, 0.f, 100.f);

  res = res & Limit(&c->suppressor.dominant_nearend_detection.enr_threshold,
                    0.f, 1000000.f);
  res = res & Limit(&c->suppressor.dominant_nearend_detection.snr_threshold,
                    0.f, 1000000.f);
  res = res & Limit(&c->suppressor.dominant_nearend_detection.hold_duration, 0,
                    10000);
  res = res & Limit(&c->suppressor.dominant_nearend_detection.trigger_threshold,
                    0, 10000);

  res = res &
        Limit(&c->suppressor.subband_nearend_detection.nearend_average_blocks,
              1, 1024);
  res =
      res & Limit(&c->suppressor.subband_nearend_detection.subband1.low, 0, 65);
  res = res & Limit(&c->suppressor.subband_nearend_detection.subband1.high,
                    c->suppressor.subband_nearend_detection.subband1.low, 65);
  res =
      res & Limit(&c->suppressor.subband_nearend_detection.subband2.low, 0, 65);
  res = res & Limit(&c->suppressor.subband_nearend_detection.subband2.high,
                    c->suppressor.subband_nearend_detection.subband2.low, 65);
  res = res & Limit(&c->suppressor.subband_nearend_detection.nearend_threshold,
                    0.f, 1.e24f);
  res = res & Limit(&c->suppressor.subband_nearend_detection.snr_threshold, 0.f,
                    1.e24f);

  res = res & Limit(&c->suppressor.high_bands_suppression.enr_threshold, 0.f,
                    1000000.f);
  res = res & Limit(&c->suppressor.high_bands_suppression.max_gain_during_echo,
                    0.f, 1.f);
  res = res & Limit(&c->suppressor.high_bands_suppression
                         .anti_howling_activation_threshold,
                    0.f, 32768.f * 32768.f);
  res = res & Limit(&c->suppressor.high_bands_suppression.anti_howling_gain,
                    0.f, 1.f);

  res = res & Limit(&c->suppressor.floor_first_increase, 0.f, 1000000.f);

  return res;
}
}  // namespace webrtc
