/*
 *  Copyright (c) 2016 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "modules/congestion_controller/goog_cc/delay_based_bwe.h"

#include <cstdint>

#include "api/network_state_predictor.h"
#include "api/transport/network_types.h"
#include "api/units/data_rate.h"
#include "api/units/time_delta.h"
#include "modules/congestion_controller/goog_cc/delay_based_bwe_unittest_helper.h"
#include "system_wrappers/include/clock.h"
#include "test/gtest.h"

namespace webrtc {

namespace {
constexpr int kNumProbesCluster0 = 5;
constexpr int kNumProbesCluster1 = 8;
const PacedPacketInfo kPacingInfo0(0, kNumProbesCluster0, 2000);
const PacedPacketInfo kPacingInfo1(1, kNumProbesCluster1, 4000);
constexpr float kTargetUtilizationFraction = 0.95f;
}  // namespace

TEST_F(DelayBasedBweTest, ProbeDetection) {
  int64_t now_ms = clock_.TimeInMilliseconds();

  // First burst sent at 8 * 1000 / 10 = 800 kbps.
  for (int i = 0; i < kNumProbesCluster0; ++i) {
    clock_.AdvanceTimeMilliseconds(10);
    now_ms = clock_.TimeInMilliseconds();
    IncomingFeedback(now_ms, now_ms, 1000, kPacingInfo0);
  }
  EXPECT_TRUE(bitrate_observer_.updated());

  // Second burst sent at 8 * 1000 / 5 = 1600 kbps.
  for (int i = 0; i < kNumProbesCluster1; ++i) {
    clock_.AdvanceTimeMilliseconds(5);
    now_ms = clock_.TimeInMilliseconds();
    IncomingFeedback(now_ms, now_ms, 1000, kPacingInfo1);
  }

  EXPECT_TRUE(bitrate_observer_.updated());
  EXPECT_GT(bitrate_observer_.latest_bitrate(), 1500000u);
}

TEST_F(DelayBasedBweTest, ProbeDetectionNonPacedPackets) {
  int64_t now_ms = clock_.TimeInMilliseconds();
  // First burst sent at 8 * 1000 / 10 = 800 kbps, but with every other packet
  // not being paced which could mess things up.
  for (int i = 0; i < kNumProbesCluster0; ++i) {
    clock_.AdvanceTimeMilliseconds(5);
    now_ms = clock_.TimeInMilliseconds();
    IncomingFeedback(now_ms, now_ms, 1000, kPacingInfo0);
    // Non-paced packet, arriving 5 ms after.
    clock_.AdvanceTimeMilliseconds(5);
    IncomingFeedback(now_ms, now_ms, 100, PacedPacketInfo());
  }

  EXPECT_TRUE(bitrate_observer_.updated());
  EXPECT_GT(bitrate_observer_.latest_bitrate(), 800000u);
}

TEST_F(DelayBasedBweTest, ProbeDetectionFasterArrival) {
  int64_t now_ms = clock_.TimeInMilliseconds();
  // First burst sent at 8 * 1000 / 10 = 800 kbps.
  // Arriving at 8 * 1000 / 5 = 1600 kbps.
  int64_t send_time_ms = 0;
  for (int i = 0; i < kNumProbesCluster0; ++i) {
    clock_.AdvanceTimeMilliseconds(1);
    send_time_ms += 10;
    now_ms = clock_.TimeInMilliseconds();
    IncomingFeedback(now_ms, send_time_ms, 1000, kPacingInfo0);
  }

  EXPECT_FALSE(bitrate_observer_.updated());
}

TEST_F(DelayBasedBweTest, ProbeDetectionSlowerArrival) {
  int64_t now_ms = clock_.TimeInMilliseconds();
  // First burst sent at 8 * 1000 / 5 = 1600 kbps.
  // Arriving at 8 * 1000 / 7 = 1142 kbps.
  // Since the receive rate is significantly below the send rate, we expect to
  // use 95% of the estimated capacity.
  int64_t send_time_ms = 0;
  for (int i = 0; i < kNumProbesCluster1; ++i) {
    clock_.AdvanceTimeMilliseconds(7);
    send_time_ms += 5;
    now_ms = clock_.TimeInMilliseconds();
    IncomingFeedback(now_ms, send_time_ms, 1000, kPacingInfo1);
  }

  EXPECT_TRUE(bitrate_observer_.updated());
  EXPECT_NEAR(bitrate_observer_.latest_bitrate(),
              kTargetUtilizationFraction * 1140000u, 10000u);
}

TEST_F(DelayBasedBweTest, ProbeDetectionSlowerArrivalHighBitrate) {
  int64_t now_ms = clock_.TimeInMilliseconds();
  // Burst sent at 8 * 1000 / 1 = 8000 kbps.
  // Arriving at 8 * 1000 / 2 = 4000 kbps.
  // Since the receive rate is significantly below the send rate, we expect to
  // use 95% of the estimated capacity.
  int64_t send_time_ms = 0;
  for (int i = 0; i < kNumProbesCluster1; ++i) {
    clock_.AdvanceTimeMilliseconds(2);
    send_time_ms += 1;
    now_ms = clock_.TimeInMilliseconds();
    IncomingFeedback(now_ms, send_time_ms, 1000, kPacingInfo1);
  }

  EXPECT_TRUE(bitrate_observer_.updated());
  EXPECT_NEAR(bitrate_observer_.latest_bitrate(),
              kTargetUtilizationFraction * 4000000u, 10000u);
}

TEST_F(DelayBasedBweTest, GetExpectedBwePeriodMs) {
  auto default_interval = bitrate_estimator_->GetExpectedBwePeriod();
  EXPECT_GT(default_interval.ms(), 0);
  CapacityDropTestHelper(1, true, 533, 0);
  auto interval = bitrate_estimator_->GetExpectedBwePeriod();
  EXPECT_GT(interval.ms(), 0);
  EXPECT_NE(interval.ms(), default_interval.ms());
}

TEST_F(DelayBasedBweTest, InitialBehavior) {
  InitialBehaviorTestHelper(730000);
}

TEST_F(DelayBasedBweTest, InitializeResult) {
  DelayBasedBwe::Result result;
  EXPECT_EQ(result.delay_detector_state, BandwidthUsage::kBwNormal);
}

TEST_F(DelayBasedBweTest, RateIncreaseReordering) {
  RateIncreaseReorderingTestHelper(730000);
}
TEST_F(DelayBasedBweTest, RateIncreaseRtpTimestamps) {
  RateIncreaseRtpTimestampsTestHelper(617);
}

TEST_F(DelayBasedBweTest, CapacityDropOneStream) {
  CapacityDropTestHelper(1, false, 500, 0);
}

TEST_F(DelayBasedBweTest, CapacityDropPosOffsetChange) {
  CapacityDropTestHelper(1, false, 867, 30000);
}

TEST_F(DelayBasedBweTest, CapacityDropNegOffsetChange) {
  CapacityDropTestHelper(1, false, 933, -30000);
}

TEST_F(DelayBasedBweTest, CapacityDropOneStreamWrap) {
  CapacityDropTestHelper(1, true, 533, 0);
}

TEST_F(DelayBasedBweTest, TestTimestampGrouping) {
  TestTimestampGroupingTestHelper();
}

TEST_F(DelayBasedBweTest, TestShortTimeoutAndWrap) {
  // Simulate a client leaving and rejoining the call after 35 seconds. This
  // will make abs send time wrap, so if streams aren't timed out properly
  // the next 30 seconds of packets will be out of order.
  TestWrappingHelper(35);
}

TEST_F(DelayBasedBweTest, TestLongTimeoutAndWrap) {
  // Simulate a client leaving and rejoining the call after some multiple of
  // 64 seconds later. This will cause a zero difference in abs send times due
  // to the wrap, but a big difference in arrival time, if streams aren't
  // properly timed out.
  TestWrappingHelper(10 * 64);
}

TEST_F(DelayBasedBweTest, TestInitialOveruse) {
  const DataRate kStartBitrate = DataRate::KilobitsPerSec(300);
  const DataRate kInitialCapacity = DataRate::KilobitsPerSec(200);
  const uint32_t kDummySsrc = 0;
  // High FPS to ensure that we send a lot of packets in a short time.
  const int kFps = 90;

  stream_generator_->AddStream(new test::RtpStream(kFps, kStartBitrate.bps()));
  stream_generator_->set_capacity_bps(kInitialCapacity.bps());

  // Needed to initialize the AimdRateControl.
  bitrate_estimator_->SetStartBitrate(kStartBitrate);

  // Produce 40 frames (in 1/3 second) and give them to the estimator.
  int64_t bitrate_bps = kStartBitrate.bps();
  bool seen_overuse = false;
  for (int i = 0; i < 40; ++i) {
    bool overuse = GenerateAndProcessFrame(kDummySsrc, bitrate_bps);
    if (overuse) {
      EXPECT_TRUE(bitrate_observer_.updated());
      EXPECT_LE(bitrate_observer_.latest_bitrate(), kInitialCapacity.bps());
      EXPECT_GT(bitrate_observer_.latest_bitrate(),
                0.8 * kInitialCapacity.bps());
      bitrate_bps = bitrate_observer_.latest_bitrate();
      seen_overuse = true;
      break;
    } else if (bitrate_observer_.updated()) {
      bitrate_bps = bitrate_observer_.latest_bitrate();
      bitrate_observer_.Reset();
    }
  }
  EXPECT_TRUE(seen_overuse);
  EXPECT_LE(bitrate_observer_.latest_bitrate(), kInitialCapacity.bps());
  EXPECT_GT(bitrate_observer_.latest_bitrate(), 0.8 * kInitialCapacity.bps());
}

TEST_F(DelayBasedBweTest, TestTimestampPrecisionHandling) {
  // This test does some basic checks to make sure that timestamps with higher
  // than millisecond precision are handled properly and do not cause any
  // problems in the estimator. Specifically, previously reported in
  // webrtc:14023 and described in more details there, the rounding to the
  // nearest milliseconds caused discrepancy in the accumulated delay. This lead
  // to false-positive overuse detection.
  // Technical details of the test:
  // Send times(ms): 0.000,  9.725, 20.000, 29.725, 40.000, 49.725, ...
  // Recv times(ms): 0.500, 10.000, 20.500, 30.000, 40.500, 50.000, ...
  // Send deltas(ms):   9.750,  10.250,  9.750, 10.250,  9.750, ...
  // Recv deltas(ms):   9.500,  10.500,  9.500, 10.500,  9.500, ...
  // There is no delay building up between the send times and the receive times,
  // therefore this case should never lead to an overuse detection. However, if
  // the time deltas were accidentally rounded to the nearest milliseconds, then
  // all the send deltas would be equal to 10ms while some recv deltas would
  // round up to 11ms which would lead in a false illusion of delay build up.
  uint32_t last_bitrate = bitrate_observer_.latest_bitrate();
  for (int i = 0; i < 1000; ++i) {
    clock_.AdvanceTimeMicroseconds(500);
    IncomingFeedback(clock_.CurrentTime(),
                     clock_.CurrentTime() - TimeDelta::Micros(500), 1000,
                     PacedPacketInfo());
    clock_.AdvanceTimeMicroseconds(9500);
    IncomingFeedback(clock_.CurrentTime(),
                     clock_.CurrentTime() - TimeDelta::Micros(250), 1000,
                     PacedPacketInfo());
    clock_.AdvanceTimeMicroseconds(10000);

    // The bitrate should never decrease in this test.
    EXPECT_LE(last_bitrate, bitrate_observer_.latest_bitrate());
    last_bitrate = bitrate_observer_.latest_bitrate();
  }
}

}  // namespace webrtc
