/*
 *  Copyright (c) 2017 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "api/video_codecs/video_encoder.h"

#include <string.h>
#include <algorithm>

#include "rtc_base/checks.h"
#include "rtc_base/strings/string_builder.h"

namespace webrtc {

// TODO(mflodman): Add default complexity for VP9 and VP9.
VideoCodecVP8 VideoEncoder::GetDefaultVp8Settings() {
  VideoCodecVP8 vp8_settings;
  memset(&vp8_settings, 0, sizeof(vp8_settings));

  vp8_settings.numberOfTemporalLayers = 1;
  vp8_settings.denoisingOn = true;
  vp8_settings.automaticResizeOn = false;
  vp8_settings.keyFrameInterval = 3000;

  return vp8_settings;
}

VideoCodecVP9 VideoEncoder::GetDefaultVp9Settings() {
  VideoCodecVP9 vp9_settings;
  memset(&vp9_settings, 0, sizeof(vp9_settings));

  vp9_settings.numberOfTemporalLayers = 1;
  vp9_settings.denoisingOn = true;
  vp9_settings.keyFrameInterval = 3000;
  vp9_settings.adaptiveQpMode = true;
  vp9_settings.automaticResizeOn = true;
  vp9_settings.numberOfSpatialLayers = 1;
  vp9_settings.flexibleMode = false;
  vp9_settings.interLayerPred = InterLayerPredMode::kOn;

  return vp9_settings;
}

VideoCodecH264 VideoEncoder::GetDefaultH264Settings() {
  VideoCodecH264 h264_settings;
  memset(&h264_settings, 0, sizeof(h264_settings));

  h264_settings.keyFrameInterval = 3000;
  h264_settings.numberOfTemporalLayers = 1;

  return h264_settings;
}

#ifndef DISABLE_H265
VideoCodecH265 VideoEncoder::GetDefaultH265Settings() {
  VideoCodecH265 h265_settings;
  memset(&h265_settings, 0, sizeof(h265_settings));

  // h265_settings.profile = kProfileBase;
  h265_settings.frameDroppingOn = true;
  h265_settings.keyFrameInterval = 3000;
  h265_settings.spsData = nullptr;
  h265_settings.spsLen = 0;
  h265_settings.ppsData = nullptr;
  h265_settings.ppsLen = 0;

  return h265_settings;
}
#endif

VideoEncoder::ScalingSettings::ScalingSettings() = default;

VideoEncoder::ScalingSettings::ScalingSettings(KOff) : ScalingSettings() {}

VideoEncoder::ScalingSettings::ScalingSettings(int low, int high)
    : thresholds(QpThresholds(low, high)) {}

VideoEncoder::ScalingSettings::ScalingSettings(int low,
                                               int high,
                                               int min_pixels)
    : thresholds(QpThresholds(low, high)), min_pixels_per_frame(min_pixels) {}

VideoEncoder::ScalingSettings::ScalingSettings(const ScalingSettings&) =
    default;

VideoEncoder::ScalingSettings::~ScalingSettings() {}

// static
constexpr VideoEncoder::ScalingSettings::KOff
    VideoEncoder::ScalingSettings::kOff;
// static
constexpr uint8_t VideoEncoder::EncoderInfo::kMaxFramerateFraction;

bool VideoEncoder::ResolutionBitrateLimits::operator==(
    const ResolutionBitrateLimits& rhs) const {
  return frame_size_pixels == rhs.frame_size_pixels &&
         min_start_bitrate_bps == rhs.min_start_bitrate_bps &&
         min_bitrate_bps == rhs.min_bitrate_bps &&
         max_bitrate_bps == rhs.max_bitrate_bps;
}

VideoEncoder::EncoderInfo::EncoderInfo()
    : scaling_settings(VideoEncoder::ScalingSettings::kOff),
      requested_resolution_alignment(1),
      apply_alignment_to_all_simulcast_layers(false),
      supports_native_handle(false),
      implementation_name("unknown"),
      has_trusted_rate_controller(false),
      is_hardware_accelerated(true),
      fps_allocation{absl::InlinedVector<uint8_t, kMaxTemporalStreams>(
          1,
          kMaxFramerateFraction)},
      supports_simulcast(false),
      preferred_pixel_formats{VideoFrameBuffer::Type::kI420} {}

VideoEncoder::EncoderInfo::EncoderInfo(const EncoderInfo&) = default;

VideoEncoder::EncoderInfo::~EncoderInfo() = default;

std::string VideoEncoder::EncoderInfo::ToString() const {
  char string_buf[2048];
  rtc::SimpleStringBuilder oss(string_buf);

  oss << "EncoderInfo { "
         "ScalingSettings { ";
  if (scaling_settings.thresholds) {
    oss << "Thresholds { "
           "low = "
        << scaling_settings.thresholds->low
        << ", high = " << scaling_settings.thresholds->high << "}, ";
  }
  oss << "min_pixels_per_frame = " << scaling_settings.min_pixels_per_frame
      << " }";
  oss << ", requested_resolution_alignment = " << requested_resolution_alignment
      << ", apply_alignment_to_all_simulcast_layers = "
      << apply_alignment_to_all_simulcast_layers
      << ", supports_native_handle = " << supports_native_handle
      << ", implementation_name = '" << implementation_name
      << "'"
         ", has_trusted_rate_controller = "
      << has_trusted_rate_controller
      << ", is_hardware_accelerated = " << is_hardware_accelerated
      << ", fps_allocation = [";
  size_t num_spatial_layer_with_fps_allocation = 0;
  for (size_t i = 0; i < kMaxSpatialLayers; ++i) {
    if (!fps_allocation[i].empty()) {
      num_spatial_layer_with_fps_allocation = i + 1;
    }
  }
  bool first = true;
  for (size_t i = 0; i < num_spatial_layer_with_fps_allocation; ++i) {
    if (fps_allocation[i].empty()) {
      break;
    }
    if (!first) {
      oss << ", ";
    }
    const absl::InlinedVector<uint8_t, kMaxTemporalStreams>& fractions =
        fps_allocation[i];
    if (!fractions.empty()) {
      first = false;
      oss << "[ ";
      for (size_t i = 0; i < fractions.size(); ++i) {
        if (i > 0) {
          oss << ", ";
        }
        oss << (static_cast<double>(fractions[i]) / kMaxFramerateFraction);
      }
      oss << "] ";
    }
  }
  oss << "]";
  oss << ", resolution_bitrate_limits = [";
  for (size_t i = 0; i < resolution_bitrate_limits.size(); ++i) {
    if (i > 0) {
      oss << ", ";
    }
    ResolutionBitrateLimits l = resolution_bitrate_limits[i];
    oss << "Limits { "
           "frame_size_pixels = "
        << l.frame_size_pixels
        << ", min_start_bitrate_bps = " << l.min_start_bitrate_bps
        << ", min_bitrate_bps = " << l.min_bitrate_bps
        << ", max_bitrate_bps = " << l.max_bitrate_bps << "} ";
  }
  oss << "] "
         ", supports_simulcast = "
      << supports_simulcast;
  oss << ", preferred_pixel_formats = [";
  for (size_t i = 0; i < preferred_pixel_formats.size(); ++i) {
    if (i > 0)
      oss << ", ";
    oss << VideoFrameBufferTypeToString(preferred_pixel_formats.at(i));
  }
  oss << "]";
  if (is_qp_trusted.has_value()) {
    oss << ", is_qp_trusted = " << is_qp_trusted.value();
  }
  oss << "}";
  return oss.str();
}

bool VideoEncoder::EncoderInfo::operator==(const EncoderInfo& rhs) const {
  if (scaling_settings.thresholds.has_value() !=
      rhs.scaling_settings.thresholds.has_value()) {
    return false;
  }
  if (scaling_settings.thresholds.has_value()) {
    QpThresholds l = *scaling_settings.thresholds;
    QpThresholds r = *rhs.scaling_settings.thresholds;
    if (l.low != r.low || l.high != r.high) {
      return false;
    }
  }
  if (scaling_settings.min_pixels_per_frame !=
      rhs.scaling_settings.min_pixels_per_frame) {
    return false;
  }

  if (supports_native_handle != rhs.supports_native_handle ||
      implementation_name != rhs.implementation_name ||
      has_trusted_rate_controller != rhs.has_trusted_rate_controller ||
      is_hardware_accelerated != rhs.is_hardware_accelerated) {
    return false;
  }

  for (size_t i = 0; i < kMaxSpatialLayers; ++i) {
    if (fps_allocation[i] != rhs.fps_allocation[i]) {
      return false;
    }
  }

  if (resolution_bitrate_limits != rhs.resolution_bitrate_limits ||
      supports_simulcast != rhs.supports_simulcast) {
    return false;
  }

  return true;
}

absl::optional<VideoEncoder::ResolutionBitrateLimits>
VideoEncoder::EncoderInfo::GetEncoderBitrateLimitsForResolution(
    int frame_size_pixels) const {
  std::vector<ResolutionBitrateLimits> bitrate_limits =
      resolution_bitrate_limits;

  // Sort the list of bitrate limits by resolution.
  sort(bitrate_limits.begin(), bitrate_limits.end(),
       [](const ResolutionBitrateLimits& lhs,
          const ResolutionBitrateLimits& rhs) {
         return lhs.frame_size_pixels < rhs.frame_size_pixels;
       });

  for (size_t i = 0; i < bitrate_limits.size(); ++i) {
    RTC_DCHECK_GE(bitrate_limits[i].min_bitrate_bps, 0);
    RTC_DCHECK_GE(bitrate_limits[i].min_start_bitrate_bps, 0);
    RTC_DCHECK_GE(bitrate_limits[i].max_bitrate_bps,
                  bitrate_limits[i].min_bitrate_bps);
    if (i > 0) {
      // The bitrate limits aren't expected to decrease with resolution.
      RTC_DCHECK_GE(bitrate_limits[i].min_bitrate_bps,
                    bitrate_limits[i - 1].min_bitrate_bps);
      RTC_DCHECK_GE(bitrate_limits[i].min_start_bitrate_bps,
                    bitrate_limits[i - 1].min_start_bitrate_bps);
      RTC_DCHECK_GE(bitrate_limits[i].max_bitrate_bps,
                    bitrate_limits[i - 1].max_bitrate_bps);
    }

    if (bitrate_limits[i].frame_size_pixels >= frame_size_pixels) {
      return absl::optional<ResolutionBitrateLimits>(bitrate_limits[i]);
    }
  }

  return absl::nullopt;
}

VideoEncoder::RateControlParameters::RateControlParameters()
    : bitrate(VideoBitrateAllocation()),
      framerate_fps(0.0),
      bandwidth_allocation(DataRate::Zero()) {}

VideoEncoder::RateControlParameters::RateControlParameters(
    const VideoBitrateAllocation& bitrate,
    double framerate_fps)
    : bitrate(bitrate),
      framerate_fps(framerate_fps),
      bandwidth_allocation(DataRate::BitsPerSec(bitrate.get_sum_bps())) {}

VideoEncoder::RateControlParameters::RateControlParameters(
    const VideoBitrateAllocation& bitrate,
    double framerate_fps,
    DataRate bandwidth_allocation)
    : bitrate(bitrate),
      framerate_fps(framerate_fps),
      bandwidth_allocation(bandwidth_allocation) {}

bool VideoEncoder::RateControlParameters::operator==(
    const VideoEncoder::RateControlParameters& rhs) const {
  return std::tie(bitrate, framerate_fps, bandwidth_allocation) ==
         std::tie(rhs.bitrate, rhs.framerate_fps, rhs.bandwidth_allocation);
}

bool VideoEncoder::RateControlParameters::operator!=(
    const VideoEncoder::RateControlParameters& rhs) const {
  return !(rhs == *this);
}

VideoEncoder::RateControlParameters::~RateControlParameters() = default;

void VideoEncoder::SetFecControllerOverride(
    FecControllerOverride* fec_controller_override) {}

int32_t VideoEncoder::InitEncode(const VideoCodec* codec_settings,
                                 int32_t number_of_cores,
                                 size_t max_payload_size) {
  const VideoEncoder::Capabilities capabilities(/* loss_notification= */ false);
  const VideoEncoder::Settings settings(capabilities, number_of_cores,
                                        max_payload_size);
  // In theory, this and the other version of InitEncode() could end up calling
  // each other in a loop until we get a stack overflow.
  // In practice, any subclass of VideoEncoder would overload at least one
  // of these, and we have a TODO in the header file to make this pure virtual.
  return InitEncode(codec_settings, settings);
}

int VideoEncoder::InitEncode(const VideoCodec* codec_settings,
                             const VideoEncoder::Settings& settings) {
  // In theory, this and the other version of InitEncode() could end up calling
  // each other in a loop until we get a stack overflow.
  // In practice, any subclass of VideoEncoder would overload at least one
  // of these, and we have a TODO in the header file to make this pure virtual.
  return InitEncode(codec_settings, settings.number_of_cores,
                    settings.max_payload_size);
}

void VideoEncoder::OnPacketLossRateUpdate(float packet_loss_rate) {}

void VideoEncoder::OnRttUpdate(int64_t rtt_ms) {}

void VideoEncoder::OnLossNotification(
    const LossNotification& loss_notification) {}

// TODO(webrtc:9722): Remove and make pure virtual.
VideoEncoder::EncoderInfo VideoEncoder::GetEncoderInfo() const {
  return EncoderInfo();
}

}  // namespace webrtc
