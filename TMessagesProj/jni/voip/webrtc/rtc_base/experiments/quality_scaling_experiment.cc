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

#include "absl/strings/match.h"
#include "api/field_trials_view.h"
#include "api/transport/field_trial_based_config.h"
#include "rtc_base/logging.h"

namespace webrtc {
namespace {
constexpr char kFieldTrial[] = "WebRTC-Video-QualityScaling";
constexpr int kMinQp = 1;
constexpr int kMaxVp8Qp = 127;
constexpr int kMaxVp9Qp = 255;
constexpr int kMaxH264Qp = 51;
constexpr int kMaxGenericQp = 255;

#if !defined(WEBRTC_IOS)
constexpr char kDefaultQualityScalingSetttings[] =
    "Enabled-29,95,149,205,24,37,26,36,0.9995,0.9999,1";
#endif

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

bool QualityScalingExperiment::Enabled(const FieldTrialsView& field_trials) {
#if defined(WEBRTC_IOS)
  return absl::StartsWith(field_trials.Lookup(kFieldTrial), "Enabled");
#else
  return !absl::StartsWith(field_trials.Lookup(kFieldTrial), "Disabled");
#endif
}

absl::optional<QualityScalingExperiment::Settings>
QualityScalingExperiment::ParseSettings(const FieldTrialsView& field_trials) {
  std::string group = field_trials.Lookup(kFieldTrial);
  // TODO(http://crbug.com/webrtc/12401): Completely remove the experiment code
  // after few releases.
#if !defined(WEBRTC_IOS)
  if (group.empty())
    group = kDefaultQualityScalingSetttings;
#endif
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
QualityScalingExperiment::GetQpThresholds(VideoCodecType codec_type,
                                          const FieldTrialsView& field_trials) {
  const auto settings = ParseSettings(field_trials);
  if (!settings)
    return absl::nullopt;

  switch (codec_type) {
    case kVideoCodecVP8:
      return GetThresholds(settings->vp8_low, settings->vp8_high, kMaxVp8Qp);
    case kVideoCodecVP9:
      return GetThresholds(settings->vp9_low, settings->vp9_high, kMaxVp9Qp);
    case kVideoCodecH265:
    //  TODO(bugs.webrtc.org/13485): Use H264 QP thresholds for now.
    case kVideoCodecH264:
      return GetThresholds(settings->h264_low, settings->h264_high, kMaxH264Qp);
    case kVideoCodecGeneric:
      return GetThresholds(settings->generic_low, settings->generic_high,
                           kMaxGenericQp);
    default:
      return absl::nullopt;
  }
}

QualityScalingExperiment::Config QualityScalingExperiment::GetConfig(
    const FieldTrialsView& field_trials) {
  const auto settings = ParseSettings(field_trials);
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
