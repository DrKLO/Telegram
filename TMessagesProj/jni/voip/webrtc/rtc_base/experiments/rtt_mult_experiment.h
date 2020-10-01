/*
 *  Copyright 2018 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */
#ifndef RTC_BASE_EXPERIMENTS_RTT_MULT_EXPERIMENT_H_
#define RTC_BASE_EXPERIMENTS_RTT_MULT_EXPERIMENT_H_

#include "absl/types/optional.h"

namespace webrtc {

class RttMultExperiment {
 public:
  struct Settings {
    float rtt_mult_setting;  // Jitter buffer size is increased by this factor
                             // times the estimated RTT.
    float rtt_mult_add_cap_ms;  // Jitter buffer size increase is capped by this
                                // value.
  };

  // Returns true if the experiment is enabled.
  static bool RttMultEnabled();

  // Returns rtt_mult value and rtt_mult addition cap value from field trial.
  static absl::optional<RttMultExperiment::Settings> GetRttMultValue();
};

}  // namespace webrtc

#endif  // RTC_BASE_EXPERIMENTS_RTT_MULT_EXPERIMENT_H_
