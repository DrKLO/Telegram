// Copyright 2018 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

#include "base/sampling_heap_profiler/poisson_allocation_sampler.h"

#include <algorithm>
#include <atomic>
#include <cmath>
#include <memory>
#include <utility>

#include "base/allocator/allocator_shim.h"
#include "base/allocator/buildflags.h"
#include "base/allocator/partition_allocator/partition_alloc.h"
#include "base/macros.h"
#include "base/no_destructor.h"
#include "base/partition_alloc_buildflags.h"
#include "base/rand_util.h"
#include "build/build_config.h"

#if defined(OS_MACOSX) || defined(OS_ANDROID)
#include <pthread.h>
#endif

namespace base {

using allocator::AllocatorDispatch;

namespace {

#if defined(OS_MACOSX) || defined(OS_ANDROID)

// The macOS implementation of libmalloc sometimes calls malloc recursively,
// delegating allocations between zones. That causes our hooks being called
// twice. The scoped guard allows us to detect that.
//
// Besides that the implementations of thread_local on macOS and Android
// seem to allocate memory lazily on the first access to thread_local variables.
// Make use of pthread TLS instead of C++ thread_local there.
class ReentryGuard {
 public:
  ReentryGuard() : allowed_(!pthread_getspecific(entered_key_)) {
    pthread_setspecific(entered_key_, reinterpret_cast<void*>(true));
  }

  ~ReentryGuard() {
    if (LIKELY(allowed_))
      pthread_setspecific(entered_key_, nullptr);
  }

  operator bool() { return allowed_; }

  static void Init() {
    int error = pthread_key_create(&entered_key_, nullptr);
    CHECK(!error);
  }

 private:
  bool allowed_;
  static pthread_key_t entered_key_;
};

pthread_key_t ReentryGuard::entered_key_;

#else

class ReentryGuard {
 public:
  operator bool() { return true; }
  static void Init() {}
};

#endif

const size_t kDefaultSamplingIntervalBytes = 128 * 1024;

// Notes on TLS usage:
//
// * There's no safe way to use TLS in malloc() as both C++ thread_local and
//   pthread do not pose any guarantees on whether they allocate or not.
// * We think that we can safely use thread_local w/o re-entrancy guard because
//   the compiler will use "tls static access model" for static builds of
//   Chrome [https://www.uclibc.org/docs/tls.pdf].
//   But there's no guarantee that this will stay true, and in practice
//   it seems to have problems on macOS/Android. These platforms do allocate
//   on the very first access to a thread_local on each thread.
// * Directly using/warming-up platform TLS seems to work on all platforms,
//   but is also not guaranteed to stay true. We make use of it for reentrancy
//   guards on macOS/Android.
// * We cannot use Windows Tls[GS]etValue API as it modifies the result of
//   GetLastError.
//
// Android thread_local seems to be using __emutls_get_address from libgcc:
// https://github.com/gcc-mirror/gcc/blob/master/libgcc/emutls.c
// macOS version is based on _tlv_get_addr from dyld:
// https://opensource.apple.com/source/dyld/dyld-635.2/src/threadLocalHelpers.s.auto.html

// The guard protects against reentering on platforms other the macOS and
// Android.
thread_local bool g_internal_reentry_guard;

// Accumulated bytes towards sample thread local key.
thread_local intptr_t g_accumulated_bytes_tls;

// Used as a workaround to avoid bias from muted samples. See
// ScopedMuteThreadSamples for more details.
thread_local intptr_t g_accumulated_bytes_tls_snapshot;
const intptr_t kAccumulatedBytesOffset = 1 << 29;

// A boolean used to distinguish first allocation on a thread:
//   false - first allocation on the thread;
//   true  - otherwise.
// Since g_accumulated_bytes_tls is initialized with zero the very first
// allocation on a thread would always trigger the sample, thus skewing the
// profile towards such allocations. To mitigate that we use the flag to
// ensure the first allocation is properly accounted.
thread_local bool g_sampling_interval_initialized_tls;

// Controls if sample intervals should not be randomized. Used for testing.
bool g_deterministic;

// A positive value if profiling is running, otherwise it's zero.
std::atomic_bool g_running;

// Pointer to the current |LockFreeAddressHashSet|.
std::atomic<LockFreeAddressHashSet*> g_sampled_addresses_set;

// Sampling interval parameter, the mean value for intervals between samples.
std::atomic_size_t g_sampling_interval{kDefaultSamplingIntervalBytes};

void (*g_hooks_install_callback)();
std::atomic_bool g_hooks_installed;

void* AllocFn(const AllocatorDispatch* self, size_t size, void* context) {
  ReentryGuard guard;
  void* address = self->next->alloc_function(self->next, size, context);
  if (LIKELY(guard)) {
    PoissonAllocationSampler::RecordAlloc(
        address, size, PoissonAllocationSampler::kMalloc, nullptr);
  }
  return address;
}

void* AllocZeroInitializedFn(const AllocatorDispatch* self,
                             size_t n,
                             size_t size,
                             void* context) {
  ReentryGuard guard;
  void* address =
      self->next->alloc_zero_initialized_function(self->next, n, size, context);
  if (LIKELY(guard)) {
    PoissonAllocationSampler::RecordAlloc(
        address, n * size, PoissonAllocationSampler::kMalloc, nullptr);
  }
  return address;
}

void* AllocAlignedFn(const AllocatorDispatch* self,
                     size_t alignment,
                     size_t size,
                     void* context) {
  ReentryGuard guard;
  void* address =
      self->next->alloc_aligned_function(self->next, alignment, size, context);
  if (LIKELY(guard)) {
    PoissonAllocationSampler::RecordAlloc(
        address, size, PoissonAllocationSampler::kMalloc, nullptr);
  }
  return address;
}

void* ReallocFn(const AllocatorDispatch* self,
                void* address,
                size_t size,
                void* context) {
  ReentryGuard guard;
  // Note: size == 0 actually performs free.
  PoissonAllocationSampler::RecordFree(address);
  address = self->next->realloc_function(self->next, address, size, context);
  if (LIKELY(guard)) {
    PoissonAllocationSampler::RecordAlloc(
        address, size, PoissonAllocationSampler::kMalloc, nullptr);
  }
  return address;
}

void FreeFn(const AllocatorDispatch* self, void* address, void* context) {
  // Note: The RecordFree should be called before free_function
  // (here and in other places).
  // That is because we need to remove the recorded allocation sample before
  // free_function, as once the latter is executed the address becomes available
  // and can be allocated by another thread. That would be racy otherwise.
  PoissonAllocationSampler::RecordFree(address);
  self->next->free_function(self->next, address, context);
}

size_t GetSizeEstimateFn(const AllocatorDispatch* self,
                         void* address,
                         void* context) {
  return self->next->get_size_estimate_function(self->next, address, context);
}

unsigned BatchMallocFn(const AllocatorDispatch* self,
                       size_t size,
                       void** results,
                       unsigned num_requested,
                       void* context) {
  ReentryGuard guard;
  unsigned num_allocated = self->next->batch_malloc_function(
      self->next, size, results, num_requested, context);
  if (LIKELY(guard)) {
    for (unsigned i = 0; i < num_allocated; ++i) {
      PoissonAllocationSampler::RecordAlloc(
          results[i], size, PoissonAllocationSampler::kMalloc, nullptr);
    }
  }
  return num_allocated;
}

void BatchFreeFn(const AllocatorDispatch* self,
                 void** to_be_freed,
                 unsigned num_to_be_freed,
                 void* context) {
  for (unsigned i = 0; i < num_to_be_freed; ++i)
    PoissonAllocationSampler::RecordFree(to_be_freed[i]);
  self->next->batch_free_function(self->next, to_be_freed, num_to_be_freed,
                                  context);
}

void FreeDefiniteSizeFn(const AllocatorDispatch* self,
                        void* address,
                        size_t size,
                        void* context) {
  PoissonAllocationSampler::RecordFree(address);
  self->next->free_definite_size_function(self->next, address, size, context);
}

static void* AlignedMallocFn(const AllocatorDispatch* self,
                             size_t size,
                             size_t alignment,
                             void* context) {
  ReentryGuard guard;
  void* address =
      self->next->aligned_malloc_function(self->next, size, alignment, context);
  if (LIKELY(guard)) {
    PoissonAllocationSampler::RecordAlloc(
        address, size, PoissonAllocationSampler::kMalloc, nullptr);
  }
  return address;
}

static void* AlignedReallocFn(const AllocatorDispatch* self,
                              void* address,
                              size_t size,
                              size_t alignment,
                              void* context) {
  ReentryGuard guard;
  // Note: size == 0 actually performs free.
  PoissonAllocationSampler::RecordFree(address);
  address = self->next->aligned_realloc_function(self->next, address, size,
                                                 alignment, context);
  if (LIKELY(guard)) {
    PoissonAllocationSampler::RecordAlloc(
        address, size, PoissonAllocationSampler::kMalloc, nullptr);
  }
  return address;
}

static void AlignedFreeFn(const AllocatorDispatch* self,
                          void* address,
                          void* context) {
  PoissonAllocationSampler::RecordFree(address);
  self->next->aligned_free_function(self->next, address, context);
}

AllocatorDispatch g_allocator_dispatch = {&AllocFn,
                                          &AllocZeroInitializedFn,
                                          &AllocAlignedFn,
                                          &ReallocFn,
                                          &FreeFn,
                                          &GetSizeEstimateFn,
                                          &BatchMallocFn,
                                          &BatchFreeFn,
                                          &FreeDefiniteSizeFn,
                                          &AlignedMallocFn,
                                          &AlignedReallocFn,
                                          &AlignedFreeFn,
                                          nullptr};

#if BUILDFLAG(USE_PARTITION_ALLOC) && !defined(OS_NACL)

void PartitionAllocHook(void* address, size_t size, const char* type) {
  PoissonAllocationSampler::RecordAlloc(
      address, size, PoissonAllocationSampler::kPartitionAlloc, type);
}

void PartitionFreeHook(void* address) {
  PoissonAllocationSampler::RecordFree(address);
}

#endif  // BUILDFLAG(USE_PARTITION_ALLOC) && !defined(OS_NACL)

}  // namespace

PoissonAllocationSampler::ScopedMuteThreadSamples::ScopedMuteThreadSamples() {
  DCHECK(!g_internal_reentry_guard);
  g_internal_reentry_guard = true;

  // We mute thread samples immediately after taking a sample, which is when we
  // reset g_accumulated_bytes_tls. This breaks the random sampling requirement
  // of the poisson process, and causes us to systematically overcount all other
  // allocations. That's because muted allocations rarely trigger a sample
  // [which would cause them to be ignored] since they occur right after
  // g_accumulated_bytes_tls is reset.
  //
  // To counteract this, we drop g_accumulated_bytes_tls by a large, fixed
  // amount to lower the probability that a sample is taken to close to 0. Then
  // we reset it after we're done muting thread samples.
  g_accumulated_bytes_tls_snapshot = g_accumulated_bytes_tls;
  g_accumulated_bytes_tls -= kAccumulatedBytesOffset;
}

PoissonAllocationSampler::ScopedMuteThreadSamples::~ScopedMuteThreadSamples() {
  DCHECK(g_internal_reentry_guard);
  g_internal_reentry_guard = false;
  g_accumulated_bytes_tls = g_accumulated_bytes_tls_snapshot;
}

// static
bool PoissonAllocationSampler::ScopedMuteThreadSamples::IsMuted() {
  return g_internal_reentry_guard;
}

PoissonAllocationSampler* PoissonAllocationSampler::instance_;

PoissonAllocationSampler::PoissonAllocationSampler() {
  CHECK_EQ(nullptr, instance_);
  instance_ = this;
  Init();
  auto* sampled_addresses = new LockFreeAddressHashSet(64);
  g_sampled_addresses_set.store(sampled_addresses, std::memory_order_release);
}

// static
void PoissonAllocationSampler::Init() {
  static bool init_once = []() {
    ReentryGuard::Init();
    return true;
  }();
  ignore_result(init_once);
}

// static
void PoissonAllocationSampler::InstallAllocatorHooksOnce() {
  static bool hook_installed = InstallAllocatorHooks();
  ignore_result(hook_installed);
}

// static
bool PoissonAllocationSampler::InstallAllocatorHooks() {
#if BUILDFLAG(USE_ALLOCATOR_SHIM)
  allocator::InsertAllocatorDispatch(&g_allocator_dispatch);
#else
  // If the allocator shim isn't available, then we don't install any hooks.
  // There's no point in printing an error message, since this can regularly
  // happen for tests.
  ignore_result(g_allocator_dispatch);
#endif  // BUILDFLAG(USE_ALLOCATOR_SHIM)

#if BUILDFLAG(USE_PARTITION_ALLOC) && !defined(OS_NACL)
  PartitionAllocHooks::SetObserverHooks(&PartitionAllocHook,
                                        &PartitionFreeHook);
#endif  // BUILDFLAG(USE_PARTITION_ALLOC) && !defined(OS_NACL)

  bool expected = false;
  if (!g_hooks_installed.compare_exchange_strong(expected, true))
    g_hooks_install_callback();

  return true;
}

// static
void PoissonAllocationSampler::SetHooksInstallCallback(
    void (*hooks_install_callback)()) {
  CHECK(!g_hooks_install_callback && hooks_install_callback);
  g_hooks_install_callback = hooks_install_callback;

  bool expected = false;
  if (!g_hooks_installed.compare_exchange_strong(expected, true))
    g_hooks_install_callback();
}

void PoissonAllocationSampler::SetSamplingInterval(size_t sampling_interval) {
  // TODO(alph): Reset the sample being collected if running.
  g_sampling_interval = sampling_interval;
}

// static
size_t PoissonAllocationSampler::GetNextSampleInterval(size_t interval) {
  if (UNLIKELY(g_deterministic))
    return interval;

  // We sample with a Poisson process, with constant average sampling
  // interval. This follows the exponential probability distribution with
  // parameter λ = 1/interval where |interval| is the average number of bytes
  // between samples.
  // Let u be a uniformly distributed random number between 0 and 1, then
  // next_sample = -ln(u) / λ
  double uniform = RandDouble();
  double value = -log(uniform) * interval;
  size_t min_value = sizeof(intptr_t);
  // We limit the upper bound of a sample interval to make sure we don't have
  // huge gaps in the sampling stream. Probability of the upper bound gets hit
  // is exp(-20) ~ 2e-9, so it should not skew the distribution.
  size_t max_value = interval * 20;
  if (UNLIKELY(value < min_value))
    return min_value;
  if (UNLIKELY(value > max_value))
    return max_value;
  return static_cast<size_t>(value);
}

// static
void PoissonAllocationSampler::RecordAlloc(void* address,
                                           size_t size,
                                           AllocatorType type,
                                           const char* context) {
  g_accumulated_bytes_tls += size;
  intptr_t accumulated_bytes = g_accumulated_bytes_tls;
  if (LIKELY(accumulated_bytes < 0))
    return;

  if (UNLIKELY(!g_running.load(std::memory_order_relaxed))) {
    // Sampling is in fact disabled. Reset the state of the sampler.
    // We do this check off the fast-path, because it's quite a rare state when
    // allocation hooks are installed but the sampler is not running.
    g_sampling_interval_initialized_tls = false;
    g_accumulated_bytes_tls = 0;
    return;
  }

  instance_->DoRecordAlloc(accumulated_bytes, size, address, type, context);
}

void PoissonAllocationSampler::DoRecordAlloc(intptr_t accumulated_bytes,
                                             size_t size,
                                             void* address,
                                             AllocatorType type,
                                             const char* context) {
  // Failed allocation? Skip the sample.
  if (UNLIKELY(!address))
    return;

  size_t mean_interval = g_sampling_interval.load(std::memory_order_relaxed);
  if (UNLIKELY(!g_sampling_interval_initialized_tls)) {
    g_sampling_interval_initialized_tls = true;
    // This is the very first allocation on the thread. It always makes it
    // passing the condition at |RecordAlloc|, because g_accumulated_bytes_tls
    // is initialized with zero due to TLS semantics.
    // Generate proper sampling interval instance and make sure the allocation
    // has indeed crossed the threshold before counting it as a sample.
    accumulated_bytes -= GetNextSampleInterval(mean_interval);
    if (accumulated_bytes < 0) {
      g_accumulated_bytes_tls = accumulated_bytes;
      return;
    }
  }

  size_t samples = accumulated_bytes / mean_interval;
  accumulated_bytes %= mean_interval;

  do {
    accumulated_bytes -= GetNextSampleInterval(mean_interval);
    ++samples;
  } while (accumulated_bytes >= 0);

  g_accumulated_bytes_tls = accumulated_bytes;

  if (UNLIKELY(ScopedMuteThreadSamples::IsMuted()))
    return;

  ScopedMuteThreadSamples no_reentrancy_scope;
  std::vector<SamplesObserver*> observers_copy;
  {
    AutoLock lock(mutex_);

    // TODO(alph): Sometimes RecordAlloc is called twice in a row without
    // a RecordFree in between. Investigate it.
    if (sampled_addresses_set().Contains(address))
      return;
    sampled_addresses_set().Insert(address);
    BalanceAddressesHashSet();
    observers_copy = observers_;
  }

  size_t total_allocated = mean_interval * samples;
  for (auto* observer : observers_copy)
    observer->SampleAdded(address, size, total_allocated, type, context);
}

void PoissonAllocationSampler::DoRecordFree(void* address) {
  if (UNLIKELY(ScopedMuteThreadSamples::IsMuted()))
    return;
  // There is a rare case on macOS and Android when the very first thread_local
  // access in ScopedMuteThreadSamples constructor may allocate and
  // thus reenter DoRecordAlloc. However the call chain won't build up further
  // as RecordAlloc accesses are guarded with pthread TLS-based ReentryGuard.
  ScopedMuteThreadSamples no_reentrancy_scope;
  std::vector<SamplesObserver*> observers_copy;
  {
    AutoLock lock(mutex_);
    observers_copy = observers_;
    sampled_addresses_set().Remove(address);
  }
  for (auto* observer : observers_copy)
    observer->SampleRemoved(address);
}

void PoissonAllocationSampler::BalanceAddressesHashSet() {
  // Check if the load_factor of the current addresses hash set becomes higher
  // than 1, allocate a new twice larger one, copy all the data,
  // and switch to using it.
  // During the copy process no other writes are made to both sets
  // as it's behind the lock.
  // All the readers continue to use the old one until the atomic switch
  // process takes place.
  LockFreeAddressHashSet& current_set = sampled_addresses_set();
  if (current_set.load_factor() < 1)
    return;
  auto new_set =
      std::make_unique<LockFreeAddressHashSet>(current_set.buckets_count() * 2);
  new_set->Copy(current_set);
  // Atomically switch all the new readers to the new set.
  g_sampled_addresses_set.store(new_set.release(), std::memory_order_release);
  // We leak the older set because we still have to keep all the old maps alive
  // as there might be reader threads that have already obtained the map,
  // but haven't yet managed to access it.
}

// static
LockFreeAddressHashSet& PoissonAllocationSampler::sampled_addresses_set() {
  return *g_sampled_addresses_set.load(std::memory_order_acquire);
}

// static
PoissonAllocationSampler* PoissonAllocationSampler::Get() {
  static NoDestructor<PoissonAllocationSampler> instance;
  return instance.get();
}

// static
void PoissonAllocationSampler::SuppressRandomnessForTest(bool suppress) {
  g_deterministic = suppress;
}

void PoissonAllocationSampler::AddSamplesObserver(SamplesObserver* observer) {
  ScopedMuteThreadSamples no_reentrancy_scope;
  AutoLock lock(mutex_);
  DCHECK(std::find(observers_.begin(), observers_.end(), observer) ==
         observers_.end());
  observers_.push_back(observer);
  InstallAllocatorHooksOnce();
  g_running = !observers_.empty();
}

void PoissonAllocationSampler::RemoveSamplesObserver(
    SamplesObserver* observer) {
  ScopedMuteThreadSamples no_reentrancy_scope;
  AutoLock lock(mutex_);
  auto it = std::find(observers_.begin(), observers_.end(), observer);
  DCHECK(it != observers_.end());
  observers_.erase(it);
  g_running = !observers_.empty();
}

}  // namespace base
