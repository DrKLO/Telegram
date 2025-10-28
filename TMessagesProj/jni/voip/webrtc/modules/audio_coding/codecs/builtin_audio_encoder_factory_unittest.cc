/*
 *  Copyright (c) 2017 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "api/audio_codecs/builtin_audio_encoder_factory.h"

#include <limits>
#include <memory>
#include <vector>

#include "rtc_base/numerics/safe_conversions.h"
#include "test/gmock.h"
#include "test/gtest.h"

namespace webrtc {

class AudioEncoderFactoryTest
    : public ::testing::TestWithParam<rtc::scoped_refptr<AudioEncoderFactory>> {
};

TEST_P(AudioEncoderFactoryTest, SupportsAtLeastOneFormat) {
  auto factory = GetParam();
  auto supported_encoders = factory->GetSupportedEncoders();
  EXPECT_FALSE(supported_encoders.empty());
}

TEST_P(AudioEncoderFactoryTest, CanQueryAllSupportedFormats) {
  auto factory = GetParam();
  auto supported_encoders = factory->GetSupportedEncoders();
  for (const auto& spec : supported_encoders) {
    auto info = factory->QueryAudioEncoder(spec.format);
    EXPECT_TRUE(info);
  }
}

TEST_P(AudioEncoderFactoryTest, CanConstructAllSupportedEncoders) {
  auto factory = GetParam();
  auto supported_encoders = factory->GetSupportedEncoders();
  for (const auto& spec : supported_encoders) {
    auto info = factory->QueryAudioEncoder(spec.format);
    auto encoder = factory->MakeAudioEncoder(127, spec.format, absl::nullopt);
    EXPECT_TRUE(encoder);
    EXPECT_EQ(encoder->SampleRateHz(), info->sample_rate_hz);
    EXPECT_EQ(encoder->NumChannels(), info->num_channels);
    EXPECT_EQ(encoder->RtpTimestampRateHz(), spec.format.clockrate_hz);
  }
}

TEST_P(AudioEncoderFactoryTest, CanRunAllSupportedEncoders) {
  constexpr int kTestPayloadType = 127;
  auto factory = GetParam();
  auto supported_encoders = factory->GetSupportedEncoders();
  for (const auto& spec : supported_encoders) {
    auto encoder =
        factory->MakeAudioEncoder(kTestPayloadType, spec.format, absl::nullopt);
    EXPECT_TRUE(encoder);
    encoder->Reset();
    const int num_samples = rtc::checked_cast<int>(
        encoder->SampleRateHz() * encoder->NumChannels() / 100);
    rtc::Buffer out;
    rtc::BufferT<int16_t> audio;
    audio.SetData(num_samples, [](rtc::ArrayView<int16_t> audio) {
      for (size_t i = 0; i != audio.size(); ++i) {
        // Just put some numbers in there, ensure they're within range.
        audio[i] =
            static_cast<int16_t>(i & std::numeric_limits<int16_t>::max());
      }
      return audio.size();
    });
    // This is here to stop the test going forever with a broken encoder.
    constexpr int kMaxEncodeCalls = 100;
    int blocks = 0;
    for (; blocks < kMaxEncodeCalls; ++blocks) {
      AudioEncoder::EncodedInfo info = encoder->Encode(
          blocks * encoder->RtpTimestampRateHz() / 100, audio, &out);
      EXPECT_EQ(info.encoded_bytes, out.size());
      if (info.encoded_bytes > 0) {
        EXPECT_EQ(0u, info.encoded_timestamp);
        EXPECT_EQ(kTestPayloadType, info.payload_type);
        break;
      }
    }
    ASSERT_LT(blocks, kMaxEncodeCalls);
    const unsigned int next_timestamp =
        blocks * encoder->RtpTimestampRateHz() / 100;
    out.Clear();
    for (; blocks < kMaxEncodeCalls; ++blocks) {
      AudioEncoder::EncodedInfo info = encoder->Encode(
          blocks * encoder->RtpTimestampRateHz() / 100, audio, &out);
      EXPECT_EQ(info.encoded_bytes, out.size());
      if (info.encoded_bytes > 0) {
        EXPECT_EQ(next_timestamp, info.encoded_timestamp);
        EXPECT_EQ(kTestPayloadType, info.payload_type);
        break;
      }
    }
    ASSERT_LT(blocks, kMaxEncodeCalls);
  }
}

INSTANTIATE_TEST_SUITE_P(BuiltinAudioEncoderFactoryTest,
                         AudioEncoderFactoryTest,
                         ::testing::Values(CreateBuiltinAudioEncoderFactory()));

TEST(BuiltinAudioEncoderFactoryTest, SupportsTheExpectedFormats) {
  using ::testing::ElementsAreArray;
  // Check that we claim to support the formats we expect from build flags, and
  // we've ordered them correctly.
  auto factory = CreateBuiltinAudioEncoderFactory();
  auto specs = factory->GetSupportedEncoders();

  const std::vector<SdpAudioFormat> supported_formats = [&specs] {
    std::vector<SdpAudioFormat> formats;
    formats.reserve(specs.size());
    for (const auto& spec : specs) {
      formats.push_back(spec.format);
    }
    return formats;
  }();

  const std::vector<SdpAudioFormat> expected_formats = {
#ifdef WEBRTC_CODEC_OPUS
    {"opus", 48000, 2, {{"minptime", "10"}, {"useinbandfec", "1"}}},
#endif
#if defined(WEBRTC_CODEC_ISAC) || defined(WEBRTC_CODEC_ISACFX)
    {"isac", 16000, 1},
#endif
#ifdef WEBRTC_CODEC_ISAC
    {"isac", 32000, 1},
#endif
    {"G722", 8000, 1},
#ifdef WEBRTC_CODEC_ILBC
    {"ilbc", 8000, 1},
#endif
    {"pcmu", 8000, 1},
    {"pcma", 8000, 1}
  };

  ASSERT_THAT(supported_formats, ElementsAreArray(expected_formats));
}

// Tests that using more channels than the maximum does not work.
TEST(BuiltinAudioEncoderFactoryTest, MaxNrOfChannels) {
  rtc::scoped_refptr<AudioEncoderFactory> aef =
      CreateBuiltinAudioEncoderFactory();
  std::vector<std::string> codecs = {
#ifdef WEBRTC_CODEC_OPUS
    "opus",
#endif
#if defined(WEBRTC_CODEC_ISAC) || defined(WEBRTC_CODEC_ISACFX)
    "isac",
#endif
#ifdef WEBRTC_CODEC_ILBC
    "ilbc",
#endif
    "pcmu",
    "pcma",
    "l16",
    "G722",
    "G711",
  };

  for (auto codec : codecs) {
    EXPECT_FALSE(aef->MakeAudioEncoder(
        /*payload_type=*/111,
        /*format=*/
        SdpAudioFormat(codec, 32000, AudioEncoder::kMaxNumberOfChannels + 1),
        /*codec_pair_id=*/absl::nullopt));
  }
}

}  // namespace webrtc
