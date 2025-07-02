/*
 *  Copyright (c) 2016 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "modules/desktop_capture/desktop_capturer.h"

#include <stdlib.h>
#include <string.h>

#include <cstring>
#include <utility>

#include "modules/desktop_capture/cropping_window_capturer.h"
#include "modules/desktop_capture/desktop_capture_options.h"
#include "modules/desktop_capture/desktop_capturer_differ_wrapper.h"
#include "system_wrappers/include/metrics.h"

#if defined(RTC_ENABLE_WIN_WGC)
#include "modules/desktop_capture/win/wgc_capturer_win.h"
#include "rtc_base/win/windows_version.h"
#endif  // defined(RTC_ENABLE_WIN_WGC)

#if defined(WEBRTC_USE_PIPEWIRE)
#include "modules/desktop_capture/linux/wayland/base_capturer_pipewire.h"
#endif

namespace webrtc {

void LogDesktopCapturerFullscreenDetectorUsage() {
  RTC_HISTOGRAM_BOOLEAN("WebRTC.Screenshare.DesktopCapturerFullscreenDetector",
                        true);
}

DesktopCapturer::~DesktopCapturer() = default;

DelegatedSourceListController*
DesktopCapturer::GetDelegatedSourceListController() {
  return nullptr;
}

void DesktopCapturer::SetSharedMemoryFactory(
    std::unique_ptr<SharedMemoryFactory> shared_memory_factory) {}

void DesktopCapturer::SetExcludedWindow(WindowId window) {}

bool DesktopCapturer::GetSourceList(SourceList* sources) {
  return true;
}

bool DesktopCapturer::SelectSource(SourceId id) {
  return false;
}

bool DesktopCapturer::FocusOnSelectedSource() {
  return false;
}

bool DesktopCapturer::IsOccluded(const DesktopVector& pos) {
  return false;
}

// static
std::unique_ptr<DesktopCapturer> DesktopCapturer::CreateWindowCapturer(
    const DesktopCaptureOptions& options) {
#if defined(RTC_ENABLE_WIN_WGC)
  if (options.allow_wgc_window_capturer() &&
      IsWgcSupported(CaptureType::kWindow)) {
    return WgcCapturerWin::CreateRawWindowCapturer(options);
  }
#endif  // defined(RTC_ENABLE_WIN_WGC)

#if defined(WEBRTC_WIN)
  if (options.allow_cropping_window_capturer()) {
    return CroppingWindowCapturer::CreateCapturer(options);
  }
#endif  // defined(WEBRTC_WIN)

  std::unique_ptr<DesktopCapturer> capturer = CreateRawWindowCapturer(options);
  if (capturer && options.detect_updated_region()) {
    capturer.reset(new DesktopCapturerDifferWrapper(std::move(capturer)));
  }

  return capturer;
}

// static
std::unique_ptr<DesktopCapturer> DesktopCapturer::CreateScreenCapturer(
    const DesktopCaptureOptions& options) {
#if defined(RTC_ENABLE_WIN_WGC)
  if (options.allow_wgc_screen_capturer() &&
      IsWgcSupported(CaptureType::kScreen)) {
    return WgcCapturerWin::CreateRawScreenCapturer(options);
  }
#endif  // defined(RTC_ENABLE_WIN_WGC)

  std::unique_ptr<DesktopCapturer> capturer = CreateRawScreenCapturer(options);
  if (capturer && options.detect_updated_region()) {
    capturer.reset(new DesktopCapturerDifferWrapper(std::move(capturer)));
  }

  return capturer;
}

// static
std::unique_ptr<DesktopCapturer> DesktopCapturer::CreateGenericCapturer(
    const DesktopCaptureOptions& options) {
  std::unique_ptr<DesktopCapturer> capturer;

#if defined(WEBRTC_USE_PIPEWIRE)
  if (options.allow_pipewire() && DesktopCapturer::IsRunningUnderWayland()) {
    capturer = std::make_unique<BaseCapturerPipeWire>(
        options, CaptureType::kAnyScreenContent);
  }

  if (capturer && options.detect_updated_region()) {
    capturer.reset(new DesktopCapturerDifferWrapper(std::move(capturer)));
  }
#endif  // defined(WEBRTC_USE_PIPEWIRE)

  return capturer;
}

#if defined(WEBRTC_USE_PIPEWIRE) || defined(WEBRTC_USE_X11)
bool DesktopCapturer::IsRunningUnderWayland() {
  const char* xdg_session_type = getenv("XDG_SESSION_TYPE");
  if (!xdg_session_type || strncmp(xdg_session_type, "wayland", 7) != 0)
    return false;

  if (!(getenv("WAYLAND_DISPLAY")))
    return false;

  return true;
}
#endif  // defined(WEBRTC_USE_PIPEWIRE) || defined(WEBRTC_USE_X11)

}  // namespace webrtc
