/*
 *  Copyright (c) 2017 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#ifndef MEDIA_BASE_H264_PROFILE_LEVEL_ID_H_
#define MEDIA_BASE_H264_PROFILE_LEVEL_ID_H_

#include <string>

#include "api/video_codecs/h264_profile_level_id.h"

// TODO(crbug.com/1187565): Remove this file once downstream projects stop
// depend on it.

namespace webrtc {
namespace H264 {

typedef H264Profile Profile;
typedef H264Level Level;
typedef H264ProfileLevelId ProfileLevelId;

constexpr H264Profile kProfileConstrainedBaseline =
    H264Profile::kProfileConstrainedBaseline;
constexpr H264Profile kProfileBaseline = H264Profile::kProfileBaseline;
constexpr H264Profile kProfileMain = H264Profile::kProfileMain;
constexpr H264Profile kProfileConstrainedHigh =
    H264Profile::kProfileConstrainedHigh;
constexpr H264Profile kProfileHigh = H264Profile::kProfileHigh;

constexpr H264Level kLevel1_b = H264Level::kLevel1_b;
constexpr H264Level kLevel1 = H264Level::kLevel1;
constexpr H264Level kLevel1_1 = H264Level::kLevel1_1;
constexpr H264Level kLevel1_2 = H264Level::kLevel1_2;
constexpr H264Level kLevel1_3 = H264Level::kLevel1_3;
constexpr H264Level kLevel2 = H264Level::kLevel2;
constexpr H264Level kLevel2_1 = H264Level::kLevel2_1;
constexpr H264Level kLevel2_2 = H264Level::kLevel2_2;
constexpr H264Level kLevel3 = H264Level::kLevel3;
constexpr H264Level kLevel3_1 = H264Level::kLevel3_1;
constexpr H264Level kLevel3_2 = H264Level::kLevel3_2;
constexpr H264Level kLevel4 = H264Level::kLevel4;
constexpr H264Level kLevel4_1 = H264Level::kLevel4_1;
constexpr H264Level kLevel4_2 = H264Level::kLevel4_2;
constexpr H264Level kLevel5 = H264Level::kLevel5;
constexpr H264Level kLevel5_1 = H264Level::kLevel5_1;
constexpr H264Level kLevel5_2 = H264Level::kLevel5_2;

// Parse profile level id that is represented as a string of 3 hex bytes.
// Nothing will be returned if the string is not a recognized H264
// profile level id.
absl::optional<ProfileLevelId> ParseProfileLevelId(const char* str);

// Parse profile level id that is represented as a string of 3 hex bytes
// contained in an SDP key-value map. A default profile level id will be
// returned if the profile-level-id key is missing. Nothing will be returned if
// the key is present but the string is invalid.
RTC_EXPORT absl::optional<ProfileLevelId> ParseSdpProfileLevelId(
    const SdpVideoFormat::Parameters& params);

// Given that a decoder supports up to a given frame size (in pixels) at up to a
// given number of frames per second, return the highest H.264 level where it
// can guarantee that it will be able to support all valid encoded streams that
// are within that level.
RTC_EXPORT absl::optional<Level> SupportedLevel(int max_frame_pixel_count,
                                                float max_fps);

// Returns canonical string representation as three hex bytes of the profile
// level id, or returns nothing for invalid profile level ids.
RTC_EXPORT absl::optional<std::string> ProfileLevelIdToString(
    const ProfileLevelId& profile_level_id);

// Returns true if the parameters have the same H264 profile, i.e. the same
// H264::Profile (Baseline, High, etc).
RTC_EXPORT bool IsSameH264Profile(const SdpVideoFormat::Parameters& params1,
                                  const SdpVideoFormat::Parameters& params2);

}  // namespace H264
}  // namespace webrtc
#endif  // MEDIA_BASE_H264_PROFILE_LEVEL_ID_H_
