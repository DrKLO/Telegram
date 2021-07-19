/*
 *  Copyright (c) 2018 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "modules/rtp_rtcp/source/rtp_packet_to_send.h"

#include <cstdint>

namespace webrtc {

RtpPacketToSend::RtpPacketToSend(const ExtensionManager* extensions)
    : RtpPacket(extensions) {}
RtpPacketToSend::RtpPacketToSend(const ExtensionManager* extensions,
                                 size_t capacity)
    : RtpPacket(extensions, capacity) {}
RtpPacketToSend::RtpPacketToSend(const RtpPacketToSend& packet) = default;
RtpPacketToSend::RtpPacketToSend(RtpPacketToSend&& packet) = default;

RtpPacketToSend& RtpPacketToSend::operator=(const RtpPacketToSend& packet) =
    default;
RtpPacketToSend& RtpPacketToSend::operator=(RtpPacketToSend&& packet) = default;

RtpPacketToSend::~RtpPacketToSend() = default;

}  // namespace webrtc
