//
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
// File: bit_gen_ref.h
// -----------------------------------------------------------------------------
//
// This header defines a bit generator "reference" class, for use in interfaces
// that take both Abseil (e.g. `absl::BitGen`) and standard library (e.g.
// `std::mt19937`) bit generators.

#ifndef ABSL_RANDOM_BIT_GEN_REF_H_
#define ABSL_RANDOM_BIT_GEN_REF_H_

#include "absl/base/macros.h"
#include "absl/meta/type_traits.h"
#include "absl/random/internal/distribution_caller.h"
#include "absl/random/internal/fast_uniform_bits.h"
#include "absl/random/internal/mocking_bit_gen_base.h"

namespace absl {
ABSL_NAMESPACE_BEGIN
namespace random_internal {

template <typename URBG, typename = void, typename = void, typename = void>
struct is_urbg : std::false_type {};

template <typename URBG>
struct is_urbg<
    URBG,
    absl::enable_if_t<std::is_same<
        typename URBG::result_type,
        typename std::decay<decltype((URBG::min)())>::type>::value>,
    absl::enable_if_t<std::is_same<
        typename URBG::result_type,
        typename std::decay<decltype((URBG::max)())>::type>::value>,
    absl::enable_if_t<std::is_same<
        typename URBG::result_type,
        typename std::decay<decltype(std::declval<URBG>()())>::type>::value>>
    : std::true_type {};

}  // namespace random_internal

// -----------------------------------------------------------------------------
// absl::BitGenRef
// -----------------------------------------------------------------------------
//
// `absl::BitGenRef` is a type-erasing class that provides a generator-agnostic
// non-owning "reference" interface for use in place of any specific uniform
// random bit generator (URBG). This class may be used for both Abseil
// (e.g. `absl::BitGen`, `absl::InsecureBitGen`) and Standard library (e.g
// `std::mt19937`, `std::minstd_rand`) bit generators.
//
// Like other reference classes, `absl::BitGenRef` does not own the
// underlying bit generator, and the underlying instance must outlive the
// `absl::BitGenRef`.
//
// `absl::BitGenRef` is particularly useful when used with an
// `absl::MockingBitGen` to test specific paths in functions which use random
// values.
//
// Example:
//    void TakesBitGenRef(absl::BitGenRef gen) {
//      int x = absl::Uniform<int>(gen, 0, 1000);
//    }
//
class BitGenRef {
 public:
  using result_type = uint64_t;

  BitGenRef(const absl::BitGenRef&) = default;
  BitGenRef(absl::BitGenRef&&) = default;
  BitGenRef& operator=(const absl::BitGenRef&) = default;
  BitGenRef& operator=(absl::BitGenRef&&) = default;

  template <typename URBG,
            typename absl::enable_if_t<
                (!std::is_same<URBG, BitGenRef>::value &&
                 random_internal::is_urbg<URBG>::value)>* = nullptr>
  BitGenRef(URBG& gen)  // NOLINT
      : mocked_gen_ptr_(MakeMockPointer(&gen)),
        t_erased_gen_ptr_(reinterpret_cast<uintptr_t>(&gen)),
        generate_impl_fn_(ImplFn<URBG>) {
  }

  static constexpr result_type(min)() {
    return (std::numeric_limits<result_type>::min)();
  }

  static constexpr result_type(max)() {
    return (std::numeric_limits<result_type>::max)();
  }

  result_type operator()() { return generate_impl_fn_(t_erased_gen_ptr_); }

 private:
  friend struct absl::random_internal::DistributionCaller<absl::BitGenRef>;
  using impl_fn = result_type (*)(uintptr_t);
  using mocker_base_t = absl::random_internal::MockingBitGenBase;

  // Convert an arbitrary URBG pointer into either a valid mocker_base_t
  // pointer or a nullptr.
  static inline mocker_base_t* MakeMockPointer(mocker_base_t* t) { return t; }
  static inline mocker_base_t* MakeMockPointer(void*) { return nullptr; }

  template <typename URBG>
  static result_type ImplFn(uintptr_t ptr) {
    // Ensure that the return values from operator() fill the entire
    // range promised by result_type, min() and max().
    absl::random_internal::FastUniformBits<result_type> fast_uniform_bits;
    return fast_uniform_bits(*reinterpret_cast<URBG*>(ptr));
  }

  mocker_base_t* mocked_gen_ptr_;
  uintptr_t t_erased_gen_ptr_;
  impl_fn generate_impl_fn_;
};

namespace random_internal {

template <>
struct DistributionCaller<absl::BitGenRef> {
  template <typename DistrT, typename... Args>
  static typename DistrT::result_type Call(absl::BitGenRef* gen_ref,
                                           Args&&... args) {
    auto* mock_ptr = gen_ref->mocked_gen_ptr_;
    if (mock_ptr == nullptr) {
      DistrT dist(std::forward<Args>(args)...);
      return dist(*gen_ref);
    } else {
      return mock_ptr->template Call<DistrT>(std::forward<Args>(args)...);
    }
  }
};

}  // namespace random_internal
ABSL_NAMESPACE_END
}  // namespace absl

#endif  // ABSL_RANDOM_BIT_GEN_REF_H_
