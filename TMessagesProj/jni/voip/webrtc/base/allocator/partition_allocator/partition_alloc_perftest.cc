// Copyright 2019 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

#include <atomic>
#include <vector>

#include "base/allocator/partition_allocator/partition_alloc.h"
#include "base/strings/stringprintf.h"
#include "base/threading/platform_thread.h"
#include "base/time/time.h"
#include "base/timer/lap_timer.h"
#include "build/build_config.h"
#include "testing/gtest/include/gtest/gtest.h"
#include "testing/perf/perf_result_reporter.h"

namespace base {
namespace {

// Change kTimeLimit to something higher if you need more time to capture a
// trace.
constexpr base::TimeDelta kTimeLimit = base::TimeDelta::FromSeconds(2);
constexpr int kWarmupRuns = 5;
constexpr int kTimeCheckInterval = 100000;

// Size constants are mostly arbitrary, but try to simulate something like CSS
// parsing which consists of lots of relatively small objects.
constexpr int kMultiBucketMinimumSize = 24;
constexpr int kMultiBucketIncrement = 13;
// Final size is 24 + (13 * 22) = 310 bytes.
constexpr int kMultiBucketRounds = 22;

constexpr char kMetricPrefixMemoryAllocation[] = "MemoryAllocation";
constexpr char kMetricThroughput[] = "throughput";
constexpr char kMetricTimePerAllocation[] = "time_per_allocation";

perf_test::PerfResultReporter SetUpReporter(const std::string& story_name) {
  perf_test::PerfResultReporter reporter(kMetricPrefixMemoryAllocation,
                                         story_name);
  reporter.RegisterImportantMetric(kMetricThroughput, "runs/s");
  reporter.RegisterImportantMetric(kMetricTimePerAllocation, "ns");
  return reporter;
}

enum class AllocatorType { kSystem, kPartitionAlloc };

class Allocator {
 public:
  Allocator() = default;
  virtual ~Allocator() = default;
  virtual void Init() {}
  virtual void* Alloc(size_t size) = 0;
  virtual void Free(void* data) = 0;
};

class SystemAllocator : public Allocator {
 public:
  SystemAllocator() = default;
  ~SystemAllocator() override = default;
  void* Alloc(size_t size) override { return malloc(size); }
  void Free(void* data) override { free(data); }
};

class PartitionAllocator : public Allocator {
 public:
  PartitionAllocator()
      : alloc_(std::make_unique<PartitionAllocatorGeneric>()) {}
  ~PartitionAllocator() override = default;

  void Init() override { alloc_->init(); }
  void* Alloc(size_t size) override { return alloc_->root()->Alloc(size, ""); }
  void Free(void* data) override { return alloc_->root()->Free(data); }

 private:
  std::unique_ptr<PartitionAllocatorGeneric> alloc_;
};

class TestLoopThread : public PlatformThread::Delegate {
 public:
  explicit TestLoopThread(OnceCallback<float()> test_fn)
      : test_fn_(std::move(test_fn)) {
    CHECK(PlatformThread::Create(0, this, &thread_handle_));
  }

  float Run() {
    PlatformThread::Join(thread_handle_);
    return laps_per_second_;
  }

  void ThreadMain() override { laps_per_second_ = std::move(test_fn_).Run(); }

  OnceCallback<float()> test_fn_;
  PlatformThreadHandle thread_handle_;
  std::atomic<float> laps_per_second_;
};

void DisplayResults(const std::string& story_name,
                    float iterations_per_second) {
  auto reporter = SetUpReporter(story_name);
  reporter.AddResult(kMetricThroughput, iterations_per_second);
  reporter.AddResult(kMetricTimePerAllocation,
                     static_cast<size_t>(1e9 / iterations_per_second));
}

class MemoryAllocationPerfNode {
 public:
  MemoryAllocationPerfNode* GetNext() const { return next_; }
  void SetNext(MemoryAllocationPerfNode* p) { next_ = p; }
  static void FreeAll(MemoryAllocationPerfNode* first, Allocator* alloc) {
    MemoryAllocationPerfNode* cur = first;
    while (cur != nullptr) {
      MemoryAllocationPerfNode* next = cur->GetNext();
      alloc->Free(cur);
      cur = next;
    }
  }

 private:
  MemoryAllocationPerfNode* next_ = nullptr;
};

#if !defined(OS_ANDROID)
float SingleBucket(Allocator* allocator) {
  auto* first =
      reinterpret_cast<MemoryAllocationPerfNode*>(allocator->Alloc(40));

  LapTimer timer(kWarmupRuns, kTimeLimit, kTimeCheckInterval);
  MemoryAllocationPerfNode* cur = first;
  do {
    auto* next =
        reinterpret_cast<MemoryAllocationPerfNode*>(allocator->Alloc(40));
    CHECK_NE(next, nullptr);
    cur->SetNext(next);
    cur = next;
    timer.NextLap();
  } while (!timer.HasTimeLimitExpired());
  // next_ = nullptr only works if the class constructor is called (it's not
  // called in this case because then we can allocate arbitrary-length
  // payloads.)
  cur->SetNext(nullptr);

  MemoryAllocationPerfNode::FreeAll(first, allocator);
  return timer.LapsPerSecond();
}
#endif  // defined(OS_ANDROID)

float SingleBucketWithFree(Allocator* allocator) {
  // Allocate an initial element to make sure the bucket stays set up.
  void* elem = allocator->Alloc(40);

  LapTimer timer(kWarmupRuns, kTimeLimit, kTimeCheckInterval);
  do {
    void* cur = allocator->Alloc(40);
    CHECK_NE(cur, nullptr);
    allocator->Free(cur);
    timer.NextLap();
  } while (!timer.HasTimeLimitExpired());

  allocator->Free(elem);
  return timer.LapsPerSecond();
}

#if !defined(OS_ANDROID)
float MultiBucket(Allocator* allocator) {
  auto* first =
      reinterpret_cast<MemoryAllocationPerfNode*>(allocator->Alloc(40));
  MemoryAllocationPerfNode* cur = first;

  LapTimer timer(kWarmupRuns, kTimeLimit, kTimeCheckInterval);
  do {
    for (int i = 0; i < kMultiBucketRounds; i++) {
      auto* next = reinterpret_cast<MemoryAllocationPerfNode*>(allocator->Alloc(
          kMultiBucketMinimumSize + (i * kMultiBucketIncrement)));
      CHECK_NE(next, nullptr);
      cur->SetNext(next);
      cur = next;
    }
    timer.NextLap();
  } while (!timer.HasTimeLimitExpired());
  cur->SetNext(nullptr);

  MemoryAllocationPerfNode::FreeAll(first, allocator);

  return timer.LapsPerSecond() * kMultiBucketRounds;
}
#endif  // defined(OS_ANDROID)

float MultiBucketWithFree(Allocator* allocator) {
  std::vector<void*> elems;
  elems.reserve(kMultiBucketRounds);
  // Do an initial round of allocation to make sure that the buckets stay in
  // use (and aren't accidentally released back to the OS).
  for (int i = 0; i < kMultiBucketRounds; i++) {
    void* cur =
        allocator->Alloc(kMultiBucketMinimumSize + (i * kMultiBucketIncrement));
    CHECK_NE(cur, nullptr);
    elems.push_back(cur);
  }

  LapTimer timer(kWarmupRuns, kTimeLimit, kTimeCheckInterval);
  do {
    for (int i = 0; i < kMultiBucketRounds; i++) {
      void* cur = allocator->Alloc(kMultiBucketMinimumSize +
                                   (i * kMultiBucketIncrement));
      CHECK_NE(cur, nullptr);
      allocator->Free(cur);
    }
    timer.NextLap();
  } while (!timer.HasTimeLimitExpired());

    for (void* ptr : elems) {
      allocator->Free(ptr);
    }

    return timer.LapsPerSecond() * kMultiBucketRounds;
}

std::unique_ptr<Allocator> CreateAllocator(AllocatorType type) {
  if (type == AllocatorType::kSystem)
    return std::make_unique<SystemAllocator>();
  return std::make_unique<PartitionAllocator>();
}

void RunTest(int thread_count,
             AllocatorType alloc_type,
             float (*test_fn)(Allocator*),
             const char* story_base_name) {
  auto alloc = CreateAllocator(alloc_type);
  alloc->Init();

  std::vector<std::unique_ptr<TestLoopThread>> threads;
  for (int i = 0; i < thread_count; ++i) {
    threads.push_back(std::make_unique<TestLoopThread>(
        BindOnce(test_fn, Unretained(alloc.get()))));
  }

  uint64_t total_laps_per_second = 0;
  uint64_t min_laps_per_second = std::numeric_limits<uint64_t>::max();
  for (int i = 0; i < thread_count; ++i) {
    uint64_t laps_per_second = threads[i]->Run();
    min_laps_per_second = std::min(laps_per_second, min_laps_per_second);
    total_laps_per_second += laps_per_second;
  }

  std::string name = base::StringPrintf(
      "%s.%s_%s_%d", kMetricPrefixMemoryAllocation, story_base_name,
      alloc_type == AllocatorType::kSystem ? "System" : "PartitionAlloc",
      thread_count);

  DisplayResults(name + "_total", total_laps_per_second);
  DisplayResults(name + "_worst", min_laps_per_second);
}

class MemoryAllocationPerfTest
    : public testing::TestWithParam<std::tuple<int, AllocatorType>> {};

INSTANTIATE_TEST_SUITE_P(
    ,
    MemoryAllocationPerfTest,
    ::testing::Combine(::testing::Values(1, 2, 3, 4),
                       ::testing::Values(AllocatorType::kSystem,
                                         AllocatorType::kPartitionAlloc)));

// This test (and the other one below) allocates a large amount of memory, which
// can cause issues on Android.
#if !defined(OS_ANDROID)
TEST_P(MemoryAllocationPerfTest, SingleBucket) {
  auto params = GetParam();
  RunTest(std::get<0>(params), std::get<1>(params), SingleBucket,
          "SingleBucket");
}
#endif

TEST_P(MemoryAllocationPerfTest, SingleBucketWithFree) {
  auto params = GetParam();
  RunTest(std::get<0>(params), std::get<1>(params), SingleBucketWithFree,
          "SingleBucketWithFree");
}

#if !defined(OS_ANDROID)
TEST_P(MemoryAllocationPerfTest, MultiBucket) {
  auto params = GetParam();
  RunTest(std::get<0>(params), std::get<1>(params), MultiBucket, "MultiBucket");
}
#endif

TEST_P(MemoryAllocationPerfTest, MultiBucketWithFree) {
  auto params = GetParam();
  RunTest(std::get<0>(params), std::get<1>(params), MultiBucketWithFree,
          "MultiBucketWithFree");
}

}  // namespace

}  // namespace base
