/*
 *  Copyright (c) 2017 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include <memory>
#include <string>
#include <tuple>
#include <vector>

#include "api/test/create_videocodec_test_fixture.h"
#include "media/base/media_constants.h"
#include "modules/video_coding/codecs/test/android_codec_factory_helper.h"
#include "modules/video_coding/codecs/test/videocodec_test_fixture_impl.h"
#include "rtc_base/strings/string_builder.h"
#include "test/gtest.h"
#include "test/testsupport/file_utils.h"

namespace webrtc {
namespace test {

namespace {
const int kForemanNumFrames = 300;
const int kForemanFramerateFps = 30;

struct RateProfileData {
  std::string name;
  std::vector<webrtc::test::RateProfile> rate_profile;
};

const size_t kConstRateIntervalSec = 10;

const RateProfileData kBitRateHighLowHigh = {
    /*name=*/"BitRateHighLowHigh",
    /*rate_profile=*/{
        {/*target_kbps=*/3000, /*input_fps=*/30, /*frame_num=*/0},
        {/*target_kbps=*/1500, /*input_fps=*/30, /*frame_num=*/300},
        {/*target_kbps=*/750, /*input_fps=*/30, /*frame_num=*/600},
        {/*target_kbps=*/1500, /*input_fps=*/30, /*frame_num=*/900},
        {/*target_kbps=*/3000, /*input_fps=*/30, /*frame_num=*/1200}}};

const RateProfileData kBitRateLowHighLow = {
    /*name=*/"BitRateLowHighLow",
    /*rate_profile=*/{
        {/*target_kbps=*/750, /*input_fps=*/30, /*frame_num=*/0},
        {/*target_kbps=*/1500, /*input_fps=*/30, /*frame_num=*/300},
        {/*target_kbps=*/3000, /*input_fps=*/30, /*frame_num=*/600},
        {/*target_kbps=*/1500, /*input_fps=*/30, /*frame_num=*/900},
        {/*target_kbps=*/750, /*input_fps=*/30, /*frame_num=*/1200}}};

const RateProfileData kFrameRateHighLowHigh = {
    /*name=*/"FrameRateHighLowHigh",
    /*rate_profile=*/{
        {/*target_kbps=*/2000, /*input_fps=*/30, /*frame_num=*/0},
        {/*target_kbps=*/2000, /*input_fps=*/15, /*frame_num=*/300},
        {/*target_kbps=*/2000, /*input_fps=*/7.5, /*frame_num=*/450},
        {/*target_kbps=*/2000, /*input_fps=*/15, /*frame_num=*/525},
        {/*target_kbps=*/2000, /*input_fps=*/30, /*frame_num=*/675}}};

const RateProfileData kFrameRateLowHighLow = {
    /*name=*/"FrameRateLowHighLow",
    /*rate_profile=*/{
        {/*target_kbps=*/2000, /*input_fps=*/7.5, /*frame_num=*/0},
        {/*target_kbps=*/2000, /*input_fps=*/15, /*frame_num=*/75},
        {/*target_kbps=*/2000, /*input_fps=*/30, /*frame_num=*/225},
        {/*target_kbps=*/2000, /*input_fps=*/15, /*frame_num=*/525},
        {/*target_kbps=*/2000, /*input_fps=*/7.5, /*frame_num=*/775}}};

VideoCodecTestFixture::Config CreateConfig() {
  VideoCodecTestFixture::Config config;
  config.filename = "foreman_cif";
  config.filepath = ResourcePath(config.filename, "yuv");
  config.num_frames = kForemanNumFrames;
  // In order to not overwhelm the OpenMAX buffers in the Android MediaCodec.
  config.encode_in_real_time = true;
  return config;
}

std::unique_ptr<VideoCodecTestFixture> CreateTestFixtureWithConfig(
    VideoCodecTestFixture::Config config) {
  InitializeAndroidObjects();  // Idempotent.
  auto encoder_factory = CreateAndroidEncoderFactory();
  auto decoder_factory = CreateAndroidDecoderFactory();
  return CreateVideoCodecTestFixture(config, std::move(decoder_factory),
                                     std::move(encoder_factory));
}
}  // namespace

TEST(VideoCodecTestMediaCodec, ForemanCif500kbpsVp8) {
  auto config = CreateConfig();
  config.SetCodecSettings(cricket::kVp8CodecName, 1, 1, 1, false, false, false,
                          352, 288);
  auto fixture = CreateTestFixtureWithConfig(config);

  std::vector<RateProfile> rate_profiles = {{500, kForemanFramerateFps, 0}};

  // The thresholds below may have to be tweaked to let even poor MediaCodec
  // implementations pass. If this test fails on the bots, disable it and
  // ping brandtr@.
  std::vector<RateControlThresholds> rc_thresholds = {
      {10, 1, 1, 0.1, 0.2, 0.1, 0, 1}};

  std::vector<QualityThresholds> quality_thresholds = {{36, 31, 0.92, 0.86}};

  fixture->RunTest(rate_profiles, &rc_thresholds, &quality_thresholds, nullptr);
}

TEST(VideoCodecTestMediaCodec, ForemanCif500kbpsH264CBP) {
  auto config = CreateConfig();
  const auto frame_checker =
      std::make_unique<VideoCodecTestFixtureImpl::H264KeyframeChecker>();
  config.encoded_frame_checker = frame_checker.get();
  config.SetCodecSettings(cricket::kH264CodecName, 1, 1, 1, false, false, false,
                          352, 288);
  auto fixture = CreateTestFixtureWithConfig(config);

  std::vector<RateProfile> rate_profiles = {{500, kForemanFramerateFps, 0}};

  // The thresholds below may have to be tweaked to let even poor MediaCodec
  // implementations pass. If this test fails on the bots, disable it and
  // ping brandtr@.
  std::vector<RateControlThresholds> rc_thresholds = {
      {10, 1, 1, 0.1, 0.2, 0.1, 0, 1}};

  std::vector<QualityThresholds> quality_thresholds = {{36, 31, 0.92, 0.86}};

  fixture->RunTest(rate_profiles, &rc_thresholds, &quality_thresholds, nullptr);
}

// TODO(brandtr): Enable this test when we have trybots/buildbots with
// HW encoders that support CHP.
TEST(VideoCodecTestMediaCodec, DISABLED_ForemanCif500kbpsH264CHP) {
  auto config = CreateConfig();
  const auto frame_checker =
      std::make_unique<VideoCodecTestFixtureImpl::H264KeyframeChecker>();

  config.h264_codec_settings.profile = H264Profile::kProfileConstrainedHigh;
  config.encoded_frame_checker = frame_checker.get();
  config.SetCodecSettings(cricket::kH264CodecName, 1, 1, 1, false, false, false,
                          352, 288);
  auto fixture = CreateTestFixtureWithConfig(config);

  std::vector<RateProfile> rate_profiles = {{500, kForemanFramerateFps, 0}};

  // The thresholds below may have to be tweaked to let even poor MediaCodec
  // implementations pass. If this test fails on the bots, disable it and
  // ping brandtr@.
  std::vector<RateControlThresholds> rc_thresholds = {
      {5, 1, 0, 0.1, 0.2, 0.1, 0, 1}};

  std::vector<QualityThresholds> quality_thresholds = {{37, 35, 0.93, 0.91}};

  fixture->RunTest(rate_profiles, &rc_thresholds, &quality_thresholds, nullptr);
}

TEST(VideoCodecTestMediaCodec, ForemanMixedRes100kbpsVp8H264) {
  auto config = CreateConfig();
  const int kNumFrames = 30;
  const std::vector<std::string> codecs = {cricket::kVp8CodecName,
                                           cricket::kH264CodecName};
  const std::vector<std::tuple<int, int>> resolutions = {
      {128, 96}, {176, 144}, {320, 240}, {480, 272}};
  const std::vector<RateProfile> rate_profiles = {
      {100, kForemanFramerateFps, 0}};
  const std::vector<QualityThresholds> quality_thresholds = {
      {29, 26, 0.8, 0.75}};

  for (const auto& codec : codecs) {
    for (const auto& resolution : resolutions) {
      const int width = std::get<0>(resolution);
      const int height = std::get<1>(resolution);
      config.filename = std::string("foreman_") + std::to_string(width) + "x" +
                        std::to_string(height);
      config.filepath = ResourcePath(config.filename, "yuv");
      config.num_frames = kNumFrames;
      config.SetCodecSettings(codec, 1, 1, 1, false, false, false, width,
                              height);

      auto fixture = CreateTestFixtureWithConfig(config);
      fixture->RunTest(rate_profiles, nullptr /* rc_thresholds */,
                       &quality_thresholds, nullptr /* bs_thresholds */);
    }
  }
}

class VideoCodecTestMediaCodecRateAdaptation
    : public ::testing::TestWithParam<
          std::tuple<RateProfileData, std::string>> {
 public:
  static std::string ParamInfoToStr(
      const ::testing::TestParamInfo<
          VideoCodecTestMediaCodecRateAdaptation::ParamType>& info) {
    char buf[512];
    rtc::SimpleStringBuilder ss(buf);
    ss << std::get<0>(info.param).name << "_" << std::get<1>(info.param);
    return ss.str();
  }
};

TEST_P(VideoCodecTestMediaCodecRateAdaptation, DISABLED_RateAdaptation) {
  const std::vector<webrtc::test::RateProfile> rate_profile =
      std::get<0>(GetParam()).rate_profile;
  const std::string codec_name = std::get<1>(GetParam());

  VideoCodecTestFixture::Config config;
  config.filename = "FourPeople_1280x720_30";
  config.filepath = ResourcePath(config.filename, "yuv");
  config.num_frames = rate_profile.back().frame_num +
                      static_cast<size_t>(kConstRateIntervalSec *
                                          rate_profile.back().input_fps);
  config.encode_in_real_time = true;
  config.SetCodecSettings(codec_name, 1, 1, 1, false, false, false, 1280, 720);

  auto fixture = CreateTestFixtureWithConfig(config);
  fixture->RunTest(rate_profile, nullptr, nullptr, nullptr);

  for (size_t i = 0; i < rate_profile.size(); ++i) {
    const size_t num_frames =
        static_cast<size_t>(rate_profile[i].input_fps * kConstRateIntervalSec);

    auto stats = fixture->GetStats().SliceAndCalcLayerVideoStatistic(
        rate_profile[i].frame_num, rate_profile[i].frame_num + num_frames - 1);
    ASSERT_EQ(stats.size(), 1u);

    // Bitrate mismatch is <= 10%.
    EXPECT_LE(stats[0].avg_bitrate_mismatch_pct, 10);
    EXPECT_GE(stats[0].avg_bitrate_mismatch_pct, -10);

    // Avg frame transmission delay and processing latency is <=100..250ms
    // depending on frame rate.
    const double expected_delay_sec =
        std::min(std::max(1 / rate_profile[i].input_fps, 0.1), 0.25);
    EXPECT_LE(stats[0].avg_delay_sec, expected_delay_sec);
    EXPECT_LE(stats[0].avg_encode_latency_sec, expected_delay_sec);
    EXPECT_LE(stats[0].avg_decode_latency_sec, expected_delay_sec);

    // Frame drops are not expected.
    EXPECT_EQ(stats[0].num_encoded_frames, num_frames);
    EXPECT_EQ(stats[0].num_decoded_frames, num_frames);

    // Periodic keyframes are not expected.
    EXPECT_EQ(stats[0].num_key_frames, i == 0 ? 1u : 0);

    // Ensure codec delivers a reasonable spatial quality.
    EXPECT_GE(stats[0].avg_psnr_y, 35);
  }
}

INSTANTIATE_TEST_SUITE_P(
    RateAdaptation,
    VideoCodecTestMediaCodecRateAdaptation,
    ::testing::Combine(::testing::Values(kBitRateLowHighLow,
                                         kBitRateHighLowHigh,
                                         kFrameRateLowHighLow,
                                         kFrameRateHighLowHigh),
                       ::testing::Values(cricket::kVp8CodecName,
                                         cricket::kVp9CodecName,
                                         cricket::kH264CodecName)),
    VideoCodecTestMediaCodecRateAdaptation::ParamInfoToStr);

}  // namespace test
}  // namespace webrtc
