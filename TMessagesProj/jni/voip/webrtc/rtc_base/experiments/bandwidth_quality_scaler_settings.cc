/*
 *  Copyright (c) 2021 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "rtc_base/experiments/bandwidth_quality_scaler_settings.h"

#include "api/transport/field_trial_based_config.h"
#include "rtc_base/logging.h"

namespace webrtc {

BandwidthQualityScalerSettings::BandwidthQualityScalerSettings(
    const FieldTrialsView* const key_value_config)
    : bitrate_state_update_interval_s_("bitrate_state_update_interval_s_") {
  ParseFieldTrial(
      {&bitrate_state_update_interval_s_},
      key_value_config->Lookup("WebRTC-Video-BandwidthQualityScalerSettings"));
}

BandwidthQualityScalerSettings
BandwidthQualityScalerSettings::ParseFromFieldTrials() {
  FieldTrialBasedConfig field_trial_config;
  return BandwidthQualityScalerSettings(&field_trial_config);
}

absl::optional<uint32_t>
BandwidthQualityScalerSettings::BitrateStateUpdateInterval() const {
  if (bitrate_state_update_interval_s_ &&
      bitrate_state_update_interval_s_.Value() <= 0) {
    RTC_LOG(LS_WARNING)
        << "Unsupported bitrate_state_update_interval_s_ value, ignored.";
    return absl::nullopt;
  }
  return bitrate_state_update_interval_s_.GetOptional();
}

}  // namespace webrtc
