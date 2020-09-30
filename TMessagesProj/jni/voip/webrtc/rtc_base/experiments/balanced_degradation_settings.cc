/*
 *  Copyright 2019 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "rtc_base/experiments/balanced_degradation_settings.h"

#include <limits>

#include "rtc_base/experiments/field_trial_list.h"
#include "rtc_base/experiments/field_trial_parser.h"
#include "rtc_base/logging.h"
#include "system_wrappers/include/field_trial.h"

namespace webrtc {
namespace {
constexpr char kFieldTrial[] = "WebRTC-Video-BalancedDegradationSettings";
constexpr int kMinFps = 1;
constexpr int kMaxFps = 100;  // 100 means unlimited fps.

std::vector<BalancedDegradationSettings::Config> DefaultConfigs() {
  return {{320 * 240,
           7,
           0,
           0,
           BalancedDegradationSettings::kNoFpsDiff,
           {0, 0, 0, 0, 0},
           {0, 0, 0, 0, 0},
           {0, 0, 0, 0, 0},
           {0, 0, 0, 0, 0},
           {0, 0, 0, 0, 0}},
          {480 * 360,
           10,
           0,
           0,
           1,
           {0, 0, 0, 0, 0},
           {0, 0, 0, 0, 0},
           {0, 0, 0, 0, 0},
           {0, 0, 0, 0, 0},
           {0, 0, 0, 0, 0}},
          {640 * 480,
           15,
           0,
           0,
           1,
           {0, 0, 0, 0, 0},
           {0, 0, 0, 0, 0},
           {0, 0, 0, 0, 0},
           {0, 0, 0, 0, 0},
           {0, 0, 0, 0, 0}}};
}

bool IsValidConfig(
    const BalancedDegradationSettings::CodecTypeSpecific& config) {
  if (config.GetQpLow().has_value() != config.GetQpHigh().has_value()) {
    RTC_LOG(LS_WARNING) << "Neither or both thresholds should be set.";
    return false;
  }
  if (config.GetQpLow().has_value() && config.GetQpHigh().has_value() &&
      config.GetQpLow().value() >= config.GetQpHigh().value()) {
    RTC_LOG(LS_WARNING) << "Invalid threshold value, low >= high threshold.";
    return false;
  }
  if (config.GetFps().has_value() && (config.GetFps().value() < kMinFps ||
                                      config.GetFps().value() > kMaxFps)) {
    RTC_LOG(LS_WARNING) << "Unsupported fps setting, value ignored.";
    return false;
  }
  return true;
}

bool IsValid(const BalancedDegradationSettings::CodecTypeSpecific& config1,
             const BalancedDegradationSettings::CodecTypeSpecific& config2) {
  bool both_or_none_set = ((config1.qp_low > 0) == (config2.qp_low > 0) &&
                           (config1.qp_high > 0) == (config2.qp_high > 0) &&
                           (config1.fps > 0) == (config2.fps > 0));
  if (!both_or_none_set) {
    RTC_LOG(LS_WARNING) << "Invalid value, all/none should be set.";
    return false;
  }
  if (config1.fps > 0 && config1.fps < config2.fps) {
    RTC_LOG(LS_WARNING) << "Invalid fps/pixel value provided.";
    return false;
  }
  return true;
}

bool IsValid(const std::vector<BalancedDegradationSettings::Config>& configs) {
  if (configs.size() <= 1) {
    RTC_LOG(LS_WARNING) << "Unsupported size, value ignored.";
    return false;
  }
  for (const auto& config : configs) {
    if (config.fps < kMinFps || config.fps > kMaxFps) {
      RTC_LOG(LS_WARNING) << "Unsupported fps setting, value ignored.";
      return false;
    }
  }
  int last_kbps = configs[0].kbps;
  for (size_t i = 1; i < configs.size(); ++i) {
    if (configs[i].kbps > 0) {
      if (configs[i].kbps < last_kbps) {
        RTC_LOG(LS_WARNING) << "Invalid bitrate value provided.";
        return false;
      }
      last_kbps = configs[i].kbps;
    }
  }
  for (size_t i = 1; i < configs.size(); ++i) {
    if (configs[i].pixels < configs[i - 1].pixels ||
        configs[i].fps < configs[i - 1].fps) {
      RTC_LOG(LS_WARNING) << "Invalid fps/pixel value provided.";
      return false;
    }
    if (!IsValid(configs[i].vp8, configs[i - 1].vp8) ||
        !IsValid(configs[i].vp9, configs[i - 1].vp9) ||
        !IsValid(configs[i].h264, configs[i - 1].h264) ||
        !IsValid(configs[i].av1, configs[i - 1].av1) ||
        !IsValid(configs[i].generic, configs[i - 1].generic)) {
      return false;
    }
  }
  for (const auto& config : configs) {
    if (!IsValidConfig(config.vp8) || !IsValidConfig(config.vp9) ||
        !IsValidConfig(config.h264) || !IsValidConfig(config.av1) ||
        !IsValidConfig(config.generic)) {
      return false;
    }
  }
  return true;
}

std::vector<BalancedDegradationSettings::Config> GetValidOrDefault(
    const std::vector<BalancedDegradationSettings::Config>& configs) {
  if (IsValid(configs)) {
    return configs;
  }
  return DefaultConfigs();
}

absl::optional<VideoEncoder::QpThresholds> GetThresholds(
    VideoCodecType type,
    const BalancedDegradationSettings::Config& config) {
  absl::optional<int> low;
  absl::optional<int> high;

  switch (type) {
    case kVideoCodecVP8:
      low = config.vp8.GetQpLow();
      high = config.vp8.GetQpHigh();
      break;
    case kVideoCodecVP9:
      low = config.vp9.GetQpLow();
      high = config.vp9.GetQpHigh();
      break;
    case kVideoCodecH264:
      low = config.h264.GetQpLow();
      high = config.h264.GetQpHigh();
      break;
    case kVideoCodecAV1:
      low = config.av1.GetQpLow();
      high = config.av1.GetQpHigh();
      break;
    case kVideoCodecGeneric:
      low = config.generic.GetQpLow();
      high = config.generic.GetQpHigh();
      break;
    default:
      break;
  }

  if (low && high) {
    RTC_LOG(LS_INFO) << "QP thresholds: low: " << *low << ", high: " << *high;
    return absl::optional<VideoEncoder::QpThresholds>(
        VideoEncoder::QpThresholds(*low, *high));
  }
  return absl::nullopt;
}

int GetFps(VideoCodecType type,
           const absl::optional<BalancedDegradationSettings::Config>& config) {
  if (!config.has_value()) {
    return std::numeric_limits<int>::max();
  }

  absl::optional<int> fps;
  switch (type) {
    case kVideoCodecVP8:
      fps = config->vp8.GetFps();
      break;
    case kVideoCodecVP9:
      fps = config->vp9.GetFps();
      break;
    case kVideoCodecH264:
      fps = config->h264.GetFps();
      break;
    case kVideoCodecAV1:
      fps = config->av1.GetFps();
      break;
    case kVideoCodecGeneric:
      fps = config->generic.GetFps();
      break;
    default:
      break;
  }

  const int framerate = fps.value_or(config->fps);

  return (framerate == kMaxFps) ? std::numeric_limits<int>::max() : framerate;
}

absl::optional<int> GetKbps(
    VideoCodecType type,
    const absl::optional<BalancedDegradationSettings::Config>& config) {
  if (!config.has_value())
    return absl::nullopt;

  absl::optional<int> kbps;
  switch (type) {
    case kVideoCodecVP8:
      kbps = config->vp8.GetKbps();
      break;
    case kVideoCodecVP9:
      kbps = config->vp9.GetKbps();
      break;
    case kVideoCodecH264:
      kbps = config->h264.GetKbps();
      break;
    case kVideoCodecAV1:
      kbps = config->av1.GetKbps();
      break;
    case kVideoCodecGeneric:
      kbps = config->generic.GetKbps();
      break;
    default:
      break;
  }

  if (kbps.has_value())
    return kbps;

  return config->kbps > 0 ? absl::optional<int>(config->kbps) : absl::nullopt;
}

absl::optional<int> GetKbpsRes(
    VideoCodecType type,
    const absl::optional<BalancedDegradationSettings::Config>& config) {
  if (!config.has_value())
    return absl::nullopt;

  absl::optional<int> kbps_res;
  switch (type) {
    case kVideoCodecVP8:
      kbps_res = config->vp8.GetKbpsRes();
      break;
    case kVideoCodecVP9:
      kbps_res = config->vp9.GetKbpsRes();
      break;
    case kVideoCodecH264:
      kbps_res = config->h264.GetKbpsRes();
      break;
    case kVideoCodecAV1:
      kbps_res = config->av1.GetKbpsRes();
      break;
    case kVideoCodecGeneric:
      kbps_res = config->generic.GetKbpsRes();
      break;
    default:
      break;
  }

  if (kbps_res.has_value())
    return kbps_res;

  return config->kbps_res > 0 ? absl::optional<int>(config->kbps_res)
                              : absl::nullopt;
}
}  // namespace

absl::optional<int> BalancedDegradationSettings::CodecTypeSpecific::GetQpLow()
    const {
  return (qp_low > 0) ? absl::optional<int>(qp_low) : absl::nullopt;
}

absl::optional<int> BalancedDegradationSettings::CodecTypeSpecific::GetQpHigh()
    const {
  return (qp_high > 0) ? absl::optional<int>(qp_high) : absl::nullopt;
}

absl::optional<int> BalancedDegradationSettings::CodecTypeSpecific::GetFps()
    const {
  return (fps > 0) ? absl::optional<int>(fps) : absl::nullopt;
}

absl::optional<int> BalancedDegradationSettings::CodecTypeSpecific::GetKbps()
    const {
  return (kbps > 0) ? absl::optional<int>(kbps) : absl::nullopt;
}

absl::optional<int> BalancedDegradationSettings::CodecTypeSpecific::GetKbpsRes()
    const {
  return (kbps_res > 0) ? absl::optional<int>(kbps_res) : absl::nullopt;
}

BalancedDegradationSettings::Config::Config() = default;

BalancedDegradationSettings::Config::Config(int pixels,
                                            int fps,
                                            int kbps,
                                            int kbps_res,
                                            int fps_diff,
                                            CodecTypeSpecific vp8,
                                            CodecTypeSpecific vp9,
                                            CodecTypeSpecific h264,
                                            CodecTypeSpecific av1,
                                            CodecTypeSpecific generic)
    : pixels(pixels),
      fps(fps),
      kbps(kbps),
      kbps_res(kbps_res),
      fps_diff(fps_diff),
      vp8(vp8),
      vp9(vp9),
      h264(h264),
      av1(av1),
      generic(generic) {}

BalancedDegradationSettings::BalancedDegradationSettings() {
  FieldTrialStructList<Config> configs(
      {FieldTrialStructMember("pixels", [](Config* c) { return &c->pixels; }),
       FieldTrialStructMember("fps", [](Config* c) { return &c->fps; }),
       FieldTrialStructMember("kbps", [](Config* c) { return &c->kbps; }),
       FieldTrialStructMember("kbps_res",
                              [](Config* c) { return &c->kbps_res; }),
       FieldTrialStructMember("fps_diff",
                              [](Config* c) { return &c->fps_diff; }),
       FieldTrialStructMember("vp8_qp_low",
                              [](Config* c) { return &c->vp8.qp_low; }),
       FieldTrialStructMember("vp8_qp_high",
                              [](Config* c) { return &c->vp8.qp_high; }),
       FieldTrialStructMember("vp8_fps", [](Config* c) { return &c->vp8.fps; }),
       FieldTrialStructMember("vp8_kbps",
                              [](Config* c) { return &c->vp8.kbps; }),
       FieldTrialStructMember("vp8_kbps_res",
                              [](Config* c) { return &c->vp8.kbps_res; }),
       FieldTrialStructMember("vp9_qp_low",
                              [](Config* c) { return &c->vp9.qp_low; }),
       FieldTrialStructMember("vp9_qp_high",
                              [](Config* c) { return &c->vp9.qp_high; }),
       FieldTrialStructMember("vp9_fps", [](Config* c) { return &c->vp9.fps; }),
       FieldTrialStructMember("vp9_kbps",
                              [](Config* c) { return &c->vp9.kbps; }),
       FieldTrialStructMember("vp9_kbps_res",
                              [](Config* c) { return &c->vp9.kbps_res; }),
       FieldTrialStructMember("h264_qp_low",
                              [](Config* c) { return &c->h264.qp_low; }),
       FieldTrialStructMember("h264_qp_high",
                              [](Config* c) { return &c->h264.qp_high; }),
       FieldTrialStructMember("h264_fps",
                              [](Config* c) { return &c->h264.fps; }),
       FieldTrialStructMember("h264_kbps",
                              [](Config* c) { return &c->h264.kbps; }),
       FieldTrialStructMember("h264_kbps_res",
                              [](Config* c) { return &c->h264.kbps_res; }),
       FieldTrialStructMember("av1_qp_low",
                              [](Config* c) { return &c->av1.qp_low; }),
       FieldTrialStructMember("av1_qp_high",
                              [](Config* c) { return &c->av1.qp_high; }),
       FieldTrialStructMember("av1_fps", [](Config* c) { return &c->av1.fps; }),
       FieldTrialStructMember("av1_kbps",
                              [](Config* c) { return &c->av1.kbps; }),
       FieldTrialStructMember("av1_kbps_res",
                              [](Config* c) { return &c->av1.kbps_res; }),
       FieldTrialStructMember("generic_qp_low",
                              [](Config* c) { return &c->generic.qp_low; }),
       FieldTrialStructMember("generic_qp_high",
                              [](Config* c) { return &c->generic.qp_high; }),
       FieldTrialStructMember("generic_fps",
                              [](Config* c) { return &c->generic.fps; }),
       FieldTrialStructMember("generic_kbps",
                              [](Config* c) { return &c->generic.kbps; }),
       FieldTrialStructMember("generic_kbps_res",
                              [](Config* c) { return &c->generic.kbps_res; })},
      {});

  ParseFieldTrial({&configs}, field_trial::FindFullName(kFieldTrial));

  configs_ = GetValidOrDefault(configs.Get());
  RTC_DCHECK_GT(configs_.size(), 1);
}

BalancedDegradationSettings::~BalancedDegradationSettings() {}

std::vector<BalancedDegradationSettings::Config>
BalancedDegradationSettings::GetConfigs() const {
  return configs_;
}

int BalancedDegradationSettings::MinFps(VideoCodecType type, int pixels) const {
  return GetFps(type, GetMinFpsConfig(pixels));
}

absl::optional<BalancedDegradationSettings::Config>
BalancedDegradationSettings::GetMinFpsConfig(int pixels) const {
  for (const auto& config : configs_) {
    if (pixels <= config.pixels)
      return config;
  }
  return absl::nullopt;
}

int BalancedDegradationSettings::MaxFps(VideoCodecType type, int pixels) const {
  return GetFps(type, GetMaxFpsConfig(pixels));
}

absl::optional<BalancedDegradationSettings::Config>
BalancedDegradationSettings::GetMaxFpsConfig(int pixels) const {
  for (size_t i = 0; i < configs_.size() - 1; ++i) {
    if (pixels <= configs_[i].pixels)
      return configs_[i + 1];
  }
  return absl::nullopt;
}

bool BalancedDegradationSettings::CanAdaptUp(VideoCodecType type,
                                             int pixels,
                                             uint32_t bitrate_bps) const {
  absl::optional<int> min_kbps = GetKbps(type, GetMaxFpsConfig(pixels));
  if (!min_kbps.has_value() || bitrate_bps == 0) {
    return true;  // No limit configured or bitrate provided.
  }
  return bitrate_bps >= static_cast<uint32_t>(min_kbps.value() * 1000);
}

bool BalancedDegradationSettings::CanAdaptUpResolution(
    VideoCodecType type,
    int pixels,
    uint32_t bitrate_bps) const {
  absl::optional<int> min_kbps = GetKbpsRes(type, GetMaxFpsConfig(pixels));
  if (!min_kbps.has_value() || bitrate_bps == 0) {
    return true;  // No limit configured or bitrate provided.
  }
  return bitrate_bps >= static_cast<uint32_t>(min_kbps.value() * 1000);
}

absl::optional<int> BalancedDegradationSettings::MinFpsDiff(int pixels) const {
  for (const auto& config : configs_) {
    if (pixels <= config.pixels) {
      return (config.fps_diff > kNoFpsDiff)
                 ? absl::optional<int>(config.fps_diff)
                 : absl::nullopt;
    }
  }
  return absl::nullopt;
}

absl::optional<VideoEncoder::QpThresholds>
BalancedDegradationSettings::GetQpThresholds(VideoCodecType type,
                                             int pixels) const {
  return GetThresholds(type, GetConfig(pixels));
}

BalancedDegradationSettings::Config BalancedDegradationSettings::GetConfig(
    int pixels) const {
  for (const auto& config : configs_) {
    if (pixels <= config.pixels)
      return config;
  }
  return configs_.back();  // Use last above highest pixels.
}

}  // namespace webrtc
