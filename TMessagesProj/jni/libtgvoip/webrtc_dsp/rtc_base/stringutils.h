/*
 *  Copyright 2004 The WebRTC Project Authors. All rights reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#ifndef RTC_BASE_STRINGUTILS_H_
#define RTC_BASE_STRINGUTILS_H_

#include <ctype.h>
#include <stdarg.h>
#include <stdio.h>
#include <string.h>

#if defined(WEBRTC_WIN)
#include <malloc.h>
#include <wchar.h>
#include <windows.h>

#define alloca _alloca
#endif  // WEBRTC_WIN

#if defined(WEBRTC_POSIX)
#ifdef BSD
#include <stdlib.h>
#else  // BSD
#include <alloca.h>
#endif  // !BSD
#include <strings.h>
#endif  // WEBRTC_POSIX

#include <string>

///////////////////////////////////////////////////////////////////////////////
// Generic string/memory utilities
///////////////////////////////////////////////////////////////////////////////

#define STACK_ARRAY(TYPE, LEN) \
  static_cast<TYPE*>(::alloca((LEN) * sizeof(TYPE)))

///////////////////////////////////////////////////////////////////////////////
// Traits simplifies porting string functions to be CTYPE-agnostic
///////////////////////////////////////////////////////////////////////////////

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
  int len16 = ::MultiByteToWideChar(CP_UTF8, 0, utf8, static_cast<int>(len),
                                    nullptr, 0);
  wchar_t* ws = STACK_ARRAY(wchar_t, len16);
  ::MultiByteToWideChar(CP_UTF8, 0, utf8, static_cast<int>(len), ws, len16);
  return std::wstring(ws, len16);
}

inline std::wstring ToUtf16(const std::string& str) {
  return ToUtf16(str.data(), str.length());
}

inline std::string ToUtf8(const wchar_t* wide, size_t len) {
  int len8 = ::WideCharToMultiByte(CP_UTF8, 0, wide, static_cast<int>(len),
                                   nullptr, 0, nullptr, nullptr);
  char* ns = STACK_ARRAY(char, len8);
  ::WideCharToMultiByte(CP_UTF8, 0, wide, static_cast<int>(len), ns, len8,
                        nullptr, nullptr);
  return std::string(ns, len8);
}

inline std::string ToUtf8(const wchar_t* wide) {
  return ToUtf8(wide, wcslen(wide));
}

inline std::string ToUtf8(const std::wstring& wstr) {
  return ToUtf8(wstr.data(), wstr.length());
}

#endif  // WEBRTC_WIN

// Replaces all occurrences of "search" with "replace".
void replace_substrs(const char* search,
                     size_t search_len,
                     const char* replace,
                     size_t replace_len,
                     std::string* s);

// True iff s1 starts with s2.
bool starts_with(const char* s1, const char* s2);

// True iff s1 ends with s2.
bool ends_with(const char* s1, const char* s2);

// Remove leading and trailing whitespaces.
std::string string_trim(const std::string& s);

// TODO(jonasolsson): replace with absl::Hex when that becomes available.
std::string ToHex(const int i);

std::string LeftPad(char padding, unsigned length, std::string s);

}  // namespace rtc

#endif  // RTC_BASE_STRINGUTILS_H_
