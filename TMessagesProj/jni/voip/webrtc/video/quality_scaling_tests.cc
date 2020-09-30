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
#include "test/call_test.h"
#include "test/field_trial.h"
#include "test/frame_generator_capturer.h"

namespace webrtc {
namespace {
constexpr int kWidth = 1280;
constexpr int kHeight = 720;
constexpr int kLowStartBps = 100000;
constexpr int kHighStartBps = 600000;
constexpr size_t kTimeoutMs = 10000;  // Some tests are expected to time out.

void SetEncoderSpecific(VideoEncoderConfig* encoder_config,
                        VideoCodecType type,
                        bool automatic_resize,
                        bool frame_dropping) {
  if (type == kVideoCodecVP8) {
    VideoCodecVP8 vp8 = VideoEncoder::GetDefaultVp8Settings();
    vp8.automaticResizeOn = automatic_resize;
    vp8.frameDroppingOn = frame_dropping;
    encoder_config->encoder_specific_settings = new rtc::RefCountedObject<
        VideoEncoderConfig::Vp8EncoderSpecificSettings>(vp8);
  } else if (type == kVideoCodecVP9) {
    VideoCodecVP9 vp9 = VideoEncoder::GetDefaultVp9Settings();
    vp9.automaticResizeOn = automatic_resize;
    vp9.frameDroppingOn = frame_dropping;
    encoder_config->encoder_specific_settings = new rtc::RefCountedObject<
        VideoEncoderConfig::Vp9EncoderSpecificSettings>(vp9);
  } else if (type == kVideoCodecH264) {
    VideoCodecH264 h264 = VideoEncoder::GetDefaultH264Settings();
    h264.frameDroppingOn = frame_dropping;
    encoder_config->encoder_specific_settings = new rtc::RefCountedObject<
        VideoEncoderConfig::H264EncoderSpecificSettings>(h264);
  }
}
}  // namespace

class QualityScalingTest : public test::CallTest {
 protected:
  void RunTest(VideoEncoderFactory* encoder_factory,
               const std::string& payload_name,
               int start_bps,
               bool automatic_resize,
               bool frame_dropping,
               bool expect_adaptation);

  const std::string kPrefix = "WebRTC-Video-QualityScaling/Enabled-";
  const std::string kEnd = ",0,0,0.9995,0.9999,1/";
};

void QualityScalingTest::RunTest(VideoEncoderFactory* encoder_factory,
                                 const std::string& payload_name,
                                 int start_bps,
                                 bool automatic_resize,
                                 bool frame_dropping,
                                 bool expect_adaptation) {
  class ScalingObserver
      : public test::SendTest,
        public test::FrameGeneratorCapturer::SinkWantsObserver {
   public:
    ScalingObserver(VideoEncoderFactory* encoder_factory,
                    const std::string& payload_name,
                    int start_bps,
                    bool automatic_resize,
                    bool frame_dropping,
                    bool expect_adaptation)
        : SendTest(expect_adaptation ? kDefaultTimeoutMs : kTimeoutMs),
          encoder_factory_(encoder_factory),
          payload_name_(payload_name),
          start_bps_(start_bps),
          automatic_resize_(automatic_resize),
          frame_dropping_(frame_dropping),
          expect_adaptation_(expect_adaptation) {}

   private:
    void OnFrameGeneratorCapturerCreated(
        test::FrameGeneratorCapturer* frame_generator_capturer) override {
      frame_generator_capturer->SetSinkWantsObserver(this);
      // Set initial resolution.
      frame_generator_capturer->ChangeResolution(kWidth, kHeight);
    }

    // Called when FrameGeneratorCapturer::AddOrUpdateSink is called.
    void OnSinkWantsChanged(rtc::VideoSinkInterface<VideoFrame>* sink,
                            const rtc::VideoSinkWants& wants) override {
      if (wants.max_pixel_count < kWidth * kHeight)
        observation_complete_.Set();
    }
    void ModifySenderBitrateConfig(
        BitrateConstraints* bitrate_config) override {
      bitrate_config->start_bitrate_bps = start_bps_;
    }

    void ModifyVideoConfigs(
        VideoSendStream::Config* send_config,
        std::vector<VideoReceiveStream::Config>* receive_configs,
        VideoEncoderConfig* encoder_config) override {
      send_config->encoder_settings.encoder_factory = encoder_factory_;
      send_config->rtp.payload_name = payload_name_;
      send_config->rtp.payload_type = kVideoSendPayloadType;
      const VideoCodecType codec_type = PayloadStringToCodecType(payload_name_);
      encoder_config->codec_type = codec_type;
      encoder_config->max_bitrate_bps = start_bps_;
      SetEncoderSpecific(encoder_config, codec_type, automatic_resize_,
                         frame_dropping_);
    }

    void PerformTest() override {
      EXPECT_EQ(expect_adaptation_, Wait())
          << "Timed out while waiting for a scale down.";
    }

    VideoEncoderFactory* const encoder_factory_;
    const std::string payload_name_;
    const int start_bps_;
    const bool automatic_resize_;
    const bool frame_dropping_;
    const bool expect_adaptation_;
  } test(encoder_factory, payload_name, start_bps, automatic_resize,
         frame_dropping, expect_adaptation);

  RunBaseTest(&test);
}

TEST_F(QualityScalingTest, AdaptsDownForHighQp_Vp8) {
  // VP8 QP thresholds, low:1, high:1 -> high QP.
  test::ScopedFieldTrials field_trials(kPrefix + "1,1,0,0,0,0" + kEnd);

  // QualityScaler enabled.
  const bool kAutomaticResize = true;
  const bool kFrameDropping = true;
  const bool kExpectAdapt = true;

  test::FunctionVideoEncoderFactory encoder_factory(
      []() { return VP8Encoder::Create(); });
  RunTest(&encoder_factory, "VP8", kHighStartBps, kAutomaticResize,
          kFrameDropping, kExpectAdapt);
}

TEST_F(QualityScalingTest, NoAdaptDownForHighQpWithResizeOff_Vp8) {
  // VP8 QP thresholds, low:1, high:1 -> high QP.
  test::ScopedFieldTrials field_trials(kPrefix + "1,1,0,0,0,0" + kEnd);

  // QualityScaler disabled.
  const bool kAutomaticResize = false;
  const bool kFrameDropping = true;
  const bool kExpectAdapt = false;

  test::FunctionVideoEncoderFactory encoder_factory(
      []() { return VP8Encoder::Create(); });
  RunTest(&encoder_factory, "VP8", kHighStartBps, kAutomaticResize,
          kFrameDropping, kExpectAdapt);
}

// TODO(bugs.webrtc.org/10388): Fix and re-enable.
TEST_F(QualityScalingTest,
       DISABLED_NoAdaptDownForHighQpWithFrameDroppingOff_Vp8) {
  // VP8 QP thresholds, low:1, high:1 -> high QP.
  test::ScopedFieldTrials field_trials(kPrefix + "1,1,0,0,0,0" + kEnd);

  // QualityScaler disabled.
  const bool kAutomaticResize = true;
  const bool kFrameDropping = false;
  const bool kExpectAdapt = false;

  test::FunctionVideoEncoderFactory encoder_factory(
      []() { return VP8Encoder::Create(); });
  RunTest(&encoder_factory, "VP8", kHighStartBps, kAutomaticResize,
          kFrameDropping, kExpectAdapt);
}

TEST_F(QualityScalingTest, NoAdaptDownForNormalQp_Vp8) {
  // VP8 QP thresholds, low:1, high:127 -> normal QP.
  test::ScopedFieldTrials field_trials(kPrefix + "1,127,0,0,0,0" + kEnd);

  // QualityScaler enabled.
  const bool kAutomaticResize = true;
  const bool kFrameDropping = true;
  const bool kExpectAdapt = false;

  test::FunctionVideoEncoderFactory encoder_factory(
      []() { return VP8Encoder::Create(); });
  RunTest(&encoder_factory, "VP8", kHighStartBps, kAutomaticResize,
          kFrameDropping, kExpectAdapt);
}

TEST_F(QualityScalingTest, AdaptsDownForLowStartBitrate) {
  // VP8 QP thresholds, low:1, high:127 -> normal QP.
  test::ScopedFieldTrials field_trials(kPrefix + "1,127,0,0,0,0" + kEnd);

  // QualityScaler enabled.
  const bool kAutomaticResize = true;
  const bool kFrameDropping = true;
  const bool kExpectAdapt = true;

  test::FunctionVideoEncoderFactory encoder_factory(
      []() { return VP8Encoder::Create(); });
  RunTest(&encoder_factory, "VP8", kLowStartBps, kAutomaticResize,
          kFrameDropping, kExpectAdapt);
}

TEST_F(QualityScalingTest, NoAdaptDownForLowStartBitrateWithScalingOff) {
  // VP8 QP thresholds, low:1, high:127 -> normal QP.
  test::ScopedFieldTrials field_trials(kPrefix + "1,127,0,0,0,0" + kEnd);

  // QualityScaler disabled.
  const bool kAutomaticResize = false;
  const bool kFrameDropping = true;
  const bool kExpectAdapt = false;

  test::FunctionVideoEncoderFactory encoder_factory(
      []() { return VP8Encoder::Create(); });
  RunTest(&encoder_factory, "VP8", kLowStartBps, kAutomaticResize,
          kFrameDropping, kExpectAdapt);
}

TEST_F(QualityScalingTest, NoAdaptDownForHighQp_Vp9) {
  // VP9 QP thresholds, low:1, high:1 -> high QP.
  test::ScopedFieldTrials field_trials(kPrefix + "0,0,1,1,0,0" + kEnd +
                                       "WebRTC-VP9QualityScaler/Disabled/");

  // QualityScaler always disabled.
  const bool kAutomaticResize = true;
  const bool kFrameDropping = true;
  const bool kExpectAdapt = false;

  test::FunctionVideoEncoderFactory encoder_factory(
      []() { return VP9Encoder::Create(); });
  RunTest(&encoder_factory, "VP9", kHighStartBps, kAutomaticResize,
          kFrameDropping, kExpectAdapt);
}

#if defined(WEBRTC_USE_H264)
TEST_F(QualityScalingTest, AdaptsDownForHighQp_H264) {
  // H264 QP thresholds, low:1, high:1 -> high QP.
  test::ScopedFieldTrials field_trials(kPrefix + "0,0,0,0,1,1" + kEnd);

  // QualityScaler always enabled.
  const bool kAutomaticResize = false;
  const bool kFrameDropping = false;
  const bool kExpectAdapt = true;

  test::FunctionVideoEncoderFactory encoder_factory(
      []() { return H264Encoder::Create(cricket::VideoCodec("H264")); });
  RunTest(&encoder_factory, "H264", kHighStartBps, kAutomaticResize,
          kFrameDropping, kExpectAdapt);
}
#endif  // defined(WEBRTC_USE_H264)

}  // namespace webrtc
