/*
 *  Copyright (c) 2019 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "logging/rtc_event_log/events/rtc_event_generic_packet_received.h"

#include "absl/memory/memory.h"

namespace webrtc {

RtcEventGenericPacketReceived::RtcEventGenericPacketReceived(
    int64_t packet_number,
    size_t packet_length)
    : packet_number_(packet_number), packet_length_(packet_length) {}

RtcEventGenericPacketReceived::RtcEventGenericPacketReceived(
    const RtcEventGenericPacketReceived& packet) = default;

RtcEventGenericPacketReceived::~RtcEventGenericPacketReceived() = default;

std::unique_ptr<RtcEventGenericPacketReceived>
RtcEventGenericPacketReceived::Copy() const {
  return absl::WrapUnique(new RtcEventGenericPacketReceived(*this));
}
RtcEvent::Type RtcEventGenericPacketReceived::GetType() const {
  return RtcEvent::Type::GenericPacketReceived;
}

bool RtcEventGenericPacketReceived::IsConfigEvent() const {
  return false;
}

}  // namespace webrtc
