/*
 *  Copyright (c) 2012 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

// Unit tests for DtmfToneGenerator class.

#include "modules/audio_coding/neteq/dtmf_tone_generator.h"

#include <math.h>

#include "common_audio/include/audio_util.h"
#include "modules/audio_coding/neteq/audio_multi_vector.h"
#include "rtc_base/strings/string_builder.h"
#include "test/gtest.h"

namespace webrtc {

class DtmfToneGeneratorTest : public ::testing::Test {
 protected:
  static const double kLowFreqHz[16];
  static const double kHighFreqHz[16];
  // This is the attenuation applied to all cases.
  const double kBaseAttenuation = 16141.0 / 16384.0;
  const double k3dbAttenuation = 23171.0 / 32768;
  const int kNumSamples = 10;

  void TestAllTones(int fs_hz, int channels) {
    AudioMultiVector signal(channels);

    for (int event = 0; event <= 15; ++event) {
      rtc::StringBuilder ss;
      ss << "Checking event " << event << " at sample rate " << fs_hz;
      SCOPED_TRACE(ss.str());
      const int kAttenuation = 0;
      ASSERT_EQ(0, tone_gen_.Init(fs_hz, event, kAttenuation));
      EXPECT_TRUE(tone_gen_.initialized());
      EXPECT_EQ(kNumSamples, tone_gen_.Generate(kNumSamples, &signal));

      double f1 = kLowFreqHz[event];
      double f2 = kHighFreqHz[event];
      const double pi = 3.14159265358979323846;

      for (int n = 0; n < kNumSamples; ++n) {
        double x = k3dbAttenuation * sin(2.0 * pi * f1 / fs_hz * (-n - 1)) +
                   sin(2.0 * pi * f2 / fs_hz * (-n - 1));
        x *= kBaseAttenuation;
        x = ldexp(x, 14);  // Scale to Q14.
        for (int channel = 0; channel < channels; ++channel) {
          EXPECT_NEAR(x, static_cast<double>(signal[channel][n]), 25);
        }
      }

      tone_gen_.Reset();
      EXPECT_FALSE(tone_gen_.initialized());
    }
  }

  void TestAmplitudes(int fs_hz, int channels) {
    AudioMultiVector signal(channels);
    AudioMultiVector ref_signal(channels);

    const int event_vec[] = {0, 4, 9, 13};  // Test a few events.
    for (int e = 0; e < 4; ++e) {
      int event = event_vec[e];
      // Create full-scale reference.
      ASSERT_EQ(0, tone_gen_.Init(fs_hz, event, 0));  // 0 attenuation.
      EXPECT_EQ(kNumSamples, tone_gen_.Generate(kNumSamples, &ref_signal));
      // Test every 5 steps (to save time).
      for (int attenuation = 1; attenuation <= 63; attenuation += 5) {
        rtc::StringBuilder ss;
        ss << "Checking event " << event << " at sample rate " << fs_hz;
        ss << "; attenuation " << attenuation;
        SCOPED_TRACE(ss.str());
        ASSERT_EQ(0, tone_gen_.Init(fs_hz, event, attenuation));
        EXPECT_EQ(kNumSamples, tone_gen_.Generate(kNumSamples, &signal));
        for (int n = 0; n < kNumSamples; ++n) {
          double attenuation_factor =
              DbToRatio(-static_cast<float>(attenuation));
          // Verify that the attenuation is correct.
          for (int channel = 0; channel < channels; ++channel) {
            EXPECT_NEAR(attenuation_factor * ref_signal[channel][n],
                        signal[channel][n], 2);
          }
        }

        tone_gen_.Reset();
      }
    }
  }

  DtmfToneGenerator tone_gen_;
};

// Low and high frequencies for events 0 through 15.
const double DtmfToneGeneratorTest::kLowFreqHz[16] = {
    941.0, 697.0, 697.0, 697.0, 770.0, 770.0, 770.0, 852.0,
    852.0, 852.0, 941.0, 941.0, 697.0, 770.0, 852.0, 941.0};
const double DtmfToneGeneratorTest::kHighFreqHz[16] = {
    1336.0, 1209.0, 1336.0, 1477.0, 1209.0, 1336.0, 1477.0, 1209.0,
    1336.0, 1477.0, 1209.0, 1477.0, 1633.0, 1633.0, 1633.0, 1633.0};

TEST_F(DtmfToneGeneratorTest, Test8000Mono) {
  TestAllTones(8000, 1);
  TestAmplitudes(8000, 1);
}

TEST_F(DtmfToneGeneratorTest, Test16000Mono) {
  TestAllTones(16000, 1);
  TestAmplitudes(16000, 1);
}

TEST_F(DtmfToneGeneratorTest, Test32000Mono) {
  TestAllTones(32000, 1);
  TestAmplitudes(32000, 1);
}

TEST_F(DtmfToneGeneratorTest, Test48000Mono) {
  TestAllTones(48000, 1);
  TestAmplitudes(48000, 1);
}

TEST_F(DtmfToneGeneratorTest, Test8000Stereo) {
  TestAllTones(8000, 2);
  TestAmplitudes(8000, 2);
}

TEST_F(DtmfToneGeneratorTest, Test16000Stereo) {
  TestAllTones(16000, 2);
  TestAmplitudes(16000, 2);
}

TEST_F(DtmfToneGeneratorTest, Test32000Stereo) {
  TestAllTones(32000, 2);
  TestAmplitudes(32000, 2);
}

TEST_F(DtmfToneGeneratorTest, Test48000Stereo) {
  TestAllTones(48000, 2);
  TestAmplitudes(48000, 2);
}

TEST(DtmfToneGenerator, TestErrors) {
  DtmfToneGenerator tone_gen;
  const int kNumSamples = 10;
  AudioMultiVector signal(1);  // One channel.

  // Try to generate tones without initializing.
  EXPECT_EQ(DtmfToneGenerator::kNotInitialized,
            tone_gen.Generate(kNumSamples, &signal));

  const int fs = 16000;       // Valid sample rate.
  const int event = 7;        // Valid event.
  const int attenuation = 0;  // Valid attenuation.
  // Initialize with invalid event -1.
  EXPECT_EQ(DtmfToneGenerator::kParameterError,
            tone_gen.Init(fs, -1, attenuation));
  // Initialize with invalid event 16.
  EXPECT_EQ(DtmfToneGenerator::kParameterError,
            tone_gen.Init(fs, 16, attenuation));
  // Initialize with invalid attenuation -1.
  EXPECT_EQ(DtmfToneGenerator::kParameterError, tone_gen.Init(fs, event, -1));
  // Initialize with invalid attenuation 64.
  EXPECT_EQ(DtmfToneGenerator::kParameterError, tone_gen.Init(fs, event, 64));
  EXPECT_FALSE(tone_gen.initialized());  // Should still be uninitialized.

  // Initialize with valid parameters.
  ASSERT_EQ(0, tone_gen.Init(fs, event, attenuation));
  EXPECT_TRUE(tone_gen.initialized());
  // NULL pointer to destination.
  EXPECT_EQ(DtmfToneGenerator::kParameterError,
            tone_gen.Generate(kNumSamples, NULL));
}

}  // namespace webrtc
