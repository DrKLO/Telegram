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

#ifndef ABSL_RANDOM_INTERNAL_FAST_UNIFORM_BITS_H_
#define ABSL_RANDOM_INTERNAL_FAST_UNIFORM_BITS_H_

#include <cstddef>
#include <cstdint>
#include <limits>
#include <type_traits>

#include "absl/base/config.h"

namespace absl {
ABSL_NAMESPACE_BEGIN
namespace random_internal {
// Returns true if the input value is zero or a power of two. Useful for
// determining if the range of output values in a URBG
template <typename UIntType>
constexpr bool IsPowerOfTwoOrZero(UIntType n) {
  return (n == 0) || ((n & (n - 1)) == 0);
}

// Computes the length of the range of values producible by the URBG, or returns
// zero if that would encompass the entire range of representable values in
// URBG::result_type.
template <typename URBG>
constexpr typename URBG::result_type RangeSize() {
  using result_type = typename URBG::result_type;
  return ((URBG::max)() == (std::numeric_limits<result_type>::max)() &&
          (URBG::min)() == std::numeric_limits<result_type>::lowest())
             ? result_type{0}
             : (URBG::max)() - (URBG::min)() + result_type{1};
}

template <typename UIntType>
constexpr UIntType LargestPowerOfTwoLessThanOrEqualTo(UIntType n) {
  return n < 2 ? n : 2 * LargestPowerOfTwoLessThanOrEqualTo(n / 2);
}

// Given a URBG generating values in the closed interval [Lo, Hi], returns the
// largest power of two less than or equal to `Hi - Lo + 1`.
template <typename URBG>
constexpr typename URBG::result_type PowerOfTwoSubRangeSize() {
  return LargestPowerOfTwoLessThanOrEqualTo(RangeSize<URBG>());
}

// Computes the floor of the log. (i.e., std::floor(std::log2(N));
template <typename UIntType>
constexpr UIntType IntegerLog2(UIntType n) {
  return (n <= 1) ? 0 : 1 + IntegerLog2(n / 2);
}

// Returns the number of bits of randomness returned through
// `PowerOfTwoVariate(urbg)`.
template <typename URBG>
constexpr size_t NumBits() {
  return RangeSize<URBG>() == 0
             ? std::numeric_limits<typename URBG::result_type>::digits
             : IntegerLog2(PowerOfTwoSubRangeSize<URBG>());
}

// Given a shift value `n`, constructs a mask with exactly the low `n` bits set.
// If `n == 0`, all bits are set.
template <typename UIntType>
constexpr UIntType MaskFromShift(UIntType n) {
  return ((n % std::numeric_limits<UIntType>::digits) == 0)
             ? ~UIntType{0}
             : (UIntType{1} << n) - UIntType{1};
}

// FastUniformBits implements a fast path to acquire uniform independent bits
// from a type which conforms to the [rand.req.urbg] concept.
// Parameterized by:
//  `UIntType`: the result (output) type
//
// The std::independent_bits_engine [rand.adapt.ibits] adaptor can be
// instantiated from an existing generator through a copy or a move. It does
// not, however, facilitate the production of pseudorandom bits from an un-owned
// generator that will outlive the std::independent_bits_engine instance.
template <typename UIntType = uint64_t>
class FastUniformBits {
 public:
  using result_type = UIntType;

  static constexpr result_type(min)() { return 0; }
  static constexpr result_type(max)() {
    return (std::numeric_limits<result_type>::max)();
  }

  template <typename URBG>
  result_type operator()(URBG& g);  // NOLINT(runtime/references)

 private:
  static_assert(std::is_unsigned<UIntType>::value,
                "Class-template FastUniformBits<> must be parameterized using "
                "an unsigned type.");

  // PowerOfTwoVariate() generates a single random variate, always returning a
  // value in the half-open interval `[0, PowerOfTwoSubRangeSize<URBG>())`. If
  // the URBG already generates values in a power-of-two range, the generator
  // itself is used. Otherwise, we use rejection sampling on the largest
  // possible power-of-two-sized subrange.
  struct PowerOfTwoTag {};
  struct RejectionSamplingTag {};
  template <typename URBG>
  static typename URBG::result_type PowerOfTwoVariate(
      URBG& g) {  // NOLINT(runtime/references)
    using tag =
        typename std::conditional<IsPowerOfTwoOrZero(RangeSize<URBG>()),
                                  PowerOfTwoTag, RejectionSamplingTag>::type;
    return PowerOfTwoVariate(g, tag{});
  }

  template <typename URBG>
  static typename URBG::result_type PowerOfTwoVariate(
      URBG& g,  // NOLINT(runtime/references)
      PowerOfTwoTag) {
    return g() - (URBG::min)();
  }

  template <typename URBG>
  static typename URBG::result_type PowerOfTwoVariate(
      URBG& g,  // NOLINT(runtime/references)
      RejectionSamplingTag) {
    // Use rejection sampling to ensure uniformity across the range.
    typename URBG::result_type u;
    do {
      u = g() - (URBG::min)();
    } while (u >= PowerOfTwoSubRangeSize<URBG>());
    return u;
  }

  // Generate() generates a random value, dispatched on whether
  // the underlying URBG must loop over multiple calls or not.
  template <typename URBG>
  result_type Generate(URBG& g,  // NOLINT(runtime/references)
                       std::true_type /* avoid_looping */);

  template <typename URBG>
  result_type Generate(URBG& g,  // NOLINT(runtime/references)
                       std::false_type /* avoid_looping */);
};

template <typename UIntType>
template <typename URBG>
typename FastUniformBits<UIntType>::result_type
FastUniformBits<UIntType>::operator()(URBG& g) {  // NOLINT(runtime/references)
  // kRangeMask is the mask used when sampling variates from the URBG when the
  // width of the URBG range is not a power of 2.
  // Y = (2 ^ kRange) - 1
  static_assert((URBG::max)() > (URBG::min)(),
                "URBG::max and URBG::min may not be equal.");
  using urbg_result_type = typename URBG::result_type;
  constexpr urbg_result_type kRangeMask =
      RangeSize<URBG>() == 0
          ? (std::numeric_limits<urbg_result_type>::max)()
          : static_cast<urbg_result_type>(PowerOfTwoSubRangeSize<URBG>() - 1);
  return Generate(g, std::integral_constant<bool, (kRangeMask >= (max)())>{});
}

template <typename UIntType>
template <typename URBG>
typename FastUniformBits<UIntType>::result_type
FastUniformBits<UIntType>::Generate(URBG& g,  // NOLINT(runtime/references)
                                    std::true_type /* avoid_looping */) {
  // The width of the result_type is less than than the width of the random bits
  // provided by URBG.  Thus, generate a single value and then simply mask off
  // the required bits.

  return PowerOfTwoVariate(g) & (max)();
}

template <typename UIntType>
template <typename URBG>
typename FastUniformBits<UIntType>::result_type
FastUniformBits<UIntType>::Generate(URBG& g,  // NOLINT(runtime/references)
                                    std::false_type /* avoid_looping */) {
  // See [rand.adapt.ibits] for more details on the constants calculated below.
  //
  // It is preferable to use roughly the same number of bits from each generator
  // call, however this is only possible when the number of bits provided by the
  // URBG is a divisor of the number of bits in `result_type`. In all other
  // cases, the number of bits used cannot always be the same, but it can be
  // guaranteed to be off by at most 1. Thus we run two loops, one with a
  // smaller bit-width size (`kSmallWidth`) and one with a larger width size
  // (satisfying `kLargeWidth == kSmallWidth + 1`). The loops are run
  // `kSmallIters` and `kLargeIters` times respectively such
  // that
  //
  //    `kTotalWidth == kSmallIters * kSmallWidth
  //                    + kLargeIters * kLargeWidth`
  //
  // where `kTotalWidth` is the total number of bits in `result_type`.
  //
  constexpr size_t kTotalWidth = std::numeric_limits<result_type>::digits;
  constexpr size_t kUrbgWidth = NumBits<URBG>();
  constexpr size_t kTotalIters =
      kTotalWidth / kUrbgWidth + (kTotalWidth % kUrbgWidth != 0);
  constexpr size_t kSmallWidth = kTotalWidth / kTotalIters;
  constexpr size_t kLargeWidth = kSmallWidth + 1;
  //
  // Because `kLargeWidth == kSmallWidth + 1`, it follows that
  //
  //     `kTotalWidth == kTotalIters * kSmallWidth + kLargeIters`
  //
  // and therefore
  //
  //     `kLargeIters == kTotalWidth % kSmallWidth`
  //
  // Intuitively, each iteration with the large width accounts for one unit
  // of the remainder when `kTotalWidth` is divided by `kSmallWidth`. As
  // mentioned above, if the URBG width is a divisor of `kTotalWidth`, then
  // there would be no need for any large iterations (i.e., one loop would
  // suffice), and indeed, in this case, `kLargeIters` would be zero.
  constexpr size_t kLargeIters = kTotalWidth % kSmallWidth;
  constexpr size_t kSmallIters =
      (kTotalWidth - (kLargeWidth * kLargeIters)) / kSmallWidth;

  static_assert(
      kTotalWidth == kSmallIters * kSmallWidth + kLargeIters * kLargeWidth,
      "Error in looping constant calculations.");

  result_type s = 0;

  constexpr size_t kSmallShift = kSmallWidth % kTotalWidth;
  constexpr result_type kSmallMask = MaskFromShift(result_type{kSmallShift});
  for (size_t n = 0; n < kSmallIters; ++n) {
    s = (s << kSmallShift) +
        (static_cast<result_type>(PowerOfTwoVariate(g)) & kSmallMask);
  }

  constexpr size_t kLargeShift = kLargeWidth % kTotalWidth;
  constexpr result_type kLargeMask = MaskFromShift(result_type{kLargeShift});
  for (size_t n = 0; n < kLargeIters; ++n) {
    s = (s << kLargeShift) +
        (static_cast<result_type>(PowerOfTwoVariate(g)) & kLargeMask);
  }

  static_assert(
      kLargeShift == kSmallShift + 1 ||
          (kLargeShift == 0 &&
           kSmallShift == std::numeric_limits<result_type>::digits - 1),
      "Error in looping constant calculations");

  return s;
}

}  // namespace random_internal
ABSL_NAMESPACE_END
}  // namespace absl

#endif  // ABSL_RANDOM_INTERNAL_FAST_UNIFORM_BITS_H_
