/*
 *  Copyright 2018 The WebRTC Project Authors. All rights reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "rtc_base/strings/audio_format_to_string.h"

#include <utility>

#include "rtc_base/strings/string_builder.h"

namespace rtc {
std::string ToString(const webrtc::SdpAudioFormat& saf) {
  char sb_buf[1024];
  rtc::SimpleStringBuilder sb(sb_buf);
  sb << "{name: " << saf.name;
  sb << ", clockrate_hz: " << saf.clockrate_hz;
  sb << ", num_channels: " << saf.num_channels;
  sb << ", parameters: {";
  const char* sep = "";
  for (const auto& kv : saf.parameters) {
    sb << sep << kv.first << ": " << kv.second;
    sep = ", ";
  }
  sb << "}}";
  return sb.str();
}
std::string ToString(const webrtc::AudioCodecInfo& aci) {
  char sb_buf[1024];
  rtc::SimpleStringBuilder sb(sb_buf);
  sb << "{sample_rate_hz: " << aci.sample_rate_hz;
  sb << ", num_channels: " << aci.num_channels;
  sb << ", default_bitrate_bps: " << aci.default_bitrate_bps;
  sb << ", min_bitrate_bps: " << aci.min_bitrate_bps;
  sb << ", max_bitrate_bps: " << aci.max_bitrate_bps;
  sb << ", allow_comfort_noise: " << aci.allow_comfort_noise;
  sb << ", supports_network_adaption: " << aci.supports_network_adaption;
  sb << "}";
  return sb.str();
}
std::string ToString(const webrtc::AudioCodecSpec& acs) {
  char sb_buf[1024];
  rtc::SimpleStringBuilder sb(sb_buf);
  sb << "{format: " << ToString(acs.format);
  sb << ", info: " << ToString(acs.info);
  sb << "}";
  return sb.str();
}
}  // namespace rtc
