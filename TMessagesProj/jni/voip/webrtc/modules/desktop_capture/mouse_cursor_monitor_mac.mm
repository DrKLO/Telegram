/*
 *  Copyright (c) 2013 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "modules/desktop_capture/mouse_cursor_monitor.h"


#include <memory>

#include <ApplicationServices/ApplicationServices.h>
#include <Cocoa/Cocoa.h>
#include <CoreFoundation/CoreFoundation.h>

#include "api/scoped_refptr.h"
#include "modules/desktop_capture/desktop_capture_options.h"
#include "modules/desktop_capture/desktop_capture_types.h"
#include "modules/desktop_capture/desktop_frame.h"
#include "modules/desktop_capture/mac/desktop_configuration.h"
#include "modules/desktop_capture/mac/desktop_configuration_monitor.h"
#include "modules/desktop_capture/mac/window_list_utils.h"
#include "modules/desktop_capture/mouse_cursor.h"
#include "rtc_base/checks.h"

namespace webrtc {

namespace {
CGImageRef CreateScaledCGImage(CGImageRef image, int width, int height) {
  // Create context, keeping original image properties.
  CGColorSpaceRef colorspace = CGImageGetColorSpace(image);
  CGContextRef context = CGBitmapContextCreate(nullptr,
                                               width,
                                               height,
                                               CGImageGetBitsPerComponent(image),
                                               width * DesktopFrame::kBytesPerPixel,
                                               colorspace,
                                               CGImageGetBitmapInfo(image));

  if (!context) return nil;

  // Draw image to context, resizing it.
  CGContextDrawImage(context, CGRectMake(0, 0, width, height), image);
  // Extract resulting image from context.
  CGImageRef imgRef = CGBitmapContextCreateImage(context);
  CGContextRelease(context);

  return imgRef;
}
}  // namespace

class MouseCursorMonitorMac : public MouseCursorMonitor {
 public:
  MouseCursorMonitorMac(const DesktopCaptureOptions& options,
                        CGWindowID window_id,
                        ScreenId screen_id);
  ~MouseCursorMonitorMac() override;

  void Init(Callback* callback, Mode mode) override;
  void Capture() override;

 private:
  static void DisplaysReconfiguredCallback(CGDirectDisplayID display,
                                           CGDisplayChangeSummaryFlags flags,
                                           void *user_parameter);
  void DisplaysReconfigured(CGDirectDisplayID display,
                            CGDisplayChangeSummaryFlags flags);

  void CaptureImage(float scale);

  rtc::scoped_refptr<DesktopConfigurationMonitor> configuration_monitor_;
  CGWindowID window_id_;
  ScreenId screen_id_;
  Callback* callback_ = NULL;
  Mode mode_;
  __strong NSImage* last_cursor_ = NULL;
};

MouseCursorMonitorMac::MouseCursorMonitorMac(const DesktopCaptureOptions& options,
                                             CGWindowID window_id,
                                             ScreenId screen_id)
    : configuration_monitor_(options.configuration_monitor()),
      window_id_(window_id),
      screen_id_(screen_id),
      mode_(SHAPE_AND_POSITION) {
  RTC_DCHECK(window_id == kCGNullWindowID || screen_id == kInvalidScreenId);
}

MouseCursorMonitorMac::~MouseCursorMonitorMac() {}

void MouseCursorMonitorMac::Init(Callback* callback, Mode mode) {
  RTC_DCHECK(!callback_);
  RTC_DCHECK(callback);

  callback_ = callback;
  mode_ = mode;
}

void MouseCursorMonitorMac::Capture() {
  RTC_DCHECK(callback_);

  CGEventRef event = CGEventCreate(NULL);
  CGPoint gc_position = CGEventGetLocation(event);
  CFRelease(event);

  DesktopVector position(gc_position.x, gc_position.y);

  MacDesktopConfiguration configuration =
      configuration_monitor_->desktop_configuration();
  float scale = GetScaleFactorAtPosition(configuration, position);

  CaptureImage(scale);

  if (mode_ != SHAPE_AND_POSITION)
    return;

  // Always report cursor position in DIP pixel.
  callback_->OnMouseCursorPosition(
      position.subtract(configuration.bounds.top_left()));
}

void MouseCursorMonitorMac::CaptureImage(float scale) {
  NSCursor* nscursor = [NSCursor currentSystemCursor];

  NSImage* nsimage = [nscursor image];
  if (nsimage == nil || !nsimage.isValid) {
    return;
  }
  NSSize nssize = [nsimage size];  // DIP size

  // No need to caputre cursor image if it's unchanged since last capture.
  if ([[nsimage TIFFRepresentation] isEqual:[last_cursor_ TIFFRepresentation]]) return;
  last_cursor_ = nsimage;

  DesktopSize size(round(nssize.width * scale),
                   round(nssize.height * scale));  // Pixel size
  NSPoint nshotspot = [nscursor hotSpot];
  DesktopVector hotspot(
      std::max(0,
               std::min(size.width(), static_cast<int>(nshotspot.x * scale))),
      std::max(0,
               std::min(size.height(), static_cast<int>(nshotspot.y * scale))));
  CGImageRef cg_image =
      [nsimage CGImageForProposedRect:NULL context:nil hints:nil];
  if (!cg_image)
    return;

  // Before 10.12, OSX may report 1X cursor on Retina screen. (See
  // crbug.com/632995.) After 10.12, OSX may report 2X cursor on non-Retina
  // screen. (See crbug.com/671436.) So scaling the cursor if needed.
  CGImageRef scaled_cg_image = nil;
  if (CGImageGetWidth(cg_image) != static_cast<size_t>(size.width())) {
    scaled_cg_image = CreateScaledCGImage(cg_image, size.width(), size.height());
    if (scaled_cg_image != nil) {
      cg_image = scaled_cg_image;
    }
  }
  if (CGImageGetBitsPerPixel(cg_image) != DesktopFrame::kBytesPerPixel * 8 ||
      CGImageGetWidth(cg_image) != static_cast<size_t>(size.width()) ||
      CGImageGetBitsPerComponent(cg_image) != 8) {
    if (scaled_cg_image != nil) CGImageRelease(scaled_cg_image);
    return;
  }

  CGDataProviderRef provider = CGImageGetDataProvider(cg_image);
  CFDataRef image_data_ref = CGDataProviderCopyData(provider);
  if (image_data_ref == NULL) {
    if (scaled_cg_image != nil) CGImageRelease(scaled_cg_image);
    return;
  }

  const uint8_t* src_data =
      reinterpret_cast<const uint8_t*>(CFDataGetBytePtr(image_data_ref));

  // Create a MouseCursor that describes the cursor and pass it to
  // the client.
  std::unique_ptr<DesktopFrame> image(
      new BasicDesktopFrame(DesktopSize(size.width(), size.height())));

  int src_stride = CGImageGetBytesPerRow(cg_image);
  image->CopyPixelsFrom(src_data, src_stride, DesktopRect::MakeSize(size));

  CFRelease(image_data_ref);
  if (scaled_cg_image != nil) CGImageRelease(scaled_cg_image);

  std::unique_ptr<MouseCursor> cursor(
      new MouseCursor(image.release(), hotspot));

  callback_->OnMouseCursor(cursor.release());
}

MouseCursorMonitor* MouseCursorMonitor::CreateForWindow(
    const DesktopCaptureOptions& options, WindowId window) {
  return new MouseCursorMonitorMac(options, window, kInvalidScreenId);
}

MouseCursorMonitor* MouseCursorMonitor::CreateForScreen(
    const DesktopCaptureOptions& options,
    ScreenId screen) {
  return new MouseCursorMonitorMac(options, kCGNullWindowID, screen);
}

std::unique_ptr<MouseCursorMonitor> MouseCursorMonitor::Create(
    const DesktopCaptureOptions& options) {
  return std::unique_ptr<MouseCursorMonitor>(
      CreateForScreen(options, kFullDesktopScreenId));
}

}  // namespace webrtc
