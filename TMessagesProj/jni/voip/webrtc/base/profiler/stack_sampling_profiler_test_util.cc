// Copyright 2019 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

#include "base/profiler/stack_sampling_profiler_test_util.h"

#include <utility>

#include "base/callback.h"
#include "base/compiler_specific.h"
#include "base/location.h"
#include "base/profiler/stack_sampling_profiler.h"
#include "base/profiler/unwinder.h"
#include "base/strings/stringprintf.h"
#include "base/test/bind_test_util.h"
#include "testing/gtest/include/gtest/gtest.h"

namespace base {

namespace {

// A profile builder for test use that expects to receive exactly one sample.
class TestProfileBuilder : public ProfileBuilder {
 public:
  // The callback is passed the last sample recorded.
  using CompletedCallback = OnceCallback<void(std::vector<Frame>)>;

  TestProfileBuilder(ModuleCache* module_cache, CompletedCallback callback)
      : module_cache_(module_cache), callback_(std::move(callback)) {}

  ~TestProfileBuilder() override = default;

  TestProfileBuilder(const TestProfileBuilder&) = delete;
  TestProfileBuilder& operator=(const TestProfileBuilder&) = delete;

  // ProfileBuilder:
  ModuleCache* GetModuleCache() override { return module_cache_; }
  void RecordMetadata(MetadataProvider* metadata_provider) override {}

  void OnSampleCompleted(std::vector<Frame> sample,
                         TimeTicks sample_timestamp) override {
    EXPECT_TRUE(sample_.empty());
    sample_ = std::move(sample);
  }

  void OnProfileCompleted(TimeDelta profile_duration,
                          TimeDelta sampling_period) override {
    EXPECT_FALSE(sample_.empty());
    std::move(callback_).Run(std::move(sample_));
  }

 private:
  ModuleCache* const module_cache_;
  CompletedCallback callback_;
  std::vector<Frame> sample_;
};

}  // namespace

TargetThread::TargetThread(OnceClosure to_run) : to_run_(std::move(to_run)) {}

TargetThread::~TargetThread() = default;

void TargetThread::ThreadMain() {
  thread_token_ = GetSamplingProfilerCurrentThreadToken();
  std::move(to_run_).Run();
}

UnwindScenario::UnwindScenario(const SetupFunction& setup_function)
    : setup_function_(setup_function) {}

UnwindScenario::~UnwindScenario() = default;

FunctionAddressRange UnwindScenario::GetWaitForSampleAddressRange() const {
  return WaitForSample(nullptr);
}

FunctionAddressRange UnwindScenario::GetSetupFunctionAddressRange() const {
  return setup_function_.Run(OnceClosure());
}

FunctionAddressRange UnwindScenario::GetOuterFunctionAddressRange() const {
  return InvokeSetupFunction(SetupFunction(), nullptr);
}

void UnwindScenario::Execute(SampleEvents* events) {
  InvokeSetupFunction(setup_function_, events);
}

// static
// Disable inlining for this function so that it gets its own stack frame.
NOINLINE FunctionAddressRange
UnwindScenario::InvokeSetupFunction(const SetupFunction& setup_function,
                                    SampleEvents* events) {
  const void* start_program_counter = GetProgramCounter();

  if (!setup_function.is_null()) {
    const auto wait_for_sample_closure =
        BindLambdaForTesting([&]() { UnwindScenario::WaitForSample(events); });
    setup_function.Run(wait_for_sample_closure);
  }

  // Volatile to prevent a tail call to GetProgramCounter().
  const void* volatile end_program_counter = GetProgramCounter();
  return {start_program_counter, end_program_counter};
}

// static
// Disable inlining for this function so that it gets its own stack frame.
NOINLINE FunctionAddressRange
UnwindScenario::WaitForSample(SampleEvents* events) {
  const void* start_program_counter = GetProgramCounter();

  if (events) {
    events->ready_for_sample.Signal();
    events->sample_finished.Wait();
  }

  // Volatile to prevent a tail call to GetProgramCounter().
  const void* volatile end_program_counter = GetProgramCounter();
  return {start_program_counter, end_program_counter};
}

// Disable inlining for this function so that it gets its own stack frame.
NOINLINE FunctionAddressRange
CallWithPlainFunction(OnceClosure wait_for_sample) {
  const void* start_program_counter = GetProgramCounter();

  if (!wait_for_sample.is_null())
    std::move(wait_for_sample).Run();

  // Volatile to prevent a tail call to GetProgramCounter().
  const void* volatile end_program_counter = GetProgramCounter();
  return {start_program_counter, end_program_counter};
}

void WithTargetThread(UnwindScenario* scenario,
                      ProfileCallback profile_callback) {
  UnwindScenario::SampleEvents events;
  TargetThread target_thread(
      BindLambdaForTesting([&]() { scenario->Execute(&events); }));

  PlatformThreadHandle target_thread_handle;
  EXPECT_TRUE(PlatformThread::Create(0, &target_thread, &target_thread_handle));

  events.ready_for_sample.Wait();

  std::move(profile_callback).Run(target_thread.thread_token());

  events.sample_finished.Signal();

  PlatformThread::Join(target_thread_handle);
}

std::vector<Frame> SampleScenario(UnwindScenario* scenario,
                                  ModuleCache* module_cache,
                                  UnwinderFactory aux_unwinder_factory) {
  StackSamplingProfiler::SamplingParams params;
  params.sampling_interval = TimeDelta::FromMilliseconds(0);
  params.samples_per_profile = 1;

  std::vector<Frame> sample;
  WithTargetThread(
      scenario,
      BindLambdaForTesting(
          [&](SamplingProfilerThreadToken target_thread_token) {
            WaitableEvent sampling_thread_completed(
                WaitableEvent::ResetPolicy::MANUAL,
                WaitableEvent::InitialState::NOT_SIGNALED);
            StackSamplingProfiler profiler(
                target_thread_token, params,
                std::make_unique<TestProfileBuilder>(
                    module_cache,
                    BindLambdaForTesting([&sample, &sampling_thread_completed](
                                             std::vector<Frame> result_sample) {
                      sample = std::move(result_sample);
                      sampling_thread_completed.Signal();
                    })));
            if (aux_unwinder_factory)
              profiler.AddAuxUnwinder(std::move(aux_unwinder_factory).Run());
            profiler.Start();
            sampling_thread_completed.Wait();
          }));

  return sample;
}

std::string FormatSampleForDiagnosticOutput(const std::vector<Frame>& sample) {
  std::string output;
  for (const auto& frame : sample) {
    output += StringPrintf(
        "0x%p %s\n", reinterpret_cast<const void*>(frame.instruction_pointer),
        frame.module ? frame.module->GetDebugBasename().AsUTF8Unsafe().c_str()
                     : "null module");
  }
  return output;
}

void ExpectStackContains(const std::vector<Frame>& stack,
                         const std::vector<FunctionAddressRange>& functions) {
  auto frame_it = stack.begin();
  auto function_it = functions.begin();
  for (; frame_it != stack.end() && function_it != functions.end();
       ++frame_it) {
    if (frame_it->instruction_pointer >=
            reinterpret_cast<uintptr_t>(function_it->start) &&
        frame_it->instruction_pointer <=
            reinterpret_cast<uintptr_t>(function_it->end)) {
      ++function_it;
    }
  }

  EXPECT_EQ(function_it, functions.end())
      << "Function in position " << function_it - functions.begin() << " at "
      << function_it->start << " was not found in stack "
      << "(or did not appear in the expected order):\n"
      << FormatSampleForDiagnosticOutput(stack);
}

void ExpectStackDoesNotContain(
    const std::vector<Frame>& stack,
    const std::vector<FunctionAddressRange>& functions) {
  struct FunctionAddressRangeCompare {
    bool operator()(const FunctionAddressRange& a,
                    const FunctionAddressRange& b) const {
      return std::make_pair(a.start, a.end) < std::make_pair(b.start, b.end);
    }
  };

  std::set<FunctionAddressRange, FunctionAddressRangeCompare> seen_functions;
  for (const auto& frame : stack) {
    for (const auto function : functions) {
      if (frame.instruction_pointer >=
              reinterpret_cast<uintptr_t>(function.start) &&
          frame.instruction_pointer <=
              reinterpret_cast<uintptr_t>(function.end)) {
        seen_functions.insert(function);
      }
    }
  }

  for (const auto function : seen_functions) {
    ADD_FAILURE() << "Function at " << function.start
                  << " was unexpectedly found in stack:\n"
                  << FormatSampleForDiagnosticOutput(stack);
  }
}

}  // namespace base
