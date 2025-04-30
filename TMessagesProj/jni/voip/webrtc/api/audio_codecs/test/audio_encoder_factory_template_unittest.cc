/*
 *  Copyright (c) 2017 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "api/audio_codecs/audio_encoder_factory_template.h"

#include <memory>

#include "api/audio_codecs/L16/audio_encoder_L16.h"
#include "api/audio_codecs/g711/audio_encoder_g711.h"
#include "api/audio_codecs/g722/audio_encoder_g722.h"
#include "api/audio_codecs/ilbc/audio_encoder_ilbc.h"
#include "api/audio_codecs/opus/audio_encoder_opus.h"
#include "test/gmock.h"
#include "test/gtest.h"
#include "test/mock_audio_encoder.h"
#include "test/scoped_key_value_config.h"

namespace webrtc {

namespace {

struct BogusParams {
  static SdpAudioFormat AudioFormat() { return {"bogus", 8000, 1}; }
  static AudioCodecInfo CodecInfo() { return {8000, 1, 12345}; }
};

struct ShamParams {
  static SdpAudioFormat AudioFormat() {
    return {"sham", 16000, 2, {{"param", "value"}}};
  }
  static AudioCodecInfo CodecInfo() { return {16000, 2, 23456}; }
};

template <typename Params>
struct AudioEncoderFakeApi {
  struct Config {
    SdpAudioFormat audio_format;
  };

  static absl::optional<Config> SdpToConfig(
      const SdpAudioFormat& audio_format) {
    if (Params::AudioFormat() == audio_format) {
      Config config = {audio_format};
      return config;
    } else {
      return absl::nullopt;
    }
  }

  static void AppendSupportedEncoders(std::vector<AudioCodecSpec>* specs) {
    specs->push_back({Params::AudioFormat(), Params::CodecInfo()});
  }

  static AudioCodecInfo QueryAudioEncoder(const Config&) {
    return Params::CodecInfo();
  }

  static std::unique_ptr<AudioEncoder> MakeAudioEncoder(
      const Config&,
      int payload_type,
      absl::optional<AudioCodecPairId> /*codec_pair_id*/ = absl::nullopt) {
    auto enc = std::make_unique<testing::StrictMock<MockAudioEncoder>>();
    EXPECT_CALL(*enc, SampleRateHz())
        .WillOnce(::testing::Return(Params::CodecInfo().sample_rate_hz));
    return std::move(enc);
  }
};

}  // namespace

TEST(AudioEncoderFactoryTemplateTest, NoEncoderTypes) {
  test::ScopedKeyValueConfig field_trials;
  rtc::scoped_refptr<AudioEncoderFactory> factory(
      rtc::make_ref_counted<
          audio_encoder_factory_template_impl::AudioEncoderFactoryT<>>(
          &field_trials));
  EXPECT_THAT(factory->GetSupportedEncoders(), ::testing::IsEmpty());
  EXPECT_EQ(absl::nullopt, factory->QueryAudioEncoder({"foo", 8000, 1}));
  EXPECT_EQ(nullptr,
            factory->MakeAudioEncoder(17, {"bar", 16000, 1}, absl::nullopt));
}

TEST(AudioEncoderFactoryTemplateTest, OneEncoderType) {
  auto factory = CreateAudioEncoderFactory<AudioEncoderFakeApi<BogusParams>>();
  EXPECT_THAT(factory->GetSupportedEncoders(),
              ::testing::ElementsAre(
                  AudioCodecSpec{{"bogus", 8000, 1}, {8000, 1, 12345}}));
  EXPECT_EQ(absl::nullopt, factory->QueryAudioEncoder({"foo", 8000, 1}));
  EXPECT_EQ(AudioCodecInfo(8000, 1, 12345),
            factory->QueryAudioEncoder({"bogus", 8000, 1}));
  EXPECT_EQ(nullptr,
            factory->MakeAudioEncoder(17, {"bar", 16000, 1}, absl::nullopt));
  auto enc = factory->MakeAudioEncoder(17, {"bogus", 8000, 1}, absl::nullopt);
  ASSERT_NE(nullptr, enc);
  EXPECT_EQ(8000, enc->SampleRateHz());
}

TEST(AudioEncoderFactoryTemplateTest, TwoEncoderTypes) {
  auto factory = CreateAudioEncoderFactory<AudioEncoderFakeApi<BogusParams>,
                                           AudioEncoderFakeApi<ShamParams>>();
  EXPECT_THAT(factory->GetSupportedEncoders(),
              ::testing::ElementsAre(
                  AudioCodecSpec{{"bogus", 8000, 1}, {8000, 1, 12345}},
                  AudioCodecSpec{{"sham", 16000, 2, {{"param", "value"}}},
                                 {16000, 2, 23456}}));
  EXPECT_EQ(absl::nullopt, factory->QueryAudioEncoder({"foo", 8000, 1}));
  EXPECT_EQ(AudioCodecInfo(8000, 1, 12345),
            factory->QueryAudioEncoder({"bogus", 8000, 1}));
  EXPECT_EQ(
      AudioCodecInfo(16000, 2, 23456),
      factory->QueryAudioEncoder({"sham", 16000, 2, {{"param", "value"}}}));
  EXPECT_EQ(nullptr,
            factory->MakeAudioEncoder(17, {"bar", 16000, 1}, absl::nullopt));
  auto enc1 = factory->MakeAudioEncoder(17, {"bogus", 8000, 1}, absl::nullopt);
  ASSERT_NE(nullptr, enc1);
  EXPECT_EQ(8000, enc1->SampleRateHz());
  EXPECT_EQ(nullptr,
            factory->MakeAudioEncoder(17, {"sham", 16000, 2}, absl::nullopt));
  auto enc2 = factory->MakeAudioEncoder(
      17, {"sham", 16000, 2, {{"param", "value"}}}, absl::nullopt);
  ASSERT_NE(nullptr, enc2);
  EXPECT_EQ(16000, enc2->SampleRateHz());
}

TEST(AudioEncoderFactoryTemplateTest, G711) {
  auto factory = CreateAudioEncoderFactory<AudioEncoderG711>();
  EXPECT_THAT(factory->GetSupportedEncoders(),
              ::testing::ElementsAre(
                  AudioCodecSpec{{"PCMU", 8000, 1}, {8000, 1, 64000}},
                  AudioCodecSpec{{"PCMA", 8000, 1}, {8000, 1, 64000}}));
  EXPECT_EQ(absl::nullopt, factory->QueryAudioEncoder({"PCMA", 16000, 1}));
  EXPECT_EQ(AudioCodecInfo(8000, 1, 64000),
            factory->QueryAudioEncoder({"PCMA", 8000, 1}));
  EXPECT_EQ(nullptr,
            factory->MakeAudioEncoder(17, {"PCMU", 16000, 1}, absl::nullopt));
  auto enc1 = factory->MakeAudioEncoder(17, {"PCMU", 8000, 1}, absl::nullopt);
  ASSERT_NE(nullptr, enc1);
  EXPECT_EQ(8000, enc1->SampleRateHz());
  auto enc2 = factory->MakeAudioEncoder(17, {"PCMA", 8000, 1}, absl::nullopt);
  ASSERT_NE(nullptr, enc2);
  EXPECT_EQ(8000, enc2->SampleRateHz());
}

TEST(AudioEncoderFactoryTemplateTest, G722) {
  auto factory = CreateAudioEncoderFactory<AudioEncoderG722>();
  EXPECT_THAT(factory->GetSupportedEncoders(),
              ::testing::ElementsAre(
                  AudioCodecSpec{{"G722", 8000, 1}, {16000, 1, 64000}}));
  EXPECT_EQ(absl::nullopt, factory->QueryAudioEncoder({"foo", 8000, 1}));
  EXPECT_EQ(AudioCodecInfo(16000, 1, 64000),
            factory->QueryAudioEncoder({"G722", 8000, 1}));
  EXPECT_EQ(nullptr,
            factory->MakeAudioEncoder(17, {"bar", 16000, 1}, absl::nullopt));
  auto enc = factory->MakeAudioEncoder(17, {"G722", 8000, 1}, absl::nullopt);
  ASSERT_NE(nullptr, enc);
  EXPECT_EQ(16000, enc->SampleRateHz());
}

TEST(AudioEncoderFactoryTemplateTest, Ilbc) {
  auto factory = CreateAudioEncoderFactory<AudioEncoderIlbc>();
  EXPECT_THAT(factory->GetSupportedEncoders(),
              ::testing::ElementsAre(
                  AudioCodecSpec{{"ILBC", 8000, 1}, {8000, 1, 13333}}));
  EXPECT_EQ(absl::nullopt, factory->QueryAudioEncoder({"foo", 8000, 1}));
  EXPECT_EQ(AudioCodecInfo(8000, 1, 13333),
            factory->QueryAudioEncoder({"ilbc", 8000, 1}));
  EXPECT_EQ(nullptr,
            factory->MakeAudioEncoder(17, {"bar", 8000, 1}, absl::nullopt));
  auto enc = factory->MakeAudioEncoder(17, {"ilbc", 8000, 1}, absl::nullopt);
  ASSERT_NE(nullptr, enc);
  EXPECT_EQ(8000, enc->SampleRateHz());
}

TEST(AudioEncoderFactoryTemplateTest, L16) {
  auto factory = CreateAudioEncoderFactory<AudioEncoderL16>();
  EXPECT_THAT(
      factory->GetSupportedEncoders(),
      ::testing::ElementsAre(
          AudioCodecSpec{{"L16", 8000, 1}, {8000, 1, 8000 * 16}},
          AudioCodecSpec{{"L16", 16000, 1}, {16000, 1, 16000 * 16}},
          AudioCodecSpec{{"L16", 32000, 1}, {32000, 1, 32000 * 16}},
          AudioCodecSpec{{"L16", 8000, 2}, {8000, 2, 8000 * 16 * 2}},
          AudioCodecSpec{{"L16", 16000, 2}, {16000, 2, 16000 * 16 * 2}},
          AudioCodecSpec{{"L16", 32000, 2}, {32000, 2, 32000 * 16 * 2}}));
  EXPECT_EQ(absl::nullopt, factory->QueryAudioEncoder({"L16", 8000, 0}));
  EXPECT_EQ(AudioCodecInfo(48000, 1, 48000 * 16),
            factory->QueryAudioEncoder({"L16", 48000, 1}));
  EXPECT_EQ(nullptr,
            factory->MakeAudioEncoder(17, {"L16", 8000, 0}, absl::nullopt));
  auto enc = factory->MakeAudioEncoder(17, {"L16", 48000, 2}, absl::nullopt);
  ASSERT_NE(nullptr, enc);
  EXPECT_EQ(48000, enc->SampleRateHz());
}

TEST(AudioEncoderFactoryTemplateTest, Opus) {
  auto factory = CreateAudioEncoderFactory<AudioEncoderOpus>();
  AudioCodecInfo info = {48000, 1, 32000, 6000, 510000};
  info.allow_comfort_noise = false;
  info.supports_network_adaption = true;
  EXPECT_THAT(
      factory->GetSupportedEncoders(),
      ::testing::ElementsAre(AudioCodecSpec{
          {"opus", 48000, 2, {{"minptime", "10"}, {"useinbandfec", "1"}}},
          info}));
  EXPECT_EQ(absl::nullopt, factory->QueryAudioEncoder({"foo", 8000, 1}));
  EXPECT_EQ(
      info,
      factory->QueryAudioEncoder(
          {"opus", 48000, 2, {{"minptime", "10"}, {"useinbandfec", "1"}}}));
  EXPECT_EQ(nullptr,
            factory->MakeAudioEncoder(17, {"bar", 16000, 1}, absl::nullopt));
  auto enc = factory->MakeAudioEncoder(17, {"opus", 48000, 2}, absl::nullopt);
  ASSERT_NE(nullptr, enc);
  EXPECT_EQ(48000, enc->SampleRateHz());
}

}  // namespace webrtc
