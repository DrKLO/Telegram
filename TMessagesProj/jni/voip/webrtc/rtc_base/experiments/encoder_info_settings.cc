/*
 *  Copyright 2021 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "rtc_base/experiments/encoder_info_settings.h"

#include <stdio.h>

#include "rtc_base/experiments/field_trial_list.h"
#include "rtc_base/logging.h"
#include "system_wrappers/include/field_trial.h"

namespace webrtc {
namespace {

std::vector<VideoEncoder::ResolutionBitrateLimits> ToResolutionBitrateLimits(
    const std::vector<EncoderInfoSettings::BitrateLimit>& limits) {
  std::vector<VideoEncoder::ResolutionBitrateLimits> result;
  for (const auto& limit : limits) {
    result.push_back(VideoEncoder::ResolutionBitrateLimits(
        limit.frame_size_pixels, limit.min_start_bitrate_bps,
        limit.min_bitrate_bps, limit.max_bitrate_bps));
  }
  return result;
}

}  // namespace

// Default bitrate limits for simulcast with one active stream:
// {frame_size_pixels, min_start_bitrate_bps, min_bitrate_bps, max_bitrate_bps}.
std::vector<VideoEncoder::ResolutionBitrateLimits>
EncoderInfoSettings::GetDefaultSinglecastBitrateLimits(
    VideoCodecType codec_type) {
  // Specific limits for VP9. Other codecs use VP8 limits.
  if (codec_type == kVideoCodecVP9) {
    return {{320 * 180, 0, 30000, 150000},
            {480 * 270, 120000, 30000, 300000},
            {640 * 360, 190000, 30000, 420000},
            {960 * 540, 350000, 30000, 1000000},
            {1280 * 720, 480000, 30000, 1500000}};
  }

  return {{320 * 180, 0, 30000, 300000},
          {480 * 270, 200000, 30000, 500000},
          {640 * 360, 300000, 30000, 800000},
          {960 * 540, 500000, 30000, 1500000},
          {1280 * 720, 900000, 30000, 2500000}};
}

absl::optional<VideoEncoder::ResolutionBitrateLimits>
EncoderInfoSettings::GetDefaultSinglecastBitrateLimitsForResolution(
    VideoCodecType codec_type,
    int frame_size_pixels) {
  VideoEncoder::EncoderInfo info;
  info.resolution_bitrate_limits =
      GetDefaultSinglecastBitrateLimits(codec_type);
  return info.GetEncoderBitrateLimitsForResolution(frame_size_pixels);
}

EncoderInfoSettings::EncoderInfoSettings(std::string name)
    : requested_resolution_alignment_("requested_resolution_alignment"),
      apply_alignment_to_all_simulcast_layers_(
          "apply_alignment_to_all_simulcast_layers") {
  FieldTrialStructList<BitrateLimit> bitrate_limits(
      {FieldTrialStructMember(
           "frame_size_pixels",
           [](BitrateLimit* b) { return &b->frame_size_pixels; }),
       FieldTrialStructMember(
           "min_start_bitrate_bps",
           [](BitrateLimit* b) { return &b->min_start_bitrate_bps; }),
       FieldTrialStructMember(
           "min_bitrate_bps",
           [](BitrateLimit* b) { return &b->min_bitrate_bps; }),
       FieldTrialStructMember(
           "max_bitrate_bps",
           [](BitrateLimit* b) { return &b->max_bitrate_bps; })},
      {});

  if (field_trial::FindFullName(name).empty()) {
    // Encoder name not found, use common string applying to all encoders.
    name = "WebRTC-GetEncoderInfoOverride";
  }

  ParseFieldTrial({&bitrate_limits, &requested_resolution_alignment_,
                   &apply_alignment_to_all_simulcast_layers_},
                  field_trial::FindFullName(name));

  resolution_bitrate_limits_ = ToResolutionBitrateLimits(bitrate_limits.Get());
}

absl::optional<int> EncoderInfoSettings::requested_resolution_alignment()
    const {
  if (requested_resolution_alignment_ &&
      requested_resolution_alignment_.Value() < 1) {
    RTC_LOG(LS_WARNING) << "Unsupported alignment value, ignored.";
    return absl::nullopt;
  }
  return requested_resolution_alignment_.GetOptional();
}

EncoderInfoSettings::~EncoderInfoSettings() {}

SimulcastEncoderAdapterEncoderInfoSettings::
    SimulcastEncoderAdapterEncoderInfoSettings()
    : EncoderInfoSettings(
          "WebRTC-SimulcastEncoderAdapter-GetEncoderInfoOverride") {}

LibvpxVp8EncoderInfoSettings::LibvpxVp8EncoderInfoSettings()
    : EncoderInfoSettings("WebRTC-VP8-GetEncoderInfoOverride") {}

LibvpxVp9EncoderInfoSettings::LibvpxVp9EncoderInfoSettings()
    : EncoderInfoSettings("WebRTC-VP9-GetEncoderInfoOverride") {}

}  // namespace webrtc
