// Copyright 2018 The Abseil Authors.
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
// File: hashtablez_sampler.h
// -----------------------------------------------------------------------------
//
// This header file defines the API for a low level library to sample hashtables
// and collect runtime statistics about them.
//
// `HashtablezSampler` controls the lifecycle of `HashtablezInfo` objects which
// store information about a single sample.
//
// `Record*` methods store information into samples.
// `Sample()` and `Unsample()` make use of a single global sampler with
// properties controlled by the flags hashtablez_enabled,
// hashtablez_sample_rate, and hashtablez_max_samples.
//
// WARNING
//
// Using this sampling API may cause sampled Swiss tables to use the global
// allocator (operator `new`) in addition to any custom allocator.  If you
// are using a table in an unusual circumstance where allocation or calling a
// linux syscall is unacceptable, this could interfere.
//
// This utility is internal-only. Use at your own risk.

#ifndef ABSL_CONTAINER_INTERNAL_HASHTABLEZ_SAMPLER_H_
#define ABSL_CONTAINER_INTERNAL_HASHTABLEZ_SAMPLER_H_

#include <atomic>
#include <functional>
#include <memory>
#include <vector>

#include "absl/base/internal/per_thread_tls.h"
#include "absl/base/optimization.h"
#include "absl/container/internal/have_sse.h"
#include "absl/synchronization/mutex.h"
#include "absl/utility/utility.h"

namespace absl {
ABSL_NAMESPACE_BEGIN
namespace container_internal {

// Stores information about a sampled hashtable.  All mutations to this *must*
// be made through `Record*` functions below.  All reads from this *must* only
// occur in the callback to `HashtablezSampler::Iterate`.
struct HashtablezInfo {
  // Constructs the object but does not fill in any fields.
  HashtablezInfo();
  ~HashtablezInfo();
  HashtablezInfo(const HashtablezInfo&) = delete;
  HashtablezInfo& operator=(const HashtablezInfo&) = delete;

  // Puts the object into a clean state, fills in the logically `const` members,
  // blocking for any readers that are currently sampling the object.
  void PrepareForSampling() ABSL_EXCLUSIVE_LOCKS_REQUIRED(init_mu);

  // These fields are mutated by the various Record* APIs and need to be
  // thread-safe.
  std::atomic<size_t> capacity;
  std::atomic<size_t> size;
  std::atomic<size_t> num_erases;
  std::atomic<size_t> max_probe_length;
  std::atomic<size_t> total_probe_length;
  std::atomic<size_t> hashes_bitwise_or;
  std::atomic<size_t> hashes_bitwise_and;

  // `HashtablezSampler` maintains intrusive linked lists for all samples.  See
  // comments on `HashtablezSampler::all_` for details on these.  `init_mu`
  // guards the ability to restore the sample to a pristine state.  This
  // prevents races with sampling and resurrecting an object.
  absl::Mutex init_mu;
  HashtablezInfo* next;
  HashtablezInfo* dead ABSL_GUARDED_BY(init_mu);

  // All of the fields below are set by `PrepareForSampling`, they must not be
  // mutated in `Record*` functions.  They are logically `const` in that sense.
  // These are guarded by init_mu, but that is not externalized to clients, who
  // can only read them during `HashtablezSampler::Iterate` which will hold the
  // lock.
  static constexpr int kMaxStackDepth = 64;
  absl::Time create_time;
  int32_t depth;
  void* stack[kMaxStackDepth];
};

inline void RecordRehashSlow(HashtablezInfo* info, size_t total_probe_length) {
#if ABSL_INTERNAL_RAW_HASH_SET_HAVE_SSE2
  total_probe_length /= 16;
#else
  total_probe_length /= 8;
#endif
  info->total_probe_length.store(total_probe_length, std::memory_order_relaxed);
  info->num_erases.store(0, std::memory_order_relaxed);
}

inline void RecordStorageChangedSlow(HashtablezInfo* info, size_t size,
                                     size_t capacity) {
  info->size.store(size, std::memory_order_relaxed);
  info->capacity.store(capacity, std::memory_order_relaxed);
  if (size == 0) {
    // This is a clear, reset the total/num_erases too.
    RecordRehashSlow(info, 0);
  }
}

void RecordInsertSlow(HashtablezInfo* info, size_t hash,
                      size_t distance_from_desired);

inline void RecordEraseSlow(HashtablezInfo* info) {
  info->size.fetch_sub(1, std::memory_order_relaxed);
  info->num_erases.fetch_add(1, std::memory_order_relaxed);
}

HashtablezInfo* SampleSlow(int64_t* next_sample);
void UnsampleSlow(HashtablezInfo* info);

class HashtablezInfoHandle {
 public:
  explicit HashtablezInfoHandle() : info_(nullptr) {}
  explicit HashtablezInfoHandle(HashtablezInfo* info) : info_(info) {}
  ~HashtablezInfoHandle() {
    if (ABSL_PREDICT_TRUE(info_ == nullptr)) return;
    UnsampleSlow(info_);
  }

  HashtablezInfoHandle(const HashtablezInfoHandle&) = delete;
  HashtablezInfoHandle& operator=(const HashtablezInfoHandle&) = delete;

  HashtablezInfoHandle(HashtablezInfoHandle&& o) noexcept
      : info_(absl::exchange(o.info_, nullptr)) {}
  HashtablezInfoHandle& operator=(HashtablezInfoHandle&& o) noexcept {
    if (ABSL_PREDICT_FALSE(info_ != nullptr)) {
      UnsampleSlow(info_);
    }
    info_ = absl::exchange(o.info_, nullptr);
    return *this;
  }

  inline void RecordStorageChanged(size_t size, size_t capacity) {
    if (ABSL_PREDICT_TRUE(info_ == nullptr)) return;
    RecordStorageChangedSlow(info_, size, capacity);
  }

  inline void RecordRehash(size_t total_probe_length) {
    if (ABSL_PREDICT_TRUE(info_ == nullptr)) return;
    RecordRehashSlow(info_, total_probe_length);
  }

  inline void RecordInsert(size_t hash, size_t distance_from_desired) {
    if (ABSL_PREDICT_TRUE(info_ == nullptr)) return;
    RecordInsertSlow(info_, hash, distance_from_desired);
  }

  inline void RecordErase() {
    if (ABSL_PREDICT_TRUE(info_ == nullptr)) return;
    RecordEraseSlow(info_);
  }

  friend inline void swap(HashtablezInfoHandle& lhs,
                          HashtablezInfoHandle& rhs) {
    std::swap(lhs.info_, rhs.info_);
  }

 private:
  friend class HashtablezInfoHandlePeer;
  HashtablezInfo* info_;
};

#if defined(ABSL_INTERNAL_HASHTABLEZ_SAMPLE)
#error ABSL_INTERNAL_HASHTABLEZ_SAMPLE cannot be directly set
#endif  // defined(ABSL_INTERNAL_HASHTABLEZ_SAMPLE)

#if (ABSL_PER_THREAD_TLS == 1) && !defined(ABSL_BUILD_DLL) && \
    !defined(ABSL_CONSUME_DLL)
#define ABSL_INTERNAL_HASHTABLEZ_SAMPLE
#endif

#if defined(ABSL_INTERNAL_HASHTABLEZ_SAMPLE)
extern ABSL_PER_THREAD_TLS_KEYWORD int64_t global_next_sample;
#endif  // ABSL_PER_THREAD_TLS

// Returns an RAII sampling handle that manages registration and unregistation
// with the global sampler.
inline HashtablezInfoHandle Sample() {
#if defined(ABSL_INTERNAL_HASHTABLEZ_SAMPLE)
  if (ABSL_PREDICT_TRUE(--global_next_sample > 0)) {
    return HashtablezInfoHandle(nullptr);
  }
  return HashtablezInfoHandle(SampleSlow(&global_next_sample));
#else
  return HashtablezInfoHandle(nullptr);
#endif  // !ABSL_PER_THREAD_TLS
}

// Holds samples and their associated stack traces with a soft limit of
// `SetHashtablezMaxSamples()`.
//
// Thread safe.
class HashtablezSampler {
 public:
  // Returns a global Sampler.
  static HashtablezSampler& Global();

  HashtablezSampler();
  ~HashtablezSampler();

  // Registers for sampling.  Returns an opaque registration info.
  HashtablezInfo* Register();

  // Unregisters the sample.
  void Unregister(HashtablezInfo* sample);

  // The dispose callback will be called on all samples the moment they are
  // being unregistered. Only affects samples that are unregistered after the
  // callback has been set.
  // Returns the previous callback.
  using DisposeCallback = void (*)(const HashtablezInfo&);
  DisposeCallback SetDisposeCallback(DisposeCallback f);

  // Iterates over all the registered `StackInfo`s.  Returning the number of
  // samples that have been dropped.
  int64_t Iterate(const std::function<void(const HashtablezInfo& stack)>& f);

 private:
  void PushNew(HashtablezInfo* sample);
  void PushDead(HashtablezInfo* sample);
  HashtablezInfo* PopDead();

  std::atomic<size_t> dropped_samples_;
  std::atomic<size_t> size_estimate_;

  // Intrusive lock free linked lists for tracking samples.
  //
  // `all_` records all samples (they are never removed from this list) and is
  // terminated with a `nullptr`.
  //
  // `graveyard_.dead` is a circular linked list.  When it is empty,
  // `graveyard_.dead == &graveyard`.  The list is circular so that
  // every item on it (even the last) has a non-null dead pointer.  This allows
  // `Iterate` to determine if a given sample is live or dead using only
  // information on the sample itself.
  //
  // For example, nodes [A, B, C, D, E] with [A, C, E] alive and [B, D] dead
  // looks like this (G is the Graveyard):
  //
  //           +---+    +---+    +---+    +---+    +---+
  //    all -->| A |--->| B |--->| C |--->| D |--->| E |
  //           |   |    |   |    |   |    |   |    |   |
  //   +---+   |   | +->|   |-+  |   | +->|   |-+  |   |
  //   | G |   +---+ |  +---+ |  +---+ |  +---+ |  +---+
  //   |   |         |        |        |        |
  //   |   | --------+        +--------+        |
  //   +---+                                    |
  //     ^                                      |
  //     +--------------------------------------+
  //
  std::atomic<HashtablezInfo*> all_;
  HashtablezInfo graveyard_;

  std::atomic<DisposeCallback> dispose_;
};

// Enables or disables sampling for Swiss tables.
void SetHashtablezEnabled(bool enabled);

// Sets the rate at which Swiss tables will be sampled.
void SetHashtablezSampleParameter(int32_t rate);

// Sets a soft max for the number of samples that will be kept.
void SetHashtablezMaxSamples(int32_t max);

// Configuration override.
// This allows process-wide sampling without depending on order of
// initialization of static storage duration objects.
// The definition of this constant is weak, which allows us to inject a
// different value for it at link time.
extern "C" bool AbslContainerInternalSampleEverything();

}  // namespace container_internal
ABSL_NAMESPACE_END
}  // namespace absl

#endif  // ABSL_CONTAINER_INTERNAL_HASHTABLEZ_SAMPLER_H_
