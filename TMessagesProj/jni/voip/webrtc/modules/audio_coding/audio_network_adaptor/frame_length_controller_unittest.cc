/*
 *  Copyright (c) 2016 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "modules/audio_coding/audio_network_adaptor/frame_length_controller.h"

#include <memory>
#include <utility>

#include "test/gtest.h"

namespace webrtc {

namespace {

constexpr float kFlIncreasingPacketLossFraction = 0.04f;
constexpr float kFlDecreasingPacketLossFraction = 0.05f;
constexpr int kFlIncreaseOverheadOffset = 0;
constexpr int kFlDecreaseOverheadOffset = 0;
constexpr int kMinEncoderBitrateBps = 6000;
constexpr int kPreventOveruseMarginBps = 5000;
constexpr size_t kOverheadBytesPerPacket = 20;
constexpr int kFl20msTo60msBandwidthBps = 40000;
constexpr int kFl60msTo20msBandwidthBps = 50000;
constexpr int kFl60msTo120msBandwidthBps = 30000;
constexpr int kFl120msTo60msBandwidthBps = 40000;
constexpr int kFl20msTo40msBandwidthBps = 45000;
constexpr int kFl40msTo20msBandwidthBps = 50000;
constexpr int kFl40msTo60msBandwidthBps = 40000;
constexpr int kFl60msTo40msBandwidthBps = 45000;

constexpr int kMediumBandwidthBps =
    (kFl40msTo20msBandwidthBps + kFl20msTo40msBandwidthBps) / 2;
constexpr float kMediumPacketLossFraction =
    (kFlDecreasingPacketLossFraction + kFlIncreasingPacketLossFraction) / 2;
const std::set<int> kDefaultEncoderFrameLengthsMs = {20, 40, 60, 120};

int VeryLowBitrate(int frame_length_ms) {
  return kMinEncoderBitrateBps + kPreventOveruseMarginBps +
         (kOverheadBytesPerPacket * 8 * 1000 / frame_length_ms);
}

std::unique_ptr<FrameLengthController> CreateController(
    const std::map<FrameLengthController::Config::FrameLengthChange, int>&
        frame_length_change_criteria,
    const std::set<int>& encoder_frame_lengths_ms,
    int initial_frame_length_ms) {
  std::unique_ptr<FrameLengthController> controller(
      new FrameLengthController(FrameLengthController::Config(
          encoder_frame_lengths_ms, initial_frame_length_ms,
          kMinEncoderBitrateBps, kFlIncreasingPacketLossFraction,
          kFlDecreasingPacketLossFraction, kFlIncreaseOverheadOffset,
          kFlDecreaseOverheadOffset, frame_length_change_criteria)));

  return controller;
}

std::map<FrameLengthController::Config::FrameLengthChange, int>
CreateChangeCriteriaFor20msAnd60ms() {
  return std::map<FrameLengthController::Config::FrameLengthChange, int>{
      {FrameLengthController::Config::FrameLengthChange(20, 60),
       kFl20msTo60msBandwidthBps},
      {FrameLengthController::Config::FrameLengthChange(60, 20),
       kFl60msTo20msBandwidthBps}};
}

std::map<FrameLengthController::Config::FrameLengthChange, int>
CreateChangeCriteriaFor20msAnd40ms() {
  return std::map<FrameLengthController::Config::FrameLengthChange, int>{
      {FrameLengthController::Config::FrameLengthChange(20, 40),
       kFl20msTo40msBandwidthBps},
      {FrameLengthController::Config::FrameLengthChange(40, 20),
       kFl40msTo20msBandwidthBps}};
}

std::map<FrameLengthController::Config::FrameLengthChange, int>
CreateChangeCriteriaFor20ms60msAnd120ms() {
  return std::map<FrameLengthController::Config::FrameLengthChange, int>{
      {FrameLengthController::Config::FrameLengthChange(20, 60),
       kFl20msTo60msBandwidthBps},
      {FrameLengthController::Config::FrameLengthChange(60, 20),
       kFl60msTo20msBandwidthBps},
      {FrameLengthController::Config::FrameLengthChange(60, 120),
       kFl60msTo120msBandwidthBps},
      {FrameLengthController::Config::FrameLengthChange(120, 60),
       kFl120msTo60msBandwidthBps}};
}

std::map<FrameLengthController::Config::FrameLengthChange, int>
CreateChangeCriteriaFor20ms40ms60msAnd120ms() {
  return std::map<FrameLengthController::Config::FrameLengthChange, int>{
      {FrameLengthController::Config::FrameLengthChange(20, 60),
       kFl20msTo60msBandwidthBps},
      {FrameLengthController::Config::FrameLengthChange(60, 20),
       kFl60msTo20msBandwidthBps},
      {FrameLengthController::Config::FrameLengthChange(20, 40),
       kFl20msTo40msBandwidthBps},
      {FrameLengthController::Config::FrameLengthChange(40, 20),
       kFl40msTo20msBandwidthBps},
      {FrameLengthController::Config::FrameLengthChange(40, 60),
       kFl40msTo60msBandwidthBps},
      {FrameLengthController::Config::FrameLengthChange(60, 40),
       kFl60msTo40msBandwidthBps},
      {FrameLengthController::Config::FrameLengthChange(60, 120),
       kFl60msTo120msBandwidthBps},
      {FrameLengthController::Config::FrameLengthChange(120, 60),
       kFl120msTo60msBandwidthBps}};
}

std::map<FrameLengthController::Config::FrameLengthChange, int>
CreateChangeCriteriaFor40msAnd60ms() {
  return std::map<FrameLengthController::Config::FrameLengthChange, int>{
      {FrameLengthController::Config::FrameLengthChange(40, 60),
       kFl40msTo60msBandwidthBps},
      {FrameLengthController::Config::FrameLengthChange(60, 40),
       kFl60msTo40msBandwidthBps}};
}

void UpdateNetworkMetrics(
    FrameLengthController* controller,
    const absl::optional<int>& uplink_bandwidth_bps,
    const absl::optional<float>& uplink_packet_loss_fraction,
    const absl::optional<size_t>& overhead_bytes_per_packet) {
  // UpdateNetworkMetrics can accept multiple network metric updates at once.
  // However, currently, the most used case is to update one metric at a time.
  // To reflect this fact, we separate the calls.
  if (uplink_bandwidth_bps) {
    Controller::NetworkMetrics network_metrics;
    network_metrics.uplink_bandwidth_bps = uplink_bandwidth_bps;
    controller->UpdateNetworkMetrics(network_metrics);
  }
  if (uplink_packet_loss_fraction) {
    Controller::NetworkMetrics network_metrics;
    network_metrics.uplink_packet_loss_fraction = uplink_packet_loss_fraction;
    controller->UpdateNetworkMetrics(network_metrics);
  }
  if (overhead_bytes_per_packet) {
    Controller::NetworkMetrics network_metrics;
    network_metrics.overhead_bytes_per_packet = overhead_bytes_per_packet;
    controller->UpdateNetworkMetrics(network_metrics);
  }
}

void CheckDecision(FrameLengthController* controller,
                   int expected_frame_length_ms) {
  AudioEncoderRuntimeConfig config;
  controller->MakeDecision(&config);
  EXPECT_EQ(expected_frame_length_ms, config.frame_length_ms);
}

}  // namespace

TEST(FrameLengthControllerTest, DecreaseTo20MsOnHighUplinkBandwidth) {
  auto controller = CreateController(CreateChangeCriteriaFor20msAnd60ms(),
                                     kDefaultEncoderFrameLengthsMs, 60);
  UpdateNetworkMetrics(controller.get(), kFl60msTo20msBandwidthBps,
                       absl::nullopt, kOverheadBytesPerPacket);
  CheckDecision(controller.get(), 20);
}

TEST(FrameLengthControllerTest, DecreaseTo20MsOnHighUplinkPacketLossFraction) {
  auto controller = CreateController(CreateChangeCriteriaFor20msAnd60ms(),
                                     kDefaultEncoderFrameLengthsMs, 60);
  UpdateNetworkMetrics(controller.get(), absl::nullopt,
                       kFlDecreasingPacketLossFraction,
                       kOverheadBytesPerPacket);
  CheckDecision(controller.get(), 20);
}

TEST(FrameLengthControllerTest,
     Maintain60MsIf20MsNotInReceiverFrameLengthRange) {
  auto controller =
      CreateController(CreateChangeCriteriaFor20msAnd60ms(), {60}, 60);
  // Set FEC on that would cause frame length to decrease if receiver frame
  // length range included 20ms.
  CheckDecision(controller.get(), 60);
}

TEST(FrameLengthControllerTest, IncreaseTo40MsOnMultipleConditions) {
  // Increase to 40ms frame length if
  // 1. `uplink_bandwidth_bps` is known to be smaller than a threshold AND
  // 2. `uplink_packet_loss_fraction` is known to be smaller than a threshold
  //    AND
  // 3. FEC is not decided or OFF.
  auto controller = CreateController(CreateChangeCriteriaFor20msAnd40ms(),
                                     kDefaultEncoderFrameLengthsMs, 20);
  UpdateNetworkMetrics(controller.get(), kFl20msTo40msBandwidthBps,
                       kFlIncreasingPacketLossFraction,
                       kOverheadBytesPerPacket);
  CheckDecision(controller.get(), 40);
}

TEST(FrameLengthControllerTest, DecreaseTo40MsOnHighUplinkBandwidth) {
  auto controller = CreateController(CreateChangeCriteriaFor40msAnd60ms(),
                                     kDefaultEncoderFrameLengthsMs, 40);
  UpdateNetworkMetrics(controller.get(), kFl60msTo40msBandwidthBps,
                       absl::nullopt, kOverheadBytesPerPacket);
  CheckDecision(controller.get(), 40);
}

TEST(FrameLengthControllerTest, Maintain60MsOnMultipleConditions) {
  // Maintain 60ms frame length if
  // 1. `uplink_bandwidth_bps` is at medium level,
  // 2. `uplink_packet_loss_fraction` is at medium,
  // 3. FEC is not decided ON.
  auto controller = CreateController(CreateChangeCriteriaFor20msAnd60ms(),
                                     kDefaultEncoderFrameLengthsMs, 60);
  UpdateNetworkMetrics(controller.get(), kMediumBandwidthBps,
                       kMediumPacketLossFraction, kOverheadBytesPerPacket);
  CheckDecision(controller.get(), 60);
}

TEST(FrameLengthControllerTest, IncreaseTo60MsOnMultipleConditions) {
  // Increase to 60ms frame length if
  // 1. `uplink_bandwidth_bps` is known to be smaller than a threshold AND
  // 2. `uplink_packet_loss_fraction` is known to be smaller than a threshold
  //    AND
  // 3. FEC is not decided or OFF.
  auto controller = CreateController(CreateChangeCriteriaFor20msAnd60ms(),
                                     kDefaultEncoderFrameLengthsMs, 20);
  UpdateNetworkMetrics(controller.get(), kFl20msTo60msBandwidthBps,
                       kFlIncreasingPacketLossFraction,
                       kOverheadBytesPerPacket);
  CheckDecision(controller.get(), 60);
}

TEST(FrameLengthControllerTest, IncreaseTo60MsOnVeryLowUplinkBandwidth) {
  auto controller = CreateController(CreateChangeCriteriaFor20msAnd60ms(),
                                     kDefaultEncoderFrameLengthsMs, 20);
  // We set packet loss fraction to kFlDecreasingPacketLossFraction, which
  // should have prevented frame length to increase, if the uplink bandwidth
  // was not this low.
  UpdateNetworkMetrics(controller.get(), VeryLowBitrate(20),
                       kFlIncreasingPacketLossFraction,
                       kOverheadBytesPerPacket);
  CheckDecision(controller.get(), 60);
}

TEST(FrameLengthControllerTest, Maintain60MsOnVeryLowUplinkBandwidth) {
  auto controller = CreateController(CreateChangeCriteriaFor20msAnd60ms(),
                                     kDefaultEncoderFrameLengthsMs, 60);
  // We set packet loss fraction to FlDecreasingPacketLossFraction, which should
  // have caused the frame length to decrease, if the uplink bandwidth was not
  // this low.
  UpdateNetworkMetrics(controller.get(), VeryLowBitrate(20),
                       kFlIncreasingPacketLossFraction,
                       kOverheadBytesPerPacket);
  CheckDecision(controller.get(), 60);
}

TEST(FrameLengthControllerTest, UpdateMultipleNetworkMetricsAtOnce) {
  // This test is similar to IncreaseTo60MsOnMultipleConditions. But instead of
  // using ::UpdateNetworkMetrics(...), which calls
  // FrameLengthController::UpdateNetworkMetrics(...) multiple times, we
  // we call it only once. This is to verify that
  // FrameLengthController::UpdateNetworkMetrics(...) can handle multiple
  // network updates at once. This is, however, not a common use case in current
  // audio_network_adaptor_impl.cc.
  auto controller = CreateController(CreateChangeCriteriaFor20msAnd60ms(),
                                     kDefaultEncoderFrameLengthsMs, 20);
  Controller::NetworkMetrics network_metrics;
  network_metrics.uplink_bandwidth_bps = kFl20msTo60msBandwidthBps;
  network_metrics.uplink_packet_loss_fraction = kFlIncreasingPacketLossFraction;
  controller->UpdateNetworkMetrics(network_metrics);
  CheckDecision(controller.get(), 60);
}

TEST(FrameLengthControllerTest,
     Maintain20MsIf60MsNotInReceiverFrameLengthRange) {
  auto controller =
      CreateController(CreateChangeCriteriaFor20msAnd60ms(), {20}, 20);
  // Use a low uplink bandwidth and a low uplink packet loss fraction that would
  // cause frame length to increase if receiver frame length included 60ms.
  UpdateNetworkMetrics(controller.get(), kFl20msTo60msBandwidthBps,
                       kFlIncreasingPacketLossFraction,
                       kOverheadBytesPerPacket);
  CheckDecision(controller.get(), 20);
}

TEST(FrameLengthControllerTest, Maintain20MsOnMediumUplinkBandwidth) {
  auto controller = CreateController(CreateChangeCriteriaFor20msAnd60ms(),
                                     kDefaultEncoderFrameLengthsMs, 20);
  UpdateNetworkMetrics(controller.get(), kMediumBandwidthBps,
                       kFlIncreasingPacketLossFraction,
                       kOverheadBytesPerPacket);
  CheckDecision(controller.get(), 20);
}

TEST(FrameLengthControllerTest, Maintain20MsOnMediumUplinkPacketLossFraction) {
  auto controller = CreateController(CreateChangeCriteriaFor20msAnd60ms(),
                                     kDefaultEncoderFrameLengthsMs, 20);
  // Use a low uplink bandwidth that would cause frame length to increase if
  // uplink packet loss fraction was low.
  UpdateNetworkMetrics(controller.get(), kFl20msTo60msBandwidthBps,
                       kMediumPacketLossFraction, kOverheadBytesPerPacket);
  CheckDecision(controller.get(), 20);
}

TEST(FrameLengthControllerTest, Maintain60MsWhenNo120msCriteriaIsSet) {
  auto controller = CreateController(CreateChangeCriteriaFor20msAnd60ms(),
                                     kDefaultEncoderFrameLengthsMs, 60);
  UpdateNetworkMetrics(controller.get(), kFl60msTo120msBandwidthBps,
                       kFlIncreasingPacketLossFraction,
                       kOverheadBytesPerPacket);
  CheckDecision(controller.get(), 60);
}

TEST(FrameLengthControllerTest, From120MsTo20MsOnHighUplinkBandwidth) {
  auto controller = CreateController(CreateChangeCriteriaFor20ms60msAnd120ms(),
                                     kDefaultEncoderFrameLengthsMs, 120);
  // It takes two steps for frame length to go from 120ms to 20ms.
  UpdateNetworkMetrics(controller.get(), kFl60msTo20msBandwidthBps,
                       absl::nullopt, kOverheadBytesPerPacket);
  CheckDecision(controller.get(), 60);

  UpdateNetworkMetrics(controller.get(), kFl60msTo20msBandwidthBps,
                       absl::nullopt, kOverheadBytesPerPacket);
  CheckDecision(controller.get(), 20);
}

TEST(FrameLengthControllerTest, From120MsTo20MsOnHighUplinkPacketLossFraction) {
  auto controller = CreateController(CreateChangeCriteriaFor20ms60msAnd120ms(),
                                     kDefaultEncoderFrameLengthsMs, 120);
  // It takes two steps for frame length to go from 120ms to 20ms.
  UpdateNetworkMetrics(controller.get(), absl::nullopt,
                       kFlDecreasingPacketLossFraction,
                       kOverheadBytesPerPacket);
  CheckDecision(controller.get(), 60);

  UpdateNetworkMetrics(controller.get(), absl::nullopt,
                       kFlDecreasingPacketLossFraction,
                       kOverheadBytesPerPacket);
  CheckDecision(controller.get(), 20);
}

TEST(FrameLengthControllerTest, Maintain120MsOnVeryLowUplinkBandwidth) {
  auto controller = CreateController(CreateChangeCriteriaFor20ms60msAnd120ms(),
                                     kDefaultEncoderFrameLengthsMs, 120);
  // We set packet loss fraction to FlDecreasingPacketLossFraction, which should
  // have caused the frame length to decrease, if the uplink bandwidth was not
  // this low.
  UpdateNetworkMetrics(controller.get(), VeryLowBitrate(60),
                       kFlDecreasingPacketLossFraction,
                       kOverheadBytesPerPacket);
  CheckDecision(controller.get(), 120);
}

TEST(FrameLengthControllerTest, From60MsTo120MsOnVeryLowUplinkBandwidth) {
  auto controller = CreateController(CreateChangeCriteriaFor20ms60msAnd120ms(),
                                     kDefaultEncoderFrameLengthsMs, 60);
  // We set packet loss fraction to FlDecreasingPacketLossFraction, which should
  // have prevented frame length to increase, if the uplink bandwidth was not
  // this low.
  UpdateNetworkMetrics(controller.get(), VeryLowBitrate(60),
                       kFlDecreasingPacketLossFraction,
                       kOverheadBytesPerPacket);
  CheckDecision(controller.get(), 120);
}

TEST(FrameLengthControllerTest, From20MsTo120MsOnMultipleConditions) {
  // Increase to 120ms frame length if
  // 1. `uplink_bandwidth_bps` is known to be smaller than a threshold AND
  // 2. `uplink_packet_loss_fraction` is known to be smaller than a threshold.
  auto controller = CreateController(CreateChangeCriteriaFor20ms60msAnd120ms(),
                                     kDefaultEncoderFrameLengthsMs, 20);
  // It takes two steps for frame length to go from 20ms to 120ms.
  UpdateNetworkMetrics(controller.get(), kFl60msTo120msBandwidthBps,
                       kFlIncreasingPacketLossFraction,
                       kOverheadBytesPerPacket);
  CheckDecision(controller.get(), 60);
  UpdateNetworkMetrics(controller.get(), kFl60msTo120msBandwidthBps,
                       kFlIncreasingPacketLossFraction,
                       kOverheadBytesPerPacket);
  CheckDecision(controller.get(), 120);
}

TEST(FrameLengthControllerTest, Stall60MsIf120MsNotInReceiverFrameLengthRange) {
  auto controller =
      CreateController(CreateChangeCriteriaFor20ms60msAnd120ms(), {20, 60}, 20);
  UpdateNetworkMetrics(controller.get(), kFl60msTo120msBandwidthBps,
                       kFlIncreasingPacketLossFraction,
                       kOverheadBytesPerPacket);
  CheckDecision(controller.get(), 60);
  UpdateNetworkMetrics(controller.get(), kFl60msTo120msBandwidthBps,
                       kFlIncreasingPacketLossFraction,
                       kOverheadBytesPerPacket);
  CheckDecision(controller.get(), 60);
}

TEST(FrameLengthControllerTest, CheckBehaviorOnChangingNetworkMetrics) {
  auto controller =
      CreateController(CreateChangeCriteriaFor20ms40ms60msAnd120ms(),
                       kDefaultEncoderFrameLengthsMs, 20);
  UpdateNetworkMetrics(controller.get(), kMediumBandwidthBps,
                       kFlIncreasingPacketLossFraction,
                       kOverheadBytesPerPacket);
  CheckDecision(controller.get(), 20);

  UpdateNetworkMetrics(controller.get(), kFl20msTo40msBandwidthBps,
                       kFlIncreasingPacketLossFraction,
                       kOverheadBytesPerPacket);
  CheckDecision(controller.get(), 40);

  UpdateNetworkMetrics(controller.get(), kFl60msTo40msBandwidthBps,
                       kMediumPacketLossFraction, kOverheadBytesPerPacket);
  CheckDecision(controller.get(), 40);

  UpdateNetworkMetrics(controller.get(), kFl20msTo60msBandwidthBps,
                       kFlIncreasingPacketLossFraction,
                       kOverheadBytesPerPacket);
  CheckDecision(controller.get(), 60);

  UpdateNetworkMetrics(controller.get(), kFl60msTo120msBandwidthBps,
                       kMediumPacketLossFraction, kOverheadBytesPerPacket);
  CheckDecision(controller.get(), 60);

  UpdateNetworkMetrics(controller.get(), kFl60msTo120msBandwidthBps,
                       kFlIncreasingPacketLossFraction,
                       kOverheadBytesPerPacket);
  CheckDecision(controller.get(), 120);

  UpdateNetworkMetrics(controller.get(), kFl120msTo60msBandwidthBps,
                       kFlIncreasingPacketLossFraction,
                       kOverheadBytesPerPacket);
  CheckDecision(controller.get(), 60);

  UpdateNetworkMetrics(controller.get(), kFl60msTo40msBandwidthBps,
                       kFlDecreasingPacketLossFraction,
                       kOverheadBytesPerPacket);
  CheckDecision(controller.get(), 40);

  UpdateNetworkMetrics(controller.get(), kMediumBandwidthBps,
                       kFlDecreasingPacketLossFraction,
                       kOverheadBytesPerPacket);
  CheckDecision(controller.get(), 20);
}

}  // namespace webrtc
