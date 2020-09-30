/*
 *  Copyright (c) 2013 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "modules/desktop_capture/linux/mouse_cursor_monitor_x11.h"

#include <X11/Xlib.h>
#include <X11/extensions/Xfixes.h>
#include <X11/extensions/xfixeswire.h>
#include <stddef.h>
#include <stdint.h>

#include <algorithm>
#include <memory>

#include "modules/desktop_capture/desktop_capture_options.h"
#include "modules/desktop_capture/desktop_capture_types.h"
#include "modules/desktop_capture/desktop_frame.h"
#include "modules/desktop_capture/desktop_geometry.h"
#include "modules/desktop_capture/linux/x_error_trap.h"
#include "modules/desktop_capture/mouse_cursor.h"
#include "modules/desktop_capture/mouse_cursor_monitor.h"
#include "rtc_base/checks.h"
#include "rtc_base/logging.h"

namespace {

// WindowCapturer returns window IDs of X11 windows with WM_STATE attribute.
// These windows may not be immediate children of the root window, because
// window managers may re-parent them to add decorations. However,
// XQueryPointer() expects to be passed children of the root. This function
// searches up the list of the windows to find the root child that corresponds
// to |window|.
Window GetTopLevelWindow(Display* display, Window window) {
  while (true) {
    // If the window is in WithdrawnState then look at all of its children.
    ::Window root, parent;
    ::Window* children;
    unsigned int num_children;
    if (!XQueryTree(display, window, &root, &parent, &children,
                    &num_children)) {
      RTC_LOG(LS_ERROR) << "Failed to query for child windows although window"
                           "does not have a valid WM_STATE.";
      return None;
    }
    if (children)
      XFree(children);

    if (parent == root)
      break;

    window = parent;
  }

  return window;
}

}  // namespace

namespace webrtc {

MouseCursorMonitorX11::MouseCursorMonitorX11(
    const DesktopCaptureOptions& options,
    Window window)
    : x_display_(options.x_display()),
      callback_(NULL),
      mode_(SHAPE_AND_POSITION),
      window_(window),
      have_xfixes_(false),
      xfixes_event_base_(-1),
      xfixes_error_base_(-1) {
  // Set a default initial cursor shape in case XFixes is not present.
  const int kSize = 5;
  std::unique_ptr<DesktopFrame> default_cursor(
      new BasicDesktopFrame(DesktopSize(kSize, kSize)));
  const uint8_t pixels[kSize * kSize] = {
      0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0xff, 0xff, 0xff,
      0x00, 0x00, 0xff, 0xff, 0xff, 0x00, 0x00, 0xff, 0xff,
      0xff, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00};
  uint8_t* ptr = default_cursor->data();
  for (int y = 0; y < kSize; ++y) {
    for (int x = 0; x < kSize; ++x) {
      *ptr++ = pixels[kSize * y + x];
      *ptr++ = pixels[kSize * y + x];
      *ptr++ = pixels[kSize * y + x];
      *ptr++ = 0xff;
    }
  }
  DesktopVector hotspot(2, 2);
  cursor_shape_.reset(new MouseCursor(default_cursor.release(), hotspot));
}

MouseCursorMonitorX11::~MouseCursorMonitorX11() {
  if (have_xfixes_) {
    x_display_->RemoveEventHandler(xfixes_event_base_ + XFixesCursorNotify,
                                   this);
  }
}

void MouseCursorMonitorX11::Init(Callback* callback, Mode mode) {
  // Init can be called only once per instance of MouseCursorMonitor.
  RTC_DCHECK(!callback_);
  RTC_DCHECK(callback);

  callback_ = callback;
  mode_ = mode;

  have_xfixes_ =
      XFixesQueryExtension(display(), &xfixes_event_base_, &xfixes_error_base_);

  if (have_xfixes_) {
    // Register for changes to the cursor shape.
    XFixesSelectCursorInput(display(), window_, XFixesDisplayCursorNotifyMask);
    x_display_->AddEventHandler(xfixes_event_base_ + XFixesCursorNotify, this);

    CaptureCursor();
  } else {
    RTC_LOG(LS_INFO) << "X server does not support XFixes.";
  }
}

void MouseCursorMonitorX11::Capture() {
  RTC_DCHECK(callback_);

  // Process X11 events in case XFixes has sent cursor notification.
  x_display_->ProcessPendingXEvents();

  // cursor_shape_| is set only if we were notified of a cursor shape change.
  if (cursor_shape_.get())
    callback_->OnMouseCursor(cursor_shape_.release());

  // Get cursor position if necessary.
  if (mode_ == SHAPE_AND_POSITION) {
    int root_x;
    int root_y;
    int win_x;
    int win_y;
    Window root_window;
    Window child_window;
    unsigned int mask;

    XErrorTrap error_trap(display());
    Bool result = XQueryPointer(display(), window_, &root_window, &child_window,
                                &root_x, &root_y, &win_x, &win_y, &mask);
    CursorState state;
    if (!result || error_trap.GetLastErrorAndDisable() != 0) {
      state = OUTSIDE;
    } else {
      // In screen mode (window_ == root_window) the mouse is always inside.
      // XQueryPointer() sets |child_window| to None if the cursor is outside
      // |window_|.
      state =
          (window_ == root_window || child_window != None) ? INSIDE : OUTSIDE;
    }

    // As the comments to GetTopLevelWindow() above indicate, in window capture,
    // the cursor position capture happens in |window_|, while the frame catpure
    // happens in |child_window|. These two windows are not alwyas same, as
    // window manager may add some decorations to the |window_|. So translate
    // the coordinate in |window_| to the coordinate space of |child_window|.
    if (window_ != root_window && state == INSIDE) {
      int translated_x, translated_y;
      Window unused;
      if (XTranslateCoordinates(display(), window_, child_window, win_x, win_y,
                                &translated_x, &translated_y, &unused)) {
        win_x = translated_x;
        win_y = translated_y;
      }
    }

    // X11 always starts the coordinate from (0, 0), so we do not need to
    // translate here.
    callback_->OnMouseCursorPosition(DesktopVector(root_x, root_y));
  }
}

bool MouseCursorMonitorX11::HandleXEvent(const XEvent& event) {
  if (have_xfixes_ && event.type == xfixes_event_base_ + XFixesCursorNotify) {
    const XFixesCursorNotifyEvent* cursor_event =
        reinterpret_cast<const XFixesCursorNotifyEvent*>(&event);
    if (cursor_event->subtype == XFixesDisplayCursorNotify) {
      CaptureCursor();
    }
    // Return false, even if the event has been handled, because there might be
    // other listeners for cursor notifications.
  }
  return false;
}

void MouseCursorMonitorX11::CaptureCursor() {
  RTC_DCHECK(have_xfixes_);

  XFixesCursorImage* img;
  {
    XErrorTrap error_trap(display());
    img = XFixesGetCursorImage(display());
    if (!img || error_trap.GetLastErrorAndDisable() != 0)
      return;
  }

  std::unique_ptr<DesktopFrame> image(
      new BasicDesktopFrame(DesktopSize(img->width, img->height)));

  // Xlib stores 32-bit data in longs, even if longs are 64-bits long.
  unsigned long* src = img->pixels;
  uint32_t* dst = reinterpret_cast<uint32_t*>(image->data());
  uint32_t* dst_end = dst + (img->width * img->height);
  while (dst < dst_end) {
    *dst++ = static_cast<uint32_t>(*src++);
  }

  DesktopVector hotspot(std::min(img->width, img->xhot),
                        std::min(img->height, img->yhot));

  XFree(img);

  cursor_shape_.reset(new MouseCursor(image.release(), hotspot));
}

// static
MouseCursorMonitor* MouseCursorMonitorX11::CreateForWindow(
    const DesktopCaptureOptions& options,
    WindowId window) {
  if (!options.x_display())
    return NULL;
  window = GetTopLevelWindow(options.x_display()->display(), window);
  if (window == None)
    return NULL;
  return new MouseCursorMonitorX11(options, window);
}

MouseCursorMonitor* MouseCursorMonitorX11::CreateForScreen(
    const DesktopCaptureOptions& options,
    ScreenId screen) {
  if (!options.x_display())
    return NULL;
  return new MouseCursorMonitorX11(
      options, DefaultRootWindow(options.x_display()->display()));
}

std::unique_ptr<MouseCursorMonitor> MouseCursorMonitorX11::Create(
    const DesktopCaptureOptions& options) {
  return std::unique_ptr<MouseCursorMonitor>(
      CreateForScreen(options, kFullDesktopScreenId));
}

}  // namespace webrtc
