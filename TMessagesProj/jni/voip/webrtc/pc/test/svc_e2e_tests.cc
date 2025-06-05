/*
 *  Copyright (c) 2022 The WebRTC project authors. All Rights Reserved.
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
#include "api/stats/rtcstats_objects.h"
#include "api/test/create_network_emulation_manager.h"
#include "api/test/create_peer_connection_quality_test_frame_generator.h"
#include "api/test/create_peerconnection_quality_test_fixture.h"
#include "api/test/frame_generator_interface.h"
#include "api/test/metrics/global_metrics_logger_and_exporter.h"
#include "api/test/network_emulation_manager.h"
#include "api/test/pclf/media_configuration.h"
#include "api/test/pclf/media_quality_test_params.h"
#include "api/test/pclf/peer_configurer.h"
#include "api/test/peerconnection_quality_test_fixture.h"
#include "api/test/simulated_network.h"
#include "api/test/time_controller.h"
#include "api/video_codecs/vp9_profile.h"
#include "call/simulated_network.h"
#include "modules/video_coding/codecs/vp9/include/vp9.h"
#include "modules/video_coding/svc/scalability_mode_util.h"
#include "rtc_base/containers/flat_map.h"
#include "system_wrappers/include/field_trial.h"
#include "test/field_trial.h"
#include "test/gmock.h"
#include "test/gtest.h"
#include "test/pc/e2e/analyzer/video/default_video_quality_analyzer.h"
#include "test/pc/e2e/network_quality_metrics_reporter.h"
#include "test/testsupport/file_utils.h"

namespace webrtc {
namespace {

using ::cricket::kAv1CodecName;
using ::cricket::kH264CodecName;
using ::cricket::kVp8CodecName;
using ::cricket::kVp9CodecName;
using ::testing::Combine;
using ::testing::Optional;
using ::testing::UnitTest;
using ::testing::Values;
using ::testing::ValuesIn;
using ::webrtc::webrtc_pc_e2e::EmulatedSFUConfig;
using ::webrtc::webrtc_pc_e2e::PeerConfigurer;
using ::webrtc::webrtc_pc_e2e::RunParams;
using ::webrtc::webrtc_pc_e2e::ScreenShareConfig;
using ::webrtc::webrtc_pc_e2e::VideoCodecConfig;
using ::webrtc::webrtc_pc_e2e::VideoConfig;

std::unique_ptr<webrtc_pc_e2e::PeerConnectionE2EQualityTestFixture>
CreateTestFixture(absl::string_view test_case_name,
                  TimeController& time_controller,
                  std::pair<EmulatedNetworkManagerInterface*,
                            EmulatedNetworkManagerInterface*> network_links,
                  rtc::FunctionView<void(PeerConfigurer*)> alice_configurer,
                  rtc::FunctionView<void(PeerConfigurer*)> bob_configurer,
                  std::unique_ptr<VideoQualityAnalyzerInterface>
                      video_quality_analyzer = nullptr) {
  auto fixture = webrtc_pc_e2e::CreatePeerConnectionE2EQualityTestFixture(
      std::string(test_case_name), time_controller, nullptr,
      std::move(video_quality_analyzer));
  auto alice = std::make_unique<PeerConfigurer>(
      network_links.first->network_dependencies());
  auto bob = std::make_unique<PeerConfigurer>(
      network_links.second->network_dependencies());
  alice_configurer(alice.get());
  bob_configurer(bob.get());
  fixture->AddPeer(std::move(alice));
  fixture->AddPeer(std::move(bob));
  return fixture;
}

// Takes the current active field trials set, and appends some new trials.
std::string AppendFieldTrials(std::string new_trial_string) {
  return std::string(field_trial::GetFieldTrialString()) + new_trial_string;
}

enum class UseDependencyDescriptor {
  Enabled,
  Disabled,
};

struct SvcTestParameters {
  static SvcTestParameters Create(const std::string& codec_name,
                                  const std::string& scalability_mode_str) {
    absl::optional<ScalabilityMode> scalability_mode =
        ScalabilityModeFromString(scalability_mode_str);
    RTC_CHECK(scalability_mode.has_value())
        << "Unsupported scalability mode: " << scalability_mode_str;

    int num_spatial_layers =
        ScalabilityModeToNumSpatialLayers(*scalability_mode);
    int num_temporal_layers =
        ScalabilityModeToNumTemporalLayers(*scalability_mode);

    return SvcTestParameters{codec_name, scalability_mode_str,
                             num_spatial_layers, num_temporal_layers};
  }

  std::string codec_name;
  std::string scalability_mode;
  int expected_spatial_layers;
  int expected_temporal_layers;
};

class SvcTest : public testing::TestWithParam<
                    std::tuple<SvcTestParameters, UseDependencyDescriptor>> {
 public:
  SvcTest()
      : video_codec_config(ToVideoCodecConfig(SvcTestParameters().codec_name)) {
  }

  static VideoCodecConfig ToVideoCodecConfig(absl::string_view codec) {
    if (codec == cricket::kVp9CodecName) {
      return VideoCodecConfig(
          cricket::kVp9CodecName,
          {{kVP9FmtpProfileId, VP9ProfileToString(VP9Profile::kProfile0)}});
    }

    return VideoCodecConfig(codec);
  }

  const SvcTestParameters& SvcTestParameters() const {
    return std::get<0>(GetParam());
  }

  bool UseDependencyDescriptor() const {
    return std::get<1>(GetParam()) == UseDependencyDescriptor::Enabled;
  }

  bool IsSMode() const {
    return SvcTestParameters().scalability_mode[0] == 'S';
  }

 protected:
  VideoCodecConfig video_codec_config;
};

std::string SvcTestNameGenerator(
    const testing::TestParamInfo<SvcTest::ParamType>& info) {
  return std::get<0>(info.param).scalability_mode +
         (std::get<1>(info.param) == UseDependencyDescriptor::Enabled ? "_DD"
                                                                      : "");
}

}  // namespace

// Records how many frames are seen for each spatial and temporal index at the
// encoder and decoder level.
class SvcVideoQualityAnalyzer : public DefaultVideoQualityAnalyzer {
 public:
  using SpatialTemporalLayerCounts = flat_map<int, flat_map<int, int>>;

  explicit SvcVideoQualityAnalyzer(Clock* clock)
      : DefaultVideoQualityAnalyzer(clock,
                                    test::GetGlobalMetricsLogger(),
                                    DefaultVideoQualityAnalyzerOptions{
                                        .compute_psnr = false,
                                        .compute_ssim = false,
                                    }) {}
  ~SvcVideoQualityAnalyzer() override = default;

  void OnFrameEncoded(absl::string_view peer_name,
                      uint16_t frame_id,
                      const EncodedImage& encoded_image,
                      const EncoderStats& stats,
                      bool discarded) override {
    absl::optional<int> spatial_id = encoded_image.SpatialIndex();
    absl::optional<int> temporal_id = encoded_image.TemporalIndex();
    encoder_layers_seen_[spatial_id.value_or(0)][temporal_id.value_or(0)]++;
    DefaultVideoQualityAnalyzer::OnFrameEncoded(
        peer_name, frame_id, encoded_image, stats, discarded);
  }

  void OnFramePreDecode(absl::string_view peer_name,
                        uint16_t frame_id,
                        const EncodedImage& input_image) override {
    absl::optional<int> spatial_id = input_image.SpatialIndex();
    absl::optional<int> temporal_id = input_image.TemporalIndex();
    if (!spatial_id) {
      decoder_layers_seen_[0][temporal_id.value_or(0)]++;
    } else {
      for (int i = 0; i <= *spatial_id; ++i) {
        // If there are no spatial layers (for example VP8), we still want to
        // record the temporal index for pseudo-layer "0" frames.
        if (*spatial_id == 0 ||
            input_image.SpatialLayerFrameSize(i).value_or(0) > 0) {
          decoder_layers_seen_[i][temporal_id.value_or(0)]++;
        }
      }
    }
    DefaultVideoQualityAnalyzer::OnFramePreDecode(peer_name, frame_id,
                                                  input_image);
  }

  void OnStatsReports(
      absl::string_view pc_label,
      const rtc::scoped_refptr<const RTCStatsReport>& report) override {
    // Extract the scalability mode reported in the stats.
    auto outbound_stats = report->GetStatsOfType<RTCOutboundRtpStreamStats>();
    for (const auto& stat : outbound_stats) {
      if (stat->scalability_mode.has_value()) {
        reported_scalability_mode_ = *stat->scalability_mode;
      }
    }
  }

  const SpatialTemporalLayerCounts& encoder_layers_seen() const {
    return encoder_layers_seen_;
  }
  const SpatialTemporalLayerCounts& decoder_layers_seen() const {
    return decoder_layers_seen_;
  }
  const absl::optional<std::string> reported_scalability_mode() const {
    return reported_scalability_mode_;
  }

 private:
  SpatialTemporalLayerCounts encoder_layers_seen_;
  SpatialTemporalLayerCounts decoder_layers_seen_;
  absl::optional<std::string> reported_scalability_mode_;
};

MATCHER_P2(HasSpatialAndTemporalLayers,
           expected_spatial_layers,
           expected_temporal_layers,
           "") {
  if (arg.size() != static_cast<size_t>(expected_spatial_layers)) {
    *result_listener << "spatial layer count mismatch expected "
                     << expected_spatial_layers << " but got " << arg.size();
    return false;
  }
  for (const auto& [spatial_layer_index, temporal_layers] : arg) {
    if (spatial_layer_index < 0 ||
        spatial_layer_index >= expected_spatial_layers) {
      *result_listener << "spatial layer index is not in range [0,"
                       << expected_spatial_layers << "[.";
      return false;
    }

    if (temporal_layers.size() !=
        static_cast<size_t>(expected_temporal_layers)) {
      *result_listener << "temporal layer count mismatch on spatial layer "
                       << spatial_layer_index << ", expected "
                       << expected_temporal_layers << " but got "
                       << temporal_layers.size();
      return false;
    }
    for (const auto& [temporal_layer_index, temporal_layer_frame_count] :
         temporal_layers) {
      if (temporal_layer_index < 0 ||
          temporal_layer_index >= expected_temporal_layers) {
        *result_listener << "temporal layer index on spatial layer "
                         << spatial_layer_index << " is not in range [0,"
                         << expected_temporal_layers << "[.";
        return false;
      }
    }
  }
  return true;
}

MATCHER_P2(HasSpatialAndTemporalLayersSMode,
           expected_spatial_layers,
           expected_temporal_layers,
           "") {
  if (arg.size() != 1) {
    *result_listener << "spatial layer count mismatch expected 1 but got "
                     << arg.size();
    return false;
  }
  for (const auto& [spatial_layer_index, temporal_layers] : arg) {
    if (spatial_layer_index != expected_spatial_layers - 1) {
      *result_listener << "spatial layer index is not equal to "
                       << expected_spatial_layers - 1 << ".";
      return false;
    }

    if (temporal_layers.size() !=
        static_cast<size_t>(expected_temporal_layers)) {
      *result_listener << "temporal layer count mismatch on spatial layer "
                       << spatial_layer_index << ", expected "
                       << expected_temporal_layers << " but got "
                       << temporal_layers.size();
      return false;
    }
    for (const auto& [temporal_layer_index, temporal_layer_frame_count] :
         temporal_layers) {
      if (temporal_layer_index < 0 ||
          temporal_layer_index >= expected_temporal_layers) {
        *result_listener << "temporal layer index on spatial layer "
                         << spatial_layer_index << " is not in range [0,"
                         << expected_temporal_layers << "[.";
        return false;
      }
    }
  }
  return true;
}

TEST_P(SvcTest, ScalabilityModeSupported) {
  std::string trials;
  if (UseDependencyDescriptor()) {
    trials += "WebRTC-DependencyDescriptorAdvertised/Enabled/";
  }
  test::ScopedFieldTrials override_trials(AppendFieldTrials(trials));
  std::unique_ptr<NetworkEmulationManager> network_emulation_manager =
      CreateNetworkEmulationManager(TimeMode::kSimulated);
  auto analyzer = std::make_unique<SvcVideoQualityAnalyzer>(
      network_emulation_manager->time_controller()->GetClock());
  SvcVideoQualityAnalyzer* analyzer_ptr = analyzer.get();
  auto fixture = CreateTestFixture(
      UnitTest::GetInstance()->current_test_info()->name(),
      *network_emulation_manager->time_controller(),
      network_emulation_manager->CreateEndpointPairWithTwoWayRoutes(
          BuiltInNetworkBehaviorConfig()),
      [this](PeerConfigurer* alice) {
        VideoConfig video(/*stream_label=*/"alice-video", /*width=*/1850,
                          /*height=*/1110, /*fps=*/30);
        if (IsSMode()) {
          video.emulated_sfu_config = EmulatedSFUConfig(
              SvcTestParameters().expected_spatial_layers - 1,
              SvcTestParameters().expected_temporal_layers - 1);
        }
        RtpEncodingParameters parameters;
        parameters.scalability_mode = SvcTestParameters().scalability_mode;
        video.encoding_params.push_back(parameters);
        auto generator = CreateScreenShareFrameGenerator(
            video, ScreenShareConfig(TimeDelta::Seconds(5)));
        alice->AddVideoConfig(std::move(video), std::move(generator));
        alice->SetVideoCodecs({video_codec_config});
      },
      [](PeerConfigurer* bob) {}, std::move(analyzer));
  fixture->Run(RunParams(TimeDelta::Seconds(10)));
  EXPECT_THAT(analyzer_ptr->encoder_layers_seen(),
              HasSpatialAndTemporalLayers(
                  SvcTestParameters().expected_spatial_layers,
                  SvcTestParameters().expected_temporal_layers));
  if (IsSMode()) {
    EXPECT_THAT(analyzer_ptr->decoder_layers_seen(),
                HasSpatialAndTemporalLayersSMode(
                    SvcTestParameters().expected_spatial_layers,
                    SvcTestParameters().expected_temporal_layers));
  } else {
    EXPECT_THAT(analyzer_ptr->decoder_layers_seen(),
                HasSpatialAndTemporalLayers(
                    SvcTestParameters().expected_spatial_layers,
                    SvcTestParameters().expected_temporal_layers));
  }
  EXPECT_THAT(analyzer_ptr->reported_scalability_mode(),
              Optional(SvcTestParameters().scalability_mode));

  RTC_LOG(LS_INFO) << "Encoder layers seen: "
                   << analyzer_ptr->encoder_layers_seen().size();
  for (auto& [spatial_index, temporal_layers] :
       analyzer_ptr->encoder_layers_seen()) {
    for (auto& [temporal_index, frame_count] : temporal_layers) {
      RTC_LOG(LS_INFO) << "  Layer: " << spatial_index << "," << temporal_index
                       << " frames: " << frame_count;
    }
  }
  RTC_LOG(LS_INFO) << "Decoder layers seen: "
                   << analyzer_ptr->decoder_layers_seen().size();
  for (auto& [spatial_index, temporal_layers] :
       analyzer_ptr->decoder_layers_seen()) {
    for (auto& [temporal_index, frame_count] : temporal_layers) {
      RTC_LOG(LS_INFO) << "  Layer: " << spatial_index << "," << temporal_index
                       << " frames: " << frame_count;
    }
  }
}

INSTANTIATE_TEST_SUITE_P(
    SvcTestVP8,
    SvcTest,
    Combine(Values(SvcTestParameters::Create(kVp8CodecName, "L1T1"),
                   SvcTestParameters::Create(kVp8CodecName, "L1T2"),
                   SvcTestParameters::Create(kVp8CodecName, "L1T3")),
            Values(UseDependencyDescriptor::Disabled,
                   UseDependencyDescriptor::Enabled)),
    SvcTestNameGenerator);

#if defined(WEBRTC_USE_H264)
INSTANTIATE_TEST_SUITE_P(
    SvcTestH264,
    SvcTest,
    Combine(ValuesIn({
                SvcTestParameters::Create(kH264CodecName, "L1T1"),
                SvcTestParameters::Create(kH264CodecName, "L1T2"),
                SvcTestParameters::Create(kH264CodecName, "L1T3"),
            }),
            // Like AV1, H.264 RTP format does not include SVC related
            // information, so always use Dependency Descriptor.
            Values(UseDependencyDescriptor::Enabled)),
    SvcTestNameGenerator);
#endif

#if defined(RTC_ENABLE_VP9)
INSTANTIATE_TEST_SUITE_P(
    SvcTestVP9,
    SvcTest,
    Combine(
        // TODO(bugs.webrtc.org/13960): Fix and enable remaining VP9 modes
        ValuesIn({
            SvcTestParameters::Create(kVp9CodecName, "L1T1"),
            SvcTestParameters::Create(kVp9CodecName, "L1T2"),
            SvcTestParameters::Create(kVp9CodecName, "L1T3"),
            SvcTestParameters::Create(kVp9CodecName, "L2T1"),
            SvcTestParameters::Create(kVp9CodecName, "L2T1h"),
            SvcTestParameters::Create(kVp9CodecName, "L2T1_KEY"),
            SvcTestParameters::Create(kVp9CodecName, "L2T2"),
            SvcTestParameters::Create(kVp9CodecName, "L2T2h"),
            SvcTestParameters::Create(kVp9CodecName, "L2T2_KEY"),
            SvcTestParameters::Create(kVp9CodecName, "L2T2_KEY_SHIFT"),
            SvcTestParameters::Create(kVp9CodecName, "L2T3"),
            SvcTestParameters::Create(kVp9CodecName, "L2T3h"),
            SvcTestParameters::Create(kVp9CodecName, "L2T3_KEY"),
            // SvcTestParameters::Create(kVp9CodecName, "L2T3_KEY_SHIFT"),
            SvcTestParameters::Create(kVp9CodecName, "L3T1"),
            SvcTestParameters::Create(kVp9CodecName, "L3T1h"),
            SvcTestParameters::Create(kVp9CodecName, "L3T1_KEY"),
            SvcTestParameters::Create(kVp9CodecName, "L3T2"),
            SvcTestParameters::Create(kVp9CodecName, "L3T2h"),
            SvcTestParameters::Create(kVp9CodecName, "L3T2_KEY"),
            // SvcTestParameters::Create(kVp9CodecName, "L3T2_KEY_SHIFT"),
            SvcTestParameters::Create(kVp9CodecName, "L3T3"),
            SvcTestParameters::Create(kVp9CodecName, "L3T3h"),
            SvcTestParameters::Create(kVp9CodecName, "L3T3_KEY"),
            // SvcTestParameters::Create(kVp9CodecName, "L3T3_KEY_SHIFT"),
            SvcTestParameters::Create(kVp9CodecName, "S2T1"),
            SvcTestParameters::Create(kVp9CodecName, "S2T1h"),
            SvcTestParameters::Create(kVp9CodecName, "S2T2"),
            SvcTestParameters::Create(kVp9CodecName, "S2T2h"),
            SvcTestParameters::Create(kVp9CodecName, "S2T3"),
            SvcTestParameters::Create(kVp9CodecName, "S2T3h"),
            SvcTestParameters::Create(kVp9CodecName, "S3T1"),
            SvcTestParameters::Create(kVp9CodecName, "S3T1h"),
            SvcTestParameters::Create(kVp9CodecName, "S3T2"),
            SvcTestParameters::Create(kVp9CodecName, "S3T2h"),
            SvcTestParameters::Create(kVp9CodecName, "S3T3"),
            SvcTestParameters::Create(kVp9CodecName, "S3T3h"),
        }),
        Values(UseDependencyDescriptor::Disabled,
               UseDependencyDescriptor::Enabled)),
    SvcTestNameGenerator);
#endif

INSTANTIATE_TEST_SUITE_P(
    SvcTestAV1,
    SvcTest,
    Combine(ValuesIn({
                SvcTestParameters::Create(kAv1CodecName, "L1T1"),
                SvcTestParameters::Create(kAv1CodecName, "L1T2"),
                SvcTestParameters::Create(kAv1CodecName, "L1T3"),
                SvcTestParameters::Create(kAv1CodecName, "L2T1"),
                SvcTestParameters::Create(kAv1CodecName, "L2T1h"),
                SvcTestParameters::Create(kAv1CodecName, "L2T1_KEY"),
                SvcTestParameters::Create(kAv1CodecName, "L2T2"),
                SvcTestParameters::Create(kAv1CodecName, "L2T2h"),
                SvcTestParameters::Create(kAv1CodecName, "L2T2_KEY"),
                SvcTestParameters::Create(kAv1CodecName, "L2T2_KEY_SHIFT"),
                SvcTestParameters::Create(kAv1CodecName, "L2T3"),
                SvcTestParameters::Create(kAv1CodecName, "L2T3h"),
                SvcTestParameters::Create(kAv1CodecName, "L2T3_KEY"),
                // SvcTestParameters::Create(kAv1CodecName, "L2T3_KEY_SHIFT"),
                // TODO(bugs.webrtc.org/15666): Investigate and reenable AV1
                // L3 tests. SvcTestParameters::Create(kAv1CodecName, "L3T1"),
                // SvcTestParameters::Create(kAv1CodecName, "L3T1h"),
                // SvcTestParameters::Create(kAv1CodecName, "L3T1_KEY"),
                // SvcTestParameters::Create(kAv1CodecName, "L3T2"),
                // SvcTestParameters::Create(kAv1CodecName, "L3T2h"),
                // SvcTestParameters::Create(kAv1CodecName, "L3T2_KEY"),
                // SvcTestParameters::Create(kAv1CodecName, "L3T2_KEY_SHIFT"),
                // SvcTestParameters::Create(kAv1CodecName, "L3T3"),
                // SvcTestParameters::Create(kAv1CodecName, "L3T3h"),
                // SvcTestParameters::Create(kAv1CodecName, "L3T3_KEY"),
                // SvcTestParameters::Create(kAv1CodecName, "L3T3_KEY_SHIFT"),
                SvcTestParameters::Create(kAv1CodecName, "S2T1"),
                SvcTestParameters::Create(kAv1CodecName, "S2T1h"),
                SvcTestParameters::Create(kAv1CodecName, "S2T2"),
                SvcTestParameters::Create(kAv1CodecName, "S2T2h"),
                SvcTestParameters::Create(kAv1CodecName, "S2T3"),
                SvcTestParameters::Create(kAv1CodecName, "S2T3h"),
                // TODO(bugs.webrtc.org/15666): Investigate and reenable AV1
                // S3 tests.
                // SvcTestParameters::Create(kAv1CodecName, "S3T1"),
                // SvcTestParameters::Create(kAv1CodecName, "S3T1h"),
                // SvcTestParameters::Create(kAv1CodecName, "S3T2"),
                // SvcTestParameters::Create(kAv1CodecName, "S3T2h"),
                // SvcTestParameters::Create(kAv1CodecName, "S3T3"),
                // SvcTestParameters::Create(kAv1CodecName, "S3T3h"),
            }),
            Values(UseDependencyDescriptor::Enabled)),
    SvcTestNameGenerator);

}  // namespace webrtc
