/*
 *  Copyright (c) 2018 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "rtc_base/experiments/jitter_upper_bound_experiment.h"

#include <stdio.h>

#include <string>

#include "rtc_base/logging.h"
#include "system_wrappers/include/field_trial.h"

namespace webrtc {

const char JitterUpperBoundExperiment::kJitterUpperBoundExperimentName[] =
    "WebRTC-JitterUpperBound";

absl::optional<double> JitterUpperBoundExperiment::GetUpperBoundSigmas() {
  if (!field_trial::IsEnabled(kJitterUpperBoundExperimentName)) {
    return absl::nullopt;
  }
  const std::string group =
      webrtc::field_trial::FindFullName(kJitterUpperBoundExperimentName);

  double upper_bound_sigmas;
  if (sscanf(group.c_str(), "Enabled-%lf", &upper_bound_sigmas) != 1) {
    RTC_LOG(LS_WARNING) << "Invalid number of parameters provided.";
    return absl::nullopt;
  }

  if (upper_bound_sigmas < 0) {
    RTC_LOG(LS_WARNING) << "Invalid jitter upper bound sigmas, must be >= 0.0: "
                        << upper_bound_sigmas;
    return absl::nullopt;
  }

  return upper_bound_sigmas;
}

}  // namespace webrtc
