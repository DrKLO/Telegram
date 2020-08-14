// Copyright (c) 2012 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

#include "base/strings/string_split.h"

#include <stddef.h>

#include "base/logging.h"
#include "base/strings/string_util.h"
#include "base/third_party/icu/icu_utf.h"

namespace base {

namespace {

// Returns either the ASCII or UTF-16 whitespace.
template<typename Str> BasicStringPiece<Str> WhitespaceForType();
#if defined(OS_WIN) && defined(BASE_STRING16_IS_STD_U16STRING)
template <>
WStringPiece WhitespaceForType<std::wstring>() {
  return kWhitespaceWide;
}
#endif

template<> StringPiece16 WhitespaceForType<string16>() {
  return kWhitespaceUTF16;
}
template<> StringPiece WhitespaceForType<std::string>() {
  return kWhitespaceASCII;
}

// General string splitter template. Can take 8- or 16-bit input, can produce
// the corresponding string or StringPiece output.
template <typename OutputStringType, typename Str>
static std::vector<OutputStringType> SplitStringT(
    BasicStringPiece<Str> str,
    BasicStringPiece<Str> delimiter,
    WhitespaceHandling whitespace,
    SplitResult result_type) {
  std::vector<OutputStringType> result;
  if (str.empty())
    return result;

  size_t start = 0;
  while (start != Str::npos) {
    size_t end = str.find_first_of(delimiter, start);

    BasicStringPiece<Str> piece;
    if (end == Str::npos) {
      piece = str.substr(start);
      start = Str::npos;
    } else {
      piece = str.substr(start, end - start);
      start = end + 1;
    }

    if (whitespace == TRIM_WHITESPACE)
      piece = TrimString(piece, WhitespaceForType<Str>(), TRIM_ALL);

    if (result_type == SPLIT_WANT_ALL || !piece.empty())
      result.emplace_back(piece);
  }
  return result;
}

bool AppendStringKeyValue(StringPiece input,
                          char delimiter,
                          StringPairs* result) {
  // Always append a new item regardless of success (it might be empty). The
  // below code will copy the strings directly into the result pair.
  result->resize(result->size() + 1);
  auto& result_pair = result->back();

  // Find the delimiter.
  size_t end_key_pos = input.find_first_of(delimiter);
  if (end_key_pos == std::string::npos) {
    DVLOG(1) << "cannot find delimiter in: " << input;
    return false;    // No delimiter.
  }
  result_pair.first = std::string(input.substr(0, end_key_pos));

  // Find the value string.
  StringPiece remains = input.substr(end_key_pos, input.size() - end_key_pos);
  size_t begin_value_pos = remains.find_first_not_of(delimiter);
  if (begin_value_pos == StringPiece::npos) {
    DVLOG(1) << "cannot parse value from input: " << input;
    return false;   // No value.
  }

  result_pair.second = std::string(
      remains.substr(begin_value_pos, remains.size() - begin_value_pos));

  return true;
}

template <typename OutputStringType, typename Str>
std::vector<OutputStringType> SplitStringUsingSubstrT(
    BasicStringPiece<Str> input,
    BasicStringPiece<Str> delimiter,
    WhitespaceHandling whitespace,
    SplitResult result_type) {
  using Piece = BasicStringPiece<Str>;
  using size_type = typename Piece::size_type;

  std::vector<OutputStringType> result;
  if (delimiter.size() == 0) {
    result.emplace_back(input);
    return result;
  }

  for (size_type begin_index = 0, end_index = 0; end_index != Piece::npos;
       begin_index = end_index + delimiter.size()) {
    end_index = input.find(delimiter, begin_index);
    Piece term = end_index == Piece::npos
                     ? input.substr(begin_index)
                     : input.substr(begin_index, end_index - begin_index);

    if (whitespace == TRIM_WHITESPACE)
      term = TrimString(term, WhitespaceForType<Str>(), TRIM_ALL);

    if (result_type == SPLIT_WANT_ALL || !term.empty())
      result.emplace_back(term);
  }

  return result;
}

}  // namespace

std::vector<std::string> SplitString(StringPiece input,
                                     StringPiece separators,
                                     WhitespaceHandling whitespace,
                                     SplitResult result_type) {
  return SplitStringT<std::string>(input, separators, whitespace, result_type);
}

std::vector<string16> SplitString(StringPiece16 input,
                                  StringPiece16 separators,
                                  WhitespaceHandling whitespace,
                                  SplitResult result_type) {
  return SplitStringT<string16>(input, separators, whitespace, result_type);
}

std::vector<StringPiece> SplitStringPiece(StringPiece input,
                                          StringPiece separators,
                                          WhitespaceHandling whitespace,
                                          SplitResult result_type) {
  return SplitStringT<StringPiece>(input, separators, whitespace, result_type);
}

std::vector<StringPiece16> SplitStringPiece(StringPiece16 input,
                                            StringPiece16 separators,
                                            WhitespaceHandling whitespace,
                                            SplitResult result_type) {
  return SplitStringT<StringPiece16>(input, separators, whitespace,
                                     result_type);
}

bool SplitStringIntoKeyValuePairs(StringPiece input,
                                  char key_value_delimiter,
                                  char key_value_pair_delimiter,
                                  StringPairs* key_value_pairs) {
  return SplitStringIntoKeyValuePairsUsingSubstr(
      input, key_value_delimiter, StringPiece(&key_value_pair_delimiter, 1),
      key_value_pairs);
}

bool SplitStringIntoKeyValuePairsUsingSubstr(
    StringPiece input,
    char key_value_delimiter,
    StringPiece key_value_pair_delimiter,
    StringPairs* key_value_pairs) {
  key_value_pairs->clear();

  std::vector<StringPiece> pairs = SplitStringPieceUsingSubstr(
      input, key_value_pair_delimiter, TRIM_WHITESPACE, SPLIT_WANT_NONEMPTY);
  key_value_pairs->reserve(pairs.size());

  bool success = true;
  for (const StringPiece& pair : pairs) {
    if (!AppendStringKeyValue(pair, key_value_delimiter, key_value_pairs)) {
      // Don't return here, to allow for pairs without associated
      // value or key; just record that the split failed.
      success = false;
    }
  }
  return success;
}

std::vector<string16> SplitStringUsingSubstr(StringPiece16 input,
                                             StringPiece16 delimiter,
                                             WhitespaceHandling whitespace,
                                             SplitResult result_type) {
  return SplitStringUsingSubstrT<string16>(input, delimiter, whitespace,
                                           result_type);
}

std::vector<std::string> SplitStringUsingSubstr(StringPiece input,
                                                StringPiece delimiter,
                                                WhitespaceHandling whitespace,
                                                SplitResult result_type) {
  return SplitStringUsingSubstrT<std::string>(input, delimiter, whitespace,
                                              result_type);
}

std::vector<StringPiece16> SplitStringPieceUsingSubstr(
    StringPiece16 input,
    StringPiece16 delimiter,
    WhitespaceHandling whitespace,
    SplitResult result_type) {
  std::vector<StringPiece16> result;
  return SplitStringUsingSubstrT<StringPiece16>(input, delimiter, whitespace,
                                                result_type);
}

std::vector<StringPiece> SplitStringPieceUsingSubstr(
    StringPiece input,
    StringPiece delimiter,
    WhitespaceHandling whitespace,
    SplitResult result_type) {
  return SplitStringUsingSubstrT<StringPiece>(input, delimiter, whitespace,
                                              result_type);
}

#if defined(OS_WIN) && defined(BASE_STRING16_IS_STD_U16STRING)
std::vector<std::wstring> SplitString(WStringPiece input,
                                      WStringPiece separators,
                                      WhitespaceHandling whitespace,
                                      SplitResult result_type) {
  return SplitStringT<std::wstring>(input, separators, whitespace, result_type);
}

std::vector<WStringPiece> SplitStringPiece(WStringPiece input,
                                           WStringPiece separators,
                                           WhitespaceHandling whitespace,
                                           SplitResult result_type) {
  return SplitStringT<WStringPiece>(input, separators, whitespace, result_type);
}

std::vector<std::wstring> SplitStringUsingSubstr(WStringPiece input,
                                                 WStringPiece delimiter,
                                                 WhitespaceHandling whitespace,
                                                 SplitResult result_type) {
  return SplitStringUsingSubstrT<std::wstring>(input, delimiter, whitespace,
                                               result_type);
}

std::vector<WStringPiece> SplitStringPieceUsingSubstr(
    WStringPiece input,
    WStringPiece delimiter,
    WhitespaceHandling whitespace,
    SplitResult result_type) {
  return SplitStringUsingSubstrT<WStringPiece>(input, delimiter, whitespace,
                                               result_type);
}
#endif

}  // namespace base
