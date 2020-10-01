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

#include <string>

#include "rtc_base/logging.h"
#include "system_wrappers/include/field_trial.h"

namespace webrtc {
namespace {
constexpr char kFieldTrial[] = "WebRTC-VP8-CpuSpeed-Arm";
constexpr int kMinSetting = -16;
constexpr int kMaxSetting = -1;
}  // namespace

absl::optional<std::vector<CpuSpeedExperiment::Config>>
CpuSpeedExperiment::GetConfigs() {
  if (!webrtc::field_trial::IsEnabled(kFieldTrial))
    return absl::nullopt;

  const std::string group = webrtc::field_trial::FindFullName(kFieldTrial);
  if (group.empty())
    return absl::nullopt;

  std::vector<Config> configs(3);
  if (sscanf(group.c_str(), "Enabled-%d,%d,%d,%d,%d,%d", &(configs[0].pixels),
             &(configs[0].cpu_speed), &(configs[1].pixels),
             &(configs[1].cpu_speed), &(configs[2].pixels),
             &(configs[2].cpu_speed)) != 6) {
    RTC_LOG(LS_WARNING) << "Too few parameters provided.";
    return absl::nullopt;
  }

  for (const auto& config : configs) {
    if (config.cpu_speed < kMinSetting || config.cpu_speed > kMaxSetting) {
      RTC_LOG(LS_WARNING) << "Unsupported cpu speed setting, value ignored.";
      return absl::nullopt;
    }
  }

  for (size_t i = 1; i < configs.size(); ++i) {
    if (configs[i].pixels < configs[i - 1].pixels ||
        configs[i].cpu_speed > configs[i - 1].cpu_speed) {
      RTC_LOG(LS_WARNING) << "Invalid parameter value provided.";
      return absl::nullopt;
    }
  }

  return absl::optional<std::vector<Config>>(configs);
}

int CpuSpeedExperiment::GetValue(int pixels,
                                 const std::vector<Config>& configs) {
  for (const auto& config : configs) {
    if (pixels <= config.pixels)
      return config.cpu_speed;
  }
  return kMinSetting;
}

}  // namespace webrtc
