/*
 *  Copyright (c) 2019 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "rtc_base/experiments/quality_rampup_experiment.h"

#include <algorithm>

#include "api/transport/field_trial_based_config.h"
#include "rtc_base/logging.h"

namespace webrtc {

QualityRampupExperiment::QualityRampupExperiment(
    const WebRtcKeyValueConfig* const key_value_config)
    : min_pixels_("min_pixels"),
      min_duration_ms_("min_duration_ms"),
      max_bitrate_factor_("max_bitrate_factor") {
  ParseFieldTrial(
      {&min_pixels_, &min_duration_ms_, &max_bitrate_factor_},
      key_value_config->Lookup("WebRTC-Video-QualityRampupSettings"));
}

QualityRampupExperiment QualityRampupExperiment::ParseSettings() {
  FieldTrialBasedConfig field_trial_config;
  return QualityRampupExperiment(&field_trial_config);
}

absl::optional<int> QualityRampupExperiment::MinPixels() const {
  return min_pixels_.GetOptional();
}

absl::optional<int> QualityRampupExperiment::MinDurationMs() const {
  return min_duration_ms_.GetOptional();
}

absl::optional<double> QualityRampupExperiment::MaxBitrateFactor() const {
  return max_bitrate_factor_.GetOptional();
}

void QualityRampupExperiment::SetMaxBitrate(int pixels,
                                            uint32_t max_bitrate_kbps) {
  if (!min_pixels_ || pixels < min_pixels_.Value() || max_bitrate_kbps == 0) {
    return;
  }
  max_bitrate_kbps_ = std::max(max_bitrate_kbps_.value_or(0), max_bitrate_kbps);
}

bool QualityRampupExperiment::BwHigh(int64_t now_ms,
                                     uint32_t available_bw_kbps) {
  if (!min_pixels_ || !min_duration_ms_ || !max_bitrate_kbps_) {
    return false;
  }

  if (available_bw_kbps <
      max_bitrate_kbps_.value() * MaxBitrateFactor().value_or(1)) {
    start_ms_.reset();
    return false;
  }

  if (!start_ms_)
    start_ms_ = now_ms;

  return (now_ms - *start_ms_) >= min_duration_ms_.Value();
}

bool QualityRampupExperiment::Enabled() const {
  return min_pixels_ || min_duration_ms_ || max_bitrate_kbps_;
}

}  // namespace webrtc
