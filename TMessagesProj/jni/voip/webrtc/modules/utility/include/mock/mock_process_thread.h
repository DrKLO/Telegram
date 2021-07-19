/*
 *  Copyright (c) 2014 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#ifndef MODULES_UTILITY_INCLUDE_MOCK_MOCK_PROCESS_THREAD_H_
#define MODULES_UTILITY_INCLUDE_MOCK_MOCK_PROCESS_THREAD_H_

#include <memory>

#include "modules/utility/include/process_thread.h"
#include "rtc_base/location.h"
#include "test/gmock.h"

namespace webrtc {

class MockProcessThread : public ProcessThread {
 public:
  MOCK_METHOD(void, Start, (), (override));
  MOCK_METHOD(void, Stop, (), (override));
  MOCK_METHOD(void, Delete, (), (override));
  MOCK_METHOD(void, WakeUp, (Module*), (override));
  MOCK_METHOD(void, PostTask, (std::unique_ptr<QueuedTask>), (override));
  MOCK_METHOD(void,
              PostDelayedTask,
              (std::unique_ptr<QueuedTask>, uint32_t),
              (override));
  MOCK_METHOD(void,
              RegisterModule,
              (Module*, const rtc::Location&),
              (override));
  MOCK_METHOD(void, DeRegisterModule, (Module*), (override));
};

}  // namespace webrtc
#endif  // MODULES_UTILITY_INCLUDE_MOCK_MOCK_PROCESS_THREAD_H_
