/*
 *  Copyright (c) 2017 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "media/base/h264_profile_level_id.h"

// TODO(crbug.com/1187565): Remove this file once downstream projects stop
// depend on it.

namespace webrtc {
namespace H264 {

absl::optional<ProfileLevelId> ParseProfileLevelId(const char* str) {
  return webrtc::ParseH264ProfileLevelId(str);
}

absl::optional<ProfileLevelId> ParseSdpProfileLevelId(
    const SdpVideoFormat::Parameters& params) {
  return webrtc::ParseSdpForH264ProfileLevelId(params);
}

absl::optional<Level> SupportedLevel(int max_frame_pixel_count, float max_fps) {
  return webrtc::H264SupportedLevel(max_frame_pixel_count, max_fps);
}

absl::optional<std::string> ProfileLevelIdToString(
    const ProfileLevelId& profile_level_id) {
  return webrtc::H264ProfileLevelIdToString(profile_level_id);
}

bool IsSameH264Profile(const SdpVideoFormat::Parameters& params1,
                       const SdpVideoFormat::Parameters& params2) {
  return webrtc::H264IsSameProfile(params1, params2);
}

}  // namespace H264
}  // namespace webrtc
