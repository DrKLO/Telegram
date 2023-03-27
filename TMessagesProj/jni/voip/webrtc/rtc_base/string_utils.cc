/*
 *  Copyright 2004 The WebRTC Project Authors. All rights reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "rtc_base/string_utils.h"

#include "absl/strings/string_view.h"

namespace rtc {

size_t strcpyn(char* buffer, size_t buflen, absl::string_view source) {
  if (buflen <= 0)
    return 0;

  size_t srclen = source.length();
  if (srclen >= buflen) {
    srclen = buflen - 1;
  }
  memcpy(buffer, source.data(), srclen);
  buffer[srclen] = 0;
  return srclen;
}

std::string ToHex(const int i) {
  char buffer[50];
  snprintf(buffer, sizeof(buffer), "%x", i);

  return std::string(buffer);
}

}  // namespace rtc
