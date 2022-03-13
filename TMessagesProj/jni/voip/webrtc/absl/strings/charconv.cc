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

#include "absl/strings/charconv.h"

#include <algorithm>
#include <cassert>
#include <cmath>
#include <cstring>

#include "absl/base/casts.h"
#include "absl/numeric/bits.h"
#include "absl/numeric/int128.h"
#include "absl/strings/internal/charconv_bigint.h"
#include "absl/strings/internal/charconv_parse.h"

// The macro ABSL_BIT_PACK_FLOATS is defined on x86-64, where IEEE floating
// point numbers have the same endianness in memory as a bitfield struct
// containing the corresponding parts.
//
// When set, we replace calls to ldexp() with manual bit packing, which is
// faster and is unaffected by floating point environment.
#ifdef ABSL_BIT_PACK_FLOATS
#error ABSL_BIT_PACK_FLOATS cannot be directly set
#elif defined(__x86_64__) || defined(_M_X64)
#define ABSL_BIT_PACK_FLOATS 1
#endif

// A note about subnormals:
//
// The code below talks about "normals" and "subnormals".  A normal IEEE float
// has a fixed-width mantissa and power of two exponent.  For example, a normal
// `double` has a 53-bit mantissa.  Because the high bit is always 1, it is not
// stored in the representation.  The implicit bit buys an extra bit of
// resolution in the datatype.
//
// The downside of this scheme is that there is a large gap between DBL_MIN and
// zero.  (Large, at least, relative to the different between DBL_MIN and the
// next representable number).  This gap is softened by the "subnormal" numbers,
// which have the same power-of-two exponent as DBL_MIN, but no implicit 53rd
// bit.  An all-bits-zero exponent in the encoding represents subnormals.  (Zero
// is represented as a subnormal with an all-bits-zero mantissa.)
//
// The code below, in calculations, represents the mantissa as a uint64_t.  The
// end result normally has the 53rd bit set.  It represents subnormals by using
// narrower mantissas.

namespace absl {
ABSL_NAMESPACE_BEGIN
namespace {

template <typename FloatType>
struct FloatTraits;

template <>
struct FloatTraits<double> {
  // The number of mantissa bits in the given float type.  This includes the
  // implied high bit.
  static constexpr int kTargetMantissaBits = 53;

  // The largest supported IEEE exponent, in our integral mantissa
  // representation.
  //
  // If `m` is the largest possible int kTargetMantissaBits bits wide, then
  // m * 2**kMaxExponent is exactly equal to DBL_MAX.
  static constexpr int kMaxExponent = 971;

  // The smallest supported IEEE normal exponent, in our integral mantissa
  // representation.
  //
  // If `m` is the smallest possible int kTargetMantissaBits bits wide, then
  // m * 2**kMinNormalExponent is exactly equal to DBL_MIN.
  static constexpr int kMinNormalExponent = -1074;

  static double MakeNan(const char* tagp) {
    // Support nan no matter which namespace it's in.  Some platforms
    // incorrectly don't put it in namespace std.
    using namespace std;  // NOLINT
    return nan(tagp);
  }

  // Builds a nonzero floating point number out of the provided parts.
  //
  // This is intended to do the same operation as ldexp(mantissa, exponent),
  // but using purely integer math, to avoid -ffastmath and floating
  // point environment issues.  Using type punning is also faster. We fall back
  // to ldexp on a per-platform basis for portability.
  //
  // `exponent` must be between kMinNormalExponent and kMaxExponent.
  //
  // `mantissa` must either be exactly kTargetMantissaBits wide, in which case
  // a normal value is made, or it must be less narrow than that, in which case
  // `exponent` must be exactly kMinNormalExponent, and a subnormal value is
  // made.
  static double Make(uint64_t mantissa, int exponent, bool sign) {
#ifndef ABSL_BIT_PACK_FLOATS
    // Support ldexp no matter which namespace it's in.  Some platforms
    // incorrectly don't put it in namespace std.
    using namespace std;  // NOLINT
    return sign ? -ldexp(mantissa, exponent) : ldexp(mantissa, exponent);
#else
    constexpr uint64_t kMantissaMask =
        (uint64_t{1} << (kTargetMantissaBits - 1)) - 1;
    uint64_t dbl = static_cast<uint64_t>(sign) << 63;
    if (mantissa > kMantissaMask) {
      // Normal value.
      // Adjust by 1023 for the exponent representation bias, and an additional
      // 52 due to the implied decimal point in the IEEE mantissa represenation.
      dbl += uint64_t{exponent + 1023u + kTargetMantissaBits - 1} << 52;
      mantissa &= kMantissaMask;
    } else {
      // subnormal value
      assert(exponent == kMinNormalExponent);
    }
    dbl += mantissa;
    return absl::bit_cast<double>(dbl);
#endif  // ABSL_BIT_PACK_FLOATS
  }
};

// Specialization of floating point traits for the `float` type.  See the
// FloatTraits<double> specialization above for meaning of each of the following
// members and methods.
template <>
struct FloatTraits<float> {
  static constexpr int kTargetMantissaBits = 24;
  static constexpr int kMaxExponent = 104;
  static constexpr int kMinNormalExponent = -149;
  static float MakeNan(const char* tagp) {
    // Support nanf no matter which namespace it's in.  Some platforms
    // incorrectly don't put it in namespace std.
    using namespace std;  // NOLINT
    return nanf(tagp);
  }
  static float Make(uint32_t mantissa, int exponent, bool sign) {
#ifndef ABSL_BIT_PACK_FLOATS
    // Support ldexpf no matter which namespace it's in.  Some platforms
    // incorrectly don't put it in namespace std.
    using namespace std;  // NOLINT
    return sign ? -ldexpf(mantissa, exponent) : ldexpf(mantissa, exponent);
#else
    constexpr uint32_t kMantissaMask =
        (uint32_t{1} << (kTargetMantissaBits - 1)) - 1;
    uint32_t flt = static_cast<uint32_t>(sign) << 31;
    if (mantissa > kMantissaMask) {
      // Normal value.
      // Adjust by 127 for the exponent representation bias, and an additional
      // 23 due to the implied decimal point in the IEEE mantissa represenation.
      flt += uint32_t{exponent + 127u + kTargetMantissaBits - 1} << 23;
      mantissa &= kMantissaMask;
    } else {
      // subnormal value
      assert(exponent == kMinNormalExponent);
    }
    flt += mantissa;
    return absl::bit_cast<float>(flt);
#endif  // ABSL_BIT_PACK_FLOATS
  }
};

// Decimal-to-binary conversions require coercing powers of 10 into a mantissa
// and a power of 2.  The two helper functions Power10Mantissa(n) and
// Power10Exponent(n) perform this task.  Together, these represent a hand-
// rolled floating point value which is equal to or just less than 10**n.
//
// The return values satisfy two range guarantees:
//
//   Power10Mantissa(n) * 2**Power10Exponent(n) <= 10**n
//     < (Power10Mantissa(n) + 1) * 2**Power10Exponent(n)
//
//   2**63 <= Power10Mantissa(n) < 2**64.
//
// Lookups into the power-of-10 table must first check the Power10Overflow() and
// Power10Underflow() functions, to avoid out-of-bounds table access.
//
// Indexes into these tables are biased by -kPower10TableMin, and the table has
// values in the range [kPower10TableMin, kPower10TableMax].
extern const uint64_t kPower10MantissaTable[];
extern const int16_t kPower10ExponentTable[];

// The smallest allowed value for use with the Power10Mantissa() and
// Power10Exponent() functions below.  (If a smaller exponent is needed in
// calculations, the end result is guaranteed to underflow.)
constexpr int kPower10TableMin = -342;

// The largest allowed value for use with the Power10Mantissa() and
// Power10Exponent() functions below.  (If a smaller exponent is needed in
// calculations, the end result is guaranteed to overflow.)
constexpr int kPower10TableMax = 308;

uint64_t Power10Mantissa(int n) {
  return kPower10MantissaTable[n - kPower10TableMin];
}

int Power10Exponent(int n) {
  return kPower10ExponentTable[n - kPower10TableMin];
}

// Returns true if n is large enough that 10**n always results in an IEEE
// overflow.
bool Power10Overflow(int n) { return n > kPower10TableMax; }

// Returns true if n is small enough that 10**n times a ParsedFloat mantissa
// always results in an IEEE underflow.
bool Power10Underflow(int n) { return n < kPower10TableMin; }

// Returns true if Power10Mantissa(n) * 2**Power10Exponent(n) is exactly equal
// to 10**n numerically.  Put another way, this returns true if there is no
// truncation error in Power10Mantissa(n).
bool Power10Exact(int n) { return n >= 0 && n <= 27; }

// Sentinel exponent values for representing numbers too large or too close to
// zero to represent in a double.
constexpr int kOverflow = 99999;
constexpr int kUnderflow = -99999;

// Struct representing the calculated conversion result of a positive (nonzero)
// floating point number.
//
// The calculated number is mantissa * 2**exponent (mantissa is treated as an
// integer.)  `mantissa` is chosen to be the correct width for the IEEE float
// representation being calculated.  (`mantissa` will always have the same bit
// width for normal values, and narrower bit widths for subnormals.)
//
// If the result of conversion was an underflow or overflow, exponent is set
// to kUnderflow or kOverflow.
struct CalculatedFloat {
  uint64_t mantissa = 0;
  int exponent = 0;
};

// Returns the bit width of the given uint128.  (Equivalently, returns 128
// minus the number of leading zero bits.)
unsigned BitWidth(uint128 value) {
  if (Uint128High64(value) == 0) {
    return static_cast<unsigned>(bit_width(Uint128Low64(value)));
  }
  return 128 - countl_zero(Uint128High64(value));
}

// Calculates how far to the right a mantissa needs to be shifted to create a
// properly adjusted mantissa for an IEEE floating point number.
//
// `mantissa_width` is the bit width of the mantissa to be shifted, and
// `binary_exponent` is the exponent of the number before the shift.
//
// This accounts for subnormal values, and will return a larger-than-normal
// shift if binary_exponent would otherwise be too low.
template <typename FloatType>
int NormalizedShiftSize(int mantissa_width, int binary_exponent) {
  const int normal_shift =
      mantissa_width - FloatTraits<FloatType>::kTargetMantissaBits;
  const int minimum_shift =
      FloatTraits<FloatType>::kMinNormalExponent - binary_exponent;
  return std::max(normal_shift, minimum_shift);
}

// Right shifts a uint128 so that it has the requested bit width.  (The
// resulting value will have 128 - bit_width leading zeroes.)  The initial
// `value` must be wider than the requested bit width.
//
// Returns the number of bits shifted.
int TruncateToBitWidth(int bit_width, uint128* value) {
  const int current_bit_width = BitWidth(*value);
  const int shift = current_bit_width - bit_width;
  *value >>= shift;
  return shift;
}

// Checks if the given ParsedFloat represents one of the edge cases that are
// not dependent on number base: zero, infinity, or NaN.  If so, sets *value
// the appropriate double, and returns true.
template <typename FloatType>
bool HandleEdgeCase(const strings_internal::ParsedFloat& input, bool negative,
                    FloatType* value) {
  if (input.type == strings_internal::FloatType::kNan) {
    // A bug in both clang and gcc would cause the compiler to optimize away the
    // buffer we are building below.  Declaring the buffer volatile avoids the
    // issue, and has no measurable performance impact in microbenchmarks.
    //
    // https://bugs.llvm.org/show_bug.cgi?id=37778
    // https://gcc.gnu.org/bugzilla/show_bug.cgi?id=86113
    constexpr ptrdiff_t kNanBufferSize = 128;
    volatile char n_char_sequence[kNanBufferSize];
    if (input.subrange_begin == nullptr) {
      n_char_sequence[0] = '\0';
    } else {
      ptrdiff_t nan_size = input.subrange_end - input.subrange_begin;
      nan_size = std::min(nan_size, kNanBufferSize - 1);
      std::copy_n(input.subrange_begin, nan_size, n_char_sequence);
      n_char_sequence[nan_size] = '\0';
    }
    char* nan_argument = const_cast<char*>(n_char_sequence);
    *value = negative ? -FloatTraits<FloatType>::MakeNan(nan_argument)
                      : FloatTraits<FloatType>::MakeNan(nan_argument);
    return true;
  }
  if (input.type == strings_internal::FloatType::kInfinity) {
    *value = negative ? -std::numeric_limits<FloatType>::infinity()
                      : std::numeric_limits<FloatType>::infinity();
    return true;
  }
  if (input.mantissa == 0) {
    *value = negative ? -0.0 : 0.0;
    return true;
  }
  return false;
}

// Given a CalculatedFloat result of a from_chars conversion, generate the
// correct output values.
//
// CalculatedFloat can represent an underflow or overflow, in which case the
// error code in *result is set.  Otherwise, the calculated floating point
// number is stored in *value.
template <typename FloatType>
void EncodeResult(const CalculatedFloat& calculated, bool negative,
                  absl::from_chars_result* result, FloatType* value) {
  if (calculated.exponent == kOverflow) {
    result->ec = std::errc::result_out_of_range;
    *value = negative ? -std::numeric_limits<FloatType>::max()
                      : std::numeric_limits<FloatType>::max();
    return;
  } else if (calculated.mantissa == 0 || calculated.exponent == kUnderflow) {
    result->ec = std::errc::result_out_of_range;
    *value = negative ? -0.0 : 0.0;
    return;
  }
  *value = FloatTraits<FloatType>::Make(calculated.mantissa,
                                        calculated.exponent, negative);
}

// Returns the given uint128 shifted to the right by `shift` bits, and rounds
// the remaining bits using round_to_nearest logic.  The value is returned as a
// uint64_t, since this is the type used by this library for storing calculated
// floating point mantissas.
//
// It is expected that the width of the input value shifted by `shift` will
// be the correct bit-width for the target mantissa, which is strictly narrower
// than a uint64_t.
//
// If `input_exact` is false, then a nonzero error epsilon is assumed.  For
// rounding purposes, the true value being rounded is strictly greater than the
// input value.  The error may represent a single lost carry bit.
//
// When input_exact, shifted bits of the form 1000000... represent a tie, which
// is broken by rounding to even -- the rounding direction is chosen so the low
// bit of the returned value is 0.
//
// When !input_exact, shifted bits of the form 10000000... represent a value
// strictly greater than one half (due to the error epsilon), and so ties are
// always broken by rounding up.
//
// When !input_exact, shifted bits of the form 01111111... are uncertain;
// the true value may or may not be greater than 10000000..., due to the
// possible lost carry bit.  The correct rounding direction is unknown.  In this
// case, the result is rounded down, and `output_exact` is set to false.
//
// Zero and negative values of `shift` are accepted, in which case the word is
// shifted left, as necessary.
uint64_t ShiftRightAndRound(uint128 value, int shift, bool input_exact,
                            bool* output_exact) {
  if (shift <= 0) {
    *output_exact = input_exact;
    return static_cast<uint64_t>(value << -shift);
  }
  if (shift >= 128) {
    // Exponent is so small that we are shifting away all significant bits.
    // Answer will not be representable, even as a subnormal, so return a zero
    // mantissa (which represents underflow).
    *output_exact = true;
    return 0;
  }

  *output_exact = true;
  const uint128 shift_mask = (uint128(1) << shift) - 1;
  const uint128 halfway_point = uint128(1) << (shift - 1);

  const uint128 shifted_bits = value & shift_mask;
  value >>= shift;
  if (shifted_bits > halfway_point) {
    // Shifted bits greater than 10000... require rounding up.
    return static_cast<uint64_t>(value + 1);
  }
  if (shifted_bits == halfway_point) {
    // In exact mode, shifted bits of 10000... mean we're exactly halfway
    // between two numbers, and we must round to even.  So only round up if
    // the low bit of `value` is set.
    //
    // In inexact mode, the nonzero error means the actual value is greater
    // than the halfway point and we must alway round up.
    if ((value & 1) == 1 || !input_exact) {
      ++value;
    }
    return static_cast<uint64_t>(value);
  }
  if (!input_exact && shifted_bits == halfway_point - 1) {
    // Rounding direction is unclear, due to error.
    *output_exact = false;
  }
  // Otherwise, round down.
  return static_cast<uint64_t>(value);
}

// Checks if a floating point guess needs to be rounded up, using high precision
// math.
//
// `guess_mantissa` and `guess_exponent` represent a candidate guess for the
// number represented by `parsed_decimal`.
//
// The exact number represented by `parsed_decimal` must lie between the two
// numbers:
//   A = `guess_mantissa * 2**guess_exponent`
//   B = `(guess_mantissa + 1) * 2**guess_exponent`
//
// This function returns false if `A` is the better guess, and true if `B` is
// the better guess, with rounding ties broken by rounding to even.
bool MustRoundUp(uint64_t guess_mantissa, int guess_exponent,
                 const strings_internal::ParsedFloat& parsed_decimal) {
  // 768 is the number of digits needed in the worst case.  We could determine a
  // better limit dynamically based on the value of parsed_decimal.exponent.
  // This would optimize pathological input cases only.  (Sane inputs won't have
  // hundreds of digits of mantissa.)
  absl::strings_internal::BigUnsigned<84> exact_mantissa;
  int exact_exponent = exact_mantissa.ReadFloatMantissa(parsed_decimal, 768);

  // Adjust the `guess` arguments to be halfway between A and B.
  guess_mantissa = guess_mantissa * 2 + 1;
  guess_exponent -= 1;

  // In our comparison:
  // lhs = exact = exact_mantissa * 10**exact_exponent
  //             = exact_mantissa * 5**exact_exponent * 2**exact_exponent
  // rhs = guess = guess_mantissa * 2**guess_exponent
  //
  // Because we are doing integer math, we can't directly deal with negative
  // exponents.  We instead move these to the other side of the inequality.
  absl::strings_internal::BigUnsigned<84>& lhs = exact_mantissa;
  int comparison;
  if (exact_exponent >= 0) {
    lhs.MultiplyByFiveToTheNth(exact_exponent);
    absl::strings_internal::BigUnsigned<84> rhs(guess_mantissa);
    // There are powers of 2 on both sides of the inequality; reduce this to
    // a single bit-shift.
    if (exact_exponent > guess_exponent) {
      lhs.ShiftLeft(exact_exponent - guess_exponent);
    } else {
      rhs.ShiftLeft(guess_exponent - exact_exponent);
    }
    comparison = Compare(lhs, rhs);
  } else {
    // Move the power of 5 to the other side of the equation, giving us:
    // lhs = exact_mantissa * 2**exact_exponent
    // rhs = guess_mantissa * 5**(-exact_exponent) * 2**guess_exponent
    absl::strings_internal::BigUnsigned<84> rhs =
        absl::strings_internal::BigUnsigned<84>::FiveToTheNth(-exact_exponent);
    rhs.MultiplyBy(guess_mantissa);
    if (exact_exponent > guess_exponent) {
      lhs.ShiftLeft(exact_exponent - guess_exponent);
    } else {
      rhs.ShiftLeft(guess_exponent - exact_exponent);
    }
    comparison = Compare(lhs, rhs);
  }
  if (comparison < 0) {
    return false;
  } else if (comparison > 0) {
    return true;
  } else {
    // When lhs == rhs, the decimal input is exactly between A and B.
    // Round towards even -- round up only if the low bit of the initial
    // `guess_mantissa` was a 1.  We shifted guess_mantissa left 1 bit at
    // the beginning of this function, so test the 2nd bit here.
    return (guess_mantissa & 2) == 2;
  }
}

// Constructs a CalculatedFloat from a given mantissa and exponent, but
// with the following normalizations applied:
//
// If rounding has caused mantissa to increase just past the allowed bit
// width, shift and adjust exponent.
//
// If exponent is too high, sets kOverflow.
//
// If mantissa is zero (representing a non-zero value not representable, even
// as a subnormal), sets kUnderflow.
template <typename FloatType>
CalculatedFloat CalculatedFloatFromRawValues(uint64_t mantissa, int exponent) {
  CalculatedFloat result;
  if (mantissa == uint64_t{1} << FloatTraits<FloatType>::kTargetMantissaBits) {
    mantissa >>= 1;
    exponent += 1;
  }
  if (exponent > FloatTraits<FloatType>::kMaxExponent) {
    result.exponent = kOverflow;
  } else if (mantissa == 0) {
    result.exponent = kUnderflow;
  } else {
    result.exponent = exponent;
    result.mantissa = mantissa;
  }
  return result;
}

template <typename FloatType>
CalculatedFloat CalculateFromParsedHexadecimal(
    const strings_internal::ParsedFloat& parsed_hex) {
  uint64_t mantissa = parsed_hex.mantissa;
  int exponent = parsed_hex.exponent;
  auto mantissa_width = static_cast<unsigned>(bit_width(mantissa));
  const int shift = NormalizedShiftSize<FloatType>(mantissa_width, exponent);
  bool result_exact;
  exponent += shift;
  mantissa = ShiftRightAndRound(mantissa, shift,
                                /* input exact= */ true, &result_exact);
  // ParseFloat handles rounding in the hexadecimal case, so we don't have to
  // check `result_exact` here.
  return CalculatedFloatFromRawValues<FloatType>(mantissa, exponent);
}

template <typename FloatType>
CalculatedFloat CalculateFromParsedDecimal(
    const strings_internal::ParsedFloat& parsed_decimal) {
  CalculatedFloat result;

  // Large or small enough decimal exponents will always result in overflow
  // or underflow.
  if (Power10Underflow(parsed_decimal.exponent)) {
    result.exponent = kUnderflow;
    return result;
  } else if (Power10Overflow(parsed_decimal.exponent)) {
    result.exponent = kOverflow;
    return result;
  }

  // Otherwise convert our power of 10 into a power of 2 times an integer
  // mantissa, and multiply this by our parsed decimal mantissa.
  uint128 wide_binary_mantissa = parsed_decimal.mantissa;
  wide_binary_mantissa *= Power10Mantissa(parsed_decimal.exponent);
  int binary_exponent = Power10Exponent(parsed_decimal.exponent);

  // Discard bits that are inaccurate due to truncation error.  The magic
  // `mantissa_width` constants below are justified in
  // https://abseil.io/about/design/charconv. They represent the number of bits
  // in `wide_binary_mantissa` that are guaranteed to be unaffected by error
  // propagation.
  bool mantissa_exact;
  int mantissa_width;
  if (parsed_decimal.subrange_begin) {
    // Truncated mantissa
    mantissa_width = 58;
    mantissa_exact = false;
    binary_exponent +=
        TruncateToBitWidth(mantissa_width, &wide_binary_mantissa);
  } else if (!Power10Exact(parsed_decimal.exponent)) {
    // Exact mantissa, truncated power of ten
    mantissa_width = 63;
    mantissa_exact = false;
    binary_exponent +=
        TruncateToBitWidth(mantissa_width, &wide_binary_mantissa);
  } else {
    // Product is exact
    mantissa_width = BitWidth(wide_binary_mantissa);
    mantissa_exact = true;
  }

  // Shift into an FloatType-sized mantissa, and round to nearest.
  const int shift =
      NormalizedShiftSize<FloatType>(mantissa_width, binary_exponent);
  bool result_exact;
  binary_exponent += shift;
  uint64_t binary_mantissa = ShiftRightAndRound(wide_binary_mantissa, shift,
                                                mantissa_exact, &result_exact);
  if (!result_exact) {
    // We could not determine the rounding direction using int128 math.  Use
    // full resolution math instead.
    if (MustRoundUp(binary_mantissa, binary_exponent, parsed_decimal)) {
      binary_mantissa += 1;
    }
  }

  return CalculatedFloatFromRawValues<FloatType>(binary_mantissa,
                                                 binary_exponent);
}

template <typename FloatType>
from_chars_result FromCharsImpl(const char* first, const char* last,
                                FloatType& value, chars_format fmt_flags) {
  from_chars_result result;
  result.ptr = first;  // overwritten on successful parse
  result.ec = std::errc();

  bool negative = false;
  if (first != last && *first == '-') {
    ++first;
    negative = true;
  }
  // If the `hex` flag is *not* set, then we will accept a 0x prefix and try
  // to parse a hexadecimal float.
  if ((fmt_flags & chars_format::hex) == chars_format{} && last - first >= 2 &&
      *first == '0' && (first[1] == 'x' || first[1] == 'X')) {
    const char* hex_first = first + 2;
    strings_internal::ParsedFloat hex_parse =
        strings_internal::ParseFloat<16>(hex_first, last, fmt_flags);
    if (hex_parse.end == nullptr ||
        hex_parse.type != strings_internal::FloatType::kNumber) {
      // Either we failed to parse a hex float after the "0x", or we read
      // "0xinf" or "0xnan" which we don't want to match.
      //
      // However, a string that begins with "0x" also begins with "0", which
      // is normally a valid match for the number zero.  So we want these
      // strings to match zero unless fmt_flags is `scientific`.  (This flag
      // means an exponent is required, which the string "0" does not have.)
      if (fmt_flags == chars_format::scientific) {
        result.ec = std::errc::invalid_argument;
      } else {
        result.ptr = first + 1;
        value = negative ? -0.0 : 0.0;
      }
      return result;
    }
    // We matched a value.
    result.ptr = hex_parse.end;
    if (HandleEdgeCase(hex_parse, negative, &value)) {
      return result;
    }
    CalculatedFloat calculated =
        CalculateFromParsedHexadecimal<FloatType>(hex_parse);
    EncodeResult(calculated, negative, &result, &value);
    return result;
  }
  // Otherwise, we choose the number base based on the flags.
  if ((fmt_flags & chars_format::hex) == chars_format::hex) {
    strings_internal::ParsedFloat hex_parse =
        strings_internal::ParseFloat<16>(first, last, fmt_flags);
    if (hex_parse.end == nullptr) {
      result.ec = std::errc::invalid_argument;
      return result;
    }
    result.ptr = hex_parse.end;
    if (HandleEdgeCase(hex_parse, negative, &value)) {
      return result;
    }
    CalculatedFloat calculated =
        CalculateFromParsedHexadecimal<FloatType>(hex_parse);
    EncodeResult(calculated, negative, &result, &value);
    return result;
  } else {
    strings_internal::ParsedFloat decimal_parse =
        strings_internal::ParseFloat<10>(first, last, fmt_flags);
    if (decimal_parse.end == nullptr) {
      result.ec = std::errc::invalid_argument;
      return result;
    }
    result.ptr = decimal_parse.end;
    if (HandleEdgeCase(decimal_parse, negative, &value)) {
      return result;
    }
    CalculatedFloat calculated =
        CalculateFromParsedDecimal<FloatType>(decimal_parse);
    EncodeResult(calculated, negative, &result, &value);
    return result;
  }
}
}  // namespace

from_chars_result from_chars(const char* first, const char* last, double& value,
                             chars_format fmt) {
  return FromCharsImpl(first, last, value, fmt);
}

from_chars_result from_chars(const char* first, const char* last, float& value,
                             chars_format fmt) {
  return FromCharsImpl(first, last, value, fmt);
}

namespace {

// Table of powers of 10, from kPower10TableMin to kPower10TableMax.
//
// kPower10MantissaTable[i - kPower10TableMin] stores the 64-bit mantissa (high
// bit always on), and kPower10ExponentTable[i - kPower10TableMin] stores the
// power-of-two exponent.  For a given number i, this gives the unique mantissa
// and exponent such that mantissa * 2**exponent <= 10**i < (mantissa + 1) *
// 2**exponent.

const uint64_t kPower10MantissaTable[] = {
    0xeef453d6923bd65aU, 0x9558b4661b6565f8U, 0xbaaee17fa23ebf76U,
    0xe95a99df8ace6f53U, 0x91d8a02bb6c10594U, 0xb64ec836a47146f9U,
    0xe3e27a444d8d98b7U, 0x8e6d8c6ab0787f72U, 0xb208ef855c969f4fU,
    0xde8b2b66b3bc4723U, 0x8b16fb203055ac76U, 0xaddcb9e83c6b1793U,
    0xd953e8624b85dd78U, 0x87d4713d6f33aa6bU, 0xa9c98d8ccb009506U,
    0xd43bf0effdc0ba48U, 0x84a57695fe98746dU, 0xa5ced43b7e3e9188U,
    0xcf42894a5dce35eaU, 0x818995ce7aa0e1b2U, 0xa1ebfb4219491a1fU,
    0xca66fa129f9b60a6U, 0xfd00b897478238d0U, 0x9e20735e8cb16382U,
    0xc5a890362fddbc62U, 0xf712b443bbd52b7bU, 0x9a6bb0aa55653b2dU,
    0xc1069cd4eabe89f8U, 0xf148440a256e2c76U, 0x96cd2a865764dbcaU,
    0xbc807527ed3e12bcU, 0xeba09271e88d976bU, 0x93445b8731587ea3U,
    0xb8157268fdae9e4cU, 0xe61acf033d1a45dfU, 0x8fd0c16206306babU,
    0xb3c4f1ba87bc8696U, 0xe0b62e2929aba83cU, 0x8c71dcd9ba0b4925U,
    0xaf8e5410288e1b6fU, 0xdb71e91432b1a24aU, 0x892731ac9faf056eU,
    0xab70fe17c79ac6caU, 0xd64d3d9db981787dU, 0x85f0468293f0eb4eU,
    0xa76c582338ed2621U, 0xd1476e2c07286faaU, 0x82cca4db847945caU,
    0xa37fce126597973cU, 0xcc5fc196fefd7d0cU, 0xff77b1fcbebcdc4fU,
    0x9faacf3df73609b1U, 0xc795830d75038c1dU, 0xf97ae3d0d2446f25U,
    0x9becce62836ac577U, 0xc2e801fb244576d5U, 0xf3a20279ed56d48aU,
    0x9845418c345644d6U, 0xbe5691ef416bd60cU, 0xedec366b11c6cb8fU,
    0x94b3a202eb1c3f39U, 0xb9e08a83a5e34f07U, 0xe858ad248f5c22c9U,
    0x91376c36d99995beU, 0xb58547448ffffb2dU, 0xe2e69915b3fff9f9U,
    0x8dd01fad907ffc3bU, 0xb1442798f49ffb4aU, 0xdd95317f31c7fa1dU,
    0x8a7d3eef7f1cfc52U, 0xad1c8eab5ee43b66U, 0xd863b256369d4a40U,
    0x873e4f75e2224e68U, 0xa90de3535aaae202U, 0xd3515c2831559a83U,
    0x8412d9991ed58091U, 0xa5178fff668ae0b6U, 0xce5d73ff402d98e3U,
    0x80fa687f881c7f8eU, 0xa139029f6a239f72U, 0xc987434744ac874eU,
    0xfbe9141915d7a922U, 0x9d71ac8fada6c9b5U, 0xc4ce17b399107c22U,
    0xf6019da07f549b2bU, 0x99c102844f94e0fbU, 0xc0314325637a1939U,
    0xf03d93eebc589f88U, 0x96267c7535b763b5U, 0xbbb01b9283253ca2U,
    0xea9c227723ee8bcbU, 0x92a1958a7675175fU, 0xb749faed14125d36U,
    0xe51c79a85916f484U, 0x8f31cc0937ae58d2U, 0xb2fe3f0b8599ef07U,
    0xdfbdcece67006ac9U, 0x8bd6a141006042bdU, 0xaecc49914078536dU,
    0xda7f5bf590966848U, 0x888f99797a5e012dU, 0xaab37fd7d8f58178U,
    0xd5605fcdcf32e1d6U, 0x855c3be0a17fcd26U, 0xa6b34ad8c9dfc06fU,
    0xd0601d8efc57b08bU, 0x823c12795db6ce57U, 0xa2cb1717b52481edU,
    0xcb7ddcdda26da268U, 0xfe5d54150b090b02U, 0x9efa548d26e5a6e1U,
    0xc6b8e9b0709f109aU, 0xf867241c8cc6d4c0U, 0x9b407691d7fc44f8U,
    0xc21094364dfb5636U, 0xf294b943e17a2bc4U, 0x979cf3ca6cec5b5aU,
    0xbd8430bd08277231U, 0xece53cec4a314ebdU, 0x940f4613ae5ed136U,
    0xb913179899f68584U, 0xe757dd7ec07426e5U, 0x9096ea6f3848984fU,
    0xb4bca50b065abe63U, 0xe1ebce4dc7f16dfbU, 0x8d3360f09cf6e4bdU,
    0xb080392cc4349decU, 0xdca04777f541c567U, 0x89e42caaf9491b60U,
    0xac5d37d5b79b6239U, 0xd77485cb25823ac7U, 0x86a8d39ef77164bcU,
    0xa8530886b54dbdebU, 0xd267caa862a12d66U, 0x8380dea93da4bc60U,
    0xa46116538d0deb78U, 0xcd795be870516656U, 0x806bd9714632dff6U,
    0xa086cfcd97bf97f3U, 0xc8a883c0fdaf7df0U, 0xfad2a4b13d1b5d6cU,
    0x9cc3a6eec6311a63U, 0xc3f490aa77bd60fcU, 0xf4f1b4d515acb93bU,
    0x991711052d8bf3c5U, 0xbf5cd54678eef0b6U, 0xef340a98172aace4U,
    0x9580869f0e7aac0eU, 0xbae0a846d2195712U, 0xe998d258869facd7U,
    0x91ff83775423cc06U, 0xb67f6455292cbf08U, 0xe41f3d6a7377eecaU,
    0x8e938662882af53eU, 0xb23867fb2a35b28dU, 0xdec681f9f4c31f31U,
    0x8b3c113c38f9f37eU, 0xae0b158b4738705eU, 0xd98ddaee19068c76U,
    0x87f8a8d4cfa417c9U, 0xa9f6d30a038d1dbcU, 0xd47487cc8470652bU,
    0x84c8d4dfd2c63f3bU, 0xa5fb0a17c777cf09U, 0xcf79cc9db955c2ccU,
    0x81ac1fe293d599bfU, 0xa21727db38cb002fU, 0xca9cf1d206fdc03bU,
    0xfd442e4688bd304aU, 0x9e4a9cec15763e2eU, 0xc5dd44271ad3cdbaU,
    0xf7549530e188c128U, 0x9a94dd3e8cf578b9U, 0xc13a148e3032d6e7U,
    0xf18899b1bc3f8ca1U, 0x96f5600f15a7b7e5U, 0xbcb2b812db11a5deU,
    0xebdf661791d60f56U, 0x936b9fcebb25c995U, 0xb84687c269ef3bfbU,
    0xe65829b3046b0afaU, 0x8ff71a0fe2c2e6dcU, 0xb3f4e093db73a093U,
    0xe0f218b8d25088b8U, 0x8c974f7383725573U, 0xafbd2350644eeacfU,
    0xdbac6c247d62a583U, 0x894bc396ce5da772U, 0xab9eb47c81f5114fU,
    0xd686619ba27255a2U, 0x8613fd0145877585U, 0xa798fc4196e952e7U,
    0xd17f3b51fca3a7a0U, 0x82ef85133de648c4U, 0xa3ab66580d5fdaf5U,
    0xcc963fee10b7d1b3U, 0xffbbcfe994e5c61fU, 0x9fd561f1fd0f9bd3U,
    0xc7caba6e7c5382c8U, 0xf9bd690a1b68637bU, 0x9c1661a651213e2dU,
    0xc31bfa0fe5698db8U, 0xf3e2f893dec3f126U, 0x986ddb5c6b3a76b7U,
    0xbe89523386091465U, 0xee2ba6c0678b597fU, 0x94db483840b717efU,
    0xba121a4650e4ddebU, 0xe896a0d7e51e1566U, 0x915e2486ef32cd60U,
    0xb5b5ada8aaff80b8U, 0xe3231912d5bf60e6U, 0x8df5efabc5979c8fU,
    0xb1736b96b6fd83b3U, 0xddd0467c64bce4a0U, 0x8aa22c0dbef60ee4U,
    0xad4ab7112eb3929dU, 0xd89d64d57a607744U, 0x87625f056c7c4a8bU,
    0xa93af6c6c79b5d2dU, 0xd389b47879823479U, 0x843610cb4bf160cbU,
    0xa54394fe1eedb8feU, 0xce947a3da6a9273eU, 0x811ccc668829b887U,
    0xa163ff802a3426a8U, 0xc9bcff6034c13052U, 0xfc2c3f3841f17c67U,
    0x9d9ba7832936edc0U, 0xc5029163f384a931U, 0xf64335bcf065d37dU,
    0x99ea0196163fa42eU, 0xc06481fb9bcf8d39U, 0xf07da27a82c37088U,
    0x964e858c91ba2655U, 0xbbe226efb628afeaU, 0xeadab0aba3b2dbe5U,
    0x92c8ae6b464fc96fU, 0xb77ada0617e3bbcbU, 0xe55990879ddcaabdU,
    0x8f57fa54c2a9eab6U, 0xb32df8e9f3546564U, 0xdff9772470297ebdU,
    0x8bfbea76c619ef36U, 0xaefae51477a06b03U, 0xdab99e59958885c4U,
    0x88b402f7fd75539bU, 0xaae103b5fcd2a881U, 0xd59944a37c0752a2U,
    0x857fcae62d8493a5U, 0xa6dfbd9fb8e5b88eU, 0xd097ad07a71f26b2U,
    0x825ecc24c873782fU, 0xa2f67f2dfa90563bU, 0xcbb41ef979346bcaU,
    0xfea126b7d78186bcU, 0x9f24b832e6b0f436U, 0xc6ede63fa05d3143U,
    0xf8a95fcf88747d94U, 0x9b69dbe1b548ce7cU, 0xc24452da229b021bU,
    0xf2d56790ab41c2a2U, 0x97c560ba6b0919a5U, 0xbdb6b8e905cb600fU,
    0xed246723473e3813U, 0x9436c0760c86e30bU, 0xb94470938fa89bceU,
    0xe7958cb87392c2c2U, 0x90bd77f3483bb9b9U, 0xb4ecd5f01a4aa828U,
    0xe2280b6c20dd5232U, 0x8d590723948a535fU, 0xb0af48ec79ace837U,
    0xdcdb1b2798182244U, 0x8a08f0f8bf0f156bU, 0xac8b2d36eed2dac5U,
    0xd7adf884aa879177U, 0x86ccbb52ea94baeaU, 0xa87fea27a539e9a5U,
    0xd29fe4b18e88640eU, 0x83a3eeeef9153e89U, 0xa48ceaaab75a8e2bU,
    0xcdb02555653131b6U, 0x808e17555f3ebf11U, 0xa0b19d2ab70e6ed6U,
    0xc8de047564d20a8bU, 0xfb158592be068d2eU, 0x9ced737bb6c4183dU,
    0xc428d05aa4751e4cU, 0xf53304714d9265dfU, 0x993fe2c6d07b7fabU,
    0xbf8fdb78849a5f96U, 0xef73d256a5c0f77cU, 0x95a8637627989aadU,
    0xbb127c53b17ec159U, 0xe9d71b689dde71afU, 0x9226712162ab070dU,
    0xb6b00d69bb55c8d1U, 0xe45c10c42a2b3b05U, 0x8eb98a7a9a5b04e3U,
    0xb267ed1940f1c61cU, 0xdf01e85f912e37a3U, 0x8b61313bbabce2c6U,
    0xae397d8aa96c1b77U, 0xd9c7dced53c72255U, 0x881cea14545c7575U,
    0xaa242499697392d2U, 0xd4ad2dbfc3d07787U, 0x84ec3c97da624ab4U,
    0xa6274bbdd0fadd61U, 0xcfb11ead453994baU, 0x81ceb32c4b43fcf4U,
    0xa2425ff75e14fc31U, 0xcad2f7f5359a3b3eU, 0xfd87b5f28300ca0dU,
    0x9e74d1b791e07e48U, 0xc612062576589ddaU, 0xf79687aed3eec551U,
    0x9abe14cd44753b52U, 0xc16d9a0095928a27U, 0xf1c90080baf72cb1U,
    0x971da05074da7beeU, 0xbce5086492111aeaU, 0xec1e4a7db69561a5U,
    0x9392ee8e921d5d07U, 0xb877aa3236a4b449U, 0xe69594bec44de15bU,
    0x901d7cf73ab0acd9U, 0xb424dc35095cd80fU, 0xe12e13424bb40e13U,
    0x8cbccc096f5088cbU, 0xafebff0bcb24aafeU, 0xdbe6fecebdedd5beU,
    0x89705f4136b4a597U, 0xabcc77118461cefcU, 0xd6bf94d5e57a42bcU,
    0x8637bd05af6c69b5U, 0xa7c5ac471b478423U, 0xd1b71758e219652bU,
    0x83126e978d4fdf3bU, 0xa3d70a3d70a3d70aU, 0xccccccccccccccccU,
    0x8000000000000000U, 0xa000000000000000U, 0xc800000000000000U,
    0xfa00000000000000U, 0x9c40000000000000U, 0xc350000000000000U,
    0xf424000000000000U, 0x9896800000000000U, 0xbebc200000000000U,
    0xee6b280000000000U, 0x9502f90000000000U, 0xba43b74000000000U,
    0xe8d4a51000000000U, 0x9184e72a00000000U, 0xb5e620f480000000U,
    0xe35fa931a0000000U, 0x8e1bc9bf04000000U, 0xb1a2bc2ec5000000U,
    0xde0b6b3a76400000U, 0x8ac7230489e80000U, 0xad78ebc5ac620000U,
    0xd8d726b7177a8000U, 0x878678326eac9000U, 0xa968163f0a57b400U,
    0xd3c21bcecceda100U, 0x84595161401484a0U, 0xa56fa5b99019a5c8U,
    0xcecb8f27f4200f3aU, 0x813f3978f8940984U, 0xa18f07d736b90be5U,
    0xc9f2c9cd04674edeU, 0xfc6f7c4045812296U, 0x9dc5ada82b70b59dU,
    0xc5371912364ce305U, 0xf684df56c3e01bc6U, 0x9a130b963a6c115cU,
    0xc097ce7bc90715b3U, 0xf0bdc21abb48db20U, 0x96769950b50d88f4U,
    0xbc143fa4e250eb31U, 0xeb194f8e1ae525fdU, 0x92efd1b8d0cf37beU,
    0xb7abc627050305adU, 0xe596b7b0c643c719U, 0x8f7e32ce7bea5c6fU,
    0xb35dbf821ae4f38bU, 0xe0352f62a19e306eU, 0x8c213d9da502de45U,
    0xaf298d050e4395d6U, 0xdaf3f04651d47b4cU, 0x88d8762bf324cd0fU,
    0xab0e93b6efee0053U, 0xd5d238a4abe98068U, 0x85a36366eb71f041U,
    0xa70c3c40a64e6c51U, 0xd0cf4b50cfe20765U, 0x82818f1281ed449fU,
    0xa321f2d7226895c7U, 0xcbea6f8ceb02bb39U, 0xfee50b7025c36a08U,
    0x9f4f2726179a2245U, 0xc722f0ef9d80aad6U, 0xf8ebad2b84e0d58bU,
    0x9b934c3b330c8577U, 0xc2781f49ffcfa6d5U, 0xf316271c7fc3908aU,
    0x97edd871cfda3a56U, 0xbde94e8e43d0c8ecU, 0xed63a231d4c4fb27U,
    0x945e455f24fb1cf8U, 0xb975d6b6ee39e436U, 0xe7d34c64a9c85d44U,
    0x90e40fbeea1d3a4aU, 0xb51d13aea4a488ddU, 0xe264589a4dcdab14U,
    0x8d7eb76070a08aecU, 0xb0de65388cc8ada8U, 0xdd15fe86affad912U,
    0x8a2dbf142dfcc7abU, 0xacb92ed9397bf996U, 0xd7e77a8f87daf7fbU,
    0x86f0ac99b4e8dafdU, 0xa8acd7c0222311bcU, 0xd2d80db02aabd62bU,
    0x83c7088e1aab65dbU, 0xa4b8cab1a1563f52U, 0xcde6fd5e09abcf26U,
    0x80b05e5ac60b6178U, 0xa0dc75f1778e39d6U, 0xc913936dd571c84cU,
    0xfb5878494ace3a5fU, 0x9d174b2dcec0e47bU, 0xc45d1df942711d9aU,
    0xf5746577930d6500U, 0x9968bf6abbe85f20U, 0xbfc2ef456ae276e8U,
    0xefb3ab16c59b14a2U, 0x95d04aee3b80ece5U, 0xbb445da9ca61281fU,
    0xea1575143cf97226U, 0x924d692ca61be758U, 0xb6e0c377cfa2e12eU,
    0xe498f455c38b997aU, 0x8edf98b59a373fecU, 0xb2977ee300c50fe7U,
    0xdf3d5e9bc0f653e1U, 0x8b865b215899f46cU, 0xae67f1e9aec07187U,
    0xda01ee641a708de9U, 0x884134fe908658b2U, 0xaa51823e34a7eedeU,
    0xd4e5e2cdc1d1ea96U, 0x850fadc09923329eU, 0xa6539930bf6bff45U,
    0xcfe87f7cef46ff16U, 0x81f14fae158c5f6eU, 0xa26da3999aef7749U,
    0xcb090c8001ab551cU, 0xfdcb4fa002162a63U, 0x9e9f11c4014dda7eU,
    0xc646d63501a1511dU, 0xf7d88bc24209a565U, 0x9ae757596946075fU,
    0xc1a12d2fc3978937U, 0xf209787bb47d6b84U, 0x9745eb4d50ce6332U,
    0xbd176620a501fbffU, 0xec5d3fa8ce427affU, 0x93ba47c980e98cdfU,
    0xb8a8d9bbe123f017U, 0xe6d3102ad96cec1dU, 0x9043ea1ac7e41392U,
    0xb454e4a179dd1877U, 0xe16a1dc9d8545e94U, 0x8ce2529e2734bb1dU,
    0xb01ae745b101e9e4U, 0xdc21a1171d42645dU, 0x899504ae72497ebaU,
    0xabfa45da0edbde69U, 0xd6f8d7509292d603U, 0x865b86925b9bc5c2U,
    0xa7f26836f282b732U, 0xd1ef0244af2364ffU, 0x8335616aed761f1fU,
    0xa402b9c5a8d3a6e7U, 0xcd036837130890a1U, 0x802221226be55a64U,
    0xa02aa96b06deb0fdU, 0xc83553c5c8965d3dU, 0xfa42a8b73abbf48cU,
    0x9c69a97284b578d7U, 0xc38413cf25e2d70dU, 0xf46518c2ef5b8cd1U,
    0x98bf2f79d5993802U, 0xbeeefb584aff8603U, 0xeeaaba2e5dbf6784U,
    0x952ab45cfa97a0b2U, 0xba756174393d88dfU, 0xe912b9d1478ceb17U,
    0x91abb422ccb812eeU, 0xb616a12b7fe617aaU, 0xe39c49765fdf9d94U,
    0x8e41ade9fbebc27dU, 0xb1d219647ae6b31cU, 0xde469fbd99a05fe3U,
    0x8aec23d680043beeU, 0xada72ccc20054ae9U, 0xd910f7ff28069da4U,
    0x87aa9aff79042286U, 0xa99541bf57452b28U, 0xd3fa922f2d1675f2U,
    0x847c9b5d7c2e09b7U, 0xa59bc234db398c25U, 0xcf02b2c21207ef2eU,
    0x8161afb94b44f57dU, 0xa1ba1ba79e1632dcU, 0xca28a291859bbf93U,
    0xfcb2cb35e702af78U, 0x9defbf01b061adabU, 0xc56baec21c7a1916U,
    0xf6c69a72a3989f5bU, 0x9a3c2087a63f6399U, 0xc0cb28a98fcf3c7fU,
    0xf0fdf2d3f3c30b9fU, 0x969eb7c47859e743U, 0xbc4665b596706114U,
    0xeb57ff22fc0c7959U, 0x9316ff75dd87cbd8U, 0xb7dcbf5354e9beceU,
    0xe5d3ef282a242e81U, 0x8fa475791a569d10U, 0xb38d92d760ec4455U,
    0xe070f78d3927556aU, 0x8c469ab843b89562U, 0xaf58416654a6babbU,
    0xdb2e51bfe9d0696aU, 0x88fcf317f22241e2U, 0xab3c2fddeeaad25aU,
    0xd60b3bd56a5586f1U, 0x85c7056562757456U, 0xa738c6bebb12d16cU,
    0xd106f86e69d785c7U, 0x82a45b450226b39cU, 0xa34d721642b06084U,
    0xcc20ce9bd35c78a5U, 0xff290242c83396ceU, 0x9f79a169bd203e41U,
    0xc75809c42c684dd1U, 0xf92e0c3537826145U, 0x9bbcc7a142b17ccbU,
    0xc2abf989935ddbfeU, 0xf356f7ebf83552feU, 0x98165af37b2153deU,
    0xbe1bf1b059e9a8d6U, 0xeda2ee1c7064130cU, 0x9485d4d1c63e8be7U,
    0xb9a74a0637ce2ee1U, 0xe8111c87c5c1ba99U, 0x910ab1d4db9914a0U,
    0xb54d5e4a127f59c8U, 0xe2a0b5dc971f303aU, 0x8da471a9de737e24U,
    0xb10d8e1456105dadU, 0xdd50f1996b947518U, 0x8a5296ffe33cc92fU,
    0xace73cbfdc0bfb7bU, 0xd8210befd30efa5aU, 0x8714a775e3e95c78U,
    0xa8d9d1535ce3b396U, 0xd31045a8341ca07cU, 0x83ea2b892091e44dU,
    0xa4e4b66b68b65d60U, 0xce1de40642e3f4b9U, 0x80d2ae83e9ce78f3U,
    0xa1075a24e4421730U, 0xc94930ae1d529cfcU, 0xfb9b7cd9a4a7443cU,
    0x9d412e0806e88aa5U, 0xc491798a08a2ad4eU, 0xf5b5d7ec8acb58a2U,
    0x9991a6f3d6bf1765U, 0xbff610b0cc6edd3fU, 0xeff394dcff8a948eU,
    0x95f83d0a1fb69cd9U, 0xbb764c4ca7a4440fU, 0xea53df5fd18d5513U,
    0x92746b9be2f8552cU, 0xb7118682dbb66a77U, 0xe4d5e82392a40515U,
    0x8f05b1163ba6832dU, 0xb2c71d5bca9023f8U, 0xdf78e4b2bd342cf6U,
    0x8bab8eefb6409c1aU, 0xae9672aba3d0c320U, 0xda3c0f568cc4f3e8U,
    0x8865899617fb1871U, 0xaa7eebfb9df9de8dU, 0xd51ea6fa85785631U,
    0x8533285c936b35deU, 0xa67ff273b8460356U, 0xd01fef10a657842cU,
    0x8213f56a67f6b29bU, 0xa298f2c501f45f42U, 0xcb3f2f7642717713U,
    0xfe0efb53d30dd4d7U, 0x9ec95d1463e8a506U, 0xc67bb4597ce2ce48U,
    0xf81aa16fdc1b81daU, 0x9b10a4e5e9913128U, 0xc1d4ce1f63f57d72U,
    0xf24a01a73cf2dccfU, 0x976e41088617ca01U, 0xbd49d14aa79dbc82U,
    0xec9c459d51852ba2U, 0x93e1ab8252f33b45U, 0xb8da1662e7b00a17U,
    0xe7109bfba19c0c9dU, 0x906a617d450187e2U, 0xb484f9dc9641e9daU,
    0xe1a63853bbd26451U, 0x8d07e33455637eb2U, 0xb049dc016abc5e5fU,
    0xdc5c5301c56b75f7U, 0x89b9b3e11b6329baU, 0xac2820d9623bf429U,
    0xd732290fbacaf133U, 0x867f59a9d4bed6c0U, 0xa81f301449ee8c70U,
    0xd226fc195c6a2f8cU, 0x83585d8fd9c25db7U, 0xa42e74f3d032f525U,
    0xcd3a1230c43fb26fU, 0x80444b5e7aa7cf85U, 0xa0555e361951c366U,
    0xc86ab5c39fa63440U, 0xfa856334878fc150U, 0x9c935e00d4b9d8d2U,
    0xc3b8358109e84f07U, 0xf4a642e14c6262c8U, 0x98e7e9cccfbd7dbdU,
    0xbf21e44003acdd2cU, 0xeeea5d5004981478U, 0x95527a5202df0ccbU,
    0xbaa718e68396cffdU, 0xe950df20247c83fdU, 0x91d28b7416cdd27eU,
    0xb6472e511c81471dU, 0xe3d8f9e563a198e5U, 0x8e679c2f5e44ff8fU,
};

const int16_t kPower10ExponentTable[] = {
    -1200, -1196, -1193, -1190, -1186, -1183, -1180, -1176, -1173, -1170, -1166,
    -1163, -1160, -1156, -1153, -1150, -1146, -1143, -1140, -1136, -1133, -1130,
    -1127, -1123, -1120, -1117, -1113, -1110, -1107, -1103, -1100, -1097, -1093,
    -1090, -1087, -1083, -1080, -1077, -1073, -1070, -1067, -1063, -1060, -1057,
    -1053, -1050, -1047, -1043, -1040, -1037, -1034, -1030, -1027, -1024, -1020,
    -1017, -1014, -1010, -1007, -1004, -1000, -997,  -994,  -990,  -987,  -984,
    -980,  -977,  -974,  -970,  -967,  -964,  -960,  -957,  -954,  -950,  -947,
    -944,  -940,  -937,  -934,  -931,  -927,  -924,  -921,  -917,  -914,  -911,
    -907,  -904,  -901,  -897,  -894,  -891,  -887,  -884,  -881,  -877,  -874,
    -871,  -867,  -864,  -861,  -857,  -854,  -851,  -847,  -844,  -841,  -838,
    -834,  -831,  -828,  -824,  -821,  -818,  -814,  -811,  -808,  -804,  -801,
    -798,  -794,  -791,  -788,  -784,  -781,  -778,  -774,  -771,  -768,  -764,
    -761,  -758,  -754,  -751,  -748,  -744,  -741,  -738,  -735,  -731,  -728,
    -725,  -721,  -718,  -715,  -711,  -708,  -705,  -701,  -698,  -695,  -691,
    -688,  -685,  -681,  -678,  -675,  -671,  -668,  -665,  -661,  -658,  -655,
    -651,  -648,  -645,  -642,  -638,  -635,  -632,  -628,  -625,  -622,  -618,
    -615,  -612,  -608,  -605,  -602,  -598,  -595,  -592,  -588,  -585,  -582,
    -578,  -575,  -572,  -568,  -565,  -562,  -558,  -555,  -552,  -549,  -545,
    -542,  -539,  -535,  -532,  -529,  -525,  -522,  -519,  -515,  -512,  -509,
    -505,  -502,  -499,  -495,  -492,  -489,  -485,  -482,  -479,  -475,  -472,
    -469,  -465,  -462,  -459,  -455,  -452,  -449,  -446,  -442,  -439,  -436,
    -432,  -429,  -426,  -422,  -419,  -416,  -412,  -409,  -406,  -402,  -399,
    -396,  -392,  -389,  -386,  -382,  -379,  -376,  -372,  -369,  -366,  -362,
    -359,  -356,  -353,  -349,  -346,  -343,  -339,  -336,  -333,  -329,  -326,
    -323,  -319,  -316,  -313,  -309,  -306,  -303,  -299,  -296,  -293,  -289,
    -286,  -283,  -279,  -276,  -273,  -269,  -266,  -263,  -259,  -256,  -253,
    -250,  -246,  -243,  -240,  -236,  -233,  -230,  -226,  -223,  -220,  -216,
    -213,  -210,  -206,  -203,  -200,  -196,  -193,  -190,  -186,  -183,  -180,
    -176,  -173,  -170,  -166,  -163,  -160,  -157,  -153,  -150,  -147,  -143,
    -140,  -137,  -133,  -130,  -127,  -123,  -120,  -117,  -113,  -110,  -107,
    -103,  -100,  -97,   -93,   -90,   -87,   -83,   -80,   -77,   -73,   -70,
    -67,   -63,   -60,   -57,   -54,   -50,   -47,   -44,   -40,   -37,   -34,
    -30,   -27,   -24,   -20,   -17,   -14,   -10,   -7,    -4,    0,     3,
    6,     10,    13,    16,    20,    23,    26,    30,    33,    36,    39,
    43,    46,    49,    53,    56,    59,    63,    66,    69,    73,    76,
    79,    83,    86,    89,    93,    96,    99,    103,   106,   109,   113,
    116,   119,   123,   126,   129,   132,   136,   139,   142,   146,   149,
    152,   156,   159,   162,   166,   169,   172,   176,   179,   182,   186,
    189,   192,   196,   199,   202,   206,   209,   212,   216,   219,   222,
    226,   229,   232,   235,   239,   242,   245,   249,   252,   255,   259,
    262,   265,   269,   272,   275,   279,   282,   285,   289,   292,   295,
    299,   302,   305,   309,   312,   315,   319,   322,   325,   328,   332,
    335,   338,   342,   345,   348,   352,   355,   358,   362,   365,   368,
    372,   375,   378,   382,   385,   388,   392,   395,   398,   402,   405,
    408,   412,   415,   418,   422,   425,   428,   431,   435,   438,   441,
    445,   448,   451,   455,   458,   461,   465,   468,   471,   475,   478,
    481,   485,   488,   491,   495,   498,   501,   505,   508,   511,   515,
    518,   521,   524,   528,   531,   534,   538,   541,   544,   548,   551,
    554,   558,   561,   564,   568,   571,   574,   578,   581,   584,   588,
    591,   594,   598,   601,   604,   608,   611,   614,   617,   621,   624,
    627,   631,   634,   637,   641,   644,   647,   651,   654,   657,   661,
    664,   667,   671,   674,   677,   681,   684,   687,   691,   694,   697,
    701,   704,   707,   711,   714,   717,   720,   724,   727,   730,   734,
    737,   740,   744,   747,   750,   754,   757,   760,   764,   767,   770,
    774,   777,   780,   784,   787,   790,   794,   797,   800,   804,   807,
    810,   813,   817,   820,   823,   827,   830,   833,   837,   840,   843,
    847,   850,   853,   857,   860,   863,   867,   870,   873,   877,   880,
    883,   887,   890,   893,   897,   900,   903,   907,   910,   913,   916,
    920,   923,   926,   930,   933,   936,   940,   943,   946,   950,   953,
    956,   960,
};

}  // namespace
ABSL_NAMESPACE_END
}  // namespace absl
