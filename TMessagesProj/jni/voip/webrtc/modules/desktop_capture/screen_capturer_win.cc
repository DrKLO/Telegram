/*
 *  Copyright (c) 2013 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include <memory>
#include <utility>

#include "modules/desktop_capture/blank_detector_desktop_capturer_wrapper.h"
#include "modules/desktop_capture/desktop_capture_options.h"
#include "modules/desktop_capture/desktop_capturer.h"
#include "modules/desktop_capture/fallback_desktop_capturer_wrapper.h"
#include "modules/desktop_capture/rgba_color.h"
#include "modules/desktop_capture/win/screen_capturer_win_directx.h"
#include "modules/desktop_capture/win/screen_capturer_win_gdi.h"

namespace webrtc {

namespace {

std::unique_ptr<DesktopCapturer> CreateScreenCapturerWinDirectx(
    const DesktopCaptureOptions& options) {
  std::unique_ptr<DesktopCapturer> capturer(
      new ScreenCapturerWinDirectx(options));
  capturer.reset(new BlankDetectorDesktopCapturerWrapper(
      std::move(capturer), RgbaColor(0, 0, 0, 0)));
  return capturer;
}

}  // namespace

// static
std::unique_ptr<DesktopCapturer> DesktopCapturer::CreateRawScreenCapturer(
    const DesktopCaptureOptions& options) {
  // Default capturer if no options are enabled is GDI.
  std::unique_ptr<DesktopCapturer> capturer(new ScreenCapturerWinGdi(options));

  // If DirectX is enabled use it as main capturer with GDI as fallback.
  if (options.allow_directx_capturer()) {
    // `dxgi_duplicator_controller` should be alive in this scope to ensure it
    // won't unload DxgiDuplicatorController.
    auto dxgi_duplicator_controller = DxgiDuplicatorController::Instance();
    if (ScreenCapturerWinDirectx::IsSupported()) {
      capturer.reset(new FallbackDesktopCapturerWrapper(
          CreateScreenCapturerWinDirectx(options), std::move(capturer)));
      return capturer;
    }
  }

  // Use GDI as default capturer without any fallback solution.
  return capturer;
}

}  // namespace webrtc
