/*
 *  Copyright (c) 2021 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "modules/audio_coding/neteq/reorder_optimizer.h"

#include "test/gtest.h"

namespace webrtc {

namespace {

constexpr int kForgetFactor = 32745;  // 0.9993 in Q15.
constexpr int kMsPerLossPercent = 20;
constexpr int kStartForgetWeight = 1;

}  // namespace

TEST(ReorderOptimizerTest, OnlyIncreaseDelayForReorderedPackets) {
  ReorderOptimizer reorder_optimizer(kForgetFactor, kMsPerLossPercent,
                                     kStartForgetWeight);
  EXPECT_FALSE(reorder_optimizer.GetOptimalDelayMs());

  // Delay should not increase for in-order packets.
  reorder_optimizer.Update(60, /*reordered=*/false, 0);
  EXPECT_EQ(reorder_optimizer.GetOptimalDelayMs(), 20);

  reorder_optimizer.Update(100, /*reordered=*/false, 0);
  EXPECT_EQ(reorder_optimizer.GetOptimalDelayMs(), 20);

  reorder_optimizer.Update(80, /*reordered=*/true, 0);
  EXPECT_EQ(reorder_optimizer.GetOptimalDelayMs(), 100);
}

TEST(ReorderOptimizerTest, AvoidIncreasingDelayWhenProbabilityIsLow) {
  ReorderOptimizer reorder_optimizer(kForgetFactor, kMsPerLossPercent,
                                     kStartForgetWeight);

  reorder_optimizer.Update(40, /*reordered=*/true, 0);
  reorder_optimizer.Update(40, /*reordered=*/true, 0);
  reorder_optimizer.Update(40, /*reordered=*/true, 0);
  EXPECT_EQ(reorder_optimizer.GetOptimalDelayMs(), 60);

  // The cost of the delay is too high relative the probability.
  reorder_optimizer.Update(600, /*reordered=*/true, 0);
  EXPECT_EQ(reorder_optimizer.GetOptimalDelayMs(), 60);
}

TEST(ReorderOptimizerTest, BaseDelayIsSubtractedFromCost) {
  constexpr int kBaseDelayMs = 200;
  ReorderOptimizer reorder_optimizer(kForgetFactor, kMsPerLossPercent,
                                     kStartForgetWeight);

  reorder_optimizer.Update(40, /*reordered=*/true, kBaseDelayMs);
  reorder_optimizer.Update(40, /*reordered=*/true, kBaseDelayMs);
  reorder_optimizer.Update(40, /*reordered=*/true, kBaseDelayMs);
  EXPECT_EQ(reorder_optimizer.GetOptimalDelayMs(), 60);

  // The cost of the delay is too high relative the probability.
  reorder_optimizer.Update(600, /*reordered=*/true, kBaseDelayMs);
  EXPECT_EQ(reorder_optimizer.GetOptimalDelayMs(), 620);
}

}  // namespace webrtc
