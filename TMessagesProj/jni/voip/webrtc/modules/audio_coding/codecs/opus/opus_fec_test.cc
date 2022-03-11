/*
 *  Copyright (c) 2014 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include <memory>

#include "modules/audio_coding/codecs/opus/opus_interface.h"
#include "rtc_base/format_macros.h"
#include "test/gtest.h"
#include "test/testsupport/file_utils.h"

using std::get;
using std::string;
using std::tuple;
using ::testing::TestWithParam;

namespace webrtc {

// Define coding parameter as <channels, bit_rate, filename, extension>.
typedef tuple<size_t, int, string, string> coding_param;
typedef struct mode mode;

struct mode {
  bool fec;
  uint8_t target_packet_loss_rate;
};

const int kOpusBlockDurationMs = 20;
const int kOpusSamplingKhz = 48;

class OpusFecTest : public TestWithParam<coding_param> {
 protected:
  OpusFecTest();

  void SetUp() override;
  void TearDown() override;

  virtual void EncodeABlock();

  virtual void DecodeABlock(bool lost_previous, bool lost_current);

  int block_duration_ms_;
  int sampling_khz_;
  size_t block_length_sample_;

  size_t channels_;
  int bit_rate_;

  size_t data_pointer_;
  size_t loop_length_samples_;
  size_t max_bytes_;
  size_t encoded_bytes_;

  WebRtcOpusEncInst* opus_encoder_;
  WebRtcOpusDecInst* opus_decoder_;

  string in_filename_;

  std::unique_ptr<int16_t[]> in_data_;
  std::unique_ptr<int16_t[]> out_data_;
  std::unique_ptr<uint8_t[]> bit_stream_;
};

void OpusFecTest::SetUp() {
  channels_ = get<0>(GetParam());
  bit_rate_ = get<1>(GetParam());
  printf("Coding %" RTC_PRIuS " channel signal at %d bps.\n", channels_,
         bit_rate_);

  in_filename_ = test::ResourcePath(get<2>(GetParam()), get<3>(GetParam()));

  FILE* fp = fopen(in_filename_.c_str(), "rb");
  ASSERT_FALSE(fp == NULL);

  // Obtain file size.
  fseek(fp, 0, SEEK_END);
  loop_length_samples_ = ftell(fp) / sizeof(int16_t);
  rewind(fp);

  // Allocate memory to contain the whole file.
  in_data_.reset(
      new int16_t[loop_length_samples_ + block_length_sample_ * channels_]);

  // Copy the file into the buffer.
  ASSERT_EQ(fread(&in_data_[0], sizeof(int16_t), loop_length_samples_, fp),
            loop_length_samples_);
  fclose(fp);

  // The audio will be used in a looped manner. To ease the acquisition of an
  // audio frame that crosses the end of the excerpt, we add an extra block
  // length of samples to the end of the array, starting over again from the
  // beginning of the array. Audio frames cross the end of the excerpt always
  // appear as a continuum of memory.
  memcpy(&in_data_[loop_length_samples_], &in_data_[0],
         block_length_sample_ * channels_ * sizeof(int16_t));

  // Maximum number of bytes in output bitstream.
  max_bytes_ = block_length_sample_ * channels_ * sizeof(int16_t);

  out_data_.reset(new int16_t[2 * block_length_sample_ * channels_]);
  bit_stream_.reset(new uint8_t[max_bytes_]);

  // If channels_ == 1, use Opus VOIP mode, otherwise, audio mode.
  int app = channels_ == 1 ? 0 : 1;

  // Create encoder memory.
  EXPECT_EQ(0, WebRtcOpus_EncoderCreate(&opus_encoder_, channels_, app, 48000));
  EXPECT_EQ(0, WebRtcOpus_DecoderCreate(&opus_decoder_, channels_, 48000));
  // Set bitrate.
  EXPECT_EQ(0, WebRtcOpus_SetBitRate(opus_encoder_, bit_rate_));
}

void OpusFecTest::TearDown() {
  // Free memory.
  EXPECT_EQ(0, WebRtcOpus_EncoderFree(opus_encoder_));
  EXPECT_EQ(0, WebRtcOpus_DecoderFree(opus_decoder_));
}

OpusFecTest::OpusFecTest()
    : block_duration_ms_(kOpusBlockDurationMs),
      sampling_khz_(kOpusSamplingKhz),
      block_length_sample_(
          static_cast<size_t>(block_duration_ms_ * sampling_khz_)),
      data_pointer_(0),
      max_bytes_(0),
      encoded_bytes_(0),
      opus_encoder_(NULL),
      opus_decoder_(NULL) {}

void OpusFecTest::EncodeABlock() {
  int value =
      WebRtcOpus_Encode(opus_encoder_, &in_data_[data_pointer_],
                        block_length_sample_, max_bytes_, &bit_stream_[0]);
  EXPECT_GT(value, 0);

  encoded_bytes_ = static_cast<size_t>(value);
}

void OpusFecTest::DecodeABlock(bool lost_previous, bool lost_current) {
  int16_t audio_type;
  int value_1 = 0, value_2 = 0;

  if (lost_previous) {
    // Decode previous frame.
    if (!lost_current &&
        WebRtcOpus_PacketHasFec(&bit_stream_[0], encoded_bytes_) == 1) {
      value_1 =
          WebRtcOpus_DecodeFec(opus_decoder_, &bit_stream_[0], encoded_bytes_,
                               &out_data_[0], &audio_type);
    } else {
      // Call decoder PLC.
      while (value_1 < static_cast<int>(block_length_sample_)) {
        int ret = WebRtcOpus_Decode(opus_decoder_, NULL, 0, &out_data_[value_1],
                                    &audio_type);
        EXPECT_EQ(ret, sampling_khz_ * 10);  // Should return 10 ms of samples.
        value_1 += ret;
      }
    }
    EXPECT_EQ(static_cast<int>(block_length_sample_), value_1);
  }

  if (!lost_current) {
    // Decode current frame.
    value_2 = WebRtcOpus_Decode(opus_decoder_, &bit_stream_[0], encoded_bytes_,
                                &out_data_[value_1 * channels_], &audio_type);
    EXPECT_EQ(static_cast<int>(block_length_sample_), value_2);
  }
}

TEST_P(OpusFecTest, RandomPacketLossTest) {
  const int kDurationMs = 200000;
  int time_now_ms, fec_frames;
  int actual_packet_loss_rate;
  bool lost_current, lost_previous;
  mode mode_set[3] = {{true, 0}, {false, 0}, {true, 50}};

  lost_current = false;
  for (int i = 0; i < 3; i++) {
    if (mode_set[i].fec) {
      EXPECT_EQ(0, WebRtcOpus_EnableFec(opus_encoder_));
      EXPECT_EQ(0, WebRtcOpus_SetPacketLossRate(
                       opus_encoder_, mode_set[i].target_packet_loss_rate));
      printf("FEC is ON, target at packet loss rate %d percent.\n",
             mode_set[i].target_packet_loss_rate);
    } else {
      EXPECT_EQ(0, WebRtcOpus_DisableFec(opus_encoder_));
      printf("FEC is OFF.\n");
    }
    // In this test, we let the target packet loss rate match the actual rate.
    actual_packet_loss_rate = mode_set[i].target_packet_loss_rate;
    // Run every mode a certain time.
    time_now_ms = 0;
    fec_frames = 0;
    while (time_now_ms < kDurationMs) {
      // Encode & decode.
      EncodeABlock();

      // Check if payload has FEC.
      int fec = WebRtcOpus_PacketHasFec(&bit_stream_[0], encoded_bytes_);

      // If FEC is disabled or the target packet loss rate is set to 0, there
      // should be no FEC in the bit stream.
      if (!mode_set[i].fec || mode_set[i].target_packet_loss_rate == 0) {
        EXPECT_EQ(fec, 0);
      } else if (fec == 1) {
        fec_frames++;
      }

      lost_previous = lost_current;
      lost_current = rand() < actual_packet_loss_rate * (RAND_MAX / 100);
      DecodeABlock(lost_previous, lost_current);

      time_now_ms += block_duration_ms_;

      // `data_pointer_` is incremented and wrapped across
      // `loop_length_samples_`.
      data_pointer_ = (data_pointer_ + block_length_sample_ * channels_) %
                      loop_length_samples_;
    }
    if (mode_set[i].fec) {
      printf("%.2f percent frames has FEC.\n",
             static_cast<float>(fec_frames) * block_duration_ms_ / 2000);
    }
  }
}

const coding_param param_set[] = {
    std::make_tuple(1,
                    64000,
                    string("audio_coding/testfile32kHz"),
                    string("pcm")),
    std::make_tuple(1,
                    32000,
                    string("audio_coding/testfile32kHz"),
                    string("pcm")),
    std::make_tuple(2,
                    64000,
                    string("audio_coding/teststereo32kHz"),
                    string("pcm"))};

// 64 kbps, stereo
INSTANTIATE_TEST_SUITE_P(AllTest, OpusFecTest, ::testing::ValuesIn(param_set));

}  // namespace webrtc
