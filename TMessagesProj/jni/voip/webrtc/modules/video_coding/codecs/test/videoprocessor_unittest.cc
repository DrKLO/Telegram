/*
 *  Copyright (c) 2012 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "modules/video_coding/codecs/test/videoprocessor.h"

#include <memory>

#include "api/scoped_refptr.h"
#include "api/test/mock_video_decoder.h"
#include "api/test/mock_video_encoder.h"
#include "api/test/videocodec_test_fixture.h"
#include "api/video/i420_buffer.h"
#include "media/base/media_constants.h"
#include "modules/video_coding/codecs/test/videocodec_test_stats_impl.h"
#include "rtc_base/task_queue_for_test.h"
#include "test/gmock.h"
#include "test/gtest.h"
#include "test/testsupport/mock/mock_frame_reader.h"

using ::testing::_;
using ::testing::AllOf;
using ::testing::Field;
using ::testing::Property;
using ::testing::ResultOf;
using ::testing::Return;

namespace webrtc {
namespace test {

namespace {

const int kWidth = 352;
const int kHeight = 288;

}  // namespace

class VideoProcessorTest : public ::testing::Test {
 protected:
  VideoProcessorTest() : q_("VP queue") {
    config_.SetCodecSettings(cricket::kVp8CodecName, 1, 1, 1, false, false,
                             false, kWidth, kHeight);

    decoder_mock_ = new MockVideoDecoder();
    decoders_.push_back(std::unique_ptr<VideoDecoder>(decoder_mock_));

    ExpectInit();
    q_.SendTask([this] {
      video_processor_ = std::make_unique<VideoProcessor>(
          &encoder_mock_, &decoders_, &frame_reader_mock_, config_, &stats_,
          &encoded_frame_writers_, /*decoded_frame_writers=*/nullptr);
    });
  }

  ~VideoProcessorTest() {
    q_.SendTask([this] { video_processor_.reset(); });
  }

  void ExpectInit() {
    EXPECT_CALL(encoder_mock_, InitEncode(_, _));
    EXPECT_CALL(encoder_mock_, RegisterEncodeCompleteCallback);
    EXPECT_CALL(*decoder_mock_, Configure);
    EXPECT_CALL(*decoder_mock_, RegisterDecodeCompleteCallback);
  }

  void ExpectRelease() {
    EXPECT_CALL(encoder_mock_, Release()).Times(1);
    EXPECT_CALL(encoder_mock_, RegisterEncodeCompleteCallback(_)).Times(1);
    EXPECT_CALL(*decoder_mock_, Release()).Times(1);
    EXPECT_CALL(*decoder_mock_, RegisterDecodeCompleteCallback(_)).Times(1);
  }

  TaskQueueForTest q_;

  VideoCodecTestFixture::Config config_;

  MockVideoEncoder encoder_mock_;
  MockVideoDecoder* decoder_mock_;
  std::vector<std::unique_ptr<VideoDecoder>> decoders_;
  MockFrameReader frame_reader_mock_;
  VideoCodecTestStatsImpl stats_;
  VideoProcessor::IvfFileWriterMap encoded_frame_writers_;
  std::unique_ptr<VideoProcessor> video_processor_;
};

TEST_F(VideoProcessorTest, InitRelease) {
  ExpectRelease();
}

TEST_F(VideoProcessorTest, ProcessFrames_FixedFramerate) {
  const int kBitrateKbps = 456;
  const int kFramerateFps = 31;
  EXPECT_CALL(
      encoder_mock_,
      SetRates(Field(&VideoEncoder::RateControlParameters::framerate_fps,
                     static_cast<double>(kFramerateFps))))
      .Times(1);
  q_.SendTask([=] { video_processor_->SetRates(kBitrateKbps, kFramerateFps); });

  EXPECT_CALL(frame_reader_mock_, PullFrame(_, _, _))
      .WillRepeatedly(Return(I420Buffer::Create(kWidth, kHeight)));
  EXPECT_CALL(
      encoder_mock_,
      Encode(Property(&VideoFrame::timestamp, 1 * 90000 / kFramerateFps), _))
      .Times(1);
  q_.SendTask([this] { video_processor_->ProcessFrame(); });

  EXPECT_CALL(
      encoder_mock_,
      Encode(Property(&VideoFrame::timestamp, 2 * 90000 / kFramerateFps), _))
      .Times(1);
  q_.SendTask([this] { video_processor_->ProcessFrame(); });

  ExpectRelease();
}

TEST_F(VideoProcessorTest, ProcessFrames_VariableFramerate) {
  const int kBitrateKbps = 456;
  const int kStartFramerateFps = 27;
  const int kStartTimestamp = 90000 / kStartFramerateFps;
  EXPECT_CALL(
      encoder_mock_,
      SetRates(Field(&VideoEncoder::RateControlParameters::framerate_fps,
                     static_cast<double>(kStartFramerateFps))))
      .Times(1);
  q_.SendTask(
      [=] { video_processor_->SetRates(kBitrateKbps, kStartFramerateFps); });

  EXPECT_CALL(frame_reader_mock_, PullFrame(_, _, _))
      .WillRepeatedly(Return(I420Buffer::Create(kWidth, kHeight)));
  EXPECT_CALL(encoder_mock_,
              Encode(Property(&VideoFrame::timestamp, kStartTimestamp), _))
      .Times(1);
  q_.SendTask([this] { video_processor_->ProcessFrame(); });

  const int kNewFramerateFps = 13;
  EXPECT_CALL(
      encoder_mock_,
      SetRates(Field(&VideoEncoder::RateControlParameters::framerate_fps,
                     static_cast<double>(kNewFramerateFps))))
      .Times(1);
  q_.SendTask(
      [=] { video_processor_->SetRates(kBitrateKbps, kNewFramerateFps); });

  EXPECT_CALL(encoder_mock_,
              Encode(Property(&VideoFrame::timestamp,
                              kStartTimestamp + 90000 / kNewFramerateFps),
                     _))
      .Times(1);
  q_.SendTask([this] { video_processor_->ProcessFrame(); });

  ExpectRelease();
}

TEST_F(VideoProcessorTest, SetRates) {
  const uint32_t kBitrateKbps = 123;
  const int kFramerateFps = 17;

  EXPECT_CALL(
      encoder_mock_,
      SetRates(AllOf(ResultOf(
                         [](const VideoEncoder::RateControlParameters& params) {
                           return params.bitrate.get_sum_kbps();
                         },
                         kBitrateKbps),
                     Field(&VideoEncoder::RateControlParameters::framerate_fps,
                           static_cast<double>(kFramerateFps)))))
      .Times(1);
  q_.SendTask([=] { video_processor_->SetRates(kBitrateKbps, kFramerateFps); });

  const uint32_t kNewBitrateKbps = 456;
  const int kNewFramerateFps = 34;
  EXPECT_CALL(
      encoder_mock_,
      SetRates(AllOf(ResultOf(
                         [](const VideoEncoder::RateControlParameters& params) {
                           return params.bitrate.get_sum_kbps();
                         },
                         kNewBitrateKbps),
                     Field(&VideoEncoder::RateControlParameters::framerate_fps,
                           static_cast<double>(kNewFramerateFps)))))
      .Times(1);
  q_.SendTask(
      [=] { video_processor_->SetRates(kNewBitrateKbps, kNewFramerateFps); });

  ExpectRelease();
}

}  // namespace test
}  // namespace webrtc
