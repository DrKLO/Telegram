/*
 *  Copyright 2018 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "rtc_base/experiments/cpu_speed_experiment.h"

#include <stdio.h>

#include "rtc_base/experiments/field_trial_list.h"
#include "rtc_base/logging.h"
#include "system_wrappers/include/field_trial.h"

namespace webrtc {
namespace {
constexpr char kFieldTrial[] = "WebRTC-VP8-CpuSpeed-Arm";
constexpr int kMinSetting = -16;
constexpr int kMaxSetting = -1;

std::vector<CpuSpeedExperiment::Config> GetValidOrEmpty(
    const std::vector<CpuSpeedExperiment::Config>& configs) {
  if (configs.empty()) {
    RTC_LOG(LS_WARNING) << "Unsupported size, value ignored.";
    return {};
  }

  for (const auto& config : configs) {
    if (config.cpu_speed < kMinSetting || config.cpu_speed > kMaxSetting) {
      RTC_LOG(LS_WARNING) << "Unsupported cpu speed setting, value ignored.";
      return {};
    }
  }

  for (size_t i = 1; i < configs.size(); ++i) {
    if (configs[i].pixels < configs[i - 1].pixels ||
        configs[i].cpu_speed > configs[i - 1].cpu_speed) {
      RTC_LOG(LS_WARNING) << "Invalid parameter value provided.";
      return {};
    }
  }

  return configs;
}

bool HasLeCores(const std::vector<CpuSpeedExperiment::Config>& configs) {
  for (const auto& config : configs) {
    if (config.cpu_speed_le_cores == 0)
      return false;
  }
  return true;
}
}  // namespace

CpuSpeedExperiment::CpuSpeedExperiment() : cores_("cores") {
  FieldTrialStructList<Config> configs(
      {FieldTrialStructMember("pixels", [](Config* c) { return &c->pixels; }),
       FieldTrialStructMember("cpu_speed",
                              [](Config* c) { return &c->cpu_speed; }),
       FieldTrialStructMember(
           "cpu_speed_le_cores",
           [](Config* c) { return &c->cpu_speed_le_cores; })},
      {});
  ParseFieldTrial({&configs, &cores_}, field_trial::FindFullName(kFieldTrial));

  configs_ = GetValidOrEmpty(configs.Get());
}

CpuSpeedExperiment::~CpuSpeedExperiment() {}

absl::optional<int> CpuSpeedExperiment::GetValue(int pixels,
                                                 int num_cores) const {
  if (configs_.empty())
    return absl::nullopt;

  bool use_le = HasLeCores(configs_) && cores_ && num_cores <= cores_.Value();

  for (const auto& config : configs_) {
    if (pixels <= config.pixels)
      return use_le ? absl::optional<int>(config.cpu_speed_le_cores)
                    : absl::optional<int>(config.cpu_speed);
  }
  return absl::optional<int>(kMinSetting);
}

}  // namespace webrtc
