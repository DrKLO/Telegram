/*
 *  Copyright (c) 2019 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "logging/rtc_event_log/events/rtc_event_generic_packet_sent.h"

#include "absl/memory/memory.h"

namespace webrtc {

RtcEventGenericPacketSent::RtcEventGenericPacketSent(int64_t packet_number,
                                                     size_t overhead_length,
                                                     size_t payload_length,
                                                     size_t padding_length)
    : packet_number_(packet_number),
      overhead_length_(overhead_length),
      payload_length_(payload_length),
      padding_length_(padding_length) {}

RtcEventGenericPacketSent::RtcEventGenericPacketSent(
    const RtcEventGenericPacketSent& packet) = default;

RtcEventGenericPacketSent::~RtcEventGenericPacketSent() = default;

std::unique_ptr<RtcEventGenericPacketSent> RtcEventGenericPacketSent::Copy()
    const {
  return absl::WrapUnique(new RtcEventGenericPacketSent(*this));
}

RtcEvent::Type RtcEventGenericPacketSent::GetType() const {
  return RtcEvent::Type::GenericPacketSent;
}

bool RtcEventGenericPacketSent::IsConfigEvent() const {
  return false;
}

}  // namespace webrtc
