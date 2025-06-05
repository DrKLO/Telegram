/*
 *  Copyright (c) 2014 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

// Unit tests for test InputAudioFile class.

#include "modules/audio_coding/neteq/tools/input_audio_file.h"

#include "rtc_base/numerics/safe_conversions.h"
#include "test/gtest.h"

namespace webrtc {
namespace test {

TEST(TestInputAudioFile, DuplicateInterleaveSeparateSrcDst) {
  static const size_t kSamples = 10;
  static const size_t kChannels = 2;
  int16_t input[kSamples];
  for (size_t i = 0; i < kSamples; ++i) {
    input[i] = rtc::checked_cast<int16_t>(i);
  }
  int16_t output[kSamples * kChannels];
  InputAudioFile::DuplicateInterleaved(input, kSamples, kChannels, output);

  // Verify output
  int16_t* output_ptr = output;
  for (size_t i = 0; i < kSamples; ++i) {
    for (size_t j = 0; j < kChannels; ++j) {
      EXPECT_EQ(static_cast<int16_t>(i), *output_ptr++);
    }
  }
}

TEST(TestInputAudioFile, DuplicateInterleaveSameSrcDst) {
  static const size_t kSamples = 10;
  static const size_t kChannels = 5;
  int16_t input[kSamples * kChannels];
  for (size_t i = 0; i < kSamples; ++i) {
    input[i] = rtc::checked_cast<int16_t>(i);
  }
  InputAudioFile::DuplicateInterleaved(input, kSamples, kChannels, input);

  // Verify output
  int16_t* output_ptr = input;
  for (size_t i = 0; i < kSamples; ++i) {
    for (size_t j = 0; j < kChannels; ++j) {
      EXPECT_EQ(static_cast<int16_t>(i), *output_ptr++);
    }
  }
}

}  // namespace test
}  // namespace webrtc
