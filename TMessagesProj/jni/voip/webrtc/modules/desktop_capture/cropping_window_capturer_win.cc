/*
 *  Copyright (c) 2014 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "modules/desktop_capture/cropping_window_capturer.h"
#include "modules/desktop_capture/desktop_capturer_differ_wrapper.h"
#include "modules/desktop_capture/win/screen_capture_utils.h"
#include "modules/desktop_capture/win/selected_window_context.h"
#include "modules/desktop_capture/win/window_capture_utils.h"
#include "rtc_base/logging.h"
#include "rtc_base/trace_event.h"
#include "rtc_base/win/windows_version.h"

namespace webrtc {

namespace {

// Used to pass input data for verifying the selected window is on top.
struct TopWindowVerifierContext : public SelectedWindowContext {
  TopWindowVerifierContext(HWND selected_window,
                           HWND excluded_window,
                           DesktopRect selected_window_rect,
                           WindowCaptureHelperWin* window_capture_helper)
      : SelectedWindowContext(selected_window,
                              selected_window_rect,
                              window_capture_helper),
        excluded_window(excluded_window) {
    RTC_DCHECK_NE(selected_window, excluded_window);
  }

  // Determines whether the selected window is on top (not occluded by any
  // windows except for those it owns or any excluded window).
  bool IsTopWindow() {
    if (!IsSelectedWindowValid()) {
      return false;
    }

    // Enumerate all top-level windows above the selected window in Z-order,
    // checking whether any overlaps it. This uses FindWindowEx rather than
    // EnumWindows because the latter excludes certain system windows (e.g. the
    // Start menu & other taskbar menus) that should be detected here to avoid
    // inadvertent capture.
    int num_retries = 0;
    while (true) {
      HWND hwnd = nullptr;
      while ((hwnd = FindWindowEx(nullptr, hwnd, nullptr, nullptr))) {
        if (hwnd == selected_window()) {
          // Windows are enumerated in top-down Z-order, so we can stop
          // enumerating upon reaching the selected window & report it's on top.
          return true;
        }

        // Ignore the excluded window.
        if (hwnd == excluded_window) {
          continue;
        }

        // Ignore windows that aren't visible on the current desktop.
        if (!window_capture_helper()->IsWindowVisibleOnCurrentDesktop(hwnd)) {
          continue;
        }

        // Ignore Chrome notification windows, especially the notification for
        // the ongoing window sharing. Notes:
        // - This only works with notifications from Chrome, not other Apps.
        // - All notifications from Chrome will be ignored.
        // - This may cause part or whole of notification window being cropped
        // into the capturing of the target window if there is overlapping.
        if (window_capture_helper()->IsWindowChromeNotification(hwnd)) {
          continue;
        }

        // Ignore windows owned by the selected window since we want to capture
        // them.
        if (IsWindowOwnedBySelectedWindow(hwnd)) {
          continue;
        }

        // Check whether this window intersects with the selected window.
        if (IsWindowOverlappingSelectedWindow(hwnd)) {
          // If intersection is not empty, the selected window is not on top.
          return false;
        }
      }

      DWORD lastError = GetLastError();
      if (lastError == ERROR_SUCCESS) {
        // The enumeration completed successfully without finding the selected
        // window (which may have been closed).
        RTC_LOG(LS_WARNING) << "Failed to find selected window (only expected "
                               "if it was closed)";
        RTC_DCHECK(!IsWindow(selected_window()));
        return false;
      } else if (lastError == ERROR_INVALID_WINDOW_HANDLE) {
        // This error may occur if a window is closed around the time it's
        // enumerated; retry the enumeration in this case up to 10 times
        // (this should be a rare race & unlikely to recur).
        if (++num_retries <= 10) {
          RTC_LOG(LS_WARNING) << "Enumeration failed due to race with a window "
                                 "closing; retrying - retry #"
                              << num_retries;
          continue;
        } else {
          RTC_LOG(LS_ERROR)
              << "Exhausted retry allowance around window enumeration failures "
                 "due to races with windows closing";
        }
      }

      // The enumeration failed with an unexpected error (or more repeats of
      // an infrequently-expected error than anticipated). After logging this &
      // firing an assert when enabled, report that the selected window isn't
      // topmost to avoid inadvertent capture of other windows.
      RTC_LOG(LS_ERROR) << "Failed to enumerate windows: " << lastError;
      RTC_DCHECK_NOTREACHED();
      return false;
    }
  }

  const HWND excluded_window;
};

class CroppingWindowCapturerWin : public CroppingWindowCapturer {
 public:
  explicit CroppingWindowCapturerWin(const DesktopCaptureOptions& options)
      : CroppingWindowCapturer(options),
        enumerate_current_process_windows_(
            options.enumerate_current_process_windows()),
        full_screen_window_detector_(options.full_screen_window_detector()) {}

  void CaptureFrame() override;

 private:
  bool ShouldUseScreenCapturer() override;
  DesktopRect GetWindowRectInVirtualScreen() override;

  // Returns either selected by user sourceId or sourceId provided by
  // FullScreenWindowDetector
  WindowId GetWindowToCapture() const;

  // The region from GetWindowRgn in the desktop coordinate if the region is
  // rectangular, or the rect from GetWindowRect if the region is not set.
  DesktopRect window_region_rect_;

  WindowCaptureHelperWin window_capture_helper_;

  bool enumerate_current_process_windows_;

  rtc::scoped_refptr<FullScreenWindowDetector> full_screen_window_detector_;

  // Used to make sure that we only log the usage of fullscreen detection once.
  mutable bool fullscreen_usage_logged_ = false;
};

void CroppingWindowCapturerWin::CaptureFrame() {
  DesktopCapturer* win_capturer = window_capturer();
  if (win_capturer) {
    // Feed the actual list of windows into full screen window detector.
    if (full_screen_window_detector_) {
      full_screen_window_detector_->UpdateWindowListIfNeeded(
          selected_window(), [this](DesktopCapturer::SourceList* sources) {
            // Get the list of top level windows, including ones with empty
            // title. win_capturer_->GetSourceList can't be used here
            // cause it filters out the windows with empty titles and
            // it uses responsiveness check which could lead to performance
            // issues.
            SourceList result;
            int window_list_flags =
                enumerate_current_process_windows_
                    ? GetWindowListFlags::kNone
                    : GetWindowListFlags::kIgnoreCurrentProcessWindows;

            if (!webrtc::GetWindowList(window_list_flags, &result))
              return false;

            // Filter out windows not visible on current desktop
            auto it = std::remove_if(
                result.begin(), result.end(), [this](const auto& source) {
                  HWND hwnd = reinterpret_cast<HWND>(source.id);
                  return !window_capture_helper_
                              .IsWindowVisibleOnCurrentDesktop(hwnd);
                });
            result.erase(it, result.end());

            sources->swap(result);
            return true;
          });
    }
    win_capturer->SelectSource(GetWindowToCapture());
  }

  CroppingWindowCapturer::CaptureFrame();
}

bool CroppingWindowCapturerWin::ShouldUseScreenCapturer() {
  if (rtc::rtc_win::GetVersion() < rtc::rtc_win::Version::VERSION_WIN8 &&
      window_capture_helper_.IsAeroEnabled()) {
    return false;
  }

  const HWND selected = reinterpret_cast<HWND>(GetWindowToCapture());
  // Check if the window is visible on current desktop.
  if (!window_capture_helper_.IsWindowVisibleOnCurrentDesktop(selected)) {
    return false;
  }

  // Check if the window is a translucent layered window.
  const LONG window_ex_style = GetWindowLong(selected, GWL_EXSTYLE);
  if (window_ex_style & WS_EX_LAYERED) {
    COLORREF color_ref_key = 0;
    BYTE alpha = 0;
    DWORD flags = 0;

    // GetLayeredWindowAttributes fails if the window was setup with
    // UpdateLayeredWindow. We have no way to know the opacity of the window in
    // that case. This happens for Stiky Note (crbug/412726).
    if (!GetLayeredWindowAttributes(selected, &color_ref_key, &alpha, &flags))
      return false;

    // UpdateLayeredWindow is the only way to set per-pixel alpha and will cause
    // the previous GetLayeredWindowAttributes to fail. So we only need to check
    // the window wide color key or alpha.
    if ((flags & LWA_COLORKEY) || ((flags & LWA_ALPHA) && (alpha < 255))) {
      return false;
    }
  }

  if (!GetWindowRect(selected, &window_region_rect_)) {
    return false;
  }

  DesktopRect content_rect;
  if (!GetWindowContentRect(selected, &content_rect)) {
    return false;
  }

  DesktopRect region_rect;
  // Get the window region and check if it is rectangular.
  const int region_type =
      GetWindowRegionTypeWithBoundary(selected, &region_rect);

  // Do not use the screen capturer if the region is empty or not rectangular.
  if (region_type == COMPLEXREGION || region_type == NULLREGION) {
    return false;
  }

  if (region_type == SIMPLEREGION) {
    // The `region_rect` returned from GetRgnBox() is always in window
    // coordinate.
    region_rect.Translate(window_region_rect_.left(),
                          window_region_rect_.top());
    // MSDN: The window region determines the area *within* the window where the
    // system permits drawing.
    // https://msdn.microsoft.com/en-us/library/windows/desktop/dd144950(v=vs.85).aspx.
    //
    // `region_rect` should always be inside of `window_region_rect_`. So after
    // the intersection, `window_region_rect_` == `region_rect`. If so, what's
    // the point of the intersecting operations? Why cannot we directly retrieve
    // `window_region_rect_` from GetWindowRegionTypeWithBoundary() function?
    // TODO(zijiehe): Figure out the purpose of these intersections.
    window_region_rect_.IntersectWith(region_rect);
    content_rect.IntersectWith(region_rect);
  }

  // Check if the client area is out of the screen area. When the window is
  // maximized, only its client area is visible in the screen, the border will
  // be hidden. So we are using `content_rect` here.
  if (!GetFullscreenRect().ContainsRect(content_rect)) {
    return false;
  }

  // Check if the window is occluded by any other window, excluding the child
  // windows, context menus, and `excluded_window_`.
  // `content_rect` is preferred, see the comments on
  // IsWindowIntersectWithSelectedWindow().
  TopWindowVerifierContext context(selected,
                                   reinterpret_cast<HWND>(excluded_window()),
                                   content_rect, &window_capture_helper_);
  return context.IsTopWindow();
}

DesktopRect CroppingWindowCapturerWin::GetWindowRectInVirtualScreen() {
  TRACE_EVENT0("webrtc",
               "CroppingWindowCapturerWin::GetWindowRectInVirtualScreen");
  DesktopRect window_rect;
  HWND hwnd = reinterpret_cast<HWND>(GetWindowToCapture());
  if (!GetCroppedWindowRect(hwnd, /*avoid_cropping_border*/ false, &window_rect,
                            /*original_rect*/ nullptr)) {
    RTC_LOG(LS_WARNING) << "Failed to get window info: " << GetLastError();
    return window_rect;
  }
  window_rect.IntersectWith(window_region_rect_);

  // Convert `window_rect` to be relative to the top-left of the virtual screen.
  DesktopRect screen_rect(GetFullscreenRect());
  window_rect.IntersectWith(screen_rect);
  window_rect.Translate(-screen_rect.left(), -screen_rect.top());
  return window_rect;
}

WindowId CroppingWindowCapturerWin::GetWindowToCapture() const {
  const auto selected_source = selected_window();
  const auto full_screen_source =
      full_screen_window_detector_
          ? full_screen_window_detector_->FindFullScreenWindow(selected_source)
          : 0;
  if (full_screen_source && full_screen_source != selected_source &&
      !fullscreen_usage_logged_) {
    fullscreen_usage_logged_ = true;
    LogDesktopCapturerFullscreenDetectorUsage();
  }
  return full_screen_source ? full_screen_source : selected_source;
}

}  // namespace

// static
std::unique_ptr<DesktopCapturer> CroppingWindowCapturer::CreateCapturer(
    const DesktopCaptureOptions& options) {
  std::unique_ptr<DesktopCapturer> capturer(
      new CroppingWindowCapturerWin(options));
  if (capturer && options.detect_updated_region()) {
    capturer.reset(new DesktopCapturerDifferWrapper(std::move(capturer)));
  }

  return capturer;
}

}  // namespace webrtc
