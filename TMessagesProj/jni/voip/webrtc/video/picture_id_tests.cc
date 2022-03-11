/*
 *  Copyright (c) 2013 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include <memory>

#include "api/test/simulated_network.h"
#include "api/test/video/function_video_encoder_factory.h"
#include "call/fake_network_pipe.h"
#include "call/simulated_network.h"
#include "media/engine/internal_encoder_factory.h"
#include "media/engine/simulcast_encoder_adapter.h"
#include "modules/rtp_rtcp/source/create_video_rtp_depacketizer.h"
#include "modules/rtp_rtcp/source/rtp_packet.h"
#include "modules/video_coding/codecs/vp8/include/vp8.h"
#include "modules/video_coding/codecs/vp9/include/vp9.h"
#include "rtc_base/numerics/safe_conversions.h"
#include "rtc_base/numerics/sequence_number_util.h"
#include "rtc_base/synchronization/mutex.h"
#include "rtc_base/task_queue_for_test.h"
#include "test/call_test.h"

namespace webrtc {
namespace {
const int kFrameMaxWidth = 1280;
const int kFrameMaxHeight = 720;
const int kFrameRate = 30;
const int kMaxSecondsLost = 5;
const int kMaxFramesLost = kFrameRate * kMaxSecondsLost;
const int kMinPacketsToObserve = 10;
const int kEncoderBitrateBps = 300000;
const uint32_t kPictureIdWraparound = (1 << 15);
const size_t kNumTemporalLayers[] = {1, 2, 3};

}  // namespace

class PictureIdObserver : public test::RtpRtcpObserver {
 public:
  explicit PictureIdObserver(VideoCodecType codec_type)
      : test::RtpRtcpObserver(test::CallTest::kDefaultTimeoutMs),
        depacketizer_(CreateVideoRtpDepacketizer(codec_type)),
        max_expected_picture_id_gap_(0),
        max_expected_tl0_idx_gap_(0),
        num_ssrcs_to_observe_(1) {}

  void SetExpectedSsrcs(size_t num_expected_ssrcs) {
    MutexLock lock(&mutex_);
    num_ssrcs_to_observe_ = num_expected_ssrcs;
  }

  void ResetObservedSsrcs() {
    MutexLock lock(&mutex_);
    // Do not clear the timestamp and picture_id, to ensure that we check
    // consistency between reinits and recreations.
    num_packets_sent_.clear();
    observed_ssrcs_.clear();
  }

  void SetMaxExpectedPictureIdGap(int max_expected_picture_id_gap) {
    MutexLock lock(&mutex_);
    max_expected_picture_id_gap_ = max_expected_picture_id_gap;
    // Expect smaller gap for `tl0_pic_idx` (running index for temporal_idx 0).
    max_expected_tl0_idx_gap_ = max_expected_picture_id_gap_ / 2;
  }

 private:
  struct ParsedPacket {
    uint32_t timestamp;
    uint32_t ssrc;
    int16_t picture_id;
    int16_t tl0_pic_idx;
    uint8_t temporal_idx;
    VideoFrameType frame_type;
  };

  bool ParsePayload(const uint8_t* packet,
                    size_t length,
                    ParsedPacket* parsed) const {
    RtpPacket rtp_packet;
    EXPECT_TRUE(rtp_packet.Parse(packet, length));
    EXPECT_TRUE(rtp_packet.Ssrc() == test::CallTest::kVideoSendSsrcs[0] ||
                rtp_packet.Ssrc() == test::CallTest::kVideoSendSsrcs[1] ||
                rtp_packet.Ssrc() == test::CallTest::kVideoSendSsrcs[2])
        << "Unknown SSRC sent.";

    if (rtp_packet.payload_size() == 0) {
      return false;  // Padding packet.
    }

    parsed->timestamp = rtp_packet.Timestamp();
    parsed->ssrc = rtp_packet.Ssrc();

    absl::optional<VideoRtpDepacketizer::ParsedRtpPayload> parsed_payload =
        depacketizer_->Parse(rtp_packet.PayloadBuffer());
    EXPECT_TRUE(parsed_payload);

    if (const auto* vp8_header = absl::get_if<RTPVideoHeaderVP8>(
            &parsed_payload->video_header.video_type_header)) {
      parsed->picture_id = vp8_header->pictureId;
      parsed->tl0_pic_idx = vp8_header->tl0PicIdx;
      parsed->temporal_idx = vp8_header->temporalIdx;
    } else if (const auto* vp9_header = absl::get_if<RTPVideoHeaderVP9>(
                   &parsed_payload->video_header.video_type_header)) {
      parsed->picture_id = vp9_header->picture_id;
      parsed->tl0_pic_idx = vp9_header->tl0_pic_idx;
      parsed->temporal_idx = vp9_header->temporal_idx;
    } else {
      RTC_DCHECK_NOTREACHED();
    }

    parsed->frame_type = parsed_payload->video_header.frame_type;
    return true;
  }

  // Verify continuity and monotonicity of picture_id sequence.
  void VerifyPictureId(const ParsedPacket& current,
                       const ParsedPacket& last) const
      RTC_EXCLUSIVE_LOCKS_REQUIRED(&mutex_) {
    if (current.timestamp == last.timestamp) {
      EXPECT_EQ(last.picture_id, current.picture_id);
      return;  // Same frame.
    }

    // Packet belongs to a new frame.
    // Picture id should be increasing.
    EXPECT_TRUE((AheadOf<uint16_t, kPictureIdWraparound>(current.picture_id,
                                                         last.picture_id)));

    // Expect continuously increasing picture id.
    int diff = ForwardDiff<uint16_t, kPictureIdWraparound>(last.picture_id,
                                                           current.picture_id);
    if (diff > 1) {
      // If the VideoSendStream is destroyed, any frames still in queue is lost.
      // Gaps only possible for first frame after a recreation, i.e. key frames.
      EXPECT_EQ(VideoFrameType::kVideoFrameKey, current.frame_type);
      EXPECT_LE(diff - 1, max_expected_picture_id_gap_);
    }
  }

  void VerifyTl0Idx(const ParsedPacket& current, const ParsedPacket& last) const
      RTC_EXCLUSIVE_LOCKS_REQUIRED(&mutex_) {
    if (current.tl0_pic_idx == kNoTl0PicIdx ||
        current.temporal_idx == kNoTemporalIdx) {
      return;  // No temporal layers.
    }

    if (current.timestamp == last.timestamp || current.temporal_idx != 0) {
      EXPECT_EQ(last.tl0_pic_idx, current.tl0_pic_idx);
      return;
    }

    // New frame with `temporal_idx` 0.
    // `tl0_pic_idx` should be increasing.
    EXPECT_TRUE(AheadOf<uint8_t>(current.tl0_pic_idx, last.tl0_pic_idx));

    // Expect continuously increasing idx.
    int diff = ForwardDiff<uint8_t>(last.tl0_pic_idx, current.tl0_pic_idx);
    if (diff > 1) {
      // If the VideoSendStream is destroyed, any frames still in queue is lost.
      // Gaps only possible for first frame after a recreation, i.e. key frames.
      EXPECT_EQ(VideoFrameType::kVideoFrameKey, current.frame_type);
      EXPECT_LE(diff - 1, max_expected_tl0_idx_gap_);
    }
  }

  Action OnSendRtp(const uint8_t* packet, size_t length) override {
    MutexLock lock(&mutex_);

    ParsedPacket parsed;
    if (!ParsePayload(packet, length, &parsed))
      return SEND_PACKET;

    uint32_t ssrc = parsed.ssrc;
    if (last_observed_packet_.find(ssrc) != last_observed_packet_.end()) {
      // Compare to last packet.
      VerifyPictureId(parsed, last_observed_packet_[ssrc]);
      VerifyTl0Idx(parsed, last_observed_packet_[ssrc]);
    }

    last_observed_packet_[ssrc] = parsed;

    // Pass the test when enough media packets have been received on all
    // streams.
    if (++num_packets_sent_[ssrc] >= kMinPacketsToObserve &&
        observed_ssrcs_.find(ssrc) == observed_ssrcs_.end()) {
      observed_ssrcs_.insert(ssrc);
      if (observed_ssrcs_.size() == num_ssrcs_to_observe_) {
        observation_complete_.Set();
      }
    }
    return SEND_PACKET;
  }

  Mutex mutex_;
  const std::unique_ptr<VideoRtpDepacketizer> depacketizer_;
  std::map<uint32_t, ParsedPacket> last_observed_packet_ RTC_GUARDED_BY(mutex_);
  std::map<uint32_t, size_t> num_packets_sent_ RTC_GUARDED_BY(mutex_);
  int max_expected_picture_id_gap_ RTC_GUARDED_BY(mutex_);
  int max_expected_tl0_idx_gap_ RTC_GUARDED_BY(mutex_);
  size_t num_ssrcs_to_observe_ RTC_GUARDED_BY(mutex_);
  std::set<uint32_t> observed_ssrcs_ RTC_GUARDED_BY(mutex_);
};

class PictureIdTest : public test::CallTest,
                      public ::testing::WithParamInterface<size_t> {
 public:
  PictureIdTest() : num_temporal_layers_(GetParam()) {}

  virtual ~PictureIdTest() {
    SendTask(RTC_FROM_HERE, task_queue(), [this]() {
      send_transport_.reset();
      receive_transport_.reset();
      DestroyCalls();
    });
  }

  void SetupEncoder(VideoEncoderFactory* encoder_factory,
                    const std::string& payload_name);
  void SetVideoEncoderConfig(int num_streams);
  void TestPictureIdContinuousAfterReconfigure(
      const std::vector<int>& ssrc_counts);
  void TestPictureIdIncreaseAfterRecreateStreams(
      const std::vector<int>& ssrc_counts);

 private:
  const size_t num_temporal_layers_;
  std::unique_ptr<PictureIdObserver> observer_;
};

INSTANTIATE_TEST_SUITE_P(TemporalLayers,
                         PictureIdTest,
                         ::testing::ValuesIn(kNumTemporalLayers));

void PictureIdTest::SetupEncoder(VideoEncoderFactory* encoder_factory,
                                 const std::string& payload_name) {
  observer_.reset(
      new PictureIdObserver(PayloadStringToCodecType(payload_name)));

  SendTask(
      RTC_FROM_HERE, task_queue(), [this, encoder_factory, payload_name]() {
        CreateCalls();

        send_transport_.reset(new test::PacketTransport(
            task_queue(), sender_call_.get(), observer_.get(),
            test::PacketTransport::kSender, payload_type_map_,
            std::make_unique<FakeNetworkPipe>(
                Clock::GetRealTimeClock(),
                std::make_unique<SimulatedNetwork>(
                    BuiltInNetworkBehaviorConfig()))));

        CreateSendConfig(kNumSimulcastStreams, 0, 0, send_transport_.get());
        GetVideoSendConfig()->encoder_settings.encoder_factory =
            encoder_factory;
        GetVideoSendConfig()->rtp.payload_name = payload_name;
        GetVideoEncoderConfig()->codec_type =
            PayloadStringToCodecType(payload_name);
        SetVideoEncoderConfig(/* number_of_streams */ 1);
      });
}

void PictureIdTest::SetVideoEncoderConfig(int num_streams) {
  GetVideoEncoderConfig()->number_of_streams = num_streams;
  GetVideoEncoderConfig()->max_bitrate_bps = kEncoderBitrateBps;

  // Always divide the same total bitrate across all streams so that sending a
  // single stream avoids lowering the bitrate estimate and requiring a
  // subsequent rampup.
  const int encoder_stream_bps = kEncoderBitrateBps / num_streams;
  double scale_factor = 1.0;
  for (int i = num_streams - 1; i >= 0; --i) {
    VideoStream& stream = GetVideoEncoderConfig()->simulcast_layers[i];
    // Reduce the min bitrate by 10% to account for overhead that might
    // otherwise cause streams to not be enabled.
    stream.min_bitrate_bps = static_cast<int>(encoder_stream_bps * 0.9);
    stream.target_bitrate_bps = encoder_stream_bps;
    stream.max_bitrate_bps = encoder_stream_bps;
    stream.num_temporal_layers = num_temporal_layers_;
    stream.scale_resolution_down_by = scale_factor;
    scale_factor *= 2.0;
  }
}

void PictureIdTest::TestPictureIdContinuousAfterReconfigure(
    const std::vector<int>& ssrc_counts) {
  SendTask(RTC_FROM_HERE, task_queue(), [this]() {
    CreateVideoStreams();
    CreateFrameGeneratorCapturer(kFrameRate, kFrameMaxWidth, kFrameMaxHeight);

    // Initial test with a single stream.
    Start();
  });

  EXPECT_TRUE(observer_->Wait()) << "Timed out waiting for packets.";

  // Reconfigure VideoEncoder and test picture id increase.
  // Expect continuously increasing picture id, equivalent to no gaps.
  observer_->SetMaxExpectedPictureIdGap(0);
  for (int ssrc_count : ssrc_counts) {
    SetVideoEncoderConfig(ssrc_count);
    observer_->SetExpectedSsrcs(ssrc_count);
    observer_->ResetObservedSsrcs();
    // Make sure the picture_id sequence is continuous on reinit and recreate.
    SendTask(RTC_FROM_HERE, task_queue(), [this]() {
      GetVideoSendStream()->ReconfigureVideoEncoder(
          GetVideoEncoderConfig()->Copy());
    });
    EXPECT_TRUE(observer_->Wait()) << "Timed out waiting for packets.";
  }

  SendTask(RTC_FROM_HERE, task_queue(), [this]() {
    Stop();
    DestroyStreams();
  });
}

void PictureIdTest::TestPictureIdIncreaseAfterRecreateStreams(
    const std::vector<int>& ssrc_counts) {
  SendTask(RTC_FROM_HERE, task_queue(), [this]() {
    CreateVideoStreams();
    CreateFrameGeneratorCapturer(kFrameRate, kFrameMaxWidth, kFrameMaxHeight);

    // Initial test with a single stream.
    Start();
  });

  EXPECT_TRUE(observer_->Wait()) << "Timed out waiting for packets.";

  // Recreate VideoSendStream and test picture id increase.
  // When the VideoSendStream is destroyed, any frames still in queue is lost
  // with it, therefore it is expected that some frames might be lost.
  observer_->SetMaxExpectedPictureIdGap(kMaxFramesLost);
  for (int ssrc_count : ssrc_counts) {
    SendTask(RTC_FROM_HERE, task_queue(), [this, &ssrc_count]() {
      DestroyVideoSendStreams();

      SetVideoEncoderConfig(ssrc_count);
      observer_->SetExpectedSsrcs(ssrc_count);
      observer_->ResetObservedSsrcs();

      CreateVideoSendStreams();
      GetVideoSendStream()->Start();
      CreateFrameGeneratorCapturer(kFrameRate, kFrameMaxWidth, kFrameMaxHeight);
    });

    EXPECT_TRUE(observer_->Wait()) << "Timed out waiting for packets.";
  }

  SendTask(RTC_FROM_HERE, task_queue(), [this]() {
    Stop();
    DestroyStreams();
  });
}

TEST_P(PictureIdTest, ContinuousAfterReconfigureVp8) {
  test::FunctionVideoEncoderFactory encoder_factory(
      []() { return VP8Encoder::Create(); });
  SetupEncoder(&encoder_factory, "VP8");
  TestPictureIdContinuousAfterReconfigure({1, 3, 3, 1, 1});
}

TEST_P(PictureIdTest, IncreasingAfterRecreateStreamVp8) {
  test::FunctionVideoEncoderFactory encoder_factory(
      []() { return VP8Encoder::Create(); });
  SetupEncoder(&encoder_factory, "VP8");
  TestPictureIdIncreaseAfterRecreateStreams({1, 3, 3, 1, 1});
}

TEST_P(PictureIdTest, ContinuousAfterStreamCountChangeVp8) {
  test::FunctionVideoEncoderFactory encoder_factory(
      []() { return VP8Encoder::Create(); });
  // Make sure that the picture id is not reset if the stream count goes
  // down and then up.
  SetupEncoder(&encoder_factory, "VP8");
  TestPictureIdContinuousAfterReconfigure({3, 1, 3});
}

TEST_P(PictureIdTest, ContinuousAfterReconfigureSimulcastEncoderAdapter) {
  InternalEncoderFactory internal_encoder_factory;
  test::FunctionVideoEncoderFactory encoder_factory(
      [&internal_encoder_factory]() {
        return std::make_unique<SimulcastEncoderAdapter>(
            &internal_encoder_factory, SdpVideoFormat("VP8"));
      });
  SetupEncoder(&encoder_factory, "VP8");
  TestPictureIdContinuousAfterReconfigure({1, 3, 3, 1, 1});
}

TEST_P(PictureIdTest, IncreasingAfterRecreateStreamSimulcastEncoderAdapter) {
  InternalEncoderFactory internal_encoder_factory;
  test::FunctionVideoEncoderFactory encoder_factory(
      [&internal_encoder_factory]() {
        return std::make_unique<SimulcastEncoderAdapter>(
            &internal_encoder_factory, SdpVideoFormat("VP8"));
      });
  SetupEncoder(&encoder_factory, "VP8");
  TestPictureIdIncreaseAfterRecreateStreams({1, 3, 3, 1, 1});
}

TEST_P(PictureIdTest, ContinuousAfterStreamCountChangeSimulcastEncoderAdapter) {
  InternalEncoderFactory internal_encoder_factory;
  test::FunctionVideoEncoderFactory encoder_factory(
      [&internal_encoder_factory]() {
        return std::make_unique<SimulcastEncoderAdapter>(
            &internal_encoder_factory, SdpVideoFormat("VP8"));
      });
  // Make sure that the picture id is not reset if the stream count goes
  // down and then up.
  SetupEncoder(&encoder_factory, "VP8");
  TestPictureIdContinuousAfterReconfigure({3, 1, 3});
}

TEST_P(PictureIdTest, IncreasingAfterRecreateStreamVp9) {
  test::FunctionVideoEncoderFactory encoder_factory(
      []() { return VP9Encoder::Create(); });
  SetupEncoder(&encoder_factory, "VP9");
  TestPictureIdIncreaseAfterRecreateStreams({1, 1});
}

}  // namespace webrtc
