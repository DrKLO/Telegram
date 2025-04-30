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
}  // namespace

bool RttMultExperiment::RttMultEnabled() {
  return !field_trial::IsDisabled(kRttMultExperiment);
}

absl::optional<RttMultExperiment::Settings>
RttMultExperiment::GetRttMultValue() {
  if (!RttMultExperiment::RttMultEnabled()) {
    return absl::nullopt;
  }
  return RttMultExperiment::Settings{.rtt_mult_setting = 0.9,
                                     .rtt_mult_add_cap_ms = 200.0};
}

}  // namespace webrtc
