/*
 *  Copyright (c) 2017 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "logging/rtc_event_log/events/rtc_event_video_send_stream_config.h"

#include <utility>

#include "absl/memory/memory.h"

namespace webrtc {

RtcEventVideoSendStreamConfig::RtcEventVideoSendStreamConfig(
    std::unique_ptr<rtclog::StreamConfig> config)
    : config_(std::move(config)) {}

RtcEventVideoSendStreamConfig::RtcEventVideoSendStreamConfig(
    const RtcEventVideoSendStreamConfig& other)
    : RtcEvent(other.timestamp_us_),
      config_(std::make_unique<rtclog::StreamConfig>(*other.config_)) {}

RtcEventVideoSendStreamConfig::~RtcEventVideoSendStreamConfig() = default;

RtcEvent::Type RtcEventVideoSendStreamConfig::GetType() const {
  return RtcEvent::Type::VideoSendStreamConfig;
}

bool RtcEventVideoSendStreamConfig::IsConfigEvent() const {
  return true;
}

std::unique_ptr<RtcEventVideoSendStreamConfig>
RtcEventVideoSendStreamConfig::Copy() const {
  return absl::WrapUnique<RtcEventVideoSendStreamConfig>(
      new RtcEventVideoSendStreamConfig(*this));
}

}  // namespace webrtc
