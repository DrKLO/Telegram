/*
 *  Copyright (c) 2016 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "modules/audio_coding/audio_network_adaptor/audio_network_adaptor_impl.h"

#include <utility>
#include <vector>

#include "api/rtc_event_log/rtc_event.h"
#include "logging/rtc_event_log/events/rtc_event_audio_network_adaptation.h"
#include "logging/rtc_event_log/mock/mock_rtc_event_log.h"
#include "modules/audio_coding/audio_network_adaptor/mock/mock_controller.h"
#include "modules/audio_coding/audio_network_adaptor/mock/mock_controller_manager.h"
#include "modules/audio_coding/audio_network_adaptor/mock/mock_debug_dump_writer.h"
#include "rtc_base/fake_clock.h"
#include "test/field_trial.h"
#include "test/gtest.h"

namespace webrtc {

using ::testing::_;
using ::testing::NiceMock;
using ::testing::Return;
using ::testing::SetArgPointee;

namespace {

constexpr size_t kNumControllers = 2;

constexpr int64_t kClockInitialTimeMs = 12345678;

MATCHER_P(NetworkMetricsIs, metric, "") {
  return arg.uplink_bandwidth_bps == metric.uplink_bandwidth_bps &&
         arg.target_audio_bitrate_bps == metric.target_audio_bitrate_bps &&
         arg.rtt_ms == metric.rtt_ms &&
         arg.overhead_bytes_per_packet == metric.overhead_bytes_per_packet &&
         arg.uplink_packet_loss_fraction == metric.uplink_packet_loss_fraction;
}

MATCHER_P(IsRtcEventAnaConfigEqualTo, config, "") {
  if (arg->GetType() != RtcEvent::Type::AudioNetworkAdaptation) {
    return false;
  }
  auto ana_event = static_cast<RtcEventAudioNetworkAdaptation*>(arg);
  return ana_event->config() == config;
}

MATCHER_P(EncoderRuntimeConfigIs, config, "") {
  return arg.bitrate_bps == config.bitrate_bps &&
         arg.frame_length_ms == config.frame_length_ms &&
         arg.uplink_packet_loss_fraction ==
             config.uplink_packet_loss_fraction &&
         arg.enable_fec == config.enable_fec &&
         arg.enable_dtx == config.enable_dtx &&
         arg.num_channels == config.num_channels;
}

struct AudioNetworkAdaptorStates {
  std::unique_ptr<AudioNetworkAdaptorImpl> audio_network_adaptor;
  std::vector<std::unique_ptr<MockController>> mock_controllers;
  std::unique_ptr<MockRtcEventLog> event_log;
  MockDebugDumpWriter* mock_debug_dump_writer;
};

AudioNetworkAdaptorStates CreateAudioNetworkAdaptor() {
  AudioNetworkAdaptorStates states;
  std::vector<Controller*> controllers;
  for (size_t i = 0; i < kNumControllers; ++i) {
    auto controller =
        std::unique_ptr<MockController>(new NiceMock<MockController>());
    EXPECT_CALL(*controller, Die());
    controllers.push_back(controller.get());
    states.mock_controllers.push_back(std::move(controller));
  }

  auto controller_manager = std::unique_ptr<MockControllerManager>(
      new NiceMock<MockControllerManager>());

  EXPECT_CALL(*controller_manager, Die());
  EXPECT_CALL(*controller_manager, GetControllers())
      .WillRepeatedly(Return(controllers));
  EXPECT_CALL(*controller_manager, GetSortedControllers(_))
      .WillRepeatedly(Return(controllers));

  states.event_log.reset(new NiceMock<MockRtcEventLog>());

  auto debug_dump_writer =
      std::unique_ptr<MockDebugDumpWriter>(new NiceMock<MockDebugDumpWriter>());
  EXPECT_CALL(*debug_dump_writer, Die());
  states.mock_debug_dump_writer = debug_dump_writer.get();

  AudioNetworkAdaptorImpl::Config config;
  config.event_log = states.event_log.get();
  // AudioNetworkAdaptorImpl governs the lifetime of controller manager.
  states.audio_network_adaptor.reset(new AudioNetworkAdaptorImpl(
      config, std::move(controller_manager), std::move(debug_dump_writer)));

  return states;
}

void SetExpectCallToUpdateNetworkMetrics(
    const std::vector<std::unique_ptr<MockController>>& controllers,
    const Controller::NetworkMetrics& check) {
  for (auto& mock_controller : controllers) {
    EXPECT_CALL(*mock_controller,
                UpdateNetworkMetrics(NetworkMetricsIs(check)));
  }
}

}  // namespace

TEST(AudioNetworkAdaptorImplTest,
     UpdateNetworkMetricsIsCalledOnSetUplinkBandwidth) {
  auto states = CreateAudioNetworkAdaptor();
  constexpr int kBandwidth = 16000;
  Controller::NetworkMetrics check;
  check.uplink_bandwidth_bps = kBandwidth;
  SetExpectCallToUpdateNetworkMetrics(states.mock_controllers, check);
  states.audio_network_adaptor->SetUplinkBandwidth(kBandwidth);
}

TEST(AudioNetworkAdaptorImplTest,
     UpdateNetworkMetricsIsCalledOnSetUplinkPacketLossFraction) {
  auto states = CreateAudioNetworkAdaptor();
  constexpr float kPacketLoss = 0.7f;
  Controller::NetworkMetrics check;
  check.uplink_packet_loss_fraction = kPacketLoss;
  SetExpectCallToUpdateNetworkMetrics(states.mock_controllers, check);
  states.audio_network_adaptor->SetUplinkPacketLossFraction(kPacketLoss);
}

TEST(AudioNetworkAdaptorImplTest, UpdateNetworkMetricsIsCalledOnSetRtt) {
  auto states = CreateAudioNetworkAdaptor();
  constexpr int kRtt = 100;
  Controller::NetworkMetrics check;
  check.rtt_ms = kRtt;
  SetExpectCallToUpdateNetworkMetrics(states.mock_controllers, check);
  states.audio_network_adaptor->SetRtt(kRtt);
}

TEST(AudioNetworkAdaptorImplTest,
     UpdateNetworkMetricsIsCalledOnSetTargetAudioBitrate) {
  auto states = CreateAudioNetworkAdaptor();
  constexpr int kTargetAudioBitrate = 15000;
  Controller::NetworkMetrics check;
  check.target_audio_bitrate_bps = kTargetAudioBitrate;
  SetExpectCallToUpdateNetworkMetrics(states.mock_controllers, check);
  states.audio_network_adaptor->SetTargetAudioBitrate(kTargetAudioBitrate);
}

TEST(AudioNetworkAdaptorImplTest, UpdateNetworkMetricsIsCalledOnSetOverhead) {
  auto states = CreateAudioNetworkAdaptor();
  constexpr size_t kOverhead = 64;
  Controller::NetworkMetrics check;
  check.overhead_bytes_per_packet = kOverhead;
  SetExpectCallToUpdateNetworkMetrics(states.mock_controllers, check);
  states.audio_network_adaptor->SetOverhead(kOverhead);
}

TEST(AudioNetworkAdaptorImplTest,
     MakeDecisionIsCalledOnGetEncoderRuntimeConfig) {
  auto states = CreateAudioNetworkAdaptor();
  for (auto& mock_controller : states.mock_controllers)
    EXPECT_CALL(*mock_controller, MakeDecision(_));
  states.audio_network_adaptor->GetEncoderRuntimeConfig();
}

TEST(AudioNetworkAdaptorImplTest,
     DumpEncoderRuntimeConfigIsCalledOnGetEncoderRuntimeConfig) {
  test::ScopedFieldTrials override_field_trials(
      "WebRTC-Audio-FecAdaptation/Enabled/");
  rtc::ScopedFakeClock fake_clock;
  fake_clock.AdvanceTime(TimeDelta::Millis(kClockInitialTimeMs));
  auto states = CreateAudioNetworkAdaptor();
  AudioEncoderRuntimeConfig config;
  config.bitrate_bps = 32000;
  config.enable_fec = true;

  EXPECT_CALL(*states.mock_controllers[0], MakeDecision(_))
      .WillOnce(SetArgPointee<0>(config));

  EXPECT_CALL(*states.mock_debug_dump_writer,
              DumpEncoderRuntimeConfig(EncoderRuntimeConfigIs(config),
                                       kClockInitialTimeMs));
  states.audio_network_adaptor->GetEncoderRuntimeConfig();
}

TEST(AudioNetworkAdaptorImplTest,
     DumpNetworkMetricsIsCalledOnSetNetworkMetrics) {
  rtc::ScopedFakeClock fake_clock;
  fake_clock.AdvanceTime(TimeDelta::Millis(kClockInitialTimeMs));

  auto states = CreateAudioNetworkAdaptor();

  constexpr int kBandwidth = 16000;
  constexpr float kPacketLoss = 0.7f;
  constexpr int kRtt = 100;
  constexpr int kTargetAudioBitrate = 15000;
  constexpr size_t kOverhead = 64;

  Controller::NetworkMetrics check;
  check.uplink_bandwidth_bps = kBandwidth;
  int64_t timestamp_check = kClockInitialTimeMs;

  EXPECT_CALL(*states.mock_debug_dump_writer,
              DumpNetworkMetrics(NetworkMetricsIs(check), timestamp_check));
  states.audio_network_adaptor->SetUplinkBandwidth(kBandwidth);

  fake_clock.AdvanceTime(TimeDelta::Millis(100));
  timestamp_check += 100;
  check.uplink_packet_loss_fraction = kPacketLoss;
  EXPECT_CALL(*states.mock_debug_dump_writer,
              DumpNetworkMetrics(NetworkMetricsIs(check), timestamp_check));
  states.audio_network_adaptor->SetUplinkPacketLossFraction(kPacketLoss);

  fake_clock.AdvanceTime(TimeDelta::Millis(50));
  timestamp_check += 50;

  fake_clock.AdvanceTime(TimeDelta::Millis(200));
  timestamp_check += 200;
  check.rtt_ms = kRtt;
  EXPECT_CALL(*states.mock_debug_dump_writer,
              DumpNetworkMetrics(NetworkMetricsIs(check), timestamp_check));
  states.audio_network_adaptor->SetRtt(kRtt);

  fake_clock.AdvanceTime(TimeDelta::Millis(150));
  timestamp_check += 150;
  check.target_audio_bitrate_bps = kTargetAudioBitrate;
  EXPECT_CALL(*states.mock_debug_dump_writer,
              DumpNetworkMetrics(NetworkMetricsIs(check), timestamp_check));
  states.audio_network_adaptor->SetTargetAudioBitrate(kTargetAudioBitrate);

  fake_clock.AdvanceTime(TimeDelta::Millis(50));
  timestamp_check += 50;
  check.overhead_bytes_per_packet = kOverhead;
  EXPECT_CALL(*states.mock_debug_dump_writer,
              DumpNetworkMetrics(NetworkMetricsIs(check), timestamp_check));
  states.audio_network_adaptor->SetOverhead(kOverhead);
}

TEST(AudioNetworkAdaptorImplTest, LogRuntimeConfigOnGetEncoderRuntimeConfig) {
  test::ScopedFieldTrials override_field_trials(
      "WebRTC-Audio-FecAdaptation/Enabled/");
  auto states = CreateAudioNetworkAdaptor();

  AudioEncoderRuntimeConfig config;
  config.bitrate_bps = 32000;
  config.enable_fec = true;

  EXPECT_CALL(*states.mock_controllers[0], MakeDecision(_))
      .WillOnce(SetArgPointee<0>(config));

  EXPECT_CALL(*states.event_log, LogProxy(IsRtcEventAnaConfigEqualTo(config)))
      .Times(1);
  states.audio_network_adaptor->GetEncoderRuntimeConfig();
}

TEST(AudioNetworkAdaptorImplTest, TestANAStats) {
  auto states = CreateAudioNetworkAdaptor();

  // Simulate some adaptation, otherwise the stats will not show anything.
  AudioEncoderRuntimeConfig config1, config2;
  config1.bitrate_bps = 32000;
  config1.num_channels = 2;
  config1.enable_fec = true;
  config1.enable_dtx = true;
  config1.frame_length_ms = 120;
  config1.uplink_packet_loss_fraction = 0.1f;
  config2.bitrate_bps = 16000;
  config2.num_channels = 1;
  config2.enable_fec = false;
  config2.enable_dtx = false;
  config2.frame_length_ms = 60;
  config1.uplink_packet_loss_fraction = 0.1f;

  EXPECT_CALL(*states.mock_controllers[0], MakeDecision(_))
      .WillOnce(SetArgPointee<0>(config1));
  states.audio_network_adaptor->GetEncoderRuntimeConfig();
  EXPECT_CALL(*states.mock_controllers[0], MakeDecision(_))
      .WillOnce(SetArgPointee<0>(config2));
  states.audio_network_adaptor->GetEncoderRuntimeConfig();
  EXPECT_CALL(*states.mock_controllers[0], MakeDecision(_))
      .WillOnce(SetArgPointee<0>(config1));
  states.audio_network_adaptor->GetEncoderRuntimeConfig();

  auto ana_stats = states.audio_network_adaptor->GetStats();

  EXPECT_EQ(ana_stats.bitrate_action_counter, 2u);
  EXPECT_EQ(ana_stats.channel_action_counter, 2u);
  EXPECT_EQ(ana_stats.dtx_action_counter, 2u);
  EXPECT_EQ(ana_stats.fec_action_counter, 2u);
  EXPECT_EQ(ana_stats.frame_length_increase_counter, 1u);
  EXPECT_EQ(ana_stats.frame_length_decrease_counter, 1u);
  EXPECT_EQ(ana_stats.uplink_packet_loss_fraction, 0.1f);
}

}  // namespace webrtc
