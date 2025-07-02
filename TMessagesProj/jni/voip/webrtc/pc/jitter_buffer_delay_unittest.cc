/*
 *  Copyright 2019 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "pc/jitter_buffer_delay.h"

#include "test/gtest.h"

namespace webrtc {

class JitterBufferDelayTest : public ::testing::Test {
 public:
  JitterBufferDelayTest() {}

 protected:
  JitterBufferDelay delay_;
};

TEST_F(JitterBufferDelayTest, Set) {
  // Delay in seconds.
  delay_.Set(3.0);
  EXPECT_EQ(delay_.GetMs(), 3000);
}

TEST_F(JitterBufferDelayTest, DefaultValue) {
  EXPECT_EQ(delay_.GetMs(), 0);  // Default value is 0ms.
}

TEST_F(JitterBufferDelayTest, Clamping) {
  // In current Jitter Buffer implementation (Audio or Video) maximum supported
  // value is 10000 milliseconds.
  delay_.Set(10.5);
  EXPECT_EQ(delay_.GetMs(), 10000);

  // Test int overflow.
  delay_.Set(21474836470.0);
  EXPECT_EQ(delay_.GetMs(), 10000);

  delay_.Set(-21474836470.0);
  EXPECT_EQ(delay_.GetMs(), 0);

  // Boundary value in seconds to milliseconds conversion.
  delay_.Set(0.0009);
  EXPECT_EQ(delay_.GetMs(), 0);

  delay_.Set(-2.0);
  EXPECT_EQ(delay_.GetMs(), 0);
}

}  // namespace webrtc
