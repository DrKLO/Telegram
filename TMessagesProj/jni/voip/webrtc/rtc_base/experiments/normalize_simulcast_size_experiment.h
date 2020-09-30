/*
 *  Copyright 2018 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#ifndef RTC_BASE_EXPERIMENTS_NORMALIZE_SIMULCAST_SIZE_EXPERIMENT_H_
#define RTC_BASE_EXPERIMENTS_NORMALIZE_SIMULCAST_SIZE_EXPERIMENT_H_

#include "absl/types/optional.h"

namespace webrtc {
class NormalizeSimulcastSizeExperiment {
 public:
  // Returns the base two exponent from field trial.
  static absl::optional<int> GetBase2Exponent();
};

}  // namespace webrtc

#endif  // RTC_BASE_EXPERIMENTS_NORMALIZE_SIMULCAST_SIZE_EXPERIMENT_H_
