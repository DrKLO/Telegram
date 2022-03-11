/*
 *  Copyright (c) 2018 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include <string>

#include "api/test/video/function_video_encoder_factory.h"
#include "media/engine/internal_encoder_factory.h"
#include "modules/video_coding/codecs/h264/include/h264.h"
#include "modules/video_coding/codecs/vp8/include/vp8.h"
#include "modules/video_coding/codecs/vp9/include/vp9.h"
#include "rtc_base/experiments/encoder_info_settings.h"
#include "test/call_test.h"
#include "test/field_trial.h"
#include "test/frame_generator_capturer.h"

namespace webrtc {
namespace {
constexpr int kInitialWidth = 1280;
constexpr int kInitialHeight = 720;
constexpr int kLowStartBps = 100000;
constexpr int kHighStartBps = 1000000;
constexpr int kDefaultVgaMinStartBps = 500000;  // From video_stream_encoder.cc
constexpr int kTimeoutMs = 10000;  // Some tests are expected to time out.

void SetEncoderSpecific(VideoEncoderConfig* encoder_config,
                        VideoCodecType type,
                        bool automatic_resize,
                        size_t num_spatial_layers) {
  if (type == kVideoCodecVP8) {
    VideoCodecVP8 vp8 = VideoEncoder::GetDefaultVp8Settings();
    vp8.automaticResizeOn = automatic_resize;
    encoder_config->encoder_specific_settings =
        rtc::make_ref_counted<VideoEncoderConfig::Vp8EncoderSpecificSettings>(
            vp8);
  } else if (type == kVideoCodecVP9) {
    VideoCodecVP9 vp9 = VideoEncoder::GetDefaultVp9Settings();
    vp9.automaticResizeOn = automatic_resize;
    vp9.numberOfSpatialLayers = num_spatial_layers;
    encoder_config->encoder_specific_settings =
        rtc::make_ref_counted<VideoEncoderConfig::Vp9EncoderSpecificSettings>(
            vp9);
  }
}
}  // namespace

class QualityScalingTest : public test::CallTest {
 protected:
  const std::string kPrefix = "WebRTC-Video-QualityScaling/Enabled-";
  const std::string kEnd = ",0,0,0.9995,0.9999,1/";
  const absl::optional<VideoEncoder::ResolutionBitrateLimits>
      kSinglecastLimits720pVp8 =
          EncoderInfoSettings::GetDefaultSinglecastBitrateLimitsForResolution(
              kVideoCodecVP8,
              1280 * 720);
  const absl::optional<VideoEncoder::ResolutionBitrateLimits>
      kSinglecastLimits360pVp9 =
          EncoderInfoSettings::GetDefaultSinglecastBitrateLimitsForResolution(
              kVideoCodecVP9,
              640 * 360);
};

class ScalingObserver : public test::SendTest {
 protected:
  ScalingObserver(const std::string& payload_name,
                  const std::vector<bool>& streams_active,
                  int start_bps,
                  bool automatic_resize,
                  bool expect_scaling)
      : SendTest(expect_scaling ? kTimeoutMs * 4 : kTimeoutMs),
        encoder_factory_(
            [](const SdpVideoFormat& format) -> std::unique_ptr<VideoEncoder> {
              if (format.name == "VP8")
                return VP8Encoder::Create();
              if (format.name == "VP9")
                return VP9Encoder::Create();
              if (format.name == "H264")
                return H264Encoder::Create(cricket::VideoCodec("H264"));
              RTC_DCHECK_NOTREACHED() << format.name;
              return nullptr;
            }),
        payload_name_(payload_name),
        streams_active_(streams_active),
        start_bps_(start_bps),
        automatic_resize_(automatic_resize),
        expect_scaling_(expect_scaling) {}

  DegradationPreference degradation_preference_ =
      DegradationPreference::MAINTAIN_FRAMERATE;

 private:
  void ModifySenderBitrateConfig(BitrateConstraints* bitrate_config) override {
    bitrate_config->start_bitrate_bps = start_bps_;
  }

  void ModifyVideoDegradationPreference(
      DegradationPreference* degradation_preference) override {
    *degradation_preference = degradation_preference_;
  }

  size_t GetNumVideoStreams() const override {
    return (payload_name_ == "VP9") ? 1 : streams_active_.size();
  }

  void ModifyVideoConfigs(
      VideoSendStream::Config* send_config,
      std::vector<VideoReceiveStream::Config>* receive_configs,
      VideoEncoderConfig* encoder_config) override {
    send_config->encoder_settings.encoder_factory = &encoder_factory_;
    send_config->rtp.payload_name = payload_name_;
    send_config->rtp.payload_type = test::CallTest::kVideoSendPayloadType;
    encoder_config->video_format.name = payload_name_;
    const VideoCodecType codec_type = PayloadStringToCodecType(payload_name_);
    encoder_config->codec_type = codec_type;
    encoder_config->max_bitrate_bps =
        std::max(start_bps_, encoder_config->max_bitrate_bps);
    if (payload_name_ == "VP9") {
      // Simulcast layers indicates which spatial layers are active.
      encoder_config->simulcast_layers.resize(streams_active_.size());
      encoder_config->simulcast_layers[0].max_bitrate_bps =
          encoder_config->max_bitrate_bps;
    }
    double scale_factor = 1.0;
    for (int i = streams_active_.size() - 1; i >= 0; --i) {
      VideoStream& stream = encoder_config->simulcast_layers[i];
      stream.active = streams_active_[i];
      stream.scale_resolution_down_by = scale_factor;
      scale_factor *= (payload_name_ == "VP9") ? 1.0 : 2.0;
    }
    SetEncoderSpecific(encoder_config, codec_type, automatic_resize_,
                       streams_active_.size());
  }

  void PerformTest() override { EXPECT_EQ(expect_scaling_, Wait()); }

  test::FunctionVideoEncoderFactory encoder_factory_;
  const std::string payload_name_;
  const std::vector<bool> streams_active_;
  const int start_bps_;
  const bool automatic_resize_;
  const bool expect_scaling_;
};

class DownscalingObserver
    : public ScalingObserver,
      public test::FrameGeneratorCapturer::SinkWantsObserver {
 public:
  DownscalingObserver(const std::string& payload_name,
                      const std::vector<bool>& streams_active,
                      int start_bps,
                      bool automatic_resize,
                      bool expect_downscale)
      : ScalingObserver(payload_name,
                        streams_active,
                        start_bps,
                        automatic_resize,
                        expect_downscale) {}

 private:
  void OnFrameGeneratorCapturerCreated(
      test::FrameGeneratorCapturer* frame_generator_capturer) override {
    frame_generator_capturer->SetSinkWantsObserver(this);
    frame_generator_capturer->ChangeResolution(kInitialWidth, kInitialHeight);
  }

  void OnSinkWantsChanged(rtc::VideoSinkInterface<VideoFrame>* sink,
                          const rtc::VideoSinkWants& wants) override {
    if (wants.max_pixel_count < kInitialWidth * kInitialHeight)
      observation_complete_.Set();
  }
};

class UpscalingObserver
    : public ScalingObserver,
      public test::FrameGeneratorCapturer::SinkWantsObserver {
 public:
  UpscalingObserver(const std::string& payload_name,
                    const std::vector<bool>& streams_active,
                    int start_bps,
                    bool automatic_resize,
                    bool expect_upscale)
      : ScalingObserver(payload_name,
                        streams_active,
                        start_bps,
                        automatic_resize,
                        expect_upscale) {}

  void SetDegradationPreference(DegradationPreference preference) {
    degradation_preference_ = preference;
  }

 private:
  void OnFrameGeneratorCapturerCreated(
      test::FrameGeneratorCapturer* frame_generator_capturer) override {
    frame_generator_capturer->SetSinkWantsObserver(this);
    frame_generator_capturer->ChangeResolution(kInitialWidth, kInitialHeight);
  }

  void OnSinkWantsChanged(rtc::VideoSinkInterface<VideoFrame>* sink,
                          const rtc::VideoSinkWants& wants) override {
    if (wants.max_pixel_count > last_wants_.max_pixel_count) {
      if (wants.max_pixel_count == std::numeric_limits<int>::max())
        observation_complete_.Set();
    }
    last_wants_ = wants;
  }

  rtc::VideoSinkWants last_wants_;
};

TEST_F(QualityScalingTest, AdaptsDownForHighQp_Vp8) {
  // qp_low:1, qp_high:1 -> kHighQp
  test::ScopedFieldTrials field_trials(kPrefix + "1,1,0,0,0,0" + kEnd);

  DownscalingObserver test("VP8", /*streams_active=*/{true}, kHighStartBps,
                           /*automatic_resize=*/true,
                           /*expect_downscale=*/true);
  RunBaseTest(&test);
}

TEST_F(QualityScalingTest, NoAdaptDownForHighQpIfScalingOff_Vp8) {
  // qp_low:1, qp_high:1 -> kHighQp
  test::ScopedFieldTrials field_trials(kPrefix + "1,1,0,0,0,0" + kEnd);

  DownscalingObserver test("VP8", /*streams_active=*/{true}, kHighStartBps,
                           /*automatic_resize=*/false,
                           /*expect_downscale=*/false);
  RunBaseTest(&test);
}

TEST_F(QualityScalingTest, NoAdaptDownForNormalQp_Vp8) {
  // qp_low:1, qp_high:127 -> kNormalQp
  test::ScopedFieldTrials field_trials(kPrefix + "1,127,0,0,0,0" + kEnd);

  DownscalingObserver test("VP8", /*streams_active=*/{true}, kHighStartBps,
                           /*automatic_resize=*/true,
                           /*expect_downscale=*/false);
  RunBaseTest(&test);
}

TEST_F(QualityScalingTest, AdaptsDownForLowStartBitrate_Vp8) {
  // qp_low:1, qp_high:127 -> kNormalQp
  test::ScopedFieldTrials field_trials(kPrefix + "1,127,0,0,0,0" + kEnd);

  DownscalingObserver test("VP8", /*streams_active=*/{true}, kLowStartBps,
                           /*automatic_resize=*/true,
                           /*expect_downscale=*/true);
  RunBaseTest(&test);
}

TEST_F(QualityScalingTest, AdaptsDownForLowStartBitrateAndThenUp) {
  // qp_low:127, qp_high:127 -> kLowQp
  test::ScopedFieldTrials field_trials(
      kPrefix + "127,127,0,0,0,0" + kEnd +
      "WebRTC-Video-BalancedDegradationSettings/"
      "pixels:230400|921600,fps:20|30,kbps:300|500/");  // should not affect

  UpscalingObserver test("VP8", /*streams_active=*/{true},
                         kDefaultVgaMinStartBps - 1,
                         /*automatic_resize=*/true, /*expect_upscale=*/true);
  RunBaseTest(&test);
}

TEST_F(QualityScalingTest, AdaptsDownAndThenUpWithBalanced) {
  // qp_low:127, qp_high:127 -> kLowQp
  test::ScopedFieldTrials field_trials(
      kPrefix + "127,127,0,0,0,0" + kEnd +
      "WebRTC-Video-BalancedDegradationSettings/"
      "pixels:230400|921600,fps:20|30,kbps:300|499/");

  UpscalingObserver test("VP8", /*streams_active=*/{true},
                         kDefaultVgaMinStartBps - 1,
                         /*automatic_resize=*/true, /*expect_upscale=*/true);
  test.SetDegradationPreference(DegradationPreference::BALANCED);
  RunBaseTest(&test);
}

TEST_F(QualityScalingTest, AdaptsDownButNotUpWithBalancedIfBitrateNotEnough) {
  // qp_low:127, qp_high:127 -> kLowQp
  test::ScopedFieldTrials field_trials(
      kPrefix + "127,127,0,0,0,0" + kEnd +
      "WebRTC-Video-BalancedDegradationSettings/"
      "pixels:230400|921600,fps:20|30,kbps:300|500/");

  UpscalingObserver test("VP8", /*streams_active=*/{true},
                         kDefaultVgaMinStartBps - 1,
                         /*automatic_resize=*/true, /*expect_upscale=*/false);
  test.SetDegradationPreference(DegradationPreference::BALANCED);
  RunBaseTest(&test);
}

TEST_F(QualityScalingTest, NoAdaptDownForLowStartBitrate_Simulcast) {
  // qp_low:1, qp_high:127 -> kNormalQp
  test::ScopedFieldTrials field_trials(kPrefix + "1,127,0,0,0,0" + kEnd);

  DownscalingObserver test("VP8", /*streams_active=*/{true, true}, kLowStartBps,
                           /*automatic_resize=*/false,
                           /*expect_downscale=*/false);
  RunBaseTest(&test);
}

TEST_F(QualityScalingTest, AdaptsDownForHighQp_HighestStreamActive_Vp8) {
  // qp_low:1, qp_high:1 -> kHighQp
  test::ScopedFieldTrials field_trials(kPrefix + "1,1,0,0,0,0" + kEnd);

  DownscalingObserver test("VP8", /*streams_active=*/{false, false, true},
                           kHighStartBps,
                           /*automatic_resize=*/true,
                           /*expect_downscale=*/true);
  RunBaseTest(&test);
}

TEST_F(QualityScalingTest,
       AdaptsDownForLowStartBitrate_HighestStreamActive_Vp8) {
  // qp_low:1, qp_high:127 -> kNormalQp
  test::ScopedFieldTrials field_trials(kPrefix + "1,127,0,0,0,0" + kEnd);

  DownscalingObserver test("VP8", /*streams_active=*/{false, false, true},
                           kSinglecastLimits720pVp8->min_start_bitrate_bps - 1,
                           /*automatic_resize=*/true,
                           /*expect_downscale=*/true);
  RunBaseTest(&test);
}

TEST_F(QualityScalingTest, AdaptsDownButNotUpWithMinStartBitrateLimit) {
  // qp_low:127, qp_high:127 -> kLowQp
  test::ScopedFieldTrials field_trials(kPrefix + "127,127,0,0,0,0" + kEnd);

  UpscalingObserver test("VP8", /*streams_active=*/{false, true},
                         kSinglecastLimits720pVp8->min_start_bitrate_bps - 1,
                         /*automatic_resize=*/true, /*expect_upscale=*/false);
  RunBaseTest(&test);
}

TEST_F(QualityScalingTest, NoAdaptDownForLowStartBitrateIfBitrateEnough_Vp8) {
  // qp_low:1, qp_high:127 -> kNormalQp
  test::ScopedFieldTrials field_trials(kPrefix + "1,127,0,0,0,0" + kEnd);

  DownscalingObserver test("VP8", /*streams_active=*/{false, false, true},
                           kSinglecastLimits720pVp8->min_start_bitrate_bps,
                           /*automatic_resize=*/true,
                           /*expect_downscale=*/false);
  RunBaseTest(&test);
}

TEST_F(QualityScalingTest,
       NoAdaptDownForLowStartBitrateIfDefaultLimitsDisabled_Vp8) {
  // qp_low:1, qp_high:127 -> kNormalQp
  test::ScopedFieldTrials field_trials(
      kPrefix + "1,127,0,0,0,0" + kEnd +
      "WebRTC-DefaultBitrateLimitsKillSwitch/Enabled/");

  DownscalingObserver test("VP8", /*streams_active=*/{false, false, true},
                           kSinglecastLimits720pVp8->min_start_bitrate_bps - 1,
                           /*automatic_resize=*/true,
                           /*expect_downscale=*/false);
  RunBaseTest(&test);
}

TEST_F(QualityScalingTest,
       NoAdaptDownForLowStartBitrate_OneStreamSinglecastLimitsNotUsed_Vp8) {
  // qp_low:1, qp_high:127 -> kNormalQp
  test::ScopedFieldTrials field_trials(kPrefix + "1,127,0,0,0,0" + kEnd);

  DownscalingObserver test("VP8", /*streams_active=*/{true},
                           kSinglecastLimits720pVp8->min_start_bitrate_bps - 1,
                           /*automatic_resize=*/true,
                           /*expect_downscale=*/false);
  RunBaseTest(&test);
}

TEST_F(QualityScalingTest, NoAdaptDownForHighQp_LowestStreamActive_Vp8) {
  // qp_low:1, qp_high:1 -> kHighQp
  test::ScopedFieldTrials field_trials(kPrefix + "1,1,0,0,0,0" + kEnd);

  DownscalingObserver test("VP8", /*streams_active=*/{true, false, false},
                           kHighStartBps,
                           /*automatic_resize=*/true,
                           /*expect_downscale=*/false);
  RunBaseTest(&test);
}

TEST_F(QualityScalingTest,
       NoAdaptDownForLowStartBitrate_LowestStreamActive_Vp8) {
  // qp_low:1, qp_high:127 -> kNormalQp
  test::ScopedFieldTrials field_trials(kPrefix + "1,127,0,0,0,0" + kEnd);

  DownscalingObserver test("VP8", /*streams_active=*/{true, false, false},
                           kLowStartBps,
                           /*automatic_resize=*/true,
                           /*expect_downscale=*/false);
  RunBaseTest(&test);
}

TEST_F(QualityScalingTest, NoAdaptDownForLowStartBitrateIfScalingOff_Vp8) {
  // qp_low:1, qp_high:127 -> kNormalQp
  test::ScopedFieldTrials field_trials(kPrefix + "1,127,0,0,0,0" + kEnd);

  DownscalingObserver test("VP8", /*streams_active=*/{true}, kLowStartBps,
                           /*automatic_resize=*/false,
                           /*expect_downscale=*/false);
  RunBaseTest(&test);
}

TEST_F(QualityScalingTest, AdaptsDownForHighQp_Vp9) {
  // qp_low:1, qp_high:1 -> kHighQp
  test::ScopedFieldTrials field_trials(kPrefix + "0,0,1,1,0,0" + kEnd +
                                       "WebRTC-VP9QualityScaler/Enabled/");

  DownscalingObserver test("VP9", /*streams_active=*/{true}, kHighStartBps,
                           /*automatic_resize=*/true,
                           /*expect_downscale=*/true);
  RunBaseTest(&test);
}

TEST_F(QualityScalingTest, NoAdaptDownForHighQpIfScalingOff_Vp9) {
  // qp_low:1, qp_high:1 -> kHighQp
  test::ScopedFieldTrials field_trials(kPrefix + "0,0,1,1,0,0" + kEnd +
                                       "WebRTC-VP9QualityScaler/Disabled/");

  DownscalingObserver test("VP9", /*streams_active=*/{true}, kHighStartBps,
                           /*automatic_resize=*/true,
                           /*expect_downscale=*/false);
  RunBaseTest(&test);
}

TEST_F(QualityScalingTest, AdaptsDownForLowStartBitrate_Vp9) {
  // qp_low:1, qp_high:255 -> kNormalQp
  test::ScopedFieldTrials field_trials(kPrefix + "0,0,1,255,0,0" + kEnd +
                                       "WebRTC-VP9QualityScaler/Enabled/");

  DownscalingObserver test("VP9", /*streams_active=*/{true}, kLowStartBps,
                           /*automatic_resize=*/true,
                           /*expect_downscale=*/true);
  RunBaseTest(&test);
}

TEST_F(QualityScalingTest, NoAdaptDownForHighQp_LowestStreamActive_Vp9) {
  // qp_low:1, qp_high:1 -> kHighQp
  test::ScopedFieldTrials field_trials(kPrefix + "0,0,1,1,0,0" + kEnd +
                                       "WebRTC-VP9QualityScaler/Enabled/");

  DownscalingObserver test("VP9", /*streams_active=*/{true, false, false},
                           kHighStartBps,
                           /*automatic_resize=*/true,
                           /*expect_downscale=*/false);
  RunBaseTest(&test);
}

TEST_F(QualityScalingTest,
       NoAdaptDownForLowStartBitrate_LowestStreamActive_Vp9) {
  // qp_low:1, qp_high:255 -> kNormalQp
  test::ScopedFieldTrials field_trials(kPrefix + "0,0,1,255,0,0" + kEnd +
                                       "WebRTC-VP9QualityScaler/Enabled/");

  DownscalingObserver test("VP9", /*streams_active=*/{true, false, false},
                           kLowStartBps,
                           /*automatic_resize=*/true,
                           /*expect_downscale=*/false);
  RunBaseTest(&test);
}

TEST_F(QualityScalingTest, AdaptsDownForHighQp_MiddleStreamActive_Vp9) {
  // qp_low:1, qp_high:1 -> kHighQp
  test::ScopedFieldTrials field_trials(kPrefix + "0,0,1,1,0,0" + kEnd +
                                       "WebRTC-VP9QualityScaler/Enabled/");

  DownscalingObserver test("VP9", /*streams_active=*/{false, true, false},
                           kHighStartBps,
                           /*automatic_resize=*/true,
                           /*expect_downscale=*/true);
  RunBaseTest(&test);
}

TEST_F(QualityScalingTest,
       AdaptsDownForLowStartBitrate_MiddleStreamActive_Vp9) {
  // qp_low:1, qp_high:255 -> kNormalQp
  test::ScopedFieldTrials field_trials(kPrefix + "0,0,1,255,0,0" + kEnd +
                                       "WebRTC-VP9QualityScaler/Enabled/");

  DownscalingObserver test("VP9", /*streams_active=*/{false, true, false},
                           kSinglecastLimits360pVp9->min_start_bitrate_bps - 1,
                           /*automatic_resize=*/true,
                           /*expect_downscale=*/true);
  RunBaseTest(&test);
}

TEST_F(QualityScalingTest, NoAdaptDownForLowStartBitrateIfBitrateEnough_Vp9) {
  // qp_low:1, qp_high:255 -> kNormalQp
  test::ScopedFieldTrials field_trials(kPrefix + "0,0,1,255,0,0" + kEnd +
                                       "WebRTC-VP9QualityScaler/Enabled/");

  DownscalingObserver test("VP9", /*streams_active=*/{false, true, false},
                           kSinglecastLimits360pVp9->min_start_bitrate_bps,
                           /*automatic_resize=*/true,
                           /*expect_downscale=*/false);
  RunBaseTest(&test);
}

#if defined(WEBRTC_USE_H264)
TEST_F(QualityScalingTest, AdaptsDownForHighQp_H264) {
  // qp_low:1, qp_high:1 -> kHighQp
  test::ScopedFieldTrials field_trials(kPrefix + "0,0,0,0,1,1" + kEnd);

  DownscalingObserver test("H264", /*streams_active=*/{true}, kHighStartBps,
                           /*automatic_resize=*/true,
                           /*expect_downscale=*/true);
  RunBaseTest(&test);
}

TEST_F(QualityScalingTest, AdaptsDownForLowStartBitrate_H264) {
  // qp_low:1, qp_high:51 -> kNormalQp
  test::ScopedFieldTrials field_trials(kPrefix + "0,0,0,0,1,51" + kEnd);

  DownscalingObserver test("H264", /*streams_active=*/{true}, kLowStartBps,
                           /*automatic_resize=*/true,
                           /*expect_downscale=*/true);
  RunBaseTest(&test);
}
#endif  // defined(WEBRTC_USE_H264)

}  // namespace webrtc
