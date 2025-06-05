/*
 *  Copyright (c) 2017 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "modules/desktop_capture/window_finder_mac.h"

#include <CoreFoundation/CoreFoundation.h>

#include <memory>
#include <utility>

#include "modules/desktop_capture/mac/desktop_configuration.h"
#include "modules/desktop_capture/mac/desktop_configuration_monitor.h"
#include "modules/desktop_capture/mac/window_list_utils.h"

namespace webrtc {

WindowFinderMac::WindowFinderMac(
    rtc::scoped_refptr<DesktopConfigurationMonitor> configuration_monitor)
    : configuration_monitor_(std::move(configuration_monitor)) {}
WindowFinderMac::~WindowFinderMac() = default;

WindowId WindowFinderMac::GetWindowUnderPoint(DesktopVector point) {
  WindowId id = kNullWindowId;
  GetWindowList(
      [&id, point](CFDictionaryRef window) {
        DesktopRect bounds;
        bounds = GetWindowBounds(window);
        if (bounds.Contains(point)) {
          id = GetWindowId(window);
          return false;
        }
        return true;
      },
      true,
      true);
  return id;
}

// static
std::unique_ptr<WindowFinder> WindowFinder::Create(
    const WindowFinder::Options& options) {
  return std::make_unique<WindowFinderMac>(options.configuration_monitor);
}

}  // namespace webrtc
