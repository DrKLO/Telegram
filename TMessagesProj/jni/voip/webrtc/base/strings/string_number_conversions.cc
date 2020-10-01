// Copyright (c) 2012 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

#include "base/strings/string_number_conversions.h"

#include <ctype.h>
#include <errno.h>
#include <stdlib.h>
#include <wctype.h>

#include <limits>
#include <type_traits>

#include "base/logging.h"
#include "base/no_destructor.h"
#include "base/numerics/safe_math.h"
#include "base/strings/string_util.h"
#include "base/strings/utf_string_conversions.h"
#include "base/third_party/double_conversion/double-conversion/double-conversion.h"

namespace base {

namespace {

template <typename STR, typename INT>
struct IntToStringT {
  static STR IntToString(INT value) {
    // log10(2) ~= 0.3 bytes needed per bit or per byte log10(2**8) ~= 2.4.
    // So round up to allocate 3 output characters per byte, plus 1 for '-'.
    const size_t kOutputBufSize =
        3 * sizeof(INT) + std::numeric_limits<INT>::is_signed;

    // Create the string in a temporary buffer, write it back to front, and
    // then return the substr of what we ended up using.
    using CHR = typename STR::value_type;
    CHR outbuf[kOutputBufSize];

    // The ValueOrDie call below can never fail, because UnsignedAbs is valid
    // for all valid inputs.
    typename std::make_unsigned<INT>::type res =
        CheckedNumeric<INT>(value).UnsignedAbs().ValueOrDie();

    CHR* end = outbuf + kOutputBufSize;
    CHR* i = end;
    do {
      --i;
      DCHECK(i != outbuf);
      *i = static_cast<CHR>((res % 10) + '0');
      res /= 10;
    } while (res != 0);
    if (IsValueNegative(value)) {
      --i;
      DCHECK(i != outbuf);
      *i = static_cast<CHR>('-');
    }
    return STR(i, end);
  }
};

// Utility to convert a character to a digit in a given base
template<typename CHAR, int BASE, bool BASE_LTE_10> class BaseCharToDigit {
};

// Faster specialization for bases <= 10
template<typename CHAR, int BASE> class BaseCharToDigit<CHAR, BASE, true> {
 public:
  static bool Convert(CHAR c, uint8_t* digit) {
    if (c >= '0' && c < '0' + BASE) {
      *digit = static_cast<uint8_t>(c - '0');
      return true;
    }
    return false;
  }
};

// Specialization for bases where 10 < base <= 36
template<typename CHAR, int BASE> class BaseCharToDigit<CHAR, BASE, false> {
 public:
  static bool Convert(CHAR c, uint8_t* digit) {
    if (c >= '0' && c <= '9') {
      *digit = c - '0';
    } else if (c >= 'a' && c < 'a' + BASE - 10) {
      *digit = c - 'a' + 10;
    } else if (c >= 'A' && c < 'A' + BASE - 10) {
      *digit = c - 'A' + 10;
    } else {
      return false;
    }
    return true;
  }
};

template <int BASE, typename CHAR>
bool CharToDigit(CHAR c, uint8_t* digit) {
  return BaseCharToDigit<CHAR, BASE, BASE <= 10>::Convert(c, digit);
}

// There is an IsUnicodeWhitespace for wchars defined in string_util.h, but it
// is locale independent, whereas the functions we are replacing were
// locale-dependent. TBD what is desired, but for the moment let's not
// introduce a change in behaviour.
template<typename CHAR> class WhitespaceHelper {
};

template<> class WhitespaceHelper<char> {
 public:
  static bool Invoke(char c) {
    return 0 != isspace(static_cast<unsigned char>(c));
  }
};

template<> class WhitespaceHelper<char16> {
 public:
  static bool Invoke(char16 c) {
    return 0 != iswspace(c);
  }
};

template<typename CHAR> bool LocalIsWhitespace(CHAR c) {
  return WhitespaceHelper<CHAR>::Invoke(c);
}

// IteratorRangeToNumberTraits should provide:
//  - a typedef for iterator_type, the iterator type used as input.
//  - a typedef for value_type, the target numeric type.
//  - static functions min, max (returning the minimum and maximum permitted
//    values)
//  - constant kBase, the base in which to interpret the input
template<typename IteratorRangeToNumberTraits>
class IteratorRangeToNumber {
 public:
  typedef IteratorRangeToNumberTraits traits;
  typedef typename traits::iterator_type const_iterator;
  typedef typename traits::value_type value_type;

  // Generalized iterator-range-to-number conversion.
  //
  static bool Invoke(const_iterator begin,
                     const_iterator end,
                     value_type* output) {
    bool valid = true;

    while (begin != end && LocalIsWhitespace(*begin)) {
      valid = false;
      ++begin;
    }

    if (begin != end && *begin == '-') {
      if (!std::numeric_limits<value_type>::is_signed) {
        *output = 0;
        valid = false;
      } else if (!Negative::Invoke(begin + 1, end, output)) {
        valid = false;
      }
    } else {
      if (begin != end && *begin == '+') {
        ++begin;
      }
      if (!Positive::Invoke(begin, end, output)) {
        valid = false;
      }
    }

    return valid;
  }

 private:
  // Sign provides:
  //  - a static function, CheckBounds, that determines whether the next digit
  //    causes an overflow/underflow
  //  - a static function, Increment, that appends the next digit appropriately
  //    according to the sign of the number being parsed.
  template<typename Sign>
  class Base {
   public:
    static bool Invoke(const_iterator begin, const_iterator end,
                       typename traits::value_type* output) {
      *output = 0;

      if (begin == end) {
        return false;
      }

      // Note: no performance difference was found when using template
      // specialization to remove this check in bases other than 16
      if (traits::kBase == 16 && end - begin > 2 && *begin == '0' &&
          (*(begin + 1) == 'x' || *(begin + 1) == 'X')) {
        begin += 2;
      }

      for (const_iterator current = begin; current != end; ++current) {
        uint8_t new_digit = 0;

        if (!CharToDigit<traits::kBase>(*current, &new_digit)) {
          return false;
        }

        if (current != begin) {
          if (!Sign::CheckBounds(output, new_digit)) {
            return false;
          }
          *output *= traits::kBase;
        }

        Sign::Increment(new_digit, output);
      }
      return true;
    }
  };

  class Positive : public Base<Positive> {
   public:
    static bool CheckBounds(value_type* output, uint8_t new_digit) {
      if (*output > static_cast<value_type>(traits::max() / traits::kBase) ||
          (*output == static_cast<value_type>(traits::max() / traits::kBase) &&
           new_digit > traits::max() % traits::kBase)) {
        *output = traits::max();
        return false;
      }
      return true;
    }
    static void Increment(uint8_t increment, value_type* output) {
      *output += increment;
    }
  };

  class Negative : public Base<Negative> {
   public:
    static bool CheckBounds(value_type* output, uint8_t new_digit) {
      if (*output < traits::min() / traits::kBase ||
          (*output == traits::min() / traits::kBase &&
           new_digit > 0 - traits::min() % traits::kBase)) {
        *output = traits::min();
        return false;
      }
      return true;
    }
    static void Increment(uint8_t increment, value_type* output) {
      *output -= increment;
    }
  };
};

template<typename ITERATOR, typename VALUE, int BASE>
class BaseIteratorRangeToNumberTraits {
 public:
  typedef ITERATOR iterator_type;
  typedef VALUE value_type;
  static value_type min() {
    return std::numeric_limits<value_type>::min();
  }
  static value_type max() {
    return std::numeric_limits<value_type>::max();
  }
  static const int kBase = BASE;
};

template<typename ITERATOR>
class BaseHexIteratorRangeToIntTraits
    : public BaseIteratorRangeToNumberTraits<ITERATOR, int, 16> {
};

template <typename ITERATOR>
class BaseHexIteratorRangeToUIntTraits
    : public BaseIteratorRangeToNumberTraits<ITERATOR, uint32_t, 16> {};

template <typename ITERATOR>
class BaseHexIteratorRangeToInt64Traits
    : public BaseIteratorRangeToNumberTraits<ITERATOR, int64_t, 16> {};

template <typename ITERATOR>
class BaseHexIteratorRangeToUInt64Traits
    : public BaseIteratorRangeToNumberTraits<ITERATOR, uint64_t, 16> {};

typedef BaseHexIteratorRangeToIntTraits<StringPiece::const_iterator>
    HexIteratorRangeToIntTraits;

typedef BaseHexIteratorRangeToUIntTraits<StringPiece::const_iterator>
    HexIteratorRangeToUIntTraits;

typedef BaseHexIteratorRangeToInt64Traits<StringPiece::const_iterator>
    HexIteratorRangeToInt64Traits;

typedef BaseHexIteratorRangeToUInt64Traits<StringPiece::const_iterator>
    HexIteratorRangeToUInt64Traits;

template <typename VALUE, int BASE>
class StringPieceToNumberTraits
    : public BaseIteratorRangeToNumberTraits<StringPiece::const_iterator,
                                             VALUE,
                                             BASE> {
};

template <typename VALUE>
bool StringToIntImpl(StringPiece input, VALUE* output) {
  return IteratorRangeToNumber<StringPieceToNumberTraits<VALUE, 10> >::Invoke(
      input.begin(), input.end(), output);
}

template <typename VALUE, int BASE>
class StringPiece16ToNumberTraits
    : public BaseIteratorRangeToNumberTraits<StringPiece16::const_iterator,
                                             VALUE,
                                             BASE> {
};

template <typename VALUE>
bool String16ToIntImpl(StringPiece16 input, VALUE* output) {
  return IteratorRangeToNumber<StringPiece16ToNumberTraits<VALUE, 10> >::Invoke(
      input.begin(), input.end(), output);
}

}  // namespace

std::string NumberToString(int value) {
  return IntToStringT<std::string, int>::IntToString(value);
}

string16 NumberToString16(int value) {
  return IntToStringT<string16, int>::IntToString(value);
}

std::string NumberToString(unsigned value) {
  return IntToStringT<std::string, unsigned>::IntToString(value);
}

string16 NumberToString16(unsigned value) {
  return IntToStringT<string16, unsigned>::IntToString(value);
}

std::string NumberToString(long value) {
  return IntToStringT<std::string, long>::IntToString(value);
}

string16 NumberToString16(long value) {
  return IntToStringT<string16, long>::IntToString(value);
}

std::string NumberToString(unsigned long value) {
  return IntToStringT<std::string, unsigned long>::IntToString(value);
}

string16 NumberToString16(unsigned long value) {
  return IntToStringT<string16, unsigned long>::IntToString(value);
}

std::string NumberToString(long long value) {
  return IntToStringT<std::string, long long>::IntToString(value);
}

string16 NumberToString16(long long value) {
  return IntToStringT<string16, long long>::IntToString(value);
}

std::string NumberToString(unsigned long long value) {
  return IntToStringT<std::string, unsigned long long>::IntToString(value);
}

string16 NumberToString16(unsigned long long value) {
  return IntToStringT<string16, unsigned long long>::IntToString(value);
}

static const double_conversion::DoubleToStringConverter*
GetDoubleToStringConverter() {
  static NoDestructor<double_conversion::DoubleToStringConverter> converter(
      double_conversion::DoubleToStringConverter::EMIT_POSITIVE_EXPONENT_SIGN,
      nullptr, nullptr, 'e', -6, 12, 0, 0);
  return converter.get();
}

std::string NumberToString(double value) {
  char buffer[32];
  double_conversion::StringBuilder builder(buffer, sizeof(buffer));
  GetDoubleToStringConverter()->ToShortest(value, &builder);
  return std::string(buffer, builder.position());
}

base::string16 NumberToString16(double value) {
  char buffer[32];
  double_conversion::StringBuilder builder(buffer, sizeof(buffer));
  GetDoubleToStringConverter()->ToShortest(value, &builder);

  // The number will be ASCII. This creates the string using the "input
  // iterator" variant which promotes from 8-bit to 16-bit via "=".
  return base::string16(&buffer[0], &buffer[builder.position()]);
}

bool StringToInt(StringPiece input, int* output) {
  return StringToIntImpl(input, output);
}

bool StringToInt(StringPiece16 input, int* output) {
  return String16ToIntImpl(input, output);
}

bool StringToUint(StringPiece input, unsigned* output) {
  return StringToIntImpl(input, output);
}

bool StringToUint(StringPiece16 input, unsigned* output) {
  return String16ToIntImpl(input, output);
}

bool StringToInt64(StringPiece input, int64_t* output) {
  return StringToIntImpl(input, output);
}

bool StringToInt64(StringPiece16 input, int64_t* output) {
  return String16ToIntImpl(input, output);
}

bool StringToUint64(StringPiece input, uint64_t* output) {
  return StringToIntImpl(input, output);
}

bool StringToUint64(StringPiece16 input, uint64_t* output) {
  return String16ToIntImpl(input, output);
}

bool StringToSizeT(StringPiece input, size_t* output) {
  return StringToIntImpl(input, output);
}

bool StringToSizeT(StringPiece16 input, size_t* output) {
  return String16ToIntImpl(input, output);
}

template <typename STRING, typename CHAR>
bool StringToDoubleImpl(STRING input, const CHAR* data, double* output) {
  static NoDestructor<double_conversion::StringToDoubleConverter> converter(
      double_conversion::StringToDoubleConverter::ALLOW_LEADING_SPACES |
          double_conversion::StringToDoubleConverter::ALLOW_TRAILING_JUNK,
      0.0, 0, nullptr, nullptr);

  int processed_characters_count;
  *output = converter->StringToDouble(data, input.size(),
                                      &processed_characters_count);

  // Cases to return false:
  //  - If the input string is empty, there was nothing to parse.
  //  - If the value saturated to HUGE_VAL.
  //  - If the entire string was not processed, there are either characters
  //    remaining in the string after a parsed number, or the string does not
  //    begin with a parseable number.
  //  - If the first character is a space, there was leading whitespace
  return !input.empty() && *output != HUGE_VAL && *output != -HUGE_VAL &&
         static_cast<size_t>(processed_characters_count) == input.size() &&
         !IsUnicodeWhitespace(input[0]);
}

bool StringToDouble(StringPiece input, double* output) {
  return StringToDoubleImpl(input, input.data(), output);
}

bool StringToDouble(StringPiece16 input, double* output) {
  return StringToDoubleImpl(
      input, reinterpret_cast<const uint16_t*>(input.data()), output);
}

std::string HexEncode(const void* bytes, size_t size) {
  static const char kHexChars[] = "0123456789ABCDEF";

  // Each input byte creates two output hex characters.
  std::string ret(size * 2, '\0');

  for (size_t i = 0; i < size; ++i) {
    char b = reinterpret_cast<const char*>(bytes)[i];
    ret[(i * 2)] = kHexChars[(b >> 4) & 0xf];
    ret[(i * 2) + 1] = kHexChars[b & 0xf];
  }
  return ret;
}

std::string HexEncode(base::span<const uint8_t> bytes) {
  return HexEncode(bytes.data(), bytes.size());
}

bool HexStringToInt(StringPiece input, int* output) {
  return IteratorRangeToNumber<HexIteratorRangeToIntTraits>::Invoke(
    input.begin(), input.end(), output);
}

bool HexStringToUInt(StringPiece input, uint32_t* output) {
  return IteratorRangeToNumber<HexIteratorRangeToUIntTraits>::Invoke(
      input.begin(), input.end(), output);
}

bool HexStringToInt64(StringPiece input, int64_t* output) {
  return IteratorRangeToNumber<HexIteratorRangeToInt64Traits>::Invoke(
    input.begin(), input.end(), output);
}

bool HexStringToUInt64(StringPiece input, uint64_t* output) {
  return IteratorRangeToNumber<HexIteratorRangeToUInt64Traits>::Invoke(
      input.begin(), input.end(), output);
}

template <typename Container>
static bool HexStringToByteContainer(StringPiece input, Container* output) {
  DCHECK_EQ(output->size(), 0u);
  size_t count = input.size();
  if (count == 0 || (count % 2) != 0)
    return false;
  for (uintptr_t i = 0; i < count / 2; ++i) {
    uint8_t msb = 0;  // most significant 4 bits
    uint8_t lsb = 0;  // least significant 4 bits
    if (!CharToDigit<16>(input[i * 2], &msb) ||
        !CharToDigit<16>(input[i * 2 + 1], &lsb)) {
      return false;
    }
    output->push_back((msb << 4) | lsb);
  }
  return true;
}

bool HexStringToBytes(StringPiece input, std::vector<uint8_t>* output) {
  return HexStringToByteContainer(input, output);
}

bool HexStringToString(StringPiece input, std::string* output) {
  return HexStringToByteContainer(input, output);
}

bool HexStringToSpan(StringPiece input, base::span<uint8_t> output) {
  size_t count = input.size();
  if (count == 0 || (count % 2) != 0)
    return false;

  if (count / 2 != output.size())
    return false;

  for (uintptr_t i = 0; i < count / 2; ++i) {
    uint8_t msb = 0;  // most significant 4 bits
    uint8_t lsb = 0;  // least significant 4 bits
    if (!CharToDigit<16>(input[i * 2], &msb) ||
        !CharToDigit<16>(input[i * 2 + 1], &lsb)) {
      return false;
    }
    output[i] = (msb << 4) | lsb;
  }
  return true;
}

}  // namespace base
