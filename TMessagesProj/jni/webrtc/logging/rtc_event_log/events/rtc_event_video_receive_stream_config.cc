/*
 *  Copyright (c) 2017 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "logging/rtc_event_log/events/rtc_event_video_receive_stream_config.h"

#include <utility>

#include "absl/memory/memory.h"
#include "rtc_base/checks.h"

namespace webrtc {

RtcEventVideoReceiveStreamConfig::RtcEventVideoReceiveStreamConfig(
    std::unique_ptr<rtclog::StreamConfig> config)
    : config_(std::move(config)) {
  RTC_DCHECK(config_);
}

RtcEventVideoReceiveStreamConfig::RtcEventVideoReceiveStreamConfig(
    const RtcEventVideoReceiveStreamConfig& other)
    : RtcEvent(other.timestamp_us_),
      config_(std::make_unique<rtclog::StreamConfig>(*other.config_)) {}

RtcEventVideoReceiveStreamConfig::~RtcEventVideoReceiveStreamConfig() = default;

RtcEvent::Type RtcEventVideoReceiveStreamConfig::GetType() const {
  return Type::VideoReceiveStreamConfig;
}

bool RtcEventVideoReceiveStreamConfig::IsConfigEvent() const {
  return true;
}

std::unique_ptr<RtcEventVideoReceiveStreamConfig>
RtcEventVideoReceiveStreamConfig::Copy() const {
  return absl::WrapUnique<RtcEventVideoReceiveStreamConfig>(
      new RtcEventVideoReceiveStreamConfig(*this));
}

}  // namespace webrtc
