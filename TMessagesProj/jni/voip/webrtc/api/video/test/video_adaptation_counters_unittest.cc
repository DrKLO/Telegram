/*
 *  Copyright (c) 2020 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "api/video/video_adaptation_counters.h"

#include "test/gtest.h"

namespace webrtc {

TEST(AdaptationCountersTest, Addition) {
  VideoAdaptationCounters a{0, 0};
  VideoAdaptationCounters b{1, 2};
  VideoAdaptationCounters total = a + b;
  EXPECT_EQ(1, total.resolution_adaptations);
  EXPECT_EQ(2, total.fps_adaptations);
}

TEST(AdaptationCountersTest, Equality) {
  VideoAdaptationCounters a{1, 2};
  VideoAdaptationCounters b{2, 1};
  EXPECT_EQ(a, a);
  EXPECT_NE(a, b);
}

}  // namespace webrtc
