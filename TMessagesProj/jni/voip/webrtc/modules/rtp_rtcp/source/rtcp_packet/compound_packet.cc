/*
 *  Copyright (c) 2016 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "modules/rtp_rtcp/source/rtcp_packet/compound_packet.h"

#include <memory>
#include <utility>

#include "rtc_base/checks.h"

namespace webrtc {
namespace rtcp {

CompoundPacket::CompoundPacket() = default;

CompoundPacket::~CompoundPacket() = default;

void CompoundPacket::Append(std::unique_ptr<RtcpPacket> packet) {
  RTC_CHECK(packet);
  appended_packets_.push_back(std::move(packet));
}

bool CompoundPacket::Create(uint8_t* packet,
                            size_t* index,
                            size_t max_length,
                            PacketReadyCallback callback) const {
  for (const auto& appended : appended_packets_) {
    if (!appended->Create(packet, index, max_length, callback))
      return false;
  }
  return true;
}

size_t CompoundPacket::BlockLength() const {
  size_t block_length = 0;
  for (const auto& appended : appended_packets_) {
    block_length += appended->BlockLength();
  }
  return block_length;
}

}  // namespace rtcp
}  // namespace webrtc
