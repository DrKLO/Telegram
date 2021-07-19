/*
 *  Copyright (c) 2019 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "rtc_base/experiments/quality_scaler_settings.h"

#include "api/transport/field_trial_based_config.h"
#include "rtc_base/logging.h"

namespace webrtc {
namespace {
const int kMinFrames = 10;
const double kMinScaleFactor = 0.01;
}  // namespace

QualityScalerSettings::QualityScalerSettings(
    const WebRtcKeyValueConfig* const key_value_config)
    : sampling_period_ms_("sampling_period_ms"),
      average_qp_window_("average_qp_window"),
      min_frames_("min_frames"),
      initial_scale_factor_("initial_scale_factor"),
      scale_factor_("scale_factor"),
      initial_bitrate_interval_ms_("initial_bitrate_interval_ms"),
      initial_bitrate_factor_("initial_bitrate_factor") {
  ParseFieldTrial(
      {&sampling_period_ms_, &average_qp_window_, &min_frames_,
       &initial_scale_factor_, &scale_factor_, &initial_bitrate_interval_ms_,
       &initial_bitrate_factor_},
      key_value_config->Lookup("WebRTC-Video-QualityScalerSettings"));
}

QualityScalerSettings QualityScalerSettings::ParseFromFieldTrials() {
  FieldTrialBasedConfig field_trial_config;
  return QualityScalerSettings(&field_trial_config);
}

absl::optional<int> QualityScalerSettings::SamplingPeriodMs() const {
  if (sampling_period_ms_ && sampling_period_ms_.Value() <= 0) {
    RTC_LOG(LS_WARNING) << "Unsupported sampling_period_ms value, ignored.";
    return absl::nullopt;
  }
  return sampling_period_ms_.GetOptional();
}

absl::optional<int> QualityScalerSettings::AverageQpWindow() const {
  if (average_qp_window_ && average_qp_window_.Value() <= 0) {
    RTC_LOG(LS_WARNING) << "Unsupported average_qp_window value, ignored.";
    return absl::nullopt;
  }
  return average_qp_window_.GetOptional();
}

absl::optional<int> QualityScalerSettings::MinFrames() const {
  if (min_frames_ && min_frames_.Value() < kMinFrames) {
    RTC_LOG(LS_WARNING) << "Unsupported min_frames value, ignored.";
    return absl::nullopt;
  }
  return min_frames_.GetOptional();
}

absl::optional<double> QualityScalerSettings::InitialScaleFactor() const {
  if (initial_scale_factor_ &&
      initial_scale_factor_.Value() < kMinScaleFactor) {
    RTC_LOG(LS_WARNING) << "Unsupported initial_scale_factor value, ignored.";
    return absl::nullopt;
  }
  return initial_scale_factor_.GetOptional();
}

absl::optional<double> QualityScalerSettings::ScaleFactor() const {
  if (scale_factor_ && scale_factor_.Value() < kMinScaleFactor) {
    RTC_LOG(LS_WARNING) << "Unsupported scale_factor value, ignored.";
    return absl::nullopt;
  }
  return scale_factor_.GetOptional();
}

absl::optional<int> QualityScalerSettings::InitialBitrateIntervalMs() const {
  if (initial_bitrate_interval_ms_ &&
      initial_bitrate_interval_ms_.Value() < 0) {
    RTC_LOG(LS_WARNING) << "Unsupported bitrate_interval value, ignored.";
    return absl::nullopt;
  }
  return initial_bitrate_interval_ms_.GetOptional();
}

absl::optional<double> QualityScalerSettings::InitialBitrateFactor() const {
  if (initial_bitrate_factor_ &&
      initial_bitrate_factor_.Value() < kMinScaleFactor) {
    RTC_LOG(LS_WARNING) << "Unsupported initial_bitrate_factor value, ignored.";
    return absl::nullopt;
  }
  return initial_bitrate_factor_.GetOptional();
}

}  // namespace webrtc
