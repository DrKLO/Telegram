/*
 *  Copyright (c) 2015 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "api/audio_codecs/opus/audio_encoder_opus.h"

#include <array>
#include <memory>
#include <utility>

#include "absl/strings/string_view.h"
#include "common_audio/mocks/mock_smoothing_filter.h"
#include "modules/audio_coding/audio_network_adaptor/mock/mock_audio_network_adaptor.h"
#include "modules/audio_coding/codecs/opus/audio_encoder_opus.h"
#include "modules/audio_coding/codecs/opus/opus_interface.h"
#include "modules/audio_coding/neteq/tools/audio_loop.h"
#include "rtc_base/checks.h"
#include "rtc_base/fake_clock.h"
#include "test/field_trial.h"
#include "test/gmock.h"
#include "test/gtest.h"
#include "test/testsupport/file_utils.h"

namespace webrtc {
using ::testing::NiceMock;
using ::testing::Return;

namespace {

constexpr int kDefaultOpusPayloadType = 105;
constexpr int kDefaultOpusRate = 32000;
constexpr int kDefaultOpusPacSize = 960;
constexpr int64_t kInitialTimeUs = 12345678;

AudioEncoderOpusConfig CreateConfigWithParameters(
    const CodecParameterMap& params) {
  const SdpAudioFormat format("opus", 48000, 2, params);
  return *AudioEncoderOpus::SdpToConfig(format);
}

struct AudioEncoderOpusStates {
  MockAudioNetworkAdaptor* mock_audio_network_adaptor;
  MockSmoothingFilter* mock_bitrate_smoother;
  std::unique_ptr<AudioEncoderOpusImpl> encoder;
  std::unique_ptr<rtc::ScopedFakeClock> fake_clock;
  AudioEncoderOpusConfig config;
};

std::unique_ptr<AudioEncoderOpusStates> CreateCodec(int sample_rate_hz,
                                                    size_t num_channels) {
  std::unique_ptr<AudioEncoderOpusStates> states =
      std::make_unique<AudioEncoderOpusStates>();
  states->mock_audio_network_adaptor = nullptr;
  states->fake_clock.reset(new rtc::ScopedFakeClock());
  states->fake_clock->SetTime(Timestamp::Micros(kInitialTimeUs));

  MockAudioNetworkAdaptor** mock_ptr = &states->mock_audio_network_adaptor;
  AudioEncoderOpusImpl::AudioNetworkAdaptorCreator creator =
      [mock_ptr](absl::string_view, RtcEventLog* event_log) {
        std::unique_ptr<MockAudioNetworkAdaptor> adaptor(
            new NiceMock<MockAudioNetworkAdaptor>());
        EXPECT_CALL(*adaptor, Die());
        *mock_ptr = adaptor.get();
        return adaptor;
      };

  AudioEncoderOpusConfig config;
  config.frame_size_ms = rtc::CheckedDivExact(kDefaultOpusPacSize, 48);
  config.sample_rate_hz = sample_rate_hz;
  config.num_channels = num_channels;
  config.bitrate_bps = kDefaultOpusRate;
  config.application = num_channels == 1
                           ? AudioEncoderOpusConfig::ApplicationMode::kVoip
                           : AudioEncoderOpusConfig::ApplicationMode::kAudio;
  config.supported_frame_lengths_ms.push_back(config.frame_size_ms);
  states->config = config;

  std::unique_ptr<MockSmoothingFilter> bitrate_smoother(
      new MockSmoothingFilter());
  states->mock_bitrate_smoother = bitrate_smoother.get();

  states->encoder.reset(
      new AudioEncoderOpusImpl(states->config, kDefaultOpusPayloadType, creator,
                               std::move(bitrate_smoother)));
  return states;
}

AudioEncoderRuntimeConfig CreateEncoderRuntimeConfig() {
  constexpr int kBitrate = 40000;
  constexpr int kFrameLength = 60;
  constexpr bool kEnableDtx = false;
  constexpr size_t kNumChannels = 1;
  AudioEncoderRuntimeConfig config;
  config.bitrate_bps = kBitrate;
  config.frame_length_ms = kFrameLength;
  config.enable_dtx = kEnableDtx;
  config.num_channels = kNumChannels;
  return config;
}

void CheckEncoderRuntimeConfig(const AudioEncoderOpusImpl* encoder,
                               const AudioEncoderRuntimeConfig& config) {
  EXPECT_EQ(*config.bitrate_bps, encoder->GetTargetBitrate());
  EXPECT_EQ(*config.frame_length_ms, encoder->next_frame_length_ms());
  EXPECT_EQ(*config.enable_dtx, encoder->GetDtx());
  EXPECT_EQ(*config.num_channels, encoder->num_channels_to_encode());
}

// Create 10ms audio data blocks for a total packet size of "packet_size_ms".
std::unique_ptr<test::AudioLoop> Create10msAudioBlocks(
    const std::unique_ptr<AudioEncoderOpusImpl>& encoder,
    int packet_size_ms) {
  const std::string file_name =
      test::ResourcePath("audio_coding/testfile32kHz", "pcm");

  std::unique_ptr<test::AudioLoop> speech_data(new test::AudioLoop());
  int audio_samples_per_ms =
      rtc::CheckedDivExact(encoder->SampleRateHz(), 1000);
  if (!speech_data->Init(
          file_name,
          packet_size_ms * audio_samples_per_ms *
              encoder->num_channels_to_encode(),
          10 * audio_samples_per_ms * encoder->num_channels_to_encode()))
    return nullptr;
  return speech_data;
}

}  // namespace

class AudioEncoderOpusTest : public ::testing::TestWithParam<int> {
 protected:
  int sample_rate_hz_{GetParam()};
};
INSTANTIATE_TEST_SUITE_P(Param,
                         AudioEncoderOpusTest,
                         ::testing::Values(16000, 48000));

TEST_P(AudioEncoderOpusTest, DefaultApplicationModeMono) {
  auto states = CreateCodec(sample_rate_hz_, 1);
  EXPECT_EQ(AudioEncoderOpusConfig::ApplicationMode::kVoip,
            states->encoder->application());
}

TEST_P(AudioEncoderOpusTest, DefaultApplicationModeStereo) {
  auto states = CreateCodec(sample_rate_hz_, 2);
  EXPECT_EQ(AudioEncoderOpusConfig::ApplicationMode::kAudio,
            states->encoder->application());
}

TEST_P(AudioEncoderOpusTest, ChangeApplicationMode) {
  auto states = CreateCodec(sample_rate_hz_, 2);
  EXPECT_TRUE(
      states->encoder->SetApplication(AudioEncoder::Application::kSpeech));
  EXPECT_EQ(AudioEncoderOpusConfig::ApplicationMode::kVoip,
            states->encoder->application());
}

TEST_P(AudioEncoderOpusTest, ResetWontChangeApplicationMode) {
  auto states = CreateCodec(sample_rate_hz_, 2);

  // Trigger a reset.
  states->encoder->Reset();
  // Verify that the mode is still kAudio.
  EXPECT_EQ(AudioEncoderOpusConfig::ApplicationMode::kAudio,
            states->encoder->application());

  // Now change to kVoip.
  EXPECT_TRUE(
      states->encoder->SetApplication(AudioEncoder::Application::kSpeech));
  EXPECT_EQ(AudioEncoderOpusConfig::ApplicationMode::kVoip,
            states->encoder->application());

  // Trigger a reset again.
  states->encoder->Reset();
  // Verify that the mode is still kVoip.
  EXPECT_EQ(AudioEncoderOpusConfig::ApplicationMode::kVoip,
            states->encoder->application());
}

TEST_P(AudioEncoderOpusTest, ToggleDtx) {
  auto states = CreateCodec(sample_rate_hz_, 2);
  // Enable DTX
  EXPECT_TRUE(states->encoder->SetDtx(true));
  EXPECT_TRUE(states->encoder->GetDtx());
  // Turn off DTX.
  EXPECT_TRUE(states->encoder->SetDtx(false));
  EXPECT_FALSE(states->encoder->GetDtx());
}

TEST_P(AudioEncoderOpusTest,
       OnReceivedUplinkBandwidthWithoutAudioNetworkAdaptor) {
  auto states = CreateCodec(sample_rate_hz_, 1);
  // Constants are replicated from audio_states->encoderopus.cc.
  const int kMinBitrateBps = 6000;
  const int kMaxBitrateBps = 510000;
  const int kOverheadBytesPerPacket = 64;
  states->encoder->OnReceivedOverhead(kOverheadBytesPerPacket);
  const int kOverheadBps = 8 * kOverheadBytesPerPacket *
                           rtc::CheckedDivExact(48000, kDefaultOpusPacSize);
  // Set a too low bitrate.
  states->encoder->OnReceivedUplinkBandwidth(kMinBitrateBps + kOverheadBps - 1,
                                             absl::nullopt);
  EXPECT_EQ(kMinBitrateBps, states->encoder->GetTargetBitrate());
  // Set a too high bitrate.
  states->encoder->OnReceivedUplinkBandwidth(kMaxBitrateBps + kOverheadBps + 1,
                                             absl::nullopt);
  EXPECT_EQ(kMaxBitrateBps, states->encoder->GetTargetBitrate());
  // Set the minimum rate.
  states->encoder->OnReceivedUplinkBandwidth(kMinBitrateBps + kOverheadBps,
                                             absl::nullopt);
  EXPECT_EQ(kMinBitrateBps, states->encoder->GetTargetBitrate());
  // Set the maximum rate.
  states->encoder->OnReceivedUplinkBandwidth(kMaxBitrateBps + kOverheadBps,
                                             absl::nullopt);
  EXPECT_EQ(kMaxBitrateBps, states->encoder->GetTargetBitrate());
  // Set rates from kMaxBitrateBps up to 32000 bps.
  for (int rate = kMinBitrateBps + kOverheadBps; rate <= 32000 + kOverheadBps;
       rate += 1000) {
    states->encoder->OnReceivedUplinkBandwidth(rate, absl::nullopt);
    EXPECT_EQ(rate - kOverheadBps, states->encoder->GetTargetBitrate());
  }
}

TEST_P(AudioEncoderOpusTest, SetReceiverFrameLengthRange) {
  auto states = CreateCodec(sample_rate_hz_, 2);
  // Before calling to `SetReceiverFrameLengthRange`,
  // `supported_frame_lengths_ms` should contain only the frame length being
  // used.
  using ::testing::ElementsAre;
  EXPECT_THAT(states->encoder->supported_frame_lengths_ms(),
              ElementsAre(states->encoder->next_frame_length_ms()));
  states->encoder->SetReceiverFrameLengthRange(0, 12345);
  states->encoder->SetReceiverFrameLengthRange(21, 60);
  EXPECT_THAT(states->encoder->supported_frame_lengths_ms(),
              ElementsAre(40, 60));
  states->encoder->SetReceiverFrameLengthRange(20, 59);
  EXPECT_THAT(states->encoder->supported_frame_lengths_ms(),
              ElementsAre(20, 40));
}

TEST_P(AudioEncoderOpusTest,
       InvokeAudioNetworkAdaptorOnReceivedUplinkPacketLossFraction) {
  auto states = CreateCodec(sample_rate_hz_, 2);
  states->encoder->EnableAudioNetworkAdaptor("", nullptr);

  auto config = CreateEncoderRuntimeConfig();
  EXPECT_CALL(*states->mock_audio_network_adaptor, GetEncoderRuntimeConfig())
      .WillOnce(Return(config));

  // Since using mock audio network adaptor, any packet loss fraction is fine.
  constexpr float kUplinkPacketLoss = 0.1f;
  EXPECT_CALL(*states->mock_audio_network_adaptor,
              SetUplinkPacketLossFraction(kUplinkPacketLoss));
  states->encoder->OnReceivedUplinkPacketLossFraction(kUplinkPacketLoss);

  CheckEncoderRuntimeConfig(states->encoder.get(), config);
}

TEST_P(AudioEncoderOpusTest,
       InvokeAudioNetworkAdaptorOnReceivedUplinkBandwidth) {
  test::ScopedFieldTrials override_field_trials(
      "WebRTC-Audio-StableTargetAdaptation/Disabled/");
  auto states = CreateCodec(sample_rate_hz_, 2);
  states->encoder->EnableAudioNetworkAdaptor("", nullptr);

  auto config = CreateEncoderRuntimeConfig();
  EXPECT_CALL(*states->mock_audio_network_adaptor, GetEncoderRuntimeConfig())
      .WillOnce(Return(config));

  // Since using mock audio network adaptor, any target audio bitrate is fine.
  constexpr int kTargetAudioBitrate = 30000;
  constexpr int64_t kProbingIntervalMs = 3000;
  EXPECT_CALL(*states->mock_audio_network_adaptor,
              SetTargetAudioBitrate(kTargetAudioBitrate));
  EXPECT_CALL(*states->mock_bitrate_smoother,
              SetTimeConstantMs(kProbingIntervalMs * 4));
  EXPECT_CALL(*states->mock_bitrate_smoother, AddSample(kTargetAudioBitrate));
  states->encoder->OnReceivedUplinkBandwidth(kTargetAudioBitrate,
                                             kProbingIntervalMs);

  CheckEncoderRuntimeConfig(states->encoder.get(), config);
}

TEST_P(AudioEncoderOpusTest,
       InvokeAudioNetworkAdaptorOnReceivedUplinkAllocation) {
  auto states = CreateCodec(sample_rate_hz_, 2);
  states->encoder->EnableAudioNetworkAdaptor("", nullptr);

  auto config = CreateEncoderRuntimeConfig();
  EXPECT_CALL(*states->mock_audio_network_adaptor, GetEncoderRuntimeConfig())
      .WillOnce(Return(config));

  BitrateAllocationUpdate update;
  update.target_bitrate = DataRate::BitsPerSec(30000);
  update.stable_target_bitrate = DataRate::BitsPerSec(20000);
  update.bwe_period = TimeDelta::Millis(200);
  EXPECT_CALL(*states->mock_audio_network_adaptor,
              SetTargetAudioBitrate(update.target_bitrate.bps()));
  EXPECT_CALL(*states->mock_audio_network_adaptor,
              SetUplinkBandwidth(update.stable_target_bitrate.bps()));
  states->encoder->OnReceivedUplinkAllocation(update);

  CheckEncoderRuntimeConfig(states->encoder.get(), config);
}

TEST_P(AudioEncoderOpusTest, InvokeAudioNetworkAdaptorOnReceivedRtt) {
  auto states = CreateCodec(sample_rate_hz_, 2);
  states->encoder->EnableAudioNetworkAdaptor("", nullptr);

  auto config = CreateEncoderRuntimeConfig();
  EXPECT_CALL(*states->mock_audio_network_adaptor, GetEncoderRuntimeConfig())
      .WillOnce(Return(config));

  // Since using mock audio network adaptor, any rtt is fine.
  constexpr int kRtt = 30;
  EXPECT_CALL(*states->mock_audio_network_adaptor, SetRtt(kRtt));
  states->encoder->OnReceivedRtt(kRtt);

  CheckEncoderRuntimeConfig(states->encoder.get(), config);
}

TEST_P(AudioEncoderOpusTest, InvokeAudioNetworkAdaptorOnReceivedOverhead) {
  auto states = CreateCodec(sample_rate_hz_, 2);
  states->encoder->EnableAudioNetworkAdaptor("", nullptr);

  auto config = CreateEncoderRuntimeConfig();
  EXPECT_CALL(*states->mock_audio_network_adaptor, GetEncoderRuntimeConfig())
      .WillOnce(Return(config));

  // Since using mock audio network adaptor, any overhead is fine.
  constexpr size_t kOverhead = 64;
  EXPECT_CALL(*states->mock_audio_network_adaptor, SetOverhead(kOverhead));
  states->encoder->OnReceivedOverhead(kOverhead);

  CheckEncoderRuntimeConfig(states->encoder.get(), config);
}

TEST_P(AudioEncoderOpusTest,
       PacketLossFractionSmoothedOnSetUplinkPacketLossFraction) {
  auto states = CreateCodec(sample_rate_hz_, 2);

  // The values are carefully chosen so that if no smoothing is made, the test
  // will fail.
  constexpr float kPacketLossFraction_1 = 0.02f;
  constexpr float kPacketLossFraction_2 = 0.198f;
  // `kSecondSampleTimeMs` is chosen to ease the calculation since
  // 0.9999 ^ 6931 = 0.5.
  constexpr int64_t kSecondSampleTimeMs = 6931;

  // First time, no filtering.
  states->encoder->OnReceivedUplinkPacketLossFraction(kPacketLossFraction_1);
  EXPECT_FLOAT_EQ(0.02f, states->encoder->packet_loss_rate());

  states->fake_clock->AdvanceTime(TimeDelta::Millis(kSecondSampleTimeMs));
  states->encoder->OnReceivedUplinkPacketLossFraction(kPacketLossFraction_2);

  // Now the output of packet loss fraction smoother should be
  // (0.02 + 0.198) / 2 = 0.109.
  EXPECT_NEAR(0.109f, states->encoder->packet_loss_rate(), 0.001);
}

TEST_P(AudioEncoderOpusTest, PacketLossRateUpperBounded) {
  auto states = CreateCodec(sample_rate_hz_, 2);

  states->encoder->OnReceivedUplinkPacketLossFraction(0.5);
  EXPECT_FLOAT_EQ(0.2f, states->encoder->packet_loss_rate());
}

TEST_P(AudioEncoderOpusTest, DoNotInvokeSetTargetBitrateIfOverheadUnknown) {
  auto states = CreateCodec(sample_rate_hz_, 2);

  states->encoder->OnReceivedUplinkBandwidth(kDefaultOpusRate * 2,
                                             absl::nullopt);

  // Since `OnReceivedOverhead` has not been called, the codec bitrate should
  // not change.
  EXPECT_EQ(kDefaultOpusRate, states->encoder->GetTargetBitrate());
}

// Verifies that the complexity adaptation in the config works as intended.
TEST(AudioEncoderOpusTest, ConfigComplexityAdaptation) {
  AudioEncoderOpusConfig config;
  config.low_rate_complexity = 8;
  config.complexity = 6;

  // Bitrate within hysteresis window. Expect empty output.
  config.bitrate_bps = 12500;
  EXPECT_EQ(absl::nullopt, AudioEncoderOpusImpl::GetNewComplexity(config));

  // Bitrate below hysteresis window. Expect higher complexity.
  config.bitrate_bps = 10999;
  EXPECT_EQ(8, AudioEncoderOpusImpl::GetNewComplexity(config));

  // Bitrate within hysteresis window. Expect empty output.
  config.bitrate_bps = 12500;
  EXPECT_EQ(absl::nullopt, AudioEncoderOpusImpl::GetNewComplexity(config));

  // Bitrate above hysteresis window. Expect lower complexity.
  config.bitrate_bps = 14001;
  EXPECT_EQ(6, AudioEncoderOpusImpl::GetNewComplexity(config));
}

// Verifies that the bandwidth adaptation in the config works as intended.
TEST_P(AudioEncoderOpusTest, ConfigBandwidthAdaptation) {
  AudioEncoderOpusConfig config;
  const size_t opus_rate_khz = rtc::CheckedDivExact(sample_rate_hz_, 1000);
  const std::vector<int16_t> silence(
      opus_rate_khz * config.frame_size_ms * config.num_channels, 0);
  constexpr size_t kMaxBytes = 1000;
  uint8_t bitstream[kMaxBytes];

  OpusEncInst* inst;
  EXPECT_EQ(0, WebRtcOpus_EncoderCreate(
                   &inst, config.num_channels,
                   config.application ==
                           AudioEncoderOpusConfig::ApplicationMode::kVoip
                       ? 0
                       : 1,
                   sample_rate_hz_));

  // Bitrate below minmum wideband. Expect narrowband.
  config.bitrate_bps = absl::optional<int>(7999);
  auto bandwidth = AudioEncoderOpusImpl::GetNewBandwidth(config, inst);
  EXPECT_EQ(absl::optional<int>(OPUS_BANDWIDTH_NARROWBAND), bandwidth);
  WebRtcOpus_SetBandwidth(inst, *bandwidth);
  // It is necessary to encode here because Opus has some logic in the encoder
  // that goes from the user-set bandwidth to the used and returned one.
  WebRtcOpus_Encode(inst, silence.data(),
                    rtc::CheckedDivExact(silence.size(), config.num_channels),
                    kMaxBytes, bitstream);

  // Bitrate not yet above maximum narrowband. Expect empty.
  config.bitrate_bps = absl::optional<int>(9000);
  bandwidth = AudioEncoderOpusImpl::GetNewBandwidth(config, inst);
  EXPECT_EQ(absl::optional<int>(), bandwidth);

  // Bitrate above maximum narrowband. Expect wideband.
  config.bitrate_bps = absl::optional<int>(9001);
  bandwidth = AudioEncoderOpusImpl::GetNewBandwidth(config, inst);
  EXPECT_EQ(absl::optional<int>(OPUS_BANDWIDTH_WIDEBAND), bandwidth);
  WebRtcOpus_SetBandwidth(inst, *bandwidth);
  // It is necessary to encode here because Opus has some logic in the encoder
  // that goes from the user-set bandwidth to the used and returned one.
  WebRtcOpus_Encode(inst, silence.data(),
                    rtc::CheckedDivExact(silence.size(), config.num_channels),
                    kMaxBytes, bitstream);

  // Bitrate not yet below minimum wideband. Expect empty.
  config.bitrate_bps = absl::optional<int>(8000);
  bandwidth = AudioEncoderOpusImpl::GetNewBandwidth(config, inst);
  EXPECT_EQ(absl::optional<int>(), bandwidth);

  // Bitrate above automatic threshold. Expect automatic.
  config.bitrate_bps = absl::optional<int>(12001);
  bandwidth = AudioEncoderOpusImpl::GetNewBandwidth(config, inst);
  EXPECT_EQ(absl::optional<int>(OPUS_AUTO), bandwidth);

  EXPECT_EQ(0, WebRtcOpus_EncoderFree(inst));
}

TEST_P(AudioEncoderOpusTest, EmptyConfigDoesNotAffectEncoderSettings) {
  auto states = CreateCodec(sample_rate_hz_, 2);
  states->encoder->EnableAudioNetworkAdaptor("", nullptr);

  auto config = CreateEncoderRuntimeConfig();
  AudioEncoderRuntimeConfig empty_config;

  EXPECT_CALL(*states->mock_audio_network_adaptor, GetEncoderRuntimeConfig())
      .WillOnce(Return(config))
      .WillOnce(Return(empty_config));

  constexpr size_t kOverhead = 64;
  EXPECT_CALL(*states->mock_audio_network_adaptor, SetOverhead(kOverhead))
      .Times(2);
  states->encoder->OnReceivedOverhead(kOverhead);
  states->encoder->OnReceivedOverhead(kOverhead);

  CheckEncoderRuntimeConfig(states->encoder.get(), config);
}

TEST_P(AudioEncoderOpusTest, UpdateUplinkBandwidthInAudioNetworkAdaptor) {
  test::ScopedFieldTrials override_field_trials(
      "WebRTC-Audio-StableTargetAdaptation/Disabled/");
  auto states = CreateCodec(sample_rate_hz_, 2);
  states->encoder->EnableAudioNetworkAdaptor("", nullptr);
  const size_t opus_rate_khz = rtc::CheckedDivExact(sample_rate_hz_, 1000);
  const std::vector<int16_t> audio(opus_rate_khz * 10 * 2, 0);
  rtc::Buffer encoded;
  EXPECT_CALL(*states->mock_bitrate_smoother, GetAverage())
      .WillOnce(Return(50000));
  EXPECT_CALL(*states->mock_audio_network_adaptor, SetUplinkBandwidth(50000));
  states->encoder->Encode(
      0, rtc::ArrayView<const int16_t>(audio.data(), audio.size()), &encoded);

  // Repeat update uplink bandwidth tests.
  for (int i = 0; i < 5; i++) {
    // Don't update till it is time to update again.
    states->fake_clock->AdvanceTime(TimeDelta::Millis(
        states->config.uplink_bandwidth_update_interval_ms - 1));
    states->encoder->Encode(
        0, rtc::ArrayView<const int16_t>(audio.data(), audio.size()), &encoded);

    // Update when it is time to update.
    EXPECT_CALL(*states->mock_bitrate_smoother, GetAverage())
        .WillOnce(Return(40000));
    EXPECT_CALL(*states->mock_audio_network_adaptor, SetUplinkBandwidth(40000));
    states->fake_clock->AdvanceTime(TimeDelta::Millis(1));
    states->encoder->Encode(
        0, rtc::ArrayView<const int16_t>(audio.data(), audio.size()), &encoded);
  }
}

TEST_P(AudioEncoderOpusTest, EncodeAtMinBitrate) {
  auto states = CreateCodec(sample_rate_hz_, 1);
  constexpr int kNumPacketsToEncode = 2;
  auto audio_frames =
      Create10msAudioBlocks(states->encoder, kNumPacketsToEncode * 20);
  ASSERT_TRUE(audio_frames) << "Create10msAudioBlocks failed";
  rtc::Buffer encoded;
  uint32_t rtp_timestamp = 12345;  // Just a number not important to this test.

  states->encoder->OnReceivedUplinkBandwidth(0, absl::nullopt);
  for (int packet_index = 0; packet_index < kNumPacketsToEncode;
       packet_index++) {
    // Make sure we are not encoding before we have enough data for
    // a 20ms packet.
    for (int index = 0; index < 1; index++) {
      states->encoder->Encode(rtp_timestamp, audio_frames->GetNextBlock(),
                              &encoded);
      EXPECT_EQ(0u, encoded.size());
    }

    // Should encode now.
    states->encoder->Encode(rtp_timestamp, audio_frames->GetNextBlock(),
                            &encoded);
    EXPECT_GT(encoded.size(), 0u);
    encoded.Clear();
  }
}

TEST(AudioEncoderOpusTest, TestConfigDefaults) {
  const auto config_opt = AudioEncoderOpus::SdpToConfig({"opus", 48000, 2});
  ASSERT_TRUE(config_opt);
  EXPECT_EQ(48000, config_opt->max_playback_rate_hz);
  EXPECT_EQ(1u, config_opt->num_channels);
  EXPECT_FALSE(config_opt->fec_enabled);
  EXPECT_FALSE(config_opt->dtx_enabled);
  EXPECT_EQ(20, config_opt->frame_size_ms);
}

TEST(AudioEncoderOpusTest, TestConfigFromParams) {
  const auto config1 = CreateConfigWithParameters({{"stereo", "0"}});
  EXPECT_EQ(1U, config1.num_channels);

  const auto config2 = CreateConfigWithParameters({{"stereo", "1"}});
  EXPECT_EQ(2U, config2.num_channels);

  const auto config3 = CreateConfigWithParameters({{"useinbandfec", "0"}});
  EXPECT_FALSE(config3.fec_enabled);

  const auto config4 = CreateConfigWithParameters({{"useinbandfec", "1"}});
  EXPECT_TRUE(config4.fec_enabled);

  const auto config5 = CreateConfigWithParameters({{"usedtx", "0"}});
  EXPECT_FALSE(config5.dtx_enabled);

  const auto config6 = CreateConfigWithParameters({{"usedtx", "1"}});
  EXPECT_TRUE(config6.dtx_enabled);

  const auto config7 = CreateConfigWithParameters({{"cbr", "0"}});
  EXPECT_FALSE(config7.cbr_enabled);

  const auto config8 = CreateConfigWithParameters({{"cbr", "1"}});
  EXPECT_TRUE(config8.cbr_enabled);

  const auto config9 =
      CreateConfigWithParameters({{"maxplaybackrate", "12345"}});
  EXPECT_EQ(12345, config9.max_playback_rate_hz);

  const auto config10 =
      CreateConfigWithParameters({{"maxaveragebitrate", "96000"}});
  EXPECT_EQ(96000, config10.bitrate_bps);

  const auto config11 = CreateConfigWithParameters({{"maxptime", "40"}});
  for (int frame_length : config11.supported_frame_lengths_ms) {
    EXPECT_LE(frame_length, 40);
  }

  const auto config12 = CreateConfigWithParameters({{"minptime", "40"}});
  for (int frame_length : config12.supported_frame_lengths_ms) {
    EXPECT_GE(frame_length, 40);
  }

  const auto config13 = CreateConfigWithParameters({{"ptime", "40"}});
  EXPECT_EQ(40, config13.frame_size_ms);

  constexpr int kMinSupportedFrameLength = 10;
  constexpr int kMaxSupportedFrameLength =
      WEBRTC_OPUS_SUPPORT_120MS_PTIME ? 120 : 60;

  const auto config14 = CreateConfigWithParameters({{"ptime", "1"}});
  EXPECT_EQ(kMinSupportedFrameLength, config14.frame_size_ms);

  const auto config15 = CreateConfigWithParameters({{"ptime", "2000"}});
  EXPECT_EQ(kMaxSupportedFrameLength, config15.frame_size_ms);
}

TEST(AudioEncoderOpusTest, TestConfigFromInvalidParams) {
  const webrtc::SdpAudioFormat format("opus", 48000, 2);
  const auto default_config = *AudioEncoderOpus::SdpToConfig(format);
#if WEBRTC_OPUS_SUPPORT_120MS_PTIME
  const std::vector<int> default_supported_frame_lengths_ms({20, 40, 60, 120});
#else
  const std::vector<int> default_supported_frame_lengths_ms({20, 40, 60});
#endif

  AudioEncoderOpusConfig config;
  config = CreateConfigWithParameters({{"stereo", "invalid"}});
  EXPECT_EQ(default_config.num_channels, config.num_channels);

  config = CreateConfigWithParameters({{"useinbandfec", "invalid"}});
  EXPECT_EQ(default_config.fec_enabled, config.fec_enabled);

  config = CreateConfigWithParameters({{"usedtx", "invalid"}});
  EXPECT_EQ(default_config.dtx_enabled, config.dtx_enabled);

  config = CreateConfigWithParameters({{"cbr", "invalid"}});
  EXPECT_EQ(default_config.dtx_enabled, config.dtx_enabled);

  config = CreateConfigWithParameters({{"maxplaybackrate", "0"}});
  EXPECT_EQ(default_config.max_playback_rate_hz, config.max_playback_rate_hz);

  config = CreateConfigWithParameters({{"maxplaybackrate", "-23"}});
  EXPECT_EQ(default_config.max_playback_rate_hz, config.max_playback_rate_hz);

  config = CreateConfigWithParameters({{"maxplaybackrate", "not a number!"}});
  EXPECT_EQ(default_config.max_playback_rate_hz, config.max_playback_rate_hz);

  config = CreateConfigWithParameters({{"maxaveragebitrate", "0"}});
  EXPECT_EQ(6000, config.bitrate_bps);

  config = CreateConfigWithParameters({{"maxaveragebitrate", "-1000"}});
  EXPECT_EQ(6000, config.bitrate_bps);

  config = CreateConfigWithParameters({{"maxaveragebitrate", "1024000"}});
  EXPECT_EQ(510000, config.bitrate_bps);

  config = CreateConfigWithParameters({{"maxaveragebitrate", "not a number!"}});
  EXPECT_EQ(default_config.bitrate_bps, config.bitrate_bps);

  config = CreateConfigWithParameters({{"maxptime", "invalid"}});
  EXPECT_EQ(default_supported_frame_lengths_ms,
            config.supported_frame_lengths_ms);

  config = CreateConfigWithParameters({{"minptime", "invalid"}});
  EXPECT_EQ(default_supported_frame_lengths_ms,
            config.supported_frame_lengths_ms);

  config = CreateConfigWithParameters({{"ptime", "invalid"}});
  EXPECT_EQ(default_supported_frame_lengths_ms,
            config.supported_frame_lengths_ms);
}

TEST(AudioEncoderOpusTest, GetFrameLenghtRange) {
  AudioEncoderOpusConfig config =
      CreateConfigWithParameters({{"maxptime", "10"}, {"ptime", "10"}});
  std::unique_ptr<AudioEncoder> encoder =
      AudioEncoderOpus::MakeAudioEncoder(config, kDefaultOpusPayloadType);
  auto ptime = webrtc::TimeDelta::Millis(10);
  absl::optional<std::pair<webrtc::TimeDelta, webrtc::TimeDelta>> range = {
      {ptime, ptime}};
  EXPECT_EQ(encoder->GetFrameLengthRange(), range);
}

// Test that bitrate will be overridden by the "maxaveragebitrate" parameter.
// Also test that the "maxaveragebitrate" can't be set to values outside the
// range of 6000 and 510000
TEST(AudioEncoderOpusTest, SetSendCodecOpusMaxAverageBitrate) {
  // Ignore if less than 6000.
  const auto config1 = AudioEncoderOpus::SdpToConfig(
      {"opus", 48000, 2, {{"maxaveragebitrate", "5999"}}});
  EXPECT_EQ(6000, config1->bitrate_bps);

  // Ignore if larger than 510000.
  const auto config2 = AudioEncoderOpus::SdpToConfig(
      {"opus", 48000, 2, {{"maxaveragebitrate", "510001"}}});
  EXPECT_EQ(510000, config2->bitrate_bps);

  const auto config3 = AudioEncoderOpus::SdpToConfig(
      {"opus", 48000, 2, {{"maxaveragebitrate", "200000"}}});
  EXPECT_EQ(200000, config3->bitrate_bps);
}

// Test maxplaybackrate <= 8000 triggers Opus narrow band mode.
TEST(AudioEncoderOpusTest, SetMaxPlaybackRateNb) {
  auto config = CreateConfigWithParameters({{"maxplaybackrate", "8000"}});
  EXPECT_EQ(8000, config.max_playback_rate_hz);
  EXPECT_EQ(12000, config.bitrate_bps);

  config = CreateConfigWithParameters(
      {{"maxplaybackrate", "8000"}, {"stereo", "1"}});
  EXPECT_EQ(8000, config.max_playback_rate_hz);
  EXPECT_EQ(24000, config.bitrate_bps);
}

// Test 8000 < maxplaybackrate <= 12000 triggers Opus medium band mode.
TEST(AudioEncoderOpusTest, SetMaxPlaybackRateMb) {
  auto config = CreateConfigWithParameters({{"maxplaybackrate", "8001"}});
  EXPECT_EQ(8001, config.max_playback_rate_hz);
  EXPECT_EQ(20000, config.bitrate_bps);

  config = CreateConfigWithParameters(
      {{"maxplaybackrate", "8001"}, {"stereo", "1"}});
  EXPECT_EQ(8001, config.max_playback_rate_hz);
  EXPECT_EQ(40000, config.bitrate_bps);
}

// Test 12000 < maxplaybackrate <= 16000 triggers Opus wide band mode.
TEST(AudioEncoderOpusTest, SetMaxPlaybackRateWb) {
  auto config = CreateConfigWithParameters({{"maxplaybackrate", "12001"}});
  EXPECT_EQ(12001, config.max_playback_rate_hz);
  EXPECT_EQ(20000, config.bitrate_bps);

  config = CreateConfigWithParameters(
      {{"maxplaybackrate", "12001"}, {"stereo", "1"}});
  EXPECT_EQ(12001, config.max_playback_rate_hz);
  EXPECT_EQ(40000, config.bitrate_bps);
}

// Test 16000 < maxplaybackrate <= 24000 triggers Opus super wide band mode.
TEST(AudioEncoderOpusTest, SetMaxPlaybackRateSwb) {
  auto config = CreateConfigWithParameters({{"maxplaybackrate", "16001"}});
  EXPECT_EQ(16001, config.max_playback_rate_hz);
  EXPECT_EQ(32000, config.bitrate_bps);

  config = CreateConfigWithParameters(
      {{"maxplaybackrate", "16001"}, {"stereo", "1"}});
  EXPECT_EQ(16001, config.max_playback_rate_hz);
  EXPECT_EQ(64000, config.bitrate_bps);
}

// Test 24000 < maxplaybackrate triggers Opus full band mode.
TEST(AudioEncoderOpusTest, SetMaxPlaybackRateFb) {
  auto config = CreateConfigWithParameters({{"maxplaybackrate", "24001"}});
  EXPECT_EQ(24001, config.max_playback_rate_hz);
  EXPECT_EQ(32000, config.bitrate_bps);

  config = CreateConfigWithParameters(
      {{"maxplaybackrate", "24001"}, {"stereo", "1"}});
  EXPECT_EQ(24001, config.max_playback_rate_hz);
  EXPECT_EQ(64000, config.bitrate_bps);
}

TEST_P(AudioEncoderOpusTest, OpusFlagDtxAsNonSpeech) {
  // Create encoder with DTX enabled.
  AudioEncoderOpusConfig config;
  config.dtx_enabled = true;
  config.sample_rate_hz = sample_rate_hz_;
  constexpr int payload_type = 17;
  const auto encoder = AudioEncoderOpus::MakeAudioEncoder(config, payload_type);

  // Open file containing speech and silence.
  const std::string kInputFileName =
      webrtc::test::ResourcePath("audio_coding/testfile32kHz", "pcm");
  test::AudioLoop audio_loop;
  // Use the file as if it were sampled at our desired input rate.
  const size_t max_loop_length_samples =
      sample_rate_hz_ * 10;  // Max 10 second loop.
  const size_t input_block_size_samples =
      10 * sample_rate_hz_ / 1000;  // 10 ms.
  EXPECT_TRUE(audio_loop.Init(kInputFileName, max_loop_length_samples,
                              input_block_size_samples));

  // Encode.
  AudioEncoder::EncodedInfo info;
  rtc::Buffer encoded(500);
  int nonspeech_frames = 0;
  int max_nonspeech_frames = 0;
  int dtx_frames = 0;
  int max_dtx_frames = 0;
  uint32_t rtp_timestamp = 0u;
  for (size_t i = 0; i < 500; ++i) {
    encoded.Clear();

    // Every second call to the encoder will generate an Opus packet.
    for (int j = 0; j < 2; j++) {
      info =
          encoder->Encode(rtp_timestamp, audio_loop.GetNextBlock(), &encoded);
      rtp_timestamp += input_block_size_samples;
    }

    // Bookkeeping of number of DTX frames.
    if (info.encoded_bytes <= 2) {
      ++dtx_frames;
    } else {
      if (dtx_frames > max_dtx_frames)
        max_dtx_frames = dtx_frames;
      dtx_frames = 0;
    }

    // Bookkeeping of number of non-speech frames.
    if (info.speech == 0) {
      ++nonspeech_frames;
    } else {
      if (nonspeech_frames > max_nonspeech_frames)
        max_nonspeech_frames = nonspeech_frames;
      nonspeech_frames = 0;
    }
  }

  // Maximum number of consecutive non-speech packets should exceed 15.
  EXPECT_GT(max_nonspeech_frames, 15);
}

TEST(AudioEncoderOpusTest, OpusDtxFilteringHighEnergyRefreshPackets) {
  test::ScopedFieldTrials override_field_trials(
      "WebRTC-Audio-OpusAvoidNoisePumpingDuringDtx/Enabled/");
  const std::string kInputFileName =
      webrtc::test::ResourcePath("audio_coding/testfile16kHz", "pcm");
  constexpr int kSampleRateHz = 16000;
  AudioEncoderOpusConfig config;
  config.dtx_enabled = true;
  config.sample_rate_hz = kSampleRateHz;
  constexpr int payload_type = 17;
  const auto encoder = AudioEncoderOpus::MakeAudioEncoder(config, payload_type);
  test::AudioLoop audio_loop;
  constexpr size_t kMaxLoopLengthSaples = kSampleRateHz * 11.6f;
  constexpr size_t kInputBlockSizeSamples = kSampleRateHz / 100;
  EXPECT_TRUE(audio_loop.Init(kInputFileName, kMaxLoopLengthSaples,
                              kInputBlockSizeSamples));
  AudioEncoder::EncodedInfo info;
  rtc::Buffer encoded(500);
  // Encode the audio file and store the last part that corresponds to silence.
  constexpr size_t kSilenceDurationSamples = kSampleRateHz * 0.2f;
  std::array<int16_t, kSilenceDurationSamples> silence;
  uint32_t rtp_timestamp = 0;
  bool last_packet_dtx_frame = false;
  bool opus_entered_dtx = false;
  bool silence_filled = false;
  size_t timestamp_start_silence = 0;
  while (!silence_filled && rtp_timestamp < kMaxLoopLengthSaples) {
    encoded.Clear();
    // Every second call to the encoder will generate an Opus packet.
    for (int j = 0; j < 2; j++) {
      auto next_frame = audio_loop.GetNextBlock();
      info = encoder->Encode(rtp_timestamp, next_frame, &encoded);
      if (opus_entered_dtx) {
        size_t silence_frame_start = rtp_timestamp - timestamp_start_silence;
        silence_filled = silence_frame_start >= kSilenceDurationSamples;
        if (!silence_filled) {
          std::copy(next_frame.begin(), next_frame.end(),
                    silence.begin() + silence_frame_start);
        }
      }
      rtp_timestamp += kInputBlockSizeSamples;
    }
    EXPECT_TRUE(info.encoded_bytes > 0 || last_packet_dtx_frame);
    last_packet_dtx_frame = info.encoded_bytes > 0 ? info.encoded_bytes <= 2
                                                   : last_packet_dtx_frame;
    if (info.encoded_bytes <= 2 && !opus_entered_dtx) {
      timestamp_start_silence = rtp_timestamp;
    }
    opus_entered_dtx = info.encoded_bytes <= 2;
  }

  EXPECT_TRUE(silence_filled);
  // The copied 200 ms of silence is used for creating 6 bursts that are fed to
  // the encoder, the first three ones with a larger energy and the last three
  // with a lower energy. This test verifies that the encoder just sends refresh
  // DTX packets during the last bursts.
  int number_non_empty_packets_during_increase = 0;
  int number_non_empty_packets_during_decrease = 0;
  for (size_t burst = 0; burst < 6; ++burst) {
    uint32_t rtp_timestamp_start = rtp_timestamp;
    const bool increase_noise = burst < 3;
    const float gain = increase_noise ? 1.4f : 0.0f;
    while (rtp_timestamp < rtp_timestamp_start + kSilenceDurationSamples) {
      encoded.Clear();
      // Every second call to the encoder will generate an Opus packet.
      for (int j = 0; j < 2; j++) {
        std::array<int16_t, kInputBlockSizeSamples> silence_frame;
        size_t silence_frame_start = rtp_timestamp - rtp_timestamp_start;
        std::transform(
            silence.begin() + silence_frame_start,
            silence.begin() + silence_frame_start + kInputBlockSizeSamples,
            silence_frame.begin(), [gain](float s) { return gain * s; });
        info = encoder->Encode(rtp_timestamp, silence_frame, &encoded);
        rtp_timestamp += kInputBlockSizeSamples;
      }
      EXPECT_TRUE(info.encoded_bytes > 0 || last_packet_dtx_frame);
      last_packet_dtx_frame = info.encoded_bytes > 0 ? info.encoded_bytes <= 2
                                                     : last_packet_dtx_frame;
      // Tracking the number of non empty packets.
      if (increase_noise && info.encoded_bytes > 2) {
        number_non_empty_packets_during_increase++;
      }
      if (!increase_noise && info.encoded_bytes > 2) {
        number_non_empty_packets_during_decrease++;
      }
    }
  }
  // Check that the refresh DTX packets are just sent during the decrease energy
  // region.
  EXPECT_EQ(number_non_empty_packets_during_increase, 0);
  EXPECT_GT(number_non_empty_packets_during_decrease, 0);
}

}  // namespace webrtc
