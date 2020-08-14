// Copyright (c) 2006-2008 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

#include "base/json/string_escape.h"

#include <stddef.h>
#include <stdint.h>

#include <limits>
#include <string>

#include "base/strings/string_util.h"
#include "base/strings/stringprintf.h"
#include "base/strings/utf_string_conversion_utils.h"
#include "base/strings/utf_string_conversions.h"
#include "base/third_party/icu/icu_utf.h"

namespace base {

namespace {

// Format string for printing a \uXXXX escape sequence.
const char kU16EscapeFormat[] = "\\u%04X";

// The code point to output for an invalid input code unit.
const uint32_t kReplacementCodePoint = 0xFFFD;

// Used below in EscapeSpecialCodePoint().
static_assert('<' == 0x3C, "less than sign must be 0x3c");

// Try to escape the |code_point| if it is a known special character. If
// successful, returns true and appends the escape sequence to |dest|. This
// isn't required by the spec, but it's more readable by humans.
bool EscapeSpecialCodePoint(uint32_t code_point, std::string* dest) {
  // WARNING: if you add a new case here, you need to update the reader as well.
  // Note: \v is in the reader, but not here since the JSON spec doesn't
  // allow it.
  switch (code_point) {
    case '\b':
      dest->append("\\b");
      break;
    case '\f':
      dest->append("\\f");
      break;
    case '\n':
      dest->append("\\n");
      break;
    case '\r':
      dest->append("\\r");
      break;
    case '\t':
      dest->append("\\t");
      break;
    case '\\':
      dest->append("\\\\");
      break;
    case '"':
      dest->append("\\\"");
      break;
    // Escape < to prevent script execution; escaping > is not necessary and
    // not doing so save a few bytes.
    case '<':
      dest->append("\\u003C");
      break;
    // Escape the "Line Separator" and "Paragraph Separator" characters, since
    // they should be treated like a new line \r or \n.
    case 0x2028:
      dest->append("\\u2028");
      break;
    case 0x2029:
      dest->append("\\u2029");
      break;
    default:
      return false;
  }
  return true;
}

template <typename S>
bool EscapeJSONStringImpl(const S& str, bool put_in_quotes, std::string* dest) {
  bool did_replacement = false;

  if (put_in_quotes)
    dest->push_back('"');

  // Casting is necessary because ICU uses int32_t. Try and do so safely.
  CHECK_LE(str.length(),
           static_cast<size_t>(std::numeric_limits<int32_t>::max()));
  const int32_t length = static_cast<int32_t>(str.length());

  for (int32_t i = 0; i < length; ++i) {
    uint32_t code_point;
    if (!ReadUnicodeCharacter(str.data(), length, &i, &code_point) ||
        code_point == static_cast<decltype(code_point)>(CBU_SENTINEL) ||
        !IsValidCodepoint(code_point)) {
      code_point = kReplacementCodePoint;
      did_replacement = true;
    }

    if (EscapeSpecialCodePoint(code_point, dest))
      continue;

    // Escape non-printing characters.
    if (code_point < 32)
      base::StringAppendF(dest, kU16EscapeFormat, code_point);
    else
      WriteUnicodeCharacter(code_point, dest);
  }

  if (put_in_quotes)
    dest->push_back('"');

  return !did_replacement;
}

}  // namespace

bool EscapeJSONString(StringPiece str, bool put_in_quotes, std::string* dest) {
  return EscapeJSONStringImpl(str, put_in_quotes, dest);
}

bool EscapeJSONString(StringPiece16 str,
                      bool put_in_quotes,
                      std::string* dest) {
  return EscapeJSONStringImpl(str, put_in_quotes, dest);
}

std::string GetQuotedJSONString(StringPiece str) {
  std::string dest;
  EscapeJSONStringImpl(str, true, &dest);
  return dest;
}

std::string GetQuotedJSONString(StringPiece16 str) {
  std::string dest;
  EscapeJSONStringImpl(str, true, &dest);
  return dest;
}

std::string EscapeBytesAsInvalidJSONString(StringPiece str,
                                           bool put_in_quotes) {
  std::string dest;

  if (put_in_quotes)
    dest.push_back('"');

  for (unsigned char c : str) {
    if (EscapeSpecialCodePoint(c, &dest))
      continue;

    if (c < 32 || c > 126)
      base::StringAppendF(&dest, kU16EscapeFormat, c);
    else
      dest.push_back(c);
  }

  if (put_in_quotes)
    dest.push_back('"');

  return dest;
}

}  // namespace base
