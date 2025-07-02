/*
 *  Copyright (c) 2018 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "modules/video_coding/utility/simulcast_utility.h"

#include <algorithm>
#include <cmath>

#include "modules/video_coding/svc/scalability_mode_util.h"
#include "rtc_base/checks.h"

namespace webrtc {

uint32_t SimulcastUtility::SumStreamMaxBitrate(int streams,
                                               const VideoCodec& codec) {
  uint32_t bitrate_sum = 0;
  for (int i = 0; i < streams; ++i) {
    bitrate_sum += codec.simulcastStream[i].maxBitrate;
  }
  return bitrate_sum;
}

int SimulcastUtility::NumberOfSimulcastStreams(const VideoCodec& codec) {
  int streams =
      codec.numberOfSimulcastStreams < 1 ? 1 : codec.numberOfSimulcastStreams;
  uint32_t simulcast_max_bitrate = SumStreamMaxBitrate(streams, codec);
  if (simulcast_max_bitrate == 0) {
    streams = 1;
  }
  return streams;
}

bool SimulcastUtility::ValidSimulcastParameters(const VideoCodec& codec,
                                                int num_streams) {
  // Check resolution.
  if (codec.width != codec.simulcastStream[num_streams - 1].width ||
      codec.height != codec.simulcastStream[num_streams - 1].height) {
    return false;
  }
  for (int i = 0; i < num_streams; ++i) {
    if (codec.width * codec.simulcastStream[i].height !=
        codec.height * codec.simulcastStream[i].width) {
      return false;
    }
  }
  for (int i = 1; i < num_streams; ++i) {
    if (codec.simulcastStream[i].width < codec.simulcastStream[i - 1].width) {
      return false;
    }
  }

  // Check frame-rate.
  for (int i = 1; i < num_streams; ++i) {
    if (fabs(codec.simulcastStream[i].maxFramerate -
             codec.simulcastStream[i - 1].maxFramerate) > 1e-9) {
      return false;
    }
  }

  // Check temporal layers.
  for (int i = 0; i < num_streams - 1; ++i) {
    if (codec.simulcastStream[i].numberOfTemporalLayers !=
        codec.simulcastStream[i + 1].numberOfTemporalLayers)
      return false;
  }
  return true;
}

bool SimulcastUtility::IsConferenceModeScreenshare(const VideoCodec& codec) {
  return codec.mode == VideoCodecMode::kScreensharing &&
         codec.legacy_conference_mode;
}

int SimulcastUtility::NumberOfTemporalLayers(const VideoCodec& codec,
                                             int spatial_id) {
  int num_temporal_layers = 0;
  if (auto scalability_mode = codec.GetScalabilityMode(); scalability_mode) {
    num_temporal_layers = ScalabilityModeToNumTemporalLayers(*scalability_mode);
  } else {
    switch (codec.codecType) {
      case kVideoCodecVP8:
        num_temporal_layers = codec.VP8().numberOfTemporalLayers;
        break;
      case kVideoCodecVP9:
        num_temporal_layers = codec.VP9().numberOfTemporalLayers;
        break;
      case kVideoCodecH264:
        num_temporal_layers = codec.H264().numberOfTemporalLayers;
        break;
      case kVideoCodecH265:
        // TODO(bugs.webrtc.org/13485)
        break;
      default:
        break;
    }
  }

  if (codec.numberOfSimulcastStreams > 0) {
    RTC_DCHECK_LT(spatial_id, codec.numberOfSimulcastStreams);
    num_temporal_layers =
        std::max(num_temporal_layers,
                 static_cast<int>(
                     codec.simulcastStream[spatial_id].numberOfTemporalLayers));
  }
  return std::max(1, num_temporal_layers);
}

}  // namespace webrtc
