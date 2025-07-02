/*
 *  Copyright (c) 2017 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "rtc_base/numerics/moving_max_counter.h"

#include "test/gtest.h"

TEST(MovingMaxCounter, ReportsMaximumInTheWindow) {
  rtc::MovingMaxCounter<int> counter(100);
  counter.Add(1, 1);
  EXPECT_EQ(counter.Max(1), 1);
  counter.Add(2, 30);
  EXPECT_EQ(counter.Max(30), 2);
  counter.Add(100, 60);
  EXPECT_EQ(counter.Max(60), 100);
  counter.Add(4, 70);
  EXPECT_EQ(counter.Max(70), 100);
  counter.Add(5, 90);
  EXPECT_EQ(counter.Max(90), 100);
}

TEST(MovingMaxCounter, IgnoresOldElements) {
  rtc::MovingMaxCounter<int> counter(100);
  counter.Add(1, 1);
  counter.Add(2, 30);
  counter.Add(100, 60);
  counter.Add(4, 70);
  counter.Add(5, 90);
  EXPECT_EQ(counter.Max(160), 100);
  // 100 is now out of the window. Next maximum is 5.
  EXPECT_EQ(counter.Max(161), 5);
}

TEST(MovingMaxCounter, HandlesEmptyWindow) {
  rtc::MovingMaxCounter<int> counter(100);
  counter.Add(123, 1);
  EXPECT_TRUE(counter.Max(101).has_value());
  EXPECT_FALSE(counter.Max(102).has_value());
}

TEST(MovingMaxCounter, HandlesSamplesWithEqualTimestamps) {
  rtc::MovingMaxCounter<int> counter(100);
  counter.Add(2, 30);
  EXPECT_EQ(counter.Max(30), 2);
  counter.Add(5, 30);
  EXPECT_EQ(counter.Max(30), 5);
  counter.Add(4, 30);
  EXPECT_EQ(counter.Max(30), 5);
  counter.Add(1, 90);
  EXPECT_EQ(counter.Max(150), 1);
}
