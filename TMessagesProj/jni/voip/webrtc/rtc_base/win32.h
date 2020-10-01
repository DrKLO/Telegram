/*
 *  Copyright 2004 The WebRTC Project Authors. All rights reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#ifndef RTC_BASE_WIN32_H_
#define RTC_BASE_WIN32_H_

#ifndef WEBRTC_WIN
#error "Only #include this header in Windows builds"
#endif

// Make sure we don't get min/max macros
#ifndef NOMINMAX
#define NOMINMAX
#endif

// clang-format off
// clang formating would change include order.
#include <winsock2.h> // must come first
#include <windows.h>
// clang-format on

typedef int socklen_t;

#ifndef SECURITY_MANDATORY_LABEL_AUTHORITY
// Add defines that we use if we are compiling against older sdks
#define SECURITY_MANDATORY_MEDIUM_RID (0x00002000L)
#define TokenIntegrityLevel static_cast<TOKEN_INFORMATION_CLASS>(0x19)
typedef struct _TOKEN_MANDATORY_LABEL {
  SID_AND_ATTRIBUTES Label;
} TOKEN_MANDATORY_LABEL, *PTOKEN_MANDATORY_LABEL;
#endif  // SECURITY_MANDATORY_LABEL_AUTHORITY

#undef SetPort

#include <string>

namespace rtc {

const char* win32_inet_ntop(int af, const void* src, char* dst, socklen_t size);
int win32_inet_pton(int af, const char* src, void* dst);

enum WindowsMajorVersions {
  kWindows2000 = 5,
  kWindowsVista = 6,
  kWindows10 = 10,
};

#if !defined(WINUWP)
bool GetOsVersion(int* major, int* minor, int* build);

inline bool IsWindowsVistaOrLater() {
  int major;
  return (GetOsVersion(&major, nullptr, nullptr) && major >= kWindowsVista);
}

inline bool IsWindowsXpOrLater() {
  int major, minor;
  return (GetOsVersion(&major, &minor, nullptr) &&
          (major >= kWindowsVista || (major == kWindows2000 && minor >= 1)));
}

inline bool IsWindows8OrLater() {
  int major, minor;
  return (GetOsVersion(&major, &minor, nullptr) &&
          (major > kWindowsVista || (major == kWindowsVista && minor >= 2)));
}

inline bool IsWindows10OrLater() {
  int major;
  return (GetOsVersion(&major, nullptr, nullptr) && (major >= kWindows10));
}

#else

// When targetting WinUWP the OS must be Windows 10 (or greater) as lesser
// Windows OS targets are not supported.
inline bool IsWindowsVistaOrLater() {
  return true;
}

inline bool IsWindowsXpOrLater() {
  return true;
}

inline bool IsWindows8OrLater() {
  return true;
}

inline bool IsWindows10OrLater() {
  return true;
}

#endif  // !defined(WINUWP)

}  // namespace rtc

#endif  // RTC_BASE_WIN32_H_
