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

#ifndef ABSL_RANDOM_INTERNAL_NONSECURE_BASE_H_
#define ABSL_RANDOM_INTERNAL_NONSECURE_BASE_H_

#include <algorithm>
#include <cstdint>
#include <iostream>
#include <iterator>
#include <random>
#include <string>
#include <type_traits>
#include <vector>

#include "absl/base/macros.h"
#include "absl/meta/type_traits.h"
#include "absl/random/internal/pool_urbg.h"
#include "absl/random/internal/salted_seed_seq.h"
#include "absl/random/internal/seed_material.h"
#include "absl/types/optional.h"
#include "absl/types/span.h"

namespace absl {
ABSL_NAMESPACE_BEGIN
namespace random_internal {

// Each instance of NonsecureURBGBase<URBG> will be seeded by variates produced
// by a thread-unique URBG-instance.
template <typename URBG>
class NonsecureURBGBase {
 public:
  using result_type = typename URBG::result_type;

  // Default constructor
  NonsecureURBGBase() : urbg_(ConstructURBG()) {}

  // Copy disallowed, move allowed.
  NonsecureURBGBase(const NonsecureURBGBase&) = delete;
  NonsecureURBGBase& operator=(const NonsecureURBGBase&) = delete;
  NonsecureURBGBase(NonsecureURBGBase&&) = default;
  NonsecureURBGBase& operator=(NonsecureURBGBase&&) = default;

  // Constructor using a seed
  template <class SSeq, typename = typename absl::enable_if_t<
                            !std::is_same<SSeq, NonsecureURBGBase>::value>>
  explicit NonsecureURBGBase(SSeq&& seq)
      : urbg_(ConstructURBG(std::forward<SSeq>(seq))) {}

  // Note: on MSVC, min() or max() can be interpreted as MIN() or MAX(), so we
  // enclose min() or max() in parens as (min)() and (max)().
  // Additionally, clang-format requires no space before this construction.

  // NonsecureURBGBase::min()
  static constexpr result_type(min)() { return (URBG::min)(); }

  // NonsecureURBGBase::max()
  static constexpr result_type(max)() { return (URBG::max)(); }

  // NonsecureURBGBase::operator()()
  result_type operator()() { return urbg_(); }

  // NonsecureURBGBase::discard()
  void discard(unsigned long long values) {  // NOLINT(runtime/int)
    urbg_.discard(values);
  }

  bool operator==(const NonsecureURBGBase& other) const {
    return urbg_ == other.urbg_;
  }

  bool operator!=(const NonsecureURBGBase& other) const {
    return !(urbg_ == other.urbg_);
  }

 private:
  // Seeder is a custom seed sequence type where generate() fills the provided
  // buffer via the RandenPool entropy source.
  struct Seeder {
    using result_type = uint32_t;

    size_t size() { return 0; }

    template <typename OutIterator>
    void param(OutIterator) const {}

    template <typename RandomAccessIterator>
    void generate(RandomAccessIterator begin, RandomAccessIterator end) {
      if (begin != end) {
        // begin, end must be random access iterators assignable from uint32_t.
        generate_impl(
            std::integral_constant<bool, sizeof(*begin) == sizeof(uint32_t)>{},
            begin, end);
      }
    }

    // Commonly, generate is invoked with a pointer to a buffer which
    // can be cast to a uint32_t.
    template <typename RandomAccessIterator>
    void generate_impl(std::integral_constant<bool, true>,
                       RandomAccessIterator begin, RandomAccessIterator end) {
      auto buffer = absl::MakeSpan(begin, end);
      auto target = absl::MakeSpan(reinterpret_cast<uint32_t*>(buffer.data()),
                                   buffer.size());
      RandenPool<uint32_t>::Fill(target);
    }

    // The non-uint32_t case should be uncommon, and involves an extra copy,
    // filling the uint32_t buffer and then mixing into the output.
    template <typename RandomAccessIterator>
    void generate_impl(std::integral_constant<bool, false>,
                       RandomAccessIterator begin, RandomAccessIterator end) {
      const size_t n = std::distance(begin, end);
      absl::InlinedVector<uint32_t, 8> data(n, 0);
      RandenPool<uint32_t>::Fill(absl::MakeSpan(data.begin(), data.end()));
      std::copy(std::begin(data), std::end(data), begin);
    }
  };

  static URBG ConstructURBG() {
    Seeder seeder;
    return URBG(seeder);
  }

  template <typename SSeq>
  static URBG ConstructURBG(SSeq&& seq) {  // NOLINT(runtime/references)
    auto salted_seq =
        random_internal::MakeSaltedSeedSeq(std::forward<SSeq>(seq));
    return URBG(salted_seq);
  }

  URBG urbg_;
};

}  // namespace random_internal
ABSL_NAMESPACE_END
}  // namespace absl

#endif  // ABSL_RANDOM_INTERNAL_NONSECURE_BASE_H_
