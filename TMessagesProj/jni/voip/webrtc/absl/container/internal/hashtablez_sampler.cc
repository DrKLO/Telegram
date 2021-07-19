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

#include "absl/container/internal/hashtablez_sampler.h"

#include <atomic>
#include <cassert>
#include <cmath>
#include <functional>
#include <limits>

#include "absl/base/attributes.h"
#include "absl/base/internal/exponential_biased.h"
#include "absl/container/internal/have_sse.h"
#include "absl/debugging/stacktrace.h"
#include "absl/memory/memory.h"
#include "absl/synchronization/mutex.h"

namespace absl {
ABSL_NAMESPACE_BEGIN
namespace container_internal {
constexpr int HashtablezInfo::kMaxStackDepth;

namespace {
ABSL_CONST_INIT std::atomic<bool> g_hashtablez_enabled{
    false
};
ABSL_CONST_INIT std::atomic<int32_t> g_hashtablez_sample_parameter{1 << 10};
ABSL_CONST_INIT std::atomic<int32_t> g_hashtablez_max_samples{1 << 20};

#if defined(ABSL_INTERNAL_HASHTABLEZ_SAMPLE)
ABSL_PER_THREAD_TLS_KEYWORD absl::base_internal::ExponentialBiased
    g_exponential_biased_generator;
#endif

}  // namespace

#if defined(ABSL_INTERNAL_HASHTABLEZ_SAMPLE)
ABSL_PER_THREAD_TLS_KEYWORD int64_t global_next_sample = 0;
#endif  // defined(ABSL_INTERNAL_HASHTABLEZ_SAMPLE)

HashtablezSampler& HashtablezSampler::Global() {
  static auto* sampler = new HashtablezSampler();
  return *sampler;
}

HashtablezSampler::DisposeCallback HashtablezSampler::SetDisposeCallback(
    DisposeCallback f) {
  return dispose_.exchange(f, std::memory_order_relaxed);
}

HashtablezInfo::HashtablezInfo() { PrepareForSampling(); }
HashtablezInfo::~HashtablezInfo() = default;

void HashtablezInfo::PrepareForSampling() {
  capacity.store(0, std::memory_order_relaxed);
  size.store(0, std::memory_order_relaxed);
  num_erases.store(0, std::memory_order_relaxed);
  max_probe_length.store(0, std::memory_order_relaxed);
  total_probe_length.store(0, std::memory_order_relaxed);
  hashes_bitwise_or.store(0, std::memory_order_relaxed);
  hashes_bitwise_and.store(~size_t{}, std::memory_order_relaxed);

  create_time = absl::Now();
  // The inliner makes hardcoded skip_count difficult (especially when combined
  // with LTO).  We use the ability to exclude stacks by regex when encoding
  // instead.
  depth = absl::GetStackTrace(stack, HashtablezInfo::kMaxStackDepth,
                              /* skip_count= */ 0);
  dead = nullptr;
}

HashtablezSampler::HashtablezSampler()
    : dropped_samples_(0), size_estimate_(0), all_(nullptr), dispose_(nullptr) {
  absl::MutexLock l(&graveyard_.init_mu);
  graveyard_.dead = &graveyard_;
}

HashtablezSampler::~HashtablezSampler() {
  HashtablezInfo* s = all_.load(std::memory_order_acquire);
  while (s != nullptr) {
    HashtablezInfo* next = s->next;
    delete s;
    s = next;
  }
}

void HashtablezSampler::PushNew(HashtablezInfo* sample) {
  sample->next = all_.load(std::memory_order_relaxed);
  while (!all_.compare_exchange_weak(sample->next, sample,
                                     std::memory_order_release,
                                     std::memory_order_relaxed)) {
  }
}

void HashtablezSampler::PushDead(HashtablezInfo* sample) {
  if (auto* dispose = dispose_.load(std::memory_order_relaxed)) {
    dispose(*sample);
  }

  absl::MutexLock graveyard_lock(&graveyard_.init_mu);
  absl::MutexLock sample_lock(&sample->init_mu);
  sample->dead = graveyard_.dead;
  graveyard_.dead = sample;
}

HashtablezInfo* HashtablezSampler::PopDead() {
  absl::MutexLock graveyard_lock(&graveyard_.init_mu);

  // The list is circular, so eventually it collapses down to
  //   graveyard_.dead == &graveyard_
  // when it is empty.
  HashtablezInfo* sample = graveyard_.dead;
  if (sample == &graveyard_) return nullptr;

  absl::MutexLock sample_lock(&sample->init_mu);
  graveyard_.dead = sample->dead;
  sample->PrepareForSampling();
  return sample;
}

HashtablezInfo* HashtablezSampler::Register() {
  int64_t size = size_estimate_.fetch_add(1, std::memory_order_relaxed);
  if (size > g_hashtablez_max_samples.load(std::memory_order_relaxed)) {
    size_estimate_.fetch_sub(1, std::memory_order_relaxed);
    dropped_samples_.fetch_add(1, std::memory_order_relaxed);
    return nullptr;
  }

  HashtablezInfo* sample = PopDead();
  if (sample == nullptr) {
    // Resurrection failed.  Hire a new warlock.
    sample = new HashtablezInfo();
    PushNew(sample);
  }

  return sample;
}

void HashtablezSampler::Unregister(HashtablezInfo* sample) {
  PushDead(sample);
  size_estimate_.fetch_sub(1, std::memory_order_relaxed);
}

int64_t HashtablezSampler::Iterate(
    const std::function<void(const HashtablezInfo& stack)>& f) {
  HashtablezInfo* s = all_.load(std::memory_order_acquire);
  while (s != nullptr) {
    absl::MutexLock l(&s->init_mu);
    if (s->dead == nullptr) {
      f(*s);
    }
    s = s->next;
  }

  return dropped_samples_.load(std::memory_order_relaxed);
}

static bool ShouldForceSampling() {
  enum ForceState {
    kDontForce,
    kForce,
    kUninitialized
  };
  ABSL_CONST_INIT static std::atomic<ForceState> global_state{
      kUninitialized};
  ForceState state = global_state.load(std::memory_order_relaxed);
  if (ABSL_PREDICT_TRUE(state == kDontForce)) return false;

  if (state == kUninitialized) {
    state = AbslContainerInternalSampleEverything() ? kForce : kDontForce;
    global_state.store(state, std::memory_order_relaxed);
  }
  return state == kForce;
}

HashtablezInfo* SampleSlow(int64_t* next_sample) {
  if (ABSL_PREDICT_FALSE(ShouldForceSampling())) {
    *next_sample = 1;
    return HashtablezSampler::Global().Register();
  }

#if !defined(ABSL_INTERNAL_HASHTABLEZ_SAMPLE)
  *next_sample = std::numeric_limits<int64_t>::max();
  return nullptr;
#else
  bool first = *next_sample < 0;
  *next_sample = g_exponential_biased_generator.GetStride(
      g_hashtablez_sample_parameter.load(std::memory_order_relaxed));
  // Small values of interval are equivalent to just sampling next time.
  ABSL_ASSERT(*next_sample >= 1);

  // g_hashtablez_enabled can be dynamically flipped, we need to set a threshold
  // low enough that we will start sampling in a reasonable time, so we just use
  // the default sampling rate.
  if (!g_hashtablez_enabled.load(std::memory_order_relaxed)) return nullptr;

  // We will only be negative on our first count, so we should just retry in
  // that case.
  if (first) {
    if (ABSL_PREDICT_TRUE(--*next_sample > 0)) return nullptr;
    return SampleSlow(next_sample);
  }

  return HashtablezSampler::Global().Register();
#endif
}

void UnsampleSlow(HashtablezInfo* info) {
  HashtablezSampler::Global().Unregister(info);
}

void RecordInsertSlow(HashtablezInfo* info, size_t hash,
                      size_t distance_from_desired) {
  // SwissTables probe in groups of 16, so scale this to count items probes and
  // not offset from desired.
  size_t probe_length = distance_from_desired;
#if ABSL_INTERNAL_RAW_HASH_SET_HAVE_SSE2
  probe_length /= 16;
#else
  probe_length /= 8;
#endif

  info->hashes_bitwise_and.fetch_and(hash, std::memory_order_relaxed);
  info->hashes_bitwise_or.fetch_or(hash, std::memory_order_relaxed);
  info->max_probe_length.store(
      std::max(info->max_probe_length.load(std::memory_order_relaxed),
               probe_length),
      std::memory_order_relaxed);
  info->total_probe_length.fetch_add(probe_length, std::memory_order_relaxed);
  info->size.fetch_add(1, std::memory_order_relaxed);
}

void SetHashtablezEnabled(bool enabled) {
  g_hashtablez_enabled.store(enabled, std::memory_order_release);
}

void SetHashtablezSampleParameter(int32_t rate) {
  if (rate > 0) {
    g_hashtablez_sample_parameter.store(rate, std::memory_order_release);
  } else {
    ABSL_RAW_LOG(ERROR, "Invalid hashtablez sample rate: %lld",
                 static_cast<long long>(rate));  // NOLINT(runtime/int)
  }
}

void SetHashtablezMaxSamples(int32_t max) {
  if (max > 0) {
    g_hashtablez_max_samples.store(max, std::memory_order_release);
  } else {
    ABSL_RAW_LOG(ERROR, "Invalid hashtablez max samples: %lld",
                 static_cast<long long>(max));  // NOLINT(runtime/int)
  }
}

}  // namespace container_internal
ABSL_NAMESPACE_END
}  // namespace absl
