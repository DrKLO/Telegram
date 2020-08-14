// Copyright 2014 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

#include <stddef.h>
#include <stdint.h>

#include "base/bind.h"
#include "base/bind_helpers.h"
#include "base/format_macros.h"
#include "base/memory/ptr_util.h"
#include "base/message_loop/message_loop_current.h"
#include "base/message_loop/message_pump_type.h"
#include "base/single_thread_task_runner.h"
#include "base/strings/stringprintf.h"
#include "base/synchronization/condition_variable.h"
#include "base/synchronization/lock.h"
#include "base/synchronization/waitable_event.h"
#include "base/task/sequence_manager/sequence_manager_impl.h"
#include "base/threading/thread.h"
#include "base/time/time.h"
#include "build/build_config.h"
#include "testing/gtest/include/gtest/gtest.h"
#include "testing/perf/perf_result_reporter.h"

#if defined(OS_ANDROID)
#include "base/android/java_handler_thread.h"
#endif

namespace base {
namespace {

constexpr char kMetricPrefixScheduleWork[] = "ScheduleWork.";
constexpr char kMetricMinBatchTime[] = "min_batch_time_per_task";
constexpr char kMetricMaxBatchTime[] = "max_batch_time_per_task";
constexpr char kMetricTotalTime[] = "total_time_per_task";
constexpr char kMetricThreadTime[] = "thread_time_per_task";

perf_test::PerfResultReporter SetUpReporter(const std::string& story_name) {
  perf_test::PerfResultReporter reporter(kMetricPrefixScheduleWork, story_name);
  reporter.RegisterImportantMetric(kMetricMinBatchTime, "us");
  reporter.RegisterImportantMetric(kMetricMaxBatchTime, "us");
  reporter.RegisterImportantMetric(kMetricTotalTime, "us");
  reporter.RegisterImportantMetric(kMetricThreadTime, "us");
  return reporter;
}

#if defined(OS_ANDROID)
class JavaHandlerThreadForTest : public android::JavaHandlerThread {
 public:
  explicit JavaHandlerThreadForTest(const char* name)
      : android::JavaHandlerThread(name, base::ThreadPriority::NORMAL) {}

  using android::JavaHandlerThread::state;
  using android::JavaHandlerThread::State;
};
#endif

}  // namespace

class ScheduleWorkTest : public testing::Test {
 public:
  ScheduleWorkTest() : counter_(0) {}

  void SetUp() override {
    if (base::ThreadTicks::IsSupported())
      base::ThreadTicks::WaitUntilInitialized();
  }

  void Increment(uint64_t amount) { counter_ += amount; }

  void Schedule(int index) {
    base::TimeTicks start = base::TimeTicks::Now();
    base::ThreadTicks thread_start;
    if (ThreadTicks::IsSupported())
      thread_start = base::ThreadTicks::Now();
    base::TimeDelta minimum = base::TimeDelta::Max();
    base::TimeDelta maximum = base::TimeDelta();
    base::TimeTicks now, lastnow = start;
    uint64_t schedule_calls = 0u;
    do {
      for (size_t i = 0; i < kBatchSize; ++i) {
        target_message_loop_base()->GetMessagePump()->ScheduleWork();
        schedule_calls++;
      }
      now = base::TimeTicks::Now();
      base::TimeDelta laptime = now - lastnow;
      lastnow = now;
      minimum = std::min(minimum, laptime);
      maximum = std::max(maximum, laptime);
    } while (now - start < base::TimeDelta::FromSeconds(kTargetTimeSec));

    scheduling_times_[index] = now - start;
    if (ThreadTicks::IsSupported())
      scheduling_thread_times_[index] =
          base::ThreadTicks::Now() - thread_start;
    min_batch_times_[index] = minimum;
    max_batch_times_[index] = maximum;
    target_message_loop_base()->GetTaskRunner()->PostTask(
        FROM_HERE, base::BindOnce(&ScheduleWorkTest::Increment,
                                  base::Unretained(this), schedule_calls));
  }

  void ScheduleWork(MessagePumpType target_type, int num_scheduling_threads) {
#if defined(OS_ANDROID)
    if (target_type == MessagePumpType::JAVA) {
      java_thread_.reset(new JavaHandlerThreadForTest("target"));
      java_thread_->Start();
    } else
#endif
    {
      target_.reset(new Thread("test"));

      Thread::Options options(target_type, 0u);
      options.message_pump_type = target_type;
      target_->StartWithOptions(options);

      // Without this, it's possible for the scheduling threads to start and run
      // before the target thread. In this case, the scheduling threads will
      // call target_message_loop()->ScheduleWork(), which dereferences the
      // loop's message pump, which is only created after the target thread has
      // finished starting.
      target_->WaitUntilThreadStarted();
    }

    std::vector<std::unique_ptr<Thread>> scheduling_threads;
    scheduling_times_.reset(new base::TimeDelta[num_scheduling_threads]);
    scheduling_thread_times_.reset(new base::TimeDelta[num_scheduling_threads]);
    min_batch_times_.reset(new base::TimeDelta[num_scheduling_threads]);
    max_batch_times_.reset(new base::TimeDelta[num_scheduling_threads]);

    for (int i = 0; i < num_scheduling_threads; ++i) {
      scheduling_threads.push_back(std::make_unique<Thread>("posting thread"));
      scheduling_threads[i]->Start();
    }

    for (int i = 0; i < num_scheduling_threads; ++i) {
      scheduling_threads[i]->task_runner()->PostTask(
          FROM_HERE, base::BindOnce(&ScheduleWorkTest::Schedule,
                                    base::Unretained(this), i));
    }

    for (int i = 0; i < num_scheduling_threads; ++i) {
      scheduling_threads[i]->Stop();
    }
#if defined(OS_ANDROID)
    if (target_type == MessagePumpType::JAVA) {
      java_thread_->Stop();
      java_thread_.reset();
    } else
#endif
    {
      target_->Stop();
      target_.reset();
    }
    base::TimeDelta total_time;
    base::TimeDelta total_thread_time;
    base::TimeDelta min_batch_time = base::TimeDelta::Max();
    base::TimeDelta max_batch_time = base::TimeDelta();
    for (int i = 0; i < num_scheduling_threads; ++i) {
      total_time += scheduling_times_[i];
      total_thread_time += scheduling_thread_times_[i];
      min_batch_time = std::min(min_batch_time, min_batch_times_[i]);
      max_batch_time = std::max(max_batch_time, max_batch_times_[i]);
    }

    std::string story_name = StringPrintf(
        "%s_pump_from_%d_threads",
        target_type == MessagePumpType::IO
            ? "io"
            : (target_type == MessagePumpType::UI ? "ui" : "default"),
        num_scheduling_threads);
    auto reporter = SetUpReporter(story_name);
    reporter.AddResult(kMetricMinBatchTime, total_time.InMicroseconds() /
                                                static_cast<double>(counter_));
    reporter.AddResult(
        kMetricMaxBatchTime,
        max_batch_time.InMicroseconds() / static_cast<double>(kBatchSize));
    reporter.AddResult(kMetricTotalTime, total_time.InMicroseconds() /
                                             static_cast<double>(counter_));
    if (ThreadTicks::IsSupported()) {
      reporter.AddResult(kMetricThreadTime, total_thread_time.InMicroseconds() /
                                                static_cast<double>(counter_));
    }
  }

  sequence_manager::internal::SequenceManagerImpl* target_message_loop_base() {
#if defined(OS_ANDROID)
    if (java_thread_) {
      return static_cast<sequence_manager::internal::SequenceManagerImpl*>(
          java_thread_->state()->sequence_manager.get());
    }
#endif
    return MessageLoopCurrent::Get()->GetCurrentSequenceManagerImpl();
  }

 private:
  std::unique_ptr<Thread> target_;
#if defined(OS_ANDROID)
  std::unique_ptr<JavaHandlerThreadForTest> java_thread_;
#endif
  std::unique_ptr<base::TimeDelta[]> scheduling_times_;
  std::unique_ptr<base::TimeDelta[]> scheduling_thread_times_;
  std::unique_ptr<base::TimeDelta[]> min_batch_times_;
  std::unique_ptr<base::TimeDelta[]> max_batch_times_;
  uint64_t counter_;

  static const size_t kTargetTimeSec = 5;
  static const size_t kBatchSize = 1000;
};

TEST_F(ScheduleWorkTest, ThreadTimeToIOFromOneThread) {
  ScheduleWork(MessagePumpType::IO, 1);
}

TEST_F(ScheduleWorkTest, ThreadTimeToIOFromTwoThreads) {
  ScheduleWork(MessagePumpType::IO, 2);
}

TEST_F(ScheduleWorkTest, ThreadTimeToIOFromFourThreads) {
  ScheduleWork(MessagePumpType::IO, 4);
}

TEST_F(ScheduleWorkTest, ThreadTimeToUIFromOneThread) {
  ScheduleWork(MessagePumpType::UI, 1);
}

TEST_F(ScheduleWorkTest, ThreadTimeToUIFromTwoThreads) {
  ScheduleWork(MessagePumpType::UI, 2);
}

TEST_F(ScheduleWorkTest, ThreadTimeToUIFromFourThreads) {
  ScheduleWork(MessagePumpType::UI, 4);
}

TEST_F(ScheduleWorkTest, ThreadTimeToDefaultFromOneThread) {
  ScheduleWork(MessagePumpType::DEFAULT, 1);
}

TEST_F(ScheduleWorkTest, ThreadTimeToDefaultFromTwoThreads) {
  ScheduleWork(MessagePumpType::DEFAULT, 2);
}

TEST_F(ScheduleWorkTest, ThreadTimeToDefaultFromFourThreads) {
  ScheduleWork(MessagePumpType::DEFAULT, 4);
}

#if defined(OS_ANDROID)
TEST_F(ScheduleWorkTest, ThreadTimeToJavaFromOneThread) {
  ScheduleWork(MessagePumpType::JAVA, 1);
}

TEST_F(ScheduleWorkTest, ThreadTimeToJavaFromTwoThreads) {
  ScheduleWork(MessagePumpType::JAVA, 2);
}

TEST_F(ScheduleWorkTest, ThreadTimeToJavaFromFourThreads) {
  ScheduleWork(MessagePumpType::JAVA, 4);
}
#endif

}  // namespace base
