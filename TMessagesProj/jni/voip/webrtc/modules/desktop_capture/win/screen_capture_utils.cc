/*
 *  Copyright (c) 2014 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "modules/desktop_capture/win/screen_capture_utils.h"

#include <shellscalingapi.h>
#include <windows.h>

#include <string>
#include <vector>

#include "modules/desktop_capture/desktop_capturer.h"
#include "modules/desktop_capture/desktop_geometry.h"
#include "rtc_base/checks.h"
#include "rtc_base/logging.h"
#include "rtc_base/string_utils.h"
#include "rtc_base/win32.h"

namespace webrtc {

bool HasActiveDisplay() {
  DesktopCapturer::SourceList screens;

  return GetScreenList(&screens) && !screens.empty();
}

bool GetScreenList(DesktopCapturer::SourceList* screens,
                   std::vector<std::string>* device_names /* = nullptr */) {
  RTC_DCHECK(screens->empty());
  RTC_DCHECK(!device_names || device_names->empty());

  BOOL enum_result = TRUE;
  for (int device_index = 0;; ++device_index) {
    DISPLAY_DEVICEW device;
    device.cb = sizeof(device);
    enum_result = EnumDisplayDevicesW(NULL, device_index, &device, 0);

    // `enum_result` is 0 if we have enumerated all devices.
    if (!enum_result) {
      break;
    }

    // We only care about active displays.
    if (!(device.StateFlags & DISPLAY_DEVICE_ACTIVE)) {
      continue;
    }

    screens->push_back({device_index, std::string()});
    if (device_names) {
      device_names->push_back(rtc::ToUtf8(device.DeviceName));
    }
  }
  return true;
}

bool GetHmonitorFromDeviceIndex(const DesktopCapturer::SourceId device_index,
                                HMONITOR* hmonitor) {
  // A device index of `kFullDesktopScreenId` or -1 represents all screens, an
  // HMONITOR of 0 indicates the same.
  if (device_index == kFullDesktopScreenId) {
    *hmonitor = 0;
    return true;
  }

  std::wstring device_key;
  if (!IsScreenValid(device_index, &device_key)) {
    return false;
  }

  DesktopRect screen_rect = GetScreenRect(device_index, device_key);
  if (screen_rect.is_empty()) {
    return false;
  }

  RECT rect = {screen_rect.left(), screen_rect.top(), screen_rect.right(),
               screen_rect.bottom()};

  HMONITOR monitor = MonitorFromRect(&rect, MONITOR_DEFAULTTONULL);
  if (monitor == NULL) {
    RTC_LOG(LS_WARNING) << "No HMONITOR found for supplied device index.";
    return false;
  }

  *hmonitor = monitor;
  return true;
}

bool IsMonitorValid(const HMONITOR monitor) {
  // An HMONITOR of 0 refers to a virtual monitor that spans all physical
  // monitors.
  if (monitor == 0) {
    // There is a bug in a Windows OS API that causes a crash when capturing if
    // there are no active displays. We must ensure there is an active display
    // before returning true.
    if (!HasActiveDisplay())
      return false;

    return true;
  }

  MONITORINFO monitor_info;
  monitor_info.cbSize = sizeof(MONITORINFO);
  return GetMonitorInfoA(monitor, &monitor_info);
}

DesktopRect GetMonitorRect(const HMONITOR monitor) {
  MONITORINFO monitor_info;
  monitor_info.cbSize = sizeof(MONITORINFO);
  if (!GetMonitorInfoA(monitor, &monitor_info)) {
    return DesktopRect();
  }

  return DesktopRect::MakeLTRB(
      monitor_info.rcMonitor.left, monitor_info.rcMonitor.top,
      monitor_info.rcMonitor.right, monitor_info.rcMonitor.bottom);
}

bool IsScreenValid(const DesktopCapturer::SourceId screen,
                   std::wstring* device_key) {
  if (screen == kFullDesktopScreenId) {
    *device_key = L"";
    return true;
  }

  DISPLAY_DEVICEW device;
  device.cb = sizeof(device);
  BOOL enum_result = EnumDisplayDevicesW(NULL, screen, &device, 0);
  if (enum_result) {
    *device_key = device.DeviceKey;
  }

  return !!enum_result;
}

DesktopRect GetFullscreenRect() {
  return DesktopRect::MakeXYWH(GetSystemMetrics(SM_XVIRTUALSCREEN),
                               GetSystemMetrics(SM_YVIRTUALSCREEN),
                               GetSystemMetrics(SM_CXVIRTUALSCREEN),
                               GetSystemMetrics(SM_CYVIRTUALSCREEN));
}

DesktopVector GetDpiForMonitor(HMONITOR monitor) {
  UINT dpi_x, dpi_y;
  // MDT_EFFECTIVE_DPI includes the scale factor as well as the system DPI.
  HRESULT hr = ::GetDpiForMonitor(monitor, MDT_EFFECTIVE_DPI, &dpi_x, &dpi_y);
  if (SUCCEEDED(hr)) {
    return {static_cast<INT>(dpi_x), static_cast<INT>(dpi_y)};
  }
  RTC_LOG_GLE_EX(LS_WARNING, hr) << "GetDpiForMonitor() failed";

  // If we can't get the per-monitor DPI, then return the system DPI.
  HDC hdc = GetDC(nullptr);
  if (hdc) {
    DesktopVector dpi{GetDeviceCaps(hdc, LOGPIXELSX),
                      GetDeviceCaps(hdc, LOGPIXELSY)};
    ReleaseDC(nullptr, hdc);
    return dpi;
  }

  // If everything fails, then return the default DPI for Windows.
  return {96, 96};
}

DesktopRect GetScreenRect(const DesktopCapturer::SourceId screen,
                          const std::wstring& device_key) {
  if (screen == kFullDesktopScreenId) {
    return GetFullscreenRect();
  }

  DISPLAY_DEVICEW device;
  device.cb = sizeof(device);
  BOOL result = EnumDisplayDevicesW(NULL, screen, &device, 0);
  if (!result) {
    return DesktopRect();
  }

  // Verifies the device index still maps to the same display device, to make
  // sure we are capturing the same device when devices are added or removed.
  // DeviceKey is documented as reserved, but it actually contains the registry
  // key for the device and is unique for each monitor, while DeviceID is not.
  if (device_key != device.DeviceKey) {
    return DesktopRect();
  }

  DEVMODEW device_mode;
  device_mode.dmSize = sizeof(device_mode);
  device_mode.dmDriverExtra = 0;
  result = EnumDisplaySettingsExW(device.DeviceName, ENUM_CURRENT_SETTINGS,
                                  &device_mode, 0);
  if (!result) {
    return DesktopRect();
  }

  return DesktopRect::MakeXYWH(
      device_mode.dmPosition.x, device_mode.dmPosition.y,
      device_mode.dmPelsWidth, device_mode.dmPelsHeight);
}

}  // namespace webrtc
