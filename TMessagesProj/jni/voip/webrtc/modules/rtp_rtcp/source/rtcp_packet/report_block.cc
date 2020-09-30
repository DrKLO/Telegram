/*
 *  Copyright (c) 2015 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "modules/rtp_rtcp/source/rtcp_packet/report_block.h"

#include "modules/rtp_rtcp/source/byte_io.h"
#include "rtc_base/checks.h"
#include "rtc_base/logging.h"

namespace webrtc {
namespace rtcp {

// From RFC 3550, RTP: A Transport Protocol for Real-Time Applications.
//
// RTCP report block (RFC 3550).
//
//     0                   1                   2                   3
//     0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1
//    +=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+
//  0 |                 SSRC_1 (SSRC of first source)                 |
//    +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
//  4 | fraction lost |       cumulative number of packets lost       |
//    +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
//  8 |           extended highest sequence number received           |
//    +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
// 12 |                      interarrival jitter                      |
//    +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
// 16 |                         last SR (LSR)                         |
//    +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
// 20 |                   delay since last SR (DLSR)                  |
// 24 +=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+
ReportBlock::ReportBlock()
    : source_ssrc_(0),
      fraction_lost_(0),
      cumulative_lost_(0),
      extended_high_seq_num_(0),
      jitter_(0),
      last_sr_(0),
      delay_since_last_sr_(0) {}

bool ReportBlock::Parse(const uint8_t* buffer, size_t length) {
  RTC_DCHECK(buffer != nullptr);
  if (length < ReportBlock::kLength) {
    RTC_LOG(LS_ERROR) << "Report Block should be 24 bytes long";
    return false;
  }

  source_ssrc_ = ByteReader<uint32_t>::ReadBigEndian(&buffer[0]);
  fraction_lost_ = buffer[4];
  cumulative_lost_ = ByteReader<int32_t, 3>::ReadBigEndian(&buffer[5]);
  extended_high_seq_num_ = ByteReader<uint32_t>::ReadBigEndian(&buffer[8]);
  jitter_ = ByteReader<uint32_t>::ReadBigEndian(&buffer[12]);
  last_sr_ = ByteReader<uint32_t>::ReadBigEndian(&buffer[16]);
  delay_since_last_sr_ = ByteReader<uint32_t>::ReadBigEndian(&buffer[20]);

  return true;
}

void ReportBlock::Create(uint8_t* buffer) const {
  // Runtime check should be done while setting cumulative_lost.
  RTC_DCHECK_LT(cumulative_lost_signed(),
                (1 << 23));  // Have only 3 bytes for it.

  ByteWriter<uint32_t>::WriteBigEndian(&buffer[0], source_ssrc());
  ByteWriter<uint8_t>::WriteBigEndian(&buffer[4], fraction_lost());
  ByteWriter<int32_t, 3>::WriteBigEndian(&buffer[5], cumulative_lost_signed());
  ByteWriter<uint32_t>::WriteBigEndian(&buffer[8], extended_high_seq_num());
  ByteWriter<uint32_t>::WriteBigEndian(&buffer[12], jitter());
  ByteWriter<uint32_t>::WriteBigEndian(&buffer[16], last_sr());
  ByteWriter<uint32_t>::WriteBigEndian(&buffer[20], delay_since_last_sr());
}

bool ReportBlock::SetCumulativeLost(int32_t cumulative_lost) {
  // We have only 3 bytes to store it, and it's a signed value.
  if (cumulative_lost >= (1 << 23) || cumulative_lost < -(1 << 23)) {
    RTC_LOG(LS_WARNING)
        << "Cumulative lost is too big to fit into Report Block";
    return false;
  }
  cumulative_lost_ = cumulative_lost;
  return true;
}

uint32_t ReportBlock::cumulative_lost() const {
  if (cumulative_lost_ < 0) {
    RTC_LOG(LS_VERBOSE) << "Ignoring negative value of cumulative_lost";
    return 0;
  }
  return cumulative_lost_;
}

}  // namespace rtcp
}  // namespace webrtc
