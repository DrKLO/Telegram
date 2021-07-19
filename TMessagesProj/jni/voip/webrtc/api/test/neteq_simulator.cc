/*
 *  Copyright (c) 2018 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "api/test/neteq_simulator.h"

namespace webrtc {
namespace test {

NetEqSimulator::SimulationStepResult::SimulationStepResult() = default;
NetEqSimulator::SimulationStepResult::SimulationStepResult(
    const NetEqSimulator::SimulationStepResult& other) = default;
NetEqSimulator::SimulationStepResult::~SimulationStepResult() = default;

NetEqSimulator::NetEqState::NetEqState() = default;
NetEqSimulator::NetEqState::NetEqState(const NetEqState& other) = default;
NetEqSimulator::NetEqState::~NetEqState() = default;

}  // namespace test
}  // namespace webrtc
