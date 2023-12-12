/*
 *  Copyright (c) 2018 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */
#include "api/audio/echo_canceller3_config_json.h"

#include <stddef.h>

#include <memory>
#include <string>
#include <vector>

#include "rtc_base/checks.h"
#include "rtc_base/logging.h"
#include "rtc_base/strings/json.h"
#include "rtc_base/strings/string_builder.h"

namespace webrtc {
namespace {
void ReadParam(const Json::Value& root, std::string param_name, bool* param) {
  RTC_DCHECK(param);
  bool v;
  if (rtc::GetBoolFromJsonObject(root, param_name, &v)) {
    *param = v;
  }
}

void ReadParam(const Json::Value& root, std::string param_name, size_t* param) {
  RTC_DCHECK(param);
  int v;
  if (rtc::GetIntFromJsonObject(root, param_name, &v) && v >= 0) {
    *param = v;
  }
}

void ReadParam(const Json::Value& root, std::string param_name, int* param) {
  RTC_DCHECK(param);
  int v;
  if (rtc::GetIntFromJsonObject(root, param_name, &v)) {
    *param = v;
  }
}

void ReadParam(const Json::Value& root, std::string param_name, float* param) {
  RTC_DCHECK(param);
  double v;
  if (rtc::GetDoubleFromJsonObject(root, param_name, &v)) {
    *param = static_cast<float>(v);
  }
}

void ReadParam(const Json::Value& root,
               std::string param_name,
               EchoCanceller3Config::Filter::RefinedConfiguration* param) {
  RTC_DCHECK(param);
  Json::Value json_array;
  if (rtc::GetValueFromJsonObject(root, param_name, &json_array)) {
    std::vector<double> v;
    rtc::JsonArrayToDoubleVector(json_array, &v);
    if (v.size() != 6) {
      RTC_LOG(LS_ERROR) << "Incorrect array size for " << param_name;
      return;
    }
    param->length_blocks = static_cast<size_t>(v[0]);
    param->leakage_converged = static_cast<float>(v[1]);
    param->leakage_diverged = static_cast<float>(v[2]);
    param->error_floor = static_cast<float>(v[3]);
    param->error_ceil = static_cast<float>(v[4]);
    param->noise_gate = static_cast<float>(v[5]);
  }
}

void ReadParam(const Json::Value& root,
               std::string param_name,
               EchoCanceller3Config::Filter::CoarseConfiguration* param) {
  RTC_DCHECK(param);
  Json::Value json_array;
  if (rtc::GetValueFromJsonObject(root, param_name, &json_array)) {
    std::vector<double> v;
    rtc::JsonArrayToDoubleVector(json_array, &v);
    if (v.size() != 3) {
      RTC_LOG(LS_ERROR) << "Incorrect array size for " << param_name;
      return;
    }
    param->length_blocks = static_cast<size_t>(v[0]);
    param->rate = static_cast<float>(v[1]);
    param->noise_gate = static_cast<float>(v[2]);
  }
}

void ReadParam(const Json::Value& root,
               std::string param_name,
               EchoCanceller3Config::Delay::AlignmentMixing* param) {
  RTC_DCHECK(param);

  Json::Value subsection;
  if (rtc::GetValueFromJsonObject(root, param_name, &subsection)) {
    ReadParam(subsection, "downmix", &param->downmix);
    ReadParam(subsection, "adaptive_selection", &param->adaptive_selection);
    ReadParam(subsection, "activity_power_threshold",
              &param->activity_power_threshold);
    ReadParam(subsection, "prefer_first_two_channels",
              &param->prefer_first_two_channels);
  }
}

void ReadParam(
    const Json::Value& root,
    std::string param_name,
    EchoCanceller3Config::Suppressor::SubbandNearendDetection::SubbandRegion*
        param) {
  RTC_DCHECK(param);
  Json::Value json_array;
  if (rtc::GetValueFromJsonObject(root, param_name, &json_array)) {
    std::vector<int> v;
    rtc::JsonArrayToIntVector(json_array, &v);
    if (v.size() != 2) {
      RTC_LOG(LS_ERROR) << "Incorrect array size for " << param_name;
      return;
    }
    param->low = static_cast<size_t>(v[0]);
    param->high = static_cast<size_t>(v[1]);
  }
}

void ReadParam(const Json::Value& root,
               std::string param_name,
               EchoCanceller3Config::Suppressor::MaskingThresholds* param) {
  RTC_DCHECK(param);
  Json::Value json_array;
  if (rtc::GetValueFromJsonObject(root, param_name, &json_array)) {
    std::vector<double> v;
    rtc::JsonArrayToDoubleVector(json_array, &v);
    if (v.size() != 3) {
      RTC_LOG(LS_ERROR) << "Incorrect array size for " << param_name;
      return;
    }
    param->enr_transparent = static_cast<float>(v[0]);
    param->enr_suppress = static_cast<float>(v[1]);
    param->emr_transparent = static_cast<float>(v[2]);
  }
}
}  // namespace

void Aec3ConfigFromJsonString(absl::string_view json_string,
                              EchoCanceller3Config* config,
                              bool* parsing_successful) {
  RTC_DCHECK(config);
  RTC_DCHECK(parsing_successful);
  EchoCanceller3Config& cfg = *config;
  cfg = EchoCanceller3Config();
  *parsing_successful = true;

  Json::Value root;
  Json::CharReaderBuilder builder;
  std::string error_message;
  std::unique_ptr<Json::CharReader> reader(builder.newCharReader());
  bool success =
      reader->parse(json_string.data(), json_string.data() + json_string.size(),
                    &root, &error_message);
  if (!success) {
    RTC_LOG(LS_ERROR) << "Incorrect JSON format: " << error_message;
    *parsing_successful = false;
    return;
  }

  Json::Value aec3_root;
  success = rtc::GetValueFromJsonObject(root, "aec3", &aec3_root);
  if (!success) {
    RTC_LOG(LS_ERROR) << "Missing AEC3 config field: " << json_string;
    *parsing_successful = false;
    return;
  }

  Json::Value section;
  if (rtc::GetValueFromJsonObject(aec3_root, "buffering", &section)) {
    ReadParam(section, "excess_render_detection_interval_blocks",
              &cfg.buffering.excess_render_detection_interval_blocks);
    ReadParam(section, "max_allowed_excess_render_blocks",
              &cfg.buffering.max_allowed_excess_render_blocks);
  }

  if (rtc::GetValueFromJsonObject(aec3_root, "delay", &section)) {
    ReadParam(section, "default_delay", &cfg.delay.default_delay);
    ReadParam(section, "down_sampling_factor", &cfg.delay.down_sampling_factor);
    ReadParam(section, "num_filters", &cfg.delay.num_filters);
    ReadParam(section, "delay_headroom_samples",
              &cfg.delay.delay_headroom_samples);
    ReadParam(section, "hysteresis_limit_blocks",
              &cfg.delay.hysteresis_limit_blocks);
    ReadParam(section, "fixed_capture_delay_samples",
              &cfg.delay.fixed_capture_delay_samples);
    ReadParam(section, "delay_estimate_smoothing",
              &cfg.delay.delay_estimate_smoothing);
    ReadParam(section, "delay_estimate_smoothing_delay_found",
              &cfg.delay.delay_estimate_smoothing_delay_found);
    ReadParam(section, "delay_candidate_detection_threshold",
              &cfg.delay.delay_candidate_detection_threshold);

    Json::Value subsection;
    if (rtc::GetValueFromJsonObject(section, "delay_selection_thresholds",
                                    &subsection)) {
      ReadParam(subsection, "initial",
                &cfg.delay.delay_selection_thresholds.initial);
      ReadParam(subsection, "converged",
                &cfg.delay.delay_selection_thresholds.converged);
    }

    ReadParam(section, "use_external_delay_estimator",
              &cfg.delay.use_external_delay_estimator);
    ReadParam(section, "log_warning_on_delay_changes",
              &cfg.delay.log_warning_on_delay_changes);

    ReadParam(section, "render_alignment_mixing",
              &cfg.delay.render_alignment_mixing);
    ReadParam(section, "capture_alignment_mixing",
              &cfg.delay.capture_alignment_mixing);
    ReadParam(section, "detect_pre_echo", &cfg.delay.detect_pre_echo);
  }

  if (rtc::GetValueFromJsonObject(aec3_root, "filter", &section)) {
    ReadParam(section, "refined", &cfg.filter.refined);
    ReadParam(section, "coarse", &cfg.filter.coarse);
    ReadParam(section, "refined_initial", &cfg.filter.refined_initial);
    ReadParam(section, "coarse_initial", &cfg.filter.coarse_initial);
    ReadParam(section, "config_change_duration_blocks",
              &cfg.filter.config_change_duration_blocks);
    ReadParam(section, "initial_state_seconds",
              &cfg.filter.initial_state_seconds);
    ReadParam(section, "coarse_reset_hangover_blocks",
              &cfg.filter.coarse_reset_hangover_blocks);
    ReadParam(section, "conservative_initial_phase",
              &cfg.filter.conservative_initial_phase);
    ReadParam(section, "enable_coarse_filter_output_usage",
              &cfg.filter.enable_coarse_filter_output_usage);
    ReadParam(section, "use_linear_filter", &cfg.filter.use_linear_filter);
    ReadParam(section, "high_pass_filter_echo_reference",
              &cfg.filter.high_pass_filter_echo_reference);
    ReadParam(section, "export_linear_aec_output",
              &cfg.filter.export_linear_aec_output);
  }

  if (rtc::GetValueFromJsonObject(aec3_root, "erle", &section)) {
    ReadParam(section, "min", &cfg.erle.min);
    ReadParam(section, "max_l", &cfg.erle.max_l);
    ReadParam(section, "max_h", &cfg.erle.max_h);
    ReadParam(section, "onset_detection", &cfg.erle.onset_detection);
    ReadParam(section, "num_sections", &cfg.erle.num_sections);
    ReadParam(section, "clamp_quality_estimate_to_zero",
              &cfg.erle.clamp_quality_estimate_to_zero);
    ReadParam(section, "clamp_quality_estimate_to_one",
              &cfg.erle.clamp_quality_estimate_to_one);
  }

  if (rtc::GetValueFromJsonObject(aec3_root, "ep_strength", &section)) {
    ReadParam(section, "default_gain", &cfg.ep_strength.default_gain);
    ReadParam(section, "default_len", &cfg.ep_strength.default_len);
    ReadParam(section, "nearend_len", &cfg.ep_strength.nearend_len);
    ReadParam(section, "echo_can_saturate", &cfg.ep_strength.echo_can_saturate);
    ReadParam(section, "bounded_erl", &cfg.ep_strength.bounded_erl);
    ReadParam(section, "erle_onset_compensation_in_dominant_nearend",
              &cfg.ep_strength.erle_onset_compensation_in_dominant_nearend);
    ReadParam(section, "use_conservative_tail_frequency_response",
              &cfg.ep_strength.use_conservative_tail_frequency_response);
  }

  if (rtc::GetValueFromJsonObject(aec3_root, "echo_audibility", &section)) {
    ReadParam(section, "low_render_limit",
              &cfg.echo_audibility.low_render_limit);
    ReadParam(section, "normal_render_limit",
              &cfg.echo_audibility.normal_render_limit);

    ReadParam(section, "floor_power", &cfg.echo_audibility.floor_power);
    ReadParam(section, "audibility_threshold_lf",
              &cfg.echo_audibility.audibility_threshold_lf);
    ReadParam(section, "audibility_threshold_mf",
              &cfg.echo_audibility.audibility_threshold_mf);
    ReadParam(section, "audibility_threshold_hf",
              &cfg.echo_audibility.audibility_threshold_hf);
    ReadParam(section, "use_stationarity_properties",
              &cfg.echo_audibility.use_stationarity_properties);
    ReadParam(section, "use_stationarity_properties_at_init",
              &cfg.echo_audibility.use_stationarity_properties_at_init);
  }

  if (rtc::GetValueFromJsonObject(aec3_root, "render_levels", &section)) {
    ReadParam(section, "active_render_limit",
              &cfg.render_levels.active_render_limit);
    ReadParam(section, "poor_excitation_render_limit",
              &cfg.render_levels.poor_excitation_render_limit);
    ReadParam(section, "poor_excitation_render_limit_ds8",
              &cfg.render_levels.poor_excitation_render_limit_ds8);
    ReadParam(section, "render_power_gain_db",
              &cfg.render_levels.render_power_gain_db);
  }

  if (rtc::GetValueFromJsonObject(aec3_root, "echo_removal_control",
                                  &section)) {
    ReadParam(section, "has_clock_drift",
              &cfg.echo_removal_control.has_clock_drift);
    ReadParam(section, "linear_and_stable_echo_path",
              &cfg.echo_removal_control.linear_and_stable_echo_path);
  }

  if (rtc::GetValueFromJsonObject(aec3_root, "echo_model", &section)) {
    Json::Value subsection;
    ReadParam(section, "noise_floor_hold", &cfg.echo_model.noise_floor_hold);
    ReadParam(section, "min_noise_floor_power",
              &cfg.echo_model.min_noise_floor_power);
    ReadParam(section, "stationary_gate_slope",
              &cfg.echo_model.stationary_gate_slope);
    ReadParam(section, "noise_gate_power", &cfg.echo_model.noise_gate_power);
    ReadParam(section, "noise_gate_slope", &cfg.echo_model.noise_gate_slope);
    ReadParam(section, "render_pre_window_size",
              &cfg.echo_model.render_pre_window_size);
    ReadParam(section, "render_post_window_size",
              &cfg.echo_model.render_post_window_size);
    ReadParam(section, "model_reverb_in_nonlinear_mode",
              &cfg.echo_model.model_reverb_in_nonlinear_mode);
  }

  if (rtc::GetValueFromJsonObject(aec3_root, "comfort_noise", &section)) {
    ReadParam(section, "noise_floor_dbfs", &cfg.comfort_noise.noise_floor_dbfs);
  }

  Json::Value subsection;
  if (rtc::GetValueFromJsonObject(aec3_root, "suppressor", &section)) {
    ReadParam(section, "nearend_average_blocks",
              &cfg.suppressor.nearend_average_blocks);

    if (rtc::GetValueFromJsonObject(section, "normal_tuning", &subsection)) {
      ReadParam(subsection, "mask_lf", &cfg.suppressor.normal_tuning.mask_lf);
      ReadParam(subsection, "mask_hf", &cfg.suppressor.normal_tuning.mask_hf);
      ReadParam(subsection, "max_inc_factor",
                &cfg.suppressor.normal_tuning.max_inc_factor);
      ReadParam(subsection, "max_dec_factor_lf",
                &cfg.suppressor.normal_tuning.max_dec_factor_lf);
    }

    if (rtc::GetValueFromJsonObject(section, "nearend_tuning", &subsection)) {
      ReadParam(subsection, "mask_lf", &cfg.suppressor.nearend_tuning.mask_lf);
      ReadParam(subsection, "mask_hf", &cfg.suppressor.nearend_tuning.mask_hf);
      ReadParam(subsection, "max_inc_factor",
                &cfg.suppressor.nearend_tuning.max_inc_factor);
      ReadParam(subsection, "max_dec_factor_lf",
                &cfg.suppressor.nearend_tuning.max_dec_factor_lf);
    }

    ReadParam(section, "lf_smoothing_during_initial_phase",
              &cfg.suppressor.lf_smoothing_during_initial_phase);
    ReadParam(section, "last_permanent_lf_smoothing_band",
              &cfg.suppressor.last_permanent_lf_smoothing_band);
    ReadParam(section, "last_lf_smoothing_band",
              &cfg.suppressor.last_lf_smoothing_band);
    ReadParam(section, "last_lf_band", &cfg.suppressor.last_lf_band);
    ReadParam(section, "first_hf_band", &cfg.suppressor.first_hf_band);

    if (rtc::GetValueFromJsonObject(section, "dominant_nearend_detection",
                                    &subsection)) {
      ReadParam(subsection, "enr_threshold",
                &cfg.suppressor.dominant_nearend_detection.enr_threshold);
      ReadParam(subsection, "enr_exit_threshold",
                &cfg.suppressor.dominant_nearend_detection.enr_exit_threshold);
      ReadParam(subsection, "snr_threshold",
                &cfg.suppressor.dominant_nearend_detection.snr_threshold);
      ReadParam(subsection, "hold_duration",
                &cfg.suppressor.dominant_nearend_detection.hold_duration);
      ReadParam(subsection, "trigger_threshold",
                &cfg.suppressor.dominant_nearend_detection.trigger_threshold);
      ReadParam(
          subsection, "use_during_initial_phase",
          &cfg.suppressor.dominant_nearend_detection.use_during_initial_phase);
      ReadParam(subsection, "use_unbounded_echo_spectrum",
                &cfg.suppressor.dominant_nearend_detection
                     .use_unbounded_echo_spectrum);
    }

    if (rtc::GetValueFromJsonObject(section, "subband_nearend_detection",
                                    &subsection)) {
      ReadParam(
          subsection, "nearend_average_blocks",
          &cfg.suppressor.subband_nearend_detection.nearend_average_blocks);
      ReadParam(subsection, "subband1",
                &cfg.suppressor.subband_nearend_detection.subband1);
      ReadParam(subsection, "subband2",
                &cfg.suppressor.subband_nearend_detection.subband2);
      ReadParam(subsection, "nearend_threshold",
                &cfg.suppressor.subband_nearend_detection.nearend_threshold);
      ReadParam(subsection, "snr_threshold",
                &cfg.suppressor.subband_nearend_detection.snr_threshold);
    }

    ReadParam(section, "use_subband_nearend_detection",
              &cfg.suppressor.use_subband_nearend_detection);

    if (rtc::GetValueFromJsonObject(section, "high_bands_suppression",
                                    &subsection)) {
      ReadParam(subsection, "enr_threshold",
                &cfg.suppressor.high_bands_suppression.enr_threshold);
      ReadParam(subsection, "max_gain_during_echo",
                &cfg.suppressor.high_bands_suppression.max_gain_during_echo);
      ReadParam(subsection, "anti_howling_activation_threshold",
                &cfg.suppressor.high_bands_suppression
                     .anti_howling_activation_threshold);
      ReadParam(subsection, "anti_howling_gain",
                &cfg.suppressor.high_bands_suppression.anti_howling_gain);
    }

    ReadParam(section, "floor_first_increase",
              &cfg.suppressor.floor_first_increase);
    ReadParam(section, "conservative_hf_suppression",
              &cfg.suppressor.conservative_hf_suppression);
  }

  if (rtc::GetValueFromJsonObject(aec3_root, "multi_channel", &section)) {
    ReadParam(section, "detect_stereo_content",
              &cfg.multi_channel.detect_stereo_content);
    ReadParam(section, "stereo_detection_threshold",
              &cfg.multi_channel.stereo_detection_threshold);
    ReadParam(section, "stereo_detection_timeout_threshold_seconds",
              &cfg.multi_channel.stereo_detection_timeout_threshold_seconds);
    ReadParam(section, "stereo_detection_hysteresis_seconds",
              &cfg.multi_channel.stereo_detection_hysteresis_seconds);
  }
}

EchoCanceller3Config Aec3ConfigFromJsonString(absl::string_view json_string) {
  EchoCanceller3Config cfg;
  bool not_used;
  Aec3ConfigFromJsonString(json_string, &cfg, &not_used);
  return cfg;
}

std::string Aec3ConfigToJsonString(const EchoCanceller3Config& config) {
  rtc::StringBuilder ost;
  ost << "{";
  ost << "\"aec3\": {";
  ost << "\"buffering\": {";
  ost << "\"excess_render_detection_interval_blocks\": "
      << config.buffering.excess_render_detection_interval_blocks << ",";
  ost << "\"max_allowed_excess_render_blocks\": "
      << config.buffering.max_allowed_excess_render_blocks;
  ost << "},";

  ost << "\"delay\": {";
  ost << "\"default_delay\": " << config.delay.default_delay << ",";
  ost << "\"down_sampling_factor\": " << config.delay.down_sampling_factor
      << ",";
  ost << "\"num_filters\": " << config.delay.num_filters << ",";
  ost << "\"delay_headroom_samples\": " << config.delay.delay_headroom_samples
      << ",";
  ost << "\"hysteresis_limit_blocks\": " << config.delay.hysteresis_limit_blocks
      << ",";
  ost << "\"fixed_capture_delay_samples\": "
      << config.delay.fixed_capture_delay_samples << ",";
  ost << "\"delay_estimate_smoothing\": "
      << config.delay.delay_estimate_smoothing << ",";
  ost << "\"delay_estimate_smoothing_delay_found\": "
      << config.delay.delay_estimate_smoothing_delay_found << ",";
  ost << "\"delay_candidate_detection_threshold\": "
      << config.delay.delay_candidate_detection_threshold << ",";

  ost << "\"delay_selection_thresholds\": {";
  ost << "\"initial\": " << config.delay.delay_selection_thresholds.initial
      << ",";
  ost << "\"converged\": " << config.delay.delay_selection_thresholds.converged;
  ost << "},";

  ost << "\"use_external_delay_estimator\": "
      << (config.delay.use_external_delay_estimator ? "true" : "false") << ",";
  ost << "\"log_warning_on_delay_changes\": "
      << (config.delay.log_warning_on_delay_changes ? "true" : "false") << ",";

  ost << "\"render_alignment_mixing\": {";
  ost << "\"downmix\": "
      << (config.delay.render_alignment_mixing.downmix ? "true" : "false")
      << ",";
  ost << "\"adaptive_selection\": "
      << (config.delay.render_alignment_mixing.adaptive_selection ? "true"
                                                                  : "false")
      << ",";
  ost << "\"activity_power_threshold\": "
      << config.delay.render_alignment_mixing.activity_power_threshold << ",";
  ost << "\"prefer_first_two_channels\": "
      << (config.delay.render_alignment_mixing.prefer_first_two_channels
              ? "true"
              : "false");
  ost << "},";

  ost << "\"capture_alignment_mixing\": {";
  ost << "\"downmix\": "
      << (config.delay.capture_alignment_mixing.downmix ? "true" : "false")
      << ",";
  ost << "\"adaptive_selection\": "
      << (config.delay.capture_alignment_mixing.adaptive_selection ? "true"
                                                                   : "false")
      << ",";
  ost << "\"activity_power_threshold\": "
      << config.delay.capture_alignment_mixing.activity_power_threshold << ",";
  ost << "\"prefer_first_two_channels\": "
      << (config.delay.capture_alignment_mixing.prefer_first_two_channels
              ? "true"
              : "false");
  ost << "},";
  ost << "\"detect_pre_echo\": "
      << (config.delay.detect_pre_echo ? "true" : "false");
  ost << "},";

  ost << "\"filter\": {";

  ost << "\"refined\": [";
  ost << config.filter.refined.length_blocks << ",";
  ost << config.filter.refined.leakage_converged << ",";
  ost << config.filter.refined.leakage_diverged << ",";
  ost << config.filter.refined.error_floor << ",";
  ost << config.filter.refined.error_ceil << ",";
  ost << config.filter.refined.noise_gate;
  ost << "],";

  ost << "\"coarse\": [";
  ost << config.filter.coarse.length_blocks << ",";
  ost << config.filter.coarse.rate << ",";
  ost << config.filter.coarse.noise_gate;
  ost << "],";

  ost << "\"refined_initial\": [";
  ost << config.filter.refined_initial.length_blocks << ",";
  ost << config.filter.refined_initial.leakage_converged << ",";
  ost << config.filter.refined_initial.leakage_diverged << ",";
  ost << config.filter.refined_initial.error_floor << ",";
  ost << config.filter.refined_initial.error_ceil << ",";
  ost << config.filter.refined_initial.noise_gate;
  ost << "],";

  ost << "\"coarse_initial\": [";
  ost << config.filter.coarse_initial.length_blocks << ",";
  ost << config.filter.coarse_initial.rate << ",";
  ost << config.filter.coarse_initial.noise_gate;
  ost << "],";

  ost << "\"config_change_duration_blocks\": "
      << config.filter.config_change_duration_blocks << ",";
  ost << "\"initial_state_seconds\": " << config.filter.initial_state_seconds
      << ",";
  ost << "\"coarse_reset_hangover_blocks\": "
      << config.filter.coarse_reset_hangover_blocks << ",";
  ost << "\"conservative_initial_phase\": "
      << (config.filter.conservative_initial_phase ? "true" : "false") << ",";
  ost << "\"enable_coarse_filter_output_usage\": "
      << (config.filter.enable_coarse_filter_output_usage ? "true" : "false")
      << ",";
  ost << "\"use_linear_filter\": "
      << (config.filter.use_linear_filter ? "true" : "false") << ",";
  ost << "\"high_pass_filter_echo_reference\": "
      << (config.filter.high_pass_filter_echo_reference ? "true" : "false")
      << ",";
  ost << "\"export_linear_aec_output\": "
      << (config.filter.export_linear_aec_output ? "true" : "false");

  ost << "},";

  ost << "\"erle\": {";
  ost << "\"min\": " << config.erle.min << ",";
  ost << "\"max_l\": " << config.erle.max_l << ",";
  ost << "\"max_h\": " << config.erle.max_h << ",";
  ost << "\"onset_detection\": "
      << (config.erle.onset_detection ? "true" : "false") << ",";
  ost << "\"num_sections\": " << config.erle.num_sections << ",";
  ost << "\"clamp_quality_estimate_to_zero\": "
      << (config.erle.clamp_quality_estimate_to_zero ? "true" : "false") << ",";
  ost << "\"clamp_quality_estimate_to_one\": "
      << (config.erle.clamp_quality_estimate_to_one ? "true" : "false");
  ost << "},";

  ost << "\"ep_strength\": {";
  ost << "\"default_gain\": " << config.ep_strength.default_gain << ",";
  ost << "\"default_len\": " << config.ep_strength.default_len << ",";
  ost << "\"nearend_len\": " << config.ep_strength.nearend_len << ",";
  ost << "\"echo_can_saturate\": "
      << (config.ep_strength.echo_can_saturate ? "true" : "false") << ",";
  ost << "\"bounded_erl\": "
      << (config.ep_strength.bounded_erl ? "true" : "false") << ",";
  ost << "\"erle_onset_compensation_in_dominant_nearend\": "
      << (config.ep_strength.erle_onset_compensation_in_dominant_nearend
              ? "true"
              : "false")
      << ",";
  ost << "\"use_conservative_tail_frequency_response\": "
      << (config.ep_strength.use_conservative_tail_frequency_response
              ? "true"
              : "false");
  ost << "},";

  ost << "\"echo_audibility\": {";
  ost << "\"low_render_limit\": " << config.echo_audibility.low_render_limit
      << ",";
  ost << "\"normal_render_limit\": "
      << config.echo_audibility.normal_render_limit << ",";
  ost << "\"floor_power\": " << config.echo_audibility.floor_power << ",";
  ost << "\"audibility_threshold_lf\": "
      << config.echo_audibility.audibility_threshold_lf << ",";
  ost << "\"audibility_threshold_mf\": "
      << config.echo_audibility.audibility_threshold_mf << ",";
  ost << "\"audibility_threshold_hf\": "
      << config.echo_audibility.audibility_threshold_hf << ",";
  ost << "\"use_stationarity_properties\": "
      << (config.echo_audibility.use_stationarity_properties ? "true" : "false")
      << ",";
  ost << "\"use_stationarity_properties_at_init\": "
      << (config.echo_audibility.use_stationarity_properties_at_init ? "true"
                                                                     : "false");
  ost << "},";

  ost << "\"render_levels\": {";
  ost << "\"active_render_limit\": " << config.render_levels.active_render_limit
      << ",";
  ost << "\"poor_excitation_render_limit\": "
      << config.render_levels.poor_excitation_render_limit << ",";
  ost << "\"poor_excitation_render_limit_ds8\": "
      << config.render_levels.poor_excitation_render_limit_ds8 << ",";
  ost << "\"render_power_gain_db\": "
      << config.render_levels.render_power_gain_db;
  ost << "},";

  ost << "\"echo_removal_control\": {";
  ost << "\"has_clock_drift\": "
      << (config.echo_removal_control.has_clock_drift ? "true" : "false")
      << ",";
  ost << "\"linear_and_stable_echo_path\": "
      << (config.echo_removal_control.linear_and_stable_echo_path ? "true"
                                                                  : "false");

  ost << "},";

  ost << "\"echo_model\": {";
  ost << "\"noise_floor_hold\": " << config.echo_model.noise_floor_hold << ",";
  ost << "\"min_noise_floor_power\": "
      << config.echo_model.min_noise_floor_power << ",";
  ost << "\"stationary_gate_slope\": "
      << config.echo_model.stationary_gate_slope << ",";
  ost << "\"noise_gate_power\": " << config.echo_model.noise_gate_power << ",";
  ost << "\"noise_gate_slope\": " << config.echo_model.noise_gate_slope << ",";
  ost << "\"render_pre_window_size\": "
      << config.echo_model.render_pre_window_size << ",";
  ost << "\"render_post_window_size\": "
      << config.echo_model.render_post_window_size << ",";
  ost << "\"model_reverb_in_nonlinear_mode\": "
      << (config.echo_model.model_reverb_in_nonlinear_mode ? "true" : "false");
  ost << "},";

  ost << "\"comfort_noise\": {";
  ost << "\"noise_floor_dbfs\": " << config.comfort_noise.noise_floor_dbfs;
  ost << "},";

  ost << "\"suppressor\": {";
  ost << "\"nearend_average_blocks\": "
      << config.suppressor.nearend_average_blocks << ",";
  ost << "\"normal_tuning\": {";
  ost << "\"mask_lf\": [";
  ost << config.suppressor.normal_tuning.mask_lf.enr_transparent << ",";
  ost << config.suppressor.normal_tuning.mask_lf.enr_suppress << ",";
  ost << config.suppressor.normal_tuning.mask_lf.emr_transparent;
  ost << "],";
  ost << "\"mask_hf\": [";
  ost << config.suppressor.normal_tuning.mask_hf.enr_transparent << ",";
  ost << config.suppressor.normal_tuning.mask_hf.enr_suppress << ",";
  ost << config.suppressor.normal_tuning.mask_hf.emr_transparent;
  ost << "],";
  ost << "\"max_inc_factor\": "
      << config.suppressor.normal_tuning.max_inc_factor << ",";
  ost << "\"max_dec_factor_lf\": "
      << config.suppressor.normal_tuning.max_dec_factor_lf;
  ost << "},";
  ost << "\"nearend_tuning\": {";
  ost << "\"mask_lf\": [";
  ost << config.suppressor.nearend_tuning.mask_lf.enr_transparent << ",";
  ost << config.suppressor.nearend_tuning.mask_lf.enr_suppress << ",";
  ost << config.suppressor.nearend_tuning.mask_lf.emr_transparent;
  ost << "],";
  ost << "\"mask_hf\": [";
  ost << config.suppressor.nearend_tuning.mask_hf.enr_transparent << ",";
  ost << config.suppressor.nearend_tuning.mask_hf.enr_suppress << ",";
  ost << config.suppressor.nearend_tuning.mask_hf.emr_transparent;
  ost << "],";
  ost << "\"max_inc_factor\": "
      << config.suppressor.nearend_tuning.max_inc_factor << ",";
  ost << "\"max_dec_factor_lf\": "
      << config.suppressor.nearend_tuning.max_dec_factor_lf;
  ost << "},";
  ost << "\"lf_smoothing_during_initial_phase\": "
      << (config.suppressor.lf_smoothing_during_initial_phase ? "true"
                                                              : "false")
      << ",";
  ost << "\"last_permanent_lf_smoothing_band\": "
      << config.suppressor.last_permanent_lf_smoothing_band << ",";
  ost << "\"last_lf_smoothing_band\": "
      << config.suppressor.last_lf_smoothing_band << ",";
  ost << "\"last_lf_band\": " << config.suppressor.last_lf_band << ",";
  ost << "\"first_hf_band\": " << config.suppressor.first_hf_band << ",";
  {
    const auto& dnd = config.suppressor.dominant_nearend_detection;
    ost << "\"dominant_nearend_detection\": {";
    ost << "\"enr_threshold\": " << dnd.enr_threshold << ",";
    ost << "\"enr_exit_threshold\": " << dnd.enr_exit_threshold << ",";
    ost << "\"snr_threshold\": " << dnd.snr_threshold << ",";
    ost << "\"hold_duration\": " << dnd.hold_duration << ",";
    ost << "\"trigger_threshold\": " << dnd.trigger_threshold << ",";
    ost << "\"use_during_initial_phase\": " << dnd.use_during_initial_phase
        << ",";
    ost << "\"use_unbounded_echo_spectrum\": "
        << dnd.use_unbounded_echo_spectrum;
    ost << "},";
  }
  ost << "\"subband_nearend_detection\": {";
  ost << "\"nearend_average_blocks\": "
      << config.suppressor.subband_nearend_detection.nearend_average_blocks
      << ",";
  ost << "\"subband1\": [";
  ost << config.suppressor.subband_nearend_detection.subband1.low << ",";
  ost << config.suppressor.subband_nearend_detection.subband1.high;
  ost << "],";
  ost << "\"subband2\": [";
  ost << config.suppressor.subband_nearend_detection.subband2.low << ",";
  ost << config.suppressor.subband_nearend_detection.subband2.high;
  ost << "],";
  ost << "\"nearend_threshold\": "
      << config.suppressor.subband_nearend_detection.nearend_threshold << ",";
  ost << "\"snr_threshold\": "
      << config.suppressor.subband_nearend_detection.snr_threshold;
  ost << "},";
  ost << "\"use_subband_nearend_detection\": "
      << config.suppressor.use_subband_nearend_detection << ",";
  ost << "\"high_bands_suppression\": {";
  ost << "\"enr_threshold\": "
      << config.suppressor.high_bands_suppression.enr_threshold << ",";
  ost << "\"max_gain_during_echo\": "
      << config.suppressor.high_bands_suppression.max_gain_during_echo << ",";
  ost << "\"anti_howling_activation_threshold\": "
      << config.suppressor.high_bands_suppression
             .anti_howling_activation_threshold
      << ",";
  ost << "\"anti_howling_gain\": "
      << config.suppressor.high_bands_suppression.anti_howling_gain;
  ost << "},";
  ost << "\"floor_first_increase\": " << config.suppressor.floor_first_increase
      << ",";
  ost << "\"conservative_hf_suppression\": "
      << config.suppressor.conservative_hf_suppression;
  ost << "},";

  ost << "\"multi_channel\": {";
  ost << "\"detect_stereo_content\": "
      << (config.multi_channel.detect_stereo_content ? "true" : "false") << ",";
  ost << "\"stereo_detection_threshold\": "
      << config.multi_channel.stereo_detection_threshold << ",";
  ost << "\"stereo_detection_timeout_threshold_seconds\": "
      << config.multi_channel.stereo_detection_timeout_threshold_seconds << ",";
  ost << "\"stereo_detection_hysteresis_seconds\": "
      << config.multi_channel.stereo_detection_hysteresis_seconds;
  ost << "}";

  ost << "}";
  ost << "}";

  return ost.Release();
}
}  // namespace webrtc
