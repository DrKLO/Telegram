/*
 *  Copyright (c) 2011 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "media/base/rtp_utils.h"

#include <string.h>

#include <vector>

// PacketTimeUpdateParams is defined in asyncpacketsocket.h.
// TODO(sergeyu): Find more appropriate place for PacketTimeUpdateParams.
#include "media/base/turn_utils.h"
#include "rtc_base/async_packet_socket.h"
#include "rtc_base/byte_order.h"
#include "rtc_base/checks.h"
#include "rtc_base/message_digest.h"

namespace cricket {

static const uint8_t kRtpVersion = 2;
static const size_t kRtpFlagsOffset = 0;
static const size_t kRtpPayloadTypeOffset = 1;
static const size_t kRtpSeqNumOffset = 2;
static const size_t kRtpTimestampOffset = 4;
static const size_t kRtpSsrcOffset = 8;
static const size_t kRtcpPayloadTypeOffset = 1;
static const size_t kRtpExtensionHeaderLen = 4;
static const size_t kAbsSendTimeExtensionLen = 3;
static const size_t kOneByteExtensionHeaderLen = 1;
static const size_t kTwoByteExtensionHeaderLen = 2;

namespace {

// Fake auth tag written by the sender when external authentication is enabled.
// HMAC in packet will be compared against this value before updating packet
// with actual HMAC value.
static const uint8_t kFakeAuthTag[10] = {0xba, 0xdd, 0xba, 0xdd, 0xba,
                                         0xdd, 0xba, 0xdd, 0xba, 0xdd};

void UpdateAbsSendTimeExtensionValue(uint8_t* extension_data,
                                     size_t length,
                                     uint64_t time_us) {
  // Absolute send time in RTP streams.
  //
  // The absolute send time is signaled to the receiver in-band using the
  // general mechanism for RTP header extensions [RFC5285]. The payload
  // of this extension (the transmitted value) is a 24-bit unsigned integer
  // containing the sender's current time in seconds as a fixed point number
  // with 18 bits fractional part.
  //
  // The form of the absolute send time extension block:
  //
  //    0                   1                   2                   3
  //    0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1
  //   +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
  //   |  ID   | len=2 |              absolute send time               |
  //   +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
  if (length != kAbsSendTimeExtensionLen) {
    RTC_NOTREACHED();
    return;
  }

  // Convert microseconds to a 6.18 fixed point value in seconds.
  uint32_t send_time = ((time_us << 18) / 1000000) & 0x00FFFFFF;
  extension_data[0] = static_cast<uint8_t>(send_time >> 16);
  extension_data[1] = static_cast<uint8_t>(send_time >> 8);
  extension_data[2] = static_cast<uint8_t>(send_time);
}

// Assumes |length| is actual packet length + tag length. Updates HMAC at end of
// the RTP packet.
void UpdateRtpAuthTag(uint8_t* rtp,
                      size_t length,
                      const rtc::PacketTimeUpdateParams& packet_time_params) {
  // If there is no key, return.
  if (packet_time_params.srtp_auth_key.empty()) {
    return;
  }

  size_t tag_length = packet_time_params.srtp_auth_tag_len;

  // ROC (rollover counter) is at the beginning of the auth tag.
  const size_t kRocLength = 4;
  if (tag_length < kRocLength || tag_length > length) {
    RTC_NOTREACHED();
    return;
  }

  uint8_t* auth_tag = rtp + (length - tag_length);

  // We should have a fake HMAC value @ auth_tag.
  RTC_DCHECK_EQ(0, memcmp(auth_tag, kFakeAuthTag, tag_length));

  // Copy ROC after end of rtp packet.
  memcpy(auth_tag, &packet_time_params.srtp_packet_index, kRocLength);
  // Authentication of a RTP packet will have RTP packet + ROC size.
  size_t auth_required_length = length - tag_length + kRocLength;

  uint8_t output[64];
  size_t result =
      rtc::ComputeHmac(rtc::DIGEST_SHA_1, &packet_time_params.srtp_auth_key[0],
                       packet_time_params.srtp_auth_key.size(), rtp,
                       auth_required_length, output, sizeof(output));

  if (result < tag_length) {
    RTC_NOTREACHED();
    return;
  }

  // Copy HMAC from output to packet. This is required as auth tag length
  // may not be equal to the actual HMAC length.
  memcpy(auth_tag, output, tag_length);
}

}  // namespace

bool GetUint8(const void* data, size_t offset, int* value) {
  if (!data || !value) {
    return false;
  }
  *value = *(static_cast<const uint8_t*>(data) + offset);
  return true;
}

bool GetUint16(const void* data, size_t offset, int* value) {
  if (!data || !value) {
    return false;
  }
  *value = static_cast<int>(
      rtc::GetBE16(static_cast<const uint8_t*>(data) + offset));
  return true;
}

bool GetUint32(const void* data, size_t offset, uint32_t* value) {
  if (!data || !value) {
    return false;
  }
  *value = rtc::GetBE32(static_cast<const uint8_t*>(data) + offset);
  return true;
}

bool SetUint8(void* data, size_t offset, uint8_t value) {
  if (!data) {
    return false;
  }
  rtc::Set8(data, offset, value);
  return true;
}

bool SetUint16(void* data, size_t offset, uint16_t value) {
  if (!data) {
    return false;
  }
  rtc::SetBE16(static_cast<uint8_t*>(data) + offset, value);
  return true;
}

bool SetUint32(void* data, size_t offset, uint32_t value) {
  if (!data) {
    return false;
  }
  rtc::SetBE32(static_cast<uint8_t*>(data) + offset, value);
  return true;
}

bool GetRtpFlags(const void* data, size_t len, int* value) {
  if (len < kMinRtpPacketLen) {
    return false;
  }
  return GetUint8(data, kRtpFlagsOffset, value);
}

bool GetRtpPayloadType(const void* data, size_t len, int* value) {
  if (len < kMinRtpPacketLen) {
    return false;
  }
  if (!GetUint8(data, kRtpPayloadTypeOffset, value)) {
    return false;
  }
  *value &= 0x7F;
  return true;
}

bool GetRtpSeqNum(const void* data, size_t len, int* value) {
  if (len < kMinRtpPacketLen) {
    return false;
  }
  return GetUint16(data, kRtpSeqNumOffset, value);
}

bool GetRtpTimestamp(const void* data, size_t len, uint32_t* value) {
  if (len < kMinRtpPacketLen) {
    return false;
  }
  return GetUint32(data, kRtpTimestampOffset, value);
}

bool GetRtpSsrc(const void* data, size_t len, uint32_t* value) {
  if (len < kMinRtpPacketLen) {
    return false;
  }
  return GetUint32(data, kRtpSsrcOffset, value);
}

bool GetRtpHeaderLen(const void* data, size_t len, size_t* value) {
  if (!data || len < kMinRtpPacketLen || !value)
    return false;
  const uint8_t* header = static_cast<const uint8_t*>(data);
  // Get base header size + length of CSRCs (not counting extension yet).
  size_t header_size = kMinRtpPacketLen + (header[0] & 0xF) * sizeof(uint32_t);
  if (len < header_size)
    return false;
  // If there's an extension, read and add in the extension size.
  if (header[0] & 0x10) {
    if (len < header_size + sizeof(uint32_t))
      return false;
    header_size +=
        ((rtc::GetBE16(header + header_size + 2) + 1) * sizeof(uint32_t));
    if (len < header_size)
      return false;
  }
  *value = header_size;
  return true;
}

bool GetRtpHeader(const void* data, size_t len, RtpHeader* header) {
  return (GetRtpPayloadType(data, len, &(header->payload_type)) &&
          GetRtpSeqNum(data, len, &(header->seq_num)) &&
          GetRtpTimestamp(data, len, &(header->timestamp)) &&
          GetRtpSsrc(data, len, &(header->ssrc)));
}

bool GetRtcpType(const void* data, size_t len, int* value) {
  if (len < kMinRtcpPacketLen) {
    return false;
  }
  return GetUint8(data, kRtcpPayloadTypeOffset, value);
}

// This method returns SSRC first of RTCP packet, except if packet is SDES.
// TODO(mallinath) - Fully implement RFC 5506. This standard doesn't restrict
// to send non-compound packets only to feedback messages.
bool GetRtcpSsrc(const void* data, size_t len, uint32_t* value) {
  // Packet should be at least of 8 bytes, to get SSRC from a RTCP packet.
  if (!data || len < kMinRtcpPacketLen + 4 || !value)
    return false;
  int pl_type;
  if (!GetRtcpType(data, len, &pl_type))
    return false;
  // SDES packet parsing is not supported.
  if (pl_type == kRtcpTypeSDES)
    return false;
  *value = rtc::GetBE32(static_cast<const uint8_t*>(data) + 4);
  return true;
}

bool SetRtpSsrc(void* data, size_t len, uint32_t value) {
  return SetUint32(data, kRtpSsrcOffset, value);
}

// Assumes version 2, no padding, no extensions, no csrcs.
bool SetRtpHeader(void* data, size_t len, const RtpHeader& header) {
  if (!IsValidRtpPayloadType(header.payload_type) || header.seq_num < 0 ||
      header.seq_num > static_cast<int>(UINT16_MAX)) {
    return false;
  }
  return (SetUint8(data, kRtpFlagsOffset, kRtpVersion << 6) &&
          SetUint8(data, kRtpPayloadTypeOffset, header.payload_type & 0x7F) &&
          SetUint16(data, kRtpSeqNumOffset,
                    static_cast<uint16_t>(header.seq_num)) &&
          SetUint32(data, kRtpTimestampOffset, header.timestamp) &&
          SetRtpSsrc(data, len, header.ssrc));
}

static bool HasCorrectRtpVersion(rtc::ArrayView<const uint8_t> packet) {
  return packet.data()[0] >> 6 == kRtpVersion;
}

bool IsRtpPacket(rtc::ArrayView<const char> packet) {
  return packet.size() >= kMinRtpPacketLen &&
         HasCorrectRtpVersion(
             rtc::reinterpret_array_view<const uint8_t>(packet));
}

// Check the RTP payload type. If 63 < payload type < 96, it's RTCP.
// For additional details, see http://tools.ietf.org/html/rfc5761.
bool IsRtcpPacket(rtc::ArrayView<const char> packet) {
  if (packet.size() < kMinRtcpPacketLen ||
      !HasCorrectRtpVersion(
          rtc::reinterpret_array_view<const uint8_t>(packet))) {
    return false;
  }

  char pt = packet[1] & 0x7F;
  return (63 < pt) && (pt < 96);
}

bool IsValidRtpPayloadType(int payload_type) {
  return payload_type >= 0 && payload_type <= 127;
}

bool IsValidRtpPacketSize(RtpPacketType packet_type, size_t size) {
  RTC_DCHECK_NE(RtpPacketType::kUnknown, packet_type);
  size_t min_packet_length = packet_type == RtpPacketType::kRtcp
                                 ? kMinRtcpPacketLen
                                 : kMinRtpPacketLen;
  return size >= min_packet_length && size <= kMaxRtpPacketLen;
}

absl::string_view RtpPacketTypeToString(RtpPacketType packet_type) {
  switch (packet_type) {
    case RtpPacketType::kRtp:
      return "RTP";
    case RtpPacketType::kRtcp:
      return "RTCP";
    case RtpPacketType::kUnknown:
      return "Unknown";
  }
  RTC_CHECK_NOTREACHED();
}

RtpPacketType InferRtpPacketType(rtc::ArrayView<const char> packet) {
  // RTCP packets are RTP packets so must check that first.
  if (IsRtcpPacket(packet)) {
    return RtpPacketType::kRtcp;
  }
  if (IsRtpPacket(packet)) {
    return RtpPacketType::kRtp;
  }
  return RtpPacketType::kUnknown;
}

bool ValidateRtpHeader(const uint8_t* rtp,
                       size_t length,
                       size_t* header_length) {
  if (header_length) {
    *header_length = 0;
  }

  if (length < kMinRtpPacketLen) {
    return false;
  }

  size_t cc_count = rtp[0] & 0x0F;
  size_t header_length_without_extension = kMinRtpPacketLen + 4 * cc_count;
  if (header_length_without_extension > length) {
    return false;
  }

  // If extension bit is not set, we are done with header processing, as input
  // length is verified above.
  if (!(rtp[0] & 0x10)) {
    if (header_length)
      *header_length = header_length_without_extension;

    return true;
  }

  rtp += header_length_without_extension;

  if (header_length_without_extension + kRtpExtensionHeaderLen > length) {
    return false;
  }

  // Getting extension profile length.
  // Length is in 32 bit words.
  uint16_t extension_length_in_32bits = rtc::GetBE16(rtp + 2);
  size_t extension_length = extension_length_in_32bits * 4;

  size_t rtp_header_length = extension_length +
                             header_length_without_extension +
                             kRtpExtensionHeaderLen;

  // Verify input length against total header size.
  if (rtp_header_length > length) {
    return false;
  }

  if (header_length) {
    *header_length = rtp_header_length;
  }
  return true;
}

// ValidateRtpHeader() must be called before this method to make sure, we have
// a sane rtp packet.
bool UpdateRtpAbsSendTimeExtension(uint8_t* rtp,
                                   size_t length,
                                   int extension_id,
                                   uint64_t time_us) {
  //  0                   1                   2                   3
  //  0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1
  // +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
  // |V=2|P|X|  CC   |M|     PT      |       sequence number         |
  // +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
  // |                           timestamp                           |
  // +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
  // |           synchronization source (SSRC) identifier            |
  // +=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+
  // |            contributing source (CSRC) identifiers             |
  // |                             ....                              |
  // +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+

  // Return if extension bit is not set.
  if (!(rtp[0] & 0x10)) {
    return true;
  }

  size_t cc_count = rtp[0] & 0x0F;
  size_t header_length_without_extension = kMinRtpPacketLen + 4 * cc_count;

  rtp += header_length_without_extension;

  // Getting extension profile ID and length.
  uint16_t profile_id = rtc::GetBE16(rtp);
  // Length is in 32 bit words.
  uint16_t extension_length_in_32bits = rtc::GetBE16(rtp + 2);
  size_t extension_length = extension_length_in_32bits * 4;

  rtp += kRtpExtensionHeaderLen;  // Moving past extension header.

  constexpr uint16_t kOneByteExtensionProfileId = 0xBEDE;
  constexpr uint16_t kTwoByteExtensionProfileId = 0x1000;

  bool found = false;
  if (profile_id == kOneByteExtensionProfileId ||
      profile_id == kTwoByteExtensionProfileId) {
    // OneByte extension header
    //  0
    //  0 1 2 3 4 5 6 7
    // +-+-+-+-+-+-+-+-+
    // |  ID   |length |
    // +-+-+-+-+-+-+-+-+

    //  0                   1                   2                   3
    //  0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1
    // +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
    // |       0xBE    |    0xDE       |           length=3            |
    // +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
    // |  ID   | L=0   |     data      |  ID   |  L=1  |   data...
    // +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
    //       ...data   |    0 (pad)    |    0 (pad)    |  ID   | L=3   |
    // +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
    // |                          data                                 |
    // +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+

    // TwoByte extension header
    //  0
    //  0 1 2 3 4 5 6 7
    // +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
    // |      ID       |    length     |
    // +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+

    //  0                   1                   2                   3
    //  0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1
    // +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
    // |     0x10      |     0x00      |           length=3            |
    // +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
    // |      ID       |      L=1      |     data      |      ID       |
    // +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
    // |      L=2      |             data              |    0 (pad)    |
    // +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
    // |      ID       |      L=2      |             data              |
    // +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+

    size_t extension_header_length = profile_id == kOneByteExtensionProfileId
                                         ? kOneByteExtensionHeaderLen
                                         : kTwoByteExtensionHeaderLen;

    const uint8_t* extension_start = rtp;
    const uint8_t* extension_end = extension_start + extension_length;

    // rtp + 1 since the minimum size per header extension is two bytes for both
    // one- and two-byte header extensions.
    while (rtp + 1 < extension_end) {
      // See RFC8285 Section 4.2-4.3 for more information about one- and
      // two-byte header extensions.
      const int id =
          profile_id == kOneByteExtensionProfileId ? (*rtp & 0xF0) >> 4 : *rtp;
      const size_t length = profile_id == kOneByteExtensionProfileId
                                ? (*rtp & 0x0F) + 1
                                : *(rtp + 1);
      if (rtp + extension_header_length + length > extension_end) {
        return false;
      }
      if (id == extension_id) {
        UpdateAbsSendTimeExtensionValue(rtp + extension_header_length, length,
                                        time_us);
        found = true;
        break;
      }
      rtp += extension_header_length + length;
      // Counting padding bytes.
      while ((rtp < extension_end) && (*rtp == 0)) {
        ++rtp;
      }
    }
  }
  return found;
}

bool ApplyPacketOptions(uint8_t* data,
                        size_t length,
                        const rtc::PacketTimeUpdateParams& packet_time_params,
                        uint64_t time_us) {
  RTC_DCHECK(data);
  RTC_DCHECK(length);

  // if there is no valid |rtp_sendtime_extension_id| and |srtp_auth_key| in
  // PacketOptions, nothing to be updated in this packet.
  if (packet_time_params.rtp_sendtime_extension_id == -1 &&
      packet_time_params.srtp_auth_key.empty()) {
    return true;
  }

  // If there is a srtp auth key present then the packet must be an RTP packet.
  // RTP packet may have been wrapped in a TURN Channel Data or TURN send
  // indication.
  size_t rtp_start_pos;
  size_t rtp_length;
  if (!UnwrapTurnPacket(data, length, &rtp_start_pos, &rtp_length)) {
    RTC_NOTREACHED();
    return false;
  }

  // Making sure we have a valid RTP packet at the end.
  auto packet = rtc::MakeArrayView(data + rtp_start_pos, rtp_length);
  if (!IsRtpPacket(rtc::reinterpret_array_view<const char>(packet)) ||
      !ValidateRtpHeader(data + rtp_start_pos, rtp_length, nullptr)) {
    RTC_NOTREACHED();
    return false;
  }

  uint8_t* start = data + rtp_start_pos;
  // If packet option has non default value (-1) for sendtime extension id,
  // then we should parse the rtp packet to update the timestamp. Otherwise
  // just calculate HMAC and update packet with it.
  if (packet_time_params.rtp_sendtime_extension_id != -1) {
    UpdateRtpAbsSendTimeExtension(start, rtp_length,
                                  packet_time_params.rtp_sendtime_extension_id,
                                  time_us);
  }

  UpdateRtpAuthTag(start, rtp_length, packet_time_params);
  return true;
}

}  // namespace cricket
