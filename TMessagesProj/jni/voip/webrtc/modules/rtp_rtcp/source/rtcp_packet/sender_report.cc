/*
 *  Copyright (c) 2016 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "modules/rtp_rtcp/source/rtcp_packet/sender_report.h"

#include <utility>

#include "modules/rtp_rtcp/source/byte_io.h"
#include "modules/rtp_rtcp/source/rtcp_packet/common_header.h"
#include "rtc_base/checks.h"
#include "rtc_base/logging.h"

namespace webrtc {
namespace rtcp {
constexpr uint8_t SenderReport::kPacketType;
constexpr size_t SenderReport::kMaxNumberOfReportBlocks;
constexpr size_t SenderReport::kSenderBaseLength;
//    Sender report (SR) (RFC 3550).
//     0                   1                   2                   3
//     0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1
//    +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
//    |V=2|P|    RC   |   PT=SR=200   |             length            |
//    +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
//  0 |                         SSRC of sender                        |
//    +=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+
//  4 |              NTP timestamp, most significant word             |
//    +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
//  8 |             NTP timestamp, least significant word             |
//    +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
// 12 |                         RTP timestamp                         |
//    +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
// 16 |                     sender's packet count                     |
//    +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
// 20 |                      sender's octet count                     |
// 24 +=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+

SenderReport::SenderReport()
    : rtp_timestamp_(0), sender_packet_count_(0), sender_octet_count_(0) {}

SenderReport::SenderReport(const SenderReport&) = default;
SenderReport::SenderReport(SenderReport&&) = default;
SenderReport& SenderReport::operator=(const SenderReport&) = default;
SenderReport& SenderReport::operator=(SenderReport&&) = default;
SenderReport::~SenderReport() = default;

bool SenderReport::Parse(const CommonHeader& packet) {
  RTC_DCHECK_EQ(packet.type(), kPacketType);

  const uint8_t report_block_count = packet.count();
  if (packet.payload_size_bytes() <
      kSenderBaseLength + report_block_count * ReportBlock::kLength) {
    RTC_LOG(LS_WARNING) << "Packet is too small to contain all the data.";
    return false;
  }
  // Read SenderReport header.
  const uint8_t* const payload = packet.payload();
  SetSenderSsrc(ByteReader<uint32_t>::ReadBigEndian(&payload[0]));
  uint32_t secs = ByteReader<uint32_t>::ReadBigEndian(&payload[4]);
  uint32_t frac = ByteReader<uint32_t>::ReadBigEndian(&payload[8]);
  ntp_.Set(secs, frac);
  rtp_timestamp_ = ByteReader<uint32_t>::ReadBigEndian(&payload[12]);
  sender_packet_count_ = ByteReader<uint32_t>::ReadBigEndian(&payload[16]);
  sender_octet_count_ = ByteReader<uint32_t>::ReadBigEndian(&payload[20]);
  report_blocks_.resize(report_block_count);
  const uint8_t* next_block = payload + kSenderBaseLength;
  for (ReportBlock& block : report_blocks_) {
    bool block_parsed = block.Parse(next_block, ReportBlock::kLength);
    RTC_DCHECK(block_parsed);
    next_block += ReportBlock::kLength;
  }
  // Double check we didn't read beyond provided buffer.
  RTC_DCHECK_LE(next_block - payload,
                static_cast<ptrdiff_t>(packet.payload_size_bytes()));
  return true;
}

size_t SenderReport::BlockLength() const {
  return kHeaderLength + kSenderBaseLength +
         report_blocks_.size() * ReportBlock::kLength;
}

bool SenderReport::Create(uint8_t* packet,
                          size_t* index,
                          size_t max_length,
                          PacketReadyCallback callback) const {
  while (*index + BlockLength() > max_length) {
    if (!OnBufferFull(packet, index, callback))
      return false;
  }
  const size_t index_end = *index + BlockLength();

  CreateHeader(report_blocks_.size(), kPacketType, HeaderLength(), packet,
               index);
  // Write SenderReport header.
  ByteWriter<uint32_t>::WriteBigEndian(&packet[*index + 0], sender_ssrc());
  ByteWriter<uint32_t>::WriteBigEndian(&packet[*index + 4], ntp_.seconds());
  ByteWriter<uint32_t>::WriteBigEndian(&packet[*index + 8], ntp_.fractions());
  ByteWriter<uint32_t>::WriteBigEndian(&packet[*index + 12], rtp_timestamp_);
  ByteWriter<uint32_t>::WriteBigEndian(&packet[*index + 16],
                                       sender_packet_count_);
  ByteWriter<uint32_t>::WriteBigEndian(&packet[*index + 20],
                                       sender_octet_count_);
  *index += kSenderBaseLength;
  // Write report blocks.
  for (const ReportBlock& block : report_blocks_) {
    block.Create(packet + *index);
    *index += ReportBlock::kLength;
  }
  // Ensure bytes written match expected.
  RTC_DCHECK_EQ(*index, index_end);
  return true;
}

bool SenderReport::AddReportBlock(const ReportBlock& block) {
  if (report_blocks_.size() >= kMaxNumberOfReportBlocks) {
    RTC_LOG(LS_WARNING) << "Max report blocks reached.";
    return false;
  }
  report_blocks_.push_back(block);
  return true;
}

bool SenderReport::SetReportBlocks(std::vector<ReportBlock> blocks) {
  if (blocks.size() > kMaxNumberOfReportBlocks) {
    RTC_LOG(LS_WARNING) << "Too many report blocks (" << blocks.size()
                        << ") for sender report.";
    return false;
  }
  report_blocks_ = std::move(blocks);
  return true;
}

}  // namespace rtcp
}  // namespace webrtc
