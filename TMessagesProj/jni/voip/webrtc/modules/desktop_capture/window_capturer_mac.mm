/*
 *  Copyright (c) 2013 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include <ApplicationServices/ApplicationServices.h>
#include <Cocoa/Cocoa.h>
#include <CoreFoundation/CoreFoundation.h>

#include <utility>

#include "api/scoped_refptr.h"
#include "modules/desktop_capture/desktop_capture_options.h"
#include "modules/desktop_capture/desktop_capturer.h"
#include "modules/desktop_capture/desktop_frame.h"
#include "modules/desktop_capture/mac/desktop_configuration.h"
#include "modules/desktop_capture/mac/desktop_configuration_monitor.h"
#include "modules/desktop_capture/mac/desktop_frame_cgimage.h"
#include "modules/desktop_capture/mac/window_list_utils.h"
#include "modules/desktop_capture/window_finder_mac.h"
#include "rtc_base/checks.h"
#include "rtc_base/logging.h"
#include "rtc_base/trace_event.h"

namespace webrtc {

namespace {

// Returns true if the window exists.
bool IsWindowValid(CGWindowID id) {
  CFArrayRef window_id_array =
      CFArrayCreate(nullptr, reinterpret_cast<const void**>(&id), 1, nullptr);
  CFArrayRef window_array =
      CGWindowListCreateDescriptionFromArray(window_id_array);
  bool valid = window_array && CFArrayGetCount(window_array);
  CFRelease(window_id_array);
  CFRelease(window_array);

  return valid;
}

class WindowCapturerMac : public DesktopCapturer {
 public:
  explicit WindowCapturerMac(
      rtc::scoped_refptr<FullScreenWindowDetector> full_screen_window_detector,
      rtc::scoped_refptr<DesktopConfigurationMonitor> configuration_monitor);
  ~WindowCapturerMac() override;

  WindowCapturerMac(const WindowCapturerMac&) = delete;
  WindowCapturerMac& operator=(const WindowCapturerMac&) = delete;

  // DesktopCapturer interface.
  void Start(Callback* callback) override;
  void CaptureFrame() override;
  bool GetSourceList(SourceList* sources) override;
  bool SelectSource(SourceId id) override;
  bool FocusOnSelectedSource() override;
  bool IsOccluded(const DesktopVector& pos) override;

 private:
  Callback* callback_ = nullptr;

  // The window being captured.
  CGWindowID window_id_ = 0;

  rtc::scoped_refptr<FullScreenWindowDetector> full_screen_window_detector_;

  const rtc::scoped_refptr<DesktopConfigurationMonitor> configuration_monitor_;

  WindowFinderMac window_finder_;

  // Used to make sure that we only log the usage of fullscreen detection once.
  bool fullscreen_usage_logged_ = false;
};

WindowCapturerMac::WindowCapturerMac(
    rtc::scoped_refptr<FullScreenWindowDetector> full_screen_window_detector,
    rtc::scoped_refptr<DesktopConfigurationMonitor> configuration_monitor)
    : full_screen_window_detector_(std::move(full_screen_window_detector)),
      configuration_monitor_(std::move(configuration_monitor)),
      window_finder_(configuration_monitor_) {}

WindowCapturerMac::~WindowCapturerMac() {}

bool WindowCapturerMac::GetSourceList(SourceList* sources) {
  return webrtc::GetWindowList(sources, true, true);
}

bool WindowCapturerMac::SelectSource(SourceId id) {
  if (!IsWindowValid(id))
    return false;
  window_id_ = id;
  return true;
}

bool WindowCapturerMac::FocusOnSelectedSource() {
  if (!window_id_)
    return false;

  CGWindowID ids[1];
  ids[0] = window_id_;
  CFArrayRef window_id_array =
      CFArrayCreate(nullptr, reinterpret_cast<const void**>(&ids), 1, nullptr);

  CFArrayRef window_array =
      CGWindowListCreateDescriptionFromArray(window_id_array);
  if (!window_array || 0 == CFArrayGetCount(window_array)) {
    // Could not find the window. It might have been closed.
    RTC_LOG(LS_INFO) << "Window not found";
    CFRelease(window_id_array);
    return false;
  }

  CFDictionaryRef window = reinterpret_cast<CFDictionaryRef>(
      CFArrayGetValueAtIndex(window_array, 0));
  CFNumberRef pid_ref = reinterpret_cast<CFNumberRef>(
      CFDictionaryGetValue(window, kCGWindowOwnerPID));

  int pid;
  CFNumberGetValue(pid_ref, kCFNumberIntType, &pid);

  // TODO(jiayl): this will bring the process main window to the front. We
  // should find a way to bring only the window to the front.
  bool result =
      [[NSRunningApplication runningApplicationWithProcessIdentifier:pid] activateWithOptions:0];

  CFRelease(window_id_array);
  CFRelease(window_array);
  return result;
}

bool WindowCapturerMac::IsOccluded(const DesktopVector& pos) {
  DesktopVector sys_pos = pos;
  if (configuration_monitor_) {
    auto configuration = configuration_monitor_->desktop_configuration();
    sys_pos = pos.add(configuration.bounds.top_left());
  }
  return window_finder_.GetWindowUnderPoint(sys_pos) != window_id_;
}

void WindowCapturerMac::Start(Callback* callback) {
  RTC_DCHECK(!callback_);
  RTC_DCHECK(callback);

  callback_ = callback;
}

void WindowCapturerMac::CaptureFrame() {
  TRACE_EVENT0("webrtc", "WindowCapturerMac::CaptureFrame");

  if (!IsWindowValid(window_id_)) {
    RTC_LOG(LS_ERROR) << "The window is not valid any longer.";
    callback_->OnCaptureResult(Result::ERROR_PERMANENT, nullptr);
    return;
  }

  CGWindowID on_screen_window = window_id_;
  if (full_screen_window_detector_) {
    full_screen_window_detector_->UpdateWindowListIfNeeded(
        window_id_, [](DesktopCapturer::SourceList* sources) {
          // Not using webrtc::GetWindowList(sources, true, false)
          // as it doesn't allow to have in the result window with
          // empty title along with titled window owned by the same pid.
          return webrtc::GetWindowList(
              [sources](CFDictionaryRef window) {
                WindowId window_id = GetWindowId(window);
                if (window_id != kNullWindowId) {
                  sources->push_back(DesktopCapturer::Source{window_id, GetWindowTitle(window)});
                }
                return true;
              },
              true,
              false);
        });

    CGWindowID full_screen_window = full_screen_window_detector_->FindFullScreenWindow(window_id_);

    if (full_screen_window != kCGNullWindowID) {
      // If this is the first time this happens, report to UMA that the feature is active.
      if (!fullscreen_usage_logged_) {
        LogDesktopCapturerFullscreenDetectorUsage();
        fullscreen_usage_logged_ = true;
      }
      on_screen_window = full_screen_window;
    }
  }

  std::unique_ptr<DesktopFrame> frame = DesktopFrameCGImage::CreateForWindow(on_screen_window);
  if (!frame) {
    RTC_LOG(LS_WARNING) << "Temporarily failed to capture window.";
    callback_->OnCaptureResult(Result::ERROR_TEMPORARY, nullptr);
    return;
  }

  frame->mutable_updated_region()->SetRect(
      DesktopRect::MakeSize(frame->size()));
  frame->set_top_left(GetWindowBounds(on_screen_window).top_left());

  float scale_factor = GetWindowScaleFactor(window_id_, frame->size());
  frame->set_dpi(DesktopVector(kStandardDPI * scale_factor, kStandardDPI * scale_factor));

  callback_->OnCaptureResult(Result::SUCCESS, std::move(frame));
}

}  // namespace

// static
std::unique_ptr<DesktopCapturer> DesktopCapturer::CreateRawWindowCapturer(
    const DesktopCaptureOptions& options) {
  return std::unique_ptr<DesktopCapturer>(new WindowCapturerMac(
      options.full_screen_window_detector(), options.configuration_monitor()));
}

}  // namespace webrtc
