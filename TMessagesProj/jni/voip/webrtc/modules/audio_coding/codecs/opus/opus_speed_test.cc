/*
 *  Copyright (c) 2014 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "modules/audio_coding/codecs/opus/opus_interface.h"
#include "modules/audio_coding/codecs/tools/audio_codec_speed_test.h"

using ::std::string;

namespace webrtc {

static const int kOpusBlockDurationMs = 20;
static const int kOpusSamplingKhz = 48;

class OpusSpeedTest : public AudioCodecSpeedTest {
 protected:
  OpusSpeedTest();
  void SetUp() override;
  void TearDown() override;
  float EncodeABlock(int16_t* in_data,
                     uint8_t* bit_stream,
                     size_t max_bytes,
                     size_t* encoded_bytes) override;
  float DecodeABlock(const uint8_t* bit_stream,
                     size_t encoded_bytes,
                     int16_t* out_data) override;
  WebRtcOpusEncInst* opus_encoder_;
  WebRtcOpusDecInst* opus_decoder_;
};

OpusSpeedTest::OpusSpeedTest()
    : AudioCodecSpeedTest(kOpusBlockDurationMs,
                          kOpusSamplingKhz,
                          kOpusSamplingKhz),
      opus_encoder_(NULL),
      opus_decoder_(NULL) {}

void OpusSpeedTest::SetUp() {
  AudioCodecSpeedTest::SetUp();
  // If channels_ == 1, use Opus VOIP mode, otherwise, audio mode.
  int app = channels_ == 1 ? 0 : 1;
  /* Create encoder memory. */
  EXPECT_EQ(0, WebRtcOpus_EncoderCreate(&opus_encoder_, channels_, app, 48000));
  EXPECT_EQ(0, WebRtcOpus_DecoderCreate(&opus_decoder_, channels_, 48000));
  /* Set bitrate. */
  EXPECT_EQ(0, WebRtcOpus_SetBitRate(opus_encoder_, bit_rate_));
}

void OpusSpeedTest::TearDown() {
  AudioCodecSpeedTest::TearDown();
  /* Free memory. */
  EXPECT_EQ(0, WebRtcOpus_EncoderFree(opus_encoder_));
  EXPECT_EQ(0, WebRtcOpus_DecoderFree(opus_decoder_));
}

float OpusSpeedTest::EncodeABlock(int16_t* in_data,
                                  uint8_t* bit_stream,
                                  size_t max_bytes,
                                  size_t* encoded_bytes) {
  clock_t clocks = clock();
  int value = WebRtcOpus_Encode(opus_encoder_, in_data, input_length_sample_,
                                max_bytes, bit_stream);
  clocks = clock() - clocks;
  EXPECT_GT(value, 0);
  *encoded_bytes = static_cast<size_t>(value);
  return 1000.0 * clocks / CLOCKS_PER_SEC;
}

float OpusSpeedTest::DecodeABlock(const uint8_t* bit_stream,
                                  size_t encoded_bytes,
                                  int16_t* out_data) {
  int value;
  int16_t audio_type;
  clock_t clocks = clock();
  value = WebRtcOpus_Decode(opus_decoder_, bit_stream, encoded_bytes, out_data,
                            &audio_type);
  clocks = clock() - clocks;
  EXPECT_EQ(output_length_sample_, static_cast<size_t>(value));
  return 1000.0 * clocks / CLOCKS_PER_SEC;
}

/* Test audio length in second. */
constexpr size_t kDurationSec = 400;

#define ADD_TEST(complexity)                                           \
  TEST_P(OpusSpeedTest, OpusSetComplexityTest##complexity) {           \
    /* Set complexity. */                                              \
    printf("Setting complexity to %d ...\n", complexity);              \
    EXPECT_EQ(0, WebRtcOpus_SetComplexity(opus_encoder_, complexity)); \
    EncodeDecode(kDurationSec);                                        \
  }

ADD_TEST(10)
ADD_TEST(9)
ADD_TEST(8)
ADD_TEST(7)
ADD_TEST(6)
ADD_TEST(5)
ADD_TEST(4)
ADD_TEST(3)
ADD_TEST(2)
ADD_TEST(1)
ADD_TEST(0)

#define ADD_BANDWIDTH_TEST(bandwidth)                                \
  TEST_P(OpusSpeedTest, OpusSetBandwidthTest##bandwidth) {           \
    /* Set bandwidth. */                                             \
    printf("Setting bandwidth to %d ...\n", bandwidth);              \
    EXPECT_EQ(0, WebRtcOpus_SetBandwidth(opus_encoder_, bandwidth)); \
    EncodeDecode(kDurationSec);                                      \
  }

ADD_BANDWIDTH_TEST(OPUS_BANDWIDTH_NARROWBAND)
ADD_BANDWIDTH_TEST(OPUS_BANDWIDTH_MEDIUMBAND)
ADD_BANDWIDTH_TEST(OPUS_BANDWIDTH_WIDEBAND)
ADD_BANDWIDTH_TEST(OPUS_BANDWIDTH_SUPERWIDEBAND)
ADD_BANDWIDTH_TEST(OPUS_BANDWIDTH_FULLBAND)

// List all test cases: (channel, bit rat, filename, extension).
const coding_param param_set[] = {
    std::make_tuple(1,
                    64000,
                    string("audio_coding/speech_mono_32_48kHz"),
                    string("pcm"),
                    true),
    std::make_tuple(1,
                    32000,
                    string("audio_coding/speech_mono_32_48kHz"),
                    string("pcm"),
                    true),
    std::make_tuple(2,
                    64000,
                    string("audio_coding/music_stereo_48kHz"),
                    string("pcm"),
                    true)};

INSTANTIATE_TEST_SUITE_P(AllTest,
                         OpusSpeedTest,
                         ::testing::ValuesIn(param_set));

}  // namespace webrtc
