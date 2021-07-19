/*
 *  Copyright (c) 2018 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "rtc_base/experiments/alr_experiment.h"

#include <inttypes.h>
#include <stdio.h>

#include <string>

#include "api/transport/field_trial_based_config.h"
#include "rtc_base/logging.h"

namespace webrtc {

const char AlrExperimentSettings::kScreenshareProbingBweExperimentName[] =
    "WebRTC-ProbingScreenshareBwe";
const char AlrExperimentSettings::kStrictPacingAndProbingExperimentName[] =
    "WebRTC-StrictPacingAndProbing";
const char kDefaultProbingScreenshareBweSettings[] = "1.0,2875,80,40,-60,3";

bool AlrExperimentSettings::MaxOneFieldTrialEnabled() {
  return AlrExperimentSettings::MaxOneFieldTrialEnabled(
      FieldTrialBasedConfig());
}

bool AlrExperimentSettings::MaxOneFieldTrialEnabled(
    const WebRtcKeyValueConfig& key_value_config) {
  return key_value_config.Lookup(kStrictPacingAndProbingExperimentName)
             .empty() ||
         key_value_config.Lookup(kScreenshareProbingBweExperimentName).empty();
}

absl::optional<AlrExperimentSettings>
AlrExperimentSettings::CreateFromFieldTrial(const char* experiment_name) {
  return AlrExperimentSettings::CreateFromFieldTrial(FieldTrialBasedConfig(),
                                                     experiment_name);
}

absl::optional<AlrExperimentSettings>
AlrExperimentSettings::CreateFromFieldTrial(
    const WebRtcKeyValueConfig& key_value_config,
    const char* experiment_name) {
  absl::optional<AlrExperimentSettings> ret;
  std::string group_name = key_value_config.Lookup(experiment_name);

  const std::string kIgnoredSuffix = "_Dogfood";
  std::string::size_type suffix_pos = group_name.rfind(kIgnoredSuffix);
  if (suffix_pos != std::string::npos &&
      suffix_pos == group_name.length() - kIgnoredSuffix.length()) {
    group_name.resize(group_name.length() - kIgnoredSuffix.length());
  }

  if (group_name.empty()) {
    if (experiment_name == kScreenshareProbingBweExperimentName) {
      // This experiment is now default-on with fixed settings.
      // TODO(sprang): Remove this kill-switch and clean up experiment code.
      group_name = kDefaultProbingScreenshareBweSettings;
    } else {
      return ret;
    }
  }

  AlrExperimentSettings settings;
  if (sscanf(group_name.c_str(), "%f,%" PRId64 ",%d,%d,%d,%d",
             &settings.pacing_factor, &settings.max_paced_queue_time,
             &settings.alr_bandwidth_usage_percent,
             &settings.alr_start_budget_level_percent,
             &settings.alr_stop_budget_level_percent,
             &settings.group_id) == 6) {
    ret.emplace(settings);
    RTC_LOG(LS_INFO) << "Using ALR experiment settings: "
                        "pacing factor: "
                     << settings.pacing_factor << ", max pacer queue length: "
                     << settings.max_paced_queue_time
                     << ", ALR bandwidth usage percent: "
                     << settings.alr_bandwidth_usage_percent
                     << ", ALR start budget level percent: "
                     << settings.alr_start_budget_level_percent
                     << ", ALR end budget level percent: "
                     << settings.alr_stop_budget_level_percent
                     << ", ALR experiment group ID: " << settings.group_id;
  } else {
    RTC_LOG(LS_INFO) << "Failed to parse ALR experiment: " << experiment_name;
  }

  return ret;
}

}  // namespace webrtc
