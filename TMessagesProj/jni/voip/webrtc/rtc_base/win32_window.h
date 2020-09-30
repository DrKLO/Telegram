/*
 *  Copyright 2004 The WebRTC Project Authors. All rights reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#ifndef RTC_BASE_WIN32_WINDOW_H_
#define RTC_BASE_WIN32_WINDOW_H_

#if defined(WEBRTC_WIN)

#include "rtc_base/win32.h"

namespace rtc {

///////////////////////////////////////////////////////////////////////////////
// Win32Window
///////////////////////////////////////////////////////////////////////////////

class Win32Window {
 public:
  Win32Window();
  virtual ~Win32Window();

  HWND handle() const { return wnd_; }

  bool Create(HWND parent,
              const wchar_t* title,
              DWORD style,
              DWORD exstyle,
              int x,
              int y,
              int cx,
              int cy);
  void Destroy();

  // Call this when your DLL unloads.
  static void Shutdown();

 protected:
  virtual bool OnMessage(UINT uMsg,
                         WPARAM wParam,
                         LPARAM lParam,
                         LRESULT& result);

  virtual bool OnClose();
  virtual void OnNcDestroy();

 private:
  static LRESULT CALLBACK WndProc(HWND hwnd,
                                  UINT uMsg,
                                  WPARAM wParam,
                                  LPARAM lParam);

  HWND wnd_;
  static HINSTANCE instance_;
  static ATOM window_class_;
};

///////////////////////////////////////////////////////////////////////////////

}  // namespace rtc

#endif  // WEBRTC_WIN

#endif  // RTC_BASE_WIN32_WINDOW_H_
