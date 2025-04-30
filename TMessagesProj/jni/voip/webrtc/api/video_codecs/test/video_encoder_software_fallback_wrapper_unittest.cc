/*
 *  Copyright (c) 2015 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "api/video_codecs/video_encoder_software_fallback_wrapper.h"

#include <stddef.h>
#include <stdint.h>

#include <memory>
#include <string>
#include <vector>

#include "absl/types/optional.h"
#include "api/fec_controller_override.h"
#include "api/scoped_refptr.h"
#include "api/test/mock_video_encoder.h"
#include "api/video/encoded_image.h"
#include "api/video/i420_buffer.h"
#include "api/video/video_bitrate_allocation.h"
#include "api/video/video_frame.h"
#include "api/video/video_frame_buffer.h"
#include "api/video/video_rotation.h"
#include "api/video_codecs/video_codec.h"
#include "api/video_codecs/video_encoder.h"
#include "modules/video_coding/codecs/vp8/include/vp8.h"
#include "modules/video_coding/include/video_codec_interface.h"
#include "modules/video_coding/include/video_error_codes.h"
#include "modules/video_coding/utility/simulcast_rate_allocator.h"
#include "rtc_base/fake_clock.h"
#include "test/fake_encoder.h"
#include "test/fake_texture_frame.h"
#include "test/field_trial.h"
#include "test/gmock.h"
#include "test/gtest.h"

namespace webrtc {
using ::testing::_;
using ::testing::Return;
using ::testing::ValuesIn;

namespace {
const int kWidth = 320;
const int kHeight = 240;
const int kNumCores = 2;
const uint32_t kFramerate = 30;
const size_t kMaxPayloadSize = 800;
const int kLowThreshold = 10;
const int kHighThreshold = 20;

const VideoEncoder::Capabilities kCapabilities(false);
const VideoEncoder::Settings kSettings(kCapabilities,
                                       kNumCores,
                                       kMaxPayloadSize);

VideoEncoder::EncoderInfo GetEncoderInfoWithTrustedRateController(
    bool trusted_rate_controller) {
  VideoEncoder::EncoderInfo info;
  info.has_trusted_rate_controller = trusted_rate_controller;
  return info;
}

VideoEncoder::EncoderInfo GetEncoderInfoWithHardwareAccelerated(
    bool hardware_accelerated) {
  VideoEncoder::EncoderInfo info;
  info.is_hardware_accelerated = hardware_accelerated;
  return info;
}

class FakeEncodedImageCallback : public EncodedImageCallback {
 public:
  Result OnEncodedImage(const EncodedImage& encoded_image,
                        const CodecSpecificInfo* codec_specific_info) override {
    ++callback_count_;
    return Result(Result::OK, callback_count_);
  }
  int callback_count_ = 0;
};
}  // namespace

class VideoEncoderSoftwareFallbackWrapperTestBase : public ::testing::Test {
 protected:
  VideoEncoderSoftwareFallbackWrapperTestBase(
      const std::string& field_trials,
      std::unique_ptr<VideoEncoder> sw_encoder)
      : override_field_trials_(field_trials),
        fake_encoder_(new CountingFakeEncoder()),
        wrapper_initialized_(false),
        fallback_wrapper_(CreateVideoEncoderSoftwareFallbackWrapper(
            std::move(sw_encoder),
            std::unique_ptr<VideoEncoder>(fake_encoder_),
            false)) {}

  class CountingFakeEncoder : public VideoEncoder {
   public:
    void SetFecControllerOverride(
        FecControllerOverride* fec_controller_override) override {
      // Ignored.
    }

    int32_t InitEncode(const VideoCodec* codec_settings,
                       const VideoEncoder::Settings& settings) override {
      ++init_encode_count_;
      return init_encode_return_code_;
    }

    int32_t Encode(const VideoFrame& frame,
                   const std::vector<VideoFrameType>* frame_types) override {
      ++encode_count_;
      last_video_frame_ = frame;
      if (encode_complete_callback_ &&
          encode_return_code_ == WEBRTC_VIDEO_CODEC_OK) {
        encode_complete_callback_->OnEncodedImage(EncodedImage(), nullptr);
      }
      return encode_return_code_;
    }

    int32_t RegisterEncodeCompleteCallback(
        EncodedImageCallback* callback) override {
      encode_complete_callback_ = callback;
      return WEBRTC_VIDEO_CODEC_OK;
    }

    int32_t Release() override {
      ++release_count_;
      return WEBRTC_VIDEO_CODEC_OK;
    }

    void SetRates(const RateControlParameters& parameters) override {}

    EncoderInfo GetEncoderInfo() const override {
      ++supports_native_handle_count_;
      EncoderInfo info;
      info.scaling_settings = ScalingSettings(kLowThreshold, kHighThreshold);
      info.supports_native_handle = supports_native_handle_;
      info.implementation_name = implementation_name_;
      if (is_qp_trusted_)
        info.is_qp_trusted = is_qp_trusted_;
      return info;
    }

    int init_encode_count_ = 0;
    int32_t init_encode_return_code_ = WEBRTC_VIDEO_CODEC_OK;
    int32_t encode_return_code_ = WEBRTC_VIDEO_CODEC_OK;
    int encode_count_ = 0;
    EncodedImageCallback* encode_complete_callback_ = nullptr;
    int release_count_ = 0;
    mutable int supports_native_handle_count_ = 0;
    bool supports_native_handle_ = false;
    bool is_qp_trusted_ = false;
    std::string implementation_name_ = "fake-encoder";
    absl::optional<VideoFrame> last_video_frame_;
  };

  void InitEncode();
  void UtilizeFallbackEncoder();
  void FallbackFromEncodeRequest();
  void EncodeFrame();
  void EncodeFrame(int expected_ret);
  void CheckLastEncoderName(const char* expected_name) {
    EXPECT_EQ(expected_name,
              fallback_wrapper_->GetEncoderInfo().implementation_name);
  }

  test::ScopedFieldTrials override_field_trials_;
  FakeEncodedImageCallback callback_;
  // `fake_encoder_` is owned and released by `fallback_wrapper_`.
  CountingFakeEncoder* fake_encoder_;
  CountingFakeEncoder* fake_sw_encoder_;
  bool wrapper_initialized_;
  std::unique_ptr<VideoEncoder> fallback_wrapper_;
  VideoCodec codec_ = {};
  std::unique_ptr<VideoFrame> frame_;
  std::unique_ptr<SimulcastRateAllocator> rate_allocator_;
};

class VideoEncoderSoftwareFallbackWrapperTest
    : public VideoEncoderSoftwareFallbackWrapperTestBase {
 protected:
  VideoEncoderSoftwareFallbackWrapperTest()
      : VideoEncoderSoftwareFallbackWrapperTest(new CountingFakeEncoder()) {}
  explicit VideoEncoderSoftwareFallbackWrapperTest(
      CountingFakeEncoder* fake_sw_encoder)
      : VideoEncoderSoftwareFallbackWrapperTestBase(
            "",
            std::unique_ptr<VideoEncoder>(fake_sw_encoder)),
        fake_sw_encoder_(fake_sw_encoder) {
    fake_sw_encoder_->implementation_name_ = "fake_sw_encoder";
  }

  CountingFakeEncoder* fake_sw_encoder_;
};

void VideoEncoderSoftwareFallbackWrapperTestBase::EncodeFrame() {
  EncodeFrame(WEBRTC_VIDEO_CODEC_OK);
}

void VideoEncoderSoftwareFallbackWrapperTestBase::EncodeFrame(
    int expected_ret) {
  rtc::scoped_refptr<I420Buffer> buffer =
      I420Buffer::Create(codec_.width, codec_.height);
  I420Buffer::SetBlack(buffer.get());
  std::vector<VideoFrameType> types(1, VideoFrameType::kVideoFrameKey);

  frame_ =
      std::make_unique<VideoFrame>(VideoFrame::Builder()
                                       .set_video_frame_buffer(buffer)
                                       .set_rotation(webrtc::kVideoRotation_0)
                                       .set_timestamp_us(0)
                                       .build());
  EXPECT_EQ(expected_ret, fallback_wrapper_->Encode(*frame_, &types));
}

void VideoEncoderSoftwareFallbackWrapperTestBase::InitEncode() {
  if (!wrapper_initialized_) {
    fallback_wrapper_->RegisterEncodeCompleteCallback(&callback_);
    EXPECT_EQ(&callback_, fake_encoder_->encode_complete_callback_);
  }

  // Register fake encoder as main.
  codec_.codecType = kVideoCodecVP8;
  codec_.maxFramerate = kFramerate;
  codec_.width = kWidth;
  codec_.height = kHeight;
  codec_.VP8()->numberOfTemporalLayers = 1;
  rate_allocator_.reset(new SimulcastRateAllocator(codec_));

  if (wrapper_initialized_) {
    fallback_wrapper_->Release();
  }

  fake_encoder_->init_encode_return_code_ = WEBRTC_VIDEO_CODEC_OK;
  EXPECT_EQ(WEBRTC_VIDEO_CODEC_OK,
            fallback_wrapper_->InitEncode(&codec_, kSettings));

  if (!wrapper_initialized_) {
    fallback_wrapper_->SetRates(VideoEncoder::RateControlParameters(
        rate_allocator_->Allocate(
            VideoBitrateAllocationParameters(300000, kFramerate)),
        kFramerate));
  }
  wrapper_initialized_ = true;
}

void VideoEncoderSoftwareFallbackWrapperTestBase::UtilizeFallbackEncoder() {
  if (!wrapper_initialized_) {
    fallback_wrapper_->RegisterEncodeCompleteCallback(&callback_);
    EXPECT_EQ(&callback_, fake_encoder_->encode_complete_callback_);
  }

  // Register with failing fake encoder. Should succeed with VP8 fallback.
  codec_.codecType = kVideoCodecVP8;
  codec_.maxFramerate = kFramerate;
  codec_.width = kWidth;
  codec_.height = kHeight;
  codec_.VP8()->numberOfTemporalLayers = 1;
  rate_allocator_.reset(new SimulcastRateAllocator(codec_));

  if (wrapper_initialized_) {
    fallback_wrapper_->Release();
  }

  fake_encoder_->init_encode_return_code_ = WEBRTC_VIDEO_CODEC_ERROR;
  EXPECT_EQ(WEBRTC_VIDEO_CODEC_OK,
            fallback_wrapper_->InitEncode(&codec_, kSettings));
  fallback_wrapper_->SetRates(VideoEncoder::RateControlParameters(
      rate_allocator_->Allocate(
          VideoBitrateAllocationParameters(300000, kFramerate)),
      kFramerate));

  int callback_count = callback_.callback_count_;
  int encode_count = fake_encoder_->encode_count_;
  EncodeFrame();
  EXPECT_EQ(encode_count, fake_encoder_->encode_count_);
  EXPECT_EQ(callback_count + 1, callback_.callback_count_);
}

void VideoEncoderSoftwareFallbackWrapperTestBase::FallbackFromEncodeRequest() {
  fallback_wrapper_->RegisterEncodeCompleteCallback(&callback_);
  codec_.codecType = kVideoCodecVP8;
  codec_.maxFramerate = kFramerate;
  codec_.width = kWidth;
  codec_.height = kHeight;
  codec_.VP8()->numberOfTemporalLayers = 1;
  rate_allocator_.reset(new SimulcastRateAllocator(codec_));
  if (wrapper_initialized_) {
    fallback_wrapper_->Release();
  }
  fallback_wrapper_->InitEncode(&codec_, kSettings);
  fallback_wrapper_->SetRates(VideoEncoder::RateControlParameters(
      rate_allocator_->Allocate(
          VideoBitrateAllocationParameters(300000, kFramerate)),
      kFramerate));
  EXPECT_EQ(1, fake_encoder_->init_encode_count_);

  // Have the non-fallback encoder request a software fallback.
  fake_encoder_->encode_return_code_ = WEBRTC_VIDEO_CODEC_FALLBACK_SOFTWARE;
  int callback_count = callback_.callback_count_;
  int encode_count = fake_encoder_->encode_count_;
  EncodeFrame();
  // Single encode request, which returned failure.
  EXPECT_EQ(encode_count + 1, fake_encoder_->encode_count_);
  EXPECT_EQ(callback_count + 1, callback_.callback_count_);
}

TEST_F(VideoEncoderSoftwareFallbackWrapperTest, InitializesEncoder) {
  VideoCodec codec = {};
  fallback_wrapper_->InitEncode(&codec, kSettings);
  EXPECT_EQ(1, fake_encoder_->init_encode_count_);
}

TEST_F(VideoEncoderSoftwareFallbackWrapperTest, EncodeRequestsFallback) {
  FallbackFromEncodeRequest();
  // After fallback, further encodes shouldn't hit the fake encoder.
  int encode_count = fake_encoder_->encode_count_;
  EncodeFrame();
  EXPECT_EQ(encode_count, fake_encoder_->encode_count_);
}

TEST_F(VideoEncoderSoftwareFallbackWrapperTest, CanUtilizeFallbackEncoder) {
  UtilizeFallbackEncoder();
  EXPECT_EQ(WEBRTC_VIDEO_CODEC_OK, fallback_wrapper_->Release());
}

TEST_F(VideoEncoderSoftwareFallbackWrapperTest,
       InternalEncoderReleasedDuringFallback) {
  EXPECT_EQ(0, fake_encoder_->init_encode_count_);
  EXPECT_EQ(0, fake_encoder_->release_count_);

  InitEncode();

  EXPECT_EQ(1, fake_encoder_->init_encode_count_);
  EXPECT_EQ(0, fake_encoder_->release_count_);

  UtilizeFallbackEncoder();

  // One successful InitEncode(), one failed.
  EXPECT_EQ(2, fake_encoder_->init_encode_count_);
  EXPECT_EQ(1, fake_encoder_->release_count_);

  EXPECT_EQ(WEBRTC_VIDEO_CODEC_OK, fallback_wrapper_->Release());

  // No extra release when the fallback is released.
  EXPECT_EQ(2, fake_encoder_->init_encode_count_);
  EXPECT_EQ(1, fake_encoder_->release_count_);
}

TEST_F(VideoEncoderSoftwareFallbackWrapperTest,
       InternalEncoderNotEncodingDuringFallback) {
  UtilizeFallbackEncoder();
  int encode_count = fake_encoder_->encode_count_;
  EncodeFrame();
  EXPECT_EQ(encode_count, fake_encoder_->encode_count_);

  EXPECT_EQ(WEBRTC_VIDEO_CODEC_OK, fallback_wrapper_->Release());
}

TEST_F(VideoEncoderSoftwareFallbackWrapperTest,
       CanRegisterCallbackWhileUsingFallbackEncoder) {
  InitEncode();
  EXPECT_EQ(&callback_, fake_encoder_->encode_complete_callback_);

  UtilizeFallbackEncoder();

  // Registering an encode-complete callback will now pass to the fallback
  // instead of the main encoder.
  FakeEncodedImageCallback callback2;
  fallback_wrapper_->RegisterEncodeCompleteCallback(&callback2);
  EXPECT_EQ(&callback_, fake_encoder_->encode_complete_callback_);

  // Encoding a frame using the fallback should arrive at the new callback.
  std::vector<VideoFrameType> types(1, VideoFrameType::kVideoFrameKey);
  frame_->set_timestamp(frame_->timestamp() + 1000);
  EXPECT_EQ(WEBRTC_VIDEO_CODEC_OK, fallback_wrapper_->Encode(*frame_, &types));
  EXPECT_EQ(callback2.callback_count_, 1);

  // Re-initialize to use the main encoder, the new callback should be in use.
  InitEncode();
  EXPECT_EQ(&callback2, fake_encoder_->encode_complete_callback_);

  frame_->set_timestamp(frame_->timestamp() + 2000);
  EXPECT_EQ(WEBRTC_VIDEO_CODEC_OK, fallback_wrapper_->Encode(*frame_, &types));
  EXPECT_EQ(callback2.callback_count_, 2);
}

TEST_F(VideoEncoderSoftwareFallbackWrapperTest,
       SupportsNativeHandleForwardedWithoutFallback) {
  fallback_wrapper_->GetEncoderInfo();
  EXPECT_EQ(1, fake_encoder_->supports_native_handle_count_);
}

TEST_F(VideoEncoderSoftwareFallbackWrapperTest,
       SupportsNativeHandleNotForwardedDuringFallback) {
  // Fake encoder signals support for native handle, default (libvpx) does not.
  fake_encoder_->supports_native_handle_ = true;
  EXPECT_TRUE(fallback_wrapper_->GetEncoderInfo().supports_native_handle);
  UtilizeFallbackEncoder();
  EXPECT_FALSE(fallback_wrapper_->GetEncoderInfo().supports_native_handle);
  // Both times, both encoders are queried.
  EXPECT_EQ(2, fake_encoder_->supports_native_handle_count_);
  EXPECT_EQ(WEBRTC_VIDEO_CODEC_OK, fallback_wrapper_->Release());
}

TEST_F(VideoEncoderSoftwareFallbackWrapperTest, ReportsImplementationName) {
  codec_.width = kWidth;
  codec_.height = kHeight;
  fallback_wrapper_->RegisterEncodeCompleteCallback(&callback_);
  fallback_wrapper_->InitEncode(&codec_, kSettings);
  EncodeFrame();
  CheckLastEncoderName("fake-encoder");
}

TEST_F(VideoEncoderSoftwareFallbackWrapperTest,
       IsQpTrustedNotForwardedDuringFallback) {
  // Fake encoder signals trusted QP, default (libvpx) does not.
  fake_encoder_->is_qp_trusted_ = true;
  EXPECT_TRUE(fake_encoder_->GetEncoderInfo().is_qp_trusted.value_or(false));
  UtilizeFallbackEncoder();
  EXPECT_FALSE(fallback_wrapper_->GetEncoderInfo().is_qp_trusted.has_value());
  EXPECT_EQ(WEBRTC_VIDEO_CODEC_OK, fallback_wrapper_->Release());
}

TEST_F(VideoEncoderSoftwareFallbackWrapperTest,
       ReportsFallbackImplementationName) {
  UtilizeFallbackEncoder();
  CheckLastEncoderName(fake_sw_encoder_->implementation_name_.c_str());
}

TEST_F(VideoEncoderSoftwareFallbackWrapperTest,
       OnEncodeFallbackNativeFrameScaledIfFallbackDoesNotSupportNativeFrames) {
  fake_encoder_->supports_native_handle_ = true;
  fake_sw_encoder_->supports_native_handle_ = false;
  InitEncode();
  int width = codec_.width * 2;
  int height = codec_.height * 2;
  VideoFrame native_frame = test::FakeNativeBuffer::CreateFrame(
      width, height, 0, 0, VideoRotation::kVideoRotation_0);
  std::vector<VideoFrameType> types(1, VideoFrameType::kVideoFrameKey);
  fake_encoder_->encode_return_code_ = WEBRTC_VIDEO_CODEC_FALLBACK_SOFTWARE;

  EXPECT_EQ(WEBRTC_VIDEO_CODEC_OK,
            fallback_wrapper_->Encode(native_frame, &types));
  EXPECT_EQ(1, fake_sw_encoder_->encode_count_);
  ASSERT_TRUE(fake_sw_encoder_->last_video_frame_.has_value());
  EXPECT_NE(VideoFrameBuffer::Type::kNative,
            fake_sw_encoder_->last_video_frame_->video_frame_buffer()->type());
  EXPECT_EQ(codec_.width, fake_sw_encoder_->last_video_frame_->width());
  EXPECT_EQ(codec_.height, fake_sw_encoder_->last_video_frame_->height());
}

TEST_F(VideoEncoderSoftwareFallbackWrapperTest,
       OnEncodeFallbackNativeFrameForwardedToFallbackIfItSupportsNativeFrames) {
  fake_encoder_->supports_native_handle_ = true;
  fake_sw_encoder_->supports_native_handle_ = true;
  InitEncode();
  int width = codec_.width * 2;
  int height = codec_.height * 2;
  VideoFrame native_frame = test::FakeNativeBuffer::CreateFrame(
      width, height, 0, 0, VideoRotation::kVideoRotation_0);
  std::vector<VideoFrameType> types(1, VideoFrameType::kVideoFrameKey);
  fake_encoder_->encode_return_code_ = WEBRTC_VIDEO_CODEC_FALLBACK_SOFTWARE;

  EXPECT_EQ(WEBRTC_VIDEO_CODEC_OK,
            fallback_wrapper_->Encode(native_frame, &types));
  EXPECT_EQ(1, fake_sw_encoder_->encode_count_);
  ASSERT_TRUE(fake_sw_encoder_->last_video_frame_.has_value());
  EXPECT_EQ(VideoFrameBuffer::Type::kNative,
            fake_sw_encoder_->last_video_frame_->video_frame_buffer()->type());
  EXPECT_EQ(native_frame.width(), fake_sw_encoder_->last_video_frame_->width());
  EXPECT_EQ(native_frame.height(),
            fake_sw_encoder_->last_video_frame_->height());
}

namespace {
const int kBitrateKbps = 200;
const int kMinPixelsPerFrame = 1;
const char kFieldTrial[] = "WebRTC-VP8-Forced-Fallback-Encoder-v2";
}  // namespace

class ForcedFallbackTest : public VideoEncoderSoftwareFallbackWrapperTestBase {
 public:
  explicit ForcedFallbackTest(const std::string& field_trials)
      : VideoEncoderSoftwareFallbackWrapperTestBase(field_trials,
                                                    VP8Encoder::Create()) {}

  ~ForcedFallbackTest() override {}

 protected:
  void SetUp() override {
    clock_.SetTime(Timestamp::Micros(1234));
    ConfigureVp8Codec();
  }

  void TearDown() override {
    if (wrapper_initialized_) {
      EXPECT_EQ(WEBRTC_VIDEO_CODEC_OK, fallback_wrapper_->Release());
    }
  }

  void ConfigureVp8Codec() {
    codec_.codecType = kVideoCodecVP8;
    codec_.maxFramerate = kFramerate;
    codec_.width = kWidth;
    codec_.height = kHeight;
    codec_.VP8()->numberOfTemporalLayers = 1;
    codec_.VP8()->automaticResizeOn = true;
    codec_.SetFrameDropEnabled(true);
    rate_allocator_.reset(new SimulcastRateAllocator(codec_));
  }

  void InitEncode(int width, int height) {
    codec_.width = width;
    codec_.height = height;
    if (wrapper_initialized_) {
      fallback_wrapper_->Release();
    }
    EXPECT_EQ(WEBRTC_VIDEO_CODEC_OK,
              fallback_wrapper_->InitEncode(&codec_, kSettings));
    fallback_wrapper_->RegisterEncodeCompleteCallback(&callback_);
    wrapper_initialized_ = true;
    SetRateAllocation(kBitrateKbps);
  }

  void SetRateAllocation(uint32_t bitrate_kbps) {
    fallback_wrapper_->SetRates(VideoEncoder::RateControlParameters(
        rate_allocator_->Allocate(
            VideoBitrateAllocationParameters(bitrate_kbps * 1000, kFramerate)),
        kFramerate));
  }

  void EncodeFrameAndVerifyLastName(const char* expected_name) {
    EncodeFrameAndVerifyLastName(expected_name, WEBRTC_VIDEO_CODEC_OK);
  }

  void EncodeFrameAndVerifyLastName(const char* expected_name,
                                    int expected_ret) {
    EncodeFrame(expected_ret);
    CheckLastEncoderName(expected_name);
  }

  rtc::ScopedFakeClock clock_;
};

class ForcedFallbackTestEnabled : public ForcedFallbackTest {
 public:
  ForcedFallbackTestEnabled()
      : ForcedFallbackTest(std::string(kFieldTrial) + "/Enabled-" +
                           std::to_string(kMinPixelsPerFrame) + "," +
                           std::to_string(kWidth * kHeight) + ",30000/") {}
};

class ForcedFallbackTestDisabled : public ForcedFallbackTest {
 public:
  ForcedFallbackTestDisabled()
      : ForcedFallbackTest(std::string(kFieldTrial) + "/Disabled/") {}
};

TEST_F(ForcedFallbackTestDisabled, NoFallbackWithoutFieldTrial) {
  // Resolution above max threshold.
  InitEncode(kWidth + 1, kHeight);
  EXPECT_EQ(1, fake_encoder_->init_encode_count_);
  EncodeFrameAndVerifyLastName("fake-encoder");

  // Resolution at max threshold.
  InitEncode(kWidth, kHeight);
  EncodeFrameAndVerifyLastName("fake-encoder");
}

TEST_F(ForcedFallbackTestEnabled, FallbackIfAtMaxResolutionLimit) {
  // Resolution above max threshold.
  InitEncode(kWidth + 1, kHeight);
  EXPECT_EQ(1, fake_encoder_->init_encode_count_);
  EncodeFrameAndVerifyLastName("fake-encoder");

  // Resolution at max threshold.
  InitEncode(kWidth, kHeight);
  EncodeFrameAndVerifyLastName("libvpx");
}

TEST_F(ForcedFallbackTestEnabled, FallbackIsKeptWhenInitEncodeIsCalled) {
  // Resolution above max threshold.
  InitEncode(kWidth + 1, kHeight);
  EXPECT_EQ(1, fake_encoder_->init_encode_count_);
  EncodeFrameAndVerifyLastName("fake-encoder");

  // Resolution at max threshold.
  InitEncode(kWidth, kHeight);
  EncodeFrameAndVerifyLastName("libvpx");

  // Re-initialize encoder, still expect fallback.
  InitEncode(kWidth / 2, kHeight / 2);
  EXPECT_EQ(1, fake_encoder_->init_encode_count_);  // No change.
  EncodeFrameAndVerifyLastName("libvpx");
}

TEST_F(ForcedFallbackTestEnabled, FallbackIsEndedWhenResolutionIsTooLarge) {
  // Resolution above max threshold.
  InitEncode(kWidth + 1, kHeight);
  EXPECT_EQ(1, fake_encoder_->init_encode_count_);
  EncodeFrameAndVerifyLastName("fake-encoder");

  // Resolution at max threshold.
  InitEncode(kWidth, kHeight);
  EncodeFrameAndVerifyLastName("libvpx");

  // Re-initialize encoder with a larger resolution, expect no fallback.
  InitEncode(kWidth + 1, kHeight);
  EXPECT_EQ(2, fake_encoder_->init_encode_count_);
  EncodeFrameAndVerifyLastName("fake-encoder");
}

TEST_F(ForcedFallbackTestEnabled, FallbackIsEndedForNonValidSettings) {
  // Resolution at max threshold.
  InitEncode(kWidth, kHeight);
  EncodeFrameAndVerifyLastName("libvpx");

  // Re-initialize encoder with invalid setting, expect no fallback.
  codec_.numberOfSimulcastStreams = 2;
  InitEncode(kWidth, kHeight);
  EXPECT_EQ(1, fake_encoder_->init_encode_count_);
  EncodeFrameAndVerifyLastName("fake-encoder");

  // Re-initialize encoder with valid setting.
  codec_.numberOfSimulcastStreams = 1;
  InitEncode(kWidth, kHeight);
  EXPECT_EQ(1, fake_encoder_->init_encode_count_);
  EncodeFrameAndVerifyLastName("libvpx");
}

TEST_F(ForcedFallbackTestEnabled, MultipleStartEndFallback) {
  const int kNumRuns = 5;
  for (int i = 1; i <= kNumRuns; ++i) {
    // Resolution at max threshold.
    InitEncode(kWidth, kHeight);
    EncodeFrameAndVerifyLastName("libvpx");
    // Resolution above max threshold.
    InitEncode(kWidth + 1, kHeight);
    EXPECT_EQ(i, fake_encoder_->init_encode_count_);
    EncodeFrameAndVerifyLastName("fake-encoder");
  }
}

TEST_F(ForcedFallbackTestDisabled, GetScaleSettings) {
  // Resolution above max threshold.
  InitEncode(kWidth + 1, kHeight);
  EXPECT_EQ(1, fake_encoder_->init_encode_count_);
  EncodeFrameAndVerifyLastName("fake-encoder");

  // Default min pixels per frame should be used.
  const auto settings = fallback_wrapper_->GetEncoderInfo().scaling_settings;
  EXPECT_TRUE(settings.thresholds.has_value());
  EXPECT_EQ(kDefaultMinPixelsPerFrame, settings.min_pixels_per_frame);
}

TEST_F(ForcedFallbackTestEnabled, GetScaleSettingsWithNoFallback) {
  // Resolution above max threshold.
  InitEncode(kWidth + 1, kHeight);
  EncodeFrameAndVerifyLastName("fake-encoder");

  // Configured min pixels per frame should be used.
  const auto settings = fallback_wrapper_->GetEncoderInfo().scaling_settings;
  EXPECT_EQ(kMinPixelsPerFrame, settings.min_pixels_per_frame);
  ASSERT_TRUE(settings.thresholds);
  EXPECT_EQ(kLowThreshold, settings.thresholds->low);
  EXPECT_EQ(kHighThreshold, settings.thresholds->high);
}

TEST_F(ForcedFallbackTestEnabled, GetScaleSettingsWithFallback) {
  // Resolution at max threshold.
  InitEncode(kWidth, kHeight);
  EncodeFrameAndVerifyLastName("libvpx");

  // Configured min pixels per frame should be used.
  const auto settings = fallback_wrapper_->GetEncoderInfo().scaling_settings;
  EXPECT_TRUE(settings.thresholds.has_value());
  EXPECT_EQ(kMinPixelsPerFrame, settings.min_pixels_per_frame);
}

TEST_F(ForcedFallbackTestEnabled, ScalingDisabledIfResizeOff) {
  // Resolution at max threshold.
  codec_.VP8()->automaticResizeOn = false;
  InitEncode(kWidth, kHeight);
  EncodeFrameAndVerifyLastName("libvpx");

  // Should be disabled for automatic resize off.
  const auto settings = fallback_wrapper_->GetEncoderInfo().scaling_settings;
  EXPECT_FALSE(settings.thresholds.has_value());
}

TEST(SoftwareFallbackEncoderTest, BothRateControllersNotTrusted) {
  auto* sw_encoder = new ::testing::NiceMock<MockVideoEncoder>();
  auto* hw_encoder = new ::testing::NiceMock<MockVideoEncoder>();

  EXPECT_CALL(*sw_encoder, GetEncoderInfo())
      .WillRepeatedly(Return(GetEncoderInfoWithTrustedRateController(false)));
  EXPECT_CALL(*hw_encoder, GetEncoderInfo())
      .WillRepeatedly(Return(GetEncoderInfoWithTrustedRateController(false)));

  std::unique_ptr<VideoEncoder> wrapper =
      CreateVideoEncoderSoftwareFallbackWrapper(
          std::unique_ptr<VideoEncoder>(sw_encoder),
          std::unique_ptr<VideoEncoder>(hw_encoder));
  EXPECT_FALSE(wrapper->GetEncoderInfo().has_trusted_rate_controller);
}

TEST(SoftwareFallbackEncoderTest, SwRateControllerTrusted) {
  auto* sw_encoder = new ::testing::NiceMock<MockVideoEncoder>();
  auto* hw_encoder = new ::testing::NiceMock<MockVideoEncoder>();
  EXPECT_CALL(*sw_encoder, GetEncoderInfo())
      .WillRepeatedly(Return(GetEncoderInfoWithTrustedRateController(true)));
  EXPECT_CALL(*hw_encoder, GetEncoderInfo())
      .WillRepeatedly(Return(GetEncoderInfoWithTrustedRateController(false)));

  std::unique_ptr<VideoEncoder> wrapper =
      CreateVideoEncoderSoftwareFallbackWrapper(
          std::unique_ptr<VideoEncoder>(sw_encoder),
          std::unique_ptr<VideoEncoder>(hw_encoder));
  EXPECT_FALSE(wrapper->GetEncoderInfo().has_trusted_rate_controller);
}

TEST(SoftwareFallbackEncoderTest, HwRateControllerTrusted) {
  auto* sw_encoder = new ::testing::NiceMock<MockVideoEncoder>();
  auto* hw_encoder = new ::testing::NiceMock<MockVideoEncoder>();
  EXPECT_CALL(*sw_encoder, GetEncoderInfo())
      .WillRepeatedly(Return(GetEncoderInfoWithTrustedRateController(false)));
  EXPECT_CALL(*hw_encoder, GetEncoderInfo())
      .WillRepeatedly(Return(GetEncoderInfoWithTrustedRateController(true)));

  std::unique_ptr<VideoEncoder> wrapper =
      CreateVideoEncoderSoftwareFallbackWrapper(
          std::unique_ptr<VideoEncoder>(sw_encoder),
          std::unique_ptr<VideoEncoder>(hw_encoder));
  EXPECT_TRUE(wrapper->GetEncoderInfo().has_trusted_rate_controller);

  VideoCodec codec_ = {};
  codec_.width = 100;
  codec_.height = 100;
  wrapper->InitEncode(&codec_, kSettings);

  // Trigger fallback to software.
  EXPECT_CALL(*hw_encoder, Encode)
      .WillOnce(Return(WEBRTC_VIDEO_CODEC_FALLBACK_SOFTWARE));
  VideoFrame frame = VideoFrame::Builder()
                         .set_video_frame_buffer(I420Buffer::Create(100, 100))
                         .build();
  wrapper->Encode(frame, nullptr);

  EXPECT_FALSE(wrapper->GetEncoderInfo().has_trusted_rate_controller);
}

TEST(SoftwareFallbackEncoderTest, BothRateControllersTrusted) {
  auto* sw_encoder = new ::testing::NiceMock<MockVideoEncoder>();
  auto* hw_encoder = new ::testing::NiceMock<MockVideoEncoder>();
  EXPECT_CALL(*sw_encoder, GetEncoderInfo())
      .WillRepeatedly(Return(GetEncoderInfoWithTrustedRateController(true)));
  EXPECT_CALL(*hw_encoder, GetEncoderInfo())
      .WillRepeatedly(Return(GetEncoderInfoWithTrustedRateController(true)));

  std::unique_ptr<VideoEncoder> wrapper =
      CreateVideoEncoderSoftwareFallbackWrapper(
          std::unique_ptr<VideoEncoder>(sw_encoder),
          std::unique_ptr<VideoEncoder>(hw_encoder));
  EXPECT_TRUE(wrapper->GetEncoderInfo().has_trusted_rate_controller);
}

TEST(SoftwareFallbackEncoderTest, ReportsHardwareAccelerated) {
  auto* sw_encoder = new ::testing::NiceMock<MockVideoEncoder>();
  auto* hw_encoder = new ::testing::NiceMock<MockVideoEncoder>();
  EXPECT_CALL(*sw_encoder, GetEncoderInfo())
      .WillRepeatedly(Return(GetEncoderInfoWithHardwareAccelerated(false)));
  EXPECT_CALL(*hw_encoder, GetEncoderInfo())
      .WillRepeatedly(Return(GetEncoderInfoWithHardwareAccelerated(true)));

  std::unique_ptr<VideoEncoder> wrapper =
      CreateVideoEncoderSoftwareFallbackWrapper(
          std::unique_ptr<VideoEncoder>(sw_encoder),
          std::unique_ptr<VideoEncoder>(hw_encoder));
  EXPECT_TRUE(wrapper->GetEncoderInfo().is_hardware_accelerated);

  VideoCodec codec_ = {};
  codec_.width = 100;
  codec_.height = 100;
  wrapper->InitEncode(&codec_, kSettings);

  // Trigger fallback to software.
  EXPECT_CALL(*hw_encoder, Encode)
      .WillOnce(Return(WEBRTC_VIDEO_CODEC_FALLBACK_SOFTWARE));
  VideoFrame frame = VideoFrame::Builder()
                         .set_video_frame_buffer(I420Buffer::Create(100, 100))
                         .build();
  wrapper->Encode(frame, nullptr);
  EXPECT_FALSE(wrapper->GetEncoderInfo().is_hardware_accelerated);
}

TEST(SoftwareFallbackEncoderTest, ConfigureHardwareOnSecondAttempt) {
  auto* sw_encoder = new ::testing::NiceMock<MockVideoEncoder>();
  auto* hw_encoder = new ::testing::NiceMock<MockVideoEncoder>();
  EXPECT_CALL(*sw_encoder, GetEncoderInfo())
      .WillRepeatedly(Return(GetEncoderInfoWithHardwareAccelerated(false)));
  EXPECT_CALL(*hw_encoder, GetEncoderInfo())
      .WillRepeatedly(Return(GetEncoderInfoWithHardwareAccelerated(true)));

  std::unique_ptr<VideoEncoder> wrapper =
      CreateVideoEncoderSoftwareFallbackWrapper(
          std::unique_ptr<VideoEncoder>(sw_encoder),
          std::unique_ptr<VideoEncoder>(hw_encoder));
  EXPECT_TRUE(wrapper->GetEncoderInfo().is_hardware_accelerated);

  // Initialize the encoder. When HW attempt fails we fallback to SW.
  VideoCodec codec_ = {};
  codec_.width = 100;
  codec_.height = 100;
  EXPECT_CALL(*hw_encoder, InitEncode(_, _))
      .WillOnce(Return(WEBRTC_VIDEO_CODEC_ERR_PARAMETER));
  EXPECT_CALL(*sw_encoder, InitEncode(_, _))
      .WillOnce(Return(WEBRTC_VIDEO_CODEC_OK));
  wrapper->InitEncode(&codec_, kSettings);

  // When reconfiguring (Release+InitEncode) we should re-attempt HW.
  wrapper->Release();
  EXPECT_CALL(*hw_encoder, InitEncode(_, _))
      .WillOnce(Return(WEBRTC_VIDEO_CODEC_OK));
  wrapper->InitEncode(&codec_, kSettings);
}

class PreferTemporalLayersFallbackTest : public ::testing::Test {
 public:
  PreferTemporalLayersFallbackTest() {}
  void SetUp() override {
    sw_ = new ::testing::NiceMock<MockVideoEncoder>();
    sw_info_.implementation_name = "sw";
    EXPECT_CALL(*sw_, GetEncoderInfo).WillRepeatedly([&]() {
      return sw_info_;
    });
    EXPECT_CALL(*sw_, InitEncode(_, _, _))
        .WillRepeatedly(Return(WEBRTC_VIDEO_CODEC_OK));

    hw_ = new ::testing::NiceMock<MockVideoEncoder>();
    hw_info_.implementation_name = "hw";
    EXPECT_CALL(*hw_, GetEncoderInfo()).WillRepeatedly([&]() {
      return hw_info_;
    });
    EXPECT_CALL(*hw_, InitEncode(_, _, _))
        .WillRepeatedly(Return(WEBRTC_VIDEO_CODEC_OK));

    wrapper_ = CreateVideoEncoderSoftwareFallbackWrapper(
        std::unique_ptr<VideoEncoder>(sw_), std::unique_ptr<VideoEncoder>(hw_),
        /*prefer_temporal_support=*/true);

    codec_settings.codecType = kVideoCodecVP8;
    codec_settings.maxFramerate = kFramerate;
    codec_settings.width = kWidth;
    codec_settings.height = kHeight;
    codec_settings.numberOfSimulcastStreams = 1;
    codec_settings.VP8()->numberOfTemporalLayers = 1;
  }

 protected:
  void SetSupportsLayers(VideoEncoder::EncoderInfo* info, bool tl_enabled) {
    int num_layers = 1;
    if (tl_enabled) {
      num_layers = codec_settings.VP8()->numberOfTemporalLayers;
    }
    SetNumLayers(info, num_layers);
  }

  void SetNumLayers(VideoEncoder::EncoderInfo* info, int num_layers) {
    info->fps_allocation[0].clear();
    for (int i = 0; i < num_layers; ++i) {
      info->fps_allocation[0].push_back(
          VideoEncoder::EncoderInfo::kMaxFramerateFraction >>
          (num_layers - i - 1));
    }
  }

  VideoCodec codec_settings;
  ::testing::NiceMock<MockVideoEncoder>* sw_;
  ::testing::NiceMock<MockVideoEncoder>* hw_;
  VideoEncoder::EncoderInfo sw_info_;
  VideoEncoder::EncoderInfo hw_info_;
  std::unique_ptr<VideoEncoder> wrapper_;
};

TEST_F(PreferTemporalLayersFallbackTest, UsesMainWhenLayersNotUsed) {
  codec_settings.VP8()->numberOfTemporalLayers = 1;
  SetSupportsLayers(&hw_info_, true);
  SetSupportsLayers(&sw_info_, true);
  EXPECT_EQ(WEBRTC_VIDEO_CODEC_OK,
            wrapper_->InitEncode(&codec_settings, kSettings));
  EXPECT_EQ(wrapper_->GetEncoderInfo().implementation_name, "hw");
}

TEST_F(PreferTemporalLayersFallbackTest, UsesMainWhenLayersSupported) {
  codec_settings.VP8()->numberOfTemporalLayers = 2;
  SetSupportsLayers(&hw_info_, true);
  SetSupportsLayers(&sw_info_, true);
  EXPECT_EQ(WEBRTC_VIDEO_CODEC_OK,
            wrapper_->InitEncode(&codec_settings, kSettings));
  EXPECT_EQ(wrapper_->GetEncoderInfo().implementation_name, "hw");
}

TEST_F(PreferTemporalLayersFallbackTest,
       UsesFallbackWhenLayersNotSupportedOnMain) {
  codec_settings.VP8()->numberOfTemporalLayers = 2;
  SetSupportsLayers(&hw_info_, false);
  SetSupportsLayers(&sw_info_, true);
  EXPECT_EQ(WEBRTC_VIDEO_CODEC_OK,
            wrapper_->InitEncode(&codec_settings, kSettings));
  EXPECT_EQ(wrapper_->GetEncoderInfo().implementation_name, "sw");
}

TEST_F(PreferTemporalLayersFallbackTest, UsesMainWhenNeitherSupportsTemporal) {
  codec_settings.VP8()->numberOfTemporalLayers = 2;
  SetSupportsLayers(&hw_info_, false);
  SetSupportsLayers(&sw_info_, false);
  EXPECT_EQ(WEBRTC_VIDEO_CODEC_OK,
            wrapper_->InitEncode(&codec_settings, kSettings));
  EXPECT_EQ(wrapper_->GetEncoderInfo().implementation_name, "hw");
}

TEST_F(PreferTemporalLayersFallbackTest, UsesFallbackWhenLayersAreUndefined) {
  codec_settings.VP8()->numberOfTemporalLayers = 2;
  SetNumLayers(&hw_info_, 1);
  SetNumLayers(&sw_info_, 0);
  EXPECT_EQ(WEBRTC_VIDEO_CODEC_OK,
            wrapper_->InitEncode(&codec_settings, kSettings));
  EXPECT_EQ(wrapper_->GetEncoderInfo().implementation_name, "sw");
}

TEST_F(PreferTemporalLayersFallbackTest, PrimesEncoderOnSwitch) {
  codec_settings.VP8()->numberOfTemporalLayers = 2;
  // Both support temporal layers, will use main one.
  SetSupportsLayers(&hw_info_, true);
  SetSupportsLayers(&sw_info_, true);

  // On first InitEncode most params have no state and will not be
  // called to update.
  EXPECT_CALL(*hw_, RegisterEncodeCompleteCallback).Times(0);
  EXPECT_CALL(*sw_, RegisterEncodeCompleteCallback).Times(0);

  EXPECT_CALL(*hw_, SetFecControllerOverride).Times(0);
  EXPECT_CALL(*sw_, SetFecControllerOverride).Times(0);

  EXPECT_CALL(*hw_, SetRates).Times(0);
  EXPECT_CALL(*hw_, SetRates).Times(0);

  EXPECT_CALL(*hw_, OnPacketLossRateUpdate).Times(0);
  EXPECT_CALL(*sw_, OnPacketLossRateUpdate).Times(0);

  EXPECT_CALL(*hw_, OnRttUpdate).Times(0);
  EXPECT_CALL(*sw_, OnRttUpdate).Times(0);

  EXPECT_CALL(*hw_, OnLossNotification).Times(0);
  EXPECT_CALL(*sw_, OnLossNotification).Times(0);

  EXPECT_EQ(WEBRTC_VIDEO_CODEC_OK,
            wrapper_->InitEncode(&codec_settings, kSettings));
  EXPECT_EQ(wrapper_->GetEncoderInfo().implementation_name, "hw");

  FakeEncodedImageCallback callback1;
  class DummyFecControllerOverride : public FecControllerOverride {
   public:
    void SetFecAllowed(bool fec_allowed) override {}
  };
  DummyFecControllerOverride fec_controller_override1;
  VideoEncoder::RateControlParameters rate_params1;
  float packet_loss1 = 0.1;
  int64_t rtt1 = 1;
  VideoEncoder::LossNotification lntf1;

  EXPECT_CALL(*hw_, RegisterEncodeCompleteCallback(&callback1));
  EXPECT_CALL(*sw_, RegisterEncodeCompleteCallback).Times(0);
  wrapper_->RegisterEncodeCompleteCallback(&callback1);

  EXPECT_CALL(*hw_, SetFecControllerOverride(&fec_controller_override1));
  EXPECT_CALL(*sw_, SetFecControllerOverride).Times(1);
  wrapper_->SetFecControllerOverride(&fec_controller_override1);

  EXPECT_CALL(*hw_, SetRates(rate_params1));
  EXPECT_CALL(*sw_, SetRates).Times(0);
  wrapper_->SetRates(rate_params1);

  EXPECT_CALL(*hw_, OnPacketLossRateUpdate(packet_loss1));
  EXPECT_CALL(*sw_, OnPacketLossRateUpdate).Times(0);
  wrapper_->OnPacketLossRateUpdate(packet_loss1);

  EXPECT_CALL(*hw_, OnRttUpdate(rtt1));
  EXPECT_CALL(*sw_, OnRttUpdate).Times(0);
  wrapper_->OnRttUpdate(rtt1);

  EXPECT_CALL(*hw_, OnLossNotification).Times(1);
  EXPECT_CALL(*sw_, OnLossNotification).Times(0);
  wrapper_->OnLossNotification(lntf1);

  // Release and re-init, with fallback to software. This should trigger
  // the software encoder to be primed with the current state.
  wrapper_->Release();
  EXPECT_CALL(*sw_, RegisterEncodeCompleteCallback(&callback1));
  EXPECT_CALL(*hw_, RegisterEncodeCompleteCallback).Times(0);

  EXPECT_CALL(*sw_, SetFecControllerOverride).Times(0);
  EXPECT_CALL(*hw_, SetFecControllerOverride).Times(0);

  // Rate control parameters are cleared on InitEncode.
  EXPECT_CALL(*sw_, SetRates).Times(0);
  EXPECT_CALL(*hw_, SetRates).Times(0);

  EXPECT_CALL(*sw_, OnPacketLossRateUpdate(packet_loss1));
  EXPECT_CALL(*hw_, OnPacketLossRateUpdate).Times(0);

  EXPECT_CALL(*sw_, OnRttUpdate(rtt1));
  EXPECT_CALL(*hw_, OnRttUpdate).Times(0);

  EXPECT_CALL(*sw_, OnLossNotification).Times(1);
  EXPECT_CALL(*hw_, OnLossNotification).Times(0);

  SetSupportsLayers(&hw_info_, false);
  EXPECT_EQ(WEBRTC_VIDEO_CODEC_OK,
            wrapper_->InitEncode(&codec_settings, kSettings));
  EXPECT_EQ(wrapper_->GetEncoderInfo().implementation_name, "sw");

  // Update with all-new params for the software encoder.
  FakeEncodedImageCallback callback2;
  DummyFecControllerOverride fec_controller_override2;
  VideoEncoder::RateControlParameters rate_params2;
  float packet_loss2 = 0.2;
  int64_t rtt2 = 2;
  VideoEncoder::LossNotification lntf2;

  EXPECT_CALL(*sw_, RegisterEncodeCompleteCallback(&callback2));
  EXPECT_CALL(*hw_, RegisterEncodeCompleteCallback).Times(0);
  wrapper_->RegisterEncodeCompleteCallback(&callback2);

  EXPECT_CALL(*sw_, SetFecControllerOverride(&fec_controller_override2));
  EXPECT_CALL(*hw_, SetFecControllerOverride).Times(1);
  wrapper_->SetFecControllerOverride(&fec_controller_override2);

  EXPECT_CALL(*sw_, SetRates(rate_params2));
  EXPECT_CALL(*hw_, SetRates).Times(0);
  wrapper_->SetRates(rate_params2);

  EXPECT_CALL(*sw_, OnPacketLossRateUpdate(packet_loss2));
  EXPECT_CALL(*hw_, OnPacketLossRateUpdate).Times(0);
  wrapper_->OnPacketLossRateUpdate(packet_loss2);

  EXPECT_CALL(*sw_, OnRttUpdate(rtt2));
  EXPECT_CALL(*hw_, OnRttUpdate).Times(0);
  wrapper_->OnRttUpdate(rtt2);

  EXPECT_CALL(*sw_, OnLossNotification).Times(1);
  EXPECT_CALL(*hw_, OnLossNotification).Times(0);
  wrapper_->OnLossNotification(lntf2);

  // Release and re-init, back to main encoder. This should trigger
  // the main encoder to be primed with the current state.
  wrapper_->Release();
  EXPECT_CALL(*hw_, RegisterEncodeCompleteCallback(&callback2));
  EXPECT_CALL(*sw_, RegisterEncodeCompleteCallback).Times(0);

  EXPECT_CALL(*hw_, SetFecControllerOverride).Times(0);
  EXPECT_CALL(*sw_, SetFecControllerOverride).Times(0);

  // Rate control parameters are cleared on InitEncode.
  EXPECT_CALL(*sw_, SetRates).Times(0);
  EXPECT_CALL(*hw_, SetRates).Times(0);

  EXPECT_CALL(*hw_, OnPacketLossRateUpdate(packet_loss2));
  EXPECT_CALL(*sw_, OnPacketLossRateUpdate).Times(0);

  EXPECT_CALL(*hw_, OnRttUpdate(rtt2));
  EXPECT_CALL(*sw_, OnRttUpdate).Times(0);

  EXPECT_CALL(*hw_, OnLossNotification).Times(1);
  EXPECT_CALL(*sw_, OnLossNotification).Times(0);

  SetSupportsLayers(&hw_info_, true);
  EXPECT_EQ(WEBRTC_VIDEO_CODEC_OK,
            wrapper_->InitEncode(&codec_settings, kSettings));
  EXPECT_EQ(wrapper_->GetEncoderInfo().implementation_name, "hw");
}

struct ResolutionBasedFallbackTestParams {
  std::string test_name;
  std::string field_trials = "";
  VideoCodecType codec_type = kVideoCodecGeneric;
  int width = 16;
  int height = 16;
  std::string expect_implementation_name;
};

using ResolutionBasedFallbackTest =
    ::testing::TestWithParam<ResolutionBasedFallbackTestParams>;

INSTANTIATE_TEST_SUITE_P(
    VideoEncoderFallbackTest,
    ResolutionBasedFallbackTest,
    ValuesIn<ResolutionBasedFallbackTestParams>(
        {{.test_name = "FallbackNotConfigured",
          .expect_implementation_name = "primary"},
         {.test_name = "ResolutionAboveFallbackThreshold",
          .field_trials = "WebRTC-Video-EncoderFallbackSettings/"
                          "resolution_threshold_px:255/",
          .expect_implementation_name = "primary"},
         {.test_name = "ResolutionEqualFallbackThreshold",
          .field_trials = "WebRTC-Video-EncoderFallbackSettings/"
                          "resolution_threshold_px:256/",
          .expect_implementation_name = "fallback"},
         {.test_name = "GenericFallbackSettingsTakePrecedence",
          .field_trials =
              "WebRTC-Video-EncoderFallbackSettings/"
              "resolution_threshold_px:255/"
              "WebRTC-VP8-Forced-Fallback-Encoder-v2/Enabled-1,256,1/",
          .codec_type = kVideoCodecVP8,
          .expect_implementation_name = "primary"}}),
    [](const testing::TestParamInfo<ResolutionBasedFallbackTest::ParamType>&
           info) { return info.param.test_name; });

TEST_P(ResolutionBasedFallbackTest, VerifyForcedEncoderFallback) {
  const ResolutionBasedFallbackTestParams& params = GetParam();
  test::ScopedFieldTrials field_trials(params.field_trials);
  auto primary = new test::FakeEncoder(Clock::GetRealTimeClock());
  auto fallback = new test::FakeEncoder(Clock::GetRealTimeClock());
  auto encoder = CreateVideoEncoderSoftwareFallbackWrapper(
      std::unique_ptr<VideoEncoder>(fallback),
      std::unique_ptr<VideoEncoder>(primary),
      /*prefer_temporal_support=*/false);
  primary->SetImplementationName("primary");
  fallback->SetImplementationName("fallback");
  VideoCodec codec;
  codec.codecType = params.codec_type;
  codec.width = params.width;
  codec.height = params.height;
  encoder->InitEncode(&codec, kSettings);
  EXPECT_EQ(encoder->GetEncoderInfo().implementation_name,
            params.expect_implementation_name);
}

}  // namespace webrtc
