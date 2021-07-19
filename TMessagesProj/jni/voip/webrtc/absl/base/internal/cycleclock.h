//
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
//

// -----------------------------------------------------------------------------
// File: cycleclock.h
// -----------------------------------------------------------------------------
//
// This header file defines a `CycleClock`, which yields the value and frequency
// of a cycle counter that increments at a rate that is approximately constant.
//
// NOTE:
//
// The cycle counter frequency is not necessarily related to the core clock
// frequency and should not be treated as such. That is, `CycleClock` cycles are
// not necessarily "CPU cycles" and code should not rely on that behavior, even
// if experimentally observed.
//
// An arbitrary offset may have been added to the counter at power on.
//
// On some platforms, the rate and offset of the counter may differ
// slightly when read from different CPUs of a multiprocessor. Usually,
// we try to ensure that the operating system adjusts values periodically
// so that values agree approximately.   If you need stronger guarantees,
// consider using alternate interfaces.
//
// The CPU is not required to maintain the ordering of a cycle counter read
// with respect to surrounding instructions.

#ifndef ABSL_BASE_INTERNAL_CYCLECLOCK_H_
#define ABSL_BASE_INTERNAL_CYCLECLOCK_H_

#include <cstdint>

#include "absl/base/config.h"

namespace absl {
ABSL_NAMESPACE_BEGIN
namespace base_internal {

// -----------------------------------------------------------------------------
// CycleClock
// -----------------------------------------------------------------------------
class CycleClock {
 public:
  // CycleClock::Now()
  //
  // Returns the value of a cycle counter that counts at a rate that is
  // approximately constant.
  static int64_t Now();

  // CycleClock::Frequency()
  //
  // Returns the amount by which `CycleClock::Now()` increases per second. Note
  // that this value may not necessarily match the core CPU clock frequency.
  static double Frequency();

 private:
  CycleClock() = delete;  // no instances
  CycleClock(const CycleClock&) = delete;
  CycleClock& operator=(const CycleClock&) = delete;
};

using CycleClockSourceFunc = int64_t (*)();

class CycleClockSource {
 private:
  // CycleClockSource::Register()
  //
  // Register a function that provides an alternate source for the unscaled CPU
  // cycle count value. The source function must be async signal safe, must not
  // call CycleClock::Now(), and must have a frequency that matches that of the
  // unscaled clock used by CycleClock. A nullptr value resets CycleClock to use
  // the default source.
  static void Register(CycleClockSourceFunc source);
};

}  // namespace base_internal
ABSL_NAMESPACE_END
}  // namespace absl

#endif  // ABSL_BASE_INTERNAL_CYCLECLOCK_H_
