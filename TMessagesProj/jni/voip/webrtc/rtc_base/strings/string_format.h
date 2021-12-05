/*
 *  Copyright 2020 The WebRTC Project Authors. All rights reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#ifndef RTC_BASE_STRINGS_STRING_FORMAT_H_
#define RTC_BASE_STRINGS_STRING_FORMAT_H_

#include <string>

namespace rtc {

#if defined(__GNUC__)
#define RTC_PRINTF_FORMAT(format_param, dots_param) \
  __attribute__((format(printf, format_param, dots_param)))
#else
#define RTC_PRINTF_FORMAT(format_param, dots_param)
#endif

// Return a C++ string given printf-like input.
// Based on base::StringPrintf() in Chrome but without its fancy dynamic memory
// allocation for any size of the input buffer.
std::string StringFormat(const char* fmt, ...) RTC_PRINTF_FORMAT(1, 2);
}  // namespace rtc

#endif  // RTC_BASE_STRINGS_STRING_FORMAT_H_
