/*
 *  Copyright (c) 2014 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#ifndef MEDIA_ENGINE_SIMULCAST_H_
#define MEDIA_ENGINE_SIMULCAST_H_

#include <stddef.h>

#include <vector>

#include "api/transport/webrtc_key_value_config.h"
#include "api/units/data_rate.h"
#include "api/video_codecs/video_encoder_config.h"

namespace cricket {

// Gets the total maximum bitrate for the `streams`.
webrtc::DataRate GetTotalMaxBitrate(
    const std::vector<webrtc::VideoStream>& streams);

// Adds any bitrate of `max_bitrate` that is above the total maximum bitrate for
// the `layers` to the highest quality layer.
void BoostMaxSimulcastLayer(webrtc::DataRate max_bitrate,
                            std::vector<webrtc::VideoStream>* layers);

// Round size to nearest simulcast-friendly size
int NormalizeSimulcastSize(int size, size_t simulcast_layers);

// Gets simulcast settings.
std::vector<webrtc::VideoStream> GetSimulcastConfig(
    size_t min_layers,
    size_t max_layers,
    int width,
    int height,
    double bitrate_priority,
    int max_qp,
    bool is_screenshare_with_conference_mode,
    bool temporal_layers_supported,
    const webrtc::WebRtcKeyValueConfig& trials);

// Gets the simulcast config layers for a non-screensharing case.
std::vector<webrtc::VideoStream> GetNormalSimulcastLayers(
    size_t max_layers,
    int width,
    int height,
    double bitrate_priority,
    int max_qp,
    bool temporal_layers_supported,
    bool base_heavy_tl3_rate_alloc,
    const webrtc::WebRtcKeyValueConfig& trials);

// Gets simulcast config layers for screenshare settings.
std::vector<webrtc::VideoStream> GetScreenshareLayers(
    size_t max_layers,
    int width,
    int height,
    double bitrate_priority,
    int max_qp,
    bool temporal_layers_supported,
    bool base_heavy_tl3_rate_alloc,
    const webrtc::WebRtcKeyValueConfig& trials);

}  // namespace cricket

#endif  // MEDIA_ENGINE_SIMULCAST_H_
