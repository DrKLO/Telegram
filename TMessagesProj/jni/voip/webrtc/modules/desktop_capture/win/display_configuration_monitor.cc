/*
 *  Copyright (c) 2017 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "modules/desktop_capture/win/display_configuration_monitor.h"

#include <windows.h>

#include "modules/desktop_capture/win/screen_capture_utils.h"
#include "rtc_base/logging.h"

namespace webrtc {

bool DisplayConfigurationMonitor::IsChanged(
    DesktopCapturer::SourceId source_id) {
  DesktopRect rect = GetFullscreenRect();
  DesktopVector dpi = GetDpiForSourceId(source_id);

  if (!initialized_) {
    initialized_ = true;
    rect_ = rect;
    source_dpis_.emplace(source_id, std::move(dpi));
    return false;
  }

  if (!source_dpis_.contains(source_id)) {
    // If this is the first time we've seen this source_id, use the current DPI
    // so the monitor does not indicate a change and possibly get reset.
    source_dpis_.emplace(source_id, dpi);
  }

  bool has_changed = false;
  if (!rect.equals(rect_) || !source_dpis_.at(source_id).equals(dpi)) {
    has_changed = true;
    rect_ = rect;
    source_dpis_.emplace(source_id, std::move(dpi));
  }

  return has_changed;
}

void DisplayConfigurationMonitor::Reset() {
  initialized_ = false;
  source_dpis_.clear();
  rect_ = {};
}

DesktopVector DisplayConfigurationMonitor::GetDpiForSourceId(
    DesktopCapturer::SourceId source_id) {
  HMONITOR monitor = 0;
  if (source_id == kFullDesktopScreenId) {
    // Get a handle to the primary monitor when capturing the full desktop.
    monitor = MonitorFromPoint({0, 0}, MONITOR_DEFAULTTOPRIMARY);
  } else if (!GetHmonitorFromDeviceIndex(source_id, &monitor)) {
    RTC_LOG(LS_WARNING) << "GetHmonitorFromDeviceIndex failed.";
  }
  return GetDpiForMonitor(monitor);
}

}  // namespace webrtc
