/*
 *  Copyright (c) 2013 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "modules/desktop_capture/win/desktop.h"

#include <vector>

#include "rtc_base/logging.h"
#include "rtc_base/string_utils.h"

namespace webrtc {

Desktop::Desktop(HDESK desktop, bool own) : desktop_(desktop), own_(own) {}

Desktop::~Desktop() {
  if (own_ && desktop_ != NULL) {
    if (!::CloseDesktop(desktop_)) {
      RTC_LOG(LS_ERROR) << "Failed to close the owned desktop handle: "
                        << GetLastError();
    }
  }
}

bool Desktop::GetName(std::wstring* desktop_name_out) const {
  if (desktop_ == NULL)
    return false;

  DWORD length = 0;
  int rv = GetUserObjectInformationW(desktop_, UOI_NAME, NULL, 0, &length);
  if (rv || GetLastError() != ERROR_INSUFFICIENT_BUFFER)
    abort();

  length /= sizeof(WCHAR);
  std::vector<WCHAR> buffer(length);
  if (!GetUserObjectInformationW(desktop_, UOI_NAME, &buffer[0],
                                 length * sizeof(WCHAR), &length)) {
    RTC_LOG(LS_ERROR) << "Failed to query the desktop name: " << GetLastError();
    return false;
  }

  desktop_name_out->assign(&buffer[0], length / sizeof(WCHAR));
  return true;
}

bool Desktop::IsSame(const Desktop& other) const {
  std::wstring name;
  if (!GetName(&name))
    return false;

  std::wstring other_name;
  if (!other.GetName(&other_name))
    return false;

  return name == other_name;
}

bool Desktop::SetThreadDesktop() const {
  if (!::SetThreadDesktop(desktop_)) {
    RTC_LOG(LS_ERROR) << "Failed to assign the desktop to the current thread: "
                      << GetLastError();
    return false;
  }

  return true;
}

Desktop* Desktop::GetDesktop(const WCHAR* desktop_name) {
  ACCESS_MASK desired_access = DESKTOP_CREATEMENU | DESKTOP_CREATEWINDOW |
                               DESKTOP_ENUMERATE | DESKTOP_HOOKCONTROL |
                               DESKTOP_WRITEOBJECTS | DESKTOP_READOBJECTS |
                               DESKTOP_SWITCHDESKTOP | GENERIC_WRITE;
  HDESK desktop = OpenDesktopW(desktop_name, 0, FALSE, desired_access);
  if (desktop == NULL) {
    RTC_LOG(LS_ERROR) << "Failed to open the desktop '"
                      << rtc::ToUtf8(desktop_name) << "': " << GetLastError();
    return NULL;
  }

  return new Desktop(desktop, true);
}

Desktop* Desktop::GetInputDesktop() {
  HDESK desktop = OpenInputDesktop(
      0, FALSE, GENERIC_READ | GENERIC_WRITE | GENERIC_EXECUTE);
  if (desktop == NULL)
    return NULL;

  return new Desktop(desktop, true);
}

Desktop* Desktop::GetThreadDesktop() {
  HDESK desktop = ::GetThreadDesktop(GetCurrentThreadId());
  if (desktop == NULL) {
    RTC_LOG(LS_ERROR)
        << "Failed to retrieve the handle of the desktop assigned to "
           "the current thread: "
        << GetLastError();
    return NULL;
  }

  return new Desktop(desktop, false);
}

}  // namespace webrtc
