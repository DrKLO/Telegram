/*
 *  Copyright (c) 2016 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "api/neteq/tick_timer.h"

#include <memory>

#include "test/gmock.h"
#include "test/gtest.h"

namespace webrtc {

// Verify that the default value for ms_per_tick is 10.
TEST(TickTimer, DefaultMsPerTick) {
  TickTimer tt;
  EXPECT_EQ(10, tt.ms_per_tick());
}

TEST(TickTimer, CustomMsPerTick) {
  TickTimer tt(17);
  EXPECT_EQ(17, tt.ms_per_tick());
}

TEST(TickTimer, Increment) {
  TickTimer tt;
  EXPECT_EQ(0u, tt.ticks());
  tt.Increment();
  EXPECT_EQ(1u, tt.ticks());

  for (int i = 0; i < 17; ++i) {
    tt.Increment();
  }
  EXPECT_EQ(18u, tt.ticks());

  tt.Increment(17);
  EXPECT_EQ(35u, tt.ticks());
}

TEST(TickTimer, WrapAround) {
  TickTimer tt;
  tt.Increment(UINT64_MAX);
  EXPECT_EQ(UINT64_MAX, tt.ticks());
  tt.Increment();
  EXPECT_EQ(0u, tt.ticks());
}

TEST(TickTimer, Stopwatch) {
  TickTimer tt;
  // Increment it a "random" number of steps.
  tt.Increment(17);

  std::unique_ptr<TickTimer::Stopwatch> sw = tt.GetNewStopwatch();
  ASSERT_TRUE(sw);

  EXPECT_EQ(0u, sw->ElapsedTicks());  // Starts at zero.
  EXPECT_EQ(0u, sw->ElapsedMs());
  tt.Increment();
  EXPECT_EQ(1u, sw->ElapsedTicks());  // Increases with the TickTimer.
  EXPECT_EQ(10u, sw->ElapsedMs());
}

TEST(TickTimer, StopwatchWrapAround) {
  TickTimer tt;
  tt.Increment(UINT64_MAX);

  std::unique_ptr<TickTimer::Stopwatch> sw = tt.GetNewStopwatch();
  ASSERT_TRUE(sw);

  tt.Increment();
  EXPECT_EQ(0u, tt.ticks());
  EXPECT_EQ(1u, sw->ElapsedTicks());
  EXPECT_EQ(10u, sw->ElapsedMs());

  tt.Increment();
  EXPECT_EQ(1u, tt.ticks());
  EXPECT_EQ(2u, sw->ElapsedTicks());
  EXPECT_EQ(20u, sw->ElapsedMs());
}

TEST(TickTimer, StopwatchMsOverflow) {
  TickTimer tt;
  std::unique_ptr<TickTimer::Stopwatch> sw = tt.GetNewStopwatch();
  ASSERT_TRUE(sw);

  tt.Increment(UINT64_MAX / 10);
  EXPECT_EQ(UINT64_MAX, sw->ElapsedMs());

  tt.Increment();
  EXPECT_EQ(UINT64_MAX, sw->ElapsedMs());

  tt.Increment(UINT64_MAX - tt.ticks());
  EXPECT_EQ(UINT64_MAX, tt.ticks());
  EXPECT_EQ(UINT64_MAX, sw->ElapsedMs());
}

TEST(TickTimer, StopwatchWithCustomTicktime) {
  const int kMsPerTick = 17;
  TickTimer tt(kMsPerTick);
  std::unique_ptr<TickTimer::Stopwatch> sw = tt.GetNewStopwatch();
  ASSERT_TRUE(sw);

  EXPECT_EQ(0u, sw->ElapsedMs());
  tt.Increment();
  EXPECT_EQ(static_cast<uint64_t>(kMsPerTick), sw->ElapsedMs());
}

TEST(TickTimer, Countdown) {
  TickTimer tt;
  // Increment it a "random" number of steps.
  tt.Increment(4711);

  std::unique_ptr<TickTimer::Countdown> cd = tt.GetNewCountdown(17);
  ASSERT_TRUE(cd);

  EXPECT_FALSE(cd->Finished());
  tt.Increment();
  EXPECT_FALSE(cd->Finished());

  tt.Increment(16);  // Total increment is now 17.
  EXPECT_TRUE(cd->Finished());

  // Further increments do not change the state.
  tt.Increment();
  EXPECT_TRUE(cd->Finished());
  tt.Increment(1234);
  EXPECT_TRUE(cd->Finished());
}
}  // namespace webrtc
