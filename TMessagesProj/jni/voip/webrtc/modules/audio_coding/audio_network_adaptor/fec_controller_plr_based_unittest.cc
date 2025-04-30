/*
 *  Copyright (c) 2016 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "modules/audio_coding/audio_network_adaptor/fec_controller_plr_based.h"

#include <utility>

#include "common_audio/mocks/mock_smoothing_filter.h"
#include "test/gtest.h"

namespace webrtc {

using ::testing::_;
using ::testing::NiceMock;
using ::testing::Return;

namespace {

// The test uses the following settings:
//
// packet-loss ^   |  |
//             |  A| C|   FEC
//             |    \  \   ON
//             | FEC \ D\_______
//             | OFF B\_________
//             |-----------------> bandwidth
//
// A : (kDisablingBandwidthLow, kDisablingPacketLossAtLowBw)
// B : (kDisablingBandwidthHigh, kDisablingPacketLossAtHighBw)
// C : (kEnablingBandwidthLow, kEnablingPacketLossAtLowBw)
// D : (kEnablingBandwidthHigh, kEnablingPacketLossAtHighBw)

constexpr int kDisablingBandwidthLow = 15000;
constexpr float kDisablingPacketLossAtLowBw = 0.08f;
constexpr int kDisablingBandwidthHigh = 64000;
constexpr float kDisablingPacketLossAtHighBw = 0.01f;
constexpr int kEnablingBandwidthLow = 17000;
constexpr float kEnablingPacketLossAtLowBw = 0.1f;
constexpr int kEnablingBandwidthHigh = 64000;
constexpr float kEnablingPacketLossAtHighBw = 0.05f;

constexpr float kEpsilon = 1e-5f;

struct FecControllerPlrBasedTestStates {
  std::unique_ptr<FecControllerPlrBased> controller;
  MockSmoothingFilter* packet_loss_smoother;
};

FecControllerPlrBasedTestStates CreateFecControllerPlrBased(
    bool initial_fec_enabled,
    const ThresholdCurve& enabling_curve,
    const ThresholdCurve& disabling_curve) {
  FecControllerPlrBasedTestStates states;
  std::unique_ptr<MockSmoothingFilter> mock_smoothing_filter(
      new NiceMock<MockSmoothingFilter>());
  states.packet_loss_smoother = mock_smoothing_filter.get();
  states.controller.reset(new FecControllerPlrBased(
      FecControllerPlrBased::Config(initial_fec_enabled, enabling_curve,
                                    disabling_curve, 0),
      std::move(mock_smoothing_filter)));
  return states;
}

FecControllerPlrBasedTestStates CreateFecControllerPlrBased(
    bool initial_fec_enabled) {
  return CreateFecControllerPlrBased(
      initial_fec_enabled,
      ThresholdCurve(kEnablingBandwidthLow, kEnablingPacketLossAtLowBw,
                     kEnablingBandwidthHigh, kEnablingPacketLossAtHighBw),
      ThresholdCurve(kDisablingBandwidthLow, kDisablingPacketLossAtLowBw,
                     kDisablingBandwidthHigh, kDisablingPacketLossAtHighBw));
}

void UpdateNetworkMetrics(FecControllerPlrBasedTestStates* states,
                          const absl::optional<int>& uplink_bandwidth_bps,
                          const absl::optional<float>& uplink_packet_loss) {
  // UpdateNetworkMetrics can accept multiple network metric updates at once.
  // However, currently, the most used case is to update one metric at a time.
  // To reflect this fact, we separate the calls.
  if (uplink_bandwidth_bps) {
    Controller::NetworkMetrics network_metrics;
    network_metrics.uplink_bandwidth_bps = uplink_bandwidth_bps;
    states->controller->UpdateNetworkMetrics(network_metrics);
  }
  if (uplink_packet_loss) {
    Controller::NetworkMetrics network_metrics;
    network_metrics.uplink_packet_loss_fraction = uplink_packet_loss;
    EXPECT_CALL(*states->packet_loss_smoother, AddSample(*uplink_packet_loss));
    states->controller->UpdateNetworkMetrics(network_metrics);
    // This is called during CheckDecision().
    EXPECT_CALL(*states->packet_loss_smoother, GetAverage())
        .WillOnce(Return(*uplink_packet_loss));
  }
}

// Checks that the FEC decision and `uplink_packet_loss_fraction` given by
// `states->controller->MakeDecision` matches `expected_enable_fec` and
// `expected_uplink_packet_loss_fraction`, respectively.
void CheckDecision(FecControllerPlrBasedTestStates* states,
                   bool expected_enable_fec,
                   float expected_uplink_packet_loss_fraction) {
  AudioEncoderRuntimeConfig config;
  states->controller->MakeDecision(&config);
  EXPECT_EQ(expected_enable_fec, config.enable_fec);
  EXPECT_EQ(expected_uplink_packet_loss_fraction,
            config.uplink_packet_loss_fraction);
}

}  // namespace

TEST(FecControllerPlrBasedTest, OutputInitValueBeforeAnyInputsAreReceived) {
  for (bool initial_fec_enabled : {false, true}) {
    auto states = CreateFecControllerPlrBased(initial_fec_enabled);
    CheckDecision(&states, initial_fec_enabled, 0);
  }
}

TEST(FecControllerPlrBasedTest, OutputInitValueWhenUplinkBandwidthUnknown) {
  // Regardless of the initial FEC state and the packet-loss rate,
  // the initial FEC state is maintained as long as the BWE is unknown.
  for (bool initial_fec_enabled : {false, true}) {
    for (float packet_loss :
         {kDisablingPacketLossAtLowBw - kEpsilon, kDisablingPacketLossAtLowBw,
          kDisablingPacketLossAtLowBw + kEpsilon,
          kEnablingPacketLossAtLowBw - kEpsilon, kEnablingPacketLossAtLowBw,
          kEnablingPacketLossAtLowBw + kEpsilon}) {
      auto states = CreateFecControllerPlrBased(initial_fec_enabled);
      UpdateNetworkMetrics(&states, absl::nullopt, packet_loss);
      CheckDecision(&states, initial_fec_enabled, packet_loss);
    }
  }
}

TEST(FecControllerPlrBasedTest,
     OutputInitValueWhenUplinkPacketLossFractionUnknown) {
  // Regardless of the initial FEC state and the BWE, the initial FEC state
  // is maintained as long as the packet-loss rate is unknown.
  for (bool initial_fec_enabled : {false, true}) {
    for (int bandwidth : {kDisablingBandwidthLow - 1, kDisablingBandwidthLow,
                          kDisablingBandwidthLow + 1, kEnablingBandwidthLow - 1,
                          kEnablingBandwidthLow, kEnablingBandwidthLow + 1}) {
      auto states = CreateFecControllerPlrBased(initial_fec_enabled);
      UpdateNetworkMetrics(&states, bandwidth, absl::nullopt);
      CheckDecision(&states, initial_fec_enabled, 0.0);
    }
  }
}

TEST(FecControllerPlrBasedTest, EnableFecForHighBandwidth) {
  auto states = CreateFecControllerPlrBased(false);
  UpdateNetworkMetrics(&states, kEnablingBandwidthHigh,
                       kEnablingPacketLossAtHighBw);
  CheckDecision(&states, true, kEnablingPacketLossAtHighBw);
}

TEST(FecControllerPlrBasedTest, UpdateMultipleNetworkMetricsAtOnce) {
  // This test is similar to EnableFecForHighBandwidth. But instead of
  // using ::UpdateNetworkMetrics(...), which calls
  // FecControllerPlrBased::UpdateNetworkMetrics(...) multiple times, we
  // we call it only once. This is to verify that
  // FecControllerPlrBased::UpdateNetworkMetrics(...) can handle multiple
  // network updates at once. This is, however, not a common use case in current
  // audio_network_adaptor_impl.cc.
  auto states = CreateFecControllerPlrBased(false);
  Controller::NetworkMetrics network_metrics;
  network_metrics.uplink_bandwidth_bps = kEnablingBandwidthHigh;
  network_metrics.uplink_packet_loss_fraction = kEnablingPacketLossAtHighBw;
  EXPECT_CALL(*states.packet_loss_smoother, GetAverage())
      .WillOnce(Return(kEnablingPacketLossAtHighBw));
  states.controller->UpdateNetworkMetrics(network_metrics);
  CheckDecision(&states, true, kEnablingPacketLossAtHighBw);
}

TEST(FecControllerPlrBasedTest, MaintainFecOffForHighBandwidth) {
  auto states = CreateFecControllerPlrBased(false);
  constexpr float kPacketLoss = kEnablingPacketLossAtHighBw * 0.99f;
  UpdateNetworkMetrics(&states, kEnablingBandwidthHigh, kPacketLoss);
  CheckDecision(&states, false, kPacketLoss);
}

TEST(FecControllerPlrBasedTest, EnableFecForMediumBandwidth) {
  auto states = CreateFecControllerPlrBased(false);
  constexpr float kPacketLoss =
      (kEnablingPacketLossAtLowBw + kEnablingPacketLossAtHighBw) / 2.0;
  UpdateNetworkMetrics(&states,
                       (kEnablingBandwidthHigh + kEnablingBandwidthLow) / 2,
                       kPacketLoss);
  CheckDecision(&states, true, kPacketLoss);
}

TEST(FecControllerPlrBasedTest, MaintainFecOffForMediumBandwidth) {
  auto states = CreateFecControllerPlrBased(false);
  constexpr float kPacketLoss =
      kEnablingPacketLossAtLowBw * 0.49f + kEnablingPacketLossAtHighBw * 0.51f;
  UpdateNetworkMetrics(&states,
                       (kEnablingBandwidthHigh + kEnablingBandwidthLow) / 2,
                       kPacketLoss);
  CheckDecision(&states, false, kPacketLoss);
}

TEST(FecControllerPlrBasedTest, EnableFecForLowBandwidth) {
  auto states = CreateFecControllerPlrBased(false);
  UpdateNetworkMetrics(&states, kEnablingBandwidthLow,
                       kEnablingPacketLossAtLowBw);
  CheckDecision(&states, true, kEnablingPacketLossAtLowBw);
}

TEST(FecControllerPlrBasedTest, MaintainFecOffForLowBandwidth) {
  auto states = CreateFecControllerPlrBased(false);
  constexpr float kPacketLoss = kEnablingPacketLossAtLowBw * 0.99f;
  UpdateNetworkMetrics(&states, kEnablingBandwidthLow, kPacketLoss);
  CheckDecision(&states, false, kPacketLoss);
}

TEST(FecControllerPlrBasedTest, MaintainFecOffForVeryLowBandwidth) {
  auto states = CreateFecControllerPlrBased(false);
  // Below `kEnablingBandwidthLow`, no packet loss fraction can cause FEC to
  // turn on.
  UpdateNetworkMetrics(&states, kEnablingBandwidthLow - 1, 1.0);
  CheckDecision(&states, false, 1.0);
}

TEST(FecControllerPlrBasedTest, DisableFecForHighBandwidth) {
  auto states = CreateFecControllerPlrBased(true);
  constexpr float kPacketLoss = kDisablingPacketLossAtHighBw - kEpsilon;
  UpdateNetworkMetrics(&states, kDisablingBandwidthHigh, kPacketLoss);
  CheckDecision(&states, false, kPacketLoss);
}

TEST(FecControllerPlrBasedTest, MaintainFecOnForHighBandwidth) {
  // Note: Disabling happens when the value is strictly below the threshold.
  auto states = CreateFecControllerPlrBased(true);
  UpdateNetworkMetrics(&states, kDisablingBandwidthHigh,
                       kDisablingPacketLossAtHighBw);
  CheckDecision(&states, true, kDisablingPacketLossAtHighBw);
}

TEST(FecControllerPlrBasedTest, DisableFecOnMediumBandwidth) {
  auto states = CreateFecControllerPlrBased(true);
  constexpr float kPacketLoss =
      (kDisablingPacketLossAtLowBw + kDisablingPacketLossAtHighBw) / 2.0f -
      kEpsilon;
  UpdateNetworkMetrics(&states,
                       (kDisablingBandwidthHigh + kDisablingBandwidthLow) / 2,
                       kPacketLoss);
  CheckDecision(&states, false, kPacketLoss);
}

TEST(FecControllerPlrBasedTest, MaintainFecOnForMediumBandwidth) {
  auto states = CreateFecControllerPlrBased(true);
  constexpr float kPacketLoss = kDisablingPacketLossAtLowBw * 0.51f +
                                kDisablingPacketLossAtHighBw * 0.49f - kEpsilon;
  UpdateNetworkMetrics(&states,
                       (kEnablingBandwidthHigh + kDisablingBandwidthLow) / 2,
                       kPacketLoss);
  CheckDecision(&states, true, kPacketLoss);
}

TEST(FecControllerPlrBasedTest, DisableFecForLowBandwidth) {
  auto states = CreateFecControllerPlrBased(true);
  constexpr float kPacketLoss = kDisablingPacketLossAtLowBw - kEpsilon;
  UpdateNetworkMetrics(&states, kDisablingBandwidthLow, kPacketLoss);
  CheckDecision(&states, false, kPacketLoss);
}

TEST(FecControllerPlrBasedTest, DisableFecForVeryLowBandwidth) {
  auto states = CreateFecControllerPlrBased(true);
  // Below `kEnablingBandwidthLow`, any packet loss fraction can cause FEC to
  // turn off.
  UpdateNetworkMetrics(&states, kDisablingBandwidthLow - 1, 1.0);
  CheckDecision(&states, false, 1.0);
}

TEST(FecControllerPlrBasedTest, CheckBehaviorOnChangingNetworkMetrics) {
  // In this test, we let the network metrics to traverse from 1 to 5.
  // packet-loss ^ 1 |  |
  //             |   | 2|
  //             |    \  \ 3
  //             |     \4 \_______
  //             |      \_________
  //             |---------5-------> bandwidth

  auto states = CreateFecControllerPlrBased(true);
  UpdateNetworkMetrics(&states, kDisablingBandwidthLow - 1, 1.0);
  CheckDecision(&states, false, 1.0);

  UpdateNetworkMetrics(&states, kEnablingBandwidthLow,
                       kEnablingPacketLossAtLowBw * 0.99f);
  CheckDecision(&states, false, kEnablingPacketLossAtLowBw * 0.99f);

  UpdateNetworkMetrics(&states, kEnablingBandwidthHigh,
                       kEnablingPacketLossAtHighBw);
  CheckDecision(&states, true, kEnablingPacketLossAtHighBw);

  UpdateNetworkMetrics(&states, kDisablingBandwidthHigh,
                       kDisablingPacketLossAtHighBw);
  CheckDecision(&states, true, kDisablingPacketLossAtHighBw);

  UpdateNetworkMetrics(&states, kDisablingBandwidthHigh + 1, 0.0);
  CheckDecision(&states, false, 0.0);
}

TEST(FecControllerPlrBasedTest, CheckBehaviorOnSpecialCurves) {
  // We test a special configuration, where the points to define the FEC
  // enabling/disabling curves are placed like the following, otherwise the test
  // is the same as CheckBehaviorOnChangingNetworkMetrics.
  //
  // packet-loss ^   |  |
  //             |   | C|
  //             |   |  |
  //             |   | D|_______
  //             |  A|___B______
  //             |-----------------> bandwidth

  constexpr int kEnablingBandwidthHigh = kEnablingBandwidthLow;
  constexpr float kDisablingPacketLossAtLowBw = kDisablingPacketLossAtHighBw;
  FecControllerPlrBasedTestStates states;
  std::unique_ptr<MockSmoothingFilter> mock_smoothing_filter(
      new NiceMock<MockSmoothingFilter>());
  states.packet_loss_smoother = mock_smoothing_filter.get();
  states.controller.reset(new FecControllerPlrBased(
      FecControllerPlrBased::Config(
          true,
          ThresholdCurve(kEnablingBandwidthLow, kEnablingPacketLossAtLowBw,
                         kEnablingBandwidthHigh, kEnablingPacketLossAtHighBw),
          ThresholdCurve(kDisablingBandwidthLow, kDisablingPacketLossAtLowBw,
                         kDisablingBandwidthHigh, kDisablingPacketLossAtHighBw),
          0),
      std::move(mock_smoothing_filter)));

  UpdateNetworkMetrics(&states, kDisablingBandwidthLow - 1, 1.0);
  CheckDecision(&states, false, 1.0);

  UpdateNetworkMetrics(&states, kEnablingBandwidthLow,
                       kEnablingPacketLossAtHighBw * 0.99f);
  CheckDecision(&states, false, kEnablingPacketLossAtHighBw * 0.99f);

  UpdateNetworkMetrics(&states, kEnablingBandwidthHigh,
                       kEnablingPacketLossAtHighBw);
  CheckDecision(&states, true, kEnablingPacketLossAtHighBw);

  UpdateNetworkMetrics(&states, kDisablingBandwidthHigh,
                       kDisablingPacketLossAtHighBw);
  CheckDecision(&states, true, kDisablingPacketLossAtHighBw);

  UpdateNetworkMetrics(&states, kDisablingBandwidthHigh + 1, 0.0);
  CheckDecision(&states, false, 0.0);
}

TEST(FecControllerPlrBasedTest, SingleThresholdCurveForEnablingAndDisabling) {
  // Note: To avoid numerical errors, keep kPacketLossAtLowBw and
  // kPacketLossAthighBw as (negative) integer powers of 2.
  // This is mostly relevant for the O3 case.
  constexpr int kBandwidthLow = 10000;
  constexpr float kPacketLossAtLowBw = 0.25f;
  constexpr int kBandwidthHigh = 20000;
  constexpr float kPacketLossAtHighBw = 0.125f;
  auto curve = ThresholdCurve(kBandwidthLow, kPacketLossAtLowBw, kBandwidthHigh,
                              kPacketLossAtHighBw);

  // B* stands for "below-curve", O* for "on-curve", and A* for "above-curve".
  //
  //                                            //
  // packet-loss ^                              //
  //             |    |                         //
  //             | B1 O1                        //
  //             |    |                         //
  //             |    O2                        //
  //             |     \ A1                     //
  //             |      \                       //
  //             |       O3   A2                //
  //             |     B2 \                     //
  //             |         \                    //
  //             |          O4--O5----          //
  //             |                              //
  //             |            B3                //
  //             |-----------------> bandwidth  //

  struct NetworkState {
    int bandwidth;
    float packet_loss;
  };

  std::vector<NetworkState> below{
      {kBandwidthLow - 1, kPacketLossAtLowBw + 0.1f},  // B1
      {(kBandwidthLow + kBandwidthHigh) / 2,
       (kPacketLossAtLowBw + kPacketLossAtHighBw) / 2 - kEpsilon},  // B2
      {kBandwidthHigh + 1, kPacketLossAtHighBw - kEpsilon}          // B3
  };

  std::vector<NetworkState> on{
      {kBandwidthLow, kPacketLossAtLowBw + 0.1f},  // O1
      {kBandwidthLow, kPacketLossAtLowBw},         // O2
      {(kBandwidthLow + kBandwidthHigh) / 2,
       (kPacketLossAtLowBw + kPacketLossAtHighBw) / 2},  // O3
      {kBandwidthHigh, kPacketLossAtHighBw},             // O4
      {kBandwidthHigh + 1, kPacketLossAtHighBw},         // O5
  };

  std::vector<NetworkState> above{
      {(kBandwidthLow + kBandwidthHigh) / 2,
       (kPacketLossAtLowBw + kPacketLossAtHighBw) / 2 + kEpsilon},  // A1
      {kBandwidthHigh + 1, kPacketLossAtHighBw + kEpsilon},         // A2
  };

  // Test that FEC is turned off whenever we're below the curve, independent
  // of the starting FEC state.
  for (NetworkState net_state : below) {
    for (bool initial_fec_enabled : {false, true}) {
      auto states =
          CreateFecControllerPlrBased(initial_fec_enabled, curve, curve);
      UpdateNetworkMetrics(&states, net_state.bandwidth, net_state.packet_loss);
      CheckDecision(&states, false, net_state.packet_loss);
    }
  }

  // Test that FEC is turned on whenever we're on the curve or above it,
  // independent of the starting FEC state.
  for (const std::vector<NetworkState>& states_list : {on, above}) {
    for (NetworkState net_state : states_list) {
      for (bool initial_fec_enabled : {false, true}) {
        auto states =
            CreateFecControllerPlrBased(initial_fec_enabled, curve, curve);
        UpdateNetworkMetrics(&states, net_state.bandwidth,
                             net_state.packet_loss);
        CheckDecision(&states, true, net_state.packet_loss);
      }
    }
  }
}

TEST(FecControllerPlrBasedTest, FecAlwaysOff) {
  ThresholdCurve always_off_curve(0, 1.0f + kEpsilon, 0, 1.0f + kEpsilon);
  for (bool initial_fec_enabled : {false, true}) {
    for (int bandwidth : {0, 10000}) {
      for (float packet_loss : {0.0f, 0.5f, 1.0f}) {
        auto states = CreateFecControllerPlrBased(
            initial_fec_enabled, always_off_curve, always_off_curve);
        UpdateNetworkMetrics(&states, bandwidth, packet_loss);
        CheckDecision(&states, false, packet_loss);
      }
    }
  }
}

TEST(FecControllerPlrBasedTest, FecAlwaysOn) {
  ThresholdCurve always_on_curve(0, 0.0f, 0, 0.0f);
  for (bool initial_fec_enabled : {false, true}) {
    for (int bandwidth : {0, 10000}) {
      for (float packet_loss : {0.0f, 0.5f, 1.0f}) {
        auto states = CreateFecControllerPlrBased(
            initial_fec_enabled, always_on_curve, always_on_curve);
        UpdateNetworkMetrics(&states, bandwidth, packet_loss);
        CheckDecision(&states, true, packet_loss);
      }
    }
  }
}

#if RTC_DCHECK_IS_ON && GTEST_HAS_DEATH_TEST && !defined(WEBRTC_ANDROID)
TEST(FecControllerPlrBasedDeathTest, InvalidConfig) {
  FecControllerPlrBasedTestStates states;
  std::unique_ptr<MockSmoothingFilter> mock_smoothing_filter(
      new NiceMock<MockSmoothingFilter>());
  states.packet_loss_smoother = mock_smoothing_filter.get();
  EXPECT_DEATH(
      states.controller.reset(new FecControllerPlrBased(
          FecControllerPlrBased::Config(
              true,
              ThresholdCurve(kDisablingBandwidthLow - 1,
                             kEnablingPacketLossAtLowBw, kEnablingBandwidthHigh,
                             kEnablingPacketLossAtHighBw),
              ThresholdCurve(
                  kDisablingBandwidthLow, kDisablingPacketLossAtLowBw,
                  kDisablingBandwidthHigh, kDisablingPacketLossAtHighBw),
              0),
          std::move(mock_smoothing_filter))),
      "Check failed");
}
#endif

}  // namespace webrtc
