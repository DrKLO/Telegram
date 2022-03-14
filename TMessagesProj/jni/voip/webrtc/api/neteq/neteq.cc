/*
 *  Copyright (c) 2012 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "api/neteq/neteq.h"

#include "rtc_base/strings/string_builder.h"

namespace webrtc {

NetEq::Config::Config() = default;
NetEq::Config::Config(const Config&) = default;
NetEq::Config::Config(Config&&) = default;
NetEq::Config::~Config() = default;
NetEq::Config& NetEq::Config::operator=(const Config&) = default;
NetEq::Config& NetEq::Config::operator=(Config&&) = default;

std::string NetEq::Config::ToString() const {
  char buf[1024];
  rtc::SimpleStringBuilder ss(buf);
  ss << "sample_rate_hz=" << sample_rate_hz << ", enable_post_decode_vad="
     << (enable_post_decode_vad ? "true" : "false")
     << ", max_packets_in_buffer=" << max_packets_in_buffer
     << ", min_delay_ms=" << min_delay_ms << ", enable_fast_accelerate="
     << (enable_fast_accelerate ? "true" : "false")
     << ", enable_muted_state=" << (enable_muted_state ? "true" : "false")
     << ", enable_rtx_handling=" << (enable_rtx_handling ? "true" : "false");
  return ss.str();
}

}  // namespace webrtc
