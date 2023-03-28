/*
 *  Copyright (c) 2022 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "api/video_codecs/scalability_mode_helper.h"

#include "modules/video_coding/svc/scalability_mode_util.h"

namespace webrtc {

absl::optional<int> ScalabilityModeStringToNumSpatialLayers(
    absl::string_view scalability_mode_string) {
  absl::optional<ScalabilityMode> scalability_mode =
      ScalabilityModeFromString(scalability_mode_string);
  if (!scalability_mode.has_value()) {
    return absl::nullopt;
  }
  return ScalabilityModeToNumSpatialLayers(*scalability_mode);
}

absl::optional<int> ScalabilityModeStringToNumTemporalLayers(
    absl::string_view scalability_mode_string) {
  absl::optional<ScalabilityMode> scalability_mode =
      ScalabilityModeFromString(scalability_mode_string);
  if (!scalability_mode.has_value()) {
    return absl::nullopt;
  }
  return ScalabilityModeToNumTemporalLayers(*scalability_mode);
}

}  // namespace webrtc
