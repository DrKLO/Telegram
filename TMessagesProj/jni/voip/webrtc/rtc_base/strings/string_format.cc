/*
 *  Copyright 2020 The WebRTC Project Authors. All rights reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "rtc_base/strings/string_format.h"

#include <cstdarg>

#include "rtc_base/checks.h"

namespace rtc {

namespace {

// This is an arbitrary limitation that can be changed if necessary, or removed
// if someone has the time and inclination to replicate the fancy logic from
// Chromium's base::StringPrinf().
constexpr int kMaxSize = 512;

}  // namespace

std::string StringFormat(const char* fmt, ...) {
  char buffer[kMaxSize];
  va_list args;
  va_start(args, fmt);
  int result = vsnprintf(buffer, kMaxSize, fmt, args);
  va_end(args);
  RTC_DCHECK_GE(result, 0) << "ERROR: vsnprintf() failed with error " << result;
  RTC_DCHECK_LT(result, kMaxSize)
      << "WARNING: string was truncated from " << result << " to "
      << (kMaxSize - 1) << " characters";
  return std::string(buffer);
}

}  // namespace rtc
