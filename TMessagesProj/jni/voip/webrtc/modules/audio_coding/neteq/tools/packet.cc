/*
 *  Copyright (c) 2014 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "modules/audio_coding/neteq/tools/packet.h"

#include "api/array_view.h"
#include "modules/rtp_rtcp/source/rtp_packet_received.h"
#include "rtc_base/checks.h"
#include "rtc_base/copy_on_write_buffer.h"

namespace webrtc {
namespace test {

Packet::Packet(rtc::CopyOnWriteBuffer packet,
               size_t virtual_packet_length_bytes,
               double time_ms,
               const RtpHeaderExtensionMap* extension_map)
    : packet_(std::move(packet)),
      virtual_packet_length_bytes_(virtual_packet_length_bytes),
      time_ms_(time_ms),
      valid_header_(ParseHeader(extension_map)) {}

Packet::Packet(const RTPHeader& header,
               size_t virtual_packet_length_bytes,
               size_t virtual_payload_length_bytes,
               double time_ms)
    : header_(header),
      virtual_packet_length_bytes_(virtual_packet_length_bytes),
      virtual_payload_length_bytes_(virtual_payload_length_bytes),
      time_ms_(time_ms),
      valid_header_(true) {}

Packet::~Packet() = default;

bool Packet::ExtractRedHeaders(std::list<RTPHeader*>* headers) const {
  //
  //  0                   1                    2                   3
  //  0 1 2 3 4 5 6 7 8 9 0 1 2 3  4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1
  // +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
  // |1|   block PT  |  timestamp offset         |   block length    |
  // +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
  // |1|    ...                                                      |
  // +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
  // |0|   block PT  |
  // +-+-+-+-+-+-+-+-+
  //

  const uint8_t* payload_ptr = payload();
  const uint8_t* payload_end_ptr = payload_ptr + payload_length_bytes();

  // Find all RED headers with the extension bit set to 1. That is, all headers
  // but the last one.
  while ((payload_ptr < payload_end_ptr) && (*payload_ptr & 0x80)) {
    RTPHeader* header = new RTPHeader;
    CopyToHeader(header);
    header->payloadType = payload_ptr[0] & 0x7F;
    uint32_t offset = (payload_ptr[1] << 6) + ((payload_ptr[2] & 0xFC) >> 2);
    header->timestamp -= offset;
    headers->push_front(header);
    payload_ptr += 4;
  }
  // Last header.
  RTC_DCHECK_LT(payload_ptr, payload_end_ptr);
  if (payload_ptr >= payload_end_ptr) {
    return false;  // Payload too short.
  }
  RTPHeader* header = new RTPHeader;
  CopyToHeader(header);
  header->payloadType = payload_ptr[0] & 0x7F;
  headers->push_front(header);
  return true;
}

void Packet::DeleteRedHeaders(std::list<RTPHeader*>* headers) {
  while (!headers->empty()) {
    delete headers->front();
    headers->pop_front();
  }
}

bool Packet::ParseHeader(const RtpHeaderExtensionMap* extension_map) {
  // Use RtpPacketReceived instead of RtpPacket because former already has a
  // converter into legacy RTPHeader.
  webrtc::RtpPacketReceived rtp_packet(extension_map);

  // Because of the special case of dummy packets that have padding marked in
  // the RTP header, but do not have rtp payload with the padding size, handle
  // padding manually. Regular RTP packet parser reports failure, but it is fine
  // in this context.
  bool padding = (packet_[0] & 0b0010'0000);
  size_t padding_size = 0;
  if (padding) {
    // Clear the padding bit to prevent failure when rtp payload is omited.
    rtc::CopyOnWriteBuffer packet(packet_);
    packet.MutableData()[0] &= ~0b0010'0000;
    if (!rtp_packet.Parse(std::move(packet))) {
      return false;
    }
    if (rtp_packet.payload_size() > 0) {
      padding_size = rtp_packet.data()[rtp_packet.size() - 1];
    }
    if (padding_size > rtp_packet.payload_size()) {
      return false;
    }
  } else {
    if (!rtp_packet.Parse(packet_)) {
      return false;
    }
  }
  rtp_payload_ = rtc::MakeArrayView(packet_.data() + rtp_packet.headers_size(),
                                    rtp_packet.payload_size() - padding_size);
  rtp_packet.GetHeader(&header_);

  RTC_CHECK_GE(virtual_packet_length_bytes_, rtp_packet.size());
  RTC_DCHECK_GE(virtual_packet_length_bytes_, rtp_packet.headers_size());
  virtual_payload_length_bytes_ =
      virtual_packet_length_bytes_ - rtp_packet.headers_size();
  return true;
}

void Packet::CopyToHeader(RTPHeader* destination) const {
  *destination = header_;
}

}  // namespace test
}  // namespace webrtc
