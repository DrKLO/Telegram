/*
 *  Copyright (c) 2014 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

// Unit tests for test Packet class.

#include "modules/audio_coding/neteq/tools/packet.h"

#include "test/gtest.h"

namespace webrtc {
namespace test {

namespace {
const int kHeaderLengthBytes = 12;

void MakeRtpHeader(int payload_type,
                   int seq_number,
                   uint32_t timestamp,
                   uint32_t ssrc,
                   uint8_t* rtp_data) {
  rtp_data[0] = 0x80;
  rtp_data[1] = static_cast<uint8_t>(payload_type);
  rtp_data[2] = (seq_number >> 8) & 0xFF;
  rtp_data[3] = (seq_number)&0xFF;
  rtp_data[4] = timestamp >> 24;
  rtp_data[5] = (timestamp >> 16) & 0xFF;
  rtp_data[6] = (timestamp >> 8) & 0xFF;
  rtp_data[7] = timestamp & 0xFF;
  rtp_data[8] = ssrc >> 24;
  rtp_data[9] = (ssrc >> 16) & 0xFF;
  rtp_data[10] = (ssrc >> 8) & 0xFF;
  rtp_data[11] = ssrc & 0xFF;
}
}  // namespace

TEST(TestPacket, RegularPacket) {
  const size_t kPacketLengthBytes = 100;
  rtc::CopyOnWriteBuffer packet_memory(kPacketLengthBytes);
  const uint8_t kPayloadType = 17;
  const uint16_t kSequenceNumber = 4711;
  const uint32_t kTimestamp = 47114711;
  const uint32_t kSsrc = 0x12345678;
  MakeRtpHeader(kPayloadType, kSequenceNumber, kTimestamp, kSsrc,
                packet_memory.MutableData());
  const double kPacketTime = 1.0;
  Packet packet(std::move(packet_memory), kPacketTime);
  ASSERT_TRUE(packet.valid_header());
  EXPECT_EQ(kPayloadType, packet.header().payloadType);
  EXPECT_EQ(kSequenceNumber, packet.header().sequenceNumber);
  EXPECT_EQ(kTimestamp, packet.header().timestamp);
  EXPECT_EQ(kSsrc, packet.header().ssrc);
  EXPECT_EQ(0, packet.header().numCSRCs);
  EXPECT_EQ(kPacketLengthBytes, packet.packet_length_bytes());
  EXPECT_EQ(kPacketLengthBytes - kHeaderLengthBytes,
            packet.payload_length_bytes());
  EXPECT_EQ(kPacketLengthBytes, packet.virtual_packet_length_bytes());
  EXPECT_EQ(kPacketLengthBytes - kHeaderLengthBytes,
            packet.virtual_payload_length_bytes());
  EXPECT_EQ(kPacketTime, packet.time_ms());
}

TEST(TestPacket, DummyPacket) {
  const size_t kPacketLengthBytes = kHeaderLengthBytes;  // Only RTP header.
  const size_t kVirtualPacketLengthBytes = 100;
  rtc::CopyOnWriteBuffer packet_memory(kPacketLengthBytes);
  const uint8_t kPayloadType = 17;
  const uint16_t kSequenceNumber = 4711;
  const uint32_t kTimestamp = 47114711;
  const uint32_t kSsrc = 0x12345678;
  MakeRtpHeader(kPayloadType, kSequenceNumber, kTimestamp, kSsrc,
                packet_memory.MutableData());
  const double kPacketTime = 1.0;
  Packet packet(std::move(packet_memory), kVirtualPacketLengthBytes,
                kPacketTime);
  ASSERT_TRUE(packet.valid_header());
  EXPECT_EQ(kPayloadType, packet.header().payloadType);
  EXPECT_EQ(kSequenceNumber, packet.header().sequenceNumber);
  EXPECT_EQ(kTimestamp, packet.header().timestamp);
  EXPECT_EQ(kSsrc, packet.header().ssrc);
  EXPECT_EQ(0, packet.header().numCSRCs);
  EXPECT_EQ(kPacketLengthBytes, packet.packet_length_bytes());
  EXPECT_EQ(kPacketLengthBytes - kHeaderLengthBytes,
            packet.payload_length_bytes());
  EXPECT_EQ(kVirtualPacketLengthBytes, packet.virtual_packet_length_bytes());
  EXPECT_EQ(kVirtualPacketLengthBytes - kHeaderLengthBytes,
            packet.virtual_payload_length_bytes());
  EXPECT_EQ(kPacketTime, packet.time_ms());
}

TEST(TestPacket, DummyPaddingPacket) {
  const size_t kPacketLengthBytes = kHeaderLengthBytes;  // Only RTP header.
  const size_t kVirtualPacketLengthBytes = 100;
  rtc::CopyOnWriteBuffer packet_memory(kPacketLengthBytes);
  const uint8_t kPayloadType = 17;
  const uint16_t kSequenceNumber = 4711;
  const uint32_t kTimestamp = 47114711;
  const uint32_t kSsrc = 0x12345678;
  MakeRtpHeader(kPayloadType, kSequenceNumber, kTimestamp, kSsrc,
                packet_memory.MutableData());
  packet_memory.MutableData()[0] |= 0b0010'0000;  // Set the padding bit.
  const double kPacketTime = 1.0;
  Packet packet(std::move(packet_memory), kVirtualPacketLengthBytes,
                kPacketTime);
  ASSERT_TRUE(packet.valid_header());
  EXPECT_EQ(kPayloadType, packet.header().payloadType);
  EXPECT_EQ(kSequenceNumber, packet.header().sequenceNumber);
  EXPECT_EQ(kTimestamp, packet.header().timestamp);
  EXPECT_EQ(kSsrc, packet.header().ssrc);
  EXPECT_EQ(0, packet.header().numCSRCs);
  EXPECT_EQ(kPacketLengthBytes, packet.packet_length_bytes());
  EXPECT_EQ(kPacketLengthBytes - kHeaderLengthBytes,
            packet.payload_length_bytes());
  EXPECT_EQ(kVirtualPacketLengthBytes, packet.virtual_packet_length_bytes());
  EXPECT_EQ(kVirtualPacketLengthBytes - kHeaderLengthBytes,
            packet.virtual_payload_length_bytes());
  EXPECT_EQ(kPacketTime, packet.time_ms());
}

namespace {
// Writes one RED block header starting at `rtp_data`, according to RFC 2198.
// returns the number of bytes written (1 or 4).
//
// Format if `last_payoad` is false:
// 0                   1                    2                   3
// 0 1 2 3 4 5 6 7 8 9 0 1 2 3  4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1
// +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
// |1|   block PT  |  timestamp offset         |   block length    |
// +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
//
// Format if `last_payoad` is true:
// 0 1 2 3 4 5 6 7
// +-+-+-+-+-+-+-+-+
// |0|   Block PT  |
// +-+-+-+-+-+-+-+-+

int MakeRedHeader(int payload_type,
                  uint32_t timestamp_offset,
                  int block_length,
                  bool last_payload,
                  uint8_t* rtp_data) {
  rtp_data[0] = 0x80 | (payload_type & 0x7F);  // Set the first bit to 1.
  if (last_payload) {
    rtp_data[0] &= 0x7F;  // Reset the first but to 0 to indicate last block.
    return 1;
  }
  rtp_data[1] = timestamp_offset >> 6;
  rtp_data[2] = (timestamp_offset & 0x3F) << 2;
  rtp_data[2] |= block_length >> 8;
  rtp_data[3] = block_length & 0xFF;
  return 4;
}
}  // namespace

TEST(TestPacket, RED) {
  const size_t kPacketLengthBytes = 100;
  rtc::CopyOnWriteBuffer packet_memory(kPacketLengthBytes);
  const uint8_t kRedPayloadType = 17;
  const uint16_t kSequenceNumber = 4711;
  const uint32_t kTimestamp = 47114711;
  const uint32_t kSsrc = 0x12345678;
  MakeRtpHeader(kRedPayloadType, kSequenceNumber, kTimestamp, kSsrc,
                packet_memory.MutableData());
  // Create four RED headers.
  // Payload types are just the same as the block index the offset is 100 times
  // the block index.
  const int kRedBlocks = 4;
  uint8_t* payload_ptr = packet_memory.MutableData() +
                         kHeaderLengthBytes;  // First byte after header.
  for (int i = 0; i < kRedBlocks; ++i) {
    int payload_type = i;
    // Offset value is not used for the last block.
    uint32_t timestamp_offset = 100 * i;
    int block_length = 10 * i;
    bool last_block = (i == kRedBlocks - 1) ? true : false;
    payload_ptr += MakeRedHeader(payload_type, timestamp_offset, block_length,
                                 last_block, payload_ptr);
  }
  const double kPacketTime = 1.0;
  // Hand over ownership of `packet_memory` to `packet`.
  Packet packet(packet_memory, kPacketLengthBytes, kPacketTime);
  ASSERT_TRUE(packet.valid_header());
  EXPECT_EQ(kRedPayloadType, packet.header().payloadType);
  EXPECT_EQ(kSequenceNumber, packet.header().sequenceNumber);
  EXPECT_EQ(kTimestamp, packet.header().timestamp);
  EXPECT_EQ(kSsrc, packet.header().ssrc);
  EXPECT_EQ(0, packet.header().numCSRCs);
  EXPECT_EQ(kPacketLengthBytes, packet.packet_length_bytes());
  EXPECT_EQ(kPacketLengthBytes - kHeaderLengthBytes,
            packet.payload_length_bytes());
  EXPECT_EQ(kPacketLengthBytes, packet.virtual_packet_length_bytes());
  EXPECT_EQ(kPacketLengthBytes - kHeaderLengthBytes,
            packet.virtual_payload_length_bytes());
  EXPECT_EQ(kPacketTime, packet.time_ms());
  std::list<RTPHeader*> red_headers;
  EXPECT_TRUE(packet.ExtractRedHeaders(&red_headers));
  EXPECT_EQ(kRedBlocks, static_cast<int>(red_headers.size()));
  int block_index = 0;
  for (std::list<RTPHeader*>::reverse_iterator it = red_headers.rbegin();
       it != red_headers.rend(); ++it) {
    // Reading list from the back, since the extraction puts the main payload
    // (which is the last one on wire) first.
    RTPHeader* red_block = *it;
    EXPECT_EQ(block_index, red_block->payloadType);
    EXPECT_EQ(kSequenceNumber, red_block->sequenceNumber);
    if (block_index == kRedBlocks - 1) {
      // Last block has zero offset per definition.
      EXPECT_EQ(kTimestamp, red_block->timestamp);
    } else {
      EXPECT_EQ(kTimestamp - 100 * block_index, red_block->timestamp);
    }
    EXPECT_EQ(kSsrc, red_block->ssrc);
    EXPECT_EQ(0, red_block->numCSRCs);
    ++block_index;
  }
  Packet::DeleteRedHeaders(&red_headers);
}

}  // namespace test
}  // namespace webrtc
