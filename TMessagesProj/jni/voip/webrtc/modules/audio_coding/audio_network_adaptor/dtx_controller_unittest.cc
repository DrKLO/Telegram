/*
 *  Copyright (c) 2016 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "modules/audio_coding/audio_network_adaptor/dtx_controller.h"

#include <memory>

#include "test/gtest.h"

namespace webrtc {

namespace {

constexpr int kDtxEnablingBandwidthBps = 55000;
constexpr int kDtxDisablingBandwidthBps = 65000;
constexpr int kMediumBandwidthBps =
    (kDtxEnablingBandwidthBps + kDtxDisablingBandwidthBps) / 2;

std::unique_ptr<DtxController> CreateController(int initial_dtx_enabled) {
  std::unique_ptr<DtxController> controller(new DtxController(
      DtxController::Config(initial_dtx_enabled, kDtxEnablingBandwidthBps,
                            kDtxDisablingBandwidthBps)));
  return controller;
}

void CheckDecision(DtxController* controller,
                   const absl::optional<int>& uplink_bandwidth_bps,
                   bool expected_dtx_enabled) {
  if (uplink_bandwidth_bps) {
    Controller::NetworkMetrics network_metrics;
    network_metrics.uplink_bandwidth_bps = uplink_bandwidth_bps;
    controller->UpdateNetworkMetrics(network_metrics);
  }
  AudioEncoderRuntimeConfig config;
  controller->MakeDecision(&config);
  EXPECT_EQ(expected_dtx_enabled, config.enable_dtx);
}

}  // namespace

TEST(DtxControllerTest, OutputInitValueWhenUplinkBandwidthUnknown) {
  constexpr bool kInitialDtxEnabled = true;
  auto controller = CreateController(kInitialDtxEnabled);
  CheckDecision(controller.get(), absl::nullopt, kInitialDtxEnabled);
}

TEST(DtxControllerTest, TurnOnDtxForLowUplinkBandwidth) {
  auto controller = CreateController(false);
  CheckDecision(controller.get(), kDtxEnablingBandwidthBps, true);
}

TEST(DtxControllerTest, TurnOffDtxForHighUplinkBandwidth) {
  auto controller = CreateController(true);
  CheckDecision(controller.get(), kDtxDisablingBandwidthBps, false);
}

TEST(DtxControllerTest, MaintainDtxOffForMediumUplinkBandwidth) {
  auto controller = CreateController(false);
  CheckDecision(controller.get(), kMediumBandwidthBps, false);
}

TEST(DtxControllerTest, MaintainDtxOnForMediumUplinkBandwidth) {
  auto controller = CreateController(true);
  CheckDecision(controller.get(), kMediumBandwidthBps, true);
}

TEST(DtxControllerTest, CheckBehaviorOnChangingUplinkBandwidth) {
  auto controller = CreateController(false);
  CheckDecision(controller.get(), kMediumBandwidthBps, false);
  CheckDecision(controller.get(), kDtxEnablingBandwidthBps, true);
  CheckDecision(controller.get(), kMediumBandwidthBps, true);
  CheckDecision(controller.get(), kDtxDisablingBandwidthBps, false);
}

}  // namespace webrtc
