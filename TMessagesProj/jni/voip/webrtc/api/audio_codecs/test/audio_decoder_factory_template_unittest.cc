/*
 *  Copyright (c) 2017 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "api/audio_codecs/audio_decoder_factory_template.h"

#include <memory>

#include "api/audio_codecs/L16/audio_decoder_L16.h"
#include "api/audio_codecs/g711/audio_decoder_g711.h"
#include "api/audio_codecs/g722/audio_decoder_g722.h"
#include "api/audio_codecs/ilbc/audio_decoder_ilbc.h"
#include "api/audio_codecs/opus/audio_decoder_opus.h"
#include "test/gmock.h"
#include "test/gtest.h"
#include "test/mock_audio_decoder.h"
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
struct AudioDecoderFakeApi {
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

  static void AppendSupportedDecoders(std::vector<AudioCodecSpec>* specs) {
    specs->push_back({Params::AudioFormat(), Params::CodecInfo()});
  }

  static AudioCodecInfo QueryAudioDecoder(const Config&) {
    return Params::CodecInfo();
  }

  static std::unique_ptr<AudioDecoder> MakeAudioDecoder(
      const Config&,
      absl::optional<AudioCodecPairId> /*codec_pair_id*/ = absl::nullopt) {
    auto dec = std::make_unique<testing::StrictMock<MockAudioDecoder>>();
    EXPECT_CALL(*dec, SampleRateHz())
        .WillOnce(::testing::Return(Params::CodecInfo().sample_rate_hz));
    EXPECT_CALL(*dec, Die());
    return std::move(dec);
  }
};

}  // namespace

TEST(AudioDecoderFactoryTemplateTest, NoDecoderTypes) {
  test::ScopedKeyValueConfig field_trials;
  rtc::scoped_refptr<AudioDecoderFactory> factory(
      rtc::make_ref_counted<
          audio_decoder_factory_template_impl::AudioDecoderFactoryT<>>(
          &field_trials));
  EXPECT_THAT(factory->GetSupportedDecoders(), ::testing::IsEmpty());
  EXPECT_FALSE(factory->IsSupportedDecoder({"foo", 8000, 1}));
  EXPECT_EQ(nullptr,
            factory->MakeAudioDecoder({"bar", 16000, 1}, absl::nullopt));
}

TEST(AudioDecoderFactoryTemplateTest, OneDecoderType) {
  auto factory = CreateAudioDecoderFactory<AudioDecoderFakeApi<BogusParams>>();
  EXPECT_THAT(factory->GetSupportedDecoders(),
              ::testing::ElementsAre(
                  AudioCodecSpec{{"bogus", 8000, 1}, {8000, 1, 12345}}));
  EXPECT_FALSE(factory->IsSupportedDecoder({"foo", 8000, 1}));
  EXPECT_TRUE(factory->IsSupportedDecoder({"bogus", 8000, 1}));
  EXPECT_EQ(nullptr,
            factory->MakeAudioDecoder({"bar", 16000, 1}, absl::nullopt));
  auto dec = factory->MakeAudioDecoder({"bogus", 8000, 1}, absl::nullopt);
  ASSERT_NE(nullptr, dec);
  EXPECT_EQ(8000, dec->SampleRateHz());
}

TEST(AudioDecoderFactoryTemplateTest, TwoDecoderTypes) {
  auto factory = CreateAudioDecoderFactory<AudioDecoderFakeApi<BogusParams>,
                                           AudioDecoderFakeApi<ShamParams>>();
  EXPECT_THAT(factory->GetSupportedDecoders(),
              ::testing::ElementsAre(
                  AudioCodecSpec{{"bogus", 8000, 1}, {8000, 1, 12345}},
                  AudioCodecSpec{{"sham", 16000, 2, {{"param", "value"}}},
                                 {16000, 2, 23456}}));
  EXPECT_FALSE(factory->IsSupportedDecoder({"foo", 8000, 1}));
  EXPECT_TRUE(factory->IsSupportedDecoder({"bogus", 8000, 1}));
  EXPECT_TRUE(
      factory->IsSupportedDecoder({"sham", 16000, 2, {{"param", "value"}}}));
  EXPECT_EQ(nullptr,
            factory->MakeAudioDecoder({"bar", 16000, 1}, absl::nullopt));
  auto dec1 = factory->MakeAudioDecoder({"bogus", 8000, 1}, absl::nullopt);
  ASSERT_NE(nullptr, dec1);
  EXPECT_EQ(8000, dec1->SampleRateHz());
  EXPECT_EQ(nullptr,
            factory->MakeAudioDecoder({"sham", 16000, 2}, absl::nullopt));
  auto dec2 = factory->MakeAudioDecoder(
      {"sham", 16000, 2, {{"param", "value"}}}, absl::nullopt);
  ASSERT_NE(nullptr, dec2);
  EXPECT_EQ(16000, dec2->SampleRateHz());
}

TEST(AudioDecoderFactoryTemplateTest, G711) {
  auto factory = CreateAudioDecoderFactory<AudioDecoderG711>();
  EXPECT_THAT(factory->GetSupportedDecoders(),
              ::testing::ElementsAre(
                  AudioCodecSpec{{"PCMU", 8000, 1}, {8000, 1, 64000}},
                  AudioCodecSpec{{"PCMA", 8000, 1}, {8000, 1, 64000}}));
  EXPECT_FALSE(factory->IsSupportedDecoder({"G711", 8000, 1}));
  EXPECT_TRUE(factory->IsSupportedDecoder({"PCMU", 8000, 1}));
  EXPECT_TRUE(factory->IsSupportedDecoder({"pcma", 8000, 1}));
  EXPECT_EQ(nullptr,
            factory->MakeAudioDecoder({"pcmu", 16000, 1}, absl::nullopt));
  auto dec1 = factory->MakeAudioDecoder({"pcmu", 8000, 1}, absl::nullopt);
  ASSERT_NE(nullptr, dec1);
  EXPECT_EQ(8000, dec1->SampleRateHz());
  auto dec2 = factory->MakeAudioDecoder({"PCMA", 8000, 1}, absl::nullopt);
  ASSERT_NE(nullptr, dec2);
  EXPECT_EQ(8000, dec2->SampleRateHz());
}

TEST(AudioDecoderFactoryTemplateTest, G722) {
  auto factory = CreateAudioDecoderFactory<AudioDecoderG722>();
  EXPECT_THAT(factory->GetSupportedDecoders(),
              ::testing::ElementsAre(
                  AudioCodecSpec{{"G722", 8000, 1}, {16000, 1, 64000}}));
  EXPECT_FALSE(factory->IsSupportedDecoder({"foo", 8000, 1}));
  EXPECT_TRUE(factory->IsSupportedDecoder({"G722", 8000, 1}));
  EXPECT_EQ(nullptr,
            factory->MakeAudioDecoder({"bar", 16000, 1}, absl::nullopt));
  auto dec1 = factory->MakeAudioDecoder({"G722", 8000, 1}, absl::nullopt);
  ASSERT_NE(nullptr, dec1);
  EXPECT_EQ(16000, dec1->SampleRateHz());
  EXPECT_EQ(1u, dec1->Channels());
  auto dec2 = factory->MakeAudioDecoder({"G722", 8000, 2}, absl::nullopt);
  ASSERT_NE(nullptr, dec2);
  EXPECT_EQ(16000, dec2->SampleRateHz());
  EXPECT_EQ(2u, dec2->Channels());
  auto dec3 = factory->MakeAudioDecoder({"G722", 8000, 3}, absl::nullopt);
  ASSERT_EQ(nullptr, dec3);
}

TEST(AudioDecoderFactoryTemplateTest, Ilbc) {
  auto factory = CreateAudioDecoderFactory<AudioDecoderIlbc>();
  EXPECT_THAT(factory->GetSupportedDecoders(),
              ::testing::ElementsAre(
                  AudioCodecSpec{{"ILBC", 8000, 1}, {8000, 1, 13300}}));
  EXPECT_FALSE(factory->IsSupportedDecoder({"foo", 8000, 1}));
  EXPECT_TRUE(factory->IsSupportedDecoder({"ilbc", 8000, 1}));
  EXPECT_EQ(nullptr,
            factory->MakeAudioDecoder({"bar", 8000, 1}, absl::nullopt));
  auto dec = factory->MakeAudioDecoder({"ilbc", 8000, 1}, absl::nullopt);
  ASSERT_NE(nullptr, dec);
  EXPECT_EQ(8000, dec->SampleRateHz());
}

TEST(AudioDecoderFactoryTemplateTest, L16) {
  auto factory = CreateAudioDecoderFactory<AudioDecoderL16>();
  EXPECT_THAT(
      factory->GetSupportedDecoders(),
      ::testing::ElementsAre(
          AudioCodecSpec{{"L16", 8000, 1}, {8000, 1, 8000 * 16}},
          AudioCodecSpec{{"L16", 16000, 1}, {16000, 1, 16000 * 16}},
          AudioCodecSpec{{"L16", 32000, 1}, {32000, 1, 32000 * 16}},
          AudioCodecSpec{{"L16", 8000, 2}, {8000, 2, 8000 * 16 * 2}},
          AudioCodecSpec{{"L16", 16000, 2}, {16000, 2, 16000 * 16 * 2}},
          AudioCodecSpec{{"L16", 32000, 2}, {32000, 2, 32000 * 16 * 2}}));
  EXPECT_FALSE(factory->IsSupportedDecoder({"foo", 8000, 1}));
  EXPECT_TRUE(factory->IsSupportedDecoder({"L16", 48000, 1}));
  EXPECT_FALSE(factory->IsSupportedDecoder({"L16", 96000, 1}));
  EXPECT_EQ(nullptr,
            factory->MakeAudioDecoder({"L16", 8000, 0}, absl::nullopt));
  auto dec = factory->MakeAudioDecoder({"L16", 48000, 2}, absl::nullopt);
  ASSERT_NE(nullptr, dec);
  EXPECT_EQ(48000, dec->SampleRateHz());
}

TEST(AudioDecoderFactoryTemplateTest, Opus) {
  auto factory = CreateAudioDecoderFactory<AudioDecoderOpus>();
  AudioCodecInfo opus_info{48000, 1, 64000, 6000, 510000};
  opus_info.allow_comfort_noise = false;
  opus_info.supports_network_adaption = true;
  const SdpAudioFormat opus_format(
      {"opus", 48000, 2, {{"minptime", "10"}, {"useinbandfec", "1"}}});
  EXPECT_THAT(factory->GetSupportedDecoders(),
              ::testing::ElementsAre(AudioCodecSpec{opus_format, opus_info}));
  EXPECT_FALSE(factory->IsSupportedDecoder({"opus", 48000, 1}));
  EXPECT_TRUE(factory->IsSupportedDecoder({"opus", 48000, 2}));
  EXPECT_EQ(nullptr,
            factory->MakeAudioDecoder({"bar", 16000, 1}, absl::nullopt));
  auto dec = factory->MakeAudioDecoder({"opus", 48000, 2}, absl::nullopt);
  ASSERT_NE(nullptr, dec);
  EXPECT_EQ(48000, dec->SampleRateHz());
}

}  // namespace webrtc
