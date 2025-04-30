/*
 *  Copyright (c) 2011 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */
#include <memory>
#include <string>

#include "modules/audio_coding/codecs/cng/webrtc_cng.h"
#include "test/gtest.h"
#include "test/testsupport/file_utils.h"

namespace webrtc {

enum {
  kSidShortIntervalUpdate = 1,
  kSidNormalIntervalUpdate = 100,
  kSidLongIntervalUpdate = 10000
};

enum : size_t {
  kCNGNumParamsLow = 0,
  kCNGNumParamsNormal = 8,
  kCNGNumParamsHigh = WEBRTC_CNG_MAX_LPC_ORDER,
  kCNGNumParamsTooHigh = WEBRTC_CNG_MAX_LPC_ORDER + 1
};

enum { kNoSid, kForceSid };

class CngTest : public ::testing::Test {
 protected:
  virtual void SetUp();

  void TestCngEncode(int sample_rate_hz, int quality);

  int16_t speech_data_[640];  // Max size of CNG internal buffers.
};

class CngDeathTest : public CngTest {};

void CngTest::SetUp() {
  FILE* input_file;
  const std::string file_name =
      webrtc::test::ResourcePath("audio_coding/testfile32kHz", "pcm");
  input_file = fopen(file_name.c_str(), "rb");
  ASSERT_TRUE(input_file != NULL);
  ASSERT_EQ(640, static_cast<int32_t>(
                     fread(speech_data_, sizeof(int16_t), 640, input_file)));
  fclose(input_file);
  input_file = NULL;
}

void CngTest::TestCngEncode(int sample_rate_hz, int quality) {
  const size_t num_samples_10ms = rtc::CheckedDivExact(sample_rate_hz, 100);
  rtc::Buffer sid_data;

  ComfortNoiseEncoder cng_encoder(sample_rate_hz, kSidNormalIntervalUpdate,
                                  quality);
  EXPECT_EQ(0U, cng_encoder.Encode(rtc::ArrayView<const int16_t>(
                                       speech_data_, num_samples_10ms),
                                   kNoSid, &sid_data));
  EXPECT_EQ(static_cast<size_t>(quality + 1),
            cng_encoder.Encode(
                rtc::ArrayView<const int16_t>(speech_data_, num_samples_10ms),
                kForceSid, &sid_data));
}

#if GTEST_HAS_DEATH_TEST && !defined(WEBRTC_ANDROID)
// Create CNG encoder, init with faulty values, free CNG encoder.
TEST_F(CngDeathTest, CngInitFail) {
  // Call with too few parameters.
  EXPECT_DEATH(
      {
        ComfortNoiseEncoder(8000, kSidNormalIntervalUpdate, kCNGNumParamsLow);
      },
      "");
  // Call with too many parameters.
  EXPECT_DEATH(
      {
        ComfortNoiseEncoder(8000, kSidNormalIntervalUpdate,
                            kCNGNumParamsTooHigh);
      },
      "");
}

// Encode Cng with too long input vector.
TEST_F(CngDeathTest, CngEncodeTooLong) {
  rtc::Buffer sid_data;

  // Create encoder.
  ComfortNoiseEncoder cng_encoder(8000, kSidNormalIntervalUpdate,
                                  kCNGNumParamsNormal);
  // Run encoder with too much data.
  EXPECT_DEATH(
      cng_encoder.Encode(rtc::ArrayView<const int16_t>(speech_data_, 641),
                         kNoSid, &sid_data),
      "");
}
#endif  // GTEST_HAS_DEATH_TEST && !defined(WEBRTC_ANDROID)

TEST_F(CngTest, CngEncode8000) {
  TestCngEncode(8000, kCNGNumParamsNormal);
}

TEST_F(CngTest, CngEncode16000) {
  TestCngEncode(16000, kCNGNumParamsNormal);
}

TEST_F(CngTest, CngEncode32000) {
  TestCngEncode(32000, kCNGNumParamsHigh);
}

TEST_F(CngTest, CngEncode48000) {
  TestCngEncode(48000, kCNGNumParamsNormal);
}

TEST_F(CngTest, CngEncode64000) {
  TestCngEncode(64000, kCNGNumParamsNormal);
}

// Update SID parameters, for both 9 and 16 parameters.
TEST_F(CngTest, CngUpdateSid) {
  rtc::Buffer sid_data;

  // Create and initialize encoder and decoder.
  ComfortNoiseEncoder cng_encoder(16000, kSidNormalIntervalUpdate,
                                  kCNGNumParamsNormal);
  ComfortNoiseDecoder cng_decoder;

  // Run normal Encode and UpdateSid.
  EXPECT_EQ(kCNGNumParamsNormal + 1,
            cng_encoder.Encode(rtc::ArrayView<const int16_t>(speech_data_, 160),
                               kForceSid, &sid_data));
  cng_decoder.UpdateSid(sid_data);

  // Reinit with new length.
  cng_encoder.Reset(16000, kSidNormalIntervalUpdate, kCNGNumParamsHigh);
  cng_decoder.Reset();

  // Expect 0 because of unstable parameters after switching length.
  EXPECT_EQ(0U,
            cng_encoder.Encode(rtc::ArrayView<const int16_t>(speech_data_, 160),
                               kForceSid, &sid_data));
  EXPECT_EQ(
      kCNGNumParamsHigh + 1,
      cng_encoder.Encode(rtc::ArrayView<const int16_t>(speech_data_ + 160, 160),
                         kForceSid, &sid_data));
  cng_decoder.UpdateSid(
      rtc::ArrayView<const uint8_t>(sid_data.data(), kCNGNumParamsNormal + 1));
}

// Update SID parameters, with wrong parameters or without calling decode.
TEST_F(CngTest, CngUpdateSidErroneous) {
  rtc::Buffer sid_data;

  // Encode.
  ComfortNoiseEncoder cng_encoder(16000, kSidNormalIntervalUpdate,
                                  kCNGNumParamsNormal);
  ComfortNoiseDecoder cng_decoder;
  EXPECT_EQ(kCNGNumParamsNormal + 1,
            cng_encoder.Encode(rtc::ArrayView<const int16_t>(speech_data_, 160),
                               kForceSid, &sid_data));

  // First run with valid parameters, then with too many CNG parameters.
  // The function will operate correctly by only reading the maximum number of
  // parameters, skipping the extra.
  EXPECT_EQ(kCNGNumParamsNormal + 1, sid_data.size());
  cng_decoder.UpdateSid(sid_data);

  // Make sure the input buffer is large enough. Since Encode() appends data, we
  // need to set the size manually only afterwards, or the buffer will be bigger
  // than anticipated.
  sid_data.SetSize(kCNGNumParamsTooHigh + 1);
  cng_decoder.UpdateSid(sid_data);
}

// Test to generate cng data, by forcing SID. Both normal and faulty condition.
TEST_F(CngTest, CngGenerate) {
  rtc::Buffer sid_data;
  int16_t out_data[640];

  // Create and initialize encoder and decoder.
  ComfortNoiseEncoder cng_encoder(16000, kSidNormalIntervalUpdate,
                                  kCNGNumParamsNormal);
  ComfortNoiseDecoder cng_decoder;

  // Normal Encode.
  EXPECT_EQ(kCNGNumParamsNormal + 1,
            cng_encoder.Encode(rtc::ArrayView<const int16_t>(speech_data_, 160),
                               kForceSid, &sid_data));

  // Normal UpdateSid.
  cng_decoder.UpdateSid(sid_data);

  // Two normal Generate, one with new_period.
  EXPECT_TRUE(cng_decoder.Generate(rtc::ArrayView<int16_t>(out_data, 640), 1));
  EXPECT_TRUE(cng_decoder.Generate(rtc::ArrayView<int16_t>(out_data, 640), 0));

  // Call Genereate with too much data.
  EXPECT_FALSE(cng_decoder.Generate(rtc::ArrayView<int16_t>(out_data, 641), 0));
}

// Test automatic SID.
TEST_F(CngTest, CngAutoSid) {
  rtc::Buffer sid_data;

  // Create and initialize encoder and decoder.
  ComfortNoiseEncoder cng_encoder(16000, kSidNormalIntervalUpdate,
                                  kCNGNumParamsNormal);
  ComfortNoiseDecoder cng_decoder;

  // Normal Encode, 100 msec, where no SID data should be generated.
  for (int i = 0; i < 10; i++) {
    EXPECT_EQ(
        0U, cng_encoder.Encode(rtc::ArrayView<const int16_t>(speech_data_, 160),
                               kNoSid, &sid_data));
  }

  // We have reached 100 msec, and SID data should be generated.
  EXPECT_EQ(kCNGNumParamsNormal + 1,
            cng_encoder.Encode(rtc::ArrayView<const int16_t>(speech_data_, 160),
                               kNoSid, &sid_data));
}

// Test automatic SID, with very short interval.
TEST_F(CngTest, CngAutoSidShort) {
  rtc::Buffer sid_data;

  // Create and initialize encoder and decoder.
  ComfortNoiseEncoder cng_encoder(16000, kSidShortIntervalUpdate,
                                  kCNGNumParamsNormal);
  ComfortNoiseDecoder cng_decoder;

  // First call will never generate SID, unless forced to.
  EXPECT_EQ(0U,
            cng_encoder.Encode(rtc::ArrayView<const int16_t>(speech_data_, 160),
                               kNoSid, &sid_data));

  // Normal Encode, 100 msec, SID data should be generated all the time.
  for (int i = 0; i < 10; i++) {
    EXPECT_EQ(
        kCNGNumParamsNormal + 1,
        cng_encoder.Encode(rtc::ArrayView<const int16_t>(speech_data_, 160),
                           kNoSid, &sid_data));
  }
}

}  // namespace webrtc
