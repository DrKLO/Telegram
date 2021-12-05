// Copyright 2018 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

#ifndef BASE_SAMPLING_HEAP_PROFILER_SAMPLING_HEAP_PROFILER_H_
#define BASE_SAMPLING_HEAP_PROFILER_SAMPLING_HEAP_PROFILER_H_

#include <atomic>
#include <unordered_map>
#include <unordered_set>
#include <vector>

#include "base/base_export.h"
#include "base/macros.h"
#include "base/sampling_heap_profiler/poisson_allocation_sampler.h"
#include "base/synchronization/lock.h"
#include "base/thread_annotations.h"
#include "base/threading/thread_id_name_manager.h"

namespace base {

template <typename T>
class NoDestructor;

// The class implements sampling profiling of native memory heap.
// It uses PoissonAllocationSampler to aggregate the heap allocations and
// record samples.
// The recorded samples can then be retrieved using GetSamples method.
class BASE_EXPORT SamplingHeapProfiler
    : private PoissonAllocationSampler::SamplesObserver,
      public base::ThreadIdNameManager::Observer {
 public:
  class BASE_EXPORT Sample {
   public:
    Sample(const Sample&);
    ~Sample();

    // Allocation size.
    size_t size;
    // Total size attributed to the sample.
    size_t total;
    // Type of the allocator.
    PoissonAllocationSampler::AllocatorType allocator;
    // Context as provided by the allocation hook.
    const char* context = nullptr;
    // Name of the thread that made the sampled allocation.
    const char* thread_name = nullptr;
    // Call stack of PC addresses responsible for the allocation.
    // If AllocationContextTracker::capture_mode() is in PSEUDO or MIXED modes
    // the frame pointers may point to the name strings instead of PCs. In this
    // cases all the strings pointers are also reported with |GetStrings| method
    // of the |SamplingHeapProfiler|. This way they can be distinguished from
    // the PC pointers.
    std::vector<void*> stack;

   private:
    friend class SamplingHeapProfiler;

    Sample(size_t size, size_t total, uint32_t ordinal);

    uint32_t ordinal;
  };

  // Starts collecting allocation samples. Returns the current profile_id.
  // This value can then be passed to |GetSamples| to retrieve only samples
  // recorded since the corresponding |Start| invocation.
  uint32_t Start();

  // Stops recording allocation samples.
  void Stop();

  // Sets sampling interval in bytes.
  void SetSamplingInterval(size_t sampling_interval);

  // Enables recording thread name that made the sampled allocation.
  void SetRecordThreadNames(bool value);

  // Returns the current thread name.
  static const char* CachedThreadName();

  // Returns current samples recorded for the profile session.
  // If |profile_id| is set to the value returned by the |Start| method,
  // it returns only the samples recorded after the corresponding |Start|
  // invocation. To retrieve all the collected samples |profile_id| must be
  // set to 0.
  std::vector<Sample> GetSamples(uint32_t profile_id);

  // List of strings used in the profile call stacks.
  std::vector<const char*> GetStrings();

  // Captures up to |max_entries| stack frames using the buffer pointed by
  // |frames|. Puts the number of captured frames into the |count| output
  // parameters. Returns the pointer to the topmost frame.
  static void** CaptureStackTrace(void** frames,
                                  size_t max_entries,
                                  size_t* count);

  static void Init();
  static SamplingHeapProfiler* Get();

  // ThreadIdNameManager::Observer implementation:
  void OnThreadNameChanged(const char* name) override;

 private:
  SamplingHeapProfiler();
  ~SamplingHeapProfiler() override;

  // PoissonAllocationSampler::SamplesObserver
  void SampleAdded(void* address,
                   size_t size,
                   size_t total,
                   PoissonAllocationSampler::AllocatorType type,
                   const char* context) override;
  void SampleRemoved(void* address) override;

  void CaptureMixedStack(const char* context, Sample* sample);
  void CaptureNativeStack(const char* context, Sample* sample);
  const char* RecordString(const char* string) EXCLUSIVE_LOCKS_REQUIRED(mutex_);

  // Mutex to access |samples_| and |strings_|.
  Lock mutex_;

  // Samples of the currently live allocations.
  std::unordered_map<void*, Sample> samples_ GUARDED_BY(mutex_);

  // When CaptureMode::PSEUDO_STACK or CaptureMode::MIXED_STACK is enabled
  // the call stack contents of samples may contain strings besides
  // PC addresses.
  // In this case each string pointer is also added to the |strings_| set.
  // The set does only contain pointers to static strings that are never
  // deleted.
  std::unordered_set<const char*> strings_ GUARDED_BY(mutex_);

  // Mutex to make |running_sessions_| and Add/Remove samples observer access
  // atomic.
  Lock start_stop_mutex_;

  // Number of the running sessions.
  int running_sessions_ = 0;

  // Last sample ordinal used to mark samples recorded during single session.
  std::atomic<uint32_t> last_sample_ordinal_{1};

  // Whether it should record thread names.
  std::atomic<bool> record_thread_names_{false};

  friend class NoDestructor<SamplingHeapProfiler>;
  friend class SamplingHeapProfilerTest;

  DISALLOW_COPY_AND_ASSIGN(SamplingHeapProfiler);
};

}  // namespace base

#endif  // BASE_SAMPLING_HEAP_PROFILER_SAMPLING_HEAP_PROFILER_H_
