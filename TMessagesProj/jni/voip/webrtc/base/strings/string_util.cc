// Copyright 2013 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

#include "base/strings/string_util.h"

#include <ctype.h>
#include <errno.h>
#include <math.h>
#include <stdarg.h>
#include <stdint.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <time.h>
#include <wchar.h>
#include <wctype.h>

#include <algorithm>
#include <limits>
#include <vector>

#include "base/logging.h"
#include "base/no_destructor.h"
#include "base/stl_util.h"
#include "base/strings/utf_string_conversion_utils.h"
#include "base/strings/utf_string_conversions.h"
#include "base/third_party/icu/icu_utf.h"
#include "build/build_config.h"

namespace base {

namespace {

// Used by ReplaceStringPlaceholders to track the position in the string of
// replaced parameters.
struct ReplacementOffset {
  ReplacementOffset(uintptr_t parameter, size_t offset)
      : parameter(parameter),
        offset(offset) {}

  // Index of the parameter.
  uintptr_t parameter;

  // Starting position in the string.
  size_t offset;
};

static bool CompareParameter(const ReplacementOffset& elem1,
                             const ReplacementOffset& elem2) {
  return elem1.parameter < elem2.parameter;
}

// Assuming that a pointer is the size of a "machine word", then
// uintptr_t is an integer type that is also a machine word.
using MachineWord = uintptr_t;

inline bool IsMachineWordAligned(const void* pointer) {
  return !(reinterpret_cast<MachineWord>(pointer) & (sizeof(MachineWord) - 1));
}

template <typename CharacterType>
struct NonASCIIMask;
template <>
struct NonASCIIMask<char> {
  static constexpr MachineWord value() {
    return static_cast<MachineWord>(0x8080808080808080ULL);
  }
};
template <>
struct NonASCIIMask<char16> {
  static constexpr MachineWord value() {
    return static_cast<MachineWord>(0xFF80FF80FF80FF80ULL);
  }
};
#if defined(WCHAR_T_IS_UTF32)
template <>
struct NonASCIIMask<wchar_t> {
  static constexpr MachineWord value() {
    return static_cast<MachineWord>(0xFFFFFF80FFFFFF80ULL);
  }
};
#endif  // WCHAR_T_IS_UTF32

}  // namespace

bool IsWprintfFormatPortable(const wchar_t* format) {
  for (const wchar_t* position = format; *position != '\0'; ++position) {
    if (*position == '%') {
      bool in_specification = true;
      bool modifier_l = false;
      while (in_specification) {
        // Eat up characters until reaching a known specifier.
        if (*++position == '\0') {
          // The format string ended in the middle of a specification.  Call
          // it portable because no unportable specifications were found.  The
          // string is equally broken on all platforms.
          return true;
        }

        if (*position == 'l') {
          // 'l' is the only thing that can save the 's' and 'c' specifiers.
          modifier_l = true;
        } else if (((*position == 's' || *position == 'c') && !modifier_l) ||
                   *position == 'S' || *position == 'C' || *position == 'F' ||
                   *position == 'D' || *position == 'O' || *position == 'U') {
          // Not portable.
          return false;
        }

        if (wcschr(L"diouxXeEfgGaAcspn%", *position)) {
          // Portable, keep scanning the rest of the format string.
          in_specification = false;
        }
      }
    }
  }

  return true;
}

namespace {

template<typename StringType>
StringType ToLowerASCIIImpl(BasicStringPiece<StringType> str) {
  StringType ret;
  ret.reserve(str.size());
  for (size_t i = 0; i < str.size(); i++)
    ret.push_back(ToLowerASCII(str[i]));
  return ret;
}

template<typename StringType>
StringType ToUpperASCIIImpl(BasicStringPiece<StringType> str) {
  StringType ret;
  ret.reserve(str.size());
  for (size_t i = 0; i < str.size(); i++)
    ret.push_back(ToUpperASCII(str[i]));
  return ret;
}

}  // namespace

std::string ToLowerASCII(StringPiece str) {
  return ToLowerASCIIImpl<std::string>(str);
}

string16 ToLowerASCII(StringPiece16 str) {
  return ToLowerASCIIImpl<string16>(str);
}

std::string ToUpperASCII(StringPiece str) {
  return ToUpperASCIIImpl<std::string>(str);
}

string16 ToUpperASCII(StringPiece16 str) {
  return ToUpperASCIIImpl<string16>(str);
}

template<class StringType>
int CompareCaseInsensitiveASCIIT(BasicStringPiece<StringType> a,
                                 BasicStringPiece<StringType> b) {
  // Find the first characters that aren't equal and compare them.  If the end
  // of one of the strings is found before a nonequal character, the lengths
  // of the strings are compared.
  size_t i = 0;
  while (i < a.length() && i < b.length()) {
    typename StringType::value_type lower_a = ToLowerASCII(a[i]);
    typename StringType::value_type lower_b = ToLowerASCII(b[i]);
    if (lower_a < lower_b)
      return -1;
    if (lower_a > lower_b)
      return 1;
    i++;
  }

  // End of one string hit before finding a different character. Expect the
  // common case to be "strings equal" at this point so check that first.
  if (a.length() == b.length())
    return 0;

  if (a.length() < b.length())
    return -1;
  return 1;
}

int CompareCaseInsensitiveASCII(StringPiece a, StringPiece b) {
  return CompareCaseInsensitiveASCIIT<std::string>(a, b);
}

int CompareCaseInsensitiveASCII(StringPiece16 a, StringPiece16 b) {
  return CompareCaseInsensitiveASCIIT<string16>(a, b);
}

bool EqualsCaseInsensitiveASCII(StringPiece a, StringPiece b) {
  if (a.length() != b.length())
    return false;
  return CompareCaseInsensitiveASCIIT<std::string>(a, b) == 0;
}

bool EqualsCaseInsensitiveASCII(StringPiece16 a, StringPiece16 b) {
  if (a.length() != b.length())
    return false;
  return CompareCaseInsensitiveASCIIT<string16>(a, b) == 0;
}

const std::string& EmptyString() {
  static const base::NoDestructor<std::string> s;
  return *s;
}

const string16& EmptyString16() {
  static const base::NoDestructor<string16> s16;
  return *s16;
}

template <class StringType>
bool ReplaceCharsT(const StringType& input,
                   BasicStringPiece<StringType> find_any_of_these,
                   BasicStringPiece<StringType> replace_with,
                   StringType* output);

bool ReplaceChars(const string16& input,
                  StringPiece16 replace_chars,
                  StringPiece16 replace_with,
                  string16* output) {
  return ReplaceCharsT(input, replace_chars, replace_with, output);
}

bool ReplaceChars(const std::string& input,
                  StringPiece replace_chars,
                  StringPiece replace_with,
                  std::string* output) {
  return ReplaceCharsT(input, replace_chars, replace_with, output);
}

bool RemoveChars(const string16& input,
                 StringPiece16 remove_chars,
                 string16* output) {
  return ReplaceCharsT(input, remove_chars, StringPiece16(), output);
}

bool RemoveChars(const std::string& input,
                 StringPiece remove_chars,
                 std::string* output) {
  return ReplaceCharsT(input, remove_chars, StringPiece(), output);
}

template <typename Str>
TrimPositions TrimStringT(BasicStringPiece<Str> input,
                          BasicStringPiece<Str> trim_chars,
                          TrimPositions positions,
                          Str* output) {
  // Find the edges of leading/trailing whitespace as desired. Need to use
  // a StringPiece version of input to be able to call find* on it with the
  // StringPiece version of trim_chars (normally the trim_chars will be a
  // constant so avoid making a copy).
  const size_t last_char = input.length() - 1;
  const size_t first_good_char =
      (positions & TRIM_LEADING) ? input.find_first_not_of(trim_chars) : 0;
  const size_t last_good_char = (positions & TRIM_TRAILING)
                                    ? input.find_last_not_of(trim_chars)
                                    : last_char;

  // When the string was all trimmed, report that we stripped off characters
  // from whichever position the caller was interested in. For empty input, we
  // stripped no characters, but we still need to clear |output|.
  if (input.empty() || first_good_char == Str::npos ||
      last_good_char == Str::npos) {
    bool input_was_empty = input.empty();  // in case output == &input
    output->clear();
    return input_was_empty ? TRIM_NONE : positions;
  }

  // Trim.
  output->assign(input.data() + first_good_char,
                 last_good_char - first_good_char + 1);

  // Return where we trimmed from.
  return static_cast<TrimPositions>(
      (first_good_char == 0 ? TRIM_NONE : TRIM_LEADING) |
      (last_good_char == last_char ? TRIM_NONE : TRIM_TRAILING));
}

bool TrimString(StringPiece16 input,
                StringPiece16 trim_chars,
                string16* output) {
  return TrimStringT(input, trim_chars, TRIM_ALL, output) != TRIM_NONE;
}

bool TrimString(StringPiece input,
                StringPiece trim_chars,
                std::string* output) {
  return TrimStringT(input, trim_chars, TRIM_ALL, output) != TRIM_NONE;
}

template<typename Str>
BasicStringPiece<Str> TrimStringPieceT(BasicStringPiece<Str> input,
                                       BasicStringPiece<Str> trim_chars,
                                       TrimPositions positions) {
  size_t begin = (positions & TRIM_LEADING) ?
      input.find_first_not_of(trim_chars) : 0;
  size_t end = (positions & TRIM_TRAILING) ?
      input.find_last_not_of(trim_chars) + 1 : input.size();
  return input.substr(begin, end - begin);
}

StringPiece16 TrimString(StringPiece16 input,
                         StringPiece16 trim_chars,
                         TrimPositions positions) {
  return TrimStringPieceT(input, trim_chars, positions);
}

StringPiece TrimString(StringPiece input,
                       StringPiece trim_chars,
                       TrimPositions positions) {
  return TrimStringPieceT(input, trim_chars, positions);
}

void TruncateUTF8ToByteSize(const std::string& input,
                            const size_t byte_size,
                            std::string* output) {
  DCHECK(output);
  if (byte_size > input.length()) {
    *output = input;
    return;
  }
  DCHECK_LE(byte_size,
            static_cast<uint32_t>(std::numeric_limits<int32_t>::max()));
  // Note: This cast is necessary because CBU8_NEXT uses int32_ts.
  int32_t truncation_length = static_cast<int32_t>(byte_size);
  int32_t char_index = truncation_length - 1;
  const char* data = input.data();

  // Using CBU8, we will move backwards from the truncation point
  // to the beginning of the string looking for a valid UTF8
  // character.  Once a full UTF8 character is found, we will
  // truncate the string to the end of that character.
  while (char_index >= 0) {
    int32_t prev = char_index;
    base_icu::UChar32 code_point = 0;
    CBU8_NEXT(data, char_index, truncation_length, code_point);
    if (!IsValidCharacter(code_point) ||
        !IsValidCodepoint(code_point)) {
      char_index = prev - 1;
    } else {
      break;
    }
  }

  if (char_index >= 0 )
    *output = input.substr(0, char_index);
  else
    output->clear();
}

TrimPositions TrimWhitespace(StringPiece16 input,
                             TrimPositions positions,
                             string16* output) {
  return TrimStringT(input, StringPiece16(kWhitespaceUTF16), positions, output);
}

StringPiece16 TrimWhitespace(StringPiece16 input,
                             TrimPositions positions) {
  return TrimStringPieceT(input, StringPiece16(kWhitespaceUTF16), positions);
}

TrimPositions TrimWhitespaceASCII(StringPiece input,
                                  TrimPositions positions,
                                  std::string* output) {
  return TrimStringT(input, StringPiece(kWhitespaceASCII), positions, output);
}

StringPiece TrimWhitespaceASCII(StringPiece input, TrimPositions positions) {
  return TrimStringPieceT(input, StringPiece(kWhitespaceASCII), positions);
}

template<typename STR>
STR CollapseWhitespaceT(const STR& text,
                        bool trim_sequences_with_line_breaks) {
  STR result;
  result.resize(text.size());

  // Set flags to pretend we're already in a trimmed whitespace sequence, so we
  // will trim any leading whitespace.
  bool in_whitespace = true;
  bool already_trimmed = true;

  int chars_written = 0;
  for (typename STR::const_iterator i(text.begin()); i != text.end(); ++i) {
    if (IsUnicodeWhitespace(*i)) {
      if (!in_whitespace) {
        // Reduce all whitespace sequences to a single space.
        in_whitespace = true;
        result[chars_written++] = L' ';
      }
      if (trim_sequences_with_line_breaks && !already_trimmed &&
          ((*i == '\n') || (*i == '\r'))) {
        // Whitespace sequences containing CR or LF are eliminated entirely.
        already_trimmed = true;
        --chars_written;
      }
    } else {
      // Non-whitespace chracters are copied straight across.
      in_whitespace = false;
      already_trimmed = false;
      result[chars_written++] = *i;
    }
  }

  if (in_whitespace && !already_trimmed) {
    // Any trailing whitespace is eliminated.
    --chars_written;
  }

  result.resize(chars_written);
  return result;
}

string16 CollapseWhitespace(const string16& text,
                            bool trim_sequences_with_line_breaks) {
  return CollapseWhitespaceT(text, trim_sequences_with_line_breaks);
}

std::string CollapseWhitespaceASCII(const std::string& text,
                                    bool trim_sequences_with_line_breaks) {
  return CollapseWhitespaceT(text, trim_sequences_with_line_breaks);
}

bool ContainsOnlyChars(StringPiece input, StringPiece characters) {
  return input.find_first_not_of(characters) == StringPiece::npos;
}

bool ContainsOnlyChars(StringPiece16 input, StringPiece16 characters) {
  return input.find_first_not_of(characters) == StringPiece16::npos;
}

template <class Char>
inline bool DoIsStringASCII(const Char* characters, size_t length) {
  if (!length)
    return true;
  constexpr MachineWord non_ascii_bit_mask = NonASCIIMask<Char>::value();
  MachineWord all_char_bits = 0;
  const Char* end = characters + length;

  // Prologue: align the input.
  while (!IsMachineWordAligned(characters) && characters < end)
    all_char_bits |= *characters++;
  if (all_char_bits & non_ascii_bit_mask)
    return false;

  // Compare the values of CPU word size.
  constexpr size_t chars_per_word = sizeof(MachineWord) / sizeof(Char);
  constexpr int batch_count = 16;
  while (characters <= end - batch_count * chars_per_word) {
    all_char_bits = 0;
    for (int i = 0; i < batch_count; ++i) {
      all_char_bits |= *(reinterpret_cast<const MachineWord*>(characters));
      characters += chars_per_word;
    }
    if (all_char_bits & non_ascii_bit_mask)
      return false;
  }

  // Process the remaining words.
  all_char_bits = 0;
  while (characters <= end - chars_per_word) {
    all_char_bits |= *(reinterpret_cast<const MachineWord*>(characters));
    characters += chars_per_word;
  }

  // Process the remaining bytes.
  while (characters < end)
    all_char_bits |= *characters++;

  return !(all_char_bits & non_ascii_bit_mask);
}

bool IsStringASCII(StringPiece str) {
  return DoIsStringASCII(str.data(), str.length());
}

bool IsStringASCII(StringPiece16 str) {
  return DoIsStringASCII(str.data(), str.length());
}

#if defined(WCHAR_T_IS_UTF32)
bool IsStringASCII(WStringPiece str) {
  return DoIsStringASCII(str.data(), str.length());
}
#endif

template <bool (*Validator)(uint32_t)>
inline static bool DoIsStringUTF8(StringPiece str) {
  const char* src = str.data();
  int32_t src_len = static_cast<int32_t>(str.length());
  int32_t char_index = 0;

  while (char_index < src_len) {
    int32_t code_point;
    CBU8_NEXT(src, char_index, src_len, code_point);
    if (!Validator(code_point))
      return false;
  }
  return true;
}

bool IsStringUTF8(StringPiece str) {
  return DoIsStringUTF8<IsValidCharacter>(str);
}

bool IsStringUTF8AllowingNoncharacters(StringPiece str) {
  return DoIsStringUTF8<IsValidCodepoint>(str);
}

// Implementation note: Normally this function will be called with a hardcoded
// constant for the lowercase_ascii parameter. Constructing a StringPiece from
// a C constant requires running strlen, so the result will be two passes
// through the buffers, one to file the length of lowercase_ascii, and one to
// compare each letter.
//
// This function could have taken a const char* to avoid this and only do one
// pass through the string. But the strlen is faster than the case-insensitive
// compares and lets us early-exit in the case that the strings are different
// lengths (will often be the case for non-matches). So whether one approach or
// the other will be faster depends on the case.
//
// The hardcoded strings are typically very short so it doesn't matter, and the
// string piece gives additional flexibility for the caller (doesn't have to be
// null terminated) so we choose the StringPiece route.
template<typename Str>
static inline bool DoLowerCaseEqualsASCII(BasicStringPiece<Str> str,
                                          StringPiece lowercase_ascii) {
  if (str.size() != lowercase_ascii.size())
    return false;
  for (size_t i = 0; i < str.size(); i++) {
    if (ToLowerASCII(str[i]) != lowercase_ascii[i])
      return false;
  }
  return true;
}

bool LowerCaseEqualsASCII(StringPiece str, StringPiece lowercase_ascii) {
  return DoLowerCaseEqualsASCII<std::string>(str, lowercase_ascii);
}

bool LowerCaseEqualsASCII(StringPiece16 str, StringPiece lowercase_ascii) {
  return DoLowerCaseEqualsASCII<string16>(str, lowercase_ascii);
}

bool EqualsASCII(StringPiece16 str, StringPiece ascii) {
  if (str.length() != ascii.length())
    return false;
  return std::equal(ascii.begin(), ascii.end(), str.begin());
}

template<typename Str>
bool StartsWithT(BasicStringPiece<Str> str,
                 BasicStringPiece<Str> search_for,
                 CompareCase case_sensitivity) {
  if (search_for.size() > str.size())
    return false;

  BasicStringPiece<Str> source = str.substr(0, search_for.size());

  switch (case_sensitivity) {
    case CompareCase::SENSITIVE:
      return source == search_for;

    case CompareCase::INSENSITIVE_ASCII:
      return std::equal(
          search_for.begin(), search_for.end(),
          source.begin(),
          CaseInsensitiveCompareASCII<typename Str::value_type>());

    default:
      NOTREACHED();
      return false;
  }
}

bool StartsWith(StringPiece str,
                StringPiece search_for,
                CompareCase case_sensitivity) {
  return StartsWithT<std::string>(str, search_for, case_sensitivity);
}

bool StartsWith(StringPiece16 str,
                StringPiece16 search_for,
                CompareCase case_sensitivity) {
  return StartsWithT<string16>(str, search_for, case_sensitivity);
}

template <typename Str>
bool EndsWithT(BasicStringPiece<Str> str,
               BasicStringPiece<Str> search_for,
               CompareCase case_sensitivity) {
  if (search_for.size() > str.size())
    return false;

  BasicStringPiece<Str> source = str.substr(str.size() - search_for.size(),
                                            search_for.size());

  switch (case_sensitivity) {
    case CompareCase::SENSITIVE:
      return source == search_for;

    case CompareCase::INSENSITIVE_ASCII:
      return std::equal(
          source.begin(), source.end(),
          search_for.begin(),
          CaseInsensitiveCompareASCII<typename Str::value_type>());

    default:
      NOTREACHED();
      return false;
  }
}

bool EndsWith(StringPiece str,
              StringPiece search_for,
              CompareCase case_sensitivity) {
  return EndsWithT<std::string>(str, search_for, case_sensitivity);
}

bool EndsWith(StringPiece16 str,
              StringPiece16 search_for,
              CompareCase case_sensitivity) {
  return EndsWithT<string16>(str, search_for, case_sensitivity);
}

char HexDigitToInt(wchar_t c) {
  DCHECK(IsHexDigit(c));
  if (c >= '0' && c <= '9')
    return static_cast<char>(c - '0');
  if (c >= 'A' && c <= 'F')
    return static_cast<char>(c - 'A' + 10);
  if (c >= 'a' && c <= 'f')
    return static_cast<char>(c - 'a' + 10);
  return 0;
}

bool IsUnicodeWhitespace(wchar_t c) {
  // kWhitespaceWide is a NULL-terminated string
  for (const wchar_t* cur = kWhitespaceWide; *cur; ++cur) {
    if (*cur == c)
      return true;
  }
  return false;
}

static const char* const kByteStringsUnlocalized[] = {
  " B",
  " kB",
  " MB",
  " GB",
  " TB",
  " PB"
};

string16 FormatBytesUnlocalized(int64_t bytes) {
  double unit_amount = static_cast<double>(bytes);
  size_t dimension = 0;
  const int kKilo = 1024;
  while (unit_amount >= kKilo &&
         dimension < base::size(kByteStringsUnlocalized) - 1) {
    unit_amount /= kKilo;
    dimension++;
  }

  char buf[64];
  if (bytes != 0 && dimension > 0 && unit_amount < 100) {
    base::snprintf(buf, base::size(buf), "%.1lf%s", unit_amount,
                   kByteStringsUnlocalized[dimension]);
  } else {
    base::snprintf(buf, base::size(buf), "%.0lf%s", unit_amount,
                   kByteStringsUnlocalized[dimension]);
  }

  return ASCIIToUTF16(buf);
}

// A Matcher for DoReplaceMatchesAfterOffset() that matches substrings.
template <class StringType>
struct SubstringMatcher {
  BasicStringPiece<StringType> find_this;

  size_t Find(const StringType& input, size_t pos) {
    return input.find(find_this.data(), pos, find_this.length());
  }
  size_t MatchSize() { return find_this.length(); }
};

// A Matcher for DoReplaceMatchesAfterOffset() that matches single characters.
template <class StringType>
struct CharacterMatcher {
  BasicStringPiece<StringType> find_any_of_these;

  size_t Find(const StringType& input, size_t pos) {
    return input.find_first_of(find_any_of_these.data(), pos,
                               find_any_of_these.length());
  }
  constexpr size_t MatchSize() { return 1; }
};

enum class ReplaceType { REPLACE_ALL, REPLACE_FIRST };

// Runs in O(n) time in the length of |str|, and transforms the string without
// reallocating when possible. Returns |true| if any matches were found.
//
// This is parameterized on a |Matcher| traits type, so that it can be the
// implementation for both ReplaceChars() and ReplaceSubstringsAfterOffset().
template <class StringType, class Matcher>
bool DoReplaceMatchesAfterOffset(StringType* str,
                                 size_t initial_offset,
                                 Matcher matcher,
                                 BasicStringPiece<StringType> replace_with,
                                 ReplaceType replace_type) {
  using CharTraits = typename StringType::traits_type;

  const size_t find_length = matcher.MatchSize();
  if (!find_length)
    return false;

  // If the find string doesn't appear, there's nothing to do.
  size_t first_match = matcher.Find(*str, initial_offset);
  if (first_match == StringType::npos)
    return false;

  // If we're only replacing one instance, there's no need to do anything
  // complicated.
  const size_t replace_length = replace_with.length();
  if (replace_type == ReplaceType::REPLACE_FIRST) {
    str->replace(first_match, find_length, replace_with.data(), replace_length);
    return true;
  }

  // If the find and replace strings are the same length, we can simply use
  // replace() on each instance, and finish the entire operation in O(n) time.
  if (find_length == replace_length) {
    auto* buffer = &((*str)[0]);
    for (size_t offset = first_match; offset != StringType::npos;
         offset = matcher.Find(*str, offset + replace_length)) {
      CharTraits::copy(buffer + offset, replace_with.data(), replace_length);
    }
    return true;
  }

  // Since the find and replace strings aren't the same length, a loop like the
  // one above would be O(n^2) in the worst case, as replace() will shift the
  // entire remaining string each time. We need to be more clever to keep things
  // O(n).
  //
  // When the string is being shortened, it's possible to just shift the matches
  // down in one pass while finding, and truncate the length at the end of the
  // search.
  //
  // If the string is being lengthened, more work is required. The strategy used
  // here is to make two find() passes through the string. The first pass counts
  // the number of matches to determine the new size. The second pass will
  // either construct the new string into a new buffer (if the existing buffer
  // lacked capacity), or else -- if there is room -- create a region of scratch
  // space after |first_match| by shifting the tail of the string to a higher
  // index, and doing in-place moves from the tail to lower indices thereafter.
  size_t str_length = str->length();
  size_t expansion = 0;
  if (replace_length > find_length) {
    // This operation lengthens the string; determine the new length by counting
    // matches.
    const size_t expansion_per_match = (replace_length - find_length);
    size_t num_matches = 0;
    for (size_t match = first_match; match != StringType::npos;
         match = matcher.Find(*str, match + find_length)) {
      expansion += expansion_per_match;
      ++num_matches;
    }
    const size_t final_length = str_length + expansion;

    if (str->capacity() < final_length) {
      // If we'd have to allocate a new buffer to grow the string, build the
      // result directly into the new allocation via append().
      StringType src(str->get_allocator());
      str->swap(src);
      str->reserve(final_length);

      size_t pos = 0;
      for (size_t match = first_match;; match = matcher.Find(src, pos)) {
        str->append(src, pos, match - pos);
        str->append(replace_with.data(), replace_length);
        pos = match + find_length;

        // A mid-loop test/break enables skipping the final Find() call; the
        // number of matches is known, so don't search past the last one.
        if (!--num_matches)
          break;
      }

      // Handle substring after the final match.
      str->append(src, pos, str_length - pos);
      return true;
    }

    // Prepare for the copy/move loop below -- expand the string to its final
    // size by shifting the data after the first match to the end of the resized
    // string.
    size_t shift_src = first_match + find_length;
    size_t shift_dst = shift_src + expansion;

    // Big |expansion| factors (relative to |str_length|) require padding up to
    // |shift_dst|.
    if (shift_dst > str_length)
      str->resize(shift_dst);

    str->replace(shift_dst, str_length - shift_src, *str, shift_src,
                 str_length - shift_src);
    str_length = final_length;
  }

  // We can alternate replacement and move operations. This won't overwrite the
  // unsearched region of the string so long as |write_offset| <= |read_offset|;
  // that condition is always satisfied because:
  //
  //   (a) If the string is being shortened, |expansion| is zero and
  //       |write_offset| grows slower than |read_offset|.
  //
  //   (b) If the string is being lengthened, |write_offset| grows faster than
  //       |read_offset|, but |expansion| is big enough so that |write_offset|
  //       will only catch up to |read_offset| at the point of the last match.
  auto* buffer = &((*str)[0]);
  size_t write_offset = first_match;
  size_t read_offset = first_match + expansion;
  do {
    if (replace_length) {
      CharTraits::copy(buffer + write_offset, replace_with.data(),
                       replace_length);
      write_offset += replace_length;
    }
    read_offset += find_length;

    // min() clamps StringType::npos (the largest unsigned value) to str_length.
    size_t match = std::min(matcher.Find(*str, read_offset), str_length);

    size_t length = match - read_offset;
    if (length) {
      CharTraits::move(buffer + write_offset, buffer + read_offset, length);
      write_offset += length;
      read_offset += length;
    }
  } while (read_offset < str_length);

  // If we're shortening the string, truncate it now.
  str->resize(write_offset);
  return true;
}

template <class StringType>
bool ReplaceCharsT(const StringType& input,
                   BasicStringPiece<StringType> find_any_of_these,
                   BasicStringPiece<StringType> replace_with,
                   StringType* output) {
  // Commonly, this is called with output and input being the same string; in
  // that case, this assignment is inexpensive.
  *output = input;

  return DoReplaceMatchesAfterOffset(
      output, 0, CharacterMatcher<StringType>{find_any_of_these}, replace_with,
      ReplaceType::REPLACE_ALL);
}

void ReplaceFirstSubstringAfterOffset(string16* str,
                                      size_t start_offset,
                                      StringPiece16 find_this,
                                      StringPiece16 replace_with) {
  DoReplaceMatchesAfterOffset(str, start_offset,
                              SubstringMatcher<string16>{find_this},
                              replace_with, ReplaceType::REPLACE_FIRST);
}

void ReplaceFirstSubstringAfterOffset(std::string* str,
                                      size_t start_offset,
                                      StringPiece find_this,
                                      StringPiece replace_with) {
  DoReplaceMatchesAfterOffset(str, start_offset,
                              SubstringMatcher<std::string>{find_this},
                              replace_with, ReplaceType::REPLACE_FIRST);
}

void ReplaceSubstringsAfterOffset(string16* str,
                                  size_t start_offset,
                                  StringPiece16 find_this,
                                  StringPiece16 replace_with) {
  DoReplaceMatchesAfterOffset(str, start_offset,
                              SubstringMatcher<string16>{find_this},
                              replace_with, ReplaceType::REPLACE_ALL);
}

void ReplaceSubstringsAfterOffset(std::string* str,
                                  size_t start_offset,
                                  StringPiece find_this,
                                  StringPiece replace_with) {
  DoReplaceMatchesAfterOffset(str, start_offset,
                              SubstringMatcher<std::string>{find_this},
                              replace_with, ReplaceType::REPLACE_ALL);
}

template <class string_type>
inline typename string_type::value_type* WriteIntoT(string_type* str,
                                                    size_t length_with_null) {
  DCHECK_GE(length_with_null, 1u);
  str->reserve(length_with_null);
  str->resize(length_with_null - 1);
  return &((*str)[0]);
}

char* WriteInto(std::string* str, size_t length_with_null) {
  return WriteIntoT(str, length_with_null);
}

char16* WriteInto(string16* str, size_t length_with_null) {
  return WriteIntoT(str, length_with_null);
}

// Generic version for all JoinString overloads. |list_type| must be a sequence
// (std::vector or std::initializer_list) of strings/StringPieces (std::string,
// string16, StringPiece or StringPiece16). |string_type| is either std::string
// or string16.
template <typename list_type, typename string_type>
static string_type JoinStringT(const list_type& parts,
                               BasicStringPiece<string_type> sep) {
  if (base::empty(parts))
    return string_type();

  // Pre-allocate the eventual size of the string. Start with the size of all of
  // the separators (note that this *assumes* parts.size() > 0).
  size_t total_size = (parts.size() - 1) * sep.size();
  for (const auto& part : parts)
    total_size += part.size();
  string_type result;
  result.reserve(total_size);

  auto iter = parts.begin();
  DCHECK(iter != parts.end());
  result.append(iter->data(), iter->size());
  ++iter;

  for (; iter != parts.end(); ++iter) {
    result.append(sep.data(), sep.size());
    result.append(iter->data(), iter->size());
  }

  // Sanity-check that we pre-allocated correctly.
  DCHECK_EQ(total_size, result.size());

  return result;
}

std::string JoinString(const std::vector<std::string>& parts,
                       StringPiece separator) {
  return JoinStringT(parts, separator);
}

string16 JoinString(const std::vector<string16>& parts,
                    StringPiece16 separator) {
  return JoinStringT(parts, separator);
}

std::string JoinString(const std::vector<StringPiece>& parts,
                       StringPiece separator) {
  return JoinStringT(parts, separator);
}

string16 JoinString(const std::vector<StringPiece16>& parts,
                    StringPiece16 separator) {
  return JoinStringT(parts, separator);
}

std::string JoinString(std::initializer_list<StringPiece> parts,
                       StringPiece separator) {
  return JoinStringT(parts, separator);
}

string16 JoinString(std::initializer_list<StringPiece16> parts,
                    StringPiece16 separator) {
  return JoinStringT(parts, separator);
}

template<class FormatStringType, class OutStringType>
OutStringType DoReplaceStringPlaceholders(
    const FormatStringType& format_string,
    const std::vector<OutStringType>& subst,
    std::vector<size_t>* offsets) {
  size_t substitutions = subst.size();
  DCHECK_LT(substitutions, 10U);

  size_t sub_length = 0;
  for (const auto& cur : subst)
    sub_length += cur.length();

  OutStringType formatted;
  formatted.reserve(format_string.length() + sub_length);

  std::vector<ReplacementOffset> r_offsets;
  for (auto i = format_string.begin(); i != format_string.end(); ++i) {
    if ('$' == *i) {
      if (i + 1 != format_string.end()) {
        ++i;
        if ('$' == *i) {
          while (i != format_string.end() && '$' == *i) {
            formatted.push_back('$');
            ++i;
          }
          --i;
        } else {
          if (*i < '1' || *i > '9') {
            DLOG(ERROR) << "Invalid placeholder: $" << *i;
            continue;
          }
          uintptr_t index = *i - '1';
          if (offsets) {
            ReplacementOffset r_offset(index,
                                       static_cast<int>(formatted.size()));
            r_offsets.insert(
                std::upper_bound(r_offsets.begin(), r_offsets.end(), r_offset,
                                 &CompareParameter),
                r_offset);
          }
          if (index < substitutions)
            formatted.append(subst.at(index));
        }
      }
    } else {
      formatted.push_back(*i);
    }
  }
  if (offsets) {
    for (const auto& cur : r_offsets)
      offsets->push_back(cur.offset);
  }
  return formatted;
}

string16 ReplaceStringPlaceholders(const string16& format_string,
                                   const std::vector<string16>& subst,
                                   std::vector<size_t>* offsets) {
  return DoReplaceStringPlaceholders(format_string, subst, offsets);
}

std::string ReplaceStringPlaceholders(StringPiece format_string,
                                      const std::vector<std::string>& subst,
                                      std::vector<size_t>* offsets) {
  return DoReplaceStringPlaceholders(format_string, subst, offsets);
}

string16 ReplaceStringPlaceholders(const string16& format_string,
                                   const string16& a,
                                   size_t* offset) {
  std::vector<size_t> offsets;
  std::vector<string16> subst;
  subst.push_back(a);
  string16 result = ReplaceStringPlaceholders(format_string, subst, &offsets);

  DCHECK_EQ(1U, offsets.size());
  if (offset)
    *offset = offsets[0];
  return result;
}

#if defined(OS_WIN) && defined(BASE_STRING16_IS_STD_U16STRING)

TrimPositions TrimWhitespace(WStringPiece input,
                             TrimPositions positions,
                             std::wstring* output) {
  return TrimStringT(input, WStringPiece(kWhitespaceWide), positions, output);
}

WStringPiece TrimWhitespace(WStringPiece input, TrimPositions positions) {
  return TrimStringPieceT(input, WStringPiece(kWhitespaceWide), positions);
}

bool TrimString(WStringPiece input,
                WStringPiece trim_chars,
                std::wstring* output) {
  return TrimStringT(input, trim_chars, TRIM_ALL, output) != TRIM_NONE;
}

WStringPiece TrimString(WStringPiece input,
                        WStringPiece trim_chars,
                        TrimPositions positions) {
  return TrimStringPieceT(input, trim_chars, positions);
}

wchar_t* WriteInto(std::wstring* str, size_t length_with_null) {
  return WriteIntoT(str, length_with_null);
}

#endif

// The following code is compatible with the OpenBSD lcpy interface.  See:
//   http://www.gratisoft.us/todd/papers/strlcpy.html
//   ftp://ftp.openbsd.org/pub/OpenBSD/src/lib/libc/string/{wcs,str}lcpy.c

namespace {

template <typename CHAR>
size_t lcpyT(CHAR* dst, const CHAR* src, size_t dst_size) {
  for (size_t i = 0; i < dst_size; ++i) {
    if ((dst[i] = src[i]) == 0)  // We hit and copied the terminating NULL.
      return i;
  }

  // We were left off at dst_size.  We over copied 1 byte.  Null terminate.
  if (dst_size != 0)
    dst[dst_size - 1] = 0;

  // Count the rest of the |src|, and return it's length in characters.
  while (src[dst_size]) ++dst_size;
  return dst_size;
}

}  // namespace

size_t strlcpy(char* dst, const char* src, size_t dst_size) {
  return lcpyT<char>(dst, src, dst_size);
}
size_t wcslcpy(wchar_t* dst, const wchar_t* src, size_t dst_size) {
  return lcpyT<wchar_t>(dst, src, dst_size);
}

}  // namespace base
