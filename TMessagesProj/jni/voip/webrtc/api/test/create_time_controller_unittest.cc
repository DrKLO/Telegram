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

#include "api/test/time_controller.h"
#include "api/units/time_delta.h"
#include "api/units/timestamp.h"
#include "test/gmock.h"
#include "test/gtest.h"

namespace webrtc {
namespace {

class FakeAlarm : public ControlledAlarmClock {
 public:
  explicit FakeAlarm(Timestamp start_time);

  Clock* GetClock() override;
  bool ScheduleAlarmAt(Timestamp deadline) override;
  void SetCallback(std::function<void()> callback) override;
  void Sleep(TimeDelta duration) override;

 private:
  SimulatedClock clock_;
  Timestamp deadline_;
  std::function<void()> callback_;
};

FakeAlarm::FakeAlarm(Timestamp start_time)
    : clock_(start_time),
      deadline_(Timestamp::PlusInfinity()),
      callback_([] {}) {}

Clock* FakeAlarm::GetClock() {
  return &clock_;
}

bool FakeAlarm::ScheduleAlarmAt(Timestamp deadline) {
  if (deadline < deadline_) {
    deadline_ = deadline;
    return true;
  }
  return false;
}

void FakeAlarm::SetCallback(std::function<void()> callback) {
  callback_ = callback;
}

void FakeAlarm::Sleep(TimeDelta duration) {
  Timestamp end_time = clock_.CurrentTime() + duration;

  while (deadline_ <= end_time) {
    clock_.AdvanceTime(deadline_ - clock_.CurrentTime());
    deadline_ = Timestamp::PlusInfinity();
    callback_();
  }

  clock_.AdvanceTime(end_time - clock_.CurrentTime());
}

TEST(CreateTimeControllerTest, CreatesNonNullController) {
  FakeAlarm alarm(Timestamp::Millis(100));
  EXPECT_NE(CreateTimeController(&alarm), nullptr);
}

}  // namespace
}  // namespace webrtc
