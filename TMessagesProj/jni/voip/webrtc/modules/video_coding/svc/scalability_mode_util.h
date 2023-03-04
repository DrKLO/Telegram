/*
 *  Copyright (c) 2022 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#ifndef MODULES_VIDEO_CODING_SVC_SCALABILITY_MODE_UTIL_H_
#define MODULES_VIDEO_CODING_SVC_SCALABILITY_MODE_UTIL_H_

#include "absl/strings/string_view.h"
#include "absl/types/optional.h"
#include "api/video_codecs/scalability_mode.h"
#include "api/video_codecs/video_codec.h"

namespace webrtc {

enum class ScalabilityModeResolutionRatio {
  kTwoToOne,    // The resolution ratio between spatial layers is 2:1.
  kThreeToTwo,  // The resolution ratio between spatial layers is 1.5:1.
};

absl::optional<ScalabilityMode> ScalabilityModeFromString(
    absl::string_view scalability_mode_string);

InterLayerPredMode ScalabilityModeToInterLayerPredMode(
    ScalabilityMode scalability_mode);

int ScalabilityModeToNumSpatialLayers(ScalabilityMode scalability_mode);

int ScalabilityModeToNumTemporalLayers(ScalabilityMode scalability_mode);

absl::optional<ScalabilityModeResolutionRatio> ScalabilityModeToResolutionRatio(
    ScalabilityMode scalability_mode);

ScalabilityMode LimitNumSpatialLayers(ScalabilityMode scalability_mode,
                                      int max_spatial_layers);

}  // namespace webrtc

#endif  // MODULES_VIDEO_CODING_SVC_SCALABILITY_MODE_UTIL_H_
