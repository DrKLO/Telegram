/*
 *  Copyright (c) 2015 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "modules/rtp_rtcp/source/rtcp_packet/bye.h"

#include <string.h>

#include <cstdint>
#include <utility>

#include "modules/rtp_rtcp/source/byte_io.h"
#include "modules/rtp_rtcp/source/rtcp_packet/common_header.h"
#include "rtc_base/checks.h"
#include "rtc_base/logging.h"

namespace webrtc {
namespace rtcp {
constexpr uint8_t Bye::kPacketType;
// Bye packet (BYE) (RFC 3550).
//
//        0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1
//       +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
//       |V=2|P|    SC   |   PT=BYE=203  |             length            |
//       +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
//       |                           SSRC/CSRC                           |
//       +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
//       :                              ...                              :
//       +=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+
// (opt) |     length    |               reason for leaving            ...
//       +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
Bye::Bye() = default;

Bye::~Bye() = default;

bool Bye::Parse(const CommonHeader& packet) {
  RTC_DCHECK_EQ(packet.type(), kPacketType);

  const uint8_t src_count = packet.count();
  // Validate packet.
  if (packet.payload_size_bytes() < 4u * src_count) {
    RTC_LOG(LS_WARNING)
        << "Packet is too small to contain CSRCs it promise to have.";
    return false;
  }
  const uint8_t* const payload = packet.payload();
  bool has_reason = packet.payload_size_bytes() > 4u * src_count;
  uint8_t reason_length = 0;
  if (has_reason) {
    reason_length = payload[4u * src_count];
    if (packet.payload_size_bytes() - 4u * src_count < 1u + reason_length) {
      RTC_LOG(LS_WARNING) << "Invalid reason length: " << reason_length;
      return false;
    }
  }
  // Once sure packet is valid, copy values.
  if (src_count == 0) {  // A count value of zero is valid, but useless.
    SetSenderSsrc(0);
    csrcs_.clear();
  } else {
    SetSenderSsrc(ByteReader<uint32_t>::ReadBigEndian(payload));
    csrcs_.resize(src_count - 1);
    for (size_t i = 1; i < src_count; ++i)
      csrcs_[i - 1] = ByteReader<uint32_t>::ReadBigEndian(&payload[4 * i]);
  }

  if (has_reason) {
    reason_.assign(reinterpret_cast<const char*>(&payload[4u * src_count + 1]),
                   reason_length);
  } else {
    reason_.clear();
  }

  return true;
}

bool Bye::Create(uint8_t* packet,
                 size_t* index,
                 size_t max_length,
                 PacketReadyCallback callback) const {
  while (*index + BlockLength() > max_length) {
    if (!OnBufferFull(packet, index, callback))
      return false;
  }
  const size_t index_end = *index + BlockLength();

  CreateHeader(1 + csrcs_.size(), kPacketType, HeaderLength(), packet, index);
  // Store srcs of the leaving clients.
  ByteWriter<uint32_t>::WriteBigEndian(&packet[*index], sender_ssrc());
  *index += sizeof(uint32_t);
  for (uint32_t csrc : csrcs_) {
    ByteWriter<uint32_t>::WriteBigEndian(&packet[*index], csrc);
    *index += sizeof(uint32_t);
  }
  // Store the reason to leave.
  if (!reason_.empty()) {
    uint8_t reason_length = static_cast<uint8_t>(reason_.size());
    packet[(*index)++] = reason_length;
    memcpy(&packet[*index], reason_.data(), reason_length);
    *index += reason_length;
    // Add padding bytes if needed.
    size_t bytes_to_pad = index_end - *index;
    RTC_DCHECK_LE(bytes_to_pad, 3);
    if (bytes_to_pad > 0) {
      memset(&packet[*index], 0, bytes_to_pad);
      *index += bytes_to_pad;
    }
  }
  RTC_DCHECK_EQ(index_end, *index);
  return true;
}

bool Bye::SetCsrcs(std::vector<uint32_t> csrcs) {
  if (csrcs.size() > kMaxNumberOfCsrcs) {
    RTC_LOG(LS_WARNING) << "Too many CSRCs for Bye packet.";
    return false;
  }
  csrcs_ = std::move(csrcs);
  return true;
}

void Bye::SetReason(std::string reason) {
  RTC_DCHECK_LE(reason.size(), 0xffu);
  reason_ = std::move(reason);
}

size_t Bye::BlockLength() const {
  size_t src_count = (1 + csrcs_.size());
  size_t reason_size_in_32bits = reason_.empty() ? 0 : (reason_.size() / 4 + 1);
  return kHeaderLength + 4 * (src_count + reason_size_in_32bits);
}

}  // namespace rtcp
}  // namespace webrtc
