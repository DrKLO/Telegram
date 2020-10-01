/*
 *  Copyright 2004 The WebRTC Project Authors. All rights reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#ifndef RTC_BASE_STRING_UTILS_H_
#define RTC_BASE_STRING_UTILS_H_

#include <ctype.h>
#include <stdarg.h>
#include <stdio.h>
#include <string.h>

#if defined(WEBRTC_WIN)
#include <malloc.h>
#include <wchar.h>
#include <windows.h>

#endif  // WEBRTC_WIN

#if defined(WEBRTC_POSIX)
#include <stdlib.h>
#include <strings.h>
#endif  // WEBRTC_POSIX

#include <string>

namespace rtc {

const size_t SIZE_UNKNOWN = static_cast<size_t>(-1);

// Safe version of strncpy that always nul-terminate.
size_t strcpyn(char* buffer,
               size_t buflen,
               const char* source,
               size_t srclen = SIZE_UNKNOWN);

///////////////////////////////////////////////////////////////////////////////
// UTF helpers (Windows only)
///////////////////////////////////////////////////////////////////////////////

#if defined(WEBRTC_WIN)

inline std::wstring ToUtf16(const char* utf8, size_t len) {
  if (len == 0)
    return std::wstring();
  int len16 = ::MultiByteToWideChar(CP_UTF8, 0, utf8, static_cast<int>(len),
                                    nullptr, 0);
  std::wstring ws(len16, 0);
  ::MultiByteToWideChar(CP_UTF8, 0, utf8, static_cast<int>(len), &*ws.begin(),
                        len16);
  return ws;
}

inline std::wstring ToUtf16(const std::string& str) {
  return ToUtf16(str.data(), str.length());
}

inline std::string ToUtf8(const wchar_t* wide, size_t len) {
  if (len == 0)
    return std::string();
  int len8 = ::WideCharToMultiByte(CP_UTF8, 0, wide, static_cast<int>(len),
                                   nullptr, 0, nullptr, nullptr);
  std::string ns(len8, 0);
  ::WideCharToMultiByte(CP_UTF8, 0, wide, static_cast<int>(len), &*ns.begin(),
                        len8, nullptr, nullptr);
  return ns;
}

inline std::string ToUtf8(const wchar_t* wide) {
  return ToUtf8(wide, wcslen(wide));
}

inline std::string ToUtf8(const std::wstring& wstr) {
  return ToUtf8(wstr.data(), wstr.length());
}

#endif  // WEBRTC_WIN

// Remove leading and trailing whitespaces.
std::string string_trim(const std::string& s);

// TODO(jonasolsson): replace with absl::Hex when that becomes available.
std::string ToHex(const int i);

std::string LeftPad(char padding, unsigned length, std::string s);

}  // namespace rtc

#endif  // RTC_BASE_STRING_UTILS_H_
