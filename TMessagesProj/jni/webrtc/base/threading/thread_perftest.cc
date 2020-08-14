// Copyright 2014 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

#include <stddef.h>

#include <memory>
#include <vector>

#include "base/base_switches.h"
#include "base/bind.h"
#include "base/command_line.h"
#include "base/location.h"
#include "base/memory/ptr_util.h"
#include "base/message_loop/message_loop_current.h"
#include "base/single_thread_task_runner.h"
#include "base/strings/stringprintf.h"
#include "base/synchronization/condition_variable.h"
#include "base/synchronization/lock.h"
#include "base/synchronization/waitable_event.h"
#include "base/task/task_observer.h"
#include "base/threading/thread.h"
#include "base/time/time.h"
#include "build/build_config.h"
#include "testing/gtest/include/gtest/gtest.h"
#include "testing/perf/perf_result_reporter.h"

#if defined(OS_POSIX)
#include <pthread.h>
#endif

namespace base {

namespace {

const int kNumRuns = 100000;

constexpr char kMetricPrefixThread[] = "Thread.";
constexpr char kMetricClockTimePerHop[] = "wall_time_per_hop";
constexpr char kMetricCpuTimePerHop[] = "cpu_time_per_hop";
constexpr char kStoryBaseTask[] = "task";
constexpr char kStoryBaseTaskWithObserver[] = "task_with_observer";
constexpr char kStoryBaseWaitableEvent[] = "waitable_event";
constexpr char kStoryBaseCondVar[] = "condition_variable";
constexpr char kStorySuffixOneThread[] = "_1_thread";
constexpr char kStorySuffixFourThreads[] = "_4_threads";

#if defined(OS_POSIX)
constexpr char kStoryBasePthreadCondVar[] = "pthread_condition_variable";
#endif  // defined(OS_POSIX)

perf_test::PerfResultReporter SetUpReporter(const std::string& story_name) {
  perf_test::PerfResultReporter reporter(kMetricPrefixThread, story_name);
  reporter.RegisterImportantMetric(kMetricClockTimePerHop, "us");
  reporter.RegisterImportantMetric(kMetricCpuTimePerHop, "us");
  return reporter;
}

// Base class for a threading perf-test. This sets up some threads for the
// test and measures the clock-time in addition to time spent on each thread.
class ThreadPerfTest : public testing::Test {
 public:
  ThreadPerfTest()
      : done_(WaitableEvent::ResetPolicy::AUTOMATIC,
              WaitableEvent::InitialState::NOT_SIGNALED) {}

  // To be implemented by each test. Subclass must uses threads_ such that
  // their cpu-time can be measured. Test must return from PingPong() _and_
  // call FinishMeasurement from any thread to complete the test.
  virtual void Init() {
    if (ThreadTicks::IsSupported())
      ThreadTicks::WaitUntilInitialized();
  }
  virtual void PingPong(int hops) = 0;
  virtual void Reset() {}

  void TimeOnThread(base::ThreadTicks* ticks, base::WaitableEvent* done) {
    *ticks = base::ThreadTicks::Now();
    done->Signal();
  }

  base::ThreadTicks ThreadNow(const base::Thread& thread) {
    base::WaitableEvent done(WaitableEvent::ResetPolicy::AUTOMATIC,
                             WaitableEvent::InitialState::NOT_SIGNALED);
    base::ThreadTicks ticks;
    thread.task_runner()->PostTask(
        FROM_HERE, base::BindOnce(&ThreadPerfTest::TimeOnThread,
                                  base::Unretained(this), &ticks, &done));
    done.Wait();
    return ticks;
  }

  void RunPingPongTest(const std::string& story_name, unsigned num_threads) {
    // Create threads and collect starting cpu-time for each thread.
    std::vector<base::ThreadTicks> thread_starts;
    while (threads_.size() < num_threads) {
      threads_.push_back(std::make_unique<base::Thread>("PingPonger"));
      threads_.back()->Start();
      if (base::ThreadTicks::IsSupported())
        thread_starts.push_back(ThreadNow(*threads_.back()));
    }

    Init();

    base::TimeTicks start = base::TimeTicks::Now();
    PingPong(kNumRuns);
    done_.Wait();
    base::TimeTicks end = base::TimeTicks::Now();

    // Gather the cpu-time spent on each thread. This does one extra tasks,
    // but that should be in the noise given enough runs.
    base::TimeDelta thread_time;
    while (threads_.size()) {
      if (base::ThreadTicks::IsSupported()) {
        thread_time += ThreadNow(*threads_.back()) - thread_starts.back();
        thread_starts.pop_back();
      }
      threads_.pop_back();
    }

    Reset();

    double num_runs = static_cast<double>(kNumRuns);
    double us_per_task_clock = (end - start).InMicroseconds() / num_runs;
    double us_per_task_cpu = thread_time.InMicroseconds() / num_runs;

    auto reporter = SetUpReporter(story_name);
    // Clock time per task.
    reporter.AddResult(kMetricClockTimePerHop, us_per_task_clock);

    // Total utilization across threads if available (likely higher).
    if (base::ThreadTicks::IsSupported()) {
      reporter.AddResult(kMetricCpuTimePerHop, us_per_task_cpu);
    }
  }

 protected:
  void FinishMeasurement() { done_.Signal(); }
  std::vector<std::unique_ptr<base::Thread>> threads_;

 private:
  base::WaitableEvent done_;
};

// Class to test task performance by posting empty tasks back and forth.
class TaskPerfTest : public ThreadPerfTest {
  base::Thread* NextThread(int count) {
    return threads_[count % threads_.size()].get();
  }

  void PingPong(int hops) override {
    if (!hops) {
      FinishMeasurement();
      return;
    }
    NextThread(hops)->task_runner()->PostTask(
        FROM_HERE, base::BindOnce(&ThreadPerfTest::PingPong,
                                  base::Unretained(this), hops - 1));
  }
};

// This tries to test the 'best-case' as well as the 'worst-case' task posting
// performance. The best-case keeps one thread alive such that it never yeilds,
// while the worse-case forces a context switch for every task. Four threads are
// used to ensure the threads do yeild (with just two it might be possible for
// both threads to stay awake if they can signal each other fast enough).
TEST_F(TaskPerfTest, TaskPingPong) {
  RunPingPongTest(std::string(kStoryBaseTask) + kStorySuffixOneThread, 1);
  RunPingPongTest(std::string(kStoryBaseTask) + kStorySuffixFourThreads, 4);
}


// Same as above, but add observers to test their perf impact.
class MessageLoopObserver : public base::TaskObserver {
 public:
  void WillProcessTask(const base::PendingTask& pending_task,
                       bool was_blocked_or_low_priority) override {}
  void DidProcessTask(const base::PendingTask& pending_task) override {}
};
MessageLoopObserver message_loop_observer;

class TaskObserverPerfTest : public TaskPerfTest {
 public:
  void Init() override {
    TaskPerfTest::Init();
    for (auto& i : threads_) {
      i->task_runner()->PostTask(
          FROM_HERE, BindOnce(
                         [](MessageLoopObserver* observer) {
                           MessageLoopCurrent::Get()->AddTaskObserver(observer);
                         },
                         Unretained(&message_loop_observer)));
    }
  }
};

TEST_F(TaskObserverPerfTest, TaskPingPong) {
  RunPingPongTest(
      std::string(kStoryBaseTaskWithObserver) + kStorySuffixOneThread, 1);
  RunPingPongTest(
      std::string(kStoryBaseTaskWithObserver) + kStorySuffixFourThreads, 4);
}

// Class to test our WaitableEvent performance by signaling back and fort.
// WaitableEvent is templated so we can also compare with other versions.
template <typename WaitableEventType>
class EventPerfTest : public ThreadPerfTest {
 public:
  void Init() override {
    for (size_t i = 0; i < threads_.size(); i++) {
      events_.push_back(std::make_unique<WaitableEventType>(
          WaitableEvent::ResetPolicy::AUTOMATIC,
          WaitableEvent::InitialState::NOT_SIGNALED));
    }
  }

  void Reset() override { events_.clear(); }

  void WaitAndSignalOnThread(size_t event) {
    size_t next_event = (event + 1) % events_.size();
    int my_hops = 0;
    do {
      events_[event]->Wait();
      my_hops = --remaining_hops_;  // We own 'hops' between Wait and Signal.
      events_[next_event]->Signal();
    } while (my_hops > 0);
    // Once we are done, all threads will signal as hops passes zero.
    // We only signal completion once, on the thread that reaches zero.
    if (!my_hops)
      FinishMeasurement();
  }

  void PingPong(int hops) override {
    remaining_hops_ = hops;
    for (size_t i = 0; i < threads_.size(); i++) {
      threads_[i]->task_runner()->PostTask(
          FROM_HERE, base::BindOnce(&EventPerfTest::WaitAndSignalOnThread,
                                    base::Unretained(this), i));
    }

    // Kick off the Signal ping-ponging.
    events_.front()->Signal();
  }

  int remaining_hops_;
  std::vector<std::unique_ptr<WaitableEventType>> events_;
};

// Similar to the task posting test, this just tests similar functionality
// using WaitableEvents. We only test four threads (worst-case), but we
// might want to craft a way to test the best-case (where the thread doesn't
// end up blocking because the event is already signalled).
typedef EventPerfTest<base::WaitableEvent> WaitableEventThreadPerfTest;
TEST_F(WaitableEventThreadPerfTest, EventPingPong) {
  RunPingPongTest(
      std::string(kStoryBaseWaitableEvent) + kStorySuffixFourThreads, 4);
}

// Build a minimal event using ConditionVariable.
class ConditionVariableEvent {
 public:
  ConditionVariableEvent(WaitableEvent::ResetPolicy reset_policy,
                         WaitableEvent::InitialState initial_state)
      : cond_(&lock_), signaled_(false) {
    DCHECK_EQ(WaitableEvent::ResetPolicy::AUTOMATIC, reset_policy);
    DCHECK_EQ(WaitableEvent::InitialState::NOT_SIGNALED, initial_state);
  }

  void Signal() {
    {
      base::AutoLock scoped_lock(lock_);
      signaled_ = true;
    }
    cond_.Signal();
  }

  void Wait() {
    base::AutoLock scoped_lock(lock_);
    while (!signaled_)
      cond_.Wait();
    signaled_ = false;
  }

 private:
  base::Lock lock_;
  base::ConditionVariable cond_;
  bool signaled_;
};

// This is meant to test the absolute minimal context switching time
// using our own base synchronization code.
typedef EventPerfTest<ConditionVariableEvent> ConditionVariablePerfTest;
TEST_F(ConditionVariablePerfTest, EventPingPong) {
  RunPingPongTest(std::string(kStoryBaseCondVar) + kStorySuffixFourThreads, 4);
}
#if defined(OS_POSIX)

// Absolutely 100% minimal posix waitable event. If there is a better/faster
// way to force a context switch, we should use that instead.
class PthreadEvent {
 public:
  PthreadEvent(WaitableEvent::ResetPolicy reset_policy,
               WaitableEvent::InitialState initial_state) {
    DCHECK_EQ(WaitableEvent::ResetPolicy::AUTOMATIC, reset_policy);
    DCHECK_EQ(WaitableEvent::InitialState::NOT_SIGNALED, initial_state);
    pthread_mutex_init(&mutex_, nullptr);
    pthread_cond_init(&cond_, nullptr);
    signaled_ = false;
  }

  ~PthreadEvent() {
    pthread_cond_destroy(&cond_);
    pthread_mutex_destroy(&mutex_);
  }

  void Signal() {
    pthread_mutex_lock(&mutex_);
    signaled_ = true;
    pthread_mutex_unlock(&mutex_);
    pthread_cond_signal(&cond_);
  }

  void Wait() {
    pthread_mutex_lock(&mutex_);
    while (!signaled_)
      pthread_cond_wait(&cond_, &mutex_);
    signaled_ = false;
    pthread_mutex_unlock(&mutex_);
  }

 private:
  bool signaled_;
  pthread_mutex_t mutex_;
  pthread_cond_t cond_;
};

// This is meant to test the absolute minimal context switching time.
// If there is any faster way to do this we should substitute it in.
typedef EventPerfTest<PthreadEvent> PthreadEventPerfTest;
TEST_F(PthreadEventPerfTest, EventPingPong) {
  RunPingPongTest(
      std::string(kStoryBasePthreadCondVar) + kStorySuffixFourThreads, 4);
}

#endif

}  // namespace

}  // namespace base
