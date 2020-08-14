// Copyright 2018 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

#include "base/observer_list.h"

#include <memory>

#include "base/logging.h"
#include "base/observer_list.h"
#include "base/strings/stringprintf.h"
#include "base/time/time.h"
#include "testing/gtest/include/gtest/gtest.h"
#include "testing/perf/perf_result_reporter.h"

// Ask the compiler not to use a register for this counter, in case it decides
// to do magic optimizations like |counter += kLaps|.
volatile int g_observer_list_perf_test_counter;

namespace base {

constexpr char kMetricPrefixObserverList[] = "ObserverList.";
constexpr char kMetricNotifyTimePerObserver[] = "notify_time_per_observer";

namespace {

perf_test::PerfResultReporter SetUpReporter(const std::string& story_name) {
  perf_test::PerfResultReporter reporter(kMetricPrefixObserverList, story_name);
  reporter.RegisterImportantMetric(kMetricNotifyTimePerObserver, "ns");
  return reporter;
}

}  // namespace

class ObserverInterface {
 public:
  ObserverInterface() {}
  virtual ~ObserverInterface() {}
  virtual void Observe() const { ++g_observer_list_perf_test_counter; }

 private:
  DISALLOW_COPY_AND_ASSIGN(ObserverInterface);
};

class UnsafeObserver : public ObserverInterface {};

class TestCheckedObserver : public CheckedObserver, public ObserverInterface {};

template <class ObserverType>
struct Pick {
  // The ObserverList type to use. Checked observers need to be in a checked
  // ObserverList.
  using ObserverListType = ObserverList<ObserverType>;
  static const char* GetName() { return "CheckedObserver"; }
};
template <>
struct Pick<UnsafeObserver> {
  using ObserverListType = ObserverList<ObserverInterface>::Unchecked;
  static const char* GetName() { return "UnsafeObserver"; }
};

template <class ObserverType>
class ObserverListPerfTest : public ::testing::Test {
 public:
  using ObserverListType = typename Pick<ObserverType>::ObserverListType;

  ObserverListPerfTest() {}

 private:
  DISALLOW_COPY_AND_ASSIGN(ObserverListPerfTest);
};

typedef ::testing::Types<UnsafeObserver, TestCheckedObserver> ObserverTypes;
TYPED_TEST_SUITE(ObserverListPerfTest, ObserverTypes);

// Performance test for base::ObserverList and Checked Observers.
TYPED_TEST(ObserverListPerfTest, NotifyPerformance) {
  constexpr int kMaxObservers = 128;
#if DCHECK_IS_ON()
  // The test takes about 100x longer in debug builds, mostly due to sequence
  // checker overheads when WeakPtr gets involved.
  constexpr int kLaps = 1000000;
#else
  constexpr int kLaps = 100000000;
#endif
  constexpr int kWarmupLaps = 100;
  std::vector<std::unique_ptr<TypeParam>> observers;

  for (int observer_count = 0; observer_count <= kMaxObservers;
       observer_count = observer_count ? observer_count * 2 : 1) {
    typename TestFixture::ObserverListType list;
    for (int i = 0; i < observer_count; ++i)
      observers.push_back(std::make_unique<TypeParam>());
    for (auto& o : observers)
      list.AddObserver(o.get());

    for (int i = 0; i < kWarmupLaps; ++i) {
      for (auto& o : list)
        o.Observe();
    }
    g_observer_list_perf_test_counter = 0;
    const int weighted_laps = kLaps / (observer_count + 1);

    TimeTicks start = TimeTicks::Now();
    for (int i = 0; i < weighted_laps; ++i) {
      for (auto& o : list)
        o.Observe();
    }
    TimeDelta duration = TimeTicks::Now() - start;

    observers.clear();

    EXPECT_EQ(observer_count * weighted_laps,
              g_observer_list_perf_test_counter);
    EXPECT_TRUE(observer_count == 0 || list.might_have_observers());

    std::string story_name =
        base::StringPrintf("%s_%d", Pick<TypeParam>::GetName(), observer_count);

    // A typical value is 3-20 nanoseconds per observe in Release, 1000-2000ns
    // in an optimized build with DCHECKs and 3000-6000ns in debug builds.
    auto reporter = SetUpReporter(story_name);
    reporter.AddResult(
        kMetricNotifyTimePerObserver,
        duration.InNanoseconds() /
            static_cast<double>(g_observer_list_perf_test_counter +
                                weighted_laps));
  }
}

}  // namespace base
