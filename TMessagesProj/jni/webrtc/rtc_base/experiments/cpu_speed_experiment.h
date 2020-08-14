/*
 *  Copyright 2018 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#ifndef RTC_BASE_EXPERIMENTS_CPU_SPEED_EXPERIMENT_H_
#define RTC_BASE_EXPERIMENTS_CPU_SPEED_EXPERIMENT_H_

#include <vector>

#include "absl/types/optional.h"

namespace webrtc {

class CpuSpeedExperiment {
 public:
  struct Config {
    bool operator==(const Config& o) const {
      return pixels == o.pixels && cpu_speed == o.cpu_speed;
    }

    int pixels;     // The video frame size.
    int cpu_speed;  // The |cpu_speed| to be used if the frame size is less
                    // than or equal to |pixels|.
  };

  // Returns the configurations from field trial on success.
  static absl::optional<std::vector<Config>> GetConfigs();

  // Gets the cpu speed from the |configs| based on |pixels|.
  static int GetValue(int pixels, const std::vector<Config>& configs);
};

}  // namespace webrtc

#endif  // RTC_BASE_EXPERIMENTS_CPU_SPEED_EXPERIMENT_H_
