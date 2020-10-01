// Copyright (c) 2012 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

// OneShotTimer, RepeatingTimer and RetainingOneShotTimer provide a simple timer
// API.  As the names suggest, OneShotTimer calls you back once after a time
// delay expires.
// RepeatingTimer on the other hand calls you back periodically with the
// prescribed time interval.
// RetainingOneShotTimer doesn't repeat the task itself like RepeatingTimer, but
// retains the given task after the time out. You can restart it with Reset
// again without giving new task to Start.
//
// All of OneShotTimer, RepeatingTimer and RetainingOneShotTimer cancel the
// timer when they go out of scope, which makes it easy to ensure that you do
// not get called when your object has gone out of scope.  Just instantiate a
// timer as a member variable of the class for which you wish to receive timer
// events.
//
// Sample RepeatingTimer usage:
//
//   class MyClass {
//    public:
//     void StartDoingStuff() {
//       timer_.Start(FROM_HERE, TimeDelta::FromSeconds(1),
//                    this, &MyClass::DoStuff);
//     }
//     void StopDoingStuff() {
//       timer_.Stop();
//     }
//    private:
//     void DoStuff() {
//       // This method is called every second to do stuff.
//       ...
//     }
//     base::RepeatingTimer timer_;
//   };
//
// Timers also support a Reset method, which allows you to easily defer the
// timer event until the timer delay passes once again.  So, in the above
// example, if 0.5 seconds have already passed, calling Reset on |timer_|
// would postpone DoStuff by another 1 second.  In other words, Reset is
// shorthand for calling Stop and then Start again with the same arguments.
//
// These APIs are not thread safe. When a method is called (except the
// constructor), all further method calls must be on the same sequence until
// Stop().
//
// By default, the scheduled tasks will be run on the same sequence that the
// Timer was *started on*. To mock time in unit tests, some old tests used
// SetTaskRunner() to schedule the delay on a test-controlled TaskRunner. The
// modern and preferred approach to mock time is to use TaskEnvironment's
// MOCK_TIME mode.

#ifndef BASE_TIMER_TIMER_H_
#define BASE_TIMER_TIMER_H_

// IMPORTANT: If you change timer code, make sure that all tests (including
// disabled ones) from timer_unittests.cc pass locally. Some are disabled
// because they're flaky on the buildbot, but when you run them locally you
// should be able to tell the difference.

#include <memory>

#include "base/base_export.h"
#include "base/bind.h"
#include "base/bind_helpers.h"
#include "base/callback.h"
#include "base/location.h"
#include "base/macros.h"
#include "base/sequence_checker_impl.h"
#include "base/sequenced_task_runner.h"
#include "base/time/time.h"

namespace base {

class TickClock;

namespace internal {

class BaseTimerTaskInternal;

//-----------------------------------------------------------------------------
// This class wraps TaskRunner::PostDelayedTask to manage delayed and repeating
// tasks. See meta comment above for thread-safety requirements.
// Do not use this class directly. Use one of OneShotTimer, RepeatingTimer or
// RetainingOneShotTimer.
//
class BASE_EXPORT TimerBase {
 public:
  // Constructs a timer. Start must be called later to set task info.
  // If |tick_clock| is provided, it is used instead of TimeTicks::Now() to get
  // TimeTicks when scheduling tasks.
  TimerBase();
  explicit TimerBase(const TickClock* tick_clock);

  // Construct a timer with task info.
  // If |tick_clock| is provided, it is used instead of TimeTicks::Now() to get
  // TimeTicks when scheduling tasks.
  TimerBase(const Location& posted_from, TimeDelta delay);
  TimerBase(const Location& posted_from,
            TimeDelta delay,
            const TickClock* tick_clock);

  virtual ~TimerBase();

  // Returns true if the timer is running (i.e., not stopped).
  bool IsRunning() const;

  // Returns the current delay for this timer.
  TimeDelta GetCurrentDelay() const;

  // Sets the task runner on which the delayed task should be scheduled when
  // this Timer is running. This method can only be called while this Timer
  // isn't running. This is an alternative (old) approach to mock time in tests.
  // The modern and preferred approach is to use
  // TaskEnvironment::TimeSource::MOCK_TIME. To avoid racy usage of Timer,
  // |task_runner| must run tasks on the same sequence which this Timer is bound
  // to (started from). TODO(gab): Migrate all callers to
  // TaskEnvironment::TimeSource::MOCK_TIME.
  virtual void SetTaskRunner(scoped_refptr<SequencedTaskRunner> task_runner);

  // Call this method to stop and cancel the timer.  It is a no-op if the timer
  // is not running.
  virtual void Stop();

  // Stop running task (if any) and abandon scheduled task (if any).
  void AbandonAndStop() {
    AbandonScheduledTask();

    Stop();
    // No more member accesses here: |this| could be deleted at this point.
  }

  // Call this method to reset the timer delay. The user task must be set. If
  // the timer is not running, this will start it by posting a task.
  virtual void Reset();

  const TimeTicks& desired_run_time() const { return desired_run_time_; }

 protected:
  virtual void OnStop() = 0;
  virtual void RunUserTask() = 0;

  // Returns the current tick count.
  TimeTicks Now() const;

  void set_desired_run_time(TimeTicks desired) { desired_run_time_ = desired; }
  void set_is_running(bool running) { is_running_ = running; }

  const Location& posted_from() const { return posted_from_; }

  // The task runner on which the task should be scheduled. If it is null, the
  // task runner for the current sequence will be used.
  scoped_refptr<SequencedTaskRunner> task_runner_;

  // Timer isn't thread-safe and must only be used on its origin sequence
  // (sequence on which it was started). Once fully Stop()'ed it may be
  // destroyed or restarted on another sequence.
  SequenceChecker origin_sequence_checker_;

  // Allocates a new |scheduled_task_| and posts it on the current sequence with
  // the given |delay|. |scheduled_task_| must be null. |scheduled_run_time_|
  // and |desired_run_time_| are reset to Now() + delay.
  void PostNewScheduledTask(TimeDelta delay);

  void StartInternal(const Location& posted_from, TimeDelta delay);

 private:
  friend class BaseTimerTaskInternal;

  // Returns the task runner on which the task should be scheduled. If the
  // corresponding |task_runner_| field is null, the task runner for the current
  // sequence is returned.
  scoped_refptr<SequencedTaskRunner> GetTaskRunner();

  // Disable |scheduled_task_| and abandon it so that it no longer refers back
  // to this object.
  void AbandonScheduledTask();

  // Called by BaseTimerTaskInternal when the delayed task fires.
  void RunScheduledTask();

  // When non-null, the |scheduled_task_| was posted to call RunScheduledTask()
  // at |scheduled_run_time_|.
  BaseTimerTaskInternal* scheduled_task_;

  // Location in user code.
  Location posted_from_;
  // Delay requested by user.
  TimeDelta delay_;

  // The time at which |scheduled_task_| is expected to fire. This time can be a
  // "zero" TimeTicks if the task must be run immediately.
  TimeTicks scheduled_run_time_;

  // The desired run time of |user_task_|. The user may update this at any time,
  // even if their previous request has not run yet. If |desired_run_time_| is
  // greater than |scheduled_run_time_|, a continuation task will be posted to
  // wait for the remaining time. This allows us to reuse the pending task so as
  // not to flood the delayed queues with orphaned tasks when the user code
  // excessively Stops and Starts the timer. This time can be a "zero" TimeTicks
  // if the task must be run immediately.
  TimeTicks desired_run_time_;

  // The tick clock used to calculate the run time for scheduled tasks.
  const TickClock* const tick_clock_;

  // If true, |user_task_| is scheduled to run sometime in the future.
  bool is_running_;

  DISALLOW_COPY_AND_ASSIGN(TimerBase);
};

}  // namespace internal

//-----------------------------------------------------------------------------
// A simple, one-shot timer.  See usage notes at the top of the file.
class BASE_EXPORT OneShotTimer : public internal::TimerBase {
 public:
  OneShotTimer();
  explicit OneShotTimer(const TickClock* tick_clock);
  ~OneShotTimer() override;

  // Start the timer to run at the given |delay| from now. If the timer is
  // already running, it will be replaced to call the given |user_task|.
  virtual void Start(const Location& posted_from,
                     TimeDelta delay,
                     OnceClosure user_task);

  // Start the timer to run at the given |delay| from now. If the timer is
  // already running, it will be replaced to call a task formed from
  // |receiver->*method|.
  template <class Receiver>
  void Start(const Location& posted_from,
             TimeDelta delay,
             Receiver* receiver,
             void (Receiver::*method)()) {
    Start(posted_from, delay, BindOnce(method, Unretained(receiver)));
  }

  // Run the scheduled task immediately, and stop the timer. The timer needs to
  // be running.
  void FireNow();

 private:
  void OnStop() final;
  void RunUserTask() final;

  OnceClosure user_task_;

  DISALLOW_COPY_AND_ASSIGN(OneShotTimer);
};

//-----------------------------------------------------------------------------
// A simple, repeating timer.  See usage notes at the top of the file.
class BASE_EXPORT RepeatingTimer : public internal::TimerBase {
 public:
  RepeatingTimer();
  explicit RepeatingTimer(const TickClock* tick_clock);
  ~RepeatingTimer() override;

  RepeatingTimer(const Location& posted_from,
                 TimeDelta delay,
                 RepeatingClosure user_task);
  RepeatingTimer(const Location& posted_from,
                 TimeDelta delay,
                 RepeatingClosure user_task,
                 const TickClock* tick_clock);

  // Start the timer to run at the given |delay| from now. If the timer is
  // already running, it will be replaced to call the given |user_task|.
  virtual void Start(const Location& posted_from,
                     TimeDelta delay,
                     RepeatingClosure user_task);

  // Start the timer to run at the given |delay| from now. If the timer is
  // already running, it will be replaced to call a task formed from
  // |receiver->*method|.
  template <class Receiver>
  void Start(const Location& posted_from,
             TimeDelta delay,
             Receiver* receiver,
             void (Receiver::*method)()) {
    Start(posted_from, delay, BindRepeating(method, Unretained(receiver)));
  }

  const RepeatingClosure& user_task() const { return user_task_; }

 private:
  // Mark this final, so that the destructor can call this safely.
  void OnStop() final;

  void RunUserTask() override;

  RepeatingClosure user_task_;

  DISALLOW_COPY_AND_ASSIGN(RepeatingTimer);
};

//-----------------------------------------------------------------------------
// A simple, one-shot timer with the retained user_task which is reused for
// multiple invocations of Start(). See usage notes at the top of the file.
class BASE_EXPORT RetainingOneShotTimer : public internal::TimerBase {
 public:
  RetainingOneShotTimer();
  explicit RetainingOneShotTimer(const TickClock* tick_clock);
  ~RetainingOneShotTimer() override;

  RetainingOneShotTimer(const Location& posted_from,
                        TimeDelta delay,
                        RepeatingClosure user_task);
  RetainingOneShotTimer(const Location& posted_from,
                        TimeDelta delay,
                        RepeatingClosure user_task,
                        const TickClock* tick_clock);

  // Start the timer to run at the given |delay| from now. If the timer is
  // already running, it will be replaced to call the given |user_task|.
  virtual void Start(const Location& posted_from,
                     TimeDelta delay,
                     RepeatingClosure user_task);

  // Start the timer to run at the given |delay| from now. If the timer is
  // already running, it will be replaced to call a task formed from
  // |receiver->*method|.
  template <class Receiver>
  void Start(const Location& posted_from,
             TimeDelta delay,
             Receiver* receiver,
             void (Receiver::*method)()) {
    Start(posted_from, delay, BindRepeating(method, Unretained(receiver)));
  }

  const RepeatingClosure& user_task() const { return user_task_; }

 protected:
  void set_user_task(const RepeatingClosure& task) { user_task_ = task; }

 private:
  // Mark this final, so that the destructor can call this safely.
  void OnStop() final;

  void RunUserTask() override;

  RepeatingClosure user_task_;

  DISALLOW_COPY_AND_ASSIGN(RetainingOneShotTimer);
};

//-----------------------------------------------------------------------------
// A Delay timer is like The Button from Lost. Once started, you have to keep
// calling Reset otherwise it will call the given method on the sequence it was
// initially Reset() from.
//
// Once created, it is inactive until Reset is called. Once |delay| seconds have
// passed since the last call to Reset, the callback is made. Once the callback
// has been made, it's inactive until Reset is called again.
//
// If destroyed, the timeout is canceled and will not occur even if already
// inflight.
class DelayTimer {
 public:
  template <class Receiver>
  DelayTimer(const Location& posted_from,
             TimeDelta delay,
             Receiver* receiver,
             void (Receiver::*method)())
      : DelayTimer(posted_from, delay, receiver, method, nullptr) {}

  template <class Receiver>
  DelayTimer(const Location& posted_from,
             TimeDelta delay,
             Receiver* receiver,
             void (Receiver::*method)(),
             const TickClock* tick_clock)
      : timer_(posted_from,
               delay,
               BindRepeating(method, Unretained(receiver)),
               tick_clock) {}

  void Reset() { timer_.Reset(); }

 private:
  RetainingOneShotTimer timer_;

  DISALLOW_COPY_AND_ASSIGN(DelayTimer);
};

}  // namespace base

#endif  // BASE_TIMER_TIMER_H_
