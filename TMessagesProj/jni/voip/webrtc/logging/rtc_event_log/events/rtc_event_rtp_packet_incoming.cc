/*
 *  Copyright (c) 2017 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "logging/rtc_event_log/events/rtc_event_rtp_packet_incoming.h"

#include "absl/memory/memory.h"
#include "modules/rtp_rtcp/source/rtp_packet_received.h"
#include "rtc_base/checks.h"

namespace webrtc {

RtcEventRtpPacketIncoming::RtcEventRtpPacketIncoming(
    const RtpPacketReceived& packet)
    : packet_(packet) {}

RtcEventRtpPacketIncoming::RtcEventRtpPacketIncoming(
    const RtcEventRtpPacketIncoming& other)
    : RtcEvent(other.timestamp_us_), packet_(other.packet_) {}

RtcEventRtpPacketIncoming::~RtcEventRtpPacketIncoming() = default;

std::unique_ptr<RtcEventRtpPacketIncoming> RtcEventRtpPacketIncoming::Copy()
    const {
  return absl::WrapUnique<RtcEventRtpPacketIncoming>(
      new RtcEventRtpPacketIncoming(*this));
}

}  // namespace webrtc
