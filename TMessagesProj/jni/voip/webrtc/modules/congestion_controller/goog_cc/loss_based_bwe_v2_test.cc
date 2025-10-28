/*
 *  Copyright 2021 The WebRTC project authors. All rights reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "modules/congestion_controller/goog_cc/loss_based_bwe_v2.h"

#include <string>
#include <vector>

#include "api/transport/network_types.h"
#include "api/units/data_rate.h"
#include "api/units/data_size.h"
#include "api/units/time_delta.h"
#include "api/units/timestamp.h"
#include "rtc_base/strings/string_builder.h"
#include "test/explicit_key_value_config.h"
#include "test/gtest.h"

namespace webrtc {

namespace {

using ::webrtc::test::ExplicitKeyValueConfig;

constexpr TimeDelta kObservationDurationLowerBound = TimeDelta::Millis(250);
constexpr TimeDelta kDelayedIncreaseWindow = TimeDelta::Millis(300);
constexpr double kMaxIncreaseFactor = 1.5;
constexpr int kPacketSize = 15'000;

class LossBasedBweV2Test : public ::testing::TestWithParam<bool> {
 protected:
  std::string Config(bool enabled, bool valid) {
    char buffer[1024];
    rtc::SimpleStringBuilder config_string(buffer);

    config_string << "WebRTC-Bwe-LossBasedBweV2/";

    if (enabled) {
      config_string << "Enabled:true";
    } else {
      config_string << "Enabled:false";
    }

    if (valid) {
      config_string << ",BwRampupUpperBoundFactor:1.2";
    } else {
      config_string << ",BwRampupUpperBoundFactor:0.0";
    }
    config_string
        << ",CandidateFactors:1.1|1.0|0.95,HigherBwBiasFactor:0.01,"
           "InherentLossLowerBound:0.001,InherentLossUpperBoundBwBalance:"
           "14kbps,"
           "InherentLossUpperBoundOffset:0.9,InitialInherentLossEstimate:0.01,"
           "NewtonIterations:2,NewtonStepSize:0.4,ObservationWindowSize:15,"
           "SendingRateSmoothingFactor:0.01,"
           "InstantUpperBoundTemporalWeightFactor:0.97,"
           "InstantUpperBoundBwBalance:90kbps,"
           "InstantUpperBoundLossOffset:0.1,TemporalWeightFactor:0.98,"
           "MinNumObservations:1";

    config_string.AppendFormat(
        ",ObservationDurationLowerBound:%dms",
        static_cast<int>(kObservationDurationLowerBound.ms()));
    config_string.AppendFormat(",MaxIncreaseFactor:%f", kMaxIncreaseFactor);
    config_string.AppendFormat(",DelayedIncreaseWindow:%dms",
                               static_cast<int>(kDelayedIncreaseWindow.ms()));

    config_string << "/";

    return config_string.str();
  }

  std::string ShortObservationConfig(std::string custom_config) {
    char buffer[1024];
    rtc::SimpleStringBuilder config_string(buffer);

    config_string << "WebRTC-Bwe-LossBasedBweV2/"
                     "MinNumObservations:1,ObservationWindowSize:2,";
    config_string << custom_config;
    config_string << "/";

    return config_string.str();
  }

  std::vector<PacketResult> CreatePacketResultsWithReceivedPackets(
      Timestamp first_packet_timestamp) {
    std::vector<PacketResult> enough_feedback(2);
    enough_feedback[0].sent_packet.size = DataSize::Bytes(kPacketSize);
    enough_feedback[1].sent_packet.size = DataSize::Bytes(kPacketSize);
    enough_feedback[0].sent_packet.send_time = first_packet_timestamp;
    enough_feedback[1].sent_packet.send_time =
        first_packet_timestamp + kObservationDurationLowerBound;
    enough_feedback[0].receive_time =
        first_packet_timestamp + kObservationDurationLowerBound;
    enough_feedback[1].receive_time =
        first_packet_timestamp + 2 * kObservationDurationLowerBound;
    return enough_feedback;
  }

  std::vector<PacketResult> CreatePacketResultsWith10pPacketLossRate(
      Timestamp first_packet_timestamp,
      DataSize lost_packet_size = DataSize::Bytes(kPacketSize)) {
    std::vector<PacketResult> enough_feedback(10);
    enough_feedback[0].sent_packet.size = DataSize::Bytes(kPacketSize);
    for (unsigned i = 0; i < enough_feedback.size(); ++i) {
      enough_feedback[i].sent_packet.size = DataSize::Bytes(kPacketSize);
      enough_feedback[i].sent_packet.send_time =
          first_packet_timestamp +
          static_cast<int>(i) * kObservationDurationLowerBound;
      enough_feedback[i].receive_time =
          first_packet_timestamp +
          static_cast<int>(i + 1) * kObservationDurationLowerBound;
    }
    enough_feedback[9].receive_time = Timestamp::PlusInfinity();
    enough_feedback[9].sent_packet.size = lost_packet_size;
    return enough_feedback;
  }

  std::vector<PacketResult> CreatePacketResultsWith50pPacketLossRate(
      Timestamp first_packet_timestamp) {
    std::vector<PacketResult> enough_feedback(2);
    enough_feedback[0].sent_packet.size = DataSize::Bytes(kPacketSize);
    enough_feedback[1].sent_packet.size = DataSize::Bytes(kPacketSize);
    enough_feedback[0].sent_packet.send_time = first_packet_timestamp;
    enough_feedback[1].sent_packet.send_time =
        first_packet_timestamp + kObservationDurationLowerBound;
    enough_feedback[0].receive_time =
        first_packet_timestamp + kObservationDurationLowerBound;
    enough_feedback[1].receive_time = Timestamp::PlusInfinity();
    return enough_feedback;
  }

  std::vector<PacketResult> CreatePacketResultsWith100pLossRate(
      Timestamp first_packet_timestamp) {
    std::vector<PacketResult> enough_feedback(2);
    enough_feedback[0].sent_packet.size = DataSize::Bytes(kPacketSize);
    enough_feedback[1].sent_packet.size = DataSize::Bytes(kPacketSize);
    enough_feedback[0].sent_packet.send_time = first_packet_timestamp;
    enough_feedback[1].sent_packet.send_time =
        first_packet_timestamp + kObservationDurationLowerBound;
    enough_feedback[0].receive_time = Timestamp::PlusInfinity();
    enough_feedback[1].receive_time = Timestamp::PlusInfinity();
    return enough_feedback;
  }
};

TEST_F(LossBasedBweV2Test, EnabledWhenGivenValidConfigurationValues) {
  ExplicitKeyValueConfig key_value_config(
      Config(/*enabled=*/true, /*valid=*/true));
  LossBasedBweV2 loss_based_bandwidth_estimator(&key_value_config);

  EXPECT_TRUE(loss_based_bandwidth_estimator.IsEnabled());
}

TEST_F(LossBasedBweV2Test, DisabledWhenGivenDisabledConfiguration) {
  ExplicitKeyValueConfig key_value_config(
      Config(/*enabled=*/false, /*valid=*/true));
  LossBasedBweV2 loss_based_bandwidth_estimator(&key_value_config);

  EXPECT_FALSE(loss_based_bandwidth_estimator.IsEnabled());
}

TEST_F(LossBasedBweV2Test, DisabledWhenGivenNonValidConfigurationValues) {
  ExplicitKeyValueConfig key_value_config(
      Config(/*enabled=*/true, /*valid=*/false));
  LossBasedBweV2 loss_based_bandwidth_estimator(&key_value_config);

  EXPECT_FALSE(loss_based_bandwidth_estimator.IsEnabled());
}

TEST_F(LossBasedBweV2Test, DisabledWhenGivenNonPositiveCandidateFactor) {
  ExplicitKeyValueConfig key_value_config_negative_candidate_factor(
      "WebRTC-Bwe-LossBasedBweV2/CandidateFactors:-1.3|1.1/");
  LossBasedBweV2 loss_based_bandwidth_estimator_1(
      &key_value_config_negative_candidate_factor);
  EXPECT_FALSE(loss_based_bandwidth_estimator_1.IsEnabled());

  ExplicitKeyValueConfig key_value_config_zero_candidate_factor(
      "WebRTC-Bwe-LossBasedBweV2/CandidateFactors:0.0|1.1/");
  LossBasedBweV2 loss_based_bandwidth_estimator_2(
      &key_value_config_zero_candidate_factor);
  EXPECT_FALSE(loss_based_bandwidth_estimator_2.IsEnabled());
}

TEST_F(LossBasedBweV2Test,
       DisabledWhenGivenConfigurationThatDoesNotAllowGeneratingCandidates) {
  ExplicitKeyValueConfig key_value_config(
      "WebRTC-Bwe-LossBasedBweV2/"
      "CandidateFactors:1.0,AckedRateCandidate:false,"
      "DelayBasedCandidate:false/");
  LossBasedBweV2 loss_based_bandwidth_estimator(&key_value_config);
  EXPECT_FALSE(loss_based_bandwidth_estimator.IsEnabled());
}

TEST_F(LossBasedBweV2Test, ReturnsDelayBasedEstimateWhenDisabled) {
  ExplicitKeyValueConfig key_value_config(
      Config(/*enabled=*/false, /*valid=*/true));
  LossBasedBweV2 loss_based_bandwidth_estimator(&key_value_config);
  loss_based_bandwidth_estimator.UpdateBandwidthEstimate(
      /*packet_results=*/{},
      /*delay_based_estimate=*/DataRate::KilobitsPerSec(100),

      /*in_alr=*/false);
  EXPECT_EQ(
      loss_based_bandwidth_estimator.GetLossBasedResult().bandwidth_estimate,
      DataRate::KilobitsPerSec(100));
}

TEST_F(LossBasedBweV2Test,
       ReturnsDelayBasedEstimateWhenWhenGivenNonValidConfigurationValues) {
  ExplicitKeyValueConfig key_value_config(
      Config(/*enabled=*/true, /*valid=*/false));
  LossBasedBweV2 loss_based_bandwidth_estimator(&key_value_config);
  loss_based_bandwidth_estimator.UpdateBandwidthEstimate(
      /*packet_results=*/{},
      /*delay_based_estimate=*/DataRate::KilobitsPerSec(100),

      /*in_alr=*/false);
  EXPECT_EQ(
      loss_based_bandwidth_estimator.GetLossBasedResult().bandwidth_estimate,
      DataRate::KilobitsPerSec(100));
}

TEST_F(LossBasedBweV2Test,
       BandwidthEstimateGivenInitializationAndThenFeedback) {
  std::vector<PacketResult> enough_feedback =
      CreatePacketResultsWithReceivedPackets(
          /*first_packet_timestamp=*/Timestamp::Zero());

  ExplicitKeyValueConfig key_value_config(
      Config(/*enabled=*/true, /*valid=*/true));
  LossBasedBweV2 loss_based_bandwidth_estimator(&key_value_config);

  loss_based_bandwidth_estimator.SetBandwidthEstimate(
      DataRate::KilobitsPerSec(600));
  loss_based_bandwidth_estimator.UpdateBandwidthEstimate(
      enough_feedback, /*delay_based_estimate=*/DataRate::PlusInfinity(),

      /*in_alr=*/false);

  EXPECT_TRUE(loss_based_bandwidth_estimator.IsReady());
  EXPECT_TRUE(loss_based_bandwidth_estimator.GetLossBasedResult()
                  .bandwidth_estimate.IsFinite());
}

TEST_F(LossBasedBweV2Test, NoBandwidthEstimateGivenNoInitialization) {
  std::vector<PacketResult> enough_feedback =
      CreatePacketResultsWithReceivedPackets(
          /*first_packet_timestamp=*/Timestamp::Zero());
  ExplicitKeyValueConfig key_value_config(
      Config(/*enabled=*/true, /*valid=*/true));
  LossBasedBweV2 loss_based_bandwidth_estimator(&key_value_config);

  loss_based_bandwidth_estimator.UpdateBandwidthEstimate(
      enough_feedback, /*delay_based_estimate=*/DataRate::PlusInfinity(),

      /*in_alr=*/false);

  EXPECT_FALSE(loss_based_bandwidth_estimator.IsReady());
  EXPECT_TRUE(loss_based_bandwidth_estimator.GetLossBasedResult()
                  .bandwidth_estimate.IsPlusInfinity());
}

TEST_F(LossBasedBweV2Test, NoBandwidthEstimateGivenNotEnoughFeedback) {
  // Create packet results where the observation duration is less than the lower
  // bound.
  PacketResult not_enough_feedback[2];
  not_enough_feedback[0].sent_packet.size = DataSize::Bytes(15'000);
  not_enough_feedback[1].sent_packet.size = DataSize::Bytes(15'000);
  not_enough_feedback[0].sent_packet.send_time = Timestamp::Zero();
  not_enough_feedback[1].sent_packet.send_time =
      Timestamp::Zero() + kObservationDurationLowerBound / 2;
  not_enough_feedback[0].receive_time =
      Timestamp::Zero() + kObservationDurationLowerBound / 2;
  not_enough_feedback[1].receive_time =
      Timestamp::Zero() + kObservationDurationLowerBound;

  ExplicitKeyValueConfig key_value_config(
      Config(/*enabled=*/true, /*valid=*/true));
  LossBasedBweV2 loss_based_bandwidth_estimator(&key_value_config);

  loss_based_bandwidth_estimator.SetBandwidthEstimate(
      DataRate::KilobitsPerSec(600));

  EXPECT_FALSE(loss_based_bandwidth_estimator.IsReady());
  EXPECT_TRUE(loss_based_bandwidth_estimator.GetLossBasedResult()
                  .bandwidth_estimate.IsPlusInfinity());

  loss_based_bandwidth_estimator.UpdateBandwidthEstimate(
      not_enough_feedback, /*delay_based_estimate=*/DataRate::PlusInfinity(),

      /*in_alr=*/false);

  EXPECT_FALSE(loss_based_bandwidth_estimator.IsReady());
  EXPECT_TRUE(loss_based_bandwidth_estimator.GetLossBasedResult()
                  .bandwidth_estimate.IsPlusInfinity());
}

TEST_F(LossBasedBweV2Test,
       SetValueIsTheEstimateUntilAdditionalFeedbackHasBeenReceived) {
  std::vector<PacketResult> enough_feedback_1 =
      CreatePacketResultsWithReceivedPackets(
          /*first_packet_timestamp=*/Timestamp::Zero());
  std::vector<PacketResult> enough_feedback_2 =
      CreatePacketResultsWithReceivedPackets(
          /*first_packet_timestamp=*/Timestamp::Zero() +
          2 * kObservationDurationLowerBound);

  ExplicitKeyValueConfig key_value_config(
      Config(/*enabled=*/true, /*valid=*/true));
  LossBasedBweV2 loss_based_bandwidth_estimator(&key_value_config);

  loss_based_bandwidth_estimator.SetBandwidthEstimate(
      DataRate::KilobitsPerSec(600));
  loss_based_bandwidth_estimator.UpdateBandwidthEstimate(
      enough_feedback_1, /*delay_based_estimate=*/DataRate::PlusInfinity(),

      /*in_alr=*/false);

  EXPECT_NE(
      loss_based_bandwidth_estimator.GetLossBasedResult().bandwidth_estimate,
      DataRate::KilobitsPerSec(600));

  loss_based_bandwidth_estimator.SetBandwidthEstimate(
      DataRate::KilobitsPerSec(600));

  EXPECT_EQ(
      loss_based_bandwidth_estimator.GetLossBasedResult().bandwidth_estimate,
      DataRate::KilobitsPerSec(600));

  loss_based_bandwidth_estimator.UpdateBandwidthEstimate(
      enough_feedback_2, /*delay_based_estimate=*/DataRate::PlusInfinity(),

      /*in_alr=*/false);

  EXPECT_NE(
      loss_based_bandwidth_estimator.GetLossBasedResult().bandwidth_estimate,
      DataRate::KilobitsPerSec(600));
}

TEST_F(LossBasedBweV2Test,
       SetAcknowledgedBitrateOnlyAffectsTheBweWhenAdditionalFeedbackIsGiven) {
  std::vector<PacketResult> enough_feedback_1 =
      CreatePacketResultsWithReceivedPackets(
          /*first_packet_timestamp=*/Timestamp::Zero());
  std::vector<PacketResult> enough_feedback_2 =
      CreatePacketResultsWithReceivedPackets(
          /*first_packet_timestamp=*/Timestamp::Zero() +
          2 * kObservationDurationLowerBound);

  ExplicitKeyValueConfig key_value_config(
      Config(/*enabled=*/true, /*valid=*/true));
  LossBasedBweV2 loss_based_bandwidth_estimator_1(&key_value_config);
  LossBasedBweV2 loss_based_bandwidth_estimator_2(&key_value_config);

  loss_based_bandwidth_estimator_1.SetBandwidthEstimate(
      DataRate::KilobitsPerSec(600));
  loss_based_bandwidth_estimator_2.SetBandwidthEstimate(
      DataRate::KilobitsPerSec(600));
  loss_based_bandwidth_estimator_1.UpdateBandwidthEstimate(
      enough_feedback_1, /*delay_based_estimate=*/DataRate::PlusInfinity(),

      /*in_alr=*/false);
  loss_based_bandwidth_estimator_2.UpdateBandwidthEstimate(
      enough_feedback_1, /*delay_based_estimate=*/DataRate::PlusInfinity(),

      /*in_alr=*/false);

  EXPECT_EQ(
      loss_based_bandwidth_estimator_1.GetLossBasedResult().bandwidth_estimate,
      DataRate::KilobitsPerSec(660));

  loss_based_bandwidth_estimator_1.SetAcknowledgedBitrate(
      DataRate::KilobitsPerSec(900));

  EXPECT_EQ(
      loss_based_bandwidth_estimator_1.GetLossBasedResult().bandwidth_estimate,
      DataRate::KilobitsPerSec(660));

  loss_based_bandwidth_estimator_1.UpdateBandwidthEstimate(
      enough_feedback_2, /*delay_based_estimate=*/DataRate::PlusInfinity(),

      /*in_alr=*/false);
  loss_based_bandwidth_estimator_2.UpdateBandwidthEstimate(
      enough_feedback_2, /*delay_based_estimate=*/DataRate::PlusInfinity(),

      /*in_alr=*/false);

  EXPECT_NE(
      loss_based_bandwidth_estimator_1.GetLossBasedResult().bandwidth_estimate,
      loss_based_bandwidth_estimator_2.GetLossBasedResult().bandwidth_estimate);
}

TEST_F(LossBasedBweV2Test,
       BandwidthEstimateIsCappedToBeTcpFairGivenTooHighLossRate) {
  std::vector<PacketResult> enough_feedback_no_received_packets =
      CreatePacketResultsWith100pLossRate(
          /*first_packet_timestamp=*/Timestamp::Zero());

  ExplicitKeyValueConfig key_value_config(
      Config(/*enabled=*/true, /*valid=*/true));
  LossBasedBweV2 loss_based_bandwidth_estimator(&key_value_config);

  loss_based_bandwidth_estimator.SetBandwidthEstimate(
      DataRate::KilobitsPerSec(600));
  loss_based_bandwidth_estimator.UpdateBandwidthEstimate(
      enough_feedback_no_received_packets,
      /*delay_based_estimate=*/DataRate::PlusInfinity(),
      /*in_alr=*/false);

  EXPECT_EQ(
      loss_based_bandwidth_estimator.GetLossBasedResult().bandwidth_estimate,
      DataRate::KilobitsPerSec(100));
}

// When network is normal, estimate can increase but never be higher than
// the delay based estimate.
TEST_F(LossBasedBweV2Test,
       BandwidthEstimateCappedByDelayBasedEstimateWhenNetworkNormal) {
  // Create two packet results, network is in normal state, 100% packets are
  // received, and no delay increase.
  std::vector<PacketResult> enough_feedback_1 =
      CreatePacketResultsWithReceivedPackets(
          /*first_packet_timestamp=*/Timestamp::Zero());
  std::vector<PacketResult> enough_feedback_2 =
      CreatePacketResultsWithReceivedPackets(
          /*first_packet_timestamp=*/Timestamp::Zero() +
          2 * kObservationDurationLowerBound);
  ExplicitKeyValueConfig key_value_config(
      Config(/*enabled=*/true, /*valid=*/true));
  LossBasedBweV2 loss_based_bandwidth_estimator(&key_value_config);

  loss_based_bandwidth_estimator.SetBandwidthEstimate(
      DataRate::KilobitsPerSec(600));
  loss_based_bandwidth_estimator.UpdateBandwidthEstimate(
      enough_feedback_1, /*delay_based_estimate=*/DataRate::PlusInfinity(),

      /*in_alr=*/false);
  // If the delay based estimate is infinity, then loss based estimate increases
  // and not bounded by delay based estimate.
  EXPECT_GT(
      loss_based_bandwidth_estimator.GetLossBasedResult().bandwidth_estimate,
      DataRate::KilobitsPerSec(600));
  loss_based_bandwidth_estimator.UpdateBandwidthEstimate(
      enough_feedback_2, /*delay_based_estimate=*/DataRate::KilobitsPerSec(500),

      /*in_alr=*/false);
  // If the delay based estimate is not infinity, then loss based estimate is
  // bounded by delay based estimate.
  EXPECT_EQ(
      loss_based_bandwidth_estimator.GetLossBasedResult().bandwidth_estimate,
      DataRate::KilobitsPerSec(500));
}

// When loss based bwe receives a strong signal of overusing and an increase in
// loss rate, it should acked bitrate for emegency backoff.
TEST_F(LossBasedBweV2Test, UseAckedBitrateForEmegencyBackOff) {
  // Create two packet results, first packet has 50% loss rate, second packet
  // has 100% loss rate.
  std::vector<PacketResult> enough_feedback_1 =
      CreatePacketResultsWith50pPacketLossRate(
          /*first_packet_timestamp=*/Timestamp::Zero());
  std::vector<PacketResult> enough_feedback_2 =
      CreatePacketResultsWith100pLossRate(
          /*first_packet_timestamp=*/Timestamp::Zero() +
          2 * kObservationDurationLowerBound);

  ExplicitKeyValueConfig key_value_config(
      Config(/*enabled=*/true, /*valid=*/true));
  LossBasedBweV2 loss_based_bandwidth_estimator(&key_value_config);

  loss_based_bandwidth_estimator.SetBandwidthEstimate(
      DataRate::KilobitsPerSec(600));
  DataRate acked_bitrate = DataRate::KilobitsPerSec(300);
  loss_based_bandwidth_estimator.SetAcknowledgedBitrate(acked_bitrate);
  // Update estimate when network is overusing, and 50% loss rate.
  loss_based_bandwidth_estimator.UpdateBandwidthEstimate(
      enough_feedback_1, /*delay_based_estimate=*/DataRate::PlusInfinity(),
      /*in_alr=*/false);
  // Update estimate again when network is continuously overusing, and 100%
  // loss rate.
  loss_based_bandwidth_estimator.UpdateBandwidthEstimate(
      enough_feedback_2, /*delay_based_estimate=*/DataRate::PlusInfinity(),
      /*in_alr=*/false);
  // The estimate bitrate now is backed off based on acked bitrate.
  EXPECT_LE(
      loss_based_bandwidth_estimator.GetLossBasedResult().bandwidth_estimate,
      acked_bitrate);
}

// When receiving the same packet feedback, loss based bwe ignores the feedback
// and returns the current estimate.
TEST_F(LossBasedBweV2Test, NoBweChangeIfObservationDurationUnchanged) {
  std::vector<PacketResult> enough_feedback_1 =
      CreatePacketResultsWithReceivedPackets(
          /*first_packet_timestamp=*/Timestamp::Zero());
  ExplicitKeyValueConfig key_value_config(
      Config(/*enabled=*/true, /*valid=*/true));
  LossBasedBweV2 loss_based_bandwidth_estimator(&key_value_config);
  loss_based_bandwidth_estimator.SetBandwidthEstimate(
      DataRate::KilobitsPerSec(600));
  loss_based_bandwidth_estimator.SetAcknowledgedBitrate(
      DataRate::KilobitsPerSec(300));

  loss_based_bandwidth_estimator.UpdateBandwidthEstimate(
      enough_feedback_1, /*delay_based_estimate=*/DataRate::PlusInfinity(),

      /*in_alr=*/false);
  DataRate estimate_1 =
      loss_based_bandwidth_estimator.GetLossBasedResult().bandwidth_estimate;

  // Use the same feedback and check if the estimate is unchanged.
  loss_based_bandwidth_estimator.UpdateBandwidthEstimate(
      enough_feedback_1, /*delay_based_estimate=*/DataRate::PlusInfinity(),

      /*in_alr=*/false);
  DataRate estimate_2 =
      loss_based_bandwidth_estimator.GetLossBasedResult().bandwidth_estimate;
  EXPECT_EQ(estimate_2, estimate_1);
}

// When receiving feedback of packets that were sent within an observation
// duration, and network is in the normal state, loss based bwe returns the
// current estimate.
TEST_F(LossBasedBweV2Test,
       NoBweChangeIfObservationDurationIsSmallAndNetworkNormal) {
  std::vector<PacketResult> enough_feedback_1 =
      CreatePacketResultsWithReceivedPackets(
          /*first_packet_timestamp=*/Timestamp::Zero());
  std::vector<PacketResult> enough_feedback_2 =
      CreatePacketResultsWithReceivedPackets(
          /*first_packet_timestamp=*/Timestamp::Zero() +
          kObservationDurationLowerBound - TimeDelta::Millis(1));
  ExplicitKeyValueConfig key_value_config(
      Config(/*enabled=*/true, /*valid=*/true));
  LossBasedBweV2 loss_based_bandwidth_estimator(&key_value_config);
  loss_based_bandwidth_estimator.SetBandwidthEstimate(
      DataRate::KilobitsPerSec(600));
  loss_based_bandwidth_estimator.UpdateBandwidthEstimate(
      enough_feedback_1, /*delay_based_estimate=*/DataRate::PlusInfinity(),

      /*in_alr=*/false);
  DataRate estimate_1 =
      loss_based_bandwidth_estimator.GetLossBasedResult().bandwidth_estimate;

  loss_based_bandwidth_estimator.UpdateBandwidthEstimate(
      enough_feedback_2, /*delay_based_estimate=*/DataRate::PlusInfinity(),

      /*in_alr=*/false);
  DataRate estimate_2 =
      loss_based_bandwidth_estimator.GetLossBasedResult().bandwidth_estimate;
  EXPECT_EQ(estimate_2, estimate_1);
}

// When receiving feedback of packets that were sent within an observation
// duration, and network is in the underusing state, loss based bwe returns the
// current estimate.
TEST_F(LossBasedBweV2Test,
       NoBweIncreaseIfObservationDurationIsSmallAndNetworkUnderusing) {
  std::vector<PacketResult> enough_feedback_1 =
      CreatePacketResultsWithReceivedPackets(
          /*first_packet_timestamp=*/Timestamp::Zero());
  std::vector<PacketResult> enough_feedback_2 =
      CreatePacketResultsWithReceivedPackets(
          /*first_packet_timestamp=*/Timestamp::Zero() +
          kObservationDurationLowerBound - TimeDelta::Millis(1));
  ExplicitKeyValueConfig key_value_config(
      Config(/*enabled=*/true, /*valid=*/true));
  LossBasedBweV2 loss_based_bandwidth_estimator(&key_value_config);
  loss_based_bandwidth_estimator.SetBandwidthEstimate(
      DataRate::KilobitsPerSec(600));
  loss_based_bandwidth_estimator.UpdateBandwidthEstimate(
      enough_feedback_1, /*delay_based_estimate=*/DataRate::PlusInfinity(),

      /*in_alr=*/false);
  DataRate estimate_1 =
      loss_based_bandwidth_estimator.GetLossBasedResult().bandwidth_estimate;

  loss_based_bandwidth_estimator.UpdateBandwidthEstimate(
      enough_feedback_2, /*delay_based_estimate=*/DataRate::PlusInfinity(),
      /*in_alr=*/false);
  DataRate estimate_2 =
      loss_based_bandwidth_estimator.GetLossBasedResult().bandwidth_estimate;
  EXPECT_LE(estimate_2, estimate_1);
}

TEST_F(LossBasedBweV2Test,
       IncreaseToDelayBasedEstimateIfNoLossOrDelayIncrease) {
  std::vector<PacketResult> enough_feedback_1 =
      CreatePacketResultsWithReceivedPackets(
          /*first_packet_timestamp=*/Timestamp::Zero());
  std::vector<PacketResult> enough_feedback_2 =
      CreatePacketResultsWithReceivedPackets(
          /*first_packet_timestamp=*/Timestamp::Zero() +
          2 * kObservationDurationLowerBound);
  ExplicitKeyValueConfig key_value_config(
      Config(/*enabled=*/true, /*valid=*/true));
  LossBasedBweV2 loss_based_bandwidth_estimator(&key_value_config);
  DataRate delay_based_estimate = DataRate::KilobitsPerSec(5000);
  loss_based_bandwidth_estimator.SetBandwidthEstimate(
      DataRate::KilobitsPerSec(600));

  loss_based_bandwidth_estimator.UpdateBandwidthEstimate(enough_feedback_1,
                                                         delay_based_estimate,
                                                         /*in_alr=*/false);
  EXPECT_EQ(
      loss_based_bandwidth_estimator.GetLossBasedResult().bandwidth_estimate,
      delay_based_estimate);
  loss_based_bandwidth_estimator.UpdateBandwidthEstimate(enough_feedback_2,
                                                         delay_based_estimate,
                                                         /*in_alr=*/false);
  EXPECT_EQ(
      loss_based_bandwidth_estimator.GetLossBasedResult().bandwidth_estimate,
      delay_based_estimate);
}

TEST_F(LossBasedBweV2Test,
       IncreaseByMaxIncreaseFactorAfterLossBasedBweBacksOff) {
  ExplicitKeyValueConfig key_value_config(ShortObservationConfig(
      "CandidateFactors:1.2|1|0.5,"
      "InstantUpperBoundBwBalance:10000kbps,"
      "MaxIncreaseFactor:1.5,NotIncreaseIfInherentLossLessThanAverageLoss:"
      "false"));

  LossBasedBweV2 loss_based_bandwidth_estimator(&key_value_config);
  DataRate delay_based_estimate = DataRate::KilobitsPerSec(5000);
  DataRate acked_rate = DataRate::KilobitsPerSec(300);
  loss_based_bandwidth_estimator.SetBandwidthEstimate(
      DataRate::KilobitsPerSec(600));
  loss_based_bandwidth_estimator.SetAcknowledgedBitrate(acked_rate);

  // Create some loss to create the loss limited scenario.
  std::vector<PacketResult> enough_feedback_1 =
      CreatePacketResultsWith100pLossRate(
          /*first_packet_timestamp=*/Timestamp::Zero());
  loss_based_bandwidth_estimator.UpdateBandwidthEstimate(enough_feedback_1,
                                                         delay_based_estimate,
                                                         /*in_alr=*/false);
  LossBasedBweV2::Result result_at_loss =
      loss_based_bandwidth_estimator.GetLossBasedResult();

  // Network recovers after loss.
  std::vector<PacketResult> enough_feedback_2 =
      CreatePacketResultsWithReceivedPackets(
          /*first_packet_timestamp=*/Timestamp::Zero() +
          kObservationDurationLowerBound);
  loss_based_bandwidth_estimator.SetAcknowledgedBitrate(
      DataRate::KilobitsPerSec(600));
  loss_based_bandwidth_estimator.UpdateBandwidthEstimate(enough_feedback_2,
                                                         delay_based_estimate,
                                                         /*in_alr=*/false);

  LossBasedBweV2::Result result_after_recovery =
      loss_based_bandwidth_estimator.GetLossBasedResult();
  EXPECT_EQ(result_after_recovery.bandwidth_estimate,
            result_at_loss.bandwidth_estimate * 1.5);
}

TEST_F(LossBasedBweV2Test,
       LossBasedStateIsDelayBasedEstimateAfterNetworkRecovering) {
  ExplicitKeyValueConfig key_value_config(ShortObservationConfig(
      "CandidateFactors:100|1|0.5,"
      "InstantUpperBoundBwBalance:10000kbps,"
      "MaxIncreaseFactor:100,"
      "NotIncreaseIfInherentLossLessThanAverageLoss:false"));
  LossBasedBweV2 loss_based_bandwidth_estimator(&key_value_config);
  DataRate delay_based_estimate = DataRate::KilobitsPerSec(600);
  DataRate acked_rate = DataRate::KilobitsPerSec(300);
  loss_based_bandwidth_estimator.SetBandwidthEstimate(
      DataRate::KilobitsPerSec(600));
  loss_based_bandwidth_estimator.SetAcknowledgedBitrate(acked_rate);

  // Create some loss to create the loss limited scenario.
  std::vector<PacketResult> enough_feedback_1 =
      CreatePacketResultsWith100pLossRate(
          /*first_packet_timestamp=*/Timestamp::Zero());
  loss_based_bandwidth_estimator.UpdateBandwidthEstimate(enough_feedback_1,
                                                         delay_based_estimate,
                                                         /*in_alr=*/false);
  ASSERT_EQ(loss_based_bandwidth_estimator.GetLossBasedResult().state,
            LossBasedState::kDecreasing);

  // Network recovers after loss.
  std::vector<PacketResult> enough_feedback_2 =
      CreatePacketResultsWithReceivedPackets(
          /*first_packet_timestamp=*/Timestamp::Zero() +
          kObservationDurationLowerBound);
  loss_based_bandwidth_estimator.SetAcknowledgedBitrate(
      DataRate::KilobitsPerSec(600));
  loss_based_bandwidth_estimator.UpdateBandwidthEstimate(enough_feedback_2,
                                                         delay_based_estimate,
                                                         /*in_alr=*/false);
  EXPECT_EQ(loss_based_bandwidth_estimator.GetLossBasedResult().state,
            LossBasedState::kDelayBasedEstimate);

  // Network recovers continuing.
  std::vector<PacketResult> enough_feedback_3 =
      CreatePacketResultsWithReceivedPackets(
          /*first_packet_timestamp=*/Timestamp::Zero() +
          kObservationDurationLowerBound * 2);
  loss_based_bandwidth_estimator.SetAcknowledgedBitrate(
      DataRate::KilobitsPerSec(600));
  loss_based_bandwidth_estimator.UpdateBandwidthEstimate(enough_feedback_3,
                                                         delay_based_estimate,
                                                         /*in_alr=*/false);
  EXPECT_EQ(loss_based_bandwidth_estimator.GetLossBasedResult().state,
            LossBasedState::kDelayBasedEstimate);
}

TEST_F(LossBasedBweV2Test,
       LossBasedStateIsNotDelayBasedEstimateIfDelayBasedEstimateInfinite) {
  ExplicitKeyValueConfig key_value_config(
      ShortObservationConfig("CandidateFactors:100|1|0.5,"
                             "InstantUpperBoundBwBalance:10000kbps,"
                             "MaxIncreaseFactor:100"));
  LossBasedBweV2 loss_based_bandwidth_estimator(&key_value_config);
  loss_based_bandwidth_estimator.SetBandwidthEstimate(
      DataRate::KilobitsPerSec(600));

  // Create some loss to create the loss limited scenario.
  std::vector<PacketResult> enough_feedback_1 =
      CreatePacketResultsWith100pLossRate(
          /*first_packet_timestamp=*/Timestamp::Zero());
  loss_based_bandwidth_estimator.UpdateBandwidthEstimate(
      enough_feedback_1,
      /*delay_based_estimate=*/DataRate::PlusInfinity(),
      /*in_alr=*/false);
  ASSERT_EQ(loss_based_bandwidth_estimator.GetLossBasedResult().state,
            LossBasedState::kDecreasing);

  // Network recovers after loss.
  std::vector<PacketResult> enough_feedback_2 =
      CreatePacketResultsWithReceivedPackets(
          /*first_packet_timestamp=*/Timestamp::Zero() +
          kObservationDurationLowerBound);
  loss_based_bandwidth_estimator.SetAcknowledgedBitrate(
      DataRate::KilobitsPerSec(600));
  loss_based_bandwidth_estimator.UpdateBandwidthEstimate(
      enough_feedback_2,
      /*delay_based_estimate=*/DataRate::PlusInfinity(),
      /*in_alr=*/false);
  EXPECT_NE(loss_based_bandwidth_estimator.GetLossBasedResult().state,
            LossBasedState::kDelayBasedEstimate);
}

// After loss based bwe backs off, the next estimate is capped by
// a factor of acked bitrate.
TEST_F(LossBasedBweV2Test,
       IncreaseByFactorOfAckedBitrateAfterLossBasedBweBacksOff) {
  ExplicitKeyValueConfig key_value_config(ShortObservationConfig(
      "LossThresholdOfHighBandwidthPreference:0.99,"
      "BwRampupUpperBoundFactor:1.2,"
      // Set InstantUpperBoundBwBalance high to disable InstantUpperBound cap.
      "InstantUpperBoundBwBalance:10000kbps,"));
  std::vector<PacketResult> enough_feedback_1 =
      CreatePacketResultsWith100pLossRate(
          /*first_packet_timestamp=*/Timestamp::Zero());
  LossBasedBweV2 loss_based_bandwidth_estimator(&key_value_config);
  DataRate delay_based_estimate = DataRate::KilobitsPerSec(5000);

  loss_based_bandwidth_estimator.SetBandwidthEstimate(
      DataRate::KilobitsPerSec(600));
  loss_based_bandwidth_estimator.SetAcknowledgedBitrate(
      DataRate::KilobitsPerSec(300));
  loss_based_bandwidth_estimator.UpdateBandwidthEstimate(enough_feedback_1,
                                                         delay_based_estimate,
                                                         /*in_alr=*/false);
  ASSERT_EQ(loss_based_bandwidth_estimator.GetLossBasedResult().state,
            LossBasedState::kDecreasing);
  LossBasedBweV2::Result result =
      loss_based_bandwidth_estimator.GetLossBasedResult();
  DataRate estimate_1 = result.bandwidth_estimate;
  ASSERT_LT(estimate_1.kbps(), 600);

  loss_based_bandwidth_estimator.SetAcknowledgedBitrate(estimate_1 * 0.9);

  int feedback_count = 1;
  while (feedback_count < 5 && result.state != LossBasedState::kIncreasing) {
    loss_based_bandwidth_estimator.UpdateBandwidthEstimate(
        CreatePacketResultsWithReceivedPackets(
            /*first_packet_timestamp=*/Timestamp::Zero() +
            feedback_count++ * kObservationDurationLowerBound),
        delay_based_estimate,
        /*in_alr=*/false);
    result = loss_based_bandwidth_estimator.GetLossBasedResult();
  }
  ASSERT_EQ(result.state, LossBasedState::kIncreasing);

  // The estimate is capped by acked_bitrate * BwRampupUpperBoundFactor.
  EXPECT_EQ(result.bandwidth_estimate, estimate_1 * 0.9 * 1.2);

  // But if acked bitrate decreases, BWE does not decrease when there is no
  // loss.
  loss_based_bandwidth_estimator.SetAcknowledgedBitrate(estimate_1 * 0.9);
  loss_based_bandwidth_estimator.UpdateBandwidthEstimate(
      CreatePacketResultsWithReceivedPackets(
          /*first_packet_timestamp=*/Timestamp::Zero() +
          feedback_count++ * kObservationDurationLowerBound),
      delay_based_estimate,
      /*in_alr=*/false);
  EXPECT_EQ(
      loss_based_bandwidth_estimator.GetLossBasedResult().bandwidth_estimate,
      result.bandwidth_estimate);
}

// Ensure that the state can switch to kIncrease even when the bandwidth is
// bounded by acked bitrate.
TEST_F(LossBasedBweV2Test, EnsureIncreaseEvenIfAckedBitrateBound) {
  ExplicitKeyValueConfig key_value_config(ShortObservationConfig(
      "LossThresholdOfHighBandwidthPreference:0.99,"
      "BwRampupUpperBoundFactor:1.2,"
      // Set InstantUpperBoundBwBalance high to disable InstantUpperBound cap.
      "InstantUpperBoundBwBalance:10000kbps,"));
  std::vector<PacketResult> enough_feedback_1 =
      CreatePacketResultsWith100pLossRate(
          /*first_packet_timestamp=*/Timestamp::Zero());
  LossBasedBweV2 loss_based_bandwidth_estimator(&key_value_config);
  DataRate delay_based_estimate = DataRate::KilobitsPerSec(5000);

  loss_based_bandwidth_estimator.SetBandwidthEstimate(
      DataRate::KilobitsPerSec(600));
  loss_based_bandwidth_estimator.SetAcknowledgedBitrate(
      DataRate::KilobitsPerSec(300));
  loss_based_bandwidth_estimator.UpdateBandwidthEstimate(enough_feedback_1,
                                                         delay_based_estimate,
                                                         /*in_alr=*/false);
  ASSERT_EQ(loss_based_bandwidth_estimator.GetLossBasedResult().state,
            LossBasedState::kDecreasing);
  LossBasedBweV2::Result result =
      loss_based_bandwidth_estimator.GetLossBasedResult();
  DataRate estimate_1 = result.bandwidth_estimate;
  ASSERT_LT(estimate_1.kbps(), 600);

  // Set a low acked bitrate.
  loss_based_bandwidth_estimator.SetAcknowledgedBitrate(estimate_1 / 2);

  int feedback_count = 1;
  while (feedback_count < 5 && result.state != LossBasedState::kIncreasing) {
    loss_based_bandwidth_estimator.UpdateBandwidthEstimate(
        CreatePacketResultsWithReceivedPackets(
            /*first_packet_timestamp=*/Timestamp::Zero() +
            feedback_count++ * kObservationDurationLowerBound),
        delay_based_estimate,
        /*in_alr=*/false);
    result = loss_based_bandwidth_estimator.GetLossBasedResult();
  }

  ASSERT_EQ(result.state, LossBasedState::kIncreasing);
  // The estimate increases by 1kbps.
  EXPECT_EQ(result.bandwidth_estimate, estimate_1 + DataRate::BitsPerSec(1));
}

// After loss based bwe backs off, the estimate is bounded during the delayed
// window.
TEST_F(LossBasedBweV2Test,
       EstimateBitrateIsBoundedDuringDelayedWindowAfterLossBasedBweBacksOff) {
  std::vector<PacketResult> enough_feedback_1 =
      CreatePacketResultsWithReceivedPackets(
          /*first_packet_timestamp=*/Timestamp::Zero());
  std::vector<PacketResult> enough_feedback_2 =
      CreatePacketResultsWith50pPacketLossRate(
          /*first_packet_timestamp=*/Timestamp::Zero() +
          kDelayedIncreaseWindow - TimeDelta::Millis(2));
  std::vector<PacketResult> enough_feedback_3 =
      CreatePacketResultsWithReceivedPackets(
          /*first_packet_timestamp=*/Timestamp::Zero() +
          kDelayedIncreaseWindow - TimeDelta::Millis(1));
  ExplicitKeyValueConfig key_value_config(
      Config(/*enabled=*/true, /*valid=*/true));
  LossBasedBweV2 loss_based_bandwidth_estimator(&key_value_config);
  DataRate delay_based_estimate = DataRate::KilobitsPerSec(5000);

  loss_based_bandwidth_estimator.SetBandwidthEstimate(
      DataRate::KilobitsPerSec(600));
  loss_based_bandwidth_estimator.SetAcknowledgedBitrate(
      DataRate::KilobitsPerSec(300));
  loss_based_bandwidth_estimator.UpdateBandwidthEstimate(enough_feedback_1,
                                                         delay_based_estimate,
                                                         /*in_alr=*/false);
  // Increase the acknowledged bitrate to make sure that the estimate is not
  // capped too low.
  loss_based_bandwidth_estimator.SetAcknowledgedBitrate(
      DataRate::KilobitsPerSec(5000));
  loss_based_bandwidth_estimator.UpdateBandwidthEstimate(enough_feedback_2,
                                                         delay_based_estimate,
                                                         /*in_alr=*/false);

  // The estimate is capped by current_estimate * kMaxIncreaseFactor because
  // it recently backed off.
  DataRate estimate_2 =
      loss_based_bandwidth_estimator.GetLossBasedResult().bandwidth_estimate;

  loss_based_bandwidth_estimator.UpdateBandwidthEstimate(enough_feedback_3,
                                                         delay_based_estimate,
                                                         /*in_alr=*/false);
  // The latest estimate is the same as the previous estimate since the sent
  // packets were sent within the DelayedIncreaseWindow.
  EXPECT_EQ(
      loss_based_bandwidth_estimator.GetLossBasedResult().bandwidth_estimate,
      estimate_2);
}

// The estimate is not bounded after the delayed increase window.
TEST_F(LossBasedBweV2Test, KeepIncreasingEstimateAfterDelayedIncreaseWindow) {
  std::vector<PacketResult> enough_feedback_1 =
      CreatePacketResultsWithReceivedPackets(
          /*first_packet_timestamp=*/Timestamp::Zero());
  std::vector<PacketResult> enough_feedback_2 =
      CreatePacketResultsWithReceivedPackets(
          /*first_packet_timestamp=*/Timestamp::Zero() +
          kDelayedIncreaseWindow - TimeDelta::Millis(1));
  std::vector<PacketResult> enough_feedback_3 =
      CreatePacketResultsWithReceivedPackets(
          /*first_packet_timestamp=*/Timestamp::Zero() +
          kDelayedIncreaseWindow + TimeDelta::Millis(1));
  ExplicitKeyValueConfig key_value_config(
      Config(/*enabled=*/true, /*valid=*/true));
  LossBasedBweV2 loss_based_bandwidth_estimator(&key_value_config);
  DataRate delay_based_estimate = DataRate::KilobitsPerSec(5000);

  loss_based_bandwidth_estimator.SetBandwidthEstimate(
      DataRate::KilobitsPerSec(600));
  loss_based_bandwidth_estimator.SetAcknowledgedBitrate(
      DataRate::KilobitsPerSec(300));
  loss_based_bandwidth_estimator.UpdateBandwidthEstimate(enough_feedback_1,
                                                         delay_based_estimate,
                                                         /*in_alr=*/false);
  // Increase the acknowledged bitrate to make sure that the estimate is not
  // capped too low.
  loss_based_bandwidth_estimator.SetAcknowledgedBitrate(
      DataRate::KilobitsPerSec(5000));
  loss_based_bandwidth_estimator.UpdateBandwidthEstimate(enough_feedback_2,
                                                         delay_based_estimate,
                                                         /*in_alr=*/false);

  // The estimate is capped by current_estimate * kMaxIncreaseFactor because it
  // recently backed off.
  DataRate estimate_2 =
      loss_based_bandwidth_estimator.GetLossBasedResult().bandwidth_estimate;

  loss_based_bandwidth_estimator.UpdateBandwidthEstimate(enough_feedback_3,
                                                         delay_based_estimate,
                                                         /*in_alr=*/false);
  // The estimate can continue increasing after the DelayedIncreaseWindow.
  EXPECT_GE(
      loss_based_bandwidth_estimator.GetLossBasedResult().bandwidth_estimate,
      estimate_2);
}

TEST_F(LossBasedBweV2Test, NotIncreaseIfInherentLossLessThanAverageLoss) {
  ExplicitKeyValueConfig key_value_config(ShortObservationConfig(
      "CandidateFactors:1.2,"
      "NotIncreaseIfInherentLossLessThanAverageLoss:true"));
  LossBasedBweV2 loss_based_bandwidth_estimator(&key_value_config);

  loss_based_bandwidth_estimator.SetBandwidthEstimate(
      DataRate::KilobitsPerSec(600));

  std::vector<PacketResult> enough_feedback_10p_loss_1 =
      CreatePacketResultsWith10pPacketLossRate(
          /*first_packet_timestamp=*/Timestamp::Zero());
  loss_based_bandwidth_estimator.UpdateBandwidthEstimate(
      enough_feedback_10p_loss_1,
      /*delay_based_estimate=*/DataRate::PlusInfinity(),
      /*in_alr=*/false);

  std::vector<PacketResult> enough_feedback_10p_loss_2 =
      CreatePacketResultsWith10pPacketLossRate(
          /*first_packet_timestamp=*/Timestamp::Zero() +
          kObservationDurationLowerBound);
  loss_based_bandwidth_estimator.UpdateBandwidthEstimate(
      enough_feedback_10p_loss_2,
      /*delay_based_estimate=*/DataRate::PlusInfinity(),
      /*in_alr=*/false);

  // Do not increase the bitrate because inherent loss is less than average loss
  EXPECT_EQ(
      loss_based_bandwidth_estimator.GetLossBasedResult().bandwidth_estimate,
      DataRate::KilobitsPerSec(600));
}

TEST_F(LossBasedBweV2Test,
       SelectHighBandwidthCandidateIfLossRateIsLessThanThreshold) {
  ExplicitKeyValueConfig key_value_config(ShortObservationConfig(
      "LossThresholdOfHighBandwidthPreference:0.20,"
      "NotIncreaseIfInherentLossLessThanAverageLoss:false"));
  LossBasedBweV2 loss_based_bandwidth_estimator(&key_value_config);
  DataRate delay_based_estimate = DataRate::KilobitsPerSec(5000);

  loss_based_bandwidth_estimator.SetBandwidthEstimate(
      DataRate::KilobitsPerSec(600));

  std::vector<PacketResult> enough_feedback_10p_loss_1 =
      CreatePacketResultsWith10pPacketLossRate(
          /*first_packet_timestamp=*/Timestamp::Zero());
  loss_based_bandwidth_estimator.UpdateBandwidthEstimate(
      enough_feedback_10p_loss_1, delay_based_estimate,

      /*in_alr=*/false);

  std::vector<PacketResult> enough_feedback_10p_loss_2 =
      CreatePacketResultsWith10pPacketLossRate(
          /*first_packet_timestamp=*/Timestamp::Zero() +
          kObservationDurationLowerBound);
  loss_based_bandwidth_estimator.UpdateBandwidthEstimate(
      enough_feedback_10p_loss_2, delay_based_estimate,

      /*in_alr=*/false);

  // Because LossThresholdOfHighBandwidthPreference is 20%, the average loss is
  // 10%, bandwidth estimate should increase.
  EXPECT_GT(
      loss_based_bandwidth_estimator.GetLossBasedResult().bandwidth_estimate,
      DataRate::KilobitsPerSec(600));
}

TEST_F(LossBasedBweV2Test,
       SelectLowBandwidthCandidateIfLossRateIsIsHigherThanThreshold) {
  ExplicitKeyValueConfig key_value_config(
      ShortObservationConfig("LossThresholdOfHighBandwidthPreference:0.05"));
  LossBasedBweV2 loss_based_bandwidth_estimator(&key_value_config);
  DataRate delay_based_estimate = DataRate::KilobitsPerSec(5000);

  loss_based_bandwidth_estimator.SetBandwidthEstimate(
      DataRate::KilobitsPerSec(600));

  std::vector<PacketResult> enough_feedback_10p_loss_1 =
      CreatePacketResultsWith10pPacketLossRate(
          /*first_packet_timestamp=*/Timestamp::Zero());
  loss_based_bandwidth_estimator.UpdateBandwidthEstimate(
      enough_feedback_10p_loss_1, delay_based_estimate,

      /*in_alr=*/false);

  std::vector<PacketResult> enough_feedback_10p_loss_2 =
      CreatePacketResultsWith10pPacketLossRate(
          /*first_packet_timestamp=*/Timestamp::Zero() +
          kObservationDurationLowerBound);
  loss_based_bandwidth_estimator.UpdateBandwidthEstimate(
      enough_feedback_10p_loss_2, delay_based_estimate,

      /*in_alr=*/false);

  // Because LossThresholdOfHighBandwidthPreference is 5%, the average loss is
  // 10%, bandwidth estimate should decrease.
  EXPECT_LT(
      loss_based_bandwidth_estimator.GetLossBasedResult().bandwidth_estimate,
      DataRate::KilobitsPerSec(600));
}

TEST_F(LossBasedBweV2Test, EstimateIsNotHigherThanMaxBitrate) {
  ExplicitKeyValueConfig key_value_config(
      Config(/*enabled=*/true, /*valid=*/true));
  LossBasedBweV2 loss_based_bandwidth_estimator(&key_value_config);
  loss_based_bandwidth_estimator.SetMinMaxBitrate(
      /*min_bitrate=*/DataRate::KilobitsPerSec(10),
      /*max_bitrate=*/DataRate::KilobitsPerSec(1000));
  loss_based_bandwidth_estimator.SetBandwidthEstimate(
      DataRate::KilobitsPerSec(1000));
  std::vector<PacketResult> enough_feedback =
      CreatePacketResultsWithReceivedPackets(
          /*first_packet_timestamp=*/Timestamp::Zero());
  loss_based_bandwidth_estimator.UpdateBandwidthEstimate(
      enough_feedback, /*delay_based_estimate=*/DataRate::PlusInfinity(),

      /*in_alr=*/false);

  EXPECT_LE(
      loss_based_bandwidth_estimator.GetLossBasedResult().bandwidth_estimate,
      DataRate::KilobitsPerSec(1000));
}

TEST_F(LossBasedBweV2Test, NotBackOffToAckedRateInAlr) {
  ExplicitKeyValueConfig key_value_config(
      ShortObservationConfig("InstantUpperBoundBwBalance:100kbps"));
  LossBasedBweV2 loss_based_bandwidth_estimator(&key_value_config);
  loss_based_bandwidth_estimator.SetMinMaxBitrate(
      /*min_bitrate=*/DataRate::KilobitsPerSec(10),
      /*max_bitrate=*/DataRate::KilobitsPerSec(1000000));
  DataRate delay_based_estimate = DataRate::KilobitsPerSec(5000);
  loss_based_bandwidth_estimator.SetBandwidthEstimate(
      DataRate::KilobitsPerSec(600));

  DataRate acked_rate = DataRate::KilobitsPerSec(100);
  loss_based_bandwidth_estimator.SetAcknowledgedBitrate(acked_rate);
  std::vector<PacketResult> enough_feedback_100p_loss_1 =
      CreatePacketResultsWith100pLossRate(
          /*first_packet_timestamp=*/Timestamp::Zero());
  loss_based_bandwidth_estimator.UpdateBandwidthEstimate(
      enough_feedback_100p_loss_1, delay_based_estimate,
      /*in_alr=*/true);

  // Make sure that the estimate decreases but higher than acked rate.
  EXPECT_GT(
      loss_based_bandwidth_estimator.GetLossBasedResult().bandwidth_estimate,
      acked_rate);

  EXPECT_LT(
      loss_based_bandwidth_estimator.GetLossBasedResult().bandwidth_estimate,
      DataRate::KilobitsPerSec(600));
}

TEST_F(LossBasedBweV2Test, BackOffToAckedRateIfNotInAlr) {
  ExplicitKeyValueConfig key_value_config(
      ShortObservationConfig("InstantUpperBoundBwBalance:100kbps"));
  LossBasedBweV2 loss_based_bandwidth_estimator(&key_value_config);
  loss_based_bandwidth_estimator.SetMinMaxBitrate(
      /*min_bitrate=*/DataRate::KilobitsPerSec(10),
      /*max_bitrate=*/DataRate::KilobitsPerSec(1000000));
  DataRate delay_based_estimate = DataRate::KilobitsPerSec(5000);
  loss_based_bandwidth_estimator.SetBandwidthEstimate(
      DataRate::KilobitsPerSec(600));

  DataRate acked_rate = DataRate::KilobitsPerSec(100);
  loss_based_bandwidth_estimator.SetAcknowledgedBitrate(acked_rate);
  std::vector<PacketResult> enough_feedback_100p_loss_1 =
      CreatePacketResultsWith100pLossRate(
          /*first_packet_timestamp=*/Timestamp::Zero());
  loss_based_bandwidth_estimator.UpdateBandwidthEstimate(
      enough_feedback_100p_loss_1, delay_based_estimate,

      /*in_alr=*/false);

  // Make sure that the estimate decreases but higher than acked rate.
  EXPECT_EQ(
      loss_based_bandwidth_estimator.GetLossBasedResult().bandwidth_estimate,
      acked_rate);
}

TEST_F(LossBasedBweV2Test, NotReadyToUseInStartPhase) {
  ExplicitKeyValueConfig key_value_config(
      ShortObservationConfig("UseInStartPhase:true"));
  LossBasedBweV2 loss_based_bandwidth_estimator(&key_value_config);
  // Make sure that the estimator is not ready to use in start phase because of
  // lacking TWCC feedback.
  EXPECT_FALSE(loss_based_bandwidth_estimator.ReadyToUseInStartPhase());
}

TEST_F(LossBasedBweV2Test, ReadyToUseInStartPhase) {
  ExplicitKeyValueConfig key_value_config(
      ShortObservationConfig("UseInStartPhase:true"));
  LossBasedBweV2 loss_based_bandwidth_estimator(&key_value_config);
  std::vector<PacketResult> enough_feedback =
      CreatePacketResultsWithReceivedPackets(
          /*first_packet_timestamp=*/Timestamp::Zero());

  loss_based_bandwidth_estimator.UpdateBandwidthEstimate(
      enough_feedback, /*delay_based_estimate=*/DataRate::KilobitsPerSec(600),
      /*in_alr=*/false);
  EXPECT_TRUE(loss_based_bandwidth_estimator.ReadyToUseInStartPhase());
}

TEST_F(LossBasedBweV2Test, BoundEstimateByAckedRate) {
  ExplicitKeyValueConfig key_value_config(
      ShortObservationConfig("LowerBoundByAckedRateFactor:1.0"));
  LossBasedBweV2 loss_based_bandwidth_estimator(&key_value_config);
  loss_based_bandwidth_estimator.SetMinMaxBitrate(
      /*min_bitrate=*/DataRate::KilobitsPerSec(10),
      /*max_bitrate=*/DataRate::KilobitsPerSec(1000000));
  loss_based_bandwidth_estimator.SetBandwidthEstimate(
      DataRate::KilobitsPerSec(600));
  loss_based_bandwidth_estimator.SetAcknowledgedBitrate(
      DataRate::KilobitsPerSec(500));

  std::vector<PacketResult> enough_feedback_100p_loss_1 =
      CreatePacketResultsWith100pLossRate(
          /*first_packet_timestamp=*/Timestamp::Zero());
  loss_based_bandwidth_estimator.UpdateBandwidthEstimate(
      enough_feedback_100p_loss_1,
      /*delay_based_estimate=*/DataRate::PlusInfinity(),
      /*in_alr=*/false);

  EXPECT_EQ(
      loss_based_bandwidth_estimator.GetLossBasedResult().bandwidth_estimate,
      DataRate::KilobitsPerSec(500));
}

TEST_F(LossBasedBweV2Test, NotBoundEstimateByAckedRate) {
  ExplicitKeyValueConfig key_value_config(
      ShortObservationConfig("LowerBoundByAckedRateFactor:0.0"));
  LossBasedBweV2 loss_based_bandwidth_estimator(&key_value_config);
  loss_based_bandwidth_estimator.SetMinMaxBitrate(
      /*min_bitrate=*/DataRate::KilobitsPerSec(10),
      /*max_bitrate=*/DataRate::KilobitsPerSec(1000000));
  loss_based_bandwidth_estimator.SetBandwidthEstimate(
      DataRate::KilobitsPerSec(600));
  loss_based_bandwidth_estimator.SetAcknowledgedBitrate(
      DataRate::KilobitsPerSec(500));

  std::vector<PacketResult> enough_feedback_100p_loss_1 =
      CreatePacketResultsWith100pLossRate(
          /*first_packet_timestamp=*/Timestamp::Zero());
  loss_based_bandwidth_estimator.UpdateBandwidthEstimate(
      enough_feedback_100p_loss_1,
      /*delay_based_estimate=*/DataRate::PlusInfinity(),
      /*in_alr=*/false);

  EXPECT_LT(
      loss_based_bandwidth_estimator.GetLossBasedResult().bandwidth_estimate,
      DataRate::KilobitsPerSec(500));
}

TEST_F(LossBasedBweV2Test, HasDecreaseStateBecauseOfUpperBound) {
  ExplicitKeyValueConfig key_value_config(ShortObservationConfig(
      "CandidateFactors:1.0,InstantUpperBoundBwBalance:10kbps"));
  LossBasedBweV2 loss_based_bandwidth_estimator(&key_value_config);
  loss_based_bandwidth_estimator.SetMinMaxBitrate(
      /*min_bitrate=*/DataRate::KilobitsPerSec(10),
      /*max_bitrate=*/DataRate::KilobitsPerSec(1000000));
  loss_based_bandwidth_estimator.SetBandwidthEstimate(
      DataRate::KilobitsPerSec(500));
  loss_based_bandwidth_estimator.SetAcknowledgedBitrate(
      DataRate::KilobitsPerSec(500));

  std::vector<PacketResult> enough_feedback_10p_loss_1 =
      CreatePacketResultsWith10pPacketLossRate(
          /*first_packet_timestamp=*/Timestamp::Zero());
  loss_based_bandwidth_estimator.UpdateBandwidthEstimate(
      enough_feedback_10p_loss_1,
      /*delay_based_estimate=*/DataRate::PlusInfinity(),
      /*in_alr=*/false);

  // Verify that the instant upper bound decreases the estimate, and state is
  // updated to kDecreasing.
  EXPECT_EQ(
      loss_based_bandwidth_estimator.GetLossBasedResult().bandwidth_estimate,
      DataRate::KilobitsPerSec(200));
  EXPECT_EQ(loss_based_bandwidth_estimator.GetLossBasedResult().state,
            LossBasedState::kDecreasing);
}

TEST_F(LossBasedBweV2Test, HasIncreaseStateBecauseOfLowerBound) {
  ExplicitKeyValueConfig key_value_config(ShortObservationConfig(
      "CandidateFactors:1.0,LowerBoundByAckedRateFactor:10.0"));
  LossBasedBweV2 loss_based_bandwidth_estimator(&key_value_config);
  loss_based_bandwidth_estimator.SetMinMaxBitrate(
      /*min_bitrate=*/DataRate::KilobitsPerSec(10),
      /*max_bitrate=*/DataRate::KilobitsPerSec(1000000));
  loss_based_bandwidth_estimator.SetBandwidthEstimate(
      DataRate::KilobitsPerSec(500));
  loss_based_bandwidth_estimator.SetAcknowledgedBitrate(
      DataRate::KilobitsPerSec(1));

  // Network has a high loss to create a loss scenario.
  std::vector<PacketResult> enough_feedback_50p_loss_1 =
      CreatePacketResultsWith50pPacketLossRate(
          /*first_packet_timestamp=*/Timestamp::Zero());
  loss_based_bandwidth_estimator.UpdateBandwidthEstimate(
      enough_feedback_50p_loss_1,
      /*delay_based_estimate=*/DataRate::PlusInfinity(),
      /*in_alr=*/false);

  ASSERT_EQ(loss_based_bandwidth_estimator.GetLossBasedResult().state,
            LossBasedState::kDecreasing);

  // Network still has a high loss, but better acked rate.
  loss_based_bandwidth_estimator.SetAcknowledgedBitrate(
      DataRate::KilobitsPerSec(200));
  std::vector<PacketResult> enough_feedback_50p_loss_2 =
      CreatePacketResultsWith50pPacketLossRate(
          /*first_packet_timestamp=*/Timestamp::Zero() +
          kObservationDurationLowerBound);
  loss_based_bandwidth_estimator.UpdateBandwidthEstimate(
      enough_feedback_50p_loss_2,
      /*delay_based_estimate=*/DataRate::PlusInfinity(),
      /*in_alr=*/false);

  // Verify that the instant lower bound increases the estimate, and state is
  // updated to kIncreasing.
  EXPECT_EQ(
      loss_based_bandwidth_estimator.GetLossBasedResult().bandwidth_estimate,
      DataRate::KilobitsPerSec(200) * 10);
  EXPECT_EQ(loss_based_bandwidth_estimator.GetLossBasedResult().state,
            LossBasedState::kIncreasing);
}

TEST_F(LossBasedBweV2Test,
       EstimateIncreaseSlowlyFromInstantUpperBoundInAlrIfFieldTrial) {
  ExplicitKeyValueConfig key_value_config(
      ShortObservationConfig("UpperBoundCandidateInAlr:true"));
  LossBasedBweV2 loss_based_bandwidth_estimator(&key_value_config);
  loss_based_bandwidth_estimator.SetBandwidthEstimate(
      DataRate::KilobitsPerSec(1000));
  loss_based_bandwidth_estimator.SetAcknowledgedBitrate(
      DataRate::KilobitsPerSec(150));
  loss_based_bandwidth_estimator.UpdateBandwidthEstimate(
      CreatePacketResultsWith50pPacketLossRate(
          /*first_packet_timestamp=*/Timestamp::Zero()),
      /*delay_based_estimate=*/DataRate::PlusInfinity(),
      /*in_alr=*/true);
  LossBasedBweV2::Result result_after_loss =
      loss_based_bandwidth_estimator.GetLossBasedResult();
  ASSERT_EQ(result_after_loss.state, LossBasedState::kDecreasing);

  for (int feedback_count = 1; feedback_count <= 3; ++feedback_count) {
    loss_based_bandwidth_estimator.UpdateBandwidthEstimate(
        CreatePacketResultsWithReceivedPackets(
            /*first_packet_timestamp=*/Timestamp::Zero() +
            feedback_count * kObservationDurationLowerBound),
        /*delay_based_estimate=*/DataRate::PlusInfinity(),
        /*in_alr=*/true);
  }
  // Expect less than 100% increase.
  EXPECT_LT(
      loss_based_bandwidth_estimator.GetLossBasedResult().bandwidth_estimate,
      2 * result_after_loss.bandwidth_estimate);
}

TEST_F(LossBasedBweV2Test, HasDelayBasedStateIfLossBasedBweIsMax) {
  ExplicitKeyValueConfig key_value_config(ShortObservationConfig(""));
  LossBasedBweV2 loss_based_bandwidth_estimator(&key_value_config);
  loss_based_bandwidth_estimator.SetMinMaxBitrate(
      /*min_bitrate=*/DataRate::KilobitsPerSec(10),
      /*max_bitrate=*/DataRate::KilobitsPerSec(1000));

  loss_based_bandwidth_estimator.UpdateBandwidthEstimate(
      /*feedback = */ CreatePacketResultsWithReceivedPackets(
          /*first_packet_timestamp=*/Timestamp::Zero()),
      /*delay_based_estimate=*/DataRate::KilobitsPerSec(2000),
      /*in_alr=*/false);
  EXPECT_EQ(loss_based_bandwidth_estimator.GetLossBasedResult().state,
            LossBasedState::kDelayBasedEstimate);
  EXPECT_EQ(
      loss_based_bandwidth_estimator.GetLossBasedResult().bandwidth_estimate,
      DataRate::KilobitsPerSec(1000));

  loss_based_bandwidth_estimator.UpdateBandwidthEstimate(
      /*feedback=*/CreatePacketResultsWith50pPacketLossRate(
          /*first_packet_timestamp=*/Timestamp::Zero() +
          kObservationDurationLowerBound),
      /*delay_based_estimate=*/DataRate::KilobitsPerSec(2000),
      /*in_alr=*/false);
  LossBasedBweV2::Result result =
      loss_based_bandwidth_estimator.GetLossBasedResult();
  ASSERT_EQ(result.state, LossBasedState::kDecreasing);
  ASSERT_LT(result.bandwidth_estimate, DataRate::KilobitsPerSec(1000));

  // Eventually  the estimator recovers to delay based state.
  int feedback_count = 2;
  while (feedback_count < 5 &&
         result.state != LossBasedState::kDelayBasedEstimate) {
    loss_based_bandwidth_estimator.UpdateBandwidthEstimate(
        /*feedback = */ CreatePacketResultsWithReceivedPackets(
            /*first_packet_timestamp=*/Timestamp::Zero() +
            feedback_count++ * kObservationDurationLowerBound),
        /*delay_based_estimate=*/DataRate::KilobitsPerSec(2000),
        /*in_alr=*/false);
    result = loss_based_bandwidth_estimator.GetLossBasedResult();
  }
  EXPECT_EQ(result.state, LossBasedState::kDelayBasedEstimate);
  EXPECT_EQ(
      loss_based_bandwidth_estimator.GetLossBasedResult().bandwidth_estimate,
      DataRate::KilobitsPerSec(1000));
}

TEST_F(LossBasedBweV2Test, IncreaseUsingPaddingStateIfFieldTrial) {
  ExplicitKeyValueConfig key_value_config(
      ShortObservationConfig("PaddingDuration:1000ms"));
  LossBasedBweV2 loss_based_bandwidth_estimator(&key_value_config);
  loss_based_bandwidth_estimator.SetBandwidthEstimate(
      DataRate::KilobitsPerSec(2500));
  loss_based_bandwidth_estimator.UpdateBandwidthEstimate(
      CreatePacketResultsWith50pPacketLossRate(
          /*first_packet_timestamp=*/Timestamp::Zero()),
      /*delay_based_estimate=*/DataRate::PlusInfinity(),
      /*in_alr=*/false);
  ASSERT_EQ(loss_based_bandwidth_estimator.GetLossBasedResult().state,
            LossBasedState::kDecreasing);

  loss_based_bandwidth_estimator.UpdateBandwidthEstimate(
      CreatePacketResultsWithReceivedPackets(
          /*first_packet_timestamp=*/Timestamp::Zero() +
          kObservationDurationLowerBound),
      /*delay_based_estimate=*/DataRate::PlusInfinity(),
      /*in_alr=*/false);
  EXPECT_EQ(loss_based_bandwidth_estimator.GetLossBasedResult().state,
            LossBasedState::kIncreaseUsingPadding);
}

TEST_F(LossBasedBweV2Test, BestCandidateResetsToUpperBoundInFieldTrial) {
  ExplicitKeyValueConfig key_value_config(
      ShortObservationConfig("PaddingDuration:1000ms,BoundBestCandidate:true"));
  LossBasedBweV2 loss_based_bandwidth_estimator(&key_value_config);
  loss_based_bandwidth_estimator.SetBandwidthEstimate(
      DataRate::KilobitsPerSec(2500));
  loss_based_bandwidth_estimator.UpdateBandwidthEstimate(
      CreatePacketResultsWith50pPacketLossRate(
          /*first_packet_timestamp=*/Timestamp::Zero()),
      /*delay_based_estimate=*/DataRate::PlusInfinity(),
      /*in_alr=*/true);
  LossBasedBweV2::Result result_after_loss =
      loss_based_bandwidth_estimator.GetLossBasedResult();
  ASSERT_EQ(result_after_loss.state, LossBasedState::kDecreasing);

  loss_based_bandwidth_estimator.UpdateBandwidthEstimate(
      CreatePacketResultsWithReceivedPackets(
          /*first_packet_timestamp=*/Timestamp::Zero() +
          kObservationDurationLowerBound),
      /*delay_based_estimate=*/DataRate::PlusInfinity(),
      /*in_alr=*/true);
  loss_based_bandwidth_estimator.UpdateBandwidthEstimate(
      CreatePacketResultsWithReceivedPackets(
          /*first_packet_timestamp=*/Timestamp::Zero() +
          2 * kObservationDurationLowerBound),
      /*delay_based_estimate=*/DataRate::PlusInfinity(),
      /*in_alr=*/true);
  // After a BWE decrease due to large loss, BWE is expected to ramp up slowly
  // and follow the acked bitrate.
  EXPECT_EQ(loss_based_bandwidth_estimator.GetLossBasedResult().state,
            LossBasedState::kIncreaseUsingPadding);
  EXPECT_NEAR(loss_based_bandwidth_estimator.GetLossBasedResult()
                  .bandwidth_estimate.kbps(),
              result_after_loss.bandwidth_estimate.kbps(), 100);
}

TEST_F(LossBasedBweV2Test, DecreaseToAckedCandidateIfPaddingInAlr) {
  ExplicitKeyValueConfig key_value_config(ShortObservationConfig(
      "PaddingDuration:1000ms,"
      // Set InstantUpperBoundBwBalance high to disable InstantUpperBound cap.
      "InstantUpperBoundBwBalance:10000kbps"));
  LossBasedBweV2 loss_based_bandwidth_estimator(&key_value_config);
  loss_based_bandwidth_estimator.SetBandwidthEstimate(
      DataRate::KilobitsPerSec(1000));
  int feedback_id = 0;
  while (loss_based_bandwidth_estimator.GetLossBasedResult().state !=
         LossBasedState::kDecreasing) {
    loss_based_bandwidth_estimator.UpdateBandwidthEstimate(
        CreatePacketResultsWith100pLossRate(
            /*first_packet_timestamp=*/Timestamp::Zero() +
            kObservationDurationLowerBound * feedback_id),
        /*delay_based_estimate=*/DataRate::PlusInfinity(),
        /*in_alr=*/true);
    feedback_id++;
  }

  while (loss_based_bandwidth_estimator.GetLossBasedResult().state !=
         LossBasedState::kIncreaseUsingPadding) {
    loss_based_bandwidth_estimator.UpdateBandwidthEstimate(
        CreatePacketResultsWithReceivedPackets(
            /*first_packet_timestamp=*/Timestamp::Zero() +
            kObservationDurationLowerBound * feedback_id),
        /*delay_based_estimate=*/DataRate::PlusInfinity(),
        /*in_alr=*/true);
    feedback_id++;
  }
  ASSERT_GT(
      loss_based_bandwidth_estimator.GetLossBasedResult().bandwidth_estimate,
      DataRate::KilobitsPerSec(900));

  loss_based_bandwidth_estimator.SetAcknowledgedBitrate(
      DataRate::KilobitsPerSec(100));
  // Padding is sent now, create some lost packets.
  loss_based_bandwidth_estimator.UpdateBandwidthEstimate(
      CreatePacketResultsWith100pLossRate(
          /*first_packet_timestamp=*/Timestamp::Zero() +
          kObservationDurationLowerBound * feedback_id),
      /*delay_based_estimate=*/DataRate::PlusInfinity(),
      /*in_alr=*/true);
  EXPECT_EQ(loss_based_bandwidth_estimator.GetLossBasedResult().state,
            LossBasedState::kDecreasing);
  EXPECT_EQ(
      loss_based_bandwidth_estimator.GetLossBasedResult().bandwidth_estimate,
      DataRate::KilobitsPerSec(100));
}

TEST_F(LossBasedBweV2Test, DecreaseAfterPadding) {
  ExplicitKeyValueConfig key_value_config(ShortObservationConfig(
      "PaddingDuration:1000ms,BwRampupUpperBoundFactor:2.0"));
  LossBasedBweV2 loss_based_bandwidth_estimator(&key_value_config);
  loss_based_bandwidth_estimator.SetBandwidthEstimate(
      DataRate::KilobitsPerSec(2500));
  DataRate acknowledged_bitrate = DataRate::KilobitsPerSec(51);
  loss_based_bandwidth_estimator.SetAcknowledgedBitrate(acknowledged_bitrate);
  loss_based_bandwidth_estimator.UpdateBandwidthEstimate(
      CreatePacketResultsWith50pPacketLossRate(
          /*first_packet_timestamp=*/Timestamp::Zero()),
      /*delay_based_estimate=*/DataRate::PlusInfinity(),
      /*in_alr=*/false);
  ASSERT_EQ(loss_based_bandwidth_estimator.GetLossBasedResult().state,
            LossBasedState::kDecreasing);
  ASSERT_EQ(
      loss_based_bandwidth_estimator.GetLossBasedResult().bandwidth_estimate,
      acknowledged_bitrate);

  acknowledged_bitrate = DataRate::KilobitsPerSec(26);
  loss_based_bandwidth_estimator.SetAcknowledgedBitrate(acknowledged_bitrate);
  int feedback_id = 1;
  while (loss_based_bandwidth_estimator.GetLossBasedResult().state !=
         LossBasedState::kIncreaseUsingPadding) {
    loss_based_bandwidth_estimator.UpdateBandwidthEstimate(
        CreatePacketResultsWithReceivedPackets(
            /*first_packet_timestamp=*/Timestamp::Zero() +
            kObservationDurationLowerBound * feedback_id),
        /*delay_based_estimate=*/DataRate::PlusInfinity(),
        /*in_alr=*/false);
    feedback_id++;
  }

  const Timestamp estimate_increased =
      Timestamp::Zero() + kObservationDurationLowerBound * feedback_id;
  // The state is kIncreaseUsingPadding for a while without changing the
  // estimate, which is limited by 2 * acked rate.
  while (loss_based_bandwidth_estimator.GetLossBasedResult().state ==
         LossBasedState::kIncreaseUsingPadding) {
    loss_based_bandwidth_estimator.UpdateBandwidthEstimate(
        CreatePacketResultsWithReceivedPackets(
            /*first_packet_timestamp=*/Timestamp::Zero() +
            kObservationDurationLowerBound * feedback_id),
        /*delay_based_estimate=*/DataRate::PlusInfinity(),
        /*in_alr=*/false);
    feedback_id++;
  }

  EXPECT_EQ(loss_based_bandwidth_estimator.GetLossBasedResult().state,
            LossBasedState::kDecreasing);
  const Timestamp start_decreasing =
      Timestamp::Zero() + kObservationDurationLowerBound * (feedback_id - 1);
  EXPECT_EQ(start_decreasing - estimate_increased, TimeDelta::Seconds(1));
}

TEST_F(LossBasedBweV2Test, IncreaseEstimateIfNotHold) {
  ExplicitKeyValueConfig key_value_config(
      ShortObservationConfig("HoldDurationFactor:0"));
  LossBasedBweV2 loss_based_bandwidth_estimator(&key_value_config);
  loss_based_bandwidth_estimator.SetBandwidthEstimate(
      DataRate::KilobitsPerSec(2500));
  loss_based_bandwidth_estimator.UpdateBandwidthEstimate(
      CreatePacketResultsWith50pPacketLossRate(
          /*first_packet_timestamp=*/Timestamp::Zero()),
      /*delay_based_estimate=*/DataRate::PlusInfinity(),
      /*in_alr=*/false);
  ASSERT_EQ(loss_based_bandwidth_estimator.GetLossBasedResult().state,
            LossBasedState::kDecreasing);
  DataRate estimate =
      loss_based_bandwidth_estimator.GetLossBasedResult().bandwidth_estimate;

  loss_based_bandwidth_estimator.UpdateBandwidthEstimate(
      CreatePacketResultsWithReceivedPackets(
          /*first_packet_timestamp=*/Timestamp::Zero() +
          kObservationDurationLowerBound),
      /*delay_based_estimate=*/DataRate::PlusInfinity(),
      /*in_alr=*/false);
  EXPECT_EQ(loss_based_bandwidth_estimator.GetLossBasedResult().state,
            LossBasedState::kIncreasing);
  EXPECT_GT(
      loss_based_bandwidth_estimator.GetLossBasedResult().bandwidth_estimate,
      estimate);
}

TEST_F(LossBasedBweV2Test, IncreaseEstimateAfterHoldDuration) {
  ExplicitKeyValueConfig key_value_config(
      ShortObservationConfig("HoldDurationFactor:10"));
  LossBasedBweV2 loss_based_bandwidth_estimator(&key_value_config);
  loss_based_bandwidth_estimator.SetBandwidthEstimate(
      DataRate::KilobitsPerSec(2500));
  loss_based_bandwidth_estimator.UpdateBandwidthEstimate(
      CreatePacketResultsWith50pPacketLossRate(
          /*first_packet_timestamp=*/Timestamp::Zero()),
      /*delay_based_estimate=*/DataRate::PlusInfinity(),
      /*in_alr=*/false);
  ASSERT_EQ(loss_based_bandwidth_estimator.GetLossBasedResult().state,
            LossBasedState::kDecreasing);
  DataRate estimate =
      loss_based_bandwidth_estimator.GetLossBasedResult().bandwidth_estimate;

  // During the hold duration, e.g. first 300ms, the estimate cannot increase.
  loss_based_bandwidth_estimator.UpdateBandwidthEstimate(
      CreatePacketResultsWithReceivedPackets(
          /*first_packet_timestamp=*/Timestamp::Zero() +
          kObservationDurationLowerBound),
      /*delay_based_estimate=*/DataRate::PlusInfinity(),
      /*in_alr=*/false);
  EXPECT_EQ(loss_based_bandwidth_estimator.GetLossBasedResult().state,
            LossBasedState::kDecreasing);
  EXPECT_EQ(
      loss_based_bandwidth_estimator.GetLossBasedResult().bandwidth_estimate,
      estimate);

  // After the hold duration, the estimate can increase.
  loss_based_bandwidth_estimator.UpdateBandwidthEstimate(
      CreatePacketResultsWithReceivedPackets(
          /*first_packet_timestamp=*/Timestamp::Zero() +
          kObservationDurationLowerBound * 2),
      /*delay_based_estimate=*/DataRate::PlusInfinity(),
      /*in_alr=*/false);
  EXPECT_EQ(loss_based_bandwidth_estimator.GetLossBasedResult().state,
            LossBasedState::kIncreasing);
  EXPECT_GE(
      loss_based_bandwidth_estimator.GetLossBasedResult().bandwidth_estimate,
      estimate);

  // Get another 50p packet loss.
  loss_based_bandwidth_estimator.UpdateBandwidthEstimate(
      CreatePacketResultsWith50pPacketLossRate(
          /*first_packet_timestamp=*/Timestamp::Zero() +
          kObservationDurationLowerBound * 3),
      /*delay_based_estimate=*/DataRate::PlusInfinity(),
      /*in_alr=*/false);
  EXPECT_EQ(loss_based_bandwidth_estimator.GetLossBasedResult().state,
            LossBasedState::kDecreasing);
  DataRate estimate_at_hold =
      loss_based_bandwidth_estimator.GetLossBasedResult().bandwidth_estimate;

  // In the hold duration, e.g. next 3s, the estimate cannot increase above the
  // hold rate. Get some lost packets to get lower estimate than the HOLD rate.
  for (int i = 4; i <= 6; ++i) {
    loss_based_bandwidth_estimator.UpdateBandwidthEstimate(
        CreatePacketResultsWith100pLossRate(
            /*first_packet_timestamp=*/Timestamp::Zero() +
            kObservationDurationLowerBound * i),
        /*delay_based_estimate=*/DataRate::PlusInfinity(),
        /*in_alr=*/false);
    EXPECT_EQ(loss_based_bandwidth_estimator.GetLossBasedResult().state,
              LossBasedState::kDecreasing);
    EXPECT_LT(
        loss_based_bandwidth_estimator.GetLossBasedResult().bandwidth_estimate,
        estimate_at_hold);
  }

  int feedback_id = 7;
  while (loss_based_bandwidth_estimator.GetLossBasedResult().state !=
         LossBasedState::kIncreasing) {
    loss_based_bandwidth_estimator.UpdateBandwidthEstimate(
        CreatePacketResultsWithReceivedPackets(
            /*first_packet_timestamp=*/Timestamp::Zero() +
            kObservationDurationLowerBound * feedback_id),
        /*delay_based_estimate=*/DataRate::PlusInfinity(),
        /*in_alr=*/false);
    if (loss_based_bandwidth_estimator.GetLossBasedResult().state ==
        LossBasedState::kDecreasing) {
      // In the hold duration, the estimate can not go higher than estimate at
      // hold.
      EXPECT_LE(loss_based_bandwidth_estimator.GetLossBasedResult()
                    .bandwidth_estimate,
                estimate_at_hold);
    } else if (loss_based_bandwidth_estimator.GetLossBasedResult().state ==
               LossBasedState::kIncreasing) {
      // After the hold duration, the estimate can increase again.
      EXPECT_GT(loss_based_bandwidth_estimator.GetLossBasedResult()
                    .bandwidth_estimate,
                estimate_at_hold);
    }
    feedback_id++;
  }
}

TEST_F(LossBasedBweV2Test, HoldRateNotLowerThanAckedRate) {
  ExplicitKeyValueConfig key_value_config(ShortObservationConfig(
      "HoldDurationFactor:10,LowerBoundByAckedRateFactor:1.0"));
  LossBasedBweV2 loss_based_bandwidth_estimator(&key_value_config);
  loss_based_bandwidth_estimator.SetBandwidthEstimate(
      DataRate::KilobitsPerSec(2500));
  loss_based_bandwidth_estimator.UpdateBandwidthEstimate(
      CreatePacketResultsWith50pPacketLossRate(
          /*first_packet_timestamp=*/Timestamp::Zero()),
      /*delay_based_estimate=*/DataRate::PlusInfinity(),
      /*in_alr=*/false);
  ASSERT_EQ(loss_based_bandwidth_estimator.GetLossBasedResult().state,
            LossBasedState::kDecreasing);

  // During the hold duration, hold rate is not lower than the acked rate.
  loss_based_bandwidth_estimator.SetAcknowledgedBitrate(
      DataRate::KilobitsPerSec(1000));
  loss_based_bandwidth_estimator.UpdateBandwidthEstimate(
      CreatePacketResultsWith50pPacketLossRate(
          /*first_packet_timestamp=*/Timestamp::Zero() +
          kObservationDurationLowerBound),
      /*delay_based_estimate=*/DataRate::PlusInfinity(),
      /*in_alr=*/false);
  EXPECT_EQ(loss_based_bandwidth_estimator.GetLossBasedResult().state,
            LossBasedState::kDecreasing);
  EXPECT_EQ(
      loss_based_bandwidth_estimator.GetLossBasedResult().bandwidth_estimate,
      DataRate::KilobitsPerSec(1000));
}

TEST_F(LossBasedBweV2Test, EstimateNotLowerThanAckedRate) {
  ExplicitKeyValueConfig key_value_config(
      ShortObservationConfig("LowerBoundByAckedRateFactor:1.0"));
  LossBasedBweV2 loss_based_bandwidth_estimator(&key_value_config);
  loss_based_bandwidth_estimator.SetBandwidthEstimate(
      DataRate::KilobitsPerSec(2500));
  loss_based_bandwidth_estimator.UpdateBandwidthEstimate(
      CreatePacketResultsWith100pLossRate(
          /*first_packet_timestamp=*/Timestamp::Zero()),
      /*delay_based_estimate=*/DataRate::PlusInfinity(),
      /*in_alr=*/false);
  ASSERT_LT(
      loss_based_bandwidth_estimator.GetLossBasedResult().bandwidth_estimate,
      DataRate::KilobitsPerSec(1000));

  loss_based_bandwidth_estimator.SetAcknowledgedBitrate(
      DataRate::KilobitsPerSec(1000));
  loss_based_bandwidth_estimator.UpdateBandwidthEstimate(
      CreatePacketResultsWith100pLossRate(
          /*first_packet_timestamp=*/Timestamp::Zero() +
          kObservationDurationLowerBound),
      /*delay_based_estimate=*/DataRate::PlusInfinity(),
      /*in_alr=*/false);
  EXPECT_EQ(
      loss_based_bandwidth_estimator.GetLossBasedResult().bandwidth_estimate,
      DataRate::KilobitsPerSec(1000));

  loss_based_bandwidth_estimator.UpdateBandwidthEstimate(
      CreatePacketResultsWithReceivedPackets(
          /*first_packet_timestamp=*/Timestamp::Zero() +
          kObservationDurationLowerBound * 2),
      /*delay_based_estimate=*/DataRate::PlusInfinity(),
      /*in_alr=*/false);
  loss_based_bandwidth_estimator.UpdateBandwidthEstimate(
      CreatePacketResultsWithReceivedPackets(
          /*first_packet_timestamp=*/Timestamp::Zero() +
          kObservationDurationLowerBound * 3),
      /*delay_based_estimate=*/DataRate::PlusInfinity(),
      /*in_alr=*/false);

  // Verify that the estimate recovers from the acked rate.
  EXPECT_GT(
      loss_based_bandwidth_estimator.GetLossBasedResult().bandwidth_estimate,
      DataRate::KilobitsPerSec(1000));
}

TEST_F(LossBasedBweV2Test, EndHoldDurationIfDelayBasedEstimateWorks) {
  ExplicitKeyValueConfig key_value_config(
      ShortObservationConfig("HoldDurationFactor:3"));
  LossBasedBweV2 loss_based_bandwidth_estimator(&key_value_config);
  loss_based_bandwidth_estimator.SetBandwidthEstimate(
      DataRate::KilobitsPerSec(2500));
  loss_based_bandwidth_estimator.UpdateBandwidthEstimate(
      CreatePacketResultsWith50pPacketLossRate(
          /*first_packet_timestamp=*/Timestamp::Zero()),
      /*delay_based_estimate=*/DataRate::PlusInfinity(),
      /*in_alr=*/false);
  ASSERT_EQ(loss_based_bandwidth_estimator.GetLossBasedResult().state,
            LossBasedState::kDecreasing);
  DataRate estimate =
      loss_based_bandwidth_estimator.GetLossBasedResult().bandwidth_estimate;

  loss_based_bandwidth_estimator.UpdateBandwidthEstimate(
      CreatePacketResultsWithReceivedPackets(
          /*first_packet_timestamp=*/Timestamp::Zero() +
          kObservationDurationLowerBound),
      /*delay_based_estimate=*/estimate + DataRate::KilobitsPerSec(10),
      /*in_alr=*/false);
  EXPECT_EQ(loss_based_bandwidth_estimator.GetLossBasedResult().state,
            LossBasedState::kDelayBasedEstimate);
  EXPECT_EQ(
      loss_based_bandwidth_estimator.GetLossBasedResult().bandwidth_estimate,
      estimate + DataRate::KilobitsPerSec(10));
}

TEST_F(LossBasedBweV2Test, UseByteLossRate) {
  ExplicitKeyValueConfig key_value_config(
      ShortObservationConfig("UseByteLossRate:true"));
  LossBasedBweV2 loss_based_bandwidth_estimator(&key_value_config);
  loss_based_bandwidth_estimator.SetBandwidthEstimate(
      DataRate::KilobitsPerSec(500));
  // Create packet feedback having 10% packet loss but more than 50% byte loss.
  loss_based_bandwidth_estimator.UpdateBandwidthEstimate(
      CreatePacketResultsWith10pPacketLossRate(
          /*first_packet_timestamp=*/Timestamp::Zero(),
          /*lost_packet_size=*/DataSize::Bytes(kPacketSize * 20)),
      /*delay_based_estimate=*/DataRate::PlusInfinity(),
      /*in_alr=*/false);
  EXPECT_EQ(loss_based_bandwidth_estimator.GetLossBasedResult().state,
            LossBasedState::kDecreasing);
  // The estimate is bounded by the instant upper bound due to high loss.
  EXPECT_LT(
      loss_based_bandwidth_estimator.GetLossBasedResult().bandwidth_estimate,
      DataRate::KilobitsPerSec(150));
}

TEST_F(LossBasedBweV2Test, PaceAtLossBasedEstimate) {
  ExplicitKeyValueConfig key_value_config(ShortObservationConfig(
      "PaceAtLossBasedEstimate:true,PaddingDuration:1000ms"));
  LossBasedBweV2 loss_based_bandwidth_estimator(&key_value_config);
  loss_based_bandwidth_estimator.SetBandwidthEstimate(
      DataRate::KilobitsPerSec(1000));
  loss_based_bandwidth_estimator.UpdateBandwidthEstimate(
      CreatePacketResultsWithReceivedPackets(
          /*first_packet_timestamp=*/Timestamp::Zero()),
      /*delay_based_estimate=*/DataRate::KilobitsPerSec(1000),
      /*in_alr=*/false);
  EXPECT_EQ(loss_based_bandwidth_estimator.GetLossBasedResult().state,
            LossBasedState::kDelayBasedEstimate);
  EXPECT_FALSE(loss_based_bandwidth_estimator.PaceAtLossBasedEstimate());

  loss_based_bandwidth_estimator.UpdateBandwidthEstimate(
      CreatePacketResultsWith100pLossRate(
          /*first_packet_timestamp=*/Timestamp::Zero() +
          kObservationDurationLowerBound),
      /*delay_based_estimate=*/DataRate::KilobitsPerSec(1000),
      /*in_alr=*/false);
  EXPECT_EQ(loss_based_bandwidth_estimator.GetLossBasedResult().state,
            LossBasedState::kDecreasing);
  EXPECT_TRUE(loss_based_bandwidth_estimator.PaceAtLossBasedEstimate());

  loss_based_bandwidth_estimator.UpdateBandwidthEstimate(
      CreatePacketResultsWithReceivedPackets(
          /*first_packet_timestamp=*/Timestamp::Zero() +
          kObservationDurationLowerBound * 2),
      /*delay_based_estimate=*/DataRate::KilobitsPerSec(1000),
      /*in_alr=*/false);
  EXPECT_EQ(loss_based_bandwidth_estimator.GetLossBasedResult().state,
            LossBasedState::kIncreaseUsingPadding);
  EXPECT_TRUE(loss_based_bandwidth_estimator.PaceAtLossBasedEstimate());
}

}  // namespace
}  // namespace webrtc
