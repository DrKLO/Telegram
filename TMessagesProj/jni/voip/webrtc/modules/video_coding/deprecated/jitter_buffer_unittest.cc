/*
 *  Copyright (c) 2011 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "modules/video_coding/deprecated/jitter_buffer.h"

#include <list>
#include <memory>
#include <string>
#include <vector>

#include "absl/memory/memory.h"
#include "common_video/h264/h264_common.h"
#include "modules/video_coding/deprecated/frame_buffer.h"
#include "modules/video_coding/deprecated/packet.h"
#include "modules/video_coding/deprecated/stream_generator.h"
#include "system_wrappers/include/clock.h"
#include "test/gmock.h"
#include "test/gtest.h"
#include "test/scoped_key_value_config.h"

namespace webrtc {

class TestBasicJitterBuffer : public ::testing::Test {
 protected:
  TestBasicJitterBuffer() {}
  void SetUp() override {
    clock_.reset(new SimulatedClock(0));
    jitter_buffer_.reset(new VCMJitterBuffer(
        clock_.get(), absl::WrapUnique(EventWrapper::Create()), field_trials_));
    jitter_buffer_->Start();
    seq_num_ = 1234;
    timestamp_ = 0;
    size_ = 1400;
    // Data vector -  0, 0, 0x80, 3, 4, 5, 6, 7, 8, 9, 0, 0, 0x80, 3....
    data_[0] = 0;
    data_[1] = 0;
    data_[2] = 0x80;
    int count = 3;
    for (unsigned int i = 3; i < sizeof(data_) - 3; ++i) {
      data_[i] = count;
      count++;
      if (count == 10) {
        data_[i + 1] = 0;
        data_[i + 2] = 0;
        data_[i + 3] = 0x80;
        count = 3;
        i += 3;
      }
    }
    RTPHeader rtp_header;
    RTPVideoHeader video_header;
    rtp_header.sequenceNumber = seq_num_;
    rtp_header.timestamp = timestamp_;
    rtp_header.markerBit = true;
    video_header.codec = kVideoCodecGeneric;
    video_header.is_first_packet_in_frame = true;
    video_header.frame_type = VideoFrameType::kVideoFrameDelta;
    packet_.reset(new VCMPacket(data_, size_, rtp_header, video_header,
                                /*ntp_time_ms=*/0, clock_->CurrentTime()));
  }

  VCMEncodedFrame* DecodeCompleteFrame() {
    VCMEncodedFrame* found_frame = jitter_buffer_->NextCompleteFrame(10);
    if (!found_frame)
      return nullptr;
    return jitter_buffer_->ExtractAndSetDecode(found_frame->RtpTimestamp());
  }

  void CheckOutFrame(VCMEncodedFrame* frame_out,
                     unsigned int size,
                     bool startCode) {
    ASSERT_TRUE(frame_out);

    const uint8_t* outData = frame_out->data();
    unsigned int i = 0;

    if (startCode) {
      EXPECT_EQ(0, outData[0]);
      EXPECT_EQ(0, outData[1]);
      EXPECT_EQ(0, outData[2]);
      EXPECT_EQ(1, outData[3]);
      i += 4;
    }

    EXPECT_EQ(size, frame_out->size());
    int count = 3;
    for (; i < size; i++) {
      if (outData[i] == 0 && outData[i + 1] == 0 && outData[i + 2] == 0x80) {
        i += 2;
      } else if (startCode && outData[i] == 0 && outData[i + 1] == 0) {
        EXPECT_EQ(0, outData[0]);
        EXPECT_EQ(0, outData[1]);
        EXPECT_EQ(0, outData[2]);
        EXPECT_EQ(1, outData[3]);
        i += 3;
      } else {
        EXPECT_EQ(count, outData[i]);
        count++;
        if (count == 10) {
          count = 3;
        }
      }
    }
  }

  uint16_t seq_num_;
  uint32_t timestamp_;
  int size_;
  uint8_t data_[1500];
  test::ScopedKeyValueConfig field_trials_;
  std::unique_ptr<VCMPacket> packet_;
  std::unique_ptr<SimulatedClock> clock_;
  std::unique_ptr<VCMJitterBuffer> jitter_buffer_;
};

class TestRunningJitterBuffer : public ::testing::Test {
 protected:
  enum { kDataBufferSize = 10 };

  virtual void SetUp() {
    clock_.reset(new SimulatedClock(0));
    max_nack_list_size_ = 150;
    oldest_packet_to_nack_ = 250;
    jitter_buffer_ = new VCMJitterBuffer(
        clock_.get(), absl::WrapUnique(EventWrapper::Create()), field_trials_);
    stream_generator_ = new StreamGenerator(0, clock_->TimeInMilliseconds());
    jitter_buffer_->Start();
    jitter_buffer_->SetNackSettings(max_nack_list_size_, oldest_packet_to_nack_,
                                    0);
    memset(data_buffer_, 0, kDataBufferSize);
  }

  virtual void TearDown() {
    jitter_buffer_->Stop();
    delete stream_generator_;
    delete jitter_buffer_;
  }

  VCMFrameBufferEnum InsertPacketAndPop(int index) {
    VCMPacket packet;
    packet.dataPtr = data_buffer_;
    bool packet_available = stream_generator_->PopPacket(&packet, index);
    EXPECT_TRUE(packet_available);
    if (!packet_available)
      return kGeneralError;  // Return here to avoid crashes below.
    bool retransmitted = false;
    return jitter_buffer_->InsertPacket(packet, &retransmitted);
  }

  VCMFrameBufferEnum InsertPacket(int index) {
    VCMPacket packet;
    packet.dataPtr = data_buffer_;
    bool packet_available = stream_generator_->GetPacket(&packet, index);
    EXPECT_TRUE(packet_available);
    if (!packet_available)
      return kGeneralError;  // Return here to avoid crashes below.
    bool retransmitted = false;
    return jitter_buffer_->InsertPacket(packet, &retransmitted);
  }

  VCMFrameBufferEnum InsertFrame(VideoFrameType frame_type) {
    stream_generator_->GenerateFrame(
        frame_type, (frame_type != VideoFrameType::kEmptyFrame) ? 1 : 0,
        (frame_type == VideoFrameType::kEmptyFrame) ? 1 : 0,
        clock_->TimeInMilliseconds());
    VCMFrameBufferEnum ret = InsertPacketAndPop(0);
    clock_->AdvanceTimeMilliseconds(kDefaultFramePeriodMs);
    return ret;
  }

  VCMFrameBufferEnum InsertFrames(int num_frames, VideoFrameType frame_type) {
    VCMFrameBufferEnum ret_for_all = kNoError;
    for (int i = 0; i < num_frames; ++i) {
      VCMFrameBufferEnum ret = InsertFrame(frame_type);
      if (ret < kNoError) {
        ret_for_all = ret;
      } else if (ret_for_all >= kNoError) {
        ret_for_all = ret;
      }
    }
    return ret_for_all;
  }

  void DropFrame(int num_packets) {
    stream_generator_->GenerateFrame(VideoFrameType::kVideoFrameDelta,
                                     num_packets, 0,
                                     clock_->TimeInMilliseconds());
    for (int i = 0; i < num_packets; ++i)
      stream_generator_->DropLastPacket();
    clock_->AdvanceTimeMilliseconds(kDefaultFramePeriodMs);
  }

  bool DecodeCompleteFrame() {
    VCMEncodedFrame* found_frame = jitter_buffer_->NextCompleteFrame(0);
    if (!found_frame)
      return false;

    VCMEncodedFrame* frame =
        jitter_buffer_->ExtractAndSetDecode(found_frame->RtpTimestamp());
    bool ret = (frame != NULL);
    jitter_buffer_->ReleaseFrame(frame);
    return ret;
  }

  test::ScopedKeyValueConfig field_trials_;
  VCMJitterBuffer* jitter_buffer_;
  StreamGenerator* stream_generator_;
  std::unique_ptr<SimulatedClock> clock_;
  size_t max_nack_list_size_;
  int oldest_packet_to_nack_;
  uint8_t data_buffer_[kDataBufferSize];
};

class TestJitterBufferNack : public TestRunningJitterBuffer {
 protected:
  TestJitterBufferNack() {}
  virtual void SetUp() { TestRunningJitterBuffer::SetUp(); }

  virtual void TearDown() { TestRunningJitterBuffer::TearDown(); }
};

TEST_F(TestBasicJitterBuffer, StopRunning) {
  jitter_buffer_->Stop();
  EXPECT_TRUE(NULL == DecodeCompleteFrame());
  jitter_buffer_->Start();

  // No packets inserted.
  EXPECT_TRUE(NULL == DecodeCompleteFrame());
}

TEST_F(TestBasicJitterBuffer, SinglePacketFrame) {
  // Always start with a complete key frame when not allowing errors.
  packet_->video_header.frame_type = VideoFrameType::kVideoFrameKey;
  packet_->video_header.is_first_packet_in_frame = true;
  packet_->markerBit = true;
  packet_->timestamp += 123 * 90;

  // Insert the packet to the jitter buffer and get a frame.
  bool retransmitted = false;
  EXPECT_EQ(kCompleteSession,
            jitter_buffer_->InsertPacket(*packet_, &retransmitted));
  VCMEncodedFrame* frame_out = DecodeCompleteFrame();
  CheckOutFrame(frame_out, size_, false);
  EXPECT_EQ(VideoFrameType::kVideoFrameKey, frame_out->FrameType());
  jitter_buffer_->ReleaseFrame(frame_out);
}

TEST_F(TestBasicJitterBuffer, DualPacketFrame) {
  packet_->video_header.frame_type = VideoFrameType::kVideoFrameKey;
  packet_->video_header.is_first_packet_in_frame = true;
  packet_->markerBit = false;

  bool retransmitted = false;
  EXPECT_EQ(kIncomplete,
            jitter_buffer_->InsertPacket(*packet_, &retransmitted));
  VCMEncodedFrame* frame_out = DecodeCompleteFrame();
  // Should not be complete.
  EXPECT_TRUE(frame_out == NULL);

  ++seq_num_;
  packet_->video_header.is_first_packet_in_frame = false;
  packet_->markerBit = true;
  packet_->seqNum = seq_num_;

  EXPECT_EQ(kCompleteSession,
            jitter_buffer_->InsertPacket(*packet_, &retransmitted));

  frame_out = DecodeCompleteFrame();
  CheckOutFrame(frame_out, 2 * size_, false);

  EXPECT_EQ(VideoFrameType::kVideoFrameKey, frame_out->FrameType());
  jitter_buffer_->ReleaseFrame(frame_out);
}

TEST_F(TestBasicJitterBuffer, 100PacketKeyFrame) {
  packet_->video_header.frame_type = VideoFrameType::kVideoFrameKey;
  packet_->video_header.is_first_packet_in_frame = true;
  packet_->markerBit = false;

  bool retransmitted = false;
  EXPECT_EQ(kIncomplete,
            jitter_buffer_->InsertPacket(*packet_, &retransmitted));

  VCMEncodedFrame* frame_out = DecodeCompleteFrame();

  // Frame should not be complete.
  EXPECT_TRUE(frame_out == NULL);

  // Insert 98 frames.
  int loop = 0;
  do {
    seq_num_++;
    packet_->video_header.is_first_packet_in_frame = false;
    packet_->markerBit = false;
    packet_->seqNum = seq_num_;

    EXPECT_EQ(kIncomplete,
              jitter_buffer_->InsertPacket(*packet_, &retransmitted));
    loop++;
  } while (loop < 98);

  // Insert last packet.
  ++seq_num_;
  packet_->video_header.is_first_packet_in_frame = false;
  packet_->markerBit = true;
  packet_->seqNum = seq_num_;

  EXPECT_EQ(kCompleteSession,
            jitter_buffer_->InsertPacket(*packet_, &retransmitted));

  frame_out = DecodeCompleteFrame();

  CheckOutFrame(frame_out, 100 * size_, false);
  EXPECT_EQ(VideoFrameType::kVideoFrameKey, frame_out->FrameType());
  jitter_buffer_->ReleaseFrame(frame_out);
}

TEST_F(TestBasicJitterBuffer, 100PacketDeltaFrame) {
  // Always start with a complete key frame.
  packet_->video_header.frame_type = VideoFrameType::kVideoFrameKey;
  packet_->video_header.is_first_packet_in_frame = true;
  packet_->markerBit = true;

  bool retransmitted = false;
  EXPECT_EQ(kCompleteSession,
            jitter_buffer_->InsertPacket(*packet_, &retransmitted));
  VCMEncodedFrame* frame_out = DecodeCompleteFrame();
  EXPECT_FALSE(frame_out == NULL);
  jitter_buffer_->ReleaseFrame(frame_out);

  ++seq_num_;
  packet_->seqNum = seq_num_;
  packet_->markerBit = false;
  packet_->video_header.frame_type = VideoFrameType::kVideoFrameDelta;
  packet_->timestamp += 33 * 90;

  EXPECT_EQ(kIncomplete,
            jitter_buffer_->InsertPacket(*packet_, &retransmitted));

  frame_out = DecodeCompleteFrame();

  // Frame should not be complete.
  EXPECT_TRUE(frame_out == NULL);

  packet_->video_header.is_first_packet_in_frame = false;
  // Insert 98 frames.
  int loop = 0;
  do {
    ++seq_num_;
    packet_->seqNum = seq_num_;

    // Insert a packet into a frame.
    EXPECT_EQ(kIncomplete,
              jitter_buffer_->InsertPacket(*packet_, &retransmitted));
    loop++;
  } while (loop < 98);

  // Insert the last packet.
  ++seq_num_;
  packet_->video_header.is_first_packet_in_frame = false;
  packet_->markerBit = true;
  packet_->seqNum = seq_num_;

  EXPECT_EQ(kCompleteSession,
            jitter_buffer_->InsertPacket(*packet_, &retransmitted));

  frame_out = DecodeCompleteFrame();

  CheckOutFrame(frame_out, 100 * size_, false);
  EXPECT_EQ(VideoFrameType::kVideoFrameDelta, frame_out->FrameType());
  jitter_buffer_->ReleaseFrame(frame_out);
}

TEST_F(TestBasicJitterBuffer, PacketReorderingReverseOrder) {
  // Insert the "first" packet last.
  seq_num_ += 100;
  packet_->video_header.frame_type = VideoFrameType::kVideoFrameKey;
  packet_->video_header.is_first_packet_in_frame = false;
  packet_->markerBit = true;
  packet_->seqNum = seq_num_;
  packet_->timestamp = timestamp_;

  bool retransmitted = false;
  EXPECT_EQ(kIncomplete,
            jitter_buffer_->InsertPacket(*packet_, &retransmitted));

  VCMEncodedFrame* frame_out = DecodeCompleteFrame();

  EXPECT_TRUE(frame_out == NULL);

  // Insert 98 packets.
  int loop = 0;
  do {
    seq_num_--;
    packet_->video_header.is_first_packet_in_frame = false;
    packet_->markerBit = false;
    packet_->seqNum = seq_num_;

    EXPECT_EQ(kIncomplete,
              jitter_buffer_->InsertPacket(*packet_, &retransmitted));
    loop++;
  } while (loop < 98);

  // Insert the last packet.
  seq_num_--;
  packet_->video_header.is_first_packet_in_frame = true;
  packet_->markerBit = false;
  packet_->seqNum = seq_num_;

  EXPECT_EQ(kCompleteSession,
            jitter_buffer_->InsertPacket(*packet_, &retransmitted));

  frame_out = DecodeCompleteFrame();

  CheckOutFrame(frame_out, 100 * size_, false);

  EXPECT_EQ(VideoFrameType::kVideoFrameKey, frame_out->FrameType());
  jitter_buffer_->ReleaseFrame(frame_out);
}

TEST_F(TestBasicJitterBuffer, FrameReordering2Frames2PacketsEach) {
  packet_->video_header.frame_type = VideoFrameType::kVideoFrameDelta;
  packet_->video_header.is_first_packet_in_frame = true;
  packet_->markerBit = false;

  bool retransmitted = false;
  EXPECT_EQ(kIncomplete,
            jitter_buffer_->InsertPacket(*packet_, &retransmitted));

  VCMEncodedFrame* frame_out = DecodeCompleteFrame();

  EXPECT_TRUE(frame_out == NULL);

  seq_num_++;
  packet_->video_header.is_first_packet_in_frame = false;
  packet_->markerBit = true;
  packet_->seqNum = seq_num_;

  EXPECT_EQ(kCompleteSession,
            jitter_buffer_->InsertPacket(*packet_, &retransmitted));

  // check that we fail to get frame since seqnum is not continuous
  frame_out = DecodeCompleteFrame();
  EXPECT_TRUE(frame_out == NULL);

  seq_num_ -= 3;
  timestamp_ -= 33 * 90;
  packet_->video_header.frame_type = VideoFrameType::kVideoFrameKey;
  packet_->video_header.is_first_packet_in_frame = true;
  packet_->markerBit = false;
  packet_->seqNum = seq_num_;
  packet_->timestamp = timestamp_;

  EXPECT_EQ(kIncomplete,
            jitter_buffer_->InsertPacket(*packet_, &retransmitted));

  frame_out = DecodeCompleteFrame();

  // It should not be complete.
  EXPECT_TRUE(frame_out == NULL);

  seq_num_++;
  packet_->video_header.is_first_packet_in_frame = false;
  packet_->markerBit = true;
  packet_->seqNum = seq_num_;

  EXPECT_EQ(kCompleteSession,
            jitter_buffer_->InsertPacket(*packet_, &retransmitted));

  frame_out = DecodeCompleteFrame();
  CheckOutFrame(frame_out, 2 * size_, false);
  EXPECT_EQ(VideoFrameType::kVideoFrameKey, frame_out->FrameType());
  jitter_buffer_->ReleaseFrame(frame_out);

  frame_out = DecodeCompleteFrame();
  CheckOutFrame(frame_out, 2 * size_, false);
  EXPECT_EQ(VideoFrameType::kVideoFrameDelta, frame_out->FrameType());
  jitter_buffer_->ReleaseFrame(frame_out);
}

TEST_F(TestBasicJitterBuffer, TestReorderingWithPadding) {
  jitter_buffer_->SetNackSettings(kMaxNumberOfFrames, kMaxNumberOfFrames, 0);
  packet_->video_header.frame_type = VideoFrameType::kVideoFrameKey;
  packet_->video_header.is_first_packet_in_frame = true;
  packet_->markerBit = true;

  // Send in an initial good packet/frame (Frame A) to start things off.
  bool retransmitted = false;
  EXPECT_EQ(kCompleteSession,
            jitter_buffer_->InsertPacket(*packet_, &retransmitted));
  VCMEncodedFrame* frame_out = DecodeCompleteFrame();
  EXPECT_TRUE(frame_out != NULL);
  jitter_buffer_->ReleaseFrame(frame_out);

  // Now send in a complete delta frame (Frame C), but with a sequence number
  // gap. No pic index either, so no temporal scalability cheating :)
  packet_->video_header.frame_type = VideoFrameType::kVideoFrameDelta;
  // Leave a gap of 2 sequence numbers and two frames.
  packet_->seqNum = seq_num_ + 3;
  packet_->timestamp = timestamp_ + (66 * 90);
  // Still isFirst = marker = true.
  // Session should be complete (frame is complete), but there's nothing to
  // decode yet.
  EXPECT_EQ(kCompleteSession,
            jitter_buffer_->InsertPacket(*packet_, &retransmitted));
  frame_out = DecodeCompleteFrame();
  EXPECT_TRUE(frame_out == NULL);

  // Now send in a complete delta frame (Frame B) that is continuous from A, but
  // doesn't fill the full gap to C. The rest of the gap is going to be padding.
  packet_->seqNum = seq_num_ + 1;
  packet_->timestamp = timestamp_ + (33 * 90);
  // Still isFirst = marker = true.
  EXPECT_EQ(kCompleteSession,
            jitter_buffer_->InsertPacket(*packet_, &retransmitted));
  frame_out = DecodeCompleteFrame();
  EXPECT_TRUE(frame_out != NULL);
  jitter_buffer_->ReleaseFrame(frame_out);

  // But Frame C isn't continuous yet.
  frame_out = DecodeCompleteFrame();
  EXPECT_TRUE(frame_out == NULL);

  // Add in the padding. These are empty packets (data length is 0) with no
  // marker bit and matching the timestamp of Frame B.
  RTPHeader rtp_header;
  RTPVideoHeader video_header;
  rtp_header.sequenceNumber = seq_num_ + 2;
  rtp_header.timestamp = timestamp_ + (33 * 90);
  rtp_header.markerBit = false;
  video_header.codec = kVideoCodecGeneric;
  video_header.frame_type = VideoFrameType::kEmptyFrame;
  VCMPacket empty_packet(data_, 0, rtp_header, video_header,
                         /*ntp_time_ms=*/0, clock_->CurrentTime());
  EXPECT_EQ(kOldPacket,
            jitter_buffer_->InsertPacket(empty_packet, &retransmitted));
  empty_packet.seqNum += 1;
  EXPECT_EQ(kOldPacket,
            jitter_buffer_->InsertPacket(empty_packet, &retransmitted));

  // But now Frame C should be ready!
  frame_out = DecodeCompleteFrame();
  EXPECT_TRUE(frame_out != NULL);
  jitter_buffer_->ReleaseFrame(frame_out);
}

TEST_F(TestBasicJitterBuffer, DuplicatePackets) {
  packet_->video_header.frame_type = VideoFrameType::kVideoFrameKey;
  packet_->video_header.is_first_packet_in_frame = true;
  packet_->markerBit = false;
  packet_->seqNum = seq_num_;
  packet_->timestamp = timestamp_;
  EXPECT_EQ(0, jitter_buffer_->num_packets());
  EXPECT_EQ(0, jitter_buffer_->num_duplicated_packets());

  bool retransmitted = false;
  EXPECT_EQ(kIncomplete,
            jitter_buffer_->InsertPacket(*packet_, &retransmitted));

  VCMEncodedFrame* frame_out = DecodeCompleteFrame();

  EXPECT_TRUE(frame_out == NULL);
  EXPECT_EQ(1, jitter_buffer_->num_packets());
  EXPECT_EQ(0, jitter_buffer_->num_duplicated_packets());

  // Insert a packet into a frame.
  EXPECT_EQ(kDuplicatePacket,
            jitter_buffer_->InsertPacket(*packet_, &retransmitted));
  EXPECT_EQ(2, jitter_buffer_->num_packets());
  EXPECT_EQ(1, jitter_buffer_->num_duplicated_packets());

  seq_num_++;
  packet_->seqNum = seq_num_;
  packet_->markerBit = true;
  packet_->video_header.is_first_packet_in_frame = false;

  EXPECT_EQ(kCompleteSession,
            jitter_buffer_->InsertPacket(*packet_, &retransmitted));

  frame_out = DecodeCompleteFrame();
  ASSERT_TRUE(frame_out != NULL);
  CheckOutFrame(frame_out, 2 * size_, false);

  EXPECT_EQ(VideoFrameType::kVideoFrameKey, frame_out->FrameType());
  EXPECT_EQ(3, jitter_buffer_->num_packets());
  EXPECT_EQ(1, jitter_buffer_->num_duplicated_packets());
  jitter_buffer_->ReleaseFrame(frame_out);
}

TEST_F(TestBasicJitterBuffer, DuplicatePreviousDeltaFramePacket) {
  packet_->video_header.frame_type = VideoFrameType::kVideoFrameKey;
  packet_->video_header.is_first_packet_in_frame = true;
  packet_->markerBit = true;
  packet_->seqNum = seq_num_;
  packet_->timestamp = timestamp_;
  EXPECT_EQ(0, jitter_buffer_->num_packets());
  EXPECT_EQ(0, jitter_buffer_->num_duplicated_packets());

  bool retransmitted = false;
  // Insert first complete frame.
  EXPECT_EQ(kCompleteSession,
            jitter_buffer_->InsertPacket(*packet_, &retransmitted));

  VCMEncodedFrame* frame_out = DecodeCompleteFrame();
  ASSERT_TRUE(frame_out != NULL);
  CheckOutFrame(frame_out, size_, false);
  EXPECT_EQ(VideoFrameType::kVideoFrameKey, frame_out->FrameType());
  jitter_buffer_->ReleaseFrame(frame_out);

  // Insert 3 delta frames.
  for (uint16_t i = 1; i <= 3; ++i) {
    packet_->seqNum = seq_num_ + i;
    packet_->timestamp = timestamp_ + (i * 33) * 90;
    packet_->video_header.frame_type = VideoFrameType::kVideoFrameDelta;
    EXPECT_EQ(kCompleteSession,
              jitter_buffer_->InsertPacket(*packet_, &retransmitted));
    EXPECT_EQ(i + 1, jitter_buffer_->num_packets());
    EXPECT_EQ(0, jitter_buffer_->num_duplicated_packets());
  }

  // Retransmit second delta frame.
  packet_->seqNum = seq_num_ + 2;
  packet_->timestamp = timestamp_ + 66 * 90;

  EXPECT_EQ(kDuplicatePacket,
            jitter_buffer_->InsertPacket(*packet_, &retransmitted));

  EXPECT_EQ(5, jitter_buffer_->num_packets());
  EXPECT_EQ(1, jitter_buffer_->num_duplicated_packets());

  // Should be able to decode 3 delta frames, key frame already decoded.
  for (size_t i = 0; i < 3; ++i) {
    frame_out = DecodeCompleteFrame();
    ASSERT_TRUE(frame_out != NULL);
    CheckOutFrame(frame_out, size_, false);
    EXPECT_EQ(VideoFrameType::kVideoFrameDelta, frame_out->FrameType());
    jitter_buffer_->ReleaseFrame(frame_out);
  }
}

TEST_F(TestBasicJitterBuffer, TestSkipForwardVp9) {
  // Verify that JB skips forward to next base layer frame.
  //  -------------------------------------------------
  // | 65485 | 65486 | 65487 | 65488 | 65489 | ...
  // | pid:5 | pid:6 | pid:7 | pid:8 | pid:9 | ...
  // | tid:0 | tid:2 | tid:1 | tid:2 | tid:0 | ...
  // |  ss   |   x   |   x   |   x   |       |
  //  -------------------------------------------------
  // |<----------tl0idx:200--------->|<---tl0idx:201---

  jitter_buffer_->SetNackSettings(kMaxNumberOfFrames, kMaxNumberOfFrames, 0);
  auto& vp9_header =
      packet_->video_header.video_type_header.emplace<RTPVideoHeaderVP9>();

  bool re = false;
  packet_->video_header.codec = kVideoCodecVP9;
  packet_->video_header.is_first_packet_in_frame = true;
  packet_->markerBit = true;
  vp9_header.flexible_mode = false;
  vp9_header.spatial_idx = 0;
  vp9_header.beginning_of_frame = true;
  vp9_header.end_of_frame = true;
  vp9_header.temporal_up_switch = false;

  packet_->seqNum = 65485;
  packet_->timestamp = 1000;
  packet_->video_header.frame_type = VideoFrameType::kVideoFrameKey;
  vp9_header.picture_id = 5;
  vp9_header.tl0_pic_idx = 200;
  vp9_header.temporal_idx = 0;
  vp9_header.ss_data_available = true;
  vp9_header.gof.SetGofInfoVP9(
      kTemporalStructureMode3);  // kTemporalStructureMode3: 0-2-1-2..
  EXPECT_EQ(kCompleteSession, jitter_buffer_->InsertPacket(*packet_, &re));

  // Insert next temporal layer 0.
  packet_->seqNum = 65489;
  packet_->timestamp = 13000;
  packet_->video_header.frame_type = VideoFrameType::kVideoFrameDelta;
  vp9_header.picture_id = 9;
  vp9_header.tl0_pic_idx = 201;
  vp9_header.temporal_idx = 0;
  vp9_header.ss_data_available = false;
  EXPECT_EQ(kCompleteSession, jitter_buffer_->InsertPacket(*packet_, &re));

  VCMEncodedFrame* frame_out = DecodeCompleteFrame();
  EXPECT_EQ(1000U, frame_out->RtpTimestamp());
  EXPECT_EQ(VideoFrameType::kVideoFrameKey, frame_out->FrameType());
  jitter_buffer_->ReleaseFrame(frame_out);

  frame_out = DecodeCompleteFrame();
  EXPECT_EQ(13000U, frame_out->RtpTimestamp());
  EXPECT_EQ(VideoFrameType::kVideoFrameDelta, frame_out->FrameType());
  jitter_buffer_->ReleaseFrame(frame_out);
}

TEST_F(TestBasicJitterBuffer, ReorderedVp9SsData_3TlLayers) {
  // Verify that frames are updated with SS data when SS packet is reordered.
  //  --------------------------------
  // | 65486 | 65487 | 65485 |...
  // | pid:6 | pid:7 | pid:5 |...
  // | tid:2 | tid:1 | tid:0 |...
  // |       |       |  ss   |
  //  --------------------------------
  // |<--------tl0idx:200--------->|

  auto& vp9_header =
      packet_->video_header.video_type_header.emplace<RTPVideoHeaderVP9>();

  bool re = false;
  packet_->video_header.codec = kVideoCodecVP9;
  packet_->video_header.is_first_packet_in_frame = true;
  packet_->markerBit = true;
  vp9_header.flexible_mode = false;
  vp9_header.spatial_idx = 0;
  vp9_header.beginning_of_frame = true;
  vp9_header.end_of_frame = true;
  vp9_header.tl0_pic_idx = 200;

  packet_->seqNum = 65486;
  packet_->timestamp = 6000;
  packet_->video_header.frame_type = VideoFrameType::kVideoFrameDelta;
  vp9_header.picture_id = 6;
  vp9_header.temporal_idx = 2;
  vp9_header.temporal_up_switch = true;
  EXPECT_EQ(kCompleteSession, jitter_buffer_->InsertPacket(*packet_, &re));

  packet_->seqNum = 65487;
  packet_->timestamp = 9000;
  packet_->video_header.frame_type = VideoFrameType::kVideoFrameDelta;
  vp9_header.picture_id = 7;
  vp9_header.temporal_idx = 1;
  vp9_header.temporal_up_switch = true;
  EXPECT_EQ(kCompleteSession, jitter_buffer_->InsertPacket(*packet_, &re));

  // Insert first frame with SS data.
  packet_->seqNum = 65485;
  packet_->timestamp = 3000;
  packet_->video_header.frame_type = VideoFrameType::kVideoFrameKey;
  packet_->video_header.width = 352;
  packet_->video_header.height = 288;
  vp9_header.picture_id = 5;
  vp9_header.temporal_idx = 0;
  vp9_header.temporal_up_switch = false;
  vp9_header.ss_data_available = true;
  vp9_header.gof.SetGofInfoVP9(
      kTemporalStructureMode3);  // kTemporalStructureMode3: 0-2-1-2..
  EXPECT_EQ(kCompleteSession, jitter_buffer_->InsertPacket(*packet_, &re));

  VCMEncodedFrame* frame_out = DecodeCompleteFrame();
  EXPECT_EQ(3000U, frame_out->RtpTimestamp());
  EXPECT_EQ(VideoFrameType::kVideoFrameKey, frame_out->FrameType());
  EXPECT_EQ(0, frame_out->CodecSpecific()->codecSpecific.VP9.temporal_idx);
  EXPECT_FALSE(
      frame_out->CodecSpecific()->codecSpecific.VP9.temporal_up_switch);
  jitter_buffer_->ReleaseFrame(frame_out);

  frame_out = DecodeCompleteFrame();
  EXPECT_EQ(6000U, frame_out->RtpTimestamp());
  EXPECT_EQ(VideoFrameType::kVideoFrameDelta, frame_out->FrameType());
  EXPECT_EQ(2, frame_out->CodecSpecific()->codecSpecific.VP9.temporal_idx);
  EXPECT_TRUE(frame_out->CodecSpecific()->codecSpecific.VP9.temporal_up_switch);
  jitter_buffer_->ReleaseFrame(frame_out);

  frame_out = DecodeCompleteFrame();
  EXPECT_EQ(9000U, frame_out->RtpTimestamp());
  EXPECT_EQ(VideoFrameType::kVideoFrameDelta, frame_out->FrameType());
  EXPECT_EQ(1, frame_out->CodecSpecific()->codecSpecific.VP9.temporal_idx);
  EXPECT_TRUE(frame_out->CodecSpecific()->codecSpecific.VP9.temporal_up_switch);
  jitter_buffer_->ReleaseFrame(frame_out);
}

TEST_F(TestBasicJitterBuffer, ReorderedVp9SsData_2Tl2SLayers) {
  // Verify that frames are updated with SS data when SS packet is reordered.
  //  -----------------------------------------
  // | 65486  | 65487  | 65485  | 65484  |...
  // | pid:6  | pid:6  | pid:5  | pid:5  |...
  // | tid:1  | tid:1  | tid:0  | tid:0  |...
  // | sid:0  | sid:1  | sid:1  | sid:0  |...
  // | t:6000 | t:6000 | t:3000 | t:3000 |
  // |        |        |        |  ss    |
  //  -----------------------------------------
  // |<-----------tl0idx:200------------>|

  auto& vp9_header =
      packet_->video_header.video_type_header.emplace<RTPVideoHeaderVP9>();

  bool re = false;
  packet_->video_header.codec = kVideoCodecVP9;
  vp9_header.flexible_mode = false;
  vp9_header.beginning_of_frame = true;
  vp9_header.end_of_frame = true;
  vp9_header.tl0_pic_idx = 200;

  packet_->video_header.is_first_packet_in_frame = true;
  packet_->markerBit = false;
  packet_->seqNum = 65486;
  packet_->timestamp = 6000;
  packet_->video_header.frame_type = VideoFrameType::kVideoFrameDelta;
  vp9_header.spatial_idx = 0;
  vp9_header.picture_id = 6;
  vp9_header.temporal_idx = 1;
  vp9_header.temporal_up_switch = true;
  EXPECT_EQ(kIncomplete, jitter_buffer_->InsertPacket(*packet_, &re));

  packet_->video_header.is_first_packet_in_frame = false;
  packet_->markerBit = true;
  packet_->seqNum = 65487;
  packet_->video_header.frame_type = VideoFrameType::kVideoFrameDelta;
  vp9_header.spatial_idx = 1;
  vp9_header.picture_id = 6;
  vp9_header.temporal_idx = 1;
  vp9_header.temporal_up_switch = true;
  EXPECT_EQ(kCompleteSession, jitter_buffer_->InsertPacket(*packet_, &re));

  packet_->video_header.is_first_packet_in_frame = false;
  packet_->markerBit = true;
  packet_->seqNum = 65485;
  packet_->timestamp = 3000;
  packet_->video_header.frame_type = VideoFrameType::kVideoFrameKey;
  vp9_header.spatial_idx = 1;
  vp9_header.picture_id = 5;
  vp9_header.temporal_idx = 0;
  vp9_header.temporal_up_switch = false;
  EXPECT_EQ(kIncomplete, jitter_buffer_->InsertPacket(*packet_, &re));

  // Insert first frame with SS data.
  packet_->video_header.is_first_packet_in_frame = true;
  packet_->markerBit = false;
  packet_->seqNum = 65484;
  packet_->video_header.frame_type = VideoFrameType::kVideoFrameKey;
  packet_->video_header.width = 352;
  packet_->video_header.height = 288;
  vp9_header.spatial_idx = 0;
  vp9_header.picture_id = 5;
  vp9_header.temporal_idx = 0;
  vp9_header.temporal_up_switch = false;
  vp9_header.ss_data_available = true;
  vp9_header.gof.SetGofInfoVP9(
      kTemporalStructureMode2);  // kTemporalStructureMode3: 0-1-0-1..
  EXPECT_EQ(kCompleteSession, jitter_buffer_->InsertPacket(*packet_, &re));

  VCMEncodedFrame* frame_out = DecodeCompleteFrame();
  EXPECT_EQ(3000U, frame_out->RtpTimestamp());
  EXPECT_EQ(VideoFrameType::kVideoFrameKey, frame_out->FrameType());
  EXPECT_EQ(0, frame_out->CodecSpecific()->codecSpecific.VP9.temporal_idx);
  EXPECT_FALSE(
      frame_out->CodecSpecific()->codecSpecific.VP9.temporal_up_switch);
  jitter_buffer_->ReleaseFrame(frame_out);

  frame_out = DecodeCompleteFrame();
  EXPECT_EQ(6000U, frame_out->RtpTimestamp());
  EXPECT_EQ(VideoFrameType::kVideoFrameDelta, frame_out->FrameType());
  EXPECT_EQ(1, frame_out->CodecSpecific()->codecSpecific.VP9.temporal_idx);
  EXPECT_TRUE(frame_out->CodecSpecific()->codecSpecific.VP9.temporal_up_switch);
  jitter_buffer_->ReleaseFrame(frame_out);
}

TEST_F(TestBasicJitterBuffer, H264InsertStartCode) {
  packet_->video_header.frame_type = VideoFrameType::kVideoFrameKey;
  packet_->video_header.is_first_packet_in_frame = true;
  packet_->markerBit = false;
  packet_->seqNum = seq_num_;
  packet_->timestamp = timestamp_;
  packet_->insertStartCode = true;

  bool retransmitted = false;
  EXPECT_EQ(kIncomplete,
            jitter_buffer_->InsertPacket(*packet_, &retransmitted));

  VCMEncodedFrame* frame_out = DecodeCompleteFrame();

  // Frame should not be complete.
  EXPECT_TRUE(frame_out == NULL);

  seq_num_++;
  packet_->video_header.is_first_packet_in_frame = false;
  packet_->markerBit = true;
  packet_->seqNum = seq_num_;

  EXPECT_EQ(kCompleteSession,
            jitter_buffer_->InsertPacket(*packet_, &retransmitted));

  frame_out = DecodeCompleteFrame();
  CheckOutFrame(frame_out, size_ * 2 + 4 * 2, true);
  EXPECT_EQ(VideoFrameType::kVideoFrameKey, frame_out->FrameType());
  jitter_buffer_->ReleaseFrame(frame_out);
}

TEST_F(TestBasicJitterBuffer, SpsAndPpsHandling) {
  auto& h264_header =
      packet_->video_header.video_type_header.emplace<RTPVideoHeaderH264>();
  packet_->timestamp = timestamp_;
  packet_->video_header.frame_type = VideoFrameType::kVideoFrameKey;
  packet_->video_header.is_first_packet_in_frame = true;
  packet_->markerBit = true;
  packet_->video_header.codec = kVideoCodecH264;
  h264_header.nalu_type = H264::NaluType::kIdr;
  h264_header.nalus[0].type = H264::NaluType::kIdr;
  h264_header.nalus[0].sps_id = -1;
  h264_header.nalus[0].pps_id = 0;
  h264_header.nalus_length = 1;
  bool retransmitted = false;
  EXPECT_EQ(kCompleteSession,
            jitter_buffer_->InsertPacket(*packet_, &retransmitted));
  // Not decodable since sps and pps are missing.
  EXPECT_EQ(nullptr, DecodeCompleteFrame());

  timestamp_ += 3000;
  packet_->timestamp = timestamp_;
  ++seq_num_;
  packet_->seqNum = seq_num_;
  packet_->video_header.frame_type = VideoFrameType::kVideoFrameKey;
  packet_->video_header.is_first_packet_in_frame = true;
  packet_->markerBit = false;
  packet_->video_header.codec = kVideoCodecH264;
  h264_header.nalu_type = H264::NaluType::kStapA;
  h264_header.nalus[0].type = H264::NaluType::kSps;
  h264_header.nalus[0].sps_id = 0;
  h264_header.nalus[0].pps_id = -1;
  h264_header.nalus[1].type = H264::NaluType::kPps;
  h264_header.nalus[1].sps_id = 0;
  h264_header.nalus[1].pps_id = 0;
  h264_header.nalus_length = 2;
  // Not complete since the marker bit hasn't been received.
  EXPECT_EQ(kIncomplete,
            jitter_buffer_->InsertPacket(*packet_, &retransmitted));

  ++seq_num_;
  packet_->seqNum = seq_num_;
  packet_->video_header.frame_type = VideoFrameType::kVideoFrameKey;
  packet_->video_header.is_first_packet_in_frame = false;
  packet_->markerBit = true;
  packet_->video_header.codec = kVideoCodecH264;
  h264_header.nalu_type = H264::NaluType::kIdr;
  h264_header.nalus[0].type = H264::NaluType::kIdr;
  h264_header.nalus[0].sps_id = -1;
  h264_header.nalus[0].pps_id = 0;
  h264_header.nalus_length = 1;
  // Complete and decodable since the pps and sps are received in the first
  // packet of this frame.
  EXPECT_EQ(kCompleteSession,
            jitter_buffer_->InsertPacket(*packet_, &retransmitted));
  VCMEncodedFrame* frame_out = DecodeCompleteFrame();
  ASSERT_NE(nullptr, frame_out);
  jitter_buffer_->ReleaseFrame(frame_out);

  timestamp_ += 3000;
  packet_->timestamp = timestamp_;
  ++seq_num_;
  packet_->seqNum = seq_num_;
  packet_->video_header.frame_type = VideoFrameType::kVideoFrameDelta;
  packet_->video_header.is_first_packet_in_frame = true;
  packet_->markerBit = true;
  packet_->video_header.codec = kVideoCodecH264;
  h264_header.nalu_type = H264::NaluType::kSlice;
  h264_header.nalus[0].type = H264::NaluType::kSlice;
  h264_header.nalus[0].sps_id = -1;
  h264_header.nalus[0].pps_id = 0;
  h264_header.nalus_length = 1;
  // Complete and decodable since sps, pps and key frame has been received.
  EXPECT_EQ(kCompleteSession,
            jitter_buffer_->InsertPacket(*packet_, &retransmitted));
  frame_out = DecodeCompleteFrame();
  ASSERT_NE(nullptr, frame_out);
  jitter_buffer_->ReleaseFrame(frame_out);
}

TEST_F(TestBasicJitterBuffer, DeltaFrame100PacketsWithSeqNumWrap) {
  seq_num_ = 0xfff0;
  packet_->video_header.frame_type = VideoFrameType::kVideoFrameKey;
  packet_->video_header.is_first_packet_in_frame = true;
  packet_->markerBit = false;
  packet_->seqNum = seq_num_;
  packet_->timestamp = timestamp_;

  bool retransmitted = false;
  EXPECT_EQ(kIncomplete,
            jitter_buffer_->InsertPacket(*packet_, &retransmitted));

  VCMEncodedFrame* frame_out = DecodeCompleteFrame();

  EXPECT_TRUE(frame_out == NULL);

  int loop = 0;
  do {
    seq_num_++;
    packet_->video_header.is_first_packet_in_frame = false;
    packet_->markerBit = false;
    packet_->seqNum = seq_num_;

    EXPECT_EQ(kIncomplete,
              jitter_buffer_->InsertPacket(*packet_, &retransmitted));

    frame_out = DecodeCompleteFrame();

    EXPECT_TRUE(frame_out == NULL);

    loop++;
  } while (loop < 98);

  seq_num_++;
  packet_->video_header.is_first_packet_in_frame = false;
  packet_->markerBit = true;
  packet_->seqNum = seq_num_;

  EXPECT_EQ(kCompleteSession,
            jitter_buffer_->InsertPacket(*packet_, &retransmitted));

  frame_out = DecodeCompleteFrame();

  CheckOutFrame(frame_out, 100 * size_, false);

  EXPECT_EQ(VideoFrameType::kVideoFrameKey, frame_out->FrameType());
  jitter_buffer_->ReleaseFrame(frame_out);
}

TEST_F(TestBasicJitterBuffer, PacketReorderingReverseWithNegSeqNumWrap) {
  // Insert "first" packet last seqnum.
  seq_num_ = 10;
  packet_->video_header.frame_type = VideoFrameType::kVideoFrameKey;
  packet_->video_header.is_first_packet_in_frame = false;
  packet_->markerBit = true;
  packet_->seqNum = seq_num_;

  bool retransmitted = false;
  EXPECT_EQ(kIncomplete,
            jitter_buffer_->InsertPacket(*packet_, &retransmitted));
  VCMEncodedFrame* frame_out = DecodeCompleteFrame();

  // Should not be complete.
  EXPECT_TRUE(frame_out == NULL);

  // Insert 98 frames.
  int loop = 0;
  do {
    seq_num_--;
    packet_->video_header.is_first_packet_in_frame = false;
    packet_->markerBit = false;
    packet_->seqNum = seq_num_;

    EXPECT_EQ(kIncomplete,
              jitter_buffer_->InsertPacket(*packet_, &retransmitted));

    frame_out = DecodeCompleteFrame();

    EXPECT_TRUE(frame_out == NULL);

    loop++;
  } while (loop < 98);

  // Insert last packet.
  seq_num_--;
  packet_->video_header.is_first_packet_in_frame = true;
  packet_->markerBit = false;
  packet_->seqNum = seq_num_;

  EXPECT_EQ(kCompleteSession,
            jitter_buffer_->InsertPacket(*packet_, &retransmitted));

  frame_out = DecodeCompleteFrame();
  CheckOutFrame(frame_out, 100 * size_, false);
  EXPECT_EQ(VideoFrameType::kVideoFrameKey, frame_out->FrameType());
  jitter_buffer_->ReleaseFrame(frame_out);
}

TEST_F(TestBasicJitterBuffer, TestInsertOldFrame) {
  //   -------      -------
  //  |   2   |    |   1   |
  //   -------      -------
  //  t = 3000     t = 2000
  seq_num_ = 2;
  timestamp_ = 3000;
  packet_->video_header.frame_type = VideoFrameType::kVideoFrameKey;
  packet_->video_header.is_first_packet_in_frame = true;
  packet_->markerBit = true;
  packet_->timestamp = timestamp_;
  packet_->seqNum = seq_num_;

  bool retransmitted = false;
  EXPECT_EQ(kCompleteSession,
            jitter_buffer_->InsertPacket(*packet_, &retransmitted));

  VCMEncodedFrame* frame_out = DecodeCompleteFrame();
  EXPECT_EQ(3000u, frame_out->RtpTimestamp());
  CheckOutFrame(frame_out, size_, false);
  EXPECT_EQ(VideoFrameType::kVideoFrameKey, frame_out->FrameType());
  jitter_buffer_->ReleaseFrame(frame_out);

  seq_num_--;
  timestamp_ = 2000;
  packet_->video_header.frame_type = VideoFrameType::kVideoFrameDelta;
  packet_->video_header.is_first_packet_in_frame = true;
  packet_->markerBit = true;
  packet_->seqNum = seq_num_;
  packet_->timestamp = timestamp_;

  EXPECT_EQ(kOldPacket, jitter_buffer_->InsertPacket(*packet_, &retransmitted));
}

TEST_F(TestBasicJitterBuffer, TestInsertOldFrameWithSeqNumWrap) {
  //   -------      -------
  //  |   2   |    |   1   |
  //   -------      -------
  //  t = 3000     t = 0xffffff00

  seq_num_ = 2;
  timestamp_ = 3000;
  packet_->video_header.frame_type = VideoFrameType::kVideoFrameKey;
  packet_->video_header.is_first_packet_in_frame = true;
  packet_->markerBit = true;
  packet_->seqNum = seq_num_;
  packet_->timestamp = timestamp_;

  bool retransmitted = false;
  EXPECT_EQ(kCompleteSession,
            jitter_buffer_->InsertPacket(*packet_, &retransmitted));

  VCMEncodedFrame* frame_out = DecodeCompleteFrame();
  EXPECT_EQ(timestamp_, frame_out->RtpTimestamp());

  CheckOutFrame(frame_out, size_, false);

  EXPECT_EQ(VideoFrameType::kVideoFrameKey, frame_out->FrameType());

  jitter_buffer_->ReleaseFrame(frame_out);

  seq_num_--;
  timestamp_ = 0xffffff00;
  packet_->video_header.frame_type = VideoFrameType::kVideoFrameDelta;
  packet_->video_header.is_first_packet_in_frame = true;
  packet_->markerBit = true;
  packet_->seqNum = seq_num_;
  packet_->timestamp = timestamp_;

  // This timestamp is old.
  EXPECT_EQ(kOldPacket, jitter_buffer_->InsertPacket(*packet_, &retransmitted));
}

TEST_F(TestBasicJitterBuffer, TimestampWrap) {
  //  ---------------     ---------------
  // |   1   |   2   |   |   3   |   4   |
  //  ---------------     ---------------
  //  t = 0xffffff00        t = 33*90

  timestamp_ = 0xffffff00;
  packet_->video_header.frame_type = VideoFrameType::kVideoFrameKey;
  packet_->video_header.is_first_packet_in_frame = true;
  packet_->markerBit = false;
  packet_->seqNum = seq_num_;
  packet_->timestamp = timestamp_;

  bool retransmitted = false;
  EXPECT_EQ(kIncomplete,
            jitter_buffer_->InsertPacket(*packet_, &retransmitted));

  VCMEncodedFrame* frame_out = DecodeCompleteFrame();
  EXPECT_TRUE(frame_out == NULL);

  seq_num_++;
  packet_->video_header.is_first_packet_in_frame = false;
  packet_->markerBit = true;
  packet_->seqNum = seq_num_;

  EXPECT_EQ(kCompleteSession,
            jitter_buffer_->InsertPacket(*packet_, &retransmitted));

  frame_out = DecodeCompleteFrame();
  CheckOutFrame(frame_out, 2 * size_, false);
  jitter_buffer_->ReleaseFrame(frame_out);

  seq_num_++;
  timestamp_ += 33 * 90;
  packet_->video_header.frame_type = VideoFrameType::kVideoFrameDelta;
  packet_->video_header.is_first_packet_in_frame = true;
  packet_->markerBit = false;
  packet_->seqNum = seq_num_;
  packet_->timestamp = timestamp_;

  EXPECT_EQ(kIncomplete,
            jitter_buffer_->InsertPacket(*packet_, &retransmitted));

  frame_out = DecodeCompleteFrame();
  EXPECT_TRUE(frame_out == NULL);

  seq_num_++;
  packet_->video_header.is_first_packet_in_frame = false;
  packet_->markerBit = true;
  packet_->seqNum = seq_num_;

  EXPECT_EQ(kCompleteSession,
            jitter_buffer_->InsertPacket(*packet_, &retransmitted));

  frame_out = DecodeCompleteFrame();
  CheckOutFrame(frame_out, 2 * size_, false);
  EXPECT_EQ(VideoFrameType::kVideoFrameDelta, frame_out->FrameType());
  jitter_buffer_->ReleaseFrame(frame_out);
}

TEST_F(TestBasicJitterBuffer, 2FrameWithTimestampWrap) {
  //   -------          -------
  //  |   1   |        |   2   |
  //   -------          -------
  // t = 0xffffff00    t = 2700

  timestamp_ = 0xffffff00;
  packet_->video_header.frame_type = VideoFrameType::kVideoFrameKey;
  packet_->video_header.is_first_packet_in_frame = true;
  packet_->markerBit = true;
  packet_->timestamp = timestamp_;

  bool retransmitted = false;
  // Insert first frame (session will be complete).
  EXPECT_EQ(kCompleteSession,
            jitter_buffer_->InsertPacket(*packet_, &retransmitted));

  // Insert next frame.
  seq_num_++;
  timestamp_ = 2700;
  packet_->video_header.frame_type = VideoFrameType::kVideoFrameDelta;
  packet_->video_header.is_first_packet_in_frame = true;
  packet_->markerBit = true;
  packet_->seqNum = seq_num_;
  packet_->timestamp = timestamp_;

  EXPECT_EQ(kCompleteSession,
            jitter_buffer_->InsertPacket(*packet_, &retransmitted));

  VCMEncodedFrame* frame_out = DecodeCompleteFrame();
  EXPECT_EQ(0xffffff00, frame_out->RtpTimestamp());
  CheckOutFrame(frame_out, size_, false);
  EXPECT_EQ(VideoFrameType::kVideoFrameKey, frame_out->FrameType());
  jitter_buffer_->ReleaseFrame(frame_out);

  VCMEncodedFrame* frame_out2 = DecodeCompleteFrame();
  EXPECT_EQ(2700u, frame_out2->RtpTimestamp());
  CheckOutFrame(frame_out2, size_, false);
  EXPECT_EQ(VideoFrameType::kVideoFrameDelta, frame_out2->FrameType());
  jitter_buffer_->ReleaseFrame(frame_out2);
}

TEST_F(TestBasicJitterBuffer, Insert2FramesReOrderedWithTimestampWrap) {
  //   -------          -------
  //  |   2   |        |   1   |
  //   -------          -------
  //  t = 2700        t = 0xffffff00

  seq_num_ = 2;
  timestamp_ = 2700;
  packet_->video_header.frame_type = VideoFrameType::kVideoFrameDelta;
  packet_->video_header.is_first_packet_in_frame = true;
  packet_->markerBit = true;
  packet_->seqNum = seq_num_;
  packet_->timestamp = timestamp_;

  bool retransmitted = false;
  EXPECT_EQ(kCompleteSession,
            jitter_buffer_->InsertPacket(*packet_, &retransmitted));

  // Insert second frame
  seq_num_--;
  timestamp_ = 0xffffff00;
  packet_->video_header.frame_type = VideoFrameType::kVideoFrameKey;
  packet_->video_header.is_first_packet_in_frame = true;
  packet_->markerBit = true;
  packet_->seqNum = seq_num_;
  packet_->timestamp = timestamp_;

  EXPECT_EQ(kCompleteSession,
            jitter_buffer_->InsertPacket(*packet_, &retransmitted));

  VCMEncodedFrame* frame_out = DecodeCompleteFrame();
  EXPECT_EQ(0xffffff00, frame_out->RtpTimestamp());
  CheckOutFrame(frame_out, size_, false);
  EXPECT_EQ(VideoFrameType::kVideoFrameKey, frame_out->FrameType());
  jitter_buffer_->ReleaseFrame(frame_out);

  VCMEncodedFrame* frame_out2 = DecodeCompleteFrame();
  EXPECT_EQ(2700u, frame_out2->RtpTimestamp());
  CheckOutFrame(frame_out2, size_, false);
  EXPECT_EQ(VideoFrameType::kVideoFrameDelta, frame_out2->FrameType());
  jitter_buffer_->ReleaseFrame(frame_out2);
}

TEST_F(TestBasicJitterBuffer, DeltaFrameWithMoreThanMaxNumberOfPackets) {
  int loop = 0;
  bool firstPacket = true;
  bool retransmitted = false;
  // Insert kMaxPacketsInJitterBuffer into frame.
  do {
    seq_num_++;
    packet_->video_header.is_first_packet_in_frame = false;
    packet_->markerBit = false;
    packet_->seqNum = seq_num_;

    if (firstPacket) {
      EXPECT_EQ(kIncomplete,
                jitter_buffer_->InsertPacket(*packet_, &retransmitted));
      firstPacket = false;
    } else {
      EXPECT_EQ(kIncomplete,
                jitter_buffer_->InsertPacket(*packet_, &retransmitted));
    }

    loop++;
  } while (loop < kMaxPacketsInSession);

  // Max number of packets inserted.
  // Insert one more packet.
  seq_num_++;
  packet_->video_header.is_first_packet_in_frame = false;
  packet_->markerBit = true;
  packet_->seqNum = seq_num_;

  // Insert the packet -> frame recycled.
  EXPECT_EQ(kSizeError, jitter_buffer_->InsertPacket(*packet_, &retransmitted));
  EXPECT_TRUE(NULL == DecodeCompleteFrame());
}

TEST_F(TestBasicJitterBuffer, ExceedNumOfFrameWithSeqNumWrap) {
  // TEST fill JB with more than max number of frame (50 delta frames +
  // 51 key frames) with wrap in seq_num_
  //
  //  --------------------------------------------------------------
  // | 65485 | 65486 | 65487 | .... | 65535 | 0 | 1 | 2 | .....| 50 |
  //  --------------------------------------------------------------
  // |<-----------delta frames------------->|<------key frames----->|

  // Make sure the jitter doesn't request a keyframe after too much non-
  // decodable frames.
  jitter_buffer_->SetNackSettings(kMaxNumberOfFrames, kMaxNumberOfFrames, 0);

  int loop = 0;
  seq_num_ = 65485;
  uint32_t first_key_frame_timestamp = 0;
  bool retransmitted = false;
  // Insert MAX_NUMBER_OF_FRAMES frames.
  do {
    timestamp_ += 33 * 90;
    seq_num_++;
    packet_->video_header.is_first_packet_in_frame = true;
    packet_->markerBit = true;
    packet_->seqNum = seq_num_;
    packet_->timestamp = timestamp_;

    if (loop == 50) {
      first_key_frame_timestamp = packet_->timestamp;
      packet_->video_header.frame_type = VideoFrameType::kVideoFrameKey;
    }

    // Insert frame.
    EXPECT_EQ(kCompleteSession,
              jitter_buffer_->InsertPacket(*packet_, &retransmitted));

    loop++;
  } while (loop < kMaxNumberOfFrames);

  // Max number of frames inserted.

  // Insert one more frame.
  timestamp_ += 33 * 90;
  seq_num_++;
  packet_->video_header.is_first_packet_in_frame = true;
  packet_->markerBit = true;
  packet_->seqNum = seq_num_;
  packet_->timestamp = timestamp_;

  // Now, no free frame - frames will be recycled until first key frame.
  EXPECT_EQ(kFlushIndicator,
            jitter_buffer_->InsertPacket(*packet_, &retransmitted));

  VCMEncodedFrame* frame_out = DecodeCompleteFrame();
  EXPECT_EQ(first_key_frame_timestamp, frame_out->RtpTimestamp());
  CheckOutFrame(frame_out, size_, false);
  EXPECT_EQ(VideoFrameType::kVideoFrameKey, frame_out->FrameType());
  jitter_buffer_->ReleaseFrame(frame_out);
}

TEST_F(TestBasicJitterBuffer, EmptyLastFrame) {
  seq_num_ = 3;
  // Insert one empty packet per frame, should never return the last timestamp
  // inserted. Only return empty frames in the presence of subsequent frames.
  int maxSize = 1000;
  bool retransmitted = false;
  for (int i = 0; i < maxSize + 10; i++) {
    timestamp_ += 33 * 90;
    seq_num_++;
    packet_->video_header.is_first_packet_in_frame = false;
    packet_->markerBit = false;
    packet_->seqNum = seq_num_;
    packet_->timestamp = timestamp_;
    packet_->video_header.frame_type = VideoFrameType::kEmptyFrame;

    EXPECT_EQ(kNoError, jitter_buffer_->InsertPacket(*packet_, &retransmitted));
  }
}

TEST_F(TestBasicJitterBuffer, NextFrameWhenIncomplete) {
  // Test that a we cannot get incomplete frames from the JB if we haven't
  // received the marker bit, unless we have received a packet from a later
  // timestamp.
  // Start with a complete key frame - insert and decode.
  jitter_buffer_->SetNackSettings(kMaxNumberOfFrames, kMaxNumberOfFrames, 0);
  packet_->video_header.frame_type = VideoFrameType::kVideoFrameKey;
  packet_->video_header.is_first_packet_in_frame = true;
  packet_->markerBit = true;
  bool retransmitted = false;

  EXPECT_EQ(kCompleteSession,
            jitter_buffer_->InsertPacket(*packet_, &retransmitted));
  VCMEncodedFrame* frame_out = DecodeCompleteFrame();
  EXPECT_TRUE(frame_out != NULL);
  jitter_buffer_->ReleaseFrame(frame_out);

  packet_->seqNum += 2;
  packet_->timestamp += 33 * 90;
  packet_->video_header.frame_type = VideoFrameType::kVideoFrameDelta;
  packet_->video_header.is_first_packet_in_frame = false;
  packet_->markerBit = false;

  EXPECT_EQ(kIncomplete,
            jitter_buffer_->InsertPacket(*packet_, &retransmitted));

  packet_->seqNum += 2;
  packet_->timestamp += 33 * 90;
  packet_->video_header.is_first_packet_in_frame = true;

  EXPECT_EQ(kIncomplete,
            jitter_buffer_->InsertPacket(*packet_, &retransmitted));
}

TEST_F(TestRunningJitterBuffer, Full) {
  // Make sure the jitter doesn't request a keyframe after too much non-
  // decodable frames.
  jitter_buffer_->SetNackSettings(kMaxNumberOfFrames, kMaxNumberOfFrames, 0);
  // Insert a key frame and decode it.
  EXPECT_GE(InsertFrame(VideoFrameType::kVideoFrameKey), kNoError);
  EXPECT_TRUE(DecodeCompleteFrame());
  DropFrame(1);
  // Fill the jitter buffer.
  EXPECT_GE(InsertFrames(kMaxNumberOfFrames, VideoFrameType::kVideoFrameDelta),
            kNoError);
  // Make sure we can't decode these frames.
  EXPECT_FALSE(DecodeCompleteFrame());
  // This frame will make the jitter buffer recycle frames until a key frame.
  // Since none is found it will have to wait until the next key frame before
  // decoding.
  EXPECT_EQ(kFlushIndicator, InsertFrame(VideoFrameType::kVideoFrameDelta));
  EXPECT_FALSE(DecodeCompleteFrame());
}

TEST_F(TestRunningJitterBuffer, EmptyPackets) {
  // Make sure a frame can get complete even though empty packets are missing.
  stream_generator_->GenerateFrame(VideoFrameType::kVideoFrameKey, 3, 3,
                                   clock_->TimeInMilliseconds());
  bool request_key_frame = false;
  // Insert empty packet.
  EXPECT_EQ(kNoError, InsertPacketAndPop(4));
  EXPECT_FALSE(request_key_frame);
  // Insert 3 media packets.
  EXPECT_EQ(kIncomplete, InsertPacketAndPop(0));
  EXPECT_FALSE(request_key_frame);
  EXPECT_EQ(kIncomplete, InsertPacketAndPop(0));
  EXPECT_FALSE(request_key_frame);
  EXPECT_EQ(kCompleteSession, InsertPacketAndPop(0));
  EXPECT_FALSE(request_key_frame);
  // Insert empty packet.
  EXPECT_EQ(kCompleteSession, InsertPacketAndPop(0));
  EXPECT_FALSE(request_key_frame);
}

TEST_F(TestRunningJitterBuffer, SkipToKeyFrame) {
  // Insert delta frames.
  EXPECT_GE(InsertFrames(5, VideoFrameType::kVideoFrameDelta), kNoError);
  // Can't decode without a key frame.
  EXPECT_FALSE(DecodeCompleteFrame());
  InsertFrame(VideoFrameType::kVideoFrameKey);
  // Skip to the next key frame.
  EXPECT_TRUE(DecodeCompleteFrame());
}

TEST_F(TestRunningJitterBuffer, DontSkipToKeyFrameIfDecodable) {
  InsertFrame(VideoFrameType::kVideoFrameKey);
  EXPECT_TRUE(DecodeCompleteFrame());
  const int kNumDeltaFrames = 5;
  EXPECT_GE(InsertFrames(kNumDeltaFrames, VideoFrameType::kVideoFrameDelta),
            kNoError);
  InsertFrame(VideoFrameType::kVideoFrameKey);
  for (int i = 0; i < kNumDeltaFrames + 1; ++i) {
    EXPECT_TRUE(DecodeCompleteFrame());
  }
}

TEST_F(TestRunningJitterBuffer, KeyDeltaKeyDelta) {
  InsertFrame(VideoFrameType::kVideoFrameKey);
  EXPECT_TRUE(DecodeCompleteFrame());
  const int kNumDeltaFrames = 5;
  EXPECT_GE(InsertFrames(kNumDeltaFrames, VideoFrameType::kVideoFrameDelta),
            kNoError);
  InsertFrame(VideoFrameType::kVideoFrameKey);
  EXPECT_GE(InsertFrames(kNumDeltaFrames, VideoFrameType::kVideoFrameDelta),
            kNoError);
  InsertFrame(VideoFrameType::kVideoFrameKey);
  for (int i = 0; i < 2 * (kNumDeltaFrames + 1); ++i) {
    EXPECT_TRUE(DecodeCompleteFrame());
  }
}

TEST_F(TestRunningJitterBuffer, TwoPacketsNonContinuous) {
  InsertFrame(VideoFrameType::kVideoFrameKey);
  EXPECT_TRUE(DecodeCompleteFrame());
  stream_generator_->GenerateFrame(VideoFrameType::kVideoFrameDelta, 1, 0,
                                   clock_->TimeInMilliseconds());
  clock_->AdvanceTimeMilliseconds(kDefaultFramePeriodMs);
  stream_generator_->GenerateFrame(VideoFrameType::kVideoFrameDelta, 2, 0,
                                   clock_->TimeInMilliseconds());
  EXPECT_EQ(kIncomplete, InsertPacketAndPop(1));
  EXPECT_EQ(kCompleteSession, InsertPacketAndPop(1));
  EXPECT_FALSE(DecodeCompleteFrame());
  EXPECT_EQ(kCompleteSession, InsertPacketAndPop(0));
  EXPECT_TRUE(DecodeCompleteFrame());
  EXPECT_TRUE(DecodeCompleteFrame());
}

TEST_F(TestJitterBufferNack, EmptyPackets) {
  // Make sure empty packets doesn't clog the jitter buffer.
  EXPECT_GE(InsertFrames(kMaxNumberOfFrames, VideoFrameType::kEmptyFrame),
            kNoError);
  InsertFrame(VideoFrameType::kVideoFrameKey);
  EXPECT_TRUE(DecodeCompleteFrame());
}

TEST_F(TestJitterBufferNack, NackTooOldPackets) {
  // Insert a key frame and decode it.
  EXPECT_GE(InsertFrame(VideoFrameType::kVideoFrameKey), kNoError);
  EXPECT_TRUE(DecodeCompleteFrame());

  // Drop one frame and insert `kNackHistoryLength` to trigger NACKing a too
  // old packet.
  DropFrame(1);
  // Insert a frame which should trigger a recycle until the next key frame.
  EXPECT_EQ(kFlushIndicator, InsertFrames(oldest_packet_to_nack_ + 1,
                                          VideoFrameType::kVideoFrameDelta));
  EXPECT_FALSE(DecodeCompleteFrame());

  bool request_key_frame = false;
  std::vector<uint16_t> nack_list =
      jitter_buffer_->GetNackList(&request_key_frame);
  // No key frame will be requested since the jitter buffer is empty.
  EXPECT_FALSE(request_key_frame);
  EXPECT_EQ(0u, nack_list.size());

  EXPECT_GE(InsertFrame(VideoFrameType::kVideoFrameDelta), kNoError);
  // Waiting for a key frame.
  EXPECT_FALSE(DecodeCompleteFrame());

  // The next complete continuous frame isn't a key frame, but we're waiting
  // for one.
  EXPECT_FALSE(DecodeCompleteFrame());
  EXPECT_GE(InsertFrame(VideoFrameType::kVideoFrameKey), kNoError);
  // Skipping ahead to the key frame.
  EXPECT_TRUE(DecodeCompleteFrame());
}

TEST_F(TestJitterBufferNack, NackLargeJitterBuffer) {
  // Insert a key frame and decode it.
  EXPECT_GE(InsertFrame(VideoFrameType::kVideoFrameKey), kNoError);
  EXPECT_TRUE(DecodeCompleteFrame());

  // Insert a frame which should trigger a recycle until the next key frame.
  EXPECT_GE(
      InsertFrames(oldest_packet_to_nack_, VideoFrameType::kVideoFrameDelta),
      kNoError);

  bool request_key_frame = false;
  std::vector<uint16_t> nack_list =
      jitter_buffer_->GetNackList(&request_key_frame);
  // Verify that the jitter buffer does not request a key frame.
  EXPECT_FALSE(request_key_frame);
  // Verify that no packets are NACKed.
  EXPECT_EQ(0u, nack_list.size());
  // Verify that we can decode the next frame.
  EXPECT_TRUE(DecodeCompleteFrame());
}

TEST_F(TestJitterBufferNack, NackListFull) {
  // Insert a key frame and decode it.
  EXPECT_GE(InsertFrame(VideoFrameType::kVideoFrameKey), kNoError);
  EXPECT_TRUE(DecodeCompleteFrame());

  // Generate and drop `kNackHistoryLength` packets to fill the NACK list.
  DropFrame(max_nack_list_size_ + 1);
  // Insert a frame which should trigger a recycle until the next key frame.
  EXPECT_EQ(kFlushIndicator, InsertFrame(VideoFrameType::kVideoFrameDelta));
  EXPECT_FALSE(DecodeCompleteFrame());

  bool request_key_frame = false;
  jitter_buffer_->GetNackList(&request_key_frame);
  // The jitter buffer is empty, so we won't request key frames until we get a
  // packet.
  EXPECT_FALSE(request_key_frame);

  EXPECT_GE(InsertFrame(VideoFrameType::kVideoFrameDelta), kNoError);
  // Now we have a packet in the jitter buffer, a key frame will be requested
  // since it's not a key frame.
  jitter_buffer_->GetNackList(&request_key_frame);
  // The jitter buffer is empty, so we won't request key frames until we get a
  // packet.
  EXPECT_TRUE(request_key_frame);
  // The next complete continuous frame isn't a key frame, but we're waiting
  // for one.
  EXPECT_FALSE(DecodeCompleteFrame());
  EXPECT_GE(InsertFrame(VideoFrameType::kVideoFrameKey), kNoError);
  // Skipping ahead to the key frame.
  EXPECT_TRUE(DecodeCompleteFrame());
}

TEST_F(TestJitterBufferNack, NoNackListReturnedBeforeFirstDecode) {
  DropFrame(10);
  // Insert a frame and try to generate a NACK list. Shouldn't get one.
  EXPECT_GE(InsertFrame(VideoFrameType::kVideoFrameDelta), kNoError);
  bool request_key_frame = false;
  std::vector<uint16_t> nack_list =
      jitter_buffer_->GetNackList(&request_key_frame);
  // No list generated, and a key frame request is signaled.
  EXPECT_EQ(0u, nack_list.size());
  EXPECT_TRUE(request_key_frame);
}

TEST_F(TestJitterBufferNack, NackListBuiltBeforeFirstDecode) {
  stream_generator_->Init(0, clock_->TimeInMilliseconds());
  InsertFrame(VideoFrameType::kVideoFrameKey);
  stream_generator_->GenerateFrame(VideoFrameType::kVideoFrameDelta, 2, 0,
                                   clock_->TimeInMilliseconds());
  stream_generator_->NextPacket(NULL);  // Drop packet.
  EXPECT_EQ(kIncomplete, InsertPacketAndPop(0));
  EXPECT_TRUE(DecodeCompleteFrame());
  bool extended = false;
  std::vector<uint16_t> nack_list = jitter_buffer_->GetNackList(&extended);
  EXPECT_EQ(1u, nack_list.size());
}

TEST_F(TestJitterBufferNack, VerifyRetransmittedFlag) {
  stream_generator_->Init(0, clock_->TimeInMilliseconds());
  stream_generator_->GenerateFrame(VideoFrameType::kVideoFrameKey, 3, 0,
                                   clock_->TimeInMilliseconds());
  VCMPacket packet;
  stream_generator_->PopPacket(&packet, 0);
  bool retransmitted = false;
  EXPECT_EQ(kIncomplete, jitter_buffer_->InsertPacket(packet, &retransmitted));
  EXPECT_FALSE(retransmitted);
  // Drop second packet.
  stream_generator_->PopPacket(&packet, 1);
  EXPECT_EQ(kIncomplete, jitter_buffer_->InsertPacket(packet, &retransmitted));
  EXPECT_FALSE(retransmitted);
  EXPECT_FALSE(DecodeCompleteFrame());
  bool extended = false;
  std::vector<uint16_t> nack_list = jitter_buffer_->GetNackList(&extended);
  uint16_t seq_num;
  EXPECT_EQ(1u, nack_list.size());
  seq_num = nack_list[0];
  stream_generator_->PopPacket(&packet, 0);
  EXPECT_EQ(packet.seqNum, seq_num);
  EXPECT_EQ(kCompleteSession,
            jitter_buffer_->InsertPacket(packet, &retransmitted));
  EXPECT_TRUE(retransmitted);
  EXPECT_TRUE(DecodeCompleteFrame());
}

TEST_F(TestJitterBufferNack, UseNackToRecoverFirstKeyFrame) {
  stream_generator_->Init(0, clock_->TimeInMilliseconds());
  stream_generator_->GenerateFrame(VideoFrameType::kVideoFrameKey, 3, 0,
                                   clock_->TimeInMilliseconds());
  EXPECT_EQ(kIncomplete, InsertPacketAndPop(0));
  // Drop second packet.
  EXPECT_EQ(kIncomplete, InsertPacketAndPop(1));
  EXPECT_FALSE(DecodeCompleteFrame());
  bool extended = false;
  std::vector<uint16_t> nack_list = jitter_buffer_->GetNackList(&extended);
  uint16_t seq_num;
  ASSERT_EQ(1u, nack_list.size());
  seq_num = nack_list[0];
  VCMPacket packet;
  stream_generator_->GetPacket(&packet, 0);
  EXPECT_EQ(packet.seqNum, seq_num);
}

TEST_F(TestJitterBufferNack, UseNackToRecoverFirstKeyFrameSecondInQueue) {
  VCMPacket packet;
  stream_generator_->Init(0, clock_->TimeInMilliseconds());
  // First frame is delta.
  stream_generator_->GenerateFrame(VideoFrameType::kVideoFrameDelta, 3, 0,
                                   clock_->TimeInMilliseconds());
  EXPECT_EQ(kIncomplete, InsertPacketAndPop(0));
  // Drop second packet in frame.
  ASSERT_TRUE(stream_generator_->PopPacket(&packet, 0));
  EXPECT_EQ(kIncomplete, InsertPacketAndPop(0));
  // Second frame is key.
  stream_generator_->GenerateFrame(VideoFrameType::kVideoFrameKey, 3, 0,
                                   clock_->TimeInMilliseconds() + 10);
  EXPECT_EQ(kIncomplete, InsertPacketAndPop(0));
  // Drop second packet in frame.
  EXPECT_EQ(kIncomplete, InsertPacketAndPop(1));
  EXPECT_FALSE(DecodeCompleteFrame());
  bool extended = false;
  std::vector<uint16_t> nack_list = jitter_buffer_->GetNackList(&extended);
  uint16_t seq_num;
  ASSERT_EQ(1u, nack_list.size());
  seq_num = nack_list[0];
  stream_generator_->GetPacket(&packet, 0);
  EXPECT_EQ(packet.seqNum, seq_num);
}

TEST_F(TestJitterBufferNack, NormalOperation) {
  EXPECT_GE(InsertFrame(VideoFrameType::kVideoFrameKey), kNoError);
  EXPECT_TRUE(DecodeCompleteFrame());

  //  ----------------------------------------------------------------
  // | 1 | 2 | .. | 8 | 9 | x | 11 | 12 | .. | 19 | x | 21 | .. | 100 |
  //  ----------------------------------------------------------------
  stream_generator_->GenerateFrame(VideoFrameType::kVideoFrameKey, 100, 0,
                                   clock_->TimeInMilliseconds());
  clock_->AdvanceTimeMilliseconds(kDefaultFramePeriodMs);
  EXPECT_EQ(kIncomplete, InsertPacketAndPop(0));
  // Verify that the frame is incomplete.
  EXPECT_FALSE(DecodeCompleteFrame());
  while (stream_generator_->PacketsRemaining() > 1) {
    if (stream_generator_->NextSequenceNumber() % 10 != 0) {
      EXPECT_EQ(kIncomplete, InsertPacketAndPop(0));
    } else {
      stream_generator_->NextPacket(NULL);  // Drop packet
    }
  }
  EXPECT_EQ(kIncomplete, InsertPacketAndPop(0));
  EXPECT_EQ(0, stream_generator_->PacketsRemaining());
  EXPECT_FALSE(DecodeCompleteFrame());
  bool request_key_frame = false;

  // Verify the NACK list.
  std::vector<uint16_t> nack_list =
      jitter_buffer_->GetNackList(&request_key_frame);
  const size_t kExpectedNackSize = 9;
  ASSERT_EQ(kExpectedNackSize, nack_list.size());
  for (size_t i = 0; i < nack_list.size(); ++i)
    EXPECT_EQ((1 + i) * 10, nack_list[i]);
}

TEST_F(TestJitterBufferNack, NormalOperationWrap) {
  bool request_key_frame = false;
  //  -------   ------------------------------------------------------------
  // | 65532 | | 65533 | 65534 | 65535 | x | 1 | .. | 9 | x | 11 |.....| 96 |
  //  -------   ------------------------------------------------------------
  stream_generator_->Init(65532, clock_->TimeInMilliseconds());
  InsertFrame(VideoFrameType::kVideoFrameKey);
  EXPECT_FALSE(request_key_frame);
  EXPECT_TRUE(DecodeCompleteFrame());
  stream_generator_->GenerateFrame(VideoFrameType::kVideoFrameDelta, 100, 0,
                                   clock_->TimeInMilliseconds());
  EXPECT_EQ(kIncomplete, InsertPacketAndPop(0));
  while (stream_generator_->PacketsRemaining() > 1) {
    if (stream_generator_->NextSequenceNumber() % 10 != 0) {
      EXPECT_EQ(kIncomplete, InsertPacketAndPop(0));
      EXPECT_FALSE(request_key_frame);
    } else {
      stream_generator_->NextPacket(NULL);  // Drop packet
    }
  }
  EXPECT_EQ(kIncomplete, InsertPacketAndPop(0));
  EXPECT_FALSE(request_key_frame);
  EXPECT_EQ(0, stream_generator_->PacketsRemaining());
  EXPECT_FALSE(DecodeCompleteFrame());
  EXPECT_FALSE(DecodeCompleteFrame());
  bool extended = false;
  std::vector<uint16_t> nack_list = jitter_buffer_->GetNackList(&extended);
  // Verify the NACK list.
  const size_t kExpectedNackSize = 10;
  ASSERT_EQ(kExpectedNackSize, nack_list.size());
  for (size_t i = 0; i < nack_list.size(); ++i)
    EXPECT_EQ(i * 10, nack_list[i]);
}

TEST_F(TestJitterBufferNack, NormalOperationWrap2) {
  bool request_key_frame = false;
  //  -----------------------------------
  // | 65532 | 65533 | 65534 | x | 0 | 1 |
  //  -----------------------------------
  stream_generator_->Init(65532, clock_->TimeInMilliseconds());
  InsertFrame(VideoFrameType::kVideoFrameKey);
  EXPECT_FALSE(request_key_frame);
  EXPECT_TRUE(DecodeCompleteFrame());
  stream_generator_->GenerateFrame(VideoFrameType::kVideoFrameDelta, 1, 0,
                                   clock_->TimeInMilliseconds());
  clock_->AdvanceTimeMilliseconds(kDefaultFramePeriodMs);
  for (int i = 0; i < 5; ++i) {
    if (stream_generator_->NextSequenceNumber() != 65535) {
      EXPECT_EQ(kCompleteSession, InsertPacketAndPop(0));
      EXPECT_FALSE(request_key_frame);
    } else {
      stream_generator_->NextPacket(NULL);  // Drop packet
    }
    stream_generator_->GenerateFrame(VideoFrameType::kVideoFrameDelta, 1, 0,
                                     clock_->TimeInMilliseconds());
    clock_->AdvanceTimeMilliseconds(kDefaultFramePeriodMs);
  }
  EXPECT_EQ(kCompleteSession, InsertPacketAndPop(0));
  EXPECT_FALSE(request_key_frame);
  bool extended = false;
  std::vector<uint16_t> nack_list = jitter_buffer_->GetNackList(&extended);
  // Verify the NACK list.
  ASSERT_EQ(1u, nack_list.size());
  EXPECT_EQ(65535, nack_list[0]);
}

TEST_F(TestJitterBufferNack, ResetByFutureKeyFrameDoesntError) {
  stream_generator_->Init(0, clock_->TimeInMilliseconds());
  InsertFrame(VideoFrameType::kVideoFrameKey);
  EXPECT_TRUE(DecodeCompleteFrame());
  bool extended = false;
  std::vector<uint16_t> nack_list = jitter_buffer_->GetNackList(&extended);
  EXPECT_EQ(0u, nack_list.size());

  // Far-into-the-future video frame, could be caused by resetting the encoder
  // or otherwise restarting. This should not fail when error when the packet is
  // a keyframe, even if all of the nack list needs to be flushed.
  stream_generator_->Init(10000, clock_->TimeInMilliseconds());
  clock_->AdvanceTimeMilliseconds(kDefaultFramePeriodMs);
  InsertFrame(VideoFrameType::kVideoFrameKey);
  EXPECT_TRUE(DecodeCompleteFrame());
  nack_list = jitter_buffer_->GetNackList(&extended);
  EXPECT_EQ(0u, nack_list.size());

  // Stream should be decodable from this point.
  clock_->AdvanceTimeMilliseconds(kDefaultFramePeriodMs);
  InsertFrame(VideoFrameType::kVideoFrameDelta);
  EXPECT_TRUE(DecodeCompleteFrame());
  nack_list = jitter_buffer_->GetNackList(&extended);
  EXPECT_EQ(0u, nack_list.size());
}
}  // namespace webrtc
