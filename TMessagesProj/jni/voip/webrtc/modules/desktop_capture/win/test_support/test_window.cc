/*
 *  Copyright (c) 2020 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "modules/desktop_capture/win/test_support/test_window.h"

namespace webrtc {
namespace {

const WCHAR kWindowClass[] = L"DesktopCaptureTestWindowClass";
const int kWindowHeight = 200;
const int kWindowWidth = 300;

LRESULT CALLBACK WindowProc(HWND hwnd,
                            UINT msg,
                            WPARAM w_param,
                            LPARAM l_param) {
  switch (msg) {
    case WM_PAINT:
      PAINTSTRUCT paint_struct;
      HDC hdc = BeginPaint(hwnd, &paint_struct);

      // Paint the window so the color is consistent and we can inspect the
      // pixels in tests and know what to expect.
      FillRect(hdc, &paint_struct.rcPaint,
               CreateSolidBrush(RGB(kTestWindowRValue, kTestWindowGValue,
                                    kTestWindowBValue)));

      EndPaint(hwnd, &paint_struct);
  }
  return DefWindowProc(hwnd, msg, w_param, l_param);
}

}  // namespace

WindowInfo CreateTestWindow(const WCHAR* window_title,
                            const int height,
                            const int width,
                            const LONG extended_styles) {
  WindowInfo info;
  ::GetModuleHandleExW(GET_MODULE_HANDLE_EX_FLAG_FROM_ADDRESS |
                           GET_MODULE_HANDLE_EX_FLAG_UNCHANGED_REFCOUNT,
                       reinterpret_cast<LPCWSTR>(&WindowProc),
                       &info.window_instance);

  WNDCLASSEXW wcex;
  memset(&wcex, 0, sizeof(wcex));
  wcex.cbSize = sizeof(wcex);
  wcex.style = CS_HREDRAW | CS_VREDRAW;
  wcex.hInstance = info.window_instance;
  wcex.lpfnWndProc = &WindowProc;
  wcex.lpszClassName = kWindowClass;
  info.window_class = ::RegisterClassExW(&wcex);

  // Use the default height and width if the caller did not supply the optional
  // height and width parameters, or if they supplied invalid values.
  int window_height = height <= 0 ? kWindowHeight : height;
  int window_width = width <= 0 ? kWindowWidth : width;
  info.hwnd =
      ::CreateWindowExW(extended_styles, kWindowClass, window_title,
                        WS_OVERLAPPEDWINDOW, CW_USEDEFAULT, CW_USEDEFAULT,
                        window_width, window_height, /*parent_window=*/nullptr,
                        /*menu_bar=*/nullptr, info.window_instance,
                        /*additional_params=*/nullptr);

  ::ShowWindow(info.hwnd, SW_SHOWNORMAL);
  ::UpdateWindow(info.hwnd);
  return info;
}

void ResizeTestWindow(const HWND hwnd, const int width, const int height) {
  // SWP_NOMOVE results in the x and y params being ignored.
  ::SetWindowPos(hwnd, HWND_TOP, /*x-coord=*/0, /*y-coord=*/0, width, height,
                 SWP_SHOWWINDOW | SWP_NOMOVE);
  ::UpdateWindow(hwnd);
}

void MoveTestWindow(const HWND hwnd, const int x, const int y) {
  // SWP_NOSIZE results in the width and height params being ignored.
  ::SetWindowPos(hwnd, HWND_TOP, x, y, /*width=*/0, /*height=*/0,
                 SWP_SHOWWINDOW | SWP_NOSIZE);
  ::UpdateWindow(hwnd);
}

void MinimizeTestWindow(const HWND hwnd) {
  ::ShowWindow(hwnd, SW_MINIMIZE);
}

void UnminimizeTestWindow(const HWND hwnd) {
  ::OpenIcon(hwnd);
}

void DestroyTestWindow(WindowInfo info) {
  ::DestroyWindow(info.hwnd);
  ::UnregisterClass(MAKEINTATOM(info.window_class), info.window_instance);
}

}  // namespace webrtc
