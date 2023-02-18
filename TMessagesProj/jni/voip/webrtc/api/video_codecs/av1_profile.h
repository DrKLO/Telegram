/*
 *  Copyright (c) 2022 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#ifndef API_VIDEO_CODECS_AV1_PROFILE_H_
#define API_VIDEO_CODECS_AV1_PROFILE_H_

#include <string>

#include "absl/strings/string_view.h"
#include "absl/types/optional.h"
#include "api/video_codecs/sdp_video_format.h"
#include "rtc_base/system/rtc_export.h"

namespace webrtc {

// Profile information for AV1 video.
extern RTC_EXPORT const char kAV1FmtpProfile[];

// Profiles can be found at:
// https://aomedia.org/av1/specification/annex-a/#profiles
// The enum values match the number specified in the SDP.
enum class AV1Profile {
  kProfile0 = 0,
  kProfile1 = 1,
  kProfile2 = 2,
};

// Helper function which converts an AV1Profile to std::string. Returns "0" if
// an unknown value is passed in.
RTC_EXPORT absl::string_view AV1ProfileToString(AV1Profile profile);

// Helper function which converts a std::string to AV1Profile. Returns null if
// |profile| is not a valid profile string.
absl::optional<AV1Profile> StringToAV1Profile(absl::string_view profile);

// Parses an SDP key-value map of format parameters to retrive an AV1 profile.
// Returns an AV1Profile if one has been specified, `kProfile0` if no profile is
// specified and an empty value if the profile key is present but contains an
// invalid value.
RTC_EXPORT absl::optional<AV1Profile> ParseSdpForAV1Profile(
    const SdpVideoFormat::Parameters& params);

// Returns true if the parameters have the same AV1 profile or neither contains
// an AV1 profile, otherwise false.
bool AV1IsSameProfile(const SdpVideoFormat::Parameters& params1,
                      const SdpVideoFormat::Parameters& params2);

}  // namespace webrtc

#endif  // API_VIDEO_CODECS_AV1_PROFILE_H_
