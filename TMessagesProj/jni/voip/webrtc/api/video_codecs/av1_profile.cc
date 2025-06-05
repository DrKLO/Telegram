/*
 *  Copyright (c) 2022 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "api/video_codecs/av1_profile.h"

#include <map>
#include <utility>

#include "media/base/media_constants.h"
#include "rtc_base/string_to_number.h"

namespace webrtc {

absl::string_view AV1ProfileToString(AV1Profile profile) {
  switch (profile) {
    case AV1Profile::kProfile0:
      return "0";
    case AV1Profile::kProfile1:
      return "1";
    case AV1Profile::kProfile2:
      return "2";
  }
  return "0";
}

absl::optional<AV1Profile> StringToAV1Profile(absl::string_view str) {
  const absl::optional<int> i = rtc::StringToNumber<int>(str);
  if (!i.has_value())
    return absl::nullopt;

  switch (i.value()) {
    case 0:
      return AV1Profile::kProfile0;
    case 1:
      return AV1Profile::kProfile1;
    case 2:
      return AV1Profile::kProfile2;
    default:
      return absl::nullopt;
  }
}

absl::optional<AV1Profile> ParseSdpForAV1Profile(
    const CodecParameterMap& params) {
  const auto profile_it = params.find(cricket::kAv1FmtpProfile);
  if (profile_it == params.end())
    return AV1Profile::kProfile0;
  const std::string& profile_str = profile_it->second;
  return StringToAV1Profile(profile_str);
}

bool AV1IsSameProfile(const CodecParameterMap& params1,
                      const CodecParameterMap& params2) {
  const absl::optional<AV1Profile> profile = ParseSdpForAV1Profile(params1);
  const absl::optional<AV1Profile> other_profile =
      ParseSdpForAV1Profile(params2);
  return profile && other_profile && profile == other_profile;
}

}  // namespace webrtc
