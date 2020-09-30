/*
 *  Copyright (c) 2015 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "modules/rtp_rtcp/source/rtcp_packet/app.h"

#include <string.h>

#include <cstdint>

#include "modules/rtp_rtcp/source/byte_io.h"
#include "modules/rtp_rtcp/source/rtcp_packet/common_header.h"
#include "rtc_base/checks.h"
#include "rtc_base/logging.h"

namespace webrtc {
namespace rtcp {
constexpr uint8_t App::kPacketType;
constexpr size_t App::kMaxDataSize;
// Application-Defined packet (APP) (RFC 3550).
//
//     0                   1                   2                   3
//     0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1
//    +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
//    |V=2|P| subtype |   PT=APP=204  |             length            |
//    +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
//  0 |                           SSRC/CSRC                           |
//    +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
//  4 |                          name (ASCII)                         |
//    +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
//  8 |                   application-dependent data                ...
//    +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+

App::App() : sub_type_(0), name_(0) {}

App::~App() = default;

bool App::Parse(const CommonHeader& packet) {
  RTC_DCHECK_EQ(packet.type(), kPacketType);
  if (packet.payload_size_bytes() < kAppBaseLength) {
    RTC_LOG(LS_WARNING) << "Packet is too small to be a valid APP packet";
    return false;
  }
  if (packet.payload_size_bytes() % 4 != 0) {
    RTC_LOG(LS_WARNING)
        << "Packet payload must be 32 bits aligned to make a valid APP packet";
    return false;
  }
  sub_type_ = packet.fmt();
  SetSenderSsrc(ByteReader<uint32_t>::ReadBigEndian(&packet.payload()[0]));
  name_ = ByteReader<uint32_t>::ReadBigEndian(&packet.payload()[4]);
  data_.SetData(packet.payload() + kAppBaseLength,
                packet.payload_size_bytes() - kAppBaseLength);
  return true;
}

void App::SetSubType(uint8_t subtype) {
  RTC_DCHECK_LE(subtype, 0x1f);
  sub_type_ = subtype;
}

void App::SetData(const uint8_t* data, size_t data_length) {
  RTC_DCHECK(data);
  RTC_DCHECK_EQ(data_length % 4, 0) << "Data must be 32 bits aligned.";
  RTC_DCHECK_LE(data_length, kMaxDataSize)
      << "App data size " << data_length << " exceed maximum of "
      << kMaxDataSize << " bytes.";
  data_.SetData(data, data_length);
}

size_t App::BlockLength() const {
  return kHeaderLength + kAppBaseLength + data_.size();
}

bool App::Create(uint8_t* packet,
                 size_t* index,
                 size_t max_length,
                 PacketReadyCallback callback) const {
  while (*index + BlockLength() > max_length) {
    if (!OnBufferFull(packet, index, callback))
      return false;
  }
  const size_t index_end = *index + BlockLength();
  CreateHeader(sub_type_, kPacketType, HeaderLength(), packet, index);

  ByteWriter<uint32_t>::WriteBigEndian(&packet[*index + 0], sender_ssrc());
  ByteWriter<uint32_t>::WriteBigEndian(&packet[*index + 4], name_);
  memcpy(&packet[*index + 8], data_.data(), data_.size());
  *index += (8 + data_.size());
  RTC_DCHECK_EQ(index_end, *index);
  return true;
}

}  // namespace rtcp
}  // namespace webrtc
