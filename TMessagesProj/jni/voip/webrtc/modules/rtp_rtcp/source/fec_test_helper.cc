/*
 *  Copyright (c) 2012 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "modules/rtp_rtcp/source/fec_test_helper.h"

#include <memory>
#include <utility>

#include "modules/rtp_rtcp/include/rtp_rtcp_defines.h"
#include "modules/rtp_rtcp/source/byte_io.h"
#include "modules/rtp_rtcp/source/rtp_packet.h"
#include "modules/rtp_rtcp/source/rtp_utility.h"
#include "rtc_base/checks.h"

namespace webrtc {
namespace test {
namespace fec {

namespace {
constexpr uint8_t kFecPayloadType = 96;
constexpr uint8_t kRedPayloadType = 97;
constexpr uint8_t kVp8PayloadType = 120;

constexpr int kPacketTimestampIncrement = 3000;
}  // namespace

MediaPacketGenerator::MediaPacketGenerator(uint32_t min_packet_size,
                                           uint32_t max_packet_size,
                                           uint32_t ssrc,
                                           Random* random)
    : min_packet_size_(min_packet_size),
      max_packet_size_(max_packet_size),
      ssrc_(ssrc),
      random_(random) {}

MediaPacketGenerator::~MediaPacketGenerator() = default;

ForwardErrorCorrection::PacketList MediaPacketGenerator::ConstructMediaPackets(
    int num_media_packets,
    uint16_t start_seq_num) {
  RTC_DCHECK_GT(num_media_packets, 0);
  uint16_t seq_num = start_seq_num;
  int time_stamp = random_->Rand<int>();

  ForwardErrorCorrection::PacketList media_packets;

  for (int i = 0; i < num_media_packets; ++i) {
    std::unique_ptr<ForwardErrorCorrection::Packet> media_packet(
        new ForwardErrorCorrection::Packet());
    media_packet->data.SetSize(
        random_->Rand(min_packet_size_, max_packet_size_));

    uint8_t* data = media_packet->data.MutableData();
    // Generate random values for the first 2 bytes
    data[0] = random_->Rand<uint8_t>();
    data[1] = random_->Rand<uint8_t>();

    // The first two bits are assumed to be 10 by the FEC encoder.
    // In fact the FEC decoder will set the two first bits to 10 regardless of
    // what they actually were. Set the first two bits to 10 so that a memcmp
    // can be performed for the whole restored packet.
    data[0] |= 0x80;
    data[0] &= 0xbf;

    // FEC is applied to a whole frame.
    // A frame is signaled by multiple packets without the marker bit set
    // followed by the last packet of the frame for which the marker bit is set.
    // Only push one (fake) frame to the FEC.
    data[1] &= 0x7f;

    webrtc::ByteWriter<uint16_t>::WriteBigEndian(&data[2], seq_num);
    webrtc::ByteWriter<uint32_t>::WriteBigEndian(&data[4], time_stamp);
    webrtc::ByteWriter<uint32_t>::WriteBigEndian(&data[8], ssrc_);

    // Generate random values for payload.
    for (size_t j = 12; j < media_packet->data.size(); ++j)
      data[j] = random_->Rand<uint8_t>();
    seq_num++;
    media_packets.push_back(std::move(media_packet));
  }
  // Last packet, set marker bit.
  ForwardErrorCorrection::Packet* media_packet = media_packets.back().get();
  RTC_DCHECK(media_packet);
  media_packet->data.MutableData()[1] |= 0x80;

  next_seq_num_ = seq_num;

  return media_packets;
}

ForwardErrorCorrection::PacketList MediaPacketGenerator::ConstructMediaPackets(
    int num_media_packets) {
  return ConstructMediaPackets(num_media_packets, random_->Rand<uint16_t>());
}

uint16_t MediaPacketGenerator::GetNextSeqNum() {
  return next_seq_num_;
}

AugmentedPacketGenerator::AugmentedPacketGenerator(uint32_t ssrc)
    : num_packets_(0), ssrc_(ssrc), seq_num_(0), timestamp_(0) {}

void AugmentedPacketGenerator::NewFrame(size_t num_packets) {
  num_packets_ = num_packets;
  timestamp_ += kPacketTimestampIncrement;
}

uint16_t AugmentedPacketGenerator::NextPacketSeqNum() {
  return ++seq_num_;
}

std::unique_ptr<AugmentedPacket> AugmentedPacketGenerator::NextPacket(
    size_t offset,
    size_t length) {
  std::unique_ptr<AugmentedPacket> packet(new AugmentedPacket());

  packet->data.SetSize(length + kRtpHeaderSize);
  uint8_t* data = packet->data.MutableData();
  for (size_t i = 0; i < length; ++i)
    data[i + kRtpHeaderSize] = offset + i;
  packet->data.SetSize(length + kRtpHeaderSize);
  packet->header.headerLength = kRtpHeaderSize;
  packet->header.markerBit = (num_packets_ == 1);
  packet->header.payloadType = kVp8PayloadType;
  packet->header.sequenceNumber = seq_num_;
  packet->header.timestamp = timestamp_;
  packet->header.ssrc = ssrc_;
  WriteRtpHeader(packet->header, data);
  ++seq_num_;
  --num_packets_;

  return packet;
}

void AugmentedPacketGenerator::WriteRtpHeader(const RTPHeader& header,
                                              uint8_t* data) {
  data[0] = 0x80;  // Version 2.
  data[1] = header.payloadType;
  data[1] |= (header.markerBit ? kRtpMarkerBitMask : 0);
  ByteWriter<uint16_t>::WriteBigEndian(data + 2, header.sequenceNumber);
  ByteWriter<uint32_t>::WriteBigEndian(data + 4, header.timestamp);
  ByteWriter<uint32_t>::WriteBigEndian(data + 8, header.ssrc);
}

FlexfecPacketGenerator::FlexfecPacketGenerator(uint32_t media_ssrc,
                                               uint32_t flexfec_ssrc)
    : AugmentedPacketGenerator(media_ssrc),
      flexfec_ssrc_(flexfec_ssrc),
      flexfec_seq_num_(0),
      flexfec_timestamp_(0) {}

std::unique_ptr<AugmentedPacket> FlexfecPacketGenerator::BuildFlexfecPacket(
    const ForwardErrorCorrection::Packet& packet) {
  RTC_DCHECK_LE(packet.data.size(),
                static_cast<size_t>(IP_PACKET_SIZE - kRtpHeaderSize));

  RTPHeader header;
  header.sequenceNumber = flexfec_seq_num_;
  ++flexfec_seq_num_;
  header.timestamp = flexfec_timestamp_;
  flexfec_timestamp_ += kPacketTimestampIncrement;
  header.ssrc = flexfec_ssrc_;

  std::unique_ptr<AugmentedPacket> packet_with_rtp_header(
      new AugmentedPacket());
  packet_with_rtp_header->data.SetSize(kRtpHeaderSize + packet.data.size());
  WriteRtpHeader(header, packet_with_rtp_header->data.MutableData());
  memcpy(packet_with_rtp_header->data.MutableData() + kRtpHeaderSize,
         packet.data.cdata(), packet.data.size());

  return packet_with_rtp_header;
}

UlpfecPacketGenerator::UlpfecPacketGenerator(uint32_t ssrc)
    : AugmentedPacketGenerator(ssrc) {}

RtpPacketReceived UlpfecPacketGenerator::BuildMediaRedPacket(
    const AugmentedPacket& packet,
    bool is_recovered) {
  // Create a temporary buffer used to wrap the media packet in RED.
  rtc::CopyOnWriteBuffer red_buffer;
  const size_t kHeaderLength = packet.header.headerLength;
  // Append header.
  red_buffer.SetData(packet.data.data(), kHeaderLength);
  // Find payload type and add it as RED header.
  uint8_t media_payload_type = red_buffer[1] & 0x7F;
  red_buffer.AppendData({media_payload_type});
  // Append rest of payload/padding.
  red_buffer.AppendData(
      packet.data.Slice(kHeaderLength, packet.data.size() - kHeaderLength));

  RtpPacketReceived red_packet;
  RTC_CHECK(red_packet.Parse(std::move(red_buffer)));
  red_packet.SetPayloadType(kRedPayloadType);
  red_packet.set_recovered(is_recovered);

  return red_packet;
}

RtpPacketReceived UlpfecPacketGenerator::BuildUlpfecRedPacket(
    const ForwardErrorCorrection::Packet& packet) {
  // Create a fake media packet to get a correct header. 1 byte RED header.
  ++num_packets_;
  std::unique_ptr<AugmentedPacket> fake_packet =
      NextPacket(0, packet.data.size() + 1);

  RtpPacketReceived red_packet;
  red_packet.Parse(fake_packet->data);
  red_packet.SetMarker(false);
  uint8_t* rtp_payload = red_packet.AllocatePayload(packet.data.size() + 1);
  rtp_payload[0] = kFecPayloadType;
  red_packet.SetPayloadType(kRedPayloadType);
  memcpy(rtp_payload + 1, packet.data.cdata(), packet.data.size());
  red_packet.set_recovered(false);

  return red_packet;
}

}  // namespace fec
}  // namespace test
}  // namespace webrtc
