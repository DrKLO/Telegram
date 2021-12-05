// Copyright 2017 The Abseil Authors.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      https://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

#include <cstdint>
#include <mutex>  // NOLINT(build/c++11)
#include <vector>

#include "absl/base/internal/cycleclock.h"
#include "absl/base/internal/spinlock.h"
#include "absl/synchronization/blocking_counter.h"
#include "absl/synchronization/internal/thread_pool.h"
#include "absl/synchronization/mutex.h"
#include "benchmark/benchmark.h"

namespace {

void BM_Mutex(benchmark::State& state) {
  static absl::Mutex* mu = new absl::Mutex;
  for (auto _ : state) {
    absl::MutexLock lock(mu);
  }
}
BENCHMARK(BM_Mutex)->UseRealTime()->Threads(1)->ThreadPerCpu();

static void DelayNs(int64_t ns, int* data) {
  int64_t end = absl::base_internal::CycleClock::Now() +
                ns * absl::base_internal::CycleClock::Frequency() / 1e9;
  while (absl::base_internal::CycleClock::Now() < end) {
    ++(*data);
    benchmark::DoNotOptimize(*data);
  }
}

template <typename MutexType>
class RaiiLocker {
 public:
  explicit RaiiLocker(MutexType* mu) : mu_(mu) { mu_->Lock(); }
  ~RaiiLocker() { mu_->Unlock(); }
 private:
  MutexType* mu_;
};

template <>
class RaiiLocker<std::mutex> {
 public:
  explicit RaiiLocker(std::mutex* mu) : mu_(mu) { mu_->lock(); }
  ~RaiiLocker() { mu_->unlock(); }
 private:
  std::mutex* mu_;
};

template <typename MutexType>
void BM_Contended(benchmark::State& state) {
  struct Shared {
    MutexType mu;
    int data = 0;
  };
  static auto* shared = new Shared;
  int local = 0;
  for (auto _ : state) {
    // Here we model both local work outside of the critical section as well as
    // some work inside of the critical section. The idea is to capture some
    // more or less realisitic contention levels.
    // If contention is too low, the benchmark won't measure anything useful.
    // If contention is unrealistically high, the benchmark will favor
    // bad mutex implementations that block and otherwise distract threads
    // from the mutex and shared state for as much as possible.
    // To achieve this amount of local work is multiplied by number of threads
    // to keep ratio between local work and critical section approximately
    // equal regardless of number of threads.
    DelayNs(100 * state.threads, &local);
    RaiiLocker<MutexType> locker(&shared->mu);
    DelayNs(state.range(0), &shared->data);
  }
}

BENCHMARK_TEMPLATE(BM_Contended, absl::Mutex)
    ->UseRealTime()
    // ThreadPerCpu poorly handles non-power-of-two CPU counts.
    ->Threads(1)
    ->Threads(2)
    ->Threads(4)
    ->Threads(6)
    ->Threads(8)
    ->Threads(12)
    ->Threads(16)
    ->Threads(24)
    ->Threads(32)
    ->Threads(48)
    ->Threads(64)
    ->Threads(96)
    ->Threads(128)
    ->Threads(192)
    ->Threads(256)
    // Some empirically chosen amounts of work in critical section.
    // 1 is low contention, 200 is high contention and few values in between.
    ->Arg(1)
    ->Arg(20)
    ->Arg(50)
    ->Arg(200);

BENCHMARK_TEMPLATE(BM_Contended, absl::base_internal::SpinLock)
    ->UseRealTime()
    // ThreadPerCpu poorly handles non-power-of-two CPU counts.
    ->Threads(1)
    ->Threads(2)
    ->Threads(4)
    ->Threads(6)
    ->Threads(8)
    ->Threads(12)
    ->Threads(16)
    ->Threads(24)
    ->Threads(32)
    ->Threads(48)
    ->Threads(64)
    ->Threads(96)
    ->Threads(128)
    ->Threads(192)
    ->Threads(256)
    // Some empirically chosen amounts of work in critical section.
    // 1 is low contention, 200 is high contention and few values in between.
    ->Arg(1)
    ->Arg(20)
    ->Arg(50)
    ->Arg(200);

BENCHMARK_TEMPLATE(BM_Contended, std::mutex)
    ->UseRealTime()
    // ThreadPerCpu poorly handles non-power-of-two CPU counts.
    ->Threads(1)
    ->Threads(2)
    ->Threads(4)
    ->Threads(6)
    ->Threads(8)
    ->Threads(12)
    ->Threads(16)
    ->Threads(24)
    ->Threads(32)
    ->Threads(48)
    ->Threads(64)
    ->Threads(96)
    ->Threads(128)
    ->Threads(192)
    ->Threads(256)
    // Some empirically chosen amounts of work in critical section.
    // 1 is low contention, 200 is high contention and few values in between.
    ->Arg(1)
    ->Arg(20)
    ->Arg(50)
    ->Arg(200);

// Measure the overhead of conditions on mutex release (when they must be
// evaluated).  Mutex has (some) support for equivalence classes allowing
// Conditions with the same function/argument to potentially not be multiply
// evaluated.
//
// num_classes==0 is used for the special case of every waiter being distinct.
void BM_ConditionWaiters(benchmark::State& state) {
  int num_classes = state.range(0);
  int num_waiters = state.range(1);

  struct Helper {
    static void Waiter(absl::BlockingCounter* init, absl::Mutex* m, int* p) {
      init->DecrementCount();
      m->LockWhen(absl::Condition(
          static_cast<bool (*)(int*)>([](int* v) { return *v == 0; }), p));
      m->Unlock();
    }
  };

  if (num_classes == 0) {
    // No equivalence classes.
    num_classes = num_waiters;
  }

  absl::BlockingCounter init(num_waiters);
  absl::Mutex mu;
  std::vector<int> equivalence_classes(num_classes, 1);

  // Must be declared last to be destroyed first.
  absl::synchronization_internal::ThreadPool pool(num_waiters);

  for (int i = 0; i < num_waiters; i++) {
    // Mutex considers Conditions with the same function and argument
    // to be equivalent.
    pool.Schedule([&, i] {
      Helper::Waiter(&init, &mu, &equivalence_classes[i % num_classes]);
    });
  }
  init.Wait();

  for (auto _ : state) {
    mu.Lock();
    mu.Unlock();  // Each unlock requires Condition evaluation for our waiters.
  }

  mu.Lock();
  for (int i = 0; i < num_classes; i++) {
    equivalence_classes[i] = 0;
  }
  mu.Unlock();
}

// Some configurations have higher thread limits than others.
#if defined(__linux__) && !defined(THREAD_SANITIZER)
constexpr int kMaxConditionWaiters = 8192;
#else
constexpr int kMaxConditionWaiters = 1024;
#endif
BENCHMARK(BM_ConditionWaiters)->RangePair(0, 2, 1, kMaxConditionWaiters);

}  // namespace
