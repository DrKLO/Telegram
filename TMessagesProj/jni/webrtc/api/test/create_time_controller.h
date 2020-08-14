/*
 *  Copyright 2019 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */
#ifndef API_TEST_CREATE_TIME_CONTROLLER_H_
#define API_TEST_CREATE_TIME_CONTROLLER_H_

#include <memory>

#include "api/call/call_factory_interface.h"
#include "api/test/time_controller.h"

namespace webrtc {

// Creates a time coltroller that wraps |alarm|.
std::unique_ptr<TimeController> CreateTimeController(
    ControlledAlarmClock* alarm);

// Creates a time controller that runs in simulated time.
std::unique_ptr<TimeController> CreateSimulatedTimeController();

// This is creates a call factory that creates Call instances that are backed by
// a time controller.
std::unique_ptr<CallFactoryInterface> CreateTimeControllerBasedCallFactory(
    TimeController* time_controller);

}  // namespace webrtc

#endif  // API_TEST_CREATE_TIME_CONTROLLER_H_
