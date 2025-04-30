/*
 *  Copyright (c) 2017 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include <stddef.h>

#include <cstdint>
#include <memory>
#include <utility>
#include <vector>

#include "absl/types/optional.h"
#include "api/environment/environment.h"
#include "api/environment/environment_factory.h"
#include "api/scoped_refptr.h"
#include "api/test/mock_video_decoder_factory.h"
#include "api/test/mock_video_encoder_factory.h"
#include "api/video/encoded_image.h"
#include "api/video/video_frame.h"
#include "api/video/video_frame_buffer.h"
#include "api/video/video_rotation.h"
#include "api/video_codecs/sdp_video_format.h"
#include "api/video_codecs/video_codec.h"
#include "api/video_codecs/video_decoder.h"
#include "api/video_codecs/video_encoder.h"
#include "common_video/include/video_frame_buffer.h"
#include "common_video/libyuv/include/webrtc_libyuv.h"
#include "media/base/media_constants.h"
#include "modules/video_coding/codecs/multiplex/include/augmented_video_frame_buffer.h"
#include "modules/video_coding/codecs/multiplex/include/multiplex_decoder_adapter.h"
#include "modules/video_coding/codecs/multiplex/include/multiplex_encoder_adapter.h"
#include "modules/video_coding/codecs/multiplex/multiplex_encoded_image_packer.h"
#include "modules/video_coding/codecs/test/video_codec_unittest.h"
#include "modules/video_coding/codecs/vp9/include/vp9.h"
#include "modules/video_coding/include/video_codec_interface.h"
#include "modules/video_coding/include/video_error_codes.h"
#include "test/gmock.h"
#include "test/gtest.h"
#include "test/video_codec_settings.h"

using ::testing::_;
using ::testing::Return;

namespace webrtc {

constexpr const char* kMultiplexAssociatedCodecName = cricket::kVp9CodecName;
const VideoCodecType kMultiplexAssociatedCodecType =
    PayloadStringToCodecType(kMultiplexAssociatedCodecName);

class TestMultiplexAdapter : public VideoCodecUnitTest,
                             public ::testing::WithParamInterface<
                                 bool /* supports_augmenting_data */> {
 public:
  TestMultiplexAdapter()
      : decoder_factory_(new webrtc::MockVideoDecoderFactory),
        encoder_factory_(new webrtc::MockVideoEncoderFactory),
        supports_augmenting_data_(GetParam()) {}

 protected:
  std::unique_ptr<VideoDecoder> CreateDecoder() override {
    return std::make_unique<MultiplexDecoderAdapter>(
        env_, decoder_factory_.get(),
        SdpVideoFormat(kMultiplexAssociatedCodecName),
        supports_augmenting_data_);
  }

  std::unique_ptr<VideoEncoder> CreateEncoder() override {
    return std::make_unique<MultiplexEncoderAdapter>(
        encoder_factory_.get(), SdpVideoFormat(kMultiplexAssociatedCodecName),
        supports_augmenting_data_);
  }

  void ModifyCodecSettings(VideoCodec* codec_settings) override {
    webrtc::test::CodecSettings(kMultiplexAssociatedCodecType, codec_settings);
    codec_settings->VP9()->numberOfTemporalLayers = 1;
    codec_settings->VP9()->numberOfSpatialLayers = 1;
    codec_settings->codecType = webrtc::kVideoCodecMultiplex;
  }

  std::unique_ptr<VideoFrame> CreateDataAugmentedInputFrame(
      VideoFrame* video_frame) {
    rtc::scoped_refptr<VideoFrameBuffer> video_buffer =
        video_frame->video_frame_buffer();
    std::unique_ptr<uint8_t[]> data =
        std::unique_ptr<uint8_t[]>(new uint8_t[16]);
    for (int i = 0; i < 16; i++) {
      data[i] = i;
    }
    auto augmented_video_frame_buffer =
        rtc::make_ref_counted<AugmentedVideoFrameBuffer>(video_buffer,
                                                         std::move(data), 16);
    return std::make_unique<VideoFrame>(
        VideoFrame::Builder()
            .set_video_frame_buffer(augmented_video_frame_buffer)
            .set_timestamp_rtp(video_frame->timestamp())
            .set_timestamp_ms(video_frame->render_time_ms())
            .set_rotation(video_frame->rotation())
            .set_id(video_frame->id())
            .build());
  }

  std::unique_ptr<VideoFrame> CreateI420AInputFrame() {
    VideoFrame input_frame = NextInputFrame();
    rtc::scoped_refptr<webrtc::I420BufferInterface> yuv_buffer =
        input_frame.video_frame_buffer()->ToI420();
    rtc::scoped_refptr<I420ABufferInterface> yuva_buffer = WrapI420ABuffer(
        yuv_buffer->width(), yuv_buffer->height(), yuv_buffer->DataY(),
        yuv_buffer->StrideY(), yuv_buffer->DataU(), yuv_buffer->StrideU(),
        yuv_buffer->DataV(), yuv_buffer->StrideV(), yuv_buffer->DataY(),
        yuv_buffer->StrideY(),
        // To keep reference alive.
        [yuv_buffer] {});
    return std::make_unique<VideoFrame>(VideoFrame::Builder()
                                            .set_video_frame_buffer(yuva_buffer)
                                            .set_timestamp_rtp(123)
                                            .set_timestamp_ms(345)
                                            .set_rotation(kVideoRotation_0)
                                            .build());
  }

  std::unique_ptr<VideoFrame> CreateInputFrame(bool contains_alpha) {
    std::unique_ptr<VideoFrame> video_frame;
    if (contains_alpha) {
      video_frame = CreateI420AInputFrame();
    } else {
      VideoFrame next_frame = NextInputFrame();
      video_frame = std::make_unique<VideoFrame>(
          VideoFrame::Builder()
              .set_video_frame_buffer(next_frame.video_frame_buffer())
              .set_timestamp_rtp(next_frame.timestamp())
              .set_timestamp_ms(next_frame.render_time_ms())
              .set_rotation(next_frame.rotation())
              .set_id(next_frame.id())
              .build());
    }
    if (supports_augmenting_data_) {
      video_frame = CreateDataAugmentedInputFrame(video_frame.get());
    }

    return video_frame;
  }

  void CheckData(rtc::scoped_refptr<VideoFrameBuffer> video_frame_buffer) {
    if (!supports_augmenting_data_) {
      return;
    }
    AugmentedVideoFrameBuffer* augmented_buffer =
        static_cast<AugmentedVideoFrameBuffer*>(video_frame_buffer.get());
    EXPECT_EQ(augmented_buffer->GetAugmentingDataSize(), 16);
    uint8_t* data = augmented_buffer->GetAugmentingData();
    for (int i = 0; i < 16; i++) {
      EXPECT_EQ(data[i], i);
    }
  }

  std::unique_ptr<VideoFrame> ExtractAXXFrame(const VideoFrame& video_frame) {
    rtc::scoped_refptr<VideoFrameBuffer> video_frame_buffer =
        video_frame.video_frame_buffer();
    if (supports_augmenting_data_) {
      AugmentedVideoFrameBuffer* augmentedBuffer =
          static_cast<AugmentedVideoFrameBuffer*>(video_frame_buffer.get());
      video_frame_buffer = augmentedBuffer->GetVideoFrameBuffer();
    }
    const I420ABufferInterface* yuva_buffer = video_frame_buffer->GetI420A();
    rtc::scoped_refptr<I420BufferInterface> axx_buffer = WrapI420Buffer(
        yuva_buffer->width(), yuva_buffer->height(), yuva_buffer->DataA(),
        yuva_buffer->StrideA(), yuva_buffer->DataU(), yuva_buffer->StrideU(),
        yuva_buffer->DataV(), yuva_buffer->StrideV(), [video_frame_buffer] {});
    return std::make_unique<VideoFrame>(VideoFrame::Builder()
                                            .set_video_frame_buffer(axx_buffer)
                                            .set_timestamp_rtp(123)
                                            .set_timestamp_ms(345)
                                            .set_rotation(kVideoRotation_0)
                                            .build());
  }

 private:
  void SetUp() override {
    EXPECT_CALL(*decoder_factory_, Die);
    // The decoders/encoders will be owned by the caller of
    // CreateVideoDecoder()/CreateVideoEncoder().
    EXPECT_CALL(*decoder_factory_, Create).Times(2).WillRepeatedly([] {
      return VP9Decoder::Create();
    });

    EXPECT_CALL(*encoder_factory_, Die);
    EXPECT_CALL(*encoder_factory_, CreateVideoEncoder)
        .Times(2)
        .WillRepeatedly([] { return VP9Encoder::Create(); });

    VideoCodecUnitTest::SetUp();
  }

  const Environment env_ = CreateEnvironment();
  const std::unique_ptr<webrtc::MockVideoDecoderFactory> decoder_factory_;
  const std::unique_ptr<webrtc::MockVideoEncoderFactory> encoder_factory_;
  const bool supports_augmenting_data_;
};

// TODO(emircan): Currently VideoCodecUnitTest tests do a complete setup
// step that goes beyond constructing `decoder_`. Simplify these tests to do
// less.
TEST_P(TestMultiplexAdapter, ConstructAndDestructDecoder) {
  EXPECT_EQ(WEBRTC_VIDEO_CODEC_OK, decoder_->Release());
}

TEST_P(TestMultiplexAdapter, ConstructAndDestructEncoder) {
  EXPECT_EQ(WEBRTC_VIDEO_CODEC_OK, encoder_->Release());
}

TEST_P(TestMultiplexAdapter, EncodeDecodeI420Frame) {
  std::unique_ptr<VideoFrame> input_frame = CreateInputFrame(false);
  EXPECT_EQ(WEBRTC_VIDEO_CODEC_OK, encoder_->Encode(*input_frame, nullptr));
  EncodedImage encoded_frame;
  CodecSpecificInfo codec_specific_info;
  ASSERT_TRUE(WaitForEncodedFrame(&encoded_frame, &codec_specific_info));
  EXPECT_EQ(kVideoCodecMultiplex, codec_specific_info.codecType);

  EXPECT_EQ(WEBRTC_VIDEO_CODEC_OK, decoder_->Decode(encoded_frame, -1));
  std::unique_ptr<VideoFrame> decoded_frame;
  absl::optional<uint8_t> decoded_qp;
  ASSERT_TRUE(WaitForDecodedFrame(&decoded_frame, &decoded_qp));
  ASSERT_TRUE(decoded_frame);
  EXPECT_GT(I420PSNR(input_frame.get(), decoded_frame.get()), 36);
  CheckData(decoded_frame->video_frame_buffer());
}

TEST_P(TestMultiplexAdapter, EncodeDecodeI420AFrame) {
  std::unique_ptr<VideoFrame> yuva_frame = CreateInputFrame(true);
  EXPECT_EQ(WEBRTC_VIDEO_CODEC_OK, encoder_->Encode(*yuva_frame, nullptr));
  EncodedImage encoded_frame;
  CodecSpecificInfo codec_specific_info;
  ASSERT_TRUE(WaitForEncodedFrame(&encoded_frame, &codec_specific_info));
  EXPECT_EQ(kVideoCodecMultiplex, codec_specific_info.codecType);

  EXPECT_EQ(WEBRTC_VIDEO_CODEC_OK, decoder_->Decode(encoded_frame, 0));
  std::unique_ptr<VideoFrame> decoded_frame;
  absl::optional<uint8_t> decoded_qp;
  ASSERT_TRUE(WaitForDecodedFrame(&decoded_frame, &decoded_qp));
  ASSERT_TRUE(decoded_frame);
  EXPECT_GT(I420PSNR(yuva_frame.get(), decoded_frame.get()), 36);

  // Find PSNR for AXX bits.
  std::unique_ptr<VideoFrame> input_axx_frame = ExtractAXXFrame(*yuva_frame);
  std::unique_ptr<VideoFrame> output_axx_frame =
      ExtractAXXFrame(*decoded_frame);
  EXPECT_GT(I420PSNR(input_axx_frame.get(), output_axx_frame.get()), 47);

  CheckData(decoded_frame->video_frame_buffer());
}

TEST_P(TestMultiplexAdapter, CheckSingleFrameEncodedBitstream) {
  std::unique_ptr<VideoFrame> input_frame = CreateInputFrame(false);
  EXPECT_EQ(WEBRTC_VIDEO_CODEC_OK, encoder_->Encode(*input_frame, nullptr));
  EncodedImage encoded_frame;
  CodecSpecificInfo codec_specific_info;
  ASSERT_TRUE(WaitForEncodedFrame(&encoded_frame, &codec_specific_info));
  EXPECT_EQ(kVideoCodecMultiplex, codec_specific_info.codecType);
  EXPECT_FALSE(encoded_frame.SpatialIndex());

  const MultiplexImage& unpacked_frame =
      MultiplexEncodedImagePacker::Unpack(encoded_frame);
  EXPECT_EQ(0, unpacked_frame.image_index);
  EXPECT_EQ(1, unpacked_frame.component_count);
  const MultiplexImageComponent& component = unpacked_frame.image_components[0];
  EXPECT_EQ(0, component.component_index);
  EXPECT_NE(nullptr, component.encoded_image.data());
  EXPECT_EQ(VideoFrameType::kVideoFrameKey, component.encoded_image._frameType);
}

TEST_P(TestMultiplexAdapter, CheckDoubleFramesEncodedBitstream) {
  std::unique_ptr<VideoFrame> yuva_frame = CreateInputFrame(true);
  EXPECT_EQ(WEBRTC_VIDEO_CODEC_OK, encoder_->Encode(*yuva_frame, nullptr));
  EncodedImage encoded_frame;
  CodecSpecificInfo codec_specific_info;
  ASSERT_TRUE(WaitForEncodedFrame(&encoded_frame, &codec_specific_info));
  EXPECT_EQ(kVideoCodecMultiplex, codec_specific_info.codecType);
  EXPECT_FALSE(encoded_frame.SpatialIndex());

  const MultiplexImage& unpacked_frame =
      MultiplexEncodedImagePacker::Unpack(encoded_frame);
  EXPECT_EQ(0, unpacked_frame.image_index);
  EXPECT_EQ(2, unpacked_frame.component_count);
  EXPECT_EQ(unpacked_frame.image_components.size(),
            unpacked_frame.component_count);
  for (int i = 0; i < unpacked_frame.component_count; ++i) {
    const MultiplexImageComponent& component =
        unpacked_frame.image_components[i];
    EXPECT_EQ(i, component.component_index);
    EXPECT_NE(nullptr, component.encoded_image.data());
    EXPECT_EQ(VideoFrameType::kVideoFrameKey,
              component.encoded_image._frameType);
  }
}

TEST_P(TestMultiplexAdapter, ImageIndexIncreases) {
  std::unique_ptr<VideoFrame> yuva_frame = CreateInputFrame(true);
  const size_t expected_num_encoded_frames = 3;
  for (size_t i = 0; i < expected_num_encoded_frames; ++i) {
    EXPECT_EQ(WEBRTC_VIDEO_CODEC_OK, encoder_->Encode(*yuva_frame, nullptr));
    EncodedImage encoded_frame;
    CodecSpecificInfo codec_specific_info;
    ASSERT_TRUE(WaitForEncodedFrame(&encoded_frame, &codec_specific_info));
    const MultiplexImage& unpacked_frame =
        MultiplexEncodedImagePacker::Unpack(encoded_frame);
    EXPECT_EQ(i, unpacked_frame.image_index);
    EXPECT_EQ(
        i ? VideoFrameType::kVideoFrameDelta : VideoFrameType::kVideoFrameKey,
        encoded_frame._frameType);
  }
}

INSTANTIATE_TEST_SUITE_P(TestMultiplexAdapter,
                         TestMultiplexAdapter,
                         ::testing::Bool());

}  // namespace webrtc
