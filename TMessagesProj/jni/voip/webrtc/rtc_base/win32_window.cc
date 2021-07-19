/*
 *  Copyright 2004 The WebRTC Project Authors. All rights reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "rtc_base/win32_window.h"

#include "rtc_base/checks.h"
#include "rtc_base/logging.h"

namespace rtc {

///////////////////////////////////////////////////////////////////////////////
// Win32Window
///////////////////////////////////////////////////////////////////////////////

static const wchar_t kWindowBaseClassName[] = L"RtcWindowBaseClass";
HINSTANCE Win32Window::instance_ = nullptr;
ATOM Win32Window::window_class_ = 0;

Win32Window::Win32Window() : wnd_(nullptr) {}

Win32Window::~Win32Window() { RTC_DCHECK(nullptr == wnd_); }

bool Win32Window::Create(HWND parent, const wchar_t* title, DWORD style,
                         DWORD exstyle, int x, int y, int cx, int cy) {
  if (wnd_) {
    // Window already exists.
    return false;
  }

  if (!window_class_) {
    if (!GetModuleHandleExW(GET_MODULE_HANDLE_EX_FLAG_FROM_ADDRESS |
                                GET_MODULE_HANDLE_EX_FLAG_UNCHANGED_REFCOUNT,
                            reinterpret_cast<LPCWSTR>(&Win32Window::WndProc),
                            &instance_)) {
      RTC_LOG_GLE(LS_ERROR) << "GetModuleHandleEx failed";
      return false;
    }

    // Register or reregister the class as necessary.  window_class_ == nullptr
    // is not an infallible indicator that the class is unregistered.
    WNDCLASSEXW wcex;
    memset(&wcex, 0, sizeof(wcex));
    wcex.cbSize = sizeof(wcex);
    if (::GetClassInfoExW(instance_, kWindowBaseClassName, &wcex) &&
        !::UnregisterClassW(kWindowBaseClassName, instance_)) {
      RTC_LOG_GLE(LS_ERROR) << "UnregisterClass failed.";
    }

    memset(&wcex, 0, sizeof(wcex));
    wcex.cbSize = sizeof(wcex);
    wcex.hInstance = instance_;
    wcex.lpfnWndProc = &Win32Window::WndProc;
    wcex.lpszClassName = kWindowBaseClassName;
    window_class_ = ::RegisterClassExW(&wcex);
    if (!window_class_) {
      RTC_LOG_GLE(LS_ERROR) << "RegisterClassEx failed";
      return false;
    }
  }
  wnd_ = ::CreateWindowExW(exstyle, kWindowBaseClassName, title, style, x, y,
                           cx, cy, parent, nullptr, instance_, this);
  return (nullptr != wnd_);
}

void Win32Window::Destroy() {
  const bool success = ::DestroyWindow(wnd_);
  RTC_DCHECK(success);
}

void Win32Window::Shutdown() {
  if (window_class_) {
    if (!::UnregisterClass(MAKEINTATOM(window_class_), instance_)) {
      RTC_LOG_GLE(LS_ERROR) << "UnregisterClass failed.";
    }
    window_class_ = 0;
  }
}

bool Win32Window::OnMessage(UINT uMsg, WPARAM wParam, LPARAM lParam,
                            LRESULT& result) {
  switch (uMsg) {
    case WM_CLOSE:
      if (!OnClose()) {
        result = 0;
        return true;
      }
      break;
  }
  return false;
}

bool Win32Window::OnClose() { return true; }

void Win32Window::OnNcDestroy() {
  // Do nothing. }
}

LRESULT Win32Window::WndProc(HWND hwnd, UINT uMsg, WPARAM wParam,
                             LPARAM lParam) {
  Win32Window* that =
      reinterpret_cast<Win32Window*>(::GetWindowLongPtr(hwnd, GWLP_USERDATA));
  if (!that && (WM_CREATE == uMsg)) {
    CREATESTRUCT* cs = reinterpret_cast<CREATESTRUCT*>(lParam);
    that = static_cast<Win32Window*>(cs->lpCreateParams);
    that->wnd_ = hwnd;
    ::SetWindowLongPtr(hwnd, GWLP_USERDATA, reinterpret_cast<LONG_PTR>(that));
  }
  if (that) {
    LRESULT result;
    bool handled = that->OnMessage(uMsg, wParam, lParam, result);
    if (WM_DESTROY == uMsg) {
      for (HWND child = ::GetWindow(hwnd, GW_CHILD); child;
           child = ::GetWindow(child, GW_HWNDNEXT)) {
        RTC_LOG(LS_INFO) << "Child window: " << static_cast<void*>(child);
      }
    }
    if (WM_NCDESTROY == uMsg) {
      ::SetWindowLongPtr(hwnd, GWLP_USERDATA, NULL);
      that->wnd_ = nullptr;
      that->OnNcDestroy();
    }
    if (handled) {
      return result;
    }
  }
  return ::DefWindowProc(hwnd, uMsg, wParam, lParam);
}

}  // namespace rtc
