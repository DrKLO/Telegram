/*
 *  Copyright (c) 2020 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */
#include "api/test/time_controller.h"

namespace webrtc {
std::unique_ptr<TaskQueueFactory> TimeController::CreateTaskQueueFactory() {
  class FactoryWrapper final : public TaskQueueFactory {
   public:
    explicit FactoryWrapper(TaskQueueFactory* inner_factory)
        : inner_(inner_factory) {}
    std::unique_ptr<TaskQueueBase, TaskQueueDeleter> CreateTaskQueue(
        absl::string_view name,
        Priority priority) const override {
      return inner_->CreateTaskQueue(name, priority);
    }

   private:
    TaskQueueFactory* const inner_;
  };
  return std::make_unique<FactoryWrapper>(GetTaskQueueFactory());
}
bool TimeController::Wait(const std::function<bool()>& condition,
                          TimeDelta max_duration) {
  // Step size is chosen to be short enough to not significantly affect latency
  // in real time tests while being long enough to avoid adding too much load to
  // the system.
  const auto kStep = TimeDelta::Millis(5);
  for (auto elapsed = TimeDelta::Zero(); elapsed < max_duration;
       elapsed += kStep) {
    if (condition())
      return true;
    AdvanceTime(kStep);
  }
  return condition();
}
}  // namespace webrtc
