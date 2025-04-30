/*
 *  Copyright (c) 2021 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "modules/audio_coding/neteq/underrun_optimizer.h"

#include "test/gtest.h"

namespace webrtc {

namespace {

constexpr int kDefaultHistogramQuantile = 1020054733;  // 0.95 in Q30.
constexpr int kForgetFactor = 32745;                   // 0.9993 in Q15.

}  // namespace

TEST(UnderrunOptimizerTest, ResamplePacketDelays) {
  TickTimer tick_timer;
  constexpr int kResampleIntervalMs = 500;
  UnderrunOptimizer underrun_optimizer(&tick_timer, kDefaultHistogramQuantile,
                                       kForgetFactor, absl::nullopt,
                                       kResampleIntervalMs);

  // The histogram should be updated once with the maximum delay observed for
  // the following sequence of updates.
  for (int i = 0; i < 500; i += 20) {
    underrun_optimizer.Update(i);
    EXPECT_FALSE(underrun_optimizer.GetOptimalDelayMs());
  }
  tick_timer.Increment(kResampleIntervalMs / tick_timer.ms_per_tick() + 1);
  underrun_optimizer.Update(0);
  EXPECT_EQ(underrun_optimizer.GetOptimalDelayMs(), 500);
}

}  // namespace webrtc
