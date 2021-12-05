/*
 *  Copyright (c) 2020 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "rtc_base/win/hstring.h"

#include <libloaderapi.h>
#include <winstring.h>

namespace {

FARPROC LoadComBaseFunction(const char* function_name) {
  static HMODULE const handle =
      ::LoadLibraryExW(L"combase.dll", nullptr, LOAD_LIBRARY_SEARCH_SYSTEM32);
  return handle ? ::GetProcAddress(handle, function_name) : nullptr;
}

decltype(&::WindowsCreateString) GetWindowsCreateString() {
  static decltype(&::WindowsCreateString) const function =
      reinterpret_cast<decltype(&::WindowsCreateString)>(
          LoadComBaseFunction("WindowsCreateString"));
  return function;
}

decltype(&::WindowsDeleteString) GetWindowsDeleteString() {
  static decltype(&::WindowsDeleteString) const function =
      reinterpret_cast<decltype(&::WindowsDeleteString)>(
          LoadComBaseFunction("WindowsDeleteString"));
  return function;
}

}  // namespace

namespace webrtc {

bool ResolveCoreWinRTStringDelayload() {
  return GetWindowsDeleteString() && GetWindowsCreateString();
}

HRESULT CreateHstring(const wchar_t* src, uint32_t len, HSTRING* out_hstr) {
  decltype(&::WindowsCreateString) create_string_func =
      GetWindowsCreateString();
  if (!create_string_func)
    return E_FAIL;
  return create_string_func(src, len, out_hstr);
}

HRESULT DeleteHstring(HSTRING hstr) {
  decltype(&::WindowsDeleteString) delete_string_func =
      GetWindowsDeleteString();
  if (!delete_string_func)
    return E_FAIL;
  return delete_string_func(hstr);
}

}  // namespace webrtc
