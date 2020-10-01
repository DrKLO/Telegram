// Copyright (c) 2018 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

#include "base/strings/utf_string_conversions.h"

#include <limits.h>
#include <stdint.h>

#include <type_traits>

#include "base/strings/string_piece.h"
#include "base/strings/string_util.h"
#include "base/strings/utf_string_conversion_utils.h"
#include "base/third_party/icu/icu_utf.h"
#include "build/build_config.h"

namespace base {

namespace {

constexpr int32_t kErrorCodePoint = 0xFFFD;

// Size coefficient ----------------------------------------------------------
// The maximum number of codeunits in the destination encoding corresponding to
// one codeunit in the source encoding.

template <typename SrcChar, typename DestChar>
struct SizeCoefficient {
  static_assert(sizeof(SrcChar) < sizeof(DestChar),
                "Default case: from a smaller encoding to the bigger one");

  // ASCII symbols are encoded by one codeunit in all encodings.
  static constexpr int value = 1;
};

template <>
struct SizeCoefficient<char16, char> {
  // One UTF-16 codeunit corresponds to at most 3 codeunits in UTF-8.
  static constexpr int value = 3;
};

#if defined(WCHAR_T_IS_UTF32)
template <>
struct SizeCoefficient<wchar_t, char> {
  // UTF-8 uses at most 4 codeunits per character.
  static constexpr int value = 4;
};

template <>
struct SizeCoefficient<wchar_t, char16> {
  // UTF-16 uses at most 2 codeunits per character.
  static constexpr int value = 2;
};
#endif  // defined(WCHAR_T_IS_UTF32)

template <typename SrcChar, typename DestChar>
constexpr int size_coefficient_v =
    SizeCoefficient<std::decay_t<SrcChar>, std::decay_t<DestChar>>::value;

// UnicodeAppendUnsafe --------------------------------------------------------
// Function overloads that write code_point to the output string. Output string
// has to have enough space for the codepoint.

// Convenience typedef that checks whether the passed in type is integral (i.e.
// bool, char, int or their extended versions) and is of the correct size.
template <typename Char, size_t N>
using EnableIfBitsAre = std::enable_if_t<std::is_integral<Char>::value &&
                                             CHAR_BIT * sizeof(Char) == N,
                                         bool>;

template <typename Char, EnableIfBitsAre<Char, 8> = true>
void UnicodeAppendUnsafe(Char* out, int32_t* size, uint32_t code_point) {
  CBU8_APPEND_UNSAFE(out, *size, code_point);
}

template <typename Char, EnableIfBitsAre<Char, 16> = true>
void UnicodeAppendUnsafe(Char* out, int32_t* size, uint32_t code_point) {
  CBU16_APPEND_UNSAFE(out, *size, code_point);
}

template <typename Char, EnableIfBitsAre<Char, 32> = true>
void UnicodeAppendUnsafe(Char* out, int32_t* size, uint32_t code_point) {
  out[(*size)++] = code_point;
}

// DoUTFConversion ------------------------------------------------------------
// Main driver of UTFConversion specialized for different Src encodings.
// dest has to have enough room for the converted text.

template <typename DestChar>
bool DoUTFConversion(const char* src,
                     int32_t src_len,
                     DestChar* dest,
                     int32_t* dest_len) {
  bool success = true;

  for (int32_t i = 0; i < src_len;) {
    int32_t code_point;
    CBU8_NEXT(src, i, src_len, code_point);

    if (!IsValidCodepoint(code_point)) {
      success = false;
      code_point = kErrorCodePoint;
    }

    UnicodeAppendUnsafe(dest, dest_len, code_point);
  }

  return success;
}

template <typename DestChar>
bool DoUTFConversion(const char16* src,
                     int32_t src_len,
                     DestChar* dest,
                     int32_t* dest_len) {
  bool success = true;

  auto ConvertSingleChar = [&success](char16 in) -> int32_t {
    if (!CBU16_IS_SINGLE(in) || !IsValidCodepoint(in)) {
      success = false;
      return kErrorCodePoint;
    }
    return in;
  };

  int32_t i = 0;

  // Always have another symbol in order to avoid checking boundaries in the
  // middle of the surrogate pair.
  while (i < src_len - 1) {
    int32_t code_point;

    if (CBU16_IS_LEAD(src[i]) && CBU16_IS_TRAIL(src[i + 1])) {
      code_point = CBU16_GET_SUPPLEMENTARY(src[i], src[i + 1]);
      if (!IsValidCodepoint(code_point)) {
        code_point = kErrorCodePoint;
        success = false;
      }
      i += 2;
    } else {
      code_point = ConvertSingleChar(src[i]);
      ++i;
    }

    UnicodeAppendUnsafe(dest, dest_len, code_point);
  }

  if (i < src_len)
    UnicodeAppendUnsafe(dest, dest_len, ConvertSingleChar(src[i]));

  return success;
}

#if defined(WCHAR_T_IS_UTF32)

template <typename DestChar>
bool DoUTFConversion(const wchar_t* src,
                     int32_t src_len,
                     DestChar* dest,
                     int32_t* dest_len) {
  bool success = true;

  for (int32_t i = 0; i < src_len; ++i) {
    int32_t code_point = src[i];

    if (!IsValidCodepoint(code_point)) {
      success = false;
      code_point = kErrorCodePoint;
    }

    UnicodeAppendUnsafe(dest, dest_len, code_point);
  }

  return success;
}

#endif  // defined(WCHAR_T_IS_UTF32)

// UTFConversion --------------------------------------------------------------
// Function template for generating all UTF conversions.

template <typename InputString, typename DestString>
bool UTFConversion(const InputString& src_str, DestString* dest_str) {
  if (IsStringASCII(src_str)) {
    dest_str->assign(src_str.begin(), src_str.end());
    return true;
  }

  dest_str->resize(src_str.length() *
                   size_coefficient_v<typename InputString::value_type,
                                      typename DestString::value_type>);

  // Empty string is ASCII => it OK to call operator[].
  auto* dest = &(*dest_str)[0];

  // ICU requires 32 bit numbers.
  int32_t src_len32 = static_cast<int32_t>(src_str.length());
  int32_t dest_len32 = 0;

  bool res = DoUTFConversion(src_str.data(), src_len32, dest, &dest_len32);

  dest_str->resize(dest_len32);
  dest_str->shrink_to_fit();

  return res;
}

}  // namespace

// UTF16 <-> UTF8 --------------------------------------------------------------

bool UTF8ToUTF16(const char* src, size_t src_len, string16* output) {
  return UTFConversion(StringPiece(src, src_len), output);
}

string16 UTF8ToUTF16(StringPiece utf8) {
  string16 ret;
  // Ignore the success flag of this call, it will do the best it can for
  // invalid input, which is what we want here.
  UTF8ToUTF16(utf8.data(), utf8.size(), &ret);
  return ret;
}

bool UTF16ToUTF8(const char16* src, size_t src_len, std::string* output) {
  return UTFConversion(StringPiece16(src, src_len), output);
}

std::string UTF16ToUTF8(StringPiece16 utf16) {
  std::string ret;
  // Ignore the success flag of this call, it will do the best it can for
  // invalid input, which is what we want here.
  UTF16ToUTF8(utf16.data(), utf16.length(), &ret);
  return ret;
}

// UTF-16 <-> Wide -------------------------------------------------------------

#if defined(WCHAR_T_IS_UTF16)
// When wide == UTF-16 the conversions are a NOP.

bool WideToUTF16(const wchar_t* src, size_t src_len, string16* output) {
  output->assign(src, src + src_len);
  return true;
}

string16 WideToUTF16(WStringPiece wide) {
  return string16(wide.begin(), wide.end());
}

bool UTF16ToWide(const char16* src, size_t src_len, std::wstring* output) {
  output->assign(src, src + src_len);
  return true;
}

std::wstring UTF16ToWide(StringPiece16 utf16) {
  return std::wstring(utf16.begin(), utf16.end());
}

#elif defined(WCHAR_T_IS_UTF32)

bool WideToUTF16(const wchar_t* src, size_t src_len, string16* output) {
  return UTFConversion(base::WStringPiece(src, src_len), output);
}

string16 WideToUTF16(WStringPiece wide) {
  string16 ret;
  // Ignore the success flag of this call, it will do the best it can for
  // invalid input, which is what we want here.
  WideToUTF16(wide.data(), wide.length(), &ret);
  return ret;
}

bool UTF16ToWide(const char16* src, size_t src_len, std::wstring* output) {
  return UTFConversion(StringPiece16(src, src_len), output);
}

std::wstring UTF16ToWide(StringPiece16 utf16) {
  std::wstring ret;
  // Ignore the success flag of this call, it will do the best it can for
  // invalid input, which is what we want here.
  UTF16ToWide(utf16.data(), utf16.length(), &ret);
  return ret;
}

#endif  // defined(WCHAR_T_IS_UTF32)

// UTF-8 <-> Wide --------------------------------------------------------------

// UTF8ToWide is the same code, regardless of whether wide is 16 or 32 bits

bool UTF8ToWide(const char* src, size_t src_len, std::wstring* output) {
  return UTFConversion(StringPiece(src, src_len), output);
}

std::wstring UTF8ToWide(StringPiece utf8) {
  std::wstring ret;
  // Ignore the success flag of this call, it will do the best it can for
  // invalid input, which is what we want here.
  UTF8ToWide(utf8.data(), utf8.length(), &ret);
  return ret;
}

#if defined(WCHAR_T_IS_UTF16)
// Easy case since we can use the "utf" versions we already wrote above.

bool WideToUTF8(const wchar_t* src, size_t src_len, std::string* output) {
  return UTF16ToUTF8(as_u16cstr(src), src_len, output);
}

std::string WideToUTF8(WStringPiece wide) {
  return UTF16ToUTF8(StringPiece16(as_u16cstr(wide), wide.size()));
}

#elif defined(WCHAR_T_IS_UTF32)

bool WideToUTF8(const wchar_t* src, size_t src_len, std::string* output) {
  return UTFConversion(WStringPiece(src, src_len), output);
}

std::string WideToUTF8(WStringPiece wide) {
  std::string ret;
  // Ignore the success flag of this call, it will do the best it can for
  // invalid input, which is what we want here.
  WideToUTF8(wide.data(), wide.length(), &ret);
  return ret;
}

#endif  // defined(WCHAR_T_IS_UTF32)

string16 ASCIIToUTF16(StringPiece ascii) {
  DCHECK(IsStringASCII(ascii)) << ascii;
  return string16(ascii.begin(), ascii.end());
}

std::string UTF16ToASCII(StringPiece16 utf16) {
  DCHECK(IsStringASCII(utf16)) << UTF16ToUTF8(utf16);
  return std::string(utf16.begin(), utf16.end());
}

}  // namespace base
