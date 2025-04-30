/*
 *  Copyright (c) 2012 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include <stdio.h>

#include <algorithm>
#include <memory>

#include "api/environment/environment_factory.h"
#include "api/test/create_frame_generator.h"
#include "api/test/frame_generator_interface.h"
#include "api/test/mock_video_decoder.h"
#include "api/test/mock_video_encoder.h"
#include "api/video_codecs/video_encoder.h"
#include "api/video_codecs/vp8_temporal_layers.h"
#include "common_video/libyuv/include/webrtc_libyuv.h"
#include "common_video/test/utilities.h"
#include "modules/video_coding/codecs/interface/mock_libvpx_interface.h"
#include "modules/video_coding/codecs/test/video_codec_unittest.h"
#include "modules/video_coding/codecs/vp8/include/vp8.h"
#include "modules/video_coding/codecs/vp8/libvpx_vp8_encoder.h"
#include "modules/video_coding/utility/vp8_header_parser.h"
#include "rtc_base/time_utils.h"
#include "test/field_trial.h"
#include "test/mappable_native_buffer.h"
#include "test/video_codec_settings.h"

namespace webrtc {

using ::testing::_;
using ::testing::AllOf;
using ::testing::ElementsAre;
using ::testing::ElementsAreArray;
using ::testing::Field;
using ::testing::Invoke;
using ::testing::NiceMock;
using ::testing::Return;
using EncoderInfo = webrtc::VideoEncoder::EncoderInfo;
using FramerateFractions =
    absl::InlinedVector<uint8_t, webrtc::kMaxTemporalStreams>;

namespace {
constexpr uint32_t kLegacyScreenshareTl0BitrateKbps = 200;
constexpr uint32_t kLegacyScreenshareTl1BitrateKbps = 1000;
constexpr uint32_t kInitialTimestampRtp = 123;
constexpr int64_t kTestNtpTimeMs = 456;
constexpr int64_t kInitialTimestampMs = 789;
constexpr int kNumCores = 1;
constexpr size_t kMaxPayloadSize = 1440;
constexpr int kWidth = 172;
constexpr int kHeight = 144;
constexpr float kFramerateFps = 30;

const VideoEncoder::Capabilities kCapabilities(false);
const VideoEncoder::Settings kSettings(kCapabilities,
                                       kNumCores,
                                       kMaxPayloadSize);
}  // namespace

class TestVp8Impl : public VideoCodecUnitTest {
 protected:
  std::unique_ptr<VideoEncoder> CreateEncoder() override {
    return VP8Encoder::Create();
  }

  std::unique_ptr<VideoDecoder> CreateDecoder() override {
    return CreateVp8Decoder(CreateEnvironment());
  }

  void ModifyCodecSettings(VideoCodec* codec_settings) override {
    webrtc::test::CodecSettings(kVideoCodecVP8, codec_settings);
    codec_settings->width = kWidth;
    codec_settings->height = kHeight;
    codec_settings->SetVideoEncoderComplexity(
        VideoCodecComplexity::kComplexityNormal);
  }

  void EncodeAndWaitForFrame(const VideoFrame& input_frame,
                             EncodedImage* encoded_frame,
                             CodecSpecificInfo* codec_specific_info,
                             bool keyframe = false) {
    std::vector<VideoFrameType> frame_types;
    if (keyframe) {
      frame_types.emplace_back(VideoFrameType::kVideoFrameKey);
    } else {
      frame_types.emplace_back(VideoFrameType::kVideoFrameDelta);
    }
    EXPECT_EQ(WEBRTC_VIDEO_CODEC_OK,
              encoder_->Encode(input_frame, &frame_types));
    ASSERT_TRUE(WaitForEncodedFrame(encoded_frame, codec_specific_info));
    VerifyQpParser(*encoded_frame);
    EXPECT_EQ(kVideoCodecVP8, codec_specific_info->codecType);
    EXPECT_EQ(0, encoded_frame->SimulcastIndex());
  }

  void EncodeAndExpectFrameWith(const VideoFrame& input_frame,
                                uint8_t temporal_idx,
                                bool keyframe = false) {
    EncodedImage encoded_frame;
    CodecSpecificInfo codec_specific_info;
    EncodeAndWaitForFrame(input_frame, &encoded_frame, &codec_specific_info,
                          keyframe);
    EXPECT_EQ(temporal_idx, codec_specific_info.codecSpecific.VP8.temporalIdx);
  }

  void VerifyQpParser(const EncodedImage& encoded_frame) const {
    int qp;
    EXPECT_GT(encoded_frame.size(), 0u);
    ASSERT_TRUE(vp8::GetQp(encoded_frame.data(), encoded_frame.size(), &qp));
    EXPECT_EQ(encoded_frame.qp_, qp) << "Encoder QP != parsed bitstream QP.";
  }
};

TEST_F(TestVp8Impl, ErrorResilienceDisabledForNoTemporalLayers) {
  codec_settings_.simulcastStream[0].numberOfTemporalLayers = 1;

  auto* const vpx = new NiceMock<MockLibvpxInterface>();
  LibvpxVp8Encoder encoder((std::unique_ptr<LibvpxInterface>(vpx)),
                           VP8Encoder::Settings());
  EXPECT_CALL(*vpx,
              codec_enc_init(
                  _, _, Field(&vpx_codec_enc_cfg_t::g_error_resilient, 0), _));
  EXPECT_EQ(WEBRTC_VIDEO_CODEC_OK,
            encoder.InitEncode(&codec_settings_, kSettings));
}

TEST_F(TestVp8Impl, DefaultErrorResilienceEnabledForTemporalLayers) {
  codec_settings_.simulcastStream[0].numberOfTemporalLayers = 2;
  codec_settings_.VP8()->numberOfTemporalLayers = 2;

  auto* const vpx = new NiceMock<MockLibvpxInterface>();
  LibvpxVp8Encoder encoder((std::unique_ptr<LibvpxInterface>(vpx)),
                           VP8Encoder::Settings());
  EXPECT_CALL(*vpx,
              codec_enc_init(_, _,
                             Field(&vpx_codec_enc_cfg_t::g_error_resilient,
                                   VPX_ERROR_RESILIENT_DEFAULT),
                             _));
  EXPECT_EQ(WEBRTC_VIDEO_CODEC_OK,
            encoder.InitEncode(&codec_settings_, kSettings));
}

TEST_F(TestVp8Impl,
       PartitionErrorResilienceEnabledForTemporalLayersWithFieldTrial) {
  test::ScopedFieldTrials field_trials(
      "WebRTC-VP8-ForcePartitionResilience/Enabled/");
  codec_settings_.simulcastStream[0].numberOfTemporalLayers = 2;
  codec_settings_.VP8()->numberOfTemporalLayers = 2;

  auto* const vpx = new NiceMock<MockLibvpxInterface>();
  LibvpxVp8Encoder encoder((std::unique_ptr<LibvpxInterface>(vpx)),
                           VP8Encoder::Settings());
  EXPECT_CALL(*vpx,
              codec_enc_init(_, _,
                             Field(&vpx_codec_enc_cfg_t::g_error_resilient,
                                   VPX_ERROR_RESILIENT_PARTITIONS),
                             _));
  EXPECT_EQ(WEBRTC_VIDEO_CODEC_OK,
            encoder.InitEncode(&codec_settings_, kSettings));
}

TEST_F(TestVp8Impl, SetRates) {
  codec_settings_.SetFrameDropEnabled(true);
  auto* const vpx = new NiceMock<MockLibvpxInterface>();
  LibvpxVp8Encoder encoder((std::unique_ptr<LibvpxInterface>(vpx)),
                           VP8Encoder::Settings());
  EXPECT_EQ(WEBRTC_VIDEO_CODEC_OK,
            encoder.InitEncode(&codec_settings_,
                               VideoEncoder::Settings(kCapabilities, 1, 1000)));

  const uint32_t kBitrateBps = 300000;
  VideoBitrateAllocation bitrate_allocation;
  bitrate_allocation.SetBitrate(0, 0, kBitrateBps);
  EXPECT_CALL(
      *vpx,
      codec_enc_config_set(
          _, AllOf(Field(&vpx_codec_enc_cfg_t::rc_target_bitrate,
                         kBitrateBps / 1000),
                   Field(&vpx_codec_enc_cfg_t::rc_undershoot_pct, 100u),
                   Field(&vpx_codec_enc_cfg_t::rc_overshoot_pct, 15u),
                   Field(&vpx_codec_enc_cfg_t::rc_buf_sz, 1000u),
                   Field(&vpx_codec_enc_cfg_t::rc_buf_optimal_sz, 600u),
                   Field(&vpx_codec_enc_cfg_t::rc_dropframe_thresh, 30u))))
      .WillOnce(Return(VPX_CODEC_OK));
  encoder.SetRates(VideoEncoder::RateControlParameters(
      bitrate_allocation, static_cast<double>(codec_settings_.maxFramerate)));
}

TEST_F(TestVp8Impl, EncodeFrameAndRelease) {
  EXPECT_EQ(WEBRTC_VIDEO_CODEC_OK, encoder_->Release());
  EXPECT_EQ(WEBRTC_VIDEO_CODEC_OK,
            encoder_->InitEncode(&codec_settings_, kSettings));

  EncodedImage encoded_frame;
  CodecSpecificInfo codec_specific_info;
  EncodeAndWaitForFrame(NextInputFrame(), &encoded_frame, &codec_specific_info);

  EXPECT_EQ(WEBRTC_VIDEO_CODEC_OK, encoder_->Release());
  EXPECT_EQ(WEBRTC_VIDEO_CODEC_UNINITIALIZED,
            encoder_->Encode(NextInputFrame(), nullptr));
}

TEST_F(TestVp8Impl, EncodeNv12FrameSimulcast) {
  EXPECT_EQ(WEBRTC_VIDEO_CODEC_OK, encoder_->Release());
  EXPECT_EQ(WEBRTC_VIDEO_CODEC_OK,
            encoder_->InitEncode(&codec_settings_, kSettings));

  EncodedImage encoded_frame;
  CodecSpecificInfo codec_specific_info;
  input_frame_generator_ = test::CreateSquareFrameGenerator(
      kWidth, kHeight, test::FrameGeneratorInterface::OutputType::kNV12,
      absl::nullopt);
  EncodeAndWaitForFrame(NextInputFrame(), &encoded_frame, &codec_specific_info);

  EXPECT_EQ(WEBRTC_VIDEO_CODEC_OK, encoder_->Release());
  EXPECT_EQ(WEBRTC_VIDEO_CODEC_UNINITIALIZED,
            encoder_->Encode(NextInputFrame(), nullptr));
}

TEST_F(TestVp8Impl, EncodeI420FrameAfterNv12Frame) {
  EXPECT_EQ(WEBRTC_VIDEO_CODEC_OK, encoder_->Release());
  EXPECT_EQ(WEBRTC_VIDEO_CODEC_OK,
            encoder_->InitEncode(&codec_settings_, kSettings));

  EncodedImage encoded_frame;
  CodecSpecificInfo codec_specific_info;
  input_frame_generator_ = test::CreateSquareFrameGenerator(
      kWidth, kHeight, test::FrameGeneratorInterface::OutputType::kNV12,
      absl::nullopt);
  EncodeAndWaitForFrame(NextInputFrame(), &encoded_frame, &codec_specific_info);
  input_frame_generator_ = test::CreateSquareFrameGenerator(
      kWidth, kHeight, test::FrameGeneratorInterface::OutputType::kI420,
      absl::nullopt);
  EncodeAndWaitForFrame(NextInputFrame(), &encoded_frame, &codec_specific_info);

  EXPECT_EQ(WEBRTC_VIDEO_CODEC_OK, encoder_->Release());
  EXPECT_EQ(WEBRTC_VIDEO_CODEC_UNINITIALIZED,
            encoder_->Encode(NextInputFrame(), nullptr));
}

TEST_F(TestVp8Impl, Configure) {
  EXPECT_EQ(WEBRTC_VIDEO_CODEC_OK, decoder_->Release());
  EXPECT_TRUE(decoder_->Configure({}));
}

TEST_F(TestVp8Impl, OnEncodedImageReportsInfo) {
  constexpr Timestamp kCaptureTimeIdentifier = Timestamp::Micros(1000);
  VideoFrame input_frame = NextInputFrame();
  input_frame.set_timestamp(kInitialTimestampRtp);
  input_frame.set_timestamp_us(kInitialTimestampMs *
                               rtc::kNumMicrosecsPerMillisec);
  input_frame.set_capture_time_identifier(kCaptureTimeIdentifier);
  EncodedImage encoded_frame;
  CodecSpecificInfo codec_specific_info;
  EncodeAndWaitForFrame(input_frame, &encoded_frame, &codec_specific_info);

  EXPECT_EQ(kInitialTimestampRtp, encoded_frame.RtpTimestamp());
  EXPECT_EQ(kWidth, static_cast<int>(encoded_frame._encodedWidth));
  EXPECT_EQ(kHeight, static_cast<int>(encoded_frame._encodedHeight));
  ASSERT_TRUE(encoded_frame.CaptureTimeIdentifier().has_value());
  EXPECT_EQ(kCaptureTimeIdentifier.us(),
            encoded_frame.CaptureTimeIdentifier()->us());
}

TEST_F(TestVp8Impl,
       EncoderFillsResolutionInCodecAgnosticSectionOfCodecSpecificInfo) {
  EncodedImage encoded_frame;
  CodecSpecificInfo codec_specific_info;
  EncodeAndWaitForFrame(NextInputFrame(), &encoded_frame, &codec_specific_info);

  ASSERT_TRUE(codec_specific_info.template_structure);
  EXPECT_THAT(codec_specific_info.template_structure->resolutions,
              ElementsAre(RenderResolution(kWidth, kHeight)));
}

TEST_F(TestVp8Impl, DecodedQpEqualsEncodedQp) {
  VideoFrame input_frame = NextInputFrame();
  EncodedImage encoded_frame;
  CodecSpecificInfo codec_specific_info;
  EncodeAndWaitForFrame(input_frame, &encoded_frame, &codec_specific_info);

  // First frame should be a key frame.
  encoded_frame._frameType = VideoFrameType::kVideoFrameKey;
  EXPECT_EQ(WEBRTC_VIDEO_CODEC_OK, decoder_->Decode(encoded_frame, -1));
  std::unique_ptr<VideoFrame> decoded_frame;
  absl::optional<uint8_t> decoded_qp;
  ASSERT_TRUE(WaitForDecodedFrame(&decoded_frame, &decoded_qp));
  ASSERT_TRUE(decoded_frame);
  ASSERT_TRUE(decoded_qp);
  EXPECT_GT(I420PSNR(&input_frame, decoded_frame.get()), 36);
  EXPECT_EQ(encoded_frame.qp_, *decoded_qp);
}

TEST_F(TestVp8Impl, ChecksSimulcastSettings) {
  codec_settings_.numberOfSimulcastStreams = 2;
  // Resolutions are not in ascending order, temporal layers do not match.
  codec_settings_.simulcastStream[0] = {.width = kWidth,
                                        .height = kHeight,
                                        .maxFramerate = kFramerateFps,
                                        .numberOfTemporalLayers = 2,
                                        .maxBitrate = 4000,
                                        .targetBitrate = 3000,
                                        .minBitrate = 2000,
                                        .qpMax = 80};
  codec_settings_.simulcastStream[1] = {.width = kWidth / 2,
                                        .height = kHeight / 2,
                                        .maxFramerate = 30,
                                        .numberOfTemporalLayers = 3,
                                        .maxBitrate = 4000,
                                        .targetBitrate = 3000,
                                        .minBitrate = 2000,
                                        .qpMax = 80};
  EXPECT_EQ(WEBRTC_VIDEO_CODEC_ERR_SIMULCAST_PARAMETERS_NOT_SUPPORTED,
            encoder_->InitEncode(&codec_settings_, kSettings));
  codec_settings_.numberOfSimulcastStreams = 3;
  // Resolutions are not in ascending order.
  codec_settings_.simulcastStream[0] = {.width = kWidth / 2,
                                        .height = kHeight / 2,
                                        .maxFramerate = kFramerateFps,
                                        .numberOfTemporalLayers = 1,
                                        .maxBitrate = 4000,
                                        .targetBitrate = 3000,
                                        .minBitrate = 2000,
                                        .qpMax = 80};
  codec_settings_.simulcastStream[1] = {.width = kWidth / 2 - 1,
                                        .height = kHeight / 2 - 1,
                                        .maxFramerate = kFramerateFps,
                                        .numberOfTemporalLayers = 1,
                                        .maxBitrate = 4000,
                                        .targetBitrate = 3000,
                                        .minBitrate = 2000,
                                        .qpMax = 80};
  codec_settings_.simulcastStream[2] = {.width = kWidth,
                                        .height = kHeight,
                                        .maxFramerate = 30,
                                        .numberOfTemporalLayers = 1,
                                        .maxBitrate = 4000,
                                        .targetBitrate = 3000,
                                        .minBitrate = 2000,
                                        .qpMax = 80};
  EXPECT_EQ(WEBRTC_VIDEO_CODEC_ERR_SIMULCAST_PARAMETERS_NOT_SUPPORTED,
            encoder_->InitEncode(&codec_settings_, kSettings));
  // Resolutions are not in ascending order.
  codec_settings_.simulcastStream[0] = {.width = kWidth,
                                        .height = kHeight,
                                        .maxFramerate = kFramerateFps,
                                        .numberOfTemporalLayers = 1,
                                        .maxBitrate = 4000,
                                        .targetBitrate = 3000,
                                        .minBitrate = 2000,
                                        .qpMax = 80};
  codec_settings_.simulcastStream[1] = {.width = kWidth,
                                        .height = kHeight,
                                        .maxFramerate = kFramerateFps,
                                        .numberOfTemporalLayers = 1,
                                        .maxBitrate = 4000,
                                        .targetBitrate = 3000,
                                        .minBitrate = 2000,
                                        .qpMax = 80};
  codec_settings_.simulcastStream[2] = {.width = kWidth - 1,
                                        .height = kHeight - 1,
                                        .maxFramerate = kFramerateFps,
                                        .numberOfTemporalLayers = 1,
                                        .maxBitrate = 4000,
                                        .targetBitrate = 3000,
                                        .minBitrate = 2000,
                                        .qpMax = 80};
  EXPECT_EQ(WEBRTC_VIDEO_CODEC_ERR_SIMULCAST_PARAMETERS_NOT_SUPPORTED,
            encoder_->InitEncode(&codec_settings_, kSettings));
  // Temporal layers do not match.
  codec_settings_.simulcastStream[0] = {.width = kWidth / 4,
                                        .height = kHeight / 4,
                                        .maxFramerate = kFramerateFps,
                                        .numberOfTemporalLayers = 1,
                                        .maxBitrate = 4000,
                                        .targetBitrate = 3000,
                                        .minBitrate = 2000,
                                        .qpMax = 80};
  codec_settings_.simulcastStream[1] = {.width = kWidth / 2,
                                        .height = kHeight / 2,
                                        .maxFramerate = kFramerateFps,
                                        .numberOfTemporalLayers = 2,
                                        .maxBitrate = 4000,
                                        .targetBitrate = 3000,
                                        .minBitrate = 2000,
                                        .qpMax = 80};
  codec_settings_.simulcastStream[2] = {.width = kWidth,
                                        .height = kHeight,
                                        .maxFramerate = kFramerateFps,
                                        .numberOfTemporalLayers = 3,
                                        .maxBitrate = 4000,
                                        .targetBitrate = 3000,
                                        .minBitrate = 2000,
                                        .qpMax = 80};
  EXPECT_EQ(WEBRTC_VIDEO_CODEC_ERR_SIMULCAST_PARAMETERS_NOT_SUPPORTED,
            encoder_->InitEncode(&codec_settings_, kSettings));
  // Resolutions do not match codec config.
  codec_settings_.simulcastStream[0] = {.width = kWidth / 4 + 1,
                                        .height = kHeight / 4 + 1,
                                        .maxFramerate = kFramerateFps,
                                        .numberOfTemporalLayers = 1,
                                        .maxBitrate = 4000,
                                        .targetBitrate = 3000,
                                        .minBitrate = 2000,
                                        .qpMax = 80};
  codec_settings_.simulcastStream[1] = {.width = kWidth / 2 + 2,
                                        .height = kHeight / 2 + 2,
                                        .maxFramerate = kFramerateFps,
                                        .numberOfTemporalLayers = 1,
                                        .maxBitrate = 4000,
                                        .targetBitrate = 3000,
                                        .minBitrate = 2000,
                                        .qpMax = 80};
  codec_settings_.simulcastStream[2] = {.width = kWidth + 4,
                                        .height = kHeight + 4,
                                        .maxFramerate = kFramerateFps,
                                        .numberOfTemporalLayers = 1,
                                        .maxBitrate = 4000,
                                        .targetBitrate = 3000,
                                        .minBitrate = 2000,
                                        .qpMax = 80};
  EXPECT_EQ(WEBRTC_VIDEO_CODEC_ERR_SIMULCAST_PARAMETERS_NOT_SUPPORTED,
            encoder_->InitEncode(&codec_settings_, kSettings));
  // Everything fine: scaling by 2, top resolution matches video, temporal
  // settings are the same for all layers.
  codec_settings_.simulcastStream[0] = {.width = kWidth / 4,
                                        .height = kHeight / 4,
                                        .maxFramerate = kFramerateFps,
                                        .numberOfTemporalLayers = 1,
                                        .maxBitrate = 4000,
                                        .targetBitrate = 3000,
                                        .minBitrate = 2000,
                                        .qpMax = 80};
  codec_settings_.simulcastStream[1] = {.width = kWidth / 2,
                                        .height = kHeight / 2,
                                        .maxFramerate = kFramerateFps,
                                        .numberOfTemporalLayers = 1,
                                        .maxBitrate = 4000,
                                        .targetBitrate = 3000,
                                        .minBitrate = 2000,
                                        .qpMax = 80};
  codec_settings_.simulcastStream[2] = {.width = kWidth,
                                        .height = kHeight,
                                        .maxFramerate = kFramerateFps,
                                        .numberOfTemporalLayers = 1,
                                        .maxBitrate = 4000,
                                        .targetBitrate = 3000,
                                        .minBitrate = 2000,
                                        .qpMax = 80};
  EXPECT_EQ(WEBRTC_VIDEO_CODEC_OK,
            encoder_->InitEncode(&codec_settings_, kSettings));
  // Everything fine: custom scaling, top resolution matches video, temporal
  // settings are the same for all layers.
  codec_settings_.simulcastStream[0] = {.width = kWidth / 4,
                                        .height = kHeight / 4,
                                        .maxFramerate = kFramerateFps,
                                        .numberOfTemporalLayers = 1,
                                        .maxBitrate = 4000,
                                        .targetBitrate = 3000,
                                        .minBitrate = 2000,
                                        .qpMax = 80};
  codec_settings_.simulcastStream[1] = {.width = kWidth,
                                        .height = kHeight,
                                        .maxFramerate = kFramerateFps,
                                        .numberOfTemporalLayers = 1,
                                        .maxBitrate = 4000,
                                        .targetBitrate = 3000,
                                        .minBitrate = 2000,
                                        .qpMax = 80};
  codec_settings_.simulcastStream[2] = {.width = kWidth,
                                        .height = kHeight,
                                        .maxFramerate = kFramerateFps,
                                        .numberOfTemporalLayers = 1,
                                        .maxBitrate = 4000,
                                        .targetBitrate = 3000,
                                        .minBitrate = 2000,
                                        .qpMax = 80};
  EXPECT_EQ(WEBRTC_VIDEO_CODEC_OK,
            encoder_->InitEncode(&codec_settings_, kSettings));
}

#if defined(WEBRTC_ANDROID)
#define MAYBE_AlignedStrideEncodeDecode DISABLED_AlignedStrideEncodeDecode
#else
#define MAYBE_AlignedStrideEncodeDecode AlignedStrideEncodeDecode
#endif
TEST_F(TestVp8Impl, MAYBE_AlignedStrideEncodeDecode) {
  VideoFrame input_frame = NextInputFrame();
  input_frame.set_timestamp(kInitialTimestampRtp);
  input_frame.set_timestamp_us(kInitialTimestampMs *
                               rtc::kNumMicrosecsPerMillisec);
  EncodedImage encoded_frame;
  CodecSpecificInfo codec_specific_info;
  EncodeAndWaitForFrame(input_frame, &encoded_frame, &codec_specific_info);

  // First frame should be a key frame.
  encoded_frame._frameType = VideoFrameType::kVideoFrameKey;
  encoded_frame.ntp_time_ms_ = kTestNtpTimeMs;
  EXPECT_EQ(WEBRTC_VIDEO_CODEC_OK, decoder_->Decode(encoded_frame, -1));

  std::unique_ptr<VideoFrame> decoded_frame;
  absl::optional<uint8_t> decoded_qp;
  ASSERT_TRUE(WaitForDecodedFrame(&decoded_frame, &decoded_qp));
  ASSERT_TRUE(decoded_frame);
  // Compute PSNR on all planes (faster than SSIM).
  EXPECT_GT(I420PSNR(&input_frame, decoded_frame.get()), 36);
  EXPECT_EQ(kInitialTimestampRtp, decoded_frame->timestamp());
}

TEST_F(TestVp8Impl, EncoderWith2TemporalLayers) {
  codec_settings_.VP8()->numberOfTemporalLayers = 2;
  EXPECT_EQ(WEBRTC_VIDEO_CODEC_OK,
            encoder_->InitEncode(&codec_settings_, kSettings));

  // Temporal layer 0.
  EncodedImage encoded_frame;
  CodecSpecificInfo codec_specific_info;
  EncodeAndWaitForFrame(NextInputFrame(), &encoded_frame, &codec_specific_info);

  EXPECT_EQ(0, codec_specific_info.codecSpecific.VP8.temporalIdx);
  // Temporal layer 1.
  EncodeAndExpectFrameWith(NextInputFrame(), 1);
  // Temporal layer 0.
  EncodeAndExpectFrameWith(NextInputFrame(), 0);
  // Temporal layer 1.
  EncodeAndExpectFrameWith(NextInputFrame(), 1);
}

TEST_F(TestVp8Impl, ScalingDisabledIfAutomaticResizeOff) {
  codec_settings_.VP8()->automaticResizeOn = false;
  EXPECT_EQ(WEBRTC_VIDEO_CODEC_OK,
            encoder_->InitEncode(&codec_settings_, kSettings));

  VideoEncoder::ScalingSettings settings =
      encoder_->GetEncoderInfo().scaling_settings;
  EXPECT_FALSE(settings.thresholds.has_value());
}

TEST_F(TestVp8Impl, ScalingEnabledIfAutomaticResizeOn) {
  codec_settings_.SetFrameDropEnabled(true);
  codec_settings_.VP8()->automaticResizeOn = true;
  EXPECT_EQ(WEBRTC_VIDEO_CODEC_OK,
            encoder_->InitEncode(&codec_settings_, kSettings));

  VideoEncoder::ScalingSettings settings =
      encoder_->GetEncoderInfo().scaling_settings;
  EXPECT_TRUE(settings.thresholds.has_value());
  EXPECT_EQ(kDefaultMinPixelsPerFrame, settings.min_pixels_per_frame);
}

TEST_F(TestVp8Impl, DontDropKeyframes) {
  // Set very high resolution to trigger overuse more easily.
  const int kScreenWidth = 1920;
  const int kScreenHeight = 1080;

  codec_settings_.width = kScreenWidth;
  codec_settings_.height = kScreenHeight;

  // Screensharing has the internal frame dropper off, and instead per frame
  // asks ScreenshareLayers to decide if it should be dropped or not.
  codec_settings_.SetFrameDropEnabled(false);
  codec_settings_.mode = VideoCodecMode::kScreensharing;
  // ScreenshareLayers triggers on 2 temporal layers and 1000kbps max bitrate.
  codec_settings_.VP8()->numberOfTemporalLayers = 2;
  codec_settings_.maxBitrate = 1000;

  // Reset the frame generator with large number of squares, leading to lots of
  // details and high probability of overshoot.
  input_frame_generator_ = test::CreateSquareFrameGenerator(
      codec_settings_.width, codec_settings_.height,
      test::FrameGeneratorInterface::OutputType::kI420,
      /* num_squares = */ absl::optional<int>(300));

  EXPECT_EQ(WEBRTC_VIDEO_CODEC_OK,
            encoder_->InitEncode(&codec_settings_, kSettings));

  VideoBitrateAllocation bitrate_allocation;
  // Bitrate only enough for TL0.
  bitrate_allocation.SetBitrate(0, 0, 200000);
  encoder_->SetRates(
      VideoEncoder::RateControlParameters(bitrate_allocation, 5.0));

  EncodedImage encoded_frame;
  CodecSpecificInfo codec_specific_info;
  EncodeAndWaitForFrame(NextInputFrame(), &encoded_frame, &codec_specific_info,
                        true);
  EncodeAndExpectFrameWith(NextInputFrame(), 0, true);
  EncodeAndExpectFrameWith(NextInputFrame(), 0, true);
  EncodeAndExpectFrameWith(NextInputFrame(), 0, true);
}

TEST_F(TestVp8Impl, KeepsTimestampOnReencode) {
  auto* const vpx = new NiceMock<MockLibvpxInterface>();
  LibvpxVp8Encoder encoder((std::unique_ptr<LibvpxInterface>(vpx)),
                           VP8Encoder::Settings());

  // Settings needed to trigger ScreenshareLayers usage, which is required for
  // overshoot-drop-reencode logic.
  codec_settings_.maxBitrate = 1000;
  codec_settings_.mode = VideoCodecMode::kScreensharing;
  codec_settings_.VP8()->numberOfTemporalLayers = 2;
  codec_settings_.legacy_conference_mode = true;

  EXPECT_CALL(*vpx, img_wrap(_, _, _, _, _, _))
      .WillOnce(Invoke([](vpx_image_t* img, vpx_img_fmt_t fmt, unsigned int d_w,
                          unsigned int d_h, unsigned int stride_align,
                          unsigned char* img_data) {
        img->fmt = fmt;
        img->d_w = d_w;
        img->d_h = d_h;
        img->img_data = img_data;
        return img;
      }));
  EXPECT_EQ(WEBRTC_VIDEO_CODEC_OK,
            encoder.InitEncode(&codec_settings_,
                               VideoEncoder::Settings(kCapabilities, 1, 1000)));
  MockEncodedImageCallback callback;
  encoder.RegisterEncodeCompleteCallback(&callback);

  // Simulate overshoot drop, re-encode: encode function will be called twice
  // with the same parameters. codec_get_cx_data() will by default return no
  // image data and be interpreted as drop.
  EXPECT_CALL(*vpx, codec_encode(_, _, /* pts = */ 0, _, _, _))
      .Times(2)
      .WillRepeatedly(Return(vpx_codec_err_t::VPX_CODEC_OK));

  auto delta_frame =
      std::vector<VideoFrameType>{VideoFrameType::kVideoFrameDelta};
  encoder.Encode(NextInputFrame(), &delta_frame);
}

TEST(LibvpxVp8EncoderTest, GetEncoderInfoReturnsStaticInformation) {
  auto* const vpx = new NiceMock<MockLibvpxInterface>();
  LibvpxVp8Encoder encoder((std::unique_ptr<LibvpxInterface>(vpx)),
                           VP8Encoder::Settings());

  const auto info = encoder.GetEncoderInfo();

  EXPECT_FALSE(info.supports_native_handle);
  EXPECT_FALSE(info.is_hardware_accelerated);
  EXPECT_TRUE(info.supports_simulcast);
  EXPECT_EQ(info.implementation_name, "libvpx");
  EXPECT_EQ(info.requested_resolution_alignment, 1u);
  EXPECT_THAT(info.preferred_pixel_formats,
              testing::UnorderedElementsAre(VideoFrameBuffer::Type::kNV12,
                                            VideoFrameBuffer::Type::kI420));
}

TEST(LibvpxVp8EncoderTest, RequestedResolutionAlignmentFromFieldTrial) {
  test::ScopedFieldTrials field_trials(
      "WebRTC-VP8-GetEncoderInfoOverride/"
      "requested_resolution_alignment:10/");

  auto* const vpx = new NiceMock<MockLibvpxInterface>();
  LibvpxVp8Encoder encoder((std::unique_ptr<LibvpxInterface>(vpx)),
                           VP8Encoder::Settings());

  EXPECT_EQ(encoder.GetEncoderInfo().requested_resolution_alignment, 10u);
  EXPECT_FALSE(
      encoder.GetEncoderInfo().apply_alignment_to_all_simulcast_layers);
  EXPECT_TRUE(encoder.GetEncoderInfo().resolution_bitrate_limits.empty());
}

TEST(LibvpxVp8EncoderTest, ResolutionBitrateLimitsFromFieldTrial) {
  test::ScopedFieldTrials field_trials(
      "WebRTC-VP8-GetEncoderInfoOverride/"
      "frame_size_pixels:123|456|789,"
      "min_start_bitrate_bps:11000|22000|33000,"
      "min_bitrate_bps:44000|55000|66000,"
      "max_bitrate_bps:77000|88000|99000/");

  auto* const vpx = new NiceMock<MockLibvpxInterface>();
  LibvpxVp8Encoder encoder((std::unique_ptr<LibvpxInterface>(vpx)),
                           VP8Encoder::Settings());

  EXPECT_THAT(
      encoder.GetEncoderInfo().resolution_bitrate_limits,
      ::testing::ElementsAre(
          VideoEncoder::ResolutionBitrateLimits{123, 11000, 44000, 77000},
          VideoEncoder::ResolutionBitrateLimits{456, 22000, 55000, 88000},
          VideoEncoder::ResolutionBitrateLimits{789, 33000, 66000, 99000}));
}

TEST(LibvpxVp8EncoderTest,
     GetEncoderInfoReturnsEmptyResolutionBitrateLimitsByDefault) {
  auto* const vpx = new NiceMock<MockLibvpxInterface>();
  LibvpxVp8Encoder encoder((std::unique_ptr<LibvpxInterface>(vpx)),
                           VP8Encoder::Settings());

  const auto info = encoder.GetEncoderInfo();

  EXPECT_TRUE(info.resolution_bitrate_limits.empty());
}

TEST(LibvpxVp8EncoderTest,
     GetEncoderInfoReturnsResolutionBitrateLimitsAsConfigured) {
  std::vector<VideoEncoder::ResolutionBitrateLimits> resolution_bitrate_limits =
      {VideoEncoder::ResolutionBitrateLimits(/*frame_size_pixels=*/640 * 360,
                                             /*min_start_bitrate_bps=*/300,
                                             /*min_bitrate_bps=*/100,
                                             /*max_bitrate_bps=*/1000),
       VideoEncoder::ResolutionBitrateLimits(320 * 180, 100, 30, 500)};
  VP8Encoder::Settings settings;
  settings.resolution_bitrate_limits = resolution_bitrate_limits;

  auto* const vpx = new NiceMock<MockLibvpxInterface>();
  LibvpxVp8Encoder encoder((std::unique_ptr<LibvpxInterface>(vpx)),
                           std::move(settings));

  const auto info = encoder.GetEncoderInfo();

  EXPECT_EQ(info.resolution_bitrate_limits, resolution_bitrate_limits);
}

TEST_F(TestVp8Impl, GetEncoderInfoFpsAllocationNoLayers) {
  FramerateFractions expected_fps_allocation[kMaxSpatialLayers] = {
      FramerateFractions(1, EncoderInfo::kMaxFramerateFraction)};

  EXPECT_THAT(encoder_->GetEncoderInfo().fps_allocation,
              ::testing::ElementsAreArray(expected_fps_allocation));
}

TEST_F(TestVp8Impl, GetEncoderInfoFpsAllocationTwoTemporalLayers) {
  EXPECT_EQ(WEBRTC_VIDEO_CODEC_OK, encoder_->Release());
  codec_settings_.numberOfSimulcastStreams = 1;
  codec_settings_.simulcastStream[0].active = true;
  codec_settings_.simulcastStream[0].targetBitrate = 100;
  codec_settings_.simulcastStream[0].maxBitrate = 100;
  codec_settings_.simulcastStream[0].numberOfTemporalLayers = 2;
  EXPECT_EQ(WEBRTC_VIDEO_CODEC_OK,
            encoder_->InitEncode(&codec_settings_, kSettings));

  FramerateFractions expected_fps_allocation[kMaxSpatialLayers];
  expected_fps_allocation[0].push_back(EncoderInfo::kMaxFramerateFraction / 2);
  expected_fps_allocation[0].push_back(EncoderInfo::kMaxFramerateFraction);

  EXPECT_THAT(encoder_->GetEncoderInfo().fps_allocation,
              ::testing::ElementsAreArray(expected_fps_allocation));
}

TEST_F(TestVp8Impl, GetEncoderInfoFpsAllocationThreeTemporalLayers) {
  EXPECT_EQ(WEBRTC_VIDEO_CODEC_OK, encoder_->Release());
  codec_settings_.numberOfSimulcastStreams = 1;
  codec_settings_.simulcastStream[0].active = true;
  codec_settings_.simulcastStream[0].targetBitrate = 100;
  codec_settings_.simulcastStream[0].maxBitrate = 100;
  codec_settings_.simulcastStream[0].numberOfTemporalLayers = 3;
  EXPECT_EQ(WEBRTC_VIDEO_CODEC_OK,
            encoder_->InitEncode(&codec_settings_, kSettings));

  FramerateFractions expected_fps_allocation[kMaxSpatialLayers];
  expected_fps_allocation[0].push_back(EncoderInfo::kMaxFramerateFraction / 4);
  expected_fps_allocation[0].push_back(EncoderInfo::kMaxFramerateFraction / 2);
  expected_fps_allocation[0].push_back(EncoderInfo::kMaxFramerateFraction);

  EXPECT_THAT(encoder_->GetEncoderInfo().fps_allocation,
              ::testing::ElementsAreArray(expected_fps_allocation));
}

TEST_F(TestVp8Impl, GetEncoderInfoFpsAllocationScreenshareLayers) {
  EXPECT_EQ(WEBRTC_VIDEO_CODEC_OK, encoder_->Release());
  codec_settings_.numberOfSimulcastStreams = 1;
  codec_settings_.mode = VideoCodecMode::kScreensharing;
  codec_settings_.simulcastStream[0].active = true;
  codec_settings_.simulcastStream[0].minBitrate = 30;
  codec_settings_.simulcastStream[0].targetBitrate =
      kLegacyScreenshareTl0BitrateKbps;
  codec_settings_.simulcastStream[0].maxBitrate =
      kLegacyScreenshareTl1BitrateKbps;
  codec_settings_.simulcastStream[0].numberOfTemporalLayers = 2;
  codec_settings_.legacy_conference_mode = true;
  EXPECT_EQ(WEBRTC_VIDEO_CODEC_OK,
            encoder_->InitEncode(&codec_settings_, kSettings));

  // Expect empty vector, since this mode doesn't have a fixed framerate.
  FramerateFractions expected_fps_allocation[kMaxSpatialLayers];
  EXPECT_THAT(encoder_->GetEncoderInfo().fps_allocation,
              ::testing::ElementsAreArray(expected_fps_allocation));
}

TEST_F(TestVp8Impl, GetEncoderInfoFpsAllocationSimulcastVideo) {
  EXPECT_EQ(WEBRTC_VIDEO_CODEC_OK, encoder_->Release());

  // Set up three simulcast streams with three temporal layers each.
  codec_settings_.numberOfSimulcastStreams = 3;
  for (int i = 0; i < codec_settings_.numberOfSimulcastStreams; ++i) {
    codec_settings_.simulcastStream[i].active = true;
    codec_settings_.simulcastStream[i].minBitrate = 30;
    codec_settings_.simulcastStream[i].targetBitrate = 30;
    codec_settings_.simulcastStream[i].maxBitrate = 30;
    codec_settings_.simulcastStream[i].numberOfTemporalLayers = 3;
    codec_settings_.simulcastStream[i].width =
        codec_settings_.width >>
        (codec_settings_.numberOfSimulcastStreams - i - 1);
    codec_settings_.simulcastStream[i].height =
        codec_settings_.height >>
        (codec_settings_.numberOfSimulcastStreams - i - 1);
  }

  EXPECT_EQ(WEBRTC_VIDEO_CODEC_OK,
            encoder_->InitEncode(&codec_settings_, kSettings));

  FramerateFractions expected_fps_allocation[kMaxSpatialLayers];
  expected_fps_allocation[0].push_back(EncoderInfo::kMaxFramerateFraction / 4);
  expected_fps_allocation[0].push_back(EncoderInfo::kMaxFramerateFraction / 2);
  expected_fps_allocation[0].push_back(EncoderInfo::kMaxFramerateFraction);
  expected_fps_allocation[1] = expected_fps_allocation[0];
  expected_fps_allocation[2] = expected_fps_allocation[0];
  EXPECT_THAT(encoder_->GetEncoderInfo().fps_allocation,
              ::testing::ElementsAreArray(expected_fps_allocation));

  // Release encoder and re-init without temporal layers.
  EXPECT_EQ(WEBRTC_VIDEO_CODEC_OK, encoder_->Release());

  // Sanity check fps allocation when not inited.
  FramerateFractions default_fps_fraction[kMaxSpatialLayers];
  default_fps_fraction[0].push_back(EncoderInfo::kMaxFramerateFraction);
  EXPECT_THAT(encoder_->GetEncoderInfo().fps_allocation,
              ::testing::ElementsAreArray(default_fps_fraction));

  for (int i = 0; i < codec_settings_.numberOfSimulcastStreams; ++i) {
    codec_settings_.simulcastStream[i].numberOfTemporalLayers = 1;
  }
  EXPECT_EQ(WEBRTC_VIDEO_CODEC_OK,
            encoder_->InitEncode(&codec_settings_, kSettings));

  for (size_t i = 0; i < 3; ++i) {
    expected_fps_allocation[i].clear();
    expected_fps_allocation[i].push_back(EncoderInfo::kMaxFramerateFraction);
  }
  EXPECT_THAT(encoder_->GetEncoderInfo().fps_allocation,
              ::testing::ElementsAreArray(expected_fps_allocation));
}

class TestVp8ImplWithMaxFrameDropTrial
    : public TestVp8Impl,
      public ::testing::WithParamInterface<
          std::tuple<std::string, TimeDelta, TimeDelta>> {
 public:
  TestVp8ImplWithMaxFrameDropTrial()
      : TestVp8Impl(), trials_(std::get<0>(GetParam())) {}

 protected:
  test::ScopedFieldTrials trials_;
};

TEST_P(TestVp8ImplWithMaxFrameDropTrial, EnforcesMaxFrameDropInterval) {
  static constexpr int kFps = 5;
  auto [trial_string, max_interval_config, min_expected_interval] = GetParam();

  // Allow one frame interval over the configured max frame drop interval.
  TimeDelta max_frame_delta =
      max_interval_config + (TimeDelta::Seconds(1) / kFps);
  EXPECT_EQ(WEBRTC_VIDEO_CODEC_OK, encoder_->Release());

  // Set up low-bitrate screenshare stream.
  codec_settings_.numberOfSimulcastStreams = 1;
  codec_settings_.legacy_conference_mode = false;
  codec_settings_.mode = VideoCodecMode::kScreensharing;
  codec_settings_.maxFramerate = kFps;
  codec_settings_.width = 2880;
  codec_settings_.height = 1800;
  codec_settings_.minBitrate = 30;
  codec_settings_.maxBitrate = 420;
  codec_settings_.SetFrameDropEnabled(true);

  codec_settings_.simulcastStream[0].active = true;
  codec_settings_.simulcastStream[0].minBitrate = codec_settings_.minBitrate;
  codec_settings_.simulcastStream[0].targetBitrate = codec_settings_.maxBitrate;
  codec_settings_.simulcastStream[0].maxBitrate = codec_settings_.maxBitrate;
  codec_settings_.simulcastStream[0].numberOfTemporalLayers = 2;
  codec_settings_.simulcastStream[0].width = codec_settings_.width;
  codec_settings_.simulcastStream[0].height = codec_settings_.height;
  codec_settings_.simulcastStream[0].maxFramerate =
      codec_settings_.maxFramerate;

  EXPECT_EQ(WEBRTC_VIDEO_CODEC_OK,
            encoder_->InitEncode(&codec_settings_, kSettings));

  // Allocate a very constained amount of bitrate to increase risk of frame
  // drops.
  VideoBitrateAllocation bitrate_allocation;
  bitrate_allocation.SetBitrate(0, 0, 50'000);
  bitrate_allocation.SetBitrate(0, 1, 50'000);
  encoder_->SetRates(
      VideoEncoder::RateControlParameters(bitrate_allocation, 5.0));

  EncodedImage encoded_frame;
  CodecSpecificInfo codec_specific_info;
  // Create a low-complexity 1 square test sequence.
  input_frame_generator_ = test::CreateSquareFrameGenerator(
      codec_settings_.width, codec_settings_.height,
      test::FrameGeneratorInterface::OutputType::kI420,
      /*num_squares=*/1);

  class Callback : public EncodedImageCallback {
   public:
    Callback() : last_callback_(Timestamp::MinusInfinity()) {}

    const std::vector<TimeDelta>& GetCallbackDeltas() const {
      return callback_deltas_;
    }
    void ClearCallbackDeltas() { callback_deltas_.clear(); }

   protected:
    Result OnEncodedImage(const EncodedImage& encoded_image,
                          const CodecSpecificInfo* codec_specific_info) {
      Timestamp timestamp =
          Timestamp::Millis(encoded_image.RtpTimestamp() / 90);
      if (last_callback_.IsFinite()) {
        callback_deltas_.push_back(timestamp - last_callback_);
      }
      last_callback_ = timestamp;
      return Result(Result::Error::OK);
    }

   private:
    std::vector<TimeDelta> callback_deltas_;
    Timestamp last_callback_;
  } callback;

  encoder_->RegisterEncodeCompleteCallback(&callback);
  std::vector<VideoFrameType> frame_types = {VideoFrameType::kVideoFrameKey};
  EXPECT_EQ(encoder_->Encode(NextInputFrame(), &frame_types),
            WEBRTC_VIDEO_CODEC_OK);
  frame_types[0] = VideoFrameType::kVideoFrameDelta;

  // Encode a couple of frames and verify reasonable frame spacing.
  for (uint32_t i = 0; i < codec_settings_.maxFramerate * 10; ++i) {
    EXPECT_EQ(encoder_->Encode(NextInputFrame(), &frame_types),
              WEBRTC_VIDEO_CODEC_OK);
  }
  auto deltas = callback.GetCallbackDeltas();
  ASSERT_FALSE(deltas.empty());
  EXPECT_LE(*std::max_element(deltas.begin(), deltas.end()), max_frame_delta);

  // Switch to a much more complex input. Verify time deltas are still OK.
  input_frame_generator_ = test::CreateSquareFrameGenerator(
      codec_settings_.width, codec_settings_.height,
      test::FrameGeneratorInterface::OutputType::kI420,
      /*num_squares=*/5000);
  callback.ClearCallbackDeltas();
  for (uint32_t i = 0; i < codec_settings_.maxFramerate * 10; ++i) {
    EXPECT_EQ(encoder_->Encode(NextInputFrame(), &frame_types),
              WEBRTC_VIDEO_CODEC_OK);
  }
  deltas = callback.GetCallbackDeltas();
  ASSERT_FALSE(deltas.empty());
  EXPECT_LE(*std::max_element(deltas.begin(), deltas.end()), max_frame_delta);

  // Check that encoder is causing the expected long frame drop intervals.
  EXPECT_GT(*std::max_element(deltas.begin(), deltas.end()),
            min_expected_interval);

  EXPECT_EQ(WEBRTC_VIDEO_CODEC_OK, encoder_->Release());
}

INSTANTIATE_TEST_SUITE_P(
    All,
    TestVp8ImplWithMaxFrameDropTrial,
    ::testing::Values(
        // Tuple of {
        //  trial string,
        //  configured max frame interval,
        //  lower bound on expected frame drop intervals
        // }
        std::make_tuple("WebRTC-VP8-MaxFrameInterval/Disabled/",
                        TimeDelta::PlusInfinity(),
                        TimeDelta::Seconds(2)),
        std::make_tuple("WebRTC-VP8-MaxFrameInterval/interval:1s/",
                        TimeDelta::Seconds(1),
                        TimeDelta::Seconds(0)),
        std::make_tuple("", TimeDelta::Seconds(2), TimeDelta::Seconds(1))));

class TestVp8ImplForPixelFormat
    : public TestVp8Impl,
      public ::testing::WithParamInterface<VideoFrameBuffer::Type> {
 public:
  TestVp8ImplForPixelFormat() : TestVp8Impl(), mappable_type_(GetParam()) {}

 protected:
  VideoFrameBuffer::Type mappable_type_;
};

TEST_P(TestVp8ImplForPixelFormat, EncodeNativeFrameSimulcast) {
  EXPECT_EQ(WEBRTC_VIDEO_CODEC_OK, encoder_->Release());

  // Configure simulcast.
  codec_settings_.numberOfSimulcastStreams = 3;
  codec_settings_.simulcastStream[0] = {.width = kWidth / 4,
                                        .height = kHeight / 4,
                                        .maxFramerate = kFramerateFps,
                                        .numberOfTemporalLayers = 1,
                                        .maxBitrate = 4000,
                                        .targetBitrate = 3000,
                                        .minBitrate = 2000,
                                        .qpMax = 80,
                                        .active = true};
  codec_settings_.simulcastStream[1] = {.width = kWidth / 2,
                                        .height = kHeight / 2,
                                        .maxFramerate = kFramerateFps,
                                        .numberOfTemporalLayers = 1,
                                        .maxBitrate = 4000,
                                        .targetBitrate = 3000,
                                        .minBitrate = 2000,
                                        .qpMax = 80,
                                        .active = true};
  codec_settings_.simulcastStream[2] = {.width = kWidth,
                                        .height = kHeight,
                                        .maxFramerate = kFramerateFps,
                                        .numberOfTemporalLayers = 1,
                                        .maxBitrate = 4000,
                                        .targetBitrate = 3000,
                                        .minBitrate = 2000,
                                        .qpMax = 80,
                                        .active = true};
  EXPECT_EQ(WEBRTC_VIDEO_CODEC_OK,
            encoder_->InitEncode(&codec_settings_, kSettings));

  // Create a zero-conversion NV12 frame (calling ToI420 on it crashes).
  VideoFrame input_frame =
      test::CreateMappableNativeFrame(1, mappable_type_, kWidth, kHeight);

  EncodedImage encoded_frame;
  CodecSpecificInfo codec_specific_info;
  EncodeAndWaitForFrame(input_frame, &encoded_frame, &codec_specific_info);

  // After encoding, we expect one mapping per simulcast layer.
  rtc::scoped_refptr<test::MappableNativeBuffer> mappable_buffer =
      test::GetMappableNativeBufferFromVideoFrame(input_frame);
  std::vector<rtc::scoped_refptr<VideoFrameBuffer>> mapped_buffers =
      mappable_buffer->GetMappedFramedBuffers();
  ASSERT_EQ(mapped_buffers.size(), 3u);
  EXPECT_EQ(mapped_buffers[0]->type(), mappable_type_);
  EXPECT_EQ(mapped_buffers[0]->width(), kWidth);
  EXPECT_EQ(mapped_buffers[0]->height(), kHeight);
  EXPECT_EQ(mapped_buffers[1]->type(), mappable_type_);
  EXPECT_EQ(mapped_buffers[1]->width(), kWidth / 2);
  EXPECT_EQ(mapped_buffers[1]->height(), kHeight / 2);
  EXPECT_EQ(mapped_buffers[2]->type(), mappable_type_);
  EXPECT_EQ(mapped_buffers[2]->width(), kWidth / 4);
  EXPECT_EQ(mapped_buffers[2]->height(), kHeight / 4);
  EXPECT_FALSE(mappable_buffer->DidConvertToI420());

  EXPECT_EQ(WEBRTC_VIDEO_CODEC_OK, encoder_->Release());
}

INSTANTIATE_TEST_SUITE_P(All,
                         TestVp8ImplForPixelFormat,
                         ::testing::Values(VideoFrameBuffer::Type::kI420,
                                           VideoFrameBuffer::Type::kNV12));

}  // namespace webrtc
