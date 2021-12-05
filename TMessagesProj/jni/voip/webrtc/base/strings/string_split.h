// Copyright (c) 2012 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

#ifndef BASE_STRINGS_STRING_SPLIT_H_
#define BASE_STRINGS_STRING_SPLIT_H_

#include <string>
#include <utility>
#include <vector>

#include "base/base_export.h"
#include "base/strings/string16.h"
#include "base/strings/string_piece.h"
#include "build/build_config.h"

namespace base {

enum WhitespaceHandling {
  KEEP_WHITESPACE,
  TRIM_WHITESPACE,
};

enum SplitResult {
  // Strictly return all results.
  //
  // If the input is ",," and the separator is ',' this will return a
  // vector of three empty strings.
  SPLIT_WANT_ALL,

  // Only nonempty results will be added to the results. Multiple separators
  // will be coalesced. Separators at the beginning and end of the input will
  // be ignored. With TRIM_WHITESPACE, whitespace-only results will be dropped.
  //
  // If the input is ",," and the separator is ',', this will return an empty
  // vector.
  SPLIT_WANT_NONEMPTY,
};

// Split the given string on ANY of the given separators, returning copies of
// the result.
//
// Note this is inverse of JoinString() defined in string_util.h.
//
// To split on either commas or semicolons, keeping all whitespace:
//
//   std::vector<std::string> tokens = base::SplitString(
//       input, ", WARN_UNUSED_RESULT;", base::KEEP_WHITESPACE,
//       base::SPLIT_WANT_ALL) WARN_UNUSED_RESULT;
BASE_EXPORT std::vector<std::string> SplitString(StringPiece input,
                                                 StringPiece separators,
                                                 WhitespaceHandling whitespace,
                                                 SplitResult result_type)
    WARN_UNUSED_RESULT;
BASE_EXPORT std::vector<string16> SplitString(StringPiece16 input,
                                              StringPiece16 separators,
                                              WhitespaceHandling whitespace,
                                              SplitResult result_type)
    WARN_UNUSED_RESULT;

// Like SplitString above except it returns a vector of StringPieces which
// reference the original buffer without copying. Although you have to be
// careful to keep the original string unmodified, this provides an efficient
// way to iterate through tokens in a string.
//
// Note this is inverse of JoinString() defined in string_util.h.
//
// To iterate through all whitespace-separated tokens in an input string:
//
//   for (const auto& cur :
//        base::SplitStringPiece(input, base::kWhitespaceASCII,
//                               base::KEEP_WHITESPACE,
//                               base::SPLIT_WANT_NONEMPTY)) {
//     ...
BASE_EXPORT std::vector<StringPiece> SplitStringPiece(
    StringPiece input,
    StringPiece separators,
    WhitespaceHandling whitespace,
    SplitResult result_type) WARN_UNUSED_RESULT;
BASE_EXPORT std::vector<StringPiece16> SplitStringPiece(
    StringPiece16 input,
    StringPiece16 separators,
    WhitespaceHandling whitespace,
    SplitResult result_type) WARN_UNUSED_RESULT;

using StringPairs = std::vector<std::pair<std::string, std::string>>;

// Splits |line| into key value pairs according to the given delimiters and
// removes whitespace leading each key and trailing each value. Returns true
// only if each pair has a non-empty key and value. |key_value_pairs| will
// include ("","") pairs for entries without |key_value_delimiter|.
BASE_EXPORT bool SplitStringIntoKeyValuePairs(StringPiece input,
                                              char key_value_delimiter,
                                              char key_value_pair_delimiter,
                                              StringPairs* key_value_pairs);

// Similar to SplitStringIntoKeyValuePairs, but use a substring
// |key_value_pair_delimiter| instead of a single char.
BASE_EXPORT bool SplitStringIntoKeyValuePairsUsingSubstr(
    StringPiece input,
    char key_value_delimiter,
    StringPiece key_value_pair_delimiter,
    StringPairs* key_value_pairs);

// Similar to SplitString, but use a substring delimiter instead of a list of
// characters that are all possible delimiters.
BASE_EXPORT std::vector<string16> SplitStringUsingSubstr(
    StringPiece16 input,
    StringPiece16 delimiter,
    WhitespaceHandling whitespace,
    SplitResult result_type) WARN_UNUSED_RESULT;
BASE_EXPORT std::vector<std::string> SplitStringUsingSubstr(
    StringPiece input,
    StringPiece delimiter,
    WhitespaceHandling whitespace,
    SplitResult result_type) WARN_UNUSED_RESULT;

// Like SplitStringUsingSubstr above except it returns a vector of StringPieces
// which reference the original buffer without copying. Although you have to be
// careful to keep the original string unmodified, this provides an efficient
// way to iterate through tokens in a string.
//
// To iterate through all newline-separated tokens in an input string:
//
//   for (const auto& cur :
//        base::SplitStringUsingSubstr(input, "\r\n",
//                                     base::KEEP_WHITESPACE,
//                                     base::SPLIT_WANT_NONEMPTY)) {
//     ...
BASE_EXPORT std::vector<StringPiece16> SplitStringPieceUsingSubstr(
    StringPiece16 input,
    StringPiece16 delimiter,
    WhitespaceHandling whitespace,
    SplitResult result_type) WARN_UNUSED_RESULT;
BASE_EXPORT std::vector<StringPiece> SplitStringPieceUsingSubstr(
    StringPiece input,
    StringPiece delimiter,
    WhitespaceHandling whitespace,
    SplitResult result_type) WARN_UNUSED_RESULT;

#if defined(OS_WIN) && defined(BASE_STRING16_IS_STD_U16STRING)
BASE_EXPORT std::vector<std::wstring> SplitString(WStringPiece input,
                                                  WStringPiece separators,
                                                  WhitespaceHandling whitespace,
                                                  SplitResult result_type)
    WARN_UNUSED_RESULT;

BASE_EXPORT std::vector<WStringPiece> SplitStringPiece(
    WStringPiece input,
    WStringPiece separators,
    WhitespaceHandling whitespace,
    SplitResult result_type) WARN_UNUSED_RESULT;

BASE_EXPORT std::vector<std::wstring> SplitStringUsingSubstr(
    WStringPiece input,
    WStringPiece delimiter,
    WhitespaceHandling whitespace,
    SplitResult result_type) WARN_UNUSED_RESULT;

BASE_EXPORT std::vector<WStringPiece> SplitStringPieceUsingSubstr(
    WStringPiece input,
    WStringPiece delimiter,
    WhitespaceHandling whitespace,
    SplitResult result_type) WARN_UNUSED_RESULT;
#endif

}  // namespace base

#endif  // BASE_STRINGS_STRING_SPLIT_H_
