/*
 *  Copyright (c) 2015 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "modules/rtp_rtcp/source/rtcp_packet/nack.h"

#include <algorithm>
#include <cstdint>
#include <utility>

#include "modules/rtp_rtcp/source/byte_io.h"
#include "modules/rtp_rtcp/source/rtcp_packet/common_header.h"
#include "rtc_base/checks.h"
#include "rtc_base/logging.h"

namespace webrtc {
namespace rtcp {
constexpr uint8_t Nack::kFeedbackMessageType;
constexpr size_t Nack::kNackItemLength;
// RFC 4585: Feedback format.
//
// Common packet format:
//
//    0                   1                   2                   3
//    0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1
//   +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
//   |V=2|P|   FMT   |       PT      |          length               |
//   +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
// 0 |                  SSRC of packet sender                        |
//   +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
// 4 |                  SSRC of media source                         |
//   +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
//   :            Feedback Control Information (FCI)                 :
//   :                                                               :
//
// Generic NACK (RFC 4585).
//
// FCI:
//    0                   1                   2                   3
//    0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1
//   +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
//   |            PID                |             BLP               |
//   +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
Nack::Nack() = default;
Nack::Nack(const Nack& rhs) = default;
Nack::~Nack() = default;

bool Nack::Parse(const CommonHeader& packet) {
  RTC_DCHECK_EQ(packet.type(), kPacketType);
  RTC_DCHECK_EQ(packet.fmt(), kFeedbackMessageType);

  if (packet.payload_size_bytes() < kCommonFeedbackLength + kNackItemLength) {
    RTC_LOG(LS_WARNING) << "Payload length " << packet.payload_size_bytes()
                        << " is too small for a Nack.";
    return false;
  }
  size_t nack_items =
      (packet.payload_size_bytes() - kCommonFeedbackLength) / kNackItemLength;

  ParseCommonFeedback(packet.payload());
  const uint8_t* next_nack = packet.payload() + kCommonFeedbackLength;

  packet_ids_.clear();
  packed_.resize(nack_items);
  for (size_t index = 0; index < nack_items; ++index) {
    packed_[index].first_pid = ByteReader<uint16_t>::ReadBigEndian(next_nack);
    packed_[index].bitmask = ByteReader<uint16_t>::ReadBigEndian(next_nack + 2);
    next_nack += kNackItemLength;
  }
  Unpack();

  return true;
}

size_t Nack::BlockLength() const {
  return kHeaderLength + kCommonFeedbackLength +
         packed_.size() * kNackItemLength;
}

bool Nack::Create(uint8_t* packet,
                  size_t* index,
                  size_t max_length,
                  PacketReadyCallback callback) const {
  RTC_DCHECK(!packed_.empty());
  // If nack list can't fit in packet, try to fragment.
  constexpr size_t kNackHeaderLength = kHeaderLength + kCommonFeedbackLength;
  for (size_t nack_index = 0; nack_index < packed_.size();) {
    size_t bytes_left_in_buffer = max_length - *index;
    if (bytes_left_in_buffer < kNackHeaderLength + kNackItemLength) {
      if (!OnBufferFull(packet, index, callback))
        return false;
      continue;
    }
    size_t num_nack_fields =
        std::min((bytes_left_in_buffer - kNackHeaderLength) / kNackItemLength,
                 packed_.size() - nack_index);

    size_t payload_size_bytes =
        kCommonFeedbackLength + (num_nack_fields * kNackItemLength);
    size_t payload_size_32bits =
        rtc::CheckedDivExact<size_t>(payload_size_bytes, 4);
    CreateHeader(kFeedbackMessageType, kPacketType, payload_size_32bits, packet,
                 index);

    CreateCommonFeedback(packet + *index);
    *index += kCommonFeedbackLength;

    size_t nack_end_index = nack_index + num_nack_fields;
    for (; nack_index < nack_end_index; ++nack_index) {
      const PackedNack& item = packed_[nack_index];
      ByteWriter<uint16_t>::WriteBigEndian(packet + *index + 0, item.first_pid);
      ByteWriter<uint16_t>::WriteBigEndian(packet + *index + 2, item.bitmask);
      *index += kNackItemLength;
    }
    RTC_DCHECK_LE(*index, max_length);
  }

  return true;
}

void Nack::SetPacketIds(const uint16_t* nack_list, size_t length) {
  RTC_DCHECK(nack_list);
  SetPacketIds(std::vector<uint16_t>(nack_list, nack_list + length));
}

void Nack::SetPacketIds(std::vector<uint16_t> nack_list) {
  RTC_DCHECK(packet_ids_.empty());
  RTC_DCHECK(packed_.empty());
  packet_ids_ = std::move(nack_list);
  Pack();
}

void Nack::Pack() {
  RTC_DCHECK(!packet_ids_.empty());
  RTC_DCHECK(packed_.empty());
  auto it = packet_ids_.begin();
  const auto end = packet_ids_.end();
  while (it != end) {
    PackedNack item;
    item.first_pid = *it++;
    // Bitmask specifies losses in any of the 16 packets following the pid.
    item.bitmask = 0;
    while (it != end) {
      uint16_t shift = static_cast<uint16_t>(*it - item.first_pid - 1);
      if (shift <= 15) {
        item.bitmask |= (1 << shift);
        ++it;
      } else {
        break;
      }
    }
    packed_.push_back(item);
  }
}

void Nack::Unpack() {
  RTC_DCHECK(packet_ids_.empty());
  RTC_DCHECK(!packed_.empty());
  for (const PackedNack& item : packed_) {
    packet_ids_.push_back(item.first_pid);
    uint16_t pid = item.first_pid + 1;
    for (uint16_t bitmask = item.bitmask; bitmask != 0; bitmask >>= 1, ++pid) {
      if (bitmask & 1)
        packet_ids_.push_back(pid);
    }
  }
}

}  // namespace rtcp
}  // namespace webrtc
