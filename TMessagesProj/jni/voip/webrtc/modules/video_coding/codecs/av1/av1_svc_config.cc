/*
 *  Copyright (c) 2020 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "modules/video_coding/codecs/av1/av1_svc_config.h"

#include <algorithm>
#include <cmath>
#include <memory>

#include "modules/video_coding/svc/create_scalability_structure.h"
#include "modules/video_coding/svc/scalable_video_controller.h"
#include "rtc_base/checks.h"
#include "rtc_base/logging.h"

namespace webrtc {

bool SetAv1SvcConfig(VideoCodec& video_codec) {
  RTC_DCHECK_EQ(video_codec.codecType, kVideoCodecAV1);

  absl::string_view scalability_mode = video_codec.ScalabilityMode();
  if (scalability_mode.empty()) {
    RTC_LOG(LS_WARNING) << "Scalability mode is not set, using 'NONE'.";
    scalability_mode = "NONE";
  }

  std::unique_ptr<ScalableVideoController> structure =
      CreateScalabilityStructure(scalability_mode);
  if (structure == nullptr) {
    RTC_LOG(LS_WARNING) << "Failed to create structure " << scalability_mode;
    return false;
  }

  ScalableVideoController::StreamLayersConfig info = structure->StreamConfig();
  for (int sl_idx = 0; sl_idx < info.num_spatial_layers; ++sl_idx) {
    SpatialLayer& spatial_layer = video_codec.spatialLayers[sl_idx];
    spatial_layer.width = video_codec.width * info.scaling_factor_num[sl_idx] /
                          info.scaling_factor_den[sl_idx];
    spatial_layer.height = video_codec.height *
                           info.scaling_factor_num[sl_idx] /
                           info.scaling_factor_den[sl_idx];
    spatial_layer.maxFramerate = video_codec.maxFramerate;
    spatial_layer.numberOfTemporalLayers = info.num_temporal_layers;
    spatial_layer.active = true;
  }

  if (info.num_spatial_layers == 1) {
    SpatialLayer& spatial_layer = video_codec.spatialLayers[0];
    spatial_layer.minBitrate = video_codec.minBitrate;
    spatial_layer.maxBitrate = video_codec.maxBitrate;
    spatial_layer.targetBitrate =
        (video_codec.minBitrate + video_codec.maxBitrate) / 2;
    return true;
  }

  for (int sl_idx = 0; sl_idx < info.num_spatial_layers; ++sl_idx) {
    SpatialLayer& spatial_layer = video_codec.spatialLayers[sl_idx];
    // minBitrate and maxBitrate formulas are copied from vp9 settings and
    // are not yet tuned for av1.
    const int num_pixels = spatial_layer.width * spatial_layer.height;
    int min_bitrate_kbps = (600.0 * std::sqrt(num_pixels) - 95'000.0) / 1000.0;
    spatial_layer.minBitrate = std::max(min_bitrate_kbps, 20);
    spatial_layer.maxBitrate = 50 + static_cast<int>(1.6 * num_pixels / 1000.0);
    spatial_layer.targetBitrate =
        (spatial_layer.minBitrate + spatial_layer.maxBitrate) / 2;
  }
  return true;
}

}  // namespace webrtc
