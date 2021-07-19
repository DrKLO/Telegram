/*
 *  Copyright 2018 The WebRTC Project Authors. All rights reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "rtc_base/network/sent_packet.h"

namespace rtc {

PacketInfo::PacketInfo() = default;
PacketInfo::PacketInfo(const PacketInfo& info) = default;
PacketInfo::~PacketInfo() = default;

SentPacket::SentPacket() = default;
SentPacket::SentPacket(int64_t packet_id, int64_t send_time_ms)
    : packet_id(packet_id), send_time_ms(send_time_ms) {}
SentPacket::SentPacket(int64_t packet_id,
                       int64_t send_time_ms,
                       const rtc::PacketInfo& info)
    : packet_id(packet_id), send_time_ms(send_time_ms), info(info) {}

}  // namespace rtc
