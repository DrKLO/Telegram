/*
 *  Copyright (c) 2017 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "modules/desktop_capture/linux/x11/window_finder_x11.h"

#include <X11/X.h>

#include <memory>

#include "modules/desktop_capture/linux/x11/window_list_utils.h"
#include "rtc_base/checks.h"

namespace webrtc {

WindowFinderX11::WindowFinderX11(XAtomCache* cache) : cache_(cache) {
  RTC_DCHECK(cache_);
}

WindowFinderX11::~WindowFinderX11() = default;

WindowId WindowFinderX11::GetWindowUnderPoint(DesktopVector point) {
  WindowId id = kNullWindowId;
  GetWindowList(cache_, [&id, this, point](::Window window) {
    DesktopRect rect;
    if (GetWindowRect(this->cache_->display(), window, &rect) &&
        rect.Contains(point)) {
      id = window;
      return false;
    }
    return true;
  });
  return id;
}

// static
std::unique_ptr<WindowFinder> WindowFinder::Create(
    const WindowFinder::Options& options) {
  if (options.cache == nullptr) {
    return nullptr;
  }

  return std::make_unique<WindowFinderX11>(options.cache);
}

}  // namespace webrtc
