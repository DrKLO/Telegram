/*
 *  Copyright (c) 2016 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "modules/rtp_rtcp/source/rtcp_packet/extended_reports.h"

#include <vector>

#include "modules/rtp_rtcp/source/byte_io.h"
#include "modules/rtp_rtcp/source/rtcp_packet/common_header.h"
#include "rtc_base/checks.h"
#include "rtc_base/logging.h"

namespace webrtc {
namespace rtcp {
constexpr uint8_t ExtendedReports::kPacketType;
constexpr size_t ExtendedReports::kMaxNumberOfDlrrItems;
// From RFC 3611: RTP Control Protocol Extended Reports (RTCP XR).
//
// Format for XR packets:
//
//   0                   1                   2                   3
//   0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1
//  +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
//  |V=2|P|reserved |   PT=XR=207   |             length            |
//  +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
//  |                              SSRC                             |
//  +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
//  :                         report blocks                         :
//  +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
//
// Extended report block:
//   0                   1                   2                   3
//   0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1
//  +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
//  |  Block Type   |   reserved    |         block length          |
//  +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
//  :             type-specific block contents                      :
//  +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
ExtendedReports::ExtendedReports() = default;
ExtendedReports::ExtendedReports(const ExtendedReports& xr) = default;
ExtendedReports::~ExtendedReports() = default;

bool ExtendedReports::Parse(const CommonHeader& packet) {
  RTC_DCHECK_EQ(packet.type(), kPacketType);

  if (packet.payload_size_bytes() < kXrBaseLength) {
    RTC_LOG(LS_WARNING)
        << "Packet is too small to be an ExtendedReports packet.";
    return false;
  }

  SetSenderSsrc(ByteReader<uint32_t>::ReadBigEndian(packet.payload()));
  rrtr_block_.reset();
  dlrr_block_.ClearItems();
  target_bitrate_ = absl::nullopt;

  const uint8_t* current_block = packet.payload() + kXrBaseLength;
  const uint8_t* const packet_end =
      packet.payload() + packet.payload_size_bytes();
  constexpr size_t kBlockHeaderSizeBytes = 4;
  while (current_block + kBlockHeaderSizeBytes <= packet_end) {
    uint8_t block_type = ByteReader<uint8_t>::ReadBigEndian(current_block);
    uint16_t block_length =
        ByteReader<uint16_t>::ReadBigEndian(current_block + 2);
    const uint8_t* next_block =
        current_block + kBlockHeaderSizeBytes + block_length * 4;
    if (next_block > packet_end) {
      RTC_LOG(LS_WARNING)
          << "Report block in extended report packet is too big.";
      return false;
    }
    switch (block_type) {
      case Rrtr::kBlockType:
        ParseRrtrBlock(current_block, block_length);
        break;
      case Dlrr::kBlockType:
        ParseDlrrBlock(current_block, block_length);
        break;
      case TargetBitrate::kBlockType:
        ParseTargetBitrateBlock(current_block, block_length);
        break;
      default:
        // Unknown block, ignore.
        RTC_LOG(LS_WARNING)
            << "Unknown extended report block type " << block_type;
        break;
    }
    current_block = next_block;
  }

  return true;
}

void ExtendedReports::SetRrtr(const Rrtr& rrtr) {
  if (rrtr_block_)
    RTC_LOG(LS_WARNING) << "Rrtr already set, overwriting.";
  rrtr_block_.emplace(rrtr);
}

bool ExtendedReports::AddDlrrItem(const ReceiveTimeInfo& time_info) {
  if (dlrr_block_.sub_blocks().size() >= kMaxNumberOfDlrrItems) {
    RTC_LOG(LS_WARNING) << "Reached maximum number of DLRR items.";
    return false;
  }
  dlrr_block_.AddDlrrItem(time_info);
  return true;
}

void ExtendedReports::SetTargetBitrate(const TargetBitrate& bitrate) {
  if (target_bitrate_)
    RTC_LOG(LS_WARNING) << "TargetBitrate already set, overwriting.";

  target_bitrate_ = bitrate;
}

size_t ExtendedReports::BlockLength() const {
  return kHeaderLength + kXrBaseLength + RrtrLength() + DlrrLength() +
         TargetBitrateLength();
}

bool ExtendedReports::Create(uint8_t* packet,
                             size_t* index,
                             size_t max_length,
                             PacketReadyCallback callback) const {
  while (*index + BlockLength() > max_length) {
    if (!OnBufferFull(packet, index, callback))
      return false;
  }
  size_t index_end = *index + BlockLength();
  const uint8_t kReserved = 0;
  CreateHeader(kReserved, kPacketType, HeaderLength(), packet, index);
  ByteWriter<uint32_t>::WriteBigEndian(packet + *index, sender_ssrc());
  *index += sizeof(uint32_t);
  if (rrtr_block_) {
    rrtr_block_->Create(packet + *index);
    *index += Rrtr::kLength;
  }
  if (dlrr_block_) {
    dlrr_block_.Create(packet + *index);
    *index += dlrr_block_.BlockLength();
  }
  if (target_bitrate_) {
    target_bitrate_->Create(packet + *index);
    *index += target_bitrate_->BlockLength();
  }
  RTC_CHECK_EQ(*index, index_end);
  return true;
}

size_t ExtendedReports::TargetBitrateLength() const {
  if (target_bitrate_)
    return target_bitrate_->BlockLength();
  return 0;
}

void ExtendedReports::ParseRrtrBlock(const uint8_t* block,
                                     uint16_t block_length) {
  if (block_length != Rrtr::kBlockLength) {
    RTC_LOG(LS_WARNING) << "Incorrect rrtr block size " << block_length
                        << " Should be " << Rrtr::kBlockLength;
    return;
  }
  if (rrtr_block_) {
    RTC_LOG(LS_WARNING)
        << "Two rrtr blocks found in same Extended Report packet";
    return;
  }
  rrtr_block_.emplace();
  rrtr_block_->Parse(block);
}

void ExtendedReports::ParseDlrrBlock(const uint8_t* block,
                                     uint16_t block_length) {
  if (dlrr_block_) {
    RTC_LOG(LS_WARNING)
        << "Two Dlrr blocks found in same Extended Report packet";
    return;
  }
  dlrr_block_.Parse(block, block_length);
}

void ExtendedReports::ParseTargetBitrateBlock(const uint8_t* block,
                                              uint16_t block_length) {
  target_bitrate_.emplace();
  target_bitrate_->Parse(block, block_length);
}
}  // namespace rtcp
}  // namespace webrtc
