/*
 *  Copyright 2019 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */
#ifndef API_TEST_TIME_CONTROLLER_H_
#define API_TEST_TIME_CONTROLLER_H_

#include <functional>
#include <memory>
#include <string>

#include "api/task_queue/task_queue_factory.h"
#include "api/units/time_delta.h"
#include "api/units/timestamp.h"
#include "rtc_base/synchronization/yield_policy.h"
#include "rtc_base/thread.h"
#include "system_wrappers/include/clock.h"

namespace webrtc {
// Interface for controlling time progress. This allows us to execute test code
// in either real time or simulated time by using different implementation of
// this interface.
class TimeController {
 public:
  virtual ~TimeController() = default;
  // Provides a clock instance that follows implementation defined time
  // progress.
  virtual Clock* GetClock() = 0;
  // The returned factory will created task queues that runs in implementation
  // defined time domain.
  virtual TaskQueueFactory* GetTaskQueueFactory() = 0;
  // Simple helper to create an owned factory that can be used as a parameter
  // for PeerConnectionFactory. Note that this might depend on the underlying
  // time controller and therfore must be destroyed before the time controller
  // is destroyed.
  std::unique_ptr<TaskQueueFactory> CreateTaskQueueFactory();

  // Creates an rtc::Thread instance. If `socket_server` is nullptr, a default
  // noop socket server is created.
  // Returned thread is not null and started.
  virtual std::unique_ptr<rtc::Thread> CreateThread(
      const std::string& name,
      std::unique_ptr<rtc::SocketServer> socket_server = nullptr) = 0;

  // Creates an rtc::Thread instance that ensure that it's set as the current
  // thread.
  virtual rtc::Thread* GetMainThread() = 0;
  // Allow task queues and process threads created by this instance to execute
  // for the given `duration`.
  virtual void AdvanceTime(TimeDelta duration) = 0;

  // Waits until condition() == true, polling condition() in small time
  // intervals.
  // Returns true if condition() was evaluated to true before `max_duration`
  // elapsed and false otherwise.
  bool Wait(const std::function<bool()>& condition,
            TimeDelta max_duration = TimeDelta::Seconds(5));
};

// Interface for telling time, scheduling an event to fire at a particular time,
// and waiting for time to pass.
class ControlledAlarmClock {
 public:
  virtual ~ControlledAlarmClock() = default;

  // Gets a clock that tells the alarm clock's notion of time.
  virtual Clock* GetClock() = 0;

  // Schedules the alarm to fire at `deadline`.
  // An alarm clock only supports one deadline. Calls to `ScheduleAlarmAt` with
  // an earlier deadline will reset the alarm to fire earlier.Calls to
  // `ScheduleAlarmAt` with a later deadline are ignored. Returns true if the
  // deadline changed, false otherwise.
  virtual bool ScheduleAlarmAt(Timestamp deadline) = 0;

  // Sets the callback that should be run when the alarm fires.
  virtual void SetCallback(std::function<void()> callback) = 0;

  // Waits for `duration` to pass, according to the alarm clock.
  virtual void Sleep(TimeDelta duration) = 0;
};

}  // namespace webrtc
#endif  // API_TEST_TIME_CONTROLLER_H_
