/*
 *  Copyright (c) 2012 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include <stdlib.h>

#include <array>
#include <memory>
#include <string>
#include <vector>

#include "api/audio_codecs/opus/audio_encoder_opus.h"
#include "modules/audio_coding/codecs/g711/audio_decoder_pcm.h"
#include "modules/audio_coding/codecs/g711/audio_encoder_pcm.h"
#include "modules/audio_coding/codecs/g722/audio_decoder_g722.h"
#include "modules/audio_coding/codecs/g722/audio_encoder_g722.h"
#include "modules/audio_coding/codecs/ilbc/audio_decoder_ilbc.h"
#include "modules/audio_coding/codecs/ilbc/audio_encoder_ilbc.h"
#include "modules/audio_coding/codecs/opus/audio_decoder_opus.h"
#include "modules/audio_coding/codecs/pcm16b/audio_decoder_pcm16b.h"
#include "modules/audio_coding/codecs/pcm16b/audio_encoder_pcm16b.h"
#include "modules/audio_coding/neteq/tools/resample_input_audio_file.h"
#include "rtc_base/system/arch.h"
#include "test/gtest.h"
#include "test/testsupport/file_utils.h"

namespace webrtc {

namespace {

constexpr int kOverheadBytesPerPacket = 50;

// The absolute difference between the input and output (the first channel) is
// compared vs `tolerance`. The parameter `delay` is used to correct for codec
// delays.
void CompareInputOutput(const std::vector<int16_t>& input,
                        const std::vector<int16_t>& output,
                        size_t num_samples,
                        size_t channels,
                        int tolerance,
                        int delay) {
  ASSERT_LE(num_samples, input.size());
  ASSERT_LE(num_samples * channels, output.size());
  for (unsigned int n = 0; n < num_samples - delay; ++n) {
    ASSERT_NEAR(input[n], output[channels * n + delay], tolerance)
        << "Exit test on first diff; n = " << n;
  }
}

// The absolute difference between the first two channels in `output` is
// compared vs `tolerance`.
void CompareTwoChannels(const std::vector<int16_t>& output,
                        size_t samples_per_channel,
                        size_t channels,
                        int tolerance) {
  ASSERT_GE(channels, 2u);
  ASSERT_LE(samples_per_channel * channels, output.size());
  for (unsigned int n = 0; n < samples_per_channel; ++n)
    ASSERT_NEAR(output[channels * n], output[channels * n + 1], tolerance)
        << "Stereo samples differ.";
}

// Calculates mean-squared error between input and output (the first channel).
// The parameter `delay` is used to correct for codec delays.
double MseInputOutput(const std::vector<int16_t>& input,
                      const std::vector<int16_t>& output,
                      size_t num_samples,
                      size_t channels,
                      int delay) {
  RTC_DCHECK_LT(delay, static_cast<int>(num_samples));
  RTC_DCHECK_LE(num_samples, input.size());
  RTC_DCHECK_LE(num_samples * channels, output.size());
  if (num_samples == 0)
    return 0.0;
  double squared_sum = 0.0;
  for (unsigned int n = 0; n < num_samples - delay; ++n) {
    squared_sum += (input[n] - output[channels * n + delay]) *
                   (input[n] - output[channels * n + delay]);
  }
  return squared_sum / (num_samples - delay);
}
}  // namespace

class AudioDecoderTest : public ::testing::Test {
 protected:
  AudioDecoderTest()
      : input_audio_(
            webrtc::test::ResourcePath("audio_coding/testfile32kHz", "pcm"),
            32000),
        codec_input_rate_hz_(32000),  // Legacy default value.
        frame_size_(0),
        data_length_(0),
        channels_(1),
        payload_type_(17),
        decoder_(NULL) {}

  ~AudioDecoderTest() override {}

  void SetUp() override {
    if (audio_encoder_)
      codec_input_rate_hz_ = audio_encoder_->SampleRateHz();
    // Create arrays.
    ASSERT_GT(data_length_, 0u) << "The test must set data_length_ > 0";
  }

  void TearDown() override {
    delete decoder_;
    decoder_ = NULL;
  }

  virtual void InitEncoder() {}

  // TODO(henrik.lundin) Change return type to size_t once most/all overriding
  // implementations are gone.
  virtual int EncodeFrame(const int16_t* input,
                          size_t input_len_samples,
                          rtc::Buffer* output) {
    AudioEncoder::EncodedInfo encoded_info;
    const size_t samples_per_10ms = audio_encoder_->SampleRateHz() / 100;
    RTC_CHECK_EQ(samples_per_10ms * audio_encoder_->Num10MsFramesInNextPacket(),
                 input_len_samples);
    std::unique_ptr<int16_t[]> interleaved_input(
        new int16_t[channels_ * samples_per_10ms]);
    for (size_t i = 0; i < audio_encoder_->Num10MsFramesInNextPacket(); ++i) {
      EXPECT_EQ(0u, encoded_info.encoded_bytes);

      // Duplicate the mono input signal to however many channels the test
      // wants.
      test::InputAudioFile::DuplicateInterleaved(input + i * samples_per_10ms,
                                                 samples_per_10ms, channels_,
                                                 interleaved_input.get());

      encoded_info =
          audio_encoder_->Encode(0,
                                 rtc::ArrayView<const int16_t>(
                                     interleaved_input.get(),
                                     audio_encoder_->NumChannels() *
                                         audio_encoder_->SampleRateHz() / 100),
                                 output);
    }
    EXPECT_EQ(payload_type_, encoded_info.payload_type);
    return static_cast<int>(encoded_info.encoded_bytes);
  }

  // Encodes and decodes audio. The absolute difference between the input and
  // output is compared vs `tolerance`, and the mean-squared error is compared
  // with `mse`. The encoded stream should contain `expected_bytes`. For stereo
  // audio, the absolute difference between the two channels is compared vs
  // `channel_diff_tolerance`.
  void EncodeDecodeTest(size_t expected_bytes,
                        int tolerance,
                        double mse,
                        int delay = 0,
                        int channel_diff_tolerance = 0) {
    ASSERT_GE(tolerance, 0) << "Test must define a tolerance >= 0";
    ASSERT_GE(channel_diff_tolerance, 0)
        << "Test must define a channel_diff_tolerance >= 0";
    size_t processed_samples = 0u;
    size_t encoded_bytes = 0u;
    InitEncoder();
    std::vector<int16_t> input;
    std::vector<int16_t> decoded;
    while (processed_samples + frame_size_ <= data_length_) {
      // Extend input vector with `frame_size_`.
      input.resize(input.size() + frame_size_, 0);
      // Read from input file.
      ASSERT_GE(input.size() - processed_samples, frame_size_);
      ASSERT_TRUE(input_audio_.Read(frame_size_, codec_input_rate_hz_,
                                    &input[processed_samples]));
      rtc::Buffer encoded;
      size_t enc_len =
          EncodeFrame(&input[processed_samples], frame_size_, &encoded);
      // Make sure that frame_size_ * channels_ samples are allocated and free.
      decoded.resize((processed_samples + frame_size_) * channels_, 0);

      const std::vector<AudioDecoder::ParseResult> parse_result =
          decoder_->ParsePayload(std::move(encoded), /*timestamp=*/0);
      RTC_CHECK_EQ(parse_result.size(), size_t{1});
      auto decode_result = parse_result[0].frame->Decode(
          rtc::ArrayView<int16_t>(&decoded[processed_samples * channels_],
                                  frame_size_ * channels_ * sizeof(int16_t)));
      RTC_CHECK(decode_result.has_value());
      EXPECT_EQ(frame_size_ * channels_, decode_result->num_decoded_samples);
      encoded_bytes += enc_len;
      processed_samples += frame_size_;
    }
    // For some codecs it doesn't make sense to check expected number of bytes,
    // since the number can vary for different platforms. Opus is such a codec.
    // In this case expected_bytes is set to 0.
    if (expected_bytes) {
      EXPECT_EQ(expected_bytes, encoded_bytes);
    }
    CompareInputOutput(input, decoded, processed_samples, channels_, tolerance,
                       delay);
    if (channels_ == 2)
      CompareTwoChannels(decoded, processed_samples, channels_,
                         channel_diff_tolerance);
    EXPECT_LE(
        MseInputOutput(input, decoded, processed_samples, channels_, delay),
        mse);
  }

  // Encodes a payload and decodes it twice with decoder re-init before each
  // decode. Verifies that the decoded result is the same.
  void ReInitTest() {
    InitEncoder();
    std::unique_ptr<int16_t[]> input(new int16_t[frame_size_]);
    ASSERT_TRUE(
        input_audio_.Read(frame_size_, codec_input_rate_hz_, input.get()));
    std::array<rtc::Buffer, 2> encoded;
    EncodeFrame(input.get(), frame_size_, &encoded[0]);
    // Make a copy.
    encoded[1].SetData(encoded[0].data(), encoded[0].size());

    std::array<std::vector<int16_t>, 2> outputs;
    for (size_t i = 0; i < outputs.size(); ++i) {
      outputs[i].resize(frame_size_ * channels_);
      decoder_->Reset();
      const std::vector<AudioDecoder::ParseResult> parse_result =
          decoder_->ParsePayload(std::move(encoded[i]), /*timestamp=*/0);
      RTC_CHECK_EQ(parse_result.size(), size_t{1});
      auto decode_result = parse_result[0].frame->Decode(outputs[i]);
      RTC_CHECK(decode_result.has_value());
      EXPECT_EQ(frame_size_ * channels_, decode_result->num_decoded_samples);
    }
    EXPECT_EQ(outputs[0], outputs[1]);
  }

  // Call DecodePlc and verify that the correct number of samples is produced.
  void DecodePlcTest() {
    InitEncoder();
    std::unique_ptr<int16_t[]> input(new int16_t[frame_size_]);
    ASSERT_TRUE(
        input_audio_.Read(frame_size_, codec_input_rate_hz_, input.get()));
    rtc::Buffer encoded;
    EncodeFrame(input.get(), frame_size_, &encoded);
    decoder_->Reset();
    std::vector<int16_t> output(frame_size_ * channels_);
    const std::vector<AudioDecoder::ParseResult> parse_result =
        decoder_->ParsePayload(std::move(encoded), /*timestamp=*/0);
    RTC_CHECK_EQ(parse_result.size(), size_t{1});
    auto decode_result = parse_result[0].frame->Decode(output);
    RTC_CHECK(decode_result.has_value());
    EXPECT_EQ(frame_size_ * channels_, decode_result->num_decoded_samples);
    // Call DecodePlc and verify that we get one frame of data.
    // (Overwrite the output from the above Decode call, but that does not
    // matter.)
    size_t dec_len =
        decoder_->DecodePlc(/*num_frames=*/1, /*decoded=*/output.data());
    EXPECT_EQ(frame_size_ * channels_, dec_len);
  }

  test::ResampleInputAudioFile input_audio_;
  int codec_input_rate_hz_;
  size_t frame_size_;
  size_t data_length_;
  size_t channels_;
  const int payload_type_;
  AudioDecoder* decoder_;
  std::unique_ptr<AudioEncoder> audio_encoder_;
};

class AudioDecoderPcmUTest : public AudioDecoderTest {
 protected:
  AudioDecoderPcmUTest() : AudioDecoderTest() {
    frame_size_ = 160;
    data_length_ = 10 * frame_size_;
    decoder_ = new AudioDecoderPcmU(1);
    AudioEncoderPcmU::Config config;
    config.frame_size_ms = static_cast<int>(frame_size_ / 8);
    config.payload_type = payload_type_;
    audio_encoder_.reset(new AudioEncoderPcmU(config));
  }
};

class AudioDecoderPcmATest : public AudioDecoderTest {
 protected:
  AudioDecoderPcmATest() : AudioDecoderTest() {
    frame_size_ = 160;
    data_length_ = 10 * frame_size_;
    decoder_ = new AudioDecoderPcmA(1);
    AudioEncoderPcmA::Config config;
    config.frame_size_ms = static_cast<int>(frame_size_ / 8);
    config.payload_type = payload_type_;
    audio_encoder_.reset(new AudioEncoderPcmA(config));
  }
};

class AudioDecoderPcm16BTest : public AudioDecoderTest {
 protected:
  AudioDecoderPcm16BTest() : AudioDecoderTest() {
    codec_input_rate_hz_ = 16000;
    frame_size_ = 20 * codec_input_rate_hz_ / 1000;
    data_length_ = 10 * frame_size_;
    decoder_ = new AudioDecoderPcm16B(codec_input_rate_hz_, 1);
    RTC_DCHECK(decoder_);
    AudioEncoderPcm16B::Config config;
    config.sample_rate_hz = codec_input_rate_hz_;
    config.frame_size_ms =
        static_cast<int>(frame_size_ / (config.sample_rate_hz / 1000));
    config.payload_type = payload_type_;
    audio_encoder_.reset(new AudioEncoderPcm16B(config));
  }
};

class AudioDecoderIlbcTest : public AudioDecoderTest {
 protected:
  AudioDecoderIlbcTest() : AudioDecoderTest() {
    codec_input_rate_hz_ = 8000;
    frame_size_ = 240;
    data_length_ = 10 * frame_size_;
    decoder_ = new AudioDecoderIlbcImpl;
    RTC_DCHECK(decoder_);
    AudioEncoderIlbcConfig config;
    config.frame_size_ms = 30;
    audio_encoder_.reset(new AudioEncoderIlbcImpl(config, payload_type_));
  }

  // Overload the default test since iLBC's function WebRtcIlbcfix_NetEqPlc does
  // not return any data. It simply resets a few states and returns 0.
  void DecodePlcTest() {
    InitEncoder();
    std::unique_ptr<int16_t[]> input(new int16_t[frame_size_]);
    ASSERT_TRUE(
        input_audio_.Read(frame_size_, codec_input_rate_hz_, input.get()));
    rtc::Buffer encoded;
    size_t enc_len = EncodeFrame(input.get(), frame_size_, &encoded);
    AudioDecoder::SpeechType speech_type;
    decoder_->Reset();
    std::unique_ptr<int16_t[]> output(new int16_t[frame_size_ * channels_]);
    size_t dec_len = decoder_->Decode(
        encoded.data(), enc_len, codec_input_rate_hz_,
        frame_size_ * channels_ * sizeof(int16_t), output.get(), &speech_type);
    EXPECT_EQ(frame_size_, dec_len);
    // Simply call DecodePlc and verify that we get 0 as return value.
    EXPECT_EQ(0U, decoder_->DecodePlc(1, output.get()));
  }
};

class AudioDecoderG722Test : public AudioDecoderTest {
 protected:
  AudioDecoderG722Test() : AudioDecoderTest() {
    codec_input_rate_hz_ = 16000;
    frame_size_ = 160;
    data_length_ = 10 * frame_size_;
    decoder_ = new AudioDecoderG722Impl;
    RTC_DCHECK(decoder_);
    AudioEncoderG722Config config;
    config.frame_size_ms = 10;
    config.num_channels = 1;
    audio_encoder_.reset(new AudioEncoderG722Impl(config, payload_type_));
  }
};

class AudioDecoderG722StereoTest : public AudioDecoderTest {
 protected:
  AudioDecoderG722StereoTest() : AudioDecoderTest() {
    channels_ = 2;
    codec_input_rate_hz_ = 16000;
    frame_size_ = 160;
    data_length_ = 10 * frame_size_;
    decoder_ = new AudioDecoderG722StereoImpl;
    RTC_DCHECK(decoder_);
    AudioEncoderG722Config config;
    config.frame_size_ms = 10;
    config.num_channels = 2;
    audio_encoder_.reset(new AudioEncoderG722Impl(config, payload_type_));
  }
};

class AudioDecoderOpusTest
    : public AudioDecoderTest,
      public testing::WithParamInterface<std::tuple<int, int>> {
 protected:
  AudioDecoderOpusTest() : AudioDecoderTest() {
    channels_ = opus_num_channels_;
    codec_input_rate_hz_ = opus_sample_rate_hz_;
    frame_size_ = rtc::CheckedDivExact(opus_sample_rate_hz_, 100);
    data_length_ = 10 * frame_size_;
    decoder_ =
        new AudioDecoderOpusImpl(opus_num_channels_, opus_sample_rate_hz_);
    AudioEncoderOpusConfig config;
    config.frame_size_ms = 10;
    config.sample_rate_hz = opus_sample_rate_hz_;
    config.num_channels = opus_num_channels_;
    config.application = opus_num_channels_ == 1
                             ? AudioEncoderOpusConfig::ApplicationMode::kVoip
                             : AudioEncoderOpusConfig::ApplicationMode::kAudio;
    audio_encoder_ = AudioEncoderOpus::MakeAudioEncoder(config, payload_type_);
    audio_encoder_->OnReceivedOverhead(kOverheadBytesPerPacket);
  }
  const int opus_sample_rate_hz_{std::get<0>(GetParam())};
  const int opus_num_channels_{std::get<1>(GetParam())};
};

INSTANTIATE_TEST_SUITE_P(Param,
                         AudioDecoderOpusTest,
                         testing::Combine(testing::Values(16000, 48000),
                                          testing::Values(1, 2)));

TEST_F(AudioDecoderPcmUTest, EncodeDecode) {
  int tolerance = 251;
  double mse = 1734.0;
  EncodeDecodeTest(data_length_, tolerance, mse);
  ReInitTest();
  EXPECT_FALSE(decoder_->HasDecodePlc());
}

namespace {
int SetAndGetTargetBitrate(AudioEncoder* audio_encoder, int rate) {
  audio_encoder->OnReceivedUplinkBandwidth(rate, absl::nullopt);
  return audio_encoder->GetTargetBitrate();
}
void TestSetAndGetTargetBitratesWithFixedCodec(AudioEncoder* audio_encoder,
                                               int fixed_rate) {
  EXPECT_EQ(fixed_rate, SetAndGetTargetBitrate(audio_encoder, 32000));
  EXPECT_EQ(fixed_rate, SetAndGetTargetBitrate(audio_encoder, fixed_rate - 1));
  EXPECT_EQ(fixed_rate, SetAndGetTargetBitrate(audio_encoder, fixed_rate));
  EXPECT_EQ(fixed_rate, SetAndGetTargetBitrate(audio_encoder, fixed_rate + 1));
}
}  // namespace

TEST_F(AudioDecoderPcmUTest, SetTargetBitrate) {
  TestSetAndGetTargetBitratesWithFixedCodec(audio_encoder_.get(), 64000);
}

TEST_F(AudioDecoderPcmATest, EncodeDecode) {
  int tolerance = 308;
  double mse = 1931.0;
  EncodeDecodeTest(data_length_, tolerance, mse);
  ReInitTest();
  EXPECT_FALSE(decoder_->HasDecodePlc());
}

TEST_F(AudioDecoderPcmATest, SetTargetBitrate) {
  TestSetAndGetTargetBitratesWithFixedCodec(audio_encoder_.get(), 64000);
}

TEST_F(AudioDecoderPcm16BTest, EncodeDecode) {
  int tolerance = 0;
  double mse = 0.0;
  EncodeDecodeTest(2 * data_length_, tolerance, mse);
  ReInitTest();
  EXPECT_FALSE(decoder_->HasDecodePlc());
}

TEST_F(AudioDecoderPcm16BTest, SetTargetBitrate) {
  TestSetAndGetTargetBitratesWithFixedCodec(audio_encoder_.get(),
                                            codec_input_rate_hz_ * 16);
}

TEST_F(AudioDecoderIlbcTest, EncodeDecode) {
  int tolerance = 6808;
  double mse = 2.13e6;
  int delay = 80;  // Delay from input to output.
  EncodeDecodeTest(500, tolerance, mse, delay);
  ReInitTest();
  EXPECT_TRUE(decoder_->HasDecodePlc());
  DecodePlcTest();
}

TEST_F(AudioDecoderIlbcTest, SetTargetBitrate) {
  TestSetAndGetTargetBitratesWithFixedCodec(audio_encoder_.get(), 13333);
}

TEST_F(AudioDecoderG722Test, EncodeDecode) {
  int tolerance = 6176;
  double mse = 238630.0;
  int delay = 22;  // Delay from input to output.
  EncodeDecodeTest(data_length_ / 2, tolerance, mse, delay);
  ReInitTest();
  EXPECT_FALSE(decoder_->HasDecodePlc());
}

TEST_F(AudioDecoderG722Test, SetTargetBitrate) {
  TestSetAndGetTargetBitratesWithFixedCodec(audio_encoder_.get(), 64000);
}

TEST_F(AudioDecoderG722StereoTest, EncodeDecode) {
  int tolerance = 6176;
  int channel_diff_tolerance = 0;
  double mse = 238630.0;
  int delay = 22;  // Delay from input to output.
  EncodeDecodeTest(data_length_, tolerance, mse, delay, channel_diff_tolerance);
  ReInitTest();
  EXPECT_FALSE(decoder_->HasDecodePlc());
}

TEST_F(AudioDecoderG722StereoTest, SetTargetBitrate) {
  TestSetAndGetTargetBitratesWithFixedCodec(audio_encoder_.get(), 128000);
}

// TODO(http://bugs.webrtc.org/12518): Enable the test after Opus has been
// updated.
TEST_P(AudioDecoderOpusTest, DISABLED_EncodeDecode) {
  constexpr int tolerance = 6176;
  constexpr int channel_diff_tolerance = 6;
  constexpr double mse = 238630.0;
  constexpr int delay = 22;  // Delay from input to output.
  EncodeDecodeTest(0, tolerance, mse, delay, channel_diff_tolerance);
  ReInitTest();
  EXPECT_FALSE(decoder_->HasDecodePlc());
}

TEST_P(AudioDecoderOpusTest, SetTargetBitrate) {
  const int overhead_rate =
      8 * kOverheadBytesPerPacket * codec_input_rate_hz_ / frame_size_;
  EXPECT_EQ(6000,
            SetAndGetTargetBitrate(audio_encoder_.get(), 5999 + overhead_rate));
  EXPECT_EQ(6000,
            SetAndGetTargetBitrate(audio_encoder_.get(), 6000 + overhead_rate));
  EXPECT_EQ(32000, SetAndGetTargetBitrate(audio_encoder_.get(),
                                          32000 + overhead_rate));
  EXPECT_EQ(510000, SetAndGetTargetBitrate(audio_encoder_.get(),
                                           510000 + overhead_rate));
  EXPECT_EQ(510000, SetAndGetTargetBitrate(audio_encoder_.get(),
                                           511000 + overhead_rate));
}

}  // namespace webrtc
