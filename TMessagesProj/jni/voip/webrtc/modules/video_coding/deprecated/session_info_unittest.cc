/*
 *  Copyright (c) 2012 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "modules/video_coding/deprecated/session_info.h"

#include <string.h>

#include "modules/video_coding/deprecated/packet.h"
#include "test/gtest.h"

namespace webrtc {

class TestSessionInfo : public ::testing::Test {
 protected:
  virtual void SetUp() {
    memset(packet_buffer_, 0, sizeof(packet_buffer_));
    memset(frame_buffer_, 0, sizeof(frame_buffer_));
    session_.Reset();
    packet_.video_header.frame_type = VideoFrameType::kVideoFrameDelta;
    packet_.sizeBytes = packet_buffer_size();
    packet_.dataPtr = packet_buffer_;
    packet_.seqNum = 0;
    packet_.timestamp = 0;
    frame_data.rtt_ms = 0;
    frame_data.rolling_average_packets_per_frame = -1;
  }

  void FillPacket(uint8_t start_value) {
    for (size_t i = 0; i < packet_buffer_size(); ++i)
      packet_buffer_[i] = start_value + i;
  }

  void VerifyPacket(uint8_t* start_ptr, uint8_t start_value) {
    for (size_t j = 0; j < packet_buffer_size(); ++j) {
      ASSERT_EQ(start_value + j, start_ptr[j]);
    }
  }

  size_t packet_buffer_size() const {
    return sizeof(packet_buffer_) / sizeof(packet_buffer_[0]);
  }
  size_t frame_buffer_size() const {
    return sizeof(frame_buffer_) / sizeof(frame_buffer_[0]);
  }

  enum { kPacketBufferSize = 10 };

  uint8_t packet_buffer_[kPacketBufferSize];
  uint8_t frame_buffer_[10 * kPacketBufferSize];

  VCMSessionInfo session_;
  VCMPacket packet_;
  FrameData frame_data;
};

class TestNalUnits : public TestSessionInfo {
 protected:
  virtual void SetUp() {
    TestSessionInfo::SetUp();
    packet_.video_header.codec = kVideoCodecVP8;
  }

  bool VerifyNalu(int offset, int packets_expected, int start_value) {
    EXPECT_GE(session_.SessionLength(),
              packets_expected * packet_buffer_size());
    for (int i = 0; i < packets_expected; ++i) {
      int packet_index = (offset + i) * packet_buffer_size();
      VerifyPacket(frame_buffer_ + packet_index, start_value + i);
    }
    return true;
  }
};

class TestNackList : public TestSessionInfo {
 protected:
  static const size_t kMaxSeqNumListLength = 30;

  virtual void SetUp() {
    TestSessionInfo::SetUp();
    seq_num_list_length_ = 0;
    memset(seq_num_list_, 0, sizeof(seq_num_list_));
  }

  void BuildSeqNumList(uint16_t low, uint16_t high) {
    size_t i = 0;
    while (low != high + 1) {
      EXPECT_LT(i, kMaxSeqNumListLength);
      if (i >= kMaxSeqNumListLength) {
        seq_num_list_length_ = kMaxSeqNumListLength;
        return;
      }
      seq_num_list_[i] = low;
      low++;
      i++;
    }
    seq_num_list_length_ = i;
  }

  void VerifyAll(int value) {
    for (int i = 0; i < seq_num_list_length_; ++i)
      EXPECT_EQ(seq_num_list_[i], value);
  }

  int seq_num_list_[kMaxSeqNumListLength];
  int seq_num_list_length_;
};

TEST_F(TestSessionInfo, TestSimpleAPIs) {
  packet_.video_header.is_first_packet_in_frame = true;
  packet_.seqNum = 0xFFFE;
  packet_.sizeBytes = packet_buffer_size();
  packet_.video_header.frame_type = VideoFrameType::kVideoFrameKey;
  FillPacket(0);
  EXPECT_EQ(packet_buffer_size(), static_cast<size_t>(session_.InsertPacket(
                                      packet_, frame_buffer_, frame_data)));
  EXPECT_FALSE(session_.HaveLastPacket());
  EXPECT_EQ(VideoFrameType::kVideoFrameKey, session_.FrameType());

  packet_.video_header.is_first_packet_in_frame = false;
  packet_.markerBit = true;
  packet_.seqNum += 1;
  EXPECT_EQ(packet_buffer_size(), static_cast<size_t>(session_.InsertPacket(
                                      packet_, frame_buffer_, frame_data)));
  EXPECT_TRUE(session_.HaveLastPacket());
  EXPECT_EQ(packet_.seqNum, session_.HighSequenceNumber());
  EXPECT_EQ(0xFFFE, session_.LowSequenceNumber());

  // Insert empty packet which will be the new high sequence number.
  // To make things more difficult we will make sure to have a wrap here.
  packet_.video_header.is_first_packet_in_frame = false;
  packet_.markerBit = true;
  packet_.seqNum = 2;
  packet_.sizeBytes = 0;
  packet_.video_header.frame_type = VideoFrameType::kEmptyFrame;
  EXPECT_EQ(0, session_.InsertPacket(packet_, frame_buffer_, frame_data));
  EXPECT_EQ(packet_.seqNum, session_.HighSequenceNumber());
}

TEST_F(TestSessionInfo, NormalOperation) {
  packet_.seqNum = 0xFFFF;
  packet_.video_header.is_first_packet_in_frame = true;
  packet_.markerBit = false;
  FillPacket(0);
  EXPECT_EQ(packet_buffer_size(), static_cast<size_t>(session_.InsertPacket(
                                      packet_, frame_buffer_, frame_data)));

  packet_.video_header.is_first_packet_in_frame = false;
  for (int i = 1; i < 9; ++i) {
    packet_.seqNum += 1;
    FillPacket(i);
    ASSERT_EQ(packet_buffer_size(), static_cast<size_t>(session_.InsertPacket(
                                        packet_, frame_buffer_, frame_data)));
  }

  packet_.seqNum += 1;
  packet_.markerBit = true;
  FillPacket(9);
  EXPECT_EQ(packet_buffer_size(), static_cast<size_t>(session_.InsertPacket(
                                      packet_, frame_buffer_, frame_data)));

  EXPECT_EQ(10 * packet_buffer_size(), session_.SessionLength());
  for (int i = 0; i < 10; ++i) {
    SCOPED_TRACE("Calling VerifyPacket");
    VerifyPacket(frame_buffer_ + i * packet_buffer_size(), i);
  }
}

TEST_F(TestSessionInfo, OutOfBoundsPackets1PacketFrame) {
  packet_.seqNum = 0x0001;
  packet_.video_header.is_first_packet_in_frame = true;
  packet_.markerBit = true;
  FillPacket(1);
  EXPECT_EQ(packet_buffer_size(), static_cast<size_t>(session_.InsertPacket(
                                      packet_, frame_buffer_, frame_data)));

  packet_.seqNum = 0x0004;
  packet_.video_header.is_first_packet_in_frame = true;
  packet_.markerBit = true;
  FillPacket(1);
  EXPECT_EQ(-3, session_.InsertPacket(packet_, frame_buffer_, frame_data));
  packet_.seqNum = 0x0000;
  packet_.video_header.is_first_packet_in_frame = false;
  packet_.markerBit = false;
  FillPacket(1);
  EXPECT_EQ(-3, session_.InsertPacket(packet_, frame_buffer_, frame_data));
}

TEST_F(TestSessionInfo, SetMarkerBitOnce) {
  packet_.seqNum = 0x0005;
  packet_.video_header.is_first_packet_in_frame = false;
  packet_.markerBit = true;
  FillPacket(1);
  EXPECT_EQ(packet_buffer_size(), static_cast<size_t>(session_.InsertPacket(
                                      packet_, frame_buffer_, frame_data)));
  ++packet_.seqNum;
  packet_.video_header.is_first_packet_in_frame = true;
  packet_.markerBit = true;
  FillPacket(1);
  EXPECT_EQ(-3, session_.InsertPacket(packet_, frame_buffer_, frame_data));
}

TEST_F(TestSessionInfo, OutOfBoundsPacketsBase) {
  // Allow packets in the range 5-6.
  packet_.seqNum = 0x0005;
  packet_.video_header.is_first_packet_in_frame = true;
  packet_.markerBit = false;
  FillPacket(1);
  EXPECT_EQ(packet_buffer_size(), static_cast<size_t>(session_.InsertPacket(
                                      packet_, frame_buffer_, frame_data)));
  // Insert an older packet with a first packet set.
  packet_.seqNum = 0x0004;
  packet_.video_header.is_first_packet_in_frame = true;
  packet_.markerBit = true;
  FillPacket(1);
  EXPECT_EQ(-3, session_.InsertPacket(packet_, frame_buffer_, frame_data));
  packet_.seqNum = 0x0006;
  packet_.video_header.is_first_packet_in_frame = true;
  packet_.markerBit = true;
  FillPacket(1);
  EXPECT_EQ(packet_buffer_size(), static_cast<size_t>(session_.InsertPacket(
                                      packet_, frame_buffer_, frame_data)));
  packet_.seqNum = 0x0008;
  packet_.video_header.is_first_packet_in_frame = false;
  packet_.markerBit = true;
  FillPacket(1);
  EXPECT_EQ(-3, session_.InsertPacket(packet_, frame_buffer_, frame_data));
}

TEST_F(TestSessionInfo, OutOfBoundsPacketsWrap) {
  packet_.seqNum = 0xFFFE;
  packet_.video_header.is_first_packet_in_frame = true;
  packet_.markerBit = false;
  FillPacket(1);
  EXPECT_EQ(packet_buffer_size(), static_cast<size_t>(session_.InsertPacket(
                                      packet_, frame_buffer_, frame_data)));

  packet_.seqNum = 0x0004;
  packet_.video_header.is_first_packet_in_frame = false;
  packet_.markerBit = true;
  FillPacket(1);
  EXPECT_EQ(packet_buffer_size(), static_cast<size_t>(session_.InsertPacket(
                                      packet_, frame_buffer_, frame_data)));
  packet_.seqNum = 0x0002;
  packet_.video_header.is_first_packet_in_frame = false;
  packet_.markerBit = false;
  FillPacket(1);
  ASSERT_EQ(packet_buffer_size(), static_cast<size_t>(session_.InsertPacket(
                                      packet_, frame_buffer_, frame_data)));
  packet_.seqNum = 0xFFF0;
  packet_.video_header.is_first_packet_in_frame = false;
  packet_.markerBit = false;
  FillPacket(1);
  EXPECT_EQ(-3, session_.InsertPacket(packet_, frame_buffer_, frame_data));
  packet_.seqNum = 0x0006;
  packet_.video_header.is_first_packet_in_frame = false;
  packet_.markerBit = false;
  FillPacket(1);
  EXPECT_EQ(-3, session_.InsertPacket(packet_, frame_buffer_, frame_data));
}

TEST_F(TestSessionInfo, OutOfBoundsOutOfOrder) {
  // Insert out of bound regular packets, and then the first and last packet.
  // Verify that correct bounds are maintained.
  packet_.seqNum = 0x0003;
  packet_.video_header.is_first_packet_in_frame = false;
  packet_.markerBit = false;
  FillPacket(1);
  EXPECT_EQ(packet_buffer_size(), static_cast<size_t>(session_.InsertPacket(
                                      packet_, frame_buffer_, frame_data)));
  // Insert an older packet with a first packet set.
  packet_.seqNum = 0x0005;
  packet_.video_header.is_first_packet_in_frame = true;
  packet_.markerBit = false;
  FillPacket(1);
  EXPECT_EQ(packet_buffer_size(), static_cast<size_t>(session_.InsertPacket(
                                      packet_, frame_buffer_, frame_data)));
  packet_.seqNum = 0x0004;
  packet_.video_header.is_first_packet_in_frame = false;
  packet_.markerBit = false;
  FillPacket(1);
  EXPECT_EQ(-3, session_.InsertPacket(packet_, frame_buffer_, frame_data));
  packet_.seqNum = 0x0010;
  packet_.video_header.is_first_packet_in_frame = false;
  packet_.markerBit = false;
  FillPacket(1);
  EXPECT_EQ(packet_buffer_size(), static_cast<size_t>(session_.InsertPacket(
                                      packet_, frame_buffer_, frame_data)));
  packet_.seqNum = 0x0008;
  packet_.video_header.is_first_packet_in_frame = false;
  packet_.markerBit = true;
  FillPacket(1);
  EXPECT_EQ(packet_buffer_size(), static_cast<size_t>(session_.InsertPacket(
                                      packet_, frame_buffer_, frame_data)));

  packet_.seqNum = 0x0009;
  packet_.video_header.is_first_packet_in_frame = false;
  packet_.markerBit = false;
  FillPacket(1);
  EXPECT_EQ(-3, session_.InsertPacket(packet_, frame_buffer_, frame_data));
}

TEST_F(TestNalUnits, OnlyReceivedEmptyPacket) {
  packet_.video_header.is_first_packet_in_frame = false;
  packet_.completeNALU = kNaluComplete;
  packet_.video_header.frame_type = VideoFrameType::kEmptyFrame;
  packet_.sizeBytes = 0;
  packet_.seqNum = 0;
  packet_.markerBit = false;
  EXPECT_EQ(0, session_.InsertPacket(packet_, frame_buffer_, frame_data));

  EXPECT_EQ(0U, session_.MakeDecodable());
  EXPECT_EQ(0U, session_.SessionLength());
}

TEST_F(TestNalUnits, OneIsolatedNaluLoss) {
  packet_.video_header.is_first_packet_in_frame = true;
  packet_.completeNALU = kNaluComplete;
  packet_.seqNum = 0;
  packet_.markerBit = false;
  FillPacket(0);
  EXPECT_EQ(packet_buffer_size(), static_cast<size_t>(session_.InsertPacket(
                                      packet_, frame_buffer_, frame_data)));

  packet_.video_header.is_first_packet_in_frame = false;
  packet_.completeNALU = kNaluComplete;
  packet_.seqNum += 2;
  packet_.markerBit = true;
  FillPacket(2);
  EXPECT_EQ(packet_buffer_size(), static_cast<size_t>(session_.InsertPacket(
                                      packet_, frame_buffer_, frame_data)));

  EXPECT_EQ(0U, session_.MakeDecodable());
  EXPECT_EQ(2 * packet_buffer_size(), session_.SessionLength());
  SCOPED_TRACE("Calling VerifyNalu");
  EXPECT_TRUE(VerifyNalu(0, 1, 0));
  SCOPED_TRACE("Calling VerifyNalu");
  EXPECT_TRUE(VerifyNalu(1, 1, 2));
}

TEST_F(TestNalUnits, LossInMiddleOfNalu) {
  packet_.video_header.is_first_packet_in_frame = true;
  packet_.completeNALU = kNaluComplete;
  packet_.seqNum = 0;
  packet_.markerBit = false;
  FillPacket(0);
  EXPECT_EQ(packet_buffer_size(), static_cast<size_t>(session_.InsertPacket(
                                      packet_, frame_buffer_, frame_data)));

  packet_.video_header.is_first_packet_in_frame = false;
  packet_.completeNALU = kNaluEnd;
  packet_.seqNum += 2;
  packet_.markerBit = true;
  FillPacket(2);
  EXPECT_EQ(packet_buffer_size(), static_cast<size_t>(session_.InsertPacket(
                                      packet_, frame_buffer_, frame_data)));

  EXPECT_EQ(packet_buffer_size(), session_.MakeDecodable());
  EXPECT_EQ(packet_buffer_size(), session_.SessionLength());
  SCOPED_TRACE("Calling VerifyNalu");
  EXPECT_TRUE(VerifyNalu(0, 1, 0));
}

TEST_F(TestNalUnits, StartAndEndOfLastNalUnitLost) {
  packet_.video_header.is_first_packet_in_frame = true;
  packet_.completeNALU = kNaluComplete;
  packet_.seqNum = 0;
  packet_.markerBit = false;
  FillPacket(0);
  EXPECT_EQ(packet_buffer_size(), static_cast<size_t>(session_.InsertPacket(
                                      packet_, frame_buffer_, frame_data)));

  packet_.video_header.is_first_packet_in_frame = false;
  packet_.completeNALU = kNaluIncomplete;
  packet_.seqNum += 2;
  packet_.markerBit = false;
  FillPacket(1);
  EXPECT_EQ(packet_buffer_size(), static_cast<size_t>(session_.InsertPacket(
                                      packet_, frame_buffer_, frame_data)));

  EXPECT_EQ(packet_buffer_size(), session_.MakeDecodable());
  EXPECT_EQ(packet_buffer_size(), session_.SessionLength());
  SCOPED_TRACE("Calling VerifyNalu");
  EXPECT_TRUE(VerifyNalu(0, 1, 0));
}

TEST_F(TestNalUnits, ReorderWrapNoLoss) {
  packet_.seqNum = 0xFFFF;
  packet_.video_header.is_first_packet_in_frame = false;
  packet_.completeNALU = kNaluIncomplete;
  packet_.seqNum += 1;
  packet_.markerBit = false;
  FillPacket(1);
  EXPECT_EQ(packet_buffer_size(), static_cast<size_t>(session_.InsertPacket(
                                      packet_, frame_buffer_, frame_data)));

  packet_.video_header.is_first_packet_in_frame = true;
  packet_.completeNALU = kNaluComplete;
  packet_.seqNum -= 1;
  packet_.markerBit = false;
  FillPacket(0);
  EXPECT_EQ(packet_buffer_size(), static_cast<size_t>(session_.InsertPacket(
                                      packet_, frame_buffer_, frame_data)));

  packet_.video_header.is_first_packet_in_frame = false;
  packet_.completeNALU = kNaluEnd;
  packet_.seqNum += 2;
  packet_.markerBit = true;
  FillPacket(2);
  EXPECT_EQ(packet_buffer_size(), static_cast<size_t>(session_.InsertPacket(
                                      packet_, frame_buffer_, frame_data)));

  EXPECT_EQ(0U, session_.MakeDecodable());
  EXPECT_EQ(3 * packet_buffer_size(), session_.SessionLength());
  SCOPED_TRACE("Calling VerifyNalu");
  EXPECT_TRUE(VerifyNalu(0, 1, 0));
}

TEST_F(TestNalUnits, WrapLosses) {
  packet_.seqNum = 0xFFFF;
  packet_.video_header.is_first_packet_in_frame = false;
  packet_.completeNALU = kNaluIncomplete;
  packet_.markerBit = false;
  FillPacket(1);
  EXPECT_EQ(packet_buffer_size(), static_cast<size_t>(session_.InsertPacket(
                                      packet_, frame_buffer_, frame_data)));

  packet_.video_header.is_first_packet_in_frame = false;
  packet_.completeNALU = kNaluEnd;
  packet_.seqNum += 2;
  packet_.markerBit = true;
  FillPacket(2);
  EXPECT_EQ(packet_buffer_size(), static_cast<size_t>(session_.InsertPacket(
                                      packet_, frame_buffer_, frame_data)));

  EXPECT_EQ(2 * packet_buffer_size(), session_.MakeDecodable());
  EXPECT_EQ(0U, session_.SessionLength());
}

TEST_F(TestNalUnits, ReorderWrapLosses) {
  packet_.seqNum = 0xFFFF;

  packet_.video_header.is_first_packet_in_frame = false;
  packet_.completeNALU = kNaluEnd;
  packet_.seqNum += 2;
  packet_.markerBit = true;
  FillPacket(2);
  EXPECT_EQ(packet_buffer_size(), static_cast<size_t>(session_.InsertPacket(
                                      packet_, frame_buffer_, frame_data)));

  packet_.seqNum -= 2;
  packet_.video_header.is_first_packet_in_frame = false;
  packet_.completeNALU = kNaluIncomplete;
  packet_.markerBit = false;
  FillPacket(1);
  EXPECT_EQ(packet_buffer_size(), static_cast<size_t>(session_.InsertPacket(
                                      packet_, frame_buffer_, frame_data)));

  EXPECT_EQ(2 * packet_buffer_size(), session_.MakeDecodable());
  EXPECT_EQ(0U, session_.SessionLength());
}

}  // namespace webrtc
