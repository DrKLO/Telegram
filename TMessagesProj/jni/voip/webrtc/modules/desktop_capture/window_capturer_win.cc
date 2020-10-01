/*
 *  Copyright (c) 2013 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "modules/desktop_capture/desktop_capture_options.h"
#include "modules/desktop_capture/desktop_capturer.h"
#include "modules/desktop_capture/win/window_capturer_win_gdi.h"
#include "modules/desktop_capture/win/window_capturer_win_wgc.h"

namespace webrtc {

// static
std::unique_ptr<DesktopCapturer> DesktopCapturer::CreateRawWindowCapturer(
    const DesktopCaptureOptions& options) {
  // TODO(bugs.webrtc.org/11760): Add a WebRTC field trial (or similar
  // mechanism) and Windows version check here that leads to use of the WGC
  // capturer once it is fully implemented.
  if (true) {
    return WindowCapturerWinGdi::CreateRawWindowCapturer(options);
  } else {
    return WindowCapturerWinWgc::CreateRawWindowCapturer(options);
  }
}

}  // namespace webrtc
