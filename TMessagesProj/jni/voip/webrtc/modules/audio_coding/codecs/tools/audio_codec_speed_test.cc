/*
 *  Copyright (c) 2014 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "modules/audio_coding/codecs/tools/audio_codec_speed_test.h"

#include "rtc_base/format_macros.h"
#include "test/gtest.h"
#include "test/testsupport/file_utils.h"

using ::std::get;

namespace webrtc {

AudioCodecSpeedTest::AudioCodecSpeedTest(int block_duration_ms,
                                         int input_sampling_khz,
                                         int output_sampling_khz)
    : block_duration_ms_(block_duration_ms),
      input_sampling_khz_(input_sampling_khz),
      output_sampling_khz_(output_sampling_khz),
      input_length_sample_(
          static_cast<size_t>(block_duration_ms_ * input_sampling_khz_)),
      output_length_sample_(
          static_cast<size_t>(block_duration_ms_ * output_sampling_khz_)),
      data_pointer_(0),
      loop_length_samples_(0),
      max_bytes_(0),
      encoded_bytes_(0),
      encoding_time_ms_(0.0),
      decoding_time_ms_(0.0),
      out_file_(NULL) {}

void AudioCodecSpeedTest::SetUp() {
  channels_ = get<0>(GetParam());
  bit_rate_ = get<1>(GetParam());
  in_filename_ = test::ResourcePath(get<2>(GetParam()), get<3>(GetParam()));
  save_out_data_ = get<4>(GetParam());

  FILE* fp = fopen(in_filename_.c_str(), "rb");
  assert(fp != NULL);

  // Obtain file size.
  fseek(fp, 0, SEEK_END);
  loop_length_samples_ = ftell(fp) / sizeof(int16_t);
  rewind(fp);

  // Allocate memory to contain the whole file.
  in_data_.reset(
      new int16_t[loop_length_samples_ + input_length_sample_ * channels_]);

  data_pointer_ = 0;

  // Copy the file into the buffer.
  ASSERT_EQ(fread(&in_data_[0], sizeof(int16_t), loop_length_samples_, fp),
            loop_length_samples_);
  fclose(fp);

  // Add an extra block length of samples to the end of the array, starting
  // over again from the beginning of the array. This is done to simplify
  // the reading process when reading over the end of the loop.
  memcpy(&in_data_[loop_length_samples_], &in_data_[0],
         input_length_sample_ * channels_ * sizeof(int16_t));

  max_bytes_ = input_length_sample_ * channels_ * sizeof(int16_t);
  out_data_.reset(new int16_t[output_length_sample_ * channels_]);
  bit_stream_.reset(new uint8_t[max_bytes_]);

  if (save_out_data_) {
    std::string out_filename =
        ::testing::UnitTest::GetInstance()->current_test_info()->name();

    // Erase '/'
    size_t found;
    while ((found = out_filename.find('/')) != std::string::npos)
      out_filename.replace(found, 1, "_");

    out_filename = test::OutputPath() + out_filename + ".pcm";

    out_file_ = fopen(out_filename.c_str(), "wb");
    assert(out_file_ != NULL);

    printf("Output to be saved in %s.\n", out_filename.c_str());
  }
}

void AudioCodecSpeedTest::TearDown() {
  if (save_out_data_) {
    fclose(out_file_);
  }
}

void AudioCodecSpeedTest::EncodeDecode(size_t audio_duration_sec) {
  size_t time_now_ms = 0;
  float time_ms;

  printf("Coding %d kHz-sampled %" RTC_PRIuS "-channel audio at %d bps ...\n",
         input_sampling_khz_, channels_, bit_rate_);

  while (time_now_ms < audio_duration_sec * 1000) {
    // Encode & decode.
    time_ms = EncodeABlock(&in_data_[data_pointer_], &bit_stream_[0],
                           max_bytes_, &encoded_bytes_);
    encoding_time_ms_ += time_ms;
    time_ms = DecodeABlock(&bit_stream_[0], encoded_bytes_, &out_data_[0]);
    decoding_time_ms_ += time_ms;
    if (save_out_data_) {
      fwrite(&out_data_[0], sizeof(int16_t), output_length_sample_ * channels_,
             out_file_);
    }
    data_pointer_ = (data_pointer_ + input_length_sample_ * channels_) %
                    loop_length_samples_;
    time_now_ms += block_duration_ms_;
  }

  printf("Encoding: %.2f%% real time,\nDecoding: %.2f%% real time.\n",
         (encoding_time_ms_ / audio_duration_sec) / 10.0,
         (decoding_time_ms_ / audio_duration_sec) / 10.0);
}

}  // namespace webrtc
