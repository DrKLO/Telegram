/*
 *  Copyright (c) 2016 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "modules/rtp_rtcp/source/rtcp_packet/tmmb_item.h"

#include "modules/rtp_rtcp/source/byte_io.h"
#include "rtc_base/checks.h"
#include "rtc_base/logging.h"

namespace webrtc {
namespace rtcp {
TmmbItem::TmmbItem(uint32_t ssrc, uint64_t bitrate_bps, uint16_t overhead)
    : ssrc_(ssrc), bitrate_bps_(bitrate_bps), packet_overhead_(overhead) {
  RTC_DCHECK_LE(overhead, 0x1ffu);
}

//    0                   1                   2                   3
//    0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1
//   +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
// 0 |                              SSRC                             |
//   +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
// 4 | MxTBR Exp |  MxTBR Mantissa                 |Measured Overhead|
//   +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
bool TmmbItem::Parse(const uint8_t* buffer) {
  ssrc_ = ByteReader<uint32_t>::ReadBigEndian(&buffer[0]);
  // Read 4 bytes into 1 block.
  uint32_t compact = ByteReader<uint32_t>::ReadBigEndian(&buffer[4]);
  // Split 1 block into 3 components.
  uint8_t exponent = compact >> 26;              // 6 bits.
  uint64_t mantissa = (compact >> 9) & 0x1ffff;  // 17 bits.
  uint16_t overhead = compact & 0x1ff;           // 9 bits.
  // Combine 3 components into 2 values.
  bitrate_bps_ = (mantissa << exponent);

  bool shift_overflow = (bitrate_bps_ >> exponent) != mantissa;
  if (shift_overflow) {
    RTC_LOG(LS_ERROR) << "Invalid tmmb bitrate value : " << mantissa << "*2^"
                      << static_cast<int>(exponent);
    return false;
  }
  packet_overhead_ = overhead;
  return true;
}

void TmmbItem::Create(uint8_t* buffer) const {
  constexpr uint64_t kMaxMantissa = 0x1ffff;  // 17 bits.
  uint64_t mantissa = bitrate_bps_;
  uint32_t exponent = 0;
  while (mantissa > kMaxMantissa) {
    mantissa >>= 1;
    ++exponent;
  }

  ByteWriter<uint32_t>::WriteBigEndian(&buffer[0], ssrc_);
  uint32_t compact = (exponent << 26) | (mantissa << 9) | packet_overhead_;
  ByteWriter<uint32_t>::WriteBigEndian(&buffer[4], compact);
}

void TmmbItem::set_packet_overhead(uint16_t overhead) {
  RTC_DCHECK_LE(overhead, 0x1ffu);
  packet_overhead_ = overhead;
}
}  // namespace rtcp
}  // namespace webrtc
