/*
 *  Copyright 2019 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "api/test/create_time_controller.h"

#include <memory>

#include "call/call.h"
#include "test/time_controller/external_time_controller.h"
#include "test/time_controller/simulated_time_controller.h"

namespace webrtc {

std::unique_ptr<TimeController> CreateTimeController(
    ControlledAlarmClock* alarm) {
  return std::make_unique<ExternalTimeController>(alarm);
}

std::unique_ptr<TimeController> CreateSimulatedTimeController() {
  return std::make_unique<GlobalSimulatedTimeController>(
      Timestamp::Seconds(10000));
}

std::unique_ptr<CallFactoryInterface> CreateTimeControllerBasedCallFactory(
    TimeController* time_controller) {
  class TimeControllerBasedCallFactory : public CallFactoryInterface {
   public:
    explicit TimeControllerBasedCallFactory(TimeController* time_controller)
        : time_controller_(time_controller) {}
    Call* CreateCall(const Call::Config& config) override {
      if (!module_thread_) {
        module_thread_ = SharedModuleThread::Create(
            time_controller_->CreateProcessThread("CallModules"),
            [this]() { module_thread_ = nullptr; });
      }
      return Call::Create(config, time_controller_->GetClock(), module_thread_,
                          time_controller_->CreateProcessThread("Pacer"));
    }

   private:
    TimeController* time_controller_;
    rtc::scoped_refptr<SharedModuleThread> module_thread_;
  };
  return std::make_unique<TimeControllerBasedCallFactory>(time_controller);
}

}  // namespace webrtc
