/*
 *  Copyright (c) 2021 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */
#include "api/test/network_emulation/create_cross_traffic.h"

#include <memory>

#include "rtc_base/task_utils/repeating_task.h"
#include "test/network/cross_traffic.h"

namespace webrtc {

std::unique_ptr<CrossTrafficGenerator> CreateRandomWalkCrossTraffic(
    CrossTrafficRoute* traffic_route,
    RandomWalkConfig config) {
  return std::make_unique<test::RandomWalkCrossTraffic>(config, traffic_route);
}

std::unique_ptr<CrossTrafficGenerator> CreatePulsedPeaksCrossTraffic(
    CrossTrafficRoute* traffic_route,
    PulsedPeaksConfig config) {
  return std::make_unique<test::PulsedPeaksCrossTraffic>(config, traffic_route);
}

std::unique_ptr<CrossTrafficGenerator> CreateFakeTcpCrossTraffic(
    EmulatedRoute* send_route,
    EmulatedRoute* ret_route,
    FakeTcpConfig config) {
  return std::make_unique<test::FakeTcpCrossTraffic>(config, send_route,
                                                     ret_route);
}

}  // namespace webrtc
