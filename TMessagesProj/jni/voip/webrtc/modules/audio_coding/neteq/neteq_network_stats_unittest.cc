/*
 *  Copyright (c) 2015 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include <memory>

#include "absl/memory/memory.h"
#include "api/audio/audio_frame.h"
#include "api/audio_codecs/audio_decoder.h"
#include "api/audio_codecs/builtin_audio_decoder_factory.h"
#include "api/neteq/neteq.h"
#include "modules/audio_coding/neteq/default_neteq_factory.h"
#include "modules/audio_coding/neteq/tools/rtp_generator.h"
#include "system_wrappers/include/clock.h"
#include "test/audio_decoder_proxy_factory.h"
#include "test/gmock.h"

namespace webrtc {
namespace test {

namespace {

std::unique_ptr<NetEq> CreateNetEq(
    const NetEq::Config& config,
    Clock* clock,
    const rtc::scoped_refptr<AudioDecoderFactory>& decoder_factory) {
  return DefaultNetEqFactory().CreateNetEq(config, decoder_factory, clock);
}

}  // namespace

using ::testing::_;
using ::testing::Return;
using ::testing::SetArgPointee;

class MockAudioDecoder final : public AudioDecoder {
 public:
  static const int kPacketDuration = 960;  // 48 kHz * 20 ms

  MockAudioDecoder(int sample_rate_hz, size_t num_channels)
      : sample_rate_hz_(sample_rate_hz),
        num_channels_(num_channels),
        fec_enabled_(false) {}
  ~MockAudioDecoder() override { Die(); }
  MOCK_METHOD(void, Die, ());

  MOCK_METHOD(void, Reset, (), (override));

  class MockFrame : public AudioDecoder::EncodedAudioFrame {
   public:
    MockFrame(size_t num_channels) : num_channels_(num_channels) {}

    size_t Duration() const override { return kPacketDuration; }

    absl::optional<DecodeResult> Decode(
        rtc::ArrayView<int16_t> decoded) const override {
      const size_t output_size =
          sizeof(int16_t) * kPacketDuration * num_channels_;
      if (decoded.size() >= output_size) {
        memset(decoded.data(), 0,
               sizeof(int16_t) * kPacketDuration * num_channels_);
        return DecodeResult{kPacketDuration * num_channels_, kSpeech};
      } else {
        ADD_FAILURE() << "Expected decoded.size() to be >= output_size ("
                      << decoded.size() << " vs. " << output_size << ")";
        return absl::nullopt;
      }
    }

   private:
    const size_t num_channels_;
  };

  std::vector<ParseResult> ParsePayload(rtc::Buffer&& payload,
                                        uint32_t timestamp) override {
    std::vector<ParseResult> results;
    if (fec_enabled_) {
      std::unique_ptr<MockFrame> fec_frame(new MockFrame(num_channels_));
      results.emplace_back(timestamp - kPacketDuration, 1,
                           std::move(fec_frame));
    }

    std::unique_ptr<MockFrame> frame(new MockFrame(num_channels_));
    results.emplace_back(timestamp, 0, std::move(frame));
    return results;
  }

  int PacketDuration(const uint8_t* encoded,
                     size_t encoded_len) const override {
    ADD_FAILURE() << "Since going through ParsePayload, PacketDuration should "
                     "never get called.";
    return kPacketDuration;
  }

  bool PacketHasFec(const uint8_t* encoded, size_t encoded_len) const override {
    ADD_FAILURE() << "Since going through ParsePayload, PacketHasFec should "
                     "never get called.";
    return fec_enabled_;
  }

  int SampleRateHz() const override { return sample_rate_hz_; }

  size_t Channels() const override { return num_channels_; }

  void set_fec_enabled(bool enable_fec) { fec_enabled_ = enable_fec; }

  bool fec_enabled() const { return fec_enabled_; }

 protected:
  int DecodeInternal(const uint8_t* encoded,
                     size_t encoded_len,
                     int sample_rate_hz,
                     int16_t* decoded,
                     SpeechType* speech_type) override {
    ADD_FAILURE() << "Since going through ParsePayload, DecodeInternal should "
                     "never get called.";
    return -1;
  }

 private:
  const int sample_rate_hz_;
  const size_t num_channels_;
  bool fec_enabled_;
};

class NetEqNetworkStatsTest {
 public:
  static const int kPayloadSizeByte = 30;
  static const int kFrameSizeMs = 20;
  static const uint8_t kPayloadType = 95;
  static const int kOutputLengthMs = 10;

  enum logic {
    kIgnore,
    kEqual,
    kSmallerThan,
    kLargerThan,
  };

  struct NetEqNetworkStatsCheck {
    logic current_buffer_size_ms;
    logic preferred_buffer_size_ms;
    logic jitter_peaks_found;
    logic packet_loss_rate;
    logic expand_rate;
    logic speech_expand_rate;
    logic preemptive_rate;
    logic accelerate_rate;
    logic secondary_decoded_rate;
    logic secondary_discarded_rate;
    logic added_zero_samples;
    NetEqNetworkStatistics stats_ref;
  };

  NetEqNetworkStatsTest(const SdpAudioFormat& format, MockAudioDecoder* decoder)
      : decoder_(decoder),
        decoder_factory_(
            rtc::make_ref_counted<AudioDecoderProxyFactory>(decoder)),
        samples_per_ms_(format.clockrate_hz / 1000),
        frame_size_samples_(kFrameSizeMs * samples_per_ms_),
        rtp_generator_(new RtpGenerator(samples_per_ms_)),
        last_lost_time_(0),
        packet_loss_interval_(0xffffffff) {
    NetEq::Config config;
    config.sample_rate_hz = format.clockrate_hz;
    neteq_ = CreateNetEq(config, Clock::GetRealTimeClock(), decoder_factory_);
    neteq_->RegisterPayloadType(kPayloadType, format);
  }

  bool Lost(uint32_t send_time) {
    if (send_time - last_lost_time_ >= packet_loss_interval_) {
      last_lost_time_ = send_time;
      return true;
    }
    return false;
  }

  void SetPacketLossRate(double loss_rate) {
    packet_loss_interval_ =
        (loss_rate >= 1e-3 ? static_cast<double>(kFrameSizeMs) / loss_rate
                           : 0xffffffff);
  }

  // `stats_ref`
  // expects.x = -1, do not care
  // expects.x = 0, 'x' in current stats should equal 'x' in `stats_ref`
  // expects.x = 1, 'x' in current stats should < 'x' in `stats_ref`
  // expects.x = 2, 'x' in current stats should > 'x' in `stats_ref`
  void CheckNetworkStatistics(NetEqNetworkStatsCheck expects) {
    NetEqNetworkStatistics stats;
    neteq_->NetworkStatistics(&stats);

#define CHECK_NETEQ_NETWORK_STATS(x)           \
  switch (expects.x) {                         \
    case kEqual:                               \
      EXPECT_EQ(stats.x, expects.stats_ref.x); \
      break;                                   \
    case kSmallerThan:                         \
      EXPECT_LT(stats.x, expects.stats_ref.x); \
      break;                                   \
    case kLargerThan:                          \
      EXPECT_GT(stats.x, expects.stats_ref.x); \
      break;                                   \
    default:                                   \
      break;                                   \
  }

    CHECK_NETEQ_NETWORK_STATS(current_buffer_size_ms);
    CHECK_NETEQ_NETWORK_STATS(preferred_buffer_size_ms);
    CHECK_NETEQ_NETWORK_STATS(jitter_peaks_found);
    CHECK_NETEQ_NETWORK_STATS(expand_rate);
    CHECK_NETEQ_NETWORK_STATS(speech_expand_rate);
    CHECK_NETEQ_NETWORK_STATS(preemptive_rate);
    CHECK_NETEQ_NETWORK_STATS(accelerate_rate);
    CHECK_NETEQ_NETWORK_STATS(secondary_decoded_rate);
    CHECK_NETEQ_NETWORK_STATS(secondary_discarded_rate);

#undef CHECK_NETEQ_NETWORK_STATS
  }

  void RunTest(int num_loops, NetEqNetworkStatsCheck expects) {
    uint32_t time_now;
    uint32_t next_send_time;

    // Initiate `last_lost_time_`.
    time_now = next_send_time = last_lost_time_ = rtp_generator_->GetRtpHeader(
        kPayloadType, frame_size_samples_, &rtp_header_);
    for (int k = 0; k < num_loops; ++k) {
      // Delay by one frame such that the FEC can come in.
      while (time_now + kFrameSizeMs >= next_send_time) {
        next_send_time = rtp_generator_->GetRtpHeader(
            kPayloadType, frame_size_samples_, &rtp_header_);
        if (!Lost(next_send_time)) {
          static const uint8_t payload[kPayloadSizeByte] = {0};
          ASSERT_EQ(NetEq::kOK, neteq_->InsertPacket(rtp_header_, payload));
        }
      }
      bool muted = true;
      EXPECT_EQ(NetEq::kOK, neteq_->GetAudio(&output_frame_, &muted));
      ASSERT_FALSE(muted);
      EXPECT_EQ(decoder_->Channels(), output_frame_.num_channels_);
      EXPECT_EQ(static_cast<size_t>(kOutputLengthMs * samples_per_ms_),
                output_frame_.samples_per_channel_);
      EXPECT_EQ(48000, neteq_->last_output_sample_rate_hz());

      time_now += kOutputLengthMs;
    }
    CheckNetworkStatistics(expects);
    neteq_->FlushBuffers();
  }

  void DecodeFecTest() {
    decoder_->set_fec_enabled(false);
    NetEqNetworkStatsCheck expects = {kIgnore,  // current_buffer_size_ms
                                      kIgnore,  // preferred_buffer_size_ms
                                      kIgnore,  // jitter_peaks_found
                                      kEqual,   // packet_loss_rate
                                      kEqual,   // expand_rate
                                      kEqual,   // voice_expand_rate
                                      kIgnore,  // preemptive_rate
                                      kEqual,   // accelerate_rate
                                      kEqual,   // decoded_fec_rate
                                      kEqual,   // discarded_fec_rate
                                      kEqual,   // added_zero_samples
                                      {0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0}};
    RunTest(50, expects);

    // Next we introduce packet losses.
    SetPacketLossRate(0.1);
    expects.expand_rate = expects.speech_expand_rate = kLargerThan;
    RunTest(50, expects);

    // Next we enable FEC.
    decoder_->set_fec_enabled(true);
    // If FEC fills in the lost packets, no packet loss will be counted.
    expects.expand_rate = expects.speech_expand_rate = kEqual;
    expects.stats_ref.expand_rate = expects.stats_ref.speech_expand_rate = 0;
    expects.secondary_decoded_rate = kLargerThan;
    expects.secondary_discarded_rate = kLargerThan;
    RunTest(50, expects);
  }

  void NoiseExpansionTest() {
    NetEqNetworkStatsCheck expects = {kIgnore,  // current_buffer_size_ms
                                      kIgnore,  // preferred_buffer_size_ms
                                      kIgnore,  // jitter_peaks_found
                                      kEqual,   // packet_loss_rate
                                      kEqual,   // expand_rate
                                      kEqual,   // speech_expand_rate
                                      kIgnore,  // preemptive_rate
                                      kEqual,   // accelerate_rate
                                      kEqual,   // decoded_fec_rate
                                      kEqual,   // discard_fec_rate
                                      kEqual,   // added_zero_samples
                                      {0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0}};
    RunTest(50, expects);

    SetPacketLossRate(1);
    expects.stats_ref.expand_rate = 16384;
    expects.stats_ref.speech_expand_rate = 5324;
    RunTest(10, expects);  // Lost 10 * 20ms in a row.
  }

 private:
  MockAudioDecoder* decoder_;
  rtc::scoped_refptr<AudioDecoderProxyFactory> decoder_factory_;
  std::unique_ptr<NetEq> neteq_;

  const int samples_per_ms_;
  const size_t frame_size_samples_;
  std::unique_ptr<RtpGenerator> rtp_generator_;
  RTPHeader rtp_header_;
  uint32_t last_lost_time_;
  uint32_t packet_loss_interval_;
  AudioFrame output_frame_;
};

TEST(NetEqNetworkStatsTest, DecodeFec) {
  MockAudioDecoder decoder(48000, 1);
  NetEqNetworkStatsTest test(SdpAudioFormat("opus", 48000, 2), &decoder);
  test.DecodeFecTest();
  EXPECT_CALL(decoder, Die()).Times(1);
}

TEST(NetEqNetworkStatsTest, StereoDecodeFec) {
  MockAudioDecoder decoder(48000, 2);
  NetEqNetworkStatsTest test(SdpAudioFormat("opus", 48000, 2), &decoder);
  test.DecodeFecTest();
  EXPECT_CALL(decoder, Die()).Times(1);
}

TEST(NetEqNetworkStatsTest, NoiseExpansionTest) {
  MockAudioDecoder decoder(48000, 1);
  NetEqNetworkStatsTest test(SdpAudioFormat("opus", 48000, 2), &decoder);
  test.NoiseExpansionTest();
  EXPECT_CALL(decoder, Die()).Times(1);
}

}  // namespace test
}  // namespace webrtc
