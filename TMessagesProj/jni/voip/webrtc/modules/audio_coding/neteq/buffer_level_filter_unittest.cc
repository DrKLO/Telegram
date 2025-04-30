/*
 *  Copyright (c) 2012 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

// Unit tests for BufferLevelFilter class.

#include "modules/audio_coding/neteq/buffer_level_filter.h"

#include <math.h>  // Access to pow function.

#include "rtc_base/strings/string_builder.h"
#include "test/gtest.h"

namespace webrtc {

TEST(BufferLevelFilter, CreateAndDestroy) {
  BufferLevelFilter* filter = new BufferLevelFilter();
  EXPECT_EQ(0, filter->filtered_current_level());
  delete filter;
}

TEST(BufferLevelFilter, ConvergenceTest) {
  BufferLevelFilter filter;
  for (int times = 10; times <= 50; times += 10) {
    for (int value = 100; value <= 200; value += 10) {
      filter.Reset();
      filter.SetTargetBufferLevel(20);  // Makes filter coefficient 251/256.
      rtc::StringBuilder ss;
      ss << "times = " << times << ", value = " << value;
      SCOPED_TRACE(ss.str());  // Print out the parameter values on failure.
      for (int i = 0; i < times; ++i) {
        filter.Update(value, 0 /* time_stretched_samples */);
      }
      // Expect the filtered value to be (theoretically)
      // (1 - (251/256) ^ `times`) * `value`.
      double expected_value_double = (1 - pow(251.0 / 256.0, times)) * value;
      int expected_value = static_cast<int>(expected_value_double);

      // The actual value may differ slightly from the expected value due to
      // intermediate-stage rounding errors in the filter implementation.
      // This is why we have to use EXPECT_NEAR with a tolerance of +/-1.
      EXPECT_NEAR(expected_value, filter.filtered_current_level(), 1);
    }
  }
}

// Verify that target buffer level impacts on the filter convergence.
TEST(BufferLevelFilter, FilterFactor) {
  BufferLevelFilter filter;
  // Update 10 times with value 100.
  const int kTimes = 10;
  const int kValue = 100;

  filter.SetTargetBufferLevel(60);  // Makes filter coefficient 252/256.
  for (int i = 0; i < kTimes; ++i) {
    filter.Update(kValue, 0 /* time_stretched_samples */);
  }
  // Expect the filtered value to be
  // (1 - (252/256) ^ `kTimes`) * `kValue`.
  int expected_value = 15;
  EXPECT_EQ(expected_value, filter.filtered_current_level());

  filter.Reset();
  filter.SetTargetBufferLevel(140);  // Makes filter coefficient 253/256.
  for (int i = 0; i < kTimes; ++i) {
    filter.Update(kValue, 0 /* time_stretched_samples */);
  }
  // Expect the filtered value to be
  // (1 - (253/256) ^ `kTimes`) * `kValue`.
  expected_value = 11;
  EXPECT_EQ(expected_value, filter.filtered_current_level());

  filter.Reset();
  filter.SetTargetBufferLevel(160);  // Makes filter coefficient 254/256.
  for (int i = 0; i < kTimes; ++i) {
    filter.Update(kValue, 0 /* time_stretched_samples */);
  }
  // Expect the filtered value to be
  // (1 - (254/256) ^ `kTimes`) * `kValue`.
  expected_value = 8;
  EXPECT_EQ(expected_value, filter.filtered_current_level());
}

TEST(BufferLevelFilter, TimeStretchedSamples) {
  BufferLevelFilter filter;
  filter.SetTargetBufferLevel(20);  // Makes filter coefficient 251/256.
  // Update 10 times with value 100.
  const int kTimes = 10;
  const int kValue = 100;
  const int kTimeStretchedSamples = 3;
  for (int i = 0; i < kTimes; ++i) {
    filter.Update(kValue, 0);
  }
  // Expect the filtered value to be
  // (1 - (251/256) ^ `kTimes`) * `kValue`.
  const int kExpectedValue = 18;
  EXPECT_EQ(kExpectedValue, filter.filtered_current_level());

  // Update filter again, now with non-zero value for packet length.
  // Set the current filtered value to be the input, in order to isolate the
  // impact of `kTimeStretchedSamples`.
  filter.Update(filter.filtered_current_level(), kTimeStretchedSamples);
  EXPECT_EQ(kExpectedValue - kTimeStretchedSamples,
            filter.filtered_current_level());
  // Try negative value and verify that we come back to the previous result.
  filter.Update(filter.filtered_current_level(), -kTimeStretchedSamples);
  EXPECT_EQ(kExpectedValue, filter.filtered_current_level());
}

}  // namespace webrtc
