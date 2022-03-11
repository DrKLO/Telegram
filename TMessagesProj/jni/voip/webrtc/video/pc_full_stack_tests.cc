/*
 *  Copyright (c) 2013 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */
#include <memory>
#include <string>
#include <utility>
#include <vector>

#include "api/media_stream_interface.h"
#include "api/test/create_network_emulation_manager.h"
#include "api/test/create_peer_connection_quality_test_frame_generator.h"
#include "api/test/create_peerconnection_quality_test_fixture.h"
#include "api/test/frame_generator_interface.h"
#include "api/test/network_emulation_manager.h"
#include "api/test/peerconnection_quality_test_fixture.h"
#include "api/test/simulated_network.h"
#include "api/test/time_controller.h"
#include "api/video_codecs/vp9_profile.h"
#include "call/simulated_network.h"
#include "modules/video_coding/codecs/vp9/include/vp9.h"
#include "system_wrappers/include/field_trial.h"
#include "test/field_trial.h"
#include "test/gtest.h"
#include "test/pc/e2e/network_quality_metrics_reporter.h"
#include "test/testsupport/file_utils.h"

namespace webrtc {

using PeerConfigurer =
    webrtc_pc_e2e::PeerConnectionE2EQualityTestFixture::PeerConfigurer;
using RunParams = webrtc_pc_e2e::PeerConnectionE2EQualityTestFixture::RunParams;
using VideoConfig =
    webrtc_pc_e2e::PeerConnectionE2EQualityTestFixture::VideoConfig;
using AudioConfig =
    webrtc_pc_e2e::PeerConnectionE2EQualityTestFixture::AudioConfig;
using ScreenShareConfig =
    webrtc_pc_e2e::PeerConnectionE2EQualityTestFixture::ScreenShareConfig;
using VideoSimulcastConfig =
    webrtc_pc_e2e::PeerConnectionE2EQualityTestFixture::VideoSimulcastConfig;
using VideoCodecConfig =
    webrtc_pc_e2e::PeerConnectionE2EQualityTestFixture::VideoCodecConfig;

namespace {

constexpr int kTestDurationSec = 45;

EmulatedNetworkNode* CreateEmulatedNodeWithConfig(
    NetworkEmulationManager* emulation,
    const BuiltInNetworkBehaviorConfig& config) {
  return emulation->CreateEmulatedNode(
      std::make_unique<SimulatedNetwork>(config));
}

std::pair<EmulatedNetworkManagerInterface*, EmulatedNetworkManagerInterface*>
CreateTwoNetworkLinks(NetworkEmulationManager* emulation,
                      const BuiltInNetworkBehaviorConfig& config) {
  auto* alice_node = CreateEmulatedNodeWithConfig(emulation, config);
  auto* bob_node = CreateEmulatedNodeWithConfig(emulation, config);

  auto* alice_endpoint = emulation->CreateEndpoint(EmulatedEndpointConfig());
  auto* bob_endpoint = emulation->CreateEndpoint(EmulatedEndpointConfig());

  emulation->CreateRoute(alice_endpoint, {alice_node}, bob_endpoint);
  emulation->CreateRoute(bob_endpoint, {bob_node}, alice_endpoint);

  return {
      emulation->CreateEmulatedNetworkManagerInterface({alice_endpoint}),
      emulation->CreateEmulatedNetworkManagerInterface({bob_endpoint}),
  };
}

std::unique_ptr<webrtc_pc_e2e::PeerConnectionE2EQualityTestFixture>
CreateTestFixture(const std::string& test_case_name,
                  TimeController& time_controller,
                  std::pair<EmulatedNetworkManagerInterface*,
                            EmulatedNetworkManagerInterface*> network_links,
                  rtc::FunctionView<void(PeerConfigurer*)> alice_configurer,
                  rtc::FunctionView<void(PeerConfigurer*)> bob_configurer) {
  auto fixture = webrtc_pc_e2e::CreatePeerConnectionE2EQualityTestFixture(
      test_case_name, time_controller, /*audio_quality_analyzer=*/nullptr,
      /*video_quality_analyzer=*/nullptr);
  fixture->AddPeer(network_links.first->network_dependencies(),
                   alice_configurer);
  fixture->AddPeer(network_links.second->network_dependencies(),
                   bob_configurer);
  fixture->AddQualityMetricsReporter(
      std::make_unique<webrtc_pc_e2e::NetworkQualityMetricsReporter>(
          network_links.first, network_links.second));
  return fixture;
}

// Takes the current active field trials set, and appends some new trials.
std::string AppendFieldTrials(std::string new_trial_string) {
  return std::string(field_trial::GetFieldTrialString()) + new_trial_string;
}

std::string ClipNameToClipPath(const char* clip_name) {
  return test::ResourcePath(clip_name, "yuv");
}

}  // namespace

#if defined(RTC_ENABLE_VP9)
TEST(PCFullStackTest, Pc_Foreman_Cif_Net_Delay_0_0_Plr_0_VP9) {
  std::unique_ptr<NetworkEmulationManager> network_emulation_manager =
      CreateNetworkEmulationManager();
  auto fixture = CreateTestFixture(
      "pc_foreman_cif_net_delay_0_0_plr_0_VP9",
      *network_emulation_manager->time_controller(),
      CreateTwoNetworkLinks(network_emulation_manager.get(),
                            BuiltInNetworkBehaviorConfig()),
      [](PeerConfigurer* alice) {
        VideoConfig video(352, 288, 30);
        video.stream_label = "alice-video";
        auto frame_generator = CreateFromYuvFileFrameGenerator(
            video, ClipNameToClipPath("foreman_cif"));
        alice->AddVideoConfig(std::move(video), std::move(frame_generator));
        alice->SetVideoCodecs({VideoCodecConfig(
            /*name=*/cricket::kVp9CodecName, /*required_params=*/{
                {kVP9FmtpProfileId,
                 VP9ProfileToString(VP9Profile::kProfile0)}})});
      },
      [](PeerConfigurer* bob) {
        bob->SetVideoCodecs({VideoCodecConfig(
            /*name=*/cricket::kVp9CodecName, /*required_params=*/{
                {kVP9FmtpProfileId,
                 VP9ProfileToString(VP9Profile::kProfile0)}})});
      });
  fixture->Run(RunParams(TimeDelta::Seconds(kTestDurationSec)));
}

TEST(PCGenericDescriptorTest,
     Pc_Foreman_Cif_Delay_50_0_Plr_5_VP9_Generic_Descriptor) {
  std::unique_ptr<NetworkEmulationManager> network_emulation_manager =
      CreateNetworkEmulationManager();
  BuiltInNetworkBehaviorConfig config;
  config.loss_percent = 5;
  config.queue_delay_ms = 50;
  auto fixture = CreateTestFixture(
      "pc_foreman_cif_delay_50_0_plr_5_VP9_generic_descriptor",
      *network_emulation_manager->time_controller(),
      CreateTwoNetworkLinks(network_emulation_manager.get(), config),
      [](PeerConfigurer* alice) {
        VideoConfig video(352, 288, 30);
        video.stream_label = "alice-video";
        auto frame_generator = CreateFromYuvFileFrameGenerator(
            video, ClipNameToClipPath("foreman_cif"));
        alice->AddVideoConfig(std::move(video), std::move(frame_generator));
        alice->SetVideoCodecs({VideoCodecConfig(
            /*name=*/cricket::kVp9CodecName, /*required_params=*/{
                {kVP9FmtpProfileId,
                 VP9ProfileToString(VP9Profile::kProfile0)}})});
      },
      [](PeerConfigurer* bob) {
        bob->SetVideoCodecs({VideoCodecConfig(
            /*name=*/cricket::kVp9CodecName, /*required_params=*/{
                {kVP9FmtpProfileId,
                 VP9ProfileToString(VP9Profile::kProfile0)}})});
      });
  fixture->Run(RunParams(TimeDelta::Seconds(kTestDurationSec)));
}

// VP9 2nd profile isn't supported on android arm and arm 64.
#if (defined(WEBRTC_ANDROID) &&                                   \
     (defined(WEBRTC_ARCH_ARM64) || defined(WEBRTC_ARCH_ARM))) || \
    (defined(WEBRTC_IOS) && defined(WEBRTC_ARCH_ARM64))
#define MAYBE_Pc_Generator_Net_Delay_0_0_Plr_0_VP9Profile2 \
  DISABLED_Pc_Generator_Net_Delay_0_0_Plr_0_VP9Profile2
#else
#define MAYBE_Pc_Generator_Net_Delay_0_0_Plr_0_VP9Profile2 \
  Pc_Generator_Net_Delay_0_0_Plr_0_VP9Profile2
#endif
TEST(PCFullStackTest, MAYBE_Pc_Generator_Net_Delay_0_0_Plr_0_VP9Profile2) {
  std::unique_ptr<NetworkEmulationManager> network_emulation_manager =
      CreateNetworkEmulationManager();
  auto fixture = CreateTestFixture(
      "pc_generator_net_delay_0_0_plr_0_VP9Profile2",
      *network_emulation_manager->time_controller(),
      CreateTwoNetworkLinks(network_emulation_manager.get(),
                            BuiltInNetworkBehaviorConfig()),
      [](PeerConfigurer* alice) {
        VideoConfig video(352, 288, 30);
        video.stream_label = "alice-video";
        auto frame_generator = CreateSquareFrameGenerator(
            video, test::FrameGeneratorInterface::OutputType::kI010);
        alice->AddVideoConfig(std::move(video), std::move(frame_generator));
        alice->SetVideoCodecs({VideoCodecConfig(
            /*name=*/cricket::kVp9CodecName, /*required_params=*/{
                {kVP9FmtpProfileId,
                 VP9ProfileToString(VP9Profile::kProfile2)}})});
      },
      [](PeerConfigurer* bob) {
        bob->SetVideoCodecs({VideoCodecConfig(
            /*name=*/cricket::kVp9CodecName, /*required_params=*/{
                {kVP9FmtpProfileId,
                 VP9ProfileToString(VP9Profile::kProfile2)}})});
      });
  fixture->Run(RunParams(TimeDelta::Seconds(kTestDurationSec)));
}

/*
// TODO(bugs.webrtc.org/10639) migrate commented out test, when required
// functionality will be supported in PeerConnection level framework.
TEST(PCFullStackTest, ForemanCifWithoutPacketLossMultiplexI420Frame) {
  auto fixture = CreateVideoQualityTestFixture();
  ParamsWithLogging foreman_cif;
  foreman_cif.call.send_side_bwe = true;
  foreman_cif.video[0] = {
      true,        352,    288,    30,
      700000,      700000, 700000, false,
      "multiplex", 1,      0,      0,
      false,       false,  false,  ClipNameToClipPath("foreman_cif")};
  foreman_cif.analyzer = {"foreman_cif_net_delay_0_0_plr_0_Multiplex", 0.0, 0.0,
                          kTestDurationSec};
  fixture->RunWithAnalyzer(foreman_cif);
}

TEST(PCFullStackTest, GeneratorWithoutPacketLossMultiplexI420AFrame) {
  auto fixture = CreateVideoQualityTestFixture();

  ParamsWithLogging generator;
  generator.call.send_side_bwe = true;
  generator.video[0] = {
      true,        352, 288, 30, 700000, 700000, 700000, false,
      "multiplex", 1,   0,   0,  false,  false,  false,  "GeneratorI420A"};
  generator.analyzer = {"generator_net_delay_0_0_plr_0_Multiplex", 0.0, 0.0,
                        kTestDurationSec};
  fixture->RunWithAnalyzer(generator);
}
*/
#endif  // defined(RTC_ENABLE_VP9)

TEST(PCFullStackTest, Pc_Net_Delay_0_0_Plr_0) {
  std::unique_ptr<NetworkEmulationManager> network_emulation_manager =
      CreateNetworkEmulationManager();
  auto fixture = CreateTestFixture(
      "pc_net_delay_0_0_plr_0", *network_emulation_manager->time_controller(),
      CreateTwoNetworkLinks(network_emulation_manager.get(),
                            BuiltInNetworkBehaviorConfig()),
      [](PeerConfigurer* alice) {
        VideoConfig video(176, 144, 30);
        video.stream_label = "alice-video";
        auto frame_generator = CreateFromYuvFileFrameGenerator(
            video, ClipNameToClipPath("paris_qcif"));
        alice->AddVideoConfig(std::move(video), std::move(frame_generator));
      },
      [](PeerConfigurer* bob) {});
  fixture->Run(RunParams(TimeDelta::Seconds(kTestDurationSec)));
}

TEST(PCGenericDescriptorTest,
     Pc_Foreman_Cif_Net_Delay_0_0_Plr_0_Generic_Descriptor) {
  std::unique_ptr<NetworkEmulationManager> network_emulation_manager =
      CreateNetworkEmulationManager();
  auto fixture = CreateTestFixture(
      "pc_foreman_cif_net_delay_0_0_plr_0_generic_descriptor",
      *network_emulation_manager->time_controller(),
      CreateTwoNetworkLinks(network_emulation_manager.get(),
                            BuiltInNetworkBehaviorConfig()),
      [](PeerConfigurer* alice) {
        VideoConfig video(352, 288, 30);
        video.stream_label = "alice-video";
        auto frame_generator = CreateFromYuvFileFrameGenerator(
            video, ClipNameToClipPath("foreman_cif"));
        alice->AddVideoConfig(std::move(video), std::move(frame_generator));
      },
      [](PeerConfigurer* bob) {});
  fixture->Run(RunParams(TimeDelta::Seconds(kTestDurationSec)));
}

TEST(PCGenericDescriptorTest,
     Pc_Foreman_Cif_30kbps_Net_Delay_0_0_Plr_0_Generic_Descriptor) {
  std::unique_ptr<NetworkEmulationManager> network_emulation_manager =
      CreateNetworkEmulationManager();
  BuiltInNetworkBehaviorConfig config;
  auto fixture = CreateTestFixture(
      "pc_foreman_cif_30kbps_net_delay_0_0_plr_0_generic_descriptor",
      *network_emulation_manager->time_controller(),
      CreateTwoNetworkLinks(network_emulation_manager.get(), config),
      [](PeerConfigurer* alice) {
        VideoConfig video(352, 288, 10);
        video.stream_label = "alice-video";
        auto frame_generator = CreateFromYuvFileFrameGenerator(
            video, ClipNameToClipPath("foreman_cif"));
        alice->AddVideoConfig(std::move(video), std::move(frame_generator));

        BitrateSettings bitrate_settings;
        bitrate_settings.min_bitrate_bps = 30000;
        bitrate_settings.start_bitrate_bps = 30000;
        bitrate_settings.max_bitrate_bps = 30000;
        alice->SetBitrateSettings(bitrate_settings);
      },
      [](PeerConfigurer* bob) {});
  RunParams run_params(TimeDelta::Seconds(kTestDurationSec));
  fixture->Run(std::move(run_params));
}

// Link capacity below default start rate.
TEST(PCFullStackTest, Pc_Foreman_Cif_Link_150kbps_Net_Delay_0_0_Plr_0) {
  std::unique_ptr<NetworkEmulationManager> network_emulation_manager =
      CreateNetworkEmulationManager();
  BuiltInNetworkBehaviorConfig config;
  config.link_capacity_kbps = 150;
  auto fixture = CreateTestFixture(
      "pc_foreman_cif_link_150kbps_net_delay_0_0_plr_0",
      *network_emulation_manager->time_controller(),
      CreateTwoNetworkLinks(network_emulation_manager.get(), config),
      [](PeerConfigurer* alice) {
        VideoConfig video(352, 288, 30);
        video.stream_label = "alice-video";
        auto frame_generator = CreateFromYuvFileFrameGenerator(
            video, ClipNameToClipPath("foreman_cif"));
        alice->AddVideoConfig(std::move(video), std::move(frame_generator));
      },
      [](PeerConfigurer* bob) {});
  fixture->Run(RunParams(TimeDelta::Seconds(kTestDurationSec)));
}

TEST(PCFullStackTest, Pc_Foreman_Cif_Link_130kbps_Delay100ms_Loss1_Ulpfec) {
  std::unique_ptr<NetworkEmulationManager> network_emulation_manager =
      CreateNetworkEmulationManager();
  BuiltInNetworkBehaviorConfig config;
  config.link_capacity_kbps = 130;
  config.queue_delay_ms = 100;
  config.loss_percent = 1;
  auto fixture = CreateTestFixture(
      "pc_foreman_cif_link_130kbps_delay100ms_loss1_ulpfec",
      *network_emulation_manager->time_controller(),
      CreateTwoNetworkLinks(network_emulation_manager.get(), config),
      [](PeerConfigurer* alice) {
        VideoConfig video(352, 288, 30);
        video.stream_label = "alice-video";
        auto frame_generator = CreateFromYuvFileFrameGenerator(
            video, ClipNameToClipPath("foreman_cif"));
        alice->AddVideoConfig(std::move(video), std::move(frame_generator));
        alice->SetUseUlpFEC(true);
      },
      [](PeerConfigurer* bob) { bob->SetUseUlpFEC(true); });
  fixture->Run(RunParams(TimeDelta::Seconds(kTestDurationSec)));
}

TEST(PCFullStackTest, Pc_Foreman_Cif_Link_50kbps_Delay100ms_Loss1_Ulpfec) {
  std::unique_ptr<NetworkEmulationManager> network_emulation_manager =
      CreateNetworkEmulationManager();
  BuiltInNetworkBehaviorConfig config;
  config.link_capacity_kbps = 50;
  config.queue_delay_ms = 100;
  config.loss_percent = 1;
  auto fixture = CreateTestFixture(
      "pc_foreman_cif_link_50kbps_delay100ms_loss1_ulpfec",
      *network_emulation_manager->time_controller(),
      CreateTwoNetworkLinks(network_emulation_manager.get(), config),
      [](PeerConfigurer* alice) {
        VideoConfig video(352, 288, 30);
        video.stream_label = "alice-video";
        auto frame_generator = CreateFromYuvFileFrameGenerator(
            video, ClipNameToClipPath("foreman_cif"));
        alice->AddVideoConfig(std::move(video), std::move(frame_generator));
        alice->SetUseUlpFEC(true);
      },
      [](PeerConfigurer* bob) { bob->SetUseUlpFEC(true); });
  fixture->Run(RunParams(TimeDelta::Seconds(kTestDurationSec)));
}

// Restricted network and encoder overproducing by 30%.
TEST(PCFullStackTest,
     Pc_Foreman_Cif_Link_150kbps_Delay100ms_30pkts_Queue_Overshoot30) {
  std::unique_ptr<NetworkEmulationManager> network_emulation_manager =
      CreateNetworkEmulationManager();
  BuiltInNetworkBehaviorConfig config;
  config.link_capacity_kbps = 150;
  config.queue_length_packets = 30;
  config.queue_delay_ms = 100;
  auto fixture = CreateTestFixture(
      "pc_foreman_cif_link_150kbps_delay100ms_30pkts_queue_overshoot30",
      *network_emulation_manager->time_controller(),
      CreateTwoNetworkLinks(network_emulation_manager.get(), config),
      [](PeerConfigurer* alice) {
        VideoConfig video(352, 288, 30);
        video.stream_label = "alice-video";
        auto frame_generator = CreateFromYuvFileFrameGenerator(
            video, ClipNameToClipPath("foreman_cif"));
        alice->AddVideoConfig(std::move(video), std::move(frame_generator));
        alice->SetVideoEncoderBitrateMultiplier(1.30);
      },
      [](PeerConfigurer* bob) { bob->SetVideoEncoderBitrateMultiplier(1.30); });
  fixture->Run(RunParams(TimeDelta::Seconds(kTestDurationSec)));
}

// Weak 3G-style link: 250kbps, 1% loss, 100ms delay, 15 packets queue.
// Packet rate and loss are low enough that loss will happen with ~3s interval.
// This triggers protection overhead to toggle between zero and non-zero.
// Link queue is restrictive enough to trigger loss on probes.
TEST(PCFullStackTest, Pc_Foreman_Cif_Link_250kbps_Delay100ms_10pkts_Loss1) {
  std::unique_ptr<NetworkEmulationManager> network_emulation_manager =
      CreateNetworkEmulationManager();
  BuiltInNetworkBehaviorConfig config;
  config.link_capacity_kbps = 250;
  config.queue_length_packets = 10;
  config.queue_delay_ms = 100;
  config.loss_percent = 1;
  auto fixture = CreateTestFixture(
      "pc_foreman_cif_link_250kbps_delay100ms_10pkts_loss1",
      *network_emulation_manager->time_controller(),
      CreateTwoNetworkLinks(network_emulation_manager.get(), config),
      [](PeerConfigurer* alice) {
        VideoConfig video(352, 288, 30);
        video.stream_label = "alice-video";
        auto frame_generator = CreateFromYuvFileFrameGenerator(
            video, ClipNameToClipPath("foreman_cif"));
        alice->AddVideoConfig(std::move(video), std::move(frame_generator));
        alice->SetVideoEncoderBitrateMultiplier(1.30);
      },
      [](PeerConfigurer* bob) { bob->SetVideoEncoderBitrateMultiplier(1.30); });
  fixture->Run(RunParams(TimeDelta::Seconds(kTestDurationSec)));
}

TEST(PCGenericDescriptorTest,
     Pc_Foreman_Cif_Delay_50_0_Plr_5_Generic_Descriptor) {
  std::unique_ptr<NetworkEmulationManager> network_emulation_manager =
      CreateNetworkEmulationManager();
  BuiltInNetworkBehaviorConfig config;
  config.loss_percent = 5;
  config.queue_delay_ms = 50;
  auto fixture = CreateTestFixture(
      "pc_foreman_cif_delay_50_0_plr_5_generic_descriptor",
      *network_emulation_manager->time_controller(),
      CreateTwoNetworkLinks(network_emulation_manager.get(), config),
      [](PeerConfigurer* alice) {
        VideoConfig video(352, 288, 30);
        video.stream_label = "alice-video";
        auto frame_generator = CreateFromYuvFileFrameGenerator(
            video, ClipNameToClipPath("foreman_cif"));
        alice->AddVideoConfig(std::move(video), std::move(frame_generator));
      },
      [](PeerConfigurer* bob) {});
  fixture->Run(RunParams(TimeDelta::Seconds(kTestDurationSec)));
}

TEST(PCGenericDescriptorTest,
     Pc_Foreman_Cif_Delay_50_0_Plr_5_Ulpfec_Generic_Descriptor) {
  std::unique_ptr<NetworkEmulationManager> network_emulation_manager =
      CreateNetworkEmulationManager();
  BuiltInNetworkBehaviorConfig config;
  config.loss_percent = 5;
  config.queue_delay_ms = 50;
  auto fixture = CreateTestFixture(
      "pc_foreman_cif_delay_50_0_plr_5_ulpfec_generic_descriptor",
      *network_emulation_manager->time_controller(),
      CreateTwoNetworkLinks(network_emulation_manager.get(), config),
      [](PeerConfigurer* alice) {
        VideoConfig video(352, 288, 30);
        video.stream_label = "alice-video";
        auto frame_generator = CreateFromYuvFileFrameGenerator(
            video, ClipNameToClipPath("foreman_cif"));
        alice->AddVideoConfig(std::move(video), std::move(frame_generator));
        alice->SetUseUlpFEC(true);
      },
      [](PeerConfigurer* bob) { bob->SetUseUlpFEC(true); });
  fixture->Run(RunParams(TimeDelta::Seconds(kTestDurationSec)));
}

TEST(PCFullStackTest, Pc_Foreman_Cif_Delay_50_0_Plr_5_Flexfec) {
  std::unique_ptr<NetworkEmulationManager> network_emulation_manager =
      CreateNetworkEmulationManager();
  BuiltInNetworkBehaviorConfig config;
  config.loss_percent = 5;
  config.queue_delay_ms = 50;
  auto fixture = CreateTestFixture(
      "pc_foreman_cif_delay_50_0_plr_5_flexfec",
      *network_emulation_manager->time_controller(),
      CreateTwoNetworkLinks(network_emulation_manager.get(), config),
      [](PeerConfigurer* alice) {
        VideoConfig video(352, 288, 30);
        video.stream_label = "alice-video";
        auto frame_generator = CreateFromYuvFileFrameGenerator(
            video, ClipNameToClipPath("foreman_cif"));
        alice->AddVideoConfig(std::move(video), std::move(frame_generator));
        alice->SetUseFlexFEC(true);
      },
      [](PeerConfigurer* bob) { bob->SetUseFlexFEC(true); });
  RunParams run_params(TimeDelta::Seconds(kTestDurationSec));
  run_params.enable_flex_fec_support = true;
  fixture->Run(std::move(run_params));
}

TEST(PCFullStackTest, Pc_Foreman_Cif_500kbps_Delay_50_0_Plr_3_Flexfec) {
  std::unique_ptr<NetworkEmulationManager> network_emulation_manager =
      CreateNetworkEmulationManager();
  BuiltInNetworkBehaviorConfig config;
  config.loss_percent = 3;
  config.link_capacity_kbps = 500;
  config.queue_delay_ms = 50;
  auto fixture = CreateTestFixture(
      "pc_foreman_cif_500kbps_delay_50_0_plr_3_flexfec",
      *network_emulation_manager->time_controller(),
      CreateTwoNetworkLinks(network_emulation_manager.get(), config),
      [](PeerConfigurer* alice) {
        VideoConfig video(352, 288, 30);
        video.stream_label = "alice-video";
        auto frame_generator = CreateFromYuvFileFrameGenerator(
            video, ClipNameToClipPath("foreman_cif"));
        alice->AddVideoConfig(std::move(video), std::move(frame_generator));
        alice->SetUseFlexFEC(true);
      },
      [](PeerConfigurer* bob) { bob->SetUseFlexFEC(true); });
  RunParams run_params(TimeDelta::Seconds(kTestDurationSec));
  run_params.enable_flex_fec_support = true;
  fixture->Run(std::move(run_params));
}

TEST(PCFullStackTest, Pc_Foreman_Cif_500kbps_Delay_50_0_Plr_3_Ulpfec) {
  std::unique_ptr<NetworkEmulationManager> network_emulation_manager =
      CreateNetworkEmulationManager();
  BuiltInNetworkBehaviorConfig config;
  config.loss_percent = 3;
  config.link_capacity_kbps = 500;
  config.queue_delay_ms = 50;
  auto fixture = CreateTestFixture(
      "pc_foreman_cif_500kbps_delay_50_0_plr_3_ulpfec",
      *network_emulation_manager->time_controller(),
      CreateTwoNetworkLinks(network_emulation_manager.get(), config),
      [](PeerConfigurer* alice) {
        VideoConfig video(352, 288, 30);
        video.stream_label = "alice-video";
        auto frame_generator = CreateFromYuvFileFrameGenerator(
            video, ClipNameToClipPath("foreman_cif"));
        alice->AddVideoConfig(std::move(video), std::move(frame_generator));
        alice->SetUseUlpFEC(true);
      },
      [](PeerConfigurer* bob) { bob->SetUseUlpFEC(true); });
  fixture->Run(RunParams(TimeDelta::Seconds(kTestDurationSec)));
}

#if defined(WEBRTC_USE_H264)
TEST(PCFullStackTest, Pc_Foreman_Cif_Net_Delay_0_0_Plr_0_H264) {
  std::unique_ptr<NetworkEmulationManager> network_emulation_manager =
      CreateNetworkEmulationManager();
  auto fixture = CreateTestFixture(
      "pc_foreman_cif_net_delay_0_0_plr_0_H264",
      *network_emulation_manager->time_controller(),
      CreateTwoNetworkLinks(network_emulation_manager.get(),
                            BuiltInNetworkBehaviorConfig()),
      [](PeerConfigurer* alice) {
        VideoConfig video(352, 288, 30);
        video.stream_label = "alice-video";
        auto frame_generator = CreateFromYuvFileFrameGenerator(
            video, ClipNameToClipPath("foreman_cif"));
        alice->AddVideoConfig(std::move(video), std::move(frame_generator));
        alice->SetVideoCodecs({VideoCodecConfig(cricket::kH264CodecName)});
      },
      [](PeerConfigurer* bob) {
        bob->SetVideoCodecs({VideoCodecConfig(cricket::kH264CodecName)});
      });
  fixture->Run(RunParams(TimeDelta::Seconds(kTestDurationSec)));
}

TEST(PCFullStackTest, Pc_Foreman_Cif_30kbps_Net_Delay_0_0_Plr_0_H264) {
  std::unique_ptr<NetworkEmulationManager> network_emulation_manager =
      CreateNetworkEmulationManager();
  BuiltInNetworkBehaviorConfig config;
  auto fixture = CreateTestFixture(
      "pc_foreman_cif_30kbps_net_delay_0_0_plr_0_H264",
      *network_emulation_manager->time_controller(),
      CreateTwoNetworkLinks(network_emulation_manager.get(), config),
      [](PeerConfigurer* alice) {
        VideoConfig video(352, 288, 10);
        video.stream_label = "alice-video";
        auto frame_generator = CreateFromYuvFileFrameGenerator(
            video, ClipNameToClipPath("foreman_cif"));
        alice->AddVideoConfig(std::move(video), std::move(frame_generator));

        BitrateSettings bitrate_settings;
        bitrate_settings.min_bitrate_bps = 30000;
        bitrate_settings.start_bitrate_bps = 30000;
        bitrate_settings.max_bitrate_bps = 30000;
        alice->SetBitrateSettings(bitrate_settings);
        alice->SetVideoCodecs({VideoCodecConfig(cricket::kH264CodecName)});
      },
      [](PeerConfigurer* bob) {
        bob->SetVideoCodecs({VideoCodecConfig(cricket::kH264CodecName)});
      });
  fixture->Run(RunParams(TimeDelta::Seconds(kTestDurationSec)));
}

TEST(PCGenericDescriptorTest,
     Pc_Foreman_Cif_Delay_50_0_Plr_5_H264_Generic_Descriptor) {
  std::unique_ptr<NetworkEmulationManager> network_emulation_manager =
      CreateNetworkEmulationManager();
  BuiltInNetworkBehaviorConfig config;
  config.loss_percent = 5;
  config.queue_delay_ms = 50;
  auto fixture = CreateTestFixture(
      "pc_foreman_cif_delay_50_0_plr_5_H264_generic_descriptor",
      *network_emulation_manager->time_controller(),
      CreateTwoNetworkLinks(network_emulation_manager.get(), config),
      [](PeerConfigurer* alice) {
        VideoConfig video(352, 288, 30);
        video.stream_label = "alice-video";
        auto frame_generator = CreateFromYuvFileFrameGenerator(
            video, ClipNameToClipPath("foreman_cif"));
        alice->AddVideoConfig(std::move(video), std::move(frame_generator));
        alice->SetVideoCodecs({VideoCodecConfig(cricket::kH264CodecName)});
      },
      [](PeerConfigurer* bob) {
        bob->SetVideoCodecs({VideoCodecConfig(cricket::kH264CodecName)});
      });
  fixture->Run(RunParams(TimeDelta::Seconds(kTestDurationSec)));
}

TEST(PCFullStackTest, Pc_Foreman_Cif_Delay_50_0_Plr_5_H264_Sps_Pps_Idr) {
  test::ScopedFieldTrials override_field_trials(
      AppendFieldTrials("WebRTC-SpsPpsIdrIsH264Keyframe/Enabled/"));

  std::unique_ptr<NetworkEmulationManager> network_emulation_manager =
      CreateNetworkEmulationManager();
  BuiltInNetworkBehaviorConfig config;
  config.loss_percent = 5;
  config.queue_delay_ms = 50;
  auto fixture = CreateTestFixture(
      "pc_foreman_cif_delay_50_0_plr_5_H264_sps_pps_idr",
      *network_emulation_manager->time_controller(),
      CreateTwoNetworkLinks(network_emulation_manager.get(), config),
      [](PeerConfigurer* alice) {
        VideoConfig video(352, 288, 30);
        video.stream_label = "alice-video";
        auto frame_generator = CreateFromYuvFileFrameGenerator(
            video, ClipNameToClipPath("foreman_cif"));
        alice->AddVideoConfig(std::move(video), std::move(frame_generator));
        alice->SetVideoCodecs({VideoCodecConfig(cricket::kH264CodecName)});
      },
      [](PeerConfigurer* bob) {
        bob->SetVideoCodecs({VideoCodecConfig(cricket::kH264CodecName)});
      });
  fixture->Run(RunParams(TimeDelta::Seconds(kTestDurationSec)));
}

TEST(PCFullStackTest, Pc_Foreman_Cif_Delay_50_0_Plr_5_H264_Flexfec) {
  std::unique_ptr<NetworkEmulationManager> network_emulation_manager =
      CreateNetworkEmulationManager();
  BuiltInNetworkBehaviorConfig config;
  config.loss_percent = 5;
  config.queue_delay_ms = 50;
  auto fixture = CreateTestFixture(
      "pc_foreman_cif_delay_50_0_plr_5_H264_flexfec",
      *network_emulation_manager->time_controller(),
      CreateTwoNetworkLinks(network_emulation_manager.get(), config),
      [](PeerConfigurer* alice) {
        VideoConfig video(352, 288, 30);
        video.stream_label = "alice-video";
        auto frame_generator = CreateFromYuvFileFrameGenerator(
            video, ClipNameToClipPath("foreman_cif"));
        alice->AddVideoConfig(std::move(video), std::move(frame_generator));
        alice->SetVideoCodecs({VideoCodecConfig(cricket::kH264CodecName)});
        alice->SetUseFlexFEC(true);
      },
      [](PeerConfigurer* bob) {
        bob->SetVideoCodecs({VideoCodecConfig(cricket::kH264CodecName)});
        bob->SetUseFlexFEC(true);
      });
  RunParams run_params(TimeDelta::Seconds(kTestDurationSec));
  run_params.enable_flex_fec_support = true;
  fixture->Run(std::move(run_params));
}

// Ulpfec with H264 is an unsupported combination, so this test is only useful
// for debugging. It is therefore disabled by default.
TEST(PCFullStackTest, DISABLED_Pc_Foreman_Cif_Delay_50_0_Plr_5_H264_Ulpfec) {
  std::unique_ptr<NetworkEmulationManager> network_emulation_manager =
      CreateNetworkEmulationManager();
  BuiltInNetworkBehaviorConfig config;
  config.loss_percent = 5;
  config.queue_delay_ms = 50;
  auto fixture = CreateTestFixture(
      "pc_foreman_cif_delay_50_0_plr_5_H264_ulpfec",
      *network_emulation_manager->time_controller(),
      CreateTwoNetworkLinks(network_emulation_manager.get(), config),
      [](PeerConfigurer* alice) {
        VideoConfig video(352, 288, 30);
        video.stream_label = "alice-video";
        auto frame_generator = CreateFromYuvFileFrameGenerator(
            video, ClipNameToClipPath("foreman_cif"));
        alice->AddVideoConfig(std::move(video), std::move(frame_generator));
        alice->SetVideoCodecs({VideoCodecConfig(cricket::kH264CodecName)});
        alice->SetUseUlpFEC(true);
      },
      [](PeerConfigurer* bob) {
        bob->SetVideoCodecs({VideoCodecConfig(cricket::kH264CodecName)});
        bob->SetUseUlpFEC(true);
      });
  fixture->Run(RunParams(TimeDelta::Seconds(kTestDurationSec)));
}
#endif  // defined(WEBRTC_USE_H264)

TEST(PCFullStackTest, Pc_Foreman_Cif_500kbps) {
  std::unique_ptr<NetworkEmulationManager> network_emulation_manager =
      CreateNetworkEmulationManager();
  BuiltInNetworkBehaviorConfig config;
  config.queue_length_packets = 0;
  config.queue_delay_ms = 0;
  config.link_capacity_kbps = 500;
  auto fixture = CreateTestFixture(
      "pc_foreman_cif_500kbps", *network_emulation_manager->time_controller(),
      CreateTwoNetworkLinks(network_emulation_manager.get(), config),
      [](PeerConfigurer* alice) {
        VideoConfig video(352, 288, 30);
        video.stream_label = "alice-video";
        auto frame_generator = CreateFromYuvFileFrameGenerator(
            video, ClipNameToClipPath("foreman_cif"));
        alice->AddVideoConfig(std::move(video), std::move(frame_generator));
      },
      [](PeerConfigurer* bob) {});
  fixture->Run(RunParams(TimeDelta::Seconds(kTestDurationSec)));
}

TEST(PCFullStackTest, Pc_Foreman_Cif_500kbps_32pkts_Queue) {
  std::unique_ptr<NetworkEmulationManager> network_emulation_manager =
      CreateNetworkEmulationManager();
  BuiltInNetworkBehaviorConfig config;
  config.queue_length_packets = 32;
  config.queue_delay_ms = 0;
  config.link_capacity_kbps = 500;
  auto fixture = CreateTestFixture(
      "pc_foreman_cif_500kbps_32pkts_queue",
      *network_emulation_manager->time_controller(),
      CreateTwoNetworkLinks(network_emulation_manager.get(), config),
      [](PeerConfigurer* alice) {
        VideoConfig video(352, 288, 30);
        video.stream_label = "alice-video";
        auto frame_generator = CreateFromYuvFileFrameGenerator(
            video, ClipNameToClipPath("foreman_cif"));
        alice->AddVideoConfig(std::move(video), std::move(frame_generator));
      },
      [](PeerConfigurer* bob) {});
  fixture->Run(RunParams(TimeDelta::Seconds(kTestDurationSec)));
}

TEST(PCFullStackTest, Pc_Foreman_Cif_500kbps_100ms) {
  std::unique_ptr<NetworkEmulationManager> network_emulation_manager =
      CreateNetworkEmulationManager();
  BuiltInNetworkBehaviorConfig config;
  config.queue_length_packets = 0;
  config.queue_delay_ms = 100;
  config.link_capacity_kbps = 500;
  auto fixture = CreateTestFixture(
      "pc_foreman_cif_500kbps_100ms",
      *network_emulation_manager->time_controller(),
      CreateTwoNetworkLinks(network_emulation_manager.get(), config),
      [](PeerConfigurer* alice) {
        VideoConfig video(352, 288, 30);
        video.stream_label = "alice-video";
        auto frame_generator = CreateFromYuvFileFrameGenerator(
            video, ClipNameToClipPath("foreman_cif"));
        alice->AddVideoConfig(std::move(video), std::move(frame_generator));
      },
      [](PeerConfigurer* bob) {});
  fixture->Run(RunParams(TimeDelta::Seconds(kTestDurationSec)));
}

TEST(PCGenericDescriptorTest,
     Pc_Foreman_Cif_500kbps_100ms_32pkts_Queue_Generic_Descriptor) {
  std::unique_ptr<NetworkEmulationManager> network_emulation_manager =
      CreateNetworkEmulationManager();
  BuiltInNetworkBehaviorConfig config;
  config.queue_length_packets = 32;
  config.queue_delay_ms = 100;
  config.link_capacity_kbps = 500;
  auto fixture = CreateTestFixture(
      "pc_foreman_cif_500kbps_100ms_32pkts_queue_generic_descriptor",
      *network_emulation_manager->time_controller(),
      CreateTwoNetworkLinks(network_emulation_manager.get(), config),
      [](PeerConfigurer* alice) {
        VideoConfig video(352, 288, 30);
        video.stream_label = "alice-video";
        auto frame_generator = CreateFromYuvFileFrameGenerator(
            video, ClipNameToClipPath("foreman_cif"));
        alice->AddVideoConfig(std::move(video), std::move(frame_generator));
      },
      [](PeerConfigurer* bob) {});
  fixture->Run(RunParams(TimeDelta::Seconds(kTestDurationSec)));
}

/*
// TODO(bugs.webrtc.org/10639) we need to disable send side bwe, but it isn't
// supported in PC level framework.
TEST(PCFullStackTest, ForemanCif500kbps100msLimitedQueueRecvBwe) {
  auto fixture = CreateVideoQualityTestFixture();
  ParamsWithLogging foreman_cif;
  foreman_cif.call.send_side_bwe = false;
  foreman_cif.video[0] = {
      true,  352,    288,     30,
      30000, 500000, 2000000, false,
      "VP8", 1,      0,       0,
      false, false,  true,    ClipNameToClipPath("foreman_cif")};
  foreman_cif.analyzer = {"foreman_cif_500kbps_100ms_32pkts_queue_recv_bwe",
                          0.0, 0.0, kTestDurationSec};
  foreman_cif.config->queue_length_packets = 32;
  foreman_cif.config->queue_delay_ms = 100;
  foreman_cif.config->link_capacity_kbps = 500;
  fixture->RunWithAnalyzer(foreman_cif);
}
*/

TEST(PCFullStackTest, Pc_Foreman_Cif_1000kbps_100ms_32pkts_Queue) {
  std::unique_ptr<NetworkEmulationManager> network_emulation_manager =
      CreateNetworkEmulationManager();
  BuiltInNetworkBehaviorConfig config;
  config.queue_length_packets = 32;
  config.queue_delay_ms = 100;
  config.link_capacity_kbps = 1000;
  auto fixture = CreateTestFixture(
      "pc_foreman_cif_1000kbps_100ms_32pkts_queue",
      *network_emulation_manager->time_controller(),
      CreateTwoNetworkLinks(network_emulation_manager.get(), config),
      [](PeerConfigurer* alice) {
        VideoConfig video(352, 288, 30);
        video.stream_label = "alice-video";
        auto frame_generator = CreateFromYuvFileFrameGenerator(
            video, ClipNameToClipPath("foreman_cif"));
        alice->AddVideoConfig(std::move(video), std::move(frame_generator));
      },
      [](PeerConfigurer* bob) {});
  fixture->Run(RunParams(TimeDelta::Seconds(kTestDurationSec)));
}

// TODO(sprang): Remove this if we have the similar ModerateLimits below?
TEST(PCFullStackTest, Pc_Conference_Motion_Hd_2000kbps_100ms_32pkts_Queue) {
  std::unique_ptr<NetworkEmulationManager> network_emulation_manager =
      CreateNetworkEmulationManager();
  BuiltInNetworkBehaviorConfig config;
  config.queue_length_packets = 32;
  config.queue_delay_ms = 100;
  config.link_capacity_kbps = 2000;
  auto fixture = CreateTestFixture(
      "pc_conference_motion_hd_2000kbps_100ms_32pkts_queue",
      *network_emulation_manager->time_controller(),
      CreateTwoNetworkLinks(network_emulation_manager.get(), config),
      [](PeerConfigurer* alice) {
        VideoConfig video(1280, 720, 50);
        video.stream_label = "alice-video";
        auto frame_generator = CreateFromYuvFileFrameGenerator(
            video, ClipNameToClipPath("ConferenceMotion_1280_720_50"));
        alice->AddVideoConfig(std::move(video), std::move(frame_generator));
      },
      [](PeerConfigurer* bob) {});
  fixture->Run(RunParams(TimeDelta::Seconds(kTestDurationSec)));
}

/*
// TODO(bugs.webrtc.org/10639) requires simulcast/SVC support in PC framework
TEST(PCGenericDescriptorTest, ConferenceMotionHd2TLModerateLimits) {
  auto fixture = CreateVideoQualityTestFixture();
  ParamsWithLogging conf_motion_hd;
  conf_motion_hd.call.send_side_bwe = true;
  conf_motion_hd.video[0] = {
      true,    1280,
      720,     50,
      30000,   3000000,
      3000000, false,
      "VP8",   2,
      -1,      0,
      false,   false,
      false,   ClipNameToClipPath("ConferenceMotion_1280_720_50")};
  conf_motion_hd.analyzer = {
      "conference_motion_hd_2tl_moderate_limits_generic_descriptor", 0.0, 0.0,
      kTestDurationSec};
  conf_motion_hd.config->queue_length_packets = 50;
  conf_motion_hd.config->loss_percent = 3;
  conf_motion_hd.config->queue_delay_ms = 100;
  conf_motion_hd.config->link_capacity_kbps = 2000;
  conf_motion_hd.call.generic_descriptor = GenericDescriptorEnabled();
  fixture->RunWithAnalyzer(conf_motion_hd);
}

// TODO(bugs.webrtc.org/10639) requires simulcast/SVC support in PC framework
TEST(PCFullStackTest, ConferenceMotionHd3TLModerateLimits) {
  auto fixture = CreateVideoQualityTestFixture();
  ParamsWithLogging conf_motion_hd;
  conf_motion_hd.call.send_side_bwe = true;
  conf_motion_hd.video[0] = {
      true,    1280,
      720,     50,
      30000,   3000000,
      3000000, false,
      "VP8",   3,
      -1,      0,
      false,   false,
      false,   ClipNameToClipPath("ConferenceMotion_1280_720_50")};
  conf_motion_hd.analyzer = {"conference_motion_hd_3tl_moderate_limits", 0.0,
                             0.0, kTestDurationSec};
  conf_motion_hd.config->queue_length_packets = 50;
  conf_motion_hd.config->loss_percent = 3;
  conf_motion_hd.config->queue_delay_ms = 100;
  conf_motion_hd.config->link_capacity_kbps = 2000;
  fixture->RunWithAnalyzer(conf_motion_hd);
}

// TODO(bugs.webrtc.org/10639) requires simulcast/SVC support in PC framework
TEST(PCFullStackTest, ConferenceMotionHd4TLModerateLimits) {
  auto fixture = CreateVideoQualityTestFixture();
  ParamsWithLogging conf_motion_hd;
  conf_motion_hd.call.send_side_bwe = true;
  conf_motion_hd.video[0] = {
      true,    1280,
      720,     50,
      30000,   3000000,
      3000000, false,
      "VP8",   4,
      -1,      0,
      false,   false,
      false,   ClipNameToClipPath("ConferenceMotion_1280_720_50")};
  conf_motion_hd.analyzer = {"conference_motion_hd_4tl_moderate_limits", 0.0,
                             0.0, kTestDurationSec};
  conf_motion_hd.config->queue_length_packets = 50;
  conf_motion_hd.config->loss_percent = 3;
  conf_motion_hd.config->queue_delay_ms = 100;
  conf_motion_hd.config->link_capacity_kbps = 2000;
  fixture->RunWithAnalyzer(conf_motion_hd);
}

// TODO(bugs.webrtc.org/10639) requires simulcast/SVC support in PC framework
TEST(PCFullStackTest, ConferenceMotionHd3TLModerateLimitsAltTLPattern) {
  test::ScopedFieldTrials field_trial(
      AppendFieldTrials("WebRTC-UseShortVP8TL3Pattern/Enabled/"));
  auto fixture = CreateVideoQualityTestFixture();
  ParamsWithLogging conf_motion_hd;
  conf_motion_hd.call.send_side_bwe = true;
  conf_motion_hd.video[0] = {
      true,    1280,
      720,     50,
      30000,   3000000,
      3000000, false,
      "VP8",   3,
      -1,      0,
      false,   false,
      false,   ClipNameToClipPath("ConferenceMotion_1280_720_50")};
  conf_motion_hd.analyzer = {"conference_motion_hd_3tl_alt_moderate_limits",
                             0.0, 0.0, kTestDurationSec};
  conf_motion_hd.config->queue_length_packets = 50;
  conf_motion_hd.config->loss_percent = 3;
  conf_motion_hd.config->queue_delay_ms = 100;
  conf_motion_hd.config->link_capacity_kbps = 2000;
  fixture->RunWithAnalyzer(conf_motion_hd);
}

// TODO(bugs.webrtc.org/10639) requires simulcast/SVC support in PC framework
TEST(PCFullStackTest,
     ConferenceMotionHd3TLModerateLimitsAltTLPatternAndBaseHeavyTLAllocation) {
  auto fixture = CreateVideoQualityTestFixture();
  test::ScopedFieldTrials field_trial(
      AppendFieldTrials("WebRTC-UseShortVP8TL3Pattern/Enabled/"
                        "WebRTC-UseBaseHeavyVP8TL3RateAllocation/Enabled/"));
  ParamsWithLogging conf_motion_hd;
  conf_motion_hd.call.send_side_bwe = true;
  conf_motion_hd.video[0] = {
      true,    1280,
      720,     50,
      30000,   3000000,
      3000000, false,
      "VP8",   3,
      -1,      0,
      false,   false,
      false,   ClipNameToClipPath("ConferenceMotion_1280_720_50")};
  conf_motion_hd.analyzer = {
      "conference_motion_hd_3tl_alt_heavy_moderate_limits", 0.0, 0.0,
      kTestDurationSec};
  conf_motion_hd.config->queue_length_packets = 50;
  conf_motion_hd.config->loss_percent = 3;
  conf_motion_hd.config->queue_delay_ms = 100;
  conf_motion_hd.config->link_capacity_kbps = 2000;
  fixture->RunWithAnalyzer(conf_motion_hd);
}
*/

#if defined(RTC_ENABLE_VP9)
TEST(PCFullStackTest, Pc_Conference_Motion_Hd_2000kbps_100ms_32pkts_Queue_Vp9) {
  std::unique_ptr<NetworkEmulationManager> network_emulation_manager =
      CreateNetworkEmulationManager();
  BuiltInNetworkBehaviorConfig config;
  config.queue_length_packets = 32;
  config.queue_delay_ms = 100;
  config.link_capacity_kbps = 2000;
  auto fixture = CreateTestFixture(
      "pc_conference_motion_hd_2000kbps_100ms_32pkts_queue_vp9",
      *network_emulation_manager->time_controller(),
      CreateTwoNetworkLinks(network_emulation_manager.get(), config),
      [](PeerConfigurer* alice) {
        VideoConfig video(1280, 720, 50);
        video.stream_label = "alice-video";
        auto frame_generator = CreateFromYuvFileFrameGenerator(
            video, ClipNameToClipPath("ConferenceMotion_1280_720_50"));
        alice->AddVideoConfig(std::move(video), std::move(frame_generator));
        alice->SetVideoCodecs({VideoCodecConfig(
            /*name=*/cricket::kVp9CodecName, /*required_params=*/{
                {kVP9FmtpProfileId,
                 VP9ProfileToString(VP9Profile::kProfile0)}})});
      },
      [](PeerConfigurer* bob) {
        bob->SetVideoCodecs({VideoCodecConfig(
            /*name=*/cricket::kVp9CodecName, /*required_params=*/{
                {kVP9FmtpProfileId,
                 VP9ProfileToString(VP9Profile::kProfile0)}})});
      });
  fixture->Run(RunParams(TimeDelta::Seconds(kTestDurationSec)));
}
#endif

TEST(PCFullStackTest, Pc_Screenshare_Slides_No_Conference_Mode) {
  std::unique_ptr<NetworkEmulationManager> network_emulation_manager =
      CreateNetworkEmulationManager();
  auto fixture = CreateTestFixture(
      "pc_screenshare_slides_no_conference_mode",
      *network_emulation_manager->time_controller(),
      CreateTwoNetworkLinks(network_emulation_manager.get(),
                            BuiltInNetworkBehaviorConfig()),
      [](PeerConfigurer* alice) {
        VideoConfig video(1850, 1110, 5);
        video.stream_label = "alice-video";
        video.content_hint = VideoTrackInterface::ContentHint::kText;
        auto frame_generator = CreateScreenShareFrameGenerator(
            video, ScreenShareConfig(TimeDelta::Seconds(10)));
        alice->AddVideoConfig(std::move(video), std::move(frame_generator));
      },
      [](PeerConfigurer* bob) {});
  fixture->Run(RunParams(TimeDelta::Seconds(kTestDurationSec)));
}

TEST(PCFullStackTest, Pc_Screenshare_Slides) {
  std::unique_ptr<NetworkEmulationManager> network_emulation_manager =
      CreateNetworkEmulationManager();
  auto fixture = CreateTestFixture(
      "pc_screenshare_slides", *network_emulation_manager->time_controller(),
      CreateTwoNetworkLinks(network_emulation_manager.get(),
                            BuiltInNetworkBehaviorConfig()),
      [](PeerConfigurer* alice) {
        VideoConfig video(1850, 1110, 5);
        video.stream_label = "alice-video";
        video.content_hint = VideoTrackInterface::ContentHint::kText;
        auto frame_generator = CreateScreenShareFrameGenerator(
            video, ScreenShareConfig(TimeDelta::Seconds(10)));
        alice->AddVideoConfig(std::move(video), std::move(frame_generator));
      },
      [](PeerConfigurer* bob) {});
  RunParams run_params(TimeDelta::Seconds(kTestDurationSec));
  run_params.use_conference_mode = true;
  fixture->Run(std::move(run_params));
}

// TODO(bugs.webrtc.org/9840): Investigate why is this test flaky on Win/Mac.
#if !defined(WEBRTC_MAC) && !defined(WEBRTC_WIN)
TEST(PCFullStackTest, Pc_Screenshare_Slides_Simulcast_No_Conference_Mode) {
  std::unique_ptr<NetworkEmulationManager> network_emulation_manager =
      CreateNetworkEmulationManager();
  auto fixture = CreateTestFixture(
      "pc_screenshare_slides_simulcast_no_conference_mode",
      *network_emulation_manager->time_controller(),
      CreateTwoNetworkLinks(network_emulation_manager.get(),
                            BuiltInNetworkBehaviorConfig()),
      [](PeerConfigurer* alice) {
        VideoConfig video(1850, 1110, 30);
        video.simulcast_config = VideoSimulcastConfig(2, 1);
        video.temporal_layers_count = 2;
        video.stream_label = "alice-video";
        video.content_hint = VideoTrackInterface::ContentHint::kText;
        auto frame_generator = CreateScreenShareFrameGenerator(
            video, ScreenShareConfig(TimeDelta::Seconds(10)));
        alice->AddVideoConfig(std::move(video), std::move(frame_generator));
      },
      [](PeerConfigurer* bob) {});
  fixture->Run(RunParams(TimeDelta::Seconds(kTestDurationSec)));
}

TEST(PCFullStackTest, Pc_Screenshare_Slides_Simulcast) {
  std::unique_ptr<NetworkEmulationManager> network_emulation_manager =
      CreateNetworkEmulationManager();
  auto fixture = CreateTestFixture(
      "pc_screenshare_slides_simulcast",
      *network_emulation_manager->time_controller(),
      CreateTwoNetworkLinks(network_emulation_manager.get(),
                            BuiltInNetworkBehaviorConfig()),
      [](PeerConfigurer* alice) {
        VideoConfig video(1850, 1110, 30);
        video.simulcast_config = VideoSimulcastConfig(2, 1);
        video.temporal_layers_count = 2;
        video.stream_label = "alice-video";
        video.content_hint = VideoTrackInterface::ContentHint::kText;
        auto frame_generator = CreateScreenShareFrameGenerator(
            video, ScreenShareConfig(TimeDelta::Seconds(10)));
        alice->AddVideoConfig(std::move(video), std::move(frame_generator));
      },
      [](PeerConfigurer* bob) {});
  RunParams run_params(TimeDelta::Seconds(kTestDurationSec));
  run_params.use_conference_mode = true;
  fixture->Run(std::move(run_params));
}
#endif  // !defined(WEBRTC_MAC) && !defined(WEBRTC_WIN)

/*
#if !defined(WEBRTC_MAC)
// TODO(bugs.webrtc.org/9840): Investigate why is this test flaky on Win/Mac.
#if !defined(WEBRTC_WIN)
// TODO(bugs.webrtc.org/10639) requires simulcast/SVC support in PC framework
TEST(PCFullStackTest, ScreenshareSlidesVP8_2TL_Simulcast_low) {
  auto fixture = CreateVideoQualityTestFixture();
  ParamsWithLogging screenshare;
  screenshare.call.send_side_bwe = true;
  screenshare.screenshare[0] = {true, false, 10};
  screenshare.video[0] = {true,    1850,  1110,  30, 800000, 2500000,
                          2500000, false, "VP8", 2,  1,      400000,
                          false,   false, false, ""};
  screenshare.analyzer = {"screenshare_slides_simulcast_low", 0.0, 0.0,
                          kTestDurationSec};
  VideoQualityTest::Params screenshare_params_high;
  screenshare_params_high.video[0] = {
      true,  1850, 1110, 60,     600000, 1250000, 1250000, false,
      "VP8", 2,    0,    400000, false,  false,   false,   ""};
  VideoQualityTest::Params screenshare_params_low;
  screenshare_params_low.video[0] = {true,    1850,  1110,  5, 30000, 200000,
                                     1000000, false, "VP8", 2, 0,     400000,
                                     false,   false, false, ""};

  std::vector<VideoStream> streams = {
      VideoQualityTest::DefaultVideoStream(screenshare_params_low, 0),
      VideoQualityTest::DefaultVideoStream(screenshare_params_high, 0)};
  screenshare.ss[0] = {
      streams, 0, 1, 0, InterLayerPredMode::kOn, std::vector<SpatialLayer>(),
      false};
  fixture->RunWithAnalyzer(screenshare);
}

#endif  // !defined(WEBRTC_WIN)
#endif  // !defined(WEBRTC_MAC)

// TODO(bugs.webrtc.org/10639) requires simulcast/SVC support in PC framework
TEST(PCFullStackTest, ScreenshareSlidesVP8_2TL_Scroll) {
  auto fixture = CreateVideoQualityTestFixture();
  ParamsWithLogging config;
  config.call.send_side_bwe = true;
  config.video[0] = {true,    1850,  1110 / 2, 5, 50000, 200000,
                     1000000, false, "VP8",    2, 1,     400000,
                     false,   false, false,    ""};
  config.screenshare[0] = {true, false, 10, 2};
  config.analyzer = {"screenshare_slides_scrolling", 0.0, 0.0,
                     kTestDurationSec};
  fixture->RunWithAnalyzer(config);
}

// TODO(bugs.webrtc.org/10639) requires simulcast/SVC support in PC framework
TEST(PCGenericDescriptorTest, Screenshare_Slides_Lossy_Net_Generic_Descriptor) {
  auto fixture = CreateVideoQualityTestFixture();
  ParamsWithLogging screenshare;
  screenshare.call.send_side_bwe = true;
  screenshare.video[0] = {true,    1850,  1110,  5, 50000, 200000,
                          1000000, false, "VP8", 2, 1,     400000,
                          false,   false, false, ""};
  screenshare.screenshare[0] = {true, false, 10};
  screenshare.analyzer = {"screenshare_slides_lossy_net_generic_descriptor",
                          0.0, 0.0, kTestDurationSec};
  screenshare.config->loss_percent = 5;
  screenshare.config->queue_delay_ms = 200;
  screenshare.config->link_capacity_kbps = 500;
  screenshare.call.generic_descriptor = true;
  fixture->RunWithAnalyzer(screenshare);
}

// TODO(bugs.webrtc.org/10639) requires simulcast/SVC support in PC framework
TEST(PCFullStackTest, ScreenshareSlidesVP8_2TL_VeryLossyNet) {
  auto fixture = CreateVideoQualityTestFixture();
  ParamsWithLogging screenshare;
  screenshare.call.send_side_bwe = true;
  screenshare.video[0] = {true,    1850,  1110,  5, 50000, 200000,
                          1000000, false, "VP8", 2, 1,     400000,
                          false,   false, false, ""};
  screenshare.screenshare[0] = {true, false, 10};
  screenshare.analyzer = {"screenshare_slides_very_lossy", 0.0, 0.0,
                          kTestDurationSec};
  screenshare.config->loss_percent = 10;
  screenshare.config->queue_delay_ms = 200;
  screenshare.config->link_capacity_kbps = 500;
  fixture->RunWithAnalyzer(screenshare);
}

// TODO(bugs.webrtc.org/10639) requires simulcast/SVC support in PC framework
TEST(PCFullStackTest, ScreenshareSlidesVP8_2TL_LossyNetRestrictedQueue) {
  auto fixture = CreateVideoQualityTestFixture();
  ParamsWithLogging screenshare;
  screenshare.call.send_side_bwe = true;
  screenshare.video[0] = {true,    1850,  1110,  5, 50000, 200000,
                          1000000, false, "VP8", 2, 1,     400000,
                          false,   false, false, ""};
  screenshare.screenshare[0] = {true, false, 10};
  screenshare.analyzer = {"screenshare_slides_lossy_limited", 0.0, 0.0,
                          kTestDurationSec};
  screenshare.config->loss_percent = 5;
  screenshare.config->link_capacity_kbps = 200;
  screenshare.config->queue_length_packets = 30;

  fixture->RunWithAnalyzer(screenshare);
}

// TODO(bugs.webrtc.org/10639) requires simulcast/SVC support in PC framework
TEST(PCFullStackTest, ScreenshareSlidesVP8_2TL_ModeratelyRestricted) {
  auto fixture = CreateVideoQualityTestFixture();
  ParamsWithLogging screenshare;
  screenshare.call.send_side_bwe = true;
  screenshare.video[0] = {true,    1850,  1110,  5, 50000, 200000,
                          1000000, false, "VP8", 2, 1,     400000,
                          false,   false, false, ""};
  screenshare.screenshare[0] = {true, false, 10};
  screenshare.analyzer = {"screenshare_slides_moderately_restricted", 0.0, 0.0,
                          kTestDurationSec};
  screenshare.config->loss_percent = 1;
  screenshare.config->link_capacity_kbps = 1200;
  screenshare.config->queue_length_packets = 30;

  fixture->RunWithAnalyzer(screenshare);
}

namespace {
// TODO(bugs.webrtc.org/10639) requires simulcast/SVC support in PC framework
// Since ParamsWithLogging::Video is not trivially destructible, we can't
// store these structs as const globals.
ParamsWithLogging::Video SvcVp9Video() {
  return ParamsWithLogging::Video{
      true,    1280,
      720,     30,
      800000,  2500000,
      2500000, false,
      "VP9",   3,
      2,       400000,
      false,   false,
      false,   ClipNameToClipPath("ConferenceMotion_1280_720_50")};
}

ParamsWithLogging::Video SimulcastVp8VideoHigh() {
  return ParamsWithLogging::Video{
      true,    1280,
      720,     30,
      800000,  2500000,
      2500000, false,
      "VP8",   3,
      2,       400000,
      false,   false,
      false,   ClipNameToClipPath("ConferenceMotion_1280_720_50")};
}

ParamsWithLogging::Video SimulcastVp8VideoMedium() {
  return ParamsWithLogging::Video{
      true,   640,
      360,    30,
      150000, 500000,
      700000, false,
      "VP8",  3,
      2,      400000,
      false,  false,
      false,  ClipNameToClipPath("ConferenceMotion_1280_720_50")};
}

ParamsWithLogging::Video SimulcastVp8VideoLow() {
  return ParamsWithLogging::Video{
      true,   320,
      180,    30,
      30000,  150000,
      200000, false,
      "VP8",  3,
      2,      400000,
      false,  false,
      false,  ClipNameToClipPath("ConferenceMotion_1280_720_50")};
}
}  // namespace
*/

#if defined(RTC_ENABLE_VP9)

TEST(PCFullStackTest, Pc_Screenshare_Slides_Vp9_3sl_High_Fps) {
  webrtc::test::ScopedFieldTrials override_trials(
      AppendFieldTrials("WebRTC-Vp9InterLayerPred/"
                        "Enabled,inter_layer_pred_mode:on/"));
  std::unique_ptr<NetworkEmulationManager> network_emulation_manager =
      CreateNetworkEmulationManager();
  auto fixture = CreateTestFixture(
      "pc_screenshare_slides_vp9_3sl_high_fps",
      *network_emulation_manager->time_controller(),
      CreateTwoNetworkLinks(network_emulation_manager.get(),
                            BuiltInNetworkBehaviorConfig()),
      [](PeerConfigurer* alice) {
        VideoConfig video(1850, 1110, 30);
        video.stream_label = "alice-video";
        video.simulcast_config = VideoSimulcastConfig(3, 2);
        video.content_hint = VideoTrackInterface::ContentHint::kText;
        auto frame_generator = CreateScreenShareFrameGenerator(
            video, ScreenShareConfig(TimeDelta::Seconds(10)));
        alice->AddVideoConfig(std::move(video), std::move(frame_generator));
        alice->SetVideoCodecs({VideoCodecConfig(
            /*name=*/cricket::kVp9CodecName, /*required_params=*/{
                {kVP9FmtpProfileId,
                 VP9ProfileToString(VP9Profile::kProfile0)}})});
      },
      [](PeerConfigurer* bob) {
        bob->SetVideoCodecs({VideoCodecConfig(
            /*name=*/cricket::kVp9CodecName, /*required_params=*/{
                {kVP9FmtpProfileId,
                 VP9ProfileToString(VP9Profile::kProfile0)}})});
      });
  fixture->Run(RunParams(TimeDelta::Seconds(kTestDurationSec)));
}

TEST(PCFullStackTest, Pc_Vp9svc_3sl_High) {
  webrtc::test::ScopedFieldTrials override_trials(
      AppendFieldTrials("WebRTC-Vp9InterLayerPred/"
                        "Enabled,inter_layer_pred_mode:on/"));
  std::unique_ptr<NetworkEmulationManager> network_emulation_manager =
      CreateNetworkEmulationManager();
  auto fixture = CreateTestFixture(
      "pc_vp9svc_3sl_high", *network_emulation_manager->time_controller(),
      CreateTwoNetworkLinks(network_emulation_manager.get(),
                            BuiltInNetworkBehaviorConfig()),
      [](PeerConfigurer* alice) {
        VideoConfig video(1280, 720, 30);
        video.stream_label = "alice-video";
        video.simulcast_config = VideoSimulcastConfig(3, 2);
        video.temporal_layers_count = 3;
        auto frame_generator = CreateFromYuvFileFrameGenerator(
            video, ClipNameToClipPath("ConferenceMotion_1280_720_50"));
        alice->AddVideoConfig(std::move(video), std::move(frame_generator));
        alice->SetVideoCodecs({VideoCodecConfig(
            /*name=*/cricket::kVp9CodecName, /*required_params=*/{
                {kVP9FmtpProfileId,
                 VP9ProfileToString(VP9Profile::kProfile0)}})});
      },
      [](PeerConfigurer* bob) {
        bob->SetVideoCodecs({VideoCodecConfig(
            /*name=*/cricket::kVp9CodecName, /*required_params=*/{
                {kVP9FmtpProfileId,
                 VP9ProfileToString(VP9Profile::kProfile0)}})});
      });
  fixture->Run(RunParams(TimeDelta::Seconds(kTestDurationSec)));
}

TEST(PCFullStackTest, Pc_Vp9svc_3sl_Low) {
  webrtc::test::ScopedFieldTrials override_trials(
      AppendFieldTrials("WebRTC-Vp9InterLayerPred/"
                        "Enabled,inter_layer_pred_mode:on/"));
  std::unique_ptr<NetworkEmulationManager> network_emulation_manager =
      CreateNetworkEmulationManager();
  auto fixture = CreateTestFixture(
      "pc_vp9svc_3sl_low", *network_emulation_manager->time_controller(),
      CreateTwoNetworkLinks(network_emulation_manager.get(),
                            BuiltInNetworkBehaviorConfig()),
      [](PeerConfigurer* alice) {
        VideoConfig video(1280, 720, 30);
        video.stream_label = "alice-video";
        video.simulcast_config = VideoSimulcastConfig(3, 0);
        video.temporal_layers_count = 3;
        auto frame_generator = CreateFromYuvFileFrameGenerator(
            video, ClipNameToClipPath("ConferenceMotion_1280_720_50"));
        alice->AddVideoConfig(std::move(video), std::move(frame_generator));
        alice->SetVideoCodecs({VideoCodecConfig(
            /*name=*/cricket::kVp9CodecName, /*required_params=*/{
                {kVP9FmtpProfileId,
                 VP9ProfileToString(VP9Profile::kProfile0)}})});
      },
      [](PeerConfigurer* bob) {
        bob->SetVideoCodecs({VideoCodecConfig(
            /*name=*/cricket::kVp9CodecName, /*required_params=*/{
                {kVP9FmtpProfileId,
                 VP9ProfileToString(VP9Profile::kProfile0)}})});
      });
  fixture->Run(RunParams(TimeDelta::Seconds(kTestDurationSec)));
}

#endif  // defined(RTC_ENABLE_VP9)

/*
// bugs.webrtc.org/9506
#if !defined(WEBRTC_MAC)

// TODO(bugs.webrtc.org/10639) requires simulcast/SVC support in PC framework
TEST(PCFullStackTest, VP9KSVC_3SL_High) {
  webrtc::test::ScopedFieldTrials override_trials(
      AppendFieldTrials("WebRTC-Vp9IssueKeyFrameOnLayerDeactivation/Enabled/"));
  auto fixture = CreateVideoQualityTestFixture();
  ParamsWithLogging simulcast;
  simulcast.call.send_side_bwe = true;
  simulcast.video[0] = SvcVp9Video();
  simulcast.analyzer = {"vp9ksvc_3sl_high", 0.0, 0.0, kTestDurationSec};
  simulcast.ss[0] = {
      std::vector<VideoStream>(),  0,    3, 2, InterLayerPredMode::kOnKeyPic,
      std::vector<SpatialLayer>(), false};
  fixture->RunWithAnalyzer(simulcast);
}

// TODO(bugs.webrtc.org/10639) requires simulcast/SVC support in PC framework
TEST(PCFullStackTest, VP9KSVC_3SL_Medium) {
  webrtc::test::ScopedFieldTrials override_trials(
      AppendFieldTrials("WebRTC-Vp9IssueKeyFrameOnLayerDeactivation/Enabled/"));
  auto fixture = CreateVideoQualityTestFixture();
  ParamsWithLogging simulcast;
  simulcast.call.send_side_bwe = true;
  simulcast.video[0] = SvcVp9Video();
  simulcast.analyzer = {"vp9ksvc_3sl_medium", 0.0, 0.0, kTestDurationSec};
  simulcast.ss[0] = {
      std::vector<VideoStream>(),  0,    3, 1, InterLayerPredMode::kOnKeyPic,
      std::vector<SpatialLayer>(), false};
  fixture->RunWithAnalyzer(simulcast);
}

// TODO(bugs.webrtc.org/10639) requires simulcast/SVC support in PC framework
TEST(PCFullStackTest, VP9KSVC_3SL_Low) {
  webrtc::test::ScopedFieldTrials override_trials(
      AppendFieldTrials("WebRTC-Vp9IssueKeyFrameOnLayerDeactivation/Enabled/"));
  auto fixture = CreateVideoQualityTestFixture();
  ParamsWithLogging simulcast;
  simulcast.call.send_side_bwe = true;
  simulcast.video[0] = SvcVp9Video();
  simulcast.analyzer = {"vp9ksvc_3sl_low", 0.0, 0.0, kTestDurationSec};
  simulcast.ss[0] = {
      std::vector<VideoStream>(),  0,    3, 0, InterLayerPredMode::kOnKeyPic,
      std::vector<SpatialLayer>(), false};
  fixture->RunWithAnalyzer(simulcast);
}

// TODO(bugs.webrtc.org/10639) requires simulcast/SVC support in PC framework
TEST(PCFullStackTest, VP9KSVC_3SL_Medium_Network_Restricted) {
  webrtc::test::ScopedFieldTrials override_trials(
      AppendFieldTrials("WebRTC-Vp9IssueKeyFrameOnLayerDeactivation/Enabled/"));
  auto fixture = CreateVideoQualityTestFixture();
  ParamsWithLogging simulcast;
  simulcast.call.send_side_bwe = true;
  simulcast.video[0] = SvcVp9Video();
  simulcast.analyzer = {"vp9ksvc_3sl_medium_network_restricted", 0.0, 0.0,
                        kTestDurationSec};
  simulcast.ss[0] = {
      std::vector<VideoStream>(),  0,    3, -1, InterLayerPredMode::kOnKeyPic,
      std::vector<SpatialLayer>(), false};
  simulcast.config->link_capacity_kbps = 1000;
  simulcast.config->queue_delay_ms = 100;
  fixture->RunWithAnalyzer(simulcast);
}

// TODO(bugs.webrtc.org/10639) requires simulcast/SVC support in PC framework
// TODO(webrtc:9722): Remove when experiment is cleaned up.
TEST(PCFullStackTest, VP9KSVC_3SL_Medium_Network_Restricted_Trusted_Rate) {
  webrtc::test::ScopedFieldTrials override_trials(
      AppendFieldTrials("WebRTC-Vp9IssueKeyFrameOnLayerDeactivation/Enabled/"));
  auto fixture = CreateVideoQualityTestFixture();
  ParamsWithLogging simulcast;
  simulcast.call.send_side_bwe = true;
  simulcast.video[0] = SvcVp9Video();
  simulcast.analyzer = {"vp9ksvc_3sl_medium_network_restricted_trusted_rate",
                        0.0, 0.0, kTestDurationSec};
  simulcast.ss[0] = {
      std::vector<VideoStream>(),  0,    3, -1, InterLayerPredMode::kOnKeyPic,
      std::vector<SpatialLayer>(), false};
  simulcast.config->link_capacity_kbps = 1000;
  simulcast.config->queue_delay_ms = 100;
  fixture->RunWithAnalyzer(simulcast);
}
#endif  // !defined(WEBRTC_MAC)

#endif  // defined(RTC_ENABLE_VP9)
*/

// Android bots can't handle FullHD, so disable the test.
// TODO(bugs.webrtc.org/9220): Investigate source of flakiness on Mac.
#if defined(WEBRTC_ANDROID) || defined(WEBRTC_MAC)
#define MAYBE_Pc_Simulcast_HD_High DISABLED_Pc_Simulcast_HD_High
#else
#define MAYBE_Pc_Simulcast_HD_High Pc_Simulcast_HD_High
#endif
TEST(PCFullStackTest, MAYBE_Pc_Simulcast_HD_High) {
  webrtc::test::ScopedFieldTrials override_trials(AppendFieldTrials(
      "WebRTC-ForceSimulatedOveruseIntervalMs/1000-50000-300/"));
  std::unique_ptr<NetworkEmulationManager> network_emulation_manager =
      CreateNetworkEmulationManager();
  BuiltInNetworkBehaviorConfig config;
  config.loss_percent = 0;
  config.queue_delay_ms = 100;
  auto fixture = CreateTestFixture(
      "pc_simulcast_HD_high", *network_emulation_manager->time_controller(),
      CreateTwoNetworkLinks(network_emulation_manager.get(), config),
      [](PeerConfigurer* alice) {
        VideoConfig video(1920, 1080, 30);
        video.simulcast_config = VideoSimulcastConfig(3, 2);
        video.temporal_layers_count = 3;
        video.stream_label = "alice-video";
        alice->AddVideoConfig(std::move(video));
      },
      [](PeerConfigurer* bob) {});
  fixture->Run(RunParams(TimeDelta::Seconds(kTestDurationSec)));
}

TEST(PCFullStackTest, Pc_Simulcast_Vp8_3sl_High) {
  std::unique_ptr<NetworkEmulationManager> network_emulation_manager =
      CreateNetworkEmulationManager();
  BuiltInNetworkBehaviorConfig config;
  config.loss_percent = 0;
  config.queue_delay_ms = 100;
  auto fixture = CreateTestFixture(
      "pc_simulcast_vp8_3sl_high",
      *network_emulation_manager->time_controller(),
      CreateTwoNetworkLinks(network_emulation_manager.get(), config),
      [](PeerConfigurer* alice) {
        VideoConfig video(1280, 720, 30);
        video.simulcast_config = VideoSimulcastConfig(3, 2);
        video.stream_label = "alice-video";
        auto frame_generator = CreateFromYuvFileFrameGenerator(
            video, ClipNameToClipPath("ConferenceMotion_1280_720_50"));
        alice->AddVideoConfig(std::move(video), std::move(frame_generator));
      },
      [](PeerConfigurer* bob) {});
  fixture->Run(RunParams(TimeDelta::Seconds(kTestDurationSec)));
}

TEST(PCFullStackTest, Pc_Simulcast_Vp8_3sl_Low) {
  std::unique_ptr<NetworkEmulationManager> network_emulation_manager =
      CreateNetworkEmulationManager();
  BuiltInNetworkBehaviorConfig config;
  config.loss_percent = 0;
  config.queue_delay_ms = 100;
  auto fixture = CreateTestFixture(
      "pc_simulcast_vp8_3sl_low", *network_emulation_manager->time_controller(),
      CreateTwoNetworkLinks(network_emulation_manager.get(), config),
      [](PeerConfigurer* alice) {
        VideoConfig video(1280, 720, 30);
        video.simulcast_config = VideoSimulcastConfig(3, 0);
        video.stream_label = "alice-video";
        auto frame_generator = CreateFromYuvFileFrameGenerator(
            video, ClipNameToClipPath("ConferenceMotion_1280_720_50"));
        alice->AddVideoConfig(std::move(video), std::move(frame_generator));
      },
      [](PeerConfigurer* bob) {});
  fixture->Run(RunParams(TimeDelta::Seconds(kTestDurationSec)));
}

/*
// This test assumes ideal network conditions with target bandwidth being
// available and exercises WebRTC calls with a high target bitrate(100 Mbps).
// Android32 bots can't handle this high bitrate, so disable test for those.
#if defined(WEBRTC_ANDROID)
#define MAYBE_HighBitrateWithFakeCodec DISABLED_HighBitrateWithFakeCodec
#else
#define MAYBE_HighBitrateWithFakeCodec HighBitrateWithFakeCodec
#endif  // defined(WEBRTC_ANDROID)
// TODO(bugs.webrtc.org/10639) Disabled because target bitrate can't be
configured yet. TEST(PCFullStackTest, MAYBE_HighBitrateWithFakeCodec) { auto
fixture = CreateVideoQualityTestFixture(); const int target_bitrate = 100000000;
  ParamsWithLogging generator;
  generator.call.send_side_bwe = true;
  generator.call.call_bitrate_config.min_bitrate_bps = target_bitrate;
  generator.call.call_bitrate_config.start_bitrate_bps = target_bitrate;
  generator.call.call_bitrate_config.max_bitrate_bps = target_bitrate;
  generator.video[0] = {true,
                        360,
                        240,
                        30,
                        target_bitrate / 2,
                        target_bitrate,
                        target_bitrate * 2,
                        false,
                        "FakeCodec",
                        1,
                        0,
                        0,
                        false,
                        false,
                        false,
                        "Generator"};
  generator.analyzer = {"high_bitrate_with_fake_codec", 0.0, 0.0,
                        kTestDurationSec};
  fixture->RunWithAnalyzer(generator);
}

// TODO(bugs.webrtc.org/10639) requires simulcast/SVC support in PC framework
TEST(PCFullStackTest, LargeRoomVP8_5thumb) {
  auto fixture = CreateVideoQualityTestFixture();
  ParamsWithLogging large_room;
  large_room.call.send_side_bwe = true;
  large_room.video[0] = SimulcastVp8VideoHigh();
  large_room.analyzer = {"largeroom_5thumb", 0.0, 0.0, kTestDurationSec};
  large_room.config->loss_percent = 0;
  large_room.config->queue_delay_ms = 100;
  ParamsWithLogging video_params_high;
  video_params_high.video[0] = SimulcastVp8VideoHigh();
  ParamsWithLogging video_params_medium;
  video_params_medium.video[0] = SimulcastVp8VideoMedium();
  ParamsWithLogging video_params_low;
  video_params_low.video[0] = SimulcastVp8VideoLow();

  std::vector<VideoStream> streams = {
      VideoQualityTest::DefaultVideoStream(video_params_low, 0),
      VideoQualityTest::DefaultVideoStream(video_params_medium, 0),
      VideoQualityTest::DefaultVideoStream(video_params_high, 0)};
  large_room.call.num_thumbnails = 5;
  large_room.ss[0] = {
      streams, 2, 1, 0, InterLayerPredMode::kOn, std::vector<SpatialLayer>(),
      false};
  fixture->RunWithAnalyzer(large_room);
}

#if defined(WEBRTC_ANDROID) || defined(WEBRTC_IOS)
// Fails on mobile devices:
// https://bugs.chromium.org/p/webrtc/issues/detail?id=7301
#define MAYBE_LargeRoomVP8_50thumb DISABLED_LargeRoomVP8_50thumb
#define MAYBE_LargeRoomVP8_15thumb DISABLED_LargeRoomVP8_15thumb
#else
#define MAYBE_LargeRoomVP8_50thumb LargeRoomVP8_50thumb
#define MAYBE_LargeRoomVP8_15thumb LargeRoomVP8_15thumb
#endif
// TODO(bugs.webrtc.org/10639) requires simulcast/SVC support in PC framework
TEST(PCFullStackTest, MAYBE_LargeRoomVP8_15thumb) {
  auto fixture = CreateVideoQualityTestFixture();
  ParamsWithLogging large_room;
  large_room.call.send_side_bwe = true;
  large_room.video[0] = SimulcastVp8VideoHigh();
  large_room.analyzer = {"largeroom_15thumb", 0.0, 0.0, kTestDurationSec};
  large_room.config->loss_percent = 0;
  large_room.config->queue_delay_ms = 100;
  ParamsWithLogging video_params_high;
  video_params_high.video[0] = SimulcastVp8VideoHigh();
  ParamsWithLogging video_params_medium;
  video_params_medium.video[0] = SimulcastVp8VideoMedium();
  ParamsWithLogging video_params_low;
  video_params_low.video[0] = SimulcastVp8VideoLow();

  std::vector<VideoStream> streams = {
      VideoQualityTest::DefaultVideoStream(video_params_low, 0),
      VideoQualityTest::DefaultVideoStream(video_params_medium, 0),
      VideoQualityTest::DefaultVideoStream(video_params_high, 0)};
  large_room.call.num_thumbnails = 15;
  large_room.ss[0] = {
      streams, 2, 1, 0, InterLayerPredMode::kOn, std::vector<SpatialLayer>(),
      false};
  fixture->RunWithAnalyzer(large_room);
}

// TODO(bugs.webrtc.org/10639) requires simulcast/SVC support in PC framework
TEST(PCFullStackTest, MAYBE_LargeRoomVP8_50thumb) {
  auto fixture = CreateVideoQualityTestFixture();
  ParamsWithLogging large_room;
  large_room.call.send_side_bwe = true;
  large_room.video[0] = SimulcastVp8VideoHigh();
  large_room.analyzer = {"largeroom_50thumb", 0.0, 0.0, kTestDurationSec};
  large_room.config->loss_percent = 0;
  large_room.config->queue_delay_ms = 100;
  ParamsWithLogging video_params_high;
  video_params_high.video[0] = SimulcastVp8VideoHigh();
  ParamsWithLogging video_params_medium;
  video_params_medium.video[0] = SimulcastVp8VideoMedium();
  ParamsWithLogging video_params_low;
  video_params_low.video[0] = SimulcastVp8VideoLow();

  std::vector<VideoStream> streams = {
      VideoQualityTest::DefaultVideoStream(video_params_low, 0),
      VideoQualityTest::DefaultVideoStream(video_params_medium, 0),
      VideoQualityTest::DefaultVideoStream(video_params_high, 0)};
  large_room.call.num_thumbnails = 50;
  large_room.ss[0] = {
      streams, 2, 1, 0, InterLayerPredMode::kOn, std::vector<SpatialLayer>(),
      false};
  fixture->RunWithAnalyzer(large_room);
}
*/

/*
class PCDualStreamsTest : public ::testing::TestWithParam<int> {};

// Disable dual video test on mobile device becuase it's too heavy.
// TODO(bugs.webrtc.org/9840): Investigate why is this test flaky on MAC.
#if !defined(WEBRTC_ANDROID) && !defined(WEBRTC_IOS) && !defined(WEBRTC_MAC)
// TODO(bugs.webrtc.org/10639) requires simulcast/SVC support in PC framework
TEST_P(PCDualStreamsTest,
       ModeratelyRestricted_SlidesVp8_2TL_Simulcast_Video_Simulcast_High) {
  const int first_stream = GetParam();
  ParamsWithLogging dual_streams;

  // Screenshare Settings.
  dual_streams.screenshare[first_stream] = {true, false, 10};
  dual_streams.video[first_stream] = {true,    1850,  1110,  5, 800000, 2500000,
                                      2500000, false, "VP8", 2, 1,      400000,
                                      false,   false, false, ""};

  ParamsWithLogging screenshare_params_high;
  screenshare_params_high.video[0] = {
      true,  1850, 1110, 60,     600000, 1250000, 1250000, false,
      "VP8", 2,    0,    400000, false,  false,   false,   ""};
  VideoQualityTest::Params screenshare_params_low;
  screenshare_params_low.video[0] = {true,    1850,  1110,  5, 30000, 200000,
                                     1000000, false, "VP8", 2, 0,     400000,
                                     false,   false, false, ""};
  std::vector<VideoStream> screenhsare_streams = {
      VideoQualityTest::DefaultVideoStream(screenshare_params_low, 0),
      VideoQualityTest::DefaultVideoStream(screenshare_params_high, 0)};

  dual_streams.ss[first_stream] = {
      screenhsare_streams,         1,    1, 0, InterLayerPredMode::kOn,
      std::vector<SpatialLayer>(), false};

  // Video settings.
  dual_streams.video[1 - first_stream] = SimulcastVp8VideoHigh();

  ParamsWithLogging video_params_high;
  video_params_high.video[0] = SimulcastVp8VideoHigh();
  ParamsWithLogging video_params_medium;
  video_params_medium.video[0] = SimulcastVp8VideoMedium();
  ParamsWithLogging video_params_low;
  video_params_low.video[0] = SimulcastVp8VideoLow();
  std::vector<VideoStream> streams = {
      VideoQualityTest::DefaultVideoStream(video_params_low, 0),
      VideoQualityTest::DefaultVideoStream(video_params_medium, 0),
      VideoQualityTest::DefaultVideoStream(video_params_high, 0)};

  dual_streams.ss[1 - first_stream] = {
      streams, 2, 1, 0, InterLayerPredMode::kOn, std::vector<SpatialLayer>(),
      false};

  // Call settings.
  dual_streams.call.send_side_bwe = true;
  dual_streams.call.dual_video = true;
  std::string test_label = "dualstreams_moderately_restricted_screenshare_" +
                           std::to_string(first_stream);
  dual_streams.analyzer = {test_label, 0.0, 0.0, kTestDurationSec};
  dual_streams.config->loss_percent = 1;
  dual_streams.config->link_capacity_kbps = 7500;
  dual_streams.config->queue_length_packets = 30;
  dual_streams.config->queue_delay_ms = 100;

  auto fixture = CreateVideoQualityTestFixture();
  fixture->RunWithAnalyzer(dual_streams);
}
#endif  // !defined(WEBRTC_ANDROID) && !defined(WEBRTC_IOS) &&
        // !defined(WEBRTC_MAC)

// TODO(bugs.webrtc.org/10639) requires simulcast/SVC support in PC framework
TEST_P(PCDualStreamsTest, Conference_Restricted) {
  const int first_stream = GetParam();
  ParamsWithLogging dual_streams;

  // Screenshare Settings.
  dual_streams.screenshare[first_stream] = {true, false, 10};
  dual_streams.video[first_stream] = {true,    1850,  1110,  5, 800000, 2500000,
                                      2500000, false, "VP8", 3, 2,      400000,
                                      false,   false, false, ""};
  // Video settings.
  dual_streams.video[1 - first_stream] = {
      true,   1280,
      720,    30,
      150000, 500000,
      700000, false,
      "VP8",  3,
      2,      400000,
      false,  false,
      false,  ClipNameToClipPath("ConferenceMotion_1280_720_50")};

  // Call settings.
  dual_streams.call.send_side_bwe = true;
  dual_streams.call.dual_video = true;
  std::string test_label = "dualstreams_conference_restricted_screenshare_" +
                           std::to_string(first_stream);
  dual_streams.analyzer = {test_label, 0.0, 0.0, kTestDurationSec};
  dual_streams.config->loss_percent = 1;
  dual_streams.config->link_capacity_kbps = 5000;
  dual_streams.config->queue_length_packets = 30;
  dual_streams.config->queue_delay_ms = 100;

  auto fixture = CreateVideoQualityTestFixture();
  fixture->RunWithAnalyzer(dual_streams);
}

INSTANTIATE_TEST_SUITE_P(PCFullStackTest,
                         PCDualStreamsTest,
                         ::testing::Values(0, 1));
*/

}  // namespace webrtc
