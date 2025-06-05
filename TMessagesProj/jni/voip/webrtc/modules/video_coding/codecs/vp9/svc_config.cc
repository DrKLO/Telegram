/*
 *  Copyright (c) 2018 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "modules/video_coding/codecs/vp9/svc_config.h"

#include <algorithm>
#include <cmath>
#include <memory>
#include <vector>

#include "media/base/video_common.h"
#include "modules/video_coding/codecs/vp9/include/vp9_globals.h"
#include "modules/video_coding/svc/create_scalability_structure.h"
#include "modules/video_coding/svc/scalability_mode_util.h"
#include "rtc_base/checks.h"
#include "rtc_base/logging.h"

namespace webrtc {

namespace {
const size_t kMinVp9SvcBitrateKbps = 30;

const size_t kMaxNumLayersForScreenSharing = 3;
const float kMaxScreenSharingLayerFramerateFps[] = {5.0, 10.0, 30.0};
const size_t kMinScreenSharingLayerBitrateKbps[] = {30, 200, 500};
const size_t kTargetScreenSharingLayerBitrateKbps[] = {150, 350, 950};
const size_t kMaxScreenSharingLayerBitrateKbps[] = {250, 500, 950};

// Gets limited number of layers for given resolution.
size_t GetLimitedNumSpatialLayers(size_t width, size_t height) {
  const bool is_landscape = width >= height;
  const size_t min_width = is_landscape ? kMinVp9SpatialLayerLongSideLength
                                        : kMinVp9SpatialLayerShortSideLength;
  const size_t min_height = is_landscape ? kMinVp9SpatialLayerShortSideLength
                                         : kMinVp9SpatialLayerLongSideLength;
  const size_t num_layers_fit_horz = static_cast<size_t>(
      std::floor(1 + std::max(0.0f, std::log2(1.0f * width / min_width))));
  const size_t num_layers_fit_vert = static_cast<size_t>(
      std::floor(1 + std::max(0.0f, std::log2(1.0f * height / min_height))));
  return std::min(num_layers_fit_horz, num_layers_fit_vert);
}
}  // namespace

std::vector<SpatialLayer> ConfigureSvcScreenSharing(size_t input_width,
                                                    size_t input_height,
                                                    float max_framerate_fps,
                                                    size_t num_spatial_layers) {
  num_spatial_layers =
      std::min(num_spatial_layers, kMaxNumLayersForScreenSharing);
  std::vector<SpatialLayer> spatial_layers;

  for (size_t sl_idx = 0; sl_idx < num_spatial_layers; ++sl_idx) {
    SpatialLayer spatial_layer = {0};
    spatial_layer.width = input_width;
    spatial_layer.height = input_height;
    spatial_layer.maxFramerate =
        std::min(kMaxScreenSharingLayerFramerateFps[sl_idx], max_framerate_fps);
    spatial_layer.numberOfTemporalLayers = 1;
    spatial_layer.minBitrate =
        static_cast<int>(kMinScreenSharingLayerBitrateKbps[sl_idx]);
    spatial_layer.maxBitrate =
        static_cast<int>(kMaxScreenSharingLayerBitrateKbps[sl_idx]);
    spatial_layer.targetBitrate =
        static_cast<int>(kTargetScreenSharingLayerBitrateKbps[sl_idx]);
    spatial_layer.active = true;
    spatial_layers.push_back(spatial_layer);
  }

  return spatial_layers;
}

std::vector<SpatialLayer> ConfigureSvcNormalVideo(
    size_t input_width,
    size_t input_height,
    float max_framerate_fps,
    size_t first_active_layer,
    size_t num_spatial_layers,
    size_t num_temporal_layers,
    absl::optional<ScalableVideoController::StreamLayersConfig> config) {
  RTC_DCHECK_LT(first_active_layer, num_spatial_layers);

  // Limit number of layers for given resolution.
  size_t limited_num_spatial_layers =
      GetLimitedNumSpatialLayers(input_width, input_height);
  if (limited_num_spatial_layers < num_spatial_layers) {
    RTC_LOG(LS_WARNING) << "Reducing number of spatial layers from "
                        << num_spatial_layers << " to "
                        << limited_num_spatial_layers
                        << " due to low input resolution.";
    num_spatial_layers = limited_num_spatial_layers;
  }

  // First active layer must be configured.
  num_spatial_layers = std::max(num_spatial_layers, first_active_layer + 1);

  // Ensure top layer is even enough.
  int required_divisiblity = 1 << (num_spatial_layers - first_active_layer - 1);
  if (config) {
    required_divisiblity = 1;
    for (size_t sl_idx = 0; sl_idx < num_spatial_layers; ++sl_idx) {
      required_divisiblity = cricket::LeastCommonMultiple(
          required_divisiblity, config->scaling_factor_den[sl_idx]);
    }
  }
  input_width = input_width - input_width % required_divisiblity;
  input_height = input_height - input_height % required_divisiblity;

  std::vector<SpatialLayer> spatial_layers;
  for (size_t sl_idx = first_active_layer; sl_idx < num_spatial_layers;
       ++sl_idx) {
    SpatialLayer spatial_layer = {0};
    spatial_layer.width = input_width >> (num_spatial_layers - sl_idx - 1);
    spatial_layer.height = input_height >> (num_spatial_layers - sl_idx - 1);
    spatial_layer.maxFramerate = max_framerate_fps;
    spatial_layer.numberOfTemporalLayers = num_temporal_layers;
    spatial_layer.active = true;

    if (config) {
      spatial_layer.width = input_width * config->scaling_factor_num[sl_idx] /
                            config->scaling_factor_den[sl_idx];
      spatial_layer.height = input_height * config->scaling_factor_num[sl_idx] /
                             config->scaling_factor_den[sl_idx];
    }

    // minBitrate and maxBitrate formulas were derived from
    // subjective-quality data to determing bit rates below which video
    // quality is unacceptable and above which additional bits do not provide
    // benefit. The formulas express rate in units of kbps.

    // TODO(ssilkin): Add to the comment PSNR/SSIM we get at encoding certain
    // video to min/max bitrate specified by those formulas.
    const size_t num_pixels = spatial_layer.width * spatial_layer.height;
    int min_bitrate =
        static_cast<int>((600. * std::sqrt(num_pixels) - 95000.) / 1000.);
    min_bitrate = std::max(min_bitrate, 0);
    spatial_layer.minBitrate =
        std::max(static_cast<size_t>(min_bitrate), kMinVp9SvcBitrateKbps);
    spatial_layer.maxBitrate =
        static_cast<int>((1.6 * num_pixels + 50 * 1000) / 1000);
    spatial_layer.targetBitrate =
        (spatial_layer.minBitrate + spatial_layer.maxBitrate) / 2;
    spatial_layers.push_back(spatial_layer);
  }

  // A workaround for situation when single HD layer is left with minBitrate
  // about 500kbps. This would mean that there will always be at least 500kbps
  // allocated to video regardless of how low is the actual BWE.
  // Also, boost maxBitrate for the first layer to account for lost ability to
  // predict from previous layers.
  if (first_active_layer > 0) {
    spatial_layers[0].minBitrate = kMinVp9SvcBitrateKbps;
    // TODO(ilnik): tune this value or come up with a different formula to
    // ensure that all singlecast configurations look good and not too much
    // bitrate is added.
    spatial_layers[0].maxBitrate *= 1.1;
  }

  return spatial_layers;
}

// Uses scalability mode to configure spatial layers.
std::vector<SpatialLayer> GetVp9SvcConfig(VideoCodec& codec) {
  RTC_DCHECK_EQ(codec.codecType, kVideoCodecVP9);

  absl::optional<ScalabilityMode> scalability_mode = codec.GetScalabilityMode();
  RTC_DCHECK(scalability_mode.has_value());

  bool requested_single_spatial_layer =
      ScalabilityModeToNumSpatialLayers(*scalability_mode) == 1;

  // Limit number of spatial layers for given resolution.
  int limited_num_spatial_layers =
      GetLimitedNumSpatialLayers(codec.width, codec.height);
  if (limited_num_spatial_layers <
      ScalabilityModeToNumSpatialLayers(*scalability_mode)) {
    ScalabilityMode limited_scalability_mode =
        LimitNumSpatialLayers(*scalability_mode, limited_num_spatial_layers);
    RTC_LOG(LS_WARNING)
        << "Reducing number of spatial layers due to low input resolution: "
        << ScalabilityModeToString(*scalability_mode) << " to "
        << ScalabilityModeToString(limited_scalability_mode);
    scalability_mode = limited_scalability_mode;
    codec.SetScalabilityMode(limited_scalability_mode);
  }

  codec.VP9()->interLayerPred =
      ScalabilityModeToInterLayerPredMode(*scalability_mode);

  absl::optional<ScalableVideoController::StreamLayersConfig> info =
      ScalabilityStructureConfig(*scalability_mode);
  if (!info.has_value()) {
    RTC_LOG(LS_WARNING) << "Failed to create structure "
                        << ScalabilityModeToString(*scalability_mode);
    return {};
  }

  // TODO(bugs.webrtc.org/11607): Add support for screensharing.
  std::vector<SpatialLayer> spatial_layers =
      GetSvcConfig(codec.width, codec.height, codec.maxFramerate,
                   /*first_active_layer=*/0, info->num_spatial_layers,
                   info->num_temporal_layers, /*is_screen_sharing=*/false,
                   codec.GetScalabilityMode() ? info : absl::nullopt);
  RTC_DCHECK(!spatial_layers.empty());

  spatial_layers[0].minBitrate = kMinVp9SvcBitrateKbps;

  // Use codec bitrate limits if spatial layering is not requested.
  if (requested_single_spatial_layer) {
    SpatialLayer& spatial_layer = spatial_layers[0];
    spatial_layer.minBitrate = codec.minBitrate;
    spatial_layer.maxBitrate = codec.maxBitrate;
    spatial_layer.targetBitrate = codec.maxBitrate;
  }

  return spatial_layers;
}

std::vector<SpatialLayer> GetSvcConfig(
    size_t input_width,
    size_t input_height,
    float max_framerate_fps,
    size_t first_active_layer,
    size_t num_spatial_layers,
    size_t num_temporal_layers,
    bool is_screen_sharing,
    absl::optional<ScalableVideoController::StreamLayersConfig> config) {
  RTC_DCHECK_GT(input_width, 0);
  RTC_DCHECK_GT(input_height, 0);
  RTC_DCHECK_GT(num_spatial_layers, 0);
  RTC_DCHECK_GT(num_temporal_layers, 0);

  if (is_screen_sharing) {
    return ConfigureSvcScreenSharing(input_width, input_height,
                                     max_framerate_fps, num_spatial_layers);
  } else {
    return ConfigureSvcNormalVideo(input_width, input_height, max_framerate_fps,
                                   first_active_layer, num_spatial_layers,
                                   num_temporal_layers, config);
  }
}

}  // namespace webrtc
