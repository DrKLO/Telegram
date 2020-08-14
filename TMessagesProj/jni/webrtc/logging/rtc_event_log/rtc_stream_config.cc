/*
 *  Copyright (c) 2017 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "logging/rtc_event_log/rtc_stream_config.h"

namespace webrtc {
namespace rtclog {

StreamConfig::StreamConfig() {}

StreamConfig::~StreamConfig() {}

StreamConfig::StreamConfig(const StreamConfig& other) = default;

bool StreamConfig::operator==(const StreamConfig& other) const {
  return local_ssrc == other.local_ssrc && remote_ssrc == other.remote_ssrc &&
         rtx_ssrc == other.rtx_ssrc && rsid == other.rsid &&
         remb == other.remb && rtcp_mode == other.rtcp_mode &&
         rtp_extensions == other.rtp_extensions && codecs == other.codecs;
}

bool StreamConfig::operator!=(const StreamConfig& other) const {
  return !(*this == other);
}

StreamConfig::Codec::Codec(const std::string& payload_name,
                           int payload_type,
                           int rtx_payload_type)
    : payload_name(payload_name),
      payload_type(payload_type),
      rtx_payload_type(rtx_payload_type) {}

bool StreamConfig::Codec::operator==(const Codec& other) const {
  return payload_name == other.payload_name &&
         payload_type == other.payload_type &&
         rtx_payload_type == other.rtx_payload_type;
}

}  // namespace rtclog
}  // namespace webrtc
