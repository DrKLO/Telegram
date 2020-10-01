/*
 *  Copyright 2018 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */
#include "rtc_base/experiments/rtt_mult_experiment.h"

#include <stdio.h>

#include <algorithm>
#include <string>

#include "rtc_base/logging.h"
#include "system_wrappers/include/field_trial.h"

namespace webrtc {

namespace {
const char kRttMultExperiment[] = "WebRTC-RttMult";
const float max_rtt_mult_setting = 1.0;
const float min_rtt_mult_setting = 0.0;
const float max_rtt_mult_add_cap_ms = 2000.0;
const float min_rtt_mult_add_cap_ms = 0.0;
}  // namespace

bool RttMultExperiment::RttMultEnabled() {
  return field_trial::IsEnabled(kRttMultExperiment);
}

absl::optional<RttMultExperiment::Settings>
RttMultExperiment::GetRttMultValue() {
  if (!RttMultExperiment::RttMultEnabled())
    return absl::nullopt;
  const std::string group =
      webrtc::field_trial::FindFullName(kRttMultExperiment);
  if (group.empty()) {
    RTC_LOG(LS_WARNING) << "Could not find rtt_mult_experiment.";
    return absl::nullopt;
  }

  Settings s;
  if (sscanf(group.c_str(), "Enabled-%f,%f", &s.rtt_mult_setting,
             &s.rtt_mult_add_cap_ms) != 2) {
    RTC_LOG(LS_WARNING) << "Invalid number of parameters provided.";
    return absl::nullopt;
  }
  // Bounds check rtt_mult_setting and rtt_mult_add_cap_ms values.
  s.rtt_mult_setting = std::min(s.rtt_mult_setting, max_rtt_mult_setting);
  s.rtt_mult_setting = std::max(s.rtt_mult_setting, min_rtt_mult_setting);
  s.rtt_mult_add_cap_ms =
      std::min(s.rtt_mult_add_cap_ms, max_rtt_mult_add_cap_ms);
  s.rtt_mult_add_cap_ms =
      std::max(s.rtt_mult_add_cap_ms, min_rtt_mult_add_cap_ms);
  RTC_LOG(LS_INFO) << "rtt_mult experiment: rtt_mult value = "
                   << s.rtt_mult_setting
                   << " rtt_mult addition cap = " << s.rtt_mult_add_cap_ms
                   << " ms.";
  return s;
}

}  // namespace webrtc
