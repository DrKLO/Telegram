/*
 *  Copyright (c) 2014 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "modules/audio_coding/codecs/red/audio_encoder_copy_red.h"

#include <memory>
#include <vector>

#include "rtc_base/checks.h"
#include "rtc_base/numerics/safe_conversions.h"
#include "test/field_trial.h"
#include "test/gtest.h"
#include "test/mock_audio_encoder.h"
#include "test/scoped_key_value_config.h"
#include "test/testsupport/rtc_expect_death.h"

using ::testing::_;
using ::testing::Eq;
using ::testing::InSequence;
using ::testing::Invoke;
using ::testing::MockFunction;
using ::testing::Not;
using ::testing::Optional;
using ::testing::Return;
using ::testing::SetArgPointee;

namespace webrtc {

namespace {
static const size_t kMaxNumSamples = 48 * 10 * 2;  // 10 ms @ 48 kHz stereo.
static const size_t kRedLastHeaderLength =
    1;  // 1 byte RED header for the last element.
}  // namespace

class AudioEncoderCopyRedTest : public ::testing::Test {
 protected:
  AudioEncoderCopyRedTest()
      : mock_encoder_(new MockAudioEncoder),
        timestamp_(4711),
        sample_rate_hz_(16000),
        num_audio_samples_10ms(sample_rate_hz_ / 100),
        red_payload_type_(63) {
    AudioEncoderCopyRed::Config config;
    config.payload_type = red_payload_type_;
    config.speech_encoder = std::unique_ptr<AudioEncoder>(mock_encoder_);
    red_.reset(new AudioEncoderCopyRed(std::move(config), field_trials_));
    memset(audio_, 0, sizeof(audio_));
    EXPECT_CALL(*mock_encoder_, NumChannels()).WillRepeatedly(Return(1U));
    EXPECT_CALL(*mock_encoder_, SampleRateHz())
        .WillRepeatedly(Return(sample_rate_hz_));
  }

  void TearDown() override { red_.reset(); }

  void Encode() {
    ASSERT_TRUE(red_.get() != NULL);
    encoded_.Clear();
    encoded_info_ = red_->Encode(
        timestamp_,
        rtc::ArrayView<const int16_t>(audio_, num_audio_samples_10ms),
        &encoded_);
    timestamp_ += rtc::checked_cast<uint32_t>(num_audio_samples_10ms);
  }

  test::ScopedKeyValueConfig field_trials_;
  MockAudioEncoder* mock_encoder_;
  std::unique_ptr<AudioEncoderCopyRed> red_;
  uint32_t timestamp_;
  int16_t audio_[kMaxNumSamples];
  const int sample_rate_hz_;
  size_t num_audio_samples_10ms;
  rtc::Buffer encoded_;
  AudioEncoder::EncodedInfo encoded_info_;
  const int red_payload_type_;
};

TEST_F(AudioEncoderCopyRedTest, CreateAndDestroy) {}

TEST_F(AudioEncoderCopyRedTest, CheckSampleRatePropagation) {
  EXPECT_CALL(*mock_encoder_, SampleRateHz()).WillOnce(Return(17));
  EXPECT_EQ(17, red_->SampleRateHz());
}

TEST_F(AudioEncoderCopyRedTest, CheckNumChannelsPropagation) {
  EXPECT_CALL(*mock_encoder_, NumChannels()).WillOnce(Return(17U));
  EXPECT_EQ(17U, red_->NumChannels());
}

TEST_F(AudioEncoderCopyRedTest, CheckFrameSizePropagation) {
  EXPECT_CALL(*mock_encoder_, Num10MsFramesInNextPacket())
      .WillOnce(Return(17U));
  EXPECT_EQ(17U, red_->Num10MsFramesInNextPacket());
}

TEST_F(AudioEncoderCopyRedTest, CheckMaxFrameSizePropagation) {
  EXPECT_CALL(*mock_encoder_, Max10MsFramesInAPacket()).WillOnce(Return(17U));
  EXPECT_EQ(17U, red_->Max10MsFramesInAPacket());
}

TEST_F(AudioEncoderCopyRedTest, CheckTargetAudioBitratePropagation) {
  EXPECT_CALL(*mock_encoder_,
              OnReceivedUplinkBandwidth(4711, absl::optional<int64_t>()));
  red_->OnReceivedUplinkBandwidth(4711, absl::nullopt);
}

TEST_F(AudioEncoderCopyRedTest, CheckPacketLossFractionPropagation) {
  EXPECT_CALL(*mock_encoder_, OnReceivedUplinkPacketLossFraction(0.5));
  red_->OnReceivedUplinkPacketLossFraction(0.5);
}

TEST_F(AudioEncoderCopyRedTest, CheckGetFrameLengthRangePropagation) {
  auto expected_range =
      std::make_pair(TimeDelta::Millis(20), TimeDelta::Millis(20));
  EXPECT_CALL(*mock_encoder_, GetFrameLengthRange())
      .WillRepeatedly(Return(absl::make_optional(expected_range)));
  EXPECT_THAT(red_->GetFrameLengthRange(), Optional(Eq(expected_range)));
}

// Checks that the an Encode() call is immediately propagated to the speech
// encoder.
TEST_F(AudioEncoderCopyRedTest, CheckImmediateEncode) {
  // Interleaving the EXPECT_CALL sequence with expectations on the MockFunction
  // check ensures that exactly one call to EncodeImpl happens in each
  // Encode call.
  InSequence s;
  MockFunction<void(int check_point_id)> check;
  for (int i = 1; i <= 6; ++i) {
    EXPECT_CALL(*mock_encoder_, EncodeImpl(_, _, _))
        .WillRepeatedly(Return(AudioEncoder::EncodedInfo()));
    EXPECT_CALL(check, Call(i));
    Encode();
    check.Call(i);
  }
}

// Checks that no output is produced if the underlying codec doesn't emit any
// new data, even if the RED codec is loaded with a secondary encoding.
TEST_F(AudioEncoderCopyRedTest, CheckNoOutput) {
  static const size_t kEncodedSize = 17;
  static const size_t kHeaderLenBytes = 5;
  {
    InSequence s;
    EXPECT_CALL(*mock_encoder_, EncodeImpl(_, _, _))
        .WillOnce(Invoke(MockAudioEncoder::FakeEncoding(kEncodedSize)))
        .WillOnce(Invoke(MockAudioEncoder::FakeEncoding(0)))
        .WillOnce(Invoke(MockAudioEncoder::FakeEncoding(kEncodedSize)));
  }

  // Start with one Encode() call that will produce output.
  Encode();
  // First call is a special case, since it does not include a secondary
  // payload.
  EXPECT_EQ(0u, encoded_info_.redundant.size());
  EXPECT_EQ(kEncodedSize + kRedLastHeaderLength, encoded_info_.encoded_bytes);

  // Next call to the speech encoder will not produce any output.
  Encode();
  EXPECT_EQ(0u, encoded_info_.encoded_bytes);

  // Final call to the speech encoder will produce output.
  Encode();
  EXPECT_EQ(2 * kEncodedSize + kHeaderLenBytes, encoded_info_.encoded_bytes);
  ASSERT_EQ(2u, encoded_info_.redundant.size());
}

// Checks that the correct payload sizes are populated into the redundancy
// information for a redundancy level of 1.
TEST_F(AudioEncoderCopyRedTest, CheckPayloadSizes1) {
  // Let the mock encoder return payload sizes 1, 2, 3, ..., 10 for the sequence
  // of calls.
  static const int kNumPackets = 10;
  InSequence s;
  for (int encode_size = 1; encode_size <= kNumPackets; ++encode_size) {
    EXPECT_CALL(*mock_encoder_, EncodeImpl(_, _, _))
        .WillOnce(Invoke(MockAudioEncoder::FakeEncoding(encode_size)));
  }

  // First call is a special case, since it does not include a secondary
  // payload.
  Encode();
  EXPECT_EQ(0u, encoded_info_.redundant.size());
  EXPECT_EQ(kRedLastHeaderLength + 1u, encoded_info_.encoded_bytes);

  for (size_t i = 2; i <= kNumPackets; ++i) {
    Encode();
    ASSERT_EQ(2u, encoded_info_.redundant.size());
    EXPECT_EQ(i, encoded_info_.redundant[1].encoded_bytes);
    EXPECT_EQ(i - 1, encoded_info_.redundant[0].encoded_bytes);
    EXPECT_EQ(5 + i + (i - 1), encoded_info_.encoded_bytes);
  }
}

// Checks that the correct payload sizes are populated into the redundancy
// information for a redundancy level of 0.
TEST_F(AudioEncoderCopyRedTest, CheckPayloadSizes0) {
  webrtc::test::ScopedKeyValueConfig field_trials(
      field_trials_, "WebRTC-Audio-Red-For-Opus/Enabled-0/");
  // Recreate the RED encoder to take the new field trial setting into account.
  AudioEncoderCopyRed::Config config;
  config.payload_type = red_payload_type_;
  config.speech_encoder = std::move(red_->ReclaimContainedEncoders()[0]);
  red_.reset(new AudioEncoderCopyRed(std::move(config), field_trials));

  // Let the mock encoder return payload sizes 1, 2, 3, ..., 10 for the sequence
  // of calls.
  static const int kNumPackets = 10;
  InSequence s;
  for (int encode_size = 1; encode_size <= kNumPackets; ++encode_size) {
    EXPECT_CALL(*mock_encoder_, EncodeImpl(_, _, _))
        .WillOnce(Invoke(MockAudioEncoder::FakeEncoding(encode_size)));
  }

  for (size_t i = 1; i <= kNumPackets; ++i) {
    Encode();
    ASSERT_EQ(0u, encoded_info_.redundant.size());
    EXPECT_EQ(1 + i, encoded_info_.encoded_bytes);
  }
}
// Checks that the correct payload sizes are populated into the redundancy
// information for a redundancy level of 2.
TEST_F(AudioEncoderCopyRedTest, CheckPayloadSizes2) {
  webrtc::test::ScopedKeyValueConfig field_trials(
      field_trials_, "WebRTC-Audio-Red-For-Opus/Enabled-2/");
  // Recreate the RED encoder to take the new field trial setting into account.
  AudioEncoderCopyRed::Config config;
  config.payload_type = red_payload_type_;
  config.speech_encoder = std::move(red_->ReclaimContainedEncoders()[0]);
  red_.reset(new AudioEncoderCopyRed(std::move(config), field_trials));

  // Let the mock encoder return payload sizes 1, 2, 3, ..., 10 for the sequence
  // of calls.
  static const int kNumPackets = 10;
  InSequence s;
  for (int encode_size = 1; encode_size <= kNumPackets; ++encode_size) {
    EXPECT_CALL(*mock_encoder_, EncodeImpl(_, _, _))
        .WillOnce(Invoke(MockAudioEncoder::FakeEncoding(encode_size)));
  }

  // First call is a special case, since it does not include a secondary
  // payload.
  Encode();
  EXPECT_EQ(0u, encoded_info_.redundant.size());
  EXPECT_EQ(kRedLastHeaderLength + 1u, encoded_info_.encoded_bytes);

  // Second call is also special since it does not include a tertiary
  // payload.
  Encode();
  EXPECT_EQ(2u, encoded_info_.redundant.size());
  EXPECT_EQ(8u, encoded_info_.encoded_bytes);

  for (size_t i = 3; i <= kNumPackets; ++i) {
    Encode();
    ASSERT_EQ(3u, encoded_info_.redundant.size());
    EXPECT_EQ(i, encoded_info_.redundant[2].encoded_bytes);
    EXPECT_EQ(i - 1, encoded_info_.redundant[1].encoded_bytes);
    EXPECT_EQ(i - 2, encoded_info_.redundant[0].encoded_bytes);
    EXPECT_EQ(9 + i + (i - 1) + (i - 2), encoded_info_.encoded_bytes);
  }
}

// Checks that the correct payload sizes are populated into the redundancy
// information for a redundancy level of 3.
TEST_F(AudioEncoderCopyRedTest, CheckPayloadSizes3) {
  webrtc::test::ScopedKeyValueConfig field_trials(
      field_trials_, "WebRTC-Audio-Red-For-Opus/Enabled-3/");
  // Recreate the RED encoder to take the new field trial setting into account.
  AudioEncoderCopyRed::Config config;
  config.payload_type = red_payload_type_;
  config.speech_encoder = std::move(red_->ReclaimContainedEncoders()[0]);
  red_.reset(new AudioEncoderCopyRed(std::move(config), field_trials_));

  // Let the mock encoder return payload sizes 1, 2, 3, ..., 10 for the sequence
  // of calls.
  static const int kNumPackets = 10;
  InSequence s;
  for (int encode_size = 1; encode_size <= kNumPackets; ++encode_size) {
    EXPECT_CALL(*mock_encoder_, EncodeImpl(_, _, _))
        .WillOnce(Invoke(MockAudioEncoder::FakeEncoding(encode_size)));
  }

  // First call is a special case, since it does not include a secondary
  // payload.
  Encode();
  EXPECT_EQ(0u, encoded_info_.redundant.size());
  EXPECT_EQ(kRedLastHeaderLength + 1u, encoded_info_.encoded_bytes);

  // Second call is also special since it does not include a tertiary
  // payload.
  Encode();
  EXPECT_EQ(2u, encoded_info_.redundant.size());
  EXPECT_EQ(8u, encoded_info_.encoded_bytes);

  // Third call is also special since it does not include a quaternary
  // payload.
  Encode();
  EXPECT_EQ(3u, encoded_info_.redundant.size());
  EXPECT_EQ(15u, encoded_info_.encoded_bytes);

  for (size_t i = 4; i <= kNumPackets; ++i) {
    Encode();
    ASSERT_EQ(4u, encoded_info_.redundant.size());
    EXPECT_EQ(i, encoded_info_.redundant[3].encoded_bytes);
    EXPECT_EQ(i - 1, encoded_info_.redundant[2].encoded_bytes);
    EXPECT_EQ(i - 2, encoded_info_.redundant[1].encoded_bytes);
    EXPECT_EQ(i - 3, encoded_info_.redundant[0].encoded_bytes);
    EXPECT_EQ(13 + i + (i - 1) + (i - 2) + (i - 3),
              encoded_info_.encoded_bytes);
  }
}

// Checks that packets encoded larger than REDs 1024 maximum are returned as-is.
TEST_F(AudioEncoderCopyRedTest, VeryLargePacket) {
  AudioEncoder::EncodedInfo info;
  info.payload_type = 63;
  info.encoded_bytes =
      1111;  // Must be > 1024 which is the maximum size encodable by RED.
  info.encoded_timestamp = timestamp_;

  EXPECT_CALL(*mock_encoder_, EncodeImpl(_, _, _))
      .WillOnce(Invoke(MockAudioEncoder::FakeEncoding(info)));

  Encode();
  ASSERT_EQ(0u, encoded_info_.redundant.size());
  ASSERT_EQ(info.encoded_bytes, encoded_info_.encoded_bytes);
  ASSERT_EQ(info.payload_type, encoded_info_.payload_type);
}

// Checks that the correct timestamps are returned.
TEST_F(AudioEncoderCopyRedTest, CheckTimestamps) {
  uint32_t primary_timestamp = timestamp_;
  AudioEncoder::EncodedInfo info;
  info.encoded_bytes = 17;
  info.encoded_timestamp = timestamp_;

  EXPECT_CALL(*mock_encoder_, EncodeImpl(_, _, _))
      .WillOnce(Invoke(MockAudioEncoder::FakeEncoding(info)));

  // First call is a special case, since it does not include a secondary
  // payload.
  Encode();
  EXPECT_EQ(primary_timestamp, encoded_info_.encoded_timestamp);

  uint32_t secondary_timestamp = primary_timestamp;
  primary_timestamp = timestamp_;
  info.encoded_timestamp = timestamp_;
  EXPECT_CALL(*mock_encoder_, EncodeImpl(_, _, _))
      .WillOnce(Invoke(MockAudioEncoder::FakeEncoding(info)));

  Encode();
  ASSERT_EQ(2u, encoded_info_.redundant.size());
  EXPECT_EQ(primary_timestamp, encoded_info_.redundant[1].encoded_timestamp);
  EXPECT_EQ(secondary_timestamp, encoded_info_.redundant[0].encoded_timestamp);
  EXPECT_EQ(primary_timestamp, encoded_info_.encoded_timestamp);
}

// Checks that the primary and secondary payloads are written correctly.
TEST_F(AudioEncoderCopyRedTest, CheckPayloads) {
  // Let the mock encoder write payloads with increasing values. The first
  // payload will have values 0, 1, 2, ..., kPayloadLenBytes - 1.
  static const size_t kPayloadLenBytes = 5;
  static const size_t kHeaderLenBytes = 5;
  uint8_t payload[kPayloadLenBytes];
  for (uint8_t i = 0; i < kPayloadLenBytes; ++i) {
    payload[i] = i;
  }
  EXPECT_CALL(*mock_encoder_, EncodeImpl(_, _, _))
      .WillRepeatedly(Invoke(MockAudioEncoder::CopyEncoding(payload)));

  // First call is a special case, since it does not include a secondary
  // payload.
  Encode();
  EXPECT_EQ(kRedLastHeaderLength + kPayloadLenBytes,
            encoded_info_.encoded_bytes);
  for (size_t i = 0; i < kPayloadLenBytes; ++i) {
    EXPECT_EQ(i, encoded_.data()[kRedLastHeaderLength + i]);
  }

  for (int j = 0; j < 1; ++j) {
    // Increment all values of the payload by 10.
    for (size_t i = 0; i < kPayloadLenBytes; ++i)
      payload[i] += 10;

    Encode();
    ASSERT_EQ(2u, encoded_info_.redundant.size());
    EXPECT_EQ(kPayloadLenBytes, encoded_info_.redundant[0].encoded_bytes);
    EXPECT_EQ(kPayloadLenBytes, encoded_info_.redundant[1].encoded_bytes);
    for (size_t i = 0; i < kPayloadLenBytes; ++i) {
      // Check secondary payload.
      EXPECT_EQ(j * 10 + i, encoded_.data()[kHeaderLenBytes + i]);

      // Check primary payload.
      EXPECT_EQ((j + 1) * 10 + i,
                encoded_.data()[kHeaderLenBytes + i + kPayloadLenBytes]);
    }
  }
}

// Checks correct propagation of payload type.
TEST_F(AudioEncoderCopyRedTest, CheckPayloadType) {
  const int primary_payload_type = red_payload_type_ + 1;
  AudioEncoder::EncodedInfo info;
  info.encoded_bytes = 17;
  info.payload_type = primary_payload_type;
  EXPECT_CALL(*mock_encoder_, EncodeImpl(_, _, _))
      .WillOnce(Invoke(MockAudioEncoder::FakeEncoding(info)));

  // First call is a special case, since it does not include a secondary
  // payload.
  Encode();
  ASSERT_EQ(0u, encoded_info_.redundant.size());

  const int secondary_payload_type = red_payload_type_ + 2;
  info.payload_type = secondary_payload_type;
  EXPECT_CALL(*mock_encoder_, EncodeImpl(_, _, _))
      .WillOnce(Invoke(MockAudioEncoder::FakeEncoding(info)));

  Encode();
  ASSERT_EQ(2u, encoded_info_.redundant.size());
  EXPECT_EQ(secondary_payload_type, encoded_info_.redundant[1].payload_type);
  EXPECT_EQ(primary_payload_type, encoded_info_.redundant[0].payload_type);
  EXPECT_EQ(red_payload_type_, encoded_info_.payload_type);
}

TEST_F(AudioEncoderCopyRedTest, CheckRFC2198Header) {
  const int primary_payload_type = red_payload_type_ + 1;
  AudioEncoder::EncodedInfo info;
  info.encoded_bytes = 10;
  info.encoded_timestamp = timestamp_;
  info.payload_type = primary_payload_type;

  EXPECT_CALL(*mock_encoder_, EncodeImpl(_, _, _))
      .WillOnce(Invoke(MockAudioEncoder::FakeEncoding(info)));
  Encode();
  info.encoded_timestamp = timestamp_;  // update timestamp.
  EXPECT_CALL(*mock_encoder_, EncodeImpl(_, _, _))
      .WillOnce(Invoke(MockAudioEncoder::FakeEncoding(info)));
  Encode();  // Second call will produce a redundant encoding.

  EXPECT_EQ(encoded_.size(),
            5u + 2 * 10u);  // header size + two encoded payloads.
  EXPECT_EQ(encoded_[0], primary_payload_type | 0x80);

  uint32_t timestamp_delta = encoded_info_.encoded_timestamp -
                             encoded_info_.redundant[0].encoded_timestamp;
  // Timestamp delta is encoded as a 14 bit value.
  EXPECT_EQ(encoded_[1], timestamp_delta >> 6);
  EXPECT_EQ(static_cast<uint8_t>(encoded_[2] >> 2), timestamp_delta & 0x3f);
  // Redundant length is encoded as 10 bit value.
  EXPECT_EQ(encoded_[2] & 0x3u, encoded_info_.redundant[1].encoded_bytes >> 8);
  EXPECT_EQ(encoded_[3], encoded_info_.redundant[1].encoded_bytes & 0xff);
  EXPECT_EQ(encoded_[4], primary_payload_type);

  EXPECT_CALL(*mock_encoder_, EncodeImpl(_, _, _))
      .WillOnce(Invoke(MockAudioEncoder::FakeEncoding(info)));
  Encode();  // Third call will produce a redundant encoding with double
             // redundancy.

  EXPECT_EQ(encoded_.size(),
            5u + 2 * 10u);  // header size + two encoded payloads.
  EXPECT_EQ(encoded_[0], primary_payload_type | 0x80);

  timestamp_delta = encoded_info_.encoded_timestamp -
                    encoded_info_.redundant[0].encoded_timestamp;
  // Timestamp delta is encoded as a 14 bit value.
  EXPECT_EQ(encoded_[1], timestamp_delta >> 6);
  EXPECT_EQ(static_cast<uint8_t>(encoded_[2] >> 2), timestamp_delta & 0x3f);
  // Redundant length is encoded as 10 bit value.
  EXPECT_EQ(encoded_[2] & 0x3u, encoded_info_.redundant[1].encoded_bytes >> 8);
  EXPECT_EQ(encoded_[3], encoded_info_.redundant[1].encoded_bytes & 0xff);

  EXPECT_EQ(encoded_[4], primary_payload_type);
  timestamp_delta = encoded_info_.encoded_timestamp -
                    encoded_info_.redundant[1].encoded_timestamp;
}

// Variant with a redundancy of 0.
TEST_F(AudioEncoderCopyRedTest, CheckRFC2198Header0) {
  webrtc::test::ScopedKeyValueConfig field_trials(
      field_trials_, "WebRTC-Audio-Red-For-Opus/Enabled-0/");
  // Recreate the RED encoder to take the new field trial setting into account.
  AudioEncoderCopyRed::Config config;
  config.payload_type = red_payload_type_;
  config.speech_encoder = std::move(red_->ReclaimContainedEncoders()[0]);
  red_.reset(new AudioEncoderCopyRed(std::move(config), field_trials));

  const int primary_payload_type = red_payload_type_ + 1;
  AudioEncoder::EncodedInfo info;
  info.encoded_bytes = 10;
  info.encoded_timestamp = timestamp_;
  info.payload_type = primary_payload_type;

  EXPECT_CALL(*mock_encoder_, EncodeImpl(_, _, _))
      .WillOnce(Invoke(MockAudioEncoder::FakeEncoding(info)));
  Encode();
  info.encoded_timestamp = timestamp_;  // update timestamp.
  EXPECT_CALL(*mock_encoder_, EncodeImpl(_, _, _))
      .WillOnce(Invoke(MockAudioEncoder::FakeEncoding(info)));
  Encode();  // Second call will not produce a redundant encoding.

  EXPECT_EQ(encoded_.size(),
            1u + 1 * 10u);  // header size + one encoded payloads.
  EXPECT_EQ(encoded_[0], primary_payload_type);
}
// Variant with a redundancy of 2.
TEST_F(AudioEncoderCopyRedTest, CheckRFC2198Header2) {
  webrtc::test::ScopedKeyValueConfig field_trials(
      field_trials_, "WebRTC-Audio-Red-For-Opus/Enabled-2/");
  // Recreate the RED encoder to take the new field trial setting into account.
  AudioEncoderCopyRed::Config config;
  config.payload_type = red_payload_type_;
  config.speech_encoder = std::move(red_->ReclaimContainedEncoders()[0]);
  red_.reset(new AudioEncoderCopyRed(std::move(config), field_trials));

  const int primary_payload_type = red_payload_type_ + 1;
  AudioEncoder::EncodedInfo info;
  info.encoded_bytes = 10;
  info.encoded_timestamp = timestamp_;
  info.payload_type = primary_payload_type;

  EXPECT_CALL(*mock_encoder_, EncodeImpl(_, _, _))
      .WillOnce(Invoke(MockAudioEncoder::FakeEncoding(info)));
  Encode();
  info.encoded_timestamp = timestamp_;  // update timestamp.
  EXPECT_CALL(*mock_encoder_, EncodeImpl(_, _, _))
      .WillOnce(Invoke(MockAudioEncoder::FakeEncoding(info)));
  Encode();  // Second call will produce a redundant encoding.

  EXPECT_EQ(encoded_.size(),
            5u + 2 * 10u);  // header size + two encoded payloads.
  EXPECT_EQ(encoded_[0], primary_payload_type | 0x80);

  uint32_t timestamp_delta = encoded_info_.encoded_timestamp -
                             encoded_info_.redundant[0].encoded_timestamp;
  // Timestamp delta is encoded as a 14 bit value.
  EXPECT_EQ(encoded_[1], timestamp_delta >> 6);
  EXPECT_EQ(static_cast<uint8_t>(encoded_[2] >> 2), timestamp_delta & 0x3f);
  // Redundant length is encoded as 10 bit value.
  EXPECT_EQ(encoded_[2] & 0x3u, encoded_info_.redundant[1].encoded_bytes >> 8);
  EXPECT_EQ(encoded_[3], encoded_info_.redundant[1].encoded_bytes & 0xff);
  EXPECT_EQ(encoded_[4], primary_payload_type);

  EXPECT_CALL(*mock_encoder_, EncodeImpl(_, _, _))
      .WillOnce(Invoke(MockAudioEncoder::FakeEncoding(info)));
  Encode();  // Third call will produce a redundant encoding with double
             // redundancy.

  EXPECT_EQ(encoded_.size(),
            9u + 3 * 10u);  // header size + three encoded payloads.
  EXPECT_EQ(encoded_[0], primary_payload_type | 0x80);

  timestamp_delta = encoded_info_.encoded_timestamp -
                    encoded_info_.redundant[0].encoded_timestamp;
  // Timestamp delta is encoded as a 14 bit value.
  EXPECT_EQ(encoded_[1], timestamp_delta >> 6);
  EXPECT_EQ(static_cast<uint8_t>(encoded_[2] >> 2), timestamp_delta & 0x3f);
  // Redundant length is encoded as 10 bit value.
  EXPECT_EQ(encoded_[2] & 0x3u, encoded_info_.redundant[1].encoded_bytes >> 8);
  EXPECT_EQ(encoded_[3], encoded_info_.redundant[1].encoded_bytes & 0xff);

  EXPECT_EQ(encoded_[4], primary_payload_type | 0x80);
  timestamp_delta = encoded_info_.encoded_timestamp -
                    encoded_info_.redundant[1].encoded_timestamp;
  // Timestamp delta is encoded as a 14 bit value.
  EXPECT_EQ(encoded_[5], timestamp_delta >> 6);
  EXPECT_EQ(static_cast<uint8_t>(encoded_[6] >> 2), timestamp_delta & 0x3f);
  // Redundant length is encoded as 10 bit value.
  EXPECT_EQ(encoded_[6] & 0x3u, encoded_info_.redundant[1].encoded_bytes >> 8);
  EXPECT_EQ(encoded_[7], encoded_info_.redundant[1].encoded_bytes & 0xff);
  EXPECT_EQ(encoded_[8], primary_payload_type);
}

TEST_F(AudioEncoderCopyRedTest, RespectsPayloadMTU) {
  const int primary_payload_type = red_payload_type_ + 1;
  AudioEncoder::EncodedInfo info;
  info.encoded_bytes = 600;
  info.encoded_timestamp = timestamp_;
  info.payload_type = primary_payload_type;

  EXPECT_CALL(*mock_encoder_, EncodeImpl(_, _, _))
      .WillOnce(Invoke(MockAudioEncoder::FakeEncoding(info)));
  Encode();
  info.encoded_timestamp = timestamp_;  // update timestamp.
  info.encoded_bytes = 500;
  EXPECT_CALL(*mock_encoder_, EncodeImpl(_, _, _))
      .WillOnce(Invoke(MockAudioEncoder::FakeEncoding(info)));
  Encode();  // Second call will produce a redundant encoding.

  EXPECT_EQ(encoded_.size(), 5u + 600u + 500u);

  info.encoded_timestamp = timestamp_;  // update timestamp.
  info.encoded_bytes = 400;
  EXPECT_CALL(*mock_encoder_, EncodeImpl(_, _, _))
      .WillOnce(Invoke(MockAudioEncoder::FakeEncoding(info)));
  Encode();  // Third call will drop the oldest packet.
  EXPECT_EQ(encoded_.size(), 5u + 500u + 400u);
}

TEST_F(AudioEncoderCopyRedTest, LargeTimestampGap) {
  const int primary_payload_type = red_payload_type_ + 1;
  AudioEncoder::EncodedInfo info;
  info.encoded_bytes = 100;
  info.encoded_timestamp = timestamp_;
  info.payload_type = primary_payload_type;

  EXPECT_CALL(*mock_encoder_, EncodeImpl(_, _, _))
      .WillOnce(Invoke(MockAudioEncoder::FakeEncoding(info)));
  Encode();
  // Update timestamp to simulate a 400ms gap like the one
  // opus DTX causes.
  timestamp_ += 19200;
  info.encoded_timestamp = timestamp_;  // update timestamp.
  info.encoded_bytes = 200;
  EXPECT_CALL(*mock_encoder_, EncodeImpl(_, _, _))
      .WillOnce(Invoke(MockAudioEncoder::FakeEncoding(info)));
  Encode();

  // The old packet will be dropped.
  EXPECT_EQ(encoded_.size(), 1u + 200u);
}

#if GTEST_HAS_DEATH_TEST && !defined(WEBRTC_ANDROID)

// This test fixture tests various error conditions that makes the
// AudioEncoderCng die via CHECKs.
class AudioEncoderCopyRedDeathTest : public AudioEncoderCopyRedTest {
 protected:
  AudioEncoderCopyRedDeathTest() : AudioEncoderCopyRedTest() {}
};

TEST_F(AudioEncoderCopyRedDeathTest, WrongFrameSize) {
  num_audio_samples_10ms *= 2;  // 20 ms frame.
  RTC_EXPECT_DEATH(Encode(), "");
  num_audio_samples_10ms = 0;  // Zero samples.
  RTC_EXPECT_DEATH(Encode(), "");
}

TEST_F(AudioEncoderCopyRedDeathTest, NullSpeechEncoder) {
  test::ScopedKeyValueConfig field_trials;
  AudioEncoderCopyRed* red = NULL;
  AudioEncoderCopyRed::Config config;
  config.speech_encoder = NULL;
  RTC_EXPECT_DEATH(
      red = new AudioEncoderCopyRed(std::move(config), field_trials),
      "Speech encoder not provided.");
  // The delete operation is needed to avoid leak reports from memcheck.
  delete red;
}

#endif  // GTEST_HAS_DEATH_TEST && !defined(WEBRTC_ANDROID)

}  // namespace webrtc
