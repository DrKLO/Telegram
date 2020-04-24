/*
 *  Copyright 2004 The WebRTC Project Authors. All rights reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "rtc_base/stringutils.h"

namespace rtc {

size_t strcpyn(char* buffer,
               size_t buflen,
               const char* source,
               size_t srclen /* = SIZE_UNKNOWN */) {
  if (buflen <= 0)
    return 0;

  if (srclen == SIZE_UNKNOWN) {
    srclen = strlen(source);
  }
  if (srclen >= buflen) {
    srclen = buflen - 1;
  }
  memcpy(buffer, source, srclen);
  buffer[srclen] = 0;
  return srclen;
}

void replace_substrs(const char* search,
                     size_t search_len,
                     const char* replace,
                     size_t replace_len,
                     std::string* s) {
  size_t pos = 0;
  while ((pos = s->find(search, pos, search_len)) != std::string::npos) {
    s->replace(pos, search_len, replace, replace_len);
    pos += replace_len;
  }
}

bool starts_with(const char* s1, const char* s2) {
  return strncmp(s1, s2, strlen(s2)) == 0;
}

bool ends_with(const char* s1, const char* s2) {
  size_t s1_length = strlen(s1);
  size_t s2_length = strlen(s2);

  if (s2_length > s1_length) {
    return false;
  }

  const char* start = s1 + (s1_length - s2_length);
  return strncmp(start, s2, s2_length) == 0;
}

static const char kWhitespace[] = " \n\r\t";

std::string string_trim(const std::string& s) {
  std::string::size_type first = s.find_first_not_of(kWhitespace);
  std::string::size_type last = s.find_last_not_of(kWhitespace);

  if (first == std::string::npos || last == std::string::npos) {
    return std::string("");
  }

  return s.substr(first, last - first + 1);
}

std::string ToHex(const int i) {
  char buffer[50];
  snprintf(buffer, sizeof(buffer), "%x", i);

  return std::string(buffer);
}

std::string LeftPad(char padding, unsigned length, std::string s) {
  if (s.length() >= length)
    return s;
  return std::string(length - s.length(), padding) + s;
}

}  // namespace rtc
