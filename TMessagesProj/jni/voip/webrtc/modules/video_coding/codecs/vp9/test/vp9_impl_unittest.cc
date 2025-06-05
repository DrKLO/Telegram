/*
 *  Copyright (c) 2017 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "absl/memory/memory.h"
#include "api/test/create_frame_generator.h"
#include "api/test/frame_generator_interface.h"
#include "api/test/mock_video_encoder.h"
#include "api/video/color_space.h"
#include "api/video/i420_buffer.h"
#include "api/video_codecs/video_encoder.h"
#include "api/video_codecs/vp9_profile.h"
#include "common_video/libyuv/include/webrtc_libyuv.h"
#include "media/base/codec.h"
#include "media/base/media_constants.h"
#include "modules/rtp_rtcp/include/rtp_rtcp_defines.h"
#include "modules/video_coding/codecs/interface/libvpx_interface.h"
#include "modules/video_coding/codecs/interface/mock_libvpx_interface.h"
#include "modules/video_coding/codecs/test/encoded_video_frame_producer.h"
#include "modules/video_coding/codecs/test/video_codec_unittest.h"
#include "modules/video_coding/codecs/vp9/include/vp9.h"
#include "modules/video_coding/codecs/vp9/libvpx_vp9_encoder.h"
#include "modules/video_coding/codecs/vp9/svc_config.h"
#include "rtc_base/strings/string_builder.h"
#include "test/explicit_key_value_config.h"
#include "test/field_trial.h"
#include "test/gmock.h"
#include "test/gtest.h"
#include "test/mappable_native_buffer.h"
#include "test/video_codec_settings.h"

namespace webrtc {
namespace {

using ::testing::_;
using ::testing::A;
using ::testing::AllOf;
using ::testing::An;
using ::testing::AnyNumber;
using ::testing::ByRef;
using ::testing::DoAll;
using ::testing::Each;
using ::testing::ElementsAre;
using ::testing::ElementsAreArray;
using ::testing::Field;
using ::testing::IsEmpty;
using ::testing::Mock;
using ::testing::NiceMock;
using ::testing::Return;
using ::testing::SafeMatcherCast;
using ::testing::SaveArgPointee;
using ::testing::SetArgPointee;
using ::testing::SizeIs;
using ::testing::TypedEq;
using ::testing::UnorderedElementsAreArray;
using ::testing::WithArg;
using EncoderInfo = webrtc::VideoEncoder::EncoderInfo;
using FramerateFractions =
    absl::InlinedVector<uint8_t, webrtc::kMaxTemporalStreams>;

constexpr size_t kWidth = 1280;
constexpr size_t kHeight = 720;

const VideoEncoder::Capabilities kCapabilities(false);
const VideoEncoder::Settings kSettings(kCapabilities,
                                       /*number_of_cores=*/1,
                                       /*max_payload_size=*/0);

VideoCodec DefaultCodecSettings() {
  VideoCodec codec_settings;
  webrtc::test::CodecSettings(kVideoCodecVP9, &codec_settings);
  codec_settings.width = kWidth;
  codec_settings.height = kHeight;
  codec_settings.VP9()->numberOfTemporalLayers = 1;
  codec_settings.VP9()->numberOfSpatialLayers = 1;
  return codec_settings;
}

void ConfigureSvc(VideoCodec& codec_settings,
                  int num_spatial_layers,
                  int num_temporal_layers = 1) {
  codec_settings.VP9()->numberOfSpatialLayers = num_spatial_layers;
  codec_settings.VP9()->numberOfTemporalLayers = num_temporal_layers;
  codec_settings.SetFrameDropEnabled(false);

  std::vector<SpatialLayer> layers = GetSvcConfig(
      codec_settings.width, codec_settings.height, codec_settings.maxFramerate,
      /*first_active_layer=*/0, num_spatial_layers, num_temporal_layers, false);
  for (size_t i = 0; i < layers.size(); ++i) {
    codec_settings.spatialLayers[i] = layers[i];
  }
}

}  // namespace

class TestVp9Impl : public VideoCodecUnitTest {
 protected:
  std::unique_ptr<VideoEncoder> CreateEncoder() override {
    return VP9Encoder::Create();
  }

  std::unique_ptr<VideoDecoder> CreateDecoder() override {
    return VP9Decoder::Create();
  }

  void ModifyCodecSettings(VideoCodec* codec_settings) override {
    webrtc::test::CodecSettings(kVideoCodecVP9, codec_settings);
    codec_settings->width = kWidth;
    codec_settings->height = kHeight;
    codec_settings->VP9()->numberOfTemporalLayers = 1;
    codec_settings->VP9()->numberOfSpatialLayers = 1;
  }
};

class TestVp9ImplForPixelFormat
    : public TestVp9Impl,
      public ::testing::WithParamInterface<
          test::FrameGeneratorInterface::OutputType> {
 protected:
  void SetUp() override {
    input_frame_generator_ = test::CreateSquareFrameGenerator(
        kWidth, kHeight, GetParam(), absl::optional<int>());
    TestVp9Impl::SetUp();
  }
};

// Disabled on ios as flake, see https://crbug.com/webrtc/7057
#if defined(WEBRTC_IOS)
TEST_P(TestVp9ImplForPixelFormat, DISABLED_EncodeDecode) {
#else
TEST_P(TestVp9ImplForPixelFormat, EncodeDecode) {
#endif
  VideoFrame input_frame = NextInputFrame();
  EXPECT_EQ(WEBRTC_VIDEO_CODEC_OK, encoder_->Encode(input_frame, nullptr));
  EncodedImage encoded_frame;
  CodecSpecificInfo codec_specific_info;
  ASSERT_TRUE(WaitForEncodedFrame(&encoded_frame, &codec_specific_info));
  // First frame should be a key frame.
  encoded_frame._frameType = VideoFrameType::kVideoFrameKey;
  EXPECT_EQ(WEBRTC_VIDEO_CODEC_OK, decoder_->Decode(encoded_frame, 0));
  std::unique_ptr<VideoFrame> decoded_frame;
  absl::optional<uint8_t> decoded_qp;
  ASSERT_TRUE(WaitForDecodedFrame(&decoded_frame, &decoded_qp));
  ASSERT_TRUE(decoded_frame);
  EXPECT_GT(I420PSNR(&input_frame, decoded_frame.get()), 36);

  const ColorSpace color_space = *decoded_frame->color_space();
  EXPECT_EQ(ColorSpace::PrimaryID::kUnspecified, color_space.primaries());
  EXPECT_EQ(ColorSpace::TransferID::kUnspecified, color_space.transfer());
  EXPECT_EQ(ColorSpace::MatrixID::kUnspecified, color_space.matrix());
  EXPECT_EQ(ColorSpace::RangeID::kLimited, color_space.range());
  EXPECT_EQ(ColorSpace::ChromaSiting::kUnspecified,
            color_space.chroma_siting_horizontal());
  EXPECT_EQ(ColorSpace::ChromaSiting::kUnspecified,
            color_space.chroma_siting_vertical());
}

TEST_P(TestVp9ImplForPixelFormat, EncodeNativeBuffer) {
  VideoFrame input_frame = NextInputFrame();
  // Replace the input frame with a fake native buffer of the same size and
  // underlying pixel format. Do not allow ToI420() for non-I420 buffers,
  // ensuring zero-conversion.
  input_frame = test::CreateMappableNativeFrame(
      input_frame.ntp_time_ms(), input_frame.video_frame_buffer()->type(),
      input_frame.width(), input_frame.height());
  EXPECT_EQ(WEBRTC_VIDEO_CODEC_OK, encoder_->Encode(input_frame, nullptr));
  EncodedImage encoded_frame;
  CodecSpecificInfo codec_specific_info;
  ASSERT_TRUE(WaitForEncodedFrame(&encoded_frame, &codec_specific_info));

  // After encoding, we would expect a single mapping to have happened.
  rtc::scoped_refptr<test::MappableNativeBuffer> mappable_buffer =
      test::GetMappableNativeBufferFromVideoFrame(input_frame);
  std::vector<rtc::scoped_refptr<VideoFrameBuffer>> mapped_buffers =
      mappable_buffer->GetMappedFramedBuffers();
  ASSERT_EQ(mapped_buffers.size(), 1u);
  EXPECT_EQ(mapped_buffers[0]->type(), mappable_buffer->mappable_type());
  EXPECT_EQ(mapped_buffers[0]->width(), input_frame.width());
  EXPECT_EQ(mapped_buffers[0]->height(), input_frame.height());
  EXPECT_FALSE(mappable_buffer->DidConvertToI420());
}

TEST_P(TestVp9ImplForPixelFormat, DecodedColorSpaceFromBitstream) {
  EXPECT_EQ(WEBRTC_VIDEO_CODEC_OK, encoder_->Encode(NextInputFrame(), nullptr));
  EncodedImage encoded_frame;
  CodecSpecificInfo codec_specific_info;
  ASSERT_TRUE(WaitForEncodedFrame(&encoded_frame, &codec_specific_info));

  // Encoded frame without explicit color space information.
  EXPECT_EQ(WEBRTC_VIDEO_CODEC_OK, decoder_->Decode(encoded_frame, 0));
  std::unique_ptr<VideoFrame> decoded_frame;
  absl::optional<uint8_t> decoded_qp;
  ASSERT_TRUE(WaitForDecodedFrame(&decoded_frame, &decoded_qp));
  ASSERT_TRUE(decoded_frame);
  // Color space present from encoded bitstream.
  ASSERT_TRUE(decoded_frame->color_space());
  // No HDR metadata present.
  EXPECT_FALSE(decoded_frame->color_space()->hdr_metadata());
}

TEST_P(TestVp9ImplForPixelFormat, DecodedQpEqualsEncodedQp) {
  EXPECT_EQ(WEBRTC_VIDEO_CODEC_OK, encoder_->Encode(NextInputFrame(), nullptr));
  EncodedImage encoded_frame;
  CodecSpecificInfo codec_specific_info;
  ASSERT_TRUE(WaitForEncodedFrame(&encoded_frame, &codec_specific_info));
  // First frame should be a key frame.
  encoded_frame._frameType = VideoFrameType::kVideoFrameKey;
  EXPECT_EQ(WEBRTC_VIDEO_CODEC_OK, decoder_->Decode(encoded_frame, 0));
  std::unique_ptr<VideoFrame> decoded_frame;
  absl::optional<uint8_t> decoded_qp;
  ASSERT_TRUE(WaitForDecodedFrame(&decoded_frame, &decoded_qp));
  ASSERT_TRUE(decoded_frame);
  ASSERT_TRUE(decoded_qp);
  EXPECT_EQ(encoded_frame.qp_, *decoded_qp);
}

TEST_P(TestVp9ImplForPixelFormat, CheckCaptureTimeID) {
  constexpr Timestamp kCaptureTimeIdentifier = Timestamp::Micros(1000);
  VideoFrame input_frame = NextInputFrame();
  input_frame.set_capture_time_identifier(kCaptureTimeIdentifier);
  EXPECT_EQ(WEBRTC_VIDEO_CODEC_OK, encoder_->Encode(input_frame, nullptr));
  EncodedImage encoded_frame;
  CodecSpecificInfo codec_specific_info;
  ASSERT_TRUE(WaitForEncodedFrame(&encoded_frame, &codec_specific_info));
  ASSERT_TRUE(encoded_frame.CaptureTimeIdentifier().has_value());
  EXPECT_EQ(kCaptureTimeIdentifier.us(),
            encoded_frame.CaptureTimeIdentifier()->us());
}

TEST_F(TestVp9Impl, SwitchInputPixelFormatsWithoutReconfigure) {
  EXPECT_EQ(WEBRTC_VIDEO_CODEC_OK, encoder_->Encode(NextInputFrame(), nullptr));
  EncodedImage encoded_frame;
  CodecSpecificInfo codec_specific_info;
  ASSERT_TRUE(WaitForEncodedFrame(&encoded_frame, &codec_specific_info));

  // Change the input frame type from I420 to NV12, encoding should still work.
  input_frame_generator_ = test::CreateSquareFrameGenerator(
      kWidth, kHeight, test::FrameGeneratorInterface::OutputType::kNV12,
      absl::optional<int>());
  EXPECT_EQ(WEBRTC_VIDEO_CODEC_OK, encoder_->Encode(NextInputFrame(), nullptr));
  ASSERT_TRUE(WaitForEncodedFrame(&encoded_frame, &codec_specific_info));

  // Flipping back to I420, encoding should still work.
  input_frame_generator_ = test::CreateSquareFrameGenerator(
      kWidth, kHeight, test::FrameGeneratorInterface::OutputType::kI420,
      absl::optional<int>());
  EXPECT_EQ(WEBRTC_VIDEO_CODEC_OK, encoder_->Encode(NextInputFrame(), nullptr));
  ASSERT_TRUE(WaitForEncodedFrame(&encoded_frame, &codec_specific_info));
}

TEST(Vp9ImplTest, ParserQpEqualsEncodedQp) {
  std::unique_ptr<VideoEncoder> encoder = VP9Encoder::Create();
  VideoCodec codec_settings = DefaultCodecSettings();
  encoder->InitEncode(&codec_settings, kSettings);

  std::vector<EncodedVideoFrameProducer::EncodedFrame> frames =
      EncodedVideoFrameProducer(*encoder)
          .SetNumInputFrames(1)
          .SetResolution({kWidth, kHeight})
          .Encode();
  ASSERT_THAT(frames, SizeIs(1));
  const auto& encoded_frame = frames.front().encoded_image;
  int qp = 0;
  ASSERT_TRUE(vp9::GetQp(encoded_frame.data(), encoded_frame.size(), &qp));
  EXPECT_EQ(encoded_frame.qp_, qp);
}

TEST(Vp9ImplTest, EncodeAttachesTemplateStructureWithSvcController) {
  std::unique_ptr<VideoEncoder> encoder = VP9Encoder::Create();
  VideoCodec codec_settings = DefaultCodecSettings();
  EXPECT_EQ(encoder->InitEncode(&codec_settings, kSettings),
            WEBRTC_VIDEO_CODEC_OK);

  std::vector<EncodedVideoFrameProducer::EncodedFrame> frames =
      EncodedVideoFrameProducer(*encoder)
          .SetNumInputFrames(2)
          .SetResolution({kWidth, kHeight})
          .Encode();

  ASSERT_THAT(frames, SizeIs(2));
  EXPECT_TRUE(frames[0].codec_specific_info.template_structure);
  EXPECT_TRUE(frames[0].codec_specific_info.generic_frame_info);

  EXPECT_FALSE(frames[1].codec_specific_info.template_structure);
  EXPECT_TRUE(frames[1].codec_specific_info.generic_frame_info);
}

TEST(Vp9ImplTest, EncoderWith2TemporalLayers) {
  std::unique_ptr<VideoEncoder> encoder = VP9Encoder::Create();
  VideoCodec codec_settings = DefaultCodecSettings();
  codec_settings.VP9()->numberOfTemporalLayers = 2;
  // Tl0PidIdx is only used in non-flexible mode.
  codec_settings.VP9()->flexibleMode = false;
  EXPECT_EQ(encoder->InitEncode(&codec_settings, kSettings),
            WEBRTC_VIDEO_CODEC_OK);

  std::vector<EncodedVideoFrameProducer::EncodedFrame> frames =
      EncodedVideoFrameProducer(*encoder)
          .SetNumInputFrames(4)
          .SetResolution({kWidth, kHeight})
          .Encode();

  ASSERT_THAT(frames, SizeIs(4));
  EXPECT_EQ(frames[0].codec_specific_info.codecSpecific.VP9.temporal_idx, 0);
  EXPECT_EQ(frames[1].codec_specific_info.codecSpecific.VP9.temporal_idx, 1);
  EXPECT_EQ(frames[2].codec_specific_info.codecSpecific.VP9.temporal_idx, 0);
  EXPECT_EQ(frames[3].codec_specific_info.codecSpecific.VP9.temporal_idx, 1);
}

TEST(Vp9ImplTest, EncodeTemporalLayersWithSvcController) {
  std::unique_ptr<VideoEncoder> encoder = VP9Encoder::Create();
  VideoCodec codec_settings = DefaultCodecSettings();
  codec_settings.VP9()->numberOfTemporalLayers = 2;
  EXPECT_EQ(encoder->InitEncode(&codec_settings, kSettings),
            WEBRTC_VIDEO_CODEC_OK);

  std::vector<EncodedVideoFrameProducer::EncodedFrame> frames =
      EncodedVideoFrameProducer(*encoder)
          .SetNumInputFrames(4)
          .SetResolution({kWidth, kHeight})
          .Encode();

  ASSERT_THAT(frames, SizeIs(4));
  EXPECT_EQ(frames[0].codec_specific_info.codecSpecific.VP9.temporal_idx, 0);
  EXPECT_EQ(frames[1].codec_specific_info.codecSpecific.VP9.temporal_idx, 1);
  EXPECT_EQ(frames[2].codec_specific_info.codecSpecific.VP9.temporal_idx, 0);
  EXPECT_EQ(frames[3].codec_specific_info.codecSpecific.VP9.temporal_idx, 1);
  // Verify codec agnostic part
  ASSERT_TRUE(frames[0].codec_specific_info.generic_frame_info);
  ASSERT_TRUE(frames[1].codec_specific_info.generic_frame_info);
  ASSERT_TRUE(frames[2].codec_specific_info.generic_frame_info);
  ASSERT_TRUE(frames[3].codec_specific_info.generic_frame_info);
  EXPECT_EQ(frames[0].codec_specific_info.generic_frame_info->temporal_id, 0);
  EXPECT_EQ(frames[1].codec_specific_info.generic_frame_info->temporal_id, 1);
  EXPECT_EQ(frames[2].codec_specific_info.generic_frame_info->temporal_id, 0);
  EXPECT_EQ(frames[3].codec_specific_info.generic_frame_info->temporal_id, 1);
}

TEST(Vp9ImplTest, EncoderWith2SpatialLayers) {
  std::unique_ptr<VideoEncoder> encoder = VP9Encoder::Create();
  VideoCodec codec_settings = DefaultCodecSettings();
  codec_settings.VP9()->numberOfSpatialLayers = 2;
  EXPECT_EQ(encoder->InitEncode(&codec_settings, kSettings),
            WEBRTC_VIDEO_CODEC_OK);

  std::vector<EncodedVideoFrameProducer::EncodedFrame> frames =
      EncodedVideoFrameProducer(*encoder)
          .SetNumInputFrames(1)
          .SetResolution({kWidth, kHeight})
          .Encode();

  ASSERT_THAT(frames, SizeIs(2));
  EXPECT_EQ(frames[0].encoded_image.SpatialIndex(), 0);
  EXPECT_EQ(frames[1].encoded_image.SpatialIndex(), 1);
}

TEST(Vp9ImplTest, EncodeSpatialLayersWithSvcController) {
  std::unique_ptr<VideoEncoder> encoder = VP9Encoder::Create();
  VideoCodec codec_settings = DefaultCodecSettings();
  codec_settings.VP9()->numberOfSpatialLayers = 2;
  EXPECT_EQ(encoder->InitEncode(&codec_settings, kSettings),
            WEBRTC_VIDEO_CODEC_OK);

  std::vector<EncodedVideoFrameProducer::EncodedFrame> frames =
      EncodedVideoFrameProducer(*encoder)
          .SetNumInputFrames(2)
          .SetResolution({kWidth, kHeight})
          .Encode();

  ASSERT_THAT(frames, SizeIs(4));
  EXPECT_EQ(frames[0].encoded_image.SpatialIndex(), 0);
  EXPECT_EQ(frames[1].encoded_image.SpatialIndex(), 1);
  EXPECT_EQ(frames[2].encoded_image.SpatialIndex(), 0);
  EXPECT_EQ(frames[3].encoded_image.SpatialIndex(), 1);
  // Verify codec agnostic part
  ASSERT_TRUE(frames[0].codec_specific_info.generic_frame_info);
  ASSERT_TRUE(frames[1].codec_specific_info.generic_frame_info);
  ASSERT_TRUE(frames[2].codec_specific_info.generic_frame_info);
  ASSERT_TRUE(frames[3].codec_specific_info.generic_frame_info);
  EXPECT_EQ(frames[0].codec_specific_info.generic_frame_info->spatial_id, 0);
  EXPECT_EQ(frames[1].codec_specific_info.generic_frame_info->spatial_id, 1);
  EXPECT_EQ(frames[2].codec_specific_info.generic_frame_info->spatial_id, 0);
  EXPECT_EQ(frames[3].codec_specific_info.generic_frame_info->spatial_id, 1);
}

TEST_F(TestVp9Impl, EncoderExplicitLayering) {
  // Override default settings.
  codec_settings_.VP9()->numberOfTemporalLayers = 1;
  codec_settings_.VP9()->numberOfSpatialLayers = 2;

  codec_settings_.width = 960;
  codec_settings_.height = 540;
  codec_settings_.spatialLayers[0].minBitrate = 200;
  codec_settings_.spatialLayers[0].maxBitrate = 500;
  codec_settings_.spatialLayers[0].targetBitrate =
      (codec_settings_.spatialLayers[0].minBitrate +
       codec_settings_.spatialLayers[0].maxBitrate) /
      2;
  codec_settings_.spatialLayers[0].active = true;

  codec_settings_.spatialLayers[1].minBitrate = 400;
  codec_settings_.spatialLayers[1].maxBitrate = 1500;
  codec_settings_.spatialLayers[1].targetBitrate =
      (codec_settings_.spatialLayers[1].minBitrate +
       codec_settings_.spatialLayers[1].maxBitrate) /
      2;
  codec_settings_.spatialLayers[1].active = true;

  codec_settings_.spatialLayers[0].width = codec_settings_.width / 2;
  codec_settings_.spatialLayers[0].height = codec_settings_.height / 2;
  codec_settings_.spatialLayers[0].maxFramerate = codec_settings_.maxFramerate;
  codec_settings_.spatialLayers[1].width = codec_settings_.width;
  codec_settings_.spatialLayers[1].height = codec_settings_.height;
  codec_settings_.spatialLayers[1].maxFramerate = codec_settings_.maxFramerate;

  EXPECT_EQ(WEBRTC_VIDEO_CODEC_OK,
            encoder_->InitEncode(&codec_settings_, kSettings));

  // Ensure it fails if scaling factors in horz/vert dimentions are different.
  codec_settings_.spatialLayers[0].width = codec_settings_.width;
  codec_settings_.spatialLayers[0].height = codec_settings_.height / 2;
  codec_settings_.spatialLayers[1].width = codec_settings_.width;
  codec_settings_.spatialLayers[1].height = codec_settings_.height;
  EXPECT_EQ(WEBRTC_VIDEO_CODEC_ERR_PARAMETER,
            encoder_->InitEncode(&codec_settings_, kSettings));

  // Ensure it fails if scaling factor is not power of two.
  codec_settings_.spatialLayers[0].width = codec_settings_.width / 3;
  codec_settings_.spatialLayers[0].height = codec_settings_.height / 3;
  codec_settings_.spatialLayers[1].width = codec_settings_.width;
  codec_settings_.spatialLayers[1].height = codec_settings_.height;
  EXPECT_EQ(WEBRTC_VIDEO_CODEC_ERR_PARAMETER,
            encoder_->InitEncode(&codec_settings_, kSettings));
}

TEST_F(TestVp9Impl, EnableDisableSpatialLayers) {
  // Configure encoder to produce N spatial layers. Encode frames of layer 0
  // then enable layer 1 and encode more frames and so on until layer N-1.
  // Then disable layers one by one in the same way.
  // Note: bit rate allocation is high to avoid frame dropping due to rate
  // control, the encoder should always produce a frame. A dropped
  // frame indicates a problem and the test will fail.
  const size_t num_spatial_layers = 3;
  const size_t num_frames_to_encode = 5;

  ConfigureSvc(codec_settings_, num_spatial_layers);
  codec_settings_.SetFrameDropEnabled(true);

  EXPECT_EQ(WEBRTC_VIDEO_CODEC_OK,
            encoder_->InitEncode(&codec_settings_, kSettings));

  VideoBitrateAllocation bitrate_allocation;
  for (size_t sl_idx = 0; sl_idx < num_spatial_layers; ++sl_idx) {
    // Allocate high bit rate to avoid frame dropping due to rate control.
    bitrate_allocation.SetBitrate(
        sl_idx, 0,
        codec_settings_.spatialLayers[sl_idx].targetBitrate * 1000 * 2);
    encoder_->SetRates(VideoEncoder::RateControlParameters(
        bitrate_allocation, codec_settings_.maxFramerate));

    for (size_t frame_num = 0; frame_num < num_frames_to_encode; ++frame_num) {
      SetWaitForEncodedFramesThreshold(sl_idx + 1);
      EXPECT_EQ(WEBRTC_VIDEO_CODEC_OK,
                encoder_->Encode(NextInputFrame(), nullptr));
      std::vector<EncodedImage> encoded_frame;
      std::vector<CodecSpecificInfo> codec_specific_info;
      ASSERT_TRUE(WaitForEncodedFrames(&encoded_frame, &codec_specific_info));
      EXPECT_EQ(codec_specific_info[0].codecSpecific.VP9.ss_data_available,
                frame_num == 0);
    }
  }

  for (size_t i = 0; i < num_spatial_layers - 1; ++i) {
    const size_t sl_idx = num_spatial_layers - i - 1;
    bitrate_allocation.SetBitrate(sl_idx, 0, 0);
    encoder_->SetRates(VideoEncoder::RateControlParameters(
        bitrate_allocation, codec_settings_.maxFramerate));

    for (size_t frame_num = 0; frame_num < num_frames_to_encode; ++frame_num) {
      SetWaitForEncodedFramesThreshold(sl_idx);
      EXPECT_EQ(WEBRTC_VIDEO_CODEC_OK,
                encoder_->Encode(NextInputFrame(), nullptr));
      std::vector<EncodedImage> encoded_frame;
      std::vector<CodecSpecificInfo> codec_specific_info;
      ASSERT_TRUE(WaitForEncodedFrames(&encoded_frame, &codec_specific_info));
      EXPECT_EQ(codec_specific_info[0].codecSpecific.VP9.ss_data_available,
                frame_num == 0);
    }
  }
}

TEST(Vp9ImplTest, EnableDisableSpatialLayersWithSvcController) {
  const int num_spatial_layers = 3;
  // Configure encoder to produce 3 spatial layers. Encode frames of layer 0
  // then enable layer 1 and encode more frames and so on.
  // Then disable layers one by one in the same way.
  // Note: bit rate allocation is high to avoid frame dropping due to rate
  // control, the encoder should always produce a frame. A dropped
  // frame indicates a problem and the test will fail.
  std::unique_ptr<VideoEncoder> encoder = VP9Encoder::Create();
  VideoCodec codec_settings = DefaultCodecSettings();
  ConfigureSvc(codec_settings, num_spatial_layers);
  codec_settings.SetFrameDropEnabled(true);
  EXPECT_EQ(encoder->InitEncode(&codec_settings, kSettings),
            WEBRTC_VIDEO_CODEC_OK);

  EncodedVideoFrameProducer producer(*encoder);
  producer.SetResolution({kWidth, kHeight});

  // Encode a key frame to validate all other frames are delta frames.
  std::vector<EncodedVideoFrameProducer::EncodedFrame> frames =
      producer.SetNumInputFrames(1).Encode();
  ASSERT_THAT(frames, Not(IsEmpty()));
  EXPECT_TRUE(frames[0].codec_specific_info.template_structure);

  const size_t num_frames_to_encode = 5;

  VideoBitrateAllocation bitrate_allocation;
  for (size_t sl_idx = 0; sl_idx < num_spatial_layers; ++sl_idx) {
    // Allocate high bit rate to avoid frame dropping due to rate control.
    bitrate_allocation.SetBitrate(
        sl_idx, 0,
        codec_settings.spatialLayers[sl_idx].targetBitrate * 1000 * 2);
    encoder->SetRates(VideoEncoder::RateControlParameters(
        bitrate_allocation, codec_settings.maxFramerate));

    frames = producer.SetNumInputFrames(num_frames_to_encode).Encode();
    // With (sl_idx+1) spatial layers expect (sl_idx+1) frames per input frame.
    ASSERT_THAT(frames, SizeIs(num_frames_to_encode * (sl_idx + 1)));
    for (size_t i = 0; i < frames.size(); ++i) {
      EXPECT_TRUE(frames[i].codec_specific_info.generic_frame_info);
      EXPECT_FALSE(frames[i].codec_specific_info.template_structure);
    }
  }

  for (int sl_idx = num_spatial_layers - 1; sl_idx > 0; --sl_idx) {
    bitrate_allocation.SetBitrate(sl_idx, 0, 0);
    encoder->SetRates(VideoEncoder::RateControlParameters(
        bitrate_allocation, codec_settings.maxFramerate));

    frames = producer.SetNumInputFrames(num_frames_to_encode).Encode();
    // With `sl_idx` spatial layer disabled, there are `sl_idx` spatial layers
    // left.
    ASSERT_THAT(frames, SizeIs(num_frames_to_encode * sl_idx));
    for (size_t i = 0; i < frames.size(); ++i) {
      EXPECT_TRUE(frames[i].codec_specific_info.generic_frame_info);
      EXPECT_FALSE(frames[i].codec_specific_info.template_structure);
    }
  }
}

MATCHER_P2(GenericLayerIs, spatial_id, temporal_id, "") {
  if (arg.codec_specific_info.generic_frame_info == absl::nullopt) {
    *result_listener << " miss generic_frame_info";
    return false;
  }
  const auto& layer = *arg.codec_specific_info.generic_frame_info;
  if (layer.spatial_id != spatial_id || layer.temporal_id != temporal_id) {
    *result_listener << " frame from layer (" << layer.spatial_id << ", "
                     << layer.temporal_id << ")";
    return false;
  }
  return true;
}

TEST(Vp9ImplTest, SpatialUpswitchNotAtGOFBoundary) {
  std::unique_ptr<VideoEncoder> encoder = VP9Encoder::Create();
  VideoCodec codec_settings = DefaultCodecSettings();
  ConfigureSvc(codec_settings, /*num_spatial_layers=*/3,
               /*num_temporal_layers=*/3);
  codec_settings.SetFrameDropEnabled(true);
  EXPECT_EQ(encoder->InitEncode(&codec_settings, kSettings),
            WEBRTC_VIDEO_CODEC_OK);

  EncodedVideoFrameProducer producer(*encoder);
  producer.SetResolution({kWidth, kHeight});

  // Disable all but spatial_layer = 0;
  VideoBitrateAllocation bitrate_allocation;
  int layer_bitrate_bps = codec_settings.spatialLayers[0].targetBitrate * 1000;
  bitrate_allocation.SetBitrate(0, 0, layer_bitrate_bps);
  bitrate_allocation.SetBitrate(0, 1, layer_bitrate_bps);
  bitrate_allocation.SetBitrate(0, 2, layer_bitrate_bps);
  encoder->SetRates(VideoEncoder::RateControlParameters(
      bitrate_allocation, codec_settings.maxFramerate));
  EXPECT_THAT(producer.SetNumInputFrames(3).Encode(),
              ElementsAre(GenericLayerIs(0, 0), GenericLayerIs(0, 2),
                          GenericLayerIs(0, 1)));

  // Upswitch to spatial_layer = 1
  layer_bitrate_bps = codec_settings.spatialLayers[1].targetBitrate * 1000;
  bitrate_allocation.SetBitrate(1, 0, layer_bitrate_bps);
  bitrate_allocation.SetBitrate(1, 1, layer_bitrate_bps);
  bitrate_allocation.SetBitrate(1, 2, layer_bitrate_bps);
  encoder->SetRates(VideoEncoder::RateControlParameters(
      bitrate_allocation, codec_settings.maxFramerate));
  // Expect upswitch doesn't happen immediately since there is no S1 frame that
  // S1T2 frame can reference.
  EXPECT_THAT(producer.SetNumInputFrames(1).Encode(),
              ElementsAre(GenericLayerIs(0, 2)));
  // Expect spatial upswitch happens now, at T0 frame.
  EXPECT_THAT(producer.SetNumInputFrames(1).Encode(),
              ElementsAre(GenericLayerIs(0, 0), GenericLayerIs(1, 0)));
}
// TODO(bugs.webrtc.org/13442) Enable once a forward fix has landed in WebRTC.
TEST_F(TestVp9Impl, DISABLED_DisableEnableBaseLayerTriggersKeyFrame) {
  // Configure encoder to produce N spatial layers. Encode frames for all
  // layers. Then disable all but the last layer. Then reenable all back again.
  test::ScopedFieldTrials override_field_trials(
      "WebRTC-Vp9ExternalRefCtrl/Enabled/");
  const size_t num_spatial_layers = 3;
  const size_t num_temporal_layers = 3;
  // Must not be multiple of temporal period to exercise all code paths.
  const size_t num_frames_to_encode = 5;

  ConfigureSvc(codec_settings_, num_spatial_layers, num_temporal_layers);
  codec_settings_.SetFrameDropEnabled(false);
  codec_settings_.VP9()->flexibleMode = false;
  codec_settings_.VP9()->interLayerPred = InterLayerPredMode::kOnKeyPic;
  codec_settings_.mode = VideoCodecMode::kRealtimeVideo;

  EXPECT_EQ(WEBRTC_VIDEO_CODEC_OK,
            encoder_->InitEncode(&codec_settings_, kSettings));

  VideoBitrateAllocation bitrate_allocation;
  for (size_t sl_idx = 0; sl_idx < num_spatial_layers; ++sl_idx) {
    for (size_t tl_idx = 0; tl_idx < num_temporal_layers; ++tl_idx) {
      // Allocate high bit rate to avoid frame dropping due to rate control.
      bitrate_allocation.SetBitrate(
          sl_idx, tl_idx,
          codec_settings_.spatialLayers[sl_idx].targetBitrate * 1000 * 2);
    }
  }
  encoder_->SetRates(VideoEncoder::RateControlParameters(
      bitrate_allocation, codec_settings_.maxFramerate));

  for (size_t frame_num = 0; frame_num < num_frames_to_encode; ++frame_num) {
    SetWaitForEncodedFramesThreshold(num_spatial_layers);
    EXPECT_EQ(WEBRTC_VIDEO_CODEC_OK,
              encoder_->Encode(NextInputFrame(), nullptr));
    std::vector<EncodedImage> encoded_frame;
    std::vector<CodecSpecificInfo> codec_specific_info;
    ASSERT_TRUE(WaitForEncodedFrames(&encoded_frame, &codec_specific_info));
    EXPECT_EQ(codec_specific_info[0].codecSpecific.VP9.ss_data_available,
              frame_num == 0);
  }

  // Disable all but top layer.
  for (size_t sl_idx = 0; sl_idx < num_spatial_layers - 1; ++sl_idx) {
    for (size_t tl_idx = 0; tl_idx < num_temporal_layers; ++tl_idx) {
      bitrate_allocation.SetBitrate(sl_idx, tl_idx, 0);
    }
  }
  encoder_->SetRates(VideoEncoder::RateControlParameters(
      bitrate_allocation, codec_settings_.maxFramerate));

  bool seen_ss_data = false;
  for (size_t frame_num = 0; frame_num < num_frames_to_encode; ++frame_num) {
    SetWaitForEncodedFramesThreshold(1);
    EXPECT_EQ(WEBRTC_VIDEO_CODEC_OK,
              encoder_->Encode(NextInputFrame(), nullptr));
    std::vector<EncodedImage> encoded_frame;
    std::vector<CodecSpecificInfo> codec_specific_info;
    ASSERT_TRUE(WaitForEncodedFrames(&encoded_frame, &codec_specific_info));
    // SS available immediatly after switching on base temporal layer.
    if (seen_ss_data) {
      EXPECT_EQ(codec_specific_info[0].codecSpecific.VP9.ss_data_available,
                false);
    } else {
      EXPECT_EQ(codec_specific_info[0].codecSpecific.VP9.ss_data_available,
                codec_specific_info[0].codecSpecific.VP9.temporal_idx == 0);
      seen_ss_data |=
          codec_specific_info[0].codecSpecific.VP9.ss_data_available;
    }
    // No key-frames generated for disabling layers.
    EXPECT_EQ(encoded_frame[0]._frameType, VideoFrameType::kVideoFrameDelta);
    EXPECT_EQ(encoded_frame[0].SpatialIndex().value_or(-1), 2);
  }
  EXPECT_TRUE(seen_ss_data);

  // Force key-frame.
  std::vector<VideoFrameType> frame_types = {VideoFrameType::kVideoFrameKey};
  SetWaitForEncodedFramesThreshold(1);
  EXPECT_EQ(WEBRTC_VIDEO_CODEC_OK,
            encoder_->Encode(NextInputFrame(), &frame_types));
  std::vector<EncodedImage> encoded_frame;
  std::vector<CodecSpecificInfo> codec_specific_info;
  ASSERT_TRUE(WaitForEncodedFrames(&encoded_frame, &codec_specific_info));
  // Key-frame should be produced.
  EXPECT_EQ(encoded_frame[0]._frameType, VideoFrameType::kVideoFrameKey);
  EXPECT_EQ(encoded_frame[0].SpatialIndex().value_or(-1), 2);

  // Encode some more frames.
  for (size_t frame_num = 0; frame_num < num_frames_to_encode; ++frame_num) {
    SetWaitForEncodedFramesThreshold(1);
    EXPECT_EQ(WEBRTC_VIDEO_CODEC_OK,
              encoder_->Encode(NextInputFrame(), nullptr));
    std::vector<EncodedImage> encoded_frame;
    std::vector<CodecSpecificInfo> codec_specific_info;
    ASSERT_TRUE(WaitForEncodedFrames(&encoded_frame, &codec_specific_info));
    EXPECT_EQ(encoded_frame[0]._frameType, VideoFrameType::kVideoFrameDelta);
    EXPECT_EQ(encoded_frame[0].SpatialIndex().value_or(-1), 2);
  }

  // Enable the second layer back.
  // Allocate high bit rate to avoid frame dropping due to rate control.
  for (size_t tl_idx = 0; tl_idx < num_temporal_layers; ++tl_idx) {
    bitrate_allocation.SetBitrate(
        1, tl_idx, codec_settings_.spatialLayers[0].targetBitrate * 1000 * 2);
  }
  encoder_->SetRates(VideoEncoder::RateControlParameters(
      bitrate_allocation, codec_settings_.maxFramerate));

  for (size_t frame_num = 0; frame_num < num_frames_to_encode; ++frame_num) {
    SetWaitForEncodedFramesThreshold(2);
    EXPECT_EQ(WEBRTC_VIDEO_CODEC_OK,
              encoder_->Encode(NextInputFrame(), nullptr));
    std::vector<EncodedImage> encoded_frame;
    std::vector<CodecSpecificInfo> codec_specific_info;
    ASSERT_TRUE(WaitForEncodedFrames(&encoded_frame, &codec_specific_info));
    ASSERT_EQ(encoded_frame.size(), 2u);
    // SS available immediatly after switching on.
    EXPECT_EQ(codec_specific_info[0].codecSpecific.VP9.ss_data_available,
              frame_num == 0);
    // Keyframe should be generated when enabling lower layers.
    const VideoFrameType expected_type = frame_num == 0
                                             ? VideoFrameType::kVideoFrameKey
                                             : VideoFrameType::kVideoFrameDelta;
    EXPECT_EQ(encoded_frame[0]._frameType, expected_type);
    EXPECT_EQ(encoded_frame[0].SpatialIndex().value_or(-1), 1);
    EXPECT_EQ(encoded_frame[1].SpatialIndex().value_or(-1), 2);
  }

  // Enable the first layer back.
  // Allocate high bit rate to avoid frame dropping due to rate control.
  for (size_t tl_idx = 0; tl_idx < num_temporal_layers; ++tl_idx) {
    bitrate_allocation.SetBitrate(
        0, tl_idx, codec_settings_.spatialLayers[1].targetBitrate * 1000 * 2);
  }
  encoder_->SetRates(VideoEncoder::RateControlParameters(
      bitrate_allocation, codec_settings_.maxFramerate));

  for (size_t frame_num = 0; frame_num < num_frames_to_encode; ++frame_num) {
    SetWaitForEncodedFramesThreshold(num_spatial_layers);
    EXPECT_EQ(WEBRTC_VIDEO_CODEC_OK,
              encoder_->Encode(NextInputFrame(), nullptr));
    std::vector<EncodedImage> encoded_frame;
    std::vector<CodecSpecificInfo> codec_specific_info;
    ASSERT_TRUE(WaitForEncodedFrames(&encoded_frame, &codec_specific_info));
    ASSERT_EQ(encoded_frame.size(), 3u);
    // SS available immediatly after switching on.
    EXPECT_EQ(codec_specific_info[0].codecSpecific.VP9.ss_data_available,
              frame_num == 0);
    // Keyframe should be generated when enabling lower layers.
    const VideoFrameType expected_type = frame_num == 0
                                             ? VideoFrameType::kVideoFrameKey
                                             : VideoFrameType::kVideoFrameDelta;
    EXPECT_EQ(encoded_frame[0]._frameType, expected_type);
  }
}
// TODO(bugs.webrtc.org/13442) Enable once a forward fix has landed in WebRTC.
TEST(Vp9ImplTest,
     DISABLED_DisableEnableBaseLayerWithSvcControllerTriggersKeyFrame) {
  // Configure encoder to produce N spatial layers. Encode frames for all
  // layers. Then disable all but the last layer. Then reenable all back again.
  const size_t num_spatial_layers = 3;
  const size_t num_temporal_layers = 3;
  // Must not be multiple of temporal period to exercise all code paths.
  const size_t num_frames_to_encode = 5;

  std::unique_ptr<VideoEncoder> encoder = VP9Encoder::Create();
  VideoCodec codec_settings = DefaultCodecSettings();
  ConfigureSvc(codec_settings, num_spatial_layers, num_temporal_layers);
  codec_settings.SetFrameDropEnabled(false);
  codec_settings.VP9()->flexibleMode = false;
  codec_settings.VP9()->interLayerPred = InterLayerPredMode::kOnKeyPic;
  codec_settings.mode = VideoCodecMode::kRealtimeVideo;

  EXPECT_EQ(encoder->InitEncode(&codec_settings, kSettings),
            WEBRTC_VIDEO_CODEC_OK);

  VideoBitrateAllocation bitrate_allocation;
  for (size_t sl_idx = 0; sl_idx < num_spatial_layers; ++sl_idx) {
    for (size_t tl_idx = 0; tl_idx < num_temporal_layers; ++tl_idx) {
      // Allocate high bit rate to avoid frame dropping due to rate control.
      bitrate_allocation.SetBitrate(
          sl_idx, tl_idx,
          codec_settings.spatialLayers[sl_idx].targetBitrate * 1000 * 2);
    }
  }
  encoder->SetRates(VideoEncoder::RateControlParameters(
      bitrate_allocation, codec_settings.maxFramerate));

  EncodedVideoFrameProducer producer(*encoder);
  producer.SetResolution({kWidth, kHeight});

  std::vector<EncodedVideoFrameProducer::EncodedFrame> frames =
      producer.SetNumInputFrames(num_frames_to_encode).Encode();
  ASSERT_THAT(frames, SizeIs(num_frames_to_encode * num_spatial_layers));

  // Disable all but top spatial layer.
  for (size_t sl_idx = 0; sl_idx < num_spatial_layers - 1; ++sl_idx) {
    for (size_t tl_idx = 0; tl_idx < num_temporal_layers; ++tl_idx) {
      bitrate_allocation.SetBitrate(sl_idx, tl_idx, 0);
    }
  }
  encoder->SetRates(VideoEncoder::RateControlParameters(
      bitrate_allocation, codec_settings.maxFramerate));

  frames = producer.SetNumInputFrames(num_frames_to_encode).Encode();
  EXPECT_THAT(frames, SizeIs(num_frames_to_encode));
  for (const auto& frame : frames) {
    // Expect no key-frames generated.
    EXPECT_FALSE(frame.codec_specific_info.template_structure);
    ASSERT_TRUE(frame.codec_specific_info.generic_frame_info);
    EXPECT_EQ(frame.codec_specific_info.generic_frame_info->spatial_id, 2);
  }

  frames = producer.ForceKeyFrame().SetNumInputFrames(1).Encode();
  ASSERT_THAT(frames, SizeIs(1));
  // Key-frame should be produced.
  EXPECT_EQ(frames[0].encoded_image._frameType, VideoFrameType::kVideoFrameKey);
  ASSERT_TRUE(frames[0].codec_specific_info.template_structure);
  ASSERT_TRUE(frames[0].codec_specific_info.generic_frame_info);
  EXPECT_EQ(frames[0].codec_specific_info.generic_frame_info->spatial_id, 2);

  frames = producer.SetNumInputFrames(num_frames_to_encode).Encode();
  ASSERT_THAT(frames, SizeIs(num_frames_to_encode));
  for (const auto& frame : frames) {
    EXPECT_EQ(frame.encoded_image._frameType, VideoFrameType::kVideoFrameDelta);
    EXPECT_FALSE(frame.codec_specific_info.template_structure);
    ASSERT_TRUE(frame.codec_specific_info.generic_frame_info);
    EXPECT_EQ(frame.codec_specific_info.generic_frame_info->spatial_id, 2);
  }

  // Enable the second layer back.
  // Allocate high bit rate to avoid frame dropping due to rate control.
  for (size_t tl_idx = 0; tl_idx < num_temporal_layers; ++tl_idx) {
    bitrate_allocation.SetBitrate(
        1, tl_idx, codec_settings.spatialLayers[0].targetBitrate * 1000 * 2);
  }
  encoder->SetRates(VideoEncoder::RateControlParameters(
      bitrate_allocation, codec_settings.maxFramerate));

  frames = producer.SetNumInputFrames(num_frames_to_encode).Encode();
  ASSERT_THAT(frames, SizeIs(num_frames_to_encode * 2));
  EXPECT_EQ(frames[0].encoded_image._frameType, VideoFrameType::kVideoFrameKey);
  EXPECT_TRUE(frames[0].codec_specific_info.template_structure);
  ASSERT_TRUE(frames[0].codec_specific_info.generic_frame_info);
  EXPECT_EQ(frames[0].codec_specific_info.generic_frame_info->spatial_id, 1);
  for (size_t i = 1; i < frames.size(); ++i) {
    EXPECT_EQ(frames[i].encoded_image._frameType,
              VideoFrameType::kVideoFrameDelta);
    EXPECT_FALSE(frames[i].codec_specific_info.template_structure);
    ASSERT_TRUE(frames[i].codec_specific_info.generic_frame_info);
    EXPECT_EQ(frames[i].codec_specific_info.generic_frame_info->spatial_id,
              1 + static_cast<int>(i % 2));
  }

  // Enable the first layer back.
  // Allocate high bit rate to avoid frame dropping due to rate control.
  for (size_t tl_idx = 0; tl_idx < num_temporal_layers; ++tl_idx) {
    bitrate_allocation.SetBitrate(
        0, tl_idx, codec_settings.spatialLayers[1].targetBitrate * 1000 * 2);
  }
  encoder->SetRates(VideoEncoder::RateControlParameters(
      bitrate_allocation, codec_settings.maxFramerate));

  frames = producer.SetNumInputFrames(num_frames_to_encode).Encode();
  ASSERT_THAT(frames, SizeIs(num_frames_to_encode * 3));
  EXPECT_TRUE(frames[0].codec_specific_info.template_structure);
  ASSERT_TRUE(frames[0].codec_specific_info.generic_frame_info);
  EXPECT_EQ(frames[0].codec_specific_info.generic_frame_info->spatial_id, 0);
  for (size_t i = 1; i < frames.size(); ++i) {
    EXPECT_FALSE(frames[i].codec_specific_info.template_structure);
    ASSERT_TRUE(frames[i].codec_specific_info.generic_frame_info);
    EXPECT_EQ(frames[i].codec_specific_info.generic_frame_info->spatial_id,
              static_cast<int>(i % 3));
  }
}

TEST_F(TestVp9Impl, DisableEnableBaseLayerTriggersKeyFrameForScreenshare) {
  // Configure encoder to produce N spatial layers. Encode frames for all
  // layers. Then disable all but the last layer. Then reenable all back again.
  const size_t num_spatial_layers = 3;
  const size_t num_frames_to_encode = 5;

  ConfigureSvc(codec_settings_, num_spatial_layers);
  codec_settings_.SetFrameDropEnabled(false);
  codec_settings_.mode = VideoCodecMode::kScreensharing;
  codec_settings_.VP9()->interLayerPred = InterLayerPredMode::kOn;
  codec_settings_.VP9()->flexibleMode = true;

  EXPECT_EQ(WEBRTC_VIDEO_CODEC_OK,
            encoder_->InitEncode(&codec_settings_, kSettings));

  VideoBitrateAllocation bitrate_allocation;
  for (size_t sl_idx = 0; sl_idx < num_spatial_layers; ++sl_idx) {
    // Allocate high bit rate to avoid frame dropping due to rate control.
    bitrate_allocation.SetBitrate(
        sl_idx, 0,
        codec_settings_.spatialLayers[sl_idx].targetBitrate * 1000 * 2);
  }
  encoder_->SetRates(VideoEncoder::RateControlParameters(
      bitrate_allocation, codec_settings_.maxFramerate));

  for (size_t frame_num = 0; frame_num < num_frames_to_encode; ++frame_num) {
    SetWaitForEncodedFramesThreshold(num_spatial_layers);
    EXPECT_EQ(WEBRTC_VIDEO_CODEC_OK,
              encoder_->Encode(NextInputFrame(), nullptr));
    std::vector<EncodedImage> encoded_frame;
    std::vector<CodecSpecificInfo> codec_specific_info;
    ASSERT_TRUE(WaitForEncodedFrames(&encoded_frame, &codec_specific_info));
    EXPECT_EQ(codec_specific_info[0].codecSpecific.VP9.ss_data_available,
              frame_num == 0);
  }

  // Disable all but top layer.
  for (size_t sl_idx = 0; sl_idx < num_spatial_layers - 1; ++sl_idx) {
    bitrate_allocation.SetBitrate(sl_idx, 0, 0);
  }
  encoder_->SetRates(VideoEncoder::RateControlParameters(
      bitrate_allocation, codec_settings_.maxFramerate));

  for (size_t frame_num = 0; frame_num < num_frames_to_encode; ++frame_num) {
    SetWaitForEncodedFramesThreshold(1);
    EXPECT_EQ(WEBRTC_VIDEO_CODEC_OK,
              encoder_->Encode(NextInputFrame(), nullptr));
    std::vector<EncodedImage> encoded_frame;
    std::vector<CodecSpecificInfo> codec_specific_info;
    ASSERT_TRUE(WaitForEncodedFrames(&encoded_frame, &codec_specific_info));
    // SS available immediatly after switching off.
    EXPECT_EQ(codec_specific_info[0].codecSpecific.VP9.ss_data_available,
              frame_num == 0);
    // No key-frames generated for disabling layers.
    EXPECT_EQ(encoded_frame[0]._frameType, VideoFrameType::kVideoFrameDelta);
    EXPECT_EQ(encoded_frame[0].SpatialIndex().value_or(-1), 2);
  }

  // Force key-frame.
  std::vector<VideoFrameType> frame_types = {VideoFrameType::kVideoFrameKey};
  SetWaitForEncodedFramesThreshold(1);
  EXPECT_EQ(WEBRTC_VIDEO_CODEC_OK,
            encoder_->Encode(NextInputFrame(), &frame_types));
  std::vector<EncodedImage> encoded_frame;
  std::vector<CodecSpecificInfo> codec_specific_info;
  ASSERT_TRUE(WaitForEncodedFrames(&encoded_frame, &codec_specific_info));
  // Key-frame should be produced.
  EXPECT_EQ(encoded_frame[0]._frameType, VideoFrameType::kVideoFrameKey);

  // Enable the second layer back.
  // Allocate high bit rate to avoid frame dropping due to rate control.
  bitrate_allocation.SetBitrate(
      1, 0, codec_settings_.spatialLayers[0].targetBitrate * 1000 * 2);
  encoder_->SetRates(VideoEncoder::RateControlParameters(
      bitrate_allocation, codec_settings_.maxFramerate));

  for (size_t frame_num = 0; frame_num < num_frames_to_encode; ++frame_num) {
    SetWaitForEncodedFramesThreshold(2);
    EXPECT_EQ(WEBRTC_VIDEO_CODEC_OK,
              encoder_->Encode(NextInputFrame(), nullptr));
    std::vector<EncodedImage> encoded_frame;
    std::vector<CodecSpecificInfo> codec_specific_info;
    ASSERT_TRUE(WaitForEncodedFrames(&encoded_frame, &codec_specific_info));
    ASSERT_EQ(encoded_frame.size(), 2u);
    // SS available immediatly after switching on.
    EXPECT_EQ(codec_specific_info[0].codecSpecific.VP9.ss_data_available,
              frame_num == 0);
    // Keyframe should be generated when enabling lower layers.
    const VideoFrameType expected_type = frame_num == 0
                                             ? VideoFrameType::kVideoFrameKey
                                             : VideoFrameType::kVideoFrameDelta;
    EXPECT_EQ(encoded_frame[0]._frameType, expected_type);
    EXPECT_EQ(encoded_frame[0].SpatialIndex().value_or(-1), 1);
    EXPECT_EQ(encoded_frame[1].SpatialIndex().value_or(-1), 2);
  }

  // Enable the first layer back.
  // Allocate high bit rate to avoid frame dropping due to rate control.
  bitrate_allocation.SetBitrate(
      0, 0, codec_settings_.spatialLayers[1].targetBitrate * 1000 * 2);
  encoder_->SetRates(VideoEncoder::RateControlParameters(
      bitrate_allocation, codec_settings_.maxFramerate));

  for (size_t frame_num = 0; frame_num < num_frames_to_encode; ++frame_num) {
    SetWaitForEncodedFramesThreshold(num_spatial_layers);
    EXPECT_EQ(WEBRTC_VIDEO_CODEC_OK,
              encoder_->Encode(NextInputFrame(), nullptr));
    std::vector<EncodedImage> encoded_frame;
    std::vector<CodecSpecificInfo> codec_specific_info;
    ASSERT_TRUE(WaitForEncodedFrames(&encoded_frame, &codec_specific_info));
    ASSERT_EQ(encoded_frame.size(), 3u);
    // SS available immediatly after switching on.
    EXPECT_EQ(codec_specific_info[0].codecSpecific.VP9.ss_data_available,
              frame_num == 0);
    // Keyframe should be generated when enabling lower layers.
    const VideoFrameType expected_type = frame_num == 0
                                             ? VideoFrameType::kVideoFrameKey
                                             : VideoFrameType::kVideoFrameDelta;
    EXPECT_EQ(encoded_frame[0]._frameType, expected_type);
  }
}

TEST_F(TestVp9Impl, EndOfPicture) {
  const size_t num_spatial_layers = 2;
  ConfigureSvc(codec_settings_, num_spatial_layers);

  EXPECT_EQ(WEBRTC_VIDEO_CODEC_OK,
            encoder_->InitEncode(&codec_settings_, kSettings));

  // Encode both base and upper layers. Check that end-of-superframe flag is
  // set on upper layer frame but not on base layer frame.
  VideoBitrateAllocation bitrate_allocation;
  bitrate_allocation.SetBitrate(
      0, 0, codec_settings_.spatialLayers[0].targetBitrate * 1000);
  bitrate_allocation.SetBitrate(
      1, 0, codec_settings_.spatialLayers[1].targetBitrate * 1000);
  encoder_->SetRates(VideoEncoder::RateControlParameters(
      bitrate_allocation, codec_settings_.maxFramerate));
  SetWaitForEncodedFramesThreshold(2);
  EXPECT_EQ(WEBRTC_VIDEO_CODEC_OK, encoder_->Encode(NextInputFrame(), nullptr));

  std::vector<EncodedImage> frames;
  std::vector<CodecSpecificInfo> codec_specific;
  ASSERT_TRUE(WaitForEncodedFrames(&frames, &codec_specific));
  EXPECT_FALSE(codec_specific[0].end_of_picture);
  EXPECT_TRUE(codec_specific[1].end_of_picture);

  // Encode only base layer. Check that end-of-superframe flag is
  // set on base layer frame.
  bitrate_allocation.SetBitrate(1, 0, 0);
  encoder_->SetRates(VideoEncoder::RateControlParameters(
      bitrate_allocation, codec_settings_.maxFramerate));
  EXPECT_EQ(WEBRTC_VIDEO_CODEC_OK,
            encoder_->InitEncode(&codec_settings_, kSettings));

  SetWaitForEncodedFramesThreshold(1);
  EXPECT_EQ(WEBRTC_VIDEO_CODEC_OK, encoder_->Encode(NextInputFrame(), nullptr));

  ASSERT_TRUE(WaitForEncodedFrames(&frames, &codec_specific));
  EXPECT_FALSE(frames[0].SpatialIndex());
  EXPECT_TRUE(codec_specific[0].end_of_picture);
}

TEST_F(TestVp9Impl, InterLayerPred) {
  const size_t num_spatial_layers = 2;
  ConfigureSvc(codec_settings_, num_spatial_layers);
  codec_settings_.SetFrameDropEnabled(false);

  VideoBitrateAllocation bitrate_allocation;
  for (size_t i = 0; i < num_spatial_layers; ++i) {
    bitrate_allocation.SetBitrate(
        i, 0, codec_settings_.spatialLayers[i].targetBitrate * 1000);
  }

  const std::vector<InterLayerPredMode> inter_layer_pred_modes = {
      InterLayerPredMode::kOff, InterLayerPredMode::kOn,
      InterLayerPredMode::kOnKeyPic};

  for (const InterLayerPredMode inter_layer_pred : inter_layer_pred_modes) {
    codec_settings_.VP9()->interLayerPred = inter_layer_pred;
    EXPECT_EQ(WEBRTC_VIDEO_CODEC_OK,
              encoder_->InitEncode(&codec_settings_, kSettings));

    encoder_->SetRates(VideoEncoder::RateControlParameters(
        bitrate_allocation, codec_settings_.maxFramerate));

    SetWaitForEncodedFramesThreshold(2);
    EXPECT_EQ(WEBRTC_VIDEO_CODEC_OK,
              encoder_->Encode(NextInputFrame(), nullptr));

    std::vector<EncodedImage> frames;
    std::vector<CodecSpecificInfo> codec_specific;
    ASSERT_TRUE(WaitForEncodedFrames(&frames, &codec_specific));

    // Key frame.
    ASSERT_EQ(frames[0].SpatialIndex(), 0);
    ASSERT_FALSE(codec_specific[0].codecSpecific.VP9.inter_pic_predicted);
    EXPECT_FALSE(codec_specific[0].codecSpecific.VP9.inter_layer_predicted);
    EXPECT_EQ(codec_specific[0].codecSpecific.VP9.non_ref_for_inter_layer_pred,
              inter_layer_pred == InterLayerPredMode::kOff);
    EXPECT_TRUE(codec_specific[0].codecSpecific.VP9.ss_data_available);

    ASSERT_EQ(frames[1].SpatialIndex(), 1);
    ASSERT_FALSE(codec_specific[1].codecSpecific.VP9.inter_pic_predicted);
    EXPECT_EQ(codec_specific[1].codecSpecific.VP9.inter_layer_predicted,
              inter_layer_pred == InterLayerPredMode::kOn ||
                  inter_layer_pred == InterLayerPredMode::kOnKeyPic);
    EXPECT_EQ(codec_specific[1].codecSpecific.VP9.ss_data_available,
              inter_layer_pred == InterLayerPredMode::kOff);
    EXPECT_TRUE(
        codec_specific[1].codecSpecific.VP9.non_ref_for_inter_layer_pred);

    // Delta frame.
    SetWaitForEncodedFramesThreshold(2);
    EXPECT_EQ(WEBRTC_VIDEO_CODEC_OK,
              encoder_->Encode(NextInputFrame(), nullptr));
    ASSERT_TRUE(WaitForEncodedFrames(&frames, &codec_specific));

    ASSERT_EQ(frames[0].SpatialIndex(), 0);
    ASSERT_TRUE(codec_specific[0].codecSpecific.VP9.inter_pic_predicted);
    EXPECT_FALSE(codec_specific[0].codecSpecific.VP9.inter_layer_predicted);
    EXPECT_EQ(codec_specific[0].codecSpecific.VP9.non_ref_for_inter_layer_pred,
              inter_layer_pred != InterLayerPredMode::kOn);
    EXPECT_FALSE(codec_specific[0].codecSpecific.VP9.ss_data_available);

    ASSERT_EQ(frames[1].SpatialIndex(), 1);
    ASSERT_TRUE(codec_specific[1].codecSpecific.VP9.inter_pic_predicted);
    EXPECT_EQ(codec_specific[1].codecSpecific.VP9.inter_layer_predicted,
              inter_layer_pred == InterLayerPredMode::kOn);
    EXPECT_TRUE(
        codec_specific[1].codecSpecific.VP9.non_ref_for_inter_layer_pred);
    EXPECT_FALSE(codec_specific[1].codecSpecific.VP9.ss_data_available);
  }
}

TEST_F(TestVp9Impl,
       EnablingUpperLayerTriggersKeyFrameIfInterLayerPredIsDisabled) {
  const size_t num_spatial_layers = 3;
  const size_t num_frames_to_encode = 2;

  ConfigureSvc(codec_settings_, num_spatial_layers);
  codec_settings_.SetFrameDropEnabled(false);

  const std::vector<InterLayerPredMode> inter_layer_pred_modes = {
      InterLayerPredMode::kOff, InterLayerPredMode::kOn,
      InterLayerPredMode::kOnKeyPic};

  for (const InterLayerPredMode inter_layer_pred : inter_layer_pred_modes) {
    codec_settings_.VP9()->interLayerPred = inter_layer_pred;
    EXPECT_EQ(WEBRTC_VIDEO_CODEC_OK,
              encoder_->InitEncode(&codec_settings_, kSettings));

    VideoBitrateAllocation bitrate_allocation;
    for (size_t sl_idx = 0; sl_idx < num_spatial_layers; ++sl_idx) {
      bitrate_allocation.SetBitrate(
          sl_idx, 0,
          codec_settings_.spatialLayers[sl_idx].targetBitrate * 1000);
      encoder_->SetRates(VideoEncoder::RateControlParameters(
          bitrate_allocation, codec_settings_.maxFramerate));

      for (size_t frame_num = 0; frame_num < num_frames_to_encode;
           ++frame_num) {
        SetWaitForEncodedFramesThreshold(sl_idx + 1);
        EXPECT_EQ(WEBRTC_VIDEO_CODEC_OK,
                  encoder_->Encode(NextInputFrame(), nullptr));
        std::vector<EncodedImage> encoded_frame;
        std::vector<CodecSpecificInfo> codec_specific_info;
        ASSERT_TRUE(WaitForEncodedFrames(&encoded_frame, &codec_specific_info));

        const bool is_first_upper_layer_frame = (sl_idx > 0 && frame_num == 0);
        if (is_first_upper_layer_frame) {
          if (inter_layer_pred == InterLayerPredMode::kOn) {
            EXPECT_EQ(encoded_frame[0]._frameType,
                      VideoFrameType::kVideoFrameDelta);
          } else {
            EXPECT_EQ(encoded_frame[0]._frameType,
                      VideoFrameType::kVideoFrameKey);
          }
        } else if (sl_idx == 0 && frame_num == 0) {
          EXPECT_EQ(encoded_frame[0]._frameType,
                    VideoFrameType::kVideoFrameKey);
        } else {
          for (size_t i = 0; i <= sl_idx; ++i) {
            EXPECT_EQ(encoded_frame[i]._frameType,
                      VideoFrameType::kVideoFrameDelta);
          }
        }
      }
    }
  }
}

TEST_F(TestVp9Impl,
       EnablingUpperLayerUnsetsInterPicPredictedInInterlayerPredModeOn) {
  const size_t num_spatial_layers = 3;
  const size_t num_frames_to_encode = 2;

  ConfigureSvc(codec_settings_, num_spatial_layers);
  codec_settings_.SetFrameDropEnabled(false);
  codec_settings_.VP9()->flexibleMode = false;

  const std::vector<InterLayerPredMode> inter_layer_pred_modes = {
      InterLayerPredMode::kOff, InterLayerPredMode::kOn,
      InterLayerPredMode::kOnKeyPic};

  for (const InterLayerPredMode inter_layer_pred : inter_layer_pred_modes) {
    codec_settings_.VP9()->interLayerPred = inter_layer_pred;
    EXPECT_EQ(WEBRTC_VIDEO_CODEC_OK,
              encoder_->InitEncode(&codec_settings_, kSettings));

    VideoBitrateAllocation bitrate_allocation;
    for (size_t sl_idx = 0; sl_idx < num_spatial_layers; ++sl_idx) {
      bitrate_allocation.SetBitrate(
          sl_idx, 0,
          codec_settings_.spatialLayers[sl_idx].targetBitrate * 1000);
      encoder_->SetRates(VideoEncoder::RateControlParameters(
          bitrate_allocation, codec_settings_.maxFramerate));

      for (size_t frame_num = 0; frame_num < num_frames_to_encode;
           ++frame_num) {
        SetWaitForEncodedFramesThreshold(sl_idx + 1);
        EXPECT_EQ(WEBRTC_VIDEO_CODEC_OK,
                  encoder_->Encode(NextInputFrame(), nullptr));
        std::vector<EncodedImage> encoded_frame;
        std::vector<CodecSpecificInfo> codec_specific_info;
        ASSERT_TRUE(WaitForEncodedFrames(&encoded_frame, &codec_specific_info));

        ASSERT_EQ(codec_specific_info.size(), sl_idx + 1);

        for (size_t i = 0; i <= sl_idx; ++i) {
          const bool is_keyframe =
              encoded_frame[0]._frameType == VideoFrameType::kVideoFrameKey;
          const bool is_first_upper_layer_frame =
              (i == sl_idx && frame_num == 0);
          // Interframe references are there, unless it's a keyframe,
          // or it's a first activated frame in a upper layer
          const bool expect_no_references =
              is_keyframe || (is_first_upper_layer_frame &&
                              inter_layer_pred == InterLayerPredMode::kOn);
          EXPECT_EQ(
              codec_specific_info[i].codecSpecific.VP9.inter_pic_predicted,
              !expect_no_references);
        }
      }
    }
  }
}

TEST_F(TestVp9Impl, EnablingDisablingUpperLayerInTheSameGof) {
  const size_t num_spatial_layers = 2;
  const size_t num_temporal_layers = 2;

  ConfigureSvc(codec_settings_, num_spatial_layers, num_temporal_layers);
  codec_settings_.SetFrameDropEnabled(false);
  codec_settings_.VP9()->flexibleMode = false;

  codec_settings_.VP9()->interLayerPred = InterLayerPredMode::kOn;
  EXPECT_EQ(WEBRTC_VIDEO_CODEC_OK,
            encoder_->InitEncode(&codec_settings_, kSettings));

  VideoBitrateAllocation bitrate_allocation;

  // Enable both spatial and both temporal layers.
  bitrate_allocation.SetBitrate(
      0, 0, codec_settings_.spatialLayers[0].targetBitrate * 1000 / 2);
  bitrate_allocation.SetBitrate(
      0, 1, codec_settings_.spatialLayers[0].targetBitrate * 1000 / 2);
  bitrate_allocation.SetBitrate(
      1, 0, codec_settings_.spatialLayers[1].targetBitrate * 1000 / 2);
  bitrate_allocation.SetBitrate(
      1, 1, codec_settings_.spatialLayers[1].targetBitrate * 1000 / 2);
  encoder_->SetRates(VideoEncoder::RateControlParameters(
      bitrate_allocation, codec_settings_.maxFramerate));

  std::vector<EncodedImage> encoded_frame;
  std::vector<CodecSpecificInfo> codec_specific_info;

  // Encode 3 frames.
  for (int i = 0; i < 3; ++i) {
    SetWaitForEncodedFramesThreshold(2);
    EXPECT_EQ(WEBRTC_VIDEO_CODEC_OK,
              encoder_->Encode(NextInputFrame(), nullptr));
    ASSERT_TRUE(WaitForEncodedFrames(&encoded_frame, &codec_specific_info));
    ASSERT_EQ(codec_specific_info.size(), 2u);
  }

  // Disable SL1 layer.
  bitrate_allocation.SetBitrate(1, 0, 0);
  bitrate_allocation.SetBitrate(1, 1, 0);
  encoder_->SetRates(VideoEncoder::RateControlParameters(
      bitrate_allocation, codec_settings_.maxFramerate));

  // Encode 1 frame.
  SetWaitForEncodedFramesThreshold(1);
  EXPECT_EQ(WEBRTC_VIDEO_CODEC_OK, encoder_->Encode(NextInputFrame(), nullptr));
  ASSERT_TRUE(WaitForEncodedFrames(&encoded_frame, &codec_specific_info));
  ASSERT_EQ(codec_specific_info.size(), 1u);
  EXPECT_EQ(encoded_frame[0]._frameType, VideoFrameType::kVideoFrameDelta);
  EXPECT_EQ(codec_specific_info[0].codecSpecific.VP9.temporal_idx, 1);
  EXPECT_EQ(codec_specific_info[0].codecSpecific.VP9.inter_pic_predicted, true);

  // Enable SL1 layer.
  bitrate_allocation.SetBitrate(
      1, 0, codec_settings_.spatialLayers[1].targetBitrate * 1000 / 2);
  bitrate_allocation.SetBitrate(
      1, 1, codec_settings_.spatialLayers[1].targetBitrate * 1000 / 2);
  encoder_->SetRates(VideoEncoder::RateControlParameters(
      bitrate_allocation, codec_settings_.maxFramerate));

  // Encode 1 frame.
  SetWaitForEncodedFramesThreshold(2);
  EXPECT_EQ(WEBRTC_VIDEO_CODEC_OK, encoder_->Encode(NextInputFrame(), nullptr));
  ASSERT_TRUE(WaitForEncodedFrames(&encoded_frame, &codec_specific_info));
  ASSERT_EQ(codec_specific_info.size(), 2u);
  EXPECT_EQ(encoded_frame[0]._frameType, VideoFrameType::kVideoFrameDelta);
  EXPECT_EQ(codec_specific_info[0].codecSpecific.VP9.temporal_idx, 0);
  EXPECT_EQ(codec_specific_info[0].codecSpecific.VP9.inter_pic_predicted, true);
  EXPECT_EQ(codec_specific_info[1].codecSpecific.VP9.inter_pic_predicted, true);
}

TEST_F(TestVp9Impl, EnablingDisablingUpperLayerAccrossGof) {
  const size_t num_spatial_layers = 2;
  const size_t num_temporal_layers = 2;

  ConfigureSvc(codec_settings_, num_spatial_layers, num_temporal_layers);
  codec_settings_.SetFrameDropEnabled(false);
  codec_settings_.VP9()->flexibleMode = false;

  codec_settings_.VP9()->interLayerPred = InterLayerPredMode::kOn;
  EXPECT_EQ(WEBRTC_VIDEO_CODEC_OK,
            encoder_->InitEncode(&codec_settings_, kSettings));

  VideoBitrateAllocation bitrate_allocation;

  // Enable both spatial and both temporal layers.
  bitrate_allocation.SetBitrate(
      0, 0, codec_settings_.spatialLayers[0].targetBitrate * 1000 / 2);
  bitrate_allocation.SetBitrate(
      0, 1, codec_settings_.spatialLayers[0].targetBitrate * 1000 / 2);
  bitrate_allocation.SetBitrate(
      1, 0, codec_settings_.spatialLayers[1].targetBitrate * 1000 / 2);
  bitrate_allocation.SetBitrate(
      1, 1, codec_settings_.spatialLayers[1].targetBitrate * 1000 / 2);
  encoder_->SetRates(VideoEncoder::RateControlParameters(
      bitrate_allocation, codec_settings_.maxFramerate));

  std::vector<EncodedImage> encoded_frame;
  std::vector<CodecSpecificInfo> codec_specific_info;

  // Encode 3 frames.
  for (int i = 0; i < 3; ++i) {
    SetWaitForEncodedFramesThreshold(2);
    EXPECT_EQ(WEBRTC_VIDEO_CODEC_OK,
              encoder_->Encode(NextInputFrame(), nullptr));
    ASSERT_TRUE(WaitForEncodedFrames(&encoded_frame, &codec_specific_info));
    ASSERT_EQ(codec_specific_info.size(), 2u);
  }

  // Disable SL1 layer.
  bitrate_allocation.SetBitrate(1, 0, 0);
  bitrate_allocation.SetBitrate(1, 1, 0);
  encoder_->SetRates(VideoEncoder::RateControlParameters(
      bitrate_allocation, codec_settings_.maxFramerate));

  // Encode 11 frames. More than Gof length 2, and odd to end at TL1 frame.
  for (int i = 0; i < 11; ++i) {
    SetWaitForEncodedFramesThreshold(1);
    EXPECT_EQ(WEBRTC_VIDEO_CODEC_OK,
              encoder_->Encode(NextInputFrame(), nullptr));
    ASSERT_TRUE(WaitForEncodedFrames(&encoded_frame, &codec_specific_info));
    ASSERT_EQ(codec_specific_info.size(), 1u);
    EXPECT_EQ(encoded_frame[0]._frameType, VideoFrameType::kVideoFrameDelta);
    EXPECT_EQ(codec_specific_info[0].codecSpecific.VP9.temporal_idx, 1 - i % 2);
    EXPECT_EQ(codec_specific_info[0].codecSpecific.VP9.inter_pic_predicted,
              true);
  }

  // Enable SL1 layer.
  bitrate_allocation.SetBitrate(
      1, 0, codec_settings_.spatialLayers[1].targetBitrate * 1000 / 2);
  bitrate_allocation.SetBitrate(
      1, 1, codec_settings_.spatialLayers[1].targetBitrate * 1000 / 2);
  encoder_->SetRates(VideoEncoder::RateControlParameters(
      bitrate_allocation, codec_settings_.maxFramerate));

  // Encode 1 frame.
  SetWaitForEncodedFramesThreshold(2);
  EXPECT_EQ(WEBRTC_VIDEO_CODEC_OK, encoder_->Encode(NextInputFrame(), nullptr));
  ASSERT_TRUE(WaitForEncodedFrames(&encoded_frame, &codec_specific_info));
  ASSERT_EQ(codec_specific_info.size(), 2u);
  EXPECT_EQ(encoded_frame[0]._frameType, VideoFrameType::kVideoFrameDelta);
  EXPECT_EQ(codec_specific_info[0].codecSpecific.VP9.temporal_idx, 0);
  EXPECT_EQ(codec_specific_info[0].codecSpecific.VP9.inter_pic_predicted, true);
  EXPECT_EQ(codec_specific_info[1].codecSpecific.VP9.inter_pic_predicted,
            false);
}

TEST_F(TestVp9Impl, EnablingNewLayerInScreenshareForcesAllLayersWithSS) {
  const size_t num_spatial_layers = 3;
  // Chosen by hand, the 2nd frame is dropped with configured per-layer max
  // framerate.
  const size_t num_frames_to_encode_before_drop = 1;

  codec_settings_.maxFramerate = 30;
  ConfigureSvc(codec_settings_, num_spatial_layers);
  codec_settings_.spatialLayers[0].maxFramerate = 5.0;
  // use 30 for the SL 1 instead of 10, so even if SL 0 frame is dropped due to
  // framerate capping we would still get back at least a middle layer. It
  // simplifies the test.
  codec_settings_.spatialLayers[1].maxFramerate = 30.0;
  codec_settings_.spatialLayers[2].maxFramerate = 30.0;
  codec_settings_.SetFrameDropEnabled(false);
  codec_settings_.mode = VideoCodecMode::kScreensharing;
  codec_settings_.VP9()->interLayerPred = InterLayerPredMode::kOn;
  codec_settings_.VP9()->flexibleMode = true;
  EXPECT_EQ(WEBRTC_VIDEO_CODEC_OK,
            encoder_->InitEncode(&codec_settings_, kSettings));

  // Enable all but the last layer.
  VideoBitrateAllocation bitrate_allocation;
  for (size_t sl_idx = 0; sl_idx < num_spatial_layers - 1; ++sl_idx) {
    bitrate_allocation.SetBitrate(
        sl_idx, 0, codec_settings_.spatialLayers[sl_idx].targetBitrate * 1000);
  }
  encoder_->SetRates(VideoEncoder::RateControlParameters(
      bitrate_allocation, codec_settings_.maxFramerate));

  // Encode enough frames to force drop due to framerate capping.
  for (size_t frame_num = 0; frame_num < num_frames_to_encode_before_drop;
       ++frame_num) {
    SetWaitForEncodedFramesThreshold(num_spatial_layers - 1);
    EXPECT_EQ(WEBRTC_VIDEO_CODEC_OK,
              encoder_->Encode(NextInputFrame(), nullptr));
    std::vector<EncodedImage> encoded_frames;
    std::vector<CodecSpecificInfo> codec_specific_info;
    ASSERT_TRUE(WaitForEncodedFrames(&encoded_frames, &codec_specific_info));
  }

  // Enable the last layer.
  bitrate_allocation.SetBitrate(
      num_spatial_layers - 1, 0,
      codec_settings_.spatialLayers[num_spatial_layers - 1].targetBitrate *
          1000);
  encoder_->SetRates(VideoEncoder::RateControlParameters(
      bitrate_allocation, codec_settings_.maxFramerate));

  // All layers are encoded, even though frame dropping should happen.
  SetWaitForEncodedFramesThreshold(num_spatial_layers);
  EXPECT_EQ(WEBRTC_VIDEO_CODEC_OK, encoder_->Encode(NextInputFrame(), nullptr));
  // Now all 3 layers should be encoded.
  std::vector<EncodedImage> encoded_frames;
  std::vector<CodecSpecificInfo> codec_specific_info;
  ASSERT_TRUE(WaitForEncodedFrames(&encoded_frames, &codec_specific_info));
  EXPECT_EQ(encoded_frames.size(), 3u);
  // Scalability structure has to be triggered.
  EXPECT_TRUE(codec_specific_info[0].codecSpecific.VP9.ss_data_available);
}

TEST_F(TestVp9Impl, ScreenshareFrameDropping) {
  const int num_spatial_layers = 3;
  const int num_frames_to_detect_drops = 2;

  codec_settings_.maxFramerate = 30;
  ConfigureSvc(codec_settings_, num_spatial_layers);
  // use 30 for the SL0 and SL1 because it simplifies the test.
  codec_settings_.spatialLayers[0].maxFramerate = 30.0;
  codec_settings_.spatialLayers[1].maxFramerate = 30.0;
  codec_settings_.spatialLayers[2].maxFramerate = 30.0;
  codec_settings_.SetFrameDropEnabled(true);
  codec_settings_.mode = VideoCodecMode::kScreensharing;
  codec_settings_.VP9()->interLayerPred = InterLayerPredMode::kOn;
  codec_settings_.VP9()->flexibleMode = true;
  EXPECT_EQ(WEBRTC_VIDEO_CODEC_OK,
            encoder_->InitEncode(&codec_settings_, kSettings));

  // Enable all but the last layer.
  VideoBitrateAllocation bitrate_allocation;
  // Very low bitrate for the lowest spatial layer to ensure rate-control drops.
  bitrate_allocation.SetBitrate(0, 0, 1000);
  bitrate_allocation.SetBitrate(
      1, 0, codec_settings_.spatialLayers[1].targetBitrate * 1000);
  // Disable highest layer.
  bitrate_allocation.SetBitrate(2, 0, 0);

  encoder_->SetRates(VideoEncoder::RateControlParameters(
      bitrate_allocation, codec_settings_.maxFramerate));

  bool frame_dropped = false;
  // Encode enough frames to force drop due to rate-control.
  for (size_t frame_num = 0; frame_num < num_frames_to_detect_drops;
       ++frame_num) {
    SetWaitForEncodedFramesThreshold(1);
    EXPECT_EQ(WEBRTC_VIDEO_CODEC_OK,
              encoder_->Encode(NextInputFrame(), nullptr));
    std::vector<EncodedImage> encoded_frames;
    std::vector<CodecSpecificInfo> codec_specific_info;
    ASSERT_TRUE(WaitForEncodedFrames(&encoded_frames, &codec_specific_info));
    EXPECT_LE(encoded_frames.size(), 2u);
    EXPECT_GE(encoded_frames.size(), 1u);
    if (encoded_frames.size() == 1) {
      frame_dropped = true;
      // Dropped frame is on the SL0.
      EXPECT_EQ(encoded_frames[0].SpatialIndex(), 1);
    }
  }
  EXPECT_TRUE(frame_dropped);

  // Enable the last layer.
  bitrate_allocation.SetBitrate(
      2, 0, codec_settings_.spatialLayers[2].targetBitrate * 1000);
  encoder_->SetRates(VideoEncoder::RateControlParameters(
      bitrate_allocation, codec_settings_.maxFramerate));
  SetWaitForEncodedFramesThreshold(1);
  EXPECT_EQ(WEBRTC_VIDEO_CODEC_OK, encoder_->Encode(NextInputFrame(), nullptr));
  std::vector<EncodedImage> encoded_frames;
  std::vector<CodecSpecificInfo> codec_specific_info;
  ASSERT_TRUE(WaitForEncodedFrames(&encoded_frames, &codec_specific_info));
  // No drop allowed.
  EXPECT_EQ(encoded_frames.size(), 3u);

  // Verify that frame-dropping is re-enabled back.
  frame_dropped = false;
  // Encode enough frames to force drop due to rate-control.
  for (size_t frame_num = 0; frame_num < num_frames_to_detect_drops;
       ++frame_num) {
    SetWaitForEncodedFramesThreshold(1);
    EXPECT_EQ(WEBRTC_VIDEO_CODEC_OK,
              encoder_->Encode(NextInputFrame(), nullptr));
    std::vector<EncodedImage> encoded_frames;
    std::vector<CodecSpecificInfo> codec_specific_info;
    ASSERT_TRUE(WaitForEncodedFrames(&encoded_frames, &codec_specific_info));
    EXPECT_LE(encoded_frames.size(), 3u);
    EXPECT_GE(encoded_frames.size(), 2u);
    if (encoded_frames.size() == 2) {
      frame_dropped = true;
      // Dropped frame is on the SL0.
      EXPECT_EQ(encoded_frames[0].SpatialIndex(), 1);
      EXPECT_EQ(encoded_frames[1].SpatialIndex(), 2);
    }
  }
  EXPECT_TRUE(frame_dropped);
}

TEST_F(TestVp9Impl, RemovingLayerIsNotDelayedInScreenshareAndAddsSsInfo) {
  const size_t num_spatial_layers = 3;
  // Chosen by hand, the 2nd frame is dropped with configured per-layer max
  // framerate.
  const size_t num_frames_to_encode_before_drop = 1;
  // Chosen by hand, exactly 5 frames are dropped for input fps=30 and max
  // framerate = 5.
  const size_t num_dropped_frames = 5;

  codec_settings_.maxFramerate = 30;
  ConfigureSvc(codec_settings_, num_spatial_layers);
  codec_settings_.spatialLayers[0].maxFramerate = 5.0;
  // use 30 for the SL 1 instead of 5, so even if SL 0 frame is dropped due to
  // framerate capping we would still get back at least a middle layer. It
  // simplifies the test.
  codec_settings_.spatialLayers[1].maxFramerate = 30.0;
  codec_settings_.spatialLayers[2].maxFramerate = 30.0;
  codec_settings_.SetFrameDropEnabled(false);
  codec_settings_.mode = VideoCodecMode::kScreensharing;
  codec_settings_.VP9()->interLayerPred = InterLayerPredMode::kOn;
  codec_settings_.VP9()->flexibleMode = true;
  EXPECT_EQ(WEBRTC_VIDEO_CODEC_OK,
            encoder_->InitEncode(&codec_settings_, kSettings));

  // All layers are enabled from the start.
  VideoBitrateAllocation bitrate_allocation;
  for (size_t sl_idx = 0; sl_idx < num_spatial_layers; ++sl_idx) {
    bitrate_allocation.SetBitrate(
        sl_idx, 0, codec_settings_.spatialLayers[sl_idx].targetBitrate * 1000);
  }
  encoder_->SetRates(VideoEncoder::RateControlParameters(
      bitrate_allocation, codec_settings_.maxFramerate));

  // Encode enough frames to force drop due to framerate capping.
  for (size_t frame_num = 0; frame_num < num_frames_to_encode_before_drop;
       ++frame_num) {
    SetWaitForEncodedFramesThreshold(num_spatial_layers);
    EXPECT_EQ(WEBRTC_VIDEO_CODEC_OK,
              encoder_->Encode(NextInputFrame(), nullptr));
    std::vector<EncodedImage> encoded_frames;
    std::vector<CodecSpecificInfo> codec_specific_info;
    ASSERT_TRUE(WaitForEncodedFrames(&encoded_frames, &codec_specific_info));
  }

  // Now the first layer should not have frames in it.
  for (size_t frame_num = 0; frame_num < num_dropped_frames - 2; ++frame_num) {
    SetWaitForEncodedFramesThreshold(2);
    EXPECT_EQ(WEBRTC_VIDEO_CODEC_OK,
              encoder_->Encode(NextInputFrame(), nullptr));
    // First layer is dropped due to frame rate cap. The last layer should not
    // be enabled yet.
    std::vector<EncodedImage> encoded_frames;
    std::vector<CodecSpecificInfo> codec_specific_info;
    ASSERT_TRUE(WaitForEncodedFrames(&encoded_frames, &codec_specific_info));
    // First layer is skipped.
    EXPECT_EQ(encoded_frames[0].SpatialIndex().value_or(-1), 1);
  }

  // Disable the last layer.
  bitrate_allocation.SetBitrate(num_spatial_layers - 1, 0, 0);
  encoder_->SetRates(VideoEncoder::RateControlParameters(
      bitrate_allocation, codec_settings_.maxFramerate));

  // Still expected to drop first layer. Last layer has to be disable also.
  for (size_t frame_num = num_dropped_frames - 2;
       frame_num < num_dropped_frames; ++frame_num) {
    // Expect back one frame.
    SetWaitForEncodedFramesThreshold(1);
    EXPECT_EQ(WEBRTC_VIDEO_CODEC_OK,
              encoder_->Encode(NextInputFrame(), nullptr));
    // First layer is dropped due to frame rate cap. The last layer should not
    // be enabled yet.
    std::vector<EncodedImage> encoded_frames;
    std::vector<CodecSpecificInfo> codec_specific_info;
    ASSERT_TRUE(WaitForEncodedFrames(&encoded_frames, &codec_specific_info));
    // First layer is skipped.
    EXPECT_EQ(encoded_frames[0].SpatialIndex().value_or(-1), 1);
    // No SS data on non-base spatial layer.
    EXPECT_FALSE(codec_specific_info[0].codecSpecific.VP9.ss_data_available);
  }

  SetWaitForEncodedFramesThreshold(2);
  EXPECT_EQ(WEBRTC_VIDEO_CODEC_OK, encoder_->Encode(NextInputFrame(), nullptr));
  std::vector<EncodedImage> encoded_frames;
  std::vector<CodecSpecificInfo> codec_specific_info;
  ASSERT_TRUE(WaitForEncodedFrames(&encoded_frames, &codec_specific_info));
  // First layer is not skipped now.
  EXPECT_EQ(encoded_frames[0].SpatialIndex().value_or(-1), 0);
  // SS data should be present.
  EXPECT_TRUE(codec_specific_info[0].codecSpecific.VP9.ss_data_available);
}

TEST_F(TestVp9Impl, DisableNewLayerInVideoDelaysSsInfoTillTL0) {
  const size_t num_spatial_layers = 3;
  const size_t num_temporal_layers = 2;
  // Chosen by hand, the 2nd frame is dropped with configured per-layer max
  // framerate.
  ConfigureSvc(codec_settings_, num_spatial_layers, num_temporal_layers);
  codec_settings_.SetFrameDropEnabled(false);
  codec_settings_.mode = VideoCodecMode::kRealtimeVideo;
  codec_settings_.VP9()->interLayerPred = InterLayerPredMode::kOnKeyPic;
  codec_settings_.VP9()->flexibleMode = false;
  EXPECT_EQ(WEBRTC_VIDEO_CODEC_OK,
            encoder_->InitEncode(&codec_settings_, kSettings));

  // Enable all the layers.
  VideoBitrateAllocation bitrate_allocation;
  for (size_t sl_idx = 0; sl_idx < num_spatial_layers; ++sl_idx) {
    for (size_t tl_idx = 0; tl_idx < num_temporal_layers; ++tl_idx) {
      bitrate_allocation.SetBitrate(
          sl_idx, tl_idx,
          codec_settings_.spatialLayers[sl_idx].targetBitrate * 1000 /
              num_temporal_layers);
    }
  }
  encoder_->SetRates(VideoEncoder::RateControlParameters(
      bitrate_allocation, codec_settings_.maxFramerate));

  std::vector<EncodedImage> encoded_frames;
  std::vector<CodecSpecificInfo> codec_specific_info;

  // Encode one TL0 frame
  SetWaitForEncodedFramesThreshold(num_spatial_layers);
  EXPECT_EQ(WEBRTC_VIDEO_CODEC_OK, encoder_->Encode(NextInputFrame(), nullptr));
  ASSERT_TRUE(WaitForEncodedFrames(&encoded_frames, &codec_specific_info));
  EXPECT_EQ(codec_specific_info[0].codecSpecific.VP9.temporal_idx, 0u);

  // Disable the last layer.
  for (size_t tl_idx = 0; tl_idx < num_temporal_layers; ++tl_idx) {
    bitrate_allocation.SetBitrate(num_spatial_layers - 1, tl_idx, 0);
  }
  encoder_->SetRates(VideoEncoder::RateControlParameters(
      bitrate_allocation, codec_settings_.maxFramerate));

  // Next is TL1 frame. The last layer is disabled immediately, but SS structure
  // is not provided here.
  SetWaitForEncodedFramesThreshold(num_spatial_layers - 1);
  EXPECT_EQ(WEBRTC_VIDEO_CODEC_OK, encoder_->Encode(NextInputFrame(), nullptr));
  ASSERT_TRUE(WaitForEncodedFrames(&encoded_frames, &codec_specific_info));
  EXPECT_EQ(codec_specific_info[0].codecSpecific.VP9.temporal_idx, 1u);
  EXPECT_FALSE(codec_specific_info[0].codecSpecific.VP9.ss_data_available);

  // Next is TL0 frame, which should have delayed SS structure.
  SetWaitForEncodedFramesThreshold(num_spatial_layers - 1);
  EXPECT_EQ(WEBRTC_VIDEO_CODEC_OK, encoder_->Encode(NextInputFrame(), nullptr));
  ASSERT_TRUE(WaitForEncodedFrames(&encoded_frames, &codec_specific_info));
  EXPECT_EQ(codec_specific_info[0].codecSpecific.VP9.temporal_idx, 0u);
  EXPECT_TRUE(codec_specific_info[0].codecSpecific.VP9.ss_data_available);
  EXPECT_TRUE(codec_specific_info[0]
                  .codecSpecific.VP9.spatial_layer_resolution_present);
  EXPECT_EQ(codec_specific_info[0].codecSpecific.VP9.num_spatial_layers,
            num_spatial_layers - 1);
}

TEST_F(TestVp9Impl,
       LowLayerMarkedAsRefIfHighLayerNotEncodedAndInterLayerPredIsEnabled) {
  ConfigureSvc(codec_settings_, 3);
  codec_settings_.SetFrameDropEnabled(false);
  codec_settings_.VP9()->interLayerPred = InterLayerPredMode::kOn;

  EXPECT_EQ(WEBRTC_VIDEO_CODEC_OK,
            encoder_->InitEncode(&codec_settings_, kSettings));

  VideoBitrateAllocation bitrate_allocation;
  bitrate_allocation.SetBitrate(
      0, 0, codec_settings_.spatialLayers[0].targetBitrate * 1000);
  encoder_->SetRates(VideoEncoder::RateControlParameters(
      bitrate_allocation, codec_settings_.maxFramerate));
  EXPECT_EQ(WEBRTC_VIDEO_CODEC_OK, encoder_->Encode(NextInputFrame(), nullptr));
  EncodedImage encoded_frame;
  CodecSpecificInfo codec_info;
  ASSERT_TRUE(WaitForEncodedFrame(&encoded_frame, &codec_info));
  EXPECT_TRUE(codec_info.codecSpecific.VP9.ss_data_available);
  EXPECT_FALSE(codec_info.codecSpecific.VP9.non_ref_for_inter_layer_pred);
}

TEST_F(TestVp9Impl, ScalabilityStructureIsAvailableInFlexibleMode) {
  codec_settings_.VP9()->flexibleMode = true;
  EXPECT_EQ(WEBRTC_VIDEO_CODEC_OK,
            encoder_->InitEncode(&codec_settings_, kSettings));

  EXPECT_EQ(WEBRTC_VIDEO_CODEC_OK, encoder_->Encode(NextInputFrame(), nullptr));
  EncodedImage encoded_frame;
  CodecSpecificInfo codec_specific_info;
  ASSERT_TRUE(WaitForEncodedFrame(&encoded_frame, &codec_specific_info));
  EXPECT_TRUE(codec_specific_info.codecSpecific.VP9.ss_data_available);
}

TEST_F(TestVp9Impl, Profile0PreferredPixelFormats) {
  EXPECT_THAT(encoder_->GetEncoderInfo().preferred_pixel_formats,
              testing::UnorderedElementsAre(VideoFrameBuffer::Type::kNV12,
                                            VideoFrameBuffer::Type::kI420));
}

TEST_F(TestVp9Impl, EncoderInfoWithoutResolutionBitrateLimits) {
  EXPECT_TRUE(encoder_->GetEncoderInfo().resolution_bitrate_limits.empty());
}

TEST_F(TestVp9Impl, EncoderInfoWithBitrateLimitsFromFieldTrial) {
  test::ScopedFieldTrials field_trials(
      "WebRTC-VP9-GetEncoderInfoOverride/"
      "frame_size_pixels:123|456|789,"
      "min_start_bitrate_bps:11000|22000|33000,"
      "min_bitrate_bps:44000|55000|66000,"
      "max_bitrate_bps:77000|88000|99000/");
  SetUp();

  EXPECT_THAT(
      encoder_->GetEncoderInfo().resolution_bitrate_limits,
      ::testing::ElementsAre(
          VideoEncoder::ResolutionBitrateLimits{123, 11000, 44000, 77000},
          VideoEncoder::ResolutionBitrateLimits{456, 22000, 55000, 88000},
          VideoEncoder::ResolutionBitrateLimits{789, 33000, 66000, 99000}));
}

TEST_F(TestVp9Impl, EncoderInfoFpsAllocation) {
  const uint8_t kNumSpatialLayers = 3;
  const uint8_t kNumTemporalLayers = 3;

  codec_settings_.maxFramerate = 30;
  codec_settings_.VP9()->numberOfSpatialLayers = kNumSpatialLayers;
  codec_settings_.VP9()->numberOfTemporalLayers = kNumTemporalLayers;

  for (uint8_t sl_idx = 0; sl_idx < kNumSpatialLayers; ++sl_idx) {
    codec_settings_.spatialLayers[sl_idx].width = codec_settings_.width;
    codec_settings_.spatialLayers[sl_idx].height = codec_settings_.height;
    codec_settings_.spatialLayers[sl_idx].minBitrate =
        codec_settings_.startBitrate;
    codec_settings_.spatialLayers[sl_idx].maxBitrate =
        codec_settings_.startBitrate;
    codec_settings_.spatialLayers[sl_idx].targetBitrate =
        codec_settings_.startBitrate;
    codec_settings_.spatialLayers[sl_idx].active = true;
    codec_settings_.spatialLayers[sl_idx].maxFramerate =
        codec_settings_.maxFramerate;
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
              ElementsAreArray(expected_fps_allocation));
}

TEST_F(TestVp9Impl, EncoderInfoFpsAllocationFlexibleMode) {
  const uint8_t kNumSpatialLayers = 3;

  codec_settings_.maxFramerate = 30;
  codec_settings_.VP9()->numberOfSpatialLayers = kNumSpatialLayers;
  codec_settings_.VP9()->numberOfTemporalLayers = 1;
  codec_settings_.VP9()->flexibleMode = true;

  VideoEncoder::RateControlParameters rate_params;
  for (uint8_t sl_idx = 0; sl_idx < kNumSpatialLayers; ++sl_idx) {
    codec_settings_.spatialLayers[sl_idx].width = codec_settings_.width;
    codec_settings_.spatialLayers[sl_idx].height = codec_settings_.height;
    codec_settings_.spatialLayers[sl_idx].minBitrate =
        codec_settings_.startBitrate;
    codec_settings_.spatialLayers[sl_idx].maxBitrate =
        codec_settings_.startBitrate;
    codec_settings_.spatialLayers[sl_idx].targetBitrate =
        codec_settings_.startBitrate;
    codec_settings_.spatialLayers[sl_idx].active = true;
    // Force different frame rates for different layers, to verify that total
    // fraction is correct.
    codec_settings_.spatialLayers[sl_idx].maxFramerate =
        codec_settings_.maxFramerate / (kNumSpatialLayers - sl_idx);
    rate_params.bitrate.SetBitrate(sl_idx, 0,
                                   codec_settings_.startBitrate * 1000);
  }
  rate_params.bandwidth_allocation =
      DataRate::BitsPerSec(rate_params.bitrate.get_sum_bps());
  rate_params.framerate_fps = codec_settings_.maxFramerate;

  EXPECT_EQ(WEBRTC_VIDEO_CODEC_OK,
            encoder_->InitEncode(&codec_settings_, kSettings));

  // No temporal layers allowed when spatial layers have different fps targets.
  FramerateFractions expected_fps_allocation[kMaxSpatialLayers];
  expected_fps_allocation[0].push_back(EncoderInfo::kMaxFramerateFraction / 3);
  expected_fps_allocation[1].push_back(EncoderInfo::kMaxFramerateFraction / 2);
  expected_fps_allocation[2].push_back(EncoderInfo::kMaxFramerateFraction);
  EXPECT_THAT(encoder_->GetEncoderInfo().fps_allocation,
              ::testing::ElementsAreArray(expected_fps_allocation));

  // SetRates with current fps does not alter outcome.
  encoder_->SetRates(rate_params);
  EXPECT_THAT(encoder_->GetEncoderInfo().fps_allocation,
              ::testing::ElementsAreArray(expected_fps_allocation));

  // Higher fps than the codec wants, should still not affect outcome.
  rate_params.framerate_fps *= 2;
  encoder_->SetRates(rate_params);
  EXPECT_THAT(encoder_->GetEncoderInfo().fps_allocation,
              ::testing::ElementsAreArray(expected_fps_allocation));
}

class Vp9ImplWithLayeringTest
    : public ::testing::TestWithParam<std::tuple<int, int, bool>> {
 protected:
  Vp9ImplWithLayeringTest()
      : num_spatial_layers_(std::get<0>(GetParam())),
        num_temporal_layers_(std::get<1>(GetParam())),
        override_field_trials_(std::get<2>(GetParam())
                                   ? "WebRTC-Vp9ExternalRefCtrl/Enabled/"
                                   : "") {}

  const uint8_t num_spatial_layers_;
  const uint8_t num_temporal_layers_;
  const test::ScopedFieldTrials override_field_trials_;
};

TEST_P(Vp9ImplWithLayeringTest, FlexibleMode) {
  // In flexible mode encoder wrapper obtains actual list of references from
  // encoder and writes it into RTP payload descriptor. Check that reference
  // list in payload descriptor matches the predefined one, which is used
  // in non-flexible mode.
  std::unique_ptr<VideoEncoder> encoder = VP9Encoder::Create();
  VideoCodec codec_settings = DefaultCodecSettings();
  codec_settings.VP9()->flexibleMode = true;
  codec_settings.SetFrameDropEnabled(false);
  codec_settings.VP9()->numberOfSpatialLayers = num_spatial_layers_;
  codec_settings.VP9()->numberOfTemporalLayers = num_temporal_layers_;
  EXPECT_EQ(encoder->InitEncode(&codec_settings, kSettings),
            WEBRTC_VIDEO_CODEC_OK);

  GofInfoVP9 gof;
  if (num_temporal_layers_ == 1) {
    gof.SetGofInfoVP9(kTemporalStructureMode1);
  } else if (num_temporal_layers_ == 2) {
    gof.SetGofInfoVP9(kTemporalStructureMode2);
  } else if (num_temporal_layers_ == 3) {
    gof.SetGofInfoVP9(kTemporalStructureMode3);
  }

  // Encode at least (num_frames_in_gof + 1) frames to verify references
  // of non-key frame with gof_idx = 0.
  int num_input_frames = gof.num_frames_in_gof + 1;
  std::vector<EncodedVideoFrameProducer::EncodedFrame> frames =
      EncodedVideoFrameProducer(*encoder)
          .SetNumInputFrames(num_input_frames)
          .SetResolution({kWidth, kHeight})
          .Encode();
  ASSERT_THAT(frames, SizeIs(num_input_frames * num_spatial_layers_));

  for (size_t i = 0; i < frames.size(); ++i) {
    const EncodedVideoFrameProducer::EncodedFrame& frame = frames[i];
    const size_t picture_idx = i / num_spatial_layers_;
    const size_t gof_idx = picture_idx % gof.num_frames_in_gof;

    const CodecSpecificInfoVP9& vp9 =
        frame.codec_specific_info.codecSpecific.VP9;
    EXPECT_EQ(frame.encoded_image.SpatialIndex(),
              num_spatial_layers_ == 1
                  ? absl::nullopt
                  : absl::optional<int>(i % num_spatial_layers_))
        << "Frame " << i;
    EXPECT_EQ(vp9.temporal_idx, num_temporal_layers_ == 1
                                    ? kNoTemporalIdx
                                    : gof.temporal_idx[gof_idx])
        << "Frame " << i;
    EXPECT_EQ(vp9.temporal_up_switch, gof.temporal_up_switch[gof_idx])
        << "Frame " << i;
    if (picture_idx == 0) {
      EXPECT_EQ(vp9.num_ref_pics, 0) << "Frame " << i;
    } else {
      EXPECT_THAT(rtc::MakeArrayView(vp9.p_diff, vp9.num_ref_pics),
                  UnorderedElementsAreArray(gof.pid_diff[gof_idx],
                                            gof.num_ref_pics[gof_idx]))
          << "Frame " << i;
    }
  }
}

INSTANTIATE_TEST_SUITE_P(All,
                         Vp9ImplWithLayeringTest,
                         ::testing::Combine(::testing::Values(1, 2, 3),
                                            ::testing::Values(1, 2, 3),
                                            ::testing::Bool()));

class TestVp9ImplFrameDropping : public TestVp9Impl {
 protected:
  void ModifyCodecSettings(VideoCodec* codec_settings) override {
    webrtc::test::CodecSettings(kVideoCodecVP9, codec_settings);
    // We need to encode quite a lot of frames in this test. Use low resolution
    // to reduce execution time.
    codec_settings->width = 64;
    codec_settings->height = 64;
    codec_settings->mode = VideoCodecMode::kScreensharing;
  }
};

TEST_F(TestVp9ImplFrameDropping, PreEncodeFrameDropping) {
  const size_t num_frames_to_encode = 100;
  const float input_framerate_fps = 30.0;
  const float video_duration_secs = num_frames_to_encode / input_framerate_fps;
  const float expected_framerate_fps = 5.0f;
  const float max_abs_framerate_error_fps = expected_framerate_fps * 0.1f;

  codec_settings_.maxFramerate = static_cast<uint32_t>(expected_framerate_fps);
  EXPECT_EQ(WEBRTC_VIDEO_CODEC_OK,
            encoder_->InitEncode(&codec_settings_, kSettings));

  VideoFrame input_frame = NextInputFrame();
  for (size_t frame_num = 0; frame_num < num_frames_to_encode; ++frame_num) {
    EXPECT_EQ(WEBRTC_VIDEO_CODEC_OK, encoder_->Encode(input_frame, nullptr));
    const size_t timestamp = input_frame.timestamp() +
                             kVideoPayloadTypeFrequency / input_framerate_fps;
    input_frame.set_timestamp(static_cast<uint32_t>(timestamp));
  }

  const size_t num_encoded_frames = GetNumEncodedFrames();
  const float encoded_framerate_fps = num_encoded_frames / video_duration_secs;
  EXPECT_NEAR(encoded_framerate_fps, expected_framerate_fps,
              max_abs_framerate_error_fps);
}

TEST_F(TestVp9ImplFrameDropping, DifferentFrameratePerSpatialLayer) {
  // Assign different frame rate to spatial layers and check that result frame
  // rate is close to the assigned one.
  const uint8_t num_spatial_layers = 3;
  const float input_framerate_fps = 30.0;
  const size_t video_duration_secs = 3;
  const size_t num_input_frames = video_duration_secs * input_framerate_fps;

  codec_settings_.VP9()->numberOfSpatialLayers = num_spatial_layers;
  codec_settings_.SetFrameDropEnabled(false);
  codec_settings_.VP9()->flexibleMode = true;

  VideoBitrateAllocation bitrate_allocation;
  for (uint8_t sl_idx = 0; sl_idx < num_spatial_layers; ++sl_idx) {
    // Frame rate increases from low to high layer.
    const uint32_t framerate_fps = 10 * (sl_idx + 1);

    codec_settings_.spatialLayers[sl_idx].width = codec_settings_.width;
    codec_settings_.spatialLayers[sl_idx].height = codec_settings_.height;
    codec_settings_.spatialLayers[sl_idx].maxFramerate = framerate_fps;
    codec_settings_.spatialLayers[sl_idx].minBitrate =
        codec_settings_.startBitrate;
    codec_settings_.spatialLayers[sl_idx].maxBitrate =
        codec_settings_.startBitrate;
    codec_settings_.spatialLayers[sl_idx].targetBitrate =
        codec_settings_.startBitrate;
    codec_settings_.spatialLayers[sl_idx].active = true;

    bitrate_allocation.SetBitrate(
        sl_idx, 0, codec_settings_.spatialLayers[sl_idx].targetBitrate * 1000);
  }

  EXPECT_EQ(WEBRTC_VIDEO_CODEC_OK,
            encoder_->InitEncode(&codec_settings_, kSettings));

  encoder_->SetRates(VideoEncoder::RateControlParameters(
      bitrate_allocation, codec_settings_.maxFramerate));

  VideoFrame input_frame = NextInputFrame();
  for (size_t frame_num = 0; frame_num < num_input_frames; ++frame_num) {
    EXPECT_EQ(WEBRTC_VIDEO_CODEC_OK, encoder_->Encode(input_frame, nullptr));
    const size_t timestamp = input_frame.timestamp() +
                             kVideoPayloadTypeFrequency / input_framerate_fps;
    input_frame.set_timestamp(static_cast<uint32_t>(timestamp));
  }

  std::vector<EncodedImage> encoded_frames;
  std::vector<CodecSpecificInfo> codec_infos;
  ASSERT_TRUE(WaitForEncodedFrames(&encoded_frames, &codec_infos));

  std::vector<size_t> num_encoded_frames(num_spatial_layers, 0);
  for (EncodedImage& encoded_frame : encoded_frames) {
    ++num_encoded_frames[encoded_frame.SpatialIndex().value_or(0)];
  }

  for (uint8_t sl_idx = 0; sl_idx < num_spatial_layers; ++sl_idx) {
    const float layer_target_framerate_fps =
        codec_settings_.spatialLayers[sl_idx].maxFramerate;
    const float layer_output_framerate_fps =
        static_cast<float>(num_encoded_frames[sl_idx]) / video_duration_secs;
    const float max_framerate_error_fps = layer_target_framerate_fps * 0.1f;
    EXPECT_NEAR(layer_output_framerate_fps, layer_target_framerate_fps,
                max_framerate_error_fps);
  }
}

class TestVp9ImplProfile2 : public TestVp9Impl {
 protected:
  void SetUp() override {
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

    TestVp9Impl::SetUp();
    input_frame_generator_ = test::CreateSquareFrameGenerator(
        codec_settings_.width, codec_settings_.height,
        test::FrameGeneratorInterface::OutputType::kI010,
        absl::optional<int>());
  }

  std::unique_ptr<VideoEncoder> CreateEncoder() override {
    cricket::VideoCodec profile2_codec =
        cricket::CreateVideoCodec(cricket::kVp9CodecName);
    profile2_codec.SetParam(kVP9FmtpProfileId,
                            VP9ProfileToString(VP9Profile::kProfile2));
    return VP9Encoder::Create(profile2_codec);
  }

  std::unique_ptr<VideoDecoder> CreateDecoder() override {
    return VP9Decoder::Create();
  }
};

TEST_F(TestVp9ImplProfile2, EncodeDecode) {
  if (!encoder_)
    return;

  VideoFrame input_frame = NextInputFrame();
  EXPECT_EQ(WEBRTC_VIDEO_CODEC_OK, encoder_->Encode(input_frame, nullptr));
  EncodedImage encoded_frame;
  CodecSpecificInfo codec_specific_info;
  ASSERT_TRUE(WaitForEncodedFrame(&encoded_frame, &codec_specific_info));
  // First frame should be a key frame.
  encoded_frame._frameType = VideoFrameType::kVideoFrameKey;
  EXPECT_EQ(WEBRTC_VIDEO_CODEC_OK, decoder_->Decode(encoded_frame, 0));
  std::unique_ptr<VideoFrame> decoded_frame;
  absl::optional<uint8_t> decoded_qp;
  ASSERT_TRUE(WaitForDecodedFrame(&decoded_frame, &decoded_qp));
  ASSERT_TRUE(decoded_frame);

  // TODO(emircan): Add PSNR for different color depths.
  EXPECT_GT(I420PSNR(*input_frame.video_frame_buffer()->ToI420(),
                     *decoded_frame->video_frame_buffer()->ToI420()),
            31);
}

TEST_F(TestVp9Impl, EncodeWithDynamicRate) {
  // Configured dynamic rate field trial and re-create the encoder.
  test::ScopedFieldTrials field_trials(
      "WebRTC-VideoRateControl/vp9_dynamic_rate:true/");
  SetUp();

  // Set 300kbps target with 100% headroom.
  VideoEncoder::RateControlParameters params;
  params.bandwidth_allocation = DataRate::BitsPerSec(300000);
  params.bitrate.SetBitrate(0, 0, params.bandwidth_allocation.bps());
  params.framerate_fps = 30.0;

  encoder_->SetRates(params);
  EXPECT_EQ(WEBRTC_VIDEO_CODEC_OK, encoder_->Encode(NextInputFrame(), nullptr));
  EncodedImage encoded_frame;
  CodecSpecificInfo codec_specific_info;
  ASSERT_TRUE(WaitForEncodedFrame(&encoded_frame, &codec_specific_info));

  // Set no headroom and encode again.
  params.bandwidth_allocation = DataRate::Zero();
  encoder_->SetRates(params);
  EXPECT_EQ(WEBRTC_VIDEO_CODEC_OK, encoder_->Encode(NextInputFrame(), nullptr));
  ASSERT_TRUE(WaitForEncodedFrame(&encoded_frame, &codec_specific_info));
}

TEST_F(TestVp9Impl, ReenablingUpperLayerAfterKFWithInterlayerPredIsEnabled) {
  const size_t num_spatial_layers = 2;
  const int num_frames_to_encode = 10;
  codec_settings_.VP9()->flexibleMode = true;
  codec_settings_.SetFrameDropEnabled(false);
  codec_settings_.VP9()->numberOfSpatialLayers = num_spatial_layers;
  codec_settings_.VP9()->numberOfTemporalLayers = 1;
  codec_settings_.VP9()->interLayerPred = InterLayerPredMode::kOn;
  // Force low frame-rate, so all layers are present for all frames.
  codec_settings_.maxFramerate = 5;

  ConfigureSvc(codec_settings_, num_spatial_layers);

  EXPECT_EQ(WEBRTC_VIDEO_CODEC_OK,
            encoder_->InitEncode(&codec_settings_, kSettings));

  VideoBitrateAllocation bitrate_allocation;
  for (size_t sl_idx = 0; sl_idx < num_spatial_layers; ++sl_idx) {
    bitrate_allocation.SetBitrate(
        sl_idx, 0, codec_settings_.spatialLayers[sl_idx].targetBitrate * 1000);
  }
  encoder_->SetRates(VideoEncoder::RateControlParameters(
      bitrate_allocation, codec_settings_.maxFramerate));

  std::vector<EncodedImage> encoded_frames;
  std::vector<CodecSpecificInfo> codec_specific;

  for (int i = 0; i < num_frames_to_encode; ++i) {
    SetWaitForEncodedFramesThreshold(num_spatial_layers);
    EXPECT_EQ(WEBRTC_VIDEO_CODEC_OK,
              encoder_->Encode(NextInputFrame(), nullptr));
    ASSERT_TRUE(WaitForEncodedFrames(&encoded_frames, &codec_specific));
    EXPECT_EQ(encoded_frames.size(), num_spatial_layers);
  }

  // Disable the last layer.
  bitrate_allocation.SetBitrate(num_spatial_layers - 1, 0, 0);
  encoder_->SetRates(VideoEncoder::RateControlParameters(
      bitrate_allocation, codec_settings_.maxFramerate));

  for (int i = 0; i < num_frames_to_encode; ++i) {
    SetWaitForEncodedFramesThreshold(num_spatial_layers - 1);
    EXPECT_EQ(WEBRTC_VIDEO_CODEC_OK,
              encoder_->Encode(NextInputFrame(), nullptr));
    ASSERT_TRUE(WaitForEncodedFrames(&encoded_frames, &codec_specific));
    EXPECT_EQ(encoded_frames.size(), num_spatial_layers - 1);
  }

  std::vector<VideoFrameType> frame_types = {VideoFrameType::kVideoFrameKey};

  // Force a key-frame with the last layer still disabled.
  SetWaitForEncodedFramesThreshold(num_spatial_layers - 1);
  EXPECT_EQ(WEBRTC_VIDEO_CODEC_OK,
            encoder_->Encode(NextInputFrame(), &frame_types));
  ASSERT_TRUE(WaitForEncodedFrames(&encoded_frames, &codec_specific));
  EXPECT_EQ(encoded_frames.size(), num_spatial_layers - 1);
  ASSERT_EQ(encoded_frames[0]._frameType, VideoFrameType::kVideoFrameKey);

  // Re-enable the last layer.
  bitrate_allocation.SetBitrate(
      num_spatial_layers - 1, 0,
      codec_settings_.spatialLayers[num_spatial_layers - 1].targetBitrate *
          1000);
  encoder_->SetRates(VideoEncoder::RateControlParameters(
      bitrate_allocation, codec_settings_.maxFramerate));

  SetWaitForEncodedFramesThreshold(num_spatial_layers);
  EXPECT_EQ(WEBRTC_VIDEO_CODEC_OK, encoder_->Encode(NextInputFrame(), nullptr));
  ASSERT_TRUE(WaitForEncodedFrames(&encoded_frames, &codec_specific));
  EXPECT_EQ(encoded_frames.size(), num_spatial_layers);
  EXPECT_EQ(encoded_frames[0]._frameType, VideoFrameType::kVideoFrameDelta);
}

TEST_F(TestVp9Impl, HandlesEmptyDecoderConfigure) {
  std::unique_ptr<VideoDecoder> decoder = CreateDecoder();
  // Check that default settings are ok for decoder.
  EXPECT_TRUE(decoder->Configure({}));
  EXPECT_EQ(WEBRTC_VIDEO_CODEC_OK, decoder->Release());
}

INSTANTIATE_TEST_SUITE_P(
    TestVp9ImplForPixelFormat,
    TestVp9ImplForPixelFormat,
    ::testing::Values(test::FrameGeneratorInterface::OutputType::kI420,
                      test::FrameGeneratorInterface::OutputType::kNV12),
    [](const auto& info) {
      return test::FrameGeneratorInterface::OutputTypeToString(info.param);
    });

// Helper function to populate an vpx_image_t instance with dimensions and
// potential image data.
std::function<vpx_image_t*(vpx_image_t*,
                           vpx_img_fmt_t,
                           unsigned int,
                           unsigned int,
                           unsigned int,
                           unsigned char* img_data)>
GetWrapImageFunction(vpx_image_t* img) {
  return [img](vpx_image_t* /*img*/, vpx_img_fmt_t fmt, unsigned int d_w,
               unsigned int d_h, unsigned int /*stride_align*/,
               unsigned char* img_data) {
    img->fmt = fmt;
    img->d_w = d_w;
    img->d_h = d_h;
    img->img_data = img_data;
    return img;
  };
}

TEST(Vp9SpeedSettingsTrialsTest, NoSvcUsesGlobalSpeedFromTl0InLayerConfig) {
  // TL0 speed 8 at >= 480x270, 5 if below that.
  test::ExplicitKeyValueConfig trials(
      "WebRTC-VP9-PerformanceFlags/"
      "use_per_layer_speed,"
      "min_pixel_count:0|129600,"
      "base_layer_speed:4|8,"
      "high_layer_speed:5|9,"
      "deblock_mode:1|0/");

  // Keep a raw pointer for EXPECT calls and the like. Ownership is otherwise
  // passed on to LibvpxVp9Encoder.
  auto* const vpx = new NiceMock<MockLibvpxInterface>();
  LibvpxVp9Encoder encoder(cricket::CreateVideoCodec(cricket::kVp9CodecName),
                           absl::WrapUnique<LibvpxInterface>(vpx), trials);

  VideoCodec settings = DefaultCodecSettings();
  settings.width = 480;
  settings.height = 270;
  vpx_image_t img;

  ON_CALL(*vpx, img_wrap).WillByDefault(GetWrapImageFunction(&img));
  ON_CALL(*vpx, codec_enc_config_default)
      .WillByDefault(DoAll(WithArg<1>([](vpx_codec_enc_cfg_t* cfg) {
                             memset(cfg, 0, sizeof(vpx_codec_enc_cfg_t));
                           }),
                           Return(VPX_CODEC_OK)));
  EXPECT_CALL(*vpx, codec_control(_, _, An<int>())).Times(AnyNumber());

  EXPECT_CALL(*vpx, codec_control(_, VP9E_SET_SVC_PARAMETERS,
                                  A<vpx_svc_extra_cfg_t*>()))
      .Times(0);

  EXPECT_CALL(*vpx, codec_control(_, VP8E_SET_CPUUSED, TypedEq<int>(8)));
  EXPECT_EQ(WEBRTC_VIDEO_CODEC_OK, encoder.InitEncode(&settings, kSettings));

  encoder.Release();
  settings.width = 352;
  settings.height = 216;

  EXPECT_CALL(*vpx, codec_control(_, VP8E_SET_CPUUSED, TypedEq<int>(4)));
  EXPECT_EQ(WEBRTC_VIDEO_CODEC_OK, encoder.InitEncode(&settings, kSettings));
}

TEST(Vp9SpeedSettingsTrialsTest,
     NoPerLayerFlagUsesGlobalSpeedFromTopLayerInConfig) {
  // TL0 speed 8 at >= 480x270, 5 if below that.
  test::ExplicitKeyValueConfig trials(
      "WebRTC-VP9-PerformanceFlags/"
      "min_pixel_count:0|129600,"
      "base_layer_speed:4|8,"
      "high_layer_speed:5|9,"
      "deblock_mode:1|0/");

  // Keep a raw pointer for EXPECT calls and the like. Ownership is otherwise
  // passed on to LibvpxVp9Encoder.
  auto* const vpx = new NiceMock<MockLibvpxInterface>();
  LibvpxVp9Encoder encoder(cricket::CreateVideoCodec(cricket::kVp9CodecName),
                           absl::WrapUnique<LibvpxInterface>(vpx), trials);

  VideoCodec settings = DefaultCodecSettings();
  settings.width = 480;
  settings.height = 270;
  ConfigureSvc(settings, 2, 3);
  vpx_image_t img;

  ON_CALL(*vpx, img_wrap).WillByDefault(GetWrapImageFunction(&img));
  ON_CALL(*vpx, codec_enc_config_default)
      .WillByDefault(DoAll(WithArg<1>([](vpx_codec_enc_cfg_t* cfg) {
                             memset(cfg, 0, sizeof(vpx_codec_enc_cfg_t));
                           }),
                           Return(VPX_CODEC_OK)));
  EXPECT_CALL(*vpx, codec_control(_, _, An<int>())).Times(AnyNumber());

  // Speed settings not populated when 'use_per_layer_speed' flag is absent.
  EXPECT_CALL(*vpx,
              codec_control(
                  _, VP9E_SET_SVC_PARAMETERS,
                  SafeMatcherCast<vpx_svc_extra_cfg_t*>(AllOf(
                      Field(&vpx_svc_extra_cfg_t::speed_per_layer, Each(0)),
                      Field(&vpx_svc_extra_cfg_t::loopfilter_ctrl, Each(0))))))
      .Times(2);

  EXPECT_CALL(*vpx, codec_control(_, VP8E_SET_CPUUSED, TypedEq<int>(8)));
  EXPECT_EQ(WEBRTC_VIDEO_CODEC_OK, encoder.InitEncode(&settings, kSettings));

  encoder.Release();
  settings.width = 476;
  settings.height = 268;
  settings.spatialLayers[0].width = settings.width / 2;
  settings.spatialLayers[0].height = settings.height / 2;
  settings.spatialLayers[1].width = settings.width;
  settings.spatialLayers[1].height = settings.height;

  EXPECT_CALL(*vpx, codec_control(_, VP8E_SET_CPUUSED, TypedEq<int>(4)));
  EXPECT_EQ(WEBRTC_VIDEO_CODEC_OK, encoder.InitEncode(&settings, kSettings));
}

TEST(Vp9SpeedSettingsTrialsTest, DefaultPerLayerFlagsWithSvc) {
  // Per-temporal and spatial layer speed settings:
  // SL0:   TL0 = speed 5, TL1/TL2 = speed 8.
  // SL1/2: TL0 = speed 7, TL1/TL2 = speed 8.
  // Deblocking-mode per spatial layer:
  // SL0: mode 1, SL1/2: mode 0.
  test::ExplicitKeyValueConfig trials(
      "WebRTC-VP9-PerformanceFlags/"
      "use_per_layer_speed,"
      "min_pixel_count:0|129600,"
      "base_layer_speed:5|7,"
      "high_layer_speed:8|8,"
      "deblock_mode:1|0/");

  // Keep a raw pointer for EXPECT calls and the like. Ownership is otherwise
  // passed on to LibvpxVp9Encoder.
  auto* const vpx = new NiceMock<MockLibvpxInterface>();
  LibvpxVp9Encoder encoder(cricket::CreateVideoCodec(cricket::kVp9CodecName),
                           absl::WrapUnique<LibvpxInterface>(vpx), trials);

  VideoCodec settings = DefaultCodecSettings();
  constexpr int kNumSpatialLayers = 3;
  constexpr int kNumTemporalLayers = 3;
  ConfigureSvc(settings, kNumSpatialLayers, kNumTemporalLayers);
  VideoBitrateAllocation bitrate_allocation;
  for (int si = 0; si < kNumSpatialLayers; ++si) {
    for (int ti = 0; ti < kNumTemporalLayers; ++ti) {
      uint32_t bitrate_bps =
          settings.spatialLayers[si].targetBitrate * 1'000 / kNumTemporalLayers;
      bitrate_allocation.SetBitrate(si, ti, bitrate_bps);
    }
  }
  vpx_image_t img;

  // Speed settings per spatial layer, for TL0.
  const int kBaseTlSpeed[VPX_MAX_LAYERS] = {5, 7, 7};
  // Speed settings per spatial layer, for TL1, TL2.
  const int kHighTlSpeed[VPX_MAX_LAYERS] = {8, 8, 8};
  // Loopfilter settings are handled within libvpx, so this array is valid for
  // both TL0 and higher.
  const int kLoopFilter[VPX_MAX_LAYERS] = {1, 0, 0};

  ON_CALL(*vpx, img_wrap).WillByDefault(GetWrapImageFunction(&img));
  ON_CALL(*vpx, codec_enc_init)
      .WillByDefault(WithArg<0>([](vpx_codec_ctx_t* ctx) {
        memset(ctx, 0, sizeof(*ctx));
        return VPX_CODEC_OK;
      }));
  ON_CALL(*vpx, codec_enc_config_default)
      .WillByDefault(DoAll(WithArg<1>([](vpx_codec_enc_cfg_t* cfg) {
                             memset(cfg, 0, sizeof(vpx_codec_enc_cfg_t));
                           }),
                           Return(VPX_CODEC_OK)));
  EXPECT_CALL(
      *vpx, codec_control(_, VP9E_SET_SVC_PARAMETERS,
                          SafeMatcherCast<vpx_svc_extra_cfg_t*>(
                              AllOf(Field(&vpx_svc_extra_cfg_t::speed_per_layer,
                                          ElementsAreArray(kBaseTlSpeed)),
                                    Field(&vpx_svc_extra_cfg_t::loopfilter_ctrl,
                                          ElementsAreArray(kLoopFilter))))));

  // Capture the callback into the vp9 wrapper.
  vpx_codec_priv_output_cx_pkt_cb_pair_t callback_pointer = {};
  EXPECT_CALL(*vpx, codec_control(_, VP9E_REGISTER_CX_CALLBACK, A<void*>()))
      .WillOnce(WithArg<2>([&](void* cbp) {
        callback_pointer =
            *reinterpret_cast<vpx_codec_priv_output_cx_pkt_cb_pair_t*>(cbp);
        return VPX_CODEC_OK;
      }));

  EXPECT_EQ(WEBRTC_VIDEO_CODEC_OK, encoder.InitEncode(&settings, kSettings));

  encoder.SetRates(VideoEncoder::RateControlParameters(bitrate_allocation,
                                                       settings.maxFramerate));

  MockEncodedImageCallback callback;
  encoder.RegisterEncodeCompleteCallback(&callback);
  auto frame_generator = test::CreateSquareFrameGenerator(
      kWidth, kHeight, test::FrameGeneratorInterface::OutputType::kI420, 10);
  Mock::VerifyAndClearExpectations(vpx);

  uint8_t data[1] = {0};
  vpx_codec_cx_pkt encoded_data = {};
  encoded_data.data.frame.buf = &data;
  encoded_data.data.frame.sz = 1;

  const auto kImageOk =
      EncodedImageCallback::Result(EncodedImageCallback::Result::OK);

  int spatial_id = 0;
  int temporal_id = 0;
  EXPECT_CALL(*vpx,
              codec_control(_, VP9E_SET_SVC_LAYER_ID, A<vpx_svc_layer_id_t*>()))
      .Times(AnyNumber());
  EXPECT_CALL(*vpx,
              codec_control(_, VP9E_GET_SVC_LAYER_ID, A<vpx_svc_layer_id_t*>()))
      .WillRepeatedly(WithArg<2>([&](vpx_svc_layer_id_t* layer_id) {
        layer_id->spatial_layer_id = spatial_id;
        layer_id->temporal_layer_id = temporal_id;
        return VPX_CODEC_OK;
      }));
  vpx_svc_ref_frame_config_t stored_refs = {};
  ON_CALL(*vpx, codec_control(_, VP9E_SET_SVC_REF_FRAME_CONFIG,
                              A<vpx_svc_ref_frame_config_t*>()))
      .WillByDefault(
          DoAll(SaveArgPointee<2>(&stored_refs), Return(VPX_CODEC_OK)));
  ON_CALL(*vpx, codec_control(_, VP9E_GET_SVC_REF_FRAME_CONFIG,
                              A<vpx_svc_ref_frame_config_t*>()))
      .WillByDefault(
          DoAll(SetArgPointee<2>(ByRef(stored_refs)), Return(VPX_CODEC_OK)));

  // First frame is keyframe.
  encoded_data.data.frame.flags = VPX_FRAME_IS_KEY;

  // Default 3-layer temporal pattern: 0-2-1-2, then repeat and do two more.
  for (int ti : {0, 2, 1, 2, 0, 2}) {
    EXPECT_CALL(*vpx, codec_encode).WillOnce(Return(VPX_CODEC_OK));
    // No update expected if flags haven't changed, and they change we we move
    // between base temporal layer and non-base temporal layer.
    if ((ti > 0) != (temporal_id > 0)) {
      EXPECT_CALL(*vpx, codec_control(
                            _, VP9E_SET_SVC_PARAMETERS,
                            SafeMatcherCast<vpx_svc_extra_cfg_t*>(AllOf(
                                Field(&vpx_svc_extra_cfg_t::speed_per_layer,
                                      ElementsAreArray(ti == 0 ? kBaseTlSpeed
                                                               : kHighTlSpeed)),
                                Field(&vpx_svc_extra_cfg_t::loopfilter_ctrl,
                                      ElementsAreArray(kLoopFilter))))));
    } else {
      EXPECT_CALL(*vpx, codec_control(_, VP9E_SET_SVC_PARAMETERS,
                                      A<vpx_svc_extra_cfg_t*>()))
          .Times(0);
    }

    VideoFrame frame =
        VideoFrame::Builder()
            .set_video_frame_buffer(frame_generator->NextFrame().buffer)
            .build();
    encoder.Encode(frame, nullptr);

    temporal_id = ti;
    for (int si = 0; si < kNumSpatialLayers; ++si) {
      spatial_id = si;

      EXPECT_CALL(callback, OnEncodedImage).WillOnce(Return(kImageOk));
      callback_pointer.output_cx_pkt(&encoded_data, callback_pointer.user_priv);
    }

    encoded_data.data.frame.flags = 0;  // Following frames are delta frames.
  }
}

struct SvcFrameDropConfigTestParameters {
  bool flexible_mode;
  absl::optional<ScalabilityMode> scalability_mode;
  std::string field_trial;
  int expected_framedrop_mode;
  int expected_max_consec_drop;
};

class TestVp9ImplSvcFrameDropConfig
    : public ::testing::TestWithParam<SvcFrameDropConfigTestParameters> {};

TEST_P(TestVp9ImplSvcFrameDropConfig, SvcFrameDropConfig) {
  SvcFrameDropConfigTestParameters test_params = GetParam();
  auto* const vpx = new NiceMock<MockLibvpxInterface>();
  LibvpxVp9Encoder encoder(
      cricket::CreateVideoCodec(cricket::kVp9CodecName),
      absl::WrapUnique<LibvpxInterface>(vpx),
      test::ExplicitKeyValueConfig(test_params.field_trial));

  vpx_image_t img;
  ON_CALL(*vpx, img_wrap).WillByDefault(GetWrapImageFunction(&img));

  EXPECT_CALL(*vpx,
              codec_control(_, VP9E_SET_SVC_FRAME_DROP_LAYER,
                            SafeMatcherCast<vpx_svc_frame_drop_t*>(AllOf(
                                Field(&vpx_svc_frame_drop_t::framedrop_mode,
                                      test_params.expected_framedrop_mode),
                                Field(&vpx_svc_frame_drop_t::max_consec_drop,
                                      test_params.expected_max_consec_drop)))));

  VideoCodec settings = DefaultCodecSettings();
  settings.VP9()->flexibleMode = test_params.flexible_mode;
  if (test_params.scalability_mode.has_value()) {
    settings.SetScalabilityMode(*test_params.scalability_mode);
  }
  settings.VP9()->numberOfSpatialLayers =
      3;  // to execute SVC code paths even when scalability_mode is not set.

  EXPECT_EQ(WEBRTC_VIDEO_CODEC_OK, encoder.InitEncode(&settings, kSettings));
}

INSTANTIATE_TEST_SUITE_P(
    All,
    TestVp9ImplSvcFrameDropConfig,
    ::testing::Values(
        // Flexible mode is disabled. Layer drop is not allowed. Ignore
        // layer_drop_mode from field trial.
        SvcFrameDropConfigTestParameters{
            .flexible_mode = false,
            .scalability_mode = ScalabilityMode::kL3T3_KEY,
            .field_trial = "WebRTC-LibvpxVp9Encoder-SvcFrameDropConfig/"
                           "Enabled,layer_drop_mode:1,max_consec_drop:7/",
            .expected_framedrop_mode = FULL_SUPERFRAME_DROP,
            .expected_max_consec_drop = 7},
        // Flexible mode is enabled but the field trial is not set. Use default
        // settings.
        SvcFrameDropConfigTestParameters{
            .flexible_mode = true,
            .scalability_mode = ScalabilityMode::kL3T3_KEY,
            .field_trial = "",
            .expected_framedrop_mode = FULL_SUPERFRAME_DROP,
            .expected_max_consec_drop = std::numeric_limits<int>::max()},
        // Flexible mode is enabled but the field trial is disabled. Use default
        // settings.
        SvcFrameDropConfigTestParameters{
            .flexible_mode = true,
            .scalability_mode = ScalabilityMode::kL3T3_KEY,
            .field_trial = "WebRTC-LibvpxVp9Encoder-SvcFrameDropConfig/"
                           "Disabled,layer_drop_mode:1,max_consec_drop:7/",
            .expected_framedrop_mode = FULL_SUPERFRAME_DROP,
            .expected_max_consec_drop = std::numeric_limits<int>::max()},
        // Flexible mode is enabled, layer drop is enabled, KSVC. Apply config
        // from field trial.
        SvcFrameDropConfigTestParameters{
            .flexible_mode = true,
            .scalability_mode = ScalabilityMode::kL3T3_KEY,
            .field_trial = "WebRTC-LibvpxVp9Encoder-SvcFrameDropConfig/"
                           "Enabled,layer_drop_mode:1,max_consec_drop:7/",
            .expected_framedrop_mode = LAYER_DROP,
            .expected_max_consec_drop = 7},
        // Flexible mode is enabled, layer drop is enabled, simulcast. Apply
        // config from field trial.
        SvcFrameDropConfigTestParameters{
            .flexible_mode = true,
            .scalability_mode = ScalabilityMode::kS3T3,
            .field_trial = "WebRTC-LibvpxVp9Encoder-SvcFrameDropConfig/"
                           "Enabled,layer_drop_mode:1,max_consec_drop:7/",
            .expected_framedrop_mode = LAYER_DROP,
            .expected_max_consec_drop = 7},
        // Flexible mode is enabled, layer drop is enabled, full SVC. Apply
        // config from field trial.
        SvcFrameDropConfigTestParameters{
            .flexible_mode = false,
            .scalability_mode = ScalabilityMode::kL3T3,
            .field_trial = "WebRTC-LibvpxVp9Encoder-SvcFrameDropConfig/"
                           "Enabled,layer_drop_mode:1,max_consec_drop:7/",
            .expected_framedrop_mode = FULL_SUPERFRAME_DROP,
            .expected_max_consec_drop = 7},
        // Flexible mode is enabled, layer-drop is enabled, scalability mode is
        // not set (i.e., SVC controller is not enabled). Ignore layer_drop_mode
        // from field trial.
        SvcFrameDropConfigTestParameters{
            .flexible_mode = true,
            .scalability_mode = absl::nullopt,
            .field_trial = "WebRTC-LibvpxVp9Encoder-SvcFrameDropConfig/"
                           "Enabled,layer_drop_mode:1,max_consec_drop:7/",
            .expected_framedrop_mode = FULL_SUPERFRAME_DROP,
            .expected_max_consec_drop = 7}));

}  // namespace webrtc
