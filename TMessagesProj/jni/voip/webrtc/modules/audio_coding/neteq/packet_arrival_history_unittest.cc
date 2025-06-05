/*
 *  Copyright (c) 2022 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "modules/audio_coding/neteq/packet_arrival_history.h"

#include <cstdint>
#include <limits>

#include "test/gtest.h"

namespace webrtc {
namespace {

constexpr int kFs = 8000;
constexpr int kFsKhz = kFs / 1000;
constexpr int kFrameSizeMs = 20;
constexpr int kFrameSizeSamples = kFrameSizeMs * kFsKhz;
constexpr int kWindowSizeMs = 1000;

class PacketArrivalHistoryTest : public testing::Test {
 public:
  PacketArrivalHistoryTest() : history_(&tick_timer_, kWindowSizeMs) {
    history_.set_sample_rate(kFs);
  }
  void IncrementTime(int delta_ms) {
    tick_timer_.Increment(delta_ms / tick_timer_.ms_per_tick());
  }
  int InsertPacketAndGetDelay(int timestamp_delta_ms) {
    uint32_t timestamp = timestamp_ + timestamp_delta_ms * kFsKhz;
    if (timestamp_delta_ms > 0) {
      timestamp_ = timestamp;
    }
    EXPECT_TRUE(history_.Insert(timestamp, kFrameSizeSamples));
    EXPECT_EQ(history_.IsNewestRtpTimestamp(timestamp),
              timestamp_delta_ms >= 0);
    return history_.GetDelayMs(timestamp);
  }

 protected:
  TickTimer tick_timer_;
  PacketArrivalHistory history_;
  uint32_t timestamp_ = 0x12345678;
};

TEST_F(PacketArrivalHistoryTest, RelativeArrivalDelay) {
  // Insert first packet.
  EXPECT_EQ(InsertPacketAndGetDelay(0), 0);

  IncrementTime(kFrameSizeMs);
  EXPECT_EQ(InsertPacketAndGetDelay(kFrameSizeMs), 0);

  IncrementTime(2 * kFrameSizeMs);
  EXPECT_EQ(InsertPacketAndGetDelay(kFrameSizeMs), 20);

  // Reordered packet.
  EXPECT_EQ(InsertPacketAndGetDelay(-3 * kFrameSizeMs), 80);

  IncrementTime(2 * kFrameSizeMs);
  EXPECT_EQ(InsertPacketAndGetDelay(kFrameSizeMs), 40);

  // Move reference packet forward.
  EXPECT_EQ(InsertPacketAndGetDelay(4 * kFrameSizeMs), 0);

  IncrementTime(2 * kFrameSizeMs);
  EXPECT_EQ(InsertPacketAndGetDelay(kFrameSizeMs), 20);

  // Earlier packet is now more delayed due to the new reference packet.
  EXPECT_EQ(history_.GetMaxDelayMs(), 80);
}

TEST_F(PacketArrivalHistoryTest, ReorderedPackets) {
  // Insert first packet.
  EXPECT_EQ(InsertPacketAndGetDelay(0), 0);

  // Insert reordered packet.
  EXPECT_EQ(InsertPacketAndGetDelay(-80), 80);

  // Insert another reordered packet.
  EXPECT_EQ(InsertPacketAndGetDelay(-kFrameSizeMs), 20);

  // Insert the next packet in order and verify that the relative delay is
  // estimated based on the first inserted packet.
  IncrementTime(4 * kFrameSizeMs);
  EXPECT_EQ(InsertPacketAndGetDelay(kFrameSizeMs), 60);

  EXPECT_EQ(history_.GetMaxDelayMs(), 60);
}

TEST_F(PacketArrivalHistoryTest, MaxHistorySize) {
  EXPECT_EQ(InsertPacketAndGetDelay(0), 0);

  IncrementTime(2 * kFrameSizeMs);
  EXPECT_EQ(InsertPacketAndGetDelay(kFrameSizeMs), 20);
  EXPECT_EQ(history_.GetMaxDelayMs(), 20);

  // Insert next packet with a timestamp difference larger than maximum history
  // size. This removes the previously inserted packet from the history.
  IncrementTime(kWindowSizeMs + kFrameSizeMs);
  EXPECT_EQ(InsertPacketAndGetDelay(kFrameSizeMs + kWindowSizeMs), 0);
  EXPECT_EQ(history_.GetMaxDelayMs(), 0);
}

TEST_F(PacketArrivalHistoryTest, TimestampWraparound) {
  timestamp_ = std::numeric_limits<uint32_t>::max();
  EXPECT_EQ(InsertPacketAndGetDelay(0), 0);

  IncrementTime(2 * kFrameSizeMs);
  // Insert timestamp that will wrap around.
  EXPECT_EQ(InsertPacketAndGetDelay(kFrameSizeMs), kFrameSizeMs);

  // Insert reordered packet before the wraparound.
  EXPECT_EQ(InsertPacketAndGetDelay(-2 * kFrameSizeMs), 3 * kFrameSizeMs);

  // Insert another in-order packet after the wraparound.
  EXPECT_EQ(InsertPacketAndGetDelay(kFrameSizeMs), 0);

  EXPECT_EQ(history_.GetMaxDelayMs(), kFrameSizeMs);
}

TEST_F(PacketArrivalHistoryTest, TimestampWraparoundBackwards) {
  timestamp_ = 0;
  EXPECT_EQ(InsertPacketAndGetDelay(0), 0);

  IncrementTime(2 * kFrameSizeMs);
  // Insert timestamp that will wrap around.
  EXPECT_EQ(InsertPacketAndGetDelay(kFrameSizeMs), kFrameSizeMs);

  // Insert reordered packet before the wraparound.
  EXPECT_EQ(InsertPacketAndGetDelay(-2 * kFrameSizeMs), 3 * kFrameSizeMs);

  // Insert another in-order packet after the wraparound.
  EXPECT_EQ(InsertPacketAndGetDelay(kFrameSizeMs), 0);

  EXPECT_EQ(history_.GetMaxDelayMs(), kFrameSizeMs);
}

TEST_F(PacketArrivalHistoryTest, OldPacketShouldNotBeInserted) {
  // Insert first packet as reference.
  EXPECT_EQ(InsertPacketAndGetDelay(0), 0);
  // Insert packet with timestamp older than the window size compared to the
  // first packet.
  EXPECT_FALSE(history_.Insert(timestamp_ - kWindowSizeMs * kFsKhz - 1,
                               kFrameSizeSamples));
}

TEST_F(PacketArrivalHistoryTest, DuplicatePacketShouldNotBeInserted) {
  // Insert first packet as reference.
  uint32_t first_timestamp = timestamp_;
  EXPECT_EQ(InsertPacketAndGetDelay(0), 0);
  EXPECT_EQ(InsertPacketAndGetDelay(kFrameSizeMs), 0);
  // Same timestamp as the first packet.
  EXPECT_FALSE(history_.Insert(first_timestamp, kFrameSizeSamples));
}

TEST_F(PacketArrivalHistoryTest, OverlappingPacketShouldNotBeInserted) {
  // Insert first packet as reference.
  EXPECT_EQ(InsertPacketAndGetDelay(0), 0);
  // 10 ms overlap with the previous packet.
  EXPECT_FALSE(history_.Insert(timestamp_ + kFrameSizeSamples / 2,
                               kFrameSizeSamples / 2));
}

}  // namespace
}  // namespace webrtc
