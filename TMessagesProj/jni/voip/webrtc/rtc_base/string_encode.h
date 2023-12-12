/*
 *  Copyright 2004 The WebRTC Project Authors. All rights reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#ifndef RTC_BASE_STRING_ENCODE_H_
#define RTC_BASE_STRING_ENCODE_H_

#include <stddef.h>

#include <string>
#include <type_traits>
#include <vector>

#include "absl/strings/string_view.h"
#include "absl/types/optional.h"
#include "api/array_view.h"
#include "rtc_base/checks.h"
#include "rtc_base/string_to_number.h"

namespace rtc {

//////////////////////////////////////////////////////////////////////
// String Encoding Utilities
//////////////////////////////////////////////////////////////////////

std::string hex_encode(absl::string_view str);
std::string hex_encode_with_delimiter(absl::string_view source, char delimiter);

// hex_decode converts ascii hex to binary.
size_t hex_decode(ArrayView<char> buffer, absl::string_view source);

// hex_decode, assuming that there is a delimiter between every byte
// pair.
// `delimiter` == 0 means no delimiter
// If the buffer is too short or the data is invalid, we return 0.
size_t hex_decode_with_delimiter(ArrayView<char> buffer,
                                 absl::string_view source,
                                 char delimiter);

// Splits the source string into multiple fields separated by delimiter,
// with duplicates of delimiter creating empty fields. Empty input produces a
// single, empty, field.
std::vector<absl::string_view> split(absl::string_view source, char delimiter);

// Splits the source string into multiple fields separated by delimiter,
// with duplicates of delimiter ignored.  Trailing delimiter ignored.
size_t tokenize(absl::string_view source,
                char delimiter,
                std::vector<std::string>* fields);

// Extract the first token from source as separated by delimiter, with
// duplicates of delimiter ignored. Return false if the delimiter could not be
// found, otherwise return true.
bool tokenize_first(absl::string_view source,
                    char delimiter,
                    std::string* token,
                    std::string* rest);

// Convert arbitrary values to/from a string.
// TODO(jonasolsson): Remove these when absl::StrCat becomes available.
std::string ToString(bool b);

std::string ToString(absl::string_view s);
// The const char* overload is needed for correct overload resolution because of
// the const void* version of ToString() below.
std::string ToString(const char* s);

std::string ToString(short s);
std::string ToString(unsigned short s);
std::string ToString(int s);
std::string ToString(unsigned int s);
std::string ToString(long int s);
std::string ToString(unsigned long int s);
std::string ToString(long long int s);
std::string ToString(unsigned long long int s);

std::string ToString(double t);
std::string ToString(long double t);

std::string ToString(const void* p);

template <typename T,
          typename std::enable_if<std::is_arithmetic<T>::value &&
                                      !std::is_same<T, bool>::value,
                                  int>::type = 0>
static bool FromString(absl::string_view s, T* t) {
  RTC_DCHECK(t);
  absl::optional<T> result = StringToNumber<T>(s);

  if (result)
    *t = *result;

  return result.has_value();
}

bool FromString(absl::string_view s, bool* b);

template <typename T>
static inline T FromString(absl::string_view str) {
  T val;
  FromString(str, &val);
  return val;
}

//////////////////////////////////////////////////////////////////////

}  // namespace rtc

#endif  // RTC_BASE_STRING_ENCODE_H__
