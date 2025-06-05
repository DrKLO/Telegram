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
#include <vector>

#include "absl/flags/flag.h"
#include "absl/functional/any_invocable.h"
#include "api/environment/environment.h"
#include "api/environment/environment_factory.h"
#include "api/test/metrics/global_metrics_logger_and_exporter.h"
#include "api/units/data_rate.h"
#include "api/units/frequency.h"
#include "api/video/resolution.h"
#include "api/video_codecs/builtin_video_decoder_factory.h"
#include "api/video_codecs/builtin_video_encoder_factory.h"
#if defined(WEBRTC_ANDROID)
#include "modules/video_coding/codecs/test/android_codec_factory_helper.h"
#endif
#include "modules/video_coding/svc/scalability_mode_util.h"
#include "rtc_base/logging.h"
#include "rtc_base/strings/string_builder.h"
#include "test/explicit_key_value_config.h"
#include "test/field_trial.h"
#include "test/gtest.h"
#include "test/test_flags.h"
#include "test/testsupport/file_utils.h"
#include "test/video_codec_tester.h"

ABSL_FLAG(std::string,
          video_name,
          "FourPeople_1280x720_30",
          "Name of input video sequence.");
ABSL_FLAG(std::string,
          encoder,
          "libaom-av1",
          "Encoder: libaom-av1, libvpx-vp9, libvpx-vp8, openh264, hw-vp8, "
          "hw-vp9, hw-av1, hw-h264, hw-h265");
ABSL_FLAG(std::string,
          decoder,
          "dav1d",
          "Decoder: dav1d, libvpx-vp9, libvpx-vp8, ffmpeg-h264, hw-vp8, "
          "hw-vp9, hw-av1, hw-h264, hw-h265");
ABSL_FLAG(std::string, scalability_mode, "L1T1", "Scalability mode.");
ABSL_FLAG(int, width, 1280, "Width.");
ABSL_FLAG(int, height, 720, "Height.");
ABSL_FLAG(std::vector<std::string>,
          bitrate_kbps,
          {"1024"},
          "Encode target bitrate per layer (l0t0,l0t1,...l1t0,l1t1 and so on) "
          "in kbps.");
ABSL_FLAG(double,
          framerate_fps,
          30.0,
          "Encode target frame rate of the top temporal layer in fps.");
ABSL_FLAG(int, num_frames, 300, "Number of frames to encode and/or decode.");
ABSL_FLAG(std::string, field_trials, "", "Field trials to apply.");
ABSL_FLAG(std::string, test_name, "", "Test name.");
ABSL_FLAG(bool, dump_decoder_input, false, "Dump decoder input.");
ABSL_FLAG(bool, dump_decoder_output, false, "Dump decoder output.");
ABSL_FLAG(bool, dump_encoder_input, false, "Dump encoder input.");
ABSL_FLAG(bool, dump_encoder_output, false, "Dump encoder output.");
ABSL_FLAG(bool, write_csv, false, "Write metrics to a CSV file.");

namespace webrtc {
namespace test {

namespace {
using ::testing::Combine;
using ::testing::Values;
using VideoSourceSettings = VideoCodecTester::VideoSourceSettings;
using EncodingSettings = VideoCodecTester::EncodingSettings;
using VideoCodecStats = VideoCodecTester::VideoCodecStats;
using Filter = VideoCodecStats::Filter;
using PacingMode = VideoCodecTester::PacingSettings::PacingMode;

struct VideoInfo {
  std::string name;
  Resolution resolution;
  Frequency framerate;
};

const std::map<std::string, VideoInfo> kRawVideos = {
    {"FourPeople_1280x720_30",
     {.name = "FourPeople_1280x720_30",
      .resolution = {.width = 1280, .height = 720},
      .framerate = Frequency::Hertz(30)}},
    {"vidyo1_1280x720_30",
     {.name = "vidyo1_1280x720_30",
      .resolution = {.width = 1280, .height = 720},
      .framerate = Frequency::Hertz(30)}},
    {"vidyo4_1280x720_30",
     {.name = "vidyo4_1280x720_30",
      .resolution = {.width = 1280, .height = 720},
      .framerate = Frequency::Hertz(30)}},
    {"KristenAndSara_1280x720_30",
     {.name = "KristenAndSara_1280x720_30",
      .resolution = {.width = 1280, .height = 720},
      .framerate = Frequency::Hertz(30)}},
    {"Johnny_1280x720_30",
     {.name = "Johnny_1280x720_30",
      .resolution = {.width = 1280, .height = 720},
      .framerate = Frequency::Hertz(30)}}};

static constexpr Frequency k90kHz = Frequency::Hertz(90000);

std::string CodecNameToCodecType(std::string name) {
  if (name.find("av1") != std::string::npos) {
    return "AV1";
  }
  if (name.find("vp9") != std::string::npos) {
    return "VP9";
  }
  if (name.find("vp8") != std::string::npos) {
    return "VP8";
  }
  if (name.find("h264") != std::string::npos) {
    return "H264";
  }
  if (name.find("h265") != std::string::npos) {
    return "H265";
  }
  RTC_CHECK_NOTREACHED();
}

// TODO(webrtc:14852): Make Create[Encoder,Decoder]Factory to work with codec
// name directly.
std::string CodecNameToCodecImpl(std::string name) {
  if (name.find("hw") != std::string::npos) {
    return "mediacodec";
  }
  return "builtin";
}

std::unique_ptr<VideoEncoderFactory> CreateEncoderFactory(std::string impl) {
  if (impl == "builtin") {
    return CreateBuiltinVideoEncoderFactory();
  }
#if defined(WEBRTC_ANDROID)
  InitializeAndroidObjects();
  return CreateAndroidEncoderFactory();
#else
  return nullptr;
#endif
}

std::unique_ptr<VideoDecoderFactory> CreateDecoderFactory(std::string impl) {
  if (impl == "builtin") {
    return CreateBuiltinVideoDecoderFactory();
  }
#if defined(WEBRTC_ANDROID)
  InitializeAndroidObjects();
  return CreateAndroidDecoderFactory();
#else
  return nullptr;
#endif
}

std::string TestName() {
  std::string test_name = absl::GetFlag(FLAGS_test_name);
  if (!test_name.empty()) {
    return test_name;
  }
  return ::testing::UnitTest::GetInstance()->current_test_info()->name();
}

std::string TestOutputPath() {
  std::string output_path =
      (rtc::StringBuilder() << OutputPath() << TestName()).str();
  std::string output_dir = DirName(output_path);
  bool result = CreateDir(output_dir);
  RTC_CHECK(result) << "Cannot create " << output_dir;
  return output_path;
}
}  // namespace

std::unique_ptr<VideoCodecStats> RunEncodeDecodeTest(
    const Environment& env,
    std::string encoder_impl,
    std::string decoder_impl,
    const VideoInfo& video_info,
    const std::map<uint32_t, EncodingSettings>& encoding_settings) {
  VideoSourceSettings source_settings{
      .file_path = ResourcePath(video_info.name, "yuv"),
      .resolution = video_info.resolution,
      .framerate = video_info.framerate};

  const SdpVideoFormat& sdp_video_format =
      encoding_settings.begin()->second.sdp_video_format;

  std::unique_ptr<VideoEncoderFactory> encoder_factory =
      CreateEncoderFactory(encoder_impl);
  if (!encoder_factory
           ->QueryCodecSupport(sdp_video_format,
                               /*scalability_mode=*/absl::nullopt)
           .is_supported) {
    RTC_LOG(LS_WARNING) << "No " << encoder_impl << " encoder for video format "
                        << sdp_video_format.ToString();
    return nullptr;
  }

  std::unique_ptr<VideoDecoderFactory> decoder_factory =
      CreateDecoderFactory(decoder_impl);
  if (!decoder_factory
           ->QueryCodecSupport(sdp_video_format,
                               /*reference_scaling=*/false)
           .is_supported) {
    RTC_LOG(LS_WARNING) << "No " << decoder_impl << " decoder for video format "
                        << sdp_video_format.ToString()
                        << ". Trying built-in decoder.";
    // TODO(ssilkin): No H264 support in ffmpeg on ARM. Consider trying HW
    // decoder.
    decoder_factory = CreateDecoderFactory("builtin");
    if (!decoder_factory
             ->QueryCodecSupport(sdp_video_format,
                                 /*reference_scaling=*/false)
             .is_supported) {
      RTC_LOG(LS_WARNING) << "No " << decoder_impl
                          << " decoder for video format "
                          << sdp_video_format.ToString();
      return nullptr;
    }
  }

  std::string output_path = TestOutputPath();

  VideoCodecTester::EncoderSettings encoder_settings;
  encoder_settings.pacing_settings.mode =
      encoder_impl == "builtin" ? PacingMode::kNoPacing : PacingMode::kRealTime;
  if (absl::GetFlag(FLAGS_dump_encoder_input)) {
    encoder_settings.encoder_input_base_path = output_path + "_enc_input";
  }
  if (absl::GetFlag(FLAGS_dump_encoder_output)) {
    encoder_settings.encoder_output_base_path = output_path + "_enc_output";
  }

  VideoCodecTester::DecoderSettings decoder_settings;
  decoder_settings.pacing_settings.mode =
      decoder_impl == "builtin" ? PacingMode::kNoPacing : PacingMode::kRealTime;
  if (absl::GetFlag(FLAGS_dump_decoder_input)) {
    decoder_settings.decoder_input_base_path = output_path + "_dec_input";
  }
  if (absl::GetFlag(FLAGS_dump_decoder_output)) {
    decoder_settings.decoder_output_base_path = output_path + "_dec_output";
  }

  return VideoCodecTester::RunEncodeDecodeTest(
      env, source_settings, encoder_factory.get(), decoder_factory.get(),
      encoder_settings, decoder_settings, encoding_settings);
}

std::unique_ptr<VideoCodecStats> RunEncodeTest(
    std::string codec_type,
    std::string codec_impl,
    const VideoInfo& video_info,
    const std::map<uint32_t, EncodingSettings>& encoding_settings) {
  VideoSourceSettings source_settings{
      .file_path = ResourcePath(video_info.name, "yuv"),
      .resolution = video_info.resolution,
      .framerate = video_info.framerate};

  const SdpVideoFormat& sdp_video_format =
      encoding_settings.begin()->second.sdp_video_format;

  std::unique_ptr<VideoEncoderFactory> encoder_factory =
      CreateEncoderFactory(codec_impl);
  if (!encoder_factory
           ->QueryCodecSupport(sdp_video_format,
                               /*scalability_mode=*/absl::nullopt)
           .is_supported) {
    RTC_LOG(LS_WARNING) << "No encoder for video format "
                        << sdp_video_format.ToString();
    return nullptr;
  }

  std::string output_path = TestOutputPath();
  VideoCodecTester::EncoderSettings encoder_settings;
  encoder_settings.pacing_settings.mode =
      codec_impl == "builtin" ? PacingMode::kNoPacing : PacingMode::kRealTime;
  if (absl::GetFlag(FLAGS_dump_encoder_input)) {
    encoder_settings.encoder_input_base_path = output_path + "_enc_input";
  }
  if (absl::GetFlag(FLAGS_dump_encoder_output)) {
    encoder_settings.encoder_output_base_path = output_path + "_enc_output";
  }

  return VideoCodecTester::RunEncodeTest(source_settings, encoder_factory.get(),
                                         encoder_settings, encoding_settings);
}

class SpatialQualityTest : public ::testing::TestWithParam<std::tuple<
                               /*codec_type=*/std::string,
                               /*codec_impl=*/std::string,
                               VideoInfo,
                               std::tuple</*width=*/int,
                                          /*height=*/int,
                                          /*framerate_fps=*/double,
                                          /*bitrate_kbps=*/int,
                                          /*expected_min_psnr=*/double>>> {
 public:
  static std::string TestParamsToString(
      const ::testing::TestParamInfo<SpatialQualityTest::ParamType>& info) {
    auto [codec_type, codec_impl, video_info, coding_settings] = info.param;
    auto [width, height, framerate_fps, bitrate_kbps, psnr] = coding_settings;
    return std::string(codec_type + codec_impl + video_info.name +
                       std::to_string(width) + "x" + std::to_string(height) +
                       "p" +
                       std::to_string(static_cast<int>(1000 * framerate_fps)) +
                       "mhz" + std::to_string(bitrate_kbps) + "kbps");
  }
};

TEST_P(SpatialQualityTest, SpatialQuality) {
  const Environment env = CreateEnvironment();
  auto [codec_type, codec_impl, video_info, coding_settings] = GetParam();
  auto [width, height, framerate_fps, bitrate_kbps, expected_min_psnr] =
      coding_settings;
  int duration_s = 10;
  int num_frames = duration_s * framerate_fps;

  std::map<uint32_t, EncodingSettings> frames_settings =
      VideoCodecTester::CreateEncodingSettings(
          codec_type, /*scalability_mode=*/"L1T1", width, height,
          {bitrate_kbps}, framerate_fps, num_frames);

  std::unique_ptr<VideoCodecStats> stats = RunEncodeDecodeTest(
      env, codec_impl, codec_impl, video_info, frames_settings);

  VideoCodecStats::Stream stream;
  if (stats != nullptr) {
    stream = stats->Aggregate(Filter{});
    if (absl::GetFlag(FLAGS_webrtc_quick_perf_test)) {
      EXPECT_GE(stream.psnr.y.GetAverage(), expected_min_psnr);
    }
  }

  stream.LogMetrics(
      GetGlobalMetricsLogger(),
      ::testing::UnitTest::GetInstance()->current_test_info()->name(),
      /*prefix=*/"",
      /*metadata=*/
      {{"video_name", video_info.name},
       {"codec_type", codec_type},
       {"codec_impl", codec_impl}});
}

INSTANTIATE_TEST_SUITE_P(
    All,
    SpatialQualityTest,
    Combine(Values("AV1", "VP9", "VP8", "H264", "H265"),
#if defined(WEBRTC_ANDROID)
            Values("builtin", "mediacodec"),
#else
            Values("builtin"),
#endif
            Values(kRawVideos.at("FourPeople_1280x720_30")),
            Values(std::make_tuple(320, 180, 30, 32, 26),
                   std::make_tuple(320, 180, 30, 64, 29),
                   std::make_tuple(320, 180, 30, 128, 32),
                   std::make_tuple(320, 180, 30, 256, 36),
                   std::make_tuple(640, 360, 30, 128, 29),
                   std::make_tuple(640, 360, 30, 256, 33),
                   std::make_tuple(640, 360, 30, 384, 35),
                   std::make_tuple(640, 360, 30, 512, 36),
                   std::make_tuple(1280, 720, 30, 256, 30),
                   std::make_tuple(1280, 720, 30, 512, 34),
                   std::make_tuple(1280, 720, 30, 1024, 37),
                   std::make_tuple(1280, 720, 30, 2048, 39))),
    SpatialQualityTest::TestParamsToString);

class BitrateAdaptationTest
    : public ::testing::TestWithParam<
          std::tuple</*codec_type=*/std::string,
                     /*codec_impl=*/std::string,
                     VideoInfo,
                     std::pair</*bitrate_kbps=*/int, /*bitrate_kbps=*/int>>> {
 public:
  static std::string TestParamsToString(
      const ::testing::TestParamInfo<BitrateAdaptationTest::ParamType>& info) {
    auto [codec_type, codec_impl, video_info, bitrate_kbps] = info.param;
    return std::string(codec_type + codec_impl + video_info.name +
                       std::to_string(bitrate_kbps.first) + "kbps" +
                       std::to_string(bitrate_kbps.second) + "kbps");
  }
};

TEST_P(BitrateAdaptationTest, BitrateAdaptation) {
  auto [codec_type, codec_impl, video_info, bitrate_kbps] = GetParam();

  int duration_s = 10;  // Duration of fixed rate interval.
  int num_frames =
      static_cast<int>(duration_s * video_info.framerate.hertz<double>());

  std::map<uint32_t, EncodingSettings> encoding_settings =
      VideoCodecTester::CreateEncodingSettings(
          codec_type, /*scalability_mode=*/"L1T1",
          /*width=*/640, /*height=*/360, {bitrate_kbps.first},
          /*framerate_fps=*/30, num_frames);

  uint32_t initial_timestamp_rtp =
      encoding_settings.rbegin()->first + k90kHz / Frequency::Hertz(30);

  std::map<uint32_t, EncodingSettings> encoding_settings2 =
      VideoCodecTester::CreateEncodingSettings(
          codec_type, /*scalability_mode=*/"L1T1",
          /*width=*/640, /*height=*/360, {bitrate_kbps.second},
          /*framerate_fps=*/30, num_frames, initial_timestamp_rtp);

  encoding_settings.merge(encoding_settings2);

  std::unique_ptr<VideoCodecStats> stats =
      RunEncodeTest(codec_type, codec_impl, video_info, encoding_settings);

  VideoCodecStats::Stream stream;
  if (stats != nullptr) {
    stream = stats->Aggregate({.min_timestamp_rtp = initial_timestamp_rtp});
    if (absl::GetFlag(FLAGS_webrtc_quick_perf_test)) {
      EXPECT_NEAR(stream.bitrate_mismatch_pct.GetAverage(), 0, 10);
      EXPECT_NEAR(stream.framerate_mismatch_pct.GetAverage(), 0, 10);
    }
  }

  stream.LogMetrics(
      GetGlobalMetricsLogger(),
      ::testing::UnitTest::GetInstance()->current_test_info()->name(),
      /*prefix=*/"",
      /*metadata=*/
      {{"codec_type", codec_type},
       {"codec_impl", codec_impl},
       {"video_name", video_info.name},
       {"rate_profile", std::to_string(bitrate_kbps.first) + "," +
                            std::to_string(bitrate_kbps.second)}});
}

INSTANTIATE_TEST_SUITE_P(
    All,
    BitrateAdaptationTest,
    Combine(Values("AV1", "VP9", "VP8", "H264", "H265"),
#if defined(WEBRTC_ANDROID)
            Values("builtin", "mediacodec"),
#else
            Values("builtin"),
#endif
            Values(kRawVideos.at("FourPeople_1280x720_30")),
            Values(std::pair(1024, 512), std::pair(512, 1024))),
    BitrateAdaptationTest::TestParamsToString);

class FramerateAdaptationTest
    : public ::testing::TestWithParam<std::tuple</*codec_type=*/std::string,
                                                 /*codec_impl=*/std::string,
                                                 VideoInfo,
                                                 std::pair<double, double>>> {
 public:
  static std::string TestParamsToString(
      const ::testing::TestParamInfo<FramerateAdaptationTest::ParamType>&
          info) {
    auto [codec_type, codec_impl, video_info, framerate_fps] = info.param;
    return std::string(
        codec_type + codec_impl + video_info.name +
        std::to_string(static_cast<int>(1000 * framerate_fps.first)) + "mhz" +
        std::to_string(static_cast<int>(1000 * framerate_fps.second)) + "mhz");
  }
};

TEST_P(FramerateAdaptationTest, FramerateAdaptation) {
  auto [codec_type, codec_impl, video_info, framerate_fps] = GetParam();

  int duration_s = 10;  // Duration of fixed rate interval.

  std::map<uint32_t, EncodingSettings> encoding_settings =
      VideoCodecTester::CreateEncodingSettings(
          codec_type, /*scalability_mode=*/"L1T1",
          /*width=*/640, /*height=*/360,
          /*layer_bitrates_kbps=*/{512}, framerate_fps.first,
          static_cast<int>(duration_s * framerate_fps.first));

  uint32_t initial_timestamp_rtp =
      encoding_settings.rbegin()->first +
      k90kHz / Frequency::Hertz(framerate_fps.first);

  std::map<uint32_t, EncodingSettings> encoding_settings2 =
      VideoCodecTester::CreateEncodingSettings(
          codec_type, /*scalability_mode=*/"L1T1", /*width=*/640,
          /*height=*/360,
          /*layer_bitrates_kbps=*/{512}, framerate_fps.second,
          static_cast<int>(duration_s * framerate_fps.second),
          initial_timestamp_rtp);

  encoding_settings.merge(encoding_settings2);

  std::unique_ptr<VideoCodecStats> stats =
      RunEncodeTest(codec_type, codec_impl, video_info, encoding_settings);

  VideoCodecStats::Stream stream;
  if (stats != nullptr) {
    stream = stats->Aggregate({.min_timestamp_rtp = initial_timestamp_rtp});
    if (absl::GetFlag(FLAGS_webrtc_quick_perf_test)) {
      EXPECT_NEAR(stream.bitrate_mismatch_pct.GetAverage(), 0, 10);
      EXPECT_NEAR(stream.framerate_mismatch_pct.GetAverage(), 0, 10);
    }
  }

  stream.LogMetrics(
      GetGlobalMetricsLogger(),
      ::testing::UnitTest::GetInstance()->current_test_info()->name(),
      /*prefix=*/"",
      /*metadata=*/
      {{"codec_type", codec_type},
       {"codec_impl", codec_impl},
       {"video_name", video_info.name},
       {"rate_profile", std::to_string(framerate_fps.first) + "," +
                            std::to_string(framerate_fps.second)}});
}

INSTANTIATE_TEST_SUITE_P(
    All,
    FramerateAdaptationTest,
    Combine(Values("AV1", "VP9", "VP8", "H264", "H265"),
#if defined(WEBRTC_ANDROID)
            Values("builtin", "mediacodec"),
#else
            Values("builtin"),
#endif
            Values(kRawVideos.at("FourPeople_1280x720_30")),
            Values(std::pair(30, 15), std::pair(15, 30))),
    FramerateAdaptationTest::TestParamsToString);

TEST(VideoCodecTest, DISABLED_EncodeDecode) {
  ScopedFieldTrials field_trials(absl::GetFlag(FLAGS_field_trials));
  const Environment env =
      CreateEnvironment(std::make_unique<ExplicitKeyValueConfig>(
          absl::GetFlag(FLAGS_field_trials)));

  std::vector<std::string> bitrate_str = absl::GetFlag(FLAGS_bitrate_kbps);
  std::vector<int> bitrate_kbps;
  std::transform(bitrate_str.begin(), bitrate_str.end(),
                 std::back_inserter(bitrate_kbps),
                 [](const std::string& str) { return std::stoi(str); });

  std::map<uint32_t, EncodingSettings> frames_settings =
      VideoCodecTester::CreateEncodingSettings(
          CodecNameToCodecType(absl::GetFlag(FLAGS_encoder)),
          absl::GetFlag(FLAGS_scalability_mode), absl::GetFlag(FLAGS_width),
          absl::GetFlag(FLAGS_height), {bitrate_kbps},
          absl::GetFlag(FLAGS_framerate_fps), absl::GetFlag(FLAGS_num_frames));

  // TODO(webrtc:14852): Pass encoder and decoder names directly, and update
  // logged test name (implies lossing history in the chromeperf dashboard).
  // Sync with changes in Stream::LogMetrics (see TODOs there).
  std::unique_ptr<VideoCodecStats> stats = RunEncodeDecodeTest(
      env, CodecNameToCodecImpl(absl::GetFlag(FLAGS_encoder)),
      CodecNameToCodecImpl(absl::GetFlag(FLAGS_decoder)),
      kRawVideos.at(absl::GetFlag(FLAGS_video_name)), frames_settings);
  ASSERT_NE(nullptr, stats);

  // Log unsliced metrics.
  VideoCodecStats::Stream stream = stats->Aggregate(Filter{});
  stream.LogMetrics(GetGlobalMetricsLogger(), TestName(), /*prefix=*/"",
                    /*metadata=*/{});

  // Log metrics sliced on spatial and temporal layer.
  ScalabilityMode scalability_mode =
      *ScalabilityModeFromString(absl::GetFlag(FLAGS_scalability_mode));
  int num_spatial_layers = ScalabilityModeToNumSpatialLayers(scalability_mode);
  int num_temporal_layers =
      ScalabilityModeToNumTemporalLayers(scalability_mode);
  for (int sidx = 0; sidx < num_spatial_layers; ++sidx) {
    for (int tidx = 0; tidx < num_temporal_layers; ++tidx) {
      std::string metric_name_prefix =
          (rtc::StringBuilder() << "s" << sidx << "t" << tidx << "_").str();
      stream = stats->Aggregate(
          {.layer_id = {{.spatial_idx = sidx, .temporal_idx = tidx}}});
      stream.LogMetrics(GetGlobalMetricsLogger(), TestName(),
                        metric_name_prefix,
                        /*metadata=*/{});
    }
  }

  if (absl::GetFlag(FLAGS_write_csv)) {
    stats->LogMetrics(
        (rtc::StringBuilder() << TestOutputPath() << ".csv").str(),
        stats->Slice(Filter{}, /*merge=*/false), /*metadata=*/
        {{"test_name", TestName()}});
  }
}

}  // namespace test

}  // namespace webrtc
