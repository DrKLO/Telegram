#include "absl/strings/internal/str_format/float_conversion.h"

#include <string.h>
#include <algorithm>
#include <cassert>
#include <cmath>
#include <string>

#include "absl/base/config.h"

namespace absl {
ABSL_NAMESPACE_BEGIN
namespace str_format_internal {

namespace {

char *CopyStringTo(string_view v, char *out) {
  std::memcpy(out, v.data(), v.size());
  return out + v.size();
}

template <typename Float>
bool FallbackToSnprintf(const Float v, const ConversionSpec &conv,
                        FormatSinkImpl *sink) {
  int w = conv.width() >= 0 ? conv.width() : 0;
  int p = conv.precision() >= 0 ? conv.precision() : -1;
  char fmt[32];
  {
    char *fp = fmt;
    *fp++ = '%';
    fp = CopyStringTo(FormatConversionSpecImplFriend::FlagsToString(conv), fp);
    fp = CopyStringTo("*.*", fp);
    if (std::is_same<long double, Float>()) {
      *fp++ = 'L';
    }
    *fp++ = FormatConversionCharToChar(conv.conversion_char());
    *fp = 0;
    assert(fp < fmt + sizeof(fmt));
  }
  std::string space(512, '\0');
  string_view result;
  while (true) {
    int n = snprintf(&space[0], space.size(), fmt, w, p, v);
    if (n < 0) return false;
    if (static_cast<size_t>(n) < space.size()) {
      result = string_view(space.data(), n);
      break;
    }
    space.resize(n + 1);
  }
  sink->Append(result);
  return true;
}

// 128-bits in decimal: ceil(128*log(2)/log(10))
//   or std::numeric_limits<__uint128_t>::digits10
constexpr int kMaxFixedPrecision = 39;

constexpr int kBufferLength = /*sign*/ 1 +
                              /*integer*/ kMaxFixedPrecision +
                              /*point*/ 1 +
                              /*fraction*/ kMaxFixedPrecision +
                              /*exponent e+123*/ 5;

struct Buffer {
  void push_front(char c) {
    assert(begin > data);
    *--begin = c;
  }
  void push_back(char c) {
    assert(end < data + sizeof(data));
    *end++ = c;
  }
  void pop_back() {
    assert(begin < end);
    --end;
  }

  char &back() {
    assert(begin < end);
    return end[-1];
  }

  char last_digit() const { return end[-1] == '.' ? end[-2] : end[-1]; }

  int size() const { return static_cast<int>(end - begin); }

  char data[kBufferLength];
  char *begin;
  char *end;
};

enum class FormatStyle { Fixed, Precision };

// If the value is Inf or Nan, print it and return true.
// Otherwise, return false.
template <typename Float>
bool ConvertNonNumericFloats(char sign_char, Float v,
                             const ConversionSpec &conv, FormatSinkImpl *sink) {
  char text[4], *ptr = text;
  if (sign_char) *ptr++ = sign_char;
  if (std::isnan(v)) {
    ptr = std::copy_n(
        FormatConversionCharIsUpper(conv.conversion_char()) ? "NAN" : "nan", 3,
        ptr);
  } else if (std::isinf(v)) {
    ptr = std::copy_n(
        FormatConversionCharIsUpper(conv.conversion_char()) ? "INF" : "inf", 3,
        ptr);
  } else {
    return false;
  }

  return sink->PutPaddedString(string_view(text, ptr - text), conv.width(), -1,
                               conv.has_left_flag());
}

// Round up the last digit of the value.
// It will carry over and potentially overflow. 'exp' will be adjusted in that
// case.
template <FormatStyle mode>
void RoundUp(Buffer *buffer, int *exp) {
  char *p = &buffer->back();
  while (p >= buffer->begin && (*p == '9' || *p == '.')) {
    if (*p == '9') *p = '0';
    --p;
  }

  if (p < buffer->begin) {
    *p = '1';
    buffer->begin = p;
    if (mode == FormatStyle::Precision) {
      std::swap(p[1], p[2]);  // move the .
      ++*exp;
      buffer->pop_back();
    }
  } else {
    ++*p;
  }
}

void PrintExponent(int exp, char e, Buffer *out) {
  out->push_back(e);
  if (exp < 0) {
    out->push_back('-');
    exp = -exp;
  } else {
    out->push_back('+');
  }
  // Exponent digits.
  if (exp > 99) {
    out->push_back(exp / 100 + '0');
    out->push_back(exp / 10 % 10 + '0');
    out->push_back(exp % 10 + '0');
  } else {
    out->push_back(exp / 10 + '0');
    out->push_back(exp % 10 + '0');
  }
}

template <typename Float, typename Int>
constexpr bool CanFitMantissa() {
  return
#if defined(__clang__) && !defined(__SSE3__)
      // Workaround for clang bug: https://bugs.llvm.org/show_bug.cgi?id=38289
      // Casting from long double to uint64_t is miscompiled and drops bits.
      (!std::is_same<Float, long double>::value ||
       !std::is_same<Int, uint64_t>::value) &&
#endif
      std::numeric_limits<Float>::digits <= std::numeric_limits<Int>::digits;
}

template <typename Float>
struct Decomposed {
  Float mantissa;
  int exponent;
};

// Decompose the double into an integer mantissa and an exponent.
template <typename Float>
Decomposed<Float> Decompose(Float v) {
  int exp;
  Float m = std::frexp(v, &exp);
  m = std::ldexp(m, std::numeric_limits<Float>::digits);
  exp -= std::numeric_limits<Float>::digits;
  return {m, exp};
}

// Print 'digits' as decimal.
// In Fixed mode, we add a '.' at the end.
// In Precision mode, we add a '.' after the first digit.
template <FormatStyle mode, typename Int>
int PrintIntegralDigits(Int digits, Buffer *out) {
  int printed = 0;
  if (digits) {
    for (; digits; digits /= 10) out->push_front(digits % 10 + '0');
    printed = out->size();
    if (mode == FormatStyle::Precision) {
      out->push_front(*out->begin);
      out->begin[1] = '.';
    } else {
      out->push_back('.');
    }
  } else if (mode == FormatStyle::Fixed) {
    out->push_front('0');
    out->push_back('.');
    printed = 1;
  }
  return printed;
}

// Back out 'extra_digits' digits and round up if necessary.
bool RemoveExtraPrecision(int extra_digits, bool has_leftover_value,
                          Buffer *out, int *exp_out) {
  if (extra_digits <= 0) return false;

  // Back out the extra digits
  out->end -= extra_digits;

  bool needs_to_round_up = [&] {
    // We look at the digit just past the end.
    // There must be 'extra_digits' extra valid digits after end.
    if (*out->end > '5') return true;
    if (*out->end < '5') return false;
    if (has_leftover_value || std::any_of(out->end + 1, out->end + extra_digits,
                                          [](char c) { return c != '0'; }))
      return true;

    // Ends in ...50*, round to even.
    return out->last_digit() % 2 == 1;
  }();

  if (needs_to_round_up) {
    RoundUp<FormatStyle::Precision>(out, exp_out);
  }
  return true;
}

// Print the value into the buffer.
// This will not include the exponent, which will be returned in 'exp_out' for
// Precision mode.
template <typename Int, typename Float, FormatStyle mode>
bool FloatToBufferImpl(Int int_mantissa, int exp, int precision, Buffer *out,
                       int *exp_out) {
  assert((CanFitMantissa<Float, Int>()));

  const int int_bits = std::numeric_limits<Int>::digits;

  // In precision mode, we start printing one char to the right because it will
  // also include the '.'
  // In fixed mode we put the dot afterwards on the right.
  out->begin = out->end =
      out->data + 1 + kMaxFixedPrecision + (mode == FormatStyle::Precision);

  if (exp >= 0) {
    if (std::numeric_limits<Float>::digits + exp > int_bits) {
      // The value will overflow the Int
      return false;
    }
    int digits_printed = PrintIntegralDigits<mode>(int_mantissa << exp, out);
    int digits_to_zero_pad = precision;
    if (mode == FormatStyle::Precision) {
      *exp_out = digits_printed - 1;
      digits_to_zero_pad -= digits_printed - 1;
      if (RemoveExtraPrecision(-digits_to_zero_pad, false, out, exp_out)) {
        return true;
      }
    }
    for (; digits_to_zero_pad-- > 0;) out->push_back('0');
    return true;
  }

  exp = -exp;
  // We need at least 4 empty bits for the next decimal digit.
  // We will multiply by 10.
  if (exp > int_bits - 4) return false;

  const Int mask = (Int{1} << exp) - 1;

  // Print the integral part first.
  int digits_printed = PrintIntegralDigits<mode>(int_mantissa >> exp, out);
  int_mantissa &= mask;

  int fractional_count = precision;
  if (mode == FormatStyle::Precision) {
    if (digits_printed == 0) {
      // Find the first non-zero digit, when in Precision mode.
      *exp_out = 0;
      if (int_mantissa) {
        while (int_mantissa <= mask) {
          int_mantissa *= 10;
          --*exp_out;
        }
      }
      out->push_front(static_cast<char>(int_mantissa >> exp) + '0');
      out->push_back('.');
      int_mantissa &= mask;
    } else {
      // We already have a digit, and a '.'
      *exp_out = digits_printed - 1;
      fractional_count -= *exp_out;
      if (RemoveExtraPrecision(-fractional_count, int_mantissa != 0, out,
                               exp_out)) {
        // If we had enough digits, return right away.
        // The code below will try to round again otherwise.
        return true;
      }
    }
  }

  auto get_next_digit = [&] {
    int_mantissa *= 10;
    int digit = static_cast<int>(int_mantissa >> exp);
    int_mantissa &= mask;
    return digit;
  };

  // Print fractional_count more digits, if available.
  for (; fractional_count > 0; --fractional_count) {
    out->push_back(get_next_digit() + '0');
  }

  int next_digit = get_next_digit();
  if (next_digit > 5 ||
      (next_digit == 5 && (int_mantissa || out->last_digit() % 2 == 1))) {
    RoundUp<mode>(out, exp_out);
  }

  return true;
}

template <FormatStyle mode, typename Float>
bool FloatToBuffer(Decomposed<Float> decomposed, int precision, Buffer *out,
                   int *exp) {
  if (precision > kMaxFixedPrecision) return false;

  // Try with uint64_t.
  if (CanFitMantissa<Float, std::uint64_t>() &&
      FloatToBufferImpl<std::uint64_t, Float, mode>(
          static_cast<std::uint64_t>(decomposed.mantissa),
          static_cast<std::uint64_t>(decomposed.exponent), precision, out, exp))
    return true;

#if defined(ABSL_HAVE_INTRINSIC_INT128)
  // If that is not enough, try with __uint128_t.
  return CanFitMantissa<Float, __uint128_t>() &&
         FloatToBufferImpl<__uint128_t, Float, mode>(
             static_cast<__uint128_t>(decomposed.mantissa),
             static_cast<__uint128_t>(decomposed.exponent), precision, out,
             exp);
#endif
  return false;
}

void WriteBufferToSink(char sign_char, string_view str,
                       const ConversionSpec &conv, FormatSinkImpl *sink) {
  int left_spaces = 0, zeros = 0, right_spaces = 0;
  int missing_chars =
      conv.width() >= 0 ? std::max(conv.width() - static_cast<int>(str.size()) -
                                       static_cast<int>(sign_char != 0),
                                   0)
                        : 0;
  if (conv.has_left_flag()) {
    right_spaces = missing_chars;
  } else if (conv.has_zero_flag()) {
    zeros = missing_chars;
  } else {
    left_spaces = missing_chars;
  }

  sink->Append(left_spaces, ' ');
  if (sign_char) sink->Append(1, sign_char);
  sink->Append(zeros, '0');
  sink->Append(str);
  sink->Append(right_spaces, ' ');
}

template <typename Float>
bool FloatToSink(const Float v, const ConversionSpec &conv,
                 FormatSinkImpl *sink) {
  // Print the sign or the sign column.
  Float abs_v = v;
  char sign_char = 0;
  if (std::signbit(abs_v)) {
    sign_char = '-';
    abs_v = -abs_v;
  } else if (conv.has_show_pos_flag()) {
    sign_char = '+';
  } else if (conv.has_sign_col_flag()) {
    sign_char = ' ';
  }

  // Print nan/inf.
  if (ConvertNonNumericFloats(sign_char, abs_v, conv, sink)) {
    return true;
  }

  int precision = conv.precision() < 0 ? 6 : conv.precision();

  int exp = 0;

  auto decomposed = Decompose(abs_v);

  Buffer buffer;

  switch (conv.conversion_char()) {
    case ConversionChar::f:
    case ConversionChar::F:
      if (!FloatToBuffer<FormatStyle::Fixed>(decomposed, precision, &buffer,
                                             nullptr)) {
        return FallbackToSnprintf(v, conv, sink);
      }
      if (!conv.has_alt_flag() && buffer.back() == '.') buffer.pop_back();
      break;

    case ConversionChar::e:
    case ConversionChar::E:
      if (!FloatToBuffer<FormatStyle::Precision>(decomposed, precision, &buffer,
                                                 &exp)) {
        return FallbackToSnprintf(v, conv, sink);
      }
      if (!conv.has_alt_flag() && buffer.back() == '.') buffer.pop_back();
      PrintExponent(
          exp, FormatConversionCharIsUpper(conv.conversion_char()) ? 'E' : 'e',
          &buffer);
      break;

    case ConversionChar::g:
    case ConversionChar::G:
      precision = std::max(0, precision - 1);
      if (!FloatToBuffer<FormatStyle::Precision>(decomposed, precision, &buffer,
                                                 &exp)) {
        return FallbackToSnprintf(v, conv, sink);
      }
      if (precision + 1 > exp && exp >= -4) {
        if (exp < 0) {
          // Have 1.23456, needs 0.00123456
          // Move the first digit
          buffer.begin[1] = *buffer.begin;
          // Add some zeros
          for (; exp < -1; ++exp) *buffer.begin-- = '0';
          *buffer.begin-- = '.';
          *buffer.begin = '0';
        } else if (exp > 0) {
          // Have 1.23456, needs 1234.56
          // Move the '.' exp positions to the right.
          std::rotate(buffer.begin + 1, buffer.begin + 2,
                      buffer.begin + exp + 2);
        }
        exp = 0;
      }
      if (!conv.has_alt_flag()) {
        while (buffer.back() == '0') buffer.pop_back();
        if (buffer.back() == '.') buffer.pop_back();
      }
      if (exp) {
        PrintExponent(
            exp,
            FormatConversionCharIsUpper(conv.conversion_char()) ? 'E' : 'e',
            &buffer);
      }
      break;

    case ConversionChar::a:
    case ConversionChar::A:
      return FallbackToSnprintf(v, conv, sink);

    default:
      return false;
  }

  WriteBufferToSink(sign_char,
                    string_view(buffer.begin, buffer.end - buffer.begin), conv,
                    sink);

  return true;
}

}  // namespace

bool ConvertFloatImpl(long double v, const ConversionSpec &conv,
                      FormatSinkImpl *sink) {
  return FloatToSink(v, conv, sink);
}

bool ConvertFloatImpl(float v, const ConversionSpec &conv,
                      FormatSinkImpl *sink) {
  return FloatToSink(v, conv, sink);
}

bool ConvertFloatImpl(double v, const ConversionSpec &conv,
                      FormatSinkImpl *sink) {
  return FloatToSink(v, conv, sink);
}

}  // namespace str_format_internal
ABSL_NAMESPACE_END
}  // namespace absl
