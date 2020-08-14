/*
 *  Copyright (c) 2019 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "modules/audio_coding/codecs/opus/audio_coder_opus_common.h"

namespace webrtc {

absl::optional<std::string> GetFormatParameter(const SdpAudioFormat& format,
                                               const std::string& param) {
  auto it = format.parameters.find(param);
  if (it == format.parameters.end())
    return absl::nullopt;

  return it->second;
}

// Parses a comma-separated string "1,2,0,6" into a std::vector<unsigned char>.
template <>
absl::optional<std::vector<unsigned char>> GetFormatParameter(
    const SdpAudioFormat& format,
    const std::string& param) {
  std::vector<unsigned char> result;
  const std::string comma_separated_list =
      GetFormatParameter(format, param).value_or("");
  size_t pos = 0;
  while (pos < comma_separated_list.size()) {
    const size_t next_comma = comma_separated_list.find(',', pos);
    const size_t distance_to_next_comma = next_comma == std::string::npos
                                              ? std::string::npos
                                              : (next_comma - pos);
    auto substring_with_number =
        comma_separated_list.substr(pos, distance_to_next_comma);
    auto conv = rtc::StringToNumber<int>(substring_with_number);
    if (!conv.has_value()) {
      return absl::nullopt;
    }
    result.push_back(*conv);
    pos += substring_with_number.size() + 1;
  }
  return result;
}

}  // namespace webrtc
