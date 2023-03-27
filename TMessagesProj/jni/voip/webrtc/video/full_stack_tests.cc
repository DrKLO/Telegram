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

#include "absl/flags/flag.h"
#include "absl/flags/parse.h"
#include "absl/types/optional.h"
#include "api/test/simulated_network.h"
#include "api/test/test_dependency_factory.h"
#include "api/test/video_quality_test_fixture.h"
#include "api/video_codecs/sdp_video_format.h"
#include "api/video_codecs/video_codec.h"
#include "api/video_codecs/vp9_profile.h"
#include "modules/video_coding/codecs/vp9/include/vp9.h"
#include "system_wrappers/include/field_trial.h"
#include "test/field_trial.h"
#include "test/gtest.h"
#include "test/testsupport/file_utils.h"
#include "video/config/video_encoder_config.h"
#include "video/video_quality_test.h"

ABSL_FLAG(std::string,
          rtc_event_log_name,
          "",
          "Filename for rtc event log. Two files "
          "with \"_send\" and \"_recv\" suffixes will be created.");
ABSL_FLAG(std::string,
          rtp_dump_name,
          "",
          "Filename for dumped received RTP stream.");
ABSL_FLAG(std::string,
          encoded_frame_path,
          "",
          "The base path for encoded frame logs. Created files will have "
          "the form <encoded_frame_path>.<n>.(recv|send.<m>).ivf");

namespace webrtc {

namespace {
static const int kFullStackTestDurationSecs = 45;

struct ParamsWithLogging : public VideoQualityTest::Params {
 public:
  ParamsWithLogging() {
    // Use these logging flags by default, for everything.
    logging = {absl::GetFlag(FLAGS_rtc_event_log_name),
               absl::GetFlag(FLAGS_rtp_dump_name),
               absl::GetFlag(FLAGS_encoded_frame_path)};
    this->config = BuiltInNetworkBehaviorConfig();
  }
};

std::unique_ptr<VideoQualityTestFixtureInterface>
CreateVideoQualityTestFixture() {
  // The components will normally be nullptr (= use defaults), but it's possible
  // for external test runners to override the list of injected components.
  auto components = TestDependencyFactory::GetInstance().CreateComponents();
  return std::make_unique<VideoQualityTest>(std::move(components));
}

// Takes the current active field trials set, and appends some new trials.
std::string AppendFieldTrials(std::string new_trial_string) {
  return std::string(field_trial::GetFieldTrialString()) + new_trial_string;
}

std::string ClipNameToClipPath(const char* clip_name) {
  return test::ResourcePath(clip_name, "yuv");
}
}  // namespace

// VideoQualityTest::Params params = {
//   { ... },      // Common.
//   { ... },      // Video-specific settings.
//   { ... },      // Screenshare-specific settings.
//   { ... },      // Analyzer settings.
//   pipe,         // FakeNetworkPipe::Config
//   { ... },      // Spatial scalability.
//   logs          // bool
// };

#if defined(RTC_ENABLE_VP9)
TEST(FullStackTest, Foreman_Cif_Net_Delay_0_0_Plr_0_VP9) {
  auto fixture = CreateVideoQualityTestFixture();
  ParamsWithLogging foreman_cif;
  foreman_cif.call.send_side_bwe = true;
  foreman_cif.video[0] = {
      true,   352,    288,    30,
      700000, 700000, 700000, false,
      "VP9",  1,      0,      0,
      false,  false,  true,   ClipNameToClipPath("foreman_cif")};
  foreman_cif.analyzer = {"foreman_cif_net_delay_0_0_plr_0_VP9", 0.0, 0.0,
                          kFullStackTestDurationSecs};
  fixture->RunWithAnalyzer(foreman_cif);
}

TEST(GenericDescriptorTest,
     Foreman_Cif_Delay_50_0_Plr_5_VP9_Generic_Descriptor) {
  auto fixture = CreateVideoQualityTestFixture();
  ParamsWithLogging foreman_cif;
  foreman_cif.call.send_side_bwe = true;
  foreman_cif.video[0] = {
      true,  352,    288,     30,
      30000, 500000, 2000000, false,
      "VP9", 1,      0,       0,
      false, false,  true,    ClipNameToClipPath("foreman_cif")};
  foreman_cif.analyzer = {"foreman_cif_delay_50_0_plr_5_VP9_generic_descriptor",
                          0.0, 0.0, kFullStackTestDurationSecs};
  foreman_cif.config->loss_percent = 5;
  foreman_cif.config->queue_delay_ms = 50;
  foreman_cif.call.generic_descriptor = true;
  fixture->RunWithAnalyzer(foreman_cif);
}

TEST(FullStackTest, Generator_Net_Delay_0_0_Plr_0_VP9Profile2) {
  // Profile 2 might not be available on some platforms until
  // https://bugs.chromium.org/p/webm/issues/detail?id=1544 is solved.
  bool profile_2_is_supported = false;
  for (const auto& codec : SupportedVP9Codecs()) {
    if (ParseSdpForVP9Profile(codec.parameters)
            .value_or(VP9Profile::kProfile0) == VP9Profile::kProfile2) {
      profile_2_is_supported = true;
    }
  }
  if (!profile_2_is_supported)
    return;
  auto fixture = CreateVideoQualityTestFixture();

  SdpVideoFormat::Parameters vp92 = {
      {kVP9FmtpProfileId, VP9ProfileToString(VP9Profile::kProfile2)}};
  ParamsWithLogging generator;
  generator.call.send_side_bwe = true;
  generator.video[0] = {
      true, 352, 288, 30,    700000, 700000, 700000,          false, "VP9",
      1,    0,   0,   false, false,  true,   "GeneratorI010", 0,     vp92};
  generator.analyzer = {"generator_net_delay_0_0_plr_0_VP9Profile2", 0.0, 0.0,
                        kFullStackTestDurationSecs};
  fixture->RunWithAnalyzer(generator);
}

TEST(FullStackTest, Foreman_Cif_Net_Delay_0_0_Plr_0_Multiplex) {
  auto fixture = CreateVideoQualityTestFixture();
  ParamsWithLogging foreman_cif;
  foreman_cif.call.send_side_bwe = true;
  foreman_cif.video[0] = {
      true,        352,    288,    30,
      700000,      700000, 700000, false,
      "multiplex", 1,      0,      0,
      false,       false,  false,  ClipNameToClipPath("foreman_cif")};
  foreman_cif.analyzer = {"foreman_cif_net_delay_0_0_plr_0_Multiplex", 0.0, 0.0,
                          kFullStackTestDurationSecs};
  fixture->RunWithAnalyzer(foreman_cif);
}

TEST(FullStackTest, Generator_Net_Delay_0_0_Plr_0_Multiplex) {
  auto fixture = CreateVideoQualityTestFixture();

  ParamsWithLogging generator;
  generator.call.send_side_bwe = true;
  generator.video[0] = {
      true,        352, 288, 30, 700000, 700000, 700000, false,
      "multiplex", 1,   0,   0,  false,  false,  false,  "GeneratorI420A"};
  generator.analyzer = {"generator_net_delay_0_0_plr_0_Multiplex", 0.0, 0.0,
                        kFullStackTestDurationSecs};
  fixture->RunWithAnalyzer(generator);
}

#endif  // defined(RTC_ENABLE_VP9)

#if defined(WEBRTC_LINUX)
// Crashes on the linux trusty perf bot: bugs.webrtc.org/9129.
#define MAYBE_Net_Delay_0_0_Plr_0 DISABLED_Net_Delay_0_0_Plr_0
#else
#define MAYBE_Net_Delay_0_0_Plr_0 Net_Delay_0_0_Plr_0
#endif
TEST(FullStackTest, MAYBE_Net_Delay_0_0_Plr_0) {
  auto fixture = CreateVideoQualityTestFixture();
  ParamsWithLogging paris_qcif;
  paris_qcif.call.send_side_bwe = true;
  paris_qcif.video[0] = {
      true,   176,    144,    30,
      300000, 300000, 300000, false,
      "VP8",  1,      0,      0,
      false,  false,  true,   ClipNameToClipPath("paris_qcif")};
  paris_qcif.analyzer = {"net_delay_0_0_plr_0", 36.0, 0.96,
                         kFullStackTestDurationSecs};
  fixture->RunWithAnalyzer(paris_qcif);
}

TEST(GenericDescriptorTest,
     Foreman_Cif_Net_Delay_0_0_Plr_0_Generic_Descriptor) {
  auto fixture = CreateVideoQualityTestFixture();
  // TODO(pbos): Decide on psnr/ssim thresholds for foreman_cif.
  ParamsWithLogging foreman_cif;
  foreman_cif.call.send_side_bwe = true;
  foreman_cif.video[0] = {
      true,   352,    288,    30,
      700000, 700000, 700000, false,
      "VP8",  1,      0,      0,
      false,  false,  true,   ClipNameToClipPath("foreman_cif")};
  foreman_cif.analyzer = {"foreman_cif_net_delay_0_0_plr_0_generic_descriptor",
                          0.0, 0.0, kFullStackTestDurationSecs};
  foreman_cif.call.generic_descriptor = true;
  fixture->RunWithAnalyzer(foreman_cif);
}

TEST(GenericDescriptorTest,
     Foreman_Cif_30kbps_Net_Delay_0_0_Plr_0_Generic_Descriptor) {
  auto fixture = CreateVideoQualityTestFixture();
  ParamsWithLogging foreman_cif;
  foreman_cif.call.send_side_bwe = true;
  foreman_cif.video[0] = {
      true,  352,   288,   10,
      30000, 30000, 30000, false,
      "VP8", 1,     0,     0,
      false, false, true,  ClipNameToClipPath("foreman_cif")};
  foreman_cif.analyzer = {
      "foreman_cif_30kbps_net_delay_0_0_plr_0_generic_descriptor", 0.0, 0.0,
      kFullStackTestDurationSecs};
  foreman_cif.call.generic_descriptor = true;
  fixture->RunWithAnalyzer(foreman_cif);
}

// Link capacity below default start rate.
TEST(FullStackTest, Foreman_Cif_Link_150kbps_Net_Delay_0_0_Plr_0) {
  auto fixture = CreateVideoQualityTestFixture();
  ParamsWithLogging foreman_cif;
  foreman_cif.call.send_side_bwe = true;
  foreman_cif.video[0] = {
      true,  352,    288,     30,
      30000, 500000, 2000000, false,
      "VP8", 1,      0,       0,
      false, false,  true,    ClipNameToClipPath("foreman_cif")};
  foreman_cif.analyzer = {"foreman_cif_link_150kbps_net_delay_0_0_plr_0", 0.0,
                          0.0, kFullStackTestDurationSecs};
  foreman_cif.config->link_capacity_kbps = 150;
  fixture->RunWithAnalyzer(foreman_cif);
}

// Restricted network and encoder overproducing by 30%.
TEST(FullStackTest,
     Foreman_Cif_Link_150kbps_Delay100ms_30pkts_Queue_Overshoot30) {
  auto fixture = CreateVideoQualityTestFixture();
  ParamsWithLogging foreman_cif;
  foreman_cif.call.send_side_bwe = true;
  foreman_cif.video[0] = {
      true,  352,    288,     30,
      30000, 500000, 2000000, false,
      "VP8", 1,      0,       0,
      false, false,  true,    ClipNameToClipPath("foreman_cif"),
      0,     {},     1.30};
  foreman_cif.analyzer = {
      "foreman_cif_link_150kbps_delay100ms_30pkts_queue_overshoot30", 0.0, 0.0,
      kFullStackTestDurationSecs};
  foreman_cif.config->link_capacity_kbps = 150;
  foreman_cif.config->queue_length_packets = 30;
  foreman_cif.config->queue_delay_ms = 100;
  fixture->RunWithAnalyzer(foreman_cif);
}

// Weak 3G-style link: 250kbps, 1% loss, 100ms delay, 15 packets queue.
// Packet rate and loss are low enough that loss will happen with ~3s interval.
// This triggers protection overhead to toggle between zero and non-zero.
// Link queue is restrictive enough to trigger loss on probes.
TEST(FullStackTest, Foreman_Cif_Link_250kbps_Delay100ms_10pkts_Loss1) {
  auto fixture = CreateVideoQualityTestFixture();
  ParamsWithLogging foreman_cif;
  foreman_cif.call.send_side_bwe = true;
  foreman_cif.video[0] = {
      true,  352,    288,     30,
      30000, 500000, 2000000, false,
      "VP8", 1,      0,       0,
      false, false,  true,    ClipNameToClipPath("foreman_cif"),
      0,     {},     1.30};
  foreman_cif.analyzer = {"foreman_cif_link_250kbps_delay100ms_10pkts_loss1",
                          0.0, 0.0, kFullStackTestDurationSecs};
  foreman_cif.config->link_capacity_kbps = 250;
  foreman_cif.config->queue_length_packets = 10;
  foreman_cif.config->queue_delay_ms = 100;
  foreman_cif.config->loss_percent = 1;
  fixture->RunWithAnalyzer(foreman_cif);
}

TEST(GenericDescriptorTest, Foreman_Cif_Delay_50_0_Plr_5_Generic_Descriptor) {
  auto fixture = CreateVideoQualityTestFixture();
  ParamsWithLogging foreman_cif;
  foreman_cif.call.send_side_bwe = true;
  foreman_cif.video[0] = {
      true,  352,    288,     30,
      30000, 500000, 2000000, false,
      "VP8", 1,      0,       0,
      false, false,  true,    ClipNameToClipPath("foreman_cif")};
  foreman_cif.analyzer = {"foreman_cif_delay_50_0_plr_5_generic_descriptor",
                          0.0, 0.0, kFullStackTestDurationSecs};
  foreman_cif.config->loss_percent = 5;
  foreman_cif.config->queue_delay_ms = 50;
  foreman_cif.call.generic_descriptor = true;
  fixture->RunWithAnalyzer(foreman_cif);
}

TEST(GenericDescriptorTest,
     Foreman_Cif_Delay_50_0_Plr_5_Ulpfec_Generic_Descriptor) {
  auto fixture = CreateVideoQualityTestFixture();
  ParamsWithLogging foreman_cif;
  foreman_cif.call.send_side_bwe = true;
  foreman_cif.video[0] = {
      true,  352,    288,     30,
      30000, 500000, 2000000, false,
      "VP8", 1,      0,       0,
      true,  false,  true,    ClipNameToClipPath("foreman_cif")};
  foreman_cif.analyzer = {
      "foreman_cif_delay_50_0_plr_5_ulpfec_generic_descriptor", 0.0, 0.0,
      kFullStackTestDurationSecs};
  foreman_cif.config->loss_percent = 5;
  foreman_cif.config->queue_delay_ms = 50;
  foreman_cif.call.generic_descriptor = true;
  fixture->RunWithAnalyzer(foreman_cif);
}

TEST(FullStackTest, Foreman_Cif_Delay_50_0_Plr_5_Flexfec) {
  auto fixture = CreateVideoQualityTestFixture();
  ParamsWithLogging foreman_cif;
  foreman_cif.call.send_side_bwe = true;
  foreman_cif.video[0] = {
      true,  352,    288,     30,
      30000, 500000, 2000000, false,
      "VP8", 1,      0,       0,
      false, true,   true,    ClipNameToClipPath("foreman_cif")};
  foreman_cif.analyzer = {"foreman_cif_delay_50_0_plr_5_flexfec", 0.0, 0.0,
                          kFullStackTestDurationSecs};
  foreman_cif.config->loss_percent = 5;
  foreman_cif.config->queue_delay_ms = 50;
  fixture->RunWithAnalyzer(foreman_cif);
}

TEST(FullStackTest, Foreman_Cif_500kbps_Delay_50_0_Plr_3_Flexfec) {
  auto fixture = CreateVideoQualityTestFixture();
  ParamsWithLogging foreman_cif;
  foreman_cif.call.send_side_bwe = true;
  foreman_cif.video[0] = {
      true,  352,    288,     30,
      30000, 500000, 2000000, false,
      "VP8", 1,      0,       0,
      false, true,   true,    ClipNameToClipPath("foreman_cif")};
  foreman_cif.analyzer = {"foreman_cif_500kbps_delay_50_0_plr_3_flexfec", 0.0,
                          0.0, kFullStackTestDurationSecs};
  foreman_cif.config->loss_percent = 3;
  foreman_cif.config->link_capacity_kbps = 500;
  foreman_cif.config->queue_delay_ms = 50;
  fixture->RunWithAnalyzer(foreman_cif);
}

TEST(FullStackTest, Foreman_Cif_500kbps_Delay_50_0_Plr_3_Ulpfec) {
  auto fixture = CreateVideoQualityTestFixture();
  ParamsWithLogging foreman_cif;
  foreman_cif.call.send_side_bwe = true;
  foreman_cif.video[0] = {
      true,  352,    288,     30,
      30000, 500000, 2000000, false,
      "VP8", 1,      0,       0,
      true,  false,  true,    ClipNameToClipPath("foreman_cif")};
  foreman_cif.analyzer = {"foreman_cif_500kbps_delay_50_0_plr_3_ulpfec", 0.0,
                          0.0, kFullStackTestDurationSecs};
  foreman_cif.config->loss_percent = 3;
  foreman_cif.config->link_capacity_kbps = 500;
  foreman_cif.config->queue_delay_ms = 50;
  fixture->RunWithAnalyzer(foreman_cif);
}

#if defined(WEBRTC_USE_H264)
TEST(FullStackTest, Foreman_Cif_Net_Delay_0_0_Plr_0_H264) {
  auto fixture = CreateVideoQualityTestFixture();
  // TODO(pbos): Decide on psnr/ssim thresholds for foreman_cif.
  ParamsWithLogging foreman_cif;
  foreman_cif.call.send_side_bwe = true;
  foreman_cif.video[0] = {
      true,   352,    288,    30,
      700000, 700000, 700000, false,
      "H264", 1,      0,      0,
      false,  false,  true,   ClipNameToClipPath("foreman_cif")};
  foreman_cif.analyzer = {"foreman_cif_net_delay_0_0_plr_0_H264", 0.0, 0.0,
                          kFullStackTestDurationSecs};
  fixture->RunWithAnalyzer(foreman_cif);
}

TEST(FullStackTest, Foreman_Cif_30kbps_Net_Delay_0_0_Plr_0_H264) {
  auto fixture = CreateVideoQualityTestFixture();
  ParamsWithLogging foreman_cif;
  foreman_cif.call.send_side_bwe = true;
  foreman_cif.video[0] = {
      true,   352,   288,   10,
      30000,  30000, 30000, false,
      "H264", 1,     0,     0,
      false,  false, true,  ClipNameToClipPath("foreman_cif")};
  foreman_cif.analyzer = {"foreman_cif_30kbps_net_delay_0_0_plr_0_H264", 0.0,
                          0.0, kFullStackTestDurationSecs};
  fixture->RunWithAnalyzer(foreman_cif);
}

TEST(GenericDescriptorTest,
     Foreman_Cif_Delay_50_0_Plr_5_H264_Generic_Descriptor) {
  auto fixture = CreateVideoQualityTestFixture();
  ParamsWithLogging foreman_cif;
  foreman_cif.call.send_side_bwe = true;
  foreman_cif.video[0] = {
      true,   352,    288,     30,
      30000,  500000, 2000000, false,
      "H264", 1,      0,       0,
      false,  false,  true,    ClipNameToClipPath("foreman_cif")};
  foreman_cif.analyzer = {
      "foreman_cif_delay_50_0_plr_5_H264_generic_descriptor", 0.0, 0.0,
      kFullStackTestDurationSecs};
  foreman_cif.config->loss_percent = 5;
  foreman_cif.config->queue_delay_ms = 50;
  foreman_cif.call.generic_descriptor = true;
  fixture->RunWithAnalyzer(foreman_cif);
}

TEST(FullStackTest, Foreman_Cif_Delay_50_0_Plr_5_H264_Sps_Pps_Idr) {
  test::ScopedFieldTrials override_field_trials(
      AppendFieldTrials("WebRTC-SpsPpsIdrIsH264Keyframe/Enabled/"));
  auto fixture = CreateVideoQualityTestFixture();

  ParamsWithLogging foreman_cif;
  foreman_cif.call.send_side_bwe = true;
  foreman_cif.video[0] = {
      true,   352,    288,     30,
      30000,  500000, 2000000, false,
      "H264", 1,      0,       0,
      false,  false,  true,    ClipNameToClipPath("foreman_cif")};
  foreman_cif.analyzer = {"foreman_cif_delay_50_0_plr_5_H264_sps_pps_idr", 0.0,
                          0.0, kFullStackTestDurationSecs};
  foreman_cif.config->loss_percent = 5;
  foreman_cif.config->queue_delay_ms = 50;
  fixture->RunWithAnalyzer(foreman_cif);
}

// Verify that this is worth the bot time, before enabling.
TEST(FullStackTest, Foreman_Cif_Delay_50_0_Plr_5_H264_Flexfec) {
  auto fixture = CreateVideoQualityTestFixture();
  ParamsWithLogging foreman_cif;
  foreman_cif.call.send_side_bwe = true;
  foreman_cif.video[0] = {
      true,   352,    288,     30,
      30000,  500000, 2000000, false,
      "H264", 1,      0,       0,
      false,  true,   true,    ClipNameToClipPath("foreman_cif")};
  foreman_cif.analyzer = {"foreman_cif_delay_50_0_plr_5_H264_flexfec", 0.0, 0.0,
                          kFullStackTestDurationSecs};
  foreman_cif.config->loss_percent = 5;
  foreman_cif.config->queue_delay_ms = 50;
  fixture->RunWithAnalyzer(foreman_cif);
}

// Ulpfec with H264 is an unsupported combination, so this test is only useful
// for debugging. It is therefore disabled by default.
TEST(FullStackTest, DISABLED_Foreman_Cif_Delay_50_0_Plr_5_H264_Ulpfec) {
  auto fixture = CreateVideoQualityTestFixture();
  ParamsWithLogging foreman_cif;
  foreman_cif.call.send_side_bwe = true;
  foreman_cif.video[0] = {
      true,   352,    288,     30,
      30000,  500000, 2000000, false,
      "H264", 1,      0,       0,
      true,   false,  true,    ClipNameToClipPath("foreman_cif")};
  foreman_cif.analyzer = {"foreman_cif_delay_50_0_plr_5_H264_ulpfec", 0.0, 0.0,
                          kFullStackTestDurationSecs};
  foreman_cif.config->loss_percent = 5;
  foreman_cif.config->queue_delay_ms = 50;
  fixture->RunWithAnalyzer(foreman_cif);
}
#endif  // defined(WEBRTC_USE_H264)

TEST(FullStackTest, Foreman_Cif_500kbps) {
  auto fixture = CreateVideoQualityTestFixture();
  ParamsWithLogging foreman_cif;
  foreman_cif.call.send_side_bwe = true;
  foreman_cif.video[0] = {
      true,  352,    288,     30,
      30000, 500000, 2000000, false,
      "VP8", 1,      0,       0,
      false, false,  true,    ClipNameToClipPath("foreman_cif")};
  foreman_cif.analyzer = {"foreman_cif_500kbps", 0.0, 0.0,
                          kFullStackTestDurationSecs};
  foreman_cif.config->queue_length_packets = 0;
  foreman_cif.config->queue_delay_ms = 0;
  foreman_cif.config->link_capacity_kbps = 500;
  fixture->RunWithAnalyzer(foreman_cif);
}

TEST(FullStackTest, Foreman_Cif_500kbps_32pkts_Queue) {
  auto fixture = CreateVideoQualityTestFixture();
  ParamsWithLogging foreman_cif;
  foreman_cif.call.send_side_bwe = true;
  foreman_cif.video[0] = {
      true,  352,    288,     30,
      30000, 500000, 2000000, false,
      "VP8", 1,      0,       0,
      false, false,  true,    ClipNameToClipPath("foreman_cif")};
  foreman_cif.analyzer = {"foreman_cif_500kbps_32pkts_queue", 0.0, 0.0,
                          kFullStackTestDurationSecs};
  foreman_cif.config->queue_length_packets = 32;
  foreman_cif.config->queue_delay_ms = 0;
  foreman_cif.config->link_capacity_kbps = 500;
  fixture->RunWithAnalyzer(foreman_cif);
}

TEST(FullStackTest, Foreman_Cif_500kbps_100ms) {
  auto fixture = CreateVideoQualityTestFixture();
  ParamsWithLogging foreman_cif;
  foreman_cif.call.send_side_bwe = true;
  foreman_cif.video[0] = {
      true,  352,    288,     30,
      30000, 500000, 2000000, false,
      "VP8", 1,      0,       0,
      false, false,  true,    ClipNameToClipPath("foreman_cif")};
  foreman_cif.analyzer = {"foreman_cif_500kbps_100ms", 0.0, 0.0,
                          kFullStackTestDurationSecs};
  foreman_cif.config->queue_length_packets = 0;
  foreman_cif.config->queue_delay_ms = 100;
  foreman_cif.config->link_capacity_kbps = 500;
  fixture->RunWithAnalyzer(foreman_cif);
}

TEST(GenericDescriptorTest,
     Foreman_Cif_500kbps_100ms_32pkts_Queue_Generic_Descriptor) {
  auto fixture = CreateVideoQualityTestFixture();
  ParamsWithLogging foreman_cif;
  foreman_cif.call.send_side_bwe = true;
  foreman_cif.video[0] = {
      true,  352,    288,     30,
      30000, 500000, 2000000, false,
      "VP8", 1,      0,       0,
      false, false,  true,    ClipNameToClipPath("foreman_cif")};
  foreman_cif.analyzer = {
      "foreman_cif_500kbps_100ms_32pkts_queue_generic_descriptor", 0.0, 0.0,
      kFullStackTestDurationSecs};
  foreman_cif.config->queue_length_packets = 32;
  foreman_cif.config->queue_delay_ms = 100;
  foreman_cif.config->link_capacity_kbps = 500;
  foreman_cif.call.generic_descriptor = true;
  fixture->RunWithAnalyzer(foreman_cif);
}

TEST(FullStackTest, Foreman_Cif_500kbps_100ms_32pkts_Queue_Recv_Bwe) {
  auto fixture = CreateVideoQualityTestFixture();
  ParamsWithLogging foreman_cif;
  foreman_cif.call.send_side_bwe = false;
  foreman_cif.video[0] = {
      true,  352,    288,     30,
      30000, 500000, 2000000, false,
      "VP8", 1,      0,       0,
      false, false,  true,    ClipNameToClipPath("foreman_cif")};
  foreman_cif.analyzer = {"foreman_cif_500kbps_100ms_32pkts_queue_recv_bwe",
                          0.0, 0.0, kFullStackTestDurationSecs};
  foreman_cif.config->queue_length_packets = 32;
  foreman_cif.config->queue_delay_ms = 100;
  foreman_cif.config->link_capacity_kbps = 500;
  fixture->RunWithAnalyzer(foreman_cif);
}

TEST(FullStackTest, Foreman_Cif_1000kbps_100ms_32pkts_Queue) {
  auto fixture = CreateVideoQualityTestFixture();
  ParamsWithLogging foreman_cif;
  foreman_cif.call.send_side_bwe = true;
  foreman_cif.video[0] = {
      true,  352,     288,     30,
      30000, 2000000, 2000000, false,
      "VP8", 1,       0,       0,
      false, false,   true,    ClipNameToClipPath("foreman_cif")};
  foreman_cif.analyzer = {"foreman_cif_1000kbps_100ms_32pkts_queue", 0.0, 0.0,
                          kFullStackTestDurationSecs};
  foreman_cif.config->queue_length_packets = 32;
  foreman_cif.config->queue_delay_ms = 100;
  foreman_cif.config->link_capacity_kbps = 1000;
  fixture->RunWithAnalyzer(foreman_cif);
}

// TODO(sprang): Remove this if we have the similar ModerateLimits below?
TEST(FullStackTest, Conference_Motion_Hd_2000kbps_100ms_32pkts_Queue) {
  auto fixture = CreateVideoQualityTestFixture();
  ParamsWithLogging conf_motion_hd;
  conf_motion_hd.call.send_side_bwe = true;
  conf_motion_hd.video[0] = {
      true,    1280,
      720,     50,
      30000,   3000000,
      3000000, false,
      "VP8",   1,
      0,       0,
      false,   false,
      false,   ClipNameToClipPath("ConferenceMotion_1280_720_50")};
  conf_motion_hd.analyzer = {"conference_motion_hd_2000kbps_100ms_32pkts_queue",
                             0.0, 0.0, kFullStackTestDurationSecs};
  conf_motion_hd.config->queue_length_packets = 32;
  conf_motion_hd.config->queue_delay_ms = 100;
  conf_motion_hd.config->link_capacity_kbps = 2000;
  fixture->RunWithAnalyzer(conf_motion_hd);
}

TEST(GenericDescriptorTest,
     Conference_Motion_Hd_2tl_Moderate_Limits_Generic_Descriptor) {
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
      kFullStackTestDurationSecs};
  conf_motion_hd.config->queue_length_packets = 50;
  conf_motion_hd.config->loss_percent = 3;
  conf_motion_hd.config->queue_delay_ms = 100;
  conf_motion_hd.config->link_capacity_kbps = 2000;
  conf_motion_hd.call.generic_descriptor = true;
  fixture->RunWithAnalyzer(conf_motion_hd);
}

TEST(FullStackTest, Conference_Motion_Hd_3tl_Moderate_Limits) {
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
                             0.0, kFullStackTestDurationSecs};
  conf_motion_hd.config->queue_length_packets = 50;
  conf_motion_hd.config->loss_percent = 3;
  conf_motion_hd.config->queue_delay_ms = 100;
  conf_motion_hd.config->link_capacity_kbps = 2000;
  fixture->RunWithAnalyzer(conf_motion_hd);
}

TEST(FullStackTest, Conference_Motion_Hd_4tl_Moderate_Limits) {
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
                             0.0, kFullStackTestDurationSecs};
  conf_motion_hd.config->queue_length_packets = 50;
  conf_motion_hd.config->loss_percent = 3;
  conf_motion_hd.config->queue_delay_ms = 100;
  conf_motion_hd.config->link_capacity_kbps = 2000;
  fixture->RunWithAnalyzer(conf_motion_hd);
}

TEST(FullStackTest, Conference_Motion_Hd_3tl_Alt_Moderate_Limits) {
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
                             0.0, 0.0, kFullStackTestDurationSecs};
  conf_motion_hd.config->queue_length_packets = 50;
  conf_motion_hd.config->loss_percent = 3;
  conf_motion_hd.config->queue_delay_ms = 100;
  conf_motion_hd.config->link_capacity_kbps = 2000;
  fixture->RunWithAnalyzer(conf_motion_hd);
}

TEST(FullStackTest, Conference_Motion_Hd_3tl_Alt_Heavy_Moderate_Limits) {
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
      kFullStackTestDurationSecs};
  conf_motion_hd.config->queue_length_packets = 50;
  conf_motion_hd.config->loss_percent = 3;
  conf_motion_hd.config->queue_delay_ms = 100;
  conf_motion_hd.config->link_capacity_kbps = 2000;
  fixture->RunWithAnalyzer(conf_motion_hd);
}

#if defined(RTC_ENABLE_VP9)
TEST(FullStackTest, Conference_Motion_Hd_2000kbps_100ms_32pkts_Queue_Vp9) {
  auto fixture = CreateVideoQualityTestFixture();
  ParamsWithLogging conf_motion_hd;
  conf_motion_hd.call.send_side_bwe = true;
  conf_motion_hd.video[0] = {
      true,    1280,
      720,     50,
      30000,   3000000,
      3000000, false,
      "VP9",   1,
      0,       0,
      false,   false,
      false,   ClipNameToClipPath("ConferenceMotion_1280_720_50")};
  conf_motion_hd.analyzer = {
      "conference_motion_hd_2000kbps_100ms_32pkts_queue_vp9", 0.0, 0.0,
      kFullStackTestDurationSecs};
  conf_motion_hd.config->queue_length_packets = 32;
  conf_motion_hd.config->queue_delay_ms = 100;
  conf_motion_hd.config->link_capacity_kbps = 2000;
  fixture->RunWithAnalyzer(conf_motion_hd);
}
#endif

TEST(FullStackTest, Screenshare_Slides) {
  auto fixture = CreateVideoQualityTestFixture();
  ParamsWithLogging screenshare;
  screenshare.call.send_side_bwe = true;
  screenshare.video[0] = {true,    1850,  1110,  5, 50000, 200000,
                          1000000, false, "VP8", 2, 1,     400000,
                          false,   false, false, ""};
  screenshare.screenshare[0] = {true, false, 10};
  screenshare.analyzer = {"screenshare_slides", 0.0, 0.0,
                          kFullStackTestDurationSecs};
  fixture->RunWithAnalyzer(screenshare);
}

#if !defined(WEBRTC_MAC) && !defined(WEBRTC_WIN)
// TODO(bugs.webrtc.org/9840): Investigate why is this test flaky on Win/Mac.
TEST(FullStackTest, Screenshare_Slides_Simulcast) {
  auto fixture = CreateVideoQualityTestFixture();
  ParamsWithLogging screenshare;
  screenshare.call.send_side_bwe = true;
  screenshare.screenshare[0] = {true, false, 10};
  screenshare.video[0] = {true,    1850,  1110,  30, 800000, 2500000,
                          2500000, false, "VP8", 2,  1,      400000,
                          false,   false, false, ""};
  screenshare.analyzer = {"screenshare_slides_simulcast", 0.0, 0.0,
                          kFullStackTestDurationSecs};
  ParamsWithLogging screenshare_params_high;
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
      streams, 1, 1, 0, InterLayerPredMode::kOn, std::vector<SpatialLayer>(),
      false};
  fixture->RunWithAnalyzer(screenshare);
}

#endif  // !defined(WEBRTC_MAC) && !defined(WEBRTC_WIN)

TEST(FullStackTest, Screenshare_Slides_Scrolling) {
  auto fixture = CreateVideoQualityTestFixture();
  ParamsWithLogging config;
  config.call.send_side_bwe = true;
  config.video[0] = {true,    1850,  1110 / 2, 5, 50000, 200000,
                     1000000, false, "VP8",    2, 1,     400000,
                     false,   false, false,    ""};
  config.screenshare[0] = {true, false, 10, 2};
  config.analyzer = {"screenshare_slides_scrolling", 0.0, 0.0,
                     kFullStackTestDurationSecs};
  fixture->RunWithAnalyzer(config);
}

TEST(GenericDescriptorTest, Screenshare_Slides_Lossy_Net_Generic_Descriptor) {
  auto fixture = CreateVideoQualityTestFixture();
  ParamsWithLogging screenshare;
  screenshare.call.send_side_bwe = true;
  screenshare.video[0] = {true,    1850,  1110,  5, 50000, 200000,
                          1000000, false, "VP8", 2, 1,     400000,
                          false,   false, false, ""};
  screenshare.screenshare[0] = {true, false, 10};
  screenshare.analyzer = {"screenshare_slides_lossy_net_generic_descriptor",
                          0.0, 0.0, kFullStackTestDurationSecs};
  screenshare.config->loss_percent = 5;
  screenshare.config->queue_delay_ms = 200;
  screenshare.config->link_capacity_kbps = 500;
  screenshare.call.generic_descriptor = true;
  fixture->RunWithAnalyzer(screenshare);
}

TEST(FullStackTest, Screenshare_Slides_Very_Lossy) {
  auto fixture = CreateVideoQualityTestFixture();
  ParamsWithLogging screenshare;
  screenshare.call.send_side_bwe = true;
  screenshare.video[0] = {true,    1850,  1110,  5, 50000, 200000,
                          1000000, false, "VP8", 2, 1,     400000,
                          false,   false, false, ""};
  screenshare.screenshare[0] = {true, false, 10};
  screenshare.analyzer = {"screenshare_slides_very_lossy", 0.0, 0.0,
                          kFullStackTestDurationSecs};
  screenshare.config->loss_percent = 10;
  screenshare.config->queue_delay_ms = 200;
  screenshare.config->link_capacity_kbps = 500;
  fixture->RunWithAnalyzer(screenshare);
}

TEST(FullStackTest, Screenshare_Slides_Lossy_Limited) {
  auto fixture = CreateVideoQualityTestFixture();
  ParamsWithLogging screenshare;
  screenshare.call.send_side_bwe = true;
  screenshare.video[0] = {true,    1850,  1110,  5, 50000, 200000,
                          1000000, false, "VP8", 2, 1,     400000,
                          false,   false, false, ""};
  screenshare.screenshare[0] = {true, false, 10};
  screenshare.analyzer = {"screenshare_slides_lossy_limited", 0.0, 0.0,
                          kFullStackTestDurationSecs};
  screenshare.config->loss_percent = 5;
  screenshare.config->link_capacity_kbps = 200;
  screenshare.config->queue_length_packets = 30;

  fixture->RunWithAnalyzer(screenshare);
}

TEST(FullStackTest, Screenshare_Slides_Moderately_Restricted) {
  auto fixture = CreateVideoQualityTestFixture();
  ParamsWithLogging screenshare;
  screenshare.call.send_side_bwe = true;
  screenshare.video[0] = {true,    1850,  1110,  5, 50000, 200000,
                          1000000, false, "VP8", 2, 1,     400000,
                          false,   false, false, ""};
  screenshare.screenshare[0] = {true, false, 10};
  screenshare.analyzer = {"screenshare_slides_moderately_restricted", 0.0, 0.0,
                          kFullStackTestDurationSecs};
  screenshare.config->loss_percent = 1;
  screenshare.config->link_capacity_kbps = 1200;
  screenshare.config->queue_length_packets = 30;

  fixture->RunWithAnalyzer(screenshare);
}

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

#if defined(RTC_ENABLE_VP9)

TEST(FullStackTest, Screenshare_Slides_Vp9_3sl_High_Fps) {
  auto fixture = CreateVideoQualityTestFixture();
  ParamsWithLogging screenshare;
  screenshare.call.send_side_bwe = true;
  screenshare.video[0] = {true,    1850,  1110,  30, 50000, 200000,
                          2000000, false, "VP9", 1,  0,     400000,
                          false,   false, false, ""};
  screenshare.screenshare[0] = {true, false, 10};
  screenshare.analyzer = {"screenshare_slides_vp9_3sl_high_fps", 0.0, 0.0,
                          kFullStackTestDurationSecs};
  screenshare.ss[0] = {
      std::vector<VideoStream>(),  0,   3, 2, InterLayerPredMode::kOn,
      std::vector<SpatialLayer>(), true};
  fixture->RunWithAnalyzer(screenshare);
}

// TODO(http://bugs.webrtc.org/9506): investigate.
#if !defined(WEBRTC_MAC)

TEST(FullStackTest, Vp9ksvc_3sl_High) {
  webrtc::test::ScopedFieldTrials override_trials(
      AppendFieldTrials("WebRTC-Vp9IssueKeyFrameOnLayerDeactivation/Enabled/"));
  auto fixture = CreateVideoQualityTestFixture();
  ParamsWithLogging simulcast;
  simulcast.call.send_side_bwe = true;
  simulcast.video[0] = SvcVp9Video();
  simulcast.analyzer = {"vp9ksvc_3sl_high", 0.0, 0.0,
                        kFullStackTestDurationSecs};
  simulcast.ss[0] = {
      std::vector<VideoStream>(),  0,    3, 2, InterLayerPredMode::kOnKeyPic,
      std::vector<SpatialLayer>(), false};
  fixture->RunWithAnalyzer(simulcast);
}

TEST(FullStackTest, Vp9ksvc_3sl_Low) {
  webrtc::test::ScopedFieldTrials override_trials(
      AppendFieldTrials("WebRTC-Vp9IssueKeyFrameOnLayerDeactivation/Enabled/"));
  auto fixture = CreateVideoQualityTestFixture();
  ParamsWithLogging simulcast;
  simulcast.call.send_side_bwe = true;
  simulcast.video[0] = SvcVp9Video();
  simulcast.analyzer = {"vp9ksvc_3sl_low", 0.0, 0.0,
                        kFullStackTestDurationSecs};
  simulcast.ss[0] = {
      std::vector<VideoStream>(),  0,    3, 0, InterLayerPredMode::kOnKeyPic,
      std::vector<SpatialLayer>(), false};
  fixture->RunWithAnalyzer(simulcast);
}

TEST(FullStackTest, Vp9ksvc_3sl_Low_Bw_Limited) {
  webrtc::test::ScopedFieldTrials override_trials(
      AppendFieldTrials("WebRTC-Vp9IssueKeyFrameOnLayerDeactivation/Enabled/"
                        "WebRTC-Vp9ExternalRefCtrl/Enabled/"));
  auto fixture = CreateVideoQualityTestFixture();
  ParamsWithLogging simulcast;
  simulcast.config->link_capacity_kbps = 500;
  simulcast.call.send_side_bwe = true;
  simulcast.video[0] = SvcVp9Video();
  simulcast.analyzer = {"vp9ksvc_3sl_low_bw_limited", 0.0, 0.0,
                        kFullStackTestDurationSecs};
  simulcast.ss[0] = {
      std::vector<VideoStream>(),  0,    3, 0, InterLayerPredMode::kOnKeyPic,
      std::vector<SpatialLayer>(), false};
  fixture->RunWithAnalyzer(simulcast);
}

TEST(FullStackTest, Vp9ksvc_3sl_Medium_Network_Restricted) {
  webrtc::test::ScopedFieldTrials override_trials(
      AppendFieldTrials("WebRTC-Vp9IssueKeyFrameOnLayerDeactivation/Enabled/"));
  auto fixture = CreateVideoQualityTestFixture();
  ParamsWithLogging simulcast;
  simulcast.call.send_side_bwe = true;
  simulcast.video[0] = SvcVp9Video();
  simulcast.analyzer = {"vp9ksvc_3sl_medium_network_restricted", 0.0, 0.0,
                        kFullStackTestDurationSecs};
  simulcast.ss[0] = {
      std::vector<VideoStream>(),  0,    3, -1, InterLayerPredMode::kOnKeyPic,
      std::vector<SpatialLayer>(), false};
  simulcast.config->link_capacity_kbps = 1000;
  simulcast.config->queue_delay_ms = 100;
  fixture->RunWithAnalyzer(simulcast);
}

// TODO(webrtc:9722): Remove when experiment is cleaned up.
TEST(FullStackTest, Vp9ksvc_3sl_Medium_Network_Restricted_Trusted_Rate) {
  webrtc::test::ScopedFieldTrials override_trials(
      AppendFieldTrials("WebRTC-Vp9IssueKeyFrameOnLayerDeactivation/Enabled/"));
  auto fixture = CreateVideoQualityTestFixture();
  ParamsWithLogging simulcast;
  simulcast.call.send_side_bwe = true;
  simulcast.video[0] = SvcVp9Video();
  simulcast.analyzer = {"vp9ksvc_3sl_medium_network_restricted_trusted_rate",
                        0.0, 0.0, kFullStackTestDurationSecs};
  simulcast.ss[0] = {
      std::vector<VideoStream>(),  0,    3, -1, InterLayerPredMode::kOnKeyPic,
      std::vector<SpatialLayer>(), false};
  simulcast.config->link_capacity_kbps = 1000;
  simulcast.config->queue_delay_ms = 100;
  fixture->RunWithAnalyzer(simulcast);
}
#endif  // !defined(WEBRTC_MAC)

#endif  // defined(RTC_ENABLE_VP9)

// Android bots can't handle FullHD, so disable the test.
// TODO(bugs.webrtc.org/9220): Investigate source of flakiness on Mac.
#if defined(WEBRTC_ANDROID) || defined(WEBRTC_MAC)
#define MAYBE_Simulcast_HD_High DISABLED_Simulcast_HD_High
#else
#define MAYBE_Simulcast_HD_High Simulcast_HD_High
#endif

TEST(FullStackTest, MAYBE_Simulcast_HD_High) {
  auto fixture = CreateVideoQualityTestFixture();
  ParamsWithLogging simulcast;
  simulcast.call.send_side_bwe = true;
  simulcast.video[0] = {true,    1920,  1080,  30,         800000, 2500000,
                        2500000, false, "VP8", 3,          2,      400000,
                        false,   false, false, "Generator"};
  simulcast.analyzer = {"simulcast_HD_high", 0.0, 0.0,
                        kFullStackTestDurationSecs};
  simulcast.config->loss_percent = 0;
  simulcast.config->queue_delay_ms = 100;
  std::vector<VideoStream> streams = {
      VideoQualityTest::DefaultVideoStream(simulcast, 0),
      VideoQualityTest::DefaultVideoStream(simulcast, 0),
      VideoQualityTest::DefaultVideoStream(simulcast, 0)};
  simulcast.ss[0] = {
      streams, 2, 1, 0, InterLayerPredMode::kOn, std::vector<SpatialLayer>(),
      true};
  webrtc::test::ScopedFieldTrials override_trials(AppendFieldTrials(
      "WebRTC-ForceSimulatedOveruseIntervalMs/1000-50000-300/"));
  fixture->RunWithAnalyzer(simulcast);
}

TEST(FullStackTest, Simulcast_Vp8_3sl_High) {
  auto fixture = CreateVideoQualityTestFixture();
  ParamsWithLogging simulcast;
  simulcast.call.send_side_bwe = true;
  simulcast.video[0] = SimulcastVp8VideoHigh();
  simulcast.analyzer = {"simulcast_vp8_3sl_high", 0.0, 0.0,
                        kFullStackTestDurationSecs};
  simulcast.config->loss_percent = 0;
  simulcast.config->queue_delay_ms = 100;
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
  simulcast.ss[0] = {
      streams, 2, 1, 0, InterLayerPredMode::kOn, std::vector<SpatialLayer>(),
      false};
  fixture->RunWithAnalyzer(simulcast);
}

TEST(FullStackTest, Simulcast_Vp8_3sl_Low) {
  auto fixture = CreateVideoQualityTestFixture();
  ParamsWithLogging simulcast;
  simulcast.call.send_side_bwe = true;
  simulcast.video[0] = SimulcastVp8VideoHigh();
  simulcast.analyzer = {"simulcast_vp8_3sl_low", 0.0, 0.0,
                        kFullStackTestDurationSecs};
  simulcast.config->loss_percent = 0;
  simulcast.config->queue_delay_ms = 100;
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
  simulcast.ss[0] = {
      streams, 0, 1, 0, InterLayerPredMode::kOn, std::vector<SpatialLayer>(),
      false};
  fixture->RunWithAnalyzer(simulcast);
}

// This test assumes ideal network conditions with target bandwidth being
// available and exercises WebRTC calls with a high target bitrate(100 Mbps).
// Android32 bots can't handle this high bitrate, so disable test for those.
#if defined(WEBRTC_ANDROID)
#define MAYBE_High_Bitrate_With_Fake_Codec DISABLED_High_Bitrate_With_Fake_Codec
#else
#define MAYBE_High_Bitrate_With_Fake_Codec High_Bitrate_With_Fake_Codec
#endif  // defined(WEBRTC_ANDROID)
TEST(FullStackTest, MAYBE_High_Bitrate_With_Fake_Codec) {
  auto fixture = CreateVideoQualityTestFixture();
  const int target_bitrate = 100000000;
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
                        kFullStackTestDurationSecs};
  fixture->RunWithAnalyzer(generator);
}

#if defined(WEBRTC_ANDROID) || defined(WEBRTC_IOS)
// Fails on mobile devices:
// https://bugs.chromium.org/p/webrtc/issues/detail?id=7301
#define MAYBE_Largeroom_50thumb DISABLED_Largeroom_50thumb
#else
#define MAYBE_Largeroom_50thumb Largeroom_50thumb
#endif

TEST(FullStackTest, MAYBE_Largeroom_50thumb) {
  auto fixture = CreateVideoQualityTestFixture();
  ParamsWithLogging large_room;
  large_room.call.send_side_bwe = true;
  large_room.video[0] = SimulcastVp8VideoHigh();
  large_room.analyzer = {"largeroom_50thumb", 0.0, 0.0,
                         kFullStackTestDurationSecs};
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

}  // namespace webrtc
