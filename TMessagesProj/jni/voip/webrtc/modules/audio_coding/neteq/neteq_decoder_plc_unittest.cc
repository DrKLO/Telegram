/*
 *  Copyright (c) 2018 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

// Test to verify correct operation when using the decoder-internal PLC.

#include <memory>
#include <utility>
#include <vector>

#include "absl/types/optional.h"
#include "modules/audio_coding/codecs/pcm16b/audio_encoder_pcm16b.h"
#include "modules/audio_coding/neteq/tools/audio_checksum.h"
#include "modules/audio_coding/neteq/tools/audio_sink.h"
#include "modules/audio_coding/neteq/tools/encode_neteq_input.h"
#include "modules/audio_coding/neteq/tools/fake_decode_from_file.h"
#include "modules/audio_coding/neteq/tools/input_audio_file.h"
#include "modules/audio_coding/neteq/tools/neteq_test.h"
#include "rtc_base/numerics/safe_conversions.h"
#include "test/audio_decoder_proxy_factory.h"
#include "test/gtest.h"
#include "test/testsupport/file_utils.h"

namespace webrtc {
namespace test {
namespace {

constexpr int kSampleRateHz = 32000;
constexpr int kRunTimeMs = 10000;

// This class implements a fake decoder. The decoder will read audio from a file
// and present as output, both for regular decoding and for PLC.
class AudioDecoderPlc : public AudioDecoder {
 public:
  AudioDecoderPlc(std::unique_ptr<InputAudioFile> input, int sample_rate_hz)
      : input_(std::move(input)), sample_rate_hz_(sample_rate_hz) {}

  void Reset() override {}
  int SampleRateHz() const override { return sample_rate_hz_; }
  size_t Channels() const override { return 1; }
  int DecodeInternal(const uint8_t* /*encoded*/,
                     size_t encoded_len,
                     int sample_rate_hz,
                     int16_t* decoded,
                     SpeechType* speech_type) override {
    RTC_CHECK_GE(encoded_len / 2, 10 * sample_rate_hz_ / 1000);
    RTC_CHECK_LE(encoded_len / 2, 2 * 10 * sample_rate_hz_ / 1000);
    RTC_CHECK_EQ(sample_rate_hz, sample_rate_hz_);
    RTC_CHECK(decoded);
    RTC_CHECK(speech_type);
    RTC_CHECK(input_->Read(encoded_len / 2, decoded));
    *speech_type = kSpeech;
    last_was_plc_ = false;
    return encoded_len / 2;
  }

  void GeneratePlc(size_t requested_samples_per_channel,
                   rtc::BufferT<int16_t>* concealment_audio) override {
    // Instead of generating random data for GeneratePlc we use the same data as
    // the input, so we can check that we produce the same result independently
    // of the losses.
    RTC_DCHECK_EQ(requested_samples_per_channel, 10 * sample_rate_hz_ / 1000);

    // Must keep a local copy of this since DecodeInternal sets it to false.
    const bool last_was_plc = last_was_plc_;

    std::vector<int16_t> decoded(5760);
    SpeechType speech_type;
    int dec_len = DecodeInternal(nullptr, 2 * 10 * sample_rate_hz_ / 1000,
                                 sample_rate_hz_, decoded.data(), &speech_type);
    concealment_audio->AppendData(decoded.data(), dec_len);
    concealed_samples_ += rtc::checked_cast<size_t>(dec_len);

    if (!last_was_plc) {
      ++concealment_events_;
    }
    last_was_plc_ = true;
  }

  size_t concealed_samples() { return concealed_samples_; }
  size_t concealment_events() { return concealment_events_; }

 private:
  const std::unique_ptr<InputAudioFile> input_;
  const int sample_rate_hz_;
  size_t concealed_samples_ = 0;
  size_t concealment_events_ = 0;
  bool last_was_plc_ = false;
};

// An input sample generator which generates only zero-samples.
class ZeroSampleGenerator : public EncodeNetEqInput::Generator {
 public:
  rtc::ArrayView<const int16_t> Generate(size_t num_samples) override {
    vec.resize(num_samples, 0);
    rtc::ArrayView<const int16_t> view(vec);
    RTC_DCHECK_EQ(view.size(), num_samples);
    return view;
  }

 private:
  std::vector<int16_t> vec;
};

// A NetEqInput which connects to another NetEqInput, but drops a number of
// consecutive packets on the way
class LossyInput : public NetEqInput {
 public:
  LossyInput(int loss_cadence,
             int burst_length,
             std::unique_ptr<NetEqInput> input)
      : loss_cadence_(loss_cadence),
        burst_length_(burst_length),
        input_(std::move(input)) {}

  absl::optional<int64_t> NextPacketTime() const override {
    return input_->NextPacketTime();
  }

  absl::optional<int64_t> NextOutputEventTime() const override {
    return input_->NextOutputEventTime();
  }

  absl::optional<SetMinimumDelayInfo> NextSetMinimumDelayInfo() const override {
    return input_->NextSetMinimumDelayInfo();
  }

  std::unique_ptr<PacketData> PopPacket() override {
    if (loss_cadence_ != 0 && (++count_ % loss_cadence_) == 0) {
      // Pop `burst_length_` packets to create the loss.
      auto packet_to_return = input_->PopPacket();
      for (int i = 0; i < burst_length_; i++) {
        input_->PopPacket();
      }
      return packet_to_return;
    }
    return input_->PopPacket();
  }

  void AdvanceOutputEvent() override { return input_->AdvanceOutputEvent(); }

  void AdvanceSetMinimumDelay() override {
    return input_->AdvanceSetMinimumDelay();
  }

  bool ended() const override { return input_->ended(); }

  absl::optional<RTPHeader> NextHeader() const override {
    return input_->NextHeader();
  }

 private:
  const int loss_cadence_;
  const int burst_length_;
  int count_ = 0;
  const std::unique_ptr<NetEqInput> input_;
};

class AudioChecksumWithOutput : public AudioChecksum {
 public:
  explicit AudioChecksumWithOutput(std::string* output_str)
      : output_str_(*output_str) {}
  ~AudioChecksumWithOutput() { output_str_ = Finish(); }

 private:
  std::string& output_str_;
};

struct TestStatistics {
  NetEqNetworkStatistics network;
  NetEqLifetimeStatistics lifetime;
};

TestStatistics RunTest(int loss_cadence,
                       int burst_length,
                       std::string* checksum) {
  NetEq::Config config;
  config.for_test_no_time_stretching = true;

  // The input is mostly useless. It sends zero-samples to a PCM16b encoder,
  // but the actual encoded samples will never be used by the decoder in the
  // test. See below about the decoder.
  auto generator = std::make_unique<ZeroSampleGenerator>();
  constexpr int kPayloadType = 100;
  AudioEncoderPcm16B::Config encoder_config;
  encoder_config.sample_rate_hz = kSampleRateHz;
  encoder_config.payload_type = kPayloadType;
  auto encoder = std::make_unique<AudioEncoderPcm16B>(encoder_config);
  auto input = std::make_unique<EncodeNetEqInput>(
      std::move(generator), std::move(encoder), kRunTimeMs);
  // Wrap the input in a loss function.
  auto lossy_input = std::make_unique<LossyInput>(loss_cadence, burst_length,
                                                  std::move(input));

  // Setting up decoders.
  NetEqTest::DecoderMap decoders;
  // Using a fake decoder which simply reads the output audio from a file.
  auto input_file = std::make_unique<InputAudioFile>(
      webrtc::test::ResourcePath("audio_coding/testfile32kHz", "pcm"));
  AudioDecoderPlc dec(std::move(input_file), kSampleRateHz);
  // Masquerading as a PCM16b decoder.
  decoders.emplace(kPayloadType, SdpAudioFormat("l16", 32000, 1));

  // Output is simply a checksum calculator.
  auto output = std::make_unique<AudioChecksumWithOutput>(checksum);

  // No callback objects.
  NetEqTest::Callbacks callbacks;

  NetEqTest neteq_test(
      config, /*decoder_factory=*/
      rtc::make_ref_counted<test::AudioDecoderProxyFactory>(&dec),
      /*codecs=*/decoders, /*text_log=*/nullptr, /*neteq_factory=*/nullptr,
      /*input=*/std::move(lossy_input), std::move(output), callbacks);
  EXPECT_LE(kRunTimeMs, neteq_test.Run());

  auto lifetime_stats = neteq_test.LifetimeStats();
  EXPECT_EQ(dec.concealed_samples(), lifetime_stats.concealed_samples);
  EXPECT_EQ(dec.concealment_events(), lifetime_stats.concealment_events);
  return {neteq_test.SimulationStats(), neteq_test.LifetimeStats()};
}
}  // namespace

// Check that some basic metrics are produced in the right direction. In
// particular, expand_rate should only increase if there are losses present. Our
// dummy decoder is designed such as the checksum should always be the same
// regardless of the losses given that calls are executed in the right order.
TEST(NetEqDecoderPlc, BasicMetrics) {
  std::string checksum;

  // Drop 1 packet every 10 packets.
  auto stats = RunTest(10, 1, &checksum);

  std::string checksum_no_loss;
  auto stats_no_loss = RunTest(0, 0, &checksum_no_loss);

  EXPECT_EQ(checksum, checksum_no_loss);

  EXPECT_EQ(stats.network.preemptive_rate,
            stats_no_loss.network.preemptive_rate);
  EXPECT_EQ(stats.network.accelerate_rate,
            stats_no_loss.network.accelerate_rate);
  EXPECT_EQ(0, stats_no_loss.network.expand_rate);
  EXPECT_GT(stats.network.expand_rate, 0);
}

// Checks that interruptions are not counted in small losses but they are
// correctly counted in long interruptions.
TEST(NetEqDecoderPlc, CountInterruptions) {
  std::string checksum;
  std::string checksum_2;
  std::string checksum_3;

  // Half of the packets lost but in short interruptions.
  auto stats_no_interruptions = RunTest(1, 1, &checksum);
  // One lost of 500 ms (250 packets).
  auto stats_one_interruption = RunTest(200, 250, &checksum_2);
  // Two losses of 250ms each (125 packets).
  auto stats_two_interruptions = RunTest(125, 125, &checksum_3);

  EXPECT_EQ(checksum, checksum_2);
  EXPECT_EQ(checksum, checksum_3);
  EXPECT_GT(stats_no_interruptions.network.expand_rate, 0);
  EXPECT_EQ(stats_no_interruptions.lifetime.total_interruption_duration_ms, 0);
  EXPECT_EQ(stats_no_interruptions.lifetime.interruption_count, 0);

  EXPECT_GT(stats_one_interruption.network.expand_rate, 0);
  EXPECT_EQ(stats_one_interruption.lifetime.total_interruption_duration_ms,
            5000);
  EXPECT_EQ(stats_one_interruption.lifetime.interruption_count, 1);

  EXPECT_GT(stats_two_interruptions.network.expand_rate, 0);
  EXPECT_EQ(stats_two_interruptions.lifetime.total_interruption_duration_ms,
            5000);
  EXPECT_EQ(stats_two_interruptions.lifetime.interruption_count, 2);
}

// Checks that small losses do not produce interruptions.
TEST(NetEqDecoderPlc, NoInterruptionsInSmallLosses) {
  std::string checksum_1;
  std::string checksum_4;

  auto stats_1 = RunTest(300, 1, &checksum_1);
  auto stats_4 = RunTest(300, 4, &checksum_4);

  EXPECT_EQ(checksum_1, checksum_4);

  EXPECT_EQ(stats_1.lifetime.interruption_count, 0);
  EXPECT_EQ(stats_1.lifetime.total_interruption_duration_ms, 0);
  EXPECT_EQ(stats_1.lifetime.concealed_samples, 640u);  // 20ms of concealment.
  EXPECT_EQ(stats_1.lifetime.concealment_events, 1u);   // in just one event.

  EXPECT_EQ(stats_4.lifetime.interruption_count, 0);
  EXPECT_EQ(stats_4.lifetime.total_interruption_duration_ms, 0);
  EXPECT_EQ(stats_4.lifetime.concealed_samples, 2560u);  // 80ms of concealment.
  EXPECT_EQ(stats_4.lifetime.concealment_events, 1u);    // in just one event.
}

// Checks that interruptions of different sizes report correct duration.
TEST(NetEqDecoderPlc, InterruptionsReportCorrectSize) {
  std::string checksum;

  for (int burst_length = 5; burst_length < 10; burst_length++) {
    auto stats = RunTest(300, burst_length, &checksum);
    auto duration = stats.lifetime.total_interruption_duration_ms;
    if (burst_length < 8) {
      EXPECT_EQ(duration, 0);
    } else {
      EXPECT_EQ(duration, burst_length * 20);
    }
  }
}

}  // namespace test
}  // namespace webrtc
