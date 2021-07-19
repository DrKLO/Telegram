// Copyright 2019 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

#ifndef BASE_PROFILER_STACK_SAMPLING_PROFILER_TEST_UTIL_H_
#define BASE_PROFILER_STACK_SAMPLING_PROFILER_TEST_UTIL_H_

#include <memory>
#include <vector>

#include "base/callback.h"
#include "base/profiler/frame.h"
#include "base/profiler/sampling_profiler_thread_token.h"
#include "base/synchronization/waitable_event.h"
#include "base/threading/platform_thread.h"

namespace base {

class Unwinder;

// A thread to target for profiling that will run the supplied closure.
class TargetThread : public PlatformThread::Delegate {
 public:
  TargetThread(OnceClosure to_run);
  ~TargetThread() override;

  // PlatformThread::Delegate:
  void ThreadMain() override;

  SamplingProfilerThreadToken thread_token() const { return thread_token_; }

 private:
  SamplingProfilerThreadToken thread_token_ = {0};
  OnceClosure to_run_;

  DISALLOW_COPY_AND_ASSIGN(TargetThread);
};

// Addresses near the start and end of a function.
struct FunctionAddressRange {
  const void* start;
  const void* end;
};

// Represents a stack unwind scenario to be sampled by the
// StackSamplingProfiler.
class UnwindScenario {
 public:
  // A callback provided by the caller that sets up the unwind scenario, then
  // calls into the passed closure to wait for a sample to be taken. Returns the
  // address range of the function that sets up the unwind scenario. The passed
  // closure will be null when invoked solely to obtain the address range.
  using SetupFunction = RepeatingCallback<FunctionAddressRange(OnceClosure)>;

  // Events to coordinate the sampling.
  struct SampleEvents {
    WaitableEvent ready_for_sample;
    WaitableEvent sample_finished;
  };

  explicit UnwindScenario(const SetupFunction& setup_function);
  ~UnwindScenario();

  UnwindScenario(const UnwindScenario&) = delete;
  UnwindScenario& operator=(const UnwindScenario&) = delete;

  // The address range of the innermost function that waits for the sample.
  FunctionAddressRange GetWaitForSampleAddressRange() const;

  // The address range of the provided setup function.
  FunctionAddressRange GetSetupFunctionAddressRange() const;

  // The address range of the outer function that indirectly invokes the setup
  // function.
  FunctionAddressRange GetOuterFunctionAddressRange() const;

  // Executes the scenario.
  void Execute(SampleEvents* events);

 private:
  static FunctionAddressRange InvokeSetupFunction(
      const SetupFunction& setup_function,
      SampleEvents* events);

  static FunctionAddressRange WaitForSample(SampleEvents* events);

  const SetupFunction setup_function_;
};

// UnwindScenario setup function that calls into |wait_for_sample| without doing
// any special unwinding setup, to exercise the "normal" unwind scenario.
FunctionAddressRange CallWithPlainFunction(OnceClosure wait_for_sample);

// The callback to perform profiling on the provided thread.
using ProfileCallback = OnceCallback<void(SamplingProfilerThreadToken)>;

// Executes |profile_callback| while running |scenario| on the target
// thread. Performs all necessary target thread startup and shutdown work before
// and afterward.
void WithTargetThread(UnwindScenario* scenario,
                      ProfileCallback profile_callback);

using UnwinderFactory = OnceCallback<std::unique_ptr<Unwinder>()>;

// Returns the sample seen when taking one sample of |scenario|.
std::vector<Frame> SampleScenario(
    UnwindScenario* scenario,
    ModuleCache* module_cache,
    UnwinderFactory aux_unwinder_factory = UnwinderFactory());

// Formats a sample into a string that can be output for test diagnostics.
std::string FormatSampleForDiagnosticOutput(const std::vector<Frame>& sample);

// Expects that the stack contains the functions with the specified address
// ranges, in the specified order.
void ExpectStackContains(const std::vector<Frame>& stack,
                         const std::vector<FunctionAddressRange>& functions);

// Expects that the stack does not contain the functions with the specified
// address ranges.
void ExpectStackDoesNotContain(
    const std::vector<Frame>& stack,
    const std::vector<FunctionAddressRange>& functions);

}  // namespace base

#endif  // BASE_PROFILER_STACK_SAMPLING_PROFILER_TEST_UTIL_H_
