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

// The implementation of CycleClock::Frequency.
//
// NOTE: only i386 and x86_64 have been well tested.
// PPC, sparc, alpha, and ia64 are based on
//    http://peter.kuscsik.com/wordpress/?p=14
// with modifications by m3b.  See also
//    https://setisvn.ssl.berkeley.edu/svn/lib/fftw-3.0.1/kernel/cycle.h

#include "absl/base/internal/cycleclock.h"

#include <atomic>
#include <chrono>  // NOLINT(build/c++11)

#include "absl/base/internal/unscaledcycleclock.h"

namespace absl {
ABSL_NAMESPACE_BEGIN
namespace base_internal {

#if ABSL_USE_UNSCALED_CYCLECLOCK

namespace {

#ifdef NDEBUG
#ifdef ABSL_INTERNAL_UNSCALED_CYCLECLOCK_FREQUENCY_IS_CPU_FREQUENCY
// Not debug mode and the UnscaledCycleClock frequency is the CPU
// frequency.  Scale the CycleClock to prevent overflow if someone
// tries to represent the time as cycles since the Unix epoch.
static constexpr int32_t kShift = 1;
#else
// Not debug mode and the UnscaledCycleClock isn't operating at the
// raw CPU frequency. There is no need to do any scaling, so don't
// needlessly sacrifice precision.
static constexpr int32_t kShift = 0;
#endif
#else
// In debug mode use a different shift to discourage depending on a
// particular shift value.
static constexpr int32_t kShift = 2;
#endif

static constexpr double kFrequencyScale = 1.0 / (1 << kShift);
static std::atomic<CycleClockSourceFunc> cycle_clock_source;

CycleClockSourceFunc LoadCycleClockSource() {
  // Optimize for the common case (no callback) by first doing a relaxed load;
  // this is significantly faster on non-x86 platforms.
  if (cycle_clock_source.load(std::memory_order_relaxed) == nullptr) {
    return nullptr;
  }
  // This corresponds to the store(std::memory_order_release) in
  // CycleClockSource::Register, and makes sure that any updates made prior to
  // registering the callback are visible to this thread before the callback is
  // invoked.
  return cycle_clock_source.load(std::memory_order_acquire);
}

}  // namespace

int64_t CycleClock::Now() {
  auto fn = LoadCycleClockSource();
  if (fn == nullptr) {
    return base_internal::UnscaledCycleClock::Now() >> kShift;
  }
  return fn() >> kShift;
}

double CycleClock::Frequency() {
  return kFrequencyScale * base_internal::UnscaledCycleClock::Frequency();
}

void CycleClockSource::Register(CycleClockSourceFunc source) {
  // Corresponds to the load(std::memory_order_acquire) in LoadCycleClockSource.
  cycle_clock_source.store(source, std::memory_order_release);
}

#else

int64_t CycleClock::Now() {
  return std::chrono::duration_cast<std::chrono::nanoseconds>(
             std::chrono::steady_clock::now().time_since_epoch())
      .count();
}

double CycleClock::Frequency() {
  return 1e9;
}

#endif

}  // namespace base_internal
ABSL_NAMESPACE_END
}  // namespace absl
