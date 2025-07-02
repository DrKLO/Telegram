/*
 *  Copyright (c) 2019 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "api/audio_codecs/opus/audio_decoder_multi_channel_opus.h"

#include "modules/audio_coding/codecs/opus/audio_coder_opus_common.h"
#include "test/gmock.h"
#include "test/gtest.h"

namespace webrtc {
using ::testing::NiceMock;
using ::testing::Return;

TEST(AudioDecoderMultiOpusTest, GetFormatParameter) {
  const SdpAudioFormat sdp_format("multiopus", 48000, 4,
                                  {{"channel_mapping", "0,1,2,3"},
                                   {"coupled_streams", "2"},
                                   {"num_streams", "2"}});

  EXPECT_EQ(GetFormatParameter(sdp_format, "channel_mapping"),
            absl::optional<std::string>("0,1,2,3"));

  EXPECT_EQ(GetFormatParameter<int>(sdp_format, "coupled_streams"),
            absl::optional<int>(2));

  EXPECT_EQ(GetFormatParameter(sdp_format, "missing"), absl::nullopt);

  EXPECT_EQ(GetFormatParameter<int>(sdp_format, "channel_mapping"),
            absl::nullopt);
}

TEST(AudioDecoderMultiOpusTest, InvalidChannelMappings) {
  {
    // Can't use channel 3 if there are only 2 channels.
    const SdpAudioFormat sdp_format("multiopus", 48000, 2,
                                    {{"channel_mapping", "3,0"},
                                     {"coupled_streams", "1"},
                                     {"num_streams", "2"}});
    const absl::optional<AudioDecoderMultiChannelOpus::Config> decoder_config =
        AudioDecoderMultiChannelOpus::SdpToConfig(sdp_format);
    EXPECT_FALSE(decoder_config.has_value());
  }
  {
    // The mapping is too long. There are only 5 channels, but 6 elements in the
    // mapping.
    const SdpAudioFormat sdp_format("multiopus", 48000, 5,
                                    {{"channel_mapping", "0,1,2,3,4,5"},
                                     {"coupled_streams", "0"},
                                     {"num_streams", "2"}});
    const absl::optional<AudioDecoderMultiChannelOpus::Config> decoder_config =
        AudioDecoderMultiChannelOpus::SdpToConfig(sdp_format);
    EXPECT_FALSE(decoder_config.has_value());
  }
  {
    // The mapping doesn't parse correctly.
    const SdpAudioFormat sdp_format(
        "multiopus", 48000, 5,
        {{"channel_mapping", "0,1,two,3,4"}, {"coupled_streams", "0"}});
    const absl::optional<AudioDecoderMultiChannelOpus::Config> decoder_config =
        AudioDecoderMultiChannelOpus::SdpToConfig(sdp_format);
    EXPECT_FALSE(decoder_config.has_value());
  }
}

TEST(AudioDecoderMultiOpusTest, ValidSdpToConfigProducesCorrectConfig) {
  const SdpAudioFormat sdp_format("multiopus", 48000, 4,
                                  {{"channel_mapping", "3,1,2,0"},
                                   {"coupled_streams", "2"},
                                   {"num_streams", "2"}});

  const absl::optional<AudioDecoderMultiChannelOpus::Config> decoder_config =
      AudioDecoderMultiChannelOpus::SdpToConfig(sdp_format);

  ASSERT_TRUE(decoder_config.has_value());
  EXPECT_TRUE(decoder_config->IsOk());
  EXPECT_EQ(decoder_config->coupled_streams, 2);
  EXPECT_THAT(decoder_config->channel_mapping,
              ::testing::ContainerEq(std::vector<unsigned char>({3, 1, 2, 0})));
}

TEST(AudioDecoderMultiOpusTest, InvalidSdpToConfigDoesNotProduceConfig) {
  {
    const SdpAudioFormat sdp_format("multiopus", 48000, 4,
                                    {{"channel_mapping", "0,1,2,3"},
                                     {"coupled_stream", "2"},
                                     {"num_streams", "2"}});

    const absl::optional<AudioDecoderMultiChannelOpus::Config> decoder_config =
        AudioDecoderMultiChannelOpus::SdpToConfig(sdp_format);

    EXPECT_FALSE(decoder_config.has_value());
  }

  {
    const SdpAudioFormat sdp_format("multiopus", 48000, 4,
                                    {{"channel_mapping", "0,1,2 3"},
                                     {"coupled_streams", "2"},
                                     {"num_streams", "2"}});

    const absl::optional<AudioDecoderMultiChannelOpus::Config> decoder_config =
        AudioDecoderMultiChannelOpus::SdpToConfig(sdp_format);

    EXPECT_FALSE(decoder_config.has_value());
  }
}

TEST(AudioDecoderMultiOpusTest, CodecsCanBeCreated) {
  const SdpAudioFormat sdp_format("multiopus", 48000, 4,
                                  {{"channel_mapping", "0,1,2,3"},
                                   {"coupled_streams", "2"},
                                   {"num_streams", "2"}});

  const absl::optional<AudioDecoderMultiChannelOpus::Config> decoder_config =
      AudioDecoderMultiChannelOpus::SdpToConfig(sdp_format);

  ASSERT_TRUE(decoder_config.has_value());

  const std::unique_ptr<AudioDecoder> opus_decoder =
      AudioDecoderMultiChannelOpus::MakeAudioDecoder(*decoder_config);

  EXPECT_TRUE(opus_decoder);
}

TEST(AudioDecoderMultiOpusTest, AdvertisedCodecsCanBeCreated) {
  std::vector<AudioCodecSpec> specs;
  AudioDecoderMultiChannelOpus::AppendSupportedDecoders(&specs);

  EXPECT_FALSE(specs.empty());

  for (const AudioCodecSpec& spec : specs) {
    const absl::optional<AudioDecoderMultiChannelOpus::Config> decoder_config =
        AudioDecoderMultiChannelOpus::SdpToConfig(spec.format);
    ASSERT_TRUE(decoder_config.has_value());

    const std::unique_ptr<AudioDecoder> opus_decoder =
        AudioDecoderMultiChannelOpus::MakeAudioDecoder(*decoder_config);

    EXPECT_TRUE(opus_decoder);
  }
}
}  // namespace webrtc
