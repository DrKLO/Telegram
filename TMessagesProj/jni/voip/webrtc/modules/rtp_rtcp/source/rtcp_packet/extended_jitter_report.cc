/*
 *  Copyright (c) 2015 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "modules/rtp_rtcp/source/rtcp_packet/extended_jitter_report.h"

#include <cstdint>
#include <utility>

#include "modules/rtp_rtcp/source/byte_io.h"
#include "modules/rtp_rtcp/source/rtcp_packet/common_header.h"
#include "rtc_base/checks.h"
#include "rtc_base/logging.h"

namespace webrtc {
namespace rtcp {
constexpr uint8_t ExtendedJitterReport::kPacketType;
// Transmission Time Offsets in RTP Streams (RFC 5450).
//
//      0                   1                   2                   3
//      0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1
//     +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
// hdr |V=2|P|    RC   |   PT=IJ=195   |             length            |
//     +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
//     |                      inter-arrival jitter                     |
//     +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
//     .                                                               .
//     .                                                               .
//     .                                                               .
//     |                      inter-arrival jitter                     |
//     +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
//
//  If present, this RTCP packet must be placed after a receiver report
//  (inside a compound RTCP packet), and MUST have the same value for RC
//  (reception report count) as the receiver report.

ExtendedJitterReport::ExtendedJitterReport() = default;

ExtendedJitterReport::~ExtendedJitterReport() = default;

bool ExtendedJitterReport::Parse(const CommonHeader& packet) {
  RTC_DCHECK_EQ(packet.type(), kPacketType);

  const uint8_t number_of_jitters = packet.count();

  if (packet.payload_size_bytes() < number_of_jitters * kJitterSizeBytes) {
    RTC_LOG(LS_WARNING) << "Packet is too small to contain all the jitter.";
    return false;
  }

  inter_arrival_jitters_.resize(number_of_jitters);
  for (size_t index = 0; index < number_of_jitters; ++index) {
    inter_arrival_jitters_[index] = ByteReader<uint32_t>::ReadBigEndian(
        &packet.payload()[index * kJitterSizeBytes]);
  }

  return true;
}

bool ExtendedJitterReport::SetJitterValues(std::vector<uint32_t> values) {
  if (values.size() > kMaxNumberOfJitterValues) {
    RTC_LOG(LS_WARNING) << "Too many inter-arrival jitter items.";
    return false;
  }
  inter_arrival_jitters_ = std::move(values);
  return true;
}

size_t ExtendedJitterReport::BlockLength() const {
  return kHeaderLength + kJitterSizeBytes * inter_arrival_jitters_.size();
}

bool ExtendedJitterReport::Create(uint8_t* packet,
                                  size_t* index,
                                  size_t max_length,
                                  PacketReadyCallback callback) const {
  while (*index + BlockLength() > max_length) {
    if (!OnBufferFull(packet, index, callback))
      return false;
  }
  const size_t index_end = *index + BlockLength();
  size_t length = inter_arrival_jitters_.size();
  CreateHeader(length, kPacketType, length, packet, index);

  for (uint32_t jitter : inter_arrival_jitters_) {
    ByteWriter<uint32_t>::WriteBigEndian(packet + *index, jitter);
    *index += kJitterSizeBytes;
  }
  // Sanity check.
  RTC_DCHECK_EQ(index_end, *index);
  return true;
}

}  // namespace rtcp
}  // namespace webrtc
