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

#if defined(RTC_ENABLE_WIN_WGC)
#include "modules/desktop_capture/blank_detector_desktop_capturer_wrapper.h"
#include "modules/desktop_capture/fallback_desktop_capturer_wrapper.h"
#include "modules/desktop_capture/win/wgc_capturer_win.h"
#include "rtc_base/win/windows_version.h"
#endif  // defined(RTC_ENABLE_WIN_WGC)

namespace webrtc {

// static
std::unique_ptr<DesktopCapturer> DesktopCapturer::CreateRawWindowCapturer(
    const DesktopCaptureOptions& options) {
  std::unique_ptr<DesktopCapturer> capturer(
      WindowCapturerWinGdi::CreateRawWindowCapturer(options));
#if defined(RTC_ENABLE_WIN_WGC)
  if (options.allow_wgc_capturer_fallback() &&
      rtc::rtc_win::GetVersion() >= rtc::rtc_win::Version::VERSION_WIN11) {
    // BlankDectector capturer will send an error when it detects a failed
    // GDI rendering, then Fallback capturer will try to capture it again with
    // WGC.
    capturer = std::make_unique<BlankDetectorDesktopCapturerWrapper>(
        std::move(capturer), RgbaColor(0, 0, 0, 0),
        /*check_per_capture*/ true);

    capturer = std::make_unique<FallbackDesktopCapturerWrapper>(
        std::move(capturer),
        WgcCapturerWin::CreateRawWindowCapturer(
            options, /*allow_delayed_capturable_check*/ true));
  }
#endif  // defined(RTC_ENABLE_WIN_WGC)
  return capturer;
}

}  // namespace webrtc
