/*
 *  Copyright (c) 2013 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include <assert.h>
#include <string.h>

#include <memory>

#include "modules/desktop_capture/desktop_capture_types.h"
#include "modules/desktop_capture/desktop_frame.h"
#include "modules/desktop_capture/desktop_geometry.h"
#include "modules/desktop_capture/mouse_cursor.h"
#include "modules/desktop_capture/mouse_cursor_monitor.h"
#include "modules/desktop_capture/win/cursor.h"
#include "modules/desktop_capture/win/screen_capture_utils.h"
#include "modules/desktop_capture/win/window_capture_utils.h"
#include "rtc_base/logging.h"

namespace webrtc {

namespace {

bool IsSameCursorShape(const CURSORINFO& left, const CURSORINFO& right) {
  // If the cursors are not showing, we do not care the hCursor handle.
  return left.flags == right.flags &&
         (left.flags != CURSOR_SHOWING || left.hCursor == right.hCursor);
}

}  // namespace

class MouseCursorMonitorWin : public MouseCursorMonitor {
 public:
  explicit MouseCursorMonitorWin(HWND window);
  explicit MouseCursorMonitorWin(ScreenId screen);
  ~MouseCursorMonitorWin() override;

  void Init(Callback* callback, Mode mode) override;
  void Capture() override;

 private:
  // Get the rect of the currently selected screen, relative to the primary
  // display's top-left. If the screen is disabled or disconnected, or any error
  // happens, an empty rect is returned.
  DesktopRect GetScreenRect();

  HWND window_;
  ScreenId screen_;

  Callback* callback_;
  Mode mode_;

  HDC desktop_dc_;

  // The last CURSORINFO (converted to MouseCursor) we have sent to the client.
  CURSORINFO last_cursor_;
};

MouseCursorMonitorWin::MouseCursorMonitorWin(HWND window)
    : window_(window),
      screen_(kInvalidScreenId),
      callback_(NULL),
      mode_(SHAPE_AND_POSITION),
      desktop_dc_(NULL) {
  memset(&last_cursor_, 0, sizeof(CURSORINFO));
}

MouseCursorMonitorWin::MouseCursorMonitorWin(ScreenId screen)
    : window_(NULL),
      screen_(screen),
      callback_(NULL),
      mode_(SHAPE_AND_POSITION),
      desktop_dc_(NULL) {
  assert(screen >= kFullDesktopScreenId);
  memset(&last_cursor_, 0, sizeof(CURSORINFO));
}

MouseCursorMonitorWin::~MouseCursorMonitorWin() {
  if (desktop_dc_)
    ReleaseDC(NULL, desktop_dc_);
}

void MouseCursorMonitorWin::Init(Callback* callback, Mode mode) {
  assert(!callback_);
  assert(callback);

  callback_ = callback;
  mode_ = mode;

  desktop_dc_ = GetDC(NULL);
}

void MouseCursorMonitorWin::Capture() {
  assert(callback_);

  CURSORINFO cursor_info;
  cursor_info.cbSize = sizeof(CURSORINFO);
  if (!GetCursorInfo(&cursor_info)) {
    RTC_LOG_F(LS_ERROR) << "Unable to get cursor info. Error = "
                        << GetLastError();
    return;
  }

  if (!IsSameCursorShape(cursor_info, last_cursor_)) {
    if (cursor_info.flags == CURSOR_SUPPRESSED) {
      // The cursor is intentionally hidden now, send an empty bitmap.
      last_cursor_ = cursor_info;
      callback_->OnMouseCursor(new MouseCursor(
          new BasicDesktopFrame(DesktopSize()), DesktopVector()));
    } else {
      // According to MSDN https://goo.gl/u6gyuC, HCURSOR instances returned by
      // functions other than CreateCursor do not need to be actively destroyed.
      // And CloseHandle function (https://goo.gl/ja5ycW) does not close a
      // cursor, so assume a HCURSOR does not need to be closed.
      if (cursor_info.flags == 0) {
        // Host machine does not have a hardware mouse attached, we will send a
        // default one instead.
        // Note, Windows automatically caches cursor resource, so we do not need
        // to cache the result of LoadCursor.
        cursor_info.hCursor = LoadCursor(nullptr, IDC_ARROW);
      }
      std::unique_ptr<MouseCursor> cursor(
          CreateMouseCursorFromHCursor(desktop_dc_, cursor_info.hCursor));
      if (cursor) {
        last_cursor_ = cursor_info;
        callback_->OnMouseCursor(cursor.release());
      }
    }
  }

  if (mode_ != SHAPE_AND_POSITION)
    return;

  // CURSORINFO::ptScreenPos is in full desktop coordinate.
  DesktopVector position(cursor_info.ptScreenPos.x, cursor_info.ptScreenPos.y);
  bool inside = cursor_info.flags == CURSOR_SHOWING;

  if (window_) {
    DesktopRect original_rect;
    DesktopRect cropped_rect;
    if (!GetCroppedWindowRect(window_, /*avoid_cropping_border*/ false,
                              &cropped_rect, &original_rect)) {
      position.set(0, 0);
      inside = false;
    } else {
      if (inside) {
        HWND windowUnderCursor = WindowFromPoint(cursor_info.ptScreenPos);
        inside = windowUnderCursor
                     ? (window_ == GetAncestor(windowUnderCursor, GA_ROOT))
                     : false;
      }
      position = position.subtract(cropped_rect.top_left());
    }
  } else {
    assert(screen_ != kInvalidScreenId);
    DesktopRect rect = GetScreenRect();
    if (inside)
      inside = rect.Contains(position);
    position = position.subtract(rect.top_left());
  }

  callback_->OnMouseCursorPosition(position);
}

DesktopRect MouseCursorMonitorWin::GetScreenRect() {
  assert(screen_ != kInvalidScreenId);
  if (screen_ == kFullDesktopScreenId) {
    return DesktopRect::MakeXYWH(GetSystemMetrics(SM_XVIRTUALSCREEN),
                                 GetSystemMetrics(SM_YVIRTUALSCREEN),
                                 GetSystemMetrics(SM_CXVIRTUALSCREEN),
                                 GetSystemMetrics(SM_CYVIRTUALSCREEN));
  }
  DISPLAY_DEVICE device;
  device.cb = sizeof(device);
  BOOL result = EnumDisplayDevices(NULL, screen_, &device, 0);
  if (!result)
    return DesktopRect();

  DEVMODE device_mode;
  device_mode.dmSize = sizeof(device_mode);
  device_mode.dmDriverExtra = 0;
  result = EnumDisplaySettingsEx(device.DeviceName, ENUM_CURRENT_SETTINGS,
                                 &device_mode, 0);
  if (!result)
    return DesktopRect();

  return DesktopRect::MakeXYWH(
      device_mode.dmPosition.x, device_mode.dmPosition.y,
      device_mode.dmPelsWidth, device_mode.dmPelsHeight);
}

MouseCursorMonitor* MouseCursorMonitor::CreateForWindow(
    const DesktopCaptureOptions& options,
    WindowId window) {
  return new MouseCursorMonitorWin(reinterpret_cast<HWND>(window));
}

MouseCursorMonitor* MouseCursorMonitor::CreateForScreen(
    const DesktopCaptureOptions& options,
    ScreenId screen) {
  return new MouseCursorMonitorWin(screen);
}

std::unique_ptr<MouseCursorMonitor> MouseCursorMonitor::Create(
    const DesktopCaptureOptions& options) {
  return std::unique_ptr<MouseCursorMonitor>(
      CreateForScreen(options, kFullDesktopScreenId));
}

}  // namespace webrtc
