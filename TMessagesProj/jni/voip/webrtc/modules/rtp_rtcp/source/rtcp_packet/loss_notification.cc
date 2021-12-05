/*
 *  Copyright (c) 2019 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "modules/rtp_rtcp/source/rtcp_packet/loss_notification.h"

#include "modules/rtp_rtcp/source/byte_io.h"
#include "modules/rtp_rtcp/source/rtcp_packet/common_header.h"
#include "rtc_base/checks.h"
#include "rtc_base/logging.h"

namespace webrtc {
namespace rtcp {

// Loss Notification
// -----------------
//     0                   1                   2                   3
//     0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1
//    +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
//    |V=2|P| FMT=15  |   PT=206      |             length            |
//    +=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+
//  0 |                  SSRC of packet sender                        |
//    +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
//  4 |                  SSRC of media source                         |
//    +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
//  8 |  Unique identifier 'L' 'N' 'T' 'F'                            |
//    +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
// 12 | Last Decoded Sequence Number  | Last Received SeqNum Delta  |D|
//    +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+

LossNotification::LossNotification()
    : last_decoded_(0), last_received_(0), decodability_flag_(false) {}

LossNotification::LossNotification(uint16_t last_decoded,
                                   uint16_t last_received,
                                   bool decodability_flag)
    : last_decoded_(last_decoded),
      last_received_(last_received),
      decodability_flag_(decodability_flag) {}

LossNotification::LossNotification(const LossNotification& rhs) = default;

LossNotification::~LossNotification() = default;

size_t LossNotification::BlockLength() const {
  return kHeaderLength + kCommonFeedbackLength + kLossNotificationPayloadLength;
}

bool LossNotification::Create(uint8_t* packet,
                              size_t* index,
                              size_t max_length,
                              PacketReadyCallback callback) const {
  while (*index + BlockLength() > max_length) {
    if (!OnBufferFull(packet, index, callback))
      return false;
  }

  const size_t index_end = *index + BlockLength();

  // Note: |index| updated by the function below.
  CreateHeader(Psfb::kAfbMessageType, kPacketType, HeaderLength(), packet,
               index);

  CreateCommonFeedback(packet + *index);
  *index += kCommonFeedbackLength;

  ByteWriter<uint32_t>::WriteBigEndian(packet + *index, kUniqueIdentifier);
  *index += sizeof(uint32_t);

  ByteWriter<uint16_t>::WriteBigEndian(packet + *index, last_decoded_);
  *index += sizeof(uint16_t);

  const uint16_t last_received_delta = last_received_ - last_decoded_;
  RTC_DCHECK_LE(last_received_delta, 0x7fff);
  const uint16_t last_received_delta_and_decodability =
      (last_received_delta << 1) | (decodability_flag_ ? 0x0001 : 0x0000);

  ByteWriter<uint16_t>::WriteBigEndian(packet + *index,
                                       last_received_delta_and_decodability);
  *index += sizeof(uint16_t);

  RTC_DCHECK_EQ(index_end, *index);
  return true;
}

bool LossNotification::Parse(const CommonHeader& packet) {
  RTC_DCHECK_EQ(packet.type(), kPacketType);
  RTC_DCHECK_EQ(packet.fmt(), Psfb::kAfbMessageType);

  if (packet.payload_size_bytes() <
      kCommonFeedbackLength + kLossNotificationPayloadLength) {
    return false;
  }

  const uint8_t* const payload = packet.payload();

  if (ByteReader<uint32_t>::ReadBigEndian(&payload[8]) != kUniqueIdentifier) {
    return false;
  }

  ParseCommonFeedback(payload);

  last_decoded_ = ByteReader<uint16_t>::ReadBigEndian(&payload[12]);

  const uint16_t last_received_delta_and_decodability =
      ByteReader<uint16_t>::ReadBigEndian(&payload[14]);
  last_received_ = last_decoded_ + (last_received_delta_and_decodability >> 1);
  decodability_flag_ = (last_received_delta_and_decodability & 0x0001);

  return true;
}

bool LossNotification::Set(uint16_t last_decoded,
                           uint16_t last_received,
                           bool decodability_flag) {
  const uint16_t delta = last_received - last_decoded;
  if (delta > 0x7fff) {
    return false;
  }
  last_received_ = last_received;
  last_decoded_ = last_decoded;
  decodability_flag_ = decodability_flag;
  return true;
}

}  // namespace rtcp
}  // namespace webrtc
