/*
 *  Copyright (c) 2012 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "modules/audio_coding/neteq/dsp_helper.h"

#include "modules/audio_coding/neteq/audio_multi_vector.h"
#include "test/gtest.h"

namespace webrtc {

TEST(DspHelper, RampSignalArray) {
  static const int kLen = 100;
  int16_t input[kLen];
  int16_t output[kLen];
  // Fill input with 1000.
  for (int i = 0; i < kLen; ++i) {
    input[i] = 1000;
  }
  int start_factor = 0;
  // Ramp from 0 to 1 (in Q14) over the array. Note that `increment` is in Q20,
  // while the factor is in Q14, hence the shift by 6.
  int increment = (16384 << 6) / kLen;

  // Test first method.
  int stop_factor =
      DspHelper::RampSignal(input, kLen, start_factor, increment, output);
  EXPECT_EQ(16383, stop_factor);  // Almost reach 1 in Q14.
  for (int i = 0; i < kLen; ++i) {
    EXPECT_EQ(1000 * i / kLen, output[i]);
  }

  // Test second method. (Note that this modifies `input`.)
  stop_factor = DspHelper::RampSignal(input, kLen, start_factor, increment);
  EXPECT_EQ(16383, stop_factor);  // Almost reach 1 in Q14.
  for (int i = 0; i < kLen; ++i) {
    EXPECT_EQ(1000 * i / kLen, input[i]);
  }
}

TEST(DspHelper, RampSignalAudioMultiVector) {
  static const int kLen = 100;
  static const int kChannels = 5;
  AudioMultiVector input(kChannels, kLen * 3);
  // Fill input with 1000.
  for (int i = 0; i < kLen * 3; ++i) {
    for (int channel = 0; channel < kChannels; ++channel) {
      input[channel][i] = 1000;
    }
  }
  // We want to start ramping at `start_index` and keep ramping for `kLen`
  // samples.
  int start_index = kLen;
  int start_factor = 0;
  // Ramp from 0 to 1 (in Q14) in `kLen` samples. Note that `increment` is in
  // Q20, while the factor is in Q14, hence the shift by 6.
  int increment = (16384 << 6) / kLen;

  int stop_factor =
      DspHelper::RampSignal(&input, start_index, kLen, start_factor, increment);
  EXPECT_EQ(16383, stop_factor);  // Almost reach 1 in Q14.
  // Verify that the first `kLen` samples are left untouched.
  int i;
  for (i = 0; i < kLen; ++i) {
    for (int channel = 0; channel < kChannels; ++channel) {
      EXPECT_EQ(1000, input[channel][i]);
    }
  }
  // Verify that the next block of `kLen` samples are ramped.
  for (; i < 2 * kLen; ++i) {
    for (int channel = 0; channel < kChannels; ++channel) {
      EXPECT_EQ(1000 * (i - kLen) / kLen, input[channel][i]);
    }
  }
  // Verify the last `kLen` samples are left untouched.
  for (; i < 3 * kLen; ++i) {
    for (int channel = 0; channel < kChannels; ++channel) {
      EXPECT_EQ(1000, input[channel][i]);
    }
  }
}
}  // namespace webrtc
