/*
 *  Copyright (c) 2012 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include <memory>
#include <vector>

#include "api/test/create_videocodec_test_fixture.h"
#include "api/test/video/function_video_encoder_factory.h"
#include "api/video_codecs/sdp_video_format.h"
#include "media/base/media_constants.h"
#include "media/engine/internal_decoder_factory.h"
#include "media/engine/internal_encoder_factory.h"
#include "media/engine/simulcast_encoder_adapter.h"
#include "modules/video_coding/utility/vp8_header_parser.h"
#include "modules/video_coding/utility/vp9_uncompressed_header_parser.h"
#include "test/gtest.h"
#include "test/testsupport/file_utils.h"

namespace webrtc {
namespace test {

using VideoStatistics = VideoCodecTestStats::VideoStatistics;

namespace {
// Codec settings.
const int kCifWidth = 352;
const int kCifHeight = 288;
const int kNumFramesShort = 100;
const int kNumFramesLong = 300;
const size_t kBitrateRdPerfKbps[] = {100,  200,  300,  400,  500,  600,
                                     700,  800,  1000, 1250, 1400, 1600,
                                     1800, 2000, 2200, 2500};
const size_t kNumFirstFramesToSkipAtRdPerfAnalysis = 60;

class QpFrameChecker : public VideoCodecTestFixture::EncodedFrameChecker {
 public:
  void CheckEncodedFrame(webrtc::VideoCodecType codec,
                         const EncodedImage& encoded_frame) const override {
    int qp;
    if (codec == kVideoCodecVP8) {
      EXPECT_TRUE(vp8::GetQp(encoded_frame.data(), encoded_frame.size(), &qp));
    } else if (codec == kVideoCodecVP9) {
      EXPECT_TRUE(vp9::GetQp(encoded_frame.data(), encoded_frame.size(), &qp));
    } else {
      RTC_DCHECK_NOTREACHED();
    }
    EXPECT_EQ(encoded_frame.qp_, qp) << "Encoder QP != parsed bitstream QP.";
  }
};

VideoCodecTestFixture::Config CreateConfig() {
  VideoCodecTestFixture::Config config;
  config.filename = "foreman_cif";
  config.filepath = ResourcePath(config.filename, "yuv");
  config.num_frames = kNumFramesLong;
  config.use_single_core = true;
  return config;
}

void PrintRdPerf(std::map<size_t, std::vector<VideoStatistics>> rd_stats) {
  printf("--> Summary\n");
  printf("%11s %5s %6s %11s %12s %11s %13s %13s %5s %7s %7s %7s %13s %13s\n",
         "uplink_kbps", "width", "height", "spatial_idx", "temporal_idx",
         "target_kbps", "downlink_kbps", "framerate_fps", "psnr", "psnr_y",
         "psnr_u", "psnr_v", "enc_speed_fps", "dec_speed_fps");
  for (const auto& rd_stat : rd_stats) {
    const size_t bitrate_kbps = rd_stat.first;
    for (const auto& layer_stat : rd_stat.second) {
      printf(
          "%11zu %5zu %6zu %11zu %12zu %11zu %13zu %13.2f %5.2f %7.2f %7.2f "
          "%7.2f"
          "%13.2f %13.2f\n",
          bitrate_kbps, layer_stat.width, layer_stat.height,
          layer_stat.spatial_idx, layer_stat.temporal_idx,
          layer_stat.target_bitrate_kbps, layer_stat.bitrate_kbps,
          layer_stat.framerate_fps, layer_stat.avg_psnr, layer_stat.avg_psnr_y,
          layer_stat.avg_psnr_u, layer_stat.avg_psnr_v,
          layer_stat.enc_speed_fps, layer_stat.dec_speed_fps);
    }
  }
}
}  // namespace

#if defined(RTC_ENABLE_VP9)
TEST(VideoCodecTestLibvpx, HighBitrateVP9) {
  auto config = CreateConfig();
  config.SetCodecSettings(cricket::kVp9CodecName, 1, 1, 1, false, true, false,
                          kCifWidth, kCifHeight);
  config.num_frames = kNumFramesShort;
  const auto frame_checker = std::make_unique<QpFrameChecker>();
  config.encoded_frame_checker = frame_checker.get();
  auto fixture = CreateVideoCodecTestFixture(config);

  std::vector<RateProfile> rate_profiles = {{500, 30, 0}};

  std::vector<RateControlThresholds> rc_thresholds = {
      {5, 1, 0, 1, 0.3, 0.1, 0, 1}};

  std::vector<QualityThresholds> quality_thresholds = {{37, 36, 0.94, 0.92}};

  fixture->RunTest(rate_profiles, &rc_thresholds, &quality_thresholds, nullptr);
}

TEST(VideoCodecTestLibvpx, ChangeBitrateVP9) {
  auto config = CreateConfig();
  config.SetCodecSettings(cricket::kVp9CodecName, 1, 1, 1, false, true, false,
                          kCifWidth, kCifHeight);
  const auto frame_checker = std::make_unique<QpFrameChecker>();
  config.encoded_frame_checker = frame_checker.get();
  auto fixture = CreateVideoCodecTestFixture(config);

  std::vector<RateProfile> rate_profiles = {
      {200, 30, 0},  // target_kbps, input_fps, frame_num
      {700, 30, 100},
      {500, 30, 200}};

  std::vector<RateControlThresholds> rc_thresholds = {
      {5, 2, 0, 1, 0.5, 0.1, 0, 1},
      {15, 3, 0, 1, 0.5, 0.1, 0, 0},
      {11, 2, 0, 1, 0.5, 0.1, 0, 0}};

  std::vector<QualityThresholds> quality_thresholds = {
      {34, 33, 0.90, 0.88}, {38, 35, 0.95, 0.91}, {35, 34, 0.93, 0.90}};

  fixture->RunTest(rate_profiles, &rc_thresholds, &quality_thresholds, nullptr);
}

TEST(VideoCodecTestLibvpx, ChangeFramerateVP9) {
  auto config = CreateConfig();
  config.SetCodecSettings(cricket::kVp9CodecName, 1, 1, 1, false, true, false,
                          kCifWidth, kCifHeight);
  const auto frame_checker = std::make_unique<QpFrameChecker>();
  config.encoded_frame_checker = frame_checker.get();
  auto fixture = CreateVideoCodecTestFixture(config);

  std::vector<RateProfile> rate_profiles = {
      {100, 24, 0},  // target_kbps, input_fps, frame_num
      {100, 15, 100},
      {100, 10, 200}};

  // Framerate mismatch should be lower for lower framerate.
  std::vector<RateControlThresholds> rc_thresholds = {
      {10, 2, 40, 1, 0.5, 0.2, 0, 1},
      {8, 2, 5, 1, 0.5, 0.2, 0, 0},
      {5, 2, 0, 1, 0.5, 0.3, 0, 0}};

  // Quality should be higher for lower framerates for the same content.
  std::vector<QualityThresholds> quality_thresholds = {
      {33, 32, 0.88, 0.86}, {33.5, 32, 0.90, 0.86}, {33.5, 31.5, 0.90, 0.85}};

  fixture->RunTest(rate_profiles, &rc_thresholds, &quality_thresholds, nullptr);
}

TEST(VideoCodecTestLibvpx, DenoiserOnVP9) {
  auto config = CreateConfig();
  config.SetCodecSettings(cricket::kVp9CodecName, 1, 1, 1, true, true, false,
                          kCifWidth, kCifHeight);
  config.num_frames = kNumFramesShort;
  const auto frame_checker = std::make_unique<QpFrameChecker>();
  config.encoded_frame_checker = frame_checker.get();
  auto fixture = CreateVideoCodecTestFixture(config);

  std::vector<RateProfile> rate_profiles = {{500, 30, 0}};

  std::vector<RateControlThresholds> rc_thresholds = {
      {5, 1, 0, 1, 0.3, 0.1, 0, 1}};

  std::vector<QualityThresholds> quality_thresholds = {{37.5, 36, 0.94, 0.93}};

  fixture->RunTest(rate_profiles, &rc_thresholds, &quality_thresholds, nullptr);
}

TEST(VideoCodecTestLibvpx, VeryLowBitrateVP9) {
  auto config = CreateConfig();
  config.SetCodecSettings(cricket::kVp9CodecName, 1, 1, 1, false, true, true,
                          kCifWidth, kCifHeight);
  const auto frame_checker = std::make_unique<QpFrameChecker>();
  config.encoded_frame_checker = frame_checker.get();
  auto fixture = CreateVideoCodecTestFixture(config);

  std::vector<RateProfile> rate_profiles = {{50, 30, 0}};

  std::vector<RateControlThresholds> rc_thresholds = {
      {15, 3, 75, 1, 0.5, 0.4, 2, 1}};

  std::vector<QualityThresholds> quality_thresholds = {{28, 25, 0.80, 0.65}};

  fixture->RunTest(rate_profiles, &rc_thresholds, &quality_thresholds, nullptr);
}

// TODO(marpan): Add temporal layer test for VP9, once changes are in
// vp9 wrapper for this.

#endif  // defined(RTC_ENABLE_VP9)

TEST(VideoCodecTestLibvpx, HighBitrateVP8) {
  auto config = CreateConfig();
  config.SetCodecSettings(cricket::kVp8CodecName, 1, 1, 1, true, true, false,
                          kCifWidth, kCifHeight);
  config.num_frames = kNumFramesShort;
  const auto frame_checker = std::make_unique<QpFrameChecker>();
  config.encoded_frame_checker = frame_checker.get();
  auto fixture = CreateVideoCodecTestFixture(config);

  std::vector<RateProfile> rate_profiles = {{500, 30, 0}};

  std::vector<RateControlThresholds> rc_thresholds = {
      {5, 1, 0, 1, 0.2, 0.1, 0, 1}};

#if defined(WEBRTC_ARCH_ARM) || defined(WEBRTC_ARCH_ARM64)
  std::vector<QualityThresholds> quality_thresholds = {{35, 33, 0.91, 0.89}};
#else
  std::vector<QualityThresholds> quality_thresholds = {{37, 35, 0.93, 0.91}};
#endif
  fixture->RunTest(rate_profiles, &rc_thresholds, &quality_thresholds, nullptr);
}

TEST(VideoCodecTestLibvpx, MAYBE_ChangeBitrateVP8) {
  auto config = CreateConfig();
  config.SetCodecSettings(cricket::kVp8CodecName, 1, 1, 1, true, true, false,
                          kCifWidth, kCifHeight);
  const auto frame_checker = std::make_unique<QpFrameChecker>();
  config.encoded_frame_checker = frame_checker.get();
  auto fixture = CreateVideoCodecTestFixture(config);

  std::vector<RateProfile> rate_profiles = {
      {200, 30, 0},  // target_kbps, input_fps, frame_num
      {800, 30, 100},
      {500, 30, 200}};

  std::vector<RateControlThresholds> rc_thresholds = {
      {5, 1, 0, 1, 0.2, 0.1, 0, 1},
      {15.5, 1, 0, 1, 0.2, 0.1, 0, 0},
      {15, 1, 0, 1, 0.2, 0.1, 0, 0}};

#if defined(WEBRTC_ARCH_ARM) || defined(WEBRTC_ARCH_ARM64)
  std::vector<QualityThresholds> quality_thresholds = {
      {31.8, 31, 0.86, 0.85}, {36, 34.8, 0.92, 0.90}, {33.5, 32, 0.90, 0.88}};
#else
  std::vector<QualityThresholds> quality_thresholds = {
      {33, 32, 0.89, 0.88}, {38, 36, 0.94, 0.93}, {35, 34, 0.92, 0.91}};
#endif
  fixture->RunTest(rate_profiles, &rc_thresholds, &quality_thresholds, nullptr);
}

TEST(VideoCodecTestLibvpx, MAYBE_ChangeFramerateVP8) {
  auto config = CreateConfig();
  config.SetCodecSettings(cricket::kVp8CodecName, 1, 1, 1, true, true, false,
                          kCifWidth, kCifHeight);
  const auto frame_checker = std::make_unique<QpFrameChecker>();
  config.encoded_frame_checker = frame_checker.get();
  auto fixture = CreateVideoCodecTestFixture(config);

  std::vector<RateProfile> rate_profiles = {
      {80, 24, 0},  // target_kbps, input_fps, frame_index_rate_update
      {80, 15, 100},
      {80, 10, 200}};

#if defined(WEBRTC_ARCH_ARM) || defined(WEBRTC_ARCH_ARM64)
  std::vector<RateControlThresholds> rc_thresholds = {
      {10, 2.42, 60, 1, 0.3, 0.3, 0, 1},
      {10, 2, 30, 1, 0.3, 0.3, 0, 0},
      {10, 2, 10, 1, 0.3, 0.2, 0, 0}};
#else
  std::vector<RateControlThresholds> rc_thresholds = {
      {10, 2, 20, 1, 0.3, 0.15, 0, 1},
      {5, 2, 5, 1, 0.3, 0.15, 0, 0},
      {4, 2, 1, 1, 0.3, 0.2, 0, 0}};
#endif

#if defined(WEBRTC_ARCH_ARM) || defined(WEBRTC_ARCH_ARM64)
  std::vector<QualityThresholds> quality_thresholds = {
      {31, 30, 0.85, 0.84}, {31.4, 30.5, 0.86, 0.84}, {30.5, 29, 0.83, 0.78}};
#else
  std::vector<QualityThresholds> quality_thresholds = {
      {31, 30, 0.87, 0.85}, {32, 31, 0.88, 0.85}, {32, 30, 0.87, 0.82}};
#endif
  fixture->RunTest(rate_profiles, &rc_thresholds, &quality_thresholds, nullptr);
}

#if defined(WEBRTC_ANDROID)
#define MAYBE_TemporalLayersVP8 DISABLED_TemporalLayersVP8
#else
#define MAYBE_TemporalLayersVP8 TemporalLayersVP8
#endif
TEST(VideoCodecTestLibvpx, MAYBE_TemporalLayersVP8) {
  auto config = CreateConfig();
  config.SetCodecSettings(cricket::kVp8CodecName, 1, 1, 3, true, true, false,
                          kCifWidth, kCifHeight);
  const auto frame_checker = std::make_unique<QpFrameChecker>();
  config.encoded_frame_checker = frame_checker.get();
  auto fixture = CreateVideoCodecTestFixture(config);

  std::vector<RateProfile> rate_profiles = {{200, 30, 0}, {400, 30, 150}};

#if defined(WEBRTC_ARCH_ARM) || defined(WEBRTC_ARCH_ARM64)
  std::vector<RateControlThresholds> rc_thresholds = {
      {10, 1, 2.1, 1, 0.2, 0.1, 0, 1}, {12, 2, 3, 1, 0.2, 0.1, 0, 1}};
#else
  std::vector<RateControlThresholds> rc_thresholds = {
      {5, 1, 0, 1, 0.2, 0.1, 0, 1}, {10, 2, 0, 1, 0.2, 0.1, 0, 1}};
#endif
// Min SSIM drops because of high motion scene with complex backgound (trees).
#if defined(WEBRTC_ARCH_ARM) || defined(WEBRTC_ARCH_ARM64)
  std::vector<QualityThresholds> quality_thresholds = {{31, 30, 0.85, 0.83},
                                                       {31, 28, 0.85, 0.75}};
#else
  std::vector<QualityThresholds> quality_thresholds = {{32, 30, 0.88, 0.85},
                                                       {33, 30, 0.89, 0.83}};
#endif
  fixture->RunTest(rate_profiles, &rc_thresholds, &quality_thresholds, nullptr);
}

#if defined(WEBRTC_ANDROID)
#define MAYBE_MultiresVP8 DISABLED_MultiresVP8
#else
#define MAYBE_MultiresVP8 MultiresVP8
#endif
TEST(VideoCodecTestLibvpx, MAYBE_MultiresVP8) {
  auto config = CreateConfig();
  config.filename = "ConferenceMotion_1280_720_50";
  config.filepath = ResourcePath(config.filename, "yuv");
  config.num_frames = 100;
  config.SetCodecSettings(cricket::kVp8CodecName, 3, 1, 3, true, true, false,
                          1280, 720);
  const auto frame_checker = std::make_unique<QpFrameChecker>();
  config.encoded_frame_checker = frame_checker.get();
  auto fixture = CreateVideoCodecTestFixture(config);

  std::vector<RateProfile> rate_profiles = {{1500, 30, 0}};
#if defined(WEBRTC_ARCH_ARM) || defined(WEBRTC_ARCH_ARM64)
  std::vector<RateControlThresholds> rc_thresholds = {
      {4.1, 1.04, 7, 0.18, 0.14, 0.08, 0, 1}};
#else
  std::vector<RateControlThresholds> rc_thresholds = {
      {5, 1, 5, 1, 0.3, 0.1, 0, 1}};
#endif
  std::vector<QualityThresholds> quality_thresholds = {{34, 32, 0.90, 0.88}};

  fixture->RunTest(rate_profiles, &rc_thresholds, &quality_thresholds, nullptr);
}

#if defined(WEBRTC_ANDROID)
#define MAYBE_SimulcastVP8 DISABLED_SimulcastVP8
#else
#define MAYBE_SimulcastVP8 SimulcastVP8
#endif
TEST(VideoCodecTestLibvpx, MAYBE_SimulcastVP8) {
  auto config = CreateConfig();
  config.filename = "ConferenceMotion_1280_720_50";
  config.filepath = ResourcePath(config.filename, "yuv");
  config.num_frames = 100;
  config.SetCodecSettings(cricket::kVp8CodecName, 3, 1, 3, true, true, false,
                          1280, 720);
  const auto frame_checker = std::make_unique<QpFrameChecker>();
  config.encoded_frame_checker = frame_checker.get();

  InternalEncoderFactory internal_encoder_factory;
  std::unique_ptr<VideoEncoderFactory> adapted_encoder_factory =
      std::make_unique<FunctionVideoEncoderFactory>([&]() {
        return std::make_unique<SimulcastEncoderAdapter>(
            &internal_encoder_factory, SdpVideoFormat(cricket::kVp8CodecName));
      });
  std::unique_ptr<InternalDecoderFactory> internal_decoder_factory(
      new InternalDecoderFactory());

  auto fixture =
      CreateVideoCodecTestFixture(config, std::move(internal_decoder_factory),
                                  std::move(adapted_encoder_factory));

  std::vector<RateProfile> rate_profiles = {{1500, 30, 0}};

  std::vector<RateControlThresholds> rc_thresholds = {
      {20, 5, 90, 1, 0.5, 0.3, 0, 1}};
  std::vector<QualityThresholds> quality_thresholds = {{34, 32, 0.90, 0.88}};

  fixture->RunTest(rate_profiles, &rc_thresholds, &quality_thresholds, nullptr);
}

#if defined(WEBRTC_ANDROID)
#define MAYBE_SvcVP9 DISABLED_SvcVP9
#else
#define MAYBE_SvcVP9 SvcVP9
#endif
TEST(VideoCodecTestLibvpx, MAYBE_SvcVP9) {
  auto config = CreateConfig();
  config.filename = "ConferenceMotion_1280_720_50";
  config.filepath = ResourcePath(config.filename, "yuv");
  config.num_frames = 100;
  config.SetCodecSettings(cricket::kVp9CodecName, 1, 3, 3, true, true, false,
                          1280, 720);
  const auto frame_checker = std::make_unique<QpFrameChecker>();
  config.encoded_frame_checker = frame_checker.get();
  auto fixture = CreateVideoCodecTestFixture(config);

  std::vector<RateProfile> rate_profiles = {{1500, 30, 0}};

  std::vector<RateControlThresholds> rc_thresholds = {
      {5, 1, 5, 1, 0.3, 0.1, 0, 1}};
  std::vector<QualityThresholds> quality_thresholds = {{36, 34, 0.93, 0.90}};

  fixture->RunTest(rate_profiles, &rc_thresholds, &quality_thresholds, nullptr);
}

TEST(VideoCodecTestLibvpx, DISABLED_MultiresVP8RdPerf) {
  auto config = CreateConfig();
  config.filename = "FourPeople_1280x720_30";
  config.filepath = ResourcePath(config.filename, "yuv");
  config.num_frames = 300;
  config.print_frame_level_stats = true;
  config.SetCodecSettings(cricket::kVp8CodecName, 3, 1, 3, true, true, false,
                          1280, 720);
  const auto frame_checker = std::make_unique<QpFrameChecker>();
  config.encoded_frame_checker = frame_checker.get();
  auto fixture = CreateVideoCodecTestFixture(config);

  std::map<size_t, std::vector<VideoStatistics>> rd_stats;
  for (size_t bitrate_kbps : kBitrateRdPerfKbps) {
    std::vector<RateProfile> rate_profiles = {{bitrate_kbps, 30, 0}};

    fixture->RunTest(rate_profiles, nullptr, nullptr, nullptr);

    rd_stats[bitrate_kbps] =
        fixture->GetStats().SliceAndCalcLayerVideoStatistic(
            kNumFirstFramesToSkipAtRdPerfAnalysis, config.num_frames - 1);
  }

  PrintRdPerf(rd_stats);
}

TEST(VideoCodecTestLibvpx, DISABLED_SvcVP9RdPerf) {
  auto config = CreateConfig();
  config.filename = "FourPeople_1280x720_30";
  config.filepath = ResourcePath(config.filename, "yuv");
  config.num_frames = 300;
  config.print_frame_level_stats = true;
  config.SetCodecSettings(cricket::kVp9CodecName, 1, 3, 3, true, true, false,
                          1280, 720);
  const auto frame_checker = std::make_unique<QpFrameChecker>();
  config.encoded_frame_checker = frame_checker.get();
  auto fixture = CreateVideoCodecTestFixture(config);

  std::map<size_t, std::vector<VideoStatistics>> rd_stats;
  for (size_t bitrate_kbps : kBitrateRdPerfKbps) {
    std::vector<RateProfile> rate_profiles = {{bitrate_kbps, 30, 0}};

    fixture->RunTest(rate_profiles, nullptr, nullptr, nullptr);

    rd_stats[bitrate_kbps] =
        fixture->GetStats().SliceAndCalcLayerVideoStatistic(
            kNumFirstFramesToSkipAtRdPerfAnalysis, config.num_frames - 1);
  }

  PrintRdPerf(rd_stats);
}

}  // namespace test
}  // namespace webrtc
