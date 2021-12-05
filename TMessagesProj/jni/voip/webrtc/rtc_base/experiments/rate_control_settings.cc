/*
 *  Copyright (c) 2019 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "rtc_base/experiments/rate_control_settings.h"

#include <inttypes.h>
#include <stdio.h>

#include <string>

#include "absl/strings/match.h"
#include "api/transport/field_trial_based_config.h"
#include "rtc_base/logging.h"
#include "rtc_base/numerics/safe_conversions.h"

namespace webrtc {

namespace {

const int kDefaultAcceptedQueueMs = 350;

const int kDefaultMinPushbackTargetBitrateBps = 30000;

const char kCongestionWindowDefaultFieldTrialString[] =
    "QueueSize:350,MinBitrate:30000,DropFrame:true";

const char kUseBaseHeavyVp8Tl3RateAllocationFieldTrialName[] =
    "WebRTC-UseBaseHeavyVP8TL3RateAllocation";

const char* kVideoHysteresisFieldTrialname =
    "WebRTC-SimulcastUpswitchHysteresisPercent";
const char* kScreenshareHysteresisFieldTrialname =
    "WebRTC-SimulcastScreenshareUpswitchHysteresisPercent";

bool IsEnabled(const WebRtcKeyValueConfig* const key_value_config,
               absl::string_view key) {
  return absl::StartsWith(key_value_config->Lookup(key), "Enabled");
}

void ParseHysteresisFactor(const WebRtcKeyValueConfig* const key_value_config,
                           absl::string_view key,
                           double* output_value) {
  std::string group_name = key_value_config->Lookup(key);
  int percent = 0;
  if (!group_name.empty() && sscanf(group_name.c_str(), "%d", &percent) == 1 &&
      percent >= 0) {
    *output_value = 1.0 + (percent / 100.0);
  }
}

}  // namespace

constexpr char CongestionWindowConfig::kKey[];

std::unique_ptr<StructParametersParser> CongestionWindowConfig::Parser() {
  return StructParametersParser::Create("QueueSize", &queue_size_ms,  //
                                        "MinBitrate", &min_bitrate_bps,
                                        "InitWin", &initial_data_window,
                                        "DropFrame", &drop_frame_only);
}

// static
CongestionWindowConfig CongestionWindowConfig::Parse(absl::string_view config) {
  CongestionWindowConfig res;
  res.Parser()->Parse(config);
  return res;
}

constexpr char VideoRateControlConfig::kKey[];

std::unique_ptr<StructParametersParser> VideoRateControlConfig::Parser() {
  // The empty comments ensures that each pair is on a separate line.
  return StructParametersParser::Create(
      "pacing_factor", &pacing_factor,                    //
      "alr_probing", &alr_probing,                        //
      "vp8_qp_max", &vp8_qp_max,                          //
      "vp8_min_pixels", &vp8_min_pixels,                  //
      "trust_vp8", &trust_vp8,                            //
      "trust_vp9", &trust_vp9,                            //
      "video_hysteresis", &video_hysteresis,              //
      "screenshare_hysteresis", &screenshare_hysteresis,  //
      "probe_max_allocation", &probe_max_allocation,      //
      "bitrate_adjuster", &bitrate_adjuster,              //
      "adjuster_use_headroom", &adjuster_use_headroom,    //
      "vp8_s0_boost", &vp8_s0_boost,                      //
      "vp8_base_heavy_tl3_alloc", &vp8_base_heavy_tl3_alloc);
}

RateControlSettings::RateControlSettings(
    const WebRtcKeyValueConfig* const key_value_config) {
  std::string congestion_window_config =
      key_value_config->Lookup(CongestionWindowConfig::kKey).empty()
          ? kCongestionWindowDefaultFieldTrialString
          : key_value_config->Lookup(CongestionWindowConfig::kKey);
  congestion_window_config_ =
      CongestionWindowConfig::Parse(congestion_window_config);
  video_config_.vp8_base_heavy_tl3_alloc = IsEnabled(
      key_value_config, kUseBaseHeavyVp8Tl3RateAllocationFieldTrialName);
  ParseHysteresisFactor(key_value_config, kVideoHysteresisFieldTrialname,
                        &video_config_.video_hysteresis);
  ParseHysteresisFactor(key_value_config, kScreenshareHysteresisFieldTrialname,
                        &video_config_.screenshare_hysteresis);
  video_config_.Parser()->Parse(
      key_value_config->Lookup(VideoRateControlConfig::kKey));
}

RateControlSettings::~RateControlSettings() = default;
RateControlSettings::RateControlSettings(RateControlSettings&&) = default;

RateControlSettings RateControlSettings::ParseFromFieldTrials() {
  FieldTrialBasedConfig field_trial_config;
  return RateControlSettings(&field_trial_config);
}

RateControlSettings RateControlSettings::ParseFromKeyValueConfig(
    const WebRtcKeyValueConfig* const key_value_config) {
  FieldTrialBasedConfig field_trial_config;
  return RateControlSettings(key_value_config ? key_value_config
                                              : &field_trial_config);
}

bool RateControlSettings::UseCongestionWindow() const {
  return static_cast<bool>(congestion_window_config_.queue_size_ms);
}

int64_t RateControlSettings::GetCongestionWindowAdditionalTimeMs() const {
  return congestion_window_config_.queue_size_ms.value_or(
      kDefaultAcceptedQueueMs);
}

bool RateControlSettings::UseCongestionWindowPushback() const {
  return congestion_window_config_.queue_size_ms &&
         congestion_window_config_.min_bitrate_bps;
}

bool RateControlSettings::UseCongestionWindowDropFrameOnly() const {
  return congestion_window_config_.drop_frame_only;
}

uint32_t RateControlSettings::CongestionWindowMinPushbackTargetBitrateBps()
    const {
  return congestion_window_config_.min_bitrate_bps.value_or(
      kDefaultMinPushbackTargetBitrateBps);
}

absl::optional<DataSize>
RateControlSettings::CongestionWindowInitialDataWindow() const {
  return congestion_window_config_.initial_data_window;
}

absl::optional<double> RateControlSettings::GetPacingFactor() const {
  return video_config_.pacing_factor;
}

bool RateControlSettings::UseAlrProbing() const {
  return video_config_.alr_probing;
}

absl::optional<int> RateControlSettings::LibvpxVp8QpMax() const {
  if (video_config_.vp8_qp_max &&
      (*video_config_.vp8_qp_max < 0 || *video_config_.vp8_qp_max > 63)) {
    RTC_LOG(LS_WARNING) << "Unsupported vp8_qp_max_ value, ignored.";
    return absl::nullopt;
  }
  return video_config_.vp8_qp_max;
}

absl::optional<int> RateControlSettings::LibvpxVp8MinPixels() const {
  if (video_config_.vp8_min_pixels && *video_config_.vp8_min_pixels < 1) {
    return absl::nullopt;
  }
  return video_config_.vp8_min_pixels;
}

bool RateControlSettings::LibvpxVp8TrustedRateController() const {
  return video_config_.trust_vp8;
}

bool RateControlSettings::Vp8BoostBaseLayerQuality() const {
  return video_config_.vp8_s0_boost;
}

bool RateControlSettings::LibvpxVp9TrustedRateController() const {
  return video_config_.trust_vp9;
}

double RateControlSettings::GetSimulcastHysteresisFactor(
    VideoCodecMode mode) const {
  if (mode == VideoCodecMode::kScreensharing) {
    return video_config_.screenshare_hysteresis;
  }
  return video_config_.video_hysteresis;
}

double RateControlSettings::GetSimulcastHysteresisFactor(
    VideoEncoderConfig::ContentType content_type) const {
  if (content_type == VideoEncoderConfig::ContentType::kScreen) {
    return video_config_.screenshare_hysteresis;
  }
  return video_config_.video_hysteresis;
}

bool RateControlSettings::Vp8BaseHeavyTl3RateAllocation() const {
  return video_config_.vp8_base_heavy_tl3_alloc;
}

bool RateControlSettings::TriggerProbeOnMaxAllocatedBitrateChange() const {
  return video_config_.probe_max_allocation;
}

bool RateControlSettings::UseEncoderBitrateAdjuster() const {
  return video_config_.bitrate_adjuster;
}

bool RateControlSettings::BitrateAdjusterCanUseNetworkHeadroom() const {
  return video_config_.adjuster_use_headroom;
}

}  // namespace webrtc
