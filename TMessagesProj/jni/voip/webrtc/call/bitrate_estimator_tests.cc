/*
 *  Copyright (c) 2013 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */
#include <functional>
#include <list>
#include <memory>
#include <string>

#include "api/test/create_frame_generator.h"
#include "call/call.h"
#include "call/fake_network_pipe.h"
#include "call/simulated_network.h"
#include "rtc_base/checks.h"
#include "rtc_base/event.h"
#include "rtc_base/logging.h"
#include "rtc_base/synchronization/mutex.h"
#include "rtc_base/task_queue_for_test.h"
#include "rtc_base/thread_annotations.h"
#include "test/call_test.h"
#include "test/direct_transport.h"
#include "test/encoder_settings.h"
#include "test/fake_decoder.h"
#include "test/fake_encoder.h"
#include "test/frame_generator_capturer.h"
#include "test/gtest.h"

namespace webrtc {
namespace {
// Note: If you consider to re-use this class, think twice and instead consider
// writing tests that don't depend on the logging system.
class LogObserver {
 public:
  LogObserver() { rtc::LogMessage::AddLogToStream(&callback_, rtc::LS_INFO); }

  ~LogObserver() { rtc::LogMessage::RemoveLogToStream(&callback_); }

  void PushExpectedLogLine(const std::string& expected_log_line) {
    callback_.PushExpectedLogLine(expected_log_line);
  }

  bool Wait() { return callback_.Wait(); }

 private:
  class Callback : public rtc::LogSink {
   public:
    void OnLogMessage(const std::string& message) override {
      MutexLock lock(&mutex_);
      // Ignore log lines that are due to missing AST extensions, these are
      // logged when we switch back from AST to TOF until the wrapping bitrate
      // estimator gives up on using AST.
      if (message.find("BitrateEstimator") != std::string::npos &&
          message.find("packet is missing") == std::string::npos) {
        received_log_lines_.push_back(message);
      }

      int num_popped = 0;
      while (!received_log_lines_.empty() && !expected_log_lines_.empty()) {
        std::string a = received_log_lines_.front();
        std::string b = expected_log_lines_.front();
        received_log_lines_.pop_front();
        expected_log_lines_.pop_front();
        num_popped++;
        EXPECT_TRUE(a.find(b) != std::string::npos) << a << " != " << b;
      }
      if (expected_log_lines_.empty()) {
        if (num_popped > 0) {
          done_.Set();
        }
        return;
      }
    }

    bool Wait() { return done_.Wait(test::CallTest::kDefaultTimeoutMs); }

    void PushExpectedLogLine(const std::string& expected_log_line) {
      MutexLock lock(&mutex_);
      expected_log_lines_.push_back(expected_log_line);
    }

   private:
    typedef std::list<std::string> Strings;
    Mutex mutex_;
    Strings received_log_lines_ RTC_GUARDED_BY(mutex_);
    Strings expected_log_lines_ RTC_GUARDED_BY(mutex_);
    rtc::Event done_;
  };

  Callback callback_;
};
}  // namespace

static const int kTOFExtensionId = 4;
static const int kASTExtensionId = 5;

class BitrateEstimatorTest : public test::CallTest {
 public:
  BitrateEstimatorTest() : receive_config_(nullptr) {}

  virtual ~BitrateEstimatorTest() { EXPECT_TRUE(streams_.empty()); }

  virtual void SetUp() {
    SendTask(RTC_FROM_HERE, task_queue(), [this]() {
      CreateCalls();

      send_transport_.reset(new test::DirectTransport(
          task_queue(),
          std::make_unique<FakeNetworkPipe>(
              Clock::GetRealTimeClock(), std::make_unique<SimulatedNetwork>(
                                             BuiltInNetworkBehaviorConfig())),
          sender_call_.get(), payload_type_map_));
      send_transport_->SetReceiver(receiver_call_->Receiver());
      receive_transport_.reset(new test::DirectTransport(
          task_queue(),
          std::make_unique<FakeNetworkPipe>(
              Clock::GetRealTimeClock(), std::make_unique<SimulatedNetwork>(
                                             BuiltInNetworkBehaviorConfig())),
          receiver_call_.get(), payload_type_map_));
      receive_transport_->SetReceiver(sender_call_->Receiver());

      VideoSendStream::Config video_send_config(send_transport_.get());
      video_send_config.rtp.ssrcs.push_back(kVideoSendSsrcs[0]);
      video_send_config.encoder_settings.encoder_factory =
          &fake_encoder_factory_;
      video_send_config.encoder_settings.bitrate_allocator_factory =
          bitrate_allocator_factory_.get();
      video_send_config.rtp.payload_name = "FAKE";
      video_send_config.rtp.payload_type = kFakeVideoSendPayloadType;
      SetVideoSendConfig(video_send_config);
      VideoEncoderConfig video_encoder_config;
      test::FillEncoderConfiguration(kVideoCodecVP8, 1, &video_encoder_config);
      SetVideoEncoderConfig(video_encoder_config);

      receive_config_ = VideoReceiveStream::Config(receive_transport_.get());
      // receive_config_.decoders will be set by every stream separately.
      receive_config_.rtp.remote_ssrc = GetVideoSendConfig()->rtp.ssrcs[0];
      receive_config_.rtp.local_ssrc = kReceiverLocalVideoSsrc;
      receive_config_.rtp.extensions.push_back(
          RtpExtension(RtpExtension::kTimestampOffsetUri, kTOFExtensionId));
      receive_config_.rtp.extensions.push_back(
          RtpExtension(RtpExtension::kAbsSendTimeUri, kASTExtensionId));
    });
  }

  virtual void TearDown() {
    SendTask(RTC_FROM_HERE, task_queue(), [this]() {
      for (auto* stream : streams_) {
        stream->StopSending();
        delete stream;
      }
      streams_.clear();

      send_transport_.reset();
      receive_transport_.reset();

      DestroyCalls();
    });
  }

 protected:
  friend class Stream;

  class Stream {
   public:
    explicit Stream(BitrateEstimatorTest* test)
        : test_(test),
          is_sending_receiving_(false),
          send_stream_(nullptr),
          frame_generator_capturer_(),
          decoder_factory_(
              []() { return std::make_unique<test::FakeDecoder>(); }) {
      test_->GetVideoSendConfig()->rtp.ssrcs[0]++;
      send_stream_ = test_->sender_call_->CreateVideoSendStream(
          test_->GetVideoSendConfig()->Copy(),
          test_->GetVideoEncoderConfig()->Copy());
      RTC_DCHECK_EQ(1, test_->GetVideoEncoderConfig()->number_of_streams);
      frame_generator_capturer_ =
          std::make_unique<test::FrameGeneratorCapturer>(
              test->clock_,
              test::CreateSquareFrameGenerator(kDefaultWidth, kDefaultHeight,
                                               absl::nullopt, absl::nullopt),
              kDefaultFramerate, *test->task_queue_factory_);
      frame_generator_capturer_->Init();
      send_stream_->SetSource(frame_generator_capturer_.get(),
                              DegradationPreference::MAINTAIN_FRAMERATE);
      send_stream_->Start();

      VideoReceiveStream::Decoder decoder;
      test_->receive_config_.decoder_factory = &decoder_factory_;
      decoder.payload_type = test_->GetVideoSendConfig()->rtp.payload_type;
      decoder.video_format =
          SdpVideoFormat(test_->GetVideoSendConfig()->rtp.payload_name);
      test_->receive_config_.decoders.clear();
      test_->receive_config_.decoders.push_back(decoder);
      test_->receive_config_.rtp.remote_ssrc =
          test_->GetVideoSendConfig()->rtp.ssrcs[0];
      test_->receive_config_.rtp.local_ssrc++;
      test_->receive_config_.renderer = &test->fake_renderer_;
      video_receive_stream_ = test_->receiver_call_->CreateVideoReceiveStream(
          test_->receive_config_.Copy());
      video_receive_stream_->Start();
      is_sending_receiving_ = true;
    }

    ~Stream() {
      EXPECT_FALSE(is_sending_receiving_);
      test_->sender_call_->DestroyVideoSendStream(send_stream_);
      frame_generator_capturer_.reset(nullptr);
      send_stream_ = nullptr;
      if (video_receive_stream_) {
        test_->receiver_call_->DestroyVideoReceiveStream(video_receive_stream_);
        video_receive_stream_ = nullptr;
      }
    }

    void StopSending() {
      if (is_sending_receiving_) {
        send_stream_->Stop();
        if (video_receive_stream_) {
          video_receive_stream_->Stop();
        }
        is_sending_receiving_ = false;
      }
    }

   private:
    BitrateEstimatorTest* test_;
    bool is_sending_receiving_;
    VideoSendStream* send_stream_;
    VideoReceiveStream* video_receive_stream_;
    std::unique_ptr<test::FrameGeneratorCapturer> frame_generator_capturer_;

    test::FunctionVideoDecoderFactory decoder_factory_;
  };

  LogObserver receiver_log_;
  std::unique_ptr<test::DirectTransport> send_transport_;
  std::unique_ptr<test::DirectTransport> receive_transport_;
  VideoReceiveStream::Config receive_config_;
  std::vector<Stream*> streams_;
};

static const char* kAbsSendTimeLog =
    "RemoteBitrateEstimatorAbsSendTime: Instantiating.";
static const char* kSingleStreamLog =
    "RemoteBitrateEstimatorSingleStream: Instantiating.";

TEST_F(BitrateEstimatorTest, InstantiatesTOFPerDefaultForVideo) {
  SendTask(RTC_FROM_HERE, task_queue(), [this]() {
    GetVideoSendConfig()->rtp.extensions.push_back(
        RtpExtension(RtpExtension::kTimestampOffsetUri, kTOFExtensionId));
    receiver_log_.PushExpectedLogLine(kSingleStreamLog);
    receiver_log_.PushExpectedLogLine(kSingleStreamLog);
    streams_.push_back(new Stream(this));
  });
  EXPECT_TRUE(receiver_log_.Wait());
}

TEST_F(BitrateEstimatorTest, ImmediatelySwitchToASTForVideo) {
  SendTask(RTC_FROM_HERE, task_queue(), [this]() {
    GetVideoSendConfig()->rtp.extensions.push_back(
        RtpExtension(RtpExtension::kAbsSendTimeUri, kASTExtensionId));
    receiver_log_.PushExpectedLogLine(kSingleStreamLog);
    receiver_log_.PushExpectedLogLine(kSingleStreamLog);
    receiver_log_.PushExpectedLogLine("Switching to absolute send time RBE.");
    receiver_log_.PushExpectedLogLine(kAbsSendTimeLog);
    streams_.push_back(new Stream(this));
  });
  EXPECT_TRUE(receiver_log_.Wait());
}

TEST_F(BitrateEstimatorTest, SwitchesToASTForVideo) {
  SendTask(RTC_FROM_HERE, task_queue(), [this]() {
    GetVideoSendConfig()->rtp.extensions.push_back(
        RtpExtension(RtpExtension::kTimestampOffsetUri, kTOFExtensionId));
    receiver_log_.PushExpectedLogLine(kSingleStreamLog);
    receiver_log_.PushExpectedLogLine(kSingleStreamLog);
    streams_.push_back(new Stream(this));
  });
  EXPECT_TRUE(receiver_log_.Wait());

  SendTask(RTC_FROM_HERE, task_queue(), [this]() {
    GetVideoSendConfig()->rtp.extensions[0] =
        RtpExtension(RtpExtension::kAbsSendTimeUri, kASTExtensionId);
    receiver_log_.PushExpectedLogLine("Switching to absolute send time RBE.");
    receiver_log_.PushExpectedLogLine(kAbsSendTimeLog);
    streams_.push_back(new Stream(this));
  });
  EXPECT_TRUE(receiver_log_.Wait());
}

// This test is flaky. See webrtc:5790.
TEST_F(BitrateEstimatorTest, DISABLED_SwitchesToASTThenBackToTOFForVideo) {
  SendTask(RTC_FROM_HERE, task_queue(), [this]() {
    GetVideoSendConfig()->rtp.extensions.push_back(
        RtpExtension(RtpExtension::kTimestampOffsetUri, kTOFExtensionId));
    receiver_log_.PushExpectedLogLine(kSingleStreamLog);
    receiver_log_.PushExpectedLogLine(kAbsSendTimeLog);
    receiver_log_.PushExpectedLogLine(kSingleStreamLog);
    streams_.push_back(new Stream(this));
  });
  EXPECT_TRUE(receiver_log_.Wait());

  SendTask(RTC_FROM_HERE, task_queue(), [this]() {
    GetVideoSendConfig()->rtp.extensions[0] =
        RtpExtension(RtpExtension::kAbsSendTimeUri, kASTExtensionId);
    receiver_log_.PushExpectedLogLine(kAbsSendTimeLog);
    receiver_log_.PushExpectedLogLine("Switching to absolute send time RBE.");
    streams_.push_back(new Stream(this));
  });
  EXPECT_TRUE(receiver_log_.Wait());

  SendTask(RTC_FROM_HERE, task_queue(), [this]() {
    GetVideoSendConfig()->rtp.extensions[0] =
        RtpExtension(RtpExtension::kTimestampOffsetUri, kTOFExtensionId);
    receiver_log_.PushExpectedLogLine(kAbsSendTimeLog);
    receiver_log_.PushExpectedLogLine(
        "WrappingBitrateEstimator: Switching to transmission time offset RBE.");
    streams_.push_back(new Stream(this));
    streams_[0]->StopSending();
    streams_[1]->StopSending();
  });
  EXPECT_TRUE(receiver_log_.Wait());
}
}  // namespace webrtc
