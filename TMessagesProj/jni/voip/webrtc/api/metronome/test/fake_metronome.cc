/*
 *  Copyright (c) 2022 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "api/metronome/test/fake_metronome.h"

#include <utility>
#include <vector>

#include "absl/functional/any_invocable.h"
#include "api/task_queue/task_queue_base.h"
#include "api/units/time_delta.h"

namespace webrtc::test {

ForcedTickMetronome::ForcedTickMetronome(TimeDelta tick_period)
    : tick_period_(tick_period) {}

void ForcedTickMetronome::RequestCallOnNextTick(
    absl::AnyInvocable<void() &&> callback) {
  callbacks_.push_back(std::move(callback));
}

TimeDelta ForcedTickMetronome::TickPeriod() const {
  return tick_period_;
}

size_t ForcedTickMetronome::NumListeners() {
  return callbacks_.size();
}

void ForcedTickMetronome::Tick() {
  std::vector<absl::AnyInvocable<void() &&>> callbacks;
  callbacks_.swap(callbacks);
  for (auto& callback : callbacks)
    std::move(callback)();
}

FakeMetronome::FakeMetronome(TimeDelta tick_period)
    : tick_period_(tick_period) {}

void FakeMetronome::SetTickPeriod(TimeDelta tick_period) {
  tick_period_ = tick_period;
}

void FakeMetronome::RequestCallOnNextTick(
    absl::AnyInvocable<void() &&> callback) {
  TaskQueueBase* current = TaskQueueBase::Current();
  callbacks_.push_back(std::move(callback));
  if (callbacks_.size() == 1) {
    current->PostDelayedTask(
        [this] {
          std::vector<absl::AnyInvocable<void() &&>> callbacks;
          callbacks_.swap(callbacks);
          for (auto& callback : callbacks)
            std::move(callback)();
        },
        tick_period_);
  }
}

TimeDelta FakeMetronome::TickPeriod() const {
  return tick_period_;
}

}  // namespace webrtc::test
