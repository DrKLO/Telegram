// Copyright 2015 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

#ifndef BASE_PROFILER_STACK_SAMPLING_PROFILER_H_
#define BASE_PROFILER_STACK_SAMPLING_PROFILER_H_

#include <memory>
#include <vector>

#include "base/base_export.h"
#include "base/macros.h"
#include "base/optional.h"
#include "base/profiler/profile_builder.h"
#include "base/profiler/sampling_profiler_thread_token.h"
#include "base/synchronization/waitable_event.h"
#include "base/threading/platform_thread.h"
#include "base/time/time.h"

namespace base {

class Unwinder;
class StackSampler;
class StackSamplerTestDelegate;

// StackSamplingProfiler periodically stops a thread to sample its stack, for
// the purpose of collecting information about which code paths are
// executing. This information is used in aggregate by UMA to identify hot
// and/or janky code paths.
//
// Sample StackSamplingProfiler usage:
//
//   // Create and customize params as desired.
//   base::StackStackSamplingProfiler::SamplingParams params;
//
//   // To process the profiles, use a custom ProfileBuilder subclass:
//   class SubProfileBuilder : public base::ProfileBuilder {...}
//
//   // Then create the profiler:
//   base::StackSamplingProfiler profiler(base::PlatformThread::CurrentId(),
//       params, std::make_unique<SubProfileBuilder>(...));
//
//   // On Android the |sampler| is not implemented in base. So, client can pass
//   // in |sampler| to use while profiling.
//   base::StackSamplingProfiler profiler(base::PlatformThread::CurrentId(),
//       params, std::make_unique<SubProfileBuilder>(...), <optional> sampler);
//
//   // Then start the profiling.
//   profiler.Start();
//   // ... work being done on the target thread here ...
//   // Optionally stop collection before complete per params.
//   profiler.Stop();
//
// The default SamplingParams causes stacks to be recorded in a single profile
// at a 10Hz interval for a total of 30 seconds. All of these parameters may be
// altered as desired.
//
// When a call stack profile is complete, or the profiler is stopped,
// ProfileBuilder's OnProfileCompleted function is called from a thread created
// by the profiler.
class BASE_EXPORT StackSamplingProfiler {
 public:
  // Represents parameters that configure the sampling.
  struct BASE_EXPORT SamplingParams {
    // Time to delay before first samples are taken.
    TimeDelta initial_delay = TimeDelta::FromMilliseconds(0);

    // Number of samples to record per profile.
    int samples_per_profile = 300;

    // Interval between samples during a sampling profile. This is the desired
    // duration from the start of one sample to the start of the next sample.
    TimeDelta sampling_interval = TimeDelta::FromMilliseconds(100);

    // When true, keeps the average sampling interval = |sampling_interval|,
    // irrespective of how long each sample takes. If a sample takes too long,
    // keeping the interval constant will lock out the sampled thread. When
    // false, sample is created with an interval of |sampling_interval|,
    // excluding the time taken by a sample. The metrics collected will not be
    // accurate, since sampling could take arbitrary amount of time, but makes
    // sure that the sampled thread gets at least the interval amount of time to
    // run between samples.
    bool keep_consistent_sampling_interval = true;
  };

  // Creates a profiler for the specified thread. An optional |test_delegate|
  // can be supplied by tests.
  //
  // The caller must ensure that this object gets destroyed before the thread
  // exits.
  StackSamplingProfiler(SamplingProfilerThreadToken thread_token,
                        const SamplingParams& params,
                        std::unique_ptr<ProfileBuilder> profile_builder,
                        StackSamplerTestDelegate* test_delegate = nullptr);

  // Same as above function, with custom |sampler| implementation. The sampler
  // on Android is not implemented in base.
  StackSamplingProfiler(const SamplingParams& params,
                        std::unique_ptr<ProfileBuilder> profile_builder,
                        std::unique_ptr<StackSampler> sampler);

  // Stops any profiling currently taking place before destroying the profiler.
  // This will block until profile_builder_'s OnProfileCompleted function has
  // executed if profiling has started but not already finished.
  ~StackSamplingProfiler();

  // Initializes the profiler and starts sampling. Might block on a
  // WaitableEvent if this StackSamplingProfiler was previously started and
  // recently stopped, while the previous profiling phase winds down.
  void Start();

  // Stops the profiler and any ongoing sampling. This method will return
  // immediately with the profile_builder_'s OnProfileCompleted function being
  // run asynchronously. At most one more stack sample will be taken after this
  // method returns. Calling this function is optional; if not invoked profiling
  // terminates when all the profiling samples specified in the SamplingParams
  // are completed or the profiler object is destroyed, whichever occurs first.
  void Stop();

  // Adds an auxiliary unwinder to handle additional, non-native-code unwind
  // scenarios.
  void AddAuxUnwinder(std::unique_ptr<Unwinder> unwinder);

  // Test peer class. These functions are purely for internal testing of
  // StackSamplingProfiler; DO NOT USE within tests outside of this directory.
  // The functions are static because they interact with the sampling thread, a
  // singleton used by all StackSamplingProfiler objects.  The functions can
  // only be called by the same thread that started the sampling.
  class BASE_EXPORT TestPeer {
   public:
    // Resets the internal state to that of a fresh start. This is necessary
    // so that tests don't inherit state from previous tests.
    static void Reset();

    // Returns whether the sampling thread is currently running or not.
    static bool IsSamplingThreadRunning();

    // Disables inherent idle-shutdown behavior.
    static void DisableIdleShutdown();

    // Initiates an idle shutdown task, as though the idle timer had expired,
    // causing the thread to exit. There is no "idle" check so this must be
    // called only when all sampling tasks have completed. This blocks until
    // the task has been executed, though the actual stopping of the thread
    // still happens asynchronously. Watch IsSamplingThreadRunning() to know
    // when the thread has exited. If |simulate_intervening_start| is true then
    // this method will make it appear to the shutdown task that a new profiler
    // was started between when the idle-shutdown was initiated and when it
    // runs.
    static void PerformSamplingThreadIdleShutdown(
        bool simulate_intervening_start);
  };

 private:
  // SamplingThread is a separate thread used to suspend and sample stacks from
  // the target thread.
  class SamplingThread;

  // Friend the global function from sample_metadata.cc so that it can call into
  // the function below.
  friend void ApplyMetadataToPastSamplesImpl(TimeTicks period_start,
                                             TimeTicks period_end,
                                             int64_t name_hash,
                                             Optional<int64_t> key,
                                             int64_t value);

  // Apply metadata to already recorded samples. See the
  // ApplyMetadataToPastSamples() docs in sample_metadata.h.
  static void ApplyMetadataToPastSamples(TimeTicks period_start,
                                         TimeTicks period_end,
                                         int64_t name_hash,
                                         Optional<int64_t> key,
                                         int64_t value);

  // The thread whose stack will be sampled.
  SamplingProfilerThreadToken thread_token_;

  const SamplingParams params_;

  // Receives the sampling data and builds a profile. The ownership of this
  // object will be transferred to the sampling thread when thread sampling
  // starts.
  std::unique_ptr<ProfileBuilder> profile_builder_;

  // Stack sampler which stops the thread and collects stack frames. The
  // ownership of this object will be transferred to the sampling thread when
  // thread sampling starts.
  std::unique_ptr<StackSampler> sampler_;

  // If an AuxUnwinder is added before Start() it will be saved here until it
  // can be passed to the sampling thread when thread sampling starts.
  std::unique_ptr<Unwinder> pending_aux_unwinder_;

  // This starts "signaled", is reset when sampling begins, and is signaled
  // when that sampling is complete and the profile_builder_'s
  // OnProfileCompleted function has executed.
  WaitableEvent profiling_inactive_;

  // An ID uniquely identifying this profiler to the sampling thread. This
  // will be an internal "null" value when no collection has been started.
  int profiler_id_;

  DISALLOW_COPY_AND_ASSIGN(StackSamplingProfiler);
};

}  // namespace base

#endif  // BASE_PROFILER_STACK_SAMPLING_PROFILER_H_
