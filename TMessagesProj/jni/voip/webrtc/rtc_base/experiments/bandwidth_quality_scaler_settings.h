/*
 *  Copyright (c) 2021 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#ifndef RTC_BASE_EXPERIMENTS_BANDWIDTH_QUALITY_SCALER_SETTINGS_H_
#define RTC_BASE_EXPERIMENTS_BANDWIDTH_QUALITY_SCALER_SETTINGS_H_

#include "absl/types/optional.h"
#include "api/field_trials_view.h"
#include "rtc_base/experiments/field_trial_parser.h"

namespace webrtc {

class BandwidthQualityScalerSettings final {
 public:
  static BandwidthQualityScalerSettings ParseFromFieldTrials();

  absl::optional<uint32_t> BitrateStateUpdateInterval() const;

 private:
  explicit BandwidthQualityScalerSettings(
      const FieldTrialsView* const key_value_config);

  FieldTrialOptional<uint32_t> bitrate_state_update_interval_s_;
};

}  // namespace webrtc

#endif  // RTC_BASE_EXPERIMENTS_BANDWIDTH_QUALITY_SCALER_SETTINGS_H_
