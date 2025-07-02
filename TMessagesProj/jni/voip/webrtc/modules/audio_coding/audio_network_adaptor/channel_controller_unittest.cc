/*
 *  Copyright (c) 2016 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "modules/audio_coding/audio_network_adaptor/channel_controller.h"

#include <memory>

#include "test/gtest.h"

namespace webrtc {

namespace {

constexpr int kNumChannels = 2;
constexpr int kChannel1To2BandwidthBps = 31000;
constexpr int kChannel2To1BandwidthBps = 29000;
constexpr int kMediumBandwidthBps =
    (kChannel1To2BandwidthBps + kChannel2To1BandwidthBps) / 2;

std::unique_ptr<ChannelController> CreateChannelController(int init_channels) {
  std::unique_ptr<ChannelController> controller(
      new ChannelController(ChannelController::Config(
          kNumChannels, init_channels, kChannel1To2BandwidthBps,
          kChannel2To1BandwidthBps)));
  return controller;
}

void CheckDecision(ChannelController* controller,
                   const absl::optional<int>& uplink_bandwidth_bps,
                   size_t expected_num_channels) {
  if (uplink_bandwidth_bps) {
    Controller::NetworkMetrics network_metrics;
    network_metrics.uplink_bandwidth_bps = uplink_bandwidth_bps;
    controller->UpdateNetworkMetrics(network_metrics);
  }
  AudioEncoderRuntimeConfig config;
  controller->MakeDecision(&config);
  EXPECT_EQ(expected_num_channels, config.num_channels);
}

}  // namespace

TEST(ChannelControllerTest, OutputInitValueWhenUplinkBandwidthUnknown) {
  constexpr int kInitChannels = 2;
  auto controller = CreateChannelController(kInitChannels);
  CheckDecision(controller.get(), absl::nullopt, kInitChannels);
}

TEST(ChannelControllerTest, SwitchTo2ChannelsOnHighUplinkBandwidth) {
  constexpr int kInitChannels = 1;
  auto controller = CreateChannelController(kInitChannels);
  // Use high bandwidth to check output switch to 2.
  CheckDecision(controller.get(), kChannel1To2BandwidthBps, 2);
}

TEST(ChannelControllerTest, SwitchTo1ChannelOnLowUplinkBandwidth) {
  constexpr int kInitChannels = 2;
  auto controller = CreateChannelController(kInitChannels);
  // Use low bandwidth to check output switch to 1.
  CheckDecision(controller.get(), kChannel2To1BandwidthBps, 1);
}

TEST(ChannelControllerTest, Maintain1ChannelOnMediumUplinkBandwidth) {
  constexpr int kInitChannels = 1;
  auto controller = CreateChannelController(kInitChannels);
  // Use between-thresholds bandwidth to check output remains at 1.
  CheckDecision(controller.get(), kMediumBandwidthBps, 1);
}

TEST(ChannelControllerTest, Maintain2ChannelsOnMediumUplinkBandwidth) {
  constexpr int kInitChannels = 2;
  auto controller = CreateChannelController(kInitChannels);
  // Use between-thresholds bandwidth to check output remains at 2.
  CheckDecision(controller.get(), kMediumBandwidthBps, 2);
}

TEST(ChannelControllerTest, CheckBehaviorOnChangingUplinkBandwidth) {
  constexpr int kInitChannels = 1;
  auto controller = CreateChannelController(kInitChannels);

  // Use between-thresholds bandwidth to check output remains at 1.
  CheckDecision(controller.get(), kMediumBandwidthBps, 1);

  // Use high bandwidth to check output switch to 2.
  CheckDecision(controller.get(), kChannel1To2BandwidthBps, 2);

  // Use between-thresholds bandwidth to check output remains at 2.
  CheckDecision(controller.get(), kMediumBandwidthBps, 2);

  // Use low bandwidth to check output switch to 1.
  CheckDecision(controller.get(), kChannel2To1BandwidthBps, 1);
}

}  // namespace webrtc
