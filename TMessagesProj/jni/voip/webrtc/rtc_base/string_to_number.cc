/*
 *  Copyright 2017 The WebRTC Project Authors. All rights reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "rtc_base/string_to_number.h"

#include <ctype.h>

#include <cerrno>
#include <cstdlib>

#include "rtc_base/checks.h"

namespace rtc {
namespace string_to_number_internal {

absl::optional<signed_type> ParseSigned(absl::string_view str, int base) {
  if (str.empty())
    return absl::nullopt;

  if (isdigit(static_cast<unsigned char>(str[0])) || str[0] == '-') {
    std::string str_str(str);
    char* end = nullptr;
    errno = 0;
    const signed_type value = std::strtoll(str_str.c_str(), &end, base);
    // Check for errors and also make sure that there were no embedded nuls in
    // the input string.
    if (end == str_str.c_str() + str_str.size() && errno == 0) {
      return value;
    }
  }
  return absl::nullopt;
}

absl::optional<unsigned_type> ParseUnsigned(absl::string_view str, int base) {
  if (str.empty())
    return absl::nullopt;

  if (isdigit(static_cast<unsigned char>(str[0])) || str[0] == '-') {
    std::string str_str(str);
    // Explicitly discard negative values. std::strtoull parsing causes unsigned
    // wraparound. We cannot just reject values that start with -, though, since
    // -0 is perfectly fine, as is -0000000000000000000000000000000.
    const bool is_negative = str[0] == '-';
    char* end = nullptr;
    errno = 0;
    const unsigned_type value = std::strtoull(str_str.c_str(), &end, base);
    // Check for errors and also make sure that there were no embedded nuls in
    // the input string.
    if (end == str_str.c_str() + str_str.size() && errno == 0 &&
        (value == 0 || !is_negative)) {
      return value;
    }
  }
  return absl::nullopt;
}

template <typename T>
T StrToT(const char* str, char** str_end);

template <>
inline float StrToT(const char* str, char** str_end) {
  return std::strtof(str, str_end);
}

template <>
inline double StrToT(const char* str, char** str_end) {
  return std::strtod(str, str_end);
}

template <>
inline long double StrToT(const char* str, char** str_end) {
  return std::strtold(str, str_end);
}

template <typename T>
absl::optional<T> ParseFloatingPoint(absl::string_view str) {
  if (str.empty())
    return absl::nullopt;

  if (str[0] == '\0')
    return absl::nullopt;
  std::string str_str(str);
  char* end = nullptr;
  errno = 0;
  const T value = StrToT<T>(str_str.c_str(), &end);
  if (end == str_str.c_str() + str_str.size() && errno == 0) {
    return value;
  }
  return absl::nullopt;
}

template absl::optional<float> ParseFloatingPoint(absl::string_view str);
template absl::optional<double> ParseFloatingPoint(absl::string_view str);
template absl::optional<long double> ParseFloatingPoint(absl::string_view str);

}  // namespace string_to_number_internal
}  // namespace rtc
