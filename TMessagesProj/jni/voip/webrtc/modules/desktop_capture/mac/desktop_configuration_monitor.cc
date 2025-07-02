/*
 *  Copyright (c) 2014 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "modules/desktop_capture/mac/desktop_configuration_monitor.h"

#include "modules/desktop_capture/mac/desktop_configuration.h"
#include "rtc_base/logging.h"
#include "rtc_base/trace_event.h"

namespace webrtc {

DesktopConfigurationMonitor::DesktopConfigurationMonitor() {
  CGError err = CGDisplayRegisterReconfigurationCallback(
      DesktopConfigurationMonitor::DisplaysReconfiguredCallback, this);
  if (err != kCGErrorSuccess)
    RTC_LOG(LS_ERROR) << "CGDisplayRegisterReconfigurationCallback " << err;
  MutexLock lock(&desktop_configuration_lock_);
  desktop_configuration_ = MacDesktopConfiguration::GetCurrent(
      MacDesktopConfiguration::TopLeftOrigin);
}

DesktopConfigurationMonitor::~DesktopConfigurationMonitor() {
  CGError err = CGDisplayRemoveReconfigurationCallback(
      DesktopConfigurationMonitor::DisplaysReconfiguredCallback, this);
  if (err != kCGErrorSuccess)
    RTC_LOG(LS_ERROR) << "CGDisplayRemoveReconfigurationCallback " << err;
}

MacDesktopConfiguration DesktopConfigurationMonitor::desktop_configuration() {
  MutexLock lock(&desktop_configuration_lock_);
  return desktop_configuration_;
}

// static
// This method may be called on any system thread.
void DesktopConfigurationMonitor::DisplaysReconfiguredCallback(
    CGDirectDisplayID display,
    CGDisplayChangeSummaryFlags flags,
    void* user_parameter) {
  DesktopConfigurationMonitor* monitor =
      reinterpret_cast<DesktopConfigurationMonitor*>(user_parameter);
  monitor->DisplaysReconfigured(display, flags);
}

void DesktopConfigurationMonitor::DisplaysReconfigured(
    CGDirectDisplayID display,
    CGDisplayChangeSummaryFlags flags) {
  TRACE_EVENT0("webrtc", "DesktopConfigurationMonitor::DisplaysReconfigured");
  RTC_LOG(LS_INFO) << "DisplaysReconfigured: "
                      "DisplayID "
                   << display << "; ChangeSummaryFlags " << flags;

  if (flags & kCGDisplayBeginConfigurationFlag) {
    reconfiguring_displays_.insert(display);
    return;
  }

  reconfiguring_displays_.erase(display);
  if (reconfiguring_displays_.empty()) {
    MutexLock lock(&desktop_configuration_lock_);
    desktop_configuration_ = MacDesktopConfiguration::GetCurrent(
        MacDesktopConfiguration::TopLeftOrigin);
  }
}

}  // namespace webrtc
