/*
 *  Copyright (c) 2017 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "modules/video_coding/codecs/test/video_codec_unittest.h"

#include <utility>

#include "api/test/create_frame_generator.h"
#include "api/video_codecs/video_encoder.h"
#include "modules/rtp_rtcp/include/rtp_rtcp_defines.h"
#include "modules/video_coding/include/video_error_codes.h"
#include "test/video_codec_settings.h"

static constexpr webrtc::TimeDelta kEncodeTimeout =
    webrtc::TimeDelta::Millis(100);
static constexpr webrtc::TimeDelta kDecodeTimeout =
    webrtc::TimeDelta::Millis(25);
// Set bitrate to get higher quality.
static const int kStartBitrate = 300;
static const int kMaxBitrate = 4000;
static const int kWidth = 176;        // Width of the input image.
static const int kHeight = 144;       // Height of the input image.
static const int kMaxFramerate = 30;  // Arbitrary value.

namespace webrtc {
namespace {
const VideoEncoder::Capabilities kCapabilities(false);
}

EncodedImageCallback::Result
VideoCodecUnitTest::FakeEncodeCompleteCallback::OnEncodedImage(
    const EncodedImage& frame,
    const CodecSpecificInfo* codec_specific_info) {
  MutexLock lock(&test_->encoded_frame_section_);
  test_->encoded_frames_.push_back(frame);
  RTC_DCHECK(codec_specific_info);
  test_->codec_specific_infos_.push_back(*codec_specific_info);
  if (!test_->wait_for_encoded_frames_threshold_) {
    test_->encoded_frame_event_.Set();
    return Result(Result::OK);
  }

  if (test_->encoded_frames_.size() ==
      test_->wait_for_encoded_frames_threshold_) {
    test_->wait_for_encoded_frames_threshold_ = 1;
    test_->encoded_frame_event_.Set();
  }
  return Result(Result::OK);
}

void VideoCodecUnitTest::FakeDecodeCompleteCallback::Decoded(
    VideoFrame& frame,
    absl::optional<int32_t> decode_time_ms,
    absl::optional<uint8_t> qp) {
  MutexLock lock(&test_->decoded_frame_section_);
  test_->decoded_frame_.emplace(frame);
  test_->decoded_qp_ = qp;
  test_->decoded_frame_event_.Set();
}

void VideoCodecUnitTest::SetUp() {
  webrtc::test::CodecSettings(kVideoCodecVP8, &codec_settings_);
  codec_settings_.startBitrate = kStartBitrate;
  codec_settings_.maxBitrate = kMaxBitrate;
  codec_settings_.maxFramerate = kMaxFramerate;
  codec_settings_.width = kWidth;
  codec_settings_.height = kHeight;

  ModifyCodecSettings(&codec_settings_);

  input_frame_generator_ = test::CreateSquareFrameGenerator(
      codec_settings_.width, codec_settings_.height,
      test::FrameGeneratorInterface::OutputType::kI420, absl::optional<int>());

  encoder_ = CreateEncoder();
  decoder_ = CreateDecoder();
  encoder_->RegisterEncodeCompleteCallback(&encode_complete_callback_);
  decoder_->RegisterDecodeCompleteCallback(&decode_complete_callback_);

  EXPECT_EQ(WEBRTC_VIDEO_CODEC_OK,
            encoder_->InitEncode(
                &codec_settings_,
                VideoEncoder::Settings(kCapabilities, 1 /* number of cores */,
                                       0 /* max payload size (unused) */)));

  VideoDecoder::Settings decoder_settings;
  decoder_settings.set_codec_type(codec_settings_.codecType);
  decoder_settings.set_max_render_resolution(
      {codec_settings_.width, codec_settings_.height});
  EXPECT_TRUE(decoder_->Configure(decoder_settings));
}

void VideoCodecUnitTest::ModifyCodecSettings(VideoCodec* codec_settings) {}

VideoFrame VideoCodecUnitTest::NextInputFrame() {
  test::FrameGeneratorInterface::VideoFrameData frame_data =
      input_frame_generator_->NextFrame();
  VideoFrame input_frame = VideoFrame::Builder()
                               .set_video_frame_buffer(frame_data.buffer)
                               .set_update_rect(frame_data.update_rect)
                               .build();

  const uint32_t timestamp =
      last_input_frame_timestamp_ +
      kVideoPayloadTypeFrequency / codec_settings_.maxFramerate;
  input_frame.set_timestamp(timestamp);
  input_frame.set_timestamp_us(timestamp * (1000 / 90));

  last_input_frame_timestamp_ = timestamp;
  return input_frame;
}

bool VideoCodecUnitTest::WaitForEncodedFrame(
    EncodedImage* frame,
    CodecSpecificInfo* codec_specific_info) {
  std::vector<EncodedImage> frames;
  std::vector<CodecSpecificInfo> codec_specific_infos;
  if (!WaitForEncodedFrames(&frames, &codec_specific_infos))
    return false;
  EXPECT_EQ(frames.size(), static_cast<size_t>(1));
  EXPECT_EQ(frames.size(), codec_specific_infos.size());
  *frame = frames[0];
  *codec_specific_info = codec_specific_infos[0];
  return true;
}

void VideoCodecUnitTest::SetWaitForEncodedFramesThreshold(size_t num_frames) {
  MutexLock lock(&encoded_frame_section_);
  wait_for_encoded_frames_threshold_ = num_frames;
}

bool VideoCodecUnitTest::WaitForEncodedFrames(
    std::vector<EncodedImage>* frames,
    std::vector<CodecSpecificInfo>* codec_specific_info) {
  EXPECT_TRUE(encoded_frame_event_.Wait(kEncodeTimeout))
      << "Timed out while waiting for encoded frame.";
  // This becomes unsafe if there are multiple threads waiting for frames.
  MutexLock lock(&encoded_frame_section_);
  EXPECT_FALSE(encoded_frames_.empty());
  EXPECT_FALSE(codec_specific_infos_.empty());
  EXPECT_EQ(encoded_frames_.size(), codec_specific_infos_.size());
  if (!encoded_frames_.empty()) {
    *frames = encoded_frames_;
    encoded_frames_.clear();
    RTC_DCHECK(!codec_specific_infos_.empty());
    *codec_specific_info = codec_specific_infos_;
    codec_specific_infos_.clear();
    return true;
  } else {
    return false;
  }
}

bool VideoCodecUnitTest::WaitForDecodedFrame(std::unique_ptr<VideoFrame>* frame,
                                             absl::optional<uint8_t>* qp) {
  bool ret = decoded_frame_event_.Wait(kDecodeTimeout);
  EXPECT_TRUE(ret) << "Timed out while waiting for a decoded frame.";
  // This becomes unsafe if there are multiple threads waiting for frames.
  MutexLock lock(&decoded_frame_section_);
  EXPECT_TRUE(decoded_frame_);
  if (decoded_frame_) {
    frame->reset(new VideoFrame(std::move(*decoded_frame_)));
    *qp = decoded_qp_;
    decoded_frame_.reset();
    return true;
  } else {
    return false;
  }
}

size_t VideoCodecUnitTest::GetNumEncodedFrames() {
  MutexLock lock(&encoded_frame_section_);
  return encoded_frames_.size();
}

}  // namespace webrtc
