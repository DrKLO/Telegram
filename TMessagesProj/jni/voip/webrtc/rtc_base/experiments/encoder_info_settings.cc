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

#include "absl/strings/string_view.h"
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
constexpr float kDefaultMinBitratebps = 30000;
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

// Return the suitable bitrate limits for specified resolution when qp is
// untrusted, they are experimental values.
// TODO(bugs.webrtc.org/12942): Maybe we need to add other codecs(VP8/VP9)
// experimental values.
std::vector<VideoEncoder::ResolutionBitrateLimits>
EncoderInfoSettings::GetDefaultSinglecastBitrateLimitsWhenQpIsUntrusted() {
  // Specific limits for H264/AVC
  return {{0 * 0, 0, 0, 0},
          {320 * 180, 0, 30000, 300000},
          {480 * 270, 300000, 30000, 500000},
          {640 * 360, 500000, 30000, 800000},
          {960 * 540, 800000, 30000, 1500000},
          {1280 * 720, 1500000, 30000, 2500000},
          {1920 * 1080, 2500000, 30000, 4000000}};
}

// Through linear interpolation, return the bitrate limit corresponding to the
// specified |frame_size_pixels|.
absl::optional<VideoEncoder::ResolutionBitrateLimits>
EncoderInfoSettings::GetSinglecastBitrateLimitForResolutionWhenQpIsUntrusted(
    absl::optional<int> frame_size_pixels,
    const std::vector<VideoEncoder::ResolutionBitrateLimits>&
        resolution_bitrate_limits) {
  if (!frame_size_pixels.has_value() || frame_size_pixels.value() <= 0) {
    return absl::nullopt;
  }

  std::vector<VideoEncoder::ResolutionBitrateLimits> bitrate_limits =
      resolution_bitrate_limits;

  // Sort the list of bitrate limits by resolution.
  sort(bitrate_limits.begin(), bitrate_limits.end(),
       [](const VideoEncoder::ResolutionBitrateLimits& lhs,
          const VideoEncoder::ResolutionBitrateLimits& rhs) {
         return lhs.frame_size_pixels < rhs.frame_size_pixels;
       });

  if (bitrate_limits.empty()) {
    return absl::nullopt;
  }

  int interpolation_index = -1;
  for (size_t i = 0; i < bitrate_limits.size(); ++i) {
    if (bitrate_limits[i].frame_size_pixels >= frame_size_pixels.value()) {
      interpolation_index = i;
      break;
    }
  }

  // -1 means that the maximum resolution is exceeded, we will select the
  // largest data as the return result.
  if (interpolation_index == -1) {
    return *bitrate_limits.rbegin();
  }

  // If we have a matching resolution, return directly without interpolation.
  if (bitrate_limits[interpolation_index].frame_size_pixels ==
      frame_size_pixels.value()) {
    return bitrate_limits[interpolation_index];
  }

  // No matching resolution, do a linear interpolate.
  int lower_pixel_count =
      bitrate_limits[interpolation_index - 1].frame_size_pixels;
  int upper_pixel_count = bitrate_limits[interpolation_index].frame_size_pixels;
  float alpha = (frame_size_pixels.value() - lower_pixel_count) * 1.0 /
                (upper_pixel_count - lower_pixel_count);
  int min_start_bitrate_bps = static_cast<int>(
      bitrate_limits[interpolation_index].min_start_bitrate_bps * alpha +
      bitrate_limits[interpolation_index - 1].min_start_bitrate_bps *
          (1.0 - alpha));
  int max_bitrate_bps = static_cast<int>(
      bitrate_limits[interpolation_index].max_bitrate_bps * alpha +
      bitrate_limits[interpolation_index - 1].max_bitrate_bps * (1.0 - alpha));

  if (max_bitrate_bps >= min_start_bitrate_bps) {
    return VideoEncoder::ResolutionBitrateLimits(
        frame_size_pixels.value(), min_start_bitrate_bps, kDefaultMinBitratebps,
        max_bitrate_bps);
  } else {
    RTC_LOG(LS_WARNING)
        << "BitRate interpolation calculating result is abnormal. "
        << " lower_pixel_count = " << lower_pixel_count
        << " upper_pixel_count = " << upper_pixel_count
        << " frame_size_pixels = " << frame_size_pixels.value()
        << " min_start_bitrate_bps = " << min_start_bitrate_bps
        << " min_bitrate_bps = " << kDefaultMinBitratebps
        << " max_bitrate_bps = " << max_bitrate_bps;
    return absl::nullopt;
  }
}

EncoderInfoSettings::EncoderInfoSettings(absl::string_view name)
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

  std::string name_str(name);
  if (field_trial::FindFullName(name_str).empty()) {
    // Encoder name not found, use common string applying to all encoders.
    name_str = "WebRTC-GetEncoderInfoOverride";
  }

  ParseFieldTrial({&bitrate_limits, &requested_resolution_alignment_,
                   &apply_alignment_to_all_simulcast_layers_},
                  field_trial::FindFullName(name_str));

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
