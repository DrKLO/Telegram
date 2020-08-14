// Copyright 2015 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

#include "base/task/sequence_manager/sequence_manager.h"

#include <stddef.h>
#include <memory>

#include "base/bind.h"
#include "base/message_loop/message_pump_default.h"
#include "base/message_loop/message_pump_type.h"
#include "base/run_loop.h"
#include "base/sequence_checker.h"
#include "base/single_thread_task_runner.h"
#include "base/strings/stringprintf.h"
#include "base/synchronization/condition_variable.h"
#include "base/task/post_task.h"
#include "base/task/sequence_manager/task_queue_impl.h"
#include "base/task/sequence_manager/test/mock_time_domain.h"
#include "base/task/sequence_manager/test/sequence_manager_for_test.h"
#include "base/task/sequence_manager/test/test_task_queue.h"
#include "base/task/sequence_manager/test/test_task_time_observer.h"
#include "base/task/sequence_manager/thread_controller_with_message_pump_impl.h"
#include "base/task/task_traits.h"
#include "base/task/thread_pool.h"
#include "base/task/thread_pool/thread_pool_impl.h"
#include "base/task/thread_pool/thread_pool_instance.h"
#include "base/threading/thread.h"
#include "base/threading/thread_task_runner_handle.h"
#include "base/time/default_tick_clock.h"
#include "build/build_config.h"
#include "testing/gtest/include/gtest/gtest.h"
#include "testing/perf/perf_result_reporter.h"

namespace base {
namespace sequence_manager {
namespace {
const int kNumTasks = 1000000;

constexpr char kMetricPrefixSequenceManager[] = "SequenceManager.";
constexpr char kMetricPostTimePerTask[] = "post_time_per_task";

perf_test::PerfResultReporter SetUpReporter(const std::string& story_name) {
  perf_test::PerfResultReporter reporter(kMetricPrefixSequenceManager,
                                         story_name);
  reporter.RegisterImportantMetric(kMetricPostTimePerTask, "us");
  return reporter;
}

}  // namespace

// To reduce noise related to the OS timer, we use a mock time domain to
// fast forward the timers.
class PerfTestTimeDomain : public MockTimeDomain {
 public:
  PerfTestTimeDomain() : MockTimeDomain(TimeTicks::Now()) {}
  ~PerfTestTimeDomain() override = default;

  Optional<TimeDelta> DelayTillNextTask(LazyNow* lazy_now) override {
    Optional<TimeTicks> run_time = NextScheduledRunTime();
    if (!run_time)
      return nullopt;
    SetNowTicks(*run_time);
    // Makes SequenceManager to continue immediately.
    return TimeDelta();
  }

  void SetNextDelayedDoWork(LazyNow* lazy_now, TimeTicks run_time) override {
    // De-dupe DoWorks.
    if (NumberOfScheduledWakeUps() == 1u)
      RequestDoWork();
  }

 private:
  DISALLOW_COPY_AND_ASSIGN(PerfTestTimeDomain);
};

enum class PerfTestType {
  // A SequenceManager with a ThreadControllerWithMessagePumpImpl driving the
  // thread.
  kUseSequenceManagerWithMessagePump,
  kUseSequenceManagerWithUIMessagePump,
  kUseSequenceManagerWithIOMessagePump,
  kUseSequenceManagerWithMessagePumpAndRandomSampling,

  // A SingleThreadTaskRunner in the thread pool.
  kUseSingleThreadInThreadPool,
};

// Customization point for SequenceManagerPerfTest which allows us to test
// various implementations.
class PerfTestDelegate {
 public:
  virtual ~PerfTestDelegate() = default;

  virtual const char* GetName() const = 0;

  virtual bool VirtualTimeIsSupported() const = 0;

  virtual bool MultipleQueuesSupported() const = 0;

  virtual scoped_refptr<TaskRunner> CreateTaskRunner() = 0;

  virtual void WaitUntilDone() = 0;

  virtual void SignalDone() = 0;
};

class BaseSequenceManagerPerfTestDelegate : public PerfTestDelegate {
 public:
  BaseSequenceManagerPerfTestDelegate() {}

  ~BaseSequenceManagerPerfTestDelegate() override = default;

  bool VirtualTimeIsSupported() const override { return true; }

  bool MultipleQueuesSupported() const override { return true; }

  scoped_refptr<TaskRunner> CreateTaskRunner() override {
    scoped_refptr<TestTaskQueue> task_queue =
        manager_->CreateTaskQueueWithType<TestTaskQueue>(
            TaskQueue::Spec("test").SetTimeDomain(time_domain_.get()));
    owned_task_queues_.push_back(task_queue);
    return task_queue->task_runner();
  }

  void WaitUntilDone() override {
    run_loop_.reset(new RunLoop());
    run_loop_->Run();
  }

  void SignalDone() override { run_loop_->Quit(); }

  SequenceManager* GetManager() const { return manager_.get(); }

  void SetSequenceManager(std::unique_ptr<SequenceManager> manager) {
    manager_ = std::move(manager);
    time_domain_ = std::make_unique<PerfTestTimeDomain>();
    manager_->RegisterTimeDomain(time_domain_.get());
  }

  void ShutDown() {
    owned_task_queues_.clear();
    manager_->UnregisterTimeDomain(time_domain_.get());
    manager_.reset();
  }

 private:
  std::unique_ptr<SequenceManager> manager_;
  std::unique_ptr<TimeDomain> time_domain_;
  std::unique_ptr<RunLoop> run_loop_;
  std::vector<scoped_refptr<TestTaskQueue>> owned_task_queues_;
};

class SequenceManagerWithMessagePumpPerfTestDelegate
    : public BaseSequenceManagerPerfTestDelegate {
 public:
  SequenceManagerWithMessagePumpPerfTestDelegate(
      const char* name,
      MessagePumpType type,
      bool randomised_sampling_enabled = false)
      : name_(name) {
    auto settings =
        SequenceManager::Settings::Builder()
            .SetRandomisedSamplingEnabled(randomised_sampling_enabled)
            .Build();
    SetSequenceManager(SequenceManagerForTest::Create(
        std::make_unique<internal::ThreadControllerWithMessagePumpImpl>(
            MessagePump::Create(type), settings),
        std::move(settings)));

    // ThreadControllerWithMessagePumpImpl doesn't provide a default task
    // runner.
    scoped_refptr<TaskQueue> default_task_queue =
        GetManager()->template CreateTaskQueueWithType<TestTaskQueue>(
            TaskQueue::Spec("default"));
    GetManager()->SetDefaultTaskRunner(default_task_queue->task_runner());
  }

  ~SequenceManagerWithMessagePumpPerfTestDelegate() override { ShutDown(); }

  const char* GetName() const override { return name_; }

 private:
  const char* const name_;
};

class SingleThreadInThreadPoolPerfTestDelegate : public PerfTestDelegate {
 public:
  SingleThreadInThreadPoolPerfTestDelegate() : done_cond_(&done_lock_) {
    ThreadPoolInstance::Set(
        std::make_unique<::base::internal::ThreadPoolImpl>("Test"));
    ThreadPoolInstance::Get()->StartWithDefaultParams();
  }

  ~SingleThreadInThreadPoolPerfTestDelegate() override {
    ThreadPoolInstance::Get()->JoinForTesting();
    ThreadPoolInstance::Set(nullptr);
  }

  const char* GetName() const override {
    return " single thread in ThreadPool ";
  }

  bool VirtualTimeIsSupported() const override { return false; }

  bool MultipleQueuesSupported() const override { return false; }

  scoped_refptr<TaskRunner> CreateTaskRunner() override {
    return ThreadPool::CreateSingleThreadTaskRunner(
        {TaskPriority::USER_BLOCKING});
  }

  void WaitUntilDone() override {
    AutoLock auto_lock(done_lock_);
    done_cond_.Wait();
  }

  void SignalDone() override {
    AutoLock auto_lock(done_lock_);
    done_cond_.Signal();
  }

 private:
  Lock done_lock_;
  ConditionVariable done_cond_;
};

class TestCase {
 public:
  // |delegate| is assumed to outlive TestCase.
  explicit TestCase(PerfTestDelegate* delegate) : delegate_(delegate) {}

  virtual ~TestCase() = default;

  virtual void Start() = 0;

 protected:
  PerfTestDelegate* const delegate_;  // NOT OWNED
};

class TaskSource {
 public:
  virtual ~TaskSource() = default;

  virtual void Start() = 0;
};

class SameThreadTaskSource : public TaskSource {
 public:
  SameThreadTaskSource(std::vector<scoped_refptr<TaskRunner>> task_runners,
                       size_t num_tasks)
      : num_queues_(task_runners.size()),
        num_tasks_(num_tasks),
        task_closure_(
            BindRepeating(&SameThreadTaskSource::TestTask, Unretained(this))),
        task_runners_(std::move(task_runners)) {
    DETACH_FROM_SEQUENCE(sequence_checker_);
  }

  void Start() override {
    num_tasks_in_flight_ = 1;
    num_tasks_to_post_ = num_tasks_;
    num_tasks_to_run_ = num_tasks_;
    // Post the initial task instead of running it synchronously to ensure that
    // all invocations happen on the same sequence.
    PostTask(0);
  }

 protected:
  virtual void PostTask(unsigned int queue) = 0;

  virtual void SignalDone() = 0;

  void TestTask() {
    DCHECK_CALLED_ON_VALID_SEQUENCE(sequence_checker_);

    if (--num_tasks_to_run_ == 0) {
      SignalDone();
      return;
    }

    num_tasks_in_flight_--;
    // NOTE there are only up to max_tasks_in_flight_ pending delayed tasks at
    // any one time.  Thanks to the lower_num_tasks_to_post going to zero if
    // there are a lot of tasks in flight, the total number of task in flight at
    // any one time is very variable.
    unsigned int lower_num_tasks_to_post =
        num_tasks_in_flight_ < (max_tasks_in_flight_ / 2) ? 1 : 0;
    unsigned int max_tasks_to_post =
        num_tasks_to_post_ % 2 ? lower_num_tasks_to_post : 10;
    for (unsigned int i = 0;
         i < max_tasks_to_post && num_tasks_in_flight_ < max_tasks_in_flight_ &&
         num_tasks_to_post_ > 0;
         i++) {
      // Choose a queue weighted towards queue 0.
      unsigned int queue = num_tasks_to_post_ % (num_queues_ + 1);
      if (queue == num_queues_) {
        queue = 0;
      }
      PostTask(queue);
      num_tasks_in_flight_++;
      num_tasks_to_post_--;
    }
  }

  const size_t num_queues_;
  const size_t num_tasks_;
  const RepeatingClosure task_closure_;
  const std::vector<scoped_refptr<TaskRunner>> task_runners_;
  const unsigned int max_tasks_in_flight_ = 200;
  unsigned int num_tasks_in_flight_;
  unsigned int num_tasks_to_post_;
  unsigned int num_tasks_to_run_;
  SEQUENCE_CHECKER(sequence_checker_);
};

class CrossThreadTaskSource : public TaskSource {
 public:
  CrossThreadTaskSource(std::vector<scoped_refptr<TaskRunner>> task_runners,
                        size_t num_tasks)
      : num_queues_(task_runners.size()),
        num_tasks_(num_tasks),
        task_closure_(
            BindRepeating(&CrossThreadTaskSource::TestTask, Unretained(this))),
        task_runners_(std::move(task_runners)) {}

  void Start() override {
    num_tasks_in_flight_ = 0;
    num_tasks_to_run_ = num_tasks_;

    for (size_t i = 0; i < num_tasks_; i++) {
      while (num_tasks_in_flight_.load(std::memory_order_acquire) >
             max_tasks_in_flight_) {
        PlatformThread::YieldCurrentThread();
      }
      // Choose a queue weighted towards queue 0.
      unsigned int queue = i % (num_queues_ + 1);
      if (queue == num_queues_) {
        queue = 0;
      }
      PostTask(queue);
      num_tasks_in_flight_++;
    }
  }

 protected:
  virtual void PostTask(unsigned int queue) = 0;

  // Will be called on the main thread.
  virtual void SignalDone() = 0;

  void TestTask() {
    if (num_tasks_to_run_.fetch_sub(1) == 1) {
      SignalDone();
      return;
    }
    num_tasks_in_flight_--;
  }

  const size_t num_queues_;
  const size_t num_tasks_;
  const RepeatingClosure task_closure_;
  const std::vector<scoped_refptr<TaskRunner>> task_runners_;
  const unsigned int max_tasks_in_flight_ = 200;
  std::atomic<unsigned int> num_tasks_in_flight_;
  std::atomic<unsigned int> num_tasks_to_run_;
};

class SingleThreadImmediateTestCase : public TestCase {
 public:
  SingleThreadImmediateTestCase(
      PerfTestDelegate* delegate,
      std::vector<scoped_refptr<TaskRunner>> task_runners)
      : TestCase(delegate),
        task_source_(std::make_unique<SingleThreadImmediateTaskSource>(
            delegate,
            std::move(task_runners),
            kNumTasks)) {}

  void Start() override { task_source_->Start(); }

 private:
  class SingleThreadImmediateTaskSource : public SameThreadTaskSource {
   public:
    SingleThreadImmediateTaskSource(
        PerfTestDelegate* delegate,
        std::vector<scoped_refptr<TaskRunner>> task_runners,
        size_t num_tasks)
        : SameThreadTaskSource(std::move(task_runners), num_tasks),
          delegate_(delegate) {}

    ~SingleThreadImmediateTaskSource() override = default;

    void PostTask(unsigned int queue) override {
      task_runners_[queue]->PostTask(FROM_HERE, task_closure_);
    }

    void SignalDone() override { delegate_->SignalDone(); }

    PerfTestDelegate* delegate_;  // NOT OWNED.
  };

  const std::unique_ptr<TaskSource> task_source_;
};

class SingleThreadDelayedTestCase : public TestCase {
 public:
  SingleThreadDelayedTestCase(
      PerfTestDelegate* delegate,
      std::vector<scoped_refptr<TaskRunner>> task_runners)
      : TestCase(delegate),
        task_source_(std::make_unique<SingleThreadDelayedTaskSource>(
            delegate,
            std::move(task_runners),
            kNumTasks)) {}

  void Start() override { task_source_->Start(); }

 private:
  class SingleThreadDelayedTaskSource : public SameThreadTaskSource {
   public:
    explicit SingleThreadDelayedTaskSource(
        PerfTestDelegate* delegate,
        std::vector<scoped_refptr<TaskRunner>> task_runners,
        size_t num_tasks)
        : SameThreadTaskSource(std::move(task_runners), num_tasks),
          delegate_(delegate) {}

    ~SingleThreadDelayedTaskSource() override = default;

    void PostTask(unsigned int queue) override {
      unsigned int delay =
          num_tasks_to_post_ % 2 ? 1 : (10 + num_tasks_to_post_ % 10);
      task_runners_[queue]->PostDelayedTask(FROM_HERE, task_closure_,
                                            TimeDelta::FromMilliseconds(delay));
    }

    void SignalDone() override { delegate_->SignalDone(); }

    PerfTestDelegate* delegate_;  // NOT OWNED.
  };

  const std::unique_ptr<TaskSource> task_source_;
};

class TwoThreadTestCase : public TestCase {
 public:
  TwoThreadTestCase(PerfTestDelegate* delegate,
                    std::vector<scoped_refptr<TaskRunner>> task_runners)
      : TestCase(delegate),
        task_runners_(std::move(task_runners)),
        num_tasks_(kNumTasks),
        auxiliary_thread_("auxillary thread") {
    auxiliary_thread_.Start();
  }

  ~TwoThreadTestCase() override { auxiliary_thread_.Stop(); }

 protected:
  void Start() override {
    done_count_ = 0;
    same_thread_task_source_ =
        std::make_unique<SingleThreadImmediateTaskSource>(this, task_runners_,
                                                          num_tasks_ / 2);
    cross_thread_task_scorce_ =
        std::make_unique<CrossThreadImmediateTaskSource>(this, task_runners_,
                                                         num_tasks_ / 2);

    auxiliary_thread_.task_runner()->PostTask(
        FROM_HERE, base::BindOnce(&CrossThreadImmediateTaskSource::Start,
                                  Unretained(cross_thread_task_scorce_.get())));
    same_thread_task_source_->Start();
  }

  class SingleThreadImmediateTaskSource : public SameThreadTaskSource {
   public:
    SingleThreadImmediateTaskSource(
        TwoThreadTestCase* two_thread_test_case,
        std::vector<scoped_refptr<TaskRunner>> task_runners,
        size_t num_tasks)
        : SameThreadTaskSource(std::move(task_runners), num_tasks),
          two_thread_test_case_(two_thread_test_case) {}

    ~SingleThreadImmediateTaskSource() override = default;

    void PostTask(unsigned int queue) override {
      task_runners_[queue]->PostTask(FROM_HERE, task_closure_);
    }

    // Will be called on the main thread.
    void SignalDone() override { two_thread_test_case_->SignalDone(); }

    TwoThreadTestCase* two_thread_test_case_;  // NOT OWNED.
  };

  class CrossThreadImmediateTaskSource : public CrossThreadTaskSource {
   public:
    CrossThreadImmediateTaskSource(
        TwoThreadTestCase* two_thread_test_case,
        std::vector<scoped_refptr<TaskRunner>> task_runners,
        size_t num_tasks)
        : CrossThreadTaskSource(std::move(task_runners), num_tasks),
          two_thread_test_case_(two_thread_test_case) {}

    ~CrossThreadImmediateTaskSource() override = default;

    void PostTask(unsigned int queue) override {
      task_runners_[queue]->PostTask(FROM_HERE, task_closure_);
    }

    // Will be called on the main thread.
    void SignalDone() override { two_thread_test_case_->SignalDone(); }

    TwoThreadTestCase* two_thread_test_case_;  // NOT OWNED.
  };

  void SignalDone() {
    if (++done_count_ == 2)
      delegate_->SignalDone();
  }

 private:
  const std::vector<scoped_refptr<TaskRunner>> task_runners_;
  const size_t num_tasks_;
  Thread auxiliary_thread_;
  std::unique_ptr<SingleThreadImmediateTaskSource> same_thread_task_source_;
  std::unique_ptr<CrossThreadImmediateTaskSource> cross_thread_task_scorce_;
  int done_count_ = 0;
};

class SequenceManagerPerfTest : public testing::TestWithParam<PerfTestType> {
 public:
  SequenceManagerPerfTest() = default;

  void SetUp() override { delegate_ = CreateDelegate(); }

  void TearDown() override { delegate_.reset(); }

  std::unique_ptr<PerfTestDelegate> CreateDelegate() {
    switch (GetParam()) {
      case PerfTestType::kUseSequenceManagerWithMessagePump:
        return std::make_unique<SequenceManagerWithMessagePumpPerfTestDelegate>(
            " SequenceManager with MessagePumpDefault ",
            MessagePumpType::DEFAULT);

      case PerfTestType::kUseSequenceManagerWithUIMessagePump:
        return std::make_unique<SequenceManagerWithMessagePumpPerfTestDelegate>(
            " SequenceManager with MessagePumpForUI ", MessagePumpType::UI);

      case PerfTestType::kUseSequenceManagerWithIOMessagePump:
        return std::make_unique<SequenceManagerWithMessagePumpPerfTestDelegate>(
            " SequenceManager with MessagePumpForIO ", MessagePumpType::IO);

      case PerfTestType::kUseSequenceManagerWithMessagePumpAndRandomSampling:
        return std::make_unique<SequenceManagerWithMessagePumpPerfTestDelegate>(
            " SequenceManager with MessagePumpDefault and random sampling ",
            MessagePumpType::DEFAULT, true);

      case PerfTestType::kUseSingleThreadInThreadPool:
        return std::make_unique<SingleThreadInThreadPoolPerfTestDelegate>();

      default:
        NOTREACHED();
        return nullptr;
    }
  }

  bool ShouldMeasureQueueScaling() const {
    // To limit test run time, we only measure multiple queues specific sequence
    // manager configurations.
    return delegate_->MultipleQueuesSupported() &&
           GetParam() == PerfTestType::kUseSequenceManagerWithUIMessagePump;
  }

  std::vector<scoped_refptr<TaskRunner>> CreateTaskRunners(int num) {
    std::vector<scoped_refptr<TaskRunner>> task_runners;
    for (int i = 0; i < num; i++) {
      task_runners.push_back(delegate_->CreateTaskRunner());
    }
    return task_runners;
  }

  void Benchmark(const std::string& story_prefix, TestCase* TestCase) {
    TimeTicks start = TimeTicks::Now();
    TimeTicks now;
    TestCase->Start();
    delegate_->WaitUntilDone();
    now = TimeTicks::Now();

    auto reporter = SetUpReporter(story_prefix + delegate_->GetName());
    reporter.AddResult(
        kMetricPostTimePerTask,
        (now - start).InMicroseconds() / static_cast<double>(kNumTasks));
  }

  std::unique_ptr<PerfTestDelegate> delegate_;
};

INSTANTIATE_TEST_SUITE_P(
    All,
    SequenceManagerPerfTest,
    testing::Values(
        PerfTestType::kUseSequenceManagerWithMessagePump,
        PerfTestType::kUseSequenceManagerWithUIMessagePump,
        PerfTestType::kUseSequenceManagerWithIOMessagePump,
        PerfTestType::kUseSingleThreadInThreadPool,
        PerfTestType::kUseSequenceManagerWithMessagePumpAndRandomSampling));
TEST_P(SequenceManagerPerfTest, PostDelayedTasks_OneQueue) {
  if (!delegate_->VirtualTimeIsSupported()) {
    LOG(INFO) << "Unsupported";
    return;
  }

  SingleThreadDelayedTestCase task_source(delegate_.get(),
                                          CreateTaskRunners(1));
  Benchmark("post delayed tasks with one queue", &task_source);
}

TEST_P(SequenceManagerPerfTest, PostDelayedTasks_FourQueues) {
  if (!delegate_->VirtualTimeIsSupported() || !ShouldMeasureQueueScaling()) {
    LOG(INFO) << "Unsupported";
    return;
  }

  SingleThreadDelayedTestCase task_source(delegate_.get(),
                                          CreateTaskRunners(4));
  Benchmark("post delayed tasks with four queues", &task_source);
}

TEST_P(SequenceManagerPerfTest, PostDelayedTasks_EightQueues) {
  if (!delegate_->VirtualTimeIsSupported() || !ShouldMeasureQueueScaling()) {
    LOG(INFO) << "Unsupported";
    return;
  }

  SingleThreadDelayedTestCase task_source(delegate_.get(),
                                          CreateTaskRunners(8));
  Benchmark("post delayed tasks with eight queues", &task_source);
}

TEST_P(SequenceManagerPerfTest, PostDelayedTasks_ThirtyTwoQueues) {
  if (!delegate_->VirtualTimeIsSupported() || !ShouldMeasureQueueScaling()) {
    LOG(INFO) << "Unsupported";
    return;
  }

  SingleThreadDelayedTestCase task_source(delegate_.get(),
                                          CreateTaskRunners(32));
  Benchmark("post delayed tasks with thirty two queues", &task_source);
}

TEST_P(SequenceManagerPerfTest, PostImmediateTasks_OneQueue) {
  SingleThreadImmediateTestCase task_source(delegate_.get(),
                                            CreateTaskRunners(1));
  Benchmark("post immediate tasks with one queue", &task_source);
}

TEST_P(SequenceManagerPerfTest, PostImmediateTasks_FourQueues) {
  if (!ShouldMeasureQueueScaling()) {
    LOG(INFO) << "Unsupported";
    return;
  }

  SingleThreadImmediateTestCase task_source(delegate_.get(),
                                            CreateTaskRunners(4));
  Benchmark("post immediate tasks with four queues", &task_source);
}

TEST_P(SequenceManagerPerfTest, PostImmediateTasks_EightQueues) {
  if (!ShouldMeasureQueueScaling()) {
    LOG(INFO) << "Unsupported";
    return;
  }

  SingleThreadImmediateTestCase task_source(delegate_.get(),
                                            CreateTaskRunners(8));
  Benchmark("post immediate tasks with eight queues", &task_source);
}

TEST_P(SequenceManagerPerfTest, PostImmediateTasks_ThirtyTwoQueues) {
  if (!ShouldMeasureQueueScaling()) {
    LOG(INFO) << "Unsupported";
    return;
  }

  SingleThreadImmediateTestCase task_source(delegate_.get(),
                                            CreateTaskRunners(32));
  Benchmark("post immediate tasks with thirty two queues", &task_source);
}

TEST_P(SequenceManagerPerfTest, PostImmediateTasksFromTwoThreads_OneQueue) {
  TwoThreadTestCase task_source(delegate_.get(), CreateTaskRunners(1));
  Benchmark("post immediate tasks with one queue from two threads",
            &task_source);
}

TEST_P(SequenceManagerPerfTest, PostImmediateTasksFromTwoThreads_FourQueues) {
  if (!ShouldMeasureQueueScaling()) {
    LOG(INFO) << "Unsupported";
    return;
  }

  TwoThreadTestCase task_source(delegate_.get(), CreateTaskRunners(4));
  Benchmark("post immediate tasks with four queues from two threads",
            &task_source);
}

TEST_P(SequenceManagerPerfTest, PostImmediateTasksFromTwoThreads_EightQueues) {
  if (!ShouldMeasureQueueScaling()) {
    LOG(INFO) << "Unsupported";
    return;
  }

  TwoThreadTestCase task_source(delegate_.get(), CreateTaskRunners(8));
  Benchmark("post immediate tasks with eight queues from two threads",
            &task_source);
}

TEST_P(SequenceManagerPerfTest,
       PostImmediateTasksFromTwoThreads_ThirtyTwoQueues) {
  if (!ShouldMeasureQueueScaling()) {
    LOG(INFO) << "Unsupported";
    return;
  }

  TwoThreadTestCase task_source(delegate_.get(), CreateTaskRunners(32));
  Benchmark("post immediate tasks with thirty two queues from two threads",
            &task_source);
}

// TODO(alexclarke): Add additional tests with different mixes of non-delayed vs
// delayed tasks.

}  // namespace sequence_manager
}  // namespace base
