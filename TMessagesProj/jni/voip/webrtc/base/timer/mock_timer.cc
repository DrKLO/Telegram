// Copyright 2014 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

#include "base/timer/mock_timer.h"

#include "base/test/test_simple_task_runner.h"

namespace base {

namespace {

void FlushPendingTasks(TestSimpleTaskRunner* task_runner) {
  // Do not use TestSimpleTaskRunner::RunPendingTasks() here. As RunPendingTasks
  // overrides ThreadTaskRunnerHandle when it runs tasks, tasks posted by timer
  // tasks to TTRH go to |test_task_runner_|, though they should be posted to
  // the original task runner.
  // Do not use TestSimpleTaskRunner::RunPendingTasks(), as its overridden
  // ThreadTaskRunnerHandle causes unexpected side effects.
  for (TestPendingTask& task : task_runner->TakePendingTasks())
    std::move(task.task).Run();
}

}  // namespace

MockOneShotTimer::MockOneShotTimer()
    : OneShotTimer(&clock_),
      test_task_runner_(MakeRefCounted<TestSimpleTaskRunner>()) {
  OneShotTimer::SetTaskRunner(test_task_runner_);
}

MockOneShotTimer::~MockOneShotTimer() = default;

void MockOneShotTimer::SetTaskRunner(
    scoped_refptr<SequencedTaskRunner> task_runner) {
  NOTREACHED() << "MockOneShotTimer doesn't support SetTaskRunner().";
}

void MockOneShotTimer::Fire() {
  DCHECK(IsRunning());
  clock_.Advance(std::max(TimeDelta(), desired_run_time() - clock_.NowTicks()));
  FlushPendingTasks(test_task_runner_.get());
}

MockRepeatingTimer::MockRepeatingTimer()
    : RepeatingTimer(&clock_),
      test_task_runner_(MakeRefCounted<TestSimpleTaskRunner>()) {
  RepeatingTimer::SetTaskRunner(test_task_runner_);
}

MockRepeatingTimer::~MockRepeatingTimer() = default;

void MockRepeatingTimer::SetTaskRunner(
    scoped_refptr<SequencedTaskRunner> task_runner) {
  NOTREACHED() << "MockRepeatingTimer doesn't support SetTaskRunner().";
}

void MockRepeatingTimer::Fire() {
  DCHECK(IsRunning());
  clock_.Advance(std::max(TimeDelta(), desired_run_time() - clock_.NowTicks()));
  FlushPendingTasks(test_task_runner_.get());
}

MockRetainingOneShotTimer::MockRetainingOneShotTimer()
    : RetainingOneShotTimer(&clock_),
      test_task_runner_(MakeRefCounted<TestSimpleTaskRunner>()) {
  RetainingOneShotTimer::SetTaskRunner(test_task_runner_);
}

MockRetainingOneShotTimer::~MockRetainingOneShotTimer() = default;

void MockRetainingOneShotTimer::SetTaskRunner(
    scoped_refptr<SequencedTaskRunner> task_runner) {
  NOTREACHED() << "MockRetainingOneShotTimer doesn't support SetTaskRunner().";
}

void MockRetainingOneShotTimer::Fire() {
  DCHECK(IsRunning());
  clock_.Advance(std::max(TimeDelta(), desired_run_time() - clock_.NowTicks()));
  FlushPendingTasks(test_task_runner_.get());
}

}  // namespace base
