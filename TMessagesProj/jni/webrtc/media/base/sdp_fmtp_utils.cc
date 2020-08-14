/*
 *  Copyright (c) 2019 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "media/base/sdp_fmtp_utils.h"

#include <map>
#include <utility>

#include "rtc_base/string_to_number.h"

namespace webrtc {
namespace {
// Max frame rate for VP8 and VP9 video.
const char kVPxFmtpMaxFrameRate[] = "max-fr";
// Max frame size for VP8 and VP9 video.
const char kVPxFmtpMaxFrameSize[] = "max-fs";
const int kVPxFmtpFrameSizeSubBlockPixels = 256;

absl::optional<int> ParsePositiveNumberFromParams(
    const SdpVideoFormat::Parameters& params,
    const char* parameter_name) {
  const auto max_frame_rate_it = params.find(parameter_name);
  if (max_frame_rate_it == params.end())
    return absl::nullopt;

  const absl::optional<int> i =
      rtc::StringToNumber<int>(max_frame_rate_it->second);
  if (!i.has_value() || i.value() <= 0)
    return absl::nullopt;
  return i;
}

}  // namespace

absl::optional<int> ParseSdpForVPxMaxFrameRate(
    const SdpVideoFormat::Parameters& params) {
  return ParsePositiveNumberFromParams(params, kVPxFmtpMaxFrameRate);
}

absl::optional<int> ParseSdpForVPxMaxFrameSize(
    const SdpVideoFormat::Parameters& params) {
  const absl::optional<int> i =
      ParsePositiveNumberFromParams(params, kVPxFmtpMaxFrameSize);
  return i ? absl::make_optional(i.value() * kVPxFmtpFrameSizeSubBlockPixels)
           : absl::nullopt;
}

}  // namespace webrtc
