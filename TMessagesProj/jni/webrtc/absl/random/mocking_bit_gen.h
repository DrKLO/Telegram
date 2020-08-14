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
// mocking_bit_gen.h
// -----------------------------------------------------------------------------
//
// This file includes an `absl::MockingBitGen` class to use as a mock within the
// Googletest testing framework. Such a mock is useful to provide deterministic
// values as return values within (otherwise random) Abseil distribution
// functions. Such determinism within a mock is useful within testing frameworks
// to test otherwise indeterminate APIs.
//
// More information about the Googletest testing framework is available at
// https://github.com/google/googletest

#ifndef ABSL_RANDOM_MOCKING_BIT_GEN_H_
#define ABSL_RANDOM_MOCKING_BIT_GEN_H_

#include <iterator>
#include <limits>
#include <memory>
#include <tuple>
#include <type_traits>
#include <typeindex>
#include <typeinfo>
#include <utility>

#include "gmock/gmock.h"
#include "gtest/gtest.h"
#include "absl/container/flat_hash_map.h"
#include "absl/meta/type_traits.h"
#include "absl/random/distributions.h"
#include "absl/random/internal/distribution_caller.h"
#include "absl/random/internal/mocking_bit_gen_base.h"
#include "absl/strings/str_cat.h"
#include "absl/strings/str_join.h"
#include "absl/types/span.h"
#include "absl/types/variant.h"
#include "absl/utility/utility.h"

namespace absl {
ABSL_NAMESPACE_BEGIN

namespace random_internal {

template <typename, typename>
struct MockSingleOverload;

}  // namespace random_internal

// MockingBitGen
//
// `absl::MockingBitGen` is a mock Uniform Random Bit Generator (URBG) class
// which can act in place of an `absl::BitGen` URBG within tests using the
// Googletest testing framework.
//
// Usage:
//
// Use an `absl::MockingBitGen` along with a mock distribution object (within
// mock_distributions.h) inside Googletest constructs such as ON_CALL(),
// EXPECT_TRUE(), etc. to produce deterministic results conforming to the
// distribution's API contract.
//
// Example:
//
//  // Mock a call to an `absl::Bernoulli` distribution using Googletest
//   absl::MockingBitGen bitgen;
//
//   ON_CALL(absl::MockBernoulli(), Call(bitgen, 0.5))
//       .WillByDefault(testing::Return(true));
//   EXPECT_TRUE(absl::Bernoulli(bitgen, 0.5));
//
//  // Mock a call to an `absl::Uniform` distribution within Googletest
//  absl::MockingBitGen bitgen;
//
//   ON_CALL(absl::MockUniform<int>(), Call(bitgen, testing::_, testing::_))
//       .WillByDefault([] (int low, int high) {
//           return (low + high) / 2;
//       });
//
//   EXPECT_EQ(absl::Uniform<int>(gen, 0, 10), 5);
//   EXPECT_EQ(absl::Uniform<int>(gen, 30, 40), 35);
//
// At this time, only mock distributions supplied within the Abseil random
// library are officially supported.
//
class MockingBitGen : public absl::random_internal::MockingBitGenBase {
 public:
  MockingBitGen() {}

  ~MockingBitGen() override {
    for (const auto& del : deleters_) del();
  }

 private:
  template <typename DistrT, typename... Args>
  using MockFnType =
      ::testing::MockFunction<typename DistrT::result_type(Args...)>;

  // MockingBitGen::Register
  //
  // Register<DistrT, FormatT, ArgTupleT> is the main extension point for
  // extending the MockingBitGen framework. It provides a mechanism to install a
  // mock expectation for the distribution `distr_t` onto the MockingBitGen
  // context.
  //
  // The returned MockFunction<...> type can be used to setup additional
  // distribution parameters of the expectation.
  template <typename DistrT, typename... Args, typename... Ms>
  decltype(std::declval<MockFnType<DistrT, Args...>>().gmock_Call(
      std::declval<Ms>()...))
  Register(Ms&&... matchers) {
    auto& mock =
        mocks_[std::type_index(GetTypeId<DistrT, std::tuple<Args...>>())];

    if (!mock.mock_fn) {
      auto* mock_fn = new MockFnType<DistrT, Args...>;
      mock.mock_fn = mock_fn;
      mock.match_impl = &MatchImpl<DistrT, Args...>;
      deleters_.emplace_back([mock_fn] { delete mock_fn; });
    }

    return static_cast<MockFnType<DistrT, Args...>*>(mock.mock_fn)
        ->gmock_Call(std::forward<Ms>(matchers)...);
  }

  mutable std::vector<std::function<void()>> deleters_;

  using match_impl_fn = void (*)(void* mock_fn, void* t_erased_dist_args,
                                 void* t_erased_result);
  struct MockData {
    void* mock_fn = nullptr;
    match_impl_fn match_impl = nullptr;
  };

  mutable absl::flat_hash_map<std::type_index, MockData> mocks_;

  template <typename DistrT, typename... Args>
  static void MatchImpl(void* mock_fn, void* dist_args, void* result) {
    using result_type = typename DistrT::result_type;
    *static_cast<result_type*>(result) = absl::apply(
        [mock_fn](Args... args) -> result_type {
          return (*static_cast<MockFnType<DistrT, Args...>*>(mock_fn))
              .Call(std::move(args)...);
        },
        *static_cast<std::tuple<Args...>*>(dist_args));
  }

  // Looks for an appropriate mock - Returns the mocked result if one is found.
  // Otherwise, returns a random value generated by the underlying URBG.
  bool CallImpl(const std::type_info& key_type, void* dist_args,
                void* result) override {
    // Trigger a mock, if there exists one that matches `param`.
    auto it = mocks_.find(std::type_index(key_type));
    if (it == mocks_.end()) return false;
    auto* mock_data = static_cast<MockData*>(&it->second);
    mock_data->match_impl(mock_data->mock_fn, dist_args, result);
    return true;
  }

  template <typename, typename>
  friend struct ::absl::random_internal::MockSingleOverload;
  friend struct ::absl::random_internal::DistributionCaller<
      absl::MockingBitGen>;
};

// -----------------------------------------------------------------------------
// Implementation Details Only Below
// -----------------------------------------------------------------------------

namespace random_internal {

template <>
struct DistributionCaller<absl::MockingBitGen> {
  template <typename DistrT, typename... Args>
  static typename DistrT::result_type Call(absl::MockingBitGen* gen,
                                           Args&&... args) {
    return gen->template Call<DistrT>(std::forward<Args>(args)...);
  }
};

}  // namespace random_internal
ABSL_NAMESPACE_END
}  // namespace absl

#endif  // ABSL_RANDOM_MOCKING_BIT_GEN_H_
