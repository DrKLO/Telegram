/*
 *  Copyright (c) 2021 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "api/video_codecs/vp9_profile.h"

#include <map>
#include <utility>

#include "rtc_base/string_to_number.h"

namespace webrtc {

// Profile information for VP9 video.
const char kVP9FmtpProfileId[] = "profile-id";

std::string VP9ProfileToString(VP9Profile profile) {
  switch (profile) {
    case VP9Profile::kProfile0:
      return "0";
    case VP9Profile::kProfile1:
      return "1";
    case VP9Profile::kProfile2:
      return "2";
    case VP9Profile::kProfile3:
      return "3";
  }
  return "0";
}

absl::optional<VP9Profile> StringToVP9Profile(const std::string& str) {
  const absl::optional<int> i = rtc::StringToNumber<int>(str);
  if (!i.has_value())
    return absl::nullopt;

  switch (i.value()) {
    case 0:
      return VP9Profile::kProfile0;
    case 1:
      return VP9Profile::kProfile1;
    case 2:
      return VP9Profile::kProfile2;
    case 3:
      return VP9Profile::kProfile3;
    default:
      return absl::nullopt;
  }
}

absl::optional<VP9Profile> ParseSdpForVP9Profile(
    const CodecParameterMap& params) {
  const auto profile_it = params.find(kVP9FmtpProfileId);
  if (profile_it == params.end())
    return VP9Profile::kProfile0;
  const std::string& profile_str = profile_it->second;
  return StringToVP9Profile(profile_str);
}

bool VP9IsSameProfile(const CodecParameterMap& params1,
                      const CodecParameterMap& params2) {
  const absl::optional<VP9Profile> profile = ParseSdpForVP9Profile(params1);
  const absl::optional<VP9Profile> other_profile =
      ParseSdpForVP9Profile(params2);
  return profile && other_profile && profile == other_profile;
}

}  // namespace webrtc
