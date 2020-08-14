/*
 *  Copyright (c) 2013 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */
#ifndef MODULES_DESKTOP_CAPTURE_DESKTOP_CAPTURE_OPTIONS_H_
#define MODULES_DESKTOP_CAPTURE_DESKTOP_CAPTURE_OPTIONS_H_

#include "api/scoped_refptr.h"
#include "rtc_base/system/rtc_export.h"

#if defined(WEBRTC_USE_X11)
#include "modules/desktop_capture/linux/shared_x_display.h"
#endif

#if defined(WEBRTC_MAC) && !defined(WEBRTC_IOS)
#include "modules/desktop_capture/mac/desktop_configuration_monitor.h"
#endif

#include "modules/desktop_capture/full_screen_window_detector.h"

namespace webrtc {

// An object that stores initialization parameters for screen and window
// capturers.
class RTC_EXPORT DesktopCaptureOptions {
 public:
  // Returns instance of DesktopCaptureOptions with default parameters. On Linux
  // also initializes X window connection. x_display() will be set to null if
  // X11 connection failed (e.g. DISPLAY isn't set).
  static DesktopCaptureOptions CreateDefault();

  DesktopCaptureOptions();
  DesktopCaptureOptions(const DesktopCaptureOptions& options);
  DesktopCaptureOptions(DesktopCaptureOptions&& options);
  ~DesktopCaptureOptions();

  DesktopCaptureOptions& operator=(const DesktopCaptureOptions& options);
  DesktopCaptureOptions& operator=(DesktopCaptureOptions&& options);

#if defined(WEBRTC_USE_X11)
  SharedXDisplay* x_display() const { return x_display_; }
  void set_x_display(rtc::scoped_refptr<SharedXDisplay> x_display) {
    x_display_ = x_display;
  }
#endif

#if defined(WEBRTC_MAC) && !defined(WEBRTC_IOS)
  // TODO(zijiehe): Remove both DesktopConfigurationMonitor and
  // FullScreenChromeWindowDetector out of DesktopCaptureOptions. It's not
  // reasonable for external consumers to set these two parameters.
  DesktopConfigurationMonitor* configuration_monitor() const {
    return configuration_monitor_;
  }
  // If nullptr is set, ScreenCapturer won't work and WindowCapturer may return
  // inaccurate result from IsOccluded() function.
  void set_configuration_monitor(
      rtc::scoped_refptr<DesktopConfigurationMonitor> m) {
    configuration_monitor_ = m;
  }

  bool allow_iosurface() const { return allow_iosurface_; }
  void set_allow_iosurface(bool allow) { allow_iosurface_ = allow; }
#endif

  FullScreenWindowDetector* full_screen_window_detector() const {
    return full_screen_window_detector_;
  }
  void set_full_screen_window_detector(
      rtc::scoped_refptr<FullScreenWindowDetector> detector) {
    full_screen_window_detector_ = detector;
  }

  // Flag indicating that the capturer should use screen change notifications.
  // Enables/disables use of XDAMAGE in the X11 capturer.
  bool use_update_notifications() const { return use_update_notifications_; }
  void set_use_update_notifications(bool use_update_notifications) {
    use_update_notifications_ = use_update_notifications;
  }

  // Flag indicating if desktop effects (e.g. Aero) should be disabled when the
  // capturer is active. Currently used only on Windows.
  bool disable_effects() const { return disable_effects_; }
  void set_disable_effects(bool disable_effects) {
    disable_effects_ = disable_effects;
  }

  // Flag that should be set if the consumer uses updated_region() and the
  // capturer should try to provide correct updated_region() for the frames it
  // generates (e.g. by comparing each frame with the previous one).
  bool detect_updated_region() const { return detect_updated_region_; }
  void set_detect_updated_region(bool detect_updated_region) {
    detect_updated_region_ = detect_updated_region;
  }

#if defined(WEBRTC_WIN)
  bool allow_use_magnification_api() const {
    return allow_use_magnification_api_;
  }
  void set_allow_use_magnification_api(bool allow) {
    allow_use_magnification_api_ = allow;
  }
  // Allowing directx based capturer or not, this capturer works on windows 7
  // with platform update / windows 8 or upper.
  bool allow_directx_capturer() const { return allow_directx_capturer_; }
  void set_allow_directx_capturer(bool enabled) {
    allow_directx_capturer_ = enabled;
  }

  // Flag that may be set to allow use of the cropping window capturer (which
  // captures the screen & crops that to the window region in some cases). An
  // advantage of using this is significantly higher capture frame rates than
  // capturing the window directly. A disadvantage of using this is the
  // possibility of capturing unrelated content (e.g. overlapping windows that
  // aren't detected properly, or neighboring regions when moving/resizing the
  // captured window). Note: this flag influences the behavior of calls to
  // DesktopCapturer::CreateWindowCapturer; calls to
  // CroppingWindowCapturer::CreateCapturer ignore the flag (treat it as true).
  bool allow_cropping_window_capturer() const {
    return allow_cropping_window_capturer_;
  }
  void set_allow_cropping_window_capturer(bool allow) {
    allow_cropping_window_capturer_ = allow;
  }
#endif

#if defined(WEBRTC_USE_PIPEWIRE)
  bool allow_pipewire() const { return allow_pipewire_; }
  void set_allow_pipewire(bool allow) { allow_pipewire_ = allow; }
#endif

 private:
#if defined(WEBRTC_USE_X11)
  rtc::scoped_refptr<SharedXDisplay> x_display_;
#endif

#if defined(WEBRTC_MAC) && !defined(WEBRTC_IOS)
  rtc::scoped_refptr<DesktopConfigurationMonitor> configuration_monitor_;
  bool allow_iosurface_ = false;
#endif

  rtc::scoped_refptr<FullScreenWindowDetector> full_screen_window_detector_;

#if defined(WEBRTC_WIN)
  bool allow_use_magnification_api_ = false;
  bool allow_directx_capturer_ = false;
  bool allow_cropping_window_capturer_ = false;
#endif
#if defined(WEBRTC_USE_X11)
  bool use_update_notifications_ = false;
#else
  bool use_update_notifications_ = true;
#endif
  bool disable_effects_ = true;
  bool detect_updated_region_ = false;
#if defined(WEBRTC_USE_PIPEWIRE)
  bool allow_pipewire_ = false;
#endif
};

}  // namespace webrtc

#endif  // MODULES_DESKTOP_CAPTURE_DESKTOP_CAPTURE_OPTIONS_H_
