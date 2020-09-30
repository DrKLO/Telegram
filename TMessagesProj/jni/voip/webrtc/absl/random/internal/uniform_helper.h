// Copyright 2019 The Abseil Authors.
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
#ifndef ABSL_RANDOM_INTERNAL_UNIFORM_HELPER_H_
#define ABSL_RANDOM_INTERNAL_UNIFORM_HELPER_H_

#include <cmath>
#include <limits>
#include <type_traits>

#include "absl/meta/type_traits.h"

namespace absl {
ABSL_NAMESPACE_BEGIN
template <typename IntType>
class uniform_int_distribution;

template <typename RealType>
class uniform_real_distribution;

// Interval tag types which specify whether the interval is open or closed
// on either boundary.

namespace random_internal {
template <typename T>
struct TagTypeCompare {};

template <typename T>
constexpr bool operator==(TagTypeCompare<T>, TagTypeCompare<T>) {
  // Tags are mono-states. They always compare equal.
  return true;
}
template <typename T>
constexpr bool operator!=(TagTypeCompare<T>, TagTypeCompare<T>) {
  return false;
}

}  // namespace random_internal

struct IntervalClosedClosedTag
    : public random_internal::TagTypeCompare<IntervalClosedClosedTag> {};
struct IntervalClosedOpenTag
    : public random_internal::TagTypeCompare<IntervalClosedOpenTag> {};
struct IntervalOpenClosedTag
    : public random_internal::TagTypeCompare<IntervalOpenClosedTag> {};
struct IntervalOpenOpenTag
    : public random_internal::TagTypeCompare<IntervalOpenOpenTag> {};

namespace random_internal {
// The functions
//    uniform_lower_bound(tag, a, b)
// and
//    uniform_upper_bound(tag, a, b)
// are used as implementation-details for absl::Uniform().
//
// Conceptually,
//    [a, b] == [uniform_lower_bound(IntervalClosedClosed, a, b),
//               uniform_upper_bound(IntervalClosedClosed, a, b)]
//    (a, b) == [uniform_lower_bound(IntervalOpenOpen, a, b),
//               uniform_upper_bound(IntervalOpenOpen, a, b)]
//    [a, b) == [uniform_lower_bound(IntervalClosedOpen, a, b),
//               uniform_upper_bound(IntervalClosedOpen, a, b)]
//    (a, b] == [uniform_lower_bound(IntervalOpenClosed, a, b),
//               uniform_upper_bound(IntervalOpenClosed, a, b)]
//
template <typename IntType, typename Tag>
typename absl::enable_if_t<
    absl::conjunction<
        std::is_integral<IntType>,
        absl::disjunction<std::is_same<Tag, IntervalOpenClosedTag>,
                          std::is_same<Tag, IntervalOpenOpenTag>>>::value,
    IntType>
uniform_lower_bound(Tag, IntType a, IntType) {
  return a + 1;
}

template <typename FloatType, typename Tag>
typename absl::enable_if_t<
    absl::conjunction<
        std::is_floating_point<FloatType>,
        absl::disjunction<std::is_same<Tag, IntervalOpenClosedTag>,
                          std::is_same<Tag, IntervalOpenOpenTag>>>::value,
    FloatType>
uniform_lower_bound(Tag, FloatType a, FloatType b) {
  return std::nextafter(a, b);
}

template <typename NumType, typename Tag>
typename absl::enable_if_t<
    absl::disjunction<std::is_same<Tag, IntervalClosedClosedTag>,
                      std::is_same<Tag, IntervalClosedOpenTag>>::value,
    NumType>
uniform_lower_bound(Tag, NumType a, NumType) {
  return a;
}

template <typename IntType, typename Tag>
typename absl::enable_if_t<
    absl::conjunction<
        std::is_integral<IntType>,
        absl::disjunction<std::is_same<Tag, IntervalClosedOpenTag>,
                          std::is_same<Tag, IntervalOpenOpenTag>>>::value,
    IntType>
uniform_upper_bound(Tag, IntType, IntType b) {
  return b - 1;
}

template <typename FloatType, typename Tag>
typename absl::enable_if_t<
    absl::conjunction<
        std::is_floating_point<FloatType>,
        absl::disjunction<std::is_same<Tag, IntervalClosedOpenTag>,
                          std::is_same<Tag, IntervalOpenOpenTag>>>::value,
    FloatType>
uniform_upper_bound(Tag, FloatType, FloatType b) {
  return b;
}

template <typename IntType, typename Tag>
typename absl::enable_if_t<
    absl::conjunction<
        std::is_integral<IntType>,
        absl::disjunction<std::is_same<Tag, IntervalClosedClosedTag>,
                          std::is_same<Tag, IntervalOpenClosedTag>>>::value,
    IntType>
uniform_upper_bound(Tag, IntType, IntType b) {
  return b;
}

template <typename FloatType, typename Tag>
typename absl::enable_if_t<
    absl::conjunction<
        std::is_floating_point<FloatType>,
        absl::disjunction<std::is_same<Tag, IntervalClosedClosedTag>,
                          std::is_same<Tag, IntervalOpenClosedTag>>>::value,
    FloatType>
uniform_upper_bound(Tag, FloatType, FloatType b) {
  return std::nextafter(b, (std::numeric_limits<FloatType>::max)());
}

template <typename NumType>
using UniformDistribution =
    typename std::conditional<std::is_integral<NumType>::value,
                              absl::uniform_int_distribution<NumType>,
                              absl::uniform_real_distribution<NumType>>::type;

template <typename NumType>
struct UniformDistributionWrapper : public UniformDistribution<NumType> {
  template <typename TagType>
  explicit UniformDistributionWrapper(TagType, NumType lo, NumType hi)
      : UniformDistribution<NumType>(
            uniform_lower_bound<NumType>(TagType{}, lo, hi),
            uniform_upper_bound<NumType>(TagType{}, lo, hi)) {}

  explicit UniformDistributionWrapper(NumType lo, NumType hi)
      : UniformDistribution<NumType>(
            uniform_lower_bound<NumType>(IntervalClosedOpenTag(), lo, hi),
            uniform_upper_bound<NumType>(IntervalClosedOpenTag(), lo, hi)) {}

  explicit UniformDistributionWrapper()
      : UniformDistribution<NumType>(std::numeric_limits<NumType>::lowest(),
                                     (std::numeric_limits<NumType>::max)()) {}
};

}  // namespace random_internal
ABSL_NAMESPACE_END
}  // namespace absl

#endif  // ABSL_RANDOM_INTERNAL_UNIFORM_HELPER_H_
