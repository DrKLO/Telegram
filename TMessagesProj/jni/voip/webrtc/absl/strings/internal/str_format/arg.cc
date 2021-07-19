//
// POSIX spec:
//   http://pubs.opengroup.org/onlinepubs/009695399/functions/fprintf.html
//
#include "absl/strings/internal/str_format/arg.h"

#include <cassert>
#include <cerrno>
#include <cstdlib>
#include <string>
#include <type_traits>

#include "absl/base/port.h"
#include "absl/strings/internal/str_format/float_conversion.h"

namespace absl {
ABSL_NAMESPACE_BEGIN
namespace str_format_internal {
namespace {

const char kDigit[2][32] = { "0123456789abcdef", "0123456789ABCDEF" };

// Reduce *capacity by s.size(), clipped to a 0 minimum.
void ReducePadding(string_view s, size_t *capacity) {
  *capacity = Excess(s.size(), *capacity);
}

// Reduce *capacity by n, clipped to a 0 minimum.
void ReducePadding(size_t n, size_t *capacity) {
  *capacity = Excess(n, *capacity);
}

template <typename T>
struct MakeUnsigned : std::make_unsigned<T> {};
template <>
struct MakeUnsigned<absl::int128> {
  using type = absl::uint128;
};
template <>
struct MakeUnsigned<absl::uint128> {
  using type = absl::uint128;
};

template <typename T>
struct IsSigned : std::is_signed<T> {};
template <>
struct IsSigned<absl::int128> : std::true_type {};
template <>
struct IsSigned<absl::uint128> : std::false_type {};

class ConvertedIntInfo {
 public:
  template <typename T>
  ConvertedIntInfo(T v, ConversionChar conv) {
    using Unsigned = typename MakeUnsigned<T>::type;
    auto u = static_cast<Unsigned>(v);
    if (IsNeg(v)) {
      is_neg_ = true;
      u = Unsigned{} - u;
    } else {
      is_neg_ = false;
    }
    UnsignedToStringRight(u, conv);
  }

  string_view digits() const {
    return {end() - size_, static_cast<size_t>(size_)};
  }
  bool is_neg() const { return is_neg_; }

 private:
  template <typename T, bool IsSigned>
  struct IsNegImpl {
    static bool Eval(T v) { return v < 0; }
  };
  template <typename T>
  struct IsNegImpl<T, false> {
    static bool Eval(T) {
      return false;
    }
  };

  template <typename T>
  bool IsNeg(T v) {
    return IsNegImpl<T, IsSigned<T>::value>::Eval(v);
  }

  template <typename T>
  void UnsignedToStringRight(T u, ConversionChar conv) {
    char *p = end();
    switch (FormatConversionCharRadix(conv)) {
      default:
      case 10:
        for (; u; u /= 10)
          *--p = static_cast<char>('0' + static_cast<size_t>(u % 10));
        break;
      case 8:
        for (; u; u /= 8)
          *--p = static_cast<char>('0' + static_cast<size_t>(u % 8));
        break;
      case 16: {
        const char *digits = kDigit[FormatConversionCharIsUpper(conv) ? 1 : 0];
        for (; u; u /= 16) *--p = digits[static_cast<size_t>(u % 16)];
        break;
      }
    }
    size_ = static_cast<int>(end() - p);
  }

  const char *end() const { return storage_ + sizeof(storage_); }
  char *end() { return storage_ + sizeof(storage_); }

  bool is_neg_;
  int size_;
  // Max size: 128 bit value as octal -> 43 digits
  char storage_[128 / 3 + 1];
};

// Note: 'o' conversions do not have a base indicator, it's just that
// the '#' flag is specified to modify the precision for 'o' conversions.
string_view BaseIndicator(const ConvertedIntInfo &info,
                          const ConversionSpec conv) {
  bool alt = conv.has_alt_flag();
  int radix = FormatConversionCharRadix(conv.conversion_char());
  if (conv.conversion_char() == ConversionChar::p)
    alt = true;  // always show 0x for %p.
  // From the POSIX description of '#' flag:
  //   "For x or X conversion specifiers, a non-zero result shall have
  //   0x (or 0X) prefixed to it."
  if (alt && radix == 16 && !info.digits().empty()) {
    if (FormatConversionCharIsUpper(conv.conversion_char())) return "0X";
    return "0x";
  }
  return {};
}

string_view SignColumn(bool neg, const ConversionSpec conv) {
  if (FormatConversionCharIsSigned(conv.conversion_char())) {
    if (neg) return "-";
    if (conv.has_show_pos_flag()) return "+";
    if (conv.has_sign_col_flag()) return " ";
  }
  return {};
}

bool ConvertCharImpl(unsigned char v, const ConversionSpec conv,
                     FormatSinkImpl *sink) {
  size_t fill = 0;
  if (conv.width() >= 0) fill = conv.width();
  ReducePadding(1, &fill);
  if (!conv.has_left_flag()) sink->Append(fill, ' ');
  sink->Append(1, v);
  if (conv.has_left_flag()) sink->Append(fill, ' ');
  return true;
}

bool ConvertIntImplInner(const ConvertedIntInfo &info,
                         const ConversionSpec conv, FormatSinkImpl *sink) {
  // Print as a sequence of Substrings:
  //   [left_spaces][sign][base_indicator][zeroes][formatted][right_spaces]
  size_t fill = 0;
  if (conv.width() >= 0) fill = conv.width();

  string_view formatted = info.digits();
  ReducePadding(formatted, &fill);

  string_view sign = SignColumn(info.is_neg(), conv);
  ReducePadding(sign, &fill);

  string_view base_indicator = BaseIndicator(info, conv);
  ReducePadding(base_indicator, &fill);

  int precision = conv.precision();
  bool precision_specified = precision >= 0;
  if (!precision_specified)
    precision = 1;

  if (conv.has_alt_flag() && conv.conversion_char() == ConversionChar::o) {
    // From POSIX description of the '#' (alt) flag:
    //   "For o conversion, it increases the precision (if necessary) to
    //   force the first digit of the result to be zero."
    if (formatted.empty() || *formatted.begin() != '0') {
      int needed = static_cast<int>(formatted.size()) + 1;
      precision = std::max(precision, needed);
    }
  }

  size_t num_zeroes = Excess(formatted.size(), precision);
  ReducePadding(num_zeroes, &fill);

  size_t num_left_spaces = !conv.has_left_flag() ? fill : 0;
  size_t num_right_spaces = conv.has_left_flag() ? fill : 0;

  // From POSIX description of the '0' (zero) flag:
  //   "For d, i, o, u, x, and X conversion specifiers, if a precision
  //   is specified, the '0' flag is ignored."
  if (!precision_specified && conv.has_zero_flag()) {
    num_zeroes += num_left_spaces;
    num_left_spaces = 0;
  }

  sink->Append(num_left_spaces, ' ');
  sink->Append(sign);
  sink->Append(base_indicator);
  sink->Append(num_zeroes, '0');
  sink->Append(formatted);
  sink->Append(num_right_spaces, ' ');
  return true;
}

template <typename T>
bool ConvertIntImplInner(T v, const ConversionSpec conv, FormatSinkImpl *sink) {
  ConvertedIntInfo info(v, conv.conversion_char());
  if (conv.is_basic() && (conv.conversion_char() != ConversionChar::p)) {
    if (info.is_neg()) sink->Append(1, '-');
    if (info.digits().empty()) {
      sink->Append(1, '0');
    } else {
      sink->Append(info.digits());
    }
    return true;
  }
  return ConvertIntImplInner(info, conv, sink);
}

template <typename T>
bool ConvertIntArg(T v, const ConversionSpec conv, FormatSinkImpl *sink) {
  if (FormatConversionCharIsFloat(conv.conversion_char())) {
    return FormatConvertImpl(static_cast<double>(v), conv, sink).value;
  }
  if (conv.conversion_char() == ConversionChar::c)
    return ConvertCharImpl(static_cast<unsigned char>(v), conv, sink);
  if (!FormatConversionCharIsIntegral(conv.conversion_char())) return false;
  if (!FormatConversionCharIsSigned(conv.conversion_char()) &&
      IsSigned<T>::value) {
    using U = typename MakeUnsigned<T>::type;
    return FormatConvertImpl(static_cast<U>(v), conv, sink).value;
  }
  return ConvertIntImplInner(v, conv, sink);
}

template <typename T>
bool ConvertFloatArg(T v, const ConversionSpec conv, FormatSinkImpl *sink) {
  return FormatConversionCharIsFloat(conv.conversion_char()) &&
         ConvertFloatImpl(v, conv, sink);
}

inline bool ConvertStringArg(string_view v, const ConversionSpec conv,
                             FormatSinkImpl *sink) {
  if (conv.conversion_char() != ConversionChar::s) return false;
  if (conv.is_basic()) {
    sink->Append(v);
    return true;
  }
  return sink->PutPaddedString(v, conv.width(), conv.precision(),
                               conv.has_left_flag());
}

}  // namespace

// ==================== Strings ====================
ConvertResult<Conv::s> FormatConvertImpl(const std::string &v,
                                         const ConversionSpec conv,
                                         FormatSinkImpl *sink) {
  return {ConvertStringArg(v, conv, sink)};
}

ConvertResult<Conv::s> FormatConvertImpl(string_view v,
                                         const ConversionSpec conv,
                                         FormatSinkImpl *sink) {
  return {ConvertStringArg(v, conv, sink)};
}

ConvertResult<Conv::s | Conv::p> FormatConvertImpl(const char *v,
                                                   const ConversionSpec conv,
                                                   FormatSinkImpl *sink) {
  if (conv.conversion_char() == ConversionChar::p)
    return {FormatConvertImpl(VoidPtr(v), conv, sink).value};
  size_t len;
  if (v == nullptr) {
    len = 0;
  } else if (conv.precision() < 0) {
    len = std::strlen(v);
  } else {
    // If precision is set, we look for the NUL-terminator on the valid range.
    len = std::find(v, v + conv.precision(), '\0') - v;
  }
  return {ConvertStringArg(string_view(v, len), conv, sink)};
}

// ==================== Raw pointers ====================
ConvertResult<Conv::p> FormatConvertImpl(VoidPtr v, const ConversionSpec conv,
                                         FormatSinkImpl *sink) {
  if (conv.conversion_char() != ConversionChar::p) return {false};
  if (!v.value) {
    sink->Append("(nil)");
    return {true};
  }
  return {ConvertIntImplInner(v.value, conv, sink)};
}

// ==================== Floats ====================
FloatingConvertResult FormatConvertImpl(float v, const ConversionSpec conv,
                                        FormatSinkImpl *sink) {
  return {ConvertFloatArg(v, conv, sink)};
}
FloatingConvertResult FormatConvertImpl(double v, const ConversionSpec conv,
                                        FormatSinkImpl *sink) {
  return {ConvertFloatArg(v, conv, sink)};
}
FloatingConvertResult FormatConvertImpl(long double v,
                                        const ConversionSpec conv,
                                        FormatSinkImpl *sink) {
  return {ConvertFloatArg(v, conv, sink)};
}

// ==================== Chars ====================
IntegralConvertResult FormatConvertImpl(char v, const ConversionSpec conv,
                                        FormatSinkImpl *sink) {
  return {ConvertIntArg(v, conv, sink)};
}
IntegralConvertResult FormatConvertImpl(signed char v,
                                        const ConversionSpec conv,
                                        FormatSinkImpl *sink) {
  return {ConvertIntArg(v, conv, sink)};
}
IntegralConvertResult FormatConvertImpl(unsigned char v,
                                        const ConversionSpec conv,
                                        FormatSinkImpl *sink) {
  return {ConvertIntArg(v, conv, sink)};
}

// ==================== Ints ====================
IntegralConvertResult FormatConvertImpl(short v,  // NOLINT
                                        const ConversionSpec conv,
                                        FormatSinkImpl *sink) {
  return {ConvertIntArg(v, conv, sink)};
}
IntegralConvertResult FormatConvertImpl(unsigned short v,  // NOLINT
                                        const ConversionSpec conv,
                                        FormatSinkImpl *sink) {
  return {ConvertIntArg(v, conv, sink)};
}
IntegralConvertResult FormatConvertImpl(int v, const ConversionSpec conv,
                                        FormatSinkImpl *sink) {
  return {ConvertIntArg(v, conv, sink)};
}
IntegralConvertResult FormatConvertImpl(unsigned v, const ConversionSpec conv,
                                        FormatSinkImpl *sink) {
  return {ConvertIntArg(v, conv, sink)};
}
IntegralConvertResult FormatConvertImpl(long v,  // NOLINT
                                        const ConversionSpec conv,
                                        FormatSinkImpl *sink) {
  return {ConvertIntArg(v, conv, sink)};
}
IntegralConvertResult FormatConvertImpl(unsigned long v,  // NOLINT
                                        const ConversionSpec conv,
                                        FormatSinkImpl *sink) {
  return {ConvertIntArg(v, conv, sink)};
}
IntegralConvertResult FormatConvertImpl(long long v,  // NOLINT
                                        const ConversionSpec conv,
                                        FormatSinkImpl *sink) {
  return {ConvertIntArg(v, conv, sink)};
}
IntegralConvertResult FormatConvertImpl(unsigned long long v,  // NOLINT
                                        const ConversionSpec conv,
                                        FormatSinkImpl *sink) {
  return {ConvertIntArg(v, conv, sink)};
}
IntegralConvertResult FormatConvertImpl(absl::int128 v,
                                        const ConversionSpec conv,
                                        FormatSinkImpl *sink) {
  return {ConvertIntArg(v, conv, sink)};
}
IntegralConvertResult FormatConvertImpl(absl::uint128 v,
                                        const ConversionSpec conv,
                                        FormatSinkImpl *sink) {
  return {ConvertIntArg(v, conv, sink)};
}

ABSL_INTERNAL_FORMAT_DISPATCH_OVERLOADS_EXPAND_();



}  // namespace str_format_internal

ABSL_NAMESPACE_END
}  // namespace absl
