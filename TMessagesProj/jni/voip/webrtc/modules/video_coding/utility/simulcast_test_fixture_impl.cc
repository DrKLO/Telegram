/*
 *  Copyright (c) 2014 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "modules/video_coding/utility/simulcast_test_fixture_impl.h"

#include <algorithm>
#include <map>
#include <memory>
#include <vector>

#include "api/environment/environment.h"
#include "api/environment/environment_factory.h"
#include "api/video/encoded_image.h"
#include "api/video_codecs/sdp_video_format.h"
#include "api/video_codecs/video_encoder.h"
#include "common_video/libyuv/include/webrtc_libyuv.h"
#include "modules/video_coding/include/video_codec_interface.h"
#include "modules/video_coding/include/video_coding_defines.h"
#include "rtc_base/checks.h"
#include "test/gtest.h"

using ::testing::_;
using ::testing::AllOf;
using ::testing::Field;
using ::testing::Return;

namespace webrtc {
namespace test {

namespace {

const int kDefaultWidth = 1280;
const int kDefaultHeight = 720;
const int kNumberOfSimulcastStreams = 3;
const int kColorY = 66;
const int kColorU = 22;
const int kColorV = 33;
const int kMaxBitrates[kNumberOfSimulcastStreams] = {150, 600, 1200};
const int kMinBitrates[kNumberOfSimulcastStreams] = {50, 150, 600};
const int kTargetBitrates[kNumberOfSimulcastStreams] = {100, 450, 1000};
const float kMaxFramerates[kNumberOfSimulcastStreams] = {30, 30, 30};
const int kScaleResolutionDownBy[kNumberOfSimulcastStreams] = {4, 2, 1};
const int kDefaultTemporalLayerProfile[3] = {3, 3, 3};
const int kNoTemporalLayerProfile[3] = {0, 0, 0};

const VideoEncoder::Capabilities kCapabilities(false);
const VideoEncoder::Settings kSettings(kCapabilities, 1, 1200);

template <typename T>
void SetExpectedValues3(T value0, T value1, T value2, T* expected_values) {
  expected_values[0] = value0;
  expected_values[1] = value1;
  expected_values[2] = value2;
}

enum PlaneType {
  kYPlane = 0,
  kUPlane = 1,
  kVPlane = 2,
  kNumOfPlanes = 3,
};

}  // namespace

class SimulcastTestFixtureImpl::TestEncodedImageCallback
    : public EncodedImageCallback {
 public:
  TestEncodedImageCallback() {
    memset(temporal_layer_, -1, sizeof(temporal_layer_));
    memset(layer_sync_, false, sizeof(layer_sync_));
  }

  Result OnEncodedImage(const EncodedImage& encoded_image,
                        const CodecSpecificInfo* codec_specific_info) override {
    bool is_vp8 = (codec_specific_info->codecType == kVideoCodecVP8);
    bool is_h264 = (codec_specific_info->codecType == kVideoCodecH264);
    // Only store the base layer.
    if (encoded_image.SimulcastIndex().value_or(0) == 0) {
      if (encoded_image._frameType == VideoFrameType::kVideoFrameKey) {
        encoded_key_frame_.SetEncodedData(EncodedImageBuffer::Create(
            encoded_image.data(), encoded_image.size()));
        encoded_key_frame_._frameType = VideoFrameType::kVideoFrameKey;
      } else {
        encoded_frame_.SetEncodedData(EncodedImageBuffer::Create(
            encoded_image.data(), encoded_image.size()));
      }
    }
    if (is_vp8) {
      layer_sync_[encoded_image.SimulcastIndex().value_or(0)] =
          codec_specific_info->codecSpecific.VP8.layerSync;
      temporal_layer_[encoded_image.SimulcastIndex().value_or(0)] =
          codec_specific_info->codecSpecific.VP8.temporalIdx;
    } else if (is_h264) {
      layer_sync_[encoded_image.SimulcastIndex().value_or(0)] =
          codec_specific_info->codecSpecific.H264.base_layer_sync;
      temporal_layer_[encoded_image.SimulcastIndex().value_or(0)] =
          codec_specific_info->codecSpecific.H264.temporal_idx;
    }
    return Result(Result::OK, encoded_image.RtpTimestamp());
  }
  // This method only makes sense for VP8.
  void GetLastEncodedFrameInfo(int* temporal_layer,
                               bool* layer_sync,
                               int stream) {
    *temporal_layer = temporal_layer_[stream];
    *layer_sync = layer_sync_[stream];
  }
  void GetLastEncodedKeyFrame(EncodedImage* encoded_key_frame) {
    *encoded_key_frame = encoded_key_frame_;
  }
  void GetLastEncodedFrame(EncodedImage* encoded_frame) {
    *encoded_frame = encoded_frame_;
  }

 private:
  EncodedImage encoded_key_frame_;
  EncodedImage encoded_frame_;
  int temporal_layer_[kNumberOfSimulcastStreams];
  bool layer_sync_[kNumberOfSimulcastStreams];
};

class SimulcastTestFixtureImpl::TestDecodedImageCallback
    : public DecodedImageCallback {
 public:
  TestDecodedImageCallback() : decoded_frames_(0) {}
  int32_t Decoded(VideoFrame& decoded_image) override {
    rtc::scoped_refptr<I420BufferInterface> i420_buffer =
        decoded_image.video_frame_buffer()->ToI420();
    for (int i = 0; i < decoded_image.width(); ++i) {
      EXPECT_NEAR(kColorY, i420_buffer->DataY()[i], 1);
    }

    // TODO(mikhal): Verify the difference between U,V and the original.
    for (int i = 0; i < i420_buffer->ChromaWidth(); ++i) {
      EXPECT_NEAR(kColorU, i420_buffer->DataU()[i], 4);
      EXPECT_NEAR(kColorV, i420_buffer->DataV()[i], 4);
    }
    decoded_frames_++;
    return 0;
  }
  int32_t Decoded(VideoFrame& decoded_image, int64_t decode_time_ms) override {
    RTC_DCHECK_NOTREACHED();
    return -1;
  }
  void Decoded(VideoFrame& decoded_image,
               absl::optional<int32_t> decode_time_ms,
               absl::optional<uint8_t> qp) override {
    Decoded(decoded_image);
  }
  int DecodedFrames() { return decoded_frames_; }

 private:
  int decoded_frames_;
};

namespace {

void SetPlane(uint8_t* data, uint8_t value, int width, int height, int stride) {
  for (int i = 0; i < height; i++, data += stride) {
    // Setting allocated area to zero - setting only image size to
    // requested values - will make it easier to distinguish between image
    // size and frame size (accounting for stride).
    memset(data, value, width);
    memset(data + width, 0, stride - width);
  }
}

// Fills in an I420Buffer from `plane_colors`.
void CreateImage(const rtc::scoped_refptr<I420Buffer>& buffer,
                 int plane_colors[kNumOfPlanes]) {
  SetPlane(buffer->MutableDataY(), plane_colors[0], buffer->width(),
           buffer->height(), buffer->StrideY());

  SetPlane(buffer->MutableDataU(), plane_colors[1], buffer->ChromaWidth(),
           buffer->ChromaHeight(), buffer->StrideU());

  SetPlane(buffer->MutableDataV(), plane_colors[2], buffer->ChromaWidth(),
           buffer->ChromaHeight(), buffer->StrideV());
}

void ConfigureStream(int width,
                     int height,
                     int max_bitrate,
                     int min_bitrate,
                     int target_bitrate,
                     float max_framerate,
                     SimulcastStream* stream,
                     int num_temporal_layers) {
  RTC_DCHECK(stream);
  stream->width = width;
  stream->height = height;
  stream->maxBitrate = max_bitrate;
  stream->minBitrate = min_bitrate;
  stream->targetBitrate = target_bitrate;
  stream->maxFramerate = max_framerate;
  if (num_temporal_layers >= 0) {
    stream->numberOfTemporalLayers = num_temporal_layers;
  }
  stream->qpMax = 45;
  stream->active = true;
}

}  // namespace

void SimulcastTestFixtureImpl::DefaultSettings(
    VideoCodec* settings,
    const int* temporal_layer_profile,
    VideoCodecType codec_type,
    bool reverse_layer_order) {
  RTC_CHECK(settings);
  *settings = {};
  settings->codecType = codec_type;
  settings->startBitrate = 300;
  settings->minBitrate = 30;
  settings->maxBitrate = 0;
  settings->maxFramerate = 30;
  settings->width = kDefaultWidth;
  settings->height = kDefaultHeight;
  settings->numberOfSimulcastStreams = kNumberOfSimulcastStreams;
  settings->active = true;
  ASSERT_EQ(3, kNumberOfSimulcastStreams);
  int layer_order[3] = {0, 1, 2};
  if (reverse_layer_order) {
    layer_order[0] = 2;
    layer_order[2] = 0;
  }
  settings->timing_frame_thresholds = {kDefaultTimingFramesDelayMs,
                                       kDefaultOutlierFrameSizePercent};
  ConfigureStream(kDefaultWidth / 4, kDefaultHeight / 4, kMaxBitrates[0],
                  kMinBitrates[0], kTargetBitrates[0], kMaxFramerates[0],
                  &settings->simulcastStream[layer_order[0]],
                  temporal_layer_profile[0]);
  ConfigureStream(kDefaultWidth / 2, kDefaultHeight / 2, kMaxBitrates[1],
                  kMinBitrates[1], kTargetBitrates[1], kMaxFramerates[1],
                  &settings->simulcastStream[layer_order[1]],
                  temporal_layer_profile[1]);
  ConfigureStream(kDefaultWidth, kDefaultHeight, kMaxBitrates[2],
                  kMinBitrates[2], kTargetBitrates[2], kMaxFramerates[2],
                  &settings->simulcastStream[layer_order[2]],
                  temporal_layer_profile[2]);
  settings->SetFrameDropEnabled(true);
  if (codec_type == kVideoCodecVP8) {
    settings->VP8()->denoisingOn = true;
    settings->VP8()->automaticResizeOn = false;
    settings->VP8()->keyFrameInterval = 3000;
  } else {
    settings->H264()->keyFrameInterval = 3000;
  }
}

SimulcastTestFixtureImpl::SimulcastTestFixtureImpl(
    std::unique_ptr<VideoEncoderFactory> encoder_factory,
    std::unique_ptr<VideoDecoderFactory> decoder_factory,
    SdpVideoFormat video_format)
    : codec_type_(PayloadStringToCodecType(video_format.name)) {
  Environment env = CreateEnvironment();
  encoder_ = encoder_factory->CreateVideoEncoder(video_format);
  decoder_ = decoder_factory->Create(env, video_format);
  SetUpCodec((codec_type_ == kVideoCodecVP8 || codec_type_ == kVideoCodecH264)
                 ? kDefaultTemporalLayerProfile
                 : kNoTemporalLayerProfile);
}

SimulcastTestFixtureImpl::~SimulcastTestFixtureImpl() {
  encoder_->Release();
  decoder_->Release();
}

void SimulcastTestFixtureImpl::SetUpCodec(const int* temporal_layer_profile) {
  encoder_->RegisterEncodeCompleteCallback(&encoder_callback_);
  decoder_->RegisterDecodeCompleteCallback(&decoder_callback_);
  DefaultSettings(&settings_, temporal_layer_profile, codec_type_);
  SetUpRateAllocator();
  EXPECT_EQ(0, encoder_->InitEncode(&settings_, kSettings));
  VideoDecoder::Settings decoder_settings;
  decoder_settings.set_max_render_resolution({kDefaultWidth, kDefaultHeight});
  decoder_settings.set_codec_type(codec_type_);
  EXPECT_TRUE(decoder_->Configure(decoder_settings));
  input_buffer_ = I420Buffer::Create(kDefaultWidth, kDefaultHeight);
  input_buffer_->InitializeData();
  input_frame_ = std::make_unique<webrtc::VideoFrame>(
      webrtc::VideoFrame::Builder()
          .set_video_frame_buffer(input_buffer_)
          .set_rotation(webrtc::kVideoRotation_0)
          .set_timestamp_us(0)
          .build());
}

void SimulcastTestFixtureImpl::SetUpRateAllocator() {
  rate_allocator_.reset(new SimulcastRateAllocator(settings_));
}

void SimulcastTestFixtureImpl::SetRates(uint32_t bitrate_kbps, uint32_t fps) {
  encoder_->SetRates(VideoEncoder::RateControlParameters(
      rate_allocator_->Allocate(
          VideoBitrateAllocationParameters(bitrate_kbps * 1000, fps)),
      static_cast<double>(fps)));
}

void SimulcastTestFixtureImpl::RunActiveStreamsTest(
    const std::vector<bool> active_streams) {
  std::vector<VideoFrameType> frame_types(kNumberOfSimulcastStreams,
                                          VideoFrameType::kVideoFrameDelta);
  UpdateActiveStreams(active_streams);
  // Set sufficient bitrate for all streams so we can test active without
  // bitrate being an issue.
  SetRates(kMaxBitrates[0] + kMaxBitrates[1] + kMaxBitrates[2], 30);

  ExpectStreams(VideoFrameType::kVideoFrameKey, active_streams);
  input_frame_->set_timestamp(input_frame_->timestamp() + 3000);
  EXPECT_EQ(0, encoder_->Encode(*input_frame_, &frame_types));

  ExpectStreams(VideoFrameType::kVideoFrameDelta, active_streams);
  input_frame_->set_timestamp(input_frame_->timestamp() + 3000);
  EXPECT_EQ(0, encoder_->Encode(*input_frame_, &frame_types));
}

void SimulcastTestFixtureImpl::UpdateActiveStreams(
    const std::vector<bool> active_streams) {
  ASSERT_EQ(static_cast<int>(active_streams.size()), kNumberOfSimulcastStreams);
  for (size_t i = 0; i < active_streams.size(); ++i) {
    settings_.simulcastStream[i].active = active_streams[i];
  }
  // Re initialize the allocator and encoder with the new settings.
  // TODO(bugs.webrtc.org/8807): Currently, we do a full "hard"
  // reconfiguration of the allocator and encoder. When the video bitrate
  // allocator has support for updating active streams without a
  // reinitialization, we can just call that here instead.
  SetUpRateAllocator();
  EXPECT_EQ(0, encoder_->InitEncode(&settings_, kSettings));
}

void SimulcastTestFixtureImpl::ExpectStream(VideoFrameType frame_type,
                                            int scaleResolutionDownBy) {
  EXPECT_CALL(
      encoder_callback_,
      OnEncodedImage(AllOf(Field(&EncodedImage::_frameType, frame_type),
                           Field(&EncodedImage::_encodedWidth,
                                 kDefaultWidth / scaleResolutionDownBy),
                           Field(&EncodedImage::_encodedHeight,
                                 kDefaultHeight / scaleResolutionDownBy)),
                     _))
      .Times(1)
      .WillRepeatedly(Return(
          EncodedImageCallback::Result(EncodedImageCallback::Result::OK, 0)));
}

void SimulcastTestFixtureImpl::ExpectStreams(
    VideoFrameType frame_type,
    const std::vector<bool> expected_streams_active) {
  ASSERT_EQ(static_cast<int>(expected_streams_active.size()),
            kNumberOfSimulcastStreams);
  for (size_t i = 0; i < kNumberOfSimulcastStreams; i++) {
    if (expected_streams_active[i]) {
      ExpectStream(frame_type, kScaleResolutionDownBy[i]);
    }
  }
}

void SimulcastTestFixtureImpl::ExpectStreams(VideoFrameType frame_type,
                                             int expected_video_streams) {
  ASSERT_GE(expected_video_streams, 0);
  ASSERT_LE(expected_video_streams, kNumberOfSimulcastStreams);
  std::vector<bool> expected_streams_active(kNumberOfSimulcastStreams, false);
  for (int i = 0; i < expected_video_streams; ++i) {
    expected_streams_active[i] = true;
  }
  ExpectStreams(frame_type, expected_streams_active);
}

void SimulcastTestFixtureImpl::VerifyTemporalIdxAndSyncForAllSpatialLayers(
    TestEncodedImageCallback* encoder_callback,
    const int* expected_temporal_idx,
    const bool* expected_layer_sync,
    int num_spatial_layers) {
  int temporal_layer = -1;
  bool layer_sync = false;
  for (int i = 0; i < num_spatial_layers; i++) {
    encoder_callback->GetLastEncodedFrameInfo(&temporal_layer, &layer_sync, i);
    EXPECT_EQ(expected_temporal_idx[i], temporal_layer);
    EXPECT_EQ(expected_layer_sync[i], layer_sync);
  }
}

// For some codecs (VP8) expect all active streams to generate a key frame even
// though a key frame was only requested for some of them.
void SimulcastTestFixtureImpl::TestKeyFrameRequestsOnAllStreams() {
  SetRates(kMaxBitrates[2], 30);  // To get all three streams.
  std::vector<VideoFrameType> frame_types(kNumberOfSimulcastStreams,
                                          VideoFrameType::kVideoFrameDelta);
  ExpectStreams(VideoFrameType::kVideoFrameKey, kNumberOfSimulcastStreams);
  EXPECT_EQ(0, encoder_->Encode(*input_frame_, &frame_types));

  ExpectStreams(VideoFrameType::kVideoFrameDelta, kNumberOfSimulcastStreams);
  input_frame_->set_timestamp(input_frame_->timestamp() + 3000);
  EXPECT_EQ(0, encoder_->Encode(*input_frame_, &frame_types));

  frame_types[0] = VideoFrameType::kVideoFrameKey;
  ExpectStreams(VideoFrameType::kVideoFrameKey, kNumberOfSimulcastStreams);
  input_frame_->set_timestamp(input_frame_->timestamp() + 3000);
  EXPECT_EQ(0, encoder_->Encode(*input_frame_, &frame_types));

  std::fill(frame_types.begin(), frame_types.end(),
            VideoFrameType::kVideoFrameDelta);
  frame_types[1] = VideoFrameType::kVideoFrameKey;
  ExpectStreams(VideoFrameType::kVideoFrameKey, kNumberOfSimulcastStreams);
  input_frame_->set_timestamp(input_frame_->timestamp() + 3000);
  EXPECT_EQ(0, encoder_->Encode(*input_frame_, &frame_types));

  std::fill(frame_types.begin(), frame_types.end(),
            VideoFrameType::kVideoFrameDelta);
  frame_types[2] = VideoFrameType::kVideoFrameKey;
  ExpectStreams(VideoFrameType::kVideoFrameKey, kNumberOfSimulcastStreams);
  input_frame_->set_timestamp(input_frame_->timestamp() + 3000);
  EXPECT_EQ(0, encoder_->Encode(*input_frame_, &frame_types));

  std::fill(frame_types.begin(), frame_types.end(),
            VideoFrameType::kVideoFrameDelta);
  ExpectStreams(VideoFrameType::kVideoFrameDelta, kNumberOfSimulcastStreams);
  input_frame_->set_timestamp(input_frame_->timestamp() + 3000);
  EXPECT_EQ(0, encoder_->Encode(*input_frame_, &frame_types));
}

// For some codecs (H264) expect only particular active streams to generate a
// key frame when a key frame was only requested for some of them.
void SimulcastTestFixtureImpl::TestKeyFrameRequestsOnSpecificStreams() {
  SetRates(kMaxBitrates[2], 30);  // To get all three streams.
  std::vector<VideoFrameType> frame_types(kNumberOfSimulcastStreams,
                                          VideoFrameType::kVideoFrameDelta);
  ExpectStreams(VideoFrameType::kVideoFrameKey, kNumberOfSimulcastStreams);
  EXPECT_EQ(0, encoder_->Encode(*input_frame_, &frame_types));

  ExpectStreams(VideoFrameType::kVideoFrameDelta, kNumberOfSimulcastStreams);
  input_frame_->set_timestamp(input_frame_->timestamp() + 3000);
  EXPECT_EQ(0, encoder_->Encode(*input_frame_, &frame_types));

  frame_types[0] = VideoFrameType::kVideoFrameKey;
  ExpectStream(VideoFrameType::kVideoFrameKey, kScaleResolutionDownBy[0]);
  ExpectStream(VideoFrameType::kVideoFrameDelta, kScaleResolutionDownBy[1]);
  ExpectStream(VideoFrameType::kVideoFrameDelta, kScaleResolutionDownBy[2]);
  input_frame_->set_timestamp(input_frame_->timestamp() + 3000);
  EXPECT_EQ(0, encoder_->Encode(*input_frame_, &frame_types));

  std::fill(frame_types.begin(), frame_types.end(),
            VideoFrameType::kVideoFrameDelta);
  frame_types[1] = VideoFrameType::kVideoFrameKey;
  ExpectStream(VideoFrameType::kVideoFrameDelta, kScaleResolutionDownBy[0]);
  ExpectStream(VideoFrameType::kVideoFrameKey, kScaleResolutionDownBy[1]);
  ExpectStream(VideoFrameType::kVideoFrameDelta, kScaleResolutionDownBy[2]);
  input_frame_->set_timestamp(input_frame_->timestamp() + 3000);
  EXPECT_EQ(0, encoder_->Encode(*input_frame_, &frame_types));

  std::fill(frame_types.begin(), frame_types.end(),
            VideoFrameType::kVideoFrameDelta);
  frame_types[2] = VideoFrameType::kVideoFrameKey;
  ExpectStream(VideoFrameType::kVideoFrameDelta, kScaleResolutionDownBy[0]);
  ExpectStream(VideoFrameType::kVideoFrameDelta, kScaleResolutionDownBy[1]);
  ExpectStream(VideoFrameType::kVideoFrameKey, kScaleResolutionDownBy[2]);
  input_frame_->set_timestamp(input_frame_->timestamp() + 3000);
  EXPECT_EQ(0, encoder_->Encode(*input_frame_, &frame_types));

  std::fill(frame_types.begin(), frame_types.end(),
            VideoFrameType::kVideoFrameDelta);
  frame_types[0] = VideoFrameType::kVideoFrameKey;
  frame_types[2] = VideoFrameType::kVideoFrameKey;
  ExpectStream(VideoFrameType::kVideoFrameKey, kScaleResolutionDownBy[0]);
  ExpectStream(VideoFrameType::kVideoFrameDelta, kScaleResolutionDownBy[1]);
  ExpectStream(VideoFrameType::kVideoFrameKey, kScaleResolutionDownBy[2]);
  input_frame_->set_timestamp(input_frame_->timestamp() + 3000);
  EXPECT_EQ(0, encoder_->Encode(*input_frame_, &frame_types));

  std::fill(frame_types.begin(), frame_types.end(),
            VideoFrameType::kVideoFrameKey);
  ExpectStream(VideoFrameType::kVideoFrameKey, kScaleResolutionDownBy[0]);
  ExpectStream(VideoFrameType::kVideoFrameKey, kScaleResolutionDownBy[1]);
  ExpectStream(VideoFrameType::kVideoFrameKey, kScaleResolutionDownBy[2]);
  input_frame_->set_timestamp(input_frame_->timestamp() + 3000);
  EXPECT_EQ(0, encoder_->Encode(*input_frame_, &frame_types));

  std::fill(frame_types.begin(), frame_types.end(),
            VideoFrameType::kVideoFrameDelta);
  ExpectStreams(VideoFrameType::kVideoFrameDelta, kNumberOfSimulcastStreams);
  input_frame_->set_timestamp(input_frame_->timestamp() + 3000);
  EXPECT_EQ(0, encoder_->Encode(*input_frame_, &frame_types));
}

void SimulcastTestFixtureImpl::TestPaddingAllStreams() {
  // We should always encode the base layer.
  SetRates(kMinBitrates[0] - 1, 30);
  std::vector<VideoFrameType> frame_types(kNumberOfSimulcastStreams,
                                          VideoFrameType::kVideoFrameDelta);
  ExpectStreams(VideoFrameType::kVideoFrameKey, 1);
  EXPECT_EQ(0, encoder_->Encode(*input_frame_, &frame_types));

  ExpectStreams(VideoFrameType::kVideoFrameDelta, 1);
  input_frame_->set_timestamp(input_frame_->timestamp() + 3000);
  EXPECT_EQ(0, encoder_->Encode(*input_frame_, &frame_types));
}

void SimulcastTestFixtureImpl::TestPaddingTwoStreams() {
  // We have just enough to get only the first stream and padding for two.
  SetRates(kMinBitrates[0], 30);
  std::vector<VideoFrameType> frame_types(kNumberOfSimulcastStreams,
                                          VideoFrameType::kVideoFrameDelta);
  ExpectStreams(VideoFrameType::kVideoFrameKey, 1);
  EXPECT_EQ(0, encoder_->Encode(*input_frame_, &frame_types));

  ExpectStreams(VideoFrameType::kVideoFrameDelta, 1);
  input_frame_->set_timestamp(input_frame_->timestamp() + 3000);
  EXPECT_EQ(0, encoder_->Encode(*input_frame_, &frame_types));
}

void SimulcastTestFixtureImpl::TestPaddingTwoStreamsOneMaxedOut() {
  // We are just below limit of sending second stream, so we should get
  // the first stream maxed out (at `maxBitrate`), and padding for two.
  SetRates(kTargetBitrates[0] + kMinBitrates[1] - 1, 30);
  std::vector<VideoFrameType> frame_types(kNumberOfSimulcastStreams,
                                          VideoFrameType::kVideoFrameDelta);
  ExpectStreams(VideoFrameType::kVideoFrameKey, 1);
  EXPECT_EQ(0, encoder_->Encode(*input_frame_, &frame_types));

  ExpectStreams(VideoFrameType::kVideoFrameDelta, 1);
  input_frame_->set_timestamp(input_frame_->timestamp() + 3000);
  EXPECT_EQ(0, encoder_->Encode(*input_frame_, &frame_types));
}

void SimulcastTestFixtureImpl::TestPaddingOneStream() {
  // We have just enough to send two streams, so padding for one stream.
  SetRates(kTargetBitrates[0] + kMinBitrates[1], 30);
  std::vector<VideoFrameType> frame_types(kNumberOfSimulcastStreams,
                                          VideoFrameType::kVideoFrameDelta);
  ExpectStreams(VideoFrameType::kVideoFrameKey, 2);
  EXPECT_EQ(0, encoder_->Encode(*input_frame_, &frame_types));

  ExpectStreams(VideoFrameType::kVideoFrameDelta, 2);
  input_frame_->set_timestamp(input_frame_->timestamp() + 3000);
  EXPECT_EQ(0, encoder_->Encode(*input_frame_, &frame_types));
}

void SimulcastTestFixtureImpl::TestPaddingOneStreamTwoMaxedOut() {
  // We are just below limit of sending third stream, so we should get
  // first stream's rate maxed out at `targetBitrate`, second at `maxBitrate`.
  SetRates(kTargetBitrates[0] + kTargetBitrates[1] + kMinBitrates[2] - 1, 30);
  std::vector<VideoFrameType> frame_types(kNumberOfSimulcastStreams,
                                          VideoFrameType::kVideoFrameDelta);
  ExpectStreams(VideoFrameType::kVideoFrameKey, 2);
  EXPECT_EQ(0, encoder_->Encode(*input_frame_, &frame_types));

  ExpectStreams(VideoFrameType::kVideoFrameDelta, 2);
  input_frame_->set_timestamp(input_frame_->timestamp() + 3000);
  EXPECT_EQ(0, encoder_->Encode(*input_frame_, &frame_types));
}

void SimulcastTestFixtureImpl::TestSendAllStreams() {
  // We have just enough to send all streams.
  SetRates(kTargetBitrates[0] + kTargetBitrates[1] + kMinBitrates[2], 30);
  std::vector<VideoFrameType> frame_types(kNumberOfSimulcastStreams,
                                          VideoFrameType::kVideoFrameDelta);
  ExpectStreams(VideoFrameType::kVideoFrameKey, 3);
  EXPECT_EQ(0, encoder_->Encode(*input_frame_, &frame_types));

  ExpectStreams(VideoFrameType::kVideoFrameDelta, 3);
  input_frame_->set_timestamp(input_frame_->timestamp() + 3000);
  EXPECT_EQ(0, encoder_->Encode(*input_frame_, &frame_types));
}

void SimulcastTestFixtureImpl::TestDisablingStreams() {
  // We should get three media streams.
  SetRates(kMaxBitrates[0] + kMaxBitrates[1] + kMaxBitrates[2], 30);
  std::vector<VideoFrameType> frame_types(kNumberOfSimulcastStreams,
                                          VideoFrameType::kVideoFrameDelta);
  ExpectStreams(VideoFrameType::kVideoFrameKey, 3);
  EXPECT_EQ(0, encoder_->Encode(*input_frame_, &frame_types));

  ExpectStreams(VideoFrameType::kVideoFrameDelta, 3);
  input_frame_->set_timestamp(input_frame_->timestamp() + 3000);
  EXPECT_EQ(0, encoder_->Encode(*input_frame_, &frame_types));

  // We should only get two streams and padding for one.
  SetRates(kTargetBitrates[0] + kTargetBitrates[1] + kMinBitrates[2] / 2, 30);
  ExpectStreams(VideoFrameType::kVideoFrameDelta, 2);
  input_frame_->set_timestamp(input_frame_->timestamp() + 3000);
  EXPECT_EQ(0, encoder_->Encode(*input_frame_, &frame_types));

  // We should only get the first stream and padding for two.
  SetRates(kTargetBitrates[0] + kMinBitrates[1] / 2, 30);
  ExpectStreams(VideoFrameType::kVideoFrameDelta, 1);
  input_frame_->set_timestamp(input_frame_->timestamp() + 3000);
  EXPECT_EQ(0, encoder_->Encode(*input_frame_, &frame_types));

  // We don't have enough bitrate for the thumbnail stream, but we should get
  // it anyway with current configuration.
  SetRates(kTargetBitrates[0] - 1, 30);
  ExpectStreams(VideoFrameType::kVideoFrameDelta, 1);
  input_frame_->set_timestamp(input_frame_->timestamp() + 3000);
  EXPECT_EQ(0, encoder_->Encode(*input_frame_, &frame_types));

  // We should only get two streams and padding for one.
  SetRates(kTargetBitrates[0] + kTargetBitrates[1] + kMinBitrates[2] / 2, 30);
  // We get a key frame because a new stream is being enabled.
  ExpectStreams(VideoFrameType::kVideoFrameKey, 2);
  input_frame_->set_timestamp(input_frame_->timestamp() + 3000);
  EXPECT_EQ(0, encoder_->Encode(*input_frame_, &frame_types));

  // We should get all three streams.
  SetRates(kTargetBitrates[0] + kTargetBitrates[1] + kTargetBitrates[2], 30);
  // We get a key frame because a new stream is being enabled.
  ExpectStreams(VideoFrameType::kVideoFrameKey, 3);
  input_frame_->set_timestamp(input_frame_->timestamp() + 3000);
  EXPECT_EQ(0, encoder_->Encode(*input_frame_, &frame_types));
}

void SimulcastTestFixtureImpl::TestActiveStreams() {
  // All streams on.
  RunActiveStreamsTest({true, true, true});
  // All streams off.
  RunActiveStreamsTest({false, false, false});
  // Low stream off.
  RunActiveStreamsTest({false, true, true});
  // Middle stream off.
  RunActiveStreamsTest({true, false, true});
  // High stream off.
  RunActiveStreamsTest({true, true, false});
  // Only low stream turned on.
  RunActiveStreamsTest({true, false, false});
  // Only middle stream turned on.
  RunActiveStreamsTest({false, true, false});
  // Only high stream turned on.
  RunActiveStreamsTest({false, false, true});
}

void SimulcastTestFixtureImpl::SwitchingToOneStream(int width, int height) {
  const int* temporal_layer_profile = nullptr;
  // Disable all streams except the last and set the bitrate of the last to
  // 100 kbps. This verifies the way GTP switches to screenshare mode.
  if (codec_type_ == kVideoCodecVP8) {
    settings_.VP8()->numberOfTemporalLayers = 1;
    temporal_layer_profile = kDefaultTemporalLayerProfile;
  } else {
    settings_.H264()->numberOfTemporalLayers = 1;
    temporal_layer_profile = kNoTemporalLayerProfile;
  }
  settings_.maxBitrate = 100;
  settings_.startBitrate = 100;
  settings_.width = width;
  settings_.height = height;
  for (int i = 0; i < settings_.numberOfSimulcastStreams - 1; ++i) {
    settings_.simulcastStream[i].maxBitrate = 0;
    settings_.simulcastStream[i].width = settings_.width;
    settings_.simulcastStream[i].height = settings_.height;
    settings_.simulcastStream[i].numberOfTemporalLayers = 1;
  }
  // Setting input image to new resolution.
  input_buffer_ = I420Buffer::Create(settings_.width, settings_.height);
  input_buffer_->InitializeData();

  input_frame_ = std::make_unique<webrtc::VideoFrame>(
      webrtc::VideoFrame::Builder()
          .set_video_frame_buffer(input_buffer_)
          .set_rotation(webrtc::kVideoRotation_0)
          .set_timestamp_us(0)
          .build());

  // The for loop above did not set the bitrate of the highest layer.
  settings_.simulcastStream[settings_.numberOfSimulcastStreams - 1].maxBitrate =
      0;
  // The highest layer has to correspond to the non-simulcast resolution.
  settings_.simulcastStream[settings_.numberOfSimulcastStreams - 1].width =
      settings_.width;
  settings_.simulcastStream[settings_.numberOfSimulcastStreams - 1].height =
      settings_.height;
  SetUpRateAllocator();
  EXPECT_EQ(0, encoder_->InitEncode(&settings_, kSettings));

  // Encode one frame and verify.
  SetRates(kMaxBitrates[0] + kMaxBitrates[1], 30);
  std::vector<VideoFrameType> frame_types(kNumberOfSimulcastStreams,
                                          VideoFrameType::kVideoFrameDelta);
  EXPECT_CALL(
      encoder_callback_,
      OnEncodedImage(AllOf(Field(&EncodedImage::_frameType,
                                 VideoFrameType::kVideoFrameKey),
                           Field(&EncodedImage::_encodedWidth, width),
                           Field(&EncodedImage::_encodedHeight, height)),
                     _))
      .Times(1)
      .WillRepeatedly(Return(
          EncodedImageCallback::Result(EncodedImageCallback::Result::OK, 0)));
  EXPECT_EQ(0, encoder_->Encode(*input_frame_, &frame_types));

  // Switch back.
  DefaultSettings(&settings_, temporal_layer_profile, codec_type_);
  // Start at the lowest bitrate for enabling base stream.
  settings_.startBitrate = kMinBitrates[0];
  SetUpRateAllocator();
  EXPECT_EQ(0, encoder_->InitEncode(&settings_, kSettings));
  SetRates(settings_.startBitrate, 30);
  ExpectStreams(VideoFrameType::kVideoFrameKey, 1);
  // Resize `input_frame_` to the new resolution.
  input_buffer_ = I420Buffer::Create(settings_.width, settings_.height);
  input_buffer_->InitializeData();
  input_frame_ = std::make_unique<webrtc::VideoFrame>(
      webrtc::VideoFrame::Builder()
          .set_video_frame_buffer(input_buffer_)
          .set_rotation(webrtc::kVideoRotation_0)
          .set_timestamp_us(0)
          .build());
  EXPECT_EQ(0, encoder_->Encode(*input_frame_, &frame_types));
}

void SimulcastTestFixtureImpl::TestSwitchingToOneStream() {
  SwitchingToOneStream(1024, 768);
}

void SimulcastTestFixtureImpl::TestSwitchingToOneOddStream() {
  SwitchingToOneStream(1023, 769);
}

void SimulcastTestFixtureImpl::TestSwitchingToOneSmallStream() {
  SwitchingToOneStream(4, 4);
}

// Test the layer pattern and sync flag for various spatial-temporal patterns.
// 3-3-3 pattern: 3 temporal layers for all spatial streams, so same
// temporal_layer id and layer_sync is expected for all streams.
void SimulcastTestFixtureImpl::TestSpatioTemporalLayers333PatternEncoder() {
  bool is_h264 = codec_type_ == kVideoCodecH264;
  TestEncodedImageCallback encoder_callback;
  encoder_->RegisterEncodeCompleteCallback(&encoder_callback);
  SetRates(kMaxBitrates[2], 30);  // To get all three streams.

  int expected_temporal_idx[3] = {-1, -1, -1};
  bool expected_layer_sync[3] = {false, false, false};

  // First frame: #0.
  EXPECT_EQ(0, encoder_->Encode(*input_frame_, NULL));
  SetExpectedValues3<int>(0, 0, 0, expected_temporal_idx);
  SetExpectedValues3<bool>(!is_h264, !is_h264, !is_h264, expected_layer_sync);
  VerifyTemporalIdxAndSyncForAllSpatialLayers(
      &encoder_callback, expected_temporal_idx, expected_layer_sync, 3);

  // Next frame: #1.
  input_frame_->set_timestamp(input_frame_->timestamp() + 3000);
  EXPECT_EQ(0, encoder_->Encode(*input_frame_, NULL));
  SetExpectedValues3<int>(2, 2, 2, expected_temporal_idx);
  SetExpectedValues3<bool>(true, true, true, expected_layer_sync);
  VerifyTemporalIdxAndSyncForAllSpatialLayers(
      &encoder_callback, expected_temporal_idx, expected_layer_sync, 3);

  // Next frame: #2.
  input_frame_->set_timestamp(input_frame_->timestamp() + 3000);
  EXPECT_EQ(0, encoder_->Encode(*input_frame_, NULL));
  SetExpectedValues3<int>(1, 1, 1, expected_temporal_idx);
  SetExpectedValues3<bool>(true, true, true, expected_layer_sync);
  VerifyTemporalIdxAndSyncForAllSpatialLayers(
      &encoder_callback, expected_temporal_idx, expected_layer_sync, 3);

  // Next frame: #3.
  input_frame_->set_timestamp(input_frame_->timestamp() + 3000);
  EXPECT_EQ(0, encoder_->Encode(*input_frame_, NULL));
  SetExpectedValues3<int>(2, 2, 2, expected_temporal_idx);
  SetExpectedValues3<bool>(false, false, false, expected_layer_sync);
  VerifyTemporalIdxAndSyncForAllSpatialLayers(
      &encoder_callback, expected_temporal_idx, expected_layer_sync, 3);

  // Next frame: #4.
  input_frame_->set_timestamp(input_frame_->timestamp() + 3000);
  EXPECT_EQ(0, encoder_->Encode(*input_frame_, NULL));
  SetExpectedValues3<int>(0, 0, 0, expected_temporal_idx);
  SetExpectedValues3<bool>(false, false, false, expected_layer_sync);
  VerifyTemporalIdxAndSyncForAllSpatialLayers(
      &encoder_callback, expected_temporal_idx, expected_layer_sync, 3);

  // Next frame: #5.
  input_frame_->set_timestamp(input_frame_->timestamp() + 3000);
  EXPECT_EQ(0, encoder_->Encode(*input_frame_, NULL));
  SetExpectedValues3<int>(2, 2, 2, expected_temporal_idx);
  SetExpectedValues3<bool>(is_h264, is_h264, is_h264, expected_layer_sync);
  VerifyTemporalIdxAndSyncForAllSpatialLayers(
      &encoder_callback, expected_temporal_idx, expected_layer_sync, 3);
}

// Test the layer pattern and sync flag for various spatial-temporal patterns.
// 3-2-1 pattern: 3 temporal layers for lowest resolution, 2 for middle, and
// 1 temporal layer for highest resolution.
// For this profile, we expect the temporal index pattern to be:
// 1st stream: 0, 2, 1, 2, ....
// 2nd stream: 0, 1, 0, 1, ...
// 3rd stream: -1, -1, -1, -1, ....
// Regarding the 3rd stream, note that a stream/encoder with 1 temporal layer
// should always have temporal layer idx set to kNoTemporalIdx = -1.
// Since CodecSpecificInfoVP8.temporalIdx is uint8_t, this will wrap to 255.
// TODO(marpan): Although this seems safe for now, we should fix this.
void SimulcastTestFixtureImpl::TestSpatioTemporalLayers321PatternEncoder() {
  EXPECT_EQ(codec_type_, kVideoCodecVP8);
  int temporal_layer_profile[3] = {3, 2, 1};
  SetUpCodec(temporal_layer_profile);
  TestEncodedImageCallback encoder_callback;
  encoder_->RegisterEncodeCompleteCallback(&encoder_callback);
  SetRates(kMaxBitrates[2], 30);  // To get all three streams.

  int expected_temporal_idx[3] = {-1, -1, -1};
  bool expected_layer_sync[3] = {false, false, false};

  // First frame: #0.
  EXPECT_EQ(0, encoder_->Encode(*input_frame_, NULL));
  SetExpectedValues3<int>(0, 0, 255, expected_temporal_idx);
  SetExpectedValues3<bool>(true, true, false, expected_layer_sync);
  VerifyTemporalIdxAndSyncForAllSpatialLayers(
      &encoder_callback, expected_temporal_idx, expected_layer_sync, 3);

  // Next frame: #1.
  input_frame_->set_timestamp(input_frame_->timestamp() + 3000);
  EXPECT_EQ(0, encoder_->Encode(*input_frame_, NULL));
  SetExpectedValues3<int>(2, 1, 255, expected_temporal_idx);
  SetExpectedValues3<bool>(true, true, false, expected_layer_sync);
  VerifyTemporalIdxAndSyncForAllSpatialLayers(
      &encoder_callback, expected_temporal_idx, expected_layer_sync, 3);

  // Next frame: #2.
  input_frame_->set_timestamp(input_frame_->timestamp() + 3000);
  EXPECT_EQ(0, encoder_->Encode(*input_frame_, NULL));
  SetExpectedValues3<int>(1, 0, 255, expected_temporal_idx);
  SetExpectedValues3<bool>(true, false, false, expected_layer_sync);
  VerifyTemporalIdxAndSyncForAllSpatialLayers(
      &encoder_callback, expected_temporal_idx, expected_layer_sync, 3);

  // Next frame: #3.
  input_frame_->set_timestamp(input_frame_->timestamp() + 3000);
  EXPECT_EQ(0, encoder_->Encode(*input_frame_, NULL));
  SetExpectedValues3<int>(2, 1, 255, expected_temporal_idx);
  SetExpectedValues3<bool>(false, false, false, expected_layer_sync);
  VerifyTemporalIdxAndSyncForAllSpatialLayers(
      &encoder_callback, expected_temporal_idx, expected_layer_sync, 3);

  // Next frame: #4.
  input_frame_->set_timestamp(input_frame_->timestamp() + 3000);
  EXPECT_EQ(0, encoder_->Encode(*input_frame_, NULL));
  SetExpectedValues3<int>(0, 0, 255, expected_temporal_idx);
  SetExpectedValues3<bool>(false, false, false, expected_layer_sync);
  VerifyTemporalIdxAndSyncForAllSpatialLayers(
      &encoder_callback, expected_temporal_idx, expected_layer_sync, 3);

  // Next frame: #5.
  input_frame_->set_timestamp(input_frame_->timestamp() + 3000);
  EXPECT_EQ(0, encoder_->Encode(*input_frame_, NULL));
  SetExpectedValues3<int>(2, 1, 255, expected_temporal_idx);
  SetExpectedValues3<bool>(false, true, false, expected_layer_sync);
  VerifyTemporalIdxAndSyncForAllSpatialLayers(
      &encoder_callback, expected_temporal_idx, expected_layer_sync, 3);
}

void SimulcastTestFixtureImpl::TestStrideEncodeDecode() {
  TestEncodedImageCallback encoder_callback;
  TestDecodedImageCallback decoder_callback;
  encoder_->RegisterEncodeCompleteCallback(&encoder_callback);
  decoder_->RegisterDecodeCompleteCallback(&decoder_callback);

  SetRates(kMaxBitrates[2], 30);  // To get all three streams.
  // Setting two (possibly) problematic use cases for stride:
  // 1. stride > width 2. stride_y != stride_uv/2
  int stride_y = kDefaultWidth + 20;
  int stride_uv = ((kDefaultWidth + 1) / 2) + 5;
  input_buffer_ = I420Buffer::Create(kDefaultWidth, kDefaultHeight, stride_y,
                                     stride_uv, stride_uv);
  input_frame_ = std::make_unique<webrtc::VideoFrame>(
      webrtc::VideoFrame::Builder()
          .set_video_frame_buffer(input_buffer_)
          .set_rotation(webrtc::kVideoRotation_0)
          .set_timestamp_us(0)
          .build());

  // Set color.
  int plane_offset[kNumOfPlanes];
  plane_offset[kYPlane] = kColorY;
  plane_offset[kUPlane] = kColorU;
  plane_offset[kVPlane] = kColorV;
  CreateImage(input_buffer_, plane_offset);

  EXPECT_EQ(0, encoder_->Encode(*input_frame_, NULL));

  // Change color.
  plane_offset[kYPlane] += 1;
  plane_offset[kUPlane] += 1;
  plane_offset[kVPlane] += 1;
  CreateImage(input_buffer_, plane_offset);
  input_frame_->set_timestamp(input_frame_->timestamp() + 3000);
  EXPECT_EQ(0, encoder_->Encode(*input_frame_, NULL));

  EncodedImage encoded_frame;
  // Only encoding one frame - so will be a key frame.
  encoder_callback.GetLastEncodedKeyFrame(&encoded_frame);
  EXPECT_EQ(0, decoder_->Decode(encoded_frame, 0));
  encoder_callback.GetLastEncodedFrame(&encoded_frame);
  decoder_->Decode(encoded_frame, 0);
  EXPECT_EQ(2, decoder_callback.DecodedFrames());
}

void SimulcastTestFixtureImpl::TestDecodeWidthHeightSet() {
  MockEncodedImageCallback encoder_callback;
  MockDecodedImageCallback decoder_callback;

  EncodedImage encoded_frame[3];
  SetRates(kMaxBitrates[2], 30);  // To get all three streams.
  encoder_->RegisterEncodeCompleteCallback(&encoder_callback);
  decoder_->RegisterDecodeCompleteCallback(&decoder_callback);

  EXPECT_CALL(encoder_callback, OnEncodedImage(_, _))
      .Times(3)
      .WillRepeatedly(
          ::testing::Invoke([&](const EncodedImage& encoded_image,
                                const CodecSpecificInfo* codec_specific_info) {
            EXPECT_EQ(encoded_image._frameType, VideoFrameType::kVideoFrameKey);

            size_t index = encoded_image.SimulcastIndex().value_or(0);
            encoded_frame[index].SetEncodedData(EncodedImageBuffer::Create(
                encoded_image.data(), encoded_image.size()));
            encoded_frame[index]._frameType = encoded_image._frameType;
            return EncodedImageCallback::Result(
                EncodedImageCallback::Result::OK, 0);
          }));
  EXPECT_EQ(0, encoder_->Encode(*input_frame_, NULL));

  EXPECT_CALL(decoder_callback, Decoded(_, _, _))
      .WillOnce(::testing::Invoke([](VideoFrame& decodedImage,
                                     absl::optional<int32_t> decode_time_ms,
                                     absl::optional<uint8_t> qp) {
        EXPECT_EQ(decodedImage.width(), kDefaultWidth / 4);
        EXPECT_EQ(decodedImage.height(), kDefaultHeight / 4);
      }));
  EXPECT_EQ(0, decoder_->Decode(encoded_frame[0], 0));

  EXPECT_CALL(decoder_callback, Decoded(_, _, _))
      .WillOnce(::testing::Invoke([](VideoFrame& decodedImage,
                                     absl::optional<int32_t> decode_time_ms,
                                     absl::optional<uint8_t> qp) {
        EXPECT_EQ(decodedImage.width(), kDefaultWidth / 2);
        EXPECT_EQ(decodedImage.height(), kDefaultHeight / 2);
      }));
  EXPECT_EQ(0, decoder_->Decode(encoded_frame[1], 0));

  EXPECT_CALL(decoder_callback, Decoded(_, _, _))
      .WillOnce(::testing::Invoke([](VideoFrame& decodedImage,
                                     absl::optional<int32_t> decode_time_ms,
                                     absl::optional<uint8_t> qp) {
        EXPECT_EQ(decodedImage.width(), kDefaultWidth);
        EXPECT_EQ(decodedImage.height(), kDefaultHeight);
      }));
  EXPECT_EQ(0, decoder_->Decode(encoded_frame[2], 0));
}

void SimulcastTestFixtureImpl::
    TestEncoderInfoForDefaultTemporalLayerProfileHasFpsAllocation() {
  VideoEncoder::EncoderInfo encoder_info = encoder_->GetEncoderInfo();
  EXPECT_EQ(encoder_info.fps_allocation[0].size(),
            static_cast<size_t>(kDefaultTemporalLayerProfile[0]));
  EXPECT_EQ(encoder_info.fps_allocation[1].size(),
            static_cast<size_t>(kDefaultTemporalLayerProfile[1]));
  EXPECT_EQ(encoder_info.fps_allocation[2].size(),
            static_cast<size_t>(kDefaultTemporalLayerProfile[2]));
}
}  // namespace test
}  // namespace webrtc
