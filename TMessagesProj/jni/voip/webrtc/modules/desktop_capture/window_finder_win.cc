/*
 *  Copyright (c) 2017 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "modules/desktop_capture/window_finder_win.h"

#include <windows.h>

#include <memory>

namespace webrtc {

WindowFinderWin::WindowFinderWin() = default;
WindowFinderWin::~WindowFinderWin() = default;

WindowId WindowFinderWin::GetWindowUnderPoint(DesktopVector point) {
  HWND window = WindowFromPoint(POINT{point.x(), point.y()});
  if (!window) {
    return kNullWindowId;
  }

  // The difference between GA_ROOTOWNER and GA_ROOT can be found at
  // https://groups.google.com/a/chromium.org/forum/#!topic/chromium-dev/Hirr_DkuZdw.
  // In short, we should use GA_ROOT, since we only care about the root window
  // but not the owner.
  window = GetAncestor(window, GA_ROOT);
  if (!window) {
    return kNullWindowId;
  }

  return reinterpret_cast<WindowId>(window);
}

// static
std::unique_ptr<WindowFinder> WindowFinder::Create(
    const WindowFinder::Options& options) {
  return std::make_unique<WindowFinderWin>();
}

}  // namespace webrtc
