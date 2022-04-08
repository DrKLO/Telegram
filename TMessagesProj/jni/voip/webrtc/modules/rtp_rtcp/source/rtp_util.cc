/*
 *  Copyright (c) 2021 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "modules/rtp_rtcp/source/rtp_util.h"

#include <cstddef>
#include <cstdint>

#include "api/array_view.h"
#include "modules/rtp_rtcp/source/byte_io.h"
#include "rtc_base/checks.h"

namespace webrtc {
namespace {

constexpr uint8_t kRtpVersion = 2;
constexpr size_t kMinRtpPacketLen = 12;
constexpr size_t kMinRtcpPacketLen = 4;

bool HasCorrectRtpVersion(rtc::ArrayView<const uint8_t> packet) {
  return packet[0] >> 6 == kRtpVersion;
}

// For additional details, see http://tools.ietf.org/html/rfc5761#section-4
bool PayloadTypeIsReservedForRtcp(uint8_t payload_type) {
  return 64 <= payload_type && payload_type < 96;
}

}  // namespace

bool IsRtpPacket(rtc::ArrayView<const uint8_t> packet) {
  return packet.size() >= kMinRtpPacketLen && HasCorrectRtpVersion(packet) &&
         !PayloadTypeIsReservedForRtcp(packet[1] & 0x7F);
}

bool IsRtcpPacket(rtc::ArrayView<const uint8_t> packet) {
  return packet.size() >= kMinRtcpPacketLen && HasCorrectRtpVersion(packet) &&
         PayloadTypeIsReservedForRtcp(packet[1] & 0x7F);
}

int ParseRtpPayloadType(rtc::ArrayView<const uint8_t> rtp_packet) {
  RTC_DCHECK(IsRtpPacket(rtp_packet));
  return rtp_packet[1] & 0x7F;
}

uint16_t ParseRtpSequenceNumber(rtc::ArrayView<const uint8_t> rtp_packet) {
  RTC_DCHECK(IsRtpPacket(rtp_packet));
  return ByteReader<uint16_t>::ReadBigEndian(rtp_packet.data() + 2);
}

uint32_t ParseRtpSsrc(rtc::ArrayView<const uint8_t> rtp_packet) {
  RTC_DCHECK(IsRtpPacket(rtp_packet));
  return ByteReader<uint32_t>::ReadBigEndian(rtp_packet.data() + 8);
}

}  // namespace webrtc
