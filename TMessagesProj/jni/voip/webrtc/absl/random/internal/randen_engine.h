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

#ifndef ABSL_RANDOM_INTERNAL_RANDEN_ENGINE_H_
#define ABSL_RANDOM_INTERNAL_RANDEN_ENGINE_H_

#include <algorithm>
#include <cinttypes>
#include <cstdlib>
#include <iostream>
#include <iterator>
#include <limits>
#include <type_traits>

#include "absl/meta/type_traits.h"
#include "absl/random/internal/iostream_state_saver.h"
#include "absl/random/internal/randen.h"

namespace absl {
ABSL_NAMESPACE_BEGIN
namespace random_internal {

// Deterministic pseudorandom byte generator with backtracking resistance
// (leaking the state does not compromise prior outputs). Based on Reverie
// (see "A Robust and Sponge-Like PRNG with Improved Efficiency") instantiated
// with an improved Simpira-like permutation.
// Returns values of type "T" (must be a built-in unsigned integer type).
//
// RANDen = RANDom generator or beetroots in Swiss High German.
// 'Strong' (well-distributed, unpredictable, backtracking-resistant) random
// generator, faster in some benchmarks than std::mt19937_64 and pcg64_c32.
template <typename T>
class alignas(16) randen_engine {
 public:
  // C++11 URBG interface:
  using result_type = T;
  static_assert(std::is_unsigned<result_type>::value,
                "randen_engine template argument must be a built-in unsigned "
                "integer type");

  static constexpr result_type(min)() {
    return (std::numeric_limits<result_type>::min)();
  }

  static constexpr result_type(max)() {
    return (std::numeric_limits<result_type>::max)();
  }

  explicit randen_engine(result_type seed_value = 0) { seed(seed_value); }

  template <class SeedSequence,
            typename = typename absl::enable_if_t<
                !std::is_same<SeedSequence, randen_engine>::value>>
  explicit randen_engine(SeedSequence&& seq) {
    seed(seq);
  }

  randen_engine(const randen_engine&) = default;

  // Returns random bits from the buffer in units of result_type.
  result_type operator()() {
    // Refill the buffer if needed (unlikely).
    if (next_ >= kStateSizeT) {
      next_ = kCapacityT;
      impl_.Generate(state_);
    }

    return state_[next_++];
  }

  template <class SeedSequence>
  typename absl::enable_if_t<
      !std::is_convertible<SeedSequence, result_type>::value>
  seed(SeedSequence&& seq) {
    // Zeroes the state.
    seed();
    reseed(seq);
  }

  void seed(result_type seed_value = 0) {
    next_ = kStateSizeT;
    // Zeroes the inner state and fills the outer state with seed_value to
    // mimics behaviour of reseed
    std::fill(std::begin(state_), std::begin(state_) + kCapacityT, 0);
    std::fill(std::begin(state_) + kCapacityT, std::end(state_), seed_value);
  }

  // Inserts entropy into (part of) the state. Calling this periodically with
  // sufficient entropy ensures prediction resistance (attackers cannot predict
  // future outputs even if state is compromised).
  template <class SeedSequence>
  void reseed(SeedSequence& seq) {
    using sequence_result_type = typename SeedSequence::result_type;
    static_assert(sizeof(sequence_result_type) == 4,
                  "SeedSequence::result_type must be 32-bit");

    constexpr size_t kBufferSize =
        Randen::kSeedBytes / sizeof(sequence_result_type);
    alignas(16) sequence_result_type buffer[kBufferSize];

    // Randen::Absorb XORs the seed into state, which is then mixed by a call
    // to Randen::Generate. Seeding with only the provided entropy is preferred
    // to using an arbitrary generate() call, so use [rand.req.seed_seq]
    // size as a proxy for the number of entropy units that can be generated
    // without relying on seed sequence mixing...
    const size_t entropy_size = seq.size();
    if (entropy_size < kBufferSize) {
      // ... and only request that many values, or 256-bits, when unspecified.
      const size_t requested_entropy = (entropy_size == 0) ? 8u : entropy_size;
      std::fill(std::begin(buffer) + requested_entropy, std::end(buffer), 0);
      seq.generate(std::begin(buffer), std::begin(buffer) + requested_entropy);
      // The Randen paper suggests preferentially initializing even-numbered
      // 128-bit vectors of the randen state (there are 16 such vectors).
      // The seed data is merged into the state offset by 128-bits, which
      // implies prefering seed bytes [16..31, ..., 208..223]. Since the
      // buffer is 32-bit values, we swap the corresponding buffer positions in
      // 128-bit chunks.
      size_t dst = kBufferSize;
      while (dst > 7) {
        // leave the odd bucket as-is.
        dst -= 4;
        size_t src = dst >> 1;
        // swap 128-bits into the even bucket
        std::swap(buffer[--dst], buffer[--src]);
        std::swap(buffer[--dst], buffer[--src]);
        std::swap(buffer[--dst], buffer[--src]);
        std::swap(buffer[--dst], buffer[--src]);
      }
    } else {
      seq.generate(std::begin(buffer), std::end(buffer));
    }
    impl_.Absorb(buffer, state_);

    // Generate will be called when operator() is called
    next_ = kStateSizeT;
  }

  void discard(uint64_t count) {
    uint64_t step = std::min<uint64_t>(kStateSizeT - next_, count);
    count -= step;

    constexpr uint64_t kRateT = kStateSizeT - kCapacityT;
    while (count > 0) {
      next_ = kCapacityT;
      impl_.Generate(state_);
      step = std::min<uint64_t>(kRateT, count);
      count -= step;
    }
    next_ += step;
  }

  bool operator==(const randen_engine& other) const {
    return next_ == other.next_ &&
           std::equal(std::begin(state_), std::end(state_),
                      std::begin(other.state_));
  }

  bool operator!=(const randen_engine& other) const {
    return !(*this == other);
  }

  template <class CharT, class Traits>
  friend std::basic_ostream<CharT, Traits>& operator<<(
      std::basic_ostream<CharT, Traits>& os,  // NOLINT(runtime/references)
      const randen_engine<T>& engine) {       // NOLINT(runtime/references)
    using numeric_type =
        typename random_internal::stream_format_type<result_type>::type;
    auto saver = random_internal::make_ostream_state_saver(os);
    for (const auto& elem : engine.state_) {
      // In the case that `elem` is `uint8_t`, it must be cast to something
      // larger so that it prints as an integer rather than a character. For
      // simplicity, apply the cast all circumstances.
      os << static_cast<numeric_type>(elem) << os.fill();
    }
    os << engine.next_;
    return os;
  }

  template <class CharT, class Traits>
  friend std::basic_istream<CharT, Traits>& operator>>(
      std::basic_istream<CharT, Traits>& is,  // NOLINT(runtime/references)
      randen_engine<T>& engine) {             // NOLINT(runtime/references)
    using numeric_type =
        typename random_internal::stream_format_type<result_type>::type;
    result_type state[kStateSizeT];
    size_t next;
    for (auto& elem : state) {
      // It is not possible to read uint8_t from wide streams, so it is
      // necessary to read a wider type and then cast it to uint8_t.
      numeric_type value;
      is >> value;
      elem = static_cast<result_type>(value);
    }
    is >> next;
    if (is.fail()) {
      return is;
    }
    std::memcpy(engine.state_, state, sizeof(engine.state_));
    engine.next_ = next;
    return is;
  }

 private:
  static constexpr size_t kStateSizeT =
      Randen::kStateBytes / sizeof(result_type);
  static constexpr size_t kCapacityT =
      Randen::kCapacityBytes / sizeof(result_type);

  // First kCapacityT are `inner', the others are accessible random bits.
  alignas(16) result_type state_[kStateSizeT];
  size_t next_;  // index within state_
  Randen impl_;
};

}  // namespace random_internal
ABSL_NAMESPACE_END
}  // namespace absl

#endif  // ABSL_RANDOM_INTERNAL_RANDEN_ENGINE_H_
