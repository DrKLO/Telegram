/*
 *  Copyright (c) 2016 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */
#include "modules/congestion_controller/goog_cc/delay_based_bwe_unittest_helper.h"

#include <algorithm>
#include <cstddef>
#include <cstdint>
#include <memory>
#include <vector>

#include "absl/types/optional.h"
#include "api/transport/network_types.h"
#include "api/units/data_rate.h"
#include "api/units/data_size.h"
#include "api/units/time_delta.h"
#include "api/units/timestamp.h"
#include "modules/congestion_controller/goog_cc/acknowledged_bitrate_estimator_interface.h"
#include "modules/congestion_controller/goog_cc/delay_based_bwe.h"
#include "modules/congestion_controller/goog_cc/probe_bitrate_estimator.h"
#include "rtc_base/checks.h"
#include "test/field_trial.h"
#include "test/gtest.h"

namespace webrtc {
constexpr size_t kMtu = 1200;
constexpr uint32_t kAcceptedBitrateErrorBps = 50000;

// Number of packets needed before we have a valid estimate.
constexpr int kNumInitialPackets = 2;

constexpr int kInitialProbingPackets = 5;

namespace test {

void TestBitrateObserver::OnReceiveBitrateChanged(uint32_t bitrate) {
  latest_bitrate_ = bitrate;
  updated_ = true;
}

RtpStream::RtpStream(int fps, int bitrate_bps)
    : fps_(fps), bitrate_bps_(bitrate_bps), next_rtp_time_(0) {
  RTC_CHECK_GT(fps_, 0);
}

// Generates a new frame for this stream. If called too soon after the
// previous frame, no frame will be generated. The frame is split into
// packets.
int64_t RtpStream::GenerateFrame(int64_t time_now_us,
                                 int64_t* next_sequence_number,
                                 std::vector<PacketResult>* packets) {
  if (time_now_us < next_rtp_time_) {
    return next_rtp_time_;
  }
  RTC_CHECK(packets != NULL);
  size_t bits_per_frame = (bitrate_bps_ + fps_ / 2) / fps_;
  size_t n_packets =
      std::max<size_t>((bits_per_frame + 4 * kMtu) / (8 * kMtu), 1u);
  size_t payload_size = (bits_per_frame + 4 * n_packets) / (8 * n_packets);
  for (size_t i = 0; i < n_packets; ++i) {
    PacketResult packet;
    packet.sent_packet.send_time =
        Timestamp::Micros(time_now_us + kSendSideOffsetUs);
    packet.sent_packet.size = DataSize::Bytes(payload_size);
    packet.sent_packet.sequence_number = (*next_sequence_number)++;
    packets->push_back(packet);
  }
  next_rtp_time_ = time_now_us + (1000000 + fps_ / 2) / fps_;
  return next_rtp_time_;
}

// The send-side time when the next frame can be generated.
int64_t RtpStream::next_rtp_time() const {
  return next_rtp_time_;
}

void RtpStream::set_bitrate_bps(int bitrate_bps) {
  ASSERT_GE(bitrate_bps, 0);
  bitrate_bps_ = bitrate_bps;
}

int RtpStream::bitrate_bps() const {
  return bitrate_bps_;
}

bool RtpStream::Compare(const std::unique_ptr<RtpStream>& lhs,
                        const std::unique_ptr<RtpStream>& rhs) {
  return lhs->next_rtp_time_ < rhs->next_rtp_time_;
}

StreamGenerator::StreamGenerator(int capacity, int64_t time_now)
    : capacity_(capacity), prev_arrival_time_us_(time_now) {}

StreamGenerator::~StreamGenerator() = default;

// Add a new stream.
void StreamGenerator::AddStream(RtpStream* stream) {
  streams_.push_back(std::unique_ptr<RtpStream>(stream));
}

// Set the link capacity.
void StreamGenerator::set_capacity_bps(int capacity_bps) {
  ASSERT_GT(capacity_bps, 0);
  capacity_ = capacity_bps;
}

// Divides `bitrate_bps` among all streams. The allocated bitrate per stream
// is decided by the current allocation ratios.
void StreamGenerator::SetBitrateBps(int bitrate_bps) {
  ASSERT_GE(streams_.size(), 0u);
  int total_bitrate_before = 0;
  for (const auto& stream : streams_) {
    total_bitrate_before += stream->bitrate_bps();
  }
  int64_t bitrate_before = 0;
  int total_bitrate_after = 0;
  for (const auto& stream : streams_) {
    bitrate_before += stream->bitrate_bps();
    int64_t bitrate_after =
        (bitrate_before * bitrate_bps + total_bitrate_before / 2) /
        total_bitrate_before;
    stream->set_bitrate_bps(bitrate_after - total_bitrate_after);
    total_bitrate_after += stream->bitrate_bps();
  }
  ASSERT_EQ(bitrate_before, total_bitrate_before);
  EXPECT_EQ(total_bitrate_after, bitrate_bps);
}

// TODO(holmer): Break out the channel simulation part from this class to make
// it possible to simulate different types of channels.
int64_t StreamGenerator::GenerateFrame(int64_t time_now_us,
                                       int64_t* next_sequence_number,
                                       std::vector<PacketResult>* packets) {
  RTC_CHECK(packets != NULL);
  RTC_CHECK(packets->empty());
  RTC_CHECK_GT(capacity_, 0);
  auto it =
      std::min_element(streams_.begin(), streams_.end(), RtpStream::Compare);
  (*it)->GenerateFrame(time_now_us, next_sequence_number, packets);
  for (PacketResult& packet : *packets) {
    int capacity_bpus = capacity_ / 1000;
    int64_t required_network_time_us =
        (8 * 1000 * packet.sent_packet.size.bytes() + capacity_bpus / 2) /
        capacity_bpus;
    prev_arrival_time_us_ =
        std::max(time_now_us + required_network_time_us,
                 prev_arrival_time_us_ + required_network_time_us);
    packet.receive_time = Timestamp::Micros(prev_arrival_time_us_);
  }
  it = std::min_element(streams_.begin(), streams_.end(), RtpStream::Compare);
  return std::max((*it)->next_rtp_time(), time_now_us);
}
}  // namespace test

DelayBasedBweTest::DelayBasedBweTest()
    : field_trial(std::make_unique<test::ScopedFieldTrials>(
          "WebRTC-Bwe-RobustThroughputEstimatorSettings/enabled:true/")),
      clock_(100000000),
      acknowledged_bitrate_estimator_(
          AcknowledgedBitrateEstimatorInterface::Create(&field_trial_config_)),
      probe_bitrate_estimator_(new ProbeBitrateEstimator(nullptr)),
      bitrate_estimator_(
          new DelayBasedBwe(&field_trial_config_, nullptr, nullptr)),
      stream_generator_(new test::StreamGenerator(1e6,  // Capacity.
                                                  clock_.TimeInMicroseconds())),
      arrival_time_offset_ms_(0),
      next_sequence_number_(0),
      first_update_(true) {}

DelayBasedBweTest::~DelayBasedBweTest() {}

void DelayBasedBweTest::AddDefaultStream() {
  stream_generator_->AddStream(new test::RtpStream(30, 3e5));
}

const uint32_t DelayBasedBweTest::kDefaultSsrc = 0;

void DelayBasedBweTest::IncomingFeedback(int64_t arrival_time_ms,
                                         int64_t send_time_ms,
                                         size_t payload_size) {
  IncomingFeedback(arrival_time_ms, send_time_ms, payload_size,
                   PacedPacketInfo());
}

void DelayBasedBweTest::IncomingFeedback(int64_t arrival_time_ms,
                                         int64_t send_time_ms,
                                         size_t payload_size,
                                         const PacedPacketInfo& pacing_info) {
  RTC_CHECK_GE(arrival_time_ms + arrival_time_offset_ms_, 0);
  IncomingFeedback(Timestamp::Millis(arrival_time_ms + arrival_time_offset_ms_),
                   Timestamp::Millis(send_time_ms), payload_size, pacing_info);
}

void DelayBasedBweTest::IncomingFeedback(Timestamp receive_time,
                                         Timestamp send_time,
                                         size_t payload_size,
                                         const PacedPacketInfo& pacing_info) {
  PacketResult packet;
  packet.receive_time = receive_time;
  packet.sent_packet.send_time = send_time;
  packet.sent_packet.size = DataSize::Bytes(payload_size);
  packet.sent_packet.pacing_info = pacing_info;
  packet.sent_packet.sequence_number = next_sequence_number_++;
  if (packet.sent_packet.pacing_info.probe_cluster_id !=
      PacedPacketInfo::kNotAProbe)
    probe_bitrate_estimator_->HandleProbeAndEstimateBitrate(packet);

  TransportPacketsFeedback msg;
  msg.feedback_time = Timestamp::Millis(clock_.TimeInMilliseconds());
  msg.packet_feedbacks.push_back(packet);
  acknowledged_bitrate_estimator_->IncomingPacketFeedbackVector(
      msg.SortedByReceiveTime());
  DelayBasedBwe::Result result =
      bitrate_estimator_->IncomingPacketFeedbackVector(
          msg, acknowledged_bitrate_estimator_->bitrate(),
          probe_bitrate_estimator_->FetchAndResetLastEstimatedBitrate(),
          /*network_estimate*/ absl::nullopt, /*in_alr*/ false);
  if (result.updated) {
    bitrate_observer_.OnReceiveBitrateChanged(result.target_bitrate.bps());
  }
}

// Generates a frame of packets belonging to a stream at a given bitrate and
// with a given ssrc. The stream is pushed through a very simple simulated
// network, and is then given to the receive-side bandwidth estimator.
// Returns true if an over-use was seen, false otherwise.
// The StreamGenerator::updated() should be used to check for any changes in
// target bitrate after the call to this function.
bool DelayBasedBweTest::GenerateAndProcessFrame(uint32_t ssrc,
                                                uint32_t bitrate_bps) {
  stream_generator_->SetBitrateBps(bitrate_bps);
  std::vector<PacketResult> packets;

  int64_t next_time_us = stream_generator_->GenerateFrame(
      clock_.TimeInMicroseconds(), &next_sequence_number_, &packets);
  if (packets.empty())
    return false;

  bool overuse = false;
  bitrate_observer_.Reset();
  clock_.AdvanceTimeMicroseconds(packets.back().receive_time.us() -
                                 clock_.TimeInMicroseconds());
  for (auto& packet : packets) {
    RTC_CHECK_GE(packet.receive_time.ms() + arrival_time_offset_ms_, 0);
    packet.receive_time += TimeDelta::Millis(arrival_time_offset_ms_);

    if (packet.sent_packet.pacing_info.probe_cluster_id !=
        PacedPacketInfo::kNotAProbe)
      probe_bitrate_estimator_->HandleProbeAndEstimateBitrate(packet);
  }

  acknowledged_bitrate_estimator_->IncomingPacketFeedbackVector(packets);
  TransportPacketsFeedback msg;
  msg.packet_feedbacks = packets;
  msg.feedback_time = Timestamp::Millis(clock_.TimeInMilliseconds());

  DelayBasedBwe::Result result =
      bitrate_estimator_->IncomingPacketFeedbackVector(
          msg, acknowledged_bitrate_estimator_->bitrate(),
          probe_bitrate_estimator_->FetchAndResetLastEstimatedBitrate(),
          /*network_estimate*/ absl::nullopt, /*in_alr*/ false);
  if (result.updated) {
    bitrate_observer_.OnReceiveBitrateChanged(result.target_bitrate.bps());
    if (!first_update_ && result.target_bitrate.bps() < bitrate_bps)
      overuse = true;
    first_update_ = false;
  }

  clock_.AdvanceTimeMicroseconds(next_time_us - clock_.TimeInMicroseconds());
  return overuse;
}

// Run the bandwidth estimator with a stream of `number_of_frames` frames, or
// until it reaches `target_bitrate`.
// Can for instance be used to run the estimator for some time to get it
// into a steady state.
uint32_t DelayBasedBweTest::SteadyStateRun(uint32_t ssrc,
                                           int max_number_of_frames,
                                           uint32_t start_bitrate,
                                           uint32_t min_bitrate,
                                           uint32_t max_bitrate,
                                           uint32_t target_bitrate) {
  uint32_t bitrate_bps = start_bitrate;
  bool bitrate_update_seen = false;
  // Produce `number_of_frames` frames and give them to the estimator.
  for (int i = 0; i < max_number_of_frames; ++i) {
    bool overuse = GenerateAndProcessFrame(ssrc, bitrate_bps);
    if (overuse) {
      EXPECT_LT(bitrate_observer_.latest_bitrate(), max_bitrate);
      EXPECT_GT(bitrate_observer_.latest_bitrate(), min_bitrate);
      bitrate_bps = bitrate_observer_.latest_bitrate();
      bitrate_update_seen = true;
    } else if (bitrate_observer_.updated()) {
      bitrate_bps = bitrate_observer_.latest_bitrate();
      bitrate_observer_.Reset();
    }
    if (bitrate_update_seen && bitrate_bps > target_bitrate) {
      break;
    }
  }
  EXPECT_TRUE(bitrate_update_seen);
  return bitrate_bps;
}

void DelayBasedBweTest::InitialBehaviorTestHelper(
    uint32_t expected_converge_bitrate) {
  const int kFramerate = 50;  // 50 fps to avoid rounding errors.
  const int kFrameIntervalMs = 1000 / kFramerate;
  const PacedPacketInfo kPacingInfo(0, 5, 5000);
  DataRate bitrate = DataRate::Zero();
  int64_t send_time_ms = 0;
  std::vector<uint32_t> ssrcs;
  EXPECT_FALSE(bitrate_estimator_->LatestEstimate(&ssrcs, &bitrate));
  EXPECT_EQ(0u, ssrcs.size());
  clock_.AdvanceTimeMilliseconds(1000);
  EXPECT_FALSE(bitrate_estimator_->LatestEstimate(&ssrcs, &bitrate));
  EXPECT_FALSE(bitrate_observer_.updated());
  bitrate_observer_.Reset();
  clock_.AdvanceTimeMilliseconds(1000);
  // Inserting packets for 5 seconds to get a valid estimate.
  for (int i = 0; i < 5 * kFramerate + 1 + kNumInitialPackets; ++i) {
    // NOTE!!! If the following line is moved under the if case then this test
    //         wont work on windows realease bots.
    PacedPacketInfo pacing_info =
        i < kInitialProbingPackets ? kPacingInfo : PacedPacketInfo();

    if (i == kNumInitialPackets) {
      EXPECT_FALSE(bitrate_estimator_->LatestEstimate(&ssrcs, &bitrate));
      EXPECT_EQ(0u, ssrcs.size());
      EXPECT_FALSE(bitrate_observer_.updated());
      bitrate_observer_.Reset();
    }
    IncomingFeedback(clock_.TimeInMilliseconds(), send_time_ms, kMtu,
                     pacing_info);
    clock_.AdvanceTimeMilliseconds(1000 / kFramerate);
    send_time_ms += kFrameIntervalMs;
  }
  EXPECT_TRUE(bitrate_estimator_->LatestEstimate(&ssrcs, &bitrate));
  ASSERT_EQ(1u, ssrcs.size());
  EXPECT_EQ(kDefaultSsrc, ssrcs.front());
  EXPECT_NEAR(expected_converge_bitrate, bitrate.bps(),
              kAcceptedBitrateErrorBps);
  EXPECT_TRUE(bitrate_observer_.updated());
  bitrate_observer_.Reset();
  EXPECT_EQ(bitrate_observer_.latest_bitrate(), bitrate.bps());
}

void DelayBasedBweTest::RateIncreaseReorderingTestHelper(
    uint32_t expected_bitrate_bps) {
  const int kFramerate = 50;  // 50 fps to avoid rounding errors.
  const int kFrameIntervalMs = 1000 / kFramerate;
  const PacedPacketInfo kPacingInfo(0, 5, 5000);
  int64_t send_time_ms = 0;
  // Inserting packets for five seconds to get a valid estimate.
  for (int i = 0; i < 5 * kFramerate + 1 + kNumInitialPackets; ++i) {
    // NOTE!!! If the following line is moved under the if case then this test
    //         wont work on windows realease bots.
    PacedPacketInfo pacing_info =
        i < kInitialProbingPackets ? kPacingInfo : PacedPacketInfo();

    // TODO(sprang): Remove this hack once the single stream estimator is gone,
    // as it doesn't do anything in Process().
    if (i == kNumInitialPackets) {
      // Process after we have enough frames to get a valid input rate estimate.

      EXPECT_FALSE(bitrate_observer_.updated());  // No valid estimate.
    }
    IncomingFeedback(clock_.TimeInMilliseconds(), send_time_ms, kMtu,
                     pacing_info);
    clock_.AdvanceTimeMilliseconds(kFrameIntervalMs);
    send_time_ms += kFrameIntervalMs;
  }
  EXPECT_TRUE(bitrate_observer_.updated());
  EXPECT_NEAR(expected_bitrate_bps, bitrate_observer_.latest_bitrate(),
              kAcceptedBitrateErrorBps);
  for (int i = 0; i < 10; ++i) {
    clock_.AdvanceTimeMilliseconds(2 * kFrameIntervalMs);
    send_time_ms += 2 * kFrameIntervalMs;
    IncomingFeedback(clock_.TimeInMilliseconds(), send_time_ms, 1000);
    IncomingFeedback(clock_.TimeInMilliseconds(),
                     send_time_ms - kFrameIntervalMs, 1000);
  }
  EXPECT_TRUE(bitrate_observer_.updated());
  EXPECT_NEAR(expected_bitrate_bps, bitrate_observer_.latest_bitrate(),
              kAcceptedBitrateErrorBps);
}

// Make sure we initially increase the bitrate as expected.
void DelayBasedBweTest::RateIncreaseRtpTimestampsTestHelper(
    int expected_iterations) {
  // This threshold corresponds approximately to increasing linearly with
  // bitrate(i) = 1.04 * bitrate(i-1) + 1000
  // until bitrate(i) > 500000, with bitrate(1) ~= 30000.
  uint32_t bitrate_bps = 30000;
  int iterations = 0;
  AddDefaultStream();
  // Feed the estimator with a stream of packets and verify that it reaches
  // 500 kbps at the expected time.
  while (bitrate_bps < 5e5) {
    bool overuse = GenerateAndProcessFrame(kDefaultSsrc, bitrate_bps);
    if (overuse) {
      EXPECT_GT(bitrate_observer_.latest_bitrate(), bitrate_bps);
      bitrate_bps = bitrate_observer_.latest_bitrate();
      bitrate_observer_.Reset();
    } else if (bitrate_observer_.updated()) {
      bitrate_bps = bitrate_observer_.latest_bitrate();
      bitrate_observer_.Reset();
    }
    ++iterations;
  }
  ASSERT_EQ(expected_iterations, iterations);
}

void DelayBasedBweTest::CapacityDropTestHelper(
    int number_of_streams,
    bool wrap_time_stamp,
    uint32_t expected_bitrate_drop_delta,
    int64_t receiver_clock_offset_change_ms) {
  const int kFramerate = 30;
  const int kStartBitrate = 900e3;
  const int kMinExpectedBitrate = 800e3;
  const int kMaxExpectedBitrate = 1100e3;
  const uint32_t kInitialCapacityBps = 1000e3;
  const uint32_t kReducedCapacityBps = 500e3;

  int steady_state_time = 0;
  if (number_of_streams <= 1) {
    steady_state_time = 10;
    AddDefaultStream();
  } else {
    steady_state_time = 10 * number_of_streams;
    int bitrate_sum = 0;
    int kBitrateDenom = number_of_streams * (number_of_streams - 1);
    for (int i = 0; i < number_of_streams; i++) {
      // First stream gets half available bitrate, while the rest share the
      // remaining half i.e.: 1/2 = Sum[n/(N*(N-1))] for n=1..N-1 (rounded up)
      int bitrate = kStartBitrate / 2;
      if (i > 0) {
        bitrate = (kStartBitrate * i + kBitrateDenom / 2) / kBitrateDenom;
      }
      stream_generator_->AddStream(new test::RtpStream(kFramerate, bitrate));
      bitrate_sum += bitrate;
    }
    ASSERT_EQ(bitrate_sum, kStartBitrate);
  }

  // Run in steady state to make the estimator converge.
  stream_generator_->set_capacity_bps(kInitialCapacityBps);
  uint32_t bitrate_bps = SteadyStateRun(
      kDefaultSsrc, steady_state_time * kFramerate, kStartBitrate,
      kMinExpectedBitrate, kMaxExpectedBitrate, kInitialCapacityBps);
  EXPECT_NEAR(kInitialCapacityBps, bitrate_bps, 180000u);
  bitrate_observer_.Reset();

  // Add an offset to make sure the BWE can handle it.
  arrival_time_offset_ms_ += receiver_clock_offset_change_ms;

  // Reduce the capacity and verify the decrease time.
  stream_generator_->set_capacity_bps(kReducedCapacityBps);
  int64_t overuse_start_time = clock_.TimeInMilliseconds();
  int64_t bitrate_drop_time = -1;
  for (int i = 0; i < 100 * number_of_streams; ++i) {
    GenerateAndProcessFrame(kDefaultSsrc, bitrate_bps);
    if (bitrate_drop_time == -1 &&
        bitrate_observer_.latest_bitrate() <= kReducedCapacityBps) {
      bitrate_drop_time = clock_.TimeInMilliseconds();
    }
    if (bitrate_observer_.updated())
      bitrate_bps = bitrate_observer_.latest_bitrate();
  }

  EXPECT_NEAR(expected_bitrate_drop_delta,
              bitrate_drop_time - overuse_start_time, 33);
}

void DelayBasedBweTest::TestTimestampGroupingTestHelper() {
  const int kFramerate = 50;  // 50 fps to avoid rounding errors.
  const int kFrameIntervalMs = 1000 / kFramerate;
  int64_t send_time_ms = 0;
  // Initial set of frames to increase the bitrate. 6 seconds to have enough
  // time for the first estimate to be generated and for Process() to be called.
  for (int i = 0; i <= 6 * kFramerate; ++i) {
    IncomingFeedback(clock_.TimeInMilliseconds(), send_time_ms, 1000);

    clock_.AdvanceTimeMilliseconds(kFrameIntervalMs);
    send_time_ms += kFrameIntervalMs;
  }
  EXPECT_TRUE(bitrate_observer_.updated());
  EXPECT_GE(bitrate_observer_.latest_bitrate(), 400000u);

  // Insert batches of frames which were sent very close in time. Also simulate
  // capacity over-use to see that we back off correctly.
  const int kTimestampGroupLength = 15;
  for (int i = 0; i < 100; ++i) {
    for (int j = 0; j < kTimestampGroupLength; ++j) {
      // Insert `kTimestampGroupLength` frames with just 1 timestamp ticks in
      // between. Should be treated as part of the same group by the estimator.
      IncomingFeedback(clock_.TimeInMilliseconds(), send_time_ms, 100);
      clock_.AdvanceTimeMilliseconds(kFrameIntervalMs / kTimestampGroupLength);
      send_time_ms += 1;
    }
    // Increase time until next batch to simulate over-use.
    clock_.AdvanceTimeMilliseconds(10);
    send_time_ms += kFrameIntervalMs - kTimestampGroupLength;
  }
  EXPECT_TRUE(bitrate_observer_.updated());
  // Should have reduced the estimate.
  EXPECT_LT(bitrate_observer_.latest_bitrate(), 400000u);
}

void DelayBasedBweTest::TestWrappingHelper(int silence_time_s) {
  const int kFramerate = 100;
  const int kFrameIntervalMs = 1000 / kFramerate;
  int64_t send_time_ms = 0;

  for (size_t i = 0; i < 3000; ++i) {
    IncomingFeedback(clock_.TimeInMilliseconds(), send_time_ms, 1000);
    clock_.AdvanceTimeMilliseconds(kFrameIntervalMs);
    send_time_ms += kFrameIntervalMs;
  }
  DataRate bitrate_before = DataRate::Zero();
  std::vector<uint32_t> ssrcs;
  bitrate_estimator_->LatestEstimate(&ssrcs, &bitrate_before);

  clock_.AdvanceTimeMilliseconds(silence_time_s * 1000);
  send_time_ms += silence_time_s * 1000;

  for (size_t i = 0; i < 24; ++i) {
    IncomingFeedback(clock_.TimeInMilliseconds(), send_time_ms, 1000);
    clock_.AdvanceTimeMilliseconds(2 * kFrameIntervalMs);
    send_time_ms += kFrameIntervalMs;
  }
  DataRate bitrate_after = DataRate::Zero();
  bitrate_estimator_->LatestEstimate(&ssrcs, &bitrate_after);
  EXPECT_LT(bitrate_after, bitrate_before);
}
}  // namespace webrtc
