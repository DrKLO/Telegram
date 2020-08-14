/*
 *  Copyright 2018 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */
#include "rtc_base/experiments/quality_scaling_experiment.h"

#include <stdio.h>

#include <string>

#include "rtc_base/logging.h"
#include "system_wrappers/include/field_trial.h"

namespace webrtc {
namespace {
constexpr char kFieldTrial[] = "WebRTC-Video-QualityScaling";
constexpr int kMinQp = 1;
constexpr int kMaxVp8Qp = 127;
constexpr int kMaxVp9Qp = 255;
constexpr int kMaxH264Qp = 51;
constexpr int kMaxGenericQp = 255;

absl::optional<VideoEncoder::QpThresholds> GetThresholds(int low,
                                                         int high,
                                                         int max) {
  if (low < kMinQp || high > max || high < low)
    return absl::nullopt;

  RTC_LOG(LS_INFO) << "QP thresholds: low: " << low << ", high: " << high;
  return absl::optional<VideoEncoder::QpThresholds>(
      VideoEncoder::QpThresholds(low, high));
}
}  // namespace

bool QualityScalingExperiment::Enabled() {
  return webrtc::field_trial::IsEnabled(kFieldTrial);
}

absl::optional<QualityScalingExperiment::Settings>
QualityScalingExperiment::ParseSettings() {
  const std::string group = webrtc::field_trial::FindFullName(kFieldTrial);
  if (group.empty())
    return absl::nullopt;

  Settings s;
  if (sscanf(group.c_str(), "Enabled-%d,%d,%d,%d,%d,%d,%d,%d,%f,%f,%d",
             &s.vp8_low, &s.vp8_high, &s.vp9_low, &s.vp9_high, &s.h264_low,
             &s.h264_high, &s.generic_low, &s.generic_high, &s.alpha_high,
             &s.alpha_low, &s.drop) != 11) {
    RTC_LOG(LS_WARNING) << "Invalid number of parameters provided.";
    return absl::nullopt;
  }
  return s;
}

absl::optional<VideoEncoder::QpThresholds>
QualityScalingExperiment::GetQpThresholds(VideoCodecType codec_type) {
  const auto settings = ParseSettings();
  if (!settings)
    return absl::nullopt;

  switch (codec_type) {
    case kVideoCodecVP8:
      return GetThresholds(settings->vp8_low, settings->vp8_high, kMaxVp8Qp);
    case kVideoCodecVP9:
      return GetThresholds(settings->vp9_low, settings->vp9_high, kMaxVp9Qp);
    case kVideoCodecH264:
      return GetThresholds(settings->h264_low, settings->h264_high, kMaxH264Qp);
    case kVideoCodecGeneric:
      return GetThresholds(settings->generic_low, settings->generic_high,
                           kMaxGenericQp);
    default:
      return absl::nullopt;
  }
}

QualityScalingExperiment::Config QualityScalingExperiment::GetConfig() {
  const auto settings = ParseSettings();
  if (!settings)
    return Config();

  Config config;
  config.use_all_drop_reasons = settings->drop > 0;

  if (settings->alpha_high < 0 || settings->alpha_low < settings->alpha_high) {
    RTC_LOG(LS_WARNING) << "Invalid alpha value provided, using default.";
    return config;
  }
  config.alpha_high = settings->alpha_high;
  config.alpha_low = settings->alpha_low;
  return config;
}

}  // namespace webrtc
