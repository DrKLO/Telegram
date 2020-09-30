// Copyright 2018 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

#ifndef BASE_SAMPLING_HEAP_PROFILER_POISSON_ALLOCATION_SAMPLER_H_
#define BASE_SAMPLING_HEAP_PROFILER_POISSON_ALLOCATION_SAMPLER_H_

#include <vector>

#include "base/base_export.h"
#include "base/compiler_specific.h"
#include "base/macros.h"
#include "base/sampling_heap_profiler/lock_free_address_hash_set.h"
#include "base/synchronization/lock.h"
#include "base/thread_annotations.h"

namespace base {

template <typename T>
class NoDestructor;

// This singleton class implements Poisson sampling of the incoming allocations
// stream. It hooks onto base::allocator and base::PartitionAlloc.
// An extra custom allocator can be hooked via SetHooksInstallCallback method.
// The only control parameter is sampling interval that controls average value
// of the sampling intervals. The actual intervals between samples are
// randomized using Poisson distribution to mitigate patterns in the allocation
// stream.
// Once accumulated allocation sizes fill up the current sample interval,
// a sample is generated and sent to the observers via |SampleAdded| call.
// When the corresponding memory that triggered the sample is freed observers
// get notified with |SampleRemoved| call.
//
class BASE_EXPORT PoissonAllocationSampler {
 public:
  enum AllocatorType : uint32_t { kMalloc, kPartitionAlloc, kBlinkGC };

  class SamplesObserver {
   public:
    virtual ~SamplesObserver() = default;
    virtual void SampleAdded(void* address,
                             size_t size,
                             size_t total,
                             AllocatorType type,
                             const char* context) = 0;
    virtual void SampleRemoved(void* address) = 0;
  };

  // The instance of this class makes sampler do not report samples generated
  // within the object scope for the current thread.
  // It allows observers to allocate/deallocate memory while holding a lock
  // without a chance to get into reentrancy problems.
  // The current implementation doesn't support ScopedMuteThreadSamples nesting.
  class BASE_EXPORT ScopedMuteThreadSamples {
   public:
    ScopedMuteThreadSamples();
    ~ScopedMuteThreadSamples();

    static bool IsMuted();
  };

  // Must be called early during the process initialization. It creates and
  // reserves a TLS slot.
  static void Init();

  // This is an entry point for plugging in an external allocator.
  // Profiler will invoke the provided callback upon initialization.
  // The callback should install hooks onto the corresponding memory allocator
  // and make them invoke PoissonAllocationSampler::RecordAlloc and
  // PoissonAllocationSampler::RecordFree upon corresponding allocation events.
  //
  // If the method is called after profiler is initialized, the callback
  // is invoked right away.
  static void SetHooksInstallCallback(void (*hooks_install_callback)());

  void AddSamplesObserver(SamplesObserver*);

  // Note: After an observer is removed it is still possible to receive
  // a notification to that observer. This is not a problem currently as
  // the only client of this interface is the base::SamplingHeapProfiler,
  // which is a singleton.
  // If there's a need for this functionality in the future, one might
  // want to put observers notification loop under a reader-writer lock.
  void RemoveSamplesObserver(SamplesObserver*);

  void SetSamplingInterval(size_t sampling_interval);
  void SuppressRandomnessForTest(bool suppress);

  static void RecordAlloc(void* address,
                          size_t,
                          AllocatorType,
                          const char* context);
  ALWAYS_INLINE static void RecordFree(void* address);

  static PoissonAllocationSampler* Get();

 private:
  PoissonAllocationSampler();
  ~PoissonAllocationSampler() = delete;

  static void InstallAllocatorHooksOnce();
  static bool InstallAllocatorHooks();
  static size_t GetNextSampleInterval(size_t base_interval);
  static LockFreeAddressHashSet& sampled_addresses_set();

  void DoRecordAlloc(intptr_t accumulated_bytes,
                     size_t size,
                     void* address,
                     AllocatorType type,
                     const char* context);
  void DoRecordFree(void* address);

  void BalanceAddressesHashSet();

  Lock mutex_;
  // The |observers_| list is guarded by |mutex_|, however a copy of it
  // is made before invoking the observers (to avoid performing expensive
  // operations under the lock) as such the SamplesObservers themselves need
  // to be thread-safe and support being invoked racily after
  // RemoveSamplesObserver().
  std::vector<SamplesObserver*> observers_ GUARDED_BY(mutex_);

  static PoissonAllocationSampler* instance_;

  friend class NoDestructor<PoissonAllocationSampler>;
  friend class SamplingHeapProfilerTest;
  friend class ScopedMuteThreadSamples;

  DISALLOW_COPY_AND_ASSIGN(PoissonAllocationSampler);
};

// static
ALWAYS_INLINE void PoissonAllocationSampler::RecordFree(void* address) {
  if (UNLIKELY(address == nullptr))
    return;
  if (UNLIKELY(sampled_addresses_set().Contains(address)))
    instance_->DoRecordFree(address);
}

}  // namespace base

#endif  // BASE_SAMPLING_HEAP_PROFILER_POISSON_ALLOCATION_SAMPLER_H_
