/*
 *  Copyright (c) 2013 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */
#include <algorithm>  // max
#include <memory>
#include <vector>

#include "absl/algorithm/container.h"
#include "api/sequence_checker.h"
#include "api/task_queue/default_task_queue_factory.h"
#include "api/task_queue/task_queue_base.h"
#include "api/test/simulated_network.h"
#include "api/video/builtin_video_bitrate_allocator_factory.h"
#include "api/video/encoded_image.h"
#include "api/video/video_bitrate_allocation.h"
#include "api/video_codecs/video_encoder.h"
#include "call/call.h"
#include "call/fake_network_pipe.h"
#include "call/rtp_transport_controller_send.h"
#include "call/simulated_network.h"
#include "call/video_send_stream.h"
#include "modules/rtp_rtcp/include/rtp_header_extension_map.h"
#include "modules/rtp_rtcp/source/rtcp_sender.h"
#include "modules/rtp_rtcp/source/rtp_header_extensions.h"
#include "modules/rtp_rtcp/source/rtp_packet.h"
#include "modules/rtp_rtcp/source/rtp_rtcp_impl2.h"
#include "modules/rtp_rtcp/source/video_rtp_depacketizer_vp9.h"
#include "modules/video_coding/codecs/vp8/include/vp8.h"
#include "modules/video_coding/codecs/vp9/include/vp9.h"
#include "rtc_base/checks.h"
#include "rtc_base/event.h"
#include "rtc_base/experiments/alr_experiment.h"
#include "rtc_base/logging.h"
#include "rtc_base/platform_thread.h"
#include "rtc_base/rate_limiter.h"
#include "rtc_base/synchronization/mutex.h"
#include "rtc_base/task_queue_for_test.h"
#include "rtc_base/task_utils/to_queued_task.h"
#include "rtc_base/time_utils.h"
#include "rtc_base/unique_id_generator.h"
#include "system_wrappers/include/sleep.h"
#include "test/call_test.h"
#include "test/configurable_frame_size_encoder.h"
#include "test/fake_encoder.h"
#include "test/fake_texture_frame.h"
#include "test/field_trial.h"
#include "test/frame_forwarder.h"
#include "test/frame_generator_capturer.h"
#include "test/frame_utils.h"
#include "test/gmock.h"
#include "test/gtest.h"
#include "test/null_transport.h"
#include "test/rtcp_packet_parser.h"
#include "test/rtp_header_parser.h"
#include "test/testsupport/perf_test.h"
#include "test/video_encoder_proxy_factory.h"
#include "video/send_statistics_proxy.h"
#include "video/transport_adapter.h"
#include "video/video_send_stream.h"

namespace webrtc {
namespace test {
class VideoSendStreamPeer {
 public:
  explicit VideoSendStreamPeer(webrtc::VideoSendStream* base_class_stream)
      : internal_stream_(
            static_cast<internal::VideoSendStream*>(base_class_stream)) {}
  absl::optional<float> GetPacingFactorOverride() const {
    return internal_stream_->GetPacingFactorOverride();
  }

 private:
  internal::VideoSendStream const* const internal_stream_;
};
}  // namespace test

namespace {
enum : int {  // The first valid value is 1.
  kAbsSendTimeExtensionId = 1,
  kTimestampOffsetExtensionId,
  kTransportSequenceNumberExtensionId,
  kVideoContentTypeExtensionId,
  kVideoRotationExtensionId,
  kVideoTimingExtensionId,
};

constexpr int64_t kRtcpIntervalMs = 1000;

enum VideoFormat {
  kGeneric,
  kVP8,
};

VideoFrame CreateVideoFrame(int width, int height, int64_t timestamp_ms) {
  return webrtc::VideoFrame::Builder()
      .set_video_frame_buffer(I420Buffer::Create(width, height))
      .set_rotation(webrtc::kVideoRotation_0)
      .set_timestamp_ms(timestamp_ms)
      .build();
}
}  // namespace

class VideoSendStreamTest : public test::CallTest {
 public:
  VideoSendStreamTest() {
    RegisterRtpExtension(RtpExtension(RtpExtension::kTransportSequenceNumberUri,
                                      kTransportSequenceNumberExtensionId));
  }

 protected:
  void TestNackRetransmission(uint32_t retransmit_ssrc,
                              uint8_t retransmit_payload_type);
  void TestPacketFragmentationSize(VideoFormat format, bool with_fec);

  void TestVp9NonFlexMode(uint8_t num_temporal_layers,
                          uint8_t num_spatial_layers);

  void TestRequestSourceRotateVideo(bool support_orientation_ext);
};

TEST_F(VideoSendStreamTest, CanStartStartedStream) {
  SendTask(RTC_FROM_HERE, task_queue(), [this]() {
    CreateSenderCall();

    test::NullTransport transport;
    CreateSendConfig(1, 0, 0, &transport);
    CreateVideoStreams();
    GetVideoSendStream()->Start();
    GetVideoSendStream()->Start();
    DestroyStreams();
    DestroyCalls();
  });
}

TEST_F(VideoSendStreamTest, CanStopStoppedStream) {
  SendTask(RTC_FROM_HERE, task_queue(), [this]() {
    CreateSenderCall();

    test::NullTransport transport;
    CreateSendConfig(1, 0, 0, &transport);
    CreateVideoStreams();
    GetVideoSendStream()->Stop();
    GetVideoSendStream()->Stop();
    DestroyStreams();
    DestroyCalls();
  });
}

TEST_F(VideoSendStreamTest, SupportsCName) {
  static std::string kCName = "PjQatC14dGfbVwGPUOA9IH7RlsFDbWl4AhXEiDsBizo=";
  class CNameObserver : public test::SendTest {
   public:
    CNameObserver() : SendTest(kDefaultTimeoutMs) {}

   private:
    Action OnSendRtcp(const uint8_t* packet, size_t length) override {
      test::RtcpPacketParser parser;
      EXPECT_TRUE(parser.Parse(packet, length));
      if (parser.sdes()->num_packets() > 0) {
        EXPECT_EQ(1u, parser.sdes()->chunks().size());
        EXPECT_EQ(kCName, parser.sdes()->chunks()[0].cname);

        observation_complete_.Set();
      }

      return SEND_PACKET;
    }

    void ModifyVideoConfigs(
        VideoSendStream::Config* send_config,
        std::vector<VideoReceiveStream::Config>* receive_configs,
        VideoEncoderConfig* encoder_config) override {
      send_config->rtp.c_name = kCName;
    }

    void PerformTest() override {
      EXPECT_TRUE(Wait()) << "Timed out while waiting for RTCP with CNAME.";
    }
  } test;

  RunBaseTest(&test);
}

TEST_F(VideoSendStreamTest, SupportsAbsoluteSendTime) {
  class AbsoluteSendTimeObserver : public test::SendTest {
   public:
    AbsoluteSendTimeObserver() : SendTest(kDefaultTimeoutMs) {
      extensions_.Register<AbsoluteSendTime>(kAbsSendTimeExtensionId);
    }

    Action OnSendRtp(const uint8_t* packet, size_t length) override {
      RtpPacket rtp_packet(&extensions_);
      EXPECT_TRUE(rtp_packet.Parse(packet, length));

      uint32_t abs_send_time = 0;
      EXPECT_FALSE(rtp_packet.HasExtension<TransmissionOffset>());
      EXPECT_TRUE(rtp_packet.GetExtension<AbsoluteSendTime>(&abs_send_time));
      if (abs_send_time != 0) {
        // Wait for at least one packet with a non-zero send time. The send time
        // is a 16-bit value derived from the system clock, and it is valid
        // for a packet to have a zero send time. To tell that from an
        // unpopulated value we'll wait for a packet with non-zero send time.
        observation_complete_.Set();
      } else {
        RTC_LOG(LS_WARNING)
            << "Got a packet with zero absoluteSendTime, waiting"
               " for another packet...";
      }

      return SEND_PACKET;
    }

    void ModifyVideoConfigs(
        VideoSendStream::Config* send_config,
        std::vector<VideoReceiveStream::Config>* receive_configs,
        VideoEncoderConfig* encoder_config) override {
      send_config->rtp.extensions.clear();
      send_config->rtp.extensions.push_back(
          RtpExtension(RtpExtension::kAbsSendTimeUri, kAbsSendTimeExtensionId));
    }

    void PerformTest() override {
      EXPECT_TRUE(Wait()) << "Timed out while waiting for single RTP packet.";
    }

   private:
    RtpHeaderExtensionMap extensions_;
  } test;

  RunBaseTest(&test);
}

TEST_F(VideoSendStreamTest, SupportsTransmissionTimeOffset) {
  static const int kEncodeDelayMs = 5;
  class TransmissionTimeOffsetObserver : public test::SendTest {
   public:
    TransmissionTimeOffsetObserver()
        : SendTest(kDefaultTimeoutMs), encoder_factory_([]() {
            return std::make_unique<test::DelayedEncoder>(
                Clock::GetRealTimeClock(), kEncodeDelayMs);
          }) {
      extensions_.Register<TransmissionOffset>(kTimestampOffsetExtensionId);
    }

   private:
    Action OnSendRtp(const uint8_t* packet, size_t length) override {
      RtpPacket rtp_packet(&extensions_);
      EXPECT_TRUE(rtp_packet.Parse(packet, length));

      int32_t toffset = 0;
      EXPECT_TRUE(rtp_packet.GetExtension<TransmissionOffset>(&toffset));
      EXPECT_FALSE(rtp_packet.HasExtension<AbsoluteSendTime>());
      EXPECT_GT(toffset, 0);
      observation_complete_.Set();

      return SEND_PACKET;
    }

    void ModifyVideoConfigs(
        VideoSendStream::Config* send_config,
        std::vector<VideoReceiveStream::Config>* receive_configs,
        VideoEncoderConfig* encoder_config) override {
      send_config->encoder_settings.encoder_factory = &encoder_factory_;
      send_config->rtp.extensions.clear();
      send_config->rtp.extensions.push_back(RtpExtension(
          RtpExtension::kTimestampOffsetUri, kTimestampOffsetExtensionId));
    }

    void PerformTest() override {
      EXPECT_TRUE(Wait()) << "Timed out while waiting for a single RTP packet.";
    }

    test::FunctionVideoEncoderFactory encoder_factory_;
    RtpHeaderExtensionMap extensions_;
  } test;

  RunBaseTest(&test);
}

TEST_F(VideoSendStreamTest, SupportsTransportWideSequenceNumbers) {
  static const uint8_t kExtensionId = kTransportSequenceNumberExtensionId;
  class TransportWideSequenceNumberObserver : public test::SendTest {
   public:
    TransportWideSequenceNumberObserver()
        : SendTest(kDefaultTimeoutMs), encoder_factory_([]() {
            return std::make_unique<test::FakeEncoder>(
                Clock::GetRealTimeClock());
          }) {
      extensions_.Register<TransportSequenceNumber>(kExtensionId);
    }

   private:
    Action OnSendRtp(const uint8_t* packet, size_t length) override {
      RtpPacket rtp_packet(&extensions_);
      EXPECT_TRUE(rtp_packet.Parse(packet, length));

      EXPECT_TRUE(rtp_packet.HasExtension<TransportSequenceNumber>());
      EXPECT_FALSE(rtp_packet.HasExtension<TransmissionOffset>());
      EXPECT_FALSE(rtp_packet.HasExtension<AbsoluteSendTime>());

      observation_complete_.Set();

      return SEND_PACKET;
    }

    void ModifyVideoConfigs(
        VideoSendStream::Config* send_config,
        std::vector<VideoReceiveStream::Config>* receive_configs,
        VideoEncoderConfig* encoder_config) override {
      send_config->encoder_settings.encoder_factory = &encoder_factory_;
    }

    void PerformTest() override {
      EXPECT_TRUE(Wait()) << "Timed out while waiting for a single RTP packet.";
    }

    test::FunctionVideoEncoderFactory encoder_factory_;
    RtpHeaderExtensionMap extensions_;
  } test;

  RunBaseTest(&test);
}

TEST_F(VideoSendStreamTest, SupportsVideoRotation) {
  class VideoRotationObserver : public test::SendTest {
   public:
    VideoRotationObserver() : SendTest(kDefaultTimeoutMs) {
      extensions_.Register<VideoOrientation>(kVideoRotationExtensionId);
    }

    Action OnSendRtp(const uint8_t* packet, size_t length) override {
      RtpPacket rtp_packet(&extensions_);
      EXPECT_TRUE(rtp_packet.Parse(packet, length));
      // Only the last packet of the frame is required to have the extension.
      if (!rtp_packet.Marker())
        return SEND_PACKET;
      EXPECT_EQ(rtp_packet.GetExtension<VideoOrientation>(), kVideoRotation_90);
      observation_complete_.Set();
      return SEND_PACKET;
    }

    void ModifyVideoConfigs(
        VideoSendStream::Config* send_config,
        std::vector<VideoReceiveStream::Config>* receive_configs,
        VideoEncoderConfig* encoder_config) override {
      send_config->rtp.extensions.clear();
      send_config->rtp.extensions.push_back(RtpExtension(
          RtpExtension::kVideoRotationUri, kVideoRotationExtensionId));
    }

    void OnFrameGeneratorCapturerCreated(
        test::FrameGeneratorCapturer* frame_generator_capturer) override {
      frame_generator_capturer->SetFakeRotation(kVideoRotation_90);
    }

    void PerformTest() override {
      EXPECT_TRUE(Wait()) << "Timed out while waiting for single RTP packet.";
    }

   private:
    RtpHeaderExtensionMap extensions_;
  } test;

  RunBaseTest(&test);
}

TEST_F(VideoSendStreamTest, SupportsVideoContentType) {
  class VideoContentTypeObserver : public test::SendTest {
   public:
    VideoContentTypeObserver()
        : SendTest(kDefaultTimeoutMs), first_frame_sent_(false) {
      extensions_.Register<VideoContentTypeExtension>(
          kVideoContentTypeExtensionId);
    }

    Action OnSendRtp(const uint8_t* packet, size_t length) override {
      RtpPacket rtp_packet(&extensions_);
      EXPECT_TRUE(rtp_packet.Parse(packet, length));
      // Only the last packet of the key-frame must have extension.
      if (!rtp_packet.Marker() || first_frame_sent_)
        return SEND_PACKET;
      // First marker bit seen means that the first frame is sent.
      first_frame_sent_ = true;
      VideoContentType type;
      EXPECT_TRUE(rtp_packet.GetExtension<VideoContentTypeExtension>(&type));
      EXPECT_TRUE(videocontenttypehelpers::IsScreenshare(type));
      observation_complete_.Set();
      return SEND_PACKET;
    }

    void ModifyVideoConfigs(
        VideoSendStream::Config* send_config,
        std::vector<VideoReceiveStream::Config>* receive_configs,
        VideoEncoderConfig* encoder_config) override {
      send_config->rtp.extensions.clear();
      send_config->rtp.extensions.push_back(RtpExtension(
          RtpExtension::kVideoContentTypeUri, kVideoContentTypeExtensionId));
      encoder_config->content_type = VideoEncoderConfig::ContentType::kScreen;
    }

    void PerformTest() override {
      EXPECT_TRUE(Wait()) << "Timed out while waiting for single RTP packet.";
    }

   private:
    bool first_frame_sent_;
    RtpHeaderExtensionMap extensions_;
  } test;

  RunBaseTest(&test);
}

TEST_F(VideoSendStreamTest, SupportsVideoTimingFrames) {
  class VideoTimingObserver : public test::SendTest {
   public:
    VideoTimingObserver()
        : SendTest(kDefaultTimeoutMs), first_frame_sent_(false) {
      extensions_.Register<VideoTimingExtension>(kVideoTimingExtensionId);
    }

    Action OnSendRtp(const uint8_t* packet, size_t length) override {
      RtpPacket rtp_packet(&extensions_);
      EXPECT_TRUE(rtp_packet.Parse(packet, length));
      // Only the last packet of the frame must have extension.
      // Also don't check packets of the second frame if they happen to get
      // through before the test terminates.
      if (!rtp_packet.Marker() || first_frame_sent_)
        return SEND_PACKET;
      EXPECT_TRUE(rtp_packet.HasExtension<VideoTimingExtension>());
      observation_complete_.Set();
      first_frame_sent_ = true;
      return SEND_PACKET;
    }

    void ModifyVideoConfigs(
        VideoSendStream::Config* send_config,
        std::vector<VideoReceiveStream::Config>* receive_configs,
        VideoEncoderConfig* encoder_config) override {
      send_config->rtp.extensions.clear();
      send_config->rtp.extensions.push_back(
          RtpExtension(RtpExtension::kVideoTimingUri, kVideoTimingExtensionId));
    }

    void PerformTest() override {
      EXPECT_TRUE(Wait()) << "Timed out while waiting for timing frames.";
    }

   private:
    RtpHeaderExtensionMap extensions_;
    bool first_frame_sent_;
  } test;

  RunBaseTest(&test);
}

class FakeReceiveStatistics : public ReceiveStatisticsProvider {
 public:
  FakeReceiveStatistics(uint32_t send_ssrc,
                        uint32_t last_sequence_number,
                        uint32_t cumulative_lost,
                        uint8_t fraction_lost) {
    stat_.SetMediaSsrc(send_ssrc);
    stat_.SetExtHighestSeqNum(last_sequence_number);
    stat_.SetCumulativeLost(cumulative_lost);
    stat_.SetFractionLost(fraction_lost);
  }

  std::vector<rtcp::ReportBlock> RtcpReportBlocks(size_t max_blocks) override {
    EXPECT_GE(max_blocks, 1u);
    return {stat_};
  }

 private:
  rtcp::ReportBlock stat_;
};

class UlpfecObserver : public test::EndToEndTest {
 public:
  // Some of the test cases are expected to time out.
  // Use a shorter timeout window than the default one for those.
  static constexpr int kReducedTimeoutMs = 10000;

  UlpfecObserver(bool header_extensions_enabled,
                 bool use_nack,
                 bool expect_red,
                 bool expect_ulpfec,
                 const std::string& codec,
                 VideoEncoderFactory* encoder_factory)
      : EndToEndTest(expect_ulpfec ? VideoSendStreamTest::kDefaultTimeoutMs
                                   : kReducedTimeoutMs),
        encoder_factory_(encoder_factory),
        payload_name_(codec),
        use_nack_(use_nack),
        expect_red_(expect_red),
        expect_ulpfec_(expect_ulpfec),
        sent_media_(false),
        sent_ulpfec_(false),
        header_extensions_enabled_(header_extensions_enabled) {
    extensions_.Register<AbsoluteSendTime>(kAbsSendTimeExtensionId);
    extensions_.Register<TransportSequenceNumber>(
        kTransportSequenceNumberExtensionId);
  }

 private:
  Action OnSendRtp(const uint8_t* packet, size_t length) override {
    RtpPacket rtp_packet(&extensions_);
    EXPECT_TRUE(rtp_packet.Parse(packet, length));

    int encapsulated_payload_type = -1;
    if (rtp_packet.PayloadType() == VideoSendStreamTest::kRedPayloadType) {
      EXPECT_TRUE(expect_red_);
      encapsulated_payload_type = rtp_packet.payload()[0];
      if (encapsulated_payload_type !=
          VideoSendStreamTest::kFakeVideoSendPayloadType) {
        EXPECT_EQ(VideoSendStreamTest::kUlpfecPayloadType,
                  encapsulated_payload_type);
      }
    } else {
      EXPECT_EQ(VideoSendStreamTest::kFakeVideoSendPayloadType,
                rtp_packet.PayloadType());
      if (rtp_packet.payload_size() > 0) {
        // Not padding-only, media received outside of RED.
        EXPECT_FALSE(expect_red_);
        sent_media_ = true;
      }
    }

    if (header_extensions_enabled_) {
      uint32_t abs_send_time;
      EXPECT_TRUE(rtp_packet.GetExtension<AbsoluteSendTime>(&abs_send_time));
      uint16_t transport_seq_num;
      EXPECT_TRUE(
          rtp_packet.GetExtension<TransportSequenceNumber>(&transport_seq_num));
      if (!first_packet_) {
        uint32_t kHalf24BitsSpace = 0xFFFFFF / 2;
        if (abs_send_time <= kHalf24BitsSpace &&
            prev_abs_send_time_ > kHalf24BitsSpace) {
          // 24 bits wrap.
          EXPECT_GT(prev_abs_send_time_, abs_send_time);
        } else {
          EXPECT_GE(abs_send_time, prev_abs_send_time_);
        }

        uint16_t seq_num_diff = transport_seq_num - prev_transport_seq_num_;
        EXPECT_EQ(1, seq_num_diff);
      }
      first_packet_ = false;
      prev_abs_send_time_ = abs_send_time;
      prev_transport_seq_num_ = transport_seq_num;
    }

    if (encapsulated_payload_type != -1) {
      if (encapsulated_payload_type ==
          VideoSendStreamTest::kUlpfecPayloadType) {
        EXPECT_TRUE(expect_ulpfec_);
        sent_ulpfec_ = true;
      } else {
        sent_media_ = true;
      }
    }

    if (sent_media_ && sent_ulpfec_) {
      observation_complete_.Set();
    }

    return SEND_PACKET;
  }

  std::unique_ptr<test::PacketTransport> CreateSendTransport(
      TaskQueueBase* task_queue,
      Call* sender_call) override {
    // At low RTT (< kLowRttNackMs) -> NACK only, no FEC.
    // Configure some network delay.
    const int kNetworkDelayMs = 100;
    BuiltInNetworkBehaviorConfig config;
    config.loss_percent = 5;
    config.queue_delay_ms = kNetworkDelayMs;
    return std::make_unique<test::PacketTransport>(
        task_queue, sender_call, this, test::PacketTransport::kSender,
        VideoSendStreamTest::payload_type_map_,
        std::make_unique<FakeNetworkPipe>(
            Clock::GetRealTimeClock(),
            std::make_unique<SimulatedNetwork>(config)));
  }

  void ModifyVideoConfigs(
      VideoSendStream::Config* send_config,
      std::vector<VideoReceiveStream::Config>* receive_configs,
      VideoEncoderConfig* encoder_config) override {
    if (use_nack_) {
      send_config->rtp.nack.rtp_history_ms =
          (*receive_configs)[0].rtp.nack.rtp_history_ms =
              VideoSendStreamTest::kNackRtpHistoryMs;
    }
    send_config->encoder_settings.encoder_factory = encoder_factory_;
    send_config->rtp.payload_name = payload_name_;
    send_config->rtp.ulpfec.red_payload_type =
        VideoSendStreamTest::kRedPayloadType;
    send_config->rtp.ulpfec.ulpfec_payload_type =
        VideoSendStreamTest::kUlpfecPayloadType;
    if (!header_extensions_enabled_) {
      send_config->rtp.extensions.clear();
    } else {
      send_config->rtp.extensions.push_back(
          RtpExtension(RtpExtension::kAbsSendTimeUri, kAbsSendTimeExtensionId));
    }
    (*receive_configs)[0].rtp.extensions = send_config->rtp.extensions;
    encoder_config->codec_type = PayloadStringToCodecType(payload_name_);
    (*receive_configs)[0].rtp.red_payload_type =
        send_config->rtp.ulpfec.red_payload_type;
    (*receive_configs)[0].rtp.ulpfec_payload_type =
        send_config->rtp.ulpfec.ulpfec_payload_type;
  }

  void PerformTest() override {
    EXPECT_EQ(expect_ulpfec_, Wait())
        << "Timed out waiting for ULPFEC and/or media packets.";
  }

  VideoEncoderFactory* encoder_factory_;
  RtpHeaderExtensionMap extensions_;
  const std::string payload_name_;
  const bool use_nack_;
  const bool expect_red_;
  const bool expect_ulpfec_;
  bool sent_media_;
  bool sent_ulpfec_;
  const bool header_extensions_enabled_;
  bool first_packet_ = true;
  uint32_t prev_abs_send_time_ = 0;
  uint16_t prev_transport_seq_num_ = 0;
};

TEST_F(VideoSendStreamTest, SupportsUlpfecWithExtensions) {
  test::FunctionVideoEncoderFactory encoder_factory(
      []() { return VP8Encoder::Create(); });
  UlpfecObserver test(true, false, true, true, "VP8", &encoder_factory);
  RunBaseTest(&test);
}

TEST_F(VideoSendStreamTest, SupportsUlpfecWithoutExtensions) {
  test::FunctionVideoEncoderFactory encoder_factory(
      []() { return VP8Encoder::Create(); });
  UlpfecObserver test(false, false, true, true, "VP8", &encoder_factory);
  RunBaseTest(&test);
}

class VideoSendStreamWithoutUlpfecTest : public test::CallTest {
 protected:
  VideoSendStreamWithoutUlpfecTest()
      : field_trial_("WebRTC-DisableUlpFecExperiment/Enabled/") {}

  test::ScopedFieldTrials field_trial_;
};

TEST_F(VideoSendStreamWithoutUlpfecTest, NoUlpfecIfDisabledThroughFieldTrial) {
  test::FunctionVideoEncoderFactory encoder_factory(
      []() { return VP8Encoder::Create(); });
  UlpfecObserver test(false, false, false, false, "VP8", &encoder_factory);
  RunBaseTest(&test);
}

// The FEC scheme used is not efficient for H264, so we should not use RED/FEC
// since we'll still have to re-request FEC packets, effectively wasting
// bandwidth since the receiver has to wait for FEC retransmissions to determine
// that the received state is actually decodable.
TEST_F(VideoSendStreamTest, DoesNotUtilizeUlpfecForH264WithNackEnabled) {
  test::FunctionVideoEncoderFactory encoder_factory([]() {
    return std::make_unique<test::FakeH264Encoder>(Clock::GetRealTimeClock());
  });
  UlpfecObserver test(false, true, false, false, "H264", &encoder_factory);
  RunBaseTest(&test);
}

// Without retransmissions FEC for H264 is fine.
TEST_F(VideoSendStreamTest, DoesUtilizeUlpfecForH264WithoutNackEnabled) {
  test::FunctionVideoEncoderFactory encoder_factory([]() {
    return std::make_unique<test::FakeH264Encoder>(Clock::GetRealTimeClock());
  });
  UlpfecObserver test(false, false, true, true, "H264", &encoder_factory);
  RunBaseTest(&test);
}

TEST_F(VideoSendStreamTest, DoesUtilizeUlpfecForVp8WithNackEnabled) {
  test::FunctionVideoEncoderFactory encoder_factory(
      []() { return VP8Encoder::Create(); });
  UlpfecObserver test(false, true, true, true, "VP8", &encoder_factory);
  RunBaseTest(&test);
}

#if defined(RTC_ENABLE_VP9)
TEST_F(VideoSendStreamTest, DoesUtilizeUlpfecForVp9WithNackEnabled) {
  test::FunctionVideoEncoderFactory encoder_factory(
      []() { return VP9Encoder::Create(); });
  UlpfecObserver test(false, true, true, true, "VP9", &encoder_factory);
  RunBaseTest(&test);
}
#endif  // defined(RTC_ENABLE_VP9)

TEST_F(VideoSendStreamTest, SupportsUlpfecWithMultithreadedH264) {
  std::unique_ptr<TaskQueueFactory> task_queue_factory =
      CreateDefaultTaskQueueFactory();
  test::FunctionVideoEncoderFactory encoder_factory([&]() {
    return std::make_unique<test::MultithreadedFakeH264Encoder>(
        Clock::GetRealTimeClock(), task_queue_factory.get());
  });
  UlpfecObserver test(false, false, true, true, "H264", &encoder_factory);
  RunBaseTest(&test);
}

// TODO(brandtr): Move these FlexFEC tests when we have created
// FlexfecSendStream.
class FlexfecObserver : public test::EndToEndTest {
 public:
  FlexfecObserver(bool header_extensions_enabled,
                  bool use_nack,
                  const std::string& codec,
                  VideoEncoderFactory* encoder_factory,
                  size_t num_video_streams)
      : EndToEndTest(VideoSendStreamTest::kDefaultTimeoutMs),
        encoder_factory_(encoder_factory),
        payload_name_(codec),
        use_nack_(use_nack),
        sent_media_(false),
        sent_flexfec_(false),
        header_extensions_enabled_(header_extensions_enabled),
        num_video_streams_(num_video_streams) {
    extensions_.Register<AbsoluteSendTime>(kAbsSendTimeExtensionId);
    extensions_.Register<TransmissionOffset>(kTimestampOffsetExtensionId);
    extensions_.Register<TransportSequenceNumber>(
        kTransportSequenceNumberExtensionId);
  }

  size_t GetNumFlexfecStreams() const override { return 1; }
  size_t GetNumVideoStreams() const override { return num_video_streams_; }

 private:
  Action OnSendRtp(const uint8_t* packet, size_t length) override {
    RtpPacket rtp_packet(&extensions_);
    EXPECT_TRUE(rtp_packet.Parse(packet, length));

    if (rtp_packet.PayloadType() == VideoSendStreamTest::kFlexfecPayloadType) {
      EXPECT_EQ(VideoSendStreamTest::kFlexfecSendSsrc, rtp_packet.Ssrc());
      sent_flexfec_ = true;
    } else {
      EXPECT_EQ(VideoSendStreamTest::kFakeVideoSendPayloadType,
                rtp_packet.PayloadType());
      EXPECT_THAT(::testing::make_tuple(VideoSendStreamTest::kVideoSendSsrcs,
                                        num_video_streams_),
                  ::testing::Contains(rtp_packet.Ssrc()));
      sent_media_ = true;
    }

    if (header_extensions_enabled_) {
      EXPECT_TRUE(rtp_packet.HasExtension<AbsoluteSendTime>());
      EXPECT_TRUE(rtp_packet.HasExtension<TransmissionOffset>());
      EXPECT_TRUE(rtp_packet.HasExtension<TransportSequenceNumber>());
    }

    if (sent_media_ && sent_flexfec_) {
      observation_complete_.Set();
    }

    return SEND_PACKET;
  }

  std::unique_ptr<test::PacketTransport> CreateSendTransport(
      TaskQueueBase* task_queue,
      Call* sender_call) override {
    // At low RTT (< kLowRttNackMs) -> NACK only, no FEC.
    // Therefore we need some network delay.
    const int kNetworkDelayMs = 100;
    BuiltInNetworkBehaviorConfig config;
    config.loss_percent = 5;
    config.queue_delay_ms = kNetworkDelayMs;
    return std::make_unique<test::PacketTransport>(
        task_queue, sender_call, this, test::PacketTransport::kSender,
        VideoSendStreamTest::payload_type_map_,
        std::make_unique<FakeNetworkPipe>(
            Clock::GetRealTimeClock(),
            std::make_unique<SimulatedNetwork>(config)));
  }

  std::unique_ptr<test::PacketTransport> CreateReceiveTransport(
      TaskQueueBase* task_queue) override {
    // We need the RTT to be >200 ms to send FEC and the network delay for the
    // send transport is 100 ms, so add 100 ms (but no loss) on the return link.
    BuiltInNetworkBehaviorConfig config;
    config.loss_percent = 0;
    config.queue_delay_ms = 100;
    return std::make_unique<test::PacketTransport>(
        task_queue, nullptr, this, test::PacketTransport::kReceiver,
        VideoSendStreamTest::payload_type_map_,
        std::make_unique<FakeNetworkPipe>(
            Clock::GetRealTimeClock(),
            std::make_unique<SimulatedNetwork>(config)));
  }

  void ModifyVideoConfigs(
      VideoSendStream::Config* send_config,
      std::vector<VideoReceiveStream::Config>* receive_configs,
      VideoEncoderConfig* encoder_config) override {
    if (use_nack_) {
      send_config->rtp.nack.rtp_history_ms =
          (*receive_configs)[0].rtp.nack.rtp_history_ms =
              VideoSendStreamTest::kNackRtpHistoryMs;
    }
    send_config->encoder_settings.encoder_factory = encoder_factory_;
    send_config->rtp.payload_name = payload_name_;
    if (header_extensions_enabled_) {
      send_config->rtp.extensions.push_back(
          RtpExtension(RtpExtension::kAbsSendTimeUri, kAbsSendTimeExtensionId));
      send_config->rtp.extensions.push_back(RtpExtension(
          RtpExtension::kTimestampOffsetUri, kTimestampOffsetExtensionId));
    } else {
      send_config->rtp.extensions.clear();
    }
    (*receive_configs)[0].rtp.extensions = send_config->rtp.extensions;
    encoder_config->codec_type = PayloadStringToCodecType(payload_name_);
  }

  void PerformTest() override {
    EXPECT_TRUE(Wait())
        << "Timed out waiting for FlexFEC and/or media packets.";
  }

  VideoEncoderFactory* encoder_factory_;
  RtpHeaderExtensionMap extensions_;
  const std::string payload_name_;
  const bool use_nack_;
  bool sent_media_;
  bool sent_flexfec_;
  const bool header_extensions_enabled_;
  const size_t num_video_streams_;
};

TEST_F(VideoSendStreamTest, SupportsFlexfecVp8) {
  test::FunctionVideoEncoderFactory encoder_factory(
      []() { return VP8Encoder::Create(); });
  FlexfecObserver test(false, false, "VP8", &encoder_factory, 1);
  RunBaseTest(&test);
}

TEST_F(VideoSendStreamTest, SupportsFlexfecSimulcastVp8) {
  test::FunctionVideoEncoderFactory encoder_factory(
      []() { return VP8Encoder::Create(); });
  FlexfecObserver test(false, false, "VP8", &encoder_factory, 2);
  RunBaseTest(&test);
}

TEST_F(VideoSendStreamTest, SupportsFlexfecWithNackVp8) {
  test::FunctionVideoEncoderFactory encoder_factory(
      []() { return VP8Encoder::Create(); });
  FlexfecObserver test(false, true, "VP8", &encoder_factory, 1);
  RunBaseTest(&test);
}

TEST_F(VideoSendStreamTest, SupportsFlexfecWithRtpExtensionsVp8) {
  test::FunctionVideoEncoderFactory encoder_factory(
      []() { return VP8Encoder::Create(); });
  FlexfecObserver test(true, false, "VP8", &encoder_factory, 1);
  RunBaseTest(&test);
}

#if defined(RTC_ENABLE_VP9)
TEST_F(VideoSendStreamTest, SupportsFlexfecVp9) {
  test::FunctionVideoEncoderFactory encoder_factory(
      []() { return VP9Encoder::Create(); });
  FlexfecObserver test(false, false, "VP9", &encoder_factory, 1);
  RunBaseTest(&test);
}

TEST_F(VideoSendStreamTest, SupportsFlexfecWithNackVp9) {
  test::FunctionVideoEncoderFactory encoder_factory(
      []() { return VP9Encoder::Create(); });
  FlexfecObserver test(false, true, "VP9", &encoder_factory, 1);
  RunBaseTest(&test);
}
#endif  // defined(RTC_ENABLE_VP9)

TEST_F(VideoSendStreamTest, SupportsFlexfecH264) {
  test::FunctionVideoEncoderFactory encoder_factory([]() {
    return std::make_unique<test::FakeH264Encoder>(Clock::GetRealTimeClock());
  });
  FlexfecObserver test(false, false, "H264", &encoder_factory, 1);
  RunBaseTest(&test);
}

TEST_F(VideoSendStreamTest, SupportsFlexfecWithNackH264) {
  test::FunctionVideoEncoderFactory encoder_factory([]() {
    return std::make_unique<test::FakeH264Encoder>(Clock::GetRealTimeClock());
  });
  FlexfecObserver test(false, true, "H264", &encoder_factory, 1);
  RunBaseTest(&test);
}

TEST_F(VideoSendStreamTest, SupportsFlexfecWithMultithreadedH264) {
  std::unique_ptr<TaskQueueFactory> task_queue_factory =
      CreateDefaultTaskQueueFactory();
  test::FunctionVideoEncoderFactory encoder_factory([&]() {
    return std::make_unique<test::MultithreadedFakeH264Encoder>(
        Clock::GetRealTimeClock(), task_queue_factory.get());
  });

  FlexfecObserver test(false, false, "H264", &encoder_factory, 1);
  RunBaseTest(&test);
}

void VideoSendStreamTest::TestNackRetransmission(
    uint32_t retransmit_ssrc,
    uint8_t retransmit_payload_type) {
  class NackObserver : public test::SendTest {
   public:
    explicit NackObserver(uint32_t retransmit_ssrc,
                          uint8_t retransmit_payload_type)
        : SendTest(kDefaultTimeoutMs),
          send_count_(0),
          retransmit_count_(0),
          retransmit_ssrc_(retransmit_ssrc),
          retransmit_payload_type_(retransmit_payload_type) {}

   private:
    Action OnSendRtp(const uint8_t* packet, size_t length) override {
      RtpPacket rtp_packet;
      EXPECT_TRUE(rtp_packet.Parse(packet, length));

      // NACK packets two times at some arbitrary points.
      const int kNackedPacketsAtOnceCount = 3;
      const int kRetransmitTarget = kNackedPacketsAtOnceCount * 2;

      // Skip padding packets because they will never be retransmitted.
      if (rtp_packet.payload_size() == 0) {
        return SEND_PACKET;
      }

      ++send_count_;

      // NACK packets at arbitrary points.
      if (send_count_ == 5 || send_count_ == 25) {
        nacked_sequence_numbers_.insert(
            nacked_sequence_numbers_.end(),
            non_padding_sequence_numbers_.end() - kNackedPacketsAtOnceCount,
            non_padding_sequence_numbers_.end());

        RtpRtcpInterface::Configuration config;
        config.clock = Clock::GetRealTimeClock();
        config.outgoing_transport = transport_adapter_.get();
        config.rtcp_report_interval_ms = kRtcpIntervalMs;
        config.local_media_ssrc = kReceiverLocalVideoSsrc;
        RTCPSender rtcp_sender(config);

        rtcp_sender.SetRTCPStatus(RtcpMode::kReducedSize);
        rtcp_sender.SetRemoteSSRC(kVideoSendSsrcs[0]);

        RTCPSender::FeedbackState feedback_state;

        EXPECT_EQ(0, rtcp_sender.SendRTCP(
                         feedback_state, kRtcpNack,
                         static_cast<int>(nacked_sequence_numbers_.size()),
                         &nacked_sequence_numbers_.front()));
      }

      uint16_t sequence_number = rtp_packet.SequenceNumber();
      if (rtp_packet.Ssrc() == retransmit_ssrc_ &&
          retransmit_ssrc_ != kVideoSendSsrcs[0]) {
        // Not kVideoSendSsrcs[0], assume correct RTX packet. Extract sequence
        // number.
        const uint8_t* rtx_header = rtp_packet.payload().data();
        sequence_number = (rtx_header[0] << 8) + rtx_header[1];
      }

      auto found = absl::c_find(nacked_sequence_numbers_, sequence_number);
      if (found != nacked_sequence_numbers_.end()) {
        nacked_sequence_numbers_.erase(found);

        if (++retransmit_count_ == kRetransmitTarget) {
          EXPECT_EQ(retransmit_ssrc_, rtp_packet.Ssrc());
          EXPECT_EQ(retransmit_payload_type_, rtp_packet.PayloadType());
          observation_complete_.Set();
        }
      } else {
        non_padding_sequence_numbers_.push_back(sequence_number);
      }

      return SEND_PACKET;
    }

    void ModifyVideoConfigs(
        VideoSendStream::Config* send_config,
        std::vector<VideoReceiveStream::Config>* receive_configs,
        VideoEncoderConfig* encoder_config) override {
      transport_adapter_.reset(
          new internal::TransportAdapter(send_config->send_transport));
      transport_adapter_->Enable();
      send_config->rtp.nack.rtp_history_ms = kNackRtpHistoryMs;
      send_config->rtp.rtx.payload_type = retransmit_payload_type_;
      if (retransmit_ssrc_ != kVideoSendSsrcs[0])
        send_config->rtp.rtx.ssrcs.push_back(retransmit_ssrc_);
    }

    void PerformTest() override {
      EXPECT_TRUE(Wait()) << "Timed out while waiting for NACK retransmission.";
    }

    std::unique_ptr<internal::TransportAdapter> transport_adapter_;
    int send_count_;
    int retransmit_count_;
    const uint32_t retransmit_ssrc_;
    const uint8_t retransmit_payload_type_;
    std::vector<uint16_t> nacked_sequence_numbers_;
    std::vector<uint16_t> non_padding_sequence_numbers_;
  } test(retransmit_ssrc, retransmit_payload_type);

  RunBaseTest(&test);
}

TEST_F(VideoSendStreamTest, RetransmitsNack) {
  // Normal NACKs should use the send SSRC.
  TestNackRetransmission(kVideoSendSsrcs[0], kFakeVideoSendPayloadType);
}

TEST_F(VideoSendStreamTest, RetransmitsNackOverRtx) {
  // NACKs over RTX should use a separate SSRC.
  TestNackRetransmission(kSendRtxSsrcs[0], kSendRtxPayloadType);
}

void VideoSendStreamTest::TestPacketFragmentationSize(VideoFormat format,
                                                      bool with_fec) {
  // Use a fake encoder to output a frame of every size in the range [90, 290],
  // for each size making sure that the exact number of payload bytes received
  // is correct and that packets are fragmented to respect max packet size.
  static const size_t kMaxPacketSize = 128;
  static const size_t start = 90;
  static const size_t stop = 290;

  // Observer that verifies that the expected number of packets and bytes
  // arrive for each frame size, from start_size to stop_size.
  class FrameFragmentationTest : public test::SendTest {
   public:
    FrameFragmentationTest(size_t max_packet_size,
                           size_t start_size,
                           size_t stop_size,
                           bool test_generic_packetization,
                           bool use_fec)
        : SendTest(kLongTimeoutMs),
          encoder_(stop),
          encoder_factory_(&encoder_),
          max_packet_size_(max_packet_size),
          stop_size_(stop_size),
          test_generic_packetization_(test_generic_packetization),
          use_fec_(use_fec),
          packet_count_(0),
          packets_lost_(0),
          last_packet_count_(0),
          last_packets_lost_(0),
          accumulated_size_(0),
          accumulated_payload_(0),
          fec_packet_received_(false),
          current_size_rtp_(start_size),
          current_size_frame_(static_cast<int>(start_size)) {
      // Fragmentation required, this test doesn't make sense without it.
      encoder_.SetFrameSize(start_size);
      RTC_DCHECK_GT(stop_size, max_packet_size);
      if (!test_generic_packetization_)
        encoder_.SetCodecType(kVideoCodecVP8);
    }

   private:
    Action OnSendRtp(const uint8_t* packet, size_t size) override {
      size_t length = size;
      RtpPacket rtp_packet;
      EXPECT_TRUE(rtp_packet.Parse(packet, length));

      EXPECT_LE(length, max_packet_size_);

      if (use_fec_ && rtp_packet.payload_size() > 0) {
        uint8_t payload_type = rtp_packet.payload()[0];
        bool is_fec = rtp_packet.PayloadType() == kRedPayloadType &&
                      payload_type == kUlpfecPayloadType;
        if (is_fec) {
          fec_packet_received_ = true;
          return SEND_PACKET;
        }
      }

      accumulated_size_ += length;

      if (use_fec_)
        TriggerLossReport(rtp_packet);

      if (test_generic_packetization_) {
        size_t overhead = rtp_packet.headers_size() + rtp_packet.padding_size();
        // Only remove payload header and RED header if the packet actually
        // contains payload.
        if (length > overhead) {
          overhead += (1 /* Generic header */);
          if (use_fec_)
            overhead += 1;  // RED for FEC header.
        }
        EXPECT_GE(length, overhead);
        accumulated_payload_ += length - overhead;
      }

      // Marker bit set indicates last packet of a frame.
      if (rtp_packet.Marker()) {
        if (use_fec_ && accumulated_payload_ == current_size_rtp_ - 1) {
          // With FEC enabled, frame size is incremented asynchronously, so
          // "old" frames one byte too small may arrive. Accept, but don't
          // increase expected frame size.
          accumulated_size_ = 0;
          accumulated_payload_ = 0;
          return SEND_PACKET;
        }

        EXPECT_GE(accumulated_size_, current_size_rtp_);
        if (test_generic_packetization_) {
          EXPECT_EQ(current_size_rtp_, accumulated_payload_);
        }

        // Last packet of frame; reset counters.
        accumulated_size_ = 0;
        accumulated_payload_ = 0;
        if (current_size_rtp_ == stop_size_) {
          // Done! (Don't increase size again, might arrive more @ stop_size).
          observation_complete_.Set();
        } else {
          // Increase next expected frame size. If testing with FEC, make sure
          // a FEC packet has been received for this frame size before
          // proceeding, to make sure that redundancy packets don't exceed
          // size limit.
          if (!use_fec_) {
            ++current_size_rtp_;
          } else if (fec_packet_received_) {
            fec_packet_received_ = false;
            ++current_size_rtp_;

            MutexLock lock(&mutex_);
            ++current_size_frame_;
          }
        }
      }

      return SEND_PACKET;
    }

    void TriggerLossReport(const RtpPacket& rtp_packet) {
      // Send lossy receive reports to trigger FEC enabling.
      const int kLossPercent = 5;
      if (++packet_count_ % (100 / kLossPercent) == 0) {
        packets_lost_++;
        int loss_delta = packets_lost_ - last_packets_lost_;
        int packets_delta = packet_count_ - last_packet_count_;
        last_packet_count_ = packet_count_;
        last_packets_lost_ = packets_lost_;
        uint8_t loss_ratio =
            static_cast<uint8_t>(loss_delta * 255 / packets_delta);
        FakeReceiveStatistics lossy_receive_stats(
            kVideoSendSsrcs[0], rtp_packet.SequenceNumber(),
            packets_lost_,  // Cumulative lost.
            loss_ratio);    // Loss percent.
        RtpRtcpInterface::Configuration config;
        config.clock = Clock::GetRealTimeClock();
        config.receive_statistics = &lossy_receive_stats;
        config.outgoing_transport = transport_adapter_.get();
        config.rtcp_report_interval_ms = kRtcpIntervalMs;
        config.local_media_ssrc = kVideoSendSsrcs[0];
        RTCPSender rtcp_sender(config);

        rtcp_sender.SetRTCPStatus(RtcpMode::kReducedSize);
        rtcp_sender.SetRemoteSSRC(kVideoSendSsrcs[0]);

        RTCPSender::FeedbackState feedback_state;

        EXPECT_EQ(0, rtcp_sender.SendRTCP(feedback_state, kRtcpRr));
      }
    }

    void UpdateConfiguration() {
      MutexLock lock(&mutex_);
      // Increase frame size for next encoded frame, in the context of the
      // encoder thread.
      if (!use_fec_ && current_size_frame_ < static_cast<int32_t>(stop_size_)) {
        ++current_size_frame_;
      }
      encoder_.SetFrameSize(static_cast<size_t>(current_size_frame_));
    }
    void ModifySenderBitrateConfig(
        BitrateConstraints* bitrate_config) override {
      const int kMinBitrateBps = 300000;
      bitrate_config->min_bitrate_bps = kMinBitrateBps;
    }

    void ModifyVideoConfigs(
        VideoSendStream::Config* send_config,
        std::vector<VideoReceiveStream::Config>* receive_configs,
        VideoEncoderConfig* encoder_config) override {
      transport_adapter_.reset(
          new internal::TransportAdapter(send_config->send_transport));
      transport_adapter_->Enable();
      if (use_fec_) {
        send_config->rtp.ulpfec.red_payload_type = kRedPayloadType;
        send_config->rtp.ulpfec.ulpfec_payload_type = kUlpfecPayloadType;
      }

      if (!test_generic_packetization_)
        send_config->rtp.payload_name = "VP8";

      send_config->encoder_settings.encoder_factory = &encoder_factory_;
      send_config->rtp.max_packet_size = kMaxPacketSize;
      encoder_.RegisterPostEncodeCallback([this]() { UpdateConfiguration(); });

      // Make sure there is at least one extension header, to make the RTP
      // header larger than the base length of 12 bytes.
      EXPECT_FALSE(send_config->rtp.extensions.empty());

      // Setup screen content disables frame dropping which makes this easier.
      EXPECT_EQ(1u, encoder_config->simulcast_layers.size());
      encoder_config->simulcast_layers[0].num_temporal_layers = 2;
      encoder_config->content_type = VideoEncoderConfig::ContentType::kScreen;
    }

    void PerformTest() override {
      EXPECT_TRUE(Wait()) << "Timed out while observing incoming RTP packets.";
    }

    std::unique_ptr<internal::TransportAdapter> transport_adapter_;
    test::ConfigurableFrameSizeEncoder encoder_;
    test::VideoEncoderProxyFactory encoder_factory_;

    const size_t max_packet_size_;
    const size_t stop_size_;
    const bool test_generic_packetization_;
    const bool use_fec_;

    uint32_t packet_count_;
    uint32_t packets_lost_;
    uint32_t last_packet_count_;
    uint32_t last_packets_lost_;
    size_t accumulated_size_;
    size_t accumulated_payload_;
    bool fec_packet_received_;

    size_t current_size_rtp_;
    Mutex mutex_;
    int current_size_frame_ RTC_GUARDED_BY(mutex_);
  };

  // Don't auto increment if FEC is used; continue sending frame size until
  // a FEC packet has been received.
  FrameFragmentationTest test(kMaxPacketSize, start, stop, format == kGeneric,
                              with_fec);

  RunBaseTest(&test);
}

// TODO(sprang): Is there any way of speeding up these tests?
TEST_F(VideoSendStreamTest, FragmentsGenericAccordingToMaxPacketSize) {
  TestPacketFragmentationSize(kGeneric, false);
}

TEST_F(VideoSendStreamTest, FragmentsGenericAccordingToMaxPacketSizeWithFec) {
  TestPacketFragmentationSize(kGeneric, true);
}

TEST_F(VideoSendStreamTest, FragmentsVp8AccordingToMaxPacketSize) {
  TestPacketFragmentationSize(kVP8, false);
}

TEST_F(VideoSendStreamTest, FragmentsVp8AccordingToMaxPacketSizeWithFec) {
  TestPacketFragmentationSize(kVP8, true);
}

// This test that padding stops being send after a while if the Camera stops
// producing video frames and that padding resumes if the camera restarts.
TEST_F(VideoSendStreamTest, NoPaddingWhenVideoIsMuted) {
  class NoPaddingWhenVideoIsMuted : public test::SendTest {
   public:
    NoPaddingWhenVideoIsMuted()
        : SendTest(kDefaultTimeoutMs),
          clock_(Clock::GetRealTimeClock()),
          capturer_(nullptr) {}

   private:
    Action OnSendRtp(const uint8_t* packet, size_t length) override {
      MutexLock lock(&mutex_);
      last_packet_time_ms_ = clock_->TimeInMilliseconds();

      RtpPacket rtp_packet;
      rtp_packet.Parse(packet, length);
      const bool only_padding = rtp_packet.payload_size() == 0;

      if (test_state_ == kBeforeStopCapture) {
        // Packets are flowing, stop camera.
        capturer_->Stop();
        test_state_ = kWaitingForPadding;
      } else if (test_state_ == kWaitingForPadding && only_padding) {
        // We're still getting padding, after stopping camera.
        test_state_ = kWaitingForNoPackets;
      } else if (test_state_ == kWaitingForMediaAfterCameraRestart &&
                 !only_padding) {
        // Media packets are flowing again, stop camera a second time.
        capturer_->Stop();
        test_state_ = kWaitingForPaddingAfterCameraStopsAgain;
      } else if (test_state_ == kWaitingForPaddingAfterCameraStopsAgain &&
                 only_padding) {
        // Padding is still flowing, test ok.
        observation_complete_.Set();
      }
      return SEND_PACKET;
    }

    Action OnSendRtcp(const uint8_t* packet, size_t length) override {
      MutexLock lock(&mutex_);
      const int kNoPacketsThresholdMs = 2000;
      if (test_state_ == kWaitingForNoPackets &&
          (last_packet_time_ms_ &&
           clock_->TimeInMilliseconds() - last_packet_time_ms_.value() >
               kNoPacketsThresholdMs)) {
        // No packets seen for |kNoPacketsThresholdMs|, restart camera.
        capturer_->Start();
        test_state_ = kWaitingForMediaAfterCameraRestart;
      }
      return SEND_PACKET;
    }

    void ModifyVideoConfigs(
        VideoSendStream::Config* send_config,
        std::vector<VideoReceiveStream::Config>* receive_configs,
        VideoEncoderConfig* encoder_config) override {
      // Make sure padding is sent if encoder is not producing media.
      encoder_config->min_transmit_bitrate_bps = 50000;
    }

    void OnFrameGeneratorCapturerCreated(
        test::FrameGeneratorCapturer* frame_generator_capturer) override {
      MutexLock lock(&mutex_);
      capturer_ = frame_generator_capturer;
    }

    void PerformTest() override {
      EXPECT_TRUE(Wait())
          << "Timed out while waiting for RTP packets to stop being sent.";
    }

    enum TestState {
      kBeforeStopCapture,
      kWaitingForPadding,
      kWaitingForNoPackets,
      kWaitingForMediaAfterCameraRestart,
      kWaitingForPaddingAfterCameraStopsAgain
    };

    TestState test_state_ = kBeforeStopCapture;
    Clock* const clock_;
    Mutex mutex_;
    absl::optional<int64_t> last_packet_time_ms_ RTC_GUARDED_BY(mutex_);
    test::FrameGeneratorCapturer* capturer_ RTC_GUARDED_BY(mutex_);
  } test;

  RunBaseTest(&test);
}

TEST_F(VideoSendStreamTest, PaddingIsPrimarilyRetransmissions) {
  const int kCapacityKbps = 10000;  // 10 Mbps
  class PaddingIsPrimarilyRetransmissions : public test::EndToEndTest {
   public:
    PaddingIsPrimarilyRetransmissions()
        : EndToEndTest(kDefaultTimeoutMs),
          clock_(Clock::GetRealTimeClock()),
          padding_length_(0),
          total_length_(0),
          call_(nullptr) {}

   private:
    void OnCallsCreated(Call* sender_call, Call* receiver_call) override {
      call_ = sender_call;
    }

    Action OnSendRtp(const uint8_t* packet, size_t length) override {
      MutexLock lock(&mutex_);

      RtpPacket rtp_packet;
      rtp_packet.Parse(packet, length);
      padding_length_ += rtp_packet.padding_size();
      total_length_ += length;
      return SEND_PACKET;
    }

    std::unique_ptr<test::PacketTransport> CreateSendTransport(
        TaskQueueBase* task_queue,
        Call* sender_call) override {
      const int kNetworkDelayMs = 50;
      BuiltInNetworkBehaviorConfig config;
      config.loss_percent = 10;
      config.link_capacity_kbps = kCapacityKbps;
      config.queue_delay_ms = kNetworkDelayMs;
      return std::make_unique<test::PacketTransport>(
          task_queue, sender_call, this, test::PacketTransport::kSender,
          payload_type_map_,
          std::make_unique<FakeNetworkPipe>(
              Clock::GetRealTimeClock(),
              std::make_unique<SimulatedNetwork>(config)));
    }

    void ModifyVideoConfigs(
        VideoSendStream::Config* send_config,
        std::vector<VideoReceiveStream::Config>* receive_configs,
        VideoEncoderConfig* encoder_config) override {
      // Turn on RTX.
      send_config->rtp.rtx.payload_type = kFakeVideoSendPayloadType;
      send_config->rtp.rtx.ssrcs.push_back(kSendRtxSsrcs[0]);
    }

    void PerformTest() override {
      // TODO(isheriff): Some platforms do not ramp up as expected to full
      // capacity due to packet scheduling delays. Fix that before getting
      // rid of this.
      SleepMs(5000);
      {
        MutexLock lock(&mutex_);
        // Expect padding to be a small percentage of total bytes sent.
        EXPECT_LT(padding_length_, .1 * total_length_);
      }
    }

    Mutex mutex_;
    Clock* const clock_;
    size_t padding_length_ RTC_GUARDED_BY(mutex_);
    size_t total_length_ RTC_GUARDED_BY(mutex_);
    Call* call_;
  } test;

  RunBaseTest(&test);
}

// This test first observes "high" bitrate use at which point it sends a REMB to
// indicate that it should be lowered significantly. The test then observes that
// the bitrate observed is sinking well below the min-transmit-bitrate threshold
// to verify that the min-transmit bitrate respects incoming REMB.
//
// Note that the test starts at "high" bitrate and does not ramp up to "higher"
// bitrate since no receiver block or remb is sent in the initial phase.
TEST_F(VideoSendStreamTest, MinTransmitBitrateRespectsRemb) {
  static const int kMinTransmitBitrateBps = 400000;
  static const int kHighBitrateBps = 150000;
  static const int kRembBitrateBps = 80000;
  static const int kRembRespectedBitrateBps = 100000;
  class BitrateObserver : public test::SendTest {
   public:
    explicit BitrateObserver(TaskQueueBase* task_queue)
        : SendTest(kDefaultTimeoutMs),
          task_queue_(task_queue),
          retranmission_rate_limiter_(Clock::GetRealTimeClock(), 1000),
          stream_(nullptr),
          bitrate_capped_(false) {}

    ~BitrateObserver() override {
      // Make sure we free |rtp_rtcp_| in the same context as we constructed it.
      SendTask(RTC_FROM_HERE, task_queue_, [this]() { rtp_rtcp_ = nullptr; });
    }

   private:
    Action OnSendRtp(const uint8_t* packet, size_t length) override {
      if (RtpHeaderParser::IsRtcp(packet, length))
        return DROP_PACKET;

      RtpPacket rtp_packet;
      if (!rtp_packet.Parse(packet, length))
        return DROP_PACKET;
      RTC_DCHECK(stream_);
      VideoSendStream::Stats stats;
      SendTask(RTC_FROM_HERE, task_queue_,
               [&]() { stats = stream_->GetStats(); });
      if (!stats.substreams.empty()) {
        EXPECT_EQ(1u, stats.substreams.size());
        int total_bitrate_bps =
            stats.substreams.begin()->second.total_bitrate_bps;
        test::PrintResult("bitrate_stats_", "min_transmit_bitrate_low_remb",
                          "bitrate_bps", static_cast<size_t>(total_bitrate_bps),
                          "bps", false);
        if (total_bitrate_bps > kHighBitrateBps) {
          rtp_rtcp_->SetRemb(kRembBitrateBps, {rtp_packet.Ssrc()});
          rtp_rtcp_->Process();
          bitrate_capped_ = true;
        } else if (bitrate_capped_ &&
                   total_bitrate_bps < kRembRespectedBitrateBps) {
          observation_complete_.Set();
        }
      }
      // Packets don't have to be delivered since the test is the receiver.
      return DROP_PACKET;
    }

    void OnVideoStreamsCreated(
        VideoSendStream* send_stream,
        const std::vector<VideoReceiveStream*>& receive_streams) override {
      stream_ = send_stream;
      RtpRtcpInterface::Configuration config;
      config.clock = Clock::GetRealTimeClock();
      config.outgoing_transport = feedback_transport_.get();
      config.retransmission_rate_limiter = &retranmission_rate_limiter_;
      rtp_rtcp_ = ModuleRtpRtcpImpl2::Create(config);
      rtp_rtcp_->SetRTCPStatus(RtcpMode::kReducedSize);
    }

    void ModifyVideoConfigs(
        VideoSendStream::Config* send_config,
        std::vector<VideoReceiveStream::Config>* receive_configs,
        VideoEncoderConfig* encoder_config) override {
      feedback_transport_.reset(
          new internal::TransportAdapter(send_config->send_transport));
      feedback_transport_->Enable();
      encoder_config->min_transmit_bitrate_bps = kMinTransmitBitrateBps;
    }

    void PerformTest() override {
      EXPECT_TRUE(Wait())
          << "Timeout while waiting for low bitrate stats after REMB.";
    }

    TaskQueueBase* const task_queue_;
    std::unique_ptr<ModuleRtpRtcpImpl2> rtp_rtcp_;
    std::unique_ptr<internal::TransportAdapter> feedback_transport_;
    RateLimiter retranmission_rate_limiter_;
    VideoSendStream* stream_;
    bool bitrate_capped_;
  } test(task_queue());

  RunBaseTest(&test);
}

TEST_F(VideoSendStreamTest, ChangingNetworkRoute) {
  static const int kStartBitrateBps = 300000;
  static const int kNewMaxBitrateBps = 1234567;
  static const uint8_t kExtensionId = kTransportSequenceNumberExtensionId;
  class ChangingNetworkRouteTest : public test::EndToEndTest {
   public:
    explicit ChangingNetworkRouteTest(TaskQueueBase* task_queue)
        : EndToEndTest(test::CallTest::kDefaultTimeoutMs),
          task_queue_(task_queue),
          call_(nullptr) {
      module_process_thread_.Detach();
      task_queue_thread_.Detach();
      extensions_.Register<TransportSequenceNumber>(kExtensionId);
    }

    ~ChangingNetworkRouteTest() {
      // Block until all already posted tasks run to avoid 'use after free'
      // when such task accesses |this|.
      SendTask(RTC_FROM_HERE, task_queue_, [] {});
    }

    void OnCallsCreated(Call* sender_call, Call* receiver_call) override {
      RTC_DCHECK_RUN_ON(&task_queue_thread_);
      RTC_DCHECK(!call_);
      call_ = sender_call;
    }

    void ModifyVideoConfigs(
        VideoSendStream::Config* send_config,
        std::vector<VideoReceiveStream::Config>* receive_configs,
        VideoEncoderConfig* encoder_config) override {
      RTC_DCHECK_RUN_ON(&task_queue_thread_);
      send_config->rtp.extensions.clear();
      send_config->rtp.extensions.push_back(RtpExtension(
          RtpExtension::kTransportSequenceNumberUri, kExtensionId));
      (*receive_configs)[0].rtp.extensions = send_config->rtp.extensions;
      (*receive_configs)[0].rtp.transport_cc = true;
    }

    void ModifyAudioConfigs(
        AudioSendStream::Config* send_config,
        std::vector<AudioReceiveStream::Config>* receive_configs) override {
      RTC_DCHECK_RUN_ON(&task_queue_thread_);
      send_config->rtp.extensions.clear();
      send_config->rtp.extensions.push_back(RtpExtension(
          RtpExtension::kTransportSequenceNumberUri, kExtensionId));
      (*receive_configs)[0].rtp.extensions.clear();
      (*receive_configs)[0].rtp.extensions = send_config->rtp.extensions;
      (*receive_configs)[0].rtp.transport_cc = true;
    }

    Action OnSendRtp(const uint8_t* packet, size_t length) override {
      RTC_DCHECK_RUN_ON(&module_process_thread_);
      task_queue_->PostTask(ToQueuedTask([this]() {
        RTC_DCHECK_RUN_ON(&task_queue_thread_);
        if (!call_)
          return;
        Call::Stats stats = call_->GetStats();
        if (stats.send_bandwidth_bps > kStartBitrateBps)
          observation_complete_.Set();
      }));
      return SEND_PACKET;
    }

    void OnStreamsStopped() override {
      RTC_DCHECK_RUN_ON(&task_queue_thread_);
      call_ = nullptr;
    }

    void PerformTest() override {
      rtc::NetworkRoute new_route;
      new_route.connected = true;
      new_route.local = rtc::RouteEndpoint::CreateWithNetworkId(10);
      new_route.remote = rtc::RouteEndpoint::CreateWithNetworkId(20);
      BitrateConstraints bitrate_config;

      SendTask(RTC_FROM_HERE, task_queue_,
               [this, &new_route, &bitrate_config]() {
                 RTC_DCHECK_RUN_ON(&task_queue_thread_);
                 call_->GetTransportControllerSend()->OnNetworkRouteChanged(
                     "transport", new_route);
                 bitrate_config.start_bitrate_bps = kStartBitrateBps;
                 call_->GetTransportControllerSend()->SetSdpBitrateParameters(
                     bitrate_config);
               });

      EXPECT_TRUE(Wait())
          << "Timed out while waiting for start bitrate to be exceeded.";

      SendTask(
          RTC_FROM_HERE, task_queue_, [this, &new_route, &bitrate_config]() {
            RTC_DCHECK_RUN_ON(&task_queue_thread_);
            bitrate_config.start_bitrate_bps = -1;
            bitrate_config.max_bitrate_bps = kNewMaxBitrateBps;
            call_->GetTransportControllerSend()->SetSdpBitrateParameters(
                bitrate_config);
            // TODO(holmer): We should set the last sent packet id here and
            // verify that we correctly ignore any packet loss reported prior to
            // that id.
            new_route.local = rtc::RouteEndpoint::CreateWithNetworkId(
                new_route.local.network_id() + 1);
            call_->GetTransportControllerSend()->OnNetworkRouteChanged(
                "transport", new_route);
            EXPECT_GE(call_->GetStats().send_bandwidth_bps, kStartBitrateBps);
          });
    }

   private:
    webrtc::SequenceChecker module_process_thread_;
    webrtc::SequenceChecker task_queue_thread_;
    TaskQueueBase* const task_queue_;
    RtpHeaderExtensionMap extensions_;
    Call* call_ RTC_GUARDED_BY(task_queue_thread_);
  } test(task_queue());

  RunBaseTest(&test);
}

// Test that if specified, relay cap is lifted on transition to direct
// connection.
TEST_F(VideoSendStreamTest, RelayToDirectRoute) {
  static const int kStartBitrateBps = 300000;
  static const int kRelayBandwidthCapBps = 800000;
  static const int kMinPacketsToSend = 100;
  webrtc::test::ScopedFieldTrials field_trials(
      std::string(field_trial::GetFieldTrialString()) +
      "WebRTC-Bwe-NetworkRouteConstraints/relay_cap:" +
      std::to_string(kRelayBandwidthCapBps) + "bps/");

  class RelayToDirectRouteTest : public test::EndToEndTest {
   public:
    explicit RelayToDirectRouteTest(TaskQueueBase* task_queue)
        : EndToEndTest(test::CallTest::kDefaultTimeoutMs),
          task_queue_(task_queue),
          call_(nullptr),
          packets_sent_(0),
          relayed_phase_(true) {
      module_process_thread_.Detach();
      task_queue_thread_.Detach();
    }

    ~RelayToDirectRouteTest() {
      // Block until all already posted tasks run to avoid 'use after free'
      // when such task accesses |this|.
      SendTask(RTC_FROM_HERE, task_queue_, [] {});
    }

    void OnCallsCreated(Call* sender_call, Call* receiver_call) override {
      RTC_DCHECK_RUN_ON(&task_queue_thread_);
      RTC_DCHECK(!call_);
      call_ = sender_call;
    }

    Action OnSendRtp(const uint8_t* packet, size_t length) override {
      RTC_DCHECK_RUN_ON(&module_process_thread_);
      task_queue_->PostTask(ToQueuedTask([this]() {
        RTC_DCHECK_RUN_ON(&task_queue_thread_);
        if (!call_)
          return;
        bool had_time_to_exceed_cap_in_relayed_phase =
            relayed_phase_ && ++packets_sent_ > kMinPacketsToSend;
        bool did_exceed_cap =
            call_->GetStats().send_bandwidth_bps > kRelayBandwidthCapBps;
        if (did_exceed_cap || had_time_to_exceed_cap_in_relayed_phase)
          observation_complete_.Set();
      }));
      return SEND_PACKET;
    }

    void OnStreamsStopped() override {
      RTC_DCHECK_RUN_ON(&task_queue_thread_);
      call_ = nullptr;
    }

    void PerformTest() override {
      rtc::NetworkRoute route;
      route.connected = true;
      route.local = rtc::RouteEndpoint::CreateWithNetworkId(10);
      route.remote = rtc::RouteEndpoint::CreateWithNetworkId(20);

      SendTask(RTC_FROM_HERE, task_queue_, [this, &route]() {
        RTC_DCHECK_RUN_ON(&task_queue_thread_);
        relayed_phase_ = true;
        route.remote = route.remote.CreateWithTurn(true);
        call_->GetTransportControllerSend()->OnNetworkRouteChanged("transport",
                                                                   route);
        BitrateConstraints bitrate_config;
        bitrate_config.start_bitrate_bps = kStartBitrateBps;

        call_->GetTransportControllerSend()->SetSdpBitrateParameters(
            bitrate_config);
      });

      EXPECT_TRUE(Wait())
          << "Timeout waiting for sufficient packets sent count.";

      SendTask(RTC_FROM_HERE, task_queue_, [this, &route]() {
        RTC_DCHECK_RUN_ON(&task_queue_thread_);
        EXPECT_LE(call_->GetStats().send_bandwidth_bps, kRelayBandwidthCapBps);

        route.remote = route.remote.CreateWithTurn(false);
        call_->GetTransportControllerSend()->OnNetworkRouteChanged("transport",
                                                                   route);
        relayed_phase_ = false;
        observation_complete_.Reset();
      });

      EXPECT_TRUE(Wait())
          << "Timeout while waiting for bandwidth to outgrow relay cap.";
    }

   private:
    webrtc::SequenceChecker module_process_thread_;
    webrtc::SequenceChecker task_queue_thread_;
    TaskQueueBase* const task_queue_;
    Call* call_ RTC_GUARDED_BY(task_queue_thread_);
    int packets_sent_ RTC_GUARDED_BY(task_queue_thread_);
    bool relayed_phase_ RTC_GUARDED_BY(task_queue_thread_);
  } test(task_queue());

  RunBaseTest(&test);
}

TEST_F(VideoSendStreamTest, ChangingTransportOverhead) {
  class ChangingTransportOverheadTest : public test::EndToEndTest {
   public:
    explicit ChangingTransportOverheadTest(TaskQueueBase* task_queue)
        : EndToEndTest(test::CallTest::kDefaultTimeoutMs),
          task_queue_(task_queue),
          call_(nullptr),
          packets_sent_(0),
          transport_overhead_(0) {}

    void OnCallsCreated(Call* sender_call, Call* receiver_call) override {
      call_ = sender_call;
    }

    Action OnSendRtp(const uint8_t* packet, size_t length) override {
      EXPECT_LE(length, kMaxRtpPacketSize);
      MutexLock lock(&lock_);
      if (++packets_sent_ < 100)
        return SEND_PACKET;
      observation_complete_.Set();
      return SEND_PACKET;
    }

    void ModifyVideoConfigs(
        VideoSendStream::Config* send_config,
        std::vector<VideoReceiveStream::Config>* receive_configs,
        VideoEncoderConfig* encoder_config) override {
      send_config->rtp.max_packet_size = kMaxRtpPacketSize;
    }

    void PerformTest() override {
      SendTask(RTC_FROM_HERE, task_queue_, [this]() {
        transport_overhead_ = 100;
        call_->GetTransportControllerSend()->OnTransportOverheadChanged(
            transport_overhead_);
      });

      EXPECT_TRUE(Wait());

      {
        MutexLock lock(&lock_);
        packets_sent_ = 0;
      }

      SendTask(RTC_FROM_HERE, task_queue_, [this]() {
        transport_overhead_ = 500;
        call_->GetTransportControllerSend()->OnTransportOverheadChanged(
            transport_overhead_);
      });

      EXPECT_TRUE(Wait());
    }

   private:
    TaskQueueBase* const task_queue_;
    Call* call_;
    Mutex lock_;
    int packets_sent_ RTC_GUARDED_BY(lock_);
    int transport_overhead_;
    const size_t kMaxRtpPacketSize = 1000;
  } test(task_queue());

  RunBaseTest(&test);
}

// Test class takes takes as argument a switch selecting if type switch should
// occur and a function pointer to reset the send stream. This is necessary
// since you cannot change the content type of a VideoSendStream, you need to
// recreate it. Stopping and recreating the stream can only be done on the main
// thread and in the context of VideoSendStreamTest (not BaseTest).
template <typename T>
class MaxPaddingSetTest : public test::SendTest {
 public:
  static const uint32_t kMinTransmitBitrateBps = 400000;
  static const uint32_t kActualEncodeBitrateBps = 40000;
  static const uint32_t kMinPacketsToSend = 50;

  MaxPaddingSetTest(bool test_switch_content_type,
                    T* stream_reset_fun,
                    TaskQueueBase* task_queue)
      : SendTest(test::CallTest::kDefaultTimeoutMs),
        running_without_padding_(test_switch_content_type),
        stream_resetter_(stream_reset_fun),
        task_queue_(task_queue) {
    RTC_DCHECK(stream_resetter_);
    module_process_thread_.Detach();
    task_queue_thread_.Detach();
  }

  ~MaxPaddingSetTest() {
    // Block until all already posted tasks run to avoid 'use after free'
    // when such task accesses |this|.
    SendTask(RTC_FROM_HERE, task_queue_, [] {});
  }

  void ModifyVideoConfigs(
      VideoSendStream::Config* send_config,
      std::vector<VideoReceiveStream::Config>* receive_configs,
      VideoEncoderConfig* encoder_config) override {
    RTC_DCHECK_RUN_ON(&task_queue_thread_);
    RTC_DCHECK_EQ(1, encoder_config->number_of_streams);
    if (running_without_padding_) {
      encoder_config->min_transmit_bitrate_bps = 0;
      encoder_config->content_type =
          VideoEncoderConfig::ContentType::kRealtimeVideo;
    } else {
      encoder_config->min_transmit_bitrate_bps = kMinTransmitBitrateBps;
      encoder_config->content_type = VideoEncoderConfig::ContentType::kScreen;
    }
    send_stream_config_ = send_config->Copy();
    encoder_config_ = encoder_config->Copy();
  }

  void OnCallsCreated(Call* sender_call, Call* receiver_call) override {
    RTC_DCHECK_RUN_ON(&task_queue_thread_);
    RTC_DCHECK(task_queue_->IsCurrent());
    RTC_DCHECK(!call_);
    RTC_DCHECK(sender_call);
    call_ = sender_call;
  }

  // Called on the pacer thread.
  Action OnSendRtp(const uint8_t* packet, size_t length) override {
    RTC_DCHECK_RUN_ON(&module_process_thread_);

    // Check the stats on the correct thread and signal the 'complete' flag
    // once we detect that we're done.

    task_queue_->PostTask(ToQueuedTask([this]() {
      RTC_DCHECK_RUN_ON(&task_queue_thread_);
      // In case we get a callback during teardown.
      // When this happens, OnStreamsStopped() has been called already,
      // |call_| is null and the streams are being torn down.
      if (!call_)
        return;

      ++packets_sent_;

      Call::Stats stats = call_->GetStats();
      if (running_without_padding_) {
        EXPECT_EQ(0, stats.max_padding_bitrate_bps);

        // Wait until at least kMinPacketsToSend frames have been encoded, so
        // that we have reliable data.
        if (packets_sent_ < kMinPacketsToSend)
          return;

        // We've sent kMinPacketsToSend packets with default configuration,
        // switch to enabling screen content and setting min transmit bitrate.
        // Note that we need to recreate the stream if changing content type.
        packets_sent_ = 0;

        encoder_config_.min_transmit_bitrate_bps = kMinTransmitBitrateBps;
        encoder_config_.content_type = VideoEncoderConfig::ContentType::kScreen;

        running_without_padding_ = false;
        (*stream_resetter_)(send_stream_config_, encoder_config_);
      } else {
        // Make sure the pacer has been configured with a min transmit bitrate.
        if (stats.max_padding_bitrate_bps > 0) {
          observation_complete_.Set();
        }
      }
    }));

    return SEND_PACKET;
  }

  // Called on |task_queue_|
  void OnStreamsStopped() override {
    RTC_DCHECK_RUN_ON(&task_queue_thread_);
    RTC_DCHECK(task_queue_->IsCurrent());
    call_ = nullptr;
  }

  void PerformTest() override {
    ASSERT_TRUE(Wait()) << "Timed out waiting for a valid padding bitrate.";
  }

 private:
  webrtc::SequenceChecker task_queue_thread_;
  Call* call_ RTC_GUARDED_BY(task_queue_thread_) = nullptr;
  VideoSendStream::Config send_stream_config_{nullptr};
  VideoEncoderConfig encoder_config_;
  webrtc::SequenceChecker module_process_thread_;
  uint32_t packets_sent_ RTC_GUARDED_BY(task_queue_thread_) = 0;
  bool running_without_padding_ RTC_GUARDED_BY(task_queue_thread_);
  T* const stream_resetter_;
  TaskQueueBase* const task_queue_;
};

TEST_F(VideoSendStreamTest, RespectsMinTransmitBitrate) {
  auto reset_fun = [](const VideoSendStream::Config& send_stream_config,
                      const VideoEncoderConfig& encoder_config) {};
  MaxPaddingSetTest<decltype(reset_fun)> test(false, &reset_fun, task_queue());
  RunBaseTest(&test);
}

TEST_F(VideoSendStreamTest, RespectsMinTransmitBitrateAfterContentSwitch) {
  // Function for removing and recreating the send stream with a new config.
  auto reset_fun = [this](const VideoSendStream::Config& send_stream_config,
                          const VideoEncoderConfig& encoder_config) {
    RTC_DCHECK(task_queue()->IsCurrent());
    Stop();
    DestroyVideoSendStreams();
    SetVideoSendConfig(send_stream_config);
    SetVideoEncoderConfig(encoder_config);
    CreateVideoSendStreams();
    SetVideoDegradation(DegradationPreference::MAINTAIN_RESOLUTION);
    Start();
  };
  MaxPaddingSetTest<decltype(reset_fun)> test(true, &reset_fun, task_queue());
  RunBaseTest(&test);
}

// This test verifies that new frame sizes reconfigures encoders even though not
// (yet) sending. The purpose of this is to permit encoding as quickly as
// possible once we start sending. Likely the frames being input are from the
// same source that will be sent later, which just means that we're ready
// earlier.
TEST_F(VideoSendStreamTest,
       EncoderReconfigureOnResolutionChangeWhenNotSending) {
  class EncoderObserver : public test::FakeEncoder {
   public:
    EncoderObserver()
        : FakeEncoder(Clock::GetRealTimeClock()),
          last_initialized_frame_width_(0),
          last_initialized_frame_height_(0) {}

    void WaitForResolution(int width, int height) {
      {
        MutexLock lock(&mutex_);
        if (last_initialized_frame_width_ == width &&
            last_initialized_frame_height_ == height) {
          return;
        }
      }
      EXPECT_TRUE(
          init_encode_called_.Wait(VideoSendStreamTest::kDefaultTimeoutMs));
      {
        MutexLock lock(&mutex_);
        EXPECT_EQ(width, last_initialized_frame_width_);
        EXPECT_EQ(height, last_initialized_frame_height_);
      }
    }

   private:
    int32_t InitEncode(const VideoCodec* config,
                       const Settings& settings) override {
      MutexLock lock(&mutex_);
      last_initialized_frame_width_ = config->width;
      last_initialized_frame_height_ = config->height;
      init_encode_called_.Set();
      return FakeEncoder::InitEncode(config, settings);
    }

    int32_t Encode(const VideoFrame& input_image,
                   const std::vector<VideoFrameType>* frame_types) override {
      ADD_FAILURE()
          << "Unexpected Encode call since the send stream is not started";
      return 0;
    }

    Mutex mutex_;
    rtc::Event init_encode_called_;
    int last_initialized_frame_width_ RTC_GUARDED_BY(&mutex_);
    int last_initialized_frame_height_ RTC_GUARDED_BY(&mutex_);
  };

  test::NullTransport transport;
  EncoderObserver encoder;
  test::VideoEncoderProxyFactory encoder_factory(&encoder);

  SendTask(RTC_FROM_HERE, task_queue(), [this, &transport, &encoder_factory]() {
    CreateSenderCall();
    CreateSendConfig(1, 0, 0, &transport);
    GetVideoSendConfig()->encoder_settings.encoder_factory = &encoder_factory;
    CreateVideoStreams();
    CreateFrameGeneratorCapturer(kDefaultFramerate, kDefaultWidth,
                                 kDefaultHeight);
    frame_generator_capturer_->Start();
  });

  encoder.WaitForResolution(kDefaultWidth, kDefaultHeight);

  SendTask(RTC_FROM_HERE, task_queue(), [this]() {
    frame_generator_capturer_->ChangeResolution(kDefaultWidth * 2,
                                                kDefaultHeight * 2);
  });

  encoder.WaitForResolution(kDefaultWidth * 2, kDefaultHeight * 2);

  SendTask(RTC_FROM_HERE, task_queue(), [this]() {
    DestroyStreams();
    DestroyCalls();
  });
}

TEST_F(VideoSendStreamTest, CanReconfigureToUseStartBitrateAbovePreviousMax) {
  class StartBitrateObserver : public test::FakeEncoder {
   public:
    StartBitrateObserver()
        : FakeEncoder(Clock::GetRealTimeClock()), start_bitrate_kbps_(0) {}
    int32_t InitEncode(const VideoCodec* config,
                       const Settings& settings) override {
      MutexLock lock(&mutex_);
      start_bitrate_kbps_ = config->startBitrate;
      start_bitrate_changed_.Set();
      return FakeEncoder::InitEncode(config, settings);
    }

    void SetRates(const RateControlParameters& parameters) override {
      MutexLock lock(&mutex_);
      start_bitrate_kbps_ = parameters.bitrate.get_sum_kbps();
      start_bitrate_changed_.Set();
      FakeEncoder::SetRates(parameters);
    }

    int GetStartBitrateKbps() const {
      MutexLock lock(&mutex_);
      return start_bitrate_kbps_;
    }

    bool WaitForStartBitrate() {
      return start_bitrate_changed_.Wait(
          VideoSendStreamTest::kDefaultTimeoutMs);
    }

   private:
    mutable Mutex mutex_;
    rtc::Event start_bitrate_changed_;
    int start_bitrate_kbps_ RTC_GUARDED_BY(mutex_);
  };

  CreateSenderCall();

  test::NullTransport transport;
  CreateSendConfig(1, 0, 0, &transport);

  BitrateConstraints bitrate_config;
  bitrate_config.start_bitrate_bps =
      2 * GetVideoEncoderConfig()->max_bitrate_bps;
  sender_call_->GetTransportControllerSend()->SetSdpBitrateParameters(
      bitrate_config);

  StartBitrateObserver encoder;
  test::VideoEncoderProxyFactory encoder_factory(&encoder);
  GetVideoSendConfig()->encoder_settings.encoder_factory = &encoder_factory;

  CreateVideoStreams();

  // Start capturing and encoding frames to force encoder reconfiguration.
  CreateFrameGeneratorCapturer(kDefaultFramerate, kDefaultWidth,
                               kDefaultHeight);
  frame_generator_capturer_->Start();

  EXPECT_TRUE(encoder.WaitForStartBitrate());
  EXPECT_EQ(GetVideoEncoderConfig()->max_bitrate_bps / 1000,
            encoder.GetStartBitrateKbps());

  GetVideoEncoderConfig()->max_bitrate_bps =
      2 * bitrate_config.start_bitrate_bps;
  GetVideoSendStream()->ReconfigureVideoEncoder(
      GetVideoEncoderConfig()->Copy());

  // New bitrate should be reconfigured above the previous max. As there's no
  // network connection this shouldn't be flaky, as no bitrate should've been
  // reported in between.
  EXPECT_TRUE(encoder.WaitForStartBitrate());
  EXPECT_EQ(bitrate_config.start_bitrate_bps / 1000,
            encoder.GetStartBitrateKbps());

  DestroyStreams();
}

class StartStopBitrateObserver : public test::FakeEncoder {
 public:
  StartStopBitrateObserver() : FakeEncoder(Clock::GetRealTimeClock()) {}
  int32_t InitEncode(const VideoCodec* config,
                     const Settings& settings) override {
    MutexLock lock(&mutex_);
    encoder_init_.Set();
    return FakeEncoder::InitEncode(config, settings);
  }

  void SetRates(const RateControlParameters& parameters) override {
    MutexLock lock(&mutex_);
    bitrate_kbps_ = parameters.bitrate.get_sum_kbps();
    bitrate_changed_.Set();
    FakeEncoder::SetRates(parameters);
  }

  bool WaitForEncoderInit() {
    return encoder_init_.Wait(VideoSendStreamTest::kDefaultTimeoutMs);
  }

  bool WaitBitrateChanged(bool non_zero) {
    do {
      absl::optional<int> bitrate_kbps;
      {
        MutexLock lock(&mutex_);
        bitrate_kbps = bitrate_kbps_;
      }
      if (!bitrate_kbps)
        continue;

      if ((non_zero && *bitrate_kbps > 0) ||
          (!non_zero && *bitrate_kbps == 0)) {
        return true;
      }
    } while (bitrate_changed_.Wait(VideoSendStreamTest::kDefaultTimeoutMs));
    return false;
  }

 private:
  Mutex mutex_;
  rtc::Event encoder_init_;
  rtc::Event bitrate_changed_;
  absl::optional<int> bitrate_kbps_ RTC_GUARDED_BY(mutex_);
};

// This test that if the encoder use an internal source, VideoEncoder::SetRates
// will be called with zero bitrate during initialization and that
// VideoSendStream::Stop also triggers VideoEncoder::SetRates Start to be called
// with zero bitrate.
TEST_F(VideoSendStreamTest, VideoSendStreamStopSetEncoderRateToZero) {
  test::NullTransport transport;
  StartStopBitrateObserver encoder;
  test::VideoEncoderProxyFactory encoder_factory(&encoder);
  encoder_factory.SetHasInternalSource(true);
  test::FrameForwarder forwarder;

  SendTask(RTC_FROM_HERE, task_queue(),
           [this, &transport, &encoder_factory, &forwarder]() {
             CreateSenderCall();
             CreateSendConfig(1, 0, 0, &transport);

             sender_call_->SignalChannelNetworkState(MediaType::VIDEO,
                                                     kNetworkUp);
             GetVideoSendConfig()->encoder_settings.encoder_factory =
                 &encoder_factory;

             CreateVideoStreams();
             // Inject a frame, to force encoder creation.
             GetVideoSendStream()->Start();
             GetVideoSendStream()->SetSource(&forwarder,
                                             DegradationPreference::DISABLED);
             forwarder.IncomingCapturedFrame(CreateVideoFrame(640, 480, 4));
           });

  EXPECT_TRUE(encoder.WaitForEncoderInit());

  SendTask(RTC_FROM_HERE, task_queue(),
           [this]() { GetVideoSendStream()->Start(); });
  EXPECT_TRUE(encoder.WaitBitrateChanged(true));

  SendTask(RTC_FROM_HERE, task_queue(),
           [this]() { GetVideoSendStream()->Stop(); });
  EXPECT_TRUE(encoder.WaitBitrateChanged(false));

  SendTask(RTC_FROM_HERE, task_queue(),
           [this]() { GetVideoSendStream()->Start(); });
  EXPECT_TRUE(encoder.WaitBitrateChanged(true));

  SendTask(RTC_FROM_HERE, task_queue(), [this]() {
    DestroyStreams();
    DestroyCalls();
  });
}

// Tests that when the encoder uses an internal source, the VideoEncoder will
// be updated with a new bitrate when turning the VideoSendStream on/off with
// VideoSendStream::UpdateActiveSimulcastLayers, and when the VideoStreamEncoder
// is reconfigured with new active layers.
TEST_F(VideoSendStreamTest, VideoSendStreamUpdateActiveSimulcastLayers) {
  test::NullTransport transport;
  StartStopBitrateObserver encoder;
  test::VideoEncoderProxyFactory encoder_factory(&encoder);
  encoder_factory.SetHasInternalSource(true);
  test::FrameForwarder forwarder;

  SendTask(RTC_FROM_HERE, task_queue(),
           [this, &transport, &encoder_factory, &forwarder]() {
             CreateSenderCall();
             // Create two simulcast streams.
             CreateSendConfig(2, 0, 0, &transport);

             sender_call_->SignalChannelNetworkState(MediaType::VIDEO,
                                                     kNetworkUp);
             GetVideoSendConfig()->encoder_settings.encoder_factory =
                 &encoder_factory;

             CreateVideoStreams();

             // Inject a frame, to force encoder creation.
             GetVideoSendStream()->Start();
             GetVideoSendStream()->SetSource(&forwarder,
                                             DegradationPreference::DISABLED);
             forwarder.IncomingCapturedFrame(CreateVideoFrame(640, 480, 4));
           });

  EXPECT_TRUE(encoder.WaitForEncoderInit());

  // When we turn on the simulcast layers it will update the BitrateAllocator,
  // which in turn updates the VideoEncoder's bitrate.
  SendTask(RTC_FROM_HERE, task_queue(), [this]() {
    GetVideoSendStream()->UpdateActiveSimulcastLayers({true, true});
  });
  EXPECT_TRUE(encoder.WaitBitrateChanged(true));

  GetVideoEncoderConfig()->simulcast_layers[0].active = true;
  GetVideoEncoderConfig()->simulcast_layers[1].active = false;
  SendTask(RTC_FROM_HERE, task_queue(), [this]() {
    GetVideoSendStream()->ReconfigureVideoEncoder(
        GetVideoEncoderConfig()->Copy());
  });
  EXPECT_TRUE(encoder.WaitBitrateChanged(true));

  // Turning off both simulcast layers should trigger a bitrate change of 0.
  GetVideoEncoderConfig()->simulcast_layers[0].active = false;
  GetVideoEncoderConfig()->simulcast_layers[1].active = false;
  SendTask(RTC_FROM_HERE, task_queue(), [this]() {
    GetVideoSendStream()->UpdateActiveSimulcastLayers({false, false});
  });
  EXPECT_TRUE(encoder.WaitBitrateChanged(false));

  SendTask(RTC_FROM_HERE, task_queue(), [this]() {
    DestroyStreams();
    DestroyCalls();
  });
}

TEST_F(VideoSendStreamTest, EncoderIsProperlyInitializedAndDestroyed) {
  class EncoderStateObserver : public test::SendTest, public VideoEncoder {
   public:
    explicit EncoderStateObserver(TaskQueueBase* task_queue)
        : SendTest(kDefaultTimeoutMs),
          task_queue_(task_queue),
          stream_(nullptr),
          initialized_(false),
          callback_registered_(false),
          num_releases_(0),
          released_(false),
          encoder_factory_(this) {}

    bool IsReleased() RTC_LOCKS_EXCLUDED(mutex_) {
      MutexLock lock(&mutex_);
      return released_;
    }

    bool IsReadyForEncode() RTC_LOCKS_EXCLUDED(mutex_) {
      MutexLock lock(&mutex_);
      return IsReadyForEncodeLocked();
    }

    size_t num_releases() RTC_LOCKS_EXCLUDED(mutex_) {
      MutexLock lock(&mutex_);
      return num_releases_;
    }

   private:
    bool IsReadyForEncodeLocked() RTC_EXCLUSIVE_LOCKS_REQUIRED(mutex_) {
      return initialized_ && callback_registered_;
    }

    void SetFecControllerOverride(
        FecControllerOverride* fec_controller_override) override {
      // Ignored.
    }

    int32_t InitEncode(const VideoCodec* codecSettings,
                       const Settings& settings) override
        RTC_LOCKS_EXCLUDED(mutex_) {
      MutexLock lock(&mutex_);
      EXPECT_FALSE(initialized_);
      initialized_ = true;
      released_ = false;
      return 0;
    }

    int32_t Encode(const VideoFrame& inputImage,
                   const std::vector<VideoFrameType>* frame_types) override {
      EXPECT_TRUE(IsReadyForEncode());

      observation_complete_.Set();
      return 0;
    }

    int32_t RegisterEncodeCompleteCallback(
        EncodedImageCallback* callback) override RTC_LOCKS_EXCLUDED(mutex_) {
      MutexLock lock(&mutex_);
      EXPECT_TRUE(initialized_);
      callback_registered_ = true;
      return 0;
    }

    int32_t Release() override RTC_LOCKS_EXCLUDED(mutex_) {
      MutexLock lock(&mutex_);
      EXPECT_TRUE(IsReadyForEncodeLocked());
      EXPECT_FALSE(released_);
      initialized_ = false;
      callback_registered_ = false;
      released_ = true;
      ++num_releases_;
      return 0;
    }

    void SetRates(const RateControlParameters& parameters) override {
      EXPECT_TRUE(IsReadyForEncode());
    }

    void OnVideoStreamsCreated(
        VideoSendStream* send_stream,
        const std::vector<VideoReceiveStream*>& receive_streams) override {
      stream_ = send_stream;
    }

    void ModifyVideoConfigs(
        VideoSendStream::Config* send_config,
        std::vector<VideoReceiveStream::Config>* receive_configs,
        VideoEncoderConfig* encoder_config) override {
      send_config->encoder_settings.encoder_factory = &encoder_factory_;
      encoder_config_ = encoder_config->Copy();
    }

    void PerformTest() override {
      EXPECT_TRUE(Wait()) << "Timed out while waiting for Encode.";

      SendTask(RTC_FROM_HERE, task_queue_, [this]() {
        EXPECT_EQ(0u, num_releases());
        stream_->ReconfigureVideoEncoder(std::move(encoder_config_));
        EXPECT_EQ(0u, num_releases());
        stream_->Stop();
        // Encoder should not be released before destroying the VideoSendStream.
        EXPECT_FALSE(IsReleased());
        EXPECT_TRUE(IsReadyForEncode());
        stream_->Start();
      });

      // Sanity check, make sure we still encode frames with this encoder.
      EXPECT_TRUE(Wait()) << "Timed out while waiting for Encode.";
    }

    TaskQueueBase* const task_queue_;
    Mutex mutex_;
    VideoSendStream* stream_;
    bool initialized_ RTC_GUARDED_BY(mutex_);
    bool callback_registered_ RTC_GUARDED_BY(mutex_);
    size_t num_releases_ RTC_GUARDED_BY(mutex_);
    bool released_ RTC_GUARDED_BY(mutex_);
    test::VideoEncoderProxyFactory encoder_factory_;
    VideoEncoderConfig encoder_config_;
  } test_encoder(task_queue());

  RunBaseTest(&test_encoder);

  EXPECT_TRUE(test_encoder.IsReleased());
  EXPECT_EQ(1u, test_encoder.num_releases());
}

static const size_t kVideoCodecConfigObserverNumberOfTemporalLayers = 3;
template <typename T>
class VideoCodecConfigObserver : public test::SendTest,
                                 public test::FakeEncoder {
 public:
  VideoCodecConfigObserver(VideoCodecType video_codec_type,
                           const char* codec_name,
                           TaskQueueBase* task_queue)
      : SendTest(VideoSendStreamTest::kDefaultTimeoutMs),
        FakeEncoder(Clock::GetRealTimeClock()),
        video_codec_type_(video_codec_type),
        codec_name_(codec_name),
        num_initializations_(0),
        stream_(nullptr),
        encoder_factory_(this),
        task_queue_(task_queue) {
    InitCodecSpecifics();
  }

 private:
  void ModifyVideoConfigs(
      VideoSendStream::Config* send_config,
      std::vector<VideoReceiveStream::Config>* receive_configs,
      VideoEncoderConfig* encoder_config) override {
    send_config->encoder_settings.encoder_factory = &encoder_factory_;
    send_config->rtp.payload_name = codec_name_;

    encoder_config->codec_type = video_codec_type_;
    encoder_config->encoder_specific_settings = GetEncoderSpecificSettings();
    EXPECT_EQ(1u, encoder_config->simulcast_layers.size());
    encoder_config->simulcast_layers[0].num_temporal_layers =
        kVideoCodecConfigObserverNumberOfTemporalLayers;
    encoder_config_ = encoder_config->Copy();
  }

  void OnVideoStreamsCreated(
      VideoSendStream* send_stream,
      const std::vector<VideoReceiveStream*>& receive_streams) override {
    stream_ = send_stream;
  }

  int32_t InitEncode(const VideoCodec* config,
                     const Settings& settings) override {
    EXPECT_EQ(video_codec_type_, config->codecType);
    VerifyCodecSpecifics(*config);
    ++num_initializations_;
    init_encode_event_.Set();
    return FakeEncoder::InitEncode(config, settings);
  }

  void InitCodecSpecifics();
  void VerifyCodecSpecifics(const VideoCodec& config) const;
  rtc::scoped_refptr<VideoEncoderConfig::EncoderSpecificSettings>
  GetEncoderSpecificSettings() const;

  void PerformTest() override {
    EXPECT_TRUE(
        init_encode_event_.Wait(VideoSendStreamTest::kDefaultTimeoutMs));
    ASSERT_EQ(1u, num_initializations_) << "VideoEncoder not initialized.";

    // Change encoder settings to actually trigger reconfiguration.
    encoder_settings_.frameDroppingOn = !encoder_settings_.frameDroppingOn;
    encoder_config_.encoder_specific_settings = GetEncoderSpecificSettings();
    SendTask(RTC_FROM_HERE, task_queue_, [&]() {
      stream_->ReconfigureVideoEncoder(std::move(encoder_config_));
    });
    ASSERT_TRUE(
        init_encode_event_.Wait(VideoSendStreamTest::kDefaultTimeoutMs));
    EXPECT_EQ(2u, num_initializations_)
        << "ReconfigureVideoEncoder did not reinitialize the encoder with "
           "new encoder settings.";
  }

  int32_t Encode(const VideoFrame& input_image,
                 const std::vector<VideoFrameType>* frame_types) override {
    // Silently skip the encode, FakeEncoder::Encode doesn't produce VP8.
    return 0;
  }

  T encoder_settings_;
  const VideoCodecType video_codec_type_;
  const char* const codec_name_;
  rtc::Event init_encode_event_;
  size_t num_initializations_;
  VideoSendStream* stream_;
  test::VideoEncoderProxyFactory encoder_factory_;
  VideoEncoderConfig encoder_config_;
  TaskQueueBase* task_queue_;
};

template <>
void VideoCodecConfigObserver<VideoCodecH264>::InitCodecSpecifics() {
  encoder_settings_ = VideoEncoder::GetDefaultH264Settings();
}

template <>
void VideoCodecConfigObserver<VideoCodecH264>::VerifyCodecSpecifics(
    const VideoCodec& config) const {
  // Check that the number of temporal layers has propagated properly to
  // VideoCodec.
  EXPECT_EQ(kVideoCodecConfigObserverNumberOfTemporalLayers,
            config.H264().numberOfTemporalLayers);

  for (unsigned char i = 0; i < config.numberOfSimulcastStreams; ++i) {
    EXPECT_EQ(kVideoCodecConfigObserverNumberOfTemporalLayers,
              config.simulcastStream[i].numberOfTemporalLayers);
  }

  // Set expected temporal layers as they should have been set when
  // reconfiguring the encoder and not match the set config.
  VideoCodecH264 encoder_settings = encoder_settings_;
  encoder_settings.numberOfTemporalLayers =
      kVideoCodecConfigObserverNumberOfTemporalLayers;
  EXPECT_EQ(
      0, memcmp(&config.H264(), &encoder_settings, sizeof(encoder_settings_)));
}

template <>
rtc::scoped_refptr<VideoEncoderConfig::EncoderSpecificSettings>
VideoCodecConfigObserver<VideoCodecH264>::GetEncoderSpecificSettings() const {
  return rtc::make_ref_counted<VideoEncoderConfig::H264EncoderSpecificSettings>(
      encoder_settings_);
}

template <>
void VideoCodecConfigObserver<VideoCodecVP8>::InitCodecSpecifics() {
  encoder_settings_ = VideoEncoder::GetDefaultVp8Settings();
}

template <>
void VideoCodecConfigObserver<VideoCodecVP8>::VerifyCodecSpecifics(
    const VideoCodec& config) const {
  // Check that the number of temporal layers has propagated properly to
  // VideoCodec.
  EXPECT_EQ(kVideoCodecConfigObserverNumberOfTemporalLayers,
            config.VP8().numberOfTemporalLayers);

  for (unsigned char i = 0; i < config.numberOfSimulcastStreams; ++i) {
    EXPECT_EQ(kVideoCodecConfigObserverNumberOfTemporalLayers,
              config.simulcastStream[i].numberOfTemporalLayers);
  }

  // Set expected temporal layers as they should have been set when
  // reconfiguring the encoder and not match the set config.
  VideoCodecVP8 encoder_settings = encoder_settings_;
  encoder_settings.numberOfTemporalLayers =
      kVideoCodecConfigObserverNumberOfTemporalLayers;
  EXPECT_EQ(
      0, memcmp(&config.VP8(), &encoder_settings, sizeof(encoder_settings_)));
}

template <>
rtc::scoped_refptr<VideoEncoderConfig::EncoderSpecificSettings>
VideoCodecConfigObserver<VideoCodecVP8>::GetEncoderSpecificSettings() const {
  return rtc::make_ref_counted<VideoEncoderConfig::Vp8EncoderSpecificSettings>(
      encoder_settings_);
}

template <>
void VideoCodecConfigObserver<VideoCodecVP9>::InitCodecSpecifics() {
  encoder_settings_ = VideoEncoder::GetDefaultVp9Settings();
}

template <>
void VideoCodecConfigObserver<VideoCodecVP9>::VerifyCodecSpecifics(
    const VideoCodec& config) const {
  // Check that the number of temporal layers has propagated properly to
  // VideoCodec.
  EXPECT_EQ(kVideoCodecConfigObserverNumberOfTemporalLayers,
            config.VP9().numberOfTemporalLayers);

  for (unsigned char i = 0; i < config.numberOfSimulcastStreams; ++i) {
    EXPECT_EQ(kVideoCodecConfigObserverNumberOfTemporalLayers,
              config.simulcastStream[i].numberOfTemporalLayers);
  }

  // Set expected temporal layers as they should have been set when
  // reconfiguring the encoder and not match the set config.
  VideoCodecVP9 encoder_settings = encoder_settings_;
  encoder_settings.numberOfTemporalLayers =
      kVideoCodecConfigObserverNumberOfTemporalLayers;
  EXPECT_EQ(
      0, memcmp(&(config.VP9()), &encoder_settings, sizeof(encoder_settings_)));
}

template <>
rtc::scoped_refptr<VideoEncoderConfig::EncoderSpecificSettings>
VideoCodecConfigObserver<VideoCodecVP9>::GetEncoderSpecificSettings() const {
  return rtc::make_ref_counted<VideoEncoderConfig::Vp9EncoderSpecificSettings>(
      encoder_settings_);
}

TEST_F(VideoSendStreamTest, EncoderSetupPropagatesVp8Config) {
  VideoCodecConfigObserver<VideoCodecVP8> test(kVideoCodecVP8, "VP8",
                                               task_queue());
  RunBaseTest(&test);
}

TEST_F(VideoSendStreamTest, EncoderSetupPropagatesVp9Config) {
  VideoCodecConfigObserver<VideoCodecVP9> test(kVideoCodecVP9, "VP9",
                                               task_queue());
  RunBaseTest(&test);
}

// Fails on MSAN: https://bugs.chromium.org/p/webrtc/issues/detail?id=11376.
#if defined(MEMORY_SANITIZER)
#define MAYBE_EncoderSetupPropagatesH264Config \
  DISABLED_EncoderSetupPropagatesH264Config
#else
#define MAYBE_EncoderSetupPropagatesH264Config EncoderSetupPropagatesH264Config
#endif
TEST_F(VideoSendStreamTest, MAYBE_EncoderSetupPropagatesH264Config) {
  VideoCodecConfigObserver<VideoCodecH264> test(kVideoCodecH264, "H264",
                                                task_queue());
  RunBaseTest(&test);
}

TEST_F(VideoSendStreamTest, RtcpSenderReportContainsMediaBytesSent) {
  class RtcpSenderReportTest : public test::SendTest {
   public:
    RtcpSenderReportTest()
        : SendTest(kDefaultTimeoutMs),
          rtp_packets_sent_(0),
          media_bytes_sent_(0) {}

   private:
    Action OnSendRtp(const uint8_t* packet, size_t length) override {
      MutexLock lock(&mutex_);
      RtpPacket rtp_packet;
      EXPECT_TRUE(rtp_packet.Parse(packet, length));
      ++rtp_packets_sent_;
      media_bytes_sent_ += rtp_packet.payload_size();
      return SEND_PACKET;
    }

    Action OnSendRtcp(const uint8_t* packet, size_t length) override {
      MutexLock lock(&mutex_);
      test::RtcpPacketParser parser;
      EXPECT_TRUE(parser.Parse(packet, length));

      if (parser.sender_report()->num_packets() > 0) {
        // Only compare sent media bytes if SenderPacketCount matches the
        // number of sent rtp packets (a new rtp packet could be sent before
        // the rtcp packet).
        if (parser.sender_report()->sender_octet_count() > 0 &&
            parser.sender_report()->sender_packet_count() ==
                rtp_packets_sent_) {
          EXPECT_EQ(media_bytes_sent_,
                    parser.sender_report()->sender_octet_count());
          observation_complete_.Set();
        }
      }

      return SEND_PACKET;
    }

    void PerformTest() override {
      EXPECT_TRUE(Wait()) << "Timed out while waiting for RTCP sender report.";
    }

    Mutex mutex_;
    size_t rtp_packets_sent_ RTC_GUARDED_BY(&mutex_);
    size_t media_bytes_sent_ RTC_GUARDED_BY(&mutex_);
  } test;

  RunBaseTest(&test);
}

TEST_F(VideoSendStreamTest, TranslatesTwoLayerScreencastToTargetBitrate) {
  static const int kScreencastMaxTargetBitrateDeltaKbps = 1;

  class VideoStreamFactory
      : public VideoEncoderConfig::VideoStreamFactoryInterface {
   public:
    VideoStreamFactory() {}

   private:
    std::vector<VideoStream> CreateEncoderStreams(
        int width,
        int height,
        const VideoEncoderConfig& encoder_config) override {
      std::vector<VideoStream> streams =
          test::CreateVideoStreams(width, height, encoder_config);
      RTC_CHECK_GT(streams[0].max_bitrate_bps,
                   kScreencastMaxTargetBitrateDeltaKbps);
      streams[0].target_bitrate_bps =
          streams[0].max_bitrate_bps -
          kScreencastMaxTargetBitrateDeltaKbps * 1000;
      return streams;
    }
  };

  class ScreencastTargetBitrateTest : public test::SendTest,
                                      public test::FakeEncoder {
   public:
    ScreencastTargetBitrateTest()
        : SendTest(kDefaultTimeoutMs),
          test::FakeEncoder(Clock::GetRealTimeClock()),
          encoder_factory_(this) {}

   private:
    int32_t InitEncode(const VideoCodec* config,
                       const Settings& settings) override {
      EXPECT_EQ(config->numberOfSimulcastStreams, 1);
      EXPECT_EQ(static_cast<unsigned int>(kScreencastMaxTargetBitrateDeltaKbps),
                config->simulcastStream[0].maxBitrate -
                    config->simulcastStream[0].targetBitrate);
      observation_complete_.Set();
      return test::FakeEncoder::InitEncode(config, settings);
    }

    void ModifyVideoConfigs(
        VideoSendStream::Config* send_config,
        std::vector<VideoReceiveStream::Config>* receive_configs,
        VideoEncoderConfig* encoder_config) override {
      send_config->encoder_settings.encoder_factory = &encoder_factory_;
      EXPECT_EQ(1u, encoder_config->number_of_streams);
      encoder_config->video_stream_factory =
          rtc::make_ref_counted<VideoStreamFactory>();
      EXPECT_EQ(1u, encoder_config->simulcast_layers.size());
      encoder_config->simulcast_layers[0].num_temporal_layers = 2;
      encoder_config->content_type = VideoEncoderConfig::ContentType::kScreen;
    }

    void PerformTest() override {
      EXPECT_TRUE(Wait())
          << "Timed out while waiting for the encoder to be initialized.";
    }
    test::VideoEncoderProxyFactory encoder_factory_;
  } test;

  RunBaseTest(&test);
}

TEST_F(VideoSendStreamTest, ReconfigureBitratesSetsEncoderBitratesCorrectly) {
  // These are chosen to be "kind of odd" to not be accidentally checked against
  // default values.
  static const int kMinBitrateKbps = 137;
  static const int kStartBitrateKbps = 345;
  static const int kLowerMaxBitrateKbps = 312;
  static const int kMaxBitrateKbps = 413;
  static const int kIncreasedStartBitrateKbps = 451;
  static const int kIncreasedMaxBitrateKbps = 597;
  // TODO(bugs.webrtc.org/12058): If these fields trial are on, we get lower
  // bitrates than expected by this test, due to encoder pushback and subtracted
  // overhead.
  webrtc::test::ScopedFieldTrials field_trials(
      std::string(field_trial::GetFieldTrialString()) +
      "WebRTC-VideoRateControl/bitrate_adjuster:false/"
      "WebRTC-SendSideBwe-WithOverhead/Disabled/");

  class EncoderBitrateThresholdObserver : public test::SendTest,
                                          public VideoBitrateAllocatorFactory,
                                          public test::FakeEncoder {
   public:
    explicit EncoderBitrateThresholdObserver(TaskQueueBase* task_queue)
        : SendTest(kDefaultTimeoutMs),
          FakeEncoder(Clock::GetRealTimeClock()),
          task_queue_(task_queue),
          target_bitrate_(0),
          num_rate_allocator_creations_(0),
          num_encoder_initializations_(0),
          call_(nullptr),
          send_stream_(nullptr),
          encoder_factory_(this),
          bitrate_allocator_factory_(
              CreateBuiltinVideoBitrateAllocatorFactory()) {}

   private:
    std::unique_ptr<VideoBitrateAllocator> CreateVideoBitrateAllocator(
        const VideoCodec& codec) override {
      EXPECT_GE(codec.startBitrate, codec.minBitrate);
      EXPECT_LE(codec.startBitrate, codec.maxBitrate);
      if (num_rate_allocator_creations_ == 0) {
        EXPECT_EQ(static_cast<unsigned int>(kMinBitrateKbps), codec.minBitrate);
        EXPECT_EQ(static_cast<unsigned int>(kStartBitrateKbps),
                  codec.startBitrate);
        EXPECT_EQ(static_cast<unsigned int>(kMaxBitrateKbps), codec.maxBitrate);
      } else if (num_rate_allocator_creations_ == 1) {
        EXPECT_EQ(static_cast<unsigned int>(kLowerMaxBitrateKbps),
                  codec.maxBitrate);
        // The start bitrate should be kept (-1) and capped to the max bitrate.
        // Since this is not an end-to-end call no receiver should have been
        // returning a REMB that could lower this estimate.
        EXPECT_EQ(codec.startBitrate, codec.maxBitrate);
      } else if (num_rate_allocator_creations_ == 2) {
        EXPECT_EQ(static_cast<unsigned int>(kIncreasedMaxBitrateKbps),
                  codec.maxBitrate);
        // The start bitrate will be whatever the rate BitRateController has
        // currently configured but in the span of the set max and min bitrate.
      }
      ++num_rate_allocator_creations_;
      create_rate_allocator_event_.Set();

      return bitrate_allocator_factory_->CreateVideoBitrateAllocator(codec);
    }

    int32_t InitEncode(const VideoCodec* codecSettings,
                       const Settings& settings) override {
      EXPECT_EQ(0, num_encoder_initializations_);
      EXPECT_EQ(static_cast<unsigned int>(kMinBitrateKbps),
                codecSettings->minBitrate);
      EXPECT_EQ(static_cast<unsigned int>(kStartBitrateKbps),
                codecSettings->startBitrate);
      EXPECT_EQ(static_cast<unsigned int>(kMaxBitrateKbps),
                codecSettings->maxBitrate);

      ++num_encoder_initializations_;

      observation_complete_.Set();
      init_encode_event_.Set();

      return FakeEncoder::InitEncode(codecSettings, settings);
    }

    void SetRates(const RateControlParameters& parameters) override {
      {
        MutexLock lock(&mutex_);
        if (target_bitrate_ == parameters.bitrate.get_sum_kbps()) {
          FakeEncoder::SetRates(parameters);
          return;
        }
        target_bitrate_ = parameters.bitrate.get_sum_kbps();
      }
      bitrate_changed_event_.Set();
      FakeEncoder::SetRates(parameters);
    }

    void WaitForSetRates(uint32_t expected_bitrate) {
      // Wait for the expected rate to be set. In some cases there can be
      // more than one update pending, in which case we keep waiting
      // until the correct value has been observed.
      const int64_t start_time = rtc::TimeMillis();
      do {
        MutexLock lock(&mutex_);
        if (target_bitrate_ == expected_bitrate) {
          return;
        }
      } while (bitrate_changed_event_.Wait(
          std::max(int64_t{1}, VideoSendStreamTest::kDefaultTimeoutMs -
                                   (rtc::TimeMillis() - start_time))));
      MutexLock lock(&mutex_);
      EXPECT_EQ(target_bitrate_, expected_bitrate)
          << "Timed out while waiting encoder rate to be set.";
    }

    void ModifySenderBitrateConfig(
        BitrateConstraints* bitrate_config) override {
      bitrate_config->min_bitrate_bps = kMinBitrateKbps * 1000;
      bitrate_config->start_bitrate_bps = kStartBitrateKbps * 1000;
      bitrate_config->max_bitrate_bps = kMaxBitrateKbps * 1000;
    }

    void ModifyVideoConfigs(
        VideoSendStream::Config* send_config,
        std::vector<VideoReceiveStream::Config>* receive_configs,
        VideoEncoderConfig* encoder_config) override {
      send_config->encoder_settings.encoder_factory = &encoder_factory_;
      send_config->encoder_settings.bitrate_allocator_factory = this;
      // Set bitrates lower/higher than min/max to make sure they are properly
      // capped.
      encoder_config->max_bitrate_bps = kMaxBitrateKbps * 1000;
      EXPECT_EQ(1u, encoder_config->simulcast_layers.size());
      encoder_config->simulcast_layers[0].min_bitrate_bps =
          kMinBitrateKbps * 1000;
      encoder_config_ = encoder_config->Copy();
    }

    void OnCallsCreated(Call* sender_call, Call* receiver_call) override {
      call_ = sender_call;
    }

    void OnVideoStreamsCreated(
        VideoSendStream* send_stream,
        const std::vector<VideoReceiveStream*>& receive_streams) override {
      send_stream_ = send_stream;
    }

    void PerformTest() override {
      ASSERT_TRUE(create_rate_allocator_event_.Wait(
          VideoSendStreamTest::kDefaultTimeoutMs))
          << "Timed out while waiting for rate allocator to be created.";
      ASSERT_TRUE(
          init_encode_event_.Wait(VideoSendStreamTest::kDefaultTimeoutMs))
          << "Timed out while waiting for encoder to be configured.";
      WaitForSetRates(kStartBitrateKbps);
      BitrateConstraints bitrate_config;
      bitrate_config.start_bitrate_bps = kIncreasedStartBitrateKbps * 1000;
      bitrate_config.max_bitrate_bps = kIncreasedMaxBitrateKbps * 1000;
      SendTask(RTC_FROM_HERE, task_queue_, [this, &bitrate_config]() {
        call_->GetTransportControllerSend()->SetSdpBitrateParameters(
            bitrate_config);
      });
      // Encoder rate is capped by EncoderConfig max_bitrate_bps.
      WaitForSetRates(kMaxBitrateKbps);
      encoder_config_.max_bitrate_bps = kLowerMaxBitrateKbps * 1000;
      SendTask(RTC_FROM_HERE, task_queue_, [&]() {
        send_stream_->ReconfigureVideoEncoder(encoder_config_.Copy());
      });
      ASSERT_TRUE(create_rate_allocator_event_.Wait(
          VideoSendStreamTest::kDefaultTimeoutMs));
      EXPECT_EQ(2, num_rate_allocator_creations_)
          << "Rate allocator should have been recreated.";

      WaitForSetRates(kLowerMaxBitrateKbps);
      EXPECT_EQ(1, num_encoder_initializations_);

      encoder_config_.max_bitrate_bps = kIncreasedMaxBitrateKbps * 1000;
      SendTask(RTC_FROM_HERE, task_queue_, [&]() {
        send_stream_->ReconfigureVideoEncoder(encoder_config_.Copy());
      });
      ASSERT_TRUE(create_rate_allocator_event_.Wait(
          VideoSendStreamTest::kDefaultTimeoutMs));
      EXPECT_EQ(3, num_rate_allocator_creations_)
          << "Rate allocator should have been recreated.";

      // Expected target bitrate is the start bitrate set in the call to
      // call_->GetTransportControllerSend()->SetSdpBitrateParameters.
      WaitForSetRates(kIncreasedStartBitrateKbps);
      EXPECT_EQ(1, num_encoder_initializations_);
    }

    TaskQueueBase* const task_queue_;
    rtc::Event create_rate_allocator_event_;
    rtc::Event init_encode_event_;
    rtc::Event bitrate_changed_event_;
    Mutex mutex_;
    uint32_t target_bitrate_ RTC_GUARDED_BY(&mutex_);

    int num_rate_allocator_creations_;
    int num_encoder_initializations_;
    webrtc::Call* call_;
    webrtc::VideoSendStream* send_stream_;
    test::VideoEncoderProxyFactory encoder_factory_;
    std::unique_ptr<VideoBitrateAllocatorFactory> bitrate_allocator_factory_;
    webrtc::VideoEncoderConfig encoder_config_;
  } test(task_queue());

  RunBaseTest(&test);
}

TEST_F(VideoSendStreamTest, ReportsSentResolution) {
  static const size_t kNumStreams = 3;
  // Unusual resolutions to make sure that they are the ones being reported.
  static const struct {
    int width;
    int height;
  } kEncodedResolution[kNumStreams] = {{241, 181}, {300, 121}, {121, 221}};
  class ScreencastTargetBitrateTest : public test::SendTest,
                                      public test::FakeEncoder {
   public:
    explicit ScreencastTargetBitrateTest(TaskQueueBase* task_queue)
        : SendTest(kDefaultTimeoutMs),
          test::FakeEncoder(Clock::GetRealTimeClock()),
          send_stream_(nullptr),
          encoder_factory_(this),
          task_queue_(task_queue) {}

   private:
    int32_t Encode(const VideoFrame& input_image,
                   const std::vector<VideoFrameType>* frame_types) override {
      CodecSpecificInfo specifics;
      specifics.codecType = kVideoCodecGeneric;

      EncodedImage encoded;
      auto buffer = EncodedImageBuffer::Create(16);
      memset(buffer->data(), 0, 16);
      encoded.SetEncodedData(buffer);
      encoded.SetTimestamp(input_image.timestamp());
      encoded.capture_time_ms_ = input_image.render_time_ms();

      for (size_t i = 0; i < kNumStreams; ++i) {
        encoded._frameType = (*frame_types)[i];
        encoded._encodedWidth = kEncodedResolution[i].width;
        encoded._encodedHeight = kEncodedResolution[i].height;
        encoded.SetSpatialIndex(i);
        EncodedImageCallback* callback;
        {
          MutexLock lock(&mutex_);
          callback = callback_;
        }
        RTC_DCHECK(callback);
        if (callback->OnEncodedImage(encoded, &specifics).error !=
            EncodedImageCallback::Result::OK) {
          return -1;
        }
      }

      observation_complete_.Set();
      return 0;
    }
    void ModifyVideoConfigs(
        VideoSendStream::Config* send_config,
        std::vector<VideoReceiveStream::Config>* receive_configs,
        VideoEncoderConfig* encoder_config) override {
      send_config->encoder_settings.encoder_factory = &encoder_factory_;
      EXPECT_EQ(kNumStreams, encoder_config->number_of_streams);
    }

    size_t GetNumVideoStreams() const override { return kNumStreams; }

    void PerformTest() override {
      EXPECT_TRUE(Wait())
          << "Timed out while waiting for the encoder to send one frame.";
      VideoSendStream::Stats stats;
      SendTask(RTC_FROM_HERE, task_queue_,
               [&]() { stats = send_stream_->GetStats(); });

      for (size_t i = 0; i < kNumStreams; ++i) {
        ASSERT_TRUE(stats.substreams.find(kVideoSendSsrcs[i]) !=
                    stats.substreams.end())
            << "No stats for SSRC: " << kVideoSendSsrcs[i]
            << ", stats should exist as soon as frames have been encoded.";
        VideoSendStream::StreamStats ssrc_stats =
            stats.substreams[kVideoSendSsrcs[i]];
        EXPECT_EQ(kEncodedResolution[i].width, ssrc_stats.width);
        EXPECT_EQ(kEncodedResolution[i].height, ssrc_stats.height);
      }
    }

    void OnVideoStreamsCreated(
        VideoSendStream* send_stream,
        const std::vector<VideoReceiveStream*>& receive_streams) override {
      send_stream_ = send_stream;
    }

    VideoSendStream* send_stream_;
    test::VideoEncoderProxyFactory encoder_factory_;
    TaskQueueBase* const task_queue_;
  } test(task_queue());

  RunBaseTest(&test);
}

#if defined(RTC_ENABLE_VP9)
class Vp9HeaderObserver : public test::SendTest {
 public:
  Vp9HeaderObserver()
      : SendTest(VideoSendStreamTest::kLongTimeoutMs),
        encoder_factory_([]() { return VP9Encoder::Create(); }),
        vp9_settings_(VideoEncoder::GetDefaultVp9Settings()),
        packets_sent_(0),
        frames_sent_(0),
        expected_width_(0),
        expected_height_(0) {}

  virtual void ModifyVideoConfigsHook(
      VideoSendStream::Config* send_config,
      std::vector<VideoReceiveStream::Config>* receive_configs,
      VideoEncoderConfig* encoder_config) {}

  virtual void InspectHeader(const RTPVideoHeaderVP9& vp9) = 0;

 private:
  const int kVp9PayloadType = test::CallTest::kVideoSendPayloadType;

  void ModifyVideoConfigs(
      VideoSendStream::Config* send_config,
      std::vector<VideoReceiveStream::Config>* receive_configs,
      VideoEncoderConfig* encoder_config) override {
    send_config->encoder_settings.encoder_factory = &encoder_factory_;
    send_config->rtp.payload_name = "VP9";
    send_config->rtp.payload_type = kVp9PayloadType;
    ModifyVideoConfigsHook(send_config, receive_configs, encoder_config);
    encoder_config->encoder_specific_settings =
        rtc::make_ref_counted<VideoEncoderConfig::Vp9EncoderSpecificSettings>(
            vp9_settings_);
    EXPECT_EQ(1u, encoder_config->number_of_streams);
    EXPECT_EQ(1u, encoder_config->simulcast_layers.size());
    encoder_config->simulcast_layers[0].num_temporal_layers =
        vp9_settings_.numberOfTemporalLayers;
    encoder_config_ = encoder_config->Copy();
  }

  void ModifyVideoCaptureStartResolution(int* width,
                                         int* height,
                                         int* frame_rate) override {
    expected_width_ = *width;
    expected_height_ = *height;
  }

  void PerformTest() override {
    bool wait = Wait();
    {
      // In case of time out, OnSendRtp might still access frames_sent_;
      MutexLock lock(&mutex_);
      EXPECT_TRUE(wait) << "Test timed out waiting for VP9 packet, num frames "
                        << frames_sent_;
    }
  }

  Action OnSendRtp(const uint8_t* packet, size_t length) override {
    RtpPacket rtp_packet;
    EXPECT_TRUE(rtp_packet.Parse(packet, length));

    EXPECT_EQ(kVp9PayloadType, rtp_packet.PayloadType());
    rtc::ArrayView<const uint8_t> rtp_payload = rtp_packet.payload();

    bool new_packet = packets_sent_ == 0 ||
                      IsNewerSequenceNumber(rtp_packet.SequenceNumber(),
                                            last_packet_sequence_number_);
    if (!rtp_payload.empty() && new_packet) {
      RTPVideoHeader video_header;
      EXPECT_NE(
          VideoRtpDepacketizerVp9::ParseRtpPayload(rtp_payload, &video_header),
          0);
      EXPECT_EQ(VideoCodecType::kVideoCodecVP9, video_header.codec);
      // Verify common fields for all configurations.
      const auto& vp9_header =
          absl::get<RTPVideoHeaderVP9>(video_header.video_type_header);
      VerifyCommonHeader(vp9_header);
      CompareConsecutiveFrames(rtp_packet, video_header);
      // Verify configuration specific settings.
      InspectHeader(vp9_header);

      ++packets_sent_;
      if (rtp_packet.Marker()) {
        MutexLock lock(&mutex_);
        ++frames_sent_;
      }
      last_packet_marker_ = rtp_packet.Marker();
      last_packet_sequence_number_ = rtp_packet.SequenceNumber();
      last_packet_timestamp_ = rtp_packet.Timestamp();
      last_vp9_ = vp9_header;
    }
    return SEND_PACKET;
  }

 protected:
  bool ContinuousPictureId(const RTPVideoHeaderVP9& vp9) const {
    if (last_vp9_.picture_id > vp9.picture_id) {
      return vp9.picture_id == 0;  // Wrap.
    } else {
      return vp9.picture_id == last_vp9_.picture_id + 1;
    }
  }

  void VerifySpatialIdxWithinFrame(const RTPVideoHeaderVP9& vp9) const {
    bool new_layer = vp9.spatial_idx != last_vp9_.spatial_idx;
    EXPECT_EQ(new_layer, vp9.beginning_of_frame);
    EXPECT_EQ(new_layer, last_vp9_.end_of_frame);
    EXPECT_EQ(new_layer ? last_vp9_.spatial_idx + 1 : last_vp9_.spatial_idx,
              vp9.spatial_idx);
  }

  void VerifyFixedTemporalLayerStructure(const RTPVideoHeaderVP9& vp9,
                                         uint8_t num_layers) const {
    switch (num_layers) {
      case 0:
        VerifyTemporalLayerStructure0(vp9);
        break;
      case 1:
        VerifyTemporalLayerStructure1(vp9);
        break;
      case 2:
        VerifyTemporalLayerStructure2(vp9);
        break;
      case 3:
        VerifyTemporalLayerStructure3(vp9);
        break;
      default:
        RTC_NOTREACHED();
    }
  }

  void VerifyTemporalLayerStructure0(const RTPVideoHeaderVP9& vp9) const {
    EXPECT_EQ(kNoTl0PicIdx, vp9.tl0_pic_idx);
    EXPECT_EQ(kNoTemporalIdx, vp9.temporal_idx);  // no tid
    EXPECT_FALSE(vp9.temporal_up_switch);
  }

  void VerifyTemporalLayerStructure1(const RTPVideoHeaderVP9& vp9) const {
    EXPECT_NE(kNoTl0PicIdx, vp9.tl0_pic_idx);
    EXPECT_EQ(0, vp9.temporal_idx);  // 0,0,0,...
    EXPECT_FALSE(vp9.temporal_up_switch);
  }

  void VerifyTemporalLayerStructure2(const RTPVideoHeaderVP9& vp9) const {
    EXPECT_NE(kNoTl0PicIdx, vp9.tl0_pic_idx);
    EXPECT_GE(vp9.temporal_idx, 0);  // 0,1,0,1,... (tid reset on I-frames).
    EXPECT_LE(vp9.temporal_idx, 1);
    EXPECT_EQ(vp9.temporal_idx > 0, vp9.temporal_up_switch);
    if (IsNewPictureId(vp9)) {
      uint8_t expected_tid =
          (!vp9.inter_pic_predicted || last_vp9_.temporal_idx == 1) ? 0 : 1;
      EXPECT_EQ(expected_tid, vp9.temporal_idx);
    }
  }

  void VerifyTemporalLayerStructure3(const RTPVideoHeaderVP9& vp9) const {
    EXPECT_NE(kNoTl0PicIdx, vp9.tl0_pic_idx);
    EXPECT_GE(vp9.temporal_idx, 0);  // 0,2,1,2,... (tid reset on I-frames).
    EXPECT_LE(vp9.temporal_idx, 2);
    if (IsNewPictureId(vp9) && vp9.inter_pic_predicted) {
      EXPECT_NE(vp9.temporal_idx, last_vp9_.temporal_idx);
      switch (vp9.temporal_idx) {
        case 0:
          EXPECT_EQ(2, last_vp9_.temporal_idx);
          EXPECT_FALSE(vp9.temporal_up_switch);
          break;
        case 1:
          EXPECT_EQ(2, last_vp9_.temporal_idx);
          EXPECT_TRUE(vp9.temporal_up_switch);
          break;
        case 2:
          EXPECT_LT(last_vp9_.temporal_idx, 2);
          EXPECT_TRUE(vp9.temporal_up_switch);
          break;
      }
    }
  }

  void VerifyTl0Idx(const RTPVideoHeaderVP9& vp9) const {
    if (vp9.tl0_pic_idx == kNoTl0PicIdx)
      return;

    uint8_t expected_tl0_idx = last_vp9_.tl0_pic_idx;
    if (vp9.temporal_idx == 0)
      ++expected_tl0_idx;
    EXPECT_EQ(expected_tl0_idx, vp9.tl0_pic_idx);
  }

  bool IsNewPictureId(const RTPVideoHeaderVP9& vp9) const {
    return frames_sent_ > 0 && (vp9.picture_id != last_vp9_.picture_id);
  }

  // Flexible mode (F=1):    Non-flexible mode (F=0):
  //
  //      +-+-+-+-+-+-+-+-+     +-+-+-+-+-+-+-+-+
  //      |I|P|L|F|B|E|V|-|     |I|P|L|F|B|E|V|-|
  //      +-+-+-+-+-+-+-+-+     +-+-+-+-+-+-+-+-+
  // I:   |M| PICTURE ID  |  I: |M| PICTURE ID  |
  //      +-+-+-+-+-+-+-+-+     +-+-+-+-+-+-+-+-+
  // M:   | EXTENDED PID  |  M: | EXTENDED PID  |
  //      +-+-+-+-+-+-+-+-+     +-+-+-+-+-+-+-+-+
  // L:   |  T  |U|  S  |D|  L: |  T  |U|  S  |D|
  //      +-+-+-+-+-+-+-+-+     +-+-+-+-+-+-+-+-+
  // P,F: | P_DIFF    |X|N|     |   TL0PICIDX   |
  //      +-+-+-+-+-+-+-+-+     +-+-+-+-+-+-+-+-+
  // X:   |EXTENDED P_DIFF|  V: | SS  ..        |
  //      +-+-+-+-+-+-+-+-+     +-+-+-+-+-+-+-+-+
  // V:   | SS  ..        |
  //      +-+-+-+-+-+-+-+-+
  void VerifyCommonHeader(const RTPVideoHeaderVP9& vp9) const {
    EXPECT_EQ(kMaxTwoBytePictureId, vp9.max_picture_id);       // M:1
    EXPECT_NE(kNoPictureId, vp9.picture_id);                   // I:1
    EXPECT_EQ(vp9_settings_.flexibleMode, vp9.flexible_mode);  // F

    if (vp9_settings_.numberOfSpatialLayers > 1) {
      EXPECT_LT(vp9.spatial_idx, vp9_settings_.numberOfSpatialLayers);
    } else if (vp9_settings_.numberOfTemporalLayers > 1) {
      EXPECT_EQ(vp9.spatial_idx, 0);
    } else {
      EXPECT_EQ(vp9.spatial_idx, kNoSpatialIdx);
    }

    if (vp9_settings_.numberOfTemporalLayers > 1) {
      EXPECT_LT(vp9.temporal_idx, vp9_settings_.numberOfTemporalLayers);
    } else if (vp9_settings_.numberOfSpatialLayers > 1) {
      EXPECT_EQ(vp9.temporal_idx, 0);
    } else {
      EXPECT_EQ(vp9.temporal_idx, kNoTemporalIdx);
    }

    if (vp9.ss_data_available)  // V
      VerifySsData(vp9);

    if (frames_sent_ == 0)
      EXPECT_FALSE(vp9.inter_pic_predicted);  // P

    if (!vp9.inter_pic_predicted) {
      EXPECT_TRUE(vp9.temporal_idx == 0 || vp9.temporal_idx == kNoTemporalIdx);
      EXPECT_FALSE(vp9.temporal_up_switch);
    }
  }

  // Scalability structure (SS).
  //
  //      +-+-+-+-+-+-+-+-+
  // V:   | N_S |Y|G|-|-|-|
  //      +-+-+-+-+-+-+-+-+
  // Y:   |    WIDTH      |  N_S + 1 times
  //      +-+-+-+-+-+-+-+-+
  //      |    HEIGHT     |
  //      +-+-+-+-+-+-+-+-+
  // G:   |      N_G      |
  //      +-+-+-+-+-+-+-+-+
  // N_G: |  T  |U| R |-|-|  N_G times
  //      +-+-+-+-+-+-+-+-+
  //      |    P_DIFF     |  R times
  //      +-+-+-+-+-+-+-+-+
  void VerifySsData(const RTPVideoHeaderVP9& vp9) const {
    EXPECT_TRUE(vp9.ss_data_available);             // V
    EXPECT_EQ(vp9_settings_.numberOfSpatialLayers,  // N_S + 1
              vp9.num_spatial_layers);
    EXPECT_TRUE(vp9.spatial_layer_resolution_present);  // Y:1
    int expected_width = expected_width_;
    int expected_height = expected_height_;
    for (int i = static_cast<int>(vp9.num_spatial_layers) - 1; i >= 0; --i) {
      EXPECT_EQ(expected_width, vp9.width[i]);    // WIDTH
      EXPECT_EQ(expected_height, vp9.height[i]);  // HEIGHT
      expected_width /= 2;
      expected_height /= 2;
    }
  }

  void CompareConsecutiveFrames(const RtpPacket& rtp_packet,
                                const RTPVideoHeader& video) const {
    const auto& vp9_header =
        absl::get<RTPVideoHeaderVP9>(video.video_type_header);

    bool new_frame =
        packets_sent_ == 0 ||
        IsNewerTimestamp(rtp_packet.Timestamp(), last_packet_timestamp_);
    EXPECT_EQ(new_frame, video.is_first_packet_in_frame);
    if (!new_frame) {
      EXPECT_FALSE(last_packet_marker_);
      EXPECT_EQ(last_packet_timestamp_, rtp_packet.Timestamp());
      EXPECT_EQ(last_vp9_.picture_id, vp9_header.picture_id);
      EXPECT_EQ(last_vp9_.temporal_idx, vp9_header.temporal_idx);
      EXPECT_EQ(last_vp9_.tl0_pic_idx, vp9_header.tl0_pic_idx);
      VerifySpatialIdxWithinFrame(vp9_header);
      return;
    }
    // New frame.
    EXPECT_TRUE(vp9_header.beginning_of_frame);

    // Compare with last packet in previous frame.
    if (frames_sent_ == 0)
      return;
    EXPECT_TRUE(last_vp9_.end_of_frame);
    EXPECT_TRUE(last_packet_marker_);
    EXPECT_TRUE(ContinuousPictureId(vp9_header));
    VerifyTl0Idx(vp9_header);
  }

  test::FunctionVideoEncoderFactory encoder_factory_;
  VideoCodecVP9 vp9_settings_;
  webrtc::VideoEncoderConfig encoder_config_;
  bool last_packet_marker_ = false;
  uint16_t last_packet_sequence_number_ = 0;
  uint32_t last_packet_timestamp_ = 0;
  RTPVideoHeaderVP9 last_vp9_;
  size_t packets_sent_;
  Mutex mutex_;
  size_t frames_sent_;
  int expected_width_;
  int expected_height_;
};

TEST_F(VideoSendStreamTest, Vp9NonFlexMode_1Tl1SLayers) {
  const uint8_t kNumTemporalLayers = 1;
  const uint8_t kNumSpatialLayers = 1;
  TestVp9NonFlexMode(kNumTemporalLayers, kNumSpatialLayers);
}

TEST_F(VideoSendStreamTest, Vp9NonFlexMode_2Tl1SLayers) {
  const uint8_t kNumTemporalLayers = 2;
  const uint8_t kNumSpatialLayers = 1;
  TestVp9NonFlexMode(kNumTemporalLayers, kNumSpatialLayers);
}

TEST_F(VideoSendStreamTest, Vp9NonFlexMode_3Tl1SLayers) {
  const uint8_t kNumTemporalLayers = 3;
  const uint8_t kNumSpatialLayers = 1;
  TestVp9NonFlexMode(kNumTemporalLayers, kNumSpatialLayers);
}

TEST_F(VideoSendStreamTest, Vp9NonFlexMode_1Tl2SLayers) {
  const uint8_t kNumTemporalLayers = 1;
  const uint8_t kNumSpatialLayers = 2;
  TestVp9NonFlexMode(kNumTemporalLayers, kNumSpatialLayers);
}

TEST_F(VideoSendStreamTest, Vp9NonFlexMode_2Tl2SLayers) {
  const uint8_t kNumTemporalLayers = 2;
  const uint8_t kNumSpatialLayers = 2;
  TestVp9NonFlexMode(kNumTemporalLayers, kNumSpatialLayers);
}

TEST_F(VideoSendStreamTest, Vp9NonFlexMode_3Tl2SLayers) {
  const uint8_t kNumTemporalLayers = 3;
  const uint8_t kNumSpatialLayers = 2;
  TestVp9NonFlexMode(kNumTemporalLayers, kNumSpatialLayers);
}

void VideoSendStreamTest::TestVp9NonFlexMode(uint8_t num_temporal_layers,
                                             uint8_t num_spatial_layers) {
  static const size_t kNumFramesToSend = 100;
  // Set to < kNumFramesToSend and coprime to length of temporal layer
  // structures to verify temporal id reset on key frame.
  static const int kKeyFrameInterval = 31;

  static const int kWidth = kMinVp9SpatialLayerWidth;
  static const int kHeight = kMinVp9SpatialLayerHeight;
  static const float kGoodBitsPerPixel = 0.1f;
  class NonFlexibleMode : public Vp9HeaderObserver {
   public:
    NonFlexibleMode(uint8_t num_temporal_layers, uint8_t num_spatial_layers)
        : num_temporal_layers_(num_temporal_layers),
          num_spatial_layers_(num_spatial_layers),
          l_field_(num_temporal_layers > 1 || num_spatial_layers > 1) {}

    void ModifyVideoConfigsHook(
        VideoSendStream::Config* send_config,
        std::vector<VideoReceiveStream::Config>* receive_configs,
        VideoEncoderConfig* encoder_config) override {
      encoder_config->codec_type = kVideoCodecVP9;
      int bitrate_bps = 0;
      for (int sl_idx = 0; sl_idx < num_spatial_layers_; ++sl_idx) {
        const int width = kWidth << sl_idx;
        const int height = kHeight << sl_idx;
        const float bpp = kGoodBitsPerPixel / (1 << sl_idx);
        bitrate_bps += static_cast<int>(width * height * bpp * 30);
      }
      encoder_config->max_bitrate_bps = bitrate_bps * 2;

      vp9_settings_.flexibleMode = false;
      vp9_settings_.frameDroppingOn = false;
      vp9_settings_.automaticResizeOn = false;
      vp9_settings_.keyFrameInterval = kKeyFrameInterval;
      vp9_settings_.numberOfTemporalLayers = num_temporal_layers_;
      vp9_settings_.numberOfSpatialLayers = num_spatial_layers_;
    }

    void ModifyVideoCaptureStartResolution(int* width,
                                           int* height,
                                           int* frame_rate) override {
      expected_width_ = kWidth << (num_spatial_layers_ - 1);
      expected_height_ = kHeight << (num_spatial_layers_ - 1);
      *width = expected_width_;
      *height = expected_height_;
    }

    void InspectHeader(const RTPVideoHeaderVP9& vp9) override {
      bool ss_data_expected =
          !vp9.inter_pic_predicted && vp9.beginning_of_frame &&
          (vp9.spatial_idx == 0 || vp9.spatial_idx == kNoSpatialIdx);
      EXPECT_EQ(ss_data_expected, vp9.ss_data_available);
      if (num_spatial_layers_ > 1) {
        EXPECT_EQ(vp9.spatial_idx > 0, vp9.inter_layer_predicted);
      } else {
        EXPECT_FALSE(vp9.inter_layer_predicted);
      }

      EXPECT_EQ(!vp9.inter_pic_predicted,
                frames_sent_ % kKeyFrameInterval == 0);

      if (IsNewPictureId(vp9)) {
        if (num_temporal_layers_ == 1 && num_spatial_layers_ == 1) {
          EXPECT_EQ(kNoSpatialIdx, vp9.spatial_idx);
        } else {
          EXPECT_EQ(0, vp9.spatial_idx);
        }
        if (num_spatial_layers_ > 1)
          EXPECT_EQ(num_spatial_layers_ - 1, last_vp9_.spatial_idx);
      }

      VerifyFixedTemporalLayerStructure(vp9,
                                        l_field_ ? num_temporal_layers_ : 0);

      if (frames_sent_ > kNumFramesToSend)
        observation_complete_.Set();
    }
    const uint8_t num_temporal_layers_;
    const uint8_t num_spatial_layers_;
    const bool l_field_;

   private:
    void ModifySenderBitrateConfig(
        BitrateConstraints* bitrate_config) override {
      const int kMinBitrateBps = 300000;
      bitrate_config->min_bitrate_bps = kMinBitrateBps;
    }
  } test(num_temporal_layers, num_spatial_layers);

  RunBaseTest(&test);
}

TEST_F(VideoSendStreamTest, Vp9NonFlexModeSmallResolution) {
  static const size_t kNumFramesToSend = 50;
  static const int kWidth = 4;
  static const int kHeight = 4;
  class NonFlexibleModeResolution : public Vp9HeaderObserver {
    void ModifyVideoConfigsHook(
        VideoSendStream::Config* send_config,
        std::vector<VideoReceiveStream::Config>* receive_configs,
        VideoEncoderConfig* encoder_config) override {
      encoder_config->codec_type = kVideoCodecVP9;
      vp9_settings_.flexibleMode = false;
      vp9_settings_.numberOfTemporalLayers = 1;
      vp9_settings_.numberOfSpatialLayers = 1;

      EXPECT_EQ(1u, encoder_config->number_of_streams);
    }

    void InspectHeader(const RTPVideoHeaderVP9& vp9_header) override {
      if (frames_sent_ > kNumFramesToSend)
        observation_complete_.Set();
    }

    void ModifyVideoCaptureStartResolution(int* width,
                                           int* height,
                                           int* frame_rate) override {
      expected_width_ = kWidth;
      expected_height_ = kHeight;
      *width = kWidth;
      *height = kHeight;
    }
  } test;

  RunBaseTest(&test);
}

#if defined(WEBRTC_ANDROID)
// Crashes on Android; bugs.webrtc.org/7401
#define MAYBE_Vp9FlexModeRefCount DISABLED_Vp9FlexModeRefCount
#else
// TODO(webrtc:9270): Support of flexible mode is temporarily disabled. Enable
// the test after webrtc:9270 is implemented.
#define MAYBE_Vp9FlexModeRefCount DISABLED_Vp9FlexModeRefCount
// #define MAYBE_Vp9FlexModeRefCount Vp9FlexModeRefCount
#endif
TEST_F(VideoSendStreamTest, MAYBE_Vp9FlexModeRefCount) {
  class FlexibleMode : public Vp9HeaderObserver {
    void ModifyVideoConfigsHook(
        VideoSendStream::Config* send_config,
        std::vector<VideoReceiveStream::Config>* receive_configs,
        VideoEncoderConfig* encoder_config) override {
      encoder_config->codec_type = kVideoCodecVP9;
      encoder_config->content_type = VideoEncoderConfig::ContentType::kScreen;
      vp9_settings_.flexibleMode = true;
      vp9_settings_.numberOfTemporalLayers = 1;
      vp9_settings_.numberOfSpatialLayers = 2;
    }

    void InspectHeader(const RTPVideoHeaderVP9& vp9_header) override {
      EXPECT_TRUE(vp9_header.flexible_mode);
      EXPECT_EQ(kNoTl0PicIdx, vp9_header.tl0_pic_idx);
      if (vp9_header.inter_pic_predicted) {
        EXPECT_GT(vp9_header.num_ref_pics, 0u);
        observation_complete_.Set();
      }
    }
  } test;

  RunBaseTest(&test);
}
#endif  // defined(RTC_ENABLE_VP9)

void VideoSendStreamTest::TestRequestSourceRotateVideo(
    bool support_orientation_ext) {
  CreateSenderCall();

  test::NullTransport transport;
  CreateSendConfig(1, 0, 0, &transport);
  GetVideoSendConfig()->rtp.extensions.clear();
  if (support_orientation_ext) {
    GetVideoSendConfig()->rtp.extensions.push_back(
        RtpExtension(RtpExtension::kVideoRotationUri, 1));
  }

  CreateVideoStreams();
  test::FrameForwarder forwarder;
  GetVideoSendStream()->SetSource(&forwarder,
                                  DegradationPreference::MAINTAIN_FRAMERATE);

  EXPECT_TRUE(forwarder.sink_wants().rotation_applied !=
              support_orientation_ext);

  DestroyStreams();
}

TEST_F(VideoSendStreamTest,
       RequestSourceRotateIfVideoOrientationExtensionNotSupported) {
  TestRequestSourceRotateVideo(false);
}

TEST_F(VideoSendStreamTest,
       DoNotRequestsRotationIfVideoOrientationExtensionSupported) {
  TestRequestSourceRotateVideo(true);
}

TEST_F(VideoSendStreamTest, EncoderConfigMaxFramerateReportedToSource) {
  static const int kMaxFps = 22;
  class FpsObserver : public test::SendTest,
                      public test::FrameGeneratorCapturer::SinkWantsObserver {
   public:
    FpsObserver() : SendTest(kDefaultTimeoutMs) {}

    void OnFrameGeneratorCapturerCreated(
        test::FrameGeneratorCapturer* frame_generator_capturer) override {
      frame_generator_capturer->SetSinkWantsObserver(this);
    }

    void OnSinkWantsChanged(rtc::VideoSinkInterface<VideoFrame>* sink,
                            const rtc::VideoSinkWants& wants) override {
      if (wants.max_framerate_fps == kMaxFps)
        observation_complete_.Set();
    }

    void ModifyVideoConfigs(
        VideoSendStream::Config* send_config,
        std::vector<VideoReceiveStream::Config>* receive_configs,
        VideoEncoderConfig* encoder_config) override {
      encoder_config->simulcast_layers[0].max_framerate = kMaxFps;
    }

    void PerformTest() override {
      EXPECT_TRUE(Wait()) << "Timed out while waiting for fps to be reported.";
    }
  } test;

  RunBaseTest(&test);
}

// This test verifies that overhead is removed from the bandwidth estimate by
// testing that the maximum possible target payload rate is smaller than the
// maximum bandwidth estimate by the overhead rate.
TEST_F(VideoSendStreamTest, RemoveOverheadFromBandwidth) {
  test::ScopedFieldTrials override_field_trials(
      "WebRTC-SendSideBwe-WithOverhead/Enabled/");
  class RemoveOverheadFromBandwidthTest : public test::EndToEndTest,
                                          public test::FakeEncoder {
   public:
    explicit RemoveOverheadFromBandwidthTest(TaskQueueBase* task_queue)
        : EndToEndTest(test::CallTest::kDefaultTimeoutMs),
          FakeEncoder(Clock::GetRealTimeClock()),
          task_queue_(task_queue),
          encoder_factory_(this),
          call_(nullptr),
          max_bitrate_bps_(0),
          first_packet_sent_(false) {}

    void SetRates(const RateControlParameters& parameters) override {
      MutexLock lock(&mutex_);
      // Wait for the first sent packet so that videosendstream knows
      // rtp_overhead.
      if (first_packet_sent_) {
        max_bitrate_bps_ = parameters.bitrate.get_sum_bps();
        bitrate_changed_event_.Set();
      }
      return FakeEncoder::SetRates(parameters);
    }

    void OnCallsCreated(Call* sender_call, Call* receiver_call) override {
      call_ = sender_call;
    }

    void ModifyVideoConfigs(
        VideoSendStream::Config* send_config,
        std::vector<VideoReceiveStream::Config>* receive_configs,
        VideoEncoderConfig* encoder_config) override {
      send_config->rtp.max_packet_size = 1200;
      send_config->encoder_settings.encoder_factory = &encoder_factory_;
      EXPECT_FALSE(send_config->rtp.extensions.empty());
    }

    Action OnSendRtp(const uint8_t* packet, size_t length) override {
      MutexLock lock(&mutex_);
      first_packet_sent_ = true;
      return SEND_PACKET;
    }

    void PerformTest() override {
      BitrateConstraints bitrate_config;
      constexpr int kStartBitrateBps = 60000;
      constexpr int kMaxBitrateBps = 60000;
      constexpr int kMinBitrateBps = 10000;
      bitrate_config.start_bitrate_bps = kStartBitrateBps;
      bitrate_config.max_bitrate_bps = kMaxBitrateBps;
      bitrate_config.min_bitrate_bps = kMinBitrateBps;
      SendTask(RTC_FROM_HERE, task_queue_, [this, &bitrate_config]() {
        call_->GetTransportControllerSend()->SetSdpBitrateParameters(
            bitrate_config);
        call_->GetTransportControllerSend()->OnTransportOverheadChanged(40);
      });

      // At a bitrate of 60kbps with a packet size of 1200B video and an
      // overhead of 40B per packet video produces 2240bps overhead.
      // So the encoder BW should be set to 57760bps.
      EXPECT_TRUE(
          bitrate_changed_event_.Wait(VideoSendStreamTest::kDefaultTimeoutMs));
      {
        MutexLock lock(&mutex_);
        EXPECT_LE(max_bitrate_bps_, 57760u);
      }
    }

   private:
    TaskQueueBase* const task_queue_;
    test::VideoEncoderProxyFactory encoder_factory_;
    Call* call_;
    Mutex mutex_;
    uint32_t max_bitrate_bps_ RTC_GUARDED_BY(&mutex_);
    bool first_packet_sent_ RTC_GUARDED_BY(&mutex_);
    rtc::Event bitrate_changed_event_;
  } test(task_queue());
  RunBaseTest(&test);
}

class PacingFactorObserver : public test::SendTest {
 public:
  PacingFactorObserver(bool configure_send_side,
                       absl::optional<float> expected_pacing_factor)
      : test::SendTest(VideoSendStreamTest::kDefaultTimeoutMs),
        configure_send_side_(configure_send_side),
        expected_pacing_factor_(expected_pacing_factor) {}

  void ModifyVideoConfigs(
      VideoSendStream::Config* send_config,
      std::vector<VideoReceiveStream::Config>* receive_configs,
      VideoEncoderConfig* encoder_config) override {
    // Check if send-side bwe extension is already present, and remove it if
    // it is not desired.
    bool has_send_side = false;
    for (auto it = send_config->rtp.extensions.begin();
         it != send_config->rtp.extensions.end(); ++it) {
      if (it->uri == RtpExtension::kTransportSequenceNumberUri) {
        if (configure_send_side_) {
          has_send_side = true;
        } else {
          send_config->rtp.extensions.erase(it);
        }
        break;
      }
    }

    if (configure_send_side_ && !has_send_side) {
      rtc::UniqueNumberGenerator<int> unique_id_generator;
      unique_id_generator.AddKnownId(0);  // First valid RTP extension ID is 1.
      for (const RtpExtension& extension : send_config->rtp.extensions) {
        unique_id_generator.AddKnownId(extension.id);
      }
      // Want send side, not present by default, so add it.
      send_config->rtp.extensions.emplace_back(
          RtpExtension::kTransportSequenceNumberUri, unique_id_generator());
    }

    // ALR only enabled for screenshare.
    encoder_config->content_type = VideoEncoderConfig::ContentType::kScreen;
  }

  void OnVideoStreamsCreated(
      VideoSendStream* send_stream,
      const std::vector<VideoReceiveStream*>& receive_streams) override {
    auto internal_send_peer = test::VideoSendStreamPeer(send_stream);
    // Video streams created, check that pacing factor is correctly configured.
    EXPECT_EQ(expected_pacing_factor_,
              internal_send_peer.GetPacingFactorOverride());
    observation_complete_.Set();
  }

  void PerformTest() override {
    EXPECT_TRUE(Wait()) << "Timed out while waiting for stream creation.";
  }

 private:
  const bool configure_send_side_;
  const absl::optional<float> expected_pacing_factor_;
};

std::string GetAlrProbingExperimentString() {
  return std::string(
             AlrExperimentSettings::kScreenshareProbingBweExperimentName) +
         "/1.0,2875,80,40,-60,3/";
}
const float kAlrProbingExperimentPaceMultiplier = 1.0f;

TEST_F(VideoSendStreamTest, AlrConfiguredWhenSendSideOn) {
  test::ScopedFieldTrials alr_experiment(GetAlrProbingExperimentString());
  // Send-side bwe on, use pacing factor from |kAlrProbingExperiment| above.
  PacingFactorObserver test_with_send_side(true,
                                           kAlrProbingExperimentPaceMultiplier);
  RunBaseTest(&test_with_send_side);
}

TEST_F(VideoSendStreamTest, AlrNotConfiguredWhenSendSideOff) {
  test::ScopedFieldTrials alr_experiment(GetAlrProbingExperimentString());
  // Send-side bwe off, use configuration should not be overridden.
  PacingFactorObserver test_without_send_side(false, absl::nullopt);
  RunBaseTest(&test_without_send_side);
}

// Test class takes as argument a function pointer to reset the send
// stream and call OnVideoStreamsCreated. This is necessary since you cannot
// change the content type of a VideoSendStream, you need to recreate it.
// Stopping and recreating the stream can only be done on the main thread and in
// the context of VideoSendStreamTest (not BaseTest). The test switches from
// realtime to screenshare and back.
template <typename T>
class ContentSwitchTest : public test::SendTest {
 public:
  enum class StreamState {
    kBeforeSwitch = 0,
    kInScreenshare = 1,
    kAfterSwitchBack = 2,
  };
  static const uint32_t kMinPacketsToSend = 50;

  explicit ContentSwitchTest(T* stream_reset_fun, TaskQueueBase* task_queue)
      : SendTest(test::CallTest::kDefaultTimeoutMs),
        call_(nullptr),
        state_(StreamState::kBeforeSwitch),
        send_stream_(nullptr),
        send_stream_config_(nullptr),
        packets_sent_(0),
        stream_resetter_(stream_reset_fun),
        task_queue_(task_queue) {
    RTC_DCHECK(stream_resetter_);
  }

  void OnVideoStreamsCreated(
      VideoSendStream* send_stream,
      const std::vector<VideoReceiveStream*>& receive_streams) override {
    MutexLock lock(&mutex_);
    send_stream_ = send_stream;
  }

  void ModifyVideoConfigs(
      VideoSendStream::Config* send_config,
      std::vector<VideoReceiveStream::Config>* receive_configs,
      VideoEncoderConfig* encoder_config) override {
    RTC_DCHECK_EQ(1, encoder_config->number_of_streams);
    encoder_config->min_transmit_bitrate_bps = 0;
    encoder_config->content_type =
        VideoEncoderConfig::ContentType::kRealtimeVideo;
    send_stream_config_ = send_config->Copy();
    encoder_config_ = encoder_config->Copy();
  }

  void OnCallsCreated(Call* sender_call, Call* receiver_call) override {
    call_ = sender_call;
  }

  Action OnSendRtp(const uint8_t* packet, size_t length) override {
    MutexLock lock(&mutex_);

    auto internal_send_peer = test::VideoSendStreamPeer(send_stream_);
    float pacing_factor =
        internal_send_peer.GetPacingFactorOverride().value_or(0.0f);
    float expected_pacing_factor = 1.1;  // Strict pacing factor.
    VideoSendStream::Stats stats;
    SendTask(RTC_FROM_HERE, task_queue_,
             [&stats, stream = send_stream_]() { stats = stream->GetStats(); });
    if (stats.content_type == webrtc::VideoContentType::SCREENSHARE) {
      expected_pacing_factor = 1.0f;  // Currently used pacing factor in ALR.
    }

    EXPECT_NEAR(expected_pacing_factor, pacing_factor, 1e-6);

    // Wait until at least kMinPacketsToSend packets to be sent, so that
    // some frames would be encoded.
    if (++packets_sent_ < kMinPacketsToSend)
      return SEND_PACKET;

    if (state_ != StreamState::kAfterSwitchBack) {
      // We've sent kMinPacketsToSend packets, switch the content type and move
      // move to the next state.
      // Note that we need to recreate the stream if changing content type.
      packets_sent_ = 0;
      if (encoder_config_.content_type ==
          VideoEncoderConfig::ContentType::kRealtimeVideo) {
        encoder_config_.content_type = VideoEncoderConfig::ContentType::kScreen;
      } else {
        encoder_config_.content_type =
            VideoEncoderConfig::ContentType::kRealtimeVideo;
      }
      switch (state_) {
        case StreamState::kBeforeSwitch:
          state_ = StreamState::kInScreenshare;
          break;
        case StreamState::kInScreenshare:
          state_ = StreamState::kAfterSwitchBack;
          break;
        case StreamState::kAfterSwitchBack:
          RTC_NOTREACHED();
          break;
      }
      content_switch_event_.Set();
      return SEND_PACKET;
    }

    observation_complete_.Set();
    return SEND_PACKET;
  }

  void PerformTest() override {
    while (GetStreamState() != StreamState::kAfterSwitchBack) {
      ASSERT_TRUE(
          content_switch_event_.Wait(test::CallTest::kDefaultTimeoutMs));
      (*stream_resetter_)(send_stream_config_, encoder_config_, this);
    }

    ASSERT_TRUE(Wait())
        << "Timed out waiting for a frame sent after switch back";
  }

 private:
  StreamState GetStreamState() {
    MutexLock lock(&mutex_);
    return state_;
  }

  Mutex mutex_;
  rtc::Event content_switch_event_;
  Call* call_;
  StreamState state_ RTC_GUARDED_BY(mutex_);
  VideoSendStream* send_stream_ RTC_GUARDED_BY(mutex_);
  VideoSendStream::Config send_stream_config_;
  VideoEncoderConfig encoder_config_;
  uint32_t packets_sent_ RTC_GUARDED_BY(mutex_);
  T* stream_resetter_;
  TaskQueueBase* task_queue_;
};

TEST_F(VideoSendStreamTest, SwitchesToScreenshareAndBack) {
  auto reset_fun = [this](const VideoSendStream::Config& send_stream_config,
                          const VideoEncoderConfig& encoder_config,
                          test::BaseTest* test) {
    SendTask(RTC_FROM_HERE, task_queue(),
             [this, &send_stream_config, &encoder_config, &test]() {
               Stop();
               DestroyVideoSendStreams();
               SetVideoSendConfig(send_stream_config);
               SetVideoEncoderConfig(encoder_config);
               CreateVideoSendStreams();
               SetVideoDegradation(DegradationPreference::MAINTAIN_RESOLUTION);
               test->OnVideoStreamsCreated(GetVideoSendStream(),
                                           video_receive_streams_);
               Start();
             });
  };
  ContentSwitchTest<decltype(reset_fun)> test(&reset_fun, task_queue());
  RunBaseTest(&test);
}

}  // namespace webrtc
